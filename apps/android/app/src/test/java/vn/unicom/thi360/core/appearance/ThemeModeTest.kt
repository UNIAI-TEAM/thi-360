package vn.unicom.thi360.core.appearance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.preferences.DataStoreAppPreferencesRepository
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.core.session.MobileAuthGateway
import vn.unicom.thi360.core.session.SessionManager
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.core.theme.BrandPalette
import vn.unicom.thi360.core.theme.Thi360Theme

@RunWith(RobolectricTestRunner::class)
class ThemeModeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    @Config(qualifiers = "w412dp-h915dp-night")
    fun unsetUserThemeFollowsAdminModeAndIgnoresSystemDarkMode() {
        var palette by mutableStateOf(BrandPalette.Default.copy(defaultMode = ThemeMode.LIGHT))
        var resolvedMode: ThemeMode? = null
        var resolvedBg = BrandPalette.DefaultDarkColors.bgPage

        composeRule.setContent {
            Thi360Theme(
                preferences = AppPreferences(themeMode = null),
                brandPalette = palette,
            ) {
                resolvedMode = Thi360Theme.themeMode
                resolvedBg = Thi360Theme.colors.bgPage
            }
        }
        composeRule.waitForIdle()

        // Even with -night system qualifier, unset user preference follows admin mode = light.
        assertEquals(ThemeMode.LIGHT, resolvedMode)
        assertEquals(BrandPalette.DefaultLightColors.bgPage, resolvedBg)

        // When admin updates mode to dark, unset user preference follows dark.
        composeRule.runOnIdle {
            palette = palette.copy(defaultMode = ThemeMode.DARK)
        }
        composeRule.waitForIdle()
        assertEquals(ThemeMode.DARK, resolvedMode)
        assertEquals(BrandPalette.DefaultDarkColors.bgPage, resolvedBg)
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-night")
    fun explicitUserChoiceOverridesAdminModeChangesAndSurvivesRestartAndLogout() = runTest {
        val prefsFile = File(tempFolder.newFolder("theme_prefs"), "prefs.preferences_pb")

        val job1 = SupervisorJob()
        val scope1 = CoroutineScope(job1 + Dispatchers.IO)
        try {
            val store1 = PreferenceDataStoreFactory.create(scope = scope1) { prefsFile }
            val repo1 = DataStoreAppPreferencesRepository(store1)
            repo1.setThemeMode(ThemeMode.LIGHT)

            val sessionManager = SessionManager(
                tokenStore = InMemoryTokenStore(
                    Tokens("a", "r", 300, 604800, false, "student.demo"),
                ),
                authGateway = object : MobileAuthGateway {
                    override suspend fun login(identifier: String, password: String): ApiResult<Tokens> = error("unused")
                    override suspend fun refresh(refreshToken: String): ApiResult<Tokens> = error("unused")
                    override suspend fun logout(refreshToken: String): ApiResult<Unit> = ApiResult.Success(Unit)
                },
            )
            sessionManager.logout()
            assertEquals(ThemeMode.LIGHT, repo1.preferences.first().themeMode)
        } finally {
            withContext(NonCancellable) { job1.cancelAndJoin() }
        }

        // Relaunch with new DataStore reading persisted file + admin config changed to DARK.
        val job2 = SupervisorJob()
        val scope2 = CoroutineScope(job2 + Dispatchers.IO)
        try {
            val store2 = PreferenceDataStoreFactory.create(scope = scope2) { prefsFile }
            val repo2 = DataStoreAppPreferencesRepository(store2)
            val restored = repo2.preferences.first()
            assertEquals(ThemeMode.LIGHT, restored.themeMode)

            var resolvedMode: ThemeMode? = null
            composeRule.setContent {
                Thi360Theme(
                    preferences = restored,
                    brandPalette = BrandPalette.Default.copy(defaultMode = ThemeMode.DARK),
                ) {
                    resolvedMode = Thi360Theme.themeMode
                }
            }
            composeRule.waitForIdle()
            assertEquals(
                "User choice LIGHT must override admin mode DARK after restart and logout",
                ThemeMode.LIGHT,
                resolvedMode,
            )
        } finally {
            withContext(NonCancellable) { job2.cancelAndJoin() }
        }
    }
}
