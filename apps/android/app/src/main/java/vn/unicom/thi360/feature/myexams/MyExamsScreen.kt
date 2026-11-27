package vn.unicom.thi360.feature.myexams

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.UUID
import kotlinx.coroutines.launch
import vn.unicom.thi360.R
import vn.unicom.thi360.core.brand.BrandMark
import vn.unicom.thi360.core.config.SupportContact
import vn.unicom.thi360.core.network.generated.model.MyExamCard
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.theme.LocalAppLocale
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.core.ui.LoadFailureKind
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.core.ui.LocalShellWidth
import vn.unicom.thi360.core.ui.RefreshableContainer
import vn.unicom.thi360.core.ui.ShellWidth
import vn.unicom.thi360.core.ui.TwoColumnLayout
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MyExamsScreen(
    feedState: MyExamsFeedState,
    greetingName: String,
    supportContact: SupportContact,
    showCompactBrandHeader: Boolean,
    onPullToRefresh: () -> Unit,
    onRetry: () -> Unit,
    onExploreExams: () -> Unit,
    onOpenOrder: (String) -> Unit,
    onOpenProduct: (String) -> Unit,
    onContinueAttempt: (String) -> Unit,
    onStartPrepare: (String) -> Unit,
    onOpenResult: (String) -> Unit,
    onOpenHistoryReset: () -> Unit,
    onOpenCheckout: (String) -> Unit,
    modifier: Modifier = Modifier,
    clickGuard: NavigationClickGuard = remember { NavigationClickGuard() },
) {
    val colors = Thi360Theme.colors
    val shellWidth = LocalShellWidth.current
    val locale = LocalAppLocale.current
    val horizontalPadding = when (shellWidth) {
        ShellWidth.Narrow -> 16.dp
        ShellWidth.Wide -> 24.dp
        ShellWidth.TwoColumn -> 28.dp
    }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val view = LocalView.current

    val grouped = remember(feedState.loadState) {
        when (val load = feedState.loadState) {
            is LoadState.Loaded -> MyExamsLayout.group(load.value)
            else -> null
        }
    }

    val bundleFocusRequesters = remember(grouped) {
        val map = LinkedHashMap<UUID, FocusRequester>()
        grouped?.bundleGroups?.forEach { group ->
            map[group.ref.bundleId] = FocusRequester()
        }
        map
    }

    val bundleItemIndexMap = remember(grouped, feedState.loadState) {
        val map = LinkedHashMap<UUID, Int>()
        if (grouped != null && grouped.count > 0) {
            val isStale = (feedState.loadState as? LoadState.Loaded)?.stale == true
            var index = 1
            if (isStale) index += 1
            index += 1
            if (grouped.checking.isNotEmpty()) {
                index += 1 + grouped.checking.size
            }
            grouped.bundleGroups.forEach { group ->
                map[group.ref.bundleId] = index
                index += 1
            }
        }
        map
    }

    val onJumpToBundle: (UUID, String) -> Unit = { bundleId, bundleName ->
        val targetIndex = bundleItemIndexMap[bundleId]
        val requester = bundleFocusRequesters[bundleId]
        if (targetIndex != null) {
            coroutineScope.launch {
                listState.animateScrollToItem(targetIndex)
                withFrameNanos { }
                requester?.let { runCatching { it.requestFocus() } }
                @Suppress("DEPRECATION")
                runCatching { view.announceForAccessibility(bundleName) }
            }
        }
    }

    RefreshableContainer(
        isRefreshing = feedState.isPullRefreshing,
        onRefresh = onPullToRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .testTag(MyExamsTestTags.LIST),
        ) {
            item(key = "my_exams_header") {
                MyExamsHeader(
                    greetingName = greetingName,
                    showCompactBrandHeader = showCompactBrandHeader,
                )
            }

            when (val loadState = feedState.loadState) {
                LoadState.Loading -> {
                    item(key = "my_exams_skeleton") {
                        MyExamsLoadingSkeleton(shellWidth = shellWidth)
                    }
                }

                is LoadState.Failed -> {
                    item(key = "my_exams_failed") {
                        MyExamsFailedCard(
                            failed = loadState,
                            onRetry = onRetry,
                        )
                    }
                }

                LoadState.Empty -> {
                    item(key = "my_exams_empty") {
                        MyExamsEmptyCard(
                            onExplore = { clickGuard.run(onExploreExams) },
                        )
                    }
                }

                is LoadState.Loaded -> {
                    if (loadState.stale) {
                        item(key = "my_exams_stale") {
                            MyExamsStaleBanner(
                                lastLoadedAtMillis = feedState.lastLoadedAtMillis,
                                isRefreshing = feedState.isRefreshing,
                                onRetry = onRetry,
                            )
                        }
                    }

                    val data = grouped ?: MyExamsLayout.group(loadState.value)
                    if (data.count == 0) {
                        item(key = "my_exams_empty_stale") {
                            MyExamsEmptyCard(
                                onExplore = { clickGuard.run(onExploreExams) },
                            )
                        }
                    } else {
                        item(key = "my_exams_section_header") {
                            MyExamsSectionHeader(
                                totalCount = data.count,
                                onExplore = { clickGuard.run(onExploreExams) },
                            )
                        }

                        if (data.checking.isNotEmpty()) {
                            item(key = "my_exams_checking_heading") {
                                Text(
                                    text = stringResource(R.string.home_checking_sectionTitle).uppercase(),
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = Thi360Theme.statusColors.warning,
                                    modifier = Modifier
                                        .semantics { heading() }
                                        .testTag(MyExamsTestTags.CHECKING_SECTION),
                                )
                            }
                            items(
                                items = data.checking,
                                key = { card -> myExamStableKey(card) },
                            ) { card ->
                                CheckingExamCardItem(
                                    card = card,
                                    supportContact = supportContact,
                                    locale = locale,
                                    isReloading = feedState.isRefreshing,
                                    onReload = onRetry,
                                    onOpenOrder = { orderCode -> clickGuard.run { onOpenOrder(orderCode) } },
                                )
                            }
                        }

                        items(
                            items = data.bundleGroups,
                            key = { group -> "bundle_group_${group.ref.bundleId}" },
                        ) { group ->
                            val focusRequester = bundleFocusRequesters[group.ref.bundleId]
                                ?: remember(group.ref.bundleId) { FocusRequester() }
                            BundleGroupSection(
                                group = group,
                                shellWidth = shellWidth,
                                locale = locale,
                                headingFocusRequester = focusRequester,
                                onViewBundle = { code -> clickGuard.run { onOpenProduct(code) } },
                                onCrossRefClick = onJumpToBundle,
                                onContinueAttempt = { id -> clickGuard.run { onContinueAttempt(id) } },
                                onStartPrepare = { id -> clickGuard.run { onStartPrepare(id) } },
                                onOpenResult = { id -> clickGuard.run { onOpenResult(id) } },
                                onOpenHistoryReset = { clickGuard.run(onOpenHistoryReset) },
                                onOpenCheckout = { code -> clickGuard.run { onOpenCheckout(code) } },
                            )
                        }

                        if (data.standalone.isNotEmpty()) {
                            if (data.bundleGroups.isNotEmpty()) {
                                item(key = "my_exams_standalone_header") {
                                    StandaloneSectionHeader(count = data.standalone.size)
                                }
                            }
                            val cardRows = chunkCardsForWidth(data.standalone, shellWidth)
                            items(
                                items = cardRows,
                                key = { row ->
                                    row.joinToString("|") { myExamStableKey(it) }
                                },
                            ) { rowCards ->
                                ResponsiveExamCardRow(
                                    cards = rowCards,
                                    shellWidth = shellWidth,
                                    locale = locale,
                                    onContinueAttempt = { id -> clickGuard.run { onContinueAttempt(id) } },
                                    onStartPrepare = { id -> clickGuard.run { onStartPrepare(id) } },
                                    onOpenResult = { id -> clickGuard.run { onOpenResult(id) } },
                                    onOpenHistoryReset = { clickGuard.run(onOpenHistoryReset) },
                                    onOpenCheckout = { code -> clickGuard.run { onOpenCheckout(code) } },
                                )
                            }
                        }
                    }
                }
            }

            item(key = "my_exams_bottom_spacer") {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun MyExamsHeader(
    greetingName: String,
    showCompactBrandHeader: Boolean,
) {
    val colors = Thi360Theme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showCompactBrandHeader) {
            BrandMark(markSize = 32.dp)
        }
        Text(
            text = stringResource(R.string.home_pageTitle),
            style = if (showCompactBrandHeader) {
                MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
            } else {
                MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            },
            color = colors.text,
            modifier = Modifier
                .semantics { heading() }
                .testTag(ShellTestTags.screenTitle(Destination.MyExams)),
        )
        if (greetingName.isNotBlank()) {
            Text(
                text = stringResource(R.string.home_subtitle, greetingName),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
                modifier = Modifier.testTag(MyExamsTestTags.SUBTITLE),
            )
        }
    }
}

@Composable
private fun MyExamsLoadingSkeleton(shellWidth: ShellWidth) {
    val colors = Thi360Theme.colors
    val loadingDesc = stringResource(R.string.common_loading)
    val indices = listOf(0, 1, 2)

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = loadingDesc }
            .testTag(MyExamsTestTags.LOADING_SKELETON),
    ) {
        val rows = if (shellWidth == ShellWidth.Narrow) indices.chunked(1) else indices.chunked(2)
        for (row in rows) {
            if (shellWidth == ShellWidth.TwoColumn) {
                TwoColumnLayout(
                    start = { SkeletonExamCard() },
                    end = {
                        if (row.size > 1) SkeletonExamCard() else Spacer(Modifier.fillMaxWidth())
                    },
                )
            } else if (shellWidth == ShellWidth.Wide) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(modifier = Modifier.weight(1f)) { SkeletonExamCard() }
                    Box(modifier = Modifier.weight(1f)) {
                        if (row.size > 1) SkeletonExamCard() else Spacer(Modifier.fillMaxWidth())
                    }
                }
            } else {
                SkeletonExamCard()
            }
        }
    }
}

