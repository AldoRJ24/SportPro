-- =====================================================================
-- SportPro · Migración 007: jugadores se unen a un equipo con código del DT
-- Flujo:
--  1. El DT (o el admin) de un equipo genera su CÓDIGO / QR de equipo (EQ-XXXX-XXXX).
--  2. El jugador (cuenta propia) o su apoderado (para su hijo) escanea/escribe el código
--     y envía una SOLICITUD. Se valida la edad contra la categoría del equipo.
--  3. Al DT le llega la notificación: «X quiere unirse a Equipo (Categoría) de Academia».
--  4. El DT acepta o rechaza. Al aceptar: el jugador entra al plantel y la cuenta del
--     jugador / apoderado pasa a ser miembro de la academia. El solicitante recibe aviso.
-- Un QR filtrado no mete a nadie: siempre decide el DT. El DT puede regenerar el código.
-- Cómo ejecutarla: SQL Editor → pegar TODO → Run (transaccional).
-- =====================================================================
begin;

alter table public.teams add column if not exists join_code text;
create unique index if not exists teams_join_code_uq on public.teams (join_code) where join_code is not null;

create table if not exists public.team_join_requests (
  id           uuid primary key default gen_random_uuid(),
  team_id      uuid not null references public.teams(id) on delete cascade,
  player_id    uuid not null references public.players(id) on delete cascade,
  requested_by uuid not null references public.profiles(id) on delete cascade,
  status       text not null default 'pending' check (status in ('pending','approved','rejected')),
  created_at   timestamptz not null default now(),
  decided_at   timestamptz,
  decided_by   uuid references public.profiles(id)
);
create unique index if not exists team_join_pending_uq
  on public.team_join_requests (team_id, player_id) where status = 'pending';

alter table public.team_join_requests enable row level security;
drop policy if exists tjr_select on public.team_join_requests;
create policy tjr_select on public.team_join_requests for select to authenticated
  using (requested_by = auth.uid() or public.is_team_manager(team_id));
revoke all on public.team_join_requests from anon, authenticated;
grant select on public.team_join_requests to authenticated;
-- Escrituras SOLO por las funciones de abajo.

-- «Halcones (Sub-15) de Academia Demo»
create or replace function public._team_label(p_team uuid)
returns text language sql stable security definer set search_path = public as $fn$
  select t.name || coalesce(' (' || t.category || ')', '') || ' de ' || a.name
  from public.teams t join public.academies a on a.id = t.academy_id where t.id = p_team
$fn$;

-- 1) Código del equipo (lo ve/genera el DT; p_regenerate invalida el anterior)
create or replace function public.team_join_code(p_team uuid, p_regenerate boolean default false)
returns text language plpgsql security definer set search_path = public as $fn$
declare c text;
begin
  if not public.is_team_manager(p_team) then raise exception 'Solo el DT del equipo puede invitar jugadores'; end if;
  select join_code into c from public.teams where id = p_team;
  if c is null or coalesce(p_regenerate, false) then
    loop
      c := 'EQ-' || public._random_code(4) || '-' || public._random_code(4);
      exit when not exists (select 1 from public.teams where join_code = c);
    end loop;
    update public.teams set join_code = c where id = p_team;
    perform public.log_audit('team_join_code', 'team', p_team, jsonb_build_object('regenerated', coalesce(p_regenerate, false)));
  end if;
  return c;
end $fn$;

-- 2) Ver a qué equipo corresponde un código (antes de solicitar)
create or replace function public.check_team_code(p_code text)
returns jsonb language plpgsql stable security definer set search_path = public as $fn$
declare t record;
begin
  select tm.id, tm.name, tm.category, tm.max_age, tm.crest_path, a.name as academy_name
    into t
    from public.teams tm join public.academies a on a.id = tm.academy_id
   where tm.join_code = upper(trim(coalesce(p_code, '')));
  if not found then return jsonb_build_object('valid', false, 'reason', 'Código de equipo no encontrado'); end if;
  return jsonb_build_object('valid', true, 'team_id', t.id, 'team_name', t.name, 'category', t.category,
                            'max_age', t.max_age, 'crest_path', t.crest_path, 'academy_name', t.academy_name);
