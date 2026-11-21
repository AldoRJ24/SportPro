-- =====================================================================
-- SportPro · Migración 018: ALINEACIÓN EN LA CANCHA.
--
--   1. Cada titular guarda DÓNDE está en la cancha (slot_x, slot_y). Las coordenadas son
--      relativas a la MITAD propia, como si el equipo atacara hacia arriba:
--        x: 0 = banda izquierda, 1 = banda derecha;
--        y: 0 = línea del arco propio, 1 = línea de medio campo.
--      Así la app dibuja al local arriba y al visitante abajo sin guardar dos versiones.
--      Si un titular no tiene coordenadas (partidos viejos o autocompletado del servidor),
--      la app lo acomoda según la formación.
--   2. Cada lado guarda el NOMBRE de su formación («4-4-2», «4-2-3-1»...) para mostrarlo.
--   3. FORMACIONES PROPIAS del entrenador: si mueve a los jugadores y la disposición ya no
--      es la predeterminada, puede guardarla con un nombre. Solo la ve y la usa él.
--   4. Guardar la alineación pasa a ser UNA sola operación en el servidor (lineup_save):
--      antes la app borraba e insertaba en dos pedidos; si el segundo fallaba, la
--      alineación quedaba vacía. Ahora todo entra o nada entra.
--   5. get_match_lineup_pitch: la alineación con coordenadas y foto del jugador. La foto
--      solo sale si es mayor de edad o si quien consulta es su apoderado, el propio
--      jugador o el staff de su equipo.
--
-- Cómo ejecutarla: Supabase Dashboard → SQL Editor → pegar TODO → Run (transaccional).
-- =====================================================================
begin;

-- ---------------------------------------------------------------------
-- 1. Coordenadas de cada titular
-- ---------------------------------------------------------------------
alter table public.match_lineup_players add column if not exists slot_x real;
alter table public.match_lineup_players add column if not exists slot_y real;
-- Las dos coordenadas van juntas, dentro de la cancha y solo en titulares
alter table public.match_lineup_players drop constraint if exists lineup_slot_chk;
alter table public.match_lineup_players add constraint lineup_slot_chk check (
  (slot_x is null) = (slot_y is null)
  and (slot_x is null or (is_starter and slot_x between 0 and 1 and slot_y between 0 and 1)));

-- ---------------------------------------------------------------------
-- 2. Nombre de la formación de cada lado
-- ---------------------------------------------------------------------
-- Formato: líneas de jugadores de campo, sin el arquero (4-4-2, 4-2-3-1, 2-3-1...)
create or replace function public._formation_label_ok(p_label text, p_format int)
returns boolean language plpgsql immutable set search_path = public as $fn$
declare v_sum int := 0; v_part text;
begin
  if p_label is null or p_label !~ '^[1-9](-[1-9]){1,4}$' then return false; end if;
  foreach v_part in array string_to_array(p_label, '-') loop
    v_sum := v_sum + v_part::int;
  end loop;
  -- Las líneas suman los jugadores de campo: formato menos el arquero
  return p_format is null or v_sum = p_format - 1;
end $fn$;

create table if not exists public.match_lineup_formations (
  match_id   uuid not null references public.matches(id) on delete cascade,
  team_side  text not null check (team_side in ('home','away')),
  formation  text not null check (public._formation_label_ok(formation, null)),
  updated_by uuid references public.profiles(id),
  updated_at timestamptz not null default now(),
  primary key (match_id, team_side)
);
alter table public.match_lineup_formations enable row level security;
revoke all on public.match_lineup_formations from anon, authenticated;
grant select on public.match_lineup_formations to authenticated;
-- Se ve igual que la alineación: en un partido acordado, el lado ajeno recién al confirmarse
drop policy if exists match_lineup_formations_select on public.match_lineup_formations;
create policy match_lineup_formations_select on public.match_lineup_formations for select to authenticated
  using (public.can_view_lineup_side(match_id, team_side));

