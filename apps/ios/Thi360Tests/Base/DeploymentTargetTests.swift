import Foundation
import XCTest

final class DeploymentTargetTests: XCTestCase {
    func testProjectYmlAndBaseXcconfigDeclareIOS18MinimumDeploymentTarget() throws {
        let iosRoot = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()

        let projectYmlURL = iosRoot.appendingPathComponent("project.yml")
        let projectYml = try String(contentsOf: projectYmlURL, encoding: .utf8)

        let targetLines = projectYml
            .components(separatedBy: .newlines)
            .enumerated()
            .filter { _, line in
                line.contains("iOS:")
                    || line.contains("IPHONEOS_DEPLOYMENT_TARGET")
                    || line.contains("deploymentTarget: \"")
            }

        XCTAssertGreaterThanOrEqual(targetLines.count, 5, "Expected 5 deployment target declarations in project.yml")
        for (idx, line) in targetLines {
            XCTAssertTrue(
                line.contains("18.0"),
                "project.yml:\(idx + 1) must declare iOS 18.0, found: \(line)"
            )
            XCTAssertFalse(
                line.contains("17.0"),
                "project.yml:\(idx + 1) must not retain iOS 17.0: \(line)"
            )
        }

        let baseConfigURL = iosRoot.appendingPathComponent("Config/Base.xcconfig")
        let baseConfig = try String(contentsOf: baseConfigURL, encoding: .utf8)
        let configLines = baseConfig
            .components(separatedBy: .newlines)
            .filter { $0.contains("IPHONEOS_DEPLOYMENT_TARGET") }
        XCTAssertEqual(configLines.count, 1, "Base.xcconfig must declare IPHONEOS_DEPLOYMENT_TARGET once")
        XCTAssertTrue(
            configLines.first?.contains("18.0") == true,
            "Base.xcconfig must declare IPHONEOS_DEPLOYMENT_TARGET = 18.0, found: \(configLines.first ?? "")"
        )
    }
}
