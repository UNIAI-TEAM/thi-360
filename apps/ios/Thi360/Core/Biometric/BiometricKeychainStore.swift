import Foundation
@preconcurrency import LocalAuthentication
import Security

enum BiometricSecretStoreError: Error, Equatable {
    case accessControlCreationFailed
    case unexpectedStatus(OSStatus)
}

protocol BiometricSecretStore: Sendable {
    func writeProtectedSecret(_ data: Data) throws
    func readProtectedSecret(reason: String) async -> BiometricUnlockResult
    func deleteProtectedSecret() throws
    func hasProtectedSecret() -> Bool
}

protocol BiometricAuthenticator: Sendable {
    func currentCapability() -> BiometricCapability
    func currentDomainState() -> Data?
    func authenticateForEnable(reason: String) async -> BiometricEvaluationResult
}

final class KeychainBiometricSecretStore: BiometricSecretStore, @unchecked Sendable {
    static let defaultService = "vn.unicom.thi360.biometric"
    static let defaultAccount = "biometric-protected-refresh-token"

    let service: String
    let account: String

    init(
        service: String = KeychainBiometricSecretStore.defaultService,
        account: String = KeychainBiometricSecretStore.defaultAccount
    ) {
        self.service = service
        self.account = account
    }

    func writeProtectedSecret(_ data: Data) throws {
        try deleteProtectedSecret()

        var cfError: Unmanaged<CFError>?
        // `.biometryCurrentSet` binds the Keychain item to the currently enrolled biometric set so re-enrollment invalidates it.
        guard let accessControl = SecAccessControlCreateWithFlags(
            kCFAllocatorDefault,
            kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            .biometryCurrentSet,
            &cfError
        ) else {
            throw BiometricSecretStoreError.accessControlCreationFailed
        }

        var addQuery = baseQuery()
        addQuery[kSecValueData as String] = data
        addQuery[kSecAttrAccessControl as String] = accessControl

        let status = SecItemAdd(addQuery as CFDictionary, nil)
        guard status == errSecSuccess else {
            throw BiometricSecretStoreError.unexpectedStatus(status)
        }
    }

    func readProtectedSecret(reason: String) async -> BiometricUnlockResult {
        let context = LAContext()
        context.localizedReason = reason
        context.localizedFallbackTitle = ""

        var policyError: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &policyError) else {
            return Self.classifyLAError(policyError)
        }

        // Authenticate the LAContext explicitly before Keychain lookup so both real SEP devices (reusing the authenticated context without double-prompting) and Simulators enforce biometrics.
        let evaluationOutcome: BiometricUnlockResult? = await withCheckedContinuation { continuation in
            context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: reason) { success, error in
                if success {
                    continuation.resume(returning: nil)
                } else {
                    continuation.resume(returning: Self.classifyLAError(error as NSError?))
                }
            }
        }
        if let failure = evaluationOutcome {
            return failure
        }

        return await withCheckedContinuation { continuation in
            DispatchQueue.global(qos: .userInitiated).async { [self] in
                var query = baseQuery()
                query[kSecReturnData as String] = kCFBooleanTrue
                query[kSecMatchLimit as String] = kSecMatchLimitOne
                query[kSecUseAuthenticationContext as String] = context

                var item: CFTypeRef?
                let status = SecItemCopyMatching(query as CFDictionary, &item)

                switch status {
                case errSecSuccess:
                    if let data = item as? Data {
                        continuation.resume(returning: .succeeded(data))
                    } else {
                        continuation.resume(returning: .invalidated)
                    }
                case errSecUserCanceled:
                    continuation.resume(returning: .cancelled)
                case errSecItemNotFound:
                    continuation.resume(returning: .invalidated)
                case errSecAuthFailed:
                    continuation.resume(returning: classifyAuthFailure())
                case errSecInteractionNotAllowed:
                    continuation.resume(returning: .failed)
                default:
                    continuation.resume(returning: .failed)
                }
            }
        }
    }

    func deleteProtectedSecret() throws {
        let status = SecItemDelete(baseQuery() as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw BiometricSecretStoreError.unexpectedStatus(status)
        }
    }

    func hasProtectedSecret() -> Bool {
        let context = LAContext()
        context.interactionNotAllowed = true

        var query = baseQuery()
        query[kSecReturnAttributes as String] = kCFBooleanTrue
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        query[kSecUseAuthenticationContext as String] = context

        let status = SecItemCopyMatching(query as CFDictionary, nil)
        return status == errSecSuccess || status == errSecInteractionNotAllowed
    }

    private func classifyAuthFailure() -> BiometricUnlockResult {
        let probe = LAContext()
        var nsError: NSError?
        _ = probe.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &nsError)
        return Self.classifyLAError(nsError)
    }

    private static func classifyLAError(_ error: NSError?) -> BiometricUnlockResult {
        guard let laError = error as? LAError else {
            return .failed
        }
        switch laError.code {
        case .userCancel, .systemCancel, .appCancel:
            return .cancelled
        case .biometryLockout:
            return .lockout
        case .biometryNotEnrolled:
            return .invalidated
        case .biometryNotAvailable:
            return .unavailable(.noHardware)
        case .passcodeNotSet:
            return .unavailable(.passcodeNotSet)
        case .authenticationFailed, .userFallback:
            return .failed
        default:
            return .failed
        }
    }

    private func baseQuery() -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
    }
}

