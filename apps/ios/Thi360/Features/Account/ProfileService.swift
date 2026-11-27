import Foundation
import HTTPTypes
import OpenAPIRuntime

protocol ProfileServiceProtocol: Sendable {
    func fetchProfile() async throws -> AccountProfile
    func updateProfile(fullName: String, phone: String?) async -> Result<AccountProfile, ProfileError>
    func uploadAvatar(_ avatar: PreparedAvatar) async -> Result<AccountProfile, ProfileError>
    func removeAvatar() async -> Result<Void, ProfileError>
    func sendVerification() async -> Result<Void, ProfileError>
    func verifyEmail(code: String) async -> Result<Void, ProfileError>
    func requestEmailChange(newEmail: String, currentPassword: String) async -> Result<Void, ProfileError>
    func confirmEmailChange(code: String) async -> Result<Void, ProfileError>
}

struct OpenAPIProfileService: ProfileServiceProtocol {
    private let apiClient: MobileAPIClient

    init(apiClient: MobileAPIClient) {
        self.apiClient = apiClient
    }

    func fetchProfile() async throws -> AccountProfile {
        let response = try await apiClient.getMyProfile()
        return AccountProfile(from: response)
    }

    func updateProfile(fullName: String, phone: String?) async -> Result<AccountProfile, ProfileError> {
        do {
            let output = try await apiClient.authenticatedClient.updateMyProfile(
                body: .json(.init(fullName: fullName, phone: phone))
            )
            switch output {
            case let .ok(response):
                let payload = try response.body.json
                return .success(AccountProfile(from: payload))
            case let .badRequest(response):
                let error = await MobileApiError.decodeErrorBody(statusCode: 400, body: try response.body.any)
                return .failure(Self.mapProfileUpdateError(error))
            case let .unauthorized(response):
                let error = await MobileApiError.decodeErrorBody(statusCode: 401, body: try response.body.any)
                return .failure(Self.mapProfileUpdateError(error))
            case let .forbidden(response):
                let error = await MobileApiError.decodeErrorBody(statusCode: 403, body: try response.body.any)
                return .failure(Self.mapProfileUpdateError(error))
            case let .notFound(response):
                let error = await MobileApiError.decodeErrorBody(statusCode: 404, body: try response.body.any)
                return .failure(Self.mapProfileUpdateError(error))
            case let .conflict(response):
                let error = await MobileApiError.decodeErrorBody(statusCode: 409, body: try response.body.any)
                return .failure(Self.mapProfileUpdateError(error))
            case let .preconditionFailed(response):
                let error = await MobileApiError.decodeErrorBody(statusCode: 412, body: try response.body.any)
                return .failure(Self.mapProfileUpdateError(error))
            case let .contentTooLarge(response):
                let error = await MobileApiError.decodeErrorBody(statusCode: 413, body: try response.body.any)
                return .failure(Self.mapProfileUpdateError(error))
            case let .unprocessableContent(response):
                let error = await MobileApiError.decodeErrorBody(statusCode: 422, body: try response.body.any)
                return .failure(Self.mapProfileUpdateError(error))
            case let .tooManyRequests(response):
                let error = await MobileApiError.decodeErrorBody(statusCode: 429, body: try response.body.any)
                return .failure(Self.mapProfileUpdateError(error))
            case let .internalServerError(response):
                let error = await MobileApiError.decodeErrorBody(statusCode: 500, body: try response.body.any)
                return .failure(Self.mapProfileUpdateError(error))
            case let .notImplemented(response):
                let error = await MobileApiError.decodeErrorBody(statusCode: 501, body: try response.body.any)
                return .failure(Self.mapProfileUpdateError(error))
            case let .badGateway(response):
                let error = await MobileApiError.decodeErrorBody(statusCode: 502, body: try response.body.any)
                return .failure(Self.mapProfileUpdateError(error))
            case let .undocumented(statusCode, payload):
                let error = await Self.decodeOptionalBody(statusCode: statusCode, body: payload.body)
                return .failure(Self.mapProfileUpdateError(error))
            }
        } catch {
            return .failure(Self.mapProfileUpdateError(MobileApiError.classify(error)))
        }
    }

