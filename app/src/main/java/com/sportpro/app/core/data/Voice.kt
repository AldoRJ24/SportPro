package com.sportpro.app.core.data

import com.sportpro.app.R
import com.sportpro.app.core.AppText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// =====================================================================================
// Modo manos libres: crear un partido conversando.
//
// Reparto del trabajo:
//  - La IA (Edge Function «voice-match-parse») solo ENTIENDE la frase: devuelve fecha, hora,
//    nombres de equipos y entrenadores tal como se dijeron, formato, minutos…  (VoiceParse)
//  - Este archivo DECIDE la conversación, sin red ni Android (se prueba con JUnit):
//      1. busca esos nombres entre los equipos y academias reales (búsqueda aproximada),
//      2. pregunta lo que falta o lo que es ambiguo, ofreciendo opciones reales,
//      3. arma el resumen para confirmar.
//  - El ViewModel solo carga datos, habla/escucha y, tras el «sí», crea el partido con las
//    mismas funciones del formulario (el servidor valida todo otra vez).
// =====================================================================================

/** Lo que la IA entendió de una frase (respuesta de la Edge Function). */
@Serializable
data class VoiceParse(
    val intent: String = "other",
    val date: String? = null,
    val time: String? = null,
    @SerialName("home_team") val homeTeam: String? = null,
    @SerialName("home_coach") val homeCoach: String? = null,
    @SerialName("home_category") val homeCategory: Int? = null,
    @SerialName("away_team") val awayTeam: String? = null,
    @SerialName("away_coach") val awayCoach: String? = null,
    @SerialName("away_academy") val awayAcademy: String? = null,
    @SerialName("away_category") val awayCategory: Int? = null,
    @SerialName("format_players") val formatPlayers: Int? = null,
    @SerialName("half_minutes") val halfMinutes: Int? = null,
    val venue: String? = null,
    @SerialName("option_index") val optionIndex: Int? = null,
    /** Error de negocio (sin plan, sin cupo, no entendí): se lee en voz alta. */
    val error: String? = null,
)

/** Un equipo que gestiono, con lo necesario para reconocerlo por voz. */
data class VoiceTeam(
    val id: String,
    val name: String,
    val category: String?,
    val maxAge: Int?,
    val coach: String?,
    val defaultFormat: Int,
    val halfMinutes: Int,
)

/** Datos reales con los que se comparan los nombres dichos. */
data class VoiceWorld(
    val myTeams: List<VoiceTeam>,
    /** Equipos donde soy DT (si el local no es uno de estos, va como propuesta a su DT). */
    val staffTeamIds: Set<String>,
    val academies: List<AcademyRef>,
    /** Rivales posibles de MI equipo local, por academia (los trae el servidor con su DT y división). */
    val rivals: Map<String, List<RivalTeam>> = emptyMap(),
)

/** Lo que se dijo de un equipo (cualquiera puede faltar). */
data class TeamQuery(val team: String? = null, val coach: String? = null, val category: Int? = null) {
    val isEmpty: Boolean get() = team.isNullOrBlank() && coach.isNullOrBlank() && category == null
    fun merge(o: TeamQuery) = TeamQuery(o.team ?: team, o.coach ?: coach, o.category ?: category)
}

/** El partido que se va armando en la conversación. */
data class VoiceDraft(
    val homeTeamId: String? = null,
    val homeQuery: TeamQuery = TeamQuery(),
    val academyId: String? = null,
    val academyQuery: String? = null,
    val awayTeamId: String? = null,
    val awayQuery: TeamQuery = TeamQuery(),
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val format: Int? = null,
    val minutes: Int? = null,
    /** null = aún no se preguntó; «» = sin sede. */
    val venue: String? = null,
)

/** Qué dato se está preguntando. */
enum class VoiceField { HOME, ACADEMY, AWAY, DATE, TIME, FORMAT, MINUTES, VENUE, CONFIRM }

/** Una opción ofrecida (se toca o se dice). [value] es el id o el número que representa. */
data class VoiceOption(val label: String, val value: String)

