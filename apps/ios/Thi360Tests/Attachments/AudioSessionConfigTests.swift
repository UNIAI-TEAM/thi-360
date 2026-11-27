import Foundation
import XCTest

final class AudioSessionConfigTests: XCTestCase {
    private static var iosRootURL: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
    }

    func testInfoPlistsDoNotEnableBackgroundAudioMode() throws {
        let plistPaths = [
            "Thi360/Resources/Info-Dev.plist",
            "Thi360/Resources/Info-Demo.plist"
        ]

        for relativePath in plistPaths {
            let url = Self.iosRootURL.appendingPathComponent(relativePath)
            let data = try Data(contentsOf: url)
            let plist = try XCTUnwrap(
                PropertyListSerialization.propertyList(from: data, format: nil) as? [String: Any],
                "Failed to parse \(relativePath)"
            )
            let modes = plist["UIBackgroundModes"] as? [String] ?? []
            XCTAssertFalse(
                modes.contains("audio"),
                "\(relativePath) MUST NOT declare UIBackgroundModes containing 'audio' (AC-21)"
            )
        }

        let runtimeModes = Bundle.main.object(forInfoDictionaryKey: "UIBackgroundModes") as? [String] ?? []
        XCTAssertFalse(
            runtimeModes.contains("audio"),
            "Runtime Bundle.main Info.plist MUST NOT declare UIBackgroundModes containing 'audio'"
        )
    }
}
