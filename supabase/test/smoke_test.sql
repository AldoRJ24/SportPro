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
-- Migración 002: sin arquero titular no se puede iniciar
select pg_temp.expect_fail($$select public.change_match_state('e0000000-0000-0000-0000-000000000001','start')$$, 'no inicia sin arquero titular (002)');
select pg_temp.expect_fail($$update public.match_lineup_players set field_position='XXX' where match_id='e0000000-0000-0000-0000-000000000001'$$, 'posición inválida rechazada (002)');
update public.match_lineup_players l set field_position = case when tp.jersey_number = 1 then 'POR' when tp.jersey_number <= 3 then 'DEF' when tp.jersey_number <= 6 then 'MED' else 'DEL' end
  from public.team_players tp where tp.player_id = l.player_id and l.match_id='e0000000-0000-0000-0000-000000000001';
select pg_temp.expect_eq((select field_position from public.get_match_lineup('e0000000-0000-0000-0000-000000000001') where jersey_number = 1 and team_side='home'), 'POR', 'lineup RPC devuelve posición (002)');
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

-- =====================================================================
-- MENSUALIDADES (migración 008). Actores: ADM = …002, DT de Halcones = …003,
-- apoderada de Mateo = …005, extraño = …007. Jugador1 no tiene apoderado.
-- =====================================================================
-- Plan: solo el admin
select pg_temp.as_user('a0000000-0000-0000-0000-000000000003');
select pg_temp.expect_fail($$select public.fee_set_plan('c0000000-0000-0000-0000-000000000001', 120, 10)$$, 'DT no define el monto de la mensualidad');
select pg_temp.expect_fail($$select public.fee_generate_charges('c0000000-0000-0000-0000-000000000001')$$, 'no se generan cobros sin plan');
select pg_temp.as_user('a0000000-0000-0000-0000-000000000002');
select pg_temp.expect_fail($$select public.fee_set_plan('c0000000-0000-0000-0000-000000000001', 0, 10)$$, 'monto 0 rechazado');
select pg_temp.expect_fail($$select public.fee_set_plan('c0000000-0000-0000-0000-000000000001', 120, 31)$$, 'día de vencimiento 31 rechazado');
select public.fee_set_plan('c0000000-0000-0000-0000-000000000001', 120, 10);

-- Selección de jugadores: el DT elige; un extraño no; solo jugadores del plantel
select pg_temp.as_user('a0000000-0000-0000-0000-000000000007');
select pg_temp.expect_fail($$select public.fee_set_player('c0000000-0000-0000-0000-000000000001','d0000001-0000-0000-0000-000000000001', true)$$, 'extraño no elige quién paga');
select pg_temp.as_user('a0000000-0000-0000-0000-000000000003');
select pg_temp.expect_fail($$select public.fee_set_player('c0000000-0000-0000-0000-000000000001','d0000001-0000-0000-0000-000000000002', true)$$, 'jugador de otro equipo no entra a la mensualidad');
select public.fee_set_player('c0000000-0000-0000-0000-000000000001', current_setting('t.child')::uuid, true);
select public.fee_set_player('c0000000-0000-0000-0000-000000000001', 'd0000001-0000-0000-0000-000000000001', true);
select public.fee_set_player('c0000000-0000-0000-0000-000000000001', 'd0000002-0000-0000-0000-000000000001', true);
select public.fee_set_player('c0000000-0000-0000-0000-000000000001', 'd0000002-0000-0000-0000-000000000001', false);

-- Generar cobros: solo para los 2 seleccionados y sin duplicar al repetir
select pg_temp.expect_eq(public.fee_generate_charges('c0000000-0000-0000-0000-000000000001'), 2, 'cobros solo para los 2 jugadores seleccionados');
select pg_temp.expect_eq(public.fee_generate_charges('c0000000-0000-0000-0000-000000000001'), 0, 'generar otra vez no duplica (idempotente)');
select pg_temp.expect_fail($$select public.fee_generate_charges('c0000000-0000-0000-0000-000000000001', (current_date + interval '6 months')::date)$$, 'no se generan cobros de meses lejanos');
select pg_temp.as_su();
select set_config('t.c_mateo', (select id::text from public.fee_charges where player_id = current_setting('t.child')::uuid), false) is not null;
select set_config('t.c_j1', (select id::text from public.fee_charges where player_id = 'd0000001-0000-0000-0000-000000000001'), false) is not null;
select pg_temp.expect_eq((select count(*) from public.notifications where kind = 'fee_charge' and user_id = 'a0000000-0000-0000-0000-000000000005')::int, 1, 'apoderada recibe aviso de la nueva mensualidad');

-- Quién ve qué
select pg_temp.as_user('a0000000-0000-0000-0000-000000000005');
select pg_temp.expect_eq((select count(*) from public.fee_statement())::int, 1, 'apoderada ve solo la mensualidad de su hijo');
select pg_temp.expect_eq((select count(*) from public.fee_charges)::int, 1, 'RLS: apoderada lee solo el cobro de su hijo');
select pg_temp.expect_eq((select count(*) from public.fee_statement('c0000000-0000-0000-0000-000000000001'))::int, 1, 'apoderada no ve cobros ajenos aunque pida el equipo');
select pg_temp.expect_fail($$select * from public.fee_team_players('c0000000-0000-0000-0000-000000000001')$$, 'apoderada no ve la deuda del equipo');
select pg_temp.as_user('a0000000-0000-0000-0000-000000000007');
select pg_temp.expect_eq((select count(*) from public.fee_charges)::int, 0, 'extraño NO ve cobros');
select pg_temp.expect_eq((select count(*) from public.fee_statement('c0000000-0000-0000-0000-000000000001'))::int, 0, 'extraño NO ve el estado de cuenta del equipo');
select pg_temp.as_user('a0000000-0000-0000-0000-000000000003');
select pg_temp.expect_eq((select count(*) from public.fee_statement())::int, 0, 'el DT no es pagador: su estado de cuenta propio está vacío');
select pg_temp.expect_eq((select count(*) from public.fee_statement('c0000000-0000-0000-0000-000000000001'))::int, 2, 'DT ve los movimientos de su equipo');
select pg_temp.expect_eq((select debt from public.fee_summary_for_me() where team_id = 'c0000000-0000-0000-0000-000000000001'), 240::numeric, 'DT ve la deuda total de su equipo');

