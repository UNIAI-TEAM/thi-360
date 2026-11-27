import Foundation

enum ReviewMapper {
    static func map(
        _ payload: ReviewWirePayload,
        fallbackAttemptId: String = "",
        resultSummary: AttemptResult? = nil
    ) -> ReviewModel {
        map(
            payload.response,
            studentAnswers: payload.studentAnswers,
            fallbackAttemptId: fallbackAttemptId,
            resultSummary: resultSummary
        )
    }

    static func map(
        _ response: Components.Schemas.AttemptReviewResponse,
        studentAnswers: [String: AnswerValue] = [:],
        fallbackAttemptId: String = "",
        resultSummary: AttemptResult? = nil
    ) -> ReviewModel {
        let resultView = AttemptResultViewLevel(rawValue: response.resultView)
        let includeSolution = resultView == .fullSolution
        let reviewLevel = resultView.questionReviewLevel
        let attemptState = AttemptResultState(reviewContractState: response.state)

        let sortedParts: [ReviewPartItem] = (response.parts ?? [])
            .enumerated()
            .sorted { lhs, rhs in
                let ordL = lhs.element.ord ?? Int32.max
                let ordR = rhs.element.ord ?? Int32.max
                if ordL != ordR { return ordL < ordR }
                return lhs.offset < rhs.offset
            }
            .map { index, dto in
                ReviewPartItem(
                    id: dto.partId ?? dto.partCode ?? "part-\(index)",
                    partId: nonEmpty(dto.partId),
                    partCode: nonEmpty(dto.partCode),
                    partName: nonEmpty(dto.partName),
                    ord: Int(dto.ord ?? Int32(index)),
                    state: nonEmpty(dto.state),
                    pendingReview: dto.pendingReview ?? false,
                    score: dto.score,
                    maxScore: dto.maxScore
                )
            }

        let rawItems = response.items ?? []
        let (topLevelItems, childrenByParent) = partitionHierarchy(rawItems)

        var groups: [ReviewGroup] = []
        for (idx, item) in topLevelItems.enumerated() {
            let itemId = item.itemId ?? "item-\(item.displayNo ?? item.ord ?? Int32(idx))"

            if item.questionType == .QUESTION_SET {
                let rawChildren = (childrenByParent[itemId] ?? []).sorted(by: sortByOrdAndDisplay)
                let visibleChildren = rawChildren.filter { $0.outcome != .PENDING_REVIEW }

                if (!rawChildren.isEmpty && visibleChildren.isEmpty)
                    || (rawChildren.isEmpty && item.outcome == .PENDING_REVIEW)
                {
                    continue
                }

                var childQuestions: [QuestionItem] = []
                var childAnswers: [String: AnswerValue] = [:]
                var childReviews: [String: ItemReview] = [:]
                var childCriteriaSections: [ReviewEssayCriteriaSectionModel] = []

                for child in visibleChildren {
                    let childQuestion = QuestionItemMapper.map(reviewItem: child)
                    let childId = childQuestion.id
                    let childAnswer = studentAnswers[childId]
                    childQuestions.append(childQuestion)
                    if let childAnswer {
                        childAnswers[childId] = childAnswer
                    }
                    let childReview = buildLeafReview(
                        item: child,
                        studentAnswer: childAnswer,
                        level: reviewLevel,
                        includeSolution: includeSolution
                    )
                    childReviews[childId] = childReview

                    if includeSolution,
                       child.outcome != .BLANK,
                       let criteria = extractCriteria(from: child.essayFeedback),
                       !criteria.isEmpty
                    {
                        childCriteriaSections.append(
                            ReviewEssayCriteriaSectionModel(
                                id: childId,
                                childDisplayNo: childQuestion.displayNo,
                                criteria: criteria
                            )
                        )
                    }
                }

                let parentQuestion = QuestionItemMapper.map(reviewItem: item, children: childQuestions)
                let parentOutcome: QuestionOutcome
                if let rawOutcome = item.outcome,
                   rawOutcome != .PENDING_REVIEW,
                   let mappedOutcome = QuestionOutcome(rawValue: rawOutcome.rawValue)
                {
                    parentOutcome = mappedOutcome
                } else {
                    parentOutcome = deriveGroupOutcome(from: visibleChildren)
                }

                let parentReview = ItemReview(
                    outcome: parentOutcome,
                    earned: item.earnedPoints,
                    max: item.pointsMax ?? 1.0,
                    level: reviewLevel,
                    showAnswers: reviewLevel != .scoreOnly,
                    solutionHtml: includeSolution ? nonEmpty(item.solution) : nil,
                    detail: [:],
                    essay: nil,
                    children: childReviews
                )

                let childNos = childQuestions.map(\.displayNo).filter { $0 > 0 }
                let fromNo = childNos.min() ?? parentQuestion.displayNo
                let toNo = childNos.max() ?? parentQuestion.displayNo

                groups.append(
                    ReviewGroup(
                        id: parentQuestion.id,
                        partId: nonEmpty(item.partId),
                        partCode: nonEmpty(item.partCode),
                        displayNo: fromNo,
                        rangeFrom: fromNo != toNo ? fromNo : nil,
                        rangeTo: fromNo != toNo ? toNo : nil,
                        outcome: parentOutcome,
                        item: parentQuestion,
                        studentAnswer: nil,
                        childAnswers: childAnswers,
                        mode: .review(parentReview),
                        essayCriteria: childCriteriaSections
                    )
                )
                continue
            }

            if item.outcome == .PENDING_REVIEW {
                continue
            }

            let question = QuestionItemMapper.map(reviewItem: item)
            let answer = studentAnswers[question.id]
            let leafReview = buildLeafReview(
                item: item,
                studentAnswer: answer,
                level: reviewLevel,
                includeSolution: includeSolution
            )
            let outcome = item.outcome.flatMap { QuestionOutcome(rawValue: $0.rawValue) } ?? .blank

            let clozeBlankCount = item.questionType == .CLOZE ? (item.body?.blanks?.count ?? 0) : 0
            let rangeFrom = clozeBlankCount > 1 ? question.displayNo : nil
            let rangeTo = clozeBlankCount > 1 ? (question.displayNo + clozeBlankCount - 1) : nil

            var criteriaSections: [ReviewEssayCriteriaSectionModel] = []
            if includeSolution,
               outcome != .blank,
               let criteria = extractCriteria(from: item.essayFeedback),
               !criteria.isEmpty
            {
                criteriaSections.append(
                    ReviewEssayCriteriaSectionModel(
                        id: question.id,
                        childDisplayNo: nil,
                        criteria: criteria
                    )
                )
            }

            groups.append(
                ReviewGroup(
                    id: question.id,
                    partId: nonEmpty(item.partId),
                    partCode: nonEmpty(item.partCode),
                    displayNo: question.displayNo,
                    rangeFrom: rangeFrom,
                    rangeTo: rangeTo,
                    outcome: outcome,
                    item: question,
                    studentAnswer: answer,
                    childAnswers: [:],
                    mode: .review(leafReview),
                    essayCriteria: criteriaSections
                )
            )
        }

        return ReviewModel(
            attemptId: nonEmpty(response.attemptId) ?? fallbackAttemptId,
            state: attemptState,
            resultView: resultView,
            selectedPartCode: nonEmpty(response.selectedPartCode),
            parts: sortedParts,
            groups: groups,
            resultSummary: resultSummary
        )
    }

