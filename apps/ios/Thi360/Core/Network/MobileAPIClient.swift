import Foundation
import OpenAPIRuntime
import OpenAPIURLSession

protocol MobileAuthClientProtocol: Sendable {
    func login(identifier: String, password: String) async throws -> SessionTokens
    func refresh(refreshToken: String) async throws -> SessionTokens
    func logout(refreshToken: String) async throws
    func me() async throws -> Components.Schemas.MeResponse
    func getMyProfile() async throws -> Components.Schemas.ProfileResponse
}

final class MobileAPIClient: MobileAuthClientProtocol, Sendable {
    let serverURL: URL
    let publicClient: Client
    let authenticatedClient: Client
    let refresher: TokenRefresher

    init(
        serverURL: URL,
        tokenStore: any TokenStore,
        transport: any ClientTransport = URLSessionTransport(),
        logger: RedactingAppLogger = RedactingAppLogger(),
        localeProvider: @escaping @Sendable () async -> AppLocale
    ) {
        self.serverURL = serverURL

        let acceptLanguage = AcceptLanguageMiddleware(localeProvider: localeProvider)
        let logging = NetworkLoggingMiddleware(logger: logger)

        let publicClient = Client(
            serverURL: serverURL,
            transport: transport,
            middlewares: [acceptLanguage, logging]
        )
        self.publicClient = publicClient

        let refresher = TokenRefresher(
            tokenStore: tokenStore,
            refreshExecutor: { refreshToken in
                try await Self.executeRefresh(using: publicClient, refreshToken: refreshToken)
            }
        )
        self.refresher = refresher

        let bearer = AuthBearerMiddleware(tokenStore: tokenStore, refresher: refresher)
        self.authenticatedClient = Client(
            serverURL: serverURL,
            transport: transport,
            middlewares: [acceptLanguage, bearer, logging]
        )
    }

    func login(identifier: String, password: String) async throws -> SessionTokens {
        do {
            let output = try await publicClient.mobileLogin(
                body: .json(.init(identifier: identifier, password: password))
            )
            switch output {
            case let .ok(response):
                let payload = try response.body.json
                return Self.toSessionTokens(payload, username: identifier)
            case let .badRequest(response):
                throw await Self.decodeAnyBody(statusCode: 400, body: try response.body.any)
            case let .unauthorized(response):
                throw await Self.decodeAnyBody(statusCode: 401, body: try response.body.any)
            case let .forbidden(response):
                throw await Self.decodeAnyBody(statusCode: 403, body: try response.body.any)
            case let .notFound(response):
                throw await Self.decodeAnyBody(statusCode: 404, body: try response.body.any)
            case let .conflict(response):
                throw await Self.decodeAnyBody(statusCode: 409, body: try response.body.any)
            case let .preconditionFailed(response):
                throw await Self.decodeAnyBody(statusCode: 412, body: try response.body.any)
            case let .contentTooLarge(response):
                throw await Self.decodeAnyBody(statusCode: 413, body: try response.body.any)
            case let .unprocessableContent(response):
                throw await Self.decodeAnyBody(statusCode: 422, body: try response.body.any)
            case let .tooManyRequests(response):
                throw await Self.decodeAnyBody(statusCode: 429, body: try response.body.any)
            case let .internalServerError(response):
                throw await Self.decodeAnyBody(statusCode: 500, body: try response.body.any)
            case let .notImplemented(response):
                throw await Self.decodeAnyBody(statusCode: 501, body: try response.body.any)
            case let .badGateway(response):
                throw await Self.decodeAnyBody(statusCode: 502, body: try response.body.any)
            case let .undocumented(statusCode, payload):
                throw await Self.decodeOptionalBody(statusCode: statusCode, body: payload.body)
            }
        } catch {
            throw MobileApiError.classify(error)
        }
    }

    func refresh(refreshToken: String) async throws -> SessionTokens {
        try await Self.executeRefresh(using: publicClient, refreshToken: refreshToken)
    }

