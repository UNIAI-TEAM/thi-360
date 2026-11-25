package vn.unicom.thi360

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.preferences.DataStoreAppPreferencesRepository
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.core.theme.BrandPalette
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.shell.AccountShellPlaceholder
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags
import vn.unicom.thi360.shell.Thi360App

@RunWith(RobolectricTestRunner::class)
class LocalePreferenceTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    @Config(qualifiers = "en-w412dp-h915dp")
    fun defaultsToVietnameseOnEnglishDeviceAndSwitchesImmediatelyWithoutLosingTab() {
        var preferences by mutableStateOf(AppPreferences())

        composeRule.setContent {
            Thi360App(
                preferences = preferences,
                onSelectLocale = { locale -> preferences = preferences.copy(locale = locale) },
                onSelectThemeMode = { mode -> preferences = preferences.copy(themeMode = mode) },
            )
        }

        composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.Home))
            .assertTextEquals("Trang chủ")
        composeRule.onNodeWithTag(ShellTestTags.PLACEHOLDER_EMPTY_TITLE)
            .assertTextEquals("Chưa có nội dung")

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Account)).performClick()
        composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.Account))
            .assertTextEquals("Tài khoản")

        composeRule.onNodeWithTag(ShellTestTags.LOCALE_OPTION_EN).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Account)).assertIsSelected()
        composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.Account))
            .assertTextEquals("Account")

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Home)).performClick()
        composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.Home))
            .assertTextEquals("Home")
        composeRule.onNodeWithTag(ShellTestTags.PLACEHOLDER_EMPTY_TITLE)
            .assertTextEquals("No content yet")
    }

    @Test
    @Config(qualifiers = "en-w412dp-h915dp-night")
    fun defaultThemeIsLightEvenWhenSystemIsDark() {
        var resolvedBgPage = BrandPalette.DefaultDarkColors.bgPage

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = null, themeMode = null)) {
                resolvedBgPage = Thi360Theme.colors.bgPage
            }
        }

        assertEquals(BrandPalette.DefaultLightColors.bgPage, resolvedBgPage)
    }

    @Test
    @Config(qualifiers = "en-w412dp-h915dp")
    fun unsetThemeModeFollowsBrandPaletteDefaultDarkMode() {
        var resolvedBgPage = BrandPalette.DefaultLightColors.bgPage
        val darkDefaultPalette = BrandPalette.Default.copy(defaultMode = ThemeMode.DARK)
        val prefs = AppPreferences(locale = null, themeMode = null)

        composeRule.setContent {
            Thi360App(
                preferences = prefs,
                brandPalette = darkDefaultPalette,
                startDestination = Destination.Account.route,
                accountContent = { twoPane ->
                    resolvedBgPage = Thi360Theme.colors.bgPage
                    AccountShellPlaceholder(
                        isTwoPaneWidth = twoPane,
                        preferences = prefs,
                        onSelectLocale = {},
                        onSelectThemeMode = {},
                    )
                },
            )
        }

        assertEquals(BrandPalette.DefaultDarkColors.bgPage, resolvedBgPage)
        composeRule.onNodeWithTag(ShellTestTags.THEME_OPTION_DARK).assertIsSelected()
        composeRule.onNodeWithTag(ShellTestTags.THEME_OPTION_LIGHT).assertIsNotSelected()
    }

    @Test
    @Config(qualifiers = "en-w412dp-h915dp")
    fun localeAndThemeModePersistAcrossDataStoreRestarts() = runTest {
        val prefsFile = File(tempFolder.newFolder("datastore"), "prefs.preferences_pb")

        val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store1 = PreferenceDataStoreFactory.create(scope = scope1) { prefsFile }
            val repo1 = DataStoreAppPreferencesRepository(store1)
            assertEquals(AppPreferences(locale = null, themeMode = null), repo1.preferences.first())
            assertEquals(AppLocale.VI, repo1.preferences.first().effectiveLocale)
            assertEquals(ThemeMode.LIGHT, repo1.preferences.first().effectiveThemeMode())

            repo1.setLocale(AppLocale.EN)
            repo1.setThemeMode(ThemeMode.DARK)
            assertEquals(AppPreferences(locale = AppLocale.EN, themeMode = ThemeMode.DARK), repo1.preferences.first())
        } finally {
            scope1.cancel()
        }

        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store2 = PreferenceDataStoreFactory.create(scope = scope2) { prefsFile }
            val repo2 = DataStoreAppPreferencesRepository(store2)
            val restored = repo2.preferences.first()
            assertEquals(AppLocale.EN, restored.locale)
            assertEquals(ThemeMode.DARK, restored.themeMode)

            composeRule.setContent {
                val prefs by repo2.preferences.collectAsStateWithLifecycle(initialValue = restored)
                val uiScope = rememberCoroutineScope()
                Thi360App(
                    preferences = prefs,
                    onSelectLocale = { uiScope.launch { repo2.setLocale(it) } },
                    onSelectThemeMode = { uiScope.launch { repo2.setThemeMode(it) } },
                )
            }

            composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.Home))
                .assertTextEquals("Home")
        } finally {
            scope2.cancel()
        }
    }
}
