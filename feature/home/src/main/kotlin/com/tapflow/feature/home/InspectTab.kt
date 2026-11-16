package com.tapflow.feature.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect as AndroidRect
import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ImageNotSupported
import androidx.compose.material.icons.rounded.StayCurrentLandscape
import androidx.compose.material.icons.rounded.StayCurrentPortrait
import androidx.compose.material.icons.rounded.TabletAndroid
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import com.tapflow.core.database.R as DbR
import com.tapflow.core.database.repository.InspectGroupWithItems
import com.tapflow.core.model.inspect.InspectCrop
import com.tapflow.core.model.inspect.InspectGroup
import com.tapflow.core.model.inspect.InspectItem
import com.tapflow.core.model.inspect.InspectPixel
import com.tapflow.core.model.inspect.ScreenOrientation
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun InspectTabContent(
  state: HomeUiState,
  onIntent: (HomeUiIntent) -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    if (state.inspectGroups.isEmpty()) {
      EmptyTabStateCard(
        icon = Icons.Filled.CenterFocusStrong,
        title = if (state.searchQuery.isBlank()) "No Inspect Captures" else "No Matching Captures",
        subtitle =
          if (state.searchQuery.isBlank()) {
            "Use Live Screen Inspect or Import Screenshot to collect lossless PNG templates and color dots."
          } else {
            "Try searching by group title, tag, or foreground app package."
          },
        modifier = Modifier.padding(24.dp).align(Alignment.Center),
      )
    } else {
      LazyColumn(
        modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        items(
          items = state.inspectGroups,
          key = { it.group.id },
        ) { entry ->
          InspectGroupCard(
            entry = entry,
            isGroupExpanded = entry.group.id in state.expandedInspectGroupIds,
            expandedItemIds = state.expandedInspectItemIds,
            imageRefreshToken = state.inspectImageRefreshToken,
            onIntent = onIntent,
          )
        }
      }
    }

    InspectSpeedDialFab(
      expanded = state.isInspectFabExpanded,
      onToggle = { onIntent(HomeUiIntent.ToggleInspectFab(!state.isInspectFabExpanded)) },
      onLiveInspect = { onIntent(HomeUiIntent.LiveInspectClicked) },
      onImportScreenshot = { onIntent(HomeUiIntent.ImportScreenshotClicked) },
      modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
    )
  }
}

