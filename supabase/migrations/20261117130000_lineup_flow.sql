-- =====================================================================
-- SportPro · Migración 017: ALINEACIONES de los partidos acordados por desafío.
--
-- Vale para los partidos que nacieron de un desafío aceptado (migración 015), es decir,
-- entre equipos de entrenadores distintos. Los demás partidos (rival externo o dos equipos
-- del mismo DT) siguen igual que antes.
--
--   1. Al aceptarse el desafío, cada entrenador ve el partido como «Alineación pendiente».
--   2. Cada uno arma SOLO su alineación y la ENVÍA. Puede editarla hasta 1 hora antes del
--      partido; si la cambia después de enviarla, debe volver a enviarla.
--   3. Mientras no se confirme, nadie ve la alineación del otro lado (ni el rival ni el público).
--   4. A 1 hora del partido se CIERRAN y CONFIRMAN las dos. Si un lado no está completo, el
--      servidor la AUTOCOMPLETA: primero con su banca y con los convocados que confirmaron,
--      después con el resto del plantel activo (nunca con quien respondió «no asiste»), y
--      siempre con 1 arquero.
--   5. Al confirmarse se avisa a los entrenadores, apoderados, jugadores y seguidores de los dos
--      equipos. Desde ese momento el partido aparece como «Próximo» para ellos, con alineaciones.
--   6. Recién con las alineaciones confirmadas se puede generar el QR del árbitro e iniciar.
--
-- El cierre a la hora exacta lo hace una tarea programada (pg_cron, cada minuto). Si pg_cron
-- no está activado, el cierre ocurre igual en cuanto alguien abre el partido, pide el QR o
-- intenta iniciarlo; solo que los avisos llegan en ese momento y no a la hora exacta.
--
-- También se bloquea cambiar fecha, formato o minutos de un partido acordado: son lo que
-- aceptó el otro entrenador.
--
-- Cómo ejecutarla: Supabase Dashboard → SQL Editor → pegar TODO → Run (transaccional).
-- =====================================================================
begin;

-- ---------------------------------------------------------------------
-- 1. Columnas y tabla nuevas
-- ---------------------------------------------------------------------
-- La app no puede escribir estas columnas: los permisos de matches son por columna.
alter table public.matches add column if not exists lineup_flow boolean not null default false;
alter table public.matches add column if not exists lineups_confirmed_at timestamptz;
alter table public.match_lineup_players add column if not exists auto_filled boolean not null default false;

-- Partidos que ya existían por desafío aceptado
update public.matches m set lineup_flow = true
 where not m.lineup_flow
   and exists (select 1 from public.match_challenges c where c.match_id = m.id);

-- Envío de la alineación de cada lado (si no hay fila, ese lado no la envió)
create table if not exists public.match_lineup_submissions (
  match_id     uuid not null references public.matches(id) on delete cascade,
  team_side    text not null check (team_side in ('home','away')),
  submitted_by uuid references public.profiles(id),
  submitted_at timestamptz not null default now(),
  primary key (match_id, team_side)
);
alter table public.match_lineup_submissions enable row level security;
revoke all on public.match_lineup_submissions from anon, authenticated;
grant select on public.match_lineup_submissions to authenticated;

-- ---------------------------------------------------------------------
-- 2. Funciones auxiliares (internas)
-- ---------------------------------------------------------------------
-- Equipo de un lado del partido
create or replace function public._match_side_team(p_match uuid, p_side text)
returns uuid language sql stable security definer set search_path = public as $fn$
  select case p_side when 'home' then home_team_id when 'away' then away_team_id end
  from public.matches where id = p_match
$fn$;

-- ¿Gestiono el equipo de ese lado?
create or replace function public.is_side_manager(p_match uuid, p_side text)
returns boolean language sql stable security definer set search_path = public as $fn$
  select coalesce(public.is_team_manager(public._match_side_team(p_match, p_side)), false)
$fn$;

