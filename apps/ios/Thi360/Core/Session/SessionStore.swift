import Foundation
import Observation
import OpenAPIRuntime
import OpenAPIURLSession

@Observable
@MainActor
final class SessionStore {
    private(set) var state: SessionState
    private(set) var connectivityState: SessionConnectivityState = .online
    private(set) var isRetryingConnection: Bool = false

    let tokenStore: any TokenStore
    let apiClient: MobileAPIClient
    let biometricManager: BiometricManager?
    var onSessionCleared: (@MainActor () -> Void)?
    var onSessionReplaced: (@MainActor () -> Void)?

    private let logger: RedactingAppLogger
    private let skipsLaunchValidation: Bool
    private var hasTriggeredLaunchValidation = false

    init(
        tokenStore: any TokenStore,
        apiClient: MobileAPIClient,
        firstRunCleaner: FirstRunKeychainCleaner? = nil,
        biometricManager: BiometricManager? = nil,
        logger: RedactingAppLogger = RedactingAppLogger(),
        initialStateOverride: SessionState? = nil,
        skipsLaunchValidation: Bool = false
    ) {
        self.tokenStore = tokenStore
        self.apiClient = apiClient
        self.biometricManager = biometricManager
        self.logger = logger
        self.skipsLaunchValidation = skipsLaunchValidation

        if let initialStateOverride {
            self.state = initialStateOverride
        } else {
            _ = try? firstRunCleaner?.cleanIfNeeded()
            if let existing = try? tokenStore.read() {
                if existing.mustChangePassword {
                    self.state = .mustChangePassword
                } else if biometricManager?.shouldLockOnColdLaunch() == true {
                    self.state = .locked
                } else {
                    self.state = .active
                }
            } else {
                biometricManager?.clearForSessionEnd()
                self.state = .signedOut(reason: .none)
            }
        }

        apiClient.refresher.eventSink.setHandler { [weak self] event in
            await MainActor.run {
                self?.applyRefreshEvent(event)
            }
        }
    }

    func validateSessionOnLaunch() async {
        guard !skipsLaunchValidation, !hasTriggeredLaunchValidation else { return }
        hasTriggeredLaunchValidation = true
        // Wait until biometric unlock succeeds before refreshing a locked session (Spec AC-65).
        guard state == .active || state == .mustChangePassword else { return }
        await probeStoredSession()
    }

    func validateSessionAfterUnlock() async {
        guard state == .active || state == .mustChangePassword else { return }
        await probeStoredSession()
    }

    func retryConnection() async {
        guard state.isAuthenticated, !isRetryingConnection else { return }
        isRetryingConnection = true
        defer { isRetryingConnection = false }
        await probeStoredSession()
    }

    func lockSession() {
        guard state == .active else { return }
        state = .locked
    }

    func unlockSession() {
        guard state == .locked else { return }
        state = .active
    }

    func login(identifier: String, password: String) async throws {
        let trimmedIdentifier = identifier.trimmingCharacters(in: .whitespacesAndNewlines)
        do {
            let tokens = try await apiClient.login(identifier: trimmedIdentifier, password: password)
            await apiClient.refresher.invalidate()
            try tokenStore.write(tokens)
            connectivityState = .online
            state = tokens.mustChangePassword ? .mustChangePassword : .active
            if !tokens.mustChangePassword {
                biometricManager?.presentEnablePromptIfEligible()
            }
            logger.log(.info, category: "session", message: "Mobile login succeeded")
        } catch {
            let apiError = MobileApiError.classify(error)
            if apiError.statusCode == 403, apiError.apiCode == "APP_STUDENT_ONLY" {
                await apiClient.refresher.invalidate()
                try? tokenStore.clear()
                biometricManager?.clearForSessionEnd()
                onSessionCleared?()
                connectivityState = .online
                state = .signedOut(reason: .studentOnly(username: trimmedIdentifier))
                logger.log(.info, category: "session", message: "Login rejected: APP_STUDENT_ONLY")
                return
            }
            logger.log(
                .warning,
                category: "session",
                message: "Mobile login failed code=\(apiError.apiCode ?? "network_or_unknown")"
            )
            throw apiError
        }
    }

