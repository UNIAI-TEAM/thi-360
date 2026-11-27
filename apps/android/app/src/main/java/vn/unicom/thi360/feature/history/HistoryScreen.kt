package vn.unicom.thi360.feature.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import vn.unicom.thi360.R
import vn.unicom.thi360.core.brand.BrandMark
import vn.unicom.thi360.core.catalog.CatalogOptionItem
import vn.unicom.thi360.core.network.generated.model.AttemptHistoryItem
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.theme.LocalAppLocale
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.core.ui.LocalShellWidth
import vn.unicom.thi360.core.ui.ShellWidth
import vn.unicom.thi360.core.ui.TwoColumnLayout
import vn.unicom.thi360.feature.myexams.ChipPill
import vn.unicom.thi360.feature.myexams.MyExamsIcons
import vn.unicom.thi360.feature.myexams.NavigationClickGuard
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags

private enum class ActiveDatePickerField {
    FROM,
    TO,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(
    feedState: HistoryFeedState,
    showCompactBrandHeader: Boolean,
    onSearchInputChanged: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onSelectKind: (String?) -> Unit,
    onSelectScope: (HistoryScope?) -> Unit,
    onSelectFromDate: (LocalDate?) -> Unit,
    onSelectToDate: (LocalDate?) -> Unit,
    onClearAllFilters: () -> Unit,
    onLoadNextPage: () -> Unit,
    onRetry: () -> Unit,
    onGoToMyExams: () -> Unit,
    onContinueAttempt: (String) -> Unit,
    onOpenResult: (String) -> Unit,
    modifier: Modifier = Modifier,
    clickGuard: NavigationClickGuard = remember { NavigationClickGuard() },
) {
    val shellWidth = LocalShellWidth.current
    val locale = LocalAppLocale.current
    val horizontalPadding = when (shellWidth) {
        ShellWidth.Narrow -> 16.dp
        ShellWidth.Wide -> 24.dp
        ShellWidth.TwoColumn -> 28.dp
    }
    val listState = rememberLazyListState()
    var activeDatePicker by remember { mutableStateOf<ActiveDatePickerField?>(null) }

    val loadedState = feedState.loadState as? LoadState.Loaded
    val loadedPage = loadedState?.value
    val loadedItems = loadedPage?.items.orEmpty()
    val isStale = loadedState?.stale == true
    val canLoadMore = !feedState.isOfflineBlock &&
        !isStale &&
        loadedPage != null &&
        loadedPage.hasMore &&
        !feedState.isLoadingMore &&
        !feedState.isFilterRefreshing

    LaunchedEffect(listState, canLoadMore, loadedItems.size) {
        if (!canLoadMore || loadedItems.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val totalItems = layoutInfo.totalItemsCount
            totalItems > 0 && lastVisible >= totalItems - 2
        }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                onLoadNextPage()
            }
    }

    val totalCountForHeader: Long? = when {
        feedState.isOfflineBlock -> null
        feedState.loadState is LoadState.Loaded -> feedState.loadState.value.totalItems
        feedState.loadState is LoadState.Empty -> 0L
        else -> null
    }

