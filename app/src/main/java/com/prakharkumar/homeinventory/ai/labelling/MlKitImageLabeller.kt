package com.prakharkumar.homeinventory.ai.labelling

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.prakharkumar.homeinventory.ai.pipeline.ImageLabeller
import com.prakharkumar.homeinventory.ai.pipeline.RawLabel
import kotlinx.coroutines.tasks.await

/**
 * Spec 5.1 - fast first-pass, on-device, bundled model. Returns broad labels;
 * it is NOT a product catalogue and must not be presented as certain.
 */
class MlKitImageLabeller(confidenceThreshold: Float = 0.5f) : ImageLabeller {

    override val modelName = "mlkit-image-labeling"

    private val labeller = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(confidenceThreshold)
            .build()
    )

    override suspend fun label(bitmap: Bitmap): List<RawLabel> {
        val input = InputImage.fromBitmap(bitmap, 0)
        return labeller.process(input).await().map {
            RawLabel(text = it.text, score = it.confidence, sourceModel = modelName)
        }
    }

    fun close() = labeller.close()
}