/** El siguiente paso de la conversación. */
sealed interface VoiceStep {
    /** Preguntar algo (con opciones, si las hay). */
    data class Ask(val field: VoiceField, val text: String, val options: List<VoiceOption> = emptyList()) : VoiceStep
    /** Antes de seguir hay que traer los equipos rivales de estas academias. */
    data class LoadRivals(val academyIds: List<String>) : VoiceStep
    /** Todo completo: leer el resumen y esperar el «sí». */
    data class Confirm(val text: String) : VoiceStep
}

/** Cómo se creará: directo (mis dos equipos), desafío a otro DT o propuesta del administrador. */
enum class VoicePath { DIRECT, CHALLENGE, PROPOSAL }

object VoiceBrain {

    // ------------------------------------------------------------------ búsqueda aproximada

    /** minúsculas, sin tildes, solo letras/números y espacios. */
    fun normalize(text: String?): String =
        Normalizer.normalize(text.orEmpty().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    // Palabras de relleno que no ayudan a distinguir equipos
    private val stop = setOf("el", "la", "los", "las", "de", "del", "y", "equipo", "grupo", "academia", "club",
        "division", "categoria", "sub", "entrenador", "profe", "dt", "su", "mi", "con", "contra", "vs")

    /** Quita diminutivos: «fernandito» → «fernand», «rodolfito» → «rodolf», «pepito» → «pep». */
    fun stem(word: String): String {
        val w = word.lowercase()
        for (suffix in listOf("cito", "cita", "itos", "itas", "ito", "ita", "illo", "illa")) {
            if (w.length - suffix.length >= 3 && w.endsWith(suffix)) return w.dropLast(suffix.length)
        }
        return w
    }

    private fun tokens(text: String?): List<String> = normalize(text).split(' ').filter { it.length >= 2 && it !in stop }

    /**
     * Qué tan bien una palabra dicha coincide con un texto real: exacta 3, raíz (diminutivo o
     * palabra incompleta de 4+ letras) 2, nada 0.
     */
    private fun wordScore(spoken: String, real: List<String>): Int = when {
        spoken in real -> 3
        real.any { r -> val s = stem(spoken); s.length >= 3 && (r.startsWith(s) || stem(r) == s) } -> 2
        spoken.length >= 4 && real.any { it.startsWith(spoken) } -> 2
        else -> 0
    }

    /** Puntaje de un equipo frente a lo dicho (nombre, entrenador y división). */
    fun teamScore(q: TeamQuery, name: String, category: String?, maxAge: Int?, coach: String?): Int {
        var score = 0
        val nameWords = tokens(name + " " + category.orEmpty())
        tokens(q.team).forEach { score += wordScore(it, nameWords) }
        val coachWords = tokens(coach)
        tokens(q.coach).forEach { score += wordScore(it, coachWords) }
        if (q.category != null) {
            val real = maxAge ?: Regex("\\d+").find(category.orEmpty())?.value?.toIntOrNull()
            score += when {
                real == null -> 0
                real == q.category -> 4
                else -> -4   // dijo otra división: este no es
            }
        }
        return score
    }

    /** Puntaje de una academia frente a lo dicho. */
    fun academyScore(spoken: String?, a: AcademyRef): Int {
        val real = tokens(a.name + " " + a.city.orEmpty())
        return tokens(spoken).sumOf { wordScore(it, real) }
    }

    /**
     * De una lista con puntaje, el ganador claro (o null) y los empatados cerca del mejor.
     * «Claro» = tiene puntaje y le saca al segundo al menos 2 puntos.
     */
    private fun <T> pick(scored: List<Pair<T, Int>>): Pair<T?, List<T>> {
        val sorted = scored.filter { it.second > 0 }.sortedByDescending { it.second }
        if (sorted.isEmpty()) return null to emptyList()
        val best = sorted[0].second
        val close = sorted.filter { it.second >= best - 1 }.map { it.first }
        return (if (close.size == 1 || sorted.size == 1 || sorted[1].second <= best - 2) sorted[0].first else null) to close
    }

    // ------------------------------------------------------------------ juntar lo dicho

    /**
     * Agrega al borrador lo que se entendió de una frase. Si había una pregunta con opciones,
     * la respuesta puede ser el número de la opción (IA) o el texto de una opción ([heard]).
     */
    fun merge(d: VoiceDraft, p: VoiceParse, pending: VoiceStep.Ask?, heard: String): VoiceDraft {
        var x = d
        // 1) Eligió una opción ofrecida
        val chosen = pending?.options?.let { opts ->
            p.optionIndex?.let { opts.getOrNull(it - 1) } ?: matchOption(opts, heard)
        }
        if (chosen != null && pending != null) {
            x = when (pending.field) {
                VoiceField.HOME -> x.copy(homeTeamId = chosen.value, academyId = null, awayTeamId = null)
                VoiceField.ACADEMY -> x.copy(academyId = chosen.value, awayTeamId = null)
                VoiceField.AWAY -> chosen.value.split('|').let { (academy, team) -> x.copy(academyId = academy, awayTeamId = team) }
                VoiceField.FORMAT -> x.copy(format = chosen.value.toIntOrNull())
                VoiceField.MINUTES -> x.copy(minutes = chosen.value.toIntOrNull())
                VoiceField.VENUE -> x.copy(venue = chosen.value)
                else -> x
            }
        }
        // 2) Datos nuevos (también sirven para corregir: «cámbialo a las 4»)
        val home = TeamQuery(p.homeTeam, p.homeCoach, p.homeCategory)
        // Cambió el equipo propio: se vuelve a buscar (los rivales posibles dependen de su división)
        if (!home.isEmpty) x = x.copy(homeTeamId = null, homeQuery = x.homeQuery.merge(home))
        if (!p.awayAcademy.isNullOrBlank()) x = x.copy(academyId = null, academyQuery = p.awayAcademy, awayTeamId = null)
        val away = TeamQuery(p.awayTeam, p.awayCoach, p.awayCategory)
        if (!away.isEmpty) x = x.copy(awayTeamId = null, awayQuery = x.awayQuery.merge(away))
        p.date?.let { s -> runCatching { LocalDate.parse(s) }.getOrNull()?.let { x = x.copy(date = it) } }
        p.time?.let { s -> runCatching { LocalTime.parse(s) }.getOrNull()?.let { x = x.copy(time = it) } }
        p.formatPlayers?.takeIf { it in 5..11 }?.let { x = x.copy(format = it) }
        p.halfMinutes?.takeIf { it in 5..45 }?.let { x = x.copy(minutes = it) }
        p.venue?.let { v -> x = x.copy(venue = if (v.trim() == "-") "" else v.trim().take(120)) }
        return x
    }

    /** ¿El texto dicho nombra claramente una de las opciones? («la de sub 15», «Lagartos»). */
    fun matchOption(options: List<VoiceOption>, heard: String): VoiceOption? {
        val spoken = tokens(heard)
        if (spoken.isEmpty()) return null
        val scored = options.map { o -> o to spoken.sumOf { wordScore(it, tokens(o.label)) } }
        return pick(scored).first
    }

    // ------------------------------------------------------------------ siguiente paso

    /** Ruta que tendrá el partido (igual que el formulario). */
    fun route(d: VoiceDraft, w: VoiceWorld): VoicePath = when {
        d.homeTeamId != null && d.homeTeamId !in w.staffTeamIds -> VoicePath.PROPOSAL
        d.awayTeamId != null && d.awayTeamId !in w.staffTeamIds -> VoicePath.CHALLENGE
        else -> VoicePath.DIRECT
    }

    /**
     * Resuelve lo que se pueda y devuelve el borrador actualizado y el siguiente paso.
     * El orden de las preguntas es el del formulario: equipo, rival, fecha, hora, formato,
     * minutos, sede y confirmación.
     */
    fun next(d0: VoiceDraft, w: VoiceWorld, now: LocalDateTime): Pair<VoiceDraft, VoiceStep> {
        var d = d0
        // ---- 1. Mi equipo
        if (d.homeTeamId == null) {
            val teams = w.myTeams
            if (teams.size == 1) {
                d = d.copy(homeTeamId = teams[0].id)
            } else {
                val (best, close) = pick(teams.map { it to teamScore(d.homeQuery, it.name, it.category, it.maxAge, it.coach) })
                if (best != null) d = d.copy(homeTeamId = best.id)
                else return d to VoiceStep.Ask(
                    VoiceField.HOME,
                    if (d.homeQuery.isEmpty) AppText.get(R.string.voice_ask_home) else AppText.get(R.string.voice_ask_home_which),
                    (close.ifEmpty { teams }).take(6).map { VoiceOption(teamLabel(it.name, it.category, it.coach), it.id) },
                )
            }
        }
        val home = w.myTeams.first { it.id == d.homeTeamId }

        // ---- 2. Academia rival
        if (d.awayTeamId == null && d.academyId == null) {
            if (!d.academyQuery.isNullOrBlank()) {
                val (best, close) = pick(w.academies.map { it to academyScore(d.academyQuery, it) })
                if (best != null) d = d.copy(academyId = best.id)
                else return d to VoiceStep.Ask(
                    VoiceField.ACADEMY,
                    if (close.isEmpty()) AppText.get(R.string.voice_ask_academy_not_found, d.academyQuery) else AppText.get(R.string.voice_ask_academy_which),
                    (close.ifEmpty { w.academies }).take(6).map { VoiceOption(it.name, it.id) },
                )
            } else if (!d.awayQuery.isEmpty) {
                // Dijo el equipo o el entrenador pero no la academia: se busca en todas
                val missing = w.academies.map { it.id }.filter { it !in w.rivals }
                if (missing.isNotEmpty()) return d to VoiceStep.LoadRivals(missing)
                val all = w.rivals.flatMap { (academy, list) -> list.map { academy to it } }
                val (best, close) = pick(all.map { it to teamScore(d.awayQuery, it.second.name, it.second.category, it.second.maxAge, it.second.coachName) })
                if (best != null) d = d.copy(academyId = best.first, awayTeamId = best.second.teamId)
                else return d to VoiceStep.Ask(
                    VoiceField.AWAY,
                    if (close.isEmpty()) AppText.get(R.string.voice_ask_away_not_found) else AppText.get(R.string.voice_ask_away_which),
                    close.take(6).map { (academy, t) -> VoiceOption(teamLabel(t.name, t.category, t.coachName), "$academy|${t.teamId}") },
                )
            } else {
                return d to VoiceStep.Ask(VoiceField.ACADEMY, AppText.get(R.string.voice_ask_rival))
            }
        }

        // ---- 3. Equipo rival dentro de la academia
        if (d.awayTeamId == null) {
            val academyId = d.academyId!!
            val list = w.rivals[academyId] ?: return d to VoiceStep.LoadRivals(listOf(academyId))
            val eligible = list.filter { it.eligible }
            val scored = list.map { it to teamScore(d.awayQuery, it.name, it.category, it.maxAge, it.coachName) }
            val (best, close) = if (d.awayQuery.isEmpty) (eligible.singleOrNull() to eligible) else pick(scored)
            when {
                best != null && !best.eligible -> return d.copy(awayQuery = TeamQuery()) to VoiceStep.Ask(
                    VoiceField.AWAY,
                    AppText.get(R.string.voice_rival_out_of_division, best.name, best.reason.orEmpty()),
                    eligible.take(6).map { VoiceOption(teamLabel(it.name, it.category, it.coachName), "$academyId|${it.teamId}") },
                )
                best != null -> d = d.copy(awayTeamId = best.teamId)
                eligible.isEmpty() -> return d.copy(academyId = null, academyQuery = null) to VoiceStep.Ask(
                    VoiceField.ACADEMY, AppText.get(R.string.voice_no_eligible, academyName(w, academyId)),
                    w.academies.filter { it.id != academyId }.take(6).map { VoiceOption(it.name, it.id) },
                )
                else -> return d to VoiceStep.Ask(
                    VoiceField.AWAY,
                    AppText.get(R.string.voice_ask_away_in_academy, academyName(w, academyId)),
                    (close.filter { it.eligible }.ifEmpty { eligible }).take(6)
                        .map { VoiceOption(teamLabel(it.name, it.category, it.coachName), "$academyId|${it.teamId}") },
                )
            }
        }
        val rival = w.rivals[d.academyId]?.firstOrNull { it.teamId == d.awayTeamId }

        // ---- 4. Fecha y hora (no en el pasado; un desafío o propuesta, con 1 h de anticipación)
        if (d.date == null || d.date!!.isBefore(now.toLocalDate())) {
            val past = d.date != null
            return d.copy(date = null) to VoiceStep.Ask(
                VoiceField.DATE, AppText.get(if (past) R.string.voice_date_past else R.string.voice_ask_date),
            )
        }
        if (d.time == null) return d to VoiceStep.Ask(VoiceField.TIME, AppText.get(R.string.voice_ask_time))
        val start = LocalDateTime.of(d.date, d.time)
        val minStart = if (route(d, w) == VoicePath.DIRECT) now else now.plusHours(1)
        if (start.isBefore(minStart)) {
            return d.copy(time = null) to VoiceStep.Ask(
                VoiceField.TIME,
                AppText.get(if (route(d, w) == VoicePath.DIRECT) R.string.voice_time_past else R.string.voice_time_one_hour),
            )
        }

        // ---- 5. Formato y minutos (se sugiere lo habitual del equipo)
        if (d.format == null) return d to VoiceStep.Ask(
            VoiceField.FORMAT, AppText.get(R.string.voice_ask_format, home.defaultFormat),
            listOf(5, 7, 9, 11).map { VoiceOption(AppText.get(R.string.voice_format_option, it), "$it") },
        )
        if (d.minutes == null) return d to VoiceStep.Ask(
            VoiceField.MINUTES, AppText.get(R.string.voice_ask_minutes, home.halfMinutes),
            listOf(home.halfMinutes, 20, 25, 30, 45).distinct().map { VoiceOption(AppText.get(R.string.voice_minutes_option, it), "$it") },
        )

        // ---- 6. Sede (opcional: se pregunta una sola vez)
        if (d.venue == null) return d to VoiceStep.Ask(
            VoiceField.VENUE, AppText.get(R.string.voice_ask_venue),
            listOf(VoiceOption(AppText.get(R.string.voice_no_venue), "")),
        )

        // ---- 7. Resumen
        return d to VoiceStep.Confirm(summary(d, w, home, rival))
    }

    private val dayFormat = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-PE"))
    private val hourFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.forLanguageTag("es-PE"))

