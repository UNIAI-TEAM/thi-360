package vn.unicom.thi360.feature.payment

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import vn.unicom.thi360.MainActivity
import vn.unicom.thi360.core.config.AppEnvironment
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.network.generated.model.CreateOrderRequest
import vn.unicom.thi360.core.network.generated.model.MeResponse
import vn.unicom.thi360.core.network.generated.model.OrderDetail
import vn.unicom.thi360.core.network.generated.model.OrderPaymentView
import vn.unicom.thi360.core.network.generated.model.PageResponseOrderSummary
import vn.unicom.thi360.core.network.generated.model.PayOrderResponse
import vn.unicom.thi360.core.network.generated.model.PriceQuoteRequest
import vn.unicom.thi360.core.network.generated.model.VnpayReturnResponse
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.session.AppSessionContainer
import vn.unicom.thi360.core.storage.TokenStore
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.feature.payment.data.CreateOrderOutcome
import vn.unicom.thi360.feature.payment.data.ExamAccessResult
import vn.unicom.thi360.feature.payment.data.PaymentGateway
import vn.unicom.thi360.feature.payment.data.PriceQuoteResult
import vn.unicom.thi360.feature.payment.data.ProductLookupResult
import vn.unicom.thi360.feature.payment.data.VnpayVerifyResult
import vn.unicom.thi360.feature.payment.ui.PaymentTestTags
import vn.unicom.thi360.feature.recovery.ResetLink
import vn.unicom.thi360.shell.routes.PaymentRoutesConfig
import vn.unicom.thi360.shell.routes.PaymentRuntimeOverrides

