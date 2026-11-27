package vn.unicom.thi360.feature.catalog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import vn.unicom.thi360.R
import vn.unicom.thi360.core.config.SupportContact
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.theme.LocalAppLocale
import vn.unicom.thi360.core.theme.Thi360Theme

@Composable
internal fun BundleDetailContent(
    detail: ProductDetailData,
    sellable: Boolean,
    accessCell: AccessCell,
    supportContact: SupportContact,
    onRetryAccess: () -> Unit,
    onOpenExam: (String) -> Unit,
    onOpenCheckout: (code: String, versionNo: Int, price: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalAppLocale.current
    val accessItems = remember(accessCell) {
        (accessCell as? AccessCell.Ready)?.row?.items.orEmpty()
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val wideDetail = maxWidth >= 720.dp
        if (wideDetail) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
                    .testTag(ProductDetailTestTags.SCROLL_CONTAINER),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    BundleHeroCard(detail = detail, locale = locale)
                    BundleSectionsColumn(
                        detail = detail,
                        accessItems = accessItems,
                        locale = locale,
                        supportContact = supportContact,
                        onOpenExam = onOpenExam,
                    )
                }
                BundlePricePanel(
                    detail = detail,
                    sellable = sellable,
                    accessCell = accessCell,
                    locale = locale,
                    onRetryAccess = onRetryAccess,
                    onOpenCheckout = onOpenCheckout,
                    modifier = Modifier.width(300.dp),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .testTag(ProductDetailTestTags.SCROLL_CONTAINER),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                BundleHeroCard(detail = detail, locale = locale)
                BundlePricePanel(
                    detail = detail,
                    sellable = sellable,
                    accessCell = accessCell,
                    locale = locale,
                    onRetryAccess = onRetryAccess,
                    onOpenCheckout = onOpenCheckout,
                )
                BundleSectionsColumn(
                    detail = detail,
                    accessItems = accessItems,
                    locale = locale,
                    supportContact = supportContact,
                    onOpenExam = onOpenExam,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BundleHeroCard(
    detail: ProductDetailData,
    locale: AppLocale,
) {
    val colors = Thi360Theme.colors
    val kindLabel = CatalogFormatting.pickLocalizedName(
        detail.examKind.nameVi,
        detail.examKind.nameEn,
        locale,
    ).ifBlank { detail.examKind.code }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.bgSurface,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 5f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(Brush.linearGradient(listOf(colors.primaryFill, colors.primaryText)))
                    .padding(16.dp)
                    .testTag(ProductDetailTestTags.HERO_BUNDLE_BANNER),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = colors.onPrimary.copy(alpha = 0.2f),
                ) {
                    Text(
                        text = detail.examKind.code,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = CatalogIcons.Bundle,
                        contentDescription = null,
                        tint = colors.onPrimary,
                        modifier = Modifier.size(28.dp),
                    )
                    Text(
                        text = stringResource(R.string.storefront_format_BUNDLE),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.onPrimary,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CatalogChip(text = kindLabel, tone = ChipTone.Primary)
                    CatalogChip(text = formatLabel(detail.format), tone = ChipTone.Subtle)
                    CatalogChip(
                        text = pluralStringResource(
                            R.plurals.bundle_detail_examCountValue,
                            detail.items.size,
                            detail.items.size,
                        ),
                        tone = ChipTone.Subtle,
                    )
                    if (detail.isReview) {
                        CatalogChip(
                            text = stringResource(R.string.storefront_review),
                            tone = ChipTone.Info,
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.storefront_detail_codeWithValue, detail.code),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted,
                    modifier = Modifier.testTag(ProductDetailTestTags.PRODUCT_CODE),
                )

                Text(
                    text = detail.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.text,
                    modifier = Modifier
                        .semantics { heading() }
                        .testTag(ProductDetailTestTags.PRODUCT_NAME),
                )

                detail.summary?.let { summary ->
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textSecondary,
                    )
                }

                detail.description?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun BundlePricePanel(
    detail: ProductDetailData,
    sellable: Boolean,
    accessCell: AccessCell,
    locale: AppLocale,
    onRetryAccess: () -> Unit,
    onOpenCheckout: (code: String, versionNo: Int, price: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    val status = Thi360Theme.statusColors
    val promo = detail.promotion
    val promoEnd = promo?.endsAt?.let { CatalogFormatting.formatPromoDayMonth(it) }
    val questionsText = CatalogFormatting.formatIntRange(detail.questionCount, locale)
    val minutesText = CatalogFormatting.formatIntRange(detail.durationMinutes, locale)
    val accessItems = (accessCell as? AccessCell.Ready)?.row?.items.orEmpty()
    val ownedItems = accessItems.filter { it.attemptsLeft > 0 }
    val pendingItems = accessItems.filter { it.hasPendingFulfillment }
    val buttonShape = RoundedCornerShape(12.dp)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.bgSurface,
        border = BorderStroke(1.dp, colors.border),
        modifier = modifier
            .fillMaxWidth()
            .testTag(ProductDetailTestTags.BUNDLE_PRICE_PANEL),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.bundle_detail_priceLabel),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.textMuted,
                modifier = Modifier.semantics { heading() },
            )

            ProductPriceView(
                code = detail.code,
                listPrice = detail.listPrice,
                price = detail.price,
                promotion = detail.promotion,
                locale = locale,
                large = true,
                showPromoEnd = false,
            )

            if (promo != null && promoEnd != null) {
                Text(
                    text = stringResource(R.string.storefront_detail_promoLine, promo.name, promoEnd),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = status.warning,
                )
            }

            HorizontalDivider(color = colors.border)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DetailKeyValueRow(
                    label = stringResource(R.string.bundle_detail_examCountLabel),
                    value = pluralStringResource(
                        R.plurals.bundle_detail_examCountValue,
                        detail.items.size,
                        detail.items.size,
                    ),
                )
                DetailKeyValueRow(
                    label = stringResource(R.string.bundle_detail_attemptsPerExamLabel),
                    value = pluralStringResource(
                        R.plurals.storefront_units_attempts,
                        detail.attemptsGranted,
                        detail.attemptsGranted,
                    ),
                )
                if (questionsText != null) {
                    DetailKeyValueRow(
                        label = stringResource(R.string.bundle_detail_totalQuestionsLabel),
                        value = stringResource(R.string.storefront_units_questions, questionsText),
                    )
                }
                if (minutesText != null) {
                    DetailKeyValueRow(
                        label = stringResource(R.string.bundle_detail_totalDurationLabel),
                        value = stringResource(R.string.storefront_units_minutes, minutesText),
                    )
                }
            }

            Text(
                text = stringResource(R.string.storefront_detail_includesTax),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
            )

            if (!sellable) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = status.warningBg,
                    border = BorderStroke(1.dp, status.warning),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(ProductDetailTestTags.BUNDLE_UNAVAILABLE_BANNER),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            imageVector = CatalogIcons.Warning,
                            contentDescription = null,
                            tint = status.warning,
                            modifier = Modifier.size(20.dp),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = stringResource(R.string.bundle_detail_unavailableTitle),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = status.warning,
                            )
                            Text(
                                text = stringResource(R.string.bundle_detail_unavailableBody),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.text,
                            )
                        }
                    }
                }
                Button(
                    onClick = {},
                    enabled = false,
                    shape = buttonShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag(ProductDetailTestTags.BUNDLE_BUY_BUTTON),
                ) {
                    Text(
                        text = stringResource(R.string.bundle_buyLabel),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            } else {
                when (accessCell) {
                    AccessCell.Loading -> {
                        val loadingLabel = stringResource(R.string.storefront_access_loading)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(buttonShape)
                                .background(colors.bgSubtle)
                                .semantics { contentDescription = loadingLabel }
                                .testTag(CatalogTestTags.productAccessLoading(detail.code)),
                        )
                    }

                    AccessCell.Failed -> {
                        Text(
                            text = stringResource(R.string.storefront_access_loadError),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.dangerText,
                            modifier = Modifier.testTag(CatalogTestTags.productAccessError(detail.code)),
                        )
                        OutlinedButton(
                            onClick = onRetryAccess,
                            shape = buttonShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag(CatalogTestTags.productAccessRetry(detail.code)),
                        ) {
                            Icon(
                                imageVector = CatalogIcons.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(text = stringResource(R.string.storefront_access_reload))
                        }
                    }

                    is AccessCell.Ready -> {
                        Button(
                            onClick = { onOpenCheckout(detail.code, detail.versionNo, detail.price) },
                            shape = buttonShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primaryFill,
                                contentColor = colors.onPrimary,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag(ProductDetailTestTags.BUNDLE_BUY_BUTTON),
                        ) {
                            Text(
                                text = stringResource(R.string.bundle_buyLabel),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        if (ownedItems.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = status.infoBg,
                                border = BorderStroke(1.dp, colors.primaryFill),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(ProductDetailTestTags.BUNDLE_OWNED_NOTES),
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    ownedItems.forEach { owned ->
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.Top,
                                        ) {
                                            Icon(
                                                imageVector = CatalogIcons.Info,
                                                contentDescription = null,
                                                tint = status.info,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Text(
                                                text = pluralStringResource(
                                                    R.plurals.bundle_detail_existingEntitlement,
                                                    owned.attemptsLeft,
                                                    owned.attemptsLeft,
                                                    owned.name,
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.text,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (pendingItems.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = status.warningBg,
                                border = BorderStroke(1.dp, status.warning),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(ProductDetailTestTags.BUNDLE_PENDING_NOTES),
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    pendingItems.forEach { pending ->
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.Top,
                                        ) {
                                            Icon(
                                                imageVector = CatalogIcons.Info,
                                                contentDescription = null,
                                                tint = status.warning,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            val pendingText = if (!pending.pendingOrderCode.isNullOrBlank()) {
                                                stringResource(
                                                    R.string.bundle_detail_pendingOrder,
                                                    pending.pendingOrderCode,
                                                    pending.name,
                                                )
                                            } else {
                                                stringResource(
                                                    R.string.bundle_detail_pendingOrderNoCode,
                                                    pending.name,
                                                )
                                            }
                                            Text(
                                                text = pendingText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.text,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Text(
                            text = stringResource(R.string.bundle_buyNote),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BundleSectionsColumn(
    detail: ProductDetailData,
    accessItems: List<BundleExamAccessRow>,
    locale: AppLocale,
    supportContact: SupportContact,
    onOpenExam: (String) -> Unit,
) {
    val colors = Thi360Theme.colors
    val accessById = remember(accessItems) { accessItems.associateBy { it.examId } }
    val accessByCode = remember(accessItems) { accessItems.associateBy { it.code } }

    DetailSectionCard(
        title = stringResource(R.string.bundle_detail_itemsTitle),
        badge = {
            CatalogChip(
                text = pluralStringResource(
                    R.plurals.bundle_detail_examCountValue,
                    detail.items.size,
                    detail.items.size,
                ),
                tone = ChipTone.Subtle,
            )
        },
        modifier = Modifier.testTag(ProductDetailTestTags.BUNDLE_ITEMS_SECTION),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            detail.items.forEachIndexed { index, item ->
                val access = accessById[item.examId] ?: accessByCode[item.code]
                BundleExamRowCard(
                    index = index,
                    item = item,
                    access = access,
                    locale = locale,
                    onOpenExam = onOpenExam,
                )
            }
        }
        Text(
            text = stringResource(R.string.bundle_detail_itemsNote),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textMuted,
        )
    }

    DetailSectionCard(
        title = stringResource(R.string.bundle_detail_howTitle),
        modifier = Modifier.testTag(ProductDetailTestTags.BUNDLE_HOW_SECTION),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "• ${stringResource(R.string.bundle_detail_how1)}",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            Text(
                text = "• ${stringResource(R.string.bundle_detail_how2)}",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            Text(
                text = "• ${stringResource(R.string.bundle_detail_how3)}",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }

    PolicyCard(supportContact = supportContact)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BundleExamRowCard(
    index: Int,
    item: BundleDetailItemData,
    access: BundleExamAccessRow?,
    locale: AppLocale,
    onOpenExam: (String) -> Unit,
) {
    val colors = Thi360Theme.colors
    val subjectLabel = item.subjectName?.let {
        CatalogFormatting.pickLocalizedName(it.nameVi, it.nameEn, locale)
    }?.takeIf { it.isNotBlank() }
    val scopeLabel = when (item.scope) {
        "PART" -> stringResource(R.string.bundle_detail_partScope)
        "SUBJECT" -> stringResource(R.string.bundle_detail_subjectScope)
        else -> item.scope
    }
    val metaLine = listOfNotNull(
        subjectLabel,
        scopeLabel.takeIf { it.isNotBlank() },
        item.partCode,
    ).joinToString(" · ")

    val questionsText = CatalogFormatting.formatIntRange(item.questionCount, locale)
    val durationText = CatalogFormatting.formatIntRange(item.durationMinutes, locale)
    val maxPointsText = CatalogFormatting.formatDecimalRange(item.maxPoints, locale)
    val stats = buildList {
        if (questionsText != null) add(stringResource(R.string.storefront_units_questions, questionsText))
        if (durationText != null) add(stringResource(R.string.storefront_units_minutes, durationText))
        if (maxPointsText != null) add(stringResource(R.string.storefront_detail_how_maxScore, maxPointsText))
    }

    val yesLabel = stringResource(R.string.storefront_detail_rights_yes)
    val noLabel = stringResource(R.string.storefront_detail_rights_no)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.bgSubtle,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ProductDetailTestTags.bundleItemRow(item.code)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(colors.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = (index + 1).toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.primaryText,
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(onClick = { onOpenExam(item.code) })
                            .testTag(ProductDetailTestTags.bundleItemTitle(item.code)),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.primaryText,
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (item.isReview) {
                            CatalogChip(
                                text = stringResource(R.string.storefront_review),
                                tone = ChipTone.Info,
                            )
                        }
                        if (access != null && access.attemptsLeft > 0) {
                            CatalogChip(
                                text = pluralStringResource(
                                    R.plurals.storefront_access_attemptsLeft,
                                    access.attemptsLeft,
                                    access.attemptsLeft,
                                ),
                                tone = ChipTone.Success,
                                modifier = Modifier.testTag(ProductDetailTestTags.bundleItemAccessChip(item.code)),
                            )
                        }
                        if (access != null && access.hasPendingFulfillment) {
                            CatalogChip(
                                text = stringResource(R.string.storefront_access_checking),
                                tone = ChipTone.Warning,
                                modifier = Modifier.testTag(ProductDetailTestTags.bundleItemAccessChip(item.code)),
                            )
                        }
                    }

                    if (metaLine.isNotEmpty()) {
                        Text(
                            text = metaLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                        )
                    }

                    if (stats.isNotEmpty()) {
                        Text(
                            text = stats.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = colors.textSecondary,
                        )
                    }
                }
            }

            HorizontalDivider(color = colors.border)

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val rightsHead = buildString {
                    append(stringResource(R.string.bundle_detail_examRightsPrefix))
                    append(' ')
                    append(
                        pluralStringResource(
                            R.plurals.storefront_units_attempts,
                            item.attemptsGranted,
                            item.attemptsGranted,
                        ),
                    )
                    item.resultView?.let { rv ->
                        append(" · ")
                        append(resultViewLabel(rv))
                    }
                }
                Text(
                    text = rightsHead,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = colors.text,
                )
                DetailKeyValueRow(
                    label = stringResource(R.string.storefront_detail_rights_practice),
                    value = if (item.benefits.practice) yesLabel else noLabel,
                )
                DetailKeyValueRow(
                    label = stringResource(R.string.storefront_detail_rights_strengths),
                    value = if (item.benefits.strengthWeakness) yesLabel else noLabel,
                )
                DetailKeyValueRow(
                    label = stringResource(R.string.storefront_detail_rights_ai),
                    value = if (item.benefits.aiExplanation) yesLabel else noLabel,
                )
                item.benefits.note?.let { note ->
                    Text(
                        text = "${stringResource(R.string.storefront_detail_rights_noteLabel)} $note",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted,
                    )
                }
            }
        }
    }
}
