package vn.unicom.thi360.core.biometric

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.biometricPromptMemoryDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "thi360_biometric_prompt_memory",
)

class DataStoreBiometricPromptMemoryStore(
    private val dataStore: DataStore<Preferences>,
) : BiometricPromptMemoryStore {

    override val memory: Flow<PromptMemory> = dataStore.data
        .catch { cause ->
            if (cause is IOException) {
                emit(emptyPreferences())
            } else {
                throw cause
            }
        }
        .map { prefs ->
            PromptMemory(lastAnswer = PromptAnswer.fromWireValue(prefs[KEY_LAST_ANSWER]))
        }

    override suspend fun current(): PromptMemory = memory.first()

    override suspend fun record(answer: PromptAnswer) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_ANSWER] = answer.wireValue
        }
    }

    companion object {
        private val KEY_LAST_ANSWER = stringPreferencesKey("last_answer")

        fun create(context: Context): DataStoreBiometricPromptMemoryStore =
            DataStoreBiometricPromptMemoryStore(context.applicationContext.biometricPromptMemoryDataStore)
    }
}
