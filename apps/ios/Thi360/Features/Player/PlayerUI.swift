import Foundation
import SwiftUI

extension View {
    func playerProbe(
        _ id: String,
        label: String = "",
        value: String? = nil,
        isButton: Bool = false,
        isEnabled: Bool = true,
        isSelected: Bool = false
    ) -> some View {
        self
            .accessibilityIdentifier(id)
            .questionProbe(
                id: id,
                label: label,
                value: value,
                isButton: isButton,
                isEnabled: isEnabled,
                isSelected: isSelected
            )
    }
}
import UIKit

enum PlayerLayout: String, Codable, Equatable, Hashable, Sendable {
    case compact
    case wide
}

enum PlayerViewportKind: Equatable, Hashable, Sendable {
    case compact
    case iPadRegular
    case phoneDuoOpen

    var playerLayout: PlayerLayout {
        switch self {
        case .compact: .compact
        case .iPadRegular, .phoneDuoOpen: .wide
        }
    }

    static func resolve(
        idiom: UIUserInterfaceIdiom,
        horizontalSizeClass: UserInterfaceSizeClass?
    ) -> PlayerViewportKind {
        switch ShellLayoutResolver.resolve(idiom: idiom, horizontalSizeClass: horizontalSizeClass) {
        case .compactTabBar:
            return .compact
        case .iPadSidebar:
            return .iPadRegular
        case .phoneTwoPaneBook:
            return .phoneDuoOpen
        }
    }
}

enum PlayerGridFilter: String, Codable, CaseIterable, Equatable, Hashable, Sendable {
    case all
    case unanswered
    case flagged
}

enum PlayerDialog: String, Codable, Equatable, Hashable, Sendable {
    case none
    case finishPart
    case submit
    case leaveConfirm
}

struct PartChoiceDraft: Codable, Equatable, Hashable, Sendable {
    var optionCode: String?
    var subjectCodes: [String]

    init(optionCode: String? = nil, subjectCodes: [String] = []) {
        self.optionCode = optionCode
        self.subjectCodes = subjectCodes
    }
}

struct PlayerLocalUI: Codable, Equatable, Hashable, Sendable {
    var headerCollapsed: Bool
    var gridFilter: PlayerGridFilter
    var gridOpen: Bool
    var dialog: PlayerDialog
    var choiceDraft: PartChoiceDraft
    var choiceDraftPartId: String?

    init(
        headerCollapsed: Bool = false,
        gridFilter: PlayerGridFilter = .all,
        gridOpen: Bool = false,
        dialog: PlayerDialog = .none,
        choiceDraft: PartChoiceDraft = PartChoiceDraft(),
        choiceDraftPartId: String? = nil
    ) {
        self.headerCollapsed = headerCollapsed
        self.gridFilter = gridFilter
        self.gridOpen = gridOpen
        self.dialog = dialog
        self.choiceDraft = choiceDraft
        self.choiceDraftPartId = choiceDraftPartId
    }

    func encodedJSONString() -> String {
        guard let data = try? JSONEncoder().encode(self),
              let string = String(data: data, encoding: .utf8)
        else {
            return ""
        }
        return string
    }

    static func decoded(from jsonString: String) -> PlayerLocalUI {
        guard !jsonString.isEmpty,
              let data = jsonString.data(using: .utf8),
              let decoded = try? JSONDecoder().decode(PlayerLocalUI.self, from: data)
        else {
            return PlayerLocalUI()
        }
        return decoded
    }
}

enum PlayerPrimarySurface: Equatable, Hashable, Sendable {
    case loading
    case loadError
    case waiting
    case closed
    case evicted
    case active
}

enum ReadOnlyBannerKind: Equatable, Hashable, Sendable {
    case none
    case sessionUnavailable
    case partClosed
}

enum PlayerTopAction: Equatable, Hashable, Sendable {
    case finishPart(PartID)
    case submit
    case none
}

struct TimerUI: Equatable, Sendable {
    let visible: Bool
    let isShared: Bool
    let partOrd: Int?
    let remaining: Duration
    let formatted: String
    let parts: TimerParts
    let isUrgent: Bool
}

struct PartTabUI: Identifiable, Equatable, Sendable {
    let partId: PartID
    let ord: Int
    let name: String
    let state: Components.Schemas.AttemptPartStateView.statePayload
    let isSelected: Bool
    let isClickable: Bool
    let answeredCount: Int
    let totalCount: Int

    var id: String { partId }
}

struct GridCellUI: Identifiable, Equatable, Sendable {
    let itemId: ItemID
    let displayNo: Int
    let completion: ItemCompletion
    let flagged: Bool
    let isCurrent: Bool
    let isUnsaved: Bool
    let subjectCode: String?

