package vn.unicom.thi360.core.biometric

import android.security.keystore.KeyPermanentlyInvalidatedException
import java.security.GeneralSecurityException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BiometricController(
    private val availabilityChecker: BiometricAvailabilityChecker,
    private val vault: BiometricVault,
    private val promptMemoryStore: BiometricPromptMemoryStore,
) {
    private val operationMutex = Mutex()

    @Volatile
    private var backgroundedAtMillis: Long? = null

    @Volatile
    private var pendingPostPasswordChangePrompt: Boolean = false

    private val _state = MutableStateFlow(computeCurrentState())
    val state: StateFlow<BiometricState> = _state.asStateFlow()

    private val _shouldPromptEnable = MutableStateFlow(false)
    val shouldPromptEnable: StateFlow<Boolean> = _shouldPromptEnable.asStateFlow()

    fun isBiometricEnabled(): Boolean = _state.value is BiometricState.On

    fun shouldStartLocked(hasStoredSession: Boolean): Boolean =
        hasStoredSession && LockPolicy.shouldLock(
            biometricEnabled = isBiometricEnabled(),
            backgroundedAtMillis = null,
            nowMillis = 0L,
            isColdStart = true,
        )

    fun refreshAvailability(): BiometricState {
        val resolved = computeCurrentState()
        _state.value = resolved
        if (resolved !is BiometricState.Off) {
            _shouldPromptEnable.value = false
        }
        return resolved
    }

    suspend fun onPasswordLoginCompleted(mustChangePassword: Boolean) {
        if (mustChangePassword) {
            pendingPostPasswordChangePrompt = true
            _shouldPromptEnable.value = false
            return
        }
        pendingPostPasswordChangePrompt = false
        evaluateEnablePrompt()
    }

    suspend fun onForcedPasswordChangeCompleted() {
        if (!pendingPostPasswordChangePrompt) return
        pendingPostPasswordChangePrompt = false
        evaluateEnablePrompt()
    }

    private suspend fun evaluateEnablePrompt() {
        val currentState = refreshAvailability()
        if (currentState !is BiometricState.Off) {
            _shouldPromptEnable.value = false
            return
        }
        val memory = promptMemoryStore.current()
        _shouldPromptEnable.value = memory.lastAnswer != PromptAnswer.LATER
    }

    suspend fun declineEnablePrompt() {
        promptMemoryStore.record(PromptAnswer.LATER)
        _shouldPromptEnable.value = false
    }

    fun dismissEnablePromptWithoutRecording() {
        _shouldPromptEnable.value = false
    }

    suspend fun enable(
        refreshToken: String?,
        authenticator: BiometricAuthenticator,
    ): BiometricEnableOutcome = operationMutex.withLock {
        val availability = availabilityChecker.check()
        if (availability is BiometricAvailability.Unavailable) {
            _state.value = BiometricState.Unavailable(availability.reason)
            _shouldPromptEnable.value = false
            return BiometricEnableOutcome.Unavailable(availability.reason)
        }
        if (refreshToken.isNullOrBlank()) {
            return BiometricEnableOutcome.MissingSession
        }

        val encryptCipher = try {
            vault.prepareEncryptCipher()
        } catch (_: Throwable) {
            vault.clear()
            _state.value = BiometricState.Off
            return BiometricEnableOutcome.Failed(BiometricAuthFailureReason.ERROR)
        }

        when (val result = authenticator.authenticate(BiometricPromptMode.ENABLE, encryptCipher)) {
            is BiometricPromptResult.Success -> {
                try {
                    vault.commitEncryptedToken(result.cipher ?: encryptCipher, refreshToken)
                    promptMemoryStore.record(PromptAnswer.YES)
                    _state.value = BiometricState.On
                    _shouldPromptEnable.value = false
                    BiometricEnableOutcome.Enabled
                } catch (_: Throwable) {
                    vault.clear()
                    _state.value = BiometricState.Off
                    BiometricEnableOutcome.Failed(BiometricAuthFailureReason.ERROR)
                }
            }

            is BiometricPromptResult.Failure -> {
                vault.clear()
                _state.value = BiometricState.Off
                BiometricEnableOutcome.Failed(result.reason)
            }
        }
    }

    fun disable() {
        vault.clear()
        _shouldPromptEnable.value = false
        _state.value = when (val availability = availabilityChecker.check()) {
            BiometricAvailability.Available -> BiometricState.Off
            is BiometricAvailability.Unavailable -> BiometricState.Unavailable(availability.reason)
        }
    }

    fun clearForSessionEnd() {
        vault.clear()
        backgroundedAtMillis = null
        pendingPostPasswordChangePrompt = false
        _shouldPromptEnable.value = false
        _state.value = when (val availability = availabilityChecker.check()) {
            BiometricAvailability.Available -> BiometricState.Off
            is BiometricAvailability.Unavailable -> BiometricState.Unavailable(availability.reason)
        }
    }

    suspend fun unlock(authenticator: BiometricAuthenticator): BiometricUnlockOutcome = operationMutex.withLock {
        if (!vault.hasWrappedToken()) {
            handleKeyInvalidatedLocked()
            return BiometricUnlockOutcome.KeyInvalidated
        }

        val decryptCipher = try {
            vault.prepareDecryptCipher()
        } catch (_: KeyPermanentlyInvalidatedException) {
            handleKeyInvalidatedLocked()
            return BiometricUnlockOutcome.KeyInvalidated
        } catch (_: BiometricKeyInvalidatedException) {
            handleKeyInvalidatedLocked()
            return BiometricUnlockOutcome.KeyInvalidated
        } catch (_: GeneralSecurityException) {
            handleKeyInvalidatedLocked()
            return BiometricUnlockOutcome.KeyInvalidated
        }

        when (val result = authenticator.authenticate(BiometricPromptMode.UNLOCK, decryptCipher)) {
            is BiometricPromptResult.Success -> {
                try {
                    val token = vault.decryptStoredToken(result.cipher ?: decryptCipher)
                    BiometricUnlockOutcome.Unlocked(token)
                } catch (_: KeyPermanentlyInvalidatedException) {
                    handleKeyInvalidatedLocked()
                    BiometricUnlockOutcome.KeyInvalidated
                } catch (_: BiometricKeyInvalidatedException) {
                    handleKeyInvalidatedLocked()
                    BiometricUnlockOutcome.KeyInvalidated
                } catch (_: GeneralSecurityException) {
                    handleKeyInvalidatedLocked()
                    BiometricUnlockOutcome.KeyInvalidated
                }
            }

            is BiometricPromptResult.Failure -> BiometricUnlockOutcome.Failed(result.reason)
        }
    }

    fun onAppBackgrounded(nowMillis: Long) {
        backgroundedAtMillis = nowMillis
    }

    fun shouldLockOnForeground(
        nowMillis: Long,
        isColdStart: Boolean = false,
    ): Boolean {
        val capturedBackgroundedAt = backgroundedAtMillis
        backgroundedAtMillis = null
        return LockPolicy.shouldLock(
            biometricEnabled = isBiometricEnabled(),
            backgroundedAtMillis = capturedBackgroundedAt,
            nowMillis = nowMillis,
            isColdStart = isColdStart,
        )
    }

    private fun handleKeyInvalidatedLocked() {
        vault.clear()
        _shouldPromptEnable.value = false
        _state.value = when (val availability = availabilityChecker.check()) {
            BiometricAvailability.Available -> BiometricState.Off
            is BiometricAvailability.Unavailable -> BiometricState.Unavailable(availability.reason)
        }
    }

    private fun computeCurrentState(): BiometricState {
        if (vault.hasWrappedToken()) {
            return BiometricState.On
        }
        return when (val availability = availabilityChecker.check()) {
            BiometricAvailability.Available -> BiometricState.Off
            is BiometricAvailability.Unavailable -> BiometricState.Unavailable(availability.reason)
        }
    }
}