    func uploadAvatar(_ avatar: PreparedAvatar) async -> Result<AccountProfile, ProfileError> {
        do {
            let client = apiClient.makeAuthenticatedClient(
                leadingMiddlewares: [AvatarMultipartMiddleware(avatar: avatar)]
            )
            let output = try await client.replaceMyAvatar()
            switch output {
            case let .ok(response):
                let payload = try response.body.json
                return .success(AccountProfile(from: payload))
            case let .badRequest(response):
                let error = await MobileApiError.decodeErrorBody(statusCode: 400, body: try response.body.any)
                return .failure(Self.mapAvatarUploadError(error))
            case let .unauthorized(response):
                let error = await MobileApiError.decodeErrorBody(statusCode: 401, body: try response.body.any)
                return .failure(Self.mapAvatarUploadError(error))
            case let .forbidden(response):
                let error = await MobileApiError.decodeErrorBody(statusCode: 403, body: try response.body.any)
                return .failure(Self.mapAvatarUploadError(error))
            case let .notFound(response):
                let error = await MobileApiError.decodeErrorBody(statusCode: 404, body: try response.body.any)
                return .failure(Self.mapAvatarUploadError(error))
            case let .conflict(response):
                let error = await MobileApiError.decodeErrorBody(statusCode: 409, body: try response.body.any)
                return .failure(Self.mapAvatarUploadError(error))
            case let .preconditionFailed(response):
                let error = await MobileApiError.decodeErrorBody(statusCode: 412, body: try response.body.any)
                return .failure(Self.mapAvatarUploadError(error))
            case let .contentTooLarge(response):
                let error = await MobileApiError.decodeErrorBody(statusCode: 413, body: try response.body.any)
                return .failure(Self.mapAvatarUploadError(error))
            case let .unprocessableContent(response):
                let error = await MobileApiError.decodeErrorBody(statusCode: 422, body: try response.body.any)
                return .failure(Self.mapAvatarUploadError(error))
            case let .tooManyRequests(response):
                let error = await MobileApiError.decodeErrorBody(statusCode: 429, body: try response.body.any)
                return .failure(Self.mapAvatarUploadError(error))
            case let .internalServerError(response):
                let error = await MobileApiError.decodeErrorBody(statusCode: 500, body: try response.body.any)
                return .failure(Self.mapAvatarUploadError(error))
            case let .notImplemented(response):
                let error = await MobileApiError.decodeErrorBody(statusCode: 501, body: try response.body.any)
                return .failure(Self.mapAvatarUploadError(error))
            case let .badGateway(response):
                let error = await MobileApiError.decodeErrorBody(statusCode: 502, body: try response.body.any)
                return .failure(Self.mapAvatarUploadError(error))
            case let .undocumented(statusCode, payload):
                let error = await Self.decodeOptionalBody(statusCode: statusCode, body: payload.body)
                return .failure(Self.mapAvatarUploadError(error))
            }
        } catch {
            if let clientError = error as? ClientError, clientError.response?.status.code == 413 {
                return .failure(.avatarTooLarge)
            }
            return .failure(Self.mapAvatarUploadError(MobileApiError.classify(error)))
        }
    }

    func removeAvatar() async -> Result<Void, ProfileError> {
        do {
            let output = try await apiClient.authenticatedClient.removeMyAvatar()
            switch output {
            case .noContent:
                return .success(())
            case let .undocumented(statusCode, _) where (200...299).contains(statusCode):
                return .success(())
            case .badRequest, .unauthorized, .forbidden, .notFound, .conflict,
                 .preconditionFailed, .contentTooLarge, .unprocessableContent,
                 .tooManyRequests, .internalServerError, .notImplemented,
                 .badGateway, .undocumented:
                return .failure(.other)
            }
        } catch {
            let classified = MobileApiError.classify(error)
            return .failure(classified.isOfflineOrConnectivity ? .offline : .other)
        }
    }

