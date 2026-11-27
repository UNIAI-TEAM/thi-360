import Foundation
import XCTest
@testable import Thi360

@MainActor
final class TokenStoreUpgradeTests: XCTestCase {
    func testPhaseOneKeychainPayloadWithoutAccountIdDecodesKeepsSessionAndPopulatesAfterMe() async throws {
        let legacyJSON = """
        {
          "accessToken": "legacy-access-token",
          "refreshToken": "legacy-refresh-token",
          "accessExpiresIn": 300,
          "refreshExpiresIn": 604800,
          "mustChangePassword": false,
          "username": "student.demo",
          "issuedAt": "2026-11-26T00:00:00Z"
        }
        """

        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        let decoded = try decoder.decode(SessionTokens.self, from: Data(legacyJSON.utf8))
        XCTAssertEqual(decoded.accessToken, "legacy-access-token")
        XCTAssertEqual(decoded.refreshToken, "legacy-refresh-token")
        XCTAssertEqual(decoded.username, "student.demo")
        XCTAssertNil(decoded.accountId)

        let meData = try ContractFixtureLoader.loadApiFixture("me-200.json")
        let refreshData = try ContractFixtureLoader.loadAuthFixture("refresh-200.json")
        let rootDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("thi360-upgrade-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: rootDir) }
        let storeProvider = AccountScopedStoreProvider(rootDirectoryURL: rootDir)

        let (sessionStore, tokenStore, transport, _) = try SessionTestFactory.makeContractStack(
            initialTokens: decoded,
            storeProvider: storeProvider
        ) { transport in
            transport.registerJSON(method: .post, path: "/api/v1/auth/mobile/refresh", statusCode: 200, data: refreshData)
            transport.registerJSON(method: .get, path: "/api/v1/me", statusCode: 200, data: meData)
        }

        XCTAssertEqual(sessionStore.state, .active, "Upgraded session without accountId must remain signed in")
        XCTAssertNil(sessionStore.currentAccountId)
        XCTAssertNil(sessionStore.currentAccountStore(), "Store must return nil until accountId is resolved")

        await sessionStore.validateSessionOnLaunch()

        XCTAssertEqual(sessionStore.state, .active)
        XCTAssertEqual(sessionStore.currentAccountId, "019320a0-0000-7000-8000-000000000001")
        XCTAssertEqual(try tokenStore.read()?.accountId, "019320a0-0000-7000-8000-000000000001")
        XCTAssertEqual(transport.callCount(method: .get, path: "/api/v1/me"), 1)

        let store = try XCTUnwrap(sessionStore.currentAccountStore())
        try await store.write(Data("ready".utf8), namespace: "drafts", key: "k1")
        let read = await store.read(namespace: "drafts", key: "k1")
        XCTAssertEqual(read, Data("ready".utf8))
    }
}
