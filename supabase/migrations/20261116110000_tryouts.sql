-- =====================================================================
-- SportPro · Migración 011: avisos de pruebas (convocatorias abiertas de
-- una academia para captar jugadores) e inscripciones.
--
-- Reglas (todas en el servidor):
--  1. PUBLICA: el admin o un DT de la academia. La academia debe estar verificada,
--     porque el aviso lo ve cualquier usuario de la app.
--  2. SE VE: los avisos abiertos y con fecha futura de academias verificadas. Quien
--     gestiona un aviso lo ve siempre (también cerrado o cancelado).
--  3. INSCRIBE: el apoderado aprobado a su hijo, o el propio jugador si ya tiene 18 años
--     (igual que en mensualidades y documentos). Se valida la EDAD del jugador en la fecha
--     de la prueba contra el rango del aviso.
--  4. DECIDE: el admin de la academia o el DT que publicó el aviso acepta o rechaza.
--     No se aceptan más jugadores que cupos. Mismo patrón que team_join_requests.
--  5. PRIVACIDAD: quien revisa las inscripciones NO es staff del jugador, así que ve al
--     menor como «Nombre I.» (player_public_name), con su edad y posición. Nada más.
--  6. Nada se borra: un aviso se cierra o se cancela; una inscripción se retira o se
--     rechaza. Todo queda en audit_log.
--
-- Cómo ejecutarla: Supabase Dashboard → SQL Editor → pegar TODO → Run (transaccional).
-- =====================================================================
begin;

-- ---------------------------------------------------------------------
-- 1. TABLAS
-- ---------------------------------------------------------------------
create table if not exists public.tryouts (
  id          uuid primary key default gen_random_uuid(),
  academy_id  uuid not null references public.academies(id) on delete cascade,
  title       text not null check (char_length(trim(title)) between 3 and 80),
  category    text not null check (char_length(trim(category)) between 2 and 30),   -- «Sub-12», «Sub-15»…
  min_age     smallint not null check (min_age between 4 and 60),
  max_age     smallint not null check (max_age between 4 and 60),
  positions   text[] not null default '{}'
              check (positions <@ array['POR','DEF','MED','DEL']::text[]),          -- vacío = todas
  event_at    timestamptz not null,
  venue       text not null check (char_length(trim(venue)) between 3 and 120),
  district    text not null check (char_length(trim(district)) between 2 and 60),
  slots       smallint not null check (slots between 1 and 200),
  notes       text,
  status      text not null default 'open' check (status in ('open','closed','cancelled')),
  created_by  uuid references public.profiles(id),
  created_at  timestamptz not null default now(),
  check (min_age <= max_age)
);
create index if not exists tryouts_open_idx on public.tryouts (event_at) where status = 'open';
create index if not exists tryouts_academy_idx on public.tryouts (academy_id);

create table if not exists public.tryout_applications (
  id           uuid primary key default gen_random_uuid(),
  tryout_id    uuid not null references public.tryouts(id) on delete cascade,
  player_id    uuid not null references public.players(id) on delete cascade,
  requested_by uuid not null references public.profiles(id) on delete cascade,
  status       text not null default 'pending' check (status in ('pending','approved','rejected','withdrawn')),
  reason       text,
  created_at   timestamptz not null default now(),
  decided_at   timestamptz,
  decided_by   uuid references public.profiles(id)
);
-- Un jugador solo puede tener UNA inscripción viva (pendiente o aceptada) por aviso
create unique index if not exists tryout_applications_live_uq
  on public.tryout_applications (tryout_id, player_id) where status in ('pending','approved');
create index if not exists tryout_applications_player_idx on public.tryout_applications (player_id);
create index if not exists tryout_applications_requester_idx on public.tryout_applications (requested_by);

-- ---------------------------------------------------------------------
-- 2. FUNCIONES DE PERMISOS
-- ---------------------------------------------------------------------

-- ¿Gestiono este aviso? Admin de la academia, o el DT que lo publicó (mientras siga siendo DT ahí)
create or replace function public.tryout_can_manage(p_tryout uuid)
returns boolean language sql stable security definer set search_path = public as $fn$
  select exists (
    select 1 from public.tryouts t
    where t.id = p_tryout
      and (public.is_platform_admin()
           or public.has_academy_role(t.academy_id, 'adm')
           or (t.created_by = auth.uid() and public.has_academy_role(t.academy_id, 'dt'))))
$fn$;

