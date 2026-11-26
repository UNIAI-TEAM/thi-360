import XCTest
@testable import Thi360

final class BundleInfoTests: XCTestCase {
    func testBundleIdentityAndMinimumDeploymentTarget() {
        let bundle = Bundle.main
        XCTAssertEqual(bundle.bundleIdentifier, "vn.unicom.thi360")
        XCTAssertEqual(bundle.object(forInfoDictionaryKey: "CFBundleDisplayName") as? String, "Thi 360")
        XCTAssertEqual(bundle.object(forInfoDictionaryKey: "MinimumOSVersion") as? String, "17.0")
        XCTAssertEqual(bundle.object(forInfoDictionaryKey: "CFBundleDevelopmentRegion") as? String, "vi")

        let localizations = bundle.object(forInfoDictionaryKey: "CFBundleLocalizations") as? [String] ?? []
        XCTAssertTrue(localizations.contains("vi"))
        XCTAssertTrue(localizations.contains("en"))
    }

    func testDefaultBrandPaletteContainsSeventeenWebTokens() {
        let hexRegex = try? NSRegularExpression(pattern: "^#[0-9A-Fa-f]{6}$")
        let palettes = [BrandPalette.defaultLight, BrandPalette.defaultDark]

        for palette in palettes {
            let values = palette.allHexValues
            XCTAssertEqual(values.count, 17)
            for hex in values {
                let range = NSRange(hex.startIndex..., in: hex)
                XCTAssertNotNil(hexRegex?.firstMatch(in: hex, range: range), "Invalid hex token: \(hex)")
            }
        }

        XCTAssertEqual(
            BrandPalette.defaultLight.allHexValues,
            [
                "#047857", "#FFFFFF", "#047857", "#ECFDF5", "#DCFCE7",
                "#F59223", "#1A1A1F", "#F7F8F9", "#FFFFFF", "#F1F2F4",
                "#11181C", "#3A3A42", "#6B6B72", "#E8E8EC", "#D4D4D8",
                "#B91C1C", "#FEF2F2"
            ]
        )
        XCTAssertEqual(
            BrandPalette.defaultDark.allHexValues,
            [
                "#55B38F", "#11181C", "#10B981", "#0A1F19", "#0C1F18",
                "#F59223", "#1A1A1F", "#0F1418", "#171D22", "#1E262C",
                "#E6EAED", "#C5CCD2", "#A9B2BA", "#2A3238", "#3A444C",
                "#F87171", "#2E1517"
            ]
        )
        XCTAssertEqual(BrandPaletteSet.defaultThi360Green.defaultMode, .light)
    }

    @MainActor
    func testAppPreferencesDefaultsAndPersistence() {
        let suiteName = "vn.unicom.thi360.tests.prefs.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        defer { defaults.removePersistentDomain(forName: suiteName) }

        let initial = AppPreferences(userDefaults: defaults)
        XCTAssertEqual(initial.locale, .vi)
        XCTAssertNil(initial.themeMode)
        XCTAssertEqual(initial.effectiveThemeMode(), .light)
        XCTAssertEqual(initial.effectiveThemeMode(serverDefault: .dark), .dark)

        initial.locale = .en
        initial.themeMode = .dark

        let reloaded = AppPreferences(userDefaults: defaults)
        XCTAssertEqual(reloaded.locale, .en)
        XCTAssertEqual(reloaded.themeMode, .dark)
        XCTAssertEqual(reloaded.effectiveThemeMode(serverDefault: .light), .dark)
    }
}
