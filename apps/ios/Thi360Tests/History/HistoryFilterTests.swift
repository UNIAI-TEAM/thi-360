import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class HistoryFilterTests: XCTestCase {
    func testKindScope() async throws {
        let hsaRow = try MyExamsTestFixtures.cloneHistoryItemJSON(templateState: "SCORED") { item in
            item["attemptId"] = "att-hsa-filter"
            item["examName"] = "Đề HSA lọc"
            item["examKindCode"] = "HSA"
        }
        let hsaPage = try MyExamsTestFixtures.makeHistoryPagePayload(items: [hsaRow], page: 0, size: 20, totalItems: 1, totalPages: 1)
        let defaultPage = try MyExamsTestFixtures.loadIndexedData(operationId: Operations.listHistory.id, status: 200, variant: "")

        let stack = try MyExamsTestStackFactory.makeStack { transport in
            try MyExamsTestStackFactory.registerDefaultMyExamsAndHistoryFixtures(on: transport)
            transport.register(method: .get, path: "/api/v1/me/attempts") { request in
                let query = MyExamsTestStackFactory.queryItems(from: request)
                if query["kind"] == "HSA" {
                    return ContractFakeTransport.jsonResponse(statusCode: 200, data: hsaPage)
                }
                return ContractFakeTransport.jsonResponse(statusCode: 200, data: defaultPage)
            }
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let feed = HistoryFeed(
            accountId: "acc-1",
            api: stack.historyAPI,
            optionsCache: stack.optionsCache,
            clock: stack.clock
        )
        await feed.loadOnAppear(resetFilters: false, reloadToken: 0)

        await feed.selectKind("HSA")
        let kindQuery = MyExamsTestStackFactory.queryItems(
            from: try XCTUnwrap(stack.transport.requests(method: .get, path: "/api/v1/me/attempts").last)
        )
        XCTAssertEqual(kindQuery["kind"], "HSA")
        XCTAssertEqual(kindQuery["page"], "0")
        XCTAssertEqual(kindQuery["size"], "20")
        XCTAssertEqual(feed.currentPage?.items.map(\.examName), ["Đề HSA lọc"])

        await feed.selectScope(.part)
        let partQuery = MyExamsTestStackFactory.queryItems(
            from: try XCTUnwrap(stack.transport.requests(method: .get, path: "/api/v1/me/attempts").last)
        )
        XCTAssertEqual(partQuery["scope"], "PART")

        await feed.selectScope(nil)
        let allScopeQuery = MyExamsTestStackFactory.queryItems(
            from: try XCTUnwrap(stack.transport.requests(method: .get, path: "/api/v1/me/attempts").last)
        )
        XCTAssertNil(allScopeQuery["scope"])

        let optFallbackVi = CatalogOptionItem(code: "CUSTOM1", nameVi: "Kỳ thi mẫu", nameEn: "")
        let optFallbackCode = CatalogOptionItem(code: "CUSTOM2", nameVi: "", nameEn: "")
        XCTAssertEqual(HistoryRowFormat.resolveOptionLabel(optFallbackVi, locale: .en), "Kỳ thi mẫu")
        XCTAssertEqual(HistoryRowFormat.resolveOptionLabel(optFallbackCode, locale: .en), "CUSTOM2")

        let error500 = try MyExamsTestFixtures.makeInternalServerError500Data()
        let failOptStack = try MyExamsTestStackFactory.makeStack { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/attempts", data: defaultPage)
            transport.registerJSON(method: .get, path: "/api/v1/catalog/options", statusCode: 500, data: error500)
        }
        defer { UserDefaults(suiteName: failOptStack.defaultsSuiteName)?.removePersistentDomain(forName: failOptStack.defaultsSuiteName) }

        let failOptFeed = HistoryFeed(
            accountId: "acc-fail-opt",
            api: failOptStack.historyAPI,
            optionsCache: failOptStack.optionsCache,
            clock: failOptStack.clock
        )
        await failOptFeed.loadOnAppear(resetFilters: false, reloadToken: 0)
        XCTAssertTrue(failOptFeed.examKindOptions.isEmpty)
        XCTAssertFalse(failOptFeed.currentPage?.items.isEmpty ?? true)
    }

    func testDateRange() async throws {
        let invalidDate422 = try MyExamsTestFixtures.loadIndexedData(
            operationId: Operations.listHistory.id,
            status: 422,
            variant: "invalid-date-filter"
        )
        let okPage = try MyExamsTestFixtures.loadIndexedData(operationId: Operations.listHistory.id, status: 200, variant: "")

        for tzIdentifier in ["Pacific/Honolulu", "Pacific/Kiritimati"] {
            try await MyExamsTestStackFactory.withDefaultTimeZone(tzIdentifier) {
                let stack = try MyExamsTestStackFactory.makeStack { transport in
                    try MyExamsTestStackFactory.registerDefaultMyExamsAndHistoryFixtures(on: transport)
                }
                defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

                let feed = HistoryFeed(accountId: "acc-tz", api: stack.historyAPI, optionsCache: stack.optionsCache, clock: stack.clock)
                await feed.loadOnAppear(resetFilters: false, reloadToken: 0)

                var cal = Calendar(identifier: .gregorian)
                cal.timeZone = TimeZone(identifier: tzIdentifier)!
                let fromDate = HistoryCalendarDate(year: 2026, month: 11, day: 1)
                let toDate = HistoryCalendarDate(year: 2026, month: 11, day: 20)

                let fromRoundTripped = HistoryCalendarDate(date: fromDate.toPickerDate(calendar: cal), calendar: cal)
                let toRoundTripped = HistoryCalendarDate(date: toDate.toPickerDate(calendar: cal), calendar: cal)

                await feed.selectFromDate(fromRoundTripped)
                await feed.selectToDate(toRoundTripped)

                let query = MyExamsTestStackFactory.queryItems(
                    from: try XCTUnwrap(stack.transport.requests(method: .get, path: "/api/v1/me/attempts").last)
                )
                XCTAssertEqual(query["from"], "2026-11-01", "from must be 2026-11-01 under \(tzIdentifier)")
                XCTAssertEqual(query["to"], "2026-11-20", "to must be 2026-11-20 under \(tzIdentifier)")

                let countBeforeInvalid = stack.transport.callCount(method: .get, path: "/api/v1/me/attempts")
                await feed.selectFromDate(HistoryCalendarDate(year: 2026, month: 11, day: 25))
                XCTAssertEqual(feed.filter.from, fromDate)
                XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/me/attempts"), countBeforeInvalid)

                await feed.selectToDate(HistoryCalendarDate(year: 2026, month: 10, day: 15))
                XCTAssertEqual(feed.filter.to, toDate)
                XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/me/attempts"), countBeforeInvalid)

                await feed.selectFromDate(nil)
                XCTAssertNil(feed.filter.from)
                XCTAssertEqual(feed.filter.to, toDate)

                await feed.selectToDate(nil)
                XCTAssertNil(feed.filter.to)
            }
        }

        let stack422 = try MyExamsTestStackFactory.makeStack { transport in
            try MyExamsTestStackFactory.registerDefaultMyExamsAndHistoryFixtures(on: transport)
            transport.enqueue(method: .get, path: "/api/v1/me/attempts", handlers: [
                { _ in ContractFakeTransport.jsonResponse(statusCode: 422, data: invalidDate422) },
                { _ in ContractFakeTransport.jsonResponse(statusCode: 200, data: okPage) },
                { _ in ContractFakeTransport.jsonResponse(statusCode: 422, data: invalidDate422) }
            ])
        }
        defer { UserDefaults(suiteName: stack422.defaultsSuiteName)?.removePersistentDomain(forName: stack422.defaultsSuiteName) }

        let feed422 = HistoryFeed(accountId: "acc-422", api: stack422.historyAPI, optionsCache: stack422.optionsCache, clock: stack422.clock)
        await feed422.loadOnAppear(resetFilters: false, reloadToken: 0)
        XCTAssertEqual(feed422.loadState, .failed(kind: .server, canRetry: true, retryAfter: nil))

        await feed422.retry()
        if case .loaded(_, false) = feed422.loadState {} else { XCTFail("Expected loaded state") }

        await feed422.selectFromDate(HistoryCalendarDate(year: 2026, month: 11, day: 1))
        if case .loaded(_, true) = feed422.loadState {} else {
            XCTFail("Expected stale banner on 422 INVALID_DATE_FILTER when already loaded, got \(feed422.loadState)")
        }
    }

    func testClearAndRetain() async throws {
        let stack = try MyExamsTestStackFactory.makeStack { transport in
            try MyExamsTestStackFactory.registerDefaultMyExamsAndHistoryFixtures(on: transport)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let router = AppRouter(initialDestination: .history)
        defer { HistoryFeedRegistry.shared.clear(for: router) }

        var reportedFrames: [String: CGRect] = [:]
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
        .environment(\.myExamsLayoutFrameObserver, { id, frame in reportedFrames[id] = frame })

        let (window, controller) = CatalogViewInspector.host(
            shell,
            size: CGSize(width: 466, height: 678),
            sizeClass: .compact,
            settleSeconds: 0.2
        )
        defer { window.isHidden = true }

        let feed = try XCTUnwrap(HistoryFeedRegistry.shared.existingFeed(for: router))
        try await MyExamsTestStackFactory.waitUntil(window: window) {
            if case .loaded = feed.loadState { return true }
            return false
        }

        XCTAssertTrue(feed.filter.isEmpty)
        XCTAssertNil(reportedFrames["history.filter.clearButton"])

        feed.searchText = "hsa"
        await feed.submitSearchImmediately()
        await feed.selectKind("HSA")
        await feed.selectScope(.subject)
        await feed.selectFromDate(HistoryCalendarDate(year: 2026, month: 11, day: 1))
        CatalogViewInspector.settle(window: window, seconds: 0.1)

        XCTAssertFalse(feed.filter.isEmpty)
        XCTAssertNotNil(reportedFrames["history.filter.clearButton"])

        controller.traitOverrides.horizontalSizeClass = .regular
        window.frame = CGRect(origin: .zero, size: CGSize(width: 669, height: 951))
        CatalogViewInspector.settle(window: window, seconds: 0.15)
        XCTAssertEqual(feed.filter.scope, .subject)
        XCTAssertEqual(feed.filter.kind, "HSA")
        XCTAssertEqual(feed.searchText, "hsa")

        router.switchTab(.home)
        CatalogViewInspector.settle(window: window, seconds: 0.08)
        router.switchTab(.history)
        CatalogViewInspector.settle(window: window, seconds: 0.12)
        XCTAssertEqual(feed.filter.scope, .subject)
        XCTAssertEqual(feed.filter.kind, "HSA")
        XCTAssertEqual(feed.filter.from, HistoryCalendarDate(year: 2026, month: 11, day: 1))

        stack.transport.resetRecordedRequests()
        router.openResult(attemptId: "att-1")
        CatalogViewInspector.settle(window: window, seconds: 0.06)
        router.back()
        try await MyExamsTestStackFactory.waitUntil(window: window) {
            stack.transport.callCount(method: .get, path: "/api/v1/me/attempts") >= 1
        }
        let returnQuery = MyExamsTestStackFactory.queryItems(
            from: try XCTUnwrap(stack.transport.requests(method: .get, path: "/api/v1/me/attempts").last)
        )
        XCTAssertEqual(returnQuery["scope"], "SUBJECT")

        stack.transport.resetRecordedRequests()
        await feed.clearFilters()
        XCTAssertTrue(feed.filter.isEmpty)
        XCTAssertEqual(feed.searchText, "")
        let clearedQuery = MyExamsTestStackFactory.queryItems(
            from: try XCTUnwrap(stack.transport.requests(method: .get, path: "/api/v1/me/attempts").last)
        )
        XCTAssertEqual(clearedQuery["page"], "0")
        XCTAssertEqual(clearedQuery["size"], "20")
        XCTAssertNil(clearedQuery["q"])
        XCTAssertNil(clearedQuery["kind"])
        XCTAssertNil(clearedQuery["scope"])
        XCTAssertNil(clearedQuery["from"])
        XCTAssertNil(clearedQuery["to"])
        XCTAssertNil(clearedQuery["examId"])
    }
}
