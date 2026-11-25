package vn.unicom.thi360

import android.app.Application
import vn.unicom.thi360.core.appearance.AppearanceRepository
import vn.unicom.thi360.core.biometric.AndroidBiometricAvailabilityChecker
import vn.unicom.thi360.core.biometric.BiometricController
import vn.unicom.thi360.core.biometric.DataStoreBiometricPromptMemoryStore
import vn.unicom.thi360.core.biometric.KeystoreBiometricVault
import vn.unicom.thi360.core.preferences.AppPreferencesRepository
import vn.unicom.thi360.core.preferences.DataStoreAppPreferencesRepository

class Thi360Application : Application() {
    val preferencesRepository: AppPreferencesRepository by lazy {
        DataStoreAppPreferencesRepository.create(this)
    }

    val appearanceRepository: AppearanceRepository by lazy {
        AppearanceRepository.create(this)
    }

    val biometricController: BiometricController by lazy {
        BiometricController(
            availabilityChecker = AndroidBiometricAvailabilityChecker(this),
            vault = KeystoreBiometricVault.create(this),
            promptMemoryStore = DataStoreBiometricPromptMemoryStore.create(this),
        )
    }
}
