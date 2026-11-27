import Foundation
import XCTest

final class AccessibilityAuditUITests: XCTestCase {
    private let minTouchTargetPt: CGFloat = 44
    private let touchTargetTolerancePt: CGFloat = 0.5
    private let uiTimeout: TimeInterval = 10

    private var server: RecoveryFixtureHTTPServer?

    override func setUpWithError() throws {
        continueAfterFailure = false
        let repoRoot = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
        let authDir = repoRoot.appendingPathComponent("contracts/mobile/auth", isDirectory: true)
        let apiDir = repoRoot.appendingPathComponent("contracts/mobile/api", isDirectory: true)
        let loginBody = try Data(contentsOf: authDir.appendingPathComponent("login-200.json"))
        let refreshBody = try Data(contentsOf: authDir.appendingPathComponent("refresh-200.json"))
        let profileBody = try Data(contentsOf: apiDir.appendingPathComponent("me-profile-200.json"))
        let filtersBody = try Data(contentsOf: apiDir.appendingPathComponent("storefront-filters-200.json"))
        let productsBody = try Data(contentsOf: apiDir.appendingPathComponent("storefront-products-list-200.json"))
        let accessBody = try Data(contentsOf: apiDir.appendingPathComponent("exam-access-query-200.json"))

        let mockServer = try RecoveryFixtureHTTPServer { method, path, _ in
            if method == "POST", path == "/api/v1/auth/mobile/login" {
                return (200, loginBody)
            }
            if method == "POST", path == "/api/v1/auth/mobile/refresh" {
                return (200, refreshBody)
            }
            if method == "GET", path == "/api/v1/me/profile" {
                return (200, profileBody)
            }
            if method == "GET", path == "/api/v1/public/storefront/filters" {
                return (200, filtersBody)
            }
            if method == "GET", path == "/api/v1/public/storefront/products" {
                return (200, productsBody)
            }
            if method == "POST", path == "/api/v1/me/exam-access/query" {
                return (200, accessBody)
            }
            if method == "POST", path == "/api/v1/auth/mobile/logout" {
                return (204, Data())
            }
            return (404, Data(#"{"code":"NOT_FOUND","message":"Not found","fieldErrors":[],"traceId":"0"}"#.utf8))
        }
        try mockServer.start()
        self.server = mockServer
    }

    override func tearDownWithError() throws {
        server?.stop()
        server = nil
    }

    func testLoginAndRegisterVoiceOverAccessibility() throws {
        let app = try launchCleanApp()

        let loginHeading = app.staticTexts["login.heading"].firstMatch
        XCTAssertTrue(loginHeading.waitForExistence(timeout: uiTimeout), "Login heading must exist")
        assertNonEmptyLabel(loginHeading, context: "Login heading")
        let loginHeadingMinY = loginHeading.frame.minY

        let languageButton = existingElement(identifier: "login.languageButton", in: app)
        assertInteractiveTarget(languageButton, context: "Login languageButton")

        let passwordToggle = app.buttons["login.passwordToggle"].firstMatch
        assertInteractiveTarget(passwordToggle, context: "Login passwordToggle")

        let forgotButton = app.buttons["login.forgotPasswordButton"].firstMatch
        assertInteractiveTarget(forgotButton, context: "Login forgotPasswordButton")

        let loginSubmit = app.buttons["login.submitButton"].firstMatch
        scrollUntilHittable(loginSubmit, in: app)
        assertInteractiveTarget(loginSubmit, context: "Login submitButton")
        XCTAssertLessThan(
            loginHeadingMinY,
            loginSubmit.frame.minY,
            "Login heading must precede primary submit button in top-to-bottom VoiceOver order"
        )

        let registerButton = app.buttons["login.registerButton"].firstMatch
        scrollUntilHittable(registerButton, in: app)
        assertInteractiveTarget(registerButton, context: "Login registerButton")
        registerButton.tap()

        let registerHeading = app.staticTexts["register.heading"].firstMatch
        XCTAssertTrue(registerHeading.waitForExistence(timeout: uiTimeout), "Register heading must exist")
        assertNonEmptyLabel(registerHeading, context: "Register heading")
        let registerHeadingMinY = registerHeading.frame.minY

        let registerBack = app.buttons["register.backButton"].firstMatch
        assertInteractiveTarget(registerBack, context: "Register backButton")

        let registerPasswordToggle = app.buttons["register.passwordToggle"].firstMatch
        scrollUntilHittable(registerPasswordToggle, in: app)
        assertInteractiveTarget(registerPasswordToggle, context: "Register passwordToggle")

        let termsCheckbox = app.buttons["register.termsCheckbox"].firstMatch
        scrollUntilHittable(termsCheckbox, in: app)
        assertInteractiveTarget(termsCheckbox, context: "Register termsCheckbox")

        let termsLink = app.buttons["register.termsLinkTerms"].firstMatch
        scrollUntilHittable(termsLink, in: app)
        assertInteractiveTarget(termsLink, context: "Register termsLinkTerms")

        let privacyLink = app.buttons["register.termsLinkPrivacy"].firstMatch
        scrollUntilHittable(privacyLink, in: app)
        assertInteractiveTarget(privacyLink, context: "Register termsLinkPrivacy")

        let registerSubmit = app.buttons["register.submitButton"].firstMatch
        scrollUntilHittable(registerSubmit, in: app)
        assertInteractiveTarget(registerSubmit, context: "Register submitButton")
        XCTAssertLessThan(
            registerHeadingMinY,
            registerSubmit.frame.minY,
            "Register heading must precede primary submit button in top-to-bottom VoiceOver order"
        )

        let registerLoginButton = app.buttons["register.loginButton"].firstMatch
        scrollUntilHittable(registerLoginButton, in: app)
        assertInteractiveTarget(registerLoginButton, context: "Register loginButton")
    }

    func testHomeAndAccountVoiceOverAccessibility() throws {
        let app = try launchCleanApp()

        let identifierField = app.textFields["login.identifierField"].firstMatch
        XCTAssertTrue(identifierField.waitForExistence(timeout: uiTimeout))
        identifierField.tap()
        identifierField.typeText("student.demo")

        let passwordField = app.secureTextFields["login.passwordField"].firstMatch
        XCTAssertTrue(passwordField.waitForExistence(timeout: 5))
        passwordField.tap()
        passwordField.typeText("Password123!")

        let submitButton = app.buttons["login.submitButton"].firstMatch
        scrollUntilHittable(submitButton, in: app)
        submitButton.tap()

        waitForHome(in: app)

        let homeHeading = app.staticTexts["catalog.heading"].firstMatch
        XCTAssertTrue(homeHeading.waitForExistence(timeout: uiTimeout), "Home heading must exist")
        assertNonEmptyLabel(homeHeading, context: "Home heading")

        let tabTitles = ["Trang chủ", "Bài thi của tôi", "Lịch sử", "Tài khoản"]
        for title in tabTitles {
            let tabButton = app.tabBars.buttons[title].firstMatch
            assertInteractiveTarget(tabButton, context: "Home tab '\(title)'")
        }

        let accountTab = app.tabBars.buttons["Tài khoản"].firstMatch
        XCTAssertLessThan(
            homeHeading.frame.minY,
            accountTab.frame.minY,
            "Home heading must precede bottom navigation actions in top-to-bottom VoiceOver order"
        )
        accountTab.tap()

        let accountHeading = app.staticTexts["shell.account.heading"].firstMatch
        XCTAssertTrue(accountHeading.waitForExistence(timeout: uiTimeout), "Account heading must exist")
        assertNonEmptyLabel(accountHeading, context: "Account heading")
        let accountHeadingMinY = accountHeading.frame.minY

        let profileLoaded = app.descendants(matching: .any).matching(identifier: "account.profile.loaded").firstMatch
        XCTAssertTrue(profileLoaded.waitForExistence(timeout: uiTimeout), "Account profile must load from fixture server")

        let changePasswordButton = app.buttons["account.changePasswordButton"].firstMatch
        scrollUntilHittable(changePasswordButton, in: app)
        assertInteractiveTarget(changePasswordButton, context: "Account changePasswordButton")
        XCTAssertLessThan(
            accountHeadingMinY,
            changePasswordButton.frame.minY,
            "Account heading must precede primary actions in top-to-bottom VoiceOver order"
        )

        let biometricSwitch = resolveBiometricToggle(in: app)
        let switchLabel = biometricSwitch.label.trimmingCharacters(in: .whitespacesAndNewlines)
        XCTAssertFalse(
            switchLabel.isEmpty,
            "Account biometric switch must expose a non-empty VoiceOver accessibilityLabel"
        )
        let lowerSwitchLabel = switchLabel.lowercased()
        XCTAssertTrue(
            lowerSwitchLabel.contains("mở khóa")
                || lowerSwitchLabel.contains("face id")
                || lowerSwitchLabel.contains("touch id")
                || lowerSwitchLabel.contains("sinh trắc")
                || lowerSwitchLabel.contains("unlock")
                || lowerSwitchLabel.contains("biometric"),
            "Account biometric switch label '\(switchLabel)' must describe biometric unlock"
        )

        for identifier in [
            "shell.account.locale.vi",
            "shell.account.locale.en",
            "shell.account.theme.light",
            "shell.account.theme.dark",
            "account.logoutButton"
        ] {
            let button = app.buttons[identifier].firstMatch
            scrollUntilHittable(button, in: app)
            assertInteractiveTarget(button, context: "Account \(identifier)")
        }
    }

    func testLockScreenVoiceOverAccessibility() throws {
        let app = try launchCleanApp(extraArguments: ["-uitest-locked"])

        let lockHeading = app.staticTexts["lock.displayName"].firstMatch
        XCTAssertTrue(lockHeading.waitForExistence(timeout: uiTimeout), "Lock screen display name heading must exist")
        assertNonEmptyLabel(lockHeading, context: "Lock displayName heading")
        let lockHeadingMinY = lockHeading.frame.minY

        let biometricButton = app.buttons["lock.unlockBiometricButton"].firstMatch
        if biometricButton.exists {
            assertInteractiveTarget(biometricButton, context: "Lock unlockBiometricButton")
            XCTAssertLessThan(
                lockHeadingMinY,
                biometricButton.frame.minY,
                "Lock heading must precede biometric unlock button"
            )
        }

        let enterPasswordButton = app.buttons["lock.enterPasswordButton"].firstMatch
        scrollUntilHittable(enterPasswordButton, in: app)
        assertInteractiveTarget(enterPasswordButton, context: "Lock enterPasswordButton")
        XCTAssertLessThan(
            lockHeadingMinY,
            enterPasswordButton.frame.minY,
            "Lock heading must precede password fallback button in top-to-bottom VoiceOver order"
        )

        let otherAccountButton = app.buttons["lock.otherAccountButton"].firstMatch
        scrollUntilHittable(otherAccountButton, in: app)
        assertInteractiveTarget(otherAccountButton, context: "Lock otherAccountButton")
        XCTAssertLessThan(
            enterPasswordButton.frame.minY,
            otherAccountButton.frame.minY,
            "Lock primary password action must precede secondary switch-account button"
        )
    }

    private func launchCleanApp(extraArguments: [String] = []) throws -> XCUIApplication {
        let server = try XCTUnwrap(server)
        let suite = "vn.unicom.thi360.uitest.a11y.\(UUID().uuidString)"
        let keychainService = "vn.unicom.thi360.uitest.a11y.kc.\(UUID().uuidString)"
        let baseURL = "http://127.0.0.1:\(server.port)"

        let app = XCUIApplication()
        app.launchArguments = [
            "-uitest-defaults-suite", suite,
            "-uitest-reset-preferences",
            "-uitest-keychain-service", keychainService,
            "-uitest-reset-session",
            "-uitest-api-base-url", baseURL
        ] + extraArguments
        app.launch()
        return app
    }

    private func waitForHome(in app: XCUIApplication) {
        let homeHeading = app.staticTexts["catalog.heading"].firstMatch
        let bioLater = app.buttons["biometric.enable.laterButton"].firstMatch
        let deadline = Date().addingTimeInterval(uiTimeout)

        while Date() < deadline {
            if homeHeading.exists {
                return
            }
            if bioLater.exists && bioLater.isHittable {
                bioLater.tap()
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        }
        XCTAssertTrue(homeHeading.exists, "Expected authenticated Home screen")
    }

    private func existingElement(identifier: String, in app: XCUIApplication) -> XCUIElement {
        let button = app.buttons[identifier].firstMatch
        if button.waitForExistence(timeout: 3) {
            return button
        }
        let fallback = app.descendants(matching: .any).matching(identifier: identifier).firstMatch
        XCTAssertTrue(fallback.waitForExistence(timeout: 5), "Expected accessibility element '\(identifier)'")
        return fallback
    }

    private func resolveBiometricToggle(in app: XCUIApplication) -> XCUIElement {
        let toggle = app.switches["account.biometricToggle"].firstMatch
        if toggle.waitForExistence(timeout: 5) {
            return toggle
        }
        let fallback = app.descendants(matching: .any).matching(identifier: "account.biometricToggle").firstMatch
        XCTAssertTrue(fallback.waitForExistence(timeout: 5), "Account biometric toggle must exist in accessibility tree")
        return fallback
    }

    private func assertNonEmptyLabel(_ element: XCUIElement, context: String) {
        let trimmed = element.label.trimmingCharacters(in: .whitespacesAndNewlines)
        XCTAssertFalse(trimmed.isEmpty, "\(context) must expose a non-empty VoiceOver accessibilityLabel")
    }

    private func assertInteractiveTarget(_ element: XCUIElement, context: String) {
        XCTAssertTrue(element.waitForExistence(timeout: 5), "\(context) must exist")
        assertNonEmptyLabel(element, context: context)
        let frame = element.frame
        XCTAssertGreaterThanOrEqual(
            frame.width,
            minTouchTargetPt - touchTargetTolerancePt,
            "\(context) width \(frame.width)pt < \(minTouchTargetPt)pt"
        )
        XCTAssertGreaterThanOrEqual(
            frame.height,
            minTouchTargetPt - touchTargetTolerancePt,
            "\(context) height \(frame.height)pt < \(minTouchTargetPt)pt"
        )
    }

    private func scrollUntilHittable(_ element: XCUIElement, in app: XCUIApplication, maxSwipes: Int = 5) {
        for _ in 0..<maxSwipes {
            if element.exists && element.isHittable {
                return
            }
            app.swipeUp()
        }
    }
}
