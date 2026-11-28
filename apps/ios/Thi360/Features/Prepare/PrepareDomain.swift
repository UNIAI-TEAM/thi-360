import Foundation

typealias AttemptPrepareResponse = Components.Schemas.AttemptPrepareResponse

enum PrepareConstants {
    static let reviewBuildMode = "REVIEW"
    static let servingStatusPublished = "PUBLISHED"
    static let subjectChoiceOptionKind = "SUBJECT_CHOICE"
}

struct PreparePreChoiceInput: Codable, Equatable, Hashable, Sendable {
    let optionCode: String
    let subjectCodes: [String]?

    init(optionCode: String, subjectCodes: [String]? = nil) {
        self.optionCode = optionCode
        self.subjectCodes = subjectCodes
    }

    var generatedInput: Components.Schemas.PreChoiceInput {
        Components.Schemas.PreChoiceInput(
            optionCode: optionCode,
            subjectCodes: subjectCodes
        )
    }
}

struct PreChoiceSelection: Equatable, Hashable, Sendable {
    var optionCode: String?
    var subjectCodes: Set<String>

    static let empty = PreChoiceSelection(optionCode: nil, subjectCodes: [])

    init(optionCode: String? = nil, subjectCodes: Set<String> = []) {
        self.optionCode = optionCode
        self.subjectCodes = subjectCodes
    }

    static func initial(from preChoice: Components.Schemas.PreChoiceView?) -> PreChoiceSelection {
        let options = preChoice?.options ?? []
        guard !options.isEmpty else { return .empty }

        if let preselected = preChoice?.preselectedCode?.trimmingCharacters(in: .whitespacesAndNewlines),
           !preselected.isEmpty,
           let matched = options.first(where: { $0.code == preselected }),
           let matchedCode = matched.code?.trimmingCharacters(in: .whitespacesAndNewlines),
           !matchedCode.isEmpty
        {
            return PreChoiceSelection(optionCode: matchedCode, subjectCodes: [])
        }

        if options.count == 1,
           let singleCode = options[0].code?.trimmingCharacters(in: .whitespacesAndNewlines),
           !singleCode.isEmpty
        {
            return PreChoiceSelection(optionCode: singleCode, subjectCodes: [])
        }

        return .empty
    }

    func selectingOption(_ code: String) -> PreChoiceSelection {
        PreChoiceSelection(optionCode: code, subjectCodes: [])
    }

    func togglingSubject(_ subjectCode: String, maxSelect: Int) -> PreChoiceSelection {
        if subjectCodes.contains(subjectCode) {
            var updated = subjectCodes
            updated.remove(subjectCode)
            return PreChoiceSelection(optionCode: optionCode, subjectCodes: updated)
        }
        guard maxSelect > 0, subjectCodes.count < maxSelect else {
            return self
        }
        var updated = subjectCodes
        updated.insert(subjectCode)
        return PreChoiceSelection(optionCode: optionCode, subjectCodes: updated)
    }

    func selectedOption(in preChoice: Components.Schemas.PreChoiceView?) -> Components.Schemas.ChoiceOptionView? {
        guard let code = optionCode?.trimmingCharacters(in: .whitespacesAndNewlines), !code.isEmpty else {
            return nil
        }
        return preChoice?.options?.first { $0.code == code }
    }

    static func requiredSubjectCount(for option: Components.Schemas.ChoiceOptionView?) -> Int {
        guard let option, option.kind == PrepareConstants.subjectChoiceOptionKind else {
            return 0
        }
        return max(0, Int(option.chooseSubjects ?? 0))
    }

    func toPreChoiceInput(for response: AttemptPrepareResponse) -> PreparePreChoiceInput? {
        guard let preChoice = response.preChoice,
              let selected = selectedOption(in: preChoice),
              let code = selected.code?.trimmingCharacters(in: .whitespacesAndNewlines),
              !code.isEmpty
        else {
            return nil
        }

        let required = Self.requiredSubjectCount(for: selected)
        if required > 0 {
            let orderedCodes = Self.orderedSubjects(subjectCodes, option: selected)
            return PreparePreChoiceInput(optionCode: code, subjectCodes: orderedCodes)
        }
        return PreparePreChoiceInput(optionCode: code, subjectCodes: nil)
    }

