package com.sportpro.app.ui.components
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.sportpro.app.R
import com.sportpro.app.core.AppText

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportpro.app.ui.theme.LiveRed
import com.sportpro.app.ui.theme.Mint400
import com.sportpro.app.ui.theme.WarnAmber
import kotlin.math.abs

/**
 * Estructura común de todas las pantallas: barra superior con título, botón «volver» y acciones.
 * [isTab] = true cuando la pantalla es una pestaña del menú inferior (el margen de abajo lo maneja la barra).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpScaffold(
    title: String,
    modifier: Modifier = Modifier,
    isTab: Boolean = false,
    onBack: (() -> Unit)? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    floatingActionButton: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_volver))
                        }
                    } else navigationIcon()
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = floatingActionButton,
        contentWindowInsets = if (isTab) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        containerColor = MaterialTheme.colorScheme.background,
        content = content,
    )
}

/** Indicador de carga centrado, para cuando la pantalla todavía no tiene datos. */
@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

/** Mensaje de error a pantalla completa con botón «Reintentar». */
@Composable
fun ErrorBox(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.common_no_se_pudo_cargar), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.common_reintentar)) }
    }
}

/** Estado vacío: título, explicación y, si se indica, un botón para la acción que resuelve el vacío. */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Etiqueta honesta para lo que aún es prototipo (regla: nunca presentarlo como funcional). Hoy ninguna pantalla la usa. */
@Composable
fun PrototypeBanner(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = WarnAmber.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill(stringResource(R.string.common_prototipo), WarnAmber)
            Spacer(Modifier.size(10.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** Título de sección dentro de una lista (en mayúsculas y color suave). */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.2.sp,
    )
}

/** Etiqueta pequeña de color (estado, rol…). Con [filled] el fondo es sólido y el texto blanco. */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, filled: Boolean = false) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .then(if (filled) Modifier.background(color) else Modifier.background(color.copy(alpha = 0.15f)))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text,
            color = if (filled) Color.White else color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/** Etiqueta roja sólida para «EN VIVO». */
@Composable
fun LivePill(text: String, modifier: Modifier = Modifier) = Pill(text, LiveRed, modifier, filled = true)

private val crestPalette = listOf(
    Color(0xFF1E88E5), Color(0xFFE53935), Color(0xFF43A047), Color(0xFFFB8C00),
    Color(0xFF8E24AA), Color(0xFF00ACC1), Color(0xFF6D4C41), Color(0xFFD81B60),
)

/** Escudo por defecto: iniciales sobre un color que siempre es el mismo para ese nombre (también para rivales externos, D-15). */
@Composable
fun Crest(name: String, size: Dp = 48.dp, modifier: Modifier = Modifier, ring: Color? = null) {
    val initials = name.split(" ", "-").filter { it.isNotBlank() && it.first().isLetter() }
        .take(2).joinToString("") { it.first().uppercase() }.ifBlank { "?" }
    val color = crestPalette[abs(name.hashCode()) % crestPalette.size]
    Box(
        modifier
            .size(size)
            .then(if (ring != null) Modifier.border(2.dp, ring, CircleShape).padding(3.dp) else Modifier)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, color = Color.White, fontWeight = FontWeight.Black, fontSize = (size.value * 0.36f).sp)
    }
}

fun roleLabel(role: String): String = when (role) {
    "adm" -> AppText.get(R.string.common_administrador)
    "dt" -> AppText.get(R.string.common_director_tecnico)
    "pad" -> AppText.get(R.string.common_apoderado)
    "jug" -> AppText.get(R.string.common_jugador)
    else -> role
}

/** Punto de conexión del registro en vivo: verde en línea, ámbar sin conexión, con los eventos pendientes de envío. */
@Composable
fun OnlineDot(online: Boolean, pending: Int, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(8.dp).clip(CircleShape)
                .background(if (online) Mint400 else WarnAmber),
        )
        Spacer(Modifier.size(6.dp))
        Text(
            when {
                !online && pending > 0 -> pluralStringResource(R.plurals.common_offline_pending, pending, pending)
                !online -> stringResource(R.string.common_sin_conexion)
                pending > 0 -> stringResource(R.string.common_enviando_x, pending)
                else -> stringResource(R.string.common_en_linea)
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Reloj que avanza para los minutos en vivo (redibuja cada [periodMs]). */
@Composable
fun rememberNowMs(periodMs: Long = 1000L): Long {
    val now = androidx.compose.runtime.produceState(System.currentTimeMillis()) {
        while (true) {
            kotlinx.coroutines.delay(periodMs)
            value = System.currentTimeMillis()
        }
    }
    return now.value
}
