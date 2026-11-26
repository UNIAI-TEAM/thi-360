import Foundation
import XCTest
@testable import Thi360

@MainActor
final class ContractFixturesTests: XCTestCase {
    private static let generatedOperationIds: Set<String> = [
        Operations.mobileLogin.id,
        Operations.mobileRefresh.id,
        Operations.mobileLogout.id,
        Operations.mobileVerifyRegistration.id,
        Operations.startRegistration.id,
        Operations.resendRegistrationCode.id,
        Operations.forgotPassword.id,
        Operations.resetPasswordByToken.id,
        Operations.changePassword.id,
        Operations.me.id,
        Operations.getMyProfile.id
    ]

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

    func testGeneratedClientCoversEveryContractPath() throws {
        let specOperations = try Self.loadOpenApiOperations()
        XCTAssertGreaterThanOrEqual(specOperations.count, 11, "OpenAPI mobile contract must declare at least the 11 phase-1 operations")

        var missing: [String] = []
        for op in specOperations {
            if !Self.generatedOperationIds.contains(op.operationId) {
                missing.append("\(op.method) \(op.path) (\(op.operationId))")
            }
        }
        XCTAssertTrue(
            missing.isEmpty,
            "Generated OpenAPI client coverage list is missing contract operations: \(missing.joined(separator: ", "))"
        )
    }

    func testIndexCoversFolder() throws {
        let indexEntries = try ContractFakeTransport.loadIndex()
        XCTAssertFalse(indexEntries.isEmpty, "contracts/mobile/api/index.json must not be empty")

        let specOperations = try Self.loadOpenApiOperations()
        let validOpIds = Set(specOperations.map(\.operationId))

        let apiDir = ContractFixtureLoader.contractsMobileURL.appendingPathComponent("api", isDirectory: true)
        let apiFiles = try FileManager.default.contentsOfDirectory(at: apiDir, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "json" && $0.lastPathComponent != "index.json" }
            .map(\.lastPathComponent)

        var indexedFiles = Set<String>()
        for entry in indexEntries {
            let normalizedFile = entry.file.hasPrefix("api/") ? String(entry.file.dropFirst(4)) : entry.file
            indexedFiles.insert(normalizedFile)

            let diskURL = entry.file.contains("/")
                ? ContractFixtureLoader.contractsMobileURL.appendingPathComponent(entry.file)
                : apiDir.appendingPathComponent(entry.file)
            XCTAssertTrue(
                FileManager.default.fileExists(atPath: diskURL.path),
                "Indexed fixture file does not exist on disk: \(entry.file)"
            )
            XCTAssertTrue(
                validOpIds.contains(entry.operationId),
                "Indexed fixture \(entry.file) points to unknown OpenAPI operationId: \(entry.operationId)"
            )
        }

        let unindexed = Set(apiFiles).subtracting(indexedFiles).sorted()
        XCTAssertTrue(
            unindexed.isEmpty,
            "Fixture files present in contracts/mobile/api/ but missing from index.json: \(unindexed.joined(separator: ", "))"
        )
    }

