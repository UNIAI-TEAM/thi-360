package vn.unicom.thi360.feature.result.review

import androidx.compose.runtime.Immutable
import java.math.BigDecimal
import java.math.MathContext
import vn.unicom.thi360.core.network.generated.model.AnswerValue
import vn.unicom.thi360.core.network.generated.model.AttemptReviewEssayFeedback
import vn.unicom.thi360.core.network.generated.model.AttemptReviewItem
import vn.unicom.thi360.core.network.generated.model.AttemptReviewPart
import vn.unicom.thi360.core.network.generated.model.AttemptReviewResponse
import vn.unicom.thi360.core.network.generated.model.EssayAnswer
import vn.unicom.thi360.core.network.generated.model.MatchingAnswer
import vn.unicom.thi360.core.network.generated.model.ReviewClozeBlankResult
import vn.unicom.thi360.feature.question.mapper.QuestionItemMapper
import vn.unicom.thi360.feature.question.model.EssayGradingState
import vn.unicom.thi360.feature.question.model.EssayReview
import vn.unicom.thi360.feature.question.model.ItemOutcome
import vn.unicom.thi360.feature.question.model.ItemReview
import vn.unicom.thi360.feature.question.model.ItemReviewKey
import vn.unicom.thi360.feature.question.model.PartReviewResult
import vn.unicom.thi360.feature.question.model.QuestionItem
import vn.unicom.thi360.feature.question.model.QuestionMode
import vn.unicom.thi360.feature.question.model.ReviewLevel
import vn.unicom.thi360.feature.question.model.TrueFalseScoringMethod

@Immutable
data class EssayCriterionRow(
    val id: String?,
    val points: BigDecimal?,
    val comment: String?,
)

@Immutable
data class EssayCriteriaBlock(
    val id: String,
    val childDisplayNo: Int? = null,
    val criteria: List<EssayCriterionRow>,
)

@Immutable
data class ReviewPaletteSummary(
    val correct: Int = 0,
    val partial: Int = 0,
    val wrong: Int = 0,
    val blank: Int = 0,
)

@Immutable
data class ReviewGroup(
    val id: String,
    val partId: String?,
    val partCode: String?,
    val displayNo: Int,
    val rangeFrom: Int? = null,
    val rangeTo: Int? = null,
    val outcome: ItemOutcome,
    val item: QuestionItem,
    val studentAnswer: AnswerValue?,
    val childAnswers: Map<String, AnswerValue> = emptyMap(),
    val mode: QuestionMode.Review,
    val essayCriteriaBlocks: List<EssayCriteriaBlock> = emptyList(),
)

@Immutable
data class ReviewPart(
    val key: String,
    val partId: String?,
    val partCode: String?,
    val partName: String?,
    val ord: Int,
    val isSkipped: Boolean,
    val pendingReview: Boolean,
    val score: BigDecimal?,
    val maxScore: BigDecimal?,
    val groups: List<ReviewGroup>,
    val summary: ReviewPaletteSummary,
)

@Immutable
data class ReviewModel(
    val state: AttemptReviewResponse.State?,
    val resultView: String?,
    val level: ReviewLevel,
    val parts: List<ReviewPart>,
)

