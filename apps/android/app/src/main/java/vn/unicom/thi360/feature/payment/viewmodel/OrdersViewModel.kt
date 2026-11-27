package vn.unicom.thi360.feature.payment.viewmodel

import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.IdempotentCall
import vn.unicom.thi360.core.network.generated.model.OrderDetail
import vn.unicom.thi360.core.network.generated.model.OrderSummary
import vn.unicom.thi360.core.ui.LoadFailureKind
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.core.ui.toLoadFailed
import vn.unicom.thi360.feature.payment.data.PaymentGateway
import vn.unicom.thi360.feature.payment.domain.OrderActionErrorKey
import vn.unicom.thi360.feature.payment.domain.OrderActionErrorMapper
import vn.unicom.thi360.feature.payment.domain.PaymentUrlResolver
import vn.unicom.thi360.feature.payment.domain.PendingPaymentOrderStore

data class OrderActionErrorBanner(
    val errorKey: OrderActionErrorKey,
    val productCode: String,
)

data class CancelDialogState(
    val orderCode: String,
    val submitting: Boolean = false,
    val errorKey: OrderActionErrorKey? = null,
)

data class OrdersListUiState(
    val username: String = "",
    val orders: LoadState<List<OrderSummary>> = LoadState.Loading,
    val lastLoadedAtMillis: Long? = null,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val selectedOrderCode: String? = null,
    val selectedDetail: LoadState<OrderDetail>? = null,
    val selectedDetailLastLoadedAtMillis: Long? = null,
    val payingOrderCode: String? = null,
    val actionError: OrderActionErrorBanner? = null,
    val cancelDialog: CancelDialogState? = null,
    val snackbarCancelledOrderCode: String? = null,
    val browserEvent: PaymentBrowserEvent? = null,
) {
    val hasMorePages: Boolean
        get() = totalPages > 0 && currentPage + 1 < totalPages

    val anyActionInFlight: Boolean
        get() = payingOrderCode != null || cancelDialog?.submitting == true
}