    val listTitleDescription = stringResource(R.string.history_listTitle)

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = listTitleDescription }
            .testTag(HistoryTestTags.LIST),
    ) {
        item(key = "history_header") {
            HistoryHeader(
                totalCount = totalCountForHeader,
                showCompactBrandHeader = showCompactBrandHeader,
            )
        }

        item(key = "history_filters") {
            HistoryFilterSection(
                feedState = feedState,
                locale = locale,
                onSearchInputChanged = onSearchInputChanged,
                onSearchSubmit = onSearchSubmit,
                onSelectKind = onSelectKind,
                onSelectScope = onSelectScope,
                onOpenFromDatePicker = { activeDatePicker = ActiveDatePickerField.FROM },
                onClearFromDate = { onSelectFromDate(null) },
                onOpenToDatePicker = { activeDatePicker = ActiveDatePickerField.TO },
                onClearToDate = { onSelectToDate(null) },
                onClearAllFilters = onClearAllFilters,
            )
        }

        if (feedState.isOfflineBlock) {
            item(key = "history_offline") {
                HistoryOfflineCard(onRetry = onRetry)
            }
        } else {
            when (val loadState = feedState.loadState) {
                LoadState.Loading -> {
                    item(key = "history_skeleton") {
                        HistoryLoadingSkeleton()
                    }
                }

                is LoadState.Failed -> {
                    item(key = "history_error") {
                        HistoryErrorCard(onRetry = onRetry)
                    }
                }

                LoadState.Empty -> {
                    item(key = "history_empty") {
                        HistoryEmptyCard(
                            hasActiveFilters = !feedState.filter.isEmpty,
                            onGoToMyExams = { clickGuard.run(onGoToMyExams) },
                            onClearFilters = onClearAllFilters,
                        )
                    }
                }

                is LoadState.Loaded -> {
                    if (loadState.stale) {
                        item(key = "history_stale") {
                            HistoryStaleBanner(
                                lastLoadedAtMillis = feedState.lastLoadedAtMillis,
                                isRetrying = feedState.isRetryingStale,
                                onRetry = onRetry,
                            )
                        }
                    }

                    val pageItems = loadState.value.items
                    if (pageItems.isEmpty()) {
                        item(key = "history_empty_stale") {
                            HistoryEmptyCard(
                                hasActiveFilters = !feedState.filter.isEmpty,
                                onGoToMyExams = { clickGuard.run(onGoToMyExams) },
                                onClearFilters = onClearAllFilters,
                            )
                        }
                    } else {
                        val dimModifier = if (feedState.isFilterRefreshing) {
                            Modifier
                                .alpha(0.6f)
                                .testTag(HistoryTestTags.FILTER_REFRESHING_DIMMED)
                        } else {
                            Modifier
                        }

                        if (shellWidth == ShellWidth.TwoColumn) {
                            val pairs = pageItems.withIndex().chunked(2)
                            items(
                                items = pairs,
                                key = { pair ->
                                    pair.joinToString("|") { indexed ->
                                        HistoryRowFormat.stableKey(indexed.value, indexed.index)
                                    }
                                },
                            ) { pair ->
                                Box(modifier = dimModifier.fillMaxWidth()) {
                                    TwoColumnLayout(
                                        start = {
                                            HistoryAttemptCard(
                                                item = pair[0].value,
                                                subjectMap = feedState.subjectMap,
                                                locale = locale,
                                                onContinueAttempt = { id -> clickGuard.run { onContinueAttempt(id) } },
                                                onOpenResult = { id -> clickGuard.run { onOpenResult(id) } },
                                            )
                                        },
                                        end = {
                                            if (pair.size > 1) {
                                                HistoryAttemptCard(
                                                    item = pair[1].value,
                                                    subjectMap = feedState.subjectMap,
                                                    locale = locale,
                                                    onContinueAttempt = { id ->
                                                        clickGuard.run { onContinueAttempt(id) }
                                                    },
                                                    onOpenResult = { id -> clickGuard.run { onOpenResult(id) } },
                                                )
                                            } else {
                                                Spacer(modifier = Modifier.fillMaxWidth())
                                            }
                                        },
                                    )
                                }
                            }
                        } else {
                            items(
                                items = pageItems.withIndex().toList(),
                                key = { indexed ->
                                    HistoryRowFormat.stableKey(indexed.value, indexed.index)
                                },
                            ) { indexed ->
                                Box(modifier = dimModifier.fillMaxWidth()) {
                                    HistoryAttemptCard(
                                        item = indexed.value,
                                        subjectMap = feedState.subjectMap,
                                        locale = locale,
                                        onContinueAttempt = { id -> clickGuard.run { onContinueAttempt(id) } },
                                        onOpenResult = { id -> clickGuard.run { onOpenResult(id) } },
                                    )
                                }
                            }
                        }

                        if (feedState.isLoadingMore) {
                            item(key = "history_load_more") {
                                val loadingDesc = stringResource(R.string.history_loading)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp)
                                        .semantics { contentDescription = loadingDesc }
                                        .testTag(HistoryTestTags.LOAD_MORE_INDICATOR),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        color = Thi360Theme.colors.primaryFill,
                                        modifier = Modifier.size(28.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "history_bottom_spacer") {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    activeDatePicker?.let { field ->
        HistoryDatePickerModal(
            field = field,
            currentFrom = feedState.filter.from,
            currentTo = feedState.filter.to,
            onDismiss = { activeDatePicker = null },
            onConfirm = { pickedDate ->
                activeDatePicker = null
                when (field) {
                    ActiveDatePickerField.FROM -> onSelectFromDate(pickedDate)
                    ActiveDatePickerField.TO -> onSelectToDate(pickedDate)
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryHeader(
    totalCount: Long?,
    showCompactBrandHeader: Boolean,
) {
    val colors = Thi360Theme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showCompactBrandHeader) {
            BrandMark(markSize = 32.dp)
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.history_pageTitle),
                style = if (showCompactBrandHeader) {
                    MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                } else {
                    MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                },
                color = colors.text,
                modifier = Modifier
                    .semantics { heading() }
                    .testTag(ShellTestTags.screenTitle(Destination.History)),
            )
            if (totalCount != null) {
                val safeCount = totalCount.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
                ChipPill(
                    text = pluralStringResource(R.plurals.history_count, safeCount, safeCount),
                    containerColor = colors.bgSubtle,
                    contentColor = colors.textMuted,
                    modifier = Modifier.testTag(HistoryTestTags.COUNT_BADGE),
                )
            }
        }
        Text(
            text = stringResource(R.string.history_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.testTag(HistoryTestTags.SUBTITLE),
        )
    }
}

@Composable
private fun HistoryFilterSection(
    feedState: HistoryFeedState,
    locale: AppLocale,
    onSearchInputChanged: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onSelectKind: (String?) -> Unit,
    onSelectScope: (HistoryScope?) -> Unit,
    onOpenFromDatePicker: () -> Unit,
    onClearFromDate: () -> Unit,
    onOpenToDatePicker: () -> Unit,
    onClearToDate: () -> Unit,
    onClearAllFilters: () -> Unit,
) {
    val colors = Thi360Theme.colors
    val filterLabel = stringResource(R.string.history_filters_label)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.bgSurface,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = filterLabel }
            .testTag(HistoryTestTags.FILTER_SECTION),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TwoColumnLayout(
                spacing = 12.dp,
                start = {
                    SearchFilterField(
                        query = feedState.searchInput,
                        onQueryChanged = onSearchInputChanged,
                        onSearchSubmit = onSearchSubmit,
                    )
                },
                end = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            ExamKindDropdown(
                                selectedKind = feedState.filter.kind,
                                options = feedState.examKinds,
                                locale = locale,
                                onSelectKind = onSelectKind,
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            ScopeDropdown(
                                selectedScope = feedState.filter.scope,
                                onSelectScope = onSelectScope,
                            )
                        }
                    }
                },
            )

            TwoColumnLayout(
                spacing = 12.dp,
                start = {
                    DateFilterField(
                        label = stringResource(R.string.history_filters_from),
                        selectedDate = feedState.filter.from,
                        fieldTag = HistoryTestTags.FROM_DATE_FIELD,
                        clearTag = HistoryTestTags.FROM_DATE_CLEAR,
                        clearContentDescription = stringResource(R.string.history_filters_clearFrom),
                        onOpenPicker = onOpenFromDatePicker,
                        onClear = onClearFromDate,
                    )
                },
                end = {
                    DateFilterField(
                        label = stringResource(R.string.history_filters_to),
                        selectedDate = feedState.filter.to,
                        fieldTag = HistoryTestTags.TO_DATE_FIELD,
                        clearTag = HistoryTestTags.TO_DATE_CLEAR,
                        clearContentDescription = stringResource(R.string.history_filters_clearTo),
                        onOpenPicker = onOpenToDatePicker,
                        onClear = onClearToDate,
                    )
                },
            )

            if (feedState.hasActiveFilters) {
                OutlinedButton(
                    onClick = onClearAllFilters,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, colors.borderStrong),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = colors.bgSubtle,
                        contentColor = colors.text,
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(HistoryTestTags.CLEAR_FILTERS_BUTTON),
                ) {
                    Text(text = stringResource(R.string.history_filters_clear))
                }
            }
        }
    }
}

@Composable
private fun SearchFilterField(
    query: String,
    onQueryChanged: (String) -> Unit,
    onSearchSubmit: () -> Unit,
) {
    val colors = Thi360Theme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.history_filters_q),
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
        )
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            singleLine = true,
            placeholder = {
                Text(
                    text = stringResource(R.string.history_filters_qPlaceholder),
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = MyExamsIcons.Search,
                    contentDescription = null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(18.dp),
                )
            },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(
                        onClick = { onQueryChanged("") },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag(HistoryTestTags.SEARCH_CLEAR_BUTTON),
                    ) {
                        Icon(
                            imageVector = MyExamsIcons.Close,
                            contentDescription = stringResource(R.string.history_filters_clearQuery),
                            tint = colors.textMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearchSubmit() }),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = colors.text,
                unfocusedTextColor = colors.text,
                focusedBorderColor = colors.primaryFill,
                unfocusedBorderColor = colors.borderStrong,
                focusedContainerColor = colors.bgSurface,
                unfocusedContainerColor = colors.bgSurface,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .testTag(HistoryTestTags.SEARCH_FIELD),
        )
    }
}

