package vn.unicom.thi360.feature.payment.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.unicom.thi360.R
import vn.unicom.thi360.core.config.SupportContact
import vn.unicom.thi360.core.network.generated.model.OrderDetail
import vn.unicom.thi360.core.network.generated.model.OrderHistoryView
import vn.unicom.thi360.core.network.generated.model.OrderPaymentView
import vn.unicom.thi360.core.theme.LocalAppLocale
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.core.ui.LoadFailureKind
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.core.ui.RefreshableContainer
import vn.unicom.thi360.core.ui.StaleBanner
import vn.unicom.thi360.feature.payment.domain.HistoryActorMapper
import vn.unicom.thi360.feature.payment.domain.HistoryReasonParser
import vn.unicom.thi360.feature.payment.domain.PaymentBrowser
import vn.unicom.thi360.feature.payment.domain.PaymentFormatters
import vn.unicom.thi360.feature.payment.domain.PaymentStatusTone
import vn.unicom.thi360.feature.payment.domain.VnpayReasonMapper
import vn.unicom.thi360.feature.payment.viewmodel.OrderActionErrorBanner
import vn.unicom.thi360.feature.payment.viewmodel.OrderDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    viewModel: OrderDetailViewModel,
    paymentBrowser: PaymentBrowser,
    supportContact: SupportContact,
    onBack: () -> Unit,
    onOpenOrders: () -> Unit,
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
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.browserEvent) {
        val event = state.browserEvent ?: return@LaunchedEffect
        viewModel.consumeBrowserEvent()
        val opened = paymentBrowser.open(context, event.resolvedUrl)
        if (!opened) {
            viewModel.onBrowserOpenFailed(event.orderCode)
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

    OrderDetailScreenContent(
        orderState = state.order,
        isRefreshing = state.isRefreshing,
        lastLoadedAtMillis = state.lastLoadedAtMillis,
        supportContact = supportContact,
        paying = state.paying,
        actionsDisabled = state.anyActionInFlight,
        actionError = state.actionError,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onPayOrder = viewModel::payOrder,
        onCancelOrder = viewModel::openCancelDialog,
        onOpenMyExams = onOpenMyExams,
        onOpenOrder = onOpenOrder,
        onOpenOrders = onOpenOrders,
        onOpenCheckout = onOpenCheckout,
        onOpenCatalog = onOpenCatalog,
        onSwitchAccount = onSwitchAccount,
        modifier = modifier,
    )

    state.cancelDialog?.let { dialogState ->
        CancelOrderConfirmDialog(
            state = dialogState,
            onConfirm = viewModel::confirmCancelOrder,
            onDismiss = viewModel::dismissCancelDialog,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OrderDetailScreenContent(
    orderState: LoadState<OrderDetail>,
    isRefreshing: Boolean,
    lastLoadedAtMillis: Long?,
    supportContact: SupportContact,
    paying: Boolean,
    actionsDisabled: Boolean,
    actionError: OrderActionErrorBanner?,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onPayOrder: () -> Unit,
    onCancelOrder: () -> Unit,
    onOpenMyExams: () -> Unit,
    onOpenOrder: (String) -> Unit,
    onOpenOrders: () -> Unit,
    onOpenCheckout: (String) -> Unit,
    onOpenCatalog: () -> Unit,
    onSwitchAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        containerColor = Thi360Theme.colors.bgPage,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.testTag(PaymentTestTags.ORDERS_SNACKBAR_HOST),
            )
        },
        modifier = modifier.testTag(PaymentTestTags.ORDER_DETAIL_SCREEN),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            PaymentTopBar(
                title = stringResource(R.string.orders_backToList),
                onBack = onBack,
                backButtonTestTag = PaymentTestTags.ORDER_DETAIL_BACK_BUTTON,
            )
            RefreshableContainer(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                OrderDetailPaneContent(
                    orderState = orderState,
                    lastLoadedAtMillis = lastLoadedAtMillis,
                    supportContact = supportContact,
                    paying = paying,
                    actionsDisabled = actionsDisabled,
                    actionError = actionError,
                    onRetry = onRefresh,
                    onPayOrder = onPayOrder,
                    onCancelOrder = onCancelOrder,
                    onOpenMyExams = onOpenMyExams,
                    onOpenOrder = onOpenOrder,
                    onOpenOrders = onOpenOrders,
                    onOpenCheckout = onOpenCheckout,
                    onOpenCatalog = onOpenCatalog,
                    onSwitchAccount = onSwitchAccount,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OrderDetailPaneContent(
    orderState: LoadState<OrderDetail>,
    lastLoadedAtMillis: Long?,
    supportContact: SupportContact,
    paying: Boolean,
    actionsDisabled: Boolean,
    actionError: OrderActionErrorBanner?,
    onRetry: () -> Unit,
    onPayOrder: () -> Unit,
    onCancelOrder: () -> Unit,
    onOpenMyExams: () -> Unit,
    onOpenOrder: (String) -> Unit,
    onOpenOrders: () -> Unit,
    onOpenCheckout: (String) -> Unit,
    onOpenCatalog: () -> Unit,
    onSwitchAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    val locale = LocalAppLocale.current
    val resources = LocalResources.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgPage)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (orderState) {
            LoadState.Loading -> {
                val loadingDesc = stringResource(R.string.common_loading)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = loadingDesc },
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PaymentSkeletonBlock(height = 28.dp, widthFraction = 0.6f)
                    PaymentSkeletonBlock(height = 180.dp)
                    PaymentSkeletonBlock(height = 120.dp)
                }
            }

            is LoadState.Failed -> {
                if (orderState.kind == LoadFailureKind.NotFound) {
                    PaymentCard(
                        borderColor = colors.dangerText.copy(alpha = 0.35f),
                        modifier = Modifier.testTag(PaymentTestTags.ORDER_DETAIL_NOT_FOUND),
                    ) {
                        Text(
                            text = stringResource(R.string.orders_notFound_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.text,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            text = stringResource(R.string.orders_notFound_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PaymentSecondaryButton(
                                text = stringResource(R.string.orders_notFound_otherAccount),
                                onClick = onSwitchAccount,
                                modifier = Modifier.testTag(PaymentTestTags.ORDER_DETAIL_SWITCH_ACCOUNT),
                            )
                            PaymentPrimaryButton(
                                text = stringResource(R.string.orders_notFound_backToOrders),
                                onClick = onOpenOrders,
                                modifier = Modifier.testTag(PaymentTestTags.ORDER_DETAIL_BACK_TO_ORDERS),
                            )
                        }
                    }
                } else {
                    PaymentCard(modifier = Modifier.testTag(PaymentTestTags.ORDER_DETAIL_ERROR)) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.orders_detail_errorTitle),
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
                                onClick = onRetry,
                            )
                        }
                    }
                }
            }

            LoadState.Empty -> Unit

            is LoadState.Loaded -> {
                val order = orderState.value
                val supportSummary = PaymentFormatters.formatSupportSummary(supportContact)

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.orders_detail_heading, order.code),
                        style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.Bold,
                        color = colors.text,
                        modifier = Modifier
                            .semantics { heading() }
                            .testTag(PaymentTestTags.ORDER_DETAIL_HEADING),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        PaymentStatusChip(
                            label = PaymentFormatters.formatStateName(order.state.value, resources),
                            tone = PaymentFormatters.orderStateTone(order.state),
                            modifier = Modifier.testTag(PaymentTestTags.ORDER_DETAIL_STATE_CHIP),
                        )
                        val methodLabel = when {
                            order.paymentMethod == OrderDetail.PaymentMethod.FREE &&
                                order.voucherDiscountVnd > 0 ->
                                stringResource(R.string.orders_amount_freeVoucherNote)
                            order.paymentMethod == OrderDetail.PaymentMethod.FREE ->
                                stringResource(R.string.orders_amount_free)
                            else -> stringResource(R.string.orders_amount_vnpay)
                        }
                        PaymentStatusChip(label = methodLabel, tone = PaymentStatusTone.Neutral)
                        if (order.productType == OrderDetail.ProductType.BUNDLE) {
                            PaymentStatusChip(
                                label = stringResource(R.string.orders_bundleBadge),
                                tone = PaymentStatusTone.Info,
                            )
                        }
                    }

                    OrderDetailActionsRow(
                        order = order,
                        paying = paying,
                        actionsDisabled = actionsDisabled,
                        onPayOrder = onPayOrder,
                        onCancelOrder = onCancelOrder,
                        onOpenMyExams = onOpenMyExams,
                        onOpenOrder = onOpenOrder,
                    )
                }

                if (orderState.stale) {
                    val staleDescription = lastLoadedAtMillis?.let { ts ->
                        stringResource(
                            R.string.orders_staleDescription,
                            PaymentFormatters.formatEpochTimeOnlyVn(ts),
                        )
                    } ?: stringResource(R.string.orders_staleDescriptionNoTime)
                    StaleBanner(
                        message = "${stringResource(R.string.orders_staleTitle)} $staleDescription",
                        onRetry = onRetry,
                    )
                }

                if (actionError != null) {
                    OrderActionErrorView(
                        banner = actionError,
                        showCreateNew = true,
                        onCreateNew = onOpenCheckout,
                        onToCatalog = onOpenCatalog,
                        modifier = Modifier.testTag(PaymentTestTags.ORDER_DETAIL_ACTION_ERROR),
                        createNewTestTag = PaymentTestTags.ORDER_DETAIL_CREATE_NEW_BUTTON,
                        toCatalogTestTag = PaymentTestTags.ORDER_DETAIL_TO_CATALOG_BUTTON,
                    )
                }

                if (order.state == OrderDetail.State.PAID) {
                    val paidSupportBody = if (supportSummary != null) {
                        stringResource(R.string.orders_paidCheckingSupport, supportSummary, order.code)
                    } else {
                        stringResource(
                            R.string.orders_paidCheckingSupportPending,
                            order.code,
                            stringResource(R.string.landing_support_pending),
                        )
                    }
                    PaymentBanner(
                        tone = PaymentStatusTone.Warning,
                        title = stringResource(R.string.orders_paidChecking, order.code),
                        body = paidSupportBody,
                    )
                }

                OrderSnapshotSection(order = order, locale = locale)
                OrderPaymentsSection(order = order, locale = locale)
                OrderHistorySection(history = order.history)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrderDetailActionsRow(
    order: OrderDetail,
    paying: Boolean,
    actionsDisabled: Boolean,
    onPayOrder: () -> Unit,
    onCancelOrder: () -> Unit,
    onOpenMyExams: () -> Unit,
    onOpenOrder: (String) -> Unit,
) {
    when (order.state) {
        OrderDetail.State.PENDING_PAYMENT -> {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PaymentPrimaryButton(
                    text = stringResource(R.string.orders_actions_continuePayment),
                    onClick = onPayOrder,
                    enabled = !actionsDisabled,
                    loading = paying,
                    modifier = Modifier.testTag(PaymentTestTags.ORDER_DETAIL_CONTINUE_BUTTON),
                )
                PaymentSecondaryButton(
                    text = stringResource(R.string.orders_actions_cancel),
                    onClick = onCancelOrder,
                    enabled = !actionsDisabled,
                    modifier = Modifier.testTag(PaymentTestTags.ORDER_DETAIL_CANCEL_BUTTON),
                )
            }
        }

        OrderDetail.State.FULFILLED -> {
            PaymentPrimaryButton(
                text = stringResource(R.string.orders_actions_goMyExams),
                onClick = onOpenMyExams,
                modifier = Modifier.testTag(PaymentTestTags.ORDER_DETAIL_GO_MY_EXAMS_BUTTON),
            )
        }

        OrderDetail.State.REPLACED -> {
            val replacement = order.replacedByOrderCode?.takeIf { it.isNotBlank() }
            if (replacement != null) {
                PaymentSecondaryButton(
                    text = stringResource(R.string.orders_actions_viewReplacement, replacement),
                    onClick = { onOpenOrder(replacement) },
                    modifier = Modifier.testTag(PaymentTestTags.ORDER_DETAIL_REPLACEMENT_BUTTON),
                )
            }
        }

        else -> Unit
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrderSnapshotSection(
    order: OrderDetail,
    locale: vn.unicom.thi360.core.preferences.AppLocale,
) {
    val colors = Thi360Theme.colors
    val resources = LocalResources.current
    val benefits = PaymentFormatters.readBenefitsSnapshot(order.benefits)
    val versionNo = order.bundleVersionNo ?: order.examVersionNo
    val amountDueText = when {
        (order.paymentMethod == OrderDetail.PaymentMethod.FREE || order.amountDueVnd == 0L) &&
            order.voucherDiscountVnd > 0L -> stringResource(R.string.orders_amount_freeVoucher)
        order.paymentMethod == OrderDetail.PaymentMethod.FREE || order.amountDueVnd == 0L ->
            stringResource(R.string.orders_amount_freeAmount)
        else -> PaymentFormatters.formatPrice(order.amountDueVnd, locale, resources)
    }

    PaymentCard(modifier = Modifier.testTag(PaymentTestTags.ORDER_DETAIL_SNAPSHOT_CARD)) {
        Text(
            text = stringResource(R.string.orders_detail_snapshotTitle),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = colors.text,
            modifier = Modifier.semantics { heading() },
        )

        OrderFieldRow(
            label = stringResource(R.string.orders_detail_fields_buyer),
            value = "${order.buyerUsername} · ${order.buyerFullName}",
        )
        OrderFieldRow(
            label = stringResource(R.string.orders_detail_fields_product),
            value = "${order.productName} · ${order.productCode}",
        )
        OrderFieldRow(
            label = stringResource(R.string.orders_detail_fields_version),
            value = versionNo?.let { "v$it" } ?: "—",
        )
        OrderFieldRow(
            label = stringResource(R.string.orders_detail_fields_listPrice),
            value = PaymentFormatters.formatPrice(order.listPriceVnd, locale, resources),
        )
        if (order.promotionDiscountVnd > 0L) {
            OrderFieldRow(
                label = stringResource(R.string.orders_detail_fields_promotion),
                value = "−${PaymentFormatters.formatPrice(order.promotionDiscountVnd, locale, resources)}",
            )
        }
        val voucherCode = order.voucherCode?.takeIf { it.isNotBlank() }
        if (voucherCode != null) {
            val voucherVal = if (order.voucherDiscountVnd > 0L) {
                "$voucherCode −${PaymentFormatters.formatPrice(order.voucherDiscountVnd, locale, resources)}"
            } else {
                voucherCode
            }
            OrderFieldRow(
                label = stringResource(R.string.orders_detail_fields_voucher),
                value = voucherVal,
            )
        }
        OrderFieldRow(
            label = stringResource(R.string.orders_detail_fields_amountDue),
            value = amountDueText,
            emphasized = true,
        )
        OrderFieldRow(
            label = stringResource(R.string.orders_detail_fields_attempts),
            value = pluralStringResource(
                R.plurals.orders_detail_attemptsValue,
                order.attemptsGranted,
                order.attemptsGranted,
            ),
        )
        OrderFieldRow(
            label = stringResource(R.string.orders_detail_fields_createdAt),
            value = PaymentFormatters.formatDateTimeVn(order.createdAt),
        )
        order.expiresAt?.let { expiresAt ->
            OrderFieldRow(
                label = stringResource(R.string.orders_detail_fields_expiresAt),
                value = PaymentFormatters.formatDateTimeVn(expiresAt),
            )
        }
        order.paidAt?.let { paidAt ->
            OrderFieldRow(
                label = stringResource(R.string.orders_detail_fields_paidAt),
                value = PaymentFormatters.formatDateTimeVn(paidAt),
            )
        }
        order.fulfilledAt?.let { fulfilledAt ->
            OrderFieldRow(
                label = stringResource(R.string.orders_detail_fields_fulfilledAt),
                value = PaymentFormatters.formatDateTimeVn(fulfilledAt),
            )
        }

        HorizontalDivider(color = colors.border)

        Text(
            text = stringResource(R.string.orders_detail_fields_benefits),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.text,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "${stringResource(R.string.orders_detail_benefits_resultView)}: ${
                    PaymentFormatters.formatResultView(order.resultView, resources)
                }",
                style = MaterialTheme.typography.bodySmall,
                color = colors.text,
            )
            OrderBenefitLine(
                label = stringResource(R.string.orders_detail_benefits_ai),
                enabled = benefits.aiExplanation,
            )
            OrderBenefitLine(
                label = stringResource(R.string.orders_detail_benefits_strengths),
                enabled = benefits.strengthWeakness,
            )
            OrderBenefitLine(
                label = stringResource(R.string.orders_detail_benefits_practice),
                enabled = benefits.practice,
            )
        }
        benefits.note?.let { note ->
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
        }

        if (order.items.isNotEmpty() &&
            (order.productType == OrderDetail.ProductType.BUNDLE || order.items.size > 1)
        ) {
            HorizontalDivider(color = colors.border)
            Text(
                text = stringResource(R.string.orders_detail_itemsTitle, order.items.size),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.text,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                order.items.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.bgPage, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                        ) {
                            Text(
                                text = item.examName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = colors.text,
                            )
                            Text(
                                text = "(${item.examCode})",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted,
                            )
                        }
                        Text(
                            text = pluralStringResource(
                                R.plurals.orders_detail_attemptsValue,
                                item.attemptsGranted,
                                item.attemptsGranted,
                            ),
                            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.Medium,
                            color = colors.textMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderFieldRow(
    label: String,
    value: String,
    emphasized: Boolean = false,
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
            style = (if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
                .copy(fontFeatureSettings = "tnum"),
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium,
            color = if (emphasized) colors.primaryText else colors.text,
        )
    }
}

@Composable
private fun OrderBenefitLine(label: String, enabled: Boolean) {
    val colors = Thi360Theme.colors
    val answer = stringResource(
        if (enabled) R.string.orders_detail_benefits_yes else R.string.orders_detail_benefits_no,
    )
    Text(
        text = "$label: $answer",
        style = MaterialTheme.typography.bodySmall,
        color = if (enabled) colors.text else colors.textMuted,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrderPaymentsSection(
    order: OrderDetail,
    locale: vn.unicom.thi360.core.preferences.AppLocale,
) {
    val colors = Thi360Theme.colors
    val resources = LocalResources.current

    PaymentCard(modifier = Modifier.testTag(PaymentTestTags.ORDER_DETAIL_PAYMENTS_CARD)) {
        Text(
            text = stringResource(R.string.orders_detail_payments_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = colors.text,
            modifier = Modifier.semantics { heading() },
        )

        if (order.payments.isEmpty()) {
            Text(
                text = stringResource(
                    if (order.paymentMethod == OrderDetail.PaymentMethod.FREE) {
                        R.string.orders_detail_payments_empty
                    } else {
                        R.string.orders_detail_payments_emptyVnpay
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                order.payments.forEach { payment ->
                    OrderPaymentRowCard(
                        payment = payment,
                        amountText = PaymentFormatters.formatPrice(payment.amountVnd, locale, resources),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrderPaymentRowCard(
    payment: OrderPaymentView,
    amountText: String,
) {
    val colors = Thi360Theme.colors
    val seqText = payment.attemptSeq.toString().padStart(2, '0')
    val bankText = listOfNotNull(
        payment.bankCode?.takeIf { it.isNotBlank() },
        payment.cardType?.takeIf { it.isNotBlank() },
    ).joinToString(" · ").ifEmpty { "—" }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgPage, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "#$seqText · ${payment.txnRef}",
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold,
                color = colors.text,
            )
            PaymentStatusChip(
                label = stringResource(PaymentFormatters.paymentStateStringRes(payment.state)),
                tone = PaymentFormatters.paymentStateTone(payment.state),
            )
            if (payment.duplicate) {
                PaymentStatusChip(
                    label = stringResource(R.string.orders_detail_payments_duplicate),
                    tone = PaymentStatusTone.Warning,
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "${stringResource(R.string.orders_detail_payments_amount)}: $amountText",
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = colors.text,
            )
            Text(
                text = "${stringResource(R.string.orders_detail_payments_bank)}: $bankText",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
            Text(
                text = PaymentFormatters.formatDateTimeVn(payment.createdAt),
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = colors.textMuted,
            )
        }
        val responseCode = payment.responseCode?.takeIf { it.isNotBlank() }
        if (responseCode != null && payment.state != OrderPaymentView.State.SUCCEEDED) {
            val reasonKey = VnpayReasonMapper.map(responseCode)
            Text(
                text = stringResource(VnpayReasonMapper.stringRes(reasonKey)),
                style = MaterialTheme.typography.bodySmall,
                color = colors.dangerText,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrderHistorySection(history: List<OrderHistoryView>) {
    val colors = Thi360Theme.colors
    val resources = LocalResources.current

    PaymentCard(modifier = Modifier.testTag(PaymentTestTags.ORDER_DETAIL_HISTORY_CARD)) {
        Text(
            text = stringResource(R.string.orders_detail_history_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = colors.text,
            modifier = Modifier.semantics { heading() },
        )

        if (history.isEmpty()) {
            Text(
                text = stringResource(R.string.orders_detail_history_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                history.forEachIndexed { index, entry ->
                    if (index > 0) {
                        HorizontalDivider(color = colors.border)
                    }
                    val transitionText = if (!entry.fromState.isNullOrBlank()) {
                        stringResource(
                            R.string.orders_detail_history_transition,
                            PaymentFormatters.formatStateName(entry.fromState, resources),
                            PaymentFormatters.formatStateName(entry.toState, resources),
                        )
                    } else {
                        stringResource(R.string.orders_detail_history_created)
                    }
                    val actorLabel = stringResource(
                        HistoryActorMapper.stringRes(HistoryActorMapper.map(entry.actor)),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = transitionText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.text,
                            )
                            Text(
                                text = actorLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted,
                            )
                        }
                        entry.reason?.takeIf { it.isNotBlank() }?.let { rawReason ->
                            Text(
                                text = HistoryReasonParser.format(rawReason, resources),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted,
                            )
                        }
                        Text(
                            text = PaymentFormatters.formatDateTimeVn(entry.at),
                            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                            color = colors.textMuted,
                        )
                    }
                }
            }
        }
    }
}
