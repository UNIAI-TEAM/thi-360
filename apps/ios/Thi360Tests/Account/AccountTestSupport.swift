import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

final class FakeBiometricAuthenticator: BiometricAuthenticator, @unchecked Sendable {
    private let lock = NSLock()
    private var _capability: BiometricCapability
    private var _domainState: Data?
    private var _enableResults: [BiometricEvaluationResult]
    private var _enableCallCount: Int = 0
    private var _lastReason: String?

    init(
        capability: BiometricCapability = .faceIDAvailable,
        domainState: Data? = Data("domain-v1".utf8),
        enableResults: [BiometricEvaluationResult] = [.succeeded]
    ) {
        self._capability = capability
        self._domainState = domainState
        self._enableResults = enableResults
    }

    var capability: BiometricCapability {
        get { lock.withLock { _capability } }
        set { lock.withLock { _capability = newValue } }
    }

    var domainState: Data? {
        get { lock.withLock { _domainState } }
        set { lock.withLock { _domainState = newValue } }
    }

    var enableCallCount: Int {
        lock.withLock { _enableCallCount }
    }

    var lastReason: String? {
        lock.withLock { _lastReason }
    }

    func enqueueEnableResults(_ results: [BiometricEvaluationResult]) {
        lock.withLock { _enableResults = results }
    }

    func currentCapability() -> BiometricCapability {
        lock.withLock { _capability }
    }

    func currentDomainState() -> Data? {
        lock.withLock { _domainState }
    }

    func authenticateForEnable(reason: String) async -> BiometricEvaluationResult {
        lock.withLock {
            _enableCallCount += 1
            _lastReason = reason
            if _enableResults.count > 1 {
                return _enableResults.removeFirst()
            }
            return _enableResults.first ?? .succeeded
        }
    }
}

final class MockAppearanceFetcher: AppearanceFetching, @unchecked Sendable {
    typealias Handler = @Sendable (String?) async -> AppearanceFetchResult

    private let lock = NSLock()
    private var handler: Handler
    private var _recordedIfNoneMatchHeaders: [String?] = []

    init(handler: @escaping Handler) {
        self.handler = handler
    }

    var callCount: Int {
        lock.withLock { _recordedIfNoneMatchHeaders.count }
    }

    var recordedIfNoneMatchHeaders: [String?] {
        lock.withLock { _recordedIfNoneMatchHeaders }
    }

    func setHandler(_ newHandler: @escaping Handler) {
        lock.withLock { handler = newHandler }
    }

    func fetch(ifNoneMatch: String?) async -> AppearanceFetchResult {
        let activeHandler: Handler = lock.withLock {
            _recordedIfNoneMatchHeaders.append(ifNoneMatch)
            return handler
        }
        return await activeHandler(ifNoneMatch)
    }
}

enum AlternatePaletteFixtures {
    static let oceanBlueVersion2JSON = """
    {
      "version": 2,
      "mode": "dark",
      "presetId": "ocean-blue",
      "light": {
        "primaryFill": "#1d4ed8",
        "onPrimary": "#ffffff",
        "primaryText": "#1d4ed8",
        "primaryContainer": "#eff6ff",
        "primaryContainerStrong": "#dbeafe",
        "accent": "#ea580c",
        "onAccent": "#ffffff",
        "bgPage": "#f7f8f9",
        "bgSurface": "#ffffff",
        "bgSubtle": "#f1f2f4",
        "text": "#11181c",
        "textSecondary": "#3a3a42",
        "textMuted": "#6b6b72",
        "border": "#e8e8ec",
        "borderStrong": "#d4d4d8",
        "dangerText": "#b91c1c",
        "dangerBg": "#fef2f2"
      },
      "dark": {
        "primaryFill": "#60a5fa",
        "onPrimary": "#11181c",
        "primaryText": "#93c5fd",
        "primaryContainer": "#0f1e36",
        "primaryContainerStrong": "#172554",
        "accent": "#fb923c",
        "onAccent": "#1a1a1f",
        "bgPage": "#0f1418",
        "bgSurface": "#171d22",
        "bgSubtle": "#1e262c",
        "text": "#e6eaed",
        "textSecondary": "#c5ccd2",
        "textMuted": "#a9b2ba",
        "border": "#2a3238",
        "borderStrong": "#3a444c",
        "dangerText": "#f87171",
        "dangerBg": "#2e1517"
      }
    }
    """

    static func oceanBluePayload(version: Int = 2, mode: ThemeMode = .dark) throws -> AppearanceTokensPayload {
        let raw = oceanBlueVersion2JSON
            .replacingOccurrences(of: "\"version\": 2", with: "\"version\": \(version)")
            .replacingOccurrences(of: "\"mode\": \"dark\"", with: "\"mode\": \"\(mode.rawValue)\"")
        return try XCTUnwrap(AppearanceTokensPayload.decodeValidated(from: Data(raw.utf8)))
    }

    static func contractGreenPayload() throws -> AppearanceTokensPayload {
        let data = try ContractFixtureLoader.loadData(relativePath: "appearance/tokens-thi360-green.json")
        return try XCTUnwrap(AppearanceTokensPayload.decodeValidated(from: data))
    }
}

