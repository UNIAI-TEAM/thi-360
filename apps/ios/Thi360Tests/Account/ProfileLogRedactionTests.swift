import Foundation
import XCTest
@testable import Thi360

@MainActor
final class ProfileLogRedactionTests: XCTestCase {
    func testDemoAndDevLogsNeverContainOtpCurrentPasswordTokensOrAvatarBytes() async throws {
        try await assertRedactedFlows(environment: .demo)
        try await assertRedactedFlows(environment: .dev)
    }

    func testRedactingLoggerRedactsCurrentPasswordJSONAndQueryParam() {
        let secret = "SuperSecretDeletePass99!"
        let payload = #"{"currentPassword":"\#(secret)","code":"918273","newEmail":"a@thi360.vn"}"#
        let redacted = RedactingAppLogger.redact(payload)
        XCTAssertFalse(redacted.contains(secret), "RedactingAppLogger must redact currentPassword JSON field")
        XCTAssertFalse(redacted.contains("918273"), "RedactingAppLogger must redact code JSON field")

        let sanitizedPath = RedactingAppLogger.sanitizePath("/api/v1/me/account/deletion?currentPassword=\(secret)")
        XCTAssertFalse(sanitizedPath.contains(secret), "RedactingAppLogger must redact currentPassword query parameter")
    }

    private func assertRedactedFlows(environment: BuildEnvironment) async throws {
        let secretAccess = "eyJhbGciOiJIUzI1NiJ9.SECRET_PROFILE_ACCESS_TOKEN"
        let secretRefresh = "SECRET_PROFILE_REFRESH_TOKEN"
        let secretVerifyOTP = "918273"
        let secretConfirmOTP = "647382"
        let secretChangePassword = "SecretChangeEmailPass88!"
        let secretDeletePassword = "SecretDeleteAccountPass77!"
        let uniqueAvatarMarker = "UNIQUE_RAW_AVATAR_MARKER_BYTES_998877"

        let logSink = InMemoryLogSink()
        let (sessionStore, _, transport, preferences) = try SessionTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens(
                accessToken: secretAccess,
                refreshToken: secretRefresh
            ),
            environment: environment,
            logSink: logSink
        ) { transport in
            try transport.registerFixture(method: .get, path: "/api/v1/me/profile", statusCode: 200, relativeFixturePath: "api/me-profile-200.json")
            try transport.registerFixture(method: .put, path: "/api/v1/me/avatar", statusCode: 200, relativeFixturePath: "api/me-avatar-put-200.json")
            try transport.registerFixture(method: .post, path: "/api/v1/me/email/verify", statusCode: 422, relativeFixturePath: "api/me-email-verify-422-email-code-invalid.json")
            try transport.registerFixture(method: .post, path: "/api/v1/me/email/change", statusCode: 400, relativeFixturePath: "api/me-email-change-400-wrong-old-password.json")
            try transport.registerFixture(method: .post, path: "/api/v1/me/email/change/confirm", statusCode: 422, relativeFixturePath: "api/me-email-change-confirm-422-email-code-invalid.json")
            try transport.registerFixture(method: .get, path: "/api/v1/me/account/deletion-check", statusCode: 200, relativeFixturePath: "api/me-account-deletion-check-200.json")
            transport.enqueue(method: .post, path: "/api/v1/me/account/deletion", handlers: [
                { _ in
                    let data = try ContractFixtureLoader.loadApiFixture("me-account-deletion-400-wrong-old-password.json")
                    return ContractFakeTransport.jsonResponse(statusCode: 400, data: data)
                },
                { _ in ContractFakeTransport.emptyResponse(statusCode: 204) }
            ])
        }
        _ = preferences
        _ = transport

        let profileService = OpenAPIProfileService(apiClient: sessionStore.apiClient)
        let deletionService = OpenAPIAccountDeletionService(apiClient: sessionStore.apiClient)

        let prepared = PreparedAvatar(
            data: Data(uniqueAvatarMarker.utf8),
            mimeType: "image/jpeg",
            fileName: "avatar.jpg"
        )
        XCTAssertFalse(prepared.description.contains(uniqueAvatarMarker))

        _ = try await profileService.fetchProfile()
        _ = await profileService.uploadAvatar(prepared)
        _ = await profileService.verifyEmail(code: secretVerifyOTP)
        _ = await profileService.requestEmailChange(newEmail: "new@thi360.vn", currentPassword: secretChangePassword)
        _ = await profileService.confirmEmailChange(code: secretConfirmOTP)
        _ = try await deletionService.check()
        _ = await deletionService.delete(currentPassword: secretDeletePassword)
        _ = await deletionService.delete(currentPassword: secretDeletePassword)

        let entries = logSink.recordedEntries
        XCTAssertGreaterThanOrEqual(entries.count, 7, "Expected HTTP logs recorded for \(environment)")
        let combined = entries.map { "[\($0.level.rawValue)/\($0.category)] \($0.message)" }.joined(separator: "\n")

        XCTAssertFalse(combined.contains(secretAccess), "Access token leaked into \(environment) logs")
        XCTAssertFalse(combined.contains(secretRefresh), "Refresh token leaked into \(environment) logs")
        XCTAssertFalse(combined.contains(secretVerifyOTP), "Verify OTP leaked into \(environment) logs")
        XCTAssertFalse(combined.contains(secretConfirmOTP), "Confirm OTP leaked into \(environment) logs")
        XCTAssertFalse(combined.contains(secretChangePassword), "Change-email password leaked into \(environment) logs")
        XCTAssertFalse(combined.contains(secretDeletePassword), "Delete-account password leaked into \(environment) logs")
        XCTAssertFalse(combined.contains(uniqueAvatarMarker), "Avatar image bytes leaked into \(environment) logs")
    }
}
