import Foundation
import HTTPTypes
import OpenAPIRuntime

struct AttemptAnswerChangeInput: Equatable, Sendable {
    let itemId: ItemID
    let seq: Int64
    let answer: AnswerValue
}

struct AttemptFlagChangeInput: Equatable, Sendable {
    let itemId: ItemID
    let flagged: Bool
}

struct AttemptStateSnapshot: Equatable, Sendable {
    let response: Components.Schemas.AttemptStateResponse
    let decodedAnswers: [ItemID: AnswerValue]

    init(
        response: Components.Schemas.AttemptStateResponse,
        decodedAnswers: [ItemID: AnswerValue] = [:]
    ) {
        self.response = response
        self.decodedAnswers = decodedAnswers
    }
}

enum AttemptAPIError: Error, Equatable, Sendable {
    case conflict(code: String, lastSeenAt: Date?, message: String)
    case rateLimited(retryAfterSeconds: Int?)
    case unauthorized(code: String)
    case clientError(statusCode: Int, code: String, message: String)
    case serverError(statusCode: Int, code: String)
    case network(URLError.Code)

    var apiCode: String? {
        switch self {
        case let .conflict(code, _, _),
             let .unauthorized(code),
             let .clientError(_, code, _),
             let .serverError(_, code):
            return code.isEmpty ? nil : code
        case .rateLimited:
            return "RATE_LIMITED"
        case .network:
            return nil
        }
    }

    var statusCode: Int? {
        switch self {
        case .conflict: 409
        case .rateLimited: 429
        case .unauthorized: 401
        case let .clientError(statusCode, _, _): statusCode
        case let .serverError(statusCode, _): statusCode
        case .network: nil
        }
    }

    var isSessionConflict: Bool {
        guard case let .conflict(code, _, _) = self else { return false }
        return code == "ATTEMPT_SESSION_REVOKED" || code == "ATTEMPT_OPEN_ELSEWHERE"
    }

    var isRetainableIdempotencyError: Bool {
        switch self {
        case .network, .rateLimited, .serverError:
            return true
        case let .clientError(statusCode, _, _):
            return statusCode == 408
        case .conflict, .unauthorized:
            return false
        }
    }

    func toLoadFailure() -> (kind: LoadFailureKind, canRetry: Bool, retryAfter: Int?) {
        switch self {
        case .network:
            return (.offline, true, nil)
        case let .conflict(code, _, _):
            return (.conflict(code: code), false, nil)
        case let .rateLimited(retryAfter):
            return (.server, true, retryAfter)
        case .unauthorized:
            return (.server, true, nil)
        case let .clientError(statusCode, _, _):
            switch statusCode {
            case 404: return (.notFound, false, nil)
            case 410: return (.gone, false, nil)
            case 403: return (.forbidden, false, nil)
            default: return (.server, true, nil)
            }
        case .serverError:
            return (.server, true, nil)
        }
    }
}

protocol AttemptAPIClient: Sendable {
    func getState(attemptId: String) async throws -> AttemptStateSnapshot
    func claimSession(attemptId: String, sessionId: String, takeover: Bool) async throws -> Components.Schemas.ClaimSessionResponse
    func heartbeat(attemptId: String, sessionId: String) async throws -> Components.Schemas.HeartbeatResponse
    func saveAnswers(
        attemptId: String,
        sessionId: String,
        changes: [AttemptAnswerChangeInput],
        flags: [AttemptFlagChangeInput],
        position: Position?
    ) async throws -> Components.Schemas.SaveAnswersResponse
    func finishPart(
        attemptId: String,
        partId: PartID,
        sessionId: String,
        finalAnswers: [AttemptAnswerChangeInput]
    ) async throws -> Components.Schemas.FinishPartResponse
    func submit(
        attemptId: String,
        sessionId: String,
        idempotencyKey: String,
        finalAnswers: [AttemptAnswerChangeInput]
    ) async throws -> Components.Schemas.SubmitAttemptResponse
    func confirmPartChoice(
        attemptId: String,
        partId: PartID,
        sessionId: String,
        idempotencyKey: String,
        optionCode: String,
        subjectCodes: [String]?
    ) async throws -> Components.Schemas.PartChoiceResponse
    func retryPartGeneration(
        attemptId: String,
        partId: PartID,
        sessionId: String,
        idempotencyKey: String,
        reason: String?
    ) async throws -> Components.Schemas.PartChoiceResponse
}

