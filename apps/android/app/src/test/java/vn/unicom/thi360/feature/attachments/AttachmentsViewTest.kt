package vn.unicom.thi360.feature.attachments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.unicom.thi360.core.network.generated.model.AttemptAttachmentView
import vn.unicom.thi360.core.network.generated.model.AttemptItemView
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.attachments.model.AttachmentMerge
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
import vn.unicom.thi360.feature.question.ui.QuestionSetView
import vn.unicom.thi360.feature.question.ui.QuestionTestTags
import vn.unicom.thi360.feature.question.ui.QuestionView

@Composable
internal fun AttachmentsTestHost(
    themeMode: ThemeMode = ThemeMode.LIGHT,
    locale: AppLocale = AppLocale.VI,
    content: @Composable () -> Unit,
) {
    Thi360Theme(
        preferences = AppPreferences(
            locale = locale,
            themeMode = themeMode,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Thi360Theme.colors.bgPage)
                .padding(16.dp),
        ) {
            content()
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AttachmentsViewTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun questionSetDeduplicatesParentAndChildAudioAndExcludesChildNonAudio() {
        val attemptId = UUID.fromString("00000000-0000-7000-8000-000000000001")
        val sharedAudioId = UUID.fromString("00000000-0000-7000-8000-000000000010")
        val childOnlyAudioId = UUID.fromString("00000000-0000-7000-8000-000000000011")
        val childPdfId = UUID.fromString("00000000-0000-7000-8000-000000000012")

        // Case 1 (AC-512): Set has audio A and child 2 also has audio A -> strictly ONE player in set header.
        val parentWithAudio = listOf(
            AttemptAttachmentView(
                assetId = sharedAudioId,
                fileName = "hoi-thoai-phan-1.mp3",
                mime = "audio/mpeg",
                url = "/api/v1/me/attempts/$attemptId/assets/$sharedAudioId",
            ),
        )
        val childItemsWithDupAudio = listOf(
            AttemptItemView(itemId = UUID.randomUUID(), attachments = emptyList()),
            AttemptItemView(
                itemId = UUID.randomUUID(),
                attachments = listOf(
                    AttemptAttachmentView(
                        assetId = sharedAudioId,
                        fileName = "hoi-thoai-phan-1.mp3",
                        mime = "audio/mpeg",
                        url = "/api/v1/me/attempts/$attemptId/assets/$sharedAudioId",
                    ),
                ),
            ),
        )
        val mergedDup = AttachmentMerge.mergeQuestionAttachments(parentWithAudio, childItemsWithDupAudio)
        assertEquals(1, mergedDup.size)
        assertEquals(sharedAudioId, mergedDup.single().assetId)

        val setItem = QuestionItem.QuestionSet(
            id = "set-1",
            displayNo = 1,
            stem = "<p>Nghe đoạn hội thoại và trả lời các câu 1–2.</p>",
            passage = "<p>Ngữ liệu bài nghe.</p>",
            children = listOf(
                QuestionItem.SingleChoice(
                    id = "child-1",
                    displayNo = 1,
                    stem = "<p>Câu hỏi con 1</p>",
                    options = listOf(ChoiceOptionItem("o1", "<p>Phương án A</p>")),
                ),
                QuestionItem.SingleChoice(
                    id = "child-2",
                    displayNo = 2,
                    stem = "<p>Câu hỏi con 2</p>",
                    options = listOf(ChoiceOptionItem("o2", "<p>Phương án B</p>")),
                ),
            ),
        )

        composeRule.setContent {
            AttachmentsTestHost {
                QuestionSetView(
                    item = setItem,
                    answers = emptyMap(),
                    mode = QuestionMode.Answering,
                    attachments = {
                        AttachmentsView(
                            attachments = mergedDup,
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
                )
            }
        }

        composeRule.onNodeWithTag(AttachmentTestTags.ATTACHMENTS_LABEL).assertTextEquals("Tệp đính kèm")
        composeRule.onAllNodesWithTag(AttachmentTestTags.audioPlayer(sharedAudioId)).assertCountEquals(1)
        composeRule.onNodeWithTag(AttachmentTestTags.audioFileName(sharedAudioId)).assertTextEquals("hoi-thoai-phan-1.mp3")

        // Case 2 (AC-727): Set has no parent attachments; child 1 has PDF + audio -> ONLY audio appears, PDF never appears.
        val childItemsPdfAndAudio = listOf(
            AttemptItemView(
                itemId = UUID.randomUUID(),
                attachments = listOf(
                    AttemptAttachmentView(
                        assetId = childPdfId,
                        fileName = "phu-luc-cau-con.pdf",
                        mime = "application/pdf",
                        url = "/api/v1/me/attempts/$attemptId/assets/$childPdfId",
                    ),
                    AttemptAttachmentView(
                        assetId = childOnlyAudioId,
                        fileName = "nghe-cau-con.mp3",
                        mime = "audio/mpeg",
                        url = "/api/v1/me/attempts/$attemptId/assets/$childOnlyAudioId",
                    ),
                ),
            ),
        )
        val mergedChildOnly = AttachmentMerge.mergeQuestionAttachments(emptyList(), childItemsPdfAndAudio)
        assertEquals(listOf(childOnlyAudioId), mergedChildOnly.map { it.assetId })
        assertTrue(mergedChildOnly.none { it.assetId == childPdfId })
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun childNonAudioPdfNotRenderedInQuestionSetSlotAndAttachmentsSitAboveTitle() {
        val attemptId = UUID.fromString("00000000-0000-7000-8000-000000000001")
        val audioId = UUID.fromString("00000000-0000-7000-8000-000000000021")
        val childPdfId = UUID.fromString("00000000-0000-7000-8000-000000000022")
        val merged = AttachmentMerge.mergeQuestionAttachments(
            parentAttachments = emptyList(),
            childItems = listOf(
                AttemptItemView(
                    itemId = UUID.randomUUID(),
                    attachments = listOf(
                        AttemptAttachmentView(
                            assetId = childPdfId,
                            fileName = "bang-con-an.pdf",
                            mime = "application/pdf",
                            url = "/api/v1/me/attempts/$attemptId/assets/$childPdfId",
                        ),
                        AttemptAttachmentView(
                            assetId = audioId,
                            fileName = "doan-nghe-1.mp3",
                            mime = "audio/mpeg",
                            url = "/api/v1/me/attempts/$attemptId/assets/$audioId",
                        ),
                    ),
                ),
            ),
        )

        val singleQuestion = QuestionItem.SingleChoice(
            id = "q-3",
            displayNo = 3,
            stem = "<p>Nghe đoạn hội thoại và chọn đáp án đúng.</p>",
            options = listOf(ChoiceOptionItem("a", "<p>A</p>")),
        )

        composeRule.setContent {
            AttachmentsTestHost {
                QuestionView(
                    item = singleQuestion,
                    answer = null,
                    mode = QuestionMode.Answering,
                    attachments = {
                        AttachmentsView(
                            attachments = merged,
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
                )
            }
        }

        val attachmentsBounds = composeRule.onNodeWithTag(AttachmentTestTags.ATTACHMENTS_CONTAINER).getUnclippedBoundsInRoot()
        val titleBounds = composeRule.onNodeWithTag(QuestionTestTags.FRAME_TITLE).getUnclippedBoundsInRoot()
        assertTrue(
            "Attachments block (${attachmentsBounds.bottom}) must sit above question title (${titleBounds.top})",
            attachmentsBounds.bottom <= titleBounds.top,
        )

        composeRule.onNodeWithTag(AttachmentTestTags.audioPlayer(audioId)).assertIsDisplayed()
        composeRule.onNodeWithText("bang-con-an.pdf").assertDoesNotExist()
        composeRule.onNodeWithTag(AttachmentTestTags.fileRow(childPdfId)).assertDoesNotExist()

        // Progress bar must NOT expose click/setProgress interactive semantics (R-134).
        val progressSemantics = composeRule.onNodeWithTag(AttachmentTestTags.audioProgressBar(audioId))
            .fetchSemanticsNode().config
        assertFalse(SemanticsActions.OnClick in progressSemantics)
        assertFalse(SemanticsActions.SetProgress in progressSemantics)
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun englishLocaleAndPausedResumableCopyMatchSpec() {
        val audioId = UUID.fromString("00000000-0000-7000-8000-000000000031")
        val playId = UUID.fromString("00000000-0000-7000-8000-000000000032")

        composeRule.setContent {
            AttachmentsTestHost(locale = AppLocale.EN) {
                AttachmentsView(
                    attachments = listOf(
                        QuestionAttachment(
                            assetId = audioId,
                            fileName = "listening-part-1.mp3",
                            mime = "audio/mpeg",
                        ),
                    ),
                    audioPlayer = { att ->
                        AttemptAudioPlayer(
                            state = AudioPlayerUiState(
                                assetId = att.assetId,
                                fileName = att.fileName,
                                phase = AudioPlayerPhase.Paused(playId, 70_000L, PauseReason.Background),
                                playsUsed = 1,
                                playsLeft = 1,
                                maxPlays = 2,
                                positionMs = 70_000L,
                                durationMs = 155_000L,
                            ),
                            onPrimaryClick = {},
                        )
                    },
                    onOpenAttachment = {},
                )
            }
        }

        composeRule.onNodeWithTag(AttachmentTestTags.ATTACHMENTS_LABEL).assertTextEquals("Attachments")
        composeRule.onNodeWithTag(AttachmentTestTags.audioPrimaryButton(audioId)).assertIsEnabled()
        composeRule.onNodeWithText("Resume playback").assertIsDisplayed()
        composeRule.onNodeWithTag(AttachmentTestTags.audioPlaysBadge(audioId)).assertTextEquals("1/2 plays left")
        composeRule.onNodeWithTag(AttachmentTestTags.audioTimeLabel(audioId)).assertTextEquals("01:10 / 02:35")
        composeRule.onNodeWithText("Audio paused. Tap Resume to continue without using a play.").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-xhdpi")
    fun playingStateDisablesPrimaryButtonAndShowsPlayingLabel() {
        val audioId = UUID.fromString("00000000-0000-7000-8000-000000000041")
        val playId = UUID.fromString("00000000-0000-7000-8000-000000000042")

        composeRule.setContent {
            AttachmentsTestHost {
                AttemptAudioPlayer(
                    state = AudioPlayerUiState(
                        assetId = audioId,
                        fileName = "part1.mp3",
                        phase = AudioPlayerPhase.Playing(playId, 20_000L),
                        playsUsed = 1,
                        playsLeft = 1,
                        maxPlays = 2,
                        positionMs = 20_000L,
                        durationMs = 155_000L,
                    ),
                    onPrimaryClick = {},
                )
            }
        }

        composeRule.onNodeWithTag(AttachmentTestTags.audioPrimaryButton(audioId)).assertIsNotEnabled()
        composeRule.onNodeWithText("Đang phát…").assertIsDisplayed()
        composeRule.onNodeWithTag(AttachmentTestTags.audioPlaysBadge(audioId)).assertTextEquals("Còn 1/2 lần nghe")
        composeRule.onNodeWithTag(AttachmentTestTags.audioTimeLabel(audioId)).assertTextEquals("00:20 / 02:35")
    }
}
