package vn.unicom.thi360.feature.result.review

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import vn.unicom.thi360.R
import vn.unicom.thi360.core.network.generated.model.AttemptResultResponse
import vn.unicom.thi360.core.network.generated.model.AttemptReviewResponse
import vn.unicom.thi360.core.theme.LocalAppLocale
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.core.ui.LoadFailureKind
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.core.ui.LoadStateView
import vn.unicom.thi360.feature.question.model.ItemOutcome
import vn.unicom.thi360.feature.question.model.QuestionItem
import vn.unicom.thi360.feature.question.model.ReviewLevel
import vn.unicom.thi360.feature.question.ui.QuestionSetView
import vn.unicom.thi360.feature.question.ui.QuestionView
import vn.unicom.thi360.feature.result.ResultClickGuard
import vn.unicom.thi360.feature.result.ResultFormat
import vn.unicom.thi360.feature.result.ResultIcons
import vn.unicom.thi360.feature.result.ResultPill
import vn.unicom.thi360.feature.result.scopeDisplayLabel
import vn.unicom.thi360.feature.richcontent.assets.AttemptAssetSource
import vn.unicom.thi360.feature.richcontent.formula.FormulaEngine

private val REVIEW_TWO_PANE_BREAKPOINT_DP = 840.dp

@Composable
fun ReviewScreen(
    uiState: ReviewUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSelectPart: (Int) -> Unit,
    onSelectGroup: (Int) -> Unit,
    onPreviousGroup: () -> Int?,
    onNextGroup: () -> Int?,
    onGoToMyExams: () -> Unit,
    onGoToHistory: () -> Unit,
    modifier: Modifier = Modifier,
    assets: AttemptAssetSource? = null,
    formulaEngine: FormulaEngine? = null,
    clickGuard: ResultClickGuard = remember { ResultClickGuard() },
) {
    val colors = Thi360Theme.colors
    val subtitle = buildReviewSubtitle(uiState.headerResult)
    val serverErrorTitle = stringResource(R.string.review_errorTitle)
    val serverErrorDescription = stringResource(R.string.review_errorDescription)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgPage)
            .testTag(ReviewTestTags.SCREEN),
    ) {
        Box(modifier = Modifier.testTag(ReviewTestTags.screenFor(uiState.attemptId))) {
            ReviewTopBar(
                title = stringResource(R.string.review_pageTitle),
                subtitle = subtitle,
                backContentDescription = stringResource(R.string.review_backToResult),
                onBack = { clickGuard.tryRun(onBack) },
            )
        }

        when (val load = uiState.loadState) {
            LoadState.Loading -> {
                val loadingLabel = stringResource(R.string.review_loading)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { contentDescription = loadingLabel }
                        .testTag(ReviewTestTags.LOADING),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(color = colors.primaryFill)
                        Text(
                            text = loadingLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted,
                        )
                    }
                }
            }

            is LoadState.Failed -> {
                when {
                    load.kind is LoadFailureKind.Forbidden -> {
                        ReviewTerminalMessageView(
                            tag = ReviewTestTags.NOT_INCLUDED,
                            title = stringResource(R.string.review_notIncluded_title),
                            description = stringResource(R.string.review_notIncluded_description),
                            primaryLabel = stringResource(R.string.review_notIncluded_backToResult),
                            onPrimary = { clickGuard.tryRun(onBack) },
                        )
                    }

                    load.kind is LoadFailureKind.Conflict &&
                        load.kind.code == ReviewViewModel.REVIEW_NOT_AVAILABLE_CODE -> {
                        ReviewTerminalMessageView(
                            tag = ReviewTestTags.NOT_AVAILABLE,
                            title = stringResource(R.string.review_notAvailable_title),
                            description = stringResource(R.string.review_notAvailable_description),
                            primaryLabel = stringResource(R.string.review_notAvailable_backToResult),
                            onPrimary = { clickGuard.tryRun(onBack) },
                        )
                    }

                    load.kind is LoadFailureKind.NotFound -> {
                        ReviewNotFoundView(
                            onGoToMyExams = { clickGuard.tryRun(onGoToMyExams) },
                            onGoToHistory = { clickGuard.tryRun(onGoToHistory) },
                        )
                    }

                    else -> {
                        LoadStateView(
                            state = load,
                            onRetry = onRetry,
                            emptyContent = {},
                            customErrorTitle = { failed ->
                                if (failed.kind == LoadFailureKind.Offline) null else serverErrorTitle
                            },
                            customErrorDescription = { failed ->
                                if (failed.kind == LoadFailureKind.Offline) null else serverErrorDescription
                            },
                            content = {},
                        )
                    }
                }
            }

            LoadState.Empty -> Unit

            is LoadState.Loaded -> {
                val model = load.value
                ReviewLoadedBody(
                    model = model,
                    selectedPartIndex = uiState.selectedPartIndex,
                    activeGroupIndex = uiState.activeGroupIndex,
                    onSelectPart = onSelectPart,
                    onSelectGroup = onSelectGroup,
                    onPreviousGroup = onPreviousGroup,
                    onNextGroup = onNextGroup,
                    assets = assets,
                    formulaEngine = formulaEngine,
                )
            }
        }
    }
}

