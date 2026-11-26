package vn.unicom.thi360.smoke

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import vn.unicom.thi360.MainActivity
import vn.unicom.thi360.R
import vn.unicom.thi360.Thi360Application
import vn.unicom.thi360.core.config.AppEnvironment
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.session.AppSessionContainer
import vn.unicom.thi360.core.storage.KeystoreTokenStore
import vn.unicom.thi360.feature.login.LoginTestTags

@RunWith(AndroidJUnit4::class)
class DemoHostReachabilityTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

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
    }

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as Thi360Application
        KeystoreTokenStore.create(context).clear()
        app.biometricController.clearForSessionEnd()
        AppSessionContainer.setTestStack(context, null)
    }

    @Test
    fun demoBuildReachesDemoHostOverHttpsAndReceivesHttpResponses() {
        assertEquals(Environment.DEMO, AppEnvironment.environment)
        assertEquals(DEMO_BASE_URL, AppEnvironment.apiBaseUrl)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val networkErrorText = localizedString(context, R.string.login_error_network)

        scenario = ActivityScenario.launch(MainActivity::class.java)

        composeRule.waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            isTagDisplayed(LoginTestTags.IDENTIFIER_FIELD)
        }
        composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD)
            .performScrollTo()
            .performTextInput(PROBE_IDENTIFIER)
        composeRule.onNodeWithTag(LoginTestTags.PASSWORD_FIELD)
            .performScrollTo()
            .performTextInput(PROBE_PASSWORD)
        runCatching { Espresso.closeSoftKeyboard() }
        composeRule.onNodeWithTag(LoginTestTags.SUBMIT_BUTTON)
            .performScrollTo()
            .performClick()

        composeRule.waitUntil(timeoutMillis = UI_TIMEOUT_MS) {
            isTagDisplayed(LoginTestTags.ERROR_BANNER)
        }

        val bannerNode = composeRule.onNodeWithTag(LoginTestTags.ERROR_BANNER).fetchSemanticsNode()
        val bannerText = bannerNode.config.getOrNull(SemanticsProperties.Text)
            .orEmpty()
            .joinToString(" ") { it.text }
            .trim()
        assertTrue("Expected non-blank error banner from demo host", bannerText.isNotBlank())
        assertNotEquals(
            "Demo host must return an HTTP response rather than a network failure",
            networkErrorText,
            bannerText,
        )

        val loginBody = """{"identifier":"$PROBE_IDENTIFIER","password":"$PROBE_PASSWORD"}"""
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val loginRequest = Request.Builder()
            .url("$DEMO_BASE_URL/api/v1/auth/mobile/login")
            .post(loginBody)
            .build()
        val loginStatus = httpClient.newCall(loginRequest).execute().use { it.code }
        assertTrue("Expected valid HTTP status for mobile login probe, got $loginStatus", loginStatus in 100..599)

        val appearanceRequest = Request.Builder()
            .url("$DEMO_BASE_URL/api/v1/appearance")
            .get()
            .build()
        val appearanceStatus = httpClient.newCall(appearanceRequest).execute().use { it.code }
        assertTrue(
            "Expected valid HTTP status for appearance probe, got $appearanceStatus",
            appearanceStatus in 100..599,
        )
    }

    private fun localizedString(
        context: Context,
        @StringRes resId: Int,
        languageTag: String = "vi",
    ): String {
        val config = Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(languageTag))
        }
        return context.createConfigurationContext(config).getString(resId)
    }

    private fun isTagDisplayed(tag: String): Boolean = runCatching {
        composeRule.onNodeWithTag(tag).assertIsDisplayed()
        true
    }.getOrDefault(false)

    companion object {
        private const val DEMO_BASE_URL = "https://thi360.zone17.click"
        private const val PROBE_IDENTIFIER = "qa.demo.probe"
        private const val PROBE_PASSWORD = "ProbePass1!"
        private const val UI_TIMEOUT_MS = 20_000L
    }
}