-- Pagar: solo quien corresponde, y con número de operación
select pg_temp.expect_fail(format($$select public.fee_report_payment(%L, 'yape', '123456')$$, current_setting('t.c_mateo')), 'el DT no paga por el jugador');
select pg_temp.expect_fail(format($$select public.fee_register_payment(%L, 'cash')$$, current_setting('t.c_mateo')), 'el DT no registra pagos (el dinero va al admin)');
select pg_temp.as_user('a0000000-0000-0000-0000-000000000005');
select pg_temp.expect_fail(format($$select public.fee_report_payment(%L, 'yape', '123456')$$, current_setting('t.c_j1')), 'apoderada no paga la mensualidad de otro niño');
select pg_temp.expect_fail(format($$select public.fee_report_payment(%L, 'yape', '')$$, current_setting('t.c_mateo')), 'Yape exige número de operación');
select pg_temp.expect_fail(format($$select public.fee_report_payment(%L, 'bitcoin', '123456')$$, current_setting('t.c_mateo')), 'método de pago inválido rechazado');
select pg_temp.expect_fail(format($$insert into public.fee_payments (charge_id, amount, method, paid_on, status) values (%L, 120, 'cash', current_date, 'confirmed')$$, current_setting('t.c_mateo')), 'cliente no inserta pagos directo en la tabla');
select pg_temp.expect_fail(format($$update public.fee_charges set status = 'paid' where id = %L$$, current_setting('t.c_mateo')), 'cliente no marca cobros como pagados');
select public.fee_report_payment(current_setting('t.c_mateo')::uuid, 'yape', '123456', 'Pagado desde el Yape de Paola') is not null;
select pg_temp.expect_fail(format($$select public.fee_report_payment(%L, 'plin', '999999')$$, current_setting('t.c_mateo')), 'no se informa dos veces el mismo cobro');
select pg_temp.expect_eq((select payment_status from public.fee_statement()), 'reported', 'pago informado queda por confirmar');
select pg_temp.as_su();
select set_config('t.pay1', (select id::text from public.fee_payments where status = 'reported'), false) is not null;
select pg_temp.expect_eq((select count(*) from public.notifications where kind = 'fee_payment_reported' and user_id = 'a0000000-0000-0000-0000-000000000002')::int, 1, 'el admin recibe el aviso de pago por confirmar');

-- Confirmar / rechazar: solo el admin
select pg_temp.as_user('a0000000-0000-0000-0000-000000000003');
select pg_temp.expect_fail(format($$select public.fee_decide_payment(%L, true)$$, current_setting('t.pay1')), 'el DT no confirma pagos');
select pg_temp.as_user('a0000000-0000-0000-0000-000000000005');
select pg_temp.expect_fail(format($$select public.fee_decide_payment(%L, true)$$, current_setting('t.pay1')), 'la apoderada no confirma su propio pago');
select pg_temp.as_user('a0000000-0000-0000-0000-000000000002');
select pg_temp.expect_fail(format($$select public.fee_decide_payment(%L, false, '')$$, current_setting('t.pay1')), 'rechazar exige motivo');
select pg_temp.expect_fail(format($$select public.fee_register_payment(%L, 'cash')$$, current_setting('t.c_mateo')), 'no se registra otro pago mientras hay uno informado');
select public.fee_decide_payment(current_setting('t.pay1')::uuid, true);
select pg_temp.expect_eq((select status from public.fee_charges where id = current_setting('t.c_mateo')::uuid), 'paid', 'admin confirma y el cobro queda pagado');
select pg_temp.expect_fail(format($$select public.fee_decide_payment(%L, false, 'me equivoqué')$$, current_setting('t.pay1')), 'un pago ya revisado no se decide otra vez');

-- Anular (nada se borra) y registrar directo
select pg_temp.expect_fail(format($$select public.fee_void_payment(%L, '')$$, current_setting('t.pay1')), 'anular exige motivo');
select public.fee_void_payment(current_setting('t.pay1')::uuid, 'El Yape no llegó a la cuenta');
select pg_temp.expect_eq((select status from public.fee_charges where id = current_setting('t.c_mateo')::uuid), 'pending', 'anular el pago devuelve el cobro a pendiente');
select pg_temp.expect_eq((select count(*) from public.fee_payments where status = 'voided')::int, 1, 'el pago anulado se conserva con su motivo');
select pg_temp.expect_fail(format($$select public.fee_register_payment(%L, 'cash', (current_date + 5))$$, current_setting('t.c_mateo')), 'fecha de pago futura rechazada');
select public.fee_register_payment(current_setting('t.c_mateo')::uuid, 'cash', null, null, 'Pagó en efectivo en la academia') is not null;
select pg_temp.expect_eq((select status from public.fee_statement(null, current_setting('t.child')::uuid)), 'paid', 'admin registra un pago directo y queda pagado');

