import Foundation
import HTTPTypes
import OpenAPIRuntime

protocol AttemptAudioGateway: Sendable {
    func openPlay(assetId: String, requestId: String) async throws -> AudioPlaySnapshot
    func resume(assetId: String, playId: String) async throws -> AudioPlaySnapshot
    func progress(assetId: String, playId: String, positionMs: Int64) async throws -> AudioPlaySnapshot
    func end(assetId: String, playId: String) async throws -> AudioPlaySnapshot
    func fetchAudio(assetId: String, playId: String) async throws -> Data
}

private struct AudioAssetDownloadMiddleware: ClientMiddleware {
    func intercept(
        _ request: HTTPRequest,
        body: HTTPBody?,
        baseURL: URL,
        operationID: String,
        next: @Sendable (HTTPRequest, HTTPBody?, URL) async throws -> (HTTPResponse, HTTPBody?)
    ) async throws -> (HTTPResponse, HTTPBody?) {
        var outgoing = request
        if operationID == Operations.downloadAttemptAsset.id {
            outgoing.headerFields[.cacheControl] = "no-store"
        }
        let (response, responseBody) = try await next(outgoing, body, baseURL)
        guard response.status.code == 200,
              operationID == Operations.downloadAttemptAsset.id
        else {
            return (response, responseBody)
        }
        // Backend serves audio/mpeg, audio/wav, etc. while OpenAPI declares application/octet-stream.
        var normalized = response
        normalized.headerFields[.contentType] = "application/octet-stream"
        return (normalized, responseBody)
    }
}

final class OpenAPIAttemptAudioGateway: AttemptAudioGateway, Sendable {
    static let maxAudioBytes = 50 * 1024 * 1024

    private let attemptId: String
    private let client: Client
    private let activeSessionIdProvider: @Sendable () async -> String?

    init(
        attemptId: String,
        client: Client,
        activeSessionIdProvider: @escaping @Sendable () async -> String?
    ) {
        self.attemptId = attemptId
        self.client = client
        self.activeSessionIdProvider = activeSessionIdProvider
    }

    convenience init(
        attemptId: String,
        apiClient: MobileAPIClient,
        activeSessionIdProvider: @escaping @Sendable () async -> String?
    ) {
        self.init(
            attemptId: attemptId,
            client: apiClient.makeAuthenticatedClient(leadingMiddlewares: [AudioAssetDownloadMiddleware()]),
            activeSessionIdProvider: activeSessionIdProvider
        )
    }

    func openPlay(assetId: String, requestId: String) async throws -> AudioPlaySnapshot {
        let sessionId = try await requireEditingSessionId()
        do {
            let output = try await client.openAudioPlay(
                path: .init(attemptId: attemptId, assetId: assetId),
                headers: .init(X_hyphen_Attempt_hyphen_Session: sessionId),
                body: .json(.init(requestId: requestId))
            )
            switch output {
            case let .ok(response):
                return AudioPlaySnapshot.fromResponse(try response.body.json)
            case let .conflict(response):
                let conflict = try response.body.json
                throw Self.mapConflict(code: conflict.code, message: conflict.message, playId: conflict.playId)
            case .unauthorized:
                throw AudioError.unauthorized
            case let .badRequest(response):
                throw await Self.decodeError(statusCode: 400, body: try response.body.any)
            case let .forbidden(response):
                throw await Self.decodeError(statusCode: 403, body: try response.body.any)
            case let .notFound(response):
                throw await Self.decodeError(statusCode: 404, body: try response.body.any)
            case let .preconditionFailed(response):
                throw await Self.decodeError(statusCode: 412, body: try response.body.any)
            case let .contentTooLarge(response):
                throw await Self.decodeError(statusCode: 413, body: try response.body.any)
            case let .unprocessableContent(response):
                throw await Self.decodeError(statusCode: 422, body: try response.body.any)
            case let .tooManyRequests(response):
                throw await Self.decodeError(statusCode: 429, body: try response.body.any)
            case let .internalServerError(response):
                throw await Self.decodeError(statusCode: 500, body: try response.body.any)
            case let .notImplemented(response):
                throw await Self.decodeError(statusCode: 501, body: try response.body.any)
            case let .badGateway(response):
                throw await Self.decodeError(statusCode: 502, body: try response.body.any)
            case let .undocumented(statusCode, payload):
                throw await Self.decodeOptionalError(statusCode: statusCode, body: payload.body)
            }
        } catch let error as AudioError {
            throw error
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            throw Self.classifyTransportError(error)
        }
    }

