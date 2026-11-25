import Foundation
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

    init(
        code: String,
        message: String = "",
        fieldErrors: [ApiFieldError] = [],
        traceId: String = ""
    ) {
        self.code = code
        self.message = message
        self.fieldErrors = fieldErrors
        self.traceId = traceId
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
            return classify(clientError.underlyingError)
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

    static func decodeErrorBody(statusCode: Int, body: HTTPBody) async -> MobileApiError {
        guard let data = try? await Data(collecting: body, upTo: 65_536),
              !data.isEmpty,
              let decoded = try? JSONDecoder().decode(ApiError.self, from: data)
        else {
            return .unexpectedResponse(statusCode: statusCode)
        }
        return .api(statusCode: statusCode, error: decoded)
    }
}
