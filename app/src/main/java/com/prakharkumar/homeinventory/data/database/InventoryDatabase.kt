package com.prakharkumar.homeinventory.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.prakharkumar.homeinventory.data.database.dao.*
import com.prakharkumar.homeinventory.data.database.entity.*

@Database(
    entities = [
        StorageLocationEntity::class,
        InventoryItemEntity::class,
        InventoryPhotoEntity::class,
        RecognitionSuggestionEntity::class,
        ItemMovementEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class InventoryDatabase : RoomDatabase() {

    abstract fun locationDao(): LocationDao
    abstract fun itemDao(): ItemDao
    abstract fun photoDao(): PhotoDao
    abstract fun suggestionDao(): SuggestionDao
    abstract fun movementDao(): MovementDao

    companion object {
        @Volatile private var instance: InventoryDatabase? = null

        fun get(context: Context): InventoryDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    InventoryDatabase::class.java,
                    "home_inventory.db"
                ).build().also { instance = it }
            }
    }
}
