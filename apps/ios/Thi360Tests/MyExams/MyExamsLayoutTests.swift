import Foundation
import XCTest
@testable import Thi360

final class MyExamsLayoutTests: XCTestCase {
    func testMixedCheckingBundlesCrossRefsAndStandalone() throws {
        let bundleX = try MyExamsTestFixtures.cloneBundleRefTemplate(bundleId: "bx", code: "BX", name: "Bộ X")
        let bundleY = try MyExamsTestFixtures.cloneBundleRefTemplate(bundleId: "by", code: "BY", name: "Bộ Y")

        let a = try MyExamsTestFixtures.cloneMyExamCardJSON(templateState: "CHECKING") { item in
            item["code"] = "A"
            item["name"] = "Bài A"
            item["pendingOrderCode"] = "DH001"
            item["bundles"] = []
        }
        let b = try MyExamsTestFixtures.cloneMyExamCardJSON(templateState: "CAN_START") { item in
            item["entitlementId"] = "ent-b"
            item["code"] = "B"
            item["name"] = "Bài B"
            item["bundles"] = [bundleX]
        }
        let c = try MyExamsTestFixtures.cloneMyExamCardJSON(templateState: "CAN_START") { item in
            item["entitlementId"] = "ent-c"
            item["code"] = "C"
            item["name"] = "Bài C"
            item["bundles"] = []
        }
        let d = try MyExamsTestFixtures.cloneMyExamCardJSON(templateState: "CAN_START") { item in
            item["entitlementId"] = "ent-d"
            item["code"] = "D"
            item["name"] = "Bài D"
            item["bundles"] = [bundleY]
        }
        let e = try MyExamsTestFixtures.cloneMyExamCardJSON(templateState: "CAN_START") { item in
            item["entitlementId"] = "ent-e"
            item["code"] = "E"
            item["name"] = "Bài E"
            item["bundles"] = [bundleX]
        }
        let f = try MyExamsTestFixtures.cloneMyExamCardJSON(templateState: "CAN_START") { item in
            item["entitlementId"] = "ent-f"
            item["code"] = "F"
            item["name"] = "Bài F"
            item["bundles"] = [bundleX, bundleY]
        }

        let payload = try MyExamsTestFixtures.makeMyExamsPayload(items: [a, b, c, d, e, f])
        let cards = try MyExamsTestFixtures.decodeMyExamCards(from: payload)
        let grouped = MyExamsLayout.group(cards)

        XCTAssertEqual(grouped.count, 6)
        XCTAssertEqual(grouped.checking.map(\.code), ["A"])
        XCTAssertEqual(grouped.bundleGroups.map(\.ref.bundleId), ["bx", "by"])
        XCTAssertEqual(grouped.bundleGroups[0].cards.map(\.code), ["B", "E", "F"])
        XCTAssertTrue(grouped.bundleGroups[0].crossRefs.isEmpty)
        XCTAssertEqual(grouped.bundleGroups[1].cards.map(\.code), ["D"])
        XCTAssertEqual(grouped.bundleGroups[1].crossRefs.map(\.examCode), ["F"])
        XCTAssertEqual(grouped.bundleGroups[1].crossRefs.first?.primaryBundle.bundleId, "bx")
        XCTAssertEqual(grouped.bundleGroups[1].totalCount, 2)
        XCTAssertEqual(grouped.standalone.map(\.code), ["C"])
        XCTAssertTrue(grouped.showsStandaloneHeading)
    }

    func testNoBundlesOmitsStandaloneHeading() throws {
        let c1 = try MyExamsTestFixtures.cloneMyExamCardJSON(templateState: "CAN_START") { item in
            item["entitlementId"] = "ent-1"
            item["code"] = "S1"
            item["bundles"] = []
        }
        let c2 = try MyExamsTestFixtures.cloneMyExamCardJSON(templateState: "IN_PROGRESS") { item in
            item["entitlementId"] = "ent-2"
            item["code"] = "S2"
            item["bundles"] = []
        }
        let cards = try MyExamsTestFixtures.decodeMyExamCards(from: try MyExamsTestFixtures.makeMyExamsPayload(items: [c1, c2]))
        let grouped = MyExamsLayout.group(cards)

        XCTAssertTrue(grouped.checking.isEmpty)
        XCTAssertTrue(grouped.bundleGroups.isEmpty)
        XCTAssertEqual(grouped.standalone.map(\.code), ["S1", "S2"])
        XCTAssertFalse(grouped.showsStandaloneHeading)
    }

    func testCheckingOnlyHasNoBundlesOrStandalone() throws {
        let chk = try MyExamsTestFixtures.cloneMyExamCardJSON(templateState: "CHECKING") { item in
            item["code"] = "CHK-1"
            item["bundles"] = []
        }
        let cards = try MyExamsTestFixtures.decodeMyExamCards(from: try MyExamsTestFixtures.makeMyExamsPayload(items: [chk]))
        let grouped = MyExamsLayout.group(cards)

        XCTAssertEqual(grouped.count, 1)
        XCTAssertEqual(grouped.checking.map(\.code), ["CHK-1"])
        XCTAssertTrue(grouped.bundleGroups.isEmpty)
        XCTAssertTrue(grouped.standalone.isEmpty)
        XCTAssertFalse(grouped.showsStandaloneHeading)
    }

    func testCrossRefOnlyBundleGroupPreservesItsOwnBundleRef() throws {
        let bundleX = try MyExamsTestFixtures.cloneBundleRefTemplate(bundleId: "bx", code: "CODE-X", name: "Bộ X")
        let bundleY = try MyExamsTestFixtures.cloneBundleRefTemplate(bundleId: "by", code: "CODE-Y", name: "Bộ Y")

        let f = try MyExamsTestFixtures.cloneMyExamCardJSON(templateState: "CAN_START") { item in
            item["entitlementId"] = "ent-f"
            item["code"] = "F"
            item["name"] = "Bài F"
            item["bundles"] = [bundleX, bundleY]
        }
        let cards = try MyExamsTestFixtures.decodeMyExamCards(from: try MyExamsTestFixtures.makeMyExamsPayload(items: [f]))
        let grouped = MyExamsLayout.group(cards)

        XCTAssertEqual(grouped.bundleGroups.count, 2)
        XCTAssertEqual(grouped.bundleGroups[0].ref.code, "CODE-X")
        XCTAssertEqual(grouped.bundleGroups[0].cards.map(\.code), ["F"])
        XCTAssertEqual(grouped.bundleGroups[1].ref.code, "CODE-Y")
        XCTAssertTrue(grouped.bundleGroups[1].cards.isEmpty)
        XCTAssertEqual(grouped.bundleGroups[1].crossRefs.map(\.examCode), ["F"])
        XCTAssertEqual(grouped.bundleGroups[1].totalCount, 1)
    }
}
