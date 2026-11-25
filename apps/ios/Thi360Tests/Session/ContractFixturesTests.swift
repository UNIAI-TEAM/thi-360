import Foundation
import XCTest
@testable import Thi360

@MainActor
final class ContractFixturesTests: XCTestCase {
    func testOpenApiSpecLinkMatchesCommittedMobileOpenApiContract() throws {
        let canonicalSpecURL = ContractFixtureLoader.contractsMobileURL
            .appendingPathComponent("openapi-mobile.json")
        let iosOpenApiURL = ContractFixtureLoader.repoRootURL
            .appendingPathComponent("apps/ios/Thi360/Core/Network/OpenAPI/openapi.json")

        let canonicalData = try Data(contentsOf: canonicalSpecURL)
        let iosLinkedData = try Data(contentsOf: iosOpenApiURL)
        XCTAssertEqual(
            iosLinkedData,
            canonicalData,
            "iOS OpenAPI generator input must match contracts/mobile/openapi-mobile.json byte-for-byte"
        )
    }

    func testAllAuthContractFixturesDecodeIntoGeneratedOpenApiSchemas() throws {
        let decoder = JSONDecoder()

        let tokenFixtures = [
            "login-200.json",
            "login-200-must-change-password.json",
            "refresh-200.json",
            "register-verify-201.json"
        ]
        for file in tokenFixtures {
            let data = try ContractFixtureLoader.loadAuthFixture(file)
            let decoded = try decoder.decode(Components.Schemas.MobileTokenResponse.self, from: data)
            XCTAssertFalse(decoded.accessToken.isEmpty, file)
            XCTAssertFalse(decoded.refreshToken.isEmpty, file)
            XCTAssertGreaterThan(decoded.accessExpiresIn, 0, file)
            XCTAssertGreaterThan(decoded.refreshExpiresIn, 0, file)
        }

        let apiErrorFixtures = [
            "login-400-validation-failed.json",
            "login-401-invalid-credentials.json",
            "login-403-account-inactive.json",
            "login-403-app-student-only.json",
            "login-429-login-throttled.json",
            "login-429-rate-limited.json",
            "refresh-401-session-revoked.json",
            "refresh-403-app-student-only.json",
            "register-verify-429-rate-limited.json"
        ]
        for file in apiErrorFixtures {
            let data = try ContractFixtureLoader.loadAuthFixture(file)
            let generated = try decoder.decode(Components.Schemas.ApiError.self, from: data)
            let domain = try decoder.decode(ApiError.self, from: data)
            XCTAssertFalse(generated.code.isEmpty, file)
            XCTAssertEqual(domain.code, generated.code, file)
        }

        let otpErrorFixtures = [
            "register-verify-422-email-code-expired.json",
            "register-verify-422-email-code-invalid.json"
        ]
        for file in otpErrorFixtures {
            let data = try ContractFixtureLoader.loadAuthFixture(file)
            let decoded = try decoder.decode(Components.Schemas.EmailCodeErrorResponse.self, from: data)
            XCTAssertFalse(decoded.code.isEmpty, file)
        }
    }

