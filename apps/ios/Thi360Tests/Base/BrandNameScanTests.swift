import Foundation
import XCTest

final class BrandNameScanTests: XCTestCase {
    private static let textExtensions: Set<String> = [
        "swift", "xcstrings", "plist", "xcconfig", "yml", "yaml", "json", "entitlements", "strings"
    ]

    func testNoUserFacingSourceCatalogOrBuildConfigContainsThiSpace360() throws {
        let iosRoot = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()

        let targets: [URL] = [
            iosRoot.appendingPathComponent("Thi360", isDirectory: true),
            iosRoot.appendingPathComponent("Config", isDirectory: true),
            iosRoot.appendingPathComponent("project.yml")
        ]

        var violations: [String] = []

        for target in targets {
            var isDir: ObjCBool = false
            guard FileManager.default.fileExists(atPath: target.path, isDirectory: &isDir) else {
                XCTFail("Expected scan target to exist: \(target.path)")
                continue
            }

            let files: [URL]
            if isDir.boolValue {
                let enumerator = try XCTUnwrap(FileManager.default.enumerator(at: target, includingPropertiesForKeys: nil))
                var collected: [URL] = []
                for case let url as URL in enumerator where Self.textExtensions.contains(url.pathExtension.lowercased()) {
                    collected.append(url)
                }
                files = collected
            } else {
                files = [target]
            }

            for fileURL in files {
                guard let content = try? String(contentsOf: fileURL, encoding: .utf8) else { continue }
                let relativePath = fileURL.path.replacingOccurrences(of: iosRoot.path + "/", with: "")
                for (index, line) in content.components(separatedBy: .newlines).enumerated() {
                    if line.contains("Thi 360") {
                        violations.append("\(relativePath):\(index + 1): \(line.trimmingCharacters(in: .whitespaces))")
                    }
                }
            }
        }

        XCTAssertTrue(
            violations.isEmpty,
            "Found forbidden 'Thi 360' string in iOS sources/resources/config:\n" + violations.joined(separator: "\n")
        )
    }
}
