package vn.unicom.thi360.feature.payment.ui

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.unicom.thi360.R
import vn.unicom.thi360.core.config.SupportContact
import vn.unicom.thi360.core.network.generated.model.OrderSummary
import vn.unicom.thi360.core.theme.LocalAppLocale
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.core.ui.ListDetailLayout
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.core.ui.LocalShellWidth
import vn.unicom.thi360.core.ui.RefreshableContainer
import vn.unicom.thi360.core.ui.ShellWidth
import vn.unicom.thi360.core.ui.StaleBanner
import vn.unicom.thi360.feature.payment.domain.PaymentBrowser
import vn.unicom.thi360.feature.payment.domain.PaymentFormatters
import vn.unicom.thi360.feature.payment.domain.PaymentStatusTone
import vn.unicom.thi360.feature.payment.viewmodel.OrdersListUiState
import vn.unicom.thi360.feature.payment.viewmodel.OrdersViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    viewModel: OrdersViewModel,
    paymentBrowser: PaymentBrowser,
    supportContact: SupportContact,
    onBack: (() -> Unit)?,
    onOpenOrder: (String) -> Unit,
    onOpenMyExams: () -> Unit,
    onOpenCheckout: (String) -> Unit,
    onOpenCatalog: () -> Unit,
    onSwitchAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }

    DisposableEffect(lifecycleOwner, viewModel) {
        var skippedFirstResume = false
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (skippedFirstResume) {
                    viewModel.onScreenResumed()
                } else {
                    skippedFirstResume = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(state.browserEvent) {
        val event = state.browserEvent ?: return@LaunchedEffect
        viewModel.consumeBrowserEvent()
        val opened = paymentBrowser.open(context, event.resolvedUrl)
        if (!opened) {
            val productCode = (state.orders as? LoadState.Loaded)?.value
                ?.firstOrNull { it.code == event.orderCode }
                ?.productCode
                .orEmpty()
            viewModel.onBrowserOpenFailed(event.orderCode, productCode)
        }
    }

    LaunchedEffect(state.snackbarCancelledOrderCode) {
        val cancelledCode = state.snackbarCancelledOrderCode ?: return@LaunchedEffect
        try {
            snackbarHostState.showSnackbar(
                resources.getString(R.string.orders_cancelDialog_success, cancelledCode),
            )
        } finally {
            viewModel.consumeSnackbar()
        }
    }

    OrdersScreenContent(
        state = state,
        snackbarHostState = snackbarHostState,
        supportContact = supportContact,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onLoadNextPage = viewModel::loadNextPage,
        onSelectOrderInTwoPane = { code -> viewModel.selectOrder(code) },
        onOpenOrderSinglePane = onOpenOrder,
        onReloadSelectedDetail = viewModel::reloadSelectedDetail,
        onPayOrder = viewModel::payOrder,
        onOpenCancelDialog = viewModel::openCancelDialog,
        onConfirmCancel = viewModel::confirmCancelOrder,
        onDismissCancelDialog = viewModel::dismissCancelDialog,
        onOpenMyExams = onOpenMyExams,
        onOpenCheckout = onOpenCheckout,
        onOpenCatalog = onOpenCatalog,
        onSwitchAccount = onSwitchAccount,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreenContent(
    state: OrdersListUiState,
    snackbarHostState: SnackbarHostState,
    supportContact: SupportContact,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onLoadNextPage: () -> Unit,
    onSelectOrderInTwoPane: (String?) -> Unit,
    onOpenOrderSinglePane: (String) -> Unit,
    onReloadSelectedDetail: () -> Unit,
    onPayOrder: (orderCode: String, productCode: String) -> Unit,
    onOpenCancelDialog: (String) -> Unit,
    onConfirmCancel: () -> Unit,
    onDismissCancelDialog: () -> Unit,
    onOpenMyExams: () -> Unit,
    onOpenCheckout: (String) -> Unit,
    onOpenCatalog: () -> Unit,
    onSwitchAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    val listState = rememberLazyListState()

    Scaffold(
        containerColor = colors.bgPage,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.testTag(PaymentTestTags.ORDERS_SNACKBAR_HOST),
            )
        },
        modifier = modifier.testTag(PaymentTestTags.ORDERS_SCREEN),
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val parentShellWidth = LocalShellWidth.current
            val canFitTwoColumns = parentShellWidth == ShellWidth.TwoColumn && maxWidth >= 680.dp
            val effectiveShellWidth = if (canFitTwoColumns) ShellWidth.TwoColumn else ShellWidth.Narrow
            val listPaneWidth = if (maxWidth < 860.dp) 300.dp else 340.dp

            LaunchedEffect(canFitTwoColumns, state.orders) {
                val items = (state.orders as? LoadState.Loaded)?.value.orEmpty()
                if (canFitTwoColumns && state.selectedOrderCode == null && items.isNotEmpty()) {
                    onSelectOrderInTwoPane(items.first().code)
                }
            }

            CompositionLocalProvider(LocalShellWidth provides effectiveShellWidth) {
                ListDetailLayout(
                    selected = if (canFitTwoColumns) state.selectedOrderCode else null,
                    listPaneWidth = listPaneWidth,
                    list = {
                        OrdersListPane(
                            state = state,
                            listState = listState,
                            supportContact = supportContact,
                            onBack = onBack,
                            onRefresh = onRefresh,
                            onLoadNextPage = onLoadNextPage,
                            onOrderClick = { order ->
                                if (canFitTwoColumns) {
                                    onSelectOrderInTwoPane(order.code)
                                } else {
                                    onOpenOrderSinglePane(order.code)
                                }
                            },
                            onPayOrder = onPayOrder,
                            onCancelOrder = onOpenCancelDialog,
                            onOpenMyExams = onOpenMyExams,
                            onOpenCheckout = onOpenCheckout,
                            onOpenCatalog = onOpenCatalog,
                        )
                    },
                    detail = { selectedCode ->
                        val detailState = state.selectedDetail ?: LoadState.Loading
                        val loadedDetail = (detailState as? LoadState.Loaded)?.value
                        OrderDetailPaneContent(
                            orderState = detailState,
                            lastLoadedAtMillis = state.selectedDetailLastLoadedAtMillis,
                            supportContact = supportContact,
                            paying = state.payingOrderCode == selectedCode,
                            actionsDisabled = state.anyActionInFlight,
                            actionError = state.actionError,
                            onRetry = onReloadSelectedDetail,
                            onPayOrder = {
                                val productCode = loadedDetail?.productCode
                                    ?: (state.orders as? LoadState.Loaded)?.value
                                        ?.firstOrNull { it.code == selectedCode }
                                        ?.productCode
                                        .orEmpty()
                                onPayOrder(selectedCode, productCode)
                            },
                            onCancelOrder = { onOpenCancelDialog(selectedCode) },
                            onOpenMyExams = onOpenMyExams,
                            onOpenOrder = { nextCode -> onSelectOrderInTwoPane(nextCode) },
                            onOpenOrders = { onSelectOrderInTwoPane(null) },
                            onOpenCheckout = onOpenCheckout,
                            onOpenCatalog = onOpenCatalog,
                            onSwitchAccount = onSwitchAccount,
                        )
                    },
                    emptyDetail = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.orders_detail_selectPrompt),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textMuted,
                                textAlign = TextAlign.Center,
                            )
                        }
                    },
                )
            }
        }
    }

    state.cancelDialog?.let { dialogState ->
        CancelOrderConfirmDialog(
            state = dialogState,
            onConfirm = onConfirmCancel,
            onDismiss = onDismissCancelDialog,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrdersListPane(
    state: OrdersListUiState,
    listState: LazyListState,
    supportContact: SupportContact,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onLoadNextPage: () -> Unit,
    onOrderClick: (OrderSummary) -> Unit,
    onPayOrder: (orderCode: String, productCode: String) -> Unit,
    onCancelOrder: (String) -> Unit,
    onOpenMyExams: () -> Unit,
    onOpenCheckout: (String) -> Unit,
    onOpenCatalog: () -> Unit,
) {
    val colors = Thi360Theme.colors
    val shouldLoadMore by remember(listState, state.hasMorePages, state.isLoadingMore) {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            state.hasMorePages && !state.isLoadingMore && totalItems > 0 && lastVisible >= totalItems - 3
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            onLoadNextPage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgPage),
    ) {
        if (onBack != null) {
            PaymentTopBar(
                title = stringResource(R.string.orders_pageTitle),
                onBack = onBack,
            )
        }

        RefreshableContainer(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when (val ordersState = state.orders) {
                LoadState.Loading -> {
                    val loadingDesc = stringResource(R.string.common_loading)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .semantics { contentDescription = loadingDesc }
                            .testTag(PaymentTestTags.ORDERS_SKELETON),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OrdersHeader(username = state.username, showTitle = onBack == null)
                        repeat(4) {
                            PaymentSkeletonBlock(height = 96.dp)
                        }
                    }
                }

                LoadState.Empty -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .testTag(PaymentTestTags.ORDERS_EMPTY_STATE),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        OrdersHeader(username = state.username, showTitle = onBack == null)
                        PaymentCard(contentPadding = 24.dp) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.orders_emptyTitle),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.text,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    text = stringResource(R.string.orders_emptyDescription),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textMuted,
                                    textAlign = TextAlign.Center,
                                )
                                PaymentPrimaryButton(
                                    text = stringResource(R.string.orders_exploreExams),
                                    onClick = onOpenCatalog,
                                    modifier = Modifier.testTag(PaymentTestTags.ORDERS_EXPLORE_BUTTON),
                                )
                            }
                        }
                    }
                }

                is LoadState.Failed -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .testTag(PaymentTestTags.ORDERS_ERROR_STATE),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        OrdersHeader(username = state.username, showTitle = onBack == null)
                        PaymentCard(contentPadding = 24.dp) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.orders_errorTitle),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.text,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    text = stringResource(R.string.orders_errorDescription),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textMuted,
                                    textAlign = TextAlign.Center,
                                )
                                PaymentSecondaryButton(
                                    text = stringResource(R.string.common_retry),
                                    onClick = onRefresh,
                                )
                            }
                        }
                    }
                }

                is LoadState.Loaded -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag(PaymentTestTags.ORDERS_LIST),
                    ) {
                        item(key = "header") {
                            OrdersHeader(username = state.username, showTitle = onBack == null)
                        }

                        if (ordersState.stale) {
                            item(key = "stale_banner") {
                                val desc = state.lastLoadedAtMillis?.let { ts ->
                                    stringResource(
                                        R.string.orders_staleDescription,
                                        PaymentFormatters.formatEpochTimeOnlyVn(ts),
                                    )
                                } ?: stringResource(R.string.orders_staleDescriptionNoTime)
                                StaleBanner(
                                    message = "${stringResource(R.string.orders_staleTitle)} $desc",
                                    onRetry = onRefresh,
                                )
                            }
                        }

                        state.actionError?.let { errorBanner ->
                            item(key = "action_error") {
                                OrderActionErrorView(
                                    banner = errorBanner,
                                    showCreateNew = true,
                                    onCreateNew = onOpenCheckout,
                                    onToCatalog = onOpenCatalog,
                                    modifier = Modifier.testTag(PaymentTestTags.ORDERS_ACTION_ERROR_BANNER),
                                )
                            }
                        }

                        items(
                            items = ordersState.value,
                            key = { it.id },
                        ) { order ->
                            OrderSummaryRowCard(
                                order = order,
                                selected = order.code == state.selectedOrderCode,
                                isPaying = state.payingOrderCode == order.code,
                                anyActionInFlight = state.anyActionInFlight,
                                supportContact = supportContact,
                                onClick = { onOrderClick(order) },
                                onPay = { onPayOrder(order.code, order.productCode) },
                                onCancel = { onCancelOrder(order.code) },
                                onOpenMyExams = onOpenMyExams,
                            )
                        }

                        if (state.isLoadingMore) {
                            item(key = "loading_more") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(color = colors.primaryFill)
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
private fun OrdersHeader(
    username: String,
    showTitle: Boolean,
) {
    val colors = Thi360Theme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showTitle) {
            Text(
                text = stringResource(R.string.orders_pageTitle),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colors.text,
                modifier = Modifier.semantics { heading() },
            )
        }
        if (username.isNotBlank()) {
            Text(
                text = stringResource(R.string.orders_subtitle, username),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrderSummaryRowCard(
    order: OrderSummary,
    selected: Boolean,
    isPaying: Boolean,
    anyActionInFlight: Boolean,
    supportContact: SupportContact,
    onClick: () -> Unit,
    onPay: () -> Unit,
    onCancel: () -> Unit,
    onOpenMyExams: () -> Unit,
) {
    val colors = Thi360Theme.colors
    val locale = LocalAppLocale.current
    val resources = LocalResources.current
    val supportSummary = PaymentFormatters.formatSupportSummary(supportContact)

    val amountText = when {
        (order.paymentMethod == OrderSummary.PaymentMethod.FREE || order.amountDueVnd == 0L) &&
            order.voucherDiscountVnd > 0L -> stringResource(R.string.orders_amount_freeVoucher)
        order.paymentMethod == OrderSummary.PaymentMethod.FREE || order.amountDueVnd == 0L ->
            stringResource(R.string.orders_amount_freeAmount)
        else -> PaymentFormatters.formatPrice(order.amountDueVnd, locale, resources)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) colors.primaryContainer.copy(alpha = 0.35f) else colors.bgSurface,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) colors.primaryFill else colors.border,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(PaymentTestTags.orderRow(order.code)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = order.code,
                    style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Bold,
                    color = colors.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                )
                PaymentStatusChip(
                    label = PaymentFormatters.formatStateName(order.state.value, resources),
                    tone = PaymentFormatters.orderSummaryStateTone(order.state),
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (order.productType == OrderSummary.ProductType.BUNDLE) {
                    PaymentStatusChip(
                        label = stringResource(R.string.orders_bundleBadge),
                        tone = PaymentStatusTone.Info,
                    )
                }
                Text(
                    text = order.productName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = colors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = PaymentFormatters.formatDateTimeVn(order.createdAt),
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = colors.textMuted,
                )
                Text(
                    text = amountText,
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold,
                    color = colors.text,
                )
            }

            if (order.state == OrderSummary.State.PENDING_PAYMENT && order.expiresAt != null) {
                Text(
                    text = stringResource(
                        R.string.orders_pendingHoldUntil,
                        PaymentFormatters.formatTimeOnlyVn(order.expiresAt),
                    ),
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = colors.textMuted,
                )
            }

            if (order.state == OrderSummary.State.PAID) {
                Text(
                    text = stringResource(R.string.orders_stateSubtext_PAID),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = colors.text,
                )
                val paidSupport = if (supportSummary != null) {
                    stringResource(R.string.orders_paidCheckingSupport, supportSummary, order.code)
                } else {
                    stringResource(
                        R.string.orders_paidCheckingSupportPending,
                        order.code,
                        stringResource(R.string.landing_support_pending),
                    )
                }
                Text(
                    text = paidSupport,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }

            when (order.state) {
                OrderSummary.State.PENDING_PAYMENT -> {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PaymentPrimaryButton(
                            text = stringResource(R.string.orders_actions_continuePayment),
                            onClick = onPay,
                            enabled = !anyActionInFlight,
                            loading = isPaying,
                            modifier = Modifier.testTag(PaymentTestTags.orderContinueButton(order.code)),
                        )
                        PaymentSecondaryButton(
                            text = stringResource(R.string.orders_actions_cancel),
                            onClick = onCancel,
                            enabled = !anyActionInFlight,
                            modifier = Modifier.testTag(PaymentTestTags.orderCancelButton(order.code)),
                        )
                    }
                }

                OrderSummary.State.FULFILLED -> {
                    PaymentSecondaryButton(
                        text = stringResource(R.string.orders_actions_goMyExams),
                        onClick = onOpenMyExams,
                        modifier = Modifier.testTag(PaymentTestTags.orderGoMyExamsButton(order.code)),
                    )
                }

                else -> Unit
            }
        }
    }
}
