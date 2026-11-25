import Foundation
import Observation
import SwiftUI

@Observable
@MainActor
final class BiometricManager {
    private(set) var capability: BiometricCapability
    private(set) var state: BiometricState
    private(set) var isPromptingEnable: Bool = false
    private(set) var isEvaluatingBiometrics: Bool = false
    private(set) var isPrivacyShieldVisible: Bool = false
    private(set) var backgroundedAt: Date?

    let lockPolicy: LockPolicy
    private let authenticator: any BiometricAuthenticator
    let secretStore: any BiometricSecretStore
    private let enrollmentStore: any BiometricEnrollmentStore
    private let promptMemoryStore: any PromptMemoryStore
    private let clock: any AppClock
    private let logger: RedactingAppLogger

    init(
        authenticator: any BiometricAuthenticator = LocalAuthenticationBiometricAuthenticator(),
        secretStore: any BiometricSecretStore = KeychainBiometricSecretStore(),
        enrollmentStore: any BiometricEnrollmentStore = UserDefaultsBiometricEnrollmentStore(),
        promptMemoryStore: any PromptMemoryStore = UserDefaultsPromptMemoryStore(),
        lockPolicy: LockPolicy = LockPolicy(),
        clock: any AppClock = SystemClock(),
        logger: RedactingAppLogger = RedactingAppLogger()
    ) {
        self.authenticator = authenticator
        self.secretStore = secretStore
        self.enrollmentStore = enrollmentStore
        self.promptMemoryStore = promptMemoryStore
        self.lockPolicy = lockPolicy
        self.clock = clock
        self.logger = logger

        let initialCapability = authenticator.currentCapability()
        self.capability = initialCapability
        self.state = Self.resolveState(
            capability: initialCapability,
            isEnrolledInApp: enrollmentStore.read().isEnabled
        )
    }

    var isEnabled: Bool {
        state == .on
    }

    var hasActiveEnrollmentRecord: Bool {
        enrollmentStore.read().isEnabled
    }

    var lastPromptAnswer: PromptAnswer? {
        promptMemoryStore.read().lastAnswer
    }

    var shouldPromptAfterPasswordLogin: Bool {
        capability.isAvailable
            && !isEnabled
            && promptMemoryStore.read().lastAnswer != .later
    }

    func refreshCapability() {
        let updatedCapability = authenticator.currentCapability()
        capability = updatedCapability
        state = Self.resolveState(
            capability: updatedCapability,
            isEnrolledInApp: enrollmentStore.read().isEnabled
        )
    }

    func presentEnablePromptIfEligible() {
        refreshCapability()
        isPromptingEnable = shouldPromptAfterPasswordLogin
    }

    func declineEnablePromptLater() {
        promptMemoryStore.write(PromptMemory(lastAnswer: .later))
        isPromptingEnable = false
    }

    func dismissEnablePromptWithoutDeclining() {
        isPromptingEnable = false
    }

    func enableFromPrompt(refreshToken: String, reason: String) async -> BiometricEvaluationResult {
        promptMemoryStore.write(PromptMemory(lastAnswer: .yes))
        let result = await enable(refreshToken: refreshToken, reason: reason)
        if result == .succeeded {
            isPromptingEnable = false
        }
        return result
    }

    func enable(refreshToken: String, reason: String) async -> BiometricEvaluationResult {
        refreshCapability()
        guard capability.isAvailable else {
            return .unavailable(capability.unavailableReason ?? .noHardware)
        }
        guard !refreshToken.isEmpty else {
            return .failed
        }

        isEvaluatingBiometrics = true
        defer { isEvaluatingBiometrics = false }

        let result = await authenticator.authenticateForEnable(reason: reason)
        guard result == .succeeded else {
            refreshCapability()
            return result
        }

        do {
            try secretStore.writeProtectedSecret(Data(refreshToken.utf8))
            let domainState = authenticator.currentDomainState()
            enrollmentStore.write(BiometricEnrollmentRecord(isEnabled: true, domainState: domainState))
            capability = authenticator.currentCapability()
            state = .on
            logger.log(.info, category: "biometric", message: "Biometric unlock enabled kind=\(capability.kind.rawValue)")
            return .succeeded
        } catch {
            try? secretStore.deleteProtectedSecret()
            enrollmentStore.clear()
            refreshCapability()
            logger.log(.warning, category: "biometric", message: "Failed storing biometric protected item")
            return .failed
        }
    }

