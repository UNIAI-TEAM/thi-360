import SwiftUI
import UIKit
import XCTest
@testable import Thi360

private final class NetworkSpyURLProtocol: URLProtocol {
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
final class PlaceholderScreensTests: XCTestCase {
    func testPlaceholderDestinationsExposeLocalizedTitleAndEmptyMessage() {
        let placeholders = Destination.allCases.filter(\.isPlaceholder)
        XCTAssertEqual(placeholders, [.home, .myExams, .history])

        XCTAssertEqual(ShellStrings.text(Destination.home.titleKey, locale: .vi), "Trang chủ")
        XCTAssertEqual(ShellStrings.text(Destination.myExams.titleKey, locale: .vi), "Bài thi của tôi")
        XCTAssertEqual(ShellStrings.text(Destination.history.titleKey, locale: .vi), "Lịch sử")
        XCTAssertEqual(ShellStrings.text("shell.placeholder.title", locale: .vi), "Chưa có nội dung")

        XCTAssertEqual(ShellStrings.text(Destination.home.titleKey, locale: .en), "Home")
        XCTAssertEqual(ShellStrings.text(Destination.myExams.titleKey, locale: .en), "My exams")
        XCTAssertEqual(ShellStrings.text(Destination.history.titleKey, locale: .en), "History")
        XCTAssertEqual(ShellStrings.text("shell.placeholder.title", locale: .en), "No content yet")

        for destination in placeholders {
            let key = destination.placeholderDescriptionKey
            XCTAssertNotNil(key)
            if let key {
                XCTAssertFalse(ShellStrings.text(key, locale: .vi).isEmpty)
                XCTAssertFalse(ShellStrings.text(key, locale: .en).isEmpty)
            }
        }
    }

    func testRenderingPlaceholderScreensPerformsZeroNetworkRequests() {
        NetworkSpyURLProtocol.reset()
        URLProtocol.registerClass(NetworkSpyURLProtocol.self)
        defer {
            URLProtocol.unregisterClass(NetworkSpyURLProtocol.self)
            NetworkSpyURLProtocol.reset()
        }

        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 844))
        defer { window.isHidden = true }

        for destination in [Destination.home, .myExams, .history] {
            for locale in AppLocale.allCases {
                let view = PlaceholderDestinationView(
                    destination: destination,
                    locale: locale,
                    showsBrandHeader: destination == .home
                )
                .environment(\.brandPalette, .defaultLight)

                let controller = UIHostingController(rootView: view)
                window.rootViewController = controller
                window.makeKeyAndVisible()
                window.layoutIfNeeded()
                RunLoop.main.run(until: Date().addingTimeInterval(0.05))
            }
        }

        NetworkSpyURLProtocol.lock.lock()
        let requests = NetworkSpyURLProtocol.capturedURLs
        NetworkSpyURLProtocol.lock.unlock()

        XCTAssertTrue(requests.isEmpty, "Placeholder screens must not make network calls: \(requests)")
    }
}
