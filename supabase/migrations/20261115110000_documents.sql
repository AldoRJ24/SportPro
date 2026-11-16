-- =====================================================================
-- SportPro · Migración 009: documentos del jugador (DNI, ficha médica,
-- autorización de viaje, seguro).
--
-- Son datos MUY sensibles de menores. Reglas (todas en el servidor):
--  1. Los archivos viven en un bucket PRIVADO («documents»), en una carpeta por jugador:
--         documents/<id del jugador>/<id del documento>.<extensión>
--     No hay URL pública: la app pide una URL FIRMADA que vence en pocos minutos.
--  2. SUBE: el apoderado aprobado del jugador (o el propio jugador si ya tiene 18 años).
--  3. VE: quien sube, el staff (DT / asistente) de un equipo donde el jugador está activo,
--     y el admin de esa academia. Nadie más: ni otros apoderados ni el resto de la academia.
--  4. REVISA: el DT o el admin aprueba, o rechaza con motivo.
--  5. Nada se borra ni se pisa: subir una versión nueva deja la anterior como «reemplazada».
--     Storage no tiene políticas de UPDATE ni DELETE para este bucket.
--  6. «Vencido» se calcula con la fecha de vencimiento (no se guarda como estado).
--  7. Todo queda en audit_log.
--
-- Cómo ejecutarla: Supabase Dashboard → SQL Editor → pegar TODO → Run (transaccional).
-- =====================================================================
begin;

-- ---------------------------------------------------------------------
-- 1. BUCKET PRIVADO (5 MB por archivo; solo imágenes JPG/PNG y PDF)
-- ---------------------------------------------------------------------
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('documents', 'documents', false, 5242880, array['image/jpeg','image/png','application/pdf'])
on conflict (id) do update
  set public = false, file_size_limit = 5242880,
      allowed_mime_types = array['image/jpeg','image/png','application/pdf'];

-- ---------------------------------------------------------------------
-- 2. TABLA
-- ---------------------------------------------------------------------
create table if not exists public.player_documents (
  id            uuid primary key default gen_random_uuid(),
  player_id     uuid not null references public.players(id) on delete cascade,
  doc_type      text not null check (doc_type in ('dni','medical','travel','insurance')),
  file_path     text not null unique,        -- ruta dentro del bucket «documents»
  mime_type     text not null check (mime_type in ('image/jpeg','image/png','application/pdf')),
  expires_on    date,                        -- null = no vence
  status        text not null default 'pending' check (status in ('pending','approved','rejected')),
  review_reason text,
  uploaded_by   uuid references public.profiles(id),
  created_at    timestamptz not null default now(),
  reviewed_by   uuid references public.profiles(id),
  reviewed_at   timestamptz,
  superseded_at timestamptz                  -- se llenó cuando subieron una versión más nueva
);
-- Solo UN documento vigente por jugador y tipo; las versiones anteriores quedan como historial
create unique index if not exists player_documents_current_uq
  on public.player_documents (player_id, doc_type) where superseded_at is null;
create index if not exists player_documents_player_idx on public.player_documents (player_id);

-- ---------------------------------------------------------------------
-- 3. FUNCIONES DE PERMISOS
-- ---------------------------------------------------------------------

-- ¿Puedo subir documentos de este jugador? Apoderado aprobado, o el propio jugador adulto.
create or replace function public.doc_can_upload(p_player uuid)
returns boolean language sql stable security definer set search_path = public as $fn$
  select exists (
    select 1 from public.players pl
    where pl.id = p_player
      and (public.is_guardian_of(pl.id)
           or (pl.user_id = auth.uid() and public.age_years(pl.birth_date) >= 18)))
$fn$;

-- ¿Puedo revisar documentos de este jugador? Staff de un equipo donde está activo, o admin de esa academia.
create or replace function public.doc_can_review(p_player uuid)
returns boolean language sql stable security definer set search_path = public as $fn$
  select public.is_platform_admin()
      or exists (select 1 from public.team_players tp
                 join public.teams t on t.id = tp.team_id
                 where tp.player_id = p_player and tp.status = 'active'
                   and (public.is_team_staff(t.id) or public.has_academy_role(t.academy_id, 'adm')))
