package vn.unicom.thi360.feature.result.review

import java.math.BigDecimal
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.unicom.thi360.core.network.generated.model.AttemptReviewEssayCriterionScore
import vn.unicom.thi360.core.network.generated.model.AttemptReviewEssayFeedback
import vn.unicom.thi360.core.network.generated.model.AttemptReviewExpectedAnswer
import vn.unicom.thi360.core.network.generated.model.AttemptReviewItem
import vn.unicom.thi360.core.network.generated.model.AttemptReviewMatchingKey
import vn.unicom.thi360.core.network.generated.model.AttemptReviewMatchingSide
import vn.unicom.thi360.core.network.generated.model.AttemptReviewOption
import vn.unicom.thi360.core.network.generated.model.AttemptReviewPart
import vn.unicom.thi360.core.network.generated.model.AttemptReviewQuestionBody
import vn.unicom.thi360.core.network.generated.model.AttemptReviewResponse
import vn.unicom.thi360.core.network.generated.model.AttemptReviewStatement
import vn.unicom.thi360.core.network.generated.model.AttemptReviewTrueFalseKey
import vn.unicom.thi360.core.network.generated.model.EssayAnswer
import vn.unicom.thi360.core.network.generated.model.MatchingAnswer
import vn.unicom.thi360.core.network.generated.model.ReviewGradingDetail
import vn.unicom.thi360.core.network.generated.model.ReviewMatchingPairResult
import vn.unicom.thi360.core.network.generated.model.ReviewStatementResult
import vn.unicom.thi360.core.network.generated.model.SingleChoiceAnswer
import vn.unicom.thi360.core.network.generated.model.TrueFalseAnswer
import vn.unicom.thi360.feature.question.model.ItemOutcome
import vn.unicom.thi360.feature.question.model.ItemReviewKey
import vn.unicom.thi360.feature.question.model.QuestionItem
import vn.unicom.thi360.feature.question.model.ReviewLevel
import vn.unicom.thi360.feature.result.ResultTestFixtures

class ReviewMapperTest {

    @Test
    fun mapsCertifiedReviewFixturesIncludingQuestionSetAndParts() {
        val full = ReviewMapper.map(ResultTestFixtures.loadReview("full-solution"))
        assertEquals(ReviewLevel.FULL_SOLUTION, full.level)
        assertEquals(1, full.parts.size)
        val part = full.parts.first()
        assertEquals(3, part.groups.size)
        val setGroup = part.groups.last()
        val setItem = setGroup.item as QuestionItem.QuestionSet
        assertEquals(3, setItem.children.size)
        assertEquals(3, setGroup.rangeFrom)
        assertEquals(5, setGroup.rangeTo)
        assertEquals(5, part.summary.correct + part.summary.partial + part.summary.wrong + part.summary.blank)

        val scoreAndAnswers = ReviewMapper.map(ResultTestFixtures.loadReview("score-and-answers"))
        assertEquals(ReviewLevel.SCORE_AND_ANSWERS, scoreAndAnswers.level)
        assertEquals(1, scoreAndAnswers.parts.first().summary.correct)
        assertEquals(4, scoreAndAnswers.parts.first().summary.blank)
    }

