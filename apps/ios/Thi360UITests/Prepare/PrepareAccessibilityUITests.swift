import Foundation
import XCTest

final class PrepareAccessibilityUITests: XCTestCase {
    private var server: PrepareFixtureHTTPServer?

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    override func tearDownWithError() throws {
        server?.stop()
        server = nil
    }

    func testPrepareDynamicType130NoClippingAnd200ScrollsToFinalButton() throws {
        let contractsDir = PrepareUITests.contractsMobileDirectory()
        let perPartData = try PrepareUITests.loadIndexedFixture(
            contractsMobileDir: contractsDir,
            operationId: "prepare",
            status: 200,
            variant: "per-part"
        )
        let notFoundData = try PrepareUITests.loadIndexedFixture(
            contractsMobileDir: contractsDir,
            operationId: "prepare",
            status: 404,
            variant: "not-found"
        )

        let fixtureServer = try PrepareUITests.makeStandardServer(contractsDir: contractsDir) { method, path in
            if method == "GET", path == "/api/v1/me/entitlements/ent-a11y-prep/prepare" {
                return (200, perPartData)
            }
            return (404, notFoundData)
        }
        try fixtureServer.start()
        self.server = fixtureServer

        do {
            let app = launchApp(
                baseURL: "http://127.0.0.1:\(fixtureServer.port)",
                route: "prepare/ent-a11y-prep",
                contentSizeCategory: "UICTContentSizeCategoryXXXL"
            )
            let windowWidth = app.windows.firstMatch.frame.width
            let title = app.staticTexts["prepare.header.title"].firstMatch
            XCTAssertTrue(title.waitForExistence(timeout: 10), "Prepare title must exist at 130% Dynamic Type")
            XCTAssertLessThanOrEqual(title.frame.maxX, windowWidth + 1.0, "Prepare title must not clip horizontally at 130%")

            let startButton = app.buttons["prepare.startButton"].firstMatch
            XCTAssertTrue(startButton.waitForExistence(timeout: 6), "Start button must exist at 130%")
            XCTAssertGreaterThanOrEqual(startButton.frame.height, 43.5, "Start button touch target must be >= 44pt")
            XCTAssertLessThanOrEqual(startButton.frame.maxX, windowWidth + 1.0, "Start button must fit within viewport at 130%")
            app.terminate()
        }

        do {
            let app = launchApp(
                baseURL: "http://127.0.0.1:\(fixtureServer.port)",
                route: "prepare/ent-a11y-prep",
                contentSizeCategory: "UICTContentSizeCategoryAccessibilityLarge"
            )
            let windowWidth = app.windows.firstMatch.frame.width
            let title = app.staticTexts["prepare.header.title"].firstMatch
            XCTAssertTrue(title.waitForExistence(timeout: 10), "Prepare title must exist at 200% Dynamic Type")

            let startButton = app.buttons["prepare.startButton"].firstMatch
            scrollUntilHittable(startButton, in: app, maxSwipes: 16)
            XCTAssertTrue(startButton.exists && startButton.isHittable, "Start button must be reachable by scrolling at 200%")
            XCTAssertGreaterThanOrEqual(startButton.frame.height, 43.5)
            XCTAssertLessThanOrEqual(startButton.frame.maxX, windowWidth + 1.0)

            let backToMyExams = app.buttons["prepare.backToMyExamsButton"].firstMatch
            scrollUntilHittable(backToMyExams, in: app, maxSwipes: 16)
            XCTAssertTrue(backToMyExams.exists && backToMyExams.isHittable, "Final Back to My Exams button must be reachable by scrolling at 200%")
            XCTAssertGreaterThanOrEqual(backToMyExams.frame.height, 43.5)
            app.terminate()
        }
    }

