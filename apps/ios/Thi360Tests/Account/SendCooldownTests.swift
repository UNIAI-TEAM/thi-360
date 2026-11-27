import Foundation
import XCTest
@testable import Thi360

final class SendCooldownTests: XCTestCase {
    func testCooldownThresholdsBackgroundElapsedAndHoChiMinhTimezoneFormatting() {
        let baseDate = Date(timeIntervalSince1970: 1_795_804_200)
        let clock = ManualClock(start: baseDate)

        let defaultCooldown = SendCooldown.start(retryAfterSeconds: nil, clock: clock)
        XCTAssertFalse(defaultCooldown.isDailyLimit)
        XCTAssertEqual(defaultCooldown.remainingSeconds(clock: clock), 60)
        XCTAssertEqual(SendCooldown.formatClock(59), "00:59")

        clock.advance(by: 30)
        XCTAssertEqual(defaultCooldown.remainingSeconds(clock: clock), 30)
        XCTAssertEqual(SendCooldown.formatClock(defaultCooldown.remainingSeconds(clock: clock)), "00:30")

        clock.advance(by: 30)
        XCTAssertEqual(defaultCooldown.remainingSeconds(clock: clock), 0)
        XCTAssertEqual(defaultCooldown.resolved(clock: clock), .ready)

        let oneSecond = SendCooldown.start(retryAfterSeconds: 1, clock: clock)
        if case .counting = oneSecond {} else { XCTFail("1s must be .counting") }

        let boundary3599 = SendCooldown.start(retryAfterSeconds: 3_599, clock: clock)
        if case .counting = boundary3599 {} else { XCTFail("3599s must be .counting") }

        let boundary3600 = SendCooldown.start(retryAfterSeconds: 3_600, clock: clock)
        guard case let .dailyLimit(until) = boundary3600 else {
            return XCTFail("3600s must switch to .dailyLimit")
        }

        let originalTZ = NSTimeZone.default
        defer { NSTimeZone.default = originalTZ }

        let utcTZ = TimeZone(identifier: "UTC")!
        let nyTZ = TimeZone(identifier: "America/New_York")!

        NSTimeZone.default = utcTZ
        let formattedFromUTC = SendCooldown.formatDailyLimit(until)

        NSTimeZone.default = nyTZ
        let formattedFromNY = SendCooldown.formatDailyLimit(until)

        XCTAssertEqual(formattedFromUTC, formattedFromNY, "Daily limit timestamp must ignore device timezone")

        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Asia/Ho_Chi_Minh")!
        let comps = calendar.dateComponents([.day, .month, .hour, .minute], from: until)
        let expected = String(format: "%d/%d %02d:%02d", comps.day!, comps.month!, comps.hour!, comps.minute!)
        XCTAssertEqual(formattedFromUTC, expected)
    }
}