object ReviewMapper {
    fun map(response: AttemptReviewResponse): ReviewModel {
        val level = reviewLevel(response.resultView)
        val sortedParts = response.parts.orEmpty()
            .sortedBy { it.ord ?: Int.MAX_VALUE }
        val rawItems = response.items.orEmpty()

        val parts = if (sortedParts.isEmpty()) {
            val groupedBuckets = LinkedHashMap<String, MutableList<AttemptReviewItem>>()
            for (item in rawItems) {
                val bucketKey = item.partId?.toString() ?: item.partCode ?: ""
                groupedBuckets.getOrPut(bucketKey) { mutableListOf() }.add(item)
            }
            val mappedGroups = groupedBuckets.values.flatMap { bucketItems ->
                mapPartItems(bucketItems, level)
            }
            listOf(
                ReviewPart(
                    key = "default",
                    partId = null,
                    partCode = null,
                    partName = null,
                    ord = 0,
                    isSkipped = false,
                    pendingReview = false,
                    score = null,
                    maxScore = null,
                    groups = mappedGroups,
                    summary = computePaletteSummary(mappedGroups),
                ),
            )
        } else {
            val knownPartIds = sortedParts.mapNotNull { it.partId?.toString() }.toSet()
            val itemsByPartIndex = Array(sortedParts.size) { mutableListOf<AttemptReviewItem>() }
            for (item in rawItems) {
                val partIndex = sortedParts.indexOfFirst { part ->
                    matchesPart(
                        itemPartId = item.partId?.toString(),
                        itemPartCode = item.partCode,
                        partId = part.partId?.toString(),
                        partCode = part.partCode,
                        knownPartIds = knownPartIds,
                    )
                }
                if (partIndex >= 0) {
                    itemsByPartIndex[partIndex].add(item)
                }
            }
            sortedParts.mapIndexed { index, part ->
                val partIdStr = part.partId?.toString()
                val partCode = part.partCode
                val partGroups = mapPartItems(itemsByPartIndex[index], level)
                ReviewPart(
                    key = partCode ?: partIdStr ?: "part-$index",
                    partId = partIdStr,
                    partCode = partCode,
                    partName = part.partName,
                    ord = part.ord ?: index,
                    isSkipped = part.state == "SKIPPED",
                    pendingReview = part.pendingReview == true,
                    score = part.score,
                    maxScore = part.maxScore,
                    groups = partGroups,
                    summary = computePaletteSummary(partGroups),
                )
            }
        }

        return ReviewModel(
            state = response.state,
            resultView = response.resultView,
            level = level,
            parts = parts,
        )
    }

