-- =====================================================================
-- SportPro · Migración 006: publicaciones en Inicio (estilo Instagram)
-- Cuando el DT aprueba el resumen con IA, ese resumen se convierte en una
-- PUBLICACIÓN que aparece en Inicio para su audiencia:
--   team    → staff del equipo local + jugadores/apoderados de ese equipo
--   academy → además, miembros de la academia local o visitante
--   public  → cualquier usuario con sesión
-- Cada publicación muestra autor (quien aprobó), hace cuánto, «me gusta» y comentarios.
-- Moderación: quien publicó (y el DT/admin del equipo) puede eliminar comentarios
-- y desactivar los comentarios. El autor de un comentario puede borrar el suyo.
-- Los comentarios eliminados no se borran de la base: se vacía el texto y queda
-- en la auditoría quién lo eliminó, cuándo y el texto original.
-- Cómo ejecutarla: SQL Editor → pegar TODO → Run (transaccional).
-- =====================================================================
begin;

alter table public.match_summaries add column if not exists comments_enabled boolean not null default true;

create table if not exists public.post_likes (
  match_id   uuid not null references public.match_summaries(match_id) on delete cascade,
  user_id    uuid not null references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (match_id, user_id)
);

create table if not exists public.post_comments (
  id            uuid primary key default gen_random_uuid(),
  match_id      uuid not null references public.match_summaries(match_id) on delete cascade,
  user_id       uuid not null references public.profiles(id) on delete cascade,
  body          text not null,
  created_at    timestamptz not null default now(),
  deleted_at    timestamptz,
  deleted_by    uuid references public.profiles(id),
  check (deleted_at is not null or char_length(trim(body)) between 1 and 500)
);
create index if not exists post_comments_match_idx on public.post_comments (match_id, created_at);

alter table public.post_likes    enable row level security;
alter table public.post_comments enable row level security;

-- ¿Puedo ver la publicación (resumen aprobado) de este partido?
create or replace function public.can_view_post(p_match uuid)
returns boolean language sql stable security definer set search_path = public as $fn$
  select exists (
    select 1 from public.match_summaries s join public.matches m on m.id = s.match_id
    where s.match_id = p_match and s.status = 'approved' and (
      public.is_platform_admin()
      or public.is_match_manager(m.id)
      or s.visibility = 'public'
      or public.is_team_staff(m.home_team_id)
      or exists (select 1 from public.team_players tp
                 where tp.team_id = m.home_team_id and tp.status = 'active'
                   and (public.is_guardian_of(tp.player_id)
                        or exists (select 1 from public.players pl where pl.id = tp.player_id and pl.user_id = auth.uid())))
      or (s.visibility = 'academy' and (
            public.is_academy_member(m.academy_id)
            or (m.away_team_id is not null and public.is_academy_member(public.team_academy(m.away_team_id)))))
    ))
$fn$;

-- ¿Puedo moderar sus comentarios? Quien la publicó o el DT/admin del equipo local.
create or replace function public.can_moderate_post(p_match uuid)
returns boolean language sql stable security definer set search_path = public as $fn$
  select exists (select 1 from public.match_summaries s
                 where s.match_id = p_match and s.status = 'approved'
                   and (s.approved_by = auth.uid() or public.is_match_manager(p_match)))
$fn$;

-- Nombre visible de una persona: los menores aparecen como «Nombre I.» (privacidad)
create or replace function public._public_profile_name(p_user uuid)
returns text language sql stable security definer set search_path = public as $fn$
  select case
    when p.birth_date is not null and public.age_years(p.birth_date) < 18 then
      split_part(trim(p.full_name), ' ', 1)
      || coalesce(' ' || upper(left(nullif(split_part(trim(p.full_name), ' ', 2), ''), 1)) || '.', '')
    else coalesce(nullif(trim(p.full_name), ''), 'Usuario')
  end
  from public.profiles p where p.id = p_user
$fn$;

-- Lectura directa (la usa Realtime para avisar a quien puede ver la publicación)
drop policy if exists post_likes_select on public.post_likes;
create policy post_likes_select on public.post_likes for select to authenticated
  using (public.can_view_post(match_id));
