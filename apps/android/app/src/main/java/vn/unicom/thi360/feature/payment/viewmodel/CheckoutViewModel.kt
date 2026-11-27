package vn.unicom.thi360.feature.payment.viewmodel

import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.IdempotentCall
import vn.unicom.thi360.core.network.generated.model.CreateOrderRequest
import vn.unicom.thi360.core.network.generated.model.CreateOrderResponse
import vn.unicom.thi360.core.network.generated.model.ExamAccessItem
import vn.unicom.thi360.core.network.generated.model.MeResponse
import vn.unicom.thi360.core.network.generated.model.PriceQuote
import vn.unicom.thi360.core.network.generated.model.PriceQuoteRequest
import vn.unicom.thi360.core.network.generated.model.Product
import vn.unicom.thi360.core.network.generated.model.ProductDetail
import vn.unicom.thi360.core.ui.LoadFailureKind
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.core.ui.toLoadFailed
import vn.unicom.thi360.feature.payment.data.CreateOrderOutcome
import vn.unicom.thi360.feature.payment.data.ExamAccessResult
import vn.unicom.thi360.feature.payment.data.PaymentGateway
import vn.unicom.thi360.feature.payment.data.PriceQuoteResult
import vn.unicom.thi360.feature.payment.data.ProductLookupResult
import vn.unicom.thi360.feature.payment.domain.PaymentUrlResolver
import vn.unicom.thi360.feature.payment.domain.PendingPaymentOrderStore
import vn.unicom.thi360.feature.payment.domain.SeenPrice
import vn.unicom.thi360.feature.payment.domain.VoucherErrorKey

enum class VoucherAppliedVariant {
    APPLIED,
    COMBINED,
    REPLACED_PROMO,
}

sealed interface VoucherFeedback {
    data object EmptyInput : VoucherFeedback
    data class Error(val key: VoucherErrorKey) : VoucherFeedback
    data class NotBetter(val code: String) : VoucherFeedback
    data class Applied(
        val variant: VoucherAppliedVariant,
        val code: String,
        val discountVnd: Long,
    ) : VoucherFeedback
}

data class VoucherBoxState(
    val input: String = "",
    val submitting: Boolean = false,
    val feedback: VoucherFeedback? = null,
)

sealed interface CheckoutSubmitError {
    data object RateLimited : CheckoutSubmitError
    data object PaymentAttemptsExceeded : CheckoutSubmitError
    data class Voucher(val key: VoucherErrorKey) : CheckoutSubmitError
    data object Generic : CheckoutSubmitError
}

sealed interface CheckoutSubmitState {
    data object Idle : CheckoutSubmitState
    data object Submitting : CheckoutSubmitState
    data class Failed(val error: CheckoutSubmitError) : CheckoutSubmitState
}

data class AccountConflict(
    val previousUsername: String,
    val current: MeResponse,
)

sealed interface CheckoutNavigationEvent {
    data class OpenBrowser(val orderCode: String, val resolvedUrl: String) : CheckoutNavigationEvent
    data class OpenPaymentResult(val orderCode: String) : CheckoutNavigationEvent
}

