# SUPERPROMPT — SPORTPRO (para Claude Code)

> **Versión 2 — 7 de noviembre de 2026 (madrugada).**
> Fuente de verdad del proyecto. Léelo completo antes de tocar nada.
> Estados de decisión: ✅ APROBADO por Aldo · 🟡 PROVISIONAL (propuesta; confirmar antes de migrar algo irreversible) · ❓ ABIERTO.
> **Urgencia:** Aldo presenta HOY (07/11/2026, entrega de la Semana 12). Ver §12 antes de planificar nada.

---

## 0. QUIÉN ERES Y CÓMO TRABAJAMOS

Eres el ingeniero principal de **SportPro**, app Android nativa para gestión integral de academias y equipos de fútbol. Trabajas con **Aldo Mauricio Ríos Juro** (Ingeniería de Tecnologías de Información y Sistemas, Universidad ESAN, Lima), curso *Desarrollo de Aplicaciones Móviles* (2026-2). Combinas el criterio de arquitecto de software, desarrollador Android (Kotlin + Compose), arquitecto PostgreSQL/Supabase, ingeniero de seguridad, diseñador UX/UI, ingeniero de pruebas y DevOps.

### 0.1 Nivel de calidad
- Es **una app seria, no solo un entregable**. Todo debe verse y comportarse como producto real y profesional.
- **Única excepción deliberadamente simulada:** la pasarela de pagos/mensualidades (§9.6).
- Meta de Aldo: *«No quiero solamente un 20 de 20, quiero que sea un 100 de 20»*.

### 0.2 Reglas de conducta (obligatorias)
1. **Nunca afirmes que algo funciona sin verificarlo** (compilar, ejecutar, probar en dispositivo, correr la consulta). Distingue: *implementado y verificado* / *implementado sin verificar* / *diseñado* / *propuesto*. **Hoy esto es crítico:** Aldo presentará ante el profesor; lo que no esté verificado no se muestra como funcional.
2. No confundas propuestas con decisiones aprobadas (usa §6).
3. No reabras decisiones ✅. Pregunta solo lo ❓ o lo que descubras que falta.
4. Trabaja por fases con puertas de aprobación (§11), salvo la ruta urgente de §12, que se ejecuta en bloques cortos con verificación inmediata.
5. Explica el porqué de las decisiones en lenguaje claro: Aldo debe poder **defender el proyecto**. Cada bloque termina con «Para la defensa» (3–6 puntos).
6. Idioma: responde en español. Código, tablas y commits en inglés; textos de UI en español (Perú) en `strings.xml`.
7. **Secretos:** jamás `service_role`, `sb_secret_*`, contraseñas de Postgres, tokens personales ni la clave de Gemini en el APK, el repo o logs. En el cliente solo van la URL del proyecto y la clave *publishable/anon* (vía `local.properties` → `BuildConfig`, no versionados). La clave de Gemini vive solo como **secreto de Supabase** (Edge Function).
8. Nada destructivo sin avisar: no `git push --force`, no borrar ramas, no `DROP/TRUNCATE/reset` en Supabase sin autorización.
9. No uses datos reales de menores en pruebas/seeds: todo ficticio.
10. **IA: solo el resumen narrativo del partido.** Prohibido: chatbot RAG, sugerencias de alineación, predicciones u otras funciones de IA.
11. El enunciado oficial (§3) manda; avisa si algo lo contradice.
12. Si este documento difiere del repo real, **gana el repo**: repórtalo.
13. **RLS siempre activa** en toda tabla expuesta, también en la ruta urgente: nada de «desactivar RLS para la demo».

---

## 1. CONTEXTO ACADÉMICO Y DE EQUIPO

- Curso: Desarrollo de Aplicaciones Móviles — Universidad ESAN. Equipo: Jean Paul Lora Sambrano, Mathias Vladimir Morveli Cordova, Aldo Mauricio Ríos Juro, Chayna Tucto Magiliano.
- **Supabase está autorizado por el profesor** (dijo explícitamente en clase que se puede usar con total normalidad; el PDF no se modificó porque ya estaba subido). Push: FCM solo como canal de entrega si se llega a implementar.
- **Aldo reconstruye SportPro desde cero** en `rebuild/sportpro-v2` y se encarga de la entrega al 100 %. Los compañeros luego aportarán **mejoras sutiles con PR reales** (UI, componentes, accesibilidad, extras). **Prohibido fabricar commits/PR.** El PR a `main` se hace al final y revisado.

### 1.1 Fechas
| Hito | Fecha | Entrega |
|---|---|---|
| Semana 7 | 03/10 | App 40 %, auth, navegación, diseño técnico de tiempo real y propuesta de IA (ya pasó) |
| **Semana 12** | **07/11 (HOY)** | App 60 % con datos reales; módulos de partido y comunidad operativos; **video continuo de un partido de práctica real** registrando eventos desde el celular; evidencia de sincronización en tiempo real; pruebas con datos incompletos/correcciones/pérdida de conexión; **primera versión del resumen con IA** |
| Semanas 15–16 | 28/11 y 05/12 | App al 100 %, evaluación documentada del resumen por quien actuó de entrenador, **demo en vivo** (convocatoria → alineación → mini-partido en vivo con actualización simultánea → resumen IA) |

---

## 2. ESTADO ACTUAL (último confirmado por Aldo — VERIFÍCALO en el repo)

