package vn.unicom.thi360

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.OrientationPolicy

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OrientationPolicy.applyTo(this)
        enableEdgeToEdge()

        val preferencesRepository = (application as Thi360Application).preferencesRepository

        setContent {
            val preferences by preferencesRepository.preferences.collectAsStateWithLifecycle(
                initialValue = AppPreferences(),
            )
            val scope = rememberCoroutineScope()

            AppRoot(
                preferences = preferences,
                onSelectLocale = { locale ->
                    scope.launch { preferencesRepository.setLocale(locale) }
                },
                onSelectThemeMode = { themeMode ->
                    scope.launch { preferencesRepository.setThemeMode(themeMode) }
                },
            )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        OrientationPolicy.applyTo(this)
    }
}
