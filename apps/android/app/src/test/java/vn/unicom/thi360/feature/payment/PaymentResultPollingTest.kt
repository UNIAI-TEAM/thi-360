package vn.unicom.thi360.feature.payment

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
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
import vn.unicom.thi360.feature.payment.domain.OrderPoller
import vn.unicom.thi360.feature.payment.domain.PaymentResultEntry
import vn.unicom.thi360.feature.payment.domain.PendingPaymentOrderStore
import vn.unicom.thi360.feature.payment.ui.PaymentResultScreen
import vn.unicom.thi360.feature.payment.ui.PaymentTestTags
import vn.unicom.thi360.feature.payment.viewmodel.PaymentResultState
import vn.unicom.thi360.feature.payment.viewmodel.PaymentResultViewModel
import vn.unicom.thi360.testing.ContractFakeServer

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class PaymentResultPollingTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class FakeLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun backgroundStopsCalls() {
        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}", 200, "orders-detail-200-paid.json")
        }
        val testScope = TestScope(StandardTestDispatcher())
        val poller = OrderPoller(
            intervalMillis = 2_000L,
            windowMillis = 60_000L,
            clockMillis = { testScope.testScheduler.currentTime },
        )
        val vm = PaymentResultViewModel(
            initialEntry = PaymentResultEntry.ByOrder(PaymentTestFixtures.ORDER_CODE),
            gateway = PaymentTestFixtures.createGateway(harness),
            pendingOrderStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() },
            apiBaseUrl = harness.server.url("/"),
            scope = testScope,
            clockMillis = { testScope.testScheduler.currentTime },
            poller = poller,
        )
        val fakeLifecycle = FakeLifecycleOwner().apply {
            registry.currentState = Lifecycle.State.RESUMED
        }
        try {
            vm.start()
            waitUntil(testScope) { vm.uiState.value.state is PaymentResultState.Checking }
            composeRule.setContent {
                CompositionLocalProvider(LocalLifecycleOwner provides fakeLifecycle) {
                    Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                        PaymentResultScreen(
                            viewModel = vm,
                            paymentBrowser = RecordingPaymentBrowser(),
                            supportContact = SupportContact(),
                            onBack = {},
                            onOpenMyExams = {},
                            onOpenOrder = {},
                            onOpenOrders = {},
                            onOpenCheckout = {},
                            onOpenCatalog = {},
                            onSwitchAccount = {},
                        )
                    }
                }
            }
            composeRule.onNodeWithText("Đang chờ xác nhận thanh toán").assertIsDisplayed()
            composeRule.onNodeWithText("Đang kiểm tra").assertIsDisplayed()

            // Move lifecycle to CREATED (backgrounded) -> ZERO API calls while in background
            composeRule.runOnIdle {
                fakeLifecycle.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            }
            val countWhenStopped = harness.server.requestCount("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}")
            testScope.advanceTimeBy(10_000L)
            testScope.runCurrent()
            assertEquals(
                "Must NOT call API while backgrounded",
                countWhenStopped,
                harness.server.requestCount("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}"),
            )

            // Resume -> immediate read!
            composeRule.runOnIdle {
                fakeLifecycle.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            }
            waitUntil(testScope) {
                harness.server.requestCount("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}") > countWhenStopped
            }
            assertTrue(harness.server.requestCount("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}") > countWhenStopped)
        } finally {
            vm.onLeaveScreen()
            harness.close()
        }
    }

    @Test
    fun pollErrorDuringAndAfterTimeoutAndCheckAgainRestartsWindow() {
        val harness = PaymentTestFixtures.createHarness {
            respondSequence(
                "GET",
                "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}",
                { ContractFakeServer.apiFixtureResponse(200, "orders-detail-200-paid.json") },
                { MockResponse().setResponseCode(503).setBody("""{"code":"SERVICE_UNAVAILABLE","message":"down"}""") },
                { ContractFakeServer.apiFixtureResponse(200, "orders-detail-200-paid.json") },
            )
            respondApiFixture("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}", 200, "orders-detail-200-paid.json")
        }
        val testScope = TestScope(StandardTestDispatcher())
        val poller = OrderPoller(
            intervalMillis = 2_000L,
            windowMillis = 60_000L,
            clockMillis = { testScope.testScheduler.currentTime },
        )
        val vm = PaymentResultViewModel(
            initialEntry = PaymentResultEntry.ByOrder(PaymentTestFixtures.ORDER_CODE),
            gateway = PaymentTestFixtures.createGateway(harness),
            pendingOrderStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() },
            apiBaseUrl = harness.server.url("/"),
            scope = testScope,
            clockMillis = { testScope.testScheduler.currentTime },
            poller = poller,
        )
        try {
            vm.start()
            waitUntil(testScope) { vm.uiState.value.state is PaymentResultState.Checking }
            composeRule.setContent {
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                    PaymentResultScreen(
                        viewModel = vm,
                        paymentBrowser = RecordingPaymentBrowser(),
                        supportContact = SupportContact(),
                        onBack = {},
                        onOpenMyExams = {},
                        onOpenOrder = {},
                        onOpenOrders = {},
                        onOpenCheckout = {},
                        onOpenCatalog = {},
                        onSwitchAccount = {},
                    )
                }
            }

            // First poll hits 503 -> keeps data & shows pollErrorNotice
            testScope.advanceTimeBy(2_000L)
            waitUntil(testScope) { (vm.uiState.value.state as? PaymentResultState.Checking)?.pollError == true }
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Tạm thời không kiểm tra được trạng thái mới nhất.", substring = true).assertIsDisplayed()

            // Advance to timeout (60s) -> shows checkingTimeoutHeading, checkingTimeoutBody, support pending
            repeat(32) {
                testScope.advanceTimeBy(2_000L)
                testScope.runCurrent()
                Thread.sleep(5)
            }
            waitUntil(testScope) { (vm.uiState.value.state as? PaymentResultState.Checking)?.timedOut == true }
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Đang kiểm tra thanh toán").assertIsDisplayed()
            composeRule.onNodeWithText("Đã quá 60 giây", substring = true).assertIsDisplayed()
            composeRule.onNodeWithText("Thông tin liên hệ đang được cập nhật.", substring = true).assertIsDisplayed()

            // Clicking "Kiểm tra lại ngay" restarts 60s window and fetches immediately
            val callsBeforeCheckAgain = harness.server.requestCount("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}")
            composeRule.onNodeWithTag(PaymentTestTags.RESULT_CHECK_AGAIN_BUTTON).performClick()
            waitUntil(testScope) {
                harness.server.requestCount("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}") > callsBeforeCheckAgain &&
                    !vm.uiState.value.isCheckingAgain
            }
            val checking = vm.uiState.value.state as PaymentResultState.Checking
            assertEquals(false, checking.timedOut)
        } finally {
            vm.onLeaveScreen()
            harness.close()
        }
    }

    private fun waitUntil(scope: TestScope, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000L
        while (!condition() && System.currentTimeMillis() < deadline) {
            scope.runCurrent()
            Thread.sleep(10)
        }
        scope.runCurrent()
        assertTrue("Condition was not met before timeout", condition())
    }
}
