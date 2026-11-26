import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class LoadStateViewTests: XCTestCase {
    func testLoading() {
        let view = LoadStateView<String, Text, Text, Text>(
            state: .loading,
            locale: .vi,
            onRetry: {},
            content: { Text($0) },
            empty: { Text("Empty state should not appear") },
            customError: { _, _ in Text("Custom error") }
        )
        .environment(\.brandPalette, .defaultLight)

        let (window, controller) = RecoveryViewTestHelper.host(
            view,
            size: CGSize(width: 390, height: 600),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { window.isHidden = true }

        XCTAssertEqual(AppStrings.common("common.loading", locale: .vi), "Đang tải")
        XCTAssertEqual(AppStrings.common("common.loading", locale: .en), "Loading")
        XCTAssertGreaterThan(controller.view.bounds.width, 0)
    }

    func testServerErrorRetriesSameCall() async {
        let log = FetchPathLog()
        let controller = LoadController<String>(
            isEmpty: { $0.isEmpty },
            fetch: {
                let count = await log.record("/api/v1/catalog/products?page=1")
                if count == 1 {
                    throw MobileApiError.api(statusCode: 500, error: ApiError(code: "INTERNAL_ERROR"))
                }
                return "Loaded products"
            }
        )

        await controller.load()
        XCTAssertEqual(controller.state, .failed(kind: .server, canRetry: true, retryAfter: nil))
        XCTAssertEqual(AppStrings.common("errorPage.title", locale: .vi), "Đã có lỗi máy chủ")
        XCTAssertEqual(
            AppStrings.common("errorPage.description", locale: .vi),
            "Đã xảy ra lỗi không mong muốn khi xử lý yêu cầu. Vui lòng thử lại sau."
        )
        XCTAssertEqual(AppStrings.common("common.retry", locale: .vi), "Thử lại")

        var retryCount = 0
        let view = LoadStateView<String, Text, EmptyView, EmptyView>(
            state: controller.state,
            locale: .vi,
            onRetry: { retryCount += 1 },
            content: { Text($0) }
        )

        view.triggerRetryIfAllowed()
        XCTAssertEqual(retryCount, 1)

        await controller.retry()
        let calls = await log.paths
        XCTAssertEqual(calls, ["/api/v1/catalog/products?page=1", "/api/v1/catalog/products?page=1"])
        XCTAssertEqual(controller.state, .loaded(value: "Loaded products", stale: false))
    }

    func testOfflineNoData() {
        let state: LoadState<String> = .failed(kind: .offline, canRetry: true, retryAfter: nil)
        let view = LoadStateView<String, Text, EmptyView, EmptyView>(
            state: state,
            locale: .vi,
            onRetry: {},
            content: { Text($0) }
        )
        .environment(\.brandPalette, .defaultLight)

        let (window, controller) = RecoveryViewTestHelper.host(
            view,
            size: CGSize(width: 390, height: 600),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { window.isHidden = true }

        XCTAssertEqual(AppStrings.common("common.offline.title", locale: .vi), "Không có kết nối")
        XCTAssertEqual(AppStrings.common("common.offline.description", locale: .vi), "Kiểm tra kết nối mạng rồi thử lại.")
        XCTAssertGreaterThan(controller.view.bounds.width, 0)
    }

    func testRetryBlockedUntilRetryAfter() async {
        let clock = ManualClock()
        let counter = RetryCounter()
        let controller = LoadController<String>(
            clock: clock,
            fetch: {
                let attempt = await counter.increment()
                if attempt == 1 {
                    throw MobileApiError.api(statusCode: 429, error: ApiError(code: "RATE_LIMITED", retryAfter: 10))
                }
                return "recovered"
            }
        )

        await controller.load()
        XCTAssertEqual(controller.state, .failed(kind: .server, canRetry: true, retryAfter: 10))
        XCTAssertFalse(controller.canRetryNow)

        var viewRetryClicks = 0
        let blockedView = LoadStateView<String, Text, EmptyView, EmptyView>(
            state: controller.state,
            locale: .vi,
            retryBlockedUntil: controller.retryAvailableAt,
            clock: clock,
            onRetry: { viewRetryClicks += 1 },
            content: { Text($0) }
        )
        XCTAssertTrue(blockedView.isRetryBlocked)
        blockedView.triggerRetryIfAllowed()
        XCTAssertEqual(viewRetryClicks, 0, "Retry button must ignore taps before retryAfter expires")

        await controller.retry()
        let countBefore = await counter.value
        XCTAssertEqual(countBefore, 1, "LoadController.retry must not fire before retryAfter expires")

        clock.advance(by: 9)
        XCTAssertFalse(controller.canRetryNow)
        await controller.retry()
        let countAt9 = await counter.value
        XCTAssertEqual(countAt9, 1)

        clock.advance(by: 1)
        XCTAssertTrue(controller.canRetryNow)
        XCTAssertFalse(blockedView.isRetryBlocked)
        blockedView.triggerRetryIfAllowed()
        XCTAssertEqual(viewRetryClicks, 1)

        await controller.retry()
        let countAfter = await counter.value
        XCTAssertEqual(countAfter, 2)
        XCTAssertEqual(controller.state, .loaded(value: "recovered", stale: false))
    }
}

private actor FetchPathLog {
    private(set) var paths: [String] = []
    func record(_ path: String) -> Int {
        paths.append(path)
        return paths.count
    }
}

private actor RetryCounter {
    private(set) var value = 0
    func increment() -> Int {
        value += 1
        return value
    }
}
