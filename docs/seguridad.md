# Revisión final de seguridad

Revisión hecha el 16 de noviembre de 2026 sobre la rama `rebuild/sportpro-v2`, con las 13 migraciones aplicadas en un Postgres local.

## Resumen

| Área | Resultado |
|---|---|
| Claves en el repositorio y en el historial de git | Sin claves secretas |
| Row Level Security | Activado en las 35 tablas |
| Permisos del rol anónimo | Ninguno sobre tablas; una sola función permitida |
| Hallazgos | 3, todos corregidos en la migración 013 |

## 1. Claves

Se buscaron patrones de claves en los archivos versionados y en todo el historial de git.

- **Archivos actuales:** no hay ninguna clave, ni URL de proyecto, ni contraseña.
- **Historial:** aparece una clave en un commit antiguo de octubre de 2026 (archivo `SupabaseClient.kt` de la primera versión). Se comprobó su tipo: es la clave **anónima**, que es pública por diseño. No da acceso por sí sola; los datos los protege RLS. No hay ninguna clave secreta, `service_role`, contraseña de Postgres ni clave de Gemini en el historial.
- **Cómo llegan las claves a la app:** `app/build.gradle.kts` lee `SUPABASE_URL` y `SUPABASE_PUBLISHABLE_KEY` de `local.properties`, que está en `.gitignore`.
- **Clave de Gemini:** la Edge Function la lee de sus secretos (`GEMINI_API_KEY`). No está en el código.

Recomendación: si el proyecto de Supabase actual es el mismo que usaba aquella primera versión, conviene rotar la clave anónima antigua desde **Project Settings → API**. No es urgente, porque esa clave es pública, pero deja el historial sin ninguna clave vigente.

## 2. Auditoría automática del esquema

`supabase/test/security_audit.sql` revisa el catálogo de Postgres después de aplicar las migraciones. Falla si alguna regla no se cumple. Lo ejecuta `supabase/test/run_local.sh`.

| # | Regla | Estado |
|---|---|---|
| 1 | Todas las tablas de `public` tienen RLS activado | OK |
| 2 | El rol anónimo no tiene permisos sobre ninguna tabla | OK |
| 3 | El rol anónimo no tiene permisos sobre ninguna columna | OK |
| 4 | El rol anónimo solo puede ejecutar `check_invitation` | OK |
| 5 | Un usuario con sesión no tiene `TRUNCATE`, `TRIGGER` ni `REFERENCES` | OK |
| 6 | Las tablas sensibles solo se escriben por funciones | OK |
| 7 | No hay permiso de borrado sobre jugadores, plantel, partidos ni eventos | OK |
| 8 | Toda función `SECURITY DEFINER` fija su `search_path` | OK |
| 9 | Las funciones internas y de trigger no son ejecutables desde la app | OK |
| 10 | El bucket `documents` es privado | OK |
| 11 | No hay políticas de modificación ni borrado sobre los documentos | OK |

`check_invitation` es la única excepción a propósito: valida un código de invitación en la pantalla de registro, antes de que exista una sesión. Solo devuelve si el código es válido y a qué academia corresponde.

## 3. Hallazgos y correcciones

### Hallazgo 1: permisos de más en `invitations` y `notifications`

- **Qué pasaba.** Estas dos tablas, creadas en la migración 003, nunca recibieron el `revoke` inicial. Supabase otorga por defecto todos los permisos de tabla a los roles `anon` y `authenticated`.
- **Impacto real.** RLS impedía que el rol anónimo leyera filas, así que no hubo fuga de datos. Pero un usuario con sesión podía cambiar el título o el texto de sus propias notificaciones, no solo marcarlas como leídas.
- **Corrección.** Migración 013: se revocan todos los permisos y se otorga solo lectura; en `notifications`, además, actualizar únicamente la columna `read_at`.

### Hallazgo 2: un administrador podía cambiar el estado de su academia

- **Qué pasaba.** La política de actualización de `academies` permite al administrador editar su academia, y la columna `status` estaba entre las editables.
- **Impacto real.** Una academia suspendida por el superadmin podía volver a marcarse como verificada por su propio administrador. Se reprodujo en la base local antes de corregirlo.
- **Corrección.** Migración 013: un trigger rechaza cualquier cambio de estado que no venga del superadmin.

### Hallazgo 3: funciones internas ejecutables por el rol anónimo

- **Qué pasaba.** Algunas funciones auxiliares y de trigger creadas después de la migración 001 conservaban el permiso de ejecución por defecto.
- **Impacto real.** Bajo: no devolvían datos sensibles y las funciones de trigger no se pueden invocar de forma útil. Aun así, eran superficie expuesta sin motivo.
- **Corrección.** Migración 013: se les quita el permiso.

Además, en la migración 012 se había cerrado otro hueco encontrado al trabajar el plantel: la app tenía permiso de borrado sobre `team_players` y podía dar de baja sin motivo.

## 4. Revisión de las tablas nuevas

Tablas agregadas en las migraciones 008 a 011, con su política de lectura. Ninguna acepta escrituras directas.

| Tabla | Quién puede leer |
|---|---|
| `fee_plans`, `fee_players`, `fee_charges` | El DT o administrador del equipo, y quien paga por ese jugador |
| `fee_payments` | Quien puede leer el cobro al que pertenece |
| `player_documents` | Quien sube (apoderado o jugador adulto), el staff de un equipo donde el jugador está activo y el administrador de esa academia |
| `notification_prefs` | Solo su dueño |
| `tryouts` | Cualquier usuario si el aviso está abierto y la academia verificada; quien lo gestiona; quien se inscribió |
| `tryout_applications` | Quien envió la inscripción y quien gestiona el aviso |

Cada política está cubierta por la prueba de humo con casos de «quién ve» y «quién no ve», incluido un usuario ajeno a la academia.

## 5. Límites de esta revisión

- **Entorno local.** La auditoría corre sobre un Postgres que imita lo mínimo de Supabase. Los permisos por defecto se configuraron igual que en Supabase, pero la comprobación definitiva es ejecutar la migración 013 en el proyecto real.
- **No es una prueba de penetración.** Se revisaron permisos, políticas y flujos; no se atacó la app ni la red.
- **Configuración del panel.** No se revisaron ajustes que viven fuera del repositorio, como los proveedores de inicio de sesión activos o los límites de envío de correo.

## 6. Cómo repetir la revisión

Con Docker Desktop abierto, desde Git Bash:

```bash
bash supabase/test/run_local.sh
```

La salida debe terminar con `AUDITORÍA DE SEGURIDAD: SIN HALLAZGOS` y `TODAS LAS PRUEBAS PASARON`.

Para comprobarlo en el proyecto real, ejecuta estas tres consultas en el **SQL Editor** de Supabase después de aplicar la migración 013. Las tres deben devolver cero filas.

Tablas sin RLS:

```sql
select c.relname
from pg_class c join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public' and c.relkind = 'r' and not c.relrowsecurity;
```

Permisos del rol anónimo sobre tablas:

```sql
select table_name, privilege_type
from information_schema.role_table_grants
where grantee = 'anon' and table_schema = 'public';
```

Funciones que el rol anónimo puede ejecutar, aparte de `check_invitation`:

```sql
select p.proname
from pg_proc p join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public'
  and has_function_privilege('anon', p.oid, 'execute')
  and p.proname <> 'check_invitation';
```