    func disable() {
        try? secretStore.deleteProtectedSecret()
        enrollmentStore.clear()
        backgroundedAt = nil
        isPrivacyShieldVisible = false
        refreshCapability()
        logger.log(.info, category: "biometric", message: "Biometric unlock disabled")
    }

    func clearForSessionEnd() {
        try? secretStore.deleteProtectedSecret()
        enrollmentStore.clear()
        backgroundedAt = nil
        isPromptingEnable = false
        isPrivacyShieldVisible = false
        refreshCapability()
    }

    func invalidateEnrollment() {
        try? secretStore.deleteProtectedSecret()
        enrollmentStore.clear()
        backgroundedAt = nil
        refreshCapability()
        logger.log(.warning, category: "biometric", message: "Biometric enrollment invalidated; requiring password")
    }

    @discardableResult
    func detectAndHandleInvalidationIfNeeded() -> Bool {
        let record = enrollmentStore.read()
        guard record.isEnabled else { return false }

        let currentCapability = authenticator.currentCapability()
        capability = currentCapability

        if currentCapability.unavailableReason == .notEnrolled {
            invalidateEnrollment()
            return true
        }

        if let enrolledDomainState = record.domainState,
           let liveDomainState = authenticator.currentDomainState(),
           enrolledDomainState != liveDomainState
        {
            invalidateEnrollment()
            return true
        }

        if !secretStore.hasProtectedSecret() {
            invalidateEnrollment()
            return true
        }

        return false
    }

    func attemptUnlock(reason: String) async -> BiometricUnlockResult {
        let record = enrollmentStore.read()
        guard record.isEnabled else {
            invalidateEnrollment()
            return .invalidated
        }

        let currentCapability = authenticator.currentCapability()
        capability = currentCapability
        if let unavailableReason = currentCapability.unavailableReason {
            if unavailableReason == .notEnrolled {
                invalidateEnrollment()
                return .invalidated
            }
            return .unavailable(unavailableReason)
        }

        let liveDomainState = authenticator.currentDomainState()
        if let enrolledDomainState = record.domainState,
           let liveDomainState,
           enrolledDomainState != liveDomainState
        {
            invalidateEnrollment()
            return .invalidated
        }

        if !secretStore.hasProtectedSecret() {
            invalidateEnrollment()
            return .invalidated
        }

        isEvaluatingBiometrics = true
        defer { isEvaluatingBiometrics = false }

        let result = await secretStore.readProtectedSecret(reason: reason)
        switch result {
        case let .succeeded(data):
            let postDomainState = authenticator.currentDomainState()
            if let enrolledDomainState = record.domainState,
               let postDomainState,
               enrolledDomainState != postDomainState
            {
                invalidateEnrollment()
                return .invalidated
            }
            return .succeeded(data)
        case .invalidated:
            invalidateEnrollment()
            return .invalidated
        case .unavailable(.notEnrolled):
            invalidateEnrollment()
            return .invalidated
        case .cancelled, .failed, .lockout, .unavailable:
            return result
        }
    }

    func shouldLockOnColdLaunch() -> Bool {
        let enabled = isEnabled || enrollmentStore.read().isEnabled
        return lockPolicy.shouldLock(
            isBiometricEnabled: enabled,
            isColdLaunch: true,
            backgroundedAt: nil,
            now: clock.now
        )
    }

