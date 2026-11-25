import Foundation
import HTTPTypes
import OpenAPIRuntime

struct AcceptLanguageMiddleware: ClientMiddleware {
    private let localeProvider: @Sendable () async -> AppLocale

    init(localeProvider: @escaping @Sendable () async -> AppLocale) {
        self.localeProvider = localeProvider
    }

    func intercept(
        _ request: HTTPRequest,
        body: HTTPBody?,
        baseURL: URL,
        operationID: String,
        next: @Sendable (HTTPRequest, HTTPBody?, URL) async throws -> (HTTPResponse, HTTPBody?)
    ) async throws -> (HTTPResponse, HTTPBody?) {
        var mutableRequest = request
        let locale = await localeProvider()
        mutableRequest.headerFields[.acceptLanguage] = locale.rawValue
        return try await next(mutableRequest, body, baseURL)
    }
}

struct NetworkLoggingMiddleware: ClientMiddleware {
    private let logger: RedactingAppLogger

    init(logger: RedactingAppLogger) {
        self.logger = logger
    }

    func intercept(
        _ request: HTTPRequest,
        body: HTTPBody?,
        baseURL: URL,
        operationID: String,
        next: @Sendable (HTTPRequest, HTTPBody?, URL) async throws -> (HTTPResponse, HTTPBody?)
    ) async throws -> (HTTPResponse, HTTPBody?) {
        let method = request.method.rawValue
        let path = request.path ?? "/"
        let headers = request.headerFields.map { ($0.name.rawName, $0.value) }

        do {
            let (response, responseBody) = try await next(request, body, baseURL)
            logger.logHTTP(
                method: method,
                path: path,
                statusCode: response.status.code,
                headers: headers
            )
            return (response, responseBody)
        } catch {
            let safeErrorSummary: String
            switch MobileApiError.classify(error) {
            case let .api(statusCode, payload):
                safeErrorSummary = "api(\(statusCode):\(payload.code))"
            case let .network(code):
                safeErrorSummary = "network(\(code.rawValue))"
            case let .unexpectedResponse(statusCode):
                safeErrorSummary = "unexpected(\(statusCode))"
            }
            logger.logHTTP(
                method: method,
                path: path,
                headers: headers,
                errorDescription: safeErrorSummary
            )
            throw error
        }
    }
}

struct AuthBearerMiddleware: ClientMiddleware {
    static let publicOperationIDs: Set<String> = [
        Operations.mobileLogin.id,
        Operations.mobileRefresh.id,
        Operations.mobileLogout.id,
        Operations.mobileVerifyRegistration.id,
        Operations.startRegistration.id,
        Operations.resendRegistrationCode.id,
        Operations.forgotPassword.id,
        Operations.resetPasswordByToken.id
    ]

    private let tokenStore: any TokenStore
    private let refresher: TokenRefresher

    init(tokenStore: any TokenStore, refresher: TokenRefresher) {
        self.tokenStore = tokenStore
        self.refresher = refresher
    }

    func intercept(
        _ request: HTTPRequest,
        body: HTTPBody?,
        baseURL: URL,
        operationID: String,
        next: @Sendable (HTTPRequest, HTTPBody?, URL) async throws -> (HTTPResponse, HTTPBody?)
    ) async throws -> (HTTPResponse, HTTPBody?) {
        guard !Self.publicOperationIDs.contains(operationID) else {
            return try await next(request, body, baseURL)
        }

        let bufferedBodyData: Data?
        if let body {
            bufferedBodyData = try await Data(collecting: body, upTo: 1_048_576)
        } else {
            bufferedBodyData = nil
        }

        let makeBody: () -> HTTPBody? = {
            bufferedBodyData.map { HTTPBody($0) }
        }

        let initialTokens = try tokenStore.read()
        var authedRequest = request
        if let accessToken = initialTokens?.accessToken, !accessToken.isEmpty {
            authedRequest.headerFields[.authorization] = "Bearer \(accessToken)"
        }

        let (response, responseBody) = try await next(authedRequest, makeBody(), baseURL)
        guard response.status.code == 401, let failedAccessToken = initialTokens?.accessToken else {
            return (response, responseBody)
        }

        let refreshedTokens = try await refresher.refreshIfNeeded(failedAccessToken: failedAccessToken)

        var retryRequest = request
        retryRequest.headerFields[.authorization] = "Bearer \(refreshedTokens.accessToken)"
        let (retryResponse, retryBody) = try await next(retryRequest, makeBody(), baseURL)
        if retryResponse.status.code == 401 {
            await refresher.handleUnrecoverableUnauthorized(accessToken: refreshedTokens.accessToken)
        }
        return (retryResponse, retryBody)
    }
}
