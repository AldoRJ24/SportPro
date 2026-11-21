# Datos de prueba

`supabase/seed_pruebas.sql` llena Supabase con un escenario completo y **ficticio** para probar la app con 2 celulares. Ningún dato es de una persona real.

## Cómo cargarlo

1. Supabase → **SQL Editor** → **New query**.
2. Pega todo `supabase/seed_pruebas.sql` y pulsa **Run**.
3. Debe terminar con el aviso «Datos de prueba cargados».

Si las cuentas ya existen, el script no hace nada (no duplica). Todo ocurre en una sola transacción: si algo falla, no queda nada a medias.

Antes de dárselo al usuario se prueba en local: `bash supabase/test/run_seed_local.sh` (carga el seed dos veces y hace 35 comprobaciones entrando como cada usuario).

## Cuentas

La contraseña de **todas** es `12345678`.

| Correo | Quién es | Para probar |
|---|---|---|
| `superadmin@sportpro.test` | Carlos, superadmin de la plataforma | Invitar administradores (hay una invitación sin usar) |
| `adm.halcones@sportpro.test` | Andrea, administradora de Academia Los Halcones | Mensualidades (confirmar el pago por revisar), invitaciones, aviso de pruebas, operadora del partido de hoy |
| `adm.tigres@sportpro.test` | Ricardo, administrador de Club Deportivo Tigres | Mensualidades de Tigres Sub-12, invitación de DT pendiente |
| `dt.halcones@sportpro.test` | Jorge, DT de Halcones Sub-15 | Editor de alineación (formación propia «Presión alta»), amistoso contra Deportivo Barranco |
| `dt2.halcones@sportpro.test` | Patricia, DT de Halcones Sub-12 | Partido de hoy listo para iniciar con QR del árbitro, desafío pendiente por responder, solicitud de Rafael para unirse, resumen IA del partido contra San Borja |
| `dt.tigres@sportpro.test` | Miguel, DT de Tigres Sub-15 y Sub-12 | Partido en entretiempo (iniciar el 2.º tiempo), alineación pendiente por enviar, aviso de pruebas de arqueros |
| `apoderado.ana@sportpro.test` | Ana, apoderada de Santiago (11), Lía (11) y Mateo (14) | Confirmar convocatorias, pagar mensualidades, ver resumen en Inicio |
| `apoderado.luis@sportpro.test` | Luis, apoderado de Daniel (12) y Rafael (11) | Aceptar a Sofía como su apoderado, ver la solicitud y la inscripción de Rafael |
| `jugador.mateo@sportpro.test` | Mateo, 14 años, jugador de Halcones Sub-15 | Responder la convocatoria del partido en 3 días |
| `jugador.sofia@sportpro.test` | Sofía, 14 años, sin apoderado todavía | Pantalla «necesitas un apoderado» hasta que Luis la acepte |
| `hincha@sportpro.test` | Fabio, seguidor | Inicio, partidos favoritos y la cancha en vivo como público |

## Academias y equipos

| Academia | Equipo | Formato | DT | Jugadores |
|---|---|---|---|---|
| Academia Los Halcones | Halcones Sub-15 | Fútbol 11, 30 min | Jorge | 16 (el 10 es Mateo) |
| Academia Los Halcones | Halcones Sub-12 | Fútbol 7, 25 min | Patricia | 10 (el 5 es Santiago y el 11, Lía) |
| Club Deportivo Tigres | Tigres Sub-15 | Fútbol 11, 30 min | Miguel | 16 |
| Club Deportivo Tigres | Tigres Sub-12 | Fútbol 7, 25 min | Miguel | 10 (el 7 es Daniel) |

## Partidos

| # | Partido | Estado | Qué tiene |
|---|---|---|---|
| 1 | Halcones Sub-15 3-2 Tigres Sub-15 | Finalizado hace 7 días | Penal, goles con asistencia, amarillas, una roja y cambios. **MVP: el 9 de Halcones** (2 goles). Resumen público en Inicio con «me gusta» y 2 comentarios |
| 2 | Halcones Sub-12 2-2 Tigres Sub-12 | Finalizado hace 3 días | 4 goleadores con 1 gol cada uno: **sin MVP** (empate). Resumen visible para la academia |
| 3 | Halcones Sub-12 3-1 Escuela San Borja (externo) | Finalizado hace 10 días | **Sin resumen**: sirve para probar «Resumen IA» |
| 4 | Tigres Sub-15 2-1 Halcones Sub-15 | **Entretiempo** | Halcones juega con un lateral adelantado (lugares propios). MVP por ahora: el 9 de Tigres |
| 5 | Halcones Sub-12 vs Tigres Sub-12 | **Listo para iniciar** (en 2 horas) | Alineaciones confirmadas y QR del árbitro. La operadora es Andrea |
| 6 | Halcones Sub-15 vs Tigres Sub-15 | Alineación pendiente (en 3 días) | Halcones ya envió; Tigres no armó nada. Mateo no respondió la convocatoria |
| 7 | Halcones Sub-15 vs Deportivo Barranco (externo) | Programado (en 2 días) | Convocatoria a medias, sin alineación |
| 8 | Halcones Sub-12 vs Escuela San Borja (externo) | Programado (en 4 días) | Ana debe confirmar a Santiago y a Lía |

También hay un **desafío pendiente** (Tigres Sub-12 desafía a Halcones Sub-12) y uno **rechazado** con motivo.

## Otros datos

- **Mensualidades:** Halcones Sub-12 cobra S/ 120 y Sub-15, S/ 150. Octubre de Santiago está pagado; noviembre de Santiago espera revisión; octubre de Lía fue rechazado y quedó vencido; el arquero Benjamín tiene octubre exonerado («Beca deportiva»). Tigres Sub-12 cobra S/ 100.
- **Avisos de pruebas:** «Pruebas Sub-12 · temporada 2027» (Halcones, Rafael inscrito y pendiente) y «Captación de arqueros Sub-15» (Tigres, Mateo aceptado).
- **Solicitudes:** Rafael pidió unirse a Halcones Sub-12; Sofía pidió que Luis sea su apoderado.
- **Formaciones propias:** «Presión alta» (Jorge), «Bloque bajo» (Miguel) y «Lateral ofensivo» (Patricia).

## Lo que no incluye

- **Fotos y documentos:** son archivos y no se pueden crear con SQL. Súbelos desde la app (una foto de perfil con una cuenta adulta; un DNI desde la cuenta de Ana).
- **Reloj del partido en entretiempo:** está detenido. Cuando el DT de Tigres pulse «Iniciar 2.º tiempo», el reloj arrancará en 30'.
