package vn.unicom.thi360.shell.routes

import android.content.Context
import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import vn.unicom.thi360.Thi360Application
import vn.unicom.thi360.core.config.AppEnvironment
import vn.unicom.thi360.core.config.SupportContact
import vn.unicom.thi360.core.session.AppSessionContainer
import vn.unicom.thi360.core.session.SessionCleanup
import vn.unicom.thi360.core.session.SessionState
import vn.unicom.thi360.feature.payment.data.OpenApiPaymentGateway
import vn.unicom.thi360.feature.payment.data.PaymentGateway
import vn.unicom.thi360.feature.payment.domain.CustomTabsPaymentBrowser
import vn.unicom.thi360.feature.payment.domain.OrderPoller
import vn.unicom.thi360.feature.payment.domain.PaymentBrowser
import vn.unicom.thi360.feature.payment.domain.PaymentResultEntry
import vn.unicom.thi360.feature.payment.domain.PaymentReturnLink
import vn.unicom.thi360.feature.payment.domain.PendingPaymentOrderStore
import vn.unicom.thi360.feature.payment.domain.SeenPrice
import vn.unicom.thi360.feature.payment.ui.CheckoutScreen
import vn.unicom.thi360.feature.payment.ui.OrderDetailScreen
import vn.unicom.thi360.feature.payment.ui.OrdersScreen
import vn.unicom.thi360.feature.payment.ui.PaymentResultScreen
import vn.unicom.thi360.feature.payment.viewmodel.CheckoutViewModel
import vn.unicom.thi360.feature.payment.viewmodel.OrderDetailViewModel
import vn.unicom.thi360.feature.payment.viewmodel.OrdersViewModel
import vn.unicom.thi360.feature.payment.viewmodel.PaymentResultViewModel
import vn.unicom.thi360.feature.recovery.ResetLink
import vn.unicom.thi360.shell.Destination

internal sealed interface PendingPaymentNavigation {
    data class ByOrder(val orderCode: String) : PendingPaymentNavigation

    data class ByReturnLink(
        val rawLink: String,
        val parsedLink: PaymentReturnLink,
    ) : PendingPaymentNavigation {
        override fun toString(): String = "ByReturnLink(link=[REDACTED])"
    }
}

internal object PaymentRoutesCoordinator {
    private val _pendingNavigation = MutableStateFlow<PendingPaymentNavigation?>(null)
    val pendingNavigation: StateFlow<PendingPaymentNavigation?> = _pendingNavigation.asStateFlow()

    private val activeResultViewModelRef = AtomicReference<PaymentResultViewModel?>(null)

    fun enqueueReturnLink(rawLink: String, parsedLink: PaymentReturnLink) {
        _pendingNavigation.value = PendingPaymentNavigation.ByReturnLink(rawLink, parsedLink)
    }

    fun enqueuePendingOrder(orderCode: String) {
        if (orderCode.isBlank()) return
        if (_pendingNavigation.value is PendingPaymentNavigation.ByReturnLink) return
        _pendingNavigation.value = PendingPaymentNavigation.ByOrder(orderCode)
    }

    fun consumePendingNavigation(expected: PendingPaymentNavigation) {
        if (_pendingNavigation.value == expected) {
            _pendingNavigation.value = null
        }
    }

    fun attachActiveResultViewModel(viewModel: PaymentResultViewModel) {
        activeResultViewModelRef.set(viewModel)
    }

    fun detachActiveResultViewModel(viewModel: PaymentResultViewModel) {
        activeResultViewModelRef.compareAndSet(viewModel, null)
    }

    fun activeResultViewModel(): PaymentResultViewModel? = activeResultViewModelRef.get()

    fun clear() {
        _pendingNavigation.value = null
        activeResultViewModelRef.set(null)
    }
}

