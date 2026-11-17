-- =====================================================================
-- SportPro · Migración 016: ENLACE DEL ÁRBITRO (QR → página web, sin cuenta).
--
-- El DT genera un QR para su partido. El QR abre una página web (publicada en Vercel)
-- donde el árbitro registra los eventos en tiempo real SIN registrarse. Quien mira en la
-- app ve los cambios al instante, porque los eventos se guardan en la misma tabla
-- match_events que ya escucha Realtime.
--
-- Cómo se protege, si el árbitro no tiene cuenta:
--   · El QR lleva un CÓDIGO SECRETO de 64 caracteres al azar (imposible de adivinar).
--     Solo sirve para ESE partido.
--   · Vence solo: deja de servir 3 horas después de terminar el partido.
--   · El DT puede generar uno nuevo (el anterior deja de servir al instante) o desactivarlo.
--   · La página no lee ni escribe tablas: solo llama a 4 funciones que reciben el código,
--     lo validan y hacen UNA acción concreta (ver, registrar, cambiar estado, anular).
--   · Las mismas reglas de siempre siguen valiendo: los triggers de match_events validan
--     el tipo de evento, el equipo y el jugador; el inicio exige alineaciones completas.
--   · Privacidad: la página muestra a los menores como «Nombre I.» y nunca muestra fotos.
--   · Trazabilidad: cada acción del árbitro queda en audit_log con el enlace que se usó.
--     Anular exige motivo y guarda la versión anterior (match_event_revisions).
--
-- Es la ÚNICA excepción a la regla «el rol anónimo no ejecuta funciones» (además de
-- check_invitation). La auditoría automática lo comprueba con una lista cerrada.
--
-- Cómo ejecutarla: Supabase Dashboard → SQL Editor → pegar TODO → Run (transaccional).
-- =====================================================================
begin;

-- ---------------------------------------------------------------------
-- 1. Tabla de enlaces (nadie la lee directo: solo por funciones)
-- ---------------------------------------------------------------------
create table if not exists public.match_referee_links (
  id            uuid primary key default gen_random_uuid(),
  match_id      uuid not null references public.matches(id) on delete cascade,
  token         text not null unique check (token ~ '^[0-9a-f]{64}$'),
  created_by    uuid references public.profiles(id),
  created_at    timestamptz not null default now(),
  revoked_at    timestamptz,
  revoked_by    uuid references public.profiles(id),
  revoke_reason text
);
-- Un solo enlace activo por partido
create unique index if not exists match_referee_links_one_active
  on public.match_referee_links (match_id) where revoked_at is null;

alter table public.match_referee_links enable row level security;
revoke all on public.match_referee_links from anon, authenticated;
-- Sin políticas ni permisos: el código secreto nunca se lee con un select desde la app.

-- ---------------------------------------------------------------------
-- 2. El cambio de estado del partido, separado de su control de permisos
--    (así lo usan igual el DT desde la app y el árbitro desde la web)
-- ---------------------------------------------------------------------
create or replace function public._match_apply_state(p_match uuid, p_action text)
returns void language plpgsql security definer set search_path = public as $fn$
declare
  m public.matches%rowtype; v_now timestamptz := now(); v_code text; v_period smallint;
  v_elapsed int := 0; v_min int; v_n int; v_type uuid;
