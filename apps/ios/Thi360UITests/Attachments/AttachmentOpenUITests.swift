import Foundation
import XCTest

final class AttachmentOpenUITests: XCTestCase {
    private var server: AttachmentsUITestServer?

    override func setUpWithError() throws {
        continueAfterFailure = false
        let mock = try AttachmentsUITestServer()
        try mock.start()
        self.server = mock
    }

    override func tearDownWithError() throws {
        server?.stop()
        server = nil
    }

    func testOpenPdfInQuickLookAndCloseDeletesTempFileAndReturnsToQuestion() throws {
        let server = try XCTUnwrap(server)
        let pdfBytes = try AttachmentsUITestServer.loadAttachmentResource("bang-so-lieu.pdf")

        let app = XCUIApplication()
        app.launchArguments = [
            "-uitest-attachments-harness", "attachmentOpen",
            "-uitest-authenticated",
            "-uitest-api-base-url", "http://127.0.0.1:\(server.port)"
        ]
        app.launch()

        let containerPathLabel = app.staticTexts["harness.attachments.containerPath"].firstMatch
        XCTAssertTrue(containerPathLabel.waitForExistence(timeout: 10))
        let containerPath = containerPathLabel.label
        XCTAssertFalse(containerPath.isEmpty)

        let fileButton = app.buttons["attachments.file.\(AttachmentsUITestServer.pdfAssetId)"].firstMatch
        XCTAssertTrue(fileButton.waitForExistence(timeout: 10), "PDF attachment row must appear in question header")
        fileButton.tap()

        let closeButton = app.buttons["attachments.viewer.closeButton"].firstMatch
        XCTAssertTrue(closeButton.waitForExistence(timeout: 12), "QLPreviewController viewer with Close button must open")

        let pdfRequests = server.recordedRequests().filter {
            $0.method == "GET" && $0.path.hasSuffix("/assets/\(AttachmentsUITestServer.pdfAssetId)")
        }
        XCTAssertEqual(pdfRequests.count, 1)
        XCTAssertTrue(
            pdfRequests.first?.headerValue("Authorization")?.hasPrefix("Bearer ") == true,
            "Attachment download must include Bearer Authorization header (AC-2)"
        )

        closeButton.tap()
        XCTAssertTrue(
            fileButton.waitForExistence(timeout: 8),
            "Closing QuickLook viewer must return directly to the exact question"
        )

        // Wait briefly for sheet onDismiss cleanup to settle.
        let telemetry = app.staticTexts["harness.attachments.telemetry"].firstMatch
        let cleanedPredicate = NSPredicate(format: "label CONTAINS 'TEMP_NOW:0'")
        expectation(for: cleanedPredicate, evaluatedWith: telemetry)
        waitForExpectations(timeout: 6)

        XCTAssertEqual(
            AttachmentsSandboxInspector.attemptAssetTempFiles(under: containerPath),
            [],
            "Caches/AttemptAssets/ must contain 0 files after closing viewer"
        )
        XCTAssertEqual(
            AttachmentsSandboxInspector.filesMatchingMediaExtensionsOrBytes(
                under: containerPath,
                forbiddenPayloads: [pdfBytes]
            ),
            [],
            "Zero PDF copies may remain anywhere in app sandbox after closing viewer (AC-2)"
        )
    }
}
