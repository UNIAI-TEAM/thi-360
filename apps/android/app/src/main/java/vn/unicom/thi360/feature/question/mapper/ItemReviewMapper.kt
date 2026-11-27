package vn.unicom.thi360.feature.question.mapper

import java.math.BigDecimal
import vn.unicom.thi360.core.network.generated.model.AttemptReviewItem
import vn.unicom.thi360.core.network.generated.model.AttemptReviewResponse
import vn.unicom.thi360.core.network.generated.model.EssayAnswer
import vn.unicom.thi360.core.network.generated.model.MatchingAnswer
import vn.unicom.thi360.core.network.generated.model.ReviewClozeBlankResult
import vn.unicom.thi360.feature.question.model.EssayCriterionReview
import vn.unicom.thi360.feature.question.model.EssayGradingState
import vn.unicom.thi360.feature.question.model.EssayReview
import vn.unicom.thi360.feature.question.model.ItemOutcome
import vn.unicom.thi360.feature.question.model.ItemReview
import vn.unicom.thi360.feature.question.model.ItemReviewKey
import vn.unicom.thi360.feature.question.model.PartReviewResult
import vn.unicom.thi360.feature.question.model.ReviewLevel
import vn.unicom.thi360.feature.question.model.TrueFalseScoringMethod

object ItemReviewMapper {
    fun reviewLevel(resultView: String?): ReviewLevel = when (resultView?.uppercase()) {
        "FULL_SOLUTION" -> ReviewLevel.FULL_SOLUTION
        "SCORE_AND_ANSWERS" -> ReviewLevel.SCORE_AND_ANSWERS
        else -> ReviewLevel.SCORE_ONLY
    }

    fun mapResponse(
        response: AttemptReviewResponse,
        includeEssayCriteria: Boolean = false,
    ): Map<String, ItemReview> {
        val level = reviewLevel(response.resultView)
        val items = response.items.orEmpty()
        val setIds = items
            .filter { it.questionType == AttemptReviewItem.QuestionType.QUESTION_SET }
            .mapNotNullTo(HashSet()) { it.itemId }

        val mappedIndividual = LinkedHashMap<String, ItemReview>(items.size)
        val childrenByParent = LinkedHashMap<String, MutableMap<String, ItemReview>>()

        for (item in items) {
            val id = item.itemId?.toString() ?: continue
            val review = mapItem(
                item = item,
                level = level,
                includeEssayCriteria = includeEssayCriteria,
            )
            mappedIndividual[id] = review
            val parentId = item.parentItemId
            if (parentId != null && parentId in setIds) {
                childrenByParent.getOrPut(parentId.toString()) { LinkedHashMap() }[id] = review
            }
        }

        val result = LinkedHashMap<String, ItemReview>(items.size)
        for ((id, review) in mappedIndividual) {
            val childMap = childrenByParent[id]
            result[id] = if (childMap != null) review.copy(children = childMap) else review
        }
        return result
    }

    fun mapItem(
        item: AttemptReviewItem,
        level: ReviewLevel,
        children: Map<String, ItemReview> = emptyMap(),
        includeEssayCriteria: Boolean = false,
    ): ItemReview {
        val outcome = mapOutcome(item.outcome)
        val key = if (level.showsAnswers) mapExpectedKey(item) else null
        val detail = mapPartDetails(item)
        val scoringMethod = TrueFalseScoringMethod.fromWire(item.gradingDetail?.scoringMethod)
        val solutionHtml = if (level.showsSolution) item.solution?.takeIf { it.isNotBlank() } else null
        val essay = if (item.questionType == AttemptReviewItem.QuestionType.ESSAY) {
            mapEssayReview(item, outcome, includeEssayCriteria)
        } else {
            null
        }

        return ItemReview(
            outcome = outcome,
            earned = item.earnedPoints,
            max = item.pointsMax ?: BigDecimal.ONE,
            level = level,
            key = key,
            detail = detail,
            scoringMethod = scoringMethod,
            solutionHtml = solutionHtml,
            essay = essay,
            children = children,
        )
    }

    private fun mapOutcome(outcome: AttemptReviewItem.Outcome?): ItemOutcome? = when (outcome) {
        AttemptReviewItem.Outcome.CORRECT -> ItemOutcome.CORRECT
        AttemptReviewItem.Outcome.PARTIAL -> ItemOutcome.PARTIAL
        AttemptReviewItem.Outcome.WRONG -> ItemOutcome.WRONG
        AttemptReviewItem.Outcome.BLANK -> ItemOutcome.BLANK
        AttemptReviewItem.Outcome.PENDING_REVIEW -> ItemOutcome.PENDING_REVIEW
        null -> null
    }

