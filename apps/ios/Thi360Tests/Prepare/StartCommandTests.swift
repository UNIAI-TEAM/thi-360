import Foundation
import HTTPTypes
import OpenAPIRuntime
import XCTest
@testable import Thi360

@MainActor
final class StartCommandTests: XCTestCase {
    private actor RequestGate {
        private var enteredContinuation: CheckedContinuation<Void, Never>?
        private var releaseContinuation: CheckedContinuation<Void, Never>?
        private var hasEntered = false
        private var hasReleased = false

        func enterAndWait() async {
            hasEntered = true
            enteredContinuation?.resume()
            enteredContinuation = nil
            if hasReleased { return }
            await withCheckedContinuation { continuation in
                releaseContinuation = continuation
            }
        }

        func waitUntilEntered() async {
            if hasEntered { return }
            await withCheckedContinuation { continuation in
                enteredContinuation = continuation
            }
        }

        func release() {
            hasReleased = true
            releaseContinuation?.resume()
            releaseContinuation = nil
        }
    }

    func testKeyBodyAndSingleFlight() async throws {
        let startingData = try PrepareTestFixtures.loadIndexedData(
            operationId: Operations.start.id,
            status: 202,
            variant: "starting"
        )
        let conflictData = try PrepareTestFixtures.loadIndexedData(
            operationId: Operations.start.id,
            status: 409,
            variant: "no-attempts-left"
        )
        let rateLimitedData = try PrepareTestFixtures.makeRateLimited429Data()
        let server500Data = try PrepareTestFixtures.makeInternalServerError500Data()

        let gate = RequestGate()
        let callCounter = LockedCounter()

        let stack = try PrepareTestStackFactory.makeStack { transport in
            transport.register(method: .post, path: "/api/v1/me/attempts") { _ in
                let step = callCounter.increment()
                switch step {
                case 1:
                    await gate.enterAndWait()
                    throw URLError(.notConnectedToInternet)
                case 2:
                    throw URLError(.timedOut)
                case 3:
                    var headers = HTTPFields()
                    headers[.contentType] = "application/json"
                    return (HTTPResponse(status: .requestTimeout, headerFields: headers), HTTPBody(server500Data))
                case 4:
                    var headers = HTTPFields()
                    headers[.contentType] = "application/json"
                    return (HTTPResponse(status: .tooManyRequests, headerFields: headers), HTTPBody(rateLimitedData))
                case 5:
                    var headers = HTTPFields()
                    headers[.contentType] = "application/json"
                    return (HTTPResponse(status: .internalServerError, headerFields: headers), HTTPBody(server500Data))
                case 6:
                    var headers = HTTPFields()
                    headers[.contentType] = "application/json"
                    return (HTTPResponse(status: .accepted, headerFields: headers), HTTPBody(startingData))
                case 7:
                    var headers = HTTPFields()
                    headers[.contentType] = "application/json"
                    return (HTTPResponse(status: .conflict, headerFields: headers), HTTPBody(conflictData))
                default:
                    var headers = HTTPFields()
                    headers[.contentType] = "application/json"
                    return (HTTPResponse(status: .accepted, headerFields: headers), HTTPBody(startingData))
                }
            }
        }
        defer { PrepareTestStackFactory.cleanup(stack) }

        let command = StartCommand(api: stack.api)

        let firstTask = Task {
            await command.start(
                entitlementId: "ent-1",
                preChoice: PreparePreChoiceInput(optionCode: "SCIENCE", subjectCodes: ["LY", "HOA"])
            )
        }
        await gate.waitUntilEntered()

        let concurrentSecond = await command.start(
            entitlementId: "ent-1",
            preChoice: PreparePreChoiceInput(optionCode: "SCIENCE", subjectCodes: ["LY", "HOA"])
        )
        XCTAssertNil(concurrentSecond, "Second tap while start is in flight must be ignored (single-flight)")
        XCTAssertEqual(callCounter.value, 1, "Single-flight must NOT send a second HTTP call")

        await gate.release()
        let firstOutcome = await firstTask.value
        XCTAssertEqual(firstOutcome, .refused(.generic))

        let timeoutOutcome = await command.start(
            entitlementId: "ent-1",
            preChoice: PreparePreChoiceInput(optionCode: "SCIENCE", subjectCodes: ["LY", "HOA"])
        )
        XCTAssertEqual(timeoutOutcome, .refused(.generic))

        let r408Outcome = await command.start(
            entitlementId: "ent-1",
            preChoice: PreparePreChoiceInput(optionCode: "SCIENCE", subjectCodes: ["LY", "HOA"])
        )
        XCTAssertEqual(r408Outcome, .refused(.generic))

        let r429Outcome = await command.start(
            entitlementId: "ent-1",
            preChoice: PreparePreChoiceInput(optionCode: "SCIENCE", subjectCodes: ["LY", "HOA"])
        )
        XCTAssertEqual(r429Outcome, .refused(.rateLimited))

        let r500Outcome = await command.start(
            entitlementId: "ent-1",
            preChoice: PreparePreChoiceInput(optionCode: "SCIENCE", subjectCodes: ["LY", "HOA"])
        )
        XCTAssertEqual(r500Outcome, .refused(.generic))

        let successOutcome = await command.start(
            entitlementId: "ent-1",
            preChoice: PreparePreChoiceInput(optionCode: "SCIENCE", subjectCodes: ["LY", "HOA"])
        )
        XCTAssertEqual(
            successOutcome,
            .opened(attemptId: "00000000-0000-7000-8000-000000000001", .starting)
        )

        let conflictOutcome = await command.start(
            entitlementId: "ent-1",
            preChoice: PreparePreChoiceInput(optionCode: "ANH", subjectCodes: nil)
        )
        XCTAssertEqual(conflictOutcome, .refused(.noAttemptsLeft))

        let afterConflictOutcome = await command.start(entitlementId: "ent-1", preChoice: nil)
        XCTAssertEqual(
            afterConflictOutcome,
            .opened(attemptId: "00000000-0000-7000-8000-000000000001", .starting)
        )

        let startRequests = stack.transport.requests(method: .post, path: "/api/v1/me/attempts")
        XCTAssertEqual(startRequests.count, 8)

        let keys = startRequests.map { $0.headers[HTTPField.Name("Idempotency-Key")!] ?? "" }
        XCTAssertNotNil(UUID(uuidString: keys[0]), "Idempotency-Key must be a valid UUID")

        XCTAssertEqual(keys[1], keys[0], "Retry after offline must reuse Idempotency-Key")
        XCTAssertEqual(keys[2], keys[0], "Retry after timeout must reuse Idempotency-Key")
        XCTAssertEqual(keys[3], keys[0], "Retry after 408 must reuse Idempotency-Key")
        XCTAssertEqual(keys[4], keys[0], "Retry after 429 must reuse Idempotency-Key")
        XCTAssertEqual(keys[5], keys[0], "Retry after 5xx must reuse Idempotency-Key")

        XCTAssertNotEqual(keys[6], keys[5], "Call after 202 success must generate a fresh UUID Idempotency-Key")
        XCTAssertNotNil(UUID(uuidString: keys[6]))
        XCTAssertNotEqual(keys[7], keys[6], "Call after 409 (other 4xx) must generate a fresh UUID Idempotency-Key")
        XCTAssertNotNil(UUID(uuidString: keys[7]))

        let bodyWithSubjects = try XCTUnwrap(
            JSONSerialization.jsonObject(with: XCTUnwrap(startRequests[0].bodyData)) as? [String: Any]
        )
        XCTAssertEqual(bodyWithSubjects["entitlementId"] as? String, "ent-1")
        let preWithSubjects = try XCTUnwrap(bodyWithSubjects["preChoice"] as? [String: Any])
        XCTAssertEqual(preWithSubjects["optionCode"] as? String, "SCIENCE")
        XCTAssertEqual(preWithSubjects["subjectCodes"] as? [String], ["LY", "HOA"])

        let bodyWithoutSubjects = try XCTUnwrap(
            JSONSerialization.jsonObject(with: XCTUnwrap(startRequests[6].bodyData)) as? [String: Any]
        )
        XCTAssertEqual(bodyWithoutSubjects["entitlementId"] as? String, "ent-1")
        let preWithoutSubjects = try XCTUnwrap(bodyWithoutSubjects["preChoice"] as? [String: Any])
        XCTAssertEqual(preWithoutSubjects["optionCode"] as? String, "ANH")
        XCTAssertNil(preWithoutSubjects["subjectCodes"], "Fixed option body must omit subjectCodes")

        let bodyWithoutPreChoice = try XCTUnwrap(
            JSONSerialization.jsonObject(with: XCTUnwrap(startRequests[7].bodyData)) as? [String: Any]
        )
        XCTAssertEqual(bodyWithoutPreChoice["entitlementId"] as? String, "ent-1")
        XCTAssertNil(bodyWithoutPreChoice["preChoice"], "Body without preChoice must omit preChoice")
    }
}

private final class LockedCounter: @unchecked Sendable {
    private let lock = NSLock()
    private var _value = 0

    var value: Int {
        lock.lock()
        defer { lock.unlock() }
        return _value
    }

    @discardableResult
    func increment() -> Int {
        lock.lock()
        defer { lock.unlock() }
        _value += 1
        return _value
    }
}