-- ---------------------------------------------------------------------
-- 3. Formaciones propias del entrenador
-- ---------------------------------------------------------------------
create table if not exists public.coach_formations (
  id              uuid primary key default gen_random_uuid(),
  owner_id        uuid not null references public.profiles(id) on delete cascade,
  format_players  smallint not null check (format_players between 5 and 11),
  name            text not null check (char_length(trim(name)) between 1 and 30),
  base_formation  text not null check (public._formation_label_ok(base_formation, null)),
  -- [{ "pos": "POR|DEF|MED|DEL", "x": 0..1, "y": 0..1 }, ...] (lo valida coach_formation_save)
  slots           jsonb not null,
  created_at      timestamptz not null default now(),
  -- No se borran: se archivan (trazabilidad)
  archived_at     timestamptz,
  archived_reason text
);
create index if not exists coach_formations_owner_idx on public.coach_formations (owner_id, format_players)
  where archived_at is null;
alter table public.coach_formations enable row level security;
revoke all on public.coach_formations from anon, authenticated;
grant select on public.coach_formations to authenticated;
-- Cada entrenador ve SOLO las suyas
drop policy if exists coach_formations_select on public.coach_formations;
create policy coach_formations_select on public.coach_formations for select to authenticated
  using (owner_id = auth.uid());

-- ¿Las casillas son válidas para ese formato? Tantas como jugadores, 1 arquero, dentro de la cancha.
create or replace function public._formation_slots_ok(p_slots jsonb, p_format int)
returns boolean language plpgsql immutable set search_path = public as $fn$
declare s jsonb; v_gk int := 0; v_x numeric; v_y numeric;
begin
  if p_slots is null or jsonb_typeof(p_slots) <> 'array' or jsonb_array_length(p_slots) <> p_format then
    return false;
  end if;
  for s in select * from jsonb_array_elements(p_slots) loop
    if jsonb_typeof(s) <> 'object' or coalesce(s->>'pos', '') not in ('POR','DEF','MED','DEL')
       or jsonb_typeof(s->'x') <> 'number' or jsonb_typeof(s->'y') <> 'number' then
      return false;
    end if;
    v_x := (s->>'x')::numeric; v_y := (s->>'y')::numeric;
    if v_x < 0 or v_x > 1 or v_y < 0 or v_y > 1 then return false; end if;
    if s->>'pos' = 'POR' then v_gk := v_gk + 1; end if;
  end loop;
  return v_gk = 1;
end $fn$;

-- Guardar una formación propia. Devuelve su id.
create or replace function public.coach_formation_save(p_format int, p_name text, p_base text, p_slots jsonb)
returns uuid language plpgsql security definer set search_path = public as $fn$
declare v_id uuid; v_name text := trim(coalesce(p_name, ''));
begin
  if auth.uid() is null then raise exception 'Inicia sesión para guardar formaciones'; end if;
  -- Solo entrenadores (staff de algún equipo) o administradores de academia
  if not (exists (select 1 from public.team_staff where user_id = auth.uid())
          or exists (select 1 from public.academy_memberships where user_id = auth.uid()
                     and role in ('dt','adm') and status = 'active')) then
    raise exception 'Solo los entrenadores guardan formaciones propias';
  end if;
  if p_format is null or p_format not between 5 and 11 then raise exception 'Formato inválido'; end if;
  if char_length(v_name) not between 1 and 30 then raise exception 'El nombre debe tener entre 1 y 30 caracteres'; end if;
  if not public._formation_label_ok(p_base, p_format) then
    raise exception 'La formación base % no corresponde a fútbol %', p_base, p_format;
  end if;
  if not public._formation_slots_ok(p_slots, p_format) then
    raise exception 'La formación debe tener % jugadores dentro de la cancha y exactamente 1 arquero', p_format;
  end if;
  if exists (select 1 from public.coach_formations where owner_id = auth.uid() and format_players = p_format
             and archived_at is null and lower(name) = lower(v_name)) then
    raise exception 'Ya tienes una formación llamada «%» para fútbol %', v_name, p_format;
  end if;
  if (select count(*) from public.coach_formations where owner_id = auth.uid() and archived_at is null) >= 30 then
    raise exception 'Tienes 30 formaciones guardadas: archiva alguna antes de guardar otra';
  end if;

  insert into public.coach_formations (owner_id, format_players, name, base_formation, slots)
  values (auth.uid(), p_format, v_name, p_base, p_slots)
  returning id into v_id;
  perform public.log_audit('coach_formation_saved', 'coach_formation', v_id,
    jsonb_build_object('format', p_format, 'name', v_name, 'base', p_base));
  return v_id;
