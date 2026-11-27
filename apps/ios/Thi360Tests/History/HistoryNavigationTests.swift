import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class HistoryNavigationTests: XCTestCase {
    func testHistoryButtonResetsFilter() async throws {
        let stack = try MyExamsTestStackFactory.makeStack { transport in
            try MyExamsTestStackFactory.registerDefaultMyExamsAndHistoryFixtures(on: transport)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let router = AppRouter(initialDestination: .history)
        defer { HistoryFeedRegistry.shared.clear(for: router) }

        let shell = AdaptiveShellView(
            router: router,
            preferences: stack.preferences,
            idiom: .phone,
            sessionStore: stack.sessionStore
        )
        .environment(\.sessionStore, stack.sessionStore)
        .environment(\.catalogOptionsCache, stack.optionsCache)
        .environment(\.brandPalette, .defaultLight)
        .environment(\.statusPalette, .light)

        let (window, controller) = CatalogViewInspector.host(shell, size: CGSize(width: 390, height: 844), settleSeconds: 0.15)
        defer { window.isHidden = true }

        let feed = try XCTUnwrap(HistoryFeedRegistry.shared.existingFeed(for: router))
        try await MyExamsTestStackFactory.waitUntil(window: window) {
            if case .loaded = feed.loadState { return true }
            return false
        }

        feed.searchText = "hsa"
        await feed.submitSearchImmediately()
        await feed.selectScope(.part)
        XCTAssertEqual(feed.filter.trimmedQ, "hsa")
        XCTAssertEqual(feed.filter.scope, .part)

        router.switchTab(.myExams)
        CatalogViewInspector.settle(window: window, seconds: 0.08)
        stack.transport.resetRecordedRequests()

        let myExamsNav = MyExamsNavigationHandler(router: router, clock: stack.clock)
        myExamsNav.handleCardAction(.history)

        try await MyExamsTestStackFactory.waitUntil(window: window) {
            feed.filter.isEmpty && stack.transport.callCount(method: .get, path: "/api/v1/me/attempts") >= 1
        }
        CatalogViewInspector.settle(window: window, seconds: 0.1)

        XCTAssertEqual(router.selectedDestination, .history)
        XCTAssertEqual(feed.searchText, "")
        XCTAssertTrue(feed.filter.isEmpty)

        let query = MyExamsTestStackFactory.queryItems(
            from: try XCTUnwrap(stack.transport.requests(method: .get, path: "/api/v1/me/attempts").last)
        )
        XCTAssertEqual(query["page"], "0")
        XCTAssertEqual(query["size"], "20")
        XCTAssertNil(query["q"])
        XCTAssertNil(query["kind"])
        XCTAssertNil(query["scope"])
        XCTAssertNil(query["from"])
        XCTAssertNil(query["to"])
        XCTAssertNil(query["examId"])

        let nodes = CatalogViewInspector.collectNodes(in: controller.view, window: window)
        XCTAssertFalse(nodes.contains(where: { $0.identifier == "history.filter.clearButton" }), "Clear button must disappear when reset to empty")

        await feed.selectScope(.subject)
        router.switchTab(.home)
        CatalogViewInspector.settle(window: window, seconds: 0.06)
        stack.transport.resetRecordedRequests()

        router.switchTab(.history, reload: true)
        try await MyExamsTestStackFactory.waitUntil(window: window) {
            stack.transport.callCount(method: .get, path: "/api/v1/me/attempts") >= 1
        }
        XCTAssertEqual(feed.filter.scope, .subject)
        let reloadQuery = MyExamsTestStackFactory.queryItems(
            from: try XCTUnwrap(stack.transport.requests(method: .get, path: "/api/v1/me/attempts").last)
        )
        XCTAssertEqual(reloadQuery["scope"], "SUBJECT")
    }

    func testRowActions() throws {
        let inProgressJSON = try MyExamsTestFixtures.cloneHistoryItemJSON(templateState: "IN_PROGRESS") { item in
            item["attemptId"] = "att-ip-1"
            item["action"] = "CONTINUE"
        }
        let scoredJSON = try MyExamsTestFixtures.cloneHistoryItemJSON(templateState: "SCORED") { item in
            item["attemptId"] = "att-sc-1"
            item["action"] = "VIEW_RESULT"
        }
        let page = try MyExamsTestFixtures.decodeHistoryPage(
            from: try MyExamsTestFixtures.makeHistoryPagePayload(items: [inProgressJSON, scoredJSON])
        )
        let inProgressItem = page.items[0]
        let scoredItem = page.items[1]

        let clock = MyExamsManualClock()
        let router = AppRouter(initialDestination: .history)
        let nav = HistoryNavigationHandler(router: router, clock: clock)

        nav.handleTitleTap(for: inProgressItem)
        XCTAssertNil(router.presentedAttempt)
        XCTAssertTrue(router.path(for: .history).isEmpty)

        nav.handleContinue(attemptId: inProgressItem.attemptId)
        nav.handleContinue(attemptId: inProgressItem.attemptId)
        XCTAssertEqual(router.presentedAttempt?.attemptId, "att-ip-1")
        router.closeAttempt()

        clock.advance(by: 1.0)
        nav.handleTitleTap(for: scoredItem)
        nav.handleViewResult(attemptId: scoredItem.attemptId)
        XCTAssertEqual(router.path(for: .history), [.result(attemptId: "att-sc-1")])
    }
}
