-- =====================================================================
-- SportPro · Migración 021: el administrador de academia propone partidos.
--
--   Hasta ahora un desafío lo enviaba el DT del equipo local y solo respondía el DT del equipo
--   rival. Ahora el ADMINISTRADOR de una academia también puede armar un partido para uno de
--   sus equipos (eligiendo el equipo y, con él, a su entrenador) contra cualquier equipo
--   registrado. Ese partido NO se crea de inmediato: es una propuesta que deben aceptar los
--   DOS entrenadores (el del equipo local y el del rival). Recién cuando ambos aceptan, el
--   servidor crea el partido; si cualquiera rechaza, la propuesta se cierra.
--
--   Cómo se modela: la misma tabla match_challenges, con una aceptación por lado.
--     - home_accepted_at / away_accepted_at: cuándo aceptó cada lado (null = falta).
--     - Quien crea la propuesta y ya decide por un lado (es su DT) deja ese lado aceptado.
--       Un desafío normal de DT queda igual que antes: el local ya aceptó al enviarlo.
--     - «Decide por un equipo» = es su DT (staff). Si el equipo no tiene staff, decide el
--       administrador de su academia (como ya pasaba con los avisos de desafíos).
--
--   Además se cierra el atajo: el administrador que no es DT de un equipo ya no puede crear un
--   partido directo para ese equipo (debe proponerlo para que su DT lo acepte).
--
-- Cómo ejecutarla: Supabase Dashboard → SQL Editor → pegar TODO → Run (transaccional).
-- Es idempotente.
-- =====================================================================
begin;

-- ---------------------------------------------------------------------
-- 1. Aceptación por lado
-- ---------------------------------------------------------------------
alter table public.match_challenges add column if not exists home_accepted_at timestamptz;
alter table public.match_challenges add column if not exists home_accepted_by uuid references public.profiles(id);
alter table public.match_challenges add column if not exists away_accepted_at timestamptz;
alter table public.match_challenges add column if not exists away_accepted_by uuid references public.profiles(id);
-- true = lo propuso un administrador que no era DT del equipo local
alter table public.match_challenges add column if not exists proposed_by_admin boolean not null default false;

-- Desafíos anteriores: el local aceptó al enviarlo; el visitante, si lo aceptó, al decidir
update public.match_challenges
   set home_accepted_at = created_at, home_accepted_by = created_by
 where home_accepted_at is null and not proposed_by_admin;
update public.match_challenges
   set away_accepted_at = decided_at, away_accepted_by = decided_by
 where status = 'accepted' and away_accepted_at is null;

-- ---------------------------------------------------------------------
-- 2. ¿Decido yo por este equipo? (DT del equipo; si no tiene staff, quien lo gestiona)
--    Se usa en una política de fila, por eso no empieza con «_» y la app puede ejecutarla.
-- ---------------------------------------------------------------------
create or replace function public.is_side_decider(p_team uuid)
returns boolean language sql stable security definer set search_path = public as $fn$
  select p_team is not null and (
    public.is_team_staff(p_team)
    or (not exists (select 1 from public.team_staff s where s.team_id = p_team)
        and public.is_team_manager(p_team)))
$fn$;

-- Nombre de quien propuso (para las notificaciones y la lista)
create or replace function public._profile_name(p_user uuid)
returns text language sql stable security definer set search_path = public as $fn$
  select coalesce(nullif(trim(full_name), ''), 'Alguien') from public.profiles where id = p_user
$fn$;

-- Avisa a quienes deciden por un equipo (sus DT; si no tiene, los admins de su academia),
-- excepto a quien hace la acción. Es la misma regla de _challenge_notify_team.
-- (Se reutiliza esa función: no hace falta otra.)

