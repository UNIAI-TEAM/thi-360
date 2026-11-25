package vn.unicom.thi360.smoke

import android.graphics.Bitmap
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.unicom.thi360.core.biometric.BiometricState
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.core.session.SignOutReason
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.account.AccountDetailPane
import vn.unicom.thi360.feature.account.AccountScreenContent
import vn.unicom.thi360.feature.account.AccountTestTags
import vn.unicom.thi360.feature.account.ProfileLoadState
import vn.unicom.thi360.feature.account.UserProfile
import vn.unicom.thi360.feature.lock.LockScreenContent
import vn.unicom.thi360.feature.lock.LockTestTags
import vn.unicom.thi360.feature.login.LoginScreenContent
import vn.unicom.thi360.feature.login.LoginTestTags
import vn.unicom.thi360.feature.login.LoginUiState
import vn.unicom.thi360.feature.password.PasswordRules
import vn.unicom.thi360.feature.recovery.RegisterTestTags
import vn.unicom.thi360.feature.register.RegisterFormScreenContent
import vn.unicom.thi360.feature.register.RegisterUiState
import vn.unicom.thi360.feature.register.RegistrationFormData
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags
import vn.unicom.thi360.shell.Thi360App

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class AccessibilityAndFontScaleTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sampleProfile = UserProfile(
        username = "minhanh.tran",
        fullName = "Trần Minh Anh",
        email = "minhanh@example.com",
        emailVerified = true,
    )

    @Test
    fun loginScreenAccessibilityAndTouchTargets() {
        renderAtScale(1.0f) {
            LoginSurface()
        }

        assertHasHeading("Login")
        assertInteractiveNodesHaveAccessibleLabel("Login")
        assertTouchTargetsAtLeast48Dp(
            screenName = "Login",
            tags = listOf(
                LoginTestTags.LANGUAGE_TOGGLE,
                LoginTestTags.PASSWORD_VISIBILITY_TOGGLE,
                LoginTestTags.FORGOT_PASSWORD_LINK,
                LoginTestTags.SUBMIT_BUTTON,
                LoginTestTags.REGISTER_LINK,
            ),
        )
        assertTopToBottomOrder(
            screenName = "Login",
            orderedTags = listOf(
                LoginTestTags.IDENTIFIER_FIELD,
                LoginTestTags.PASSWORD_FIELD,
                LoginTestTags.FORGOT_PASSWORD_LINK,
                LoginTestTags.SUBMIT_BUTTON,
                LoginTestTags.REGISTER_LINK,
            ),
        )
    }

    @Test
    fun loginScreenFontScale130() {
        verifyLoginAtScale(scale = 1.3f, snapshotName = "login-130")
    }

    @Test
    fun loginScreenFontScale200() {
        verifyLoginAtScale(scale = 2.0f, snapshotName = "login-200")
    }

    @Test
    fun registerScreenAccessibilityAndTouchTargets() {
        renderAtScale(1.0f) {
            RegisterSurface()
        }

        assertHasHeading("Register")
        assertInteractiveNodesHaveAccessibleLabel("Register")
        assertTouchTargetsAtLeast48Dp(
            screenName = "Register",
            tags = listOf(
                RegisterTestTags.BACK_BUTTON,
                RegisterTestTags.PASSWORD_VISIBILITY_TOGGLE,
                RegisterTestTags.TERMS_CHECKBOX,
                RegisterTestTags.TERMS_VIEW_BUTTON,
                RegisterTestTags.SUBMIT_BUTTON,
                RegisterTestTags.LOGIN_LINK,
            ),
            scrollableTags = setOf(
                RegisterTestTags.PASSWORD_VISIBILITY_TOGGLE,
                RegisterTestTags.TERMS_CHECKBOX,
                RegisterTestTags.TERMS_VIEW_BUTTON,
                RegisterTestTags.SUBMIT_BUTTON,
                RegisterTestTags.LOGIN_LINK,
            ),
        )
        assertTopToBottomOrder(
            screenName = "Register",
            orderedTags = listOf(
                RegisterTestTags.FULL_NAME_FIELD,
                RegisterTestTags.USERNAME_FIELD,
                RegisterTestTags.EMAIL_FIELD,
                RegisterTestTags.PASSWORD_FIELD,
                RegisterTestTags.TERMS_CHECKBOX,
                RegisterTestTags.SUBMIT_BUTTON,
            ),
        )
    }

    @Test
    fun registerScreenFontScale130() {
        verifyRegisterAtScale(scale = 1.3f, snapshotName = "register-130")
    }

    @Test
    fun registerScreenFontScale200() {
        verifyRegisterAtScale(scale = 2.0f, snapshotName = "register-200")
    }

    @Test
    fun lockScreenAccessibilityAndTouchTargets() {
        renderAtScale(1.0f) {
            LockSurface()
        }

        assertHasHeading("Lock")
        assertInteractiveNodesHaveAccessibleLabel("Lock")
        assertTouchTargetsAtLeast48Dp(
            screenName = "Lock",
            tags = listOf(
                LockTestTags.UNLOCK_BUTTON,
                LockTestTags.ENTER_PASSWORD_BUTTON,
                LockTestTags.OTHER_ACCOUNT_BUTTON,
            ),
        )
        assertTopToBottomOrder(
            screenName = "Lock",
            orderedTags = listOf(
                LockTestTags.LOCK_USER_NAME,
                LockTestTags.LOCK_BODY,
                LockTestTags.UNLOCK_BUTTON,
                LockTestTags.ENTER_PASSWORD_BUTTON,
                LockTestTags.OTHER_ACCOUNT_BUTTON,
            ),
        )
    }

    @Test
    fun lockScreenFontScale130() {
        verifyLockAtScale(scale = 1.3f, snapshotName = "lock-130")
    }

    @Test
    fun lockScreenFontScale200() {
        verifyLockAtScale(scale = 2.0f, snapshotName = "lock-200")
    }

    @Test
    fun homeScreenAccessibilityAndTouchTargets() {
        renderAtScale(1.0f) {
            HomeSurface()
        }

        assertHasHeading("Home")
        assertInteractiveNodesHaveAccessibleLabel("Home")
        assertTouchTargetsAtLeast48Dp(
            screenName = "Home",
            tags = Destination.entries.map(ShellTestTags::navItem),
        )
        assertTopToBottomOrder(
            screenName = "Home",
            orderedTags = listOf(
                ShellTestTags.screenTitle(Destination.Home),
                ShellTestTags.PLACEHOLDER_EMPTY_TITLE,
                ShellTestTags.BOTTOM_BAR,
            ),
        )
    }

    @Test
    fun homeScreenFontScale130() {
        verifyHomeAtScale(scale = 1.3f, snapshotName = "home-130")
    }

    @Test
    fun homeScreenFontScale200() {
        verifyHomeAtScale(scale = 2.0f, snapshotName = "home-200")
    }

    @Test
    fun accountScreenAccessibilityAndTouchTargets() {
        renderAtScale(1.0f) {
            AccountSurface()
        }

        assertHasHeading("Account")
        assertInteractiveNodesHaveAccessibleLabel("Account")
        assertTouchTargetsAtLeast48Dp(
            screenName = "Account",
            tags = listOf(
                AccountTestTags.CHANGE_PASSWORD_ROW,
                AccountTestTags.BIOMETRIC_SWITCH,
                AccountTestTags.LANGUAGE_ROW,
                AccountTestTags.THEME_OPTION_LIGHT,
                AccountTestTags.THEME_OPTION_DARK,
                AccountTestTags.LOGOUT_BUTTON,
                ShellTestTags.navItem(Destination.Home),
                ShellTestTags.navItem(Destination.Account),
            ),
            scrollableTags = setOf(
                AccountTestTags.CHANGE_PASSWORD_ROW,
                AccountTestTags.BIOMETRIC_SWITCH,
                AccountTestTags.LANGUAGE_ROW,
                AccountTestTags.THEME_OPTION_LIGHT,
                AccountTestTags.THEME_OPTION_DARK,
                AccountTestTags.LOGOUT_BUTTON,
            ),
        )
        assertTopToBottomOrder(
            screenName = "Account",
            orderedTags = listOf(
                ShellTestTags.screenTitle(Destination.Account),
                AccountTestTags.PROFILE_CARD,
                AccountTestTags.CHANGE_PASSWORD_ROW,
                AccountTestTags.BIOMETRIC_ROW,
                AccountTestTags.LANGUAGE_ROW,
                AccountTestTags.THEME_ROW,
                AccountTestTags.LOGOUT_BUTTON,
            ),
        )
    }

    @Test
    fun accountScreenFontScale130() {
        verifyAccountAtScale(scale = 1.3f, snapshotName = "account-130")
    }

    @Test
    fun accountScreenFontScale200() {
        verifyAccountAtScale(scale = 2.0f, snapshotName = "account-200")
    }

    private fun verifyLoginAtScale(scale: Float, snapshotName: String) {
        var submitted = false
        renderAtScale(scale) {
            LoginSurface(onSubmit = { submitted = true })
        }
        exportSnapshotIfRequested(snapshotName)

        assertNoHorizontalClipping(
            screenName = "Login@$scale",
            tags = listOf(
                LoginTestTags.LANGUAGE_TOGGLE,
                LoginTestTags.IDENTIFIER_FIELD,
                LoginTestTags.PASSWORD_FIELD,
                LoginTestTags.FORGOT_PASSWORD_LINK,
                LoginTestTags.SUBMIT_BUTTON,
                LoginTestTags.REGISTER_LINK,
            ),
            scrollableTags = setOf(
                LoginTestTags.IDENTIFIER_FIELD,
                LoginTestTags.PASSWORD_FIELD,
                LoginTestTags.FORGOT_PASSWORD_LINK,
                LoginTestTags.SUBMIT_BUTTON,
                LoginTestTags.REGISTER_LINK,
            ),
        )

        composeRule.onNodeWithTag(LoginTestTags.SUBMIT_BUTTON)
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        assertTrue("Login CTA must remain clickable at fontScale=$scale", submitted)
    }

    private fun verifyRegisterAtScale(scale: Float, snapshotName: String) {
        var submitted = false
        renderAtScale(scale) {
            RegisterSurface(onSubmit = { submitted = true })
        }
        exportSnapshotIfRequested(snapshotName)

        assertNoHorizontalClipping(
            screenName = "Register@$scale",
            tags = listOf(
                RegisterTestTags.BACK_BUTTON,
                RegisterTestTags.FULL_NAME_FIELD,
                RegisterTestTags.USERNAME_FIELD,
                RegisterTestTags.EMAIL_FIELD,
                RegisterTestTags.PASSWORD_FIELD,
                RegisterTestTags.TERMS_CHECKBOX,
                RegisterTestTags.SUBMIT_BUTTON,
                RegisterTestTags.LOGIN_LINK,
            ),
            scrollableTags = setOf(
                RegisterTestTags.FULL_NAME_FIELD,
                RegisterTestTags.USERNAME_FIELD,
                RegisterTestTags.EMAIL_FIELD,
                RegisterTestTags.PASSWORD_FIELD,
                RegisterTestTags.TERMS_CHECKBOX,
                RegisterTestTags.SUBMIT_BUTTON,
                RegisterTestTags.LOGIN_LINK,
            ),
        )

        composeRule.onNodeWithTag(RegisterTestTags.SUBMIT_BUTTON)
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        assertTrue("Register CTA must remain clickable at fontScale=$scale", submitted)
    }

    private fun verifyLockAtScale(scale: Float, snapshotName: String) {
        var unlocked = false
        var passwordFallback = false
        renderAtScale(scale) {
            LockSurface(
                onUnlockClick = { unlocked = true },
                onEnterPasswordClick = { passwordFallback = true },
            )
        }
        exportSnapshotIfRequested(snapshotName)

        assertNoHorizontalClipping(
            screenName = "Lock@$scale",
            tags = listOf(
                LockTestTags.LOCK_USER_NAME,
                LockTestTags.LOCK_BODY,
                LockTestTags.UNLOCK_BUTTON,
                LockTestTags.ENTER_PASSWORD_BUTTON,
                LockTestTags.OTHER_ACCOUNT_BUTTON,
            ),
            scrollableTags = setOf(
                LockTestTags.LOCK_USER_NAME,
                LockTestTags.LOCK_BODY,
            ),
        )

        composeRule.onNodeWithTag(LockTestTags.UNLOCK_BUTTON)
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        composeRule.onNodeWithTag(LockTestTags.ENTER_PASSWORD_BUTTON)
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        assertTrue("Lock unlock CTA must remain clickable at fontScale=$scale", unlocked)
        assertTrue("Lock password fallback CTA must remain clickable at fontScale=$scale", passwordFallback)
    }

    private fun verifyHomeAtScale(scale: Float, snapshotName: String) {
        renderAtScale(scale) {
            HomeSurface()
        }
        exportSnapshotIfRequested(snapshotName)

        assertNoHorizontalClipping(
            screenName = "Home@$scale",
            tags = listOf(
                ShellTestTags.screenTitle(Destination.Home),
                ShellTestTags.PLACEHOLDER_EMPTY_TITLE,
                ShellTestTags.BOTTOM_BAR,
            ) + Destination.entries.map(ShellTestTags::navItem),
            scrollableTags = setOf(
                ShellTestTags.screenTitle(Destination.Home),
                ShellTestTags.PLACEHOLDER_EMPTY_TITLE,
            ),
        )

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.MyExams))
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.MyExams))
            .assertIsDisplayed()
    }

    private fun verifyAccountAtScale(scale: Float, snapshotName: String) {
        var loggedOut = false
        renderAtScale(scale) {
            AccountSurface(onLogoutClick = { loggedOut = true })
        }
        exportSnapshotIfRequested(snapshotName)

        assertNoHorizontalClipping(
            screenName = "Account@$scale",
            tags = listOf(
                ShellTestTags.screenTitle(Destination.Account),
                AccountTestTags.PROFILE_CARD,
                AccountTestTags.CHANGE_PASSWORD_ROW,
                AccountTestTags.BIOMETRIC_ROW,
                AccountTestTags.LANGUAGE_ROW,
                AccountTestTags.THEME_ROW,
                AccountTestTags.LOGOUT_BUTTON,
                ShellTestTags.BOTTOM_BAR,
            ),
            scrollableTags = setOf(
                ShellTestTags.screenTitle(Destination.Account),
                AccountTestTags.PROFILE_CARD,
                AccountTestTags.CHANGE_PASSWORD_ROW,
                AccountTestTags.BIOMETRIC_ROW,
                AccountTestTags.LANGUAGE_ROW,
                AccountTestTags.THEME_ROW,
                AccountTestTags.LOGOUT_BUTTON,
            ),
        )

        composeRule.onNodeWithTag(AccountTestTags.LOGOUT_BUTTON)
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        assertTrue("Account logout CTA must remain clickable at fontScale=$scale", loggedOut)
    }

    private fun renderAtScale(scale: Float, content: @Composable () -> Unit) {
        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = baseDensity.density, fontScale = scale),
            ) {
                content()
            }
        }
        composeRule.waitForIdle()
    }

    private fun assertHasHeading(screenName: String) {
        val root = composeRule.onRoot(useUnmergedTree = false).fetchSemanticsNode()
        val headings = collectNodes(root).filter { node ->
            node.config.contains(SemanticsProperties.Heading)
        }
        assertTrue("$screenName must expose at least one TalkBack heading node", headings.isNotEmpty())
    }

    private fun assertInteractiveNodesHaveAccessibleLabel(screenName: String) {
        val root = composeRule.onRoot(useUnmergedTree = false).fetchSemanticsNode()
        val interactiveNodes = collectNodes(root).filter { node ->
            node.config.contains(SemanticsActions.OnClick) ||
                node.config.contains(SemanticsProperties.ToggleableState)
        }
        assertTrue("$screenName must contain interactive controls", interactiveNodes.isNotEmpty())

        interactiveNodes.forEach { node ->
            val hasText = node.config.getOrNull(SemanticsProperties.Text)
                .orEmpty()
                .any { it.text.isNotBlank() }
            val hasEditable = node.config.getOrNull(SemanticsProperties.EditableText)
                ?.text
                ?.isNotBlank() == true
            val hasContentDesc = node.config.getOrNull(SemanticsProperties.ContentDescription)
                .orEmpty()
                .any { it.isNotBlank() }
            val testTag = node.config.getOrNull(SemanticsProperties.TestTag) ?: "id=${node.id}"
            assertTrue(
                "$screenName interactive node '$testTag' must have non-blank Text or ContentDescription for TalkBack",
                hasText || hasEditable || hasContentDesc,
            )
        }
    }

    private fun assertTouchTargetsAtLeast48Dp(
        screenName: String,
        tags: List<String>,
        scrollableTags: Set<String> = emptySet(),
    ) {
        tags.forEach { tag ->
            val interaction = composeRule.onNodeWithTag(tag)
            if (tag in scrollableTags) {
                interaction.performScrollTo()
            }
            // Use touchBoundsInRoot so Material 3 controls with 48dp minimumInteractiveComponentSize
            // expansion (e.g., Switch's 52x32dp visual track) are measured by their real touch target.
            val touchBounds = interaction.fetchSemanticsNode().touchBoundsInRoot
            val touchWidth = with(composeRule.density) { touchBounds.width.toDp() }
            val touchHeight = with(composeRule.density) { touchBounds.height.toDp() }
            assertTrue(
                "$screenName touch target '$tag' width=$touchWidth must be >= $MIN_TOUCH_TARGET_DP",
                touchWidth >= MIN_TOUCH_TARGET_DP - DIMENSION_TOLERANCE_DP,
            )
            assertTrue(
                "$screenName touch target '$tag' height=$touchHeight must be >= $MIN_TOUCH_TARGET_DP",
                touchHeight >= MIN_TOUCH_TARGET_DP - DIMENSION_TOLERANCE_DP,
            )
        }
    }

    private fun assertTopToBottomOrder(screenName: String, orderedTags: List<String>) {
        val tops = orderedTags.map { tag ->
            tag to composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot().top
        }
        for (i in 0 until tops.lastIndex) {
            val (upperTag, upperTop) = tops[i]
            val (lowerTag, lowerTop) = tops[i + 1]
            assertTrue(
                "$screenName reading order violated: '$upperTag' (top=$upperTop) must be above '$lowerTag' (top=$lowerTop)",
                upperTop <= lowerTop,
            )
        }
    }

    private fun assertNoHorizontalClipping(
        screenName: String,
        tags: List<String>,
        scrollableTags: Set<String> = emptySet(),
    ) {
        val rootBounds = composeRule.onRoot().getUnclippedBoundsInRoot()
        tags.forEach { tag ->
            val interaction = composeRule.onNodeWithTag(tag)
            if (tag in scrollableTags) {
                interaction.performScrollTo()
            }
            val bounds = interaction.getUnclippedBoundsInRoot()
            assertTrue(
                "$screenName node '$tag' must have positive rendered bounds (width=${bounds.width}, height=${bounds.height})",
                bounds.width > 0.dp && bounds.height > 0.dp,
            )
            assertTrue(
                "$screenName node '$tag' left=${bounds.left} clips outside root left=${rootBounds.left}",
                bounds.left >= rootBounds.left - CLIP_TOLERANCE_DP,
            )
            assertTrue(
                "$screenName node '$tag' right=${bounds.right} clips outside root right=${rootBounds.right}",
                bounds.right <= rootBounds.right + CLIP_TOLERANCE_DP,
            )
        }
    }

    private fun exportSnapshotIfRequested(fileBaseName: String) {
        val snapshotDirPath = System.getProperty(SNAPSHOT_DIR_PROPERTY)?.trim()
            .takeUnless { it.isNullOrEmpty() }
            ?: System.getenv(SNAPSHOT_DIR_ENV)?.trim()
                .takeUnless { it.isNullOrEmpty() }
            ?: return

        val dir = File(snapshotDirPath)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val outputFile = File(dir, "$fileBaseName.png")
        FileOutputStream(outputFile).use { stream ->
            assertTrue(
                "Failed to compress PNG snapshot $outputFile",
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream),
            )
        }
        assertTrue("Snapshot $outputFile must be non-empty", outputFile.length() > 0L)
    }

    private fun collectNodes(node: SemanticsNode): List<SemanticsNode> {
        val result = mutableListOf(node)
        for (child in node.children) {
            result.addAll(collectNodes(child))
        }
        return result
    }

    @Composable
    private fun LoginSurface(onSubmit: () -> Unit = {}) {
        Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT)) {
            LoginScreenContent(
                uiState = LoginUiState(identifier = "minhanh.tran"),
                signOutReason = SignOutReason.None,
                onIdentifierChanged = {},
                onTogglePasswordVisibility = {},
                onSubmit = onSubmit,
                onSelectLocale = {},
                onForgotPasswordClick = {},
                onRegisterClick = {},
            )
        }
    }

    @Composable
    private fun RegisterSurface(onSubmit: () -> Unit = {}) {
        Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT)) {
            RegisterFormScreenContent(
                formData = RegistrationFormData(
                    fullName = "Trần Minh Anh",
                    username = "minhanh.tran",
                    email = "minhanh@example.com",
                    acceptTerms = true,
                ),
                uiState = RegisterUiState(),
                passwordState = TextFieldState("QaSmoke1!a"),
                evaluation = PasswordRules.evaluate("QaSmoke1!a"),
                onFullNameChanged = {},
                onUsernameChanged = {},
                onEmailChanged = {},
                onTogglePasswordVisibility = {},
                onAcceptTermsChanged = {},
                onSubmit = onSubmit,
                onBackToLogin = {},
            )
        }
    }

    @Composable
    private fun LockSurface(
        onUnlockClick: () -> Unit = {},
        onEnterPasswordClick: () -> Unit = {},
    ) {
        Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT)) {
            LockScreenContent(
                displayName = "Trần Minh Anh",
                username = "minhanh.tran",
                feedback = null,
                isUnlocking = false,
                isSwitchingAccount = false,
                onUnlockClick = onUnlockClick,
                onEnterPasswordClick = onEnterPasswordClick,
                onOtherAccountClick = {},
            )
        }
    }

    @Composable
    private fun HomeSurface() {
        Thi360App(
            preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT),
            startDestination = Destination.Home.route,
        )
    }

    @Composable
    private fun AccountSurface(onLogoutClick: () -> Unit = {}) {
        Thi360App(
            preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT),
            startDestination = Destination.Account.route,
            accountContent = { isTwoPane ->
                AccountScreenContent(
                    isTwoPaneWidth = isTwoPane,
                    profileState = ProfileLoadState.Loaded(sampleProfile),
                    preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT),
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
                    onLogoutClick = onLogoutClick,
                    detailContent = {},
                )
            },
        )
    }

    companion object {
        private const val SNAPSHOT_DIR_PROPERTY = "thi360.qa.snapshotDir"
        private const val SNAPSHOT_DIR_ENV = "THI360_QA_SNAPSHOT_DIR"
        private val MIN_TOUCH_TARGET_DP: Dp = 48.dp
        private val DIMENSION_TOLERANCE_DP: Dp = 0.5.dp
        private val CLIP_TOLERANCE_DP: Dp = 1.dp
    }
}
