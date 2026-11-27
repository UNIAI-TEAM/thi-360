import Foundation
import OpenAPIRuntime
import OpenAPIURLSession

struct RegisterStartResult: Equatable, Sendable {
    let registrationId: String
    let email: String
    let resendAfterSeconds: Int
}

protocol MobileAuthClientProtocol: Sendable {
    func login(identifier: String, password: String) async throws -> SessionTokens
    func refresh(refreshToken: String) async throws -> SessionTokens
    func logout(refreshToken: String) async throws
    func me() async throws -> Components.Schemas.MeResponse
    func getMyProfile() async throws -> Components.Schemas.ProfileResponse
}

protocol MobileRecoveryClientProtocol: Sendable {
    func startRegistration(
        username: String,
        fullName: String,
        email: String,
        password: String,
        acceptTerms: Bool,
        termsVersion: String,
        registrationId: String?
    ) async throws -> RegisterStartResult
    func resendRegistrationCode(registrationId: String) async throws -> RegisterStartResult
    func verifyRegistration(registrationId: String, code: String, username: String?) async throws -> SessionTokens
    func forgotPassword(email: String) async throws
    func resetPassword(token: String, newPassword: String) async throws
    func changePassword(oldPassword: String, newPassword: String) async throws
}

struct FlexibleISO8601DateTranscoder: DateTranscoder, Sendable {
    func encode(_ date: Date) throws -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.string(from: date)
    }

    func decode(_ dateString: String) throws -> Date {
        if let date = Self.parse(dateString) {
            return date
        }
        throw DecodingError.dataCorrupted(
            DecodingError.Context(
                codingPath: [],
                debugDescription: "Invalid ISO-8601 date: \(dateString)"
            )
        )
    }

    static func parse(_ dateString: String) -> Date? {
        let withFraction = ISO8601DateFormatter()
        withFraction.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = withFraction.date(from: dateString) {
            return date
        }
        let standard = ISO8601DateFormatter()
        standard.formatOptions = [.withInternetDateTime]
        return standard.date(from: dateString)
    }
}

final class MobileAPIClient: MobileAuthClientProtocol, MobileRecoveryClientProtocol, Sendable {
    let serverURL: URL
    let publicClient: Client
    let authenticatedClient: Client
    let refresher: TokenRefresher

    private let configuration: Configuration
    private let transport: any ClientTransport
    private let acceptLanguage: AcceptLanguageMiddleware
    private let retryAfter: RetryAfterMiddleware
    private let bearer: AuthBearerMiddleware
    private let logging: NetworkLoggingMiddleware

