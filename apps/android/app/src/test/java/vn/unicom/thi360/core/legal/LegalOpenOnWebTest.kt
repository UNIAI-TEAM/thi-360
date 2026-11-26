package vn.unicom.thi360.core.legal

import android.content.Intent
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import vn.unicom.thi360.BuildConfig
import vn.unicom.thi360.Thi360Application
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.theme.Thi360Theme

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h844dp")
class LegalOpenOnWebTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun stubRepo(): LegalRepository = LegalRepository { path ->
        val id = if (path.contains("privacy")) "privacy" else "terms"
        val body = """
            {
              "id": "$id",
              "locale": "vi",
              "version": "2026-11-26",
              "status": "published",
              "effectiveDate": "2026-11-26",
              "title": "Văn bản $id",
              "blocks": [{"type": "paragraph", "text": "Nội dung."}]
            }
        """.trimIndent()
        ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
    }

    @Test
    fun openOnWebFiresActionViewForTermsUsingBuildConfigWebBaseUrl() {
        val app = ApplicationProvider.getApplicationContext<Thi360Application>()
        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                LegalDocumentScreen(
                    id = LegalId.TERMS,
                    repository = stubRepo(),
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag(LegalTestTags.OPEN_ON_WEB).performScrollTo().performClick()
        val intent = Shadows.shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("${BuildConfig.WEB_BASE_URL}/dieu-khoan-giao-dich-chung", intent.data?.toString())
    }

    @Test
    fun openOnWebFiresActionViewForPrivacyUsingBuildConfigWebBaseUrl() {
        val app = ApplicationProvider.getApplicationContext<Thi360Application>()
        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                LegalDocumentScreen(
                    id = LegalId.PRIVACY,
                    repository = stubRepo(),
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag(LegalTestTags.OPEN_ON_WEB).performScrollTo().performClick()
        val intent = Shadows.shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("${BuildConfig.WEB_BASE_URL}/chinh-sach-bao-mat-thong-tin", intent.data?.toString())
    }
}
