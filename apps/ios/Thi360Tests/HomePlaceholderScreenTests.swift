import SwiftUI
import UIKit
import XCTest
@testable import Thi360

private final class HomePlaceholderNetworkSpyURLProtocol: URLProtocol {
    static let lock = NSLock()
    static var capturedURLs: [URL] = []

    static func reset() {
        lock.lock()
        capturedURLs.removeAll()
        lock.unlock()
    }

    override class func canInit(with request: URLRequest) -> Bool {
        if let url = request.url {
            lock.lock()
            capturedURLs.append(url)
            lock.unlock()
        }
        return true
    }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest {
        request
    }

    override func startLoading() {
        client?.urlProtocol(self, didFailWithError: URLError(.notConnectedToInternet))
    }

    override func stopLoading() {}
}

@MainActor
final class HomePlaceholderScreenTests: XCTestCase {
    func testHomePlaceholderExposesLocalizedTitleAndEmptyMessage() {
        XCTAssertEqual(ShellStrings.text(Destination.home.titleKey, locale: .vi), "Trang chủ")
        XCTAssertEqual(ShellStrings.text(Destination.home.titleKey, locale: .en), "Home")
        XCTAssertEqual(CatalogStrings.text("storefront.catalog.title", locale: .vi), "Chọn bài thi")
        XCTAssertEqual(CatalogStrings.text("storefront.catalog.title", locale: .en), "Choose an exam")
        XCTAssertEqual(CatalogStrings.text("storefront.catalog.emptyTitle", locale: .vi), "Hiện chưa có bài thi nào đang mở bán")
        XCTAssertEqual(CatalogStrings.text("storefront.catalog.emptyTitle", locale: .en), "No exams are on sale right now")
    }

    func testRenderingHomePlaceholderPerformsZeroNetworkRequests() {
        HomePlaceholderNetworkSpyURLProtocol.reset()
        URLProtocol.registerClass(HomePlaceholderNetworkSpyURLProtocol.self)
        defer {
            URLProtocol.unregisterClass(HomePlaceholderNetworkSpyURLProtocol.self)
            HomePlaceholderNetworkSpyURLProtocol.reset()
        }

        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 844))
        defer { window.isHidden = true }

        for locale in AppLocale.allCases {
            let preferences = AppPreferences(userDefaults: UserDefaults(suiteName: "home.placeholder.\(UUID().uuidString)")!)
            preferences.locale = locale
            let view = CatalogRoutes.homeRoot(preferences: preferences, showsBrandHeader: true)
                .environment(\.brandPalette, .defaultLight)

            let controller = UIHostingController(rootView: view)
            window.rootViewController = controller
            window.makeKeyAndVisible()
            window.layoutIfNeeded()
            RunLoop.main.run(until: Date().addingTimeInterval(0.05))
        }

        HomePlaceholderNetworkSpyURLProtocol.lock.lock()
        let requests = HomePlaceholderNetworkSpyURLProtocol.capturedURLs
        HomePlaceholderNetworkSpyURLProtocol.lock.unlock()

        XCTAssertTrue(requests.isEmpty, "Home placeholder must not make network calls: \(requests)")
    }
}