-- Vencidos, recordatorio y exoneración
select pg_temp.as_su();
update public.fee_charges set due_date = current_date - 5 where id = current_setting('t.c_j1')::uuid;
select pg_temp.as_user('a0000000-0000-0000-0000-000000000003');
select pg_temp.expect_eq((select status from public.fee_statement('c0000000-0000-0000-0000-000000000001', 'd0000001-0000-0000-0000-000000000001')), 'overdue', 'cobro pendiente con fecha pasada se muestra vencido');
select pg_temp.expect_eq((select overdue_count from public.fee_team_players('c0000000-0000-0000-0000-000000000001') where player_id = 'd0000001-0000-0000-0000-000000000001'), 1, 'DT ve qué jugador está vencido');
select pg_temp.expect_eq(public.fee_send_reminders('c0000000-0000-0000-0000-000000000001'), 1, 'recordatorio enviado al cobro vencido');
select pg_temp.expect_eq(public.fee_send_reminders('c0000000-0000-0000-0000-000000000001'), 0, 'no se repite el recordatorio el mismo día');
select pg_temp.expect_fail(format($$select public.fee_waive_charge(%L, 'Beca deportiva')$$, current_setting('t.c_j1')), 'el DT no exonera cobros');
select pg_temp.as_user('a0000000-0000-0000-0000-000000000002');
select pg_temp.expect_fail(format($$select public.fee_waive_charge(%L, '')$$, current_setting('t.c_j1')), 'exonerar exige motivo');
select public.fee_waive_charge(current_setting('t.c_j1')::uuid, 'Beca deportiva');
select pg_temp.expect_eq((select debt from public.fee_summary_for_me() where team_id = 'c0000000-0000-0000-0000-000000000001'), 0::numeric, 'deuda del equipo en 0 tras pago y exoneración');
select pg_temp.expect_fail(format($$select public.fee_waive_charge(%L, 'otra vez')$$, current_setting('t.c_mateo')), 'un cobro pagado no se exonera');

-- Auditoría y rol anónimo
select pg_temp.as_su();
select pg_temp.expect_eq((select count(*) from public.audit_log where action like 'fee_%') >= 12, true, 'las acciones de mensualidades quedan en audit_log');
set role anon;
select pg_temp.expect_fail($$select count(*) from public.fee_charges$$, 'anon no lee cobros');
select pg_temp.expect_fail($$select count(*) from public.fee_payments$$, 'anon no lee pagos');
select pg_temp.expect_fail($$select * from public.fee_statement()$$, 'anon no ejecuta el estado de cuenta');
reset role;

-- =====================================================================
-- DOCUMENTOS (migración 009). Mateo (hijo de la apoderada …005) juega en Halcones.
-- DT de Halcones = …003, ADM = …002, …004 es miembro de la academia pero NO staff.
-- storage.objects es la tabla real donde Storage guarda cada archivo: insertar ahí
-- como un usuario equivale a subir un archivo con su sesión.
-- =====================================================================
select pg_temp.as_su();
select set_config('t.f1', current_setting('t.child') || '/doc-dni-1.pdf', false) is not null;
select set_config('t.f2', current_setting('t.child') || '/doc-dni-2.jpg', false) is not null;
select set_config('t.f3', current_setting('t.child') || '/doc-med-1.pdf', false) is not null;
select set_config('t.f4', current_setting('t.child') || '/doc-seg-1.pdf', false) is not null;

-- Storage: solo el apoderado sube, y solo en la carpeta de su hijo
select pg_temp.as_user('a0000000-0000-0000-0000-000000000007');
select pg_temp.expect_fail(format($$insert into storage.objects (bucket_id, name) values ('documents', %L)$$, current_setting('t.f1')), 'extraño no sube archivos a la carpeta de un jugador');
select pg_temp.as_user('a0000000-0000-0000-0000-000000000003');
select pg_temp.expect_fail(format($$insert into storage.objects (bucket_id, name) values ('documents', %L)$$, current_setting('t.f1')), 'el DT no sube documentos por la familia');
select pg_temp.as_user('a0000000-0000-0000-0000-000000000005');
select pg_temp.expect_fail($$insert into storage.objects (bucket_id, name) values ('documents', 'd0000001-0000-0000-0000-000000000001/x.pdf')$$, 'apoderada no sube a la carpeta de otro niño');
select pg_temp.expect_fail($$insert into storage.objects (bucket_id, name) values ('documents', 'carpeta-rara/x.pdf')$$, 'carpeta que no es un jugador rechazada');
insert into storage.objects (bucket_id, name)
select 'documents', unnest(array[current_setting('t.f1'), current_setting('t.f2'), current_setting('t.f3'), current_setting('t.f4')]);

