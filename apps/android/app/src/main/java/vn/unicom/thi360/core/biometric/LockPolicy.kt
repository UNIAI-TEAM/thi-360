package vn.unicom.thi360.core.biometric

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import vn.unicom.thi360.core.session.SessionState

object LockPolicy {
    const val LOCK_TIMEOUT_MILLIS: Long = 5 * 60 * 1000L

    fun shouldLock(
        biometricEnabled: Boolean,
        backgroundedAtMillis: Long?,
        nowMillis: Long,
        isColdStart: Boolean = false,
    ): Boolean {
        if (!biometricEnabled) return false
        if (isColdStart) return true
        val backgroundedAt = backgroundedAtMillis ?: return false
        return (nowMillis - backgroundedAt) >= LOCK_TIMEOUT_MILLIS
    }
}

object RecentsPrivacyPolicy {
    fun shouldProtectWindow(
        biometricState: BiometricState,
        sessionState: SessionState,
    ): Boolean = biometricState is BiometricState.On || sessionState is SessionState.Locked
}

fun Activity.applyRecentsPrivacy(protect: Boolean) {
    if (protect) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        setRecentsScreenshotEnabled(!protect)
    }
}
