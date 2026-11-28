-- =====================================================================
-- SportPro · Migración 022: moderación de comentarios.
--
--   1. FILTRO DE PALABRAS: si un comentario trae lenguaje ofensivo NO se publica. Se registra
--      un «strike» y a la persona le llega la notificación de que su comentario se eliminó
--      automáticamente. El texto se compara normalizado (minúsculas, sin tildes, «p0t@» = «pota»,
--      letras repetidas juntas «tooonto» = «tonto») para que sea difícil esquivarlo.
--   2. REPORTES: cualquiera que ve la publicación puede reportar un comentario ajeno, eligiendo
--      un motivo. Al juntar 3 reportes de personas distintas, el comentario se elimina, su autor
--      recibe un strike y una notificación con el texto eliminado.
--   3. SUSPENSIÓN: 3 strikes en menos de 7 días = 7 días sin poder comentar.
--
--   Reglas del proyecto que se mantienen: nada se borra de verdad (el texto original queda en
--   audit_log y en los strikes), las tablas nuevas solo se escriben por funciones, y los avisos
--   de moderación NO se pueden silenciar (su categoría es null: se entregan siempre).
--
-- Cómo ejecutarla: Supabase Dashboard → SQL Editor → pegar TODO → Run (transaccional).
-- =====================================================================
begin;

-- ---------------------------------------------------------------------
-- 1. Tablas
-- ---------------------------------------------------------------------
-- Lista de palabras prohibidas (en la forma normalizada que usa _filter_normalize)
create table if not exists public.banned_words (
  word       text primary key check (char_length(word) between 2 and 40),
  created_at timestamptz not null default now()
);

-- Strikes: cada comentario bloqueado por el filtro o eliminado por reportes
create table if not exists public.comment_strikes (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references public.profiles(id) on delete cascade,
  comment_id  uuid references public.post_comments(id) on delete set null,  -- null = bloqueado antes de publicarse
  match_id    uuid references public.match_summaries(match_id) on delete set null,
  reason      text not null check (reason in ('filter', 'reports')),
  body        text not null,          -- texto original (trazabilidad)
  created_at  timestamptz not null default now()
);
create index if not exists comment_strikes_user_idx on public.comment_strikes (user_id, created_at desc);

-- Suspensiones para comentar
create table if not exists public.comment_bans (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references public.profiles(id) on delete cascade,
  until       timestamptz not null,
  created_at  timestamptz not null default now()
);
create index if not exists comment_bans_user_idx on public.comment_bans (user_id, until desc);

-- Reportes de comentarios (uno por persona y comentario)
create table if not exists public.comment_reports (
  id          uuid primary key default gen_random_uuid(),
  comment_id  uuid not null references public.post_comments(id) on delete cascade,
  reporter_id uuid not null references public.profiles(id) on delete cascade,
  reason      text not null check (reason in ('offensive','harassment','hate','spam','inappropriate','other')),
  details     text check (details is null or char_length(details) <= 300),
  created_at  timestamptz not null default now(),
  unique (comment_id, reporter_id)
);
create index if not exists comment_reports_comment_idx on public.comment_reports (comment_id);

-- Motivo por el que se eliminó un comentario (null = sigue publicado o lo borró su autor/moderador)
alter table public.post_comments add column if not exists removed_reason text
  check (removed_reason is null or removed_reason in ('reports'));

-- Nadie las lee ni las escribe directo: solo las funciones de abajo
alter table public.banned_words    enable row level security;
alter table public.comment_strikes enable row level security;
alter table public.comment_bans    enable row level security;
alter table public.comment_reports enable row level security;
revoke all on public.banned_words, public.comment_strikes, public.comment_bans, public.comment_reports from anon, authenticated;

-- ---------------------------------------------------------------------
-- 2. Normalización y detección
-- ---------------------------------------------------------------------
-- minúsculas → sin tildes → números/símbolos que imitan letras → solo letras y espacios →
-- una letra repetida 3 o más veces se reduce a una («putaaa» → «puta», «tooonto» → «tonto»).
-- Las dobles se respetan: «perra» no debe volverse «pera». Las palabras de la lista pasan por
-- la misma función antes de comparar.
create or replace function public._filter_normalize(p_text text)
returns text language sql immutable set search_path = public as $fn$
  select trim(regexp_replace(
           regexp_replace(
             regexp_replace(
               translate(lower(coalesce(p_text, '')),
                         'áàäâéèëêíìïîóòöôúùüûñ013457@$',
                         'aaaaeeeeiiiioooouuuunoieastas'),
               '[^a-z]+', ' ', 'g'),
             '([a-z])\1{2,}', '\1', 'g'),
           '\s+', ' ', 'g'))