-- ¿Puedo escribir la alineación de ese lado? En los partidos acordados, solo su entrenador.
-- En los demás, como siempre: también el DT del equipo local (por ejemplo, para un equipo
-- de su academia que no tiene DT).
create or replace function public.can_write_lineup_side(p_match uuid, p_side text)
returns boolean language sql stable security definer set search_path = public as $fn$
  select public.is_side_manager(p_match, p_side)
      or exists (select 1 from public.matches m where m.id = p_match and not m.lineup_flow
                 and public.is_match_manager(p_match))
$fn$;

-- ¿Puedo ver la alineación de ese lado? Siempre en partidos sin este flujo; en los acordados,
-- solo quien gestiona ese lado hasta que se confirman.
create or replace function public.can_view_lineup_side(p_match uuid, p_side text)
returns boolean language sql stable security definer set search_path = public as $fn$
  select public.can_view_match(p_match) and exists (
    select 1 from public.matches m where m.id = p_match
      and (not m.lineup_flow or m.lineups_confirmed_at is not null or public.is_side_manager(p_match, p_side)))
$fn$;

drop policy if exists match_lineup_submissions_select on public.match_lineup_submissions;
create policy match_lineup_submissions_select on public.match_lineup_submissions for select to authenticated
  using (public.is_side_manager(match_id, 'home') or public.is_side_manager(match_id, 'away'));

-- Las alineaciones de desafío y los avisos van en la categoría «Convocatorias»
create or replace function public._notification_category(p_kind text)
returns text language sql immutable set search_path = public as $fn$
  select case
    when p_kind = 'callup' or p_kind like 'match\_lineup%'   then 'callups'
    when p_kind = 'match_live'                               then 'live'
    when p_kind like 'post\_comment%'                        then 'comments'
    when p_kind like 'fee\_%'                                then 'payments'
    when p_kind like 'guardian\_%' or p_kind like 'team\_join\_%'
      or p_kind like 'document\_%' or p_kind like 'tryout\_%'
      or p_kind like 'match\_challenge%'
      or p_kind = 'invite_used'                              then 'requests'
    else null end
$fn$;

-- ---------------------------------------------------------------------
-- 3. Al aceptar un desafío, el partido entra en este flujo
-- ---------------------------------------------------------------------
create or replace function public.trg_challenge_match_flow()
returns trigger language plpgsql security definer set search_path = public as $fn$
begin
  if new.match_id is not null and new.match_id is distinct from old.match_id then
    update public.matches set lineup_flow = true where id = new.match_id;
  end if;
  return new;
end $fn$;
drop trigger if exists trg_challenge_match_flow on public.match_challenges;
create trigger trg_challenge_match_flow after update of match_id on public.match_challenges
  for each row execute function public.trg_challenge_match_flow();

-- ---------------------------------------------------------------------
-- 4. Reglas sobre la alineación (plazo, confirmación, envío)
-- ---------------------------------------------------------------------
-- 4.1 La validación original, con una excepción: el autocompletado del servidor puede
--     alinear a quien no confirmó (nunca a quien dijo «no asiste»).
create or replace function public.trg_lineup_validate()
returns trigger language plpgsql security definer set search_path = public as $fn$
declare m public.matches%rowtype; v_team uuid; v_auto boolean := coalesce(current_setting('app.lineup_autofill', true), '') = 'on';
begin
  select * into m from public.matches where id = new.match_id;
  if m.status <> 'scheduled' then raise exception 'La alineación solo se edita antes de iniciar el partido'; end if;
  v_team := case new.team_side when 'home' then m.home_team_id else m.away_team_id end;
  if v_team is null then raise exception 'El rival es externo: use el plantel manual'; end if;
  if not exists (select 1 from public.team_players tp where tp.team_id = v_team and tp.player_id = new.player_id and tp.status = 'active') then
    raise exception 'El jugador no pertenece al equipo seleccionado';
  end if;
  if v_auto then
    if exists (select 1 from public.match_callups c where c.match_id = new.match_id and c.player_id = new.player_id and c.status = 'declined') then
      raise exception 'No se alinea a un jugador que avisó que no asiste';
    end if;
  elsif not exists (select 1 from public.match_callups c where c.match_id = new.match_id and c.player_id = new.player_id and c.status = 'confirmed') then
    raise exception 'Solo pueden alinearse jugadores que confirmaron su asistencia';
  end if;
  return new;