-- ¿Puedo inscribir a este jugador? Apoderado aprobado, o el propio jugador adulto.
create or replace function public.tryout_can_apply(p_player uuid)
returns boolean language sql stable security definer set search_path = public as $fn$
  select exists (
    select 1 from public.players pl
    where pl.id = p_player
      and (public.is_guardian_of(pl.id)
           or (pl.user_id = auth.uid() and public.age_years(pl.birth_date) >= 18)))
$fn$;

-- Edad que tendrá el jugador el día de la prueba
create or replace function public._age_on(p_birth date, p_day date)
returns int language sql immutable set search_path = public as $fn$
  select date_part('year', age(p_day, p_birth))::int
$fn$;

-- Los avisos de pruebas entran en la categoría «solicitudes» de los ajustes de notificaciones.
-- (Se vuelve a definir la función de la migración 010 agregando solo la línea de tryout_.)
create or replace function public._notification_category(p_kind text)
returns text language sql immutable set search_path = public as $fn$
  select case
    when p_kind = 'callup'                                   then 'callups'
    when p_kind = 'match_live'                               then 'live'
    when p_kind like 'post\_comment%'                        then 'comments'
    when p_kind like 'fee\_%'                                then 'payments'
    when p_kind like 'guardian\_%' or p_kind like 'team\_join\_%'
      or p_kind like 'document\_%' or p_kind like 'tryout\_%'
      or p_kind = 'invite_used'                              then 'requests'
    else null end
$fn$;

-- ---------------------------------------------------------------------
-- 3. RLS: lectura limitada; escritura SOLO por funciones
-- ---------------------------------------------------------------------
alter table public.tryouts enable row level security;
alter table public.tryout_applications enable row level security;

drop policy if exists tryouts_select on public.tryouts;
create policy tryouts_select on public.tryouts for select to authenticated
  using ((status = 'open' and public.academy_is_verified(academy_id)) or public.tryout_can_manage(id)
         or exists (select 1 from public.tryout_applications a
                    where a.tryout_id = tryouts.id and a.requested_by = auth.uid()));

drop policy if exists tryout_applications_select on public.tryout_applications;
create policy tryout_applications_select on public.tryout_applications for select to authenticated
  using (requested_by = auth.uid() or public.tryout_can_manage(tryout_id));

revoke all on public.tryouts, public.tryout_applications from anon, authenticated;
grant select on public.tryouts, public.tryout_applications to authenticated;

-- ---------------------------------------------------------------------
-- 4. FUNCIONES DE ESCRITURA
-- ---------------------------------------------------------------------

-- 4.1 Publicar un aviso
create or replace function public.tryout_create(
  p_academy uuid, p_title text, p_category text, p_min_age int, p_max_age int, p_positions text[],
  p_event_at timestamptz, p_venue text, p_district text, p_slots int, p_notes text default null)
returns uuid language plpgsql security definer set search_path = public as $fn$
declare v_id uuid; v_positions text[];
begin
  if not (public.is_platform_admin() or public.is_academy_staff(p_academy)) then
    raise exception 'Solo el administrador o un DT de la academia publica avisos de pruebas';
  end if;
  if not public.academy_is_verified(p_academy) then
    raise exception 'La academia debe estar verificada para publicar avisos de pruebas';
  end if;
  if char_length(trim(coalesce(p_title, ''))) < 3 then raise exception 'Escribe un título (mínimo 3 letras)'; end if;
  if char_length(trim(coalesce(p_category, ''))) < 2 then raise exception 'Escribe la categoría (por ejemplo Sub-12)'; end if;
  if p_min_age is null or p_max_age is null or p_min_age < 4 or p_max_age > 60 or p_min_age > p_max_age then
    raise exception 'El rango de edad no es válido';
  end if;
  if p_event_at is null or p_event_at <= now() then raise exception 'La fecha de la prueba debe ser futura'; end if;
  if char_length(trim(coalesce(p_venue, ''))) < 3 then raise exception 'Escribe el lugar de la prueba'; end if;
  if char_length(trim(coalesce(p_district, ''))) < 2 then raise exception 'Escribe el distrito'; end if;
  if p_slots is null or p_slots < 1 or p_slots > 200 then raise exception 'Los cupos deben estar entre 1 y 200'; end if;
  -- Posiciones: sin repetidos y solo códigos válidos (vacío = se buscan todas)
  select coalesce(array_agg(distinct x order by x), '{}') into v_positions from unnest(coalesce(p_positions, '{}')) x;
  if not (v_positions <@ array['POR','DEF','MED','DEL']::text[]) then raise exception 'Posición no válida'; end if;

  insert into public.tryouts (academy_id, title, category, min_age, max_age, positions, event_at,
                              venue, district, slots, notes, created_by)
  values (p_academy, trim(p_title), trim(p_category), p_min_age, p_max_age, v_positions, p_event_at,
          trim(p_venue), initcap(trim(p_district)), p_slots, nullif(trim(coalesce(p_notes, '')), ''), auth.uid())
  returning id into v_id;
  perform public.log_audit('tryout_created', 'tryout', v_id,
    jsonb_build_object('academy_id', p_academy, 'category', trim(p_category), 'slots', p_slots));
  return v_id;
