# SportPro — Cómo conectar Android Studio con Supabase (paso a paso)

Orden recomendado: **1) clave → 2) base de datos → 3) cuentas y datos demo → 4) Android → 5) IA**.

---

## 1. Obtener la clave *publishable* (2 minutos)

1. Entra a https://supabase.com/dashboard y abre tu proyecto (`mfrdvmodjziuknlptjhf`).
2. Arriba en la pantalla del proyecto pulsa **Connect**: ahí aparecen la **Project URL** y la **Publishable key**.
   - Alternativa: **Project Settings → API Keys**. En la pestaña de claves nuevas busca **Publishable key** (empieza con `sb_publishable_…`).
   - Si no hay ninguna, pulsa **Create new API Keys** y copia la *Publishable key*.
3. Copia dos cosas:
   - URL: `https://mfrdvmodjziuknlptjhf.supabase.co`
   - Clave: `sb_publishable_...`

**Qué sí y qué no:**
- La *publishable key* **está diseñada para ir dentro de la app** (Supabase la documenta como segura de exponer). Puedes dársela a Claude Code.
- **NUNCA** compartas la *Secret key* (`sb_secret_…`), ni `service_role`, ni la contraseña de Postgres. Esas no se pegan en chats ni en el repo.

---

## 2. Crear la base de datos (3 minutos)

1. Dashboard → **SQL Editor → New query**.
2. Abre `supabase/migrations/20261107000000_sportpro_core.sql`, copia TODO y pégalo. **Run**.
   - Debe terminar sin errores (es transaccional: si algo falla, no se aplica nada).
3. Verifica: **Table Editor** debe mostrar ~21 tablas y `event_types` con **17 filas** (catálogo base: gol, penal, tarjetas, cambio, esquina, etc.).
4. Guarda el mismo archivo en tu repo en `supabase/migrations/` (así queda versionado).

> Más adelante, cuando uses el CLI (`supabase link` + `supabase db push`), puede intentar volver a aplicar esta migración. Si ocurre: `supabase migration repair --status applied 20261107000000`.

---

## 3. Cuentas y datos de demo

1. Autenticación: tienes *Confirm email* activado. Para hoy elige UNA:
   - **A (rápida):** Dashboard → **Authentication → Users → Add user → Create new user**, marca **Auto Confirm User**. Crea 2 usuarios (el tuyo y uno para el 2.º celular).
   - **B:** registrarte desde la app y confirmar por correo.
   - (Los usuarios creados desde el dashboard no traen fecha de nacimiento; el script de demo se la pone.)
2. Abre `supabase/seed_demo.sql`, cambia los dos correos (`v_main_email`, `v_second_email`) y ejecútalo en el SQL Editor.
3. Resultado: academia demo verificada, 2 equipos (Halcones y Leones Sub-15) con 9 jugadores ficticios cada uno, tú como **superadmin + ADM + DT + operador**, tu 2.ª cuenta como **apoderada de un jugador y seguidora**, y **un partido listo para iniciar** (convocados confirmados, 7 titulares por lado).

---

## 4. Android Studio

### 4.1 `local.properties` (NO se sube a GitHub; ya está en `.gitignore`)
```properties
SUPABASE_URL=https://mfrdvmodjziuknlptjhf.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_PEGA_AQUI
```

### 4.2 `app/AndroidManifest.xml`
```xml
<uses-permission android:name="android.permission.INTERNET" />
```

### 4.3 `app/build.gradle.kts`
Versiones: **no las copies de memoria**. Usa la última de https://github.com/supabase-community/supabase-kt/releases (para `SUPABASE_VERSION`) y la versión de Ktor que esa release recomienda (`KTOR_VERSION`). El plugin de serialización debe tener **la misma versión de Kotlin** que tu proyecto.
```kotlin
plugins {
    // ... los plugins que ya tienes ...
    id("org.jetbrains.kotlin.plugin.serialization") version "TU_VERSION_DE_KOTLIN"
}

android {
    buildFeatures { buildConfig = true }
    defaultConfig {
        val props = java.util.Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) f.inputStream().use { load(it) }
        }
        buildConfigField("String", "SUPABASE_URL", "\"${props.getProperty("SUPABASE_URL", "")}\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"${props.getProperty("SUPABASE_PUBLISHABLE_KEY", "")}\"")
    }
}

dependencies {
    implementation(platform("io.github.jan-tennert.supabase:bom:SUPABASE_VERSION"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")
    implementation("io.github.jan-tennert.supabase:functions-kt")
    // Motor HTTP: usa OkHttp (soporta WebSockets, necesarios para Realtime)
    implementation("io.ktor:ktor-client-okhttp:KTOR_VERSION")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:LA_QUE_PIDA_SUPABASE_KT")
}
```
Luego **Sync Project**.

