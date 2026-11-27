package vn.unicom.thi360.feature.question.mapper

import vn.unicom.thi360.core.network.generated.model.AttemptItemView
import vn.unicom.thi360.core.network.generated.model.AttemptReviewItem
import vn.unicom.thi360.core.network.generated.model.AttemptReviewResponse
import vn.unicom.thi360.core.network.generated.model.Cloze
import vn.unicom.thi360.core.network.generated.model.Essay
import vn.unicom.thi360.core.network.generated.model.Matching
import vn.unicom.thi360.core.network.generated.model.MultipleChoice
import vn.unicom.thi360.core.network.generated.model.QuestionSet
import vn.unicom.thi360.core.network.generated.model.ShortAnswer
import vn.unicom.thi360.core.network.generated.model.SingleChoice
import vn.unicom.thi360.core.network.generated.model.TrueFalse
import vn.unicom.thi360.feature.question.model.AnswerFormat
import vn.unicom.thi360.feature.question.model.ChoiceOptionItem
import vn.unicom.thi360.feature.question.model.ClozeBlankItem
import vn.unicom.thi360.feature.question.model.MatchingSideItem
import vn.unicom.thi360.feature.question.model.MultipleChoiceScoringRule
import vn.unicom.thi360.feature.question.model.QuestionItem
import vn.unicom.thi360.feature.question.model.TrueFalseScoringMethod
import vn.unicom.thi360.feature.question.model.TrueFalseStatementItem

object QuestionItemMapper {
    fun mapAttemptItems(items: List<AttemptItemView>): List<QuestionItem> {
        val setIds = items
            .filter { it.questionType == AttemptItemView.QuestionType.QUESTION_SET || it.questionBody is QuestionSet }
            .mapNotNullTo(HashSet()) { it.itemId }

        val childrenByParent = LinkedHashMap<String, MutableList<QuestionItem>>()
        for (item in items) {
            val parentId = item.parentItemId
            if (parentId != null && parentId in setIds) {
                val mappedChild = mapAttemptItem(item, children = emptyList()) ?: continue
                childrenByParent.getOrPut(parentId.toString()) { ArrayList() }.add(mappedChild)
            }
        }

        val result = ArrayList<QuestionItem>(items.size)
        for (item in items) {
            val parentId = item.parentItemId
            if (parentId != null && parentId in setIds) continue
            val idKey = item.itemId?.toString() ?: continue
            val children = childrenByParent[idKey].orEmpty()
            val mapped = mapAttemptItem(item, children = children) ?: continue
            result.add(mapped)
        }
        return result
    }

