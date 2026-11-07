-- Prueba de humo de seguridad y flujo de partido. Pensada para correr en una base LIMPIA
-- de pruebas (local / dev), NUNCA en el proyecto con datos reales.
-- Cada bloque imprime OK o lanza una excepción.
\set ON_ERROR_STOP on
\set QUIET on
\pset tuples_only on

create or replace function pg_temp.as_user(u uuid) returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claim.sub', u::text, false);
  execute 'set role authenticated';
end $$;
create or replace function pg_temp.as_su() returns void language plpgsql as $$
begin execute 'reset role'; perform set_config('request.jwt.claim.sub', '', false); end $$;
create or replace function pg_temp.expect_fail(p_sql text, p_label text) returns void language plpgsql as $$
begin
  begin execute p_sql; exception when others then raise notice 'OK  (bloqueado) %: %', p_label, sqlerrm; return; end;
  raise exception 'FALLO: debía bloquearse -> %', p_label;
end $$;
create or replace function pg_temp.expect_eq(p_actual anyelement, p_expected anyelement, p_label text) returns void language plpgsql as $$
begin
  if p_actual is distinct from p_expected then raise exception 'FALLO % : esperado %, obtenido %', p_label, p_expected, p_actual; end if;
  raise notice 'OK  %', p_label;
end $$;

-- ===== Usuarios (como si se registraran desde la app) =====
insert into auth.users (id, email, raw_user_meta_data) values
 ('a0000000-0000-0000-0000-000000000001','super@t.pe','{"full_name":"Aldo Super","birth_date":"1999-05-01"}'),
 ('a0000000-0000-0000-0000-000000000002','adm@t.pe',  '{"full_name":"Ana Admin","birth_date":"1985-03-10"}'),
 ('a0000000-0000-0000-0000-000000000003','dt@t.pe',   '{"full_name":"Diego DT","birth_date":"1980-07-07"}'),
 ('a0000000-0000-0000-0000-000000000004','op@t.pe',   '{"full_name":"Olga Operadora","birth_date":"1990-01-01"}'),
 ('a0000000-0000-0000-0000-000000000005','pad@t.pe',  '{"full_name":"Paola Apoderada","birth_date":"1984-09-09"}'),
 ('a0000000-0000-0000-0000-000000000006','fan@t.pe',  '{"full_name":"Fabio Seguidor","birth_date":"1995-02-02"}'),
 ('a0000000-0000-0000-0000-000000000007','out@t.pe',  '{"full_name":"Orlando Extraño","birth_date":"1992-02-02"}');
select pg_temp.expect_fail($$insert into auth.users (id,email,raw_user_meta_data) values (gen_random_uuid(),'kid@t.pe','{"full_name":"Niño","birth_date":"2018-01-01"}')$$,
  'registro de menor de 13 rechazado');
select pg_temp.expect_eq((select count(*) from public.profiles)::int, 7, '7 perfiles creados por trigger');

-- ===== Superadmin y academia (setup como dueño) =====
insert into public.platform_admins values ('a0000000-0000-0000-0000-000000000001');
select pg_temp.as_user('a0000000-0000-0000-0000-000000000001');
insert into public.academies (id, name, city, status) values ('b0000000-0000-0000-0000-000000000001','Academia Demo','Lima','verified');
insert into public.academy_memberships (academy_id,user_id,role) values
 ('b0000000-0000-0000-0000-000000000001','a0000000-0000-0000-0000-000000000002','adm');
select pg_temp.as_su();
insert into public.academy_memberships (academy_id,user_id,role) values
 ('b0000000-0000-0000-0000-000000000001','a0000000-0000-0000-0000-000000000003','dt'),
 ('b0000000-0000-0000-0000-000000000001','a0000000-0000-0000-0000-000000000004','pad');

-- ADM: crea equipos, asigna DT; NO puede crear otro ADM; extraño no ve datos
select pg_temp.as_user('a0000000-0000-0000-0000-000000000002');
insert into public.teams (id, academy_id, name, category, max_age, default_format_players) values
 ('c0000000-0000-0000-0000-000000000001','b0000000-0000-0000-0000-000000000001','Halcones','Sub-15',15,7),
 ('c0000000-0000-0000-0000-000000000002','b0000000-0000-0000-0000-000000000001','Leones','Sub-15',15,7);
