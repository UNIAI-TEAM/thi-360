package vn.unicom.thi360.feature.recovery

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import okhttp3.mockwebserver.MockWebServer
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
import vn.unicom.thi360.BuildConfig
import vn.unicom.thi360.MainActivity
import vn.unicom.thi360.Thi360Application
import vn.unicom.thi360.core.config.AppEnvironment
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.session.SessionState
import vn.unicom.thi360.core.theme.BrandPalette
import vn.unicom.thi360.shell.ActiveResetOverlay
import vn.unicom.thi360.shell.AppSessionContent

@RunWith(RobolectricTestRunner::class)
class ManifestAppLinksTest {

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

    private fun expectedFlavorHost(): String = when (BuildConfig.FLAVOR) {
        "dev" -> "10.0.2.2"
        "demo" -> "thi360.zone17.click"
        else -> error("Unexpected flavor: ${BuildConfig.FLAVOR}")
    }

    @Test
    fun manifestMatchesFlavorResetPasswordHttpsAndRejectsOtherPathsAndHosts() {
        val context = ApplicationProvider.getApplicationContext<Thi360Application>()
        val pm = context.packageManager
        val host = expectedFlavorHost()
        assertEquals(host, ResetLink.allowedHostFromWebBaseUrl(AppEnvironment.webBaseUrl))

        val validIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://$host/reset-password?token=abc"),
        ).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            setPackage(context.packageName)
        }
        val resolvedValid = pm.queryIntentActivities(validIntent, 0)
        assertEquals(
            "Expected MainActivity to handle https://$host/reset-password",
            1,
            resolvedValid.size,
        )
        assertEquals(MainActivity::class.java.name, resolvedValid.single().activityInfo.name)

        val rejectedUris = listOf(
            "https://$host/login",
            "https://$host/home",
            "https://$host/reset-password/extra?token=abc",
            "http://$host/reset-password?token=abc",
            "https://evil.example/reset-password?token=abc",
        )
        for (raw in rejectedUris) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(raw)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                setPackage(context.packageName)
            }
            val resolved = pm.queryIntentActivities(intent, 0)
            assertTrue("App must NOT claim $raw, got $resolved", resolved.isEmpty())
            assertNull("ResetLink.parse must reject $raw", ResetLink.parse(Uri.parse(raw), host))
        }
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun resetLinkBypassesLockedStateAndOpensNewPasswordScreen() {
        val host = expectedFlavorHost()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://$host/reset-password?token=abc"))
        val parsed = ResetLink.parseIntent(intent, host) as ResetLinkIntentResult.Valid
        assertEquals("abc", parsed.link.token)

        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/").toString(),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(),
            localeProvider = { AppLocale.VI },
        )
        val gateway = OpenApiRecoveryGateway(stack.publicAuthApi, stack.authenticatedAuthApi)

        composeRule.setContent {
            AppSessionContent(
                sessionState = SessionState.Locked(username = "student.demo"),
                sessionManager = stack.sessionManager,
                recoveryGateway = gateway,
                preferences = AppPreferences(locale = AppLocale.VI),
                brandPalette = BrandPalette.Default,
                onSelectLocale = {},
                onSelectThemeMode = {},
                navController = null,
                startDestination = null,
                accountContent = null,
                extraDestinations = {},
                onForgotPasswordClick = null,
                onRegisterClick = null,
                lockedContent = {
                    Text(
                        text = "Locked",
                        modifier = Modifier.testTag("lock_screen_content"),
                    )
                },
                activeResetOverlay = ActiveResetOverlay.Valid(parsed.link),
                onDismissResetOverlay = {},
                onMarkResetLinkInvalid = {},
                openForgotPasswordOverlay = false,
                onOpenForgotPasswordOverlay = {},
                clockMillis = System::currentTimeMillis,
            )
        }

        composeRule.onNodeWithTag(PasswordTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(PasswordTestTags.LINK_BANNER).assertIsDisplayed()
        composeRule.onNodeWithTag(PasswordTestTags.NEW_PASSWORD_FIELD).assertIsDisplayed()
        composeRule.onNodeWithTag("lock_screen_content").assertDoesNotExist()
    }
}
