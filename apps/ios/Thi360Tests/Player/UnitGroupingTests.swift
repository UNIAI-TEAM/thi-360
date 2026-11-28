import Foundation
import XCTest
@testable import Thi360

final class UnitGroupingTests: XCTestCase {
    func testDisplaySegmentsGroupingAndOffsetNormalization() throws {
        // Plan A6 mutation: set displayNo 11..13 and segments 1..2 to verify offset normalization + ungrouped fallback.
        let (attempt, bodies) = try PlayerTestFixtures.decodeAttemptState(variant: "in-progress-shared") { json in
            if var parts = json["parts"] as? [[String: Any]], !parts.isEmpty,
               var items = parts[0]["items"] as? [[String: Any]], items.count >= 3
            {
                items = Array(items.prefix(3))
                items[0]["displayNo"] = 11
                items[1]["displayNo"] = 12
                items[2]["displayNo"] = 13
                parts[0]["items"] = items
                parts[0]["displaySegments"] = [
                    [
                        "code": "SEG_A",
                        "name": "Đoạn A",
                        "fromPosition": 1,
                        "toPosition": 2,
                        "commandCount": 2,
                        "maxPoints": 2.0
                    ]
                ]
                json["parts"] = parts
            }
        }
        let part = try XCTUnwrap(attempt.parts?.first)
        let groups = UnitGrouping.make(part: part, bodies: bodies)
        XCTAssertEqual(groups.count, 2)
        if case let .segment(_, code, name, fromNo, toNo, _, _, isUngrouped) = groups[0].header {
            XCTAssertEqual(code, "SEG_A")
            XCTAssertEqual(name, "Đoạn A")
            XCTAssertEqual(fromNo, 11)
            XCTAssertEqual(toNo, 12)
            XCTAssertFalse(isUngrouped)
        } else {
            XCTFail("Expected segment header")
        }
        if case let .segment(_, _, _, _, _, _, _, isUngrouped) = groups[1].header {
            XCTAssertTrue(isUngrouped)
        } else {
            XCTFail("Expected ungrouped fallback header")
        }
    }

    func testSubjectGroupingSingleVsMultiAndSubjectNamesResolution() throws {
        let (singleAttempt, singleBodies) = try PlayerTestFixtures.decodeAttemptState(variant: "in-progress-shared") { json in
            if var parts = json["parts"] as? [[String: Any]], !parts.isEmpty,
               var items = parts[0]["items"] as? [[String: Any]]
            {
                parts[0]["displaySegments"] = []
                for i in items.indices {
                    items[i]["componentCode"] = "TOAN"
                }
                parts[0]["items"] = items
                json["parts"] = parts
            }
        }
        let singleGroups = UnitGrouping.make(part: try XCTUnwrap(singleAttempt.parts?.first), bodies: singleBodies)
        XCTAssertEqual(singleGroups.count, 1)
        XCTAssertEqual(singleGroups[0].header, .none)

        let (multiAttempt, multiBodies) = try PlayerTestFixtures.decodeAttemptState(variant: "in-progress-shared") { json in
            if var parts = json["parts"] as? [[String: Any]], !parts.isEmpty,
               var items = parts[0]["items"] as? [[String: Any]], items.count >= 3
            {
                parts[0]["displaySegments"] = []
                items[0]["componentCode"] = "LY"
                items[1]["componentCode"] = "LY"
                items[2]["componentCode"] = "HOA"
                parts[0]["items"] = items
                json["parts"] = parts
            }
        }
        let multiGroups = UnitGrouping.make(part: try XCTUnwrap(multiAttempt.parts?.first), bodies: multiBodies)
        XCTAssertGreaterThanOrEqual(multiGroups.count, 2)
        if case let .subject(_, code0, _, _, count0) = multiGroups[0].header {
            XCTAssertEqual(code0, "LY")
            XCTAssertEqual(count0, 2)
        } else {
            XCTFail("Expected LY subject group")
        }
        if case let .subject(_, code1, _, _, _) = multiGroups[1].header {
            XCTAssertEqual(code1, "HOA")
        } else {
            XCTFail("Expected HOA subject group")
        }

        for code in SubjectNames.knownCodes {
            let vi = SubjectNames.resolve(code: code, locale: .vi)
            let en = SubjectNames.resolve(code: code, locale: .en)
            XCTAssertFalse(vi.isEmpty)
            XCTAssertFalse(en.isEmpty)
            XCTAssertNotEqual(vi, "attempt.subjects.codes.\(code)")
            XCTAssertNotEqual(en, "attempt.subjects.codes.\(code)")
        }

        let extra: [String: Components.Schemas.ChoiceSubjectView] = [
            "ROBOTICS": Components.Schemas.ChoiceSubjectView(code: "ROBOTICS", nameEn: "Robotics", nameVi: "Robot học")
        ]
        XCTAssertEqual(SubjectNames.resolve(code: "ROBOTICS", prepareSubjects: extra, locale: .vi), "Robot học")
        XCTAssertEqual(SubjectNames.resolve(code: "ROBOTICS", prepareSubjects: extra, locale: .en), "Robotics")
        XCTAssertEqual(SubjectNames.resolve(code: "UNLISTED_CODE", prepareSubjects: extra, locale: .vi), "UNLISTED_CODE")
    }
}