-- Crea el partido de una propuesta ya aceptada por los dos lados y avisa a todos.
create or replace function public._challenge_finalize(p_challenge uuid)
returns uuid language plpgsql security definer set search_path = public as $fn$
declare c public.match_challenges%rowtype; v_match uuid; v_home text; v_away text;
begin
  select * into c from public.match_challenges where id = p_challenge for update;
  insert into public.matches (home_team_id, away_team_id, format_players, half_minutes,
                              scheduled_at, venue, visibility, created_by)
  values (c.home_team_id, c.away_team_id, c.format_players, c.half_minutes,
          c.scheduled_at, c.venue, c.visibility, c.created_by)
  returning id into v_match;
  -- El trigger de inserción anota como creador a quien ejecuta: se deja a quien propuso
  update public.matches set created_by = c.created_by where id = v_match;
  update public.match_challenges
     set status = 'accepted', decided_by = auth.uid(), decided_at = now(), match_id = v_match
   where id = c.id;
  select name into v_home from public.teams where id = c.home_team_id;
  select name into v_away from public.teams where id = c.away_team_id;
  perform public._challenge_notify_team(c.home_team_id, 'match_challenge_accepted', 'Partido confirmado',
    v_home || ' vs ' || v_away || ': ' || public._challenge_summary(c) || '. Ya puedes preparar tu alineación.',
    jsonb_build_object('challenge_id', c.id, 'match_id', v_match));
  perform public._challenge_notify_team(c.away_team_id, 'match_challenge_accepted', 'Partido confirmado',
    v_home || ' vs ' || v_away || ': ' || public._challenge_summary(c) || '. Ya puedes preparar tu alineación.',
    jsonb_build_object('challenge_id', c.id, 'match_id', v_match));
  -- Si lo propuso un administrador, también se le avisa a él
  if c.created_by is distinct from auth.uid() and c.proposed_by_admin then
    perform public._notify(c.created_by, 'match_challenge_accepted', 'Partido confirmado',
      'Los dos entrenadores aceptaron ' || v_home || ' vs ' || v_away || '.',
      jsonb_build_object('challenge_id', c.id, 'match_id', v_match));
  end if;
  perform public.log_audit('match_challenge_accepted', 'match_challenge', c.id, jsonb_build_object('match_id', v_match));
  return v_match;
end $fn$;

-- ---------------------------------------------------------------------
-- 3. Enviar un desafío o una propuesta (misma firma que en la 015)
-- ---------------------------------------------------------------------
create or replace function public.challenge_create(
  p_home_team uuid, p_away_team uuid, p_format_players int, p_half_minutes int,
  p_scheduled_at timestamptz, p_venue text default null, p_visibility text default 'followers')
returns uuid language plpgsql security definer set search_path = public as $fn$
declare
  v_home public.teams%rowtype; v_away public.teams%rowtype; c public.match_challenges%rowtype;
  v_home_ok boolean; v_away_ok boolean; v_by_admin boolean; v_who text;
