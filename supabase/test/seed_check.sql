-- =====================================================================
-- SportPro · Comprobaciones del seed de pruebas (supabase/seed_pruebas.sql).
-- Entra como distintos usuarios (igual que la app) y revisa lo que ve cada uno.
-- La ejecuta supabase/test/run_seed_local.sh. NUNCA en el proyecto real.
-- =====================================================================
\set ON_ERROR_STOP on
\set QUIET on
\pset tuples_only on

create or replace function pg_temp.as_email(p_email text) returns void language plpgsql as $$
declare v_id text;
begin
  execute 'reset role';   -- se busca el id como dueño (la app no puede leer auth.users)
  select id::text into v_id from auth.users where email = p_email;
  perform set_config('request.jwt.claim.sub', v_id, false);
  execute 'set role authenticated';
end $$;
create or replace function pg_temp.as_su() returns void language plpgsql as $$
begin execute 'reset role'; perform set_config('request.jwt.claim.sub', '', false); end $$;
create or replace function pg_temp.expect_eq(p_actual anyelement, p_expected anyelement, p_label text) returns void language plpgsql as $$
begin
  if p_actual is distinct from p_expected then raise exception 'FALLO % : esperado %, obtenido %', p_label, p_expected, p_actual; end if;
  raise notice 'OK  %', p_label;
end $$;
create or replace function pg_temp.match_of(p_home text, p_status text) returns uuid language sql as $$
  select m.id from public.matches m join public.teams t on t.id = m.home_team_id
  where t.name = p_home and m.status::text = p_status order by m.scheduled_at limit 1
$$;

-- ---------- Cuentas ----------
select pg_temp.expect_eq((select count(*) from auth.users where email like '%@sportpro.test')::int, 11, 'se crearon 11 cuentas');
select pg_temp.expect_eq((select count(*) from auth.users where email like '%@sportpro.test'
                          and encrypted_password = extensions.crypt('12345678', encrypted_password))::int, 11,
                         'todas las cuentas usan la contraseña 12345678');
select pg_temp.expect_eq((select count(*) from auth.identities i join auth.users u on u.id = i.user_id
                          where u.email like '%@sportpro.test' and i.provider = 'email')::int, 11, 'cada cuenta tiene su identidad de correo');
select pg_temp.expect_eq((select string_agg(coalesce(p.account_type, '-'), ',' order by u.email collate "C") from auth.users u join public.profiles p on p.id = u.id
                          where u.email like '%@sportpro.test'),
                         'adm,adm,pad,pad,dt,dt,dt,pad,jug,jug,-', 'cada cuenta tiene su tipo (por invitación o registro)');

select pg_temp.as_email('superadmin@sportpro.test');
select pg_temp.expect_eq(public.is_platform_admin(), true, 'el superadmin lo es');
select pg_temp.as_email('jugador.mateo@sportpro.test');
select pg_temp.expect_eq((public.my_account()->>'needs_guardian')::boolean, false, 'Mateo ya tiene apoderada aprobada');
select pg_temp.as_email('jugador.sofia@sportpro.test');
select pg_temp.expect_eq((public.my_account()->>'needs_guardian')::boolean, true, 'Sofía espera que su apoderado acepte');
select pg_temp.as_email('apoderado.luis@sportpro.test');
select pg_temp.expect_eq((select count(*) from public.my_guardian_requests())::int >= 1, true, 'Luis tiene la solicitud de Sofía por responder');

-- ---------- Academias, equipos y planteles ----------
select pg_temp.as_su();
select pg_temp.expect_eq((select count(*) from public.academies where status = 'verified')::int, 2, '2 academias verificadas');
select pg_temp.expect_eq((select string_agg(t.name || '=' || (select count(*) from public.team_players tp where tp.team_id = t.id and tp.status = 'active'), ',' order by t.name)
                          from public.teams t),
                         'Halcones Sub-12=10,Halcones Sub-15=16,Tigres Sub-12=10,Tigres Sub-15=16', 'planteles completos');

-- ---------- Partidos ----------
select pg_temp.expect_eq((select string_agg(status::text || '=' || n, ',' order by status::text)
                          from (select status, count(*) n from public.matches group by status) x),
                         'finished=3,halftime=1,scheduled=4', 'partidos en todos los estados');
select pg_temp.expect_eq((select string_agg(home_score || '-' || away_score, ',' order by scheduled_at) from public.matches where status = 'finished'),
                         '3-1,3-2,2-2', 'marcadores de los finalizados (calculados de los eventos)');
select pg_temp.expect_eq((select home_score || '-' || away_score from public.matches where status = 'halftime'), '2-1', 'el partido en entretiempo va 2-1');
select pg_temp.expect_eq((select count(*) from public.match_events e join public.event_types t on t.id = e.event_type_id
                          where t.code = 'goal' and e.payload ? 'assist_player_id')::int, 12, 'goles con asistencia registrados');