private final class AttemptWireCallContext: @unchecked Sendable {
    private let lock = NSLock()
    let outgoingBodyData: Data?
    private var _decodedAnswers: [ItemID: AnswerValue] = [:]
    private var _rawRetryAfterSeconds: Int?

    init(outgoingBodyData: Data? = nil) {
        self.outgoingBodyData = outgoingBodyData
    }

    var decodedAnswers: [ItemID: AnswerValue] {
        lock.lock()
        defer { lock.unlock() }
        return _decodedAnswers
    }

    var rawRetryAfterSeconds: Int? {
        lock.lock()
        defer { lock.unlock() }
        return _rawRetryAfterSeconds
    }

    func setDecodedAnswers(_ answers: [ItemID: AnswerValue]) {
        lock.lock()
        _decodedAnswers = answers
        lock.unlock()
    }

    func setRawRetryAfterSeconds(_ seconds: Int?) {
        lock.lock()
        _rawRetryAfterSeconds = seconds
        lock.unlock()
    }
}

private enum AttemptWireContext {
    @TaskLocal static var current: AttemptWireCallContext?
}

struct AttemptWireMiddleware: ClientMiddleware {
    func intercept(
        _ request: HTTPRequest,
        body: HTTPBody?,
        baseURL: URL,
        operationID: String,
        next: @Sendable (HTTPRequest, HTTPBody?, URL) async throws -> (HTTPResponse, HTTPBody?)
    ) async throws -> (HTTPResponse, HTTPBody?) {
        let context = AttemptWireContext.current
        var outgoingRequest = request
        let effectiveBody: HTTPBody?
        if let overrideData = context?.outgoingBodyData {
            effectiveBody = HTTPBody(overrideData)
            outgoingRequest.headerFields[.contentLength] = String(overrideData.count)
        } else {
            effectiveBody = body
        }

        let (response, responseBody) = try await next(outgoingRequest, effectiveBody, baseURL)

        if let context, response.status.code == 429 {
            context.setRawRetryAfterSeconds(Self.parseRetryAfterAllowingZero(from: response.headerFields))
        }

        if let context,
           operationID == Operations.getState.id,
           response.status.code == 200,
           let responseBody
        {
            let data = try await Data(collecting: responseBody, upTo: 8_388_608)
            context.setDecodedAnswers(Self.extractAnswers(fromStateData: data))
            return (response, HTTPBody(data))
        }

        return (response, responseBody)
    }

    static func parseRetryAfterAllowingZero(from headers: HTTPFields?) -> Int? {
        guard let headers,
              let name = HTTPField.Name("Retry-After"),
              let raw = headers[name]?.trimmingCharacters(in: .whitespacesAndNewlines),
              !raw.isEmpty
        else {
            return nil
        }
        if let intVal = Int(raw), intVal >= 0 {
            return intVal
        }
        if let doubleVal = Double(raw), doubleVal.isFinite, doubleVal >= 0 {
            return Int(ceil(doubleVal))
        }
        return nil
    }

    static func extractAnswers(fromStateData data: Data) -> [ItemID: AnswerValue] {
        guard let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let parts = root["parts"] as? [[String: Any]]
        else {
            return [:]
        }

        let decoder = MobileAPIClient.makeJSONDecoder()
        var result: [ItemID: AnswerValue] = [:]
        for part in parts {
            guard let items = part["items"] as? [[String: Any]] else { continue }
            for item in items {
                guard let itemId = item["itemId"] as? String,
                      !itemId.isEmpty,
                      let answerObj = item["answer"] as? [String: Any],
                      let answerData = try? JSONSerialization.data(withJSONObject: answerObj),
                      let decoded = try? decoder.decode(AnswerValue.self, from: answerData)
                else {
                    continue
                }
                result[itemId] = decoded
            }
        }
        return result
    }
}

private struct WireAnswerChange: Encodable {
    let itemId: String
    let seq: Int64
    let answer: AnswerValue
}

private struct WireFlagChange: Encodable {
    let itemId: String
    let flagged: Bool
}

private struct WireSaveAnswersRequest: Encodable {
    let changes: [WireAnswerChange]?
    let flags: [WireFlagChange]?
    let position: Position?
}

private struct WireFinalAnswersRequest: Encodable {
    let finalAnswers: [WireAnswerChange]
}