end $fn$;

-- 3) Enviar solicitud: para mí (mi perfil de jugador) o para mi hijo vinculado
create or replace function public.request_team_join(p_code text, p_player uuid)
returns uuid language plpgsql security definer set search_path = public as $fn$
declare
  v_team public.teams%rowtype; pl public.players%rowtype;
  v_id uuid; v_age int; v_name text; v_who text; v_sent int := 0; r record;
begin
  if auth.uid() is null then raise exception 'No autenticado'; end if;
  select * into v_team from public.teams where join_code = upper(trim(coalesce(p_code, '')));
  if not found then raise exception 'Código de equipo no válido'; end if;
  select * into pl from public.players where id = p_player;
  if not found then raise exception 'Jugador no encontrado'; end if;
  -- (coalesce: un hijo sin cuenta tiene user_id NULL y NULL no debe pasar la validación)
  if not (coalesce(pl.user_id = auth.uid(), false) or public.is_guardian_of(pl.id)) then
    raise exception 'Solo puedes enviar la solicitud para ti o para tus hijos vinculados';
  end if;
  v_age := public.age_years(pl.birth_date);
  if v_team.max_age is not null and v_age > v_team.max_age then
    raise exception 'La categoría % es hasta % años y el jugador tiene %', v_team.category, v_team.max_age, v_age;
  end if;
  if exists (select 1 from public.team_players where team_id = v_team.id and player_id = pl.id and status = 'active') then
    raise exception 'El jugador ya pertenece a este equipo';
  end if;
  if exists (select 1 from public.team_join_requests where team_id = v_team.id and player_id = pl.id and status = 'pending') then
    raise exception 'Ya hay una solicitud pendiente para este equipo';
  end if;

  insert into public.team_join_requests (team_id, player_id, requested_by)
  values (v_team.id, pl.id, auth.uid()) returning id into v_id;

  v_name := trim(pl.first_name || ' ' || pl.last_name);
  -- «Mateo Rojas quiere unirse a Halcones (Sub-15) de Academia Demo» o, si lo pide el apoderado,
  -- «Paola Rojas (apoderado/a) solicita que Mateo Rojas se una a Halcones (Sub-15) de Academia Demo»
  v_who := case when pl.user_id is not distinct from auth.uid()
                then v_name || ' quiere unirse a ' || public._team_label(v_team.id)
                else coalesce(nullif(trim((select full_name from public.profiles where id = auth.uid())), ''), 'Un apoderado')
                     || ' (apoderado/a) solicita que ' || v_name || ' se una a ' || public._team_label(v_team.id) end;
  -- Aviso al DT del equipo; si el equipo aún no tiene DT, a los administradores de la academia
  for r in select distinct s.user_id as u from public.team_staff s where s.team_id = v_team.id loop
    perform public._notify(r.u, 'team_join_request', 'Solicitud para unirse',
      v_who, jsonb_build_object('request_id', v_id, 'team_id', v_team.id));
    v_sent := v_sent + 1;
  end loop;
  if v_sent = 0 then
    for r in select distinct m.user_id as u from public.academy_memberships m
              where m.academy_id = v_team.academy_id and m.role = 'adm' and m.status = 'active' loop
      perform public._notify(r.u, 'team_join_request', 'Solicitud para unirse',
        v_who, jsonb_build_object('request_id', v_id, 'team_id', v_team.id));
    end loop;
  end if;
  return v_id;
end $fn$;

-- 4) Solicitudes pendientes de los equipos que gestiono (DT / admin)
create or replace function public.team_join_requests_for_me()
returns table (id uuid, team_id uuid, team_name text, category text, academy_name text,
               player_id uuid, player_name text, player_age int, requested_by_name text,
               by_guardian boolean, created_at timestamptz)
