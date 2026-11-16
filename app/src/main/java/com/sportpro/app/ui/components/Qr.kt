package com.sportpro.app.ui.components
import androidx.compose.ui.res.stringResource
import com.sportpro.app.R
import com.sportpro.app.core.AppText

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** Genera el QR (negro sobre blanco) con ZXing dentro del celular: el código no pasa por servicios externos. */
fun qrBitmap(text: String, sizePx: Int = 640): ImageBitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 1)
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val w = matrix.width
    val h = matrix.height
    val pixels = IntArray(w * h)
    for (y in 0 until h) {
        val row = y * w
        for (x in 0 until w) {
            pixels[row + x] = if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
    }
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    bmp.setPixels(pixels, 0, w, 0, 0, w, h)
    return bmp.asImageBitmap()
}

/** Muestra un código QR sobre fondo blanco con esquinas redondeadas. Se genera una sola vez por texto. */
@Composable
fun QrImage(text: String, modifier: Modifier = Modifier, size: Dp = 240.dp) {
    val image = remember(text) { qrBitmap(text) }
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(10.dp),
    ) {
        Image(image, contentDescription = stringResource(R.string.qr_codigo_qr_de_invitacion), modifier = Modifier.size(size))
    }
}

/**
 * Abre el escáner de códigos de Google (Play services; no necesita permiso de cámara).
 * [onResult] recibe el texto leído, o null si se canceló; [onError] recibe un mensaje legible.
 */
fun scanQr(context: Context, onResult: (String?) -> Unit, onError: (String) -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .build()
    GmsBarcodeScanning.getClient(context, options)
        .startScan()
        .addOnSuccessListener { barcode -> onResult(barcode.rawValue) }
        .addOnCanceledListener { onResult(null) }
        .addOnFailureListener { e -> onError(e.message ?: AppText.get(R.string.qr_no_se_pudo_abrir_el)) }
}
