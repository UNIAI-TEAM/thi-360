import Foundation

enum LoadFailureKind: Equatable, Sendable {
    case offline
    case server
    case notFound
    case gone
    case forbidden
    case conflict(code: String)

    static func classify(_ error: MobileApiError) -> (kind: LoadFailureKind, canRetry: Bool, retryAfter: Int?) {
        switch error {
        case .network:
            return (.offline, true, nil)
        case let .api(statusCode, payload):
            switch statusCode {
            case 404:
                return (.notFound, false, nil)
            case 410:
                return (.gone, false, nil)
            case 403:
                return (.forbidden, false, nil)
            case 409:
                return (.conflict(code: payload.code), false, nil)
            case 429:
                return (.server, true, error.retryAfterSeconds)
            default:
                return (.server, true, error.retryAfterSeconds)
            }
        case let .unexpectedResponse(statusCode):
            switch statusCode {
            case 404:
                return (.notFound, false, nil)
            case 410:
                return (.gone, false, nil)
            case 403:
                return (.forbidden, false, nil)
            case 409:
                return (.conflict(code: ""), false, nil)
            default:
                return (.server, true, nil)
            }
        }
    }
}

enum LoadState<T> {
    case loading
    case loaded(value: T, stale: Bool)
    case empty
    case failed(kind: LoadFailureKind, canRetry: Bool, retryAfter: Int?)
}

extension LoadState: Equatable where T: Equatable {}
extension LoadState: Sendable where T: Sendable {}