    private fun mapPartItems(
        partItems: List<AttemptReviewItem>,
        level: ReviewLevel,
    ): List<ReviewGroup> {
        if (partItems.isEmpty()) return emptyList()
        val includeSolution = level == ReviewLevel.FULL_SOLUTION
        val setById = LinkedHashMap<String, AttemptReviewItem>()
        for (item in partItems) {
            if (item.questionType == AttemptReviewItem.QuestionType.QUESTION_SET) {
                val id = item.itemId?.toString()
                if (id != null) {
                    setById[id] = item
                }
            }
        }

        val sortedRaw = partItems.sortedWith(ITEM_COMPARATOR)
        val childrenByParent = LinkedHashMap<String, MutableList<AttemptReviewItem>>()
        val topLevelItems = mutableListOf<AttemptReviewItem>()
        var lastSetId: String? = null
        var remainingExpectedChildren = 0

        for (item in sortedRaw) {
            val parentId = item.parentItemId?.toString()
            if (parentId != null) {
                val resolvedParent = when {
                    setById.containsKey(parentId) -> parentId
                    // BE-1 anonymized fixtures assign unique sequential UUIDs per occurrence; associate orphaned children with the preceding set in the same part.
                    lastSetId != null && remainingExpectedChildren > 0 -> {
                        remainingExpectedChildren -= 1
                        lastSetId
                    }
                    else -> parentId
                }
                childrenByParent.getOrPut(resolvedParent) { mutableListOf() }.add(item)
            } else {
                topLevelItems.add(item)
                if (item.questionType == AttemptReviewItem.QuestionType.QUESTION_SET) {
                    lastSetId = item.itemId?.toString()
                    remainingExpectedChildren = item.gradingDetail?.childCount ?: Int.MAX_VALUE
                } else {
                    lastSetId = null
                    remainingExpectedChildren = 0
                }
            }
        }

        val mappedGroups = mutableListOf<ReviewGroup>()
        for ((index, item) in topLevelItems.withIndex()) {
            if (item.questionType == AttemptReviewItem.QuestionType.QUESTION_SET) {
                val setId = item.itemId?.toString() ?: "set-$index"
                val rawChildren = childrenByParent[setId].orEmpty().sortedWith(ITEM_COMPARATOR)
                val visibleChildren = rawChildren.filter { it.outcome != AttemptReviewItem.Outcome.PENDING_REVIEW }

                if (visibleChildren.isEmpty()) {
                    continue
                }

                val childQuestionItems = mutableListOf<QuestionItem>()
                val childAnswers = LinkedHashMap<String, AnswerValue>()
                val childReviews = LinkedHashMap<String, ItemReview>()
                val childCriteriaBlocks = mutableListOf<EssayCriteriaBlock>()

                visibleChildren.forEach { child ->
                    val mappedChild = QuestionItemMapper.mapReviewItem(child, children = emptyList())
                        ?: return@forEach
                    val childReview = buildLeafReview(child, level)
                    childQuestionItems.add(mappedChild)
                    child.studentAnswer?.let { childAnswers[mappedChild.id] = it }
                    childReviews[mappedChild.id] = childReview

                    if (includeSolution && child.outcome != AttemptReviewItem.Outcome.BLANK) {
                        buildEssayCriteriaBlock(
                            id = mappedChild.id,
                            childDisplayNo = mappedChild.displayNo,
                            feedback = child.essayFeedback,
                        )?.let { childCriteriaBlocks.add(it) }
                    }
                }

                if (childQuestionItems.isEmpty()) {
                    continue
                }

                val parentQuestion = QuestionItemMapper.mapReviewItem(item, children = childQuestionItems)
                    ?: continue

                val hasFilteredChildren = visibleChildren.size != rawChildren.size
                val groupOutcome = if (
                    !hasFilteredChildren &&
                    item.outcome != null &&
                    item.outcome != AttemptReviewItem.Outcome.PENDING_REVIEW
                ) {
                    mapOutcome(item.outcome)
                } else {
                    deriveGroupOutcome(visibleChildren)
                }

                val parentReview = ItemReview(
                    outcome = groupOutcome,
                    earned = item.earnedPoints,
                    max = item.pointsMax ?: BigDecimal.ONE,
                    level = level,
                    solutionHtml = if (includeSolution) item.solution?.takeIf { it.isNotBlank() } else null,
                    children = childReviews,
                )

                val childNos = childQuestionItems.map { it.displayNo }.filter { it > 0 }
                val fromNo = childNos.minOrNull() ?: parentQuestion.displayNo
                val toNo = childNos.maxOrNull() ?: parentQuestion.displayNo

                mappedGroups.add(
                    ReviewGroup(
                        id = parentQuestion.id,
                        partId = item.partId?.toString(),
                        partCode = item.partCode,
                        displayNo = fromNo,
                        rangeFrom = if (fromNo != toNo) fromNo else null,
                        rangeTo = if (fromNo != toNo) toNo else null,
                        outcome = groupOutcome,
                        item = parentQuestion,
                        studentAnswer = null,
                        childAnswers = childAnswers,
                        mode = QuestionMode.Review(parentReview),
                        essayCriteriaBlocks = childCriteriaBlocks,
                    ),
                )
                continue
            }

            if (item.outcome == AttemptReviewItem.Outcome.PENDING_REVIEW) {
                continue
            }

            val question = QuestionItemMapper.mapReviewItem(item, children = emptyList()) ?: continue
            val leafReview = buildLeafReview(item, level)
            val clozeBlankCount = if (item.questionType == AttemptReviewItem.QuestionType.CLOZE) {
                item.body?.blanks?.size ?: 0
            } else {
                0
            }
            val rangeFrom = if (clozeBlankCount > 1) question.displayNo else null
            val rangeTo = if (clozeBlankCount > 1) question.displayNo + clozeBlankCount - 1 else null

            val criteriaBlocks = if (includeSolution && item.outcome != AttemptReviewItem.Outcome.BLANK) {
                listOfNotNull(
                    buildEssayCriteriaBlock(
                        id = question.id,
                        childDisplayNo = null,
                        feedback = item.essayFeedback,
                    ),
                )
            } else {
                emptyList()
            }

            mappedGroups.add(
                ReviewGroup(
                    id = question.id,
                    partId = item.partId?.toString(),
                    partCode = item.partCode,
                    displayNo = question.displayNo,
                    rangeFrom = rangeFrom,
                    rangeTo = rangeTo,
                    outcome = mapOutcome(item.outcome),
                    item = question,
                    studentAnswer = item.studentAnswer,
                    childAnswers = emptyMap(),
                    mode = QuestionMode.Review(leafReview),
                    essayCriteriaBlocks = criteriaBlocks,
                ),
            )
        }

        return mappedGroups
    }

