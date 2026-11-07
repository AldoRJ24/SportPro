-- =====================================================================
-- SPORTPRO — Datos de DEMO (todo ficticio). Ejecutar DESPUÉS de la migración.
--
-- ANTES: registra desde la app (o en Authentication → Users → Add user) las dos cuentas:
--   v_main   = tu cuenta principal (será superadmin + ADM + DT + operador)
--   v_second = tu segunda cuenta (apoderada de un jugador y seguidora; para el 2.º celular)
-- Cambia los dos correos de abajo por los reales y ejecuta TODO en el SQL Editor.
-- Es re-ejecutable: si la academia demo ya existe, no duplica nada.
-- =====================================================================
do $seed$
declare
  v_main_email   text := 'cuenta-principal@ejemplo.com';   -- <<< EDITAR
  v_second_email text := 'cuenta-secundaria@ejemplo.com';  -- <<< EDITAR
  v_main uuid; v_second uuid;
  v_academy uuid; v_home uuid; v_away uuid; v_match uuid; v_child uuid;
  v_firsts text[] := array['Mateo','Luis','Diego','Joaquín','Thiago','Bruno','Sebastián','Adrián','Gael'];
  v_lasts_h text[] := array['Quispe','Huamán','Rojas','Flores','Vargas','Castillo','Mendoza','Salas','Paredes'];
  v_lasts_l text[] := array['Torres','Rivera','Campos','Silva','Ramos','Ortiz','Cruz','Vega','Luna'];
  v_pos text[] := array['Portero','Defensa','Defensa','Mediocampista','Mediocampista','Mediocampista','Delantero','Delantero','Defensa'];
  v_pid uuid; i int;
begin
  select id into v_main   from auth.users where lower(email) = lower(v_main_email);
  select id into v_second from auth.users where lower(email) = lower(v_second_email);
  if v_main is null then
    raise exception 'No existe el usuario % . Regístralo primero en la app (o en Authentication → Users).', v_main_email;
  end if;
  if exists (select 1 from public.academies where name = 'Academia Demo SportPro') then
    raise notice 'La academia demo ya existe: no se duplica nada.'; return;
  end if;

  update public.profiles set birth_date = coalesce(birth_date, date '1999-05-01'), full_name = case when full_name = '' then 'Aldo (demo)' else full_name end where id = v_main;
  if v_second is not null then
    update public.profiles set birth_date = coalesce(birth_date, date '1998-01-01'), full_name = case when full_name = '' then 'Apoderada (demo)' else full_name end where id = v_second;
  end if;

  insert into public.platform_admins (user_id) values (v_main) on conflict do nothing;

  insert into public.academies (name, city, status, created_by) values ('Academia Demo SportPro', 'Lima', 'verified', v_main) returning id into v_academy;
  insert into public.academy_memberships (academy_id, user_id, role) values
    (v_academy, v_main, 'adm'), (v_academy, v_main, 'dt');
  if v_second is not null then
    insert into public.academy_memberships (academy_id, user_id, role) values (v_academy, v_second, 'pad');
  end if;

  insert into public.teams (academy_id, name, category, max_age, default_format_players, half_minutes)
    values (v_academy, 'Halcones Sub-15', 'Sub-15', 15, 7, 25) returning id into v_home;
  insert into public.teams (academy_id, name, category, max_age, default_format_players, half_minutes)
    values (v_academy, 'Leones Sub-15', 'Sub-15', 15, 7, 25) returning id into v_away;
  insert into public.team_staff (team_id, user_id, staff_role) values (v_home, v_main, 'dt'), (v_away, v_main, 'dt');

  for i in 1..9 loop
    insert into public.players (first_name, last_name, birth_date, position, created_by)
      values (v_firsts[i], v_lasts_h[i], date '2012-03-01' + (i * 17), v_pos[i], v_main) returning id into v_pid;
    insert into public.team_players (team_id, player_id, jersey_number) values (v_home, v_pid, i);
    if i = 5 and v_second is not null then
      insert into public.guardian_player_links (guardian_user_id, player_id, status, is_primary, decided_at)
        values (v_second, v_pid, 'approved', true, now());
    end if;
    insert into public.players (first_name, last_name, birth_date, position, created_by)
      values (v_firsts[10 - i], v_lasts_l[i], date '2012-05-01' + (i * 13), v_pos[i], v_main) returning id into v_pid;
    insert into public.team_players (team_id, player_id, jersey_number) values (v_away, v_pid, i);
  end loop;

  -- Partido listo para iniciar: convocados confirmados, 7 titulares por lado, operador = tu cuenta principal
  insert into public.matches (home_team_id, away_team_id, format_players, half_minutes, visibility, venue, scheduled_at, created_by)
    values (v_home, v_away, 7, 25, 'followers', 'Cancha demo', now(), v_main) returning id into v_match;
  insert into public.match_callups (match_id, team_id, player_id, status)
    select v_match, tp.team_id, tp.player_id, 'confirmed' from public.team_players tp where tp.team_id in (v_home, v_away);
  insert into public.match_lineup_players (match_id, team_side, player_id, is_starter)
    select v_match, 'home', player_id, jersey_number <= 7 from public.team_players where team_id = v_home;
  insert into public.match_lineup_players (match_id, team_side, player_id, is_starter)
    select v_match, 'away', player_id, jersey_number <= 7 from public.team_players where team_id = v_away;
  insert into public.match_operators (match_id, user_id, assigned_by) values (v_match, v_main, v_main);

  -- Posiciones (requiere la migración 002): plantel y alineación del partido demo
  update public.team_players tp set position = case
           when p.position = 'Portero' then 'POR' when p.position = 'Defensa' then 'DEF'
           when p.position = 'Mediocampista' then 'MED' else 'DEL' end
    from public.players p where p.id = tp.player_id and tp.team_id in (v_home, v_away);
  update public.match_lineup_players l set field_position = tp.position
    from public.team_players tp
   where l.match_id = v_match and tp.player_id = l.player_id and tp.team_id in (v_home, v_away);

  if v_second is not null then
    insert into public.follows (user_id, team_id) values (v_second, v_home) on conflict do nothing;
  end if;
  raise notice 'Demo creada. Academia %, partido % listo para iniciar.', v_academy, v_match;
end $seed$;
