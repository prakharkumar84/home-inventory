package com.prakharkumar.homeinventory.data.repository

import com.prakharkumar.homeinventory.data.database.dao.LocationDao
import com.prakharkumar.homeinventory.data.database.entity.StorageLocationEntity
import com.prakharkumar.homeinventory.domain.model.LocationNode
import com.prakharkumar.homeinventory.domain.model.LocationType
import com.prakharkumar.homeinventory.domain.model.StorageLocation
import com.prakharkumar.homeinventory.domain.repository.DeleteCheck
import com.prakharkumar.homeinventory.domain.repository.LocationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

class LocationRepositoryImpl(private val dao: LocationDao) : LocationRepository {

    private fun StorageLocationEntity.toDomain(count: Int = 0) = StorageLocation(
        id = id,
        parentId = parentId,
        name = name,
        description = description,
        photoPath = photoPath,
        type = runCatching { LocationType.valueOf(locationType) }.getOrDefault(LocationType.OTHER),
        sortOrder = sortOrder,
        itemCount = count
    )

    override fun observeTree(): Flow<List<LocationNode>> =
        combine(dao.observeAll(), dao.observeItemCounts()) { locations, counts ->
            val countMap = counts.associate { it.locationId to it.count }
            val byParent = locations.groupBy { it.parentId }

            fun build(parentId: String?, depth: Int): List<LocationNode> =
                byParent[parentId].orEmpty().map { entity ->
                    val children = build(entity.id, depth + 1)
                    LocationNode(
                        location = entity.toDomain(countMap[entity.id] ?: 0),
                        depth = depth,
                        children = children
                    )
                }
            build(null, 0)
        }

    override fun observeChildren(parentId: String?): Flow<List<StorageLocation>> =
        combine(dao.observeChildren(parentId), dao.observeItemCounts()) { children, counts ->
            val countMap = counts.associate { it.locationId to it.count }
            children.map { it.toDomain(countMap[it.id] ?: 0) }
        }

    override fun observeLocation(id: String): Flow<StorageLocation?> =
        dao.observeById(id).map { it?.toDomain() }

    override fun observeLocationCount(): Flow<Int> = dao.observeLocationTotal()

    override suspend fun create(
        name: String,
        parentId: String?,
        type: LocationType,
        description: String?
    ): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        dao.upsert(
            StorageLocationEntity(
                id = id,
                parentId = parentId,
                name = name.trim(),
                description = description?.trim()?.takeIf { it.isNotEmpty() },
                locationType = type.name,
                createdAt = now,
                updatedAt = now
            )
        )
        return id
    }

    override suspend fun rename(id: String, name: String, description: String?) {
        val existing = dao.byId(id) ?: return
        dao.update(
            existing.copy(
                name = name.trim(),
                description = description?.trim()?.takeIf { it.isNotEmpty() },
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /** Spec 9.1 - a location must never become its own descendant. */
    override suspend fun move(id: String, newParentId: String?) {
        if (id == newParentId) return
        var cursor = newParentId
        while (cursor != null) {
            if (cursor == id) return
            cursor = dao.byId(cursor)?.parentId
        }
        val existing = dao.byId(id) ?: return
        dao.update(existing.copy(parentId = newParentId, updatedAt = System.currentTimeMillis()))
    }

    override suspend fun breadcrumb(locationId: String): String {
        val parts = mutableListOf<String>()
        var cursor: String? = locationId
        var guard = 0
        while (cursor != null && guard++ < 32) {
            val node = dao.byId(cursor) ?: break
            parts.add(0, node.name)
            cursor = node.parentId
        }
        return parts.joinToString(" › ")
    }

    override suspend fun canDelete(id: String) = DeleteCheck(dao.childCount(id), dao.itemCount(id))

    override suspend fun deleteMovingItemsTo(id: String, targetLocationId: String?) {
        // Caller is responsible for reassigning items first (spec 2.1: never silently orphan).
        dao.delete(id)
    }

    override suspend fun ensureUnassignedLocation(): String {
        val existing = dao.observeAll().first().firstOrNull { it.name == UNASSIGNED }
        return existing?.id ?: create(UNASSIGNED, null, LocationType.OTHER, "Items whose location was removed")
    }

    override suspend fun flatList(): List<Pair<StorageLocation, Int>> {
        val all = dao.observeAll().first()
        val byParent = all.groupBy { it.parentId }
        val out = mutableListOf<Pair<StorageLocation, Int>>()
        fun walk(parentId: String?, depth: Int) {
            byParent[parentId].orEmpty().forEach {
                out += it.toDomain() to depth
                walk(it.id, depth + 1)
            }
        }
        walk(null, 0)
        return out
    }

    companion object { const val UNASSIGNED = "Unassigned" }
}
