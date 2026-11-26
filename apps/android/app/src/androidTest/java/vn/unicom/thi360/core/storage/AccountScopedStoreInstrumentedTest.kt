package vn.unicom.thi360.core.storage

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountScopedStoreInstrumentedTest {

    private lateinit var context: Context
    private lateinit var rootDir: File

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        rootDir = File(AccountScopedStore.defaultRootDir(context), "instrumented_test")
        rootDir.deleteRecursively()
    }

    @After
    fun tearDown() {
        rootDir.deleteRecursively()
    }

    @Test
    fun storeResidesInsideNoBackupFilesDirWritesAtomicallyAndIsolatesAccountsOnDevice() {
        val defaultRoot = AccountScopedStore.defaultRootDir(context)
        assertEquals(
            "Default AccountScopedStore root must reside under noBackupFilesDir",
            context.noBackupFilesDir.canonicalPath,
            defaultRoot.parentFile?.canonicalPath,
        )

        val storeA = AccountScopedStore(rootDir, "device-account-A")
        val storeB = AccountScopedStore(rootDir, "device-account-B")

        assertTrue(storeA.writeString("drafts", "attempt-1", """{"q1":"B"}"""))
        assertEquals("""{"q1":"B"}""", storeA.readString("drafts", "attempt-1"))
        assertNull("Account B must not read Account A data on device", storeB.readString("drafts", "attempt-1"))

        assertTrue(storeA.writeString("drafts", "attempt-1", """{"q1":"C"}"""))
        assertEquals("""{"q1":"C"}""", storeA.readString("drafts", "attempt-1"))

        val entryFile = storeA.entryFileForTest("drafts", "attempt-1")
        assertTrue(entryFile.isFile)
        val leftoverTemps = entryFile.parentFile?.listFiles { f -> f.name.endsWith(".tmp") }.orEmpty()
        assertTrue("Atomic rename must leave zero .tmp files", leftoverTemps.isEmpty())

        assertTrue(storeB.writeString("drafts", "attempt-1", """{"q1":"D"}"""))
        AccountScopedStore.wipeAccount(rootDir, "device-account-A")

        assertFalse(AccountScopedStore.accountDirectory(rootDir, "device-account-A").exists())
        assertEquals("""{"q1":"D"}""", storeB.readString("drafts", "attempt-1"))
    }
}
