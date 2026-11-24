package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import com.example.data.Reel
import com.example.data.ReelChunk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

data class PreparedReelVideo(
    val durationSec: Long,
    val thumbnailBase64: String,
    val chunksBase64: List<String>,
    val mimeType: String = "video/mp4"
)

/**
 * Prepares short vertical Reels videos (<= 30 seconds) for Firestore-backed chunk storage
 * and reconstructs cached local .mp4 files for smooth TextureView + MediaPlayer playback.
 */
object ReelVideoHelper {

    private const val MAX_DURATION_MS = 30_900L // allow small rounding up to 30.9s -> coerced to 30s
    private const val CHUNK_RAW_BYTES = 460_000 // ~613k Base64 chars (< 680k Firestore rule bound)
    private const val MAX_CHUNKS = 16
    private const val MAX_TOTAL_RAW_BYTES = CHUNK_RAW_BYTES * MAX_CHUNKS // ~7.36 MB

    suspend fun prepareVideoForUpload(
        context: Context,
        uri: Uri
    ): Result<PreparedReelVideo> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L

            if (durationMs <= 0L) {
                return@withContext Result.failure(
                    IllegalArgumentException("Could not read video duration. Please pick an MP4 clip.")
                )
            }
            if (durationMs > MAX_DURATION_MS) {
                val actualSec = ((durationMs + 500L) / 1000L)
                return@withContext Result.failure(
                    IllegalArgumentException("Reels must be 30 seconds or shorter (selected video is ${actualSec}s).")
                )
            }
            val durationSec = ((durationMs + 500L) / 1000L)
                .coerceIn(1L, Reel.MAX_DURATION_SECONDS.toLong())

            // Extract compressed JPEG cover thumbnail
            val frame = retriever.getFrameAtTime(
                minOf(500_000L, durationMs * 500L),
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )
            val thumbnailBase64 = if (frame != null) {
                encodeThumbnail(frame)
            } else {
                ""
            }

            // Read raw video bytes (bounded so upload fits cleanly into <= 16 Firestore chunks)
            val rawBytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(32_768)
                var total = 0
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    total += read
                    if (total > MAX_TOTAL_RAW_BYTES) {
                        return@withContext Result.failure(
                            IllegalArgumentException(
                                "Video file is too large (max ~7 MB for 30s Reel). Record in 720p or trim the clip shorter."
                            )
                        )
                    }
                    out.write(buf, 0, read)
                }
                out.toByteArray()
            } ?: return@withContext Result.failure(
                IllegalArgumentException("Unable to open selected video file.")
            )

            if (rawBytes.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Selected video file is empty."))
            }

            val chunks = ArrayList<String>()
            var offset = 0
            while (offset < rawBytes.size) {
                val len = minOf(CHUNK_RAW_BYTES, rawBytes.size - offset)
                val encoded = Base64.encodeToString(rawBytes, offset, len, Base64.NO_WRAP)
                chunks.add(encoded)
                offset += len
            }

            if (chunks.isEmpty() || chunks.size > MAX_CHUNKS) {
                return@withContext Result.failure(
                    IllegalArgumentException("Video exceeds maximum chunk count ($MAX_CHUNKS). Try a shorter clip.")
                )
            }

            Result.success(
                PreparedReelVideo(
                    durationSec = durationSec,
                    thumbnailBase64 = thumbnailBase64,
                    chunksBase64 = chunks,
                    mimeType = "video/mp4"
                )
            )
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException(e.message ?: "Failed to process video."))
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun encodeThumbnail(bitmap: Bitmap): String {
        val maxDim = 480
        val width = bitmap.width.coerceAtLeast(1)
        val height = bitmap.height.coerceAtLeast(1)
        val scale = minOf(1f, maxDim.toFloat() / maxOf(width, height).toFloat())
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (width * scale).toInt().coerceAtLeast(1),
                (height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }
        val baos = ByteArrayOutputStream()
        var quality = 76
        var encoded = ""
        while (quality >= 36) {
            baos.reset()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            encoded = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            if (encoded.length <= 125_000) break
            quality -= 12
        }
        if (scaled !== bitmap) runCatching { scaled.recycle() }
        runCatching { bitmap.recycle() }
        return encoded
    }

    suspend fun getOrAssembleCachedVideoFile(
        context: Context,
        reelId: String,
        loadChunks: suspend () -> Result<List<ReelChunk>>
    ): Result<File> = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "reels_cache").apply { mkdirs() }
        val safeName = reelId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val target = File(dir, "$safeName.mp4")
        if (target.exists() && target.length() > 0L) {
            return@withContext Result.success(target)
        }

        val chunksResult = loadChunks()
        val chunks = chunksResult.getOrElse { return@withContext Result.failure(it) }
        if (chunks.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Video data not found."))
        }

        val tmpFile = File(dir, "$safeName.tmp")
        return@withContext try {
            FileOutputStream(tmpFile).use { fos ->
                chunks.sortedBy { it.chunkIndex }.forEach { chunk ->
                    val decoded = Base64.decode(chunk.dataBase64, Base64.DEFAULT)
                    fos.write(decoded)
                }
            }
            if (target.exists()) target.delete()
            if (!tmpFile.renameTo(target)) {
                tmpFile.copyTo(target, overwrite = true)
                tmpFile.delete()
            }
            Result.success(target)
        } catch (e: Exception) {
            runCatching { tmpFile.delete() }
            Result.failure(e)
        }
    }
}