final class AttemptAPI: AttemptAPIClient, Sendable {
    private let client: Client
    private let encoder: JSONEncoder = {
        let enc = JSONEncoder()
        return enc
    }()

    init(
        serverURL: URL,
        tokenStore: any TokenStore,
        refresher: TokenRefresher,
        transport: any ClientTransport,
        logger: RedactingAppLogger = RedactingAppLogger(),
        localeProvider: @escaping @Sendable () async -> AppLocale = { .vi }
    ) {
        let configuration = Configuration(dateTranscoder: FlexibleISO8601DateTranscoder())
        let acceptLanguage = AcceptLanguageMiddleware(localeProvider: localeProvider)
        let wire = AttemptWireMiddleware()
        let retryAfter = RetryAfterMiddleware()
        let bearer = AuthBearerMiddleware(tokenStore: tokenStore, refresher: refresher)
        let logging = NetworkLoggingMiddleware(logger: logger)
        self.client = Client(
            serverURL: serverURL,
            configuration: configuration,
            transport: transport,
            middlewares: [acceptLanguage, wire, retryAfter, bearer, logging]
        )
    }

    convenience init(apiClient: MobileAPIClient) {
        self.init(client: apiClient.makeAuthenticatedClient(leadingMiddlewares: [AttemptWireMiddleware()]))
    }

    init(client: Client) {
        self.client = client
    }

    func getState(attemptId: String) async throws -> AttemptStateSnapshot {
        let context = AttemptWireCallContext()
        return try await AttemptWireContext.$current.withValue(context) {
            do {
                let output = try await client.getState(path: .init(attemptId: attemptId))
                switch output {
                case let .ok(response):
                    let payload = try response.body.json
                    return AttemptStateSnapshot(
                        response: payload,
                        decodedAnswers: context.decodedAnswers
                    )
                case let .badRequest(response):
                    throw await Self.decodeError(statusCode: 400, body: try response.body.any, context: context)
                case let .unauthorized(response):
                    throw await Self.decodeError(statusCode: 401, body: try response.body.any, context: context)
                case let .forbidden(response):
                    throw await Self.decodeError(statusCode: 403, body: try response.body.any, context: context)
                case let .notFound(response):
                    throw await Self.decodeError(statusCode: 404, body: try response.body.any, context: context)
                case let .conflict(response):
                    throw await Self.decodeError(statusCode: 409, body: try response.body.any, context: context)
                case let .preconditionFailed(response):
                    throw await Self.decodeError(statusCode: 412, body: try response.body.any, context: context)
                case let .contentTooLarge(response):
                    throw await Self.decodeError(statusCode: 413, body: try response.body.any, context: context)
                case let .unprocessableContent(response):
                    throw await Self.decodeError(statusCode: 422, body: try response.body.any, context: context)
                case let .tooManyRequests(response):
                    throw await Self.decodeError(statusCode: 429, body: try response.body.any, context: context)
                case let .internalServerError(response):
                    throw await Self.decodeError(statusCode: 500, body: try response.body.any, context: context)
                case let .notImplemented(response):
                    throw await Self.decodeError(statusCode: 501, body: try response.body.any, context: context)
                case let .badGateway(response):
                    throw await Self.decodeError(statusCode: 502, body: try response.body.any, context: context)
                case let .undocumented(statusCode, payload):
                    throw await Self.decodeOptionalError(statusCode: statusCode, body: payload.body, context: context)
                }
            } catch let apiError as AttemptAPIError {
                throw apiError
            } catch {
                throw Self.classifyTransportError(error, context: context)
            }
        }
    }

