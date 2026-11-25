package vn.unicom.thi360.core.biometric

import javax.crypto.Cipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class FakeBiometricAvailabilityChecker(
    var availability: BiometricAvailability = BiometricAvailability.Available,
) : BiometricAvailabilityChecker {
    override fun check(): BiometricAvailability = availability
}

internal class InMemoryBiometricPromptMemoryStore(
    initial: PromptMemory = PromptMemory(),
) : BiometricPromptMemoryStore {
    private val _memory = MutableStateFlow(initial)
    override val memory: Flow<PromptMemory> = _memory.asStateFlow()

    var recordCount: Int = 0
        private set

    override suspend fun current(): PromptMemory = _memory.value

    override suspend fun record(answer: PromptAnswer) {
        recordCount++
        _memory.value = PromptMemory(lastAnswer = answer)
    }
}

internal class FakeBiometricVault(
    initialWrappedToken: String? = null,
) : BiometricVault {
    var storedToken: String? = initialWrappedToken
    var simulateKeyInvalidated: Boolean = false
    var clearCount: Int = 0
        private set
    var commitCount: Int = 0
        private set

    override fun hasWrappedToken(): Boolean = storedToken != null

    override fun prepareEncryptCipher(): Cipher? = null

    override fun commitEncryptedToken(cipher: Cipher?, refreshToken: String) {
        commitCount++
        storedToken = refreshToken
        simulateKeyInvalidated = false
    }

    override fun prepareDecryptCipher(): Cipher? {
        if (simulateKeyInvalidated || storedToken == null) {
            throw BiometricKeyInvalidatedException("Simulated biometric enrollment change")
        }
        return null
    }

    override fun decryptStoredToken(cipher: Cipher?): String {
        if (simulateKeyInvalidated || storedToken == null) {
            throw BiometricKeyInvalidatedException("Simulated biometric enrollment change")
        }
        return checkNotNull(storedToken)
    }

    override fun clear() {
        clearCount++
        storedToken = null
        simulateKeyInvalidated = false
    }
}

internal class FakeBiometricAuthenticator(
    var nextResult: BiometricPromptResult = BiometricPromptResult.Success(null),
) : BiometricAuthenticator {
    val calls = mutableListOf<BiometricPromptMode>()

    override suspend fun authenticate(
        mode: BiometricPromptMode,
        cipher: Cipher?,
    ): BiometricPromptResult {
        calls.add(mode)
        return nextResult
    }
}
