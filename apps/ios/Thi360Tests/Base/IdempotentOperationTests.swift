import Foundation
import XCTest
@testable import Thi360

final class IdempotentOperationTests: XCTestCase {
    func testRetryableErrorsPreserveKeyAndNonRetryableOrSuccessRotateKey() async throws {
        let operation = IdempotentOperation()
        let keyLog = KeyLog()

        let retryableErrors: [MobileApiError] = [
            .network(.notConnectedToInternet),
            .network(.timedOut),
            .api(statusCode: 408, error: ApiError(code: "REQUEST_TIMEOUT")),
            .api(statusCode: 429, error: ApiError(code: "RATE_LIMITED")),
            .api(statusCode: 500, error: ApiError(code: "INTERNAL_ERROR")),
            .api(statusCode: 503, error: ApiError(code: "SERVICE_UNAVAILABLE"))
        ]

        for error in retryableErrors {
            do {
                _ = try await operation.run { key -> String in
                    await keyLog.append(key)
                    throw error
                }
                XCTFail("Expected error \(error)")
            } catch {}
        }

        let recordedKeys = await keyLog.keys
        XCTAssertEqual(recordedKeys.count, retryableErrors.count)
        let firstKey = try XCTUnwrap(recordedKeys.first)
        XCTAssertTrue(recordedKeys.allSatisfy { $0 == firstKey }, "All retryable errors must reuse exact same Idempotency-Key")
        let persistedKey = await operation.activeKey
        XCTAssertEqual(persistedKey, firstKey)

        // Succeeding with the retried key rotates key for the next click.
        let successKeys = KeyLog()
        let result = try await operation.run { key -> String in
            await successKeys.append(key)
            return "ok"
        }
        XCTAssertEqual(result, "ok")
        let successKey = await successKeys.keys.first
        XCTAssertEqual(successKey, firstKey)
        let clearedAfterSuccess = await operation.activeKey
        XCTAssertNil(clearedAfterSuccess)

        let nextClickKeys = KeyLog()
        _ = try await operation.run { key -> String in
            await nextClickKeys.append(key)
            return "ok2"
        }
        let nextClickRaw = await nextClickKeys.keys.first
        let nextClickKey = try XCTUnwrap(nextClickRaw)
        XCTAssertNotEqual(nextClickKey, firstKey, "Next operation after success must mint a new Idempotency-Key")

        // Terminal 4xx errors clear the key so the next click generates a fresh key.
        let terminalStatuses = [400, 404, 409, 410, 422]
        var seenTerminalKeys = Set<String>()
        for status in terminalStatuses {
            let log = KeyLog()
            do {
                _ = try await operation.run { key -> String in
                    await log.append(key)
                    throw MobileApiError.api(statusCode: status, error: ApiError(code: "ERR_\(status)"))
                }
                XCTFail("Expected status \(status)")
            } catch {}
            let usedKeyRaw = await log.keys.first
            let usedKey = try XCTUnwrap(usedKeyRaw)
            XCTAssertFalse(usedKey.isEmpty)
            XCTAssertTrue(seenTerminalKeys.insert(usedKey).inserted, "Status \(status) must clear key for next tap")
            let afterTerminal = await operation.activeKey
            XCTAssertNil(afterTerminal, "Status \(status) must not keep old key")
        }
    }

    func testDoubleTapOnlySendsOneRequest() async throws {
        let operation = IdempotentOperation()
        let gate = TestAsyncGate()
        let counter = CallCounter()

        let first = Task<String?, Error> {
            try await operation.run { _ in
                await counter.increment()
                await gate.enterAndWait()
                return "done"
            }
        }

        await gate.waitUntilEntered()
        let second = Task<String?, Error> {
            try await operation.run { _ in
                await counter.increment()
                return "should-not-run"
            }
        }

        let secondValue = try await second.value
        XCTAssertNil(secondValue, "Concurrent second tap must be ignored while first request is in flight")

        await gate.release()
        let value = try await first.value
        XCTAssertEqual(value, "done")
        let count = await counter.value
        XCTAssertEqual(count, 1)
    }

    func testRestorePersistedKeyReusesExactKeyAfterProcessRestart() async throws {
        let firstProcess = IdempotentOperation()
        let captured = KeyLog()
        do {
            _ = try await firstProcess.run { key -> Bool in
                await captured.append(key)
                throw MobileApiError.network(.networkConnectionLost)
            }
        } catch {}

        let savedKeyRaw = await firstProcess.activeKey
        let savedKey = try XCTUnwrap(savedKeyRaw)
        let firstKey = await captured.keys.first
        XCTAssertEqual(savedKey, firstKey)

        let restartedProcess = IdempotentOperation()
        await restartedProcess.restore(key: savedKey)
        let retried = KeyLog()
        _ = try await restartedProcess.run { key -> Bool in
            await retried.append(key)
            return true
        }
        let retriedKey = await retried.keys.first
        XCTAssertEqual(retriedKey, savedKey, "Restored operation must send the exact persisted Idempotency-Key")
    }
}

private actor KeyLog {
    private(set) var keys: [String] = []
    func append(_ key: String) { keys.append(key) }
}

private actor CallCounter {
    private(set) var value = 0
    func increment() { value += 1 }
}