    @Test
    fun mapsSingleChoiceMatchingTrueFalseAndMissingPointsDefaults() {
        val partId = UUID.randomUUID()
        val scId = UUID.randomUUID()
        val matchId = UUID.randomUUID()
        val tfId = UUID.randomUUID()

        val response = AttemptReviewResponse(
            attemptId = UUID.fromString(ResultTestFixtures.ATTEMPT_UUID_1),
            state = AttemptReviewResponse.State.SCORED,
            resultView = "FULL_SOLUTION",
            parts = listOf(
                AttemptReviewPart(
                    partId = partId,
                    partCode = "P1",
                    partName = "Phần 1",
                    ord = 0,
                    state = "CLOSED",
                ),
            ),
            items = listOf(
                AttemptReviewItem(
                    itemId = scId,
                    partId = partId,
                    partCode = "P1",
                    displayNo = 1,
                    ord = 1,
                    questionType = AttemptReviewItem.QuestionType.SINGLE_CHOICE,
                    content = "<p>Câu 1</p>",
                    body = AttemptReviewQuestionBody(
                        options = listOf(
                            AttemptReviewOption(id = "a", content = "<p>A</p>"),
                            AttemptReviewOption(id = "b", content = "<p>B</p>"),
                        ),
                    ),
                    studentAnswer = SingleChoiceAnswer(optionId = "a"),
                    optionOrder = listOf("b", "a"),
                    pointsMax = null,
                    earnedPoints = null,
                    outcome = AttemptReviewItem.Outcome.WRONG,
                    expectedAnswer = AttemptReviewExpectedAnswer(expectedOptionIds = listOf("b")),
                ),
                AttemptReviewItem(
                    itemId = matchId,
                    partId = partId,
                    partCode = "P1",
                    displayNo = 2,
                    ord = 2,
                    questionType = AttemptReviewItem.QuestionType.MATCHING,
                    content = "<p>Ghép đôi</p>",
                    body = AttemptReviewQuestionBody(
                        left = listOf(
                            AttemptReviewMatchingSide(id = "L1", content = "<p>Left 1</p>"),
                            AttemptReviewMatchingSide(id = "L2", content = "<p>Left 2</p>"),
                            AttemptReviewMatchingSide(id = "L3", content = "<p>Left 3</p>"),
                        ),
                        right = listOf(
                            AttemptReviewMatchingSide(id = "R1", content = "<p>Right 1</p>"),
                            AttemptReviewMatchingSide(id = "R2", content = "<p>Right 2</p>"),
                            AttemptReviewMatchingSide(id = "R3", content = "<p>Right 3</p>"),
                        ),
                    ),
                    studentAnswer = MatchingAnswer(pairs = mapOf("L1" to "R1", "L2" to "R3")),
                    pointsMax = BigDecimal("3.0"),
                    earnedPoints = BigDecimal("1.0"),
                    outcome = AttemptReviewItem.Outcome.PARTIAL,
                    gradingDetail = ReviewGradingDetail(
                        pairResults = listOf(
                            ReviewMatchingPairResult(leftId = "L1", matched = true, pointsEarned = BigDecimal("1.0")),
                            ReviewMatchingPairResult(leftId = "L2", matched = false, pointsEarned = BigDecimal.ZERO),
                            ReviewMatchingPairResult(leftId = "L3", matched = false, pointsEarned = BigDecimal.ZERO),
                        ),
                    ),
                    expectedAnswer = AttemptReviewExpectedAnswer(
                        matchingKeys = listOf(
                            AttemptReviewMatchingKey(leftId = "L1", rightId = "R1"),
                            AttemptReviewMatchingKey(leftId = "L2", rightId = "R2"),
                            AttemptReviewMatchingKey(leftId = "L3", rightId = "R3"),
                        ),
                    ),
                ),
                AttemptReviewItem(
                    itemId = tfId,
                    partId = partId,
                    partCode = "P1",
                    displayNo = 3,
                    ord = 3,
                    questionType = AttemptReviewItem.QuestionType.TRUE_FALSE,
                    content = "<p>Đúng sai</p>",
                    body = AttemptReviewQuestionBody(
                        statements = listOf(
                            AttemptReviewStatement(id = "S1", content = "<p>Ý 1</p>"),
                            AttemptReviewStatement(id = "S2", content = "<p>Ý 2</p>"),
                            AttemptReviewStatement(id = "S3", content = "<p>Ý 3</p>"),
                        ),
                    ),
                    studentAnswer = TrueFalseAnswer(statements = mapOf("S1" to true, "S2" to false)),
                    pointsMax = BigDecimal("1.0"),
                    earnedPoints = BigDecimal("0.25"),
                    outcome = AttemptReviewItem.Outcome.PARTIAL,
                    gradingDetail = ReviewGradingDetail(
                        statementResults = listOf(
                            ReviewStatementResult(id = "S1", answered = true, matched = true),
                            ReviewStatementResult(id = "S2", answered = true, matched = false),
                            ReviewStatementResult(id = "S3", answered = false, matched = false),
                        ),
                    ),
                    expectedAnswer = AttemptReviewExpectedAnswer(
                        trueFalseKeys = listOf(
                            AttemptReviewTrueFalseKey(statementId = "S1", expected = true),
                            AttemptReviewTrueFalseKey(statementId = "S2", expected = true),
                            AttemptReviewTrueFalseKey(statementId = "S3", expected = false),
                        ),
                    ),
                ),
            ),
        )

        val mapped = ReviewMapper.map(response)
        val groups = mapped.parts.first().groups
        assertEquals(3, groups.size)

        val scGroup = groups[0]
        val scItem = scGroup.item as QuestionItem.SingleChoice
        assertEquals(listOf("b", "a"), scItem.optionOrder)
        assertEquals(ItemOutcome.WRONG, scGroup.outcome)
        assertNull(scGroup.mode.review.earned)
        assertEquals(BigDecimal.ONE, scGroup.mode.review.max)
        assertEquals(ItemReviewKey.Choice(setOf("b")), scGroup.mode.review.key)

        val matchGroup = groups[1]
        val pairDetails = matchGroup.mode.review.detail
        assertEquals(ItemOutcome.CORRECT, pairDetails["L1"]?.outcome)
        assertEquals(ItemOutcome.WRONG, pairDetails["L2"]?.outcome)
        assertEquals(ItemOutcome.BLANK, pairDetails["L3"]?.outcome)
        assertEquals(0, BigDecimal.ONE.compareTo(pairDetails["L1"]?.max))
        assertEquals(0, BigDecimal.ONE.compareTo(pairDetails["L2"]?.max))

        val tfGroup = groups[2]
        val stmtDetails = tfGroup.mode.review.detail
        assertEquals(ItemOutcome.CORRECT, stmtDetails["S1"]?.outcome)
        assertEquals(ItemOutcome.WRONG, stmtDetails["S2"]?.outcome)
        assertEquals(ItemOutcome.BLANK, stmtDetails["S3"]?.outcome)
    }

