package vn.unicom.thi360.feature.payment.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import vn.unicom.thi360.R
import vn.unicom.thi360.core.config.SupportContact
import vn.unicom.thi360.core.network.generated.model.BundleAccessExamItem
import vn.unicom.thi360.core.network.generated.model.BundleDetailItem
import vn.unicom.thi360.core.network.generated.model.ExamAccessItem
import vn.unicom.thi360.core.network.generated.model.PriceQuote
import vn.unicom.thi360.core.network.generated.model.ProductDetail
import vn.unicom.thi360.core.theme.LocalAppLocale
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.core.ui.LoadFailureKind
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.feature.payment.domain.PaymentBrowser
import vn.unicom.thi360.feature.payment.domain.PaymentFormatters
import vn.unicom.thi360.feature.payment.domain.PaymentStatusTone
import vn.unicom.thi360.feature.payment.domain.VoucherErrorMapper
import vn.unicom.thi360.feature.payment.viewmodel.CheckoutNavigationEvent
import vn.unicom.thi360.feature.payment.viewmodel.CheckoutSubmitError
import vn.unicom.thi360.feature.payment.viewmodel.CheckoutSubmitState
import vn.unicom.thi360.feature.payment.viewmodel.CheckoutUiState
import vn.unicom.thi360.feature.payment.viewmodel.CheckoutViewModel
import vn.unicom.thi360.feature.payment.viewmodel.VoucherAppliedVariant
import vn.unicom.thi360.feature.payment.viewmodel.VoucherBoxState
import vn.unicom.thi360.feature.payment.viewmodel.VoucherFeedback

