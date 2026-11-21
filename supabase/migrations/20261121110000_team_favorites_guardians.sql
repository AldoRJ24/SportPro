-- =====================================================================
-- SportPro · Migración 019: equipos favoritos y apoderados en el plantel.
--
--   1. EQUIPOS FAVORITOS: cada usuario marca con una estrella los equipos que quiere tener a
--      mano. Es aparte de «Seguir»: no cambia qué partidos ve ni le manda avisos.
--   2. APODERADOS EN EL PLANTEL: el staff del equipo (DT, asistente o administrador) ve quién
--      es el apoderado de cada jugador menor de edad. Nadie más lo ve (privacidad de menores).
--
-- Cómo ejecutarla: Supabase Dashboard → SQL Editor → pegar TODO → Run (transaccional).
-- =====================================================================
begin;

-- ---------------------------------------------------------------------
-- 1. Equipos favoritos
-- ---------------------------------------------------------------------
create table if not exists public.team_favorites (
  user_id    uuid not null references public.profiles(id) on delete cascade,
  team_id    uuid not null references public.teams(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (user_id, team_id)
);
create index if not exists team_favorites_team on public.team_favorites (team_id);

alter table public.team_favorites enable row level security;
-- Supabase da todos los permisos por defecto: se quitan y se deja solo lectura
revoke all on public.team_favorites from anon, authenticated;
grant select on public.team_favorites to authenticated;

-- Cada usuario ve solo sus favoritos
drop policy if exists team_favorites_select on public.team_favorites;
create policy team_favorites_select on public.team_favorites for select to authenticated
  using (user_id = (select auth.uid()));

-- Marcar / desmarcar. Devuelve true si quedó marcado. (Un favorito es una preferencia
-- personal, no un registro del club: quitarlo sí lo borra, igual que los partidos favoritos.)
create or replace function public.toggle_team_favorite(p_team uuid)
returns boolean language plpgsql security definer set search_path = public as $fn$
begin
  if auth.uid() is null then raise exception 'Inicia sesión para guardar favoritos'; end if;
  delete from public.team_favorites where user_id = auth.uid() and team_id = p_team;
  if found then return false; end if;
  if not exists (select 1 from public.teams where id = p_team) then
    raise exception 'Equipo inexistente';
  end if;
  insert into public.team_favorites (user_id, team_id) values (auth.uid(), p_team)
  on conflict do nothing;
  return true;
end $fn$;

-- ---------------------------------------------------------------------
-- 2. Apoderados de los jugadores menores de un equipo (solo para su staff)
-- ---------------------------------------------------------------------
-- Una fila por jugador MENOR activo: nombres de sus apoderados aprobados (el principal
-- primero) o null si todavía no tiene ninguno aprobado.
create or replace function public.team_guardians(p_team uuid)
returns table (player_id uuid, guardian_names text)
language sql stable security definer set search_path = public as $fn$
  select tp.player_id,
         (select string_agg(coalesce(nullif(trim(pr.full_name), ''), 'Apoderado'), ', '
                            order by g.is_primary desc, g.created_at)
            from public.guardian_player_links g
            join public.profiles pr on pr.id = g.guardian_user_id
           where g.player_id = tp.player_id and g.status = 'approved')
  from public.team_players tp
  join public.players pl on pl.id = tp.player_id
  where tp.team_id = p_team and tp.status = 'active'
    and public.age_years(pl.birth_date) < 18
    and public.is_team_manager(p_team)
$fn$;

-- ---------------------------------------------------------------------
-- 3. Permisos de ejecución
-- ---------------------------------------------------------------------
revoke execute on function public.toggle_team_favorite(uuid), public.team_guardians(uuid) from public, anon;
grant execute on function public.toggle_team_favorite(uuid), public.team_guardians(uuid) to authenticated;

commit;
