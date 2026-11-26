package vn.unicom.thi360.feature.lock

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.biometric.BiometricAvailability
import vn.unicom.thi360.core.biometric.BiometricController
import vn.unicom.thi360.core.biometric.BiometricPromptResult
import vn.unicom.thi360.core.biometric.BiometricState
import vn.unicom.thi360.core.biometric.FakeBiometricAuthenticator
import vn.unicom.thi360.core.biometric.FakeBiometricAvailabilityChecker
import vn.unicom.thi360.core.biometric.FakeBiometricVault
import vn.unicom.thi360.core.biometric.InMemoryBiometricPromptMemoryStore
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.session.SessionState
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.feature.login.LoginTestTags
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class LockScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var server: ContractFakeServer

    @Before
    fun setUp() {
        server = ContractFakeServer().start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun timeoutLockHidesAppContentShowsThreeButtonsAndUnlockRestoresExactScreen() {
        val vault = FakeBiometricVault(initialWrappedToken = "refresh-1")
        val controller = BiometricController(
            availabilityChecker = FakeBiometricAvailabilityChecker(BiometricAvailability.Available),
            vault = vault,
            promptMemoryStore = InMemoryBiometricPromptMemoryStore(),
        )
        val tokenStore = InMemoryTokenStore(
            Tokens("access-1", "refresh-1", 300, 604800, false, "student.demo"),
        )
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.VI },
            onSessionCleared = { controller.clearForSessionEnd() },
        )
        val authenticator = FakeBiometricAuthenticator(BiometricPromptResult.Success(null))
        var backgrounded by mutableStateOf(false)

        composeRule.setContent {
            AppRoot(
                preferences = AppPreferences(locale = AppLocale.VI),
                onSelectLocale = {},
                onSelectThemeMode = {},
                sessionManager = stack.sessionManager,
                validateOnLaunch = false,
                biometricController = controller,
                biometricAuthenticator = authenticator,
                isAppBackgrounded = backgrounded,
            )
        }

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.History)).performClick()
        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.History)).assertIsSelected()
        composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.History)).assertIsDisplayed()

        composeRule.runOnIdle {
            backgrounded = true
            controller.onAppBackgrounded(nowMillis = 10_000L)
        }
        composeRule.onNodeWithTag(LockTestTags.RECENTS_PRIVACY_SHIELD).assertIsDisplayed()

        composeRule.runOnIdle {
            backgrounded = false
            assertFalse(controller.shouldLockOnForeground(nowMillis = 10_000L + 299_000L))
        }
        composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.History)).assertIsDisplayed()

        composeRule.runOnIdle {
            controller.onAppBackgrounded(nowMillis = 500_000L)
            assertTrue(controller.shouldLockOnForeground(nowMillis = 500_000L + 300_000L))
            stack.sessionManager.lockSession()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(LockTestTags.LOCK_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(LockTestTags.UNLOCK_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithTag(LockTestTags.ENTER_PASSWORD_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithTag(LockTestTags.OTHER_ACCOUNT_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithTag(ShellTestTags.BOTTOM_BAR).assertDoesNotExist()
        composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.History)).assertDoesNotExist()

        server.respondAuthFixture("POST", "/api/v1/auth/mobile/refresh", 200, "refresh-200.json")
        composeRule.onNodeWithTag(LockTestTags.UNLOCK_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            stack.sessionManager.state.value is SessionState.Active
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.History)).assertIsSelected()
        composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.History)).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun otherAccount() {
        val vault = FakeBiometricVault(initialWrappedToken = "locked-refresh")
        val controller = BiometricController(
            availabilityChecker = FakeBiometricAvailabilityChecker(BiometricAvailability.Available),
            vault = vault,
            promptMemoryStore = InMemoryBiometricPromptMemoryStore(),
        )
        val tokenStore = InMemoryTokenStore(
            Tokens("locked-access", "locked-refresh", 300, 604800, false, "student.locked"),
        )
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.VI },
            onSessionCleared = { controller.clearForSessionEnd() },
            startLocked = true,
        )
        server.respondEmpty("POST", "/api/v1/auth/mobile/logout", 204)

        composeRule.setContent {
            AppRoot(
                preferences = AppPreferences(locale = AppLocale.VI),
                onSelectLocale = {},
                onSelectThemeMode = {},
                sessionManager = stack.sessionManager,
                validateOnLaunch = false,
                biometricController = controller,
                biometricAuthenticator = FakeBiometricAuthenticator(),
            )
        }

        composeRule.onNodeWithTag(LockTestTags.OTHER_ACCOUNT_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            stack.sessionManager.state.value is SessionState.SignedOut &&
                server.requestCount("POST", "/api/v1/auth/mobile/logout") == 1
        }
        composeRule.waitForIdle()

        assertNull("Held session must be cleared", tokenStore.read())
        assertEquals(BiometricState.Off, controller.state.value)
        assertFalse(vault.hasWrappedToken())
        assertEquals(
            "/api/v1/auth/mobile/logout",
            server.requests("POST", "/api/v1/auth/mobile/logout").single().path,
        )
        composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD)
            .assertIsDisplayed()
            .assertTextContains("", substring = false)
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun labels() {
        composeRule.setContent {
            vn.unicom.thi360.core.theme.Thi360Theme(
                preferences = AppPreferences(locale = AppLocale.VI),
            ) {
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

        composeRule.onNodeWithTag(LockTestTags.LOCK_BODY)
            .assertTextContains("vân tay hoặc khuôn mặt", substring = true)
        composeRule.onNodeWithTag(LockTestTags.UNLOCK_BUTTON)
            .assertTextContains("Mở khóa", substring = true)
        composeRule.onNodeWithTag(LockTestTags.ENTER_PASSWORD_BUTTON)
            .assertTextContains("Nhập mật khẩu", substring = true)
        composeRule.onNodeWithTag(LockTestTags.OTHER_ACCOUNT_BUTTON)
            .assertTextContains("Đăng nhập bằng tài khoản khác", substring = true)
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun rotationWhileLockedStillRestoresPriorTabOnUnlock() {
        val vault = FakeBiometricVault(initialWrappedToken = "refresh-rotate")
        val controller = BiometricController(
            availabilityChecker = FakeBiometricAvailabilityChecker(BiometricAvailability.Available),
            vault = vault,
            promptMemoryStore = InMemoryBiometricPromptMemoryStore(),
        )
        val tokenStore = InMemoryTokenStore(
            Tokens("access-rotate", "refresh-rotate", 300, 604800, false, "student.rotate"),
        )
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.VI },
            onSessionCleared = { controller.clearForSessionEnd() },
        )
        val restorationTester = StateRestorationTester(composeRule)

        restorationTester.setContent {
            AppRoot(
                preferences = AppPreferences(locale = AppLocale.VI),
                onSelectLocale = {},
                onSelectThemeMode = {},
                sessionManager = stack.sessionManager,
                validateOnLaunch = false,
                biometricController = controller,
                biometricAuthenticator = FakeBiometricAuthenticator(BiometricPromptResult.Success(null)),
            )
        }

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.History)).performClick()
        composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.History)).assertIsDisplayed()

        composeRule.runOnIdle { stack.sessionManager.lockSession() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(LockTestTags.LOCK_SCREEN).assertIsDisplayed()

        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(LockTestTags.LOCK_SCREEN).assertIsDisplayed()

        server.respondAuthFixture("POST", "/api/v1/auth/mobile/refresh", 200, "refresh-200.json")
        composeRule.onNodeWithTag(LockTestTags.UNLOCK_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            stack.sessionManager.state.value is SessionState.Active
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.History)).assertIsSelected()
        composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.History)).assertIsDisplayed()
    }
}