data class CheckoutUiState(
    val code: String,
    val seenPrice: SeenPrice,
    val user: LoadState<MeResponse> = LoadState.Loading,
    val acknowledgedUser: MeResponse? = null,
    val accountConflict: AccountConflict? = null,
    val product: LoadState<ProductDetail> = LoadState.Loading,
    val goneProduct: Product? = null,
    val productUnavailable: Boolean = false,
    val access: LoadState<ExamAccessItem?> = LoadState.Loading,
    val baseQuote: LoadState<PriceQuote> = LoadState.Loading,
    val overrideQuote: PriceQuote? = null,
    val voucher: VoucherBoxState = VoucherBoxState(),
    val priceChangedSeenOverride: Long? = null,
    val submit: CheckoutSubmitState = CheckoutSubmitState.Idle,
    val navigationEvent: CheckoutNavigationEvent? = null,
) {
    val activeQuote: PriceQuote?
        get() = overrideQuote ?: (baseQuote as? LoadState.Loaded)?.value

    val loadedProduct: ProductDetail?
        get() = (product as? LoadState.Loaded)?.value

    val isBundle: Boolean
        get() = loadedProduct?.let {
            it.productType == "BUNDLE" || it.format == "BUNDLE"
        } == true

    val accessRow: ExamAccessItem?
        get() = (access as? LoadState.Loaded)?.value

    val isBlockedByChecking: Boolean
        get() = accessRow?.let {
            it.state == ExamAccessItem.State.CHECKING || it.hasPendingFulfillment
        } == true

    val hasExistingAccess: Boolean
        get() = accessRow?.let {
            it.state == ExamAccessItem.State.CAN_START || it.state == ExamAccessItem.State.IN_PROGRESS
        } == true

    val accessUnavailable: Boolean
        get() = access !is LoadState.Loaded

    val displayUser: MeResponse?
        get() = accountConflict?.current ?: acknowledgedUser ?: (user as? LoadState.Loaded)?.value

    val showPriceChangedBanner: Boolean
        get() {
            val quote = activeQuote ?: return false
            if (priceChangedSeenOverride != null) return true
            val base = (baseQuote as? LoadState.Loaded)?.value
            if (base?.changed == true) return true
            if (overrideQuote?.voucher == null && quote.changed) return true
            if (seenPrice.versionNo != null && quote.versionNo != seenPrice.versionNo) return true
            if (base != null && quote.versionNo != base.versionNo) return true
            if (overrideQuote == null && seenPrice.price != null && quote.finalPrice != seenPrice.price) return true
            return false
        }

    val bannerSeenPrice: Long?
        get() = priceChangedSeenOverride ?: seenPrice.price

    val isVoucherControlsDisabled: Boolean
        get() = voucher.submitting ||
            submit is CheckoutSubmitState.Submitting ||
            isBlockedByChecking ||
            productUnavailable ||
            accountConflict != null

    val isPayButtonEnabled: Boolean
        get() = activeQuote != null &&
            acknowledgedUser != null &&
            !accessUnavailable &&
            !isBlockedByChecking &&
            !productUnavailable &&
            accountConflict == null &&
            submit !is CheckoutSubmitState.Submitting &&
            !voucher.submitting
}

