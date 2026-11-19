package com.tapflow.core.database.repository

import androidx.compose.runtime.Immutable
import com.tapflow.core.database.asset.ScriptAssetManager
import com.tapflow.core.database.dao.InspectDao
import com.tapflow.core.database.entity.toDomain
import com.tapflow.core.database.entity.toEntity
import com.tapflow.core.model.inspect.InspectGroup
import com.tapflow.core.model.inspect.InspectItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Immutable
data class InspectGroupWithItems(
  val group: InspectGroup,
  val items: List<InspectItem>,
)

@Immutable
data class InspectStorageStats(
  val groupCount: Int = 0,
  val itemCount: Int = 0,
  val imageCount: Int = 0,
  val totalBytes: Long = 0L,
)

@Singleton
class InspectRepository @Inject constructor(
  private val inspectDao: InspectDao,
  private val scriptAssetManager: ScriptAssetManager,
) {
  fun observeGroupsWithItems(): Flow<List<InspectGroupWithItems>> {
    return combine(
      inspectDao.observeAllGroups(),
      inspectDao.observeAllItems(),
    ) { groupEntities, itemEntities ->
      val itemsByGroup = itemEntities.map { it.toDomain() }.groupBy { it.groupId }
      groupEntities.map { groupEntity ->
        val group = groupEntity.toDomain()
        InspectGroupWithItems(
          group = group,
          items = itemsByGroup[group.id].orEmpty(),
        )
      }
    }.flowOn(Dispatchers.IO)
  }

  fun observeItem(itemId: String): Flow<InspectItem?> =
    inspectDao.observeItemById(itemId).map { it?.toDomain() }.flowOn(Dispatchers.IO)

  suspend fun getItem(itemId: String): InspectItem? =
    withContext(Dispatchers.IO) { inspectDao.getItemById(itemId)?.toDomain() }

  suspend fun getGroup(groupId: String): InspectGroup? =
    withContext(Dispatchers.IO) { inspectDao.getGroupById(groupId)?.toDomain() }

  suspend fun getAllGroupsWithItems(): List<InspectGroupWithItems> =
    withContext(Dispatchers.IO) {
      val groups = inspectDao.getAllGroupsOnce()
      val itemsByGroup = inspectDao.getAllItemsOnce().map { it.toDomain() }.groupBy { it.groupId }
      groups.map { groupEntity ->
        val group = groupEntity.toDomain()
        InspectGroupWithItems(
          group = group,
          items = itemsByGroup[group.id].orEmpty(),
        )
      }
    }

  suspend fun countGroups(): Int =
    withContext(Dispatchers.IO) { inspectDao.countGroups() }

  suspend fun getInspectStorageStats(): InspectStorageStats =
    withContext(Dispatchers.IO) {
      val groupCount = inspectDao.countGroups()
      val itemCount = inspectDao.countItems()
      val diskUsage = scriptAssetManager.getInspectDiskUsage()
      InspectStorageStats(
        groupCount = groupCount,
        itemCount = itemCount,
        imageCount = diskUsage.imageCount,
        totalBytes = diskUsage.totalBytes,
      )
    }

  suspend fun clearAllInspectImagesOnly(): Result<Int> =
    withContext(Dispatchers.IO) {
      runCatching {
        scriptAssetManager.clearInspectDirectories()
      }
    }

  suspend fun saveGroup(group: InspectGroup) =
    withContext(Dispatchers.IO) {
      val normalized =
        if (group.createdAtMs <= 0L) group.copy(createdAtMs = System.currentTimeMillis()) else group
      inspectDao.upsertGroup(normalized.toEntity())
    }

  suspend fun saveItem(item: InspectItem) =
    withContext(Dispatchers.IO) {
      val normalized =
        if (item.createdAtMs <= 0L) item.copy(createdAtMs = System.currentTimeMillis()) else item
      inspectDao.upsertItem(normalized.toEntity())
    }

  suspend fun deletePixelFromItem(itemId: String, pixelId: String) =
    withContext(Dispatchers.IO) {
      val current = inspectDao.getItemById(itemId)?.toDomain() ?: return@withContext
      val updated = current.copy(pixels = current.pixels.filterNot { it.id == pixelId })
      inspectDao.upsertItem(updated.toEntity())
    }

  suspend fun deleteCropFromItem(itemId: String, cropId: String) =
    withContext(Dispatchers.IO) {
      val current = inspectDao.getItemById(itemId)?.toDomain() ?: return@withContext
      val targetCrop = current.crops.firstOrNull { it.id == cropId }
      val updated = current.copy(crops = current.crops.filterNot { it.id == cropId })
      inspectDao.upsertItem(updated.toEntity())
      scriptAssetManager.deleteFileIfExists(targetCrop?.cropPngPath)
    }

  suspend fun deleteItem(itemId: String) =
    withContext(Dispatchers.IO) {
      val item = inspectDao.getItemById(itemId)?.toDomain() ?: return@withContext
      inspectDao.deleteItemById(itemId)
      val pathsToDelete =
        buildList {
          add(item.fullScreenPngPath)
          item.crops.forEach { add(it.cropPngPath) }
        }
      scriptAssetManager.deleteFilesIfExist(pathsToDelete)
    }

  suspend fun deleteGroup(groupId: String) =
    withContext(Dispatchers.IO) {
      val childItems = inspectDao.getItemsByGroupOnce(groupId).map { it.toDomain() }
      inspectDao.deleteGroupById(groupId)
      val pathsToDelete =
        buildList {
          childItems.forEach { item ->
            add(item.fullScreenPngPath)
            item.crops.forEach { add(it.cropPngPath) }
          }
        }
      scriptAssetManager.deleteFilesIfExist(pathsToDelete)
    }
}
