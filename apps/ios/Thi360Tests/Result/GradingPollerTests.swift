import Foundation
import XCTest
@testable import Thi360

final class GradingPollerTests: XCTestCase {
    func testPollsEvery3SecondsAndStopsWhenServerTransitions() async {
        let clock = FakePollClock()
        let poller = GradingPoller(clock: clock, interval: .seconds(3), window: .seconds(120))
        let counter = PollCounter()

        await poller.startIfNeeded(
            isSceneActive: true,
            onPoll: {
                let count = await counter.increment()
                return count < 3
            },
            onTimeout: {
                await counter.markTimeout()
            }
        )
        await clock.settle()

        var calls = await counter.pollCount
        XCTAssertEqual(calls, 0, "Should wait 3s before first scheduled poll")

        await clock.advance(by: .seconds(3))
        calls = await counter.pollCount
        XCTAssertEqual(calls, 1)

        await clock.advance(by: .seconds(3))
        calls = await counter.pollCount
        XCTAssertEqual(calls, 2)

        await clock.advance(by: .seconds(3))
        calls = await counter.pollCount
        XCTAssertEqual(calls, 3)

        await clock.advance(by: .seconds(30))
        calls = await counter.pollCount
        XCTAssertEqual(calls, 3, "Must stop polling after server transitions out of GRADING")
        let phase = await poller.phase
        XCTAssertEqual(phase, .idle)
    }

    func testBackgroundPausesPollingAndForegroundBefore120sPollsImmediatelyUntilOriginalDeadline() async {
        let clock = FakePollClock()
        let poller = GradingPoller(clock: clock, interval: .seconds(3), window: .seconds(120))
        let counter = PollCounter()

        await poller.startIfNeeded(
            isSceneActive: true,
            onPoll: {
                _ = await counter.increment()
                return true
            },
            onTimeout: {
                await counter.markTimeout()
            }
        )
        await clock.waitForWaiter()

        await clock.advance(by: .seconds(3))
        var calls = await counter.pollCount
        XCTAssertEqual(calls, 1)

        await poller.setSceneActive(false)
        let pausedPhase = await poller.phase
        if case .paused = pausedPhase {
        } else {
            XCTFail("Expected paused phase, got \(pausedPhase)")
        }

        await clock.advance(by: .seconds(111))
        calls = await counter.pollCount
        XCTAssertEqual(calls, 1, "Zero polls allowed while app is in background")

        await poller.setSceneActive(true)
        await clock.waitForWaiter()
        calls = await counter.pollCount
        XCTAssertEqual(calls, 2, "Returning to foreground before 120s must poll immediately")

        await clock.advance(by: .seconds(3))
        await clock.waitForWaiter()
        calls = await counter.pollCount
        XCTAssertEqual(calls, 3, "Must resume 3s interval until original 120s deadline (t=117s)")

        await clock.advance(by: .seconds(3))
        await clock.settle()
        calls = await counter.pollCount
        XCTAssertEqual(calls, 3, "Must time out at original 120s deadline (t=120s) without extra poll")
        let timedOut = await counter.didTimeout
        XCTAssertTrue(timedOut)
        let finalPhase = await poller.phase
        XCTAssertEqual(finalPhase, .timedOut)
    }

    func testStopCancelsPollingImmediately() async {
        let clock = FakePollClock()
        let poller = GradingPoller(clock: clock, interval: .seconds(3), window: .seconds(120))
        let counter = PollCounter()

        await poller.startIfNeeded(
            isSceneActive: true,
            onPoll: {
                _ = await counter.increment()
                return true
            },
            onTimeout: {
                await counter.markTimeout()
            }
        )
        await clock.settle()

        await poller.stop()
        await clock.advance(by: .seconds(30))
        let calls = await counter.pollCount
        XCTAssertEqual(calls, 0)
        let phase = await poller.phase
        XCTAssertEqual(phase, .idle)
    }

    func testTimeoutAndReload() async {
        let clock = FakePollClock()
        let poller = GradingPoller(clock: clock, interval: .seconds(3), window: .seconds(120))
        let counter = PollCounter()

        await poller.startIfNeeded(
            isSceneActive: true,
            onPoll: {
                _ = await counter.increment()
                return true
            },
            onTimeout: {
                await counter.markTimeout()
            }
        )
        await clock.waitForWaiter()

        for step in 0..<40 {
            await clock.advance(by: .seconds(3))
            if step < 39 {
                await clock.waitForWaiter()
            }
        }
        await clock.settle()

        let timedOut = await counter.didTimeout
        XCTAssertTrue(timedOut)
        let phaseAfterTimeout = await poller.phase
        XCTAssertEqual(phaseAfterTimeout, .timedOut)

        let callsAtTimeout = await counter.pollCount
        XCTAssertEqual(callsAtTimeout, 39)

        await clock.advance(by: .seconds(15))
        let callsAfterExtraWait = await counter.pollCount
        XCTAssertEqual(callsAfterExtraWait, 39, "Must not poll after timing out")

        await counter.resetTimeout()
        await poller.restartWindowAfterReload(
            isSceneActive: true,
            onPoll: {
                _ = await counter.increment()
                return true
            },
            onTimeout: {
                await counter.markTimeout()
            }
        )
        await clock.waitForWaiter()

        await clock.advance(by: .seconds(3))
        await clock.waitForWaiter()
        let callsAfterReload = await counter.pollCount
        XCTAssertEqual(callsAfterReload, 40, "Reload must open a fresh 120s window and resume 3s polling")

        for step in 0..<39 {
            await clock.advance(by: .seconds(3))
            if step < 38 {
                await clock.waitForWaiter()
            }
        }
        await clock.settle()
        let timedOutAgain = await counter.didTimeout
        XCTAssertTrue(timedOutAgain, "Second 120s window must also time out cleanly")
    }
}

private actor PollCounter {
    private(set) var pollCount: Int = 0
    private(set) var didTimeout: Bool = false

    func increment() -> Int {
        pollCount += 1
        return pollCount
    }

    func markTimeout() {
        didTimeout = true
    }

    func resetTimeout() {
        didTimeout = false
    }
}
