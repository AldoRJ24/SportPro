-- =====================================================================
-- SportPro · Migración 015: DESAFÍOS entre equipos registrados en la app.
--
-- Hasta ahora un DT podía crear un partido contra un equipo de OTRA academia sin que el otro
-- entrenador lo aceptara. Ahora ese partido nace como un «desafío»:
--
--   1. El DT elige su equipo (local), la academia rival y uno de sus equipos (visitante).
--      Regla de DIVISIÓN: solo se puede desafiar a un equipo cuya edad máxima esté entre
--      -1 y +1 de la propia (un Sub-14 juega contra Sub-13, Sub-14 o Sub-15).
--   2. Define jugadores por lado (5 a 11), minutos por tiempo, fecha, hora y sede.
--   3. El entrenador rival recibe una notificación con todos los ajustes y ACEPTA o RECHAZA.
--   4. Al aceptar se crea el partido (programado) y los dos entrenadores lo ven en «Partidos».
--
-- Nada se borra: un desafío rechazado, cancelado o vencido se conserva con su estado.
-- Todas las reglas se validan aquí; la app solo las repite para dar mensajes claros.
--
-- Además se cierra el camino anterior: la política de inserción de «matches» ya no permite
-- crear directo un partido contra un equipo registrado que uno no gestiona.
--
-- Cómo ejecutarla: Supabase Dashboard → SQL Editor → pegar TODO → Run (transaccional).
-- =====================================================================
begin;

-- ---------------------------------------------------------------------
-- 1. Tabla
-- ---------------------------------------------------------------------
create table if not exists public.match_challenges (
  id              uuid primary key default gen_random_uuid(),
  home_team_id    uuid not null references public.teams(id) on delete cascade,   -- quien desafía (local)
  away_team_id    uuid not null references public.teams(id) on delete cascade,   -- desafiado (visitante)
  format_players  smallint not null check (format_players between 5 and 11),
  half_minutes    smallint not null check (half_minutes between 5 and 60),
  scheduled_at    timestamptz not null,
  venue           text check (venue is null or char_length(venue) <= 120),
  visibility      text not null default 'followers' check (visibility in ('public','followers','private')),
  status          text not null default 'pending'
                  check (status in ('pending','accepted','rejected','cancelled','expired')),
  created_by      uuid not null references public.profiles(id),
  created_at      timestamptz not null default now(),
  decided_by      uuid references public.profiles(id),
  decided_at      timestamptz,
  decision_reason text,
  match_id        uuid references public.matches(id) on delete set null,          -- partido creado al aceptar
  constraint match_challenges_distinct_teams check (home_team_id <> away_team_id)
);
create index if not exists match_challenges_home on public.match_challenges (home_team_id, created_at desc);
create index if not exists match_challenges_away on public.match_challenges (away_team_id, created_at desc);
-- Un mismo equipo no puede tener dos desafíos pendientes contra el mismo rival
create unique index if not exists match_challenges_one_pending
  on public.match_challenges (home_team_id, away_team_id) where status = 'pending';

alter table public.match_challenges enable row level security;
revoke all on public.match_challenges from anon, authenticated;
grant select on public.match_challenges to authenticated;

-- Lo ven quienes gestionan cualquiera de los dos equipos
drop policy if exists match_challenges_select on public.match_challenges;
create policy match_challenges_select on public.match_challenges for select to authenticated
  using (public.is_team_manager(home_team_id) or public.is_team_manager(away_team_id));

-- ---------------------------------------------------------------------
-- 2. Funciones auxiliares (internas)
-- ---------------------------------------------------------------------
-- Regla de división: edades máximas a 1 de distancia. Un equipo sin edad máxima (categoría
-- libre) solo juega contra otro sin edad máxima.
create or replace function public._division_ok(p_a smallint, p_b smallint)
returns boolean language sql immutable set search_path = public as $fn$
  select case when p_a is null and p_b is null then true
              when p_a is null or p_b is null then false
              else abs(p_a - p_b) <= 1 end
$fn$;

-- Nombre del entrenador del equipo (el DT más antiguo). Un entrenador es un adulto con cuenta.
create or replace function public._team_coach_name(p_team uuid)
returns text language sql stable security definer set search_path = public as $fn$
  select pr.full_name
  from public.team_staff ts join public.profiles pr on pr.id = ts.user_id
  where ts.team_id = p_team and ts.staff_role = 'dt'
  order by ts.created_at limit 1
$fn$;

