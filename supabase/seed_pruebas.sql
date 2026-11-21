-- =====================================================================
-- SportPro · DATOS DE PRUEBA COMPLETOS (todo ficticio).
--
-- Crea 11 cuentas (contraseña de todas: 12345678), 2 academias, 4 equipos con plantel,
-- partidos en todos los estados (finalizados con eventos, uno en entretiempo, uno listo para
-- iniciar, alineaciones pendientes, contra rival externo), desafíos, resúmenes publicados en
-- Inicio con «me gusta» y comentarios, mensualidades con pagos, avisos de pruebas,
-- solicitudes y formaciones propias. Las cuentas están en docs/datos-de-prueba.md.
--
-- Cómo se cargan los datos: casi todo se hace LLAMANDO A LAS MISMAS FUNCIONES DEL SERVIDOR
-- que usa la app, actuando como cada usuario (se fija su id con set_config, igual que el
-- token de la app). Así se respetan todas las reglas y se generan solas las notificaciones
-- y la auditoría. Solo se escribe directo donde la app también lo hace (equipos, plantel,
-- convocatorias, eventos) o para «mover en el tiempo» los partidos ya jugados.
--
-- Requisitos: todas las migraciones aplicadas (001 a 018).
-- Cómo ejecutarlo: Supabase Dashboard → SQL Editor → pegar TODO → Run.
-- Si las cuentas de prueba ya existen, no hace nada (no duplica).
-- No tiene fotos ni documentos: esos se suben desde la app (no se pueden crear por SQL).
--
-- Atomicidad: toda la carga ocurre dentro de UN solo bloque «do» (al final). Si algo falla,
-- Postgres deshace el bloque completo y no queda nada a medias. No se usa begin/commit porque
-- el SQL Editor confirma cada instrucción por separado.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Funciones auxiliares TEMPORALES (pg_temp: desaparecen al cerrar la sesión)
-- ---------------------------------------------------------------------

-- Actuar como un usuario: auth.uid() devuelve este id (null = como el dueño del proyecto)
create or replace function pg_temp.act(p_user uuid) returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claim.sub', coalesce(p_user::text, ''), true);
end $$;

-- Crea una cuenta con correo confirmado y contraseña 12345678 (igual que un registro desde la app).
-- El trigger handle_new_user crea el perfil, el tipo de cuenta y aplica la invitación si la hay.
create or replace function pg_temp.new_user(p_email text, p_meta jsonb) returns uuid language plpgsql as $$
declare v_id uuid := gen_random_uuid();
begin
  insert into auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
                          raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
                          confirmation_token, recovery_token, email_change_token_new, email_change,
                          email_change_token_current, phone_change, phone_change_token, reauthentication_token)
  values ('00000000-0000-0000-0000-000000000000', v_id, 'authenticated', 'authenticated', lower(p_email),
          extensions.crypt('12345678', extensions.gen_salt('bf')), now(),
          '{"provider":"email","providers":["email"]}'::jsonb, p_meta, now(), now(),
          '', '', '', '', '', '', '', '');
  insert into auth.identities (id, user_id, provider_id, identity_data, provider, last_sign_in_at, created_at, updated_at)
  values (gen_random_uuid(), v_id, v_id::text,
          jsonb_build_object('sub', v_id::text, 'email', lower(p_email), 'email_verified', true),
          'email', now(), now(), now());
  return v_id;
end $$;

-- Jugadores del seed por equipo y dorsal: H15 / T15 (Sub-15) y H12 / T12 (Sub-12).
-- La tabla temporal seed_pl se crea DENTRO del bloque de carga (ver más abajo), por eso
-- estas funciones son plpgsql: buscan la tabla al ejecutarse, no al crearse.
create or replace function pg_temp.pl(p_team text, p_jersey int) returns uuid language plpgsql as $$
begin
  return (select id from seed_pl where team = p_team and jersey = p_jersey);
end $$;

-- Alta de un jugador ficticio en el plantel (perfil sin cuenta, como lo crea el staff)
create or replace function pg_temp.add_player(p_key text, p_team uuid, p_jersey int, p_first text, p_last text,
                                              p_birth date, p_pos text) returns uuid language plpgsql as $$
declare v_id uuid;
begin
  insert into public.players (first_name, last_name, birth_date, position) values (p_first, p_last, p_birth, p_pos)
  returning id into v_id;
  perform pg_temp.enroll(p_key, p_team, p_jersey, v_id, p_pos);
  return v_id;
end $$;

-- Mete a un jugador que ya existe (por ejemplo, el hijo creado por su apoderada) al plantel
create or replace function pg_temp.enroll(p_key text, p_team uuid, p_jersey int, p_player uuid, p_pos text)
returns void language plpgsql as $$
begin
  insert into public.team_players (team_id, player_id, jersey_number, position) values (p_team, p_player, p_jersey, p_pos);
  insert into seed_pl values (p_key, p_jersey, p_player);
end $$;

-- Alineación para lineup_save. Titulares «dorsal:POS» o «dorsal:POS:x:y» (lugar en la cancha);
-- suplentes «dorsal,dorsal».
create or replace function pg_temp.lineup(p_team text, p_starters text, p_subs text) returns jsonb language plpgsql as $$
declare r jsonb := '[]'::jsonb; part text; f text[];
begin
  foreach part in array string_to_array(p_starters, ',') loop
    f := string_to_array(trim(part), ':');
    r := r || jsonb_build_object(
      'player_id', pg_temp.pl(p_team, f[1]::int), 'is_starter', true, 'field_position', f[2],
      'jersey_number', f[1]::int,
      'slot_x', case when array_length(f, 1) >= 4 then f[3]::numeric end,
      'slot_y', case when array_length(f, 1) >= 4 then f[4]::numeric end);
  end loop;
  if coalesce(p_subs, '') <> '' then
    foreach part in array string_to_array(p_subs, ',') loop
      r := r || jsonb_build_object('player_id', pg_temp.pl(p_team, trim(part)::int), 'is_starter', false,
                                   'jersey_number', trim(part)::int);
    end loop;
  end if;
  return r;
end $$;

