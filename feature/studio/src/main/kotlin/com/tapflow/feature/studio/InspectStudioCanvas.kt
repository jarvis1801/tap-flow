package com.tapflow.feature.studio

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.tapflow.core.model.inspect.InspectCrop
import com.tapflow.core.model.inspect.InspectItem
import com.tapflow.core.model.inspect.InspectPixel
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class StudioLoadedImage(
  val softwareBitmap: Bitmap,
  val imageBitmap: ImageBitmap,
)

@Composable
fun rememberCoilSoftwareImage(fullScreenPngPath: String): StudioLoadedImage? {
  val context = LocalContext.current
  var loaded by remember(fullScreenPngPath) { mutableStateOf<StudioLoadedImage?>(null) }

  LaunchedEffect(fullScreenPngPath) {
    if (fullScreenPngPath.isBlank()) {
      loaded = null
      return@LaunchedEffect
    }
    val file = File(fullScreenPngPath)
    if (!file.exists()) {
      loaded = null
      return@LaunchedEffect
    }
    val result =
      withContext(Dispatchers.IO) {
        val request =
          ImageRequest.Builder(context)
            .data(file)
            .allowHardware(false)
            .build()
        val res = context.imageLoader.execute(request)
        if (res is SuccessResult) {
          val bmp = res.drawable.toBitmap()
          val sw =
            if (bmp.config == Bitmap.Config.HARDWARE) {
              bmp.copy(Bitmap.Config.ARGB_8888, false)
            } else {
              bmp
            }
          StudioLoadedImage(softwareBitmap = sw, imageBitmap = sw.asImageBitmap())
        } else {
          null
        }
      }
    loaded = result
  }

  return loaded
}

internal class ImageTransform(
  val baseScale: Float,
  val totalScale: Float,
  val left: Float,
  val top: Float,
  val imgWidth: Int,
  val imgHeight: Int,
) {
  fun imageToScreen(ix: Float, iy: Float): Offset =
    Offset(left + ix * totalScale, top + iy * totalScale)

  fun screenToImage(sx: Float, sy: Float): Pair<Int, Int> {
    val ix = ((sx - left) / totalScale).roundToInt().coerceIn(0, (imgWidth - 1).coerceAtLeast(0))
    val iy = ((sy - top) / totalScale).roundToInt().coerceIn(0, (imgHeight - 1).coerceAtLeast(0))
    return ix to iy
  }
}

internal fun computeImageTransform(
  viewport: IntSize,
  imgWidth: Int,
  imgHeight: Int,
  zoom: Float,
  pan: Offset,
): ImageTransform? {
  if (viewport.width <= 0 || viewport.height <= 0 || imgWidth <= 0 || imgHeight <= 0) return null
  val baseScale = min(viewport.width.toFloat() / imgWidth, viewport.height.toFloat() / imgHeight)
  val totalScale = baseScale * zoom
  val drawnW = imgWidth * totalScale
  val drawnH = imgHeight * totalScale
  val baseLeft = (viewport.width - imgWidth * baseScale) / 2f
  val baseTop = (viewport.height - imgHeight * baseScale) / 2f
  val maxPanX = max(0f, (drawnW - viewport.width) / 2f + viewport.width * 0.25f)
  val maxPanY = max(0f, (drawnH - viewport.height) / 2f + viewport.height * 0.25f)
  val clampedPanX = pan.x.coerceIn(-maxPanX, maxPanX)
  val clampedPanY = pan.y.coerceIn(-maxPanY, maxPanY)
  val left = baseLeft - (drawnW - imgWidth * baseScale) / 2f + clampedPanX
  val top = baseTop - (drawnH - imgHeight * baseScale) / 2f + clampedPanY
  return ImageTransform(
    baseScale = baseScale,
    totalScale = totalScale,
    left = left,
    top = top,
    imgWidth = imgWidth,
    imgHeight = imgHeight,
  )
}

