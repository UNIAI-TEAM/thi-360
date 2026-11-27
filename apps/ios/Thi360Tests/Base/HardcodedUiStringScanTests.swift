import Foundation
import XCTest

final class HardcodedUiStringScanTests: XCTestCase {
    private static let uiDirectories = [
        "Shell",
        "Core/Loading",
        "Core/Layout",
        "Core/Brand",
        "Core/Legal",
        "Features/Register"
    ]

    func testNoHardcodedDisplayStringsInPortalUiViews() throws {
        let sourceRoot = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("Thi360", isDirectory: true)

        let literalPattern = try NSRegularExpression(
            pattern: #"(?:Text|Button|Label|navigationTitle)\(\s*"([^"\\]*(?:\\.[^"\\]*)*)"\s*(?:,|\))"#
        )
        let allowedNonProse = Set(["", "•", "·", "-", "—", "/", "×", "360", "%"])
        var violations: [String] = []

        for dir in Self.uiDirectories {
            let dirURL = sourceRoot.appendingPathComponent(dir, isDirectory: true)
            guard let enumerator = FileManager.default.enumerator(at: dirURL, includingPropertiesForKeys: nil) else {
                continue
            }
            for case let fileURL as URL in enumerator where fileURL.pathExtension == "swift" {
                let relativePath = fileURL.path.replacingOccurrences(of: sourceRoot.path + "/", with: "")
                let content = try String(contentsOf: fileURL, encoding: .utf8)
                for (index, line) in content.components(separatedBy: .newlines).enumerated() {
                    let trimmed = line.trimmingCharacters(in: .whitespaces)
                    if trimmed.hasPrefix("//") { continue }
                    let range = NSRange(line.startIndex..., in: line)
                    for match in literalPattern.matches(in: line, range: range) {
                        guard let literalRange = Range(match.range(at: 1), in: line) else { continue }
                        let literal = String(line[literalRange])
                        if allowedNonProse.contains(literal) { continue }
                        let strippedInterpolations = Self.stripSwiftInterpolations(from: literal)
                        let hasLetters = strippedInterpolations.unicodeScalars.contains { CharacterSet.letters.contains($0) }
                        if hasLetters {
                            violations.append("\(relativePath):\(index + 1) hardcoded UI string: \"\(literal)\"")
                        }
                    }
                }
            }
        }

        XCTAssertTrue(
            violations.isEmpty,
            "Hardcoded UI strings found:\n" + violations.joined(separator: "\n")
        )
    }

    private static func stripSwiftInterpolations(from literal: String) -> String {
        var result = ""
        let chars = Array(literal)
        var i = 0
        while i < chars.count {
            if chars[i] == "\\", i + 1 < chars.count, chars[i + 1] == "(" {
                var depth = 1
                i += 2
                while i < chars.count && depth > 0 {
                    if chars[i] == "(" { depth += 1 }
                    else if chars[i] == ")" { depth -= 1 }
                    i += 1
                }
            } else {
                result.append(chars[i])
                i += 1
            }
        }
        return result
    }
}
