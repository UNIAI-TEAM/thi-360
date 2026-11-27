package vn.unicom.thi360.feature.payment

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.config.AppEnvironment
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.feature.payment.domain.PendingPaymentOrderStore
import vn.unicom.thi360.feature.payment.ui.PaymentTestTags
import vn.unicom.thi360.feature.recovery.ResetLink
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.routes.AppNavigator
import vn.unicom.thi360.shell.routes.AppRoute
import vn.unicom.thi360.shell.routes.KEY_RESOLVED_ORDER_CODE
import vn.unicom.thi360.shell.routes.PaymentRoutesConfig
import vn.unicom.thi360.shell.routes.PaymentRuntimeOverrides
import vn.unicom.thi360.shell.routes.registerPaymentHooks

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class PaymentReturnRoutingTest {

    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun tearDown() {
        PaymentRoutesConfig.setTestOverrides(null)
    }

    @Test
    fun returnLinkSendsExactVnpParamsToVerifyThenLoadsOrder() {
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
        val returnUrl = "https://$host/checkout/result/app?vnp_TxnRef=DH26112600000101&vnp_ResponseCode=00&vnp_Amount=20000000&vnp_BankCode=NCB&vnp_SecureHash=sig123"
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
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.RESULT_HEADING).fetchSemanticsNodes().isNotEmpty()
            }

            composeRule.onNodeWithTag(PaymentTestTags.RESULT_HEADING).assertTextEquals("Đã cấp bài thi").assertIsDisplayed()
            composeRule.onNodeWithTag(PaymentTestTags.RESULT_STATUS_CHIP).assertTextEquals("Đã cấp bài thi").assertIsDisplayed()
            val verifyReq = harness.server.lastRequest("GET", "/api/v1/public/payments/vnpay/return")!!
            val reqUrl = verifyReq.requestUrl!!
            assertEquals(
                setOf("vnp_TxnRef", "vnp_ResponseCode", "vnp_Amount", "vnp_BankCode", "vnp_SecureHash"),
                reqUrl.queryParameterNames,
            )
            assertEquals("DH26112600000101", reqUrl.queryParameter("vnp_TxnRef"))
            assertEquals("00", reqUrl.queryParameter("vnp_ResponseCode"))
            assertEquals("20000000", reqUrl.queryParameter("vnp_Amount"))
            assertEquals("NCB", reqUrl.queryParameter("vnp_BankCode"))
            assertEquals("sig123", reqUrl.queryParameter("vnp_SecureHash"))
            assertEquals(1, harness.server.requestCount("GET", "/api/v1/me/orders/${PaymentTestFixtures.ORDER_CODE}"))
        } finally {
            harness.close()
        }
    }

    @Test
    fun invalidLinksDoNotOpenResultOrCallVerify() {
        val host = ResetLink.allowedHostFromWebBaseUrl(AppEnvironment.webBaseUrl)
        val harness = PaymentTestFixtures.createHarness()
        val pendingStore = PendingPaymentOrderStore { harness.accountScopedStoreProvider.current() }
        registerPaymentHooks(
            inboundLinkRouter = harness.inboundLinkRouter,
            sessionCleanup = harness.sessionCleanup,
            pendingOrderStoreProvider = { pendingStore },
            allowedHostProvider = { host },
        )
        try {
            val badUris = listOf(
                "http://$host/checkout/result/app?vnp_ResponseCode=00",
                "https://evil.example/checkout/result/app?vnp_ResponseCode=00",
                "https://$host/checkout/result?vnp_ResponseCode=00",
                "https://$host/checkout/result/app/x?vnp_ResponseCode=00",
            )
            for (raw in badUris) {
                assertFalse(
                    "Invalid URI must be ignored: $raw",
                    harness.inboundLinkRouter.handleIntent(Intent(Intent.ACTION_VIEW, Uri.parse(raw))),
                )
            }
            assertEquals(0, harness.server.requestCount("GET", "/api/v1/public/payments/vnpay/return"))
        } finally {
            harness.close()
        }
    }

    @Test
    fun fallbackOpenAppTargetHandledAsReturnLink() {
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
        val fallbackTarget = "https://$host/checkout/result/app?vnp_TxnRef=DH26112600000101&vnp_ResponseCode=00&vnp_SecureHash=fallbacksig"
        try {
            composeRule.setContent {
                AppRoot(
                    preferences = AppPreferences(locale = AppLocale.VI),
                    onSelectLocale = {},
                    onSelectThemeMode = {},
                    sessionManager = harness.stack.sessionManager,
                    validateOnLaunch = false,
                    recoveryGateway = harness.recoveryGateway,
                    initialIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fallbackTarget)),
                    inboundLinkRouter = harness.inboundLinkRouter,
                    legalRepository = harness.legalRepository,
                )
            }
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.RESULT_HEADING).fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue(harness.server.requestCount("GET", "/api/v1/public/payments/vnpay/return") == 1)
            composeRule.onNodeWithTag(PaymentTestTags.RESULT_HEADING).assertTextEquals("Đã cấp bài thi").assertIsDisplayed()
            composeRule.onNodeWithTag(PaymentTestTags.RESULT_STATUS_CHIP).assertTextEquals("Đã cấp bài thi").assertIsDisplayed()
        } finally {
            harness.close()
        }
    }

    @Test
    fun byReturnLinkOpenOrderDetailThenBackPreservesFulfilledResultWithoutReverifying() {
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
        val returnUrl = "https://$host/checkout/result/app?vnp_TxnRef=DH26112600000101&vnp_ResponseCode=00&vnp_SecureHash=sigback"
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
                    initialIntent = Intent(Intent.ACTION_VIEW, Uri.parse(returnUrl)),
                    inboundLinkRouter = harness.inboundLinkRouter,
                    legalRepository = harness.legalRepository,
                )
            }
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.RESULT_VIEW_ORDER_BUTTON).fetchSemanticsNodes().isNotEmpty()
            }
            val nav = checkNotNull(capturedNav)
            val resultEntry = nav.navController.currentBackStackEntry!!
            val savedStateKeys = resultEntry.savedStateHandle.keys()
            assertEquals(PaymentTestFixtures.ORDER_CODE, resultEntry.savedStateHandle.get<String>(KEY_RESOLVED_ORDER_CODE))
            assertTrue("vnp_* parameters must never enter SavedState", savedStateKeys.none { it.contains("vnp_", ignoreCase = true) })
            val linkToken = resultEntry.arguments?.getString(AppRoute.ARG_LINK_TOKEN)
            assertNull("Sensitive linkToken must be removed from linkStore once orderCode is resolved", nav.linkStore.get(linkToken))

            // Push OrderDetail on top of PaymentResult
            composeRule.onNodeWithTag(PaymentTestTags.RESULT_VIEW_ORDER_BUTTON).performClick()
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.ORDER_DETAIL_HEADING).fetchSemanticsNodes().isNotEmpty()
            }

            // Press Back from OrderDetail -> returns to PaymentResult with Fulfilled order (not ByOrder("") / NotOwned)
            composeRule.onNodeWithTag(PaymentTestTags.ORDER_DETAIL_BACK_BUTTON).performClick()
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodesWithTag(PaymentTestTags.RESULT_VIEW_MY_EXAMS_BUTTON).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(PaymentTestTags.RESULT_HEADING).assertTextEquals("Đã cấp bài thi").assertIsDisplayed()
            composeRule.onNodeWithTag(PaymentTestTags.RESULT_STATUS_CHIP).assertTextEquals("Đã cấp bài thi").assertIsDisplayed()
            composeRule.onNodeWithTag(PaymentTestTags.RESULT_SWITCH_ACCOUNT_BUTTON).assertDoesNotExist()
            assertEquals(
                "Back navigation must not re-submit /vnpay/return",
                1,
                harness.server.requestCount("GET", "/api/v1/public/payments/vnpay/return"),
            )
            assertEquals(
                "Must never call /api/v1/me/orders/ with empty code",
                0,
                harness.server.requestCount("GET", "/api/v1/me/orders/"),
            )
        } finally {
            harness.close()
        }
    }
}
