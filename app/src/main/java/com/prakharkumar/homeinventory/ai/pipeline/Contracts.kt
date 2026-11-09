package com.prakharkumar.homeinventory.ai.pipeline

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * Spec 7 / 22.6 - every model sits behind an adapter interface so a model can be
 * swapped (ML Kit -> YOLO -> MobileCLIP) without touching the database or UI.
 */

data class RawLabel(
    val text: String,
    val score: Float,
    val sourceModel: String,
    val box: Rect? = null,
    val cropPath: String? = null
)

interface ImageLabeller {
    val modelName: String
    suspend fun label(bitmap: Bitmap): List<RawLabel>
}

interface ObjectDetector {
    val modelName: String
    /** Returns detected regions; classification may be absent or coarse. */
    suspend fun detect(bitmap: Bitmap): List<DetectedRegion>
}

data class DetectedRegion(
    val box: Rect,
    val labels: List<RawLabel> = emptyList(),
    val trackingId: Int? = null
)

interface TextReader {
    val modelName: String
    suspend fun read(bitmap: Bitmap): String
}

/**
 * Placeholder seam for Milestone 5 (MobileCLIP or another compact image-text model).
 * Not wired into the default pipeline; similarity is ranking, never proof.
 */
interface ImageTextMatcher {
    val modelName: String
    suspend fun rank(crop: Bitmap, candidates: List<String>): List<RawLabel>
}
