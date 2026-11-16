-- =====================================================================
-- SportPro · Migración 012: gestión del plantel por el DT.
--
--  1. Cambiar el DORSAL de un jugador con una función que avisa claro si el número
--     ya lo usa otro jugador activo del mismo equipo.
--  2. DAR DE BAJA a un jugador: no se borra, pasa a «inactive» con MOTIVO y fecha.
--     Se avisa a su familia y queda en audit_log.
--  3. Cierre de un hueco: hasta ahora la app podía, técnicamente, BORRAR filas del plantel
--     o darlas de baja sin motivo escribiendo directo en la tabla. Ahora:
--        · se quita el permiso de DELETE sobre team_players (nada se borra);
--        · un trigger exige motivo en toda baja, venga de donde venga.
--  4. Si el jugador vuelve a entrar (nueva solicitud aceptada), se limpian motivo y fecha.
--
-- Cómo ejecutarla: Supabase Dashboard → SQL Editor → pegar TODO → Run (transaccional).
-- =====================================================================
begin;

alter table public.team_players add column if not exists inactive_reason text;
alter table public.team_players add column if not exists left_at timestamptz;

-- Nada se borra del plantel: la baja es un cambio de estado
revoke delete on public.team_players from authenticated;

-- ---------------------------------------------------------------------
-- Trigger: toda baja lleva motivo; todo regreso limpia el motivo
-- ---------------------------------------------------------------------
create or replace function public.trg_team_players_status()
returns trigger language plpgsql set search_path = public as $fn$
begin
  if old.status = 'active' and new.status = 'inactive' then
    if char_length(trim(coalesce(new.inactive_reason, ''))) < 3 then
      raise exception 'Escribe el motivo de la baja';
    end if;
    new.inactive_reason := trim(new.inactive_reason);
    new.left_at := coalesce(new.left_at, now());
  elsif old.status = 'inactive' and new.status = 'active' then
    new.inactive_reason := null;
    new.left_at := null;
  end if;
  return new;
end $fn$;

drop trigger if exists trg_team_players_status on public.team_players;
create trigger trg_team_players_status before update of status on public.team_players
  for each row execute function public.trg_team_players_status();

-- ---------------------------------------------------------------------
-- 1) Cambiar el dorsal (null = sin dorsal)
-- ---------------------------------------------------------------------
create or replace function public.team_set_jersey(p_team_player uuid, p_number int)
returns void language plpgsql security definer set search_path = public as $fn$
declare tp public.team_players%rowtype;
begin
  select * into tp from public.team_players where id = p_team_player for update;
  if not found then raise exception 'Jugador no encontrado en el plantel'; end if;
  if not public.is_team_manager(tp.team_id) then
    raise exception 'Solo el DT o el administrador del equipo cambia los dorsales';
  end if;
  if tp.status <> 'active' then raise exception 'El jugador ya no está activo en el equipo'; end if;
  if p_number is not null and (p_number < 0 or p_number > 99) then
    raise exception 'El dorsal debe estar entre 0 y 99';
  end if;
  -- Se comprueba antes de guardar para dar un mensaje claro (el índice único lo impide igual)
  if p_number is not null and exists (
       select 1 from public.team_players o
       where o.team_id = tp.team_id and o.status = 'active' and o.jersey_number = p_number and o.id <> tp.id) then
    raise exception 'El dorsal % ya lo usa otro jugador del equipo', p_number;
  end if;
  update public.team_players set jersey_number = p_number where id = tp.id;
  perform public.log_audit('team_jersey_changed', 'team', tp.team_id,
    jsonb_build_object('player_id', tp.player_id, 'from', tp.jersey_number, 'to', p_number));
end $fn$;

-- ---------------------------------------------------------------------
-- 2) Dar de baja con motivo
-- ---------------------------------------------------------------------
create or replace function public.team_remove_player(p_team_player uuid, p_reason text)
returns void language plpgsql security definer set search_path = public as $fn$
declare tp public.team_players%rowtype; v_name text;
begin
  select * into tp from public.team_players where id = p_team_player for update;
  if not found then raise exception 'Jugador no encontrado en el plantel'; end if;
  if not public.is_team_manager(tp.team_id) then
    raise exception 'Solo el DT o el administrador del equipo da de baja a un jugador';
  end if;
  if tp.status <> 'active' then raise exception 'El jugador ya está dado de baja'; end if;
  if char_length(trim(coalesce(p_reason, ''))) < 3 then raise exception 'Escribe el motivo de la baja'; end if;

  update public.team_players set status = 'inactive', inactive_reason = trim(p_reason), left_at = now()
   where id = tp.id;
  -- Deja de estar en la lista de mensualidades del equipo (los cobros ya generados se conservan)
  update public.fee_players set active = false, updated_by = auth.uid(), updated_at = now()
   where team_id = tp.team_id and player_id = tp.player_id and active;

  select trim(first_name || ' ' || last_name) into v_name from public.players where id = tp.player_id;
  -- Aviso a los apoderados y a la cuenta del jugador
  perform public._notify(s.u, 'team_player_removed', 'Baja del equipo',
            v_name || ' fue dado de baja de ' || public._team_label(tp.team_id) || '. Motivo: ' || trim(p_reason),
            jsonb_build_object('team_id', tp.team_id, 'player_id', tp.player_id))
  from (select g.guardian_user_id as u from public.guardian_player_links g
         where g.player_id = tp.player_id and g.status = 'approved'
        union
        select pl.user_id from public.players pl where pl.id = tp.player_id and pl.user_id is not null) s;

  perform public.log_audit('team_player_removed', 'team', tp.team_id,
    jsonb_build_object('player_id', tp.player_id, 'reason', trim(p_reason)));
end $fn$;

revoke execute on function public.trg_team_players_status() from public, anon, authenticated;
revoke execute on function public.team_set_jersey(uuid, int), public.team_remove_player(uuid, text) from public, anon;
grant execute on function public.team_set_jersey(uuid, int), public.team_remove_player(uuid, text) to authenticated;

commit;
