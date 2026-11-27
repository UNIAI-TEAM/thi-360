import Foundation

final class AccountScopedStoreProvider: @unchecked Sendable {
    let rootDirectoryURL: URL

    private let lock = NSLock()
    private var stores: [String: AccountScopedStore] = [:]

    init(rootDirectoryURL: URL = AccountScopedStoreProvider.defaultRootDirectoryURL()) {
        self.rootDirectoryURL = rootDirectoryURL
    }

    static func defaultRootDirectoryURL() -> URL {
        let appSupport = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? FileManager.default.temporaryDirectory
        return appSupport.appendingPathComponent("AccountScoped", isDirectory: true)
    }

    func store(for accountId: String?) -> AccountScopedStore? {
        guard let trimmed = accountId?.trimmingCharacters(in: .whitespacesAndNewlines),
              !trimmed.isEmpty
        else {
            return nil
        }
        lock.lock()
        defer { lock.unlock() }
        if let existing = stores[trimmed] {
            return existing
        }
        guard let created = try? AccountScopedStore(accountId: trimmed, rootDirectoryURL: rootDirectoryURL) else {
            return nil
        }
        stores[trimmed] = created
        return created
    }

    func read(accountId: String?, namespace: String, key: String) async -> Data? {
        guard let store = store(for: accountId) else { return nil }
        return await store.read(namespace: namespace, key: key)
    }

    @discardableResult
    func write(_ data: Data, accountId: String?, namespace: String, key: String) async throws -> Bool {
        guard let store = store(for: accountId) else { return false }
        try await store.write(data, namespace: namespace, key: key)
        return true
    }

    func wipeAccount(_ accountId: String) async throws {
        let trimmed = accountId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }

        let targetStore: AccountScopedStore
        lock.lock()
        if let existing = stores.removeValue(forKey: trimmed) {
            targetStore = existing
        } else {
            lock.unlock()
            targetStore = try AccountScopedStore(accountId: trimmed, rootDirectoryURL: rootDirectoryURL)
            try await targetStore.wipeAll()
            return
        }
        lock.unlock()

        try await targetStore.wipeAll()
    }

    func accountDirectoryExists(for accountId: String) -> Bool {
        let slug = AccountScopedStore.digest(accountId.trimmingCharacters(in: .whitespacesAndNewlines))
        let dirURL = rootDirectoryURL.appendingPathComponent(slug, isDirectory: true)
        return FileManager.default.fileExists(atPath: dirURL.path)
    }
}
