import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class MyExamsAccessibilityTests: XCTestCase {
    private let phoneSize = CGSize(width: 390, height: 844)
    private let scales: [(label: String, uiCategory: UIContentSizeCategory, dynamicType: DynamicTypeSize)] = [
        ("130%", .extraExtraExtraLarge, .xxxLarge),
        ("200%", .accessibilityLarge, .accessibility2)
    ]

    func testMyExamsAndHistoryPassDynamicType130And200TouchTargetsVoiceOverAndStableIdentifiers() async throws {
        let myExamsData = try MyExamsTestFixtures.loadIndexedData(operationId: Operations.listMyExams.id, status: 200, variant: "")
        let cards = try MyExamsTestFixtures.decodeMyExamCards(from: myExamsData)
        let historyData = try MyExamsTestFixtures.loadIndexedData(operationId: Operations.listHistory.id, status: 200, variant: "")
        let historyResp = try MyExamsTestFixtures.decodeHistoryPage(from: historyData)
        let loadedTime = ISO8601DateFormatter().date(from: "2026-11-20T02:05:00Z")!

        for card in cards {
            if let ent = card.entitlementId, !ent.isEmpty {
                XCTAssertEqual(card.id, ent)
            } else if let order = card.trimmedPendingOrderCode {
                XCTAssertEqual(card.id, "order:\(order):\(card.code)")
            }
        }
        for item in historyResp.items {
            XCTAssertEqual(item.id, item.attemptId)
        }

        let myExamsRouter = AppRouter(initialDestination: .myExams)
        let myExamsFeed = MyExamsFeed(accountId: "acc-1", api: PreviewEmptyMyExamsAPI(), initialFullName: "Nguyễn Minh Anh", initialUsername: "hv01")
        myExamsFeed.setLoadedStateForTesting(items: cards, lastLoadedAt: loadedTime, stale: true)

        let historyRouter = AppRouter(initialDestination: .history)
        let historyFeed = HistoryFeed(accountId: "acc-1", api: PreviewEmptyHistoryAPI())
        let page = HistoryPage(items: historyResp.items, page: 0, totalItems: historyResp.totalItems, totalPages: 1, hasMore: false, isLoadingMore: false)
        historyFeed.setLoadedStateForTesting(
            page: page,
            lastLoadedAt: loadedTime,
            stale: true,
            filter: HistoryFilter(
                q: "hsa",
                kind: "HSA",
                scope: .part,
                from: HistoryCalendarDate(year: 2026, month: 11, day: 1),
                to: HistoryCalendarDate(year: 2026, month: 11, day: 20)
            )
        )

        for scale in scales {
            try verifyAccessibility(
                screen: "MyExams@\(scale.label)",
                scale: scale,
                expectedControlIDs: [
                    "myExams.exploreExamsButton",
                    "myExams.checkingCard.viewOrder.DH261126000001",
                    "myExams.checkingCard.reload.EXAM-DEMO-01"
                ],
                view: MyExamsScreen(
                    feed: myExamsFeed,
                    router: myExamsRouter,
                    locale: .vi,
                    showsBrandHeader: true,
                    supportContact: SupportContact(phone: "1900 1234", email: "hotro@thi360.vn", hours: "8:00–21:00")
                )
            )

            try verifyAccessibility(
                screen: "History@\(scale.label)",
                scale: scale,
                expectedControlIDs: [
                    "history.filter.openSheetButton",
                    "history.filter.dateChipButton",
                    "history.filter.clearButton"
                ],
                view: HistoryScreen(
                    feed: historyFeed,
                    router: historyRouter,
                    resetFilters: false,
                    locale: .vi,
                    showsBrandHeader: true
                )
            )

            try verifyAccessibility(
                screen: "HistoryFilterSheet@\(scale.label)",
                scale: scale,
                expectedControlIDs: [],
                view: HistoryFilterSheetView(
                    feed: historyFeed,
                    locale: .vi,
                    onDismiss: {}
                )
            )
        }
    }

    private func verifyAccessibility<Content: View>(
        screen: String,
        scale: (label: String, uiCategory: UIContentSizeCategory, dynamicType: DynamicTypeSize),
        expectedControlIDs: [String],
        view: Content
    ) throws {
        var reportedFrames: [String: CGRect] = [:]
        let wrapped = view
            .environment(\.brandPalette, .defaultLight)
            .environment(\.statusPalette, .light)
            .environment(\.shellWidth, .narrow)
            .environment(\.dynamicTypeSize, scale.dynamicType)
            .environment(\.myExamsLayoutFrameObserver, { id, frame in reportedFrames[id] = frame })

        let (window, controller) = CatalogViewInspector.host(
            wrapped,
            size: phoneSize,
            uiCategory: scale.uiCategory,
            settleSeconds: 0.12
        )
        defer { window.isHidden = true }

        for controlID in expectedControlIDs {
            let frame = try XCTUnwrap(reportedFrames[controlID], "\(screen) expected control '\(controlID)' to render")
            XCTAssertGreaterThanOrEqual(frame.width, 43.5, "\(screen) '\(controlID)' width \(frame.width)pt < 44pt")
            XCTAssertGreaterThanOrEqual(frame.height, 43.5, "\(screen) '\(controlID)' height \(frame.height)pt < 44pt")
            XCTAssertGreaterThanOrEqual(frame.minX, -1.0, "\(screen) '\(controlID)' overflows left")
            XCTAssertLessThanOrEqual(frame.maxX, phoneSize.width + 1.0, "\(screen) '\(controlID)' overflows right (\(frame.maxX)pt)")
        }

        for (id, frame) in reportedFrames where frame.width > 0 && frame.height > 0 {
            XCTAssertGreaterThanOrEqual(frame.minX, -1.0, "\(screen) '\(id)' overflows left")
            XCTAssertLessThanOrEqual(frame.maxX, phoneSize.width + 1.0, "\(screen) '\(id)' overflows right (\(frame.maxX)pt)")
        }

        let scrollViews = RecoveryViewTestHelper.findViews(ofType: UIScrollView.self, in: controller.view)
        XCTAssertFalse(scrollViews.isEmpty, "\(screen) must be vertically scrollable")
        for scrollView in scrollViews where scrollView.contentSize.width > 0 {
            XCTAssertLessThanOrEqual(
                scrollView.contentSize.width,
                phoneSize.width + 1.0,
                "\(screen) UIScrollView horizontal overflow \(scrollView.contentSize.width)pt"
            )
        }

        let (_, png) = try CatalogViewInspector.renderPNG(window: window, size: phoneSize)
        XCTAssertGreaterThan(png.count, 2048, "\(screen) rendered snapshot unexpectedly empty")
    }
}
