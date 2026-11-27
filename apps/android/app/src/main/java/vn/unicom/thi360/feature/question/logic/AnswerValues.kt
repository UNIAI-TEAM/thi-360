package vn.unicom.thi360.feature.question.logic

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import vn.unicom.thi360.core.network.generated.infrastructure.Serializer
import vn.unicom.thi360.core.network.generated.model.AnswerValue
import vn.unicom.thi360.core.network.generated.model.ClozeAnswer
import vn.unicom.thi360.core.network.generated.model.EssayAnswer
import vn.unicom.thi360.core.network.generated.model.MatchingAnswer
import vn.unicom.thi360.core.network.generated.model.MultipleChoiceAnswer
import vn.unicom.thi360.core.network.generated.model.ShortAnswerValue
import vn.unicom.thi360.core.network.generated.model.SingleChoiceAnswer
import vn.unicom.thi360.core.network.generated.model.TrueFalseAnswer
import vn.unicom.thi360.feature.question.model.QuestionItem

object AnswerValues {
    @OptIn(ExperimentalSerializationApi::class)
    val wireJson: Json = Json(Serializer.kotlinxSerializationJson) {
        explicitNulls = false
    }

    fun encodeToJson(answer: AnswerValue): String =
        wireJson.encodeToString(AnswerValue.serializer(), answer)

    fun decodeFromJson(raw: String): AnswerValue =
        wireJson.decodeFromString(AnswerValue.serializer(), raw)

    fun stripNullChars(text: String): String =
        if (text.indexOf('\u0000') >= 0) text.replace("\u0000", "") else text

    fun singleChoice(
        item: QuestionItem.SingleChoice,
        optionId: String?,
    ): SingleChoiceAnswer {
        if (optionId == null) return SingleChoiceAnswer(optionId = null)
        val valid = item.options.any { it.id == optionId }
        return SingleChoiceAnswer(optionId = if (valid) optionId else null)
    }

    fun multipleChoice(
        item: QuestionItem.MultipleChoice,
        optionIds: List<String>,
    ): MultipleChoiceAnswer {
        val validIds = item.options.mapTo(HashSet()) { it.id }
        val filtered = LinkedHashSet<String>(optionIds.size)
        for (id in optionIds) {
            if (id in validIds) {
                filtered.add(id)
            }
        }
        return MultipleChoiceAnswer(optionIds = filtered.toList())
    }

    fun shortAnswer(text: String): ShortAnswerValue =
        ShortAnswerValue(text = stripNullChars(text))

    fun cloze(
        item: QuestionItem.Cloze,
        blanks: Map<String, String>,
    ): ClozeAnswer {
        val validIds = item.blanks.mapTo(HashSet()) { it.id }
        val result = LinkedHashMap<String, String>(blanks.size)
        for ((blankId, value) in blanks) {
            if (blankId in validIds) {
                result[blankId] = stripNullChars(value)
            }
        }
        return ClozeAnswer(blanks = result)
    }

    fun trueFalse(
        item: QuestionItem.TrueFalse,
        statements: Map<String, Boolean?>,
    ): TrueFalseAnswer {
        val validIds = item.statements.mapTo(HashSet()) { it.id }
        val result = LinkedHashMap<String, Boolean>(statements.size)
        for ((statementId, picked) in statements) {
            if (picked != null && statementId in validIds) {
                result[statementId] = picked
            }
        }
        return TrueFalseAnswer(statements = result)
    }

    fun matching(
        item: QuestionItem.Matching,
        pairs: Map<String, String?>,
    ): MatchingAnswer {
        val validLeft = item.left.mapTo(HashSet()) { it.id }
        val validRight = item.right.mapTo(HashSet()) { it.id }
        val usedRight = HashSet<String>()
        val result = LinkedHashMap<String, String>(pairs.size)
        for ((leftId, rightId) in pairs) {
            if (rightId == null || leftId !in validLeft || rightId !in validRight) continue
            if (!item.reuseRight && !usedRight.add(rightId)) continue
            result[leftId] = rightId
        }
        return MatchingAnswer(pairs = result)
    }

    fun essay(
        html: String,
        wordCount: Int = WordCounter.count(stripNullChars(html)),
    ): EssayAnswer {
        val cleaned = stripNullChars(html)
        val safeWordCount = if (cleaned.isBlank()) 0 else wordCount.coerceAtLeast(0)
        return EssayAnswer(
            html = if (cleaned.isBlank()) "" else cleaned,
            wordCount = safeWordCount,
        )
    }
}