-- Texto con los ajustes del partido, para las notificaciones
create or replace function public._challenge_summary(c public.match_challenges)
returns text language sql stable security definer set search_path = public as $fn$
  select c.format_players || ' vs ' || c.format_players
      || ' · ' || c.half_minutes || ' min por tiempo · '
      || to_char(c.scheduled_at at time zone 'America/Lima', 'DD/MM/YYYY "a las" HH24:MI')
      || coalesce(' · ' || nullif(trim(c.venue), ''), '')
$fn$;

-- Avisa a quienes deciden por un equipo: sus DT; si no tiene, los administradores de su academia.
-- Nunca avisa a quien hizo la acción.
create or replace function public._challenge_notify_team(p_team uuid, p_kind text, p_title text, p_body text, p_data jsonb)
returns void language plpgsql security definer set search_path = public as $fn$
begin
  if exists (select 1 from public.team_staff where team_id = p_team) then
    perform public._notify(ts.user_id, p_kind, p_title, p_body, p_data)
    from public.team_staff ts
    where ts.team_id = p_team and ts.user_id is distinct from auth.uid();
  else
    perform public._notify(m.user_id, p_kind, p_title, p_body, p_data)
    from public.academy_memberships m
    where m.academy_id = public.team_academy(p_team) and m.role = 'adm' and m.status = 'active'
      and m.user_id is distinct from auth.uid();
  end if;
end $fn$;

-- Los avisos de desafíos entran en la categoría «solicitudes» de los ajustes de notificaciones
create or replace function public._notification_category(p_kind text)
returns text language sql immutable set search_path = public as $fn$
  select case
    when p_kind = 'callup'                                   then 'callups'
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
-- 3. Funciones para la app
-- ---------------------------------------------------------------------
-- 3.1 Equipos de una academia como posibles rivales de MI equipo: con el nombre del
--     entrenador y si se pueden desafiar (y, si no, por qué).
create or replace function public.challenge_rival_teams(p_home_team uuid, p_academy uuid)
returns table (team_id uuid, name text, category text, max_age smallint, coach_name text,
               eligible boolean, reason text)
language plpgsql stable security definer set search_path = public as $fn$
declare v_home public.teams%rowtype;
begin
  select * into v_home from public.teams where id = p_home_team;
  if not found then raise exception 'Equipo no encontrado'; end if;
  if not public.is_team_manager(p_home_team) then raise exception 'No gestionas ese equipo'; end if;
  -- Solo academias verificadas (o la propia)
  if not (public.academy_is_verified(p_academy) or p_academy = v_home.academy_id) then
    raise exception 'La academia rival debe estar verificada';
  end if;
  return query
    select t.id, t.name, t.category, t.max_age, public._team_coach_name(t.id),
           public._division_ok(v_home.max_age, t.max_age),
           case when public._division_ok(v_home.max_age, t.max_age) then null
                when v_home.max_age is null or t.max_age is null then 'Categoría sin edad máxima: no se puede comparar'
                else 'Fuera de tu división (solo ' || (v_home.max_age - 1) || ' a ' || (v_home.max_age + 1) || ')' end
    from public.teams t
    where t.academy_id = p_academy and t.id <> p_home_team
    order by public._division_ok(v_home.max_age, t.max_age) desc, t.max_age nulls last, t.name;
end $fn$;

-- 3.2 Enviar un desafío
create or replace function public.challenge_create(
  p_home_team uuid, p_away_team uuid, p_format_players int, p_half_minutes int,
  p_scheduled_at timestamptz, p_venue text default null, p_visibility text default 'followers')
