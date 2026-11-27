package vn.unicom.thi360.feature.attachments

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import vn.unicom.thi360.core.network.generated.model.AnswerValue
import vn.unicom.thi360.core.network.generated.model.AttemptAttachmentView
import vn.unicom.thi360.core.network.generated.model.SingleChoiceAnswer
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.attachments.ui.AttachmentTestTags
import vn.unicom.thi360.feature.attachments.ui.AttachmentsView
import vn.unicom.thi360.feature.attachments.ui.rememberAttachmentOpener
import vn.unicom.thi360.feature.attachments.viewer.ViewerIntentLauncher
import vn.unicom.thi360.feature.question.model.ChoiceOptionItem
import vn.unicom.thi360.feature.question.model.QuestionItem
import vn.unicom.thi360.feature.question.model.QuestionMode
import vn.unicom.thi360.feature.question.ui.QuestionView
import vn.unicom.thi360.feature.richcontent.assets.AttemptAssetSource

@RunWith(AndroidJUnit4::class)
class AttachmentOpenInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tapPdfCallsAuthenticatedAssetEndpointOpensReadOnlyFileProviderUriAndDeletesTempOnClose() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AttemptAssetSource.purgeTempDirectory(context.cacheDir)

        val pdfBytes = AttachmentAndroidTestSupport.loadAssetBytes("attachments/bang-so-lieu.pdf")
        val pdfAssetId = UUID.fromString("00000000-0000-7000-8000-000000000513")
        val server = EmbeddedAttachmentServer()
        server.registerAsset(pdfAssetId, "application/pdf", pdfBytes)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val harness = runBlocking { AttachmentAndroidTestSupport.createHarness(context, server, scope) }
        try {
            val baselineStorage = AttachmentAndroidTestSupport.snapshotAppStorage(context)
            val capturedIntent = AtomicReference<Intent?>()
            val capturedClose = AtomicReference<(() -> Unit)?>()

            val question = QuestionItem.SingleChoice(
                id = "q-513",
                displayNo = 3,
                stem = "Xem bảng số liệu đính kèm và chọn đáp án đúng.",
                optionOrder = listOf("a", "b"),
                points = null,
                options = listOf(
                    ChoiceOptionItem("a", "Phương án A"),
                    ChoiceOptionItem("b", "Phương án B"),
                ),
            )
            val attachments = listOf(
                AttemptAttachmentView(
                    assetId = pdfAssetId,
                    fileName = "bang-so-lieu.pdf",
                    mime = "application/pdf",
                    url = "/api/v1/me/attempts/${server.attemptId}/assets/$pdfAssetId",
                ),
            )
            var currentAnswer by mutableStateOf<AnswerValue?>(SingleChoiceAnswer("a"))

            composeRule.setContent {
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT)) {
                    val opener = rememberAttachmentOpener(
                        assetSource = harness.assetSource,
                        customLauncher = ViewerIntentLauncher { intent, onClosed ->
                            capturedIntent.set(intent)
                            capturedClose.set(onClosed)
                        },
                    )
                    QuestionView(
                        item = question,
                        answer = currentAnswer,
                        mode = QuestionMode.Answering,
                        onAnswer = { currentAnswer = it },
                        modifier = Modifier.padding(16.dp),
                        attachments = {
                            AttachmentsView(
                                parentAttachments = attachments,
                                audioPlayer = {},
                                opener = opener,
                            )
                        },
                    )
                }
            }

            composeRule.waitForIdle()
            composeRule.onNodeWithText("bang-so-lieu.pdf").assertIsDisplayed()
            composeRule.onNodeWithTag(AttachmentTestTags.fileRow(pdfAssetId)).performClick()

            composeRule.waitUntil(timeoutMillis = 5_000L) { capturedIntent.get() != null }

            val intent = requireNotNull(capturedIntent.get())
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals("application/pdf", intent.type)
            assertTrue(
                "Viewer intent must grant read URI permission",
                (intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0,
            )
            assertEquals(
                "Viewer intent must not grant write URI permission",
                0,
                intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            val uri = requireNotNull(intent.data)
            assertEquals("content", uri.scheme)
            assertEquals("${context.packageName}.fileprovider", uri.authority)
            assertTrue(
                "FileProvider URI must point inside attempt_assets path, got $uri",
                uri.path.orEmpty().contains("attempt_assets"),
            )

            // Verify FileProvider serves the exact PDF bytes read-only via real ContentResolver.
            val readBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            assertNotNull("ContentResolver must open FileProvider URI for reading", readBytes)
            assertArrayEquals(pdfBytes, readBytes)

            val assetRequests = server.requests().filter {
                it.method == "GET" && it.path == "/api/v1/me/attempts/${server.attemptId}/assets/$pdfAssetId"
            }
            assertEquals("Must call authenticated GET .../assets/{assetId} once", 1, assetRequests.size)
            assertEquals("Bearer android-instrumented-token", assetRequests.single().header("Authorization"))

            // Temp file exists inside cacheDir/attempt-assets while viewer is open.
            val tempDir = AttemptAssetSource.tempAssetDirectory(context.cacheDir)
            assertTrue("Expected temp PDF file while viewer is open", tempDir.listFiles()?.isNotEmpty() == true)

            // Simulate OS viewer returning to the question screen.
            composeRule.runOnUiThread {
                capturedClose.get()?.invoke()
            }
            composeRule.waitForIdle()

            // Returned to exact question with answer intact.
            composeRule.onNodeWithText("bang-so-lieu.pdf").assertIsDisplayed()
            assertEquals(SingleChoiceAnswer("a"), currentAnswer)

            // No file containing PDF bytes remains anywhere in app data/cache directories.
            val afterClose = AttachmentAndroidTestSupport.snapshotAppStorage(context)
            assertEquals(baselineStorage["filesDir"], afterClose["filesDir"])
            assertEquals(baselineStorage["cacheDir"], afterClose["cacheDir"])
            assertEquals(baselineStorage["noBackupFilesDir"], afterClose["noBackupFilesDir"])
            assertEquals(baselineStorage["externalCacheDir"], afterClose["externalCacheDir"])
            for (path in afterClose.values.flatten()) {
                val file = File(path)
                if (file.length() == pdfBytes.size.toLong()) {
                    assertFalse(
                        "Found residual file with PDF bytes after closing viewer: $path",
                        file.readBytes().contentEquals(pdfBytes),
                    )
                }
            }
        } finally {
            harness.close()
            server.close()
            scope.cancel()
            AttemptAssetSource.purgeTempDirectory(context.cacheDir)
        }
    }
}
