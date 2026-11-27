package vn.unicom.thi360.feature.payment

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
import vn.unicom.thi360.feature.payment.domain.PendingPaymentOrderStore
import vn.unicom.thi360.feature.payment.ui.CheckoutScreen
import vn.unicom.thi360.feature.payment.ui.PaymentTestTags
import vn.unicom.thi360.feature.payment.viewmodel.CheckoutViewModel
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class CreateOrderIdempotencyTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun doubleTapSendsOnce() {
        val gate = CountDownLatch(1)
        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/public/storefront/products/${PaymentTestFixtures.EXAM_CODE}", 200, "storefront-products-detail-200-exam.json")
            respondApiFixture("POST", "/api/v1/me/price-quote", 200, "price-quote-200.json")
            respondJson("POST", "/api/v1/me/exam-access/query", 200, PaymentTestFixtures.examAccessBody(PaymentTestFixtures.EXAM_CODE, "NONE"))
            respondDynamic("POST", "/api/v1/me/orders") {
                gate.await(5, TimeUnit.SECONDS)
                ContractFakeServer.apiFixtureResponse(201, "orders-create-201-redirect.json")
            }
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
            mountCheckout(vm)
            composeRule.waitUntilMainIdle { vm.state.value.isPayButtonEnabled }
            vm.submitOrder()
            vm.submitOrder()
            gate.countDown()
            composeRule.waitUntilMainIdle {
                harness.server.requestCount("POST", "/api/v1/me/orders") == 1 &&
                    vm.state.value.isPayButtonEnabled
            }
            assertEquals(1, harness.server.requestCount("POST", "/api/v1/me/orders"))
        } finally {
            gate.countDown()
            scope.cancel()
            harness.close()
        }
    }

    @Test
    fun transientErrorsKeepKey() {
        val keySeq = AtomicInteger(0)
        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/public/storefront/products/${PaymentTestFixtures.EXAM_CODE}", 200, "storefront-products-detail-200-exam.json")
            respondApiFixture("POST", "/api/v1/me/price-quote", 200, "price-quote-200.json")
            respondJson("POST", "/api/v1/me/exam-access/query", 200, PaymentTestFixtures.examAccessBody(PaymentTestFixtures.EXAM_CODE, "NONE"))
            respondSequence(
                "POST",
                "/api/v1/me/orders",
                { PaymentTestFixtures.networkDisconnectResponse() },
                { MockResponse().setResponseCode(408).addHeader("Retry-After", "5").setBody("""{"code":"REQUEST_TIMEOUT","message":"timeout"}""") },
                { MockResponse().setResponseCode(429).setBody("""{"code":"RATE_LIMITED","message":"slow down"}""") },
                { MockResponse().setResponseCode(503).setBody("""{"code":"SERVICE_UNAVAILABLE","message":"down"}""") },
                { ContractFakeServer.apiFixtureResponse(201, "orders-create-201-redirect.json") },
            )
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val browser = RecordingPaymentBrowser()
        val vm = CheckoutViewModel(
            code = PaymentTestFixtures.EXAM_CODE,
            gateway = PaymentTestFixtures.createGateway(harness),
            pendingOrderStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() },
            apiBaseUrl = harness.server.url("/"),
            scope = scope,
            keyGenerator = { "00000000-0000-4000-8000-00000000000${keySeq.incrementAndGet()}" },
        )
        try {
            vm.loadInitial()
            mountCheckout(vm, browser)
            composeRule.waitUntilMainIdle { vm.state.value.isPayButtonEnabled }

            repeat(5) { attempt ->
                composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_PAY_BUTTON).performScrollTo().performClick()
                val expected = attempt + 1
                composeRule.waitUntilMainIdle {
                    harness.server.requestCount("POST", "/api/v1/me/orders") >= expected &&
                        vm.state.value.isPayButtonEnabled
                }
            }

            val keys = harness.server.requests("POST", "/api/v1/me/orders").map { it.getHeader("Idempotency-Key") }
            assertEquals(5, keys.size)
            assertEquals(setOf("00000000-0000-4000-8000-000000000001"), keys.toSet())
            composeRule.waitUntilMainIdle { browser.openedUrls.isNotEmpty() }
            assertTrue(browser.openedUrls.isNotEmpty())
        } finally {
            scope.cancel()
            harness.close()
        }
    }

    @Test
    fun newKeyAfterSuccess4xxOrVoucherChange() {
        val keySeq = AtomicInteger(0)
        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/public/storefront/products/${PaymentTestFixtures.EXAM_CODE}", 200, "storefront-products-detail-200-exam.json")
            respondSequence(
                "POST",
                "/api/v1/me/price-quote",
                { ContractFakeServer.apiFixtureResponse(200, "price-quote-200.json") },
                { ContractFakeServer.apiFixtureResponse(200, "price-quote-200-voucher.json") },
                { ContractFakeServer.apiFixtureResponse(200, "price-quote-200.json") },
            )
            respondJson("POST", "/api/v1/me/exam-access/query", 200, PaymentTestFixtures.examAccessBody(PaymentTestFixtures.EXAM_CODE, "NONE"))
            respondSequence(
                "POST",
                "/api/v1/me/orders",
                // 1. 400 deterministic 4xx -> resets key
                { MockResponse().setResponseCode(400).setBody("""{"code":"VALIDATION_FAILED","message":"bad"}""") },
                // 2. 503 transient -> keeps key #2
                { MockResponse().setResponseCode(503).setBody("""{"code":"SERVICE_UNAVAILABLE","message":"down"}""") },
                // (voucher apply resets key #2!)
                // 3. 503 transient -> keeps key #3
                { MockResponse().setResponseCode(503).setBody("""{"code":"SERVICE_UNAVAILABLE","message":"down"}""") },
                // (voucher remove resets key #3!)
                // 4. 201 success -> resets key #4
                { ContractFakeServer.apiFixtureResponse(201, "orders-create-201-redirect.json") },
                // 5. 201 success -> uses key #5
                { ContractFakeServer.apiFixtureResponse(201, "orders-create-201-redirect.json") },
            )
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val vm = CheckoutViewModel(
            code = PaymentTestFixtures.EXAM_CODE,
            gateway = PaymentTestFixtures.createGateway(harness),
            pendingOrderStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() },
            apiBaseUrl = harness.server.url("/"),
            scope = scope,
            keyGenerator = { "00000000-0000-4000-8000-00000000000${keySeq.incrementAndGet()}" },
        )
        try {
            vm.loadInitial()
            mountCheckout(vm)
            composeRule.waitUntilMainIdle { vm.state.value.isPayButtonEnabled }

            // Call 1 -> 400 (key #1, reset)
            vm.submitOrder()
            composeRule.waitUntilMainIdle { harness.server.requestCount("POST", "/api/v1/me/orders") == 1 && vm.state.value.isPayButtonEnabled }

            // Call 2 -> 503 (key #2, retained)
            vm.submitOrder()
            composeRule.waitUntilMainIdle { harness.server.requestCount("POST", "/api/v1/me/orders") == 2 && vm.state.value.isPayButtonEnabled }
            assertEquals("00000000-0000-4000-8000-000000000002", vm.currentCreateIdempotencyKey)

            // Apply voucher -> MUST reset key #2 (ĐT 134)
            vm.onVoucherInputChanged("EXAM-DEMO-03")
            vm.applyVoucher()
            composeRule.waitUntilMainIdle { harness.server.requestCount("POST", "/api/v1/me/price-quote") == 2 && !vm.state.value.voucher.submitting }
            assertEquals(null, vm.currentCreateIdempotencyKey)

            // Call 3 -> 503 (key #3, retained)
            vm.submitOrder()
            composeRule.waitUntilMainIdle { harness.server.requestCount("POST", "/api/v1/me/orders") == 3 && vm.state.value.isPayButtonEnabled }
            assertEquals("00000000-0000-4000-8000-000000000003", vm.currentCreateIdempotencyKey)

            // Remove voucher -> MUST reset key #3 (ĐT 134)
            vm.removeVoucher()
            composeRule.waitUntilMainIdle { harness.server.requestCount("POST", "/api/v1/me/price-quote") == 3 && !vm.state.value.voucher.submitting }
            assertEquals(null, vm.currentCreateIdempotencyKey)

            // Call 4 -> 201 (key #4, reset)
            vm.submitOrder()
            composeRule.waitUntilMainIdle { harness.server.requestCount("POST", "/api/v1/me/orders") == 4 && vm.state.value.isPayButtonEnabled }

            // Call 5 -> 201 (key #5)
            vm.submitOrder()
            composeRule.waitUntilMainIdle { harness.server.requestCount("POST", "/api/v1/me/orders") == 5 && vm.state.value.isPayButtonEnabled }

            val keys = harness.server.requests("POST", "/api/v1/me/orders").map { it.getHeader("Idempotency-Key") }
            assertEquals(5, keys.toSet().size)
            assertNotEquals(keys[0], keys[1])
            assertNotEquals(keys[1], keys[2])
            assertNotEquals(keys[2], keys[3])
            assertNotEquals(keys[3], keys[4])
        } finally {
            scope.cancel()
            harness.close()
        }
    }

    @Test
    fun reusedAndReplacedOrderCodes() {
        val replacedOrderBody = PaymentTestFixtures.mutateJson("orders-create-201-redirect.json") { obj ->
            val order = obj["order"]!!.jsonObject
            JsonObject(
                obj + mapOf(
                    "order" to JsonObject(order + ("code" to JsonPrimitive("DH261126000099"))),
                    "paymentUrl" to JsonPrimitive("https://sandbox.vnpayment.vn/pay?txn=NEW99"),
                ),
            )
        }
        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/public/storefront/products/${PaymentTestFixtures.EXAM_CODE}", 200, "storefront-products-detail-200-exam.json")
            respondApiFixture("POST", "/api/v1/me/price-quote", 200, "price-quote-200.json")
            respondJson("POST", "/api/v1/me/exam-access/query", 200, PaymentTestFixtures.examAccessBody(PaymentTestFixtures.EXAM_CODE, "NONE"))
            respondSequence(
                "POST",
                "/api/v1/me/orders",
                { ContractFakeServer.apiFixtureResponse(201, "orders-create-201-reused.json") },
                { ContractFakeServer.jsonResponse(201, replacedOrderBody) },
            )
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
            clockMillis = { 10_000L },
        )
        try {
            vm.loadInitial()
            mountCheckout(vm, browser)
            composeRule.waitUntilMainIdle { vm.state.value.isPayButtonEnabled }

            // Reused pending order code
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_PAY_BUTTON).performScrollTo().performClick()
            composeRule.waitUntilMainIdle { browser.openedUrls.size == 1 }
            assertEquals(PaymentTestFixtures.ORDER_CODE, pendingStore.readValid(10_000L)?.orderCode)

            // Replaced order code
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_PAY_BUTTON).performScrollTo().performClick()
            composeRule.waitUntilMainIdle { browser.openedUrls.size == 2 }
            assertEquals("DH261126000099", pendingStore.readValid(10_000L)?.orderCode)
            assertEquals("https://sandbox.vnpayment.vn/pay?txn=NEW99", browser.openedUrls[1])
        } finally {
            scope.cancel()
            harness.close()
        }
    }

    private fun mountCheckout(
        vm: CheckoutViewModel,
        browser: RecordingPaymentBrowser = RecordingPaymentBrowser(),
    ) {
        composeRule.setContent {
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
