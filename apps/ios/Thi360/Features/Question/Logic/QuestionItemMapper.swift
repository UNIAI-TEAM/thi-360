import Foundation

enum AttemptQuestionBodyValue: Hashable, Sendable {
    case singleChoice(Components.Schemas.SingleChoice)
    case multipleChoice(Components.Schemas.MultipleChoice)
    case shortAnswer(Components.Schemas.ShortAnswer)
    case cloze(Components.Schemas.Cloze)
    case trueFalse(Components.Schemas.TrueFalse)
    case matching(Components.Schemas.Matching)
    case essay(Components.Schemas.Essay)
    case questionSet(Components.Schemas.QuestionSet)

    var type: QuestionType {
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
}

extension AttemptQuestionBodyValue: Codable {
    private enum DiscriminatorKey: String, CodingKey {
        case type
    }

    init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: DiscriminatorKey.self)
        let rawType = try container.decode(String.self, forKey: .type)
        guard let kind = QuestionType.parse(rawType) else {
            throw DecodingError.dataCorruptedError(
                forKey: .type,
                in: container,
                debugDescription: "Unknown AttemptQuestionBody type: \(rawType)"
            )
        }
        switch kind {
        case .singleChoice:
            self = .singleChoice(try Components.Schemas.SingleChoice(from: decoder))
        case .multipleChoice:
            self = .multipleChoice(try Components.Schemas.MultipleChoice(from: decoder))
        case .shortAnswer:
            self = .shortAnswer(try Components.Schemas.ShortAnswer(from: decoder))
        case .cloze:
            self = .cloze(try Components.Schemas.Cloze(from: decoder))
        case .trueFalse:
            self = .trueFalse(try Components.Schemas.TrueFalse(from: decoder))
        case .matching:
            self = .matching(try Components.Schemas.Matching(from: decoder))
        case .essay:
            self = .essay(try Components.Schemas.Essay(from: decoder))
        case .questionSet:
            self = .questionSet(try Components.Schemas.QuestionSet(from: decoder))
        }
    }

    func encode(to encoder: any Encoder) throws {
        switch self {
        case let .singleChoice(value): try value.encode(to: encoder)
        case let .multipleChoice(value): try value.encode(to: encoder)
        case let .shortAnswer(value): try value.encode(to: encoder)
        case let .cloze(value): try value.encode(to: encoder)
        case let .trueFalse(value): try value.encode(to: encoder)
        case let .matching(value): try value.encode(to: encoder)
        case let .essay(value): try value.encode(to: encoder)
        case let .questionSet(value): try value.encode(to: encoder)
        }
    }
}

enum QuestionItemMapper {
    static func extractBodies(fromAttemptStateData data: Data) -> [String: AttemptQuestionBodyValue] {
        guard let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let parts = root["parts"] as? [[String: Any]]
        else {
            return [:]
        }
        let decoder = MobileAPIClient.makeJSONDecoder()
        var bodies: [String: AttemptQuestionBodyValue] = [:]
        for part in parts {
            guard let items = part["items"] as? [[String: Any]] else { continue }
            for item in items {
                guard let itemId = item["itemId"] as? String,
                      !itemId.isEmpty,
                      var bodyObj = item["questionBody"] as? [String: Any]
                else {
                    continue
                }
                if bodyObj["type"] == nil, let fallbackType = item["questionType"] as? String {
                    bodyObj["type"] = fallbackType
                }
                guard let bodyData = try? JSONSerialization.data(withJSONObject: bodyObj),
                      let decoded = try? decoder.decode(AttemptQuestionBodyValue.self, from: bodyData)
                else {
                    continue
                }
                bodies[itemId] = decoded
            }
        }
        return bodies
    }

    static func mapParts(fromAttemptStateData data: Data) throws -> [String: [QuestionItem]] {
        let decoder = MobileAPIClient.makeJSONDecoder()
        let response = try decoder.decode(Components.Schemas.AttemptStateResponse.self, from: data)
        let bodies = extractBodies(fromAttemptStateData: data)
        var byPart: [String: [QuestionItem]] = [:]
        for part in response.parts ?? [] {
            let partKey = part.partId ?? part.partCode ?? ""
            let mapped = mapItems(part.items ?? [], bodies: bodies)
            byPart[partKey] = mapped
        }
        return byPart
    }

