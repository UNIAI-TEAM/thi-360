import Foundation
import XCTest
@testable import Thi360

@MainActor
final class AttemptAPIHTTPTests: XCTestCase {
    func testAllAttemptEndpointsSendHeadersAndRoundTripFullAnswerPayloads() async throws {
        let clock = TestEngineClock()
        let server = try FakeAttemptServer(initialStateVariant: "in-progress-per-part", clock: clock)
        let api = server.makeAttemptAPI()

        let snapshot = try await api.getState(attemptId: server.attemptId)
        XCTAssertEqual(snapshot.response.attemptId, server.attemptId)
        XCTAssertEqual(snapshot.response.state, .IN_PROGRESS)

        let claimResp = try await api.claimSession(
            attemptId: server.attemptId,
            sessionId: "11111111-1111-4111-8111-111111111111",
            takeover: false
        )
        XCTAssertEqual(claimResp.sessionId, "11111111-1111-4111-8111-111111111111")

        let hbResp = try await api.heartbeat(attemptId: server.attemptId, sessionId: claimResp.sessionId)
        XCTAssertEqual(hbResp.state, .IN_PROGRESS)
        XCTAssertEqual(server.calls(for: .heartbeat).last?.sessionHeader, claimResp.sessionId)
        XCTAssertEqual(server.calls(for: .heartbeat).last?.authorizationHeader, "Bearer attempt-test-access-token")

        let itemIds = server.leafItemIds(inPartIndex: 0)
        let partId = server.partId(at: 0)
        XCTAssertGreaterThanOrEqual(itemIds.count, 2)

        let changes: [AttemptAnswerChangeInput] = [
            AttemptAnswerChangeInput(itemId: itemIds[0], seq: 1, answer: .singleChoice(optionId: "a")),
            AttemptAnswerChangeInput(itemId: itemIds[1], seq: 2, answer: .shortAnswer(text: "42"))
        ]
        let saveResp = try await api.saveAnswers(
            attemptId: server.attemptId,
            sessionId: claimResp.sessionId,
            changes: changes,
            flags: [AttemptFlagChangeInput(itemId: itemIds[0], flagged: true)],
            position: Position(partId: partId, itemId: itemIds[1])
        )
        XCTAssertEqual(saveResp.results.count, 2)
        XCTAssertTrue(saveResp.results.allSatisfy { $0.status == .SAVED })

        let saveCall = try XCTUnwrap(server.calls(for: .saveAnswers).last)
        XCTAssertEqual(saveCall.sessionHeader, claimResp.sessionId)
        let saveBody = try XCTUnwrap(saveCall.decodedJSON())
        let wireChanges = try XCTUnwrap(saveBody["changes"] as? [[String: Any]])
        XCTAssertEqual((wireChanges[0]["answer"] as? [String: Any])?["optionId"] as? String, "a")
        XCTAssertEqual((wireChanges[1]["answer"] as? [String: Any])?["text"] as? String, "42")

        let reloaded = try await api.getState(attemptId: server.attemptId)
        XCTAssertEqual(reloaded.decodedAnswers[itemIds[0]], .singleChoice(optionId: "a"))
        XCTAssertEqual(reloaded.decodedAnswers[itemIds[1]], .shortAnswer(text: "42"))

        let finishResp = try await api.finishPart(
            attemptId: server.attemptId,
            partId: partId,
            sessionId: claimResp.sessionId,
            finalAnswers: []
        )
        XCTAssertEqual(finishResp.attemptId, server.attemptId)
        XCTAssertNil(server.calls(for: .finishPart).last?.idempotencyKeyHeader)
        XCTAssertEqual(server.calls(for: .finishPart).last?.sessionHeader, claimResp.sessionId)

        let choiceResp = try await api.confirmPartChoice(
            attemptId: server.attemptId,
            partId: partId,
            sessionId: claimResp.sessionId,
            idempotencyKey: "choice-idem-1",
            optionCode: "SCI",
            subjectCodes: ["LY", "HOA", "SINH"]
        )
        XCTAssertEqual(choiceResp.attemptId, server.attemptId)
        XCTAssertEqual(server.calls(for: .confirmPartChoice).last?.idempotencyKeyHeader, "choice-idem-1")
        XCTAssertEqual(server.calls(for: .confirmPartChoice).last?.sessionHeader, claimResp.sessionId)

        let retryResp = try await api.retryPartGeneration(
            attemptId: server.attemptId,
            partId: partId,
            sessionId: claimResp.sessionId,
            idempotencyKey: "retry-idem-1",
            reason: nil
        )
        XCTAssertEqual(retryResp.attemptId, server.attemptId)
        XCTAssertEqual(server.calls(for: .retryPartGeneration).last?.idempotencyKeyHeader, "retry-idem-1")
        XCTAssertEqual(server.calls(for: .retryPartGeneration).last?.sessionHeader, claimResp.sessionId)

        let submitResp = try await api.submit(
            attemptId: server.attemptId,
            sessionId: claimResp.sessionId,
            idempotencyKey: "submit-idem-1",
            finalAnswers: []
        )
        XCTAssertEqual(submitResp.attemptId, server.attemptId)
        XCTAssertEqual(server.calls(for: .submit).last?.idempotencyKeyHeader, "submit-idem-1")
        XCTAssertEqual(server.calls(for: .submit).last?.sessionHeader, claimResp.sessionId)
    }

    func testSaveAnswersRateLimitPreservesZeroOneAndMissingRetryAfter() async throws {
        let server = try FakeAttemptServer(initialStateVariant: "in-progress-shared")
        let api = server.makeAttemptAPI()
        let sessionId = "22222222-2222-4222-8222-222222222222"
        let itemId = server.firstLeafItemId()

        server.enqueue(
            .saveAnswers,
            try .fixture(operationId: Operations.saveAnswers.id, status: 429, variant: "rate-limited", headers: ["Retry-After": "0"])
        )
        server.enqueue(
            .saveAnswers,
            try .fixture(operationId: Operations.saveAnswers.id, status: 429, variant: "rate-limited", headers: ["Retry-After": "1"])
        )
        // Plan A6 documented mutation: omit Retry-After header on certified 429 fixture.
        server.enqueue(
            .saveAnswers,
            try .fixture(operationId: Operations.saveAnswers.id, status: 429, variant: "rate-limited", headers: [:])
        )

        let input = [AttemptAnswerChangeInput(itemId: itemId, seq: 1, answer: .singleChoice(optionId: "a"))]

        for expected in [Int?(0), Int?(1), Int?(nil)] {
            do {
                _ = try await api.saveAnswers(attemptId: server.attemptId, sessionId: sessionId, changes: input, flags: [], position: nil)
                XCTFail("Expected 429 error")
            } catch let error as AttemptAPIError {
                XCTAssertEqual(error, .rateLimited(retryAfterSeconds: expected))
            }
        }
    }
}
