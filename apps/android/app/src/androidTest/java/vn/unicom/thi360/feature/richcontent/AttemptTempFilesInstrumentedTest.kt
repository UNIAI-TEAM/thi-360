package vn.unicom.thi360.feature.richcontent

import android.os.Environment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import vn.unicom.thi360.core.session.SessionCleanup
import vn.unicom.thi360.feature.richcontent.assets.AttemptAssetSource
import vn.unicom.thi360.feature.richcontent.model.AttemptAssetRef

@RunWith(AndroidJUnit4::class)
class AttemptTempFilesInstrumentedTest {

    private fun listAllRegularFiles(root: File?): Set<String> {
        if (root == null || !root.exists()) return emptySet()
        return root.walkTopDown()
            .filter { it.isFile }
            .map { it.canonicalPath }
            .toSet()
    }

    private fun snapshotAppStorage(): Map<String, Set<String>> {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        return mapOf(
            "filesDir" to listAllRegularFiles(ctx.filesDir),
            "cacheDir" to listAllRegularFiles(ctx.cacheDir),
            "noBackupFilesDir" to listAllRegularFiles(ctx.noBackupFilesDir),
            "externalCacheDir" to listAllRegularFiles(ctx.externalCacheDir),
        )
    }

    @Test
    fun attemptAssetsStayInMemoryAndViewerTempFilesCleanOnCloseLeaveLogoutAndAppStart() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        AttemptAssetSource.purgeTempDirectory(ctx.cacheDir)

        val assetPayload = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3, 4, 5, 6, 7, 8)
        val receivedAuthHeaders = CopyOnWriteArrayList<String>()
        val server = ServerSocket(0)
        val running = AtomicBoolean(true)
        val serverThread = thread(isDaemon = true, name = "attempt-temp-files-server") {
            while (running.get()) {
                try {
                    val socket = server.accept()
                    socket.use { s ->
                        val reader = s.getInputStream().bufferedReader()
                        var line = reader.readLine()
                        while (!line.isNullOrEmpty()) {
                            if (line.startsWith("Authorization:", ignoreCase = true)) {
                                receivedAuthHeaders += line.substringAfter(':').trim()
                            }
                            line = reader.readLine()
                        }
                        val out = s.getOutputStream()
                        val hdr = "HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: ${assetPayload.size}\r\nConnection: close\r\n\r\n"
                        out.write(hdr.toByteArray(Charsets.US_ASCII))
                        out.write(assetPayload)
                        out.flush()
                    }
                } catch (_: Throwable) {
                    if (!running.get()) break
                }
            }
        }

        val client = OkHttpClient.Builder()
            .cache(null)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("Authorization", "Bearer device-test-token")
                    .build()
                chain.proceed(req)
            }
            .build()

        val source = AttemptAssetSource(
            attemptId = "att-device-22",
            baseUrl = "http://127.0.0.1:${server.localPort}/",
            client = client,
            cacheDir = ctx.cacheDir,
        )

        try {
            val baseline = snapshotAppStorage()
            val ref = AttemptAssetRef.Attempt("9b8f8b1f-9b8f-4b8f-9b8f-9b8f8b8f8b8f")

            val loaded = source.load(ref)
            assertArrayEquals(assetPayload, loaded)
            assertTrue("Expected Bearer Authorization header", receivedAuthHeaders.any { it == "Bearer device-test-token" })
            val afterLoad = snapshotAppStorage()
            assertEquals("filesDir must not gain files after load", baseline["filesDir"], afterLoad["filesDir"])
            assertEquals("cacheDir must not gain files after load", baseline["cacheDir"], afterLoad["cacheDir"])
            assertEquals("noBackupFilesDir must not gain files after load", baseline["noBackupFilesDir"], afterLoad["noBackupFilesDir"])
            assertEquals("externalCacheDir must not gain files after load", baseline["externalCacheDir"], afterLoad["externalCacheDir"])

            val expectedDir = AttemptAssetSource.tempAssetDirectory(ctx.cacheDir).canonicalPath
            val handle1 = source.openForViewer(ref, "png")
            val file1 = handle1.file
            assertTrue("Viewer temp file must exist", file1.isFile)
            assertEquals("Viewer temp file must reside inside cacheDir/attempt-assets", expectedDir, file1.parentFile?.canonicalPath)
            assertFalse("Viewer temp filename must not leak assetId", file1.name.contains("9b8f8b1f"))
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).canonicalPath
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).canonicalPath
            assertFalse("Viewer temp file must not be in Downloads", file1.canonicalPath.startsWith(downloadsDir))
            assertFalse("Viewer temp file must not be in Pictures", file1.canonicalPath.startsWith(picturesDir))

            handle1.close()
            assertFalse("close() must delete viewer temp file", file1.exists())
            assertEquals(baseline["cacheDir"], snapshotAppStorage()["cacheDir"])

            val handle2 = source.openForViewer(ref, "pdf")
            assertTrue(handle2.file.isFile)
            source.purgeAll()
            assertFalse("purgeAll() must delete viewer temp files", handle2.file.exists())
            assertEquals(baseline["cacheDir"], snapshotAppStorage()["cacheDir"])

            val handle3 = source.openForViewer(ref, "mp3")
            assertTrue(handle3.file.isFile)
            val cleanup = SessionCleanup.create(ctx)
            cleanup.onLogout("acc-device-test")
            assertFalse("SessionCleanup.onLogout must delete attempt-assets temp files", handle3.file.exists())
            assertEquals(baseline["cacheDir"], snapshotAppStorage()["cacheDir"])

            val orphan = source.openForViewer(ref, "png").file
            assertTrue("Orphaned temp file must exist before simulated process restart", orphan.isFile)
            SessionCleanup.create(ctx).onAppStart()
            assertFalse("SessionCleanup.onAppStart must delete orphaned attempt-assets temp files", orphan.exists())
            val finalSnap = snapshotAppStorage()
            assertEquals(baseline["filesDir"], finalSnap["filesDir"])
            assertEquals(baseline["cacheDir"], finalSnap["cacheDir"])
            assertEquals(baseline["noBackupFilesDir"], finalSnap["noBackupFilesDir"])
            assertEquals(baseline["externalCacheDir"], finalSnap["externalCacheDir"])
        } finally {
            source.purgeAll()
            running.set(false)
            runCatching { server.close() }
            serverThread.join(1_000)
        }
    }
}

