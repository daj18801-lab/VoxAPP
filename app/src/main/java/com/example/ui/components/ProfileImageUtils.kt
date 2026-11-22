package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

private object Base64BitmapCache {
    private val cache = LruCache<String, ImageBitmap>(48)

    fun decode(base64: String): ImageBitmap? {
        if (base64.isBlank()) return null
        val key = "${base64.length}_${base64.hashCode()}"
        cache.get(key)?.let { return it }
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val imageBitmap = bmp.asImageBitmap()
            cache.put(key, imageBitmap)
            imageBitmap
        } catch (_: Exception) {
            null
        }
    }
}

@Composable
fun rememberBase64ImageBitmap(base64: String): ImageBitmap? {
    return remember(base64) {
        if (base64.isBlank()) null else Base64BitmapCache.decode(base64)
    }
}

/**
 * Decodes a Photo Picker image URI safely with inSampleSize downsampling, optionally center-crops
 * to a square (for profile avatars), scales down to [maxDimensionPx], and compresses to JPEG
 * Base64 staying strictly within [maxBase64Chars] so Firestore documents remain fast & well under 1 MiB.
 */
suspend fun encodeImageUriToBase64Jpeg(
    context: Context,
    uri: Uri,
    maxDimensionPx: Int = 640,
    squareCrop: Boolean = false,
    maxBase64Chars: Int = 360_000
): String? = withContext(Dispatchers.IO) {
    try {
        val resolver = context.contentResolver

        // Step 1: Read bounds only
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOpts)
        }
        val origW = boundsOpts.outWidth
        val origH = boundsOpts.outHeight
        if (origW <= 0 || origH <= 0) return@withContext null

        // Step 2: Calculate power-of-two sample size to avoid OOM on large camera photos
        var sampleSize = 1
        while ((origW / sampleSize) > maxDimensionPx * 2 || (origH / sampleSize) > maxDimensionPx * 2) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: return@withContext null

        // Step 3: Optional square center crop (for avatars)
        val cropped = if (squareCrop && decoded.width != decoded.height) {
            val side = min(decoded.width, decoded.height)
            val left = (decoded.width - side) / 2
            val top = (decoded.height - side) / 2
            Bitmap.createBitmap(decoded, left, top, side, side).also {
                if (it != decoded) decoded.recycle()
            }
        } else {
            decoded
        }

        // Step 4: Scale proportionally to fit maxDimensionPx
        val longest = max(cropped.width, cropped.height)
        val scaled = if (longest > maxDimensionPx) {
            val ratio = maxDimensionPx.toFloat() / longest.toFloat()
            val targetW = (cropped.width * ratio).toInt().coerceAtLeast(1)
            val targetH = (cropped.height * ratio).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(cropped, targetW, targetH, true).also {
                if (it != cropped) cropped.recycle()
            }
        } else {
            cropped
        }

        // Step 5: Iteratively compress JPEG until Base64 fits target budget
        val qualities = intArrayOf(78, 65, 52, 40, 30)
        var encoded: String? = null
        for (q in qualities) {
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, q, out)
            val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            if (b64.length <= maxBase64Chars) {
                encoded = b64
                break
            }
        }
        scaled.recycle()
        encoded
    } catch (_: Exception) {
        null
    }
}

@Composable
fun FullScreenPhotoDialog(
    photoBase64: String,
    contentDescription: String,
    onDismiss: () -> Unit
) {
    val bitmap = rememberBase64ImageBitmap(photoBase64) ?: return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
            )
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.65f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 24.dp, end = 8.dp)
                    .size(48.dp)
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close photo viewer",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
