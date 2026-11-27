import Foundation
import XCTest

final class FormulaEnginePinTests: XCTestCase {
    private struct PackageResolvedFile: Decodable {
        let pins: [Pin]
        struct Pin: Decodable {
            let identity: String
            let location: String
            let state: State
            struct State: Decodable {
                let version: String?
                let revision: String?
            }
        }
    }

    func testSwaTexIsPinnedToExactVersionInProjectYmlAndPackageResolved() throws {
        let iosRoot = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()

        let projectYmlURL = iosRoot.appendingPathComponent("project.yml")
        let projectYml = try String(contentsOf: projectYmlURL, encoding: .utf8)

        let lines = projectYml.components(separatedBy: .newlines)
        guard let swaTexHeaderIndex = lines.firstIndex(where: { $0.trimmingCharacters(in: .whitespaces) == "SwaTex:" }) else {
            XCTFail("project.yml must declare SwaTex package")
            return
        }

        var blockLines: [String] = []
        for line in lines.dropFirst(swaTexHeaderIndex + 1) {
            if line.hasPrefix("    ") || line.trimmingCharacters(in: .whitespaces).isEmpty {
                blockLines.append(line.trimmingCharacters(in: .whitespaces))
            } else {
                break
            }
        }

        let exactLine = try XCTUnwrap(
            blockLines.first { $0.hasPrefix("exactVersion:") },
            "SwaTex in project.yml must use exactVersion (no range, from:, +, or latest). Found: \(blockLines)"
        )
        XCTAssertFalse(blockLines.contains { $0.hasPrefix("from:") || $0.hasPrefix("majorVersion:") || $0.hasPrefix("minorVersion:") || $0.hasPrefix("branch:") })

        let declaredVersion = exactLine
            .replacingOccurrences(of: "exactVersion:", with: "")
            .trimmingCharacters(in: CharacterSet.whitespacesAndNewlines.union(CharacterSet(charactersIn: "\"'")))

        XCTAssertFalse(declaredVersion.isEmpty)
        XCTAssertFalse(declaredVersion.contains("+"))
        XCTAssertFalse(declaredVersion.lowercased().contains("latest"))
        let semverRegex = try NSRegularExpression(pattern: "^\\d+\\.\\d+\\.\\d+$")
        let range = NSRange(location: 0, length: (declaredVersion as NSString).length)
        XCTAssertNotNil(semverRegex.firstMatch(in: declaredVersion, range: range), "Expected exact semver, got '\(declaredVersion)'")

        let resolvedURL = iosRoot.appendingPathComponent(
            "Thi360.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved"
        )
        let resolvedData = try Data(contentsOf: resolvedURL)
        let resolved = try JSONDecoder().decode(PackageResolvedFile.self, from: resolvedData)

        let swaTexPin = try XCTUnwrap(
            resolved.pins.first { $0.identity.lowercased() == "swatex" },
            "Package.resolved must pin SwaTex"
        )
        XCTAssertEqual(
            swaTexPin.state.version,
            declaredVersion,
            "Package.resolved SwaTex version (\(swaTexPin.state.version ?? "nil")) must match project.yml exactVersion (\(declaredVersion))"
        )
    }
}