begin
  select * into v_home from public.teams where id = p_home_team;
  if not found then raise exception 'Elige tu equipo'; end if;
  select * into v_away from public.teams where id = p_away_team;
  if not found then raise exception 'Elige el equipo rival'; end if;
  if not public.is_team_manager(p_home_team) then
    raise exception 'Solo el DT o el administrador del equipo puede proponer el partido';
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
  if p_scheduled_at is null or p_scheduled_at < now() + interval '1 hour' then
    raise exception 'El partido debe programarse con al menos 1 hora de anticipación';
  end if;
  if coalesce(p_visibility, '') not in ('public','followers','private') then
    raise exception 'Visibilidad no válida';
  end if;
  if exists (select 1 from public.match_challenges x
             where x.home_team_id = p_home_team and x.away_team_id = p_away_team and x.status = 'pending') then
    raise exception 'Ya hay una propuesta pendiente entre esos dos equipos';
  end if;

  -- ¿Por qué lados decido yo? Esos quedan aceptados desde ya.
  v_home_ok := public.is_side_decider(p_home_team);
  v_away_ok := public.is_side_decider(p_away_team);
  v_by_admin := not v_home_ok;

  insert into public.match_challenges (home_team_id, away_team_id, format_players, half_minutes,
                                       scheduled_at, venue, visibility, created_by, proposed_by_admin,
                                       home_accepted_at, home_accepted_by, away_accepted_at, away_accepted_by)
  values (p_home_team, p_away_team, p_format_players, p_half_minutes, p_scheduled_at,
          nullif(trim(coalesce(p_venue, '')), ''), p_visibility, auth.uid(), v_by_admin,
          case when v_home_ok then now() end, case when v_home_ok then auth.uid() end,
          case when v_away_ok then now() end, case when v_away_ok then auth.uid() end)
  returning * into c;

  perform public.log_audit('match_challenge_created', 'match_challenge', c.id,
    jsonb_build_object('home_team_id', p_home_team, 'away_team_id', p_away_team,
                       'scheduled_at', p_scheduled_at, 'by_admin', v_by_admin));

  -- Si yo decidía por los dos lados, no hay a quién preguntar: el partido se crea ya
  if v_home_ok and v_away_ok then
    perform public._challenge_finalize(c.id);
    return c.id;
  end if;

  v_who := public._profile_name(auth.uid());
  if not v_home_ok then
    perform public._challenge_notify_team(p_home_team, 'match_challenge', 'Partido propuesto por tu academia',
      v_who || ' propone ' || v_home.name || ' vs ' || v_away.name || ': ' || public._challenge_summary(c)
        || '. Acepta o rechaza.',
      jsonb_build_object('challenge_id', c.id, 'home_team_id', p_home_team, 'away_team_id', p_away_team));
  end if;
  if not v_away_ok then
    perform public._challenge_notify_team(p_away_team, 'match_challenge',
      case when v_by_admin then 'Te proponen un partido' else 'Te desafiaron a un partido' end,
      v_home.name || ' (' || coalesce(v_home.category, '') || ') quiere jugar contra ' || v_away.name || ': '
        || public._challenge_summary(c) || '. Acepta o rechaza.',
      jsonb_build_object('challenge_id', c.id, 'home_team_id', p_home_team, 'away_team_id', p_away_team));
  end if;
  return c.id;
end $fn$;

-- ---------------------------------------------------------------------
-- 4. Responder: cada entrenador acepta o rechaza SU lado. Con los dos aceptados se crea el
--    partido (devuelve su id); si todavía falta el otro lado, devuelve null.
-- ---------------------------------------------------------------------
create or replace function public.challenge_respond(p_challenge uuid, p_accept boolean, p_reason text default null)
returns uuid language plpgsql security definer set search_path = public as $fn$
declare
  c public.match_challenges%rowtype; v_home text; v_away text;
  v_my_home boolean; v_my_away boolean; v_reason text := nullif(trim(coalesce(p_reason, '')), '');
