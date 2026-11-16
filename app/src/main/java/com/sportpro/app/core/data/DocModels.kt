package com.sportpro.app.core.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------------- Documentos del jugador (migración 009) ----------------
// Reflejo de lo que devuelven las funciones SQL doc_*. Los permisos (can_upload, can_review)
// vienen calculados por el servidor; la app solo los usa para mostrar u ocultar botones.

/** Un documento vigente de un jugador. */
@Serializable
data class PlayerDoc(
    val id: String,
    /** dni | medical | travel | insurance */
    @SerialName("doc_type") val docType: String,
    /** Ruta dentro del bucket privado «documents»; con ella se pide la URL firmada. */
    @SerialName("file_path") val filePath: String,
    @SerialName("mime_type") val mimeType: String = "",
    @SerialName("expires_on") val expiresOn: String? = null,
    /** pending | approved | rejected */
    val status: String = "pending",
    @SerialName("review_reason") val reviewReason: String? = null,
    /** El servidor compara la fecha de vencimiento con «hoy» en hora de Perú. */
    @SerialName("is_expired") val isExpired: Boolean = false,
    @SerialName("uploaded_by_name") val uploadedByName: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("reviewed_by_name") val reviewedByName: String? = null,
    @SerialName("reviewed_at") val reviewedAt: String? = null,
)

/** Respuesta de doc_list: los documentos vigentes y qué puedo hacer yo con ellos. */
@Serializable
data class DocList(
    @SerialName("can_upload") val canUpload: Boolean = false,
    @SerialName("can_review") val canReview: Boolean = false,
    /** Tipos obligatorios según el servidor (la autorización de viaje es opcional). */
    val required: List<String> = emptyList(),
    val docs: List<PlayerDoc> = emptyList(),
) {
    fun docOf(type: String): PlayerDoc? = docs.firstOrNull { it.docType == type }
}

/** Fila de doc_team_status: qué le falta a cada jugador del plantel. */
@Serializable
data class DocTeamRow(
    @SerialName("player_id") val playerId: String,
    @SerialName("player_name") val playerName: String,
    @SerialName("jersey_number") val jerseyNumber: Int? = null,
    /** Tipos obligatorios sin un documento aprobado y vigente. */
    val missing: List<String> = emptyList(),
    @SerialName("pending_count") val pendingCount: Int = 0,
    @SerialName("rejected_count") val rejectedCount: Int = 0,
    @SerialName("expired_count") val expiredCount: Int = 0,
    val complete: Boolean = false,
)

/** Estado que se muestra en la tarjeta de un tipo de documento. */
enum class DocState { MISSING, OPTIONAL, PENDING, APPROVED, REJECTED, EXPIRED }

/**
 * Lógica pura de documentos (sin Android ni red, para tests unitarios).
 * Los límites son los mismos del bucket y de la función SQL doc_register: aquí solo sirven
 * para avisar ANTES de gastar datos subiendo un archivo que el servidor va a rechazar.
 */
object Docs {
    /** Tipos en el orden en que se muestran. Los códigos son los de la base de datos. */
    val TYPES: List<String> = listOf("dni", "medical", "insurance", "travel")

    /** Formatos permitidos: fotos JPG/PNG y PDF. */
    val MIME_TYPES: List<String> = listOf("image/jpeg", "image/png", "application/pdf")

    /** Tamaño máximo por archivo: 5 MB (igual que file_size_limit del bucket). */
    const val MAX_BYTES: Int = 5 * 1024 * 1024

    /** Extensión del archivo según su formato, o null si el formato no está permitido. */
    fun extensionFor(mime: String?): String? = when (mime) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "application/pdf" -> "pdf"
        else -> null
    }

    /**
     * Ruta del archivo en Storage: «<jugador>/<documento>.<ext>».
     * La primera carpeta TIENE que ser el id del jugador: las políticas de Storage deciden
     * con ella quién puede subir y quién puede ver.
     */
    fun pathFor(playerId: String, docId: String, mime: String?): String? =
        extensionFor(mime)?.let { ext -> "$playerId/$docId.$ext" }

    fun isSizeOk(bytes: Int): Boolean = bytes in 1..MAX_BYTES

    /**
     * Qué estado mostrar para un tipo. El orden importa: un documento aprobado pero vencido
     * se muestra como VENCIDO, porque ya no sirve aunque alguien lo haya aprobado antes.
     */
    fun stateOf(doc: PlayerDoc?, required: Boolean): DocState = when {
        doc == null -> if (required) DocState.MISSING else DocState.OPTIONAL
        doc.status == "rejected" -> DocState.REJECTED
        doc.isExpired -> DocState.EXPIRED
        doc.status == "approved" -> DocState.APPROVED
        else -> DocState.PENDING
    }
}
