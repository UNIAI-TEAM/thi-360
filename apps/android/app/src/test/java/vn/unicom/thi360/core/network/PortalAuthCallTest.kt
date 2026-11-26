package vn.unicom.thi360.core.network

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
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
import retrofit2.Response
import retrofit2.http.GET
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.session.SessionCleanup
import vn.unicom.thi360.core.session.SessionState
import vn.unicom.thi360.core.session.SignOutReason
import vn.unicom.thi360.core.storage.AccountScopedStoreProvider
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.feature.login.LoginTestTags
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class PortalAuthCallTest {

    private interface PortalProbeApi {
        @GET("/api/v1/me/profile")
        suspend fun probeProtected(): Response<String>
    }

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var server: ContractFakeServer

    @Before
    fun setUp() {
        server = ContractFakeServer().start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun refreshOnceThenRetry() {
        val expiredAccess = "expired-portal-access-1"
        val freshAccess = "fresh-portal-access-2"
        val refreshBody = ContractFixtureLoader.readAuthFixture("refresh-200.json")
            .replace("fixture-access-token", freshAccess)
        val profileBody = ContractFixtureLoader.readApiFixture("me-profile-200.json")

        server.respondDynamic("GET", "/api/v1/me/profile") { req ->
            if (req.getHeader("Authorization") == "Bearer $freshAccess") {
                ContractFakeServer.jsonResponse(200, profileBody)
            } else {
                ContractFakeServer.apiFixtureResponse(401, "me-401-unauthenticated.json")
            }
        }
        server.respondJson("POST", "/api/v1/auth/mobile/refresh", 200, refreshBody)

        val tokenStore = InMemoryTokenStore(
            Tokens(expiredAccess, "valid-refresh", 300, 604800, false, "student.demo", accountId = "acc-1"),
        )
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.VI },
        )

        val probeApi = stack.authenticatedService(PortalProbeApi::class.java)
        val response = runBlocking(Dispatchers.IO) { probeApi.probeProtected() }

        assertTrue("Expected retried protected call to succeed", response.isSuccessful)
        assertEquals(1, server.requestCount("POST", "/api/v1/auth/mobile/refresh"))
        val calls = server.requests("GET", "/api/v1/me/profile")
        assertEquals(2, calls.size)
        assertEquals("Bearer $expiredAccess", calls[0].getHeader("Authorization"))
        assertEquals("Bearer $freshAccess", calls[1].getHeader("Authorization"))
        assertEquals(SessionState.Active("student.demo", isOffline = false), stack.sessionManager.state.value)
    }

    @Test
    fun concurrentCallsSingleRefresh() {
        val expiredAccess = "expired-concurrent-1"
        val freshAccess = "fresh-concurrent-2"
        val refreshBody = ContractFixtureLoader.readAuthFixture("refresh-200.json")
            .replace("fixture-access-token", freshAccess)
        val profileBody = ContractFixtureLoader.readApiFixture("me-profile-200.json")
        val refreshCount = AtomicInteger(0)
        val arrived = CountDownLatch(3)

        server.respondDynamic("GET", "/api/v1/me/profile") { req ->
            if (req.getHeader("Authorization") == "Bearer $freshAccess") {
                ContractFakeServer.jsonResponse(200, profileBody)
            } else {
                arrived.countDown()
                arrived.await(5, TimeUnit.SECONDS)
                ContractFakeServer.apiFixtureResponse(401, "me-401-unauthenticated.json")
            }
        }
        server.respondDynamic("POST", "/api/v1/auth/mobile/refresh") {
            refreshCount.incrementAndGet()
            Thread.sleep(100)
            ContractFakeServer.jsonResponse(200, refreshBody)
        }

        val tokenStore = InMemoryTokenStore(
            Tokens(expiredAccess, "valid-refresh", 300, 604800, false, "student.demo", accountId = "acc-1"),
        )
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.VI },
        )
        val probeApi = stack.authenticatedService(PortalProbeApi::class.java)

        val responses = runBlocking {
            (1..3).map {
                async(Dispatchers.IO) { probeApi.probeProtected() }
            }.awaitAll()
        }

        assertEquals("Three concurrent 401 calls must trigger only one refresh", 1, refreshCount.get())
        assertTrue(responses.all { it.isSuccessful && it.code() == 200 })
        assertTrue(
            server.requests("GET", "/api/v1/me/profile").all {
                it.getHeader("Authorization")?.startsWith("Bearer ") == true
            },
        )
        assertEquals(SessionState.Active("student.demo", isOffline = false), stack.sessionManager.state.value)
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun expiredSessionKeepsAccountData() {
        val rootDir = Files.createTempDirectory("thi360-expired-keeps-data-").toFile()
        try {
            val accountId = "acc-keep-data"
            val tokenStore = InMemoryTokenStore(
                Tokens("stale-access", "revoked-refresh", 300, 604800, false, "student.demo", accountId = accountId),
            )
            val cleanupRuns = AtomicInteger(0)
            val sessionCleanup = SessionCleanup(rootDir)
            val storeProvider = AccountScopedStoreProvider(
                rootDir = rootDir,
                accountIdProvider = { tokenStore.read()?.accountId },
            )
            sessionCleanup.register { _ ->
                cleanupRuns.incrementAndGet()
            }

            // Write account-scoped draft before 401 session expiration.
            val storeBefore = checkNotNull(storeProvider.current())
            storeBefore.write("drafts", "attempt-1", """{"q1":"A"}""".toByteArray(Charsets.UTF_8))

            server.respondApiFixture("GET", "/api/v1/me/profile", 401, "me-401-session-revoked.json")
            server.respondAuthFixture("POST", "/api/v1/auth/mobile/refresh", 401, "refresh-401-session-revoked.json")

            val stack = Thi360ApiFactory.createSessionStack(
                baseUrl = server.url("/"),
                environment = Environment.DEV,
                tokenStore = tokenStore,
                localeProvider = { AppLocale.VI },
                sessionCleanup = sessionCleanup,
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

            val probeApi = stack.authenticatedService(PortalProbeApi::class.java)
            val resp = runBlocking(Dispatchers.IO) { probeApi.probeProtected() }
            assertEquals(401, resp.code())
            assertNull("Tokens must be cleared on 401 refresh", tokenStore.read())
            assertEquals(SessionState.SignedOut(SignOutReason.Expired), stack.sessionManager.state.value)
            assertEquals("Logout SessionCleanup must NOT run on 401 session expiration", 0, cleanupRuns.get())

            composeRule.waitUntil(timeoutMillis = 5_000) {
                runCatching {
                    composeRule.onNodeWithTag(LoginTestTags.NOTICE_BANNER).assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }
            composeRule.onNodeWithTag(LoginTestTags.NOTICE_BANNER)
                .assertTextContains("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.")

            // Same account logs back in -> previously saved AccountScopedStore data is still readable!
            server.respondAuthFixture("POST", "/api/v1/auth/mobile/login", 200, "login-200.json")
            server.respondJson(
                "GET",
                "/api/v1/me",
                200,
                ContractFixtureLoader.readApiFixture("me-200.json")
                    .replace("019320a0-0000-7000-8000-000000000001", accountId),
            )
            runBlocking(Dispatchers.IO) {
                stack.sessionManager.login("student.demo", "Pass1234!")
            }
            val storeAfterRelogin = checkNotNull(storeProvider.storeFor(accountId))
            val restored = storeAfterRelogin.read("drafts", "attempt-1")?.toString(Charsets.UTF_8)
            assertEquals("""{"q1":"A"}""", restored)
        } finally {
            rootDir.deleteRecursively()
        }
    }
}
