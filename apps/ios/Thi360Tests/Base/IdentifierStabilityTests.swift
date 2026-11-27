import Foundation
import XCTest
@testable import Thi360

final class IdentifierStabilityTests: XCTestCase {
    func testBundleIdKeychainIdentifiersAndRenamedStringKeysMatchBaseSha() throws {
        XCTAssertEqual(Bundle.main.bundleIdentifier, "vn.unicom.thi360")

        let iosRoot = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
        let baseConfig = try String(
            contentsOf: iosRoot.appendingPathComponent("Config/Base.xcconfig"),
            encoding: .utf8
        )
        XCTAssertTrue(baseConfig.contains("PRODUCT_BUNDLE_IDENTIFIER = vn.unicom.thi360"))

        XCTAssertEqual(KeychainTokenStore.defaultService, "vn.unicom.thi360.session")
        XCTAssertEqual(KeychainTokenStore.defaultAccount, "mobile-session-tokens")
        XCTAssertEqual(FirstRunKeychainCleaner.defaultFlagKey, "thi360.storage.hasCompletedFirstRun")

        let expectedPreservedKeys: [(table: String, keys: [String])] = [
            ("Shell", ["shell.app.name"]),
            ("Account", [
                "account.biometric.reasonEnable",
                "account.biometric.reasonUnlock",
                "account.logout.confirmTitle",
                "biometric.enable.subtitleFaceID",
                "biometric.enable.subtitleTouchID",
                "lock.privacyShield.label"
            ]),
            ("Recovery", ["recovery.register.termsLabel", "recovery.register.termsRequired"])
        ]

        for (table, keys) in expectedPreservedKeys {
            for key in keys {
                let vi = AppStrings.text(key, table: table, locale: .vi)
                let en = AppStrings.text(key, table: table, locale: .en)
                XCTAssertNotEqual(vi, key, "Missing vi string for \(table)::\(key)")
                XCTAssertNotEqual(en, key, "Missing en string for \(table)::\(key)")
                XCTAssertFalse(vi.contains("Thi 360"), "\(table)::\(key) vi must not contain 'Thi 360'")
                XCTAssertFalse(en.contains("Thi 360"), "\(table)::\(key) en must not contain 'Thi 360'")
            }
        }
    }
}
