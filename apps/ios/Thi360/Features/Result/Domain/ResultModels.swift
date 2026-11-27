import Foundation

enum AttemptResultState: String, Codable, Hashable, Sendable, CaseIterable {
    case grading = "GRADING"
    case pendingReview = "PENDING_REVIEW"
    case scored = "SCORED"
    case gradingError = "GRADING_ERROR"

    init(contractState: Components.Schemas.AttemptResultResponse.statePayload?) {
        switch contractState {
        case .GRADING:
            self = .grading
        case .PENDING_REVIEW:
            self = .pendingReview
        case .SCORED:
            self = .scored
        case .GRADING_ERROR:
            self = .gradingError
        case .STARTING, .IN_PROGRESS, .ABORTED, .none:
            self = .grading
        }
    }

    init?(reviewContractState: Components.Schemas.AttemptReviewResponse.statePayload?) {
        switch reviewContractState {
        case .GRADING:
            self = .grading
        case .PENDING_REVIEW:
            self = .pendingReview
        case .SCORED:
            self = .scored
        case .GRADING_ERROR:
            self = .gradingError
        case .STARTING, .IN_PROGRESS, .ABORTED, .none:
            return nil
        }
    }
}

enum AttemptResultEndReason: Equatable, Hashable, Sendable {
    case submitted
    case timeUp
    case choiceTimeout
    case faultClosed
    case unknown(String)

    init?(contractReason: Components.Schemas.AttemptResultResponse.endReasonPayload?) {
        guard let contractReason else { return nil }
        switch contractReason {
        case .SUBMITTED:
            self = .submitted
        case .TIME_UP:
            self = .timeUp
        case .CHOICE_TIMEOUT:
            self = .choiceTimeout
        case .FAULT_CLOSED:
            self = .faultClosed
        }
    }

    var localizationKey: String? {
        switch self {
        case .submitted: "result.endReasons.SUBMITTED"
        case .timeUp: "result.endReasons.TIME_UP"
        case .choiceTimeout: "result.endReasons.CHOICE_TIMEOUT"
        case .faultClosed: "result.endReasons.FAULT_CLOSED"
        case .unknown: nil
        }
    }

    var fallbackText: String {
        switch self {
        case .submitted: "SUBMITTED"
        case .timeUp: "TIME_UP"
        case .choiceTimeout: "CHOICE_TIMEOUT"
        case .faultClosed: "FAULT_CLOSED"
        case let .unknown(raw): raw
        }
    }
}

enum AttemptResultScope: Equatable, Hashable, Sendable {
    case full
    case part
    case subject
    case unknown(String)

    init?(rawValue: String?) {
        guard let rawValue, !rawValue.isEmpty else { return nil }
        switch rawValue {
        case "FULL": self = .full
        case "PART": self = .part
        case "SUBJECT": self = .subject
        default: self = .unknown(rawValue)
        }
    }

    var localizationKey: String? {
        switch self {
        case .full: "result.scopes.FULL"
        case .part: "result.scopes.PART"
        case .subject: "result.scopes.SUBJECT"
        case .unknown: nil
        }
    }

    var fallbackText: String {
        switch self {
        case .full: "FULL"
        case .part: "PART"
        case .subject: "SUBJECT"
        case let .unknown(raw): raw
        }
    }
}

enum AttemptResultViewLevel: Equatable, Hashable, Sendable {
    case score
    case scoreAndAnswers
    case fullSolution
    case unknown(String)

    init(rawValue: String?) {
        switch rawValue {
        case "SCORE":
            self = .score
        case "SCORE_AND_ANSWERS":
            self = .scoreAndAnswers
        case "FULL_SOLUTION":
            self = .fullSolution
        case let other?:
            self = .unknown(other)
        case .none:
            self = .score
        }
    }

    var questionReviewLevel: ReviewLevel {
        switch self {
        case .fullSolution: .fullSolution
        case .scoreAndAnswers: .scoreAndAnswers
        case .score, .unknown: .scoreOnly
        }
    }
}

struct AttemptResultStats: Equatable, Hashable, Sendable {
    let correctCount: Int
    let partialCount: Int
    let wrongCount: Int
    let blankCount: Int
    let pendingReviewCount: Int
    let totalCount: Int

    init(
        correctCount: Int,
        partialCount: Int,
        wrongCount: Int,
        blankCount: Int,
        pendingReviewCount: Int,
        totalCount: Int? = nil
    ) {
        self.correctCount = correctCount
        self.partialCount = partialCount
        self.wrongCount = wrongCount
        self.blankCount = blankCount
        self.pendingReviewCount = pendingReviewCount
        self.totalCount = totalCount ?? (correctCount + partialCount + wrongCount + blankCount + pendingReviewCount)
    }

