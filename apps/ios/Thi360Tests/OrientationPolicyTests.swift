import UIKit
import XCTest
@testable import Thi360

final class OrientationPolicyTests: XCTestCase {
    func testCompactPhoneIsLockedToPortrait() {
        let mask = OrientationPolicy.supportedOrientations(idiom: .phone, horizontalSizeClass: .compact)
        XCTAssertEqual(mask, .portrait)
        XCTAssertFalse(OrientationPolicy.allowsLandscape(idiom: .phone, horizontalSizeClass: .compact))

        let unspecifiedMask = OrientationPolicy.supportedOrientations(idiom: .phone, horizontalSizeClass: .unspecified)
        XCTAssertEqual(unspecifiedMask, .portrait)
        XCTAssertFalse(OrientationPolicy.allowsLandscape(idiom: .phone, horizontalSizeClass: .unspecified))
    }

    func testRegularPhoneSupportsPortraitAndLandscape() {
        let mask = OrientationPolicy.supportedOrientations(idiom: .phone, horizontalSizeClass: .regular)
        XCTAssertEqual(mask, .allButUpsideDown)
        XCTAssertTrue(mask.contains(.portrait))
        XCTAssertTrue(mask.contains(.landscapeLeft))
        XCTAssertTrue(mask.contains(.landscapeRight))
        XCTAssertTrue(OrientationPolicy.allowsLandscape(idiom: .phone, horizontalSizeClass: .regular))
    }

    func testIPadSupportsAllOrientationsAcrossSizeClasses() {
        XCTAssertEqual(
            OrientationPolicy.supportedOrientations(idiom: .pad, horizontalSizeClass: .regular),
            .all
        )
        XCTAssertTrue(OrientationPolicy.allowsLandscape(idiom: .pad, horizontalSizeClass: .regular))

        XCTAssertEqual(
            OrientationPolicy.supportedOrientations(idiom: .pad, horizontalSizeClass: .compact),
            .all
        )
        XCTAssertTrue(OrientationPolicy.allowsLandscape(idiom: .pad, horizontalSizeClass: .compact))
    }
}
