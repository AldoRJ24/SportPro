package com.sportpro.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sportpro.app.core.data.MatchRow
import com.sportpro.app.core.data.clock
import com.sportpro.app.core.data.formatDayTime
import com.sportpro.app.core.data.statusLabel
import com.sportpro.app.ui.theme.LiveRed
import com.sportpro.app.ui.theme.Mint400
import com.sportpro.app.ui.theme.ScoreStyle
import com.sportpro.app.ui.theme.WarnAmber

/** Match card (§9.8): crests on both sides, score in the middle, status and date on top. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MatchCard(
    match: MatchRow,
    nowMs: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val a11y = "${match.homeName} ${match.homeScore}, ${match.awayName} ${match.awayScore}, ${match.statusLabel(nowMs)}"
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = a11y }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(match, nowMs)
                Spacer(Modifier.weight(1f))
                Text(
                    listOfNotNull(match.category, match.venue).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamColumn(match.homeName, match.home?.crestPath, Modifier.weight(1f))
                Text(
                    if (match.isScheduled) "vs" else "${match.homeScore} - ${match.awayScore}",
                    style = if (match.isScheduled) MaterialTheme.typography.titleLarge else ScoreStyle,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                TeamColumn(match.awayName, match.away?.crestPath, Modifier.weight(1f))
            }
            if (!match.isScheduled) {
                Spacer(Modifier.height(6.dp))
                Text(
                    formatDayTime(match.scheduledAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun TeamColumn(name: String, crestPath: String?, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // Foto de la academia del equipo (o iniciales; el rival externo no tiene foto)
        TeamCrest(name, crestPath, 52.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            name,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun StatusBadge(match: MatchRow, nowMs: Long) {
    when (match.status) {
        "live" -> LivePill("EN VIVO · ${match.clock(nowMs).label}")
        "halftime" -> Pill("ENTRETIEMPO", WarnAmber)
        "finished" -> Pill("FINALIZADO", Mint400)
        "cancelled" -> Pill("CANCELADO", MaterialTheme.colorScheme.onSurfaceVariant)
        else -> Pill(formatDayTime(match.scheduledAt).uppercase(), MaterialTheme.colorScheme.secondary)
    }
}

/** Instagram-stories-like strip with the live matches the user follows (§9.8). */
@Composable
fun LiveStrip(matches: List<MatchRow>, nowMs: Long, onClick: (MatchRow) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(matches, key = { it.id }) { m ->
            Column(
                Modifier
                    .width(76.dp)
                    .combinedClickableCompat { onClick(m) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TeamCrest(m.homeName, m.home?.crestPath, 64.dp, ring = LiveRed)
                Spacer(Modifier.height(4.dp))
                LivePill(if (m.status == "halftime") "ET" else m.clock(nowMs).label)
                Text(
                    "${m.homeScore}-${m.awayScore} ${m.awayName}",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit): Modifier =
    this.combinedClickable(onClick = onClick)
