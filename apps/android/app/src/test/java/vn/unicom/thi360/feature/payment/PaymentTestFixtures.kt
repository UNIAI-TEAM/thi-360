package vn.unicom.thi360.feature.payment

import android.content.Context
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.robolectric.Shadows
import vn.unicom.thi360.core.config.AppEnvironment
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.legal.LegalRepository
import vn.unicom.thi360.core.network.AppLogger
import vn.unicom.thi360.core.network.ContractFixtureLoader
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.MutableConnectivityMonitor
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.network.generated.infrastructure.Serializer
import vn.unicom.thi360.core.network.generated.model.MeResponse
import vn.unicom.thi360.core.network.generated.model.OrderDetail
import vn.unicom.thi360.core.network.generated.model.OrderSummary
import vn.unicom.thi360.core.network.generated.model.PageResponseOrderSummary
import vn.unicom.thi360.core.network.generated.model.PriceQuote
import vn.unicom.thi360.core.network.generated.model.ProductDetail
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.session.SessionCleanup
import vn.unicom.thi360.core.storage.AccountScopedStore
import vn.unicom.thi360.core.storage.AccountScopedStoreProvider
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.feature.payment.data.OpenApiPaymentGateway
import vn.unicom.thi360.feature.payment.domain.PaymentBrowser
import vn.unicom.thi360.feature.payment.domain.PendingPaymentOrderStore
import vn.unicom.thi360.feature.recovery.OpenApiRecoveryGateway
import vn.unicom.thi360.feature.recovery.ResetLink
import vn.unicom.thi360.shell.routes.InboundLinkRouter
import vn.unicom.thi360.testing.ContractFakeServer
import vn.unicom.thi360.testing.ContractRepoLegalAssetSource
import vn.unicom.thi360.testing.PortalTestHarness

internal object PaymentTestFixtures {
    const val ACCOUNT_ID = "019320a0-0000-7000-8000-000000000001"
    const val OTHER_ACCOUNT_ID = "019320a0-0000-7000-8000-000000000099"
    const val EXAM_CODE = "EXAM-DEMO-01"
    const val BUNDLE_CODE = "BUNDLE-DEMO-01"
    const val ORDER_CODE = "DH261126000001"

    val defaultTokens = Tokens(
        accessToken = "access-token-payment-test",
        refreshToken = "refresh-token-payment-test",
        accessExpiresIn = 900,
        refreshExpiresIn = 3600,
        mustChangePassword = false,
        accountId = ACCOUNT_ID,
    )

    private val rawJson = Json { ignoreUnknownKeys = true }

    fun readFixture(name: String): String = ContractFixtureLoader.readApiFixture(name)

    fun decodeMe(name: String = "me-200.json"): MeResponse =
        Serializer.kotlinxSerializationJson.decodeFromString(MeResponse.serializer(), readFixture(name))

    fun decodeProduct(name: String = "storefront-products-detail-200-exam.json"): ProductDetail =
        Serializer.kotlinxSerializationJson.decodeFromString(ProductDetail.serializer(), readFixture(name))

    fun decodeQuote(name: String = "price-quote-200.json"): PriceQuote =
        Serializer.kotlinxSerializationJson.decodeFromString(PriceQuote.serializer(), readFixture(name))

    fun decodeOrderDetail(name: String = "orders-detail-200-fulfilled.json"): OrderDetail =
        Serializer.kotlinxSerializationJson.decodeFromString(OrderDetail.serializer(), readFixture(name))

    fun decodeOrdersPage(name: String = "orders-list-200.json"): PageResponseOrderSummary =
        Serializer.kotlinxSerializationJson.decodeFromString(
            PageResponseOrderSummary.serializer(),
            readFixture(name),
        )

    fun mutateJson(fixtureName: String, transform: (JsonObject) -> JsonObject): String {
        val parsed = rawJson.parseToJsonElement(readFixture(fixtureName)).jsonObject
        return transform(parsed).toString()
    }

    /**
     * Plan A6 unproducible-response exception: derives 400 VALIDATION_FAILED for vnpay/return
     * by mutating the certified `vnpay-return-422-vnpay-signature-invalid.json` fixture's `code`
     * and `message` fields.
     */
    fun vnpayReturn400ValidationFailedBody(): String =
        mutateJson("vnpay-return-422-vnpay-signature-invalid.json") { obj ->
            JsonObject(
                obj + mapOf(
                    "code" to JsonPrimitive("VALIDATION_FAILED"),
                    "message" to JsonPrimitive("Missing required vnpay parameters"),
                ),
            )
        }

    /**
     * Plan A6 unproducible-response exception: derives 409 PRODUCT_UNAVAILABLE without an embedded
     * product payload by using the certified `orders-create-409-product-unavailable.json` fixture
     * (mutated only to ensure no `product` key is present).
     */
    fun productUnavailableWithoutProductBody(): String =
        mutateJson("orders-create-409-product-unavailable.json") { obj ->
            JsonObject(obj.filterKeys { it != "product" })
        }