    func resume(assetId: String, playId: String) async throws -> AudioPlaySnapshot {
        let sessionId = try await requireEditingSessionId()
        do {
            let output = try await client.resumeAudioPlay(
                path: .init(attemptId: attemptId, assetId: assetId, playId: playId),
                headers: .init(X_hyphen_Attempt_hyphen_Session: sessionId)
            )
            switch output {
            case let .ok(response):
                return AudioPlaySnapshot.fromResponse(try response.body.json)
            case let .conflict(response):
                throw await Self.decodeError(statusCode: 409, body: try response.body.any)
            case .unauthorized:
                throw AudioError.unauthorized
            case let .badRequest(response):
                throw await Self.decodeError(statusCode: 400, body: try response.body.any)
            case let .forbidden(response):
                throw await Self.decodeError(statusCode: 403, body: try response.body.any)
            case let .notFound(response):
                throw await Self.decodeError(statusCode: 404, body: try response.body.any)
            case let .preconditionFailed(response):
                throw await Self.decodeError(statusCode: 412, body: try response.body.any)
            case let .contentTooLarge(response):
                throw await Self.decodeError(statusCode: 413, body: try response.body.any)
            case let .unprocessableContent(response):
                throw await Self.decodeError(statusCode: 422, body: try response.body.any)
            case let .tooManyRequests(response):
                throw await Self.decodeError(statusCode: 429, body: try response.body.any)
            case let .internalServerError(response):
                throw await Self.decodeError(statusCode: 500, body: try response.body.any)
            case let .notImplemented(response):
                throw await Self.decodeError(statusCode: 501, body: try response.body.any)
            case let .badGateway(response):
                throw await Self.decodeError(statusCode: 502, body: try response.body.any)
            case let .undocumented(statusCode, payload):
                throw await Self.decodeOptionalError(statusCode: statusCode, body: payload.body)
            }
        } catch let error as AudioError {
            throw error
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            throw Self.classifyTransportError(error)
        }
    }

    func progress(assetId: String, playId: String, positionMs: Int64) async throws -> AudioPlaySnapshot {
        let sessionId = try await requireEditingSessionId()
        do {
            let output = try await client.updateAudioProgress(
                path: .init(attemptId: attemptId, assetId: assetId, playId: playId),
                headers: .init(X_hyphen_Attempt_hyphen_Session: sessionId),
                body: .json(.init(positionMs: max(0, positionMs)))
            )
            switch output {
            case let .ok(response):
                return AudioPlaySnapshot.fromResponse(try response.body.json)
            case let .conflict(response):
                let conflict = try response.body.json
                throw Self.mapConflict(
                    code: conflict.code,
                    message: conflict.message,
                    positionMs: conflict.positionMs
                )
            case .unauthorized:
                throw AudioError.unauthorized
            case let .badRequest(response):
                throw await Self.decodeError(statusCode: 400, body: try response.body.any)
            case let .forbidden(response):
                throw await Self.decodeError(statusCode: 403, body: try response.body.any)
            case let .notFound(response):
                throw await Self.decodeError(statusCode: 404, body: try response.body.any)
            case let .preconditionFailed(response):
                throw await Self.decodeError(statusCode: 412, body: try response.body.any)
            case let .contentTooLarge(response):
                throw await Self.decodeError(statusCode: 413, body: try response.body.any)
            case let .unprocessableContent(response):
                throw await Self.decodeError(statusCode: 422, body: try response.body.any)
            case let .tooManyRequests(response):
                throw await Self.decodeError(statusCode: 429, body: try response.body.any)
            case let .internalServerError(response):
                throw await Self.decodeError(statusCode: 500, body: try response.body.any)
            case let .notImplemented(response):
                throw await Self.decodeError(statusCode: 501, body: try response.body.any)
            case let .badGateway(response):
                throw await Self.decodeError(statusCode: 502, body: try response.body.any)
            case let .undocumented(statusCode, payload):
                throw await Self.decodeOptionalError(statusCode: statusCode, body: payload.body)
            }
        } catch let error as AudioError {
            throw error
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            throw Self.classifyTransportError(error)
        }
    }