@Composable
private fun ReviewTopBar(
    title: String,
    subtitle: String?,
    backContentDescription: String,
    onBack: () -> Unit,
) {
    val colors = Thi360Theme.colors
    Surface(
        color = colors.bgSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag(ReviewTestTags.BACK_BUTTON),
                ) {
                    Icon(
                        imageVector = ResultIcons.ArrowBack,
                        contentDescription = backContentDescription,
                        tint = colors.text,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.text,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag(ReviewTestTags.HEADER_SUBTITLE),
                        )
                    }
                }
            }
            HorizontalDivider(color = colors.border)
        }
    }
}

@Composable
private fun ReviewLoadedBody(
    model: ReviewModel,
    selectedPartIndex: Int,
    activeGroupIndex: Int,
    onSelectPart: (Int) -> Unit,
    onSelectGroup: (Int) -> Unit,
    onPreviousGroup: () -> Int?,
    onNextGroup: () -> Int?,
    assets: AttemptAssetSource?,
    formulaEngine: FormulaEngine?,
) {
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors
    val selectedPart = model.parts.getOrNull(selectedPartIndex) ?: model.parts.firstOrNull()
    val questionListState = rememberLazyListState()
    val paletteRowState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(selectedPartIndex) {
        questionListState.scrollToItem(0)
        paletteRowState.scrollToItem(0)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (model.level == ReviewLevel.SCORE_AND_ANSWERS) {
            ReviewBanner(
                text = stringResource(R.string.review_scoreOnlyNotice),
                containerColor = statusColors.infoBg,
                contentColor = statusColors.info,
                icon = ResultIcons.Info,
                tag = ReviewTestTags.SCORE_ONLY_BANNER,
            )
        }
        if (model.state == AttemptReviewResponse.State.PENDING_REVIEW) {
            ReviewBanner(
                text = stringResource(R.string.review_pendingBanner),
                containerColor = statusColors.warningBg,
                contentColor = statusColors.warning,
                icon = ResultIcons.Warning,
                tag = ReviewTestTags.PENDING_BANNER,
            )
        }

        if (model.parts.size > 1) {
            ReviewPartTabs(
                parts = model.parts,
                selectedIndex = selectedPartIndex,
                onSelectPart = onSelectPart,
            )
        }

        if (selectedPart == null) {
            ReviewEmptyPartCard()
            return
        }

        if (selectedPart.isSkipped) {
            ReviewSkippedPartCard(part = selectedPart)
            return
        }

        if (selectedPart.groups.isEmpty()) {
            ReviewEmptyPartCard()
            return
        }

        val jumpToGroup: (Int) -> Unit = { targetIndex ->
            onSelectGroup(targetIndex)
            coroutineScope.launch {
                questionListState.animateScrollToItem(targetIndex)
                paletteRowState.animateScrollToItem(targetIndex)
            }
        }
        val handlePrev: () -> Unit = {
            val target = onPreviousGroup()
            if (target != null) {
                coroutineScope.launch {
                    questionListState.animateScrollToItem(target)
                    paletteRowState.animateScrollToItem(target)
                }
            }
        }
        val handleNext: () -> Unit = {
            val target = onNextGroup()
            if (target != null) {
                coroutineScope.launch {
                    questionListState.animateScrollToItem(target)
                    paletteRowState.animateScrollToItem(target)
                }
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            val isWide = maxWidth >= REVIEW_TWO_PANE_BREAKPOINT_DP
            if (isWide) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    ReviewPaletteSidePane(
                        part = selectedPart,
                        activeGroupIndex = activeGroupIndex,
                        onSelectGroup = jumpToGroup,
                        onPrevious = handlePrev,
                        onNext = handleNext,
                        modifier = Modifier
                            .width(300.dp)
                            .fillMaxHeight(),
                    )
                    ReviewQuestionList(
                        groups = selectedPart.groups,
                        activeGroupIndex = activeGroupIndex,
                        listState = questionListState,
                        assets = assets,
                        formulaEngine = formulaEngine,
                        contentPadding = PaddingValues(bottom = 24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    ReviewPaletteStrip(
                        part = selectedPart,
                        activeGroupIndex = activeGroupIndex,
                        paletteRowState = paletteRowState,
                        onSelectGroup = jumpToGroup,
                        onPrevious = handlePrev,
                        onNext = handleNext,
                    )
                    HorizontalDivider(color = colors.border)
                    ReviewQuestionList(
                        groups = selectedPart.groups,
                        activeGroupIndex = activeGroupIndex,
                        listState = questionListState,
                        assets = assets,
                        formulaEngine = formulaEngine,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewBanner(
    text: String,
    containerColor: Color,
    contentColor: Color,
    icon: ImageVector,
    tag: String,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag(tag),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = contentColor,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ReviewPartTabs(
    parts: List<ReviewPart>,
    selectedIndex: Int,
    onSelectPart: (Int) -> Unit,
) {
    val colors = Thi360Theme.colors
    val partsLabel = stringResource(R.string.review_nav_partsLabel)

    Surface(
        color = colors.bgSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = partsLabel }
                    .testTag(ReviewTestTags.PARTS_BAR),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(parts, key = { _, part -> part.key }) { index, part ->
                    val isSelected = index == selectedIndex
                    val label = part.partName?.takeIf { it.isNotBlank() }
                        ?: part.partCode?.takeIf { it.isNotBlank() }
                        ?: part.key

                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (isSelected) colors.primaryContainer else colors.bgSubtle,
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) colors.primaryFill else colors.border,
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .semantics { selected = isSelected }
                            .clickable(role = Role.Tab) { onSelectPart(index) }
                            .testTag(ReviewTestTags.partTab(index)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) colors.primaryText else colors.text,
                            )
                            if (part.isSkipped) {
                                ResultPill(
                                    text = stringResource(R.string.review_nav_skippedPartBadge),
                                    containerColor = colors.bgSurface,
                                    contentColor = colors.textMuted,
                                    mergeSemantics = false,
                                )
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = colors.border)
        }
    }
}

@Composable
private fun ReviewPaletteStrip(
    part: ReviewPart,
    activeGroupIndex: Int,
    paletteRowState: LazyListState,
    onSelectGroup: (Int) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val colors = Thi360Theme.colors
    val prevEnabled = activeGroupIndex > 0
    val nextEnabled = activeGroupIndex < part.groups.lastIndex

    Surface(
        color = colors.bgSurface,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ReviewTestTags.PALETTE_STRIP),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.review_nav_paletteTitle),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.text,
                )
            }
            Text(
                text = stringResource(
                    R.string.review_nav_paletteSummary,
                    part.summary.correct,
                    part.summary.partial,
                    part.summary.wrong,
                    part.summary.blank,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                modifier = Modifier.testTag(ReviewTestTags.PALETTE_SUMMARY),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IconButton(
                    onClick = onPrevious,
                    enabled = prevEnabled,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag(ReviewTestTags.PREV_BUTTON),
                ) {
                    Icon(
                        imageVector = ResultIcons.ChevronLeft,
                        contentDescription = stringResource(R.string.review_nav_prev),
                        tint = if (prevEnabled) colors.text else colors.borderStrong,
                    )
                }

                LazyRow(
                    state = paletteRowState,
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(part.groups, key = { _, g -> g.id }) { index, group ->
                        PaletteCell(
                            group = group,
                            index = index,
                            isActive = index == activeGroupIndex,
                            onClick = { onSelectGroup(index) },
                        )
                    }
                }

                IconButton(
                    onClick = onNext,
                    enabled = nextEnabled,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag(ReviewTestTags.NEXT_BUTTON),
                ) {
                    Icon(
                        imageVector = ResultIcons.ChevronRight,
                        contentDescription = stringResource(R.string.review_nav_next),
                        tint = if (nextEnabled) colors.text else colors.borderStrong,
                    )
                }
            }
            ReviewPaletteLegend()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewPaletteSidePane(
    part: ReviewPart,
    activeGroupIndex: Int,
    onSelectGroup: (Int) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    val prevEnabled = activeGroupIndex > 0
    val nextEnabled = activeGroupIndex < part.groups.lastIndex

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.bgSurface,
        border = BorderStroke(1.dp, colors.border),
        modifier = modifier.testTag(ReviewTestTags.PALETTE_SIDE_PANE),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.review_nav_paletteTitle),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.text,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(
                    R.string.review_nav_paletteSummary,
                    part.summary.correct,
                    part.summary.partial,
                    part.summary.wrong,
                    part.summary.blank,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                modifier = Modifier.testTag(ReviewTestTags.PALETTE_SUMMARY),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                part.groups.forEachIndexed { index, group ->
                    PaletteCell(
                        group = group,
                        index = index,
                        isActive = index == activeGroupIndex,
                        onClick = { onSelectGroup(index) },
                    )
                }
            }
            HorizontalDivider(color = colors.border)
            ReviewPaletteLegend()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onPrevious,
                    enabled = prevEnabled,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag(ReviewTestTags.PREV_BUTTON),
                ) {
                    Text(
                        text = stringResource(R.string.review_nav_prev),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedButton(
                    onClick = onNext,
                    enabled = nextEnabled,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag(ReviewTestTags.NEXT_BUTTON),
                ) {
                    Text(
                        text = stringResource(R.string.review_nav_next),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun PaletteCell(
    group: ReviewGroup,
    index: Int,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors
    val displayText = if (group.rangeFrom != null && group.rangeTo != null) {
        "${group.rangeFrom}–${group.rangeTo}"
    } else {
        "${group.displayNo}"
    }
    val questionAriaLabel = if (group.rangeFrom != null && group.rangeTo != null) {
        stringResource(R.string.review_questionRangeLabel, group.rangeFrom, group.rangeTo)
    } else {
        stringResource(R.string.review_questionLabel, group.displayNo)
    }
    val outcomeAriaLabel = outcomeDisplayLabel(group.outcome)
    val cellAria = stringResource(R.string.review_nav_paletteItemAria, questionAriaLabel, outcomeAriaLabel)
    val currentLabel = stringResource(R.string.review_nav_currentItem)

    val (bgColor, fgColor, borderColor, borderWidthDp, dashed, icon) = when (group.outcome) {
        ItemOutcome.CORRECT -> PaletteCellStyle(
            bg = statusColors.success,
            fg = Color.White,
            border = statusColors.success,
            borderWidth = 1.dp,
            dashed = false,
            icon = ResultIcons.Check,
        )
        ItemOutcome.PARTIAL -> PaletteCellStyle(
            bg = statusColors.warningBg,
            fg = statusColors.warning,
            border = statusColors.warning,
            borderWidth = 2.dp,
            dashed = false,
            icon = ResultIcons.Partial,
        )
        ItemOutcome.WRONG -> PaletteCellStyle(
            bg = colors.dangerBg,
            fg = colors.dangerText,
            border = colors.dangerText,
            borderWidth = 2.dp,
            dashed = true,
            icon = ResultIcons.Close,
        )
        ItemOutcome.BLANK,
        ItemOutcome.PENDING_REVIEW,
        -> PaletteCellStyle(
            bg = colors.bgSubtle,
            fg = colors.textSecondary,
            border = colors.borderStrong,
            borderWidth = 1.dp,
            dashed = true,
            icon = ResultIcons.Dash,
        )
    }

    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .clip(shape)
            .background(bgColor)
            .drawBehind {
                val strokePx = borderWidthDp.toPx()
                val radiusPx = 10.dp.toPx()
                val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f) else null
                drawRoundRect(
                    color = borderColor,
                    cornerRadius = CornerRadius(radiusPx, radiusPx),
                    style = Stroke(width = strokePx, pathEffect = effect),
                )
                if (isActive) {
                    drawRoundRect(
                        color = colors.primaryFill,
                        cornerRadius = CornerRadius(radiusPx, radiusPx),
                        style = Stroke(width = 3.dp.toPx()),
                    )
                }
            }
            .semantics(mergeDescendants = true) {
                contentDescription = cellAria
                selected = isActive
                if (isActive) {
                    stateDescription = currentLabel
                }
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(ReviewTestTags.paletteCell(index)),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fgColor,
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = displayText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = fgColor,
                maxLines = 1,
            )
        }
    }
}

private data class PaletteCellStyle(
    val bg: Color,
    val fg: Color,
    val border: Color,
    val borderWidth: androidx.compose.ui.unit.Dp,
    val dashed: Boolean,
    val icon: ImageVector,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewPaletteLegend() {
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors
    val legendLabel = stringResource(R.string.review_nav_paletteLegend)

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = legendLabel }
            .testTag(ReviewTestTags.PALETTE_LEGEND),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LegendItem(
            icon = ResultIcons.Check,
            tint = statusColors.success,
            label = stringResource(R.string.result_stats_correct),
        )
        LegendItem(
            icon = ResultIcons.Partial,
            tint = statusColors.warning,
            label = stringResource(R.string.result_stats_partial),
        )
        LegendItem(
            icon = ResultIcons.Close,
            tint = colors.dangerText,
            label = stringResource(R.string.result_stats_wrong),
        )
        LegendItem(
            icon = ResultIcons.Dash,
            tint = colors.textMuted,
            label = stringResource(R.string.result_stats_blank),
        )
    }
}

@Composable
private fun LegendItem(
    icon: ImageVector,
    tint: Color,
    label: String,
) {
    val colors = Thi360Theme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun ReviewQuestionList(
    groups: List<ReviewGroup>,
    activeGroupIndex: Int,
    listState: LazyListState,
    assets: AttemptAssetSource?,
    formulaEngine: FormulaEngine?,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    val currentLabel = stringResource(R.string.review_nav_currentItem)

    LazyColumn(
        state = listState,
        modifier = modifier.testTag(ReviewTestTags.QUESTION_LIST),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        itemsIndexed(groups, key = { _, group -> group.id }) { index, group ->
            val isActive = index == activeGroupIndex
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.bgSurface,
                border = BorderStroke(
                    width = if (isActive) 2.dp else 1.dp,
                    color = if (isActive) colors.primaryFill else colors.border,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        if (isActive) {
                            stateDescription = currentLabel
                        }
                    }
                    .testTag(ReviewTestTags.questionCard(index)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    val item = group.item
                    if (item is QuestionItem.QuestionSet) {
                        QuestionSetView(
                            item = item,
                            answers = group.childAnswers,
                            mode = group.mode,
                            assets = assets,
                            formulaEngine = formulaEngine,
                        )
                    } else {
                        QuestionView(
                            item = item,
                            answer = group.studentAnswer,
                            mode = group.mode,
                            assets = assets,
                            formulaEngine = formulaEngine,
                        )
                    }

                    group.essayCriteriaBlocks.forEach { block ->
                        EssayCriteriaSection(block = block)
                    }
                }
            }
        }
    }
}

@Composable
private fun EssayCriteriaSection(
    block: EssayCriteriaBlock,
) {
    val colors = Thi360Theme.colors
    val locale = LocalAppLocale.current
    val baseHeading = stringResource(R.string.review_essayCriteria_title)
    val heading = if (block.childDisplayNo != null) {
        "$baseHeading · ${stringResource(R.string.review_questionLabel, block.childDisplayNo)}"
    } else {
        baseHeading
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ReviewTestTags.essayCriteriaBlock(block.id)),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider(color = colors.border)
        Text(
            text = heading,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.text,
            modifier = Modifier.semantics { heading() },
        )

        block.criteria.forEachIndexed { idx, row ->
            val criterionName = row.id
                ?: stringResource(R.string.review_essayCriteria_criterionFallback, idx + 1)
            val pointsText = row.points?.let { pts ->
                stringResource(
                    R.string.review_essayCriteria_pointsValue,
                    ResultFormat.formatScore(pts, locale),
                )
            } ?: "—"
            val commentText = row.comment ?: "—"

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = colors.bgSubtle,
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CriteriaFieldRow(
                        label = stringResource(R.string.review_essayCriteria_criterionCol),
                        value = criterionName,
                        boldValue = true,
                    )
                    CriteriaFieldRow(
                        label = stringResource(R.string.review_essayCriteria_pointsCol),
                        value = pointsText,
                        boldValue = true,
                    )
                    CriteriaFieldRow(
                        label = stringResource(R.string.review_essayCriteria_commentCol),
                        value = commentText,
                        boldValue = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun CriteriaFieldRow(
    label: String,
    value: String,
    boldValue: Boolean,
) {
    val colors = Thi360Theme.colors
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textMuted,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (boldValue) FontWeight.SemiBold else FontWeight.Normal,
            color = colors.text,
        )
    }
}

@Composable
private fun ReviewSkippedPartCard(part: ReviewPart) {
    val colors = Thi360Theme.colors
    val locale = LocalAppLocale.current
    val description = if (part.score != null && part.maxScore != null) {
        stringResource(
            R.string.review_skippedPart_description,
            ResultFormat.formatScore(part.score, locale),
            ResultFormat.formatScore(part.maxScore, locale),
        )
    } else {
        stringResource(R.string.review_skippedPart_descriptionNoScore)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag(ReviewTestTags.SKIPPED_PART_CARD),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.bgSurface,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.widthIn(max = 460.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.review_skippedPart_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.text,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ReviewEmptyPartCard() {
    val colors = Thi360Theme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag(ReviewTestTags.EMPTY_PART_CARD),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.bgSurface,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.widthIn(max = 460.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.review_emptyPart_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.text,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.review_emptyPart_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ReviewTerminalMessageView(
    tag: String,
    title: String,
    description: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
) {
    val colors = Thi360Theme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onPrimary,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primaryFill,
                    contentColor = colors.onPrimary,
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(ReviewTestTags.BACK_TO_RESULT_BUTTON),
            ) {
                Text(text = primaryLabel)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewNotFoundView(
    onGoToMyExams: () -> Unit,
    onGoToHistory: () -> Unit,
) {
    val colors = Thi360Theme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag(ReviewTestTags.NOT_FOUND),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.review_notFound_eyebrow),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = colors.dangerText,
            )
            Text(
                text = stringResource(R.string.review_notFound_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.review_notFound_description),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onGoToMyExams,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primaryFill,
                        contentColor = colors.onPrimary,
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(ReviewTestTags.MY_EXAMS_BUTTON),
                ) {
                    Text(text = stringResource(R.string.review_notFound_toHome))
                }
                OutlinedButton(
                    onClick = onGoToHistory,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(ReviewTestTags.HISTORY_BUTTON),
                ) {
                    Text(text = stringResource(R.string.review_notFound_toHistory))
                }
            }
        }
    }
}

@Composable
private fun buildReviewSubtitle(headerResult: AttemptResultResponse?): String? {
    if (headerResult == null) return null
    val examName = headerResult.examName?.takeIf { it.isNotBlank() } ?: return null
    val scopeLabel = scopeDisplayLabel(headerResult.scope)
    val attemptNo = headerResult.attemptNo ?: 1
    val locale = LocalAppLocale.current
    val resolvedScore = ResultFormat.resolveDisplayScore(headerResult)

    return when {
        resolvedScore != null && resolvedScore.max != null -> stringResource(
            R.string.review_subtitleWithScore,
            examName,
            scopeLabel,
            attemptNo,
            ResultFormat.formatScore(resolvedScore.score, locale),
            ResultFormat.formatScore(resolvedScore.max, locale),
        )
        resolvedScore != null -> {
            val base = stringResource(R.string.review_subtitle, examName, scopeLabel, attemptNo)
            "$base · ${stringResource(R.string.result_total_title)}: ${ResultFormat.formatScore(resolvedScore.score, locale)}"
        }
        else -> stringResource(R.string.review_subtitle, examName, scopeLabel, attemptNo)
    }
}

@Composable
private fun outcomeDisplayLabel(outcome: ItemOutcome): String = when (outcome) {
    ItemOutcome.CORRECT -> stringResource(R.string.review_outcomes_CORRECT)
    ItemOutcome.PARTIAL -> stringResource(R.string.review_outcomes_PARTIAL)
    ItemOutcome.WRONG -> stringResource(R.string.review_outcomes_WRONG)
    ItemOutcome.BLANK -> stringResource(R.string.review_outcomes_BLANK)
    ItemOutcome.PENDING_REVIEW -> stringResource(R.string.review_outcomes_PENDING_REVIEW)
}
