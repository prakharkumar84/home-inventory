package com.prakharkumar.homeinventory.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.prakharkumar.homeinventory.data.database.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(location: StorageLocationEntity)

    @Update
    suspend fun update(location: StorageLocationEntity)

    @Query("SELECT * FROM storage_location WHERE archivedAt IS NULL ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<StorageLocationEntity>>

    @Query("SELECT * FROM storage_location WHERE id = :id")
    suspend fun byId(id: String): StorageLocationEntity?

    @Query("SELECT * FROM storage_location WHERE id = :id")
    fun observeById(id: String): Flow<StorageLocationEntity?>

    @Query("SELECT * FROM storage_location WHERE parentId IS :parentId AND archivedAt IS NULL ORDER BY sortOrder, name")
    fun observeChildren(parentId: String?): Flow<List<StorageLocationEntity>>

    @Query("SELECT COUNT(*) FROM storage_location WHERE parentId = :id AND archivedAt IS NULL")
    suspend fun childCount(id: String): Int

    @Query("SELECT COUNT(*) FROM inventory_item WHERE locationId = :id AND archivedAt IS NULL")
    suspend fun itemCount(id: String): Int

    @Query("SELECT locationId AS locationId, COUNT(*) AS count FROM inventory_item WHERE archivedAt IS NULL GROUP BY locationId")
    fun observeItemCounts(): Flow<List<LocationItemCount>>

    @Query("UPDATE storage_location SET archivedAt = :now WHERE id = :id")
    suspend fun archive(id: String, now: Long)

    @Query("DELETE FROM storage_location WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM storage_location WHERE archivedAt IS NULL")
    fun observeLocationTotal(): Flow<Int>
}

data class LocationItemCount(val locationId: String, val count: Int)

@Dao
interface ItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: InventoryItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<InventoryItemEntity>)

    @Query("SELECT * FROM inventory_item WHERE id = :id")
    fun observeById(id: String): Flow<InventoryItemEntity?>

    @Query("SELECT * FROM inventory_item WHERE id = :id")
    suspend fun byId(id: String): InventoryItemEntity?

    @Query("SELECT * FROM inventory_item WHERE locationId = :locationId AND archivedAt IS NULL ORDER BY updatedAt DESC")
    fun observeByLocation(locationId: String): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_item WHERE archivedAt IS NULL ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<InventoryItemEntity>>

    @Query("SELECT COUNT(*) FROM inventory_item WHERE archivedAt IS NULL")
    fun observeItemTotal(): Flow<Int>

    /**
     * Spec 10 - v1 search: exact first, then prefix, then substring, across the
     * user-visible text fields. Runs fully offline on SQLite.
     */
    @Query(
        """
        SELECT * FROM inventory_item
        WHERE archivedAt IS NULL AND (
            normalizedName LIKE '%' || :q || '%'
            OR LOWER(IFNULL(category, '')) LIKE '%' || :q || '%'
            OR LOWER(IFNULL(description, '')) LIKE '%' || :q || '%'
            OR LOWER(IFNULL(notes, '')) LIKE '%' || :q || '%'
            OR LOWER(IFNULL(barcode, '')) LIKE '%' || :q || '%'
            OR LOWER(IFNULL(serialNumber, '')) LIKE '%' || :q || '%'
        )
        ORDER BY
            CASE
                WHEN normalizedName = :q THEN 0
                WHEN normalizedName LIKE :q || '%' THEN 1
                WHEN normalizedName LIKE '%' || :q || '%' THEN 2
                ELSE 3
            END,
            name COLLATE NOCASE
        LIMIT 200
        """
    )
    suspend fun search(q: String): List<InventoryItemEntity>

    /** Spec 13 - weak duplicate clue: same normalised name in the same location. */
    @Query("SELECT * FROM inventory_item WHERE locationId = :locationId AND normalizedName = :normalizedName AND archivedAt IS NULL LIMIT 5")
    suspend fun findPossibleDuplicates(locationId: String, normalizedName: String): List<InventoryItemEntity>

    @Query("UPDATE inventory_item SET locationId = :toLocationId, updatedAt = :now WHERE id = :itemId")
    suspend fun updateLocation(itemId: String, toLocationId: String, now: Long)

    @Query("UPDATE inventory_item SET archivedAt = :now WHERE id = :id")
    suspend fun archive(id: String, now: Long)

    @Query("DELETE FROM inventory_item WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE inventory_item SET locationId = :toLocationId, updatedAt = :now WHERE locationId = :fromLocationId")
    suspend fun reassignAll(fromLocationId: String, toLocationId: String, now: Long)
}

@Dao
interface PhotoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(photo: InventoryPhotoEntity)

    @Query("SELECT * FROM inventory_photo WHERE id = :id")
    suspend fun byId(id: String): InventoryPhotoEntity?

    @Query("SELECT COUNT(*) FROM inventory_item WHERE photoPath = :path AND archivedAt IS NULL")
    suspend fun itemsUsingPhoto(path: String): Int

    @Query("DELETE FROM inventory_photo WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT IFNULL(SUM(fileSize), 0) FROM inventory_photo")
    suspend fun totalPhotoBytes(): Long
}

@Dao
interface SuggestionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(suggestions: List<RecognitionSuggestionEntity>)

    @Query("SELECT * FROM recognition_suggestion WHERE photoId = :photoId ORDER BY modelScore DESC")
    suspend fun byPhoto(photoId: String): List<RecognitionSuggestionEntity>

    @Query("UPDATE recognition_suggestion SET reviewState = :state WHERE id = :id")
    suspend fun setReviewState(id: String, state: String)

    @Query("DELETE FROM recognition_suggestion WHERE photoId = :photoId")
    suspend fun clearForPhoto(photoId: String)
}

@Dao
interface MovementDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(movement: ItemMovementEntity)

    @Transaction
    @Query("SELECT * FROM item_movement WHERE itemId = :itemId ORDER BY movedAt DESC")
    fun observeForItem(itemId: String): Flow<List<ItemMovementEntity>>
}
