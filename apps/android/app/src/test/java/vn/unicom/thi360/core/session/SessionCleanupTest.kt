package vn.unicom.thi360.core.session

import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.unicom.thi360.core.storage.AccountScopedStore

class SessionCleanupTest {

    private lateinit var rootDir: File

    @Before
    fun setUp() {
        rootDir = Files.createTempDirectory("thi360-session-cleanup-").toFile()
    }

    @After
    fun tearDown() {
        rootDir.deleteRecursively()
    }

    @Test
    fun onLogoutCleansOnlyTargetAccountAndFailingCleanerDoesNotBlockOthers() = runTest {
        val storeA = AccountScopedStore(rootDir, "acc-A")
        val storeB = AccountScopedStore(rootDir, "acc-B")
        assertTrue(storeA.writeString("drafts", "k1", "valA"))
        assertTrue(storeB.writeString("drafts", "k1", "valB"))

        val cleanedBySecond = mutableListOf<String>()
        val appStartRuns = AtomicInteger(0)

        val cleanup = SessionCleanup(rootDir)
        cleanup.register { _ ->
            throw IllegalStateException("Simulated cleaner failure")
        }
        cleanup.register { accountId ->
            cleanedBySecond += accountId
        }
        cleanup.registerAppStartCleaner {
            appStartRuns.incrementAndGet()
        }

        cleanup.onLogout("acc-A")

        assertEquals(listOf("acc-A"), cleanedBySecond)
        assertFalse("Account A directory must be wiped even when first cleaner threw", AccountScopedStore.accountDirectory(rootDir, "acc-A").exists())
        assertEquals("valB", storeB.readString("drafts", "k1"))
        assertTrue("Pending marker must be cleared after successful logout cleanup", cleanup.pendingMarkers().isEmpty())

        cleanup.onAppStart()
        assertEquals(1, appStartRuns.get())
    }

    @Test
    fun interruptedLogoutCompletesOnStart() = runTest {
        val storeA = AccountScopedStore(rootDir, "acc-crashed-A")
        val storeB = AccountScopedStore(rootDir, "acc-healthy-B")
        assertTrue(storeA.writeString("drafts", "att-9", "must-be-wiped-on-next-start"))
        assertTrue(storeB.writeString("drafts", "att-9", "must-stay-intact"))

        // Process 1: logout writes pending marker, then crashes (CancellationException simulates process kill mid-cleanup).
        val cleanupBeforeCrash = SessionCleanup(rootDir)
        cleanupBeforeCrash.register { _ ->
            throw CancellationException("Simulated process death mid-cleanup")
        }

        var crashed = false
        try {
            cleanupBeforeCrash.onLogout("acc-crashed-A")
        } catch (_: CancellationException) {
            crashed = true
        }
        assertTrue(crashed)
        assertTrue("Pending cleanup marker must be persisted before cleaners run", cleanupBeforeCrash.pendingMarkers().isNotEmpty())
        assertTrue("Data of acc-crashed-A still exists right after simulated mid-cleanup crash", AccountScopedStore.accountDirectory(rootDir, "acc-crashed-A").exists())

        // Process 2: fresh SessionCleanup runs onAppStart() BEFORE any screen reads the store -> finishes wipe!
        val restartedCleanerLog = mutableListOf<String>()
        val cleanupAfterRestart = SessionCleanup(rootDir)
        cleanupAfterRestart.register { accountId ->
            restartedCleanerLog += accountId
        }

        cleanupAfterRestart.onAppStart()

        assertEquals(listOf("acc-crashed-A"), restartedCleanerLog)
        assertFalse(
            "Interrupted logout cleanup must finish wiping acc-crashed-A on next app start",
            AccountScopedStore.accountDirectory(rootDir, "acc-crashed-A").exists(),
        )
        assertTrue("Pending markers must be cleared after startup recovery", cleanupAfterRestart.pendingMarkers().isEmpty())
        assertEquals("must-stay-intact", storeB.readString("drafts", "att-9"))
    }
}
