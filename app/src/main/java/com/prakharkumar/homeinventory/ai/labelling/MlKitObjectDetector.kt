package com.prakharkumar.homeinventory.ai.labelling

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.prakharkumar.homeinventory.ai.pipeline.DetectedRegion
import com.prakharkumar.homeinventory.ai.pipeline.ObjectDetector
import com.prakharkumar.homeinventory.ai.pipeline.RawLabel
import kotlinx.coroutines.tasks.await

/**
 * Spec 5.2 - locates multiple objects so each can be cropped and inspected
 * separately. Default ML Kit classification is coarse (5 broad categories), so it
 * is used for localisation first and classification only as a weak signal.
 *
 * Note: object-detection ships as an unbundled library, so Play services downloads
 * the model once. Labelling and OCR are bundled, so the pipeline still produces
 * suggestions if the detector is unavailable.
 *
 * To upgrade to a mobile YOLO model later, implement [ObjectDetector] with a
 * LiteRT/TFLite runtime and register it in RecognitionPipeline. Verify the model
 * licence and redistribution terms before bundling any weights.
 */
class MlKitObjectDetector : ObjectDetector {

    override val modelName = "mlkit-object-detection"

    private val detector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
    )

    override suspend fun detect(bitmap: Bitmap): List<DetectedRegion> {
        val input = InputImage.fromBitmap(bitmap, 0)
        return detector.process(input).await().map { obj ->
            DetectedRegion(
                box = obj.boundingBox,
                trackingId = obj.trackingId,
                labels = obj.labels.map {
                    RawLabel(
                        text = it.text,
                        score = it.confidence,
                        sourceModel = modelName,
                        box = obj.boundingBox
                    )
                }
            )
        }
    }

    fun close() = detector.close()
}
