package vn.unicom.thi360.feature.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.MutableConnectivityMonitor
import vn.unicom.thi360.core.network.generated.model.AnswerValue
import vn.unicom.thi360.core.network.generated.model.AttemptItemView
import vn.unicom.thi360.core.network.generated.model.AttemptPartStateView
import vn.unicom.thi360.core.network.generated.model.AttemptStateResponse
import vn.unicom.thi360.core.network.generated.model.ClaimSessionRequest
import vn.unicom.thi360.core.network.generated.model.ClaimSessionResponse
import vn.unicom.thi360.core.network.generated.model.ConfirmPartChoiceRequest
import vn.unicom.thi360.core.network.generated.model.FinalAnswerResult
import vn.unicom.thi360.core.network.generated.model.FinishPartRequest
import vn.unicom.thi360.core.network.generated.model.FinishPartResponse
import vn.unicom.thi360.core.network.generated.model.HeartbeatResponse
import vn.unicom.thi360.core.network.generated.model.OptionView
import vn.unicom.thi360.core.network.generated.model.PartChoiceResponse
import vn.unicom.thi360.core.network.generated.model.RetryPartRequest
import vn.unicom.thi360.core.network.generated.model.SaveAnswersRequest
import vn.unicom.thi360.core.network.generated.model.SaveAnswersResponse
import vn.unicom.thi360.core.network.generated.model.SingleChoice
import vn.unicom.thi360.core.network.generated.model.SingleChoiceAnswer
import vn.unicom.thi360.core.network.generated.model.SubmitAttemptRequest
import vn.unicom.thi360.core.network.generated.model.SubmitAttemptResponse
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.storage.AccountScopedStore
import vn.unicom.thi360.feature.attemptengine.AttemptApi
import vn.unicom.thi360.feature.attemptengine.ClaimSessionApiResult
import vn.unicom.thi360.feature.attemptengine.OpenApiAttemptApi
import vn.unicom.thi360.feature.question.ui.QuestionTestTags
import vn.unicom.thi360.shell.Thi360App
import vn.unicom.thi360.shell.routes.AppNavigator
import vn.unicom.thi360.shell.routes.AppRoute
import vn.unicom.thi360.shell.routes.LocalPlayerRouteOverrides
import vn.unicom.thi360.shell.routes.PlayerRouteOverrides