@Composable
fun InteractiveInspectCanvas(
  state: InspectStudioUiState,
  item: InspectItem,
  loadedImage: StudioLoadedImage?,
  zoom: Float,
  onZoomChange: (Float) -> Unit,
  onIntent: (InspectStudioUiIntent) -> Unit,
  modifier: Modifier = Modifier,
) {
  var viewportSize by remember { mutableStateOf(IntSize.Zero) }
  var panOffset by remember { mutableStateOf(Offset.Zero) }
  var activeTouchOffset by remember { mutableStateOf<Offset?>(null) }

  val imgW = loadedImage?.imageBitmap?.width ?: item.screenWidth.coerceAtLeast(1)
  val imgH = loadedImage?.imageBitmap?.height ?: item.screenHeight.coerceAtLeast(1)

  val transform =
    remember(viewportSize, imgW, imgH, zoom, panOffset) {
      computeImageTransform(viewportSize, imgW, imgH, zoom, panOffset)
    }

  val primaryColor = MaterialTheme.colorScheme.primary
  val tertiaryColor = MaterialTheme.colorScheme.tertiary
  val density = LocalDensity.current
  val touchHitRadiusPx = with(density) { 24.dp.toPx() }
  val cropSlopPx = with(density) { 8.dp.toPx() }
  val textMeasurer = rememberTextMeasurer()

  val latestState by rememberUpdatedState(state)
  val latestItem by rememberUpdatedState(item)
  val latestLoadedImage by rememberUpdatedState(loadedImage)
  val latestZoom by rememberUpdatedState(zoom)
  val latestOnZoomChange by rememberUpdatedState(onZoomChange)
  val latestOnIntent by rememberUpdatedState(onIntent)

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .background(Color(0xFF0E1014))
        .onSizeChanged { viewportSize = it },
  ) {
    Canvas(
      modifier =
        Modifier
          .fillMaxSize()
          .pointerInput(item.id, imgW, imgH) {
            awaitEachGesture {
              val firstDown = awaitFirstDown(requireUnconsumed = false)
              val singleFingerDownPos = firstDown.position
              var isMultiTouch = false
              var hasDraggedSingleFinger = false
              var cropAnchorImg: Pair<Int, Int>? = null
              var dragMode = SingleDragKind.POINT_OR_NEW_CROP
              val downState = latestState
              val initialCrop = IntSize(downState.cropWidth, downState.cropHeight)
              val initialCropOrigin = IntOffset(downState.cropX, downState.cropY)

              val downTf = computeImageTransform(viewportSize, imgW, imgH, latestZoom, panOffset)
              if (downTf != null && downState.mode == StudioMode.IMAGE_CROP) {
                val (ix, iy) = downTf.screenToImage(firstDown.position.x, firstDown.position.y)
                val insideCrop =
                  ix in downState.cropX..(downState.cropX + downState.cropWidth) &&
                    iy in downState.cropY..(downState.cropY + downState.cropHeight)
                val nearBottomRight =
                  abs(ix - (downState.cropX + downState.cropWidth)) <= (24f / downTf.totalScale).coerceAtLeast(12f) &&
                    abs(iy - (downState.cropY + downState.cropHeight)) <= (24f / downTf.totalScale).coerceAtLeast(12f)
                dragMode =
                  when {
                    nearBottomRight -> SingleDragKind.RESIZE_CROP
                    insideCrop -> SingleDragKind.MOVE_CROP
                    else -> SingleDragKind.NEW_CROP
                  }
                cropAnchorImg = ix to iy
              }

              while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) {
                  // Clean single-tap release
                  val currentState = latestState
                  val releaseTf = computeImageTransform(viewportSize, imgW, imgH, latestZoom, panOffset) ?: downTf
                  if (!isMultiTouch && !hasDraggedSingleFinger && releaseTf != null) {
                    handleSingleTap(
                      tapPos = singleFingerDownPos,
                      tf = releaseTf,
                      touchHitRadiusPx = touchHitRadiusPx,
                      cropSlopPx = cropSlopPx,
                      state = currentState,
                      item = latestItem,
                      loadedImage = latestLoadedImage,
                      onIntent = latestOnIntent,
                    )
                  }
                  activeTouchOffset = null
                  break
                }

                if (pressed.size >= 2) {
                  isMultiTouch = true
                  activeTouchOffset = null
                  val zoomDelta = event.calculateZoom()
                  val panDelta = event.calculatePan()
                  val centroid = event.calculateCentroid(useCurrent = true)
                  val curZoom = latestZoom
                  val currentTransform =
                    computeImageTransform(viewportSize, imgW, imgH, curZoom, panOffset)
                  val nextZoom = (curZoom * zoomDelta).coerceIn(1.0f, 10.0f)

                  if (currentTransform != null && zoomDelta != 1f) {
                    val scaleRatio = nextZoom / curZoom
                    val focalX = (centroid.x - viewportSize.width / 2f)
                    val focalY = (centroid.y - viewportSize.height / 2f)
                    val zoomedPan =
                      Offset(
                        x = (panOffset.x - focalX) * scaleRatio + focalX + panDelta.x,
                        y = (panOffset.y - focalY) * scaleRatio + focalY + panDelta.y,
                      )
                    panOffset = if (nextZoom <= 1.01f) Offset.Zero else zoomedPan
                  } else {
                    panOffset += panDelta
                  }
                  latestOnZoomChange(nextZoom)
                  pressed.forEach { it.consume() }
                } else if (!isMultiTouch) {
                  val pointer = pressed.first()
                  val deltaFromDown = pointer.position - singleFingerDownPos
                  if (!hasDraggedSingleFinger && hypot(deltaFromDown.x, deltaFromDown.y) > viewConfiguration.touchSlop) {
                    hasDraggedSingleFinger = true
                  }
                  if (hasDraggedSingleFinger) {
                    activeTouchOffset = pointer.position
                    val currentState = latestState
                    val tf = computeImageTransform(viewportSize, imgW, imgH, latestZoom, panOffset)
                    if (tf != null) {
                      val (ix, iy) = tf.screenToImage(pointer.position.x, pointer.position.y)
                      when (currentState.mode) {
                        StudioMode.PIXEL_INSPECT -> {
                          val hex =
                            latestLoadedImage?.softwareBitmap?.let { bmp ->
                              StudioBitmapProcessor.formatArgbToHex(
                                bmp.getPixel(
                                  ix.coerceIn(0, bmp.width - 1),
                                  iy.coerceIn(0, bmp.height - 1),
                                ),
                              )
                            }
                          latestOnIntent(InspectStudioUiIntent.UpdatePixelCursor(ix, iy, hex))
                        }

                        StudioMode.IMAGE_CROP -> {
                          val anchor = cropAnchorImg ?: (ix to iy)
                          when (dragMode) {
                            SingleDragKind.NEW_CROP, SingleDragKind.POINT_OR_NEW_CROP -> {
                              val left = min(anchor.first, ix)
                              val top = min(anchor.second, iy)
                              val w = max(8, abs(ix - anchor.first))
                              val h = max(8, abs(iy - anchor.second))
                              latestOnIntent(InspectStudioUiIntent.UpdateCropBox(left, top, w, h))
                            }
                            SingleDragKind.MOVE_CROP -> {
                              val dx = ix - anchor.first
                              val dy = iy - anchor.second
                              latestOnIntent(
                                InspectStudioUiIntent.UpdateCropBox(
                                  x = initialCropOrigin.x + dx,
                                  y = initialCropOrigin.y + dy,
                                  width = initialCrop.width,
                                  height = initialCrop.height,
                                ),
                              )
                            }
                            SingleDragKind.RESIZE_CROP -> {
                              val newW = max(12, ix - initialCropOrigin.x)
                              val newH = max(12, iy - initialCropOrigin.y)
                              latestOnIntent(
                                InspectStudioUiIntent.UpdateCropBox(
                                  x = initialCropOrigin.x,
                                  y = initialCropOrigin.y,
                                  width = newW,
                                  height = newH,
                                ),
                              )
                            }
                          }
                        }
                      }
                    }
                  }
                  pointer.consume()
                }
              }
            }
          },
    ) {
      val tf = transform ?: return@Canvas
      val dstOffset = IntOffset(tf.left.roundToInt(), tf.top.roundToInt())
      val dstSize = IntSize((imgW * tf.totalScale).roundToInt(), (imgH * tf.totalScale).roundToInt())

      if (loadedImage != null) {
        drawImage(
          image = loadedImage.imageBitmap,
          srcOffset = IntOffset.Zero,
          srcSize = IntSize(imgW, imgH),
          dstOffset = dstOffset,
          dstSize = dstSize,
          filterQuality = FilterQuality.None,
        )
      } else {
        drawRect(
          color = Color(0xFF1D222B),
          topLeft = Offset(tf.left, tf.top),
          size = Size(dstSize.width.toFloat(), dstSize.height.toFloat()),
        )
      }

      // Crisp Pixel Grid at high zoom (when single pixel >= 7px on screen)
      if (tf.totalScale >= 7f) {
        val visibleStartX = max(0, ((0f - tf.left) / tf.totalScale).toInt())
        val visibleEndX = min(imgW, ((size.width - tf.left) / tf.totalScale).toInt() + 1)
        val visibleStartY = max(0, ((0f - tf.top) / tf.totalScale).toInt())
        val visibleEndY = min(imgH, ((size.height - tf.top) / tf.totalScale).toInt() + 1)
        val gridColor = Color.White.copy(alpha = 0.22f)
        for (px in visibleStartX..visibleEndX) {
          val sx = tf.left + px * tf.totalScale
          drawLine(
            color = gridColor,
            start = Offset(sx, max(tf.top, 0f)),
            end = Offset(sx, min(tf.top + dstSize.height, size.height)),
            strokeWidth = 1f,
          )
        }
        for (py in visibleStartY..visibleEndY) {
          val sy = tf.top + py * tf.totalScale
          drawLine(
            color = gridColor,
            start = Offset(max(tf.left, 0f), sy),
            end = Offset(min(tf.left + dstSize.width, size.width), sy),
            strokeWidth = 1f,
          )
        }
      }

      // Render all saved Pixel & Crop markers if toggled ON
      if (state.markersVisible) {
        val popupTarget = state.inspectedMarkerPopup
        item.crops.forEach { crop ->
          val tl = tf.imageToScreen(crop.x.toFloat(), crop.y.toFloat())
          val wPx = crop.width * tf.totalScale
          val hPx = crop.height * tf.totalScale
          val isHighlighted =
            crop.id == state.selectedCropId ||
              (popupTarget is StudioMarkerInfoPopup.Crop && popupTarget.crop.id == crop.id)
          val strokeColor = if (isHighlighted) tertiaryColor else Color(0xFFFFC107)

          // Subtle translucent fill + outer dark halo + crisp stroke
          drawRect(
            color = strokeColor.copy(alpha = 0.14f),
            topLeft = tl,
            size = Size(wPx, hPx),
          )
          drawRect(
            color = Color.Black.copy(alpha = 0.55f),
            topLeft = tl,
            size = Size(wPx, hPx),
            style = Stroke(width = (if (isHighlighted) 5.dp else 3.5.dp).toPx()),
          )
          drawRect(
            color = strokeColor,
            topLeft = tl,
            size = Size(wPx, hPx),
            style = Stroke(width = (if (isHighlighted) 3.dp else 2.dp).toPx()),
          )

          // Corner markers
          val cornerR = if (isHighlighted) 4.dp.toPx() else 3.dp.toPx()
          listOf(
            tl,
            Offset(tl.x + wPx, tl.y),
            Offset(tl.x, tl.y + hPx),
            Offset(tl.x + wPx, tl.y + hPx),
          ).forEach { corner ->
            drawCircle(color = Color.Black.copy(alpha = 0.7f), radius = cornerR + 1.dp.toPx(), center = corner)
            drawCircle(color = strokeColor, radius = cornerR, center = corner)
          }

          // Compact label badge
          val badgeLabel = crop.label.ifBlank { "${crop.width}×${crop.height}" }
          val textLayout =
            textMeasurer.measure(
              text = badgeLabel,
              style = TextStyle(
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
              ),
              maxLines = 1,
            )
          val padH = 5.dp.toPx()
          val padV = 2.dp.toPx()
          val pillW = textLayout.size.width + padH * 2f
          val pillH = textLayout.size.height + padV * 2f
          val badgeTop = if (tl.y - pillH - 3.dp.toPx() >= 0f) tl.y - pillH - 3.dp.toPx() else tl.y + 3.dp.toPx()
          val badgeLeft = tl.x.coerceIn(2.dp.toPx(), (size.width - pillW - 2.dp.toPx()).coerceAtLeast(2.dp.toPx()))
          drawRoundRect(
            color = Color(0xCC0D1117),
            topLeft = Offset(badgeLeft, badgeTop),
            size = Size(pillW, pillH),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
          )
          drawText(
            textLayoutResult = textLayout,
            topLeft = Offset(badgeLeft + padH, badgeTop + padV),
          )
        }

        item.pixels.forEach { px ->
          val center = tf.imageToScreen(px.x.toFloat(), px.y.toFloat())
          val isHighlighted =
            px.id == state.selectedPixelId ||
              (popupTarget is StudioMarkerInfoPopup.Pixel && popupTarget.pixel.id == px.id)
          val radius = if (isHighlighted) 11.dp.toPx() else 8.5.dp.toPx()
          drawCircle(
            color = Color.Black.copy(alpha = 0.65f),
            radius = radius + 2.dp.toPx(),
            center = center,
            style = Stroke(width = 2.dp.toPx()),
          )
          drawCircle(
            color = parseStudioHexColor(px.hexColor),
            radius = radius,
            center = center,
          )
          drawCircle(
            color = if (isHighlighted) primaryColor else Color.White,
            radius = radius,
            center = center,
            style = Stroke(width = if (isHighlighted) 3.dp.toPx() else 2.dp.toPx()),
          )
        }
      }

      // Active Mode Overlay
      if (state.mode == StudioMode.PIXEL_INSPECT) {
        val c = tf.imageToScreen(state.cursorX.toFloat(), state.cursorY.toFloat())
        val crossRadius = 18.dp.toPx()
        drawCircle(
          color = Color.White,
          radius = 10.dp.toPx(),
          center = c,
          style = Stroke(width = 2.5.dp.toPx()),
        )
        drawCircle(
          color = primaryColor,
          radius = 12.dp.toPx(),
          center = c,
          style = Stroke(width = 1.5.dp.toPx()),
        )
        drawLine(
          color = Color.White,
          start = Offset(c.x - crossRadius, c.y),
          end = Offset(c.x - 5.dp.toPx(), c.y),
          strokeWidth = 2.dp.toPx(),
        )
        drawLine(
          color = Color.White,
          start = Offset(c.x + 5.dp.toPx(), c.y),
          end = Offset(c.x + crossRadius, c.y),
          strokeWidth = 2.dp.toPx(),
        )
        drawLine(
          color = Color.White,
          start = Offset(c.x, c.y - crossRadius),
          end = Offset(c.x, c.y - 5.dp.toPx()),
          strokeWidth = 2.dp.toPx(),
        )
        drawLine(
          color = Color.White,
          start = Offset(c.x, c.y + 5.dp.toPx()),
          end = Offset(c.x, c.y + crossRadius),
          strokeWidth = 2.dp.toPx(),
        )
      } else {
        val tl = tf.imageToScreen(state.cropX.toFloat(), state.cropY.toFloat())
        val wPx = state.cropWidth * tf.totalScale
        val hPx = state.cropHeight * tf.totalScale
        // Darken border outside selection slightly inside image
        drawRect(
          color = primaryColor.copy(alpha = 0.14f),
          topLeft = tl,
          size = Size(wPx, hPx),
        )
        drawRect(
          color = primaryColor,
          topLeft = tl,
          size = Size(wPx, hPx),
          style = Stroke(width = 2.5.dp.toPx()),
        )
        // Corner handles
        val handleR = 6.dp.toPx()
        listOf(
          tl,
          Offset(tl.x + wPx, tl.y),
          Offset(tl.x, tl.y + hPx),
          Offset(tl.x + wPx, tl.y + hPx),
        ).forEach { corner ->
          drawCircle(color = primaryColor, radius = handleR, center = corner)
          drawCircle(color = Color.White, radius = handleR * 0.55f, center = corner)
        }
      }
    }

    // Dynamic Touch Loupe Overlay (appears on touch-down, smart-flips near top edge, hides on touch-up)
    activeTouchOffset?.let { touchPos ->
      DynamicTouchLoupeOverlay(
        fingerPos = touchPos,
        viewportSize = viewportSize,
        state = state,
        loadedImage = loadedImage,
      )
    }
  }
}