end $fn$;

-- 4.2 Plazo y confirmación. Cualquier cambio a mano borra el «enviado» de ese lado.
create or replace function public.trg_lineup_flow()
returns trigger language plpgsql security definer set search_path = public as $fn$
declare r record; m public.matches%rowtype; v_auto boolean := coalesce(current_setting('app.lineup_autofill', true), '') = 'on';
begin
  if tg_op = 'DELETE' then r := old; else r := new; end if;
  select * into m from public.matches where id = r.match_id;
  if m.lineup_flow and not v_auto then
    if m.lineups_confirmed_at is not null then
      raise exception 'Las alineaciones ya están confirmadas y no se pueden cambiar';
    end if;
    if m.scheduled_at <= now() + interval '1 hour' then
      raise exception 'El plazo para editar la alineación venció (1 hora antes del partido)';
    end if;
    delete from public.match_lineup_submissions where match_id = r.match_id and team_side = r.team_side;
  end if;
  if tg_op = 'DELETE' then return old; end if;
  -- Solo el servidor marca una fila como autocompletada
  if not v_auto then new.auto_filled := false; end if;
  return new;
end $fn$;
drop trigger if exists trg_lineup_flow on public.match_lineup_players;
create trigger trg_lineup_flow before insert or update or delete on public.match_lineup_players
  for each row execute function public.trg_lineup_flow();

-- 4.3 En un partido acordado cada entrenador escribe SOLO su lado
drop policy if exists lineup_write on public.match_lineup_players;
create policy lineup_write on public.match_lineup_players for all to authenticated
  using (public.can_write_lineup_side(match_id, team_side))
  with check (public.can_write_lineup_side(match_id, team_side));

-- 4.4 Nadie ve la alineación del otro lado antes de la confirmación
drop policy if exists lineup_select on public.match_lineup_players;
create policy lineup_select on public.match_lineup_players for select to authenticated
  using (public.can_view_lineup_side(match_id, team_side));

create or replace function public.get_match_lineup(p_match uuid)
returns table (team_side text, player_id uuid, external_player_id uuid, player_name text,
               jersey_number smallint, is_starter boolean, field_position text)
language sql stable security definer set search_path = public as $fn$
  select l.team_side, l.player_id, null::uuid, public.player_display_name(l.player_id),
         coalesce(l.jersey_number, tp.jersey_number),
         l.is_starter, coalesce(l.field_position, tp.position)
  from public.match_lineup_players l
  join public.matches mm on mm.id = l.match_id
  left join public.team_players tp
         on tp.player_id = l.player_id
        and tp.team_id = case l.team_side when 'home' then mm.home_team_id else mm.away_team_id end
  where l.match_id = p_match and public.can_view_lineup_side(p_match, l.team_side)
  union all
  select 'away', null::uuid, x.id, x.name, x.jersey_number, x.is_starter, x.field_position
  from public.match_external_players x
  where x.match_id = p_match and public.can_view_match(p_match)
$fn$;

-- 4.5 Lo acordado en el desafío no se cambia por un solo lado
create or replace function public.trg_matches_agreed_settings()
returns trigger language plpgsql security definer set search_path = public as $fn$
begin
  if old.lineup_flow and auth.uid() is not null
     and (new.scheduled_at is distinct from old.scheduled_at
          or new.format_players is distinct from old.format_players
          or new.half_minutes is distinct from old.half_minutes) then
    raise exception 'La fecha, el formato y los minutos los acordaron los dos entrenadores: no se cambian';
  end if;
  -- No se inicia un partido acordado sin las alineaciones confirmadas
  if old.status = 'scheduled' and new.status = 'live' and old.lineup_flow and old.lineups_confirmed_at is null then
    raise exception 'Las alineaciones se confirman 1 hora antes del partido; todavía no se puede iniciar';
  end if;
  return new;
