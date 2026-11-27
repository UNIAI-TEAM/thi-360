package vn.unicom.thi360.feature.question.model

import androidx.compose.runtime.Immutable
import java.math.BigDecimal

@Immutable
enum class QuestionType {
    SINGLE_CHOICE,
    MULTIPLE_CHOICE,
    SHORT_ANSWER,
    CLOZE,
    TRUE_FALSE,
    MATCHING,
    ESSAY,
    QUESTION_SET,
}

@Immutable
enum class AnswerFormat {
    INTEGER,
    DECIMAL,
    FRACTION,
    CHEM_FORMULA,
    CHEM_EQUATION,
    TEXT,
    ;

    companion object {
        fun fromWire(value: String?): AnswerFormat? = when (value?.uppercase()) {
            "INTEGER" -> INTEGER
            "DECIMAL" -> DECIMAL
            "FRACTION" -> FRACTION
            "CHEM_FORMULA" -> CHEM_FORMULA
            "CHEM_EQUATION" -> CHEM_EQUATION
            "TEXT" -> TEXT
            else -> null
        }
    }
}

@Immutable
enum class MultipleChoiceScoringRule {
    ALL_OR_NOTHING,
    PARTIAL,
    ;

    companion object {
        fun fromWire(value: String?): MultipleChoiceScoringRule? = when (value?.uppercase()) {
            "ALL_OR_NOTHING" -> ALL_OR_NOTHING
            "PARTIAL" -> PARTIAL
            else -> null
        }
    }
}

@Immutable
enum class TrueFalseScoringMethod {
    PER_STATEMENT,
    COUNT_TABLE,
    ;

    companion object {
        fun fromWire(value: String?): TrueFalseScoringMethod? = when (value?.uppercase()) {
            "PER_STATEMENT" -> PER_STATEMENT
            "COUNT_TABLE" -> COUNT_TABLE
            else -> null
        }
    }
}

@Immutable
data class ChoiceOptionItem(
    val id: String,
    val content: String,
)

@Immutable
data class ClozeBlankItem(
    val id: String,
    val points: BigDecimal? = null,
)

@Immutable
data class TrueFalseStatementItem(
    val id: String,
    val content: String,
    val points: BigDecimal? = null,
)

@Immutable
data class MatchingSideItem(
    val id: String,
    val content: String,
)

@Immutable
sealed interface QuestionItem {
    val id: String
    val displayNo: Int
    val stem: String
    val optionOrder: List<String>
    val points: BigDecimal?
    val type: QuestionType

    @Immutable
    data class SingleChoice(
        override val id: String,
        override val displayNo: Int,
        override val stem: String,
        override val optionOrder: List<String> = emptyList(),
        override val points: BigDecimal? = null,
        val options: List<ChoiceOptionItem>,
    ) : QuestionItem {
        override val type: QuestionType = QuestionType.SINGLE_CHOICE
    }

    @Immutable
    data class MultipleChoice(
        override val id: String,
        override val displayNo: Int,
        override val stem: String,
        override val optionOrder: List<String> = emptyList(),
        override val points: BigDecimal? = null,
        val options: List<ChoiceOptionItem>,
        val scoringRule: MultipleChoiceScoringRule? = null,
    ) : QuestionItem {
        override val type: QuestionType = QuestionType.MULTIPLE_CHOICE
    }

    @Immutable
    data class ShortAnswer(
        override val id: String,
        override val displayNo: Int,
        override val stem: String,
        override val optionOrder: List<String> = emptyList(),
        override val points: BigDecimal? = null,
        val answerFormat: AnswerFormat? = null,
        val inputHint: String? = null,
    ) : QuestionItem {
        override val type: QuestionType = QuestionType.SHORT_ANSWER
    }

    @Immutable
    data class Cloze(
        override val id: String,
        override val displayNo: Int,
        override val stem: String,
        override val optionOrder: List<String> = emptyList(),
        override val points: BigDecimal? = null,
        val passage: String,
        val blanks: List<ClozeBlankItem>,
        val blankNumberStart: Int? = null,
    ) : QuestionItem {
        override val type: QuestionType = QuestionType.CLOZE
    }

