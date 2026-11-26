import Foundation
import XCTest
@testable import Thi360

final class BiometricPromptTextTests: XCTestCase {
    func testBiometricReasonsAndFaceIdUsageReferenceThi360InBothLanguages() throws {
        let reasonKeys = [
            "account.biometric.reasonUnlock",
            "account.biometric.reasonEnable"
        ]

        for locale in AppLocale.allCases {
            for key in reasonKeys {
                let resolved = AccountStrings.text(
                    key,
                    locale: locale,
                    replacements: ["biometric": "Face ID"]
                )
                XCTAssertTrue(
                    resolved.contains("Thi360"),
                    "\(key) (\(locale.rawValue)) must contain 'Thi360', got: \(resolved)"
                )
                XCTAssertFalse(
                    resolved.contains("Thi 360"),
                    "\(key) (\(locale.rawValue)) must not contain 'Thi 360', got: \(resolved)"
                )
            }
        }

        let infoPlistCatalogURL = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("Thi360/Resources/InfoPlist.xcstrings")
        let data = try Data(contentsOf: infoPlistCatalogURL)
        let json = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        let strings = try XCTUnwrap(json["strings"] as? [String: Any])
        let entry = try XCTUnwrap(strings["NSFaceIDUsageDescription"] as? [String: Any])
        let localizations = try XCTUnwrap(entry["localizations"] as? [String: Any])

        for lang in ["vi", "en"] {
            let langDict = try XCTUnwrap(localizations[lang] as? [String: Any])
            let unit = try XCTUnwrap(langDict["stringUnit"] as? [String: Any])
            let value = try XCTUnwrap(unit["value"] as? String)
            XCTAssertTrue(value.contains("Thi360"), "InfoPlist.xcstrings \(lang) must contain 'Thi360'")
            XCTAssertFalse(value.contains("Thi 360"), "InfoPlist.xcstrings \(lang) must not contain 'Thi 360'")
        }
    }
}
