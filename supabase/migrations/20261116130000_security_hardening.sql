-- =====================================================================
-- SportPro · Migración 013: cierre de huecos encontrados en la revisión
-- final de seguridad (supabase/test/security_audit.sql).
--
--  1. Las tablas `invitations` y `notifications` (migración 003) nunca recibieron el
--     «revoke» inicial. Supabase da por defecto TODOS los permisos de tabla a `anon` y
--     `authenticated`, así que:
--        · `anon` tenía permisos sobre ellas (RLS no le devolvía filas, pero el permiso sobraba);
--        · un usuario podía cambiar el título o el texto de SUS notificaciones, no solo
--          marcarlas como leídas.
--     Ahora quedan como el resto: nada para `anon`; `authenticated` solo lee (y en
--     notifications puede actualizar únicamente la columna read_at).
--  2. Un admin de academia podía cambiar el ESTADO de su propia academia (por ejemplo,
--     volver a «verified» una academia suspendida por el superadmin). Ahora solo el
--     superadmin puede cambiar ese campo.
--  3. Algunas funciones internas quedaron ejecutables por `anon`. Se les quita el permiso.
--     La única función que `anon` debe poder llamar es check_invitation(), porque valida
--     el código de invitación ANTES de que exista una sesión (pantalla de registro).
--
-- Cómo ejecutarla: Supabase Dashboard → SQL Editor → pegar TODO → Run (transaccional).
-- =====================================================================
begin;

-- ---------------------------------------------------------------------
-- 1. Permisos de tabla de invitations y notifications
-- ---------------------------------------------------------------------
revoke all on public.invitations, public.notifications from anon, authenticated;
grant select on public.invitations to authenticated;                 -- crear / usar: solo por funciones
grant select on public.notifications to authenticated;
grant update (read_at) on public.notifications to authenticated;     -- marcar como leída, nada más

-- ---------------------------------------------------------------------
-- 2. Solo el superadmin cambia el estado de una academia
-- ---------------------------------------------------------------------
create or replace function public.trg_academies_status_guard()
returns trigger language plpgsql security definer set search_path = public as $fn$
begin
  -- auth.uid() es null cuando el cambio viene del SQL Editor o de service_role (dueño del
  -- proyecto): ahí se permite. Desde la app siempre hay un usuario, y debe ser superadmin.
  if new.status is distinct from old.status and auth.uid() is not null and not public.is_platform_admin() then
    raise exception 'Solo el superadministrador puede cambiar el estado de una academia';
  end if;
  return new;
end $fn$;

drop trigger if exists trg_academies_status_guard on public.academies;
create trigger trg_academies_status_guard before update of status on public.academies
  for each row execute function public.trg_academies_status_guard();

-- ---------------------------------------------------------------------
-- 3. Funciones internas: fuera del alcance de anon
-- ---------------------------------------------------------------------
-- Las funciones de trigger nunca se llaman a mano: se les quita el permiso a todos.
do $$
declare r record;
begin
  for r in
    select p.oid::regprocedure as sig
    from pg_proc p join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and p.prorettype = 'pg_catalog.trigger'::regtype
  loop
    execute format('revoke execute on function %s from public, anon, authenticated', r.sig);
  end loop;
end $$;

revoke execute on function public._invite_label(text) from public, anon, authenticated;

-- Estas dos las usan las políticas de Storage, que se evalúan con el rol del usuario:
-- `authenticated` debe poder ejecutarlas; `anon` no.
revoke execute on function public.can_manage_academy_folder(text), public.can_publish_avatar(uuid) from public, anon;
grant execute on function public.can_manage_academy_folder(text), public.can_publish_avatar(uuid) to authenticated;

commit;
