package vn.unicom.thi360.smoke

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import vn.unicom.thi360.MainActivity
import vn.unicom.thi360.R
import vn.unicom.thi360.Thi360Application
import vn.unicom.thi360.core.config.AppEnvironment
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.session.AppSessionContainer
import vn.unicom.thi360.core.storage.KeystoreTokenStore
import vn.unicom.thi360.feature.account.AccountTestTags
import vn.unicom.thi360.feature.lock.LockTestTags
import vn.unicom.thi360.feature.login.LoginTestTags
import vn.unicom.thi360.feature.recovery.PasswordTestTags
import vn.unicom.thi360.feature.recovery.RecoveryTestTags
import vn.unicom.thi360.feature.recovery.RegisterTestTags
import vn.unicom.thi360.feature.recovery.ResetLink
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags

@RunWith(AndroidJUnit4::class)
class SmokeAgainstBackendTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var apiBaseUrl: String
    private lateinit var mailpitBaseUrl: String
    private var scenario: ActivityScenario<MainActivity>? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        val args = InstrumentationRegistry.getArguments()
        apiBaseUrl = args.getString("apiBaseUrl")?.trim().takeUnless { it.isNullOrEmpty() }
            ?: AppEnvironment.apiBaseUrl
        mailpitBaseUrl = args.getString("mailpitBaseUrl")?.trim().takeUnless { it.isNullOrEmpty() }
            ?: DEFAULT_MAILPIT_URL

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as Thi360Application

        KeystoreTokenStore.create(context).clear()
        app.biometricController.clearForSessionEnd()
        runBlocking {
            app.preferencesRepository.setLocale(AppLocale.VI)
            app.preferencesRepository.setThemeMode(null)
        }

        if (apiBaseUrl == AppEnvironment.apiBaseUrl) {
            AppSessionContainer.setTestStack(context, null)
        } else {
            val stack = Thi360ApiFactory.createSessionStack(
                baseUrl = apiBaseUrl,
                environment = AppEnvironment.environment,
                tokenStore = KeystoreTokenStore.create(context),
                localeProvider = { AppLocale.VI },
                onSessionCleared = { app.biometricController.clearForSessionEnd() },
                startLocked = false,
            )
            AppSessionContainer.setTestStack(context, stack)
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
    fun endToEndStudentLifecycleAndAdminRoleRejection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val epochSeconds = System.currentTimeMillis() / 1_000L
        val fullName = "QA Android Student"
        val username = "qaand$epochSeconds"
        val email = "qaand$epochSeconds@thi360.local"
        val password1 = "QaSmoke1!a"
        val password2 = "QaSmoke2@b"
        val password3 = "QaSmoke3#c"
        val seenMessageIds = mutableSetOf<String>()

        val activeScenario = ActivityScenario.launch(MainActivity::class.java)
        scenario = activeScenario

        waitForTag(LoginTestTags.IDENTIFIER_FIELD)

        composeRule.onNodeWithTag(LoginTestTags.REGISTER_LINK).performScrollTo().performClick()
        waitForTag(RegisterTestTags.SCREEN_FORM)

        inputText(RegisterTestTags.FULL_NAME_FIELD, fullName)
        inputText(RegisterTestTags.USERNAME_FIELD, username)
        inputText(RegisterTestTags.EMAIL_FIELD, email)
        inputText(RegisterTestTags.PASSWORD_FIELD, password1)
        closeKeyboardIfOpen()
        composeRule.onNodeWithTag(RegisterTestTags.TERMS_CHECKBOX).performScrollTo().performClick()
        composeRule.onNodeWithTag(RegisterTestTags.SUBMIT_BUTTON).performScrollTo().performClick()

        waitForTag(RegisterTestTags.SCREEN_OTP)

        val otpMail = awaitMailpitMessage(recipientEmail = email, excludeIds = seenMessageIds) { detail ->
            extractOtpCode(detail) != null
        }
        seenMessageIds.add(otpMail.id)
        val otpCode = checkNotNull(extractOtpCode(otpMail)) {
            "Failed to parse 6-digit OTP code from Mailpit message"
        }

        inputText(RegisterTestTags.OTP_CODE_FIELD, otpCode)
        closeKeyboardIfOpen()
        composeRule.onNodeWithTag(RegisterTestTags.OTP_SUBMIT_BUTTON).performScrollTo().performClick()

        waitForAuthenticatedHome()

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Account)).performClick()
        waitForTag(AccountTestTags.PROFILE_FULL_NAME)
        composeRule.onNodeWithTag(AccountTestTags.PROFILE_FULL_NAME)
            .performScrollTo()
            .assertTextContains(fullName, substring = true)
        composeRule.onNodeWithTag(AccountTestTags.PROFILE_USERNAME)
            .performScrollTo()
            .assertTextContains(username, substring = true)
        composeRule.onNodeWithTag(AccountTestTags.PROFILE_EMAIL)
            .performScrollTo()
            .assertTextContains(email, substring = true)
        composeRule.onNodeWithTag(AccountTestTags.PROFILE_EMAIL_BADGE)
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains(localizedString(context, R.string.account_email_verified), substring = true)

        composeRule.onNodeWithTag(AccountTestTags.CHANGE_PASSWORD_ROW).performScrollTo().performClick()
        waitForTag(PasswordTestTags.OLD_PASSWORD_FIELD)
        inputText(PasswordTestTags.OLD_PASSWORD_FIELD, password1)
        inputText(PasswordTestTags.NEW_PASSWORD_FIELD, password2)
        inputText(PasswordTestTags.CONFIRM_PASSWORD_FIELD, password2)
        closeKeyboardIfOpen()
        composeRule.onNodeWithTag(PasswordTestTags.SUBMIT_BUTTON).performScrollTo().performClick()

        waitForTagPresent(PasswordTestTags.SUCCESS_BANNER)
        runCatching { composeRule.onNodeWithTag(PasswordTestTags.SUCCESS_BANNER).performScrollTo() }
        composeRule.onNodeWithTag(PasswordTestTags.SUCCESS_BANNER).assertIsDisplayed()
        if (isTagDisplayed(PasswordTestTags.CLOSE_BUTTON)) {
            composeRule.onNodeWithTag(PasswordTestTags.CLOSE_BUTTON).performClick()
            waitForTag(ShellTestTags.screenTitle(Destination.Account))
        }
        assertNotNull(
            "Session tokens must remain stored after voluntary password change",
            KeystoreTokenStore.create(context).read(),
        )

        logoutFromAccount()
        assertNull(
            "Session tokens must be cleared after logout",
            KeystoreTokenStore.create(context).read(),
        )

        loginWithCredentials(username, password2)
        waitForAuthenticatedHome()
        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Account)).performClick()
        waitForTag(ShellTestTags.screenTitle(Destination.Account))
        logoutFromAccount()

        composeRule.onNodeWithTag(LoginTestTags.FORGOT_PASSWORD_LINK).performScrollTo().performClick()
        waitForTag(RecoveryTestTags.FORGOT_EMAIL_FIELD)
        inputText(RecoveryTestTags.FORGOT_EMAIL_FIELD, email)
        closeKeyboardIfOpen()
        composeRule.onNodeWithTag(RecoveryTestTags.FORGOT_SUBMIT_BUTTON).performScrollTo().performClick()
        waitForTag(RecoveryTestTags.FORGOT_SCREEN_SENT)

        val resetMail = awaitMailpitMessage(recipientEmail = email, excludeIds = seenMessageIds) { detail ->
            extractResetToken(detail) != null
        }
        seenMessageIds.add(resetMail.id)
        val resetToken = checkNotNull(extractResetToken(resetMail)) {
            "Failed to parse password reset token from Mailpit message"
        }

        val allowedHost = ResetLink.allowedHostFromWebBaseUrl(AppEnvironment.webBaseUrl)
        val resetUri = Uri.parse("https://$allowedHost/reset-password?token=${Uri.encode(resetToken)}")
        activeScenario.onActivity { activity ->
            val resetIntent = Intent(Intent.ACTION_VIEW, resetUri).apply {
                setClass(activity, MainActivity::class.java)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            activity.startActivity(resetIntent)
        }

        waitForTag(PasswordTestTags.LINK_BANNER)
        inputText(PasswordTestTags.NEW_PASSWORD_FIELD, password3)
        inputText(PasswordTestTags.CONFIRM_PASSWORD_FIELD, password3)
        closeKeyboardIfOpen()
        composeRule.onNodeWithTag(PasswordTestTags.SUBMIT_BUTTON).performScrollTo().performClick()

        waitForTag(LoginTestTags.NOTICE_BANNER)
        waitForTag(LoginTestTags.IDENTIFIER_FIELD)

        loginWithCredentials(username, password3)
        waitForAuthenticatedHome()
        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Account)).performClick()
        waitForTag(ShellTestTags.screenTitle(Destination.Account))
        logoutFromAccount()

        loginWithCredentials(MANAGE_ACCOUNT_USERNAME, MANAGE_ACCOUNT_PASSWORD)
        waitForTag(LoginTestTags.ROLE_NOTICE_SCREEN)
        composeRule.onNodeWithTag(LoginTestTags.ROLE_NOTICE_BODY)
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains(MANAGE_ACCOUNT_USERNAME, substring = true)
        assertNull(
            "MANAGE account must never persist tokens in KeystoreTokenStore",
            KeystoreTokenStore.create(context).read(),
        )

        composeRule.onNodeWithTag(LoginTestTags.ROLE_NOTICE_SWITCH_ACCOUNT).performClick()
        waitForTag(LoginTestTags.IDENTIFIER_FIELD)
    }

    private fun loginWithCredentials(identifier: String, password: String) {
        waitForTag(LoginTestTags.IDENTIFIER_FIELD)
        inputText(LoginTestTags.IDENTIFIER_FIELD, identifier)
        inputText(LoginTestTags.PASSWORD_FIELD, password)
        closeKeyboardIfOpen()
        composeRule.onNodeWithTag(LoginTestTags.SUBMIT_BUTTON).performScrollTo().performClick()
    }

    private fun waitForAuthenticatedHome() {
        composeRule.waitUntil(timeoutMillis = NETWORK_TIMEOUT_MS) {
            isTagDisplayed(ShellTestTags.screenTitle(Destination.Home)) ||
                isTagDisplayed(LockTestTags.ENABLE_PROMPT_LATER)
        }
        if (isTagDisplayed(LockTestTags.ENABLE_PROMPT_LATER)) {
            composeRule.onNodeWithTag(LockTestTags.ENABLE_PROMPT_LATER).performClick()
        }
        waitForTag(ShellTestTags.screenTitle(Destination.Home))
    }

    private fun logoutFromAccount() {
        composeRule.onNodeWithTag(AccountTestTags.LOGOUT_BUTTON).performScrollTo().performClick()
        waitForTag(AccountTestTags.LOGOUT_CONFIRM_BUTTON)
        composeRule.onNodeWithTag(AccountTestTags.LOGOUT_CONFIRM_BUTTON).performClick()
        waitForTag(LoginTestTags.IDENTIFIER_FIELD)
    }

    private fun inputText(tag: String, value: String) {
        composeRule.onNodeWithTag(tag).performScrollTo().performTextInput(value)
    }

    private fun closeKeyboardIfOpen() {
        runCatching { Espresso.closeSoftKeyboard() }
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

    private fun waitForTag(tag: String, timeoutMillis: Long = NETWORK_TIMEOUT_MS) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            isTagDisplayed(tag)
        }
    }

    private fun waitForTagPresent(tag: String, timeoutMillis: Long = NETWORK_TIMEOUT_MS) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            runCatching {
                composeRule.onNodeWithTag(tag).assertExists()
                true
            }.getOrDefault(false)
        }
    }

    private fun isTagDisplayed(tag: String): Boolean = runCatching {
        composeRule.onNodeWithTag(tag).assertIsDisplayed()
        true
    }.getOrDefault(false)

    private data class MailpitMessage(
        val id: String,
        val text: String,
        val html: String,
    )

    private fun awaitMailpitMessage(
        recipientEmail: String,
        excludeIds: Set<String>,
        matcher: (MailpitMessage) -> Boolean,
    ): MailpitMessage {
        val deadline = System.currentTimeMillis() + MAILPIT_TIMEOUT_MS
        val normalizedBase = mailpitBaseUrl.trimEnd('/')
        var lastError: String? = null

        while (System.currentTimeMillis() < deadline) {
            try {
                val listRequest = Request.Builder()
                    .url("$normalizedBase/api/v1/messages?limit=50")
                    .get()
                    .build()
                val listBody = httpClient.newCall(listRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        lastError = "Mailpit list returned HTTP ${response.code}"
                        null
                    } else {
                        response.body.string()
                    }
                }
                if (listBody != null) {
                    val root = json.parseToJsonElement(listBody).jsonObject
                    val messages = root["messages"]?.jsonArray.orEmpty()
                    for (entry in messages) {
                        val obj = entry.jsonObject
                        val id = obj["ID"]?.jsonPrimitive?.contentOrNull ?: continue
                        if (id in excludeIds) continue
                        if (!hasRecipient(obj, recipientEmail)) continue

                        val detail = fetchMailpitMessage(normalizedBase, id) ?: continue
                        if (matcher(detail)) {
                            return detail
                        }
                    }
                }
            } catch (error: Exception) {
                lastError = "${error::class.java.simpleName}: ${error.message}"
            }
            Thread.sleep(MAILPIT_POLL_INTERVAL_MS)
        }
        fail("Timed out waiting for Mailpit email to $recipientEmail (lastError=${lastError ?: "none"})")
        error("unreachable")
    }

    private fun hasRecipient(messageObj: JsonObject, recipientEmail: String): Boolean {
        val recipients = messageObj["To"]?.jsonArray ?: return false
        return recipients.any { recipient ->
            val address = recipient.jsonObject["Address"]?.jsonPrimitive?.contentOrNull
            address.equals(recipientEmail, ignoreCase = true)
        }
    }

    private fun fetchMailpitMessage(normalizedBase: String, id: String): MailpitMessage? {
        val request = Request.Builder()
            .url("$normalizedBase/api/v1/message/$id")
            .get()
            .build()
        val body = httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body.string()
        }
        val obj = json.parseToJsonElement(body).jsonObject
        return MailpitMessage(
            id = id,
            text = obj["Text"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            html = obj["HTML"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }

    private fun extractOtpCode(message: MailpitMessage): String? {
        OTP_REGEX.find(message.text)?.groupValues?.get(1)?.let { return it }
        // Strip HTML tags and CSS hex colors before scanning HTML fallback so `#047857` is never misread as an OTP.
        val strippedHtml = HTML_TAG_REGEX.replace(message.html, " ")
        return OTP_REGEX.find(strippedHtml)?.groupValues?.get(1)
    }

    private fun extractResetToken(message: MailpitMessage): String? {
        val candidate = RESET_TOKEN_REGEX.find(message.text)?.groupValues?.get(1)
            ?: RESET_TOKEN_REGEX.find(message.html.replace("&amp;", "&"))?.groupValues?.get(1)
            ?: return null
        return URLDecoder.decode(candidate, StandardCharsets.UTF_8.name())
            .trim()
            .takeIf { it.isNotEmpty() }
    }

    companion object {
        private const val DEFAULT_MAILPIT_URL = "http://10.0.2.2:8025"
        private const val NETWORK_TIMEOUT_MS = 20_000L
        private const val MAILPIT_TIMEOUT_MS = 30_000L
        private const val MAILPIT_POLL_INTERVAL_MS = 500L
        private const val MANAGE_ACCOUNT_USERNAME = "ops.admin"
        private const val MANAGE_ACCOUNT_PASSWORD = "Thi360@dev"

        private val OTP_REGEX = Regex("""(?<![#\d])(\d{6})(?!\d)""")
        private val HTML_TAG_REGEX = Regex("""<[^>]+>""")
        private val RESET_TOKEN_REGEX = Regex("""/reset-password\?[^\s"'<>]*\btoken=([A-Za-z0-9._~%-]+)""")
    }
}
