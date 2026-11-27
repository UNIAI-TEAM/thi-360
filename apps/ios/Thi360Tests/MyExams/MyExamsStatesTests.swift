import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class MyExamsStatesTests: XCTestCase {
    func testLoadingAndRetryPolicy() async throws {
        let error500 = try MyExamsTestFixtures.makeInternalServerError500Data()
        let error400 = try MyExamsTestFixtures.makeBadRequest400Data()
        let okData = try MyExamsTestFixtures.loadIndexedData(operationId: Operations.listMyExams.id, status: 200, variant: "")
        let loadingGate = TestAsyncGate()

        let sleeper = RecordingRetrySleeper()
        let stack = try MyExamsTestStackFactory.makeStack { transport in
            transport.enqueue(method: .get, path: "/api/v1/me/exams", handlers: [
                { _ in
                    await loadingGate.enterAndWait()
                    throw URLError(.timedOut)
                },
                { _ in ContractFakeTransport.jsonResponse(statusCode: 500, data: error500) },
                { _ in ContractFakeTransport.jsonResponse(statusCode: 502, data: error500) },
                { _ in ContractFakeTransport.jsonResponse(statusCode: 200, data: okData) }
            ])
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let router = AppRouter(initialDestination: .myExams)
        let feed = MyExamsFeed(
            accountId: "acc-1",
            api: stack.myExamsAPI,
            connectivityMonitor: stack.connectivityMonitor,
            clock: stack.clock,
            retrySleeper: sleeper,
            initialFullName: "An",
            initialUsername: "an"
        )

        var recordedFrames: [String: CGRect] = [:]
        let screen = MyExamsScreen(feed: feed, router: router, locale: .vi, showsBrandHeader: false)
        let (window, _) = CatalogViewInspector.host(
            screen
                .environment(\.brandPalette, .defaultLight)
                .environment(\.statusPalette, .light)
                .environment(\.myExamsLayoutFrameObserver, { id, rect in recordedFrames[id] = rect }),
            settleSeconds: 0.06
        )
        defer { window.isHidden = true }

        await loadingGate.waitUntilEntered()
        CatalogViewInspector.settle(window: window, seconds: 0.05)

        XCTAssertNotNil(recordedFrames["myExams.loadingSkeleton"], "Initial load must display 3-card loading skeleton")
        XCTAssertEqual(MyExamsSkeletonView(locale: .vi, isRegularWidth: false).accessibilityLoadingLabel, "Đang tải")
        XCTAssertTrue(screen.renderedStrings.contains("Đang tải"))
        let (_, loadingPNG) = try CatalogViewInspector.renderPNG(window: window, size: CGSize(width: 390, height: 844))

        await loadingGate.release()
        try await MyExamsTestStackFactory.waitUntil(window: window) {
            if case .failed(.server, true, nil) = feed.loadState { return true }
            return false
        }
        CatalogViewInspector.settle(window: window, seconds: 0.06)

        XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/me/exams"), 3, "Initial network/5xx error must auto-retry at most 2 times (3 total calls)")
        XCTAssertEqual(sleeper.recordedAttempts, [0, 1])

        let errText = screen.renderedStrings.joined(separator: "\n")
        XCTAssertTrue(errText.contains("Không tải được danh sách bài thi"))
        XCTAssertTrue(errText.contains("Kiểm tra kết nối mạng rồi thử lại."))
        XCTAssertTrue(errText.contains("Thử lại"))
        let (_, errorPNG) = try CatalogViewInspector.renderPNG(window: window, size: CGSize(width: 390, height: 844))
        XCTAssertNotEqual(loadingPNG, errorPNG)

        await feed.retryFromError()
        XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/me/exams"), 4)
        if case .loaded = feed.loadState {} else {
            XCTFail("Expected .loaded after retry, got \(feed.loadState)")
        }

        let sleeper4xx = RecordingRetrySleeper()
        let stack4xx = try MyExamsTestStackFactory.makeStack { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/exams", statusCode: 400, data: error400)
        }
        defer { UserDefaults(suiteName: stack4xx.defaultsSuiteName)?.removePersistentDomain(forName: stack4xx.defaultsSuiteName) }

        let feed4xx = MyExamsFeed(
            accountId: "acc-2",
            api: stack4xx.myExamsAPI,
            connectivityMonitor: stack4xx.connectivityMonitor,
            clock: stack4xx.clock,
            retrySleeper: sleeper4xx
        )
        await feed4xx.loadOnAppear()
        XCTAssertEqual(stack4xx.transport.callCount(method: .get, path: "/api/v1/me/exams"), 1, "4xx != 401 must not auto-retry")
        XCTAssertTrue(sleeper4xx.recordedAttempts.isEmpty)
        XCTAssertEqual(feed4xx.loadState, .failed(kind: .server, canRetry: true, retryAfter: nil))
    }

    func testStaleAndOffline() async throws {
        try await MyExamsTestStackFactory.withDefaultTimeZone("UTC") {
            let error500 = try MyExamsTestFixtures.makeInternalServerError500Data()
            let firstCard = try MyExamsTestFixtures.cloneMyExamCardJSON(templateState: "CAN_START") { item in
                item["entitlementId"] = "ent-s1"
                item["code"] = "STALE-1"
                item["name"] = "Đề bản cũ"
                item["bundles"] = []
            }
            let freshCard = try MyExamsTestFixtures.cloneMyExamCardJSON(templateState: "CAN_START") { item in
                item["entitlementId"] = "ent-s2"
                item["code"] = "FRESH-2"
                item["name"] = "Đề bản mới"
                item["bundles"] = []
            }
            let firstData = try MyExamsTestFixtures.makeMyExamsPayload(items: [firstCard])
            let freshData = try MyExamsTestFixtures.makeMyExamsPayload(items: [freshCard])
            let retryGate = TestAsyncGate()

            let loadedInstant = ISO8601DateFormatter().date(from: "2026-11-20T02:05:00Z")!
            let clock = MyExamsManualClock(now: loadedInstant)

            let stack = try MyExamsTestStackFactory.makeStack(clock: clock) { transport in
                transport.enqueue(method: .get, path: "/api/v1/me/exams", handlers: [
                    { _ in ContractFakeTransport.jsonResponse(statusCode: 200, data: firstData) },
                    { _ in ContractFakeTransport.jsonResponse(statusCode: 500, data: error500) },
                    { _ in
                        await retryGate.enterAndWait()
                        return ContractFakeTransport.jsonResponse(statusCode: 200, data: freshData)
                    }
                ])
            }
            defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

            let feed = MyExamsFeed(
                accountId: "acc-1",
                api: stack.myExamsAPI,
                connectivityMonitor: stack.connectivityMonitor,
                clock: clock,
                retrySleeper: RecordingRetrySleeper(),
                initialFullName: "An",
                initialUsername: "an"
            )
            let router = AppRouter(initialDestination: .myExams)

            await feed.loadOnAppear()
            await feed.reloadKeepingExisting()

            guard case let .loaded(staleItems, isStale) = feed.loadState else {
                return XCTFail("Expected .loaded(stale: true), got \(feed.loadState)")
            }
            XCTAssertTrue(isStale)
            XCTAssertEqual(staleItems.first?.name, "Đề bản cũ")

            let screen = MyExamsScreen(feed: feed, router: router, locale: .vi, showsBrandHeader: false)
            let (window, _) = CatalogViewInspector.host(
                screen
                    .environment(\.brandPalette, .defaultLight)
                    .environment(\.statusPalette, .light),
                settleSeconds: 0.1
            )
            defer { window.isHidden = true }

            let staleText = screen.renderedStrings.joined(separator: "\n")
            XCTAssertTrue(staleText.contains("Dữ liệu chưa được cập nhật."))
            XCTAssertTrue(
                staleText.contains("Không tải lại được danh sách; đang hiện bản đã tải lúc 09:05."),
                "Stale time must use Asia/Ho_Chi_Minh (09:05) under UTC device timezone; got: \(staleText)"
            )
            XCTAssertTrue(staleText.contains("Đề bản cũ"))

            let noTimeBanner = MyExamsStaleBannerView(lastLoadedAt: nil, isReloading: false, locale: .vi, onRetry: {})
            XCTAssertTrue(noTimeBanner.renderedStrings.joined(separator: "\n").contains(MyExamsStrings.text("home.staleDescriptionNoTime", locale: .vi)))

            let retryTask = Task { await feed.retryFromStaleBanner() }
            await retryGate.waitUntilEntered()
            XCTAssertTrue(feed.isReloading)
            await feed.retryFromStaleBanner()
            XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/me/exams"), 3, "Duplicate stale retry tap must be ignored while in flight")

            await retryGate.release()
            await retryTask.value
            XCTAssertFalse(feed.isReloading)
            guard case let .loaded(freshItems, freshStale) = feed.loadState else {
                return XCTFail("Expected fresh loaded state, got \(feed.loadState)")
            }
            XCTAssertFalse(freshStale)
            XCTAssertEqual(freshItems.first?.name, "Đề bản mới")

            stack.connectivityMonitor.applyUpdate(false)
            await feed.reloadKeepingExisting()
            guard case let .loaded(offlineCached, offlineStale) = feed.loadState else {
                return XCTFail("Offline reload with cached items must show stale banner, got \(feed.loadState)")
            }
            XCTAssertTrue(offlineStale)
            XCTAssertEqual(offlineCached.first?.name, "Đề bản mới")

            let offlineFeed = MyExamsFeed(
                accountId: "acc-off",
                api: stack.myExamsAPI,
                connectivityMonitor: stack.connectivityMonitor,
                clock: clock,
                retrySleeper: RecordingRetrySleeper(),
                initialFullName: "An",
                initialUsername: "an"
            )
            await offlineFeed.loadOnAppear()
            XCTAssertEqual(offlineFeed.loadState, .failed(kind: .offline, canRetry: true, retryAfter: nil))

            let offlineScreen = MyExamsScreen(feed: offlineFeed, router: router, locale: .vi, showsBrandHeader: false)
            let (offWin, _) = CatalogViewInspector.host(
                offlineScreen
                    .environment(\.brandPalette, .defaultLight)
                    .environment(\.statusPalette, .light),
                settleSeconds: 0.08
            )
            defer { offWin.isHidden = true }
            let offText = offlineScreen.renderedStrings.joined(separator: "\n")
            XCTAssertTrue(offText.contains("Không có kết nối"))
            XCTAssertTrue(offText.contains("Thử lại"))
        }
    }

    func testEmpty() async throws {
        let emptyData = try MyExamsTestFixtures.loadIndexedData(
            operationId: Operations.listMyExams.id,
            status: 200,
            variant: "empty"
        )
        let stack = try MyExamsTestStackFactory.makeStack { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/exams", data: emptyData)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let router = AppRouter(initialDestination: .myExams)
        let feed = MyExamsFeed(
            accountId: "acc-1",
            api: stack.myExamsAPI,
            clock: stack.clock,
            retrySleeper: RecordingRetrySleeper(),
            initialFullName: "An",
            initialUsername: "an"
        )
        await feed.loadOnAppear()
        XCTAssertEqual(feed.loadState, .empty)

        let screen = MyExamsScreen(feed: feed, router: router, locale: .vi, showsBrandHeader: false)
        let (window, _) = CatalogViewInspector.host(
            screen
                .environment(\.brandPalette, .defaultLight)
                .environment(\.statusPalette, .light),
            settleSeconds: 0.08
        )
        defer { window.isHidden = true }

        let text = screen.renderedStrings.joined(separator: "\n")
        XCTAssertTrue(text.contains("Bạn chưa có bài thi nào"))
        XCTAssertTrue(text.contains("Chọn bài thi phù hợp trong danh mục để bắt đầu luyện tập."))
        XCTAssertTrue(text.contains("Khám phá bài thi"))

        let nav = MyExamsNavigationHandler(router: router, clock: stack.clock)
        nav.openExploreCatalog()
        XCTAssertEqual(router.selectedDestination, .home)
    }
}
