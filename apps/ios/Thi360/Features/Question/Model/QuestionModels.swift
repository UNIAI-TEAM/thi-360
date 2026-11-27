import Foundation

enum QuestionType: String, Codable, Hashable, Sendable, CaseIterable {
    case singleChoice = "SINGLE_CHOICE"
    case multipleChoice = "MULTIPLE_CHOICE"
    case shortAnswer = "SHORT_ANSWER"
    case cloze = "CLOZE"
    case trueFalse = "TRUE_FALSE"
    case matching = "MATCHING"
    case essay = "ESSAY"
    case questionSet = "QUESTION_SET"

    static func parse(_ raw: String?) -> QuestionType? {
        guard let raw else { return nil }
        switch raw {
        case "MCQ_SINGLE":
            return .singleChoice
        case "MCQ_MULTI":
            return .multipleChoice
        default:
            return QuestionType(rawValue: raw)
        }
    }
}

enum QuestionOutcome: String, Codable, Hashable, Sendable, CaseIterable {
    case correct = "CORRECT"
    case partial = "PARTIAL"
    case wrong = "WRONG"
    case blank = "BLANK"
    case pendingReview = "PENDING_REVIEW"
}

enum AnswerFormat: String, Codable, Hashable, Sendable, CaseIterable {
    case integer = "INTEGER"
    case decimal = "DECIMAL"
    case fraction = "FRACTION"
    case text = "TEXT"
    case chemFormula = "CHEM_FORMULA"
    case chemEquation = "CHEM_EQUATION"

    var isNumericFormat: Bool {
        switch self {
        case .integer, .decimal, .fraction:
            true
        case .text, .chemFormula, .chemEquation:
            false
        }
    }

    var formatHint: String? {
        switch self {
        case .integer:
            "0-9"
        case .decimal, .fraction:
            "0-9 · ."
        case .text, .chemFormula, .chemEquation:
            nil
        }
    }
}

enum MultipleChoiceRule: String, Codable, Hashable, Sendable {
    case allOrNothing = "ALL_OR_NOTHING"
    case partial = "PARTIAL"
}

enum TrueFalseScoringMethod: String, Codable, Hashable, Sendable {
    case perStatement = "PER_STATEMENT"
    case countTable = "COUNT_TABLE"
}

struct ChoiceOption: Identifiable, Hashable, Sendable {
    let id: String
    let content: String
    let correct: Bool?

    init(id: String, content: String, correct: Bool? = nil) {
        self.id = id
        self.content = content
        self.correct = correct
    }
}

struct ClozeBlank: Identifiable, Hashable, Sendable {
    let id: String
    let answers: [String]
    let points: Double?

    init(id: String, answers: [String] = [], points: Double? = nil) {
        self.id = id
        self.answers = answers
        self.points = points
    }
}

struct TrueFalseStatement: Identifiable, Hashable, Sendable {
    let id: String
    let content: String
    let answer: Bool?
    let points: Double?

    init(id: String, content: String, answer: Bool? = nil, points: Double? = nil) {
        self.id = id
        self.content = content
        self.answer = answer
        self.points = points
    }
}

struct MatchingSideItem: Identifiable, Hashable, Sendable {
    let id: String
    let content: String
}

struct MatchingPairKey: Hashable, Sendable {
    let leftId: String
    let rightId: String
    let points: Double?

    init(leftId: String, rightId: String, points: Double? = nil) {
        self.leftId = leftId
        self.rightId = rightId
        self.points = points
    }
}

