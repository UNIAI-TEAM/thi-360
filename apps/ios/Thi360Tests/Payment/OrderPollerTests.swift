import SwiftUI
import XCTest
@testable import Thi360

@MainActor
final class OrderPollerTests: XCTestCase {
    func testPollsEvery2SecondsAndTimesOutAt60Seconds() async {
        let clock = ManualClock()
        var pollTimes: [TimeInterval] = []
        var timedOutCount = 0
        let start = clock.now

        let poller = OrderPoller(
            interval: 2.0,
            window: 60.0,
            clock: clock,
            sleep: { seconds in
                clock.advance(by: seconds)
            }
        )

        let timeoutExpectation = expectation(description: "Poller times out at 60s")
        poller.start(
            onPoll: {
                pollTimes.append(clock.now.timeIntervalSince(start))
                return .continue
            },
            onTimeout: {
                timedOutCount += 1
                timeoutExpectation.fulfill()
            }
        )

        await fulfillment(of: [timeoutExpectation], timeout: 3.0)

        XCTAssertEqual(timedOutCount, 1)
        XCTAssertFalse(poller.isRunning)
        XCTAssertEqual(pollTimes.count, 29, "Polls at 2, 4, ..., 58s before 60s window expires")
        XCTAssertEqual(pollTimes.first, 2.0)
        XCTAssertEqual(pollTimes.last, 58.0)
    }

    func testSingleFlightNeverOverlapsSlowCall() async {
        let clock = ManualClock()
        var inFlight = 0
        var maxConcurrent = 0
        var completedCalls = 0
        let gate = TestAsyncGate()

        let poller = OrderPoller(
            interval: 2.0,
            window: 60.0,
            clock: clock,
            sleep: { seconds in
                clock.advance(by: seconds)
                await Task.yield()
            }
        )

        let doneExpectation = expectation(description: "2 sequential polls completed")
        poller.start(
            onPoll: {
                inFlight += 1
                maxConcurrent = max(maxConcurrent, inFlight)
                if completedCalls == 0 {
                    await gate.enterAndWait()
                }
                clock.advance(by: 3.0)
                inFlight -= 1
                completedCalls += 1
                if completedCalls >= 2 {
                    doneExpectation.fulfill()
                    return .stop
                }
                return .continue
            },
            onTimeout: {}
        )

        await gate.waitUntilEntered()
        XCTAssertEqual(inFlight, 1)
        poller.setScenePhase(.background)
        poller.setScenePhase(.active)
        XCTAssertEqual(maxConcurrent, 1)

        await gate.release()
        await fulfillment(of: [doneExpectation], timeout: 3.0)
        XCTAssertEqual(maxConcurrent, 1)
    }

    func testRestartWindowStartsFresh60Seconds() async {
        let clock = ManualClock()
        var timeouts = 0
        let poller = OrderPoller(
            interval: 2.0,
            window: 60.0,
            clock: clock,
            sleep: { seconds in
                clock.advance(by: seconds)
            }
        )

        let firstTimeout = expectation(description: "First 60s timeout")
        poller.start(
            onPoll: { .continue },
            onTimeout: {
                timeouts += 1
                firstTimeout.fulfill()
            }
        )
        await fulfillment(of: [firstTimeout], timeout: 3.0)
        XCTAssertEqual(timeouts, 1)

        let secondTimeout = expectation(description: "Second 60s timeout after restart")
        poller.restartWindow(
            onPoll: { .continue },
            onTimeout: {
                timeouts += 1
                secondTimeout.fulfill()
            }
        )
        await fulfillment(of: [secondTimeout], timeout: 3.0)
        XCTAssertEqual(timeouts, 2)
    }
}