end $fn$;
drop trigger if exists trg_matches_agreed_settings on public.matches;
create trigger trg_matches_agreed_settings before update on public.matches
  for each row execute function public.trg_matches_agreed_settings();

-- ---------------------------------------------------------------------
-- 5. Autocompletar y confirmar
-- ---------------------------------------------------------------------
-- Completa un lado: titulares hasta el formato, con exactamente 1 arquero.
-- Devuelve cuántos titulares FALTAN después de intentarlo (0 = completa).
create or replace function public._lineup_autofill_side(p_match uuid, p_side text)
returns int language plpgsql security definer set search_path = public as $fn$
declare
  m public.matches%rowtype; v_team uuid; v_starters int; v_gk int; c record; v_pos text;
begin
  select * into m from public.matches where id = p_match;
  v_team := case p_side when 'home' then m.home_team_id else m.away_team_id end;
  if v_team is null then return 0; end if;
  perform set_config('app.lineup_autofill', 'on', true);

  -- a) Arquero: si no hay titular en el arco, se elige uno
  select count(*) into v_gk from public.match_lineup_players
   where match_id = p_match and team_side = p_side and is_starter and field_position = 'POR';
  if v_gk = 0 then
    select count(*) into v_starters from public.match_lineup_players
     where match_id = p_match and team_side = p_side and is_starter;
    if v_starters >= m.format_players then
      -- Titulares completos sin arquero: el que juega de arquero habitualmente (o el primero) va al arco
      update public.match_lineup_players l set field_position = 'POR', auto_filled = true
       where l.id = (select l2.id from public.match_lineup_players l2
                     left join public.team_players tp on tp.team_id = v_team and tp.player_id = l2.player_id
                     where l2.match_id = p_match and l2.team_side = p_side and l2.is_starter
                     order by (tp.position = 'POR') desc nulls last, coalesce(l2.jersey_number, tp.jersey_number) nulls last
                     limit 1);
    else
      select * into c from public._lineup_candidates(p_match, p_side) order by gk_first, prio, jersey nulls last limit 1;
      if c.player_id is not null then
        if c.in_lineup then
          update public.match_lineup_players set is_starter = true, field_position = 'POR', auto_filled = true
           where match_id = p_match and player_id = c.player_id;
        else
          insert into public.match_lineup_players (match_id, team_side, player_id, is_starter, field_position, jersey_number, auto_filled)
          values (p_match, p_side, c.player_id, true, 'POR', c.jersey, true);
        end if;
      end if;
    end if;
  end if;

  -- b) Resto de titulares
  loop
    select count(*) into v_starters from public.match_lineup_players
     where match_id = p_match and team_side = p_side and is_starter;
    exit when v_starters >= m.format_players;
    select * into c from public._lineup_candidates(p_match, p_side) order by prio, jersey nulls last limit 1;
    exit when c.player_id is null;
    -- Ya hay arquero: nadie más va al arco; sin posición habitual juega de medio
    v_pos := case when c.usual_position is null or c.usual_position = 'POR' then 'MED' else c.usual_position end;
    if c.in_lineup then
      update public.match_lineup_players set is_starter = true, field_position = v_pos, auto_filled = true
       where match_id = p_match and player_id = c.player_id;
    else
      insert into public.match_lineup_players (match_id, team_side, player_id, is_starter, field_position, jersey_number, auto_filled)
      values (p_match, p_side, c.player_id, true, v_pos, c.jersey, true);
    end if;
  end loop;

  -- c) Los que confirmaron y no quedaron en la alineación entran como suplentes
  insert into public.match_lineup_players (match_id, team_side, player_id, is_starter, field_position, jersey_number, auto_filled)
  select p_match, p_side, cu.player_id, false, tp.position, tp.jersey_number, true
  from public.match_callups cu
  join public.team_players tp on tp.team_id = v_team and tp.player_id = cu.player_id and tp.status = 'active'
  where cu.match_id = p_match and cu.team_id = v_team and cu.status = 'confirmed'
    and not exists (select 1 from public.match_lineup_players l where l.match_id = p_match and l.player_id = cu.player_id);

  perform set_config('app.lineup_autofill', 'off', true);
  select count(*) into v_starters from public.match_lineup_players
   where match_id = p_match and team_side = p_side and is_starter;
  return greatest(m.format_players - v_starters, 0);