$fn$;

-- ¿El texto trae alguna palabra prohibida? Busca la palabra completa o con un final real de
-- plural, género, aumentativo o diminutivo («tontos», «tontaza», «idiotita»). No acepta
-- cualquier final: así «Vergara» (apellido) no se confunde. Las palabras largas (7+ letras)
-- también se buscan con el texto pegado («m a r i c o n» → «maricon»).
create or replace function public._is_offensive(p_text text)
returns boolean language sql stable security definer set search_path = public as $fn$
  with t as (select ' ' || public._filter_normalize(p_text) || ' ' as spaced,
                    replace(public._filter_normalize(p_text), ' ', '') as glued)
  , w as (select public._filter_normalize(word) as n from public.banned_words)
  select exists (
    select 1 from w, t
    where t.spaced ~ (' ' || w.n || '(s|es)? ')
       -- Palabras de 6+ letras: el final se pega a la raíz («cojud» + «azo», «idiot» + «ita»).
       -- En las cortas no, porque «perra» → «perr» + «o» bloquearía «perro».
       or (char_length(w.n) >= 6 and t.spaced ~ (' ' || regexp_replace(w.n, '[aeo]$', '')
             || '(a|o|e|as|os|es|azo|aza|azos|ote|ota|ito|ita|itos|itas|isimo|isima) '))
       or (char_length(w.n) >= 7 and position(w.n in t.glued) > 0)
  )
$fn$;

-- Lista inicial (Perú / español general / inglés básico). El administrador de la plataforma
-- puede agregar más con un insert en el SQL Editor.
insert into public.banned_words (word) values
  ('puta'),('puto'),('putas'),('mierda'),('mrd'),('carajo'),('cojudo'),('cojuda'),('huevon'),('huevona'),
  ('webon'),('webona'),('weon'),('conchatumadre'),('conchasumadre'),('concha tu madre'),('ctm'),('csm'),('ptm'),('hdp'),
  ('hijo de puta'),('maricon'),('marica'),('cabron'),('cabrona'),('pendejo'),('pendeja'),('imbecil'),('idiota'),('estupido'),
  ('estupida'),('tarado'),('tarada'),('retrasado'),('retrasada'),('mongolico'),('perra'),('zorra'),('malparido'),('gonorrea'),
  ('verga'),('pinga'),('chucha'),('cagon'),('culero'),('lameculos'),('joder'),('jodete'),('qlo'),('basura humana'),
  ('fuck'),('shit'),('bitch'),('asshole')
on conflict (word) do nothing;

-- ---------------------------------------------------------------------
-- 3. Strikes y suspensión
-- ---------------------------------------------------------------------
-- Suspensión vigente de una persona (null = puede comentar)
create or replace function public._comment_ban_until(p_user uuid)
returns timestamptz language sql stable security definer set search_path = public as $fn$
  select max(until) from public.comment_bans where user_id = p_user and until > now()
$fn$;

-- Registra un strike y, si con él llega a 3 en 7 días (y no está ya suspendido), lo suspende
-- 7 días y se lo avisa. Devuelve cuántos strikes lleva en la última semana.
create or replace function public._comment_strike(p_user uuid, p_comment uuid, p_match uuid, p_reason text, p_body text)
returns int language plpgsql security definer set search_path = public as $fn$
declare v_count int; v_until timestamptz;
begin
  insert into public.comment_strikes (user_id, comment_id, match_id, reason, body)
  values (p_user, p_comment, p_match, p_reason, p_body);
  select count(*) into v_count from public.comment_strikes
   where user_id = p_user and created_at > now() - interval '7 days';
  if v_count >= 3 and public._comment_ban_until(p_user) is null then
    v_until := now() + interval '7 days';
    insert into public.comment_bans (user_id, until) values (p_user, v_until);
    perform public._notify(p_user, 'moderation_comment_ban', 'No puedes comentar por 7 días',
      'Se eliminaron 3 de tus comentarios en menos de una semana. Podrás volver a comentar el '
        || to_char(v_until at time zone 'America/Lima', 'DD/MM/YYYY "a las" HH24:MI') || '.',
      jsonb_build_object('until', v_until));
    perform public.log_audit('comment_ban', 'profile', p_user, jsonb_build_object('until', v_until));
  end if;
  return v_count;