    func testWaitingDynamicType130NoClippingAnd200ScrollsToFinalButton() throws {
        let contractsDir = PrepareUITests.contractsMobileDirectory()
        let openStartingData = try PrepareUITests.loadIndexedFixture(
            contractsMobileDir: contractsDir,
            operationId: "prepare",
            status: 200,
            variant: "open-starting"
        )
        let abortedStateData = try PrepareUITests.loadIndexedFixture(
            contractsMobileDir: contractsDir,
            operationId: "getState",
            status: 200,
            variant: "aborted"
        )
        let notFoundData = try PrepareUITests.loadIndexedFixture(
            contractsMobileDir: contractsDir,
            operationId: "prepare",
            status: 404,
            variant: "not-found"
        )

        let fixtureServer = try PrepareUITests.makeStandardServer(contractsDir: contractsDir) { method, path in
            if method == "GET", path == "/api/v1/me/entitlements/ent-a11y-wait/prepare" {
                return (200, openStartingData)
            }
            if method == "GET", path == "/api/v1/me/attempts/00000000-0000-7000-8000-000000000003" {
                return (200, abortedStateData)
            }
            return (404, notFoundData)
        }
        try fixtureServer.start()
        self.server = fixtureServer

        do {
            let app = launchApp(
                baseURL: "http://127.0.0.1:\(fixtureServer.port)",
                route: "prepare/ent-a11y-wait",
                contentSizeCategory: "UICTContentSizeCategoryXXXL"
            )
            let windowWidth = app.windows.firstMatch.frame.width
            let failedHeading = app.staticTexts["waiting.failedHeading"].firstMatch
            XCTAssertTrue(failedHeading.waitForExistence(timeout: 10), "Waiting failed heading must appear at 130%")
            XCTAssertLessThanOrEqual(failedHeading.frame.maxX, windowWidth + 1.0, "Waiting heading must not clip at 130%")

            let retryButton = app.buttons["waiting.retryButton"].firstMatch
            scrollUntilHittable(retryButton, in: app, maxSwipes: 6)
            XCTAssertTrue(retryButton.exists && retryButton.isHittable)
            XCTAssertGreaterThanOrEqual(retryButton.frame.height, 43.5)
            XCTAssertLessThanOrEqual(retryButton.frame.maxX, windowWidth + 1.0)
            app.terminate()
        }

        do {
            let app = launchApp(
                baseURL: "http://127.0.0.1:\(fixtureServer.port)",
                route: "prepare/ent-a11y-wait",
                contentSizeCategory: "UICTContentSizeCategoryAccessibilityLarge"
            )
            let windowWidth = app.windows.firstMatch.frame.width
            let failedHeading = app.staticTexts["waiting.failedHeading"].firstMatch
            XCTAssertTrue(failedHeading.waitForExistence(timeout: 10), "Waiting failed heading must appear at 200%")

            let backButton = app.buttons["waiting.backToMyExamsButton"].firstMatch
            scrollUntilHittable(backButton, in: app, maxSwipes: 16)
            XCTAssertTrue(backButton.exists && backButton.isHittable, "Final Back to My Exams button must be reachable by scrolling at 200%")
            XCTAssertGreaterThanOrEqual(backButton.frame.height, 43.5)
            XCTAssertLessThanOrEqual(backButton.frame.maxX, windowWidth + 1.0)
            app.terminate()
        }
    }

    private func launchApp(
        baseURL: String,
        route: String,
        contentSizeCategory: String
    ) -> XCUIApplication {
        let suite = "vn.unicom.thi360.uitest.prepare.a11y.\(UUID().uuidString)"
        let app = XCUIApplication()
        app.launchArguments = [
            "-uitest-defaults-suite", suite,
            "-uitest-reset-preferences",
            "-uitest-authenticated",
            "-uitest-api-base-url", baseURL,
            "-uitest-destination", "myExams",
            "-uitest-route", route,
            "-UIPreferredContentSizeCategoryName", contentSizeCategory
        ]
        app.launch()
        return app
    }

    private func scrollUntilHittable(_ element: XCUIElement, in app: XCUIApplication, maxSwipes: Int = 10) {
        for _ in 0..<maxSwipes {
            if element.exists && element.isHittable {
                return
            }
            app.swipeUp()
        }
    }
}
