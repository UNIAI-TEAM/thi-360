import Foundation

struct LockPolicy: Equatable, Sendable {
    static let defaultTimeoutSeconds: TimeInterval = 300

    let timeoutSeconds: TimeInterval

    init(timeoutSeconds: TimeInterval = LockPolicy.defaultTimeoutSeconds) {
        self.timeoutSeconds = max(1, timeoutSeconds)
    }

    func shouldLock(
        isBiometricEnabled: Bool,
        isColdLaunch: Bool,
        backgroundedAt: Date?,
        now: Date
    ) -> Bool {
        guard isBiometricEnabled else { return false }
        if isColdLaunch {
            return true
        }
        guard let backgroundedAt else { return false }
        let elapsed = now.timeIntervalSince(backgroundedAt)
        // Lock when timeout has elapsed or device clock moved backwards while backgrounded.
        return elapsed >= timeoutSeconds || elapsed < 0
    }
}
