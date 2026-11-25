package vn.unicom.thi360.feature.login

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
import vn.unicom.thi360.core.session.LoginErrorKind
import vn.unicom.thi360.core.session.SignOutReason
import vn.unicom.thi360.core.theme.Thi360Theme

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LoginScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun captureLogin(
        name: String,
        themeMode: ThemeMode,
        uiState: LoginUiState = LoginUiState(identifier = "minhanh.tran"),
        signOutReason: SignOutReason = SignOutReason.None,
    ) {
        composeRule.setContent {
            Thi360Theme(
                preferences = AppPreferences(
                    locale = AppLocale.VI,
                    themeMode = themeMode,
                ),
            ) {
                LoginScreenContent(
                    uiState = uiState,
                    signOutReason = signOutReason,
                    onIdentifierChanged = {},
                    onTogglePasswordVisibility = {},
                    onSubmit = {},
                    onSelectLocale = {},
                    onForgotPasswordClick = {},
                    onRegisterClick = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    private fun captureRoleNotice(
        name: String,
        themeMode: ThemeMode,
    ) {
        composeRule.setContent {
            Thi360Theme(
                preferences = AppPreferences(
                    locale = AppLocale.VI,
                    themeMode = themeMode,
                ),
            ) {
                RoleNoticeScreen(
                    username = "quantri.noidung",
                    onSwitchAccount = {},
                    onSelectLocale = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun login_390x844_light() = captureLogin("login_390x844_light", ThemeMode.LIGHT)

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun login_390x844_dark() = captureLogin("login_390x844_dark", ThemeMode.DARK)

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun login_390x844_error_light() = captureLogin(
        name = "login_390x844_error_light",
        themeMode = ThemeMode.LIGHT,
        uiState = LoginUiState(
            identifier = "minhanh.tran",
            errorKind = LoginErrorKind.INVALID_CREDENTIALS,
        ),
    )

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun login_1280x800_light() = captureLogin("login_1280x800_light", ThemeMode.LIGHT)

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun login_1280x800_dark() = captureLogin("login_1280x800_dark", ThemeMode.DARK)

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun role_notice_390x844_light() = captureRoleNotice("role_notice_390x844_light", ThemeMode.LIGHT)

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun role_notice_390x844_dark() = captureRoleNotice("role_notice_390x844_dark", ThemeMode.DARK)
}
