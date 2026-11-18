package com.sportpro.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val base = Typography()

// Estilo deportivo: titulos en negrita cursiva y etiquetas con mas espaciado.
val Typography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
)

val ScoreStyle = TextStyle(
    fontSize = 44.sp,
    fontWeight = FontWeight.Black,
    fontStyle = FontStyle.Italic,
    letterSpacing = 1.sp,
)
