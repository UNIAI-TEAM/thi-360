import Foundation
import XCTest
@testable import Thi360

final class PreChoiceSelectionTests: XCTestCase {
    func testInitialSelectionMatchesPreselectedCodeAndSingleOptionFallback() throws {
        let singleMatched = try PrepareTestFixtures.loadPrepareResponse(variant: "pre-choice")
        let initialMatched = PreChoiceSelection.initial(from: singleMatched.preChoice)
        XCTAssertEqual(initialMatched.optionCode, "CS")
        XCTAssertTrue(initialMatched.subjectCodes.isEmpty)

        // Plan A6 derivation: clears `preselectedCode` on certified single-option `pre-choice` fixture.
        let (_, singleNoPreselect) = try PrepareTestFixtures.mutatePrepare(variant: "pre-choice") { root in
            var pre = (root["preChoice"] as? [String: Any]) ?? [:]
            pre["preselectedCode"] = NSNull()
            root["preChoice"] = pre
        }
        XCTAssertEqual(PreChoiceSelection.initial(from: singleNoPreselect.preChoice).optionCode, "CS")

        let (_, twoOptionsNoPreselect) = try PrepareTestFixtures.makeSubjectChoicePrepareData(
            preselectedCode: nil,
            chooseSubjects: 2,
            includeSecondFixedOption: true
        )
        XCTAssertNil(PreChoiceSelection.initial(from: twoOptionsNoPreselect.preChoice).optionCode)
        XCTAssertEqual(
            StartGate.resolve(response: twoOptionsNoPreselect, selection: .empty),
            .needOption
        )

        let (_, twoOptionsPreselected) = try PrepareTestFixtures.makeSubjectChoicePrepareData(
            preselectedCode: "ANH",
            chooseSubjects: 2,
            includeSecondFixedOption: true
        )
        XCTAssertEqual(PreChoiceSelection.initial(from: twoOptionsPreselected.preChoice).optionCode, "ANH")

        let (_, twoOptionsBadPreselect) = try PrepareTestFixtures.makeSubjectChoicePrepareData(
            preselectedCode: "DOES_NOT_EXIST",
            chooseSubjects: 2,
            includeSecondFixedOption: true
        )
        XCTAssertNil(PreChoiceSelection.initial(from: twoOptionsBadPreselect.preChoice).optionCode)

        // Plan A6 derivation: sets `preChoice.options = []` on certified `pre-choice` fixture for 0-options boundary.
        let (_, zeroOptions) = try PrepareTestFixtures.mutatePrepare(variant: "pre-choice") { root in
            var pre = (root["preChoice"] as? [String: Any]) ?? [:]
            pre["options"] = []
            root["preChoice"] = pre
        }
        XCTAssertNil(PreChoiceSelection.initial(from: zeroOptions.preChoice).optionCode)
        XCTAssertEqual(StartGate.resolve(response: zeroOptions, selection: .empty), .noServingOption)
    }

    func testSubjectChoiceBoundariesEnforceMaxCapUncheckAndOptionSwitchReset() throws {
        let (_, resp) = try PrepareTestFixtures.makeSubjectChoicePrepareData(
            preselectedCode: "SCIENCE",
            chooseSubjects: 2,
            includeSecondFixedOption: true
        )
        var selection = PreChoiceSelection.initial(from: resp.preChoice)
        XCTAssertEqual(selection.optionCode, "SCIENCE")
        XCTAssertEqual(StartGate.resolve(response: resp, selection: selection), .needSubjects(count: 2))

        selection = selection.togglingSubject("LY", maxSelect: 2)
        XCTAssertEqual(selection.subjectCodes, ["LY"])
        XCTAssertEqual(StartGate.resolve(response: resp, selection: selection), .needSubjects(count: 2))

        selection = selection.togglingSubject("HOA", maxSelect: 2)
        XCTAssertEqual(selection.subjectCodes, ["LY", "HOA"])
        XCTAssertEqual(StartGate.resolve(response: resp, selection: selection), .open)

        let capped = selection.togglingSubject("SINH", maxSelect: 2)
        XCTAssertEqual(capped.subjectCodes, ["LY", "HOA"], "Cannot select more than N subjects")

        let unchecked = capped.togglingSubject("LY", maxSelect: 2)
        XCTAssertEqual(unchecked.subjectCodes, ["HOA"])
        XCTAssertEqual(StartGate.resolve(response: resp, selection: unchecked), .needSubjects(count: 2))

        let repicked = unchecked.togglingSubject("SINH", maxSelect: 2)
        XCTAssertEqual(repicked.subjectCodes, ["HOA", "SINH"])
        XCTAssertEqual(StartGate.resolve(response: resp, selection: repicked), .open)

        let payloadWithSubjects = repicked.toPreChoiceInput(for: resp)
        XCTAssertEqual(payloadWithSubjects?.optionCode, "SCIENCE")
        XCTAssertEqual(payloadWithSubjects?.subjectCodes, ["HOA", "SINH"])

        let switched = repicked.selectingOption("ANH")
        XCTAssertEqual(switched.optionCode, "ANH")
        XCTAssertTrue(switched.subjectCodes.isEmpty)
        XCTAssertEqual(StartGate.resolve(response: resp, selection: switched), .open)

        let payloadWithoutSubjects = switched.toPreChoiceInput(for: resp)
        XCTAssertEqual(payloadWithoutSubjects?.optionCode, "ANH")
        XCTAssertNil(payloadWithoutSubjects?.subjectCodes, "Fixed option must omit subjectCodes")
    }
}
