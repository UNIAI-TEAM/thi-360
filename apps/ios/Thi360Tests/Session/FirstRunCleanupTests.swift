import Foundation
import XCTest
@testable import Thi360

final class FirstRunCleanupTests: XCTestCase {
    @MainActor
    func testFirstRunAfterReinstallPurgesLeftoverKeychainTokensWhileNormalRelaunchKeepsSession() throws {
        let service = "vn.unicom.thi360.tests.firstrun.\(UUID().uuidString)"
        let suiteName = "vn.unicom.thi360.tests.firstrun.defaults.\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suiteName))
        let store = KeychainTokenStore(service: service)
        defer {
            try? store.clear()
            defaults.removePersistentDomain(forName: suiteName)
        }

        let staleTokens = SessionTokens(
            accessToken: "leftover-access-from-previous-install",
            refreshToken: "leftover-refresh-from-previous-install",
            accessExpiresIn: 300,
            refreshExpiresIn: 604_800,
            mustChangePassword: false,
            username: "previous.user"
        )
        try store.write(staleTokens)
        XCTAssertNotNil(try store.read(), "Precondition: leftover Keychain tokens exist before reinstall launch")

        XCTAssertFalse(defaults.bool(forKey: FirstRunKeychainCleaner.defaultFlagKey))

        let cleaner = FirstRunKeychainCleaner(userDefaults: defaults, tokenStore: store)
        let client = MobileAPIClient(
            serverURL: URL(string: "https://api.thi360.test")!,
            tokenStore: store,
            transport: MockClientTransport { _ in MockClientTransport.emptyResponse(statusCode: 500) },
            localeProvider: { .vi }
        )

        let sessionAfterReinstall = SessionStore(
            tokenStore: store,
            apiClient: client,
            firstRunCleaner: cleaner
        )

        XCTAssertEqual(sessionAfterReinstall.state, .signedOut(reason: .none))
        XCTAssertNil(try store.read(), "First launch after reinstall must purge leftover Keychain items")
        XCTAssertTrue(defaults.bool(forKey: FirstRunKeychainCleaner.defaultFlagKey))

        let freshTokens = SessionTokens(
            accessToken: "fresh-access-after-login",
            refreshToken: "fresh-refresh-after-login",
            accessExpiresIn: 300,
            refreshExpiresIn: 604_800,
            mustChangePassword: false,
            username: "current.user"
        )
        try store.write(freshTokens)

        let sessionAfterNormalRelaunch = SessionStore(
            tokenStore: store,
            apiClient: client,
            firstRunCleaner: cleaner
        )

        XCTAssertEqual(sessionAfterNormalRelaunch.state, .active)
        XCTAssertEqual(try store.read()?.accessToken, "fresh-access-after-login")
        XCTAssertEqual(try store.read()?.refreshToken, "fresh-refresh-after-login")
    }
}