begin
  select * into m from public.matches where id = p_match for update;
  if not found then raise exception 'Partido inexistente'; end if;

  if p_action = 'start' then
    if m.status <> 'scheduled' then raise exception 'El partido ya inició o finalizó'; end if;
    select count(*) into v_n from public.match_lineup_players where match_id = p_match and team_side = 'home' and is_starter;
    if v_n < m.format_players then
      raise exception 'Alineación local incompleta: se requieren % titulares (hay %)', m.format_players, v_n;
    end if;
    if m.away_team_id is not null then
      select count(*) into v_n from public.match_lineup_players where match_id = p_match and team_side = 'away' and is_starter;
      if v_n < m.format_players then
        raise exception 'Alineación visitante incompleta: se requieren % titulares (hay %)', m.format_players, v_n;
      end if;
    end if;
    v_code := 'match_start'; v_period := 1; v_elapsed := 0;
  elsif p_action = 'halftime' then
    if m.status <> 'live' or m.period <> 1 then raise exception 'Solo se puede cerrar el primer tiempo en juego'; end if;
    v_code := 'first_half_end'; v_period := 1;
    v_elapsed := m.elapsed_before_seconds + floor(extract(epoch from (v_now - m.period_started_at)))::int;
  elsif p_action = 'second_half' then
    if m.status <> 'halftime' then raise exception 'El partido no está en entretiempo'; end if;
    v_code := 'second_half_start'; v_period := 2; v_elapsed := 0;
  elsif p_action = 'finish' then
    if m.status not in ('live','halftime') then raise exception 'El partido no está en juego'; end if;
    v_code := 'match_end'; v_period := greatest(m.period, 1);
    v_elapsed := m.elapsed_before_seconds
                 + case when m.status = 'live' then floor(extract(epoch from (v_now - m.period_started_at)))::int else 0 end;
  else
    raise exception 'Acción inválida: %', p_action;
  end if;

  v_min := least(200, (v_period - 1) * m.half_minutes + (v_elapsed / 60));
  select id into v_type from public.event_types where academy_id is null and code = v_code;

  -- 1) primero el evento de control; 2) luego el estado y el reloj
  insert into public.match_events (match_id, client_event_id, event_type_id, period, minute, team_side)
  values (p_match, gen_random_uuid(), v_type, v_period, v_min, 'none');

  if p_action = 'start' then
    update public.matches set status = 'live', period = 1, period_started_at = v_now,
           elapsed_before_seconds = 0, clock_running = true, started_at = v_now where id = p_match;
  elsif p_action = 'halftime' then
    update public.matches set status = 'halftime', clock_running = false,
           elapsed_before_seconds = v_elapsed, period_started_at = null where id = p_match;
  elsif p_action = 'second_half' then
    update public.matches set status = 'live', period = 2, period_started_at = v_now,
           elapsed_before_seconds = 0, clock_running = true where id = p_match;
  else
    update public.matches set status = 'finished', clock_running = false, finished_at = v_now,
           elapsed_before_seconds = v_elapsed, period_started_at = null where id = p_match;
  end if;
end $fn$;

-- La función de siempre (app): comprueba permisos y aplica. Mismo comportamiento que antes.
create or replace function public.change_match_state(p_match uuid, p_action text)
returns void language plpgsql security definer set search_path = public as $fn$
begin
  if not exists (select 1 from public.matches where id = p_match) then raise exception 'Partido inexistente'; end if;
  if not (public.is_match_manager(p_match) or public.is_match_operator(p_match)) then
    raise exception 'Sin permiso para controlar este partido';
  end if;
  perform public._match_apply_state(p_match, p_action);
  perform public.log_audit('match_state_' || p_action, 'match', p_match, '{}'::jsonb);
end $fn$;

-- ---------------------------------------------------------------------
-- 3. Validar el código del QR. Devuelve el enlace activo o nada.
-- ---------------------------------------------------------------------
create or replace function public._referee_link(p_token text)
returns public.match_referee_links language sql stable security definer set search_path = public as $fn$
  select l.*
  from public.match_referee_links l
  join public.matches m on m.id = l.match_id
  where p_token ~ '^[0-9a-f]{64}$'
    and l.token = p_token
    and l.revoked_at is null
    and m.status <> 'cancelled'
    -- Vence solo: 3 horas después de terminar el partido
    and (m.status <> 'finished' or m.finished_at > now() - interval '3 hours')
$fn$;

-- Igual que la anterior, pero falla con un mensaje claro si el código no sirve
create or replace function public._referee_link_or_fail(p_token text)
returns public.match_referee_links language plpgsql stable security definer set search_path = public as $fn$
declare l public.match_referee_links;
begin
  l := public._referee_link(p_token);
  if l.id is null then
    raise exception 'Este código de árbitro no es válido o ya venció. Pide al DT un QR nuevo.';
  end if;
  return l;
