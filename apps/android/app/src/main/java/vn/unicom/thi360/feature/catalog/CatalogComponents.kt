package vn.unicom.thi360.feature.catalog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import vn.unicom.thi360.R
import vn.unicom.thi360.Thi360Application
import vn.unicom.thi360.core.brand.BrandMark
import vn.unicom.thi360.core.config.SupportContact
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.theme.Thi360Theme

internal class DoubleTapGuard(
    private val clockMillis: () -> Long,
    private val cooldownMs: Long = DEFAULT_COOLDOWN_MS,
) {
    private var lastTriggerMillis: Long = Long.MIN_VALUE

    fun run(action: () -> Unit) {
        val now = clockMillis()
        if (lastTriggerMillis == Long.MIN_VALUE || now - lastTriggerMillis >= cooldownMs) {
            lastTriggerMillis = now
            action()
        }
    }

    companion object {
        const val DEFAULT_COOLDOWN_MS = 500L
    }
}

@Composable
internal fun rememberDoubleTapGuard(
    clockMillis: () -> Long,
    cooldownMs: Long = DoubleTapGuard.DEFAULT_COOLDOWN_MS,
): DoubleTapGuard = remember(clockMillis, cooldownMs) {
    DoubleTapGuard(clockMillis = clockMillis, cooldownMs = cooldownMs)
}

internal enum class AccessVariant {
    Card,
    Detail,
}

internal enum class ChipTone {
    Primary,
    Subtle,
    Info,
    Success,
    Warning,
    Accent,
}