$fn$;

create or replace function public.doc_can_view(p_player uuid)
returns boolean language sql stable security definer set search_path = public as $fn$
  select public.doc_can_upload(p_player) or public.doc_can_review(p_player)
$fn$;

-- Para las políticas de Storage: la primera carpeta de la ruta es el id del jugador.
-- Devuelve false (no error) si la carpeta no es un id válido.
create or replace function public.doc_folder_allows(p_folder text, p_write boolean)
returns boolean language plpgsql stable security definer set search_path = public as $fn$
declare v_player uuid;
begin
  begin
    v_player := p_folder::uuid;
  exception when others then
    return false;
  end;
  return case when p_write then public.doc_can_upload(v_player) else public.doc_can_view(v_player) end;
end $fn$;

-- Tipos obligatorios para considerar a un jugador «completo». La autorización de viaje
-- es opcional: solo hace falta cuando hay un viaje.
create or replace function public._doc_required_types()
returns text[] language sql immutable set search_path = public as $fn$
  select array['dni','medical','insurance']
$fn$;

create or replace function public._doc_type_label(p_type text)
returns text language sql immutable set search_path = public as $fn$
  select case p_type when 'dni' then 'DNI' when 'medical' then 'ficha médica'
                     when 'travel' then 'autorización de viaje' when 'insurance' then 'seguro'
                     else p_type end
$fn$;

-- ---------------------------------------------------------------------
-- 4. RLS DE LA TABLA: solo lectura para quien puede ver; escritura por funciones
-- ---------------------------------------------------------------------
alter table public.player_documents enable row level security;
drop policy if exists player_documents_select on public.player_documents;
create policy player_documents_select on public.player_documents for select to authenticated
  using (public.doc_can_view(player_id));
revoke all on public.player_documents from anon, authenticated;
grant select on public.player_documents to authenticated;

-- ---------------------------------------------------------------------
-- 5. POLÍTICAS DE STORAGE (bucket «documents»)
--    Sin política de UPDATE ni DELETE: un archivo subido no se puede cambiar ni borrar.
-- ---------------------------------------------------------------------
drop policy if exists documents_select on storage.objects;
create policy documents_select on storage.objects for select to authenticated
  using (bucket_id = 'documents' and public.doc_folder_allows((storage.foldername(name))[1], false));

drop policy if exists documents_insert on storage.objects;
create policy documents_insert on storage.objects for insert to authenticated
  with check (bucket_id = 'documents' and public.doc_folder_allows((storage.foldername(name))[1], true));

-- ---------------------------------------------------------------------
-- 6. FUNCIONES DE ESCRITURA
-- ---------------------------------------------------------------------

-- 6.1 Registrar un archivo que la app YA subió a Storage. Comprueba que el archivo existe
--     de verdad y que está en la carpeta del jugador (no se puede «registrar» una ruta ajena).
create or replace function public.doc_register(p_player uuid, p_type text, p_path text, p_mime text,
                                               p_expires date default null)
