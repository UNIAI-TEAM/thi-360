import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class PaletteApplicationTests: XCTestCase {
    func testContractPaletteAndAlternatePaletteApplyBrandAndNeutralColorsInLightAndDark() async throws {
        let greenPayload = try AlternatePaletteFixtures.contractGreenPayload()
        let oceanPayload = try AlternatePaletteFixtures.oceanBluePayload(version: 2, mode: .light)

        // Verify contract fixture brand and neutral tokens match built-in default in both modes.
        XCTAssertTrue(greenPayload.light.equalsIgnoringHexCase(BrandPalette.defaultLight))
        XCTAssertTrue(greenPayload.dark.equalsIgnoringHexCase(BrandPalette.defaultDark))

        // Verify alternate palette preserves exact web neutral tokens while updating brand roles.
        XCTAssertEqual(oceanPayload.light.primaryFill, "#1d4ed8")
        XCTAssertEqual(oceanPayload.light.accent, "#ea580c")
        XCTAssertEqual(oceanPayload.light.bgPage.lowercased(), BrandPalette.defaultLight.bgPage.lowercased())
        XCTAssertEqual(oceanPayload.light.bgSurface.lowercased(), BrandPalette.defaultLight.bgSurface.lowercased())
        XCTAssertEqual(oceanPayload.light.text.lowercased(), BrandPalette.defaultLight.text.lowercased())
        XCTAssertEqual(oceanPayload.light.border.lowercased(), BrandPalette.defaultLight.border.lowercased())

        XCTAssertEqual(oceanPayload.dark.primaryFill, "#60a5fa")
        XCTAssertEqual(oceanPayload.dark.bgPage.lowercased(), BrandPalette.defaultDark.bgPage.lowercased())
        XCTAssertEqual(oceanPayload.dark.bgSurface.lowercased(), BrandPalette.defaultDark.bgSurface.lowercased())
        XCTAssertEqual(oceanPayload.dark.text.lowercased(), BrandPalette.defaultDark.text.lowercased())
        XCTAssertEqual(oceanPayload.dark.border.lowercased(), BrandPalette.defaultDark.border.lowercased())

        // Verify AppearanceRepository applies contract palette and alternate palette dynamically and produces distinct rendered UI snapshots.
        let profileData = try ContractFixtureLoader.loadApiFixture("me-profile-200.json")
        let fetcher = MockAppearanceFetcher { _ in
            .updated(greenPayload, etag: "\"0\"")
        }
        let repo = AppearanceRepository(
            fetcher: fetcher,
            cacheStore: InMemoryAppearanceCacheStore(),
            clock: ManualClock()
        )
        _ = await repo.refreshOnLaunch()
        XCTAssertEqual(repo.current.presetId, "thi360-green")
        XCTAssertTrue(repo.current.palette(for: .light).equalsIgnoringHexCase(greenPayload.light))
        XCTAssertTrue(repo.current.palette(for: .dark).equalsIgnoringHexCase(greenPayload.dark))

        var greenPNGs: [ThemeMode: Data] = [:]
        var oceanPNGs: [ThemeMode: Data] = [:]

        for mode in ThemeMode.allCases {
            greenPNGs[mode] = try await renderPaletteSnapshot(
                repo: repo,
                mode: mode,
                profileData: profileData,
                name: "palette-thi360-green-\(mode.rawValue)"
            )
        }

        fetcher.setHandler { _ in
            .updated(oceanPayload, etag: "\"2\"")
        }
        _ = await repo.refresh()
        XCTAssertEqual(repo.current.presetId, "ocean-blue")
        XCTAssertEqual(repo.currentVersion, 2)
        XCTAssertTrue(repo.current.palette(for: .light).equalsIgnoringHexCase(oceanPayload.light))
        XCTAssertTrue(repo.current.palette(for: .dark).equalsIgnoringHexCase(oceanPayload.dark))

        for mode in ThemeMode.allCases {
            oceanPNGs[mode] = try await renderPaletteSnapshot(
                repo: repo,
                mode: mode,
                profileData: profileData,
                name: "palette-ocean-blue-\(mode.rawValue)"
            )
        }

        XCTAssertNotEqual(greenPNGs[.light], oceanPNGs[.light], "Alternate light palette must visibly change rendered screen pixels")
        XCTAssertNotEqual(greenPNGs[.dark], oceanPNGs[.dark], "Alternate dark palette must visibly change rendered screen pixels")
    }

    private func renderPaletteSnapshot(
        repo: AppearanceRepository,
        mode: ThemeMode,
        profileData: Data,
        name: String
    ) async throws -> Data {
        let stack = AccountTestFactory.makeStack(
            initialTokens: AccountTestFactory.sampleTokens(),
            capability: .faceIDAvailable,
            initialEnrolled: false,
            themeMode: mode
        ) { _ in
            MockClientTransport.jsonResponse(statusCode: 200, data: profileData)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let router = AppRouter(initialDestination: .account)
        let accountVM = router.accountViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager
        )
        await accountVM.loadProfile()

        let rootView = AppRootView(
            router: router,
            preferences: stack.preferences,
            sessionStore: stack.sessionStore,
            appearanceRepository: repo,
            biometricManager: stack.biometricManager,
            idiomOverride: .phone
        )
        let (window, _) = RecoveryViewTestHelper.host(
            rootView,
            size: CGSize(width: 390, height: 844),
            sizeClass: .compact,
            themeMode: mode
        )
        defer { window.isHidden = true }

        return try RecoveryViewTestHelper.captureSnapshot(
            window: window,
            size: CGSize(width: 390, height: 844),
            name: name,
            in: self
        )
    }
}