    static func deriveGroupOutcome(from children: [Components.Schemas.AttemptReviewItem]) -> QuestionOutcome {
        guard !children.isEmpty else { return .blank }
        let outcomes = children.map { child in
            child.outcome.flatMap { QuestionOutcome(rawValue: $0.rawValue) } ?? .blank
        }
        if outcomes.allSatisfy({ $0 == .correct }) { return .correct }
        if outcomes.allSatisfy({ $0 == .blank }) { return .blank }
        if outcomes.allSatisfy({ $0 == .wrong }) { return .wrong }
        return .partial
    }

    private static func partitionHierarchy(
        _ rawItems: [Components.Schemas.AttemptReviewItem]
    ) -> (topLevel: [Components.Schemas.AttemptReviewItem], childrenByParent: [String: [Components.Schemas.AttemptReviewItem]]) {
        var parentIds = Set<String>()
        var questionSets: [Components.Schemas.AttemptReviewItem] = []

        for item in rawItems where item.questionType == .QUESTION_SET {
            if let id = item.itemId, !id.isEmpty {
                parentIds.insert(id)
                questionSets.append(item)
            }
        }
        questionSets.sort(by: sortByOrdAndDisplay)

        var childrenByParent: [String: [Components.Schemas.AttemptReviewItem]] = [:]
        var topLevel: [Components.Schemas.AttemptReviewItem] = []
        var unmatchedChildren: [Components.Schemas.AttemptReviewItem] = []

        for item in rawItems {
            if let parentId = item.parentItemId, !parentId.isEmpty {
                if parentIds.contains(parentId) {
                    childrenByParent[parentId, default: []].append(item)
                } else {
                    unmatchedChildren.append(item)
                }
            } else {
                topLevel.append(item)
            }
        }

        // Contract fixtures anonymize UUIDs per occurrence, so child.parentItemId may not equal set.itemId;
        // pair orphaned children to the nearest preceding unfulfilled QUESTION_SET in the same part.
        if !unmatchedChildren.isEmpty {
            for child in unmatchedChildren.sorted(by: sortByOrdAndDisplay) {
                let childOrd = child.ord ?? Int32.max
                if let targetParent = questionSets.last(where: { set in
                    guard let setId = set.itemId else { return false }
                    let expectedCount = Int(set.gradingDetail?.childCount ?? 0)
                    guard expectedCount > 0 else { return false }
                    let currentCount = childrenByParent[setId]?.count ?? 0
                    guard currentCount < expectedCount else { return false }
                    let samePart = (set.partCode != nil && set.partCode == child.partCode)
                        || (set.partId != nil && set.partId == child.partId)
                    let setOrd = set.ord ?? Int32.min
                    return samePart && setOrd < childOrd
                }), let setId = targetParent.itemId {
                    childrenByParent[setId, default: []].append(child)
                } else {
                    topLevel.append(child)
                }
            }
        }

        topLevel.sort(by: sortByOrdAndDisplay)
        return (topLevel, childrenByParent)
    }

