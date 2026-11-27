package vn.unicom.thi360.feature.payment

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.unicom.thi360.feature.payment.domain.PendingPaymentOrderStore
import vn.unicom.thi360.feature.payment.domain.SeenPrice
import vn.unicom.thi360.feature.payment.viewmodel.CheckoutViewModel
import vn.unicom.thi360.testing.ContractFakeServer

@OptIn(ExperimentalCoroutinesApi::class)
class PriceChangeTest {

    @Test
    fun bannerConditions() = runTest {
        assertBannerShown(
            seen = SeenPrice(versionNo = 1, price = 200_000L),
            quoteFixture = "price-quote-200-changed.json",
            expectedShown = true,
            expectedSeenPrice = 200_000L,
        )
        assertBannerShown(
            seen = SeenPrice(versionNo = 1, price = 200_000L),
            quoteFixture = "price-quote-200.json",
            quoteMutator = { obj -> JsonObject(obj + ("versionNo" to JsonPrimitive(2))) },
            expectedShown = true,
            expectedSeenPrice = 200_000L,
        )
        assertBannerShown(
            seen = SeenPrice(versionNo = 1, price = 150_000L),
            quoteFixture = "price-quote-200.json",
            expectedShown = true,
            expectedSeenPrice = 150_000L,
        )
        assertBannerShown(
            seen = SeenPrice(versionNo = 1, price = 200_000L),
            quoteFixture = "price-quote-200.json",
            expectedShown = false,
            expectedSeenPrice = 200_000L,
        )
    }