    fun mapAttemptItem(
        item: AttemptItemView,
        children: List<QuestionItem> = emptyList(),
    ): QuestionItem? {
        val id = item.itemId?.toString() ?: return null
        val displayNo = item.displayNo ?: children.minOfOrNull { it.displayNo } ?: 0
        val stem = item.content.orEmpty()
        val optionOrder = item.optionOrder.orEmpty()
        val points = item.points

        return when (val body = item.questionBody) {
            is SingleChoice -> QuestionItem.SingleChoice(
                id = id,
                displayNo = displayNo,
                stem = stem,
                optionOrder = optionOrder,
                points = points,
                options = body.options.orEmpty().mapNotNull { opt ->
                    val optId = opt.id ?: return@mapNotNull null
                    ChoiceOptionItem(id = optId, content = opt.content.orEmpty())
                },
            )
            is MultipleChoice -> QuestionItem.MultipleChoice(
                id = id,
                displayNo = displayNo,
                stem = stem,
                optionOrder = optionOrder,
                points = points,
                options = body.options.orEmpty().mapNotNull { opt ->
                    val optId = opt.id ?: return@mapNotNull null
                    ChoiceOptionItem(id = optId, content = opt.content.orEmpty())
                },
                scoringRule = MultipleChoiceScoringRule.fromWire(body.scoringRule),
            )
            is ShortAnswer -> QuestionItem.ShortAnswer(
                id = id,
                displayNo = displayNo,
                stem = stem,
                optionOrder = optionOrder,
                points = points,
                answerFormat = AnswerFormat.fromWire(body.answerFormat),
                inputHint = body.inputHint?.takeIf { it.isNotEmpty() },
            )
            is Cloze -> QuestionItem.Cloze(
                id = id,
                displayNo = displayNo,
                stem = stem,
                optionOrder = optionOrder,
                points = points,
                passage = body.passage.orEmpty(),
                blanks = body.blanks.orEmpty().mapNotNull { blank ->
                    val blankId = blank.id ?: return@mapNotNull null
                    ClozeBlankItem(id = blankId)
                },
            )
            is TrueFalse -> QuestionItem.TrueFalse(
                id = id,
                displayNo = displayNo,
                stem = stem,
                optionOrder = optionOrder,
                points = points,
                statements = body.statements.orEmpty().mapNotNull { stmt ->
                    val stmtId = stmt.id ?: return@mapNotNull null
                    TrueFalseStatementItem(id = stmtId, content = stmt.content.orEmpty())
                },
            )
            is Matching -> QuestionItem.Matching(
                id = id,
                displayNo = displayNo,
                stem = stem,
                optionOrder = optionOrder,
                points = points,
                left = body.left.orEmpty().mapNotNull { side ->
                    val sideId = side.id ?: return@mapNotNull null
                    MatchingSideItem(id = sideId, content = side.content.orEmpty())
                },
                right = body.right.orEmpty().mapNotNull { side ->
                    val sideId = side.id ?: return@mapNotNull null
                    MatchingSideItem(id = sideId, content = side.content.orEmpty())
                },
                reuseRight = body.reuseRight == true,
            )
            is Essay -> QuestionItem.Essay(
                id = id,
                displayNo = displayNo,
                stem = stem,
                optionOrder = optionOrder,
                points = points,
                wordLimit = body.wordLimit,
            )
            is QuestionSet -> QuestionItem.QuestionSet(
                id = id,
                displayNo = displayNo,
                stem = stem,
                optionOrder = optionOrder,
                points = points,
                passage = body.passage.orEmpty(),
                blankNumberStart = body.blankNumberStart,
                children = children,
            )
            null -> null
        }
    }

    fun mapReviewResponse(response: AttemptReviewResponse): List<QuestionItem> =
        mapReviewItems(response.items.orEmpty())

    fun mapReviewItems(items: List<AttemptReviewItem>): List<QuestionItem> {
        val setIds = items
            .filter { it.questionType == AttemptReviewItem.QuestionType.QUESTION_SET }
            .mapNotNullTo(HashSet()) { it.itemId }

        val childrenByParent = LinkedHashMap<String, MutableList<QuestionItem>>()
        for (item in items) {
            val parentId = item.parentItemId
            if (parentId != null && parentId in setIds) {
                val mappedChild = mapReviewItem(item, children = emptyList()) ?: continue
                childrenByParent.getOrPut(parentId.toString()) { ArrayList() }.add(mappedChild)
            }
        }

        val result = ArrayList<QuestionItem>(items.size)
        for (item in items) {
            val parentId = item.parentItemId
            if (parentId != null && parentId in setIds) continue
            val idKey = item.itemId?.toString() ?: continue
            val children = childrenByParent[idKey].orEmpty()
            val mapped = mapReviewItem(item, children = children) ?: continue
            result.add(mapped)
        }
        return result
    }