    var id: String { itemId }
}

struct FinishPartSummaryUI: Equatable, Sendable {
    let partId: PartID
    let partName: String
    let answeredCount: Int
    let partialCount: Int
    let missingCount: Int
    let totalCount: Int
    let nextPartNeedsChoice: Bool
    let hasNextPart: Bool
}

struct PartMissingRowUI: Identifiable, Equatable, Sendable {
    let partId: PartID
    let ord: Int
    let partName: String
    let answeredCount: Int
    let totalCount: Int
    let missingCount: Int

    var id: String { partId }
}

struct EssayStatusSummaryUI: Equatable, Sendable {
    let totalWords: Int
    let hasUnsavedWords: Bool
}

struct EssayUnderLimitUI: Identifiable, Equatable, Sendable {
    let itemId: ItemID
    let displayNo: Int
    let wordCount: Int
    let wordLimit: Int

    var id: String { itemId }
}

struct SubmitSummaryUI: Equatable, Sendable {
    let answeredCount: Int
    let partialCount: Int
    let blankCount: Int
    let flaggedCount: Int
    let totalCount: Int
    let partBreakdown: [PartMissingRowUI]
    let essayStatus: EssayStatusSummaryUI?
    let essayWarnings: [EssayUnderLimitUI]
}

struct ChoicePanelUI: Equatable, Sendable {
    let partId: PartID
    let partOrd: Int
    let partName: String
    let metadataState: LoadState<PartChoiceMetadata>
    let matchedChoice: Components.Schemas.AfterStartChoiceView?
    let options: [Components.Schemas.ChoiceOptionView]
    let subjectsByCode: [String: Components.Schemas.ChoiceSubjectView]
    let remaining: Duration
    let isTimerVisible: Bool
    let isUrgent: Bool
    let isExpired: Bool
    let selectedOptionCode: String?
    let selectedSubjectCodes: [String]
    let requiredSubjectCount: Int
    let isLocked: Bool
    let canConfirm: Bool
    let isSending: Bool
    let errorCode: String?
}

struct GenerationPanelUI: Equatable, Sendable {
    let partId: PartID
    let partName: String
    let isFailed: Bool
    let chosenOptionName: String?
    let isLocked: Bool
    let isRetrying: Bool
    let errorCode: String?
}

struct PlayerUI: Equatable, Sendable {
    let surface: PlayerPrimarySurface
    let layout: PlayerLayout
    let loadFailureKind: LoadFailureKind?
    let canRetryLoad: Bool
    let attempt: Components.Schemas.AttemptStateResponse?
    let examTitle: String
    let closedReason: ClosedReason?
    let waitingInitialState: Components.Schemas.AttemptStateResponse.statePayload?
    let waitingAbortReason: String?
    let showTakeoverDialog: Bool
    let takeoverLastSeenAt: Date?
    let isTakeoverSending: Bool
    let takeoverErrorCode: String?
    let isOffline: Bool
    let headerCollapsed: Bool
    let saveStatus: SaveStatus
    let hasUnsavedChanges: Bool
    let timer: TimerUI
    let topAction: PlayerTopAction
    let topActionEnabled: Bool
    let readOnlyBanner: ReadOnlyBannerKind
    let canEdit: Bool
    let canUseAttachments: Bool
    let questionMode: QuestionMode
    let activePart: Components.Schemas.AttemptPartStateView?
    let activePartDisplayName: String
    let partTabs: [PartTabUI]
    let showPartTabs: Bool
    let unitGroups: [UnitGroup]
    let allGridCells: [GridCellUI]
    let filteredGridCells: [GridCellUI]
    let gridGroups: [GridGroup]
    let gridFilter: PlayerGridFilter
    let gridSheetOpen: Bool
    let currentItemId: ItemID?
    let currentDisplayIndex: Int
    let answeredInActivePart: Int
    let totalInActivePart: Int
    let choicePanel: ChoicePanelUI?
    let generationPanel: GenerationPanelUI?
    let activeDialog: PlayerDialog
    let finishPartSummary: FinishPartSummaryUI?
    let submitSummary: SubmitSummaryUI?
    let commandState: CommandState
    let subjectsByCode: [String: Components.Schemas.ChoiceSubjectView]

    func questionMode(for itemId: ItemID, answers: [ItemID: LocalAnswer]) -> QuestionMode {
        guard canEdit else { return .locked }
        if answers[itemId]?.sync == .locked {
            return .locked
        }
        return .answering
    }

