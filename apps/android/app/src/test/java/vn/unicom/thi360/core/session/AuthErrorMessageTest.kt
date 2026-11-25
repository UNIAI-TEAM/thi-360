package vn.unicom.thi360.core.session

import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import vn.unicom.thi360.Thi360Application

@RunWith(RobolectricTestRunner::class)
class AuthErrorMessageTest {

    private fun localizedString(resId: Int, languageTag: String): String {
        val base = ApplicationProvider.getApplicationContext<Thi360Application>()
        val config = Configuration(base.resources.configuration).apply {
            setLocales(LocaleList(Locale.forLanguageTag(languageTag)))
        }
        return base.createConfigurationContext(config).getString(resId)
    }

    @Test
    fun mapsAllFourKnownCodesAndFallsBackToGenericForUnknownCodes() {
        assertEquals(LoginErrorKind.INVALID_CREDENTIALS, AuthErrorMessage.fromApiErrorCode("INVALID_CREDENTIALS"))
        assertEquals(LoginErrorKind.ACCOUNT_INACTIVE, AuthErrorMessage.fromApiErrorCode("ACCOUNT_INACTIVE"))
        assertEquals(LoginErrorKind.LOGIN_THROTTLED, AuthErrorMessage.fromApiErrorCode("LOGIN_THROTTLED"))
        assertEquals(LoginErrorKind.RATE_LIMITED, AuthErrorMessage.fromApiErrorCode("RATE_LIMITED"))

        assertEquals(LoginErrorKind.GENERIC, AuthErrorMessage.fromApiErrorCode("UNKNOWN_BACKEND_CODE"))
        assertEquals(LoginErrorKind.GENERIC, AuthErrorMessage.fromApiErrorCode(""))
        assertEquals(LoginErrorKind.GENERIC, AuthErrorMessage.fromApiErrorCode(null))
    }

    @Test
    fun vietnameseAndEnglishStringsMatchWebLoginErrorCopy() {
        assertEquals(
            "Tên đăng nhập hoặc mật khẩu không đúng.",
            localizedString(LoginErrorKind.INVALID_CREDENTIALS.messageRes, "vi"),
        )
        assertEquals(
            "Tài khoản đã ngừng hoạt động. Vui lòng liên hệ quản trị viên.",
            localizedString(LoginErrorKind.ACCOUNT_INACTIVE.messageRes, "vi"),
        )
        assertEquals(
            "Bạn đã nhập sai quá nhiều lần. Vui lòng thử lại sau 15 phút.",
            localizedString(LoginErrorKind.LOGIN_THROTTLED.messageRes, "vi"),
        )
        assertEquals(
            "Quá nhiều yêu cầu từ địa chỉ của bạn. Vui lòng thử lại sau ít phút.",
            localizedString(LoginErrorKind.RATE_LIMITED.messageRes, "vi"),
        )
        assertEquals(
            "Không thể đăng nhập. Vui lòng thử lại.",
            localizedString(LoginErrorKind.GENERIC.messageRes, "vi"),
        )

        assertEquals(
            "Username or password is incorrect.",
            localizedString(LoginErrorKind.INVALID_CREDENTIALS.messageRes, "en"),
        )
        assertEquals(
            "Account is inactive. Please contact an administrator.",
            localizedString(LoginErrorKind.ACCOUNT_INACTIVE.messageRes, "en"),
        )
        assertEquals(
            "Too many failed attempts. Please try again in 15 minutes.",
            localizedString(LoginErrorKind.LOGIN_THROTTLED.messageRes, "en"),
        )
        assertEquals(
            "Too many requests from your address. Please try again in a few minutes.",
            localizedString(LoginErrorKind.RATE_LIMITED.messageRes, "en"),
        )
        assertEquals(
            "Unable to sign in. Please try again.",
            localizedString(LoginErrorKind.GENERIC.messageRes, "en"),
        )

        val unknownCode = "INTERNAL_DB_ERR_42"
        val genericVi = localizedString(AuthErrorMessage.fromApiErrorCode(unknownCode).messageRes, "vi")
        assertFalse(genericVi.contains(unknownCode))
    }
}
