package vn.unicom.thi360.feature.payment

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.util.TimeZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.assertEquals
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
import vn.unicom.thi360.feature.payment.domain.PaymentResultEntry
import vn.unicom.thi360.feature.payment.domain.PaymentReturnLink
import vn.unicom.thi360.feature.payment.domain.PendingPaymentOrderStore
import vn.unicom.thi360.feature.payment.ui.PaymentResultScreen
import vn.unicom.thi360.feature.payment.ui.PaymentTestTags
import vn.unicom.thi360.feature.payment.viewmodel.PaymentResultViewModel
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class PaymentResultScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val slotHost by lazy { ComposeSlotHost(composeRule) }

    @Test
    fun verifyErrorsInvalidSignature400NotFoundAndNetworkError() {
        // 422 VNPAY_SIGNATURE_INVALID -> does not call getOrder
        assertVerifyFailure(
            verifyStatus = 422,
            verifyBody = PaymentTestFixtures.readFixture("vnpay-return-422-vnpay-signature-invalid.json"),
            expectedHeading = "Không xác minh được phản hồi thanh toán",
        )
        // 400 VALIDATION_FAILED (Plan A6 unproducible-response mutation) -> InvalidSignature, no getOrder
        assertVerifyFailure(
            verifyStatus = 400,
            verifyBody = PaymentTestFixtures.vnpayReturn400ValidationFailedBody(),
            expectedHeading = "Không xác minh được phản hồi thanh toán",
        )
        // 404 ORDER_NOT_FOUND
        assertVerifyFailure(
            verifyStatus = 404,
            verifyBody = PaymentTestFixtures.readFixture("vnpay-return-404-order-not-found.json"),
            expectedHeading = "Không tìm thấy đơn hàng",
        )
        // 500 -> LoadError with retry
        assertVerifyFailure(
            verifyStatus = 500,
            verifyBody = """{"code":"INTERNAL_ERROR","message":"boom"}""",
            expectedHeading = "Không thể kiểm tra kết quả thanh toán",
        )
    }

    private fun assertVerifyFailure(
        verifyStatus: Int,
        verifyBody: String,
        expectedHeading: String,
    ) {
        val harness = PaymentTestFixtures.createHarness {
            respondJson("GET", "/api/v1/public/payments/vnpay/return", verifyStatus, verifyBody)
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val link = PaymentReturnLink(mapOf("vnp_TxnRef" to "TX1", "vnp_ResponseCode" to "00", "vnp_SecureHash" to "bad"))
        val vm = PaymentResultViewModel(
            initialEntry = PaymentResultEntry.ByReturnLink(link),
            gateway = PaymentTestFixtures.createGateway(harness),
            pendingOrderStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() },
            apiBaseUrl = harness.server.url("/"),
            scope = scope,
        )
        try {
            vm.start()
            mountResult(vm)
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.RESULT_HEADING).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText(expectedHeading).assertIsDisplayed()
            assertEquals(0, harness.server.requestCount("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}"))
        } finally {
            vm.onLeaveScreen()
            scope.cancel()
            harness.close()
        }
    }

    @Test
    fun notOwnedOrder404ShowsOrderCodeUsernameAndSwitchAccount() {
        var switchedAccount = false
        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}", 404, "orders-detail-404-order-not-found.json")
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val vm = PaymentResultViewModel(
            initialEntry = PaymentResultEntry.ByOrder(PaymentTestFixtures.ORDER_CODE),
            gateway = PaymentTestFixtures.createGateway(harness),
            pendingOrderStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() },
            apiBaseUrl = harness.server.url("/"),
            scope = scope,
            usernameProvider = { "student.demo" },
        )
        try {
            vm.start()
            mountResult(vm, onSwitchAccount = { switchedAccount = true })
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.RESULT_SWITCH_ACCOUNT_BUTTON).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Không tìm thấy đơn trong tài khoản đang đăng nhập").assertIsDisplayed()
            composeRule.onNodeWithText(PaymentTestFixtures.ORDER_CODE, substring = true).assertIsDisplayed()
            composeRule.onNodeWithText("student.demo", substring = true).assertIsDisplayed()
            composeRule.onNodeWithTag(PaymentTestTags.RESULT_SWITCH_ACCOUNT_BUTTON).performClick()
            assertTrue(switchedAccount)
        } finally {
            vm.onLeaveScreen()
            scope.cancel()
            harness.close()
        }
    }

    @Test
    fun vietnamTimeRegardlessOfDeviceZone() {
        val originalZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}", 200, "orders-detail-200-fulfilled.json")
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
            mountResult(vm)
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.RESULT_HEADING).fetchSemanticsNodes().isNotEmpty()
            }
            // Fixture fulfilledAt = 2026-11-20T10:00:00Z -> Asia/Ho_Chi_Minh (+07:00) = 20/11/2026 17:00
            composeRule.onNodeWithTag(PaymentTestTags.RESULT_HEADING).assertTextEquals("Đã cấp bài thi").assertIsDisplayed()
            composeRule.onNodeWithTag(PaymentTestTags.RESULT_STATUS_CHIP).assertTextEquals("Đã cấp bài thi").assertIsDisplayed()
            composeRule.onNodeWithText("20/11/2026 17:00", substring = true).assertIsDisplayed()
            composeRule.onNodeWithTag(PaymentTestTags.RESULT_VIEW_MY_EXAMS_BUTTON).assertIsDisplayed()
            composeRule.onNodeWithTag(PaymentTestTags.RESULT_VIEW_ORDER_BUTTON).assertIsDisplayed()
        } finally {
            vm.onLeaveScreen()
            scope.cancel()
            harness.close()
            TimeZone.setDefault(originalZone)
        }
    }

    @Test
    fun fulfilledFreeVoucherUsesFreeBodyAndFailedShowsVnpayReason() {
        // Free voucher fulfilled
        val freeHarness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}", 200, "orders-detail-200-free-voucher.json")
        }
        val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val vm1 = PaymentResultViewModel(
            initialEntry = PaymentResultEntry.ByOrder(PaymentTestFixtures.ORDER_CODE),
            gateway = PaymentTestFixtures.createGateway(freeHarness),
            pendingOrderStore = PendingPaymentOrderStore { freeHarness.accountScopedStoreProvider.current() },
            apiBaseUrl = freeHarness.server.url("/"),
            scope = scope1,
        )
        try {
            vm1.start()
            mountResult(vm1)
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.RESULT_HEADING).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("áp dụng voucher miễn phí", substring = true).assertIsDisplayed()
        } finally {
            vm1.onLeaveScreen()
            scope1.cancel()
            freeHarness.close()
        }

        // Failed payment with VNPAY code 24
        val failedCode24 = PaymentTestFixtures.mutateJson("orders-detail-200-payment-failed.json") { obj ->
            val payment = buildJsonObject {
                put("id", "00000000-0000-7000-8000-000000000006")
                put("attemptSeq", 1)
                put("txnRef", "DH26112600000101")
                put("amountVnd", 200_000L)
                put("state", "FAILED")
                put("duplicate", false)
                put("responseCode", "24")
                put("createdAt", "2026-11-20T10:00:00Z")
            }
            JsonObject(obj + ("payments" to JsonArray(listOf(payment))))
        }
        val failedHarness = PaymentTestFixtures.createHarness {
            respondJson("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}", 200, failedCode24)
        }
        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val vm2 = PaymentResultViewModel(
            initialEntry = PaymentResultEntry.ByOrder(PaymentTestFixtures.ORDER_CODE),
            gateway = PaymentTestFixtures.createGateway(failedHarness),
            pendingOrderStore = PendingPaymentOrderStore { failedHarness.accountScopedStoreProvider.current() },
            apiBaseUrl = failedHarness.server.url("/"),
            scope = scope2,
        )
        try {
            vm2.start()
            mountResult(vm2)
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.RESULT_RETRY_PAY_BUTTON).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Thanh toán chưa thành công").assertIsDisplayed()
            composeRule.onNodeWithText("Bạn đã hủy giao dịch trên VNPAY.").assertIsDisplayed()
            composeRule.onNodeWithText("Mã giao dịch: DH26112600000101 · 200.000 đ").assertIsDisplayed()
        } finally {
            vm2.onLeaveScreen()
            scope2.cancel()
            failedHarness.close()
        }
    }

    @Test
    fun oldTxnRefFollowsOrderState() {
        // Return link carries FAILED for old attemptSeq=1 txnRef, while order already has newer attemptSeq=2 CREATED
        val orderWithNewerAttempt = PaymentTestFixtures.mutateJson("orders-detail-200-pending-payment.json") { obj ->
            val p1 = buildJsonObject {
                put("id", "00000000-0000-7000-8000-000000000001")
                put("attemptSeq", 1)
                put("txnRef", "OLD_TXN_01")
                put("amountVnd", 200_000L)
                put("state", "FAILED")
                put("duplicate", false)
                put("responseCode", "24")
                put("createdAt", "2026-11-20T10:00:00Z")
            }
            val p2 = buildJsonObject {
                put("id", "00000000-0000-7000-8000-000000000002")
                put("attemptSeq", 2)
                put("txnRef", "NEW_TXN_02")
                put("amountVnd", 200_000L)
                put("state", "CREATED")
                put("duplicate", false)
                put("createdAt", "2026-11-20T10:02:00Z")
            }
            JsonObject(obj + ("payments" to JsonArray(listOf(p1, p2))))
        }
        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/public/payments/vnpay/return", 200, "vnpay-return-200-failed.json")
            respondJson("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}", 200, orderWithNewerAttempt)
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val oldLink = PaymentReturnLink(mapOf("vnp_TxnRef" to "OLD_TXN_01", "vnp_ResponseCode" to "24", "vnp_SecureHash" to "sig"))
        val vm = PaymentResultViewModel(
            initialEntry = PaymentResultEntry.ByReturnLink(oldLink),
            gateway = PaymentTestFixtures.createGateway(harness),
            pendingOrderStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() },
            apiBaseUrl = harness.server.url("/"),
            scope = scope,
        )
        try {
            vm.start()
            mountResult(vm)
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.RESULT_HEADING).fetchSemanticsNodes().isNotEmpty()
            }
            // Must follow current order state (Checking), NOT show Failed from old txnRef!
            composeRule.onNodeWithText("Đang chờ xác nhận thanh toán").assertIsDisplayed()
            composeRule.onNodeWithText("Thanh toán chưa thành công").assertDoesNotExist()
        } finally {
            vm.onLeaveScreen()
            scope.cancel()
            harness.close()
        }
    }

    @Test
    fun closedStatesShowReplacementOrBuyAgainAndAlwaysViewOrders() {
        var openedOrder: String? = null
        var openedCheckout: String? = null
        val replacedBody = PaymentTestFixtures.mutateJson("orders-detail-200-replaced.json") { obj ->
            JsonObject(obj + ("replacedByOrderCode" to JsonPrimitive("DH261126000099")))
        }
        val harness1 = PaymentTestFixtures.createHarness {
            respondJson("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}", 200, replacedBody)
        }
        val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val vm1 = PaymentResultViewModel(
            initialEntry = PaymentResultEntry.ByOrder(PaymentTestFixtures.ORDER_CODE),
            gateway = PaymentTestFixtures.createGateway(harness1),
            pendingOrderStore = PendingPaymentOrderStore { harness1.accountScopedStoreProvider.current() },
            apiBaseUrl = harness1.server.url("/"),
            scope = scope1,
        )
        try {
            vm1.start()
            mountResult(vm1, onOpenOrder = { openedOrder = it })
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.RESULT_REPLACEMENT_BUTTON).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Đơn hàng đã đóng").assertIsDisplayed()
            composeRule.onNodeWithText("Xem đơn thay thế DH261126000099").performClick()
            assertEquals("DH261126000099", openedOrder)
            composeRule.onNodeWithTag(PaymentTestTags.RESULT_VIEW_ORDERS_BUTTON).assertIsDisplayed()
        } finally {
            vm1.onLeaveScreen()
            scope1.cancel()
            harness1.close()
        }

        // Expired -> Mua lại bài thi + Xem đơn của tôi
        val harness2 = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}", 200, "orders-detail-200-expired.json")
        }
        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val vm2 = PaymentResultViewModel(
            initialEntry = PaymentResultEntry.ByOrder(PaymentTestFixtures.ORDER_CODE),
            gateway = PaymentTestFixtures.createGateway(harness2),
            pendingOrderStore = PendingPaymentOrderStore { harness2.accountScopedStoreProvider.current() },
            apiBaseUrl = harness2.server.url("/"),
            scope = scope2,
        )
        try {
            vm2.start()
            mountResult(vm2, onOpenCheckout = { openedCheckout = it })
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.RESULT_BUY_AGAIN_BUTTON).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Mua lại bài thi").performClick()
            assertEquals("EXAM-DEMO-04", openedCheckout)
            composeRule.onNodeWithTag(PaymentTestTags.RESULT_VIEW_ORDERS_BUTTON).assertIsDisplayed()
        } finally {
            vm2.onLeaveScreen()
            scope2.cancel()
            harness2.close()
        }
    }

    private fun mountResult(
        vm: PaymentResultViewModel,
        onOpenOrder: (String) -> Unit = {},
        onOpenCheckout: (String) -> Unit = {},
        onSwitchAccount: () -> Unit = {},
    ) {
        slotHost.set {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                PaymentResultScreen(
                    viewModel = vm,
                    paymentBrowser = RecordingPaymentBrowser(),
                    supportContact = SupportContact(),
                    onBack = {},
                    onOpenMyExams = {},
                    onOpenOrder = onOpenOrder,
                    onOpenOrders = {},
                    onOpenCheckout = onOpenCheckout,
                    onOpenCatalog = {},
                    onSwitchAccount = onSwitchAccount,
                )
            }
        }
    }
}
