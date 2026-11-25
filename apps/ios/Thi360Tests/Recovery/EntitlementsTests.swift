import Foundation
import XCTest
@testable import Thi360

final class EntitlementsTests: XCTestCase {
    private var iosRootURL: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
    }

    func testEntitlementsDeclareOnlyAssociatedDomainsApplinksForWebHost() throws {
        let entitlementsURL = iosRootURL.appendingPathComponent("Thi360/Thi360.entitlements")
        XCTAssertTrue(FileManager.default.fileExists(atPath: entitlementsURL.path), "Thi360.entitlements must exist")

        let data = try Data(contentsOf: entitlementsURL)
        let plist = try XCTUnwrap(
            PropertyListSerialization.propertyList(from: data, format: nil) as? [String: Any]
        )

        XCTAssertEqual(
            Set(plist.keys),
            ["com.apple.developer.associated-domains"],
            "Thi360.entitlements must declare only Associated Domains"
        )

        let domains = try XCTUnwrap(plist["com.apple.developer.associated-domains"] as? [String])
        XCTAssertEqual(domains.count, 1, "Entitlements must contain exactly one applinks entry")
        XCTAssertEqual(domains.first, "applinks:$(THI360_WEB_APPLINKS_HOST)")
        XCTAssertFalse(domains.contains { $0.contains("*") }, "Associated Domains must never use wildcards")
    }

    func testConfigFilesAndProjectBindApplinksHostMatchingWebBaseURL() throws {
        let devConfig = try parseXCConfig(at: iosRootURL.appendingPathComponent("Config/Dev.xcconfig"))
        let demoConfig = try parseXCConfig(at: iosRootURL.appendingPathComponent("Config/Demo.xcconfig"))

        XCTAssertEqual(devConfig["THI360_WEB_APPLINKS_HOST"], "localhost")
        XCTAssertEqual(demoConfig["THI360_WEB_APPLINKS_HOST"], "thi360.zone17.click")

        let activeEnv = AppEnvironment.current
        let expectedHost = activeEnv.isDemo
            ? demoConfig["THI360_WEB_APPLINKS_HOST"]
            : devConfig["THI360_WEB_APPLINKS_HOST"]
        XCTAssertEqual(activeEnv.webBaseURL.host, expectedHost)

        let projectYAML = try String(contentsOf: iosRootURL.appendingPathComponent("project.yml"), encoding: .utf8)
        XCTAssertTrue(
            projectYAML.contains("CODE_SIGN_ENTITLEMENTS: Thi360/Thi360.entitlements"),
            "project.yml must bind CODE_SIGN_ENTITLEMENTS to Thi360/Thi360.entitlements"
        )
    }

    private func parseXCConfig(at url: URL) throws -> [String: String] {
        let content = try String(contentsOf: url, encoding: .utf8)
        var values: [String: String] = [:]
        for rawLine in content.components(separatedBy: .newlines) {
            let line = rawLine.trimmingCharacters(in: .whitespaces)
            guard !line.isEmpty, !line.hasPrefix("#"), !line.hasPrefix("//") else { continue }
            let parts = line.split(separator: "=", maxSplits: 1)
            guard parts.count == 2 else { continue }
            let key = parts[0].trimmingCharacters(in: .whitespaces)
            let value = parts[1].trimmingCharacters(in: .whitespaces)
            values[key] = value
        }
        return values
    }
}
