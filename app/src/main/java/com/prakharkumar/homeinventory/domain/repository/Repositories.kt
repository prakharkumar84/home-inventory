package com.prakharkumar.homeinventory.domain.repository

import com.prakharkumar.homeinventory.domain.model.*
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    fun observeTree(): Flow<List<LocationNode>>
    fun observeChildren(parentId: String?): Flow<List<StorageLocation>>
    fun observeLocation(id: String): Flow<StorageLocation?>
    fun observeLocationCount(): Flow<Int>
    suspend fun create(name: String, parentId: String?, type: LocationType, description: String?): String
    suspend fun rename(id: String, name: String, description: String?)
    suspend fun move(id: String, newParentId: String?)
    suspend fun breadcrumb(locationId: String): String
    suspend fun canDelete(id: String): DeleteCheck
    suspend fun deleteMovingItemsTo(id: String, targetLocationId: String?)
    suspend fun ensureUnassignedLocation(): String
    suspend fun flatList(): List<Pair<StorageLocation, Int>>
}

data class DeleteCheck(val childLocations: Int, val items: Int) {
    val isSafe: Boolean get() = childLocations == 0 && items == 0
}

interface ItemRepository {
    fun observeByLocation(locationId: String): Flow<List<InventoryItem>>
    fun observeRecent(limit: Int = 10): Flow<List<InventoryItem>>
    fun observeItem(id: String): Flow<InventoryItem?>
    fun observeItemCount(): Flow<Int>
    fun observeMovements(itemId: String): Flow<List<ItemMovement>>
    suspend fun search(query: String): List<SearchResult>
    suspend fun saveConfirmed(items: List<ItemSuggestion>, locationId: String, photoPath: String?, photoId: String?): List<String>
    suspend fun upsertManual(item: InventoryItem): String
    suspend fun update(item: InventoryItem)
    suspend fun move(itemId: String, toLocationId: String, note: String?)
    suspend fun delete(itemId: String)
    suspend fun possibleDuplicates(locationId: String, name: String): List<InventoryItem>
    suspend fun reassignAll(fromLocationId: String, toLocationId: String)
}