    func claimSession(
        attemptId: String,
        sessionId: String,
        takeover: Bool
    ) async throws -> Components.Schemas.ClaimSessionResponse {
        let context = AttemptWireCallContext()
        return try await AttemptWireContext.$current.withValue(context) {
            do {
                let output = try await client.claimSession(
                    path: .init(attemptId: attemptId),
                    body: .json(.init(sessionId: sessionId, takeover: takeover ? true : nil))
                )
                switch output {
                case let .ok(response):
                    return try response.body.json
                case let .conflict(response):
                    let conflict = try response.body.json
                    throw AttemptAPIError.conflict(
                        code: conflict.code,
                        lastSeenAt: conflict.lastSeenAt,
                        message: conflict.message
                    )
                case let .badRequest(response):
                    throw await Self.decodeError(statusCode: 400, body: try response.body.any, context: context)
                case let .unauthorized(response):
                    throw await Self.decodeError(statusCode: 401, body: try response.body.any, context: context)
                case let .forbidden(response):
                    throw await Self.decodeError(statusCode: 403, body: try response.body.any, context: context)
                case let .notFound(response):
                    throw await Self.decodeError(statusCode: 404, body: try response.body.any, context: context)
                case let .preconditionFailed(response):
                    throw await Self.decodeError(statusCode: 412, body: try response.body.any, context: context)
                case let .contentTooLarge(response):
                    throw await Self.decodeError(statusCode: 413, body: try response.body.any, context: context)
                case let .unprocessableContent(response):
                    throw await Self.decodeError(statusCode: 422, body: try response.body.any, context: context)
                case let .tooManyRequests(response):
                    throw await Self.decodeError(statusCode: 429, body: try response.body.any, context: context)
                case let .internalServerError(response):
                    throw await Self.decodeError(statusCode: 500, body: try response.body.any, context: context)
                case let .notImplemented(response):
                    throw await Self.decodeError(statusCode: 501, body: try response.body.any, context: context)
                case let .badGateway(response):
                    throw await Self.decodeError(statusCode: 502, body: try response.body.any, context: context)
                case let .undocumented(statusCode, payload):
                    throw await Self.decodeOptionalError(statusCode: statusCode, body: payload.body, context: context)
                }
            } catch let apiError as AttemptAPIError {
                throw apiError
            } catch {
                throw Self.classifyTransportError(error, context: context)
            }
        }
    }

    func heartbeat(attemptId: String, sessionId: String) async throws -> Components.Schemas.HeartbeatResponse {
        let context = AttemptWireCallContext()
        return try await AttemptWireContext.$current.withValue(context) {
            do {
                let output = try await client.heartbeat(
                    path: .init(attemptId: attemptId),
                    headers: .init(X_hyphen_Attempt_hyphen_Session: sessionId)
                )
                switch output {
                case let .ok(response):
                    return try response.body.json
                case let .badRequest(response):
                    throw await Self.decodeError(statusCode: 400, body: try response.body.any, context: context)
                case let .unauthorized(response):
                    throw await Self.decodeError(statusCode: 401, body: try response.body.any, context: context)
                case let .forbidden(response):
                    throw await Self.decodeError(statusCode: 403, body: try response.body.any, context: context)
                case let .notFound(response):
                    throw await Self.decodeError(statusCode: 404, body: try response.body.any, context: context)
                case let .conflict(response):
                    throw await Self.decodeError(statusCode: 409, body: try response.body.any, context: context)
                case let .preconditionFailed(response):
                    throw await Self.decodeError(statusCode: 412, body: try response.body.any, context: context)
                case let .contentTooLarge(response):
                    throw await Self.decodeError(statusCode: 413, body: try response.body.any, context: context)
                case let .unprocessableContent(response):
                    throw await Self.decodeError(statusCode: 422, body: try response.body.any, context: context)
                case let .tooManyRequests(response):
                    throw await Self.decodeError(statusCode: 429, body: try response.body.any, context: context)
                case let .internalServerError(response):
                    throw await Self.decodeError(statusCode: 500, body: try response.body.any, context: context)
                case let .notImplemented(response):
                    throw await Self.decodeError(statusCode: 501, body: try response.body.any, context: context)
                case let .badGateway(response):
                    throw await Self.decodeError(statusCode: 502, body: try response.body.any, context: context)
                case let .undocumented(statusCode, payload):
                    throw await Self.decodeOptionalError(statusCode: statusCode, body: payload.body, context: context)
                }
            } catch let apiError as AttemptAPIError {
                throw apiError
            } catch {
                throw Self.classifyTransportError(error, context: context)
            }
        }
    }

