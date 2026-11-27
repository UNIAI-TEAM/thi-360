package vn.unicom.thi360.feature.question

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.unicom.thi360.core.network.generated.infrastructure.Serializer
import vn.unicom.thi360.core.network.generated.model.AnswerValue
import vn.unicom.thi360.core.network.generated.model.AttemptReviewEssayCriterionScore
import vn.unicom.thi360.core.network.generated.model.AttemptReviewEssayFeedback
import vn.unicom.thi360.core.network.generated.model.AttemptReviewItem
import vn.unicom.thi360.core.network.generated.model.AttemptReviewResponse
import vn.unicom.thi360.core.network.generated.model.AttemptStateResponse
import vn.unicom.thi360.core.network.generated.model.ClozeAnswer
import vn.unicom.thi360.core.network.generated.model.EssayAnswer
import vn.unicom.thi360.core.network.generated.model.MatchingAnswer
import vn.unicom.thi360.core.network.generated.model.MultipleChoiceAnswer
import vn.unicom.thi360.core.network.generated.model.ReviewGradingDetail
import vn.unicom.thi360.core.network.generated.model.ReviewMatchingPairResult
import vn.unicom.thi360.core.network.generated.model.ShortAnswerValue
import vn.unicom.thi360.core.network.generated.model.SingleChoiceAnswer
import vn.unicom.thi360.core.network.generated.model.TrueFalseAnswer
import vn.unicom.thi360.feature.question.logic.AnswerValues
import vn.unicom.thi360.feature.question.mapper.ItemReviewMapper
import vn.unicom.thi360.feature.question.mapper.QuestionItemMapper
import vn.unicom.thi360.feature.question.model.ChoiceOptionItem
import vn.unicom.thi360.feature.question.model.ClozeBlankItem
import vn.unicom.thi360.feature.question.model.MatchingSideItem
import vn.unicom.thi360.feature.question.model.QuestionItem
import vn.unicom.thi360.feature.question.model.ReviewLevel
import vn.unicom.thi360.feature.question.model.TrueFalseStatementItem

class AnswerValueJsonTest {

    @Test
    fun encodesAllSevenAnswerValueTypesMatchingWireContract() {
        val scNull = SingleChoiceAnswer(optionId = null)
        assertEquals("""{"type":"SINGLE_CHOICE"}""", AnswerValues.encodeToJson(scNull))
        assertEquals(scNull, Serializer.kotlinxSerializationJson.decodeFromString(AnswerValue.serializer(), """{"type":"SINGLE_CHOICE"}"""))

        val scPicked = SingleChoiceAnswer(optionId = "s1")
        assertEquals("""{"type":"SINGLE_CHOICE","optionId":"s1"}""", AnswerValues.encodeToJson(scPicked))

        val mc = MultipleChoiceAnswer(optionIds = listOf("m3", "m1"))
        assertEquals("""{"type":"MULTIPLE_CHOICE","optionIds":["m3","m1"]}""", AnswerValues.encodeToJson(mc))
        assertEquals("""{"type":"MULTIPLE_CHOICE","optionIds":[]}""", AnswerValues.encodeToJson(MultipleChoiceAnswer(emptyList())))

        val sa = ShortAnswerValue(text = "  56 ")
        assertEquals("""{"type":"SHORT_ANSWER","text":"  56 "}""", AnswerValues.encodeToJson(sa))

        val cloze = ClozeAnswer(blanks = linkedMapOf("b2" to "0", "b1" to ""))
        assertEquals("""{"type":"CLOZE","blanks":{"b2":"0","b1":""}}""", AnswerValues.encodeToJson(cloze))

        val tf = TrueFalseAnswer(statements = linkedMapOf("t1" to true, "t2" to false))
        assertEquals("""{"type":"TRUE_FALSE","statements":{"t1":true,"t2":false}}""", AnswerValues.encodeToJson(tf))

        val matching = MatchingAnswer(pairs = linkedMapOf("l1" to "r2"))
        assertEquals("""{"type":"MATCHING","pairs":{"l1":"r2"}}""", AnswerValues.encodeToJson(matching))

        val essay = EssayAnswer(html = "<p>Xin chào</p>", wordCount = 2)
        assertEquals("""{"type":"ESSAY","html":"<p>Xin chào</p>","wordCount":2}""", AnswerValues.encodeToJson(essay))
        assertEquals("""{"type":"ESSAY","html":"","wordCount":0}""", AnswerValues.encodeToJson(EssayAnswer("", 0)))
    }

