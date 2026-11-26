package vn.unicom.thi360.core.storage

import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AccountScopedStoreTest {

    private lateinit var rootDir: File

    @Before
    fun setUp() {
        rootDir = Files.createTempDirectory("thi360-account-store-test-").toFile()
    }

    @After
    fun tearDown() {
        rootDir.deleteRecursively()
    }

    @Test
    fun accountsAreIsolated() {
        val storeA = AccountScopedStore(rootDir, "account-A")
        val storeB = AccountScopedStore(rootDir, "account-B")

        assertTrue(storeA.writeString("attempt_drafts", "att-100", """{"q1":"C"}"""))
        assertTrue(storeA.writeString("positions", "att-100", "question=7"))

        // Account B cannot read Account A's data even knowing the exact namespace & key.
        assertNull("Account B must not read Account A drafts", storeB.read("attempt_drafts", "att-100"))
        assertNull("Account B must not read Account A positions", storeB.read("positions", "att-100"))

        // Account B writing the exact same namespace/key does not overwrite Account A's entry.
        assertTrue(storeB.writeString("attempt_drafts", "att-100", """{"q1":"D"}"""))
        assertEquals("""{"q1":"C"}""", storeA.readString("attempt_drafts", "att-100"))
        assertEquals("""{"q1":"D"}""", storeB.readString("attempt_drafts", "att-100"))

        // Path traversal / special characters cannot escape account directory.
        assertTrue(storeB.writeString("../account-A", "../../att-100", "injected"))
        assertEquals("""{"q1":"C"}""", storeA.readString("attempt_drafts", "att-100"))

        // Deleting namespace in A preserves B's data.
        storeA.deleteNamespace("attempt_drafts")
        assertNull(storeA.read("attempt_drafts", "att-100"))
        assertEquals("question=7", storeA.readString("positions", "att-100"))
        assertEquals("""{"q1":"D"}""", storeB.readString("attempt_drafts", "att-100"))

        // Wiping A removes all of A while leaving B completely untouched.
        AccountScopedStore.wipeAccount(rootDir, "account-A")
        assertFalse(AccountScopedStore.accountDirectory(rootDir, "account-A").exists())
        assertEquals("""{"q1":"D"}""", storeB.readString("attempt_drafts", "att-100"))
    }

    @Test
    fun atomicWriteLeavesNoTempFilesAndInterruptedTempNeverCorruptsPreviousValue() {
        val store = AccountScopedStore(rootDir, "account-atomic")
        val v1 = """{"answers":[1,2,3]}"""
        val v2 = """{"answers":[1,2,3,4]}"""

        assertTrue(store.writeString("drafts", "att-1", v1))
        val targetFile = store.entryFileForTest("drafts", "att-1")
        assertTrue(targetFile.isFile)

        // Simulate a crashed partial temp file left next to the committed file.
        val orphanTemp = File(targetFile.parentFile, "${targetFile.name}.crashed.tmp")
        orphanTemp.writeText("""{"answers":[BROKEN""")
        assertEquals("Orphan temp file must never corrupt committed data", v1, store.readString("drafts", "att-1"))

        // Subsequent atomic write replaces with v2 cleanly and leaves no new temp files.
        assertTrue(store.writeString("drafts", "att-1", v2))
        assertEquals(v2, store.readString("drafts", "att-1"))
        val newTemps = targetFile.parentFile?.listFiles { f -> f.name.endsWith(".tmp") && f != orphanTemp }.orEmpty()
        assertTrue("No temp files may remain after atomic write", newTemps.isEmpty())
    }

    @Test
    fun concurrentWriteAndWipe() = runBlocking {
        val storeA = AccountScopedStore(rootDir, "account-concurrent-A")
        val started = CountDownLatch(2)
        val stop = AtomicBoolean(false)

        val writers = (1..2).map { writerId ->
            async(Dispatchers.IO) {
                started.countDown()
                var i = 0
                while (!stop.get() && i < 150) {
                    runCatching {
                        storeA.writeString("drafts", "shared-key", "writer-$writerId-rev-$i")
                    }
                    i++
                }
            }
        }

        assertTrue(started.await(3, TimeUnit.SECONDS))
        // Trigger logout wipe mid-flight while both coroutines are writing to the same key.
        AccountScopedStore.wipeAccount(rootDir, "account-concurrent-A")
        stop.set(true)
        writers.awaitAll()

        // After wipe completes, NO file of account A may remain on disk and stale handle writes are rejected!
        assertFalse(
            "No files of account A may remain after concurrent write + wipe",
            AccountScopedStore.accountDirectory(rootDir, "account-concurrent-A").exists(),
        )
        assertFalse(
            "Invalidated pre-logout store handle must refuse subsequent writes",
            storeA.writeString("drafts", "after-wipe", "must-not-persist"),
        )
        assertFalse(AccountScopedStore.accountDirectory(rootDir, "account-concurrent-A").exists())
    }

    @Test
    fun providerReturnsNullWhenAccountIdIsMissing() {
        var accountId: String? = null
        val provider = AccountScopedStoreProvider(rootDir) { accountId }

        assertNull("Provider must return null before accountId is known", provider.current())
        accountId = "   "
        assertNull("Provider must return null for blank accountId", provider.current())

        accountId = "acc-resolved-1"
        val store = provider.current()
        assertTrue(store != null)
        assertTrue(store!!.writeString("ns", "k", "v"))
        assertEquals("v", store.readString("ns", "k"))
    }
}