@Composable
fun CheckoutScreen(
    viewModel: CheckoutViewModel,
    paymentBrowser: PaymentBrowser,
    supportContact: SupportContact,
    onBack: () -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenProduct: (String) -> Unit,
    onOpenPaymentResult: (String) -> Unit,
    onOpenOrders: () -> Unit,
    onOpenMyExams: () -> Unit,
    onOpenPrepare: (String) -> Unit,
    onOpenAttempt: (String) -> Unit,
    onSwitchAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.navigationEvent) {
        when (val event = state.navigationEvent) {
            is CheckoutNavigationEvent.OpenBrowser -> {
                viewModel.consumeNavigationEvent()
                val opened = paymentBrowser.open(context, event.resolvedUrl)
                if (!opened) {
                    viewModel.onBrowserOpenFailed(event.orderCode)
                }
            }

            is CheckoutNavigationEvent.OpenPaymentResult -> {
                viewModel.consumeNavigationEvent()
                onOpenPaymentResult(event.orderCode)
            }

            null -> Unit
        }
    }

    CheckoutScreenContent(
        state = state,
        supportContact = supportContact,
        onBack = onBack,
        onRetryInitial = viewModel::retryInitial,
        onRetryAccess = viewModel::retryAccess,
        onRetryQuote = viewModel::retryBaseQuote,
        onVoucherInputChange = viewModel::onVoucherInputChanged,
        onApplyVoucher = viewModel::applyVoucher,
        onRemoveVoucher = viewModel::removeVoucher,
        onSubmitOrder = viewModel::submitOrder,
        onAcknowledgeNewAccount = viewModel::acknowledgeNewAccount,
        onOpenCatalog = onOpenCatalog,
        onOpenProduct = onOpenProduct,
        onOpenPaymentResult = onOpenPaymentResult,
        onOpenOrders = onOpenOrders,
        onOpenMyExams = onOpenMyExams,
        onOpenPrepare = onOpenPrepare,
        onOpenAttempt = onOpenAttempt,
        onSwitchAccount = onSwitchAccount,
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheckoutScreenContent(
    state: CheckoutUiState,
    supportContact: SupportContact,
    onBack: () -> Unit,
    onRetryInitial: () -> Unit,
    onRetryAccess: () -> Unit,
    onRetryQuote: () -> Unit,
    onVoucherInputChange: (String) -> Unit,
    onApplyVoucher: () -> Unit,
    onRemoveVoucher: () -> Unit,
    onSubmitOrder: () -> Unit,
    onAcknowledgeNewAccount: () -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenProduct: (String) -> Unit,
    onOpenPaymentResult: (String) -> Unit,
    onOpenOrders: () -> Unit,
    onOpenMyExams: () -> Unit,
    onOpenPrepare: (String) -> Unit,
    onOpenAttempt: (String) -> Unit,
    onSwitchAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var summaryOffsetY by remember { mutableIntStateOf(0) }

    LaunchedEffect(state.accountConflict, state.priceChangedSeenOverride, state.productUnavailable) {
        if (state.accountConflict != null || state.priceChangedSeenOverride != null || state.productUnavailable) {
            scrollState.animateScrollTo(0)
        }
    }

    val backTitle = if (state.isBundle || (state.productUnavailable && state.loadedProduct == null)) {
        stringResource(R.string.bundle_checkout_backToBundle)
    } else {
        stringResource(R.string.checkout_backToExam)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgPage)
            .testTag(PaymentTestTags.CHECKOUT_SCREEN),
    ) {
        PaymentTopBar(
            title = backTitle,
            onBack = onBack,
        )

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            val useTwoColumns = maxWidth >= 840.dp
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 1120.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val productState = state.product
                    val userState = state.user
                    val goneProduct = state.goneProduct

                    when {
                        goneProduct != null -> {
                            CheckoutGoneView(
                                productName = goneProduct.name,
                                onOpenCatalog = onOpenCatalog,
                            )
                        }

                        productState is LoadState.Loading || userState is LoadState.Loading -> {
                            CheckoutLoadingSkeleton()
                        }

                        userState is LoadState.Failed && state.displayUser == null -> {
                            CheckoutErrorView(onRetry = onRetryInitial)
                        }

                        productState is LoadState.Failed && productState.kind == LoadFailureKind.NotFound -> {
                            CheckoutNotFoundView(onOpenCatalog = onOpenCatalog)
                        }

                        productState is LoadState.Failed &&
                            productState.kind is LoadFailureKind.Conflict &&
                            productState.kind.code == "PRODUCT_UNAVAILABLE" -> {
                            CheckoutUnavailableFullView(
                                code = state.code,
                                onOpenProduct = onOpenProduct,
                                onOpenCatalog = onOpenCatalog,
                            )
                        }

                        productState is LoadState.Failed -> {
                            CheckoutErrorView(onRetry = onRetryInitial)
                        }

                        productState is LoadState.Loaded -> {
                            val product = productState.value
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = stringResource(R.string.checkout_heading),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.text,
                                    modifier = Modifier.semantics { heading() },
                                )
                                Text(
                                    text = stringResource(
                                        if (state.isBundle) {
                                            R.string.bundle_checkout_subtitle
                                        } else {
                                            R.string.checkout_subtitle
                                        },
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textMuted,
                                )
                            }

                            if (state.productUnavailable) {
                                PaymentBanner(
                                    tone = PaymentStatusTone.Warning,
                                    title = stringResource(R.string.bundle_checkout_unavailableTitle),
                                    body = stringResource(R.string.bundle_checkout_unavailableBody),
                                    modifier = Modifier.testTag(PaymentTestTags.CHECKOUT_UNAVAILABLE_BANNER),
                                    action = {
                                        PaymentSecondaryButton(
                                            text = stringResource(R.string.bundle_checkout_unavailableActionBack),
                                            onClick = { onOpenProduct(state.code) },
                                            modifier = Modifier.testTag(PaymentTestTags.CHECKOUT_BACK_TO_BUNDLE_BUTTON),
                                        )
                                    },
                                )
                            }

                            state.accountConflict?.let { conflict ->
                                PaymentBanner(
                                    tone = PaymentStatusTone.Danger,
                                    title = stringResource(R.string.checkout_account_changedTitle),
                                    body = stringResource(
                                        R.string.checkout_account_changedDescription,
                                        conflict.previousUsername,
                                        conflict.current.username,
                                    ),
                                    modifier = Modifier.testTag(PaymentTestTags.CHECKOUT_ACCOUNT_CHANGED_BANNER),
                                    action = {
                                        PaymentPrimaryButton(
                                            text = stringResource(
                                                R.string.checkout_account_continueAsCurrent,
                                                conflict.current.username,
                                            ),
                                            onClick = onAcknowledgeNewAccount,
                                            modifier = Modifier.testTag(PaymentTestTags.CHECKOUT_ACCOUNT_CONTINUE_BUTTON),
                                        )
                                    },
                                )
                            }

                            if (state.showPriceChangedBanner && state.activeQuote != null) {
                                PriceChangedBanner(
                                    quote = state.activeQuote!!,
                                    seenPrice = state.bannerSeenPrice,
                                )
                            }

                            CheckoutAccessBannerSection(
                                state = state,
                                onRetryAccess = onRetryAccess,
                                onOpenPaymentResult = onOpenPaymentResult,
                                onOpenOrders = onOpenOrders,
                                onOpenMyExams = onOpenMyExams,
                                onOpenPrepare = onOpenPrepare,
                                onOpenAttempt = onOpenAttempt,
                                onBuyMoreClick = {
                                    coroutineScope.launch {
                                        scrollState.animateScrollTo(summaryOffsetY)
                                    }
                                },
                            )

                            val leftColumn: @Composable () -> Unit = {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    state.displayUser?.let { user ->
                                        CheckoutAccountCard(
                                            fullName = user.fullName.trim().ifEmpty { user.username },
                                            username = user.username,
                                            isBundle = state.isBundle,
                                            bundleExamCount = product.items?.size ?: 0,
                                            onSwitchAccount = onSwitchAccount,
                                        )
                                    }
                                    CheckoutProductCard(
                                        product = product,
                                        activeQuote = state.activeQuote,
                                        isBundle = state.isBundle,
                                        bundleAccessItems = state.accessRow?.items.orEmpty(),
                                    )
                                    CheckoutRefundCard(
                                        isBundle = state.isBundle,
                                        supportContact = supportContact,
                                    )
                                }
                            }

                            val rightColumn: @Composable () -> Unit = {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.onGloballyPositioned { coords ->
                                        summaryOffsetY = coords.positionInParent().y.toInt()
                                    },
                                ) {
                                    if (state.activeQuote != null) {
                                        VoucherBoxCard(
                                            quote = state.activeQuote!!,
                                            voucherState = state.voucher,
                                            isBundle = state.isBundle,
                                            controlsDisabled = state.isVoucherControlsDisabled,
                                            onInputChange = onVoucherInputChange,
                                            onApply = onApplyVoucher,
                                            onRemove = onRemoveVoucher,
                                        )
                                    }
                                    CheckoutSummaryCard(
                                        product = product,
                                        state = state,
                                        onRetryQuote = onRetryQuote,
                                        onSubmitOrder = onSubmitOrder,
                                    )
                                }
                            }

                            if (useTwoColumns) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Box(modifier = Modifier.weight(1.15f)) { leftColumn() }
                                    Box(modifier = Modifier.weight(0.85f)) { rightColumn() }
                                }
                            } else {
                                leftColumn()
                                rightColumn()
                            }
                        }

                        else -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckoutLoadingSkeleton() {
    val loadingDesc = stringResource(R.string.common_loading)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = loadingDesc }
            .testTag(PaymentTestTags.CHECKOUT_SKELETON),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PaymentSkeletonBlock(height = 28.dp, widthFraction = 0.55f)
        PaymentSkeletonBlock(height = 120.dp)
        PaymentSkeletonBlock(height = 180.dp)
        PaymentSkeletonBlock(height = 140.dp)
    }
}

