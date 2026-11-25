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
    private let logger: RedactingAppLogger
    private let skipsLaunchValidation: Bool
    private var hasTriggeredLaunchValidation = false

    init(
        tokenStore: any TokenStore,
        apiClient: MobileAPIClient,
        firstRunCleaner: FirstRunKeychainCleaner? = nil,
        logger: RedactingAppLogger = RedactingAppLogger(),
        initialStateOverride: SessionState? = nil,
        skipsLaunchValidation: Bool = false
    ) {
        self.tokenStore = tokenStore
        self.apiClient = apiClient
        self.logger = logger
        self.skipsLaunchValidation = skipsLaunchValidation

        if let initialStateOverride {
            self.state = initialStateOverride
        } else {
            _ = try? firstRunCleaner?.cleanIfNeeded()
            if let existing = try? tokenStore.read() {
                self.state = existing.mustChangePassword ? .mustChangePassword : .active
            } else {
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
        guard state.isAuthenticated else { return }
        await probeStoredSession()
    }

    func retryConnection() async {
        guard state.isAuthenticated, !isRetryingConnection else { return }
        isRetryingConnection = true
        defer { isRetryingConnection = false }
        await probeStoredSession()
    }

    func login(identifier: String, password: String) async throws {
        let trimmedIdentifier = identifier.trimmingCharacters(in: .whitespacesAndNewlines)
        do {
            let tokens = try await apiClient.login(identifier: trimmedIdentifier, password: password)
            await apiClient.refresher.invalidate()
            try tokenStore.write(tokens)
            connectivityState = .online
            state = tokens.mustChangePassword ? .mustChangePassword : .active
            logger.log(.info, category: "session", message: "Mobile login succeeded")
        } catch {
            let apiError = MobileApiError.classify(error)
            if apiError.statusCode == 403, apiError.apiCode == "APP_STUDENT_ONLY" {
                await apiClient.refresher.invalidate()
                try? tokenStore.clear()
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

    var currentUsername: String? {
        (try? tokenStore.read())?.username
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
        }
        logger.log(.info, category: "session", message: "Forced password change completed")
    }

    func clearLocalSession(reason: SignedOutReason = .none) async {
        await apiClient.refresher.invalidate()
        try? tokenStore.clear()
        connectivityState = .online
        state = .signedOut(reason: reason)
    }

    func acknowledgeRoleNotice() {
        if case .signedOut(.studentOnly) = state {
            state = .signedOut(reason: .none)
        }
    }

    func signOut(reason: SignedOutReason = .none) async {
        let refreshToken = (try? tokenStore.read())?.refreshToken
        await apiClient.refresher.invalidate()
        try? tokenStore.clear()
        connectivityState = .online
        state = .signedOut(reason: reason)
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
            connectivityState = .online
            state = .signedOut(reason: .expired)
        case let .studentOnly(username):
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

        let cleaner = FirstRunKeychainCleaner(userDefaults: defaults, tokenStore: tokenStore)
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