    init(dto: Components.Schemas.AttemptResultUnitStats) {
        let correct = Int(dto.correctCount ?? 0)
        let partial = Int(dto.partialCount ?? 0)
        let wrong = Int(dto.wrongCount ?? 0)
        let blank = Int(dto.blankCount ?? 0)
        let pending = Int(dto.pendingReviewCount ?? 0)
        self.init(
            correctCount: correct,
            partialCount: partial,
            wrongCount: wrong,
            blankCount: blank,
            pendingReviewCount: pending,
            totalCount: dto.totalCount.map(Int.init)
        )
    }
}

struct AttemptChoiceItem: Equatable, Hashable, Sendable {
    let key: String
    let optionCode: String
    let subjectCodes: [String]
}

struct AttemptResultPartItem: Identifiable, Equatable, Hashable, Sendable {
    let id: String
    let partId: String?
    let partCode: String?
    let partName: String?
    let ord: Int?
    let skipped: Bool
    let pendingReview: Bool
    let score: Double?
    let maxScore: Double?
    let stats: AttemptResultStats?

    var displayTitle: String {
        if let partName, !partName.isEmpty { return partName }
        if let partCode, !partCode.isEmpty { return partCode }
        return "—"
    }
}

struct AttemptResultSegmentItem: Identifiable, Equatable, Hashable, Sendable {
    let id: String
    let partCode: String?
    let code: String?
    let name: String?
    let fromPosition: Int?
    let toPosition: Int?
    let pendingReview: Bool
    let score: Double?
    let maxPoints: Double?
    let stats: AttemptResultStats?

    var displayTitle: String {
        if let name, !name.isEmpty { return name }
        if let code, !code.isEmpty { return code }
        return "—"
    }
}

struct AttemptResultSubjectItem: Identifiable, Equatable, Hashable, Sendable {
    let id: String
    let code: String?
    let name: String?
    let pendingReview: Bool
    let score: Double?
    let maxScore: Double?
    let stats: AttemptResultStats?
}

struct AttemptResultVactSubsectionItem: Identifiable, Equatable, Hashable, Sendable {
    let id: String
    let sectionId: String?
    let label: String?
    let questionCount: Int?
    let correctCount: Int
    let totalCount: Int
    let correctRate: Double
}

struct AttemptResultVactComponentItem: Identifiable, Equatable, Hashable, Sendable {
    let id: String
    let componentCode: String
    let name: String?
    let correctCount: Int
    let totalCount: Int
    let correctRate: Double
    let subsections: [AttemptResultVactSubsectionItem]
}

struct AttemptResult: Equatable, Hashable, Sendable {
    let attemptId: String
    let examName: String
    let examKindCode: String?
    let scope: AttemptResultScope?
    let partCode: String?
    let subjectCode: String?
    let attemptNo: Int?
    let state: AttemptResultState
    let regrading: Bool
    let endReason: AttemptResultEndReason?
    let startedAt: Date?
    let finishedAt: Date?
    let displayScore: Double?
    let rawScore: Double?
    let scaleMax: Double?
    let rawMaxScore: Double?
    let resultView: AttemptResultViewLevel
    let reviewAvailable: Bool
    let compensated: Bool
    let faultFlag: String?
    let faultReason: String?
    // Intentionally omits `faultMessage` so UI layers cannot display raw backend fault copy (R-149 / AC-580).
    let choices: [AttemptChoiceItem]
    let statistics: AttemptResultStats?
    let parts: [AttemptResultPartItem]
    let segments: [AttemptResultSegmentItem]
    let subjects: [AttemptResultSubjectItem]
    let vactComponents: [AttemptResultVactComponentItem]

