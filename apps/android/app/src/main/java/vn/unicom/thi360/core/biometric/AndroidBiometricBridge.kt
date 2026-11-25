package vn.unicom.thi360.core.biometric

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import vn.unicom.thi360.R

class AndroidBiometricAvailabilityChecker(
    private val context: Context,
) : BiometricAvailabilityChecker {

    override fun check(): BiometricAvailability {
        val status = BiometricManager.from(context.applicationContext)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        return mapStatus(status)
    }

    companion object {
        internal fun mapStatus(status: Int): BiometricAvailability = when (status) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.Available
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                BiometricAvailability.Unavailable(BiometricUnavailableReason.NO_HARDWARE)
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                BiometricAvailability.Unavailable(BiometricUnavailableReason.HARDWARE_UNAVAILABLE)
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                BiometricAvailability.Unavailable(BiometricUnavailableReason.NOT_ENROLLED)
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                BiometricAvailability.Unavailable(BiometricUnavailableReason.SECURITY_UPDATE_REQUIRED)
            else -> BiometricAvailability.Unavailable(BiometricUnavailableReason.UNSUPPORTED)
        }
    }
}

class AndroidBiometricAuthenticator(
    private val activity: FragmentActivity,
    private val localizedContextProvider: () -> Context = { activity },
) : BiometricAuthenticator {

    override suspend fun authenticate(
        mode: BiometricPromptMode,
        cipher: Cipher?,
    ): BiometricPromptResult = suspendCancellableCoroutine { continuation ->
        val settled = AtomicBoolean(false)
        val stringsContext = localizedContextProvider()
        val titleRes = when (mode) {
            BiometricPromptMode.ENABLE -> R.string.biometric_os_prompt_enable_title
            BiometricPromptMode.UNLOCK -> R.string.biometric_os_prompt_unlock_title
        }
        val subtitleRes = when (mode) {
            BiometricPromptMode.ENABLE -> R.string.biometric_os_prompt_enable_subtitle
            BiometricPromptMode.UNLOCK -> R.string.biometric_os_prompt_unlock_subtitle
        }
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(stringsContext.getString(titleRes))
            .setSubtitle(stringsContext.getString(subtitleRes))
            .setNegativeButtonText(stringsContext.getString(R.string.biometric_os_prompt_cancel))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (settled.compareAndSet(false, true) && continuation.isActive) {
                        continuation.resume(BiometricPromptResult.Success(result.cryptoObject?.cipher ?: cipher))
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (settled.compareAndSet(false, true) && continuation.isActive) {
                        continuation.resume(BiometricPromptResult.Failure(mapErrorCode(errorCode)))
                    }
                }

                override fun onAuthenticationFailed() {
                    // System prompt remains open for retry until user cancels or OS locks out.
                }
            },
        )

        continuation.invokeOnCancellation {
            executor.execute {
                runCatching { prompt.cancelAuthentication() }
            }
        }

        executor.execute {
            runCatching {
                if (cipher != null) {
                    prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
                } else {
                    prompt.authenticate(promptInfo)
                }
            }.onFailure {
                if (settled.compareAndSet(false, true) && continuation.isActive) {
                    continuation.resume(BiometricPromptResult.Failure(BiometricAuthFailureReason.ERROR))
                }
            }
        }
    }

    companion object {
        internal fun mapErrorCode(errorCode: Int): BiometricAuthFailureReason = when (errorCode) {
            BiometricPrompt.ERROR_USER_CANCELED,
            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
            BiometricPrompt.ERROR_CANCELED,
            -> BiometricAuthFailureReason.CANCELLED
            BiometricPrompt.ERROR_LOCKOUT -> BiometricAuthFailureReason.LOCKOUT
            BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> BiometricAuthFailureReason.LOCKOUT_PERMANENT
            else -> BiometricAuthFailureReason.ERROR
        }
    }
}
