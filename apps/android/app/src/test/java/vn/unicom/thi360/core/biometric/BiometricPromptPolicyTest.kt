package vn.unicom.thi360.core.biometric

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BiometricPromptPolicyTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun dataStorePromptMemoryPersistsLaterAcrossRestartsAndLogout() = runTest {
        val file = File(tempFolder.newFolder("bio_memory"), "prompt.preferences_pb")

        val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store1 = DataStoreBiometricPromptMemoryStore(
                PreferenceDataStoreFactory.create(scope = scope1) { file },
            )
            assertEquals(PromptMemory(null), store1.current())
            store1.record(PromptAnswer.LATER)
            assertEquals(PromptMemory(PromptAnswer.LATER), store1.current())
        } finally {
            scope1.cancel()
        }

        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store2 = DataStoreBiometricPromptMemoryStore(
                PreferenceDataStoreFactory.create(scope = scope2) { file },
            )
            assertEquals(PromptMemory(PromptAnswer.LATER), store2.current())
        } finally {
            scope2.cancel()
        }
    }

    @Test
    fun enrolledDevicePromptsAfterPasswordLoginUnlessLastAnswerWasLaterAndPersistsAcrossLogout() = runTest {
        val availability = FakeBiometricAvailabilityChecker(BiometricAvailability.Available)
        val vault = FakeBiometricVault()
        val memory = InMemoryBiometricPromptMemoryStore()
        val controller = BiometricController(availability, vault, memory)

        controller.onPasswordLoginCompleted(mustChangePassword = false)
        assertTrue("Fresh enrolled device must prompt after password login", controller.shouldPromptEnable.value)

        controller.declineEnablePrompt()
        assertFalse(controller.shouldPromptEnable.value)
        assertEquals(PromptAnswer.LATER, memory.current().lastAnswer)

        controller.clearForSessionEnd()
        assertEquals(PromptAnswer.LATER, memory.current().lastAnswer)

        controller.onPasswordLoginCompleted(mustChangePassword = false)
        assertFalse("Device with lastAnswer=LATER must not prompt again after re-login", controller.shouldPromptEnable.value)
    }

    @Test
    fun forcedPasswordChangeDefersPromptUntilChangeCompletes() = runTest {
        val controller = BiometricController(
            availabilityChecker = FakeBiometricAvailabilityChecker(BiometricAvailability.Available),
            vault = FakeBiometricVault(),
            promptMemoryStore = InMemoryBiometricPromptMemoryStore(),
        )

        controller.onPasswordLoginCompleted(mustChangePassword = true)
        assertFalse("Must not prompt before forced password change completes", controller.shouldPromptEnable.value)

        controller.onForcedPasswordChangeCompleted()
        assertTrue("Must prompt right after forced password change completes", controller.shouldPromptEnable.value)
    }

    @Test
    fun unavailableOrUnenrolledDeviceNeverPromptsAndReportsUnavailableState() = runTest {
        for (reason in listOf(BiometricUnavailableReason.NO_HARDWARE, BiometricUnavailableReason.NOT_ENROLLED)) {
            val controller = BiometricController(
                availabilityChecker = FakeBiometricAvailabilityChecker(BiometricAvailability.Unavailable(reason)),
                vault = FakeBiometricVault(),
                promptMemoryStore = InMemoryBiometricPromptMemoryStore(),
            )
            controller.onPasswordLoginCompleted(mustChangePassword = false)
            assertFalse(controller.shouldPromptEnable.value)
            assertEquals(BiometricState.Unavailable(reason), controller.state.value)
        }
    }
}