    static func mapAllUnits(fromAttemptStateData data: Data) throws -> [QuestionItem] {
        let decoder = MobileAPIClient.makeJSONDecoder()
        let response = try decoder.decode(Components.Schemas.AttemptStateResponse.self, from: data)
        let bodies = extractBodies(fromAttemptStateData: data)
        var all: [QuestionItem] = []
        for part in response.parts ?? [] {
            all.append(contentsOf: mapItems(part.items ?? [], bodies: bodies))
        }
        return all
    }

    static func mapItems(
        _ items: [Components.Schemas.AttemptItemView],
        bodies: [String: AttemptQuestionBodyValue]
    ) -> [QuestionItem] {
        var parentIds = Set<String>()
        for item in items where item.questionType == .QUESTION_SET {
            if let id = item.itemId, !id.isEmpty {
                parentIds.insert(id)
            }
        }

        var childrenByParent: [String: [Components.Schemas.AttemptItemView]] = [:]
        for item in items {
            if let parentId = item.parentItemId, parentIds.contains(parentId) {
                childrenByParent[parentId, default: []].append(item)
            }
        }

        var result: [QuestionItem] = []
        for item in items {
            if let parentId = item.parentItemId, parentIds.contains(parentId) {
                continue
            }
            let itemId = item.itemId ?? ""
            if item.questionType == .QUESTION_SET {
                let childViews = childrenByParent[itemId] ?? []
                let mappedChildren = childViews.map { child in
                    let childId = child.itemId ?? ""
                    return map(item: child, body: bodies[childId])
                }
                result.append(map(item: item, body: bodies[itemId], children: mappedChildren))
            } else {
                result.append(map(item: item, body: bodies[itemId]))
            }
        }
        return result
    }

