package vn.unicom.thi360.feature.payment

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.rememberNavController
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.config.AppEnvironment
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.feature.login.LoginTestTags
import vn.unicom.thi360.feature.payment.domain.PendingPaymentOrderStore
import vn.unicom.thi360.feature.payment.ui.PaymentTestTags
import vn.unicom.thi360.feature.recovery.ResetLink
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.routes.AppNavigator
import vn.unicom.thi360.shell.routes.AppRoute
import vn.unicom.thi360.shell.routes.PaymentRoutesConfig
import vn.unicom.thi360.shell.routes.PaymentRuntimeOverrides
import vn.unicom.thi360.shell.routes.registerPaymentHooks
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class PaymentReturnGatingTest {

    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun tearDown() {
        PaymentRoutesConfig.setTestOverrides(null)
    }

    @Test
    fun lockedFirst() {
        val host = ResetLink.allowedHostFromWebBaseUrl(AppEnvironment.webBaseUrl)
        val harness = PaymentTestFixtures.createHarness(
            initialTokens = PaymentTestFixtures.defaultTokens,
            startLocked = true,
            serverSetup = {
                respondAuthFixture("POST", "/api/v1/auth/mobile/refresh", 200, "refresh-200.json")
                respondApiFixture("GET", "/api/v1/public/payments/vnpay/return", 200, "vnpay-return-200-succeeded.json")
                respondApiFixture("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}", 200, "orders-detail-200-fulfilled.json")
            },
        )
        val pendingStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() }
        PaymentRoutesConfig.setTestOverrides(
            PaymentRuntimeOverrides(
                gateway = PaymentTestFixtures.createGateway(harness),
                pendingOrderStore = pendingStore,
                apiBaseUrl = harness.server.url("/"),
            ),
        )
        registerPaymentHooks(
            inboundLinkRouter = harness.inboundLinkRouter,
            sessionCleanup = harness.sessionCleanup,
            pendingOrderStoreProvider = { pendingStore },
            allowedHostProvider = { host },
        )
        val returnUrl = "https://$host/checkout/result/app?vnp_TxnRef=DH26112600000101&vnp_ResponseCode=00&vnp_SecureHash=sig"
        try {
            composeRule.setContent {
                AppRoot(
                    preferences = AppPreferences(locale = AppLocale.VI),
                    onSelectLocale = {},
                    onSelectThemeMode = {},
                    sessionManager = harness.stack.sessionManager,
                    validateOnLaunch = false,
                    recoveryGateway = harness.recoveryGateway,
                    initialIntent = Intent(Intent.ACTION_VIEW, Uri.parse(returnUrl)),
                    lockedContent = {
                        Text("LockGate", modifier = Modifier.testTag("payment_lock_gate"))
                    },
                    inboundLinkRouter = harness.inboundLinkRouter,
                    legalRepository = harness.legalRepository,
                )
            }
            composeRule.onNodeWithTag("payment_lock_gate").assertIsDisplayed()
            composeRule.onNodeWithTag(PaymentTestTags.RESULT_SCREEN).assertDoesNotExist()
            assertEquals(0, harness.server.requestCount("GET", "/api/v1/public/payments/vnpay/return"))

            runBlocking { harness.stack.sessionManager.unlockAndRefresh() }
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.RESULT_HEADING).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(PaymentTestTags.RESULT_HEADING).assertTextEquals("Đã cấp bài thi").assertIsDisplayed()
            composeRule.onNodeWithTag(PaymentTestTags.RESULT_STATUS_CHIP).assertTextEquals("Đã cấp bài thi").assertIsDisplayed()
        } finally {
            harness.close()
        }
    }

    @Test
    fun signedOutThenLogin() {
        val host = ResetLink.allowedHostFromWebBaseUrl(AppEnvironment.webBaseUrl)
        val harness = PaymentTestFixtures.createHarness(
            initialTokens = null,
            serverSetup = {
                respondAuthFixture("POST", "/api/v1/auth/mobile/login", 200, "login-200.json")
                respondApiFixture("GET", "/api/v1/public/payments/vnpay/return", 200, "vnpay-return-200-succeeded.json")
                respondApiFixture("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}", 200, "orders-detail-200-fulfilled.json")
            },
        )
        val pendingStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() }
        PaymentRoutesConfig.setTestOverrides(
            PaymentRuntimeOverrides(
                gateway = PaymentTestFixtures.createGateway(harness),
                pendingOrderStore = pendingStore,
                apiBaseUrl = harness.server.url("/"),
            ),
        )
        registerPaymentHooks(
            inboundLinkRouter = harness.inboundLinkRouter,
            sessionCleanup = harness.sessionCleanup,
            pendingOrderStoreProvider = { pendingStore },
            allowedHostProvider = { host },
        )
        val returnUrl = "https://$host/checkout/result/app?vnp_TxnRef=DH26112600000101&vnp_ResponseCode=00&vnp_SecureHash=sig"
        try {
            composeRule.setContent {
                AppRoot(
                    preferences = AppPreferences(locale = AppLocale.VI),
                    onSelectLocale = {},
                    onSelectThemeMode = {},
                    sessionManager = harness.stack.sessionManager,
                    validateOnLaunch = false,
                    recoveryGateway = harness.recoveryGateway,
                    initialIntent = Intent(Intent.ACTION_VIEW, Uri.parse(returnUrl)),
                    inboundLinkRouter = harness.inboundLinkRouter,
                    legalRepository = harness.legalRepository,
                )
            }
            composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD).assertIsDisplayed()
            composeRule.onNodeWithTag(PaymentTestTags.RESULT_SCREEN).assertDoesNotExist()
            assertEquals(0, harness.server.requestCount("GET", "/api/v1/public/payments/vnpay/return"))

            composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD).performTextInput("student.demo")
            composeRule.onNodeWithTag(LoginTestTags.PASSWORD_FIELD).performTextInput("Secret123!")
            composeRule.onNodeWithTag(LoginTestTags.SUBMIT_BUTTON).performClick()

            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.RESULT_HEADING).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(PaymentTestTags.RESULT_HEADING).assertTextEquals("Đã cấp bài thi").assertIsDisplayed()
            composeRule.onNodeWithTag(PaymentTestTags.RESULT_STATUS_CHIP).assertTextEquals("Đã cấp bài thi").assertIsDisplayed()
        } finally {
            harness.close()
        }
    }

    @Test
    fun notOwnedAfterLogin() {
        val host = ResetLink.allowedHostFromWebBaseUrl(AppEnvironment.webBaseUrl)
        val harness = PaymentTestFixtures.createHarness(
            initialTokens = null,
            serverSetup = {
                respondAuthFixture("POST", "/api/v1/auth/mobile/login", 200, "login-200.json")
                respondApiFixture("GET", "/api/v1/public/payments/vnpay/return", 200, "vnpay-return-200-succeeded.json")
                respondApiFixture("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}", 404, "orders-detail-404-order-not-found.json")
            },
        )
        val pendingStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() }
        PaymentRoutesConfig.setTestOverrides(
            PaymentRuntimeOverrides(
                gateway = PaymentTestFixtures.createGateway(harness),
                pendingOrderStore = pendingStore,
                apiBaseUrl = harness.server.url("/"),
            ),
        )
        registerPaymentHooks(
            inboundLinkRouter = harness.inboundLinkRouter,
            sessionCleanup = harness.sessionCleanup,
            pendingOrderStoreProvider = { pendingStore },
            allowedHostProvider = { host },
        )
        val returnUrl = "https://$host/checkout/result/app?vnp_TxnRef=DH26112600000101&vnp_ResponseCode=00&vnp_SecureHash=sig"
        try {
            composeRule.setContent {
                AppRoot(
                    preferences = AppPreferences(locale = AppLocale.VI),
                    onSelectLocale = {},
                    onSelectThemeMode = {},
                    sessionManager = harness.stack.sessionManager,
                    validateOnLaunch = false,
                    recoveryGateway = harness.recoveryGateway,
                    initialIntent = Intent(Intent.ACTION_VIEW, Uri.parse(returnUrl)),
                    inboundLinkRouter = harness.inboundLinkRouter,
                    legalRepository = harness.legalRepository,
                )
            }
            runBlocking {
                harness.stack.sessionManager.login("student.demo", "Secret123!")
            }
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.RESULT_SWITCH_ACCOUNT_BUTTON).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Không tìm thấy đơn trong tài khoản đang đăng nhập").assertIsDisplayed()
            composeRule.onNodeWithText(PaymentTestFixtures.ORDER_CODE, substring = true).assertIsDisplayed()
            composeRule.onNodeWithText("student.demo", substring = true).assertIsDisplayed()
        } finally {
            harness.close()
        }
    }

    @Test
    fun sameOrderUpdatesOneScreen() {
        val host = ResetLink.allowedHostFromWebBaseUrl(AppEnvironment.webBaseUrl)
        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/public/payments/vnpay/return", 200, "vnpay-return-200-succeeded.json")
            respondSequence(
                "GET",
                "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}",
                { ContractFakeServer.apiFixtureResponse(200, "orders-detail-200-pending-payment.json") },
                { ContractFakeServer.apiFixtureResponse(200, "orders-detail-200-fulfilled.json") },
            )
        }
        val pendingStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() }
        PaymentRoutesConfig.setTestOverrides(
            PaymentRuntimeOverrides(
                gateway = PaymentTestFixtures.createGateway(harness),
                pendingOrderStore = pendingStore,
                apiBaseUrl = harness.server.url("/"),
            ),
        )
        registerPaymentHooks(
            inboundLinkRouter = harness.inboundLinkRouter,
            sessionCleanup = harness.sessionCleanup,
            pendingOrderStoreProvider = { pendingStore },
            allowedHostProvider = { host },
        )
        var capturedNav: AppNavigator? = null
        try {
            composeRule.setContent {
                val navController = rememberNavController()
                val appNav = AppNavigator(navController)
                capturedNav = appNav
                AppRoot(
                    preferences = AppPreferences(locale = AppLocale.VI),
                    onSelectLocale = {},
                    onSelectThemeMode = {},
                    navController = navController,
                    navigator = appNav,
                    sessionManager = harness.stack.sessionManager,
                    validateOnLaunch = false,
                    recoveryGateway = harness.recoveryGateway,
                    inboundLinkRouter = harness.inboundLinkRouter,
                    legalRepository = harness.legalRepository,
                )
            }
            val nav = checkNotNull(capturedNav)
            composeRule.runOnIdle {
                nav.openPaymentResult(AppRoute.PaymentResult.ByOrder(PaymentTestFixtures.ORDER_CODE))
            }
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.RESULT_CONTINUE_PAYMENT_BUTTON).fetchSemanticsNodes().isNotEmpty()
            }
            val backStackBefore = nav.navController.currentBackStack.value.count {
                it.destination.route?.contains("/payment-result?") == true
            }
            assertEquals(1, backStackBefore)

            // Deliver return link for same order -> updates SAME screen without pushing second result screen
            val returnUri = Uri.parse("https://$host/checkout/result/app?vnp_TxnRef=DH26112600000101&vnp_ResponseCode=00&vnp_SecureHash=sig1")
            composeRule.runOnIdle {
                harness.inboundLinkRouter.handleUri(returnUri)
            }
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.RESULT_VIEW_MY_EXAMS_BUTTON).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(PaymentTestTags.RESULT_HEADING).assertTextEquals("Đã cấp bài thi").assertIsDisplayed()
            composeRule.onNodeWithTag(PaymentTestTags.RESULT_STATUS_CHIP).assertTextEquals("Đã cấp bài thi").assertIsDisplayed()
            val backStackAfter = nav.navController.currentBackStack.value.count {
                it.destination.route?.contains("/payment-result?") == true
            }
            assertEquals(1, backStackAfter)
        } finally {
            harness.close()
        }
    }

    @Test
    fun sameLinkTwiceOneScreen() {
        val host = ResetLink.allowedHostFromWebBaseUrl(AppEnvironment.webBaseUrl)
        val harness = PaymentTestFixtures.createHarness {
            respondApiFixture("GET", "/api/v1/public/payments/vnpay/return", 200, "vnpay-return-200-succeeded.json")
            respondApiFixture("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}", 200, "orders-detail-200-fulfilled.json")
        }
        val pendingStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() }
        PaymentRoutesConfig.setTestOverrides(
            PaymentRuntimeOverrides(
                gateway = PaymentTestFixtures.createGateway(harness),
                pendingOrderStore = pendingStore,
                apiBaseUrl = harness.server.url("/"),
            ),
        )
        registerPaymentHooks(
            inboundLinkRouter = harness.inboundLinkRouter,
            sessionCleanup = harness.sessionCleanup,
            pendingOrderStoreProvider = { pendingStore },
            allowedHostProvider = { host },
        )
        var capturedNav: AppNavigator? = null
        val returnUri = Uri.parse("https://$host/checkout/result/app?vnp_TxnRef=DH26112600000101&vnp_ResponseCode=00&vnp_SecureHash=dup")
        try {
            composeRule.setContent {
                val navController = rememberNavController()
                val appNav = AppNavigator(navController)
                capturedNav = appNav
                AppRoot(
                    preferences = AppPreferences(locale = AppLocale.VI),
                    onSelectLocale = {},
                    onSelectThemeMode = {},
                    navController = navController,
                    navigator = appNav,
                    sessionManager = harness.stack.sessionManager,
                    validateOnLaunch = false,
                    recoveryGateway = harness.recoveryGateway,
                    initialIntent = Intent(Intent.ACTION_VIEW, returnUri),
                    inboundLinkRouter = harness.inboundLinkRouter,
                    legalRepository = harness.legalRepository,
                )
            }
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.RESULT_HEADING).fetchSemanticsNodes().isNotEmpty()
            }
            val nav = checkNotNull(capturedNav)
            composeRule.runOnIdle {
                harness.inboundLinkRouter.handleUri(returnUri)
            }
            composeRule.waitForIdle()
            val count = nav.navController.currentBackStack.value.count {
                it.destination.route?.contains("/payment-result?") == true
            }
            assertEquals(1, count)
            assertEquals(1, harness.server.requestCount("GET", "/api/v1/public/payments/vnpay/return"))
        } finally {
            harness.close()
        }
    }

    @Test
    fun lockAndUnlockWithin15MinutesPreservesPendingOrderAndNoResultYet() {
        val host = ResetLink.allowedHostFromWebBaseUrl(AppEnvironment.webBaseUrl)
        val harness = PaymentTestFixtures.createHarness {
            respondAuthFixture("POST", "/api/v1/auth/mobile/refresh", 200, "refresh-200.json")
            respondApiFixture("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}", 200, "orders-detail-200-pending-payment.json")
        }
        val pendingStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() }
        var nowMillis = 100_000L
        pendingStore.save(PaymentTestFixtures.ORDER_CODE, openedAtMillis = nowMillis)
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
                    lockedContent = {
                        Text("LockGate", modifier = Modifier.testTag("payment_lock_gate"))
                    },
                    inboundLinkRouter = harness.inboundLinkRouter,
                    legalRepository = harness.legalRepository,
                )
            }
            composeRule.runOnIdle {
                harness.inboundLinkRouter.notifyForegroundReturn()
            }
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.RESULT_CONTINUE_PAYMENT_BUTTON).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Chưa nhận được kết quả thanh toán").assertIsDisplayed()

            // Simulate 6-minute background (> 5-min biometric lock threshold, < 15-min PendingPaymentOrder TTL)
            nowMillis += 6L * 60L * 1000L
            composeRule.runOnIdle {
                harness.stack.sessionManager.lockSession()
            }
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag("payment_lock_gate").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(
                "PendingPaymentOrder must NOT be wiped when SessionState.Locked unmounts PaymentResultScreen",
                PaymentTestFixtures.ORDER_CODE,
                pendingStore.readValid(nowMillis)?.orderCode,
            )

            composeRule.runOnIdle {
                harness.inboundLinkRouter.notifyForegroundReturn()
            }
            runBlocking { harness.stack.sessionManager.unlockAndRefresh() }
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.RESULT_CONTINUE_PAYMENT_BUTTON).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Chưa nhận được kết quả thanh toán").assertIsDisplayed()
            assertEquals(
                "PendingPaymentOrder must still be intact after unlocking into NoResultYet",
                PaymentTestFixtures.ORDER_CODE,
                pendingStore.readValid(nowMillis)?.orderCode,
            )
        } finally {
            harness.close()
        }
    }

    @Test
    fun byReturnLinkCheckingLockAndUnlockWithPendingOrderPreservesCheckingWithoutDuplicateGetOrder() {
        val host = ResetLink.allowedHostFromWebBaseUrl(AppEnvironment.webBaseUrl)
        val unlockGetOrderGate = CountDownLatch(1)
        val unlockGetOrderEntered = CountDownLatch(1)
        val orderRequestSeq = AtomicInteger(0)
        val harness = PaymentTestFixtures.createHarness {
            respondAuthFixture("POST", "/api/v1/auth/mobile/refresh", 200, "refresh-200.json")
            respondApiFixture("GET", "/api/v1/public/payments/vnpay/return", 200, "vnpay-return-200-pending.json")
            respond("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}") {
                // Hold the post-unlock getOrder request in Loading so checkPendingOrder deterministically
                // collides with the initial Loading window (R2-2).
                if (orderRequestSeq.incrementAndGet() == 2) {
                    unlockGetOrderEntered.countDown()
                    unlockGetOrderGate.await(5, TimeUnit.SECONDS)
                }
                ContractFakeServer.apiFixtureResponse(200, "orders-detail-200-pending-payment.json")
            }
        }
        val pendingStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() }
        var nowMillis = 100_000L
        pendingStore.save(PaymentTestFixtures.ORDER_CODE, openedAtMillis = nowMillis)
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
        val returnUrl = "https://$host/checkout/result/app?vnp_TxnRef=DH26112600000101&vnp_ResponseCode=00&vnp_SecureHash=siglockchk"
        try {
            composeRule.setContent {
                AppRoot(
                    preferences = AppPreferences(locale = AppLocale.VI),
                    onSelectLocale = {},
                    onSelectThemeMode = {},
                    sessionManager = harness.stack.sessionManager,
                    validateOnLaunch = false,
                    recoveryGateway = harness.recoveryGateway,
                    initialIntent = Intent(Intent.ACTION_VIEW, Uri.parse(returnUrl)),
                    lockedContent = {
                        Text("LockGate", modifier = Modifier.testTag("payment_lock_gate"))
                    },
                    inboundLinkRouter = harness.inboundLinkRouter,
                    legalRepository = harness.legalRepository,
                )
            }
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.RESULT_CHECK_AGAIN_BUTTON).fetchSemanticsNodes().isNotEmpty()
            }
            val ordersRequestsBeforeLock = harness.server.requestCount("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}")

            // Lock after 6 minutes (> 5-min biometric lock, < 15-min PendingPaymentOrder TTL) while in Checking
            nowMillis += 6L * 60L * 1000L
            composeRule.runOnIdle {
                harness.stack.sessionManager.lockSession()
            }
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag("payment_lock_gate").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(PaymentTestFixtures.ORDER_CODE, pendingStore.readValid(nowMillis)?.orderCode)

            // Queue foreground return while locked, then unlock: post-unlock getOrder holds in Loading
            // while checkPendingOrder -> acceptEntry(ByOrder) executes and must short-circuit cleanly.
            composeRule.runOnIdle {
                harness.inboundLinkRouter.notifyForegroundReturn()
            }
            runBlocking { harness.stack.sessionManager.unlockAndRefresh() }
            composeRule.waitForIdle()
            unlockGetOrderEntered.await(5, TimeUnit.SECONDS)
            composeRule.waitForIdle()
            unlockGetOrderGate.countDown()

            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.RESULT_CHECK_AGAIN_BUTTON).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Đang chờ xác nhận thanh toán").assertIsDisplayed()
            composeRule.onNodeWithTag(PaymentTestTags.RESULT_CONTINUE_PAYMENT_BUTTON).assertDoesNotExist()
            assertEquals(1, harness.server.requestCount("GET", "/api/v1/public/payments/vnpay/return"))
            assertEquals(
                "Unlocking into Checking must not duplicate GET /api/v1/me/orders/{code} during initial Loading",
                ordersRequestsBeforeLock + 1,
                harness.server.requestCount("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}"),
            )
        } finally {
            unlockGetOrderGate.countDown()
            harness.close()
        }
    }
}
