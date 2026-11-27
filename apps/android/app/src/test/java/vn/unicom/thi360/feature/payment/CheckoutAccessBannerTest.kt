package vn.unicom.thi360.feature.payment

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
import vn.unicom.thi360.feature.payment.domain.PendingPaymentOrderStore
import vn.unicom.thi360.feature.payment.ui.CheckoutScreen
import vn.unicom.thi360.feature.payment.ui.PaymentTestTags
import vn.unicom.thi360.feature.payment.viewmodel.CheckoutViewModel
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class CheckoutAccessBannerTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun checkingBlocksPayAndOpensPaymentResultOrOrders() {
        val host = ComposeSlotHost(composeRule)
        var openedPaymentResult: String? = null
        var openedOrders = false

        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/public/storefront/products/${PaymentTestFixtures.EXAM_CODE}", 200, "storefront-products-detail-200-exam.json")
            respondApiFixture("POST", "/api/v1/me/price-quote", 200, "price-quote-200.json")
            respondJson(
                "POST",
                "/api/v1/me/exam-access/query",
                200,
                PaymentTestFixtures.examAccessBody(
                    code = PaymentTestFixtures.EXAM_CODE,
                    state = "CHECKING",
                    hasPendingFulfillment = true,
                    pendingOrderCode = "DH261126000099",
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
            host.set {
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                    CheckoutScreen(
                        viewModel = vm,
                        paymentBrowser = RecordingPaymentBrowser(),
                        supportContact = SupportContact(),
                        onBack = {},
                        onOpenCatalog = {},
                        onOpenProduct = {},
                        onOpenPaymentResult = { openedPaymentResult = it },
                        onOpenOrders = { openedOrders = true },
                        onOpenMyExams = {},
                        onOpenPrepare = {},
                        onOpenAttempt = {},
                        onSwitchAccount = {},
                    )
                }
            }
            composeRule.waitUntilMainIdle {
                vm.state.value.activeQuote != null &&
                    composeRule.onAllNodesWithTag(PaymentTestTags.CHECKOUT_VIEW_STATUS_BUTTON).fetchSemanticsNodes().isNotEmpty() &&
                    composeRule.onAllNodesWithTag(PaymentTestTags.CHECKOUT_PAY_BUTTON).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Đang kiểm tra đơn DH261126000099").assertIsDisplayed()
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_PAY_BUTTON).performScrollTo().assertIsNotEnabled()
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_VOUCHER_APPLY).performScrollTo().assertIsNotEnabled()
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_VIEW_STATUS_BUTTON).performScrollTo().performClick()
            assertEquals("DH261126000099", openedPaymentResult)
        } finally {
            scope.cancel()
            harness.close()
        }

        // Without pendingOrderCode -> opens orders
        val noCodeHarness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/public/storefront/products/${PaymentTestFixtures.EXAM_CODE}", 200, "storefront-products-detail-200-exam.json")
            respondApiFixture("POST", "/api/v1/me/price-quote", 200, "price-quote-200.json")
            respondJson(
                "POST",
                "/api/v1/me/exam-access/query",
                200,
                PaymentTestFixtures.examAccessBody(
                    code = PaymentTestFixtures.EXAM_CODE,
                    state = "CHECKING",
                    hasPendingFulfillment = true,
                    pendingOrderCode = null,
                ),
            )
        }
        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val vm2 = CheckoutViewModel(
            code = PaymentTestFixtures.EXAM_CODE,
            gateway = PaymentTestFixtures.createGateway(noCodeHarness),
            pendingOrderStore = PendingPaymentOrderStore { noCodeHarness.accountScopedStoreProvider.current() },
            apiBaseUrl = noCodeHarness.server.url("/"),
            scope = scope2,
        )
        try {
            vm2.loadInitial()
            host.set {
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                    CheckoutScreen(
                        viewModel = vm2,
                        paymentBrowser = RecordingPaymentBrowser(),
                        supportContact = SupportContact(),
                        onBack = {},
                        onOpenCatalog = {},
                        onOpenProduct = {},
                        onOpenPaymentResult = {},
                        onOpenOrders = { openedOrders = true },
                        onOpenMyExams = {},
                        onOpenPrepare = {},
                        onOpenAttempt = {},
                        onSwitchAccount = {},
                    )
                }
            }
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.CHECKOUT_VIEW_STATUS_BUTTON).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_VIEW_STATUS_BUTTON).performClick()
            assertTrue(openedOrders)
        } finally {
            scope2.cancel()
            noCodeHarness.close()
        }
    }

    @Test
    fun canStartAndInProgressOpenPrepareAndAttemptAndKeepPayEnabled() {
        val host = ComposeSlotHost(composeRule)
        var openedPrepare: String? = null
        var openedAttempt: String? = null

        val canStartHarness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/public/storefront/products/${PaymentTestFixtures.EXAM_CODE}", 200, "storefront-products-detail-200-exam.json")
            respondApiFixture("POST", "/api/v1/me/price-quote", 200, "price-quote-200.json")
            respondJson(
                "POST",
                "/api/v1/me/exam-access/query",
                200,
                PaymentTestFixtures.examAccessBody(
                    code = PaymentTestFixtures.EXAM_CODE,
                    state = "CAN_START",
                    attemptsLeft = 3,
                    entitlementId = "00000000-0000-7000-8000-000000000002",
                ),
            )
        }
        val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val vm1 = CheckoutViewModel(
            code = PaymentTestFixtures.EXAM_CODE,
            gateway = PaymentTestFixtures.createGateway(canStartHarness),
            pendingOrderStore = PendingPaymentOrderStore { canStartHarness.accountScopedStoreProvider.current() },
            apiBaseUrl = canStartHarness.server.url("/"),
            scope = scope1,
        )
        try {
            vm1.loadInitial()
            host.set {
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                    CheckoutScreen(
                        viewModel = vm1,
                        paymentBrowser = RecordingPaymentBrowser(),
                        supportContact = SupportContact(),
                        onBack = {},
                        onOpenCatalog = {},
                        onOpenProduct = {},
                        onOpenPaymentResult = {},
                        onOpenOrders = {},
                        onOpenMyExams = {},
                        onOpenPrepare = { openedPrepare = it },
                        onOpenAttempt = { openedAttempt = it },
                        onSwitchAccount = {},
                    )
                }
            }
            composeRule.waitUntilMainIdle {
                vm1.state.value.isPayButtonEnabled &&
                    composeRule.onAllNodesWithTag(PaymentTestTags.CHECKOUT_ENTER_EXAM_BUTTON).fetchSemanticsNodes().isNotEmpty() &&
                    composeRule.onAllNodesWithTag(PaymentTestTags.CHECKOUT_PAY_BUTTON).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Bạn đang có 3 lượt với bài thi này").assertIsDisplayed()
            composeRule.onNodeWithText("Vào bài thi").performClick()
            assertEquals("00000000-0000-7000-8000-000000000002", openedPrepare)
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_BUY_MORE_BUTTON).performClick()
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_PAY_BUTTON).performScrollTo().assertIsEnabled()
        } finally {
            scope1.cancel()
            canStartHarness.close()
        }

        val inProgressHarness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/public/storefront/products/${PaymentTestFixtures.EXAM_CODE}", 200, "storefront-products-detail-200-exam.json")
            respondApiFixture("POST", "/api/v1/me/price-quote", 200, "price-quote-200.json")
            respondJson(
                "POST",
                "/api/v1/me/exam-access/query",
                200,
                PaymentTestFixtures.examAccessBody(
                    code = PaymentTestFixtures.EXAM_CODE,
                    state = "IN_PROGRESS",
                    attemptsLeft = 1,
                    entitlementId = "00000000-0000-7000-8000-000000000004",
                    activeAttemptId = "00000000-0000-7000-8000-000000000005",
                ),
            )
        }
        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val vm2 = CheckoutViewModel(
            code = PaymentTestFixtures.EXAM_CODE,
            gateway = PaymentTestFixtures.createGateway(inProgressHarness),
            pendingOrderStore = PendingPaymentOrderStore { inProgressHarness.accountScopedStoreProvider.current() },
            apiBaseUrl = inProgressHarness.server.url("/"),
            scope = scope2,
        )
        try {
            vm2.loadInitial()
            host.set {
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                    CheckoutScreen(
                        viewModel = vm2,
                        paymentBrowser = RecordingPaymentBrowser(),
                        supportContact = SupportContact(),
                        onBack = {},
                        onOpenCatalog = {},
                        onOpenProduct = {},
                        onOpenPaymentResult = {},
                        onOpenOrders = {},
                        onOpenMyExams = {},
                        onOpenPrepare = { openedPrepare = it },
                        onOpenAttempt = { openedAttempt = it },
                        onSwitchAccount = {},
                    )
                }
            }
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.CHECKOUT_ENTER_EXAM_BUTTON).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Tiếp tục").performClick()
            assertEquals("00000000-0000-7000-8000-000000000005", openedAttempt)
        } finally {
            scope2.cancel()
            inProgressHarness.close()
        }
    }

    @Test
    fun bundleShowsExistingAndPendingExamLines() {
        val bundleItemsAccess = listOf(
            buildJsonObject {
                put("examId", "00000000-0000-7000-8000-000000000010")
                put("code", "EXAM-DEMO-02")
                put("name", "Toán tư duy")
                put("state", "CAN_START")
                put("attemptsLeft", 2)
                put("hasPendingFulfillment", false)
            },
            buildJsonObject {
                put("examId", "00000000-0000-7000-8000-000000000011")
                put("code", "EXAM-DEMO-03")
                put("name", "Ngôn ngữ")
                put("state", "CHECKING")
                put("attemptsLeft", 0)
                put("hasPendingFulfillment", true)
                put("pendingOrderCode", "DH261126000044")
            },
            buildJsonObject {
                put("examId", "00000000-0000-7000-8000-000000000012")
                put("code", "EXAM-DEMO-04")
                put("name", "Khoa học")
                put("state", "CHECKING")
                put("attemptsLeft", 0)
                put("hasPendingFulfillment", true)
            },
        )
        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/public/storefront/products/${PaymentTestFixtures.BUNDLE_CODE}", 200, "storefront-products-detail-200-bundle.json")
            respondApiFixture("POST", "/api/v1/me/price-quote", 200, "price-quote-200.json")
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
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.CHECKOUT_ACCESS_BANNER).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Bạn đã có 2 lượt bài Toán tư duy; mua bộ sẽ cộng thêm lượt", substring = true).assertIsDisplayed()
            composeRule.onNodeWithText("Đơn DH261126000044 cho bài Ngôn ngữ đang được kiểm tra.", substring = true).assertIsDisplayed()
            composeRule.onNodeWithText("Có đơn đang được kiểm tra cho bài Khoa học.", substring = true).assertIsDisplayed()
        } finally {
            scope.cancel()
            harness.close()
        }
    }

    @Test
    fun accessErrorOrForbiddenLocksPayUntilRetrySucceeds() {
        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/public/storefront/products/${PaymentTestFixtures.EXAM_CODE}", 200, "storefront-products-detail-200-exam.json")
            respondApiFixture("POST", "/api/v1/me/price-quote", 200, "price-quote-200.json")
            respondSequence(
                "POST",
                "/api/v1/me/exam-access/query",
                { MockResponse().setResponseCode(403).setBody("""{"code":"FORBIDDEN","message":"forbidden"}""") },
                { ContractFakeServer.jsonResponse(200, PaymentTestFixtures.examAccessBody(PaymentTestFixtures.EXAM_CODE, "NONE")) },
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
                vm.state.value.activeQuote != null &&
                    composeRule.onAllNodesWithTag(PaymentTestTags.CHECKOUT_ACCESS_RETRY_BUTTON).fetchSemanticsNodes().isNotEmpty() &&
                    composeRule.onAllNodesWithTag(PaymentTestTags.CHECKOUT_PAY_BUTTON).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Chưa kiểm tra được quyền sử dụng").assertIsDisplayed()
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_PAY_BUTTON).performScrollTo().assertIsNotEnabled()
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_ACCESS_RETRY_BUTTON).performScrollTo().performClick()
            composeRule.waitUntilMainIdle { vm.state.value.isPayButtonEnabled }
            composeRule.onNodeWithTag(PaymentTestTags.CHECKOUT_PAY_BUTTON).performScrollTo().assertIsEnabled()
        } finally {
            scope.cancel()
            harness.close()
        }
    }
}
