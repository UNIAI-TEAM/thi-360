package vn.unicom.thi360.feature.payment

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.config.SupportContact
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.core.ui.StaleBannerTestTags
import vn.unicom.thi360.feature.payment.domain.PendingPaymentOrderStore
import vn.unicom.thi360.feature.payment.ui.OrderDetailScreen
import vn.unicom.thi360.feature.payment.ui.PaymentTestTags
import vn.unicom.thi360.feature.payment.viewmodel.OrderDetailViewModel
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class OrderDetailScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersAllSnapshotPaymentsAndHistoryFields() {
        val richDetail = PaymentTestFixtures.mutateJson("orders-detail-200-pending-payment.json") { obj ->
            val p1 = buildJsonObject {
                put("id", "00000000-0000-7000-8000-000000000010")
                put("attemptSeq", 1)
                put("txnRef", "TXN_DETAIL_01")
                put("amountVnd", 150_000L)
                put("state", "FAILED")
                put("duplicate", false)
                put("bankCode", "NCB")
                put("cardType", "ATM")
                put("responseCode", "24")
                put("createdAt", "2026-11-20T10:00:00Z")
            }
            val p2 = buildJsonObject {
                put("id", "00000000-0000-7000-8000-000000000011")
                put("attemptSeq", 2)
                put("txnRef", "TXN_DETAIL_02")
                put("amountVnd", 150_000L)
                put("state", "CREATED")
                put("duplicate", true)
                put("createdAt", "2026-11-20T10:05:00Z")
            }
            val h1 = buildJsonObject {
                put("id", "00000000-0000-7000-8000-000000000020")
                put("fromState", JsonNull)
                put("toState", "PENDING_PAYMENT")
                put("actor", "LEARNER")
                put("reason", "ORDER_CREATED")
                put("at", "2026-11-20T10:00:00Z")
            }
            val h2 = buildJsonObject {
                put("id", "00000000-0000-7000-8000-000000000021")
                put("fromState", "PENDING_PAYMENT")
                put("toState", "PENDING_PAYMENT")
                put("actor", "UNKNOWN_BOT")
                put("reason", "PAYMENT_ATTEMPT_CREATED:TXN_DETAIL_02")
                put("at", "2026-11-20T10:05:00Z")
            }
            val h3 = buildJsonObject {
                put("id", "00000000-0000-7000-8000-000000000022")
                put("fromState", "PENDING_PAYMENT")
                put("toState", "PENDING_PAYMENT")
                put("actor", "SYSTEM")
                put("reason", "SOME_FUTURE_REASON:REF")
                put("at", "2026-11-20T10:06:00Z")
            }
            JsonObject(
                obj + mapOf(
                    "examVersionNo" to JsonNull,
                    "bundleVersionNo" to JsonNull,
                    "listPriceVnd" to JsonPrimitive(200_000L),
                    "promotionDiscountVnd" to JsonPrimitive(20_000L),
                    "voucherCode" to JsonPrimitive("SAVE30"),
                    "voucherDiscountVnd" to JsonPrimitive(30_000L),
                    "amountDueVnd" to JsonPrimitive(150_000L),
                    "payments" to JsonArray(listOf(p1, p2)),
                    "history" to JsonArray(listOf(h1, h2, h3)),
                ),
            )
        }
        val harness = PaymentTestFixtures.createHarness {
            respondJson("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}", 200, richDetail)
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val vm = OrderDetailViewModel(
            orderCode = PaymentTestFixtures.ORDER_CODE,
            gateway = PaymentTestFixtures.createGateway(harness),
            pendingOrderStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() },
            apiBaseUrl = harness.server.url("/"),
            scope = scope,
        )
        try {
            vm.load()
            composeRule.setContent {
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                    OrderDetailScreen(
                        viewModel = vm,
                        paymentBrowser = RecordingPaymentBrowser(),
                        supportContact = SupportContact(),
                        onBack = {},
                        onOpenOrders = {},
                        onOpenOrder = {},
                        onOpenMyExams = {},
                        onOpenCheckout = {},
                        onOpenCatalog = {},
                        onSwitchAccount = {},
                    )
                }
            }
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.ORDER_DETAIL_SNAPSHOT_CARD).fetchSemanticsNodes().isNotEmpty()
            }

            composeRule.onNodeWithText("Đơn ${PaymentTestFixtures.ORDER_CODE}").assertIsDisplayed()
            composeRule.onNodeWithText("Thông tin đơn hàng").assertIsDisplayed()
            composeRule.onNodeWithText("—").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("SAVE30 −30.000 đ").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("150.000 đ").performScrollTo().assertIsDisplayed()

            composeRule.onNodeWithText("Giao dịch thanh toán").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("#01 · TXN_DETAIL_01").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Ngân hàng: NCB · ATM").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Bạn đã hủy giao dịch trên VNPAY.").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("#02 · TXN_DETAIL_02").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Thu trùng").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Ngân hàng: —").performScrollTo().assertIsDisplayed()

            composeRule.onNodeWithText("Lịch sử xử lý").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Tạo đơn").performScrollTo().assertIsDisplayed()
            composeRule.onAllNodesWithText("Chờ thanh toán → Chờ thanh toán").onFirst().performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Khởi tạo giao dịch thanh toán TXN_DETAIL_02").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Cập nhật trạng thái đơn hàng").performScrollTo().assertIsDisplayed()
        } finally {
            scope.cancel()
            harness.close()
        }
    }

    @Test
    fun handles404InitialErrorAndStaleReload() {
        val host = ComposeSlotHost(composeRule)
        var switchedAccount = false
        var backToOrders = false
        val notFoundHarness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}", 404, "orders-detail-404-order-not-found.json")
        }
        val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val vm1 = OrderDetailViewModel(
            orderCode = PaymentTestFixtures.ORDER_CODE,
            gateway = PaymentTestFixtures.createGateway(notFoundHarness),
            pendingOrderStore = PendingPaymentOrderStore { notFoundHarness.accountScopedStoreProvider.current() },
            apiBaseUrl = notFoundHarness.server.url("/"),
            scope = scope1,
        )
        try {
            vm1.load()
            host.set {
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                    OrderDetailScreen(
                        viewModel = vm1,
                        paymentBrowser = RecordingPaymentBrowser(),
                        supportContact = SupportContact(),
                        onBack = {},
                        onOpenOrders = { backToOrders = true },
                        onOpenOrder = {},
                        onOpenMyExams = {},
                        onOpenCheckout = {},
                        onOpenCatalog = {},
                        onSwitchAccount = { switchedAccount = true },
                    )
                }
            }
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.ORDER_DETAIL_NOT_FOUND).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Không tìm thấy đơn hàng").assertIsDisplayed()
            composeRule.onNodeWithTag(PaymentTestTags.ORDER_DETAIL_SWITCH_ACCOUNT).performClick()
            composeRule.onNodeWithTag(PaymentTestTags.ORDER_DETAIL_BACK_TO_ORDERS).performClick()
            assertTrue(switchedAccount)
            assertTrue(backToOrders)
        } finally {
            scope1.cancel()
            notFoundHarness.close()
        }

        // Initial 500 -> Retry -> Loaded -> Refresh 503 -> Stale banner keeping data
        val staleHarness = PaymentTestFixtures.createHarness {
            respondSequence(
                "GET",
                "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}",
                { MockResponse().setResponseCode(500).setBody("""{"code":"INTERNAL_ERROR","message":"err"}""") },
                { ContractFakeServer.apiFixtureResponse(200, "orders-detail-200-fulfilled.json") },
                { MockResponse().setResponseCode(503).setBody("""{"code":"SERVICE_UNAVAILABLE","message":"down"}""") },
            )
        }
        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val vm2 = OrderDetailViewModel(
            orderCode = PaymentTestFixtures.ORDER_CODE,
            gateway = PaymentTestFixtures.createGateway(staleHarness),
            pendingOrderStore = PendingPaymentOrderStore { staleHarness.accountScopedStoreProvider.current() },
            apiBaseUrl = staleHarness.server.url("/"),
            scope = scope2,
        )
        try {
            vm2.load()
            host.set {
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                    OrderDetailScreen(
                        viewModel = vm2,
                        paymentBrowser = RecordingPaymentBrowser(),
                        supportContact = SupportContact(),
                        onBack = {},
                        onOpenOrders = {},
                        onOpenOrder = {},
                        onOpenMyExams = {},
                        onOpenCheckout = {},
                        onOpenCatalog = {},
                        onSwitchAccount = {},
                    )
                }
            }
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.ORDER_DETAIL_ERROR).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Không tải được chi tiết đơn hàng").assertIsDisplayed()
            composeRule.onNodeWithText("Thử lại").performClick()

            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.ORDER_DETAIL_HEADING).fetchSemanticsNodes().isNotEmpty()
            }
            vm2.refresh()
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(StaleBannerTestTags.BANNER).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(PaymentTestTags.ORDER_DETAIL_HEADING).assertIsDisplayed()
        } finally {
            scope2.cancel()
            staleHarness.close()
        }
    }
}
