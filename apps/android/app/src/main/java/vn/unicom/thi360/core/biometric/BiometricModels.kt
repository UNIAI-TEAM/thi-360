package vn.unicom.thi360.core.biometric

import javax.crypto.Cipher
import kotlinx.coroutines.flow.Flow

enum class BiometricUnavailableReason {
    NO_HARDWARE,
    HARDWARE_UNAVAILABLE,
    NOT_ENROLLED,
    SECURITY_UPDATE_REQUIRED,
    UNSUPPORTED,
}

sealed interface BiometricState {
    data class Unavailable(val reason: BiometricUnavailableReason) : BiometricState
    data object Off : BiometricState
    data object On : BiometricState
}

enum class PromptAnswer(val wireValue: String) {
    YES("yes"),
    LATER("later");

    companion object {
        fun fromWireValue(value: String?): PromptAnswer? =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) }
    }
}

data class PromptMemory(
    val lastAnswer: PromptAnswer? = null,
)

sealed interface BiometricAvailability {
    data object Available : BiometricAvailability
    data class Unavailable(val reason: BiometricUnavailableReason) : BiometricAvailability
}

enum class BiometricAuthFailureReason {
    CANCELLED,
    FAILED,
    LOCKOUT,
    LOCKOUT_PERMANENT,
    ERROR,
}

enum class BiometricPromptMode {
    ENABLE,
    UNLOCK,
}

sealed interface BiometricPromptResult {
    data class Success(val cipher: Cipher? = null) : BiometricPromptResult
    data class Failure(val reason: BiometricAuthFailureReason) : BiometricPromptResult
}

sealed interface BiometricEnableOutcome {
    data object Enabled : BiometricEnableOutcome
    data class Failed(val reason: BiometricAuthFailureReason) : BiometricEnableOutcome
    data class Unavailable(val reason: BiometricUnavailableReason) : BiometricEnableOutcome
    data object MissingSession : BiometricEnableOutcome
}

sealed interface BiometricUnlockOutcome {
    data class Unlocked(val refreshToken: String) : BiometricUnlockOutcome {
        override fun toString(): String = "Unlocked(refreshToken=[REDACTED])"
    }

    data object KeyInvalidated : BiometricUnlockOutcome
    data class Failed(val reason: BiometricAuthFailureReason) : BiometricUnlockOutcome
}

interface BiometricAvailabilityChecker {
    fun check(): BiometricAvailability
}

interface BiometricAuthenticator {
    suspend fun authenticate(
        mode: BiometricPromptMode,
        cipher: Cipher?,
    ): BiometricPromptResult
}

interface BiometricPromptMemoryStore {
    val memory: Flow<PromptMemory>
    suspend fun current(): PromptMemory
    suspend fun record(answer: PromptAnswer)
}