returns uuid language plpgsql security definer set search_path = public as $fn$
declare v_id uuid; v_name text; r record;
begin
  if auth.uid() is null then raise exception 'No autenticado'; end if;
  if not public.doc_can_upload(p_player) then
    raise exception 'Solo el apoderado (o el jugador si es mayor de edad) puede subir sus documentos';
  end if;
  if p_type is null or p_type not in ('dni','medical','travel','insurance') then
    raise exception 'Tipo de documento no válido';
  end if;
  if p_mime is null or p_mime not in ('image/jpeg','image/png','application/pdf') then
    raise exception 'Solo se aceptan fotos (JPG o PNG) o archivos PDF';
  end if;
  if p_path is null or split_part(p_path, '/', 1) <> p_player::text or position('..' in p_path) > 0 then
    raise exception 'El archivo debe estar en la carpeta del jugador';
  end if;
  if not exists (select 1 from storage.objects o where o.bucket_id = 'documents' and o.name = p_path) then
    raise exception 'El archivo no se subió. Inténtalo otra vez.';
  end if;
  if p_expires is not null and p_expires < public._today_pe() then
    raise exception 'La fecha de vencimiento ya pasó: sube un documento vigente';
  end if;

  -- La versión anterior (si había) queda en el historial como «reemplazada»
  update public.player_documents set superseded_at = now()
   where player_id = p_player and doc_type = p_type and superseded_at is null;

  insert into public.player_documents (player_id, doc_type, file_path, mime_type, expires_on, uploaded_by)
  values (p_player, p_type, p_path, p_mime, p_expires, auth.uid())
  returning id into v_id;

  -- Aviso al staff de los equipos del jugador y a los admins de esas academias
  select trim(first_name || ' ' || last_name) into v_name from public.players where id = p_player;
  for r in
    select distinct u from (
      select s.user_id as u
        from public.team_players tp join public.team_staff s on s.team_id = tp.team_id
       where tp.player_id = p_player and tp.status = 'active'
      union
      select m.user_id
        from public.team_players tp join public.teams t on t.id = tp.team_id
        join public.academy_memberships m on m.academy_id = t.academy_id and m.role = 'adm' and m.status = 'active'
       where tp.player_id = p_player and tp.status = 'active') x
    where u <> auth.uid()
  loop
    perform public._notify(r.u, 'document_uploaded', 'Documento por revisar',
      'Subieron ' || public._doc_type_label(p_type) || ' de ' || v_name || '. Revísalo y apruébalo.',
      jsonb_build_object('document_id', v_id, 'player_id', p_player));
  end loop;

  perform public.log_audit('document_uploaded', 'player', p_player,
    jsonb_build_object('document_id', v_id, 'doc_type', p_type, 'expires_on', p_expires));
  return v_id;
end $fn$;

-- 6.2 El DT o el admin aprueba, o rechaza con motivo
create or replace function public.doc_review(p_doc uuid, p_accept boolean, p_reason text default null)
returns void language plpgsql security definer set search_path = public as $fn$
declare d public.player_documents%rowtype; v_ok boolean := coalesce(p_accept, false); r record;
begin
  select * into d from public.player_documents where id = p_doc for update;
  if not found then raise exception 'Documento no encontrado'; end if;
  if not public.doc_can_review(d.player_id) then
    raise exception 'Solo el DT del equipo o el administrador de la academia revisa documentos';
  end if;
  if d.superseded_at is not null then raise exception 'Este documento fue reemplazado por una versión más nueva'; end if;
  if d.status <> 'pending' then raise exception 'Este documento ya fue revisado'; end if;
  if not v_ok and char_length(trim(coalesce(p_reason, ''))) < 3 then
    raise exception 'Escribe el motivo del rechazo';
  end if;

  update public.player_documents
     set status = case when v_ok then 'approved' else 'rejected' end,
         review_reason = case when v_ok then null else trim(p_reason) end,
         reviewed_by = auth.uid(), reviewed_at = now()
   where id = d.id;

  -- Aviso a quien lo subió y a los apoderados del jugador
  for r in
    select d.uploaded_by as u where d.uploaded_by is not null
    union
    select g.guardian_user_id from public.guardian_player_links g
     where g.player_id = d.player_id and g.status = 'approved'
  loop
    perform public._notify(r.u,
      case when v_ok then 'document_approved' else 'document_rejected' end,
      case when v_ok then 'Documento aprobado' else 'Documento rechazado' end,
      initcap(left(public._doc_type_label(d.doc_type), 1)) || substr(public._doc_type_label(d.doc_type), 2)
        || case when v_ok then ': aprobado.' else ': rechazado. Motivo: ' || trim(p_reason) end,
      jsonb_build_object('document_id', d.id, 'player_id', d.player_id));
  end loop;

  perform public.log_audit('document_' || case when v_ok then 'approved' else 'rejected' end,
    'player', d.player_id, jsonb_build_object('document_id', d.id, 'doc_type', d.doc_type, 'reason', p_reason));
end $fn$;

-- ---------------------------------------------------------------------
-- 7. FUNCIONES DE LECTURA
-- ---------------------------------------------------------------------

