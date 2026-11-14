package com.tapflow.core.database.backup

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tapflow.core.database.TapFlowJson
import com.tapflow.core.database.dao.PipelineDao
import com.tapflow.core.database.dao.ScriptDao
import com.tapflow.core.database.entity.toDomain
import com.tapflow.core.database.entity.toEntity
import com.tapflow.core.model.pipeline.Pipeline
import com.tapflow.core.model.script.Script
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class TapFlowBackupEnvelope(
  val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
  val exportedAtUtcMs: Long,
  val scripts: List<Script> = emptyList(),
  val pipelines: List<Pipeline> = emptyList(),
) {
  companion object {
    const val CURRENT_SCHEMA_VERSION: Int = 1
  }
}

enum class ImportConflictStrategy {
  IMPORT_AS_COPY,
  OVERWRITE,
}

data class BackupImportPreview(
  val fileName: String?,
  val scriptsCount: Int,
  val pipelinesCount: Int,
  val duplicateScriptTitles: List<String>,
  val duplicatePipelineTitles: List<String>,
  val envelope: TapFlowBackupEnvelope,
)

data class BackupExportSummary(
  val scriptsExported: Int,
  val pipelinesExported: Int,
)

data class BackupImportResult(
  val scriptsImported: Int,
  val pipelinesImported: Int,
)