indirect enum QuestionItem: Identifiable, Hashable, Sendable {
    case singleChoice(
        id: String,
        displayNo: Int,
        points: Double,
        stem: String?,
        optionOrder: [String]?,
        options: [ChoiceOption]
    )
    case multipleChoice(
        id: String,
        displayNo: Int,
        points: Double,
        stem: String?,
        optionOrder: [String]?,
        options: [ChoiceOption],
        scoringRule: MultipleChoiceRule?
    )
    case shortAnswer(
        id: String,
        displayNo: Int,
        points: Double,
        stem: String?,
        answerFormat: AnswerFormat?,
        inputHint: String?,
        answers: [String]
    )
    case cloze(
        id: String,
        displayNo: Int,
        points: Double,
        stem: String?,
        passage: String,
        blanks: [ClozeBlank],
        blankNumberStart: Int?
    )
    case trueFalse(
        id: String,
        displayNo: Int,
        points: Double,
        stem: String?,
        statements: [TrueFalseStatement],
        scoringMethod: TrueFalseScoringMethod?
    )
    case matching(
        id: String,
        displayNo: Int,
        points: Double,
        stem: String?,
        optionOrder: [String]?,
        left: [MatchingSideItem],
        right: [MatchingSideItem],
        reuseRight: Bool,
        pairs: [MatchingPairKey]?
    )
    case essay(
        id: String,
        displayNo: Int,
        points: Double,
        stem: String?,
        wordLimit: Int?
    )
    case questionSet(
        id: String,
        displayNo: Int,
        points: Double,
        stem: String?,
        passage: String,
        blankNumberStart: Int?,
        children: [QuestionItem]
    )

    var id: String {
        switch self {
        case let .singleChoice(id, _, _, _, _, _),
             let .multipleChoice(id, _, _, _, _, _, _),
             let .shortAnswer(id, _, _, _, _, _, _),
             let .cloze(id, _, _, _, _, _, _),
             let .trueFalse(id, _, _, _, _, _),
             let .matching(id, _, _, _, _, _, _, _, _),
             let .essay(id, _, _, _, _),
             let .questionSet(id, _, _, _, _, _, _):
            id
        }
    }

    var displayNo: Int {
        switch self {
        case let .singleChoice(_, displayNo, _, _, _, _),
             let .multipleChoice(_, displayNo, _, _, _, _, _),
             let .shortAnswer(_, displayNo, _, _, _, _, _),
             let .cloze(_, displayNo, _, _, _, _, _),
             let .trueFalse(_, displayNo, _, _, _, _),
             let .matching(_, displayNo, _, _, _, _, _, _, _),
             let .essay(_, displayNo, _, _, _),
             let .questionSet(_, displayNo, _, _, _, _, _):
            displayNo
        }
    }

    var points: Double {
        switch self {
        case let .singleChoice(_, _, points, _, _, _),
             let .multipleChoice(_, _, points, _, _, _, _),
             let .shortAnswer(_, _, points, _, _, _, _),
             let .cloze(_, _, points, _, _, _, _),
             let .trueFalse(_, _, points, _, _, _),
             let .matching(_, _, points, _, _, _, _, _, _),
             let .essay(_, _, points, _, _),
             let .questionSet(_, _, points, _, _, _, _):
            points
        }
    }

    var stem: String? {
        switch self {
        case let .singleChoice(_, _, _, stem, _, _),
             let .multipleChoice(_, _, _, stem, _, _, _),
             let .shortAnswer(_, _, _, stem, _, _, _),
             let .cloze(_, _, _, stem, _, _, _),
             let .trueFalse(_, _, _, stem, _, _),
             let .matching(_, _, _, stem, _, _, _, _, _),
             let .essay(_, _, _, stem, _),
             let .questionSet(_, _, _, stem, _, _, _):
            stem
        }
    }

    var optionOrder: [String]? {
        switch self {
        case let .singleChoice(_, _, _, _, optionOrder, _),
             let .multipleChoice(_, _, _, _, optionOrder, _, _),
             let .matching(_, _, _, _, optionOrder, _, _, _, _):
            optionOrder
        default:
            nil
        }
    }

    var questionType: QuestionType {
        switch self {
        case .singleChoice: .singleChoice
        case .multipleChoice: .multipleChoice
        case .shortAnswer: .shortAnswer
        case .cloze: .cloze
        case .trueFalse: .trueFalse
        case .matching: .matching
        case .essay: .essay
        case .questionSet: .questionSet
        }
    }

    var displayRange: (from: Int, to: Int) {
        switch self {
        case let .cloze(_, displayNo, _, _, _, blanks, _):
            if blanks.count > 1 {
                return (displayNo, displayNo + blanks.count - 1)
            }
            return (displayNo, displayNo)
        case let .questionSet(_, displayNo, _, _, _, _, children):
            let nos = children.map(\.displayNo).filter { $0 > 0 }
            guard let minNo = nos.min(), let maxNo = nos.max() else {
                return (displayNo, displayNo)
            }
            return (minNo, maxNo)
        default:
            return (displayNo, displayNo)
        }
    }
}

