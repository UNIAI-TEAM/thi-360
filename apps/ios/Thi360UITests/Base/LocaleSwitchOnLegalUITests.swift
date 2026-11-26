import XCTest

final class LocaleSwitchOnLegalUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testSwitchingLocaleWhileLegalScreenIsOpenUpdatesDocumentInPlace() {
        let suite = "vn.unicom.thi360.uitest.legallocale.\(UUID().uuidString)"
        let app = XCUIApplication()
        app.launchArguments = [
            "-uitest-defaults-suite", suite,
            "-uitest-reset-preferences",
            "-uitest-state-probe",
            "-uitest-authenticated",
            "-uitest-destination", "account",
            "-uitest-route", "legal/terms"
        ]
        app.launch()

        let legalScreen = app.descendants(matching: .any).matching(identifier: "legal.screen.terms").firstMatch
        XCTAssertTrue(legalScreen.waitForExistence(timeout: 8))

        let openOnWebVi = app.buttons["legal.openOnWebButton"].firstMatch
        XCTAssertTrue(openOnWebVi.waitForExistence(timeout: 5))
        XCTAssertTrue(openOnWebVi.label.contains("Mở trên web"), "Expected Vietnamese label, got '\(openOnWebVi.label)'")

        let toggleLocale = app.buttons["legal.probe.toggleLocale"].firstMatch
        XCTAssertTrue(toggleLocale.waitForExistence(timeout: 5))
        toggleLocale.tap()

        XCTAssertTrue(legalScreen.exists, "Legal screen must stay open when locale switches")
        let openOnWebEn = app.buttons["legal.openOnWebButton"].firstMatch
        XCTAssertTrue(
            openOnWebEn.label.contains("Open on web"),
            "Expected English label after in-place locale switch, got '\(openOnWebEn.label)'"
        )
        if app.staticTexts["legal.title"].firstMatch.exists {
            XCTAssertTrue(
                app.descendants(matching: .any).matching(identifier: "legal.note").firstMatch.waitForExistence(timeout: 3),
                "English legal document must display Vietnamese-precedence note"
            )
        }
    }
}
