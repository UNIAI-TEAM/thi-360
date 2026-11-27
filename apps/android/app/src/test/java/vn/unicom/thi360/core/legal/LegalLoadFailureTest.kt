package vn.unicom.thi360.core.legal

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import vn.unicom.thi360.Thi360Application
import vn.unicom.thi360.core.config.AppEnvironment
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.login.LoginTestTags
import vn.unicom.thi360.feature.recovery.OpenApiRecoveryGateway
import vn.unicom.thi360.feature.recovery.RegisterTestTags
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class LegalLoadFailureTest {

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

    private fun brokenLegalRepository(): LegalRepository = LegalRepository { path ->
        when {
            path.endsWith("terms.vi.json") -> throw FileNotFoundException("Missing $path")
            path.endsWith("privacy.vi.json") -> ByteArrayInputStream("""{"id":"privacy","blocks":[{"type":"unknown_block"}]}""".toByteArray())
            else -> throw FileNotFoundException("Missing $path")
        }
    }

    @Test
    fun repositoryReturnsTypedMissingAndMalformedErrorsWithoutThrowing() {
        val repo = brokenLegalRepository()

        val missing = repo.load(LegalId.TERMS, AppLocale.VI)
        assertTrue(missing.isFailure)
        assertTrue(missing.exceptionOrNull() is LegalLoadError.Missing)

        val malformed = repo.load(LegalId.PRIVACY, AppLocale.VI)
        assertTrue(malformed.isFailure)
        assertTrue(malformed.exceptionOrNull() is LegalLoadError.Malformed)
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun brokenLegalShowsControlledErrorWithOpenOnWebAndNoRetryButton() {
        val repo = brokenLegalRepository()
        val app = ApplicationProvider.getApplicationContext<Thi360Application>()

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                LegalDocumentScreen(
                    id = LegalId.TERMS,
                    repository = repo,
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(LegalTestTags.ERROR_CONTAINER).assertIsDisplayed()
        composeRule.onNodeWithTag(LegalTestTags.ERROR_TITLE).assertTextContains("Đã có lỗi máy chủ")
        composeRule.onNodeWithTag(LegalTestTags.ERROR_DESCRIPTION)
            .assertTextContains("Đã xảy ra lỗi không mong muốn khi xử lý yêu cầu. Vui lòng thử lại sau.")
        composeRule.onNodeWithText("Thử lại").assertDoesNotExist()

        composeRule.onNodeWithTag(LegalTestTags.OPEN_ON_WEB).assertIsDisplayed().performClick()
        val started = Shadows.shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals(LegalId.TERMS.webUrl(AppEnvironment.webBaseUrl), started.data?.toString())
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun registerStillSubmitsWhenLegalAssetsAreBroken() {
        val repo = brokenLegalRepository()
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(),
            localeProvider = { AppLocale.VI },
        )
        val gateway = OpenApiRecoveryGateway(stack.publicAuthApi, stack.authenticatedAuthApi)
        server.respondApiFixture("POST", "/api/v1/auth/register", 200, "register-200.json")

        composeRule.setContent {
            AppRoot(
                preferences = AppPreferences(locale = AppLocale.VI),
                onSelectLocale = {},
                onSelectThemeMode = {},
                sessionManager = stack.sessionManager,
                recoveryGateway = gateway,
                legalRepository = repo,
            )
        }

        composeRule.onNodeWithTag(LoginTestTags.REGISTER_LINK).performScrollTo().performClick()
        composeRule.onNodeWithTag(RegisterTestTags.FULL_NAME_FIELD).performScrollTo().performTextInput("Nguyễn Văn An")
        composeRule.onNodeWithTag(RegisterTestTags.USERNAME_FIELD).performScrollTo().performTextInput("student.brokenlegal")
        composeRule.onNodeWithTag(RegisterTestTags.EMAIL_FIELD).performScrollTo().performTextInput("broken@thi360.vn")
        composeRule.onNodeWithTag(RegisterTestTags.PASSWORD_FIELD).performScrollTo().performTextInput("StudentPass1!")
        composeRule.onNodeWithTag(RegisterTestTags.TERMS_CHECKBOX).performScrollTo().performClick()
        composeRule.onNodeWithTag(RegisterTestTags.SUBMIT_BUTTON).performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(RegisterTestTags.SCREEN_OTP).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        assertEquals(1, server.requestCount("POST", "/api/v1/auth/register"))
    }
}