end $fn$;

-- 4.2 Cerrar (ya no recibe inscripciones), reabrir o cancelar un aviso
create or replace function public.tryout_set_status(p_tryout uuid, p_status text)
returns void language plpgsql security definer set search_path = public as $fn$
declare t public.tryouts%rowtype; r record;
begin
  select * into t from public.tryouts where id = p_tryout for update;
  if not found then raise exception 'Aviso no encontrado'; end if;
  if not public.tryout_can_manage(p_tryout) then raise exception 'No gestionas este aviso de prueba'; end if;
  if p_status is null or p_status not in ('open','closed','cancelled') then raise exception 'Estado no válido'; end if;
  if t.status = 'cancelled' then raise exception 'Un aviso cancelado no se puede cambiar'; end if;
  if t.status = p_status then return; end if;
  update public.tryouts set status = p_status where id = p_tryout;

  -- Al cancelar se avisa a quienes tenían una inscripción pendiente o aceptada
  if p_status = 'cancelled' then
    for r in select distinct a.requested_by as u from public.tryout_applications a
              where a.tryout_id = p_tryout and a.status in ('pending','approved') loop
      perform public._notify(r.u, 'tryout_cancelled', 'Prueba cancelada',
        'La academia canceló la prueba «' || t.title || '».', jsonb_build_object('tryout_id', p_tryout));
    end loop;
  end if;
  perform public.log_audit('tryout_' || p_status, 'tryout', p_tryout, '{}'::jsonb);
end $fn$;

-- 4.3 Inscribir a un jugador (mi hijo, o yo si soy adulto)
create or replace function public.tryout_apply(p_tryout uuid, p_player uuid)
returns uuid language plpgsql security definer set search_path = public as $fn$
declare
  t public.tryouts%rowtype; pl public.players%rowtype;
  v_id uuid; v_age int; r record;
begin
  if auth.uid() is null then raise exception 'No autenticado'; end if;
  select * into t from public.tryouts where id = p_tryout;
  if not found then raise exception 'Aviso no encontrado'; end if;
  if t.status <> 'open' then raise exception 'Este aviso ya no recibe inscripciones'; end if;
  if t.event_at <= now() then raise exception 'La prueba ya pasó'; end if;
  select * into pl from public.players where id = p_player;
  if not found then raise exception 'Jugador no encontrado'; end if;
  if not public.tryout_can_apply(p_player) then
    raise exception 'Solo el apoderado (o el jugador si es mayor de edad) puede inscribirlo';
  end if;
  -- Edad el día de la prueba, en hora de Perú
  v_age := public._age_on(pl.birth_date, (t.event_at at time zone 'America/Lima')::date);
  if v_age < t.min_age or v_age > t.max_age then
    raise exception 'La prueba es para jugadores de % a % años y el jugador tendrá %', t.min_age, t.max_age, v_age;
  end if;
  if exists (select 1 from public.tryout_applications
             where tryout_id = p_tryout and player_id = p_player and status in ('pending','approved')) then
    raise exception 'El jugador ya está inscrito en esta prueba';
  end if;
  if (select count(*) from public.tryout_applications where tryout_id = p_tryout and status = 'approved') >= t.slots then
    raise exception 'Ya no quedan cupos en esta prueba';
  end if;

  insert into public.tryout_applications (tryout_id, player_id, requested_by)
  values (p_tryout, p_player, auth.uid()) returning id into v_id;

  -- Aviso a quienes gestionan: admins de la academia y quien publicó.
  -- El mensaje usa el nombre público (menores: «Nombre I.»).
  for r in
    select distinct u from (
      select m.user_id as u from public.academy_memberships m
       where m.academy_id = t.academy_id and m.role = 'adm' and m.status = 'active'
      union
      select t.created_by) x
    where u is not null and u <> auth.uid()
  loop
    perform public._notify(r.u, 'tryout_application', 'Nueva inscripción a prueba',
      public.player_public_name(p_player) || ' (' || v_age || ' años) se inscribió en «' || t.title || '».',
      jsonb_build_object('tryout_id', p_tryout, 'application_id', v_id));
  end loop;
  perform public.log_audit('tryout_applied', 'tryout', p_tryout,
    jsonb_build_object('application_id', v_id, 'player_id', p_player));
  return v_id;
