import SwiftUI
import UIKit
import XCTest
@testable import Thi360

private final class MyExamsPlaceholderNetworkSpyURLProtocol: URLProtocol {
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
final class MyExamsPlaceholderScreenTests: XCTestCase {
    func testMyExamsPlaceholderExposesLocalizedTitleAndEmptyMessage() {
        XCTAssertEqual(ShellStrings.text(Destination.myExams.titleKey, locale: .vi), "Bài thi của tôi")
        XCTAssertEqual(ShellStrings.text(Destination.myExams.titleKey, locale: .en), "My exams")
        XCTAssertEqual(MyExamsStrings.text("home.pageTitle", locale: .vi), "Bài thi của tôi")
        XCTAssertEqual(MyExamsStrings.text("home.pageTitle", locale: .en), "My exams")
        XCTAssertEqual(MyExamsStrings.text("home.emptyTitle", locale: .vi), "Bạn chưa có bài thi nào")
        XCTAssertEqual(MyExamsStrings.text("home.emptyTitle", locale: .en), "You have no exams yet")
    }

    func testRenderingMyExamsPlaceholderPerformsZeroNetworkRequests() {
        MyExamsPlaceholderNetworkSpyURLProtocol.reset()
        URLProtocol.registerClass(MyExamsPlaceholderNetworkSpyURLProtocol.self)
        defer {
            URLProtocol.unregisterClass(MyExamsPlaceholderNetworkSpyURLProtocol.self)
            MyExamsPlaceholderNetworkSpyURLProtocol.reset()
        }

        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 844))
        defer { window.isHidden = true }

        for locale in AppLocale.allCases {
            let preferences = AppPreferences(userDefaults: UserDefaults(suiteName: "myexams.placeholder.\(UUID().uuidString)")!)
            preferences.locale = locale
            let view = MyExamsRoutes.myExamsRoot(preferences: preferences, showsBrandHeader: false)
                .environment(\.brandPalette, .defaultLight)

            let controller = UIHostingController(rootView: view)
            window.rootViewController = controller
            window.makeKeyAndVisible()
            window.layoutIfNeeded()
            RunLoop.main.run(until: Date().addingTimeInterval(0.05))
        }

        MyExamsPlaceholderNetworkSpyURLProtocol.lock.lock()
        let requests = MyExamsPlaceholderNetworkSpyURLProtocol.capturedURLs
        MyExamsPlaceholderNetworkSpyURLProtocol.lock.unlock()

        XCTAssertTrue(requests.isEmpty, "MyExams placeholder must not make network calls: \(requests)")
    }
}