    @Test
    fun answerValuesWrapperFiltersInvalidIdsDeduplicatesAndEnforcesMatchingUniqueness() {
        val scItem = QuestionItem.SingleChoice(
            id = "q1",
            displayNo = 1,
            stem = "",
            options = listOf(ChoiceOptionItem("a", "<p>A</p>"), ChoiceOptionItem("b", "<p>B</p>")),
        )
        assertEquals(SingleChoiceAnswer(null), AnswerValues.singleChoice(scItem, "unknown"))
        assertEquals(SingleChoiceAnswer("a"), AnswerValues.singleChoice(scItem, "a"))

        val mcItem = QuestionItem.MultipleChoice(
            id = "q2",
            displayNo = 2,
            stem = "",
            options = listOf(ChoiceOptionItem("a", "<p>A</p>"), ChoiceOptionItem("b", "<p>B</p>")),
        )
        assertEquals(
            MultipleChoiceAnswer(listOf("b", "a")),
            AnswerValues.multipleChoice(mcItem, listOf("b", "bad", "b", "a")),
        )

        val clozeItem = QuestionItem.Cloze(
            id = "q3",
            displayNo = 3,
            stem = "",
            passage = "[[b1]] [[b2]]",
            blanks = listOf(ClozeBlankItem("b1"), ClozeBlankItem("b2")),
        )
        assertEquals(
            ClozeAnswer(linkedMapOf("b1" to "", "b2" to "ok")),
            AnswerValues.cloze(clozeItem, linkedMapOf("b1" to "\u0000", "unknown" to "x", "b2" to "o\u0000k")),
        )

        val tfItem = QuestionItem.TrueFalse(
            id = "q4",
            displayNo = 4,
            stem = "",
            statements = listOf(TrueFalseStatementItem("t1", "<p>1</p>")),
        )
        assertEquals(
            TrueFalseAnswer(emptyMap()),
            AnswerValues.trueFalse(tfItem, mapOf("t1" to null, "bad" to true)),
        )

        val matchExclusive = QuestionItem.Matching(
            id = "q5",
            displayNo = 5,
            stem = "",
            left = listOf(MatchingSideItem("l1", "<p>1</p>"), MatchingSideItem("l2", "<p>2</p>")),
            right = listOf(MatchingSideItem("r1", "<p>A</p>"), MatchingSideItem("r2", "<p>B</p>")),
            reuseRight = false,
        )
        assertEquals(
            MatchingAnswer(linkedMapOf("l1" to "r1")),
            AnswerValues.matching(matchExclusive, linkedMapOf("l1" to "r1", "l2" to "r1", "bad" to "r2")),
        )

        val matchReuse = matchExclusive.copy(reuseRight = true)
        assertEquals(
            MatchingAnswer(linkedMapOf("l1" to "r1", "l2" to "r1")),
            AnswerValues.matching(matchReuse, linkedMapOf("l1" to "r1", "l2" to "r1")),
        )
    }

    @Test
    fun mapsOpenApiAttemptAndReviewContractFixtures() {
        val sharedRaw = QuestionSamplesLoader.apiFixtureFile("attempts-detail-200-in-progress-shared.json")
            .readText(Charsets.UTF_8)
        val sharedState = Serializer.kotlinxSerializationJson.decodeFromString(
            AttemptStateResponse.serializer(),
            sharedRaw,
        )
        val mappedAttemptItems = sharedState.parts.orEmpty().flatMap { part ->
            QuestionItemMapper.mapAttemptItems(part.items.orEmpty())
        }
        assertTrue(mappedAttemptItems.isNotEmpty())
        assertTrue(mappedAttemptItems.any { it is QuestionItem.SingleChoice })
        assertTrue(mappedAttemptItems.any { it is QuestionItem.MultipleChoice })

        val fullSolutionRaw = QuestionSamplesLoader.apiFixtureFile("attempts-review-200-full-solution.json")
            .readText(Charsets.UTF_8)
        val fullSolutionResp = Serializer.kotlinxSerializationJson.decodeFromString(
            AttemptReviewResponse.serializer(),
            fullSolutionRaw,
        )
        val mappedReviewItems = QuestionItemMapper.mapReviewResponse(fullSolutionResp)
        val mappedReviews = ItemReviewMapper.mapResponse(fullSolutionResp)
        assertTrue(mappedReviewItems.isNotEmpty())
        assertTrue(mappedReviews.values.all { it.level == ReviewLevel.FULL_SOLUTION })

        val scoreOnlyResp = fullSolutionResp.copy(resultView = "SCORE_ONLY")
        val scoreOnlyReviews = ItemReviewMapper.mapResponse(scoreOnlyResp)
        assertTrue(scoreOnlyReviews.values.all { it.level == ReviewLevel.SCORE_ONLY && it.key == null && it.effectiveSolutionHtml == null })

        val scoreAndAnswersRaw = QuestionSamplesLoader.apiFixtureFile("attempts-review-200-score-and-answers.json")
            .readText(Charsets.UTF_8)
        val scoreAndAnswersResp = Serializer.kotlinxSerializationJson.decodeFromString(
            AttemptReviewResponse.serializer(),
            scoreAndAnswersRaw,
        )
        val saaReviews = ItemReviewMapper.mapResponse(scoreAndAnswersResp)
        assertNotNull(saaReviews.values.firstOrNull())
        assertTrue(saaReviews.values.all { it.level == ReviewLevel.SCORE_AND_ANSWERS && it.effectiveSolutionHtml == null })
    }

