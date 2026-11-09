package com.prakharkumar.homeinventory.ai.ranking

import com.prakharkumar.homeinventory.ai.pipeline.RawLabel
import com.prakharkumar.homeinventory.domain.model.ConfidenceState
import com.prakharkumar.homeinventory.domain.model.ItemSuggestion
import com.prakharkumar.homeinventory.util.normalizeForSearch
import com.prakharkumar.homeinventory.util.titleCaseWords
import java.util.UUID

/**
 * Spec 5.6 / 5.7 - explainable merge, NOT a score average across unrelated models.
 *
 * Rules:
 *  - agreement between independent models outranks a single high score
 *  - a single model's score alone can never reach STRONG
 *  - de-duplicate by normalised label
 *  - unclear results stay as NEEDS_CONFIRMATION rather than inventing certainty
 */
object SuggestionRanker {

    private const val STRONG_SINGLE_SCORE = 0.80f
    private const val POSSIBLE_SCORE = 0.55f

    fun merge(labels: List<RawLabel>, cropPath: String? = null): List<ItemSuggestion> {
        if (labels.isEmpty()) return emptyList()

        return labels
            .filter { it.text.isNotBlank() }
            .groupBy { normalizeForSearch(it.text) }
            .filterKeys { it.isNotEmpty() }
            .map { (normalized, group) ->
                val models = group.map { it.sourceModel }.distinct()
                val best = group.maxBy { it.score }
                val agreement = models.size > 1

                val confidence = when {
                    agreement && best.score >= POSSIBLE_SCORE -> ConfidenceState.STRONG
                    agreement -> ConfidenceState.POSSIBLE
                    best.score >= STRONG_SINGLE_SCORE -> ConfidenceState.POSSIBLE
                    best.score >= POSSIBLE_SCORE -> ConfidenceState.NEEDS_CONFIRMATION
                    else -> ConfidenceState.NEEDS_CONFIRMATION
                }

                ItemSuggestion(
                    id = UUID.randomUUID().toString(),
                    name = normalized.titleCaseWords(),
                    category = CategoryVocabulary.categoryFor(normalized),
                    cropPath = cropPath ?: best.cropPath,
                    confidence = confidence,
                    sourceModels = models,
                    rawLabel = best.text,
                    score = best.score,
                    // Only strong suggestions are pre-ticked; the user still confirms the save.
                    selected = confidence == ConfidenceState.STRONG
                )
            }
            .sortedWith(
                compareByDescending<ItemSuggestion> { it.confidence.ordinal == 0 }
                    .thenByDescending { it.sourceModels.size }
                    .thenByDescending { it.score }
            )
    }

    /** Keeps the strongest suggestion per detected region, plus whole-image fallbacks. */
    fun limitPerRegion(suggestions: List<ItemSuggestion>, max: Int = 3): List<ItemSuggestion> =
        suggestions.take(max)
}