    init(dto: Components.Schemas.AttemptResultResponse, fallbackAttemptId: String) {
        self.attemptId = dto.attemptId ?? fallbackAttemptId
        self.examName = dto.examName ?? ""
        self.examKindCode = Self.nonEmpty(dto.examKindCode)
        self.scope = AttemptResultScope(rawValue: dto.scope)
        self.partCode = Self.nonEmpty(dto.partCode)
        self.subjectCode = Self.nonEmpty(dto.subjectCode)
        self.attemptNo = dto.attemptNo.map(Int.init)
        self.state = AttemptResultState(contractState: dto.state)
        self.regrading = dto.regrading ?? false
        self.endReason = AttemptResultEndReason(contractReason: dto.endReason)
        self.startedAt = dto.startedAt
        self.finishedAt = dto.finishedAt
        self.displayScore = dto.displayScore
        self.rawScore = dto.rawScore
        self.scaleMax = dto.scaleMax
        self.rawMaxScore = dto.rawMaxScore
        self.resultView = AttemptResultViewLevel(rawValue: dto.resultView)
        self.reviewAvailable = dto.reviewAvailable ?? false
        self.compensated = dto.compensated ?? false
        self.faultFlag = Self.nonEmpty(dto.faultFlag)
        self.faultReason = Self.nonEmpty(dto.faultReason)

        let rawChoices = dto.choices?.additionalProperties ?? [:]
        self.choices = rawChoices.keys.sorted().compactMap { key in
            guard let summary = rawChoices[key] else { return nil }
            let code = summary.optionCode.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !code.isEmpty else { return nil }
            return AttemptChoiceItem(
                key: key,
                optionCode: code,
                subjectCodes: summary.subjectCodes ?? []
            )
        }

        self.statistics = dto.statistics.map(AttemptResultStats.init(dto:))
        self.parts = (dto.parts ?? []).enumerated().map { index, part in
            AttemptResultPartItem(
                id: part.partId ?? part.partCode ?? "part-\(index)",
                partId: Self.nonEmpty(part.partId),
                partCode: Self.nonEmpty(part.partCode),
                partName: Self.nonEmpty(part.partName),
                ord: part.ord.map(Int.init),
                skipped: part.skipped ?? false,
                pendingReview: part.pendingReview ?? false,
                score: part.score,
                maxScore: part.maxScore,
                stats: part.stats.map(AttemptResultStats.init(dto:))
            )
        }
        self.segments = (dto.segments ?? []).enumerated().map { index, segment in
            AttemptResultSegmentItem(
                id: "\(segment.partCode ?? "")-\(segment.code ?? String(index))",
                partCode: Self.nonEmpty(segment.partCode),
                code: Self.nonEmpty(segment.code),
                name: Self.nonEmpty(segment.name),
                fromPosition: segment.fromPosition.map(Int.init),
                toPosition: segment.toPosition.map(Int.init),
                pendingReview: segment.pendingReview ?? false,
                score: segment.score,
                maxPoints: segment.maxPoints,
                stats: segment.stats.map(AttemptResultStats.init(dto:))
            )
        }
        self.subjects = (dto.subjects ?? []).enumerated().map { index, subject in
            AttemptResultSubjectItem(
                id: subject.code ?? "subject-\(index)",
                code: Self.nonEmpty(subject.code),
                name: Self.nonEmpty(subject.name),
                pendingReview: subject.pendingReview ?? false,
                score: subject.score,
                maxScore: subject.maxScore,
                stats: subject.stats.map(AttemptResultStats.init(dto:))
            )
        }
        self.vactComponents = (dto.vactComponents ?? []).enumerated().map { index, comp in
            let code = comp.componentCode ?? ""
            let subs = (comp.subsections ?? []).enumerated().map { sIdx, sub in
                let qCount = sub.questionCount.map(Int.init)
                let total = sub.totalCount.map(Int.init) ?? qCount ?? 0
                return AttemptResultVactSubsectionItem(
                    id: sub.sectionId ?? "\(code)-sub-\(sIdx)",
                    sectionId: Self.nonEmpty(sub.sectionId),
                    label: Self.nonEmpty(sub.label),
                    questionCount: qCount,
                    correctCount: Int(sub.correctCount ?? 0),
                    totalCount: total,
                    correctRate: sub.correctRate ?? 0
                )
            }
            return AttemptResultVactComponentItem(
                id: code.isEmpty ? "vact-\(index)" : code,
                componentCode: code,
                name: Self.nonEmpty(comp.name),
                correctCount: Int(comp.correctCount ?? 0),
                totalCount: Int(comp.totalCount ?? 0),
                correctRate: comp.correctRate ?? 0,
                subsections: subs
            )
        }
    }

    var isVact: Bool {
        examKindCode == "VACT"
    }

    var showsSystemFaultBanner: Bool {
        compensated || faultFlag == "SYSTEM_FAULT" || endReason == .faultClosed
    }

    var canOpenReview: Bool {
        reviewAvailable && resultView != .score && (state == .scored || state == .pendingReview)
    }

    var isPendingUnregraded: Bool {
        state == .pendingReview && !regrading
    }

    var statusStateKey: String {
        if regrading {
            return "result.states.REGRADING"
        }
        return "result.states.\(state.rawValue)"
    }

    private static func nonEmpty(_ value: String?) -> String? {
        guard let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines), !trimmed.isEmpty else {
            return nil
        }
        return trimmed
    }
}
