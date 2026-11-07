-- =====================================================================
-- SPORTPRO — Migración 001: núcleo (identidad, academias, equipos,
-- jugadores, partidos en vivo con trazabilidad, estadísticas, IA).
--
-- Cómo ejecutarla: Supabase Dashboard → SQL Editor → pegar TODO → Run.
-- Es transaccional: si algo falla, no se aplica nada.
-- NO incluye (siguiente migración): comunidad/posts, entrenamientos,
-- mensualidades, códigos de invitación/QR, solicitudes de vínculo
-- 13-17 años, notificaciones, documentos.
--
-- Principios: RLS en TODAS las tablas; el cliente (Android) solo
-- conoce la clave publishable; nada de service_role en el APK;
-- estado del partido, marcador, revisiones y estadísticas solo se
-- modifican por funciones del servidor.
-- =====================================================================
begin;

-- ---------------------------------------------------------------------
-- 0. TIPOS
-- ---------------------------------------------------------------------
create type public.app_role     as enum ('adm', 'dt', 'jug', 'pad');
create type public.match_status as enum ('scheduled', 'live', 'halftime', 'finished', 'cancelled');
create type public.event_status as enum ('active', 'voided');

-- ---------------------------------------------------------------------
-- 1. UTILIDADES
-- ---------------------------------------------------------------------
create or replace function public.set_updated_at()
returns trigger language plpgsql as $fn$
begin
  new.updated_at := now();
  return new;
end $fn$;

create or replace function public.age_years(p_birth date)
returns int language sql stable as $fn$
  select case when p_birth is null then null
              else date_part('year', age(current_date, p_birth))::int end
$fn$;

-- ---------------------------------------------------------------------
-- 2. TABLAS
-- ---------------------------------------------------------------------

-- 2.1 Perfiles (1 a 1 con auth.users)
create table public.profiles (
  id          uuid primary key references auth.users(id) on delete cascade,
  full_name   text not null default '',
  birth_date  date check (birth_date is null or birth_date > date '1900-01-01'),
  avatar_path text,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);
create trigger trg_profiles_updated before update on public.profiles
  for each row execute function public.set_updated_at();

-- Superadmins de plataforma: SOLO se insertan a mano (SQL editor).
create table public.platform_admins (
  user_id    uuid primary key references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now()
);