enum ReviewLevel: String, Codable, Hashable, Sendable {
    case scoreOnly = "SCORE_ONLY"
    case scoreAndAnswers = "SCORE_AND_ANSWERS"
    case fullSolution = "FULL_SOLUTION"
}

struct PartResult: Hashable, Sendable {
    let key: String
    let outcome: QuestionOutcome
    let earned: Double?
    let max: Double?

    init(key: String, outcome: QuestionOutcome, earned: Double? = nil, max: Double? = nil) {
        self.key = key
        self.outcome = outcome
        self.earned = earned
        self.max = max
    }
}

struct EssayCriterionResult: Hashable, Sendable {
    let description: String
    let earned: Double?
    let max: Double
}

enum EssayGradingState: String, Codable, Hashable, Sendable {
    case pending = "PENDING"
    case graded = "GRADED"
    case regrading = "REGRADING"
}

struct EssayReviewDetail: Hashable, Sendable {
    let state: EssayGradingState
    let wordCount: Int?
    let criteria: [EssayCriterionResult]
    let comment: String?

    init(
        state: EssayGradingState,
        wordCount: Int? = nil,
        criteria: [EssayCriterionResult] = [],
        comment: String? = nil
    ) {
        self.state = state
        self.wordCount = wordCount
        self.criteria = criteria
        self.comment = comment
    }
}

struct ItemReview: Hashable, Sendable {
    let outcome: QuestionOutcome?
    let earned: Double?
    let max: Double?
    let level: ReviewLevel
    let showAnswers: Bool
    let solutionHtml: String?
    let detail: [String: PartResult]
    let essay: EssayReviewDetail?
    let children: [String: ItemReview]

    init(
        outcome: QuestionOutcome? = nil,
        earned: Double? = nil,
        max: Double? = nil,
        level: ReviewLevel = .fullSolution,
        showAnswers: Bool? = nil,
        solutionHtml: String? = nil,
        detail: [String: PartResult] = [:],
        essay: EssayReviewDetail? = nil,
        children: [String: ItemReview] = [:]
    ) {
        self.outcome = outcome
        self.earned = earned
        self.max = max
        self.level = level
        self.showAnswers = showAnswers ?? (level != .scoreOnly)
        self.solutionHtml = solutionHtml
        self.detail = detail
        self.essay = essay
        self.children = children
    }

    var visibleSolutionHtml: String? {
        guard showAnswers, level == .fullSolution, let html = solutionHtml, !html.isEmpty else {
            return nil
        }
        return html
    }

    func childReview(for childId: String) -> ItemReview {
        if let direct = children[childId] {
            return direct
        }
        return ItemReview(
            outcome: nil,
            earned: nil,
            max: nil,
            level: level,
            showAnswers: showAnswers,
            solutionHtml: nil,
            detail: [:],
            essay: nil,
            children: [:]
        )
    }
}

enum QuestionMode: Hashable, Sendable {
    case answering
    case locked
    case review(ItemReview)

    var richContentMode: RichContentMode {
        switch self {
        case .answering: .answering
        case .locked: .locked
        case .review: .review
        }
    }

    var reviewData: ItemReview? {
        guard case let .review(data) = self else { return nil }
        return data
    }

    var isAnswering: Bool {
        if case .answering = self { return true }
        return false
    }

    var isLocked: Bool {
        if case .locked = self { return true }
        return false
    }

    var isReview: Bool {
        if case .review = self { return true }
        return false
    }
}
