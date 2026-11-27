package vn.unicom.thi360.feature.catalog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import vn.unicom.thi360.R
import vn.unicom.thi360.core.config.SupportContact
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.theme.LocalAppLocale
import vn.unicom.thi360.core.theme.Thi360Theme

@Composable
internal fun ExamDetailContent(
    detail: ProductDetailData,
    accessCell: AccessCell,
    apiBaseUrl: String,
    imageLoader: ImageLoader?,
    supportContact: SupportContact,
    onRetryAccess: () -> Unit,
    onOpenCheckout: (code: String, versionNo: Int, price: Long) -> Unit,
    onOpenPrepare: (entitlementId: String) -> Unit,
    onOpenAttempt: (attemptId: String) -> Unit,
    onOpenMyExams: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalAppLocale.current
    val hasEssay = remember(detail) { CatalogFormatting.hasEssay(detail) }

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
                    ExamHeroCard(
                        detail = detail,
                        locale = locale,
                        apiBaseUrl = apiBaseUrl,
                        imageLoader = imageLoader,
                    )
                    ExamSectionsColumn(
                        detail = detail,
                        locale = locale,
                        hasEssay = hasEssay,
                        supportContact = supportContact,
                    )
                }
                ExamPricePanel(
                    detail = detail,
                    accessCell = accessCell,
                    locale = locale,
                    onRetryAccess = onRetryAccess,
                    onOpenCheckout = onOpenCheckout,
                    onOpenPrepare = onOpenPrepare,
                    onOpenAttempt = onOpenAttempt,
                    onOpenMyExams = onOpenMyExams,
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
                ExamHeroCard(
                    detail = detail,
                    locale = locale,
                    apiBaseUrl = apiBaseUrl,
                    imageLoader = imageLoader,
                )
                ExamPricePanel(
                    detail = detail,
                    accessCell = accessCell,
                    locale = locale,
                    onRetryAccess = onRetryAccess,
                    onOpenCheckout = onOpenCheckout,
                    onOpenPrepare = onOpenPrepare,
                    onOpenAttempt = onOpenAttempt,
                    onOpenMyExams = onOpenMyExams,
                )
                ExamSectionsColumn(
                    detail = detail,
                    locale = locale,
                    hasEssay = hasEssay,
                    supportContact = supportContact,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExamHeroCard(
    detail: ProductDetailData,
    locale: AppLocale,
    apiBaseUrl: String,
    imageLoader: ImageLoader?,
) {
    val colors = Thi360Theme.colors

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.bgSurface,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ExamDetailHeroCover(
                detail = detail,
                apiBaseUrl = apiBaseUrl,
                imageLoader = imageLoader,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    CatalogChip(text = detail.examKind.code, tone = ChipTone.Primary)
                    FormatAndScopeChips(
                        format = detail.format,
                        scopeLabel = detail.scopeLabel,
                        locale = locale,
                    )
                    if (detail.isReview) {
                        CatalogChip(
                            text = stringResource(R.string.storefront_review),
                            tone = ChipTone.Info,
                        )
                    }
                }

                Text(
                    text = "${stringResource(R.string.storefront_detail_codeLabel)} ${detail.code}",
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

                detail.objectives?.let { objectives ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.storefront_detail_objectives),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.text,
                        )
                        Text(
                            text = objectives,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    }
                }

                detail.audience?.let { audience ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.storefront_detail_audience),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.text,
                        )
                        Text(
                            text = audience,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExamPricePanel(
    detail: ProductDetailData,
    accessCell: AccessCell,
    locale: AppLocale,
    onRetryAccess: () -> Unit,
    onOpenCheckout: (code: String, versionNo: Int, price: Long) -> Unit,
    onOpenPrepare: (entitlementId: String) -> Unit,
    onOpenAttempt: (attemptId: String) -> Unit,
    onOpenMyExams: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    val status = Thi360Theme.statusColors
    val promo = detail.promotion
    val promoEnd = promo?.endsAt?.let { CatalogFormatting.formatPromoDayMonth(it) }
    val questionsText = CatalogFormatting.formatIntRange(detail.questionCount, locale)
    val minutesText = CatalogFormatting.formatIntRange(detail.durationMinutes, locale)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.bgSurface,
        border = BorderStroke(1.dp, colors.border),
        modifier = modifier
            .fillMaxWidth()
            .testTag(ProductDetailTestTags.PRICE_PANEL),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.storefront_detail_priceHeading),
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

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = pluralStringResource(
                        R.plurals.storefront_detail_attemptsPerPurchase,
                        detail.attemptsGranted,
                        detail.attemptsGranted,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.text,
                )
                if (questionsText != null && minutesText != null) {
                    Text(
                        text = stringResource(
                            R.string.storefront_detail_perAttempt,
                            questionsText,
                            minutesText,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
                Text(
                    text = stringResource(R.string.storefront_detail_includesTax),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                )
            }

            AccessActionPanel(
                code = detail.code,
                seenVersionNo = detail.versionNo,
                seenPrice = detail.price,
                isBundle = false,
                cell = accessCell,
                variant = AccessVariant.Detail,
                onRetryAccess = onRetryAccess,
                onOpenDetail = {},
                onOpenCheckout = onOpenCheckout,
                onOpenPrepare = onOpenPrepare,
                onOpenAttempt = onOpenAttempt,
                onOpenMyExams = onOpenMyExams,
            )
        }
    }
}

@Composable
private fun ExamSectionsColumn(
    detail: ProductDetailData,
    locale: AppLocale,
    hasEssay: Boolean,
    supportContact: SupportContact,
) {
    if (detail.parts.isNotEmpty()) {
        ExamStructureCard(detail = detail, locale = locale)
    }
    if (detail.choices.isNotEmpty()) {
        ExamChoicesCard(choices = detail.choices, locale = locale)
    }
    ExamHowCard(detail = detail, locale = locale, hasEssay = hasEssay)
    ExamRightsCard(detail = detail, hasEssay = hasEssay)
    PolicyCard(supportContact = supportContact)
}

@Composable
private fun ExamStructureCard(
    detail: ProductDetailData,
    locale: AppLocale,
) {
    val colors = Thi360Theme.colors
    val emptyDash = stringResource(R.string.storefront_detail_emptyValue)
    val totalQuestions = CatalogFormatting.formatIntRange(detail.questionCount, locale)
    val totalMinutes = CatalogFormatting.formatIntRange(detail.durationMinutes, locale)

    DetailSectionCard(
        title = stringResource(R.string.storefront_detail_structureTitle),
        modifier = Modifier.testTag(ProductDetailTestTags.STRUCTURE_SECTION),
    ) {
        Text(
            text = stringResource(R.string.storefront_detail_structureCaption),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
        )
        if (totalQuestions != null && totalMinutes != null) {
            Text(
                text = stringResource(R.string.storefront_detail_structureTotal, totalQuestions, totalMinutes),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.primaryText,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            detail.parts.forEach { part ->
                val qText = CatalogFormatting.formatIntRange(part.questionCount, locale)
                    ?.let { stringResource(R.string.storefront_units_questions, it) } ?: emptyDash
                val dText = CatalogFormatting.formatIntRange(part.durationMinutes, locale)
                    ?.let { stringResource(R.string.storefront_units_minutes, it) } ?: emptyDash
                val pText = CatalogFormatting.formatDecimalRange(part.maxPoints, locale)
                    ?.let { stringResource(R.string.storefront_detail_how_points, it) } ?: emptyDash
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.bgSubtle,
                    border = BorderStroke(1.dp, colors.border),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val orderLabel = stringResource(R.string.storefront_detail_columns_order)
                            val partLabel = stringResource(R.string.storefront_detail_columns_part)
                            val natureLabel = stringResource(R.string.storefront_detail_columns_nature)
                            val natureValue = stringResource(
                                if (part.required) {
                                    R.string.storefront_detail_required
                                } else {
                                    R.string.storefront_detail_optional
                                },
                            )
                            Text(
                                text = "${part.order}. ${part.name}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.text,
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics {
                                        contentDescription = "$orderLabel ${part.order}, $partLabel ${part.name}"
                                    },
                            )
                            CatalogChip(
                                text = natureValue,
                                tone = if (part.required) ChipTone.Primary else ChipTone.Subtle,
                                modifier = Modifier.semantics {
                                    contentDescription = "$natureLabel: $natureValue"
                                },
                            )
                        }
                        DetailKeyValueRow(
                            label = stringResource(R.string.storefront_detail_columns_questions),
                            value = qText,
                        )
                        DetailKeyValueRow(
                            label = stringResource(R.string.storefront_detail_columns_duration),
                            value = dText,
                        )
                        DetailKeyValueRow(
                            label = stringResource(R.string.storefront_detail_columns_maxPoints),
                            value = pText,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExamChoicesCard(
    choices: List<DetailChoiceGroupData>,
    locale: AppLocale,
) {
    val colors = Thi360Theme.colors
    DetailSectionCard(
        title = stringResource(R.string.storefront_detail_choicesTitle),
        modifier = Modifier.testTag(ProductDetailTestTags.CHOICES_SECTION),
    ) {
        choices.forEachIndexed { idx, group ->
            if (idx > 0) {
                HorizontalDivider(color = colors.border)
            }
            Text(
                text = group.partName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.text,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                group.options.forEach { option ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.bgSubtle,
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = option.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.text,
                            )
                            if (option.chooseSubjects != null && option.subjects.isNotEmpty()) {
                                Text(
                                    text = stringResource(
                                        R.string.storefront_detail_chooseSubjects,
                                        option.chooseSubjects,
                                        option.subjects.size,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.primaryText,
                                )
                            }
                            if (option.countsByPick.isNotEmpty()) {
                                Text(
                                    text = stringResource(
                                        R.string.storefront_detail_countsByPick,
                                        option.countsByPick.joinToString(" / "),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary,
                                )
                            }
                            if (option.subjects.isNotEmpty()) {
                                val names = option.subjects.map { subj ->
                                    CatalogFormatting.pickLocalizedName(subj.nameVi, subj.nameEn, locale)
                                        .ifBlank { subj.code }
                                }
                                Text(
                                    text = names.joinToString(", "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary,
                                )
                            }
                            val ownMerged = CatalogFormatting.mergeAnswerTypes(option.answerTypes)
                            if (ownMerged.isNotEmpty()) {
                                val summary = ownMerged.map { merged ->
                                    "${answerTypeLabel(merged.type)}: ${merged.count}"
                                }.joinToString(" · ")
                                Text(
                                    text = summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary,
                                )
                            }
                            option.subjectAnswerTypes.forEach { subjTypes ->
                                val merged = CatalogFormatting.mergeAnswerTypes(subjTypes.answerTypes)
                                if (merged.isNotEmpty()) {
                                    val subjName = CatalogFormatting.pickLocalizedName(
                                        subjTypes.subject.nameVi,
                                        subjTypes.subject.nameEn,
                                        locale,
                                    ).ifBlank { subjTypes.subject.code }
                                    val summary = merged.map { m ->
                                        "${answerTypeLabel(m.type)}: ${m.count}"
                                    }.joinToString(" · ")
                                    Text(
                                        text = stringResource(
                                            R.string.storefront_detail_subjectAnswerTypes,
                                            subjName,
                                            summary,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textSecondary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExamHowCard(
    detail: ProductDetailData,
    locale: AppLocale,
    hasEssay: Boolean,
) {
    val colors = Thi360Theme.colors
    val status = Thi360Theme.statusColors
    val mergedPartTypes = remember(detail.parts) {
        CatalogFormatting.mergeAnswerTypes(detail.parts.flatMap { it.answerTypes })
    }
    val maxScoreText = detail.scoring.maxScore?.let {
        CatalogFormatting.formatBigDecimal(it, locale)
    } ?: CatalogFormatting.formatDecimalRange(detail.maxPoints, locale)

    DetailSectionCard(
        title = stringResource(R.string.storefront_detail_howTitle),
        modifier = Modifier.testTag(ProductDetailTestTags.HOW_SECTION),
    ) {
        if (mergedPartTypes.isNotEmpty()) {
            val summary = mergedPartTypes.map { merged ->
                stringResource(
                    R.string.storefront_detail_answerTypeCount,
                    answerTypeLabel(merged.type),
                    merged.count,
                )
            }.joinToString(" · ")
            DetailKeyValueRow(
                label = stringResource(R.string.storefront_detail_how_answerTypes),
                value = summary,
            )
        }

        if (maxScoreText != null) {
            DetailKeyValueRow(
                label = stringResource(R.string.storefront_detail_how_scoring),
                value = stringResource(R.string.storefront_detail_how_maxScore, maxScoreText),
            )
        }

        val durationLabel = CatalogFormatting.formatIntRange(detail.durationMinutes, locale)
            ?.let { stringResource(R.string.storefront_units_minutes, it) }
        val timingBehavior = detail.scoring.timing?.let { timing ->
            when (timing) {
                "PER_PART" -> stringResource(R.string.storefront_detail_how_timingPER_PART)
                "SHARED" -> stringResource(R.string.storefront_detail_how_timingSHARED)
                else -> timing
            }
        }
        val timingRowValue = listOfNotNull(durationLabel, timingBehavior).joinToString(" · ")
        if (timingRowValue.isNotEmpty()) {
            DetailKeyValueRow(
                label = stringResource(R.string.storefront_detail_how_timing),
                value = timingRowValue,
            )
        }

        DetailKeyValueRow(
            label = stringResource(R.string.storefront_detail_how_switching),
            value = stringResource(R.string.storefront_detail_how_switchingValue),
        )

        if (hasEssay) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = status.infoBg,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.storefront_detail_how_essayNote),
                    style = MaterialTheme.typography.bodySmall,
                    color = status.info,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        if (detail.omittedEssayGroups.isNotEmpty()) {
            val noEssayPrefix = stringResource(R.string.storefront_detail_how_noEssay)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                detail.omittedEssayGroups.forEach { group ->
                    val pointsSuffix = group.points?.let {
                        " · ${stringResource(R.string.storefront_detail_how_points, CatalogFormatting.formatBigDecimal(it, locale))}"
                    }.orEmpty()
                    Text(
                        text = "$noEssayPrefix ${group.name}$pointsSuffix",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExamRightsCard(
    detail: ProductDetailData,
    hasEssay: Boolean,
) {
    val colors = Thi360Theme.colors
    val yesLabel = stringResource(R.string.storefront_detail_rights_yes)
    val noLabel = stringResource(R.string.storefront_detail_rights_no)

    DetailSectionCard(
        title = stringResource(R.string.storefront_detail_rights_title),
        modifier = Modifier.testTag(ProductDetailTestTags.RIGHTS_SECTION),
    ) {
        DetailKeyValueRow(
            label = stringResource(R.string.storefront_detail_rights_attempts),
            value = pluralStringResource(
                R.plurals.storefront_detail_attemptsPerPurchase,
                detail.attemptsGranted,
                detail.attemptsGranted,
            ),
        )
        detail.resultView?.let { rv ->
            DetailKeyValueRow(
                label = stringResource(R.string.storefront_detail_rights_resultView),
                value = resultViewLabel(rv),
            )
        }
        DetailKeyValueRow(
            label = stringResource(R.string.storefront_detail_rights_resultTiming),
            value = stringResource(
                if (hasEssay) {
                    R.string.storefront_detail_rights_afterEssay
                } else {
                    R.string.storefront_detail_rights_afterGrading
                },
            ),
        )
        DetailKeyValueRow(
            label = stringResource(R.string.storefront_detail_rights_ai),
            value = if (detail.benefits.aiExplanation) yesLabel else noLabel,
        )
        DetailKeyValueRow(
            label = stringResource(R.string.storefront_detail_rights_strengths),
            value = if (detail.benefits.strengthWeakness) yesLabel else noLabel,
        )
        DetailKeyValueRow(
            label = stringResource(R.string.storefront_detail_rights_practice),
            value = if (detail.benefits.practice) yesLabel else noLabel,
        )
        detail.benefits.note?.let { note ->
            Text(
                text = "${stringResource(R.string.storefront_detail_rights_noteLabel)} $note",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
        }
    }
}

@Composable
internal fun PolicyCard(
    supportContact: SupportContact,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    DetailSectionCard(
        title = stringResource(R.string.storefront_detail_policy_title),
        modifier = modifier.testTag(ProductDetailTestTags.POLICY_SECTION),
    ) {
        Text(
            text = stringResource(R.string.storefront_detail_policy_priceNote),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        Text(
            text = stringResource(
                R.string.storefront_detail_policy_refund,
                stringResource(R.string.storefront_detail_policy_refundPending),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        SupportContactBlock(supportContact = supportContact)
    }
}

@Composable
internal fun DetailSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    badge: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = Thi360Theme.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.bgSurface,
        border = BorderStroke(1.dp, colors.border),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.text,
                    modifier = Modifier.semantics { heading() },
                )
                badge?.invoke()
            }
            content()
        }
    }
}

@Composable
internal fun DetailKeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
            modifier = Modifier.weight(0.45f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = colors.text,
            modifier = Modifier.weight(0.55f),
        )
    }
}
