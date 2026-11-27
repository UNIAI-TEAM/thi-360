package vn.unicom.thi360.feature.payment

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.feature.payment.domain.PendingPaymentOrderStore
import vn.unicom.thi360.feature.payment.domain.SeenPrice
import vn.unicom.thi360.feature.payment.ui.CheckoutScreen
import vn.unicom.thi360.feature.payment.ui.PaymentTestTags
import vn.unicom.thi360.feature.payment.viewmodel.CheckoutViewModel
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class CheckoutScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsAccountProductRefund() {
        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/public/storefront/products/${PaymentTestFixtures.EXAM_CODE}", 200, "storefront-products-detail-200-exam.json")
            respondJson(
                "POST",
                "/api/v1/me/price-quote",
                200,
                PaymentTestFixtures.mutateJson("price-quote-200.json") { obj ->
                    JsonObject(obj + ("versionNo" to JsonPrimitive(3)))
                },
            )
            respondJson("POST", "/api/v1/me/exam-access/query", 200, PaymentTestFixtures.examAccessBody(PaymentTestFixtures.EXAM_CODE, "NONE"))
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val browser = RecordingPaymentBrowser()
        val vm = CheckoutViewModel(
            code = PaymentTestFixtures.EXAM_CODE,
            seenPrice = SeenPrice(versionNo = 3, price = 200_000L),
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
                        paymentBrowser = browser,
                        supportContact = SupportContact(phone = "1900 1234", email = "hotro@thi360.vn"),
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
            composeRule.waitUntilMainIdle {
                vm.state.value.isPayButtonEnabled &&
                    composeRule.onAllNodesWithTag(PaymentTestTags.CHECKOUT_PAY_BUTTON).fetchSemanticsNodes().isNotEmpty()
            }

            composeRule.onNodeWithText("Xác nhận mua").assertIsDisplayed()
            composeRule.onNodeWithText("Tài khoản nhận bài thi").assertIsDisplayed()
            composeRule.onNodeWithText("Nguyễn Văn An").assertIsDisplayed()
            composeRule.onNodeWithText("student.demo").assertIsDisplayed()
            composeRule.onNodeWithText("Đề thi HSA tổng hợp DEMO").assertIsDisplayed()
            composeRule.onNodeWithText("Phiên bản v3").assertIsDisplayed()
            composeRule.onNodeWithText("Chính sách hoàn tiền").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("1900 1234 · hotro@thi360.vn", substring = true).performScrollTo().assertIsDisplayed()
        } finally {
            scope.cancel()
            harness.close()
        }
    }

    @Test
    fun bundleListsExams() {
        val bundleItemsAccess = listOf(
            buildJsonObject {
                put("examId", "00000000-0000-7000-8000-000000000010")
                put("code", "EXAM-DEMO-02")
                put("name", "Đề thi thành phần 1")
                put("state", "CAN_START")
                put("attemptsLeft", 2)
                put("hasPendingFulfillment", false)
            },
        )
        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/public/storefront/products/${PaymentTestFixtures.BUNDLE_CODE}", 200, "storefront-products-detail-200-bundle.json")
            respondJson(
                "POST",
                "/api/v1/me/price-quote",
                200,
                PaymentTestFixtures.mutateJson("price-quote-200.json") { obj ->
                    JsonObject(
                        obj + mapOf(
                            "productCode" to JsonPrimitive(PaymentTestFixtures.BUNDLE_CODE),
                            "listPrice" to JsonPrimitive(320_000L),
                            "finalPrice" to JsonPrimitive(320_000L),
                            "attemptsGranted" to JsonPrimitive(4),
                        ),
                    )
                },
            )
            respondJson(
                "POST",
                "/api/v1/me/exam-access/query",
                200,
                PaymentTestFixtures.examAccessBody(
                    code = PaymentTestFixtures.BUNDLE_CODE,
                    state = "NONE",
                    bundleItems = bundleItemsAccess,
                ),
            )
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val vm = CheckoutViewModel(
            code = PaymentTestFixtures.BUNDLE_CODE,
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
            composeRule.waitUntilMainIdle {
                vm.state.value.access is LoadState.Loaded &&
                    vm.state.value.isPayButtonEnabled &&
                    composeRule.onAllNodesWithTag(PaymentTestTags.CHECKOUT_PAY_BUTTON).fetchSemanticsNodes().isNotEmpty()
            }

            composeRule.onNodeWithText("2 bài thi trong bộ", substring = true).assertIsDisplayed()
            composeRule.onNodeWithText("Còn 2 lượt").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Bài đã có quyền thì lượt mua thêm cộng vào lượt đang có.", substring = true)
                .performScrollTo()
                .assertIsDisplayed()
            composeRule.onNodeWithText("Thông tin liên hệ đang được cập nhật.", substring = true)
                .performScrollTo()
                .assertIsDisplayed()
        } finally {
            scope.cancel()
            harness.close()
        }
    }

    @Test
    fun loadingHasNoPayButton() {
        val gate = CountDownLatch(1)
        val harness = PaymentTestFixtures.createHarness {
            respondDynamic("GET", "/api/v1/public/storefront/products/${PaymentTestFixtures.EXAM_CODE}") {
                gate.await(5, TimeUnit.SECONDS)
                ContractFakeServer.apiFixtureResponse(200, "storefront-products-detail-200-exam.json")
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
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_SKELETON).assertIsDisplayed()
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_PAY_BUTTON).assertDoesNotExist()
        } finally {
            gate.countDown()
            scope.cancel()
            harness.close()
        }
    }

    @Test
    fun productErrors() {
        val host = ComposeSlotHost(composeRule)
        assertProduct404(host)
        assertProduct410FromProduct(host)
        assertProduct410FromQuote(host)
        assertProduct410FromCreateOrder(host)
        assertProduct409WithoutProduct(host)
        assertProduct409FromQuoteKeepsProductAndLocksPay(host)
        assertProduct409FromCreateKeepsProductAndLocksPay(host)
        assertProduct5xxRetries(host)
    }

    private fun assertProduct404(host: ComposeSlotHost) {
        var openedCatalog = false
        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/public/storefront/products/${PaymentTestFixtures.EXAM_CODE}", 404, "storefront-products-detail-404-not-found.json")
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
                        onOpenCatalog = { openedCatalog = true },
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
                composeRule.onAllNodesWithTag(PaymentTestTags.CHECKOUT_NOT_FOUND_STATE).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Không tìm thấy bài thi").assertIsDisplayed()
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_BACK_TO_CATALOG_BUTTON).performClick()
            assertTrue(openedCatalog)
        } finally {
            scope.cancel()
            harness.close()
        }
    }

    private fun assertProduct410FromProduct(host: ComposeSlotHost) {
        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/public/storefront/products/${PaymentTestFixtures.EXAM_CODE}", 410, "storefront-products-detail-410-product-discontinued.json")
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
                composeRule.onAllNodesWithTag(PaymentTestTags.CHECKOUT_GONE_STATE).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Bài thi này hiện đã ngừng bán").assertIsDisplayed()
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_PAY_BUTTON).assertDoesNotExist()
        } finally {
            scope.cancel()
            harness.close()
        }
    }

    private fun assertProduct410FromQuote(host: ComposeSlotHost) {
        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/public/storefront/products/${PaymentTestFixtures.EXAM_CODE}", 200, "storefront-products-detail-200-exam.json")
            respondApiFixture("POST", "/api/v1/me/price-quote", 410, "price-quote-410-product-discontinued.json")
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
                composeRule.onAllNodesWithTag(PaymentTestTags.CHECKOUT_GONE_STATE).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_PAY_BUTTON).assertDoesNotExist()
        } finally {
            scope.cancel()
            harness.close()
        }
    }

    private fun assertProduct410FromCreateOrder(host: ComposeSlotHost) {
        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/public/storefront/products/${PaymentTestFixtures.EXAM_CODE}", 200, "storefront-products-detail-200-exam.json")
            respondApiFixture("POST", "/api/v1/me/price-quote", 200, "price-quote-200.json")
            respondJson("POST", "/api/v1/me/exam-access/query", 200, PaymentTestFixtures.examAccessBody(PaymentTestFixtures.EXAM_CODE, "NONE"))
            respondApiFixture("POST", "/api/v1/me/orders", 410, "orders-create-410-product-discontinued.json")
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
            composeRule.waitUntilMainIdle {
                vm.state.value.isPayButtonEnabled &&
                    composeRule.onAllNodesWithTag(PaymentTestTags.CHECKOUT_PAY_BUTTON).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_PAY_BUTTON).performScrollTo().performClick()
            composeRule.waitUntilMainIdle {
                composeRule.onAllNodesWithTag(PaymentTestTags.CHECKOUT_GONE_STATE).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_PAY_BUTTON).assertDoesNotExist()
        } finally {
            scope.cancel()
            harness.close()
        }
    }

    private fun assertProduct409WithoutProduct(host: ComposeSlotHost) {
        var openedProduct: String? = null
        var openedCatalog = false
        val harness = PaymentTestFixtures.createHarness {
            respondJson(
                "GET",
                "/api/v1/public/storefront/products/${PaymentTestFixtures.BUNDLE_CODE}",
                409,
                PaymentTestFixtures.productUnavailableWithoutProductBody(),
            )
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val vm = CheckoutViewModel(
            code = PaymentTestFixtures.BUNDLE_CODE,
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
                        onOpenCatalog = { openedCatalog = true },
                        onOpenProduct = { openedProduct = it },
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
                composeRule.onAllNodesWithTag(PaymentTestTags.CHECKOUT_UNAVAILABLE_BANNER).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Bộ đề tạm thời chưa bán được.").assertIsDisplayed()
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_BACK_TO_BUNDLE_BUTTON).performClick()
            assertEquals(PaymentTestFixtures.BUNDLE_CODE, openedProduct)
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_BACK_TO_CATALOG_BUTTON).performClick()
            assertTrue(openedCatalog)
        } finally {
            scope.cancel()
            harness.close()
        }
    }

    private fun assertProduct409FromQuoteKeepsProductAndLocksPay(host: ComposeSlotHost) {
        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/public/storefront/products/${PaymentTestFixtures.BUNDLE_CODE}", 200, "storefront-products-detail-200-bundle.json")
            respondApiFixture("POST", "/api/v1/me/price-quote", 409, "orders-create-409-product-unavailable.json")
            respondJson("POST", "/api/v1/me/exam-access/query", 200, PaymentTestFixtures.examAccessBody(PaymentTestFixtures.BUNDLE_CODE, "NONE"))
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val vm = CheckoutViewModel(
            code = PaymentTestFixtures.BUNDLE_CODE,
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
                composeRule.onAllNodesWithTag(PaymentTestTags.CHECKOUT_UNAVAILABLE_BANNER).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Bộ đề luyện thi HSA DEMO").assertIsDisplayed()
            composeRule.onNodeWithText("Một bài trong bộ vừa ngừng bán. Chưa có khoản nào bị trừ.").assertIsDisplayed()
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_PAY_BUTTON).performScrollTo().assertIsNotEnabled()
        } finally {
            scope.cancel()
            harness.close()
        }
    }

    private fun assertProduct409FromCreateKeepsProductAndLocksPay(host: ComposeSlotHost) {
        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/public/storefront/products/${PaymentTestFixtures.BUNDLE_CODE}", 200, "storefront-products-detail-200-bundle.json")
            respondApiFixture("POST", "/api/v1/me/price-quote", 200, "price-quote-200.json")
            respondJson("POST", "/api/v1/me/exam-access/query", 200, PaymentTestFixtures.examAccessBody(PaymentTestFixtures.BUNDLE_CODE, "NONE"))
            respondApiFixture("POST", "/api/v1/me/orders", 409, "orders-create-409-product-unavailable.json")
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val vm = CheckoutViewModel(
            code = PaymentTestFixtures.BUNDLE_CODE,
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
            composeRule.waitUntilMainIdle {
                vm.state.value.isPayButtonEnabled &&
                    composeRule.onAllNodesWithTag(PaymentTestTags.CHECKOUT_PAY_BUTTON).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_PAY_BUTTON).performScrollTo().performClick()
            composeRule.waitUntilMainIdle {
                composeRule.onAllNodesWithTag(PaymentTestTags.CHECKOUT_UNAVAILABLE_BANNER).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Một bài trong bộ vừa ngừng bán. Chưa có khoản nào bị trừ.").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_PAY_BUTTON).performScrollTo().assertIsNotEnabled()
        } finally {
            scope.cancel()
            harness.close()
        }
    }

    private fun assertProduct5xxRetries(host: ComposeSlotHost) {
        val harness = PaymentTestFixtures.createHarness {
            respondSequence(
                "GET",
                "/api/v1/public/storefront/products/${PaymentTestFixtures.EXAM_CODE}",
                { MockResponse().setResponseCode(500).setBody("""{"code":"INTERNAL_ERROR","message":"boom"}""") },
                { ContractFakeServer.apiFixtureResponse(200, "storefront-products-detail-200-exam.json") },
            )
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
                composeRule.onAllNodesWithTag(PaymentTestTags.CHECKOUT_ERROR_STATE).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Không thể tải thông tin thanh toán").assertIsDisplayed()
            composeRule.onNodeWithText("Thử lại").performClick()
            composeRule.waitUntilMainIdle {
                vm.state.value.isPayButtonEnabled &&
                    composeRule.onAllNodesWithTag(PaymentTestTags.CHECKOUT_PAY_BUTTON).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Đề thi HSA tổng hợp DEMO").assertIsDisplayed()
        } finally {
            scope.cancel()
            harness.close()
        }
    }
}
