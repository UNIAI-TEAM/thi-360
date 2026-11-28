import Foundation
import XCTest

final class PortalStateRestorationUITests: XCTestCase {
    private struct IndexedFixtureEntry: Decodable {
        let file: String
        let operationId: String
        let status: Int
        let variant: String?
    }

    private var server: ContractFixtureHTTPServer?
    private var attemptId: String = ""
    private var shortAnswerItemId: String = ""
    private var expectedExamName: String = ""

    override func setUpWithError() throws {
        continueAfterFailure = false
        let runAttemptId = UUID().uuidString.lowercased()
        let mockServer = try ContractFixtureHTTPServer()
        try mockServer.registerStandardAuthFixtures()
        let metadata = try registerAttemptFixtures(on: mockServer, attemptId: runAttemptId)
        try mockServer.start()
        self.server = mockServer
        self.attemptId = runAttemptId
        self.shortAnswerItemId = metadata.shortAnswerItemId
        self.expectedExamName = metadata.examName
    }

    override func tearDownWithError() throws {
        XCUIDevice.shared.orientation = .portrait
        server?.stop()
        server = nil
    }

    func testPortalRoutePreservesParametersDraftAndScrollAcrossRotationAndSizeClassToggle() throws {
        let server = try XCTUnwrap(server)
        let suite = "vn.unicom.thi360.uitest.portalstate.\(UUID().uuidString)"
        let app = XCUIApplication()
        app.launchArguments = [
            "-uitest-defaults-suite", suite,
            "-uitest-reset-preferences",
            "-uitest-state-probe",
            "-uitest-authenticated",
            "-uitest-destination", "home",
            "-uitest-route", "attempt/\(attemptId)",
            "-uitest-api-base-url", "http://127.0.0.1:\(server.port)"
        ]
        app.launch()

        let examTitle = app.staticTexts["player.topBar.examTitle"].firstMatch
        XCTAssertTrue(examTitle.waitForExistence(timeout: 10), "Expected real attempt portal route to load")
        XCTAssertEqual(examTitle.label, expectedExamName)

        let draftFieldId = "question.\(shortAnswerItemId).shortAnswerField"
        let flagButtonId = "question.\(shortAnswerItemId).flagButton"
        let draftField = app.textFields[draftFieldId].firstMatch
        scrollUntilExistsAndHittable(draftField, in: app)
        XCTAssertTrue(draftField.waitForExistence(timeout: 6), "Expected SHORT_ANSWER draft field on certified fixture")

        let sample = "Portal draft 99"
        enterText(sample, into: draftField, in: app)
        XCTAssertEqual(draftField.value as? String, sample)

        let flagButton = app.buttons[flagButtonId].firstMatch
        scrollUntilExistsAndHittable(flagButton, in: app)
        XCTAssertTrue(flagButton.waitForExistence(timeout: 5))
        flagButton.tap()
        XCTAssertEqual(flagButton.value as? String, "1")

        if examTitle.isHittable {
            examTitle.tap()
        }

        let statePath = "/api/v1/me/attempts/\(attemptId)"
        let getStateCountBefore = server.callCount(method: "GET", path: statePath)
        XCTAssertGreaterThanOrEqual(getStateCountBefore, 1)

        XCUIDevice.shared.orientation = .landscapeLeft
        Thread.sleep(forTimeInterval: 0.5)
        assertRestoredState(in: app, draftFieldId: draftFieldId, flagButtonId: flagButtonId, expectedDraft: sample)

        XCUIDevice.shared.orientation = .portrait
        Thread.sleep(forTimeInterval: 0.5)
        assertRestoredState(in: app, draftFieldId: draftFieldId, flagButtonId: flagButtonId, expectedDraft: sample)

        let toggleButton = app.buttons["player.probe.toggleSizeClass"].firstMatch
        XCTAssertTrue(toggleButton.waitForExistence(timeout: 5), "Expected -uitest-state-probe size-class toggle")
        toggleButton.tap()

        XCTAssertTrue(
            app.staticTexts["player.grid.title"].firstMatch.waitForExistence(timeout: 5),
            "Widening horizontalSizeClass must display wide sidebar question grid"
        )
        assertRestoredState(in: app, draftFieldId: draftFieldId, flagButtonId: flagButtonId, expectedDraft: sample)

        // OrientationPolicy allows landscape on phone when horizontalSizeClass is regular.
        XCUIDevice.shared.orientation = .landscapeLeft
        Thread.sleep(forTimeInterval: 0.5)
        XCTAssertTrue(app.staticTexts["player.grid.title"].firstMatch.waitForExistence(timeout: 5))
        assertRestoredState(in: app, draftFieldId: draftFieldId, flagButtonId: flagButtonId, expectedDraft: sample)
        XCUIDevice.shared.orientation = .portrait
        Thread.sleep(forTimeInterval: 0.5)

        let toggleBack = app.buttons["player.probe.toggleSizeClass"].firstMatch
        XCTAssertTrue(toggleBack.waitForExistence(timeout: 5))
        toggleBack.tap()

        XCTAssertTrue(
            app.buttons["player.bottomBar.openGrid"].firstMatch.waitForExistence(timeout: 5),
            "Returning to compact horizontalSizeClass must restore compact grid trigger"
        )
        assertRestoredState(in: app, draftFieldId: draftFieldId, flagButtonId: flagButtonId, expectedDraft: sample)
        XCTAssertEqual(
            server.callCount(method: "GET", path: statePath),
            getStateCountBefore,
            "Rotation and size-class toggles must preserve state without re-fetching GET /attempts/{id}"
        )
    }