    func sendVerification() async -> Result<Void, ProfileError> {
        do {
            let output = try await apiClient.authenticatedClient.sendVerification()
            switch output {
            case .accepted:
                return .success(())
            case let .tooManyRequests(response):
                let payload = try response.body.json
                return .failure(.rateLimited(retryAfter: payload.retryAfter.map(Int.init)))
            case let .undocumented(statusCode, _) where (200...299).contains(statusCode):
                return .success(())
            case let .undocumented(statusCode, payload) where statusCode == 429:
                let error = await Self.decodeOptionalBody(statusCode: 429, body: payload.body)
                return .failure(Self.mapSendVerificationError(error))
            case .badRequest, .unauthorized, .forbidden, .notFound, .conflict,
                 .preconditionFailed, .contentTooLarge, .unprocessableContent,
                 .internalServerError, .notImplemented, .badGateway, .undocumented:
                return .failure(.other)
            }
        } catch {
            if let clientError = error as? ClientError, clientError.response?.status.code == 429 {
                let retryAfter = RetryAfter.parseSeconds(from: clientError.response?.headerFields)
                return .failure(.rateLimited(retryAfter: retryAfter))
            }
            return .failure(Self.mapSendVerificationError(MobileApiError.classify(error)))
        }
    }

    func verifyEmail(code: String) async -> Result<Void, ProfileError> {
        do {
            let output = try await apiClient.authenticatedClient.verify(
                body: .json(.init(code: code))
            )
            switch output {
            case .noContent:
                return .success(())
            case let .unprocessableContent(response):
                let payload = try response.body.json
                return .failure(Self.mapEmailCodeErrorResponse(payload))
            case let .undocumented(statusCode, _) where (200...299).contains(statusCode):
                return .success(())
            case let .undocumented(statusCode, payload):
                let error = await Self.decodeOptionalBody(statusCode: statusCode, body: payload.body)
                return .failure(Self.mapVerifyEmailError(error))
            case .badRequest, .unauthorized, .forbidden, .notFound, .conflict,
                 .preconditionFailed, .contentTooLarge, .tooManyRequests,
                 .internalServerError, .notImplemented, .badGateway:
                return .failure(.other)
            }
        } catch {
            return .failure(Self.mapVerifyEmailError(MobileApiError.classify(error)))
        }
    }

    func requestEmailChange(newEmail: String, currentPassword: String) async -> Result<Void, ProfileError> {
        do {
            let output = try await apiClient.authenticatedClient.requestChange(
                body: .json(.init(currentPassword: currentPassword, newEmail: newEmail))
            )
            switch output {
            case .accepted:
                return .success(())
            case let .badRequest(response):
                let error = await MobileApiError.decodeErrorBody(statusCode: 400, body: try response.body.any)
                return .failure(Self.mapRequestEmailChangeError(error))
            case let .conflict(response):
                let error = await MobileApiError.decodeErrorBody(statusCode: 409, body: try response.body.any)
                return .failure(Self.mapRequestEmailChangeError(error))
            case let .tooManyRequests(response):
                let payload = try response.body.json
                let retryAfter = payload.retryAfter.map(Int.init)
                if payload.code == "LOGIN_THROTTLED" {
                    if let retryAfter {
                        return .failure(.rateLimited(retryAfter: retryAfter))
                    }
                    return .failure(.throttled)
                }
                return .failure(.rateLimited(retryAfter: retryAfter))
            case let .unprocessableContent(response):
                let error = await MobileApiError.decodeErrorBody(statusCode: 422, body: try response.body.any)
                return .failure(Self.mapRequestEmailChangeError(error))
            case let .undocumented(statusCode, _) where (200...299).contains(statusCode):
                return .success(())
            case let .undocumented(statusCode, payload):
                let error = await Self.decodeOptionalBody(statusCode: statusCode, body: payload.body)
                return .failure(Self.mapRequestEmailChangeError(error))
            case .unauthorized, .forbidden, .notFound, .preconditionFailed,
                 .contentTooLarge, .internalServerError, .notImplemented, .badGateway:
                return .failure(.other)
            }
        } catch {
            if let clientError = error as? ClientError, clientError.response?.status.code == 429 {
                let retryAfter = RetryAfter.parseSeconds(from: clientError.response?.headerFields)
                return .failure(.rateLimited(retryAfter: retryAfter))
            }
            return .failure(Self.mapRequestEmailChangeError(MobileApiError.classify(error)))
        }
    }