    static func make(
        state: EngineState,
        layout: PlayerLayout,
        local: PlayerLocalUI,
        bodies: [ItemID: AttemptQuestionBodyValue] = [:],
        partChoiceLoad: LoadState<PartChoiceMetadata> = .loading,
        dismissedRefusalCode: String? = nil
    ) -> PlayerUI {
        let attempt = state.attempt
        let surface = resolvePrimarySurface(state)

        let loadFailureKind: LoadFailureKind?
        let canRetryLoad: Bool
        if case let .failed(kind, canRetry, _) = state.load {
            loadFailureKind = kind
            canRetryLoad = canRetry
        } else {
            loadFailureKind = nil
            canRetryLoad = false
        }

        let sortedParts = attempt.map(PartSelection.sortedParts(from:)) ?? []
        let loadedMetadata: PartChoiceMetadata?
        if case let .loaded(value, _) = partChoiceLoad {
            loadedMetadata = value
        } else {
            loadedMetadata = nil
        }

        let activePart = resolveActivePart(
            attempt: attempt,
            sortedParts: sortedParts,
            positionPartId: state.position?.partId
        )
        let activePartState = activePart?.state

        let isEditingSession: Bool
        if case .editing = state.session {
            isEditingSession = true
        } else {
            isEditingSession = false
        }
        let isSessionEditable: Bool
        switch state.session {
        case .editing, .acquiring:
            isSessionEditable = true
        case .openElsewhere, .evicted, .readOnly:
            isSessionEditable = false
        }
        let isSessionReadOnly = !isSessionEditable

        let isAttemptOpen = surface == .active
            && state.closed == nil
            && attempt?.state == .IN_PROGRESS

        let canEdit = isAttemptOpen
            && isSessionEditable
            && activePartState == .OPEN
        let canUseAttachments = canEdit && isEditingSession

        let readOnlyBanner: ReadOnlyBannerKind
        if surface != .active {
            readOnlyBanner = .none
        } else if case let .readOnly(reason) = state.session {
            switch reason {
            case .partNotOpen:
                readOnlyBanner = .partClosed
            case .attemptNotInProgress:
                readOnlyBanner = .sessionUnavailable
            case .closed:
                readOnlyBanner = .none
            }
        } else if activePartState == .CLOSED {
            readOnlyBanner = .partClosed
        } else {
            readOnlyBanner = .none
        }

        let isFreeNavigation = attempt?.navigationMode == .FREE
        let partTabs: [PartTabUI] = sortedParts.enumerated().compactMap { idx, part in
            guard let partId = part.partId, !partId.isEmpty else { return nil }
            let pState = part.state ?? .PENDING
            let leaves = UnitGrouping.leafItems(in: part)
            let answered = leaves.reduce(into: 0) { count, leaf in
                guard let id = leaf.itemId else { return }
                if Completion.of(item: leaf, value: state.answers[id]?.value, bodies: bodies) == .answered {
                    count += 1
                }
            }
            let isSelected = (partId == activePart?.partId)
            let isClickable = isFreeNavigation
                && isAttemptOpen
                && !isSessionReadOnly
                && pState == .OPEN
                && !isSelected
            return PartTabUI(
                partId: partId,
                ord: Int(part.ord ?? 0),
                name: resolvePartDisplayName(
                    part: part,
                    displayOrd: idx + 1,
                    attempt: attempt,
                    metadata: loadedMetadata
                ),
                state: pState,
                isSelected: isSelected,
                isClickable: isClickable,
                answeredCount: answered,
                totalCount: leaves.count
            )
        }

        let topAction = resolveTopAction(attempt: attempt, sortedParts: sortedParts, activePart: activePart)
        let isCommandSending: Bool
        if case .sending = state.command {
            isCommandSending = true
        } else {
            isCommandSending = false
        }
        let topActionEnabled = topAction != .none
            && isEditingSession
            && canEdit
            && !isCommandSending

        let timer = buildExamTimerUI(state: state, sortedParts: sortedParts, activePart: activePart)

        let unitGroups: [UnitGroup]
        if let activePart, activePartState == .OPEN || activePartState == .CLOSED {
            unitGroups = UnitGrouping.buildGroups(part: activePart, bodies: bodies)
        } else {
            unitGroups = []
        }

        let activeLeaves = activePart.map(UnitGrouping.leafItems(in:)) ?? []
        let currentItemId = resolveCurrentItemId(leaves: activeLeaves, preferredId: state.position?.itemId)
        let allGridCells: [GridCellUI] = activeLeaves.compactMap { leaf in
            guard let id = leaf.itemId, !id.isEmpty else { return nil }
            let localAns = state.answers[id]
            let completion = Completion.of(item: leaf, value: localAns?.value, bodies: bodies)
            let isUnsaved = localAns?.sync == .pending || localAns?.sync == .rejected
            let subjectCode = leaf.componentCode?
                .trimmingCharacters(in: .whitespacesAndNewlines)
            return GridCellUI(
                itemId: id,
                displayNo: Int(leaf.displayNo ?? 0),
                completion: completion,
                flagged: localAns?.flagged == true,
                isCurrent: id == currentItemId,
                isUnsaved: isUnsaved,
                subjectCode: (subjectCode?.isEmpty == false) ? subjectCode : nil
            )
        }

        let filteredGridCells: [GridCellUI]
        switch local.gridFilter {
        case .all:
            filteredGridCells = allGridCells
        case .unanswered:
            filteredGridCells = allGridCells.filter { $0.completion != .answered }
        case .flagged:
            filteredGridCells = allGridCells.filter(\.flagged)
        }

        let gridGroups: [GridGroup]
        if let activePart {
            gridGroups = UnitGrouping.groupGridCells(
                filteredCells: filteredGridCells,
                allPartCells: allGridCells,
                part: activePart
            )
        } else {
            gridGroups = []
        }

        let currentDisplayIndex: Int
        if let idx = allGridCells.firstIndex(where: { $0.itemId == currentItemId }) {
            currentDisplayIndex = idx + 1
        } else {
            currentDisplayIndex = allGridCells.isEmpty ? 0 : 1
        }
        let answeredInActivePart = allGridCells.reduce(into: 0) { count, cell in
            if cell.completion == .answered { count += 1 }
        }

        let subjectsByCode = loadedMetadata?.subjectsByCode ?? [:]

        let effectiveRefusalCode: String?
        if case let .refused(code) = state.command, code != dismissedRefusalCode {
            effectiveRefusalCode = code
        } else {
            effectiveRefusalCode = nil
        }

        let activeDisplayOrd: Int
        if let activePart {
            if let idx = sortedParts.firstIndex(where: { $0.partId == activePart.partId }) {
                activeDisplayOrd = idx + 1
            } else {
                let rawOrd = Int(activePart.ord ?? 0)
                activeDisplayOrd = rawOrd > 0 ? rawOrd : 1
            }
        } else {
            activeDisplayOrd = 1
        }

        let isPanelLocked = !isAttemptOpen || isSessionReadOnly

        let choicePanel: ChoicePanelUI?
        if surface == .active, let activePart, activePartState == .AWAITING_CHOICE {
            choicePanel = buildChoicePanelUI(
                state: state,
                attempt: attempt,
                part: activePart,
                displayOrd: activeDisplayOrd,
                isLocked: isPanelLocked,
                local: local,
                partChoiceLoad: partChoiceLoad,
                refusalCode: effectiveRefusalCode
            )
        } else {
            choicePanel = nil
        }

        let generationPanel: GenerationPanelUI?
        if surface == .active,
           let activePart,
           activePartState == .GENERATING || activePartState == .GENERATION_FAILED
        {
            generationPanel = buildGenerationPanelUI(
                state: state,
                attempt: attempt,
                part: activePart,
                displayOrd: activeDisplayOrd,
                isLocked: isPanelLocked,
                metadata: loadedMetadata,
                refusalCode: effectiveRefusalCode
            )
        } else {
            generationPanel = nil
        }

        let takeoverLastSeenAt: Date?
        let showTakeoverDialog: Bool
        if surface == .active, case let .openElsewhere(lastSeenAt) = state.session {
            showTakeoverDialog = true
            takeoverLastSeenAt = lastSeenAt
        } else {
            showTakeoverDialog = false
            takeoverLastSeenAt = nil
        }

        let effectiveDialog: PlayerDialog
        if showTakeoverDialog || surface != .active {
            effectiveDialog = .none
        } else if case let .unconfirmed(kind) = state.command {
            switch kind {
            case .finishPart:
                effectiveDialog = .finishPart
            case .submit:
                effectiveDialog = .submit
            }
        } else if effectiveRefusalCode != nil,
                  local.dialog == .finishPart || local.dialog == .submit
        {
            effectiveDialog = .none
        } else {
            effectiveDialog = local.dialog
        }

        let finishPartSummary: FinishPartSummaryUI?
        if effectiveDialog == .finishPart, let activePart {
            finishPartSummary = buildFinishPartSummary(
                activePart: activePart,
                activeDisplayOrd: activeDisplayOrd,
                sortedParts: sortedParts,
                attempt: attempt,
                metadata: loadedMetadata,
                state: state,
                bodies: bodies
            )
        } else {
            finishPartSummary = nil
        }

        let submitSummary: SubmitSummaryUI?
        if effectiveDialog == .submit, let attempt {
            submitSummary = buildSubmitSummary(
                sortedParts: sortedParts,
                attempt: attempt,
                metadata: loadedMetadata,
                state: state,
                bodies: bodies
            )
        } else {
            submitSummary = nil
        }

        let activePartDisplayName = activePart.map {
            resolvePartDisplayName(
                part: $0,
                displayOrd: activeDisplayOrd,
                attempt: attempt,
                metadata: loadedMetadata
            )
        } ?? ""

        let isSaveUnsavedOrSaving: Bool
        switch state.save {
        case .saving, .unsaved:
            isSaveUnsavedOrSaving = true
        case .saved, .offline:
            isSaveUnsavedOrSaving = false
        }
        let hasUnsavedChanges = isSaveUnsavedOrSaving
            || state.answers.values.contains { $0.sync == .pending || $0.sync == .rejected }

        let isTakeoverSending = (state.command == .sending(.takeover))
        let takeoverErrorCode: String? = (showTakeoverDialog || surface == .evicted)
            ? effectiveRefusalCode
            : nil

        let waitingInitialState: Components.Schemas.AttemptStateResponse.statePayload?
        if let st = attempt?.state, st == .STARTING || st == .ABORTED {
            waitingInitialState = st
        } else {
            waitingInitialState = nil
        }

        let resolvedClosedReason: ClosedReason?
        if surface == .closed {
            resolvedClosedReason = state.closed ?? attempt.flatMap(PartSelection.inferClosedReason(from:)) ?? .submitted
        } else {
            resolvedClosedReason = state.closed
        }

        return PlayerUI(
            surface: surface,
            layout: layout,
            loadFailureKind: loadFailureKind,
            canRetryLoad: canRetryLoad,
            attempt: attempt,
            examTitle: attempt?.examName ?? "",
            closedReason: resolvedClosedReason,
            waitingInitialState: waitingInitialState,
            waitingAbortReason: attempt?.abortReason?.rawValue,
            showTakeoverDialog: showTakeoverDialog,
            takeoverLastSeenAt: takeoverLastSeenAt,
            isTakeoverSending: isTakeoverSending,
            takeoverErrorCode: takeoverErrorCode,
            isOffline: state.network == .offline,
            headerCollapsed: local.headerCollapsed,
            saveStatus: state.save,
            hasUnsavedChanges: hasUnsavedChanges,
            timer: timer,
            topAction: topAction,
            topActionEnabled: topActionEnabled,
            readOnlyBanner: readOnlyBanner,
            canEdit: canEdit,
            canUseAttachments: canUseAttachments,
            questionMode: canEdit ? .answering : .locked,
            activePart: activePart,
            activePartDisplayName: activePartDisplayName,
            partTabs: partTabs,
            showPartTabs: partTabs.count > 1,
            unitGroups: unitGroups,
            allGridCells: allGridCells,
            filteredGridCells: filteredGridCells,
            gridGroups: gridGroups,
            gridFilter: local.gridFilter,
            gridSheetOpen: layout == .compact && local.gridOpen && !showTakeoverDialog && surface == .active,
            currentItemId: currentItemId,
            currentDisplayIndex: currentDisplayIndex,
            answeredInActivePart: answeredInActivePart,
            totalInActivePart: allGridCells.count,
            choicePanel: choicePanel,
            generationPanel: generationPanel,
            activeDialog: effectiveDialog,
            finishPartSummary: finishPartSummary,
            submitSummary: submitSummary,
            commandState: state.command,
            subjectsByCode: subjectsByCode
        )
    }

