import Foundation

actor IdempotentOperation {
    private var currentKey: String?
    private var isInFlight = false
    private let keyGenerator: @Sendable () -> String

    init(
        initialKey: String? = nil,
        keyGenerator: @escaping @Sendable () -> String = { UUID().uuidString }
    ) {
        self.currentKey = initialKey
        self.keyGenerator = keyGenerator
    }

    var activeKey: String? {
        currentKey
    }

    var isRunning: Bool {
        isInFlight
    }

    func restore(key: String) {
        let trimmed = key.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        currentKey = trimmed
    }

    func reset() {
        currentKey = nil
    }

    @discardableResult
    func run<T: Sendable>(
        _ operation: @Sendable (String) async throws -> T
    ) async throws -> T? {
        guard !isInFlight else {
            return nil
        }

        isInFlight = true
        defer { isInFlight = false }

        let key: String
        if let existing = currentKey {
            key = existing
        } else {
            let generated = keyGenerator()
            currentKey = generated
            key = generated
        }

        do {
            let value = try await operation(key)
            currentKey = nil
            return value
        } catch {
            let classified = MobileApiError.classify(error)
            if !Self.shouldRetainKey(for: classified) {
                currentKey = nil
            }
            throw error
        }
    }

    static func shouldRetainKey(for error: MobileApiError) -> Bool {
        switch error {
        case .network:
            return true
        case let .api(statusCode, _), let .unexpectedResponse(statusCode):
            return shouldRetainKey(forStatusCode: statusCode)
        }
    }

    static func shouldRetainKey(forStatusCode statusCode: Int) -> Bool {
        if statusCode == 408 || statusCode == 429 {
            return true
        }
        if (500...599).contains(statusCode) {
            return true
        }
        if statusCode == 0 {
            return true
        }
        return false
    }
}