@Composable
internal fun CatalogChip(
    text: String,
    tone: ChipTone,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    val status = Thi360Theme.statusColors
    val (bg, fg, borderColor) = when (tone) {
        ChipTone.Primary -> Triple(colors.primaryContainer, colors.primaryText, colors.primaryFill)
        ChipTone.Subtle -> Triple(colors.bgSubtle, colors.textSecondary, Color.Transparent)
        ChipTone.Info -> Triple(status.infoBg, status.info, Color.Transparent)
        ChipTone.Success -> Triple(status.successBg, status.success, Color.Transparent)
        ChipTone.Warning -> Triple(status.warningBg, status.warning, status.warning)
        ChipTone.Accent -> Triple(colors.accent, colors.onAccent, Color.Transparent)
    }
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(999.dp),
        color = bg,
        border = if (borderColor != Color.Transparent) BorderStroke(1.dp, borderColor) else null,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
internal fun formatLabel(format: String): String = when (format) {
    "FULL" -> stringResource(R.string.storefront_format_FULL)
    "PART" -> stringResource(R.string.storefront_format_PART)
    "SUBJECT" -> stringResource(R.string.storefront_format_SUBJECT)
    "BUNDLE" -> stringResource(R.string.storefront_format_BUNDLE)
    else -> format
}

@Composable
internal fun sortLabel(sort: CatalogSort): String = when (sort) {
    CatalogSort.NEWEST -> stringResource(R.string.storefront_catalog_sort_NEWEST)
    CatalogSort.PRICE_ASC -> stringResource(R.string.storefront_catalog_sort_PRICE_ASC)
    CatalogSort.PRICE_DESC -> stringResource(R.string.storefront_catalog_sort_PRICE_DESC)
}

@Composable
internal fun answerTypeLabel(type: String): String = when (type) {
    "CLOZE" -> stringResource(R.string.storefront_detail_answerType_CLOZE)
    "ESSAY" -> stringResource(R.string.storefront_detail_answerType_ESSAY)
    "MATCHING" -> stringResource(R.string.storefront_detail_answerType_MATCHING)
    "MULTIPLE_CHOICE" -> stringResource(R.string.storefront_detail_answerType_MULTIPLE_CHOICE)
    "QUESTION_SET" -> stringResource(R.string.storefront_detail_answerType_QUESTION_SET)
    "SHORT_ANSWER" -> stringResource(R.string.storefront_detail_answerType_SHORT_ANSWER)
    "SINGLE_CHOICE" -> stringResource(R.string.storefront_detail_answerType_SINGLE_CHOICE)
    "TRUE_FALSE" -> stringResource(R.string.storefront_detail_answerType_TRUE_FALSE)
    else -> type
}

@Composable
internal fun resultViewLabel(resultView: String): String = when (resultView) {
    "FULL_SOLUTION" -> stringResource(R.string.storefront_detail_rights_resultViews_FULL_SOLUTION)
    "SCORE" -> stringResource(R.string.storefront_detail_rights_resultViews_SCORE)
    "SCORE_AND_ANSWERS" -> stringResource(R.string.storefront_detail_rights_resultViews_SCORE_AND_ANSWERS)
    else -> resultView
}

@Composable
internal fun FormatAndScopeChips(
    format: String,
    scopeLabel: LocalizedNameData?,
    locale: AppLocale,
) {
    CatalogChip(text = formatLabel(format), tone = ChipTone.Subtle)
    val scopeName = scopeLabel?.let { CatalogFormatting.pickLocalizedName(it.nameVi, it.nameEn, locale) }
        ?.takeIf { it.isNotBlank() }
    if (scopeName != null) {
        CatalogChip(text = scopeName, tone = ChipTone.Subtle)
    }
}

@Composable
internal fun ProductCover(
    code: String,
    coverUrl: String?,
    examKind: ExamKindData,
    format: String,
    isBundle: Boolean,
    discountPercent: Int?,
    locale: AppLocale,
    apiBaseUrl: String,
    imageLoader: ImageLoader?,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 16f / 9f,
) {
    val context = LocalContext.current
    val resolvedUrl = remember(isBundle, apiBaseUrl, coverUrl) {
        if (isBundle) null else CatalogFormatting.resolveCoverUrl(apiBaseUrl, coverUrl)
    }
    var imageFailed by remember(resolvedUrl) { mutableStateOf(false) }
    var imageLoaded by remember(resolvedUrl) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
    ) {
        if (resolvedUrl == null || imageFailed || !imageLoaded) {
            DefaultProductCover(
                code = code,
                examKind = examKind,
                format = format,
                isBundle = isBundle,
                locale = locale,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (resolvedUrl == null || imageFailed) {
                            Modifier.testTag(CatalogTestTags.productCoverFallback(code))
                        } else {
                            Modifier
                        },
                    ),
            )
        }

        if (resolvedUrl != null && !imageFailed) {
            val resolvedLoader = imageLoader ?: remember(context) {
                (context.applicationContext as? Thi360Application)?.imageLoader?.imageLoader
                    ?: SingletonImageLoader.get(context)
            }
            AsyncImage(
                model = resolvedUrl,
                imageLoader = resolvedLoader,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onState = { state ->
                    when (state) {
                        is AsyncImagePainter.State.Success -> {
                            imageLoaded = true
                            imageFailed = false
                        }
                        is AsyncImagePainter.State.Error -> {
                            imageLoaded = false
                            imageFailed = true
                        }
                        else -> Unit
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(CatalogTestTags.productCover(code)),
            )
        }

        if (discountPercent != null) {
            CatalogChip(
                text = stringResource(R.string.storefront_coverDiscount, discountPercent),
                tone = ChipTone.Accent,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clearAndSetSemantics {},
            )
        }
    }
}

@Composable
private fun DefaultProductCover(
    code: String,
    examKind: ExamKindData,
    format: String,
    isBundle: Boolean,
    locale: AppLocale,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    val kindLabel = CatalogFormatting.pickLocalizedName(examKind.nameVi, examKind.nameEn, locale)
        .ifBlank { examKind.code }
    val bundleLabel = stringResource(R.string.storefront_format_BUNDLE)

    val (backgroundBrush, foreground, badgeBg, badgeFg, icon) = when {
        isBundle -> CoverStyle(
            brush = Brush.linearGradient(listOf(colors.primaryFill, colors.primaryText)),
            fg = colors.onPrimary,
            badgeBg = colors.onPrimary.copy(alpha = 0.18f),
            badgeFg = colors.onPrimary,
            icon = CatalogIcons.Bundle,
        )
        format == "FULL" -> CoverStyle(
            brush = Brush.linearGradient(listOf(colors.primaryFill, colors.primaryText)),
            fg = colors.onPrimary,
            badgeBg = colors.onPrimary.copy(alpha = 0.18f),
            badgeFg = colors.onPrimary,
            icon = CatalogIcons.Book,
        )
        format == "PART" -> CoverStyle(
            brush = Brush.linearGradient(listOf(colors.primaryContainer, colors.primaryContainerStrong)),
            fg = colors.primaryText,
            badgeBg = colors.bgSurface,
            badgeFg = colors.primaryText,
            icon = CatalogIcons.Grid,
        )
        else -> CoverStyle(
            brush = Brush.linearGradient(listOf(colors.bgSurface, colors.primaryContainer)),
            fg = colors.primaryText,
            badgeBg = colors.primaryContainer,
            badgeFg = colors.primaryText,
            icon = CatalogIcons.Document,
        )
    }

    Column(
        modifier = modifier
            .background(backgroundBrush)
            .padding(16.dp)
            .clearAndSetSemantics {},
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = badgeBg,
        ) {
            Text(
                text = if (isBundle) examKind.code else kindLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = badgeFg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(if (isBundle) 24.dp else 18.dp),
            )
            Text(
                text = if (isBundle) bundleLabel else code,
                style = if (isBundle) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private data class CoverStyle(
    val brush: Brush,
    val fg: Color,
    val badgeBg: Color,
    val badgeFg: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

@Composable
internal fun ExamDetailHeroCover(
    detail: ProductDetailData,
    apiBaseUrl: String,
    imageLoader: ImageLoader?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resolvedUrl = remember(apiBaseUrl, detail.coverUrl) {
        CatalogFormatting.resolveCoverUrl(apiBaseUrl, detail.coverUrl)
    }
    var imageFailed by remember(resolvedUrl) { mutableStateOf(false) }
    var imageLoaded by remember(resolvedUrl) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 6f)
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
    ) {
        if (resolvedUrl == null || imageFailed || !imageLoaded) {
            ExamDetailBrandPlaceholder(
                examKindCode = detail.examKind.code,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(ProductDetailTestTags.HERO_BRAND_PLACEHOLDER),
            )
        }

        if (resolvedUrl != null && !imageFailed) {
            val resolvedLoader = imageLoader ?: remember(context) {
                (context.applicationContext as? Thi360Application)?.imageLoader?.imageLoader
                    ?: SingletonImageLoader.get(context)
            }
            AsyncImage(
                model = resolvedUrl,
                imageLoader = resolvedLoader,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onState = { state ->
                    when (state) {
                        is AsyncImagePainter.State.Success -> {
                            imageLoaded = true
                            imageFailed = false
                        }
                        is AsyncImagePainter.State.Error -> {
                            imageLoaded = false
                            imageFailed = true
                        }
                        else -> Unit
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(ProductDetailTestTags.HERO_COVER),
            )
        }
    }
}

@Composable
private fun ExamDetailBrandPlaceholder(
    examKindCode: String,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    Box(
        modifier = modifier
            .background(
                Brush.linearGradient(
                    listOf(colors.primaryContainer, colors.primaryContainerStrong),
                ),
            )
            .padding(20.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BrandMark(markSize = 40.dp, showName = false)
            CatalogChip(
                text = examKindCode,
                tone = ChipTone.Primary,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProductPriceView(
    code: String,
    listPrice: Long,
    price: Long,
    promotion: PromotionBadgeData?,
    locale: AppLocale,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    showPromoEnd: Boolean = true,
) {
    val colors = Thi360Theme.colors
    val status = Thi360Theme.statusColors
    val priceFormatted = stringResource(
        R.string.storefront_price,
        CatalogFormatting.formatPriceAmount(price, locale),
    )
    val listPriceFormatted = stringResource(
        R.string.storefront_price,
        CatalogFormatting.formatPriceAmount(listPrice, locale),
    )
    val percent = if (promotion != null) CatalogFormatting.discountPercent(listPrice, price) else null
    val promoEndDayMonth = promotion?.endsAt?.let { CatalogFormatting.formatPromoDayMonth(it) }
    val listPriceA11y = "${stringResource(R.string.storefront_listPriceLabel)} $listPriceFormatted"

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = priceFormatted,
                style = if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.primaryText,
                modifier = Modifier.testTag(CatalogTestTags.productPrice(code)),
            )
            if (percent != null) {
                Text(
                    text = listPriceFormatted,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    textDecoration = TextDecoration.LineThrough,
                    modifier = Modifier
                        .semantics { contentDescription = listPriceA11y }
                        .testTag(CatalogTestTags.productListPrice(code)),
                )
                CatalogChip(
                    text = stringResource(R.string.storefront_discount, percent),
                    tone = ChipTone.Warning,
                    modifier = Modifier.testTag(CatalogTestTags.productDiscount(code)),
                )
            }
        }
        if (showPromoEnd && percent != null && promoEndDayMonth != null) {
            Text(
                text = stringResource(R.string.storefront_promoEnds, promoEndDayMonth),
                style = MaterialTheme.typography.labelMedium,
                color = status.warning,
                modifier = Modifier.testTag(CatalogTestTags.productPromoEnd(code)),
            )
        }
    }
}

@Composable
internal fun AccessActionPanel(
    code: String,
    seenVersionNo: Int,
    seenPrice: Long,
    isBundle: Boolean,
    cell: AccessCell,
    variant: AccessVariant,
    onRetryAccess: () -> Unit,
    onOpenDetail: () -> Unit,
    onOpenCheckout: (code: String, versionNo: Int, price: Long) -> Unit,
    onOpenPrepare: (entitlementId: String) -> Unit,
    onOpenAttempt: (attemptId: String) -> Unit,
    onOpenMyExams: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    val status = Thi360Theme.statusColors
    val spacing = if (variant == AccessVariant.Detail) 12.dp else 8.dp
    val buttonShape = RoundedCornerShape(12.dp)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        when (cell) {
            AccessCell.Loading -> {
                val loadingLabel = stringResource(R.string.storefront_access_loading)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(buttonShape)
                        .background(colors.bgSubtle)
                        .semantics { contentDescription = loadingLabel }
                        .testTag(CatalogTestTags.productAccessLoading(code)),
                )
            }

            AccessCell.Failed -> {
                Text(
                    text = stringResource(R.string.storefront_access_loadError),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.dangerText,
                    modifier = Modifier.testTag(CatalogTestTags.productAccessError(code)),
                )
                OutlinedButton(
                    onClick = onRetryAccess,
                    shape = buttonShape,
                    border = BorderStroke(1.dp, colors.borderStrong),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.text),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag(CatalogTestTags.productAccessRetry(code)),
                ) {
                    Icon(
                        imageVector = CatalogIcons.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(text = stringResource(R.string.storefront_access_reload))
                }
                if (variant == AccessVariant.Card) {
                    ViewDetailButton(code = code, onClick = onOpenDetail)
                }
            }

            is AccessCell.Ready -> {
                val model = when (variant) {
                    AccessVariant.Card -> AccessButtons.forCard(
                        row = cell.row,
                        code = code,
                        seenVersionNo = seenVersionNo,
                        seenPrice = seenPrice,
                        isBundle = isBundle,
                    )
                    AccessVariant.Detail -> AccessButtons.forPricePanel(
                        row = cell.row,
                        code = code,
                        seenVersionNo = seenVersionNo,
                        seenPrice = seenPrice,
                    )
                }

                model.leadNote?.let { note ->
                    AccessNoteText(note = note)
                }

                PrimaryAccessButton(
                    code = code,
                    button = model.primary,
                    onOpenCheckout = onOpenCheckout,
                    onOpenPrepare = onOpenPrepare,
                    onOpenAttempt = onOpenAttempt,
                    onOpenMyExams = onOpenMyExams,
                )

                model.secondary?.let { secondary ->
                    SecondaryAccessButton(
                        code = code,
                        button = secondary,
                        onOpenCheckout = onOpenCheckout,
                    )
                }

                model.tailNote?.let { note ->
                    AccessNoteText(
                        note = note,
                        colorOverride = if (note is AccessNote.PendingTopUp) status.warning else null,
                    )
                }

                if (model.showViewDetail) {
                    ViewDetailButton(code = code, onClick = onOpenDetail)
                }
            }
        }
    }
}

@Composable
private fun AccessNoteText(
    note: AccessNote,
    colorOverride: Color? = null,
) {
    val colors = Thi360Theme.colors
    val text = when (note) {
        AccessNote.ContinueInProgress -> stringResource(R.string.storefront_access_continueNote)
        is AccessNote.AttemptsLeft -> pluralStringResource(
            R.plurals.storefront_access_attemptsLeft,
            note.count,
            note.count,
        )
        AccessNote.Exhausted -> stringResource(R.string.storefront_access_exhaustedNote)
        is AccessNote.PendingTopUp -> stringResource(R.string.storefront_access_pendingTopUp, note.orderCode)
        is AccessNote.CheckingOrder -> stringResource(R.string.storefront_access_checkingNote, note.orderCode)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = colorOverride ?: colors.textSecondary,
    )
}

@Composable
private fun PrimaryAccessButton(
    code: String,
    button: AccessButton,
    onOpenCheckout: (code: String, versionNo: Int, price: Long) -> Unit,
    onOpenPrepare: (entitlementId: String) -> Unit,
    onOpenAttempt: (attemptId: String) -> Unit,
    onOpenMyExams: () -> Unit,
) {
    val colors = Thi360Theme.colors
    val status = Thi360Theme.statusColors
    val shape = RoundedCornerShape(12.dp)

    when (button) {
        is AccessButton.Checking -> {
            OutlinedButton(
                onClick = {},
                enabled = false,
                shape = shape,
                border = BorderStroke(1.dp, status.warning),
                colors = ButtonDefaults.outlinedButtonColors(
                    disabledContainerColor = status.warningBg,
                    disabledContentColor = status.warning,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag(CatalogTestTags.productPrimaryAction(code)),
            ) {
                Text(
                    text = stringResource(R.string.storefront_access_checking),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        is AccessButton.Continue -> {
            Button(
                onClick = {
                    val attemptId = button.attemptId?.trim()?.takeIf { it.isNotEmpty() }
                    if (attemptId != null) onOpenAttempt(attemptId) else onOpenMyExams()
                },
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primaryFill,
                    contentColor = colors.onPrimary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag(CatalogTestTags.productPrimaryAction(code)),
            ) {
                Text(
                    text = stringResource(R.string.storefront_access_continue),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        is AccessButton.Enter -> {
            Button(
                onClick = {
                    val entitlementId = button.entitlementId?.trim()?.takeIf { it.isNotEmpty() }
                    if (entitlementId != null) onOpenPrepare(entitlementId) else onOpenMyExams()
                },
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primaryFill,
                    contentColor = colors.onPrimary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag(CatalogTestTags.productPrimaryAction(code)),
            ) {
                Text(
                    text = stringResource(R.string.storefront_access_enter),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        is AccessButton.Buy -> {
            Button(
                onClick = { onOpenCheckout(button.code, button.seenVersionNo, button.seenPrice) },
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primaryFill,
                    contentColor = colors.onPrimary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag(CatalogTestTags.productPrimaryAction(code)),
            ) {
                Text(
                    text = stringResource(
                        if (button.isBundle) R.string.storefront_access_buyBundle else R.string.storefront_access_buy,
                    ),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        is AccessButton.BuyAgain -> {
            Button(
                onClick = { onOpenCheckout(button.code, button.seenVersionNo, button.seenPrice) },
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primaryFill,
                    contentColor = colors.onPrimary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag(CatalogTestTags.productPrimaryAction(code)),
            ) {
                Text(
                    text = stringResource(R.string.storefront_access_buyAgain),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        is AccessButton.BuyMore -> {
            Button(
                onClick = { onOpenCheckout(button.code, button.seenVersionNo, button.seenPrice) },
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primaryFill,
                    contentColor = colors.onPrimary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag(CatalogTestTags.productPrimaryAction(code)),
            ) {
                Text(
                    text = stringResource(R.string.storefront_access_buyMore),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun SecondaryAccessButton(
    code: String,
    button: AccessButton,
    onOpenCheckout: (code: String, versionNo: Int, price: Long) -> Unit,
) {
    val colors = Thi360Theme.colors
    if (button is AccessButton.BuyMore) {
        OutlinedButton(
            onClick = { onOpenCheckout(button.code, button.seenVersionNo, button.seenPrice) },
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, colors.borderStrong),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.text),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag(CatalogTestTags.productSecondaryAction(code)),
        ) {
            Text(
                text = stringResource(R.string.storefront_access_buyMore),
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun ViewDetailButton(
    code: String,
    onClick: () -> Unit,
) {
    val colors = Thi360Theme.colors
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, colors.border),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = colors.bgSubtle,
            contentColor = colors.text,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag(CatalogTestTags.productViewDetail(code)),
    ) {
        Text(
            text = stringResource(R.string.storefront_access_viewDetail),
            fontWeight = FontWeight.Medium,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProductCardView(
    item: ProductCardItem,
    accessCell: AccessCell,
    isSelected: Boolean,
    locale: AppLocale,
    apiBaseUrl: String,
    imageLoader: ImageLoader?,
    onOpenDetail: (String) -> Unit,
    onRetryAccess: (String) -> Unit,
    onOpenCheckout: (code: String, versionNo: Int, price: Long) -> Unit,
    onOpenPrepare: (entitlementId: String) -> Unit,
    onOpenAttempt: (attemptId: String) -> Unit,
    onOpenMyExams: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    val discount = item.promotion?.let { CatalogFormatting.discountPercent(item.listPrice, item.price) }
    val questionsText = CatalogFormatting.formatIntRange(item.questionCount, locale)
    val minutesText = CatalogFormatting.formatIntRange(item.durationMinutes, locale)
    val metaParts = buildList {
        if (questionsText != null) {
            add(stringResource(R.string.storefront_units_questions, questionsText))
        }
        if (minutesText != null) {
            add(stringResource(R.string.storefront_units_minutes, minutesText))
        }
        add(
            if (item.isBundle) {
                pluralStringResource(
                    R.plurals.storefront_units_attemptsPerExam,
                    item.attemptsGranted,
                    item.attemptsGranted,
                )
            } else {
                pluralStringResource(
                    R.plurals.storefront_units_attempts,
                    item.attemptsGranted,
                    item.attemptsGranted,
                )
            },
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(CatalogTestTags.productCard(item.code)),
        shape = RoundedCornerShape(16.dp),
        color = colors.bgSurface,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) colors.primaryFill else colors.border,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = { onOpenDetail(item.code) })
                    .testTag(CatalogTestTags.productHeader(item.code)),
            ) {
                ProductCover(
                    code = item.code,
                    coverUrl = item.coverUrl,
                    examKind = item.examKind,
                    format = item.format,
                    isBundle = item.isBundle,
                    discountPercent = discount,
                    locale = locale,
                    apiBaseUrl = apiBaseUrl,
                    imageLoader = imageLoader,
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        CatalogChip(text = item.examKind.code, tone = ChipTone.Primary)
                        FormatAndScopeChips(
                            format = item.format,
                            scopeLabel = item.scopeLabel,
                            locale = locale,
                        )
                        if (item.isReview) {
                            CatalogChip(
                                text = stringResource(R.string.storefront_review),
                                tone = ChipTone.Info,
                            )
                        }
                    }

                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(CatalogTestTags.productTitle(item.code)),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (metaParts.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = colors.bgSubtle,
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = metaParts.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = colors.textSecondary,
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag(CatalogTestTags.productStats(item.code)),
                        )
                    }
                }

                ProductPriceView(
                    code = item.code,
                    listPrice = item.listPrice,
                    price = item.price,
                    promotion = item.promotion,
                    locale = locale,
                )

                AccessActionPanel(
                    code = item.code,
                    seenVersionNo = item.versionNo,
                    seenPrice = item.price,
                    isBundle = item.isBundle,
                    cell = accessCell,
                    variant = AccessVariant.Card,
                    onRetryAccess = { onRetryAccess(item.code) },
                    onOpenDetail = { onOpenDetail(item.code) },
                    onOpenCheckout = onOpenCheckout,
                    onOpenPrepare = onOpenPrepare,
                    onOpenAttempt = onOpenAttempt,
                    onOpenMyExams = onOpenMyExams,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun CatalogFilterSheet(
    filters: StorefrontFiltersData?,
    query: CatalogQuery,
    locale: AppLocale,
    onSelectFormat: (CatalogFormat?) -> Unit,
    onSelectPart: (kindCode: String, partCode: String) -> Unit,
    onSelectSubject: (String?) -> Unit,
    onClearPartAndSubject: () -> Unit,
    onSelectSort: (CatalogSort) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Thi360Theme.colors
    @Suppress("DEPRECATION")
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val availableFormats = remember(filters) { CatalogFilterOptions.formats(filters) }
    val partSubjectOptions = remember(filters, query.kind, locale) {
        CatalogFilterOptions.partAndSubjectOptions(filters, query.kind, locale)
    }
    val selectedPartSubjectKey = remember(query) { CatalogFilterOptions.selectedPartSubjectKey(query) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.bgSurface,
        modifier = Modifier.testTag(CatalogTestTags.FILTER_SHEET),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.storefront_catalog_filtersLabel),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.text,
                    modifier = Modifier.semantics { heading() },
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag(CatalogTestTags.FILTER_SHEET_CLOSE),
                ) {
                    Icon(
                        imageVector = CatalogIcons.Close,
                        contentDescription = stringResource(R.string.storefront_catalog_closeFilters),
                        tint = colors.text,
                    )
                }
            }

            if (availableFormats.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.storefront_catalog_formatLabel),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.text,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        CatalogSelectableChip(
                            selected = query.format == null,
                            label = stringResource(R.string.storefront_catalog_formatAll),
                            onClick = { onSelectFormat(null) },
                            modifier = Modifier.testTag(CatalogTestTags.FILTER_FORMAT_ALL),
                        )
                        availableFormats.forEach { fmt ->
                            CatalogSelectableChip(
                                selected = query.format == fmt,
                                label = formatLabel(fmt.apiValue),
                                onClick = { onSelectFormat(fmt) },
                                modifier = Modifier.testTag(CatalogTestTags.formatChip(fmt.apiValue)),
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = colors.bgSubtle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(CatalogTestTags.FILTER_FORMAT_HINT),
                    ) {
                        Text(
                            text = "${stringResource(R.string.storefront_catalog_formatHintLabel)} ${stringResource(R.string.storefront_catalog_formatHint)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }

            if (partSubjectOptions.size > 1) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.storefront_catalog_partLabel),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.text,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        partSubjectOptions.forEach { option ->
                            val label = when (option) {
                                PartSubjectOption.All -> stringResource(R.string.storefront_catalog_partAll)
                                is PartSubjectOption.Part -> option.label
                                is PartSubjectOption.Subject -> option.label
                            }
                            val tag = when (option) {
                                PartSubjectOption.All -> CatalogTestTags.FILTER_PART_ALL
                                else -> CatalogTestTags.partSubjectChip(option.key)
                            }
                            CatalogSelectableChip(
                                selected = selectedPartSubjectKey == option.key,
                                label = label,
                                onClick = {
                                    when (option) {
                                        PartSubjectOption.All -> onClearPartAndSubject()
                                        is PartSubjectOption.Part -> onSelectPart(option.kindCode, option.partCode)
                                        is PartSubjectOption.Subject -> onSelectSubject(option.subjectCode)
                                    }
                                },
                                modifier = Modifier.testTag(tag),
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.storefront_catalog_sortLabel),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.text,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    CatalogSort.entries.forEach { sort ->
                        CatalogSelectableChip(
                            selected = query.sort == sort,
                            label = sortLabel(sort),
                            onClick = { onSelectSort(sort) },
                            modifier = Modifier.testTag(CatalogTestTags.sortChip(sort)),
                        )
                    }
                }
            }

            HorizontalDivider(color = colors.border)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = {
                        onClearAll()
                        onDismiss()
                    },
                    enabled = query.isFiltered,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(CatalogTestTags.FILTER_SHEET_CLEAR),
                ) {
                    Text(text = stringResource(R.string.storefront_catalog_clearFilters))
                }
            }
        }
    }
}

@Composable
internal fun CatalogSelectableChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = colors.bgSubtle,
            labelColor = colors.textSecondary,
            selectedContainerColor = colors.primaryContainer,
            selectedLabelColor = colors.primaryText,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = colors.border,
            selectedBorderColor = colors.primaryFill,
        ),
        modifier = modifier.heightIn(min = 48.dp),
    )
}

@Composable
internal fun SupportContactBlock(
    supportContact: SupportContact,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (!supportContact.hasAnyChannel) {
            Text(
                text = stringResource(R.string.landing_support_pending),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
            )
        } else {
            val channels = listOfNotNull(
                supportContact.normalizedPhone,
                supportContact.normalizedEmail,
            ).joinToString(" · ")
            if (channels.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.storefront_detail_policy_contactLine, channels),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
            supportContact.normalizedHours?.let { hours ->
                Text(
                    text = stringResource(R.string.storefront_detail_policy_hoursLine, hours),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }
    }
}
