import Foundation
import HTTPTypes
import OpenAPIRuntime

struct ApiFieldError: Codable, Equatable, Sendable {
    let field: String
    let code: String
    let message: String
}

struct ApiError: Codable, Equatable, Sendable {
    let code: String
    let message: String
    let fieldErrors: [ApiFieldError]
    let traceId: String
    let retryAfter: Int?
    let remainingAttempts: Int?

    init(
        code: String,
        message: String = "",
        fieldErrors: [ApiFieldError] = [],
        traceId: String = "",
        retryAfter: Int? = nil,
        remainingAttempts: Int? = nil
    ) {
        self.code = code
        self.message = message
        self.fieldErrors = fieldErrors
        self.traceId = traceId
        self.retryAfter = retryAfter
        self.remainingAttempts = remainingAttempts
    }
}

enum MobileApiError: Error, Equatable, Sendable {
    case api(statusCode: Int, error: ApiError)
    case network(URLError.Code)
    case unexpectedResponse(statusCode: Int)

    var apiCode: String? {
        if case let .api(_, error) = self {
            return error.code
        }
        return nil
    }

    var statusCode: Int? {
        switch self {
        case let .api(statusCode, _), let .unexpectedResponse(statusCode):
            statusCode
        case .network:
            nil
        }
    }

    var retryAfterSeconds: Int? {
        switch self {
        case let .api(_, error):
            guard let seconds = error.retryAfter, seconds > 0 else { return nil }
            return seconds
        case .network, .unexpectedResponse:
            return nil
        }
    }

    var isOfflineOrConnectivity: Bool {
        guard case let .network(code) = self else {
            return false
        }
        return Self.connectivityErrorCodes.contains(code)
    }

    static let connectivityErrorCodes: Set<URLError.Code> = [
        .notConnectedToInternet,
        .networkConnectionLost,
        .timedOut,
        .cannotFindHost,
        .cannotConnectToHost,
        .dnsLookupFailed,
        .dataNotAllowed,
        .internationalRoamingOff,
        .callIsActive,
        .secureConnectionFailed
    ]

    static func classify(_ error: any Error) -> MobileApiError {
        if let mobileError = error as? MobileApiError {
            return mobileError
        }
        if let clientError = error as? ClientError {
            let classified = classify(clientError.underlyingError)
            if let retryAfter = RetryAfter.parseSeconds(from: clientError.response?.headerFields) {
                switch classified {
                case let .api(statusCode, payload):
                    return .api(
                        statusCode: statusCode,
                        error: ApiError(
                            code: payload.code,
                            message: payload.message,
                            fieldErrors: payload.fieldErrors,
                            traceId: payload.traceId,
                            retryAfter: retryAfter,
                            remainingAttempts: payload.remainingAttempts
                        )
                    )
                case let .unexpectedResponse(statusCode) where statusCode == 429:
                    return .api(statusCode: 429, error: ApiError(code: "RATE_LIMITED", retryAfter: retryAfter))
                default:
                    break
                }
            }
            return classified
        }
        if let urlError = error as? URLError {
            return .network(urlError.code)
        }
        let nsError = error as NSError
        if nsError.domain == NSURLErrorDomain {
            return .network(URLError.Code(rawValue: nsError.code))
        }
        return .unexpectedResponse(statusCode: 0)
    }

    static func decodeErrorBody(
        statusCode: Int,
        body: HTTPBody,
        retryAfterHeader: String? = nil
    ) async -> MobileApiError {
        let parsedHeader = RetryAfter.parseSeconds(retryAfterHeader)
        guard let data = try? await Data(collecting: body, upTo: 65_536),
              !data.isEmpty,
              let decoded = try? JSONDecoder().decode(ApiError.self, from: data)
        else {
            if statusCode == 429, let parsedHeader {
                return .api(statusCode: 429, error: ApiError(code: "RATE_LIMITED", retryAfter: parsedHeader))
            }
            return .unexpectedResponse(statusCode: statusCode)
        }

        let resolvedRetryAfter = RetryAfter.resolve(
            headerValue: retryAfterHeader,
            bodyRetryAfter: decoded.retryAfter
        )
        let merged = ApiError(
            code: decoded.code,
            message: decoded.message,
            fieldErrors: decoded.fieldErrors,
            traceId: decoded.traceId,
            retryAfter: resolvedRetryAfter,
            remainingAttempts: decoded.remainingAttempts
        )
        return .api(statusCode: statusCode, error: merged)
    }
}