    func reauthenticateFromLock(identifier: String, password: String) async throws {
        let trimmedIdentifier = identifier.trimmingCharacters(in: .whitespacesAndNewlines)
        let previousRefreshToken = (try? tokenStore.read())?.refreshToken

        do {
            let newTokens = try await apiClient.login(identifier: trimmedIdentifier, password: password)
            await apiClient.refresher.invalidate()
            try tokenStore.write(newTokens)
            biometricManager?.clearForSessionEnd()
            onSessionReplaced?()
            connectivityState = .online
            state = newTokens.mustChangePassword ? .mustChangePassword : .active

            if let previousRefreshToken,
               !previousRefreshToken.isEmpty,
               previousRefreshToken != newTokens.refreshToken
            {
                _ = try? await apiClient.logout(refreshToken: previousRefreshToken)
            }

            if !newTokens.mustChangePassword {
                biometricManager?.presentEnablePromptIfEligible()
            }
            logger.log(.info, category: "session", message: "Lock password fallback re-authentication succeeded")
        } catch {
            let apiError = MobileApiError.classify(error)
            if apiError.statusCode == 403, apiError.apiCode == "APP_STUDENT_ONLY" {
                try? tokenStore.clear()
                biometricManager?.clearForSessionEnd()
                onSessionCleared?()
                connectivityState = .online
                state = .signedOut(reason: .studentOnly(username: trimmedIdentifier))
                await apiClient.refresher.invalidate()
                if let previousRefreshToken, !previousRefreshToken.isEmpty {
                    _ = try? await apiClient.logout(refreshToken: previousRefreshToken)
                }
                logger.log(.info, category: "session", message: "Lock fallback rejected: APP_STUDENT_ONLY")
                return
            }
            logger.log(
                .warning,
                category: "session",
                message: "Lock password fallback failed code=\(apiError.apiCode ?? "network_or_unknown")"
            )
            throw apiError
        }
    }

    var currentUsername: String? {
        (try? tokenStore.read())?.username
    }

    var currentRefreshToken: String? {
        (try? tokenStore.read())?.refreshToken
    }

    func adoptSession(_ tokens: SessionTokens) async throws {
        await apiClient.refresher.invalidate()
        try tokenStore.write(tokens)
        connectivityState = .online
        state = tokens.mustChangePassword ? .mustChangePassword : .active
        logger.log(.info, category: "session", message: "Mobile session established")
    }

    func completeForcedPasswordChange() async throws {
        if let stored = try tokenStore.read() {
            let updated = SessionTokens(
                accessToken: stored.accessToken,
                refreshToken: stored.refreshToken,
                accessExpiresIn: stored.accessExpiresIn,
                refreshExpiresIn: stored.refreshExpiresIn,
                mustChangePassword: false,
                username: stored.username,
                issuedAt: stored.issuedAt
            )
            try tokenStore.write(updated)
        }
        connectivityState = .online
        if state == .mustChangePassword {
            state = .active
            biometricManager?.presentEnablePromptIfEligible()
        }
        logger.log(.info, category: "session", message: "Forced password change completed")
    }

    func clearLocalSession(reason: SignedOutReason = .none) async {
        try? tokenStore.clear()
        biometricManager?.clearForSessionEnd()
        onSessionCleared?()
        connectivityState = .online
        state = .signedOut(reason: reason)
        await apiClient.refresher.invalidate()
    }

    func acknowledgeRoleNotice() {
        if case .signedOut(.studentOnly) = state {
            state = .signedOut(reason: .none)
        }
    }

    func signOut(reason: SignedOutReason = .none) async {
        let refreshToken = (try? tokenStore.read())?.refreshToken
        let hasRefreshToken = (refreshToken?.isEmpty == false)
        guard state.isAuthenticated || hasRefreshToken else {
            return
        }

        // Clear persisted tokens and transition synchronously before awaiting so duplicate taps never emit a second logout.
        try? tokenStore.clear()
        biometricManager?.clearForSessionEnd()
        onSessionCleared?()
        connectivityState = .online
        state = .signedOut(reason: reason)
        await apiClient.refresher.invalidate()

        if let refreshToken, !refreshToken.isEmpty {
            _ = try? await apiClient.logout(refreshToken: refreshToken)
        }
    }

