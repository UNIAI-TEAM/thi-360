package vn.unicom.thi360.feature.payment

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.config.SupportContact
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.payment.domain.PaymentResultEntry
import vn.unicom.thi360.feature.payment.domain.PendingPaymentOrderStore
import vn.unicom.thi360.feature.payment.ui.OrderDetailScreen
import vn.unicom.thi360.feature.payment.ui.PaymentResultScreen
import vn.unicom.thi360.feature.payment.ui.PaymentTestTags
import vn.unicom.thi360.feature.payment.viewmodel.OrderDetailViewModel
import vn.unicom.thi360.feature.payment.viewmodel.PaymentResultViewModel
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class PaymentOutcomeDisplayTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun multiAttemptOrderAndDuplicateChargeDisplayServerDecisionAccurately() {
        val host = ComposeSlotHost(composeRule)
        val mixedAttempts = PaymentTestFixtures.mutateJson("orders-detail-200-fulfilled.json") { obj ->
            val p1 = buildJsonObject {
                put("id", "00000000-0000-7000-8000-000000000011")
                put("attemptSeq", 1)
                put("txnRef", "TXN_SUCCESS_01")
                put("amountVnd", 200_000L)
                put("state", "SUCCEEDED")
                put("duplicate", false)
                put("bankCode", "NCB")
                put("cardType", "ATM")
                put("createdAt", "2026-11-20T10:00:00Z")
            }
            val p2 = buildJsonObject {
                put("id", "00000000-0000-7000-8000-000000000012")
                put("attemptSeq", 2)
                put("txnRef", "TXN_CREATED_02")
                put("amountVnd", 200_000L)
                put("state", "CREATED")
                put("duplicate", false)
                put("createdAt", "2026-11-20T10:01:00Z")
            }
            JsonObject(obj + ("payments" to JsonArray(listOf(p1, p2))))
        }

        val harness = PaymentTestFixtures.createHarness {
            respondJson("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}", 200, mixedAttempts)
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
            host.set {
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
                composeRule.onAllNodesWithTag(PaymentTestTags.ORDER_DETAIL_PAYMENTS_CARD).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Đã cấp bài thi").assertIsDisplayed()
            composeRule.onNodeWithText("#01 · TXN_SUCCESS_01").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Thành công").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("#02 · TXN_CREATED_02").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Khởi tạo").performScrollTo().assertIsDisplayed()
        } finally {
            scope.cancel()
            harness.close()
        }

        // Duplicate payment fixture shows "Thành công" + "Thu trùng" badge and no extra actions
        val dupHarness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}", 200, "orders-detail-200-duplicate-payment.json")
        }
        val dupScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val dupVm = OrderDetailViewModel(
            orderCode = PaymentTestFixtures.ORDER_CODE,
            gateway = PaymentTestFixtures.createGateway(dupHarness),
            pendingOrderStore = PendingPaymentOrderStore { dupHarness.accountScopedStoreProvider.current() },
            apiBaseUrl = dupHarness.server.url("/"),
            scope = dupScope,
        )
        try {
            dupVm.load()
            host.set {
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                    OrderDetailScreen(
                        viewModel = dupVm,
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
                composeRule.onAllNodesWithTag(PaymentTestTags.ORDER_DETAIL_PAYMENTS_CARD).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Thu trùng").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithTag(PaymentTestTags.ORDER_DETAIL_CONTINUE_BUTTON).assertDoesNotExist()
            composeRule.onNodeWithTag(PaymentTestTags.ORDER_DETAIL_CANCEL_BUTTON).assertDoesNotExist()
        } finally {
            dupScope.cancel()
            dupHarness.close()
        }
    }

    @Test
    fun closedOrderLaterReturningPaidAndFulfilledUpdatesResultAndDetail() {
        val harness = PaymentTestFixtures.createHarness {
            respondSequence(
                "GET",
                "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}",
                { ContractFakeServer.apiFixtureResponse(200, "orders-detail-200-expired.json") },
                { ContractFakeServer.apiFixtureResponse(200, "orders-detail-200-paid.json") },
                { ContractFakeServer.apiFixtureResponse(200, "orders-detail-200-fulfilled.json") },
            )
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val vm = PaymentResultViewModel(
            initialEntry = PaymentResultEntry.ByOrder(PaymentTestFixtures.ORDER_CODE),
            gateway = PaymentTestFixtures.createGateway(harness),
            pendingOrderStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() },
            apiBaseUrl = harness.server.url("/"),
            scope = scope,
        )
        try {
            vm.start()
            composeRule.setContent {
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                    PaymentResultScreen(
                        viewModel = vm,
                        paymentBrowser = RecordingPaymentBrowser(),
                        supportContact = SupportContact(),
                        onBack = {},
                        onOpenMyExams = {},
                        onOpenOrder = {},
                        onOpenOrders = {},
                        onOpenCheckout = {},
                        onOpenCatalog = {},
                        onSwitchAccount = {},
                    )
                }
            }
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.RESULT_BUY_AGAIN_BUTTON).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Đơn hàng đã đóng").assertIsDisplayed()

            // Server reclaims late payment -> PAID
            vm.retryLoad()
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.RESULT_CHECK_AGAIN_BUTTON).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Đang chờ xác nhận thanh toán").assertIsDisplayed()

            // Server fulfills -> FULFILLED
            vm.retryLoad()
            composeRule.waitUntilMainIdle {
                harness.server.requestCount("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}") >= 3 &&
                    vm.uiState.value.activeOrder?.state?.value == "FULFILLED"
            }
            assertEquals("FULFILLED", vm.uiState.value.activeOrder?.state?.value)
            composeRule.onNodeWithTag(PaymentTestTags.RESULT_HEADING).assertTextEquals("Đã cấp bài thi").assertIsDisplayed()
            composeRule.onNodeWithTag(PaymentTestTags.RESULT_STATUS_CHIP).assertTextEquals("Đã cấp bài thi").assertIsDisplayed()
        } finally {
            vm.onLeaveScreen()
            scope.cancel()
            harness.close()
        }
    }
}