end $fn$;

-- ---------------------------------------------------------------------
-- 4. Comentar (reemplaza a la de la migración 006): ahora devuelve un resultado en vez de solo
--    el id, porque un comentario ofensivo no es un error: se registra el strike y se informa.
--    { id, blocked, strikes, banned_until }
-- ---------------------------------------------------------------------
drop function if exists public.add_post_comment(uuid, text);
create function public.add_post_comment(p_match uuid, p_body text)
returns jsonb language plpgsql security definer set search_path = public as $fn$
declare v_id uuid; v_body text := trim(coalesce(p_body, '')); s public.match_summaries%rowtype;
        v_until timestamptz; v_strikes int;
begin
  if auth.uid() is null then raise exception 'No autenticado'; end if;
  if not public.can_view_post(p_match) then raise exception 'Publicación no disponible'; end if;
  v_until := public._comment_ban_until(auth.uid());
  if v_until is not null then
    raise exception 'No puedes comentar hasta el % (se eliminaron 3 de tus comentarios en una semana)',
      to_char(v_until at time zone 'America/Lima', 'DD/MM/YYYY HH24:MI');
  end if;
  select * into s from public.match_summaries where match_id = p_match;
  if not s.comments_enabled then raise exception 'Los comentarios están desactivados en esta publicación'; end if;
  if char_length(v_body) < 1 then raise exception 'Escribe un comentario'; end if;
  if char_length(v_body) > 500 then raise exception 'El comentario es muy largo (máx. 500 caracteres)'; end if;
  if (select count(*) from public.post_comments
       where user_id = auth.uid() and created_at > now() - interval '1 minute') >= 5 then
    raise exception 'Vas muy rápido: espera un momento antes de comentar otra vez';
  end if;

  -- Filtro: no se publica, cuenta como strike y se avisa
  if public._is_offensive(v_body) then
    v_strikes := public._comment_strike(auth.uid(), null, p_match, 'filter', v_body);
    perform public._notify(auth.uid(), 'moderation_comment_blocked', 'Comentario eliminado automáticamente',
      'Tu comentario «' || left(v_body, 80) || '» tenía lenguaje ofensivo y se eliminó automáticamente. Strike '
        || least(v_strikes, 3) || ' de 3 esta semana.',
      jsonb_build_object('match_id', p_match));
    perform public.log_audit('comment_blocked', 'match', p_match, jsonb_build_object('text', v_body));
    return jsonb_build_object('id', null, 'blocked', true, 'strikes', v_strikes,
                              'banned_until', public._comment_ban_until(auth.uid()));
  end if;

  insert into public.post_comments (match_id, user_id, body) values (p_match, auth.uid(), v_body)
  returning id into v_id;
  if s.approved_by is not null and s.approved_by <> auth.uid() then
    perform public._notify(s.approved_by, 'post_comment', 'Nuevo comentario',
      public._public_profile_name(auth.uid()) || ' comentó tu publicación: «' || left(v_body, 80) || '»',
      jsonb_build_object('match_id', p_match, 'comment_id', v_id));
  end if;
  return jsonb_build_object('id', v_id, 'blocked', false, 'strikes', null, 'banned_until', null);
end $fn$;