-- 2.2 Academias, membresías, equipos
create table public.academies (
  id         uuid primary key default gen_random_uuid(),
  name       text not null check (char_length(trim(name)) between 2 and 80),
  city       text,
  crest_path text,
  status     text not null default 'pending' check (status in ('pending','verified','suspended')),
  created_by uuid references public.profiles(id) default auth.uid(),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create trigger trg_academies_updated before update on public.academies
  for each row execute function public.set_updated_at();

create table public.academy_memberships (
  id         uuid primary key default gen_random_uuid(),
  academy_id uuid not null references public.academies(id) on delete cascade,
  user_id    uuid not null references public.profiles(id) on delete cascade,
  role       public.app_role not null,
  status     text not null default 'active' check (status in ('active','pending','revoked')),
  invited_by uuid references public.profiles(id) default auth.uid(),
  created_at timestamptz not null default now(),
  unique (academy_id, user_id, role)
);
create index on public.academy_memberships (user_id);

create table public.teams (
  id                    uuid primary key default gen_random_uuid(),
  academy_id            uuid not null references public.academies(id) on delete cascade,
  name                  text not null check (char_length(trim(name)) between 2 and 60),
  category              text not null,                       -- 'Sub-10', 'Sub-15', 'Primera'...
  max_age               smallint check (max_age between 5 and 60), -- validación edad↔categoría
  default_format_players smallint not null default 7 check (default_format_players between 5 and 11),
  half_minutes          smallint not null default 25 check (half_minutes between 5 and 60),
  crest_path            text,
  created_at            timestamptz not null default now(),
  unique (academy_id, name)
);

create table public.team_staff (
  team_id    uuid not null references public.teams(id) on delete cascade,
  user_id    uuid not null references public.profiles(id) on delete cascade,
  staff_role text not null default 'dt' check (staff_role in ('dt','assistant')),
  created_at timestamptz not null default now(),
  primary key (team_id, user_id)
);

-- 2.3 Jugadores (perfil deportivo ≠ cuenta). Puede existir sin cuenta (<13).
create table public.players (
  id               uuid primary key default gen_random_uuid(),
  user_id          uuid unique references public.profiles(id) on delete set null,
  first_name       text not null check (char_length(trim(first_name)) >= 1),
  last_name        text not null check (char_length(trim(last_name))  >= 1),
  birth_date       date not null check (birth_date > date '1900-01-01'),
  position         text,
  photo_path       text,
  photo_authorized boolean not null default false,
  created_by       uuid references public.profiles(id) default auth.uid(),
  claimed_at       timestamptz,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now()
);
create trigger trg_players_updated before update on public.players
  for each row execute function public.set_updated_at();

-- Datos sensibles separados (Regla A de privacidad)
create table public.player_private_data (
  player_id               uuid primary key references public.players(id) on delete cascade,
  height_cm               numeric(5,1) check (height_cm between 50 and 250),
  weight_kg               numeric(5,1) check (weight_kg between 10 and 250),
  phone                   text,
  emergency_contact_name  text,
  emergency_contact_phone text,
  updated_at              timestamptz not null default now()
);
create trigger trg_ppd_updated before update on public.player_private_data
  for each row execute function public.set_updated_at();

create table public.team_players (
  id            uuid primary key default gen_random_uuid(),
  team_id       uuid not null references public.teams(id) on delete cascade,
  player_id     uuid not null references public.players(id) on delete cascade,
  jersey_number smallint check (jersey_number between 0 and 99),
  status        text not null default 'active' check (status in ('active','inactive')),
  joined_at     timestamptz not null default now(),
  unique (team_id, player_id)
);
create unique index team_players_jersey_unique
  on public.team_players (team_id, jersey_number)
  where status = 'active' and jersey_number is not null;
create index on public.team_players (player_id);

create table public.guardian_player_links (
  id               uuid primary key default gen_random_uuid(),
  guardian_user_id uuid not null references public.profiles(id) on delete cascade,
  player_id        uuid not null references public.players(id) on delete cascade,
  status           text not null default 'approved' check (status in ('pending','approved','rejected','revoked')),
  is_primary       boolean not null default false,
  created_at       timestamptz not null default now(),
  decided_at       timestamptz,
  unique (guardian_user_id, player_id)
);
create index on public.guardian_player_links (player_id);

-- Seguir academias/equipos (alimenta franja "En vivo" y secciones)
create table public.follows (
  id         uuid primary key default gen_random_uuid(),
  user_id    uuid not null references public.profiles(id) on delete cascade default auth.uid(),
  academy_id uuid references public.academies(id) on delete cascade,
  team_id    uuid references public.teams(id) on delete cascade,
  created_at timestamptz not null default now(),
  check ((academy_id is not null)::int + (team_id is not null)::int = 1)
);
create unique index follows_user_academy on public.follows (user_id, academy_id) where academy_id is not null;
create unique index follows_user_team    on public.follows (user_id, team_id)    where team_id is not null;

-- 2.4 Partidos
create table public.matches (
  id                     uuid primary key default gen_random_uuid(),
  academy_id             uuid not null references public.academies(id),   -- la fija un trigger
  home_team_id           uuid not null references public.teams(id),
  away_team_id           uuid references public.teams(id),                -- rival registrado
  away_external_name     text check (away_external_name is null or char_length(trim(away_external_name)) between 2 and 60), -- rival externo
  format_players         smallint not null default 7 check (format_players between 5 and 11),
  half_minutes           smallint not null default 25 check (half_minutes between 5 and 60),
  halves                 smallint not null default 2 check (halves = 2),
  scheduled_at           timestamptz,
  venue                  text,
  visibility             text not null default 'followers' check (visibility in ('public','followers','private')),
  status                 public.match_status not null default 'scheduled',
  period                 smallint not null default 0 check (period between 0 and 4),
  period_started_at      timestamptz,
  elapsed_before_seconds integer not null default 0,
  clock_running          boolean not null default false,
  started_at             timestamptz,
  finished_at            timestamptz,
  home_score             smallint not null default 0,
  away_score             smallint not null default 0,
  created_by             uuid references public.profiles(id) default auth.uid(),
  created_at             timestamptz not null default now(),
  updated_at             timestamptz not null default now(),
  constraint matches_one_rival check ((away_team_id is not null) <> (away_external_name is not null)),
  constraint matches_distinct_teams check (away_team_id is null or away_team_id <> home_team_id)
);
create trigger trg_matches_updated before update on public.matches
  for each row execute function public.set_updated_at();
create index on public.matches (home_team_id, scheduled_at desc);
create index on public.matches (away_team_id, scheduled_at desc);
create index on public.matches (status);

create table public.match_external_players (   -- plantel escrito a mano del rival externo
  id            uuid primary key default gen_random_uuid(),
  match_id      uuid not null references public.matches(id) on delete cascade,
  name          text not null check (char_length(trim(name)) >= 1),
  jersey_number smallint check (jersey_number between 0 and 99),
  is_starter    boolean not null default true,
  created_at    timestamptz not null default now()
);

create table public.match_operators (          -- quién registra eventos (máx. 1 activo)
  id          uuid primary key default gen_random_uuid(),
  match_id    uuid not null references public.matches(id) on delete cascade,
  user_id     uuid not null references public.profiles(id),
  assigned_by uuid references public.profiles(id),
  active      boolean not null default true,
  assigned_at timestamptz not null default now()
);
create unique index match_operators_one_active on public.match_operators (match_id) where active;

create table public.match_callups (
  id           uuid primary key default gen_random_uuid(),
  match_id     uuid not null references public.matches(id) on delete cascade,
  team_id      uuid not null references public.teams(id),
  player_id    uuid not null references public.players(id),
  status       text not null default 'pending' check (status in ('pending','confirmed','declined')),
  note         text,
  responded_by uuid references public.profiles(id),
  responded_at timestamptz,
  created_at   timestamptz not null default now(),
  unique (match_id, player_id)
);

create table public.match_lineup_players (
  id             uuid primary key default gen_random_uuid(),
  match_id       uuid not null references public.matches(id) on delete cascade,
  team_side      text not null check (team_side in ('home','away')),
  player_id      uuid not null references public.players(id),
  is_starter     boolean not null default true,
  field_position text,
  jersey_number  smallint check (jersey_number between 0 and 99),
  unique (match_id, player_id)
);

-- 2.5 Catálogo de eventos (configurable) y eventos (append-only)
create table public.event_types (
  id               uuid primary key default gen_random_uuid(),
  academy_id       uuid references public.academies(id) on delete cascade,  -- NULL = catálogo global base
  code             text not null check (code ~ '^[a-z0-9_]+$'),
  name             text not null,
  icon             text,                                                    -- clave que la app mapea a un dibujo
  group_kind       text not null check (group_kind in ('control','main','secondary')),
  scoring_rule     text not null default 'none' check (scoring_rule in ('none','always','if_converted')),
  required_payload text[] not null default '{}',
  is_active        boolean not null default true,
  sort_order       smallint not null default 100,
  created_at       timestamptz not null default now()
);
create unique index event_types_code_unique
  on public.event_types (coalesce(academy_id, '00000000-0000-0000-0000-000000000000'::uuid), code);

create table public.match_events (
  id                 uuid primary key default gen_random_uuid(),
  match_id           uuid not null references public.matches(id) on delete cascade,
  client_event_id    uuid not null,                -- idempotencia (lo genera el celular)
  seq                integer not null default 0,   -- orden lógico (lo asigna el servidor)
  event_type_id      uuid not null references public.event_types(id),
  period             smallint not null check (period between 1 and 4),
  minute             smallint not null check (minute between 0 and 200),
  minute_extra       smallint not null default 0 check (minute_extra between 0 and 30),
  team_side          text not null check (team_side in ('home','away','none')),
  player_id          uuid references public.players(id),
  external_player_id uuid references public.match_external_players(id),
  payload            jsonb not null default '{}'::jsonb,
  notes              text check (notes is null or char_length(notes) <= 500),
  status             public.event_status not null default 'active',
  recorded_late      boolean not null default false,
  created_by         uuid references public.profiles(id),
  created_at         timestamptz not null default now(),
  updated_at         timestamptz not null default now(),
  voided_by          uuid references public.profiles(id),
  voided_at          timestamptz,
  void_reason        text,
  unique (match_id, client_event_id),
  unique (match_id, seq),
  check (player_id is null or external_player_id is null)
);
create index on public.match_events (match_id, seq);
create index on public.match_events (player_id) where player_id is not null;

create table public.match_event_revisions (
  id          uuid primary key default gen_random_uuid(),
  event_id    uuid not null references public.match_events(id) on delete cascade,
  revision_no integer not null,
  change_kind text not null check (change_kind in ('correction','void')),
  snapshot    jsonb not null,            -- cómo estaba ANTES del cambio
  changed_by  uuid references public.profiles(id),
  reason      text,
  created_at  timestamptz not null default now(),
  unique (event_id, revision_no)
);

-- 2.6 Resumen IA
create table public.match_summaries (
  id                uuid primary key default gen_random_uuid(),
  match_id          uuid not null unique references public.matches(id) on delete cascade,
  status            text not null default 'draft' check (status in ('draft','edited','approved')),
  ai_text           text,
  final_text        text,
  generation_count  smallint not null default 0 check (generation_count between 0 and 3),
  model             text,
  last_generated_at timestamptz,
  visibility        text check (visibility in ('public','academy','team')),
  edited_by         uuid references public.profiles(id),
  approved_by       uuid references public.profiles(id),
  approved_at       timestamptz,
  created_at        timestamptz not null default now(),
  updated_at        timestamptz not null default now()
);
create trigger trg_ms_updated before update on public.match_summaries
  for each row execute function public.set_updated_at();

create table public.match_summary_versions (
  id         uuid primary key default gen_random_uuid(),
  summary_id uuid not null references public.match_summaries(id) on delete cascade,
  version_no integer not null,
  source     text not null check (source in ('ai','manual')),
  text       text not null,
  created_by uuid references public.profiles(id),
  created_at timestamptz not null default now(),
  unique (summary_id, version_no)
);

-- 2.7 Auditoría
create table public.audit_log (
  id        bigint generated always as identity primary key,
  actor     uuid,
  action    text not null,
  entity    text not null,
  entity_id uuid,
  data      jsonb not null default '{}'::jsonb,
  at        timestamptz not null default now()
);
create index on public.audit_log (entity, entity_id);

create or replace function public.log_audit(p_action text, p_entity text, p_entity_id uuid, p_data jsonb default '{}')
returns void language sql security definer set search_path = public as $fn$
  insert into public.audit_log(actor, action, entity, entity_id, data)
  values (auth.uid(), p_action, p_entity, p_entity_id, coalesce(p_data, '{}'::jsonb));
$fn$;

-- ---------------------------------------------------------------------
-- 3. FUNCIONES AUXILIARES DE AUTORIZACIÓN (SECURITY DEFINER, search_path fijo)
-- ---------------------------------------------------------------------
create or replace function public.is_platform_admin()
returns boolean language sql stable security definer set search_path = public as $fn$
  select exists (select 1 from public.platform_admins where user_id = auth.uid())
$fn$;

create or replace function public.user_has_academy_role(p_user uuid, p_academy uuid, p_role public.app_role)
returns boolean language sql stable security definer set search_path = public as $fn$
  select exists (select 1 from public.academy_memberships
                 where user_id = p_user and academy_id = p_academy and role = p_role and status = 'active')
$fn$;

create or replace function public.has_academy_role(p_academy uuid, p_role public.app_role)
returns boolean language sql stable security definer set search_path = public as $fn$
  select public.user_has_academy_role(auth.uid(), p_academy, p_role)
$fn$;

create or replace function public.user_is_academy_member(p_user uuid, p_academy uuid)
returns boolean language sql stable security definer set search_path = public as $fn$
  select exists (select 1 from public.academy_memberships
                 where user_id = p_user and academy_id = p_academy and status = 'active')
$fn$;

create or replace function public.is_academy_member(p_academy uuid)
returns boolean language sql stable security definer set search_path = public as $fn$
  select public.user_is_academy_member(auth.uid(), p_academy)
$fn$;

create or replace function public.is_academy_staff(p_academy uuid)
returns boolean language sql stable security definer set search_path = public as $fn$
  select public.has_academy_role(p_academy, 'adm') or public.has_academy_role(p_academy, 'dt')
$fn$;

create or replace function public.academy_is_verified(p_academy uuid)
returns boolean language sql stable security definer set search_path = public as $fn$
  select exists (select 1 from public.academies where id = p_academy and status = 'verified')
$fn$;

create or replace function public.team_academy(p_team uuid)
returns uuid language sql stable security definer set search_path = public as $fn$
  select academy_id from public.teams where id = p_team
$fn$;

create or replace function public.is_team_staff(p_team uuid)
returns boolean language sql stable security definer set search_path = public as $fn$
  select exists (select 1 from public.team_staff where team_id = p_team and user_id = auth.uid())
$fn$;

create or replace function public.is_team_manager(p_team uuid)
returns boolean language sql stable security definer set search_path = public as $fn$
  select public.is_platform_admin()
      or public.has_academy_role(public.team_academy(p_team), 'adm')
      or public.is_team_staff(p_team)
$fn$;

create or replace function public.shares_academy_with_user(p_user uuid)
returns boolean language sql stable security definer set search_path = public as $fn$
  select exists (
    select 1 from public.academy_memberships a
    join public.academy_memberships b on b.academy_id = a.academy_id
    where a.user_id = auth.uid() and a.status = 'active'
      and b.user_id = p_user     and b.status = 'active')
$fn$;

create or replace function public.my_age()
returns int language sql stable security definer set search_path = public as $fn$
  select public.age_years(birth_date) from public.profiles where id = auth.uid()
$fn$;

create or replace function public.is_guardian_of(p_player uuid)
returns boolean language sql stable security definer set search_path = public as $fn$
  select exists (select 1 from public.guardian_player_links
                 where player_id = p_player and guardian_user_id = auth.uid() and status = 'approved')
$fn$;

-- Ver identidad básica del jugador (nombre, posición, foto autorizada)
create or replace function public.can_view_player(p_player uuid)
returns boolean language sql stable security definer set search_path = public as $fn$
  select public.is_platform_admin()
      or public.is_guardian_of(p_player)
      or exists (select 1 from public.players pl
                 where pl.id = p_player and (pl.user_id = auth.uid() or pl.created_by = auth.uid()))
      or exists (select 1 from public.team_players tp
                 join public.teams t on t.id = tp.team_id
                 where tp.player_id = p_player and public.is_academy_member(t.academy_id))
$fn$;

-- Ver datos físicos / contactos / estadísticas detalladas (Regla A)
create or replace function public.can_view_player_private(p_player uuid)
returns boolean language sql stable security definer set search_path = public as $fn$
  select public.is_platform_admin()
      or public.is_guardian_of(p_player)
      or exists (select 1 from public.players pl
                 where pl.id = p_player and pl.user_id = auth.uid() and coalesce(public.my_age(), 0) >= 13)
      or exists (select 1 from public.team_players tp
                 join public.teams t on t.id = tp.team_id
                 where tp.player_id = p_player
                   and (public.is_team_staff(t.id) or public.has_academy_role(t.academy_id, 'adm')))
$fn$;

create or replace function public.can_manage_player_data(p_player uuid)
returns boolean language sql stable security definer set search_path = public as $fn$
  select public.is_guardian_of(p_player)
      or exists (select 1 from public.players pl
                 where pl.id = p_player and pl.user_id = auth.uid() and coalesce(public.my_age(), 0) >= 13)
$fn$;

create or replace function public.player_public_name(p_player uuid)
returns text language sql stable security definer set search_path = public as $fn$
  select case when public.age_years(pl.birth_date) >= 18
              then pl.first_name || ' ' || pl.last_name
              else pl.first_name || ' ' || left(pl.last_name, 1) || '.' end
  from public.players pl where pl.id = p_player
$fn$;

create or replace function public.player_display_name(p_player uuid)
returns text language sql stable security definer set search_path = public as $fn$
  select case when public.can_view_player_private(pl.id) or public.age_years(pl.birth_date) >= 18
              then pl.first_name || ' ' || pl.last_name
              else pl.first_name || ' ' || left(pl.last_name, 1) || '.' end
  from public.players pl where pl.id = p_player
$fn$;

create or replace function public.is_match_manager(p_match uuid)
returns boolean language sql stable security definer set search_path = public as $fn$
  select coalesce((select public.is_team_manager(m.home_team_id) from public.matches m where m.id = p_match), false)
$fn$;

create or replace function public.is_match_operator(p_match uuid)
returns boolean language sql stable security definer set search_path = public as $fn$
  select exists (select 1 from public.match_operators
                 where match_id = p_match and user_id = auth.uid() and active)
$fn$;

create or replace function public.can_view_match(p_match uuid)
returns boolean language sql stable security definer set search_path = public as $fn$
  select exists (
    select 1 from public.matches mt
    where mt.id = p_match and (
      public.is_platform_admin()
      or mt.visibility = 'public'
      or public.is_academy_member(mt.academy_id)
      or (mt.away_team_id is not null and public.is_academy_member(public.team_academy(mt.away_team_id)))
      or public.is_match_operator(mt.id)
      or exists (select 1 from public.team_players tp
                 where tp.status = 'active'
                   and tp.team_id in (mt.home_team_id, mt.away_team_id)
                   and (public.is_guardian_of(tp.player_id)
                        or exists (select 1 from public.players pl
                                   where pl.id = tp.player_id and pl.user_id = auth.uid())))
      or (mt.visibility = 'followers' and exists (
            select 1 from public.follows f
            where f.user_id = auth.uid()
              and (f.team_id in (mt.home_team_id, mt.away_team_id)
                   or f.academy_id in (mt.academy_id, public.team_academy(mt.away_team_id)))))
    ))
$fn$;

create or replace function public.can_record_event(p_match uuid)
returns boolean language sql stable security definer set search_path = public as $fn$
  select exists (
    select 1 from public.matches mt where mt.id = p_match and (
      (mt.status in ('live','halftime') and (public.is_match_manager(mt.id) or public.is_match_operator(mt.id)))
      or (mt.status = 'finished' and public.is_match_manager(mt.id))))
$fn$;

-- ---------------------------------------------------------------------
-- 4. TRIGGERS DE INTEGRIDAD Y NEGOCIO
-- ---------------------------------------------------------------------

-- 4.1 Alta de usuario → perfil (bloquea menores de 13 en el servidor)
create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $fn$
declare v_birth date; v_name text;
begin
  v_name := coalesce(nullif(trim(new.raw_user_meta_data->>'full_name'), ''), split_part(coalesce(new.email,''), '@', 1), '');
  begin
    v_birth := nullif(new.raw_user_meta_data->>'birth_date', '')::date;
  exception when others then
    v_birth := null;
  end;
  if v_birth is not null and public.age_years(v_birth) < 13 then
    raise exception 'Debes tener al menos 13 años para crear una cuenta. Un apoderado puede crear tu perfil.';
  end if;
  insert into public.profiles (id, full_name, birth_date) values (new.id, v_name, v_birth);
  return new;
end $fn$;

create trigger on_auth_user_created after insert on auth.users
  for each row execute function public.handle_new_user();

-- 4.2 Autorización de foto de menores: solo el apoderado (o admin)
create or replace function public.trg_players_photo_auth()
returns trigger language plpgsql security definer set search_path = public as $fn$
begin
  if new.photo_authorized is distinct from old.photo_authorized
     and public.age_years(old.birth_date) < 18
     and not (public.is_guardian_of(old.id) or public.is_platform_admin() or auth.uid() is null) then
    raise exception 'Solo el apoderado puede autorizar la foto de un menor';
  end if;
  return new;
end $fn$;
create trigger trg_players_photo_auth before update on public.players
  for each row execute function public.trg_players_photo_auth();

-- 4.3 Edad vs categoría (validación en servidor)
create or replace function public.trg_team_players_age()
returns trigger language plpgsql security definer set search_path = public as $fn$
declare v_max smallint; v_birth date; v_cat text;
begin
  select max_age, category into v_max, v_cat from public.teams where id = new.team_id;
  select birth_date into v_birth from public.players where id = new.player_id;
  if v_max is not null and public.age_years(v_birth) > v_max then
    raise exception 'El jugador (% años) excede la categoría % (máx. % años)', public.age_years(v_birth), v_cat, v_max;
  end if;
  return new;
end $fn$;
create trigger trg_team_players_age before insert on public.team_players
  for each row execute function public.trg_team_players_age();

-- 4.4 Partido: academia derivada del equipo local; valida rival
create or replace function public.trg_matches_before_insert()
returns trigger language plpgsql security definer set search_path = public as $fn$
begin
  new.academy_id := public.team_academy(new.home_team_id);
  if new.academy_id is null then raise exception 'Equipo local inválido'; end if;
  new.created_by := coalesce(auth.uid(), new.created_by);
  new.status := 'scheduled'; new.period := 0; new.home_score := 0; new.away_score := 0;
  new.clock_running := false; new.elapsed_before_seconds := 0;
  new.started_at := null; new.finished_at := null; new.period_started_at := null;
  return new;
end $fn$;
create trigger trg_matches_before_insert before insert on public.matches
  for each row execute function public.trg_matches_before_insert();

-- 4.5 Convocatoria: quién y cuándo respondió
create or replace function public.trg_callups_before_update()
returns trigger language plpgsql security definer set search_path = public as $fn$
begin
  if new.status is distinct from old.status then
    new.responded_by := auth.uid();
    new.responded_at := now();
  end if;
  return new;
end $fn$;
create trigger trg_callups_before_update before update on public.match_callups
  for each row execute function public.trg_callups_before_update();

-- 4.6 Alineación: solo convocados CONFIRMADOS y del equipo correcto (US-09)
create or replace function public.trg_lineup_validate()
returns trigger language plpgsql security definer set search_path = public as $fn$
declare m public.matches%rowtype; v_team uuid;
begin
  select * into m from public.matches where id = new.match_id;
  if m.status <> 'scheduled' then raise exception 'La alineación solo se edita antes de iniciar el partido'; end if;
  v_team := case new.team_side when 'home' then m.home_team_id else m.away_team_id end;
  if v_team is null then raise exception 'El rival es externo: use el plantel manual'; end if;
  if not exists (select 1 from public.team_players tp where tp.team_id = v_team and tp.player_id = new.player_id and tp.status = 'active') then
    raise exception 'El jugador no pertenece al equipo seleccionado';
  end if;
  if not exists (select 1 from public.match_callups c where c.match_id = new.match_id and c.player_id = new.player_id and c.status = 'confirmed') then
    raise exception 'Solo pueden alinearse jugadores que confirmaron su asistencia';
  end if;
  return new;
end $fn$;
create trigger trg_lineup_validate before insert or update on public.match_lineup_players
  for each row execute function public.trg_lineup_validate();

-- 4.7 Eventos: preparación (idempotencia, seq, autoría) — se ejecuta primero
create or replace function public.trg_match_event_a_prepare()
returns trigger language plpgsql security definer set search_path = public as $fn$
declare m public.matches%rowtype;
begin
  select * into m from public.matches where id = new.match_id;
  if not found then raise exception 'Partido inexistente'; end if;
  perform pg_advisory_xact_lock(hashtextextended(new.match_id::text, 0));
  new.seq          := coalesce((select max(seq) from public.match_events where match_id = new.match_id), 0) + 1;
  new.created_by   := coalesce(auth.uid(), new.created_by);
  new.status       := 'active';
  new.voided_by := null; new.voided_at := null; new.void_reason := null;
  new.recorded_late := (m.status = 'finished');
  return new;
end $fn$;
create trigger trg_match_event_a_prepare before insert on public.match_events
  for each row execute function public.trg_match_event_a_prepare();

-- 4.8 Eventos: validación por tipo (datos obligatorios, equipo↔jugador)
create or replace function public.trg_match_event_b_validate()
returns trigger language plpgsql security definer set search_path = public as $fn$
declare
  m public.matches%rowtype; t public.event_types%rowtype;
  k text; v_team uuid; v_val text;
begin
  select * into m from public.matches where id = new.match_id;
  select * into t from public.event_types where id = new.event_type_id;
  if not found then raise exception 'Tipo de evento inexistente'; end if;
  if t.academy_id is not null and t.academy_id <> m.academy_id then
    raise exception 'El tipo de evento no pertenece a esta academia';
  end if;
  if not t.is_active then raise exception 'El tipo de evento "%" está desactivado', t.name; end if;

  if t.group_kind = 'control' then
    if new.team_side <> 'none' then raise exception 'Los eventos de control no tienen equipo'; end if;
  else
    if new.team_side not in ('home','away') then raise exception 'Indica el equipo (local o visitante)'; end if;
  end if;

  foreach k in array t.required_payload loop
    if not (new.payload ? k) or new.payload->>k is null or trim(new.payload->>k) = '' then
      raise exception 'Falta el dato obligatorio "%" para el evento "%"', k, t.name;
    end if;
  end loop;

  if new.payload ? 'own_goal' and jsonb_typeof(new.payload->'own_goal') <> 'boolean' then
    raise exception 'own_goal debe ser true/false';
  end if;
  if new.payload ? 'result' and new.payload->>'result' not in ('converted','missed','saved') then
    raise exception 'result debe ser converted, missed o saved';
  end if;

  if new.team_side in ('home','away') then
    v_team := case new.team_side when 'home' then m.home_team_id else m.away_team_id end;
    if new.player_id is not null then
      if v_team is null or not exists (select 1 from public.team_players tp
           where tp.team_id = v_team and tp.player_id = new.player_id and tp.status = 'active') then
        raise exception 'El jugador no pertenece al equipo seleccionado';
      end if;
    end if;
    if new.external_player_id is not null then
      if new.team_side <> 'away' or m.away_team_id is not null
         or not exists (select 1 from public.match_external_players x where x.id = new.external_player_id and x.match_id = new.match_id) then
        raise exception 'Jugador externo inválido para este partido';
      end if;
    end if;
    if v_team is not null then
      foreach k in array array['assist_player_id','player_in_id','player_out_id'] loop
        v_val := new.payload->>k;
        if v_val is not null and v_val <> '' then
          begin
            perform v_val::uuid;
          exception when others then
            raise exception '% no es un identificador válido', k;
          end;
          if not exists (select 1 from public.team_players tp
                         where tp.team_id = v_team and tp.player_id = v_val::uuid and tp.status = 'active') then
            raise exception '% no pertenece al equipo seleccionado', k;
          end if;
        end if;
      end loop;
    end if;
  else
    if new.player_id is not null or new.external_player_id is not null then
      raise exception 'Un evento de control no lleva jugador';
    end if;
  end if;
  return new;
end $fn$;
create trigger trg_match_event_b_validate before insert or update on public.match_events
  for each row execute function public.trg_match_event_b_validate();

-- 4.9 Eventos: trazabilidad de cambios (revisión automática)
create or replace function public.trg_match_event_c_revision()
returns trigger language plpgsql security definer set search_path = public as $fn$
begin
  if new.match_id <> old.match_id or new.client_event_id <> old.client_event_id
     or new.seq <> old.seq or new.created_by is distinct from old.created_by or new.created_at <> old.created_at then
    raise exception 'Campos inmutables del evento';
  end if;
  if old.status = 'voided' then
    raise exception 'El evento está anulado y no se puede modificar';
  end if;
  insert into public.match_event_revisions (event_id, revision_no, change_kind, snapshot, changed_by, reason)
  values (old.id,
          coalesce((select max(revision_no) from public.match_event_revisions where event_id = old.id), 0) + 1,
          case when new.status = 'voided' then 'void' else 'correction' end,
          to_jsonb(old), auth.uid(), nullif(current_setting('app.change_reason', true), ''));
  new.updated_at := now();
  return new;
end $fn$;
create trigger trg_match_event_c_revision before update on public.match_events
  for each row execute function public.trg_match_event_c_revision();

-- 4.10 Marcador derivado de eventos vigentes (gol en contra invierte el lado)
create or replace function public.recompute_match_score(p_match uuid)
returns void language sql security definer set search_path = public as $fn$
  update public.matches m set
    home_score = (select count(*) from public.match_events e join public.event_types t on t.id = e.event_type_id
                  where e.match_id = p_match and e.status = 'active' and e.team_side in ('home','away')
                    and (t.scoring_rule = 'always' or (t.scoring_rule = 'if_converted' and e.payload->>'result' = 'converted'))
                    and ((e.team_side = 'home') <> (coalesce(e.payload->>'own_goal', 'false') = 'true'))),
    away_score = (select count(*) from public.match_events e join public.event_types t on t.id = e.event_type_id
                  where e.match_id = p_match and e.status = 'active' and e.team_side in ('home','away')
                    and (t.scoring_rule = 'always' or (t.scoring_rule = 'if_converted' and e.payload->>'result' = 'converted'))
                    and ((e.team_side = 'away') <> (coalesce(e.payload->>'own_goal', 'false') = 'true')))
  where m.id = p_match
$fn$;

create or replace function public.trg_match_event_d_score()
returns trigger language plpgsql security definer set search_path = public as $fn$
begin
  perform public.recompute_match_score(new.match_id);
  return null;
end $fn$;
create trigger trg_match_event_d_score after insert or update on public.match_events
  for each row execute function public.trg_match_event_d_score();

-- ---------------------------------------------------------------------
-- 5. FUNCIONES DE NEGOCIO (RPC) — el cliente llama a estas, no escribe a mano
-- ---------------------------------------------------------------------

-- 5.1 Fecha de nacimiento del usuario (solo si aún no la tiene)
create or replace function public.set_my_birth_date(p_birth date)
returns void language plpgsql security definer set search_path = public as $fn$
begin
  if auth.uid() is null then raise exception 'No autenticado'; end if;
  if p_birth is null or p_birth > current_date or public.age_years(p_birth) < 13 then
    raise exception 'Fecha de nacimiento inválida o menor de 13 años';
  end if;
  update public.profiles set birth_date = p_birth where id = auth.uid() and birth_date is null;
  if not found then raise exception 'La fecha de nacimiento ya fue registrada'; end if;
end $fn$;

-- 5.2 Apoderado crea el perfil de un hijo MENOR DE 13 (queda vinculado y aprobado)
create or replace function public.create_child_player(p_first text, p_last text, p_birth date, p_position text default null)
returns uuid language plpgsql security definer set search_path = public as $fn$
declare v_id uuid;
begin
  if auth.uid() is null then raise exception 'No autenticado'; end if;
  if coalesce(public.my_age(), 0) < 18 then raise exception 'Solo un adulto puede crear perfiles de menores'; end if;
  if p_birth is null or p_birth > current_date or public.age_years(p_birth) >= 13 then
    raise exception 'Los perfiles gestionados por el apoderado son para menores de 13 años';
  end if;
  if coalesce(trim(p_first), '') = '' or coalesce(trim(p_last), '') = '' then raise exception 'Nombre y apellido obligatorios'; end if;
  insert into public.players (first_name, last_name, birth_date, position, created_by)
  values (trim(p_first), trim(p_last), p_birth, nullif(trim(coalesce(p_position,'')), ''), auth.uid())
  returning id into v_id;
  insert into public.guardian_player_links (guardian_user_id, player_id, status, is_primary, decided_at)
  values (auth.uid(), v_id, 'approved', true, now());
  perform public.log_audit('create_child_player', 'player', v_id, '{}'::jsonb);
  return v_id;
end $fn$;

-- 5.3 Jugador adulto crea su propio perfil deportivo (13-17 llegará con el flujo de vínculo)
create or replace function public.create_my_player(p_first text, p_last text, p_position text default null)
returns uuid language plpgsql security definer set search_path = public as $fn$
declare v_id uuid; v_birth date;
begin
  select birth_date into v_birth from public.profiles where id = auth.uid();
  if v_birth is null or public.age_years(v_birth) < 18 then
    raise exception 'Por ahora solo jugadores mayores de 18 años pueden crear su perfil directamente';
  end if;
  insert into public.players (user_id, first_name, last_name, birth_date, position, created_by, claimed_at)
  values (auth.uid(), trim(p_first), trim(p_last), v_birth, nullif(trim(coalesce(p_position,'')), ''), auth.uid(), now())
  returning id into v_id;
  return v_id;
end $fn$;

-- 5.4 El DT designa al operador del partido (1 activo a la vez)
create or replace function public.set_match_operator(p_match uuid, p_user uuid)
returns void language plpgsql security definer set search_path = public as $fn$
declare v_academy uuid;
begin
  if not public.is_match_manager(p_match) then raise exception 'Solo el DT del equipo puede designar al operador'; end if;
  select academy_id into v_academy from public.matches where id = p_match;
  if not public.user_is_academy_member(p_user, v_academy) then
    raise exception 'El operador debe ser miembro de la academia';
  end if;
  update public.match_operators set active = false where match_id = p_match and active;
  insert into public.match_operators (match_id, user_id, assigned_by) values (p_match, p_user, auth.uid());
  perform public.log_audit('set_match_operator', 'match', p_match, jsonb_build_object('operator', p_user));
end $fn$;

-- 5.5 Estado del partido + evento de control + reloj
create or replace function public.change_match_state(p_match uuid, p_action text)
returns void language plpgsql security definer set search_path = public as $fn$
declare
  m public.matches%rowtype; v_now timestamptz := now(); v_code text; v_period smallint;
  v_elapsed int := 0; v_min int; v_n int; v_type uuid;
begin
  select * into m from public.matches where id = p_match for update;
  if not found then raise exception 'Partido inexistente'; end if;
  if not (public.is_match_manager(p_match) or public.is_match_operator(p_match)) then
    raise exception 'Sin permiso para controlar este partido';
  end if;

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

  -- 1) primero el evento (el trigger lo marca "tardío" solo si el partido ya estaba finalizado)
  insert into public.match_events (match_id, client_event_id, event_type_id, period, minute, team_side)
  values (p_match, gen_random_uuid(), v_type, v_period, v_min, 'none');

  -- 2) luego el estado y el reloj
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
  perform public.log_audit('match_state_' || p_action, 'match', p_match, '{}'::jsonb);
