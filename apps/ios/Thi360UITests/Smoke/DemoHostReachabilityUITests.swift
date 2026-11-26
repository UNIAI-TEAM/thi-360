import Foundation
import XCTest

final class DemoHostReachabilityUITests: XCTestCase {
    private let demoBaseURL = "https://thi360.zone17.click"

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testDemoBuildReachesDemoHostOverHttpsAndReceivesHttpResponses() throws {
        let app = XCUIApplication()
        app.launch()

        let identifierField = app.textFields["login.identifierField"]
        XCTAssertTrue(
            identifierField.waitForExistence(timeout: 15),
            "Demo launch without session must present Login screen"
        )
        identifierField.tap()
        identifierField.typeText("qa.demo.probe")

        let passwordField = app.secureTextFields["login.passwordField"]
        XCTAssertTrue(passwordField.waitForExistence(timeout: 5))
        passwordField.tap()
        passwordField.typeText("ProbePass1!")

        let submitButton = app.buttons["login.submitButton"]
        XCTAssertTrue(submitButton.waitForExistence(timeout: 5))
        if !submitButton.isHittable {
            app.swipeUp()
        }
        submitButton.tap()

        let banner = app.descendants(matching: .any).matching(identifier: "login.bannerMessage").firstMatch
        XCTAssertTrue(
            banner.waitForExistence(timeout: 20),
            "Demo host probe must surface an HTTP error banner"
        )

        var bannerText = banner.label.trimmingCharacters(in: .whitespacesAndNewlines)
        if bannerText.isEmpty {
            bannerText = banner.staticTexts.allElementsBoundByIndex
                .map(\.label)
                .joined(separator: " ")
                .trimmingCharacters(in: .whitespacesAndNewlines)
        }
        XCTAssertFalse(bannerText.isEmpty, "Demo error banner must contain non-empty text")
        XCTAssertFalse(
            bannerText.contains("Không có kết nối mạng") || bannerText.contains("No internet connection"),
            "Demo build must receive a server HTTP response rather than an offline error: \(bannerText)"
        )

        let loginURL = try XCTUnwrap(URL(string: "\(demoBaseURL)/api/v1/auth/mobile/login"))
        var loginRequest = URLRequest(url: loginURL)
        loginRequest.httpMethod = "POST"
        loginRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
        loginRequest.httpBody = Data(#"{"identifier":"qa.demo.probe","password":"ProbePass1!"}"#.utf8)
        let loginStatus = try performSyncHTTPSStatus(loginRequest)
        XCTAssertTrue(
            (100...599).contains(loginStatus),
            "Expected valid HTTP status from demo mobile login probe, got \(loginStatus)"
        )

        let appearanceURL = try XCTUnwrap(URL(string: "\(demoBaseURL)/api/v1/appearance"))
        var appearanceRequest = URLRequest(url: appearanceURL)
        appearanceRequest.httpMethod = "GET"
        let appearanceStatus = try performSyncHTTPSStatus(appearanceRequest)
        XCTAssertTrue(
            (100...599).contains(appearanceStatus),
            "Expected valid HTTP status from demo appearance probe, got \(appearanceStatus)"
        )
    }

    private func performSyncHTTPSStatus(_ request: URLRequest) throws -> Int {
        let semaphore = DispatchSemaphore(value: 0)
        var capturedResponse: URLResponse?
        var capturedError: Error?

        let task = URLSession.shared.dataTask(with: request) { _, response, error in
            capturedResponse = response
            capturedError = error
            semaphore.signal()
        }
        task.resume()

        if semaphore.wait(timeout: .now() + 20) == .timedOut {
            task.cancel()
            throw URLError(.timedOut)
        }
        if let capturedError {
            throw capturedError
        }
        let http = try XCTUnwrap(capturedResponse as? HTTPURLResponse)
        return http.statusCode
    }
}
