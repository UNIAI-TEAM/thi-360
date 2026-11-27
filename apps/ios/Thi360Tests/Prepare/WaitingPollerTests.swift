import Foundation
import XCTest
@testable import Thi360

@MainActor
final class WaitingPollerTests: XCTestCase {
    private final class StubPollAPI: PrepareAPIClientProtocol, @unchecked Sendable {
        typealias Handler = @Sendable (Int, String) async throws -> AttemptPollResult

        private let lock = NSLock()
        private var count = 0
        private var inFlight = 0
        private var maxConcurrent = 0
        private let handler: Handler

        init(handler: @escaping Handler) {
            self.handler = handler
        }

        var callCount: Int {
            lock.lock()
            defer { lock.unlock() }
            return count
        }

        var maxConcurrentCalls: Int {
            lock.lock()
            defer { lock.unlock() }
            return maxConcurrent
        }

        func prepare(entitlementId: String) async throws -> AttemptPrepareResponse {
            throw MobileApiError.network(.notConnectedToInternet)
        }

        func startAttempt(
            entitlementId: String,
            preChoice: PreparePreChoiceInput?,
            idempotencyKey: String
        ) async throws -> StartAttemptResult {
            throw MobileApiError.network(.notConnectedToInternet)
        }

        func getAttemptState(attemptId: String) async throws -> AttemptPollResult {
            let callIndex = lock.withLock { () -> Int in
                count += 1
                inFlight += 1
                maxConcurrent = max(maxConcurrent, inFlight)
                return count
            }
            defer {
                lock.withLock {
                    inFlight -= 1
                }
            }
            return try await handler(callIndex, attemptId)
        }
    }

    func testFastSlowCadenceNonOverlappingSlowServerErrorToleranceAnd89vs90Boundary() async throws {
        let clock = VirtualPrepareClock()
        let api = StubPollAPI { callIndex, attemptId in
            switch callIndex {
            case 1:
                try await clock.sleep(seconds: 3.0)
                return AttemptPollResult(attemptId: attemptId, state: .starting)
            case 2:
                throw MobileApiError.network(.notConnectedToInternet)
            case 3:
                throw MobileApiError.unexpectedResponse(statusCode: 503)
            default:
                return AttemptPollResult(attemptId: attemptId, state: .starting)
            }
        }

        var phases: [WaitingPhase] = []
        var readyIds: [String] = []
        var abortedReasons: [String?] = []

        let poller = WaitingPoller(
            attemptId: "att-poll-1",
            clock: clock,
            api: api,
            onReady: { readyIds.append($0) },
            onAborted: { abortedReasons.append($0) },
            onPhaseChanged: { phases.append($0) }
        )
        defer { poller.stop() }

        poller.start(initiallyForeground: true)
        await clock.settle()
        XCTAssertEqual(api.callCount, 0, "First poll waits 1s interval")

        await clock.advance(by: 1.0)
        XCTAssertEqual(api.callCount, 1, "First poll starts at t=1s")

        await clock.advance(by: 1.0)
        await clock.advance(by: 1.0)
        XCTAssertEqual(api.callCount, 1, "Slow 3s server response must NEVER trigger overlapping poll")
        XCTAssertEqual(api.maxConcurrentCalls, 1)

        await clock.advance(by: 1.0)
        XCTAssertEqual(api.callCount, 1, "First 3s poll completes at t=4s; next waits 1s")

        await clock.advance(by: 1.0)
        XCTAssertEqual(api.callCount, 2, "Second poll fires at t=5s and throws network error")
        XCTAssertTrue(readyIds.isEmpty)
        XCTAssertTrue(abortedReasons.isEmpty)
        if case .polling = poller.phase {} else {
            XCTFail("Network error during polling must keep polling phase, got \(poller.phase)")
        }

        await clock.advance(by: 1.0)
        XCTAssertEqual(api.callCount, 3, "Third poll fires at t=6s and throws 503")
        if case .polling = poller.phase {} else {
            XCTFail("5xx error during polling must keep polling phase, got \(poller.phase)")
        }

        await clock.advanceTo(89.0)
        if case .polling = poller.phase {} else {
            XCTFail("At 89s boundary poller must still be in .polling, got \(poller.phase)")
        }

        await clock.advanceTo(90.0)
        XCTAssertEqual(poller.phase, .busy, "At 90s boundary poller must switch to .busy")

        let callsAt90 = api.callCount
        await clock.advance(by: 4.0)
        XCTAssertEqual(api.callCount, callsAt90, "In busy phase (<5s elapsed) must NOT poll every 1s")

        await clock.advance(by: 1.0)
        XCTAssertEqual(api.callCount, callsAt90 + 1, "In busy phase must poll at 5s interval")

        await clock.advance(by: 10.0)
        XCTAssertEqual(api.callCount, callsAt90 + 3, "Busy polling continues indefinitely every 5s")
        XCTAssertEqual(api.maxConcurrentCalls, 1)
    }

