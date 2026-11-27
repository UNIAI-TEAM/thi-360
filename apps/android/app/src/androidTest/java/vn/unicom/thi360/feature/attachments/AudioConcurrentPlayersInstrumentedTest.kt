package vn.unicom.thi360.feature.attachments

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.attachments.controller.AttemptAudioController
import vn.unicom.thi360.feature.attachments.model.AudioPlayerPhase
import vn.unicom.thi360.feature.attachments.playback.AndroidMediaPlayerEngine
import vn.unicom.thi360.feature.attachments.playback.AudioFocusOwner
import vn.unicom.thi360.feature.attachments.playback.AudioMediaSessionBridge
import vn.unicom.thi360.feature.attachments.ui.AttachmentTestTags
import vn.unicom.thi360.feature.attachments.ui.AttemptAudioPlayer

@RunWith(AndroidJUnit4::class)
class AudioConcurrentPlayersInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun tearDown() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AudioMediaSessionBridge.getInstance(context).releaseForTests()
    }

    @Test
    fun twoAudioPlayersInSamePartPlayConcurrentlyUnderSingleSharedAudioFocusOwner() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val wavBytes = AttachmentAndroidTestSupport.loadAssetBytes("attachments/silence-35s.wav")
        val assetA = UUID.fromString("00000000-0000-7000-8000-000000000730")
        val assetB = UUID.fromString("00000000-0000-7000-8000-000000000731")

        val server = EmbeddedAttachmentServer(maxPlays = 2, defaultDurationMs = 35_000L)
        server.registerAsset(assetA, "audio/wav", wavBytes, durationMs = 35_000L)
        server.registerAsset(assetB, "audio/wav", wavBytes, durationMs = 35_000L)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val harness = runBlocking { AttachmentAndroidTestSupport.createHarness(context, server, scope) }
        val engineA = AndroidMediaPlayerEngine(context = context, scope = scope)
        val engineB = AndroidMediaPlayerEngine(context = context, scope = scope)
        val controllerA = AttemptAudioController(
            attemptId = server.attemptId,
            assetId = assetA,
            partId = server.partId,
            fileName = "audio-a.wav",
            engine = harness.engine,
            gateway = harness.gateway,
            playbackEngine = engineA,
            scope = scope,
        )
        val controllerB = AttemptAudioController(
            attemptId = server.attemptId,
            assetId = assetB,
            partId = server.partId,
            fileName = "audio-b.wav",
            engine = harness.engine,
            gateway = harness.gateway,
            playbackEngine = engineB,
            scope = scope,
        )

        try {
            composeRule.setContent {
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AttemptAudioPlayer(controller = controllerA, attachLifecycle = false)
                        AttemptAudioPlayer(controller = controllerB, attachLifecycle = false)
                    }
                }
            }

            composeRule.waitForIdle()
            composeRule.onNodeWithTag(AttachmentTestTags.audioPrimaryButton(assetA)).performClick()
            composeRule.waitUntil(timeoutMillis = 5_000L) {
                controllerA.uiState.value.phase is AudioPlayerPhase.Playing && engineA.isPlaying()
            }
            assertEquals(1, controllerA.uiState.value.playsLeft)
            assertEquals(2, controllerB.uiState.value.playsLeft)

            // Start player B while player A is playing.
            composeRule.onNodeWithTag(AttachmentTestTags.audioPrimaryButton(assetB)).performClick()
            composeRule.waitUntil(timeoutMillis = 5_000L) {
                controllerB.uiState.value.phase is AudioPlayerPhase.Playing && engineB.isPlaying()
            }

            // Allow time for any erroneous audio focus loss callbacks to settle.
            Thread.sleep(500L)

            val openCallsForB = server.requests().filter {
                it.method == "POST" && it.path == "/api/v1/me/attempts/${server.attemptId}/audio/$assetB/plays"
            }
            assertEquals("Starting audio B must call POST .../audio/{B}/plays once", 1, openCallsForB.size)

            assertTrue("Audio A must still be playing concurrently with audio B", engineA.isPlaying())
            assertTrue("Audio B must be playing concurrently with audio A", engineB.isPlaying())
            assertTrue(controllerA.uiState.value.phase is AudioPlayerPhase.Playing)
            assertTrue(controllerB.uiState.value.phase is AudioPlayerPhase.Playing)
            assertEquals("Audio A playsLeft must remain 1/2", 1, controllerA.uiState.value.playsLeft)
            assertEquals("Audio B playsLeft must decrement to 1/2", 1, controllerB.uiState.value.playsLeft)
            assertEquals(
                "Shared AudioFocusOwner must hold both players concurrently",
                2,
                AudioFocusOwner.getInstance(context).activeClientCount(),
            )
        } finally {
            controllerA.close()
            controllerB.close()
            engineA.stopAndRelease()
            engineB.stopAndRelease()
            harness.close()
            server.close()
            scope.cancel()
        }
    }
}
