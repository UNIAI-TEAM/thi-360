import Foundation

struct PreparedAvatar: Equatable, Sendable, CustomStringConvertible {
    let data: Data
    let mimeType: String
    let fileName: String

    init(data: Data, mimeType: String, fileName: String? = nil) {
        self.data = data
        self.mimeType = mimeType
        if let fileName {
            self.fileName = fileName
        } else {
            switch mimeType.lowercased() {
            case "image/png":
                self.fileName = "avatar.png"
            case "image/webp":
                self.fileName = "avatar.webp"
            default:
                self.fileName = "avatar.jpg"
            }
        }
    }

    var description: String {
        "PreparedAvatar(mimeType: \(mimeType), fileName: \(fileName), bytes: \(data.count))"
    }
}

enum AvatarPrepareResult: Equatable, Sendable {
    case prepared(Data, mime: String)
    case unreadable
}

enum ProfileError: Error, Equatable, Sendable {
    case field(name: String, code: String)
    case codeInvalid(remaining: Int?)
    case codeExpired
    case emailTaken
    case rateLimited(retryAfter: Int?)
    case throttled
    case syncFailed
    case avatarTooLarge
    case avatarDimensions
    case avatarInvalid
    case offline
    case other
}

enum DeletionBlocker: String, Equatable, Hashable, Sendable {
    case attemptInProgress = "ATTEMPT_IN_PROGRESS"
    case orderBeingChecked = "ORDER_BEING_CHECKED"
    case orderPendingPayment = "ORDER_PENDING_PAYMENT"
}

struct DeletionCheck: Equatable, Sendable {
    let canDelete: Bool
    let blockers: [DeletionBlocker]
    let unusedAttempts: Int
    let pendingGrading: Bool
}

enum AccountDeletionFailureKind: Equatable, Sendable {
    case wrongPassword
    case throttled
    case network
    case server
}

enum AccountDeletionOutcome: Equatable, Sendable {
    case deleted
    case blocked([DeletionBlocker])
    case failed(AccountDeletionFailureKind)
}

enum AccountDeletionState: Equatable, Sendable {
    case checking(LoadState<DeletionCheck>)
    case blocked([DeletionBlocker])
    case ready(unusedAttempts: Int, pendingGrading: Bool)
    case confirming(unusedAttempts: Int, pendingGrading: Bool)
    case deleting(unusedAttempts: Int, pendingGrading: Bool)
    case deleted
    case failed(kind: AccountDeletionFailureKind, unusedAttempts: Int, pendingGrading: Bool)

    var unusedAttempts: Int {
        switch self {
        case let .ready(unusedAttempts, _),
             let .confirming(unusedAttempts, _),
             let .deleting(unusedAttempts, _),
             let .failed(_, unusedAttempts, _):
            unusedAttempts
        case .checking, .blocked, .deleted:
            0
        }
    }

    var pendingGrading: Bool {
        switch self {
        case let .ready(_, pendingGrading),
             let .confirming(_, pendingGrading),
             let .deleting(_, pendingGrading),
             let .failed(_, _, pendingGrading):
            pendingGrading
        case .checking, .blocked, .deleted:
            false
        }
    }
}
