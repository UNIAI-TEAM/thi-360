package vn.unicom.thi360.feature.richcontent.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.unicom.thi360.R
import vn.unicom.thi360.feature.richcontent.assets.AttemptAssetSource
import vn.unicom.thi360.feature.richcontent.model.AttemptAssetRef
import vn.unicom.thi360.feature.richcontent.model.FigureWidth
import vn.unicom.thi360.feature.richcontent.model.RichInline

internal const val MAX_IMAGE_ZOOM_SCALE = 4f
internal const val MIN_IMAGE_ZOOM_SCALE = 1f

internal fun clampImageZoomScale(scale: Float): Float =
    scale.coerceIn(MIN_IMAGE_ZOOM_SCALE, MAX_IMAGE_ZOOM_SCALE)

internal fun clampImagePanOffset(
    offset: Offset,
    scale: Float,
    containerSize: IntSize,
): Offset {
    if (scale <= 1f || containerSize.width <= 0 || containerSize.height <= 0) {
        return Offset.Zero
    }
    val maxX = (containerSize.width * (scale - 1f)) / 2f
    val maxY = (containerSize.height * (scale - 1f)) / 2f
    return Offset(
        x = offset.x.coerceIn(-maxX, maxX),
        y = offset.y.coerceIn(-maxY, maxY),
    )
}

private sealed interface RichImageLoadState {
    data object Loading : RichImageLoadState
    data class Loaded(val bitmap: ImageBitmap) : RichImageLoadState
    data object Failed : RichImageLoadState
}