end $fn$;

-- Archivar (no borrar) una formación propia
create or replace function public.coach_formation_archive(p_id uuid, p_reason text default null)
returns void language plpgsql security definer set search_path = public as $fn$
begin
  if not exists (select 1 from public.coach_formations where id = p_id and owner_id = auth.uid()) then
    raise exception 'Formación inexistente';
  end if;
  update public.coach_formations
     set archived_at = now(), archived_reason = nullif(trim(coalesce(p_reason, '')), '')
   where id = p_id and archived_at is null;
  perform public.log_audit('coach_formation_archived', 'coach_formation', p_id,
    jsonb_build_object('reason', p_reason));
end $fn$;

-- ---------------------------------------------------------------------
-- 4. Guardar la alineación de un lado en UNA sola operación
-- ---------------------------------------------------------------------
-- p_entries: [{ player_id, is_starter, field_position, jersey_number, slot_x, slot_y }, ...]
-- Los triggers de siempre siguen validando cada fila: jugador del equipo, convocado que
-- confirmó, plazo de 1 hora y confirmación en partidos acordados (y borran el «enviado»).
create or replace function public.lineup_save(p_match uuid, p_side text, p_formation text, p_entries jsonb)
returns void language plpgsql security definer set search_path = public as $fn$
declare m public.matches%rowtype; e jsonb;
begin
  select * into m from public.matches where id = p_match;
  if not found then raise exception 'Partido inexistente'; end if;
  if p_side is null or p_side not in ('home','away') then raise exception 'Lado inválido'; end if;
  -- La función salta las políticas de fila: el permiso se revisa aquí con la misma regla
  if not public.can_write_lineup_side(p_match, p_side) then
    raise exception 'Sin permiso para armar la alineación de este equipo';
  end if;
  if public._match_side_team(p_match, p_side) is null then raise exception 'El rival es externo: use el plantel manual'; end if;
  if m.status <> 'scheduled' then raise exception 'La alineación solo se edita antes de iniciar el partido'; end if;
  -- Mismos mensajes que el trigger, también para una alineación vacía (que no dispara triggers)
  if m.lineup_flow and m.lineups_confirmed_at is not null then
    raise exception 'Las alineaciones ya están confirmadas y no se pueden cambiar';
  end if;
  if m.lineup_flow and m.scheduled_at <= now() + interval '1 hour' then
    raise exception 'El plazo para editar la alineación venció (1 hora antes del partido)';
  end if;
  if p_formation is not null and not public._formation_label_ok(p_formation, m.format_players) then
    raise exception 'La formación % no corresponde a fútbol %', p_formation, m.format_players;
  end if;
  if p_entries is null or jsonb_typeof(p_entries) <> 'array' then raise exception 'Alineación inválida'; end if;
  if jsonb_array_length(p_entries) > 40 then raise exception 'Demasiados jugadores en la alineación'; end if;

  -- Se reemplaza la alineación de ese lado (si algo falla, la transacción deshace todo)
  delete from public.match_lineup_players where match_id = p_match and team_side = p_side;
  for e in select * from jsonb_array_elements(p_entries) loop
    insert into public.match_lineup_players
      (match_id, team_side, player_id, is_starter, field_position, jersey_number, slot_x, slot_y)
    values (p_match, p_side, (e->>'player_id')::uuid, coalesce((e->>'is_starter')::boolean, false),
            nullif(e->>'field_position', ''), (e->>'jersey_number')::smallint,
            (e->>'slot_x')::real, (e->>'slot_y')::real);
  end loop;

  if p_formation is null then
    delete from public.match_lineup_formations where match_id = p_match and team_side = p_side;
  else
    insert into public.match_lineup_formations (match_id, team_side, formation, updated_by)
    values (p_match, p_side, p_formation, auth.uid())
    on conflict (match_id, team_side) do update
      set formation = excluded.formation, updated_by = excluded.updated_by, updated_at = now();
  end if;

  perform public.log_audit('match_lineup_saved', 'match', p_match,
    jsonb_build_object('side', p_side, 'formation', p_formation, 'players', jsonb_array_length(p_entries)));
