package vn.unicom.thi360.core.config

import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import vn.unicom.thi360.BuildConfig
import vn.unicom.thi360.R
import vn.unicom.thi360.Thi360Application

@RunWith(RobolectricTestRunner::class)
class SupportContactConfigTest {

    @Test
    fun buildConfigSupportContactMatchesFlavorAndEmptyFallsBackToPendingMessage() {
        val contact = AppEnvironment.supportContact
        when (BuildConfig.FLAVOR) {
            "dev" -> {
                assertEquals("", BuildConfig.SUPPORT_PHONE)
                assertEquals("", BuildConfig.SUPPORT_EMAIL)
                assertEquals("", BuildConfig.SUPPORT_HOURS)
                assertFalse("Empty dev support fields must report hasAnyChannel = false", contact.hasAnyChannel)
                assertNull(contact.formattedSummary())
            }
            "demo" -> {
                assertEquals(BuildConfig.SUPPORT_PHONE, contact.phone)
                assertEquals(BuildConfig.SUPPORT_EMAIL, contact.email)
                assertEquals(BuildConfig.SUPPORT_HOURS, contact.hours)
                if (contact.phone.isBlank() && contact.email.isBlank() && contact.hours.isBlank()) {
                    assertFalse(contact.hasAnyChannel)
                    assertNull(contact.formattedSummary())
                }
            }
            else -> throw AssertionError("Unexpected flavor: ${BuildConfig.FLAVOR}")
        }

        val populated = SupportContact(phone = "1900 1234", email = "hotro@thi360.vn", hours = "8:00–17:30")
        assertTrue(populated.hasAnyChannel)
        assertEquals("1900 1234 · hotro@thi360.vn · 8:00–17:30", populated.formattedSummary())

        val blank = SupportContact(phone = "   ", email = "", hours = "  ")
        assertFalse(blank.hasAnyChannel)
        assertNull(blank.formattedSummary())

        val context = ApplicationProvider.getApplicationContext<Thi360Application>()
        val viContext = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag("vi")) },
        )
        val enContext = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag("en")) },
        )
        assertEquals(
            "Thông tin liên hệ đang được cập nhật.",
            viContext.getString(R.string.landing_support_pending),
        )
        assertEquals(
            "Contact information is being updated.",
            enContext.getString(R.string.landing_support_pending),
        )
    }
}