end $fn$;

-- Candidatos para completar un lado, en orden de preferencia:
--   prio 1: suplentes ya elegidos por el DT; 2: convocados que confirmaron;
--   3: convocados que no respondieron; 4: resto del plantel activo.
-- Nunca quien avisó que no asiste, ni quien ya es titular.
create or replace function public._lineup_candidates(p_match uuid, p_side text)
returns table (player_id uuid, in_lineup boolean, prio int, gk_first int, jersey smallint, usual_position text)
language sql stable security definer set search_path = public as $fn$
  select tp.player_id,
         l.id is not null,
         case when l.id is not null then 1
              when cu.status = 'confirmed' then 2
              when cu.status = 'pending' then 3
              else 4 end,
         case when tp.position = 'POR' then 0 else 1 end,
         tp.jersey_number,
         tp.position
  from public.team_players tp
  left join public.match_lineup_players l on l.match_id = p_match and l.player_id = tp.player_id
  left join public.match_callups cu on cu.match_id = p_match and cu.player_id = tp.player_id
  where tp.team_id = public._match_side_team(p_match, p_side)
    and tp.status = 'active'
    and coalesce(cu.status, '') <> 'declined'
    and (l.id is null or not l.is_starter)
$fn$;

-- Cierra y confirma las alineaciones de un partido acordado, si ya es la hora (o si p_force).
-- Devuelve true si confirmó en esta llamada.
create or replace function public._lineups_lock(p_match uuid, p_force boolean default false)
returns boolean language plpgsql security definer set search_path = public as $fn$
declare
  m public.matches%rowtype; v_home text; v_away text; v_missing_home int; v_missing_away int;
  v_when text; v_body text; r record;
begin
  select * into m from public.matches where id = p_match for update;
  if not found or not m.lineup_flow or m.lineups_confirmed_at is not null or m.status <> 'scheduled' then
    return false;
  end if;
  if not p_force and m.scheduled_at > now() + interval '1 hour' then return false; end if;

  select name into v_home from public.teams where id = m.home_team_id;
  select name into v_away from public.teams where id = m.away_team_id;
  v_missing_home := public._lineup_autofill_side(p_match, 'home');
  v_missing_away := public._lineup_autofill_side(p_match, 'away');
  update public.matches set lineups_confirmed_at = now() where id = p_match;

  v_when := to_char(m.scheduled_at at time zone 'America/Lima', 'DD/MM/YYYY "a las" HH24:MI');
  v_body := v_home || ' vs ' || v_away || ' · ' || v_when || coalesce(' · ' || nullif(trim(m.venue), ''), '')
            || '. Ya puedes ver las alineaciones.';

  -- Avisos: entrenadores, apoderados y jugadores de los alineados, y seguidores (sin repetir)
  for r in
    select distinct u.user_id from (
      select ts.user_id from public.team_staff ts where ts.team_id in (m.home_team_id, m.away_team_id)
      union
      select am.user_id from public.academy_memberships am
       where am.role = 'adm' and am.status = 'active'
         and am.academy_id in (public.team_academy(m.home_team_id), public.team_academy(m.away_team_id))
      union
      select g.guardian_user_id from public.match_lineup_players l
        join public.guardian_player_links g on g.player_id = l.player_id and g.status = 'approved'
       where l.match_id = p_match
      union
      select pl.user_id from public.match_lineup_players l join public.players pl on pl.id = l.player_id
       where l.match_id = p_match and pl.user_id is not null
      union
      select f.user_id from public.follows f
       where f.team_id in (m.home_team_id, m.away_team_id)
          or f.academy_id in (public.team_academy(m.home_team_id), public.team_academy(m.away_team_id))
    ) u where u.user_id is not null
  loop
    perform public._notify(r.user_id, 'match_lineups', 'Alineaciones confirmadas', v_body,
      jsonb_build_object('match_id', p_match));
  end loop;

  -- Aviso aparte al entrenador de un lado que se autocompletó o quedó incompleto
  if exists (select 1 from public.match_lineup_players where match_id = p_match and team_side = 'home' and auto_filled)
     or v_missing_home > 0 then
    perform public._challenge_notify_team(m.home_team_id, 'match_lineup_autofilled', 'Alineación completada por el sistema',
      case when v_missing_home > 0
           then 'No alcanzan los jugadores disponibles de ' || v_home || ': faltan ' || v_missing_home || ' titulares.'
           else 'La alineación de ' || v_home || ' se completó sola porque no estaba lista 1 hora antes.' end,
      jsonb_build_object('match_id', p_match));
  end if;
  if exists (select 1 from public.match_lineup_players where match_id = p_match and team_side = 'away' and auto_filled)
     or v_missing_away > 0 then
    perform public._challenge_notify_team(m.away_team_id, 'match_lineup_autofilled', 'Alineación completada por el sistema',
      case when v_missing_away > 0
           then 'No alcanzan los jugadores disponibles de ' || v_away || ': faltan ' || v_missing_away || ' titulares.'
           else 'La alineación de ' || v_away || ' se completó sola porque no estaba lista 1 hora antes.' end,
      jsonb_build_object('match_id', p_match));
  end if;

  perform public.log_audit('match_lineups_confirmed', 'match', p_match,
    jsonb_build_object('missing_home', v_missing_home, 'missing_away', v_missing_away));
  return true;
