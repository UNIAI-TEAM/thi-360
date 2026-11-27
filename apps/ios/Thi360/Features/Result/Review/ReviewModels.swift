import Foundation

struct ReviewPartItem: Identifiable, Equatable, Hashable, Sendable {
    let id: String
    let partId: String?
    let partCode: String?
    let partName: String?
    let ord: Int
    let state: String?
    let pendingReview: Bool
    let score: Double?
    let maxScore: Double?

    var isSkipped: Bool {
        state == "SKIPPED"
    }

    var displayTitle: String {
        if let partName, !partName.isEmpty { return partName }
        if let partCode, !partCode.isEmpty { return partCode }
        return "—"
    }

    var stableKey: String {
        partCode ?? partId ?? id
    }
}

struct ReviewEssayCriterionRow: Identifiable, Equatable, Hashable, Sendable {
    let id: String
    let index: Int
    let criterionId: String?
    let points: Double?
    let comment: String?
}

struct ReviewEssayCriteriaSectionModel: Identifiable, Equatable, Hashable, Sendable {
    let id: String
    let childDisplayNo: Int?
    let criteria: [ReviewEssayCriterionRow]
}

struct ReviewGroup: Identifiable, Equatable, Hashable, Sendable {
    let id: String
    let partId: String?
    let partCode: String?
    let displayNo: Int
    let rangeFrom: Int?
    let rangeTo: Int?
    let outcome: QuestionOutcome
    let item: QuestionItem
    let studentAnswer: AnswerValue?
    let childAnswers: [String: AnswerValue]
    let mode: QuestionMode
    let essayCriteria: [ReviewEssayCriteriaSectionModel]

    var badgeText: String {
        if let rangeFrom, let rangeTo {
            return "\(rangeFrom)–\(rangeTo)"
        }
        return String(displayNo)
    }

    func accessibleTitle(locale: AppLocale) -> String {
        if let rangeFrom, let rangeTo {
            return ResultStrings.text(
                "review.questionRangeLabel",
                locale: locale,
                replacements: ["from": String(rangeFrom), "to": String(rangeTo)]
            )
        }
        return ResultStrings.text(
            "review.questionLabel",
            locale: locale,
            replacements: ["no": String(displayNo)]
        )
    }
}

struct ReviewPaletteSummary: Equatable, Hashable, Sendable {
    let correct: Int
    let partial: Int
    let wrong: Int
    let blank: Int
}

struct ReviewModel: Equatable, Sendable {
    let attemptId: String
    let state: AttemptResultState?
    let resultView: AttemptResultViewLevel
    let selectedPartCode: String?
    let parts: [ReviewPartItem]
    let groups: [ReviewGroup]
    var resultSummary: AttemptResult?

    func groups(for part: ReviewPartItem?) -> [ReviewGroup] {
        guard let part else { return groups }
        return groups.filter { group in
            if let pId = part.partId, let gId = group.partId, pId == gId {
                return true
            }
            if let pCode = part.partCode, let gCode = group.partCode, pCode == gCode {
                return true
            }
            return false
        }
    }

    static func paletteSummary(for groups: [ReviewGroup]) -> ReviewPaletteSummary {
        var correct = 0
        var partial = 0
        var wrong = 0
        var blank = 0

        func tally(_ outcome: QuestionOutcome?) {
            switch outcome {
            case .correct:
                correct += 1
            case .partial:
                partial += 1
            case .wrong:
                wrong += 1
            case .blank, .pendingReview, .none:
                blank += 1
            }
        }

        for group in groups {
            if case let .questionSet(_, _, _, _, _, _, children) = group.item, !children.isEmpty {
                let childReviews = group.mode.reviewData?.children ?? [:]
                for child in children {
                    tally(childReviews[child.id]?.outcome)
                }
            } else {
                tally(group.outcome)
            }
        }

        return ReviewPaletteSummary(
            correct: correct,
            partial: partial,
            wrong: wrong,
            blank: blank
        )
    }
}