end $fn$;

-- 5.6 Corregir un evento (conserva revisión automática)
create or replace function public.correct_match_event(p_event uuid, p_patch jsonb, p_reason text)
returns void language plpgsql security definer set search_path = public as $fn$
declare e public.match_events%rowtype; v_status public.match_status;
begin
  select * into e from public.match_events where id = p_event for update;
  if not found then raise exception 'Evento inexistente'; end if;
  select status into v_status from public.matches where id = e.match_id;
  if not (public.is_match_manager(e.match_id)
          or (public.is_match_operator(e.match_id) and v_status in ('live','halftime'))) then
    raise exception 'Sin permiso para corregir este evento';
  end if;
  if coalesce(trim(p_reason), '') = '' then raise exception 'Indica el motivo de la corrección'; end if;
  perform set_config('app.change_reason', p_reason, true);
  update public.match_events set
    minute             = coalesce((p_patch->>'minute')::smallint, minute),
    minute_extra       = coalesce((p_patch->>'minute_extra')::smallint, minute_extra),
    team_side          = coalesce(p_patch->>'team_side', team_side),
    event_type_id      = coalesce((p_patch->>'event_type_id')::uuid, event_type_id),
    player_id          = case when p_patch ? 'player_id' then nullif(p_patch->>'player_id', '')::uuid else player_id end,
    external_player_id = case when p_patch ? 'external_player_id' then nullif(p_patch->>'external_player_id', '')::uuid else external_player_id end,
    payload            = case when p_patch ? 'payload' then p_patch->'payload' else payload end,
    notes              = case when p_patch ? 'notes' then p_patch->>'notes' else notes end
  where id = p_event;
  perform public.log_audit('correct_event', 'match_event', p_event, jsonb_build_object('reason', p_reason));
