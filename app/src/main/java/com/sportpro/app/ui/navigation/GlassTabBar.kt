package com.sportpro.app.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportpro.app.ui.theme.BrandBlue
import com.sportpro.app.ui.theme.BrandBlueBright
import com.sportpro.app.ui.theme.BrandBlueNight
import com.sportpro.app.ui.theme.LocalIsDark
import com.sportpro.app.ui.theme.glassHairline
import com.sportpro.app.ui.theme.glassStyle
import com.sportpro.app.ui.theme.pressScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

/** Alto de la barra sin contar el borde del sistema (gestos / botones de Android). */
val TabBarHeight = 58.dp

/** Una pestaña de la barra: icono relleno cuando está elegida y de contorno cuando no (como iOS). */
class TabBarItem(
    val label: String,
    val selected: Boolean,
    val iconSelected: Painter,
    val iconIdle: Painter,
    val onClick: () -> Unit,
    val badge: Int = 0,
)

/**
 * Barra inferior de «vidrio» al estilo iOS: el contenido pasa por debajo y se ve desenfocado.
 *
 * Si [onCreate] no es null (solo para quien dirige equipos), en el centro aparece el botón «+»
 * grande para crear un partido o desafío, al estilo TikTok. Jugadores y apoderados no lo ven:
 * sus pestañas se reparten el ancho completo.
 */
@Composable
fun GlassTabBar(
    items: List<TabBarItem>,
    haze: HazeState?,
    onCreate: (() -> Unit)?,
    createLabel: String,
    modifier: Modifier = Modifier,
) {
    val hairline = glassHairline()
    val glass = glassStyle()
    Box(
        modifier
            .fillMaxWidth()
            .then(if (haze != null) Modifier.hazeEffect(haze, glass) else Modifier.background(MaterialTheme.colorScheme.background))
            // Filo superior: la «luz» que toca el canto del vidrio
            .drawBehind { drawLine(hairline, Offset(0f, 0f), Offset(size.width, 0f), 1f) }
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().height(TabBarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Con «+», la mitad de las pestañas va a cada lado para que el botón quede al centro exacto
            val half = if (onCreate != null) (items.size + 1) / 2 else items.size
            items.take(half).forEach { TabCell(it, Modifier.weight(1f)) }
            if (onCreate != null) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    CreateButton(onCreate, createLabel)
                }
            }
            items.drop(half).forEach { TabCell(it, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun TabCell(item: TabBarItem, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    // El color cambia con una transición corta; la escala responde al apoyar el dedo
    val tint by animateColorAsState(
        if (item.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "tabTint",
    )
    Column(
        modifier
            .fillMaxHeight()
            .clickable(interactionSource = interaction, indication = null, role = Role.Tab, onClick = item.onClick)
            .pressScale(interaction, pressed = 0.88f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BadgedBox(badge = { if (item.badge > 0) Badge { Text(if (item.badge > 9) "9+" else item.badge.toString()) } }) {
            Icon(
                if (item.selected) item.iconSelected else item.iconIdle,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            item.label,
            color = tint,
            fontSize = 10.5.sp,
            fontWeight = if (item.selected) FontWeight.SemiBold else FontWeight.Medium,
            letterSpacing = 0.1.sp,
            maxLines = 1,
        )
    }
}

/**
 * Botón «+» central. Cápsula redondeada con degradado azul, brillo fino en el borde superior y una
 * sombra del mismo azul (parece flotar sobre el vidrio). Al tocarlo se encoge con un resorte
 * y da una vibración corta: es la acción más importante del DT.
 */
@Composable
private fun CreateButton(onCreate: () -> Unit, label: String) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val dark = LocalIsDark.current
    val top = if (dark) BrandBlueNight else BrandBlueBright
    val shape = RoundedCornerShape(15.dp)
    Box(
        Modifier
            .pressScale(interaction, pressed = 0.88f)
            .size(width = 58.dp, height = 40.dp)
            .shadow(elevation = 10.dp, shape = shape, ambientColor = BrandBlue, spotColor = BrandBlue)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(top, BrandBlue)))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.05f))),
                shape = shape,
            )
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = label) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onCreate()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = label, tint = Color.White, modifier = Modifier.size(28.dp))
    }
}
