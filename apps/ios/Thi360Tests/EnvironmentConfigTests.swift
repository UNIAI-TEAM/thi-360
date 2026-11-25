import XCTest
@testable import Thi360

final class EnvironmentConfigTests: XCTestCase {
    func testActiveSchemeEnvironmentConfiguration() {
        let env = AppEnvironment.current

        switch env.kind {
        case .demo:
            XCTAssertEqual(env.apiBaseURL.absoluteString, "https://thi360.zone17.click")
            XCTAssertEqual(env.webBaseURL.absoluteString, "https://thi360.zone17.click")
            XCTAssertEqual(env.apiBaseURL.scheme, "https")
            XCTAssertEqual(env.webBaseURL.scheme, "https")
        case .dev:
            XCTAssertNotNil(env.apiBaseURL.host)
            XCTAssertNotNil(env.webBaseURL.host)
            XCTAssertTrue(["http", "https"].contains(env.apiBaseURL.scheme ?? ""))
            XCTAssertTrue(["http", "https"].contains(env.webBaseURL.scheme ?? ""))
        }
    }

    func testDemoEnvironmentRejectsCleartextURLs() {
        let insecureDemo: [String: Any] = [
            "THI360_ENV": "demo",
            "THI360_API_BASE_URL": "http://thi360.zone17.click",
            "THI360_WEB_BASE_URL": "https://thi360.zone17.click"
        ]

        XCTAssertThrowsError(try AppEnvironment.load(from: insecureDemo)) { error in
            XCTAssertEqual(
                error as? AppEnvironmentError,
                .insecureDemoURL(key: "THI360_API_BASE_URL", url: URL(string: "http://thi360.zone17.click")!)
            )
        }
    }

    func testValidDemoAndDevDictionariesParseCleanly() throws {
        let demo = try AppEnvironment.load(from: [
            "THI360_ENV": "demo",
            "THI360_API_BASE_URL": "https://thi360.zone17.click",
            "THI360_WEB_BASE_URL": "https://thi360.zone17.click"
        ])
        XCTAssertEqual(demo.kind, .demo)
        XCTAssertEqual(demo.apiBaseURL.absoluteString, "https://thi360.zone17.click")
        XCTAssertEqual(demo.webBaseURL.absoluteString, "https://thi360.zone17.click")

        let dev = try AppEnvironment.load(from: [
            "THI360_ENV": "dev",
            "THI360_API_BASE_URL": "http://localhost:8080",
            "THI360_WEB_BASE_URL": "http://localhost:3000"
        ])
        XCTAssertEqual(dev.kind, .dev)
        XCTAssertEqual(dev.apiBaseURL.absoluteString, "http://localhost:8080")
        XCTAssertEqual(dev.webBaseURL.absoluteString, "http://localhost:3000")
    }
}