    func end(assetId: String, playId: String) async throws -> AudioPlaySnapshot {
        let sessionId = try await requireEditingSessionId()
        do {
            let output = try await client.endAudioPlay(
                path: .init(attemptId: attemptId, assetId: assetId, playId: playId),
                headers: .init(X_hyphen_Attempt_hyphen_Session: sessionId)
            )
            switch output {
            case let .ok(response):
                return AudioPlaySnapshot.fromResponse(try response.body.json)
            case let .conflict(response):
                throw await Self.decodeError(statusCode: 409, body: try response.body.any)
            case .unauthorized:
                throw AudioError.unauthorized
            case let .badRequest(response):
                throw await Self.decodeError(statusCode: 400, body: try response.body.any)
            case let .forbidden(response):
                throw await Self.decodeError(statusCode: 403, body: try response.body.any)
            case let .notFound(response):
                throw await Self.decodeError(statusCode: 404, body: try response.body.any)
            case let .preconditionFailed(response):
                throw await Self.decodeError(statusCode: 412, body: try response.body.any)
            case let .contentTooLarge(response):
                throw await Self.decodeError(statusCode: 413, body: try response.body.any)
            case let .unprocessableContent(response):
                throw await Self.decodeError(statusCode: 422, body: try response.body.any)
            case let .tooManyRequests(response):
                throw await Self.decodeError(statusCode: 429, body: try response.body.any)
            case let .internalServerError(response):
                throw await Self.decodeError(statusCode: 500, body: try response.body.any)
            case let .notImplemented(response):
                throw await Self.decodeError(statusCode: 501, body: try response.body.any)
            case let .badGateway(response):
                throw await Self.decodeError(statusCode: 502, body: try response.body.any)
            case let .undocumented(statusCode, payload):
                throw await Self.decodeOptionalError(statusCode: statusCode, body: payload.body)
            }
        } catch let error as AudioError {
            throw error
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            throw Self.classifyTransportError(error)
        }
    }

    func fetchAudio(assetId: String, playId: String) async throws -> Data {
        _ = try await requireEditingSessionId()
        do {
            let output = try await client.downloadAttemptAsset(
                path: .init(attemptId: attemptId, assetId: assetId),
                query: .init(playId: playId)
            )
            switch output {
            case let .ok(response):
                let data = try await Data(collecting: try response.body.binary, upTo: Self.maxAudioBytes)
                guard !data.isEmpty else {
                    throw AudioError.other(statusCode: 200, code: nil, message: "Empty audio payload")
                }
                return data
            case .unauthorized:
                throw AudioError.unauthorized
            case let .conflict(response):
                throw await Self.decodeError(statusCode: 409, body: try response.body.any)
            case let .badRequest(response):
                throw await Self.decodeError(statusCode: 400, body: try response.body.any)
            case let .forbidden(response):
                throw await Self.decodeError(statusCode: 403, body: try response.body.any)
            case let .notFound(response):
                throw await Self.decodeError(statusCode: 404, body: try response.body.any)
            case let .preconditionFailed(response):
                throw await Self.decodeError(statusCode: 412, body: try response.body.any)
            case let .contentTooLarge(response):
                throw await Self.decodeError(statusCode: 413, body: try response.body.any)
            case let .unprocessableContent(response):
                throw await Self.decodeError(statusCode: 422, body: try response.body.any)
            case let .tooManyRequests(response):
                throw await Self.decodeError(statusCode: 429, body: try response.body.any)
            case let .internalServerError(response):
                throw await Self.decodeError(statusCode: 500, body: try response.body.any)
            case let .notImplemented(response):
                throw await Self.decodeError(statusCode: 501, body: try response.body.any)
            case let .badGateway(response):
                throw await Self.decodeError(statusCode: 502, body: try response.body.any)
            case let .undocumented(statusCode, payload):
                if statusCode == 200, let body = payload.body {
                    let data = try await Data(collecting: body, upTo: Self.maxAudioBytes)
                    guard !data.isEmpty else {
                        throw AudioError.other(statusCode: 200, code: nil, message: "Empty audio payload")
                    }
                    return data
                }
                throw await Self.decodeOptionalError(statusCode: statusCode, body: payload.body)
            }
        } catch let error as AudioError {
            throw error
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            throw Self.classifyTransportError(error)
        }
    }

