import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class HistoryStatesTests: XCTestCase {
    func testLoadingSkeletonInitialErrorStaleDimmedOfflineAndBothEmptyStates() async throws {
        try await MyExamsTestStackFactory.withDefaultTimeZone("UTC") {
            let error500 = try MyExamsTestFixtures.makeInternalServerError500Data()
            let loadedData = try MyExamsTestFixtures.loadIndexedData(operationId: Operations.listHistory.id, status: 200, variant: "")
            let emptyData = try MyExamsTestFixtures.loadIndexedData(operationId: Operations.listHistory.id, status: 200, variant: "empty")
            let initialGate = TestAsyncGate()
            let filterGate = TestAsyncGate()

            let loadedInstant = ISO8601DateFormatter().date(from: "2026-11-20T02:05:00Z")!
            let clock = MyExamsManualClock(now: loadedInstant)

            let stack = try MyExamsTestStackFactory.makeStack(clock: clock) { transport in
                try MyExamsTestStackFactory.registerDefaultMyExamsAndHistoryFixtures(on: transport)
                transport.enqueue(method: .get, path: "/api/v1/me/attempts", handlers: [
                    { _ in
                        await initialGate.enterAndWait()
                        return ContractFakeTransport.jsonResponse(statusCode: 500, data: error500)
                    },
                    { _ in ContractFakeTransport.jsonResponse(statusCode: 200, data: loadedData) },
                    { _ in
                        await filterGate.enterAndWait()
                        return ContractFakeTransport.jsonResponse(statusCode: 500, data: error500)
                    },
                    { _ in ContractFakeTransport.jsonResponse(statusCode: 200, data: emptyData) },
                    { _ in ContractFakeTransport.jsonResponse(statusCode: 200, data: emptyData) }
                ])
            }
            defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

            let router = AppRouter(initialDestination: .history)
            let feed = HistoryFeed(
                accountId: "acc-1",
                api: stack.historyAPI,
                optionsCache: stack.optionsCache,
                connectivityMonitor: stack.connectivityMonitor,
                clock: clock
            )

            var frames: [String: CGRect] = [:]
            let screen = HistoryScreen(feed: feed, router: router, resetFilters: false, locale: .vi, showsBrandHeader: false)
            let (window, controller) = CatalogViewInspector.host(
                screen
                    .environment(\.brandPalette, .defaultLight)
                    .environment(\.statusPalette, .light)
                    .environment(\.myExamsLayoutFrameObserver, { id, rect in frames[id] = rect }),
                settleSeconds: 0.06
            )
            defer { window.isHidden = true }

            await initialGate.waitUntilEntered()
            CatalogViewInspector.settle(window: window, seconds: 0.05)

            XCTAssertNotNil(frames["history.loadingSkeleton"])
            XCTAssertEqual(HistorySkeletonView(locale: .vi).accessibilityLoadingLabel, "Đang tải lịch sử luyện thi")
            XCTAssertEqual(RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: controller.view).count, 1, "Filters must remain usable during loading")

            await initialGate.release()
            try await MyExamsTestStackFactory.waitUntil(window: window) {
                if case .failed(.server, true, nil) = feed.loadState { return true }
                return false
            }
            CatalogViewInspector.settle(window: window, seconds: 0.06)
            XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/me/attempts"), 1, "History initial error must NOT auto-retry")
            let errText = screen.renderedStrings.joined(separator: "\n")
            XCTAssertTrue(errText.contains("Không tải được lịch sử luyện thi"))
            XCTAssertTrue(errText.contains("Thử lại"))

            await feed.retry()
            XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/me/attempts"), 2)
            if case .loaded = feed.loadState {} else { XCTFail("Expected loaded state after retry") }

            let scopeTask = Task { await feed.selectScope(.part) }
            await filterGate.waitUntilEntered()
            XCTAssertTrue(feed.isSwitchingFilter, "Changing filter with loaded data must dim existing list")

            await filterGate.release()
            await scopeTask.value
            XCTAssertFalse(feed.isSwitchingFilter)
            guard case let .loaded(stalePage, staleFlag) = feed.loadState else {
                return XCTFail("Expected stale loaded state, got \(feed.loadState)")
            }
            XCTAssertTrue(staleFlag)
            XCTAssertFalse(stalePage.items.isEmpty)

            let staleText = screen.renderedStrings.joined(separator: "\n")
            XCTAssertTrue(staleText.contains("Dữ liệu chưa được cập nhật."))
            XCTAssertTrue(
                staleText.contains("09:05, 20/11/2026"),
                "History stale banner must format Asia/Ho_Chi_Minh HH:mm, dd/MM/yyyy under UTC TZ; got: \(staleText)"
            )

            let noTimeBanner = HistoryStaleBannerView(lastLoadedAt: nil, isRetrying: false, locale: .vi, onRetry: {})
            XCTAssertTrue(noTimeBanner.renderedStrings.joined(separator: "\n").contains(MyExamsStrings.text("history.stale.descriptionNoTime", locale: .vi)))

            await feed.retry()
            let retryQuery = MyExamsTestStackFactory.queryItems(
                from: try XCTUnwrap(stack.transport.requests(method: .get, path: "/api/v1/me/attempts").last)
            )
            XCTAssertEqual(retryQuery["scope"], "PART")
            XCTAssertTrue(feed.isNoMatchState)
            let noMatchText = screen.renderedStrings.joined(separator: "\n")
            XCTAssertTrue(noMatchText.contains("Chưa có lượt thi phù hợp"))
            XCTAssertTrue(noMatchText.contains("Xóa bộ lọc"))

            await feed.clearFilters()
            XCTAssertEqual(feed.loadState, .empty)
            let noAttemptsText = screen.renderedStrings.joined(separator: "\n")
            XCTAssertTrue(noAttemptsText.contains("Bạn chưa có lượt luyện thi nào"))
            XCTAssertTrue(noAttemptsText.contains("Bài thi của tôi"))

            let nav = HistoryNavigationHandler(router: router, clock: clock)
            nav.openMyExamsTab()
            XCTAssertEqual(router.selectedDestination, .myExams)

            router.switchTab(.history)
            stack.connectivityMonitor.applyUpdate(false)
            await feed.reloadWithCurrentFilter()
            XCTAssertEqual(feed.loadState, .failed(kind: .offline, canRetry: true, retryAfter: nil))
            CatalogViewInspector.settle(window: window, seconds: 0.08)
            let offText = screen.renderedStrings.joined(separator: "\n")
            XCTAssertTrue(offText.contains("Không có kết nối"))
            XCTAssertTrue(offText.contains("Thử lại"))
            XCTAssertEqual(RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: controller.view).count, 1, "Filter bar must remain visible when offline")
        }
    }
}
