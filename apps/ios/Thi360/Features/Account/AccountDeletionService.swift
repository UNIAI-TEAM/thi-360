import Foundation
import OpenAPIRuntime

protocol AccountDeletionServiceProtocol: Sendable {
    func check() async throws -> DeletionCheck
    func delete(currentPassword: String) async -> AccountDeletionOutcome
}

struct OpenAPIAccountDeletionService: AccountDeletionServiceProtocol {
    private let apiClient: MobileAPIClient

    init(apiClient: MobileAPIClient) {
        self.apiClient = apiClient
    }

    func check() async throws -> DeletionCheck {
        do {
            let output = try await apiClient.authenticatedClient.checkMyAccountDeletion()
            switch output {
            case let .ok(response):
                let payload = try response.body.json
                return DeletionCheck(
                    canDelete: payload.canDelete,
                    blockers: payload.blockers.compactMap { DeletionBlocker(rawValue: $0.rawValue) },
                    unusedAttempts: Int(payload.unusedAttempts),
                    pendingGrading: payload.pendingGrading
                )
            case let .badRequest(response):
                throw await MobileApiError.decodeErrorBody(statusCode: 400, body: try response.body.any)
            case let .unauthorized(response):
                let payload = try response.body.json
                throw MobileApiError.api(statusCode: 401, error: Self.toApiError(payload))
            case let .forbidden(response):
                let payload = try response.body.json
                throw MobileApiError.api(statusCode: 403, error: Self.toApiError(payload))
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

    func delete(currentPassword: String) async -> AccountDeletionOutcome {
        do {
            let output = try await apiClient.authenticatedClient.deleteMyAccount(
                body: .json(.init(currentPassword: currentPassword))
            )
            switch output {
            case .noContent:
                return .deleted
            case .unauthorized:
                return .deleted
            case let .badRequest(response):
                let payload = try response.body.json
                return Self.mapBadRequest(Self.toApiError(payload))
            case let .conflict(response):
                let payload = try response.body.json
                if payload.code == "ACCOUNT_DELETION_BLOCKED" {
                    let blockers = payload.blockers.compactMap { DeletionBlocker(rawValue: $0.rawValue) }
                    return .blocked(blockers)
                }
                return .failed(.server)
            case .tooManyRequests:
                return .failed(.throttled)
            case let .undocumented(statusCode, _) where (200...299).contains(statusCode) || statusCode == 401:
                return .deleted
            case let .undocumented(statusCode, _) where statusCode == 429:
                return .failed(.throttled)
            case .forbidden, .notFound, .preconditionFailed, .contentTooLarge,
                 .unprocessableContent, .internalServerError, .notImplemented,
                 .badGateway, .undocumented:
                return .failed(.server)
            }
        } catch {
            let classified = MobileApiError.classify(error)
            if classified.statusCode == 401 {
                return .deleted
            }
            if classified.isOfflineOrConnectivity {
                return .failed(.network)
            }
            if case let .api(statusCode, payload) = classified {
                if statusCode == 400 {
                    return Self.mapBadRequest(payload)
                }
                if statusCode == 429 {
                    return .failed(.throttled)
                }
            }
            return .failed(.server)
        }
    }

    private static func mapBadRequest(_ error: ApiError) -> AccountDeletionOutcome {
        if error.code == "WRONG_OLD_PASSWORD"
            || error.fieldErrors.contains(where: { $0.field == "currentPassword" })
        {
            return .failed(.wrongPassword)
        }
        return .failed(.server)
    }

    private static func toApiError(_ response: Components.Schemas.ApiError) -> ApiError {
        ApiError(
            code: response.code,
            message: response.message,
            fieldErrors: response.fieldErrors.map {
                ApiFieldError(field: $0.field, code: $0.code, message: $0.message)
            },
            traceId: response.traceId
        )
    }
}