    static func map(
        item: Components.Schemas.AttemptItemView,
        body: AttemptQuestionBodyValue?,
        children: [QuestionItem] = []
    ) -> QuestionItem {
        let id = item.itemId ?? ""
        let displayNo = Int(item.displayNo ?? 1)
        let points = item.points ?? 1.0
        let stem = nonEmpty(item.content)
        let optionOrder = (item.optionOrder?.isEmpty == false) ? item.optionOrder : nil
        let resolvedType = QuestionType.parse(item.questionType?.rawValue) ?? body?.type ?? .singleChoice

        switch resolvedType {
        case .singleChoice:
            let options: [ChoiceOption]
            if case let .singleChoice(payload) = body {
                options = (payload.value2.options ?? []).map {
                    ChoiceOption(id: $0.id ?? "", content: $0.content ?? "")
                }
            } else {
                options = []
            }
            return .singleChoice(
                id: id,
                displayNo: displayNo,
                points: points,
                stem: stem,
                optionOrder: optionOrder,
                options: options
            )

        case .multipleChoice:
            let options: [ChoiceOption]
            let rule: MultipleChoiceRule?
            if case let .multipleChoice(payload) = body {
                options = (payload.value2.options ?? []).map {
                    ChoiceOption(id: $0.id ?? "", content: $0.content ?? "")
                }
                rule = payload.value2.scoringRule.flatMap(MultipleChoiceRule.init(rawValue:))
            } else {
                options = []
                rule = nil
            }
            return .multipleChoice(
                id: id,
                displayNo: displayNo,
                points: points,
                stem: stem,
                optionOrder: optionOrder,
                options: options,
                scoringRule: rule
            )

        case .shortAnswer:
            let format: AnswerFormat?
            let hint: String?
            if case let .shortAnswer(payload) = body {
                format = payload.value2.answerFormat.flatMap(AnswerFormat.init(rawValue:))
                hint = nonEmpty(payload.value2.inputHint)
            } else {
                format = nil
                hint = nil
            }
            return .shortAnswer(
                id: id,
                displayNo: displayNo,
                points: points,
                stem: stem,
                answerFormat: format,
                inputHint: hint,
                answers: []
            )

        case .cloze:
            let passage: String
            let blanks: [ClozeBlank]
            if case let .cloze(payload) = body {
                passage = payload.value2.passage ?? ""
                blanks = (payload.value2.blanks ?? []).map { ClozeBlank(id: $0.id ?? "") }
            } else {
                passage = ""
                blanks = []
            }
            return .cloze(
                id: id,
                displayNo: displayNo,
                points: points,
                stem: stem,
                passage: passage,
                blanks: blanks,
                blankNumberStart: nil
            )

        case .trueFalse:
            let statements: [TrueFalseStatement]
            if case let .trueFalse(payload) = body {
                statements = (payload.value2.statements ?? []).map {
                    TrueFalseStatement(id: $0.id ?? "", content: $0.content ?? "")
                }
            } else {
                statements = []
            }
            return .trueFalse(
                id: id,
                displayNo: displayNo,
                points: points,
                stem: stem,
                statements: statements,
                scoringMethod: nil
            )

        case .matching:
            let left: [MatchingSideItem]
            let right: [MatchingSideItem]
            let reuseRight: Bool
            if case let .matching(payload) = body {
                left = (payload.value2.left ?? []).map {
                    MatchingSideItem(id: $0.id ?? "", content: $0.content ?? "")
                }
                right = (payload.value2.right ?? []).map {
                    MatchingSideItem(id: $0.id ?? "", content: $0.content ?? "")
                }
                reuseRight = payload.value2.reuseRight ?? false
            } else {
                left = []
                right = []
                reuseRight = false
            }
            return .matching(
                id: id,
                displayNo: displayNo,
                points: points,
                stem: stem,
                optionOrder: optionOrder,
                left: left,
                right: right,
                reuseRight: reuseRight,
                pairs: nil
            )

        case .essay:
            let wordLimit: Int?
            if case let .essay(payload) = body, let limit = payload.value2.wordLimit {
                wordLimit = Int(limit)
            } else {
                wordLimit = nil
            }
            return .essay(
                id: id,
                displayNo: displayNo,
                points: points,
                stem: stem,
                wordLimit: wordLimit
            )

        case .questionSet:
            let passage: String
            let blankStart: Int?
            if case let .questionSet(payload) = body {
                passage = payload.value2.passage ?? ""
                blankStart = payload.value2.blankNumberStart.map(Int.init)
            } else {
                passage = ""
                blankStart = nil
            }
            return .questionSet(
                id: id,
                displayNo: displayNo,
                points: points,
                stem: stem,
                passage: passage,
                blankNumberStart: blankStart,
                children: children
            )
        }
    }

