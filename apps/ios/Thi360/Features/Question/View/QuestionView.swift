import SwiftUI

struct QuestionView<Attachments: View>: View {
    let item: QuestionItem
    let answer: AnswerValue?
    let answers: [String: AnswerValue]
    let mode: QuestionMode
    let flagged: Bool
    let flaggedIds: Set<String>
    let isLoadingEssayEditor: Bool
    let essayController: EssayEditorController?
    let onAnswer: (String, AnswerValue) -> Void
    let onFlag: (String, Bool) -> Void
    let assets: AttemptAssetSource?
    let formulaEngine: any FormulaEngine
    let locale: AppLocale
    let fixedWidth: CGFloat?
    @ViewBuilder let attachments: () -> Attachments

    init(
        item: QuestionItem,
        answer: AnswerValue? = nil,
        answers: [String: AnswerValue] = [:],
        mode: QuestionMode = .answering,
        flagged: Bool = false,
        flaggedIds: Set<String> = [],
        isLoadingEssayEditor: Bool = false,
        essayController: EssayEditorController? = nil,
        onAnswer: @escaping (String, AnswerValue) -> Void = { _, _ in },
        onFlag: @escaping (String, Bool) -> Void = { _, _ in },
        assets: AttemptAssetSource? = nil,
        formulaEngine: any FormulaEngine = SwaTexFormulaEngine.shared,
        locale: AppLocale = .vi,
        fixedWidth: CGFloat? = nil,
        @ViewBuilder attachments: @escaping () -> Attachments = { EmptyView() }
    ) {
        self.item = item
        self.answer = answer ?? answers[item.id]
        self.answers = answers
        self.mode = mode
        self.flagged = flagged || flaggedIds.contains(item.id)
        self.flaggedIds = flaggedIds
        self.isLoadingEssayEditor = isLoadingEssayEditor
        self.essayController = essayController
        self.onAnswer = onAnswer
        self.onFlag = onFlag
        self.assets = assets
        self.formulaEngine = formulaEngine
        self.locale = locale
        self.fixedWidth = fixedWidth
        self.attachments = attachments
    }

    init(
        item: QuestionItem,
        answer: AnswerValue?,
        mode: QuestionMode,
        flagged: Bool = false,
        onAnswer: @escaping (AnswerValue) -> Void,
        onFlag: @escaping (Bool) -> Void = { _ in },
        assets: AttemptAssetSource? = nil,
        formulaEngine: any FormulaEngine = SwaTexFormulaEngine.shared,
        locale: AppLocale = .vi,
        fixedWidth: CGFloat? = nil,
        @ViewBuilder attachments: @escaping () -> Attachments = { EmptyView() }
    ) {
        self.init(
            item: item,
            answer: answer,
            answers: answer.map { [item.id: $0] } ?? [:],
            mode: mode,
            flagged: flagged,
            flaggedIds: flagged ? [item.id] : [],
            onAnswer: { _, value in onAnswer(value) },
            onFlag: { _, next in onFlag(next) },
            assets: assets,
            formulaEngine: formulaEngine,
            locale: locale,
            fixedWidth: fixedWidth,
            attachments: attachments
        )
    }