final class LocalAuthenticationBiometricAuthenticator: BiometricAuthenticator, @unchecked Sendable {
    func currentCapability() -> BiometricCapability {
        let context = LAContext()
        var error: NSError?
        let canEvaluate = context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error)
        let kind = Self.mapBiometryType(context.biometryType)

        if canEvaluate {
            guard kind != .none else {
                return .unavailable(.noHardware)
            }
            return BiometricCapability(kind: kind, unavailableReason: nil)
        }

        guard let laError = error as? LAError else {
            return .unavailable(.noHardware, kind: kind)
        }

        switch laError.code {
        case .biometryLockout:
            return BiometricCapability(kind: kind == .none ? .faceID : kind, unavailableReason: nil)
        case .biometryNotEnrolled:
            return .unavailable(.notEnrolled, kind: kind)
        case .passcodeNotSet:
            return .unavailable(.passcodeNotSet, kind: kind)
        case .biometryNotAvailable:
            return .unavailable(.noHardware, kind: .none)
        default:
            return .unavailable(.noHardware, kind: kind)
        }
    }

    func currentDomainState() -> Data? {
        let context = LAContext()
        _ = context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil)
        return context.evaluatedPolicyDomainState
    }

    func authenticateForEnable(reason: String) async -> BiometricEvaluationResult {
        let context = LAContext()
        context.localizedFallbackTitle = ""

        var authError: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &authError) else {
            return Self.mapEvaluationError(authError)
        }

        return await withCheckedContinuation { continuation in
            context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: reason) { success, error in
                if success {
                    continuation.resume(returning: .succeeded)
                } else {
                    continuation.resume(returning: Self.mapEvaluationError(error as NSError?))
                }
            }
        }
    }

    private static func mapBiometryType(_ type: LABiometryType) -> BiometricKind {
        switch type {
        case .faceID:
            return .faceID
        case .touchID:
            return .touchID
        case .opticID:
            return .faceID
        case .none:
            return .none
        @unknown default:
            return .none
        }
    }

    private static func mapEvaluationError(_ error: NSError?) -> BiometricEvaluationResult {
        guard let laError = error as? LAError else {
            return .failed
        }
        switch laError.code {
        case .userCancel, .systemCancel, .appCancel:
            return .cancelled
        case .biometryLockout:
            return .lockout
        case .biometryNotEnrolled:
            return .unavailable(.notEnrolled)
        case .biometryNotAvailable:
            return .unavailable(.noHardware)
        case .passcodeNotSet:
            return .unavailable(.passcodeNotSet)
        case .authenticationFailed, .userFallback:
            return .failed
        default:
            return .failed
        }
    }
}

final class InMemoryBiometricSecretStore: BiometricSecretStore, @unchecked Sendable {
    private let lock = NSLock()
    private var stored: Data?
    var nextReadOutcomeOverride: BiometricUnlockResult?
    var isInvalidated: Bool = false

    func writeProtectedSecret(_ data: Data) throws {
        lock.lock()
        defer { lock.unlock() }
        stored = data
        isInvalidated = false
    }

    func readProtectedSecret(reason: String) async -> BiometricUnlockResult {
        readOutcomeSynchronously()
    }

    private func readOutcomeSynchronously() -> BiometricUnlockResult {
        lock.lock()
        let override = nextReadOutcomeOverride
        nextReadOutcomeOverride = nil
        let invalidated = isInvalidated
        let currentData = stored
        lock.unlock()

        if let override {
            return override
        }
        if invalidated {
            return .invalidated
        }
        guard let currentData else {
            return .invalidated
        }
        return .succeeded(currentData)
    }

    func deleteProtectedSecret() throws {
        lock.lock()
        defer { lock.unlock() }
        stored = nil
        isInvalidated = false
    }

    func hasProtectedSecret() -> Bool {
        lock.lock()
        defer { lock.unlock() }
        return stored != nil && !isInvalidated
    }

    func peekStoredDataForTesting() -> Data? {
        lock.lock()
        defer { lock.unlock() }
        return stored
    }
}