-- Convocatoria de un equipo: todos confirmados salvo los dorsales indicados
create or replace function pg_temp.callups(p_match uuid, p_team_key text, p_team uuid,
                                           p_pending int[] default '{}', p_declined int[] default '{}')
returns void language plpgsql as $$
begin
  insert into public.match_callups (match_id, team_id, player_id, status)
  select p_match, p_team, s.id,
         case when s.jersey = any (p_declined) then 'declined'
              when s.jersey = any (p_pending) then 'pending' else 'confirmed' end
  from seed_pl s where s.team = p_team_key;
end $$;

-- Registrar un evento del partido (como lo hace la app del DT u operador)
create or replace function pg_temp.ev(p_match uuid, p_code text, p_period int, p_minute int, p_side text,
                                      p_player uuid default null, p_payload jsonb default '{}',
                                      p_external uuid default null) returns uuid language plpgsql as $$
declare v_id uuid;
begin
  insert into public.match_events (match_id, client_event_id, event_type_id, period, minute, team_side,
                                   player_id, external_player_id, payload)
  values (p_match, gen_random_uuid(),
          (select id from public.event_types where academy_id is null and code = p_code),
          p_period, p_minute, p_side, p_player, p_external, coalesce(p_payload, '{}'::jsonb))
  returning id into v_id;
  return v_id;
end $$;

-- Gol con asistencia opcional (mismo equipo)
create or replace function pg_temp.goal(p_match uuid, p_period int, p_minute int, p_side text, p_team text,
                                        p_scorer int, p_assist int default null) returns void language plpgsql as $$
begin
  perform pg_temp.ev(p_match, 'goal', p_period, p_minute, p_side, pg_temp.pl(p_team, p_scorer),
    jsonb_strip_nulls(jsonb_build_object('own_goal', false,
      'assist_player_id', case when p_assist is not null then pg_temp.pl(p_team, p_assist) end)));
end $$;

-- Cambio: sale «p_out», entra «p_in»
create or replace function pg_temp.sub(p_match uuid, p_period int, p_minute int, p_side text, p_team text,
                                       p_out int, p_in int) returns void language plpgsql as $$
begin
  perform pg_temp.ev(p_match, 'substitution', p_period, p_minute, p_side, pg_temp.pl(p_team, p_out),
    jsonb_build_object('player_out_id', pg_temp.pl(p_team, p_out), 'player_in_id', pg_temp.pl(p_team, p_in)));
end $$;

-- Cierra un periodo como si hubieran pasado «p_minutes» minutos de juego (el reloj lo calcula el servidor)
create or replace function pg_temp.play(p_match uuid, p_minutes int, p_action text) returns void language plpgsql as $$
begin
  update public.matches set period_started_at = now() - make_interval(mins => p_minutes) where id = p_match;
  perform public.change_match_state(p_match, p_action);
end $$;

-- ---------------------------------------------------------------------
-- Carga
-- ---------------------------------------------------------------------
do $seed$
declare
  -- Cuentas
  u_super uuid; u_admh uuid; u_admt uuid; u_dth uuid; u_dt2 uuid; u_dtt uuid;
  u_ana uuid; u_luis uuid; u_mateo uuid; u_sofia uuid; u_fan uuid;
  -- Academias y equipos
  a_h uuid; a_t uuid; t_h15 uuid; t_t15 uuid; t_h12 uuid; t_t12 uuid;
  -- Jugadores con nombre propio en las pruebas
  p_santiago uuid; p_lia uuid; p_daniel uuid; p_rafael uuid; p_mateo uuid;
  -- Partidos, desafíos y otros
  m1 uuid; m2 uuid; m3 uuid; m4 uuid; m5 uuid; m6 uuid; m7 uuid; m8 uuid;
  c uuid; v_code text; v_id uuid; v_pay uuid; v_tr uuid;
  x1 uuid; x10 uuid;
  oct date := (date_trunc('month', now() at time zone 'America/Lima') - interval '1 month')::date;
  nov date := date_trunc('month', now() at time zone 'America/Lima')::date;
  slots11 jsonb := '[{"pos":"POR","x":0.5,"y":0.07},{"pos":"DEF","x":0.14,"y":0.42},{"pos":"DEF","x":0.38,"y":0.34},
                     {"pos":"DEF","x":0.62,"y":0.34},{"pos":"DEF","x":0.86,"y":0.42},{"pos":"MED","x":0.28,"y":0.62},
                     {"pos":"MED","x":0.5,"y":0.56},{"pos":"MED","x":0.72,"y":0.62},{"pos":"DEL","x":0.18,"y":0.86},
                     {"pos":"DEL","x":0.5,"y":0.92},{"pos":"DEL","x":0.82,"y":0.86}]';