    var body: some View {
        switch item {
        case let .singleChoice(_, _, _, _, optionOrder, options):
            ChoiceQuestionView(
                item: item,
                options: options,
                optionOrder: optionOrder,
                isMultiple: false,
                selectedIds: answer?.singleChoiceOptionId.map { [$0] } ?? [],
                mode: mode,
                flagged: flagged,
                onAnswer: { onAnswer(item.id, $0) },
                onFlag: { onFlag(item.id, $0) },
                assets: assets,
                formulaEngine: formulaEngine,
                locale: locale,
                fixedWidth: fixedWidth,
                attachments: attachments
            )

        case let .multipleChoice(_, _, _, _, optionOrder, options, _):
            ChoiceQuestionView(
                item: item,
                options: options,
                optionOrder: optionOrder,
                isMultiple: true,
                selectedIds: answer?.multipleChoiceOptionIds ?? [],
                mode: mode,
                flagged: flagged,
                onAnswer: { onAnswer(item.id, $0) },
                onFlag: { onFlag(item.id, $0) },
                assets: assets,
                formulaEngine: formulaEngine,
                locale: locale,
                fixedWidth: fixedWidth,
                attachments: attachments
            )

        case let .shortAnswer(_, _, _, _, answerFormat, inputHint, acceptedAnswers):
            ShortAnswerQuestionView(
                item: item,
                answerFormat: answerFormat,
                inputHint: inputHint,
                acceptedAnswers: acceptedAnswers,
                currentText: answer?.shortAnswerText ?? "",
                mode: mode,
                flagged: flagged,
                onAnswer: { onAnswer(item.id, $0) },
                onFlag: { onFlag(item.id, $0) },
                assets: assets,
                formulaEngine: formulaEngine,
                locale: locale,
                fixedWidth: fixedWidth,
                attachments: attachments
            )

        case let .cloze(_, _, _, _, passage, blanks, blankNumberStart):
            ClozeQuestionView(
                item: item,
                passage: passage,
                blanks: blanks,
                blankNumberStart: blankNumberStart,
                currentBlanks: answer?.clozeBlanks ?? [:],
                mode: mode,
                flagged: flagged,
                onAnswer: { onAnswer(item.id, $0) },
                onFlag: { onFlag(item.id, $0) },
                assets: assets,
                formulaEngine: formulaEngine,
                locale: locale,
                fixedWidth: fixedWidth,
                attachments: attachments
            )

        case let .trueFalse(_, _, _, _, statements, scoringMethod):
            TrueFalseQuestionView(
                item: item,
                statements: statements,
                scoringMethod: scoringMethod,
                currentStatements: answer?.trueFalseStatements ?? [:],
                mode: mode,
                flagged: flagged,
                onAnswer: { onAnswer(item.id, $0) },
                onFlag: { onFlag(item.id, $0) },
                assets: assets,
                formulaEngine: formulaEngine,
                locale: locale,
                fixedWidth: fixedWidth,
                attachments: attachments
            )

        case let .matching(_, _, _, _, optionOrder, left, right, reuseRight, pairs):
            MatchingQuestionView(
                item: item,
                optionOrder: optionOrder,
                left: left,
                right: right,
                reuseRight: reuseRight,
                pairs: pairs,
                currentPairs: answer?.matchingPairs ?? [:],
                mode: mode,
                flagged: flagged,
                onAnswer: { onAnswer(item.id, $0) },
                onFlag: { onFlag(item.id, $0) },
                assets: assets,
                formulaEngine: formulaEngine,
                locale: locale,
                fixedWidth: fixedWidth,
                attachments: attachments
            )

        case let .essay(_, _, _, _, wordLimit):
            EssayQuestionView(
                item: item,
                wordLimit: wordLimit,
                currentHtml: answer?.essayHtml ?? "",
                currentWordCount: answer?.essayWordCount,
                mode: mode,
                flagged: flagged,
                isLoadingEditor: isLoadingEssayEditor,
                externalController: essayController,
                onAnswer: { onAnswer(item.id, $0) },
                onFlag: { onFlag(item.id, $0) },
                assets: assets,
                formulaEngine: formulaEngine,
                locale: locale,
                fixedWidth: fixedWidth,
                attachments: attachments
            )

        case let .questionSet(_, _, _, _, passage, _, children):
            QuestionSetView(
                item: item,
                passage: passage,
                children: children,
                answers: answers,
                mode: mode,
                flaggedIds: flaggedIds,
                onAnswer: onAnswer,
                onFlag: onFlag,
                assets: assets,
                formulaEngine: formulaEngine,
                locale: locale,
                fixedWidth: fixedWidth,
                attachments: attachments
            )
        }
    }
}
