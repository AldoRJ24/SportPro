package com.sportpro.app.ui.theme

import androidx.compose.ui.graphics.Color

// Paleta SportPro: azul, blanco y negro, con la lógica de colores de iOS.
// El blanco (o el negro en modo oscuro) es el fondo, las tarjetas son un gris muy suave que
// las separa sin bordes, y el azul es el único color de marca: se reserva para la acción
// principal y lo seleccionado, así destaca sin recargar.

// ---- Azul de marca ----
val BrandBlue = Color(0xFF1D4ED8)        // acción principal, enlaces, lo seleccionado (modo claro)
val BrandBlueBright = Color(0xFF3B6FF6)  // segundo tono del degradado del botón «+»
val BrandBlueDark = Color(0xFF0B2A6F)    // texto sobre azul muy claro
val BrandBlueSoft = Color(0xFFDCE7FF)    // fondo de lo seleccionado (pestaña, chip)
val BrandBlueLight = Color(0xFF93B4FF)   // azul sobre fondo negro (avisos inferiores)
val BrandBlueNight = Color(0xFF5B8CFF)   // azul de marca en modo oscuro (contraste sobre negro)
val BrandBlueNightSoft = Color(0xFF142B5C) // fondo de lo seleccionado en modo oscuro

// ---- Modo claro: texto y líneas ----
val Ink = Color(0xFF0A0A0A)              // texto principal
val InkSoft = Color(0xFF6E6E73)          // texto secundario (gris de sistema de Apple)
val Line = Color(0xFFE5E5EA)             // divisores finos
val LineStrong = Color(0xFFC7C7CC)       // bordes de campos

// ---- Modo claro: superficies ----
val Paper = Color(0xFFFFFFFF)            // fondo
val Mist50 = Color(0xFFF9F9FB)           // hojas y menús
val Mist100 = Color(0xFFF2F2F7)          // tarjetas (gris agrupado de iOS)
val Mist200 = Color(0xFFE9E9EF)          // rellenos dentro de tarjetas
val Mist300 = Color(0xFFE1E1E7)

// ---- Modo oscuro: superficies (negro puro de fondo, grises elevados de iOS) ----
val Night = Color(0xFF000000)            // fondo
val Night100 = Color(0xFF1C1C1E)         // tarjetas
val Night200 = Color(0xFF2C2C2E)         // rellenos y diálogos
val Night300 = Color(0xFF3A3A3C)
val NightInk = Color(0xFFF5F5F7)         // texto principal
val NightInkSoft = Color(0xFF98989F)     // texto secundario
val NightLine = Color(0xFF38383A)        // divisores
val NightLineStrong = Color(0xFF545458)  // bordes de campos

// ---- Colores con significado (elegidos para leerse bien sobre blanco y sobre negro) ----
val SuccessGreen = Color(0xFF15803D)     // confirmado, pagado, al día
val WarnAmber = Color(0xFFB45309)        // pendiente, atención
val ErrorRed = Color(0xFFDC2626)         // error, anulado, rechazado
val ErrorRedNight = Color(0xFFFF453A)    // error en modo oscuro
val LiveRed = Color(0xFFE11D2E)          // partido en vivo
val FavoriteGold = Color(0xFFE8A200)     // estrella de favorito
val CardYellow = Color(0xFFFFD60A)       // tarjeta amarilla
val CardRed = Color(0xFFFF453A)          // tarjeta roja

// ---- Cancha (alineación) ----
// El verde solo se usa para dibujar el césped; dos tonos para las franjas cortadas.
val PitchGreen = Color(0xFF2E7D4F)
val PitchGreenStripe = Color(0xFF2A7349)
val PitchLine = Color(0x99FFFFFF)        // líneas blancas semitransparentes
