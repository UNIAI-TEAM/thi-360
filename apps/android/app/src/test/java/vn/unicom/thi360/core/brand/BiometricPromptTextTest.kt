package vn.unicom.thi360.core.brand

import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import java.util.Locale
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import vn.unicom.thi360.R
import vn.unicom.thi360.Thi360Application

@RunWith(RobolectricTestRunner::class)
class BiometricPromptTextTest {

    private fun localizedContext(tag: String) =
        ApplicationProvider.getApplicationContext<Thi360Application>().let { base ->
            val config = Configuration(base.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(tag))
            }
            base.createConfigurationContext(config)
        }

    @Test
    fun biometricPromptTitleAndCopyUseThi360WithoutSpaceInViAndEn() {
        for (tag in listOf("vi", "en")) {
            val ctx = localizedContext(tag)
            val promptTitle = ctx.getString(R.string.biometric_os_prompt_unlock_title)
            val enableBody = ctx.getString(R.string.biometric_enable_body)
            val logoutMsg = ctx.getString(R.string.account_logout_confirm_message)

            assertTrue(
                "biometric_os_prompt_unlock_title ($tag) must contain 'Thi360', was '$promptTitle'",
                promptTitle.contains("Thi360"),
            )
            assertFalse(
                "biometric_os_prompt_unlock_title ($tag) must not contain 'Thi 360', was '$promptTitle'",
                promptTitle.contains("Thi 360"),
            )
            assertTrue(
                "biometric_enable_body ($tag) must contain 'Thi360', was '$enableBody'",
                enableBody.contains("Thi360"),
            )
            assertFalse(enableBody.contains("Thi 360"))
            assertTrue(
                "account_logout_confirm_message ($tag) must contain 'Thi360', was '$logoutMsg'",
                logoutMsg.contains("Thi360"),
            )
            assertFalse(logoutMsg.contains("Thi 360"))
        }
    }
}