-- Registrar el documento: validaciones
select pg_temp.expect_fail(format($$select public.doc_register(%L, 'dni', %L, 'application/pdf')$$, current_setting('t.child'), current_setting('t.child') || '/no-existe.pdf'), 'no se registra un archivo que no se subió');
select pg_temp.expect_fail(format($$select public.doc_register(%L, 'dni', 'd0000001-0000-0000-0000-000000000001/x.pdf', 'application/pdf')$$, current_setting('t.child')), 'no se registra una ruta de otra carpeta');
select pg_temp.expect_fail(format($$select public.doc_register(%L, 'pasaporte', %L, 'application/pdf')$$, current_setting('t.child'), current_setting('t.f1')), 'tipo de documento inválido rechazado');
select pg_temp.expect_fail(format($$select public.doc_register(%L, 'dni', %L, 'application/zip')$$, current_setting('t.child'), current_setting('t.f1')), 'formato de archivo no permitido rechazado');
select pg_temp.expect_fail(format($$select public.doc_register(%L, 'dni', %L, 'application/pdf', (current_date - 3))$$, current_setting('t.child'), current_setting('t.f1')), 'documento ya vencido rechazado');
select pg_temp.expect_fail(format($$select public.doc_register('d0000001-0000-0000-0000-000000000001', 'dni', %L, 'application/pdf')$$, current_setting('t.f1')), 'apoderada no registra documentos de otro niño');
select pg_temp.expect_fail(format($$insert into public.player_documents (player_id, doc_type, file_path, mime_type, status) values (%L, 'dni', 'x/y.pdf', 'application/pdf', 'approved')$$, current_setting('t.child')), 'cliente no inserta documentos directo en la tabla');
select public.doc_register(current_setting('t.child')::uuid, 'dni', current_setting('t.f1'), 'application/pdf') is not null;
select pg_temp.expect_eq((public.doc_list(current_setting('t.child')::uuid)->>'can_upload')::boolean, true, 'apoderada puede subir documentos de su hijo');
select pg_temp.expect_eq((public.doc_list(current_setting('t.child')::uuid)->>'can_review')::boolean, false, 'apoderada no puede aprobar documentos');

-- Los archivos no se modifican ni se borran (no hay política de UPDATE ni DELETE)
with u as (update storage.objects set name = name || '.x' where bucket_id = 'documents' returning 1) select pg_temp.expect_eq((select count(*) from u)::int, 0, 'un archivo subido no se puede modificar');
with u as (delete from storage.objects where bucket_id = 'documents' returning 1) select pg_temp.expect_eq((select count(*) from u)::int, 0, 'un archivo subido no se puede borrar');

-- Quién ve los documentos
select pg_temp.as_su();
select set_config('t.d1', (select id::text from public.player_documents where file_path = current_setting('t.f1')), false) is not null;
select pg_temp.expect_eq((select count(*) from public.notifications where kind = 'document_uploaded' and user_id in ('a0000000-0000-0000-0000-000000000003','a0000000-0000-0000-0000-000000000002'))::int, 2, 'DT y admin reciben aviso del documento por revisar');
select pg_temp.as_user('a0000000-0000-0000-0000-000000000003');
select pg_temp.expect_eq((select count(*) from public.player_documents)::int, 1, 'DT del equipo ve el documento');
select pg_temp.expect_eq((select count(*) from storage.objects where bucket_id = 'documents')::int, 4, 'DT del equipo puede abrir los archivos');
select pg_temp.as_user('a0000000-0000-0000-0000-000000000002');
select pg_temp.expect_eq((select count(*) from public.player_documents)::int, 1, 'admin de la academia ve el documento');
select pg_temp.as_user('a0000000-0000-0000-0000-000000000004');
select pg_temp.expect_eq((select count(*) from public.player_documents)::int, 0, 'miembro de la academia que no es staff NO ve documentos');
select pg_temp.expect_eq((select count(*) from storage.objects where bucket_id = 'documents')::int, 0, 'miembro que no es staff NO abre archivos');
select pg_temp.expect_fail(format($$select public.doc_list(%L)$$, current_setting('t.child')), 'miembro que no es staff no lista documentos');
select pg_temp.as_user('a0000000-0000-0000-0000-000000000007');
select pg_temp.expect_eq((select count(*) from public.player_documents)::int, 0, 'extraño NO ve documentos');
select pg_temp.expect_eq((select count(*) from storage.objects where bucket_id = 'documents')::int, 0, 'extraño NO abre archivos');

-- Revisar: solo DT / admin, con motivo al rechazar
select pg_temp.as_user('a0000000-0000-0000-0000-000000000005');
select pg_temp.expect_fail(format($$select public.doc_review(%L, true)$$, current_setting('t.d1')), 'la apoderada no aprueba su propio documento');
select pg_temp.as_user('a0000000-0000-0000-0000-000000000004');
select pg_temp.expect_fail(format($$select public.doc_review(%L, true)$$, current_setting('t.d1')), 'miembro que no es staff no aprueba documentos');
select pg_temp.as_user('a0000000-0000-0000-0000-000000000003');
select pg_temp.expect_fail(format($$select public.doc_review(%L, false, ' ')$$, current_setting('t.d1')), 'rechazar un documento exige motivo');
select public.doc_review(current_setting('t.d1')::uuid, false, 'La foto está borrosa');
select pg_temp.expect_eq((select status from public.player_documents where id = current_setting('t.d1')::uuid), 'rejected', 'DT rechaza el documento con motivo');
select pg_temp.expect_fail(format($$select public.doc_review(%L, true)$$, current_setting('t.d1')), 'un documento ya revisado no se revisa otra vez');
select pg_temp.as_su();
select pg_temp.expect_eq((select count(*) from public.notifications where kind = 'document_rejected' and user_id = 'a0000000-0000-0000-0000-000000000005')::int, 1, 'la apoderada recibe el aviso del rechazo');