@RunWith(AndroidJUnit4::class)
class PlayerProcessDeathInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var storeRoot: File

    @Before
    fun setUp() {
        storeRoot = File(composeRule.activity.cacheDir, "player-process-death-${UUID.randomUUID()}").apply {
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        storeRoot.deleteRecursively()
    }

    private fun buildFixture(): Triple<AttemptStateResponse, UUID, UUID> {
        val attemptId = UUID.randomUUID()
        val partId = UUID.randomUUID()
        val item1Id = UUID.randomUUID()
        val item2Id = UUID.randomUUID()
        val now = OffsetDateTime.now()

        val attemptState = AttemptStateResponse(
            attemptId = attemptId,
            examId = UUID.randomUUID(),
            examName = "Thi thử HSA Process Death",
            attemptNo = 1,
            state = AttemptStateResponse.State.IN_PROGRESS,
            navigationMode = AttemptStateResponse.NavigationMode.SEQUENTIAL,
            timing = AttemptStateResponse.Timing.PER_PART,
            serverNow = now,
            parts = listOf(
                AttemptPartStateView(
                    partId = partId,
                    ord = 0,
                    partName = "Phần 1: Toán học",
                    state = AttemptPartStateView.State.OPEN,
                    deadlineAt = now.plusMinutes(20),
                    items = listOf(
                        AttemptItemView(
                            itemId = item1Id,
                            displayNo = 1,
                            content = "<p>Câu 1</p>",
                            questionType = AttemptItemView.QuestionType.SINGLE_CHOICE,
                            questionBody = SingleChoice(
                                options = listOf(
                                    OptionView(id = "opt-1a", content = "Đáp án 1A"),
                                    OptionView(id = "opt-1b", content = "Đáp án 1B"),
                                ),
                            ),
                        ),
                        AttemptItemView(
                            itemId = item2Id,
                            displayNo = 2,
                            content = "<p>Câu 2</p>",
                            questionType = AttemptItemView.QuestionType.SINGLE_CHOICE,
                            questionBody = SingleChoice(
                                options = listOf(
                                    OptionView(id = "opt-2a", content = "Đáp án 2A"),
                                    OptionView(id = "opt-2b", content = "Đáp án 2B"),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        return Triple(attemptState, partId, item2Id)
    }

    private fun buildFakeApi(
        attemptState: AttemptStateResponse,
        activeSession: AtomicReference<UUID?>,
        savedOnServer: MutableMap<UUID, AnswerValue?>,
    ): AttemptApi = object : AttemptApi {
        override suspend fun getState(attemptId: UUID): ApiResult<AttemptStateResponse> =
            ApiResult.Success(attemptState.copy(serverNow = OffsetDateTime.now()))

        override suspend fun claimSession(
            attemptId: UUID,
            request: ClaimSessionRequest,
        ): ClaimSessionApiResult {
            val cur = activeSession.get()
            if (cur != null && cur != request.sessionId && request.takeover != true) {
                return ClaimSessionApiResult.OpenElsewhere(
                    lastSeenAt = OffsetDateTime.now(),
                    code = OpenApiAttemptApi.CODE_ATTEMPT_OPEN_ELSEWHERE,
                )
            }
            val sid = cur ?: request.sessionId ?: UUID.randomUUID()
            activeSession.set(sid)
            return ClaimSessionApiResult.Claimed(
                ClaimSessionResponse(sessionId = sid, serverNow = OffsetDateTime.now()),
            )
        }

        override suspend fun heartbeat(attemptId: UUID, sessionId: UUID): ApiResult<HeartbeatResponse> =
            ApiResult.Success(HeartbeatResponse(serverNow = OffsetDateTime.now(), state = HeartbeatResponse.State.IN_PROGRESS))

        override suspend fun saveAnswers(
            attemptId: UUID,
            sessionId: UUID,
            request: SaveAnswersRequest,
        ): ApiResult<SaveAnswersResponse> {
            val results = request.changes.orEmpty().map { ch ->
                savedOnServer[ch.itemId] = ch.answer
                FinalAnswerResult(itemId = ch.itemId, seq = ch.seq, status = FinalAnswerResult.Status.SAVED)
            }
            return ApiResult.Success(SaveAnswersResponse(results = results, serverNow = OffsetDateTime.now()))
        }

        override suspend fun finishPart(
            attemptId: UUID,
            partId: UUID,
            sessionId: UUID,
            request: FinishPartRequest,
        ): ApiResult<FinishPartResponse> = error("Not used")

        override suspend fun submit(
            attemptId: UUID,
            sessionId: UUID,
            idempotencyKey: String,
            request: SubmitAttemptRequest,
        ): ApiResult<SubmitAttemptResponse> = error("Not used")

        override suspend fun confirmPartChoice(
            attemptId: UUID,
            partId: UUID,
            sessionId: UUID,
            idempotencyKey: String,
            request: ConfirmPartChoiceRequest,
        ): ApiResult<PartChoiceResponse> = error("Not used")

        override suspend fun retryPartGeneration(
            attemptId: UUID,
            partId: UUID,
            sessionId: UUID,
            idempotencyKey: String,
            request: RetryPartRequest,
        ): ApiResult<PartChoiceResponse> = error("Not used")
    }

    @Test
    fun processDeathRecreationRestoresAttemptPositionAndDraftWithoutTakeoverDialog() {
        val (attemptState, _, item2Id) = buildFixture()
        val attemptId = requireNotNull(attemptState.attemptId)
        val activeSession = AtomicReference<UUID?>(null)
        val savedOnServer = linkedMapOf<UUID, AnswerValue?>()
        val connectivity = MutableConnectivityMonitor(true)
        val fakeApi = buildFakeApi(attemptState, activeSession, savedOnServer)

        val initialOverrides = PlayerRouteOverrides(
            attemptApi = fakeApi,
            store = AccountScopedStore(storeRoot, "acc-death"),
            accountId = "acc-death",
            connectivity = connectivity,
        )

        val navControllerRef = AtomicReference<NavHostController?>(null)
        val navRef = AtomicReference<AppNavigator?>(null)
        composeRule.setContent {
            val navController = rememberNavController()
            val appNav = AppNavigator(navController)
            navControllerRef.set(navController)
            navRef.set(appNav)
            CompositionLocalProvider(LocalPlayerRouteOverrides provides initialOverrides) {
                Thi360App(
                    preferences = AppPreferences(locale = AppLocale.VI),
                    navController = navController,
                    navigator = appNav,
                )
            }
        }

        composeRule.runOnIdle { navRef.get()!!.openAttempt(attemptId.toString()) }
        composeRule.waitUntil(10_000L) {
            composeRule.onAllNodes(hasTestTag(PlayerTestTags.TOP_BAR)).fetchSemanticsNodes().isNotEmpty()
        }

        if (composeRule.onAllNodes(hasTestTag(PlayerTestTags.NARROW_GRID_BUTTON)).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag(PlayerTestTags.NARROW_GRID_BUTTON).performClick()
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithTag(PlayerTestTags.gridCell(item2Id.toString())).performClick()
        composeRule.waitForIdle()

        connectivity.setOnline(false)
        composeRule.onNodeWithTag(QuestionTestTags.choiceOption("opt-2a")).performClick()
        composeRule.waitForIdle()
        val originalSessionId = activeSession.get()
        assertNotNull(originalSessionId)

        var savedNavState: Bundle? = null
        composeRule.runOnIdle {
            savedNavState = navControllerRef.get()!!.saveState()
        }
        assertNotNull(savedNavState)

        connectivity.setOnline(true)
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        val recreatedOverrides = PlayerRouteOverrides(
            attemptApi = fakeApi,
            store = AccountScopedStore(storeRoot, "acc-death"),
            accountId = "acc-death",
            connectivity = connectivity,
        )
        val restoredNavControllerRef = AtomicReference<NavHostController?>(null)
        composeRule.setContent {
            val navController = rememberNavController()
            remember(navController, savedNavState) {
                navController.restoreState(savedNavState)
                true
            }
            val appNav = AppNavigator(navController)
            restoredNavControllerRef.set(navController)
            CompositionLocalProvider(LocalPlayerRouteOverrides provides recreatedOverrides) {
                Thi360App(
                    preferences = AppPreferences(locale = AppLocale.VI),
                    navController = navController,
                    navigator = appNav,
                )
            }
        }

        composeRule.waitUntil(10_000L) {
            composeRule.onAllNodes(hasTestTag(PlayerTestTags.TOP_BAR)).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(
            attemptId.toString(),
            restoredNavControllerRef.get()?.currentBackStackEntry?.arguments?.getString(AppRoute.ARG_ATTEMPT_ID),
        )
        composeRule.onNodeWithTag(PlayerTestTags.TAKEOVER_DIALOG).assertDoesNotExist()
        composeRule.waitUntil(10_000L) {
            savedOnServer[item2Id] == SingleChoiceAnswer("opt-2a")
        }
        if (composeRule.onAllNodes(hasTestTag(PlayerTestTags.NARROW_GRID_BUTTON)).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag(PlayerTestTags.NARROW_GRID_BUTTON).performClick()
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithTag(PlayerTestTags.gridCell(item2Id.toString())).assertIsSelected()
        assertEquals(originalSessionId, activeSession.get())
        assertEquals(SingleChoiceAnswer("opt-2a"), savedOnServer[item2Id])
    }

    @Test
    fun coldStartAfterForceStopOpensStartRouteAndContinueReentersAttempt() {
        val (attemptState, _, item2Id) = buildFixture()
        val attemptId = requireNotNull(attemptState.attemptId)
        val activeSession = AtomicReference<UUID?>(null)
        val savedOnServer = linkedMapOf<UUID, AnswerValue?>()
        val connectivity = MutableConnectivityMonitor(true)
        val fakeApi = buildFakeApi(attemptState, activeSession, savedOnServer)

        val initialOverrides = PlayerRouteOverrides(
            attemptApi = fakeApi,
            store = AccountScopedStore(storeRoot, "acc-death"),
            accountId = "acc-death",
            connectivity = connectivity,
        )

        val navRef = AtomicReference<AppNavigator?>(null)
        composeRule.setContent {
            val navController = rememberNavController()
            val appNav = AppNavigator(navController)
            navRef.set(appNav)
            CompositionLocalProvider(LocalPlayerRouteOverrides provides initialOverrides) {
                Thi360App(
                    preferences = AppPreferences(locale = AppLocale.VI),
                    navController = navController,
                    navigator = appNav,
                )
            }
        }

        composeRule.runOnIdle { navRef.get()!!.openAttempt(attemptId.toString()) }
        composeRule.waitUntil(10_000L) {
            composeRule.onAllNodes(hasTestTag(PlayerTestTags.TOP_BAR)).fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodes(hasTestTag(PlayerTestTags.NARROW_GRID_BUTTON)).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag(PlayerTestTags.NARROW_GRID_BUTTON).performClick()
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithTag(PlayerTestTags.gridCell(item2Id.toString())).performClick()
        composeRule.waitForIdle()
        val originalSessionId = activeSession.get()
        assertNotNull(originalSessionId)

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        val coldOverrides = PlayerRouteOverrides(
            attemptApi = fakeApi,
            store = AccountScopedStore(storeRoot, "acc-death"),
            accountId = "acc-death",
            connectivity = connectivity,
        )
        val coldNavRef = AtomicReference<AppNavigator?>(null)
        composeRule.setContent {
            val navController = rememberNavController()
            val appNav = AppNavigator(navController)
            coldNavRef.set(appNav)
            CompositionLocalProvider(LocalPlayerRouteOverrides provides coldOverrides) {
                Thi360App(
                    preferences = AppPreferences(locale = AppLocale.VI),
                    navController = navController,
                    navigator = appNav,
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PlayerTestTags.SCREEN).assertDoesNotExist()

        composeRule.runOnIdle { coldNavRef.get()!!.openAttempt(attemptId.toString()) }
        composeRule.waitUntil(10_000L) {
            composeRule.onAllNodes(hasTestTag(PlayerTestTags.TOP_BAR)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(PlayerTestTags.TAKEOVER_DIALOG).assertDoesNotExist()
        if (composeRule.onAllNodes(hasTestTag(PlayerTestTags.NARROW_GRID_BUTTON)).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag(PlayerTestTags.NARROW_GRID_BUTTON).performClick()
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithTag(PlayerTestTags.gridCell(item2Id.toString())).assertIsSelected()
        assertEquals(originalSessionId, activeSession.get())
    }
}
