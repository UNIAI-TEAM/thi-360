package vn.unicom.thi360.feature.catalog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import vn.unicom.thi360.R
import vn.unicom.thi360.core.brand.BrandMark
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.theme.LocalAppLocale
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.core.ui.ListDetailLayout
import vn.unicom.thi360.core.ui.LoadFailureKind
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.core.ui.LocalShellWidth
import vn.unicom.thi360.core.ui.RefreshableContainer
import vn.unicom.thi360.core.ui.ShellWidth
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags
import vn.unicom.thi360.shell.routes.AppNavigator

@Composable
fun CatalogRoute(
    navigator: AppNavigator,
    showCompactBrandHeader: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val injected = LocalCatalogDependencies.current
    val dependencies = remember(injected, context) {
        injected ?: CatalogDependencies.defaultFor(context)
    }
    val accountId by dependencies.sessionManager.currentAccountId.collectAsStateWithLifecycle()
    val sessionGeneration by dependencies.sessionManager.sessionGeneration.collectAsStateWithLifecycle()
    val controller = remember(accountId, sessionGeneration, dependencies) {
        dependencies.feedStore.getOrCreate(
            accountId = accountId,
            sessionGeneration = sessionGeneration,
            dependencies = dependencies,
        )
    }

    CatalogScreen(
        controller = controller,
        dependencies = dependencies,
        navigator = navigator,
        showCompactBrandHeader = showCompactBrandHeader,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun CatalogScreen(
    controller: CatalogFeedController,
    dependencies: CatalogDependencies,
    navigator: AppNavigator,
    showCompactBrandHeader: Boolean,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val locale = LocalAppLocale.current
    val shellWidth = LocalShellWidth.current
    val isTwoPane = shellWidth == ShellWidth.TwoColumn
    val tapGuard = rememberDoubleTapGuard(dependencies.clockMillis)
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(controller) {
        val alreadyLoaded = controller.state.value.hasInitialLoaded
        if (alreadyLoaded) {
            controller.refreshAccessOnReturn()
        } else {
            controller.ensureInitialized()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, controller) {
        var skippedFirstResume = false
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (skippedFirstResume) {
                    controller.refreshAccessOnReturn()
                } else {
                    skippedFirstResume = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val onOpenDetail: (String) -> Unit = { code ->
        if (isTwoPane) {
            controller.selectProduct(code)
        } else {
            tapGuard.run { navigator.openProduct(code) }
        }
    }

    val onOpenCheckout: (String, Int, Long) -> Unit = { code, versionNo, price ->
        tapGuard.run {
            navigator.openCheckout(
                code = code,
                seenVersionNo = versionNo,
                seenPrice = price,
            )
        }
    }

    val onOpenPrepare: (String) -> Unit = { entitlementId ->
        tapGuard.run { navigator.openPrepare(entitlementId) }
    }

    val onOpenAttempt: (String) -> Unit = { attemptId ->
        tapGuard.run { navigator.openAttempt(attemptId) }
    }

    val onOpenMyExams: () -> Unit = {
        tapGuard.run { navigator.switchTab(Destination.MyExams) }
    }

    val listState = remember(controller) {
        LazyListState(
            firstVisibleItemIndex = controller.state.value.savedScrollIndex,
            firstVisibleItemScrollOffset = controller.state.value.savedScrollOffset,
        )
    }

    LaunchedEffect(listState, controller) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                controller.updateScrollPosition(index, offset)
            }
    }

    LaunchedEffect(state.query) {
        if (state.savedScrollIndex == 0 && state.savedScrollOffset == 0 && listState.firstVisibleItemIndex > 0) {
            listState.scrollToItem(0, 0)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(CatalogTestTags.SCREEN),
    ) {
        ListDetailLayout(
            selected = state.selectedProductCode,
            modifier = Modifier.fillMaxSize(),
            list = {
            CatalogListPane(
                state = state,
                listState = listState,
                locale = locale,
                dependencies = dependencies,
                showCompactBrandHeader = showCompactBrandHeader,
                isTwoPane = isTwoPane,
                onOpenFilterSheet = { showFilterSheet = true },
                onSearchChange = controller::onSearchInputChanged,
                onSearchSubmit = controller::submitSearchNow,
                onSearchClear = controller::clearSearchInput,
                onSelectKind = controller::selectKind,
                onClearFilters = controller::clearAllFilters,
                onRefresh = controller::pullToRefresh,
                onRetryInitial = controller::retryInitial,
                onLoadNextPage = controller::loadNextPage,
                onRetryAccess = { code -> controller.retryAccess(listOf(code)) },
                onOpenDetail = onOpenDetail,
                onOpenCheckout = onOpenCheckout,
                onOpenPrepare = onOpenPrepare,
                onOpenAttempt = onOpenAttempt,
                onOpenMyExams = onOpenMyExams,
            )
        },
        detail = { selectedCode ->
            BackHandler(enabled = !isTwoPane) {
                controller.selectProduct(null)
            }
            ProductDetailPane(
                code = selectedCode,
                dependencies = dependencies,
                navigator = navigator,
                showBackButton = !isTwoPane,
                onBack = { controller.selectProduct(null) },
                onNavigateToCatalog = { controller.selectProduct(null) },
                onOpenNestedProduct = { childCode ->
                    if (isTwoPane) {
                        controller.selectProduct(childCode)
                    } else {
                        tapGuard.run { navigator.openProduct(childCode) }
                    }
                },
            )
        },
        emptyDetail = {
            CatalogEmptyDetailPlaceholder()
        },
        )
    }

    if (showFilterSheet) {
        CatalogFilterSheet(
            filters = state.filters,
            query = state.query,
            locale = locale,
            onSelectFormat = controller::selectFormat,
            onSelectPart = controller::selectPart,
            onSelectSubject = controller::selectSubject,
            onClearPartAndSubject = controller::clearPartAndSubject,
            onSelectSort = controller::selectSort,
            onClearAll = controller::clearAllFilters,
            onDismiss = { showFilterSheet = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CatalogListPane(
    state: CatalogFeedState,
    listState: LazyListState,
    locale: AppLocale,
    dependencies: CatalogDependencies,
    showCompactBrandHeader: Boolean,
    isTwoPane: Boolean,
    onOpenFilterSheet: () -> Unit,
    onSearchChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onSearchClear: () -> Unit,
    onSelectKind: (String?) -> Unit,
    onClearFilters: () -> Unit,
    onRefresh: () -> Unit,
    onRetryInitial: () -> Unit,
    onLoadNextPage: () -> Unit,
    onRetryAccess: (String) -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenCheckout: (code: String, versionNo: Int, price: Long) -> Unit,
    onOpenPrepare: (entitlementId: String) -> Unit,
    onOpenAttempt: (attemptId: String) -> Unit,
    onOpenMyExams: () -> Unit,
) {
    val colors = Thi360Theme.colors
    val status = Thi360Theme.statusColors
    val hideFilterBlock = state.loadState is LoadState.Empty && !state.query.isFiltered
    val kindChips = remember(state.filters) { CatalogFilterOptions.kindChips(state.filters) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgPage),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                if (showCompactBrandHeader) {
                    BrandMark(markSize = 28.dp, showName = false)
                }
                Text(
                    text = stringResource(R.string.storefront_catalog_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.text,
                    modifier = Modifier
                        .semantics { heading() }
                        .testTag(ShellTestTags.screenTitle(Destination.Home)),
                )
            }

            if (!hideFilterBlock) {
                val activeCount = state.query.activeFilterSheetCount
                val filterDesc = stringResource(R.string.storefront_catalog_filtersLabel)
                IconButton(
                    onClick = onOpenFilterSheet,
                    modifier = Modifier.testTag(CatalogTestTags.FILTER_BUTTON),
                ) {
                    BadgedBox(
                        badge = {
                            if (activeCount > 0) {
                                Badge(
                                    containerColor = colors.primaryFill,
                                    contentColor = colors.onPrimary,
                                    modifier = Modifier.testTag(CatalogTestTags.FILTER_BADGE),
                                ) {
                                    Text(text = activeCount.toString())
                                }
                            }
                        },
                    ) {
                        Icon(
                            imageVector = CatalogIcons.Filter,
                            contentDescription = filterDesc,
                            tint = colors.text,
                        )
                    }
                }
            }
        }

        if (!hideFilterBlock) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp)
                    .testTag(CatalogTestTags.FILTER_BLOCK),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = state.searchInput,
                    onValueChange = onSearchChange,
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.storefront_catalog_searchLabel)) },
                    placeholder = { Text(text = stringResource(R.string.storefront_catalog_searchPlaceholder)) },
                    leadingIcon = {
                        Icon(
                            imageVector = CatalogIcons.Search,
                            contentDescription = null,
                            tint = colors.textMuted,
                        )
                    },
                    trailingIcon = if (state.searchInput.isNotEmpty()) {
                        {
                            IconButton(
                                onClick = onSearchClear,
                                modifier = Modifier.testTag(CatalogTestTags.SEARCH_CLEAR),
                            ) {
                                Icon(
                                    imageVector = CatalogIcons.Close,
                                    contentDescription = stringResource(R.string.storefront_catalog_clearSearch),
                                    tint = colors.textMuted,
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
                        focusedContainerColor = colors.bgSurface,
                        unfocusedContainerColor = colors.bgSurface,
                        focusedBorderColor = colors.primaryFill,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.text,
                        unfocusedTextColor = colors.text,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(CatalogTestTags.SEARCH_FIELD),
                )

                if (kindChips.isNotEmpty()) {
                    val kindGroupLabel = stringResource(R.string.storefront_catalog_kindLabel)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = kindGroupLabel }
                            .testTag(CatalogTestTags.KIND_ROW),
                    ) {
                        item(key = "__all_kinds__") {
                            CatalogSelectableChip(
                                selected = state.query.kind == null,
                                label = stringResource(R.string.storefront_catalog_kindAll),
                                onClick = { onSelectKind(null) },
                                modifier = Modifier.testTag(CatalogTestTags.KIND_ALL_CHIP),
                            )
                        }
                        items(items = kindChips, key = { it.code }) { kind ->
                            val fullName = CatalogFormatting.pickLocalizedName(kind.nameVi, kind.nameEn, locale)
                                .ifBlank { kind.code }
                            CatalogSelectableChip(
                                selected = state.query.kind == kind.code,
                                label = kind.code,
                                onClick = { onSelectKind(kind.code) },
                                modifier = Modifier
                                    .semantics { contentDescription = fullName }
                                    .testTag(CatalogTestTags.kindChip(kind.code)),
                            )
                        }
                    }
                }

                if (state.query.isFiltered) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(CatalogTestTags.ACTIVE_FILTERS_ROW),
                    ) {
                        state.query.format?.let { fmt ->
                            CatalogChip(text = formatLabel(fmt.apiValue), tone = ChipTone.Primary)
                        }
                        if (!state.query.part.isNullOrBlank()) {
                            val partLabel = state.filters?.parts
                                ?.firstOrNull { it.kindCode == state.query.kind && it.code == state.query.part }
                                ?.name ?: state.query.part
                            CatalogChip(text = partLabel, tone = ChipTone.Primary)
                        }
                        if (!state.query.subject.isNullOrBlank()) {
                            val subjectLabel = state.filters?.subjects
                                ?.firstOrNull { it.code == state.query.subject }
                                ?.let { CatalogFormatting.pickLocalizedName(it.nameVi, it.nameEn, locale) }
                                ?.ifBlank { state.query.subject }
                                ?: state.query.subject
                            CatalogChip(text = subjectLabel, tone = ChipTone.Primary)
                        }
                        if (state.query.sort != CatalogSort.NEWEST) {
                            CatalogChip(
                                text = stringResource(
                                    R.string.storefront_catalog_activeSortChip,
                                    sortLabel(state.query.sort),
                                ),
                                tone = ChipTone.Subtle,
                            )
                        }
                        TextButton(
                            onClick = onClearFilters,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag(CatalogTestTags.CLEAR_FILTERS_BUTTON),
                        ) {
                            Text(
                                text = stringResource(R.string.storefront_catalog_clearFilters),
                                color = colors.primaryText,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }

        if (state.isOffline && state.loadState is LoadState.Loaded) {
            Surface(
                color = status.warningBg,
                border = BorderStroke(1.dp, status.warning),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag(CatalogTestTags.OFFLINE_BANNER),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.common_offline_title),
                        style = MaterialTheme.typography.bodySmall,
                        color = status.warning,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = onRefresh,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(text = stringResource(R.string.common_retry), color = status.warning)
                    }
                }
            }
        }

        if (state.refreshFailed && state.loadState is LoadState.Loaded) {
            Surface(
                color = colors.dangerBg,
                border = BorderStroke(1.dp, colors.dangerText),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag(CatalogTestTags.REFRESH_ERROR_BANNER),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.storefront_catalog_errorMessage),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.dangerText,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = onRefresh,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(text = stringResource(R.string.common_retry), color = colors.dangerText)
                    }
                }
            }
        }

        RefreshableContainer(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when (val load = state.loadState) {
                LoadState.Loading -> CatalogLoadingSkeleton()

                LoadState.Empty -> {
                    if (state.query.isFiltered) {
                        CatalogNoResultsView(onClearFilters = onClearFilters)
                    } else {
                        CatalogEmptyView()
                    }
                }

                is LoadState.Failed -> CatalogErrorView(
                    failed = load,
                    clockMillis = dependencies.clockMillis,
                    onRetry = onRetryInitial,
                )

                is LoadState.Loaded -> {
                    val page = load.value
                    val shouldLoadMore = remember(page.items.size, page.tail, listState) {
                        androidx.compose.runtime.derivedStateOf {
                            if (page.tail != TailState.Idle) return@derivedStateOf false
                            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            lastVisible >= (page.items.size - PAGINATION_PREFETCH_THRESHOLD).coerceAtLeast(0)
                        }
                    }

                    LaunchedEffect(shouldLoadMore.value, page.nextPage) {
                        if (shouldLoadMore.value) {
                            onLoadNextPage()
                        }
                    }

                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag(CatalogTestTags.LIST),
                    ) {
                        item(key = "__showing__") {
                            Text(
                                text = stringResource(
                                    R.string.storefront_catalog_showing,
                                    1,
                                    page.items.size,
                                    page.total.toInt(),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .testTag(CatalogTestTags.SHOWING_SUMMARY),
                            )
                        }

                        items(
                            items = page.items,
                            key = { it.code },
                        ) { item ->
                            val cell = state.accessMap[item.code] ?: AccessCell.Loading
                            ProductCardView(
                                item = item,
                                accessCell = cell,
                                isSelected = isTwoPane && state.selectedProductCode == item.code,
                                locale = locale,
                                apiBaseUrl = dependencies.apiBaseUrl,
                                imageLoader = dependencies.imageLoader,
                                onOpenDetail = onOpenDetail,
                                onRetryAccess = onRetryAccess,
                                onOpenCheckout = onOpenCheckout,
                                onOpenPrepare = onOpenPrepare,
                                onOpenAttempt = onOpenAttempt,
                                onOpenMyExams = onOpenMyExams,
                            )
                        }

                        item(key = "__tail__") {
                            when (val tail = page.tail) {
                                TailState.Loading -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp)
                                            .testTag(CatalogTestTags.TAIL_LOADING),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(
                                            color = colors.primaryFill,
                                            modifier = Modifier.size(28.dp),
                                        )
                                    }
                                }

                                is TailState.Error -> {
                                    CatalogTailErrorCard(
                                        tailError = tail,
                                        clockMillis = dependencies.clockMillis,
                                        onRetry = onLoadNextPage,
                                    )
                                }

                                TailState.Idle, TailState.End -> Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogTailErrorCard(
    tailError: TailState.Error,
    clockMillis: () -> Long,
    onRetry: () -> Unit,
) {
    val colors = Thi360Theme.colors
    val blockedUntilMillis = remember(tailError) {
        val wait = tailError.retryAfterSeconds
        if (wait != null && wait > 0L) clockMillis() + wait * 1_000L else 0L
    }
    var tick by remember(tailError) { mutableIntStateOf(0) }
    LaunchedEffect(tailError, blockedUntilMillis) {
        while (blockedUntilMillis > 0L && clockMillis() < blockedUntilMillis) {
            withContext(Dispatchers.Default) {
                delay(RETRY_POLL_INTERVAL_MS)
            }
            tick++
        }
    }
    val retryAllowedNow = tick >= 0 && (blockedUntilMillis <= 0L || clockMillis() >= blockedUntilMillis)

    Surface(
        color = colors.bgSurface,
        border = BorderStroke(1.dp, colors.border),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag(CatalogTestTags.TAIL_ERROR),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (tailError.isOffline) {
                Text(
                    text = stringResource(R.string.common_offline_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.text,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.common_offline_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    text = stringResource(R.string.storefront_catalog_errorMessage),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            OutlinedButton(
                onClick = {
                    if (blockedUntilMillis <= 0L || clockMillis() >= blockedUntilMillis) {
                        onRetry()
                    }
                },
                enabled = retryAllowedNow,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(CatalogTestTags.TAIL_RETRY),
            ) {
                Text(text = stringResource(R.string.common_retry))
            }
        }
    }
}

@Composable
private fun CatalogLoadingSkeleton() {
    val colors = Thi360Theme.colors
    val loadingLabel = stringResource(R.string.storefront_catalog_loading)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { contentDescription = loadingLabel }
            .testTag(CatalogTestTags.LOADING_SKELETON),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(3) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.bgSurface,
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(colors.bgSubtle),
                    )
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.55f)
                                .height(16.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.bgSubtle),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .height(20.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.bgSubtle),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.bgSubtle),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogEmptyView() {
    val colors = Thi360Theme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag(CatalogTestTags.EMPTY_CATALOG),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.storefront_catalog_emptyTitle),
            style = MaterialTheme.typography.titleMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CatalogNoResultsView(
    onClearFilters: () -> Unit,
) {
    val colors = Thi360Theme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag(CatalogTestTags.NO_RESULTS),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 360.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.storefront_catalog_noResultsTitle),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.text,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.storefront_catalog_noResultsDescription),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
            )
            OutlinedButton(
                onClick = onClearFilters,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(CatalogTestTags.NO_RESULTS_CLEAR),
            ) {
                Text(text = stringResource(R.string.storefront_catalog_clearFilters))
            }
        }
    }
}

