package com.prakharkumar.homeinventory.ai.pipeline

import android.graphics.Bitmap
import com.prakharkumar.homeinventory.ai.ranking.SuggestionRanker
import com.prakharkumar.homeinventory.data.database.dao.PhotoDao
import com.prakharkumar.homeinventory.data.database.dao.SuggestionDao
import com.prakharkumar.homeinventory.data.database.entity.InventoryPhotoEntity
import com.prakharkumar.homeinventory.data.database.entity.RecognitionSuggestionEntity
import com.prakharkumar.homeinventory.data.files.PhotoStorage
import com.prakharkumar.homeinventory.domain.model.ItemSuggestion
import com.prakharkumar.homeinventory.domain.model.RecognitionOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Spec 5.6 - orchestration. Fast signal first, heavier work after, every stage
 * optional and individually failure-tolerant. Recognition failure must never
 * block manual entry (spec 18).
 */
class RecognitionPipeline(
    private val labeller: ImageLabeller,
    private val detector: ObjectDetector,
    private val textReader: TextReader,
    private val photoStorage: PhotoStorage,
    private val photoDao: PhotoDao,
    private val suggestionDao: SuggestionDao
) {

    data class Options(
        val multiObject: Boolean = true,
        val runOcr: Boolean = true,
        val maxRegions: Int = 8
    )

    suspend fun run(
        photoFile: File,
        options: Options = Options(),
        onStage: (String) -> Unit = {}
    ): RecognitionOutcome = withContext(Dispatchers.Default) {

        val photoId = UUID.randomUUID().toString()

        val bitmap: Bitmap = try {
            onStage("Preparing image")
            photoStorage.loadOriented(photoFile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext RecognitionOutcome(
                photoId = photoId,
                photoPath = photoFile.absolutePath,
                suggestions = emptyList(),
                failureMessage = "This image could not be read. You can still add items manually."
            )
        }

        val thumbPath = runCatching { photoStorage.saveThumbnail(bitmap) }.getOrNull()
        val hash = runCatching { photoStorage.perceptualHash(bitmap) }.getOrNull()

        photoDao.upsert(
            InventoryPhotoEntity(
                id = photoId,
                localPath = photoFile.absolutePath,
                thumbnailPath = thumbPath,
                width = bitmap.width,
                height = bitmap.height,
                fileSize = photoFile.length(),
                perceptualHash = hash,
                createdAt = System.currentTimeMillis()
            )
        )

        val suggestions = mutableListOf<ItemSuggestion>()
        val failures = mutableListOf<String>()

        // Stage 1: whole-image labels (fast first pass).
        onStage("Looking at the photo")
        val wholeImageLabels = runCatching { labeller.label(bitmap) }
            .onFailure { failures += "labelling" }
            .getOrDefault(emptyList())

        // Stage 2: region detection, then per-crop labelling.
        if (options.multiObject) {
            onStage("Finding separate objects")
            val regions = runCatching { detector.detect(bitmap) }
                .onFailure { failures += "detection" }
                .getOrDefault(emptyList())
                .sortedByDescending { it.box.width() * it.box.height() }
                .take(options.maxRegions)

            regions.forEach { region ->
                val cropPath = runCatching { photoStorage.saveCrop(bitmap, region.box) }.getOrNull()
                val cropLabels = if (cropPath != null) {
                    runCatching {
                        labeller.label(photoStorage.loadOriented(File(cropPath), maxEdge = 640))
                    }.getOrDefault(emptyList())
                } else emptyList()

                val merged = SuggestionRanker.merge(region.labels + cropLabels, cropPath)
                suggestions += SuggestionRanker.limitPerRegion(merged, max = 2)
            }
        }

        // Whole-image labels fill in when no region produced anything useful.
        if (suggestions.isEmpty()) {
            suggestions += SuggestionRanker.merge(wholeImageLabels).take(5)
        } else {
            val known = suggestions.map { it.name.lowercase() }.toSet()
            suggestions += SuggestionRanker.merge(wholeImageLabels)
                .filter { it.name.lowercase() !in known }
                .take(2)
        }

        // Stage 3: OCR as an extra clue and search field.
        val ocrText = if (options.runOcr) {
            onStage("Reading any text")
            runCatching { textReader.read(bitmap) }
                .onFailure { failures += "ocr" }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
        } else null

        val finalSuggestions = suggestions.distinctBy { it.name.lowercase() }

        // Suggestions are persisted separately from inventory (spec 9.4).
        suggestionDao.clearForPhoto(photoId)
        suggestionDao.upsertAll(
            finalSuggestions.map {
                RecognitionSuggestionEntity(
                    id = it.id,
                    photoId = photoId,
                    itemCropPath = it.cropPath,
                    suggestedName = it.name,
                    suggestedCategory = it.category,
                    suggestedDescription = it.description,
                    sourceModel = it.sourceModels.joinToString(","),
                    rawModelLabel = it.rawLabel,
                    modelScore = it.score,
                    confidenceState = it.confidence.name,
                    reviewState = "PENDING",
                    createdAt = System.currentTimeMillis()
                )
            }
        )

        RecognitionOutcome(
            photoId = photoId,
            photoPath = photoFile.absolutePath,
            suggestions = finalSuggestions,
            ocrText = ocrText,
            failureMessage = when {
                finalSuggestions.isEmpty() && failures.isNotEmpty() ->
                    "Recognition did not finish. Add the items manually below."
                finalSuggestions.isEmpty() ->
                    "Nothing recognisable was found. Add the items manually below."
                else -> null
            }
        )
    }
}