-- Nueva versión: la anterior queda en el historial como reemplazada
select pg_temp.as_user('a0000000-0000-0000-0000-000000000005');
select public.doc_register(current_setting('t.child')::uuid, 'dni', current_setting('t.f2'), 'image/jpeg') is not null;
select pg_temp.expect_eq(jsonb_array_length(public.doc_list(current_setting('t.child')::uuid)->'docs'), 1, 'solo hay un DNI vigente tras subir la versión nueva');
select pg_temp.expect_eq((select count(*) from public.player_documents where doc_type = 'dni')::int, 2, 'la versión anterior se conserva en el historial');
select pg_temp.as_user('a0000000-0000-0000-0000-000000000003');
select pg_temp.expect_fail(format($$select public.doc_review(%L, true)$$, current_setting('t.d1')), 'no se revisa una versión reemplazada');
select public.doc_review((select id from public.player_documents where file_path = current_setting('t.f2')), true);
select pg_temp.expect_eq((select missing from public.doc_team_status('c0000000-0000-0000-0000-000000000001') where player_id = current_setting('t.child')::uuid), array['insurance','medical'], 'DT ve qué documentos le faltan al jugador');
select pg_temp.expect_eq((select count(*) from public.doc_team_status('c0000000-0000-0000-0000-000000000001') where complete)::int, 0, 'ningún jugador está completo todavía');

-- Completar y vencimiento
select pg_temp.as_user('a0000000-0000-0000-0000-000000000005');
select public.doc_register(current_setting('t.child')::uuid, 'medical', current_setting('t.f3'), 'application/pdf') is not null;
select public.doc_register(current_setting('t.child')::uuid, 'insurance', current_setting('t.f4'), 'application/pdf', (current_date + 30)) is not null;
select pg_temp.expect_fail($$select * from public.doc_team_status('c0000000-0000-0000-0000-000000000001')$$, 'la apoderada no ve los documentos del plantel');
select pg_temp.as_user('a0000000-0000-0000-0000-000000000002');
select pg_temp.expect_eq((select pending_count from public.doc_team_status('c0000000-0000-0000-0000-000000000001') where player_id = current_setting('t.child')::uuid), 2, 'admin ve 2 documentos por revisar');
select public.doc_review((select id from public.player_documents where file_path = current_setting('t.f3')), true);
select public.doc_review((select id from public.player_documents where file_path = current_setting('t.f4')), true);
select pg_temp.expect_eq((select complete from public.doc_team_status('c0000000-0000-0000-0000-000000000001') where player_id = current_setting('t.child')::uuid), true, 'jugador completo con DNI, ficha médica y seguro aprobados');
select pg_temp.as_su();
update public.player_documents set expires_on = current_date - 3 where file_path = current_setting('t.f4');
select pg_temp.as_user('a0000000-0000-0000-0000-000000000003');
select pg_temp.expect_eq((select missing from public.doc_team_status('c0000000-0000-0000-0000-000000000001') where player_id = current_setting('t.child')::uuid), array['insurance'], 'un seguro vencido vuelve a figurar como faltante');
select pg_temp.expect_eq((select expired_count from public.doc_team_status('c0000000-0000-0000-0000-000000000001') where player_id = current_setting('t.child')::uuid), 1, 'DT ve el documento vencido');

-- Auditoría y rol anónimo
select pg_temp.as_su();
select pg_temp.expect_eq((select count(*) from public.audit_log where action like 'document_%') >= 8, true, 'las acciones de documentos quedan en audit_log');
select pg_temp.expect_eq((select public from storage.buckets where id = 'documents'), false, 'el bucket de documentos es privado');
set role anon;
select pg_temp.expect_fail($$select count(*) from public.player_documents$$, 'anon no lee documentos');
select pg_temp.expect_fail(format($$select public.doc_list(%L)$$, current_setting('t.child')), 'anon no lista documentos');
reset role;

-- =====================================================================
-- NOTIFICACIONES (migración 010): preferencias, convocatoria y partido en vivo.
-- Apoderada de Mateo = …005, seguidor de Halcones = …006, DT = …003, ADM = …002.
-- =====================================================================
select pg_temp.as_user('a0000000-0000-0000-0000-000000000005');
select pg_temp.expect_eq((select count(*) from public.my_notification_prefs() where enabled)::int, 5, 'las 5 categorías empiezan encendidas');
select pg_temp.expect_fail($$select public.set_notification_pref('spam', false)$$, 'categoría de notificación inválida rechazada');
select pg_temp.expect_fail($$insert into public.notification_prefs (user_id, category, enabled) values (auth.uid(), 'live', false)$$, 'cliente no escribe preferencias directo en la tabla');

-- Convocatoria: llega al apoderado con el partido en «data»
select pg_temp.as_user('a0000000-0000-0000-0000-000000000003');
insert into public.matches (id, home_team_id, away_external_name, format_players, half_minutes, visibility, venue)
values ('e0000000-0000-0000-0000-000000000002','c0000000-0000-0000-0000-000000000001','Rival Externo', 7, 25, 'followers', 'Cancha 2');
insert into public.match_callups (match_id, team_id, player_id)
values ('e0000000-0000-0000-0000-000000000002','c0000000-0000-0000-0000-000000000001', current_setting('t.child')::uuid);
select pg_temp.as_su();
select pg_temp.expect_eq((select data->>'match_id' from public.notifications where kind = 'callup' and user_id = 'a0000000-0000-0000-0000-000000000005'), 'e0000000-0000-0000-0000-000000000002', 'la apoderada recibe la convocatoria con el partido para abrirlo');