    private static func resolvePrimarySurface(_ state: EngineState) -> PlayerPrimarySurface {
        guard let attempt = state.attempt else {
            if case .failed = state.load {
                return .loadError
            }
            return .loading
        }
        if attempt.state == .STARTING || attempt.state == .ABORTED {
            return .waiting
        }
        if state.closed != nil || PartSelection.closedAttemptStates.contains(attempt.state) {
            return .closed
        }
        if case .evicted = state.session {
            return .evicted
        }
        return .active
    }

    private static func resolveActivePart(
        attempt: Components.Schemas.AttemptStateResponse?,
        sortedParts: [Components.Schemas.AttemptPartStateView],
        positionPartId: PartID?
    ) -> Components.Schemas.AttemptPartStateView? {
        guard let attempt, !sortedParts.isEmpty else { return nil }
        if let positionPartId,
           let matched = sortedParts.first(where: { $0.partId == positionPartId })
        {
            return matched
        }
        if let fallbackPartId = PartSelection.afterClose(attempt)?.partId,
           let matched = sortedParts.first(where: { $0.partId == fallbackPartId })
        {
            return matched
        }
        return sortedParts.first
    }

    private static func resolveCurrentItemId(
        leaves: [Components.Schemas.AttemptItemView],
        preferredId: ItemID?
    ) -> ItemID? {
        guard !leaves.isEmpty else { return nil }
        if let preferredId,
           let matched = leaves.first(where: { $0.itemId == preferredId })?.itemId
        {
            return matched
        }
        return leaves.first?.itemId
    }

