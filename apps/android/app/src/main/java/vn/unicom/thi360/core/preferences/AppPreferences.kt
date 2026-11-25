package vn.unicom.thi360.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

enum class AppLocale(val languageTag: String) {
    VI("vi"),
    EN("en");

    fun toJavaLocale(): Locale = Locale.forLanguageTag(languageTag)

    companion object {
        val Default: AppLocale = VI

        fun fromTag(tag: String?): AppLocale? =
            entries.firstOrNull { it.languageTag.equals(tag, ignoreCase = true) }
    }
}

enum class ThemeMode(val wireValue: String) {
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromWireValue(value: String?): ThemeMode? =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) }
    }
}

data class AppPreferences(
    val locale: AppLocale? = null,
    val themeMode: ThemeMode? = null,
) {
    val effectiveLocale: AppLocale
        get() = locale ?: AppLocale.Default

    fun effectiveThemeMode(defaultMode: ThemeMode = ThemeMode.LIGHT): ThemeMode =
        themeMode ?: defaultMode
}

interface AppPreferencesRepository {
    val preferences: Flow<AppPreferences>
    suspend fun setLocale(locale: AppLocale)
    suspend fun setThemeMode(themeMode: ThemeMode?)
}

private val Context.appPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "thi360_app_preferences",
)

class DataStoreAppPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) : AppPreferencesRepository {

    override val preferences: Flow<AppPreferences> = dataStore.data
        .catch { cause ->
            if (cause is IOException) {
                emit(emptyPreferences())
            } else {
                throw cause
            }
        }
        .map { prefs ->
            AppPreferences(
                locale = AppLocale.fromTag(prefs[KEY_LOCALE]),
                themeMode = ThemeMode.fromWireValue(prefs[KEY_THEME_MODE]),
            )
        }

    override suspend fun setLocale(locale: AppLocale) {
        dataStore.edit { prefs ->
            prefs[KEY_LOCALE] = locale.languageTag
        }
    }

    override suspend fun setThemeMode(themeMode: ThemeMode?) {
        dataStore.edit { prefs ->
            if (themeMode == null) {
                prefs.remove(KEY_THEME_MODE)
            } else {
                prefs[KEY_THEME_MODE] = themeMode.wireValue
            }
        }
    }

    companion object {
        private val KEY_LOCALE = stringPreferencesKey("locale")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")

        fun create(context: Context): DataStoreAppPreferencesRepository =
            DataStoreAppPreferencesRepository(context.applicationContext.appPreferencesDataStore)
    }
}
