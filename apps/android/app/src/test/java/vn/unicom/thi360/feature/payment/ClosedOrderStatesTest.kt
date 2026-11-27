package vn.unicom.thi360.feature.payment

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.config.SupportContact
import vn.unicom.thi360.core.network.generated.model.OrderDetail
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.payment.domain.PaymentFormatters
import vn.unicom.thi360.feature.payment.domain.PaymentStatusTone
import vn.unicom.thi360.feature.payment.domain.PendingPaymentOrderStore
import vn.unicom.thi360.feature.payment.ui.OrderDetailScreen
import vn.unicom.thi360.feature.payment.ui.PaymentTestTags
import vn.unicom.thi360.feature.payment.viewmodel.OrderDetailViewModel
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class ClosedOrderStatesTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun replacedShowsReplacementButtonAndHistoryLine() {
        var openedReplacement: String? = null
        val replacedWithHistory = PaymentTestFixtures.mutateJson("orders-detail-200-replaced.json") { obj ->
            val h = buildJsonObject {
                put("id", "00000000-0000-7000-8000-000000000050")
                put("fromState", "PENDING_PAYMENT")
                put("toState", "REPLACED")
                put("actor", "LEARNER")
                put("reason", "REPLACED_BY:DH261126000099")
                put("at", "2026-11-20T10:10:00Z")
            }
            JsonObject(
                obj + mapOf(
                    "replacedByOrderCode" to JsonPrimitive("DH261126000099"),
                    "history" to JsonArray(listOf(h)),
                ),
            )
        }
        val harness = PaymentTestFixtures.createHarness {
            respondJson("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}", 200, replacedWithHistory)
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
                        onOpenOrder = { openedReplacement = it },
                        onOpenMyExams = {},
                        onOpenCheckout = {},
                        onOpenCatalog = {},
                        onSwitchAccount = {},
                    )
                }
            }
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.ORDER_DETAIL_REPLACEMENT_BUTTON).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Đã thay thế").assertIsDisplayed()
            composeRule.onNodeWithTag(PaymentTestTags.ORDER_DETAIL_CONTINUE_BUTTON).assertDoesNotExist()
            composeRule.onNodeWithTag(PaymentTestTags.ORDER_DETAIL_CANCEL_BUTTON).assertDoesNotExist()
            composeRule.onNodeWithText("Đã thay thế bằng đơn DH261126000099").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithTag(PaymentTestTags.ORDER_DETAIL_REPLACEMENT_BUTTON).performScrollTo().performClick()
            assertEquals("DH261126000099", openedReplacement)
        } finally {
            scope.cancel()
            harness.close()
        }
    }

    @Test
    fun allClosedStatesShowProperBadgesTonesAndNoPayOrCancelButtons() {
        assertEquals(PaymentStatusTone.Info, PaymentFormatters.orderStateTone(OrderDetail.State.REFUNDED))
        assertEquals(PaymentStatusTone.Neutral, PaymentFormatters.orderStateTone(OrderDetail.State.REPLACED))
        assertEquals(PaymentStatusTone.Neutral, PaymentFormatters.orderStateTone(OrderDetail.State.EXPIRED))
        assertEquals(PaymentStatusTone.Neutral, PaymentFormatters.orderStateTone(OrderDetail.State.CANCELLED))

        val host = ComposeSlotHost(composeRule)
        val cases = listOf(
            "orders-detail-200-expired.json" to "Hết hạn",
            "orders-detail-200-cancelled.json" to "Đã hủy",
            "orders-detail-200-refunded.json" to "Đã hoàn tiền",
            "orders-detail-200-replaced.json" to "Đã thay thế",
        )
        for ((fixture, expectedLabel) in cases) {
            val harness = PaymentTestFixtures.createHarness {
                respondApiFixture("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}", 200, fixture)
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
                    composeRule.onAllNodesWithTag(PaymentTestTags.ORDER_DETAIL_STATE_CHIP).fetchSemanticsNodes().isNotEmpty()
                }
                composeRule.onNodeWithText(expectedLabel).assertIsDisplayed()
                composeRule.onNodeWithTag(PaymentTestTags.ORDER_DETAIL_CONTINUE_BUTTON).assertDoesNotExist()
                composeRule.onNodeWithTag(PaymentTestTags.ORDER_DETAIL_CANCEL_BUTTON).assertDoesNotExist()
            } finally {
                scope.cancel()
                harness.close()
            }
        }
    }

    @Test
    fun pendingOrderExpiringUpdatesOnReloadAndShowsOrderExpiredWithCreateNewOnStalePay() {
        var openedCheckout: String? = null
        val harness = PaymentTestFixtures.createHarness {
            respondSequence(
                "GET",
                "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}",
                { ContractFakeServer.apiFixtureResponse(200, "orders-detail-200-pending-payment.json") },
                { ContractFakeServer.apiFixtureResponse(200, "orders-detail-200-expired.json") },
            )
            respondApiFixture("POST", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}/pay", 409, "orders-pay-409-order-expired.json")
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
                        onOpenCheckout = { openedCheckout = it },
                        onOpenCatalog = {},
                        onSwitchAccount = {},
                    )
                }
            }
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.ORDER_DETAIL_CONTINUE_BUTTON).fetchSemanticsNodes().isNotEmpty()
            }
            // Stale click on Continue Payment after 15-min window expired -> 409 ORDER_EXPIRED + reload updates badge to "Hết hạn" + "Tạo đơn mới"
            composeRule.onNodeWithTag(PaymentTestTags.ORDER_DETAIL_CONTINUE_BUTTON).performClick()
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.ORDER_DETAIL_CREATE_NEW_BUTTON).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Hết hạn").assertIsDisplayed()
            composeRule.onNodeWithText("Đơn đã hết hạn thanh toán. Vui lòng tạo đơn mới theo giá hiện tại.").assertIsDisplayed()
            composeRule.onNodeWithTag(PaymentTestTags.ORDER_DETAIL_CREATE_NEW_BUTTON).performClick()
            assertEquals("EXAM-DEMO-04", openedCheckout)
        } finally {
            scope.cancel()
            harness.close()
        }
    }
}
