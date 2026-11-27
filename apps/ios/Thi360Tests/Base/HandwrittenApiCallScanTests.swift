import Foundation
import XCTest

final class HandwrittenApiCallScanTests: XCTestCase {
    // Only binary image downloading and phase-1 public web theme fetch are allowed outside Core/Network.
    private static let allowedRelativePaths: Set<String> = [
        "Core/Image/ImageLoader.swift",
        "Core/Appearance/AppearanceRepository.swift"
    ]

    func testNoHandwrittenApiRequestsOutsideGeneratedClientAndAllowlist() throws {
        let sourceRoot = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("Thi360", isDirectory: true)

        let enumerator = try XCTUnwrap(FileManager.default.enumerator(at: sourceRoot, includingPropertiesForKeys: nil))
        var violations: [String] = []
        let forbiddenTokens = ["URLSession", "URLRequest", "HTTPURLResponse", "/api/v1/"]

        for case let fileURL as URL in enumerator where fileURL.pathExtension == "swift" {
            let relativePath = fileURL.path.replacingOccurrences(of: sourceRoot.path + "/", with: "")
            if relativePath.hasPrefix("Core/Network/") || Self.allowedRelativePaths.contains(relativePath) {
                continue
            }

            let content = try String(contentsOf: fileURL, encoding: .utf8)
            for (index, line) in content.components(separatedBy: .newlines).enumerated() {
                let trimmed = line.trimmingCharacters(in: .whitespaces)
                if trimmed.hasPrefix("//") { continue }
                for token in forbiddenTokens where line.contains(token) {
                    violations.append("\(relativePath):\(index + 1) contains '\(token)'")
                }
            }
        }

        XCTAssertTrue(
            violations.isEmpty,
            "Handwritten API calls found outside Core/Network:\n" + violations.joined(separator: "\n")
        )
    }
}
