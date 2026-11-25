import Foundation
import XCTest
@testable import Thi360

final class AuthErrorMessageTests: XCTestCase {
    func testKnownLoginErrorCodesMatchWebStudentMessagesInBothLocales() throws {
        let webMessagesDir = ContractFixtureLoader.repoRootURL
            .appendingPathComponent("apps/web-student/src/messages", isDirectory: true)

        for locale in AppLocale.allCases {
            let fileURL = webMessagesDir.appendingPathComponent("\(locale.rawValue).json")
            let data = try Data(contentsOf: fileURL)
            let json = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
            let loginSection = try XCTUnwrap(json["login"] as? [String: Any])
            let errorsSection = try XCTUnwrap(loginSection["errors"] as? [String: String])

            for apiCode in ["INVALID_CREDENTIALS", "ACCOUNT_INACTIVE", "LOGIN_THROTTLED", "RATE_LIMITED"] {
                let expected = try XCTUnwrap(errorsSection[apiCode], "Missing web message for \(apiCode) in \(locale.rawValue)")
                let actual = AuthErrorMessage.message(forApiCode: apiCode, locale: locale)
                XCTAssertEqual(actual, expected, "Mismatch for \(apiCode) in \(locale.rawValue)")
            }

            let expectedGeneric = try XCTUnwrap(errorsSection["generic"])
            XCTAssertEqual(AuthErrorMessage.message(forApiCode: "UNEXPECTED_BACKEND_CODE", locale: locale), expectedGeneric)
            XCTAssertEqual(AuthErrorMessage.message(forApiCode: nil, locale: locale), expectedGeneric)
            XCTAssertEqual(AuthErrorMessage.message(forApiCode: "", locale: locale), expectedGeneric)
            XCTAssertEqual(
                AuthErrorMessage.message(for: .unexpectedResponse(statusCode: 503), locale: locale),
                expectedGeneric
            )
        }
    }

    func testUnknownErrorCodeNeverExposesRawCodeOrTechnicalMessage() {
        let error = MobileApiError.api(
            statusCode: 500,
            error: ApiError(
                code: "DB_DEADLOCK_INTERNAL",
                message: "org.postgresql.util.PSQLException",
                fieldErrors: [],
                traceId: "trace-999"
            )
        )

        for locale in AppLocale.allCases {
            let rendered = AuthErrorMessage.message(for: error, locale: locale)
            XCTAssertEqual(rendered, AuthErrorMessage.message(for: .generic, locale: locale))
            XCTAssertFalse(rendered.contains("DB_DEADLOCK_INTERNAL"))
            XCTAssertFalse(rendered.contains("PSQLException"))
            XCTAssertFalse(rendered.contains("trace-999"))
        }
    }

    func testNetworkErrorMapsToLocalizedOfflineMessage() {
        let error = MobileApiError.network(.notConnectedToInternet)
        XCTAssertEqual(AuthErrorMessage.code(for: error), .offline)
        XCTAssertEqual(
            AuthErrorMessage.message(for: error, locale: .vi),
            "Không có kết nối mạng. Vui lòng kiểm tra kết nối và thử lại."
        )
        XCTAssertEqual(
            AuthErrorMessage.message(for: error, locale: .en),
            "No internet connection. Please check your connection and try again."
        )
    }

    @MainActor
    func testAcceptLanguageHeaderFollowsAppPreferences() async throws {
        let loginFixture = try ContractFixtureLoader.loadAuthFixture("login-200.json")
        let (sessionStore, _, transport, preferences) = SessionTestFactory.makeStack(locale: .vi) { _ in
            MockClientTransport.jsonResponse(statusCode: 200, data: loginFixture)
        }

        try await sessionStore.login(identifier: "student.demo", password: "Password123!")
        XCTAssertEqual(transport.recordedRequests.last?.header(.acceptLanguage), "vi")

        preferences.locale = .en
        try await sessionStore.login(identifier: "student.demo", password: "Password123!")
        XCTAssertEqual(transport.recordedRequests.last?.header(.acceptLanguage), "en")
    }
}
