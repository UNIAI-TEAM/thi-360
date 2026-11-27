package vn.unicom.thi360.feature.payment

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.unicom.thi360.core.config.SupportContact
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.core.ui.LocalShellWidth
import vn.unicom.thi360.core.ui.ShellWidth
import vn.unicom.thi360.core.ui.shellWidthForDp
import vn.unicom.thi360.feature.payment.domain.SeenPrice
import vn.unicom.thi360.feature.payment.ui.CheckoutScreenContent
import vn.unicom.thi360.feature.payment.ui.OrderDetailPaneContent
import vn.unicom.thi360.feature.payment.ui.OrdersScreenContent
import vn.unicom.thi360.feature.payment.ui.PaymentResultScreenContent
import vn.unicom.thi360.feature.payment.viewmodel.CheckoutUiState
import vn.unicom.thi360.feature.payment.viewmodel.OrdersListUiState
import vn.unicom.thi360.feature.payment.viewmodel.PaymentResultState
import vn.unicom.thi360.feature.payment.viewmodel.PaymentResultUiState

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PaymentScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val supportContact = SupportContact(phone = "1900 1234", email = "hotro@thi360.vn", hours = "8:00–20:00")

    private fun sampleCheckoutState(): CheckoutUiState {
        val me = PaymentTestFixtures.decodeMe()
        val product = PaymentTestFixtures.decodeProduct()
        val quote = PaymentTestFixtures.decodeQuote("price-quote-200-voucher.json")
        return CheckoutUiState(
            code = product.code,
            seenPrice = SeenPrice(versionNo = 1, price = 200_000L),
            user = LoadState.Loaded(me),
            acknowledgedUser = me,
            product = LoadState.Loaded(product),
            access = LoadState.Loaded(null),
            baseQuote = LoadState.Loaded(quote),
        )
    }

    private fun sampleResultState(): PaymentResultUiState {
        val order = PaymentTestFixtures.decodeOrderDetail("orders-detail-200-fulfilled.json")
        return PaymentResultUiState(state = PaymentResultState.Fulfilled(order))
    }

    private fun sampleOrdersState(): OrdersListUiState {
        val page = PaymentTestFixtures.decodeOrdersPage()
        val detail = PaymentTestFixtures.decodeOrderDetail("orders-detail-200-pending-payment.json")
        return OrdersListUiState(
            username = "student.demo",
            orders = LoadState.Loaded(page.items),
            selectedOrderCode = page.items.first().code,
            selectedDetail = LoadState.Loaded(detail),
            currentPage = 0,
            totalPages = 1,
        )
    }

    private fun capture(
        name: String,
        widthDp: Int,
        themeMode: ThemeMode,
        fontScale: Float = 1.0f,
        shellWidthOverride: ShellWidth? = null,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density, fontScale = fontScale),
                LocalShellWidth provides (shellWidthOverride ?: shellWidthForDp(widthDp)),
            ) {
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = themeMode)) {
                    content()
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    private fun captureCheckout(name: String, widthDp: Int, themeMode: ThemeMode, fontScale: Float = 1.0f) {
        val state = sampleCheckoutState()
        capture(name, widthDp, themeMode, fontScale) {
            CheckoutScreenContent(
                state = state,
                supportContact = supportContact,
                onBack = {},
                onRetryInitial = {},
                onRetryAccess = {},
                onRetryQuote = {},
                onVoucherInputChange = {},
                onApplyVoucher = {},
                onRemoveVoucher = {},
                onSubmitOrder = {},
                onAcknowledgeNewAccount = {},
                onOpenCatalog = {},
                onOpenProduct = {},
                onOpenPaymentResult = {},
                onOpenOrders = {},
                onOpenMyExams = {},
                onOpenPrepare = {},
                onOpenAttempt = {},
                onSwitchAccount = {},
            )
        }
    }

    private fun captureResult(name: String, widthDp: Int, themeMode: ThemeMode, fontScale: Float = 1.0f) {
        val state = sampleResultState()
        capture(name, widthDp, themeMode, fontScale) {
            PaymentResultScreenContent(
                uiState = state,
                supportContact = supportContact,
                onBack = {},
                onRetryLoad = {},
                onCheckAgain = {},
                onRetryPay = {},
                onOpenMyExams = {},
                onOpenOrder = {},
                onOpenOrders = {},
                onOpenCheckout = {},
                onOpenCatalog = {},
                onSwitchAccount = {},
            )
        }
    }

    private fun captureOrders(
        name: String,
        widthDp: Int,
        themeMode: ThemeMode,
        fontScale: Float = 1.0f,
        containerWidthDp: Int? = null,
    ) {
        val state = sampleOrdersState()
        val snackbar = SnackbarHostState()
        capture(name, widthDp, themeMode, fontScale) {
            val inner: @androidx.compose.runtime.Composable () -> Unit = {
                OrdersScreenContent(
                    state = state,
                    snackbarHostState = snackbar,
                    supportContact = supportContact,
                    onBack = if (containerWidthDp == null && widthDp < 840) ({}) else null,
                    onRefresh = {},
                    onLoadNextPage = {},
                    onSelectOrderInTwoPane = {},
                    onOpenOrderSinglePane = {},
                    onReloadSelectedDetail = {},
                    onPayOrder = { _, _ -> },
                    onOpenCancelDialog = {},
                    onConfirmCancel = {},
                    onDismissCancelDialog = {},
                    onOpenMyExams = {},
                    onOpenCheckout = {},
                    onOpenCatalog = {},
                    onSwitchAccount = {},
                )
            }
            if (containerWidthDp != null) {
                Box(modifier = Modifier.width(containerWidthDp.dp).fillMaxHeight()) { inner() }
            } else {
                inner()
            }
        }
    }

    private fun captureOrderDetail(name: String, widthDp: Int, themeMode: ThemeMode, fontScale: Float = 1.0f) {
        val detail = PaymentTestFixtures.decodeOrderDetail("orders-detail-200-duplicate-payment.json")
        capture(name, widthDp, themeMode, fontScale) {
            OrderDetailPaneContent(
                orderState = LoadState.Loaded(detail),
                lastLoadedAtMillis = null,
                supportContact = supportContact,
                paying = false,
                actionsDisabled = false,
                actionError = null,
                onRetry = {},
                onPayOrder = {},
                onCancelOrder = {},
                onOpenMyExams = {},
                onOpenOrder = {},
                onOpenOrders = {},
                onOpenCheckout = {},
                onOpenCatalog = {},
                onSwitchAccount = {},
            )
        }
    }

    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun payment_checkout_360x640_light() = captureCheckout("payment_checkout_360x640_light", 360, ThemeMode.LIGHT)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun payment_checkout_360x640_dark() = captureCheckout("payment_checkout_360x640_dark", 360, ThemeMode.DARK)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun payment_checkout_360x640_fs130_light() = captureCheckout("payment_checkout_360x640_fs130_light", 360, ThemeMode.LIGHT, 1.3f)
    @Test @Config(qualifiers = "w412dp-h915dp-xhdpi") fun payment_checkout_412x915_light() = captureCheckout("payment_checkout_412x915_light", 412, ThemeMode.LIGHT)
    @Test @Config(qualifiers = "w412dp-h915dp-xhdpi") fun payment_checkout_412x915_dark() = captureCheckout("payment_checkout_412x915_dark", 412, ThemeMode.DARK)
    @Test @Config(qualifiers = "w840dp-h720dp-xhdpi") fun payment_checkout_840x720_light() = captureCheckout("payment_checkout_840x720_light", 840, ThemeMode.LIGHT)
    @Test @Config(qualifiers = "w1280dp-h800dp-xhdpi") fun payment_checkout_1280x800_light() = captureCheckout("payment_checkout_1280x800_light", 1280, ThemeMode.LIGHT)

    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun payment_result_360x640_light() = captureResult("payment_result_360x640_light", 360, ThemeMode.LIGHT)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun payment_result_360x640_dark() = captureResult("payment_result_360x640_dark", 360, ThemeMode.DARK)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun payment_result_360x640_fs130_light() = captureResult("payment_result_360x640_fs130_light", 360, ThemeMode.LIGHT, 1.3f)
    @Test @Config(qualifiers = "w412dp-h915dp-xhdpi") fun payment_result_412x915_light() = captureResult("payment_result_412x915_light", 412, ThemeMode.LIGHT)
    @Test @Config(qualifiers = "w840dp-h720dp-xhdpi") fun payment_result_840x720_light() = captureResult("payment_result_840x720_light", 840, ThemeMode.LIGHT)
    @Test @Config(qualifiers = "w1280dp-h800dp-xhdpi") fun payment_result_1280x800_dark() = captureResult("payment_result_1280x800_dark", 1280, ThemeMode.DARK)

    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun payment_orders_360x640_light() = captureOrders("payment_orders_360x640_light", 360, ThemeMode.LIGHT)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun payment_orders_360x640_dark() = captureOrders("payment_orders_360x640_dark", 360, ThemeMode.DARK)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun payment_orders_360x640_fs130_light() = captureOrders("payment_orders_360x640_fs130_light", 360, ThemeMode.LIGHT, 1.3f)
    @Test @Config(qualifiers = "w412dp-h915dp-xhdpi") fun payment_orders_412x915_light() = captureOrders("payment_orders_412x915_light", 412, ThemeMode.LIGHT)
    @Test @Config(qualifiers = "w840dp-h720dp-xhdpi") fun payment_orders_840x720_light() = captureOrders("payment_orders_840x720_light", 840, ThemeMode.LIGHT)
    @Test @Config(qualifiers = "w840dp-h720dp-xhdpi") fun payment_orders_840x720_dark() = captureOrders("payment_orders_840x720_dark", 840, ThemeMode.DARK)
    @Test @Config(qualifiers = "w1280dp-h800dp-xhdpi") fun payment_orders_1280x800_light() = captureOrders("payment_orders_1280x800_light", 1280, ThemeMode.LIGHT)
    @Test @Config(qualifiers = "w840dp-h720dp-xhdpi") fun payment_orders_account_pane_840x720_light() = captureOrders("payment_orders_account_pane_840x720_light", 840, ThemeMode.LIGHT, containerWidthDp = 367)
    @Test @Config(qualifiers = "w1280dp-h800dp-xhdpi") fun payment_orders_account_pane_1280x800_light() = captureOrders("payment_orders_account_pane_1280x800_light", 1280, ThemeMode.LIGHT, containerWidthDp = 807)

    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun payment_order_detail_360x640_light() = captureOrderDetail("payment_order_detail_360x640_light", 360, ThemeMode.LIGHT)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun payment_order_detail_360x640_dark() = captureOrderDetail("payment_order_detail_360x640_dark", 360, ThemeMode.DARK)
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun payment_order_detail_360x640_fs130_light() = captureOrderDetail("payment_order_detail_360x640_fs130_light", 360, ThemeMode.LIGHT, 1.3f)
    @Test @Config(qualifiers = "w412dp-h915dp-xhdpi") fun payment_order_detail_412x915_light() = captureOrderDetail("payment_order_detail_412x915_light", 412, ThemeMode.LIGHT)
    @Test @Config(qualifiers = "w840dp-h720dp-xhdpi") fun payment_order_detail_840x720_light() = captureOrderDetail("payment_order_detail_840x720_light", 840, ThemeMode.LIGHT)
    @Test @Config(qualifiers = "w1280dp-h800dp-xhdpi") fun payment_order_detail_1280x800_dark() = captureOrderDetail("payment_order_detail_1280x800_dark", 1280, ThemeMode.DARK)
}
