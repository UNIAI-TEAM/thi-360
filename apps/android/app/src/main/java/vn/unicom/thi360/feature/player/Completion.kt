package vn.unicom.thi360.feature.player

import androidx.compose.runtime.Immutable
import vn.unicom.thi360.core.network.generated.model.AnswerValue
import vn.unicom.thi360.core.network.generated.model.AttemptItemView
import vn.unicom.thi360.core.network.generated.model.Cloze
import vn.unicom.thi360.core.network.generated.model.ClozeAnswer
import vn.unicom.thi360.core.network.generated.model.Essay
import vn.unicom.thi360.core.network.generated.model.EssayAnswer
import vn.unicom.thi360.core.network.generated.model.Matching
import vn.unicom.thi360.core.network.generated.model.MatchingAnswer
import vn.unicom.thi360.core.network.generated.model.MultipleChoiceAnswer
import vn.unicom.thi360.core.network.generated.model.ShortAnswerValue
import vn.unicom.thi360.core.network.generated.model.SingleChoiceAnswer
import vn.unicom.thi360.core.network.generated.model.TrueFalse
import vn.unicom.thi360.core.network.generated.model.TrueFalseAnswer
import vn.unicom.thi360.feature.question.logic.WordCounter

enum class ItemCompletion {
    Answered,
    Partial,
    Blank,
}

@Immutable
data class CompletionDetail(
    val completion: ItemCompletion,
    val filled: Int,
    val total: Int,
)

@Immutable
data class EssayLimitStatus(
    val isEssay: Boolean,
    val wordCount: Int,
    val wordLimit: Int?,
    val underLimit: Boolean,
)

object Completion {

    fun of(item: AttemptItemView, answer: AnswerValue?): ItemCompletion =
        detail(item, answer).completion

    fun detail(item: AttemptItemView, answer: AnswerValue?): CompletionDetail {
        val qType = item.questionType ?: return CompletionDetail(ItemCompletion.Blank, 0, 0)
        if (qType == AttemptItemView.QuestionType.QUESTION_SET) {
            return CompletionDetail(ItemCompletion.Blank, 0, 0)
        }

        return when (qType) {
            AttemptItemView.QuestionType.SINGLE_CHOICE -> {
                val filled = (answer as? SingleChoiceAnswer)?.optionId?.trim()?.isNotEmpty() == true
                CompletionDetail(
                    completion = if (filled) ItemCompletion.Answered else ItemCompletion.Blank,
                    filled = if (filled) 1 else 0,
                    total = 1,
                )
            }

            AttemptItemView.QuestionType.MULTIPLE_CHOICE -> {
                val filled = (answer as? MultipleChoiceAnswer)?.optionIds?.isNotEmpty() == true
                CompletionDetail(
                    completion = if (filled) ItemCompletion.Answered else ItemCompletion.Blank,
                    filled = if (filled) 1 else 0,
                    total = 1,
                )
            }

            AttemptItemView.QuestionType.SHORT_ANSWER -> {
                val filled = (answer as? ShortAnswerValue)?.text?.trim()?.isNotEmpty() == true
                CompletionDetail(
                    completion = if (filled) ItemCompletion.Answered else ItemCompletion.Blank,
                    filled = if (filled) 1 else 0,
                    total = 1,
                )
            }

            AttemptItemView.QuestionType.CLOZE -> {
                val bodyIds = (item.questionBody as? Cloze)?.blanks.orEmpty()
                    .mapNotNull { it.id?.takeIf(String::isNotBlank) }
                val map = (answer as? ClozeAnswer)?.blanks.orEmpty()
                val total = if (bodyIds.isNotEmpty()) bodyIds.size else map.size
                if (total == 0) {
                    CompletionDetail(ItemCompletion.Blank, 0, 0)
                } else {
                    val filled = if (bodyIds.isNotEmpty()) {
                        bodyIds.count { id -> !map[id].isNullOrBlank() }
                    } else {
                        map.values.count { it.isNotBlank() }
                    }
                    CompletionDetail(
                        completion = completionFromCounts(filled, total),
                        filled = filled,
                        total = total,
                    )
                }
            }

            AttemptItemView.QuestionType.TRUE_FALSE -> {
                val bodyIds = (item.questionBody as? TrueFalse)?.statements.orEmpty()
                    .mapNotNull { it.id?.takeIf(String::isNotBlank) }
                val map = (answer as? TrueFalseAnswer)?.statements.orEmpty()
                val total = if (bodyIds.isNotEmpty()) bodyIds.size else map.size
                if (total == 0) {
                    CompletionDetail(ItemCompletion.Blank, 0, 0)
                } else {
                    val filled = if (bodyIds.isNotEmpty()) {
                        bodyIds.count { id -> map.containsKey(id) }
                    } else {
                        map.size
                    }
                    CompletionDetail(
                        completion = completionFromCounts(filled, total),
                        filled = filled,
                        total = total,
                    )
                }
            }

            AttemptItemView.QuestionType.MATCHING -> {
                val bodyIds = (item.questionBody as? Matching)?.left.orEmpty()
                    .mapNotNull { it.id?.takeIf(String::isNotBlank) }
                val map = (answer as? MatchingAnswer)?.pairs.orEmpty()
                val total = if (bodyIds.isNotEmpty()) bodyIds.size else map.size
                if (total == 0) {
                    CompletionDetail(ItemCompletion.Blank, 0, 0)
                } else {
                    val filled = if (bodyIds.isNotEmpty()) {
                        bodyIds.count { id -> !map[id].isNullOrBlank() }
                    } else {
                        map.values.count { it.isNotBlank() }
                    }
                    CompletionDetail(
                        completion = completionFromCounts(filled, total),
                        filled = filled,
                        total = total,
                    )
                }
            }

            AttemptItemView.QuestionType.ESSAY -> {
                val html = (answer as? EssayAnswer)?.html.orEmpty()
                val words = WordCounter.count(html)
                val filled = words > 0
                CompletionDetail(
                    completion = if (filled) ItemCompletion.Answered else ItemCompletion.Blank,
                    filled = if (filled) 1 else 0,
                    total = 1,
                )
            }

            AttemptItemView.QuestionType.QUESTION_SET -> CompletionDetail(ItemCompletion.Blank, 0, 0)
        }
    }

    fun essayLimit(item: AttemptItemView, answer: AnswerValue?): EssayLimitStatus {
        if (item.questionType != AttemptItemView.QuestionType.ESSAY) {
            return EssayLimitStatus(
                isEssay = false,
                wordCount = 0,
                wordLimit = null,
                underLimit = false,
            )
        }
        val wordLimit = (item.questionBody as? Essay)?.wordLimit?.takeIf { it > 0 }
        val html = (answer as? EssayAnswer)?.html.orEmpty()
        val wordCount = WordCounter.count(html)
        return EssayLimitStatus(
            isEssay = true,
            wordCount = wordCount,
            wordLimit = wordLimit,
            underLimit = wordLimit != null && wordCount < wordLimit,
        )
    }

    private fun completionFromCounts(filled: Int, total: Int): ItemCompletion = when {
        filled <= 0 -> ItemCompletion.Blank
        filled < total -> ItemCompletion.Partial
        else -> ItemCompletion.Answered
    }
}