    private func assertRestoredState(
        in app: XCUIApplication,
        draftFieldId: String,
        flagButtonId: String,
        expectedDraft: String
    ) {
        let title = app.staticTexts["player.topBar.examTitle"].firstMatch
        XCTAssertTrue(title.waitForExistence(timeout: 5))
        XCTAssertEqual(title.label, expectedExamName)
        let restoredField = app.textFields[draftFieldId].firstMatch
        XCTAssertTrue(restoredField.waitForExistence(timeout: 5))
        XCTAssertEqual(restoredField.value as? String, expectedDraft)
        let restoredFlag = app.buttons[flagButtonId].firstMatch
        XCTAssertTrue(restoredFlag.waitForExistence(timeout: 5))
        XCTAssertEqual(restoredFlag.value as? String, "1")
    }

    private static func isoTimestamp(_ date: Date = Date()) -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter.string(from: date)
    }

    private func registerAttemptFixtures(
        on mockServer: ContractFixtureHTTPServer,
        attemptId: String
    ) throws -> (shortAnswerItemId: String, examName: String) {
        let indexData = try ContractFixtureHTTPServer.loadFixture("api/index.json")
        let entries = try JSONDecoder().decode([IndexedFixtureEntry].self, from: indexData)

        func loadFixtureData(_ operationId: String, status: Int, variant: String = "") throws -> Data {
            let match = try XCTUnwrap(entries.first {
                $0.operationId == operationId && $0.status == status && ($0.variant ?? "") == variant
            })
            let relativePath = match.file.contains("/") ? match.file : "api/\(match.file)"
            return try ContractFixtureHTTPServer.loadFixture(relativePath)
        }

        let now = Date()
        let futureDeadline = Self.isoTimestamp(now.addingTimeInterval(1800))

        let stateTemplateData = try loadFixtureData("getState", status: 200, variant: "in-progress-per-part")
        var stateObj = try XCTUnwrap(JSONSerialization.jsonObject(with: stateTemplateData) as? [String: Any])
        stateObj["attemptId"] = attemptId
        stateObj["serverNow"] = Self.isoTimestamp(now)
        if stateObj["deadlineAt"] != nil && !(stateObj["deadlineAt"] is NSNull) {
            stateObj["deadlineAt"] = futureDeadline
        }
        var shortAnswerId = ""
        if var parts = stateObj["parts"] as? [[String: Any]] {
            for index in parts.indices {
                if parts[index]["state"] as? String == "OPEN",
                   parts[index]["deadlineAt"] != nil && !(parts[index]["deadlineAt"] is NSNull)
                {
                    parts[index]["deadlineAt"] = futureDeadline
                }
                if shortAnswerId.isEmpty, let items = parts[index]["items"] as? [[String: Any]] {
                    if let shortAnswer = items.first(where: { ($0["questionType"] as? String) == "SHORT_ANSWER" }),
                       let itemId = shortAnswer["itemId"] as? String
                    {
                        shortAnswerId = itemId
                    }
                }
            }
            stateObj["parts"] = parts
        }
        let resolvedShortAnswerId = try XCTUnwrap(shortAnswerId.isEmpty ? nil : shortAnswerId)
        let resolvedExamName = try XCTUnwrap(stateObj["examName"] as? String)
        let stateData = try JSONSerialization.data(withJSONObject: stateObj)
        mockServer.register(method: "GET", path: "/api/v1/me/attempts/\(attemptId)", statusCode: 200, body: stateData)

        let claimTemplateData = try loadFixtureData("claimSession", status: 200)
        mockServer.register(method: "POST", path: "/api/v1/me/attempts/\(attemptId)/session") { _, _, body in
            let req = (try? JSONSerialization.jsonObject(with: body) as? [String: Any]) ?? [:]
            var responseObj = (try? JSONSerialization.jsonObject(with: claimTemplateData) as? [String: Any]) ?? [:]
            if let requestedSessionId = req["sessionId"] as? String, !requestedSessionId.isEmpty {
                responseObj["sessionId"] = requestedSessionId
            }
            responseObj["serverNow"] = Self.isoTimestamp()
            let data = (try? JSONSerialization.data(withJSONObject: responseObj)) ?? Data()
            return ContractHTTPResponse(statusCode: 200, body: data)
        }

        let heartbeatTemplateData = try loadFixtureData("heartbeat", status: 200)
        mockServer.register(method: "POST", path: "/api/v1/me/attempts/\(attemptId)/heartbeat") { _, _, _ in
            var responseObj = (try? JSONSerialization.jsonObject(with: heartbeatTemplateData) as? [String: Any]) ?? [:]
            responseObj["serverNow"] = Self.isoTimestamp()
            responseObj["state"] = "IN_PROGRESS"
            let data = (try? JSONSerialization.data(withJSONObject: responseObj)) ?? Data()
            return ContractHTTPResponse(statusCode: 200, body: data)
        }

        let saveTemplateData = try loadFixtureData("saveAnswers", status: 200, variant: "saved")
        mockServer.register(method: "PUT", path: "/api/v1/me/attempts/\(attemptId)/answers") { _, _, body in
            let req = (try? JSONSerialization.jsonObject(with: body) as? [String: Any]) ?? [:]
            var savedResults: [[String: Any]] = []
            if let changes = req["changes"] as? [[String: Any]] {
                for change in changes {
                    if let itemId = change["itemId"] as? String, let seq = change["seq"] as? NSNumber {
                        let entry: [String: Any] = [
                            "itemId": itemId,
                            "seq": seq.int64Value,
                            "status": "SAVED",
                            "reason": NSNull()
                        ]
                        savedResults.append(entry)
                    }
                }
            }
            var responseObj = (try? JSONSerialization.jsonObject(with: saveTemplateData) as? [String: Any]) ?? [:]
            responseObj["results"] = savedResults
            responseObj["serverNow"] = Self.isoTimestamp()
            let data = (try? JSONSerialization.data(withJSONObject: responseObj)) ?? Data()
            return ContractHTTPResponse(statusCode: 200, body: data)
        }

        return (shortAnswerItemId: resolvedShortAnswerId, examName: resolvedExamName)
    }

    private func scrollUntilExistsAndHittable(_ element: XCUIElement, in app: XCUIApplication, maxSwipes: Int = 6) {
        for _ in 0..<maxSwipes {
            if element.exists && element.isHittable {
                return
            }
            app.swipeUp()
        }
    }

    private func enterText(_ text: String, into field: XCUIElement, in app: XCUIApplication) {
        field.tap()
        _ = app.keyboards.firstMatch.waitForExistence(timeout: 3.0)
        field.typeText(text)
        if (field.value as? String) == text {
            return
        }
        field.tap()
        _ = app.keyboards.firstMatch.waitForExistence(timeout: 2.0)
        let currentLength = ((field.value as? String) ?? "").count
        if currentLength > 0 {
            field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: currentLength + 4))
        }
        field.typeText(text)
    }
}