@Composable
private fun ExamKindDropdown(
    selectedKind: String?,
    options: List<CatalogOptionItem>,
    locale: AppLocale,
    onSelectKind: (String?) -> Unit,
) {
    val colors = Thi360Theme.colors
    var expanded by remember { mutableStateOf(false) }
    val allLabel = stringResource(R.string.history_filters_kindAll)
    val selectedLabel = if (selectedKind.isNullOrBlank()) {
        allLabel
    } else {
        options.firstOrNull { it.code == selectedKind }
            ?.let { HistoryRowFormat.pickCatalogName(it, locale) }
            ?: selectedKind
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.history_filters_kind),
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.bgSurface,
                border = BorderStroke(1.dp, colors.borderStrong),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.DropdownList) { expanded = true }
                    .testTag(HistoryTestTags.KIND_DROPDOWN),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = selectedLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = MyExamsIcons.ChevronDown,
                        contentDescription = null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(text = allLabel) },
                    onClick = {
                        expanded = false
                        onSelectKind(null)
                    },
                    modifier = Modifier.testTag(HistoryTestTags.kindOption(null)),
                )
                for (option in options) {
                    val label = HistoryRowFormat.pickCatalogName(option, locale)
                    DropdownMenuItem(
                        text = { Text(text = label) },
                        onClick = {
                            expanded = false
                            onSelectKind(option.code)
                        },
                        modifier = Modifier.testTag(HistoryTestTags.kindOption(option.code)),
                    )
                }
            }
        }
    }
}

