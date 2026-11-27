package vn.unicom.thi360.core.brand

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.session.SignOutReason
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.lock.LockScreenContent
import vn.unicom.thi360.feature.login.LoginScreenContent
import vn.unicom.thi360.feature.login.LoginUiState
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.Thi360App

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h844dp")
class BrandMarkUsageTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loginScreenDisplaysBrandMark() {
        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                LoginScreenContent(
                    uiState = LoginUiState(),
                    signOutReason = SignOutReason.None,
                    onIdentifierChanged = {},
                    onTogglePasswordVisibility = {},
                    onSubmit = {},
                    onSelectLocale = {},
                    onForgotPasswordClick = {},
                    onRegisterClick = {},
                )
            }
        }
        composeRule.onNodeWithTag(BrandMarkTestTags.CONTAINER).assertIsDisplayed()
        composeRule.onNodeWithTag(BrandMarkTestTags.ICON).assertIsDisplayed()
        composeRule.onNodeWithTag(BrandMarkTestTags.NAME).assertIsDisplayed()
    }

    @Test
    fun lockScreenDisplaysBrandMark() {
        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
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
        composeRule.onNodeWithTag(BrandMarkTestTags.CONTAINER).assertIsDisplayed()
        composeRule.onNodeWithTag(BrandMarkTestTags.ICON).assertIsDisplayed()
        composeRule.onNodeWithTag(BrandMarkTestTags.NAME).assertIsDisplayed()
    }

    @Test
    fun placeholderScreenDisplaysBrandMark() {
        composeRule.setContent {
            Thi360App(
                preferences = AppPreferences(locale = AppLocale.VI),
                startDestination = Destination.Home.route,
            )
        }
        composeRule.onNodeWithTag(BrandMarkTestTags.CONTAINER).assertIsDisplayed()
    }
}