class CheckoutViewModel(
    val code: String,
    val seenPrice: SeenPrice = SeenPrice(),
    private val gateway: PaymentGateway,
    private val pendingOrderStore: PendingPaymentOrderStore,
    private val apiBaseUrl: String,
    private val scope: CoroutineScope,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val isOnline: () -> Boolean = { true },
    keyGenerator: () -> String = { UUID.randomUUID().toString() },
    initialUser: MeResponse? = null,
) {
    private val createOrderIdempotency = IdempotentCall(keyGenerator = keyGenerator)
    private val voucherInFlight = AtomicBoolean(false)

    private val _state = MutableStateFlow(
        CheckoutUiState(
            code = code,
            seenPrice = seenPrice,
            user = if (initialUser != null) LoadState.Loaded(initialUser) else LoadState.Loading,
            acknowledgedUser = initialUser,
        ),
    )
    val state: StateFlow<CheckoutUiState> = _state.asStateFlow()

    val currentCreateIdempotencyKey: String?
        get() = createOrderIdempotency.currentKey

    fun loadInitial() {
        scope.launch {
            val userDeferred = async {
                val existing = _state.value.acknowledgedUser
                if (existing != null) ApiResult.Success(existing) else gateway.fetchCurrentUser()
            }
            val productDeferred = async { gateway.loadProduct(code) }

            val userResult = userDeferred.await()
            val productResult = productDeferred.await()

            applyUserResult(userResult)
            val loadedProduct = applyProductResult(productResult)

            val canQueryDependent = _state.value.acknowledgedUser != null &&
                loadedProduct != null &&
                _state.value.goneProduct == null
            if (canQueryDependent) {
                val accessDeferred = async { gateway.queryExamAccess(listOf(code)) }
                val quoteDeferred = if (!_state.value.productUnavailable) {
                    async { fetchBaseQuoteInternal() }
                } else {
                    null
                }
                applyAccessResult(accessDeferred.await())
                quoteDeferred?.await()
            }
        }
    }

    fun retryInitial() {
        _state.update {
            it.copy(
                user = if (it.acknowledgedUser != null) LoadState.Loaded(it.acknowledgedUser) else LoadState.Loading,
                product = LoadState.Loading,
                goneProduct = null,
                productUnavailable = false,
                access = LoadState.Loading,
                baseQuote = LoadState.Loading,
                overrideQuote = null,
                submit = CheckoutSubmitState.Idle,
            )
        }
        loadInitial()
    }

    fun retryAccess() {
        scope.launch {
            _state.update { it.copy(access = LoadState.Loading) }
            val result = gateway.queryExamAccess(listOf(code))
            if (result is ExamAccessResult.Forbidden) {
                applyUserResult(gateway.fetchCurrentUser())
            }
            applyAccessResult(result)
        }
    }

    fun retryBaseQuote() {
        scope.launch {
            _state.update { it.copy(baseQuote = LoadState.Loading) }
            fetchBaseQuoteInternal()
        }
    }

    fun onVoucherInputChanged(input: String) {
        _state.update { current ->
            val nextFeedback = when (current.voucher.feedback) {
                is VoucherFeedback.EmptyInput,
                is VoucherFeedback.Error,
                -> null
                else -> current.voucher.feedback
            }
            current.copy(
                voucher = current.voucher.copy(
                    input = input,
                    feedback = nextFeedback,
                ),
            )
        }
    }

    fun applyVoucher() {
        val current = _state.value
        if (current.isVoucherControlsDisabled) return
        val trimmed = current.voucher.input.trim()
        if (trimmed.isEmpty()) {
            _state.update {
                it.copy(voucher = it.voucher.copy(feedback = VoucherFeedback.EmptyInput))
            }
            return
        }
        if (!voucherInFlight.compareAndSet(false, true)) return

        val previousQuote = current.activeQuote
        _state.update {
            it.copy(voucher = it.voucher.copy(submitting = true, feedback = null))
        }
        scope.launch {
            try {
                val result = gateway.requestQuote(
                    PriceQuoteRequest(
                        code = code,
                        seenPrice = null,
                        seenVersionNo = seenPrice.versionNo,
                        voucherCode = trimmed,
                    ),
                )
                when (result) {
                    is PriceQuoteResult.Success -> {
                        createOrderIdempotency.reset()
                        val nextQuote = result.quote
                        val nextFeedback: VoucherFeedback? = when {
                            nextQuote.voucherNotBetter -> VoucherFeedback.NotBetter(trimmed)
                            nextQuote.voucher != null -> {
                                val variant = when {
                                    nextQuote.discountApplied == PriceQuote.DiscountApplied.COMBINED ->
                                        VoucherAppliedVariant.COMBINED
                                    previousQuote?.promotion != null && nextQuote.promotion == null ->
                                        VoucherAppliedVariant.REPLACED_PROMO
                                    else -> VoucherAppliedVariant.APPLIED
                                }
                                VoucherFeedback.Applied(
                                    variant = variant,
                                    code = nextQuote.voucher.code,
                                    discountVnd = nextQuote.voucher.discountVnd,
                                )
                            }
                            else -> null
                        }
                        _state.update {
                            it.copy(
                                overrideQuote = nextQuote,
                                submit = CheckoutSubmitState.Idle,
                                voucher = it.voucher.copy(
                                    input = nextQuote.voucher?.code ?: it.voucher.input,
                                    submitting = false,
                                    feedback = nextFeedback,
                                ),
                            )
                        }
                    }

                    is PriceQuoteResult.Gone -> {
                        _state.update {
                            it.copy(
                                goneProduct = result.product,
                                product = LoadState.Failed(LoadFailureKind.Gone, canRetry = false),
                                voucher = it.voucher.copy(submitting = false),
                            )
                        }
                    }

                    PriceQuoteResult.ProductUnavailable -> {
                        _state.update {
                            it.copy(
                                productUnavailable = true,
                                voucher = it.voucher.copy(submitting = false),
                            )
                        }
                    }

                    is PriceQuoteResult.VoucherRejected -> {
                        _state.update {
                            it.copy(
                                voucher = it.voucher.copy(
                                    submitting = false,
                                    feedback = VoucherFeedback.Error(result.key),
                                ),
                            )
                        }
                    }

                    PriceQuoteResult.RateLimited -> {
                        _state.update {
                            it.copy(
                                voucher = it.voucher.copy(
                                    submitting = false,
                                    feedback = VoucherFeedback.Error(VoucherErrorKey.RATE_LIMITED),
                                ),
                            )
                        }
                    }

                    PriceQuoteResult.Unauthorized,
                    PriceQuoteResult.NotFound,
                    is PriceQuoteResult.Failure,
                    -> {
                        _state.update {
                            it.copy(
                                voucher = it.voucher.copy(
                                    submitting = false,
                                    feedback = VoucherFeedback.Error(VoucherErrorKey.GENERIC),
                                ),
                            )
                        }
                    }
                }
            } finally {
                voucherInFlight.set(false)
            }
        }
    }

    fun removeVoucher() {
        val current = _state.value
        if (current.isVoucherControlsDisabled) return
        if (!voucherInFlight.compareAndSet(false, true)) return

        _state.update {
            it.copy(voucher = it.voucher.copy(submitting = true, feedback = null))
        }
        scope.launch {
            try {
                val result = gateway.requestQuote(
                    PriceQuoteRequest(
                        code = code,
                        seenPrice = seenPrice.price,
                        seenVersionNo = seenPrice.versionNo,
                        voucherCode = null,
                    ),
                )
                when (result) {
                    is PriceQuoteResult.Success -> {
                        createOrderIdempotency.reset()
                        _state.update {
                            it.copy(
                                overrideQuote = result.quote,
                                submit = CheckoutSubmitState.Idle,
                                voucher = VoucherBoxState(input = "", submitting = false, feedback = null),
                            )
                        }
                    }

                    is PriceQuoteResult.Gone -> {
                        _state.update {
                            it.copy(
                                goneProduct = result.product,
                                product = LoadState.Failed(LoadFailureKind.Gone, canRetry = false),
                                voucher = it.voucher.copy(submitting = false),
                            )
                        }
                    }

                    PriceQuoteResult.ProductUnavailable -> {
                        _state.update {
                            it.copy(
                                productUnavailable = true,
                                voucher = it.voucher.copy(submitting = false),
                            )
                        }
                    }

                    PriceQuoteResult.RateLimited -> {
                        _state.update {
                            it.copy(
                                voucher = it.voucher.copy(
                                    submitting = false,
                                    feedback = VoucherFeedback.Error(VoucherErrorKey.RATE_LIMITED),
                                ),
                            )
                        }
                    }

                    is PriceQuoteResult.VoucherRejected -> {
                        _state.update {
                            it.copy(
                                voucher = it.voucher.copy(
                                    submitting = false,
                                    feedback = VoucherFeedback.Error(result.key),
                                ),
                            )
                        }
                    }

                    PriceQuoteResult.Unauthorized,
                    PriceQuoteResult.NotFound,
                    is PriceQuoteResult.Failure,
                    -> {
                        _state.update {
                            it.copy(
                                voucher = it.voucher.copy(
                                    submitting = false,
                                    feedback = VoucherFeedback.Error(VoucherErrorKey.GENERIC),
                                ),
                            )
                        }
                    }
                }
            } finally {
                voucherInFlight.set(false)
            }
        }
    }

    fun submitOrder() {
        val current = _state.value
        val quote = current.activeQuote ?: return
        val user = current.acknowledgedUser ?: return
        if (!current.isPayButtonEnabled || createOrderIdempotency.isInFlight) return

        val request = CreateOrderRequest(
            code = code,
            expectedAccountId = user.id,
            expectedAmountDue = quote.finalPrice,
            expectedVersionNo = quote.versionNo,
            voucherCode = quote.voucher?.code,
        )

        _state.update { it.copy(submit = CheckoutSubmitState.Submitting) }
        scope.launch {
            val callResult = createOrderIdempotency.execute { idempotencyKey ->
                gateway.createOrder(request, idempotencyKey)
            } ?: return@launch

            when (callResult) {
                is ApiResult.Success -> handleCreateOutcome(callResult.data, quote, user)
                is ApiResult.HttpFailure -> {
                    val error = if (callResult.statusCode == 429 || callResult.error?.code == "RATE_LIMITED") {
                        CheckoutSubmitError.RateLimited
                    } else {
                        CheckoutSubmitError.Generic
                    }
                    _state.update { it.copy(submit = CheckoutSubmitState.Failed(error)) }
                }
                is ApiResult.NetworkFailure -> {
                    _state.update { it.copy(submit = CheckoutSubmitState.Failed(CheckoutSubmitError.Generic)) }
                }
            }
        }
    }

    fun onBrowserOpenFailed(orderCode: String) {
        pendingOrderStore.clearIfMatches(orderCode)
        _state.update {
            it.copy(
                submit = CheckoutSubmitState.Failed(CheckoutSubmitError.Generic),
                navigationEvent = null,
            )
        }
    }

    fun consumeNavigationEvent() {
        _state.update { it.copy(navigationEvent = null) }
    }

    fun acknowledgeNewAccount() {
        val conflict = _state.value.accountConflict ?: return
        createOrderIdempotency.reset()
        _state.update {
            it.copy(
                user = LoadState.Loaded(conflict.current),
                acknowledgedUser = conflict.current,
                accountConflict = null,
                overrideQuote = null,
                priceChangedSeenOverride = null,
                voucher = VoucherBoxState(),
                submit = CheckoutSubmitState.Idle,
                access = LoadState.Loading,
                baseQuote = LoadState.Loading,
            )
        }
        scope.launch {
            val accessDeferred = async { gateway.queryExamAccess(listOf(code)) }
            val quoteDeferred = async { fetchBaseQuoteInternal() }
            applyAccessResult(accessDeferred.await())
            quoteDeferred.await()
        }
    }

    private suspend fun handleCreateOutcome(
        outcome: CreateOrderOutcome,
        submittedQuote: PriceQuote,
        submittedUser: MeResponse,
    ) {
        when (outcome) {
            is CreateOrderOutcome.Success -> {
                val resp = outcome.response
                val navEvent = when (resp.next) {
                    CreateOrderResponse.Next.FULFILLED,
                    CreateOrderResponse.Next.CHECKING,
                    -> CheckoutNavigationEvent.OpenPaymentResult(resp.order.code)

                    CreateOrderResponse.Next.REDIRECT -> {
                        val rawUrl = resp.paymentUrl
                        if (!rawUrl.isNullOrBlank()) {
                            val resolved = PaymentUrlResolver.resolve(rawUrl, apiBaseUrl)
                            pendingOrderStore.save(resp.order.code, clockMillis())
                            CheckoutNavigationEvent.OpenBrowser(resp.order.code, resolved)
                        } else {
                            CheckoutNavigationEvent.OpenPaymentResult(resp.order.code)
                        }
                    }
                }
                _state.update {
                    it.copy(
                        submit = CheckoutSubmitState.Idle,
                        navigationEvent = navEvent,
                    )
                }
            }

            is CreateOrderOutcome.PriceChanged -> {
                val newQuote = outcome.quote
                _state.update {
                    it.copy(
                        priceChangedSeenOverride = submittedQuote.finalPrice,
                        overrideQuote = newQuote,
                        voucher = VoucherBoxState(
                            input = newQuote.voucher?.code.orEmpty(),
                            submitting = false,
                            feedback = null,
                        ),
                        submit = CheckoutSubmitState.Idle,
                    )
                }
            }

            CreateOrderOutcome.AccountChanged -> {
                when (val refreshed = gateway.fetchCurrentUser()) {
                    is ApiResult.Success -> {
                        val current = refreshed.data
                        if (current.id != submittedUser.id) {
                            _state.update {
                                it.copy(
                                    user = LoadState.Loaded(current),
                                    accountConflict = AccountConflict(
                                        previousUsername = submittedUser.username,
                                        current = current,
                                    ),
                                    submit = CheckoutSubmitState.Idle,
                                )
                            }
                        } else {
                            _state.update {
                                it.copy(submit = CheckoutSubmitState.Failed(CheckoutSubmitError.Generic))
                            }
                        }
                    }

                    else -> {
                        _state.update {
                            it.copy(submit = CheckoutSubmitState.Failed(CheckoutSubmitError.Generic))
                        }
                    }
                }
            }

            is CreateOrderOutcome.ProductGone -> {
                _state.update {
                    it.copy(
                        goneProduct = outcome.product,
                        product = LoadState.Failed(LoadFailureKind.Gone, canRetry = false),
                        submit = CheckoutSubmitState.Idle,
                    )
                }
            }

            CreateOrderOutcome.ProductUnavailable -> {
                _state.update {
                    it.copy(
                        productUnavailable = true,
                        submit = CheckoutSubmitState.Idle,
                    )
                }
            }

            is CreateOrderOutcome.VoucherRejected -> {
                _state.update {
                    it.copy(
                        overrideQuote = null,
                        voucher = VoucherBoxState(),
                        submit = CheckoutSubmitState.Failed(CheckoutSubmitError.Voucher(outcome.key)),
                        baseQuote = LoadState.Loading,
                    )
                }
                fetchBaseQuoteInternal()
            }

            CreateOrderOutcome.PaymentAttemptsExceeded -> {
                _state.update {
                    it.copy(submit = CheckoutSubmitState.Failed(CheckoutSubmitError.PaymentAttemptsExceeded))
                }
            }

            CreateOrderOutcome.RateLimited -> {
                _state.update {
                    it.copy(submit = CheckoutSubmitState.Failed(CheckoutSubmitError.RateLimited))
                }
            }

            CreateOrderOutcome.Unauthorized -> {
                _state.update { it.copy(submit = CheckoutSubmitState.Idle) }
            }

            is CreateOrderOutcome.Failure -> {
                _state.update {
                    it.copy(submit = CheckoutSubmitState.Failed(CheckoutSubmitError.Generic))
                }
            }
        }
    }

    private suspend fun fetchBaseQuoteInternal() {
        val result = gateway.requestQuote(
            PriceQuoteRequest(
                code = code,
                seenPrice = seenPrice.price,
                seenVersionNo = seenPrice.versionNo,
                voucherCode = null,
            ),
        )
        when (result) {
            is PriceQuoteResult.Success -> {
                _state.update { it.copy(baseQuote = LoadState.Loaded(result.quote)) }
            }

            is PriceQuoteResult.Gone -> {
                _state.update {
                    it.copy(
                        goneProduct = result.product,
                        product = LoadState.Failed(LoadFailureKind.Gone, canRetry = false),
                        baseQuote = LoadState.Failed(LoadFailureKind.Gone, canRetry = false),
                    )
                }
            }

            PriceQuoteResult.ProductUnavailable -> {
                _state.update {
                    it.copy(
                        productUnavailable = true,
                        baseQuote = LoadState.Failed(
                            LoadFailureKind.Conflict("PRODUCT_UNAVAILABLE"),
                            canRetry = false,
                        ),
                    )
                }
            }

            PriceQuoteResult.NotFound -> {
                _state.update {
                    it.copy(baseQuote = LoadState.Failed(LoadFailureKind.NotFound, canRetry = true))
                }
            }

            PriceQuoteResult.RateLimited -> {
                _state.update {
                    it.copy(baseQuote = LoadState.Failed(LoadFailureKind.Server, canRetry = true))
                }
            }

            PriceQuoteResult.Unauthorized,
            is PriceQuoteResult.VoucherRejected,
            -> {
                _state.update {
                    it.copy(baseQuote = LoadState.Failed(LoadFailureKind.Server, canRetry = true))
                }
            }

            is PriceQuoteResult.Failure -> {
                val failed = when (val err = result.error) {
                    is ApiResult.HttpFailure -> err.toLoadFailed().copy(canRetry = true)
                    is ApiResult.NetworkFailure -> err.toLoadFailed(isOnline())
                    is ApiResult.Success -> LoadState.Failed(LoadFailureKind.Server, canRetry = true)
                }
                _state.update { it.copy(baseQuote = failed) }
            }
        }
    }

    private fun applyUserResult(result: ApiResult<MeResponse>) {
        when (result) {
            is ApiResult.Success -> {
                val me = result.data
                _state.update { current ->
                    val baseline = current.acknowledgedUser ?: me
                    val conflict = if (baseline.id != me.id) {
                        AccountConflict(previousUsername = baseline.username, current = me)
                    } else {
                        current.accountConflict
                    }
                    current.copy(
                        user = LoadState.Loaded(me),
                        acknowledgedUser = baseline,
                        accountConflict = conflict,
                    )
                }
            }

            is ApiResult.HttpFailure -> {
                _state.update { it.copy(user = result.toLoadFailed()) }
            }

            is ApiResult.NetworkFailure -> {
                _state.update { it.copy(user = result.toLoadFailed(isOnline())) }
            }
        }
    }

    private fun applyProductResult(result: ProductLookupResult): ProductDetail? = when (result) {
        is ProductLookupResult.Success -> {
            _state.update {
                it.copy(
                    product = LoadState.Loaded(result.product),
                    goneProduct = null,
                    productUnavailable = false,
                )
            }
            result.product
        }

        ProductLookupResult.NotFound -> {
            _state.update {
                it.copy(product = LoadState.Failed(LoadFailureKind.NotFound, canRetry = false))
            }
            null
        }

        is ProductLookupResult.Gone -> {
            _state.update {
                it.copy(
                    goneProduct = result.product,
                    product = LoadState.Failed(LoadFailureKind.Gone, canRetry = false),
                )
            }
            null
        }

        is ProductLookupResult.Unavailable -> {
            if (result.product != null) {
                _state.update {
                    it.copy(
                        product = LoadState.Loaded(result.product),
                        productUnavailable = true,
                    )
                }
                result.product
            } else {
                _state.update {
                    it.copy(
                        product = LoadState.Failed(
                            LoadFailureKind.Conflict("PRODUCT_UNAVAILABLE"),
                            canRetry = false,
                        ),
                        productUnavailable = true,
                    )
                }
                null
            }
        }

        is ProductLookupResult.Failure -> {
            val failed = when (val err = result.error) {
                is ApiResult.HttpFailure -> err.toLoadFailed().copy(canRetry = true)
                is ApiResult.NetworkFailure -> err.toLoadFailed(isOnline())
                is ApiResult.Success -> LoadState.Failed(LoadFailureKind.Server, canRetry = true)
            }
            _state.update { it.copy(product = failed) }
            null
        }
    }

    private fun applyAccessResult(result: ExamAccessResult) {
        when (result) {
            is ExamAccessResult.Success -> {
                val match = result.items.firstOrNull { it.code == code }
                _state.update { it.copy(access = LoadState.Loaded(match)) }
            }

            ExamAccessResult.Forbidden -> {
                _state.update {
                    it.copy(access = LoadState.Failed(LoadFailureKind.Forbidden, canRetry = true))
                }
            }

            is ExamAccessResult.Failure -> {
                val failed = when (val err = result.error) {
                    is ApiResult.HttpFailure -> err.toLoadFailed().copy(canRetry = true)
                    is ApiResult.NetworkFailure -> err.toLoadFailed(isOnline())
                    is ApiResult.Success -> LoadState.Failed(LoadFailureKind.Server, canRetry = true)
                }
                _state.update { it.copy(access = failed) }
            }
        }
    }
}