    fun mapReviewItem(
        item: AttemptReviewItem,
        children: List<QuestionItem> = emptyList(),
    ): QuestionItem? {
        val id = item.itemId?.toString() ?: return null
        val displayNo = item.displayNo ?: children.minOfOrNull { it.displayNo } ?: 0
        val stem = item.content.orEmpty()
        val optionOrder = item.optionOrder.orEmpty()
        val points = item.pointsMax
        val body = item.body

        return when (item.questionType) {
            AttemptReviewItem.QuestionType.SINGLE_CHOICE -> QuestionItem.SingleChoice(
                id = id,
                displayNo = displayNo,
                stem = stem,
                optionOrder = optionOrder,
                points = points,
                options = body?.options.orEmpty().mapNotNull { opt ->
                    val optId = opt.id ?: return@mapNotNull null
                    ChoiceOptionItem(id = optId, content = opt.content.orEmpty())
                },
            )
            AttemptReviewItem.QuestionType.MULTIPLE_CHOICE -> QuestionItem.MultipleChoice(
                id = id,
                displayNo = displayNo,
                stem = stem,
                optionOrder = optionOrder,
                points = points,
                options = body?.options.orEmpty().mapNotNull { opt ->
                    val optId = opt.id ?: return@mapNotNull null
                    ChoiceOptionItem(id = optId, content = opt.content.orEmpty())
                },
                scoringRule = MultipleChoiceScoringRule.fromWire(body?.scoringRule),
            )
            AttemptReviewItem.QuestionType.SHORT_ANSWER -> QuestionItem.ShortAnswer(
                id = id,
                displayNo = displayNo,
                stem = stem,
                optionOrder = optionOrder,
                points = points,
                answerFormat = AnswerFormat.fromWire(body?.answerFormat),
                inputHint = body?.inputHint?.takeIf { it.isNotEmpty() },
            )
            AttemptReviewItem.QuestionType.CLOZE -> QuestionItem.Cloze(
                id = id,
                displayNo = displayNo,
                stem = stem,
                optionOrder = optionOrder,
                points = points,
                passage = body?.passage.orEmpty(),
                blanks = body?.blanks.orEmpty().mapNotNull { blank ->
                    val blankId = blank.id ?: return@mapNotNull null
                    ClozeBlankItem(id = blankId)
                },
                blankNumberStart = body?.blankNumberStart,
            )
            AttemptReviewItem.QuestionType.TRUE_FALSE -> QuestionItem.TrueFalse(
                id = id,
                displayNo = displayNo,
                stem = stem,
                optionOrder = optionOrder,
                points = points,
                statements = body?.statements.orEmpty().mapNotNull { stmt ->
                    val stmtId = stmt.id ?: return@mapNotNull null
                    TrueFalseStatementItem(id = stmtId, content = stmt.content.orEmpty())
                },
                scoringMethod = TrueFalseScoringMethod.fromWire(item.gradingDetail?.scoringMethod),
            )
            AttemptReviewItem.QuestionType.MATCHING -> QuestionItem.Matching(
                id = id,
                displayNo = displayNo,
                stem = stem,
                optionOrder = optionOrder,
                points = points,
                left = body?.left.orEmpty().mapNotNull { side ->
                    val sideId = side.id ?: return@mapNotNull null
                    MatchingSideItem(id = sideId, content = side.content.orEmpty())
                },
                right = body?.right.orEmpty().mapNotNull { side ->
                    val sideId = side.id ?: return@mapNotNull null
                    MatchingSideItem(id = sideId, content = side.content.orEmpty())
                },
                reuseRight = body?.reuseRight == true,
            )
            AttemptReviewItem.QuestionType.ESSAY -> QuestionItem.Essay(
                id = id,
                displayNo = displayNo,
                stem = stem,
                optionOrder = optionOrder,
                points = points,
                wordLimit = body?.wordLimit,
            )
            AttemptReviewItem.QuestionType.QUESTION_SET -> QuestionItem.QuestionSet(
                id = id,
                displayNo = displayNo,
                stem = stem,
                optionOrder = optionOrder,
                points = points,
                passage = body?.passage.orEmpty(),
                blankNumberStart = body?.blankNumberStart,
                children = children,
            )
            null -> null
        }
    }
}