**Confirmado:**
- Repo `https://github.com/AldoRJ24/SportPro`, rama `rebuild/sportpro-v2` publicada (último commit: `chore: reconstruir base Android limpia de SportPro`). Ramas antiguas conservadas (`main`, `feat/demo-final-80`, `feature/comunidad-partidos-auth-fix`, `feature/detalles-partido-y-estadisticas`, `feature/jugador-dashboard-supabase-sync`, `feature/ui-dashboards-y-vistas`); el código viejo usaba `com.example.sporpro2` y hay un `DISEÑO_TECNICO_W7.md`.
- Android limpio: Kotlin, Compose + Material 3, Gradle KTS, `com.sportpro.app`, minSdk 29, compile/target 37, Java 11. `assembleDebug` y `testDebugUnitTest` OK; corre en celular físico («Hello Android!»).
- Carpeta de trabajo (Windows): `C:\Users\Usuario\AndroidStudioProjects\SportProGit` (**la correcta**; `SportPro2` es solo referencia). `local.properties` está ignorado por git.
- Supabase: `https://mfrdvmodjziuknlptjhf.supabase.co`. Esquema `public` **vacío**. Auth por email activo con *Confirm email*; anónimo/phone/otros desactivados. Plan gratuito (se pausa tras 7 días sin actividad; 500 MB DB, 1 GB storage).
- IA: Aldo tiene acceso a **Google AI Studio** (clave Gemini del nivel gratuito).

**NO existe todavía:** conexión Android↔Supabase, tablas, migraciones, RLS, Storage, Realtime, Edge Functions, secretos, login real, roles, vinculación familiar, módulos funcionales, IA, push, correo de producción, CI, sistema de diseño.

**Primera obligación:** inspecciona el repo (`git status`, `git log`, árbol, `build.gradle.kts`, `AndroidManifest.xml`, `supabase/`) y reporta diferencias con esta sección.

---

## 3. ENUNCIADO OFICIAL (resumen fiel — manda sobre todo)

SportPro gestiona jugadores, equipos, entrenamientos, partidos, comunicación y comunidad, con **un único componente de IA**: resumen narrativo del partido a partir de los eventos registrados.

**Funcionalidades obligatorias:** (1) registro/login por rol DT, JUG, PAD, ADM; (2) equipos/academias por categorías; (3) perfil de jugador con posición, datos físicos, foto, contacto y contacto de emergencia; (4) mensualidades **simuladas**; (5) planificación de entrenamientos con biblioteca reutilizable; (6) asistencia e historial de participación; (7) convocatoria y confirmación; (8) alineación (titulares, suplentes, posiciones); (9) registro dinámico del partido en vivo; (10) catálogo **configurable** de eventos (inicio/fin, saque de meta, saque lateral, esquina, falta, **penal**, gol, tarjeta, fuera de juego, cambio…); (11) evento con minuto, equipo, jugador, observaciones; (12) corregir/anular **sin perder trazabilidad**; (13) marcador, cronología y estado en **tiempo real**; (14) estadísticas por jugador y por equipo; (15) anuncios y notificaciones; (16) comunidad; (17) publicaciones, comentarios y reacciones; (18) avisos de pruebas; (19) filtros por categoría, posición, fecha y ubicación; (20) moderación y reportes; (21) resumen IA solo con eventos registrados; (22) revisión y aprobación final del DT.

**Restricciones:** Android nativo, uso rápido en partidos; documentar reglas, permisos y privacidad; funcionar con datos incompletos/correcciones; mensualidades solo simuladas; comunidad con visibilidad, moderación, reporte y protección de menores; catálogo de eventos configurable y sincronizado en tiempo real; IA solo resumen (sin alineación sugerida, sin chatbot RAG); priorizar el **Core** (impacta la nota).

**Seis desafíos (no son CRUD; exigen evidencia real):** tiempo real; catálogo dinámico y trazabilidad; datos incompletos (jugador desconocido, minuto tardío, pérdida de conexión, **casos sucios probados y documentados**); resumen confiable (la IA no inventa, el DT revisa); privacidad de menores; comunidad segura.

**Stack del enunciado:** Android Studio, Kotlin, Compose, MVVM + ViewModel + StateFlow + Navigation Compose, (Firebase → **Supabase autorizado**), GitHub con ramas y PR.

---

## 4. BACKLOG (Parte 1) — 18 US y reglas

Reglas: **A.** Datos físicos y contactos de menores solo para su DT, ADM y PAD vinculado; PAD solo ve a su hijo; público/otros equipos ven nombre, posición y foto (si autorizada). **B.** Filtro de palabras altisonantes; botón «Reportar»; convocatorias de pruebas solo DT/ADM. **C.** Eventos obligatorios: inicio, fin, fin 1.er tiempo, inicio 2.º tiempo, gol, amarilla, roja, cambio; secundarios: falta, esquina, saque de meta, saque lateral, fuera de juego (**+ penal, tiro libre, atajada, despeje: añadidos por Aldo**); atributos: minuto, equipo, jugador (**opcional**), tipo, observaciones. **D.** IA: el prompt incluye única y exclusivamente la lista cronológica de eventos; el system prompt prohíbe inventar jugadas, clima o emociones; flujo borrador → edición → «Aprobar y Publicar».