end $fn$;

-- ---------------------------------------------------------------------
-- 4. Funciones para el DT (app, con sesión)
-- ---------------------------------------------------------------------
-- Devuelve el código activo del partido; lo crea si no hay. Con p_regenerate = true crea uno
-- nuevo y el anterior deja de servir al instante.
create or replace function public.referee_link_get(p_match uuid, p_regenerate boolean default false)
returns text language plpgsql security definer set search_path = public as $fn$
declare m public.matches%rowtype; v_token text;
begin
  select * into m from public.matches where id = p_match;
  if not found then raise exception 'Partido inexistente'; end if;
  if not public.is_match_manager(p_match) then
    raise exception 'Solo el DT o el administrador del equipo local genera el QR del árbitro';
  end if;
  if m.status in ('finished', 'cancelled') then
    raise exception 'El partido ya terminó: no se puede generar un QR de árbitro';
  end if;

  if not coalesce(p_regenerate, false) then
    select token into v_token from public.match_referee_links where match_id = p_match and revoked_at is null;
    if v_token is not null then return v_token; end if;
  end if;

  update public.match_referee_links
     set revoked_at = now(), revoked_by = auth.uid(), revoke_reason = 'Reemplazado por un código nuevo'
   where match_id = p_match and revoked_at is null;
  -- 64 caracteres hexadecimales al azar (dos UUID v4 sin guiones: 244 bits aleatorios)
  v_token := replace(gen_random_uuid()::text, '-', '') || replace(gen_random_uuid()::text, '-', '');
  insert into public.match_referee_links (match_id, token, created_by) values (p_match, v_token, auth.uid());
  perform public.log_audit('referee_link_created', 'match', p_match, jsonb_build_object('regenerated', coalesce(p_regenerate, false)));
  return v_token;
end $fn$;

-- Desactiva el código activo (por ejemplo, si el QR se compartió por error)
create or replace function public.referee_link_revoke(p_match uuid)
returns void language plpgsql security definer set search_path = public as $fn$
begin
  if not public.is_match_manager(p_match) then
    raise exception 'Solo el DT o el administrador del equipo local desactiva el QR del árbitro';
  end if;
  update public.match_referee_links
     set revoked_at = now(), revoked_by = auth.uid(), revoke_reason = 'Desactivado por el DT'
   where match_id = p_match and revoked_at is null;
  perform public.log_audit('referee_link_revoked', 'match', p_match, '{}'::jsonb);
end $fn$;

