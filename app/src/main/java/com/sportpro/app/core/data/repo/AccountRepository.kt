package com.sportpro.app.core.data.repo

import com.sportpro.app.core.data.AccountStatus
import com.sportpro.app.core.data.AcademyRef
import com.sportpro.app.core.data.CreatedInvitation
import com.sportpro.app.core.data.GuardianRequest
import com.sportpro.app.core.data.InvitationCheck
import com.sportpro.app.core.data.InvitationRow
import com.sportpro.app.core.data.NotificationRow
import com.sportpro.app.core.data.TeamRef
import com.sportpro.app.core.data.TeamStaffMember
import com.sportpro.app.core.data.MemberRow
import com.sportpro.app.core.data.decodeRows
import com.sportpro.app.core.data.decodeScalar
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Repositorio de CUENTA: tipo de cuenta, vínculo apoderado ↔ jugador menor, notificaciones,
 * invitaciones por QR (admin de academia y DT), foto de perfil y equipos de la academia.
 * Las reglas (edades, quién puede invitar, uso de los códigos) viven en funciones SQL
 * (migración 003); aquí solo se llaman.
 */
class AccountRepository(private val client: SupabaseClient) {

    private fun uid(): String = client.auth.currentUserOrNull()?.id ?: error("No autenticado")

    // ---------- Cuenta ----------
    // Estado de la cuenta: tipo (pad/jug/adm/dt), edad y si un jugador menor aún necesita apoderado.
    suspend fun myAccount(): AccountStatus =
        client.postgrest.rpc("my_account").decodeScalar()

    // ---------- Vínculo apoderado ↔ jugador menor ----------
    // VÍNCULO APODERADO ↔ JUGADOR MENOR (13 a 17 años):
    // 1. El apoderado (18+) ve su código único SP-XXXX-XXXX en «Mis hijos» (my_guardian_code).
    // 2. El menor lo escribe (request_guardian_link): queda una solicitud PENDIENTE y al apoderado
    //    le llega la notificación «X quiere vincularse contigo».
    // 3. El apoderado acepta o rechaza (respond_guardian_link). Mientras no acepte, el menor no entra
    //    a ningún apartado de la app (SportProRoot le muestra GuardianLinkScreen).
    // Límites del servidor: no puedes usar tu propio código, máximo 3 solicitudes pendientes y un
    // solo apoderado aprobado.
    suspend fun myGuardianCode(): String =
        client.postgrest.rpc("my_guardian_code").decodeScalar()

    suspend fun requestGuardianLink(code: String) {
        client.postgrest.rpc("request_guardian_link", buildJsonObject { put("p_code", code) })
    }

    suspend fun guardianRequests(): List<GuardianRequest> =
        client.postgrest.rpc("my_guardian_requests").decodeRows()

    suspend fun respondGuardianLink(linkId: String, accept: Boolean) {
        client.postgrest.rpc("respond_guardian_link", buildJsonObject {
            put("p_link", linkId)
            put("p_accept", accept)
        })
    }

    // ---------- Notificaciones ----------
    // NOTIFICACIONES: la RLS garantiza que cada usuario solo lee las suyas. Se traen las 50 más recientes.
    suspend fun notifications(): List<NotificationRow> =
        client.from("notifications").select(Columns.raw("id,kind,title,body,data,read_at,created_at")) {
            order("created_at", Order.DESCENDING)
            limit(50)
        }.decodeRows()

    // Marca como leídas (pone read_at); el contador de la campana baja.
    suspend fun markRead(ids: List<String>) {
        if (ids.isEmpty()) return
        client.from("notifications").update(buildJsonObject { put("read_at", OffsetDateTime.now().toString()) }) {
            filter { isIn("id", ids) }
        }
    }

