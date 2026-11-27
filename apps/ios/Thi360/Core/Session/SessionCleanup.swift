import Foundation

protocol SessionCleanupParticipant: Sendable {
    var id: String { get }
    func onLogout(accountId: String) async throws
    func onAppStart() async throws
}

extension SessionCleanupParticipant {
    func onAppStart() async throws {}
}

actor SessionCleanup {
    private static let pendingMarkerFilename = ".pending-logout-cleanup.json"

    private let storeProvider: AccountScopedStoreProvider
    private let markerFileURL: URL
    private let logger: RedactingAppLogger
    private var participants: [any SessionCleanupParticipant] = [
        AttemptAssetTempCleanupParticipant(),
        AttemptAudioCleanupParticipant()
    ]

    init(
        storeProvider: AccountScopedStoreProvider = AccountScopedStoreProvider(),
        logger: RedactingAppLogger = RedactingAppLogger()
    ) {
        self.storeProvider = storeProvider
        self.markerFileURL = storeProvider.rootDirectoryURL
            .appendingPathComponent(Self.pendingMarkerFilename, isDirectory: false)
        self.logger = logger
    }

    func register(_ participant: any SessionCleanupParticipant) {
        participants.removeAll { $0.id == participant.id }
        participants.append(participant)
    }

    func runLogout(accountId: String?) async {
        guard let trimmed = accountId?.trimmingCharacters(in: .whitespacesAndNewlines),
              !trimmed.isEmpty
        else {
            return
        }

        // Persist the cleanup marker before deleting anything so a crash mid-logout resumes on next app start.
        addPendingAccountId(trimmed)
        let succeeded = await executeLogoutCleanup(for: trimmed)
        if succeeded {
            removePendingAccountId(trimmed)
        }
    }

    func runAppStart() async {
        let pendingIds = readPendingAccountIds()
        for pendingId in pendingIds {
            let succeeded = await executeLogoutCleanup(for: pendingId)
            if succeeded {
                removePendingAccountId(pendingId)
            }
        }

        for participant in participants {
            do {
                try await participant.onAppStart()
            } catch {
                logger.log(.warning, category: "cleanup", message: "App start cleanup failed id=\(participant.id)")
            }
        }
    }

    func pendingAccountIdsForTesting() -> [String] {
        readPendingAccountIds()
    }

    func recordPendingLogoutForTesting(_ accountId: String) {
        addPendingAccountId(accountId)
    }

    private func executeLogoutCleanup(for accountId: String) async -> Bool {
        var allSucceeded = true

        for participant in participants {
            do {
                try await participant.onLogout(accountId: accountId)
            } catch {
                allSucceeded = false
                logger.log(.warning, category: "cleanup", message: "Logout cleanup participant failed id=\(participant.id)")
            }
        }

        do {
            try await storeProvider.wipeAccount(accountId)
        } catch {
            allSucceeded = false
            logger.log(.warning, category: "cleanup", message: "AccountScopedStore wipe failed")
        }

        return allSucceeded
    }

    private func readPendingAccountIds() -> [String] {
        Self.pendingLogoutAccountIds(rootDirectoryURL: storeProvider.rootDirectoryURL)
    }

    static func pendingLogoutAccountIds(rootDirectoryURL: URL) -> [String] {
        let markerURL = rootDirectoryURL.appendingPathComponent(pendingMarkerFilename, isDirectory: false)
        guard let data = try? Data(contentsOf: markerURL),
              let ids = try? JSONDecoder().decode([String].self, from: data)
        else {
            return []
        }
        return ids
    }

    private func writePendingAccountIds(_ ids: [String]) {
        let fileManager = FileManager.default
        if ids.isEmpty {
            if fileManager.fileExists(atPath: markerFileURL.path) {
                try? fileManager.removeItem(at: markerFileURL)
            }
            return
        }
        do {
            try fileManager.createDirectory(at: storeProvider.rootDirectoryURL, withIntermediateDirectories: true)
            try? AccountScopedStore.excludeFromBackup(storeProvider.rootDirectoryURL)
            let data = try JSONEncoder().encode(ids)
            try data.write(to: markerFileURL, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
            try? AccountScopedStore.excludeFromBackup(markerFileURL)
        } catch {
            logger.log(.warning, category: "cleanup", message: "Failed writing pending logout marker")
        }
    }

    private func addPendingAccountId(_ accountId: String) {
        var ids = readPendingAccountIds()
        if !ids.contains(accountId) {
            ids.append(accountId)
            writePendingAccountIds(ids)
        }
    }

    private func removePendingAccountId(_ accountId: String) {
        var ids = readPendingAccountIds()
        ids.removeAll { $0 == accountId }
        writePendingAccountIds(ids)
    }
}
