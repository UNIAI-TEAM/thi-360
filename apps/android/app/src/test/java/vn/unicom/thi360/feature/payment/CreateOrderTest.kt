package vn.unicom.thi360.feature.payment

import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.unicom.thi360.feature.payment.domain.PendingPaymentOrderStore
import vn.unicom.thi360.feature.payment.viewmodel.CheckoutNavigationEvent
import vn.unicom.thi360.feature.payment.viewmodel.CheckoutViewModel

@OptIn(ExperimentalCoroutinesApi::class)
class CreateOrderTest {

    @Test
    fun requestHeaderAndBody() {
        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/public/storefront/products/${PaymentTestFixtures.EXAM_CODE}", 200, "storefront-products-detail-200-exam.json")
            respondApiFixture("POST", "/api/v1/me/price-quote", 200, "price-quote-200-voucher.json")
            respondJson("POST", "/api/v1/me/exam-access/query", 200, PaymentTestFixtures.examAccessBody(PaymentTestFixtures.EXAM_CODE, "NONE"))
            respondApiFixture("POST", "/api/v1/me/orders", 201, "orders-create-201-redirect.json")
        }
        val testScope = TestScope(StandardTestDispatcher())
        try {
            val vm = CheckoutViewModel(
                code = PaymentTestFixtures.EXAM_CODE,
                gateway = PaymentTestFixtures.createGateway(harness),
                pendingOrderStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() },
                apiBaseUrl = harness.server.url("/"),
                scope = testScope,
            )
            vm.loadInitial()
            waitUntilIdle(testScope) { vm.state.value.isPayButtonEnabled }
            vm.submitOrder()
            waitUntilIdle(testScope) {
                harness.server.requestCount("POST", "/api/v1/me/orders") == 1 &&
                    vm.state.value.navigationEvent != null
            }

            val req = harness.server.lastRequest("POST", "/api/v1/me/orders")!!
            val keyHeader = req.getHeader("Idempotency-Key")
            assertNotNull(keyHeader)
            UUID.fromString(keyHeader)

            val body = Json.parseToJsonElement(req.body.readUtf8()).jsonObject
            assertEquals(PaymentTestFixtures.EXAM_CODE, body["code"]!!.toString().trim('"'))
            assertEquals(PaymentTestFixtures.ACCOUNT_ID, body["expectedAccountId"]!!.toString().trim('"'))
            assertEquals("160000", body["expectedAmountDue"]!!.toString())
            assertEquals("1", body["expectedVersionNo"]!!.toString())
            assertEquals("EXAM-DEMO-03", body["voucherCode"]!!.toString().trim('"'))
        } finally {
            harness.close()
        }

        // Without voucher: voucherCode omitted
        val noVoucherHarness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/public/storefront/products/${PaymentTestFixtures.EXAM_CODE}", 200, "storefront-products-detail-200-exam.json")
            respondApiFixture("POST", "/api/v1/me/price-quote", 200, "price-quote-200.json")
            respondJson("POST", "/api/v1/me/exam-access/query", 200, PaymentTestFixtures.examAccessBody(PaymentTestFixtures.EXAM_CODE, "NONE"))
            respondApiFixture("POST", "/api/v1/me/orders", 201, "orders-create-201-redirect.json")
        }
        val scope2 = TestScope(StandardTestDispatcher())
        try {
            val vm2 = CheckoutViewModel(
                code = PaymentTestFixtures.EXAM_CODE,
                gateway = PaymentTestFixtures.createGateway(noVoucherHarness),
                pendingOrderStore = PendingPaymentOrderStore { noVoucherHarness.accountScopedStoreProvider.current() },
                apiBaseUrl = noVoucherHarness.server.url("/"),
                scope = scope2,
            )
            vm2.loadInitial()
            waitUntilIdle(scope2) { vm2.state.value.isPayButtonEnabled }
            vm2.submitOrder()
            waitUntilIdle(scope2) {
                noVoucherHarness.server.requestCount("POST", "/api/v1/me/orders") == 1 &&
                    vm2.state.value.navigationEvent != null
            }
            val body = Json.parseToJsonElement(
                noVoucherHarness.server.lastRequest("POST", "/api/v1/me/orders")!!.body.readUtf8(),
            ).jsonObject
            assertFalse("voucherCode must be omitted when quote has no voucher", body.containsKey("voucherCode"))
        } finally {
            noVoucherHarness.close()
        }
    }

    @Test
    fun nextRedirectFulfilledChecking() {
        // 1. REDIRECT -> opens browser & saves PendingPaymentOrder
        runNextCase("orders-create-201-redirect.json") { event, pendingStore ->
            val openBrowser = event as CheckoutNavigationEvent.OpenBrowser
            assertEquals(PaymentTestFixtures.ORDER_CODE, openBrowser.orderCode)
            assertTrue(openBrowser.resolvedUrl.startsWith("http://localhost:3000/mock-vnpay/pay"))
            assertEquals(PaymentTestFixtures.ORDER_CODE, pendingStore.readValid(1_000L)?.orderCode)
        }

        // 2. FULFILLED -> opens payment-result directly, no browser, no pending order
        runNextCase("orders-create-201-fulfilled.json") { event, pendingStore ->
            val openResult = event as CheckoutNavigationEvent.OpenPaymentResult
            assertEquals(PaymentTestFixtures.ORDER_CODE, openResult.orderCode)
            assertEquals(null, pendingStore.readValid(1_000L))
        }

        // 3. CHECKING -> opens payment-result directly, no browser, no pending order
        runNextCase("orders-create-201-checking.json") { event, pendingStore ->
            val openResult = event as CheckoutNavigationEvent.OpenPaymentResult
            assertEquals(PaymentTestFixtures.ORDER_CODE, openResult.orderCode)
            assertEquals(null, pendingStore.readValid(1_000L))
        }
    }

    private fun runNextCase(
        createFixture: String,
        verify: (CheckoutNavigationEvent, PendingPaymentOrderStore) -> Unit,
    ) {
        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/public/storefront/products/${PaymentTestFixtures.EXAM_CODE}", 200, "storefront-products-detail-200-exam.json")
            respondApiFixture("POST", "/api/v1/me/price-quote", 200, "price-quote-200.json")
            respondJson("POST", "/api/v1/me/exam-access/query", 200, PaymentTestFixtures.examAccessBody(PaymentTestFixtures.EXAM_CODE, "NONE"))
            respondApiFixture("POST", "/api/v1/me/orders", 201, createFixture)
        }
        val testScope = TestScope(StandardTestDispatcher())
        val pendingStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() }
        try {
            val vm = CheckoutViewModel(
                code = PaymentTestFixtures.EXAM_CODE,
                gateway = PaymentTestFixtures.createGateway(harness),
                pendingOrderStore = pendingStore,
                apiBaseUrl = harness.server.url("/"),
                scope = testScope,
                clockMillis = { 1_000L },
            )
            vm.loadInitial()
            waitUntilIdle(testScope) { vm.state.value.isPayButtonEnabled }
            vm.submitOrder()
            waitUntilIdle(testScope) { vm.state.value.navigationEvent != null }
            verify(vm.state.value.navigationEvent!!, pendingStore)
        } finally {
            harness.close()
        }
    }

    private fun waitUntilIdle(scope: TestScope, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000L
        while (!condition() && System.currentTimeMillis() < deadline) {
            scope.advanceUntilIdle()
            Thread.sleep(10)
        }
        scope.advanceUntilIdle()
        assertTrue("Condition was not met before timeout", condition())
    }
}