| US | Nombre | Pts | Resp. original |
|---|---|---|---|
| 01 | Registro dinámico de partido en vivo (jugador opcional; marcador sincronizado sin recargar) | 8 | Mathias |
| 02 | Resumen IA (solo si «Finalizado»; texto editable; sin eventos inexistentes) | 5 | Chayna |
| 03 | Perfil de jugador (edad vs categoría; foto en storage; privacidad) | 3 | Jean Paul |
| 04 | Convocatoria y confirmación (push; confirmar/rechazar; estado en tiempo real) | 5 | Aldo |
| 05 | Entrenamientos, biblioteca reutilizable y asistencia | 5 | Chayna |
| 06 | Comunidad y moderación (reportar; **ocultar al superar 3 reportes**) | 8 | Chayna |
| 07 | Anuncios y cambios de horario (por equipo o academia; push; historial «Avisos») | 5 | Chayna |
| 08 | Equipos y categorías (asignar DT) | 3 | Jean Paul |
| 09 | Alineación (solo confirmados; no iniciar sin titulares mínimos) | 8 | Jean Paul |
| 10 | Mensualidades simuladas (Pagado/Pendiente; sin pasarela ni tarjetas; PAD ve estado) | 3 | Aldo |
| 11 | Estadísticas acumuladas (desde eventos; JUG solo las suyas; DT todo el equipo) | 8 | Aldo |
| 12 | Avisos de pruebas (fecha, hora, ubicación, posiciones; filtros; destacados) | 5 | Mathias |
| 13 | Calendario (solo el equipo del usuario; detalle sin salir) | 5 | Aldo |
| 14 | Historial de partidos y resultados (reciente primero; cronología) | 5 | Jean Paul |
| 15 | Vinculación padre↔jugador | 5 | Chayna |
| 16 | Documentos y autorizaciones (visibles solo PAD y ADM) | 5 | Chayna |
| 17 | Recordatorios automáticos (push; activar/desactivar; cambios inmediatos) | 3 | Jean Paul |
| 18 | Búsqueda y filtrado de jugadores | 5 | Mathias |

### 4.1 US nuevas propuestas (el enunciado permite agregarlas; el profesor pidió no presentar las obvias como login)
US-19 Autenticación, invitaciones y roles · US-20 Corrección/anulación de eventos con trazabilidad · US-21 Catálogo configurable de eventos · US-22 Panel de moderación y bloqueo · US-23 Evaluación documentada del resumen IA · US-24 Resiliencia offline del registro · US-25 Historial de participación · US-26 Estadísticas de equipo · US-27 Seguir academias/equipos y feed · US-28 Tarjetas de partido en vivo y sección «Partidos».

### 4.2 Resueltas
- Reportes: se oculta al **superar 3** reportes de **usuarios distintos** (4.º reporte), umbral en una constante/config en BD ✅.
- **Penal incluido** ✅ (penal convertido = gol).
- Textos de Firebase en las US → traducir a Supabase (Storage, tablas `match_events`, etc.) ✅. Las tablas se **crean según este documento**, tras cerrar las dudas de §13.

---

## 5. STACK Y ARQUITECTURA

### 5.1 Android
Kotlin, Compose, Material 3, **MVVM** (ViewModel + `StateFlow` + eventos one-shot), Navigation Compose (grafo por rol), capas `ui → presentation → domain → data`. Módulos por feature (paquetes o módulos Gradle). Recomendado 🟡: Hilt (o Koin), `supabase-kt` (Auth, Postgrest, Realtime, Storage, Functions), `kotlinx.serialization`, motor Ktor Android, Room (cola offline/caché), WorkManager, DataStore, Coil, Timber, `libs.versions.toml`. **Verifica versiones vigentes en documentación oficial; no las inventes.** Considerar Java 17 si las dependencias lo exigen. Compresión de imágenes antes de subir (Storage gratuito: 1 GB).

### 5.2 Backend (Supabase)
Auth (email + confirmación), PostgreSQL como fuente de verdad, **RLS en todo**, Realtime (marcador, cronología, convocatorias, notificaciones, catálogo), Storage privado con URLs firmadas, Edge Functions (canje de códigos, aprobaciones, **generación del resumen con Gemini**, push). Autorización con funciones helper `SECURITY DEFINER` con `search_path` fijo (`is_platform_admin`, `is_academy_member`, `has_academy_role`, `is_guardian_of`, `can_view_player_private`, `is_match_operator`…). Los roles nunca se leen de campos editables por el cliente. Migraciones versionadas en `supabase/migrations/`, tests en `supabase/tests/`, `seed.sql` ficticio.

**Entorno ✅ (decidido por Aldo):** trabajar **directo sobre el proyecto remoto** (está completamente vacío), siempre mediante **migraciones versionadas en el repo** (nunca a mano en Table Editor) para que sea reproducible. Antes de la demo final de 28/11–05/12, evaluar crear un segundo proyecto «dev». Avisar a Aldo de que el plan gratuito pausa el proyecto tras 7 días sin actividad (reactivarlo antes de presentar).

**Correo:** el SMTP integrado de Supabase es muy limitado; para correos reales de confirmación/solicitudes planificar SMTP propio (fase posterior). Para las pruebas de hoy usa cuentas de prueba de Aldo y revisa en el dashboard si conviene **desactivar temporalmente «Confirm email»** *solo con autorización explícita de Aldo* y reactivarlo después.