    func testTerminalNonStartingStateOpensAttemptAndAbortedStopsPolling() async throws {
        for (variant, expectedReady) in [
            ("in-progress-per-part", true),
            ("closed-submitted", true),
            ("aborted", false)
        ] {
            let clock = VirtualPrepareClock()
            let stateData = try PrepareTestFixtures.makeAttemptStateData(
                variant: variant,
                attemptId: "att-\(variant)"
            )
            let stack = try PrepareTestStackFactory.makeStack { transport in
                transport.registerJSON(
                    method: .get,
                    path: "/api/v1/me/attempts/att-\(variant)",
                    data: stateData
                )
            }
            defer { PrepareTestStackFactory.cleanup(stack) }

            var readyIds: [String] = []
            var abortedReasons: [String?] = []

            let poller = WaitingPoller(
                attemptId: "att-\(variant)",
                clock: clock,
                api: stack.api,
                onReady: { readyIds.append($0) },
                onAborted: { abortedReasons.append($0) }
            )
            defer { poller.stop() }

            poller.start(initiallyForeground: true)
            await clock.advance(by: 1.0)

            let callsAfterTerminal = stack.transport.requests(method: .get, path: "/api/v1/me/attempts/att-\(variant)").count
            XCTAssertEqual(callsAfterTerminal, 1)

            if expectedReady {
                XCTAssertEqual(readyIds, ["att-\(variant)"])
                XCTAssertTrue(abortedReasons.isEmpty)
            } else {
                XCTAssertTrue(readyIds.isEmpty)
                XCTAssertEqual(abortedReasons, ["STALE_STARTING"])
                XCTAssertEqual(poller.phase, .failed(abortReason: "STALE_STARTING"))
            }

            await clock.advance(by: 10.0)
            let callsAfterWait = stack.transport.requests(method: .get, path: "/api/v1/me/attempts/att-\(variant)").count
            XCTAssertEqual(callsAfterWait, 1, "Polling must stop completely after terminal state \(variant)")
        }
    }

    func testBackgroundAndBack() async throws {
        let clock = VirtualPrepareClock()
        let startingData = try PrepareTestFixtures.makeAttemptStateData(
            variant: "starting",
            attemptId: "att-bg-1"
        )
        let stack = try PrepareTestStackFactory.makeStack { transport in
            transport.registerJSON(
                method: .get,
                path: "/api/v1/me/attempts/att-bg-1",
                data: startingData
            )
        }
        defer { PrepareTestStackFactory.cleanup(stack) }

        let poller = WaitingPoller(
            attemptId: "att-bg-1",
            clock: clock,
            api: stack.api
        )

        poller.start(initiallyForeground: true)
        await clock.advance(by: 1.0)
        XCTAssertEqual(pollCount(stack, id: "att-bg-1"), 1)

        poller.setForeground(false)
        await clock.advance(by: 15.0)
        XCTAssertEqual(pollCount(stack, id: "att-bg-1"), 1, "No polls may fire while app is backgrounded")

        poller.setForeground(true)
        await clock.settleAsync()
        XCTAssertEqual(pollCount(stack, id: "att-bg-1"), 2, "Returning to foreground must poll immediately")

        await clock.advance(by: 1.0)
        XCTAssertEqual(pollCount(stack, id: "att-bg-1"), 3, "Foreground resumes 1s cadence")

        poller.stop()
        poller.setForeground(true)
        await clock.advance(by: 10.0)
        XCTAssertEqual(pollCount(stack, id: "att-bg-1"), 3, "Leaving waiting screen via stop() must cancel all future polls")
    }

    private func pollCount(_ stack: PrepareTestStackFactory.Stack, id: String) -> Int {
        stack.transport.requests(method: .get, path: "/api/v1/me/attempts/\(id)").count
    }
}