internal data class PaymentRuntimeOverrides(
    val gateway: PaymentGateway? = null,
    val paymentBrowser: PaymentBrowser? = null,
    val pendingOrderStore: PendingPaymentOrderStore? = null,
    val supportContact: SupportContact? = null,
    val apiBaseUrl: String? = null,
    val clockMillis: (() -> Long)? = null,
    val isOnline: (() -> Boolean)? = null,
    val keyGenerator: (() -> String)? = null,
    val pollerFactory: (() -> OrderPoller)? = null,
    val onSwitchAccountOverride: (() -> Unit)? = null,
)

object PaymentRoutesConfig {
    private val overridesRef = AtomicReference<PaymentRuntimeOverrides?>(null)

    @VisibleForTesting
    internal fun setTestOverrides(overrides: PaymentRuntimeOverrides?) {
        overridesRef.set(overrides)
        if (overrides == null) {
            PaymentRoutesCoordinator.clear()
        }
    }

    internal fun currentOverrides(): PaymentRuntimeOverrides? = overridesRef.get()
}

fun registerPaymentHooks(application: Thi360Application): List<AutoCloseable> {
    val pendingOrderStore = PendingPaymentOrderStore {
        application.accountScopedStoreProvider.current()
    }
    return registerPaymentHooks(
        inboundLinkRouter = application.inboundLinkRouter,
        sessionCleanup = application.sessionCleanup,
        pendingOrderStoreProvider = {
            PaymentRoutesConfig.currentOverrides()?.pendingOrderStore ?: pendingOrderStore
        },
        allowedHostProvider = {
            ResetLink.allowedHostFromWebBaseUrl(AppEnvironment.webBaseUrl)
        },
        clockMillisProvider = {
            PaymentRoutesConfig.currentOverrides()?.clockMillis ?: System::currentTimeMillis
        },
    )
}

internal fun registerPaymentHooks(
    inboundLinkRouter: InboundLinkRouter,
    sessionCleanup: SessionCleanup,
    pendingOrderStoreProvider: () -> PendingPaymentOrderStore,
    allowedHostProvider: () -> String,
    clockMillisProvider: () -> (() -> Long) = { System::currentTimeMillis },
): List<AutoCloseable> {
    val handles = mutableListOf<AutoCloseable>()

    handles += inboundLinkRouter.register(
        parser = InboundLinkParser { uri: Uri ->
            val parsed = PaymentReturnLink.parse(uri, allowedHostProvider()) ?: return@InboundLinkParser null
            val rawLink = uri.toString()
            ({
                PaymentRoutesCoordinator.enqueueReturnLink(rawLink, parsed)
            })
        },
        requiresUnlockedSession = true,
    )

    val checkPendingOrder: () -> Unit = {
        val now = clockMillisProvider().invoke()
        val pending = pendingOrderStoreProvider().readValid(now)
        if (pending != null) {
            PaymentRoutesCoordinator.enqueuePendingOrder(pending.orderCode)
        }
    }

    handles += inboundLinkRouter.onAppStarted(checkPendingOrder)
    handles += inboundLinkRouter.onForeground(checkPendingOrder)

    sessionCleanup.register {
        pendingOrderStoreProvider().clear()
        PaymentRoutesCoordinator.clear()
    }

    return handles
}

@Composable
fun PaymentRoutesEffect(navigator: AppNavigator) {
    val pending by PaymentRoutesCoordinator.pendingNavigation.collectAsStateWithLifecycle()

    LaunchedEffect(pending, navigator) {
        val event = pending ?: return@LaunchedEffect
        PaymentRoutesCoordinator.consumePendingNavigation(event)
        val currentRoute = navigator.navController.currentDestination?.route
        val isOnPaymentResult = currentRoute?.contains("/payment-result?") == true
        val activeVm = PaymentRoutesCoordinator.activeResultViewModel()

        when (event) {
            is PendingPaymentNavigation.ByReturnLink -> {
                if (isOnPaymentResult && activeVm != null) {
                    activeVm.acceptEntry(PaymentResultEntry.ByReturnLink(event.parsedLink))
                } else {
                    navigator.openPaymentResult(AppRoute.PaymentResult.ByReturnLink(event.rawLink))
                }
            }

            is PendingPaymentNavigation.ByOrder -> {
                if (isOnPaymentResult && activeVm != null && activeVm.currentOrderCode == event.orderCode) {
                    activeVm.acceptEntry(PaymentResultEntry.ByOrder(event.orderCode))
                } else if (isOnPaymentResult && activeVm != null) {
                    activeVm.acceptEntry(PaymentResultEntry.ByOrder(event.orderCode))
                } else {
                    navigator.openPaymentResult(AppRoute.PaymentResult.ByOrder(event.orderCode))
                }
            }
        }
    }
}

