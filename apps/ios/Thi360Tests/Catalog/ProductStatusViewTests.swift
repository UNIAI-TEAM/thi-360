import Foundation
import SwiftUI
import XCTest
@testable import Thi360

@MainActor
final class ProductStatusViewTests: XCTestCase {
    func testDiscontinued410WithNameAndEmptyNameFallbackMakesZeroExtraCalls() async throws {
        let goneWithName = try CatalogTestFixtures.makeGone410Data(code: "X", name: "Đề X")
        let goneEmptyName = try CatalogTestFixtures.makeGone410Data(code: "X", name: "")
        let goneMissingName = try CatalogTestFixtures.makeGone410Data(code: "X", name: nil)

        let stack = try CatalogTestStackFactory.makeStack { transport in
            transport.enqueue(method: .get, path: "/api/v1/public/storefront/products/X", handlers: [
                { _ in ContractFakeTransport.jsonResponse(statusCode: 410, data: goneWithName) },
                { _ in ContractFakeTransport.jsonResponse(statusCode: 410, data: goneEmptyName) },
                { _ in ContractFakeTransport.jsonResponse(statusCode: 410, data: goneMissingName) }
            ])
        }
        defer {
            UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName)
        }

        let vm = ProductDetailViewModel(code: "X", api: stack.api)
        await vm.loadIfNeeded()
        XCTAssertEqual(vm.loadedOutcome, .gone(code: "X", name: "Đề X"))
        XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/public/storefront/products/X"), 1)
        XCTAssertEqual(stack.transport.callCount(method: .post, path: "/api/v1/me/exam-access/query"), 0)

        let router = AppRouter(initialDestination: .home)
        let nav = CatalogNavigationHandler(router: router)
        let goneView = ExamGoneView(
            code: "X",
            name: "Đề X",
            locale: .vi,
            onReturnToCatalog: { nav.returnToCatalog {} },
            onOpenMyExams: { nav.openMyExamsTab() }
        )

        let joined = goneView.renderedStrings.joined(separator: "\n")
        XCTAssertTrue(joined.contains("MÃ LỖI 410"))
        XCTAssertTrue(joined.contains("Bài thi đã ngừng bán"))
        XCTAssertTrue(joined.contains("Đề X (X) không còn mở bán. Bạn có thể chọn bài thi khác trong danh mục."))
        XCTAssertTrue(joined.contains("Nếu đã mua bài này, quyền và số lượt còn lại của bạn vẫn ở Bài thi của tôi."))
        XCTAssertTrue(joined.contains("Về danh mục bài thi"))
        XCTAssertTrue(joined.contains("Bài thi của tôi"))
        XCTAssertFalse(joined.contains("Cấu trúc bài thi"))
        XCTAssertFalse(joined.contains("Mua bài thi"))

        let (window, _) = CatalogViewInspector.host(
            goneView
                .padding(16)
                .environment(\.brandPalette, .defaultLight)
                .environment(\.statusPalette, .light)
        )
        let (_, png) = try CatalogViewInspector.renderPNG(window: window, size: CGSize(width: 390, height: 844))
        XCTAssertGreaterThan(png.count, 2048)
        window.isHidden = true

        await vm.retry()
        XCTAssertEqual(vm.loadedOutcome, .gone(code: "X", name: "X"))
        XCTAssertEqual(
            CatalogFormatting.goneDescription(code: "X", name: "", locale: .vi),
            "X (X) không còn mở bán. Bạn có thể chọn bài thi khác trong danh mục."
        )

        await vm.retry()
        XCTAssertEqual(vm.loadedOutcome, .gone(code: "X", name: "X"))
        XCTAssertEqual(stack.transport.callCount(method: .post, path: "/api/v1/me/exam-access/query"), 0)

        nav.openMyExamsTab()
        XCTAssertEqual(router.selectedDestination, .myExams)