    fun computePaletteSummary(groups: List<ReviewGroup>): ReviewPaletteSummary {
        var correct = 0
        var partial = 0
        var wrong = 0
        var blank = 0

        fun tally(outcome: ItemOutcome?) {
            when (outcome) {
                ItemOutcome.CORRECT -> correct += 1
                ItemOutcome.PARTIAL -> partial += 1
                ItemOutcome.WRONG -> wrong += 1
                ItemOutcome.BLANK,
                ItemOutcome.PENDING_REVIEW,
                null,
                -> blank += 1
            }
        }

        for (group in groups) {
            val setItem = group.item as? QuestionItem.QuestionSet
            if (setItem != null && setItem.children.isNotEmpty()) {
                for (child in setItem.children) {
                    tally(group.mode.review.children[child.id]?.outcome)
                }
            } else {
                tally(group.outcome)
            }
        }

        return ReviewPaletteSummary(
            correct = correct,
            partial = partial,
            wrong = wrong,
            blank = blank,
        )
    }

    fun deriveGroupOutcome(children: List<AttemptReviewItem>): ItemOutcome {
        if (children.isEmpty()) return ItemOutcome.BLANK
        val outcomes = children.map { mapOutcome(it.outcome) }
        return when {
            outcomes.all { it == ItemOutcome.CORRECT } -> ItemOutcome.CORRECT
            outcomes.all { it == ItemOutcome.BLANK } -> ItemOutcome.BLANK
            outcomes.all { it == ItemOutcome.WRONG } -> ItemOutcome.WRONG
            else -> ItemOutcome.PARTIAL
        }
    }

    private fun reviewLevel(resultView: String?): ReviewLevel = when (resultView?.uppercase()) {
        "FULL_SOLUTION" -> ReviewLevel.FULL_SOLUTION
        "SCORE_AND_ANSWERS" -> ReviewLevel.SCORE_AND_ANSWERS
        else -> ReviewLevel.SCORE_ONLY
    }

    private fun matchesPart(
        itemPartId: String?,
        itemPartCode: String?,
        partId: String?,
        partCode: String?,
        knownPartIds: Set<String>,
    ): Boolean {
        if (partId != null && itemPartId != null && itemPartId in knownPartIds) {
            return partId == itemPartId
        }
        if (partCode != null && itemPartCode != null) {
            return partCode == itemPartCode
        }
        return false
    }

    private fun buildLeafReview(
        item: AttemptReviewItem,
        level: ReviewLevel,
    ): ItemReview {
        val includeSolution = level == ReviewLevel.FULL_SOLUTION
        val outcome = item.outcome?.let { mapOutcome(it) }
        val key = if (level.showsAnswers) mapExpectedKey(item) else null
        val detail = mapPartDetails(item)
        val scoringMethod = TrueFalseScoringMethod.fromWire(
            item.gradingDetail?.scoringMethod ?: item.body?.scoringRule,
        )
        val solutionHtml = if (includeSolution) item.solution?.takeIf { it.isNotBlank() } else null
        val essay = if (item.questionType == AttemptReviewItem.QuestionType.ESSAY) {
            val isBlank = outcome == ItemOutcome.BLANK
            val state = if (outcome == ItemOutcome.PENDING_REVIEW) {
                EssayGradingState.PENDING
            } else {
                EssayGradingState.GRADED
            }
            val wordCount = (item.studentAnswer as? EssayAnswer)?.wordCount
                ?: item.gradingDetail?.wordCount
            val comment = if (includeSolution && !isBlank) {
                item.essayFeedback?.comment?.takeIf { it.isNotBlank() }
            } else {
                null
            }
            // Keep criteria empty inside QuestionView because ReviewScreen draws the 3-column criteria card list (B1.7).
            EssayReview(
                state = state,
                wordCount = wordCount,
                comment = comment,
                criteria = emptyList(),
            )
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
        )
    }

