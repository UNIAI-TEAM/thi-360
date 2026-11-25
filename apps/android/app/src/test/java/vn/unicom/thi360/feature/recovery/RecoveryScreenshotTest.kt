package vn.unicom.thi360.feature.recovery

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.password.NewPasswordMode
import vn.unicom.thi360.feature.password.NewPasswordScreenContent
import vn.unicom.thi360.feature.password.NewPasswordUiState
import vn.unicom.thi360.feature.password.PasswordRules
import vn.unicom.thi360.feature.register.OtpScreenContent
import vn.unicom.thi360.feature.register.RegisterFormScreenContent
import vn.unicom.thi360.feature.register.RegisterUiState
import vn.unicom.thi360.feature.register.RegistrationFormData

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecoveryScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun captureRegister(name: String, themeMode: ThemeMode) {
        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = themeMode)) {
                RegisterFormScreenContent(
                    formData = RegistrationFormData(
                        fullName = "Trần Minh Anh",
                        username = "minhanh.tran",
                        email = "minhanh@example.com",
                        acceptTerms = true,
                    ),
                    uiState = RegisterUiState(),
                    passwordState = TextFieldState("Matkhau1"),
                    evaluation = PasswordRules.evaluate("Matkhau1"),
                    onFullNameChanged = {},
                    onUsernameChanged = {},
                    onEmailChanged = {},
                    onTogglePasswordVisibility = {},
                    onAcceptTermsChanged = {},
                    onSubmit = {},
                    onBackToLogin = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    private fun captureOtp(name: String, themeMode: ThemeMode) {
        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = themeMode)) {
                OtpScreenContent(
                    email = "minhanh@example.com",
                    otpState = TextFieldState("4071"),
                    otpCodeError = null,
                    otpAlert = null,
                    showResentNotice = false,
                    isVerifying = false,
                    isResending = false,
                    remainingResendSeconds = 42L,
                    onVerify = {},
                    onResend = {},
                    onBackToEdit = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    private fun captureForgot(name: String, themeMode: ThemeMode, sent: Boolean) {
        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = themeMode)) {
                ForgotPasswordScreenContent(
                    uiState = if (sent) {
                        ForgotPasswordUiState(
                            step = ForgotPasswordStep.Sent(email = "minhanh@example.com", resendAtMillis = 58_000L),
                            email = "minhanh@example.com",
                        )
                    } else {
                        ForgotPasswordUiState(email = "minhanh@example.com")
                    },
                    remainingResendSeconds = if (sent) 58L else 0L,
                    onEmailChanged = {},
                    onSubmit = {},
                    onResend = {},
                    onBackToLogin = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    private fun captureNewPassword(
        name: String,
        themeMode: ThemeMode,
        mode: NewPasswordMode,
        embeddedInPane: Boolean = false,
    ) {
        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = themeMode)) {
                NewPasswordScreenContent(
                    mode = mode,
                    uiState = NewPasswordUiState(),
                    oldPasswordState = TextFieldState("matkhaucu"),
                    newPasswordState = TextFieldState("Matkhau1"),
                    confirmPasswordState = TextFieldState(),
                    evaluation = PasswordRules.evaluate("Matkhau1"),
                    embeddedInPane = embeddedInPane,
                    onBack = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun register_390x844_light() = captureRegister("register_390x844_light", ThemeMode.LIGHT)

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun register_390x844_dark() = captureRegister("register_390x844_dark", ThemeMode.DARK)

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun otp_390x844_light() = captureOtp("otp_390x844_light", ThemeMode.LIGHT)

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun otp_390x844_dark() = captureOtp("otp_390x844_dark", ThemeMode.DARK)

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun forgot_form_390x844_light() = captureForgot("forgot_form_390x844_light", ThemeMode.LIGHT, sent = false)

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun forgot_sent_390x844_light() = captureForgot("forgot_sent_390x844_light", ThemeMode.LIGHT, sent = true)

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun forgot_sent_390x844_dark() = captureForgot("forgot_sent_390x844_dark", ThemeMode.DARK, sent = true)

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun new_password_link_390x844_light() = captureNewPassword(
        name = "new_password_link_390x844_light",
        themeMode = ThemeMode.LIGHT,
        mode = NewPasswordMode.FromLink("snapshot-placeholder-token"),
    )

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun new_password_forced_390x844_light() = captureNewPassword(
        name = "new_password_forced_390x844_light",
        themeMode = ThemeMode.LIGHT,
        mode = NewPasswordMode.Forced("minhanh.tran"),
    )

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun new_password_forced_390x844_dark() = captureNewPassword(
        name = "new_password_forced_390x844_dark",
        themeMode = ThemeMode.DARK,
        mode = NewPasswordMode.Forced("minhanh.tran"),
    )

    @Test
    @Config(qualifiers = "w840dp-h720dp-xhdpi")
    fun change_password_840x720_light() = captureNewPassword(
        name = "change_password_840x720_light",
        themeMode = ThemeMode.LIGHT,
        mode = NewPasswordMode.Voluntary("minhanh.tran"),
        embeddedInPane = true,
    )
}