### 4.4 Cliente único
```kotlin
// data/remote/SupabaseProvider.kt
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.functions.Functions

object SupabaseProvider {
    val client = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
    ) {
        install(Auth)
        install(Postgrest)
        install(Realtime)
        install(Storage)
        install(Functions)
    }
}
```

### 4.5 Prueba de conexión (debe devolver 17)
```kotlin
// 1) iniciar sesión con una cuenta de demo
SupabaseProvider.client.auth.signInWith(Email) { email = "tu@correo"; password = "..." }
// 2) leer el catálogo (RLS exige usuario autenticado)
@Serializable data class EventType(val id: String, val code: String, val name: String)
val tipos = SupabaseProvider.client.from("event_types").select().decodeList<EventType>()
Log.d("SportPro", "Catálogo: ${tipos.size} tipos")   // → 17
```
Si ves 0 filas o error 401/403: la sesión no se inició o la clave está mal. Si ves un error de red: falta el permiso INTERNET.

### 4.6 Registro desde la app (enviando fecha de nacimiento)
El servidor **rechaza menores de 13** y usa la fecha para las reglas de privacidad:
```kotlin
SupabaseProvider.client.auth.signUpWith(Email) {
    email = correo; password = clave
    data = buildJsonObject { put("full_name", nombre); put("birth_date", "1999-05-01") }  // yyyy-MM-dd
}
```

### 4.7 Cómo habla la app con la base (reglas importantes)
- **Registrar evento:** `INSERT` en `match_events` con un `client_event_id` (UUID generado en el celular). Si hay un reenvío, el servidor devuelve error de duplicado → trátalo como **éxito** (así no se duplica nada tras una caída de red).
- **Iniciar / medio tiempo / 2.º tiempo / finalizar:** RPC `change_match_state(p_match, p_action)` con `'start' | 'halftime' | 'second_half' | 'finish'`. Inicia solo con la alineación completa.
- **Corregir / anular:** RPC `correct_match_event(p_event, p_patch jsonb, p_reason)` y `void_match_event(p_event, p_reason)`. Nunca `UPDATE`/`DELETE` directo (el servidor lo bloquea y guarda la revisión).
- **Cronología con nombres:** RPC `get_match_timeline(p_match)` (aplica la privacidad: menores como «Nombre + inicial» para el público).
- **Tiempo real:** suscríbete a cambios de `matches` (marcador/estado) y `match_events`; **cuando llegue un aviso, vuelve a pedir la cronología** por RPC. Así los nombres respetan la privacidad.
- **Estadísticas:** RPC `get_player_stats(p_player)` y `get_team_stats(p_team)`.
- **Hijo menor de 13:** RPC `create_child_player(p_first, p_last, p_birth, p_position)`.
- **Operador:** RPC `set_match_operator(p_match, p_user)`.
- **Resumen IA:** Edge Function `generate-match-summary` (ver §5) → luego `save_summary_edit` y `approve_summary`.

---

## 5. Resumen con IA (Gemini)

1. En https://aistudio.google.com crea una **API key** (la que ya tienes sirve). **No la pongas en Android ni en el repo.**
2. Guárdala como secreto de Supabase: Dashboard → **Edge Functions → Secrets** (o CLI: `supabase secrets set GEMINI_API_KEY=... GEMINI_MODEL=<modelo Flash vigente en AI Studio>`).
3. Despliega `supabase/functions/generate-match-summary/index.ts` (CLI: `supabase functions deploy generate-match-summary --no-verify-jwt`; o desde el editor del dashboard).
4. La función **no está probada**: Claude Code debe ejecutarla con el partido de demo finalizado y ajustarla.
5. Llamada desde Android (con la sesión del DT): `client.functions.invoke("generate-match-summary", body = buildJsonObject { put("match_id", id) })`.

La IA **solo recibe** eventos con seudónimos (`J1`, `J2`, …); los nombres se restituyen en el servidor. Nunca se envían nombres a Google.

---

## 6. Checklist para hoy (presentación 4 pm)

- [ ] Clave publishable copiada y puesta en `local.properties`
- [ ] Migración ejecutada (17 filas en `event_types`)
- [ ] 2 cuentas de demo + `seed_demo.sql` ejecutado
- [ ] Prueba de conexión en el celular (17 tipos)
- [ ] Login → partido demo → iniciar → registrar eventos → ver en el 2.º celular
- [ ] Caso sucio: evento sin jugador y luego asignarlo
- [ ] Caso sucio: modo avión → registrar → reconectar sin duplicados
- [ ] Finalizar → resumen IA → editar → aprobar
- [ ] Reactivar el proyecto si Supabase lo pausó (plan gratuito: 7 días sin actividad)
- [ ] Grabar el video del partido
