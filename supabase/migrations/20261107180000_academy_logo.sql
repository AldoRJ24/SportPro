-- =====================================================================
-- SportPro · Migración 005: foto de la academia + fotos de entrenadores
-- - Bucket público «academy-logos» (máx. 2 MB), carpeta = id de la academia.
--   Solo el ADMINISTRADOR de esa academia (o el superadmin) puede subir/cambiar/borrar.
-- - academies.crest_path = foto de la academia. Todos sus equipos muestran la MISMA
--   foto: teams.crest_path se copia siempre desde la academia (nadie la edita a mano).
-- - Foto de perfil: además de los mayores de edad con fecha registrada, pueden publicar
--   foto los entrenadores/administradores (staff) aunque su cuenta no tenga fecha.
--   Los menores siguen bloqueados.
-- Cómo ejecutarla: SQL Editor → pegar TODO → Run (transaccional).
-- =====================================================================
begin;

-- 1) ¿Puede esta persona publicar foto de perfil? -----------------------
create or replace function public.can_publish_avatar(p_user uuid)
returns boolean language sql stable security definer set search_path = public as $fn$
  select coalesce((
    select case
      when p.birth_date is not null then public.age_years(p.birth_date) >= 18
      else exists (select 1 from public.academy_memberships m
                    where m.user_id = p.id and m.role in ('adm', 'dt') and m.status = 'active')
        or exists (select 1 from public.team_staff s where s.user_id = p.id)
        or exists (select 1 from public.platform_admins a where a.user_id = p.id)
    end
    from public.profiles p where p.id = p_user), false)
$fn$;
grant execute on function public.can_publish_avatar(uuid) to authenticated;

drop policy if exists avatars_insert_own on storage.objects;
create policy avatars_insert_own on storage.objects for insert to authenticated
  with check (bucket_id = 'avatars'
              and (storage.foldername(name))[1] = auth.uid()::text
              and public.can_publish_avatar(auth.uid()));

drop policy if exists avatars_update_own on storage.objects;
create policy avatars_update_own on storage.objects for update to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text)
  with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text
              and public.can_publish_avatar(auth.uid()));

create or replace function public.trg_profiles_avatar_check()
returns trigger language plpgsql set search_path = public as $fn$
begin
  if new.avatar_path is distinct from old.avatar_path and new.avatar_path is not null then
    if split_part(new.avatar_path, '/', 1) <> new.id::text then
      raise exception 'La foto debe estar en tu propia carpeta';
    end if;
    if not public.can_publish_avatar(new.id) then
      raise exception 'Los menores de edad no pueden publicar foto de perfil';
    end if;
  end if;
  return new;
end $fn$;

-- 2) Foto de la academia ------------------------------------------------
insert into storage.buckets (id, name, public, file_size_limit)
values ('academy-logos', 'academy-logos', true, 2097152)
on conflict (id) do update set public = true, file_size_limit = 2097152;

-- ¿Quien llama administra la academia de esta carpeta? (false si la carpeta no es un id válido)
create or replace function public.can_manage_academy_folder(p_folder text)
returns boolean language plpgsql stable security definer set search_path = public as $fn$
declare v_academy uuid;
begin
  begin
    v_academy := p_folder::uuid;
  exception when others then
    return false;
  end;
  return public.is_platform_admin() or public.has_academy_role(v_academy, 'adm');
end $fn$;
grant execute on function public.can_manage_academy_folder(text) to authenticated;

drop policy if exists academy_logos_select on storage.objects;
create policy academy_logos_select on storage.objects for select to authenticated
  using (bucket_id = 'academy-logos' and public.can_manage_academy_folder((storage.foldername(name))[1]));

drop policy if exists academy_logos_insert on storage.objects;
create policy academy_logos_insert on storage.objects for insert to authenticated
  with check (bucket_id = 'academy-logos' and public.can_manage_academy_folder((storage.foldername(name))[1]));

drop policy if exists academy_logos_update on storage.objects;
create policy academy_logos_update on storage.objects for update to authenticated
  using (bucket_id = 'academy-logos' and public.can_manage_academy_folder((storage.foldername(name))[1]))
  with check (bucket_id = 'academy-logos' and public.can_manage_academy_folder((storage.foldername(name))[1]));

drop policy if exists academy_logos_delete on storage.objects;
create policy academy_logos_delete on storage.objects for delete to authenticated
  using (bucket_id = 'academy-logos' and public.can_manage_academy_folder((storage.foldername(name))[1]));

-- crest_path de la academia solo puede apuntar a SU carpeta (quién puede editarla: RLS academies_update = admin)
create or replace function public.trg_academies_crest_check()
returns trigger language plpgsql set search_path = public as $fn$
begin
  if new.crest_path is distinct from old.crest_path and new.crest_path is not null
     and split_part(new.crest_path, '/', 1) <> new.id::text then
    raise exception 'La foto debe estar en la carpeta de la academia';
  end if;
  return new;
end $fn$;
drop trigger if exists trg_academies_crest_check on public.academies;
create trigger trg_academies_crest_check before update of crest_path on public.academies
  for each row execute function public.trg_academies_crest_check();

-- Los equipos SIEMPRE muestran la foto de su academia (no se puede poner otra a mano)
create or replace function public.trg_teams_crest_sync()
returns trigger language plpgsql security definer set search_path = public as $fn$
begin
  new.crest_path := (select a.crest_path from public.academies a where a.id = new.academy_id);
  return new;
end $fn$;
drop trigger if exists trg_teams_crest_sync on public.teams;
create trigger trg_teams_crest_sync before insert or update on public.teams
  for each row execute function public.trg_teams_crest_sync();

-- Al cambiar la foto de la academia, se copia a todos sus equipos
create or replace function public.trg_academies_crest_propagate()
returns trigger language plpgsql security definer set search_path = public as $fn$
begin
  update public.teams set crest_path = new.crest_path
   where academy_id = new.id and crest_path is distinct from new.crest_path;
  return null;
end $fn$;
drop trigger if exists trg_academies_crest_propagate on public.academies;
create trigger trg_academies_crest_propagate after update of crest_path on public.academies
  for each row execute function public.trg_academies_crest_propagate();

-- Alinear los equipos existentes con su academia
update public.teams t set crest_path = a.crest_path
  from public.academies a
 where a.id = t.academy_id and t.crest_path is distinct from a.crest_path;

commit;