end $fn$;

-- Tarea programada: confirma todos los partidos que llegaron a su hora límite
create or replace function public._lineups_lock_due()
returns int language plpgsql security definer set search_path = public as $fn$
declare r record; v_n int := 0;
begin
  for r in select id from public.matches
            where lineup_flow and lineups_confirmed_at is null and status = 'scheduled'
              and scheduled_at <= now() + interval '1 hour'
  loop
    if public._lineups_lock(r.id) then v_n := v_n + 1; end if;
  end loop;
  return v_n;
end $fn$;

-- ---------------------------------------------------------------------
-- 6. Funciones para la app
-- ---------------------------------------------------------------------
-- 6.1 Estado de las alineaciones de un partido (y cierre perezoso si ya es la hora)
create or replace function public.lineup_status(p_match uuid)
returns jsonb language plpgsql security definer set search_path = public as $fn$
declare m public.matches%rowtype;
begin
  if not public.can_view_match(p_match) then raise exception 'No tienes acceso a este partido'; end if;
  perform public._lineups_lock(p_match);
  select * into m from public.matches where id = p_match;
  return jsonb_build_object(
    'flow', m.lineup_flow,
    'deadline', case when m.lineup_flow then m.scheduled_at - interval '1 hour' end,
    'confirmed_at', m.lineups_confirmed_at,
    'sides', (select jsonb_agg(jsonb_build_object(
                'side', s.side,
                'can_edit', public.is_side_manager(p_match, s.side),
                'submitted_at', (select submitted_at from public.match_lineup_submissions x where x.match_id = p_match and x.team_side = s.side),
                'auto_filled', exists (select 1 from public.match_lineup_players l where l.match_id = p_match and l.team_side = s.side and l.auto_filled))
              order by s.side desc)
              from (values ('home'), ('away')) s(side)
              where s.side = 'home' or m.away_team_id is not null));
end $fn$;