    @Test
    fun setOutcome() {
        val cCorrect = childItem(AttemptReviewItem.Outcome.CORRECT)
        val cWrong = childItem(AttemptReviewItem.Outcome.WRONG)
        val cBlank = childItem(AttemptReviewItem.Outcome.BLANK)

        assertEquals(ItemOutcome.CORRECT, ReviewMapper.deriveGroupOutcome(listOf(cCorrect, cCorrect)))
        assertEquals(ItemOutcome.BLANK, ReviewMapper.deriveGroupOutcome(listOf(cBlank, cBlank)))
        assertEquals(ItemOutcome.WRONG, ReviewMapper.deriveGroupOutcome(listOf(cWrong, cWrong)))
        assertEquals(ItemOutcome.PARTIAL, ReviewMapper.deriveGroupOutcome(listOf(cCorrect, cWrong)))
        assertEquals(ItemOutcome.PARTIAL, ReviewMapper.deriveGroupOutcome(listOf(cCorrect, cBlank)))
        assertEquals(ItemOutcome.PARTIAL, ReviewMapper.deriveGroupOutcome(listOf(cWrong, cBlank)))

        val partId = UUID.randomUUID()
        val setId = UUID.randomUUID()
        val response = AttemptReviewResponse(
            attemptId = UUID.fromString(ResultTestFixtures.ATTEMPT_UUID_1),
            state = AttemptReviewResponse.State.SCORED,
            resultView = "FULL_SOLUTION",
            parts = listOf(AttemptReviewPart(partId = partId, partCode = "P1", ord = 0, state = "CLOSED")),
            items = listOf(
                AttemptReviewItem(
                    itemId = setId,
                    partId = partId,
                    partCode = "P1",
                    ord = 1,
                    questionType = AttemptReviewItem.QuestionType.QUESTION_SET,
                    content = "<p>Set</p>",
                    body = AttemptReviewQuestionBody(passage = "<p>Passage</p>"),
                    outcome = AttemptReviewItem.Outcome.PENDING_REVIEW,
                ),
                cCorrect.copy(itemId = UUID.randomUUID(), partId = partId, partCode = "P1", parentItemId = setId, displayNo = 1, ord = 2),
                cWrong.copy(itemId = UUID.randomUUID(), partId = partId, partCode = "P1", parentItemId = setId, displayNo = 2, ord = 3),
            ),
        )
        val mapped = ReviewMapper.map(response)
        assertEquals(ItemOutcome.PARTIAL, mapped.parts.first().groups.single().outcome)
    }

