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

    @MainActor
    func testPortalRoutesKeepPolicy() throws {
        let routes: [AppRoute] = [
            .catalog,
            .product(code: "ielts-01"),
            .checkout(code: "ielts-01", seenVersionNo: 2, seenPrice: 199_000),
            .paymentResult(.byOrder(orderCode: "ORD-1")),
            .orders,
            .order(orderCode: "ORD-1"),
            .myExams,
            .history(resetFilters: false),
            .prepare(entitlementId: "ent-1"),
            .attempt(attemptId: "att-1"),
            .result(attemptId: "att-1"),
            .review(attemptId: "att-1"),
            .profile,
            .profileVerifyEmail,
            .deleteAccount,
            .legal(.terms),
            .legal(.privacy)
        ]

        for route in routes {
            XCTAssertEqual(
                OrientationPolicy.supportedOrientations(idiom: .phone, horizontalSizeClass: .compact),
                .portrait,
                "Compact phone must remain portrait on route \(route.logicalName)"
            )
            XCTAssertEqual(
                OrientationPolicy.supportedOrientations(idiom: .phone, horizontalSizeClass: .regular),
                .allButUpsideDown,
                "Regular phone (iPhone Duo unfolded) must allow portrait and landscape on route \(route.logicalName)"
            )
            XCTAssertEqual(
                OrientationPolicy.supportedOrientations(idiom: .pad, horizontalSizeClass: .regular),
                .all,
                "iPad must allow all orientations on route \(route.logicalName)"
            )
        }

        let routesDir = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("Thi360/Shell/Routes", isDirectory: true)
        let files = try FileManager.default.contentsOfDirectory(at: routesDir, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "swift" }
        XCTAssertFalse(files.isEmpty)
        for file in files {
            let source = try String(contentsOf: file, encoding: .utf8)
            XCTAssertFalse(
                source.contains("requestGeometryUpdate") || source.contains("supportedInterfaceOrientations"),
                "\(file.lastPathComponent) must not override orientation policy"
            )
        }
    }
}
