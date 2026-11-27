package vn.unicom.thi360.feature.payment.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.unicom.thi360.R
import vn.unicom.thi360.core.config.SupportContact
import vn.unicom.thi360.core.network.generated.model.OrderDetail
import vn.unicom.thi360.core.theme.LocalAppLocale
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.payment.domain.OrderActionErrorKey
import vn.unicom.thi360.feature.payment.domain.OrderActionErrorMapper
import vn.unicom.thi360.feature.payment.domain.PaymentBrowser
import vn.unicom.thi360.feature.payment.domain.PaymentFormatters
import vn.unicom.thi360.feature.payment.domain.PaymentStatusTone
import vn.unicom.thi360.feature.payment.domain.VnpayReasonMapper
import vn.unicom.thi360.feature.payment.viewmodel.PaymentResultState
import vn.unicom.thi360.feature.payment.viewmodel.PaymentResultUiState
import vn.unicom.thi360.feature.payment.viewmodel.PaymentResultViewModel

@Composable
fun PaymentResultScreen(
    viewModel: PaymentResultViewModel,
    paymentBrowser: PaymentBrowser,
    supportContact: SupportContact,
    onBack: () -> Unit,
    onOpenMyExams: () -> Unit,
    onOpenOrder: (String) -> Unit,
    onOpenOrders: () -> Unit,
    onOpenCheckout: (String) -> Unit,
    onOpenCatalog: () -> Unit,
    onSwitchAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.onLifecyclePause()
                Lifecycle.Event.ON_START -> viewModel.onLifecycleResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onLeaveScreen()
        }
    }

    LaunchedEffect(uiState.browserEvent) {
        val event = uiState.browserEvent ?: return@LaunchedEffect
        viewModel.consumeBrowserEvent()
        val opened = paymentBrowser.open(context, event.resolvedUrl)
        if (!opened) {
            viewModel.onBrowserOpenFailed(event.orderCode)
        }
    }

    PaymentResultScreenContent(
        uiState = uiState,
        supportContact = supportContact,
        onBack = {
            viewModel.onLeaveScreen()
            onBack()
        },
        onRetryLoad = viewModel::retryLoad,
        onCheckAgain = viewModel::checkAgain,
        onRetryPay = viewModel::retryPay,
        onOpenMyExams = {
            viewModel.onLeaveScreen()
            onOpenMyExams()
        },
        onOpenOrder = { code ->
            viewModel.onLeaveScreen()
            onOpenOrder(code)
        },
        onOpenOrders = {
            viewModel.onLeaveScreen()
            onOpenOrders()
        },
        onOpenCheckout = { productCode ->
            viewModel.onLeaveScreen()
            onOpenCheckout(productCode)
        },
        onOpenCatalog = {
            viewModel.onLeaveScreen()
            onOpenCatalog()
        },
        onSwitchAccount = {
            viewModel.onLeaveScreen()
            onSwitchAccount()
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaymentResultScreenContent(
    uiState: PaymentResultUiState,
    supportContact: SupportContact,
    onBack: () -> Unit,
    onRetryLoad: () -> Unit,
    onCheckAgain: () -> Unit,
    onRetryPay: () -> Unit,
    onOpenMyExams: () -> Unit,
    onOpenOrder: (String) -> Unit,
    onOpenOrders: () -> Unit,
    onOpenCheckout: (String) -> Unit,
    onOpenCatalog: () -> Unit,
    onSwitchAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors
    val locale = LocalAppLocale.current
    val resources = LocalResources.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgPage)
            .testTag(PaymentTestTags.RESULT_SCREEN),
    ) {
        PaymentTopBar(
            title = stringResource(R.string.checkout_resultPageTitle),
            onBack = onBack,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (val state = uiState.state) {
                    PaymentResultState.Verifying,
                    PaymentResultState.Loading,
                    -> {
                        val loadingDesc = stringResource(R.string.common_loading)
                        PaymentCard(
                            modifier = Modifier
                                .semantics { contentDescription = loadingDesc }
                                .testTag(PaymentTestTags.RESULT_SKELETON),
                            contentPadding = 20.dp,
                        ) {
                            PaymentSkeletonBlock(height = 28.dp, widthFraction = 0.65f)
                            PaymentSkeletonBlock(height = 18.dp)
                            PaymentSkeletonBlock(height = 48.dp, widthFraction = 0.5f)
                        }
                    }

                    PaymentResultState.InvalidSignature -> {
                        PaymentCard(
                            borderColor = colors.dangerText.copy(alpha = 0.35f),
                            contentPadding = 20.dp,
                        ) {
                            Text(
                                text = stringResource(R.string.checkout_result_invalidSignatureHeading),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.text,
                                modifier = Modifier
                                    .semantics { heading() }
                                    .testTag(PaymentTestTags.RESULT_HEADING),
                            )
                            Text(
                                text = stringResource(R.string.checkout_result_invalidSignatureBody),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textMuted,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                PaymentPrimaryButton(
                                    text = stringResource(R.string.checkout_result_viewOrders),
                                    onClick = onOpenOrders,
                                    modifier = Modifier.testTag(PaymentTestTags.RESULT_VIEW_ORDERS_BUTTON),
                                )
                                PaymentSecondaryButton(
                                    text = stringResource(R.string.checkout_result_viewMyExams),
                                    onClick = onOpenMyExams,
                                    modifier = Modifier.testTag(PaymentTestTags.RESULT_VIEW_MY_EXAMS_BUTTON),
                                )
                            }
                        }
                    }

                    PaymentResultState.VerifyNotFound -> {
                        PaymentCard(contentPadding = 20.dp) {
                            Text(
                                text = stringResource(R.string.orders_notFound_title),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.text,
                                modifier = Modifier
                                    .semantics { heading() }
                                    .testTag(PaymentTestTags.RESULT_HEADING),
                            )
                            Text(
                                text = stringResource(R.string.checkout_result_verifyNotFoundBody),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textMuted,
                            )
                            PaymentPrimaryButton(
                                text = stringResource(R.string.checkout_result_viewOrders),
                                onClick = onOpenOrders,
                                modifier = Modifier.testTag(PaymentTestTags.RESULT_VIEW_ORDERS_BUTTON),
                            )
                        }
                    }

                    PaymentResultState.LoadError -> {
                        PaymentCard(
                            borderColor = colors.dangerText.copy(alpha = 0.35f),
                            contentPadding = 20.dp,
                        ) {
                            Text(
                                text = stringResource(R.string.checkout_result_loadErrorHeading),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.text,
                                modifier = Modifier
                                    .semantics { heading() }
                                    .testTag(PaymentTestTags.RESULT_HEADING),
                            )
                            Text(
                                text = stringResource(R.string.checkout_result_loadErrorBody),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textMuted,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                PaymentSecondaryButton(
                                    text = stringResource(R.string.common_retry),
                                    onClick = onRetryLoad,
                                    modifier = Modifier.testTag(PaymentTestTags.RESULT_RETRY_LOAD_BUTTON),
                                )
                                PaymentPrimaryButton(
                                    text = stringResource(R.string.checkout_result_viewOrders),
                                    onClick = onOpenOrders,
                                    modifier = Modifier.testTag(PaymentTestTags.RESULT_VIEW_ORDERS_BUTTON),
                                )
                            }
                        }
                    }

                    is PaymentResultState.NotOwned -> {
                        PaymentCard(contentPadding = 20.dp) {
                            Text(
                                text = stringResource(R.string.checkout_result_notOwnedHeading),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.text,
                                modifier = Modifier
                                    .semantics { heading() }
                                    .testTag(PaymentTestTags.RESULT_HEADING),
                            )
                            Text(
                                text = stringResource(
                                    R.string.checkout_result_notOwnedBody,
                                    state.orderCode,
                                    state.username,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textMuted,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                PaymentPrimaryButton(
                                    text = stringResource(R.string.orders_notFound_otherAccount),
                                    onClick = onSwitchAccount,
                                    modifier = Modifier.testTag(PaymentTestTags.RESULT_SWITCH_ACCOUNT_BUTTON),
                                )
                                PaymentSecondaryButton(
                                    text = stringResource(R.string.checkout_result_viewOrders),
                                    onClick = onOpenOrders,
                                    modifier = Modifier.testTag(PaymentTestTags.RESULT_VIEW_ORDERS_BUTTON),
                                )
                            }
                        }
                    }

                    is PaymentResultState.Fulfilled -> {
                        val order = state.order
                        val isFreeVoucher = order.amountDueVnd == 0L && order.voucherDiscountVnd > 0L
                        val amountText = if (isFreeVoucher) {
                            stringResource(R.string.orders_amount_freeVoucher)
                        } else {
                            PaymentFormatters.formatPrice(order.amountDueVnd, locale, resources)
                        }
                        PaymentCard(
                            borderColor = statusColors.success.copy(alpha = 0.4f),
                            contentPadding = 20.dp,
                        ) {
                            Text(
                                text = stringResource(R.string.checkout_result_successHeading),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = statusColors.success,
                                modifier = Modifier
                                    .semantics { heading() }
                                    .testTag(PaymentTestTags.RESULT_HEADING),
                            )
                            Text(
                                text = stringResource(
                                    if (isFreeVoucher) {
                                        R.string.checkout_result_successFreeBody
                                    } else {
                                        R.string.checkout_result_successBody
                                    },
                                    order.code,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textMuted,
                            )
                            OrderSummaryInfoBox(
                                productName = order.productName,
                                details = listOfNotNull(
                                    order.code,
                                    amountText,
                                    order.fulfilledAt?.let { PaymentFormatters.formatDateTimeVn(it) },
                                ).joinToString(" · "),
                            )
                            ResultActionErrorSection(
                                errorKey = uiState.retryPayError,
                                onOpenCatalog = onOpenCatalog,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                PaymentPrimaryButton(
                                    text = stringResource(R.string.checkout_result_viewMyExams),
                                    onClick = onOpenMyExams,
                                    modifier = Modifier.testTag(PaymentTestTags.RESULT_VIEW_MY_EXAMS_BUTTON),
                                )
                                PaymentSecondaryButton(
                                    text = stringResource(R.string.checkout_result_viewOrder),
                                    onClick = { onOpenOrder(order.code) },
                                    modifier = Modifier.testTag(PaymentTestTags.RESULT_VIEW_ORDER_BUTTON),
                                )
                            }
                        }
                    }

                    is PaymentResultState.Failed -> {
                        val order = state.order
                        val reasonKey = VnpayReasonMapper.map(state.reasonCode)
                        val txnPrefix = state.latestPayment?.txnRef?.let { txnRef ->
                            "${stringResource(R.string.checkout_result_txnRefLabel)} $txnRef"
                        } ?: order.code
                        val amountText = PaymentFormatters.formatPrice(order.amountDueVnd, locale, resources)

                        PaymentCard(
                            borderColor = colors.dangerText.copy(alpha = 0.35f),
                            contentPadding = 20.dp,
                        ) {
                            Text(
                                text = stringResource(R.string.checkout_result_failedHeading),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.text,
                                modifier = Modifier
                                    .semantics { heading() }
                                    .testTag(PaymentTestTags.RESULT_HEADING),
                            )
                            Text(
                                text = stringResource(R.string.checkout_result_failedBody, order.code),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textMuted,
                            )
                            Text(
                                text = stringResource(VnpayReasonMapper.stringRes(reasonKey)),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = colors.dangerText,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                            OrderSummaryInfoBox(
                                productName = order.productName,
                                details = "$txnPrefix · $amountText",
                            )
                            ResultActionErrorSection(
                                errorKey = uiState.retryPayError,
                                onOpenCatalog = onOpenCatalog,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                PaymentPrimaryButton(
                                    text = stringResource(R.string.checkout_result_retryPay),
                                    onClick = onRetryPay,
                                    loading = uiState.retryingPay,
                                    modifier = Modifier.testTag(PaymentTestTags.RESULT_RETRY_PAY_BUTTON),
                                )
                                PaymentSecondaryButton(
                                    text = stringResource(R.string.checkout_result_viewOrder),
                                    onClick = { onOpenOrder(order.code) },
                                    modifier = Modifier.testTag(PaymentTestTags.RESULT_VIEW_ORDER_BUTTON),
                                )
                            }
                        }
                    }

                    is PaymentResultState.Checking -> {
                        val order = state.order
                        val supportText = PaymentFormatters.formatSupportSummary(supportContact)
                            ?: stringResource(R.string.landing_support_pending)
                        val amountText = PaymentFormatters.formatPrice(order.amountDueVnd, locale, resources)

                        PaymentCard(
                            borderColor = statusColors.warning.copy(alpha = 0.45f),
                            contentPadding = 20.dp,
                        ) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = stringResource(
                                        if (state.timedOut) {
                                            R.string.checkout_result_checkingTimeoutHeading
                                        } else {
                                            R.string.checkout_result_checkingHeading
                                        },
                                    ),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.text,
                                    modifier = Modifier
                                        .semantics { heading() }
                                        .testTag(PaymentTestTags.RESULT_HEADING),
                                )
                                PaymentStatusChip(
                                    label = stringResource(R.string.checkout_result_checkingBadge),
                                    tone = PaymentStatusTone.Warning,
                                    modifier = Modifier.testTag(PaymentTestTags.RESULT_STATUS_CHIP),
                                )
                            }
                            Text(
                                text = stringResource(
                                    if (state.timedOut) {
                                        R.string.checkout_result_checkingTimeoutBody
                                    } else {
                                        R.string.checkout_result_checkingPending
                                    },
                                    order.code,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textMuted,
                            )
                            if (state.timedOut) {
                                Text(
                                    text = stringResource(
                                        R.string.checkout_result_checkingTimeoutSupport,
                                        supportText,
                                        order.code,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textMuted,
                                )
                            }
                            if (state.pollError) {
                                PaymentBanner(
                                    tone = PaymentStatusTone.Warning,
                                    title = null,
                                    body = stringResource(
                                        if (state.timedOut) {
                                            R.string.checkout_result_pollErrorTimeoutNotice
                                        } else {
                                            R.string.checkout_result_pollErrorNotice
                                        },
                                    ),
                                    modifier = Modifier.testTag(PaymentTestTags.RESULT_POLL_ERROR_BANNER),
                                )
                            }
                            OrderSummaryInfoBox(
                                productName = order.productName,
                                details = "${order.code} · $amountText",
                            )
                            ResultActionErrorSection(
                                errorKey = uiState.retryPayError,
                                onOpenCatalog = onOpenCatalog,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                PaymentSecondaryButton(
                                    text = stringResource(R.string.checkout_result_checkAgain),
                                    onClick = onCheckAgain,
                                    loading = uiState.isCheckingAgain,
                                    modifier = Modifier.testTag(PaymentTestTags.RESULT_CHECK_AGAIN_BUTTON),
                                )
                                PaymentPrimaryButton(
                                    text = stringResource(R.string.checkout_result_viewOrder),
                                    onClick = { onOpenOrder(order.code) },
                                    modifier = Modifier.testTag(PaymentTestTags.RESULT_VIEW_ORDER_BUTTON),
                                )
                                PaymentSecondaryButton(
                                    text = stringResource(R.string.checkout_result_viewMyExams),
                                    onClick = onOpenMyExams,
                                    modifier = Modifier.testTag(PaymentTestTags.RESULT_VIEW_MY_EXAMS_BUTTON),
                                )
                            }
                        }
                    }

                    is PaymentResultState.NoResultYet -> {
                        val order = state.order
                        val amountText = PaymentFormatters.formatPrice(order.amountDueVnd, locale, resources)
                        val bodyText = order.expiresAt?.let { expiresAt ->
                            stringResource(
                                R.string.checkout_result_noResultBody,
                                order.code,
                                PaymentFormatters.formatTimeOnlyVn(expiresAt),
                            )
                        } ?: stringResource(
                            R.string.checkout_result_noResultBodyNoExpiry,
                            order.code,
                        )

                        PaymentCard(
                            borderColor = statusColors.warning.copy(alpha = 0.45f),
                            contentPadding = 20.dp,
                        ) {
                            Text(
                                text = stringResource(R.string.checkout_result_noResultHeading),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.text,
                                modifier = Modifier
                                    .semantics { heading() }
                                    .testTag(PaymentTestTags.RESULT_HEADING),
                            )
                            Text(
                                text = bodyText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textMuted,
                            )
                            if (state.pollError) {
                                PaymentBanner(
                                    tone = PaymentStatusTone.Warning,
                                    title = null,
                                    body = stringResource(R.string.checkout_result_pollErrorNotice),
                                    modifier = Modifier.testTag(PaymentTestTags.RESULT_POLL_ERROR_BANNER),
                                )
                            }
                            OrderSummaryInfoBox(
                                productName = order.productName,
                                details = "${order.code} · $amountText",
                            )
                            ResultActionErrorSection(
                                errorKey = uiState.retryPayError,
                                onOpenCatalog = onOpenCatalog,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                PaymentPrimaryButton(
                                    text = stringResource(R.string.orders_actions_continuePayment),
                                    onClick = onRetryPay,
                                    loading = uiState.retryingPay,
                                    modifier = Modifier.testTag(PaymentTestTags.RESULT_CONTINUE_PAYMENT_BUTTON),
                                )
                                PaymentSecondaryButton(
                                    text = stringResource(R.string.checkout_result_viewOrder),
                                    onClick = { onOpenOrder(order.code) },
                                    modifier = Modifier.testTag(PaymentTestTags.RESULT_VIEW_ORDER_BUTTON),
                                )
                            }
                        }
                    }

                    is PaymentResultState.Closed -> {
                        val order = state.order
                        PaymentCard(contentPadding = 20.dp) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.checkout_result_closedHeading),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.text,
                                    modifier = Modifier
                                        .semantics { heading() }
                                        .testTag(PaymentTestTags.RESULT_HEADING),
                                )
                                PaymentStatusChip(
                                    label = PaymentFormatters.formatStateName(order.state.value, resources),
                                    tone = PaymentFormatters.orderStateTone(order.state),
                                    modifier = Modifier.testTag(PaymentTestTags.RESULT_STATUS_CHIP),
                                )
                            }
                            Text(
                                text = stringResource(R.string.checkout_result_closedBody, order.code),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textMuted,
                            )
                            ResultActionErrorSection(
                                errorKey = uiState.retryPayError,
                                onOpenCatalog = onOpenCatalog,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                val replacementCode = order.replacedByOrderCode?.takeIf { it.isNotBlank() }
                                if (replacementCode != null) {
                                    PaymentPrimaryButton(
                                        text = stringResource(
                                            R.string.orders_actions_viewReplacement,
                                            replacementCode,
                                        ),
                                        onClick = { onOpenOrder(replacementCode) },
                                        modifier = Modifier.testTag(PaymentTestTags.RESULT_REPLACEMENT_BUTTON),
                                    )
                                } else {
                                    PaymentPrimaryButton(
                                        text = stringResource(R.string.checkout_result_buyAgain),
                                        onClick = { onOpenCheckout(order.productCode) },
                                        modifier = Modifier.testTag(PaymentTestTags.RESULT_BUY_AGAIN_BUTTON),
                                    )
                                }
                                PaymentSecondaryButton(
                                    text = stringResource(R.string.checkout_result_viewOrders),
                                    onClick = onOpenOrders,
                                    modifier = Modifier.testTag(PaymentTestTags.RESULT_VIEW_ORDERS_BUTTON),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderSummaryInfoBox(
    productName: String,
    details: String,
) {
    val colors = Thi360Theme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgPage, RoundedCornerShape(10.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = productName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.text,
        )
        Text(
            text = details,
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            color = colors.textMuted,
        )
    }
}

@Composable
private fun ResultActionErrorSection(
    errorKey: OrderActionErrorKey?,
    onOpenCatalog: () -> Unit,
) {
    if (errorKey == null) return
    val showCatalogCta = errorKey == OrderActionErrorKey.PRODUCT_DISCONTINUED ||
        errorKey == OrderActionErrorKey.PRODUCT_NOT_FOUND
    PaymentBanner(
        tone = PaymentStatusTone.Danger,
        title = null,
        body = stringResource(OrderActionErrorMapper.stringRes(errorKey)),
        modifier = Modifier.testTag(PaymentTestTags.RESULT_ACTION_ERROR_BANNER),
        action = if (showCatalogCta) {
            {
                PaymentSecondaryButton(
                    text = stringResource(R.string.orders_actions_toCatalog),
                    onClick = onOpenCatalog,
                    modifier = Modifier.testTag(PaymentTestTags.RESULT_TO_CATALOG_BUTTON),
                )
            }
        } else {
            null
        },
    )
}