begin
  if exists (select 1 from auth.users where email = 'superadmin@sportpro.test') then
    raise notice 'Los datos de prueba ya estaban cargados: no se hizo nada.';
    return;
  end if;
  -- Tabla de trabajo (temporal: solo existe durante esta carga)
  drop table if exists pg_temp.seed_pl;
  create temp table seed_pl (team text, jersey int, id uuid, primary key (team, jersey));

  -- =================== 1. Cuentas, academias e invitaciones ===================
  u_super := pg_temp.new_user('superadmin@sportpro.test', '{"full_name":"Carlos Superadmin","birth_date":"1985-03-12"}');
  insert into public.platform_admins (user_id) values (u_super);

  -- El superadmin invita a los dos administradores; al registrarse, cada uno crea su academia
  perform pg_temp.act(u_super);
  v_code := public.create_admin_invitation()->>'code';
  u_admh := pg_temp.new_user('adm.halcones@sportpro.test', jsonb_build_object(
    'full_name', 'Andrea Morales', 'birth_date', '1982-08-20', 'invite_code', v_code,
    'academy_name', 'Academia Los Halcones', 'academy_city', 'Lima'));
  perform pg_temp.act(u_super);
  v_code := public.create_admin_invitation()->>'code';
  u_admt := pg_temp.new_user('adm.tigres@sportpro.test', jsonb_build_object(
    'full_name', 'Ricardo Salinas', 'birth_date', '1979-11-02', 'invite_code', v_code,
    'academy_name', 'Club Deportivo Tigres', 'academy_city', 'Lima'));
  -- Una invitación de administrador sin usar (para ver la lista de invitaciones)
  perform pg_temp.act(u_super);
  perform public.create_admin_invitation();

  select academy_id into a_h from public.academy_memberships where user_id = u_admh and role = 'adm';
  select academy_id into a_t from public.academy_memberships where user_id = u_admt and role = 'adm';

  -- Cada administrador invita a sus entrenadores
  perform pg_temp.act(u_admh);
  v_code := public.create_coach_invitation(a_h)->>'code';
  u_dth := pg_temp.new_user('dt.halcones@sportpro.test', jsonb_build_object(
    'full_name', 'Jorge Paredes', 'birth_date', '1988-05-14', 'invite_code', v_code));
  perform pg_temp.act(u_admh);
  v_code := public.create_coach_invitation(a_h)->>'code';
  u_dt2 := pg_temp.new_user('dt2.halcones@sportpro.test', jsonb_build_object(
    'full_name', 'Patricia Quispe', 'birth_date', '1990-01-25', 'invite_code', v_code));
  perform pg_temp.act(u_admt);
  v_code := public.create_coach_invitation(a_t)->>'code';
  u_dtt := pg_temp.new_user('dt.tigres@sportpro.test', jsonb_build_object(
    'full_name', 'Miguel Torres', 'birth_date', '1986-09-30', 'invite_code', v_code));
  perform pg_temp.act(u_admt);
  perform public.create_coach_invitation(a_t);   -- invitación de DT pendiente

  -- Familias, jugadores con cuenta y un seguidor
  u_ana   := pg_temp.new_user('apoderado.ana@sportpro.test',  '{"full_name":"Ana Pérez","birth_date":"1987-04-09","account_type":"pad"}');
  u_luis  := pg_temp.new_user('apoderado.luis@sportpro.test', '{"full_name":"Luis Gómez","birth_date":"1984-12-03","account_type":"pad"}');
  u_mateo := pg_temp.new_user('jugador.mateo@sportpro.test',  '{"first_name":"Mateo","last_name":"Rojas","birth_date":"2012-04-18","account_type":"jug"}');
  u_sofia := pg_temp.new_user('jugador.sofia@sportpro.test',  '{"first_name":"Sofía","last_name":"Mendoza","birth_date":"2012-07-02","account_type":"jug"}');
  u_fan   := pg_temp.new_user('hincha@sportpro.test',         '{"full_name":"Fabio Hincha","birth_date":"1995-06-17","account_type":"pad"}');
  select id into p_mateo from public.players where user_id = u_mateo;

  -- Mateo (14) pide a Ana ser su apoderada y ella acepta; Sofía (14) espera la respuesta de Luis
  perform pg_temp.act(u_mateo);
  v_id := (public.request_guardian_link((select guardian_code from public.profiles where id = u_ana))->>'link_id')::uuid;
  perform pg_temp.act(u_ana);
  perform public.respond_guardian_link(v_id, true);
  perform pg_temp.act(u_sofia);
  perform public.request_guardian_link((select guardian_code from public.profiles where id = u_luis));

  -- Hijos menores de 13 creados por sus apoderados
  perform pg_temp.act(u_ana);
  p_santiago := public.create_child_player('Santiago', 'Pérez', date '2015-02-10', 'Mediocampista');
  p_lia      := public.create_child_player('Lía', 'Pérez', date '2015-08-20', 'Delantera');
  perform pg_temp.act(u_luis);
  p_daniel := public.create_child_player('Daniel', 'Gómez', date '2014-09-15', 'Mediocampista');
  p_rafael := public.create_child_player('Rafael', 'Gómez', date '2015-05-11', 'Defensa');
  insert into public.player_private_data (player_id, height_cm, weight_kg, emergency_contact_name, emergency_contact_phone)
  values (p_santiago, 142, 36.5, 'Ana Pérez', '999 111 222'), (p_daniel, 150, 41, 'Luis Gómez', '999 333 444');

  -- =================== 2. Equipos y planteles ===================
  perform pg_temp.act(u_admh);
  insert into public.teams (academy_id, name, category, max_age, default_format_players, half_minutes)
  values (a_h, 'Halcones Sub-15', 'Sub-15', 15, 11, 30) returning id into t_h15;
  insert into public.teams (academy_id, name, category, max_age, default_format_players, half_minutes)
  values (a_h, 'Halcones Sub-12', 'Sub-12', 12, 7, 25) returning id into t_h12;
  insert into public.team_staff (team_id, user_id, staff_role) values (t_h15, u_dth, 'dt'), (t_h12, u_dt2, 'dt');
  perform pg_temp.act(u_admt);
  insert into public.teams (academy_id, name, category, max_age, default_format_players, half_minutes)
  values (a_t, 'Tigres Sub-15', 'Sub-15', 15, 11, 30) returning id into t_t15;
  insert into public.teams (academy_id, name, category, max_age, default_format_players, half_minutes)
  values (a_t, 'Tigres Sub-12', 'Sub-12', 12, 7, 25) returning id into t_t12;
  insert into public.team_staff (team_id, user_id, staff_role) values (t_t15, u_dtt, 'dt'), (t_t12, u_dtt, 'dt');

  -- Halcones Sub-15 (fútbol 11). El 10 es Mateo, que tiene cuenta propia.
  perform pg_temp.act(u_dth);
  perform pg_temp.add_player('H15', t_h15, 1,  'Sebastián', 'Quispe',  date '2011-12-05', 'POR');
  perform pg_temp.add_player('H15', t_h15, 2,  'Diego',     'Huamán',  date '2011-12-20', 'DEF');
  perform pg_temp.add_player('H15', t_h15, 3,  'Rodrigo',   'Flores',  date '2012-01-08', 'DEF');
  perform pg_temp.add_player('H15', t_h15, 4,  'Andrés',    'Vargas',  date '2012-01-30', 'DEF');
  perform pg_temp.add_player('H15', t_h15, 5,  'Nicolás',   'Castillo',date '2012-02-14', 'DEF');
  perform pg_temp.add_player('H15', t_h15, 6,  'Joaquín',   'Mendoza', date '2012-03-03', 'MED');
  perform pg_temp.add_player('H15', t_h15, 7,  'Thiago',    'Paredes', date '2012-03-21', 'DEL');
  perform pg_temp.add_player('H15', t_h15, 8,  'Gabriel',   'Salas',   date '2012-04-02', 'MED');
  perform pg_temp.add_player('H15', t_h15, 9,  'Bruno',     'Ramírez', date '2012-05-11', 'DEL');
  perform pg_temp.enroll('H15', t_h15, 10, p_mateo, 'MED');
  perform pg_temp.add_player('H15', t_h15, 11, 'Adrián',    'Chávez',  date '2012-06-07', 'DEL');
  perform pg_temp.add_player('H15', t_h15, 12, 'Lucas',     'Torres',  date '2012-06-29', 'POR');
  perform pg_temp.add_player('H15', t_h15, 13, 'Fabián',    'Ríos',    date '2012-07-15', 'DEF');
  perform pg_temp.add_player('H15', t_h15, 14, 'Iker',      'Navarro', date '2012-08-09', 'MED');
  perform pg_temp.add_player('H15', t_h15, 15, 'Samuel',    'Cáceres', date '2012-09-01', 'MED');
  perform pg_temp.add_player('H15', t_h15, 16, 'Emiliano',  'Soto',    date '2012-10-12', 'DEL');

  -- Tigres Sub-15 (fútbol 11)
  perform pg_temp.act(u_dtt);
  perform pg_temp.add_player('T15', t_t15, 1,  'Franco',    'Gutiérrez', date '2011-12-11', 'POR');
  perform pg_temp.add_player('T15', t_t15, 2,  'Matías',    'León',      date '2011-12-28', 'DEF');
  perform pg_temp.add_player('T15', t_t15, 3,  'Renato',    'Aguilar',   date '2012-01-17', 'DEF');
  perform pg_temp.add_player('T15', t_t15, 4,  'Gonzalo',   'Paz',       date '2012-02-05', 'DEF');
  perform pg_temp.add_player('T15', t_t15, 5,  'Alonso',    'Benítez',   date '2012-02-26', 'DEF');
  perform pg_temp.add_player('T15', t_t15, 6,  'Valentín',  'Espinoza',  date '2012-03-14', 'MED');
  perform pg_temp.add_player('T15', t_t15, 7,  'Daniel',    'Herrera',   date '2012-04-06', 'DEL');
  perform pg_temp.add_player('T15', t_t15, 8,  'Santiago',  'Medina',    date '2012-04-25', 'MED');
  perform pg_temp.add_player('T15', t_t15, 9,  'Martín',    'Zapata',    date '2012-05-19', 'DEL');
  perform pg_temp.add_player('T15', t_t15, 10, 'Leonardo',  'Cárdenas',  date '2012-06-02', 'MED');
  perform pg_temp.add_player('T15', t_t15, 11, 'Ian',       'Villanueva',date '2012-06-23', 'DEL');
  perform pg_temp.add_player('T15', t_t15, 12, 'Hugo',      'Palacios',  date '2012-07-10', 'POR');
  perform pg_temp.add_player('T15', t_t15, 13, 'Marcelo',   'Ortega',    date '2012-08-01', 'DEF');
  perform pg_temp.add_player('T15', t_t15, 14, 'Tomás',     'Delgado',   date '2012-08-27', 'MED');
  perform pg_temp.add_player('T15', t_t15, 15, 'Esteban',   'Guerrero',  date '2012-09-18', 'MED');
  perform pg_temp.add_player('T15', t_t15, 16, 'Josué',     'Rivas',     date '2012-10-30', 'DEL');

  -- Halcones Sub-12 (fútbol 7). El 5 y el 11 son los hijos de Ana.
  perform pg_temp.act(u_dt2);
  perform pg_temp.add_player('H12', t_h12, 1,  'Benjamín', 'Cruz',   date '2014-06-04', 'POR');
  perform pg_temp.add_player('H12', t_h12, 2,  'Lucas',    'Vega',   date '2014-07-19', 'DEF');
  perform pg_temp.add_player('H12', t_h12, 3,  'Elías',    'Campos', date '2014-08-30', 'DEF');
  perform pg_temp.add_player('H12', t_h12, 4,  'Dylan',    'Ramos',  date '2014-10-12', 'MED');
  perform pg_temp.enroll('H12', t_h12, 5, p_santiago, 'MED');
  perform pg_temp.add_player('H12', t_h12, 6,  'Facundo',  'Luna',   date '2014-12-01', 'MED');
  perform pg_temp.add_player('H12', t_h12, 8,  'Gael',     'Ortiz',  date '2015-01-22', 'DEF');
  perform pg_temp.add_player('H12', t_h12, 9,  'Joaquín',  'Silva',  date '2015-03-15', 'DEL');
  perform pg_temp.enroll('H12', t_h12, 11, p_lia, 'DEL');
  perform pg_temp.add_player('H12', t_h12, 12, 'Ángel',    'Rivera', date '2015-04-28', 'POR');

  -- Tigres Sub-12 (fútbol 7). El 7 es Daniel, hijo de Luis.
  perform pg_temp.act(u_dtt);
  perform pg_temp.add_player('T12', t_t12, 1,  'Mauricio', 'Salazar', date '2014-06-18', 'POR');
  perform pg_temp.add_player('T12', t_t12, 2,  'Piero',    'Núñez',   date '2014-07-27', 'DEF');
  perform pg_temp.add_player('T12', t_t12, 3,  'Cristian', 'Mora',    date '2014-08-14', 'DEF');
  perform pg_temp.add_player('T12', t_t12, 4,  'Renzo',    'Valdez',  date '2014-10-03', 'MED');
  perform pg_temp.add_player('T12', t_t12, 6,  'Aarón',    'Ibarra',  date '2014-11-20', 'MED');
  perform pg_temp.enroll('T12', t_t12, 7, p_daniel, 'MED');
  perform pg_temp.add_player('T12', t_t12, 8,  'Jhon',     'Peña',    date '2015-01-09', 'DEF');
  perform pg_temp.add_player('T12', t_t12, 9,  'Kevin',    'Castro',  date '2015-02-21', 'DEL');
  perform pg_temp.add_player('T12', t_t12, 11, 'Bastián',  'Lozano',  date '2015-04-04', 'DEL');
  perform pg_temp.add_player('T12', t_t12, 12, 'Saúl',     'Montes',  date '2015-05-30', 'POR');

  -- Las familias y el jugador con cuenta son miembros de su academia
  insert into public.academy_memberships (academy_id, user_id, role) values
    (a_h, u_ana, 'pad'), (a_h, u_mateo, 'jug'), (a_t, u_luis, 'pad');
  -- Seguidores
  insert into public.follows (user_id, academy_id) values (u_fan, a_h);
  insert into public.follows (user_id, team_id) values (u_fan, t_t15), (u_luis, t_h12);

  -- Formaciones propias de cada entrenador (solo las ve quien las guardó)
  perform pg_temp.act(u_dth);
  perform public.coach_formation_save(11, 'Presión alta', '4-3-3', slots11);
  perform pg_temp.act(u_dtt);
  perform public.coach_formation_save(11, 'Bloque bajo', '5-3-2',
    '[{"pos":"POR","x":0.5,"y":0.06},{"pos":"DEF","x":0.1,"y":0.3},{"pos":"DEF","x":0.3,"y":0.22},{"pos":"DEF","x":0.5,"y":0.2},
      {"pos":"DEF","x":0.7,"y":0.22},{"pos":"DEF","x":0.9,"y":0.3},{"pos":"MED","x":0.25,"y":0.5},{"pos":"MED","x":0.5,"y":0.46},
      {"pos":"MED","x":0.75,"y":0.5},{"pos":"DEL","x":0.35,"y":0.8},{"pos":"DEL","x":0.65,"y":0.8}]');
  perform pg_temp.act(u_dt2);
  perform public.coach_formation_save(7, 'Lateral ofensivo', '2-3-1',
    '[{"pos":"POR","x":0.5,"y":0.07},{"pos":"DEF","x":0.25,"y":0.3},{"pos":"DEF","x":0.8,"y":0.5},{"pos":"MED","x":0.2,"y":0.6},
      {"pos":"MED","x":0.5,"y":0.55},{"pos":"MED","x":0.75,"y":0.7},{"pos":"DEL","x":0.5,"y":0.88}]');

  -- =================== 3. Partido 1 · FINALIZADO hace 7 días (desafío, fútbol 11) ===================
  -- Halcones 3-2 Tigres. MVP: el 9 de Halcones (2 goles). Con asistencias, tarjetas, roja y cambios.
  perform pg_temp.act(u_dth);
  c := public.challenge_create(t_h15, t_t15, 11, 30, now() + interval '2 hours', 'Complejo Los Halcones, Surco', 'public');
  perform pg_temp.act(u_dtt);
  m1 := public.challenge_respond(c, true, null);
  perform pg_temp.act(u_dth);
  perform pg_temp.callups(m1, 'H15', t_h15);
  perform public.lineup_save(m1, 'home', '4-3-3',
    pg_temp.lineup('H15', '1:POR,2:DEF,3:DEF,4:DEF,5:DEF,6:MED,8:MED,10:MED,7:DEL,9:DEL,11:DEL', '12,13,14,15,16'));
  perform public.lineup_submit(m1, 'home');
  perform pg_temp.act(u_dtt);
  perform pg_temp.callups(m1, 'T15', t_t15);
  perform public.lineup_save(m1, 'away', '4-4-2',
    pg_temp.lineup('T15', '1:POR,2:DEF,3:DEF,4:DEF,5:DEF,6:MED,8:MED,10:MED,7:MED,9:DEL,11:DEL', '12,13,14,15,16'));
  perform public.lineup_submit(m1, 'away');
  perform pg_temp.act(null);
  perform public._lineups_lock(m1, true);
  perform pg_temp.act(u_dth);
  perform public.change_match_state(m1, 'start');
  perform pg_temp.ev(m1, 'penalty', 1, 12, 'home', pg_temp.pl('H15', 9), '{"result":"converted"}');
  perform pg_temp.goal(m1, 1, 20, 'away', 'T15', 10, 8);
  perform pg_temp.ev(m1, 'yellow_card', 1, 25, 'home', pg_temp.pl('H15', 5));
  perform pg_temp.ev(m1, 'corner', 1, 27, 'away', pg_temp.pl('T15', 7));
  perform pg_temp.ev(m1, 'yellow_card', 1, 33, 'away', pg_temp.pl('T15', 4));
  perform pg_temp.goal(m1, 1, 38, 'home', 'H15', 7, 8);
  perform pg_temp.play(m1, 30, 'halftime');
  perform public.change_match_state(m1, 'second_half');
  perform pg_temp.ev(m1, 'save', 2, 41, 'home', pg_temp.pl('H15', 1));
  perform pg_temp.goal(m1, 2, 55, 'home', 'H15', 9, 10);
  perform pg_temp.sub(m1, 2, 60, 'home', 'H15', 7, 14);
  perform pg_temp.ev(m1, 'red_card', 2, 64, 'away', pg_temp.pl('T15', 6));
  perform pg_temp.sub(m1, 2, 65, 'away', 'T15', 8, 15);
  perform pg_temp.goal(m1, 2, 70, 'away', 'T15', 9, 2);
  perform pg_temp.sub(m1, 2, 75, 'home', 'H15', 11, 16);
  perform pg_temp.ev(m1, 'foul', 2, 82, 'away', pg_temp.pl('T15', 3));
  perform pg_temp.play(m1, 30, 'finish');
  -- Resumen con IA aprobado y publicado en Inicio (público)
  perform public.upsert_ai_summary(m1,
    'Los Halcones Sub-15 se impusieron 3-2 a los Tigres en un partido intenso en Surco. Abrieron el marcador de penal y, '
    || 'tras el empate visitante, volvieron a adelantarse antes del descanso. En el complemento ampliaron la ventaja y '
    || 'resistieron el descuento final ante un rival que terminó con diez jugadores.', 'gemini-2.5-flash');
  perform public.approve_summary(m1, 'public');

  -- =================== 4. Partido 2 · FINALIZADO hace 3 días (desafío Sub-12, fútbol 7) ===================
  -- Halcones 2-2 Tigres. Cuatro jugadores con 1 gol cada uno: NO hay MVP (empate).
  perform pg_temp.act(u_dt2);
  c := public.challenge_create(t_h12, t_t12, 7, 25, now() + interval '2 hours', 'Complejo Los Halcones, Surco', 'followers');
  perform pg_temp.act(u_dtt);
  m2 := public.challenge_respond(c, true, null);
  perform pg_temp.act(u_dt2);
  perform pg_temp.callups(m2, 'H12', t_h12);
  perform public.lineup_save(m2, 'home', '2-3-1', pg_temp.lineup('H12', '1:POR,2:DEF,3:DEF,4:MED,5:MED,6:MED,9:DEL', '8,11,12'));
  perform public.lineup_submit(m2, 'home');
  perform pg_temp.act(u_dtt);
  perform pg_temp.callups(m2, 'T12', t_t12);
  perform public.lineup_save(m2, 'away', '2-3-1', pg_temp.lineup('T12', '1:POR,2:DEF,3:DEF,4:MED,7:MED,6:MED,9:DEL', '8,11,12'));
  perform public.lineup_submit(m2, 'away');
  perform pg_temp.act(null);
  perform public._lineups_lock(m2, true);
  perform pg_temp.act(u_dt2);
  perform public.change_match_state(m2, 'start');
  perform pg_temp.goal(m2, 1, 8, 'home', 'H12', 9, 5);
  perform pg_temp.goal(m2, 1, 15, 'away', 'T12', 9, 7);
  perform pg_temp.ev(m2, 'yellow_card', 1, 21, 'away', pg_temp.pl('T12', 3));
  perform pg_temp.play(m2, 25, 'halftime');
  perform public.change_match_state(m2, 'second_half');
  perform pg_temp.sub(m2, 2, 30, 'home', 'H12', 6, 11);
  perform pg_temp.sub(m2, 2, 35, 'away', 'T12', 4, 11);
  perform pg_temp.goal(m2, 2, 40, 'home', 'H12', 11, 9);
  perform pg_temp.goal(m2, 2, 45, 'away', 'T12', 7);
  perform pg_temp.play(m2, 25, 'finish');
  perform public.upsert_ai_summary(m2,
    'Empate 2-2 entre Halcones y Tigres Sub-12 en un partido parejo de principio a fin. Cada equipo respondió a los goles '
    || 'del rival y el resultado reflejó lo que se vio en la cancha.', 'gemini-2.5-flash');
  perform public.approve_summary(m2, 'academy');

  -- =================== 5. Partido 3 · FINALIZADO hace 10 días contra rival EXTERNO, SIN resumen ===================
  -- Sirve para probar «Resumen IA» desde la app. Halcones 3-1 Escuela San Borja.
  perform pg_temp.act(u_dt2);
  insert into public.matches (home_team_id, away_external_name, format_players, half_minutes, visibility, venue, scheduled_at)
  values (t_h12, 'Escuela San Borja', 7, 25, 'followers', 'Complejo Los Halcones, Surco', now()) returning id into m3;
  insert into public.match_external_players (match_id, name, jersey_number, is_starter, field_position) values
    (m3, 'Arquero San Borja', 1, true, 'POR'), (m3, 'Defensa 2', 2, true, 'DEF'), (m3, 'Defensa 3', 3, true, 'DEF'),
    (m3, 'Medio 6', 6, true, 'MED'), (m3, 'Medio 8', 8, true, 'MED'), (m3, 'Volante 7', 7, true, 'MED'),
    (m3, 'Delantero 10', 10, true, 'DEL'), (m3, 'Suplente 12', 12, false, 'POR'), (m3, 'Suplente 14', 14, false, 'MED');
  select id into x10 from public.match_external_players where match_id = m3 and jersey_number = 10;
  select id into x1  from public.match_external_players where match_id = m3 and jersey_number = 1;
  perform pg_temp.callups(m3, 'H12', t_h12);
  perform public.lineup_save(m3, 'home', '2-3-1', pg_temp.lineup('H12', '1:POR,2:DEF,3:DEF,4:MED,5:MED,6:MED,9:DEL', '8,11,12'));
  perform public.change_match_state(m3, 'start');
  perform pg_temp.goal(m3, 1, 5, 'home', 'H12', 9);
  perform pg_temp.ev(m3, 'goal', 1, 19, 'away', null, '{"own_goal":false}', x10);
  perform pg_temp.play(m3, 25, 'halftime');
  perform public.change_match_state(m3, 'second_half');
  perform pg_temp.goal(m3, 2, 33, 'home', 'H12', 4, 9);
  perform pg_temp.ev(m3, 'save', 2, 38, 'away', null, '{}', x1);
  perform pg_temp.goal(m3, 2, 47, 'home', 'H12', 9, 5);
  perform pg_temp.play(m3, 25, 'finish');

  -- =================== 6. Partido 4 · EN ENTRETIEMPO (desafío, Tigres local) ===================
  -- Tigres 2-1 Halcones al descanso. Halcones juega con una disposición propia (lateral adelantado).
  -- Para seguirlo: el DT de Tigres pulsa «Iniciar 2.º tiempo» y registra eventos.
  perform pg_temp.act(u_dtt);
  c := public.challenge_create(t_t15, t_h15, 11, 30, now() + interval '2 hours', 'Estadio Municipal de San Miguel', 'public');
  perform pg_temp.act(u_dth);
  m4 := public.challenge_respond(c, true, null);
  perform pg_temp.callups(m4, 'H15', t_h15);
  perform public.lineup_save(m4, 'away', '4-2-3-1', pg_temp.lineup('H15',
    '1:POR:0.5:0.07,2:DEF:0.12:0.46,3:DEF:0.38:0.3,4:DEF:0.62:0.3,5:DEF:0.88:0.34,6:MED:0.35:0.5,8:MED:0.65:0.5,'
    || '7:MED:0.18:0.7,10:MED:0.5:0.68,11:MED:0.82:0.7,9:DEL:0.5:0.9', '12,13,14,15,16'));
  perform public.lineup_submit(m4, 'away');
  perform pg_temp.act(u_dtt);
  perform pg_temp.callups(m4, 'T15', t_t15);
  perform public.lineup_save(m4, 'home', '4-3-3',
    pg_temp.lineup('T15', '1:POR,2:DEF,3:DEF,4:DEF,5:DEF,6:MED,8:MED,10:MED,7:DEL,9:DEL,11:DEL', '12,13,14,15,16'));
  perform public.lineup_submit(m4, 'home');
  perform pg_temp.act(null);
  perform public._lineups_lock(m4, true);
  perform pg_temp.act(u_dtt);
  perform public.change_match_state(m4, 'start');
  perform pg_temp.goal(m4, 1, 10, 'home', 'T15', 9, 10);
  perform pg_temp.ev(m4, 'yellow_card', 1, 17, 'away', pg_temp.pl('H15', 4));
  perform pg_temp.goal(m4, 1, 22, 'away', 'H15', 9, 10);
  perform pg_temp.goal(m4, 1, 28, 'home', 'T15', 9, 7);
  perform pg_temp.play(m4, 30, 'halftime');

  -- =================== 7. Partido 5 · LISTO PARA INICIAR (desafío Sub-12, alineaciones confirmadas) ===================
  -- Hay QR del árbitro disponible. La operadora es la administradora de Halcones.
  perform pg_temp.act(u_dt2);
  c := public.challenge_create(t_h12, t_t12, 7, 25, now() + interval '2 hours', 'Complejo Los Halcones, Surco', 'public');
  perform pg_temp.act(u_dtt);
  m5 := public.challenge_respond(c, true, null);
  perform pg_temp.act(u_dt2);
  perform pg_temp.callups(m5, 'H12', t_h12);
  perform public.lineup_save(m5, 'home', '2-3-1', pg_temp.lineup('H12', '1:POR,2:DEF,3:DEF,4:MED,5:MED,6:MED,9:DEL', '8,11,12'));
  perform public.lineup_submit(m5, 'home');
  perform pg_temp.act(u_dtt);
  perform pg_temp.callups(m5, 'T12', t_t12);
  perform public.lineup_save(m5, 'away', '2-3-1', pg_temp.lineup('T12',
    '1:POR:0.5:0.07,2:DEF:0.3:0.3,3:DEF:0.7:0.3,4:MED:0.2:0.58,7:MED:0.5:0.5,6:MED:0.8:0.58,9:DEL:0.5:0.86', '8,11,12'));
  perform public.lineup_submit(m5, 'away');
  perform pg_temp.act(null);
  perform public._lineups_lock(m5, true);
  perform pg_temp.act(u_dt2);
  perform public.set_match_operator(m5, u_admh);

  -- =================== 8. Partido 6 · ALINEACIÓN PENDIENTE (desafío Sub-15 en 3 días) ===================
  -- Halcones ya envió la suya (formación propia «Presión alta»); Tigres todavía no armó nada.
  -- Mateo (10) no respondió la convocatoria; el 13 y el 16 avisaron que no asisten.
  perform pg_temp.act(u_dth);
  c := public.challenge_create(t_h15, t_t15, 11, 30, now() + interval '3 days', 'Complejo Los Halcones, Surco', 'followers');
  perform pg_temp.act(u_dtt);
  m6 := public.challenge_respond(c, true, null);
  perform pg_temp.act(u_dth);
  perform pg_temp.callups(m6, 'H15', t_h15, array[10], array[13, 16]);
  perform public.lineup_save(m6, 'home', '4-3-3', pg_temp.lineup('H15',
    '1:POR:0.5:0.07,2:DEF:0.14:0.42,3:DEF:0.38:0.34,4:DEF:0.62:0.34,5:DEF:0.86:0.42,6:MED:0.28:0.62,8:MED:0.5:0.56,'
    || '14:MED:0.72:0.62,7:DEL:0.18:0.86,9:DEL:0.5:0.92,11:DEL:0.82:0.86', '12,15'));
  perform public.lineup_submit(m6, 'home');
  perform pg_temp.act(u_dtt);
  perform pg_temp.callups(m6, 'T15', t_t15, array[1, 2, 3, 4, 5, 6, 7, 8]);

  -- =================== 9. Partidos amistosos contra rivales externos (sin desafío) ===================
  -- Partido 7: Halcones Sub-15 vs Deportivo Barranco en 2 días, convocatoria a medias y sin alineación
  perform pg_temp.act(u_dth);
  insert into public.matches (home_team_id, away_external_name, format_players, half_minutes, visibility, venue, scheduled_at)
  values (t_h15, 'Deportivo Barranco', 11, 30, 'followers', 'Parque Municipal de Barranco', now() + interval '2 days')
  returning id into m7;
  insert into public.match_external_players (match_id, name, jersey_number, is_starter, field_position)
  select m7, 'Jugador Barranco ' || n, n, n <= 11,
         case when n in (1, 12) then 'POR' when n between 2 and 5 then 'DEF' when n between 6 and 8 or n = 13 then 'MED' else 'DEL' end
  from generate_series(1, 13) n;
  perform pg_temp.callups(m7, 'H15', t_h15, array[10, 11, 12, 13, 14, 15, 16]);
  -- Partido 8: Halcones Sub-12 vs Escuela San Borja (revancha) en 4 días: Ana debe confirmar a sus hijos
  perform pg_temp.act(u_dt2);
  insert into public.matches (home_team_id, away_external_name, format_players, half_minutes, visibility, venue, scheduled_at)
  values (t_h12, 'Escuela San Borja', 7, 25, 'followers', 'Polideportivo de San Borja', now() + interval '4 days')
  returning id into m8;
  perform pg_temp.callups(m8, 'H12', t_h12, array[5, 11, 12], array[]::int[]);

  -- =================== 10. Desafíos sin partido ===================
  -- Pendiente: Tigres Sub-12 desafía a Halcones Sub-12 (la DT de Halcones Sub-12 decide)
  perform pg_temp.act(u_dtt);
  perform public.challenge_create(t_t12, t_h12, 7, 25, now() + interval '6 days', 'Estadio Municipal de San Miguel', 'followers');
  -- Rechazado con motivo
  perform pg_temp.act(u_dth);
  c := public.challenge_create(t_h15, t_t15, 11, 30, now() + interval '8 days', null, 'followers');
  perform pg_temp.act(u_dtt);
  perform public.challenge_respond(c, false, 'Ese fin de semana jugamos un torneo');

  -- =================== 11. Inicio: «me gusta» y comentarios ===================
  perform pg_temp.act(u_fan);
  perform public.toggle_post_like(m1);
  perform public.add_post_comment(m1, '¡Qué partidazo! Bien jugado los dos equipos.');
  perform pg_temp.act(u_ana);
  perform public.toggle_post_like(m1);
  perform public.add_post_comment(m1, 'Felicitaciones a los Halcones, gran remontada.');
  perform public.toggle_post_like(m2);
  perform pg_temp.act(u_luis);
  perform public.toggle_post_like(m1);
  -- Favoritos del seguidor
  perform pg_temp.act(u_fan);
  perform public.toggle_match_favorite(m4);
  perform public.toggle_match_favorite(m5);

  -- =================== 12. Mensualidades (simuladas) ===================
  perform pg_temp.act(u_admh);
  perform public.fee_set_plan(t_h12, 120, 5);
  perform public.fee_set_plan(t_h15, 150, 10);
  perform pg_temp.act(u_dt2);
  perform public.fee_set_player(t_h12, s.id, true) from seed_pl s where s.team = 'H12';
  perform public.fee_generate_charges(t_h12, oct);
  perform public.fee_generate_charges(t_h12, nov);
  perform pg_temp.act(u_dth);
  perform public.fee_set_player(t_h15, s.id, true) from seed_pl s where s.team = 'H15';
  perform public.fee_generate_charges(t_h15, nov);
  -- Los cobros del mes pasado vencieron en su fecha (se generaron hoy: se ajusta para la prueba)
  perform pg_temp.act(null);
  update public.fee_charges ch set due_date = ch.period + (fp.due_day - 1)
    from public.fee_plans fp where fp.team_id = ch.team_id and ch.period = oct;

  -- Ana paga: octubre de Santiago (confirmado), noviembre de Santiago (por revisar),
  -- octubre de Lía (rechazado: queda pendiente y vencido). Benjamín (1) está exonerado en octubre.
  perform pg_temp.act(u_ana);
  v_pay := public.fee_report_payment((select id from public.fee_charges where player_id = p_santiago and period = oct), 'yape', '458712');
  perform pg_temp.act(u_admh);
  perform public.fee_decide_payment(v_pay, true);
  perform pg_temp.act(u_ana);
  perform public.fee_report_payment((select id from public.fee_charges where player_id = p_santiago and period = nov), 'plin', '993120');
  v_pay := public.fee_report_payment((select id from public.fee_charges where player_id = p_lia and period = oct), 'transfer', '00012345');
  perform pg_temp.act(u_admh);
  perform public.fee_decide_payment(v_pay, false, 'El número de operación no coincide con el depósito');
  perform public.fee_waive_charge((select id from public.fee_charges where player_id = pg_temp.pl('H12', 1) and period = oct), 'Beca deportiva');
  -- Tigres Sub-12
  perform pg_temp.act(u_admt);
  perform public.fee_set_plan(t_t12, 100, 15);
  perform pg_temp.act(u_dtt);
  perform public.fee_set_player(t_t12, s.id, true) from seed_pl s where s.team = 'T12';
  perform public.fee_generate_charges(t_t12, nov);

  -- =================== 13. Avisos de pruebas ===================
  perform pg_temp.act(u_admh);
  v_tr := public.tryout_create(a_h, 'Pruebas Sub-12 · temporada 2027', 'Sub-12', 10, 12, array['DEF', 'MED'],
    now() + interval '10 days', 'Complejo Deportivo Los Halcones', 'Santiago de Surco', 15, 'Traer zapatillas, canilleras y agua.');
  perform pg_temp.act(u_luis);
  perform public.tryout_apply(v_tr, p_rafael);
  perform pg_temp.act(u_dtt);
  v_tr := public.tryout_create(a_t, 'Captación de arqueros Sub-15', 'Sub-15', 13, 15, array['POR'],
    now() + interval '7 days', 'Estadio Municipal de San Miguel', 'San Miguel', 5, null);
  perform pg_temp.act(u_ana);
  v_id := public.tryout_apply(v_tr, p_mateo);
  perform pg_temp.act(u_dtt);
  perform public.tryout_decide(v_id, true, null);

  -- =================== 14. Solicitud para unirse a un equipo (pendiente) ===================
  perform pg_temp.act(u_dt2);
  v_code := public.team_join_code(t_h12);
  perform pg_temp.act(u_luis);
  perform public.request_team_join(v_code, p_rafael);

  -- =================== 15. Mover en el tiempo los partidos ya jugados ===================
  -- (como el dueño del proyecto: la fecha acordada de un desafío no la cambia un usuario)
  perform pg_temp.act(null);
  update public.matches set scheduled_at = now() - interval '7 days', started_at = now() - interval '7 days',
         finished_at = now() - interval '7 days' + interval '75 minutes' where id = m1;
  update public.matches set scheduled_at = now() - interval '3 days', started_at = now() - interval '3 days',
         finished_at = now() - interval '3 days' + interval '60 minutes' where id = m2;
  update public.matches set scheduled_at = now() - interval '10 days', started_at = now() - interval '10 days',
         finished_at = now() - interval '10 days' + interval '60 minutes' where id = m3;
  update public.matches set scheduled_at = now() - interval '45 minutes', started_at = now() - interval '45 minutes' where id = m4;
  update public.matches set scheduled_at = now() + interval '2 hours' where id = m5;
  update public.match_summaries set approved_at = now() - interval '7 days' + interval '2 hours' where match_id = m1;
  update public.match_summaries set approved_at = now() - interval '3 days' + interval '2 hours' where match_id = m2;
  -- Los avisos «en vivo» de los partidos ya terminados quedan como leídos
  update public.notifications set read_at = now()
   where kind = 'match_live' and (data->>'match_id')::uuid in (m1, m2, m3);

  drop table seed_pl;
  raise notice 'Datos de prueba cargados. Contraseña de todas las cuentas: 12345678 (ver docs/datos-de-prueba.md).';
end $seed$;