    private static func buildLeafReview(
        item: Components.Schemas.AttemptReviewItem,
        studentAnswer: AnswerValue?,
        level: ReviewLevel,
        includeSolution: Bool
    ) -> ItemReview {
        let outcome = item.outcome.flatMap { QuestionOutcome(rawValue: $0.rawValue) }
        let resolvedMax = item.pointsMax ?? 1.0
        let detailMap = buildPartResults(item: item, studentAnswer: studentAnswer, resolvedMax: resolvedMax)

        var essayDetail: EssayReviewDetail?
        if item.questionType == .ESSAY {
            let wordCount: Int? = studentAnswer?.essayWordCount
                ?? item.gradingDetail?.wordCount.map(Int.init)
                ?? (studentAnswer.map { WordCounter.count(html: $0.essayHtml) })
            let state: EssayGradingState = outcome == .pendingReview ? .pending : .graded
            let comment = (includeSolution && outcome != .blank) ? nonEmpty(item.essayFeedback?.comment) : nil
            essayDetail = EssayReviewDetail(
                state: state,
                wordCount: wordCount,
                criteria: [],
                comment: comment
            )
        }

        return ItemReview(
            outcome: outcome,
            earned: item.earnedPoints,
            max: resolvedMax,
            level: level,
            showAnswers: level != .scoreOnly,
            solutionHtml: includeSolution ? nonEmpty(item.solution) : nil,
            detail: detailMap,
            essay: essayDetail,
            children: [:]
        )
    }

    private static func buildPartResults(
        item: Components.Schemas.AttemptReviewItem,
        studentAnswer: AnswerValue?,
        resolvedMax: Double
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
            let pairMax = resolvedMax / Double(pairs.count)
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

    private static func extractCriteria(
        from feedback: Components.Schemas.AttemptReviewEssayFeedback?
    ) -> [ReviewEssayCriterionRow]? {
        guard let rawCriteria = feedback?.criteria, !rawCriteria.isEmpty else {
            return nil
        }
        return rawCriteria.enumerated().map { idx, criterion in
            let id = nonEmpty(criterion.id)
            return ReviewEssayCriterionRow(
                id: id ?? "criterion-\(idx + 1)",
                index: idx + 1,
                criterionId: id,
                points: criterion.points,
                comment: nonEmpty(criterion.comment)
            )
        }
    }

    private static func sortByOrdAndDisplay(
        _ a: Components.Schemas.AttemptReviewItem,
        _ b: Components.Schemas.AttemptReviewItem
    ) -> Bool {
        let ordA = a.ord ?? Int32.max
        let ordB = b.ord ?? Int32.max
        if ordA != ordB { return ordA < ordB }
        return (a.displayNo ?? Int32.max) < (b.displayNo ?? Int32.max)
    }

    private static func nonEmpty(_ value: String?) -> String? {
        guard let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines), !trimmed.isEmpty else {
            return nil
        }
        return trimmed
    }
}