-- ---------------------------------------------------------------------
-- 5. Funciones para la página del árbitro (sin cuenta: reciben el código)
-- ---------------------------------------------------------------------
-- 5.1 Todo lo que la página necesita en una sola llamada. La página la repite cada pocos
--     segundos, así ve también lo que registra el DT desde la app.
create or replace function public.referee_match(p_token text)
returns jsonb language plpgsql stable security definer set search_path = public as $fn$
declare l public.match_referee_links; m public.matches%rowtype; v jsonb;
begin
  l := public._referee_link_or_fail(p_token);
  select * into m from public.matches where id = l.match_id;
  select jsonb_build_object(
    'server_now', now(),
    'match', jsonb_build_object(
      'id', m.id, 'status', m.status, 'period', m.period,
      'period_started_at', m.period_started_at, 'elapsed_before_seconds', m.elapsed_before_seconds,
      'clock_running', m.clock_running, 'half_minutes', m.half_minutes, 'format_players', m.format_players,
      'home_score', m.home_score, 'away_score', m.away_score,
      'scheduled_at', m.scheduled_at, 'venue', m.venue,
      'home_name', (select name from public.teams where id = m.home_team_id),
      'away_name', coalesce((select name from public.teams where id = m.away_team_id), m.away_external_name),
      'category', (select category from public.teams where id = m.home_team_id)),
    -- Alineación de los dos lados. Menores como «Nombre I.» (player_public_name), sin fotos.
    'lineup', coalesce((
      select jsonb_agg(x order by x->>'side', (x->>'is_starter')::boolean desc, (x->>'jersey')::int nulls last)
      from (
        select jsonb_build_object(
                 'side', lp.team_side, 'key', lp.player_id, 'external', false,
                 'name', public.player_public_name(lp.player_id),
                 'jersey', coalesce(lp.jersey_number, tp.jersey_number),
                 'is_starter', lp.is_starter, 'position', coalesce(lp.field_position, tp.position)) as x
        from public.match_lineup_players lp
        left join public.team_players tp
               on tp.player_id = lp.player_id
              and tp.team_id = case lp.team_side when 'home' then m.home_team_id else m.away_team_id end
        where lp.match_id = m.id
        union all
        select jsonb_build_object(
                 'side', 'away', 'key', ep.id, 'external', true, 'name', ep.name,
                 'jersey', ep.jersey_number, 'is_starter', ep.is_starter, 'position', ep.field_position)
        from public.match_external_players ep
        where ep.match_id = m.id
      ) s), '[]'::jsonb),
    -- Tipos de evento que el árbitro puede registrar (los de control van con los botones de estado)
    'event_types', coalesce((
      select jsonb_agg(jsonb_build_object(
               'id', t.id, 'code', t.code, 'name', t.name, 'icon', t.icon, 'group_kind', t.group_kind,
               'scoring_rule', t.scoring_rule, 'required_payload', to_jsonb(t.required_payload))
             order by t.sort_order, t.name)
      from public.event_types t
      where t.is_active and t.group_kind <> 'control'
        and (t.academy_id is null or t.academy_id = m.academy_id)), '[]'::jsonb),
    'events', coalesce((
      select jsonb_agg(jsonb_build_object(
               'id', e.id, 'seq', e.seq, 'code', t.code, 'name', t.name, 'icon', t.icon,
               'group_kind', t.group_kind, 'period', e.period, 'minute', e.minute,
               'minute_extra', e.minute_extra, 'side', e.team_side,
               'player_key', coalesce(e.player_id, e.external_player_id),
               'player_name', case when e.player_id is not null then public.player_public_name(e.player_id)
                                   when e.external_player_id is not null then (select ep.name from public.match_external_players ep where ep.id = e.external_player_id) end,
               'payload', e.payload, 'status', e.status)
             order by e.seq)
      from public.match_events e join public.event_types t on t.id = e.event_type_id
      where e.match_id = m.id), '[]'::jsonb)
  ) into v;
  return v;
end $fn$;

-- 5.2 Registrar un evento. Idempotente: si llega dos veces el mismo client_event_id
--     (reintento sin señal), devuelve el evento que ya existe y no lo duplica.
create or replace function public.referee_record_event(
  p_token text, p_client_event_id uuid, p_event_type uuid, p_period int, p_minute int,
  p_minute_extra int default 0, p_team_side text default null, p_player uuid default null,
  p_external_player uuid default null, p_payload jsonb default '{}'::jsonb)
returns uuid language plpgsql security definer set search_path = public as $fn$
declare l public.match_referee_links; m public.matches%rowtype; v_kind text; v_id uuid;
begin
  l := public._referee_link_or_fail(p_token);
  if p_client_event_id is null then raise exception 'Falta el identificador del evento'; end if;
  -- Reintento: ya estaba guardado
  select id into v_id from public.match_events where match_id = l.match_id and client_event_id = p_client_event_id;
  if v_id is not null then return v_id; end if;

  select * into m from public.matches where id = l.match_id;
  if m.status = 'finished' then raise exception 'El partido ya terminó: no se registran más eventos'; end if;
  if m.status not in ('live', 'halftime') then
    raise exception 'El partido no está en juego: inícialo para registrar eventos';
  end if;
  select group_kind into v_kind from public.event_types where id = p_event_type;
  if v_kind is null then raise exception 'Tipo de evento inexistente'; end if;
  if v_kind = 'control' then raise exception 'El inicio, el entretiempo y el final se marcan con sus botones'; end if;
  if p_minute is null or p_minute not between 0 and 200 then raise exception 'Minuto inválido'; end if;
  if coalesce(p_minute_extra, 0) not between 0 and 30 then raise exception 'Tiempo añadido inválido'; end if;

  -- El resto (equipo, jugador, datos obligatorios) lo validan los triggers de match_events
  insert into public.match_events (match_id, client_event_id, event_type_id, period, minute, minute_extra,
                                   team_side, player_id, external_player_id, payload)
  values (l.match_id, p_client_event_id, p_event_type, coalesce(p_period, greatest(m.period, 1)), p_minute,
          coalesce(p_minute_extra, 0), p_team_side, p_player, p_external_player, coalesce(p_payload, '{}'::jsonb))
  returning id into v_id;
  perform public.log_audit('referee_event', 'match_event', v_id,
    jsonb_build_object('match_id', l.match_id, 'referee_link_id', l.id));
  return v_id;
