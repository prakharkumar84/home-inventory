package com.prakharkumar.homeinventory.domain.model

enum class LocationType { HOME, ROOM, CUPBOARD, DRAWER, BOX, SHELF, OTHER }

enum class RecognitionStatus { MANUAL, CONFIRMED, EDITED }

/** Spec 5.7 - app-level states, NOT calibrated probabilities. */
enum class ConfidenceState { STRONG, POSSIBLE, NEEDS_CONFIRMATION }

data class StorageLocation(
    val id: String,
    val parentId: String?,
    val name: String,
    val description: String? = null,
    val photoPath: String? = null,
    val type: LocationType = LocationType.OTHER,
    val sortOrder: Int = 0,
    val itemCount: Int = 0
)

data class LocationNode(
    val location: StorageLocation,
    val depth: Int,
    val children: List<LocationNode> = emptyList()
)

data class InventoryItem(
    val id: String,
    val locationId: String,
    val name: String,
    val category: String? = null,
    val description: String? = null,
    val quantity: Double = 1.0,
    val unit: String? = null,
    val photoPath: String? = null,
    val barcode: String? = null,
    val serialNumber: String? = null,
    val notes: String? = null,
    val status: RecognitionStatus = RecognitionStatus.MANUAL,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class SearchResult(
    val item: InventoryItem,
    val breadcrumb: String
)

/** One editable row in the review screen (spec 12.5). */
data class ItemSuggestion(
    val id: String,
    val name: String,
    val category: String? = null,
    val description: String? = null,
    val quantity: Double = 1.0,
    val cropPath: String? = null,
    val confidence: ConfidenceState = ConfidenceState.NEEDS_CONFIRMATION,
    val sourceModels: List<String> = emptyList(),
    val rawLabel: String? = null,
    val score: Float = 0f,
    val selected: Boolean = false,
    val userEdited: Boolean = false
)

data class RecognitionOutcome(
    val photoId: String,
    val photoPath: String,
    val suggestions: List<ItemSuggestion>,
    val ocrText: String? = null,
    val failureMessage: String? = null
)

data class ItemMovement(
    val id: String,
    val itemId: String,
    val fromLocationName: String?,
    val toLocationName: String,
    val movedAt: Long,
    val note: String?
)
