import Foundation

enum AnswerValues {
    static func stripNullBytes(_ value: String) -> String {
        value.replacingOccurrences(of: "\u{0000}", with: "")
    }

    static func singleChoice(item: QuestionItem, optionId: String?) -> AnswerValue {
        guard case let .singleChoice(_, _, _, _, _, options) = item else {
            return .singleChoice(optionId: nil)
        }
        guard let optionId else {
            return .singleChoice(optionId: nil)
        }
        let cleaned = stripNullBytes(optionId)
        let validIds = Set(options.map(\.id))
        return .singleChoice(optionId: validIds.contains(cleaned) ? cleaned : nil)
    }

    static func multipleChoice(item: QuestionItem, optionIds: [String]) -> AnswerValue {
        guard case let .multipleChoice(_, _, _, _, _, options, _) = item else {
            return .multipleChoice(optionIds: [])
        }
        let validIds = Set(options.map(\.id))
        var seen = Set<String>()
        var filtered: [String] = []
        filtered.reserveCapacity(optionIds.count)
        for rawId in optionIds {
            let cleaned = stripNullBytes(rawId)
            if validIds.contains(cleaned), seen.insert(cleaned).inserted {
                filtered.append(cleaned)
            }
        }
        return .multipleChoice(optionIds: filtered)
    }

    static func shortAnswer(text: String) -> AnswerValue {
        .shortAnswer(text: stripNullBytes(text))
    }

    static func cloze(item: QuestionItem, blanks: [(id: String, text: String)]) -> AnswerValue {
        guard case let .cloze(_, _, _, _, _, itemBlanks, _) = item else {
            return .cloze(blanks: [:])
        }
        let validIds = Set(itemBlanks.map(\.id))
        var map: [String: String] = [:]
        for entry in blanks {
            let cleanedId = stripNullBytes(entry.id)
            if validIds.contains(cleanedId) {
                map[cleanedId] = stripNullBytes(entry.text)
            }
        }
        return .cloze(blanks: map)
    }

    static func cloze(item: QuestionItem, blanks: [String: String]) -> AnswerValue {
        guard case let .cloze(_, _, _, _, _, itemBlanks, _) = item else {
            return .cloze(blanks: [:])
        }
        var ordered: [(id: String, text: String)] = []
        ordered.reserveCapacity(blanks.count)
        for blank in itemBlanks {
            if let text = blanks[blank.id] {
                ordered.append((blank.id, text))
            }
        }
        return cloze(item: item, blanks: ordered)
    }

    static func orderedClozeEntries(item: QuestionItem, blanks: [String: String]) -> [(id: String, text: String)] {
        guard case let .cloze(_, _, _, _, _, itemBlanks, _) = item else {
            return []
        }
        return itemBlanks.compactMap { blank in
            guard let text = blanks[blank.id] else { return nil }
            return (blank.id, stripNullBytes(text))
        }
    }

    static func trueFalse(item: QuestionItem, statements: [String: Bool]) -> AnswerValue {
        guard case let .trueFalse(_, _, _, _, itemStatements, _) = item else {
            return .trueFalse(statements: [:])
        }
        let validIds = Set(itemStatements.map(\.id))
        var filtered: [String: Bool] = [:]
        for (rawId, value) in statements {
            let cleanedId = stripNullBytes(rawId)
            if validIds.contains(cleanedId) {
                filtered[cleanedId] = value
            }
        }
        return .trueFalse(statements: filtered)
    }

    static func trueFalse(item: QuestionItem, statements: [String: Bool?]) -> AnswerValue {
        var nonNil: [String: Bool] = [:]
        for (key, value) in statements {
            if let value {
                nonNil[key] = value
            }
        }
        return trueFalse(item: item, statements: nonNil)
    }

    static func matching(item: QuestionItem, pairs: [String: String]) -> AnswerValue {
        guard case let .matching(_, _, _, _, _, left, right, reuseRight, _) = item else {
            return .matching(pairs: [:])
        }
        let validRightIds = Set(right.map(\.id))
        var usedRight = Set<String>()
        var filtered: [String: String] = [:]
        for leftItem in left {
            guard let rawRight = pairs[leftItem.id] else { continue }
            let cleanedRight = stripNullBytes(rawRight)
            guard !cleanedRight.isEmpty, validRightIds.contains(cleanedRight) else { continue }
            if !reuseRight && !usedRight.insert(cleanedRight).inserted {
                continue
            }
            filtered[leftItem.id] = cleanedRight
        }
        return .matching(pairs: filtered)
    }

    static func matching(item: QuestionItem, pairs: [String: String?]) -> AnswerValue {
        var nonNil: [String: String] = [:]
        for (key, value) in pairs {
            if let value, !value.isEmpty {
                nonNil[key] = value
            }
        }
        return matching(item: item, pairs: nonNil)
    }

    static func essay(html: String, wordCount: Int? = nil) -> AnswerValue {
        let cleaned = stripNullBytes(html)
        let plain = WordCounter.textForWordCount(html: cleaned).trimmingCharacters(in: .whitespacesAndNewlines)
        if cleaned.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || plain.isEmpty {
            return .essay(html: "", wordCount: 0)
        }
        let resolvedCount = wordCount ?? WordCounter.count(html: cleaned)
        return .essay(html: cleaned, wordCount: Int32(max(0, resolvedCount)))
    }
}

extension AnswerValue {
    var singleChoiceOptionId: String? {
        guard case let .singleChoice(payload) = self else { return nil }
        return payload.value2.optionId
    }

    var multipleChoiceOptionIds: [String] {
        guard case let .multipleChoice(payload) = self else { return [] }
        return payload.value2.optionIds ?? []
    }

    var shortAnswerText: String {
        guard case let .shortAnswer(payload) = self else { return "" }
        return payload.value2.text ?? ""
    }

    var clozeBlanks: [String: String] {
        guard case let .cloze(payload) = self else { return [:] }
        return payload.value2.blanks?.additionalProperties ?? [:]
    }

    var trueFalseStatements: [String: Bool] {
        guard case let .trueFalse(payload) = self else { return [:] }
        return payload.value2.statements?.additionalProperties ?? [:]
    }

    var matchingPairs: [String: String] {
        guard case let .matching(payload) = self else { return [:] }
        return payload.value2.pairs?.additionalProperties ?? [:]
    }

    var essayHtml: String {
        guard case let .essay(payload) = self else { return "" }
        return payload.value2.html ?? ""
    }

    var essayWordCount: Int? {
        guard case let .essay(payload) = self, let count = payload.value2.wordCount else { return nil }
        return Int(count)
    }
}
