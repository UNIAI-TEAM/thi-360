import Foundation
import Security

struct SessionTokens: Codable, Equatable, Sendable, CustomStringConvertible, CustomDebugStringConvertible {
    let accessToken: String
    let refreshToken: String
    let accessExpiresIn: Int
    let refreshExpiresIn: Int
    let mustChangePassword: Bool
    let username: String?
    let issuedAt: Date

    init(
        accessToken: String,
        refreshToken: String,
        accessExpiresIn: Int,
        refreshExpiresIn: Int,
        mustChangePassword: Bool,
        username: String? = nil,
        issuedAt: Date = Date()
    ) {
        self.accessToken = accessToken
        self.refreshToken = refreshToken
        self.accessExpiresIn = accessExpiresIn
        self.refreshExpiresIn = refreshExpiresIn
        self.mustChangePassword = mustChangePassword
        self.username = username
        self.issuedAt = issuedAt
    }

    var description: String {
        "SessionTokens(accessToken: <redacted>, refreshToken: <redacted>, accessExpiresIn: \(accessExpiresIn), refreshExpiresIn: \(refreshExpiresIn), mustChangePassword: \(mustChangePassword))"
    }

    var debugDescription: String { description }
}

protocol TokenStore: Sendable {
    func read() throws -> SessionTokens?
    func write(_ tokens: SessionTokens) throws
    func clear() throws
}

enum KeychainTokenStoreError: Error, Equatable {
    case unexpectedStatus(OSStatus)
    case encodingFailed
    case decodingFailed
}

final class KeychainTokenStore: TokenStore, @unchecked Sendable {
    static let defaultService = "vn.unicom.thi360.session"
    static let defaultAccount = "mobile-session-tokens"

    let service: String
    let account: String

    init(
        service: String = KeychainTokenStore.defaultService,
        account: String = KeychainTokenStore.defaultAccount
    ) {
        self.service = service
        self.account = account
    }

    func read() throws -> SessionTokens? {
        var query = baseQuery()
        query[kSecReturnData as String] = kCFBooleanTrue
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        switch status {
        case errSecSuccess:
            guard let data = item as? Data else {
                throw KeychainTokenStoreError.decodingFailed
            }
            do {
                return try Self.makeDecoder().decode(SessionTokens.self, from: data)
            } catch {
                throw KeychainTokenStoreError.decodingFailed
            }
        case errSecItemNotFound:
            return nil
        default:
            throw KeychainTokenStoreError.unexpectedStatus(status)
        }
    }

    func write(_ tokens: SessionTokens) throws {
        let data: Data
        do {
            data = try Self.makeEncoder().encode(tokens)
        } catch {
            throw KeychainTokenStoreError.encodingFailed
        }

        let deleteStatus = SecItemDelete(baseQuery() as CFDictionary)
        if deleteStatus != errSecSuccess && deleteStatus != errSecItemNotFound {
            throw KeychainTokenStoreError.unexpectedStatus(deleteStatus)
        }

        var addQuery = baseQuery()
        addQuery[kSecValueData as String] = data
        addQuery[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly

        let addStatus = SecItemAdd(addQuery as CFDictionary, nil)
        guard addStatus == errSecSuccess else {
            throw KeychainTokenStoreError.unexpectedStatus(addStatus)
        }
    }

    func clear() throws {
        let status = SecItemDelete(baseQuery() as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw KeychainTokenStoreError.unexpectedStatus(status)
        }
    }

    func readItemAccessibility() throws -> String? {
        var query = baseQuery()
        query[kSecReturnAttributes as String] = kCFBooleanTrue
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        switch status {
        case errSecSuccess:
            let attributes = item as? [String: Any]
            return attributes?[kSecAttrAccessible as String] as? String
        case errSecItemNotFound:
            return nil
        default:
            throw KeychainTokenStoreError.unexpectedStatus(status)
        }
    }

    private func baseQuery() -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
    }

    private static func makeEncoder() -> JSONEncoder {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        return encoder
    }

    private static func makeDecoder() -> JSONDecoder {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }
}

final class InMemoryTokenStore: TokenStore, @unchecked Sendable {
    private let lock = NSLock()
    private var stored: SessionTokens?

    init(initialTokens: SessionTokens? = nil) {
        self.stored = initialTokens
    }

    func read() throws -> SessionTokens? {
        lock.lock()
        defer { lock.unlock() }
        return stored
    }

    func write(_ tokens: SessionTokens) throws {
        lock.lock()
        defer { lock.unlock() }
        stored = tokens
    }

    func clear() throws {
        lock.lock()
        defer { lock.unlock() }
        stored = nil
    }
}

struct FirstRunKeychainCleaner: @unchecked Sendable {
    static let defaultFlagKey = "thi360.storage.hasCompletedFirstRun"

    private let userDefaults: UserDefaults
    private let flagKey: String
    private let tokenStore: any TokenStore
    private let additionalCleanup: (@Sendable () -> Void)?

    init(
        userDefaults: UserDefaults = .standard,
        flagKey: String = FirstRunKeychainCleaner.defaultFlagKey,
        tokenStore: any TokenStore,
        additionalCleanup: (@Sendable () -> Void)? = nil
    ) {
        self.userDefaults = userDefaults
        self.flagKey = flagKey
        self.tokenStore = tokenStore
        self.additionalCleanup = additionalCleanup
    }

    @discardableResult
    func cleanIfNeeded() throws -> Bool {
        if userDefaults.bool(forKey: flagKey) {
            return false
        }
        // iOS preserves Keychain entries across app uninstall/reinstall while wiping UserDefaults.
        try tokenStore.clear()
        additionalCleanup?()
        userDefaults.set(true, forKey: flagKey)
        return true
    }
}
