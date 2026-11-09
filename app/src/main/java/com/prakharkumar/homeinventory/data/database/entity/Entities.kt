package com.prakharkumar.homeinventory.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Physical storage location. Tree is built with [parentId].
 * Spec 9.1 - locationType keeps HOME/ROOM/DRAWER/BOX semantics so the data model
 * already supports multiple top-level spaces even if v1 UI shows one home.
 */
@Entity(
    tableName = "storage_location",
    foreignKeys = [
        ForeignKey(
            entity = StorageLocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("parentId"), Index("name")]
)
data class StorageLocationEntity(
    @PrimaryKey val id: String,
    val parentId: String?,
    val name: String,
    val description: String? = null,
    val photoPath: String? = null,
    val locationType: String = "OTHER",
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val archivedAt: Long? = null
)

/** Spec 9.2 - a confirmed inventory record. Always has a location. */
@Entity(
    tableName = "inventory_item",
    foreignKeys = [
        ForeignKey(
            entity = StorageLocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("locationId"), Index("normalizedName"), Index("barcode")]
)
data class InventoryItemEntity(
    @PrimaryKey val id: String,
    val locationId: String,
    val name: String,
    val normalizedName: String,
    val category: String? = null,
    val description: String? = null,
    val quantity: Double = 1.0,
    val unit: String? = null,
    val photoPath: String? = null,
    val sourcePhotoId: String? = null,
    val barcode: String? = null,
    val serialNumber: String? = null,
    val notes: String? = null,
    val recognitionStatus: String = "MANUAL",
    val createdAt: Long,
    val updatedAt: Long,
    val archivedAt: Long? = null
)

/** Spec 9.3 - file paths only, never blobs. */
@Entity(tableName = "inventory_photo")
data class InventoryPhotoEntity(
    @PrimaryKey val id: String,
    val localPath: String,
    val thumbnailPath: String? = null,
    val width: Int,
    val height: Int,
    val mimeType: String = "image/jpeg",
    val fileSize: Long = 0L,
    val perceptualHash: String? = null,
    val captureSessionId: String? = null,
    val createdAt: Long
)

/**
 * Spec 9.4 - AI output is kept OUT of the inventory tables until the user confirms.
 */
@Entity(
    tableName = "recognition_suggestion",
    foreignKeys = [
        ForeignKey(
            entity = InventoryPhotoEntity::class,
            parentColumns = ["id"],
            childColumns = ["photoId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("photoId")]
)
data class RecognitionSuggestionEntity(
    @PrimaryKey val id: String,
    val photoId: String,
    val itemCropPath: String? = null,
    val suggestedName: String,
    val suggestedCategory: String? = null,
    val suggestedDescription: String? = null,
    val sourceModel: String,
    val rawModelLabel: String? = null,
    val modelScore: Float = 0f,
    val confidenceState: String = "NEEDS_CONFIRMATION",
    val reviewState: String = "PENDING",
    val createdAt: Long
)

/** Spec 9.5 - movement history; moving must never duplicate an item. */
@Entity(
    tableName = "item_movement",
    foreignKeys = [
        ForeignKey(
            entity = InventoryItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("itemId")]
)
data class ItemMovementEntity(
    @PrimaryKey val id: String,
    val itemId: String,
    val fromLocationId: String?,
    val toLocationId: String,
    val movedAt: Long,
    val note: String? = null
)
