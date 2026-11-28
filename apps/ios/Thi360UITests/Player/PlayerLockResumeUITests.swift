import Foundation
import XCTest

final class PlayerLockResumeUITests: XCTestCase {
    private var server: PlayerUITestFixtureServer?

    override func setUpWithError() throws {
        continueAfterFailure = false
        SimulatorBiometricHelper.setEnrolled(true)
    }

    override func tearDownWithError() throws {
        SimulatorBiometricHelper.setEnrolled(false)
        server?.stop()
        server = nil
    }

    func testBiometricLockHidesPlayerRefreshesOnUnlockAndHandlesClosedWhileLocked() throws {
        let mock = try PlayerUITestFixtureServer(
            attemptId: "00000000-0000-7000-8000-000000000301",
            durationSeconds: 900
        )
        self.server = mock

        let suite = "vn.unicom.thi360.uitest.playerlock.\(UUID().uuidString)"
        let keychainService = "vn.unicom.thi360.uitest.playerlock.kc.\(UUID().uuidString)"
        let app = XCUIApplication()
        app.launchArguments = [
            "-uitest-defaults-suite", suite,
            "-uitest-reset-preferences",
            "-uitest-keychain-service", keychainService,
            "-uitest-reset-session",
            "-uitest-lock-timeout", "2",
            "-uitest-route", "attempt/\(mock.attemptId)",
            "-uitest-api-base-url", mock.baseURL
        ]
        app.launch()

        let idField = app.textFields["login.identifierField"].firstMatch
        XCTAssertTrue(idField.waitForExistence(timeout: 8))
        idField.tap()
        idField.typeText("student.demo")
        let pwField = app.secureTextFields["login.passwordField"].firstMatch
        XCTAssertTrue(pwField.waitForExistence(timeout: 5))
        pwField.tap()
        pwField.typeText("Password123!")
        app.buttons["login.submitButton"].firstMatch.tap()

        let enableBtn = app.buttons["biometric.enable.confirmButton"].firstMatch
        XCTAssertTrue(enableBtn.waitForExistence(timeout: 8))
        enableBtn.tap()
        PortalBiometricUITestHelper.acceptFaceIDPermissionIfPresent(timeout: 2.5)

        let examTitle = app.staticTexts["player.topBar.examTitle"].firstMatch
        PortalBiometricUITestHelper.waitForBiometricMatch(until: examTitle, timeout: 10)
        XCTAssertTrue(examTitle.exists)

        let timerEl = app.otherElements["player.topBar.timer"].firstMatch
        XCTAssertTrue(timerEl.waitForExistence(timeout: 5))
        let timerBeforeLock = timerEl.label
        let countsBeforeLock = mock.counts

        XCUIDevice.shared.press(.home)
        Thread.sleep(forTimeInterval: 4.0)
        app.activate()

        let unlockBtn = app.buttons["lock.unlockBiometricButton"].firstMatch
        XCTAssertTrue(unlockBtn.waitForExistence(timeout: 10), "Lock screen must appear before player after timeout")
        XCTAssertFalse(examTitle.exists, "Exam questions must NOT be visible behind lock screen")

        unlockBtn.tap()
        PortalBiometricUITestHelper.acceptFaceIDPermissionIfPresent(timeout: 1.5)
        PortalBiometricUITestHelper.waitForBiometricMatch(until: examTitle, timeout: 10)
        XCTAssertTrue(examTitle.exists)

        Thread.sleep(forTimeInterval: 1.5)
        let countsAfterUnlock = mock.counts
        XCTAssertGreaterThan(countsAfterUnlock.getState, countsBeforeLock.getState, "Unlock must refresh attempt state via R-99")
        XCTAssertNotEqual(app.otherElements["player.topBar.timer"].firstMatch.label, timerBeforeLock, "Timer must deduct locked time")

        try mock.transitionToClosedTimeUp()
        XCUIDevice.shared.press(.home)
        Thread.sleep(forTimeInterval: 3.5)
        app.activate()
        XCTAssertTrue(unlockBtn.waitForExistence(timeout: 10))
        unlockBtn.tap()
        PortalBiometricUITestHelper.acceptFaceIDPermissionIfPresent(timeout: 1.5)

        let closedHeading = app.staticTexts["player.closed.heading"].firstMatch
        PortalBiometricUITestHelper.waitForBiometricMatch(until: closedHeading, timeout: 12)
        XCTAssertTrue(closedHeading.exists, "Attempt closed while locked must land on Closed screen after unlock")
    }
}
