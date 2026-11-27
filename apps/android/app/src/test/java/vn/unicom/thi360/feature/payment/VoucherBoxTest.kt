package vn.unicom.thi360.feature.payment

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
class VoucherBoxTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val slotHost by lazy { ComposeSlotHost(composeRule) }

    @Test
    fun emptyAndWhitespaceRejectedLocallyWithoutApiCall() {
        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/public/storefront/products/${PaymentTestFixtures.EXAM_CODE}", 200, "storefront-products-detail-200-exam.json")
            respondApiFixture("POST", "/api/v1/me/price-quote", 200, "price-quote-200.json")
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
            mountCheckout(vm)
            composeRule.waitUntilMainIdle {
                vm.state.value.isPayButtonEnabled &&
                    composeRule.onAllNodesWithTag(PaymentTestTags.CHECKOUT_VOUCHER_APPLY).fetchSemanticsNodes().isNotEmpty()
            }
            val quoteCallsBefore = harness.server.requestCount("POST", "/api/v1/me/price-quote")
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_VOUCHER_INPUT).performScrollTo().performTextInput("   ")
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_VOUCHER_APPLY).performScrollTo().performClick()
            composeRule.onNodeWithText("Vui lòng nhập mã giảm giá.").performScrollTo().assertIsDisplayed()
            assertEquals(quoteCallsBefore, harness.server.requestCount("POST", "/api/v1/me/price-quote"))

            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_VOUCHER_INPUT).performScrollTo().performTextInput("A")
            composeRule.onNodeWithText("Vui lòng nhập mã giảm giá.").assertDoesNotExist()
        } finally {
            scope.cancel()
            harness.close()
        }
    }

    @Test
    fun appliedVariantsNotBetterAndRemove() {
        val promoQuote = PaymentTestFixtures.mutateJson("price-quote-200.json") { obj ->
            JsonObject(
                obj + mapOf(
                    "finalPrice" to JsonPrimitive(180_000L),
                    "discountApplied" to JsonPrimitive("PROMOTION_ONLY"),
                    "promotion" to buildJsonObject {
                        put("id", "00000000-0000-7000-8000-000000000011")
                        put("name", "Giảm tự động")
                        put("discountType", "AMOUNT")
                        put("discountValue", 20_000L)
                        put("discountVnd", 20_000L)
                        put("endsAt", "2026-12-31T00:00:00Z")
                    },
                ),
            )
        }
        val replacedPromoQuote = PaymentTestFixtures.mutateJson("price-quote-200-voucher.json") { obj ->
            JsonObject(obj + ("discountApplied" to JsonPrimitive("VOUCHER_ONLY")))
        }
        val combinedQuote = PaymentTestFixtures.mutateJson("price-quote-200-voucher.json") { obj ->
            JsonObject(
                obj + mapOf(
                    "discountApplied" to JsonPrimitive("COMBINED"),
                    "finalPrice" to JsonPrimitive(140_000L),
                    "promotion" to buildJsonObject {
                        put("id", "00000000-0000-7000-8000-000000000011")
                        put("name", "Giảm tự động")
                        put("discountType", "AMOUNT")
                        put("discountValue", 20_000L)
                        put("discountVnd", 20_000L)
                        put("endsAt", "2026-12-31T00:00:00Z")
                    },
                ),
            )
        }
        val notBetterQuote = PaymentTestFixtures.mutateJson("price-quote-200.json") { obj ->
            JsonObject(
                obj + mapOf(
                    "finalPrice" to JsonPrimitive(180_000L),
                    "voucherNotBetter" to JsonPrimitive(true),
                ),
            )
        }

        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/public/storefront/products/${PaymentTestFixtures.EXAM_CODE}", 200, "storefront-products-detail-200-exam.json")
            respondSequence(
                "POST",
                "/api/v1/me/price-quote",
                { ContractFakeServer.jsonResponse(200, promoQuote) },
                { ContractFakeServer.jsonResponse(200, replacedPromoQuote) },
                { ContractFakeServer.jsonResponse(200, combinedQuote) },
                { ContractFakeServer.jsonResponse(200, notBetterQuote) },
                { ContractFakeServer.apiFixtureResponse(200, "price-quote-200-voucher.json") },
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
            mountCheckout(vm)
            composeRule.waitUntilMainIdle {
                vm.state.value.isPayButtonEnabled &&
                    composeRule.onAllNodesWithTag(PaymentTestTags.CHECKOUT_VOUCHER_APPLY).fetchSemanticsNodes().isNotEmpty()
            }

            // 1. Trims whitespace and shows appliedReplacedPromo
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_VOUCHER_INPUT).performScrollTo().performTextInput("  EXAM-DEMO-03  ")
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_VOUCHER_APPLY).performScrollTo().performClick()
            composeRule.waitUntilMainIdle {
                harness.server.requestCount("POST", "/api/v1/me/price-quote") == 2 &&
                    !vm.state.value.voucher.submitting
            }
            val sentVoucher = Json.parseToJsonElement(
                harness.server.requests("POST", "/api/v1/me/price-quote")[1].body.readUtf8(),
            ).jsonObject["voucherCode"]!!.toString().trim('"')
            assertEquals("EXAM-DEMO-03", sentVoucher)
            composeRule.onNodeWithText("Mã này giảm nhiều hơn nên đã thay thế ưu đãi tự động.", substring = true).performScrollTo().assertIsDisplayed()

            // 2. Combined
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_VOUCHER_APPLY).performScrollTo().performClick()
            composeRule.waitUntilMainIdle {
                harness.server.requestCount("POST", "/api/v1/me/price-quote") == 3 &&
                    !vm.state.value.voucher.submitting
            }
            composeRule.onNodeWithText("cùng ưu đãi tự động.", substring = true).performScrollTo().assertIsDisplayed()

            // 3. Not better
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_VOUCHER_INPUT).performScrollTo().performTextClearance()
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_VOUCHER_INPUT).performTextInput("SMALL5")
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_VOUCHER_APPLY).performScrollTo().performClick()
            composeRule.waitUntilMainIdle {
                harness.server.requestCount("POST", "/api/v1/me/price-quote") == 4 &&
                    !vm.state.value.voucher.submitting
            }
            composeRule.onNodeWithText("Mã SMALL5 không giảm nhiều hơn ưu đãi tự động hiện tại", substring = true).performScrollTo().assertIsDisplayed()

            // 4. Plain applied then Remove
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_VOUCHER_APPLY).performScrollTo().performClick()
            composeRule.waitUntilMainIdle {
                harness.server.requestCount("POST", "/api/v1/me/price-quote") == 5 &&
                    !vm.state.value.voucher.submitting
            }
            composeRule.onNodeWithText("Đã áp dụng mã EXAM-DEMO-03 (-40.000 đ).").performScrollTo().assertIsDisplayed()

            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_VOUCHER_REMOVE).performScrollTo().performClick()
            composeRule.waitUntilMainIdle {
                harness.server.requestCount("POST", "/api/v1/me/price-quote") == 6 &&
                    !vm.state.value.voucher.submitting
            }
            assertEquals("", vm.state.value.voucher.input)
            composeRule.onNodeWithText("Mã giảm giá (EXAM-DEMO-03)").assertDoesNotExist()
        } finally {
            scope.cancel()
            harness.close()
        }
    }

    @Test
    fun allErrorCodesBundleVariantRateLimitAndUnknown() {
        val examCases = listOf(
            Triple(422, "price-quote-422-voucher-invalid.json", "Mã giảm giá không tồn tại hoặc không hợp lệ."),
            Triple(422, "price-quote-422-voucher-expired.json", "Mã giảm giá đã hết hạn sử dụng."),
            Triple(422, "price-quote-422-voucher-exhausted.json", "Mã giảm giá đã hết lượt sử dụng."),
            Triple(422, "price-quote-422-voucher-user-limit.json", "Bạn đã dùng hết số lượt cho phép của mã giảm giá này."),
            Triple(422, "price-quote-422-voucher-not-applicable.json", "Mã giảm giá không áp dụng cho bài thi này."),
            Triple(422, "price-quote-422-voucher-not-eligible.json", "Tài khoản của bạn chưa đủ điều kiện sử dụng mã này."),
            Triple(429, "price-quote-429-rate-limited.json", "Bạn kiểm tra mã quá nhanh. Vui lòng thử lại sau ít phút."),
        )

        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/public/storefront/products/${PaymentTestFixtures.EXAM_CODE}", 200, "storefront-products-detail-200-exam.json")
            respondJson("POST", "/api/v1/me/exam-access/query", 200, PaymentTestFixtures.examAccessBody(PaymentTestFixtures.EXAM_CODE, "NONE"))
            respondSequence(
                "POST",
                "/api/v1/me/price-quote",
                { ContractFakeServer.apiFixtureResponse(200, "price-quote-200.json") },
                { ContractFakeServer.apiFixtureResponse(422, "price-quote-422-voucher-invalid.json") },
                { ContractFakeServer.apiFixtureResponse(422, "price-quote-422-voucher-expired.json") },
                { ContractFakeServer.apiFixtureResponse(422, "price-quote-422-voucher-exhausted.json") },
                { ContractFakeServer.apiFixtureResponse(422, "price-quote-422-voucher-user-limit.json") },
                { ContractFakeServer.apiFixtureResponse(422, "price-quote-422-voucher-not-applicable.json") },
                { ContractFakeServer.apiFixtureResponse(422, "price-quote-422-voucher-not-eligible.json") },
                { ContractFakeServer.apiFixtureResponse(429, "price-quote-429-rate-limited.json") },
                { MockResponse().setResponseCode(422).setBody("""{"code":"VOUCHER_MYSTERY","message":"unknown"}""") },
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
            vm.loadInitial()
            mountCheckout(vm)
            composeRule.waitUntilMainIdle {
                vm.state.value.isPayButtonEnabled &&
                    composeRule.onAllNodesWithTag(PaymentTestTags.CHECKOUT_VOUCHER_APPLY).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_VOUCHER_INPUT).performScrollTo().performTextInput("BADCODE")

            var expectedCalls = 1
            for ((_, _, expectedMessage) in examCases) {
                expectedCalls++
                composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_VOUCHER_APPLY).performScrollTo().performClick()
                val target = expectedCalls
                composeRule.waitUntilMainIdle {
                    harness.server.requestCount("POST", "/api/v1/me/price-quote") == target &&
                        !vm.state.value.voucher.submitting
                }
                composeRule.onNodeWithText(expectedMessage).performScrollTo().assertIsDisplayed()
                assertEquals("BADCODE", vm.state.value.voucher.input)
                assertEquals(200_000L, vm.state.value.activeQuote?.finalPrice)
            }

            // Unknown error code -> generic
            expectedCalls++
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_VOUCHER_APPLY).performScrollTo().performClick()
            val unknownTarget = expectedCalls
            composeRule.waitUntilMainIdle {
                harness.server.requestCount("POST", "/api/v1/me/price-quote") == unknownTarget &&
                    !vm.state.value.voucher.submitting
            }
            composeRule.onNodeWithText("Không thể áp dụng mã giảm giá. Vui lòng thử lại.").performScrollTo().assertIsDisplayed()

            // Typing clears error
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_VOUCHER_INPUT).performScrollTo().performTextInput("X")
            composeRule.onNodeWithText("Không thể áp dụng mã giảm giá. Vui lòng thử lại.").assertDoesNotExist()
        } finally {
            scope.cancel()
            harness.close()
        }

        // Bundle VOUCHER_NOT_APPLICABLE uses VOUCHER_NOT_APPLICABLE_BUNDLE
        val bundleHarness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/public/storefront/products/${PaymentTestFixtures.BUNDLE_CODE}", 200, "storefront-products-detail-200-bundle.json")
            respondJson("POST", "/api/v1/me/exam-access/query", 200, PaymentTestFixtures.examAccessBody(PaymentTestFixtures.BUNDLE_CODE, "NONE"))
            respondSequence(
                "POST",
                "/api/v1/me/price-quote",
                { ContractFakeServer.apiFixtureResponse(200, "price-quote-200.json") },
                { ContractFakeServer.apiFixtureResponse(422, "price-quote-422-voucher-not-applicable.json") },
            )
        }
        val bundleScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val bundleVm = CheckoutViewModel(
            code = PaymentTestFixtures.BUNDLE_CODE,
            gateway = PaymentTestFixtures.createGateway(bundleHarness),
            pendingOrderStore = PendingPaymentOrderStore { bundleHarness.accountScopedStoreProvider.current() },
            apiBaseUrl = bundleHarness.server.url("/"),
            scope = bundleScope,
        )
        try {
            bundleVm.loadInitial()
            mountCheckout(bundleVm)
            composeRule.waitUntilMainIdle {
                bundleVm.state.value.isPayButtonEnabled &&
                    composeRule.onAllNodesWithTag(PaymentTestTags.CHECKOUT_VOUCHER_APPLY).fetchSemanticsNodes().isNotEmpty()
            }
            bundleVm.onVoucherInputChanged("SINGLEONLY")
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_VOUCHER_APPLY).performScrollTo().performClick()
            composeRule.waitUntilMainIdle {
                bundleHarness.server.requestCount("POST", "/api/v1/me/price-quote") == 2 &&
                    !bundleVm.state.value.voucher.submitting
            }
            composeRule.onNodeWithText("Mã này chỉ áp cho một số bài thi, không áp cho bộ đề.").performScrollTo().assertIsDisplayed()
        } finally {
            bundleScope.cancel()
            bundleHarness.close()
        }
    }

    @Test
    fun doubleTapSendsOneQuote() {
        val gate = CountDownLatch(1)
        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/public/storefront/products/${PaymentTestFixtures.EXAM_CODE}", 200, "storefront-products-detail-200-exam.json")
            respondJson("POST", "/api/v1/me/exam-access/query", 200, PaymentTestFixtures.examAccessBody(PaymentTestFixtures.EXAM_CODE, "NONE"))
            respondDynamicSequence(
                "POST",
                "/api/v1/me/price-quote",
                listOf(
                    { ContractFakeServer.apiFixtureResponse(200, "price-quote-200.json") },
                    {
                        gate.await(5, TimeUnit.SECONDS)
                        ContractFakeServer.apiFixtureResponse(200, "price-quote-200-voucher.json")
                    },
                ),
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
            vm.loadInitial()
            mountCheckout(vm)
            composeRule.waitUntilMainIdle {
                vm.state.value.isPayButtonEnabled &&
                    composeRule.onAllNodesWithTag(PaymentTestTags.CHECKOUT_VOUCHER_APPLY).fetchSemanticsNodes().isNotEmpty()
            }
            vm.onVoucherInputChanged("EXAM-DEMO-03")
            vm.applyVoucher()
            vm.applyVoucher()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_VOUCHER_INPUT).performScrollTo().assertIsNotEnabled()
            gate.countDown()
            composeRule.waitUntilMainIdle { !vm.state.value.voucher.submitting }
            assertEquals(2, harness.server.requestCount("POST", "/api/v1/me/price-quote"))
        } finally {
            gate.countDown()
            scope.cancel()
            harness.close()
        }
    }

    private fun mountCheckout(vm: CheckoutViewModel) {
        slotHost.set {
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
    }
}