@Composable
private fun CatalogErrorView(
    failed: LoadState.Failed,
    clockMillis: () -> Long,
    onRetry: () -> Unit,
) {
    val colors = Thi360Theme.colors
    val blockedUntilMillis = remember(failed) {
        val wait = failed.retryAfter
        if (wait != null && wait > 0L) clockMillis() + wait * 1_000L else 0L
    }
    var tick by remember(failed) { mutableIntStateOf(0) }
    LaunchedEffect(failed, blockedUntilMillis) {
        while (blockedUntilMillis > 0L && clockMillis() < blockedUntilMillis) {
            withContext(Dispatchers.Default) { delay(RETRY_POLL_INTERVAL_MS) }
            tick++
        }
    }
    val retryAllowed = tick >= 0 && (blockedUntilMillis <= 0L || clockMillis() >= blockedUntilMillis)

    val title = if (failed.kind == LoadFailureKind.Offline) {
        stringResource(R.string.common_offline_title)
    } else {
        stringResource(R.string.storefront_catalog_errorMessage)
    }
    val description = if (failed.kind == LoadFailureKind.Offline) {
        stringResource(R.string.common_offline_description)
    } else {
        null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag(CatalogTestTags.ERROR_STATE),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 380.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.text,
                textAlign = TextAlign.Center,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center,
                )
            }
            Button(
                onClick = {
                    if (blockedUntilMillis <= 0L || clockMillis() >= blockedUntilMillis) {
                        onRetry()
                    }
                },
                enabled = retryAllowed,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primaryFill,
                    contentColor = colors.onPrimary,
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(CatalogTestTags.ERROR_RETRY),
            ) {
                Text(text = stringResource(R.string.common_retry))
            }
        }
    }
}

@Composable
private fun CatalogEmptyDetailPlaceholder() {
    val colors = Thi360Theme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgPage)
            .padding(24.dp)
            .semantics(mergeDescendants = true) {}
            .testTag(CatalogTestTags.EMPTY_DETAIL_PLACEHOLDER),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.storefront_catalog_selectPlaceholder),
            style = MaterialTheme.typography.titleMedium,
            color = colors.textMuted,
            textAlign = TextAlign.Center,
        )
    }
}

private const val PAGINATION_PREFETCH_THRESHOLD = 3
private const val RETRY_POLL_INTERVAL_MS = 250L
