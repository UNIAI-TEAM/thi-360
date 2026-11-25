package vn.unicom.thi360.core.session

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.ContractFixtureLoader
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.feature.login.LoginTestTags
import vn.unicom.thi360.shell.AppRoot

@RunWith(RobolectricTestRunner::class)
class TokenRefreshTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun singleFlight() {
        val expiredAccessToken = "expired-access-token-1"
        val refreshedAccessToken = "refreshed-access-token-2"
        val refreshCount = AtomicInteger(0)
        val initialUnauthorizedArrived = CountDownLatch(3)

        val refreshBody = ContractFixtureLoader.readAuthFixture("refresh-200.json")
            .replace("fixture-access-token", refreshedAccessToken)
        val meSuccessBody = ContractFixtureLoader.readApiFixture("me-200.json")
        val meUnauthorizedBody = ContractFixtureLoader.readApiFixture("me-401-unauthenticated.json")

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/api/v1/me" -> {
                    val auth = request.getHeader("Authorization")
                    if (auth == "Bearer $refreshedAccessToken") {
                        ContractFixtureLoader.jsonResponse(200, meSuccessBody)
                    } else {
                        initialUnauthorizedArrived.countDown()
                        initialUnauthorizedArrived.await(5, TimeUnit.SECONDS)
                        ContractFixtureLoader.jsonResponse(401, meUnauthorizedBody)
                    }
                }

                "/api/v1/auth/mobile/refresh" -> {
                    refreshCount.incrementAndGet()
                    Thread.sleep(150)
                    ContractFixtureLoader.jsonResponse(200, refreshBody)
                }

                else -> MockResponse().setResponseCode(404)
            }
        }

        val tokenStore = InMemoryTokenStore(
            Tokens(
                accessToken = expiredAccessToken,
                refreshToken = "valid-refresh-token",
                accessExpiresIn = 300,
                refreshExpiresIn = 604800,
                mustChangePassword = false,
                username = "student.demo",
            ),
        )
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/").toString(),
            environment = Environment.DEV,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.VI },
        )

        val responses = runBlocking {
            (1..3).map {
                async(Dispatchers.IO) {
                    stack.authenticatedAuthApi.me()
                }
            }.awaitAll()
        }

        assertEquals(
            "Three concurrent 401 calls must trigger exactly one mobile/refresh request",
            1,
            refreshCount.get(),
        )
        assertTrue("All 3 retried calls must succeed with 200", responses.all { it.isSuccessful && it.code() == 200 })
        assertTrue(responses.all { it.body()?.username == "student.demo" })
        assertEquals(refreshedAccessToken, tokenStore.read()?.accessToken)
        assertEquals(SessionState.Active(username = "student.demo", isOffline = false), stack.sessionManager.state.value)
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun expiredSessionSignsOut() {
        val meUnauthorizedBody = ContractFixtureLoader.readApiFixture("me-401-session-revoked.json")
        val refreshExpiredBody = ContractFixtureLoader.readAuthFixture("refresh-401-session-revoked.json")

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/api/v1/me" -> ContractFixtureLoader.jsonResponse(401, meUnauthorizedBody)
                "/api/v1/auth/mobile/refresh" -> ContractFixtureLoader.jsonResponse(401, refreshExpiredBody)
                else -> MockResponse().setResponseCode(404)
            }
        }

        val tokenStore = InMemoryTokenStore(
            Tokens(
                accessToken = "stale-access",
                refreshToken = "revoked-refresh",
                accessExpiresIn = 300,
                refreshExpiresIn = 604800,
                mustChangePassword = false,
                username = "student.demo",
            ),
        )
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/").toString(),
            environment = Environment.DEV,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.VI },
        )

        composeRule.setContent {
            AppRoot(
                preferences = AppPreferences(locale = AppLocale.VI),
                onSelectLocale = {},
                onSelectThemeMode = {},
                sessionManager = stack.sessionManager,
                validateOnLaunch = false,
            )
        }

        val response = runBlocking(Dispatchers.IO) { stack.authenticatedAuthApi.me() }
        assertEquals(401, response.code())
        assertNull("401 on mobile/refresh must clear stored tokens", tokenStore.read())
        assertEquals(
            SessionState.SignedOut(SignOutReason.Expired),
            stack.sessionManager.state.value,
        )

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(LoginTestTags.NOTICE_BANNER).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(LoginTestTags.NOTICE_BANNER)
            .assertTextContains("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.")
        composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD).assertIsDisplayed()
    }
}
