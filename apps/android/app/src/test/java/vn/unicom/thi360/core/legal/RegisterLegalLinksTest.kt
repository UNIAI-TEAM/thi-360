package vn.unicom.thi360.core.legal

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.LinkAnnotation
import java.io.ByteArrayInputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.feature.login.LoginTestTags
import vn.unicom.thi360.feature.recovery.OpenApiRecoveryGateway
import vn.unicom.thi360.feature.recovery.RegisterTestTags
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class RegisterLegalLinksTest {

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

    private fun sampleLegalJson(id: String, title: String): String = """
        {
          "id": "$id",
          "locale": "vi",
          "version": "2026-11-26",
          "status": "published",
          "effectiveDate": "2026-11-26",
          "title": "$title",
          "note": null,
          "contentHash": "hash-$id",
          "blocks": [
            {"type": "heading", "level": 2, "text": "1. Giới thiệu"},
            {"type": "paragraph", "text": "Nội dung $title."}
          ]
        }
    """.trimIndent()

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun consentTextExposesTwoClickableLinksAndOpeningThenClosingLegalPreservesRegisterFormState() {
        val legalRepo = LegalRepository { path ->
            val content = when {
                path.endsWith("terms.vi.json") -> sampleLegalJson("terms", "Điều kiện giao dịch chung")
                path.endsWith("privacy.vi.json") -> sampleLegalJson("privacy", "Chính sách bảo mật thông tin")
                else -> throw java.io.FileNotFoundException(path)
            }
            ByteArrayInputStream(content.toByteArray(Charsets.UTF_8))
        }

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
        composeRule.onNodeWithTag(RegisterTestTags.SCREEN_FORM).assertIsDisplayed()

        // Fill form and check terms box before tapping legal links.
        composeRule.onNodeWithTag(RegisterTestTags.FULL_NAME_FIELD).performScrollTo().performTextInput("Nguyễn Văn An")
        composeRule.onNodeWithTag(RegisterTestTags.USERNAME_FIELD).performScrollTo().performTextInput("student.legal")
        composeRule.onNodeWithTag(RegisterTestTags.EMAIL_FIELD).performScrollTo().performTextInput("legal@thi360.vn")
        composeRule.onNodeWithTag(RegisterTestTags.PASSWORD_FIELD).performScrollTo().performTextInput("StudentPass1!")
        composeRule.onNodeWithTag(RegisterTestTags.TERMS_CHECKBOX).performScrollTo().performClick()

        // Verify consent text has exact Vietnamese sentence and exposes two LinkAnnotation.Clickable links for TalkBack.
        val consentNode = composeRule.onNodeWithTag(RegisterTestTags.TERMS_CONSENT_TEXT).performScrollTo()
        consentNode.assertTextContains(
            "Tôi đã đọc và đồng ý với Điều kiện giao dịch chung và Chính sách bảo mật thông tin của Thi360.",
        )
        val annotatedTexts = consentNode.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text).orEmpty()
        assertTrue(annotatedTexts.isNotEmpty())
        val annotated = annotatedTexts.first()
        val linkAnnotations = annotated.getLinkAnnotations(0, annotated.length)
        assertEquals("Expected exactly 2 link annotations (<terms> and <privacy>)", 2, linkAnnotations.size)

        val termsRange = linkAnnotations[0]
        val privacyRange = linkAnnotations[1]
        assertEquals("Điều kiện giao dịch chung", annotated.text.substring(termsRange.start, termsRange.end))
        assertEquals("Chính sách bảo mật thông tin", annotated.text.substring(privacyRange.start, privacyRange.end))
        assertTrue(termsRange.item is LinkAnnotation.Clickable)
        assertTrue(privacyRange.item is LinkAnnotation.Clickable)

        // Open Terms link -> navigates to legal/terms screen.
        composeRule.runOnIdle {
            (termsRange.item as LinkAnnotation.Clickable).linkInteractionListener?.onClick(termsRange.item)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(LegalTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(LegalTestTags.TITLE).assertTextContains("Điều kiện giao dịch chung")

        // Close legal screen -> returns to Register with EVERY field + checkbox preserved!
        composeRule.onNodeWithTag(LegalTestTags.BACK_BUTTON).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(RegisterTestTags.SCREEN_FORM).assertIsDisplayed()
        composeRule.onNodeWithTag(RegisterTestTags.FULL_NAME_FIELD).performScrollTo().assertTextContains("Nguyễn Văn An")
        composeRule.onNodeWithTag(RegisterTestTags.USERNAME_FIELD).performScrollTo().assertTextContains("student.legal")
        composeRule.onNodeWithTag(RegisterTestTags.EMAIL_FIELD).performScrollTo().assertTextContains("legal@thi360.vn")
        composeRule.onNodeWithTag(RegisterTestTags.TERMS_CHECKBOX).performScrollTo().assertIsOn()

        // Open Privacy link -> navigates to legal/privacy screen and returning still preserves form state!
        val consentNode2 = composeRule.onNodeWithTag(RegisterTestTags.TERMS_CONSENT_TEXT).performScrollTo()
        val privacyItem = consentNode2.fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.Text)!!.first()
            .let { it.getLinkAnnotations(0, it.length)[1].item as LinkAnnotation.Clickable }
        composeRule.runOnIdle {
            privacyItem.linkInteractionListener?.onClick(privacyItem)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(LegalTestTags.TITLE).assertTextContains("Chính sách bảo mật thông tin")

        composeRule.onNodeWithTag(LegalTestTags.BACK_BUTTON).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(RegisterTestTags.FULL_NAME_FIELD).performScrollTo().assertTextContains("Nguyễn Văn An")
        composeRule.onNodeWithTag(RegisterTestTags.TERMS_CHECKBOX).performScrollTo().assertIsOn()
    }
}
