package com.sportpro.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private val base = Typography()

/**
 * Tipografía con las reglas de Apple: el espaciado entre letras depende del tamaño.
 * Los títulos grandes van más juntos (espaciado negativo) y con interlineado corto; el texto
 * pequeño se abre un poco para leerse bien. La jerarquía sale de combinar tamaño y peso.
 * Se usa la fuente del sistema (Roboto), que ya trae su ajuste óptico.
 */
val Typography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.025).em, lineHeight = 40.sp),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.022).em),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.018).em),
    // Título de las pestañas (estilo «título grande» de iOS)
    titleLarge = base.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.015).em),
    titleMedium = base.titleMedium.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em),
    titleSmall = base.titleSmall.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.005).em),
    bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 23.sp, letterSpacing = (-0.01).em),
    bodyMedium = base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.em),
    bodySmall = base.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.005.em),
    labelLarge = base.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.em),
    labelMedium = base.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.01.em),
    labelSmall = base.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.015.em),
)

val ScoreStyle = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.02).em)
