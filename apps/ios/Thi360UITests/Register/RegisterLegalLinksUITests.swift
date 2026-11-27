import XCTest

final class RegisterLegalLinksUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testRegisterOpensTermsAndPrivacyRoutesAndPreservesFormDataOnBack() {
        let suite = "vn.unicom.thi360.uitest.reglegal.\(UUID().uuidString)"
        let keychainService = "vn.unicom.thi360.uitest.reglegal.kc.\(UUID().uuidString)"
        let app = XCUIApplication()
        app.launchArguments = [
            "-uitest-defaults-suite", suite,
            "-uitest-reset-preferences",
            "-uitest-keychain-service", keychainService,
            "-uitest-reset-session"
        ]
        app.launch()

        let registerButton = app.buttons["login.registerButton"].firstMatch
        XCTAssertTrue(registerButton.waitForExistence(timeout: 8))
        registerButton.tap()

        let fullNameField = app.textFields["register.fullNameField"].firstMatch
        XCTAssertTrue(fullNameField.waitForExistence(timeout: 5))
        fullNameField.tap()
        fullNameField.typeText("Nguyen Van An")

        let usernameField = app.textFields["register.usernameField"].firstMatch
        usernameField.tap()
        usernameField.typeText("student.legal")

        let emailField = app.textFields["register.emailField"].firstMatch
        emailField.tap()
        emailField.typeText("legal@thi360.vn")

        let heading = app.staticTexts["register.heading"].firstMatch
        if heading.isHittable {
            heading.tap()
        }

        let termsCheckbox = app.buttons["register.termsCheckbox"].firstMatch
        scrollUntilHittable(termsCheckbox, in: app)
        XCTAssertTrue(termsCheckbox.waitForExistence(timeout: 5))
        termsCheckbox.tap()

        XCTAssertFalse(app.buttons["register.viewTermsButton"].exists, "Legacy viewTermsButton must be removed")

        let termsLink = app.buttons["register.termsLinkTerms"].firstMatch
        scrollUntilHittable(termsLink, in: app)
        XCTAssertTrue(termsLink.waitForExistence(timeout: 5))
        termsLink.tap()

        let termsScreen = app.descendants(matching: .any).matching(identifier: "legal.screen.terms").firstMatch
        XCTAssertTrue(termsScreen.waitForExistence(timeout: 5), "Tapping terms link must push legal/terms")
        XCTAssertTrue(app.buttons["legal.openOnWebButton"].firstMatch.exists)

        app.navigationBars.buttons.firstMatch.tap()

        XCTAssertTrue(app.staticTexts["register.heading"].firstMatch.waitForExistence(timeout: 5))
        XCTAssertEqual(app.textFields["register.fullNameField"].firstMatch.value as? String, "Nguyen Van An")
        XCTAssertEqual(app.textFields["register.usernameField"].firstMatch.value as? String, "student.legal")
        XCTAssertEqual(app.textFields["register.emailField"].firstMatch.value as? String, "legal@thi360.vn")

        let privacyLink = app.buttons["register.termsLinkPrivacy"].firstMatch
        XCTAssertTrue(privacyLink.waitForExistence(timeout: 5))
        app.swipeUp()
        scrollUntilHittable(privacyLink, in: app)
        privacyLink.tap()

        let privacyScreen = app.descendants(matching: .any).matching(identifier: "legal.screen.privacy").firstMatch
        XCTAssertTrue(privacyScreen.waitForExistence(timeout: 5), "Tapping privacy link must push legal/privacy")

        app.navigationBars.buttons.firstMatch.tap()
        XCTAssertEqual(app.textFields["register.fullNameField"].firstMatch.value as? String, "Nguyen Van An")
        XCTAssertEqual(app.textFields["register.emailField"].firstMatch.value as? String, "legal@thi360.vn")
    }

    private func scrollUntilHittable(_ element: XCUIElement, in app: XCUIApplication, maxSwipes: Int = 5) {
        let maxSafeY = app.windows.firstMatch.frame.height * 0.78
        for _ in 0..<maxSwipes {
            if element.exists && element.isHittable && element.frame.maxY < maxSafeY {
                return
            }
            app.swipeUp()
        }
    }
}