private data class ResolvedPaymentDependencies(
    val gateway: PaymentGateway,
    val paymentBrowser: PaymentBrowser,
    val pendingOrderStore: PendingPaymentOrderStore,
    val supportContact: SupportContact,
    val apiBaseUrl: String,
    val clockMillis: () -> Long,
    val isOnline: () -> Boolean,
    val keyGenerator: () -> String,
    val pollerFactory: () -> OrderPoller,
    val usernameProvider: () -> String?,
    val switchAccount: suspend () -> Unit,
)

@Composable
private fun rememberPaymentDependencies(): ResolvedPaymentDependencies {
    val context = LocalContext.current
    val overrides = PaymentRoutesConfig.currentOverrides()
    return remember(context, overrides) {
        resolvePaymentDependencies(context, overrides)
    }
}

private fun resolvePaymentDependencies(
    context: Context,
    overrides: PaymentRuntimeOverrides?,
): ResolvedPaymentDependencies {
    val app = context.applicationContext as? Thi360Application
    val stack = if (overrides?.gateway == null || overrides.onSwitchAccountOverride == null) {
        AppSessionContainer.get(context)
    } else {
        null
    }
    val gateway = overrides?.gateway ?: OpenApiPaymentGateway.fromSessionStack(checkNotNull(stack))
    val browser = overrides?.paymentBrowser ?: CustomTabsPaymentBrowser()
    val pendingOrderStore = overrides?.pendingOrderStore ?: PendingPaymentOrderStore {
        app?.accountScopedStoreProvider?.current()
    }
    val supportContact = overrides?.supportContact ?: SupportContact.fromBuildConfig()
    val apiBaseUrl = overrides?.apiBaseUrl ?: AppEnvironment.apiBaseUrl
    val clockMillis = overrides?.clockMillis ?: System::currentTimeMillis
    val isOnline = overrides?.isOnline ?: {
        app?.connectivityMonitor?.online?.value ?: true
    }
    val keyGenerator = overrides?.keyGenerator ?: { UUID.randomUUID().toString() }
    val pollerFactory = overrides?.pollerFactory ?: { OrderPoller(clockMillis = clockMillis) }
    val usernameProvider: () -> String? = {
        when (val s = stack?.sessionManager?.state?.value) {
            is SessionState.Active -> s.username
            is SessionState.Locked -> s.username
            is SessionState.MustChangePassword -> s.username
            else -> null
        }
    }
    val switchAccount: suspend () -> Unit = {
        val customSwitch = overrides?.onSwitchAccountOverride
        if (customSwitch != null) {
            customSwitch()
        } else {
            stack?.sessionManager?.logout()
        }
    }
    return ResolvedPaymentDependencies(
        gateway = gateway,
        paymentBrowser = browser,
        pendingOrderStore = pendingOrderStore,
        supportContact = supportContact,
        apiBaseUrl = apiBaseUrl,
        clockMillis = clockMillis,
        isOnline = isOnline,
        keyGenerator = keyGenerator,
        pollerFactory = pollerFactory,
        usernameProvider = usernameProvider,
        switchAccount = switchAccount,
    )
}