    func saveAnswers(
        attemptId: String,
        sessionId: String,
        changes: [AttemptAnswerChangeInput],
        flags: [AttemptFlagChangeInput],
        position: Position?
    ) async throws -> Components.Schemas.SaveAnswersResponse {
        let wirePayload = WireSaveAnswersRequest(
            changes: changes.isEmpty ? nil : changes.map {
                WireAnswerChange(itemId: $0.itemId, seq: $0.seq, answer: $0.answer)
            },
            flags: flags.isEmpty ? nil : flags.map {
                WireFlagChange(itemId: $0.itemId, flagged: $0.flagged)
            },
            position: position
        )
        let encodedBody = try encoder.encode(wirePayload)
        let context = AttemptWireCallContext(outgoingBodyData: encodedBody)

        let generatedBody = Components.Schemas.SaveAnswersRequest(
            changes: changes.isEmpty ? nil : changes.map {
                Components.Schemas.AnswerChange(
                    answer: $0.answer.generatedDiscriminator,
                    itemId: $0.itemId,
                    seq: $0.seq
                )
            },
            flags: flags.isEmpty ? nil : flags.map {
                Components.Schemas.FlagChange(flagged: $0.flagged, itemId: $0.itemId)
            },
            position: position.map {
                Components.Schemas.AttemptPosition(itemId: $0.itemId, partId: $0.partId)
            }
        )

        return try await AttemptWireContext.$current.withValue(context) {
            do {
                let output = try await client.saveAnswers(
                    path: .init(attemptId: attemptId),
                    headers: .init(X_hyphen_Attempt_hyphen_Session: sessionId),
                    body: .json(generatedBody)
                )
                switch output {
                case let .ok(response):
                    return try response.body.json
                case let .tooManyRequests(response):
                    let headerSeconds = response.headers.Retry_hyphen_After.map(Int.init)
                        ?? context.rawRetryAfterSeconds
                    throw AttemptAPIError.rateLimited(retryAfterSeconds: headerSeconds)
                case let .badRequest(response):
                    throw await Self.decodeError(statusCode: 400, body: try response.body.any, context: context)
                case let .unauthorized(response):
                    throw await Self.decodeError(statusCode: 401, body: try response.body.any, context: context)
                case let .forbidden(response):
                    throw await Self.decodeError(statusCode: 403, body: try response.body.any, context: context)
                case let .notFound(response):
                    throw await Self.decodeError(statusCode: 404, body: try response.body.any, context: context)
                case let .conflict(response):
                    throw await Self.decodeError(statusCode: 409, body: try response.body.any, context: context)
                case let .preconditionFailed(response):
                    throw await Self.decodeError(statusCode: 412, body: try response.body.any, context: context)
                case let .contentTooLarge(response):
                    throw await Self.decodeError(statusCode: 413, body: try response.body.any, context: context)
                case let .unprocessableContent(response):
                    throw await Self.decodeError(statusCode: 422, body: try response.body.any, context: context)
                case let .internalServerError(response):
                    throw await Self.decodeError(statusCode: 500, body: try response.body.any, context: context)
                case let .notImplemented(response):
                    throw await Self.decodeError(statusCode: 501, body: try response.body.any, context: context)
                case let .badGateway(response):
                    throw await Self.decodeError(statusCode: 502, body: try response.body.any, context: context)
                case let .undocumented(statusCode, payload):
                    throw await Self.decodeOptionalError(statusCode: statusCode, body: payload.body, context: context)
                }
            } catch let apiError as AttemptAPIError {
                throw apiError
            } catch {
                throw Self.classifyTransportError(error, context: context)
            }
        }
    }

