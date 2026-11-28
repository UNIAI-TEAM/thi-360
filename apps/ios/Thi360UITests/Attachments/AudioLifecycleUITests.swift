import Foundation
import XCTest

final class AudioLifecycleUITests: XCTestCase {
    private var server: AttachmentsUITestServer?

    override func setUpWithError() throws {
        continueAfterFailure = false
        // Start audio at 00:14 with 6s progress cadence and server save capped at 00:20 (20_000 ms),
        // so after ~11s audio is at 00:25 while server-saved position is strictly 00:20 (AC-14).
        let mock = try AttachmentsUITestServer(
            audioFileName: "silence-35s.wav",
            audioDurationMs: 35_000,
            initialOpenPositionMs: 14_000,
            capSavedProgressAtMs: 20_000
        )
        try mock.start()
        self.server = mock
        SimulatorBiometricHelper.setEnrolled(true)
    }

    override func tearDownWithError() throws {
        SimulatorBiometricHelper.setEnrolled(false)
        server?.stop()
        server = nil
    }

    func testHomeStopsWithinOneSecondAndBiometricLockReturnsToResumeWithoutAutoplay() throws {
        let server = try XCTUnwrap(server)
        let suite = "vn.unicom.thi360.uitest.audio.lifecycle.\(UUID().uuidString)"
        let keychainService = "vn.unicom.thi360.uitest.audio.lifecycle.kc.\(UUID().uuidString)"

        let app = XCUIApplication()
        app.launchArguments = [
            "-uitest-defaults-suite", suite,
            "-uitest-reset-preferences",
            "-uitest-keychain-service", keychainService,
            "-uitest-reset-session",
            "-uitest-lock-timeout", "2",
            "-uitest-attachments-harness", "audioLifecycle",
            "-uitest-audio-progress-ms", "6000",
            "-uitest-api-base-url", "http://127.0.0.1:\(server.port)"
        ]
        app.launch()

        // 1. Authenticate and enable Face ID so we also verify real LockView after background timeout.
        let identifierField = app.textFields["login.identifierField"]
        XCTAssertTrue(identifierField.waitForExistence(timeout: 10))
        identifierField.tap()
        identifierField.typeText("student.demo")

        let passwordField = app.secureTextFields["login.passwordField"]
        XCTAssertTrue(passwordField.waitForExistence(timeout: 5))
        passwordField.tap()
        passwordField.typeText("Password123!")
        app.buttons["login.submitButton"].tap()

        let enableBiometricButton = app.buttons["biometric.enable.confirmButton"]
        XCTAssertTrue(enableBiometricButton.waitForExistence(timeout: 10))
        enableBiometricButton.tap()
        SimulatorBiometricHelper.acceptFaceIDPermissionIfPresent(timeout: 2.5)

        let playButton = app.buttons["audio.primaryButton.\(AttachmentsUITestServer.audioAssetId)"].firstMatch
        waitForBiometricMatch(until: playButton, timeout: 12)
        XCTAssertTrue(playButton.exists, "Unlocking/enabling biometric must land on Attachments harness")

        // Wait until AttemptEngine claims session and enables Play ("Nghe").
        let enabledPredicate = NSPredicate(format: "isEnabled == true")
        expectation(for: enabledPredicate, evaluatedWith: playButton)
        waitForExpectations(timeout: 10)
        XCTAssertEqual(playButton.label, "Nghe")

        // 2. Tap "Nghe" and wait until playback passes 00:20 (progress saved at 00:20) and reaches ~00:24..00:25.
        playButton.tap()
        let timeLabel = app.staticTexts["audio.timeLabel.\(AttachmentsUITestServer.audioAssetId)"].firstMatch
        XCTAssertTrue(timeLabel.waitForExistence(timeout: 6))

        let reached24 = waitUntil(timeout: 16) {
            server.callCount(method: "PUT", pathContains: "/progress") >= 1
                && (timeLabel.label.hasPrefix("00:24") || timeLabel.label.hasPrefix("00:25") || timeLabel.label.hasPrefix("00:26"))
        }
        XCTAssertTrue(reached24, "Expected progress saved at 00:20 and live playback around 00:25; got '\(timeLabel.label)'")

        // 3. Press Home -> must stop in < 1s and not play in background; return immediately (< 2s lock timeout).
        XCUIDevice.shared.press(.home)
        Thread.sleep(forTimeInterval: 0.7)
        app.activate()

        XCTAssertTrue(playButton.waitForExistence(timeout: 8))
        XCTAssertEqual(playButton.label, "Phát lại", "Returning from Home must show 'Phát lại', never auto-play (AC-14)")
        let banner = app.staticTexts["audio.banner.\(AttachmentsUITestServer.audioAssetId)"].firstMatch
        XCTAssertTrue(banner.waitForExistence(timeout: 4))
        XCTAssertEqual(banner.label, "Âm thanh đã tạm dừng. Bấm Phát lại để nghe tiếp mà không mất lượt nghe.")

        let telemetry = app.staticTexts["harness.attachments.telemetry"].firstMatch
        XCTAssertTrue(telemetry.label.contains("PLAYING:false"), "Audio must be stopped after Home: \(telemetry.label)")
        let stopMs = parseTelemetryInt(key: "STOP_MS", from: telemetry.label)
        XCTAssertNotNil(stopMs)
        XCTAssertLessThan(stopMs ?? 9_999, 1_000, "Audio must stop within < 1s on Home (AC-14): \(telemetry.label)")
        XCTAssertEqual(server.callCount(method: "POST", pathContains: "/resume"), 0, "Must not auto-resume on foreground")

        // 4. Tap "Phát lại" -> calls resume, resumes from 00:20 (server saved), playsLeft stays 1/2.
        playButton.tap()
        let resumedPlaying = waitUntil(timeout: 8) {
            server.callCount(method: "POST", pathContains: "/resume") == 1
                && playButton.label == "Đang phát…"
        }
        XCTAssertTrue(resumedPlaying, "Tapping 'Phát lại' must call resume and enter 'Đang phát…'")
        XCTAssertTrue(
            timeLabel.label.hasPrefix("00:20") || timeLabel.label.hasPrefix("00:21") || timeLabel.label.hasPrefix("00:22"),
            "Resumed playback must continue from server-saved 00:20, got '\(timeLabel.label)'"
        )
        let badge = app.staticTexts["audio.playsBadge.\(AttachmentsUITestServer.audioAssetId)"].firstMatch
        XCTAssertEqual(badge.label, "Còn 1/2 lần nghe", "Resuming must not consume another play")

        // 5. Background past biometric lock timeout (2s) -> unlock Face ID -> still shows "Phát lại", no auto-play.
        XCUIDevice.shared.press(.home)
        Thread.sleep(forTimeInterval: 4.5)
        app.activate()

        let unlockButton = app.buttons["lock.unlockBiometricButton"].firstMatch
        XCTAssertTrue(unlockButton.waitForExistence(timeout: 12), "Returning past lock timeout must present Lock screen")
        unlockButton.tap()
        SimulatorBiometricHelper.acceptFaceIDPermissionIfPresent(timeout: 1.5)
        waitForBiometricMatch(until: playButton, timeout: 12)

        XCTAssertTrue(playButton.exists)
        XCTAssertEqual(playButton.label, "Phát lại", "Unlocking Face ID must return to 'Phát lại' without auto-play (AC-14)")
        XCTAssertTrue(telemetry.label.contains("PLAYING:false"))
        XCTAssertEqual(server.callCount(method: "POST", pathContains: "/resume"), 1, "Unlocking Face ID must not auto-call resume")
    }

    private func waitForBiometricMatch(until element: XCUIElement, timeout: TimeInterval) {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if element.exists { return }
            SimulatorBiometricHelper.triggerMatch()
            if element.waitForExistence(timeout: 1.0) { return }
        }
    }

    private func waitUntil(timeout: TimeInterval, predicate: () -> Bool) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if predicate() { return true }
            Thread.sleep(forTimeInterval: 0.2)
        }
        return predicate()
    }

    private func parseTelemetryInt(key: String, from raw: String) -> Int? {
        for part in raw.split(separator: "|") {
            let kv = part.split(separator: ":", maxSplits: 1)
            if kv.count == 2 && kv[0] == key {
                return Int(kv[1])
            }
        }
        return nil
    }
}