    private fun assertBannerShown(
        seen: SeenPrice,
        quoteFixture: String,
        quoteMutator: (JsonObject) -> JsonObject = { it },
        expectedShown: Boolean,
        expectedSeenPrice: Long?,
    ) {
        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/public/storefront/products/${PaymentTestFixtures.EXAM_CODE}", 200, "storefront-products-detail-200-exam.json")
            respondJson("POST", "/api/v1/me/price-quote", 200, PaymentTestFixtures.mutateJson(quoteFixture, quoteMutator))
            respondJson("POST", "/api/v1/me/exam-access/query", 200, PaymentTestFixtures.examAccessBody(PaymentTestFixtures.EXAM_CODE, "NONE"))
        }
        val testScope = TestScope(StandardTestDispatcher())
        try {
            val vm = CheckoutViewModel(
                code = PaymentTestFixtures.EXAM_CODE,
                seenPrice = seen,
                gateway = PaymentTestFixtures.createGateway(harness),
                pendingOrderStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() },
                apiBaseUrl = harness.server.url("/"),
                scope = testScope,
            )
            vm.loadInitial()
            waitUntilIdle(testScope) { vm.state.value.activeQuote != null }
            assertEquals(expectedShown, vm.state.value.showPriceChangedBanner)
            assertEquals(expectedSeenPrice, vm.state.value.bannerSeenPrice)
        } finally {
            harness.close()
        }
    }

    @Test
    fun priceChangedOnCreate() {
        val updatedQuoteConflict = PaymentTestFixtures.mutateJson("orders-create-409-price-changed.json") { obj ->
            val quote = obj["quote"]!!.jsonObject
            JsonObject(
                obj + (
                    "quote" to JsonObject(
                        quote + mapOf(
                            "versionNo" to JsonPrimitive(4),
                            "finalPrice" to JsonPrimitive(240_000L),
                            "listPrice" to JsonPrimitive(240_000L),
                        ),
                    )
                    ),
            )
        }
        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/public/storefront/products/${PaymentTestFixtures.EXAM_CODE}", 200, "storefront-products-detail-200-exam.json")
            respondApiFixture("POST", "/api/v1/me/price-quote", 200, "price-quote-200.json")
            respondJson("POST", "/api/v1/me/exam-access/query", 200, PaymentTestFixtures.examAccessBody(PaymentTestFixtures.EXAM_CODE, "NONE"))
            respondJson("POST", "/api/v1/me/orders", 409, updatedQuoteConflict)
        }
        val testScope = TestScope(StandardTestDispatcher())
        try {
            val vm = CheckoutViewModel(
                code = PaymentTestFixtures.EXAM_CODE,
                seenPrice = SeenPrice(versionNo = 1, price = 200_000L),
                gateway = PaymentTestFixtures.createGateway(harness),
                pendingOrderStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() },
                apiBaseUrl = harness.server.url("/"),
                scope = testScope,
            )
            vm.loadInitial()
            waitUntilIdle(testScope) { vm.state.value.isPayButtonEnabled }
            vm.submitOrder()
            waitUntilIdle(testScope) { vm.state.value.activeQuote?.versionNo == 4 }

            assertTrue(vm.state.value.showPriceChangedBanner)
            assertEquals(200_000L, vm.state.value.bannerSeenPrice)
            assertEquals(240_000L, vm.state.value.activeQuote?.finalPrice)
            assertEquals(4, vm.state.value.activeQuote?.versionNo)
            assertNull("Must not navigate to payment browser on 409 PRICE_CHANGED", vm.state.value.navigationEvent)
        } finally {
            harness.close()
        }
    }

    @Test
    fun seenValuesKeptAfterPriceChanged() {
        val initialSeen = SeenPrice(versionNo = 1, price = 150_000L)
        val priceChangedBody = PaymentTestFixtures.mutateJson("orders-create-409-price-changed.json") { obj ->
            val quote = obj["quote"]!!.jsonObject
            JsonObject(
                obj + (
                    "quote" to JsonObject(
                        quote + mapOf(
                            "versionNo" to JsonPrimitive(5),
                            "finalPrice" to JsonPrimitive(260_000L),
                            "listPrice" to JsonPrimitive(260_000L),
                        ),
                    )
                    ),
            )
        }
        val voucherAppliedBody = PaymentTestFixtures.mutateJson("price-quote-200-voucher.json") { obj ->
            JsonObject(
                obj + mapOf(
                    "versionNo" to JsonPrimitive(5),
                    "listPrice" to JsonPrimitive(260_000L),
                    "finalPrice" to JsonPrimitive(220_000L),
                ),
            )
        }
        val removedVoucherBody = PaymentTestFixtures.mutateJson("price-quote-200.json") { obj ->
            JsonObject(
                obj + mapOf(
                    "versionNo" to JsonPrimitive(5),
                    "listPrice" to JsonPrimitive(260_000L),
                    "finalPrice" to JsonPrimitive(260_000L),
                ),
            )
        }
        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/public/storefront/products/${PaymentTestFixtures.EXAM_CODE}", 200, "storefront-products-detail-200-exam.json")
            respondSequence(
                "POST",
                "/api/v1/me/price-quote",
                { ContractFakeServer.apiFixtureResponse(200, "price-quote-200.json") },
                { ContractFakeServer.jsonResponse(200, voucherAppliedBody) },
                { ContractFakeServer.jsonResponse(200, removedVoucherBody) },
            )
            respondJson("POST", "/api/v1/me/exam-access/query", 200, PaymentTestFixtures.examAccessBody(PaymentTestFixtures.EXAM_CODE, "NONE"))
            respondSequence(
                "POST",
                "/api/v1/me/orders",
                { ContractFakeServer.jsonResponse(409, priceChangedBody) },
                { ContractFakeServer.apiFixtureResponse(201, "orders-create-201-redirect.json") },
            )
        }
        val testScope = TestScope(StandardTestDispatcher())
        try {
            val vm = CheckoutViewModel(
                code = PaymentTestFixtures.EXAM_CODE,
                seenPrice = initialSeen,
                gateway = PaymentTestFixtures.createGateway(harness),
                pendingOrderStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() },
                apiBaseUrl = harness.server.url("/"),
                scope = testScope,
            )
            vm.loadInitial()
            waitUntilIdle(testScope) { vm.state.value.isPayButtonEnabled }

            // Trigger 409 PRICE_CHANGED
            vm.submitOrder()
            waitUntilIdle(testScope) { vm.state.value.activeQuote?.versionNo == 5 }
            assertEquals(initialSeen, vm.state.value.seenPrice)
            assertTrue(vm.state.value.showPriceChangedBanner)

            // Apply voucher: must send ONLY original seenVersionNo (no seenPrice)
            vm.onVoucherInputChanged("EXAM-DEMO-03")
            vm.applyVoucher()
            waitUntilIdle(testScope) { harness.server.requestCount("POST", "/api/v1/me/price-quote") == 2 }
            val applyBody = Json.parseToJsonElement(
                harness.server.requests("POST", "/api/v1/me/price-quote")[1].body.readUtf8(),
            ).jsonObject
            assertEquals("1", applyBody["seenVersionNo"]!!.toString())
            assertFalse("Apply voucher must NOT send seenPrice", applyBody.containsKey("seenPrice"))
            assertTrue("Banner stays visible after applying voucher", vm.state.value.showPriceChangedBanner)

            // Remove voucher: must send BOTH original seenPrice and seenVersionNo
            vm.removeVoucher()
            waitUntilIdle(testScope) { harness.server.requestCount("POST", "/api/v1/me/price-quote") == 3 }
            val removeBody = Json.parseToJsonElement(
                harness.server.requests("POST", "/api/v1/me/price-quote")[2].body.readUtf8(),
            ).jsonObject
            assertEquals("1", removeBody["seenVersionNo"]!!.toString())
            assertEquals("150000", removeBody["seenPrice"]!!.toString())
            assertTrue("Banner stays visible after removing voucher", vm.state.value.showPriceChangedBanner)

            // Next createMyOrder sends expectedAmountDue and expectedVersionNo of the currently displayed quote (260000, v5)
            vm.submitOrder()
            waitUntilIdle(testScope) { harness.server.requestCount("POST", "/api/v1/me/orders") == 2 }
            val secondCreateBody = Json.parseToJsonElement(
                harness.server.requests("POST", "/api/v1/me/orders")[1].body.readUtf8(),
            ).jsonObject
            assertEquals("260000", secondCreateBody["expectedAmountDue"]!!.toString())
            assertEquals("5", secondCreateBody["expectedVersionNo"]!!.toString())
        } finally {
            harness.close()
        }
    }

    private fun waitUntilIdle(scope: TestScope, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000L
        while (!condition() && System.currentTimeMillis() < deadline) {
            scope.advanceUntilIdle()
            Thread.sleep(10)
        }
        scope.advanceUntilIdle()
        assertTrue("Condition was not met before timeout", condition())
    }
}