private enum class SingleDragKind {
  POINT_OR_NEW_CROP,
  NEW_CROP,
  MOVE_CROP,
  RESIZE_CROP,
}

private fun handleSingleTap(
  tapPos: Offset,
  tf: ImageTransform,
  touchHitRadiusPx: Float,
  cropSlopPx: Float,
  state: InspectStudioUiState,
  item: InspectItem,
  loadedImage: StudioLoadedImage?,
  onIntent: (InspectStudioUiIntent) -> Unit,
) {
  if (state.markersVisible) {
    // Hit-test saved InspectPixel markers first (~24.dp screen touch radius)
    val hitPixel =
      item.pixels
        .mapNotNull { px ->
          val screenPt = tf.imageToScreen(px.x.toFloat(), px.y.toFloat())
          val dist = hypot(screenPt.x - tapPos.x, screenPt.y - tapPos.y)
          if (dist <= touchHitRadiusPx) px to dist else null
        }
        .minByOrNull { it.second }
        ?.first
    if (hitPixel != null) {
      onIntent(InspectStudioUiIntent.ShowMarkerInfo(StudioMarkerInfoPopup.Pixel(hitPixel)))
      return
    }

    // Hit-test saved InspectCrop rectangles regardless of active StudioMode
    val (tapIx, tapIy) = tf.screenToImage(tapPos.x, tapPos.y)
    val hitCrop =
      item.crops.firstOrNull { crop ->
        val insideImg =
          tapIx in crop.x..(crop.x + crop.width) && tapIy in crop.y..(crop.y + crop.height)
        if (insideImg) {
          true
        } else {
          val tl = tf.imageToScreen(crop.x.toFloat(), crop.y.toFloat())
          val wPx = crop.width * tf.totalScale
          val hPx = crop.height * tf.totalScale
          tapPos.x in (tl.x - cropSlopPx)..(tl.x + wPx + cropSlopPx) &&
            tapPos.y in (tl.y - cropSlopPx)..(tl.y + hPx + cropSlopPx)
        }
      }
    if (hitCrop != null) {
      onIntent(InspectStudioUiIntent.ShowMarkerInfo(StudioMarkerInfoPopup.Crop(hitCrop)))
      return
    }
  }

  val (ix, iy) = tf.screenToImage(tapPos.x, tapPos.y)
  if (state.mode == StudioMode.PIXEL_INSPECT) {
    val hex =
      loadedImage?.softwareBitmap?.let { bmp ->
        StudioBitmapProcessor.formatArgbToHex(
          bmp.getPixel(ix.coerceIn(0, bmp.width - 1), iy.coerceIn(0, bmp.height - 1)),
        )
      }
    onIntent(InspectStudioUiIntent.UpdatePixelCursor(ix, iy, hex))
  } else {
    onIntent(
      InspectStudioUiIntent.UpdateCropBox(
        x = ix - state.cropWidth / 2,
        y = iy - state.cropHeight / 2,
        width = state.cropWidth,
        height = state.cropHeight,
      ),
    )
  }
}

