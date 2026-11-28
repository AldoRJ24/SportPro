-- =====================================================================
-- SportPro · Migración 020: el staff también sube documentos de sus jugadores.
--
--   Antes solo el apoderado (o el jugador adulto) podía subir el DNI, la ficha médica, el
--   seguro o la autorización de viaje. En la práctica muchos apoderados entregan los papeles
--   en físico al DT o en la oficina de la academia, así que ahora también pueden subirlos:
--     - el DT (staff) de un equipo activo del jugador,
--     - el administrador de la academia de ese equipo,
--     - el superadmin de la plataforma.
--   Es decir: quien ya podía REVISAR documentos (doc_can_review) ahora también puede SUBIRLOS.
--
--   Reglas que se mantienen:
--     - El bucket sigue privado y sin UPDATE ni DELETE: cada subida es un archivo nuevo.
--     - Las políticas de Storage no cambian: usan doc_folder_allows → doc_can_upload, que es
--       justo lo que se amplía aquí.
--     - Trazabilidad: uploaded_by guarda quién subió y log_audit lo registra con su rol.
--
--   Lo nuevo:
--     - Si sube el staff, el documento queda APROBADO al instante (quien lo revisaría es él mismo:
--       pedirle que se apruebe a sí mismo sería un paso vacío). Queda registrado como revisado
--       por esa persona y en ese momento.
--     - Se avisa a los apoderados del jugador que el staff subió el documento.
--
-- Cómo ejecutarla: Supabase Dashboard → SQL Editor → pegar TODO → Run (transaccional).
-- Es idempotente: se puede ejecutar más de una vez sin romper nada.
-- =====================================================================
begin;

-- ---------------------------------------------------------------------
-- 1. QUIÉN PUEDE SUBIR: apoderado, jugador adulto o quien puede revisar (staff / admin)
-- ---------------------------------------------------------------------
create or replace function public.doc_can_upload(p_player uuid)
returns boolean language sql stable security definer set search_path = public as $fn$
  select exists (
           select 1 from public.players pl
           where pl.id = p_player
             and (public.is_guardian_of(pl.id)
                  or (pl.user_id = auth.uid() and public.age_years(pl.birth_date) >= 18)))
      or public.doc_can_review(p_player)
$fn$;

-- ---------------------------------------------------------------------
-- 2. REGISTRAR UNA SUBIDA (versión vigente: copia de la 009 + rama del staff)
-- ---------------------------------------------------------------------
create or replace function public.doc_register(p_player uuid, p_type text, p_path text, p_mime text, p_expires date default null)
returns uuid language plpgsql security definer set search_path = public as $fn$
declare
  v_id uuid;
  v_name text;
  r record;
  -- true si quien sube NO es el apoderado ni el propio jugador adulto, sino el staff
  v_by_staff boolean;
begin
  if auth.uid() is null then raise exception 'No autenticado'; end if;
  if not public.doc_can_upload(p_player) then
    raise exception 'Solo el apoderado, el jugador mayor de edad o el staff del equipo pueden subir sus documentos';
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

  -- Apoderado o jugador adulto → queda pendiente de revisión, como antes.
  -- Staff (y no apoderado) → queda aprobado por él mismo.
  v_by_staff := public.doc_can_review(p_player)
    and not exists (select 1 from public.players pl
                     where pl.id = p_player
                       and (public.is_guardian_of(pl.id)
                            or (pl.user_id = auth.uid() and public.age_years(pl.birth_date) >= 18)));

  -- La versión anterior (si había) queda en el historial como «reemplazada»
  update public.player_documents set superseded_at = now()
   where player_id = p_player and doc_type = p_type and superseded_at is null;

  insert into public.player_documents (player_id, doc_type, file_path, mime_type, expires_on, uploaded_by,
                                       status, reviewed_by, reviewed_at)
  values (p_player, p_type, p_path, p_mime, p_expires, auth.uid(),
          case when v_by_staff then 'approved' else 'pending' end,
          case when v_by_staff then auth.uid() end,
          case when v_by_staff then now() end)
  returning id into v_id;

  select trim(first_name || ' ' || last_name) into v_name from public.players where id = p_player;

  if v_by_staff then
    -- Aviso a los apoderados: el staff subió (y aprobó) el documento de su hijo
    for r in
      select g.guardian_user_id as u from public.guardian_player_links g
       where g.player_id = p_player and g.status = 'approved' and g.guardian_user_id <> auth.uid()
    loop
      perform public._notify(r.u, 'document_uploaded_by_staff', 'Documento registrado',
        'El staff subió ' || public._doc_type_label(p_type) || ' de ' || v_name || '. Ya figura como aprobado.',
        jsonb_build_object('document_id', v_id, 'player_id', p_player));
    end loop;
  else
    -- Aviso al staff de los equipos del jugador y a los admins de esas academias (como en la 009)
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
  end if;

  perform public.log_audit('document_uploaded', 'player', p_player,
    jsonb_build_object('document_id', v_id, 'doc_type', p_type, 'expires_on', p_expires, 'by_staff', v_by_staff));
  return v_id;
end $fn$;

-- ---------------------------------------------------------------------
-- 3. PERMISOS (mismos que en la 009: solo usuarios con sesión)
-- ---------------------------------------------------------------------
revoke execute on function public.doc_can_upload(uuid), public.doc_register(uuid, text, text, text, date) from public, anon;
grant execute on function public.doc_can_upload(uuid), public.doc_register(uuid, text, text, text, date) to authenticated;

commit;
