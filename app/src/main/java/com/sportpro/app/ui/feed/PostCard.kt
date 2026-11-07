package com.sportpro.app.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sportpro.app.core.data.FeedPost
import com.sportpro.app.core.data.PostComment
import com.sportpro.app.core.data.timeAgo
import com.sportpro.app.ui.components.Avatar
import com.sportpro.app.ui.components.Pill
import com.sportpro.app.ui.components.TeamCrest
import com.sportpro.app.ui.theme.LiveRed

private fun audienceLabel(visibility: String?): String = when (visibility) {
    "public" -> "Público"
    "academy" -> "Academia"
    else -> "Equipo"
}

private fun likesLabel(n: Int): String = if (n == 1) "1 me gusta" else "$n me gusta"

private fun commentsLabel(p: FeedPost): String = when {
    p.comments == 0 && p.commentsEnabled -> "Comentar"
    p.comments == 0 -> "Comentarios desactivados"
    p.comments == 1 -> "Ver 1 comentario"
    else -> "Ver los ${p.comments} comentarios"
}

/**
 * Publicación en Inicio (estilo Instagram): autor + hace cuánto + audiencia, marcador del partido
 * (toca para abrirlo), texto del resumen (toca para expandir), me gusta y comentarios.
 */
@Composable
fun PostCard(
    post: FeedPost,
    nowMs: Long,
    onLike: () -> Unit,
    onComments: () -> Unit,
    onOpenMatch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(post.matchId) { mutableStateOf(false) }
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Cabecera: quién publicó, academia, hace cuánto y para quién es visible
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(post.authorName, post.authorAvatar, size = 40.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(post.authorName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(post.academyName, timeAgo(post.publishedAt, nowMs).ifBlank { null }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Pill(audienceLabel(post.visibility), MaterialTheme.colorScheme.secondary)
            }
            // Marcador final del partido (abre el detalle con la cronología)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(onClick = onOpenMatch)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TeamCrest(post.homeName, post.homeCrest, size = 32.dp)
                Spacer(Modifier.width(8.dp))
                Text(post.homeName, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${post.homeScore} - ${post.awayScore}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Text(
                    post.awayName, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                TeamCrest(post.awayName, post.awayCrest, size = 32.dp)
            }
            Text(
                post.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { expanded = !expanded },
            )
            Text(
                "Resumen generado con IA y revisado por el DT",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Acciones: me gusta y comentarios
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onLike) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = if (post.liked) "Quitar me gusta" else "Me gusta",
                        tint = if (post.liked) LiveRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(likesLabel(post.likes), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onComments) { Text(commentsLabel(post)) }
            }
        }
    }
}

/**
 * Hoja de comentarios. Cualquiera que vea la publicación puede comentar (si están activados).
 * Quien publicó (o el DT/admin del equipo) ve el interruptor para desactivarlos y el botón de
 * eliminar en TODOS los comentarios; el resto solo puede eliminar los suyos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheet(
    post: FeedPost,
    comments: List<PostComment>,
    loading: Boolean,
    sending: Boolean,
    nowMs: Long,
    onDismiss: () -> Unit,
    onSend: (String, () -> Unit) -> Unit,
    onDelete: (PostComment) -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by rememberSaveable(post.matchId) { mutableStateOf("") }
    var confirm by remember { mutableStateOf<PostComment?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Comentarios", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (post.canModerate) {
                    Text("Permitir", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Switch(checked = post.commentsEnabled, onCheckedChange = onToggleEnabled)
                }
            }
            if (post.canModerate) {
                Text(
                    "Como autor puedes eliminar comentarios ofensivos o desactivar los comentarios.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (!loading && comments.isEmpty()) {
                    item {
                        Text(
                            if (post.commentsEnabled) "Aún no hay comentarios. ¡Sé el primero!" else "Sin comentarios.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(comments, key = { it.id }) { c ->
                    Row(verticalAlignment = Alignment.Top) {
                        Avatar(c.authorName, c.authorAvatar, size = 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(c.authorName, style = MaterialTheme.typography.labelLarge)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    timeAgo(c.createdAt, nowMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(c.body, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (c.canDelete) {
                            IconButton(onClick = { confirm = c }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Eliminar comentario", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (post.commentsEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.take(500) },
                        placeholder = { Text("Escribe un comentario…") },
                        maxLines = 3,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onSend(text) { text = "" } }, enabled = !sending && text.isNotBlank()) { Text("Publicar") }
                }
            } else {
                Text(
                    "Los comentarios están desactivados por el autor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    confirm?.let { c ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("¿Eliminar comentario?") },
            text = {
                Text(
                    if (c.isMine) "Se quitará tu comentario."
                    else "Se quitará el comentario de ${c.authorName}. Queda registrado quién lo eliminó.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(c)
                    confirm = null
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancelar") } },
        )
    }
}