    private func requireEditingSessionId() async throws -> String {
        guard let sessionId = await activeSessionIdProvider(),
              !sessionId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        else {
            throw AudioError.sessionRevoked
        }
        return sessionId
    }

    static func mapConflict(
        code: String,
        message: String? = nil,
        playId: String? = nil,
        positionMs: Int64? = nil
    ) -> AudioError {
        switch code {
        case "AUDIO_PLAY_IN_PROGRESS":
            return .inProgress(playId: playId)
        case "AUDIO_PLAYS_EXHAUSTED":
            return .exhausted
        case "AUDIO_SEEK_REJECTED":
            return .seekRejected(positionMs: positionMs)
        case "AUDIO_PLAY_STALE":
            return .stale
        case "PART_CLOSED":
            return .partClosed
        case "TIME_UP":
            return .timeUp
        case "ATTEMPT_SESSION_REVOKED", "ATTEMPT_OPEN_ELSEWHERE":
            return .sessionRevoked
        default:
            return .other(statusCode: 409, code: code, message: message)
        }
    }

    private static func decodeError(statusCode: Int, body: HTTPBody) async -> AudioError {
        guard let data = try? await Data(collecting: body, upTo: 65_536), !data.isEmpty else {
            return statusCode == 401 ? .unauthorized : .other(statusCode: statusCode, code: nil, message: nil)
        }
        return mapRawError(statusCode: statusCode, data: data)
    }

    private static func decodeOptionalError(statusCode: Int, body: HTTPBody?) async -> AudioError {
        if let body {
            return await decodeError(statusCode: statusCode, body: body)
        }
        return statusCode == 401 ? .unauthorized : .other(statusCode: statusCode, code: nil, message: nil)
    }

    static func mapRawError(statusCode: Int, data: Data) -> AudioError {
        if statusCode == 401 {
            return .unauthorized
        }
        let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
        let code = json?["code"] as? String
        let message = json?["message"] as? String
        if statusCode == 409, let code {
            let playId = json?["playId"] as? String
            let positionMs = (json?["positionMs"] as? NSNumber)?.int64Value
            return mapConflict(code: code, message: message, playId: playId, positionMs: positionMs)
        }
        return .other(statusCode: statusCode, code: code, message: message)
    }

    private static func classifyTransportError(_ error: any Error) -> AudioError {
        let classified = MobileApiError.classify(error)
        switch classified {
        case let .network(code):
            return .network(code)
        case let .api(statusCode, payload):
            if statusCode == 401 { return .unauthorized }
            if statusCode == 409 {
                return mapConflict(code: payload.code, message: payload.message)
            }
            return .other(statusCode: statusCode, code: payload.code, message: payload.message)
        case let .unexpectedResponse(statusCode):
            if statusCode == 401 { return .unauthorized }
            return .other(statusCode: statusCode, code: nil, message: nil)
        }
    }
}