end $fn$;

-- 5.7 Anular un evento (no se borra; queda trazado)
create or replace function public.void_match_event(p_event uuid, p_reason text)
returns void language plpgsql security definer set search_path = public as $fn$
declare e public.match_events%rowtype; v_status public.match_status;
begin
  select * into e from public.match_events where id = p_event for update;
  if not found then raise exception 'Evento inexistente'; end if;
  select status into v_status from public.matches where id = e.match_id;
  if not (public.is_match_manager(e.match_id)
          or (public.is_match_operator(e.match_id) and v_status in ('live','halftime'))) then
    raise exception 'Sin permiso para anular este evento';
  end if;
  if coalesce(trim(p_reason), '') = '' then raise exception 'Indica el motivo de la anulación'; end if;
  perform set_config('app.change_reason', p_reason, true);
  update public.match_events set status = 'voided', voided_by = auth.uid(), voided_at = now(), void_reason = p_reason
  where id = p_event;
  perform public.log_audit('void_event', 'match_event', p_event, jsonb_build_object('reason', p_reason));
end $fn$;

-- 5.8 Cronología (con nombres según privacidad). El cliente la vuelve a pedir
--     cada vez que Realtime avisa de un cambio.
create or replace function public.get_match_timeline(p_match uuid)
returns table (
  event_id uuid, seq int, code text, type_name text, icon text, group_kind text,
  period smallint, minute smallint, minute_extra smallint, team_side text,
  player_id uuid, player_name text, jersey_number smallint,
  payload jsonb, notes text, status public.event_status, recorded_late boolean, created_at timestamptz)
