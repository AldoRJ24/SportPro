-- =====================================================================
-- SportPro · Arranque para pruebas LOCALES (Postgres limpio en Docker).
--
-- Un Postgres normal no trae lo que Supabase crea solo: los roles anon /
-- authenticated / service_role, el esquema auth (usuarios y auth.uid()) y el
-- esquema storage. Este archivo crea una IMITACIÓN mínima de todo eso para poder
-- ejecutar las migraciones y supabase/test/smoke_test.sql sin tocar el proyecto real.
--
-- NUNCA ejecutar esto en Supabase: allá ya existe todo de verdad.
-- Uso: ver supabase/test/run_local.sh
-- =====================================================================

-- Roles que usa Supabase
do $$ begin
  if not exists (select 1 from pg_roles where rolname = 'anon')          then create role anon nologin; end if;
  if not exists (select 1 from pg_roles where rolname = 'authenticated') then create role authenticated nologin; end if;
  if not exists (select 1 from pg_roles where rolname = 'service_role')  then create role service_role nologin bypassrls; end if;
end $$;

-- Esquema auth: tabla de usuarios y auth.uid() (lee el «sub» del token, igual que Supabase)
create schema if not exists auth;
create table if not exists auth.users (
  id                 uuid primary key default gen_random_uuid(),
  email              text,
  raw_user_meta_data jsonb default '{}'::jsonb,
  created_at         timestamptz default now()
);
create or replace function auth.uid() returns uuid language sql stable as $fn$
  select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid
$fn$;
grant usage on schema auth to anon, authenticated, service_role;
grant execute on function auth.uid() to anon, authenticated, service_role;

-- Esquema storage: solo lo que tocan las migraciones (buckets, objects y foldername)
create schema if not exists storage;
create table if not exists storage.buckets (
  id                 text primary key,
  name               text not null,
  public             boolean default false,
  file_size_limit    bigint,
  allowed_mime_types text[]
);
create table if not exists storage.objects (
  id        uuid primary key default gen_random_uuid(),
  bucket_id text references storage.buckets(id),
  name      text,
  owner     uuid
);
alter table storage.objects enable row level security;
create or replace function storage.foldername(name text) returns text[] language sql immutable as $fn$
  select (string_to_array(name, '/'))[1 : array_length(string_to_array(name, '/'), 1) - 1]
$fn$;
grant usage on schema storage to anon, authenticated, service_role;
grant all on storage.objects, storage.buckets to authenticated, service_role;

-- Igual que Supabase: todo lo que se cree en «public» nace con permisos para estos roles.
-- Por eso las migraciones hacen «revoke» explícito, y aquí podemos comprobar que funciona.
grant usage on schema public to anon, authenticated, service_role;
alter default privileges in schema public grant all on tables    to anon, authenticated, service_role;
alter default privileges in schema public grant all on sequences to anon, authenticated, service_role;
alter default privileges in schema public grant execute on functions to anon, authenticated, service_role;