@MainActor
enum AccountTestFactory {
    struct Stack {
        let sessionStore: SessionStore
        let tokenStore: InMemoryTokenStore
        let transport: ContractFakeTransport
        let preferences: AppPreferences
        let biometricManager: BiometricManager
        let authenticator: FakeBiometricAuthenticator
        let secretStore: InMemoryBiometricSecretStore
        let enrollmentStore: InMemoryBiometricEnrollmentStore
        let promptMemoryStore: InMemoryPromptMemoryStore
        let storeProvider: AccountScopedStoreProvider
        let sessionCleanup: SessionCleanup
        let clock: ManualClock
        let defaultsSuiteName: String
    }

    static func makeStack(
        initialTokens: SessionTokens? = nil,
        capability: BiometricCapability = .faceIDAvailable,
        enableResults: [BiometricEvaluationResult] = [.succeeded],
        initialEnrolled: Bool = false,
        initialPromptAnswer: PromptAnswer? = nil,
        locale: AppLocale = .vi,
        themeMode: ThemeMode? = nil,
        lockTimeout: TimeInterval = LockPolicy.defaultTimeoutSeconds,
        handler: @escaping MockClientTransport.Handler
    ) -> Stack {
        makeContractStack(
            initialTokens: initialTokens,
            capability: capability,
            enableResults: enableResults,
            initialEnrolled: initialEnrolled,
            initialPromptAnswer: initialPromptAnswer,
            locale: locale,
            themeMode: themeMode,
            lockTimeout: lockTimeout
        ) { transport in
            transport.setFallback(handler)
        }
    }

    static func makeContractStack(
        initialTokens: SessionTokens? = nil,
        capability: BiometricCapability = .faceIDAvailable,
        enableResults: [BiometricEvaluationResult] = [.succeeded],
        initialEnrolled: Bool = false,
        initialPromptAnswer: PromptAnswer? = nil,
        locale: AppLocale = .vi,
        themeMode: ThemeMode? = nil,
        lockTimeout: TimeInterval = LockPolicy.defaultTimeoutSeconds,
        storeProvider: AccountScopedStoreProvider? = nil,
        sessionCleanup: SessionCleanup? = nil,
        configure: (ContractFakeTransport) -> Void = { _ in }
    ) -> Stack {
        let suite = "vn.unicom.thi360.tests.account.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        let preferences = AppPreferences(userDefaults: defaults)
        preferences.locale = locale
        preferences.themeMode = themeMode

        let clock = ManualClock()
        let authenticator = FakeBiometricAuthenticator(
            capability: capability,
            enableResults: enableResults
        )
        let secretStore = InMemoryBiometricSecretStore()
        if initialEnrolled, let refreshToken = initialTokens?.refreshToken {
            try? secretStore.writeProtectedSecret(Data(refreshToken.utf8))
        }
        let enrollmentStore = InMemoryBiometricEnrollmentStore(
            initial: initialEnrolled
                ? BiometricEnrollmentRecord(isEnabled: true, domainState: authenticator.domainState)
                : .disabled
        )
        let promptMemoryStore = InMemoryPromptMemoryStore(
            initial: PromptMemory(lastAnswer: initialPromptAnswer)
        )
        let biometricManager = BiometricManager(
            authenticator: authenticator,
            secretStore: secretStore,
            enrollmentStore: enrollmentStore,
            promptMemoryStore: promptMemoryStore,
            lockPolicy: LockPolicy(timeoutSeconds: lockTimeout),
            clock: clock
        )

        let tokenStore = InMemoryTokenStore(initialTokens: initialTokens)
        let transport = ContractFakeTransport()
        configure(transport)
        let client = MobileAPIClient(
            serverURL: URL(string: "https://api.thi360.test")!,
            tokenStore: tokenStore,
            transport: transport,
            localeProvider: {
                await MainActor.run { preferences.locale }
            }
        )
        let resolvedStoreProvider = storeProvider ?? AccountScopedStoreProvider(
            rootDirectoryURL: FileManager.default.temporaryDirectory
                .appendingPathComponent("thi360-account-store-\(UUID().uuidString)", isDirectory: true)
        )
        let resolvedCleanup = sessionCleanup ?? SessionCleanup(storeProvider: resolvedStoreProvider)
        let sessionStore = SessionStore(
            tokenStore: tokenStore,
            apiClient: client,
            biometricManager: biometricManager,
            storeProvider: resolvedStoreProvider,
            sessionCleanup: resolvedCleanup
        )

        return Stack(
            sessionStore: sessionStore,
            tokenStore: tokenStore,
            transport: transport,
            preferences: preferences,
            biometricManager: biometricManager,
            authenticator: authenticator,
            secretStore: secretStore,
            enrollmentStore: enrollmentStore,
            promptMemoryStore: promptMemoryStore,
            storeProvider: resolvedStoreProvider,
            sessionCleanup: resolvedCleanup,
            clock: clock,
            defaultsSuiteName: suite
        )
    }

    static func sampleTokens(
        username: String = "student.demo",
        accountId: String? = "019320a0-0000-7000-8000-000000000001",
        accessToken: String = "access-token-1",
        refreshToken: String = "refresh-token-1",
        mustChangePassword: Bool = false
    ) -> SessionTokens {
        SessionTokens(
            accessToken: accessToken,
            refreshToken: refreshToken,
            accessExpiresIn: 300,
            refreshExpiresIn: 604_800,
            mustChangePassword: mustChangePassword,
            username: username,
            accountId: accountId
        )
    }
}

private extension NSLock {
    func withLock<T>(_ body: () -> T) -> T {
        lock()
        defer { unlock() }
        return body()
    }
}
