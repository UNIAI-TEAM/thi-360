import Foundation

@MainActor
protocol AudioLogoutCleanable: AnyObject {
    func stopAndPurgeForLogout()
}

@MainActor
final class AttemptAudioSessionRegistry {
    static let shared = AttemptAudioSessionRegistry()

    private final class WeakBox {
        weak var value: (any AudioLogoutCleanable)?
        init(_ value: any AudioLogoutCleanable) {
            self.value = value
        }
    }

    private var entries: [UUID: WeakBox] = [:]

    @discardableResult
    func register(_ cleanable: any AudioLogoutCleanable) -> UUID {
        prune()
        let token = UUID()
        entries[token] = WeakBox(cleanable)
        return token
    }

    func unregister(_ token: UUID) {
        entries.removeValue(forKey: token)
    }

    func stopAndPurgeAll() {
        let targets = entries.values.compactMap(\.value)
        entries.removeAll()
        for target in targets {
            target.stopAndPurgeForLogout()
        }
        AudioSessionCoordinator.shared.clearNowPlayingInfo()
        URLCache.shared.removeAllCachedResponses()
    }

    var activeCount: Int {
        prune()
        return entries.count
    }

    private func prune() {
        entries = entries.filter { $0.value.value != nil }
    }
}

struct AttemptAudioCleanupParticipant: SessionCleanupParticipant, Sendable {
    let id = "attachments.attemptAudio"

    func onLogout(accountId: String) async throws {
        await MainActor.run {
            AttemptAudioSessionRegistry.shared.stopAndPurgeAll()
        }
    }

    func onAppStart() async throws {
        await MainActor.run {
            AttemptAudioSessionRegistry.shared.stopAndPurgeAll()
        }
    }
}