insert into public.team_staff values ('c0000000-0000-0000-0000-000000000001','a0000000-0000-0000-0000-000000000003','dt');
select pg_temp.expect_fail($$insert into public.academy_memberships (academy_id,user_id,role) values ('b0000000-0000-0000-0000-000000000001','a0000000-0000-0000-0000-000000000006','adm')$$,
  'ADM no puede crear otro ADM');
select pg_temp.expect_fail($$insert into public.platform_admins values ('a0000000-0000-0000-0000-000000000002')$$, 'cliente no escribe platform_admins');


-- ===== Apoderada crea perfil de hijo <13 =====
select pg_temp.as_su();
update public.profiles set birth_date = '1984-09-09' where id='a0000000-0000-0000-0000-000000000005';
select pg_temp.as_user('a0000000-0000-0000-0000-000000000005');
select pg_temp.expect_fail($$select public.create_child_player('Teo','Mayor','2005-01-01')$$, 'perfil gestionado exige menor de 13');
do $$ declare v uuid; begin
  v := public.create_child_player('Mateo','Rojas','2016-04-04','Delantero');
  perform set_config('t.child', v::text, false);
end $$;
select pg_temp.expect_eq((select count(*) from public.guardian_player_links where status='approved')::int, 1, 'vínculo apoderado-hijo creado y aprobado');

-- Jugadores de los dos equipos (como superusuario, equivalente al seed)
select pg_temp.as_su();
insert into public.players (id, first_name, last_name, birth_date, position)
select ('d00000' || lpad(i::text, 2, '0') || '-0000-0000-0000-000000000001')::uuid, 'Jugador' || i, 'Halcon' || i, date '2012-06-01', 'MC' from generate_series(1,9) i;
insert into public.players (id, first_name, last_name, birth_date, position)
select ('d00000' || lpad(i::text, 2, '0') || '-0000-0000-0000-000000000002')::uuid, 'Rival' || i, 'Leon' || i, date '2012-06-01', 'MC' from generate_series(1,9) i;
insert into public.team_players (team_id, player_id, jersey_number)
select 'c0000000-0000-0000-0000-000000000001', ('d00000' || lpad(i::text, 2, '0') || '-0000-0000-0000-000000000001')::uuid, i from generate_series(1,9) i;
insert into public.team_players (team_id, player_id, jersey_number)
select 'c0000000-0000-0000-0000-000000000002', ('d00000' || lpad(i::text, 2, '0') || '-0000-0000-0000-000000000002')::uuid, i from generate_series(1,9) i;
insert into public.team_players (team_id, player_id, jersey_number)
values ('c0000000-0000-0000-0000-000000000001', current_setting('t.child')::uuid, 20);
-- edad vs categoría
insert into public.players (id, first_name, last_name, birth_date) values ('d9000000-0000-0000-0000-000000000009','Viejo','Veterano','1990-01-01');
select pg_temp.expect_fail($$insert into public.team_players (team_id, player_id) values ('c0000000-0000-0000-0000-000000000001','d9000000-0000-0000-0000-000000000009')$$, 'adulto no entra a Sub-15');
-- dorsal repetido
select pg_temp.expect_fail($$insert into public.team_players (team_id, player_id, jersey_number) values ('c0000000-0000-0000-0000-000000000001','d9000000-0000-0000-0000-000000000009',1)$$, 'dorsal duplicado / edad');

-- Privacidad: datos físicos
insert into public.player_private_data (player_id, height_cm, weight_kg, phone, emergency_contact_name, emergency_contact_phone)
values (current_setting('t.child')::uuid, 140, 35, '999', 'Paola', '988');
select pg_temp.as_user('a0000000-0000-0000-0000-000000000007');  -- extraño
select pg_temp.expect_eq((select count(*) from public.player_private_data)::int, 0, 'extraño NO ve datos físicos/contactos');
select pg_temp.expect_eq((select count(*) from public.players)::int, 0, 'extraño NO ve jugadores');
select pg_temp.as_user('a0000000-0000-0000-0000-000000000005');  -- apoderada
select pg_temp.expect_eq((select count(*) from public.player_private_data)::int, 1, 'apoderada SÍ ve datos de su hijo');
select pg_temp.as_user('a0000000-0000-0000-0000-000000000003');  -- DT
select pg_temp.expect_eq((select count(*) from public.player_private_data)::int, 1, 'DT del equipo SÍ ve datos');