    func logout(refreshToken: String) async throws {
        do {
            let output = try await publicClient.mobileLogout(
                body: .json(.init(refreshToken: refreshToken))
            )
            switch output {
            case .noContent:
                return
            case let .badRequest(response):
                throw await Self.decodeAnyBody(statusCode: 400, body: try response.body.any)
            case let .unauthorized(response):
                throw await Self.decodeAnyBody(statusCode: 401, body: try response.body.any)
            case let .forbidden(response):
                throw await Self.decodeAnyBody(statusCode: 403, body: try response.body.any)
            case let .notFound(response):
                throw await Self.decodeAnyBody(statusCode: 404, body: try response.body.any)
            case let .conflict(response):
                throw await Self.decodeAnyBody(statusCode: 409, body: try response.body.any)
            case let .preconditionFailed(response):
                throw await Self.decodeAnyBody(statusCode: 412, body: try response.body.any)
            case let .contentTooLarge(response):
                throw await Self.decodeAnyBody(statusCode: 413, body: try response.body.any)
            case let .unprocessableContent(response):
                throw await Self.decodeAnyBody(statusCode: 422, body: try response.body.any)
            case let .tooManyRequests(response):
                throw await Self.decodeAnyBody(statusCode: 429, body: try response.body.any)
            case let .internalServerError(response):
                throw await Self.decodeAnyBody(statusCode: 500, body: try response.body.any)
            case let .notImplemented(response):
                throw await Self.decodeAnyBody(statusCode: 501, body: try response.body.any)
            case let .badGateway(response):
                throw await Self.decodeAnyBody(statusCode: 502, body: try response.body.any)
            case let .undocumented(statusCode, payload):
                throw await Self.decodeOptionalBody(statusCode: statusCode, body: payload.body)
            }
        } catch {
            throw MobileApiError.classify(error)
        }
    }

    func me() async throws -> Components.Schemas.MeResponse {
        do {
            let output = try await authenticatedClient.me()
            switch output {
            case let .ok(response):
                return try response.body.json
            case let .badRequest(response):
                throw await Self.decodeAnyBody(statusCode: 400, body: try response.body.any)
            case let .unauthorized(response):
                throw await Self.decodeAnyBody(statusCode: 401, body: try response.body.any)
            case let .forbidden(response):
                throw await Self.decodeAnyBody(statusCode: 403, body: try response.body.any)
            case let .notFound(response):
                throw await Self.decodeAnyBody(statusCode: 404, body: try response.body.any)
            case let .conflict(response):
                throw await Self.decodeAnyBody(statusCode: 409, body: try response.body.any)
            case let .preconditionFailed(response):
                throw await Self.decodeAnyBody(statusCode: 412, body: try response.body.any)
            case let .contentTooLarge(response):
                throw await Self.decodeAnyBody(statusCode: 413, body: try response.body.any)
            case let .unprocessableContent(response):
                throw await Self.decodeAnyBody(statusCode: 422, body: try response.body.any)
            case let .tooManyRequests(response):
                throw await Self.decodeAnyBody(statusCode: 429, body: try response.body.any)
            case let .internalServerError(response):
                throw await Self.decodeAnyBody(statusCode: 500, body: try response.body.any)
            case let .notImplemented(response):
                throw await Self.decodeAnyBody(statusCode: 501, body: try response.body.any)
            case let .badGateway(response):
                throw await Self.decodeAnyBody(statusCode: 502, body: try response.body.any)
            case let .undocumented(statusCode, payload):
                throw await Self.decodeOptionalBody(statusCode: statusCode, body: payload.body)
            }
        } catch {
            throw MobileApiError.classify(error)
        }
    }