@RunWith(AndroidJUnit4::class)
class PaymentReturnLinkInstrumentedTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private class MemoryTokenStore(initial: Tokens?) : TokenStore {
        private var current: Tokens? = initial
        override fun read(): Tokens? = current
        override fun write(tokens: Tokens) {
            current = tokens
        }
        override fun clear() {
            current = null
        }
    }

    private fun stubFulfilledOrder(): OrderDetail = OrderDetail(
        amountDueVnd = 200_000L,
        attemptsGranted = 3,
        benefits = mapOf("aiExplanation" to JsonPrimitive(true)),
        buyerFullName = "Nguyễn Văn An",
        buyerUsername = "student.demo",
        code = "DH261126000001",
        createdAt = OffsetDateTime.parse("2026-11-20T10:00:00Z"),
        discountApplied = OrderDetail.DiscountApplied.NONE,
        history = emptyList(),
        id = UUID.fromString("00000000-0000-7000-8000-000000000001"),
        items = emptyList(),
        listPriceVnd = 200_000L,
        optionCodes = emptyList(),
        paymentMethod = OrderDetail.PaymentMethod.VNPAY,
        payments = listOf(
            OrderPaymentView(
                amountVnd = 200_000L,
                attemptSeq = 1,
                createdAt = OffsetDateTime.parse("2026-11-20T10:00:00Z"),
                duplicate = false,
                id = UUID.fromString("00000000-0000-7000-8000-000000000006"),
                state = OrderPaymentView.State.SUCCEEDED,
                txnRef = "DH26112600000101",
            ),
        ),
        productCode = "EXAM-DEMO-01",
        productName = "Đề thi HSA tổng hợp DEMO",
        productType = OrderDetail.ProductType.EXAM,
        promotionDiscountVnd = 0L,
        resultView = "FULL_SOLUTION",
        state = OrderDetail.State.FULFILLED,
        updatedAt = OffsetDateTime.parse("2026-11-20T10:00:00Z"),
        userId = UUID.fromString("019320a0-0000-7000-8000-000000000001"),
        voucherDiscountVnd = 0L,
        fulfilledAt = OffsetDateTime.parse("2026-11-20T10:00:00Z"),
    )

    private fun stubGateway(
        capturedParams: AtomicReference<Map<String, String>?>,
    ): PaymentGateway = object : PaymentGateway {
        override suspend fun fetchCurrentUser(): ApiResult<MeResponse> = ApiResult.Success(
            MeResponse(
                access = MeResponse.Access.EXAM,
                fullName = "Nguyễn Văn An",
                homeUrl = "/home",
                id = UUID.fromString("019320a0-0000-7000-8000-000000000001"),
                permissions = emptyList(),
                realm = MeResponse.Realm.APP,
                username = "student.demo",
            ),
        )
        override suspend fun loadProduct(code: String): ProductLookupResult = ProductLookupResult.NotFound
        override suspend fun requestQuote(request: PriceQuoteRequest): PriceQuoteResult = PriceQuoteResult.NotFound
        override suspend fun queryExamAccess(codes: List<String>): ExamAccessResult = ExamAccessResult.Success(emptyList())
        override suspend fun createOrder(request: CreateOrderRequest, idempotencyKey: String): ApiResult<CreateOrderOutcome> = error("unused")
        override suspend fun verifyVnpayReturn(vnpParams: Map<String, String>): VnpayVerifyResult {
            capturedParams.set(vnpParams)
            return VnpayVerifyResult.Success(
                VnpayReturnResponse(orderCode = "DH261126000001", paymentState = "SUCCEEDED"),
            )
        }
        override suspend fun listOrders(page: Int, size: Int): ApiResult<PageResponseOrderSummary> = error("unused")
        override suspend fun getOrder(orderCode: String): ApiResult<OrderDetail> = ApiResult.Success(stubFulfilledOrder())
        override suspend fun payOrder(orderCode: String, idempotencyKey: String): ApiResult<PayOrderResponse> = error("unused")
        override suspend fun cancelOrder(orderCode: String, idempotencyKey: String): ApiResult<OrderDetail> = error("unused")
    }

    private fun installActiveSessionAndGateway(
        capturedParams: AtomicReference<Map<String, String>?>,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tokenStore = MemoryTokenStore(
            Tokens(
                accessToken = "instrumented-access-token",
                refreshToken = "instrumented-refresh-token",
                accessExpiresIn = 900,
                refreshExpiresIn = 3600,
                mustChangePassword = false,
                accountId = "019320a0-0000-7000-8000-000000000001",
            ),
        )
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = "http://127.0.0.1:1/",
            environment = Environment.DEV,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.VI },
            resolveAccountIdOnAuth = false,
        )
        AppSessionContainer.setTestStack(context, stack)
        PaymentRoutesConfig.setTestOverrides(
            PaymentRuntimeOverrides(
                gateway = stubGateway(capturedParams),
            ),
        )
    }

    @After
    fun tearDown() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PaymentRoutesConfig.setTestOverrides(null)
        AppSessionContainer.setTestStack(context, null)
    }

    @Test
    fun coldStartOpensResult() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val host = ResetLink.allowedHostFromWebBaseUrl(AppEnvironment.webBaseUrl)
        val captured = AtomicReference<Map<String, String>?>(null)
        installActiveSessionAndGateway(captured)

        val returnIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://$host/checkout/result/app?vnp_TxnRef=DH26112600000101&vnp_ResponseCode=00&vnp_SecureHash=cold123"),
        ).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            setClass(context, MainActivity::class.java)
        }

        val resolved = context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_VIEW, returnIntent.data).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                setPackage(context.packageName)
            },
            0,
        )
        assertEquals(1, resolved.size)
        assertEquals(MainActivity::class.java.name, resolved.single().activityInfo.name)

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val launchIntent = Intent(returnIntent)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

        val activity = instrumentation.startActivitySync(launchIntent) as MainActivity
        try {
            composeRule.waitUntil(timeoutMillis = 15_000) {
                runCatching {
                    composeRule.onNodeWithTag(PaymentTestTags.RESULT_HEADING).assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }
            composeRule.onNodeWithText("Đã cấp bài thi").assertIsDisplayed()
            assertEquals("DH26112600000101", captured.get()?.get("vnp_TxnRef"))
            assertEquals("00", captured.get()?.get("vnp_ResponseCode"))
            assertEquals("cold123", captured.get()?.get("vnp_SecureHash"))
        } finally {
            instrumentation.runOnMainSync {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    activity.finish()
                }
            }
            instrumentation.waitForIdleSync()
        }
    }

    @Test
    fun warmIntentOpensResult() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val host = ResetLink.allowedHostFromWebBaseUrl(AppEnvironment.webBaseUrl)
        val captured = AtomicReference<Map<String, String>?>(null)
        installActiveSessionAndGateway(captured)

        val mainIntent = Intent(Intent.ACTION_MAIN).apply {
            setClass(context, MainActivity::class.java)
        }

        ActivityScenario.launch<MainActivity>(mainIntent).use { scenario ->
            composeRule.waitForIdle()
            val warmReturnIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://$host/checkout/result/app?vnp_TxnRef=DH26112600000101&vnp_ResponseCode=00&vnp_SecureHash=warm456"),
            ).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                setClass(context, MainActivity::class.java)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }

            scenario.onActivity { activity ->
                activity.startActivity(warmReturnIntent)
            }

            composeRule.waitUntil(timeoutMillis = 15_000) {
                runCatching {
                    composeRule.onNodeWithTag(PaymentTestTags.RESULT_HEADING).assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }
            composeRule.onNodeWithText("Đã cấp bài thi").assertIsDisplayed()
            assertEquals("DH26112600000101", captured.get()?.get("vnp_TxnRef"))
            assertEquals("warm456", captured.get()?.get("vnp_SecureHash"))
        }
    }
}
