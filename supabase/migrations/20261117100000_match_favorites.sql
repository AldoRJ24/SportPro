-- =====================================================================
-- SportPro · Migración 014: partidos favoritos y lista «mis partidos».
--
--  1. FAVORITOS: cada usuario puede marcar partidos con una estrella. Es una preferencia
--     personal (como seguir a un equipo): solo la ve su dueño. La tabla no acepta escrituras
--     directas; se cambia con toggle_match_favorite(), que comprueba que el usuario
--     pueda ver ese partido.
--  2. MIS PARTIDOS: my_matches() devuelve SOLO los partidos que le interesan al usuario,
--     con el motivo de cada uno:
--        · by_follow : sigue a uno de los equipos o a su academia;
--        · by_child  : juega un jugador del que es apoderado aprobado;
--        · by_staff  : dirige uno de los equipos, administra su academia o es el operador;
--        · by_player : juega él mismo;
--        · is_favorite: lo marcó como favorito.
--     Un partido aparece si cumple al menos un motivo Y además el usuario tiene permiso
--     de verlo (can_view_match). Así la pestaña «Partidos» deja de mostrar partidos
--     públicos de academias que el usuario no sigue.
--
-- Cómo ejecutarla: Supabase Dashboard → SQL Editor → pegar TODO → Run (transaccional).
-- =====================================================================
begin;

-- ---------------------------------------------------------------------
-- Tabla de favoritos
-- ---------------------------------------------------------------------
create table if not exists public.match_favorites (
  user_id    uuid not null references public.profiles(id) on delete cascade,
  match_id   uuid not null references public.matches(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (user_id, match_id)
);
create index if not exists match_favorites_match on public.match_favorites (match_id);

alter table public.match_favorites enable row level security;
-- Supabase da todos los permisos por defecto: se quitan y se deja solo lectura
revoke all on public.match_favorites from anon, authenticated;
grant select on public.match_favorites to authenticated;

drop policy if exists match_favorites_select on public.match_favorites;
create policy match_favorites_select on public.match_favorites for select to authenticated
  using (user_id = (select auth.uid()));

-- ---------------------------------------------------------------------
-- Marcar / desmarcar favorito. Devuelve true si quedó marcado.
-- ---------------------------------------------------------------------
create or replace function public.toggle_match_favorite(p_match uuid)
returns boolean language plpgsql security definer set search_path = public as $fn$
begin
  if auth.uid() is null then raise exception 'Inicia sesión para guardar favoritos'; end if;
  -- Quitar un favorito siempre se permite (aunque el partido ya no sea visible para el usuario)
  delete from public.match_favorites where user_id = auth.uid() and match_id = p_match;
  if found then return false; end if;
  if not public.can_view_match(p_match) then
    raise exception 'No tienes acceso a este partido';
  end if;
  insert into public.match_favorites (user_id, match_id) values (auth.uid(), p_match)
  on conflict do nothing;
  return true;
end $fn$;

-- ---------------------------------------------------------------------
-- Mis partidos: ids + motivo por el que aparecen (los 100 más recientes)
-- ---------------------------------------------------------------------
create or replace function public.my_matches()
returns table (match_id uuid, is_favorite boolean, by_follow boolean, by_child boolean,
               by_staff boolean, by_player boolean)
language sql stable security definer set search_path = public as $fn$
  with scoped as (
    select m.id, m.scheduled_at, m.created_at,
      exists (select 1 from public.match_favorites mf
              where mf.user_id = auth.uid() and mf.match_id = m.id) as is_favorite,
      exists (select 1 from public.follows f
              where f.user_id = auth.uid()
                and (f.team_id in (m.home_team_id, m.away_team_id)
                     or f.academy_id in (m.academy_id, public.team_academy(m.away_team_id)))) as by_follow,
      exists (select 1 from public.team_players tp
              join public.guardian_player_links g on g.player_id = tp.player_id
              where tp.status = 'active' and tp.team_id in (m.home_team_id, m.away_team_id)
                and g.guardian_user_id = auth.uid() and g.status = 'approved') as by_child,
      (exists (select 1 from public.team_staff ts
               where ts.user_id = auth.uid() and ts.team_id in (m.home_team_id, m.away_team_id))
       or exists (select 1 from public.academy_memberships am
                  where am.user_id = auth.uid() and am.role = 'adm' and am.status = 'active'
                    and am.academy_id in (m.academy_id, public.team_academy(m.away_team_id)))
       or exists (select 1 from public.match_operators mo
                  where mo.match_id = m.id and mo.user_id = auth.uid() and mo.active)) as by_staff,
      exists (select 1 from public.team_players tp
              join public.players pl on pl.id = tp.player_id
              where tp.status = 'active' and tp.team_id in (m.home_team_id, m.away_team_id)
                and pl.user_id = auth.uid()) as by_player
    from public.matches m
  )
  select s.id, s.is_favorite, s.by_follow, s.by_child, s.by_staff, s.by_player
  from scoped s
  where (s.is_favorite or s.by_follow or s.by_child or s.by_staff or s.by_player)
    and public.can_view_match(s.id)
  order by s.scheduled_at desc nulls last, s.created_at desc
  limit 100
$fn$;

-- Solo usuarios con sesión
revoke execute on function public.toggle_match_favorite(uuid) from public, anon;
revoke execute on function public.my_matches() from public, anon;
grant execute on function public.toggle_match_favorite(uuid) to authenticated;
grant execute on function public.my_matches() to authenticated;

commit;