-- ---------------------------------------------------------------------
-- 5. Reportar un comentario. Al 3er reporte de personas distintas se elimina.
--    Devuelve { reports, removed }.
-- ---------------------------------------------------------------------
create or replace function public.report_post_comment(p_comment uuid, p_reason text, p_details text default null)
returns jsonb language plpgsql security definer set search_path = public as $fn$
declare c public.post_comments%rowtype; v_n int; v_top text; v_label text;
begin
  if auth.uid() is null then raise exception 'No autenticado'; end if;
  select * into c from public.post_comments where id = p_comment for update;
  if not found or c.deleted_at is not null then raise exception 'Ese comentario ya no está disponible'; end if;
  if not public.can_view_post(c.match_id) then raise exception 'Publicación no disponible'; end if;
  if c.user_id = auth.uid() then raise exception 'No puedes reportar tu propio comentario'; end if;
  if p_reason is null or p_reason not in ('offensive','harassment','hate','spam','inappropriate','other') then
    raise exception 'Elige el motivo del reporte';
  end if;
  if exists (select 1 from public.comment_reports where comment_id = c.id and reporter_id = auth.uid()) then
    raise exception 'Ya reportaste este comentario';
  end if;
  insert into public.comment_reports (comment_id, reporter_id, reason, details)
  values (c.id, auth.uid(), p_reason, nullif(trim(coalesce(p_details, '')), ''));
  select count(*) into v_n from public.comment_reports where comment_id = c.id;
  perform public.log_audit('comment_reported', 'post_comment', c.id, jsonb_build_object('reason', p_reason, 'reports', v_n));

  if v_n < 3 then
    return jsonb_build_object('reports', v_n, 'removed', false);
  end if;

  -- 3 reportes: se elimina (el texto original queda en el strike y en la auditoría)
  select reason into v_top from public.comment_reports where comment_id = c.id
   group by reason order by count(*) desc, min(created_at) limit 1;
  v_label := case v_top when 'offensive' then 'lenguaje ofensivo' when 'harassment' then 'acoso'
                        when 'hate' then 'discriminación' when 'spam' then 'spam'
                        when 'inappropriate' then 'contenido inapropiado' else 'otros motivos' end;
  perform public.log_audit('comment_removed_by_reports', 'post_comment', c.id,
    jsonb_build_object('match_id', c.match_id, 'author', c.user_id, 'original_text', c.body, 'top_reason', v_top));
  update public.post_comments set deleted_at = now(), deleted_by = null, removed_reason = 'reports', body = ''
   where id = c.id;
  perform public._comment_strike(c.user_id, c.id, c.match_id, 'reports', c.body);
  perform public._notify(c.user_id, 'moderation_comment_removed', 'Tu comentario fue eliminado',
    'Tu comentario «' || left(c.body, 80) || '» ha sido eliminado por reportes de la comunidad (' || v_label || ').',
    jsonb_build_object('match_id', c.match_id));
  return jsonb_build_object('reports', v_n, 'removed', true);
end $fn$;

-- ---------------------------------------------------------------------
-- 6. Comentarios de una publicación: ahora dicen si ya lo reporté y si puedo reportarlo
-- ---------------------------------------------------------------------
drop function if exists public.get_post_comments(uuid);
create function public.get_post_comments(p_match uuid)
returns table (id uuid, user_id uuid, author_name text, author_avatar text, body text,
               created_at timestamptz, is_mine boolean, can_delete boolean,
               can_report boolean, reported_by_me boolean)
language sql stable security definer set search_path = public as $fn$
  select c.id, c.user_id, public._public_profile_name(c.user_id), p.avatar_path, c.body, c.created_at,
         c.user_id = auth.uid(),
         c.user_id = auth.uid() or public.can_moderate_post(c.match_id),
         c.user_id <> auth.uid(),
         exists (select 1 from public.comment_reports r where r.comment_id = c.id and r.reporter_id = auth.uid())
  from public.post_comments c
  left join public.profiles p on p.id = c.user_id
  where c.match_id = p_match and c.deleted_at is null and public.can_view_post(p_match)
  order by c.created_at asc
  limit 300
$fn$;

-- Mi situación para comentar: strikes de la semana y hasta cuándo estoy suspendido
create or replace function public.my_comment_status()
returns jsonb language sql stable security definer set search_path = public as $fn$
  select jsonb_build_object(
    'strikes', (select count(*) from public.comment_strikes where user_id = auth.uid() and created_at > now() - interval '7 days'),
    'banned_until', public._comment_ban_until(auth.uid()))
$fn$;

-- ---------------------------------------------------------------------
-- 7. Permisos
-- ---------------------------------------------------------------------
revoke execute on function public._filter_normalize(text), public._is_offensive(text), public._comment_ban_until(uuid),
  public._comment_strike(uuid, uuid, uuid, text, text) from public, anon, authenticated;
revoke execute on function public.add_post_comment(uuid, text), public.report_post_comment(uuid, text, text),
  public.get_post_comments(uuid), public.my_comment_status() from public, anon;
grant execute on function public.add_post_comment(uuid, text), public.report_post_comment(uuid, text, text),
  public.get_post_comments(uuid), public.my_comment_status() to authenticated;

commit;