    private static func resolveTopAction(
        attempt: Components.Schemas.AttemptStateResponse?,
        sortedParts: [Components.Schemas.AttemptPartStateView],
        activePart: Components.Schemas.AttemptPartStateView?
    ) -> PlayerTopAction {
        guard let attempt,
              let activePart,
              activePart.state == .OPEN,
              let activePartId = activePart.partId,
              !activePartId.isEmpty
        else {
            return .none
        }
        if attempt.navigationMode == .FREE {
            return .submit
        }
        let activeOrd = activePart.ord ?? 0
        let hasRemainingNextPart = sortedParts.contains { part in
            (part.ord ?? 0) > activeOrd && part.state != .CLOSED && part.state != .SKIPPED
        }
        return hasRemainingNextPart ? .finishPart(activePartId) : .submit
    }

    private static func buildExamTimerUI(
        state: EngineState,
        sortedParts: [Components.Schemas.AttemptPartStateView],
        activePart: Components.Schemas.AttemptPartStateView?
    ) -> TimerUI {
        let isShared: Bool
        let visible: Bool
        switch state.clock.target {
        case .attempt:
            isShared = true
            visible = true
        case .part:
            isShared = false
            visible = activePart?.state == .OPEN
        case .choice, .none:
            isShared = false
            visible = false
        }

        let remaining = visible ? max(.zero, state.clock.remaining ?? .zero) : .zero
        let displayOrd: Int?
        if !isShared && visible, let activePart {
            if let idx = sortedParts.firstIndex(where: { $0.partId == activePart.partId }) {
                displayOrd = idx + 1
            } else {
                let rawOrd = Int(activePart.ord ?? 0)
                displayOrd = rawOrd > 0 ? rawOrd : 1
            }
        } else {
            displayOrd = nil
        }

        let parts = TimerText.parts(remaining: remaining)
        return TimerUI(
            visible: visible,
            isShared: isShared,
            partOrd: displayOrd,
            remaining: remaining,
            formatted: TimerText.format(parts: parts),
            parts: parts,
            isUrgent: visible && TimerText.isUrgent(remaining: remaining, threshold: TimerText.examUrgentThreshold)
        )
    }