    func getMyProfile() async throws -> Components.Schemas.ProfileResponse {
        do {
            let output = try await authenticatedClient.getMyProfile()
            switch output {
            case let .ok(response):
                return try response.body.json
            case let .badRequest(response):
                throw await Self.decodeAnyBody(statusCode: 400, body: try response.body.any)
            case let .unauthorized(response):
                throw await Self.decodeAnyBody(statusCode: 401, body: try response.body.any)
            case let .forbidden(response):
                throw await Self.decodeAnyBody(statusCode: 403, body: try response.body.any)
            case let .notFound(response):
                throw await Self.decodeAnyBody(statusCode: 404, body: try response.body.any)
            case let .conflict(response):
                throw await Self.decodeAnyBody(statusCode: 409, body: try response.body.any)
            case let .preconditionFailed(response):
                throw await Self.decodeAnyBody(statusCode: 412, body: try response.body.any)
            case let .contentTooLarge(response):
                throw await Self.decodeAnyBody(statusCode: 413, body: try response.body.any)
            case let .unprocessableContent(response):
                throw await Self.decodeAnyBody(statusCode: 422, body: try response.body.any)
            case let .tooManyRequests(response):
                throw await Self.decodeAnyBody(statusCode: 429, body: try response.body.any)
            case let .internalServerError(response):
                throw await Self.decodeAnyBody(statusCode: 500, body: try response.body.any)
            case let .notImplemented(response):
                throw await Self.decodeAnyBody(statusCode: 501, body: try response.body.any)
            case let .badGateway(response):
                throw await Self.decodeAnyBody(statusCode: 502, body: try response.body.any)
            case let .undocumented(statusCode, payload):
                throw await Self.decodeOptionalBody(statusCode: statusCode, body: payload.body)
            }
        } catch {
            throw MobileApiError.classify(error)
        }
    }

    private static func executeRefresh(
        using client: Client,
        refreshToken: String
    ) async throws -> SessionTokens {
        do {
            let output = try await client.mobileRefresh(
                body: .json(.init(refreshToken: refreshToken))
            )
            switch output {
            case let .ok(response):
                let payload = try response.body.json
                return toSessionTokens(payload, username: nil)
            case let .badRequest(response):
                throw await decodeAnyBody(statusCode: 400, body: try response.body.any)
            case let .unauthorized(response):
                throw await decodeAnyBody(statusCode: 401, body: try response.body.any)
            case let .forbidden(response):
                throw await decodeAnyBody(statusCode: 403, body: try response.body.any)
            case let .notFound(response):
                throw await decodeAnyBody(statusCode: 404, body: try response.body.any)
            case let .conflict(response):
                throw await decodeAnyBody(statusCode: 409, body: try response.body.any)
            case let .preconditionFailed(response):
                throw await decodeAnyBody(statusCode: 412, body: try response.body.any)
            case let .contentTooLarge(response):
                throw await decodeAnyBody(statusCode: 413, body: try response.body.any)
            case let .unprocessableContent(response):
                throw await decodeAnyBody(statusCode: 422, body: try response.body.any)
            case let .tooManyRequests(response):
                throw await decodeAnyBody(statusCode: 429, body: try response.body.any)
            case let .internalServerError(response):
                throw await decodeAnyBody(statusCode: 500, body: try response.body.any)
            case let .notImplemented(response):
                throw await decodeAnyBody(statusCode: 501, body: try response.body.any)
            case let .badGateway(response):
                throw await decodeAnyBody(statusCode: 502, body: try response.body.any)
            case let .undocumented(statusCode, payload):
                throw await decodeOptionalBody(statusCode: statusCode, body: payload.body)
            }
        } catch {
            throw MobileApiError.classify(error)
        }
    }

    private static func toSessionTokens(
        _ response: Components.Schemas.MobileTokenResponse,
        username: String?
    ) -> SessionTokens {
        SessionTokens(
            accessToken: response.accessToken,
            refreshToken: response.refreshToken,
            accessExpiresIn: Int(response.accessExpiresIn),
            refreshExpiresIn: Int(response.refreshExpiresIn),
            mustChangePassword: response.mustChangePassword,
            username: username
        )
    }

    private static func decodeAnyBody(statusCode: Int, body: HTTPBody) async -> MobileApiError {
        await MobileApiError.decodeErrorBody(statusCode: statusCode, body: body)
    }

    private static func decodeOptionalBody(statusCode: Int, body: HTTPBody?) async -> MobileApiError {
        if let body {
            return await MobileApiError.decodeErrorBody(statusCode: statusCode, body: body)
        }
        return .unexpectedResponse(statusCode: statusCode)
    }
}
