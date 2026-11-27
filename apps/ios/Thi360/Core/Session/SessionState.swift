import Foundation

enum SignedOutReason: Equatable, Sendable {
    case none
    case expired
    case studentOnly(username: String)
    case passwordReset
    case accountDeleted
}

enum SessionState: Equatable, Sendable {
    case signedOut(reason: SignedOutReason)
    case mustChangePassword
    case active
    case locked

    var isAuthenticated: Bool {
        switch self {
        case .signedOut:
            false
        case .mustChangePassword, .active, .locked:
            true
        }
    }
}

enum SessionConnectivityState: Equatable, Sendable {
    case online
    case offline
}
