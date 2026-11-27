package vn.unicom.thi360.feature.payment

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.config.SupportContact
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.session.SessionState
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.payment.domain.PendingPaymentOrderStore
import vn.unicom.thi360.feature.payment.ui.CheckoutScreen
import vn.unicom.thi360.feature.payment.ui.PaymentTestTags
import vn.unicom.thi360.feature.payment.viewmodel.CheckoutViewModel
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class CreateOrderErrorsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun voucherRejectedOnCreateClearsVoucherAndRequotes() {
        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/public/storefront/products/${PaymentTestFixtures.EXAM_CODE}", 200, "storefront-products-detail-200-exam.json")
            respondSequence(
                "POST",
                "/api/v1/me/price-quote",
                { ContractFakeServer.apiFixtureResponse(200, "price-quote-200-voucher.json") },
                { ContractFakeServer.apiFixtureResponse(200, "price-quote-200.json") },
            )
            respondJson("POST", "/api/v1/me/exam-access/query", 200, PaymentTestFixtures.examAccessBody(PaymentTestFixtures.EXAM_CODE, "NONE"))
            respondApiFixture("POST", "/api/v1/me/orders", 422, "orders-create-422-voucher-invalid.json")
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val vm = CheckoutViewModel(
            code = PaymentTestFixtures.EXAM_CODE,
            gateway = PaymentTestFixtures.createGateway(harness),
            pendingOrderStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() },
            apiBaseUrl = harness.server.url("/"),
            scope = scope,
        )
        try {
            val host = ComposeSlotHost(composeRule)
            vm.loadInitial()
            mountCheckout(host, vm)
            composeRule.waitUntilMainIdle { vm.state.value.isPayButtonEnabled }
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_PAY_BUTTON).performScrollTo().performClick()
            composeRule.waitUntilMainIdle {
                harness.server.requestCount("POST", "/api/v1/me/price-quote") == 2 &&
                    vm.state.value.activeQuote?.finalPrice == 200_000L
            }

            composeRule.onNodeWithText("Mã giảm giá không tồn tại hoặc không hợp lệ.").performScrollTo().assertIsDisplayed()
            assertEquals("", vm.state.value.voucher.input)
            assertEquals(200_000L, vm.state.value.activeQuote?.finalPrice)
        } finally {
            scope.cancel()
            harness.close()
        }
    }

    @Test
    fun paymentAttemptsExceededAndRateLimitedAndGenericErrors() {
        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/public/storefront/products/${PaymentTestFixtures.EXAM_CODE}", 200, "storefront-products-detail-200-exam.json")
            respondApiFixture("POST", "/api/v1/me/price-quote", 200, "price-quote-200.json")
            respondJson("POST", "/api/v1/me/exam-access/query", 200, PaymentTestFixtures.examAccessBody(PaymentTestFixtures.EXAM_CODE, "NONE"))
            respondSequence(
                "POST",
                "/api/v1/me/orders",
                { ContractFakeServer.apiFixtureResponse(422, "orders-create-422-payment-attempts-exceeded.json") },
                { MockResponse().setResponseCode(429).setBody("""{"code":"RATE_LIMITED","message":"rate"}""") },
                { ContractFakeServer.apiFixtureResponse(409, "orders-create-409-idempotency-key-reused.json") },
                { MockResponse().setResponseCode(500).setBody("""{"code":"INTERNAL_ERROR","message":"err"}""") },
                { PaymentTestFixtures.networkDisconnectResponse() },
                { MockResponse().setResponseCode(400).setBody("""{"code":"UNKNOWN_CODE","message":"?"}""") },
            )
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val vm = CheckoutViewModel(
            code = PaymentTestFixtures.EXAM_CODE,
            gateway = PaymentTestFixtures.createGateway(harness),
            pendingOrderStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() },
            apiBaseUrl = harness.server.url("/"),
            scope = scope,
        )
        try {
            val host = ComposeSlotHost(composeRule)
            vm.loadInitial()
            mountCheckout(host, vm)
            composeRule.waitUntilMainIdle { vm.state.value.isPayButtonEnabled }

            val expectedMessages = listOf(
                "Đơn đã vượt quá số lần tạo giao dịch thanh toán. Vui lòng tạo đơn mới.",
                "Bạn thao tác quá nhanh. Vui lòng chờ một lát rồi thử lại.",
                "Không thể tạo đơn hàng. Vui lòng thử lại.",
                "Không thể tạo đơn hàng. Vui lòng thử lại.",
                "Không thể tạo đơn hàng. Vui lòng thử lại.",
                "Không thể tạo đơn hàng. Vui lòng thử lại.",
            )
            expectedMessages.forEachIndexed { idx, expected ->
                composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_PAY_BUTTON).performScrollTo().performClick()
                val target = idx + 1
                composeRule.waitUntilMainIdle {
                    harness.server.requestCount("POST", "/api/v1/me/orders") >= target &&
                        vm.state.value.isPayButtonEnabled
                }
                composeRule.onNodeWithText(expected).performScrollTo().assertIsDisplayed()
                composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_PAY_BUTTON).performScrollTo().assertIsEnabled()
                assertEquals(200_000L, vm.state.value.activeQuote?.finalPrice)
            }
        } finally {
            scope.cancel()
            harness.close()
        }
    }

    @Test
    fun accountChangedDifferentAndSameAccount() {
        val host = ComposeSlotHost(composeRule)
        val otherMe = PaymentTestFixtures.mutateJson("me-200.json") { obj ->
            JsonObject(
                obj + mapOf(
                    "id" to JsonPrimitive(PaymentTestFixtures.OTHER_ACCOUNT_ID),
                    "username" to JsonPrimitive("student.other"),
                    "fullName" to JsonPrimitive("Trần Thị Bình"),
                ),
            )
        }
        val harness = PaymentTestFixtures.createHarness(installDefaultMeHandler = false) {
            respondSequence(
                "GET",
                "/api/v1/me",
                { ContractFakeServer.apiFixtureResponse(200, "me-200.json") },
                { ContractFakeServer.jsonResponse(200, otherMe) },
            )
            respondApiFixture("GET", "/api/v1/public/storefront/products/${PaymentTestFixtures.EXAM_CODE}", 200, "storefront-products-detail-200-exam.json")
            respondApiFixture("POST", "/api/v1/me/price-quote", 200, "price-quote-200.json")
            respondJson("POST", "/api/v1/me/exam-access/query", 200, PaymentTestFixtures.examAccessBody(PaymentTestFixtures.EXAM_CODE, "NONE"))
            respondApiFixture("POST", "/api/v1/me/orders", 409, "orders-create-409-account-changed.json")
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val vm = CheckoutViewModel(
            code = PaymentTestFixtures.EXAM_CODE,
            gateway = PaymentTestFixtures.createGateway(harness),
            pendingOrderStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() },
            apiBaseUrl = harness.server.url("/"),
            scope = scope,
        )
        try {
            vm.loadInitial()
            mountCheckout(host, vm)
            composeRule.waitUntilMainIdle { vm.state.value.isPayButtonEnabled }
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_PAY_BUTTON).performScrollTo().performClick()
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.CHECKOUT_ACCOUNT_CHANGED_BANNER).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Tài khoản đăng nhập đã thay đổi").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Tiếp tục với student.other").performScrollTo().assertIsDisplayed()

            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_ACCOUNT_CONTINUE_BUTTON).performScrollTo().performClick()
            composeRule.waitUntilMainIdle {
                harness.server.requestCount("POST", "/api/v1/me/price-quote") == 2 &&
                    harness.server.requestCount("POST", "/api/v1/me/exam-access/query") == 2
            }
            assertNull(vm.state.value.accountConflict)
            assertEquals("student.other", vm.state.value.acknowledgedUser?.username)
        } finally {
            scope.cancel()
            harness.close()
        }

        // Same account returned on 409 ACCOUNT_CHANGED -> generic error
        val sameHarness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/public/storefront/products/${PaymentTestFixtures.EXAM_CODE}", 200, "storefront-products-detail-200-exam.json")
            respondApiFixture("POST", "/api/v1/me/price-quote", 200, "price-quote-200.json")
            respondJson("POST", "/api/v1/me/exam-access/query", 200, PaymentTestFixtures.examAccessBody(PaymentTestFixtures.EXAM_CODE, "NONE"))
            respondApiFixture("POST", "/api/v1/me/orders", 409, "orders-create-409-account-changed.json")
        }
        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val vm2 = CheckoutViewModel(
            code = PaymentTestFixtures.EXAM_CODE,
            gateway = PaymentTestFixtures.createGateway(sameHarness),
            pendingOrderStore = PendingPaymentOrderStore { sameHarness.accountScopedStoreProvider.current() },
            apiBaseUrl = sameHarness.server.url("/"),
            scope = scope2,
        )
        try {
            vm2.loadInitial()
            mountCheckout(host, vm2)
            composeRule.waitUntilMainIdle { vm2.state.value.isPayButtonEnabled }
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_PAY_BUTTON).performScrollTo().performClick()
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.CHECKOUT_SUBMIT_ERROR).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Không thể tạo đơn hàng. Vui lòng thử lại.").performScrollTo().assertIsDisplayed()
        } finally {
            scope2.cancel()
            sameHarness.close()
        }
    }

    @Test
    fun unauthorizedWithFailedRefreshSignsOutAndShowsNoOrderCreated() {
        val host = ComposeSlotHost(composeRule)
        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/public/storefront/products/${PaymentTestFixtures.EXAM_CODE}", 200, "storefront-products-detail-200-exam.json")
            respondApiFixture("POST", "/api/v1/me/price-quote", 200, "price-quote-200.json")
            respondJson("POST", "/api/v1/me/exam-access/query", 200, PaymentTestFixtures.examAccessBody(PaymentTestFixtures.EXAM_CODE, "NONE"))
            respondJson("POST", "/api/v1/me/orders", 401, """{"code":"UNAUTHORIZED","message":"expired"}""")
            respondJson("POST", "/api/v1/auth/mobile/refresh", 401, """{"code":"TOKEN_INVALID","message":"invalid"}""")
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val pendingStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() }
        val browser = RecordingPaymentBrowser()
        val vm = CheckoutViewModel(
            code = PaymentTestFixtures.EXAM_CODE,
            gateway = PaymentTestFixtures.createGateway(harness),
            pendingOrderStore = pendingStore,
            apiBaseUrl = harness.server.url("/"),
            scope = scope,
        )
        try {
            vm.loadInitial()
            mountCheckout(host, vm, browser)
            composeRule.waitUntilMainIdle { vm.state.value.isPayButtonEnabled }
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_PAY_BUTTON).performScrollTo().performClick()
            composeRule.waitUntilMainIdle { harness.stack.sessionManager.state.value is SessionState.SignedOut }

            assertTrue(harness.stack.sessionManager.state.value is SessionState.SignedOut)
            assertTrue("No payment browser should open", browser.openedUrls.isEmpty())
            assertNull("No pending order should be saved", pendingStore.readValid(System.currentTimeMillis()))
            assertNull("No navigation event for created order", vm.state.value.navigationEvent)
        } finally {
            scope.cancel()
            harness.close()
        }
    }

    private fun mountCheckout(
        host: ComposeSlotHost,
        vm: CheckoutViewModel,
        browser: RecordingPaymentBrowser = RecordingPaymentBrowser(),
    ) {
        host.set {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                CheckoutScreen(
                    viewModel = vm,
                    paymentBrowser = browser,
                    supportContact = SupportContact(),
                    onBack = {},
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
    }
}