-- Partido en vivo: avisa a la familia del convocado y al seguidor del equipo.
-- (Solo en esta prueba se apaga la validación de alineación para poder poner el partido en vivo
--  sin armar los dos equipos otra vez; se vuelve a encender más abajo.)
alter table public.matches disable trigger trg_matches_start_formation;
update public.matches set status = 'live' where id = 'e0000000-0000-0000-0000-000000000002';
select pg_temp.expect_eq((select count(*) from public.notifications where kind = 'match_live' and data->>'match_id' = 'e0000000-0000-0000-0000-000000000002' and user_id = 'a0000000-0000-0000-0000-000000000005')::int, 1, 'la familia del convocado recibe partido en vivo');
select pg_temp.expect_eq((select count(*) from public.notifications where kind = 'match_live' and data->>'match_id' = 'e0000000-0000-0000-0000-000000000002' and user_id = 'a0000000-0000-0000-0000-000000000006')::int, 1, 'el seguidor del equipo recibe partido en vivo');
select pg_temp.expect_eq((select count(*) from public.notifications where kind = 'match_live' and user_id = 'a0000000-0000-0000-0000-000000000007')::int, 0, 'quien no sigue al equipo no recibe partido en vivo');
update public.matches set status = 'halftime' where id = 'e0000000-0000-0000-0000-000000000002';
update public.matches set status = 'live' where id = 'e0000000-0000-0000-0000-000000000002';
select pg_temp.expect_eq((select count(*) from public.notifications where kind = 'match_live' and data->>'match_id' = 'e0000000-0000-0000-0000-000000000002' and user_id = 'a0000000-0000-0000-0000-000000000006')::int, 1, 'al reanudar el segundo tiempo no se repite el aviso');

-- Apagar una categoría: _notify deja de crear esos avisos, y solo esos
select pg_temp.as_user('a0000000-0000-0000-0000-000000000005');
select public.set_notification_pref('payments', false);
select public.set_notification_pref('callups', false);
select pg_temp.expect_eq((select enabled from public.my_notification_prefs() where category = 'payments'), false, 'la apoderada apaga los avisos de mensualidades');
select pg_temp.as_user('a0000000-0000-0000-0000-000000000003');
select pg_temp.expect_eq(public.fee_generate_charges('c0000000-0000-0000-0000-000000000001', (date_trunc('month', current_date) + interval '1 month')::date) >= 1, true, 'se generan los cobros del mes siguiente');
insert into public.matches (id, home_team_id, away_external_name, format_players, half_minutes, visibility, venue)
values ('e0000000-0000-0000-0000-000000000003','c0000000-0000-0000-0000-000000000001','Otro Rival', 7, 25, 'followers', 'Cancha 3');
insert into public.match_callups (match_id, team_id, player_id)
values ('e0000000-0000-0000-0000-000000000003','c0000000-0000-0000-0000-000000000001', current_setting('t.child')::uuid);
select pg_temp.as_su();
select pg_temp.expect_eq((select count(*) from public.notifications where kind = 'fee_charge' and user_id = 'a0000000-0000-0000-0000-000000000005')::int, 1, 'con mensualidades apagadas no llega el aviso del cobro nuevo');
select pg_temp.expect_eq((select count(*) from public.notifications where kind = 'callup' and user_id = 'a0000000-0000-0000-0000-000000000005')::int, 1, 'con convocatorias apagadas no llega la convocatoria nueva');
update public.matches set status = 'live' where id = 'e0000000-0000-0000-0000-000000000003';
select pg_temp.expect_eq((select count(*) from public.notifications where kind = 'match_live' and data->>'match_id' = 'e0000000-0000-0000-0000-000000000003' and user_id = 'a0000000-0000-0000-0000-000000000005')::int, 1, 'las demás categorías siguen llegando');
select pg_temp.as_user('a0000000-0000-0000-0000-000000000005');
select public.set_notification_pref('callups', true);
select pg_temp.expect_eq((select count(*) from public.notification_prefs)::int, 2, 'cada usuario ve solo sus preferencias');
select pg_temp.as_user('a0000000-0000-0000-0000-000000000003');
select pg_temp.expect_eq((select count(*) from public.notification_prefs)::int, 0, 'el DT no ve las preferencias de la apoderada');

-- Marcar todo como leído: solo lo mío
select pg_temp.as_su();
alter table public.matches enable trigger trg_matches_start_formation;
select pg_temp.as_su();
select set_config('t.unread_adm', (select count(*)::text from public.notifications where user_id = 'a0000000-0000-0000-0000-000000000002' and read_at is null), false) is not null;
select pg_temp.as_user('a0000000-0000-0000-0000-000000000005');
select pg_temp.expect_eq(public.mark_all_notifications_read() > 0, true, 'marcar todo como leído devuelve cuántas marcó');
select pg_temp.expect_eq((select count(*) from public.notifications where read_at is null)::int, 0, 'no me quedan notificaciones sin leer');
select pg_temp.expect_eq(public.mark_all_notifications_read(), 0, 'marcar otra vez no cambia nada');
select pg_temp.as_su();
select pg_temp.expect_eq((select count(*)::text from public.notifications where user_id = 'a0000000-0000-0000-0000-000000000002' and read_at is null), current_setting('t.unread_adm'), 'las notificaciones de otra persona no se tocan');
set role anon;
select pg_temp.expect_fail($$select * from public.my_notification_prefs()$$, 'anon no lee preferencias');
select pg_temp.expect_fail($$select public.mark_all_notifications_read()$$, 'anon no marca notificaciones');
reset role;

