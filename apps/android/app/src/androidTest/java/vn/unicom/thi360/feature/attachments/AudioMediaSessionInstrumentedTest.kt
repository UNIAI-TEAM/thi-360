package vn.unicom.thi360.feature.attachments

import android.app.NotificationManager
import android.content.Context
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
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
import org.junit.Assert.assertNotNull
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
import vn.unicom.thi360.feature.attachments.playback.AudioMediaSessionBridge
import vn.unicom.thi360.feature.attachments.ui.AttachmentTestTags
import vn.unicom.thi360.feature.attachments.ui.AttemptAudioPlayer

@RunWith(AndroidJUnit4::class)
class AudioMediaSessionInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun tearDown() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AudioMediaSessionBridge.getInstance(context).releaseForTests()
    }

    @Test
    fun mediaSessionExposesOnlyPauseWithNoNotificationRejectsSeekAndHeadsetPauseResumesFromServerPosition() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val wavBytes = AttachmentAndroidTestSupport.loadAssetBytes("attachments/silence-35s.wav")
        val audioAssetId = UUID.fromString("00000000-0000-7000-8000-000000000521")

        val server = EmbeddedAttachmentServer(defaultDurationMs = 35_000L)
        server.registerAsset(audioAssetId, "audio/wav", wavBytes, durationMs = 35_000L)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val harness = runBlocking { AttachmentAndroidTestSupport.createHarness(context, server, scope) }
        val playbackEngine = AndroidMediaPlayerEngine(context = context, scope = scope)
        val controller = AttemptAudioController(
            attemptId = server.attemptId,
            assetId = audioAssetId,
            partId = server.partId,
            fileName = "listening-part-1.wav",
            engine = harness.engine,
            gateway = harness.gateway,
            playbackEngine = playbackEngine,
            scope = scope,
        )

        try {
            composeRule.setContent {
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT)) {
                    AttemptAudioPlayer(
                        controller = controller,
                        modifier = Modifier.padding(16.dp),
                        attachLifecycle = false,
                    )
                }
            }

            composeRule.waitForIdle()
            composeRule.onNodeWithTag(AttachmentTestTags.audioPrimaryButton(audioAssetId)).performClick()

            composeRule.waitUntil(timeoutMillis = 5_000L) {
                controller.uiState.value.phase is AudioPlayerPhase.Playing && playbackEngine.isPlaying()
            }

            val playingPhase = controller.uiState.value.phase as AudioPlayerPhase.Playing
            val sessionBridge = AudioMediaSessionBridge.getInstance(context)
            val token = sessionBridge.sessionToken
            assertNotNull("Active playback must register framework MediaSession token", token)

            val mediaController = MediaController(context, requireNotNull(token))
            composeRule.waitUntil(timeoutMillis = 3_000L) {
                mediaController.playbackState?.state == PlaybackState.STATE_PLAYING
            }

            val playbackState = requireNotNull(mediaController.playbackState)
            val actions = playbackState.actions
            assertTrue("ACTION_PLAY_PAUSE must be allowed", (actions and PlaybackState.ACTION_PLAY_PAUSE) != 0L)
            assertTrue("ACTION_PAUSE must be allowed", (actions and PlaybackState.ACTION_PAUSE) != 0L)

            val forbiddenActions = PlaybackState.ACTION_SEEK_TO or
                PlaybackState.ACTION_FAST_FORWARD or
                PlaybackState.ACTION_REWIND or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_SET_PLAYBACK_SPEED or
                PlaybackState.ACTION_PLAY_FROM_URI
            assertEquals(
                "MediaSession must expose zero seek/skip/speed actions, got actions=$actions",
                0L,
                actions and forbiddenActions,
            )

            // Verify zero media notifications are posted by the app.
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val appNotifications = nm.activeNotifications.filter { it.packageName == context.packageName }
            assertEquals(
                "App must not post any media playback notification",
                0,
                appNotifications.size,
            )

            // Send seekTo + fastForward to MediaSession and verify playback ignores them and stays near start.
            mediaController.transportControls.seekTo(25_000L)
            mediaController.transportControls.fastForward()
            Thread.sleep(450L)
            val posAfterSeekAttempt = playbackEngine.currentPositionMs()
            assertTrue(
                "Seek command via MediaSession must be rejected; expected < 6000ms, got ${posAfterSeekAttempt}ms",
                posAfterSeekAttempt < 6_000L,
            )

            // Server has saved position 12_000ms; headset pause stops audio and switches button to "Phát lại".
            server.overrideServerPositionMs(playingPhase.playId, 12_000L)
            mediaController.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
            mediaController.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))

            composeRule.waitUntil(timeoutMillis = 2_000L) {
                !playbackEngine.isPlaying() && controller.uiState.value.phase is AudioPlayerPhase.Paused
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(AttachmentTestTags.audioPrimaryButton(audioAssetId))
                .assertIsDisplayed()
                .assertTextEquals("Phát lại")

            // Tapping "Phát lại" calls /resume and resumes from server-saved 12_000ms without consuming a play.
            server.resetRequests()
            composeRule.onNodeWithTag(AttachmentTestTags.audioPrimaryButton(audioAssetId)).performClick()
            composeRule.waitUntil(timeoutMillis = 5_000L) {
                controller.uiState.value.phase is AudioPlayerPhase.Playing && playbackEngine.isPlaying()
            }

            val resumeCalls = server.requests().filter { it.path.endsWith("/resume") }
            assertEquals("Resume endpoint must be called once after headset pause", 1, resumeCalls.size)
            val resumedPos = playbackEngine.currentPositionMs()
            assertTrue(
                "Resumed position must start near server-saved 12000ms, got ${resumedPos}ms",
                resumedPos in 11_000L..16_000L,
            )
            assertEquals("Plays left must remain unchanged after resume", 1, controller.uiState.value.playsLeft)
        } finally {
            controller.close()
            playbackEngine.stopAndRelease()
            harness.close()
            server.close()
            scope.cancel()
        }
    }
}