    func applyRefreshEvent(_ event: TokenRefreshEvent) {
        guard state.isAuthenticated else { return }

        switch event {
        case let .refreshed(tokens):
            connectivityState = .online
            if tokens.mustChangePassword {
                state = .mustChangePassword
            } else if state != .locked {
                state = .active
            }
        case .expired:
            biometricManager?.clearForSessionEnd()
            onSessionCleared?()
            connectivityState = .online
            state = .signedOut(reason: .expired)
        case let .studentOnly(username):
            biometricManager?.clearForSessionEnd()
            onSessionCleared?()
            connectivityState = .online
            state = .signedOut(reason: .studentOnly(username: username))
        }
    }

    private func probeStoredSession() async {
        do {
            _ = try await apiClient.refresher.refreshIfNeeded(failedAccessToken: nil)
            if state.isAuthenticated {
                connectivityState = .online
            }
        } catch {
            guard state.isAuthenticated else { return }
            let apiError = MobileApiError.classify(error)
            if apiError.isOfflineOrConnectivity {
                // Offline launch never signs out regardless of device clock (Spec AC-36, AC-90).
                connectivityState = .offline
                logger.log(.info, category: "session", message: "Session launch probe offline; retaining session")
            }
        }
    }

    static func makeForCurrentProcess(
        preferences: AppPreferences,
        biometricManager: BiometricManager? = nil,
        arguments: [String] = ProcessInfo.processInfo.arguments,
        environment: AppEnvironment = .current
    ) -> SessionStore {
        let logger = RedactingAppLogger(environment: environment.kind)

        let defaults: UserDefaults
        let keychainService: String
        let serverURL: URL
        let initialStateOverride: SessionState?
        let resetSession: Bool

        if environment.isDev {
            if let suiteName = argumentValue(after: "-uitest-defaults-suite", in: arguments),
               let suite = UserDefaults(suiteName: suiteName)
            {
                defaults = suite
            } else {
                defaults = .standard
            }

            keychainService = argumentValue(after: "-uitest-keychain-service", in: arguments)
                ?? KeychainTokenStore.defaultService

            if let rawURL = argumentValue(after: "-uitest-api-base-url", in: arguments),
               let parsed = URL(string: rawURL),
               let host = parsed.host?.lowercased(),
               host == "127.0.0.1" || host == "localhost"
            {
                serverURL = parsed
            } else {
                serverURL = environment.apiBaseURL
            }

            if arguments.contains("-uitest-locked") {
                initialStateOverride = .locked
            } else if arguments.contains("-uitest-must-change-password") {
                initialStateOverride = .mustChangePassword
            } else if arguments.contains("-uitest-authenticated") {
                initialStateOverride = .active
            } else {
                initialStateOverride = nil
            }
            resetSession = arguments.contains("-uitest-reset-session")
        } else {
            defaults = .standard
            keychainService = KeychainTokenStore.defaultService
            serverURL = environment.apiBaseURL
            initialStateOverride = nil
            resetSession = false
        }

        let tokenStore = KeychainTokenStore(service: keychainService)
        if resetSession {
            try? tokenStore.clear()
            defaults.removeObject(forKey: FirstRunKeychainCleaner.defaultFlagKey)
        }

        let biometricSecretStore = biometricManager?.secretStore
        let cleaner = FirstRunKeychainCleaner(
            userDefaults: defaults,
            tokenStore: tokenStore,
            additionalCleanup: {
                try? biometricSecretStore?.deleteProtectedSecret()
            }
        )
        let client = MobileAPIClient(
            serverURL: serverURL,
            tokenStore: tokenStore,
            logger: logger,
            localeProvider: {
                await MainActor.run { preferences.locale }
            }
        )

        return SessionStore(
            tokenStore: tokenStore,
            apiClient: client,
            firstRunCleaner: cleaner,
            biometricManager: biometricManager,
            logger: logger,
            initialStateOverride: initialStateOverride,
            skipsLaunchValidation: initialStateOverride != nil
        )
    }

    private static func argumentValue(after flag: String, in arguments: [String]) -> String? {
        guard let index = arguments.firstIndex(of: flag), index + 1 < arguments.count else {
            return nil
        }
        return arguments[index + 1]
    }
}