end $fn$;

-- ---------------------------------------------------------------------
-- 5. Foto del jugador en la alineación (privacidad de menores)
-- ---------------------------------------------------------------------
-- La foto es la del perfil de su cuenta. Se entrega solo si es mayor de edad o si quien
-- consulta puede ver sus datos privados (apoderado, él mismo o el staff de su equipo).
create or replace function public._player_photo(p_player uuid)
returns text language sql stable security definer set search_path = public as $fn$
  select pr.avatar_path
  from public.players pl
  join public.profiles pr on pr.id = pl.user_id
  where pl.id = p_player
    and (public.age_years(pl.birth_date) >= 18 or public.can_view_player_private(pl.id))
$fn$;

-- Fotos del plantel para el editor de alineación (solo el staff o el admin del equipo).
-- Devuelve únicamente a los jugadores que tienen foto; los demás se dibujan con iniciales.
create or replace function public.get_roster_photos(p_team uuid)
returns table (player_id uuid, photo_path text)
language sql stable security definer set search_path = public as $fn$
  select tp.player_id, ph.photo
  from public.team_players tp
  cross join lateral (select public._player_photo(tp.player_id) as photo) ph
  where tp.team_id = p_team and tp.status = 'active' and ph.photo is not null
    and public.is_team_manager(p_team)
$fn$;

-- Alineación para dibujar la cancha: lo mismo que get_match_lineup (versión de la 017, con
-- las mismas reglas de visibilidad por lado) más las coordenadas y la foto.
-- Es una función NUEVA y no un cambio de get_match_lineup: cambiar su tipo de resultado
-- obligaría a borrarla, y las migraciones 002 y 017 (que la redefinen) dejarían de poder
-- ejecutarse otra vez.
create or replace function public.get_match_lineup_pitch(p_match uuid)
returns table (team_side text, player_id uuid, external_player_id uuid, player_name text,
               jersey_number smallint, is_starter boolean, field_position text,
               slot_x real, slot_y real, photo_path text)
language sql stable security definer set search_path = public as $fn$
  select l.team_side, l.player_id, null::uuid, public.player_display_name(l.player_id),
         coalesce(l.jersey_number, tp.jersey_number),
         l.is_starter, coalesce(l.field_position, tp.position),
         l.slot_x, l.slot_y, public._player_photo(l.player_id)
  from public.match_lineup_players l
  join public.matches mm on mm.id = l.match_id
  left join public.team_players tp
         on tp.player_id = l.player_id
        and tp.team_id = case l.team_side when 'home' then mm.home_team_id else mm.away_team_id end
  where l.match_id = p_match and public.can_view_lineup_side(p_match, l.team_side)
  union all
  -- El rival externo no tiene cuenta ni foto; la app lo acomoda según su formación
  select 'away', null::uuid, x.id, x.name, x.jersey_number, x.is_starter, x.field_position,
         null::real, null::real, null::text
  from public.match_external_players x
  where x.match_id = p_match and public.can_view_match(p_match)
$fn$;

-- ---------------------------------------------------------------------
-- 6. Permisos de ejecución
-- ---------------------------------------------------------------------
revoke execute on function public._formation_label_ok(text, int), public._formation_slots_ok(jsonb, int),
  public._player_photo(uuid)
  from public, anon, authenticated;
revoke execute on function public.coach_formation_save(int, text, text, jsonb),
  public.coach_formation_archive(uuid, text), public.lineup_save(uuid, text, text, jsonb),
  public.get_match_lineup_pitch(uuid), public.get_roster_photos(uuid)
  from public, anon;
grant execute on function public.coach_formation_save(int, text, text, jsonb),
  public.coach_formation_archive(uuid, text), public.lineup_save(uuid, text, text, jsonb),
  public.get_match_lineup_pitch(uuid), public.get_roster_photos(uuid)
  to authenticated;

commit;