    private static func orderedSubjects(
        _ selected: Set<String>,
        option: Components.Schemas.ChoiceOptionView
    ) -> [String] {
        let optionOrder = (option.subjects ?? []).compactMap(\.code)
        var ordered: [String] = []
        var remaining = selected
        for code in optionOrder where remaining.contains(code) {
            ordered.append(code)
            remaining.remove(code)
        }
        if !remaining.isEmpty {
            ordered.append(contentsOf: remaining.sorted())
        }
        return ordered
    }
}

enum DescriptionKind: Equatable, Sendable {
    case `class`
    case published
    case discontinued
}

enum StartGate: Equatable, Sendable {
    case open
    case inProgress(attemptId: String)
    case exhausted(DescriptionKind, canBuyMore: Bool)
    case noServingOption
    case needOption
    case needSubjects(count: Int)

    static func resolve(
        response: AttemptPrepareResponse,
        selection: PreChoiceSelection
    ) -> StartGate {
        if let open = response.openAttempt,
           open.state == .IN_PROGRESS,
           let attemptId = open.attemptId?.trimmingCharacters(in: .whitespacesAndNewlines),
           !attemptId.isEmpty
        {
            return .inProgress(attemptId: attemptId)
        }

        if response.attemptsLeft <= 0 {
            let isClass = response.grantSource?.source == .CLASS
            let isPublished = response.servingStatus == PrepareConstants.servingStatusPublished
            let kind: DescriptionKind
            if isClass {
                kind = .class
            } else if isPublished {
                kind = .published
            } else {
                kind = .discontinued
            }
            return .exhausted(kind, canBuyMore: isPublished && !isClass)
        }

        if let preChoice = response.preChoice {
            let options = preChoice.options ?? []
            if options.isEmpty {
                return .noServingOption
            }
            guard let selected = selection.selectedOption(in: preChoice) else {
                return .needOption
            }
            let requiredSubjects = PreChoiceSelection.requiredSubjectCount(for: selected)
            if requiredSubjects > 0 && selection.subjectCodes.count != requiredSubjects {
                return .needSubjects(count: requiredSubjects)
            }
        }

        return .open
    }
}

enum StartRefusal: Equatable, Sendable {
    case noAttemptsLeft
    case choiceNotAllowed
    case noServingOption
    case subjectsInvalid
    case rateLimited
    case generic
}

enum StartState: Equatable, Sendable {
    case idle
    case sending
    case refused(StartRefusal)
}

enum AttemptOpenState: Equatable, Sendable {
    case starting
    case inProgress
    case aborted(abortReason: String?)
    case closed
}

enum StartOutcome: Equatable, Sendable {
    case opened(attemptId: String, AttemptOpenState)
    case refused(StartRefusal)
}

enum WaitingPhase: Equatable, Sendable {
    case polling(elapsed: TimeInterval)
    case busy
    case failed(abortReason: String?)
    case retrying
    case retryRefused(StartRefusal)
}

enum WaitingOrigin: Equatable, Sendable {
    case prepare
    case player
}

enum PrepareTimingDisplay: Equatable, Sendable {
    case free
    case perPart
    case shared

    static func resolve(for response: AttemptPrepareResponse) -> PrepareTimingDisplay {
        if response.navigationMode == .FREE {
            return .free
        }
        if response.timing == .PER_PART {
            return .perPart
        }
        return .shared
    }

    var localizationKeySegment: String {
        switch self {
        case .free: "FREE"
        case .perPart: "PER_PART"
        case .shared: "SHARED"
        }
    }
}

struct StartAttemptResult: Equatable, Sendable {
    let attemptId: String
    let state: AttemptOpenState
    let resumed: Bool
    let retryable: Bool
}

struct AttemptPollResult: Equatable, Sendable {
    let attemptId: String
    let state: AttemptOpenState
}

struct PrepareScreenState: Equatable, Sendable {
    var load: LoadState<AttemptPrepareResponse> = .loading
    var selection: PreChoiceSelection = .empty
    var gate: StartGate = .open
    var start: StartState = .idle
    var waiting: WaitingPhase? = nil
    var waitingAttemptId: String? = nil
    var waitingContext: StartContext? = nil
    var waitingInMemoryPreChoice: PreparePreChoiceInput? = nil
    var waitingAbortReason: String? = nil
}
