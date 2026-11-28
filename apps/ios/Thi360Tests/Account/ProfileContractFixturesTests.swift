import Foundation
import XCTest
@testable import Thi360

@MainActor
final class ProfileContractFixturesTests: XCTestCase {
    func testOpenAPIContractExposesAllProfileAndDeletionOperationsAndDecodesEveryFixture() async throws {
        let openApiURL = ContractFixtureLoader.contractsMobileURL.appendingPathComponent("openapi-mobile.json")
        let openApiData = try Data(contentsOf: openApiURL)
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: openApiData) as? [String: Any])
        let paths = try XCTUnwrap(root["paths"] as? [String: Any])

        let requiredOperations: [(path: String, method: String)] = [
            ("/api/v1/me/profile", "get"),
            ("/api/v1/me/profile", "patch"),
            ("/api/v1/me/avatar", "put"),
            ("/api/v1/me/avatar", "delete"),
            ("/api/v1/avatars/{assetId}", "get"),
            ("/api/v1/me/email/verification", "post"),
            ("/api/v1/me/email/verify", "post"),
            ("/api/v1/me/email/change", "post"),
            ("/api/v1/me/email/change/confirm", "post"),
            ("/api/v1/me/account/deletion-check", "get"),
            ("/api/v1/me/account/deletion", "post")
        ]

        for op in requiredOperations {
            let pathItem = try XCTUnwrap(paths[op.path] as? [String: Any], "Missing OpenAPI path \(op.path)")
            let operation = try XCTUnwrap(pathItem[op.method] as? [String: Any], "Missing \(op.method.uppercased()) \(op.path)")
            let parameters = (operation["parameters"] as? [[String: Any]]) ?? []
            XCTAssertFalse(
                parameters.contains { ($0["in"] as? String)?.lowercased() == "cookie" },
                "Operation \(op.method.uppercased()) \(op.path) must not declare cookie parameters"
            )
        }

        let decoder = MobileAPIClient.makeJSONDecoder()

        let profileWithAvatar = try decoder.decode(
            Components.Schemas.ProfileResponse.self,
            from: ContractFixtureLoader.loadApiFixture("me-profile-200-avatar-pending-email.json")
        )
        let domainProfile = AccountProfile(from: profileWithAvatar)
        XCTAssertEqual(domainProfile.avatarAssetId, "00000000-0000-7000-8000-000000000001")
        XCTAssertEqual(domainProfile.avatarUrl, "/api/v1/avatars/00000000-0000-7000-8000-000000000001")
        XCTAssertEqual(domainProfile.pendingEmail, "student.new@thi360.vn")

        let (sessionStore, _, transport, _) = try SessionTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        ) { transport in
            try transport.registerFixture(method: .get, path: "/api/v1/me/profile", statusCode: 200, relativeFixturePath: "api/me-profile-200-avatar-pending-email.json")
            try transport.registerFixture(method: .patch, path: "/api/v1/me/profile", statusCode: 400, relativeFixturePath: "api/me-profile-update-400-phone.json")
            try transport.registerFixture(method: .put, path: "/api/v1/me/avatar", statusCode: 413, relativeFixturePath: "api/me-avatar-put-413-avatar-too-large.json")
            try transport.registerFixture(method: .post, path: "/api/v1/me/email/verification", statusCode: 429, relativeFixturePath: "api/me-email-verification-429-rate-limited.json")
            try transport.registerFixture(method: .post, path: "/api/v1/me/email/verify", statusCode: 422, relativeFixturePath: "api/me-email-verify-422-email-code-invalid.json")
            try transport.registerFixture(method: .post, path: "/api/v1/me/email/change", statusCode: 409, relativeFixturePath: "api/me-email-change-409-email-taken.json")
            try transport.registerFixture(method: .post, path: "/api/v1/me/email/change/confirm", statusCode: 422, relativeFixturePath: "api/me-email-change-confirm-422-email-code-invalid.json")
            try transport.registerFixture(method: .get, path: "/api/v1/me/account/deletion-check", statusCode: 200, relativeFixturePath: "api/me-account-deletion-check-200-blocked.json")
            try transport.registerFixture(method: .post, path: "/api/v1/me/account/deletion", statusCode: 409, relativeFixturePath: "api/me-account-deletion-409-account-deletion-blocked.json")
        }

        let profileService = OpenAPIProfileService(apiClient: sessionStore.apiClient)
        let deletionService = OpenAPIAccountDeletionService(apiClient: sessionStore.apiClient)

        let fetched = try await profileService.fetchProfile()
        XCTAssertEqual(fetched.pendingEmail, "student.new@thi360.vn")

        let patchErr = await profileService.updateProfile(fullName: "Test", phone: "123")
        XCTAssertEqual(patchErr, .failure(.field(name: "phone", code: "PHONE_INVALID")))

        let avatarErr = await profileService.uploadAvatar(PreparedAvatar(data: try ProfileFixtureHelper.makeSamplePNG(), mimeType: "image/png"))
        XCTAssertEqual(avatarErr, .failure(.avatarTooLarge))

        let rateErr = await profileService.sendVerification()
        XCTAssertEqual(rateErr.failureError, .rateLimited(retryAfter: 60))

        let verifyErr = await profileService.verifyEmail(code: "123456")
        XCTAssertEqual(verifyErr.failureError, .codeInvalid(remaining: 4))

        let changeErr = await profileService.requestEmailChange(newEmail: "a@b.com", currentPassword: "p")
        XCTAssertEqual(changeErr.failureError, .emailTaken)

        let confirmErr = await profileService.confirmEmailChange(code: "123456")
        XCTAssertEqual(confirmErr.failureError, .codeInvalid(remaining: 4))

        let checkResult = try await deletionService.check()
        XCTAssertFalse(checkResult.canDelete)
        XCTAssertEqual(Set(checkResult.blockers), [.attemptInProgress, .orderBeingChecked, .orderPendingPayment])

        let deleteBlocked = await deletionService.delete(currentPassword: "Password123!")
        XCTAssertEqual(deleteBlocked, .blocked([.attemptInProgress, .orderBeingChecked, .orderPendingPayment]))

        try transport.registerFixture(method: .put, path: "/api/v1/me/avatar", statusCode: 422, relativeFixturePath: "api/me-avatar-put-422-avatar-dimensions-exceeded.json")
        let dimErr = await profileService.uploadAvatar(PreparedAvatar(data: try ProfileFixtureHelper.makeSamplePNG(), mimeType: "image/png"))
        XCTAssertEqual(dimErr, .failure(.avatarDimensions))

        try transport.registerFixture(method: .put, path: "/api/v1/me/avatar", statusCode: 422, relativeFixturePath: "api/me-avatar-put-422-avatar-invalid-image.json")
        let invalidImgErr = await profileService.uploadAvatar(PreparedAvatar(data: try ProfileFixtureHelper.makeSamplePNG(), mimeType: "image/png"))
        XCTAssertEqual(invalidImgErr, .failure(.avatarInvalid))

        try transport.registerFixture(method: .post, path: "/api/v1/me/email/verify", statusCode: 422, relativeFixturePath: "api/me-email-verify-422-email-code-expired.json")
        let expiredErr = await profileService.verifyEmail(code: "123456")
        XCTAssertEqual(expiredErr.failureError, .codeExpired)

        try transport.registerFixture(method: .post, path: "/api/v1/me/email/change", statusCode: 400, relativeFixturePath: "api/me-email-change-400-wrong-old-password.json")
        let wrongPwdErr = await profileService.requestEmailChange(newEmail: "a@b.com", currentPassword: "bad")
        XCTAssertEqual(wrongPwdErr.failureError, .field(name: "currentPassword", code: "WRONG_OLD_PASSWORD"))

        try transport.registerFixture(method: .post, path: "/api/v1/me/account/deletion", statusCode: 400, relativeFixturePath: "api/me-account-deletion-400-wrong-old-password.json")
        let delWrongPwd = await deletionService.delete(currentPassword: "bad")
        XCTAssertEqual(delWrongPwd, .failed(.wrongPassword))

        try transport.registerFixture(method: .post, path: "/api/v1/me/account/deletion", statusCode: 429, relativeFixturePath: "api/me-account-deletion-429-login-throttled.json")
        let delThrottled = await deletionService.delete(currentPassword: "bad")
        XCTAssertEqual(delThrottled, .failed(.throttled))
    }
}

private extension Result where Failure == ProfileError {
    var failureError: ProfileError? {
        if case let .failure(error) = self {
            return error
        }
        return nil
    }
}