    private static func buildChoicePanelUI(
        state: EngineState,
        attempt: Components.Schemas.AttemptStateResponse?,
        part: Components.Schemas.AttemptPartStateView,
        displayOrd: Int,
        isLocked: Bool,
        local: PlayerLocalUI,
        partChoiceLoad: LoadState<PartChoiceMetadata>,
        refusalCode: String?
    ) -> ChoicePanelUI {
        let partId = part.partId ?? ""
        let metadata: PartChoiceMetadata?
        if case let .loaded(value, _) = partChoiceLoad {
            metadata = value
        } else {
            metadata = nil
        }
        let matched = metadata?.choice(for: part)
        let options = matched?.options ?? []

        let draftForPart = (local.choiceDraftPartId == partId) ? local.choiceDraft : PartChoiceDraft()
        let selectedCode: String?
        if let draftCode = draftForPart.optionCode,
           options.contains(where: { $0.code == draftCode })
        {
            selectedCode = draftCode
        } else if options.count == 1 {
            selectedCode = options.first?.code
        } else {
            selectedCode = nil
        }

        let selectedOption = options.first(where: { $0.code == selectedCode })
        let requiredSubjects = max(0, Int(selectedOption?.chooseSubjects ?? 0))
        let validSubjectSet = Set((selectedOption?.subjects ?? []).compactMap(\.code))
        let selectedSubjects: [String]
        if requiredSubjects > 0 {
            selectedSubjects = draftForPart.subjectCodes.filter { validSubjectSet.contains($0) }
        } else {
            selectedSubjects = []
        }

        let isChoiceCountdown: Bool
        if case .choice = state.clock.target {
            isChoiceCountdown = true
        } else {
            isChoiceCountdown = false
        }
        let remaining = isChoiceCountdown ? max(.zero, state.clock.remaining ?? .zero) : .zero
        let isExpired = isChoiceCountdown && remaining <= .zero
        let isSending = (state.command == .sending(.choice))
        let isEditingSession: Bool
        if case .editing = state.session {
            isEditingSession = true
        } else {
            isEditingSession = false
        }
        let hasSelectedCode = selectedCode?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false
        let canConfirm = isEditingSession
            && !isLocked
            && !isExpired
            && !isSending
            && selectedOption != nil
            && hasSelectedCode
            && (requiredSubjects == 0 || selectedSubjects.count == requiredSubjects)

        return ChoicePanelUI(
            partId: partId,
            partOrd: Int(part.ord ?? 3),
            partName: resolvePartDisplayName(part: part, displayOrd: displayOrd, attempt: attempt, metadata: metadata),
            metadataState: partChoiceLoad,
            matchedChoice: matched,
            options: options,
            subjectsByCode: metadata?.subjectsByCode ?? [:],
            remaining: remaining,
            isTimerVisible: isChoiceCountdown || part.choiceDeadlineAt != nil,
            isUrgent: isChoiceCountdown && !isExpired
                && TimerText.isUrgent(remaining: remaining, threshold: TimerText.choiceUrgentThreshold),
            isExpired: isExpired,
            selectedOptionCode: selectedCode,
            selectedSubjectCodes: selectedSubjects,
            requiredSubjectCount: requiredSubjects,
            isLocked: isLocked,
            canConfirm: canConfirm,
            isSending: isSending,
            errorCode: refusalCode
        )
    }