drop policy if exists post_comments_select on public.post_comments;
create policy post_comments_select on public.post_comments for select to authenticated
  using (public.can_view_post(match_id));
revoke all on public.post_likes, public.post_comments from anon, authenticated;
grant select on public.post_likes, public.post_comments to authenticated;
-- Escrituras: SOLO por las funciones de abajo (no hay grants de insert/update/delete).

-- 1) Feed de Inicio --------------------------------------------------------
create or replace function public.get_feed(p_limit int default 30)
returns table (
  match_id uuid, published_at timestamptz, text text, visibility text,
  author_id uuid, author_name text, author_avatar text, academy_name text,
  home_name text, away_name text, home_crest text, away_crest text,
  home_score int, away_score int,
  likes int, liked boolean, comments int, comments_enabled boolean, can_moderate boolean)
language sql stable security definer set search_path = public as $fn$
  select s.match_id, s.approved_at, coalesce(s.final_text, s.ai_text, ''), s.visibility,
         s.approved_by, public._public_profile_name(s.approved_by), pr.avatar_path, a.name,
         ht.name, coalesce(at.name, m.away_external_name, 'Rival'), ht.crest_path, at.crest_path,
         m.home_score, m.away_score,
         (select count(*) from public.post_likes l where l.match_id = s.match_id)::int,
         exists (select 1 from public.post_likes l where l.match_id = s.match_id and l.user_id = auth.uid()),
         (select count(*) from public.post_comments c where c.match_id = s.match_id and c.deleted_at is null)::int,
         s.comments_enabled,
         public.can_moderate_post(s.match_id)
  from public.match_summaries s
  join public.matches m on m.id = s.match_id
  join public.teams ht on ht.id = m.home_team_id
  left join public.teams at on at.id = m.away_team_id
  left join public.academies a on a.id = m.academy_id
  left join public.profiles pr on pr.id = s.approved_by
  where s.status = 'approved' and public.can_view_post(s.match_id)
  order by s.approved_at desc nulls last
  limit least(greatest(coalesce(p_limit, 30), 1), 100)
$fn$;

-- 2) Me gusta (toca de nuevo para quitarlo) ---------------------------------
create or replace function public.toggle_post_like(p_match uuid)
returns jsonb language plpgsql security definer set search_path = public as $fn$
declare v_liked boolean;
begin
  if auth.uid() is null then raise exception 'No autenticado'; end if;
  if not public.can_view_post(p_match) then raise exception 'Publicación no disponible'; end if;
  delete from public.post_likes where match_id = p_match and user_id = auth.uid();
  if found then
    v_liked := false;
  else
    insert into public.post_likes (match_id, user_id) values (p_match, auth.uid());
    v_liked := true;
  end if;
  return jsonb_build_object('liked', v_liked,
    'likes', (select count(*) from public.post_likes where match_id = p_match));
end $fn$;

-- 3) Comentarios ------------------------------------------------------------
create or replace function public.get_post_comments(p_match uuid)
returns table (id uuid, user_id uuid, author_name text, author_avatar text, body text,
               created_at timestamptz, is_mine boolean, can_delete boolean)
language sql stable security definer set search_path = public as $fn$
  select c.id, c.user_id, public._public_profile_name(c.user_id), p.avatar_path, c.body, c.created_at,
         c.user_id = auth.uid(),
         c.user_id = auth.uid() or public.can_moderate_post(c.match_id)
  from public.post_comments c
  left join public.profiles p on p.id = c.user_id
  where c.match_id = p_match and c.deleted_at is null and public.can_view_post(p_match)
  order by c.created_at asc
  limit 300
$fn$;

