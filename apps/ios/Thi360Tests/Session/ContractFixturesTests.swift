import Foundation
import HTTPTypes
import OpenAPIRuntime
import XCTest
@testable import Thi360

@MainActor
final class ContractFixturesTests: XCTestCase {
    private static let generatedOperationIds: Set<String> = [
        Operations.changePassword.id,
        Operations.mobileLogin.id,
        Operations.mobileLogout.id,
        Operations.mobileRefresh.id,
        Operations.mobileVerifyRegistration.id,
        Operations.forgotPassword.id,
        Operations.resetPasswordByToken.id,
        Operations.startRegistration.id,
        Operations.resendRegistrationCode.id,
        Operations.getAvatar.id,
        Operations.getOptions.id,
        Operations.me.id,
        Operations.listHistory.id,
        Operations.start.id,
        Operations.getState.id,
        Operations.saveAnswers.id,
        Operations.downloadAttemptAsset.id,
        Operations.openAudioPlay.id,
        Operations.endAudioPlay.id,
        Operations.updateAudioProgress.id,
        Operations.resumeAudioPlay.id,
        Operations.heartbeat.id,
        Operations.confirmPartChoice.id,
        Operations.finishPart.id,
        Operations.retryPartGeneration.id,
        Operations.getResult.id,
        Operations.getReview.id,
        Operations.claimSession.id,
        Operations.downloadSolutionAsset.id,
        Operations.submit.id,
        Operations.replaceMyAvatar.id,
        Operations.removeMyAvatar.id,
        Operations.requestChange.id,
        Operations.confirmChange.id,
        Operations.sendVerification.id,
        Operations.verify.id,
        Operations.prepare.id,
        Operations.queryMyExamAccess.id,
        Operations.listMyExams.id,
        Operations.listMyOrders.id,
        Operations.createMyOrder.id,
        Operations.getMyOrder.id,
        Operations.cancelMyOrder.id,
        Operations.payMyOrder.id,
        Operations.quoteMyPrice.id,
        Operations.getMyProfile.id,
        Operations.updateMyProfile.id,
        Operations.verifyVnpayReturn.id,
        Operations.getStorefrontFilters.id,
        Operations.listStorefrontProducts.id,
        Operations.getStorefrontProduct.id,
        Operations.checkMyAccountDeletion.id,
        Operations.deleteMyAccount.id
    ]

