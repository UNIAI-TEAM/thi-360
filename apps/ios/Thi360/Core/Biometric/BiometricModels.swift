import Foundation

enum BiometricKind: String, Codable, Equatable, Sendable {
    case faceID
    case touchID
    case none

    var displayName: String {
        switch self {
        case .faceID: "Face ID"
        case .touchID: "Touch ID"
        case .none: ""
        }
    }

    var symbolName: String {
        switch self {
        case .faceID: "faceid"
        case .touchID: "touchid"
        case .none: "lock.shield"
        }
    }
}

enum BiometricUnavailableReason: String, Codable, Equatable, Sendable {
    case noHardware
    case notEnrolled
    case passcodeNotSet
}

enum BiometricState: Equatable, Sendable {
    case unavailable(BiometricUnavailableReason)
    case off
    case on
}

enum PromptAnswer: String, Codable, Equatable, Sendable {
    case yes
    case later
}

struct PromptMemory: Codable, Equatable, Sendable {
    var lastAnswer: PromptAnswer?

    init(lastAnswer: PromptAnswer? = nil) {
        self.lastAnswer = lastAnswer
    }
}

struct BiometricCapability: Equatable, Sendable {
    let kind: BiometricKind
    let unavailableReason: BiometricUnavailableReason?

    var isAvailable: Bool {
        kind != .none && unavailableReason == nil
    }

    static let faceIDAvailable = BiometricCapability(kind: .faceID, unavailableReason: nil)
    static let touchIDAvailable = BiometricCapability(kind: .touchID, unavailableReason: nil)

    static func unavailable(_ reason: BiometricUnavailableReason, kind: BiometricKind = .none) -> BiometricCapability {
        BiometricCapability(kind: kind, unavailableReason: reason)
    }
}

enum BiometricEvaluationResult: Equatable, Sendable {
    case succeeded
    case cancelled
    case failed
    case lockout
    case invalidated
    case unavailable(BiometricUnavailableReason)
}

enum BiometricUnlockResult: Equatable, Sendable {
    case succeeded(Data)
    case cancelled
    case failed
    case lockout
    case invalidated
    case unavailable(BiometricUnavailableReason)
}

struct BiometricEnrollmentRecord: Codable, Equatable, Sendable {
    let isEnabled: Bool
    let domainState: Data?

    static let disabled = BiometricEnrollmentRecord(isEnabled: false, domainState: nil)
}

protocol PromptMemoryStore: Sendable {
    func read() -> PromptMemory
    func write(_ memory: PromptMemory)
    func clear()
}

final class UserDefaultsPromptMemoryStore: PromptMemoryStore, @unchecked Sendable {
    static let defaultKey = "thi360.biometric.promptLastAnswer"

    private let defaults: UserDefaults
    private let key: String

    init(userDefaults: UserDefaults = .standard, key: String = UserDefaultsPromptMemoryStore.defaultKey) {
        self.defaults = userDefaults
        self.key = key
    }

    func read() -> PromptMemory {
        guard let raw = defaults.string(forKey: key), let answer = PromptAnswer(rawValue: raw) else {
            return PromptMemory(lastAnswer: nil)
        }
        return PromptMemory(lastAnswer: answer)
    }

    func write(_ memory: PromptMemory) {
        if let answer = memory.lastAnswer {
            defaults.set(answer.rawValue, forKey: key)
        } else {
            defaults.removeObject(forKey: key)
        }
    }

    func clear() {
        defaults.removeObject(forKey: key)
    }
}

final class InMemoryPromptMemoryStore: PromptMemoryStore, @unchecked Sendable {
    private let lock = NSLock()
    private var stored: PromptMemory

    init(initial: PromptMemory = PromptMemory()) {
        self.stored = initial
    }

    func read() -> PromptMemory {
        lock.lock()
        defer { lock.unlock() }
        return stored
    }

    func write(_ memory: PromptMemory) {
        lock.lock()
        defer { lock.unlock() }
        stored = memory
    }

    func clear() {
        lock.lock()
        defer { lock.unlock() }
        stored = PromptMemory()
    }
}

protocol BiometricEnrollmentStore: Sendable {
    func read() -> BiometricEnrollmentRecord
    func write(_ record: BiometricEnrollmentRecord)
    func clear()
}

final class UserDefaultsBiometricEnrollmentStore: BiometricEnrollmentStore, @unchecked Sendable {
    static let defaultKey = "thi360.biometric.enrollmentRecord"

    private let defaults: UserDefaults
    private let key: String

    init(userDefaults: UserDefaults = .standard, key: String = UserDefaultsBiometricEnrollmentStore.defaultKey) {
        self.defaults = userDefaults
        self.key = key
    }

    func read() -> BiometricEnrollmentRecord {
        guard let data = defaults.data(forKey: key),
              let record = try? JSONDecoder().decode(BiometricEnrollmentRecord.self, from: data)
        else {
            return .disabled
        }
        return record
    }

    func write(_ record: BiometricEnrollmentRecord) {
        guard let data = try? JSONEncoder().encode(record) else { return }
        defaults.set(data, forKey: key)
    }

    func clear() {
        defaults.removeObject(forKey: key)
    }
}

final class InMemoryBiometricEnrollmentStore: BiometricEnrollmentStore, @unchecked Sendable {
    private let lock = NSLock()
    private var stored: BiometricEnrollmentRecord

    init(initial: BiometricEnrollmentRecord = .disabled) {
        self.stored = initial
    }

    func read() -> BiometricEnrollmentRecord {
        lock.lock()
        defer { lock.unlock() }
        return stored
    }

    func write(_ record: BiometricEnrollmentRecord) {
        lock.lock()
        defer { lock.unlock() }
        stored = record
    }

    func clear() {
        lock.lock()
        defer { lock.unlock() }
        stored = .disabled
    }
}