    private fun mapBlankOutcome(outcome: ReviewClozeBlankResult.Outcome?): ItemOutcome = when (outcome) {
        ReviewClozeBlankResult.Outcome.CORRECT -> ItemOutcome.CORRECT
        ReviewClozeBlankResult.Outcome.PARTIAL -> ItemOutcome.PARTIAL
        ReviewClozeBlankResult.Outcome.WRONG -> ItemOutcome.WRONG
        ReviewClozeBlankResult.Outcome.BLANK -> ItemOutcome.BLANK
        ReviewClozeBlankResult.Outcome.PENDING_REVIEW -> ItemOutcome.PENDING_REVIEW
        null -> ItemOutcome.BLANK
    }

    private fun mapExpectedKey(item: AttemptReviewItem): ItemReviewKey? {
        val expected = item.expectedAnswer ?: return null
        return when (item.questionType) {
            AttemptReviewItem.QuestionType.SINGLE_CHOICE,
            AttemptReviewItem.QuestionType.MULTIPLE_CHOICE,
            -> expected.expectedOptionIds?.takeIf { it.isNotEmpty() }?.let {
                ItemReviewKey.Choice(it.toSet())
            }
            AttemptReviewItem.QuestionType.SHORT_ANSWER ->
                expected.acceptedTexts?.takeIf { it.isNotEmpty() }?.let {
                    ItemReviewKey.ShortAnswer(it)
                }
            AttemptReviewItem.QuestionType.CLOZE ->
                expected.clozeKeys?.takeIf { it.isNotEmpty() }?.let { keys ->
                    val map = LinkedHashMap<String, List<String>>(keys.size)
                    for (entry in keys) {
                        val blankId = entry.blankId ?: continue
                        map[blankId] = entry.acceptedTexts.orEmpty()
                    }
                    ItemReviewKey.Cloze(map)
                }
            AttemptReviewItem.QuestionType.TRUE_FALSE ->
                expected.trueFalseKeys?.takeIf { it.isNotEmpty() }?.let { keys ->
                    val map = LinkedHashMap<String, Boolean>(keys.size)
                    for (entry in keys) {
                        val statementId = entry.statementId ?: continue
                        val expectedBool = entry.expected ?: continue
                        map[statementId] = expectedBool
                    }
                    ItemReviewKey.TrueFalse(map)
                }
            AttemptReviewItem.QuestionType.MATCHING ->
                expected.matchingKeys?.takeIf { it.isNotEmpty() }?.let { keys ->
                    val map = LinkedHashMap<String, String>(keys.size)
                    for (entry in keys) {
                        val leftId = entry.leftId ?: continue
                        val rightId = entry.rightId ?: continue
                        map[leftId] = rightId
                    }
                    ItemReviewKey.Matching(map)
                }
            else -> null
        }
    }

    private fun mapPartDetails(item: AttemptReviewItem): Map<String, PartReviewResult> {
        val grading = item.gradingDetail ?: return emptyMap()
        val result = LinkedHashMap<String, PartReviewResult>()

        grading.blankResults?.forEach { blank ->
            val id = blank.id ?: return@forEach
            result[id] = PartReviewResult(
                outcome = mapBlankOutcome(blank.outcome),
                earned = blank.pointsEarned,
                max = blank.pointsMax,
            )
        }

        grading.statementResults?.forEach { stmt ->
            val id = stmt.id ?: return@forEach
            val outcome = when {
                stmt.answered != true -> ItemOutcome.BLANK
                stmt.matched == true -> ItemOutcome.CORRECT
                else -> ItemOutcome.WRONG
            }
            result[id] = PartReviewResult(outcome = outcome)
        }

        val studentPairs = (item.studentAnswer as? MatchingAnswer)?.pairs.orEmpty()
        grading.pairResults?.forEach { pair ->
            val leftId = pair.leftId ?: return@forEach
            val answered = studentPairs[leftId] != null
            val outcome = when {
                pair.matched == true -> ItemOutcome.CORRECT
                !answered -> ItemOutcome.BLANK
                else -> ItemOutcome.WRONG
            }
            result[leftId] = PartReviewResult(
                outcome = outcome,
                earned = pair.pointsEarned,
            )
        }

        return result
    }

    private fun mapEssayReview(
        item: AttemptReviewItem,
        outcome: ItemOutcome?,
        includeEssayCriteria: Boolean,
    ): EssayReview {
        val feedback = item.essayFeedback
        val state = when {
            outcome == ItemOutcome.PENDING_REVIEW -> EssayGradingState.PENDING
            feedback != null || item.earnedPoints != null -> EssayGradingState.GRADED
            else -> EssayGradingState.PENDING
        }
        val wordCount = item.gradingDetail?.wordCount
            ?: (item.studentAnswer as? EssayAnswer)?.wordCount
        val criteria = if (includeEssayCriteria) {
            feedback?.criteria.orEmpty().map { criterion ->
                EssayCriterionReview(
                    id = criterion.id,
                    description = criterion.comment?.takeIf { it.isNotBlank() } ?: criterion.id.orEmpty(),
                    earned = criterion.points,
                    comment = criterion.comment,
                )
            }
        } else {
            emptyList()
        }
        return EssayReview(
            state = state,
            wordCount = wordCount,
            comment = feedback?.comment?.takeIf { it.isNotBlank() },
            criteria = criteria,
        )
    }
}
