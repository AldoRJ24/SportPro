-- =====================================================================
-- SportPro · Auditoría de seguridad del esquema.
--
-- No prueba un flujo: revisa el CATÁLOGO de Postgres (qué permisos existen de verdad)
-- después de aplicar todas las migraciones. Si alguien agrega una tabla sin RLS o deja
-- un permiso de más, esta auditoría falla y dice cuál.
--
-- La ejecuta supabase/test/run_local.sh. Cada regla imprime OK o lanza un error.
-- =====================================================================
\set ON_ERROR_STOP on
\set QUIET on
\pset tuples_only on

create or replace function pg_temp.expect_none(p_found text, p_label text) returns void language plpgsql as $$
begin
  if coalesce(p_found, '') <> '' then raise exception 'FALLO % -> %', p_label, p_found; end if;
  raise notice 'OK  %', p_label;
end $$;

-- 1) Toda tabla de «public» tiene Row Level Security activado
select pg_temp.expect_none(
  (select string_agg(c.relname, ', ' order by c.relname)
     from pg_class c join pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'public' and c.relkind = 'r' and not c.relrowsecurity),
  'todas las tablas de public tienen RLS activado');

-- 2) El rol anónimo (sin sesión) no tiene NINGÚN permiso sobre tablas de public
select pg_temp.expect_none(
  (select string_agg(distinct table_name, ', ' order by table_name)
     from information_schema.role_table_grants
    where grantee = 'anon' and table_schema = 'public'),
  'anon no tiene permisos sobre ninguna tabla');

select pg_temp.expect_none(
  (select string_agg(distinct table_name, ', ' order by table_name)
     from information_schema.column_privileges
    where grantee = 'anon' and table_schema = 'public'),
  'anon no tiene permisos sobre ninguna columna');

-- 3) anon solo puede ejecutar check_invitation (valida el código antes de registrarse)
select pg_temp.expect_none(
  (select string_agg(p.proname, ', ' order by p.proname)
     from pg_proc p join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and has_function_privilege('anon', p.oid, 'execute')
      and p.proname <> 'check_invitation'),
  'anon solo puede ejecutar check_invitation');

-- 4) Nadie con sesión puede vaciar tablas ni crear triggers o referencias
select pg_temp.expect_none(
  (select string_agg(distinct table_name || ':' || privilege_type, ', ')
     from information_schema.role_table_grants
    where grantee = 'authenticated' and table_schema = 'public'
      and privilege_type in ('TRUNCATE', 'TRIGGER', 'REFERENCES')),
  'authenticated no tiene TRUNCATE, TRIGGER ni REFERENCES');

-- 5) Tablas donde la app NUNCA debe escribir directo (solo por funciones del servidor)
select pg_temp.expect_none(
  (select string_agg(distinct table_name || ':' || privilege_type, ', ')
     from information_schema.role_table_grants
    where grantee = 'authenticated' and table_schema = 'public'
      and privilege_type in ('INSERT', 'UPDATE', 'DELETE')
      and table_name in ('audit_log', 'platform_admins', 'notifications', 'invitations', 'notification_prefs',
                         'fee_plans', 'fee_players', 'fee_charges', 'fee_payments', 'player_documents',
                         'tryouts', 'tryout_applications', 'team_join_requests', 'post_likes', 'post_comments',
                         'match_summaries', 'match_summary_versions', 'match_event_revisions', 'match_operators',
                         'guardian_player_links')),
  'las tablas sensibles solo se escriben por funciones');

-- 6) Nada se borra: no hay permiso de DELETE sobre historial, jugadores, plantel ni eventos
select pg_temp.expect_none(
  (select string_agg(distinct table_name, ', ')
     from information_schema.role_table_grants
    where grantee = 'authenticated' and table_schema = 'public' and privilege_type = 'DELETE'
      and table_name in ('players', 'team_players', 'match_events', 'matches', 'profiles', 'audit_log')),
  'no hay permiso de borrado sobre jugadores, plantel, partidos ni eventos');

-- 7) Las funciones SECURITY DEFINER fijan su search_path (evita que alguien las engañe
--    creando objetos con el mismo nombre en otro esquema)
select pg_temp.expect_none(
  (select string_agg(p.proname, ', ' order by p.proname)
     from pg_proc p join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and p.prosecdef
      and not exists (select 1 from unnest(coalesce(p.proconfig, '{}')) c where c like 'search_path=%')),
  'toda función SECURITY DEFINER fija su search_path');

-- 8) Las funciones internas (nombre con «_» inicial) y las de trigger no son llamables desde la app
select pg_temp.expect_none(
  (select string_agg(p.proname, ', ' order by p.proname)
     from pg_proc p join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and has_function_privilege('authenticated', p.oid, 'execute')
      and (p.proname like '\_%' or p.prorettype = 'pg_catalog.trigger'::regtype)),
  'las funciones internas y de trigger no son ejecutables por la app');

-- 9) El bucket de documentos de menores es privado y sus archivos no se modifican ni se borran
select pg_temp.expect_none(
  (select 'documents es público' from storage.buckets where id = 'documents' and public),
  'el bucket documents es privado');
select pg_temp.expect_none(
  (select string_agg(polname, ', ') from pg_policy
    where polrelid = 'storage.objects'::regclass and polname like 'documents\_%' and polcmd in ('w', 'd')),
  'no hay políticas de UPDATE ni DELETE sobre los documentos');

\echo '=== AUDITORÍA DE SEGURIDAD: SIN HALLAZGOS ==='