language sql stable security definer set search_path = public as $fn$
  select e.id, e.seq, t.code, t.name, t.icon, t.group_kind,
         e.period, e.minute, e.minute_extra, e.team_side,
         e.player_id,
         case when e.player_id is not null then public.player_display_name(e.player_id)
              when e.external_player_id is not null then (select x.name from public.match_external_players x where x.id = e.external_player_id)
         end,
         coalesce(
           (select tp.jersey_number from public.team_players tp join public.matches mm on mm.id = e.match_id
             where tp.player_id = e.player_id
               and tp.team_id = case e.team_side when 'home' then mm.home_team_id else mm.away_team_id end limit 1),
           (select x.jersey_number from public.match_external_players x where x.id = e.external_player_id)),
         e.payload, e.notes, e.status, e.recorded_late, e.created_at
  from public.match_events e
  join public.event_types t on t.id = e.event_type_id
  where e.match_id = p_match and public.can_view_match(p_match)
  order by e.seq
$fn$;

create or replace function public.get_match_lineup(p_match uuid)
returns table (team_side text, player_id uuid, external_player_id uuid, player_name text,
               jersey_number smallint, is_starter boolean, field_position text)
language sql stable security definer set search_path = public as $fn$
  select l.team_side, l.player_id, null::uuid, public.player_display_name(l.player_id),
         coalesce(l.jersey_number, (select tp.jersey_number from public.team_players tp join public.matches mm on mm.id = l.match_id
                  where tp.player_id = l.player_id and tp.team_id = case l.team_side when 'home' then mm.home_team_id else mm.away_team_id end limit 1)),
         l.is_starter, l.field_position
  from public.match_lineup_players l
  where l.match_id = p_match and public.can_view_match(p_match)
  union all
  select 'away', null::uuid, x.id, x.name, x.jersey_number, x.is_starter, null
  from public.match_external_players x
  where x.match_id = p_match and public.can_view_match(p_match)