    private static func buildGenerationPanelUI(
        state: EngineState,
        attempt: Components.Schemas.AttemptStateResponse?,
        part: Components.Schemas.AttemptPartStateView,
        displayOrd: Int,
        isLocked: Bool,
        metadata: PartChoiceMetadata?,
        refusalCode: String?
    ) -> GenerationPanelUI {
        let partId = part.partId ?? ""
        let chosenName = resolveChosenOptionName(part: part, attempt: attempt, metadata: metadata)
        let basePartName = trimmedNonEmpty(part.partName)
            ?? trimmedNonEmpty(part.partCode)
            ?? "#\(displayOrd)"
        let isRetrying = (state.command == .sending(.retry))
        return GenerationPanelUI(
            partId: partId,
            partName: basePartName,
            isFailed: part.state == .GENERATION_FAILED,
            chosenOptionName: chosenName,
            isLocked: isLocked,
            isRetrying: isRetrying,
            errorCode: refusalCode
        )
    }

    private static func resolveChosenOptionName(
        part: Components.Schemas.AttemptPartStateView,
        attempt: Components.Schemas.AttemptStateResponse?,
        metadata: PartChoiceMetadata?
    ) -> String? {
        let partChoiceDict = part.choice?.additionalProperties.value
        if let directName = trimmedNonEmpty(partChoiceDict?["name"] as? String) {
            return directName
        }

        var optionCode = UnitGrouping.parseChoice(part.choice).optionCode
        if let choicesMap = attempt?.choices?.additionalProperties.value {
            let ordKey = part.ord.map(String.init)
            let rawEntry = ordKey.flatMap { choicesMap[$0] } ?? part.partCode.flatMap { choicesMap[$0] }
            if let dict = rawEntry as? [String: Any] {
                if let objName = trimmedNonEmpty(dict["name"] as? String) {
                    return objName
                }
                if optionCode == nil {
                    optionCode = trimmedNonEmpty(dict["optionCode"] as? String)
                        ?? trimmedNonEmpty(dict["code"] as? String)
                }
            } else if optionCode == nil, let str = rawEntry as? String {
                optionCode = trimmedNonEmpty(str)
            }
        }

        guard let optionCode else { return nil }
        if let matchedName = metadata?.choice(for: part)?
            .options?
            .first(where: { $0.code == optionCode })?
            .name
            .flatMap(trimmedNonEmpty)
        {
            return matchedName
        }
        return optionCode
    }

    private static func resolvePartDisplayName(
        part: Components.Schemas.AttemptPartStateView,
        displayOrd: Int,
        attempt: Components.Schemas.AttemptStateResponse?,
        metadata: PartChoiceMetadata?
    ) -> String {
        if let chosenName = resolveChosenOptionName(part: part, attempt: attempt, metadata: metadata) {
            return chosenName
        }
        if let partName = trimmedNonEmpty(part.partName) {
            return partName
        }
        if let partCode = trimmedNonEmpty(part.partCode) {
            return partCode
        }
        return "#\(displayOrd)"
    }