language sql stable security definer set search_path = public as $fn$
  select r.id, t.id, t.name, t.category, a.name, p.id, trim(p.first_name || ' ' || p.last_name),
         public.age_years(p.birth_date), coalesce(nullif(trim(pr.full_name), ''), 'Usuario'),
         p.user_id is distinct from r.requested_by, r.created_at
  from public.team_join_requests r
  join public.teams t on t.id = r.team_id
  join public.academies a on a.id = t.academy_id
  join public.players p on p.id = r.player_id
  left join public.profiles pr on pr.id = r.requested_by
  where r.status = 'pending' and public.is_team_manager(r.team_id)
  order by r.created_at
$fn$;

-- 5) Mis solicitudes enviadas (para ver si están pendientes, aceptadas o rechazadas)
create or replace function public.my_team_join_requests()
returns table (id uuid, team_name text, category text, academy_name text, player_name text,
               status text, created_at timestamptz)
language sql stable security definer set search_path = public as $fn$
  select r.id, t.name, t.category, a.name, trim(p.first_name || ' ' || p.last_name), r.status, r.created_at
  from public.team_join_requests r
  join public.teams t on t.id = r.team_id
  join public.academies a on a.id = t.academy_id
  join public.players p on p.id = r.player_id
  where r.requested_by = auth.uid()
  order by r.created_at desc
  limit 20
$fn$;

-- 6) El DT acepta o rechaza
create or replace function public.respond_team_join(p_request uuid, p_accept boolean)
returns void language plpgsql security definer set search_path = public as $fn$
declare r public.team_join_requests%rowtype; pl public.players%rowtype; v_academy uuid;
begin
  select * into r from public.team_join_requests where id = p_request for update;
  if not found then raise exception 'Solicitud no encontrada'; end if;
  if not public.is_team_manager(r.team_id) then raise exception 'Solo el DT del equipo puede responder'; end if;
  if r.status <> 'pending' then raise exception 'Esta solicitud ya fue respondida'; end if;
  select * into pl from public.players where id = r.player_id;
  v_academy := public.team_academy(r.team_id);

  if coalesce(p_accept, false) then
    -- Entra al plantel (el trigger vuelve a validar edad vs categoría)
    insert into public.team_players (team_id, player_id, status) values (r.team_id, r.player_id, 'active')
    on conflict (team_id, player_id) do update set status = 'active';
    -- La cuenta del jugador y la del apoderado pasan a ser miembros de la academia
    if pl.user_id is not null then
      insert into public.academy_memberships (academy_id, user_id, role, status)
      values (v_academy, pl.user_id, 'jug', 'active')
      on conflict (academy_id, user_id, role) do update set status = 'active';
    end if;
    if r.requested_by is distinct from pl.user_id then
      insert into public.academy_memberships (academy_id, user_id, role, status)
      values (v_academy, r.requested_by, 'pad', 'active')
      on conflict (academy_id, user_id, role) do update set status = 'active';
    end if;
  end if;

  update public.team_join_requests
     set status = case when coalesce(p_accept, false) then 'approved' else 'rejected' end,
         decided_at = now(), decided_by = auth.uid()
   where id = r.id;

  perform public._notify(r.requested_by, 'team_join_answer',
    case when coalesce(p_accept, false) then 'Solicitud aceptada' else 'Solicitud rechazada' end,
    trim(pl.first_name || ' ' || pl.last_name)
      || case when coalesce(p_accept, false) then ' ya es parte de ' else ' no fue aceptado en ' end
      || public._team_label(r.team_id),
    jsonb_build_object('team_id', r.team_id));
  perform public.log_audit('team_join_' || case when coalesce(p_accept, false) then 'approved' else 'rejected' end,
    'team', r.team_id, jsonb_build_object('player_id', r.player_id, 'request_id', r.id));
end $fn$;

revoke execute on function public._team_label(uuid) from public, anon, authenticated;
revoke execute on function public.team_join_code(uuid, boolean), public.check_team_code(text),
  public.request_team_join(text, uuid), public.team_join_requests_for_me(),
  public.my_team_join_requests(), public.respond_team_join(uuid, boolean) from public, anon;
grant execute on function public.team_join_code(uuid, boolean), public.check_team_code(text),
  public.request_team_join(text, uuid), public.team_join_requests_for_me(),
  public.my_team_join_requests(), public.respond_team_join(uuid, boolean) to authenticated;

commit;
