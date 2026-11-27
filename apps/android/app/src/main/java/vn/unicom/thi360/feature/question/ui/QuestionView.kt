package vn.unicom.thi360.feature.question.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import vn.unicom.thi360.core.network.generated.model.AnswerValue
import vn.unicom.thi360.core.network.generated.model.ClozeAnswer
import vn.unicom.thi360.core.network.generated.model.EssayAnswer
import vn.unicom.thi360.core.network.generated.model.MatchingAnswer
import vn.unicom.thi360.core.network.generated.model.MultipleChoiceAnswer
import vn.unicom.thi360.core.network.generated.model.ShortAnswerValue
import vn.unicom.thi360.core.network.generated.model.SingleChoiceAnswer
import vn.unicom.thi360.core.network.generated.model.TrueFalseAnswer
import vn.unicom.thi360.feature.question.model.QuestionItem
import vn.unicom.thi360.feature.question.model.QuestionMode
import vn.unicom.thi360.feature.richcontent.assets.AttemptAssetSource
import vn.unicom.thi360.feature.richcontent.formula.FormulaEngine

@Composable
fun QuestionView(
    item: QuestionItem,
    answer: AnswerValue?,
    mode: QuestionMode,
    flagged: Boolean = false,
    onAnswer: (AnswerValue) -> Unit = {},
    onFlag: (() -> Unit)? = null,
    assets: AttemptAssetSource? = null,
    formulaEngine: FormulaEngine? = null,
    attachments: (@Composable () -> Unit)? = null,
    essayEditorReady: Boolean = true,
    modifier: Modifier = Modifier,
) {
    when (item) {
        is QuestionItem.SingleChoice -> SingleChoiceQuestionView(
            item = item,
            answer = answer as? SingleChoiceAnswer,
            mode = mode,
            flagged = flagged,
            onAnswer = onAnswer,
            onFlag = onFlag,
            assets = assets,
            formulaEngine = formulaEngine,
            attachments = attachments,
            modifier = modifier,
        )
        is QuestionItem.MultipleChoice -> MultipleChoiceQuestionView(
            item = item,
            answer = answer as? MultipleChoiceAnswer,
            mode = mode,
            flagged = flagged,
            onAnswer = onAnswer,
            onFlag = onFlag,
            assets = assets,
            formulaEngine = formulaEngine,
            attachments = attachments,
            modifier = modifier,
        )
        is QuestionItem.ShortAnswer -> ShortAnswerQuestionView(
            item = item,
            answer = answer as? ShortAnswerValue,
            mode = mode,
            flagged = flagged,
            onAnswer = onAnswer,
            onFlag = onFlag,
            assets = assets,
            formulaEngine = formulaEngine,
            attachments = attachments,
            modifier = modifier,
        )
        is QuestionItem.Cloze -> ClozeQuestionView(
            item = item,
            answer = answer as? ClozeAnswer,
            mode = mode,
            flagged = flagged,
            onAnswer = onAnswer,
            onFlag = onFlag,
            assets = assets,
            formulaEngine = formulaEngine,
            attachments = attachments,
            modifier = modifier,
        )
        is QuestionItem.TrueFalse -> TrueFalseQuestionView(
            item = item,
            answer = answer as? TrueFalseAnswer,
            mode = mode,
            flagged = flagged,
            onAnswer = onAnswer,
            onFlag = onFlag,
            assets = assets,
            formulaEngine = formulaEngine,
            attachments = attachments,
            modifier = modifier,
        )
        is QuestionItem.Matching -> MatchingQuestionView(
            item = item,
            answer = answer as? MatchingAnswer,
            mode = mode,
            flagged = flagged,
            onAnswer = onAnswer,
            onFlag = onFlag,
            assets = assets,
            formulaEngine = formulaEngine,
            attachments = attachments,
            modifier = modifier,
        )
        is QuestionItem.Essay -> EssayQuestionView(
            item = item,
            answer = answer as? EssayAnswer,
            mode = mode,
            flagged = flagged,
            onAnswer = onAnswer,
            onFlag = onFlag,
            assets = assets,
            formulaEngine = formulaEngine,
            attachments = attachments,
            essayEditorReady = essayEditorReady,
            modifier = modifier,
        )
        is QuestionItem.QuestionSet -> QuestionSetView(
            item = item,
            answers = emptyMap(),
            mode = mode,
            flaggedIds = emptySet(),
            onAnswer = { _, value -> onAnswer(value) },
            onFlag = null,
            assets = assets,
            formulaEngine = formulaEngine,
            attachments = attachments,
            modifier = modifier,
        )
    }
}
