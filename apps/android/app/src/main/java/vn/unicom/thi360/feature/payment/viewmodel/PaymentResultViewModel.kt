package vn.unicom.thi360.feature.payment.viewmodel

import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.IdempotentCall
import vn.unicom.thi360.core.network.generated.model.OrderDetail
import vn.unicom.thi360.core.network.generated.model.OrderPaymentView
import vn.unicom.thi360.feature.payment.data.PaymentGateway
import vn.unicom.thi360.feature.payment.data.VnpayVerifyResult
import vn.unicom.thi360.feature.payment.domain.OrderActionErrorKey
import vn.unicom.thi360.feature.payment.domain.OrderActionErrorMapper
import vn.unicom.thi360.feature.payment.domain.OrderPoller
import vn.unicom.thi360.feature.payment.domain.PaymentFormatters
import vn.unicom.thi360.feature.payment.domain.PaymentResultEntry
import vn.unicom.thi360.feature.payment.domain.PaymentReturnLink
import vn.unicom.thi360.feature.payment.domain.PaymentUrlResolver
import vn.unicom.thi360.feature.payment.domain.PendingPaymentOrderStore
import vn.unicom.thi360.feature.payment.domain.PollDirective

sealed interface PaymentResultState {
    data object Verifying : PaymentResultState
    data object Loading : PaymentResultState
    data class Fulfilled(val order: OrderDetail) : PaymentResultState
    data class Failed(
        val order: OrderDetail,
        val reasonCode: String?,
        val latestPayment: OrderPaymentView?,
    ) : PaymentResultState
    data class Checking(
        val order: OrderDetail,
        val timedOut: Boolean,
        val pollError: Boolean = false,
    ) : PaymentResultState
    data class NoResultYet(
        val order: OrderDetail,
        val timedOut: Boolean,
        val pollError: Boolean = false,
    ) : PaymentResultState
    data class Closed(val order: OrderDetail) : PaymentResultState
    data object InvalidSignature : PaymentResultState
    data object VerifyNotFound : PaymentResultState
    data class NotOwned(val orderCode: String, val username: String) : PaymentResultState
    data object LoadError : PaymentResultState
}

data class PaymentBrowserEvent(
    val orderCode: String,
    val resolvedUrl: String,
)

data class PaymentResultUiState(
    val state: PaymentResultState,
    val isCheckingAgain: Boolean = false,
    val retryingPay: Boolean = false,
    val retryPayError: OrderActionErrorKey? = null,
    val browserEvent: PaymentBrowserEvent? = null,
) {
    val activeOrder: OrderDetail?
        get() = when (val current = state) {
            is PaymentResultState.Fulfilled -> current.order
            is PaymentResultState.Failed -> current.order
            is PaymentResultState.Checking -> current.order
            is PaymentResultState.NoResultYet -> current.order
            is PaymentResultState.Closed -> current.order
            else -> null
        }
}

