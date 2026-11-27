import SwiftUI
import UIKit
import XCTest
@testable import Thi360

private final class HistoryPlaceholderNetworkSpyURLProtocol: URLProtocol {
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
final class HistoryPlaceholderScreenTests: XCTestCase {
    func testHistoryPlaceholderExposesLocalizedTitleAndEmptyMessage() {
        XCTAssertEqual(ShellStrings.text(Destination.history.titleKey, locale: .vi), "Lịch sử")
        XCTAssertEqual(ShellStrings.text(Destination.history.titleKey, locale: .en), "History")
        XCTAssertEqual(MyExamsStrings.text("history.pageTitle", locale: .vi), "Lịch sử luyện thi")
        XCTAssertEqual(MyExamsStrings.text("history.pageTitle", locale: .en), "Practice history")
        XCTAssertEqual(MyExamsStrings.text("history.empty.noAttemptsTitle", locale: .vi), "Bạn chưa có lượt luyện thi nào")
        XCTAssertEqual(MyExamsStrings.text("history.empty.noAttemptsTitle", locale: .en), "You have no practice attempts yet")
    }

    func testRenderingHistoryPlaceholderPerformsZeroNetworkRequests() {
        HistoryPlaceholderNetworkSpyURLProtocol.reset()
        URLProtocol.registerClass(HistoryPlaceholderNetworkSpyURLProtocol.self)
        defer {
            URLProtocol.unregisterClass(HistoryPlaceholderNetworkSpyURLProtocol.self)
            HistoryPlaceholderNetworkSpyURLProtocol.reset()
        }

        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 844))
        defer { window.isHidden = true }

        for locale in AppLocale.allCases {
            let preferences = AppPreferences(userDefaults: UserDefaults(suiteName: "history.placeholder.\(UUID().uuidString)")!)
            preferences.locale = locale
            let view = MyExamsRoutes.historyRoot(resetFilters: false, preferences: preferences, showsBrandHeader: false)
                .environment(\.brandPalette, .defaultLight)

            let controller = UIHostingController(rootView: view)
            window.rootViewController = controller
            window.makeKeyAndVisible()
            window.layoutIfNeeded()
            RunLoop.main.run(until: Date().addingTimeInterval(0.05))
        }

        HistoryPlaceholderNetworkSpyURLProtocol.lock.lock()
        let requests = HistoryPlaceholderNetworkSpyURLProtocol.capturedURLs
        HistoryPlaceholderNetworkSpyURLProtocol.lock.unlock()

        XCTAssertTrue(requests.isEmpty, "History placeholder must not make network calls: \(requests)")
    }
}