@Composable
private fun DynamicTouchLoupeOverlay(
  fingerPos: Offset,
  viewportSize: IntSize,
  state: InspectStudioUiState,
  loadedImage: StudioLoadedImage?,
) {
  val density = LocalDensity.current
  val loupePx = with(density) { 136.dp.toPx() }
  val fingerGapPx = with(density) { 56.dp.toPx() }
  val topSafeThresholdPx = with(density) { 80.dp.toPx() } + loupePx + fingerGapPx

  val placeBelowFinger = fingerPos.y < topSafeThresholdPx
  val rawY =
    if (placeBelowFinger) {
      fingerPos.y + fingerGapPx
    } else {
      fingerPos.y - loupePx - fingerGapPx
    }
  val maxX = (viewportSize.width - loupePx - 16f).coerceAtLeast(16f)
  val maxY = (viewportSize.height - loupePx - 16f).coerceAtLeast(16f)
  val clampedX = (fingerPos.x - loupePx / 2f).coerceIn(16f, maxX).roundToInt()
  val clampedY = rawY.coerceIn(16f, maxY).roundToInt()

  Surface(
    shape = if (state.mode == StudioMode.PIXEL_INSPECT) CircleShape else RoundedCornerShape(16.dp),
    color = Color(0xFF14171D),
    tonalElevation = 8.dp,
    modifier =
      Modifier
        .offset { IntOffset(clampedX, clampedY) }
        .size(136.dp)
        .shadow(10.dp, if (state.mode == StudioMode.PIXEL_INSPECT) CircleShape else RoundedCornerShape(16.dp))
        .border(
          width = 2.dp,
          color = MaterialTheme.colorScheme.primary,
          shape = if (state.mode == StudioMode.PIXEL_INSPECT) CircleShape else RoundedCornerShape(16.dp),
        ),
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      Canvas(
        modifier =
          Modifier
            .fillMaxSize()
            .clip(if (state.mode == StudioMode.PIXEL_INSPECT) CircleShape else RoundedCornerShape(16.dp)),
      ) {
        val img = loadedImage?.imageBitmap ?: return@Canvas
        if (state.mode == StudioMode.PIXEL_INSPECT) {
          // 10x crosshair magnification lens around (cursorX, cursorY)
          val sampleRadius = max(6, (size.width / 20f).roundToInt())
          val sx = (state.cursorX - sampleRadius).coerceIn(0, (img.width - 1).coerceAtLeast(0))
          val sy = (state.cursorY - sampleRadius).coerceIn(0, (img.height - 1).coerceAtLeast(0))
          val sw = (sampleRadius * 2 + 1).coerceAtMost(img.width - sx).coerceAtLeast(1)
          val sh = (sampleRadius * 2 + 1).coerceAtMost(img.height - sy).coerceAtLeast(1)
          drawImage(
            image = img,
            srcOffset = IntOffset(sx, sy),
            srcSize = IntSize(sw, sh),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            filterQuality = FilterQuality.None,
          )
          val cx = size.width / 2f
          val cy = size.height / 2f
          drawLine(Color.White.copy(alpha = 0.85f), Offset(0f, cy), Offset(size.width, cy), strokeWidth = 1.5f)
          drawLine(Color.White.copy(alpha = 0.85f), Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 1.5f)
          drawRect(
            color = Color.Cyan,
            topLeft = Offset(cx - 5f, cy - 5f),
            size = Size(10f, 10f),
            style = Stroke(width = 2f),
          )
        } else {
          val sx = state.cropX.coerceIn(0, (img.width - 1).coerceAtLeast(0))
          val sy = state.cropY.coerceIn(0, (img.height - 1).coerceAtLeast(0))
          val sw = state.cropWidth.coerceIn(1, (img.width - sx).coerceAtLeast(1))
          val sh = state.cropHeight.coerceIn(1, (img.height - sy).coerceAtLeast(1))
          drawImage(
            image = img,
            srcOffset = IntOffset(sx, sy),
            srcSize = IntSize(sw, sh),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            filterQuality = FilterQuality.None,
          )
        }
      }

      Surface(
        color = Color.Black.copy(alpha = 0.72f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
      ) {
        if (state.mode == StudioMode.PIXEL_INSPECT) {
          Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Box(
              modifier =
                Modifier
                  .size(10.dp)
                  .clip(CircleShape)
                  .background(parseStudioHexColor(state.cursorHex))
                  .border(1.dp, Color.White, CircleShape),
            )
            Text(
              text = "${state.cursorHex} (${state.cursorX},${state.cursorY})",
              style = MaterialTheme.typography.labelSmall,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              color = Color.White,
            )
          }
        } else {
          Text(
            text = "${state.cropWidth} × ${state.cropHeight} px",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
          )
        }
      }
    }
  }
}

internal fun parseStudioHexColor(hex: String): Color {
  val clean = hex.removePrefix("#").trim()
  val parsed = clean.toLongOrNull(16) ?: return Color.Gray
  return when (clean.length) {
    6 -> Color((0xFF000000L or parsed).toInt())
    8 -> Color(parsed.toInt())
    else -> Color.Gray
  }
}