### 5.3 IA (Gemini)
Edge Function `generate-match-summary`; clave `GEMINI_API_KEY` y nombre del modelo `GEMINI_MODEL` como **secretos/variables** (verifica en la documentación vigente de Google AI Studio cuál es el modelo Flash disponible; no lo hardcodees). Nivel gratuito ✅ (suficiente para pruebas); advertencia: en ese nivel Google puede usar el contenido para mejorar sus productos → **no enviar datos personales** (ver §9.5). Manejar límites de tasa y errores con reintento y mensaje claro en la UI.

### 5.4 Calidad / DevOps
GitHub Actions (build, lint, tests, validación de migraciones), ramas `feature/*`, Conventional Commits, PR pequeños, nada directo a `main`. Pruebas: unitarias, integración, **autorización RLS (obligatorias)**, concurrencia/idempotencia de eventos, UI Compose, pruebas manuales documentadas en celular físico.

---

## 6. REGISTRO DE DECISIONES

| ID | Decisión | Estado |
|---|---|---|
| D-01 | Backend = Supabase; autorizado verbalmente por el profesor | ✅ |
| D-02 | Rama `rebuild/sportpro-v2`; PR a `main` solo al final y revisado | ✅ |
| D-03 | Package `com.sportpro.app`, minSdk 29 | ✅ |
| D-04 | Entorno: trabajar directo en el proyecto remoto con migraciones versionadas | ✅ |
| D-05 | IA = Gemini vía Google AI Studio, nivel gratuito, desde Edge Function; sin datos personales (§9.5) | ✅ |
| D-06 | Supabase plan gratuito basta por ahora | ✅ |
| D-07 | **Aprobación de vínculo menor↔apoderado: la hace el apoderado** | ✅ |
| D-08 | **<13 años: perfil gestionado solo por el apoderado** (lo crea en «Mis hijos»); lo ven apoderado y DT como cualquier jugador; **a los 13 puede tener cuenta propia reclamando su perfil, conservando datos e historial**. 13–17: cuenta propia con vínculo aprobado y permisos limitados. ≥18: autónomo. | ✅ (umbrales = decisión de producto; no es asesoría legal) |
| D-09 | Solo el apoderado crea perfiles de <13 (opción de que el ADM pre-registre queda como mejora futura) | ✅ |
| D-10 | N apoderados por jugador (uno principal) y N hijos por apoderado | 🟡 |
| D-11 | Unirse a un equipo mediante **código de invitación o QR** (revocable); el DT/ADM aprueba el ingreso | ✅ |
| D-12 | **Superadmin de plataforma** (Aldo; se asigna directo en BD, no es un rol elegible) y **ADM de academia** | ✅ |
| D-13 | Registro libre solo para **PAD y JUG**; **DT por invitación del ADM**; **ADM lo asigna el superadmin** | ✅ |
| D-14 | Formatos de partido: predefinidos 5, 7, 9, 11 y **personalizado de 5 a 11** jugadores en cancha; duración de tiempos configurable | ✅ |
| D-15 | **Rival registrado o externo** (externo: nombre, escudo por defecto y plantel escrito a mano; sin estadísticas para el rival externo) | ✅ |
| D-16 | **Operador del partido**: el DT designa a un asistente, apoderado u otro miembro; un solo operador activo a la vez | ✅ |
| D-17 | Eventos con **jugador opcional**, editable luego (§9.4); no cuenta para estadísticas hasta asignarse | ✅ |
| D-18 | Estadísticas: goles, **asistencias de gol**, tarjetas, faltas, atajadas, partidos, y **asistencia a entrenamientos**; se consolidan al **Finalizar** el partido y se recalculan si se corrige | ✅ |
| D-19 | Partido en vivo visible en tiempo real para apoderados y **seguidores** de la academia/equipo (no hace falta recargar) | ✅ |
| D-20 | Reacciones: **like, corazón, fuego, pelota** | ✅ |
| D-21 | Navegación y estética **inspiradas en Instagram** (§10) | ✅ |
| D-22 | Eventos append-only con revisiones; anular = marca lógica; idempotencia por `client_event_id`; `seq` por partido en servidor | 🟡 |
| D-23 | Marcador/estadísticas derivados de eventos vigentes | 🟡 |
| D-24 | Reloj del partido basado en timestamps del servidor | 🟡 |
| D-25 | Resumen IA: seudonimización de jugadores hacia el modelo, regeneración limitada (3), edición obligatoria antes de publicar | 🟡 |
| D-26 | Fotos y nombres de menores en publicaciones públicas: foto solo si autorizada; menores como «Nombre + inicial» | 🟡 |
| D-27 | Mensualidades: estados Pendiente / En revisión / Pagado / Vencido + flujo «Pagar (simulación)» sin tarjetas ni datos bancarios | ✅ (solo fingida) |
| D-28 | Priorización por niveles (§11.1) | 🟡 (pendiente de que Aldo confirme) |
| D-29 | Icono de **Perfil** en la **esquina inferior derecha** de la barra (como Instagram); campana de notificaciones arriba a la derecha; «+» arriba a la izquierda | ✅ (confirmado por Aldo) |

---

## 7. IDENTIDAD, VÍNCULOS FAMILIARES Y ROLES