-- ===== Partido: DT crea, convoca, confirma, alinea, designa operador =====
select pg_temp.as_user('a0000000-0000-0000-0000-000000000003');
insert into public.matches (id, home_team_id, away_team_id, format_players, half_minutes, visibility, venue)
values ('e0000000-0000-0000-0000-000000000001','c0000000-0000-0000-0000-000000000001','c0000000-0000-0000-0000-000000000002', 7, 25, 'followers', 'Cancha 1');
select pg_temp.expect_eq((select academy_id from public.matches where id='e0000000-0000-0000-0000-000000000001'), 'b0000000-0000-0000-0000-000000000001'::uuid, 'academia del partido derivada por trigger');
select pg_temp.expect_fail($$update public.matches set home_score=9 where id='e0000000-0000-0000-0000-000000000001'$$, 'cliente no puede editar marcador');
select pg_temp.expect_fail($$select public.change_match_state('e0000000-0000-0000-0000-000000000001','start')$$, 'no inicia sin alineación');

insert into public.match_callups (match_id, team_id, player_id)
select 'e0000000-0000-0000-0000-000000000001','c0000000-0000-0000-0000-000000000001', player_id from public.team_players where team_id='c0000000-0000-0000-0000-000000000001' and jersey_number <= 9;
-- el DT local NO puede convocar a jugadores del rival
select pg_temp.expect_fail($$insert into public.match_callups (match_id, team_id, player_id) select 'e0000000-0000-0000-0000-000000000001','c0000000-0000-0000-0000-000000000002', player_id from public.team_players where team_id='c0000000-0000-0000-0000-000000000002'$$, 'DT local no convoca jugadores del rival');
select pg_temp.expect_fail($$insert into public.match_lineup_players (match_id, team_side, player_id) values ('e0000000-0000-0000-0000-000000000001','home','d0000001-0000-0000-0000-000000000001')$$, 'no se alinea a un no confirmado');
-- DT local confirma lo suyo; el DT visitante (simulado como superusuario) convoca y confirma lo suyo
update public.match_callups set status='confirmed' where match_id='e0000000-0000-0000-0000-000000000001';
select pg_temp.as_su();
insert into public.match_callups (match_id, team_id, player_id, status)
select 'e0000000-0000-0000-0000-000000000001','c0000000-0000-0000-0000-000000000002', player_id, 'confirmed' from public.team_players where team_id='c0000000-0000-0000-0000-000000000002';
select pg_temp.as_user('a0000000-0000-0000-0000-000000000003');
insert into public.match_lineup_players (match_id, team_side, player_id, is_starter)
select 'e0000000-0000-0000-0000-000000000001','home', player_id, true from public.team_players where team_id='c0000000-0000-0000-0000-000000000001' and jersey_number <= 7;
insert into public.match_lineup_players (match_id, team_side, player_id, is_starter)
select 'e0000000-0000-0000-0000-000000000001','away', player_id, true from public.team_players where team_id='c0000000-0000-0000-0000-000000000002' and jersey_number <= 7;
select public.set_match_operator('e0000000-0000-0000-0000-000000000001','a0000000-0000-0000-0000-000000000004');
select pg_temp.expect_fail($$select public.set_match_operator('e0000000-0000-0000-0000-000000000001','a0000000-0000-0000-0000-000000000007')$$, 'operador debe ser de la academia');

-- ===== Operadora registra el partido =====
select pg_temp.as_user('a0000000-0000-0000-0000-000000000004');
select pg_temp.expect_fail($$insert into public.match_events (match_id, client_event_id, event_type_id, period, minute, team_side) select 'e0000000-0000-0000-0000-000000000001', gen_random_uuid(), id, 1, 5, 'home' from public.event_types where code='goal'$$, 'no se registra antes de iniciar');
select public.change_match_state('e0000000-0000-0000-0000-000000000001','start');
select pg_temp.expect_eq((select status::text from public.matches where id='e0000000-0000-0000-0000-000000000001'), 'live', 'partido en vivo');