    @Test
    fun itemReviewMapperGatesEssayFeedbackComputesMatchingPairMaxAndPreservesCriterionId() {
        val essayItem = AttemptReviewItem(
            questionType = AttemptReviewItem.QuestionType.ESSAY,
            outcome = AttemptReviewItem.Outcome.PARTIAL,
            earnedPoints = BigDecimal("3.5"),
            pointsMax = BigDecimal("5"),
            solution = "<p>Bài mẫu tham khảo</p>",
            studentAnswer = EssayAnswer("<p>Bài làm học viên</p>", 4),
            essayFeedback = AttemptReviewEssayFeedback(
                comment = "Lập luận khá chặt chẽ",
                points = BigDecimal("3.5"),
                criteria = listOf(
                    AttemptReviewEssayCriterionScore(
                        id = "Tiêu chí 1: Luận điểm",
                        points = BigDecimal("2"),
                        comment = "Nêu đúng luận điểm chính",
                    ),
                ),
            ),
        )

        val scoreOnly = ItemReviewMapper.mapItem(essayItem, ReviewLevel.SCORE_ONLY, includeEssayCriteria = true)
        assertNull(scoreOnly.effectiveSolutionHtml)
        assertNull(scoreOnly.essay?.comment)
        assertTrue(scoreOnly.essay?.criteria.orEmpty().isEmpty())

        val scoreAndAnswers = ItemReviewMapper.mapItem(essayItem, ReviewLevel.SCORE_AND_ANSWERS, includeEssayCriteria = true)
        assertNull(scoreAndAnswers.effectiveSolutionHtml)
        assertNull(scoreAndAnswers.essay?.comment)
        assertTrue(scoreAndAnswers.essay?.criteria.orEmpty().isEmpty())

        val fullDefault = ItemReviewMapper.mapItem(essayItem, ReviewLevel.FULL_SOLUTION)
        assertEquals("<p>Bài mẫu tham khảo</p>", fullDefault.effectiveSolutionHtml)
        assertEquals("Lập luận khá chặt chẽ", fullDefault.essay?.comment)
        assertTrue(fullDefault.essay?.criteria.orEmpty().isEmpty())

        val fullWithCriteria = ItemReviewMapper.mapItem(essayItem, ReviewLevel.FULL_SOLUTION, includeEssayCriteria = true)
        assertEquals("Lập luận khá chặt chẽ", fullWithCriteria.essay?.comment)
        assertEquals(1, fullWithCriteria.essay?.criteria?.size)
        val criterion = fullWithCriteria.essay!!.criteria.first()
        assertEquals("Tiêu chí 1: Luận điểm", criterion.id)
        assertEquals("Tiêu chí 1: Luận điểm", criterion.description)
        assertEquals("Nêu đúng luận điểm chính", criterion.comment)
        assertEquals(BigDecimal("2"), criterion.earned)

        val matchingItem = AttemptReviewItem(
            questionType = AttemptReviewItem.QuestionType.MATCHING,
            outcome = AttemptReviewItem.Outcome.PARTIAL,
            earnedPoints = BigDecimal("0.5"),
            pointsMax = BigDecimal("1"),
            studentAnswer = MatchingAnswer(linkedMapOf("l1" to "r1", "l2" to "r2", "l4" to "r4")),
            gradingDetail = ReviewGradingDetail(
                pairResults = listOf(
                    ReviewMatchingPairResult(leftId = "l1", matched = true, pointsEarned = BigDecimal("0.25")),
                    ReviewMatchingPairResult(leftId = "l2", matched = false, pointsEarned = BigDecimal.ZERO),
                    ReviewMatchingPairResult(leftId = "l3", matched = false, pointsEarned = BigDecimal.ZERO),
                    ReviewMatchingPairResult(leftId = "l4", matched = true, pointsEarned = BigDecimal("0.25")),
                ),
            ),
        )
        val mappedMatching = ItemReviewMapper.mapItem(matchingItem, ReviewLevel.FULL_SOLUTION)
        assertEquals(4, mappedMatching.detail.size)
        assertEquals(BigDecimal("0.25"), mappedMatching.detail["l1"]?.max)
        assertEquals(BigDecimal("0.25"), mappedMatching.detail["l2"]?.max)
        assertEquals(BigDecimal("0.25"), mappedMatching.detail["l3"]?.max)
        assertEquals(BigDecimal("0.25"), mappedMatching.detail["l4"]?.max)
    }
}