        let feed = CatalogFeed(accountId: "acc-1", api: stack.api)
        await feed.selectKind("HSA")
        feed.selectedProductCode = "X"
        nav.returnToCatalog { feed.selectedProductCode = nil }
        XCTAssertEqual(router.selectedDestination, .home)
        XCTAssertNil(feed.selectedProductCode)
        XCTAssertEqual(feed.query.kind, "HSA")
    }

    func testNotFound404View() async throws {
        let notFoundData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.getStorefrontProduct.id,
            status: 404
        )
        let stack = try CatalogTestStackFactory.makeStack { transport in
            transport.registerJSON(method: .get, path: "/api/v1/public/storefront/products/NOPE", statusCode: 404, data: notFoundData)
        }
        defer {
            UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName)
        }

        let vm = ProductDetailViewModel(code: "NOPE", api: stack.api)
        await vm.loadIfNeeded()
        XCTAssertEqual(vm.loadedOutcome, .notFound)
        XCTAssertEqual(stack.transport.callCount(method: .post, path: "/api/v1/me/exam-access/query"), 0)

        let view = ExamNotFoundView(locale: .vi, onReturnToCatalog: {})
        let joined = view.renderedStrings.joined(separator: "\n")
        XCTAssertTrue(joined.contains("MÃ LỖI 404"))
        XCTAssertTrue(joined.contains("Không tìm thấy bài thi"))
        XCTAssertTrue(joined.contains("Đường dẫn không đúng hoặc bài thi chưa được công bố."))
        XCTAssertTrue(joined.contains("Về danh mục bài thi"))
        XCTAssertFalse(joined.contains("Mua bài thi"))

        let (window, _) = CatalogViewInspector.host(
            view.padding(16).environment(\.brandPalette, .defaultLight).environment(\.statusPalette, .light)
        )
        defer { window.isHidden = true }
        let (_, png) = try CatalogViewInspector.renderPNG(window: window, size: CGSize(width: 390, height: 844))
        XCTAssertGreaterThan(png.count, 2048)
    }

    func testBundleUnavailable409() async throws {
        let conflictWithProduct = try CatalogTestFixtures.makeUnavailableBundle409Data(includeProduct: true)
        let conflictWithoutProduct = try CatalogTestFixtures.makeUnavailableBundle409Data(includeProduct: false)

        let stack = try CatalogTestStackFactory.makeStack { transport in
            transport.registerJSON(method: .get, path: "/api/v1/public/storefront/products/BD-409-FULL", statusCode: 409, data: conflictWithProduct)
            transport.registerJSON(method: .get, path: "/api/v1/public/storefront/products/BD-409-BARE", statusCode: 409, data: conflictWithoutProduct)
        }
        defer {
            UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName)
        }

        let fullVM = ProductDetailViewModel(code: "BD-409-FULL", api: stack.api)
        await fullVM.loadIfNeeded()
        guard case let .bundle(detail, sellable) = fullVM.loadedOutcome else {
            return XCTFail("Expected .bundle(sellable: false), got \(String(describing: fullVM.loadedOutcome))")
        }
        XCTAssertFalse(sellable)
        XCTAssertEqual(stack.transport.callCount(method: .post, path: "/api/v1/me/exam-access/query"), 0)

        let fullView = BundleDetailView(
            product: detail,
            sellable: false,
            accessCell: .loading,
            locale: .vi,
            supportContact: .empty,
            onOpenExam: { _ in },
            onBuyBundle: {},
            onReloadAccess: {}
        )
        XCTAssertFalse(fullView.isBuyBundleButtonEnabled)
        let joinedA = fullView.renderedBuyPanelActionLabels.joined(separator: "\n")
        XCTAssertTrue(joinedA.contains("Bộ đề tạm thời chưa bán được."))
        XCTAssertTrue(joinedA.contains("Một bài trong bộ đang được cập nhật. Bạn vẫn mua được từng bài lẻ."))
        XCTAssertTrue(joinedA.contains("Mua bộ đề"))

        let (windowA, _) = CatalogViewInspector.host(
            ScrollView { fullView.padding(16) }.environment(\.brandPalette, .defaultLight).environment(\.statusPalette, .light)
        )
        let (_, pngA) = try CatalogViewInspector.renderPNG(window: windowA, size: CGSize(width: 390, height: 844))
        XCTAssertGreaterThan(pngA.count, 4096)
        windowA.isHidden = true

        let bareVM = ProductDetailViewModel(code: "BD-409-BARE", api: stack.api)
        await bareVM.loadIfNeeded()
        XCTAssertEqual(bareVM.loadedOutcome, .bundleUnavailable)
        XCTAssertEqual(stack.transport.callCount(method: .post, path: "/api/v1/me/exam-access/query"), 0)

        let bareView = BundleUnavailableView(locale: .vi, onReturnToCatalog: {})
        XCTAssertFalse(bareView.isBuyButtonEnabled)
        let joinedB = bareView.renderedStrings.joined(separator: "\n")
        XCTAssertTrue(joinedB.contains("TẠM NGỪNG BÁN"))
        XCTAssertTrue(joinedB.contains("Bộ đề tạm thời chưa bán được."))
        XCTAssertTrue(joinedB.contains("Mua bộ đề"))
        XCTAssertTrue(joinedB.contains("Về danh mục bài thi"))

        let (windowB, _) = CatalogViewInspector.host(
            bareView.padding(16).environment(\.brandPalette, .defaultLight).environment(\.statusPalette, .light)
        )
        defer { windowB.isHidden = true }
        let (_, pngB) = try CatalogViewInspector.renderPNG(window: windowB, size: CGSize(width: 390, height: 844))
        XCTAssertGreaterThan(pngB.count, 2048)
    }

    func testBundleSaleStateUnavailable200() async throws {
        let unavailable200 = try CatalogTestFixtures.makeUnavailableBundle200Data()
        let stack = try CatalogTestStackFactory.makeStack { transport in
            transport.registerJSON(method: .get, path: "/api/v1/public/storefront/products/BD-200-UNAVAIL", statusCode: 200, data: unavailable200)
        }
        defer {
            UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName)
        }

        let vm = ProductDetailViewModel(code: "BD-200-UNAVAIL", api: stack.api)
        await vm.loadIfNeeded()

        guard case let .bundle(detail, sellable) = vm.loadedOutcome else {
            return XCTFail("Expected .bundle(sellable: false) for 200 saleState=UNAVAILABLE")
        }
        XCTAssertFalse(sellable)
        XCTAssertEqual(detail.saleState, "UNAVAILABLE")
        XCTAssertEqual(stack.transport.callCount(method: .post, path: "/api/v1/me/exam-access/query"), 0)

        let view = BundleDetailView(
            product: detail,
            sellable: false,
            accessCell: .loading,
            locale: .vi,
            supportContact: .empty,
            onOpenExam: { _ in },
            onBuyBundle: {},
            onReloadAccess: {}
        )
        XCTAssertFalse(view.isBuyBundleButtonEnabled)
        XCTAssertTrue(view.renderedBuyPanelActionLabels.joined(separator: "\n").contains("Bộ đề tạm thời chưa bán được."))

        let (window, _) = CatalogViewInspector.host(
            ScrollView { view.padding(16) }.environment(\.brandPalette, .defaultLight).environment(\.statusPalette, .light)
        )
        defer { window.isHidden = true }
        let (_, png) = try CatalogViewInspector.renderPNG(window: window, size: CGSize(width: 390, height: 844))
        XCTAssertGreaterThan(png.count, 4096)
    }
}
