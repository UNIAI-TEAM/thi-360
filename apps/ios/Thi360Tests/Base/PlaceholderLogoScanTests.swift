import Foundation
import XCTest

final class PlaceholderLogoScanTests: XCTestCase {
    func testNoTemporaryCheckmarkCircleLogoOrDuplicateAppNameLogoRemains() throws {
        let appRoot = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("Thi360", isDirectory: true)

        let checkedBrandFiles = [
            "Features/Login/LoginView.swift",
            "Features/Lock/LockView.swift",
            "Shell/PlaceholderDestinationView.swift"
        ]
        for relative in checkedBrandFiles {
            let content = try String(contentsOf: appRoot.appendingPathComponent(relative), encoding: .utf8)
            XCTAssertTrue(
                content.contains("BrandMark("),
                "\(relative) must render BrandMark"
            )
            // Ensure no temporary checkmark.circle icon is paired with shell.app.name as a logo.
            let lines = content.components(separatedBy: .newlines)
            for (idx, line) in lines.enumerated() where line.contains("checkmark.circle") {
                let windowStart = max(0, idx - 8)
                let windowEnd = min(lines.count - 1, idx + 8)
                let snippet = lines[windowStart...windowEnd].joined(separator: "\n")
                XCTAssertFalse(
                    snippet.contains("shell.app.name") || snippet.contains("loginBrandBadge") || snippet.contains("BrandWordmarkHeader"),
                    "\(relative):\(idx + 1) still uses checkmark.circle as a brand logo"
                )
            }
        }

        // Outside BrandMark.swift and PrivacyShieldView.swift, no file may render shell.app.name inside a logo badge.
        // Note: LockView.swift uses shell.app.name strictly as a text fallback when cachedAccountDisplayName is empty.
        let allowedAppNameFiles: Set<String> = [
            "Core/Brand/BrandMark.swift",
            "Features/Lock/PrivacyShieldView.swift",
            "Features/Lock/LockView.swift"
        ]

        let enumerator = try XCTUnwrap(FileManager.default.enumerator(at: appRoot, includingPropertiesForKeys: nil))
        var violations: [String] = []
        for case let fileURL as URL in enumerator where fileURL.pathExtension == "swift" {
            let relativePath = fileURL.path.replacingOccurrences(of: appRoot.path + "/", with: "")
            if allowedAppNameFiles.contains(relativePath) { continue }
            let content = try String(contentsOf: fileURL, encoding: .utf8)
            if content.contains("shell.app.name") {
                violations.append("\(relativePath) renders shell.app.name outside BrandMark/PrivacyShieldView")
            }
        }

        XCTAssertTrue(
            violations.isEmpty,
            "Unexpected shell.app.name logo usage:\n" + violations.joined(separator: "\n")
        )
    }
}