    static func map(
        reviewItem: Components.Schemas.AttemptReviewItem,
        children: [QuestionItem] = []
    ) -> QuestionItem {
        let id = reviewItem.itemId ?? ""
        let displayNo = Int(reviewItem.displayNo ?? reviewItem.ord ?? 1)
        let points = reviewItem.pointsMax ?? 1.0
        let stem = nonEmpty(reviewItem.content)
        let optionOrder = (reviewItem.optionOrder?.isEmpty == false) ? reviewItem.optionOrder : nil
        let body = reviewItem.body
        let expected = reviewItem.expectedAnswer
        let detail = reviewItem.gradingDetail
        let resolvedType = QuestionType.parse(reviewItem.questionType?.rawValue) ?? .singleChoice

        switch resolvedType {
        case .singleChoice:
            let expectedIds = expected?.expectedOptionIds ?? []
            let expectedSet = Set(expectedIds)
            let hasExpected = !expectedIds.isEmpty
            let options = (body?.options ?? []).enumerated().map { idx, opt in
                let optId = opt.id ?? "opt-\(idx)"
                return ChoiceOption(
                    id: optId,
                    content: opt.content ?? "",
                    correct: hasExpected ? expectedSet.contains(optId) : nil
                )
            }
            return .singleChoice(
                id: id,
                displayNo: displayNo,
                points: points,
                stem: stem,
                optionOrder: optionOrder,
                options: options
            )

        case .multipleChoice:
            let expectedIds = expected?.expectedOptionIds ?? []
            let expectedSet = Set(expectedIds)
            let hasExpected = !expectedIds.isEmpty
            let options = (body?.options ?? []).enumerated().map { idx, opt in
                let optId = opt.id ?? "opt-\(idx)"
                return ChoiceOption(
                    id: optId,
                    content: opt.content ?? "",
                    correct: hasExpected ? expectedSet.contains(optId) : nil
                )
            }
            let ruleRaw = body?.scoringRule ?? detail?.scoringRule
            return .multipleChoice(
                id: id,
                displayNo: displayNo,
                points: points,
                stem: stem,
                optionOrder: optionOrder,
                options: options,
                scoringRule: ruleRaw.flatMap(MultipleChoiceRule.init(rawValue:))
            )

        case .shortAnswer:
            return .shortAnswer(
                id: id,
                displayNo: displayNo,
                points: points,
                stem: stem,
                answerFormat: body?.answerFormat.flatMap(AnswerFormat.init(rawValue:)),
                inputHint: nonEmpty(body?.inputHint),
                answers: expected?.acceptedTexts ?? []
            )

        case .cloze:
            var keyByBlank: [String: [String]] = [:]
            for key in expected?.clozeKeys ?? [] {
                if let blankId = key.blankId {
                    keyByBlank[blankId] = key.acceptedTexts ?? []
                }
            }
            let blanks = (body?.blanks ?? []).enumerated().map { idx, blank in
                let blankId = blank.id ?? "blank-\(idx)"
                return ClozeBlank(id: blankId, answers: keyByBlank[blankId] ?? [])
            }
            return .cloze(
                id: id,
                displayNo: displayNo,
                points: points,
                stem: stem,
                passage: body?.passage ?? "",
                blanks: blanks,
                blankNumberStart: body?.blankNumberStart.map(Int.init)
            )

        case .trueFalse:
            var keyByStmt: [String: Bool] = [:]
            for key in expected?.trueFalseKeys ?? [] {
                if let stmtId = key.statementId, let expectedVal = key.expected {
                    keyByStmt[stmtId] = expectedVal
                }
            }
            let statements = (body?.statements ?? []).enumerated().map { idx, stmt in
                let stmtId = stmt.id ?? "stmt-\(idx)"
                return TrueFalseStatement(
                    id: stmtId,
                    content: stmt.content ?? "",
                    answer: keyByStmt[stmtId]
                )
            }
            let methodRaw = detail?.scoringMethod ?? body?.scoringRule
            return .trueFalse(
                id: id,
                displayNo: displayNo,
                points: points,
                stem: stem,
                statements: statements,
                scoringMethod: methodRaw.flatMap(TrueFalseScoringMethod.init(rawValue:))
            )

        case .matching:
            let left = (body?.left ?? []).enumerated().map { idx, side in
                MatchingSideItem(id: side.id ?? "left-\(idx)", content: side.content ?? "")
            }
            let right = (body?.right ?? []).enumerated().map { idx, side in
                MatchingSideItem(id: side.id ?? "right-\(idx)", content: side.content ?? "")
            }
            let pairs = (expected?.matchingKeys ?? []).compactMap { key -> MatchingPairKey? in
                guard let leftId = key.leftId, let rightId = key.rightId else { return nil }
                return MatchingPairKey(leftId: leftId, rightId: rightId)
            }
            return .matching(
                id: id,
                displayNo: displayNo,
                points: points,
                stem: stem,
                optionOrder: optionOrder,
                left: left,
                right: right,
                reuseRight: body?.reuseRight ?? detail?.reuseRight ?? false,
                pairs: pairs.isEmpty ? nil : pairs
            )

        case .essay:
            return .essay(
                id: id,
                displayNo: displayNo,
                points: points,
                stem: stem,
                wordLimit: body?.wordLimit.map(Int.init)
            )

        case .questionSet:
            return .questionSet(
                id: id,
                displayNo: displayNo,
                points: points,
                stem: stem,
                passage: body?.passage ?? "",
                blankNumberStart: body?.blankNumberStart.map(Int.init),
                children: children
            )
        }
    }

    private static func nonEmpty(_ value: String?) -> String? {
        guard let value, !value.isEmpty else { return nil }
        return value
    }
}