    /** Resumen hablado: qué, cuándo, cómo y por qué camino se crea. */
    fun summary(d: VoiceDraft, w: VoiceWorld, home: VoiceTeam, rival: RivalTeam?): String {
        val rivalName = rival?.let { teamLabel(it.name, it.category, null) } ?: "?"
        val base = AppText.get(
            R.string.voice_summary,
            teamLabel(home.name, home.category, null), rivalName,
            d.date!!.format(dayFormat), d.time!!.format(hourFormat).replace("a. m.", "de la mañana").replace("p. m.", "de la tarde"),
            d.format!!, d.minutes!!,
        )
        val where = d.venue?.takeIf { it.isNotBlank() }?.let { " " + AppText.get(R.string.voice_summary_venue, it) } ?: ""
        val how = when (route(d, w)) {
            VoicePath.DIRECT -> AppText.get(R.string.voice_route_direct)
            VoicePath.CHALLENGE -> AppText.get(R.string.voice_route_challenge, rival?.coachName ?: AppText.get(R.string.voice_the_rival_coach))
            VoicePath.PROPOSAL -> AppText.get(R.string.voice_route_proposal)
        }
        return "$base$where. $how ${AppText.get(R.string.voice_confirm_question)}"
    }

    private fun teamLabel(name: String, category: String?, coach: String?): String =
        listOfNotNull(name, category?.takeIf { it.isNotBlank() && !normalize(name).contains(normalize(it)) }, coach?.let { "DT $it" })
            .joinToString(" · ")

    private fun academyName(w: VoiceWorld, id: String) = w.academies.firstOrNull { it.id == id }?.name.orEmpty()

    /** «Sí» / «no» dichos de forma clara (para no esperar a la IA en la confirmación). */
    fun quickYesNo(heard: String): Boolean? {
        val t = normalize(heard)
        val yes = listOf("si", "dale", "ok", "okey", "claro", "confirmo", "confirma", "confirmalo", "envialo", "crealo", "de acuerdo", "perfecto", "listo")
        val no = listOf("no", "todavia no", "espera", "aun no")
        return when {
            yes.any { t == it || t.startsWith("$it ") } -> true
            no.any { t == it || t.startsWith("$it ") } -> false
            else -> null
        }
    }

    /** ¿Quiere salir? («cancela», «olvídalo», «salir»). */
    fun isCancel(heard: String): Boolean {
        val t = normalize(heard)
        return listOf("cancela", "cancelar", "olvidalo", "salir", "ya no", "detente", "para").any { t == it || t.startsWith("$it ") }
    }
}
