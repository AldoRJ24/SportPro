package com.sportpro.app.core.data
import com.sportpro.app.R
import com.sportpro.app.core.AppText

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val esPe: Locale = Locale.forLanguageTag("es-PE")
private val dayTime: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", esPe)
private val dayOnly: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", esPe)
private val timeOnly: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", esPe)

fun parseInstant(iso: String?): Instant? =
    iso?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }

fun formatDayTime(iso: String?): String =
    parseInstant(iso)?.atZone(ZoneId.systemDefault())?.format(dayTime) ?: AppText.get(R.string.time_sin_fecha)

fun formatDay(iso: String?): String =
    parseInstant(iso)?.atZone(ZoneId.systemDefault())?.format(dayOnly)?.replaceFirstChar { it.uppercase() } ?: AppText.get(R.string.time_sin_fecha)

fun formatTime(iso: String?): String =
    parseInstant(iso)?.atZone(ZoneId.systemDefault())?.format(timeOnly) ?: "--:--"

/** «hace 5 min», «hace 2 h», «hace 3 d»… como en las redes sociales; más de una semana → fecha. */
fun timeAgo(iso: String?, nowMs: Long = System.currentTimeMillis()): String {
    val t = parseInstant(iso)?.toEpochMilli() ?: return ""
    val s = ((nowMs - t) / 1000).coerceAtLeast(0)
    return when {
        s < 60 -> AppText.get(R.string.time_now)
        s < 3600 -> AppText.get(R.string.time_hace_x_min, s / 60)
        s < 86_400 -> AppText.get(R.string.time_hace_x_h, s / 3600)
        s < 7 * 86_400 -> AppText.get(R.string.time_hace_x_d, s / 86_400)
        else -> formatDay(iso)
    }
}

/**
 * RELOJ DEL PARTIDO. No hay un temporizador local: el minuto se deriva de datos del servidor
 * (period_started_at, elapsed_before_seconds, clock_running), así todos los celulares muestran
 * el mismo minuto aunque abran la app tarde o pierdan la conexión.
 */
data class MatchClock(val minute: Int, val extra: Int, val seconds: Long) {
    val label: String get() = if (extra > 0) "$minute+$extra'" else "$minute'"
}

// Segundos jugados = acumulado antes de la pausa + (ahora − inicio del periodo) si el reloj corre.
// Minuto estilo fútbol (el primero es 1'); el 2.º tiempo empieza en halfMinutes + 1.
// Pasado el tiempo reglamentario se muestra como añadido, por ejemplo 25+2'.
fun MatchRow.clock(nowMs: Long = System.currentTimeMillis()): MatchClock {
    val running = if (clockRunning) {
        val start = parseInstant(periodStartedAt)?.toEpochMilli() ?: nowMs
        ((nowMs - start) / 1000).coerceAtLeast(0)
    } else 0L
    val elapsed = elapsedBeforeSeconds + running
    val p = period.coerceAtLeast(1)
    val base = (p - 1) * halfMinutes
    val inPeriod = (elapsed / 60).toInt() + 1 // football style: the first minute is 1'
    return if (inPeriod > halfMinutes) {
        MatchClock(base + halfMinutes, (inPeriod - halfMinutes).coerceAtMost(30), elapsed)
    } else {
        MatchClock(base + inPeriod, 0, elapsed)
    }
}

fun MatchRow.statusLabel(nowMs: Long = System.currentTimeMillis()): String = when (status) {
    "live" -> AppText.get(R.string.time_en_vivo_x, clock(nowMs).label)
    "halftime" -> AppText.get(R.string.time_entretiempo)
    "finished" -> AppText.get(R.string.time_finalizado)
    "cancelled" -> AppText.get(R.string.time_cancelado)
    else -> formatDayTime(scheduledAt)
}
