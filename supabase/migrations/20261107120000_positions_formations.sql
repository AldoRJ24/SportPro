-- =====================================================================
-- SportPro · Migración 002: posiciones y formaciones
-- - Posiciones fijas: POR (arquero), DEF, MED, DEL.
-- - El DT asigna la posición habitual de cada jugador en su plantel (team_players.position).
-- - Cada partido guarda la posición de cada titular (match_lineup_players.field_position).
-- - El rival externo también tiene posición y titularidad.
-- - Al iniciar: exactamente 1 arquero titular por equipo y rival externo con titulares completos.
-- =====================================================================
begin;

-- 1) Columnas y valores permitidos -------------------------------------
alter table public.team_players
  add column if not exists position text;
alter table public.team_players
  drop constraint if exists team_players_position_chk;
alter table public.team_players
  add constraint team_players_position_chk check (position is null or position in ('POR','DEF','MED','DEL'));

alter table public.match_external_players
  add column if not exists field_position text;
alter table public.match_external_players
  drop constraint if exists mep_position_chk;
alter table public.match_external_players
  add constraint mep_position_chk check (field_position is null or field_position in ('POR','DEF','MED','DEL'));

-- Normaliza valores previos de la alineación antes de la restricción
update public.match_lineup_players set field_position = null
 where field_position is not null and field_position not in ('POR','DEF','MED','DEL');
alter table public.match_lineup_players
  drop constraint if exists lineup_position_chk;
alter table public.match_lineup_players
  add constraint lineup_position_chk check (field_position is null or field_position in ('POR','DEF','MED','DEL'));

-- 2) Rellenar la posición del plantel desde el texto libre del jugador ----
update public.team_players tp
   set position = case
         when lower(p.position) like 'port%' or lower(p.position) like 'arq%' then 'POR'
         when lower(p.position) like 'def%' then 'DEF'
         when lower(p.position) like 'med%' or lower(p.position) like 'vol%' then 'MED'
         when lower(p.position) like 'del%' then 'DEL'
       end
  from public.players p
 where p.id = tp.player_id and tp.position is null;

-- Partidos aún no iniciados: completar la posición de la alineación con la del plantel
update public.match_lineup_players l
   set field_position = tp.position
  from public.matches m, public.team_players tp
 where m.id = l.match_id and m.status = 'scheduled' and l.field_position is null
   and tp.player_id = l.player_id
   and tp.team_id = case l.team_side when 'home' then m.home_team_id else m.away_team_id end;

-- 3) La alineación devuelve también la posición (incluido el rival externo)
create or replace function public.get_match_lineup(p_match uuid)
returns table (team_side text, player_id uuid, external_player_id uuid, player_name text,
               jersey_number smallint, is_starter boolean, field_position text)
language sql stable security definer set search_path = public as $fn$
  select l.team_side, l.player_id, null::uuid, public.player_display_name(l.player_id),
         coalesce(l.jersey_number, tp.jersey_number),
         l.is_starter, coalesce(l.field_position, tp.position)
  from public.match_lineup_players l
  join public.matches mm on mm.id = l.match_id
  left join public.team_players tp
         on tp.player_id = l.player_id
        and tp.team_id = case l.team_side when 'home' then mm.home_team_id else mm.away_team_id end
  where l.match_id = p_match and public.can_view_match(p_match)
  union all
  select 'away', null::uuid, x.id, x.name, x.jersey_number, x.is_starter, x.field_position
  from public.match_external_players x
  where x.match_id = p_match and public.can_view_match(p_match)
$fn$;
grant execute on function public.get_match_lineup(uuid) to authenticated;

-- 4) Regla en el servidor al iniciar el partido -------------------------
create or replace function public.trg_matches_start_formation()
returns trigger language plpgsql security definer set search_path = public as $fn$
declare v_gk int; v_n int;
begin
  if old.status = 'scheduled' and new.status = 'live' then
    select count(*) into v_gk from public.match_lineup_players
     where match_id = new.id and team_side = 'home' and is_starter and field_position = 'POR';
    if v_gk <> 1 then
      raise exception 'El equipo local debe tener exactamente 1 arquero titular (tiene %). Asígnalo en la alineación.', v_gk;
    end if;
    if new.away_team_id is not null then
      select count(*) into v_gk from public.match_lineup_players
       where match_id = new.id and team_side = 'away' and is_starter and field_position = 'POR';
      if v_gk <> 1 then
        raise exception 'El equipo visitante debe tener exactamente 1 arquero titular (tiene %).', v_gk;
      end if;
    else
      select count(*), count(*) filter (where field_position = 'POR') into v_n, v_gk
        from public.match_external_players where match_id = new.id and is_starter;
      if v_n < new.format_players then
        raise exception 'El rival externo necesita % titulares (tiene %). Complétalo en «Convocatoria y alineación».', new.format_players, v_n;
      end if;
      if v_gk <> 1 then
        raise exception 'El rival externo debe tener exactamente 1 arquero titular (tiene %).', v_gk;
      end if;
    end if;
  end if;
  return new;
end $fn$;

drop trigger if exists trg_matches_start_formation on public.matches;
create trigger trg_matches_start_formation before update of status on public.matches
  for each row execute function public.trg_matches_start_formation();

-- 5) La cronología incluye también el id del jugador externo (para saber quién está en cancha)
drop function if exists public.get_match_timeline(uuid);
create function public.get_match_timeline(p_match uuid)
returns table (
  event_id uuid, seq int, code text, type_name text, icon text, group_kind text,
  period smallint, minute smallint, minute_extra smallint, team_side text,
  player_id uuid, player_name text, jersey_number smallint,
  payload jsonb, notes text, status public.event_status, recorded_late boolean, created_at timestamptz,
  external_player_id uuid)
language sql stable security definer set search_path = public as $fn$
  select e.id, e.seq, t.code, t.name, t.icon, t.group_kind,
         e.period, e.minute, e.minute_extra, e.team_side,
         e.player_id,
         case when e.player_id is not null then public.player_display_name(e.player_id)
              when e.external_player_id is not null then (select x.name from public.match_external_players x where x.id = e.external_player_id)
         end,
         coalesce(
           (select tp.jersey_number from public.team_players tp join public.matches mm on mm.id = e.match_id
             where tp.player_id = e.player_id
               and tp.team_id = case e.team_side when 'home' then mm.home_team_id else mm.away_team_id end limit 1),
           (select x.jersey_number from public.match_external_players x where x.id = e.external_player_id)),
         e.payload, e.notes, e.status, e.recorded_late, e.created_at,
         e.external_player_id
  from public.match_events e
  join public.event_types t on t.id = e.event_type_id
  where e.match_id = p_match and public.can_view_match(p_match)
  order by e.seq
$fn$;
revoke execute on function public.get_match_timeline(uuid) from public, anon;
grant execute on function public.get_match_timeline(uuid) to authenticated;

commit;