    @discardableResult
    func handleScenePhaseChange(
        _ phase: ScenePhase,
        sessionState: SessionState
    ) -> Bool {
        let isAuthenticated = sessionState.isAuthenticated
        let protectionActive = isEnabled || enrollmentStore.read().isEnabled || sessionState == .locked

        switch phase {
        case .inactive:
            if isAuthenticated && protectionActive && !isEvaluatingBiometrics {
                isPrivacyShieldVisible = true
            }
            return false

        case .background:
            if isAuthenticated && protectionActive {
                isPrivacyShieldVisible = true
                if sessionState == .active && (isEnabled || enrollmentStore.read().isEnabled) && backgroundedAt == nil {
                    backgroundedAt = clock.now
                }
            }
            return false

        case .active:
            isPrivacyShieldVisible = false
            refreshCapability()
            let shouldLock = sessionState == .active && lockPolicy.shouldLock(
                isBiometricEnabled: isEnabled || enrollmentStore.read().isEnabled,
                isColdLaunch: false,
                backgroundedAt: backgroundedAt,
                now: clock.now
            )
            backgroundedAt = nil
            return shouldLock

        @unknown default:
            return false
        }
    }

    private static func resolveState(
        capability: BiometricCapability,
        isEnrolledInApp: Bool
    ) -> BiometricState {
        if let reason = capability.unavailableReason {
            return .unavailable(reason)
        }
        return isEnrolledInApp ? .on : .off
    }

    static func makeForCurrentProcess(
        arguments: [String] = ProcessInfo.processInfo.arguments,
        environment: AppEnvironment = .current
    ) -> BiometricManager {
        let defaults: UserDefaults
        let keychainService: String
        let lockTimeout: TimeInterval

        if environment.isDev {
            if let suiteName = argumentValue(after: "-uitest-defaults-suite", in: arguments),
               let suite = UserDefaults(suiteName: suiteName)
            {
                defaults = suite
            } else {
                defaults = .standard
            }

            if let baseService = argumentValue(after: "-uitest-keychain-service", in: arguments) {
                keychainService = "\(baseService).biometric"
            } else {
                keychainService = KeychainBiometricSecretStore.defaultService
            }

            if let rawTimeout = argumentValue(after: "-uitest-lock-timeout", in: arguments),
               let parsedTimeout = TimeInterval(rawTimeout),
               parsedTimeout > 0
            {
                lockTimeout = parsedTimeout
            } else {
                lockTimeout = LockPolicy.defaultTimeoutSeconds
            }
        } else {
            defaults = .standard
            keychainService = KeychainBiometricSecretStore.defaultService
            lockTimeout = LockPolicy.defaultTimeoutSeconds
        }

        let secretStore = KeychainBiometricSecretStore(service: keychainService)
        let enrollmentStore = UserDefaultsBiometricEnrollmentStore(userDefaults: defaults)
        let promptMemoryStore = UserDefaultsPromptMemoryStore(userDefaults: defaults)

        if environment.isDev && arguments.contains("-uitest-reset-session") {
            try? secretStore.deleteProtectedSecret()
            enrollmentStore.clear()
        }
        if environment.isDev && arguments.contains("-uitest-reset-preferences") {
            promptMemoryStore.clear()
        }

        return BiometricManager(
            authenticator: LocalAuthenticationBiometricAuthenticator(),
            secretStore: secretStore,
            enrollmentStore: enrollmentStore,
            promptMemoryStore: promptMemoryStore,
            lockPolicy: LockPolicy(timeoutSeconds: lockTimeout),
            clock: SystemClock(),
            logger: RedactingAppLogger(environment: environment.kind)
        )
    }

    private static func argumentValue(after flag: String, in arguments: [String]) -> String? {
        guard let index = arguments.firstIndex(of: flag), index + 1 < arguments.count else {
            return nil
        }
        return arguments[index + 1]
    }
}