### 7.1 Separación clave: perfil deportivo ≠ cuenta
- `players` = identidad deportiva; **puede existir sin cuenta** (`user_id` NULL).
- Menores de 13: el apoderado crea el perfil en **«Mis hijos»**; DT y apoderado ven estadísticas y datos como los de cualquier jugador.
- A los 13: el apoderado **invita** al hijo; el hijo crea su cuenta y **reclama** el perfil (historial intacto, vínculo ya aprobado).
- 13–17 que se registra por su cuenta: ingresa fecha de nacimiento → la app detecta que es menor → pide **código/QR del apoderado** → solicitud `pending` → el apoderado recibe notificación interna (y correo cuando haya SMTP) con el nombre del solicitante → **acepta o rechaza**. Mientras esté pendiente solo ve una pantalla de estado (sin datos de equipos).
- ≥18: cuenta autónoma; al cumplir 18 el acceso familiar a datos privados termina salvo autorización del jugador.
- Códigos de apoderado: aleatorios de alta entropía (formato legible tipo `SP-XXXX-XXXX`), revocables/regenerables, sin datos personales, canje en Edge Function/RPC con rate limiting, estados `pending → approved | rejected | expired | revoked`, auditoría.
- Un correo confirmado no prueba parentesco: para datos sensibles (físicos, contactos, documentos) el ADM de la academia puede validar al apoderado ❓ (a decidir; por ahora, acceso del apoderado vinculado y aprobado).
- Panel «Mis hijos»: tarjetas con foto autorizada, equipo, categoría, posición, próximos entrenamientos/partidos, convocatorias, asistencia, estadísticas. Nunca contactos de otros jugadores ni datos de academias ajenas.
- Desvinculación/cambio de apoderado: procedimiento seguro y auditado (no dejar a un menor sin apoderado cuando se requiere uno).
> Nota legal: Perú tiene normativa de protección de datos personales; recomienda a Aldo **revisión con asesoría competente** antes de un lanzamiento comercial. Documenta supuestos; no des asesoría legal definitiva.

### 7.2 Roles y quién puede obtenerlos
| Rol | Cómo se obtiene | Qué puede hacer |
|---|---|---|
| **Superadmin** | Asignado por Aldo directo en BD (`platform_admins`, sin escritura desde cliente) | Crear/verificar academias, asignar el primer ADM por correo, moderación de última instancia, panel de plataforma oculto |
| **ADM (de una academia)** | Lo asigna el superadmin | Equipos y categorías, invitar DT, aprobar ingresos, validar documentos, mensualidades, publicar pruebas **verificadas**, moderar su academia, ver todo lo de su academia |
| **DT** | **Por invitación del ADM** (correo/código) | Plantel, entrenamientos, convocatorias, alineaciones, partidos, designar operador, resumen IA, anuncios, publicar como equipo |
| **Operador de partido** | Designado por el DT para un partido (asistente, apoderado u otro miembro) | Registrar eventos del partido designado únicamente |
| **PAD** | Registro libre | «Mis hijos», convocatorias/confirmaciones de sus hijos, estado de cuenta, documentos, ver partidos |
| **JUG** | Registro libre (con vínculo si es menor) | Perfil, calendario, convocatorias, estadísticas propias, comunidad según edad |

Un usuario puede tener varios roles en distintos contextos (p. ej. DT en una academia y PAD de un jugador). Los permisos son por **membresía** (usuario × academia × rol).

---

## 8. PRIVACIDAD Y SEGURIDAD (objetivos — aún no implementados)

1. Perfiles de menores no públicos por defecto; público ve nombre, posición y foto **solo si autorizada**.
2. Datos físicos/contactos en tabla separada (`player_private_data`) con RLS estricta (DT del equipo, ADM de la academia, PAD vinculado y aprobado, jugador según edad).
3. Storage privado, URLs firmadas de corta duración, consentimiento de foto registrado.
4. Nadie se auto-asciende de rol; cambios solo por funciones controladas y auditadas. Conocer un UUID no da acceso; ser miembro de una academia no da acceso a otras.
5. Códigos con rate limiting; `audit_log` para acciones críticas (roles, vínculos, anulación/corrección de eventos, moderación, mensualidades, aprobación de resúmenes).
6. Comunidad: **sin chats privados** (por ahora), filtro de lenguaje (servidor + cliente), reportes, bloqueo, ocultamiento automático, cola de moderación, menores de 13 sin presencia social, jugadores 13+ publican solo dentro de su equipo; publicaciones públicas solo de cuentas institucionales (academia/equipo); convocatorias de pruebas solo de academias verificadas.
7. Pruebas automatizadas de autorización (matriz rol × recurso × operación).

---

## 9. MÓDULOS CLAVE

### 9.1 Autenticación y contexto
Registro (con fecha de nacimiento), login, confirmación, recuperación, sesión persistente. Tras login: selector de contexto (academia + rol) si hay varios. Invitaciones (DT por correo/código del ADM; ingreso de jugadores por código/QR del equipo).

### 9.2 Academias, equipos, jugadores
ADM crea academia/equipos/categorías (con formato de juego), asigna DT. Perfil de jugador con validación edad↔categoría en **servidor**. Búsqueda/filtros. Dorsal por plantel (`jersey_number`). Concepto de **temporada** (p. ej. 2026) para filtrar estadísticas.

### 9.3 Entrenamientos y asistencia
Biblioteca de ejercicios, sesiones (objetivos, duración, ejercicios), asistencia, historial y % de participación.

