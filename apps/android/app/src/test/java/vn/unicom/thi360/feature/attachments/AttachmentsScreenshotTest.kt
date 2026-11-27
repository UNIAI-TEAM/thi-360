package vn.unicom.thi360.feature.attachments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import java.util.UUID
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.feature.attachments.model.AudioLockReason
import vn.unicom.thi360.feature.attachments.model.AudioPlayerPhase
import vn.unicom.thi360.feature.attachments.model.AudioPlayerUiState
import vn.unicom.thi360.feature.attachments.model.PauseReason
import vn.unicom.thi360.feature.attachments.model.QuestionAttachment
import vn.unicom.thi360.feature.attachments.ui.AttachmentTestTags
import vn.unicom.thi360.feature.attachments.ui.AttachmentsView
import vn.unicom.thi360.feature.attachments.ui.AttemptAudioPlayer
import vn.unicom.thi360.feature.question.model.ChoiceOptionItem
import vn.unicom.thi360.feature.question.model.QuestionItem
import vn.unicom.thi360.feature.question.model.QuestionMode
import vn.unicom.thi360.feature.question.ui.QuestionView

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AttachmentsScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val audioId = UUID.fromString("00000000-0000-7000-8000-000000000051")
    private val playId = UUID.fromString("00000000-0000-7000-8000-000000000052")
    private val pdfId = UUID.fromString("00000000-0000-7000-8000-000000000053")

    private val sampleQuestion = QuestionItem.SingleChoice(
        id = "q-audio-3",
        displayNo = 3,
        stem = "<p>Nghe đoạn hội thoại và chọn đáp án đúng.</p><p><em>Where are the two speakers planning to go this weekend?</em></p>",
        options = listOf(
            ChoiceOptionItem("a", "<p>To a science museum</p>"),
            ChoiceOptionItem("b", "<p>To their grandparents’ farm</p>"),
            ChoiceOptionItem("c", "<p>To a football match</p>"),
        ),
    )

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun attachments_audio_ready_light() {
        composeRule.setContent {
            AttachmentsTestHost(themeMode = ThemeMode.LIGHT) {
                QuestionView(
                    item = sampleQuestion,
                    answer = null,
                    mode = QuestionMode.Answering,
                    attachments = {
                        AttachmentsView(
                            attachments = listOf(
                                QuestionAttachment(audioId, "nghe-hieu-phan-1-hoi-thoai.mp3", "audio/mpeg"),
                                QuestionAttachment(pdfId, "bang-so-lieu.pdf", "application/pdf"),
                            ),
                            audioPlayer = { att ->
                                AttemptAudioPlayer(
                                    state = AudioPlayerUiState(
                                        assetId = att.assetId,
                                        fileName = att.fileName,
                                        phase = AudioPlayerPhase.Idle(2),
                                        playsUsed = 0,
                                        playsLeft = 2,
                                        maxPlays = 2,
                                        positionMs = 0L,
                                        durationMs = 155_000L,
                                    ),
                                    onPrimaryClick = {},
                                )
                            },
                            onOpenAttachment = {},
                        )
                    },
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/attachments_audio_ready_light.png")
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun attachments_audio_ready_dark() {
        composeRule.setContent {
            AttachmentsTestHost(themeMode = ThemeMode.DARK) {
                QuestionView(
                    item = sampleQuestion,
                    answer = null,
                    mode = QuestionMode.Answering,
                    attachments = {
                        AttachmentsView(
                            attachments = listOf(
                                QuestionAttachment(audioId, "nghe-hieu-phan-1-hoi-thoai.mp3", "audio/mpeg"),
                                QuestionAttachment(pdfId, "bang-so-lieu.pdf", "application/pdf"),
                            ),
                            audioPlayer = { att ->
                                AttemptAudioPlayer(
                                    state = AudioPlayerUiState(
                                        assetId = att.assetId,
                                        fileName = att.fileName,
                                        phase = AudioPlayerPhase.Idle(2),
                                        playsUsed = 0,
                                        playsLeft = 2,
                                        maxPlays = 2,
                                        positionMs = 0L,
                                        durationMs = 155_000L,
                                    ),
                                    onPrimaryClick = {},
                                )
                            },
                            onOpenAttachment = {},
                        )
                    },
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/attachments_audio_ready_dark.png")
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun attachments_audio_states_light() {
        composeRule.setContent {
            AttachmentsTestHost(themeMode = ThemeMode.LIGHT) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    AttemptAudioPlayer(
                        state = AudioPlayerUiState(
                            assetId = audioId,
                            fileName = "nghe-hieu-phan-1.mp3",
                            phase = AudioPlayerPhase.Playing(playId, 70_000L),
                            playsUsed = 1,
                            playsLeft = 1,
                            maxPlays = 2,
                            positionMs = 70_000L,
                            durationMs = 155_000L,
                        ),
                        onPrimaryClick = {},
                    )
                    AttemptAudioPlayer(
                        state = AudioPlayerUiState(
                            assetId = UUID.fromString("00000000-0000-7000-8000-000000000061"),
                            fileName = "nghe-hieu-tam-dung.mp3",
                            phase = AudioPlayerPhase.Paused(playId, 70_000L, PauseReason.Background),
                            playsUsed = 1,
                            playsLeft = 1,
                            maxPlays = 2,
                            positionMs = 70_000L,
                            durationMs = 155_000L,
                        ),
                        onPrimaryClick = {},
                    )
                    AttemptAudioPlayer(
                        state = AudioPlayerUiState(
                            assetId = UUID.fromString("00000000-0000-7000-8000-000000000062"),
                            fileName = "nghe-hieu-het-luot.mp3",
                            phase = AudioPlayerPhase.Exhausted,
                            playsUsed = 2,
                            playsLeft = 0,
                            maxPlays = 2,
                            positionMs = 155_000L,
                            durationMs = 155_000L,
                        ),
                        onPrimaryClick = {},
                    )
                    AttemptAudioPlayer(
                        state = AudioPlayerUiState(
                            assetId = UUID.fromString("00000000-0000-7000-8000-000000000063"),
                            fileName = "nghe-hieu-bi-day.mp3",
                            phase = AudioPlayerPhase.Locked(AudioLockReason.Evicted),
                            playsUsed = 1,
                            playsLeft = 1,
                            maxPlays = 2,
                            positionMs = 40_000L,
                            durationMs = 155_000L,
                        ),
                        onPrimaryClick = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/attachments_audio_states_light.png")
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun attachments_audio_states_dark() {
        composeRule.setContent {
            AttachmentsTestHost(themeMode = ThemeMode.DARK) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    AttemptAudioPlayer(
                        state = AudioPlayerUiState(
                            assetId = audioId,
                            fileName = "nghe-hieu-tam-dung.mp3",
                            phase = AudioPlayerPhase.Paused(playId, 70_000L, PauseReason.Background),
                            playsUsed = 1,
                            playsLeft = 1,
                            maxPlays = 2,
                            positionMs = 70_000L,
                            durationMs = 155_000L,
                        ),
                        onPrimaryClick = {},
                    )
                    AttemptAudioPlayer(
                        state = AudioPlayerUiState(
                            assetId = UUID.fromString("00000000-0000-7000-8000-000000000064"),
                            fileName = "nghe-hieu-mat-mang.mp3",
                            phase = AudioPlayerPhase.WaitingNetwork(playId),
                            playsUsed = 1,
                            playsLeft = 1,
                            maxPlays = 2,
                            positionMs = 40_000L,
                            durationMs = 155_000L,
                            isOnline = false,
                        ),
                        onPrimaryClick = {},
                    )
                    AttemptAudioPlayer(
                        state = AudioPlayerUiState(
                            assetId = UUID.fromString("00000000-0000-7000-8000-000000000065"),
                            fileName = "nghe-hieu-het-gio.mp3",
                            phase = AudioPlayerPhase.Locked(AudioLockReason.TimeUp),
                            playsUsed = 1,
                            playsLeft = 1,
                            maxPlays = 2,
                            positionMs = 40_000L,
                            durationMs = 155_000L,
                        ),
                        onPrimaryClick = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/attachments_audio_states_dark.png")
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 1.3f)
    fun attachments_audio_font130_light() {
        composeRule.setContent {
            AttachmentsTestHost(themeMode = ThemeMode.LIGHT) {
                AttemptAudioPlayer(
                    state = AudioPlayerUiState(
                        assetId = audioId,
                        fileName = "nghe-hieu-phan-1-hoi-thoai-rat-dai.mp3",
                        phase = AudioPlayerPhase.Paused(playId, 70_000L, PauseReason.Background),
                        playsUsed = 1,
                        playsLeft = 1,
                        maxPlays = 2,
                        positionMs = 70_000L,
                        durationMs = 155_000L,
                    ),
                    onPrimaryClick = {},
                )
            }
        }
        composeRule.waitForIdle()

        val buttonBounds = composeRule.onNodeWithTag(AttachmentTestTags.audioPrimaryButton(audioId)).getUnclippedBoundsInRoot()
        val badgeBounds = composeRule.onNodeWithTag(AttachmentTestTags.audioPlaysBadge(audioId)).getUnclippedBoundsInRoot()
        val bannerBounds = composeRule.onNodeWithTag(AttachmentTestTags.audioBanner(audioId)).getUnclippedBoundsInRoot()

        composeRule.onNodeWithTag(AttachmentTestTags.audioPrimaryButton(audioId)).assertIsDisplayed()
        composeRule.onNodeWithTag(AttachmentTestTags.audioPlaysBadge(audioId)).assertIsDisplayed()
        composeRule.onNodeWithTag(AttachmentTestTags.audioBanner(audioId)).assertIsDisplayed()
        val horizontalOverlap = buttonBounds.left < badgeBounds.right && badgeBounds.left < buttonBounds.right
        val verticalOverlap = buttonBounds.top < badgeBounds.bottom && badgeBounds.top < buttonBounds.bottom
        assertTrue(
            "Button ($buttonBounds) and plays badge ($badgeBounds) must not overlap at 130% fontScale",
            !(horizontalOverlap && verticalOverlap),
        )
        assertTrue(
            "Banner ($bannerBounds) must sit below controls without clipping",
            bannerBounds.top >= buttonBounds.bottom && (bannerBounds.bottom - bannerBounds.top) > 0.dp,
        )

        composeRule.onRoot().captureRoboImage("src/test/snapshots/attachments_audio_font130_light.png")
    }
}
