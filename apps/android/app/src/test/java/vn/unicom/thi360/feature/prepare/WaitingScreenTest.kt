package vn.unicom.thi360.feature.prepare

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import java.io.File
import java.nio.file.Files
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import vn.unicom.thi360.core.config.SupportContact
import vn.unicom.thi360.core.network.ApiError
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.generated.model.AttemptPrepareResponse
import vn.unicom.thi360.core.network.generated.model.AttemptStateResponse
import vn.unicom.thi360.core.network.generated.model.StartAttemptRequest
import vn.unicom.thi360.core.network.generated.model.StartAttemptResponse
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.storage.AccountScopedStoreProvider
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.prepare.waiting.AttemptWaitingContent
import vn.unicom.thi360.feature.prepare.waiting.AttemptWaitingScreen

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class WaitingScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val supportContact = SupportContact(
        phone = "1900 1234",
        email = "hotro@thi360.vn",
        hours = "08:00-21:00",
    )

    @Test
    fun content() {
        var phase by mutableStateOf<WaitingPhase>(WaitingPhase.Polling(1_000L))
        var shared by mutableStateOf(false)
        var myExamsClicks = 0

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                AttemptWaitingContent(
                    examTitle = "HSA đầy đủ (thu nhỏ)",
                    phase = phase,
                    abortReason = null,
                    firstPartName = "Toán học và xử lí số liệu",
                    firstPartMinutes = if (shared) 90 else 5,
                    isSharedTiming = shared,
                    supportContact = supportContact,
                    onBack = {},
                    onMyExams = { myExamsClicks++ },
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithTag(PrepareTestTags.WAITING_TITLE_BAR)
            .assertIsDisplayed()
            .assertTextContains("HSA đầy đủ (thu nhỏ)")
        composeRule.onNodeWithTag(PrepareTestTags.WAITING_CLOCK_PAUSED)
            .assertIsDisplayed()
            .assertTextContains("Chưa tính giờ")
        composeRule.onNodeWithTag(PrepareTestTags.WAITING_POLLING_SECTION)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(PrepareTestTags.WAITING_FIRST_PART)
            .assertTextContains("Toán học và xử lí số liệu")
        composeRule.onNodeWithTag(PrepareTestTags.WAITING_DURATION_LABEL)
            .assertTextContains("Thời gian của phần")
        composeRule.onNodeWithTag(PrepareTestTags.WAITING_DURATION_VALUE)
            .assertTextContains("5 phút")
        composeRule.onNodeWithTag(PrepareTestTags.WAITING_REASSURE_NOTE)
            .assertIsDisplayed()
            .assertTextContains("Bài thi của tôi", substring = true)

        val reassureText = composeRule.onNodeWithTag(PrepareTestTags.WAITING_REASSURE_NOTE)
            .fetchSemanticsNode()
            .config
            .toString()
        assertFalse("A-87 app copy must not mention web 'trang'", reassureText.contains("trang này"))

        composeRule.onNodeWithTag(PrepareTestTags.WAITING_REASSURE_NOTE).performClick()
        assertTrue(myExamsClicks == 1)

        composeRule.runOnIdle { shared = true }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PrepareTestTags.WAITING_DURATION_LABEL)
            .assertTextContains("Thời gian làm bài")
        composeRule.onNodeWithTag(PrepareTestTags.WAITING_DURATION_VALUE)
            .assertTextContains("90 phút")

        composeRule.runOnIdle { phase = WaitingPhase.Busy }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PrepareTestTags.WAITING_BUSY_SECTION).assertIsDisplayed()
        composeRule.onNodeWithTag(PrepareTestTags.WAITING_BUSY_NOTICE)
            .assertTextContains("Sau 90 giây vẫn chưa xong")
        composeRule.onNodeWithTag(PrepareTestTags.WAITING_BUSY_TITLE)
            .assertTextContains("Hệ thống đang bận")
        composeRule.onNodeWithTag(PrepareTestTags.WAITING_BUSY_DESCRIPTION)
            .assertTextContains("Ứng dụng tự kiểm tra lại", substring = true)
        val busyDescText = composeRule.onNodeWithTag(PrepareTestTags.WAITING_BUSY_DESCRIPTION)
            .fetchSemanticsNode()
            .config
            .toString()
        assertFalse("A-87 busy description must not mention web 'Trang'", busyDescText.contains("Trang tự kiểm tra"))
        composeRule.onNodeWithTag(PrepareTestTags.WAITING_BUSY_BACK_BUTTON).performClick()
        assertTrue(myExamsClicks == 2)
    }

    @Test
    fun failedVariants() {
        var phase by mutableStateOf<WaitingPhase>(WaitingPhase.Failed("STALE_STARTING"))
        var abortReason by mutableStateOf<String?>("STALE_STARTING")
        var retryClicks = 0
        var myExamsClicks = 0

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                AttemptWaitingContent(
                    examTitle = "Đề thi thử",
                    phase = phase,
                    abortReason = abortReason,
                    firstPartName = null,
                    firstPartMinutes = null,
                    isSharedTiming = false,
                    supportContact = supportContact,
                    onBack = {},
                    onMyExams = { myExamsClicks++ },
                    onRetry = { retryClicks++ },
                )
            }
        }

        val expectedReasons = listOf(
            "STALE_STARTING" to "Việc chuẩn bị đề bị gián đoạn.",
            "GENERATION_FAILED" to "Chưa tạo được bộ câu hỏi cho đề này.",
            "GENERATION_TIMEOUT" to "Tạo đề quá thời gian cho phép.",
            "GENERATION_BUSY" to "Hệ thống đang có nhiều yêu cầu tạo đề. Vui lòng thử lại sau giây lát.",
            "AUDIO_ASSET_MISSING" to "Thiếu tệp âm thanh cần thiết cho đề thi.",
            "AUDIO_ASSET_INVALID" to "Tệp âm thanh của đề thi không hợp lệ hoặc không đọc được.",
            "UNKNOWN_REASON" to "Không thể chuẩn bị đề thi lúc này.",
            null to "Không thể chuẩn bị đề thi lúc này.",
        )

        expectedReasons.forEach { (code, expectedMessage) ->
            composeRule.runOnIdle {
                abortReason = code
                phase = WaitingPhase.Failed(code)
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(PrepareTestTags.WAITING_FAILED_NOTICE)
                .assertTextContains("Chuẩn bị đề thất bại")
            composeRule.onNodeWithTag(PrepareTestTags.WAITING_FAILED_HEADING)
                .assertTextContains("Chưa chuẩn bị được đề")
            composeRule.onNodeWithTag(PrepareTestTags.WAITING_FAILED_REASON)
                .assertTextContains(expectedMessage)
            composeRule.onNodeWithTag(PrepareTestTags.WAITING_FAILED_REFUND_NOTE)
                .assertTextContains("Lượt đã được trả lại, bạn chưa mất lượt nào. Đồng hồ chưa chạy.")
        }

        composeRule.onNodeWithTag(PrepareTestTags.WAITING_FAILED_BACK_BUTTON).performClick()
        composeRule.onNodeWithTag(PrepareTestTags.WAITING_RETRY_BUTTON).performClick()
        assertTrue(myExamsClicks == 1)
        assertTrue(retryClicks == 1)

        composeRule.runOnIdle {
            phase = WaitingPhase.RetryRefused(StartRefusal.NoServingOption)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PrepareTestTags.WAITING_RETRY_ERROR)
            .assertIsDisplayed()
            .assertTextContains("1900 1234", substring = true)
    }

    @Test
    fun playerOriginAttemptWaitingScreenHandlesRestartOpenPrepareAndMissingContext() {
        val tempRoot: File = Files.createTempDirectory("thi360-waiting-screen-").toFile()
        try {
            val provider = AccountScopedStoreProvider(tempRoot) { "acc-player" }
            val store = StartContextStore(provider)
            val entitlementId = "00000000-0000-7000-8000-000000000001"
            val oldAttemptId = UUID.fromString("00000000-0000-7000-8000-000000000401")
            val newAttemptId = UUID.fromString("00000000-0000-7000-8000-000000000402")
            val missingAttemptId = UUID.fromString("00000000-0000-7000-8000-000000000403")
            val ctx = StartContext(
                entitlementId = entitlementId,
                preChoice = null,
                examTitle = "HSA đầy đủ",
                examKindLabel = "HSA",
                firstPartName = "Phần 1",
                firstPartMinutes = 60,
                timing = "PER_PART",
            )
            store.save(oldAttemptId, ctx)

            var callIdx = 0
            val fakeApi = object : PrepareApi {
                override suspend fun prepare(entitlementId: UUID): ApiResult<AttemptPrepareResponse> =
                    error("unused")

                override suspend fun start(
                    idempotencyKey: String,
                    request: StartAttemptRequest,
                ): ApiResult<StartAttemptResponse> {
                    callIdx++
                    return if (callIdx == 1) {
                        ApiResult.HttpFailure(422, ApiError("CHOICE_NOT_ALLOWED", "choice"))
                    } else {
                        ApiResult.Success(
                            PrepareTestFixtures.loadStart(202, "starting").copy(attemptId = newAttemptId),
                        )
                    }
                }

                override suspend fun getAttemptState(attemptId: UUID): ApiResult<AttemptStateResponse> =
                    ApiResult.Success(PrepareTestFixtures.loadAttemptState("starting"))
            }

            var currentAttemptId by mutableStateOf(oldAttemptId.toString())
            var openedPrepareEntId: String? = null
            var restartedAttemptId: String? = null
            var myExamsCalls = 0

            composeRule.setContent {
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                    AttemptWaitingScreen(
                        attemptId = currentAttemptId,
                        examTitle = ctx.examTitle,
                        context = null,
                        origin = WaitingOrigin.Player,
                        onReady = {},
                        onRestarted = { restartedAttemptId = it },
                        onOpenPrepare = { openedPrepareEntId = it },
                        onMyExams = { myExamsCalls++ },
                        initialState = AttemptStateResponse.State.ABORTED,
                        initialAbortReason = "STALE_STARTING",
                        supportContact = supportContact,
                        prepareApi = fakeApi,
                        startContextStore = store,
                    )
                }
            }

            composeRule.onNodeWithTag(PrepareTestTags.WAITING_RETRY_BUTTON).performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                ShadowLooper.idleMainLooper()
                openedPrepareEntId == entitlementId
            }

            composeRule.onNodeWithTag(PrepareTestTags.WAITING_RETRY_BUTTON).performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                ShadowLooper.idleMainLooper()
                restartedAttemptId == newAttemptId.toString()
            }
            assertNull(store.read(oldAttemptId))
            assertEquals(ctx, store.read(newAttemptId))

            composeRule.runOnIdle { currentAttemptId = missingAttemptId.toString() }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(PrepareTestTags.WAITING_RETRY_BUTTON).performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                ShadowLooper.idleMainLooper()
                myExamsCalls == 1
            }
        } finally {
            tempRoot.deleteRecursively()
        }
    }
}