    func testAllSharedApiContractFixturesDecodeIntoGeneratedOpenApiSchemas() throws {
        let decoder = JSONDecoder()

        let me = try decoder.decode(
            Components.Schemas.MeResponse.self,
            from: try ContractFixtureLoader.loadApiFixture("me-200.json")
        )
        XCTAssertEqual(me.access, .EXAM)
        XCTAssertEqual(me.realm, .APP)

        let profileWithEmail = try decoder.decode(
            Components.Schemas.ProfileResponse.self,
            from: try ContractFixtureLoader.loadApiFixture("me-profile-200.json")
        )
        XCTAssertEqual(profileWithEmail.email, "student.demo@thi360.vn")
        XCTAssertTrue(profileWithEmail.emailVerified)

        let profileWithoutEmail = try decoder.decode(
            Components.Schemas.ProfileResponse.self,
            from: try ContractFixtureLoader.loadApiFixture("me-profile-200-no-email.json")
        )
        XCTAssertNil(profileWithoutEmail.email)
        XCTAssertFalse(profileWithoutEmail.emailVerified)

        for file in ["register-200.json", "register-resend-200.json"] {
            let reg = try decoder.decode(
                Components.Schemas.RegisterStartResponse.self,
                from: try ContractFixtureLoader.loadApiFixture(file)
            )
            XCTAssertFalse(reg.registrationId.isEmpty, file)
            XCTAssertGreaterThan(reg.resendAfterSeconds, 0, file)
        }

        let forgot = try decoder.decode(
            Components.Schemas.PasswordForgotResponse.self,
            from: try ContractFixtureLoader.loadApiFixture("password-forgot-202.json")
        )
        XCTAssertFalse(forgot.message.isEmpty)

        for file in [
            "change-password-400-password-weak.json",
            "change-password-400-wrong-old-password.json",
            "me-401-session-revoked.json",
            "me-401-unauthenticated.json",
            "password-forgot-400-validation-failed.json",
            "password-reset-400-password-weak.json",
            "password-reset-422-reset-token-invalid.json",
            "register-400-validation-failed.json",
            "register-409-email-taken.json",
            "register-409-username-taken.json"
        ] {
            let err = try decoder.decode(
                Components.Schemas.ApiError.self,
                from: try ContractFixtureLoader.loadApiFixture(file)
            )
            XCTAssertFalse(err.code.isEmpty, file)
        }

        for file in [
            "register-429-otp-rate-limited.json",
            "register-resend-422-email-code-expired.json",
            "register-resend-429-rate-limited.json"
        ] {
            let err = try decoder.decode(
                Components.Schemas.EmailCodeErrorResponse.self,
                from: try ContractFixtureLoader.loadApiFixture(file)
            )
            XCTAssertFalse(err.code.isEmpty, file)
        }
    }

    func testGeneratedClientEndToEndAgainstContractFixtureFiles() async throws {
        let loginData = try ContractFixtureLoader.loadAuthFixture("login-200.json")
        let meData = try ContractFixtureLoader.loadApiFixture("me-200.json")
        let profileData = try ContractFixtureLoader.loadApiFixture("me-profile-200.json")

        let (sessionStore, _, transport, _) = SessionTestFactory.makeStack { request in
            if request.path.hasSuffix("/api/v1/auth/mobile/login") {
                return MockClientTransport.jsonResponse(statusCode: 200, data: loginData)
            }
            if request.path.hasSuffix("/api/v1/me") {
                XCTAssertEqual(request.header(.authorization), "Bearer fixture-access-token")
                return MockClientTransport.jsonResponse(statusCode: 200, data: meData)
            }
            if request.path.hasSuffix("/api/v1/me/profile") {
                XCTAssertEqual(request.header(.authorization), "Bearer fixture-access-token")
                return MockClientTransport.jsonResponse(statusCode: 200, data: profileData)
            }
            return MockClientTransport.emptyResponse(statusCode: 404)
        }

        try await sessionStore.login(identifier: "student.demo", password: "Password123!")
        let me = try await sessionStore.apiClient.me()
        let profile = try await sessionStore.apiClient.getMyProfile()

        XCTAssertEqual(me.username, "student.demo")
        XCTAssertEqual(profile.email, "student.demo@thi360.vn")
        XCTAssertEqual(transport.recordedRequests.count, 3)
    }

    func testAppearanceContractFixtureDecodesAndMatchesBuiltInDefaultPalette() throws {
        let appearanceData = try ContractFixtureLoader.loadData(relativePath: "appearance/tokens-thi360-green.json")
        let payload = try XCTUnwrap(AppearanceTokensPayload.decodeValidated(from: appearanceData))
        XCTAssertEqual(payload.version, 0)
        XCTAssertEqual(payload.mode, .light)
        XCTAssertEqual(payload.presetId, "thi360-green")
        XCTAssertTrue(payload.light.equalsIgnoringHexCase(BrandPalette.defaultLight))
        XCTAssertTrue(payload.dark.equalsIgnoringHexCase(BrandPalette.defaultDark))
    }
}
