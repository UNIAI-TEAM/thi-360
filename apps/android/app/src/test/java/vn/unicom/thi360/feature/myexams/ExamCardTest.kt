package vn.unicom.thi360.feature.myexams

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import java.util.UUID
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.config.SupportContact
import vn.unicom.thi360.core.network.generated.model.IntRange as ApiIntRange
import vn.unicom.thi360.core.network.generated.model.LocalizedName
import vn.unicom.thi360.core.network.generated.model.MyExamCard
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.core.ui.LoadState

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class ExamCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun content() {
        val classCard = MyExamsTestFixtures.makeCard(
            code = "CARD-CLASS",
            name = "Đề Toán 12A1",
            entitlementSource = ENTITLEMENT_SOURCE_CLASS,
            className = "12A1",
            state = MyExamCard.State.CAN_START,
            format = "SUBJECT",
            scopeLabel = LocalizedName(nameVi = "Toán", nameEn = ""),
            questionCount = ApiIntRange(min = 50, max = 50),
            durationMinutes = ApiIntRange(min = 90, max = 90),
            attemptsLeft = 2,
            coverUrl = "/api/v1/public/storefront/covers/ignored",
            actions = listOf(MyExamCard.Actions.START),
        )
        val reviewCard = MyExamsTestFixtures.makeCard(
            code = "CARD-REVIEW",
            name = "Đề ôn tập",
            entitlementSource = "PURCHASE",
            buildMode = BUILD_MODE_REVIEW,
            state = MyExamCard.State.CAN_START,
            format = "CUSTOM_FORMAT",
            questionCount = ApiIntRange(min = 40, max = 60),
            durationMinutes = null,
            attemptsLeft = 3,
            actions = listOf(MyExamCard.Actions.START),
        )
        val classNoNameCard = MyExamsTestFixtures.makeCard(
            code = "CARD-CLASS-NONAME",
            name = "Đề lớp ẩn tên",
            entitlementSource = ENTITLEMENT_SOURCE_CLASS,
            className = "   ",
            state = MyExamCard.State.NONE,
            format = "FULL",
            questionCount = null,
            durationMinutes = null,
            attemptsLeft = 1,
            actions = listOf(MyExamCard.Actions.START),
        )

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                MyExamsScreen(
                    feedState = MyExamsFeedState(
                        loadState = LoadState.Loaded(listOf(classCard, reviewCard, classNoNameCard)),
                    ),
                    greetingName = "An",
                    supportContact = SupportContact(),
                    showCompactBrandHeader = false,
                    onPullToRefresh = {},
                    onRetry = {},
                    onExploreExams = {},
                    onOpenOrder = {},
                    onOpenProduct = {},
                    onContinueAttempt = {},
                    onStartPrepare = {},
                    onOpenResult = {},
                    onOpenHistoryReset = {},
                    onOpenCheckout = {},
                )
            }
        }

        composeRule.onNodeWithText("Bài theo lớp · 12A1").assertIsDisplayed()
        composeRule.onAllNodesWithText("Có thể bắt đầu").assertCountEquals(2).onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("Bài thi theo môn").assertIsDisplayed()
        composeRule.onNodeWithText("Toán").assertIsDisplayed()
        composeRule.onNodeWithText("50 câu · 90 phút mỗi lượt").assertIsDisplayed()
        composeRule.onNodeWithText("2").assertIsDisplayed()

        composeRule.onNodeWithTag(MyExamsTestTags.LIST)
            .performScrollToNode(hasTestTag(MyExamsTestTags.examCard("CARD-REVIEW")))
        composeRule.onNodeWithText("Đã mua").assertIsDisplayed()
        composeRule.onNodeWithText("Bài ôn").assertIsDisplayed()
        composeRule.onNodeWithText("CUSTOM_FORMAT").assertIsDisplayed()
        composeRule.onNodeWithText("40–60 câu").assertIsDisplayed()

        composeRule.onNodeWithTag(MyExamsTestTags.LIST)
            .performScrollToNode(hasTestTag(MyExamsTestTags.examCard("CARD-CLASS-NONAME")))
        composeRule.onNodeWithText("Bài theo lớp").assertIsDisplayed()
        composeRule.onNodeWithText("NONE").assertIsDisplayed()
    }

    @Test
    fun englishScopeLabelFallsBackToVietnameseWhenNameEnBlank() {
        val card = MyExamsTestFixtures.makeCard(
            code = "CARD-EN-FALLBACK",
            name = "Math Exam",
            format = "SUBJECT",
            scopeLabel = LocalizedName(nameVi = "Toán nâng cao", nameEn = ""),
            questionCount = ApiIntRange(min = 40, max = 60),
            durationMinutes = null,
            actions = listOf(MyExamCard.Actions.START),
        )

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.EN)) {
                MyExamsScreen(
                    feedState = MyExamsFeedState(loadState = LoadState.Loaded(listOf(card))),
                    greetingName = "An",
                    supportContact = SupportContact(),
                    showCompactBrandHeader = false,
                    onPullToRefresh = {},
                    onRetry = {},
                    onExploreExams = {},
                    onOpenOrder = {},
                    onOpenProduct = {},
                    onContinueAttempt = {},
                    onStartPrepare = {},
                    onOpenResult = {},
                    onOpenHistoryReset = {},
                    onOpenCheckout = {},
                )
            }
        }

        composeRule.onNodeWithText("Toán nâng cao").assertIsDisplayed()
        composeRule.onNodeWithText("40–60 questions").assertIsDisplayed()
    }

    @Test
    fun notesPlacement() {
        val classExhausted = MyExamsTestFixtures.makeCard(
            code = "CLS-EXH",
            name = "Đề lớp hết lượt",
            entitlementSource = ENTITLEMENT_SOURCE_CLASS,
            state = MyExamCard.State.EXHAUSTED,
            attemptsLeft = 0,
            actions = listOf(MyExamCard.Actions.HISTORY, MyExamCard.Actions.REBUY),
        )
        val unavailableStart = MyExamsTestFixtures.makeCard(
            code = "UNAVAIL-1",
            name = "Đề ngừng bán còn lượt",
            saleState = MyExamCard.SaleState.UNAVAILABLE,
            state = MyExamCard.State.CAN_START,
            attemptsLeft = 1,
            actions = listOf(MyExamCard.Actions.START),
        )
        val lastAttemptWithTopUp = MyExamsTestFixtures.makeCard(
            code = "LAST-1",
            name = "Đề đang làm lượt cuối",
            state = MyExamCard.State.IN_PROGRESS,
            attemptsLeft = 0,
            activeAttemptId = UUID.randomUUID(),
            hasPendingFulfillment = true,
            pendingOrderCode = "DH002",
            actions = listOf(
                MyExamCard.Actions.CONTINUE,
                MyExamCard.Actions.HISTORY,
                MyExamCard.Actions.BUY_MORE,
            ),
        )

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                MyExamsScreen(
                    feedState = MyExamsFeedState(
                        loadState = LoadState.Loaded(
                            listOf(classExhausted, unavailableStart, lastAttemptWithTopUp),
                        ),
                    ),
                    greetingName = "An",
                    supportContact = SupportContact(),
                    showCompactBrandHeader = false,
                    onPullToRefresh = {},
                    onRetry = {},
                    onExploreExams = {},
                    onOpenOrder = {},
                    onOpenProduct = {},
                    onContinueAttempt = {},
                    onStartPrepare = {},
                    onOpenResult = {},
                    onOpenHistoryReset = {},
                    onOpenCheckout = {},
                )
            }
        }

        composeRule.onNodeWithText(
            "Đã hết lượt được giao cho bài thi này. Kết quả và lịch sử vẫn xem được.",
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(MyExamsTestTags.examCardHistory("CLS-EXH")).assertIsDisplayed()
        composeRule.onNodeWithTag(MyExamsTestTags.examCardRebuy("CLS-EXH")).assertDoesNotExist()

        composeRule.onNodeWithTag(MyExamsTestTags.LIST)
            .performScrollToNode(hasTestTag(MyExamsTestTags.examCard("UNAVAIL-1")))
        composeRule.onNodeWithTag(MyExamsTestTags.examCardStart("UNAVAIL-1")).assertIsDisplayed()
        composeRule.onNodeWithText(
            "Bài thi đã ngừng bán. Bạn vẫn dùng được các lượt còn lại.",
        ).assertIsDisplayed()

        composeRule.onNodeWithTag(MyExamsTestTags.LIST)
            .performScrollToNode(hasTestTag(MyExamsTestTags.examCard("LAST-1")))
        val continueBounds = composeRule.onNodeWithTag(MyExamsTestTags.examCardContinue("LAST-1"))
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val lastAttemptNoteBounds = composeRule
            .onNodeWithTag(MyExamsTestTags.examCardMiddleNote("LAST-1", CardNote.LAST_ATTEMPT))
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val historyBounds = composeRule.onNodeWithTag(MyExamsTestTags.examCardHistory("LAST-1"))
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val pendingTopUpBounds = composeRule
            .onNodeWithTag(MyExamsTestTags.examCardPendingTopUpNote("LAST-1"))
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()

        composeRule.onNodeWithText(
            "Đơn mua thêm lượt DH002 đang được kiểm tra. Bạn không cần thanh toán lại.",
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(MyExamsTestTags.examCardBuyMore("LAST-1")).assertDoesNotExist()

        assertTrue(
            "Last-attempt note must sit below Continue and above History row",
            continueBounds.bottom <= lastAttemptNoteBounds.top &&
                lastAttemptNoteBounds.bottom <= historyBounds.top,
        )
        assertTrue(
            "Pending top-up note must sit below History row",
            historyBounds.bottom <= pendingTopUpBounds.top,
        )
    }
}
