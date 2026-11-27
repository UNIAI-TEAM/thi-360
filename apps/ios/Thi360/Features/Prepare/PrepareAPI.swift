import Foundation
import HTTPTypes
import OpenAPIRuntime

protocol PrepareAPIClientProtocol: Sendable {
    func prepare(entitlementId: String) async throws -> AttemptPrepareResponse
    func startAttempt(
        entitlementId: String,
        preChoice: PreparePreChoiceInput?,
        idempotencyKey: String
    ) async throws -> StartAttemptResult
    func getAttemptState(attemptId: String) async throws -> AttemptPollResult
}

private final class PrepareWireCallBox: @unchecked Sendable {
    private let lock = NSLock()
    private var _rawAbortReason: String?

    var rawAbortReason: String? {
        lock.lock()
        defer { lock.unlock() }
        return _rawAbortReason
    }

    func setRawAbortReason(_ reason: String?) {
        lock.lock()
        _rawAbortReason = reason
        lock.unlock()
    }
}

private enum PrepareWireContext {
    @TaskLocal static var box: PrepareWireCallBox?
}

struct PrepareWireMiddleware: ClientMiddleware {
    func intercept(
        _ request: HTTPRequest,
        body: HTTPBody?,
        baseURL: URL,
        operationID: String,
        next: @Sendable (HTTPRequest, HTTPBody?, URL) async throws -> (HTTPResponse, HTTPBody?)
    ) async throws -> (HTTPResponse, HTTPBody?) {
        let (response, responseBody) = try await next(request, body, baseURL)
        guard let box = PrepareWireContext.box,
              (operationID == Operations.start.id || operationID == Operations.getState.id),
              (response.status.code == 200 || response.status.code == 202),
              let responseBody
        else {
            return (response, responseBody)
        }

        let collected = try await Data(collecting: responseBody, upTo: 8_388_608)
        let normalized = Self.captureAndNormalizeAbortReason(in: collected, box: box)
        return (response, HTTPBody(normalized))
    }

    // Preserves unknown abortReason codes for generic fallback while keeping generated closed-enum decoding safe.
    private static func captureAndNormalizeAbortReason(in data: Data, box: PrepareWireCallBox) -> Data {
        guard var root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
              let reason = root["abortReason"] as? String
        else {
            return data
        }
        box.setRawAbortReason(reason)
        if PrepareStrings.knownAbortReasons.contains(reason) {
            return data
        }
        root["abortReason"] = NSNull()
        return (try? JSONSerialization.data(withJSONObject: root)) ?? data
    }
}

final class PrepareAPI: PrepareAPIClientProtocol, Sendable {
    private let client: Client

    init(client: Client) {
        self.client = client
    }

    convenience init(apiClient: MobileAPIClient) {
        self.init(client: apiClient.makeAuthenticatedClient(leadingMiddlewares: [PrepareWireMiddleware()]))
    }

    func prepare(entitlementId: String) async throws -> AttemptPrepareResponse {
        do {
            let output = try await client.prepare(path: .init(entitlementId: entitlementId))
            switch output {
            case let .ok(response):
                return try response.body.json
            case let .badRequest(response):
                throw await MobileApiError.decodeErrorBody(statusCode: 400, body: try response.body.any)
            case let .unauthorized(response):
                throw await MobileApiError.decodeErrorBody(statusCode: 401, body: try response.body.any)
            case let .forbidden(response):
                throw await MobileApiError.decodeErrorBody(statusCode: 403, body: try response.body.any)
            case let .notFound(response):
                throw await MobileApiError.decodeErrorBody(statusCode: 404, body: try response.body.any)
            case let .conflict(response):
                throw await MobileApiError.decodeErrorBody(statusCode: 409, body: try response.body.any)
            case let .preconditionFailed(response):
                throw await MobileApiError.decodeErrorBody(statusCode: 412, body: try response.body.any)
            case let .contentTooLarge(response):
                throw await MobileApiError.decodeErrorBody(statusCode: 413, body: try response.body.any)
            case let .unprocessableContent(response):
                throw await MobileApiError.decodeErrorBody(statusCode: 422, body: try response.body.any)
            case let .tooManyRequests(response):
                throw await MobileApiError.decodeErrorBody(statusCode: 429, body: try response.body.any)
            case let .internalServerError(response):
                throw await MobileApiError.decodeErrorBody(statusCode: 500, body: try response.body.any)
            case let .notImplemented(response):
                throw await MobileApiError.decodeErrorBody(statusCode: 501, body: try response.body.any)
            case let .badGateway(response):
                throw await MobileApiError.decodeErrorBody(statusCode: 502, body: try response.body.any)
            case let .undocumented(statusCode, payload):
                throw await Self.decodeOptionalBody(statusCode: statusCode, body: payload.body)
            }
        } catch {
            throw MobileApiError.classify(error)
        }
    }

