import Foundation
import XCTest
@testable import Thi360

final class CameraUsageDescriptionTests: XCTestCase {
    func testCameraUsageDescriptionPresentInBundleInfoPlistsAndLocalizedInViAndEn() throws {
        let expectedVi = "Thi360 dùng máy ảnh để chụp ảnh đại diện của bạn."
        let expectedEn = "Thi360 uses the camera to take your profile picture."
        let bundleValue = Bundle.main.object(forInfoDictionaryKey: "NSCameraUsageDescription") as? String
        XCTAssertTrue(
            bundleValue == expectedVi || bundleValue == expectedEn,
            "Bundle NSCameraUsageDescription must be localized vi or en copy, got: \(String(describing: bundleValue))"
        )

        let resourcesDir = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("Thi360/Resources", isDirectory: true)

        for plistName in ["Info-Dev.plist", "Info-Demo.plist"] {
            let plistData = try Data(contentsOf: resourcesDir.appendingPathComponent(plistName))
            let dict = try XCTUnwrap(
                PropertyListSerialization.propertyList(from: plistData, format: nil) as? [String: Any]
            )
            XCTAssertEqual(
                dict["NSCameraUsageDescription"] as? String,
                "Thi360 dùng máy ảnh để chụp ảnh đại diện của bạn.",
                "\(plistName) must declare NSCameraUsageDescription with Thi360 copy (A-68)"
            )
            XCTAssertNil(
                dict["NSPhotoLibraryUsageDescription"],
                "\(plistName) must not request Photo Library permission because PhotosPicker is used"
            )
        }

        let catalogData = try Data(contentsOf: resourcesDir.appendingPathComponent("InfoPlist.xcstrings"))
        let json = try XCTUnwrap(JSONSerialization.jsonObject(with: catalogData) as? [String: Any])
        let strings = try XCTUnwrap(json["strings"] as? [String: Any])
        let entry = try XCTUnwrap(strings["NSCameraUsageDescription"] as? [String: Any])
        let localizations = try XCTUnwrap(entry["localizations"] as? [String: Any])
        let vi = try XCTUnwrap((localizations["vi"] as? [String: Any])?["stringUnit"] as? [String: Any])
        let en = try XCTUnwrap((localizations["en"] as? [String: Any])?["stringUnit"] as? [String: Any])

        XCTAssertEqual(vi["value"] as? String, "Thi360 dùng máy ảnh để chụp ảnh đại diện của bạn.")
        XCTAssertEqual(en["value"] as? String, "Thi360 uses the camera to take your profile picture.")
    }
}
