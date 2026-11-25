import XCTest
@testable import Thi360

final class TransportSecurityTests: XCTestCase {
    func testActiveConfigurationMeetsTransportSecurityPolicy() {
        let environment = AppEnvironment.current
        let violations = TransportSecurityPolicy.validate(
            infoDictionary: Bundle.main.infoDictionary,
            environment: environment.kind
        )
        XCTAssertTrue(violations.isEmpty, "ATS violations for \(environment.kind): \(violations)")

        let ats = Bundle.main.infoDictionary?["NSAppTransportSecurity"] as? [String: Any]
        switch environment.kind {
        case .demo:
            XCTAssertNil(ats, "Demo Info.plist must have zero ATS exceptions")
        case .dev:
            XCTAssertNotNil(ats, "Dev Info.plist must define local ATS settings")
            XCTAssertNotEqual(ats?["NSAllowsArbitraryLoads"] as? Bool, true)
            XCTAssertEqual(ats?["NSAllowsLocalNetworking"] as? Bool, true)
        }
    }

    func testCleartextHTTPPolicyMatchesActiveConfiguration() {
        let info = Bundle.main.infoDictionary
        let publicHTTP = URL(string: "http://thi360.zone17.click/")!
        let localhostHTTP = URL(string: "http://localhost:8080/")!
        let loopbackHTTP = URL(string: "http://127.0.0.1:8080/")!

        XCTAssertFalse(
            TransportSecurityPolicy.permitsCleartextHTTP(to: publicHTTP, infoDictionary: info),
            "Public HTTP must never be permitted in Dev or Demo"
        )

        if AppEnvironment.current.isDemo {
            XCTAssertFalse(TransportSecurityPolicy.permitsCleartextHTTP(to: localhostHTTP, infoDictionary: info))
            XCTAssertFalse(TransportSecurityPolicy.permitsCleartextHTTP(to: loopbackHTTP, infoDictionary: info))
        } else {
            XCTAssertTrue(TransportSecurityPolicy.permitsCleartextHTTP(to: localhostHTTP, infoDictionary: info))
            XCTAssertTrue(TransportSecurityPolicy.permitsCleartextHTTP(to: loopbackHTTP, infoDictionary: info))
        }
    }

    func testCheckedInInfoPlistsSatisfyRespectivePolicies() throws {
        let resourcesURL = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("Thi360/Resources", isDirectory: true)

        let devPlist = try loadPlist(at: resourcesURL.appendingPathComponent("Info-Dev.plist"))
        let demoPlist = try loadPlist(at: resourcesURL.appendingPathComponent("Info-Demo.plist"))

        XCTAssertTrue(TransportSecurityPolicy.validate(infoDictionary: devPlist, environment: .dev).isEmpty)
        XCTAssertTrue(TransportSecurityPolicy.validate(infoDictionary: demoPlist, environment: .demo).isEmpty)
        XCTAssertNil(demoPlist["NSAppTransportSecurity"])
    }

    func testPolicyValidatorRejectsInsecureOverrides() {
        let arbitraryDev: [String: Any] = [
            "NSAppTransportSecurity": [
                "NSAllowsArbitraryLoads": true
            ]
        ]
        XCTAssertFalse(TransportSecurityPolicy.validate(infoDictionary: arbitraryDev, environment: .dev).isEmpty)

        let externalDomainDev: [String: Any] = [
            "NSAppTransportSecurity": [
                "NSAllowsLocalNetworking": true,
                "NSExceptionDomains": [
                    "example.com": ["NSExceptionAllowsInsecureHTTPLoads": true]
                ]
            ]
        ]
        XCTAssertFalse(TransportSecurityPolicy.validate(infoDictionary: externalDomainDev, environment: .dev).isEmpty)

        let demoWithException: [String: Any] = [
            "NSAppTransportSecurity": [
                "NSAllowsLocalNetworking": true
            ]
        ]
        XCTAssertFalse(TransportSecurityPolicy.validate(infoDictionary: demoWithException, environment: .demo).isEmpty)
    }

    private func loadPlist(at url: URL) throws -> [String: Any] {
        let data = try Data(contentsOf: url)
        let raw = try PropertyListSerialization.propertyList(from: data, format: nil)
        return try XCTUnwrap(raw as? [String: Any])
    }
}
