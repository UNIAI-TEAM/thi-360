package vn.unicom.thi360.core.legal

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.LinkAnnotation
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.Thi360Application
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.MutableConnectivityMonitor
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.feature.login.LoginTestTags
import vn.unicom.thi360.feature.recovery.OpenApiRecoveryGateway
import vn.unicom.thi360.feature.recovery.RegisterTestTags
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class LegalOfflineTest {

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
    @Config(qualifiers = "w390dp-h844dp")
    fun legalDocumentOpensOfflineFromRegisterWithZeroNetworkCalls() {
        val connectivity = MutableConnectivityMonitor(initialOnline = false)
        assertFalse(connectivity.online.value)

        val context = ApplicationProvider.getApplicationContext<Thi360Application>()
        val legalRepo = LegalRepository.create(context)
        val expectedTerms = legalRepo.load(LegalId.TERMS, AppLocale.VI).getOrThrow()

        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(),
            localeProvider = { AppLocale.VI },
        )
        val gateway = OpenApiRecoveryGateway(stack.publicAuthApi, stack.authenticatedAuthApi)

        composeRule.setContent {
            AppRoot(
                preferences = AppPreferences(locale = AppLocale.VI),
                onSelectLocale = {},
                onSelectThemeMode = {},
                sessionManager = stack.sessionManager,
                recoveryGateway = gateway,
                legalRepository = legalRepo,
            )
        }

        composeRule.onNodeWithTag(LoginTestTags.REGISTER_LINK).performScrollTo().performClick()
        val consent = composeRule.onNodeWithTag(RegisterTestTags.TERMS_CONSENT_TEXT).performScrollTo()
        val annotated = consent.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)!!.first()
        val termsLink = annotated.getLinkAnnotations(0, annotated.length).first().item as LinkAnnotation.Clickable

        composeRule.runOnIdle { termsLink.linkInteractionListener?.onClick(termsLink) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(LegalTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(LegalTestTags.TITLE).assertTextContains(expectedTerms.title)
        composeRule.onNodeWithTag(LegalTestTags.block(0)).assertIsDisplayed()
        assertEquals("Opening legal document offline must send zero HTTP requests", 0, server.totalRequestCount)
    }
}
