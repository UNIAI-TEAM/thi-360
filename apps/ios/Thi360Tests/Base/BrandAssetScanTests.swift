import Foundation
import ImageIO
import XCTest

final class BrandAssetScanTests: XCTestCase {
    func testNoWordmarkOrSvgAssetUsedInAppBundleOrSources() throws {
        let iosRoot = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
        let appRoot = iosRoot.appendingPathComponent("Thi360", isDirectory: true)

        let enumerator = try XCTUnwrap(FileManager.default.enumerator(at: appRoot, includingPropertiesForKeys: nil))
        var violations: [String] = []

        for case let fileURL as URL in enumerator {
            let relativePath = fileURL.path.replacingOccurrences(of: iosRoot.path + "/", with: "")
            let lowerName = fileURL.lastPathComponent.lowercased()
            if lowerName.contains("wordmark") || lowerName == "thi360-icon.svg" {
                violations.append("Forbidden brand asset file: \(relativePath)")
            }

            if ["swift", "json", "plist", "yml"].contains(fileURL.pathExtension.lowercased()),
               let content = try? String(contentsOf: fileURL, encoding: .utf8)
            {
                if content.contains("thi360-wordmark") || content.contains("thi360-icon.svg") {
                    violations.append("Forbidden asset reference in \(relativePath)")
                }
            }

            if fileURL.pathExtension.lowercased() == "png",
               relativePath.contains("Assets.xcassets"),
               let source = CGImageSourceCreateWithURL(fileURL as CFURL, nil),
               let props = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
               let width = props[kCGImagePropertyPixelWidth] as? CGFloat,
               let height = props[kCGImagePropertyPixelHeight] as? CGFloat,
               height > 0
            {
                let ratio = width / height
                if ratio > 1.3 {
                    violations.append("Wide wordmark-shaped PNG in Assets.xcassets: \(relativePath) (\(Int(width))x\(Int(height)))")
                }
            }
        }

        XCTAssertTrue(violations.isEmpty, "Forbidden brand asset detected:\n" + violations.joined(separator: "\n"))
    }
}
