import Foundation

struct MappedReviewEntry: Identifiable, Hashable, Sendable {
    let id: String
    let partId: String?
    let partCode: String?
    let item: QuestionItem
    let studentAnswer: AnswerValue?
    let childAnswers: [String: AnswerValue]
    let review: ItemReview
}

enum ItemReviewMapper {
    static func extractStudentAnswers(fromReviewData data: Data) -> [String: AnswerValue] {
        guard let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let items = root["items"] as? [[String: Any]]
        else {
            return [:]
        }
        let decoder = MobileAPIClient.makeJSONDecoder()
        var answers: [String: AnswerValue] = [:]
        for item in items {
            guard let itemId = item["itemId"] as? String,
                  !itemId.isEmpty,
                  let answerObj = item["studentAnswer"] as? [String: Any],
                  let answerData = try? JSONSerialization.data(withJSONObject: answerObj),
                  let decoded = try? decoder.decode(AnswerValue.self, from: answerData)
            else {
                continue
            }
            answers[itemId] = decoded
        }
        return answers
    }

    static func map(
        reviewData: Data,
        includeEssayCriteria: Bool = false
    ) throws -> [MappedReviewEntry] {
        let decoder = MobileAPIClient.makeJSONDecoder()
        let response = try decoder.decode(Components.Schemas.AttemptReviewResponse.self, from: reviewData)
        let answers = extractStudentAnswers(fromReviewData: reviewData)
        return map(
            response: response,
            studentAnswers: answers,
            includeEssayCriteria: includeEssayCriteria
        )
    }

    static func map(
        response: Components.Schemas.AttemptReviewResponse,
        studentAnswers: [String: AnswerValue] = [:],
        includeEssayCriteria: Bool = false
    ) -> [MappedReviewEntry] {
        let level = resolveReviewLevel(response.resultView)
        let rawItems = response.items ?? []

        var parentIds = Set<String>()
        for item in rawItems where item.questionType == .QUESTION_SET {
            if let id = item.itemId, !id.isEmpty {
                parentIds.insert(id)
            }
        }

        var childrenByParent: [String: [Components.Schemas.AttemptReviewItem]] = [:]
        var topLevel: [Components.Schemas.AttemptReviewItem] = []

        for item in rawItems {
            if let parentId = item.parentItemId, parentIds.contains(parentId) {
                childrenByParent[parentId, default: []].append(item)
            } else {
                topLevel.append(item)
            }
        }

        topLevel.sort(by: sortByOrdAndDisplay)

        var entries: [MappedReviewEntry] = []
        for item in topLevel {
            let itemId = item.itemId ?? ""
            if item.questionType == .QUESTION_SET {
                let rawChildren = (childrenByParent[itemId] ?? []).sorted(by: sortByOrdAndDisplay)
                var childQuestions: [QuestionItem] = []
                var childAnswers: [String: AnswerValue] = [:]
                var childReviews: [String: ItemReview] = [:]

                for child in rawChildren {
                    let childQuestion = QuestionItemMapper.map(reviewItem: child)
                    let childId = childQuestion.id
                    let childAnswer = studentAnswers[childId]
                    childQuestions.append(childQuestion)
                    if let childAnswer {
                        childAnswers[childId] = childAnswer
                    }
                    childReviews[childId] = mapLeafReview(
                        item: child,
                        studentAnswer: childAnswer,
                        level: level,
                        includeEssayCriteria: includeEssayCriteria
                    )
                }

                let parentQuestion = QuestionItemMapper.map(reviewItem: item, children: childQuestions)
                let parentReview = ItemReview(
                    outcome: item.outcome.flatMap { QuestionOutcome(rawValue: $0.rawValue) },
                    earned: item.earnedPoints,
                    max: item.pointsMax ?? 1.0,
                    level: level,
                    showAnswers: level != .scoreOnly,
                    solutionHtml: level == .fullSolution ? item.solution : nil,
                    detail: [:],
                    essay: nil,
                    children: childReviews
                )

                entries.append(
                    MappedReviewEntry(
                        id: parentQuestion.id,
                        partId: item.partId,
                        partCode: item.partCode,
                        item: parentQuestion,
                        studentAnswer: nil,
                        childAnswers: childAnswers,
                        review: parentReview
                    )
                )
            } else {
                let question = QuestionItemMapper.map(reviewItem: item)
                let answer = studentAnswers[question.id]
                let review = mapLeafReview(
                    item: item,
                    studentAnswer: answer,
                    level: level,
                    includeEssayCriteria: includeEssayCriteria
                )
                entries.append(
                    MappedReviewEntry(
                        id: question.id,
                        partId: item.partId,
                        partCode: item.partCode,
                        item: question,
                        studentAnswer: answer,
                        childAnswers: [:],
                        review: review
                    )
                )
            }
        }
        return entries
    }

