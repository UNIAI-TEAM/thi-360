package vn.unicom.thi360.feature.recovery

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import java.io.File
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import vn.unicom.thi360.MainActivity
import vn.unicom.thi360.Thi360Application
import vn.unicom.thi360.core.config.AppEnvironment
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.feature.login.LoginTestTags
import vn.unicom.thi360.feature.password.NewPasswordMode
import vn.unicom.thi360.shell.AppRoot

@RunWith(RobolectricTestRunner::class)
class ResetTokenHandlingTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        ShadowLog.clear()
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun resetTokenIsNeverLoggedOrPersistedAndCannotBeReusedAfterLeavingScreen() {
        val secretResetToken = "reset-secret-token-xyz-987654"
        val host = ResetLink.allowedHostFromWebBaseUrl(AppEnvironment.webBaseUrl)
        val link = ResetLink(secretResetToken)
        val mode = NewPasswordMode.FromLink(secretResetToken)

        assertFalse("ResetLink.toString must redact token", link.toString().contains(secretResetToken))
        assertTrue(link.toString().contains("[REDACTED]"))
        assertFalse("NewPasswordMode.FromLink.toString must redact token", mode.toString().contains(secretResetToken))
        assertTrue(mode.toString().contains("[REDACTED]"))

        val tokenStore = InMemoryTokenStore()
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/").toString(),
            environment = Environment.DEMO,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.VI },
        )
        val gateway = OpenApiRecoveryGateway(stack.publicAuthApi, stack.authenticatedAuthApi)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://$host/reset-password?token=$secretResetToken"))

        val restorationTester = StateRestorationTester(composeRule)

        restorationTester.setContent {
            AppRoot(
                preferences = AppPreferences(locale = AppLocale.VI),
                onSelectLocale = {},
                onSelectThemeMode = {},
                sessionManager = stack.sessionManager,
                validateOnLaunch = false,
                recoveryGateway = gateway,
                initialIntent = intent,
            )
        }

        composeRule.onNodeWithTag(PasswordTestTags.SCREEN).assertIsDisplayed()

        // Leave reset screen via Close button -> token must be evicted and AppRoot itself must suppress
        // replay across saved-instance-state restore even when the same initialIntent is still supplied.
        composeRule.onNodeWithTag(PasswordTestTags.CLOSE_BUTTON).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD).assertIsDisplayed()
        composeRule.onNodeWithTag(PasswordTestTags.SCREEN).assertDoesNotExist()

        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD).assertIsDisplayed()
        composeRule.onNodeWithTag(PasswordTestTags.SCREEN).assertDoesNotExist()
        assertEquals(0, server.requestCount)

        // Verify token is absent from all logs and all app storage directories.
        val allLogLines = ShadowLog.getLogs().flatMap { item ->
            listOfNotNull(item.msg, item.throwable?.stackTraceToString())
        }
        for (line in allLogLines) {
            assertFalse("Reset token leaked into logs: $line", line.contains(secretResetToken))
        }

        val appContext = ApplicationProvider.getApplicationContext<Thi360Application>()
        val storageDirs = listOfNotNull(
            File(appContext.applicationInfo.dataDir, "shared_prefs"),
            File(appContext.filesDir, "datastore"),
            appContext.noBackupFilesDir,
        )
        for (dir in storageDirs) {
            if (!dir.isDirectory) continue
            dir.walkTopDown().filter { it.isFile }.forEach { file ->
                val raw = file.readBytes().toString(Charsets.ISO_8859_1)
                assertFalse("Reset token persisted on disk in ${file.name}", raw.contains(secretResetToken))
            }
        }
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun dismissedResetLinkDoesNotReplayAfterActivityRecreate() {
        val context = ApplicationProvider.getApplicationContext<Thi360Application>()
        val host = ResetLink.allowedHostFromWebBaseUrl(AppEnvironment.webBaseUrl)
        val launchIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://$host/reset-password?token=recreate-ephemeral-token"),
        ).setClass(context, MainActivity::class.java)

        ActivityScenario.launch<MainActivity>(launchIntent).use { scenario ->
            composeRule.waitUntil(timeoutMillis = 5_000) {
                runCatching {
                    composeRule.onNodeWithTag(PasswordTestTags.SCREEN).assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }
            scenario.onActivity { activity ->
                assertNull("Consumed reset intent URI must be cleared in place", activity.intent?.data)
            }

            composeRule.onNodeWithTag(PasswordTestTags.CLOSE_BUTTON).performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                runCatching {
                    composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD).assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }
            composeRule.onNodeWithTag(PasswordTestTags.SCREEN).assertDoesNotExist()

            scenario.recreate()
            composeRule.waitForIdle()

            composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD).assertIsDisplayed()
            composeRule.onNodeWithTag(PasswordTestTags.SCREEN).assertDoesNotExist()
        }
    }
}
