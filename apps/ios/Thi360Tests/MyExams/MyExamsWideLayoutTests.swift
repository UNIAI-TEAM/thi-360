import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class MyExamsWideLayoutTests: XCTestCase {
    func testRegularWidthMultiColumnOrderAndFullSpanOnIPadAndIPhoneDuo() async throws {
        XCTAssertTrue(MyExamsRoutes.myExamsSpansBothPanesOnPhoneBook)
        XCTAssertTrue(MyExamsRoutes.historySpansBothPanesOnPhoneBook)

        let items = try (1...4).map { idx in
            try MyExamsTestFixtures.cloneMyExamCardJSON(templateState: "CAN_START") { item in
                item["entitlementId"] = "ent-wide-\(idx)"
                item["code"] = "WIDE-\(idx)"
                item["name"] = "Đề cột rộng \(idx)"
                item["bundles"] = []
            }
        }
        let payload = try MyExamsTestFixtures.makeMyExamsPayload(items: items)

        let stack = try MyExamsTestStackFactory.makeStack { transport in
            try MyExamsTestStackFactory.registerDefaultMyExamsAndHistoryFixtures(on: transport)
            transport.registerJSON(method: .get, path: "/api/v1/me/exams", data: payload)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let feed = MyExamsFeed(
            accountId: "acc-1",
            api: stack.myExamsAPI,
            clock: stack.clock,
            retrySleeper: RecordingRetrySleeper(),
            initialFullName: "An",
            initialUsername: "an"
        )
        await feed.loadOnAppear()

        var reportedFrames: [String: CGRect] = [:]
        let router = AppRouter(initialDestination: .myExams)
        let screen = MyExamsScreen(feed: feed, router: router, locale: .vi, showsBrandHeader: false)
            .environment(\.brandPalette, .defaultLight)
            .environment(\.statusPalette, .light)
            .environment(\.shellWidth, .twoColumn)
            .environment(\.myExamsLayoutFrameObserver, { id, frame in reportedFrames[id] = frame })

        let (gridWindow, _) = CatalogViewInspector.host(
            screen,
            size: CGSize(width: 900, height: 1100),
            sizeClass: .regular,
            settleSeconds: 0.15
        )
        defer { gridWindow.isHidden = true }

        let f1 = try XCTUnwrap(reportedFrames["myExams.card.title.WIDE-1"], "Card 1 must lay out")
        let f2 = try XCTUnwrap(reportedFrames["myExams.card.title.WIDE-2"], "Card 2 must lay out")
        let f3 = try XCTUnwrap(reportedFrames["myExams.card.title.WIDE-3"], "Card 3 must lay out")
        let f4 = try XCTUnwrap(reportedFrames["myExams.card.title.WIDE-4"], "Card 4 must lay out")

        XCTAssertLessThan(f1.minX, f2.minX, "Card 1 must be left of Card 2 in row 1")
        XCTAssertEqual(f1.minY, f2.minY, accuracy: 8.0, "Card 1 and Card 2 must share row 1")
        XCTAssertLessThan(f3.minX, f4.minX, "Card 3 must be left of Card 4 in row 2")
        XCTAssertEqual(f3.minY, f4.minY, accuracy: 8.0, "Card 3 and Card 4 must share row 2")
        XCTAssertLessThan(f1.maxY, f3.minY, "Row 1 must precede Row 2 vertically")

        let padRouter = AppRouter(initialDestination: .myExams)
        defer {
            MyExamsFeedRegistry.shared.clear(for: padRouter)
            HistoryFeedRegistry.shared.clear(for: padRouter)
        }
        let padShell = AdaptiveShellView(
            router: padRouter,
            preferences: stack.preferences,
            idiom: .pad,
            sessionStore: stack.sessionStore
        )
        .environment(\.sessionStore, stack.sessionStore)
        .environment(\.catalogOptionsCache, stack.optionsCache)
        .environment(\.brandPalette, .defaultLight)
        .environment(\.statusPalette, .light)

        let (padWindow, padController) = CatalogViewInspector.host(
            padShell,
            size: CGSize(width: 1180, height: 820),
            sizeClass: .regular,
            settleSeconds: 0.2
        )
        defer { padWindow.isHidden = true }

        let padText = CatalogViewInspector.allStrings(in: padController.view, window: padWindow).joined(separator: "\n")
        XCTAssertFalse(padText.contains("Chọn một bài thi để xem chi tiết."), "MyExams on iPad must not show a detail split placeholder")

        let duoRouter = AppRouter(initialDestination: .myExams)
        defer {
            MyExamsFeedRegistry.shared.clear(for: duoRouter)
            HistoryFeedRegistry.shared.clear(for: duoRouter)
        }
        let duoShell = AdaptiveShellView(
            router: duoRouter,
            preferences: stack.preferences,
            idiom: .phone,
            sessionStore: stack.sessionStore
        )
        .environment(\.sessionStore, stack.sessionStore)
        .environment(\.catalogOptionsCache, stack.optionsCache)
        .environment(\.brandPalette, .defaultLight)
        .environment(\.statusPalette, .light)

        let duoSize = CGSize(width: 669, height: 951)
        let (duoWindow, duoController) = CatalogViewInspector.host(
            duoShell,
            size: duoSize,
            sizeClass: .regular,
            settleSeconds: 0.2
        )
        defer { duoWindow.isHidden = true }

        let myExamsNavs = MyExamsTestStackFactory.findNavigationControllers(in: duoController)
        XCTAssertEqual(myExamsNavs.count, 1, "MyExams on iPhone Duo unfolded must span both panes with 1 NavigationStack")
        XCTAssertEqual(try XCTUnwrap(myExamsNavs.first?.view.bounds.width), duoSize.width, accuracy: 2.0)

        duoRouter.switchTab(.history)
        CatalogViewInspector.settle(window: duoWindow, seconds: 0.2)
        let historyNavs = MyExamsTestStackFactory.findNavigationControllers(in: duoController)
        XCTAssertEqual(historyNavs.count, 1, "History on iPhone Duo unfolded must span both panes with 1 NavigationStack")
        XCTAssertEqual(try XCTUnwrap(historyNavs.first?.view.bounds.width), duoSize.width, accuracy: 2.0)
    }
}