    func finishPart(
        attemptId: String,
        partId: PartID,
        sessionId: String,
        finalAnswers: [AttemptAnswerChangeInput]
    ) async throws -> Components.Schemas.FinishPartResponse {
        let encodedBody = try encoder.encode(
            WireFinalAnswersRequest(
                finalAnswers: finalAnswers.map {
                    WireAnswerChange(itemId: $0.itemId, seq: $0.seq, answer: $0.answer)
                }
            )
        )
        let context = AttemptWireCallContext(outgoingBodyData: encodedBody)
        let generatedBody = Components.Schemas.FinishPartRequest(
            finalAnswers: finalAnswers.map {
                Components.Schemas.FinalAnswerChange(
                    answer: $0.answer.generatedDiscriminator,
                    itemId: $0.itemId,
                    seq: $0.seq
                )
            }
        )

        return try await AttemptWireContext.$current.withValue(context) {
            do {
                let output = try await client.finishPart(
                    path: .init(attemptId: attemptId, partId: partId),
                    headers: .init(X_hyphen_Attempt_hyphen_Session: sessionId),
                    body: .json(generatedBody)
                )
                switch output {
                case let .ok(response):
                    return try response.body.json
                case let .badRequest(response):
                    throw await Self.decodeError(statusCode: 400, body: try response.body.any, context: context)
                case let .unauthorized(response):
                    throw await Self.decodeError(statusCode: 401, body: try response.body.any, context: context)
                case let .forbidden(response):
                    throw await Self.decodeError(statusCode: 403, body: try response.body.any, context: context)
                case let .notFound(response):
                    throw await Self.decodeError(statusCode: 404, body: try response.body.any, context: context)
                case let .conflict(response):
                    throw await Self.decodeError(statusCode: 409, body: try response.body.any, context: context)
                case let .preconditionFailed(response):
                    throw await Self.decodeError(statusCode: 412, body: try response.body.any, context: context)
                case let .contentTooLarge(response):
                    throw await Self.decodeError(statusCode: 413, body: try response.body.any, context: context)
                case let .unprocessableContent(response):
                    throw await Self.decodeError(statusCode: 422, body: try response.body.any, context: context)
                case let .tooManyRequests(response):
                    throw await Self.decodeError(statusCode: 429, body: try response.body.any, context: context)
                case let .internalServerError(response):
                    throw await Self.decodeError(statusCode: 500, body: try response.body.any, context: context)
                case let .notImplemented(response):
                    throw await Self.decodeError(statusCode: 501, body: try response.body.any, context: context)
                case let .badGateway(response):
                    throw await Self.decodeError(statusCode: 502, body: try response.body.any, context: context)
                case let .undocumented(statusCode, payload):
                    throw await Self.decodeOptionalError(statusCode: statusCode, body: payload.body, context: context)
                }
            } catch let apiError as AttemptAPIError {
                throw apiError
            } catch {
                throw Self.classifyTransportError(error, context: context)
            }
        }
    }

    func submit(
        attemptId: String,
        sessionId: String,
        idempotencyKey: String,
        finalAnswers: [AttemptAnswerChangeInput]
    ) async throws -> Components.Schemas.SubmitAttemptResponse {
        let encodedBody = try encoder.encode(
            WireFinalAnswersRequest(
                finalAnswers: finalAnswers.map {
                    WireAnswerChange(itemId: $0.itemId, seq: $0.seq, answer: $0.answer)
                }
            )
        )
        let context = AttemptWireCallContext(outgoingBodyData: encodedBody)
        let generatedBody = Components.Schemas.SubmitAttemptRequest(
            finalAnswers: finalAnswers.map {
                Components.Schemas.FinalAnswerChange(
                    answer: $0.answer.generatedDiscriminator,
                    itemId: $0.itemId,
                    seq: $0.seq
                )
            }
        )

        return try await AttemptWireContext.$current.withValue(context) {
            do {
                let output = try await client.submit(
                    path: .init(attemptId: attemptId),
                    headers: .init(
                        Idempotency_hyphen_Key: idempotencyKey,
                        X_hyphen_Attempt_hyphen_Session: sessionId
                    ),
                    body: .json(generatedBody)
                )
                switch output {
                case let .ok(response):
                    return try response.body.json
                case let .badRequest(response):
                    throw await Self.decodeError(statusCode: 400, body: try response.body.any, context: context)
                case let .unauthorized(response):
                    throw await Self.decodeError(statusCode: 401, body: try response.body.any, context: context)
                case let .forbidden(response):
                    throw await Self.decodeError(statusCode: 403, body: try response.body.any, context: context)
                case let .notFound(response):
                    throw await Self.decodeError(statusCode: 404, body: try response.body.any, context: context)
                case let .conflict(response):
                    throw await Self.decodeError(statusCode: 409, body: try response.body.any, context: context)
                case let .preconditionFailed(response):
                    throw await Self.decodeError(statusCode: 412, body: try response.body.any, context: context)
                case let .contentTooLarge(response):
                    throw await Self.decodeError(statusCode: 413, body: try response.body.any, context: context)
                case let .unprocessableContent(response):
                    throw await Self.decodeError(statusCode: 422, body: try response.body.any, context: context)
                case let .tooManyRequests(response):
                    throw await Self.decodeError(statusCode: 429, body: try response.body.any, context: context)
                case let .internalServerError(response):
                    throw await Self.decodeError(statusCode: 500, body: try response.body.any, context: context)
                case let .notImplemented(response):
                    throw await Self.decodeError(statusCode: 501, body: try response.body.any, context: context)
                case let .badGateway(response):
                    throw await Self.decodeError(statusCode: 502, body: try response.body.any, context: context)
                case let .undocumented(statusCode, payload):
                    throw await Self.decodeOptionalError(statusCode: statusCode, body: payload.body, context: context)
                }
            } catch let apiError as AttemptAPIError {
                throw apiError
            } catch {
                throw Self.classifyTransportError(error, context: context)
            }
        }
    }