returns uuid language plpgsql security definer set search_path = public as $fn$
declare v_home public.teams%rowtype; v_away public.teams%rowtype; c public.match_challenges%rowtype;
begin
  select * into v_home from public.teams where id = p_home_team;
  if not found then raise exception 'Elige tu equipo'; end if;
  select * into v_away from public.teams where id = p_away_team;
  if not found then raise exception 'Elige el equipo rival'; end if;
  if not public.is_team_manager(p_home_team) then
    raise exception 'Solo el DT o el administrador del equipo puede desafiar';
  end if;
  if p_home_team = p_away_team then raise exception 'Un equipo no puede jugar contra sí mismo'; end if;
  if not (public.academy_is_verified(v_away.academy_id) or v_away.academy_id = v_home.academy_id) then
    raise exception 'La academia rival debe estar verificada';
  end if;
  if not public._division_ok(v_home.max_age, v_away.max_age) then
    raise exception 'Ese equipo está fuera de tu división: solo puedes jugar contra una división arriba o abajo';
  end if;
  if p_format_players is null or p_format_players not between 5 and 11 then
    raise exception 'El formato debe ser de 5 a 11 jugadores por lado';
  end if;
  if p_half_minutes is null or p_half_minutes not between 5 and 60 then
    raise exception 'Cada tiempo debe durar entre 5 y 60 minutos';
  end if;
  -- Las alineaciones se cierran 1 hora antes: el partido debe quedar a más de 1 hora
  if p_scheduled_at is null or p_scheduled_at < now() + interval '1 hour' then
    raise exception 'El partido debe programarse con al menos 1 hora de anticipación';
  end if;
  if coalesce(p_visibility, '') not in ('public','followers','private') then
    raise exception 'Visibilidad no válida';
  end if;
  if exists (select 1 from public.match_challenges x
             where x.home_team_id = p_home_team and x.away_team_id = p_away_team and x.status = 'pending') then
    raise exception 'Ya tienes un desafío pendiente contra ese equipo';
  end if;

  insert into public.match_challenges (home_team_id, away_team_id, format_players, half_minutes,
                                       scheduled_at, venue, visibility, created_by)
  values (p_home_team, p_away_team, p_format_players, p_half_minutes, p_scheduled_at,
          nullif(trim(coalesce(p_venue, '')), ''), p_visibility, auth.uid())
  returning * into c;

  perform public._challenge_notify_team(p_away_team, 'match_challenge', 'Te desafiaron a un partido',
    v_home.name || ' (' || v_home.category || ') quiere jugar contra ' || v_away.name || ': '
      || public._challenge_summary(c) || '. Acepta o rechaza.',
    jsonb_build_object('challenge_id', c.id, 'home_team_id', p_home_team, 'away_team_id', p_away_team));
  perform public.log_audit('match_challenge_created', 'match_challenge', c.id,
    jsonb_build_object('home_team_id', p_home_team, 'away_team_id', p_away_team, 'scheduled_at', p_scheduled_at));
  return c.id;
end $fn$;

-- 3.3 Responder un desafío (lo hace quien gestiona el equipo desafiado).
--     Al aceptar se crea el partido y se devuelve su id; al rechazar devuelve null.
create or replace function public.challenge_respond(p_challenge uuid, p_accept boolean, p_reason text default null)
returns uuid language plpgsql security definer set search_path = public as $fn$
declare c public.match_challenges%rowtype; v_home_name text; v_away_name text; v_match uuid;
begin
  select * into c from public.match_challenges where id = p_challenge for update;
  if not found then raise exception 'Desafío no encontrado'; end if;
  if not public.is_team_manager(c.away_team_id) then
    raise exception 'Solo el entrenador del equipo desafiado puede responder';
  end if;
  if c.status <> 'pending' then raise exception 'Este desafío ya fue respondido'; end if;
  -- Si ya pasó la hora límite (1 hora antes del partido), el desafío venció. No hace falta
  -- guardar ese estado: «vencido» se calcula a partir de la fecha, igual que un cobro vencido.
  if c.scheduled_at < now() + interval '1 hour' then
    raise exception 'Este desafío venció: falta menos de 1 hora para el partido';
  end if;
  select name into v_home_name from public.teams where id = c.home_team_id;
  select name into v_away_name from public.teams where id = c.away_team_id;

  if p_accept then
    insert into public.matches (home_team_id, away_team_id, format_players, half_minutes,
                                scheduled_at, venue, visibility, created_by)
    values (c.home_team_id, c.away_team_id, c.format_players, c.half_minutes,
            c.scheduled_at, c.venue, c.visibility, c.created_by)
    returning id into v_match;
    -- El trigger de inserción anota como creador a quien ejecuta: se corrige al que desafió
    update public.matches set created_by = c.created_by where id = v_match;
    update public.match_challenges
       set status = 'accepted', decided_by = auth.uid(), decided_at = now(), match_id = v_match
     where id = c.id;
    perform public._challenge_notify_team(c.home_team_id, 'match_challenge_accepted', 'Desafío aceptado',
      v_away_name || ' aceptó jugar contra ' || v_home_name || ': ' || public._challenge_summary(c)
        || '. Ya puedes preparar tu alineación.',
      jsonb_build_object('challenge_id', c.id, 'match_id', v_match));
    perform public.log_audit('match_challenge_accepted', 'match_challenge', c.id, jsonb_build_object('match_id', v_match));
    return v_match;
  end if;

  update public.match_challenges
     set status = 'rejected', decided_by = auth.uid(), decided_at = now(),
         decision_reason = nullif(trim(coalesce(p_reason, '')), '')
   where id = c.id;
  perform public._challenge_notify_team(c.home_team_id, 'match_challenge_rejected', 'Desafío rechazado',
    v_away_name || ' no aceptó el partido contra ' || v_home_name
      || coalesce('. Motivo: ' || nullif(trim(coalesce(p_reason, '')), ''), '') || '.',
    jsonb_build_object('challenge_id', c.id));
  perform public.log_audit('match_challenge_rejected', 'match_challenge', c.id,
    jsonb_build_object('reason', nullif(trim(coalesce(p_reason, '')), '')));
  return null;