select pg_temp.expect_eq((select count(*) from public.match_lineup_players where slot_x is not null)::int, 29, 'titulares con lugar propio en la cancha');
select pg_temp.expect_eq((select count(*) from public.match_lineup_formations)::int, 10, 'cada alineación guardada tiene su formación');

-- El partido listo para iniciar tiene alineaciones confirmadas y QR del árbitro
select pg_temp.as_email('dt2.halcones@sportpro.test');
select pg_temp.expect_eq((select lineups_confirmed_at is not null from public.matches m join public.teams t on t.id = m.home_team_id
                          where t.name = 'Halcones Sub-12' and m.lineup_flow and m.status = 'scheduled'), true, 'el partido de hoy tiene alineaciones confirmadas');
select pg_temp.expect_eq(public.referee_link_get(pg_temp.match_of('Halcones Sub-12', 'scheduled')) ~ '^[0-9a-f]{64}$', true, 'se puede pedir el QR del árbitro');

-- Alineación pendiente: Halcones ya envió, Tigres no
select pg_temp.as_email('dt.halcones@sportpro.test');
select pg_temp.expect_eq((select (s->'sides'->0->>'submitted_at') is not null and (s->'sides'->1->>'submitted_at') is null
                          from (select public.lineup_status(m.id) s from public.matches m join public.teams t on t.id = m.home_team_id
                                where t.name = 'Halcones Sub-15' and m.lineup_flow and m.status = 'scheduled') x), true,
                         'en el partido en 3 días Halcones envió y Tigres no');
select pg_temp.expect_eq((select count(*) from public.coach_formations)::int, 1, 'cada DT ve solo su formación propia');

-- ---------- Lo que ve cada perfil ----------
select pg_temp.as_email('hincha@sportpro.test');
select pg_temp.expect_eq((select count(*) from public.get_feed())::int, 1, 'el seguidor ve en Inicio solo la publicación pública');
select pg_temp.expect_eq((select count(*) from public.my_matches() where is_favorite)::int, 2, 'el seguidor tiene 2 partidos favoritos');
select pg_temp.expect_eq((select count(*) from public.get_match_lineup_pitch(pg_temp.match_of('Tigres Sub-15', 'halftime')))::int, 32,
                         'el seguidor ve las dos alineaciones del partido en curso');
select pg_temp.as_email('apoderado.ana@sportpro.test');
select pg_temp.expect_eq((select count(*) from public.get_feed())::int, 2, 'la apoderada ve las dos publicaciones (pública y de su academia)');
select pg_temp.expect_eq((select count(*) from public.notifications)::int > 0, true, 'la apoderada tiene notificaciones');
select pg_temp.expect_eq((select count(*) from public.fee_statement(null, null) where status in ('pending', 'overdue'))::int >= 1, true,
                         'la apoderada tiene mensualidades pendientes por pagar');
select pg_temp.as_email('dt.tigres@sportpro.test');
select pg_temp.expect_eq((select count(*) from public.my_challenges())::int >= 5, true, 'el DT de Tigres ve sus desafíos');

-- ---------- Mensualidades, pruebas y solicitudes ----------
select pg_temp.as_su();
select pg_temp.expect_eq((select count(*) from public.fee_charges)::int, 46, 'cobros generados (Halcones Sub-12 dos meses, Sub-15 y Tigres Sub-12)');
select pg_temp.expect_eq((select string_agg(status || '=' || n, ',' order by status) from (select status, count(*) n from public.fee_payments group by status) x),
                         'confirmed=1,rejected=1,reported=1', 'pagos confirmados, rechazados y por revisar');
select pg_temp.expect_eq((select count(*) from public.fee_charges where status = 'waived')::int, 1, 'un cobro exonerado');
select pg_temp.expect_eq((select count(*) from public.tryouts where status = 'open')::int, 2, '2 avisos de pruebas abiertos');
select pg_temp.expect_eq((select string_agg(status, ',' order by status) from public.tryout_applications), 'approved,pending', 'una inscripción aceptada y una pendiente');
select pg_temp.expect_eq((select count(*) from public.team_join_requests where status = 'pending')::int, 1, 'una solicitud para unirse a un equipo');
select pg_temp.expect_eq((select string_agg(status, ',' order by status) from public.match_challenges), 'accepted,accepted,accepted,accepted,accepted,pending,rejected',
                         'desafíos aceptados, uno pendiente y uno rechazado');
select pg_temp.expect_eq((select count(*) from public.post_comments)::int, 2, 'comentarios en la publicación');

\echo '=== SEED DE PRUEBAS VERIFICADO ==='