@Singleton
class TapFlowBackupManager @Inject constructor(
  @ApplicationContext private val context: Context,
  private val scriptDao: ScriptDao,
  private val pipelineDao: PipelineDao,
) {
  suspend fun createBackupEnvelopeJson(): Pair<String, BackupExportSummary> =
    withContext(Dispatchers.IO) {
      val scripts = scriptDao.getAllOnce().map { it.toDomain() }
      val pipelines = pipelineDao.getAllOnce().map { it.toDomain() }
      val envelope =
        TapFlowBackupEnvelope(
          schemaVersion = TapFlowBackupEnvelope.CURRENT_SCHEMA_VERSION,
          exportedAtUtcMs = System.currentTimeMillis(),
          scripts = scripts,
          pipelines = pipelines,
        )
      val jsonText = TapFlowJson.instance.encodeToString(TapFlowBackupEnvelope.serializer(), envelope)
      jsonText to BackupExportSummary(scriptsExported = scripts.size, pipelinesExported = pipelines.size)
    }

  suspend fun exportToUri(uri: Uri): Result<BackupExportSummary> =
    withContext(Dispatchers.IO) {
      runCatching {
        val (jsonText, summary) = createBackupEnvelopeJson()
        context.contentResolver.openOutputStream(uri)?.use { stream ->
          stream.write(jsonText.toByteArray(Charsets.UTF_8))
          stream.flush()
        } ?: throw IOException("Unable to open output stream for backup URI")
        summary
      }
    }

  suspend fun inspectBackupUri(uri: Uri): Result<BackupImportPreview> =
    withContext(Dispatchers.IO) {
      runCatching {
        val displayName = resolveDisplayName(uri)
        if (displayName != null &&
          !displayName.endsWith(".tapflow", ignoreCase = true) &&
          !displayName.endsWith(".json", ignoreCase = true)
        ) {
          throw IllegalArgumentException("Unsupported file '$displayName'. Please select a .tapflow backup file.")
        }

        val rawJson =
          context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader(Charsets.UTF_8).readText()
          } ?: throw IOException("Unable to read backup file.")

        val envelope =
          TapFlowJson.instance.decodeFromString(TapFlowBackupEnvelope.serializer(), rawJson)

        val existingScriptTitles =
          scriptDao.getAllOnce().map { it.title.trim().lowercase() }.toHashSet()
        val existingPipelineTitles =
          pipelineDao.getAllOnce().map { it.title.trim().lowercase() }.toHashSet()

        val duplicateScripts =
          envelope.scripts
            .map { it.title }
            .filter { it.trim().lowercase() in existingScriptTitles }
            .distinct()

        val duplicatePipelines =
          envelope.pipelines
            .map { it.title }
            .filter { it.trim().lowercase() in existingPipelineTitles }
            .distinct()

        BackupImportPreview(
          fileName = displayName,
          scriptsCount = envelope.scripts.size,
          pipelinesCount = envelope.pipelines.size,
          duplicateScriptTitles = duplicateScripts,
          duplicatePipelineTitles = duplicatePipelines,
          envelope = envelope,
        )
      }
    }

  suspend fun commitImport(
    preview: BackupImportPreview,
    strategy: ImportConflictStrategy,
  ): BackupImportResult =
    withContext(Dispatchers.IO) {
      val now = System.currentTimeMillis()
      val existingScripts = scriptDao.getAllOnce().map { it.toDomain() }
      val existingPipelines = pipelineDao.getAllOnce().map { it.toDomain() }

      val existingScriptByTitle =
        existingScripts.associateBy { it.title.trim().lowercase() }
      val existingPipelineByTitle =
        existingPipelines.associateBy { it.title.trim().lowercase() }
      val existingScriptIds = existingScripts.mapTo(HashSet()) { it.id }
      val existingPipelineIds = existingPipelines.mapTo(HashSet()) { it.id }

      val scriptIdRemap = mutableMapOf<String, String>()
      val scriptsToUpsert = mutableListOf<Script>()

      for (incoming in preview.envelope.scripts) {
        val titleKey = incoming.title.trim().lowercase()
        val conflicting = existingScriptByTitle[titleKey]

        val resolved =
          when {
            conflicting != null && strategy == ImportConflictStrategy.OVERWRITE -> {
              scriptIdRemap[incoming.id] = conflicting.id
              incoming.copy(
                id = conflicting.id,
                createdAtMs = conflicting.createdAtMs,
                updatedAtMs = now,
              )
            }
            conflicting != null && strategy == ImportConflictStrategy.IMPORT_AS_COPY -> {
              val newId = UUID.randomUUID().toString()
              scriptIdRemap[incoming.id] = newId
              incoming.copy(
                id = newId,
                title = "${incoming.title} (Copy)",
                createdAtMs = now,
                updatedAtMs = now,
              )
            }
            incoming.id in existingScriptIds -> {
              val newId = UUID.randomUUID().toString()
              scriptIdRemap[incoming.id] = newId
              incoming.copy(id = newId, createdAtMs = now, updatedAtMs = now)
            }
            else -> {
              scriptIdRemap[incoming.id] = incoming.id
              incoming.copy(
                createdAtMs = if (incoming.createdAtMs <= 0L) now else incoming.createdAtMs,
                updatedAtMs = now,
              )
            }
          }
        scriptsToUpsert += resolved
      }

      val pipelinesToUpsert = mutableListOf<Pipeline>()
      for (incoming in preview.envelope.pipelines) {
        val remappedSteps =
          incoming.steps.map { step ->
            step.copy(scriptId = scriptIdRemap[step.scriptId] ?: step.scriptId)
          }
        val titleKey = incoming.title.trim().lowercase()
        val conflicting = existingPipelineByTitle[titleKey]

        val resolved =
          when {
            conflicting != null && strategy == ImportConflictStrategy.OVERWRITE -> {
              incoming.copy(
                id = conflicting.id,
                steps = remappedSteps,
                createdAtMs = conflicting.createdAtMs,
                updatedAtMs = now,
              )
            }
            conflicting != null && strategy == ImportConflictStrategy.IMPORT_AS_COPY -> {
              incoming.copy(
                id = UUID.randomUUID().toString(),
                title = "${incoming.title} (Copy)",
                steps = remappedSteps,
                createdAtMs = now,
                updatedAtMs = now,
              )
            }
            incoming.id in existingPipelineIds -> {
              incoming.copy(
                id = UUID.randomUUID().toString(),
                steps = remappedSteps,
                createdAtMs = now,
                updatedAtMs = now,
              )
            }
            else -> {
              incoming.copy(
                steps = remappedSteps,
                createdAtMs = if (incoming.createdAtMs <= 0L) now else incoming.createdAtMs,
                updatedAtMs = now,
              )
            }
          }
        pipelinesToUpsert += resolved
      }

      if (scriptsToUpsert.isNotEmpty()) {
        scriptDao.upsertAll(scriptsToUpsert.map { it.toEntity() })
      }
      if (pipelinesToUpsert.isNotEmpty()) {
        pipelineDao.upsertAll(pipelinesToUpsert.map { it.toEntity() })
      }

      BackupImportResult(
        scriptsImported = scriptsToUpsert.size,
        pipelinesImported = pipelinesToUpsert.size,
      )
    }

  private fun resolveDisplayName(uri: Uri): String? {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
      if (it.moveToFirst()) {
        val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0) return it.getString(idx)
      }
    }
    return uri.lastPathSegment
  }
}