@Composable
private fun ScopeDropdown(
    selectedScope: HistoryScope?,
    onSelectScope: (HistoryScope?) -> Unit,
) {
    val colors = Thi360Theme.colors
    var expanded by remember { mutableStateOf(false) }
    val allLabel = stringResource(R.string.history_filters_scopeAll)
    val selectedLabel = selectedScope?.let { scopeLabel(it) } ?: allLabel

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.history_filters_scope),
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.bgSurface,
                border = BorderStroke(1.dp, colors.borderStrong),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.DropdownList) { expanded = true }
                    .testTag(HistoryTestTags.SCOPE_DROPDOWN),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = selectedLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = MyExamsIcons.ChevronDown,
                        contentDescription = null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(text = allLabel) },
                    onClick = {
                        expanded = false
                        onSelectScope(null)
                    },
                    modifier = Modifier.testTag(HistoryTestTags.scopeOption(null)),
                )
                for (scope in HistoryScope.entries) {
                    DropdownMenuItem(
                        text = { Text(text = scopeLabel(scope)) },
                        onClick = {
                            expanded = false
                            onSelectScope(scope)
                        },
                        modifier = Modifier.testTag(HistoryTestTags.scopeOption(scope)),
                    )
                }
            }
        }
    }
}

@Composable
private fun DateFilterField(
    label: String,
    selectedDate: LocalDate?,
    fieldTag: String,
    clearTag: String,
    clearContentDescription: String,
    onOpenPicker: () -> Unit,
    onClear: () -> Unit,
) {
    val colors = Thi360Theme.colors
    val displayText = selectedDate?.let { HistoryRowFormat.formatFilterDate(it) } ?: label

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.bgSurface,
                border = BorderStroke(1.dp, colors.borderStrong),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClick = onOpenPicker)
                    .testTag(fieldTag),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selectedDate != null) colors.text else colors.textMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = MyExamsIcons.Calendar,
                        contentDescription = null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (selectedDate != null) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag(clearTag),
                ) {
                    Icon(
                        imageVector = MyExamsIcons.Close,
                        contentDescription = clearContentDescription,
                        tint = colors.textMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryDatePickerModal(
    field: ActiveDatePickerField,
    currentFrom: LocalDate?,
    currentTo: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val initialDate = when (field) {
        ActiveDatePickerField.FROM -> currentFrom
        ActiveDatePickerField.TO -> currentTo
    }
    val initialMillis = initialDate?.let { HistoryRowFormat.localDateToUtcMillis(it) }
    val selectableDates = remember(field, currentFrom, currentTo) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val candidate = HistoryRowFormat.utcMillisToLocalDate(utcTimeMillis)
                return when (field) {
                    ActiveDatePickerField.FROM -> currentTo == null || !candidate.isAfter(currentTo)
                    ActiveDatePickerField.TO -> currentFrom == null || !candidate.isBefore(currentFrom)
                }
            }
        }
    }
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
        selectableDates = selectableDates,
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) {
                        val picked = HistoryRowFormat.utcMillisToLocalDate(millis)
                        val valid = when (field) {
                            ActiveDatePickerField.FROM -> currentTo == null || !picked.isAfter(currentTo)
                            ActiveDatePickerField.TO -> currentFrom == null || !picked.isBefore(currentFrom)
                        }
                        if (valid) {
                            onConfirm(picked)
                        }
                    }
                },
                enabled = pickerState.selectedDateMillis != null,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(HistoryTestTags.DATE_PICKER_CONFIRM),
            ) {
                Text(text = stringResource(R.string.history_filters_dateConfirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(HistoryTestTags.DATE_PICKER_CANCEL),
            ) {
                Text(text = stringResource(R.string.history_filters_dateCancel))
            }
        },
        modifier = Modifier.testTag(HistoryTestTags.DATE_PICKER_DIALOG),
    ) {
        DatePicker(state = pickerState)
    }
}