    func confirmEmailChange(code: String) async -> Result<Void, ProfileError> {
        do {
            let output = try await apiClient.authenticatedClient.confirmChange(
                body: .json(.init(code: code))
            )
            switch output {
            case .noContent:
                return .success(())
            case let .conflict(response):
                let error = await MobileApiError.decodeErrorBody(statusCode: 409, body: try response.body.any)
                return .failure(Self.mapConfirmEmailChangeError(error))
            case let .unprocessableContent(response):
                let payload = try response.body.json
                return .failure(Self.mapEmailCodeErrorResponse(payload))
            case .serviceUnavailable:
                return .failure(.syncFailed)
            case let .undocumented(statusCode, _) where (200...299).contains(statusCode):
                return .success(())
            case let .undocumented(statusCode, payload):
                let error = await Self.decodeOptionalBody(statusCode: statusCode, body: payload.body)
                return .failure(Self.mapConfirmEmailChangeError(error))
            case .badRequest, .unauthorized, .forbidden, .notFound,
                 .preconditionFailed, .contentTooLarge, .tooManyRequests,
                 .internalServerError, .notImplemented, .badGateway:
                return .failure(.other)
            }
        } catch {
            if let clientError = error as? ClientError, clientError.response?.status.code == 503 {
                return .failure(.syncFailed)
            }
            return .failure(Self.mapConfirmEmailChangeError(MobileApiError.classify(error)))
        }
    }

    static func mapProfileUpdateError(_ error: MobileApiError) -> ProfileError {
        if error.isOfflineOrConnectivity {
            return .offline
        }
        guard case let .api(_, payload) = error else {
            return .other
        }
        if let phoneField = payload.fieldErrors.first(where: { $0.field == "phone" }) {
            return .field(name: phoneField.field, code: phoneField.code)
        }
        if let nameField = payload.fieldErrors.first(where: { $0.field == "fullName" }) {
            return .field(name: nameField.field, code: nameField.code)
        }
        return .other
    }

    static func mapAvatarUploadError(_ error: MobileApiError) -> ProfileError {
        if error.isOfflineOrConnectivity {
            return .offline
        }
        switch error {
        case let .api(statusCode, payload):
            if statusCode == 413 || payload.code == "AVATAR_TOO_LARGE" || payload.code == "UPLOAD_TOO_LARGE" {
                return .avatarTooLarge
            }
            if payload.code == "AVATAR_DIMENSIONS_EXCEEDED" {
                return .avatarDimensions
            }
            if payload.code == "AVATAR_INVALID_IMAGE" {
                return .avatarInvalid
            }
            return .other
        case let .unexpectedResponse(statusCode) where statusCode == 413:
            return .avatarTooLarge
        default:
            return .other
        }
    }

    static func mapSendVerificationError(_ error: MobileApiError) -> ProfileError {
        if error.isOfflineOrConnectivity {
            return .offline
        }
        if error.statusCode == 429 {
            return .rateLimited(retryAfter: error.retryAfterSeconds)
        }
        return .other
    }

    static func mapVerifyEmailError(_ error: MobileApiError) -> ProfileError {
        if error.isOfflineOrConnectivity {
            return .offline
        }
        guard case let .api(_, payload) = error else {
            return .other
        }
        switch payload.code {
        case "EMAIL_CODE_INVALID":
            return .codeInvalid(remaining: payload.remainingAttempts)
        case "EMAIL_CODE_EXPIRED":
            return .codeExpired
        default:
            return .other
        }
    }