class OrdersViewModel(
    private val gateway: PaymentGateway,
    private val pendingOrderStore: PendingPaymentOrderStore,
    private val apiBaseUrl: String,
    private val scope: CoroutineScope,
    private val usernameProvider: () -> String? = { null },
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val isOnline: () -> Boolean = { true },
    private val keyGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    private val inFlightAction = AtomicBoolean(false)
    private val loadingNextPage = AtomicBoolean(false)
    private val payCalls = mutableMapOf<String, IdempotentCall>()
    private val cancelCalls = mutableMapOf<String, IdempotentCall>()

    private val _state = MutableStateFlow(
        OrdersListUiState(username = usernameProvider().orEmpty()),
    )
    val state: StateFlow<OrdersListUiState> = _state.asStateFlow()

    fun currentPayKey(orderCode: String): String? = payCalls[orderCode]?.currentKey

    fun currentCancelKey(orderCode: String): String? = cancelCalls[orderCode]?.currentKey

    fun loadInitial() {
        scope.launch {
            if (_state.value.username.isBlank()) {
                val fromProvider = usernameProvider()?.takeIf { it.isNotBlank() }
                if (fromProvider != null) {
                    _state.update { it.copy(username = fromProvider) }
                } else {
                    when (val me = gateway.fetchCurrentUser()) {
                        is ApiResult.Success -> _state.update { it.copy(username = me.data.username) }
                        else -> Unit
                    }
                }
            }
            loadFirstPage(isRefresh = false)
        }
    }

    fun refresh() {
        scope.launch {
            loadFirstPage(isRefresh = true)
            val selected = _state.value.selectedOrderCode
            if (selected != null) {
                loadSelectedDetailInternal(selected, keepExistingOnError = true)
            }
        }
    }

    fun onScreenResumed() {
        if (_state.value.orders !is LoadState.Loading) {
            refresh()
        }
    }

    fun loadNextPage() {
        val current = _state.value
        if (!current.hasMorePages || current.isLoadingMore) return
        if (!loadingNextPage.compareAndSet(false, true)) return

        val nextPage = current.currentPage + 1
        _state.update { it.copy(isLoadingMore = true) }
        scope.launch {
            try {
                when (val result = gateway.listOrders(page = nextPage, size = ORDERS_PAGE_SIZE)) {
                    is ApiResult.Success -> {
                        val pageData = result.data
                        _state.update { state ->
                            val existing = (state.orders as? LoadState.Loaded)?.value.orEmpty()
                            val existingIds = existing.mapTo(HashSet()) { it.id }
                            val merged = existing + pageData.items.filter { it.id !in existingIds }
                            state.copy(
                                orders = LoadState.Loaded(merged, stale = false),
                                currentPage = pageData.page,
                                totalPages = pageData.totalPages,
                                lastLoadedAtMillis = clockMillis(),
                                isLoadingMore = false,
                            )
                        }
                    }

                    is ApiResult.HttpFailure,
                    is ApiResult.NetworkFailure,
                    -> {
                        _state.update { it.copy(isLoadingMore = false) }
                    }
                }
            } finally {
                loadingNextPage.set(false)
            }
        }
    }

    fun selectOrder(orderCode: String?) {
        if (orderCode == null) {
            _state.update {
                it.copy(
                    selectedOrderCode = null,
                    selectedDetail = null,
                    selectedDetailLastLoadedAtMillis = null,
                )
            }
            return
        }
        val sameOrder = _state.value.selectedOrderCode == orderCode
        _state.update {
            it.copy(
                selectedOrderCode = orderCode,
                selectedDetail = if (sameOrder) it.selectedDetail else LoadState.Loading,
                actionError = if (sameOrder) it.actionError else null,
            )
        }
        scope.launch {
            loadSelectedDetailInternal(orderCode, keepExistingOnError = sameOrder)
        }
    }

    fun reloadSelectedDetail() {
        val selected = _state.value.selectedOrderCode ?: return
        scope.launch {
            val hasData = _state.value.selectedDetail is LoadState.Loaded
            if (!hasData) {
                _state.update { it.copy(selectedDetail = LoadState.Loading) }
            }
            loadSelectedDetailInternal(selected, keepExistingOnError = hasData)
        }
    }

    fun payOrder(orderCode: String, productCode: String) {
        if (!inFlightAction.compareAndSet(false, true)) return

        val call = payCalls.getOrPut(orderCode) { IdempotentCall(keyGenerator = keyGenerator) }
        _state.update {
            it.copy(
                payingOrderCode = orderCode,
                actionError = null,
            )
        }
        scope.launch {
            var shouldReload = false
            try {
                val result = call.execute { idempotencyKey ->
                    gateway.payOrder(orderCode, idempotencyKey)
                } ?: return@launch

                when (result) {
                    is ApiResult.Success -> {
                        val resolvedUrl = PaymentUrlResolver.resolve(result.data.paymentUrl, apiBaseUrl)
                        pendingOrderStore.save(orderCode, clockMillis())
                        syncOrderDetailIntoList(result.data.order)
                        _state.update {
                            it.copy(
                                payingOrderCode = null,
                                browserEvent = PaymentBrowserEvent(
                                    orderCode = orderCode,
                                    resolvedUrl = resolvedUrl,
                                ),
                            )
                        }
                    }

                    is ApiResult.HttpFailure,
                    is ApiResult.NetworkFailure,
                    -> {
                        val mapped = OrderActionErrorMapper.mapFailure(result)
                        _state.update {
                            it.copy(
                                payingOrderCode = null,
                                actionError = OrderActionErrorBanner(
                                    errorKey = mapped,
                                    productCode = productCode,
                                ),
                            )
                        }
                        shouldReload = true
                    }
                }
            } finally {
                inFlightAction.set(false)
                _state.update { it.copy(payingOrderCode = null) }
            }
            if (shouldReload) {
                loadFirstPage(isRefresh = true)
                if (_state.value.selectedOrderCode == orderCode) {
                    loadSelectedDetailInternal(orderCode, keepExistingOnError = true)
                }
            }
        }
    }

    fun openCancelDialog(orderCode: String) {
        if (inFlightAction.get() || _state.value.anyActionInFlight) return
        _state.update {
            it.copy(cancelDialog = CancelDialogState(orderCode = orderCode))
        }
    }

    fun dismissCancelDialog() {
        val dialog = _state.value.cancelDialog ?: return
        if (dialog.submitting) return
        if (dialog.errorKey == null) {
            cancelCalls[dialog.orderCode]?.reset()
        }
        _state.update { it.copy(cancelDialog = null) }
    }

    fun confirmCancelOrder() {
        val dialog = _state.value.cancelDialog ?: return
        if (dialog.submitting) return
        if (!inFlightAction.compareAndSet(false, true)) return

        val orderCode = dialog.orderCode
        val call = cancelCalls.getOrPut(orderCode) { IdempotentCall(keyGenerator = keyGenerator) }
        _state.update {
            it.copy(cancelDialog = dialog.copy(submitting = true, errorKey = null))
        }
        scope.launch {
            var reloadList = false
            var reloadSelected = false
            try {
                val result = call.execute { idempotencyKey ->
                    gateway.cancelOrder(orderCode, idempotencyKey)
                } ?: return@launch

                when (result) {
                    is ApiResult.Success -> {
                        val updatedOrder = result.data
                        pendingOrderStore.clearIfMatches(orderCode)
                        syncOrderDetailIntoList(updatedOrder)
                        _state.update {
                            it.copy(
                                cancelDialog = null,
                                snackbarCancelledOrderCode = orderCode,
                            )
                        }
                        reloadList = true
                    }

                    is ApiResult.HttpFailure,
                    is ApiResult.NetworkFailure,
                    -> {
                        val mapped = OrderActionErrorMapper.mapFailure(result)
                        _state.update {
                            it.copy(
                                cancelDialog = CancelDialogState(
                                    orderCode = orderCode,
                                    submitting = false,
                                    errorKey = mapped,
                                ),
                            )
                        }
                        reloadList = true
                        reloadSelected = _state.value.selectedOrderCode == orderCode
                    }
                }
            } finally {
                inFlightAction.set(false)
            }
            if (reloadList) {
                loadFirstPage(isRefresh = true)
            }
            if (reloadSelected) {
                loadSelectedDetailInternal(orderCode, keepExistingOnError = true)
            }
        }
    }

    fun onBrowserOpenFailed(orderCode: String, productCode: String) {
        pendingOrderStore.clearIfMatches(orderCode)
        _state.update {
            it.copy(
                browserEvent = null,
                actionError = OrderActionErrorBanner(
                    errorKey = OrderActionErrorKey.GENERIC,
                    productCode = productCode,
                ),
            )
        }
    }

    fun consumeBrowserEvent() {
        _state.update { it.copy(browserEvent = null) }
    }

    fun consumeSnackbar() {
        _state.update { it.copy(snackbarCancelledOrderCode = null) }
    }

    private suspend fun loadFirstPage(isRefresh: Boolean) {
        val existingItems = (_state.value.orders as? LoadState.Loaded)?.value
        if (isRefresh && existingItems != null) {
            _state.update { it.copy(isRefreshing = true) }
        } else if (existingItems == null) {
            _state.update { it.copy(orders = LoadState.Loading) }
        }

        when (val result = gateway.listOrders(page = 0, size = ORDERS_PAGE_SIZE)) {
            is ApiResult.Success -> {
                val pageData = result.data
                val now = clockMillis()
                val nextOrdersState = if (pageData.items.isEmpty()) {
                    LoadState.Empty
                } else {
                    LoadState.Loaded(pageData.items, stale = false)
                }
                _state.update { current ->
                    val preservedSelection = current.selectedOrderCode
                    current.copy(
                        orders = nextOrdersState,
                        lastLoadedAtMillis = now,
                        currentPage = pageData.page,
                        totalPages = pageData.totalPages,
                        isRefreshing = false,
                        selectedOrderCode = preservedSelection,
                    )
                }
            }

            is ApiResult.HttpFailure -> {
                _state.update { current ->
                    val existing = current.orders as? LoadState.Loaded
                    if (existing != null) {
                        current.copy(
                            orders = existing.copy(stale = true),
                            isRefreshing = false,
                        )
                    } else {
                        current.copy(
                            orders = result.toLoadFailed().copy(canRetry = true),
                            isRefreshing = false,
                        )
                    }
                }
            }

            is ApiResult.NetworkFailure -> {
                _state.update { current ->
                    val existing = current.orders as? LoadState.Loaded
                    if (existing != null) {
                        current.copy(
                            orders = existing.copy(stale = true),
                            isRefreshing = false,
                        )
                    } else {
                        current.copy(
                            orders = result.toLoadFailed(isOnline()),
                            isRefreshing = false,
                        )
                    }
                }
            }
        }
    }

    private suspend fun loadSelectedDetailInternal(
        orderCode: String,
        keepExistingOnError: Boolean,
    ) {
        when (val result = gateway.getOrder(orderCode)) {
            is ApiResult.Success -> {
                val detail = result.data
                val now = clockMillis()
                if (_state.value.selectedOrderCode == orderCode) {
                    _state.update {
                        it.copy(
                            selectedDetail = LoadState.Loaded(detail, stale = false),
                            selectedDetailLastLoadedAtMillis = now,
                        )
                    }
                }
                syncOrderDetailIntoList(detail)
            }

            is ApiResult.HttpFailure -> {
                if (_state.value.selectedOrderCode != orderCode) return
                val isNotFound = result.statusCode == 404 || result.error?.code == "ORDER_NOT_FOUND"
                val existing = _state.value.selectedDetail as? LoadState.Loaded
                val nextDetail = when {
                    isNotFound -> LoadState.Failed(LoadFailureKind.NotFound, canRetry = false)
                    keepExistingOnError && existing != null -> existing.copy(stale = true)
                    else -> result.toLoadFailed().copy(canRetry = true)
                }
                _state.update { it.copy(selectedDetail = nextDetail) }
            }

            is ApiResult.NetworkFailure -> {
                if (_state.value.selectedOrderCode != orderCode) return
                val existing = _state.value.selectedDetail as? LoadState.Loaded
                val nextDetail = if (keepExistingOnError && existing != null) {
                    existing.copy(stale = true)
                } else {
                    result.toLoadFailed(isOnline())
                }
                _state.update { it.copy(selectedDetail = nextDetail) }
            }
        }
    }

    private fun syncOrderDetailIntoList(detail: OrderDetail) {
        val summaryState = OrderSummary.State.entries.firstOrNull { it.value == detail.state.value }
        _state.update { current ->
            val loadedOrders = current.orders as? LoadState.Loaded
            val updatedOrders = if (loadedOrders != null && summaryState != null) {
                loadedOrders.copy(
                    value = loadedOrders.value.map { item ->
                        if (item.code == detail.code) {
                            item.copy(
                                state = summaryState,
                                paidAt = detail.paidAt,
                                fulfilledAt = detail.fulfilledAt,
                                expiresAt = detail.expiresAt,
                            )
                        } else {
                            item
                        }
                    },
                )
            } else {
                current.orders
            }
            val updatedSelectedDetail = if (current.selectedOrderCode == detail.code) {
                LoadState.Loaded(detail, stale = false)
            } else {
                current.selectedDetail
            }
            current.copy(
                orders = updatedOrders,
                selectedDetail = updatedSelectedDetail,
                selectedDetailLastLoadedAtMillis = if (current.selectedOrderCode == detail.code) {
                    clockMillis()
                } else {
                    current.selectedDetailLastLoadedAtMillis
                },
            )
        }
    }

    companion object {
        const val ORDERS_PAGE_SIZE = 20
    }
}

