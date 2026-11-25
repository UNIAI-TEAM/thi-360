package vn.unicom.thi360.core.biometric

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.unicom.thi360.core.session.SessionState

class LockPolicyTest {

    @Test
    fun boundaryMinutesAndColdStartEnforceFiveMinuteRule() {
        val t0 = 1_000_000L

        assertFalse(
            "4m59s must stay unlocked",
            LockPolicy.shouldLock(
                biometricEnabled = true,
                backgroundedAtMillis = t0,
                nowMillis = t0 + 299_000L,
                isColdStart = false,
            ),
        )
        assertTrue(
            "Exactly 5m00s must lock",
            LockPolicy.shouldLock(
                biometricEnabled = true,
                backgroundedAtMillis = t0,
                nowMillis = t0 + 300_000L,
                isColdStart = false,
            ),
        )
        assertTrue(
            "5m01s must lock",
            LockPolicy.shouldLock(
                biometricEnabled = true,
                backgroundedAtMillis = t0,
                nowMillis = t0 + 301_000L,
                isColdStart = false,
            ),
        )
        assertTrue(
            "Cold start must lock when biometric is enabled",
            LockPolicy.shouldLock(
                biometricEnabled = true,
                backgroundedAtMillis = null,
                nowMillis = t0,
                isColdStart = true,
            ),
        )
        assertFalse(
            "Disabled biometric must never lock even on cold start or after timeout",
            LockPolicy.shouldLock(
                biometricEnabled = false,
                backgroundedAtMillis = t0,
                nowMillis = t0 + 600_000L,
                isColdStart = true,
            ),
        )
    }

    @Test
    fun recentsPrivacyProtectsWindowWhenBiometricEnabledOrSessionLocked() {
        assertTrue(
            RecentsPrivacyPolicy.shouldProtectWindow(
                biometricState = BiometricState.On,
                sessionState = SessionState.Active(username = "student.demo"),
            ),
        )
        assertTrue(
            RecentsPrivacyPolicy.shouldProtectWindow(
                biometricState = BiometricState.Off,
                sessionState = SessionState.Locked(username = "student.demo"),
            ),
        )
        assertFalse(
            RecentsPrivacyPolicy.shouldProtectWindow(
                biometricState = BiometricState.Off,
                sessionState = SessionState.Active(username = "student.demo"),
            ),
        )
        assertFalse(
            RecentsPrivacyPolicy.shouldProtectWindow(
                biometricState = BiometricState.Unavailable(BiometricUnavailableReason.NO_HARDWARE),
                sessionState = SessionState.SignedOut(),
            ),
        )
    }
}