    @Test
    fun pendingReviewFiltered() {
        val partId = UUID.randomUUID()
        val allPendingSetId = UUID.randomUUID()
        val mixedSetId = UUID.randomUUID()
        val gradedChildId = UUID.randomUUID()
        val pendingChildId = UUID.randomUUID()
        val pendingEssayId = UUID.randomUUID()
        val gradedMcqId = UUID.randomUUID()

        val response = AttemptReviewResponse(
            attemptId = UUID.fromString(ResultTestFixtures.ATTEMPT_UUID_1),
            state = AttemptReviewResponse.State.PENDING_REVIEW,
            resultView = "FULL_SOLUTION",
            parts = listOf(
                AttemptReviewPart(partId = partId, partCode = "P1", partName = "Phần 1", ord = 0, state = "CLOSED"),
            ),
            items = listOf(
                AttemptReviewItem(
                    itemId = pendingEssayId,
                    partId = partId,
                    partCode = "P1",
                    displayNo = 1,
                    ord = 1,
                    questionType = AttemptReviewItem.QuestionType.ESSAY,
                    content = "<p>Tự luận chờ chấm</p>",
                    studentAnswer = EssayAnswer(html = "<p>Bài làm</p>", wordCount = 2),
                    outcome = AttemptReviewItem.Outcome.PENDING_REVIEW,
                    essayFeedback = AttemptReviewEssayFeedback(
                        comment = "Should not show",
                        criteria = listOf(AttemptReviewEssayCriterionScore(id = "C1", points = BigDecimal.ONE)),
                    ),
                ),
                childItem(AttemptReviewItem.Outcome.CORRECT).copy(
                    itemId = gradedMcqId,
                    partId = partId,
                    partCode = "P1",
                    displayNo = 2,
                    ord = 2,
                ),
                AttemptReviewItem(
                    itemId = allPendingSetId,
                    partId = partId,
                    partCode = "P1",
                    ord = 3,
                    questionType = AttemptReviewItem.QuestionType.QUESTION_SET,
                    content = "<p>Chùm chờ chấm hoàn toàn</p>",
                    body = AttemptReviewQuestionBody(passage = "<p>Đoạn văn ẩn</p>"),
                    outcome = AttemptReviewItem.Outcome.PENDING_REVIEW,
                ),
                AttemptReviewItem(
                    itemId = UUID.randomUUID(),
                    partId = partId,
                    partCode = "P1",
                    parentItemId = allPendingSetId,
                    displayNo = 3,
                    ord = 4,
                    questionType = AttemptReviewItem.QuestionType.ESSAY,
                    content = "<p>Con chờ chấm 1</p>",
                    outcome = AttemptReviewItem.Outcome.PENDING_REVIEW,
                ),
                AttemptReviewItem(
                    itemId = mixedSetId,
                    partId = partId,
                    partCode = "P1",
                    ord = 5,
                    questionType = AttemptReviewItem.QuestionType.QUESTION_SET,
                    content = "<p>Chùm hỗn hợp</p>",
                    body = AttemptReviewQuestionBody(passage = "<p>Đoạn văn giữ lại</p>"),
                    outcome = AttemptReviewItem.Outcome.PENDING_REVIEW,
                ),
                childItem(AttemptReviewItem.Outcome.WRONG).copy(
                    itemId = gradedChildId,
                    partId = partId,
                    partCode = "P1",
                    parentItemId = mixedSetId,
                    displayNo = 4,
                    ord = 6,
                ),
                AttemptReviewItem(
                    itemId = pendingChildId,
                    partId = partId,
                    partCode = "P1",
                    parentItemId = mixedSetId,
                    displayNo = 5,
                    ord = 7,
                    questionType = AttemptReviewItem.QuestionType.ESSAY,
                    content = "<p>Con tự luận chờ chấm</p>",
                    outcome = AttemptReviewItem.Outcome.PENDING_REVIEW,
                ),
            ),
        )

        val mapped = ReviewMapper.map(response)
        val groups = mapped.parts.first().groups
        assertEquals(2, groups.size)
        assertEquals(gradedMcqId.toString(), groups[0].id)
        assertEquals(mixedSetId.toString(), groups[1].id)

        val mixedSet = groups[1].item as QuestionItem.QuestionSet
        assertEquals(1, mixedSet.children.size)
        assertEquals(gradedChildId.toString(), mixedSet.children.single().id)
        assertEquals(ItemOutcome.WRONG, groups[1].outcome)

        assertFalse(groups.any { it.id == pendingEssayId.toString() })
        assertFalse(groups.any { it.id == allPendingSetId.toString() })

        val summary = mapped.parts.first().summary
        assertEquals(1, summary.correct)
        assertEquals(1, summary.wrong)
        assertEquals(0, summary.blank)
        assertEquals(0, summary.partial)

        val certifiedPending = ReviewMapper.map(ResultTestFixtures.loadReview("pending-review"))
        assertTrue(certifiedPending.parts.flatMap { it.groups }.none { it.outcome == ItemOutcome.PENDING_REVIEW })
    }

    private fun childItem(outcome: AttemptReviewItem.Outcome): AttemptReviewItem = AttemptReviewItem(
        itemId = UUID.randomUUID(),
        displayNo = 1,
        ord = 1,
        questionType = AttemptReviewItem.QuestionType.SINGLE_CHOICE,
        content = "<p>Câu hỏi</p>",
        body = AttemptReviewQuestionBody(
            options = listOf(
                AttemptReviewOption(id = "a", content = "<p>A</p>"),
                AttemptReviewOption(id = "b", content = "<p>B</p>"),
            ),
        ),
        pointsMax = BigDecimal.ONE,
        earnedPoints = if (outcome == AttemptReviewItem.Outcome.CORRECT) BigDecimal.ONE else BigDecimal.ZERO,
        outcome = outcome,
        expectedAnswer = AttemptReviewExpectedAnswer(expectedOptionIds = listOf("a")),
    )
}
