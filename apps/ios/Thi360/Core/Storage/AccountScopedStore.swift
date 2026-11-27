import CryptoKit
import Foundation

enum AccountScopedStoreError: Error, Equatable {
    case invalidAccountId
    case invalidNamespace
    case invalidKey
    case storeWiped
}

actor AccountScopedStore {
    let accountId: String
    let accountDirectoryURL: URL

    private var isWiped = false

    init(accountId: String, rootDirectoryURL: URL) throws {
        let trimmed = accountId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            throw AccountScopedStoreError.invalidAccountId
        }
        self.accountId = trimmed
        let accountSlug = Self.digest(trimmed)
        self.accountDirectoryURL = rootDirectoryURL.appendingPathComponent(accountSlug, isDirectory: true)
    }

    func read(namespace: String, key: String) -> Data? {
        guard !isWiped,
              let fileURL = try? resolvedFileURL(namespace: namespace, key: key)
        else {
            return nil
        }
        return try? Data(contentsOf: fileURL)
    }

    func write(_ data: Data, namespace: String, key: String) throws {
        guard !isWiped else {
            throw AccountScopedStoreError.storeWiped
        }
        let fileURL = try resolvedFileURL(namespace: namespace, key: key)
        let namespaceDir = fileURL.deletingLastPathComponent()

        let fileManager = FileManager.default
        try fileManager.createDirectory(at: namespaceDir, withIntermediateDirectories: true)
        try Self.excludeFromBackup(accountDirectoryURL.deletingLastPathComponent())
        try Self.excludeFromBackup(accountDirectoryURL)
        try Self.excludeFromBackup(namespaceDir)

        try data.write(to: fileURL, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        try Self.excludeFromBackup(fileURL)
    }

    func delete(namespace: String, key: String) throws {
        let fileURL = try resolvedFileURL(namespace: namespace, key: key)
        let fileManager = FileManager.default
        if fileManager.fileExists(atPath: fileURL.path) {
            try fileManager.removeItem(at: fileURL)
        }
    }

    func deleteNamespace(_ namespace: String) throws {
        let namespaceDir = try resolvedNamespaceURL(namespace: namespace)
        let fileManager = FileManager.default
        if fileManager.fileExists(atPath: namespaceDir.path) {
            try fileManager.removeItem(at: namespaceDir)
        }
    }

    func wipeAll() throws {
        isWiped = true
        let fileManager = FileManager.default
        if fileManager.fileExists(atPath: accountDirectoryURL.path) {
            try fileManager.removeItem(at: accountDirectoryURL)
        }
    }

    func fileURLForTesting(namespace: String, key: String) throws -> URL {
        try resolvedFileURL(namespace: namespace, key: key)
    }

    private func resolvedNamespaceURL(namespace: String) throws -> URL {
        let trimmed = namespace.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            throw AccountScopedStoreError.invalidNamespace
        }
        return accountDirectoryURL.appendingPathComponent(Self.digest(trimmed), isDirectory: true)
    }

    private func resolvedFileURL(namespace: String, key: String) throws -> URL {
        let trimmedKey = key.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedKey.isEmpty else {
            throw AccountScopedStoreError.invalidKey
        }
        let namespaceURL = try resolvedNamespaceURL(namespace: namespace)
        return namespaceURL.appendingPathComponent("\(Self.digest(trimmedKey)).bin", isDirectory: false)
    }

    static func digest(_ value: String) -> String {
        let hash = SHA256.hash(data: Data(value.utf8))
        return hash.map { String(format: "%02x", $0) }.joined()
    }

    static func excludeFromBackup(_ url: URL) throws {
        var mutableURL = url
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try mutableURL.setResourceValues(values)
    }
}