    /** Realtime de MIS notificaciones (filtro user_id = yo): actualiza la campana y la pantalla de vínculo al instante. */
    fun observeNotifications(): Flow<Unit> = channelFlow {
        val me = uid()
        val channel = client.channel("notif-$me-${UUID.randomUUID()}")
        val flow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "notifications"
            filter("user_id", FilterOperator.EQ, me)
        }
        launch { flow.collect { send(Unit) } }
        channel.subscribe()
        awaitClose {
            CoroutineScope(Dispatchers.IO).launch { runCatching { client.realtime.removeChannel(channel) } }
        }
    }

    // ---------- Invitaciones ----------
    /**
     * INVITACIONES POR QR. Flujo:
     * - Superadmin → create_admin_invitation: código para registrar a un ADMINISTRADOR de academia.
     * - Admin de academia → create_coach_invitation(academia): código para un DT de SU academia.
     * - El QR contiene sportpro://invite/CÓDIGO; al escanearlo se abre el registro con la invitación.
     * - Cada código es de UN solo uso, vence a los 7 días y se puede anular.
     * - Al registrarse con el código, el trigger handle_new_user crea la membresía (y la academia,
     *   si es admin) y marca el código como usado.
     * check_invitation funciona incluso sin sesión: solo dice si el código es válido y para qué.
     */
    suspend fun checkInvitation(code: String): InvitationCheck =
        client.postgrest.rpc("check_invitation", buildJsonObject { put("p_code", code) }).decodeScalar()

    suspend fun createAdminInvitation(): CreatedInvitation =
        client.postgrest.rpc("create_admin_invitation").decodeScalar()

    suspend fun createCoachInvitation(academyId: String): CreatedInvitation =
        client.postgrest.rpc("create_coach_invitation", buildJsonObject { put("p_academy", academyId) }).decodeScalar()

    // Historial de las invitaciones que YO creé: activa, usada (y por quién), vencida o anulada.
    suspend fun myInvitations(): List<InvitationRow> =
        client.postgrest.rpc("my_invitations").decodeRows()

    // Anular: solo quien la creó y solo si todavía no se usó.
    suspend fun revokeInvitation(id: String) {
        client.postgrest.rpc("revoke_invitation", buildJsonObject { put("p_id", id) })
    }

    /** Un adulto YA registrado acepta una invitación (escaneó el QR con la sesión iniciada). Un admin debe indicar su academia. */
    suspend fun redeemInvitation(code: String, academyName: String?, academyCity: String?) {
        client.postgrest.rpc("redeem_invitation", buildJsonObject {
            put("p_code", code)
            put("p_academy_name", academyName)
            put("p_academy_city", academyCity)
        })
    }

    // ---------- Perfil: nombre y foto (migración 004) ----------
    suspend fun updateMyName(fullName: String) {
        client.from("profiles").update(buildJsonObject { put("full_name", fullName.trim()) }) {
            filter { eq("id", uid()) }
        }
    }

    /**
     * FOTO DE PERFIL: sube el JPEG (ya recortado a 512 px en el celular) al bucket «avatars», dentro
     * de la carpeta del propio usuario, y guarda la ruta en profiles.avatar_path.
     * Seguridad (migración 004): Storage solo deja escribir en tu carpeta, un trigger impide usar la
     * ruta de otra persona y los menores de edad no pueden subir foto.
     * La ruta lleva la hora para que cada foto nueva tenga una URL distinta (sin caché vieja).
     */
    suspend fun uploadAvatar(jpeg: ByteArray): String {
        val me = uid()
        val path = "$me/avatar-${System.currentTimeMillis()}.jpg"
        client.storage.from("avatars").upload(path, jpeg) { upsert = true }
        client.from("profiles").update(buildJsonObject { put("avatar_path", path) }) { filter { eq("id", me) } }
        return path
    }

    // Quitar la foto: avatar_path queda vacío y se vuelve a mostrar el escudo con iniciales.
    suspend fun removeAvatar() {
        client.from("profiles").update(buildJsonObject { put("avatar_path", null as String?) }) { filter { eq("id", uid()) } }
    }

    // ---------- Equipos de la academia (ADM) ----------
    // ---------- Foto de la academia (migración 005) ----------
    suspend fun academy(academyId: String): AcademyRef? =
        client.from("academies").select(Columns.raw("id,name,city,status,crest_path")) {
            filter { eq("id", academyId) }
        }.decodeRows<AcademyRef>().firstOrNull()

    /**
     * FOTO DE LA ACADEMIA: sube el JPEG al bucket «academy-logos» en la carpeta de la academia y
     * guarda la ruta en academies.crest_path. En el servidor:
     * - Storage solo deja escribir en esa carpeta al ADMINISTRADOR de la academia (o al superadmin);
     * - un trigger copia la misma ruta a todos los equipos de la academia (teams.crest_path),
     *   así cada equipo muestra la foto de su academia y nadie puede ponerle otra a mano.
     */
    suspend fun uploadAcademyLogo(academyId: String, jpeg: ByteArray): String {
        val path = "$academyId/logo-${System.currentTimeMillis()}.jpg"
        client.storage.from("academy-logos").upload(path, jpeg) { upsert = true }
        client.from("academies").update(buildJsonObject { put("crest_path", path) }) { filter { eq("id", academyId) } }
        return path
    }

    // Quitar la foto: la academia y sus equipos vuelven a mostrar las iniciales.
    suspend fun removeAcademyLogo(academyId: String) {
        client.from("academies").update(buildJsonObject { put("crest_path", null as String?) }) {
            filter { eq("id", academyId) }
        }
    }

    // Gestión del ADMIN de la academia: listar y crear equipos, asignar o quitar DT.
    // El servidor (RLS) solo lo permite a quien administra esa academia.
    suspend fun academyTeams(academyId: String): List<TeamRef> =
        client.from("teams").select(Columns.raw("id,name,category,academy_id,crest_path,default_format_players,half_minutes")) {
            filter { eq("academy_id", academyId) }
            order("name", Order.ASCENDING)
        }.decodeRows()

    suspend fun createTeam(academyId: String, name: String, category: String, maxAge: Int?, format: Int, halfMinutes: Int) {
        client.from("teams").insert(buildJsonArray {
            add(buildJsonObject {
                put("academy_id", academyId)
                put("name", name.trim())
                put("category", category.trim())
                if (maxAge != null) put("max_age", maxAge)
                put("default_format_players", format)
                put("half_minutes", halfMinutes)
            })
        })
    }

    suspend fun teamStaff(teamIds: List<String>): List<TeamStaffMember> {
        if (teamIds.isEmpty()) return emptyList()
        return client.from("team_staff").select(Columns.raw("team_id,user_id,staff_role,profile:profiles!user_id(id,full_name,avatar_path)")) {
            filter { isIn("team_id", teamIds) }
        }.decodeRows()
    }

    // DT activos de la academia (entraron con invitación) para asignarlos a un equipo.
    suspend fun academyCoaches(academyId: String): List<MemberRow> =
        client.from("academy_memberships")
            .select(Columns.raw("user_id,role,profile:profiles!user_id(id,full_name,avatar_path)")) {
                filter {
                    eq("academy_id", academyId)
                    eq("role", "dt")
                    eq("status", "active")
                }
            }.decodeRows()

    suspend fun assignCoach(teamId: String, userId: String) {
        client.from("team_staff").insert(buildJsonArray {
            add(buildJsonObject {
                put("team_id", teamId)
                put("user_id", userId)
                put("staff_role", "dt")
            })
        })
    }

    suspend fun removeCoach(teamId: String, userId: String) {
        client.from("team_staff").delete {
            filter {
                eq("team_id", teamId)
                eq("user_id", userId)
            }
        }
    }
}