data class OrderDetailUiState(
    val orderCode: String,
    val order: LoadState<OrderDetail> = LoadState.Loading,
    val lastLoadedAtMillis: Long? = null,
    val isRefreshing: Boolean = false,
    val paying: Boolean = false,
    val actionError: OrderActionErrorBanner? = null,
    val cancelDialog: CancelDialogState? = null,
    val snackbarCancelledOrderCode: String? = null,
    val browserEvent: PaymentBrowserEvent? = null,
) {
    val loadedOrder: OrderDetail?
        get() = (order as? LoadState.Loaded)?.value

    val anyActionInFlight: Boolean
        get() = paying || cancelDialog?.submitting == true
}

class OrderDetailViewModel(
    val orderCode: String,
    private val gateway: PaymentGateway,
    private val pendingOrderStore: PendingPaymentOrderStore,
    private val apiBaseUrl: String,
    private val scope: CoroutineScope,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val isOnline: () -> Boolean = { true },
    keyGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    private val inFlightAction = AtomicBoolean(false)
    private val payCall = IdempotentCall(keyGenerator = keyGenerator)
    private val cancelCall = IdempotentCall(keyGenerator = keyGenerator)

    private val _state = MutableStateFlow(OrderDetailUiState(orderCode = orderCode))
    val state: StateFlow<OrderDetailUiState> = _state.asStateFlow()

    val currentPayKey: String?
        get() = payCall.currentKey

    val currentCancelKey: String?
        get() = cancelCall.currentKey

    fun load() {
        scope.launch {
            loadInternal(isRefresh = false)
        }
    }

    fun refresh() {
        scope.launch {
            loadInternal(isRefresh = true)
        }
    }

    fun payOrder() {
        val loaded = _state.value.loadedOrder ?: return
        if (!inFlightAction.compareAndSet(false, true)) return

        _state.update { it.copy(paying = true, actionError = null) }
        scope.launch {
            var shouldReload = false
            try {
                val result = payCall.execute { idempotencyKey ->
                    gateway.payOrder(loaded.code, idempotencyKey)
                } ?: return@launch

                when (result) {
                    is ApiResult.Success -> {
                        val resolvedUrl = PaymentUrlResolver.resolve(result.data.paymentUrl, apiBaseUrl)
                        pendingOrderStore.save(loaded.code, clockMillis())
                        _state.update {
                            it.copy(
                                order = LoadState.Loaded(result.data.order, stale = false),
                                lastLoadedAtMillis = clockMillis(),
                                paying = false,
                                browserEvent = PaymentBrowserEvent(
                                    orderCode = loaded.code,
                                    resolvedUrl = resolvedUrl,
                                ),
                            )
                        }
                    }

                    is ApiResult.HttpFailure,
                    is ApiResult.NetworkFailure,
                    -> {
                        val mapped = OrderActionErrorMapper.mapFailure(result)
                        _state.update {
                            it.copy(
                                paying = false,
                                actionError = OrderActionErrorBanner(
                                    errorKey = mapped,
                                    productCode = loaded.productCode,
                                ),
                            )
                        }
                        shouldReload = true
                    }
                }
            } finally {
                inFlightAction.set(false)
                _state.update { it.copy(paying = false) }
            }
            if (shouldReload) {
                loadInternal(isRefresh = true)
            }
        }
    }

    fun openCancelDialog() {
        if (inFlightAction.get() || _state.value.anyActionInFlight) return
        _state.update {
            it.copy(cancelDialog = CancelDialogState(orderCode = orderCode))
        }
    }

    fun dismissCancelDialog() {
        val dialog = _state.value.cancelDialog ?: return
        if (dialog.submitting) return
        if (dialog.errorKey == null) {
            cancelCall.reset()
        }
        _state.update { it.copy(cancelDialog = null) }
    }

    fun confirmCancelOrder() {
        val dialog = _state.value.cancelDialog ?: return
        if (dialog.submitting) return
        if (!inFlightAction.compareAndSet(false, true)) return

        _state.update {
            it.copy(cancelDialog = dialog.copy(submitting = true, errorKey = null))
        }
        scope.launch {
            var shouldReload = false
            try {
                val result = cancelCall.execute { idempotencyKey ->
                    gateway.cancelOrder(orderCode, idempotencyKey)
                } ?: return@launch

                when (result) {
                    is ApiResult.Success -> {
                        pendingOrderStore.clearIfMatches(orderCode)
                        _state.update {
                            it.copy(
                                order = LoadState.Loaded(result.data, stale = false),
                                lastLoadedAtMillis = clockMillis(),
                                cancelDialog = null,
                                snackbarCancelledOrderCode = orderCode,
                            )
                        }
                    }

                    is ApiResult.HttpFailure,
                    is ApiResult.NetworkFailure,
                    -> {
                        val mapped = OrderActionErrorMapper.mapFailure(result)
                        val productCode = _state.value.loadedOrder?.productCode.orEmpty()
                        _state.update {
                            it.copy(
                                cancelDialog = CancelDialogState(
                                    orderCode = orderCode,
                                    submitting = false,
                                    errorKey = mapped,
                                ),
                                actionError = OrderActionErrorBanner(
                                    errorKey = mapped,
                                    productCode = productCode,
                                ),
                            )
                        }
                        shouldReload = true
                    }
                }
            } finally {
                inFlightAction.set(false)
            }
            if (shouldReload) {
                loadInternal(isRefresh = true)
            }
        }
    }

    fun onBrowserOpenFailed(code: String) {
        pendingOrderStore.clearIfMatches(code)
        val productCode = _state.value.loadedOrder?.productCode.orEmpty()
        _state.update {
            it.copy(
                browserEvent = null,
                actionError = OrderActionErrorBanner(
                    errorKey = OrderActionErrorKey.GENERIC,
                    productCode = productCode,
                ),
            )
        }
    }

    fun consumeBrowserEvent() {
        _state.update { it.copy(browserEvent = null) }
    }

    fun consumeSnackbar() {
        _state.update { it.copy(snackbarCancelledOrderCode = null) }
    }

    private suspend fun loadInternal(isRefresh: Boolean) {
        val existing = _state.value.order as? LoadState.Loaded
        if (isRefresh && existing != null) {
            _state.update { it.copy(isRefreshing = true) }
        } else if (existing == null) {
            _state.update { it.copy(order = LoadState.Loading) }
        }

        when (val result = gateway.getOrder(orderCode)) {
            is ApiResult.Success -> {
                _state.update {
                    it.copy(
                        order = LoadState.Loaded(result.data, stale = false),
                        lastLoadedAtMillis = clockMillis(),
                        isRefreshing = false,
                    )
                }
            }

            is ApiResult.HttpFailure -> {
                val isNotFound = result.statusCode == 404 || result.error?.code == "ORDER_NOT_FOUND"
                _state.update { current ->
                    val currentLoaded = current.order as? LoadState.Loaded
                    val nextOrder = when {
                        isNotFound -> LoadState.Failed(LoadFailureKind.NotFound, canRetry = false)
                        currentLoaded != null -> currentLoaded.copy(stale = true)
                        else -> result.toLoadFailed().copy(canRetry = true)
                    }
                    current.copy(order = nextOrder, isRefreshing = false)
                }
            }

            is ApiResult.NetworkFailure -> {
                _state.update { current ->
                    val currentLoaded = current.order as? LoadState.Loaded
                    val nextOrder = if (currentLoaded != null) {
                        currentLoaded.copy(stale = true)
                    } else {
                        result.toLoadFailed(isOnline())
                    }
                    current.copy(order = nextOrder, isRefreshing = false)
                }
            }
        }
    }
}
