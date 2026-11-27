import Foundation
import XCTest
@testable import Thi360

@MainActor
final class LoadControllerTests: XCTestCase {
    func testFailureKinds() async {
        let cases: [(MobileApiError, LoadFailureKind, Bool, Int?)] = [
            (.network(.notConnectedToInternet), .offline, true, nil),
            (.api(statusCode: 500, error: ApiError(code: "INTERNAL")), .server, true, nil),
            (.api(statusCode: 404, error: ApiError(code: "NOT_FOUND")), .notFound, false, nil),
            (.api(statusCode: 410, error: ApiError(code: "GONE")), .gone, false, nil),
            (.api(statusCode: 403, error: ApiError(code: "FORBIDDEN")), .forbidden, false, nil),
            (.api(statusCode: 409, error: ApiError(code: "PRICE_CHANGED")), .conflict(code: "PRICE_CHANGED"), false, nil),
            (.api(statusCode: 429, error: ApiError(code: "RATE_LIMITED", retryAfter: 15)), .server, true, 15)
        ]

        for (error, expectedKind, expectedRetry, expectedRetryAfter) in cases {
            let controller = LoadController<String>(fetch: { throw error })
            await controller.load()
            XCTAssertEqual(
                controller.state,
                .failed(kind: expectedKind, canRetry: expectedRetry, retryAfter: expectedRetryAfter)
            )
        }
    }

    func testOfflineKeepsStaleData() async {
        let flag = ToggleFlag()
        let controller = LoadController<[String]>(
            accountIdProvider: { "acc-1" },
            isEmpty: { $0.isEmpty },
            fetch: {
                if await flag.isOn {
                    throw MobileApiError.network(.notConnectedToInternet)
                }
                return ["IELTS Mock 1", "IELTS Mock 2"]
            }
        )

        await controller.load()
        XCTAssertEqual(controller.state, .loaded(value: ["IELTS Mock 1", "IELTS Mock 2"], stale: false))

        await flag.set(true)
        await controller.refresh()
        XCTAssertEqual(controller.state, .loaded(value: ["IELTS Mock 1", "IELTS Mock 2"], stale: true))
    }

    func testAutoRetryWhenOnline() async throws {
        let observer = FakeNetworkPathObserver()
        let monitor = ConnectivityMonitor(initialIsOnline: false, observer: observer, autoStart: false)
        monitor.start()

        let counter = LoadCounter()
        let controller = LoadController<String>(
            accountIdProvider: { "acc-1" },
            connectivityMonitor: monitor,
            fetch: {
                let attempt = await counter.increment()
                if attempt == 1 {
                    throw MobileApiError.network(.notConnectedToInternet)
                }
                return "Fresh data"
            }
        )

        await controller.load()
        XCTAssertEqual(controller.state, .failed(kind: .offline, canRetry: true, retryAfter: nil))
        let count1 = await counter.value
        XCTAssertEqual(count1, 1)

        observer.emit(isOnline: true)
        try await Task.sleep(nanoseconds: 120_000_000)

        let count2 = await counter.value
        XCTAssertEqual(count2, 2, "Regaining network connectivity must auto-retry once")
        XCTAssertEqual(controller.state, .loaded(value: "Fresh data", stale: false))
    }

    func testPullToRefreshReplacesData() async {
        let stateBox = RefreshBox()
        let controller = LoadController<[String]>(
            accountIdProvider: { "acc-1" },
            isEmpty: { $0.isEmpty },
            fetch: {
                let (version, shouldFail) = await stateBox.snapshot()
                if shouldFail {
                    throw MobileApiError.api(statusCode: 500, error: ApiError(code: "SERVER_ERROR"))
                }
                return ["Item v\(version)"]
            }
        )

        await controller.load()
        XCTAssertEqual(controller.state, .loaded(value: ["Item v1"], stale: false))

        await stateBox.set(version: 2, fail: false)
        await controller.refresh()
        XCTAssertEqual(controller.state, .loaded(value: ["Item v2"], stale: false))

        await stateBox.set(version: 3, fail: true)
        await controller.refresh()
        XCTAssertEqual(controller.state, .loaded(value: ["Item v2"], stale: true))
    }

    func testStaleDataClearedOnAccountChange() async {
        var activeAccount: String? = "account-A"
        let flag = ToggleFlag()
        let controller = LoadController<[String]>(
            accountIdProvider: { activeAccount },
            isEmpty: { $0.isEmpty },
            fetch: {
                if await flag.isOn {
                    throw MobileApiError.network(.notConnectedToInternet)
                }
                return ["User A secret exam"]
            }
        )

        await controller.load()
        XCTAssertEqual(controller.state, .loaded(value: ["User A secret exam"], stale: false))

        activeAccount = "account-B"
        await flag.set(true)
        await controller.load()
        XCTAssertEqual(
            controller.state,
            .failed(kind: .offline, canRetry: true, retryAfter: nil),
            "Account B must never see stale data belonging to Account A"
        )

        controller.clearCachedData()
        XCTAssertEqual(controller.state, .loading)
    }
}

private actor ToggleFlag {
    private(set) var isOn = false
    func set(_ value: Bool) { isOn = value }
}

private actor LoadCounter {
    private(set) var value = 0
    func increment() -> Int {
        value += 1
        return value
    }
}

private actor RefreshBox {
    private var version = 1
    private var fail = false
    func set(version: Int, fail: Bool) {
        self.version = version
        self.fail = fail
    }
    func snapshot() -> (Int, Bool) { (version, fail) }
}
