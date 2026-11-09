package com.prakharkumar.homeinventory.data.repository

import com.prakharkumar.homeinventory.data.database.dao.ItemDao
import com.prakharkumar.homeinventory.data.database.dao.MovementDao
import com.prakharkumar.homeinventory.data.database.dao.PhotoDao
import com.prakharkumar.homeinventory.data.database.entity.InventoryItemEntity
import com.prakharkumar.homeinventory.data.database.entity.ItemMovementEntity
import com.prakharkumar.homeinventory.data.files.PhotoStorage
import com.prakharkumar.homeinventory.domain.model.*
import com.prakharkumar.homeinventory.domain.repository.ItemRepository
import com.prakharkumar.homeinventory.domain.repository.LocationRepository
import com.prakharkumar.homeinventory.util.normalizeForSearch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class ItemRepositoryImpl(
    private val itemDao: ItemDao,
    private val movementDao: MovementDao,
    private val photoDao: PhotoDao,
    private val locationRepository: LocationRepository,
    private val photoStorage: PhotoStorage
) : ItemRepository {

    private fun InventoryItemEntity.toDomain() = InventoryItem(
        id = id,
        locationId = locationId,
        name = name,
        category = category,
        description = description,
        quantity = quantity,
        unit = unit,
        photoPath = photoPath,
        barcode = barcode,
        serialNumber = serialNumber,
        notes = notes,
        status = runCatching { RecognitionStatus.valueOf(recognitionStatus) }
            .getOrDefault(RecognitionStatus.MANUAL),
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    override fun observeByLocation(locationId: String) =
        itemDao.observeByLocation(locationId).map { list -> list.map { it.toDomain() } }

    override fun observeRecent(limit: Int) =
        itemDao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    override fun observeItem(id: String) = itemDao.observeById(id).map { it?.toDomain() }

    override fun observeItemCount(): Flow<Int> = itemDao.observeItemTotal()

    override fun observeMovements(itemId: String) =
        movementDao.observeForItem(itemId).map { list ->
            list.map {
                ItemMovement(
                    id = it.id,
                    itemId = it.itemId,
                    fromLocationName = it.fromLocationId,
                    toLocationName = it.toLocationId,
                    movedAt = it.movedAt,
                    note = it.note
                )
            }
        }

    override suspend fun search(query: String): List<SearchResult> {
        val q = normalizeForSearch(query)
        if (q.isEmpty()) return emptyList()
        val breadcrumbCache = mutableMapOf<String, String>()
        return itemDao.search(q).map { entity ->
            SearchResult(
                item = entity.toDomain(),
                breadcrumb = breadcrumbCache.getOrPut(entity.locationId) {
                    locationRepository.breadcrumb(entity.locationId)
                }
            )
        }
    }

    /** Spec 2.2 / 12.5 - only user-confirmed suggestions ever reach the inventory table. */
    override suspend fun saveConfirmed(
        items: List<ItemSuggestion>,
        locationId: String,
        photoPath: String?,
        photoId: String?
    ): List<String> {
        val now = System.currentTimeMillis()
        val entities = items.map { suggestion ->
            InventoryItemEntity(
                id = UUID.randomUUID().toString(),
                locationId = locationId,
                name = suggestion.name.trim(),
                normalizedName = normalizeForSearch(suggestion.name),
                category = suggestion.category?.takeIf { it.isNotBlank() },
                description = suggestion.description?.takeIf { it.isNotBlank() },
                quantity = suggestion.quantity,
                photoPath = suggestion.cropPath ?: photoPath,
                sourcePhotoId = photoId,
                recognitionStatus = when {
                    suggestion.sourceModels.isEmpty() -> RecognitionStatus.MANUAL.name
                    suggestion.userEdited -> RecognitionStatus.EDITED.name
                    else -> RecognitionStatus.CONFIRMED.name
                },
                createdAt = now,
                updatedAt = now
            )
        }
        itemDao.upsertAll(entities)
        entities.forEach {
            movementDao.insert(
                ItemMovementEntity(
                    id = UUID.randomUUID().toString(),
                    itemId = it.id,
                    fromLocationId = null,
                    toLocationId = locationId,
                    movedAt = now,
                    note = "Added"
                )
            )
        }
        return entities.map { it.id }
    }

    override suspend fun upsertManual(item: InventoryItem): String {
        val now = System.currentTimeMillis()
        val id = item.id.ifBlank { UUID.randomUUID().toString() }
        itemDao.upsert(
            InventoryItemEntity(
                id = id,
                locationId = item.locationId,
                name = item.name.trim(),
                normalizedName = normalizeForSearch(item.name),
                category = item.category,
                description = item.description,
                quantity = item.quantity,
                unit = item.unit,
                photoPath = item.photoPath,
                barcode = item.barcode,
                serialNumber = item.serialNumber,
                notes = item.notes,
                recognitionStatus = item.status.name,
                createdAt = if (item.createdAt == 0L) now else item.createdAt,
                updatedAt = now
            )
        )
        return id
    }

    override suspend fun update(item: InventoryItem) {
        val existing = itemDao.byId(item.id) ?: return
        itemDao.upsert(
            existing.copy(
                name = item.name.trim(),
                normalizedName = normalizeForSearch(item.name),
                category = item.category,
                description = item.description,
                quantity = item.quantity,
                unit = item.unit,
                barcode = item.barcode,
                serialNumber = item.serialNumber,
                notes = item.notes,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /** Spec 2.4 - move updates the record and logs history; it never duplicates. */
    override suspend fun move(itemId: String, toLocationId: String, note: String?) {
        val existing = itemDao.byId(itemId) ?: return
        if (existing.locationId == toLocationId) return
        val now = System.currentTimeMillis()
        itemDao.updateLocation(itemId, toLocationId, now)
        movementDao.insert(
            ItemMovementEntity(
                id = UUID.randomUUID().toString(),
                itemId = itemId,
                fromLocationId = existing.locationId,
                toLocationId = toLocationId,
                movedAt = now,
                note = note
            )
        )
    }

    override suspend fun delete(itemId: String) {
        val existing = itemDao.byId(itemId) ?: return
        itemDao.delete(itemId)
        val path = existing.photoPath
        if (path != null) {
            val stillUsed = photoDao.itemsUsingPhoto(path) > 0
            photoStorage.deleteIfUnused(path, stillUsed)
        }
    }

    override suspend fun possibleDuplicates(locationId: String, name: String) =
        itemDao.findPossibleDuplicates(locationId, normalizeForSearch(name)).map { it.toDomain() }

    override suspend fun reassignAll(fromLocationId: String, toLocationId: String) {
        itemDao.reassignAll(fromLocationId, toLocationId, System.currentTimeMillis())
    }
}