$fn$;

-- 5.9 Estadísticas (derivadas de eventos vigentes de partidos FINALIZADOS)
create or replace function public.get_player_stats(p_player uuid)
returns table (matches_played int, goals int, assists int, yellow_cards int, red_cards int,
               fouls int, saves int, own_goals int)
language plpgsql stable security definer set search_path = public as $fn$
begin
  if not public.can_view_player_private(p_player) then
    raise exception 'Sin permiso para ver estas estadísticas';
  end if;
  return query
  with ev as (
    select e.*, t.code, coalesce(e.payload->>'own_goal', 'false') = 'true' as own
    from public.match_events e
    join public.event_types t on t.id = e.event_type_id
    join public.matches m on m.id = e.match_id
    where m.status = 'finished' and e.status = 'active')
  select
    (select count(distinct m.id)::int from public.matches m where m.status = 'finished' and (
        exists (select 1 from public.match_lineup_players l where l.match_id = m.id and l.player_id = p_player)
        or exists (select 1 from ev where ev.match_id = m.id and ev.player_id = p_player))),
    (select count(*)::int from ev where ev.player_id = p_player and not ev.own
        and (ev.code = 'goal' or (ev.code = 'penalty' and ev.payload->>'result' = 'converted'))),
    (select count(*)::int from ev where ev.code = 'goal' and not ev.own and ev.payload->>'assist_player_id' = p_player::text),
    (select count(*)::int from ev where ev.player_id = p_player and ev.code = 'yellow_card'),
    (select count(*)::int from ev where ev.player_id = p_player and ev.code = 'red_card'),
    (select count(*)::int from ev where ev.player_id = p_player and ev.code = 'foul'),
    (select count(*)::int from ev where ev.player_id = p_player and ev.code = 'save'),
    (select count(*)::int from ev where ev.player_id = p_player and ev.code = 'goal' and ev.own);
end $fn$;

create or replace function public.get_team_stats(p_team uuid)
returns table (matches_played int, wins int, draws int, losses int, goals_for int, goals_against int)
language plpgsql stable security definer set search_path = public as $fn$
begin
  if not (public.is_platform_admin() or public.is_academy_member(public.team_academy(p_team))) then
    raise exception 'Sin permiso para ver estas estadísticas';
  end if;
  return query
  with g as (
    select case when m.home_team_id = p_team then m.home_score else m.away_score end as gf,
           case when m.home_team_id = p_team then m.away_score else m.home_score end as ga
    from public.matches m
    where m.status = 'finished' and (m.home_team_id = p_team or m.away_team_id = p_team))
  select count(*)::int,
         count(*) filter (where gf > ga)::int, count(*) filter (where gf = ga)::int, count(*) filter (where gf < ga)::int,
         coalesce(sum(gf), 0)::int, coalesce(sum(ga), 0)::int
  from g;
end $fn$;

-- 5.10 IA: funciones para la Edge Function (service_role). La IA NUNCA recibe nombres.
create or replace function public._ai_alias_map(p_match uuid)
returns table (alias text, kind text, ref uuid)
language sql stable security definer set search_path = public as $fn$
  with ev as (select * from public.match_events where match_id = p_match and status = 'active'),
  ids as (
    select distinct pid from (
      select player_id::text as pid from ev
      union all select payload->>'assist_player_id' from ev
      union all select payload->>'player_in_id' from ev
      union all select payload->>'player_out_id' from ev) s
    where pid is not null and pid <> ''),
  ext as (select distinct external_player_id::text as pid from ev where external_player_id is not null)
  select 'J' || (row_number() over (order by md5(pid || p_match::text)))::text, 'player', pid::uuid from ids
  union all
  select 'R' || (row_number() over (order by pid))::text, 'external', pid::uuid from ext
$fn$;