    func confirmPartChoice(
        attemptId: String,
        partId: PartID,
        sessionId: String,
        idempotencyKey: String,
        optionCode: String,
        subjectCodes: [String]?
    ) async throws -> Components.Schemas.PartChoiceResponse {
        let context = AttemptWireCallContext()
        let cleanedSubjects = subjectCodes.flatMap { $0.isEmpty ? nil : $0 }
        return try await AttemptWireContext.$current.withValue(context) {
            do {
                let output = try await client.confirmPartChoice(
                    path: .init(attemptId: attemptId, partId: partId),
                    headers: .init(
                        Idempotency_hyphen_Key: idempotencyKey,
                        X_hyphen_Attempt_hyphen_Session: sessionId
                    ),
                    body: .json(.init(optionCode: optionCode, subjectCodes: cleanedSubjects))
                )
                switch output {
                case let .ok(response):
                    return try response.body.json
                case let .accepted(response):
                    return try response.body.json
                case let .badRequest(response):
                    throw await Self.decodeError(statusCode: 400, body: try response.body.any, context: context)
                case let .unauthorized(response):
                    throw await Self.decodeError(statusCode: 401, body: try response.body.any, context: context)
                case let .forbidden(response):
                    throw await Self.decodeError(statusCode: 403, body: try response.body.any, context: context)
                case let .notFound(response):
                    throw await Self.decodeError(statusCode: 404, body: try response.body.any, context: context)
                case let .conflict(response):
                    throw await Self.decodeError(statusCode: 409, body: try response.body.any, context: context)
                case let .preconditionFailed(response):
                    throw await Self.decodeError(statusCode: 412, body: try response.body.any, context: context)
                case let .contentTooLarge(response):
                    throw await Self.decodeError(statusCode: 413, body: try response.body.any, context: context)
                case let .unprocessableContent(response):
                    throw await Self.decodeError(statusCode: 422, body: try response.body.any, context: context)
                case let .tooManyRequests(response):
                    throw await Self.decodeError(statusCode: 429, body: try response.body.any, context: context)
                case let .internalServerError(response):
                    throw await Self.decodeError(statusCode: 500, body: try response.body.any, context: context)
                case let .notImplemented(response):
                    throw await Self.decodeError(statusCode: 501, body: try response.body.any, context: context)
                case let .badGateway(response):
                    throw await Self.decodeError(statusCode: 502, body: try response.body.any, context: context)
                case let .undocumented(statusCode, payload):
                    throw await Self.decodeOptionalError(statusCode: statusCode, body: payload.body, context: context)
                }
            } catch let apiError as AttemptAPIError {
                throw apiError
            } catch {
                throw Self.classifyTransportError(error, context: context)
            }
        }
    }

    func retryPartGeneration(
        attemptId: String,
        partId: PartID,
        sessionId: String,
        idempotencyKey: String,
        reason: String?
    ) async throws -> Components.Schemas.PartChoiceResponse {
        let context = AttemptWireCallContext()
        return try await AttemptWireContext.$current.withValue(context) {
            do {
                let output = try await client.retryPartGeneration(
                    path: .init(attemptId: attemptId, partId: partId),
                    headers: .init(
                        Idempotency_hyphen_Key: idempotencyKey,
                        X_hyphen_Attempt_hyphen_Session: sessionId
                    ),
                    body: .json(.init(reason: reason))
                )
                switch output {
                case let .ok(response):
                    return try response.body.json
                case let .accepted(response):
                    return try response.body.json
                case let .badRequest(response):
                    throw await Self.decodeError(statusCode: 400, body: try response.body.any, context: context)
                case let .unauthorized(response):
                    throw await Self.decodeError(statusCode: 401, body: try response.body.any, context: context)
                case let .forbidden(response):
                    throw await Self.decodeError(statusCode: 403, body: try response.body.any, context: context)
                case let .notFound(response):
                    throw await Self.decodeError(statusCode: 404, body: try response.body.any, context: context)
                case let .conflict(response):
                    throw await Self.decodeError(statusCode: 409, body: try response.body.any, context: context)
                case let .preconditionFailed(response):
                    throw await Self.decodeError(statusCode: 412, body: try response.body.any, context: context)
                case let .contentTooLarge(response):
                    throw await Self.decodeError(statusCode: 413, body: try response.body.any, context: context)
                case let .unprocessableContent(response):
                    throw await Self.decodeError(statusCode: 422, body: try response.body.any, context: context)
                case let .tooManyRequests(response):
                    throw await Self.decodeError(statusCode: 429, body: try response.body.any, context: context)
                case let .internalServerError(response):
                    throw await Self.decodeError(statusCode: 500, body: try response.body.any, context: context)
                case let .notImplemented(response):
                    throw await Self.decodeError(statusCode: 501, body: try response.body.any, context: context)
                case let .badGateway(response):
                    throw await Self.decodeError(statusCode: 502, body: try response.body.any, context: context)
                case let .undocumented(statusCode, payload):
                    throw await Self.decodeOptionalError(statusCode: statusCode, body: payload.body, context: context)
                }
            } catch let apiError as AttemptAPIError {
                throw apiError
            } catch {
                throw Self.classifyTransportError(error, context: context)
            }
        }
    }

