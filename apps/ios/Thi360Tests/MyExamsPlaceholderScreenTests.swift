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
        XCTAssertTrue(Destination.myExams.isPlaceholder)
        XCTAssertEqual(ShellStrings.text(Destination.myExams.titleKey, locale: .vi), "Bài thi của tôi")
        XCTAssertEqual(ShellStrings.text(Destination.myExams.titleKey, locale: .en), "My exams")
        XCTAssertEqual(ShellStrings.text("shell.placeholder.title", locale: .vi), "Chưa có nội dung")
        XCTAssertEqual(ShellStrings.text("shell.placeholder.title", locale: .en), "No content yet")

        let key = Destination.myExams.placeholderDescriptionKey
        XCTAssertNotNil(key)
        if let key {
            XCTAssertFalse(ShellStrings.text(key, locale: .vi).isEmpty)
            XCTAssertFalse(ShellStrings.text(key, locale: .en).isEmpty)
        }
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
