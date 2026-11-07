-- =====================================================================
-- SportPro · Migración 004: foto de perfil
-- - Bucket público «avatars» (máx. 2 MB): cada usuario sube solo a su carpeta {su_id}/...
-- - Solo cuentas de 18+ pueden subir foto (privacidad de menores).
-- - profiles.avatar_path debe apuntar a la carpeta del propio usuario.
-- - La foto la ve quien comparte academia con la persona (profiles_select).
-- =====================================================================
begin;

insert into storage.buckets (id, name, public, file_size_limit)
values ('avatars', 'avatars', true, 2097152)
on conflict (id) do update set public = true, file_size_limit = 2097152;

drop policy if exists avatars_select_own on storage.objects;
create policy avatars_select_own on storage.objects for select to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists avatars_insert_own on storage.objects;
create policy avatars_insert_own on storage.objects for insert to authenticated
  with check (bucket_id = 'avatars'
              and (storage.foldername(name))[1] = auth.uid()::text
              and coalesce(public.my_age(), 0) >= 18);

drop policy if exists avatars_update_own on storage.objects;
create policy avatars_update_own on storage.objects for update to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text)
  with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text
              and coalesce(public.my_age(), 0) >= 18);

drop policy if exists avatars_delete_own on storage.objects;
create policy avatars_delete_own on storage.objects for delete to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);

-- avatar_path solo puede apuntar a la carpeta propia (o quedar vacío)
create or replace function public.trg_profiles_avatar_check()
returns trigger language plpgsql set search_path = public as $fn$
begin
  if new.avatar_path is distinct from old.avatar_path and new.avatar_path is not null then
    if split_part(new.avatar_path, '/', 1) <> new.id::text then
      raise exception 'La foto debe estar en tu propia carpeta';
    end if;
    if coalesce(public.age_years(new.birth_date), 0) < 18 then
      raise exception 'Los menores de edad no pueden publicar foto de perfil';
    end if;
  end if;
  return new;
end $fn$;
drop trigger if exists trg_profiles_avatar_check on public.profiles;
create trigger trg_profiles_avatar_check before update of avatar_path on public.profiles
  for each row execute function public.trg_profiles_avatar_check();

commit;
