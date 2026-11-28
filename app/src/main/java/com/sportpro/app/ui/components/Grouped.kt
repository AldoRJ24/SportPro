package com.sportpro.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sportpro.app.ui.theme.LocalIsDark
import com.sportpro.app.ui.theme.pressScale

/**
 * Grupo de filas al estilo de Ajustes de iOS: una tarjeta redondeada con las filas separadas por
 * líneas finas que empiezan después del icono (no cortan el icono, así el ojo agrupa mejor).
 */
@Composable
fun GroupedSection(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) { content() }
}

/**
 * Fila tocable de un grupo: icono en un cuadrado de color, título, subtítulo opcional y «›».
 * [divider] dibuja la línea fina bajo la fila (false en la última del grupo).
 */
@Composable
fun GroupedRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    divider: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), role = Role.Button, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Icono blanco sobre un cuadrado redondeado de color: el sello visual de Ajustes de iOS
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(iconTint),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
            )
        }
        if (divider) HorizontalDivider(Modifier.padding(start = 56.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * Control segmentado de iOS: un riel gris con una «pastilla» blanca que se desliza hasta la
 * opción elegida con un resorte sin rebote (si se toca otra opción a mitad del movimiento,
 * cambia de rumbo desde donde está).
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = LocalIsDark.current
    val track = if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f)
    val thumb = if (dark) Color(0xFF636366) else Color.White
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(track)
            .padding(2.dp),
    ) {
        val segment = maxWidth / options.size
        val x by animateDpAsState(
            targetValue = segment * selected,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "segment",
        )
        // Pastilla de la opción elegida
        Box(
            Modifier
                .offset(x = x)
                .width(segment)
                .fillMaxHeight()
                .shadow(2.dp, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(thumb),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { i, label ->
                val interaction = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(interactionSource = interaction, indication = null, role = Role.RadioButton) { onSelect(i) }
                        .pressScale(interaction, pressed = 0.94f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
