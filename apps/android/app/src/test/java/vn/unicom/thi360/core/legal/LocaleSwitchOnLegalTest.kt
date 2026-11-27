package vn.unicom.thi360.core.legal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.theme.Thi360Theme

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h844dp")
class LocaleSwitchOnLegalTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun switchingLocaleWhileViewingLegalUpdatesDocumentInPlaceWithoutLeavingScreen() {
        val viJson = """
            {
              "id": "terms",
              "locale": "vi",
              "version": "2026-11-26",
              "status": "published",
              "effectiveDate": "2026-11-26",
              "title": "Điều kiện giao dịch chung",
              "note": null,
              "blocks": [{"type": "paragraph", "text": "Bản tiếng Việt có giá trị pháp lý."}]
            }
        """.trimIndent()
        val enJson = """
            {
              "id": "terms",
              "locale": "en",
              "version": "2026-11-26",
              "status": "published",
              "effectiveDate": "2026-11-26",
              "title": "General Terms of Service",
              "note": "The Vietnamese version is the legally binding text.",
              "blocks": [{"type": "paragraph", "text": "English reference translation."}]
            }
        """.trimIndent()

        val repo = LegalRepository { path ->
            val raw = if (path.endsWith(".en.json")) enJson else viJson
            ByteArrayInputStream(raw.toByteArray(Charsets.UTF_8))
        }

        var locale by mutableStateOf(AppLocale.VI)
        var backCalls = 0

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = locale)) {
                LegalDocumentScreen(
                    id = LegalId.TERMS,
                    repository = repo,
                    onBack = { backCalls++ },
                )
            }
        }

        composeRule.onNodeWithTag(LegalTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(LegalTestTags.TITLE).assertTextContains("Điều kiện giao dịch chung")
        composeRule.onNodeWithTag(LegalTestTags.NOTE).assertDoesNotExist()

        // Switch locale live to EN -> stays on legal screen, renders EN document and note immediately.
        composeRule.runOnIdle { locale = AppLocale.EN }
        composeRule.waitForIdle()

        assertEquals("Switching locale must not pop LegalDocumentScreen", 0, backCalls)
        composeRule.onNodeWithTag(LegalTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(LegalTestTags.TITLE).assertTextContains("General Terms of Service")
        composeRule.onNodeWithTag(LegalTestTags.NOTE)
            .assertIsDisplayed()
            .assertTextContains("The Vietnamese version is the legally binding text.")
    }
}