    init(
        serverURL: URL,
        tokenStore: any TokenStore,
        transport: any ClientTransport = URLSessionTransport(),
        logger: RedactingAppLogger = RedactingAppLogger(),
        localeProvider: @escaping @Sendable () async -> AppLocale
    ) {
        self.serverURL = serverURL

        let configuration = Configuration(dateTranscoder: FlexibleISO8601DateTranscoder())
        let acceptLanguage = AcceptLanguageMiddleware(localeProvider: localeProvider)
        let retryAfter = RetryAfterMiddleware()
        let logging = NetworkLoggingMiddleware(logger: logger)

        let publicClient = Client(
            serverURL: serverURL,
            configuration: configuration,
            transport: transport,
            middlewares: [acceptLanguage, retryAfter, logging]
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
        self.configuration = configuration
        self.transport = transport
        self.acceptLanguage = acceptLanguage
        self.retryAfter = retryAfter
        self.bearer = bearer
        self.logging = logging
        self.authenticatedClient = Client(
            serverURL: serverURL,
            configuration: configuration,
            transport: transport,
            middlewares: [acceptLanguage, retryAfter, bearer, logging]
        )
    }

    func makePublicClient(leadingMiddlewares: [any ClientMiddleware]) -> Client {
        Client(
            serverURL: serverURL,
            configuration: configuration,
            transport: transport,
            middlewares: leadingMiddlewares + [acceptLanguage, retryAfter, logging]
        )
    }

    func makeAuthenticatedClient(leadingMiddlewares: [any ClientMiddleware]) -> Client {
        Client(
            serverURL: serverURL,
            configuration: configuration,
            transport: transport,
            middlewares: [acceptLanguage] + leadingMiddlewares + [retryAfter, bearer, logging]
        )
    }

    static func makeJSONDecoder() -> JSONDecoder {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .custom { decoder in
            let container = try decoder.singleValueContainer()
            let raw = try container.decode(String.self)
            guard let date = FlexibleISO8601DateTranscoder.parse(raw) else {
                throw DecodingError.dataCorruptedError(
                    in: container,
                    debugDescription: "Invalid ISO-8601 date: \(raw)"
                )
            }
            return date
        }
        return decoder
    }

    static func decodeJSONBody<T: Decodable>(
        _ type: T.Type,
        from body: HTTPBody,
        maxBytes: Int = 8_388_608
    ) async throws -> T {
        let data = try await Data(collecting: body, upTo: maxBytes)
        return try makeJSONDecoder().decode(type, from: data)
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

    func startRegistration(
        username: String,
        fullName: String,
        email: String,
        password: String,
        acceptTerms: Bool,
        termsVersion: String,
        registrationId: String?
    ) async throws -> RegisterStartResult {
        do {
            let output = try await publicClient.startRegistration(
                body: .json(.init(
                    acceptTerms: acceptTerms,
                    callbackUrl: nil,
                    email: email,
                    fullName: fullName,
                    password: password,
                    registrationId: registrationId,
                    termsVersion: termsVersion,
                    username: username
                ))
            )
            switch output {
            case let .ok(response):
                let payload = try response.body.json
                return RegisterStartResult(
                    registrationId: payload.registrationId,
                    email: payload.email,
                    resendAfterSeconds: Int(payload.resendAfterSeconds)
                )
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
                let payload = try response.body.json
                throw MobileApiError.api(statusCode: 429, error: Self.toApiError(payload))
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

    func resendRegistrationCode(registrationId: String) async throws -> RegisterStartResult {
        do {
            let output = try await publicClient.resendRegistrationCode(
                body: .json(.init(registrationId: registrationId))
            )
            switch output {
            case let .ok(response):
                let payload = try response.body.json
                return RegisterStartResult(
                    registrationId: payload.registrationId,
                    email: payload.email,
                    resendAfterSeconds: Int(payload.resendAfterSeconds)
                )
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
                let payload = try response.body.json
                throw MobileApiError.api(statusCode: 422, error: Self.toApiError(payload))
            case let .tooManyRequests(response):
                let payload = try response.body.json
                throw MobileApiError.api(statusCode: 429, error: Self.toApiError(payload))
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

    func verifyRegistration(
        registrationId: String,
        code: String,
        username: String?
    ) async throws -> SessionTokens {
        do {
            let output = try await publicClient.mobileVerifyRegistration(
                body: .json(.init(code: code, registrationId: registrationId))
            )
            switch output {
            case let .created(response):
                let payload = try response.body.json
                return Self.toSessionTokens(payload, username: username)
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
                let payload = try response.body.json
                throw MobileApiError.api(statusCode: 422, error: Self.toApiError(payload))
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

    func forgotPassword(email: String) async throws {
        do {
            let output = try await publicClient.forgotPassword(
                body: .json(.init(email: email))
            )
            switch output {
            case .accepted:
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

    func resetPassword(token: String, newPassword: String) async throws {
        do {
            let output = try await publicClient.resetPasswordByToken(
                body: .json(.init(newPassword: newPassword, token: token))
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

    func changePassword(oldPassword: String, newPassword: String) async throws {
        do {
            let output = try await authenticatedClient.changePassword(
                body: .json(.init(newPassword: newPassword, oldPassword: oldPassword))
            )
            switch output {
            case .ok:
                return
            case let .undocumented(statusCode, _) where statusCode == 204:
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

    private static func toApiError(_ response: Components.Schemas.EmailCodeErrorResponse) -> ApiError {
        ApiError(
            code: response.code,
            message: response.message,
            fieldErrors: response.fieldErrors.map {
                ApiFieldError(field: $0.field, code: $0.code, message: $0.message)
            },
            traceId: response.traceId,
            retryAfter: response.retryAfter.map(Int.init),
            remainingAttempts: response.remainingAttempts.map(Int.init)
        )
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
