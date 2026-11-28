package vn.unicom.thi360.feature.player

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.network.MutableConnectivityMonitor
import vn.unicom.thi360.core.network.generated.model.AttemptItemView
import vn.unicom.thi360.core.network.generated.model.SingleChoice
import vn.unicom.thi360.core.network.generated.model.SingleChoiceAnswer
import vn.unicom.thi360.core.storage.AccountScopedStore
import vn.unicom.thi360.feature.attemptengine.AttemptEngine
import vn.unicom.thi360.feature.attemptengine.AttemptOperation
import vn.unicom.thi360.feature.attemptengine.EditSessionState
import vn.unicom.thi360.feature.attemptengine.FakeAttemptServer
import vn.unicom.thi360.feature.attemptengine.PartSelection
import vn.unicom.thi360.feature.attemptengine.SaveStatus
import vn.unicom.thi360.feature.question.ui.QuestionTestTags

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class PlayerAnswerFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var server: FakeAttemptServer
    private lateinit var storeRoot: java.io.File
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        server = FakeAttemptServer().start()
        storeRoot = Files.createTempDirectory("thi360-answer-flow-").toFile()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.shutdown()
        storeRoot.deleteRecursively()
    }

    @Test
    fun optimisticSelectionDisplaysImmediatelyDuringDelayedSaveThenConfirmsSaved() {
        val attempt = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("in-progress-per-part"))
        server.setAttemptState(attempt)
        val saveGate = server.gateNext(AttemptOperation.SAVE_ANSWERS)

        val engine = AttemptEngine(
            attemptId = attempt.attemptId,
            accountId = "acc-flow",
            api = server.virtualTimeApi(),
            store = AccountScopedStore(storeRoot, "acc-flow"),
            connectivity = MutableConnectivityMonitor(true),
            scope = scope,
        )

        composeRule.setContent {
            PlayerTestTheme {
                PlayerHost(
                    attemptId = attempt.attemptId.toString(),
                    engine = engine,
                    onCloseAttempt = {},
                    onBackToMyExams = {},
                    onViewResult = {},
                    onReplaceAttempt = {},
                    onOpenPrepare = {},
                )
            }
        }

        waitForEngineState(composeRule, engine) { it.session is EditSessionState.Editing }

        val firstPart = PartSelection.sortedParts(attempt).first { it.state == vn.unicom.thi360.core.network.generated.model.AttemptPartStateView.State.OPEN }
        val scItem = PartSelection.leafItems(firstPart)
            .first { it.questionType == AttemptItemView.QuestionType.SINGLE_CHOICE }
        val optionId = (scItem.questionBody as SingleChoice).options!!.first().id!!

        composeRule.onNodeWithTag(QuestionTestTags.choiceOption(optionId)).performClick()
        composeRule.waitForIdle()

        // Immediately optimistic in engine & UI while PUT /answers is still gated (simulating 3s latency).
        assertEquals(SingleChoiceAnswer(optionId), engine.state.value.answers[scItem.itemId!!]?.value)
        assertTrue(
            "Expected Saving status while PUT /answers is in-flight, was ${engine.state.value.save}",
            engine.state.value.save is SaveStatus.Saving,
        )
        composeRule.onNodeWithTag(PlayerTestTags.TOP_BAR_SAVE_STATUS)
            .assertIsDisplayed()
            .assertTextContains("Đang lưu", substring = true)

        // Release server save response -> transitions cleanly to Saved.
        saveGate.complete(Unit)
        waitForEngineState(composeRule, engine) { it.save == SaveStatus.Saved }
        composeRule.onNodeWithTag(PlayerTestTags.TOP_BAR_SAVE_STATUS).assertTextContains("Đã lưu")
    }
}
