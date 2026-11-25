import Foundation
import XCTest
@testable import Thi360

@MainActor
final class ThemeModeTests: XCTestCase {
    func testUnsetUserThemeFollowsServerModeIgnoringSystemAndUserChoiceOverridesAcrossRelaunchAndLogout() async throws {
        let suite = "vn.unicom.thi360.tests.thememode.\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }

        let greenLight = try AlternatePaletteFixtures.contractGreenPayload()
        let oceanDark = try AlternatePaletteFixtures.oceanBluePayload(version: 2, mode: .dark)
        let oceanLight = try AlternatePaletteFixtures.oceanBluePayload(version: 3, mode: .light)

        let fetcher = MockAppearanceFetcher { _ in .updated(greenLight, etag: "\"0\"") }
        let repo = AppearanceRepository(
            fetcher: fetcher,
            cacheStore: InMemoryAppearanceCacheStore(),
            clock: ManualClock()
        )

        let prefs = AppPreferences(userDefaults: defaults)
        XCTAssertNil(prefs.themeMode)

        // 1. User has not chosen theme -> follows server defaultMode (.light), independent of OS scheme.
        _ = await repo.refreshOnLaunch()
        XCTAssertEqual(prefs.effectiveThemeMode(serverDefault: repo.current.defaultMode), .light)
        XCTAssertEqual(prefs.effectiveThemeMode(serverDefault: repo.current.defaultMode).colorScheme, .light)

        // 2. Admin switches server mode to .dark -> unset user theme follows server .dark.
        fetcher.setHandler { _ in .updated(oceanDark, etag: "\"2\"") }
        _ = await repo.refresh()
        XCTAssertEqual(repo.current.defaultMode, .dark)
        XCTAssertEqual(prefs.effectiveThemeMode(serverDefault: repo.current.defaultMode), .dark)
        XCTAssertEqual(prefs.effectiveThemeMode(serverDefault: repo.current.defaultMode).colorScheme, .dark)

        // 3. User explicitly selects .light -> overrides admin .dark mode.
        prefs.themeMode = .light
        XCTAssertEqual(prefs.effectiveThemeMode(serverDefault: repo.current.defaultMode), .light)

        // 4. User switches to .dark, admin flips server back to .light -> user choice .dark wins.
        prefs.themeMode = .dark
        fetcher.setHandler { _ in .updated(oceanLight, etag: "\"3\"") }
        _ = await repo.refresh()
        XCTAssertEqual(repo.current.defaultMode, .light)
        XCTAssertEqual(prefs.effectiveThemeMode(serverDefault: repo.current.defaultMode), .dark)

        // 5. Persists across relaunch and logout.
        let stack = AccountTestFactory.makeStack(
            initialTokens: AccountTestFactory.sampleTokens(),
            themeMode: nil
        ) { request in
            if request.path.hasSuffix("/api/v1/auth/mobile/logout") {
                return MockClientTransport.emptyResponse(statusCode: 204)
            }
            return MockClientTransport.emptyResponse(statusCode: 200)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        stack.preferences.themeMode = .dark
        await stack.sessionStore.signOut(reason: .none)
        XCTAssertEqual(stack.preferences.themeMode, .dark, "Theme choice must survive logout")
        XCTAssertEqual(stack.preferences.effectiveThemeMode(serverDefault: .light), .dark)

        let relaunchedPrefs = AppPreferences(userDefaults: defaults)
        XCTAssertEqual(relaunchedPrefs.themeMode, .dark, "Theme choice must survive app relaunch")
        XCTAssertEqual(relaunchedPrefs.effectiveThemeMode(serverDefault: .light), .dark)
    }
}