create or replace function public.build_ai_payload(p_match uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $fn$
declare v jsonb;
begin
  with am as (select * from public._ai_alias_map(p_match)),
  mt as (
    select m.*, ht.name as home_name, coalesce(at.name, m.away_external_name) as away_name, ht.category
    from public.matches m
    join public.teams ht on ht.id = m.home_team_id
    left join public.teams at on at.id = m.away_team_id
    where m.id = p_match),
  ev as (
    select e.*, t.code, t.name as type_name,
      (select tp.jersey_number from public.team_players tp
        where tp.player_id = e.player_id
          and tp.team_id = case e.team_side when 'home' then (select home_team_id from mt) else (select away_team_id from mt) end limit 1) as jersey,
      (select x.jersey_number from public.match_external_players x where x.id = e.external_player_id) as ext_jersey
    from public.match_events e join public.event_types t on t.id = e.event_type_id
    where e.match_id = p_match and e.status = 'active')
  select jsonb_build_object(
    'match', (select jsonb_build_object('home_team', home_name, 'away_team', away_name, 'category', category,
                'players_per_side', format_players, 'minutes_per_half', half_minutes,
                'home_score', home_score, 'away_score', away_score, 'status', status) from mt),
    'events', coalesce((select jsonb_agg(jsonb_strip_nulls(jsonb_build_object(
          'seq', ev.seq, 'period', ev.period, 'minute', ev.minute,
          'minute_extra', nullif(ev.minute_extra, 0),
          'type', ev.code, 'type_name', ev.type_name, 'team_side', ev.team_side,
          'actor', coalesce((select alias from am where kind = 'player' and ref = ev.player_id),
                            (select alias from am where kind = 'external' and ref = ev.external_player_id)),
          'jersey', coalesce(ev.jersey, ev.ext_jersey),
          'details', jsonb_strip_nulls(jsonb_build_object(
              'result', ev.payload->>'result',
              'own_goal', ev.payload->'own_goal',
              'assist', (select alias from am where kind = 'player' and ref::text = ev.payload->>'assist_player_id'),
              'player_in', (select alias from am where kind = 'player' and ref::text = ev.payload->>'player_in_id'),
              'player_out', (select alias from am where kind = 'player' and ref::text = ev.payload->>'player_out_id')))
        )) order by ev.seq) from ev), '[]'::jsonb))
  into v;
  return v;
end $fn$;

create or replace function public.render_ai_text(p_match uuid, p_text text)
returns text language plpgsql stable security definer set search_path = public as $fn$
declare r record; v_text text := p_text; v_name text;
begin
  for r in select * from public._ai_alias_map(p_match) order by length(alias) desc, alias desc loop
    if r.kind = 'player' then v_name := public.player_public_name(r.ref);
    else select name into v_name from public.match_external_players where id = r.ref; end if;
    v_text := regexp_replace(v_text, '\m' || r.alias || '\M', replace(coalesce(v_name, r.alias), E'\\', E'\\\\'), 'g');
  end loop;
  return v_text;
end $fn$;

create or replace function public.can_generate_summary(p_match uuid)
returns boolean language sql stable security definer set search_path = public as $fn$
  select public.is_match_manager(p_match)
     and exists (select 1 from public.matches where id = p_match and status = 'finished')
$fn$;

create or replace function public.upsert_ai_summary(p_match uuid, p_text text, p_model text)
returns uuid language plpgsql security definer set search_path = public as $fn$
declare v_row public.match_summaries%rowtype;
begin
  if not exists (select 1 from public.matches where id = p_match and status = 'finished') then
    raise exception 'El resumen solo se genera para partidos finalizados';
  end if;
  insert into public.match_summaries (match_id, ai_text, final_text, generation_count, model, last_generated_at, status)
  values (p_match, p_text, p_text, 1, p_model, now(), 'draft')
  on conflict (match_id) do update set
    ai_text = excluded.ai_text, final_text = excluded.final_text,
    generation_count = public.match_summaries.generation_count + 1,
    model = excluded.model, last_generated_at = now(), status = 'draft'
  where public.match_summaries.status <> 'approved' and public.match_summaries.generation_count < 3
  returning * into v_row;
  if not found then raise exception 'Límite de 3 regeneraciones alcanzado o el resumen ya fue aprobado'; end if;
  insert into public.match_summary_versions (summary_id, version_no, source, text)
  values (v_row.id, coalesce((select max(version_no) from public.match_summary_versions where summary_id = v_row.id), 0) + 1, 'ai', p_text);
  return v_row.id;
end $fn$;

create or replace function public.save_summary_edit(p_match uuid, p_text text)
returns void language plpgsql security definer set search_path = public as $fn$
declare v_id uuid;
begin
  if not public.is_match_manager(p_match) then raise exception 'Solo el DT puede editar el resumen'; end if;
  if coalesce(trim(p_text), '') = '' then raise exception 'El resumen no puede estar vacío'; end if;
  update public.match_summaries set final_text = p_text, status = 'edited', edited_by = auth.uid()
  where match_id = p_match and status <> 'approved' returning id into v_id;
  if v_id is null then raise exception 'No hay borrador editable'; end if;
  insert into public.match_summary_versions (summary_id, version_no, source, text, created_by)
  values (v_id, coalesce((select max(version_no) from public.match_summary_versions where summary_id = v_id), 0) + 1, 'manual', p_text, auth.uid());
end $fn$;

create or replace function public.approve_summary(p_match uuid, p_visibility text)
returns void language plpgsql security definer set search_path = public as $fn$
begin
  if not public.is_match_manager(p_match) then raise exception 'Solo el DT puede aprobar el resumen'; end if;
  if p_visibility not in ('public','academy','team') then raise exception 'Visibilidad inválida'; end if;
  update public.match_summaries set status = 'approved', visibility = p_visibility,
         approved_by = auth.uid(), approved_at = now()
  where match_id = p_match and status <> 'approved';
  if not found then raise exception 'No hay borrador para aprobar'; end if;
  perform public.log_audit('approve_summary', 'match', p_match, jsonb_build_object('visibility', p_visibility));
end $fn$;

-- ---------------------------------------------------------------------
-- 6. ROW LEVEL SECURITY
-- ---------------------------------------------------------------------
alter table public.profiles               enable row level security;
alter table public.platform_admins        enable row level security;
alter table public.academies              enable row level security;
alter table public.academy_memberships    enable row level security;
alter table public.teams                  enable row level security;
alter table public.team_staff             enable row level security;
alter table public.players                enable row level security;
alter table public.player_private_data    enable row level security;
alter table public.team_players           enable row level security;
alter table public.guardian_player_links  enable row level security;
alter table public.follows                enable row level security;
alter table public.matches                enable row level security;
alter table public.match_external_players enable row level security;
alter table public.match_operators        enable row level security;
alter table public.match_callups          enable row level security;
alter table public.match_lineup_players   enable row level security;
alter table public.event_types            enable row level security;
alter table public.match_events           enable row level security;
alter table public.match_event_revisions  enable row level security;
alter table public.match_summaries        enable row level security;
alter table public.match_summary_versions enable row level security;
alter table public.audit_log              enable row level security;
-- platform_admins: sin políticas = nadie desde el cliente.

create policy profiles_select on public.profiles for select to authenticated
  using (id = auth.uid() or public.is_platform_admin() or public.shares_academy_with_user(id));
create policy profiles_update on public.profiles for update to authenticated
  using (id = auth.uid()) with check (id = auth.uid());

create policy academies_select on public.academies for select to authenticated
  using (public.is_platform_admin() or public.is_academy_member(id) or status = 'verified');
create policy academies_insert on public.academies for insert to authenticated
  with check (public.is_platform_admin());
create policy academies_update on public.academies for update to authenticated
  using (public.is_platform_admin() or public.has_academy_role(id, 'adm'))
  with check (public.is_platform_admin() or public.has_academy_role(id, 'adm'));
create policy academies_delete on public.academies for delete to authenticated
  using (public.is_platform_admin());

create policy memberships_select on public.academy_memberships for select to authenticated
  using (user_id = auth.uid() or public.is_platform_admin() or public.is_academy_staff(academy_id));
create policy memberships_insert on public.academy_memberships for insert to authenticated
  with check (public.is_platform_admin() or (public.has_academy_role(academy_id, 'adm') and role <> 'adm'));
create policy memberships_update on public.academy_memberships for update to authenticated
  using (public.is_platform_admin() or (public.has_academy_role(academy_id, 'adm') and role <> 'adm'))
  with check (public.is_platform_admin() or (public.has_academy_role(academy_id, 'adm') and role <> 'adm'));
create policy memberships_delete on public.academy_memberships for delete to authenticated
  using (public.is_platform_admin() or (public.has_academy_role(academy_id, 'adm') and role <> 'adm'));

create policy teams_select on public.teams for select to authenticated
  using (public.is_platform_admin() or public.is_academy_member(academy_id) or public.academy_is_verified(academy_id));
create policy teams_write on public.teams for all to authenticated
  using (public.is_platform_admin() or public.has_academy_role(academy_id, 'adm'))
  with check (public.is_platform_admin() or public.has_academy_role(academy_id, 'adm'));

create policy team_staff_select on public.team_staff for select to authenticated
  using (public.is_platform_admin() or public.is_academy_member(public.team_academy(team_id)));
create policy team_staff_insert on public.team_staff for insert to authenticated
  with check ((public.is_platform_admin() or public.has_academy_role(public.team_academy(team_id), 'adm'))
              and public.user_has_academy_role(user_id, public.team_academy(team_id), 'dt'));
create policy team_staff_delete on public.team_staff for delete to authenticated
  using (public.is_platform_admin() or public.has_academy_role(public.team_academy(team_id), 'adm'));

create policy players_select on public.players for select to authenticated
  using (public.can_view_player(id));
create policy players_update on public.players for update to authenticated
  using (public.is_guardian_of(id) or user_id = auth.uid() or public.is_platform_admin())
  with check (public.is_guardian_of(id) or user_id = auth.uid() or public.is_platform_admin());

create policy ppd_select on public.player_private_data for select to authenticated
  using (public.can_view_player_private(player_id));
create policy ppd_insert on public.player_private_data for insert to authenticated
  with check (public.can_manage_player_data(player_id));
create policy ppd_update on public.player_private_data for update to authenticated
  using (public.can_manage_player_data(player_id)) with check (public.can_manage_player_data(player_id));

create policy team_players_select on public.team_players for select to authenticated
  using (public.is_team_manager(team_id) or public.is_academy_member(public.team_academy(team_id))
         or public.is_guardian_of(player_id)
         or exists (select 1 from public.players pl where pl.id = player_id and pl.user_id = auth.uid()));
create policy team_players_write on public.team_players for all to authenticated
  using (public.is_team_manager(team_id)) with check (public.is_team_manager(team_id));

create policy gpl_select on public.guardian_player_links for select to authenticated
  using (guardian_user_id = auth.uid() or public.can_view_player_private(player_id));

create policy follows_select on public.follows for select to authenticated using (user_id = auth.uid());
create policy follows_insert on public.follows for insert to authenticated
  with check (user_id = auth.uid()
    and (academy_id is null or public.academy_is_verified(academy_id) or public.is_academy_member(academy_id))
    and (team_id is null or public.academy_is_verified(public.team_academy(team_id)) or public.is_academy_member(public.team_academy(team_id))));
create policy follows_delete on public.follows for delete to authenticated using (user_id = auth.uid());

create policy matches_select on public.matches for select to authenticated using (public.can_view_match(id));
create policy matches_insert on public.matches for insert to authenticated
  with check (public.is_team_manager(home_team_id)
    and (away_team_id is null
         or public.team_academy(away_team_id) = public.team_academy(home_team_id)
         or public.academy_is_verified(public.team_academy(away_team_id))));
create policy matches_update on public.matches for update to authenticated
  using (public.is_match_manager(id) and status = 'scheduled')
  with check (public.is_match_manager(id));

create policy mep_select on public.match_external_players for select to authenticated
  using (public.can_view_match(match_id));
create policy mep_write on public.match_external_players for all to authenticated
  using (public.is_match_manager(match_id) or public.is_match_operator(match_id))
  with check (public.is_match_manager(match_id) or public.is_match_operator(match_id));

create policy mop_select on public.match_operators for select to authenticated
  using (public.is_match_manager(match_id) or user_id = auth.uid());

create policy callups_select on public.match_callups for select to authenticated
  using (public.is_team_manager(team_id) or public.is_guardian_of(player_id)
         or exists (select 1 from public.players pl where pl.id = player_id and pl.user_id = auth.uid()));
create policy callups_insert on public.match_callups for insert to authenticated
  with check (public.is_team_manager(team_id)
    and exists (select 1 from public.team_players tp where tp.team_id = match_callups.team_id and tp.player_id = match_callups.player_id and tp.status = 'active')
    and exists (select 1 from public.matches m where m.id = match_id and m.status = 'scheduled'
                and (m.home_team_id = match_callups.team_id or m.away_team_id = match_callups.team_id)));
create policy callups_update on public.match_callups for update to authenticated
  using (public.is_team_manager(team_id) or public.is_guardian_of(player_id)
         or exists (select 1 from public.players pl where pl.id = player_id and pl.user_id = auth.uid()))
  with check (public.is_team_manager(team_id) or public.is_guardian_of(player_id)
         or exists (select 1 from public.players pl where pl.id = player_id and pl.user_id = auth.uid()));
create policy callups_delete on public.match_callups for delete to authenticated
  using (public.is_team_manager(team_id));

create policy lineup_select on public.match_lineup_players for select to authenticated
  using (public.can_view_match(match_id));
create policy lineup_write on public.match_lineup_players for all to authenticated
  using (public.is_match_manager(match_id)) with check (public.is_match_manager(match_id));

create policy event_types_select on public.event_types for select to authenticated
  using (academy_id is null or public.is_academy_member(academy_id) or public.is_platform_admin());
create policy event_types_insert on public.event_types for insert to authenticated
  with check (public.is_platform_admin() or (academy_id is not null and public.is_academy_staff(academy_id)));
create policy event_types_update on public.event_types for update to authenticated
  using (public.is_platform_admin() or (academy_id is not null and public.is_academy_staff(academy_id)))
  with check (public.is_platform_admin() or (academy_id is not null and public.is_academy_staff(academy_id)));

create policy match_events_select on public.match_events for select to authenticated
  using (public.can_view_match(match_id));
create policy match_events_insert on public.match_events for insert to authenticated
  with check (public.can_record_event(match_id));
-- Sin políticas de UPDATE/DELETE: corregir/anular solo por las funciones correct_/void_match_event.

create policy mer_select on public.match_event_revisions for select to authenticated
  using (exists (select 1 from public.match_events e where e.id = event_id and public.is_match_manager(e.match_id)));

create policy ms_select on public.match_summaries for select to authenticated
  using (public.is_match_manager(match_id));
create policy msv_select on public.match_summary_versions for select to authenticated
  using (exists (select 1 from public.match_summaries s where s.id = summary_id and public.is_match_manager(s.match_id)));

create policy audit_select on public.audit_log for select to authenticated
  using (public.is_platform_admin());

-- ---------------------------------------------------------------------
-- 7. PRIVILEGIOS (defensa en profundidad)
-- ---------------------------------------------------------------------
revoke all on all tables    in schema public from anon, authenticated;
revoke all on all sequences in schema public from anon, authenticated;
revoke execute on all functions in schema public from public, anon, authenticated;

grant select on public.profiles, public.academies, public.academy_memberships, public.teams,
  public.team_staff, public.players, public.player_private_data, public.team_players,
  public.guardian_player_links, public.follows, public.matches, public.match_external_players,
  public.match_operators, public.match_callups, public.match_lineup_players, public.event_types,
  public.match_events, public.match_event_revisions, public.match_summaries,
  public.match_summary_versions, public.audit_log to authenticated;

grant update (full_name, avatar_path) on public.profiles to authenticated;
grant insert (id, name, city, crest_path, status) on public.academies to authenticated;
grant update (name, city, crest_path, status) on public.academies to authenticated;
grant delete on public.academies to authenticated;
grant insert (academy_id, user_id, role, status) on public.academy_memberships to authenticated;
grant update (role, status) on public.academy_memberships to authenticated;
grant delete on public.academy_memberships to authenticated;
grant insert, update, delete on public.teams to authenticated;
grant insert, delete on public.team_staff to authenticated;
grant update (first_name, last_name, position, photo_path, photo_authorized) on public.players to authenticated;
grant insert, update on public.player_private_data to authenticated;
grant insert, update, delete on public.team_players to authenticated;
grant insert (academy_id, team_id), delete on public.follows to authenticated;
grant insert (id, home_team_id, away_team_id, away_external_name, format_players, half_minutes,
              scheduled_at, venue, visibility) on public.matches to authenticated;
grant update (scheduled_at, venue, visibility, format_players, half_minutes, away_external_name) on public.matches to authenticated;
grant insert, update, delete on public.match_external_players to authenticated;
grant insert (match_id, team_id, player_id, note), delete on public.match_callups to authenticated;
grant update (status, note) on public.match_callups to authenticated;
grant insert, update, delete on public.match_lineup_players to authenticated;
grant insert, update on public.event_types to authenticated;
grant insert (match_id, client_event_id, event_type_id, period, minute, minute_extra, team_side,
              player_id, external_player_id, payload, notes) on public.match_events to authenticated;

-- Funciones que usa el cliente (y las políticas RLS)
grant execute on function
  public.age_years(date), public.is_platform_admin(), public.user_has_academy_role(uuid, uuid, public.app_role),
  public.has_academy_role(uuid, public.app_role), public.user_is_academy_member(uuid, uuid),
  public.is_academy_member(uuid), public.is_academy_staff(uuid), public.academy_is_verified(uuid),
  public.team_academy(uuid), public.is_team_staff(uuid), public.is_team_manager(uuid),
  public.shares_academy_with_user(uuid), public.my_age(), public.is_guardian_of(uuid),
  public.can_view_player(uuid), public.can_view_player_private(uuid), public.can_manage_player_data(uuid),
  public.player_public_name(uuid), public.player_display_name(uuid),
  public.is_match_manager(uuid), public.is_match_operator(uuid), public.can_view_match(uuid),
  public.can_record_event(uuid), public.can_generate_summary(uuid),
  public.set_my_birth_date(date), public.create_child_player(text, text, date, text),
  public.create_my_player(text, text, text), public.set_match_operator(uuid, uuid),
  public.change_match_state(uuid, text), public.correct_match_event(uuid, jsonb, text),
  public.void_match_event(uuid, text), public.get_match_timeline(uuid), public.get_match_lineup(uuid),
  public.get_player_stats(uuid), public.get_team_stats(uuid),
  public.save_summary_edit(uuid, text), public.approve_summary(uuid, text)
to authenticated;

-- Solo la Edge Function (service_role)
grant execute on function
  public.build_ai_payload(uuid), public.render_ai_text(uuid, text),
  public.upsert_ai_summary(uuid, text, text), public._ai_alias_map(uuid)
to service_role;

-- ---------------------------------------------------------------------
-- 8. CATÁLOGO BASE DE EVENTOS (global). Iconos = claves que la app mapea.
-- ---------------------------------------------------------------------
insert into public.event_types (code, name, icon, group_kind, scoring_rule, required_payload, sort_order) values
  ('match_start',       'Inicio de partido',          'whistle',       'control',   'none',         '{}', 1),
  ('first_half_end',    'Fin del primer tiempo',      'half_time',     'control',   'none',         '{}', 2),
  ('second_half_start', 'Inicio del segundo tiempo',  'whistle',       'control',   'none',         '{}', 3),
  ('match_end',         'Fin de partido',             'whistle_end',   'control',   'none',         '{}', 4),
  ('goal',              'Gol',                        'ball',          'main',      'always',       '{}', 10),
  ('penalty',           'Penal',                      'penalty',       'main',      'if_converted', '{result}', 11),
  ('yellow_card',       'Tarjeta amarilla',           'card_yellow',   'main',      'none',         '{}', 12),
  ('red_card',          'Tarjeta roja',               'card_red',      'main',      'none',         '{}', 13),
  ('substitution',      'Cambio',                     'swap',          'main',      'none',         '{player_out_id,player_in_id}', 14),
  ('foul',              'Falta',                      'foul',          'main',      'none',         '{}', 15),
  ('corner',            'Tiro de esquina',            'corner',        'secondary', 'none',         '{}', 20),
  ('free_kick',         'Tiro libre',                 'free_kick',     'secondary', 'none',         '{}', 21),
  ('goal_kick',         'Saque de meta',              'goal_kick',     'secondary', 'none',         '{}', 22),
  ('throw_in',          'Saque lateral',              'throw_in',      'secondary', 'none',         '{}', 23),
  ('offside',           'Fuera de juego',             'offside',       'secondary', 'none',         '{}', 24),
  ('save',              'Atajada',                    'glove',         'secondary', 'none',         '{}', 25),
  ('clearance',         'Despeje',                    'clearance',     'secondary', 'none',         '{}', 26);

-- ---------------------------------------------------------------------
-- 9. REALTIME (marcador, cronología, convocatorias, catálogo)
-- ---------------------------------------------------------------------
do $do$
begin
  if exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
    alter publication supabase_realtime add table
      public.matches, public.match_events, public.match_callups, public.event_types;
  end if;
end $do$;

commit;
