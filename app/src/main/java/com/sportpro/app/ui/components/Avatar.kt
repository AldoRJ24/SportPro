package com.sportpro.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sportpro.app.SupabaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Caché en memoria de fotos de perfil (URL pública; la ruta cambia con cada foto nueva, así nunca se ve una vieja). */
object AvatarCache {
    private val cache = LruCache<String, ImageBitmap>(40)

    fun peek(url: String): ImageBitmap? = cache.get(url)

    suspend fun load(url: String): ImageBitmap? {
        cache.get(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                try {
                    if (conn.responseCode != 200) return@runCatching null
                    conn.inputStream.use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
                } finally {
                    conn.disconnect()
                }
            }.getOrNull()
        }?.also { cache.put(url, it) }
    }

    fun put(url: String, image: ImageBitmap) {
        cache.put(url, image)
    }
}

/** Foto de perfil si la persona tiene una; si no, el escudo con sus iniciales. Se descarga en segundo plano. */
@Composable
fun Avatar(name: String, avatarPath: String?, size: Dp = 48.dp, modifier: Modifier = Modifier, ring: Color? = null) =
    RemoteCircleImage(name, SupabaseProvider.avatarUrl(avatarPath), size, modifier, ring)

/**
 * Escudo de un equipo o academia: la foto de la academia si el administrador subió una;
 * si no, las iniciales. Todos los equipos de una academia comparten la misma foto.
 */
@Composable
fun TeamCrest(name: String, crestPath: String?, size: Dp = 48.dp, modifier: Modifier = Modifier, ring: Color? = null) =
    RemoteCircleImage(name, SupabaseProvider.academyLogoUrl(crestPath), size, modifier, ring)

// Descarga la imagen (con caché) y la muestra en círculo; mientras carga o si no hay, muestra las iniciales.
@Composable
private fun RemoteCircleImage(name: String, url: String?, size: Dp, modifier: Modifier, ring: Color?) {
    val image by produceState(initialValue = url?.let { AvatarCache.peek(it) }, url) {
        value = url?.let { AvatarCache.load(it) }
    }
    val img = image
    if (img == null) {
        Crest(name, size, modifier, ring)
    } else {
        Image(
            bitmap = img,
            contentDescription = "Imagen de $name",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .then(if (ring != null) Modifier.border(2.dp, ring, CircleShape) else Modifier)
                .clip(CircleShape),
        )
    }
}

/**
 * Reads the picked image (EXIF rotation applied by ImageDecoder), crops it square,
 * scales it to 512 px and compresses it to JPEG (~50–120 KB) before uploading.
 */
fun prepareAvatarJpeg(context: Context, uri: Uri, sizePx: Int = 512): ByteArray {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        val w = info.size.width
        val h = info.size.height
        val scale = (sizePx * 1.5f) / minOf(w, h)
        if (scale < 1f) decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
    val side = minOf(decoded.width, decoded.height)
    val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
    val scaled = if (side > sizePx) Bitmap.createScaledBitmap(square, sizePx, sizePx, true) else square
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
    return out.toByteArray()
}