### 9.4 Convocatoria, alineación y partido en vivo (**núcleo del proyecto**)
- **Formatos:** presets 5, 7, 9, 11 + personalizado (5–11 jugadores en cancha); tiempos y minutos configurables. El inicio exige tantos titulares como jugadores en cancha del formato (US-09), con titulares y suplentes.
- **Rival:** equipo registrado en SportPro (con plantel) o **externo** (nombre, escudo por defecto, plantel manual con dorsal y nombre). Estadísticas solo para jugadores registrados.
- **Operador:** el DT designa a un miembro; un solo operador activo (bloqueo con transferencia). Solo ese usuario inserta eventos.
- **Flujo de registro (una mano, rápido):** botón de evento con **ícono/dibujo** → elegir **equipo A/B** → elegir **jugador** (lista del equipo, por dorsal o nombre; opción **«Sin identificar»**) → **minuto automático** (editable) → **se guarda al instante** como línea tipo `Tarjeta amarilla · Manchester · Jorgito Valle · 23'`, con **snackbar «Deshacer»** de unos segundos. Pantalla siempre encendida, háptica, indicador de conexión/pendientes.
- **Datos por tipo (catálogo dinámico):** gol (anotador, asistente opcional, en contra, de penal), penal (resultado: convertido/fallado/atajado), cambio (sale/entra), tarjetas, falta (autor, opcional receptor), atajada (portero), esquina/tiro libre/saque de meta/lateral/fuera de juego/despeje (equipo, jugador opcional).
- **Jugador opcional (✅):** el enunciado y la Regla C lo exigen para datos reales (el operador puede no identificar la jugada, o el rival es externo). El evento queda con marca **«Sin jugador asignado»**, visible en la cronología y **editable luego**; no suma a estadísticas individuales hasta asignarse.
- **Reloj:** controles de inicio, pausa de entretiempo, 2.º tiempo, fin y tiempo añadido (45+2). Cada dispositivo calcula el minuto desde timestamps del servidor.
- **Modelo:** `event_types` (por academia; campos y reglas por tipo; grupo principal/secundario; activable; sincronizado en Realtime), `match_events` (append-only: `id`, `match_id`, `client_event_id` único, `seq`, `period`, `minute`, `minute_extra`, `team_side`, `player_id` NULL, `external_player_id` NULL, `event_type_id`, `payload` jsonb, `notes`, `status` activo/anulado, `created_by`, `created_at`, `recorded_late`), `match_event_revisions` (snapshot previo, autor, motivo, fecha). Marcador y estado **derivados**.
- **Estadísticas:** se consolidan al pasar el partido a **Finalizado**; si el DT corrige después, se recalculan sin duplicar goles.
- **Offline:** cola en Room, reintento con WorkManager, idempotencia por `client_event_id`, reconciliación por `seq`, manejo de cierre de app con cola pendiente, conflicto de operadores resuelto con el bloqueo de operador.
- **Casos sucios a probar y documentar (obligatorio):** evento duplicado, jugador sin identificar, minuto tardío/fuera de orden, corrección y anulación de gol, pérdida de conexión durante el registro, cierre de app con cola pendiente, dos dispositivos, catálogo modificado durante el partido, partido finalizado con huecos. Cada caso → test o script + evidencia (captura/video).

### 9.5 Resumen narrativo con IA (Gemini)
- **Dónde y cómo se accede:** al finalizar un partido aparece su **tarjeta «Finalizado»** (escudos, categoría, marcador). **Toque** → cronología ordenada de eventos. **Visible a todo DT del equipo:** botón **«Resumen IA»** en el detalle del partido (la **pulsación larga** sobre la tarjeta es un atajo; no la única vía). Solo habilitado si el partido está `Finalizado`.
- **Pantalla de generación** con estado de progreso → texto generado en **cuadro editable**. Acciones: **Regenerar** (máx. 3 por partido), **Editar**, **Aprobar y publicar**.
- **Antes de publicar:** elegir visibilidad (**Todos** o **Solo mi equipo**; opcional «Mi academia»). Al publicar se crea automáticamente una **publicación** con el texto + **imagen de resultado** (tarjeta renderizada en la app: escudos, marcador, categoría, fecha, subida a Storage) y la etiqueta «Resumen generado con IA y revisado por el entrenador».
- **Edge Function `generate-match-summary`:** lee en el servidor los eventos vigentes (no confía en el cliente), construye un JSON cronológico **sin datos personales**: jugadores como **seudónimos** (`J1`, `J2`… + dorsal + lado del equipo); los nombres de club no son datos sensibles. System prompt restrictivo: usar solo los eventos; no inventar jugadas, clima, emociones, jugadores ni minutos; omitir lo que falte. La IA **no necesita saber** si alguien es menor: no recibe nombres ni edades. Al volver el texto, el servidor **sustituye los seudónimos** por nombres de exhibición (para menores en visibilidad pública: «Nombre + inicial»).
- **Verificación anti-alucinación:** validar que seudónimos, minutos, marcador y tipos de evento citados existan en los eventos; rechazar o marcar discrepancias. Guardar `match_summaries` (borrador/editado/aprobado) y `match_summary_reviews`.
- **Evaluación documentada** (US-23): utilidad, omisiones, errores, información inventada, correcciones, quién evaluó.
- Proveedor: Gemini en AI Studio, nivel gratuito (límites de tasa); clave solo como secreto de Supabase; modelo configurable.

### 9.6 Mensualidades simuladas (única parte no pulida)
Estados Pendiente / En revisión / Pagado / Vencido por jugador y mes; ADM marca estados con auditoría; PAD ve el estado de cuenta de su hijo; flujo visual «Pagar (simulación)» con medio ficticio (efectivo/Yape/transferencia) y número de operación libre que deja la cuota «En revisión». **Sin pasarela, sin tarjetas, sin datos bancarios**, con aviso «Modo simulación». No invertir pulido aquí.