fun NavGraphBuilder.paymentGraph(
    tab: Destination,
    navigator: AppNavigator,
) {
    composable(
        route = AppRoute.tabRoute(tab, AppRoute.CHECKOUT_PATTERN),
        arguments = listOf(
            navArgument(AppRoute.ARG_CODE) { type = NavType.StringType },
            navArgument(AppRoute.ARG_SEEN_VERSION_NO) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument(AppRoute.ARG_SEEN_PRICE) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) { backStackEntry ->
        val args = backStackEntry.arguments
        val code = AppRoute.decodeSegment(args?.getString(AppRoute.ARG_CODE).orEmpty()).orEmpty()
        val seenVersionNo = args?.getString(AppRoute.ARG_SEEN_VERSION_NO)?.toIntOrNull()
        val seenPrice = args?.getString(AppRoute.ARG_SEEN_PRICE)?.toLongOrNull()
        val deps = rememberPaymentDependencies()
        val scope = rememberCoroutineScope()

        val viewModel = remember(code, seenVersionNo, seenPrice, deps) {
            CheckoutViewModel(
                code = code,
                seenPrice = SeenPrice(versionNo = seenVersionNo, price = seenPrice),
                gateway = deps.gateway,
                pendingOrderStore = deps.pendingOrderStore,
                apiBaseUrl = deps.apiBaseUrl,
                scope = scope,
                clockMillis = deps.clockMillis,
                isOnline = deps.isOnline,
                keyGenerator = deps.keyGenerator,
            )
        }
        LaunchedEffect(viewModel) {
            viewModel.loadInitial()
        }

        CheckoutScreen(
            viewModel = viewModel,
            paymentBrowser = deps.paymentBrowser,
            supportContact = deps.supportContact,
            onBack = { navigator.back() },
            onOpenCatalog = { navigator.switchTab(Destination.Home) },
            onOpenProduct = { productCode -> navigator.openProduct(productCode) },
            onOpenPaymentResult = { orderCode ->
                navigator.openPaymentResult(AppRoute.PaymentResult.ByOrder(orderCode))
            },
            onOpenOrders = { navigator.openOrders() },
            onOpenMyExams = { navigator.switchTab(Destination.MyExams) },
            onOpenPrepare = { entitlementId -> navigator.openPrepare(entitlementId) },
            onOpenAttempt = { attemptId -> navigator.openAttempt(attemptId) },
            onSwitchAccount = {
                scope.launch { deps.switchAccount() }
            },
        )
    }

    composable(
        route = AppRoute.tabRoute(tab, AppRoute.PAYMENT_RESULT_PATTERN),
        arguments = listOf(
            navArgument(AppRoute.ARG_ORDER_CODE) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument(AppRoute.ARG_LINK_TOKEN) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) { backStackEntry ->
        val args = backStackEntry.arguments
        val orderCode = args?.getString(AppRoute.ARG_ORDER_CODE)?.let { AppRoute.decodeSegment(it) }
        val linkToken = args?.getString(AppRoute.ARG_LINK_TOKEN)?.let { AppRoute.decodeSegment(it) }
        val rawReturnLink = remember(linkToken) {
            linkToken?.let { token -> navigator.linkStore.get(token) }
        }
        val parsedReturnLink = remember(rawReturnLink) {
            rawReturnLink?.let { PaymentReturnLink.fromStoredLink(it) }
        }
        val entry = remember(orderCode, parsedReturnLink) {
            when {
                parsedReturnLink != null -> PaymentResultEntry.ByReturnLink(parsedReturnLink)
                !orderCode.isNullOrBlank() -> PaymentResultEntry.ByOrder(orderCode)
                else -> PaymentResultEntry.ByOrder("")
            }
        }
        val deps = rememberPaymentDependencies()
        val scope = rememberCoroutineScope()

        val viewModel = remember(entry, deps) {
            PaymentResultViewModel(
                initialEntry = entry,
                gateway = deps.gateway,
                pendingOrderStore = deps.pendingOrderStore,
                apiBaseUrl = deps.apiBaseUrl,
                scope = scope,
                usernameProvider = deps.usernameProvider,
                clockMillis = deps.clockMillis,
                poller = deps.pollerFactory(),
                keyGenerator = deps.keyGenerator,
            )
        }

        DisposableEffect(viewModel, linkToken) {
            PaymentRoutesCoordinator.attachActiveResultViewModel(viewModel)
            onDispose {
                PaymentRoutesCoordinator.detachActiveResultViewModel(viewModel)
                if (!linkToken.isNullOrBlank()) {
                    navigator.linkStore.remove(linkToken)
                }
            }
        }

        LaunchedEffect(viewModel) {
            viewModel.start()
        }

        PaymentResultScreen(
            viewModel = viewModel,
            paymentBrowser = deps.paymentBrowser,
            supportContact = deps.supportContact,
            onBack = { navigator.back() },
            onOpenMyExams = { navigator.switchTab(Destination.MyExams) },
            onOpenOrder = { code -> navigator.openOrder(code) },
            onOpenOrders = { navigator.openOrders() },
            onOpenCheckout = { productCode -> navigator.openCheckout(productCode) },
            onOpenCatalog = { navigator.switchTab(Destination.Home) },
            onSwitchAccount = {
                scope.launch { deps.switchAccount() }
            },
        )
    }

    composable(AppRoute.tabRoute(tab, AppRoute.ORDERS_PATH)) {
        val deps = rememberPaymentDependencies()
        val scope = rememberCoroutineScope()
        val viewModel = remember(deps) {
            OrdersViewModel(
                gateway = deps.gateway,
                pendingOrderStore = deps.pendingOrderStore,
                apiBaseUrl = deps.apiBaseUrl,
                scope = scope,
                usernameProvider = deps.usernameProvider,
                clockMillis = deps.clockMillis,
                isOnline = deps.isOnline,
                keyGenerator = deps.keyGenerator,
            )
        }
        LaunchedEffect(viewModel) {
            viewModel.loadInitial()
        }

        OrdersScreen(
            viewModel = viewModel,
            paymentBrowser = deps.paymentBrowser,
            supportContact = deps.supportContact,
            onBack = { navigator.back() },
            onOpenOrder = { orderCode -> navigator.openOrder(orderCode) },
            onOpenMyExams = { navigator.switchTab(Destination.MyExams) },
            onOpenCheckout = { productCode -> navigator.openCheckout(productCode) },
            onOpenCatalog = { navigator.switchTab(Destination.Home) },
            onSwitchAccount = {
                scope.launch { deps.switchAccount() }
            },
        )
    }

    composable(
        route = AppRoute.tabRoute(tab, AppRoute.ORDER_PATTERN),
        arguments = listOf(
            navArgument(AppRoute.ARG_ORDER_CODE) { type = NavType.StringType },
        ),
    ) { backStackEntry ->
        val orderCode = AppRoute.decodeSegment(
            backStackEntry.arguments?.getString(AppRoute.ARG_ORDER_CODE).orEmpty(),
        ).orEmpty()
        val deps = rememberPaymentDependencies()
        val scope = rememberCoroutineScope()
        val viewModel = remember(orderCode, deps) {
            OrderDetailViewModel(
                orderCode = orderCode,
                gateway = deps.gateway,
                pendingOrderStore = deps.pendingOrderStore,
                apiBaseUrl = deps.apiBaseUrl,
                scope = scope,
                clockMillis = deps.clockMillis,
                isOnline = deps.isOnline,
                keyGenerator = deps.keyGenerator,
            )
        }
        LaunchedEffect(viewModel) {
            viewModel.load()
        }

        OrderDetailScreen(
            viewModel = viewModel,
            paymentBrowser = deps.paymentBrowser,
            supportContact = deps.supportContact,
            onBack = { navigator.back() },
            onOpenOrders = { navigator.openOrders() },
            onOpenOrder = { nextCode -> navigator.openOrder(nextCode) },
            onOpenMyExams = { navigator.switchTab(Destination.MyExams) },
            onOpenCheckout = { productCode -> navigator.openCheckout(productCode) },
            onOpenCatalog = { navigator.switchTab(Destination.Home) },
            onSwitchAccount = {
                scope.launch { deps.switchAccount() }
            },
        )
    }
}
