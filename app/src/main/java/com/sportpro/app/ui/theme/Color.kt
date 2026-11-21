package com.sportpro.app.ui.theme

import androidx.compose.ui.graphics.Color

// Paleta SportPro: azul, blanco y negro. Tema claro, sobrio y de alto contraste.
// El blanco es el fondo, el negro es el texto y el azul es el único color de marca:
// se reserva para la acción principal y lo seleccionado, así destaca sin recargar.

// ---- Azul de marca ----
val BrandBlue = Color(0xFF1D4ED8)        // acción principal, enlaces, lo seleccionado
val BrandBlueDark = Color(0xFF0B2A6F)    // texto sobre azul muy claro
val BrandBlueSoft = Color(0xFFDCE7FF)    // fondo de lo seleccionado (pestaña, chip)
val BrandBlueLight = Color(0xFF93B4FF)   // azul sobre fondo negro (avisos inferiores)

// ---- Negro y grises (texto y líneas) ----
val Ink = Color(0xFF0A0A0A)              // texto principal
val InkSoft = Color(0xFF5B6472)          // texto secundario
val Line = Color(0xFFE3E7ED)             // divisores
val LineStrong = Color(0xFF9AA3B2)       // bordes de campos

// ---- Blanco y superficies ----
val Paper = Color(0xFFFFFFFF)            // fondo
val Mist50 = Color(0xFFF7F8FA)
val Mist100 = Color(0xFFF3F5F9)          // tarjetas
val Mist200 = Color(0xFFECEFF4)
val Mist300 = Color(0xFFE4E8EF)

// ---- Colores con significado (elegidos para leerse bien sobre blanco) ----
val SuccessGreen = Color(0xFF15803D)     // confirmado, pagado, al día
val WarnAmber = Color(0xFFB45309)        // pendiente, atención
val ErrorRed = Color(0xFFDC2626)         // error, anulado, rechazado
val LiveRed = Color(0xFFE11D2E)          // partido en vivo
val FavoriteGold = Color(0xFFE8A200)     // estrella de favorito
val CardYellow = Color(0xFFFFD60A)       // tarjeta amarilla
val CardRed = Color(0xFFFF453A)          // tarjeta roja

// ---- Cancha (alineación) ----
// El verde solo se usa para dibujar el césped; dos tonos para las franjas cortadas.
val PitchGreen = Color(0xFF2E7D4F)
val PitchGreenStripe = Color(0xFF2A7349)
val PitchLine = Color(0x99FFFFFF)        // líneas blancas semitransparentes
