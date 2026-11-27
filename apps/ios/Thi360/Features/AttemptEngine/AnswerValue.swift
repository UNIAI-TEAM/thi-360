import Foundation

enum AnswerKind: String, Codable, Hashable, Sendable, CaseIterable {
    case singleChoice = "SINGLE_CHOICE"
    case multipleChoice = "MULTIPLE_CHOICE"
    case shortAnswer = "SHORT_ANSWER"
    case cloze = "CLOZE"
    case trueFalse = "TRUE_FALSE"
    case matching = "MATCHING"
    case essay = "ESSAY"

    var saveSchedule: AnswerSaveSchedule {
        switch self {
        case .singleChoice, .multipleChoice, .trueFalse, .matching:
            return .immediate
        case .shortAnswer, .cloze:
            return .debounced(.milliseconds(800))
        case .essay:
            return .essay(debounce: .seconds(2), maxWait: .seconds(15))
        }
    }
}

enum AnswerSaveSchedule: Equatable, Sendable {
    case immediate
    case debounced(Duration)
    case essay(debounce: Duration, maxWait: Duration)
}

enum AnswerValue: Hashable, Sendable {
    case singleChoice(Components.Schemas.SingleChoiceAnswer)
    case multipleChoice(Components.Schemas.MultipleChoiceAnswer)
    case shortAnswer(Components.Schemas.ShortAnswerValue)
    case cloze(Components.Schemas.ClozeAnswer)
    case trueFalse(Components.Schemas.TrueFalseAnswer)
    case matching(Components.Schemas.MatchingAnswer)
    case essay(Components.Schemas.EssayAnswer)

    var type: AnswerKind {
        switch self {
        case .singleChoice: .singleChoice
        case .multipleChoice: .multipleChoice
        case .shortAnswer: .shortAnswer
        case .cloze: .cloze
        case .trueFalse: .trueFalse
        case .matching: .matching
        case .essay: .essay
        }
    }

    var saveSchedule: AnswerSaveSchedule {
        type.saveSchedule
    }

    var generatedDiscriminator: Components.Schemas.AnswerValue {
        Components.Schemas.AnswerValue(_type: type.rawValue)
    }

    static func singleChoice(optionId: String? = nil) -> AnswerValue {
        .singleChoice(
            Components.Schemas.SingleChoiceAnswer(
                value1: .init(_type: AnswerKind.singleChoice.rawValue),
                value2: .init(optionId: optionId)
            )
        )
    }

    static func multipleChoice(optionIds: [String]) -> AnswerValue {
        .multipleChoice(
            Components.Schemas.MultipleChoiceAnswer(
                value1: .init(_type: AnswerKind.multipleChoice.rawValue),
                value2: .init(optionIds: optionIds)
            )
        )
    }

    static func shortAnswer(text: String) -> AnswerValue {
        .shortAnswer(
            Components.Schemas.ShortAnswerValue(
                value1: .init(_type: AnswerKind.shortAnswer.rawValue),
                value2: .init(text: text)
            )
        )
    }

    static func cloze(blanks: [String: String]) -> AnswerValue {
        .cloze(
            Components.Schemas.ClozeAnswer(
                value1: .init(_type: AnswerKind.cloze.rawValue),
                value2: .init(blanks: .init(additionalProperties: blanks))
            )
        )
    }

    static func trueFalse(statements: [String: Bool]) -> AnswerValue {
        .trueFalse(
            Components.Schemas.TrueFalseAnswer(
                value1: .init(_type: AnswerKind.trueFalse.rawValue),
                value2: .init(statements: .init(additionalProperties: statements))
            )
        )
    }

    static func matching(pairs: [String: String]) -> AnswerValue {
        .matching(
            Components.Schemas.MatchingAnswer(
                value1: .init(_type: AnswerKind.matching.rawValue),
                value2: .init(pairs: .init(additionalProperties: pairs))
            )
        )
    }

    static func essay(html: String, wordCount: Int32? = nil) -> AnswerValue {
        .essay(
            Components.Schemas.EssayAnswer(
                value1: .init(_type: AnswerKind.essay.rawValue),
                value2: .init(html: html, wordCount: wordCount)
            )
        )
    }
}

extension AnswerValue: Codable {
    private enum DiscriminatorKey: String, CodingKey {
        case type
    }

    init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: DiscriminatorKey.self)
        let rawType = try container.decode(String.self, forKey: .type)
        guard let kind = AnswerKind(rawValue: rawType) else {
            throw DecodingError.dataCorruptedError(
                forKey: .type,
                in: container,
                debugDescription: "Unknown AnswerValue type: \(rawType)"
            )
        }
        switch kind {
        case .singleChoice:
            self = .singleChoice(try Components.Schemas.SingleChoiceAnswer(from: decoder))
        case .multipleChoice:
            self = .multipleChoice(try Components.Schemas.MultipleChoiceAnswer(from: decoder))
        case .shortAnswer:
            self = .shortAnswer(try Components.Schemas.ShortAnswerValue(from: decoder))
        case .cloze:
            self = .cloze(try Components.Schemas.ClozeAnswer(from: decoder))
        case .trueFalse:
            self = .trueFalse(try Components.Schemas.TrueFalseAnswer(from: decoder))
        case .matching:
            self = .matching(try Components.Schemas.MatchingAnswer(from: decoder))
        case .essay:
            self = .essay(try Components.Schemas.EssayAnswer(from: decoder))
        }
    }

    func encode(to encoder: any Encoder) throws {
        switch self {
        case let .singleChoice(value):
            try value.encode(to: encoder)
        case let .multipleChoice(value):
            try value.encode(to: encoder)
        case let .shortAnswer(value):
            try value.encode(to: encoder)
        case let .cloze(value):
            try value.encode(to: encoder)
        case let .trueFalse(value):
            try value.encode(to: encoder)
        case let .matching(value):
            try value.encode(to: encoder)
        case let .essay(value):
            try value.encode(to: encoder)
        }
    }
}