    private static let requiredPhase1Paths: Set<String> = [
        "/api/v1/auth/change-password",
        "/api/v1/auth/mobile/login",
        "/api/v1/auth/mobile/logout",
        "/api/v1/auth/mobile/refresh",
        "/api/v1/auth/mobile/register/verify",
        "/api/v1/auth/password/forgot",
        "/api/v1/auth/password/reset",
        "/api/v1/auth/register",
        "/api/v1/auth/register/resend",
        "/api/v1/me",
        "/api/v1/me/profile"
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
        XCTAssertGreaterThanOrEqual(specOperations.count, 51, "OpenAPI mobile contract must include all 51 operations")

        let specPaths = Set(specOperations.map(\.path))
        let missingPhase1 = Self.requiredPhase1Paths.subtracting(specPaths).sorted()
        XCTAssertTrue(missingPhase1.isEmpty, "OpenAPI contract missing phase-1 paths: \(missingPhase1.joined(separator: ", "))")

        var missing: [String] = []
        for op in specOperations where !Self.generatedOperationIds.contains(op.operationId) {
            missing.append("\(op.method) \(op.path) (\(op.operationId))")
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

        let errors = Self.validateIndexCoverage(
            entries: indexEntries,
            apiFolderFiles: apiFiles,
            validOperationIds: validOpIds,
            contractsMobileURL: ContractFixtureLoader.contractsMobileURL
        )
        XCTAssertTrue(errors.isEmpty, errors.joined(separator: "\n"))

        let unindexedErrors = Self.validateIndexCoverage(
            entries: indexEntries,
            apiFolderFiles: apiFiles + ["orphan-unindexed-fixture.json"],
            validOperationIds: validOpIds,
            contractsMobileURL: ContractFixtureLoader.contractsMobileURL
        )
        XCTAssertTrue(
            unindexedErrors.contains { $0.contains("orphan-unindexed-fixture.json") },
            "Validator must fail with filename when folder contains unindexed fixture"
        )

        let bogusEntry = ContractFixtureIndexEntry(
            file: "api/me-200.json",
            operationId: "nonExistentOperationId",
            method: "GET",
            path: "/api/v1/unknown",
            status: 200,
            schema: nil,
            description: nil
        )
        let unknownOpErrors = Self.validateIndexCoverage(
            entries: indexEntries + [bogusEntry],
            apiFolderFiles: apiFiles,
            validOperationIds: validOpIds,
            contractsMobileURL: ContractFixtureLoader.contractsMobileURL
        )
        XCTAssertTrue(
            unknownOpErrors.contains { $0.contains("api/me-200.json") && $0.contains("nonExistentOperationId") },
            "Validator must fail with filename when index points to an unknown OpenAPI operation"
        )
    }

    func testEveryIndexedFixtureDecodes() throws {
        let indexEntries = try ContractFakeTransport.loadIndex()
        XCTAssertGreaterThanOrEqual(indexEntries.count, 100)
        let decoder = MobileAPIClient.makeJSONDecoder()
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
        let decoder = MobileAPIClient.makeJSONDecoder()

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
        let myExamsData = try ContractFixtureLoader.loadApiFixture("my-exams-list-200.json")
        let start202Data = try ContractFixtureLoader.loadApiFixture("attempts-start-202-starting.json")
        let order201Data = try ContractFixtureLoader.loadApiFixture("orders-create-201-redirect.json")

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
            transport.register(method: .get, path: "/api/v1/me/exams") { request in
                XCTAssertEqual(request.header(.authorization), "Bearer fixture-access-token")
                return ContractFakeTransport.jsonResponse(statusCode: 200, data: myExamsData)
            }
            transport.register(method: .post, path: "/api/v1/me/attempts") { request in
                XCTAssertEqual(request.header(.authorization), "Bearer fixture-access-token")
                return ContractFakeTransport.jsonResponse(statusCode: 202, data: start202Data)
            }
            transport.register(method: .post, path: "/api/v1/me/orders") { request in
                XCTAssertEqual(request.header(.authorization), "Bearer fixture-access-token")
                return ContractFakeTransport.jsonResponse(statusCode: 201, data: order201Data)
            }
        }

        try await sessionStore.login(identifier: "student.demo", password: "Password123!")
        let me = try await sessionStore.apiClient.me()
        let profile = try await sessionStore.apiClient.getMyProfile()

        let examsOutput = try await sessionStore.apiClient.authenticatedClient.listMyExams()
        guard case let .ok(examsResponse) = examsOutput else {
            return XCTFail("Expected 200 for listMyExams, got \(examsOutput)")
        }
        let examsPayload = try examsResponse.body.json
        XCTAssertFalse(examsPayload.items.isEmpty)

        let startOutput = try await sessionStore.apiClient.authenticatedClient.start(
            headers: .init(Idempotency_hyphen_Key: "idem-start-1"),
            body: .json(.init(entitlementId: "00000000-0000-7000-8000-000000000001"))
        )
        guard case let .accepted(startResponse) = startOutput else {
            return XCTFail("Expected 202 for start, got \(startOutput)")
        }
        let started = try startResponse.body.json
        XCTAssertFalse(started.attemptId.isEmpty)

        let orderOutput = try await sessionStore.apiClient.authenticatedClient.createMyOrder(
            headers: .init(Idempotency_hyphen_Key: "idem-order-1"),
            body: .json(.init(
                code: "EXAM-DEMO-01",
                expectedAccountId: "019320a0-0000-7000-8000-000000000001",
                expectedAmountDue: 200_000,
                expectedVersionNo: 1
            ))
        )
        guard case let .created(orderResponse) = orderOutput else {
            return XCTFail("Expected 201 for createMyOrder, got \(orderOutput)")
        }
        let createdOrder = try orderResponse.body.json
        XCTAssertFalse(createdOrder.order.code.isEmpty)

        XCTAssertEqual(me.username, "student.demo")
        XCTAssertEqual(profile.email, "student.demo@thi360.vn")
        XCTAssertEqual(transport.callCount(method: .post, path: "/api/v1/auth/mobile/login"), 1)
        XCTAssertGreaterThanOrEqual(transport.callCount(method: .get, path: "/api/v1/me"), 1)
        XCTAssertEqual(transport.callCount(method: .get, path: "/api/v1/me/profile"), 1)
        XCTAssertEqual(transport.callCount(method: .get, path: "/api/v1/me/exams"), 1)
        XCTAssertEqual(transport.callCount(method: .post, path: "/api/v1/me/attempts"), 1)
        XCTAssertEqual(transport.callCount(method: .post, path: "/api/v1/me/orders"), 1)
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

    private static func validateIndexCoverage(
        entries: [ContractFixtureIndexEntry],
        apiFolderFiles: [String],
        validOperationIds: Set<String>,
        contractsMobileURL: URL
    ) -> [String] {
        var errors: [String] = []
        var indexedApiFiles = Set<String>()
        let apiDir = contractsMobileURL.appendingPathComponent("api", isDirectory: true)

        for entry in entries {
            if entry.file.hasPrefix("api/") {
                indexedApiFiles.insert(String(entry.file.dropFirst(4)))
            } else if !entry.file.contains("/") {
                indexedApiFiles.insert(entry.file)
            }

            let diskURL = entry.file.contains("/")
                ? contractsMobileURL.appendingPathComponent(entry.file)
                : apiDir.appendingPathComponent(entry.file)
            if !FileManager.default.fileExists(atPath: diskURL.path) {
                errors.append("Indexed fixture file does not exist on disk: \(entry.file)")
            }
            if !validOperationIds.contains(entry.operationId) {
                errors.append("Indexed fixture \(entry.file) points to unknown OpenAPI operationId: \(entry.operationId)")
            }
        }

        let unindexed = Set(apiFolderFiles).subtracting(indexedApiFiles).sorted()
        for file in unindexed {
            errors.append("Fixture file present in contracts/mobile/api/ but missing from index.json: \(file)")
        }
        return errors
    }

    @inline(never)
    private static func verifyDecodes<T: Decodable>(
        _ type: T.Type,
        from data: Data,
        decoder: JSONDecoder
    ) throws {
        _ = try decoder.decode(type, from: data)
    }

    private static func decodeIndexedFixture(
        entry: ContractFixtureIndexEntry,
        data: Data,
        decoder: JSONDecoder
    ) throws {
        do {
            switch (entry.operationId, entry.status) {
            case (Operations.mobileLogin.id, 200),
                 (Operations.mobileRefresh.id, 200),
                 (Operations.mobileVerifyRegistration.id, 201):
                try verifyDecodes(Components.Schemas.MobileTokenResponse.self, from: data, decoder: decoder)
            case (Operations.me.id, 200):
                try verifyDecodes(Components.Schemas.MeResponse.self, from: data, decoder: decoder)
            case (Operations.getMyProfile.id, 200),
                 (Operations.updateMyProfile.id, 200),
                 (Operations.replaceMyAvatar.id, 200):
                try verifyDecodes(Components.Schemas.ProfileResponse.self, from: data, decoder: decoder)
            case (Operations.startRegistration.id, 200),
                 (Operations.resendRegistrationCode.id, 200):
                try verifyDecodes(Components.Schemas.RegisterStartResponse.self, from: data, decoder: decoder)
            case (Operations.forgotPassword.id, 202):
                try verifyDecodes(Components.Schemas.PasswordForgotResponse.self, from: data, decoder: decoder)
            case (Operations.getOptions.id, 200):
                try verifyDecodes([Components.Schemas.CatalogOptionResponse].self, from: data, decoder: decoder)
            case (Operations.listHistory.id, 200):
                try verifyDecodes(Components.Schemas.PageResponseAttemptHistoryItem.self, from: data, decoder: decoder)
            case (Operations.start.id, 200),
                 (Operations.start.id, 202):
                try verifyDecodes(Components.Schemas.StartAttemptResponse.self, from: data, decoder: decoder)
            case (Operations.getState.id, 200):
                try verifyDecodes(Components.Schemas.AttemptStateResponse.self, from: data, decoder: decoder)
            case (Operations.saveAnswers.id, 200):
                try verifyDecodes(Components.Schemas.SaveAnswersResponse.self, from: data, decoder: decoder)
            case (Operations.openAudioPlay.id, 200),
                 (Operations.endAudioPlay.id, 200),
                 (Operations.updateAudioProgress.id, 200),
                 (Operations.resumeAudioPlay.id, 200):
                try verifyDecodes(Components.Schemas.AttemptAudioPlayResponse.self, from: data, decoder: decoder)
            case (Operations.openAudioPlay.id, 409):
                try verifyDecodes(Components.Schemas.AudioPlayInProgressResponse.self, from: data, decoder: decoder)
            case (Operations.updateAudioProgress.id, 409):
                try verifyDecodes(Components.Schemas.AudioSeekRejectedResponse.self, from: data, decoder: decoder)
            case (Operations.heartbeat.id, 200):
                try verifyDecodes(Components.Schemas.HeartbeatResponse.self, from: data, decoder: decoder)
            case (Operations.confirmPartChoice.id, 200),
                 (Operations.confirmPartChoice.id, 202),
                 (Operations.retryPartGeneration.id, 200),
                 (Operations.retryPartGeneration.id, 202):
                try verifyDecodes(Components.Schemas.PartChoiceResponse.self, from: data, decoder: decoder)
            case (Operations.finishPart.id, 200):
                try verifyDecodes(Components.Schemas.FinishPartResponse.self, from: data, decoder: decoder)
            case (Operations.getResult.id, 200):
                try verifyDecodes(Components.Schemas.AttemptResultResponse.self, from: data, decoder: decoder)
            case (Operations.getReview.id, 200):
                try verifyDecodes(Components.Schemas.AttemptReviewResponse.self, from: data, decoder: decoder)
            case (Operations.claimSession.id, 200):
                try verifyDecodes(Components.Schemas.ClaimSessionResponse.self, from: data, decoder: decoder)
            case (Operations.claimSession.id, 409):
                try verifyDecodes(Components.Schemas.AttemptSessionConflictResponse.self, from: data, decoder: decoder)
            case (Operations.submit.id, 200):
                try verifyDecodes(Components.Schemas.SubmitAttemptResponse.self, from: data, decoder: decoder)
            case (Operations.prepare.id, 200):
                try verifyDecodes(Components.Schemas.AttemptPrepareResponse.self, from: data, decoder: decoder)
            case (Operations.queryMyExamAccess.id, 200):
                try verifyDecodes(Components.Schemas.ExamAccessQueryResponse.self, from: data, decoder: decoder)
            case (Operations.listMyExams.id, 200):
                try verifyDecodes(Components.Schemas.MyExamsResponse.self, from: data, decoder: decoder)
            case (Operations.listMyOrders.id, 200):
                try verifyDecodes(Components.Schemas.PageResponseOrderSummary.self, from: data, decoder: decoder)
            case (Operations.createMyOrder.id, 201):
                try verifyDecodes(Components.Schemas.CreateOrderResponse.self, from: data, decoder: decoder)
            case (Operations.getMyOrder.id, 200),
                 (Operations.cancelMyOrder.id, 200):
                try verifyDecodes(Components.Schemas.OrderDetail.self, from: data, decoder: decoder)
            case (Operations.payMyOrder.id, 200):
                try verifyDecodes(Components.Schemas.PayOrderResponse.self, from: data, decoder: decoder)
            case (Operations.quoteMyPrice.id, 200):
                try verifyDecodes(Components.Schemas.PriceQuote.self, from: data, decoder: decoder)
            case (Operations.verifyVnpayReturn.id, 200):
                try verifyDecodes(Components.Schemas.VnpayReturnResponse.self, from: data, decoder: decoder)
            case (Operations.getStorefrontFilters.id, 200):
                try verifyDecodes(Components.Schemas.StorefrontFilters.self, from: data, decoder: decoder)
            case (Operations.listStorefrontProducts.id, 200):
                try verifyDecodes(Components.Schemas.PageResponseProductCard.self, from: data, decoder: decoder)
            case (Operations.getStorefrontProduct.id, 200):
                try verifyDecodes(Components.Schemas.ProductDetail.self, from: data, decoder: decoder)
            case (Operations.checkMyAccountDeletion.id, 200):
                try verifyDecodes(Components.Schemas.AccountDeletionCheckResponse.self, from: data, decoder: decoder)
            case (Operations.deleteMyAccount.id, 409):
                try verifyDecodes(Components.Schemas.AccountDeletionBlockedResponse.self, from: data, decoder: decoder)
            case (Operations.createMyOrder.id, 409),
                 (Operations.payMyOrder.id, 409),
                 (Operations.cancelMyOrder.id, 409),
                 (Operations.getMyOrder.id, 409),
                 (Operations.listMyOrders.id, 409):
                try verifyDecodes(Components.Schemas.OrderPriceChangedResponse.self, from: data, decoder: decoder)
            case (Operations.createMyOrder.id, 410),
                 (Operations.payMyOrder.id, 410),
                 (Operations.cancelMyOrder.id, 410),
                 (Operations.getMyOrder.id, 410),
                 (Operations.listMyOrders.id, 410),
                 (Operations.quoteMyPrice.id, 410),
                 (Operations.getStorefrontProduct.id, 410):
                try verifyDecodes(Components.Schemas.ProductGoneResponse.self, from: data, decoder: decoder)
            case (Operations.startRegistration.id, 429),
                 (Operations.resendRegistrationCode.id, 422),
                 (Operations.resendRegistrationCode.id, 429),
                 (Operations.mobileVerifyRegistration.id, 422),
                 (Operations.requestChange.id, 429),
                 (Operations.confirmChange.id, 422),
                 (Operations.confirmChange.id, 503),
                 (Operations.sendVerification.id, 429),
                 (Operations.verify.id, 422):
                try verifyDecodes(Components.Schemas.EmailCodeErrorResponse.self, from: data, decoder: decoder)
            case (_, let status) where status >= 400:
                try verifyDecodes(Components.Schemas.ApiError.self, from: data, decoder: decoder)
            default:
                XCTFail("Unmapped contract fixture \(entry.file) for operationId=\(entry.operationId) status=\(entry.status)")
            }
        } catch {
            XCTFail("Failed to decode \(entry.file) (\(entry.operationId) \(entry.status)): \(error)")
        }
    }
}
