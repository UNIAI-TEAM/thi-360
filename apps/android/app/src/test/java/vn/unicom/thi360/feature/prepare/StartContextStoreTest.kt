package vn.unicom.thi360.feature.prepare

import java.io.File
import java.nio.file.Files
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.unicom.thi360.core.network.generated.model.PreChoiceInput
import vn.unicom.thi360.core.storage.AccountScopedStoreProvider
import vn.unicom.thi360.feature.attemptengine.AttemptStorageKeys

class StartContextStoreTest {

    private lateinit var tempRoot: File

    @Before
    fun setUp() {
        tempRoot = Files.createTempDirectory("thi360-start-context-test-").toFile()
    }

    @After
    fun tearDown() {
        tempRoot.deleteRecursively()
    }

    @Test
    fun persistsAcrossProcessRestartIsolatesAccountsAndMovesOnRetry() {
        val attemptId1 = UUID.fromString("00000000-0000-7000-8000-000000000101")
        val attemptId2 = UUID.fromString("00000000-0000-7000-8000-000000000102")
        val entitlementId = "00000000-0000-7000-8000-000000000001"
        val expectedContext = StartContext(
            entitlementId = entitlementId,
            preChoice = PreChoiceInput(optionCode = "SCIENCE", subjectCodes = listOf("LY", "HOA", "SINH")),
            examTitle = "HSA đầy đủ (thu nhỏ)",
            examKindLabel = "Đánh giá năng lực ĐHQG Hà Nội (HSA)",
            firstPartName = "Toán học và xử lí số liệu",
            firstPartMinutes = 75,
            timing = "PER_PART",
        )

        var activeAccount: String? = "account-A"
        val provider1 = AccountScopedStoreProvider(tempRoot) { activeAccount }
        val store1 = StartContextStore(provider1)

        assertEquals(AttemptStorageKeys.NAMESPACE, StartContextStore.NAMESPACE)
        assertEquals(
            AttemptStorageKeys.startContextKey(attemptId1),
            StartContextStore.keyFor(attemptId1),
        )

        assertTrue(store1.save(attemptId1, expectedContext))
        assertNotNull(
            provider1.current()?.readString(
                AttemptStorageKeys.NAMESPACE,
                AttemptStorageKeys.startContextKey(attemptId1),
            ),
        )

        val providerAfterRestart = AccountScopedStoreProvider(tempRoot) { activeAccount }
        val storeAfterRestart = StartContextStore(providerAfterRestart)
        assertEquals(expectedContext, storeAfterRestart.read(attemptId1))

        activeAccount = "account-B"
        assertNull(storeAfterRestart.read(attemptId1))

        activeAccount = "account-A"
        assertEquals(expectedContext, storeAfterRestart.read(attemptId1))

        val moved = storeAfterRestart.moveTo(
            oldAttemptId = attemptId1,
            newAttemptId = attemptId2,
            updatedContext = expectedContext,
        )
        assertTrue(moved)
        assertNull(storeAfterRestart.read(attemptId1))
        assertEquals(expectedContext, storeAfterRestart.read(attemptId2))

        storeAfterRestart.delete(attemptId2)
        assertNull(storeAfterRestart.read(attemptId2))
    }

    @Test
    fun keepsContextWhenAttemptIsAbortedAndSupportsSharedDuration() {
        val abortedId = UUID.fromString("00000000-0000-7000-8000-000000000201")
        val provider = AccountScopedStoreProvider(tempRoot) { "account-A" }
        val store = StartContextStore(provider)
        val sharedContext = StartContext(
            entitlementId = "00000000-0000-7000-8000-000000000009",
            preChoice = null,
            examTitle = "Đề thi THPTQG Toán",
            examKindLabel = "Tốt nghiệp THPT (THPTQG)",
            firstPartName = "Phần I · Trắc nghiệm",
            firstPartMinutes = 90,
            timing = "SHARED",
        )

        assertTrue(store.save(abortedId, sharedContext))
        assertEquals(sharedContext, store.read(abortedId))
    }
}