    func startAttempt(
        entitlementId: String,
        preChoice: PreparePreChoiceInput?,
        idempotencyKey: String
    ) async throws -> StartAttemptResult {
        let box = PrepareWireCallBox()
        return try await PrepareWireContext.$box.withValue(box) {
            do {
                let output = try await client.start(
                    headers: .init(Idempotency_hyphen_Key: idempotencyKey),
                    body: .json(.init(
                        entitlementId: entitlementId,
                        preChoice: preChoice?.generatedInput
                    ))
                )
                switch output {
                case let .ok(response):
                    return Self.mapStartResponse(try response.body.json, rawAbortReason: box.rawAbortReason)
                case let .accepted(response):
                    return Self.mapStartResponse(try response.body.json, rawAbortReason: box.rawAbortReason)
                case let .badRequest(response):
                    throw await MobileApiError.decodeErrorBody(statusCode: 400, body: try response.body.any)
                case let .unauthorized(response):
                    throw await MobileApiError.decodeErrorBody(statusCode: 401, body: try response.body.any)
                case let .forbidden(response):
                    throw await MobileApiError.decodeErrorBody(statusCode: 403, body: try response.body.any)
                case let .notFound(response):
                    throw await MobileApiError.decodeErrorBody(statusCode: 404, body: try response.body.any)
                case let .conflict(response):
                    throw await MobileApiError.decodeErrorBody(statusCode: 409, body: try response.body.any)
                case let .preconditionFailed(response):
                    throw await MobileApiError.decodeErrorBody(statusCode: 412, body: try response.body.any)
                case let .contentTooLarge(response):
                    throw await MobileApiError.decodeErrorBody(statusCode: 413, body: try response.body.any)
                case let .unprocessableContent(response):
                    throw await MobileApiError.decodeErrorBody(statusCode: 422, body: try response.body.any)
                case let .tooManyRequests(response):
                    throw await MobileApiError.decodeErrorBody(statusCode: 429, body: try response.body.any)
                case let .internalServerError(response):
                    throw await MobileApiError.decodeErrorBody(statusCode: 500, body: try response.body.any)
                case let .notImplemented(response):
                    throw await MobileApiError.decodeErrorBody(statusCode: 501, body: try response.body.any)
                case let .badGateway(response):
                    throw await MobileApiError.decodeErrorBody(statusCode: 502, body: try response.body.any)
                case let .undocumented(statusCode, payload):
                    throw await Self.decodeOptionalBody(statusCode: statusCode, body: payload.body)
                }
            } catch {
                throw MobileApiError.classify(error)
            }
        }
    }