@Composable
private fun CheckoutNotFoundView(onOpenCatalog: () -> Unit) {
    val colors = Thi360Theme.colors
    PaymentCard(
        modifier = Modifier.testTag(PaymentTestTags.CHECKOUT_NOT_FOUND_STATE),
        contentPadding = 24.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.checkout_notFound_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.checkout_notFound_description),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
            )
            PaymentPrimaryButton(
                text = stringResource(R.string.checkout_backToCatalog),
                onClick = onOpenCatalog,
                modifier = Modifier.testTag(PaymentTestTags.CHECKOUT_BACK_TO_CATALOG_BUTTON),
            )
        }
    }
}

@Composable
private fun CheckoutGoneView(
    productName: String,
    onOpenCatalog: () -> Unit,
) {
    val colors = Thi360Theme.colors
    PaymentCard(
        modifier = Modifier.testTag(PaymentTestTags.CHECKOUT_GONE_STATE),
        contentPadding = 24.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.checkout_gone_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.checkout_gone_description, productName),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
            )
            PaymentPrimaryButton(
                text = stringResource(R.string.checkout_backToCatalog),
                onClick = onOpenCatalog,
                modifier = Modifier.testTag(PaymentTestTags.CHECKOUT_BACK_TO_CATALOG_BUTTON),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CheckoutUnavailableFullView(
    code: String,
    onOpenProduct: (String) -> Unit,
    onOpenCatalog: () -> Unit,
) {
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors
    PaymentCard(
        modifier = Modifier.testTag(PaymentTestTags.CHECKOUT_UNAVAILABLE_BANNER),
        containerColor = statusColors.warningBg,
        borderColor = statusColors.warning.copy(alpha = 0.45f),
        contentPadding = 24.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.bundle_checkout_unavailableTitle),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = statusColors.warning,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.payment_bundle_detail_unavailableBody),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.text,
                textAlign = TextAlign.Center,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PaymentSecondaryButton(
                    text = stringResource(R.string.bundle_checkout_unavailableActionBack),
                    onClick = { onOpenProduct(code) },
                    modifier = Modifier.testTag(PaymentTestTags.CHECKOUT_BACK_TO_BUNDLE_BUTTON),
                )
                PaymentPrimaryButton(
                    text = stringResource(R.string.checkout_backToCatalog),
                    onClick = onOpenCatalog,
                    modifier = Modifier.testTag(PaymentTestTags.CHECKOUT_BACK_TO_CATALOG_BUTTON),
                )
            }
        }
    }
}

