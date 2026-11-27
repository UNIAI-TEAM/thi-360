package vn.unicom.thi360.feature.payment

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

            composeRule.onNodeWithText("Đã cấp bài thi").assertIsDisplayed()
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
            composeRule.onNodeWithText("Đã cấp bài thi").assertIsDisplayed()
        } finally {
            harness.close()
        }
    }
}
