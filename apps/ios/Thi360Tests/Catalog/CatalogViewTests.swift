import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class CatalogViewTests: XCTestCase {
    func testNoLandingBlocks() async throws {
        let stack = try CatalogTestStackFactory.makeStack { transport in
            try CatalogTestStackFactory.registerDefaultStorefrontFixtures(on: transport)
        }
        defer {
            UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName)
        }

        let feed = CatalogFeed(accountId: "acc-1", api: stack.api)
        await feed.loadInitialIfNeeded()

        let listView = CatalogListColumnView(
            feed: feed,
            locale: .vi,
            showsBrandHeader: true,
            showsSelectionHighlight: false,
            onSelectProduct: { _ in },
            onAccessButtonTap: { _ in }
        )

        XCTAssertEqual(listView.headingText, "Chọn bài thi")
        XCTAssertTrue(listView.showsFilterHeader)

        let router = AppRouter(initialDestination: .home)
        let view = CatalogHomeView(
            feed: feed,
            router: router,
            locale: .vi,
            showsBrandHeader: true,
            api: stack.api
        )
        .environment(\.brandPalette, .defaultLight)
        .environment(\.statusPalette, .light)
        .environment(\.shellWidth, .narrow)

        let (window, controller) = CatalogViewInspector.host(view)
        defer { window.isHidden = true }

        let textFields = RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: controller.view)
        XCTAssertEqual(textFields.count, 1)
        XCTAssertEqual(textFields[0].placeholder, "Ví dụ: HSA FULL hoặc HSA-FULL-01")
        XCTAssertFalse(RecoveryViewTestHelper.findViews(ofType: UIScrollView.self, in: controller.view).isEmpty)

        let catalogStrings = [
            listView.headingText,
            listView.emptyCatalogMessage,
            listView.loadingSkeletonLabel,
            CatalogStrings.text("storefront.catalog.searchLabel", locale: .vi),
            CatalogStrings.text("storefront.catalog.searchPlaceholder", locale: .vi)
        ].joined(separator: "\n")
        XCTAssertFalse(catalogStrings.contains("Chưa có nội dung"))
        XCTAssertFalse(catalogStrings.contains("Cách Thi360 hoạt động"))
        XCTAssertFalse(catalogStrings.contains("Vì sao chọn"))
        XCTAssertFalse(catalogStrings.contains("Câu hỏi thường gặp"))

        let (_, png) = try CatalogViewInspector.renderPNG(window: window, size: CGSize(width: 390, height: 844))
        XCTAssertGreaterThan(png.count, 4096)
    }
}