begin
  select * into c from public.match_challenges where id = p_challenge for update;
  if not found then raise exception 'Desafío no encontrado'; end if;
  if c.status <> 'pending' then raise exception 'Este desafío ya fue respondido'; end if;
  if c.scheduled_at < now() + interval '1 hour' then
    raise exception 'Este desafío venció: falta menos de 1 hora para el partido';
  end if;
  -- Solo responde quien decide por un lado que todavía no aceptó
  v_my_home := c.home_accepted_at is null and public.is_side_decider(c.home_team_id);
  v_my_away := c.away_accepted_at is null and public.is_side_decider(c.away_team_id);
  if not (v_my_home or v_my_away) then
    raise exception 'Este partido no espera tu respuesta';
  end if;
  select name into v_home from public.teams where id = c.home_team_id;
  select name into v_away from public.teams where id = c.away_team_id;

  if p_accept then
    update public.match_challenges
       set home_accepted_at = case when v_my_home then now() else home_accepted_at end,
           home_accepted_by = case when v_my_home then auth.uid() else home_accepted_by end,
           away_accepted_at = case when v_my_away then now() else away_accepted_at end,
           away_accepted_by = case when v_my_away then auth.uid() else away_accepted_by end
     where id = c.id
    returning * into c;
    if c.home_accepted_at is not null and c.away_accepted_at is not null then
      return public._challenge_finalize(c.id);
    end if;
    -- Falta el otro lado: se avisa a quien propuso para que sepa en qué va
    if c.created_by is distinct from auth.uid() then
      perform public._notify(c.created_by, 'match_challenge_progress', 'Un entrenador aceptó',
        public._profile_name(auth.uid()) || ' aceptó ' || v_home || ' vs ' || v_away
          || '. Falta que acepte el otro entrenador.',
        jsonb_build_object('challenge_id', c.id));
    end if;
    perform public.log_audit('match_challenge_side_accepted', 'match_challenge', c.id,
      jsonb_build_object('home', v_my_home, 'away', v_my_away));
    return null;
  end if;

  update public.match_challenges
     set status = 'rejected', decided_by = auth.uid(), decided_at = now(), decision_reason = v_reason
   where id = c.id;
  -- Avisa al otro lado y a quien lo propuso
  if v_my_away then
    perform public._challenge_notify_team(c.home_team_id, 'match_challenge_rejected', 'Partido rechazado',
      v_away || ' no aceptó el partido contra ' || v_home || coalesce('. Motivo: ' || v_reason, '') || '.',
      jsonb_build_object('challenge_id', c.id));
  else
    perform public._challenge_notify_team(c.away_team_id, 'match_challenge_rejected', 'Partido rechazado',
      v_home || ' no aceptó el partido contra ' || v_away || coalesce('. Motivo: ' || v_reason, '') || '.',
      jsonb_build_object('challenge_id', c.id));
  end if;
  if c.proposed_by_admin and c.created_by is distinct from auth.uid() then
    perform public._notify(c.created_by, 'match_challenge_rejected', 'Partido rechazado',
      public._profile_name(auth.uid()) || ' rechazó ' || v_home || ' vs ' || v_away || coalesce('. Motivo: ' || v_reason, '') || '.',
      jsonb_build_object('challenge_id', c.id));
  end if;
  perform public.log_audit('match_challenge_rejected', 'match_challenge', c.id, jsonb_build_object('reason', v_reason));
  return null;
end $fn$;

-- ---------------------------------------------------------------------
-- 5. Cancelar: quien lo propuso, o quien gestiona el equipo local
-- ---------------------------------------------------------------------
create or replace function public.challenge_cancel(p_challenge uuid)
returns void language plpgsql security definer set search_path = public as $fn$
declare c public.match_challenges%rowtype; v_home text;
begin
  select * into c from public.match_challenges where id = p_challenge for update;
  if not found then raise exception 'Desafío no encontrado'; end if;
  if not (c.created_by = auth.uid() or public.is_team_manager(c.home_team_id)) then
    raise exception 'Solo quien envió el desafío puede cancelarlo';
  end if;
  if c.status <> 'pending' then raise exception 'Este desafío ya fue respondido'; end if;
  update public.match_challenges
     set status = 'cancelled', decided_by = auth.uid(), decided_at = now() where id = c.id;
  select name into v_home from public.teams where id = c.home_team_id;
  perform public._challenge_notify_team(c.away_team_id, 'match_challenge_cancelled', 'Partido cancelado',
    v_home || ' canceló el partido del ' || to_char(c.scheduled_at at time zone 'America/Lima', 'DD/MM/YYYY') || '.',
    jsonb_build_object('challenge_id', c.id));
  if c.proposed_by_admin then
    perform public._challenge_notify_team(c.home_team_id, 'match_challenge_cancelled', 'Partido cancelado',
      'Se canceló la propuesta ' || v_home || ' del ' || to_char(c.scheduled_at at time zone 'America/Lima', 'DD/MM/YYYY') || '.',
      jsonb_build_object('challenge_id', c.id));
  end if;
  perform public.log_audit('match_challenge_cancelled', 'match_challenge', c.id, '{}'::jsonb);
end $fn$;

