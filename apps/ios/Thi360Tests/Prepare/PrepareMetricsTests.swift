import Foundation
import XCTest
@testable import Thi360

final class PrepareMetricsTests: XCTestCase {
    func testFallbackSumsPartsAndPrefersTopLevelDurationMinutes() throws {
        let shared = try PrepareTestFixtures.loadPrepareResponse(variant: "shared")
        let sharedMetrics = PrepareMetrics.of(shared)
        XCTAssertEqual(sharedMetrics.partCount, 3)
        XCTAssertEqual(sharedMetrics.questionCount, 40)
        XCTAssertEqual(sharedMetrics.durationMinutes, 90)
        XCTAssertEqual(sharedMetrics.maxPoints, Decimal(10))

        let perPart = try PrepareTestFixtures.loadPrepareResponse(variant: "per-part")
        let perPartMetrics = PrepareMetrics.of(perPart)
        XCTAssertEqual(perPartMetrics.partCount, 3)
        XCTAssertEqual(perPartMetrics.questionCount, 37)
        XCTAssertEqual(perPartMetrics.durationMinutes, 15)
        XCTAssertEqual(perPartMetrics.maxPoints, Decimal(37))
    }

    func testTopLevelDurationPriorityOverPartSumAndPartSumWhenTopLevelNull() throws {
        // Plan A6 derivation: mutates only `durationMinutes` on certified `per-part` fixture to verify priority and fallback rules.
        let (_, withTopLevel) = try PrepareTestFixtures.mutatePrepare(variant: "per-part") { root in
            root["durationMinutes"] = 120
        }
        XCTAssertEqual(PrepareMetrics.of(withTopLevel).durationMinutes, 120)

        let (_, withoutTopLevel) = try PrepareTestFixtures.mutatePrepare(variant: "per-part") { root in
            root["durationMinutes"] = NSNull()
        }
        XCTAssertEqual(PrepareMetrics.of(withoutTopLevel).durationMinutes, 15)

        let (_, noDurationsAnywhere) = try PrepareTestFixtures.mutatePrepare(variant: "shared") { root in
            root["durationMinutes"] = NSNull()
        }
        XCTAssertNil(PrepareMetrics.of(noDurationsAnywhere).durationMinutes)
    }

    func testSelectedOptionOverridesMetricsAndFallsBackPerFieldWhenNil() throws {
        let (_, response) = try PrepareTestFixtures.makeSubjectChoicePrepareData(
            preselectedCode: nil,
            chooseSubjects: 2,
            includeSecondFixedOption: true
        )

        let unselected = PrepareMetrics.of(response, selection: .empty)
        XCTAssertEqual(unselected.partCount, 2)
        XCTAssertEqual(unselected.questionCount, 3)
        XCTAssertEqual(unselected.durationMinutes, 0)
        XCTAssertEqual(unselected.maxPoints, Decimal(string: "2.25"))

        let scienceSelected = PrepareMetrics.of(response, selection: PreChoiceSelection(optionCode: "SCIENCE"))
        XCTAssertEqual(scienceSelected.questionCount, 60)
        XCTAssertEqual(scienceSelected.durationMinutes, 75)
        XCTAssertEqual(scienceSelected.maxPoints, Decimal(string: "60.5"))

        let englishSelected = PrepareMetrics.of(response, selection: PreChoiceSelection(optionCode: "ANH"))
        XCTAssertEqual(englishSelected.questionCount, 50)
        XCTAssertEqual(englishSelected.durationMinutes, 60)
        XCTAssertEqual(englishSelected.maxPoints, Decimal(50))

        // Plan A6 derivation: clears `durationMinutes` and `maxPoints` on one option of certified `pre-choice` fixture to test partial fallback.
        let (_, partialOptionResp) = try PrepareTestFixtures.mutatePrepare(variant: "pre-choice") { root in
            root["durationMinutes"] = 45
            var pre = (root["preChoice"] as? [String: Any]) ?? [:]
            var opts = (pre["options"] as? [[String: Any]]) ?? []
            if !opts.isEmpty {
                opts[0]["questionCount"] = 99
                opts[0]["durationMinutes"] = NSNull()
                opts[0]["maxPoints"] = NSNull()
            }
            pre["options"] = opts
            root["preChoice"] = pre
        }
        let partialMetrics = PrepareMetrics.of(partialOptionResp, selection: PreChoiceSelection(optionCode: "CS"))
        XCTAssertEqual(partialMetrics.questionCount, 99)
        XCTAssertEqual(partialMetrics.durationMinutes, 45)
        XCTAssertEqual(partialMetrics.maxPoints, Decimal(string: "2.25"))
    }

    func testFloatingPointRoundingToThreeDecimalsAndFormatting() throws {
        // Plan A6 derivation: sets `parts[*].maxPoints` on certified `shared` fixture to floating-point values (0.1 + 0.2 + 0.3336) matching web metrics.ts rounding tests.
        let (_, resp) = try PrepareTestFixtures.mutatePrepare(variant: "shared") { root in
            var parts = (root["parts"] as? [[String: Any]]) ?? []
            parts[0]["maxPoints"] = 0.1
            parts[1]["maxPoints"] = 0.2
            parts[2]["maxPoints"] = 0.3336
            root["parts"] = parts
        }
        let metrics = PrepareMetrics.of(resp)
        XCTAssertEqual(metrics.maxPoints, Decimal(string: "0.634"))
        XCTAssertEqual(PrepareFormatting.formatPoints(metrics.maxPoints!, locale: .vi), "0,634")
        XCTAssertEqual(PrepareFormatting.formatPoints(metrics.maxPoints!, locale: .en), "0.634")
        XCTAssertEqual(PrepareFormatting.formatPoints(Decimal(10), locale: .vi), "10")
        XCTAssertEqual(PrepareFormatting.formatPoints(Decimal(string: "2.50")!, locale: .vi), "2,5")
    }

    func testAllNilPartMetricsProduceNilTotals() throws {
        // Plan A6 derivation: nulls metric fields on certified `class` fixture to verify empty/nil handling.
        let (_, resp) = try PrepareTestFixtures.mutatePrepare(variant: "class") { root in
            root["durationMinutes"] = NSNull()
            var parts = (root["parts"] as? [[String: Any]]) ?? []
            for idx in parts.indices {
                parts[idx]["questionCount"] = NSNull()
                parts[idx]["durationMinutes"] = NSNull()
                parts[idx]["maxPoints"] = NSNull()
            }
            root["parts"] = parts
        }
        let metrics = PrepareMetrics.of(resp)
        XCTAssertEqual(metrics.partCount, 1)
        XCTAssertNil(metrics.questionCount)
        XCTAssertNil(metrics.durationMinutes)
        XCTAssertNil(metrics.maxPoints)

        let summary = PrepareFormatting.summaryLine(metrics: metrics, locale: .vi)
        XCTAssertEqual(summary, "1 phần")
    }

    func testFormatDayMonthUsesVietnamTimeZone() throws {
        let iso = ISO8601DateFormatter()
        let utcLateEvening = try XCTUnwrap(iso.date(from: "2026-11-20T18:30:00Z"))
        XCTAssertEqual(PrepareFormatting.formatDayMonth(utcLateEvening), "21/11")
    }
}