    static func mapRequestEmailChangeError(_ error: MobileApiError) -> ProfileError {
        if error.isOfflineOrConnectivity {
            return .offline
        }
        guard case let .api(statusCode, payload) = error else {
            if error.statusCode == 429 {
                return .rateLimited(retryAfter: nil)
            }
            return .other
        }
        if statusCode == 429 {
            if payload.code == "LOGIN_THROTTLED" {
                if let retryAfter = payload.retryAfter {
                    return .rateLimited(retryAfter: retryAfter)
                }
                return .throttled
            }
            return .rateLimited(retryAfter: payload.retryAfter)
        }
        if statusCode == 409, payload.code == "EMAIL_TAKEN" {
            return .emailTaken
        }
        if let passwordField = payload.fieldErrors.first(where: { $0.field == "currentPassword" }) {
            return .field(name: "currentPassword", code: passwordField.code)
        }
        if payload.code == "WRONG_OLD_PASSWORD" {
            return .field(name: "currentPassword", code: "WRONG_OLD_PASSWORD")
        }
        if let emailField = payload.fieldErrors.first(where: { $0.field == "newEmail" }) {
            return .field(name: "newEmail", code: emailField.code)
        }
        return .other
    }

    static func mapConfirmEmailChangeError(_ error: MobileApiError) -> ProfileError {
        if error.isOfflineOrConnectivity {
            return .offline
        }
        if error.statusCode == 503 {
            return .syncFailed
        }
        guard case let .api(statusCode, payload) = error else {
            return .other
        }
        switch payload.code {
        case "EMAIL_CODE_INVALID":
            return .codeInvalid(remaining: payload.remainingAttempts)
        case "EMAIL_CODE_EXPIRED":
            return .codeExpired
        case "EMAIL_TAKEN" where statusCode == 409:
            return .emailTaken
        default:
            return .other
        }
    }

    private static func mapEmailCodeErrorResponse(_ payload: Components.Schemas.EmailCodeErrorResponse) -> ProfileError {
        switch payload.code {
        case "EMAIL_CODE_INVALID":
            return .codeInvalid(remaining: payload.remainingAttempts.map(Int.init))
        case "EMAIL_CODE_EXPIRED":
            return .codeExpired
        default:
            return .other
        }
    }

    private static func decodeOptionalBody(statusCode: Int, body: HTTPBody?) async -> MobileApiError {
        if let body {
            return await MobileApiError.decodeErrorBody(statusCode: statusCode, body: body)
        }
        return .unexpectedResponse(statusCode: statusCode)
    }
}

struct AvatarMultipartMiddleware: ClientMiddleware {
    let avatar: PreparedAvatar
    let boundary: String

    init(avatar: PreparedAvatar, boundary: String = "Thi360Avatar-\(UUID().uuidString)") {
        self.avatar = avatar
        self.boundary = boundary
    }

    func intercept(
        _ request: HTTPRequest,
        body: HTTPBody?,
        baseURL: URL,
        operationID: String,
        next: @Sendable (HTTPRequest, HTTPBody?, URL) async throws -> (HTTPResponse, HTTPBody?)
    ) async throws -> (HTTPResponse, HTTPBody?) {
        guard operationID == Operations.replaceMyAvatar.id else {
            return try await next(request, body, baseURL)
        }
        var mutated = request
        mutated.headerFields[.contentType] = "multipart/form-data; boundary=\(boundary)"
        let multipartData = Self.encodeMultipart(avatar: avatar, boundary: boundary)
        return try await next(mutated, HTTPBody(multipartData), baseURL)
    }

    static func encodeMultipart(avatar: PreparedAvatar, boundary: String) -> Data {
        var data = Data()
        data.append(Data("--\(boundary)\r\n".utf8))
        data.append(Data("Content-Disposition: form-data; name=\"file\"; filename=\"\(avatar.fileName)\"\r\n".utf8))
        data.append(Data("Content-Type: \(avatar.mimeType)\r\n\r\n".utf8))
        data.append(avatar.data)
        data.append(Data("\r\n--\(boundary)--\r\n".utf8))
        return data
    }
}