    static func mapLeafReview(
        item: Components.Schemas.AttemptReviewItem,
        studentAnswer: AnswerValue?,
        level: ReviewLevel,
        includeEssayCriteria: Bool = false
    ) -> ItemReview {
        let includeSolution = level == .fullSolution
        let showAnswers = level != .scoreOnly
        let outcome = item.outcome.flatMap { QuestionOutcome(rawValue: $0.rawValue) }
        let detailMap = buildPartResults(item: item, studentAnswer: studentAnswer)

        var essayDetail: EssayReviewDetail?
        if item.questionType == .ESSAY {
            let wordCount: Int? = studentAnswer?.essayWordCount
                ?? item.gradingDetail?.wordCount.map(Int.init)
                ?? (studentAnswer.map { WordCounter.count(html: $0.essayHtml) })
            let state: EssayGradingState = outcome == .pendingReview ? .pending : .graded
            // Plan B1.7: Review screen draws the 3-column criteria table itself, so Review mapper omits criteria by default.
            let criteria: [EssayCriterionResult] = includeEssayCriteria
                ? (item.essayFeedback?.criteria ?? []).enumerated().map { idx, criterion in
                    EssayCriterionResult(
                        description: criterion.id ?? "\(idx + 1)",
                        earned: criterion.points,
                        max: criterion.points ?? 0
                    )
                }
                : []
            essayDetail = EssayReviewDetail(
                state: state,
                wordCount: wordCount,
                criteria: criteria,
                comment: includeSolution ? item.essayFeedback?.comment : nil
            )
        }

        return ItemReview(
            outcome: outcome,
            earned: item.earnedPoints,
            max: item.pointsMax ?? 1.0,
            level: level,
            showAnswers: showAnswers,
            solutionHtml: includeSolution ? item.solution : nil,
            detail: detailMap,
            essay: essayDetail,
            children: [:]
        )
    }

    private static func resolveReviewLevel(_ raw: String?) -> ReviewLevel {
        switch raw {
        case "FULL_SOLUTION":
            .fullSolution
        case "SCORE_AND_ANSWERS":
            .scoreAndAnswers
        default:
            .scoreOnly
        }
    }

    private static func buildPartResults(
        item: Components.Schemas.AttemptReviewItem,
        studentAnswer: AnswerValue?
    ) -> [String: PartResult] {
        guard let detail = item.gradingDetail else { return [:] }
        var map: [String: PartResult] = [:]

        if let blanks = detail.blankResults, !blanks.isEmpty {
            for blank in blanks {
                guard let id = blank.id else { continue }
                let outcome = blank.outcome.flatMap { QuestionOutcome(rawValue: $0.rawValue) } ?? .blank
                map[id] = PartResult(
                    key: id,
                    outcome: outcome,
                    earned: blank.pointsEarned,
                    max: blank.pointsMax
                )
            }
            return map
        }

        if let statements = detail.statementResults, !statements.isEmpty {
            for stmt in statements {
                guard let id = stmt.id else { continue }
                let outcome: QuestionOutcome
                if stmt.matched == true {
                    outcome = .correct
                } else if stmt.answered == true {
                    outcome = .wrong
                } else {
                    outcome = .blank
                }
                map[id] = PartResult(key: id, outcome: outcome, earned: nil, max: nil)
            }
            return map
        }

        if let pairs = detail.pairResults, !pairs.isEmpty {
            let studentPairs = studentAnswer?.matchingPairs ?? [:]
            let pairMax: Double? = item.pointsMax.map { $0 / Double(pairs.count) }
            for pair in pairs {
                guard let leftId = pair.leftId else { continue }
                let outcome: QuestionOutcome
                if pair.matched == true {
                    outcome = .correct
                } else if let picked = studentPairs[leftId], !picked.isEmpty {
                    outcome = .wrong
                } else {
                    outcome = .blank
                }
                map[leftId] = PartResult(
                    key: leftId,
                    outcome: outcome,
                    earned: pair.pointsEarned,
                    max: pairMax
                )
            }
            return map
        }

        return map
    }

    private static func sortByOrdAndDisplay(
        _ a: Components.Schemas.AttemptReviewItem,
        _ b: Components.Schemas.AttemptReviewItem
    ) -> Bool {
        let ordA = a.ord ?? Int32.max
        let ordB = b.ord ?? Int32.max
        if ordA != ordB {
            return ordA < ordB
        }
        return (a.displayNo ?? Int32.max) < (b.displayNo ?? Int32.max)
    }
}