end $fn$;

-- 5.3 Iniciar, entretiempo, segundo tiempo y finalizar
create or replace function public.referee_change_state(p_token text, p_action text)
returns void language plpgsql security definer set search_path = public as $fn$
declare l public.match_referee_links;
begin
  l := public._referee_link_or_fail(p_token);
  perform public._match_apply_state(l.match_id, p_action);
  perform public.log_audit('match_state_' || p_action, 'match', l.match_id,
    jsonb_build_object('via', 'referee_link', 'referee_link_id', l.id));
end $fn$;

-- 5.4 Anular un evento (con motivo). Nada se borra: queda tachado y con su versión anterior.
create or replace function public.referee_void_event(p_token text, p_event uuid, p_reason text)
returns void language plpgsql security definer set search_path = public as $fn$
declare l public.match_referee_links; e public.match_events%rowtype; v_kind text; v_status public.match_status;
begin
  l := public._referee_link_or_fail(p_token);
  select * into e from public.match_events where id = p_event and match_id = l.match_id for update;
  if not found then raise exception 'Evento inexistente en este partido'; end if;
  select status into v_status from public.matches where id = l.match_id;
  if v_status not in ('live', 'halftime') then raise exception 'Solo se anulan eventos mientras el partido está en juego'; end if;
  select group_kind into v_kind from public.event_types where id = e.event_type_id;
  if v_kind = 'control' then raise exception 'El inicio, el entretiempo y el final no se anulan'; end if;
  if e.status = 'voided' then raise exception 'El evento ya estaba anulado'; end if;
  if char_length(trim(coalesce(p_reason, ''))) < 3 then raise exception 'Escribe el motivo de la anulación'; end if;
  perform set_config('app.change_reason', 'Árbitro: ' || trim(p_reason), true);
  update public.match_events
     set status = 'voided', voided_at = now(), void_reason = 'Árbitro: ' || trim(p_reason)
   where id = e.id;
  perform public.log_audit('void_event', 'match_event', e.id,
    jsonb_build_object('reason', trim(p_reason), 'via', 'referee_link', 'referee_link_id', l.id));
end $fn$;

-- ---------------------------------------------------------------------
-- 6. Permisos de ejecución
-- ---------------------------------------------------------------------
revoke execute on function public._match_apply_state(uuid, text), public._referee_link(text),
  public._referee_link_or_fail(text)
  from public, anon, authenticated;
revoke execute on function public.referee_link_get(uuid, boolean), public.referee_link_revoke(uuid),
  public.referee_match(text),
  public.referee_record_event(text, uuid, uuid, int, int, int, text, uuid, uuid, jsonb),
  public.referee_change_state(text, text), public.referee_void_event(text, uuid, text)
  from public;
revoke execute on function public.referee_link_get(uuid, boolean), public.referee_link_revoke(uuid) from anon;
grant execute on function public.referee_link_get(uuid, boolean), public.referee_link_revoke(uuid) to authenticated;
-- Las 4 funciones de la página: también sin sesión (el código del QR es la llave)
grant execute on function public.referee_match(text),
  public.referee_record_event(text, uuid, uuid, int, int, int, text, uuid, uuid, jsonb),
  public.referee_change_state(text, text), public.referee_void_event(text, uuid, text)
  to anon, authenticated;

commit;
