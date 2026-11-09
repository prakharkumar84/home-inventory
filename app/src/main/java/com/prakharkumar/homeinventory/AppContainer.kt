package com.prakharkumar.homeinventory

import android.content.Context
import com.prakharkumar.homeinventory.ai.labelling.MlKitImageLabeller
import com.prakharkumar.homeinventory.ai.labelling.MlKitObjectDetector
import com.prakharkumar.homeinventory.ai.ocr.MlKitTextReader
import com.prakharkumar.homeinventory.ai.pipeline.RecognitionPipeline
import com.prakharkumar.homeinventory.data.database.InventoryDatabase
import com.prakharkumar.homeinventory.data.files.PhotoStorage
import com.prakharkumar.homeinventory.data.repository.ItemRepositoryImpl
import com.prakharkumar.homeinventory.data.repository.LocationRepositoryImpl
import com.prakharkumar.homeinventory.domain.repository.ItemRepository
import com.prakharkumar.homeinventory.domain.repository.LocationRepository

/**
 * Manual dependency injection (spec 4 allows this for a small first app).
 * Models are created lazily so nothing heavy loads at startup (spec 17).
 */
class AppContainer(context: Context) {

    private val db = InventoryDatabase.get(context)

    val photoStorage = PhotoStorage(context)

    val locationRepository: LocationRepository = LocationRepositoryImpl(db.locationDao())

    val itemRepository: ItemRepository = ItemRepositoryImpl(
        itemDao = db.itemDao(),
        movementDao = db.movementDao(),
        photoDao = db.photoDao(),
        locationRepository = locationRepository,
        photoStorage = photoStorage
    )

    val recognitionPipeline: RecognitionPipeline by lazy {
        RecognitionPipeline(
            labeller = MlKitImageLabeller(),
            detector = MlKitObjectDetector(),
            textReader = MlKitTextReader(),
            photoStorage = photoStorage,
            photoDao = db.photoDao(),
            suggestionDao = db.suggestionDao()
        )
    }
}