    private fun buildEssayCriteriaBlock(
        id: String,
        childDisplayNo: Int?,
        feedback: AttemptReviewEssayFeedback?,
    ): EssayCriteriaBlock? {
        val rawCriteria = feedback?.criteria.orEmpty()
        if (rawCriteria.isEmpty()) return null
        return EssayCriteriaBlock(
            id = id,
            childDisplayNo = childDisplayNo,
            criteria = rawCriteria.map { criterion ->
                EssayCriterionRow(
                    id = criterion.id?.takeIf { it.isNotBlank() },
                    points = criterion.points,
                    comment = criterion.comment?.takeIf { it.isNotBlank() },
                )
            },
        )
    }

    private fun mapOutcome(outcome: AttemptReviewItem.Outcome?): ItemOutcome = when (outcome) {
        AttemptReviewItem.Outcome.CORRECT -> ItemOutcome.CORRECT
        AttemptReviewItem.Outcome.PARTIAL -> ItemOutcome.PARTIAL
        AttemptReviewItem.Outcome.WRONG -> ItemOutcome.WRONG
        AttemptReviewItem.Outcome.BLANK -> ItemOutcome.BLANK
        AttemptReviewItem.Outcome.PENDING_REVIEW -> ItemOutcome.PENDING_REVIEW
        null -> ItemOutcome.BLANK
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

        val blankResults = grading.blankResults.orEmpty()
        if (blankResults.isNotEmpty()) {
            for (blank in blankResults) {
                val id = blank.id ?: continue
                result[id] = PartReviewResult(
                    outcome = mapBlankOutcome(blank.outcome),
                    earned = blank.pointsEarned,
                    max = blank.pointsMax,
                )
            }
            return result
        }

        val statementResults = grading.statementResults.orEmpty()
        if (statementResults.isNotEmpty()) {
            for (stmt in statementResults) {
                val id = stmt.id ?: continue
                val outcome = when {
                    stmt.matched == true -> ItemOutcome.CORRECT
                    stmt.answered == true -> ItemOutcome.WRONG
                    else -> ItemOutcome.BLANK
                }
                result[id] = PartReviewResult(outcome = outcome)
            }
            return result
        }

        val pairResults = grading.pairResults.orEmpty()
        if (pairResults.isNotEmpty()) {
            val studentPairs = (item.studentAnswer as? MatchingAnswer)?.pairs.orEmpty()
            val pairMax = if (item.pointsMax != null) {
                val divided = item.pointsMax
                    .divide(BigDecimal.valueOf(pairResults.size.toLong()), MathContext.DECIMAL64)
                    .stripTrailingZeros()
                if (divided.scale() < 0) divided.setScale(0) else divided
            } else {
                null
            }
            for (pair in pairResults) {
                val leftId = pair.leftId ?: continue
                val picked = studentPairs[leftId]
                val outcome = when {
                    pair.matched == true -> ItemOutcome.CORRECT
                    !picked.isNullOrEmpty() -> ItemOutcome.WRONG
                    else -> ItemOutcome.BLANK
                }
                result[leftId] = PartReviewResult(
                    outcome = outcome,
                    earned = pair.pointsEarned,
                    max = pairMax,
                )
            }
            return result
        }

        return emptyMap()
    }

    private val ITEM_COMPARATOR: Comparator<AttemptReviewItem> = compareBy(
        { it.ord ?: Int.MAX_VALUE },
        { it.displayNo ?: Int.MAX_VALUE },
    )
}
