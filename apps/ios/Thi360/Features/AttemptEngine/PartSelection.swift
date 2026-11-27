import Foundation

enum PartSelection {
    static let closedAttemptStates: Set<Components.Schemas.AttemptStateResponse.statePayload> = [
        .GRADING,
        .PENDING_REVIEW,
        .SCORED,
        .GRADING_ERROR
    ]

    static func isUnopenedActive(_ state: Components.Schemas.AttemptPartStateView.statePayload?) -> Bool {
        switch state {
        case .AWAITING_CHOICE, .GENERATING, .GENERATION_FAILED:
            return true
        default:
            return false
        }
    }

    static func isSelectable(_ state: Components.Schemas.AttemptPartStateView.statePayload?) -> Bool {
        state == .OPEN || isUnopenedActive(state)
    }

    static func sortedParts(from attempt: Components.Schemas.AttemptStateResponse) -> [Components.Schemas.AttemptPartStateView] {
        (attempt.parts ?? []).sorted { ($0.ord ?? 0) < ($1.ord ?? 0) }
    }

    static func firstLeafItemId(in part: Components.Schemas.AttemptPartStateView) -> ItemID? {
        (part.items ?? []).first { $0.questionType != .QUESTION_SET && ($0.itemId?.isEmpty == false) }?.itemId
    }

    static func parseServerPosition(from attempt: Components.Schemas.AttemptStateResponse) -> Position? {
        guard let raw = attempt.lastPosition?.additionalProperties.value else {
            return nil
        }
        let partId = (raw["partId"] as? String).flatMap { $0.isEmpty ? nil : $0 }
        let itemId = (raw["itemId"] as? String).flatMap { $0.isEmpty ? nil : $0 }
        guard let partId else { return nil }
        return Position(partId: partId, itemId: itemId)
    }

    static func initial(
        attempt: Components.Schemas.AttemptStateResponse,
        serverPosition: Position?,
        localPosition: Position?
    ) -> Position? {
        let parts = sortedParts(from: attempt)
        guard !parts.isEmpty else { return nil }

        let resolvedServer = serverPosition ?? parseServerPosition(from: attempt)
        let preferredPartId = resolvedServer?.partId ?? localPosition?.partId

        if let preferredPartId,
           let preferredPart = parts.first(where: { $0.partId == preferredPartId }),
           isSelectable(preferredPart.state)
        {
            let preferredItemId = resolveItemId(
                in: preferredPart,
                serverItemId: resolvedServer?.partId == preferredPartId ? resolvedServer?.itemId : nil,
                localItemId: localPosition?.partId == preferredPartId ? localPosition?.itemId : nil
            )
            return Position(partId: preferredPartId, itemId: preferredItemId)
        }

        return afterClose(attempt)
    }

    static func afterClose(_ attempt: Components.Schemas.AttemptStateResponse) -> Position? {
        let parts = sortedParts(from: attempt)
        guard !parts.isEmpty else { return nil }

        let firstUnopened = parts.first { isUnopenedActive($0.state) }
        let firstOpen = parts.first { $0.state == .OPEN }

        let chosen: Components.Schemas.AttemptPartStateView?
        if let firstUnopened,
           firstOpen == nil || (firstUnopened.ord ?? 0) < (firstOpen?.ord ?? 0)
        {
            chosen = firstUnopened
        } else if let firstOpen {
            chosen = firstOpen
        } else if let firstUnopened {
            chosen = firstUnopened
        } else {
            chosen = parts.first
        }

        guard let chosen, let partId = chosen.partId else { return nil }
        return Position(partId: partId, itemId: firstLeafItemId(in: chosen))
    }

    static func inferClosedReason(from attempt: Components.Schemas.AttemptStateResponse) -> ClosedReason? {
        if attempt.state == .ABORTED {
            return .aborted(reason: attempt.abortReason?.rawValue)
        }
        guard closedAttemptStates.contains(attempt.state) else {
            return nil
        }

        let parts = sortedParts(from: attempt)
        for part in parts {
            if part.state == .SKIPPED,
               let choiceDeadlineAt = part.choiceDeadlineAt,
               let closedAt = part.closedAt,
               closedAt >= choiceDeadlineAt
            {
                return .choiceTimeout
            }
        }
        if parts.contains(where: { $0.state == .SKIPPED && $0.choiceDeadlineAt == nil }) {
            return .timeUp
        }
        if attempt.timing == .SHARED,
           let deadlineAt = attempt.deadlineAt,
           parts.contains(where: { ($0.closedAt ?? .distantPast) >= deadlineAt })
        {
            return .timeUp
        }
        if let lastPart = parts.last,
           let closedAt = lastPart.closedAt,
           let deadlineAt = lastPart.deadlineAt,
           closedAt >= deadlineAt
        {
            return .timeUp
        }
        return .submitted
    }

    private static func resolveItemId(
        in part: Components.Schemas.AttemptPartStateView,
        serverItemId: ItemID?,
        localItemId: ItemID?
    ) -> ItemID? {
        let leafIds = Set((part.items ?? []).compactMap { item -> ItemID? in
            guard item.questionType != .QUESTION_SET else { return nil }
            return item.itemId
        })
        if let serverItemId, leafIds.contains(serverItemId) {
            return serverItemId
        }
        if let localItemId, leafIds.contains(localItemId) {
            return localItemId
        }
        return firstLeafItemId(in: part)
    }
}
