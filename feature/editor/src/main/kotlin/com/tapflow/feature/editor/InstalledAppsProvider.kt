package com.tapflow.feature.editor

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Immutable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Immutable
data class InstalledAppEntry(
  val packageName: String,
  val label: String,
  val iconDrawable: Drawable?,
)

@Singleton
class InstalledAppsProvider @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  suspend fun loadLaunchableApps(): List<InstalledAppEntry> =
    withContext(Dispatchers.IO) {
      val pm = context.packageManager
      val launcherIntent =
        Intent(Intent.ACTION_MAIN).apply {
          addCategory(Intent.CATEGORY_LAUNCHER)
        }
      val resolved = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
      resolved
        .distinctBy { it.activityInfo.packageName }
        .map { info ->
          val pkg = info.activityInfo.packageName
          val label = info.loadLabel(pm)?.toString().orEmpty().ifBlank { pkg }
          val icon = runCatching { info.loadIcon(pm) }.getOrNull()
          InstalledAppEntry(
            packageName = pkg,
            label = label,
            iconDrawable = icon,
          )
        }
        .sortedBy { it.label.lowercase() }
    }
}