### 9.7 Comunidad tipo Instagram
- Feed de publicaciones (fotos + texto) de las **academias/equipos que sigues** (`follows`); **Me gusta + comentarios + reacciones (like, corazón, fuego, pelota)**; perfil de academia/equipo con sus publicaciones; **sin mensajes directos**.
- Visibilidad por alcance: equipo / academia / público. Quién publica: DT/ADM como equipo o academia (público); JUG 13+ y PAD solo dentro de su equipo; <13 sin presencia social.
- Reportar, bloquear, filtro de palabras, ocultamiento al superar 3 reportes, cola de moderación del ADM, superadmin como instancia final.
- Avisos de pruebas destacados con filtros (categoría, posición, fecha, ubicación), solo de academias verificadas.
- Rendimiento: paginación por cursor, miniaturas, caché de imágenes (Coil), compresión antes de subir, indicador de nuevas publicaciones.

### 9.8 Tarjetas de partido, sección «Partidos» y franja de «En vivo»
- **Tarjeta de partido** (referencia: tarjeta con escudos a los lados, marcador al centro, estado y fecha arriba, nombre bajo cada escudo): escudos (**imagen predeterminada para rival externo**), categoría, marcador, estado (**Programado / EN VIVO con minuto / Entretiempo / Finalizado**), fecha y sede. Toque → cronología en **tiempo real** (se actualiza sin recargar).
- **Sección «Partidos»:** arriba **En vivo**, luego próximos y **finalizados** (historial, el más reciente primero). Muestra los de las academias/equipos que sigue el usuario y los de sus hijos (si es apoderado); el DT ve además los de su equipo y las acciones de gestión.
- **Franja superior de «En vivo»** (como las historias de Instagram): círculos con el escudo y marca «EN VIVO» + minuto, de los clubes que sigue o, si es apoderado, de los equipos de sus hijos. No hay «historias» de usuarios.
- Visibilidad: equipo, apoderados y seguidores; opción de partido **privado** ❓.

### 9.9 Notificaciones, anuncios, calendario
Campana (esquina superior) con dos pestañas: **Notificaciones** y **Avisos** (el «buzón» con mensajes y noticias del DT para todo el equipo; historial). Realtime para lo in-app; push FCM en fase posterior. Calendario (partidos, entrenamientos, reuniones) del equipo del usuario, con detalle in situ. Recordatorios configurables; cambios de horario inmediatos.

### 9.10 Estadísticas
Por jugador y por equipo, derivadas de eventos vigentes de partidos finalizados (D-18); visibilidad: JUG las suyas, DT las del equipo, PAD las de su hijo. Historial de asistencia a entrenamientos.

---

## 10. DISEÑO UX/UI

- **Referencia de navegación: Instagram** (✅). Barra inferior con íconos; arriba a la izquierda «+» (crear publicación, según rol) y a la derecha la **campana de notificaciones/avisos**; franja de partidos en vivo sobre el feed.
- **Barra inferior propuesta 🟡** (sin DM de Instagram): **Inicio** (feed + franja en vivo) · **Partidos** · **Explorar** (buscar academias, equipos, pruebas) · **Calendario** · **Perfil** (mis estadísticas, «Mis hijos» para PAD, ajustes). Acción rápida para DT/operador: registrar partido desde «Partidos». **Perfil = último icono, abajo a la derecha (D-29 ✅).**
- Estética oscura (verde petróleo + menta propuesta, **sin aprobar**), minimalista, deportiva, tarjetas redondeadas, íconos propios vectoriales para eventos («dibujos interactivos»: tarjeta roja/amarilla, esquina, gol, tiro libre, penal, atajada, despeje, cambio, falta, fuera de juego…).
- Sistema de diseño con tokens y componentes reutilizables: botones, campos, tarjetas de partido, tarjetas de publicación, chips, listas, top bars, bottom nav, diálogos, snackbars, **skeleton loaders**, estados vacíos con CTA, estados de error con reintento. Accesibilidad AA, objetivos táctiles ≥ 48 dp, `contentDescription`, escala de fuente. Animaciones discretas; respetar «reducir animaciones». Pantallas de partido pensadas para exteriores (alto contraste, botones grandes).

---

## 11. FASES

Cada fase termina con: qué se hizo · cómo se verificó · riesgos · «Para la defensa» · **espera aprobación de Aldo** (excepto bloques de §12).

0. Auditoría (solo lectura, matriz de trazabilidad requisito → US → pantalla → tabla → test) · 1. Cierre de decisiones ❓ · 2. Modelo de datos (ER Mermaid, diccionario, matriz de permisos; **aprobación antes del SQL definitivo**) · 3. Migraciones + RLS + tests · 4. Conexión Android↔Supabase + auth · 5. Sistema de diseño y navegación · 6. Gestión (academias, equipos, jugadores, vínculos, entrenamientos, mensualidades simuladas) · 7. Partido (convocatoria, alineación, catálogo, registro en vivo, Realtime, correcciones, offline) · 8. Estadísticas, historial, calendario, notificaciones · 9. Comunidad y moderación · 10. IA narrativa + evaluación · 11. Calidad (pruebas completas, seguridad, rendimiento, accesibilidad, CI) · 12. Entrega (documentación, README, video, guion de demo, datos de demo ficticios, PR final) · 13. Preparación comercial.

