package vn.unicom.thi360.feature.payment

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import vn.unicom.thi360.feature.payment.domain.SeenPrice
import vn.unicom.thi360.feature.payment.ui.CheckoutScreen
import vn.unicom.thi360.feature.payment.ui.PaymentTestTags
import vn.unicom.thi360.feature.payment.viewmodel.CheckoutViewModel
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class CheckoutQuoteTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun requestFields() {
        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/public/storefront/products/${PaymentTestFixtures.EXAM_CODE}", 200, "storefront-products-detail-200-exam.json")
            respondApiFixture("POST", "/api/v1/me/price-quote", 200, "price-quote-200.json")
            respondJson("POST", "/api/v1/me/exam-access/query", 200, PaymentTestFixtures.examAccessBody(PaymentTestFixtures.EXAM_CODE, "NONE"))
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            val vmWithSeen = CheckoutViewModel(
                code = PaymentTestFixtures.EXAM_CODE,
                seenPrice = SeenPrice(versionNo = 2, price = 180_000L),
                gateway = PaymentTestFixtures.createGateway(harness),
                pendingOrderStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() },
                apiBaseUrl = harness.server.url("/"),
                scope = scope,
            )
            vmWithSeen.loadInitial()
            composeRule.waitUntilMainIdle { harness.server.requestCount("POST", "/api/v1/me/price-quote") == 1 }
            val bodyWithSeen = Json.parseToJsonElement(
                harness.server.lastRequest("POST", "/api/v1/me/price-quote")!!.body.readUtf8(),
            ).jsonObject
            assertEquals(PaymentTestFixtures.EXAM_CODE, bodyWithSeen["code"]!!.toString().trim('"'))
            assertEquals("180000", bodyWithSeen["seenPrice"]!!.toString())
            assertEquals("2", bodyWithSeen["seenVersionNo"]!!.toString())
            assertFalse("voucherCode must be omitted when null", bodyWithSeen.containsKey("voucherCode"))

            harness.server.resetRecordedRequests()
            val vmWithoutSeen = CheckoutViewModel(
                code = PaymentTestFixtures.EXAM_CODE,
                seenPrice = SeenPrice(),
                gateway = PaymentTestFixtures.createGateway(harness),
                pendingOrderStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() },
                apiBaseUrl = harness.server.url("/"),
                scope = scope,
            )
            vmWithoutSeen.loadInitial()
            composeRule.waitUntilMainIdle { harness.server.requestCount("POST", "/api/v1/me/price-quote") == 1 }
            val bodyWithoutSeen = Json.parseToJsonElement(
                harness.server.lastRequest("POST", "/api/v1/me/price-quote")!!.body.readUtf8(),
            ).jsonObject
            assertEquals(PaymentTestFixtures.EXAM_CODE, bodyWithoutSeen["code"]!!.toString().trim('"'))
            assertFalse("seenPrice must be omitted when null", bodyWithoutSeen.containsKey("seenPrice"))
            assertFalse("seenVersionNo must be omitted when null", bodyWithoutSeen.containsKey("seenVersionNo"))
        } finally {
            scope.cancel()
            harness.close()
        }
    }

    @Test
    fun summaryLines() {
        val quoteWithPromoAndVoucher = PaymentTestFixtures.mutateJson("price-quote-200-voucher.json") { obj ->
            JsonObject(
                obj + mapOf(
                    "listPrice" to JsonPrimitive(250_000L),
                    "finalPrice" to JsonPrimitive(160_000L),
                    "discountApplied" to JsonPrimitive("COMBINED"),
                    "promotion" to buildJsonObject {
                        put("id", "00000000-0000-7000-8000-000000000099")
                        put("name", "Ưu đãi mùa thi")
                        put("discountType", "AMOUNT")
                        put("discountValue", 50_000L)
                        put("discountVnd", 50_000L)
                        put("endsAt", "2026-12-31T00:00:00Z")
                    },
                ),
            )
        }
        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/public/storefront/products/${PaymentTestFixtures.EXAM_CODE}", 200, "storefront-products-detail-200-exam.json")
            respondJson("POST", "/api/v1/me/price-quote", 200, quoteWithPromoAndVoucher)
            respondJson("POST", "/api/v1/me/exam-access/query", 200, PaymentTestFixtures.examAccessBody(PaymentTestFixtures.EXAM_CODE, "NONE"))
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
            composeRule.setContent {
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                    CheckoutScreen(
                        viewModel = vm,
                        paymentBrowser = RecordingPaymentBrowser(),
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
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.CHECKOUT_PAY_BUTTON).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Giá gốc").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("250.000 đ").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Ưu đãi tự động (Ưu đãi mùa thi)").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("-50.000 đ").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Mã giảm giá (EXAM-DEMO-03)").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("-40.000 đ").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Thành tiền").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("160.000 đ").performScrollTo().assertIsDisplayed()
        } finally {
            scope.cancel()
            harness.close()
        }
    }

    @Test
    fun ctaLabels() {
        val host = ComposeSlotHost(composeRule)
        assertCta(host, finalPrice = 200_000L, withVoucher = false, expectedCta = "Thanh toán qua VNPAY", expectedTotal = "200.000 đ")
        assertCta(host, finalPrice = 0L, withVoucher = true, expectedCta = "Dùng voucher và nhận bài thi", expectedTotal = "0 đ (đã áp dụng voucher)")
        assertCta(host, finalPrice = 0L, withVoucher = false, expectedCta = "Nhận bài thi miễn phí", expectedTotal = "0 đ")
    }

    private fun assertCta(
        host: ComposeSlotHost,
        finalPrice: Long,
        withVoucher: Boolean,
        expectedCta: String,
        expectedTotal: String,
    ) {
        val baseFixture = if (withVoucher) "price-quote-200-voucher.json" else "price-quote-200.json"
        val quoteBody = PaymentTestFixtures.mutateJson(baseFixture) { obj ->
            JsonObject(obj + ("finalPrice" to JsonPrimitive(finalPrice)))
        }
        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/public/storefront/products/${PaymentTestFixtures.EXAM_CODE}", 200, "storefront-products-detail-200-exam.json")
            respondJson("POST", "/api/v1/me/price-quote", 200, quoteBody)
            respondJson("POST", "/api/v1/me/exam-access/query", 200, PaymentTestFixtures.examAccessBody(PaymentTestFixtures.EXAM_CODE, "NONE"))
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
            host.set {
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                    CheckoutScreen(
                        viewModel = vm,
                        paymentBrowser = RecordingPaymentBrowser(),
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
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.CHECKOUT_PAY_BUTTON).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onAllNodesWithText(expectedTotal).onLast().performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText(expectedCta).performScrollTo().assertIsDisplayed()
        } finally {
            scope.cancel()
            harness.close()
        }
    }

    @Test
    fun quoteErrorNoAutoRetry() {
        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/public/storefront/products/${PaymentTestFixtures.EXAM_CODE}", 200, "storefront-products-detail-200-exam.json")
            respondSequence(
                "POST",
                "/api/v1/me/price-quote",
                { MockResponse().setResponseCode(503).setBody("""{"code":"SERVICE_UNAVAILABLE","message":"down"}""") },
                { ContractFakeServer.apiFixtureResponse(200, "price-quote-200.json") },
            )
            respondJson("POST", "/api/v1/me/exam-access/query", 200, PaymentTestFixtures.examAccessBody(PaymentTestFixtures.EXAM_CODE, "NONE"))
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
            composeRule.setContent {
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                    CheckoutScreen(
                        viewModel = vm,
                        paymentBrowser = RecordingPaymentBrowser(),
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
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.CHECKOUT_QUOTE_RETRY_BUTTON).fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(1, harness.server.requestCount("POST", "/api/v1/me/price-quote"))
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_QUOTE_RETRY_BUTTON).performScrollTo().performClick()
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.CHECKOUT_PAY_BUTTON).fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(2, harness.server.requestCount("POST", "/api/v1/me/price-quote"))
            assertTrue(vm.state.value.activeQuote != null)
        } finally {
            scope.cancel()
            harness.close()
        }
    }
}