@Composable
private fun SkeletonExamCard() {
    val colors = Thi360Theme.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.bgSurface,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBox(width = 56.dp, height = 22.dp, radius = 11.dp)
                SkeletonBox(width = 72.dp, height = 22.dp, radius = 11.dp)
            }
            SkeletonBox(width = 200.dp, height = 20.dp, radius = 6.dp)
            SkeletonBox(width = 140.dp, height = 16.dp, radius = 6.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.bgSubtle),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.bgSubtle),
            )
        }
    }
}

@Composable
private fun SkeletonBox(
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    radius: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(radius))
            .background(Thi360Theme.colors.bgSubtle),
    )
}

@Composable
private fun MyExamsFailedCard(
    failed: LoadState.Failed,
    onRetry: () -> Unit,
) {
    val colors = Thi360Theme.colors
    val isOffline = failed.kind == LoadFailureKind.Offline
    val title = if (isOffline) {
        stringResource(R.string.common_offline_title)
    } else {
        stringResource(R.string.home_errorTitle)
    }
    val description = if (isOffline) {
        stringResource(R.string.common_offline_description)
    } else {
        stringResource(R.string.home_errorDescription)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.bgSurface,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(if (isOffline) MyExamsTestTags.OFFLINE_STATE else MyExamsTestTags.ERROR_STATE),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isOffline) colors.bgSubtle else colors.dangerBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MyExamsIcons.AlertTriangle,
                    contentDescription = null,
                    tint = if (isOffline) colors.textMuted else colors.dangerText,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 360.dp),
            )
            OutlinedButton(
                onClick = onRetry,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, colors.borderStrong),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(
                        if (isOffline) {
                            MyExamsTestTags.OFFLINE_RETRY_BUTTON
                        } else {
                            MyExamsTestTags.ERROR_RETRY_BUTTON
                        },
                    ),
            ) {
                Text(
                    text = stringResource(R.string.common_retry),
                    color = colors.text,
                )
            }
        }
    }
}