-- =====================================================================
-- AVISOS DE PRUEBAS (migración 011). Academia Demo está verificada.
-- DT = …003 publica T1; ADM = …002 publica T2; apoderada = …005 (Mateo, nacido en 2016);
-- extraño = …007 solo busca. La apoderada apagó «mensualidades», no «solicitudes».
-- =====================================================================
select pg_temp.as_user('a0000000-0000-0000-0000-000000000007');
select pg_temp.expect_fail($$select public.tryout_create('b0000000-0000-0000-0000-000000000001','Prueba pirata','Sub-12',9,12,'{}', now() + interval '5 days','Cancha X','Lima',10)$$, 'extraño no publica avisos de pruebas');
select pg_temp.as_user('a0000000-0000-0000-0000-000000000005');
select pg_temp.expect_fail($$select public.tryout_create('b0000000-0000-0000-0000-000000000001','Prueba','Sub-12',9,12,'{}', now() + interval '5 days','Cancha X','Lima',10)$$, 'apoderada no publica avisos de pruebas');
select pg_temp.as_user('a0000000-0000-0000-0000-000000000003');
select pg_temp.expect_fail($$select public.tryout_create('b0000000-0000-0000-0000-000000000001','Prueba','Sub-12',9,12,'{}', now() - interval '1 day','Cancha X','Lima',10)$$, 'fecha de prueba pasada rechazada');
select pg_temp.expect_fail($$select public.tryout_create('b0000000-0000-0000-0000-000000000001','Prueba','Sub-12',12,9,'{}', now() + interval '5 days','Cancha X','Lima',10)$$, 'rango de edad invertido rechazado');
select pg_temp.expect_fail($$select public.tryout_create('b0000000-0000-0000-0000-000000000001','Prueba','Sub-12',9,12,'{LIBERO}', now() + interval '5 days','Cancha X','Lima',10)$$, 'posición inválida rechazada');
select pg_temp.expect_fail($$select public.tryout_create('b0000000-0000-0000-0000-000000000001','Prueba','Sub-12',9,12,'{}', now() + interval '5 days','Cancha X','Lima',0)$$, 'cero cupos rechazado');
select set_config('t.t1', public.tryout_create('b0000000-0000-0000-0000-000000000001','Prueba de delanteros','Sub-12',9,12,'{DEL,DEL}', now() + interval '10 days','Cancha 1 de la academia','surco',1,'Traer canilleras')::text, false) is not null;
select pg_temp.as_user('a0000000-0000-0000-0000-000000000002');
select set_config('t.t2', public.tryout_create('b0000000-0000-0000-0000-000000000001','Prueba Sub-17','Sub-17',15,17,'{}', now() + interval '20 days','Estadio municipal','Miraflores',5)::text, false) is not null;
select pg_temp.expect_fail($$insert into public.tryouts (academy_id, title, category, min_age, max_age, event_at, venue, district, slots) values ('b0000000-0000-0000-0000-000000000001','Directo','Sub-12',9,12, now() + interval '5 days','Cancha','Lima',5)$$, 'cliente no inserta avisos directo en la tabla');

-- Buscar con filtros (se aplican en el servidor)
select pg_temp.as_user('a0000000-0000-0000-0000-000000000007');
select pg_temp.expect_eq((select count(*) from public.tryout_search())::int, 2, 'cualquier usuario ve los avisos abiertos');
select pg_temp.expect_eq((select count(*) from public.tryout_search('sub-12'))::int, 1, 'filtro por categoría (sin distinguir mayúsculas)');
select pg_temp.expect_eq((select count(*) from public.tryout_search(null, 'DEL'))::int, 2, 'filtro por posición: incluye avisos que buscan todas');
select pg_temp.expect_eq((select title from public.tryout_search(null, 'POR')), 'Prueba Sub-17', 'filtro por posición: excluye avisos que buscan otra');
select pg_temp.expect_eq((select count(*) from public.tryout_search(null, null, (current_date + 15)))::int, 1, 'filtro por fecha desde');
select pg_temp.expect_eq((select count(*) from public.tryout_search(null, null, null, (current_date + 15)))::int, 1, 'filtro por fecha hasta');
select pg_temp.expect_eq((select title from public.tryout_search(null, null, null, null, 'SURCO')), 'Prueba de delanteros', 'filtro por distrito');
select pg_temp.expect_eq((select count(*) from public.tryout_search('Sub-12', 'DEL', null, null, 'Miraflores'))::int, 0, 'los filtros se combinan');
select pg_temp.expect_eq((select positions from public.tryout_search('Sub-12')), array['DEL'], 'posiciones repetidas se guardan una sola vez');
select pg_temp.expect_eq((select bool_or(can_manage) from public.tryout_search()), false, 'un extraño no gestiona ningún aviso');

-- Inscribir: solo a mi hijo y con la edad del aviso
select pg_temp.as_user('a0000000-0000-0000-0000-000000000005');
select pg_temp.expect_fail(format($$select public.tryout_apply(%L, %L)$$, current_setting('t.t2'), current_setting('t.child')), 'la edad del jugador no entra en el rango del aviso');
select pg_temp.expect_fail(format($$select public.tryout_apply(%L, 'd0000001-0000-0000-0000-000000000001')$$, current_setting('t.t1')), 'apoderada no inscribe al hijo de otra persona');
select set_config('t.app1', public.tryout_apply(current_setting('t.t1')::uuid, current_setting('t.child')::uuid)::text, false) is not null;
select pg_temp.expect_fail(format($$select public.tryout_apply(%L, %L)$$, current_setting('t.t1'), current_setting('t.child')), 'no se inscribe dos veces al mismo jugador');
select set_config('t.child2', public.create_child_player('Luz','Rojas','2015-05-05','Delantera')::text, false) is not null;
select set_config('t.app2', public.tryout_apply(current_setting('t.t1')::uuid, current_setting('t.child2')::uuid)::text, false) is not null;
select pg_temp.expect_eq((select count(*) from public.my_tryout_applications())::int, 2, 'la apoderada ve sus 2 inscripciones');
select pg_temp.expect_eq((select my_applications from public.tryout_search('Sub-12')), 2, 'el aviso muestra cuántas inscripciones mías tiene');
select pg_temp.expect_fail(format($$select * from public.tryout_applications_list(%L)$$, current_setting('t.t1')), 'la apoderada no ve la lista de inscritos');
select pg_temp.as_su();
select pg_temp.expect_eq((select count(distinct user_id) from public.notifications where kind = 'tryout_application' and user_id in ('a0000000-0000-0000-0000-000000000002','a0000000-0000-0000-0000-000000000003'))::int, 2, 'admin y DT que publicó reciben el aviso de inscripción');

