package com.prakharkumar.homeinventory

import com.prakharkumar.homeinventory.ai.pipeline.RawLabel
import com.prakharkumar.homeinventory.ai.ranking.SuggestionRanker
import com.prakharkumar.homeinventory.domain.model.ConfidenceState
import com.prakharkumar.homeinventory.util.normalizeForSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionRankerTest {

    @Test
    fun `agreement between two models outranks a single high score`() {
        val merged = SuggestionRanker.merge(
            listOf(
                RawLabel("Cable", 0.60f, "mlkit-image-labeling"),
                RawLabel("cable", 0.58f, "mlkit-object-detection"),
                RawLabel("Fashion", 0.95f, "mlkit-image-labeling")
            )
        )
        assertEquals("Cable", merged.first().name)
        assertEquals(ConfidenceState.STRONG, merged.first().confidence)
    }

    @Test
    fun `a single model can never reach strong`() {
        val merged = SuggestionRanker.merge(listOf(RawLabel("Shoe", 0.99f, "mlkit-image-labeling")))
        assertTrue(merged.first().confidence != ConfidenceState.STRONG)
    }

    @Test
    fun `low confidence suggestions are not pre-selected`() {
        val merged = SuggestionRanker.merge(listOf(RawLabel("Plant", 0.30f, "mlkit-image-labeling")))
        assertTrue(merged.all { !it.selected })
    }

    @Test
    fun `duplicate labels are merged once`() {
        val merged = SuggestionRanker.merge(
            listOf(
                RawLabel("HDMI Cable", 0.7f, "a"),
                RawLabel("hdmi  cable", 0.6f, "a")
            )
        )
        assertEquals(1, merged.size)
    }

    @Test
    fun `category is inferred offline from the local vocabulary`() {
        val merged = SuggestionRanker.merge(listOf(RawLabel("Screwdriver", 0.7f, "a")))
        assertEquals("Tools", merged.first().category)
    }

    @Test
    fun `normalisation strips case accents and punctuation`() {
        assertEquals("aa batteries", normalizeForSearch("  AA-Batteries!! "))
    }
}