    fun examAccessBody(
        code: String,
        state: String,
        attemptsLeft: Int = 0,
        entitlementId: String? = null,
        activeAttemptId: String? = null,
        hasPendingFulfillment: Boolean = false,
        pendingOrderCode: String? = null,
        bundleItems: List<JsonObject>? = null,
    ): String {
        val base = rawJson.parseToJsonElement(readFixture("exam-access-query-200.json")).jsonObject
        val template = base["items"]!!.jsonArray.first().jsonObject
        val row = buildJsonObject {
            template.forEach { (k, v) -> put(k, v) }
            put("code", code)
            put("state", state)
            put("attemptsLeft", attemptsLeft)
            if (entitlementId != null) put("entitlementId", entitlementId) else put("entitlementId", kotlinx.serialization.json.JsonNull)
            if (activeAttemptId != null) put("activeAttemptId", activeAttemptId) else put("activeAttemptId", kotlinx.serialization.json.JsonNull)
            put("hasPendingFulfillment", hasPendingFulfillment)
            if (pendingOrderCode != null) put("pendingOrderCode", pendingOrderCode) else put("pendingOrderCode", kotlinx.serialization.json.JsonNull)
            if (bundleItems != null) {
                put("items", buildJsonArray { bundleItems.forEach { add(it) } })
            } else {
                put("items", kotlinx.serialization.json.JsonNull)
            }
        }
        return buildJsonObject {
            put("items", buildJsonArray { add(row) })
        }.toString()
    }

    fun createHarness(
        initialTokens: Tokens? = defaultTokens,
        environment: Environment = Environment.DEV,
        startLocked: Boolean = false,
        installDefaultMeHandler: Boolean = true,
        serverSetup: ContractFakeServer.() -> Unit = {},
    ): PortalTestHarness {
        val server = ContractFakeServer(installDefaultMeHandler = installDefaultMeHandler)
        server.serverSetup()
        server.start()

        val rootDir = Files.createTempDirectory("thi360-payment-harness-").toFile()
        val tokenStore = InMemoryTokenStore(initialTokens)
        val sessionCleanup = SessionCleanup(rootDir)
        val storeProvider = AccountScopedStoreProvider(
            rootDir = rootDir,
            accountIdProvider = { tokenStore.read()?.accountId },
        )
        // Thread-safe log list avoids unsynchronized ArrayList races when CheckoutViewModel.loadInitial()
        // executes concurrent OkHttp requests across dispatcher threads.
        val logs: MutableList<String> = CopyOnWriteArrayList()
        val logger = object : AppLogger {
            override fun debug(tag: String, message: String) {
                logs.add("D/$tag: $message")
            }

            override fun warn(tag: String, message: String) {
                logs.add("W/$tag: $message")
            }
        }
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = environment,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.VI },
            logger = logger,
            startLocked = startLocked,
            sessionCleanup = sessionCleanup,
            resolveAccountIdOnAuth = true,
        )
        val recoveryGateway = OpenApiRecoveryGateway(
            publicAuthApi = stack.publicAuthApi,
            authenticatedAuthApi = stack.authenticatedAuthApi,
        )

        return PortalTestHarness(
            server = server,
            tokenStore = tokenStore,
            storeRootDir = rootDir,
            accountScopedStoreProvider = storeProvider,
            sessionCleanup = sessionCleanup,
            connectivityMonitor = MutableConnectivityMonitor(true),
            inboundLinkRouter = InboundLinkRouter {
                ResetLink.allowedHostFromWebBaseUrl(AppEnvironment.webBaseUrl)
            },
            legalRepository = LegalRepository(ContractRepoLegalAssetSource()),
            stack = stack,
            recoveryGateway = recoveryGateway,
            logs = logs,
        )
    }

    fun createGateway(harness: PortalTestHarness): OpenApiPaymentGateway =
        OpenApiPaymentGateway.fromSessionStack(harness.stack)

    fun createTempPendingStore(accountId: String = ACCOUNT_ID): Pair<File, PendingPaymentOrderStore> {
        val rootDir = Files.createTempDirectory("thi360-pending-order-").toFile()
        val store = PendingPaymentOrderStore {
            AccountScopedStore(rootDir = rootDir, accountId = accountId)
        }
        return rootDir to store
    }

    /**
     * Simulates a mid-response TCP disconnect so OkHttp raises an IOException without
     * silently issuing a transparent connection retry before the caller sees NetworkFailure.
     */
    fun networkDisconnectResponse(): MockResponse = MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .addHeader("Content-Length", "128")
        .setBody("{\"truncated")
        .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
}

internal class RecordingPaymentBrowser(
    var returnValue: Boolean = true,
) : PaymentBrowser {
    val openedUrls = mutableListOf<String>()

    override fun open(context: Context, url: String): Boolean {
        openedUrls += url
        return returnValue
    }
}

internal fun ComposeContentTestRule.waitUntilMainIdle(
    timeoutMillis: Long = 5_000L,
    condition: () -> Boolean,
) {
    waitUntil(timeoutMillis) {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        condition()
    }
}

internal class ComposeSlotHost(
    private val composeRule: ComposeContentTestRule,
) {
    private val slot = mutableStateOf<Pair<Int, @Composable () -> Unit>>(0 to {})
    private var mounted = false
    private var sequence = 0

    fun set(content: @Composable () -> Unit) {
        val next = ++sequence to content
        if (!mounted) {
            mounted = true
            slot.value = next
            composeRule.setContent {
                val (id, body) = slot.value
                key(id) { body() }
            }
        } else {
            composeRule.runOnIdle { slot.value = next }
            composeRule.waitForIdle()
        }
    }
}