-- gol local con jugador, gol local SIN jugador, gol visitante
insert into public.match_events (match_id, client_event_id, event_type_id, period, minute, team_side, player_id)
select 'e0000000-0000-0000-0000-000000000001','f0000000-0000-0000-0000-000000000001', id, 1, 10, 'home', 'd0000009-0000-0000-0000-000000000001' from public.event_types where code='goal';
-- (jugador 9 no está alineado pero sí en el plantel: válido)
insert into public.match_events (match_id, client_event_id, event_type_id, period, minute, team_side)
select 'e0000000-0000-0000-0000-000000000001','f0000000-0000-0000-0000-000000000002', id, 1, 15, 'home' from public.event_types where code='goal';
insert into public.match_events (match_id, client_event_id, event_type_id, period, minute, team_side, player_id)
select 'e0000000-0000-0000-0000-000000000001','f0000000-0000-0000-0000-000000000003', id, 1, 20, 'away', 'd0000003-0000-0000-0000-000000000002' from public.event_types where code='goal';
select pg_temp.expect_eq((select home_score from public.matches where id='e0000000-0000-0000-0000-000000000001')::int, 2, 'marcador local = 2 (con y sin jugador)');
select pg_temp.expect_eq((select away_score from public.matches where id='e0000000-0000-0000-0000-000000000001')::int, 1, 'marcador visitante = 1');
-- idempotencia: reenvío del mismo evento
select pg_temp.expect_fail($$insert into public.match_events (match_id, client_event_id, event_type_id, period, minute, team_side) select 'e0000000-0000-0000-0000-000000000001','f0000000-0000-0000-0000-000000000002', id, 1, 15, 'home' from public.event_types where code='goal'$$, 'reenvío duplicado rechazado (idempotencia)');
-- jugador del equipo equivocado
select pg_temp.expect_fail($$insert into public.match_events (match_id, client_event_id, event_type_id, period, minute, team_side, player_id) select 'e0000000-0000-0000-0000-000000000001', gen_random_uuid(), id, 1, 22, 'home', 'd0000003-0000-0000-0000-000000000002' from public.event_types where code='yellow_card'$$, 'jugador rival en equipo local rechazado');
-- datos obligatorios por tipo: penal sin resultado, cambio sin ids
select pg_temp.expect_fail($$insert into public.match_events (match_id, client_event_id, event_type_id, period, minute, team_side) select 'e0000000-0000-0000-0000-000000000001', gen_random_uuid(), id, 1, 25, 'home' from public.event_types where code='penalty'$$, 'penal sin resultado rechazado');
-- penal convertido cuenta como gol
insert into public.match_events (match_id, client_event_id, event_type_id, period, minute, team_side, player_id, payload)
select 'e0000000-0000-0000-0000-000000000001', gen_random_uuid(), id, 1, 25, 'away', 'd0000004-0000-0000-0000-000000000002', '{"result":"converted"}' from public.event_types where code='penalty';
select pg_temp.expect_eq((select away_score from public.matches where id='e0000000-0000-0000-0000-000000000001')::int, 2, 'penal convertido suma gol visitante');
-- gol en contra (jugador local anota en su arco) suma al visitante
insert into public.match_events (match_id, client_event_id, event_type_id, period, minute, team_side, player_id, payload)
select 'e0000000-0000-0000-0000-000000000001', gen_random_uuid(), id, 1, 28, 'home', 'd0000002-0000-0000-0000-000000000001', '{"own_goal":true}' from public.event_types where code='goal';
select pg_temp.expect_eq((select away_score from public.matches where id='e0000000-0000-0000-0000-000000000001')::int, 3, 'gol en contra suma al rival');
-- sin permisos de UPDATE/DELETE directos
select pg_temp.expect_fail($$update public.match_events set minute=99 where match_id='e0000000-0000-0000-0000-000000000001'$$, 'sin UPDATE directo a eventos');
select pg_temp.expect_fail($$delete from public.match_events where match_id='e0000000-0000-0000-0000-000000000001'$$, 'sin DELETE directo a eventos');

-- CORRECCIÓN: asignar jugador al gol que quedó "sin identificar" + trazabilidad
select public.correct_match_event(
  (select id from public.match_events where client_event_id='f0000000-0000-0000-0000-000000000002'),
  '{"player_id":"d0000005-0000-0000-0000-000000000001","minute":16}'::jsonb, 'Se identificó al anotador');