@Composable
private fun HistoryLoadingSkeleton() {
    val colors = Thi360Theme.colors
    val loadingDesc = stringResource(R.string.history_loading)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.bgSurface,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = loadingDesc }
            .testTag(HistoryTestTags.LOADING_SKELETON),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.bgSubtle),
                )
            }
        }
    }
}

@Composable
private fun HistoryOfflineCard(onRetry: () -> Unit) {
    val colors = Thi360Theme.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.bgSurface,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(HistoryTestTags.OFFLINE_STATE),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.common_offline_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.common_offline_description),
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
                    .testTag(HistoryTestTags.OFFLINE_RETRY_BUTTON),
            ) {
                Text(text = stringResource(R.string.common_retry), color = colors.text)
            }
        }
    }
}

@Composable
private fun HistoryErrorCard(onRetry: () -> Unit) {
    val colors = Thi360Theme.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.bgSurface,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(HistoryTestTags.ERROR_STATE),
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
                    .background(colors.dangerBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MyExamsIcons.AlertTriangle,
                    contentDescription = null,
                    tint = colors.dangerText,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                text = stringResource(R.string.history_error_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.history_error_description),
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
                    .testTag(HistoryTestTags.ERROR_RETRY_BUTTON),
            ) {
                Text(text = stringResource(R.string.common_retry), color = colors.text)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryStaleBanner(
    lastLoadedAtMillis: Long?,
    isRetrying: Boolean,
    onRetry: () -> Unit,
) {
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors
    val formattedTime = HistoryRowFormat.formatStaleTime(lastLoadedAtMillis)
    val description = if (formattedTime != null) {
        stringResource(R.string.history_stale_description, formattedTime)
    } else {
        stringResource(R.string.history_stale_descriptionNoTime)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = statusColors.warningBg,
        border = BorderStroke(1.dp, statusColors.warning),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag(HistoryTestTags.STALE_BANNER),
    ) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f, fill = true),
            ) {
                Text(
                    text = stringResource(R.string.history_stale_title),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = statusColors.warning,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
            OutlinedButton(
                onClick = onRetry,
                enabled = !isRetrying,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, statusColors.warning.copy(alpha = 0.5f)),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(HistoryTestTags.STALE_RETRY_BUTTON),
            ) {
                if (isRetrying) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = statusColors.warning,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(text = stringResource(R.string.common_retry), color = statusColors.warning)
            }
        }
    }
}