end $fn$;

-- 3.4 Cancelar un desafío que envié y que aún no responden
create or replace function public.challenge_cancel(p_challenge uuid)
returns void language plpgsql security definer set search_path = public as $fn$
declare c public.match_challenges%rowtype; v_home_name text;
begin
  select * into c from public.match_challenges where id = p_challenge for update;
  if not found then raise exception 'Desafío no encontrado'; end if;
  if not public.is_team_manager(c.home_team_id) then
    raise exception 'Solo quien envió el desafío puede cancelarlo';
  end if;
  if c.status <> 'pending' then raise exception 'Este desafío ya fue respondido'; end if;
  update public.match_challenges
     set status = 'cancelled', decided_by = auth.uid(), decided_at = now() where id = c.id;
  select name into v_home_name from public.teams where id = c.home_team_id;
  perform public._challenge_notify_team(c.away_team_id, 'match_challenge_cancelled', 'Desafío cancelado',
    v_home_name || ' canceló el desafío del ' || to_char(c.scheduled_at at time zone 'America/Lima', 'DD/MM/YYYY') || '.',
    jsonb_build_object('challenge_id', c.id));
  perform public.log_audit('match_challenge_cancelled', 'match_challenge', c.id, '{}'::jsonb);
end $fn$;

-- 3.5 Mis desafíos (recibidos y enviados), del más nuevo al más viejo.
--     «Vencido» se calcula: un pendiente cuya hora límite ya pasó se muestra como vencido.
create or replace function public.my_challenges()
returns table (id uuid, incoming boolean, outgoing boolean, status text,
               home_team_id uuid, home_team_name text, home_category text, home_academy_name text, home_coach_name text,
               away_team_id uuid, away_team_name text, away_category text, away_academy_name text, away_coach_name text,
               format_players smallint, half_minutes smallint, scheduled_at timestamptz, venue text,
               visibility text, decision_reason text, match_id uuid, created_at timestamptz)
language sql stable security definer set search_path = public as $fn$
  select c.id,
         public.is_team_manager(c.away_team_id),
         public.is_team_manager(c.home_team_id),
         case when c.status = 'pending' and c.scheduled_at < now() + interval '1 hour' then 'expired' else c.status end,
         h.id, h.name, h.category, ha.name, public._team_coach_name(h.id),
         a.id, a.name, a.category, aa.name, public._team_coach_name(a.id),
         c.format_players, c.half_minutes, c.scheduled_at, c.venue,
         c.visibility, c.decision_reason, c.match_id, c.created_at
  from public.match_challenges c
  join public.teams h on h.id = c.home_team_id
  join public.academies ha on ha.id = h.academy_id
  join public.teams a on a.id = c.away_team_id
  join public.academies aa on aa.id = a.academy_id
  where public.is_team_manager(c.home_team_id) or public.is_team_manager(c.away_team_id)
  order by (c.status = 'pending' and c.scheduled_at >= now() + interval '1 hour') desc, c.created_at desc
  limit 100
$fn$;

-- ---------------------------------------------------------------------
-- 4. Cierre del camino anterior: crear directo un partido contra un equipo registrado
--    solo se permite si también gestionas ese equipo (por ejemplo, dos equipos tuyos).
--    Contra el equipo de otro entrenador hay que enviar un desafío.
-- ---------------------------------------------------------------------
drop policy if exists matches_insert on public.matches;
create policy matches_insert on public.matches for insert to authenticated
  with check (public.is_team_manager(home_team_id)
    and (away_team_id is null or public.is_team_manager(away_team_id)));

-- ---------------------------------------------------------------------
-- 5. Permisos de ejecución
-- ---------------------------------------------------------------------
revoke execute on function public._division_ok(smallint, smallint), public._team_coach_name(uuid),
  public._challenge_summary(public.match_challenges),
  public._challenge_notify_team(uuid, text, text, text, jsonb)
  from public, anon, authenticated;
revoke execute on function public.challenge_rival_teams(uuid, uuid),
  public.challenge_create(uuid, uuid, int, int, timestamptz, text, text),
  public.challenge_respond(uuid, boolean, text), public.challenge_cancel(uuid), public.my_challenges()
  from public, anon;
grant execute on function public.challenge_rival_teams(uuid, uuid),
  public.challenge_create(uuid, uuid, int, int, timestamptz, text, text),
  public.challenge_respond(uuid, boolean, text), public.challenge_cancel(uuid), public.my_challenges()
  to authenticated;

commit;
