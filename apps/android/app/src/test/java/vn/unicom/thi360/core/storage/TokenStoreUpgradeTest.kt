package vn.unicom.thi360.core.storage

import java.io.File
import java.nio.file.Files
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.AppLogger
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.session.SessionState
import vn.unicom.thi360.testing.ContractFakeServer

class TokenStoreUpgradeTest {

    private lateinit var tempDir: File
    private lateinit var server: ContractFakeServer

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("thi360-token-upgrade-").toFile()
        server = ContractFakeServer().start()
    }

    @After
    fun tearDown() {
        server.shutdown()
        tempDir.deleteRecursively()
    }

    @Test
    fun phase1TokenRecordWithoutAccountIdStaysSignedInAndPopulatesAccountIdAfterMe() = runTest {
        val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val cipher = object : TokenCipher {
            private val random = SecureRandom()
            override fun encrypt(plaintext: ByteArray): ByteArray {
                val iv = ByteArray(12).also { random.nextBytes(it) }
                val c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
                return iv + c.doFinal(plaintext)
            }

            override fun decrypt(ciphertext: ByteArray): ByteArray {
                val iv = ciphertext.copyOfRange(0, 12)
                val body = ciphertext.copyOfRange(12, ciphertext.size)
                val c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                return c.doFinal(body)
            }
        }

        // Write a real Phase-1 encrypted record that has NO "accountId" JSON field at all.
        val phase1Json = """
            {
              "accessToken": "phase1-access-token",
              "refreshToken": "phase1-refresh-token",
              "accessExpiresIn": 300,
              "refreshExpiresIn": 604800,
              "mustChangePassword": false,
              "username": "student.phase1"
            }
        """.trimIndent()
        val storageFile = File(tempDir, KeystoreTokenStore.TOKEN_FILE_NAME)
        storageFile.writeBytes(cipher.encrypt(phase1Json.toByteArray(Charsets.UTF_8)))

        val tokenStore = KeystoreTokenStore(storageFile = storageFile, cipher = cipher)
        val upgraded = tokenStore.read()
        assertNotNull("Phase-1 token record without accountId must deserialize cleanly", upgraded)
        assertEquals("student.phase1", upgraded!!.username)
        assertNull("Upgraded Phase-1 record initially has null accountId", upgraded.accountId)

        val accountStoreRoot = File(tempDir, "account_scoped")
        val storeProvider = AccountScopedStoreProvider(accountStoreRoot) { tokenStore.read()?.accountId }
        assertNull("AccountScopedStoreProvider must return null before GET /api/v1/me resolves accountId", storeProvider.current())

        server.respondAuthFixture("POST", "/api/v1/auth/mobile/refresh", 200, "refresh-200.json")
        server.respondApiFixture("GET", "/api/v1/me", 200, "me-200.json")

        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.VI },
            logger = object : AppLogger {
                override fun debug(tag: String, message: String) = Unit
                override fun warn(tag: String, message: String) = Unit
            },
        )

        // User stays signed in immediately on upgrade (no forced logout).
        assertTrue(stack.sessionManager.state.value is SessionState.Active)

        // Launch validation refreshes and fetches GET /api/v1/me -> persists accountId into TokenStore.
        stack.sessionManager.validateSessionOnLaunch()
        val resolvedId = tokenStore.read()?.accountId
        assertEquals("019320a0-0000-7000-8000-000000000001", resolvedId)
        assertEquals(resolvedId, stack.sessionManager.currentAccountId.value)

        // Once resolved, AccountScopedStoreProvider returns a working store scoped to that accountId.
        val store = storeProvider.current()
        assertNotNull(store)
        assertTrue(store!!.writeString("drafts", "k1", "saved"))
        assertEquals("saved", store.readString("drafts", "k1"))
    }
}
