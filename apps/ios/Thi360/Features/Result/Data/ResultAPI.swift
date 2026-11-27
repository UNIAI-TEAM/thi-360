import Foundation
import HTTPTypes
import OpenAPIRuntime

struct ReviewWirePayload: Equatable, Sendable {
    let response: Components.Schemas.AttemptReviewResponse
    let studentAnswers: [String: AnswerValue]

    init(
        response: Components.Schemas.AttemptReviewResponse,
        studentAnswers: [String: AnswerValue] = [:]
    ) {
        self.response = response
        self.studentAnswers = studentAnswers
    }
}

protocol ResultAPIClient: Sendable {
    func getResult(attemptId: String) async throws -> Components.Schemas.AttemptResultResponse
    func getReview(attemptId: String) async throws -> ReviewWirePayload
}

private final class ReviewWireCallContext: @unchecked Sendable {
    private let lock = NSLock()
    private var _studentAnswers: [String: AnswerValue] = [:]

    var studentAnswers: [String: AnswerValue] {
        lock.lock()
        defer { lock.unlock() }
        return _studentAnswers
    }

    func setStudentAnswers(_ answers: [String: AnswerValue]) {
        lock.lock()
        _studentAnswers = answers
        lock.unlock()
    }
}

private enum ReviewWireContext {
    @TaskLocal static var current: ReviewWireCallContext?
}

struct ReviewWireMiddleware: ClientMiddleware {
    func intercept(
        _ request: HTTPRequest,
        body: HTTPBody?,
        baseURL: URL,
        operationID: String,
        next: @Sendable (HTTPRequest, HTTPBody?, URL) async throws -> (HTTPResponse, HTTPBody?)
    ) async throws -> (HTTPResponse, HTTPBody?) {
        let (response, responseBody) = try await next(request, body, baseURL)

        if let context = ReviewWireContext.current,
           operationID == Operations.getReview.id,
           response.status.code == 200,
           let responseBody
        {
            let data = try await Data(collecting: responseBody, upTo: 8_388_608)
            context.setStudentAnswers(ItemReviewMapper.extractStudentAnswers(fromReviewData: data))
            return (response, HTTPBody(data))
        }

        return (response, responseBody)
    }
}

final class OpenAPIResultAPI: ResultAPIClient, Sendable {
    private let client: Client

    init(apiClient: MobileAPIClient) {
        self.client = apiClient.makeAuthenticatedClient(leadingMiddlewares: [ReviewWireMiddleware()])
    }

    init(client: Client) {
        self.client = client
    }

    func getResult(attemptId: String) async throws -> Components.Schemas.AttemptResultResponse {
        do {
            let output = try await client.getResult(path: .init(attemptId: attemptId))
            switch output {
            case let .ok(response):
                let data = try await Data(collecting: try response.body.any, upTo: 8_388_608)
                return try MobileAPIClient.makeJSONDecoder().decode(
                    Components.Schemas.AttemptResultResponse.self,
                    from: data
                )
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
                if let body = payload.body {
                    throw await MobileApiError.decodeErrorBody(statusCode: statusCode, body: body)
                }
                throw MobileApiError.unexpectedResponse(statusCode: statusCode)
            }
        } catch {
            throw MobileApiError.classify(error)
        }
    }

    func getReview(attemptId: String) async throws -> ReviewWirePayload {
        let context = ReviewWireCallContext()
        return try await ReviewWireContext.$current.withValue(context) {
            do {
                let output = try await client.getReview(path: .init(attemptId: attemptId))
                switch output {
                case let .ok(response):
                    let data = try await Data(collecting: try response.body.any, upTo: 8_388_608)
                    let payload = try MobileAPIClient.makeJSONDecoder().decode(
                        Components.Schemas.AttemptReviewResponse.self,
                        from: data
                    )
                    let answers = context.studentAnswers.isEmpty
                        ? ItemReviewMapper.extractStudentAnswers(fromReviewData: data)
                        : context.studentAnswers
                    return ReviewWirePayload(
                        response: payload,
                        studentAnswers: answers
                    )
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
                    if let body = payload.body {
                        throw await MobileApiError.decodeErrorBody(statusCode: statusCode, body: body)
                    }
                    throw MobileApiError.unexpectedResponse(statusCode: statusCode)
                }
            } catch {
                throw MobileApiError.classify(error)
            }
        }
    }
}
