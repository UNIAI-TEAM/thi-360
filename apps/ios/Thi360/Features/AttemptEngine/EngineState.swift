import Foundation

typealias ItemID = String
typealias PartID = String

struct Position: Codable, Equatable, Hashable, Sendable {
    let partId: PartID
    let itemId: ItemID?

    init(partId: PartID, itemId: ItemID? = nil) {
        self.partId = partId
        self.itemId = itemId
    }
}

enum AnswerSyncState: Equatable, Sendable {
    case saved
    case pending
    case rejected
    case locked
}

struct LocalAnswer: Equatable, Sendable {
    var value: AnswerValue?
    var seq: Int64
    var flagged: Bool
    var sync: AnswerSyncState

    init(
        value: AnswerValue?,
        seq: Int64,
        flagged: Bool,
        sync: AnswerSyncState
    ) {
        self.value = value
        self.seq = seq
        self.flagged = flagged
        self.sync = sync
    }
}

enum ReadOnlyReason: Equatable, Sendable {
    case attemptNotInProgress
    case partNotOpen
    case closed(ClosedReason)
}

enum EditSessionState: Equatable, Sendable {
    case acquiring
    case editing(sessionId: String)
    case openElsewhere(lastSeenAt: Date?)
    case evicted
    case readOnly(ReadOnlyReason)

    var activeSessionId: String? {
        if case let .editing(sessionId) = self {
            return sessionId
        }
        return nil
    }
}

enum SaveFailureReason: Equatable, Sendable {
    case networkError
    case serverError
    case rateLimited(Int)
    case rejected
    case sessionConflict
}

enum SaveStatus: Equatable, Sendable {
    case saved
    case saving
    case unsaved(SaveFailureReason)
    case offline
}

enum NetworkStatus: Equatable, Sendable {
    case online
    case offline
}

enum SendingCommand: Equatable, Sendable {
    case finishPart
    case submit
    case choice
    case retry
    case takeover
}

enum UnconfirmedCommand: Equatable, Sendable {
    case finishPart
    case submit
}

enum CommandState: Equatable, Sendable {
    case idle
    case sending(SendingCommand)
    case unconfirmed(UnconfirmedCommand)
    case refused(code: String)
}

enum ClosedReason: Equatable, Sendable {
    case timeUp
    case submitted
    case choiceTimeout
    case aborted(reason: String?)
    case notOpen
}

enum CloseTarget: Equatable, Sendable {
    case part(PartID)
    case attempt
}

enum EngineEvent: Equatable, Sendable {
    case beforeClose(CloseTarget)
    case partClosed(PartID)
    case attemptClosed(ClosedReason)
    case sessionLost
}

struct EngineState: Equatable, Sendable {
    var load: LoadState<Void>
    var attempt: Components.Schemas.AttemptStateResponse?
    var answers: [ItemID: LocalAnswer]
    var position: Position?
    var session: EditSessionState
    var clock: Countdown
    var save: SaveStatus
    var network: NetworkStatus
    var command: CommandState
    var closed: ClosedReason?

    static func initial(isOnline: Bool) -> EngineState {
        EngineState(
            load: .loading,
            attempt: nil,
            answers: [:],
            position: nil,
            session: .acquiring,
            clock: .none,
            save: .saved,
            network: isOnline ? .online : .offline,
            command: .idle,
            closed: nil
        )
    }

    var activeSessionId: String? {
        session.activeSessionId
    }

    var isSessionReadOnly: Bool {
        switch session {
        case .openElsewhere, .evicted, .readOnly:
            return true
        case .acquiring, .editing:
            return closed != nil
        }
    }

    static func == (lhs: EngineState, rhs: EngineState) -> Bool {
        let loadEqual: Bool
        switch (lhs.load, rhs.load) {
        case (.loading, .loading), (.empty, .empty):
            loadEqual = true
        case let (.loaded(_, s1), .loaded(_, s2)):
            loadEqual = (s1 == s2)
        case let (.failed(k1, r1, a1), .failed(k2, r2, a2)):
            loadEqual = (k1 == k2 && r1 == r2 && a1 == a2)
        default:
            loadEqual = false
        }
        return loadEqual
            && lhs.attempt == rhs.attempt
            && lhs.answers == rhs.answers
            && lhs.position == rhs.position
            && lhs.session == rhs.session
            && lhs.clock == rhs.clock
            && lhs.save == rhs.save
            && lhs.network == rhs.network
            && lhs.command == rhs.command
            && lhs.closed == rhs.closed
    }
}
