package vn.unicom.thi360.feature.recovery

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import vn.unicom.thi360.MainActivity
import vn.unicom.thi360.core.config.AppEnvironment
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.session.AppSessionContainer
import vn.unicom.thi360.core.session.SessionState
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.theme.BrandPalette
import vn.unicom.thi360.feature.login.LoginTestTags
import vn.unicom.thi360.shell.ActiveResetOverlay
import vn.unicom.thi360.shell.AppSessionContent
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ResetLinkInstrumentedTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun resetPasswordViewIntentResolvesAndOpensNewPasswordScreenWhileOtherPathsAreRejected() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val host = ResetLink.allowedHostFromWebBaseUrl(AppEnvironment.webBaseUrl)

        val validIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://$host/reset-password?token=abc"),
        ).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            setPackage(context.packageName)
        }
        val resolved = context.packageManager.queryIntentActivities(validIntent, 0)
        assertEquals(1, resolved.size)
        assertEquals(MainActivity::class.java.name, resolved.single().activityInfo.name)

        val otherPathIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://$host/other-path"),
        ).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            setPackage(context.packageName)
        }
        assertTrue(context.packageManager.queryIntentActivities(otherPathIntent, 0).isEmpty())

        val otherHostIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://evil.example/reset-password?token=abc"),
        ).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            setPackage(context.packageName)
        }
        assertTrue(context.packageManager.queryIntentActivities(otherHostIntent, 0).isEmpty())

        val launchIntent = Intent(validIntent).setClass(context, MainActivity::class.java)
        ActivityScenario.launch<MainActivity>(launchIntent).use { scenario ->
            composeRule.waitUntil(timeoutMillis = 10_000) {
                runCatching {
                    composeRule.onNodeWithTag(PasswordTestTags.SCREEN).assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }
            composeRule.onNodeWithTag(PasswordTestTags.LINK_BANNER).assertIsDisplayed()
            composeRule.onNodeWithTag(PasswordTestTags.NEW_PASSWORD_FIELD).assertIsDisplayed()
            scenario.onActivity { activity ->
                assertTrue(
                    "Consumed reset intent data must be cleared from Activity",
                    activity.intent?.data == null,
                )
            }

            composeRule.onNodeWithTag(PasswordTestTags.CLOSE_BUTTON).performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                runCatching {
                    composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD).assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }
            composeRule.onNodeWithTag(PasswordTestTags.SCREEN).assertDoesNotExist()

            scenario.recreate()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                runCatching {
                    composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD).assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }
            composeRule.onNodeWithTag(PasswordTestTags.SCREEN).assertDoesNotExist()
        }
    }
}

@RunWith(AndroidJUnit4::class)
class ResetLinkOverlayInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun resetLinkBypassesLockedSessionState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sessionManager = AppSessionContainer.get(context).sessionManager
        val dummyGateway = object : RecoveryGateway {
            override suspend fun startRegistration(
                fullName: String,
                username: String,
                email: String,
                password: String,
                termsVersion: String,
                registrationId: UUID?,
            ): ApiResult<RegistrationStartData> = error("unused")

            override suspend fun resendRegistrationCode(registrationId: UUID): ApiResult<RegistrationStartData> = error("unused")
            override suspend fun verifyRegistration(registrationId: UUID, code: String, username: String?): ApiResult<Tokens> = error("unused")
            override suspend fun forgotPassword(email: String): ApiResult<Unit> = error("unused")
            override suspend fun resetPassword(token: String, newPassword: String): ApiResult<Unit> = ApiResult.Success(Unit)
            override suspend fun changePassword(oldPassword: String, newPassword: String): ApiResult<Unit> = error("unused")
        }

        composeRule.setContent {
            AppSessionContent(
                sessionState = SessionState.Locked(username = "student.demo"),
                sessionManager = sessionManager,
                recoveryGateway = dummyGateway,
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
                        text = "Biometric Lock",
                        modifier = Modifier.testTag("lock_overlay"),
                    )
                },
                activeResetOverlay = ActiveResetOverlay.Valid(ResetLink("abc")),
                onDismissResetOverlay = {},
                onMarkResetLinkInvalid = {},
                openForgotPasswordOverlay = false,
                onOpenForgotPasswordOverlay = {},
                clockMillis = System::currentTimeMillis,
            )
        }

        composeRule.onNodeWithTag(PasswordTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(PasswordTestTags.LINK_BANNER).assertIsDisplayed()
        composeRule.onNodeWithTag("lock_overlay").assertDoesNotExist()
    }
}
