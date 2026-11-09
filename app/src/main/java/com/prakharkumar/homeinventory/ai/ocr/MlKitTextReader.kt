package com.prakharkumar.homeinventory.ai.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.prakharkumar.homeinventory.ai.pipeline.TextReader
import kotlinx.coroutines.tasks.await

/**
 * Spec 5.5 - reads packaging/label text. OCR can be wrong; the text is offered as
 * an editable clue and an extra search field, never as a confirmed identity.
 */
class MlKitTextReader : TextReader {

    override val modelName = "mlkit-text-recognition"

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun read(bitmap: Bitmap): String {
        val input = InputImage.fromBitmap(bitmap, 0)
        return recognizer.process(input).await().text.trim()
    }

    fun close() = recognizer.close()
}
