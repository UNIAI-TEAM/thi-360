package vn.unicom.thi360.feature.player

import android.text.SpannableStringBuilder
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.BaseInputConnection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createComposeRule
import java.nio.file.Files
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.network.MutableConnectivityMonitor
import vn.unicom.thi360.core.network.generated.model.AttemptItemView
import vn.unicom.thi360.core.network.generated.model.AttemptPartStateView
import vn.unicom.thi360.core.network.generated.model.Essay
import vn.unicom.thi360.core.storage.AccountScopedStore
import vn.unicom.thi360.feature.attemptengine.AttemptEngine
import vn.unicom.thi360.feature.attemptengine.AttemptOperation
import vn.unicom.thi360.feature.attemptengine.EditSessionState
import vn.unicom.thi360.feature.attemptengine.FakeAttemptServer
import vn.unicom.thi360.feature.attemptengine.VirtualEngineClock
import vn.unicom.thi360.feature.question.ui.EssayEditText

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class PlayerEssayImeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var server: FakeAttemptServer
    private lateinit var storeRoot: java.io.File

    @Before
    fun setUp() {
        server = FakeAttemptServer().start()
        storeRoot = Files.createTempDirectory("thi360-essay-ime-").toFile()
    }

    @After
    fun tearDown() {
        server.shutdown()
        storeRoot.deleteRecursively()
    }

    @Test
    fun fifteenSecondEssayAutosaveSendsOnlyCommittedTextExcludingActiveImeComposingSpan() {
        val base = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("in-progress-shared"))
        val firstPart = base.parts!!.first()
        val essayItemId = firstPart.items!!.first().itemId!!
        val essayItem = AttemptItemView(
            itemId = essayItemId,
            displayNo = 1,
            content = "<p>Viết đoạn văn ngắn.</p>",
            questionType = AttemptItemView.QuestionType.ESSAY,
            questionBody = Essay(wordLimit = 100),
        )
        val attempt = base.copy(
            parts = listOf(firstPart.copy(state = AttemptPartStateView.State.OPEN, items = listOf(essayItem))),
        )
        server.setAttemptState(attempt)

        val testDispatcher = StandardTestDispatcher()
        val testScope = TestScope(testDispatcher)
        val clock = VirtualEngineClock(testScope)
        val engine = AttemptEngine(
            attemptId = attempt.attemptId,
            accountId = "acc-ime",
            api = server.virtualTimeApi(),
            store = AccountScopedStore(storeRoot, "acc-ime"),
            clock = clock,
            connectivity = MutableConnectivityMonitor(true),
            scope = testScope.backgroundScope,
        )

        var rootView: View? = null
        composeRule.setContent {
            rootView = LocalView.current
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

        testScope.testScheduler.runCurrent()
        composeRule.waitForIdle()
        assertTrue(engine.state.value.session is EditSessionState.Editing)

        val editor = findEssayEditText(rootView!!)
        requireNotNull(editor) { "Expected EssayEditText inside PlayerHost" }

        // Step 1: use the real Android InputConnection to commit text every 1.5s while keeping an
        // active Vietnamese IME composing token ("Vieej") so the 2s debounce resets until the 15s
        // maxWait timer fires during active composition.
        val committedWords = listOf("Bài", "làm", "tiếng", "Việt", "học", "sinh", "tự", "luận", "thi", "cử", "mẫu")
        val inputConnection = composeRule.runOnIdle {
            editor.requestFocus()
            checkNotNull(editor.onCreateInputConnection(android.view.inputmethod.EditorInfo()))
        }
        for (word in committedWords) {
            composeRule.runOnIdle {
                inputConnection.commitText("$word ", 1)
                inputConnection.setComposingText("Vieej", 1)
            }
            testScope.testScheduler.advanceTimeBy(1_500L)
            testScope.testScheduler.runCurrent()
        }

        val firstSaveCalls = server.calls(AttemptOperation.SAVE_ANSWERS)
        assertTrue("Expected at least 1 PUT /answers at 15s maxWait", firstSaveCalls.isNotEmpty())
        val firstPayload = firstSaveCalls.last().body.orEmpty()
        assertTrue("Committed text must be saved: $firstPayload", firstPayload.contains("Bài làm tiếng"))
        assertFalse("Active composing buffer 'Vieej' must NOT be sent: $firstPayload", firstPayload.contains("Vieej"))

        // Step 2: IME commits final word "hoànchỉnh" (finishing composition) -> next save includes it.
        composeRule.runOnIdle {
            inputConnection.commitText("hoànchỉnh", 1)
            inputConnection.finishComposingText()
        }
        testScope.testScheduler.advanceTimeBy(3_000L)
        testScope.testScheduler.runCurrent()

        val finalSaveBody = server.calls(AttemptOperation.SAVE_ANSWERS).last().body.orEmpty()
        assertTrue("Committed final text must be sent after commit: $finalSaveBody", finalSaveBody.contains("hoànchỉnh"))
        engine.close()
    }

    private fun findEssayEditText(view: View): EssayEditText? {
        if (view is EssayEditText) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findEssayEditText(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }
}