@Composable
private fun MyExamsEmptyCard(onExplore: () -> Unit) {
    val colors = Thi360Theme.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.bgSurface,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MyExamsTestTags.EMPTY_STATE),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_empty_grid),
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = stringResource(R.string.home_emptyTitle),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.home_emptyDescription),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 360.dp),
            )
            Button(
                onClick = onExplore,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primaryFill,
                    contentColor = colors.onPrimary,
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(MyExamsTestTags.EMPTY_EXPLORE_BUTTON),
            ) {
                Text(text = stringResource(R.string.home_exploreExams))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MyExamsStaleBanner(
    lastLoadedAtMillis: Long?,
    isRefreshing: Boolean,
    onRetry: () -> Unit,
) {
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors
    val formattedTime = formatMyExamsStaleTime(lastLoadedAtMillis)
    val description = if (formattedTime != null) {
        stringResource(R.string.home_staleDescription, formattedTime)
    } else {
        stringResource(R.string.home_staleDescriptionNoTime)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = statusColors.warningBg,
        border = BorderStroke(1.dp, statusColors.warning),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag(MyExamsTestTags.STALE_BANNER),
    ) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
                modifier = Modifier.weight(1f, fill = true),
            ) {
                Icon(
                    imageVector = MyExamsIcons.AlertTriangle,
                    contentDescription = null,
                    tint = statusColors.warning,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(18.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.home_staleTitle),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = statusColors.warning,
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
            }
            OutlinedButton(
                onClick = onRetry,
                enabled = !isRefreshing,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, statusColors.warning.copy(alpha = 0.5f)),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(MyExamsTestTags.STALE_RETRY_BUTTON),
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = statusColors.warning,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = stringResource(R.string.common_retry),
                    color = statusColors.warning,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MyExamsSectionHeader(
    totalCount: Int,
    onExplore: () -> Unit,
) {
    val colors = Thi360Theme.colors
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MyExamsTestTags.SECTION_HEADER),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = true),
        ) {
            Text(
                text = stringResource(R.string.home_sectionTitle),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = colors.text,
                modifier = Modifier.semantics { heading() },
            )
            ChipPill(
                text = pluralStringResource(R.plurals.home_examCount, totalCount, totalCount),
                containerColor = colors.bgSubtle,
                contentColor = colors.textSecondary,
                modifier = Modifier.testTag(MyExamsTestTags.SECTION_COUNT),
            )
        }
        OutlinedButton(
            onClick = onExplore,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, colors.borderStrong),
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag(MyExamsTestTags.EXPLORE_BUTTON),
        ) {
            Icon(
                imageVector = MyExamsIcons.Compass,
                contentDescription = null,
                tint = colors.text,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.home_exploreExams),
                color = colors.text,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StandaloneSectionHeader(count: Int) {
    val colors = Thi360Theme.colors
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MyExamsTestTags.STANDALONE_SECTION_HEADER),
    ) {
        Text(
            text = stringResource(R.string.bundle_home_standaloneHeading),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = colors.text,
            modifier = Modifier.semantics { heading() },
        )
        ChipPill(
            text = pluralStringResource(R.plurals.home_examCount, count, count),
            containerColor = colors.bgSubtle,
            contentColor = colors.textSecondary,
            modifier = Modifier.testTag(MyExamsTestTags.STANDALONE_SECTION_COUNT),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CheckingExamCardItem(
    card: MyExamCard,
    supportContact: SupportContact,
    locale: AppLocale,
    isReloading: Boolean,
    onReload: () -> Unit,
    onOpenOrder: (String) -> Unit,
) {
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors
    val examKindName = pickExamKindName(card.examKind, locale)
    val checkingMessage = CheckingMessage.of(card, supportContact)
    val pendingText = stringResource(R.string.landing_support_pending)
    val messageText = when (checkingMessage) {
        is CheckingMessage.WithOrderAndContact -> stringResource(
            R.string.home_checking_message,
            checkingMessage.orderCode,
            checkingMessage.contact,
        )
        is CheckingMessage.WithOrderNoContact -> stringResource(
            R.string.home_checking_messageNoContact,
            checkingMessage.orderCode,
            pendingText,
        )
        is CheckingMessage.NoOrderWithContact -> stringResource(
            R.string.home_checking_messageNoOrder,
            checkingMessage.contact,
        )
        CheckingMessage.NoOrderNoContact -> stringResource(
            R.string.home_checking_messageNoOrderNoContact,
            pendingText,
        )
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.bgSurface,
        border = BorderStroke(1.dp, statusColors.warning),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MyExamsTestTags.checkingCard(card.code)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRect(
                        color = statusColors.warning,
                        topLeft = Offset.Zero,
                        size = Size(width = 4.dp.toPx(), height = size.height),
                    )
                }
                .padding(start = 20.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ChipPill(
                    text = examKindName,
                    containerColor = colors.bgSubtle,
                    contentColor = colors.textSecondary,
                )
                ChipPill(
                    text = stringResource(R.string.home_checking_status),
                    containerColor = statusColors.warningBg,
                    contentColor = statusColors.warning,
                    bold = true,
                )
            }
            Text(
                text = card.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.text,
            )
            Text(
                text = messageText,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val orderCode = checkingMessage.orderCodeOrNull
                if (orderCode != null) {
                    OutlinedButton(
                        onClick = { onOpenOrder(orderCode) },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, colors.borderStrong),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag(MyExamsTestTags.checkingViewOrderButton(card.code)),
                    ) {
                        Text(
                            text = stringResource(R.string.home_checking_viewOrder),
                            color = colors.text,
                        )
                    }
                }
                OutlinedButton(
                    onClick = onReload,
                    enabled = !isReloading,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, colors.borderStrong),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = colors.bgSubtle,
                        contentColor = colors.text,
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(MyExamsTestTags.checkingReloadButton(card.code)),
                ) {
                    if (isReloading) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = colors.text,
                            modifier = Modifier.size(16.dp),
                        )
                    } else {
                        Icon(
                            imageVector = MyExamsIcons.Refresh,
                            contentDescription = null,
                            tint = colors.text,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.home_checking_reload))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BundleGroupSection(
    group: BundleGroup,
    shellWidth: ShellWidth,
    locale: AppLocale,
    headingFocusRequester: FocusRequester,
    onViewBundle: (String) -> Unit,
    onCrossRefClick: (UUID, String) -> Unit,
    onContinueAttempt: (String) -> Unit,
    onStartPrepare: (String) -> Unit,
    onOpenResult: (String) -> Unit,
    onOpenHistoryReset: () -> Unit,
    onOpenCheckout: (String) -> Unit,
) {
    val colors = Thi360Theme.colors

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.bgSurface,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MyExamsTestTags.bundleGroup(group.ref.bundleId)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = true),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = MyExamsIcons.Package,
                            contentDescription = null,
                            tint = colors.primaryText,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = group.ref.name,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = colors.text,
                                modifier = Modifier
                                    .focusRequester(headingFocusRequester)
                                    .focusable()
                                    .semantics { heading() }
                                    .testTag(MyExamsTestTags.bundleHeading(group.ref.bundleId)),
                            )
                            ChipPill(
                                text = pluralStringResource(
                                    R.plurals.home_examCount,
                                    group.totalItemCount,
                                    group.totalItemCount,
                                ),
                                containerColor = colors.bgSubtle,
                                contentColor = colors.textSecondary,
                                modifier = Modifier.testTag(
                                    MyExamsTestTags.bundleCount(group.ref.bundleId),
                                ),
                            )
                        }
                        Text(
                            text = group.ref.code,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                        )
                    }
                }
                TextButton(
                    onClick = { onViewBundle(group.ref.code) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(MyExamsTestTags.bundleViewButton(group.ref.bundleId)),
                ) {
                    Text(
                        text = stringResource(R.string.bundle_home_viewBundle),
                        color = colors.primaryText,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (group.cards.isNotEmpty()) {
                val cardRows = chunkCardsForWidth(group.cards, shellWidth)
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    for (row in cardRows) {
                        ResponsiveExamCardRow(
                            cards = row,
                            shellWidth = shellWidth,
                            locale = locale,
                            onContinueAttempt = onContinueAttempt,
                            onStartPrepare = onStartPrepare,
                            onOpenResult = onOpenResult,
                            onOpenHistoryReset = onOpenHistoryReset,
                            onOpenCheckout = onOpenCheckout,
                        )
                    }
                }
            }

            if (group.crossRefs.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.bgPage,
                    border = BorderStroke(1.dp, colors.border),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        for (ref in group.crossRefs) {
                            val fullText = stringResource(
                                R.string.bundle_home_crossReference,
                                ref.examName,
                                ref.primaryBundle.name,
                            )
                            val annotated = remember(fullText, ref.examName, ref.primaryBundle.name, colors) {
                                buildAnnotatedString {
                                    append(fullText)
                                    val examStart = fullText.indexOf(ref.examName)
                                    if (examStart >= 0) {
                                        addStyle(
                                            style = SpanStyle(
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.text,
                                            ),
                                            start = examStart,
                                            end = examStart + ref.examName.length,
                                        )
                                    }
                                    val bundleStart = fullText.lastIndexOf(ref.primaryBundle.name)
                                    if (bundleStart >= 0) {
                                        addStyle(
                                            style = SpanStyle(
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.primaryText,
                                                textDecoration = TextDecoration.Underline,
                                            ),
                                            start = bundleStart,
                                            end = bundleStart + ref.primaryBundle.name.length,
                                        )
                                    }
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(role = Role.Button) {
                                        onCrossRefClick(
                                            ref.primaryBundle.bundleId,
                                            ref.primaryBundle.name,
                                        )
                                    }
                                    .padding(horizontal = 6.dp, vertical = 10.dp)
                                    .testTag(
                                        MyExamsTestTags.bundleCrossRef(
                                            group.ref.bundleId,
                                            ref.examCode,
                                        ),
                                    ),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(
                                    text = annotated,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = stringResource(R.string.bundle_home_groupFootnote),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                modifier = Modifier.testTag(MyExamsTestTags.bundleFootnote(group.ref.bundleId)),
            )
        }
    }
}

private fun chunkCardsForWidth(cards: List<MyExamCard>, shellWidth: ShellWidth): List<List<MyExamCard>> =
    when (shellWidth) {
        ShellWidth.Narrow -> cards.chunked(1)
        ShellWidth.Wide, ShellWidth.TwoColumn -> cards.chunked(2)
    }

@Composable
private fun ResponsiveExamCardRow(
    cards: List<MyExamCard>,
    shellWidth: ShellWidth,
    locale: AppLocale,
    onContinueAttempt: (String) -> Unit,
    onStartPrepare: (String) -> Unit,
    onOpenResult: (String) -> Unit,
    onOpenHistoryReset: () -> Unit,
    onOpenCheckout: (String) -> Unit,
) {
    when (shellWidth) {
        ShellWidth.Narrow -> {
            MyExamCardItem(
                card = cards.first(),
                locale = locale,
                onContinueAttempt = onContinueAttempt,
                onStartPrepare = onStartPrepare,
                onOpenResult = onOpenResult,
                onOpenHistoryReset = onOpenHistoryReset,
                onOpenCheckout = onOpenCheckout,
            )
        }

        ShellWidth.Wide -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    MyExamCardItem(
                        card = cards.first(),
                        locale = locale,
                        onContinueAttempt = onContinueAttempt,
                        onStartPrepare = onStartPrepare,
                        onOpenResult = onOpenResult,
                        onOpenHistoryReset = onOpenHistoryReset,
                        onOpenCheckout = onOpenCheckout,
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    if (cards.size > 1) {
                        MyExamCardItem(
                            card = cards[1],
                            locale = locale,
                            onContinueAttempt = onContinueAttempt,
                            onStartPrepare = onStartPrepare,
                            onOpenResult = onOpenResult,
                            onOpenHistoryReset = onOpenHistoryReset,
                            onOpenCheckout = onOpenCheckout,
                        )
                    } else {
                        Spacer(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        ShellWidth.TwoColumn -> {
            TwoColumnLayout(
                start = {
                    MyExamCardItem(
                        card = cards.first(),
                        locale = locale,
                        onContinueAttempt = onContinueAttempt,
                        onStartPrepare = onStartPrepare,
                        onOpenResult = onOpenResult,
                        onOpenHistoryReset = onOpenHistoryReset,
                        onOpenCheckout = onOpenCheckout,
                    )
                },
                end = {
                    if (cards.size > 1) {
                        MyExamCardItem(
                            card = cards[1],
                            locale = locale,
                            onContinueAttempt = onContinueAttempt,
                            onStartPrepare = onStartPrepare,
                            onOpenResult = onOpenResult,
                            onOpenHistoryReset = onOpenHistoryReset,
                            onOpenCheckout = onOpenCheckout,
                        )
                    } else {
                        Spacer(modifier = Modifier.fillMaxWidth())
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MyExamCardItem(
    card: MyExamCard,
    locale: AppLocale,
    onContinueAttempt: (String) -> Unit,
    onStartPrepare: (String) -> Unit,
    onOpenResult: (String) -> Unit,
    onOpenHistoryReset: () -> Unit,
    onOpenCheckout: (String) -> Unit,
) {
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors
    val buttons = remember(card) { CardButtons.of(card) }
    val notes = remember(card) { CardNotes.of(card) }

    val examKindName = pickExamKindName(card.examKind, locale)
    val isClassAssigned = card.entitlementSource == ENTITLEMENT_SOURCE_CLASS
    val trimmedClassName = card.className?.trim()?.takeIf { it.isNotEmpty() }
    val sourceChipText = when {
        isClassAssigned && trimmedClassName != null ->
            stringResource(R.string.home_card_classAssigned, trimmedClassName)
        isClassAssigned -> stringResource(R.string.home_card_classAssignedNoName)
        else -> stringResource(R.string.home_card_purchased)
    }
    val statusLabel = when (card.state) {
        MyExamCard.State.IN_PROGRESS -> stringResource(R.string.home_status_IN_PROGRESS)
        MyExamCard.State.CAN_START -> stringResource(R.string.home_status_CAN_START)
        MyExamCard.State.EXHAUSTED -> stringResource(R.string.home_status_EXHAUSTED)
        MyExamCard.State.CHECKING,
        MyExamCard.State.NONE,
        -> card.state.value
    }
    val (statusBg, statusFg) = when (card.state) {
        MyExamCard.State.IN_PROGRESS -> statusColors.infoBg to statusColors.info
        MyExamCard.State.CAN_START -> statusColors.successBg to statusColors.success
        MyExamCard.State.EXHAUSTED,
        MyExamCard.State.CHECKING,
        MyExamCard.State.NONE,
        -> colors.bgSubtle to colors.textSecondary
    }
    val formatLabel = when (card.format) {
        "FULL" -> stringResource(R.string.storefront_format_FULL)
        "PART" -> stringResource(R.string.storefront_format_PART)
        "SUBJECT" -> stringResource(R.string.storefront_format_SUBJECT)
        "BUNDLE" -> stringResource(R.string.storefront_format_BUNDLE)
        else -> card.format
    }
    val scopeChip = pickScopeLabel(card.scopeLabel, locale)

    val questionPart = card.questionCount?.let { range ->
        if (range.min == range.max) {
            stringResource(R.string.storefront_units_questions, range.min)
        } else {
            stringResource(R.string.storefront_units_questions, 0)
                .replaceFirst("0", "${range.min}–${range.max}")
        }
    }
    val durationPart = formatRangeText(card.durationMinutes)?.let { text ->
        stringResource(R.string.home_card_minutesPerAttempt, text)
    }
    val metaLine = listOfNotNull(questionPart, durationPart).joinToString(" · ")

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.bgSurface,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MyExamsTestTags.examCard(card.code)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ChipPill(
                    text = examKindName,
                    containerColor = colors.bgSubtle,
                    contentColor = colors.textSecondary,
                )
                ChipPill(
                    text = sourceChipText,
                    containerColor = if (isClassAssigned) statusColors.infoBg else colors.primaryContainer,
                    contentColor = if (isClassAssigned) statusColors.info else colors.primaryText,
                    bold = true,
                )
                if (card.buildMode == BUILD_MODE_REVIEW) {
                    ChipPill(
                        text = stringResource(R.string.storefront_review),
                        containerColor = statusColors.infoBg,
                        contentColor = statusColors.info,
                        bold = true,
                    )
                }
                ChipPill(
                    text = statusLabel,
                    containerColor = statusBg,
                    contentColor = statusFg,
                    bold = true,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = card.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ChipPill(
                        text = formatLabel,
                        containerColor = colors.bgSubtle,
                        contentColor = colors.textSecondary,
                    )
                    if (!scopeChip.isNullOrBlank()) {
                        ChipPill(
                            text = scopeChip,
                            containerColor = colors.bgSubtle,
                            contentColor = colors.textSecondary,
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
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = colors.bgPage,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.home_card_attemptsLeft),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = card.attemptsLeft.toString(),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.text,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (buttons.continueAttemptId != null) {
                    Button(
                        onClick = { onContinueAttempt(buttons.continueAttemptId) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primaryFill,
                            contentColor = colors.onPrimary,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag(MyExamsTestTags.examCardContinue(card.code)),
                    ) {
                        Text(text = stringResource(R.string.home_actions_continue))
                    }
                } else if (buttons.startEntitlementId != null) {
                    Button(
                        onClick = { onStartPrepare(buttons.startEntitlementId) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primaryFill,
                            contentColor = colors.onPrimary,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag(MyExamsTestTags.examCardStart(card.code)),
                    ) {
                        Text(text = stringResource(R.string.home_actions_start))
                    }
                }

                if (buttons.showRebuy) {
                    Button(
                        onClick = { onOpenCheckout(card.code) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primaryFill,
                            contentColor = colors.onPrimary,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag(MyExamsTestTags.examCardRebuy(card.code)),
                    ) {
                        Text(text = stringResource(R.string.home_actions_rebuy))
                    }
                }

                for (note in notes.middleNotes) {
                    Text(
                        text = noteString(note),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        modifier = Modifier.testTag(MyExamsTestTags.examCardMiddleNote(card.code, note)),
                    )
                }

                if (buttons.hasSecondaryRow) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (buttons.resultAttemptId != null) {
                            OutlinedButton(
                                onClick = { onOpenResult(buttons.resultAttemptId) },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, colors.border),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = colors.bgSubtle,
                                    contentColor = colors.text,
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag(MyExamsTestTags.examCardViewResult(card.code)),
                            ) {
                                Text(
                                    text = stringResource(R.string.home_actions_viewResult),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                        if (buttons.showHistory) {
                            OutlinedButton(
                                onClick = onOpenHistoryReset,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, colors.border),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = colors.bgSubtle,
                                    contentColor = colors.text,
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag(MyExamsTestTags.examCardHistory(card.code)),
                            ) {
                                Text(
                                    text = stringResource(R.string.home_actions_history),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }

                if (notes.pendingTopUpOrderCode != null) {
                    Text(
                        text = stringResource(
                            R.string.home_notes_pendingTopUp,
                            notes.pendingTopUpOrderCode,
                        ),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = statusColors.warning,
                        modifier = Modifier.testTag(MyExamsTestTags.examCardPendingTopUpNote(card.code)),
                    )
                }

                if (buttons.showBuyMore) {
                    OutlinedButton(
                        onClick = { onOpenCheckout(card.code) },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, colors.borderStrong),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag(MyExamsTestTags.examCardBuyMore(card.code)),
                    ) {
                        Text(
                            text = stringResource(R.string.home_actions_buyMore),
                            color = colors.text,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun noteString(note: CardNote): String = when (note) {
    CardNote.LAST_ATTEMPT -> stringResource(R.string.home_notes_lastAttempt)
    CardNote.CLASS_EXHAUSTED -> stringResource(R.string.home_notes_classExhausted)
    CardNote.CLASS_EXHAUSTED_WITH_HISTORY -> stringResource(R.string.home_notes_classExhaustedWithHistory)
    CardNote.DISCONTINUED_EXHAUSTED -> stringResource(R.string.home_notes_discontinuedExhausted)
    CardNote.DISCONTINUED_EXHAUSTED_WITH_HISTORY ->
        stringResource(R.string.home_notes_discontinuedExhaustedWithHistory)
    CardNote.DISCONTINUED_CAN_START -> stringResource(R.string.home_notes_discontinuedCanStart)
}

@Composable
internal fun ChipPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor,
        modifier = modifier.semantics(mergeDescendants = true) {},
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = contentColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