select pg_temp.expect_eq((select player_id from public.match_events where client_event_id='f0000000-0000-0000-0000-000000000002'), 'd0000005-0000-0000-0000-000000000001'::uuid, 'jugador asignado después');
select pg_temp.expect_fail($$select public.correct_match_event((select id from public.match_events where client_event_id='f0000000-0000-0000-0000-000000000002'), '{"minute":17}'::jsonb, '')$$, 'corrección exige motivo');
-- ANULACIÓN del gol visitante f..03 → marcador baja
select public.void_match_event((select id from public.match_events where client_event_id='f0000000-0000-0000-0000-000000000003'), 'Era fuera de juego');
select pg_temp.expect_eq((select away_score from public.matches where id='e0000000-0000-0000-0000-000000000001')::int, 2, 'anular gol baja el marcador');
select pg_temp.expect_fail($$select public.correct_match_event((select id from public.match_events where client_event_id='f0000000-0000-0000-0000-000000000003'), '{"minute":1}'::jsonb, 'x')$$, 'evento anulado no se edita');

-- Entretiempo y 2.º tiempo
select public.change_match_state('e0000000-0000-0000-0000-000000000001','halftime');
select public.change_match_state('e0000000-0000-0000-0000-000000000001','second_half');
-- Cambio con datos obligatorios
insert into public.match_events (match_id, client_event_id, event_type_id, period, minute, team_side, payload)
select 'e0000000-0000-0000-0000-000000000001', gen_random_uuid(), id, 2, 35, 'home',
  '{"player_out_id":"d0000001-0000-0000-0000-000000000001","player_in_id":"d0000008-0000-0000-0000-000000000001"}' from public.event_types where code='substitution';
-- rival externo no aplica aquí; la operadora NO puede finalizar sin permiso? sí puede
-- Extraño / seguidor
select pg_temp.as_user('a0000000-0000-0000-0000-000000000007');
select pg_temp.expect_eq((select count(*) from public.matches)::int, 0, 'extraño NO ve el partido');
select pg_temp.expect_eq((select count(*) from public.get_match_timeline('e0000000-0000-0000-0000-000000000001'))::int, 0, 'extraño NO ve la cronología');
select pg_temp.expect_fail($$insert into public.match_events (match_id, client_event_id, event_type_id, period, minute, team_side) select 'e0000000-0000-0000-0000-000000000001', gen_random_uuid(), id, 1, 5, 'home' from public.event_types where code='goal'$$, 'extraño no registra eventos');
select pg_temp.as_user('a0000000-0000-0000-0000-000000000006');
insert into public.follows (team_id) values ('c0000000-0000-0000-0000-000000000001');
select pg_temp.expect_eq((select count(*) from public.matches)::int, 1, 'SEGUIDOR ve el partido en vivo');
select pg_temp.expect_eq((select count(*) from public.get_match_timeline('e0000000-0000-0000-0000-000000000001') where status='active')::int, 8, 'seguidor ve la cronología (8 eventos activos)');
select pg_temp.expect_fail($$select * from public.get_player_stats('d0000001-0000-0000-0000-000000000001')$$, 'seguidor NO ve estadísticas detalladas');
-- Seguidor ve menores como "Nombre + inicial"
select pg_temp.expect_eq((select player_name from public.get_match_timeline('e0000000-0000-0000-0000-000000000001') where player_id='d0000005-0000-0000-0000-000000000001'), 'Jugador5 H.', 'menor aparece como Nombre + inicial al público');
select pg_temp.as_user('a0000000-0000-0000-0000-000000000003');
select pg_temp.expect_eq((select player_name from public.get_match_timeline('e0000000-0000-0000-0000-000000000001') where player_id='d0000005-0000-0000-0000-000000000001'), 'Jugador5 Halcon5', 'DT ve el nombre completo');
select pg_temp.expect_eq((select count(*) from public.match_event_revisions)::int, 2, 'revisiones guardadas (corrección + anulación)');