end $fn$;

-- 4.4 Quien inscribió retira su inscripción (no se borra: queda «retirada»)
create or replace function public.tryout_withdraw(p_application uuid)
returns void language plpgsql security definer set search_path = public as $fn$
declare a public.tryout_applications%rowtype;
begin
  select * into a from public.tryout_applications where id = p_application for update;
  if not found then raise exception 'Inscripción no encontrada'; end if;
  if a.requested_by is distinct from auth.uid() then raise exception 'Solo quien inscribió puede retirar la inscripción'; end if;
  if a.status not in ('pending','approved') then raise exception 'Esta inscripción ya no está activa'; end if;
  update public.tryout_applications
     set status = 'withdrawn', decided_at = now(), decided_by = auth.uid() where id = a.id;
  perform public.log_audit('tryout_withdrawn', 'tryout', a.tryout_id, jsonb_build_object('application_id', a.id));
end $fn$;

-- 4.5 Quien gestiona acepta o rechaza. No se aceptan más jugadores que cupos.
create or replace function public.tryout_decide(p_application uuid, p_accept boolean, p_reason text default null)
returns void language plpgsql security definer set search_path = public as $fn$
declare a public.tryout_applications%rowtype; t public.tryouts%rowtype; v_ok boolean := coalesce(p_accept, false);
begin
  select * into a from public.tryout_applications where id = p_application for update;
  if not found then raise exception 'Inscripción no encontrada'; end if;
  -- Se bloquea el aviso para que dos personas no acepten a la vez el último cupo
  select * into t from public.tryouts where id = a.tryout_id for update;
  if not public.tryout_can_manage(a.tryout_id) then
    raise exception 'Solo el administrador de la academia o quien publicó el aviso responde inscripciones';
  end if;
  if a.status <> 'pending' then raise exception 'Esta inscripción ya fue respondida'; end if;
  if v_ok and (select count(*) from public.tryout_applications
               where tryout_id = t.id and status = 'approved') >= t.slots then
    raise exception 'Ya no quedan cupos: la prueba tiene % y están todos ocupados', t.slots;
  end if;

  update public.tryout_applications
     set status = case when v_ok then 'approved' else 'rejected' end,
         reason = nullif(trim(coalesce(p_reason, '')), ''),
         decided_at = now(), decided_by = auth.uid()
   where id = a.id;

  perform public._notify(a.requested_by, 'tryout_answer',
    case when v_ok then 'Inscripción aceptada' else 'Inscripción rechazada' end,
    case when v_ok
         then 'Aceptaron la inscripción a «' || t.title || '». Es el '
              || to_char(t.event_at at time zone 'America/Lima', 'DD/MM/YYYY "a las" HH24:MI') || ' en ' || t.venue || '.'
         else 'No aceptaron la inscripción a «' || t.title || '».'
              || coalesce(' Motivo: ' || nullif(trim(coalesce(p_reason, '')), ''), '') end,
    jsonb_build_object('tryout_id', t.id, 'application_id', a.id));
  perform public.log_audit('tryout_application_' || case when v_ok then 'approved' else 'rejected' end,
    'tryout', t.id, jsonb_build_object('application_id', a.id, 'player_id', a.player_id, 'reason', p_reason));
end $fn$;

-- ---------------------------------------------------------------------
-- 5. FUNCIONES DE LECTURA
-- ---------------------------------------------------------------------

-- 5.1 Buscar avisos con filtros REALES (se aplican en el servidor). Un filtro null no filtra.
--     Devuelve los abiertos y futuros de academias verificadas + los que yo gestiono.
create or replace function public.tryout_search(
  p_category text default null, p_position text default null,
  p_from date default null, p_to date default null, p_district text default null)
returns table (id uuid, academy_id uuid, academy_name text, crest_path text, title text, category text,
               min_age int, max_age int, positions text[], event_at timestamptz, venue text, district text,
               slots int, approved_count int, pending_count int, notes text, status text,
               can_manage boolean, my_applications int)