-- 6.2 Enviar mi alineación (completa y con 1 arquero). Avisa al otro entrenador.
create or replace function public.lineup_submit(p_match uuid, p_side text)
returns void language plpgsql security definer set search_path = public as $fn$
declare m public.matches%rowtype; v_n int; v_gk int; v_team text; v_other text;
begin
  select * into m from public.matches where id = p_match;
  if not found then raise exception 'Partido inexistente'; end if;
  if p_side not in ('home', 'away') then raise exception 'Lado inválido'; end if;
  if not public.is_side_manager(p_match, p_side) then raise exception 'Solo el entrenador de ese equipo envía su alineación'; end if;
  if not m.lineup_flow then raise exception 'Este partido no necesita enviar la alineación'; end if;
  if m.lineups_confirmed_at is not null then raise exception 'Las alineaciones ya están confirmadas'; end if;
  if m.scheduled_at <= now() + interval '1 hour' then raise exception 'El plazo para enviar la alineación venció (1 hora antes del partido)'; end if;
  select count(*), count(*) filter (where field_position = 'POR') into v_n, v_gk
    from public.match_lineup_players where match_id = p_match and team_side = p_side and is_starter;
  if v_n <> m.format_players then
    raise exception 'Faltan titulares: se requieren % (hay %)', m.format_players, v_n;
  end if;
  if v_gk <> 1 then raise exception 'Debe haber exactamente 1 arquero titular (hay %)', v_gk; end if;

  insert into public.match_lineup_submissions (match_id, team_side, submitted_by)
  values (p_match, p_side, auth.uid())
  on conflict (match_id, team_side) do update set submitted_by = excluded.submitted_by, submitted_at = now();

  select name into v_team from public.teams where id = public._match_side_team(p_match, p_side);
  v_other := case p_side when 'home' then 'away' else 'home' end;
  perform public._challenge_notify_team(public._match_side_team(p_match, v_other), 'match_lineup_submitted',
    'El rival envió su alineación',
    v_team || ' ya envió su alineación. Se publican las dos 1 hora antes del partido.',
    jsonb_build_object('match_id', p_match));
  perform public.log_audit('match_lineup_submitted', 'match', p_match, jsonb_build_object('side', p_side));
end $fn$;

-- 6.3 Antes de iniciar (app o árbitro), si ya es la hora se confirman las alineaciones
create or replace function public.change_match_state(p_match uuid, p_action text)
returns void language plpgsql security definer set search_path = public as $fn$
begin
  if not exists (select 1 from public.matches where id = p_match) then raise exception 'Partido inexistente'; end if;
  if not (public.is_match_manager(p_match) or public.is_match_operator(p_match)) then
    raise exception 'Sin permiso para controlar este partido';
  end if;
  perform public._lineups_lock(p_match);
  perform public._match_apply_state(p_match, p_action);
  perform public.log_audit('match_state_' || p_action, 'match', p_match, '{}'::jsonb);
end $fn$;

create or replace function public.referee_change_state(p_token text, p_action text)
returns void language plpgsql security definer set search_path = public as $fn$
declare l public.match_referee_links;
begin
  l := public._referee_link_or_fail(p_token);
  perform public._lineups_lock(l.match_id);
  perform public._match_apply_state(l.match_id, p_action);
  perform public.log_audit('match_state_' || p_action, 'match', l.match_id,
    jsonb_build_object('via', 'referee_link', 'referee_link_id', l.id));
end $fn$;

-- 6.4 El QR del árbitro de un partido acordado, solo con las alineaciones confirmadas
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
  perform public._lineups_lock(p_match);
  if m.lineup_flow and not exists (select 1 from public.matches where id = p_match and lineups_confirmed_at is not null) then
    raise exception 'El QR del árbitro se genera cuando se confirman las alineaciones (1 hora antes del partido)';
  end if;

  if not coalesce(p_regenerate, false) then
    select token into v_token from public.match_referee_links where match_id = p_match and revoked_at is null;
    if v_token is not null then return v_token; end if;
  end if;

  update public.match_referee_links
     set revoked_at = now(), revoked_by = auth.uid(), revoke_reason = 'Reemplazado por un código nuevo'
   where match_id = p_match and revoked_at is null;
  v_token := replace(gen_random_uuid()::text, '-', '') || replace(gen_random_uuid()::text, '-', '');
  insert into public.match_referee_links (match_id, token, created_by) values (p_match, v_token, auth.uid());
  perform public.log_audit('referee_link_created', 'match', p_match, jsonb_build_object('regenerated', coalesce(p_regenerate, false)));
  return v_token;
end $fn$;

-- 6.5 «Mis partidos»: un partido acordado solo lo ven sus entrenadores hasta que se
--     confirman las alineaciones. Después aparece para familias, jugadores y seguidores.
create or replace function public.my_matches()
returns table (match_id uuid, is_favorite boolean, by_follow boolean, by_child boolean,
               by_staff boolean, by_player boolean)
