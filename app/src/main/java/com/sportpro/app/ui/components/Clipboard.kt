package com.sportpro.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * Copia un texto al portapapeles del teléfono (por ejemplo un código de invitación).
 * Se usa el servicio de Android directamente: la API anterior de Compose
 * (LocalClipboardManager) quedó obsoleta.
 */
fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    manager.setPrimaryClip(ClipData.newPlainText("SportPro", text))
}