    func getAttemptState(attemptId: String) async throws -> AttemptPollResult {
        let box = PrepareWireCallBox()
        return try await PrepareWireContext.$box.withValue(box) {
            do {
                let output = try await client.getState(path: .init(attemptId: attemptId))
                switch output {
                case let .ok(response):
                    let payload = try response.body.json
                    let openState = Self.mapAttemptState(
                        payload.state,
                        abortReason: box.rawAbortReason ?? payload.abortReason?.rawValue
                    )
                    return AttemptPollResult(attemptId: payload.attemptId, state: openState)
                case let .badRequest(response):
                    throw await MobileApiError.decodeErrorBody(statusCode: 400, body: try response.body.any)
                case let .unauthorized(response):
                    throw await MobileApiError.decodeErrorBody(statusCode: 401, body: try response.body.any)
                case let .forbidden(response):
                    throw await MobileApiError.decodeErrorBody(statusCode: 403, body: try response.body.any)
                case let .notFound(response):
                    throw await MobileApiError.decodeErrorBody(statusCode: 404, body: try response.body.any)
                case let .conflict(response):
                    throw await MobileApiError.decodeErrorBody(statusCode: 409, body: try response.body.any)
                case let .preconditionFailed(response):
                    throw await MobileApiError.decodeErrorBody(statusCode: 412, body: try response.body.any)
                case let .contentTooLarge(response):
                    throw await MobileApiError.decodeErrorBody(statusCode: 413, body: try response.body.any)
                case let .unprocessableContent(response):
                    throw await MobileApiError.decodeErrorBody(statusCode: 422, body: try response.body.any)
                case let .tooManyRequests(response):
                    throw await MobileApiError.decodeErrorBody(statusCode: 429, body: try response.body.any)
                case let .internalServerError(response):
                    throw await MobileApiError.decodeErrorBody(statusCode: 500, body: try response.body.any)
                case let .notImplemented(response):
                    throw await MobileApiError.decodeErrorBody(statusCode: 501, body: try response.body.any)
                case let .badGateway(response):
                    throw await MobileApiError.decodeErrorBody(statusCode: 502, body: try response.body.any)
                case let .undocumented(statusCode, payload):
                    throw await Self.decodeOptionalBody(statusCode: statusCode, body: payload.body)
                }
            } catch {
                throw MobileApiError.classify(error)
            }
        }
    }

    private static func mapStartResponse(
        _ payload: Components.Schemas.StartAttemptResponse,
        rawAbortReason: String?
    ) -> StartAttemptResult {
        let openState: AttemptOpenState
        switch payload.state {
        case .STARTING:
            openState = .starting
        case .IN_PROGRESS:
            openState = .inProgress
        case .ABORTED:
            openState = .aborted(abortReason: rawAbortReason ?? payload.abortReason?.rawValue)
        case .GRADING, .PENDING_REVIEW, .SCORED, .GRADING_ERROR:
            openState = .closed
        }
        return StartAttemptResult(
            attemptId: payload.attemptId,
            state: openState,
            resumed: payload.resumed,
            retryable: payload.retryable
        )
    }

    private static func mapAttemptState(
        _ state: Components.Schemas.AttemptStateResponse.statePayload,
        abortReason: String?
    ) -> AttemptOpenState {
        switch state {
        case .STARTING:
            return .starting
        case .IN_PROGRESS:
            return .inProgress
        case .ABORTED:
            return .aborted(abortReason: abortReason)
        case .GRADING, .PENDING_REVIEW, .SCORED, .GRADING_ERROR:
            return .closed
        }
    }

    private static func decodeOptionalBody(statusCode: Int, body: HTTPBody?) async -> MobileApiError {
        if let body {
            return await MobileApiError.decodeErrorBody(statusCode: statusCode, body: body)
        }
        return .unexpectedResponse(statusCode: statusCode)
    }
}

struct PreviewEmptyPrepareAPI: PrepareAPIClientProtocol {
    func prepare(entitlementId: String) async throws -> AttemptPrepareResponse {
        throw MobileApiError.network(.notConnectedToInternet)
    }

    func startAttempt(
        entitlementId: String,
        preChoice: PreparePreChoiceInput?,
        idempotencyKey: String
    ) async throws -> StartAttemptResult {
        throw MobileApiError.network(.notConnectedToInternet)
    }

    func getAttemptState(attemptId: String) async throws -> AttemptPollResult {
        throw MobileApiError.network(.notConnectedToInternet)
    }
}
