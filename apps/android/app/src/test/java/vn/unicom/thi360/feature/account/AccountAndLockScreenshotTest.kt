package vn.unicom.thi360.feature.account

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
import vn.unicom.thi360.core.appearance.AppearanceTokensParser
import vn.unicom.thi360.core.appearance.PaletteApplicationTest
import vn.unicom.thi360.core.biometric.BiometricState
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.core.theme.BrandPalette
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.feature.lock.BiometricEnablePromptContent
import vn.unicom.thi360.feature.lock.LockScreenContent
import vn.unicom.thi360.feature.password.NewPasswordMode
import vn.unicom.thi360.feature.password.NewPasswordScreenContent
import vn.unicom.thi360.feature.password.NewPasswordUiState
import vn.unicom.thi360.feature.password.PasswordRules

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AccountAndLockScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sampleProfile = UserProfile(
        username = "minhanh.tran",
        fullName = "Trần Minh Anh",
        email = "minhanh@example.com",
        emailVerified = true,
    )

    private fun captureAccount(
        name: String,
        isTwoPane: Boolean,
        themeMode: ThemeMode,
        brandPalette: BrandPalette = BrandPalette.Default,
    ) {
        composeRule.setContent {
            Thi360Theme(
                preferences = AppPreferences(locale = AppLocale.VI, themeMode = themeMode),
                brandPalette = brandPalette,
            ) {
                AccountScreenContent(
                    isTwoPaneWidth = isTwoPane,
                    profileState = LoadState.Loaded(sampleProfile),
                    preferences = AppPreferences(locale = AppLocale.VI, themeMode = themeMode),
                    biometricState = BiometricState.On,
                    selectedPane = AccountDetailPane.CHANGE_PASSWORD,
                    isUpdatingBiometric = false,
                    showBiometricError = false,
                    isLoggingOut = false,
                    onRetryProfile = {},
                    onChangePasswordClick = {},
                    onToggleBiometric = {},
                    onLanguageRowClick = {},
                    onThemeRowClick = {},
                    onSelectThemeMode = {},
                    onLogoutClick = {},
                    detailContent = {
                        NewPasswordScreenContent(
                            mode = NewPasswordMode.Voluntary("minhanh.tran"),
                            uiState = NewPasswordUiState(),
                            oldPasswordState = TextFieldState("matkhaucu"),
                            newPasswordState = TextFieldState("Matkhau1"),
                            confirmPasswordState = TextFieldState(),
                            evaluation = PasswordRules.evaluate("Matkhau1"),
                            embeddedInPane = true,
                        )
                    },
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    private fun captureBiometricEnable(name: String, themeMode: ThemeMode) {
        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = themeMode)) {
                BiometricEnablePromptContent(
                    isEnabling = false,
                    showError = false,
                    onEnableClick = {},
                    onLaterClick = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    private fun captureLock(name: String, themeMode: ThemeMode) {
        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = themeMode)) {
                LockScreenContent(
                    displayName = "Trần Minh Anh",
                    username = "minhanh.tran",
                    feedback = null,
                    isUnlocking = false,
                    isSwitchingAccount = false,
                    onUnlockClick = {},
                    onEnterPasswordClick = {},
                    onOtherAccountClick = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun account_390x844_light() = captureAccount("account_390x844_light", isTwoPane = false, ThemeMode.LIGHT)

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun account_390x844_dark() = captureAccount("account_390x844_dark", isTwoPane = false, ThemeMode.DARK)

    @Test
    @Config(qualifiers = "w840dp-h720dp-xhdpi")
    fun account_840x720_light() = captureAccount("account_840x720_light", isTwoPane = true, ThemeMode.LIGHT)

    @Test
    @Config(qualifiers = "w840dp-h720dp-xhdpi")
    fun account_840x720_dark() = captureAccount("account_840x720_dark", isTwoPane = true, ThemeMode.DARK)

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun biometric_enable_390x844_light() = captureBiometricEnable("biometric_enable_390x844_light", ThemeMode.LIGHT)

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun lock_390x844_light() = captureLock("lock_390x844_light", ThemeMode.LIGHT)

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun lock_390x844_dark() = captureLock("lock_390x844_dark", ThemeMode.DARK)

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun account_390x844_palette_navy_light() {
        val navy = checkNotNull(AppearanceTokensParser.parse(PaletteApplicationTest.NAVY_TEAL_JSON)).palette
        captureAccount("account_390x844_palette_navy_light", isTwoPane = false, ThemeMode.LIGHT, navy)
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun account_390x844_palette_navy_dark() {
        val navy = checkNotNull(AppearanceTokensParser.parse(PaletteApplicationTest.NAVY_TEAL_JSON)).palette
        captureAccount("account_390x844_palette_navy_dark", isTwoPane = false, ThemeMode.DARK, navy)
    }
}
