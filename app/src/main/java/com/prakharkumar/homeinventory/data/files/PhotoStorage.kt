package com.prakharkumar.homeinventory.data.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Spec 11 - photos live in app-private storage, database stores paths only.
 */
class PhotoStorage(private val context: Context) {

    private val photoDir: File by lazy { File(context.filesDir, "photos").apply { mkdirs() } }
    private val thumbDir: File by lazy { File(context.filesDir, "thumbs").apply { mkdirs() } }
    private val cropDir: File by lazy { File(context.filesDir, "crops").apply { mkdirs() } }

    fun newPhotoFile(): File = File(photoDir, "${UUID.randomUUID()}.jpg")

    suspend fun importFromUri(uri: Uri): File = withContext(Dispatchers.IO) {
        val target = newPhotoFile()
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        } ?: error("Could not read the selected image.")
        target
    }

    /** Spec 5.6 step 1-2: fix orientation, keep a reduced working copy for inference. */
    suspend fun loadOriented(file: File, maxEdge: Int = 1600): Bitmap = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxEdge) sample *= 2

        val bitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: error("Unsupported or corrupt image.")

        val rotation = when (ExifInterface(file.absolutePath)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (rotation == 0f) bitmap
        else Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height,
            Matrix().apply { postRotate(rotation) }, true
        )
    }

    /** Spec 5.6 step 5: crop detected region with a small margin. */
    suspend fun saveCrop(source: Bitmap, box: Rect, marginRatio: Float = 0.06f): String? =
        withContext(Dispatchers.IO) {
            val mx = (box.width() * marginRatio).toInt()
            val my = (box.height() * marginRatio).toInt()
            val left = (box.left - mx).coerceAtLeast(0)
            val top = (box.top - my).coerceAtLeast(0)
            val right = (box.right + mx).coerceAtMost(source.width)
            val bottom = (box.bottom + my).coerceAtMost(source.height)
            if (right - left < 24 || bottom - top < 24) return@withContext null

            val crop = Bitmap.createBitmap(source, left, top, right - left, bottom - top)
            val file = File(cropDir, "${UUID.randomUUID()}.jpg")
            FileOutputStream(file).use { crop.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            file.absolutePath
        }

    suspend fun saveThumbnail(source: Bitmap, edge: Int = 320): String = withContext(Dispatchers.IO) {
        val scale = edge.toFloat() / maxOf(source.width, source.height)
        val thumb = if (scale >= 1f) source else Bitmap.createScaledBitmap(
            source, (source.width * scale).toInt(), (source.height * scale).toInt(), true
        )
        val file = File(thumbDir, "${UUID.randomUUID()}.jpg")
        FileOutputStream(file).use { thumb.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        file.absolutePath
    }

    suspend fun writeJpeg(bitmap: Bitmap, target: File): File = withContext(Dispatchers.IO) {
        FileOutputStream(target).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        target
    }

    suspend fun deleteIfUnused(path: String?, stillUsed: Boolean) = withContext(Dispatchers.IO) {
        if (path != null && !stillUsed) runCatching { File(path).delete() }
        Unit
    }

    suspend fun usageBytes(): Long = withContext(Dispatchers.IO) {
        listOf(photoDir, thumbDir, cropDir).sumOf { dir ->
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }
    }

    /** Spec 13 - cheap 64-bit average hash, used only as a weak duplicate clue. */
    suspend fun perceptualHash(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        val small = Bitmap.createScaledBitmap(bitmap, 8, 8, true)
        val grays = IntArray(64)
        for (y in 0 until 8) for (x in 0 until 8) {
            val p = small.getPixel(x, y)
            grays[y * 8 + x] = ((p shr 16 and 0xFF) * 299 + (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
        }
        val avg = grays.average()
        buildString { grays.forEach { append(if (it >= avg) '1' else '0') } }
    }
}
