import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class MyExamsViewTests: XCTestCase {
    func testFirstOpen() async throws {
        let meData = try MyExamsTestFixtures.makeMePayload(fullName: "   Nguyễn Minh Anh   ", username: "hv01")
        let stack = try MyExamsTestStackFactory.makeStack { transport in
            try MyExamsTestStackFactory.registerDefaultMyExamsAndHistoryFixtures(on: transport)
            transport.registerJSON(method: .get, path: "/api/v1/me", data: meData)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let router = AppRouter(initialDestination: .myExams)
        let feed = MyExamsFeed(
            accountId: "acc-1",
            api: stack.myExamsAPI,
            connectivityMonitor: stack.connectivityMonitor,
            clock: stack.clock,
            retrySleeper: RecordingRetrySleeper(),
            initialFullName: nil,
            initialUsername: "hv01"
        )

        let screen = MyExamsScreen(feed: feed, router: router, locale: .vi, showsBrandHeader: true)
        let (window, controller) = CatalogViewInspector.host(
            screen
                .environment(\.brandPalette, .defaultLight)
                .environment(\.statusPalette, .light),
            size: CGSize(width: 390, height: 844),
            settleSeconds: 0.15
        )
        defer { window.isHidden = true }

        try await MyExamsTestStackFactory.waitUntil(window: window) {
            if case .loaded = feed.loadState {
                return feed.greetingDisplayName == "Nguyễn Minh Anh"
            }
            return false
        }
        CatalogViewInspector.settle(window: window, seconds: 0.08)

        let calls = stack.transport.requests(method: .get, path: "/api/v1/me/exams")
        XCTAssertEqual(calls.count, 1, "Opening MyExams must call GET /api/v1/me/exams strictly once")
        XCTAssertEqual(calls.first?.header(.authorization), "Bearer test-access-token")

        let allText = screen.renderedStrings.joined(separator: "\n")
        XCTAssertFalse(allText.contains("Chưa có nội dung"), "Placeholder text must no longer appear")
        XCTAssertTrue(allText.contains("Bài thi của tôi"))
        XCTAssertTrue(allText.contains("Chào Nguyễn Minh Anh."))
        XCTAssertFalse(allText.contains("Chào    Nguyễn Minh Anh"), "Full name must be trimmed")

        XCTAssertFalse(RecoveryViewTestHelper.findViews(ofType: UIScrollView.self, in: controller.view).isEmpty)
        let (_, png) = try CatalogViewInspector.renderPNG(window: window, size: CGSize(width: 390, height: 844))
        XCTAssertGreaterThan(png.count, 4096)
    }

    func testGreetingFallback() async throws {
        let meData = try MyExamsTestFixtures.makeMePayload(fullName: "   ", username: "hv01")
        let stack = try MyExamsTestStackFactory.makeStack { transport in
            try MyExamsTestStackFactory.registerDefaultMyExamsAndHistoryFixtures(on: transport)
            transport.registerJSON(method: .get, path: "/api/v1/me", data: meData)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let router = AppRouter(initialDestination: .myExams)
        let feed = MyExamsFeed(
            accountId: "acc-1",
            api: stack.myExamsAPI,
            connectivityMonitor: stack.connectivityMonitor,
            clock: stack.clock,
            retrySleeper: RecordingRetrySleeper(),
            initialFullName: "   ",
            initialUsername: "hv01"
        )

        let screen = MyExamsScreen(feed: feed, router: router, locale: .vi, showsBrandHeader: false)
        let (window, _) = CatalogViewInspector.host(
            screen
                .environment(\.brandPalette, .defaultLight)
                .environment(\.statusPalette, .light),
            settleSeconds: 0.15
        )
        defer { window.isHidden = true }

        try await MyExamsTestStackFactory.waitUntil(window: window) {
            if case .loaded = feed.loadState { return true }
            return false
        }
        let allText = screen.renderedStrings.joined(separator: "\n")
        XCTAssertTrue(allText.contains("Chào hv01."), "Blank fullName must fall back to username 'hv01'")
    }

    func testEnglish() async throws {
        let oneCard = try MyExamsTestFixtures.cloneMyExamCardJSON(templateState: "CAN_START") { item in
            item["entitlementId"] = "ent-en-1"
            item["code"] = "EN-EXAM-1"
            item["name"] = "English Exam 1"
            item["bundles"] = []
        }
        let threeCards = try (1...3).map { idx in
            try MyExamsTestFixtures.cloneMyExamCardJSON(templateState: "CAN_START") { item in
                item["entitlementId"] = "ent-en-\(idx)"
                item["code"] = "EN-EXAM-\(idx)"
                item["name"] = "English Exam \(idx)"
                item["bundles"] = []
            }
        }
        let onePayload = try MyExamsTestFixtures.makeMyExamsPayload(items: [oneCard])
        let threePayload = try MyExamsTestFixtures.makeMyExamsPayload(items: threeCards)

        let stack = try MyExamsTestStackFactory.makeStack(locale: .en) { transport in
            transport.enqueue(method: .get, path: "/api/v1/me/exams", handlers: [
                { _ in ContractFakeTransport.jsonResponse(statusCode: 200, data: onePayload) },
                { _ in ContractFakeTransport.jsonResponse(statusCode: 200, data: threePayload) }
            ])
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let router = AppRouter(initialDestination: .myExams)
        let feed = MyExamsFeed(
            accountId: "acc-1",
            api: stack.myExamsAPI,
            clock: stack.clock,
            retrySleeper: RecordingRetrySleeper(),
            initialFullName: "Alice",
            initialUsername: "alice"
        )

        let screen = MyExamsScreen(feed: feed, router: router, locale: .en, showsBrandHeader: false)
        let (window, _) = CatalogViewInspector.host(
            screen
                .environment(\.brandPalette, .defaultLight)
                .environment(\.statusPalette, .light),
            settleSeconds: 0.12
        )
        defer { window.isHidden = true }

        try await MyExamsTestStackFactory.waitUntil(window: window) {
            if case let .loaded(items, _) = feed.loadState { return items.count == 1 }
            return false
        }
        let oneText = screen.renderedStrings.joined(separator: "\n")
        XCTAssertTrue(oneText.contains("My exams"))
        XCTAssertTrue(oneText.contains("Hello Alice."))
        XCTAssertTrue(oneText.contains("1 exam"))
        XCTAssertTrue(oneText.contains("Explore exams"))

        await feed.reloadKeepingExisting()
        CatalogViewInspector.settle(window: window, seconds: 0.08)
        let threeText = screen.renderedStrings.joined(separator: "\n")
        XCTAssertTrue(threeText.contains("3 exams"))
    }

    func testGroups() async throws {
        let bundleX = try MyExamsTestFixtures.cloneBundleRefTemplate(bundleId: "bundle-x", code: "BX-01", name: "Bộ đề X")
        let bundleY = try MyExamsTestFixtures.cloneBundleRefTemplate(bundleId: "bundle-y", code: "BY-02", name: "Bộ đề Y")

        let cardA = try MyExamsTestFixtures.cloneMyExamCardJSON(templateState: "CHECKING") { item in
            item["code"] = "CARD-A"
            item["name"] = "Đề A kiểm tra"
            item["pendingOrderCode"] = "DH001"
            item["bundles"] = []
        }
        let cardB = try MyExamsTestFixtures.cloneMyExamCardJSON(templateState: "CAN_START") { item in
            item["entitlementId"] = "ent-b"
            item["code"] = "CARD-B"
            item["name"] = "Đề B thuộc X"
            item["bundles"] = [bundleX]
        }
        let cardC = try MyExamsTestFixtures.cloneMyExamCardJSON(templateState: "CAN_START") { item in
            item["entitlementId"] = "ent-c"
            item["code"] = "CARD-C"
            item["name"] = "Đề C mua lẻ"
            item["bundles"] = []
        }
        let cardD = try MyExamsTestFixtures.cloneMyExamCardJSON(templateState: "CAN_START") { item in
            item["entitlementId"] = "ent-d"
            item["code"] = "CARD-D"
            item["name"] = "Đề D thuộc Y"
            item["bundles"] = [bundleY]
        }
        let cardE = try MyExamsTestFixtures.cloneMyExamCardJSON(templateState: "CAN_START") { item in
            item["entitlementId"] = "ent-e"
            item["code"] = "CARD-E"
            item["name"] = "Đề E thuộc X"
            item["bundles"] = [bundleX]
        }

        let payload = try MyExamsTestFixtures.makeMyExamsPayload(items: [cardA, cardB, cardC, cardD, cardE])
        let stack = try MyExamsTestStackFactory.makeStack { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/exams", data: payload)
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

        let screen = MyExamsScreen(feed: feed, router: router, locale: .vi, showsBrandHeader: false)
        let (window, _) = CatalogViewInspector.host(
            screen
                .environment(\.brandPalette, .defaultLight)
                .environment(\.statusPalette, .light),
            size: CGSize(width: 390, height: 1200),
            settleSeconds: 0.12
        )
        defer { window.isHidden = true }

        let text = screen.renderedStrings.joined(separator: "\n")
        XCTAssertTrue(text.contains("Bài đã mua"))
        XCTAssertTrue(text.contains("5 bài"))
        XCTAssertTrue(text.contains("Đang kiểm tra"))
        XCTAssertTrue(text.contains("Bộ đề X"))
        XCTAssertTrue(text.contains("Bộ đề Y"))
        XCTAssertTrue(text.contains("Bài mua lẻ"))
        XCTAssertTrue(text.contains("1 bài"))
        XCTAssertTrue(text.contains("Đề A kiểm tra"))
        XCTAssertTrue(text.contains("Đề B thuộc X"))
        XCTAssertTrue(text.contains("Đề E thuộc X"))
        XCTAssertTrue(text.contains("Đề D thuộc Y"))
        XCTAssertTrue(text.contains("Đề C mua lẻ"))

        let (_, png) = try CatalogViewInspector.renderPNG(window: window, size: CGSize(width: 390, height: 1200))
        XCTAssertGreaterThan(png.count, 4096)
    }
}