    func testEveryIndexedFixtureDecodes() throws {
        let indexEntries = try ContractFakeTransport.loadIndex()
        XCTAssertFalse(indexEntries.isEmpty)
        let decoder = JSONDecoder()
        let apiDir = ContractFixtureLoader.contractsMobileURL.appendingPathComponent("api", isDirectory: true)

        for entry in indexEntries {
            let fileURL = entry.file.contains("/")
                ? ContractFixtureLoader.contractsMobileURL.appendingPathComponent(entry.file)
                : apiDir.appendingPathComponent(entry.file)
            let data = try Data(contentsOf: fileURL)

            if entry.status >= 400 {
                let apiError = try decoder.decode(ApiError.self, from: data)
                XCTAssertFalse(apiError.code.isEmpty, "Error fixture \(entry.file) must have non-empty code")
            }

            try Self.decodeIndexedFixture(entry: entry, data: data, decoder: decoder)
        }
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

    func testGeneratedClientEndToEndAgainstContractFixtureFiles() async throws {
        let loginData = try ContractFixtureLoader.loadAuthFixture("login-200.json")
        let meData = try ContractFixtureLoader.loadApiFixture("me-200.json")
        let profileData = try ContractFixtureLoader.loadApiFixture("me-profile-200.json")

        let (sessionStore, _, transport, _) = try SessionTestFactory.makeContractStack { transport in
            transport.registerJSON(method: .post, path: "/api/v1/auth/mobile/login", statusCode: 200, data: loginData)
            transport.register(method: .get, path: "/api/v1/me") { request in
                XCTAssertEqual(request.header(.authorization), "Bearer fixture-access-token")
                return ContractFakeTransport.jsonResponse(statusCode: 200, data: meData)
            }
            transport.register(method: .get, path: "/api/v1/me/profile") { request in
                XCTAssertEqual(request.header(.authorization), "Bearer fixture-access-token")
                return ContractFakeTransport.jsonResponse(statusCode: 200, data: profileData)
            }
        }

        try await sessionStore.login(identifier: "student.demo", password: "Password123!")
        let me = try await sessionStore.apiClient.me()
        let profile = try await sessionStore.apiClient.getMyProfile()

        XCTAssertEqual(me.username, "student.demo")
        XCTAssertEqual(profile.email, "student.demo@thi360.vn")
        XCTAssertEqual(transport.callCount(method: .post, path: "/api/v1/auth/mobile/login"), 1)
        XCTAssertGreaterThanOrEqual(transport.callCount(method: .get, path: "/api/v1/me"), 1)
        XCTAssertEqual(transport.callCount(method: .get, path: "/api/v1/me/profile"), 1)
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

    private struct OpenApiOperationInfo {
        let method: String
        let path: String
        let operationId: String
    }

    private static func loadOpenApiOperations() throws -> [OpenApiOperationInfo] {
        let specURL = ContractFixtureLoader.contractsMobileURL.appendingPathComponent("openapi-mobile.json")
        let data = try Data(contentsOf: specURL)
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        let paths = try XCTUnwrap(root["paths"] as? [String: [String: Any]])
        let httpMethods: Set<String> = ["get", "post", "put", "patch", "delete"]

        var results: [OpenApiOperationInfo] = []
        for (path, pathItem) in paths.sorted(by: { $0.key < $1.key }) {
            for (method, rawOp) in pathItem where httpMethods.contains(method.lowercased()) {
                guard let op = rawOp as? [String: Any],
                      let opId = op["operationId"] as? String
                else { continue }
                results.append(OpenApiOperationInfo(method: method.uppercased(), path: path, operationId: opId))
            }
        }
        return results
    }

    private static func decodeIndexedFixture(
        entry: ContractFixtureIndexEntry,
        data: Data,
        decoder: JSONDecoder
    ) throws {
        switch (entry.operationId, entry.status) {
        case (Operations.mobileLogin.id, 200),
             (Operations.mobileRefresh.id, 200),
             (Operations.mobileVerifyRegistration.id, 201):
            _ = try decoder.decode(Components.Schemas.MobileTokenResponse.self, from: data)
        case (Operations.me.id, 200):
            _ = try decoder.decode(Components.Schemas.MeResponse.self, from: data)
        case (Operations.getMyProfile.id, 200):
            _ = try decoder.decode(Components.Schemas.ProfileResponse.self, from: data)
        case (Operations.startRegistration.id, 200),
             (Operations.resendRegistrationCode.id, 200):
            _ = try decoder.decode(Components.Schemas.RegisterStartResponse.self, from: data)
        case (Operations.forgotPassword.id, 202):
            _ = try decoder.decode(Components.Schemas.PasswordForgotResponse.self, from: data)
        case (Operations.startRegistration.id, 429),
             (Operations.resendRegistrationCode.id, 422),
             (Operations.resendRegistrationCode.id, 429),
             (Operations.mobileVerifyRegistration.id, 422):
            _ = try decoder.decode(Components.Schemas.EmailCodeErrorResponse.self, from: data)
        case (_, let status) where status >= 400:
            _ = try decoder.decode(Components.Schemas.ApiError.self, from: data)
        default:
            XCTFail("Unmapped contract fixture \(entry.file) for operationId=\(entry.operationId) status=\(entry.status)")
        }
    }
}