language sql stable security definer set search_path = public as $fn$
  select t.id, t.academy_id, a.name, a.crest_path, t.title, t.category,
         t.min_age::int, t.max_age::int, t.positions, t.event_at, t.venue, t.district, t.slots::int,
         (select count(*)::int from public.tryout_applications x where x.tryout_id = t.id and x.status = 'approved'),
         -- cuántas esperan respuesta: solo le interesa (y solo se le dice) a quien gestiona
         case when public.tryout_can_manage(t.id)
              then (select count(*)::int from public.tryout_applications x where x.tryout_id = t.id and x.status = 'pending')
              else 0 end,
         t.notes, t.status, public.tryout_can_manage(t.id),
         (select count(*)::int from public.tryout_applications x
           where x.tryout_id = t.id and x.requested_by = auth.uid() and x.status in ('pending','approved'))
  from public.tryouts t
  join public.academies a on a.id = t.academy_id
  where ((t.status = 'open' and t.event_at > now() and a.status = 'verified') or public.tryout_can_manage(t.id))
    and (p_category is null or lower(t.category) = lower(trim(p_category)))
    -- un aviso sin posiciones busca todas, así que coincide con cualquier posición pedida
    and (p_position is null or cardinality(t.positions) = 0 or p_position = any(t.positions))
    and (p_from is null or (t.event_at at time zone 'America/Lima')::date >= p_from)
    and (p_to   is null or (t.event_at at time zone 'America/Lima')::date <= p_to)
    and (p_district is null or lower(t.district) = lower(trim(p_district)))
  order by t.event_at
$fn$;

-- 5.2 Inscripciones de un aviso (solo quien gestiona). Menores como «Nombre I.».
create or replace function public.tryout_applications_list(p_tryout uuid)
returns table (id uuid, player_name text, player_age int, player_position text,
               requested_by_name text, by_guardian boolean, status text, reason text, created_at timestamptz)
language plpgsql stable security definer set search_path = public as $fn$
begin
  if not public.tryout_can_manage(p_tryout) then raise exception 'No gestionas este aviso de prueba'; end if;
  return query
  select a.id, public.player_public_name(p.id), public.age_years(p.birth_date), p.position,
         coalesce(nullif(trim(pr.full_name), ''), 'Usuario'),
         p.user_id is distinct from a.requested_by, a.status, a.reason, a.created_at
  from public.tryout_applications a
  join public.players p on p.id = a.player_id
  left join public.profiles pr on pr.id = a.requested_by
  where a.tryout_id = p_tryout and a.status <> 'withdrawn'
  order by case a.status when 'pending' then 0 when 'approved' then 1 else 2 end, a.created_at;
end $fn$;

-- 5.3 Mis inscripciones (las que yo envié), con el estado y los datos de la prueba
create or replace function public.my_tryout_applications()
returns table (id uuid, tryout_id uuid, title text, academy_name text, event_at timestamptz, venue text,
               tryout_status text, player_id uuid, player_name text, status text, reason text, created_at timestamptz)
language sql stable security definer set search_path = public as $fn$
  select a.id, t.id, t.title, ac.name, t.event_at, t.venue, t.status,
         p.id, trim(p.first_name || ' ' || p.last_name), a.status, a.reason, a.created_at
  from public.tryout_applications a
  join public.tryouts t on t.id = a.tryout_id
  join public.academies ac on ac.id = t.academy_id
  join public.players p on p.id = a.player_id
  where a.requested_by = auth.uid()
  order by a.created_at desc
  limit 50
$fn$;

-- ---------------------------------------------------------------------
-- 6. PERMISOS DE EJECUCIÓN
-- ---------------------------------------------------------------------
revoke execute on function public._age_on(date, date) from public, anon, authenticated;

revoke execute on function public.tryout_can_manage(uuid), public.tryout_can_apply(uuid),
  public.tryout_create(uuid, text, text, int, int, text[], timestamptz, text, text, int, text),
  public.tryout_set_status(uuid, text), public.tryout_apply(uuid, uuid), public.tryout_withdraw(uuid),
  public.tryout_decide(uuid, boolean, text), public.tryout_search(text, text, date, date, text),
  public.tryout_applications_list(uuid), public.my_tryout_applications()
  from public, anon;
grant execute on function public.tryout_can_manage(uuid), public.tryout_can_apply(uuid),
  public.tryout_create(uuid, text, text, int, int, text[], timestamptz, text, text, int, text),
  public.tryout_set_status(uuid, text), public.tryout_apply(uuid, uuid), public.tryout_withdraw(uuid),
  public.tryout_decide(uuid, boolean, text), public.tryout_search(text, text, date, date, text),
  public.tryout_applications_list(uuid), public.my_tryout_applications()
  to authenticated;

commit;