    @Immutable
    data class TrueFalse(
        override val id: String,
        override val displayNo: Int,
        override val stem: String,
        override val optionOrder: List<String> = emptyList(),
        override val points: BigDecimal? = null,
        val statements: List<TrueFalseStatementItem>,
        val scoringMethod: TrueFalseScoringMethod? = null,
    ) : QuestionItem {
        override val type: QuestionType = QuestionType.TRUE_FALSE
    }

    @Immutable
    data class Matching(
        override val id: String,
        override val displayNo: Int,
        override val stem: String,
        override val optionOrder: List<String> = emptyList(),
        override val points: BigDecimal? = null,
        val left: List<MatchingSideItem>,
        val right: List<MatchingSideItem>,
        val reuseRight: Boolean = false,
    ) : QuestionItem {
        override val type: QuestionType = QuestionType.MATCHING
    }

    @Immutable
    data class Essay(
        override val id: String,
        override val displayNo: Int,
        override val stem: String,
        override val optionOrder: List<String> = emptyList(),
        override val points: BigDecimal? = null,
        val wordLimit: Int? = null,
    ) : QuestionItem {
        override val type: QuestionType = QuestionType.ESSAY
    }

    @Immutable
    data class QuestionSet(
        override val id: String,
        override val displayNo: Int,
        override val stem: String,
        override val optionOrder: List<String> = emptyList(),
        override val points: BigDecimal? = null,
        val passage: String,
        val blankNumberStart: Int? = null,
        val children: List<QuestionItem>,
    ) : QuestionItem {
        override val type: QuestionType = QuestionType.QUESTION_SET
    }
}

@Immutable
enum class ItemOutcome {
    CORRECT,
    PARTIAL,
    WRONG,
    BLANK,
    PENDING_REVIEW,
}

@Immutable
enum class ReviewLevel {
    SCORE_ONLY,
    SCORE_AND_ANSWERS,
    FULL_SOLUTION,
    ;

    val showsAnswers: Boolean
        get() = this != SCORE_ONLY

    val showsSolution: Boolean
        get() = this == FULL_SOLUTION
}

@Immutable
data class PartReviewResult(
    val outcome: ItemOutcome,
    val earned: BigDecimal? = null,
    val max: BigDecimal? = null,
)

@Immutable
sealed interface ItemReviewKey {
    @Immutable
    data class Choice(val correctOptionIds: Set<String>) : ItemReviewKey

    @Immutable
    data class ShortAnswer(val acceptedTexts: List<String>) : ItemReviewKey

    @Immutable
    data class Cloze(val blanks: Map<String, List<String>>) : ItemReviewKey

    @Immutable
    data class TrueFalse(val statements: Map<String, Boolean>) : ItemReviewKey

    @Immutable
    data class Matching(val pairs: Map<String, String>) : ItemReviewKey
}

@Immutable
enum class EssayGradingState {
    PENDING,
    GRADED,
    REGRADING,
}

@Immutable
data class EssayCriterionReview(
    val id: String? = null,
    val description: String,
    val earned: BigDecimal? = null,
    val max: BigDecimal? = null,
    val comment: String? = null,
)

@Immutable
data class EssayReview(
    val state: EssayGradingState,
    val wordCount: Int? = null,
    val comment: String? = null,
    val criteria: List<EssayCriterionReview> = emptyList(),
)

@Immutable
data class ItemReview(
    val outcome: ItemOutcome?,
    val earned: BigDecimal?,
    val max: BigDecimal,
    val level: ReviewLevel,
    val key: ItemReviewKey? = null,
    val detail: Map<String, PartReviewResult> = emptyMap(),
    val scoringMethod: TrueFalseScoringMethod? = null,
    val solutionHtml: String? = null,
    val essay: EssayReview? = null,
    val children: Map<String, ItemReview> = emptyMap(),
) {
    val showsAnswers: Boolean
        get() = level.showsAnswers

    val effectiveSolutionHtml: String?
        get() = if (level.showsSolution && !solutionHtml.isNullOrBlank()) solutionHtml else null

    fun childReview(childId: String): ItemReview {
        val direct = children[childId]
        if (direct != null) return direct
        return ItemReview(
            outcome = null,
            earned = null,
            max = BigDecimal.ONE,
            level = level,
        )
    }
}

@Immutable
sealed interface QuestionMode {
    @Immutable
    data object Answering : QuestionMode

    @Immutable
    data object Locked : QuestionMode

    @Immutable
    data class Review(val review: ItemReview) : QuestionMode
}