@Composable
private fun HistoryEmptyCard(
    hasActiveFilters: Boolean,
    onGoToMyExams: () -> Unit,
    onClearFilters: () -> Unit,
) {
    val colors = Thi360Theme.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.bgSurface,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(
                if (hasActiveFilters) HistoryTestTags.EMPTY_NO_MATCH else HistoryTestTags.EMPTY_NO_ATTEMPTS,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(
                    if (hasActiveFilters) {
                        R.string.history_empty_title
                    } else {
                        R.string.history_empty_noAttemptsTitle
                    },
                ),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(
                    if (hasActiveFilters) {
                        R.string.history_empty_description
                    } else {
                        R.string.history_empty_noAttemptsDescription
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 360.dp),
            )
            Button(
                onClick = if (hasActiveFilters) onClearFilters else onGoToMyExams,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primaryFill,
                    contentColor = colors.onPrimary,
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(
                        if (hasActiveFilters) {
                            HistoryTestTags.EMPTY_CLEAR_FILTERS_BUTTON
                        } else {
                            HistoryTestTags.EMPTY_TO_MY_EXAMS_BUTTON
                        },
                    ),
            ) {
                Text(
                    text = stringResource(
                        if (hasActiveFilters) {
                            R.string.history_empty_clearFilters
                        } else {
                            R.string.history_empty_toMyExams
                        },
                    ),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryAttemptCard(
    item: AttemptHistoryItem,
    subjectMap: Map<String, CatalogOptionItem>,
    locale: AppLocale,
    onContinueAttempt: (String) -> Unit,
    onOpenResult: (String) -> Unit,
) {
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors
    val attemptIdStr = item.attemptId?.toString().orEmpty()
    val status = HistoryRowFormat.resolveStatus(item.state, item.regrading)
    val faultBanner = HistoryRowFormat.resolveFaultBanner(item.compensated, item.faultFlag)
    val scopeBase = HistoryScope.fromWireValue(item.scope)?.let { scopeLabel(it) }
        ?: item.scope?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.history_noChoice)
    val scopeDetail = when (val detail = HistoryRowFormat.resolveScopeDetail(item, subjectMap, locale)) {
        is FormattedScopeDetail.Part -> stringResource(R.string.history_scopeDetails_PART, detail.partCode)
        is FormattedScopeDetail.Subject ->
            stringResource(R.string.history_scopeDetails_SUBJECT, detail.subjectName)
        null -> null
    }
    val scopeTagText = if (scopeDetail != null) "$scopeBase · $scopeDetail" else scopeBase

    val scoreText = HistoryRowFormat.formatScore(item, locale) ?: stringResource(R.string.history_noScore)
    val showVactNote = HistoryRowFormat.showVactPracticeNote(item)
    val showRegradingNote = item.regrading == true

    val formattedChoices = HistoryRowFormat.formatChoices(item.choices, subjectMap, locale)
    val choicesText = if (formattedChoices.isEmpty()) {
        stringResource(R.string.history_noChoice)
    } else {
        val renderedChoices = mutableListOf<String>()
        for (entry in formattedChoices) {
            val optLabel = when (val opt = entry.option) {
                is FormattedChoiceOption.Known -> choiceOptionLabel(opt.code)
                is FormattedChoiceOption.Resolved -> opt.label
            }
            renderedChoices.add(
                if (entry.subjectNames.isNotEmpty()) {
                    "$optLabel: ${entry.subjectNames.joinToString(", ")}"
                } else {
                    optLabel
                },
            )
        }
        renderedChoices.joinToString(" · ")
    }

    val attemptNoPart = item.attemptNo?.let { stringResource(R.string.history_attemptNo, it) }
    val startedAtPart = HistoryRowFormat.formatStartedAt(item.startedAt)
    val attemptStartedText = listOfNotNull(attemptNoPart, startedAtPart).joinToString(" · ")

    val isContinue = item.action == AttemptHistoryItem.Action.CONTINUE
    val isViewResult = item.action == AttemptHistoryItem.Action.VIEW_RESULT
    val canOpenResult = isViewResult && attemptIdStr.isNotEmpty()
    val canContinue = isContinue && attemptIdStr.isNotEmpty()

    val (statusBg, statusFg) = when (status.tone) {
        HistoryStatusTone.SUCCESS -> statusColors.successBg to statusColors.success
        HistoryStatusTone.WARNING -> statusColors.warningBg to statusColors.warning
        HistoryStatusTone.INFO -> statusColors.infoBg to statusColors.info
        HistoryStatusTone.DANGER -> colors.dangerBg to colors.dangerText
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.bgSurface,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(HistoryTestTags.rowCard(attemptIdStr)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                val titleText = item.examName.orEmpty()
                if (canOpenResult) {
                    Box(
                        modifier = Modifier
                            .weight(1f, fill = true)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(role = Role.Button) { onOpenResult(attemptIdStr) }
                            .testTag(HistoryTestTags.rowTitle(attemptIdStr)),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                textDecoration = TextDecoration.Underline,
                            ),
                            color = colors.primaryText,
                        )
                    }
                } else {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.text,
                        modifier = Modifier
                            .weight(1f, fill = true)
                            .testTag(HistoryTestTags.rowTitle(attemptIdStr)),
                    )
                }

                ChipPill(
                    text = statusLabel(status.key),
                    containerColor = statusBg,
                    contentColor = statusFg,
                    bold = true,
                )
            }

            if (faultBanner != null) {
                Text(
                    text = when (faultBanner) {
                        HistoryFaultBanner.COMPENSATED -> stringResource(R.string.history_fault_compensated)
                        HistoryFaultBanner.SYSTEM_FAULT -> stringResource(R.string.history_fault_systemFault)
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = when (faultBanner) {
                        HistoryFaultBanner.COMPENSATED -> statusColors.info
                        HistoryFaultBanner.SYSTEM_FAULT -> statusColors.warning
                    },
                    modifier = Modifier.testTag(HistoryTestTags.rowFault(attemptIdStr)),
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ChipPill(
                    text = scopeTagText,
                    containerColor = colors.bgSubtle,
                    contentColor = colors.textSecondary,
                )
                if (showVactNote) {
                    ChipPill(
                        text = stringResource(R.string.history_vactPracticeNote),
                        containerColor = statusColors.infoBg,
                        contentColor = statusColors.info,
                    )
                }
                if (showRegradingNote) {
                    ChipPill(
                        text = stringResource(R.string.history_regradingNote),
                        containerColor = statusColors.warningBg,
                        contentColor = statusColors.warning,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.bgPage)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                HistoryMetaRow(
                    label = stringResource(R.string.history_columns_score),
                    value = scoreText,
                    valueTag = HistoryTestTags.rowScore(attemptIdStr),
                    emphasize = true,
                )
                HistoryMetaRow(
                    label = stringResource(R.string.history_columns_choice),
                    value = choicesText,
                    valueTag = HistoryTestTags.rowChoices(attemptIdStr),
                )
                if (attemptStartedText.isNotEmpty()) {
                    HistoryMetaRow(
                        label = stringResource(R.string.history_columns_attemptStarted),
                        value = attemptStartedText,
                        valueTag = HistoryTestTags.rowAttemptStarted(attemptIdStr),
                    )
                }
            }

            if (canContinue) {
                Button(
                    onClick = { onContinueAttempt(attemptIdStr) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primaryFill,
                        contentColor = colors.onPrimary,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag(HistoryTestTags.rowContinueButton(attemptIdStr)),
                ) {
                    Text(text = stringResource(R.string.history_actions_continue))
                }
            } else if (canOpenResult) {
                OutlinedButton(
                    onClick = { onOpenResult(attemptIdStr) },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, colors.borderStrong),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = colors.bgSubtle,
                        contentColor = colors.text,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag(HistoryTestTags.rowViewResultButton(attemptIdStr)),
                ) {
                    Text(text = stringResource(R.string.history_actions_viewResult))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryMetaRow(
    label: String,
    value: String,
    valueTag: String,
    emphasize: Boolean = false,
) {
    val colors = Thi360Theme.colors
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = colors.text,
            modifier = Modifier.testTag(valueTag),
        )
    }
}

@Composable
private fun scopeLabel(scope: HistoryScope): String = when (scope) {
    HistoryScope.FULL -> stringResource(R.string.history_scopes_FULL)
    HistoryScope.PART -> stringResource(R.string.history_scopes_PART)
    HistoryScope.SUBJECT -> stringResource(R.string.history_scopes_SUBJECT)
}

@Composable
private fun statusLabel(key: HistoryStatusKey): String = when (key) {
    HistoryStatusKey.REGRADING -> stringResource(R.string.history_states_REGRADING)
    HistoryStatusKey.SCORED -> stringResource(R.string.history_states_SCORED)
    HistoryStatusKey.PENDING_REVIEW -> stringResource(R.string.history_states_PENDING_REVIEW)
    HistoryStatusKey.IN_PROGRESS -> stringResource(R.string.history_states_IN_PROGRESS)
    HistoryStatusKey.GRADING -> stringResource(R.string.history_states_GRADING)
    HistoryStatusKey.GRADING_ERROR -> stringResource(R.string.history_states_GRADING_ERROR)
}

@Composable
private fun choiceOptionLabel(code: KnownChoiceOptionCode): String = when (code) {
    KnownChoiceOptionCode.ANH -> stringResource(R.string.history_choiceOptions_ANH)
    KnownChoiceOptionCode.CS -> stringResource(R.string.history_choiceOptions_CS)
    KnownChoiceOptionCode.ENGLISH -> stringResource(R.string.history_choiceOptions_ENGLISH)
    KnownChoiceOptionCode.ICT -> stringResource(R.string.history_choiceOptions_ICT)
    KnownChoiceOptionCode.KH -> stringResource(R.string.history_choiceOptions_KH)
    KnownChoiceOptionCode.SCIENCE -> stringResource(R.string.history_choiceOptions_SCIENCE)
    KnownChoiceOptionCode.TA -> stringResource(R.string.history_choiceOptions_TA)
}