@Composable
private fun CheckoutErrorView(onRetry: () -> Unit) {
    val colors = Thi360Theme.colors
    PaymentCard(
        modifier = Modifier.testTag(PaymentTestTags.CHECKOUT_ERROR_STATE),
        contentPadding = 24.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.checkout_loadError_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.checkout_loadError_description),
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

@Composable
private fun PriceChangedBanner(
    quote: PriceQuote,
    seenPrice: Long?,
) {
    val locale = LocalAppLocale.current
    val resources = LocalResources.current
    val latestText = PaymentFormatters.formatPrice(quote.finalPrice, locale, resources)
    val body = if (seenPrice != null && seenPrice != quote.finalPrice) {
        val seenText = PaymentFormatters.formatPrice(seenPrice, locale, resources)
        stringResource(R.string.checkout_priceChanged_descriptionWithSeen, latestText, seenText)
    } else {
        stringResource(R.string.checkout_priceChanged_description, latestText)
    }
    PaymentBanner(
        tone = PaymentStatusTone.Warning,
        title = stringResource(R.string.checkout_priceChanged_title),
        body = body,
        modifier = Modifier.testTag(PaymentTestTags.CHECKOUT_PRICE_CHANGED_BANNER),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CheckoutAccessBannerSection(
    state: CheckoutUiState,
    onRetryAccess: () -> Unit,
    onOpenPaymentResult: (String) -> Unit,
    onOpenOrders: () -> Unit,
    onOpenMyExams: () -> Unit,
    onOpenPrepare: (String) -> Unit,
    onOpenAttempt: (String) -> Unit,
    onBuyMoreClick: () -> Unit,
) {
    val accessState = state.access
    if (accessState is LoadState.Failed) {
        PaymentBanner(
            tone = PaymentStatusTone.Danger,
            title = stringResource(R.string.checkout_accessBanner_loadErrorTitle),
            body = stringResource(R.string.checkout_accessBanner_loadErrorDescription),
            modifier = Modifier.testTag(PaymentTestTags.CHECKOUT_ACCESS_BANNER),
            action = {
                PaymentSecondaryButton(
                    text = stringResource(R.string.common_retry),
                    onClick = onRetryAccess,
                    modifier = Modifier.testTag(PaymentTestTags.CHECKOUT_ACCESS_RETRY_BUTTON),
                )
            },
        )
        return
    }

    val accessRow = state.accessRow ?: return
    val pendingOrderCode = accessRow.pendingOrderCode?.takeIf { it.isNotBlank() }

    val checkingBlock: (@Composable () -> Unit)? = if (state.isBlockedByChecking) {
        {
            PaymentBanner(
                tone = PaymentStatusTone.Warning,
                title = stringResource(
                    R.string.checkout_accessBanner_checkingTitle,
                    pendingOrderCode.orEmpty(),
                ),
                body = stringResource(R.string.checkout_accessBanner_checkingDescription),
                modifier = Modifier.testTag(PaymentTestTags.CHECKOUT_ACCESS_BANNER),
                action = {
                    PaymentSecondaryButton(
                        text = stringResource(R.string.checkout_accessBanner_viewPaymentStatus),
                        onClick = {
                            if (pendingOrderCode != null) {
                                onOpenPaymentResult(pendingOrderCode)
                            } else {
                                onOpenOrders()
                            }
                        },
                        modifier = Modifier.testTag(PaymentTestTags.CHECKOUT_VIEW_STATUS_BUTTON),
                    )
                },
            )
        }
    } else {
        null
    }

    if (state.isBundle && !accessRow.items.isNullOrEmpty()) {
        val ownedItems = accessRow.items.filter { it.attemptsLeft > 0 }
        val pendingItems = accessRow.items.filter {
            it.hasPendingFulfillment || it.state == BundleAccessExamItem.State.CHECKING
        }
        if (ownedItems.isEmpty() && pendingItems.isEmpty() && checkingBlock == null) {
            return
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(PaymentTestTags.CHECKOUT_ACCESS_BANNER),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            checkingBlock?.invoke()
            if (ownedItems.isNotEmpty()) {
                val lines = ownedItems.map { item ->
                    pluralStringResource(
                        R.plurals.payment_bundle_existingEntitlement,
                        item.attemptsLeft,
                        item.attemptsLeft,
                        item.name,
                    )
                }
                PaymentBanner(
                    tone = PaymentStatusTone.Info,
                    title = null,
                    body = lines.joinToString("\n"),
                )
            }
            if (pendingItems.isNotEmpty()) {
                val lines = pendingItems.map { item ->
                    val code = item.pendingOrderCode?.takeIf { it.isNotBlank() }
                    if (code != null) {
                        stringResource(R.string.bundle_checkout_pendingExamOrder, code, item.name)
                    } else {
                        stringResource(R.string.bundle_checkout_pendingExamOrderNoCode, item.name)
                    }
                }
                PaymentBanner(
                    tone = PaymentStatusTone.Warning,
                    title = null,
                    body = lines.joinToString("\n"),
                )
            }
        }
        return
    }

    if (checkingBlock != null) {
        checkingBlock()
        return
    }

    if (state.hasExistingAccess) {
        val isInProgress = accessRow.state == ExamAccessItem.State.IN_PROGRESS &&
            accessRow.activeAttemptId != null
        val title = if (isInProgress) {
            pluralStringResource(
                R.plurals.checkout_accessBanner_existingInProgressTitle,
                accessRow.attemptsLeft,
                accessRow.attemptsLeft,
            )
        } else {
            pluralStringResource(
                R.plurals.checkout_accessBanner_existingTitle,
                accessRow.attemptsLeft,
                accessRow.attemptsLeft,
            )
        }
        PaymentBanner(
            tone = PaymentStatusTone.Info,
            title = title,
            body = "",
            modifier = Modifier.testTag(PaymentTestTags.CHECKOUT_ACCESS_BANNER),
            action = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PaymentPrimaryButton(
                        text = stringResource(
                            if (isInProgress) {
                                R.string.checkout_accessBanner_continueAttempt
                            } else {
                                R.string.checkout_accessBanner_enterExam
                            },
                        ),
                        onClick = {
                            val activeAttemptId = accessRow.activeAttemptId?.toString()
                            val entitlementId = accessRow.entitlementId?.toString()
                            when {
                                isInProgress && activeAttemptId != null -> onOpenAttempt(activeAttemptId)
                                entitlementId != null -> onOpenPrepare(entitlementId)
                                else -> onOpenMyExams()
                            }
                        },
                        modifier = Modifier.testTag(PaymentTestTags.CHECKOUT_ENTER_EXAM_BUTTON),
                    )
                    PaymentSecondaryButton(
                        text = stringResource(R.string.checkout_accessBanner_buyMore),
                        onClick = onBuyMoreClick,
                        modifier = Modifier.testTag(PaymentTestTags.CHECKOUT_BUY_MORE_BUTTON),
                    )
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CheckoutAccountCard(
    fullName: String,
    username: String,
    isBundle: Boolean,
    bundleExamCount: Int,
    onSwitchAccount: () -> Unit,
) {
    val colors = Thi360Theme.colors
    PaymentCard {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.checkout_account_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.text,
            )
            TextButton(
                onClick = onSwitchAccount,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(PaymentTestTags.CHECKOUT_SWITCH_ACCOUNT),
            ) {
                Text(
                    text = stringResource(R.string.checkout_account_switchAccount),
                    color = colors.primaryText,
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bgPage, RoundedCornerShape(10.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = fullName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.text,
            )
            Text(
                text = username,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
        }
        Text(
            text = if (isBundle) {
                pluralStringResource(
                    R.plurals.bundle_checkout_grantNotice,
                    bundleExamCount,
                    bundleExamCount,
                    username,
                )
            } else {
                stringResource(R.string.checkout_account_grantNotice, username)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CheckoutProductCard(
    product: ProductDetail,
    activeQuote: PriceQuote?,
    isBundle: Boolean,
    bundleAccessItems: List<BundleAccessExamItem>,
) {
    val colors = Thi360Theme.colors
    val locale = LocalAppLocale.current
    val resources = LocalResources.current
    val bundleItems = if (isBundle) product.items.orEmpty() else emptyList()
    val accessByCode = remember(bundleAccessItems) {
        bundleAccessItems.associateBy { it.code }
    }
    val versionNo = activeQuote?.versionNo ?: product.versionNo
    val attemptsGranted = activeQuote?.attemptsGranted ?: product.attemptsGranted
    val examKindLabel = PaymentFormatters.pickLocalizedName(
        product.examKind.nameVi,
        product.examKind.nameEn,
        locale,
    ).ifBlank { product.examKind.code }

    PaymentCard {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PaymentStatusChip(label = examKindLabel, tone = PaymentStatusTone.Neutral)
            PaymentStatusChip(label = formatChipLabel(product), tone = PaymentStatusTone.Info)
            if (isBundle) {
                PaymentStatusChip(
                    label = pluralStringResource(
                        R.plurals.payment_bundle_examCountValue,
                        bundleItems.size,
                        bundleItems.size,
                    ),
                    tone = PaymentStatusTone.Info,
                )
            }
            if (product.buildMode == "REVIEW") {
                PaymentStatusChip(
                    label = stringResource(R.string.storefront_review),
                    tone = PaymentStatusTone.Info,
                )
            }
            PaymentStatusChip(
                label = stringResource(R.string.checkout_product_version, versionNo),
                tone = PaymentStatusTone.Neutral,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(
                    if (isBundle) {
                        R.string.bundle_checkout_sectionTitle
                    } else {
                        R.string.checkout_product_sectionTitle
                    },
                ),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMuted,
            )
            Text(
                text = product.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.text,
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = if (isBundle) {
                    pluralStringResource(
                        R.plurals.storefront_units_attemptsPerExam,
                        attemptsGranted,
                        attemptsGranted,
                    )
                } else {
                    pluralStringResource(
                        R.plurals.checkout_product_attemptsGranted,
                        attemptsGranted,
                        attemptsGranted,
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
            )
            PaymentFormatters.formatIntRange(product.questionCount, locale)?.let { qRange ->
                Text(
                    text = stringResource(R.string.payment_units_questions_range, qRange),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                )
            }
            PaymentFormatters.formatIntRange(product.durationMinutes, locale)?.let { dRange ->
                Text(
                    text = stringResource(R.string.payment_units_minutes_range, dRange),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                )
            }
        }

        if (isBundle && bundleItems.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                bundleItems.forEach { item ->
                    BundleItemRow(
                        item = item,
                        attemptsGranted = activeQuote?.attemptsGranted ?: item.attemptsGranted,
                        existingAccess = accessByCode[item.code],
                    )
                }
                Text(
                    text = stringResource(R.string.bundle_checkout_existingNote),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
        }

        HorizontalDivider(color = colors.border)

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            product.resultView?.takeIf { it.isNotBlank() }?.let { rv ->
                Text(
                    text = PaymentFormatters.formatResultView(rv, resources),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.text,
                )
            }
            BenefitPillText(
                label = stringResource(R.string.payment_detail_rights_ai),
                enabled = product.benefits.aiExplanation,
            )
            BenefitPillText(
                label = stringResource(R.string.payment_detail_rights_strengths),
                enabled = product.benefits.strengthWeakness,
            )
            BenefitPillText(
                label = stringResource(R.string.payment_detail_rights_practice),
                enabled = product.benefits.practice,
            )
        }

        product.benefits.note?.takeIf { it.isNotBlank() }?.let { note ->
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BundleItemRow(
    item: BundleDetailItem,
    attemptsGranted: Int,
    existingAccess: BundleAccessExamItem?,
) {
    val colors = Thi360Theme.colors
    val locale = LocalAppLocale.current
    val resources = LocalResources.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgPage, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.text,
                modifier = Modifier.weight(1f),
            )
            if (item.buildMode == "REVIEW") {
                PaymentStatusChip(
                    label = stringResource(R.string.storefront_review),
                    tone = PaymentStatusTone.Info,
                )
            }
        }
        item.subjectName?.let { subject ->
            val subjectText = PaymentFormatters.pickLocalizedName(subject.nameVi, subject.nameEn, locale)
            if (subjectText.isNotBlank()) {
                Text(
                    text = subjectText,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PaymentFormatters.formatIntRange(item.questionCount, locale)?.let { q ->
                Text(
                    text = stringResource(R.string.payment_units_questions_range, q),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
            PaymentFormatters.formatIntRange(item.durationMinutes, locale)?.let { d ->
                Text(
                    text = stringResource(R.string.payment_units_minutes_range, d),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
            PaymentFormatters.formatDecimalRange(item.maxPoints, locale)?.let { pts ->
                Text(
                    text = stringResource(R.string.payment_detail_maxScore, pts),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
            Text(
                text = pluralStringResource(
                    R.plurals.storefront_units_attempts,
                    attemptsGranted,
                    attemptsGranted,
                ),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = colors.text,
            )
            item.resultView?.takeIf { it.isNotBlank() }?.let { rv ->
                Text(
                    text = PaymentFormatters.formatResultView(rv, resources),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
        }
        if (existingAccess != null && existingAccess.attemptsLeft > 0) {
            Text(
                text = pluralStringResource(
                    R.plurals.payment_access_attemptsLeft,
                    existingAccess.attemptsLeft,
                    existingAccess.attemptsLeft,
                ),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = colors.primaryText,
            )
        }
    }
}

@Composable
private fun BenefitPillText(label: String, enabled: Boolean) {
    val colors = Thi360Theme.colors
    val valueText = stringResource(
        if (enabled) R.string.payment_detail_rights_yes else R.string.payment_detail_rights_no,
    )
    Text(
        text = "$label: $valueText",
        style = MaterialTheme.typography.bodySmall,
        color = if (enabled) colors.text else colors.textMuted,
    )
}

@Composable
private fun formatChipLabel(product: ProductDetail): String {
    val locale = LocalAppLocale.current
    val scope = product.scopeLabel?.let {
        PaymentFormatters.pickLocalizedName(it.nameVi, it.nameEn, locale)
    }?.takeIf { it.isNotBlank() }
    if (scope != null) return scope
    return when (product.format) {
        "BUNDLE" -> stringResource(R.string.storefront_format_BUNDLE)
        "FULL" -> stringResource(R.string.storefront_format_FULL)
        "PART" -> stringResource(R.string.storefront_format_PART)
        "SUBJECT" -> stringResource(R.string.storefront_format_SUBJECT)
        else -> product.format
    }
}

@Composable
private fun CheckoutRefundCard(
    isBundle: Boolean,
    supportContact: SupportContact,
) {
    val colors = Thi360Theme.colors
    val supportText = PaymentFormatters.formatSupportSummary(supportContact)
        ?: stringResource(R.string.landing_support_pending)
    PaymentCard {
        Text(
            text = stringResource(R.string.checkout_refund_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.text,
        )
        Text(
            text = stringResource(
                if (isBundle) R.string.bundle_checkout_refundBody else R.string.checkout_refund_body,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
        )
        Text(
            text = "${stringResource(R.string.checkout_refund_supportLabel)} $supportText",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoucherBoxCard(
    quote: PriceQuote,
    voucherState: VoucherBoxState,
    isBundle: Boolean,
    controlsDisabled: Boolean,
    onInputChange: (String) -> Unit,
    onApply: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = Thi360Theme.colors
    val locale = LocalAppLocale.current
    val resources = LocalResources.current

    PaymentCard {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.checkout_voucher_sectionTitle),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.text,
            )
            if (quote.voucher != null) {
                TextButton(
                    onClick = onRemove,
                    enabled = !controlsDisabled,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(PaymentTestTags.CHECKOUT_VOUCHER_REMOVE),
                ) {
                    Text(
                        text = stringResource(R.string.checkout_voucher_remove),
                        color = colors.primaryText,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = voucherState.input,
                onValueChange = onInputChange,
                enabled = !controlsDisabled,
                singleLine = true,
                label = { Text(stringResource(R.string.checkout_voucher_label)) },
                placeholder = { Text(stringResource(R.string.checkout_voucher_placeholder)) },
                isError = voucherState.feedback is VoucherFeedback.EmptyInput ||
                    voucherState.feedback is VoucherFeedback.Error,
                modifier = Modifier
                    .weight(1f)
                    .testTag(PaymentTestTags.CHECKOUT_VOUCHER_INPUT),
            )
            PaymentSecondaryButton(
                text = stringResource(R.string.checkout_voucher_apply),
                onClick = onApply,
                enabled = !controlsDisabled,
                loading = voucherState.submitting,
                modifier = Modifier.testTag(PaymentTestTags.CHECKOUT_VOUCHER_APPLY),
            )
        }

        when (val feedback = voucherState.feedback) {
            VoucherFeedback.EmptyInput -> {
                Text(
                    text = stringResource(R.string.checkout_voucher_emptyInput),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.dangerText,
                    modifier = Modifier
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag(PaymentTestTags.CHECKOUT_VOUCHER_FEEDBACK),
                )
            }

            is VoucherFeedback.Error -> {
                Text(
                    text = stringResource(VoucherErrorMapper.stringRes(feedback.key, isBundle)),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.dangerText,
                    modifier = Modifier
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag(PaymentTestTags.CHECKOUT_VOUCHER_FEEDBACK),
                )
            }

            is VoucherFeedback.NotBetter -> {
                PaymentBanner(
                    tone = PaymentStatusTone.Info,
                    title = null,
                    body = stringResource(R.string.checkout_voucher_notBetter, feedback.code),
                    modifier = Modifier.testTag(PaymentTestTags.CHECKOUT_VOUCHER_FEEDBACK),
                )
            }

            is VoucherFeedback.Applied -> {
                val discountText = PaymentFormatters.formatPrice(feedback.discountVnd, locale, resources)
                val resId = when (feedback.variant) {
                    VoucherAppliedVariant.COMBINED -> R.string.checkout_voucher_appliedCombined
                    VoucherAppliedVariant.REPLACED_PROMO -> R.string.checkout_voucher_appliedReplacedPromo
                    VoucherAppliedVariant.APPLIED -> R.string.checkout_voucher_applied
                }
                PaymentBanner(
                    tone = PaymentStatusTone.Success,
                    title = null,
                    body = stringResource(resId, feedback.code, discountText),
                    modifier = Modifier.testTag(PaymentTestTags.CHECKOUT_VOUCHER_FEEDBACK),
                )
            }

            null -> {
                val appliedVoucher = quote.voucher
                if (appliedVoucher != null) {
                    val discountText = PaymentFormatters.formatPrice(
                        appliedVoucher.discountVnd,
                        locale,
                        resources,
                    )
                    val resId = if (quote.discountApplied == PriceQuote.DiscountApplied.COMBINED) {
                        R.string.checkout_voucher_appliedCombined
                    } else {
                        R.string.checkout_voucher_applied
                    }
                    PaymentBanner(
                        tone = PaymentStatusTone.Success,
                        title = null,
                        body = stringResource(resId, appliedVoucher.code, discountText),
                        modifier = Modifier.testTag(PaymentTestTags.CHECKOUT_VOUCHER_FEEDBACK),
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckoutSummaryCard(
    product: ProductDetail,
    state: CheckoutUiState,
    onRetryQuote: () -> Unit,
    onSubmitOrder: () -> Unit,
) {
    val colors = Thi360Theme.colors
    val locale = LocalAppLocale.current
    val resources = LocalResources.current
    val activeQuote = state.activeQuote

    PaymentCard(modifier = Modifier.testTag(PaymentTestTags.CHECKOUT_SUMMARY_CARD)) {
        Text(
            text = stringResource(R.string.checkout_summary_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = colors.text,
        )

        when {
            state.productUnavailable && activeQuote == null -> {
                SummaryLine(
                    label = stringResource(R.string.checkout_summary_listPrice),
                    value = PaymentFormatters.formatPrice(product.listPrice, locale, resources),
                )
                HorizontalDivider(color = colors.border)
                SummaryLine(
                    label = stringResource(R.string.checkout_summary_totalDue),
                    value = PaymentFormatters.formatPrice(product.price, locale, resources),
                    emphasized = true,
                )
                PaymentPrimaryButton(
                    text = stringResource(R.string.checkout_summary_ctaVnpay),
                    onClick = {},
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(PaymentTestTags.CHECKOUT_PAY_BUTTON),
                )
            }

            state.baseQuote is LoadState.Loading && activeQuote == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(PaymentTestTags.CHECKOUT_QUOTE_SKELETON),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PaymentSkeletonBlock(height = 20.dp)
                    PaymentSkeletonBlock(height = 20.dp, widthFraction = 0.75f)
                    PaymentSkeletonBlock(height = 48.dp)
                }
            }

            state.baseQuote is LoadState.Failed && activeQuote == null -> {
                Text(
                    text = stringResource(R.string.checkout_loadError_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.dangerText,
                )
                PaymentSecondaryButton(
                    text = stringResource(R.string.common_retry),
                    onClick = onRetryQuote,
                    modifier = Modifier.testTag(PaymentTestTags.CHECKOUT_QUOTE_RETRY_BUTTON),
                )
            }

            activeQuote != null -> {
                SummaryLine(
                    label = stringResource(R.string.checkout_summary_listPrice),
                    value = PaymentFormatters.formatPrice(activeQuote.listPrice, locale, resources),
                )

                val promo = activeQuote.promotion
                if (promo != null && promo.discountVnd > 0) {
                    val promoLabel = if (promo.name.isNotBlank()) {
                        stringResource(R.string.checkout_summary_promotion, promo.name)
                    } else {
                        stringResource(R.string.checkout_summary_promotionFallback)
                    }
                    SummaryLine(
                        label = promoLabel,
                        value = "-${PaymentFormatters.formatPrice(promo.discountVnd, locale, resources)}",
                        accent = true,
                    )
                }

                val voucher = activeQuote.voucher
                if (voucher != null && voucher.discountVnd > 0) {
                    SummaryLine(
                        label = stringResource(R.string.checkout_summary_voucherDiscount, voucher.code),
                        value = "-${PaymentFormatters.formatPrice(voucher.discountVnd, locale, resources)}",
                        accent = true,
                    )
                }

                HorizontalDivider(color = colors.border)

                val totalDisplay = if (activeQuote.finalPrice == 0L && activeQuote.voucher != null) {
                    stringResource(R.string.checkout_summary_freeVoucher)
                } else {
                    PaymentFormatters.formatPrice(activeQuote.finalPrice, locale, resources)
                }
                SummaryLine(
                    label = stringResource(R.string.checkout_summary_totalDue),
                    value = totalDisplay,
                    emphasized = true,
                )

                val submitState = state.submit
                if (submitState is CheckoutSubmitState.Failed) {
                    val errorText = when (val err = submitState.error) {
                        CheckoutSubmitError.RateLimited ->
                            stringResource(R.string.checkout_summary_errorRateLimited)
                        CheckoutSubmitError.PaymentAttemptsExceeded ->
                            stringResource(R.string.orders_errors_PAYMENT_ATTEMPTS_EXCEEDED)
                        is CheckoutSubmitError.Voucher ->
                            stringResource(VoucherErrorMapper.stringRes(err.key, state.isBundle))
                        CheckoutSubmitError.Generic ->
                            stringResource(R.string.checkout_summary_errorGeneric)
                    }
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = colors.dangerText,
                        modifier = Modifier
                            .semantics { liveRegion = LiveRegionMode.Polite }
                            .testTag(PaymentTestTags.CHECKOUT_SUBMIT_ERROR),
                    )
                }

                val ctaLabel = when {
                    activeQuote.finalPrice > 0L -> stringResource(R.string.checkout_summary_ctaVnpay)
                    activeQuote.voucher != null -> stringResource(R.string.checkout_summary_ctaFree)
                    else -> stringResource(R.string.checkout_summary_ctaFreeNoVoucher)
                }
                PaymentPrimaryButton(
                    text = ctaLabel,
                    onClick = onSubmitOrder,
                    enabled = state.isPayButtonEnabled,
                    loading = submitState is CheckoutSubmitState.Submitting || state.access is LoadState.Loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(PaymentTestTags.CHECKOUT_PAY_BUTTON),
                )
            }
        }
    }
}

@Composable
private fun SummaryLine(
    label: String,
    value: String,
    accent: Boolean = false,
    emphasized: Boolean = false,
) {
    val colors = Thi360Theme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = if (emphasized) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
            color = when {
                accent -> colors.primaryText
                emphasized -> colors.text
                else -> colors.textMuted
            },
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
        )
        Text(
            text = value,
            style = (if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
                .copy(fontFeatureSettings = "tnum"),
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium,
            color = when {
                accent || emphasized -> colors.primaryText
                else -> colors.text
            },
            textAlign = TextAlign.End,
        )
    }
}
