import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class HistoryViewTests: XCTestCase {
    func testFirstOpen() async throws {
        let subjectRow = try MyExamsTestFixtures.cloneHistoryItemJSON(templateState: "SCORED") { item in
            item["attemptId"] = "att-toan-1"
            item["examName"] = "Đề Toán tổng hợp"
            item["scope"] = "SUBJECT"
            item["subjectCode"] = "TOAN"
            item["partCode"] = NSNull()
        }
        let pageData = try MyExamsTestFixtures.makeHistoryPagePayload(items: [subjectRow], page: 0, size: 20, totalItems: 7, totalPages: 1)

        let stack = try MyExamsTestStackFactory.makeStack { transport in
            try MyExamsTestStackFactory.registerDefaultMyExamsAndHistoryFixtures(on: transport)
            transport.registerJSON(method: .get, path: "/api/v1/me/attempts", data: pageData)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let router = AppRouter(initialDestination: .history)
        let feed = HistoryFeed(
            accountId: "acc-1",
            api: stack.historyAPI,
            optionsCache: stack.optionsCache,
            connectivityMonitor: stack.connectivityMonitor,
            clock: stack.clock
        )

        let screen = HistoryScreen(feed: feed, router: router, resetFilters: false, locale: .vi, showsBrandHeader: true)
        let (window, controller) = CatalogViewInspector.host(
            screen
                .environment(\.brandPalette, .defaultLight)
                .environment(\.statusPalette, .light),
            settleSeconds: 0.15
        )
        defer { window.isHidden = true }

        try await MyExamsTestStackFactory.waitUntil(window: window) {
            if case .loaded = feed.loadState {
                return !feed.subjectMap.isEmpty
            }
            return false
        }
        CatalogViewInspector.settle(window: window, seconds: 0.08)

        let attemptCalls = stack.transport.requests(method: .get, path: "/api/v1/me/attempts")
        XCTAssertEqual(attemptCalls.count, 1)
        let attemptQuery = MyExamsTestStackFactory.queryItems(from: try XCTUnwrap(attemptCalls.first))
        XCTAssertEqual(attemptQuery["page"], "0")
        XCTAssertEqual(attemptQuery["size"], "20")
        XCTAssertNil(attemptQuery["q"])
        XCTAssertNil(attemptQuery["kind"])
        XCTAssertNil(attemptQuery["scope"])
        XCTAssertNil(attemptQuery["from"])
        XCTAssertNil(attemptQuery["to"])
        XCTAssertNil(attemptQuery["examId"])

        let optionGroups = Set(
            stack.transport.requests(method: .get, path: "/api/v1/catalog/options")
                .compactMap { MyExamsTestStackFactory.queryItems(from: $0)["group"] }
        )
        XCTAssertEqual(optionGroups, ["EXAM", "SUBJECT"])

        let allText = screen.renderedStrings.joined(separator: "\n")
        XCTAssertFalse(allText.contains("Chưa có nội dung"))
        XCTAssertTrue(allText.contains("Lịch sử luyện thi"))
        XCTAssertTrue(allText.contains("7 lượt"))
        XCTAssertTrue(allText.contains("Môn Toán học"))
        XCTAssertFalse(RecoveryViewTestHelper.findViews(ofType: UIScrollView.self, in: controller.view).isEmpty)

        let error500 = try MyExamsTestFixtures.makeInternalServerError500Data()
        let fallbackStack = try MyExamsTestStackFactory.makeStack { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/attempts", data: pageData)
            transport.registerJSON(method: .get, path: "/api/v1/catalog/options", statusCode: 500, data: error500)
        }
        defer { UserDefaults(suiteName: fallbackStack.defaultsSuiteName)?.removePersistentDomain(forName: fallbackStack.defaultsSuiteName) }

        let fallbackFeed = HistoryFeed(
            accountId: "acc-2",
            api: fallbackStack.historyAPI,
            optionsCache: fallbackStack.optionsCache,
            clock: fallbackStack.clock
        )
        let fallbackRouter = AppRouter(initialDestination: .history)
        await fallbackFeed.loadOnAppear(resetFilters: false, reloadToken: 0)

        let fallbackScreen = HistoryScreen(feed: fallbackFeed, router: fallbackRouter, resetFilters: false, locale: .vi, showsBrandHeader: false)
        let (fbWin, _) = CatalogViewInspector.host(
            fallbackScreen
                .environment(\.brandPalette, .defaultLight)
                .environment(\.statusPalette, .light),
            settleSeconds: 0.08
        )
        defer { fbWin.isHidden = true }
        let fbText = fallbackScreen.renderedStrings.joined(separator: "\n")
        XCTAssertTrue(fbText.contains("Đề Toán tổng hợp"))
        XCTAssertTrue(fbText.contains("Môn TOAN"), "When catalog/options fails, subject code TOAN must fall back to 'Môn TOAN'")
    }

    func testOptionsNotRefetchedWithin300s() async throws {
        let clock = MyExamsManualClock()
        let stack = try MyExamsTestStackFactory.makeStack(clock: clock) { transport in
            try MyExamsTestStackFactory.registerDefaultMyExamsAndHistoryFixtures(on: transport)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let feed = HistoryFeed(
            accountId: "acc-1",
            api: stack.historyAPI,
            optionsCache: stack.optionsCache,
            clock: clock
        )
        await feed.loadOnAppear(resetFilters: false, reloadToken: 0)
        XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/catalog/options"), 2)
        XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/me/attempts"), 1)

        await feed.selectScope(.part)
        XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/me/attempts"), 2)

        clock.advance(by: 299)
        feed.markLeftTab()
        await feed.loadOnAppear(resetFilters: false, reloadToken: 0)

        XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/catalog/options"), 2, "Catalog options must not be refetched within 300s")
        XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/me/attempts"), 3)
        let lastQuery = MyExamsTestStackFactory.queryItems(
            from: try XCTUnwrap(stack.transport.requests(method: .get, path: "/api/v1/me/attempts").last)
        )
        XCTAssertEqual(lastQuery["scope"], "PART")

        clock.advance(by: 2)
        feed.markLeftTab()
        await feed.loadOnAppear(resetFilters: false, reloadToken: 0)
        XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/catalog/options"), 4)
    }
}
