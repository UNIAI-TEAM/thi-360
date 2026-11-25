package vn.unicom.thi360

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import vn.unicom.thi360.core.biometric.AndroidBiometricAuthenticator
import vn.unicom.thi360.core.biometric.RecentsPrivacyPolicy
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.session.AppSessionContainer
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.OrientationPolicy

class MainActivity : FragmentActivity() {

    private var isAppBackgrounded by mutableStateOf(false)
    private var hasCompletedInitialStart = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OrientationPolicy.applyTo(this)
        enableEdgeToEdge()

        val app = application as Thi360Application
        val preferencesRepository = app.preferencesRepository
        val appearanceRepository = app.appearanceRepository
        val biometricController = app.biometricController
        val sessionStack = AppSessionContainer.get(this)

        if (savedInstanceState == null) {
            lifecycleScope.launch {
                appearanceRepository.refresh(force = true)
            }
        }

        setContent {
            val preferences by preferencesRepository.preferences.collectAsStateWithLifecycle(
                initialValue = AppPreferences(),
            )
            val brandPalette by appearanceRepository.current.collectAsStateWithLifecycle()
            val biometricState by biometricController.state.collectAsStateWithLifecycle()
            val sessionState by sessionStack.sessionManager.state.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()

            val biometricAuthenticator = remember(this@MainActivity, preferences.effectiveLocale) {
                val localizedConfig = Configuration(resources.configuration).apply {
                    setLocales(LocaleList(preferences.effectiveLocale.toJavaLocale()))
                }
                val localizedContext = createConfigurationContext(localizedConfig)
                AndroidBiometricAuthenticator(
                    activity = this@MainActivity,
                    localizedContextProvider = { localizedContext },
                )
            }

            LaunchedEffect(biometricState, sessionState) {
                val protect = RecentsPrivacyPolicy.shouldProtectWindow(
                    biometricState = biometricState,
                    sessionState = sessionState,
                )
                applyRecentsPrivacy(protect)
            }

            AppRoot(
                preferences = preferences,
                brandPalette = brandPalette,
                onSelectLocale = { locale ->
                    scope.launch { preferencesRepository.setLocale(locale) }
                },
                onSelectThemeMode = { themeMode ->
                    scope.launch { preferencesRepository.setThemeMode(themeMode) }
                },
                sessionManager = sessionStack.sessionManager,
                biometricController = biometricController,
                biometricAuthenticator = biometricAuthenticator,
                isAppBackgrounded = isAppBackgrounded,
            )
        }
    }

    override fun onStart() {
        super.onStart()
        isAppBackgrounded = false
        val app = application as Thi360Application
        val sessionManager = AppSessionContainer.get(this).sessionManager
        val biometricController = app.biometricController
        if (hasCompletedInitialStart) {
            if (biometricController.shouldLockOnForeground(nowMillis = SystemClock.elapsedRealtime())) {
                sessionManager.lockSession()
            }
            lifecycleScope.launch {
                app.appearanceRepository.refreshOnResume()
            }
        } else {
            hasCompletedInitialStart = true
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            isAppBackgrounded = true
            val app = application as Thi360Application
            app.biometricController.onAppBackgrounded(SystemClock.elapsedRealtime())
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        OrientationPolicy.applyTo(this)
    }

    internal fun applyRecentsPrivacy(protect: Boolean) {
        if (protect) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(!protect)
        }
    }
}
