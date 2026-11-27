package vn.unicom.thi360.feature.payment

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.config.AppEnvironment
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
import vn.unicom.thi360.feature.recovery.ResetLink
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.routes.PaymentRoutesConfig
import vn.unicom.thi360.shell.routes.PaymentRuntimeOverrides
import vn.unicom.thi360.shell.routes.registerPaymentHooks
import vn.unicom.thi360.testing.ContractFakeServer

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class NoLinkReturnTest {

    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun tearDown() {
        PaymentRoutesConfig.setTestOverrides(null)
    }

    @Test
    fun foregroundReturnOpensResultForPendingOrderWithin15MinutesAndNotAfter() {
        val host = ResetLink.allowedHostFromWebBaseUrl(AppEnvironment.webBaseUrl)
        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}", 200, "orders-detail-200-fulfilled.json")
        }
        val pendingStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() }
        var nowMillis = 100_000L
        pendingStore.save(PaymentTestFixtures.ORDER_CODE, openedAtMillis = 100_000L)
        PaymentRoutesConfig.setTestOverrides(
            PaymentRuntimeOverrides(
                gateway = PaymentTestFixtures.createGateway(harness),
                pendingOrderStore = pendingStore,
                apiBaseUrl = harness.server.url("/"),
                clockMillis = { nowMillis },
            ),
        )
        registerPaymentHooks(
            inboundLinkRouter = harness.inboundLinkRouter,
            sessionCleanup = harness.sessionCleanup,
            pendingOrderStoreProvider = { pendingStore },
            allowedHostProvider = { host },
            clockMillisProvider = { { nowMillis } },
        )
        try {
            composeRule.setContent {
                AppRoot(
                    preferences = AppPreferences(locale = AppLocale.VI),
                    onSelectLocale = {},
                    onSelectThemeMode = {},
                    sessionManager = harness.stack.sessionManager,
                    validateOnLaunch = false,
                    recoveryGateway = harness.recoveryGateway,
                    inboundLinkRouter = harness.inboundLinkRouter,
                    legalRepository = harness.legalRepository,
                )
            }
            composeRule.runOnIdle {
                harness.inboundLinkRouter.notifyForegroundReturn()
            }
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.RESULT_HEADING).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Đã cấp bài thi").assertIsDisplayed()
        } finally {
            harness.close()
        }
    }

    @Test
    fun coldStartWithin15MinutesOpensResultAndPast15MinutesOpensNormalHome() {
        val host = ResetLink.allowedHostFromWebBaseUrl(AppEnvironment.webBaseUrl)
        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}", 200, "orders-detail-200-paid.json")
        }
        val pendingStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() }
        var nowMillis = 1_000_000L
        pendingStore.save(PaymentTestFixtures.ORDER_CODE, openedAtMillis = 0L) // > 15 min old (16m 40s)
        PaymentRoutesConfig.setTestOverrides(
            PaymentRuntimeOverrides(
                gateway = PaymentTestFixtures.createGateway(harness),
                pendingOrderStore = pendingStore,
                apiBaseUrl = harness.server.url("/"),
                clockMillis = { nowMillis },
            ),
        )
        registerPaymentHooks(
            inboundLinkRouter = harness.inboundLinkRouter,
            sessionCleanup = harness.sessionCleanup,
            pendingOrderStoreProvider = { pendingStore },
            allowedHostProvider = { host },
            clockMillisProvider = { { nowMillis } },
        )
        try {
            harness.inboundLinkRouter.onSessionStateChanged(harness.stack.sessionManager.state.value)
            harness.inboundLinkRouter.notifyAppStarted()
            composeRule.setContent {
                AppRoot(
                    preferences = AppPreferences(locale = AppLocale.VI),
                    onSelectLocale = {},
                    onSelectThemeMode = {},
                    sessionManager = harness.stack.sessionManager,
                    validateOnLaunch = false,
                    recoveryGateway = harness.recoveryGateway,
                    inboundLinkRouter = harness.inboundLinkRouter,
                    legalRepository = harness.legalRepository,
                )
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(PaymentTestTags.RESULT_SCREEN).assertDoesNotExist()
            assertEquals(0, harness.server.requestCount("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}"))
        } finally {
            harness.close()
        }
    }

    @Test
    fun noLinkPendingOrderShowsNoResultYetAndKeepsButtonsAfter60sTimeoutWithoutCheckingTimeoutBody() {
        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}", 200, "orders-detail-200-pending-payment.json")
        }
        val dispatcher = StandardTestDispatcher()
        val testScope = TestScope(dispatcher)
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
            val deadline = System.currentTimeMillis() + 5_000L
            while (vm.uiState.value.state !is PaymentResultState.NoResultYet && System.currentTimeMillis() < deadline) {
                testScope.advanceUntilIdle()
                Thread.sleep(10)
            }
            assertTrue(vm.uiState.value.state is PaymentResultState.NoResultYet)

            // Advance past 60s polling window (ĐT 707, A-79, A-102)
            repeat(35) {
                testScope.advanceTimeBy(2_000L)
                testScope.advanceUntilIdle()
                Thread.sleep(5)
            }
            val finalState = vm.uiState.value.state as PaymentResultState.NoResultYet
            assertTrue("Poller must stop after 60s", finalState.timedOut)

            val callsAtTimeout = harness.server.requestCount("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}")
            testScope.advanceTimeBy(10_000L)
            testScope.advanceUntilIdle()
            assertEquals("No polling calls after 60s", callsAtTimeout, harness.server.requestCount("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}"))

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
            composeRule.onNodeWithText("Chưa nhận được kết quả thanh toán").assertIsDisplayed()
            composeRule.onNodeWithText("đơn vẫn giữ giá tới 17:00", substring = true).assertIsDisplayed()
            composeRule.onNodeWithTag(PaymentTestTags.RESULT_CONTINUE_PAYMENT_BUTTON).assertIsDisplayed()
            composeRule.onNodeWithTag(PaymentTestTags.RESULT_VIEW_ORDER_BUTTON).assertIsDisplayed()
            composeRule.onNodeWithText("bạn không cần thanh toán lại", substring = true).assertDoesNotExist()
        } finally {
            vm.onLeaveScreen()
            harness.close()
        }
    }

    @Test
    fun noLinkPendingOrderTransitionsToFulfilledWhenPollReceivesFulfilled() {
        val harness = PaymentTestFixtures.createHarness {
            respondSequence(
                "GET",
                "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}",
                { ContractFakeServer.apiFixtureResponse(200, "orders-detail-200-pending-payment.json") },
                { ContractFakeServer.apiFixtureResponse(200, "orders-detail-200-fulfilled.json") },
            )
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
            poller = poller,
        )
        try {
            vm.start()
            val deadline1 = System.currentTimeMillis() + 5_000L
            while (vm.uiState.value.state !is PaymentResultState.NoResultYet && System.currentTimeMillis() < deadline1) {
                testScope.advanceUntilIdle()
                Thread.sleep(10)
            }
            assertTrue(vm.uiState.value.state is PaymentResultState.NoResultYet)

            testScope.advanceTimeBy(2_000L)
            val deadline2 = System.currentTimeMillis() + 5_000L
            while (vm.uiState.value.state !is PaymentResultState.Fulfilled && System.currentTimeMillis() < deadline2) {
                testScope.advanceUntilIdle()
                Thread.sleep(10)
            }
            assertTrue(vm.uiState.value.state is PaymentResultState.Fulfilled)
        } finally {
            vm.onLeaveScreen()
            harness.close()
        }
    }
}
