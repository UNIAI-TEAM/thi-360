package vn.unicom.thi360.smoke

import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import vn.unicom.thi360.MainActivity
import vn.unicom.thi360.Thi360Application
import vn.unicom.thi360.core.config.AppEnvironment
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.session.AppSessionContainer
import vn.unicom.thi360.core.storage.KeystoreTokenStore
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags

@RunWith(AndroidJUnit4::class)
class StartupBenchmarkTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private var fixtureServer: LoopbackRefreshServer? = null

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as Thi360Application
        KeystoreTokenStore.create(context).clear()
        app.biometricController.clearForSessionEnd()
        AppSessionContainer.setTestStack(context, null)
        runBlocking {
            app.preferencesRepository.setLocale(AppLocale.VI)
            app.preferencesRepository.setThemeMode(null)
        }
        fixtureServer = LoopbackRefreshServer().also { it.start() }
    }

    @After
    fun tearDown() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as Thi360Application
        KeystoreTokenStore.create(context).clear()
        app.biometricController.clearForSessionEnd()
        AppSessionContainer.setTestStack(context, null)
        fixtureServer?.stop()
        fixtureServer = null
    }

    @Test
    fun medianColdLaunchWithPersistedSessionIsWithinTwoSeconds() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as Thi360Application
        val server = checkNotNull(fixtureServer)
        val baseUrl = "http://127.0.0.1:${server.port}"
        val runsMs = mutableListOf<Long>()

        repeat(RUN_COUNT) { iteration ->
            val tokenStore = KeystoreTokenStore.create(context)
            tokenStore.write(
                Tokens(
                    accessToken = "startup-access-$iteration",
                    refreshToken = "startup-refresh-$iteration",
                    accessExpiresIn = 300,
                    refreshExpiresIn = 604_800,
                    mustChangePassword = false,
                    username = "qa.startup",
                ),
            )
            AppSessionContainer.setTestStack(context, null)

            val startNanos = SystemClock.elapsedRealtimeNanos()
            val stack = Thi360ApiFactory.createSessionStack(
                baseUrl = baseUrl,
                environment = AppEnvironment.environment,
                tokenStore = KeystoreTokenStore.create(context),
                localeProvider = { AppLocale.VI },
                onSessionCleared = { app.biometricController.clearForSessionEnd() },
                startLocked = false,
            )
            AppSessionContainer.setTestStack(context, stack)

            val scenario = ActivityScenario.launch(MainActivity::class.java)
            try {
                composeRule.waitUntil(timeoutMillis = LAUNCH_TIMEOUT_MS) {
                    isHomeInteractive()
                }
                val elapsedMs = (SystemClock.elapsedRealtimeNanos() - startNanos) / 1_000_000L
                runsMs.add(elapsedMs)
            } finally {
                scenario.close()
                AppSessionContainer.setTestStack(context, null)
            }
        }

        val sorted = runsMs.sorted()
        val medianMs = sorted[sorted.size / 2]
        val summary = "QA_ANDROID_STARTUP_MS runs=$runsMs median=$medianMs"
        Log.i(LOG_TAG, summary)
        println(summary)

        assertTrue(
            "Expected median cold launch <= ${MAX_MEDIAN_MS}ms, got ${medianMs}ms (runs=$runsMs)",
            medianMs <= MAX_MEDIAN_MS,
        )
    }

    private fun isHomeInteractive(): Boolean = runCatching {
        composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.Home)).assertIsDisplayed()
        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Account))
            .assertIsDisplayed()
            .assertIsEnabled()
        true
    }.getOrDefault(false)

    private class LoopbackRefreshServer {
        private val running = AtomicBoolean(false)
        private lateinit var serverSocket: ServerSocket
        private var workerThread: Thread? = null

        val port: Int
            get() = serverSocket.localPort

        fun start() {
            serverSocket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
            running.set(true)
            workerThread = Thread({
                while (running.get()) {
                    try {
                        val client = serverSocket.accept()
                        client.use { socket ->
                            socket.soTimeout = 2_000
                            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                            val requestLine = reader.readLine().orEmpty()
                            while (true) {
                                val headerLine = reader.readLine() ?: break
                                if (headerLine.isEmpty()) break
                            }
                            val bodyBytes = if (requestLine.contains("/api/v1/auth/mobile/refresh")) {
                                REFRESH_OK_JSON.toByteArray(StandardCharsets.UTF_8)
                            } else {
                                NOT_FOUND_JSON.toByteArray(StandardCharsets.UTF_8)
                            }
                            val status = if (requestLine.contains("/api/v1/auth/mobile/refresh")) {
                                "200 OK"
                            } else {
                                "404 Not Found"
                            }
                            val headers = (
                                "HTTP/1.1 $status\r\n" +
                                    "Content-Type: application/json; charset=utf-8\r\n" +
                                    "Content-Length: ${bodyBytes.size}\r\n" +
                                    "Connection: close\r\n\r\n"
                                ).toByteArray(StandardCharsets.UTF_8)
                            socket.getOutputStream().write(headers)
                            socket.getOutputStream().write(bodyBytes)
                            socket.getOutputStream().flush()
                        }
                    } catch (_: SocketException) {
                        if (!running.get()) break
                    } catch (_: Exception) {
                        if (!running.get()) break
                    }
                }
            }, "qa-startup-fixture-http").apply {
                isDaemon = true
                start()
            }
        }

        fun stop() {
            running.set(false)
            runCatching { serverSocket.close() }
            workerThread?.join(1_000)
            workerThread = null
        }

        companion object {
            private const val REFRESH_OK_JSON =
                """{"accessToken":"refreshed-access","refreshToken":"refreshed-refresh","accessExpiresIn":300,"refreshExpiresIn":604800,"mustChangePassword":false,"username":"qa.startup"}"""
            private const val NOT_FOUND_JSON =
                """{"code":"NOT_FOUND","message":"Not found","fieldErrors":[],"traceId":"0"}"""
        }
    }

    companion object {
        private const val LOG_TAG = "StartupBenchmarkTest"
        private const val RUN_COUNT = 5
        private const val MAX_MEDIAN_MS = 2_000L
        private const val LAUNCH_TIMEOUT_MS = 10_000L
    }
}