class PaymentResultViewModel(
    initialEntry: PaymentResultEntry,
    private val gateway: PaymentGateway,
    private val pendingOrderStore: PendingPaymentOrderStore,
    private val apiBaseUrl: String,
    private val scope: CoroutineScope,
    private val usernameProvider: () -> String? = { null },
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val poller: OrderPoller = OrderPoller(clockMillis = clockMillis),
    keyGenerator: () -> String = { UUID.randomUUID().toString() },
    private val onOrderCodeResolved: (orderCode: String, verifiedFromLink: Boolean) -> Unit = { _, _ -> },
) {
    private val payIdempotency = IdempotentCall(keyGenerator = keyGenerator)
    private var currentEntry: PaymentResultEntry = initialEntry
    private var resolvedOrderCode: String? = (initialEntry as? PaymentResultEntry.ByOrder)?.orderCode
    private var verifiedFromLink: Boolean =
        (initialEntry as? PaymentResultEntry.ByOrder)?.verifiedFromLink == true
    private var verifiedPaymentState: String? = null
    private var verifiedTxnRef: String? = null
    private var verifiedResponseCode: String? = null
    private var lastProcessedLink: PaymentReturnLink? = null
    private var hasTimedOut: Boolean = false

    private val _uiState = MutableStateFlow(
        PaymentResultUiState(
            state = when (initialEntry) {
                is PaymentResultEntry.ByReturnLink -> PaymentResultState.Verifying
                is PaymentResultEntry.ByOrder -> PaymentResultState.Loading
            },
        ),
    )
    val uiState: StateFlow<PaymentResultUiState> = _uiState.asStateFlow()

    val currentPayIdempotencyKey: String?
        get() = payIdempotency.currentKey

    val currentOrderCode: String?
        get() = resolvedOrderCode

    fun start() {
        when (val entry = currentEntry) {
            is PaymentResultEntry.ByReturnLink -> runVerifyAndLoad(entry.link)
            is PaymentResultEntry.ByOrder -> {
                resolvedOrderCode = entry.orderCode
                verifiedFromLink = entry.verifiedFromLink
                if (entry.orderCode.isNotBlank()) {
                    onOrderCodeResolved(entry.orderCode, entry.verifiedFromLink)
                }
                scope.launch {
                    loadOrderOnce(entry.orderCode, isInitialLoad = true)
                }
            }
        }
    }

    fun acceptEntry(newEntry: PaymentResultEntry) {
        when (newEntry) {
            is PaymentResultEntry.ByReturnLink -> {
                if (lastProcessedLink == newEntry.link) {
                    return
                }
                poller.stop()
                hasTimedOut = false
                currentEntry = newEntry
                _uiState.update {
                    it.copy(
                        state = PaymentResultState.Verifying,
                        retryPayError = null,
                    )
                }
                runVerifyAndLoad(newEntry.link)
            }

            is PaymentResultEntry.ByOrder -> {
                if (resolvedOrderCode == newEntry.orderCode && _uiState.value.activeOrder != null) {
                    scope.launch {
                        loadOrderOnce(newEntry.orderCode, isInitialLoad = false)
                    }
                    return
                }
                poller.stop()
                hasTimedOut = false
                verifiedFromLink = newEntry.verifiedFromLink
                verifiedPaymentState = null
                verifiedTxnRef = null
                verifiedResponseCode = null
                lastProcessedLink = null
                currentEntry = newEntry
                resolvedOrderCode = newEntry.orderCode
                if (newEntry.orderCode.isNotBlank()) {
                    onOrderCodeResolved(newEntry.orderCode, newEntry.verifiedFromLink)
                }
                payIdempotency.reset()
                _uiState.update {
                    it.copy(
                        state = PaymentResultState.Loading,
                        retryPayError = null,
                    )
                }
                scope.launch {
                    loadOrderOnce(newEntry.orderCode, isInitialLoad = true)
                }
            }
        }
    }

    fun retryLoad() {
        when (val entry = currentEntry) {
            is PaymentResultEntry.ByReturnLink -> {
                if (resolvedOrderCode == null || !verifiedFromLink) {
                    _uiState.update { it.copy(state = PaymentResultState.Verifying) }
                    runVerifyAndLoad(entry.link)
                } else {
                    val code = resolvedOrderCode ?: return
                    _uiState.update { it.copy(state = PaymentResultState.Loading) }
                    scope.launch {
                        loadOrderOnce(code, isInitialLoad = true)
                    }
                }
            }

            is PaymentResultEntry.ByOrder -> {
                _uiState.update { it.copy(state = PaymentResultState.Loading) }
                scope.launch {
                    loadOrderOnce(entry.orderCode, isInitialLoad = true)
                }
            }
        }
    }

    fun checkAgain() {
        val code = resolvedOrderCode ?: return
        hasTimedOut = false
        _uiState.update { current ->
            val updatedState = when (val s = current.state) {
                is PaymentResultState.Checking -> s.copy(timedOut = false, pollError = false)
                is PaymentResultState.NoResultYet -> s.copy(timedOut = false, pollError = false)
                else -> s
            }
            current.copy(state = updatedState, isCheckingAgain = true)
        }
        poller.restartWindow(
            scope = scope,
            onPoll = {
                val directive = loadOrderOnce(code, isInitialLoad = false)
                _uiState.update { it.copy(isCheckingAgain = false) }
                directive
            },
            onTimeout = { handlePollTimeout() },
        )
    }

    fun retryPay() {
        val order = _uiState.value.activeOrder ?: return
        if (_uiState.value.retryingPay || payIdempotency.isInFlight) return

        _uiState.update { it.copy(retryingPay = true, retryPayError = null) }
        scope.launch {
            val result = payIdempotency.execute { idempotencyKey ->
                gateway.payOrder(order.code, idempotencyKey)
            } ?: return@launch

            when (result) {
                is ApiResult.Success -> {
                    poller.stop()
                    val resolvedUrl = PaymentUrlResolver.resolve(result.data.paymentUrl, apiBaseUrl)
                    pendingOrderStore.save(order.code, clockMillis())
                    _uiState.update {
                        it.copy(
                            retryingPay = false,
                            browserEvent = PaymentBrowserEvent(
                                orderCode = order.code,
                                resolvedUrl = resolvedUrl,
                            ),
                        )
                    }
                }

                is ApiResult.HttpFailure,
                is ApiResult.NetworkFailure,
                -> {
                    val mapped = OrderActionErrorMapper.mapFailure(result)
                    _uiState.update {
                        it.copy(
                            retryingPay = false,
                            retryPayError = mapped,
                        )
                    }
                    loadOrderOnce(order.code, isInitialLoad = false)
                }
            }
        }
    }

    fun onBrowserOpenFailed(orderCode: String) {
        pendingOrderStore.clearIfMatches(orderCode)
        _uiState.update {
            it.copy(
                retryPayError = OrderActionErrorKey.GENERIC,
                browserEvent = null,
            )
        }
    }

    fun consumeBrowserEvent() {
        _uiState.update { it.copy(browserEvent = null) }
    }

    fun onLifecyclePause() {
        poller.onPause()
    }

    fun onLifecycleResume() {
        poller.onResume()
    }

    fun onLeaveScreen() {
        poller.stop()
        pendingOrderStore.clear()
    }

    private fun runVerifyAndLoad(link: PaymentReturnLink) {
        lastProcessedLink = link
        scope.launch {
            when (val result = gateway.verifyVnpayReturn(link.vnpParams)) {
                is VnpayVerifyResult.Success -> {
                    val resp = result.response
                    resolvedOrderCode = resp.orderCode
                    verifiedFromLink = true
                    verifiedPaymentState = resp.paymentState
                    verifiedTxnRef = link.txnRef
                    verifiedResponseCode = link.responseCode
                    if (resp.orderCode.isNotBlank()) {
                        onOrderCodeResolved(resp.orderCode, true)
                    }
                    _uiState.update { it.copy(state = PaymentResultState.Loading) }
                    loadOrderOnce(resp.orderCode, isInitialLoad = true)
                }

                VnpayVerifyResult.InvalidSignature -> {
                    pendingOrderStore.clear()
                    _uiState.update { it.copy(state = PaymentResultState.InvalidSignature) }
                }

                VnpayVerifyResult.OrderNotFound -> {
                    pendingOrderStore.clear()
                    _uiState.update { it.copy(state = PaymentResultState.VerifyNotFound) }
                }

                is VnpayVerifyResult.Failure -> {
                    lastProcessedLink = null
                    _uiState.update { it.copy(state = PaymentResultState.LoadError) }
                }
            }
        }
    }

    private suspend fun loadOrderOnce(
        orderCode: String,
        isInitialLoad: Boolean,
    ): PollDirective {
        return when (val result = gateway.getOrder(orderCode)) {
            is ApiResult.Success -> applyOrder(result.data)

            is ApiResult.HttpFailure -> {
                if (result.statusCode == 404 || result.error?.code == "ORDER_NOT_FOUND") {
                    poller.stop()
                    pendingOrderStore.clearIfMatches(orderCode)
                    val username = resolveUsername()
                    _uiState.update {
                        it.copy(state = PaymentResultState.NotOwned(orderCode = orderCode, username = username))
                    }
                    PollDirective.STOP
                } else {
                    handleOrderLoadError(isInitialLoad)
                }
            }

            is ApiResult.NetworkFailure -> handleOrderLoadError(isInitialLoad)
        }
    }

    private fun handleOrderLoadError(isInitialLoad: Boolean): PollDirective {
        val current = _uiState.value.state
        return when (current) {
            is PaymentResultState.Checking -> {
                _uiState.update {
                    it.copy(state = current.copy(pollError = true))
                }
                if (hasTimedOut) PollDirective.STOP else PollDirective.CONTINUE
            }

            is PaymentResultState.NoResultYet -> {
                _uiState.update {
                    it.copy(state = current.copy(pollError = true))
                }
                if (hasTimedOut) PollDirective.STOP else PollDirective.CONTINUE
            }

            else -> {
                if (isInitialLoad || _uiState.value.activeOrder == null) {
                    _uiState.update { it.copy(state = PaymentResultState.LoadError) }
                }
                PollDirective.STOP
            }
        }
    }

    private fun applyOrder(order: OrderDetail): PollDirective {
        resolvedOrderCode = order.code
        if (order.code.isNotBlank()) {
            onOrderCodeResolved(order.code, verifiedFromLink)
        }
        when (order.state) {
            OrderDetail.State.FULFILLED -> {
                poller.stop()
                pendingOrderStore.clearIfMatches(order.code)
                _uiState.update { it.copy(state = PaymentResultState.Fulfilled(order)) }
                return PollDirective.STOP
            }

            OrderDetail.State.EXPIRED,
            OrderDetail.State.CANCELLED,
            OrderDetail.State.REPLACED,
            OrderDetail.State.REFUNDED,
            -> {
                poller.stop()
                pendingOrderStore.clearIfMatches(order.code)
                _uiState.update { it.copy(state = PaymentResultState.Closed(order)) }
                return PollDirective.STOP
            }

            OrderDetail.State.PAID -> {
                _uiState.update {
                    it.copy(
                        state = PaymentResultState.Checking(
                            order = order,
                            timedOut = hasTimedOut,
                            pollError = false,
                        ),
                    )
                }
                ensurePollingStarted(order.code)
                return if (hasTimedOut) PollDirective.STOP else PollDirective.CONTINUE
            }

            OrderDetail.State.PENDING_PAYMENT -> {
                val latestPayment = PaymentFormatters.latestPayment(order)
                val verifiedFailed = verifiedFromLink &&
                    verifiedPaymentState == "FAILED" &&
                    (latestPayment == null || latestPayment.txnRef == verifiedTxnRef)
                if (latestPayment?.state == OrderPaymentView.State.FAILED || verifiedFailed) {
                    poller.stop()
                    pendingOrderStore.clearIfMatches(order.code)
                    val reasonCode = if (latestPayment?.state == OrderPaymentView.State.FAILED) {
                        latestPayment.responseCode
                    } else {
                        latestPayment?.responseCode ?: verifiedResponseCode
                    }
                    _uiState.update {
                        it.copy(
                            state = PaymentResultState.Failed(
                                order = order,
                                reasonCode = reasonCode,
                                latestPayment = latestPayment,
                            ),
                        )
                    }
                    return PollDirective.STOP
                }

                if (verifiedFromLink) {
                    _uiState.update {
                        it.copy(
                            state = PaymentResultState.Checking(
                                order = order,
                                timedOut = hasTimedOut,
                                pollError = false,
                            ),
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            state = PaymentResultState.NoResultYet(
                                order = order,
                                timedOut = hasTimedOut,
                                pollError = false,
                            ),
                        )
                    }
                }
                ensurePollingStarted(order.code)
                return if (hasTimedOut) PollDirective.STOP else PollDirective.CONTINUE
            }
        }
    }

    private fun ensurePollingStarted(orderCode: String) {
        if (!hasTimedOut && !poller.isRunning) {
            poller.start(
                scope = scope,
                onPoll = { loadOrderOnce(orderCode, isInitialLoad = false) },
                onTimeout = { handlePollTimeout() },
            )
        }
    }

    private fun handlePollTimeout() {
        hasTimedOut = true
        _uiState.update { current ->
            val nextState = when (val s = current.state) {
                is PaymentResultState.Checking -> s.copy(timedOut = true)
                is PaymentResultState.NoResultYet -> s.copy(timedOut = true)
                else -> s
            }
            current.copy(state = nextState, isCheckingAgain = false)
        }
    }

    private suspend fun resolveUsername(): String {
        val local = usernameProvider()?.takeIf { it.isNotBlank() }
        if (local != null) return local
        return when (val me = gateway.fetchCurrentUser()) {
            is ApiResult.Success -> me.data.username
            else -> ""
        }
    }
}