@Composable
private fun InspectGroupCard(
  entry: InspectGroupWithItems,
  isGroupExpanded: Boolean,
  expandedItemIds: Set<String>,
  imageRefreshToken: Long,
  onIntent: (HomeUiIntent) -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth().testTag("inspect_group_${entry.group.id}"),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .clickable { onIntent(HomeUiIntent.ToggleInspectGroupExpanded(entry.group.id)) },
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = entry.group.title.ifBlank { "Untitled Group" },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "${entry.items.size} Items",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
          )
        }

        IconButton(
          onClick = { onIntent(HomeUiIntent.RequestDeleteInspectGroup(entry.group.id)) },
          modifier = Modifier.size(48.dp).testTag("delete_inspect_group_${entry.group.id}"),
        ) {
          Icon(
            imageVector = Icons.Filled.DeleteOutline,
            contentDescription = "Delete inspect group",
            tint = MaterialTheme.colorScheme.error,
          )
        }
        IconButton(
          onClick = { onIntent(HomeUiIntent.ToggleInspectGroupExpanded(entry.group.id)) },
          modifier = Modifier.size(48.dp).testTag("toggle_inspect_group_${entry.group.id}"),
        ) {
          Icon(
            imageVector = if (isGroupExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (isGroupExpanded) "Collapse group" else "Expand group",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      if (entry.group.tags.isNotEmpty()) {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.padding(top = 4.dp),
        ) {
          entry.group.tags.forEach { tag ->
            AssistChip(
              onClick = {},
              label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
            )
          }
        }
      }

      AnimatedVisibility(visible = isGroupExpanded) {
        Column(
          modifier = Modifier.padding(top = 10.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          entry.items.forEach { item ->
            InspectItemNestedCard(
              item = item,
              isExpanded = item.id in expandedItemIds,
              imageRefreshToken = imageRefreshToken,
              onIntent = onIntent,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun InspectItemNestedCard(
  item: InspectItem,
  isExpanded: Boolean,
  imageRefreshToken: Long,
  onIntent: (HomeUiIntent) -> Unit,
) {
  val isPreview = LocalInspectionMode.current
  val imageExists by
    produceState<Boolean?>(
      initialValue = if (isPreview) item.fullScreenPngPath.isNotBlank() else null,
      item.fullScreenPngPath,
      imageRefreshToken,
      isPreview,
    ) {
      if (isPreview) {
        value = item.fullScreenPngPath.isNotBlank()
        return@produceState
      }
      value =
        withContext(Dispatchers.IO) {
          val path = item.fullScreenPngPath
          path.isNotBlank() && File(path).let { it.exists() && it.isFile }
        }
    }

  Card(
    modifier = Modifier.fillMaxWidth().testTag("inspect_item_${item.id}"),
    colors =
      CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
      ),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      val displayAppName = item.appName.ifBlank { "Screen Capture" }
      val pixelWord = if (item.pixels.size == 1) "Pixel" else "Pixels"
      val cropWord = if (item.crops.size == 1) "Crop" else "Crops"

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surfaceContainerHighest,
          border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
          modifier = Modifier.size(92.dp),
        ) {
          Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
          ) {
            when (imageExists) {
              true -> {
                AsyncImage(
                  model = File(item.fullScreenPngPath),
                  contentDescription = "Screenshot for $displayAppName",
                  contentScale = ContentScale.Fit,
                  modifier = Modifier.fillMaxSize(),
                )
              }
              false -> {
                Column(
                  modifier = Modifier.padding(6.dp).testTag("inspect_image_deleted_${item.id}"),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.ImageNotSupported,
                    contentDescription = "Image Deleted",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                  )
                  Text(
                    text = "Image Deleted",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
              null -> Unit
            }
          }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            TargetAppIcon(
              packageName = item.packageName,
              contentDescription = null,
            )
            Text(
              text = displayAppName,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
            )
            IconButton(
              onClick = { onIntent(HomeUiIntent.ToggleInspectItemExpanded(item.id)) },
              modifier = Modifier.size(48.dp).testTag("toggle_inspect_item_${item.id}"),
            ) {
              Icon(
                imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (isExpanded) "Hide targets" else "Show targets",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          InspectOrientationBadge(
            item = item,
            modifier = Modifier.testTag("orientation_badge_${item.id}"),
          )

          Text(
            text = "${item.pixels.size} $pixelWord • ${item.crops.size} $cropWord",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      AnimatedVisibility(visible = isExpanded) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          HorizontalDivider()

          Text(
            text = "Inspected Color Pixels (${item.pixels.size})",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
          )
          if (item.pixels.isEmpty()) {
            Text(
              text = "No color pixels saved yet.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            item.pixels.forEach { pixel ->
              PixelEntryRow(
                pixel = pixel,
                onDelete = { onIntent(HomeUiIntent.DeleteInspectPixel(item.id, pixel.id)) },
              )
            }
          }

          HorizontalDivider()

          Text(
            text = "Inspected Image Crops (${item.crops.size})",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
          )
          if (item.crops.isEmpty()) {
            Text(
              text = "No image crops saved yet.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            item.crops.forEach { crop ->
              CropEntryRow(
                fullScreenPngPath = item.fullScreenPngPath,
                crop = crop,
                imageRefreshToken = imageRefreshToken,
                onDelete = { onIntent(HomeUiIntent.DeleteInspectCrop(item.id, crop.id)) },
              )
            }
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val isMissingImage = imageExists == false
        val canOpenStudio = imageExists == true
        FilledTonalButton(
          onClick = { onIntent(HomeUiIntent.OpenInspectStudio(item.id)) },
          enabled = canOpenStudio,
          colors =
            ButtonDefaults.filledTonalButtonColors(
              containerColor = MaterialTheme.colorScheme.secondaryContainer,
              contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
              disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
              disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
          modifier = Modifier.weight(1f).testTag("open_studio_${item.id}"),
        ) {
          Icon(
            imageVector =
              if (isMissingImage) Icons.Rounded.ImageNotSupported else Icons.Filled.CenterFocusStrong,
            contentDescription = null,
            tint =
              if (canOpenStudio) {
                MaterialTheme.colorScheme.onSecondaryContainer
              } else {
                MaterialTheme.colorScheme.onSurfaceVariant
              },
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = if (isMissingImage) "Studio Unavailable (Image Deleted)" else "Open Studio",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        IconButton(
          onClick = { onIntent(HomeUiIntent.DeleteInspectItem(item.id)) },
          modifier = Modifier.size(48.dp).testTag("delete_inspect_item_${item.id}"),
        ) {
          Icon(
            imageVector = Icons.Filled.DeleteOutline,
            contentDescription = "Delete inspect screenshot",
            tint = MaterialTheme.colorScheme.error,
          )
        }
      }
    }
  }
}

@Composable
private fun PixelEntryRow(
  pixel: InspectPixel,
  onDelete: () -> Unit,
) {
  val swatchColor = parseHexColorOrDefault(pixel.hexColor)
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Box(
      modifier =
        Modifier
          .size(20.dp)
          .clip(CircleShape)
          .background(swatchColor)
          .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = pixel.label.ifBlank { "Pixel" },
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium,
      )
      Text(
        text = "${pixel.hexColor} • (${pixel.x}, ${pixel.y})",
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    IconButton(
      onClick = onDelete,
      modifier = Modifier.size(48.dp).testTag("delete_pixel_${pixel.id}"),
    ) {
      Icon(
        imageVector = Icons.Filled.DeleteOutline,
        contentDescription = "Delete pixel",
        tint = MaterialTheme.colorScheme.error,
      )
    }
  }
}

private data class CropThumbState(
  val bitmap: ImageBitmap? = null,
  val isImageDeleted: Boolean = false,
)

@Composable
private fun CropEntryRow(
  fullScreenPngPath: String,
  crop: InspectCrop,
  imageRefreshToken: Long,
  onDelete: () -> Unit,
) {
  val isPreview = LocalInspectionMode.current
  val thumbState by
    produceState(
      initialValue = CropThumbState(isImageDeleted = isPreview && fullScreenPngPath.isBlank()),
      fullScreenPngPath,
      crop.x,
      crop.y,
      crop.width,
      crop.height,
      crop.cropPngPath,
      imageRefreshToken,
      isPreview,
    ) {
      if (isPreview) return@produceState
      value =
        withContext(Dispatchers.IO) {
          runCatching {
            val legacyPath = crop.cropPngPath?.takeIf { it.isNotBlank() }
            if (legacyPath != null) {
              val legacyFile = File(legacyPath)
              if (legacyFile.exists() && legacyFile.isFile) {
                val bmp = BitmapFactory.decodeFile(legacyFile.absolutePath)?.asImageBitmap()
                return@runCatching CropThumbState(bitmap = bmp, isImageDeleted = bmp == null)
              }
            }
            if (fullScreenPngPath.isBlank()) {
              return@runCatching CropThumbState(isImageDeleted = true)
            }
            val screenFile = File(fullScreenPngPath)
            if (!screenFile.exists() || !screenFile.isFile) {
              return@runCatching CropThumbState(isImageDeleted = true)
            }
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(screenFile.absolutePath, boundsOpts)
            val imgW = boundsOpts.outWidth
            val imgH = boundsOpts.outHeight
            if (imgW <= 0 || imgH <= 0) {
              return@runCatching CropThumbState(isImageDeleted = true)
            }
            val safeX = crop.x.coerceIn(0, imgW - 1)
            val safeY = crop.y.coerceIn(0, imgH - 1)
            val safeW = crop.width.coerceIn(1, imgW - safeX)
            val safeH = crop.height.coerceIn(1, imgH - safeY)
            @Suppress("DEPRECATION")
            val decoder = BitmapRegionDecoder.newInstance(screenFile.absolutePath, false)
            try {
              val bmp =
                decoder.decodeRegion(
                  AndroidRect(safeX, safeY, safeX + safeW, safeY + safeH),
                  BitmapFactory.Options(),
                )?.asImageBitmap()
              CropThumbState(bitmap = bmp, isImageDeleted = bmp == null)
            } finally {
              decoder.recycle()
            }
          }.getOrElse { CropThumbState(isImageDeleted = true) }
        }
    }

  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Surface(
      shape = MaterialTheme.shapes.extraSmall,
      color = MaterialTheme.colorScheme.surface,
      modifier = Modifier.size(40.dp),
    ) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val bmp = thumbState.bitmap
        when {
          bmp != null -> {
            Image(
              bitmap = bmp,
              contentDescription = crop.label,
              contentScale = ContentScale.Fit,
              modifier = Modifier.fillMaxSize(),
            )
          }
          thumbState.isImageDeleted -> {
            Icon(
              imageVector = Icons.Rounded.ImageNotSupported,
              contentDescription = "Image Deleted",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp),
            )
          }
          else -> {
            Icon(
              imageVector = Icons.Filled.Crop,
              contentDescription = crop.label,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = crop.label.ifBlank { "Template Crop" },
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium,
      )
      Text(
        text = "${crop.width} × ${crop.height} px • (${crop.x}, ${crop.y})",
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (thumbState.isImageDeleted) {
        Text(
          text = "Image Deleted",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.error,
        )
      }
    }
    IconButton(
      onClick = onDelete,
      modifier = Modifier.size(48.dp).testTag("delete_crop_${crop.id}"),
    ) {
      Icon(
        imageVector = Icons.Filled.DeleteOutline,
        contentDescription = "Delete crop",
        tint = MaterialTheme.colorScheme.error,
      )
    }
  }
}

@Composable
private fun InspectSpeedDialFab(
  expanded: Boolean,
  onToggle: () -> Unit,
  onLiveInspect: () -> Unit,
  onImportScreenshot: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.End,
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    if (expanded) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Surface(
          shape = MaterialTheme.shapes.small,
          tonalElevation = 4.dp,
          shadowElevation = 2.dp,
        ) {
          Text(
            text = "Import Screenshot",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          )
        }
        SmallFloatingActionButton(
          onClick = onImportScreenshot,
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
          elevation = FloatingActionButtonDefaults.elevation(),
          modifier = Modifier.size(48.dp).testTag("fab_import_screenshot"),
        ) {
          Icon(
            imageVector = Icons.Filled.PhotoLibrary,
            contentDescription = "Import screenshot",
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
          )
        }
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Surface(
          shape = MaterialTheme.shapes.small,
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
          contentColor = MaterialTheme.colorScheme.onSurface,
          tonalElevation = 4.dp,
          shadowElevation = 2.dp,
        ) {
          Text(
            text = "Live Screen Inspect (Pro)",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          )
        }
        SmallFloatingActionButton(
          onClick = onLiveInspect,
          containerColor = MaterialTheme.colorScheme.secondaryContainer,
          contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
          modifier = Modifier.size(48.dp).testTag("fab_live_inspect"),
        ) {
          Icon(
            imageVector = Icons.Filled.Image,
            contentDescription = "Live screen inspect",
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
          )
        }
      }
    }

    ExtendedFloatingActionButton(
      onClick = onToggle,
      containerColor = MaterialTheme.colorScheme.primaryContainer,
      contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
      icon = {
        Icon(
          imageVector = if (expanded) Icons.Filled.Close else Icons.Filled.Add,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
      },
      text = { Text(if (expanded) "Close" else "New Inspect") },
      modifier = Modifier.testTag("fab_new_inspect"),
    )
  }
}

private object InspectAppIconCache {
  private val cache = LruCache<String, ImageBitmap>(48)

  suspend fun load(context: Context, packageName: String): ImageBitmap? {
    if (packageName.isBlank()) return null
    cache.get(packageName)?.let { return it }
    return withContext(Dispatchers.IO) {
      runCatching {
        val drawable = context.packageManager.getApplicationIcon(packageName)
        drawable.toBitmap(width = 64, height = 64, config = Bitmap.Config.ARGB_8888)
          .asImageBitmap()
          .also { cache.put(packageName, it) }
      }.getOrNull()
    }
  }
}

@Composable
private fun TargetAppIcon(
  packageName: String,
  contentDescription: String?,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val isPreview = LocalInspectionMode.current
  val iconBitmap by
    produceState<ImageBitmap?>(initialValue = null, packageName, isPreview) {
      value =
        if (isPreview || packageName.isBlank()) {
          null
        } else {
          InspectAppIconCache.load(context, packageName)
        }
    }

  val loaded = iconBitmap
  if (loaded != null) {
    Image(
      bitmap = loaded,
      contentDescription = contentDescription,
      contentScale = ContentScale.Crop,
      modifier = modifier.size(24.dp).clip(RoundedCornerShape(6.dp)),
    )
  } else {
    Surface(
      modifier = modifier.size(24.dp),
      shape = RoundedCornerShape(6.dp),
      color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = Icons.Rounded.Apps,
          contentDescription = contentDescription,
          tint = MaterialTheme.colorScheme.onSecondaryContainer,
          modifier = Modifier.size(15.dp),
        )
      }
    }
  }
}

@Composable
private fun InspectOrientationBadge(
  item: InspectItem,
  modifier: Modifier = Modifier,
) {
  val orientation = item.screenOrientation
  val icon =
    when (orientation) {
      ScreenOrientation.PORTRAIT -> Icons.Rounded.StayCurrentPortrait
      ScreenOrientation.LANDSCAPE -> Icons.Rounded.StayCurrentLandscape
      ScreenOrientation.SQUARE_UNFOLDED -> Icons.Rounded.TabletAndroid
    }
  val label =
    when (orientation) {
      ScreenOrientation.PORTRAIT -> stringResource(DbR.string.orientation_portrait)
      ScreenOrientation.LANDSCAPE -> stringResource(DbR.string.orientation_landscape)
      ScreenOrientation.SQUARE_UNFOLDED ->
        when {
          item.screenHeight > item.screenWidth ->
            stringResource(DbR.string.orientation_unfolded_portrait)
          item.screenWidth > item.screenHeight ->
            stringResource(DbR.string.orientation_unfolded_landscape)
          else -> stringResource(DbR.string.orientation_unfolded_square)
        }
    }
  val containerColor =
    when (orientation) {
      ScreenOrientation.SQUARE_UNFOLDED -> MaterialTheme.colorScheme.tertiaryContainer
      ScreenOrientation.LANDSCAPE -> MaterialTheme.colorScheme.secondaryContainer
      ScreenOrientation.PORTRAIT -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
  val contentColor =
    when (orientation) {
      ScreenOrientation.SQUARE_UNFOLDED -> MaterialTheme.colorScheme.onTertiaryContainer
      ScreenOrientation.LANDSCAPE -> MaterialTheme.colorScheme.onSecondaryContainer
      ScreenOrientation.PORTRAIT -> MaterialTheme.colorScheme.onSurfaceVariant
    }

  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(8.dp),
    color = containerColor,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = contentColor,
        modifier = Modifier.size(13.dp),
      )
      Text(
        text = "$label • ${item.screenWidth} × ${item.screenHeight}",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = contentColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

private fun parseHexColorOrDefault(hex: String): Color {
  val clean = hex.removePrefix("#").trim()
  val parsed = clean.toLongOrNull(16) ?: return Color.Gray
  return when (clean.length) {
    6 -> Color((0xFF000000L or parsed).toInt())
    8 -> Color(parsed.toInt())
    else -> Color.Gray
  }
}

@Preview(showBackground = true)
@Composable
private fun InspectTabPreview() {
  MaterialTheme {
    InspectTabContent(
      state =
        HomeUiState(
          expandedInspectGroupIds = setOf("g1"),
          expandedInspectItemIds = setOf("i1"),
          inspectGroups =
            listOf(
              InspectGroupWithItems(
                group = InspectGroup("g1", "RPG Lobby", listOf("Lobby", "Buttons")),
                items =
                  listOf(
                    InspectItem(
                      id = "i1",
                      groupId = "g1",
                      fullScreenPngPath = "",
                      screenWidth = 1080,
                      screenHeight = 2400,
                      appName = "Fantasy Quest (Cover)",
                      packageName = "com.jarvis.tapflow.game",
                      pixels =
                        listOf(
                          InspectPixel("px1", "Confirm Gold", 540, 1820, "#FFC107"),
                        ),
                      crops =
                        listOf(
                          InspectCrop("cr1", "Start Button", "", 400, 1750, 220, 96),
                        ),
                    ),
                    InspectItem(
                      id = "i2",
                      groupId = "g1",
                      fullScreenPngPath = "",
                      screenWidth = 2076,
                      screenHeight = 2152,
                      appName = "Fantasy Quest (Unfolded)",
                      packageName = "com.jarvis.tapflow.game",
                    ),
                  ),
              ),
            ),
        ),
      onIntent = {},
    )
  }
}