-- 7.1 Documentos VIGENTES de un jugador + qué puedo hacer yo con ellos
create or replace function public.doc_list(p_player uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $fn$
begin
  if not public.doc_can_view(p_player) then
    raise exception 'No tienes permiso para ver los documentos de este jugador';
  end if;
  return jsonb_build_object(
    'can_upload', public.doc_can_upload(p_player),
    'can_review', public.doc_can_review(p_player),
    'required', to_jsonb(public._doc_required_types()),
    'docs', coalesce((
      select jsonb_agg(jsonb_build_object(
               'id', d.id, 'doc_type', d.doc_type, 'file_path', d.file_path, 'mime_type', d.mime_type,
               'expires_on', d.expires_on, 'status', d.status, 'review_reason', d.review_reason,
               'is_expired', d.expires_on is not null and d.expires_on < public._today_pe(),
               'uploaded_by_name', nullif(trim(ub.full_name), ''), 'created_at', d.created_at,
               'reviewed_by_name', nullif(trim(rb.full_name), ''), 'reviewed_at', d.reviewed_at)
             order by d.doc_type)
      from public.player_documents d
      left join public.profiles ub on ub.id = d.uploaded_by
      left join public.profiles rb on rb.id = d.reviewed_by
      where d.player_id = p_player and d.superseded_at is null), '[]'::jsonb));
end $fn$;

-- 7.2 Vista del DT: qué le falta a cada jugador del plantel.
--     «missing» = tipos obligatorios sin un documento vigente APROBADO y no vencido.
create or replace function public.doc_team_status(p_team uuid)
returns table (player_id uuid, player_name text, jersey_number int,
               missing text[], pending_count int, rejected_count int, expired_count int, complete boolean)
language plpgsql stable security definer set search_path = public as $fn$
begin
  if not public.is_team_manager(p_team) then
    raise exception 'Solo el DT o el administrador del equipo ve los documentos del plantel';
  end if;
  return query
  select p.id, trim(p.first_name || ' ' || p.last_name), tp.jersey_number::int,
         m.missing,
         (select count(*)::int from public.player_documents d
           where d.player_id = p.id and d.superseded_at is null and d.status = 'pending'),
         (select count(*)::int from public.player_documents d
           where d.player_id = p.id and d.superseded_at is null and d.status = 'rejected'),
         (select count(*)::int from public.player_documents d
           where d.player_id = p.id and d.superseded_at is null and d.status = 'approved'
             and d.expires_on is not null and d.expires_on < public._today_pe()),
         coalesce(array_length(m.missing, 1), 0) = 0
  from public.team_players tp
  join public.players p on p.id = tp.player_id
  cross join lateral (
    select coalesce(array_agg(rt.t order by rt.t), '{}'::text[]) as missing
    from unnest(public._doc_required_types()) as rt(t)
    where not exists (select 1 from public.player_documents d
                      where d.player_id = p.id and d.doc_type = rt.t and d.superseded_at is null
                        and d.status = 'approved'
                        and (d.expires_on is null or d.expires_on >= public._today_pe()))) m
  where tp.team_id = p_team and tp.status = 'active'
  order by 8, 2;      -- primero los incompletos
end $fn$;

-- ---------------------------------------------------------------------
-- 8. PERMISOS DE EJECUCIÓN
-- ---------------------------------------------------------------------
revoke execute on function public._doc_required_types(), public._doc_type_label(text) from public, anon, authenticated;

revoke execute on function public.doc_can_upload(uuid), public.doc_can_review(uuid), public.doc_can_view(uuid),
  public.doc_folder_allows(text, boolean), public.doc_register(uuid, text, text, text, date),
  public.doc_review(uuid, boolean, text), public.doc_list(uuid), public.doc_team_status(uuid)
  from public, anon;
grant execute on function public.doc_can_upload(uuid), public.doc_can_review(uuid), public.doc_can_view(uuid),
  public.doc_folder_allows(text, boolean), public.doc_register(uuid, text, text, text, date),
  public.doc_review(uuid, boolean, text), public.doc_list(uuid), public.doc_team_status(uuid)
  to authenticated;

commit;