-- ---------------------------------------------------------------------
-- 6. Mis desafíos y propuestas: ahora con el estado de cada lado.
--    incoming = espera MI respuesta (decido por un lado pendiente).
--    outgoing = lo propuse yo o gestiono el equipo local (puedo cancelarlo).
-- ---------------------------------------------------------------------
drop function if exists public.my_challenges();
create function public.my_challenges()
returns table (id uuid, incoming boolean, outgoing boolean, status text,
               home_team_id uuid, home_team_name text, home_category text, home_academy_name text, home_coach_name text,
               away_team_id uuid, away_team_name text, away_category text, away_academy_name text, away_coach_name text,
               format_players smallint, half_minutes smallint, scheduled_at timestamptz, venue text,
               visibility text, decision_reason text, match_id uuid, created_at timestamptz,
               home_accepted boolean, away_accepted boolean, proposed_by_admin boolean, proposed_by_name text)
language sql stable security definer set search_path = public as $fn$
  select c.id,
         (c.home_accepted_at is null and public.is_side_decider(c.home_team_id))
           or (c.away_accepted_at is null and public.is_side_decider(c.away_team_id)),
         c.created_by = auth.uid() or public.is_team_manager(c.home_team_id),
         case when c.status = 'pending' and c.scheduled_at < now() + interval '1 hour' then 'expired' else c.status end,
         h.id, h.name, h.category, ha.name, public._team_coach_name(h.id),
         a.id, a.name, a.category, aa.name, public._team_coach_name(a.id),
         c.format_players, c.half_minutes, c.scheduled_at, c.venue,
         c.visibility, c.decision_reason, c.match_id, c.created_at,
         c.home_accepted_at is not null, c.away_accepted_at is not null,
         c.proposed_by_admin, public._profile_name(c.created_by)
  from public.match_challenges c
  join public.teams h on h.id = c.home_team_id
  join public.academies ha on ha.id = h.academy_id
  join public.teams a on a.id = c.away_team_id
  join public.academies aa on aa.id = a.academy_id
  where public.is_team_manager(c.home_team_id) or public.is_team_manager(c.away_team_id)
     or c.created_by = auth.uid()
  order by (c.status = 'pending' and c.scheduled_at >= now() + interval '1 hour') desc, c.created_at desc
  limit 100
$fn$;

-- Lo ven también quienes lo propusieron (antes: solo quienes gestionan alguno de los equipos)
drop policy if exists match_challenges_select on public.match_challenges;
create policy match_challenges_select on public.match_challenges for select to authenticated
  using (public.is_team_manager(home_team_id) or public.is_team_manager(away_team_id) or created_by = auth.uid());

-- ---------------------------------------------------------------------
-- 7. Equipos que gestiono con su DT: el administrador elige el equipo viendo quién lo entrena
-- ---------------------------------------------------------------------
create or replace function public.managed_team_coaches()
returns table (team_id uuid, coach_name text, i_coach boolean)
language sql stable security definer set search_path = public as $fn$
  select t.id, public._team_coach_name(t.id), public.is_team_staff(t.id)
  from public.teams t
  where public.is_team_manager(t.id)
$fn$;

-- ---------------------------------------------------------------------
-- 8. Partido directo (sin propuesta): solo si decido por los equipos involucrados.
--    El administrador que no es DT del equipo ya no puede saltarse la aceptación del DT.
-- ---------------------------------------------------------------------
drop policy if exists matches_insert on public.matches;
create policy matches_insert on public.matches for insert to authenticated
  with check (public.is_side_decider(home_team_id)
    and (away_team_id is null or public.is_side_decider(away_team_id)));

-- ---------------------------------------------------------------------
-- 9. Permisos de ejecución
-- ---------------------------------------------------------------------
revoke execute on function public._profile_name(uuid), public._challenge_finalize(uuid) from public, anon, authenticated;
revoke execute on function public.is_side_decider(uuid), public.challenge_create(uuid, uuid, int, int, timestamptz, text, text),
  public.challenge_respond(uuid, boolean, text), public.challenge_cancel(uuid), public.my_challenges(),
  public.managed_team_coaches()
  from public, anon;
grant execute on function public.is_side_decider(uuid), public.challenge_create(uuid, uuid, int, int, timestamptz, text, text),
  public.challenge_respond(uuid, boolean, text), public.challenge_cancel(uuid), public.my_challenges(),
  public.managed_team_coaches()
  to authenticated;

commit;