    private static func buildFinishPartSummary(
        activePart: Components.Schemas.AttemptPartStateView,
        activeDisplayOrd: Int,
        sortedParts: [Components.Schemas.AttemptPartStateView],
        attempt: Components.Schemas.AttemptStateResponse?,
        metadata: PartChoiceMetadata?,
        state: EngineState,
        bodies: [ItemID: AttemptQuestionBodyValue]
    ) -> FinishPartSummaryUI {
        let leaves = UnitGrouping.leafItems(in: activePart)
        var answered = 0
        var partial = 0
        var blank = 0
        for leaf in leaves {
            guard let id = leaf.itemId else { continue }
            switch Completion.of(item: leaf, value: state.answers[id]?.value, bodies: bodies) {
            case .answered: answered += 1
            case .partial: partial += 1
            case .blank: blank += 1
            }
        }
        let activeOrd = activePart.ord ?? 0
        let nextPart = sortedParts.first(where: { ($0.ord ?? 0) > activeOrd })
        let nextNeedsChoice = nextPart?.state == .AWAITING_CHOICE
        return FinishPartSummaryUI(
            partId: activePart.partId ?? "",
            partName: resolvePartDisplayName(part: activePart, displayOrd: activeDisplayOrd, attempt: attempt, metadata: metadata),
            answeredCount: answered,
            partialCount: partial,
            missingCount: blank,
            totalCount: leaves.count,
            nextPartNeedsChoice: nextNeedsChoice,
            hasNextPart: nextPart != nil
        )
    }

    private static func buildSubmitSummary(
        sortedParts: [Components.Schemas.AttemptPartStateView],
        attempt: Components.Schemas.AttemptStateResponse,
        metadata: PartChoiceMetadata?,
        state: EngineState,
        bodies: [ItemID: AttemptQuestionBodyValue]
    ) -> SubmitSummaryUI {
        var answered = 0
        var partial = 0
        var blank = 0
        var flagged = 0
        var total = 0

        var partBreakdown: [PartMissingRowUI] = []
        var hasEssay = false
        var totalEssayWords = 0
        var hasUnsavedEssay = false
        var essayWarnings: [EssayUnderLimitUI] = []

        for (idx, part) in sortedParts.enumerated() {
            let leaves = UnitGrouping.leafItems(in: part)
            guard !leaves.isEmpty else { continue }
            var partAnswered = 0
            for leaf in leaves {
                guard let id = leaf.itemId else { continue }
                let localAns = state.answers[id]
                total += 1
                if localAns?.flagged == true {
                    flagged += 1
                }
                switch Completion.of(item: leaf, value: localAns?.value, bodies: bodies) {
                case .answered:
                    answered += 1
                    partAnswered += 1
                case .partial:
                    partial += 1
                case .blank:
                    blank += 1
                }

                let essayInfo = Completion.essayLimit(item: leaf, value: localAns?.value, body: bodies[id])
                if essayInfo.isEssay {
                    hasEssay = true
                    totalEssayWords += essayInfo.wordCount
                    if localAns?.sync == .pending || localAns?.sync == .rejected {
                        hasUnsavedEssay = true
                    }
                    if essayInfo.underLimit, let wordLimit = essayInfo.wordLimit {
                        essayWarnings.append(
                            EssayUnderLimitUI(
                                itemId: id,
                                displayNo: Int(leaf.displayNo ?? 0),
                                wordCount: essayInfo.wordCount,
                                wordLimit: wordLimit
                            )
                        )
                    }
                }
            }

            let partMissing = max(0, leaves.count - partAnswered)
            partBreakdown.append(
                PartMissingRowUI(
                    partId: part.partId ?? "",
                    ord: Int(part.ord ?? 0),
                    partName: resolvePartDisplayName(part: part, displayOrd: idx + 1, attempt: attempt, metadata: metadata),
                    answeredCount: partAnswered,
                    totalCount: leaves.count,
                    missingCount: partMissing
                )
            )
        }

        return SubmitSummaryUI(
            answeredCount: answered,
            partialCount: partial,
            blankCount: blank,
            flaggedCount: flagged,
            totalCount: total,
            partBreakdown: partBreakdown,
            essayStatus: hasEssay
                ? EssayStatusSummaryUI(totalWords: totalEssayWords, hasUnsavedWords: hasUnsavedEssay)
                : nil,
            essayWarnings: essayWarnings
        )
    }

    private static func trimmedNonEmpty(_ value: String?) -> String? {
        guard let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines), !trimmed.isEmpty else {
            return nil
        }
        return trimmed
    }
}
