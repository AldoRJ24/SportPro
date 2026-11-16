package com.sportpro.app.core.data

import java.time.LocalDate
import java.time.ZoneId

// ---------------- Notificaciones: a dónde lleva cada una y cómo se agrupan (migración 010) ----------------
// Lógica pura (sin Android): se puede probar con tests unitarios.

/** Pantalla que se abre al tocar una notificación. La navegación real la hace MainShell. */
sealed interface NotificationTarget {
    /** Partido (convocatoria o partido en vivo). */
    data class Match(val matchId: String) : NotificationTarget
    /** Publicación comentada: el feed de Inicio, donde están los comentarios. */
    data object Feed : NotificationTarget
    /** Perfil: ahí se responden las solicitudes de equipo y de vínculo, y se ve el equipo nuevo. */
    data object Profile : NotificationTarget
    /** «Mis mensualidades» (lo que yo pago). */
    data object MyFees : NotificationTarget
    /** Movimientos de un jugador en un equipo (el admin revisa un pago informado). */
    data class TeamFees(val teamId: String, val playerId: String?) : NotificationTarget
    /** Aviso de prueba (nueva inscripción, respuesta o cancelación). */
    data class Tryout(val tryoutId: String) : NotificationTarget
    /** Documentos de un jugador. */
    data class PlayerDocs(val playerId: String) : NotificationTarget
}

/** Categorías que el usuario puede apagar, en el orden en que se muestran (códigos de la base de datos). */
val NOTIFICATION_CATEGORIES: List<String> = listOf("callups", "live", "comments", "requests", "payments")

/**
 * Decide el destino según el tipo (`kind`) y los ids que el servidor guardó en `data`.
 * Devuelve null cuando no hay a dónde ir (tipo desconocido o falta el id): en ese caso la
 * notificación solo se marca como leída. Nunca lanza error por datos incompletos.
 */
fun notificationTarget(kind: String, data: (String) -> String?): NotificationTarget? = when {
    kind == "callup" || kind == "match_live" ->
        data("match_id")?.let { NotificationTarget.Match(it) }

    kind.startsWith("post_comment") -> NotificationTarget.Feed

    // El admin recibe «pago por confirmar»: va directo a los movimientos de ese jugador
    kind == "fee_payment_reported" ->
        data("team_id")?.let { NotificationTarget.TeamFees(it, data("player_id")) }

    // El resto de avisos de mensualidades son para quien paga
    kind.startsWith("fee_") -> NotificationTarget.MyFees

    kind.startsWith("document_") ->
        data("player_id")?.let { NotificationTarget.PlayerDocs(it) }

    kind.startsWith("tryout_") ->
        data("tryout_id")?.let { NotificationTarget.Tryout(it) }

    kind.startsWith("team_join_") || kind.startsWith("guardian_") || kind == "invite_used" ->
        NotificationTarget.Profile

    else -> null
}

/** Atajo para una fila real: lee los ids del JSON `data` de la notificación. */
fun NotificationRow.target(): NotificationTarget? = notificationTarget(kind) { key -> dataString(key) }

/** Día (en la zona horaria del celular) en que se creó la notificación; null si no tiene fecha válida. */
fun notificationDay(createdAt: String?, zone: ZoneId = ZoneId.systemDefault()): LocalDate? =
    parseInstant(createdAt)?.atZone(zone)?.toLocalDate()

/**
 * Agrupa por día conservando el orden en que llegan (el servidor ya las manda de la más nueva
 * a la más vieja). Devuelve pares (día, notificaciones de ese día).
 */
fun <T> groupByDay(items: List<T>, dayOf: (T) -> LocalDate?): List<Pair<LocalDate?, List<T>>> {
    val groups = LinkedHashMap<LocalDate?, MutableList<T>>()   // LinkedHashMap respeta el orden de llegada
    for (item in items) groups.getOrPut(dayOf(item)) { mutableListOf() }.add(item)
    return groups.map { (day, list) -> day to list }
}