create or replace function public.add_post_comment(p_match uuid, p_body text)
returns uuid language plpgsql security definer set search_path = public as $fn$
declare v_id uuid; v_body text := trim(coalesce(p_body, '')); s public.match_summaries%rowtype;
begin
  if auth.uid() is null then raise exception 'No autenticado'; end if;
  if not public.can_view_post(p_match) then raise exception 'Publicación no disponible'; end if;
  select * into s from public.match_summaries where match_id = p_match;
  if not s.comments_enabled then raise exception 'Los comentarios están desactivados en esta publicación'; end if;
  if char_length(v_body) < 1 then raise exception 'Escribe un comentario'; end if;
  if char_length(v_body) > 500 then raise exception 'El comentario es muy largo (máx. 500 caracteres)'; end if;
  -- Anti-spam simple: máximo 5 comentarios por minuto por persona
  if (select count(*) from public.post_comments
       where user_id = auth.uid() and created_at > now() - interval '1 minute') >= 5 then
    raise exception 'Vas muy rápido: espera un momento antes de comentar otra vez';
  end if;
  insert into public.post_comments (match_id, user_id, body) values (p_match, auth.uid(), v_body)
  returning id into v_id;
  if s.approved_by is not null and s.approved_by <> auth.uid() then
    perform public._notify(s.approved_by, 'post_comment', 'Nuevo comentario',
      public._public_profile_name(auth.uid()) || ' comentó tu publicación: «' || left(v_body, 80) || '»',
      jsonb_build_object('match_id', p_match, 'comment_id', v_id));
  end if;
  return v_id;
end $fn$;

-- Eliminar: el autor del comentario o quien modera la publicación. Queda en la auditoría.
create or replace function public.delete_post_comment(p_comment uuid)
returns void language plpgsql security definer set search_path = public as $fn$
declare c public.post_comments%rowtype; v_mod boolean;
begin
  select * into c from public.post_comments where id = p_comment for update;
  if not found or c.deleted_at is not null then raise exception 'Comentario no encontrado'; end if;
  v_mod := public.can_moderate_post(c.match_id);
  if c.user_id <> auth.uid() and not v_mod then raise exception 'No puedes eliminar este comentario'; end if;
  perform public.log_audit('delete_post_comment', 'post_comment', c.id,
    jsonb_build_object('match_id', c.match_id, 'author', c.user_id, 'original_text', c.body,
                       'by_moderator', c.user_id <> auth.uid()));
  update public.post_comments set deleted_at = now(), deleted_by = auth.uid(), body = '' where id = c.id;
  if c.user_id <> auth.uid() then
    perform public._notify(c.user_id, 'post_comment_removed', 'Comentario eliminado',
      'Quien publicó el resumen eliminó tu comentario.', jsonb_build_object('match_id', c.match_id));
  end if;
end $fn$;

-- Activar / desactivar comentarios de la publicación (solo quien modera)
create or replace function public.set_post_comments_enabled(p_match uuid, p_enabled boolean)
returns void language plpgsql security definer set search_path = public as $fn$
begin
  if not public.can_moderate_post(p_match) then raise exception 'Solo quien publicó puede cambiar esto'; end if;
  update public.match_summaries set comments_enabled = coalesce(p_enabled, true) where match_id = p_match;
  perform public.log_audit('post_comments_enabled', 'match', p_match, jsonb_build_object('enabled', p_enabled));
end $fn$;

revoke execute on function public._public_profile_name(uuid) from public, anon, authenticated;
revoke execute on function public.can_view_post(uuid), public.can_moderate_post(uuid),
  public.get_feed(int), public.toggle_post_like(uuid), public.get_post_comments(uuid),
  public.add_post_comment(uuid, text), public.delete_post_comment(uuid),
  public.set_post_comments_enabled(uuid, boolean) from public, anon;
grant execute on function public.can_view_post(uuid), public.can_moderate_post(uuid),
  public.get_feed(int), public.toggle_post_like(uuid), public.get_post_comments(uuid),
  public.add_post_comment(uuid, text), public.delete_post_comment(uuid),
  public.set_post_comments_enabled(uuid, boolean) to authenticated;

-- Realtime: likes y comentarios llegan al instante a los otros celulares
do $rt$
begin
  if exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
    if not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime'
                   and schemaname = 'public' and tablename = 'post_comments') then
      alter publication supabase_realtime add table public.post_comments;
    end if;
    if not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime'
                   and schemaname = 'public' and tablename = 'post_likes') then
      alter publication supabase_realtime add table public.post_likes;
    end if;
  end if;
end $rt$;

commit;