    private static func decodeError(
        statusCode: Int,
        body: HTTPBody,
        context: AttemptWireCallContext
    ) async -> AttemptAPIError {
        let data = (try? await Data(collecting: body, upTo: 65_536)) ?? Data()
        return buildError(statusCode: statusCode, data: data, context: context)
    }

    private static func decodeOptionalError(
        statusCode: Int,
        body: HTTPBody?,
        context: AttemptWireCallContext
    ) async -> AttemptAPIError {
        let data: Data
        if let body {
            data = (try? await Data(collecting: body, upTo: 65_536)) ?? Data()
        } else {
            data = Data()
        }
        return buildError(statusCode: statusCode, data: data, context: context)
    }

    private static func buildError(
        statusCode: Int,
        data: Data,
        context: AttemptWireCallContext
    ) -> AttemptAPIError {
        let decoder = MobileAPIClient.makeJSONDecoder()
        if statusCode == 409,
           !data.isEmpty,
           let conflict = try? decoder.decode(Components.Schemas.AttemptSessionConflictResponse.self, from: data)
        {
            return .conflict(
                code: conflict.code,
                lastSeenAt: conflict.lastSeenAt,
                message: conflict.message
            )
        }

        let parsed = !data.isEmpty ? (try? decoder.decode(ApiError.self, from: data)) : nil
        let code = parsed?.code ?? ""
        let message = parsed?.message ?? ""

        switch statusCode {
        case 401:
            return .unauthorized(code: code.isEmpty ? "UNAUTHENTICATED" : code)
        case 409:
            return .conflict(code: code, lastSeenAt: nil, message: message)
        case 429:
            return .rateLimited(retryAfterSeconds: context.rawRetryAfterSeconds ?? parsed?.retryAfter)
        case 400...499:
            return .clientError(statusCode: statusCode, code: code, message: message)
        default:
            return .serverError(statusCode: statusCode, code: code)
        }
    }

    private static func classifyTransportError(
        _ error: any Error,
        context: AttemptWireCallContext
    ) -> AttemptAPIError {
        let classified = MobileApiError.classify(error)
        switch classified {
        case let .network(code):
            return .network(code)
        case let .api(statusCode, payload):
            switch statusCode {
            case 401:
                return .unauthorized(code: payload.code)
            case 409:
                return .conflict(code: payload.code, lastSeenAt: nil, message: payload.message)
            case 429:
                return .rateLimited(retryAfterSeconds: context.rawRetryAfterSeconds ?? payload.retryAfter)
            case 400...499:
                return .clientError(statusCode: statusCode, code: payload.code, message: payload.message)
            default:
                return .serverError(statusCode: statusCode, code: payload.code)
            }
        case let .unexpectedResponse(statusCode):
            if statusCode == 429 {
                return .rateLimited(retryAfterSeconds: context.rawRetryAfterSeconds)
            }
            if (400...499).contains(statusCode) {
                return .clientError(statusCode: statusCode, code: "", message: "")
            }
            if statusCode >= 500 {
                return .serverError(statusCode: statusCode, code: "")
            }
            return .network(.unknown)
        }
    }
}