@Composable
internal fun RichImageView(
    image: RichInline.Image,
    figureWidth: FigureWidth,
    zoomable: Boolean,
    captionText: String?,
    assets: AttemptAssetSource?,
    palette: RichContentPalette,
    modifier: Modifier = Modifier,
) {
    var retryToken by remember(image.ref) { mutableIntStateOf(0) }
    var loadState by remember(image.ref) {
        mutableStateOf<RichImageLoadState>(
            if (image.ref === AttemptAssetRef.Blocked || assets == null) {
                RichImageLoadState.Failed
            } else {
                RichImageLoadState.Loading
            },
        )
    }
    var showZoomDialog by remember(image.ref) { mutableStateOf(false) }

    LaunchedEffect(image.ref, assets, retryToken) {
        if (image.ref === AttemptAssetRef.Blocked || assets == null) {
            loadState = RichImageLoadState.Failed
            return@LaunchedEffect
        }
        loadState = RichImageLoadState.Loading
        val decoded = runCatching {
            val bytes = assets.load(image.ref)
            withContext(Dispatchers.Default) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
        }.getOrNull()
        loadState = if (decoded != null) {
            RichImageLoadState.Loaded(decoded)
        } else {
            RichImageLoadState.Failed
        }
    }

    val widthModifier = Modifier.fillMaxWidth(figureWidth.widthFraction)

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        when (val state = loadState) {
            RichImageLoadState.Loading -> {
                val loadingDesc = image.alt.ifBlank { stringResource(R.string.richcontent_image_loading) }
                val placeholderModifier = if (image.width != null && image.height != null && image.width > 0 && image.height > 0) {
                    widthModifier.aspectRatio(image.width.toFloat() / image.height.toFloat())
                } else {
                    widthModifier.height(28.dp)
                }
                Box(
                    modifier = placeholderModifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.quoteBackground)
                        .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                        .semantics { contentDescription = loadingDesc }
                        .testTag(RichContentTestTags.IMAGE_LOADING),
                )
            }

            is RichImageLoadState.Loaded -> {
                val zoomActionLabel = stringResource(R.string.richcontent_image_zoom_action)
                val clickableModifier = if (zoomable) {
                    Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = zoomActionLabel,
                    ) {
                        showZoomDialog = true
                    }
                } else {
                    Modifier
                }

                Box(
                    modifier = widthModifier
                        .then(clickableModifier)
                        .testTag(RichContentTestTags.IMAGE_CONTENT),
                    contentAlignment = Alignment.Center,
                ) {
                    val aspect = if (state.bitmap.width > 0 && state.bitmap.height > 0) {
                        state.bitmap.width.toFloat() / state.bitmap.height.toFloat()
                    } else {
                        null
                    }
                    Image(
                        bitmap = state.bitmap,
                        contentDescription = image.alt.ifBlank { null },
                        contentScale = ContentScale.Fit,
                        colorFilter = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (aspect != null) Modifier.aspectRatio(aspect) else Modifier),
                    )
                    if (zoomable) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(palette.surface.copy(alpha = 0.9f))
                                .border(1.dp, palette.border, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            ZoomExpandIcon(color = palette.text, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                if (showZoomDialog && zoomable) {
                    RichImageZoomDialog(
                        bitmap = state.bitmap,
                        alt = image.alt,
                        captionText = captionText,
                        onDismiss = { showZoomDialog = false },
                    )
                }
            }

            RichImageLoadState.Failed -> {
                val failedText = stringResource(R.string.richcontent_image_load_failed)
                Column(
                    modifier = widthModifier
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.quoteBackground)
                        .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button) {
                            if (loadState is RichImageLoadState.Failed) {
                                loadState = RichImageLoadState.Loading
                                retryToken++
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                        .testTag(RichContentTestTags.IMAGE_ERROR),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    BrokenImageIcon(color = palette.textMuted, modifier = Modifier.size(28.dp))
                    if (image.alt.isNotBlank()) {
                        Text(
                            text = image.alt,
                            color = palette.textSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Text(
                        text = failedText,
                        color = palette.textMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
internal fun RichImageZoomDialog(
    bitmap: ImageBitmap,
    alt: String,
    captionText: String?,
    onDismiss: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val closeLabel = stringResource(R.string.common_close)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag(RichContentTestTags.IMAGE_ZOOM_DIALOG),
            color = Color(0xFF0F1418),
            contentColor = Color(0xFFE6EAED),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .clickable(role = Role.Button, onClick = onDismiss)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .semantics { contentDescription = closeLabel }
                            .testTag(RichContentTestTags.IMAGE_ZOOM_CLOSE),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = closeLabel,
                            color = Color(0xFFE6EAED),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    val badgeText = "${(scale * 10f).roundToInt() / 10f}×"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF1E262C))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag(RichContentTestTags.IMAGE_ZOOM_BADGE),
                    ) {
                        Text(
                            text = badgeText,
                            color = Color(0xFFE6EAED),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onSizeChanged { containerSize = it }
                        .semantics {
                            stateDescription = "scale=${"%.2f".format(scale)}"
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val nextScale = clampImageZoomScale(scale * zoom)
                                scale = nextScale
                                offset = clampImagePanOffset(offset + pan, nextScale, containerSize)
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (scale > 1.1f) {
                                        scale = 1f
                                        offset = Offset.Zero
                                    } else {
                                        scale = 2f
                                    }
                                },
                            )
                        }
                        .testTag(RichContentTestTags.IMAGE_ZOOM_SURFACE),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = alt.ifBlank { null },
                        contentScale = ContentScale.Fit,
                        colorFilter = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            },
                    )
                }

                if (!captionText.isNullOrBlank()) {
                    Text(
                        text = captionText,
                        color = Color(0xFFC5CCD2),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BrokenImageIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = size.minDimension * 0.08f, cap = StrokeCap.Round)
        val inset = size.minDimension * 0.12f
        drawRoundRect(
            color = color,
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2f, size.height - inset * 2f),
            cornerRadius = CornerRadius(size.minDimension * 0.12f),
            style = stroke,
        )
        drawLine(
            color = color,
            start = Offset(inset * 1.5f, size.height - inset * 1.5f),
            end = Offset(size.width - inset * 1.5f, inset * 1.5f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun ZoomExpandIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val s = size.minDimension
        val sw = s * 0.12f
        val pad = s * 0.15f
        val arm = s * 0.28f
        drawLine(color, Offset(s - pad - arm, pad), Offset(s - pad, pad), sw, StrokeCap.Round)
        drawLine(color, Offset(s - pad, pad), Offset(s - pad, pad + arm), sw, StrokeCap.Round)
        drawLine(color, Offset(s - pad, pad), Offset(s * 0.55f, s * 0.45f), sw, StrokeCap.Round)

        drawLine(color, Offset(pad, s - pad - arm), Offset(pad, s - pad), sw, StrokeCap.Round)
        drawLine(color, Offset(pad, s - pad), Offset(pad + arm, s - pad), sw, StrokeCap.Round)
        drawLine(color, Offset(pad, s - pad), Offset(s * 0.45f, s * 0.55f), sw, StrokeCap.Round)
    }
}