-- Privacidad y permisos sobre las inscripciones
select pg_temp.as_user('a0000000-0000-0000-0000-000000000007');
select pg_temp.expect_eq((select count(*) from public.tryout_applications)::int, 0, 'extraño NO ve inscripciones');
select pg_temp.expect_fail(format($$select public.tryout_decide(%L, true)$$, current_setting('t.app1')), 'extraño no responde inscripciones');
select pg_temp.expect_fail(format($$select public.tryout_withdraw(%L)$$, current_setting('t.app1')), 'extraño no retira la inscripción de otro');
select pg_temp.as_user('a0000000-0000-0000-0000-000000000003');
select pg_temp.expect_eq((select player_name from public.tryout_applications_list(current_setting('t.t1')::uuid) where player_age = public.age_years('2016-04-04')), 'Mateo R.', 'quien revisa ve al menor como Nombre + inicial');
select pg_temp.expect_eq((select pending_count from public.tryout_search('Sub-12')), 2, 'quien gestiona ve cuántas inscripciones esperan respuesta');

-- Aceptar respetando los cupos (T1 tiene 1 cupo)
select public.tryout_decide(current_setting('t.app1')::uuid, true);
select pg_temp.expect_fail(format($$select public.tryout_decide(%L, true)$$, current_setting('t.app2')), 'no se acepta a más jugadores que cupos');
select pg_temp.expect_fail(format($$select public.tryout_decide(%L, false)$$, current_setting('t.app1')), 'una inscripción ya respondida no se responde otra vez');
select public.tryout_decide(current_setting('t.app2')::uuid, false, 'Ya no quedan cupos');
select pg_temp.as_su();
select pg_temp.expect_eq((select count(*) from public.notifications where kind = 'tryout_answer' and user_id = 'a0000000-0000-0000-0000-000000000005')::int, 2, 'la apoderada recibe la respuesta de cada inscripción');
select pg_temp.as_user('a0000000-0000-0000-0000-000000000005');
select pg_temp.expect_fail(format($$select public.tryout_apply(%L, %L)$$, current_setting('t.t1'), current_setting('t.child2')), 'no se inscribe en una prueba sin cupos');
select pg_temp.expect_eq((select reason from public.my_tryout_applications() where status = 'rejected'), 'Ya no quedan cupos', 'la apoderada ve el motivo del rechazo');

-- Retirar, cerrar y cancelar (nada se borra)
select public.tryout_withdraw(current_setting('t.app1')::uuid);
select pg_temp.expect_eq((select approved_count from public.tryout_search('Sub-12')), 0, 'al retirar la inscripción se libera el cupo');
select pg_temp.expect_fail(format($$select public.tryout_withdraw(%L)$$, current_setting('t.app1')), 'una inscripción retirada no se retira otra vez');
select pg_temp.expect_fail(format($$select public.tryout_set_status(%L, 'closed')$$, current_setting('t.t1')), 'la apoderada no cierra el aviso');
select pg_temp.as_user('a0000000-0000-0000-0000-000000000003');
select pg_temp.expect_fail(format($$select public.tryout_set_status(%L, 'closed')$$, current_setting('t.t2')), 'el DT no cierra un aviso que publicó el admin');
select public.tryout_set_status(current_setting('t.t1')::uuid, 'closed');
select pg_temp.expect_eq((select count(*) from public.tryout_search('Sub-12'))::int, 1, 'quien gestiona sigue viendo su aviso cerrado');
select pg_temp.as_user('a0000000-0000-0000-0000-000000000007');
select pg_temp.expect_eq((select count(*) from public.tryout_search())::int, 1, 'un aviso cerrado ya no aparece en la búsqueda');
select pg_temp.as_user('a0000000-0000-0000-0000-000000000005');
select pg_temp.expect_fail(format($$select public.tryout_apply(%L, %L)$$, current_setting('t.t1'), current_setting('t.child')), 'no se inscribe en un aviso cerrado');
select pg_temp.as_user('a0000000-0000-0000-0000-000000000002');
select public.tryout_set_status(current_setting('t.t1')::uuid, 'cancelled');
select pg_temp.expect_fail(format($$select public.tryout_set_status(%L, 'open')$$, current_setting('t.t1')), 'un aviso cancelado no se reabre');
select pg_temp.as_su();
select pg_temp.expect_eq((select count(*) from public.tryout_applications)::int, 2, 'las inscripciones retiradas y rechazadas se conservan');
select pg_temp.expect_eq((select count(*) from public.audit_log where action like 'tryout_%') >= 8, true, 'las acciones de pruebas quedan en audit_log');
set role anon;
select pg_temp.expect_fail($$select * from public.tryout_search()$$, 'anon no busca avisos');
select pg_temp.expect_fail($$select count(*) from public.tryout_applications$$, 'anon no lee inscripciones');
reset role;

select pg_temp.as_su();
\echo '=== TODAS LAS PRUEBAS PASARON ==='
