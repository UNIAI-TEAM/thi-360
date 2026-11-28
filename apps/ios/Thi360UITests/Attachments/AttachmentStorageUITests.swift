import Foundation
import XCTest

final class AttachmentStorageUITests: XCTestCase {
    private var server: AttachmentsUITestServer?

    override func setUpWithError() throws {
        continueAfterFailure = false
        let mock = try AttachmentsUITestServer(
            audioFileName: "silence-2s.wav",
            audioDurationMs: 2_000,
            initialOpenPositionMs: 0
        )
        try mock.start()
        self.server = mock
    }

    override func tearDownWithError() throws {
        server?.stop()
        server = nil
    }

    func testNoResidualAudioOrTempFilesAfterPlaybackMidViewerCrashLeaveAndLogout() throws {
        let server = try XCTUnwrap(server)
        let pdfBytes = try AttachmentsUITestServer.loadAttachmentResource("bang-so-lieu.pdf")
        let audioBytes = try AttachmentsUITestServer.loadAttachmentResource("silence-2s.wav")
        let imageBytes = AttachmentsUITestServer.samplePNGData()
        let forbiddenPayloads = [pdfBytes, audioBytes, imageBytes]

        let suite = "vn.unicom.thi360.uitest.audio.storage.\(UUID().uuidString)"
        let keychainService = "vn.unicom.thi360.uitest.audio.storage.kc.\(UUID().uuidString)"

        let app = XCUIApplication()
        app.launchArguments = [
            "-uitest-defaults-suite", suite,
            "-uitest-reset-preferences",
            "-uitest-keychain-service", keychainService,
            "-uitest-reset-session",
            "-uitest-authenticated",
            "-uitest-attachments-harness", "storage",
            "-uitest-api-base-url", "http://127.0.0.1:\(server.port)"
        ]
        app.launch()

        let containerPathLabel = app.staticTexts["harness.attachments.containerPath"].firstMatch
        XCTAssertTrue(containerPathLabel.waitForExistence(timeout: 10))
        let containerPath = containerPathLabel.label
        XCTAssertFalse(containerPath.isEmpty)

        // (a) Play 2s audio to completion, load image, leave attempt -> zero audio/image files on disk.
        let loadImageButton = app.buttons["harness.attachments.loadImage"].firstMatch
        XCTAssertTrue(loadImageButton.waitForExistence(timeout: 6))
        loadImageButton.tap()

        let playButton = app.buttons["audio.primaryButton.\(AttachmentsUITestServer.audioAssetId)"].firstMatch
        let enabledPredicate = NSPredicate(format: "isEnabled == true")
        expectation(for: enabledPredicate, evaluatedWith: playButton)
        waitForExpectations(timeout: 10)
        playButton.tap()

        let endedOnServer = waitUntil(timeout: 10) {
            server.callCount(method: "POST", pathContains: "/end") >= 1
        }
        XCTAssertTrue(endedOnServer, "2s audio must play to completion and call end")

        let telemetry = app.staticTexts["harness.attachments.telemetry"].firstMatch
        let audioReleased = waitUntil(timeout: 6) {
            telemetry.label.contains("PLAYING:false") && telemetry.label.contains("DATA:false")
        }
        XCTAssertTrue(audioReleased, "In-memory audio Data must be dropped when play ends: \(telemetry.label)")

        // (b) Open PDF viewer and terminate app abruptly mid-viewer -> cold relaunch must purge before first screen!
        let pdfButton = app.buttons["attachments.file.\(AttachmentsUITestServer.pdfAssetId)"].firstMatch
        XCTAssertTrue(pdfButton.waitForExistence(timeout: 6))
        pdfButton.tap()

        let closeButton = app.buttons["attachments.viewer.closeButton"].firstMatch
        XCTAssertTrue(closeButton.waitForExistence(timeout: 10))
        XCTAssertEqual(
            AttachmentsSandboxInspector.attemptAssetTempFiles(under: containerPath).count,
            1,
            "Temp PDF file should exist while viewer is open before crash"
        )

        app.terminate()

        // Cold relaunch without clearing caches -> SessionCleanup.runAppStart() must purge Caches/AttemptAssets/ before mount!
        app.launch()
        let telemetryAfterCrash = app.staticTexts["harness.attachments.telemetry"].firstMatch
        XCTAssertTrue(telemetryAfterCrash.waitForExistence(timeout: 10))
        XCTAssertTrue(
            telemetryAfterCrash.label.contains("MOUNT_COUNT:0"),
            "Cold start after mid-viewer termination must purge temp files before first screen mounts: \(telemetryAfterCrash.label)"
        )
        XCTAssertEqual(
            AttachmentsSandboxInspector.attemptAssetTempFiles(under: containerPath),
            [],
            "Zero files may remain in Caches/AttemptAssets/ after cold-start cleanup"
        )

        // (c) Open PDF, close it, tap Leave Attempt, tap Logout -> Login screen shown & sandbox has 0 residual files (AC-21).
        let pdfButton2 = app.buttons["attachments.file.\(AttachmentsUITestServer.pdfAssetId)"].firstMatch
        XCTAssertTrue(pdfButton2.waitForExistence(timeout: 8))
        pdfButton2.tap()
        let closeButton2 = app.buttons["attachments.viewer.closeButton"].firstMatch
        XCTAssertTrue(closeButton2.waitForExistence(timeout: 10))
        closeButton2.tap()

        let leaveButton = app.buttons["harness.attachments.leaveAttempt"].firstMatch
        XCTAssertTrue(leaveButton.waitForExistence(timeout: 8))
        leaveButton.tap()

        let logoutButton = app.buttons["harness.attachments.logout"].firstMatch
        XCTAssertTrue(logoutButton.waitForExistence(timeout: 6))
        logoutButton.tap()

        let loginIdentifier = app.textFields["login.identifierField"].firstMatch
        XCTAssertTrue(loginIdentifier.waitForExistence(timeout: 10), "Logout must transition cleanly to Login screen")

        app.terminate()

        XCTAssertEqual(
            AttachmentsSandboxInspector.attemptAssetTempFiles(under: containerPath),
            [],
            "Caches/AttemptAssets/ must be empty after logout"
        )
        XCTAssertEqual(
            AttachmentsSandboxInspector.filesMatchingMediaExtensionsOrBytes(
                under: containerPath,
                forbiddenPayloads: forbiddenPayloads
            ),
            [],
            "Zero PDF/audio/image asset files may remain in app sandbox after leave & logout (AC-21)"
        )
    }

    private func waitUntil(timeout: TimeInterval, predicate: () -> Bool) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if predicate() { return true }
            Thread.sleep(forTimeInterval: 0.2)
        }
        return predicate()
    }
}
