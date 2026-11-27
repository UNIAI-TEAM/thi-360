import Foundation
import XCTest
@testable import Thi360

@MainActor
final class ResultFormatTests: XCTestCase {
    func testTimestampAndDurationAlwaysUseHoChiMinhTimezoneRegardlessOfDeviceTimezone() throws {
        let previousTZ = NSTimeZone.default
        NSTimeZone.default = TimeZone(secondsFromGMT: -5 * 3600)!
        defer { NSTimeZone.default = previousTZ }

        let iso = ISO8601DateFormatter()
        let start = try XCTUnwrap(iso.date(from: "2026-11-26T01:05:00Z"))
        let end = try XCTUnwrap(iso.date(from: "2026-11-26T02:20:30Z"))

        XCTAssertEqual(ResultFormat.formatAttemptDateTime(start), "08:05, 26/11/2026")
        XCTAssertEqual(ResultFormat.formatAttemptDateTime(end), "09:20, 26/11/2026")
        XCTAssertEqual(
            ResultFormat.formatDuration(startedAt: start, finishedAt: end, locale: .vi),
            "1 giờ 15 phút"
        )
        XCTAssertEqual(
            ResultFormat.formatDuration(startedAt: start, finishedAt: end, locale: .en),
            "1 hr 15 min"
        )

        XCTAssertEqual(
            ResultFormat.formatDuration(startedAt: start, finishedAt: start, locale: .vi),
            "0 giây"
        )
        XCTAssertEqual(
            ResultFormat.formatDuration(startedAt: start, finishedAt: start.addingTimeInterval(45), locale: .vi),
            "45 giây"
        )
        XCTAssertEqual(
            ResultFormat.formatDuration(startedAt: start, finishedAt: start.addingTimeInterval(45), locale: .en),
            "45 sec"
        )
        XCTAssertEqual(
            ResultFormat.formatDuration(startedAt: start, finishedAt: start.addingTimeInterval(15 * 60 + 20), locale: .vi),
            "15 phút"
        )
        XCTAssertEqual(
            ResultFormat.formatDuration(startedAt: start, finishedAt: nil, locale: .vi),
            "—"
        )
        XCTAssertEqual(
            ResultFormat.formatDuration(startedAt: nil, finishedAt: end, locale: .vi),
            "—"
        )
    }

    func testLocaleScoreFormattingAndFallbacksIncludingZeroAndA6MissingScaleMax() throws {
        XCTAssertEqual(ResultFormat.formatScore(7.5, locale: .vi), "7,5")
        XCTAssertEqual(ResultFormat.formatScore(7.5, locale: .en), "7.5")
        XCTAssertEqual(ResultFormat.formatScore(7.50, locale: .vi), "7,5")
        XCTAssertEqual(ResultFormat.formatScore(7.00, locale: .vi), "7")
        XCTAssertEqual(ResultFormat.formatScore(7.567, locale: .vi), "7,57")
        XCTAssertEqual(ResultFormat.formatScore(7.567, locale: .en), "7.57")
        XCTAssertEqual(ResultFormat.formatScore(0, locale: .vi), "0")
        XCTAssertEqual(ResultFormat.formatScore(1200, locale: .vi), "1.200")
        XCTAssertEqual(ResultFormat.formatScore(1200, locale: .en), "1,200")

        // BE-1 A6 addendum: SCORED always computes non-null scaleMax on live backend;
        // remove scaleMax from certified attempts-result-200-scored.json in test code to verify fallback to rawMaxScore.
        let noScaleMax = try ResultTestFixtures.decodeResult(variant: "scored") { root in
            root.removeValue(forKey: "scaleMax")
            root["rawMaxScore"] = 10.0
            root["displayScore"] = 7.5
        }
        let resolvedNoScaleMax = try XCTUnwrap(ResultFormat.resolveDisplayScore(for: noScaleMax))
        XCTAssertEqual(resolvedNoScaleMax.score, 7.5)
        XCTAssertEqual(resolvedNoScaleMax.max, 10.0)

        let rawScoreOnly = try ResultTestFixtures.decodeResult(variant: "scored") { root in
            root.removeValue(forKey: "displayScore")
            root["rawScore"] = 4.25
            root.removeValue(forKey: "scaleMax")
            root.removeValue(forKey: "rawMaxScore")
        }
        let resolvedRawOnly = try XCTUnwrap(ResultFormat.resolveDisplayScore(for: rawScoreOnly))
        XCTAssertEqual(resolvedRawOnly.score, 4.25)
        XCTAssertNil(resolvedRawOnly.max)

        let zeroScore = try ResultTestFixtures.decodeResult(variant: "zero-score")
        let resolvedZero = try XCTUnwrap(ResultFormat.resolveDisplayScore(for: zeroScore))
        XCTAssertEqual(resolvedZero.score, 0.0)
        XCTAssertEqual(ResultFormat.formatScore(resolvedZero.score, locale: .vi), "0")

        let pendingNoRegrade = try ResultTestFixtures.decodeResult(variant: "pending-review")
        XCTAssertNil(ResultFormat.resolveDisplayScore(for: pendingNoRegrade))

        let regrading = try ResultTestFixtures.decodeResult(variant: "regrading")
        XCTAssertNotNil(ResultFormat.resolveDisplayScore(for: regrading))
    }
}