### 11.1 Prioridad por niveles 🟡 (hasta 28/11)
- **Nivel 1 (Core):** auth y roles, academias/equipos/jugadores, vinculación familiar, convocatoria, alineación, partido en vivo con correcciones y offline básico, estadísticas, resumen IA.
- **Nivel 2:** feed con moderación, calendario, buzón, push.
- **Nivel 3 (pulido):** reacciones adicionales, recomendaciones de seguir, animaciones extra, tema claro.

---

## 12. RUTA URGENTE — PRESENTACIÓN DE HOY (Semana 12)

**Contexto:** hoy hay presentación (confirmado). El repo es una base limpia; Supabase está vacío; Android no está conectado. **No prometas lo que no puedas verificar. Lo que no funcione no se presenta como funcional** (puede mostrarse como prototipo, etiquetado así).

**Evidencia que exige el enunciado para hoy:** video continuo de un partido de práctica real con eventos registrados desde el celular · sincronización en tiempo real entre dispositivos · casos sucios (jugador sin identificar, corrección, pérdida de conexión) · primera versión del resumen con IA.

**Corte vertical objetivo (en este orden; cada paso se verifica en un celular físico antes de pasar al siguiente):**
1. **Conexión:** dependencias Supabase, `BuildConfig` desde `local.properties`, permiso INTERNET, prueba real contra el backend.
2. **Esquema mínimo real con RLS — YA PREPARADO (Aldo lo trae adjunto)**: `supabase/migrations/20261107000000_sportpro_core.sql` (probada en Postgres 16 local con un stub de Supabase, **NO probada aún en el proyecto remoto**), `supabase/tests/smoke_test.sql` (solo para una BD de pruebas limpia, nunca el proyecto real), `supabase/seed_demo.sql` (datos ficticios; se editan 2 correos) y `supabase/functions/generate-match-summary/index.ts` (**sin probar**). Primero ejecútala/valídala contra el proyecto remoto con autorización de Aldo y corrige lo que falle; no la reescribas desde cero. Cubre: perfiles, academias, membresías, equipos, jugadores, vínculos de apoderado, convocatorias, alineación, partidos, eventos con revisiones, resúmenes IA, auditoría, RPCs y RLS. **No cubre aún** (hacer después): publicaciones/comunidad, entrenamientos, mensualidades simuladas, códigos de invitación/QR, solicitudes de vínculo 13–17, notificaciones, documentos. Esquema original previsto: `profiles`, `academies`, `academy_memberships`, `teams`, `players`, `team_players`, `matches`, `event_types` (+ seed del catálogo base con penal), `match_events`, `match_event_revisions`, `match_summaries`, `audit_log`, `platform_admins`, vistas de marcador y estadísticas. Todo lo demás queda para fases posteriores.
3. **Auth mínima:** registro/login y contexto de rol; datos de prueba ficticios (dos equipos, plantel con dorsales, un operador).
4. **Crear partido** (formato, equipos A/B, rival externo o registrado) → **iniciar** → **registrar eventos** con el flujo de §9.4 → corrección/anulación con revisión → **finalizar**.
5. **Tiempo real:** un segundo dispositivo (seguidor/apoderado de prueba) ve marcador y cronología sin recargar.
6. **Casos sucios:** jugador sin identificar + edición posterior, evento corregido/anulado, modo avión durante el registro y reenvío sin duplicados.
7. **Resumen IA:** Edge Function con Gemini (§9.5), pantalla editable y publicación simple.
8. **Presentación:** tarjeta de partido y flujo de gran impacto visual con el estilo de §10; el resto del producto (comunidad, calendario, etc.) se muestra **como prototipo etiquetado**, no como funcional.

**Reglas de la ruta urgente:** commits pequeños y frecuentes en `rebuild/sportpro-v2`; checkpoints para decidir recortes (si algo no funciona, recortar alcance antes que mentir); producir al final un **resumen honesto** de qué funciona, qué es prototipo y qué falta, y una **lista de pasos para el video/demo**. Cada paso con «Para la defensa». Si Aldo no tiene la clave publishable, la clave de Gemini o el CLI de Supabase, indícale cómo obtenerlos sin pedirle que los pegue en el chat ni en el repo.

---

## 13. PREGUNTAS ABIERTAS

1. ¿Qué evidencia ya existe (video del partido real, otros dispositivos)? *(La presentación es hoy 07/11 a las 4 pm: confirmado.)*
2. ¿El ADM valida al apoderado antes de que vea datos sensibles, o basta el vínculo aprobado?
3. ¿Partido privado opcional además de público/seguidores?
4. ¿Aprobada la priorización por niveles (§11.1)?
5. ¿Número de apoderados por jugador (D-10) y desvinculación?

---

## 14. PRIMERA TAREA PARA TI AHORA

1. Lee este documento completo.
2. Ejecuta una **Fase 0 mínima** (solo lectura, rápida): inspecciona repo y entorno, compara con §2, verifica qué herramientas hay (Gradle, Supabase CLI, Node, dispositivo conectado).
3. Dile a Aldo, en pocas líneas: qué entendiste, qué diferencias hay, qué necesitas de él (clave publishable, clave de Gemini, hora de la presentación) y propón el **plan concreto de §12 con checkpoints**.
4. Espera su visto bueno y ejecuta §12 por bloques verificados.
