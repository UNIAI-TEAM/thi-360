import Foundation
import XCTest

final class PaymentSurfaceSourceTests: XCTestCase {
    func testPaymentSourceContainsNoWebViewOrCookieStorage() throws {
        let paymentRoot = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("Thi360/Features/Payment", isDirectory: true)

        let routeFile = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("Thi360/Shell/Routes/PaymentRoutes.swift")

        let forbiddenTokens = [
            "import WebKit",
            "WKWebView",
            "UIWebView",
            "HTTPCookieStorage",
            "WKWebsiteDataStore"
        ]

        var files: [URL] = [routeFile]
        if let enumerator = FileManager.default.enumerator(at: paymentRoot, includingPropertiesForKeys: nil) {
            for case let fileURL as URL in enumerator where fileURL.pathExtension == "swift" {
                files.append(fileURL)
            }
        }
        XCTAssertFalse(files.isEmpty)

        var violations: [String] = []
        for fileURL in files {
            let content = try String(contentsOf: fileURL, encoding: .utf8)
            for token in forbiddenTokens where content.contains(token) {
                violations.append("\(fileURL.lastPathComponent) contains forbidden token '\(token)'")
            }
        }

        XCTAssertTrue(violations.isEmpty, violations.joined(separator: "\n"))
    }
}