language sql stable security definer set search_path = public as $fn$
  with scoped as (
    select m.id, m.scheduled_at, m.created_at,
      (not m.lineup_flow or m.lineups_confirmed_at is not null) as published,
      exists (select 1 from public.match_favorites mf
              where mf.user_id = auth.uid() and mf.match_id = m.id) as is_favorite,
      exists (select 1 from public.follows f
              where f.user_id = auth.uid()
                and (f.team_id in (m.home_team_id, m.away_team_id)
                     or f.academy_id in (m.academy_id, public.team_academy(m.away_team_id)))) as by_follow,
      exists (select 1 from public.team_players tp
              join public.guardian_player_links g on g.player_id = tp.player_id
              where tp.status = 'active' and tp.team_id in (m.home_team_id, m.away_team_id)
                and g.guardian_user_id = auth.uid() and g.status = 'approved') as by_child,
      (exists (select 1 from public.team_staff ts
               where ts.user_id = auth.uid() and ts.team_id in (m.home_team_id, m.away_team_id))
       or exists (select 1 from public.academy_memberships am
                  where am.user_id = auth.uid() and am.role = 'adm' and am.status = 'active'
                    and am.academy_id in (m.academy_id, public.team_academy(m.away_team_id)))
       or exists (select 1 from public.match_operators mo
                  where mo.match_id = m.id and mo.user_id = auth.uid() and mo.active)) as by_staff,
      exists (select 1 from public.team_players tp
              join public.players pl on pl.id = tp.player_id
              where tp.status = 'active' and tp.team_id in (m.home_team_id, m.away_team_id)
                and pl.user_id = auth.uid()) as by_player
    from public.matches m
  )
  select s.id, s.is_favorite, s.by_follow, s.by_child, s.by_staff, s.by_player
  from scoped s
  where (s.by_staff or (s.published and (s.is_favorite or s.by_follow or s.by_child or s.by_player)))
    and public.can_view_match(s.id)
  order by s.scheduled_at desc nulls last, s.created_at desc
  limit 100
$fn$;

-- ---------------------------------------------------------------------
-- 7. Tarea programada (pg_cron), si está activado
-- ---------------------------------------------------------------------
do $do$
begin
  if exists (select 1 from pg_extension where extname = 'pg_cron') then
    perform cron.unschedule(jobid) from cron.job where jobname = 'sportpro-lineups-lock';
    perform cron.schedule('sportpro-lineups-lock', '* * * * *', 'select public._lineups_lock_due()');
    raise notice 'Tarea sportpro-lineups-lock programada cada minuto';
  else
    raise notice 'pg_cron no está activado: las alineaciones se confirmarán al abrir el partido. Actívalo en Database → Extensions y vuelve a ejecutar esta migración.';
  end if;
end $do$;

-- ---------------------------------------------------------------------
-- 8. Permisos de ejecución
-- ---------------------------------------------------------------------
revoke execute on function public._match_side_team(uuid, text), public._lineup_autofill_side(uuid, text),
  public._lineup_candidates(uuid, text), public._lineups_lock(uuid, boolean), public._lineups_lock_due(),
  public.trg_challenge_match_flow(), public.trg_lineup_flow(), public.trg_matches_agreed_settings(),
  public.trg_lineup_validate()
  from public, anon, authenticated;
-- Las usan las políticas de fila: las ejecuta el usuario con sesión, nunca el anónimo
revoke execute on function public.is_side_manager(uuid, text), public.can_view_lineup_side(uuid, text),
  public.can_write_lineup_side(uuid, text),
  public.lineup_status(uuid), public.lineup_submit(uuid, text), public.get_match_lineup(uuid) from public, anon;
grant execute on function public.is_side_manager(uuid, text), public.can_view_lineup_side(uuid, text),
  public.can_write_lineup_side(uuid, text),
  public.lineup_status(uuid), public.lineup_submit(uuid, text), public.get_match_lineup(uuid) to authenticated;

commit;
