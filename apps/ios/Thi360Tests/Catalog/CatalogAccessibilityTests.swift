import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class CatalogAccessibilityTests: XCTestCase {
    private let phoneSize = CGSize(width: 390, height: 844)
    private let scales: [(label: String, uiCategory: UIContentSizeCategory, dynamicType: DynamicTypeSize)] = [
        ("130%", .extraExtraExtraLarge, .xxxLarge),
        ("200%", .accessibilityLarge, .accessibility2)
    ]

    func testCatalogDetailAndStatusScreensPassDynamicType130And200TargetsAndVoiceOver() async throws {
        let examData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.getStorefrontProduct.id,
            status: 200,
            variant: "exam"
        )
        let bundleData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.getStorefrontProduct.id,
            status: 200,
            variant: "bundle"
        )

        let stack = try CatalogTestStackFactory.makeStack { transport in
            try CatalogTestStackFactory.registerDefaultStorefrontFixtures(on: transport)
        }
        defer {
            UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName)
        }

        let feed = CatalogFeed(accountId: "acc-1", api: stack.api)
        await feed.loadInitialIfNeeded()

        let decoder = MobileAPIClient.makeJSONDecoder()
        let examDetail = CatalogAPI.mapProductDetail(
            try decoder.decode(Components.Schemas.ProductDetail.self, from: examData),
            supplementalJSONData: examData
        )
        let bundleDetail = CatalogAPI.mapProductDetail(
            try decoder.decode(Components.Schemas.ProductDetail.self, from: bundleData),
            supplementalJSONData: bundleData
        )
        let router = AppRouter(initialDestination: .home)

        for scale in scales {
            try verifyAccessibility(
                screen: "Catalog@\(scale.label)",
                scale: scale,
                view: CatalogHomeView(
                    feed: feed,
                    router: router,
                    locale: .vi,
                    showsBrandHeader: true
                )
            )

            try verifyAccessibility(
                screen: "CatalogFilterSheet@\(scale.label)",
                scale: scale,
                view: CatalogFilterSheetView(feed: feed, locale: .vi, onDismiss: {})
            )

            try verifyAccessibility(
                screen: "ExamDetail@\(scale.label)",
                scale: scale,
                view: ScrollView {
                    ExamDetailView(
                        product: examDetail,
                        accessCell: .ready(.none(code: examDetail.code)),
                        locale: .vi,
                        supportContact: .empty,
                        onAccessButtonTap: { _ in },
                        onReloadAccess: {}
                    )
                    .padding(16)
                }
            )

            try verifyAccessibility(
                screen: "BundleDetail@\(scale.label)",
                scale: scale,
                view: ScrollView {
                    BundleDetailView(
                        product: bundleDetail,
                        sellable: true,
                        accessCell: .ready(.none(code: bundleDetail.code)),
                        locale: .vi,
                        supportContact: .empty,
                        onOpenExam: { _ in },
                        onBuyBundle: {},
                        onReloadAccess: {}
                    )
                    .padding(16)
                }
            )

            try verifyAccessibility(
                screen: "Status410@\(scale.label)",
                scale: scale,
                view: ScrollView {
                    ExamGoneView(
                        code: "EXAM-01",
                        name: "Đề thi ngừng bán",
                        locale: .vi,
                        onReturnToCatalog: {},
                        onOpenMyExams: {}
                    )
                    .padding(16)
                }
            )
        }
    }

    private func verifyAccessibility<Content: View>(
        screen: String,
        scale: (label: String, uiCategory: UIContentSizeCategory, dynamicType: DynamicTypeSize),
        view: Content
    ) throws {
        let wrapped = view
            .environment(\.brandPalette, .defaultLight)
            .environment(\.statusPalette, .light)
            .environment(\.shellWidth, .narrow)
            .environment(\.dynamicTypeSize, scale.dynamicType)

        let (window, controller) = CatalogViewInspector.host(
            wrapped,
            size: phoneSize,
            uiCategory: scale.uiCategory,
            settleSeconds: 0.1
        )
        defer { window.isHidden = true }

        let nodes = CatalogViewInspector.collectNodes(in: controller.view, window: window)
        for button in nodes.filter({ $0.traits.contains(.button) }) {
            XCTAssertFalse(
                button.label.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                "\(screen) button '\(button.identifier)' must have a VoiceOver label"
            )
            guard button.frameInWindow.width > 0, button.frameInWindow.height > 0 else { continue }
            XCTAssertGreaterThanOrEqual(button.frameInWindow.width, 43.5, "\(screen) button width < 44pt")
            XCTAssertGreaterThanOrEqual(button.frameInWindow.height, 43.5, "\(screen) button height < 44pt")
        }

        for node in nodes where node.frameInWindow.width > 0 && node.frameInWindow.height > 0 {
            XCTAssertGreaterThanOrEqual(node.frameInWindow.minX, -1.0, "\(screen) '\(node.identifier)' overflows left")
            XCTAssertLessThanOrEqual(node.frameInWindow.maxX, phoneSize.width + 1.0, "\(screen) '\(node.identifier)' overflows right")
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
