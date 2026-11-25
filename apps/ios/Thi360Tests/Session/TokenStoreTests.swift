import Foundation
import Security
import XCTest
@testable import Thi360

final class TokenStoreTests: XCTestCase {
    func testKeychainStoresTokensWithThisDeviceOnlyAndLeavesUserDefaultsAndDocumentsClean() throws {
        let service = "vn.unicom.thi360.tests.keychain.\(UUID().uuidString)"
        let store = KeychainTokenStore(service: service)
        defer { try? store.clear() }

        let accessSentinel = "kc-access-sentinel-\(UUID().uuidString)"
        let refreshSentinel = "kc-refresh-sentinel-\(UUID().uuidString)"
        let initialTokens = SessionTokens(
            accessToken: accessSentinel,
            refreshToken: refreshSentinel,
            accessExpiresIn: 300,
            refreshExpiresIn: 604_800,
            mustChangePassword: false,
            username: "student.keychain"
        )

        XCTAssertNil(try store.read())

        try store.write(initialTokens)

        let loaded = try store.read()
        XCTAssertEqual(loaded?.accessToken, accessSentinel)
        XCTAssertEqual(loaded?.refreshToken, refreshSentinel)
        XCTAssertEqual(loaded?.accessExpiresIn, 300)
        XCTAssertEqual(loaded?.refreshExpiresIn, 604_800)
        XCTAssertEqual(loaded?.mustChangePassword, false)
        XCTAssertEqual(loaded?.username, "student.keychain")

        let accessibility = try store.readItemAccessibility()
        XCTAssertEqual(
            accessibility,
            kSecAttrAccessibleWhenUnlockedThisDeviceOnly as String,
            "Keychain item must use kSecAttrAccessibleWhenUnlockedThisDeviceOnly so tokens never leave this device"
        )

        let standardDefaultsDump = String(describing: UserDefaults.standard.dictionaryRepresentation())
        XCTAssertFalse(standardDefaultsDump.contains(accessSentinel), "Access token leaked into UserDefaults")
        XCTAssertFalse(standardDefaultsDump.contains(refreshSentinel), "Refresh token leaked into UserDefaults")

        if let documentsURL = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first {
            let enumerator = FileManager.default.enumerator(at: documentsURL, includingPropertiesForKeys: nil)
            while let fileURL = enumerator?.nextObject() as? URL {
                if let content = try? Data(contentsOf: fileURL),
                   let text = String(data: content, encoding: .utf8)
                {
                    XCTAssertFalse(text.contains(accessSentinel), "Access token leaked into Documents file \(fileURL.lastPathComponent)")
                    XCTAssertFalse(text.contains(refreshSentinel), "Refresh token leaked into Documents file \(fileURL.lastPathComponent)")
                }
            }
        }

        let updatedTokens = SessionTokens(
            accessToken: "kc-access-updated-\(UUID().uuidString)",
            refreshToken: "kc-refresh-updated-\(UUID().uuidString)",
            accessExpiresIn: 300,
            refreshExpiresIn: 604_800,
            mustChangePassword: true,
            username: "student.keychain"
        )
        try store.write(updatedTokens)
        XCTAssertEqual(try store.read()?.accessToken, updatedTokens.accessToken)
        XCTAssertEqual(try store.read()?.mustChangePassword, true)
        XCTAssertEqual(
            try store.readItemAccessibility(),
            kSecAttrAccessibleWhenUnlockedThisDeviceOnly as String
        )

        try store.clear()
        XCTAssertNil(try store.read())
        XCTAssertNil(try store.readItemAccessibility())
    }
}
