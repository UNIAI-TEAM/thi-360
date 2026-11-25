package vn.unicom.thi360

import android.app.Application
import androidx.annotation.VisibleForTesting
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

    private var testAppearanceRepository: AppearanceRepository? = null
    private val defaultAppearanceRepository: AppearanceRepository by lazy {
        AppearanceRepository.create(this)
    }
    val appearanceRepository: AppearanceRepository
        get() = testAppearanceRepository ?: defaultAppearanceRepository

    private var testBiometricController: BiometricController? = null
    private val defaultBiometricController: BiometricController by lazy {
        BiometricController(
            availabilityChecker = AndroidBiometricAvailabilityChecker(this),
            vault = KeystoreBiometricVault.create(this),
            promptMemoryStore = DataStoreBiometricPromptMemoryStore.create(this),
        )
    }
    val biometricController: BiometricController
        get() = testBiometricController ?: defaultBiometricController

    @VisibleForTesting
    internal fun setTestOverrides(
        appearanceRepository: AppearanceRepository? = null,
        biometricController: BiometricController? = null,
    ) {
        testAppearanceRepository = appearanceRepository
        testBiometricController = biometricController
    }
}
