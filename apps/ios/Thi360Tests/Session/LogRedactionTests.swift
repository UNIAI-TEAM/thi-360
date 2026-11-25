import Foundation
import XCTest
@testable import Thi360

@MainActor
final class LogRedactionTests: XCTestCase {
    func testDemoLogsNeverExposePasswordOrTokensDuringLoginRefreshAndProtectedRequests() async throws {
        let demoEnvironment = AppEnvironment(
            kind: .demo,
            apiBaseURL: URL(string: "https://thi360.zone17.click")!,
            webBaseURL: URL(string: "https://thi360.zone17.click")!
        )
        let isolatedSuite = "vn.unicom.thi360.tests.demo.\(UUID().uuidString)"
        let isolatedKeychainService = "vn.unicom.thi360.tests.demo.kc.\(UUID().uuidString)"
        let demoDefaults = try XCTUnwrap(UserDefaults(suiteName: isolatedSuite))
        defer { demoDefaults.removePersistentDomain(forName: isolatedSuite) }
        let demoPrefs = AppPreferences(userDefaults: demoDefaults)

        let defaultStore = KeychainTokenStore()
        let previousDefaultTokens = try? defaultStore.read()
        let previousFirstRunFlag = UserDefaults.standard.object(forKey: FirstRunKeychainCleaner.defaultFlagKey)
        try? defaultStore.clear()
        defer {
            if let previousDefaultTokens {
                try? defaultStore.write(previousDefaultTokens)
            } else {
                try? defaultStore.clear()
            }
            if let previousFirstRunFlag {
                UserDefaults.standard.set(previousFirstRunFlag, forKey: FirstRunKeychainCleaner.defaultFlagKey)
            } else {
                UserDefaults.standard.removeObject(forKey: FirstRunKeychainCleaner.defaultFlagKey)
            }
        }

        let demoSession = SessionStore.makeForCurrentProcess(
            preferences: demoPrefs,
            arguments: [
                "-uitest-authenticated",
                "-uitest-api-base-url", "http://127.0.0.1:9999",
                "-uitest-keychain-service", isolatedKeychainService,
                "-uitest-defaults-suite", isolatedSuite
            ],
            environment: demoEnvironment
        )
        XCTAssertEqual(demoSession.apiClient.serverURL, demoEnvironment.apiBaseURL)
        XCTAssertEqual((demoSession.tokenStore as? KeychainTokenStore)?.service, KeychainTokenStore.defaultService)
        XCTAssertEqual(demoSession.state, .signedOut(reason: .none), "Demo must ignore -uitest-authenticated")

        #if THI360_DEMO
        XCTAssertEqual(AppEnvironment.current.kind, .demo)
        XCTAssertEqual(AppEnvironment.current.apiBaseURL, demoEnvironment.apiBaseURL)
        #endif

        let secretPassword = "DemoSecretPassword!9941"
        let initialAccess = "demo-secret-access-token-8841"
        let initialRefresh = "demo-secret-refresh-token-7732"
        let refreshedAccess = "demo-refreshed-access-token-6623"
        let refreshedRefresh = "demo-refreshed-refresh-token-5514"
        let resetToken = "demo-reset-link-token-4405"

        let loginResponseJSON = """
        {
          "accessToken": "\(initialAccess)",
          "refreshToken": "\(initialRefresh)",
          "accessExpiresIn": 300,
          "refreshExpiresIn": 604800,
          "mustChangePassword": false
        }
        """
        let refreshResponseJSON = """
        {
          "accessToken": "\(refreshedAccess)",
          "refreshToken": "\(refreshedRefresh)",
          "accessExpiresIn": 300,
          "refreshExpiresIn": 604800,
          "mustChangePassword": false
        }
        """
        let unauthorizedFixture = try ContractFixtureLoader.loadApiFixture("me-401-unauthenticated.json")
        let meFixture = try ContractFixtureLoader.loadApiFixture("me-200.json")

        let sink = InMemoryLogSink()
        let (sessionStore, _, _, _) = SessionTestFactory.makeStack(
            environment: .demo,
            logSink: sink
        ) { request in
            if request.path.hasSuffix("/api/v1/auth/mobile/login") {
                return MockClientTransport.jsonResponse(statusCode: 200, data: Data(loginResponseJSON.utf8))
            }
            if request.path.hasSuffix("/api/v1/auth/mobile/refresh") {
                return MockClientTransport.jsonResponse(statusCode: 200, data: Data(refreshResponseJSON.utf8))
            }
            if request.path.hasSuffix("/api/v1/me") {
                if request.header(.authorization) == "Bearer \(refreshedAccess)" {
                    return MockClientTransport.jsonResponse(statusCode: 200, data: meFixture)
                }
                return MockClientTransport.jsonResponse(statusCode: 401, data: unauthorizedFixture)
            }
            if request.path.hasSuffix("/api/v1/auth/mobile/logout") {
                return MockClientTransport.emptyResponse(statusCode: 204)
            }
            return MockClientTransport.emptyResponse(statusCode: 500)
        }

        try await sessionStore.login(identifier: "student.demo", password: secretPassword)
        let me = try await sessionStore.apiClient.me()
        XCTAssertEqual(me.username, "student.demo")
        await sessionStore.signOut()

        let logger = RedactingAppLogger(environment: .demo, sink: sink)
        logger.log(.info, category: "auth", message: #"{"password":"\#(secretPassword)","accessToken":"\#(initialAccess)","refreshToken":"\#(initialRefresh)"}"#)
        logger.log(.info, category: "auth", message: "Authorization: Bearer \(refreshedAccess)")
        logger.logHTTP(
            method: "GET",
            path: "/reset-password?token=\(resetToken)",
            statusCode: 200,
            headers: [("Authorization", "Bearer \(refreshedAccess)")]
        )

        let tokensDescription = String(
            describing: SessionTokens(
                accessToken: initialAccess,
                refreshToken: initialRefresh,
                accessExpiresIn: 300,
                refreshExpiresIn: 604_800,
                mustChangePassword: false
            )
        )
        logger.log(.info, category: "session", message: tokensDescription)

        let entries = sink.recordedEntries
        XCTAssertFalse(entries.isEmpty, "Expected network and session logs to be recorded")

        let secrets = [secretPassword, initialAccess, initialRefresh, refreshedAccess, refreshedRefresh, resetToken]
        for entry in entries {
            for secret in secrets {
                XCTAssertFalse(
                    entry.message.contains(secret),
                    "Demo log leaked sensitive value '\(secret)' in entry: \(entry.message)"
                )
            }
        }
    }
}