-- ===== Fin del partido + estadísticas + IA =====
select public.change_match_state('e0000000-0000-0000-0000-000000000001','finish');
select pg_temp.expect_eq((select status::text from public.matches where id='e0000000-0000-0000-0000-000000000001'), 'finished', 'partido finalizado');
select pg_temp.expect_eq((select goals from public.get_player_stats('d0000005-0000-0000-0000-000000000001'))::int, 1, 'estadística: gol corregido cuenta al jugador asignado');
select pg_temp.expect_eq((select goals from public.get_player_stats('d0000009-0000-0000-0000-000000000001'))::int, 1, 'estadística: gol del jugador 9');
select pg_temp.expect_eq((select own_goals from public.get_player_stats('d0000002-0000-0000-0000-000000000001'))::int, 1, 'estadística: gol en contra registrado aparte');
select pg_temp.expect_eq((select wins from public.get_team_stats('c0000000-0000-0000-0000-000000000001'))::int, 0, 'estadística de equipo (local 2-2 → empate)');
select pg_temp.expect_eq((select draws from public.get_team_stats('c0000000-0000-0000-0000-000000000001'))::int, 1, 'estadística de equipo: empate');
select pg_temp.expect_eq(public.can_generate_summary('e0000000-0000-0000-0000-000000000001'), true, 'DT puede generar resumen');
-- padre ve estadísticas de su hijo; DT también
select pg_temp.as_user('a0000000-0000-0000-0000-000000000005');
select pg_temp.expect_eq((select count(*) from public.get_player_stats(current_setting('t.child')::uuid))::int, 1, 'apoderada ve estadísticas de su hijo');
select pg_temp.expect_fail($$select * from public.get_player_stats('d0000001-0000-0000-0000-000000000001')$$, 'apoderada NO ve las de otro niño');

-- IA: la Edge Function (service_role) obtiene datos SIN nombres
select pg_temp.as_su();
set role service_role;
do $$
declare v jsonb; t text; id uuid;
begin
  v := public.build_ai_payload('e0000000-0000-0000-0000-000000000001');
  if v::text ~* 'Halcon[0-9]|Leon[0-9]|Jugador[0-9]|Rival[0-9]' then raise exception 'FALLO: el payload de IA contiene nombres: %', v; end if;
  raise notice 'OK  payload IA sin nombres: % eventos', jsonb_array_length(v->'events');
  t := public.render_ai_text('e0000000-0000-0000-0000-000000000001', 'Al minuto 10 anotó J1 y luego J10 o J2.');
  raise notice 'OK  texto IA con nombres restituidos: %', t;
  id := public.upsert_ai_summary('e0000000-0000-0000-0000-000000000001', 'Borrador 1', 'gemini');
  perform public.upsert_ai_summary('e0000000-0000-0000-0000-000000000001', 'Borrador 2', 'gemini');
  perform public.upsert_ai_summary('e0000000-0000-0000-0000-000000000001', 'Borrador 3', 'gemini');
  begin perform public.upsert_ai_summary('e0000000-0000-0000-0000-000000000001', 'Borrador 4', 'gemini');
    raise exception 'FALLO: debía limitar a 3 regeneraciones';
  exception when others then
    if sqlerrm like 'FALLO%' then raise; end if;
    raise notice 'OK  (bloqueado) 4.ª regeneración: %', sqlerrm;
  end;
end $$;
reset role;
select pg_temp.as_user('a0000000-0000-0000-0000-000000000003');
select public.save_summary_edit('e0000000-0000-0000-0000-000000000001', 'Texto corregido por el DT');
select public.approve_summary('e0000000-0000-0000-0000-000000000001', 'team');
select pg_temp.expect_eq((select status from public.match_summaries), 'approved', 'resumen aprobado por el DT');
select pg_temp.expect_fail($$select public.build_ai_payload('e0000000-0000-0000-0000-000000000001')$$, 'cliente no puede llamar funciones de IA internas');
select pg_temp.as_user('a0000000-0000-0000-0000-000000000006');
select pg_temp.expect_fail($$select public.approve_summary('e0000000-0000-0000-0000-000000000001','public')$$, 'seguidor no aprueba resúmenes');
select pg_temp.expect_fail($$select public.change_match_state('e0000000-0000-0000-0000-000000000001','finish')$$, 'seguidor no controla el partido');

select pg_temp.as_su();
\echo '=== TODAS LAS PRUEBAS PASARON ==='
