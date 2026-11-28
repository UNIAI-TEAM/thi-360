import Foundation

enum ItemCompletion: Equatable, Hashable, Sendable {
    case answered
    case partial
    case blank
}

struct CompletionDetail: Equatable, Hashable, Sendable {
    let completion: ItemCompletion
    let filled: Int
    let total: Int
}

struct EssayLimitStatus: Equatable, Hashable, Sendable {
    let isEssay: Bool
    let wordCount: Int
    let wordLimit: Int?
    let underLimit: Bool
}

enum Completion {
    static func of(
        item: Components.Schemas.AttemptItemView,
        value: AnswerValue?,
        body: AttemptQuestionBodyValue? = nil
    ) -> ItemCompletion {
        detail(item: item, value: value, body: body).completion
    }

    static func of(
        item: Components.Schemas.AttemptItemView,
        value: AnswerValue?,
        bodies: [ItemID: AttemptQuestionBodyValue]
    ) -> ItemCompletion {
        let body = item.itemId.flatMap { bodies[$0] }
        return detail(item: item, value: value, body: body).completion
    }

    static func detail(
        item: Components.Schemas.AttemptItemView,
        value: AnswerValue?,
        body: AttemptQuestionBodyValue? = nil
    ) -> CompletionDetail {
        guard let questionType = item.questionType, questionType != .QUESTION_SET else {
            return CompletionDetail(completion: .blank, filled: 0, total: 0)
        }

        switch questionType {
        case .SINGLE_CHOICE:
            let filled = value?.singleChoiceOptionId?
                .trimmingCharacters(in: .whitespacesAndNewlines)
                .isEmpty == false
            return CompletionDetail(
                completion: filled ? .answered : .blank,
                filled: filled ? 1 : 0,
                total: 1
            )

        case .MULTIPLE_CHOICE:
            let filled = !(value?.multipleChoiceOptionIds ?? []).isEmpty
            return CompletionDetail(
                completion: filled ? .answered : .blank,
                filled: filled ? 1 : 0,
                total: 1
            )

        case .SHORT_ANSWER:
            let filled = value?.shortAnswerText
                .trimmingCharacters(in: .whitespacesAndNewlines)
                .isEmpty == false
            return CompletionDetail(
                completion: filled ? .answered : .blank,
                filled: filled ? 1 : 0,
                total: 1
            )

        case .CLOZE:
            let bodyIds: [String]
            if case let .cloze(payload) = body {
                bodyIds = (payload.value2.blanks ?? [])
                    .compactMap { $0.id?.trimmingCharacters(in: .whitespacesAndNewlines) }
                    .filter { !$0.isEmpty }
            } else {
                bodyIds = []
            }
            let map = value?.clozeBlanks ?? [:]
            let total = !bodyIds.isEmpty ? bodyIds.count : map.count
            guard total > 0 else {
                return CompletionDetail(completion: .blank, filled: 0, total: 0)
            }
            let filled: Int
            if !bodyIds.isEmpty {
                filled = bodyIds.reduce(into: 0) { count, id in
                    if let text = map[id], !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        count += 1
                    }
                }
            } else {
                filled = map.values.reduce(into: 0) { count, text in
                    if !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        count += 1
                    }
                }
            }
            return CompletionDetail(
                completion: completionFromCounts(filled: filled, total: total),
                filled: filled,
                total: total
            )

        case .TRUE_FALSE:
            let bodyIds: [String]
            if case let .trueFalse(payload) = body {
                bodyIds = (payload.value2.statements ?? [])
                    .compactMap { $0.id?.trimmingCharacters(in: .whitespacesAndNewlines) }
                    .filter { !$0.isEmpty }
            } else {
                bodyIds = []
            }
            let map = value?.trueFalseStatements ?? [:]
            let total = !bodyIds.isEmpty ? bodyIds.count : map.count
            guard total > 0 else {
                return CompletionDetail(completion: .blank, filled: 0, total: 0)
            }
            let filled: Int
            if !bodyIds.isEmpty {
                filled = bodyIds.reduce(into: 0) { count, id in
                    if map[id] != nil {
                        count += 1
                    }
                }
            } else {
                filled = map.count
            }
            return CompletionDetail(
                completion: completionFromCounts(filled: filled, total: total),
                filled: filled,
                total: total
            )

        case .MATCHING:
            let bodyIds: [String]
            if case let .matching(payload) = body {
                bodyIds = (payload.value2.left ?? [])
                    .compactMap { $0.id?.trimmingCharacters(in: .whitespacesAndNewlines) }
                    .filter { !$0.isEmpty }
            } else {
                bodyIds = []
            }
            let map = value?.matchingPairs ?? [:]
            let total = !bodyIds.isEmpty ? bodyIds.count : map.count
            guard total > 0 else {
                return CompletionDetail(completion: .blank, filled: 0, total: 0)
            }
            let filled: Int
            if !bodyIds.isEmpty {
                filled = bodyIds.reduce(into: 0) { count, id in
                    if let pair = map[id], !pair.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        count += 1
                    }
                }
            } else {
                filled = map.values.reduce(into: 0) { count, pair in
                    if !pair.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        count += 1
                    }
                }
            }
            return CompletionDetail(
                completion: completionFromCounts(filled: filled, total: total),
                filled: filled,
                total: total
            )

        case .ESSAY:
            let html = value?.essayHtml ?? ""
            let words = WordCounter.count(html: html)
            let filled = words > 0
            return CompletionDetail(
                completion: filled ? .answered : .blank,
                filled: filled ? 1 : 0,
                total: 1
            )

        case .QUESTION_SET:
            return CompletionDetail(completion: .blank, filled: 0, total: 0)
        }
    }

    static func essayLimit(
        item: Components.Schemas.AttemptItemView,
        value: AnswerValue?,
        body: AttemptQuestionBodyValue? = nil
    ) -> EssayLimitStatus {
        guard item.questionType == .ESSAY else {
            return EssayLimitStatus(isEssay: false, wordCount: 0, wordLimit: nil, underLimit: false)
        }
        let wordLimit: Int?
        if case let .essay(payload) = body, let rawLimit = payload.value2.wordLimit, rawLimit > 0 {
            wordLimit = Int(rawLimit)
        } else {
            wordLimit = nil
        }
        let html = value?.essayHtml ?? ""
        let wordCount = WordCounter.count(html: html)
        return EssayLimitStatus(
            isEssay: true,
            wordCount: wordCount,
            wordLimit: wordLimit,
            underLimit: wordLimit.map { wordCount < $0 } ?? false
        )
    }

    private static func completionFromCounts(filled: Int, total: Int) -> ItemCompletion {
        if filled <= 0 { return .blank }
        if filled < total { return .partial }
        return .answered
    }
}
