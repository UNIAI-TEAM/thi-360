package vn.unicom.thi360.feature.attachments

import android.os.Environment
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import vn.unicom.thi360.core.session.SessionCleanup
import vn.unicom.thi360.feature.attachments.controller.AttemptAudioController
import vn.unicom.thi360.feature.attachments.model.AudioPlayerPhase
import vn.unicom.thi360.feature.attachments.model.QuestionAttachment
import vn.unicom.thi360.feature.attachments.playback.AndroidMediaPlayerEngine
import vn.unicom.thi360.feature.attachments.playback.AudioMediaSessionBridge
import vn.unicom.thi360.feature.attachments.viewer.AttachmentOpener
import vn.unicom.thi360.feature.attachments.viewer.ViewerIntentLauncher
import vn.unicom.thi360.feature.richcontent.assets.AttemptAssetSource
import vn.unicom.thi360.feature.richcontent.model.AttemptAssetRef

@RunWith(AndroidJUnit4::class)
class AttachmentStorageInstrumentedTest {

    @After
    fun tearDown() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AudioMediaSessionBridge.getInstance(context).releaseForTests()
        AttemptAssetSource.purgeTempDirectory(context.cacheDir)
    }

    @Test
    fun audioNeverWritesToDiskAndTempFilesCleanUpOnStartupMediaStoreCheckAndLogout() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AttemptAssetSource.purgeTempDirectory(context.cacheDir)

        val shortWav = AttachmentAndroidTestSupport.loadAssetBytes("attachments/silence-2s.wav")
        val longWav = AttachmentAndroidTestSupport.loadAssetBytes("attachments/silence-35s.wav")
        val pdfBytes = AttachmentAndroidTestSupport.loadAssetBytes("attachments/bang-so-lieu.pdf")
        val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4)

        val shortAudioId = UUID.fromString("00000000-0000-7000-8000-000000000732")
        val longAudioId = UUID.fromString("00000000-0000-7000-8000-000000000735")
        val pdfId = UUID.fromString("00000000-0000-7000-8000-000000000733")
        val pngId = UUID.fromString("00000000-0000-7000-8000-000000000734")

        val server = EmbeddedAttachmentServer(maxPlays = 2, defaultDurationMs = 2_000L)
        server.registerAsset(shortAudioId, "audio/wav", shortWav, durationMs = 2_000L)
        server.registerAsset(longAudioId, "audio/wav", longWav, durationMs = 35_000L)
        server.registerAsset(pdfId, "application/pdf", pdfBytes)
        server.registerAsset(pngId, "image/png", pngBytes)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val harness = AttachmentAndroidTestSupport.createHarness(context, server, scope)
        val sessionCleanup = SessionCleanup.create(context)

        try {
            val baselineMediaStore = AttachmentAndroidTestSupport.queryMediaStoreIds(context)
            val baselineStorage = AttachmentAndroidTestSupport.snapshotAppStorage(context)

            // Step 1 (ĐT 732): Play 2s audio to completion, leave screen, close controller -> zero audio files on disk.
            val engine1 = AndroidMediaPlayerEngine(context = context, scope = scope)
            val controller1 = AttemptAudioController(
                attemptId = server.attemptId,
                assetId = shortAudioId,
                partId = server.partId,
                fileName = "short.wav",
                engine = harness.engine,
                gateway = harness.gateway,
                playbackEngine = engine1,
                scope = scope,
                sessionCleanup = sessionCleanup,
            )
            controller1.onPrimaryAction()
            waitFor(timeoutMs = 5_000L) { controller1.uiState.value.phase is AudioPlayerPhase.Playing }
            // Wait for 2s audio to finish naturally and invoke /end.
            waitFor(timeoutMs = 7_000L) {
                !engine1.isPlaying() && controller1.uiState.value.phase is AudioPlayerPhase.Idle
            }
            controller1.onLeaveScreen()
            controller1.close()
            engine1.stopAndRelease()

            val afterAudioStorage = AttachmentAndroidTestSupport.snapshotAppStorage(context)
            assertEquals("filesDir must have zero new files after audio playback", baselineStorage["filesDir"], afterAudioStorage["filesDir"])
            assertEquals("cacheDir must have zero new files after audio playback", baselineStorage["cacheDir"], afterAudioStorage["cacheDir"])
            assertEquals("noBackupFilesDir must have zero new files after audio playback", baselineStorage["noBackupFilesDir"], afterAudioStorage["noBackupFilesDir"])
            assertEquals("externalCacheDir must have zero new files after audio playback", baselineStorage["externalCacheDir"], afterAudioStorage["externalCacheDir"])

            // Inspect app data dir via shell ls to verify zero audio artifacts exist anywhere under dataDir.
            val lsOutput = runShell("ls -R ${context.dataDir.absolutePath}")
            assertFalse("No .wav file may exist under app dataDir:\n$lsOutput", lsOutput.contains(".wav", ignoreCase = true))
            assertFalse("No .mp3 file may exist under app dataDir:\n$lsOutput", lsOutput.contains(".mp3", ignoreCase = true))

            // Step 2 (ĐT 733): Simulate OS killing app while viewer temp file is open -> next onAppStart deletes it.
            val orphanedTemp = harness.assetSource.openForViewer(
                ref = AttemptAssetRef.Attempt(pdfId.toString()),
                extension = "pdf",
            ).file
            assertTrue("Orphaned temp file must exist prior to simulated restart", orphanedTemp.isFile)
            SessionCleanup.create(context).onAppStart()
            assertFalse("onAppStart must delete orphaned viewer temp files before first screen", orphanedTemp.exists())

            // Step 3 (ĐT 734): Load image, open PDF viewer and close it, and check MediaStore & public storage.
            val loadedImage = harness.assetSource.load(AttemptAssetRef.Attempt(pngId.toString()))
            assertEquals(pngBytes.size, loadedImage.size)
            val viewerHandle = harness.assetSource.openForViewer(AttemptAssetRef.Attempt(pdfId.toString()), "pdf")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).canonicalPath
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).canonicalPath
            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).canonicalPath
            assertFalse(viewerHandle.file.canonicalPath.startsWith(downloadsDir))
            assertFalse(viewerHandle.file.canonicalPath.startsWith(picturesDir))
            assertFalse(viewerHandle.file.canonicalPath.startsWith(musicDir))
            viewerHandle.close()

            val afterMediaStore = AttachmentAndroidTestSupport.queryMediaStoreIds(context)
            assertEquals("MediaStore.Downloads must have zero entries written by app", baselineMediaStore["downloads"], afterMediaStore["downloads"])
            assertEquals("MediaStore.Images must have zero entries written by app", baselineMediaStore["images"], afterMediaStore["images"])
            assertEquals("MediaStore.Audio must have zero entries written by app", baselineMediaStore["audio"], afterMediaStore["audio"])

            // Step 4 (ĐT 735): Open PDF viewer + active playing audio, then logout -> audio stops and all attempt assets deleted.
            val engine2 = AndroidMediaPlayerEngine(context = context, scope = scope)
            val controller2 = AttemptAudioController(
                attemptId = server.attemptId,
                assetId = longAudioId,
                partId = server.partId,
                fileName = "logout-audio.wav",
                engine = harness.engine,
                gateway = harness.gateway,
                playbackEngine = engine2,
                scope = scope,
                sessionCleanup = sessionCleanup,
            )
            val opener = AttachmentOpener(
                assetSource = harness.assetSource,
                uriResolver = AttachmentOpener.defaultUriResolver(context),
                launcher = ViewerIntentLauncher { _, _ -> },
                sessionCleanup = sessionCleanup,
            )
            val opened = opener.open(
                QuestionAttachment(
                    assetId = pdfId,
                    fileName = "bang-so-lieu.pdf",
                    mime = "application/pdf",
                    url = "/api/v1/me/attempts/${server.attemptId}/assets/$pdfId",
                ),
            )
            assertTrue("Expected attachment viewer to open before logout", opened)
            val tempFilesBeforeLogout = AttemptAssetSource.tempAssetDirectory(context.cacheDir).listFiles().orEmpty().toList()
            assertTrue("Temp file must exist before logout", tempFilesBeforeLogout.isNotEmpty())

            controller2.onPrimaryAction()
            waitFor(timeoutMs = 5_000L) {
                controller2.uiState.value.phase is AudioPlayerPhase.Playing && engine2.isPlaying()
            }

            sessionCleanup.onLogout("acc-android-audio")

            assertFalse("Audio playback must stop immediately on logout", engine2.isPlaying())
            for (file in tempFilesBeforeLogout) {
                assertFalse("Viewer temp file must be deleted on logout: ${file.name}", file.exists())
            }
            val tempFilesAfterLogout = AttemptAssetSource.tempAssetDirectory(context.cacheDir).listFiles().orEmpty()
            assertEquals("attempt-assets temp directory must be empty after logout", 0, tempFilesAfterLogout.size)

            val afterLogoutStorage = AttachmentAndroidTestSupport.snapshotAppStorage(context)
            assertEquals(baselineStorage["filesDir"], afterLogoutStorage["filesDir"])
            assertEquals(baselineStorage["cacheDir"], afterLogoutStorage["cacheDir"])
            assertEquals(baselineStorage["noBackupFilesDir"], afterLogoutStorage["noBackupFilesDir"])
            assertEquals(baselineStorage["externalCacheDir"], afterLogoutStorage["externalCacheDir"])

            opener.close()
            controller2.close()
            engine2.stopAndRelease()
        } finally {
            harness.close()
            server.close()
            scope.cancel()
            AttemptAssetSource.purgeTempDirectory(context.cacheDir)
        }
    }

    private suspend fun waitFor(timeoutMs: Long, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!predicate() && System.currentTimeMillis() < deadline) {
            delay(30L)
        }
        assertTrue("Condition timed out after ${timeoutMs}ms", predicate())
    }

    private fun runShell(command: String): String {
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        return ui.executeShellCommand(command).use { pfd ->
            ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
        }
    }
}
