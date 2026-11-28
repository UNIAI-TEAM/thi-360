import Foundation
import HTTPTypes
import OpenAPIRuntime

final class PlayerQuestionBodyStore: @unchecked Sendable {
    private let lock = NSLock()
    private var bodiesByAttempt: [String: [ItemID: AttemptQuestionBodyValue]] = [:]

    func update(_ bodies: [ItemID: AttemptQuestionBodyValue], for attemptId: String) {
        lock.lock()
        var existing = bodiesByAttempt[attemptId] ?? [:]
        for (key, value) in bodies {
            existing[key] = value
        }
        bodiesByAttempt[attemptId] = existing
        lock.unlock()
    }

    func replace(_ bodies: [ItemID: AttemptQuestionBodyValue], for attemptId: String) {
        lock.lock()
        bodiesByAttempt[attemptId] = bodies
        lock.unlock()
    }

    func bodies(for attemptId: String) -> [ItemID: AttemptQuestionBodyValue] {
        lock.lock()
        defer { lock.unlock() }
        return bodiesByAttempt[attemptId] ?? [:]
    }

    func remove(for attemptId: String) {
        lock.lock()
        bodiesByAttempt.removeValue(forKey: attemptId)
        lock.unlock()
    }

    func removeAll() {
        lock.lock()
        bodiesByAttempt.removeAll()
        lock.unlock()
    }
}

struct PlayerQuestionBodyWireMiddleware: ClientMiddleware {
    let store: PlayerQuestionBodyStore

    func intercept(
        _ request: HTTPRequest,
        body: HTTPBody?,
        baseURL: URL,
        operationID: String,
        next: @Sendable (HTTPRequest, HTTPBody?, URL) async throws -> (HTTPResponse, HTTPBody?)
    ) async throws -> (HTTPResponse, HTTPBody?) {
        let (response, responseBody) = try await next(request, body, baseURL)
        guard operationID == Operations.getState.id,
              response.status.code == 200,
              let responseBody
        else {
            return (response, responseBody)
        }

        let data = try await Data(collecting: responseBody, upTo: 8_388_608)
        let extracted = QuestionItemMapper.extractBodies(fromAttemptStateData: data)
        if let attemptId = Self.extractAttemptId(fromStateData: data, requestPath: request.path) {
            store.replace(extracted, for: attemptId)
        }
        return (response, HTTPBody(data))
    }

    static func extractAttemptId(fromStateData data: Data, requestPath: String?) -> String? {
        if let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           let attemptId = root["attemptId"] as? String,
           !attemptId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        {
            return attemptId
        }
        guard let requestPath else { return nil }
        let components = requestPath
            .split(separator: "?", maxSplits: 1)
            .first?
            .split(separator: "/")
            .map(String.init) ?? []
        if let idx = components.lastIndex(of: "attempts"), idx + 1 < components.count {
            return components[idx + 1]
        }
        return nil
    }
}

enum PlayerAttemptAPIFactory {
    static func make(
        apiClient: MobileAPIClient,
        bodyStore: PlayerQuestionBodyStore
    ) -> AttemptAPI {
        let client = apiClient.makeAuthenticatedClient(
            leadingMiddlewares: [
                PlayerQuestionBodyWireMiddleware(store: bodyStore),
                AttemptWireMiddleware()
            ]
        )
        return AttemptAPI(client: client)
    }
}
