import Foundation
import XCTest

final class SmokeAgainstBackendUITests: XCTestCase {
    private let networkTimeout: TimeInterval = 20
    private let mailpitTimeout: TimeInterval = 30
    private let mailpitPollInterval: TimeInterval = 0.5

    private var apiBaseURL: String {
        let env = ProcessInfo.processInfo.environment
        if let value = env["THI360_SMOKE_API_URL"], !value.isEmpty {
            return value
        }
        if let value = env["TEST_RUNNER_THI360_SMOKE_API_URL"], !value.isEmpty {
            return value
        }
        return "http://127.0.0.1:8080"
    }

    private var mailpitBaseURL: String {
        let env = ProcessInfo.processInfo.environment
        if let value = env["THI360_SMOKE_MAILPIT_URL"], !value.isEmpty {
            return value
        }
        if let value = env["TEST_RUNNER_THI360_SMOKE_MAILPIT_URL"], !value.isEmpty {
            return value
        }
        return "http://127.0.0.1:8025"
    }

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testEndToEndStudentLifecycleAndAdminRoleRejection() throws {
        let suite = "vn.unicom.thi360.uitest.smoke.\(UUID().uuidString)"
        let keychainService = "vn.unicom.thi360.uitest.smoke.kc.\(UUID().uuidString)"
        let timestamp = Int(Date().timeIntervalSince1970)
        let fullName = "QA iOS Student"
        let username = "qaios\(timestamp)"
        let email = "qaios\(timestamp)@thi360.local"
        let password1 = "QaSmoke1!a"
        let password2 = "QaSmoke2@b"
        let password3 = "QaSmoke3#c"
        var seenMessageIDs = Set<String>()

        let app = XCUIApplication()
        app.launchArguments = [
            "-uitest-defaults-suite", suite,
            "-uitest-reset-preferences",
            "-uitest-keychain-service", keychainService,
            "-uitest-reset-session",
            "-uitest-api-base-url", apiBaseURL
        ]
        app.launch()

        let registerButton = app.buttons["login.registerButton"]
        XCTAssertTrue(registerButton.waitForExistence(timeout: networkTimeout))
        scrollUntilHittable(registerButton, in: app)
        registerButton.tap()

        let fullNameField = app.textFields["register.fullNameField"]
        XCTAssertTrue(fullNameField.waitForExistence(timeout: networkTimeout))
        fullNameField.tap()
        fullNameField.typeText(fullName)

        let usernameField = app.textFields["register.usernameField"]
        scrollUntilHittable(usernameField, in: app)
        usernameField.tap()
        usernameField.typeText(username)

        let emailField = app.textFields["register.emailField"]
        scrollUntilHittable(emailField, in: app)
        emailField.tap()
        emailField.typeText(email)

        // Toggle password visible before typing so iOS Simulator AutoFill overlay does not intercept .newPassword input.
        let registerPasswordToggle = app.buttons["register.passwordToggle"]
        scrollUntilHittable(registerPasswordToggle, in: app)
        registerPasswordToggle.tap()

        let registerPasswordField = app.textFields["register.passwordField"]
        XCTAssertTrue(registerPasswordField.waitForExistence(timeout: 5))
        scrollUntilHittable(registerPasswordField, in: app)
        registerPasswordField.tap()
        registerPasswordField.typeText(password1)

        dismissKeyboard(in: app, fallbackHeading: "Đăng ký")

        let termsCheckbox = app.buttons["register.termsCheckbox"]
        scrollUntilHittable(termsCheckbox, in: app)
        termsCheckbox.tap()

        let registerSubmit = app.buttons["register.submitButton"]
        scrollUntilHittable(registerSubmit, in: app)
        registerSubmit.tap()

        XCTAssertTrue(
            app.staticTexts["Xác minh email"].waitForExistence(timeout: networkTimeout),
            "Registration submit must transition to OTP verification screen"
        )

        let otpMessage = try awaitMailpitMessage(recipientEmail: email, excluding: seenMessageIDs) { detail in
            self.extractOtpCode(from: detail) != nil
        }
        seenMessageIDs.insert(otpMessage.id)
        let otpCode = try XCTUnwrap(
            extractOtpCode(from: otpMessage),
            "Failed to parse 6-digit OTP code from Mailpit message"
        )

        let otpField = app.textFields["otp.codeField"]
        XCTAssertTrue(otpField.waitForExistence(timeout: networkTimeout))
        otpField.tap()
        otpField.typeText(otpCode)

        dismissKeyboard(in: app, fallbackHeading: "Xác minh email")

        let otpSubmit = app.buttons["otp.submitButton"]
        scrollUntilHittable(otpSubmit, in: app)
        otpSubmit.tap()

        waitForAuthenticatedHome(in: app)

        openAccountTab(in: app)
        let profileLoaded = app.descendants(matching: .any).matching(identifier: "account.profile.loaded").firstMatch
        XCTAssertTrue(profileLoaded.waitForExistence(timeout: networkTimeout), "Account profile must load from backend")
        XCTAssertEqual(app.staticTexts["account.profile.fullName"].firstMatch.label, fullName)
        XCTAssertEqual(app.staticTexts["account.profile.username"].firstMatch.label, username)
        XCTAssertEqual(app.staticTexts["account.profile.email"].firstMatch.label, email)
        let emailStatus = app.descendants(matching: .any).matching(identifier: "account.profile.emailStatus").firstMatch
        XCTAssertTrue(emailStatus.waitForExistence(timeout: 5))
        XCTAssertTrue(
            emailStatus.label.contains("Email đã xác minh") || app.staticTexts["Email đã xác minh"].firstMatch.exists,
            "Verified email badge must be displayed after OTP registration"
        )

        let changePasswordButton = app.buttons["account.changePasswordButton"].firstMatch
        scrollUntilHittable(changePasswordButton, in: app)
        changePasswordButton.tap()

        let oldToggle = app.buttons["password.oldToggle"].firstMatch
        XCTAssertTrue(oldToggle.waitForExistence(timeout: networkTimeout))
        scrollUntilHittable(oldToggle, in: app)
        oldToggle.tap()

        let newToggle = app.buttons["password.newToggle"].firstMatch
        XCTAssertTrue(newToggle.waitForExistence(timeout: 5))
        scrollUntilHittable(newToggle, in: app)
        newToggle.tap()

        let confirmToggle = app.buttons["password.confirmToggle"].firstMatch
        XCTAssertTrue(confirmToggle.waitForExistence(timeout: 5))
        scrollUntilHittable(confirmToggle, in: app)
        confirmToggle.tap()

        let oldField = app.textFields["password.oldField"].firstMatch
        XCTAssertTrue(oldField.waitForExistence(timeout: 5))
        scrollUntilHittable(oldField, in: app)
        oldField.tap()
        oldField.typeText(password1)

        let newField = app.textFields["password.newField"].firstMatch
        scrollUntilHittable(newField, in: app)
        newField.tap()
        newField.typeText(password2)

        let confirmField = app.textFields["password.confirmField"].firstMatch
        scrollUntilHittable(confirmField, in: app)
        confirmField.tap()
        confirmField.typeText(password2)

        dismissKeyboard(in: app, fallbackHeading: "Đổi mật khẩu")

        let passwordSubmit = app.buttons["password.submitButton"].firstMatch
        scrollUntilHittable(passwordSubmit, in: app)
        passwordSubmit.tap()

        let changeBanner = app.descendants(matching: .any).matching(identifier: "password.bannerMessage").firstMatch
        XCTAssertTrue(changeBanner.waitForExistence(timeout: networkTimeout), "Voluntary password change must show success banner")
        XCTAssertTrue(
            changeBanner.label.contains("Đã đổi mật khẩu thành công") || app.staticTexts["Đã đổi mật khẩu thành công."].firstMatch.exists,
            "Expected voluntary change password success message"
        )

        let closePasswordButton = app.buttons["password.closeButton"].firstMatch
        if closePasswordButton.waitForExistence(timeout: 3) {
            closePasswordButton.tap()
        }
        XCTAssertTrue(
            app.descendants(matching: .any).matching(identifier: "shell.account.view").firstMatch.waitForExistence(timeout: networkTimeout),
            "Session must remain authenticated on Account screen after voluntary password change"
        )

        logoutFromAccount(in: app)

        login(identifier: username, password: password2, in: app)
        waitForAuthenticatedHome(in: app)
        openAccountTab(in: app)
        logoutFromAccount(in: app)

        let forgotButton = app.buttons["login.forgotPasswordButton"].firstMatch
        XCTAssertTrue(forgotButton.waitForExistence(timeout: networkTimeout))
        scrollUntilHittable(forgotButton, in: app)
        forgotButton.tap()

        let forgotEmailField = app.textFields["forgot.emailField"].firstMatch
        XCTAssertTrue(forgotEmailField.waitForExistence(timeout: networkTimeout))
        forgotEmailField.tap()
        forgotEmailField.typeText(email)

        dismissKeyboard(in: app, fallbackHeading: "Quên mật khẩu")

        let forgotSubmit = app.buttons["forgot.submitButton"].firstMatch
        scrollUntilHittable(forgotSubmit, in: app)
        forgotSubmit.tap()

        XCTAssertTrue(
            app.staticTexts["forgot.sentHeading"].firstMatch.waitForExistence(timeout: networkTimeout),
            "Forgot password must show confirmation screen"
        )

        let resetMessage = try awaitMailpitMessage(recipientEmail: email, excluding: seenMessageIDs) { detail in
            self.extractResetToken(from: detail) != nil
        }
        seenMessageIDs.insert(resetMessage.id)
        let resetToken = try XCTUnwrap(
            extractResetToken(from: resetMessage),
            "Failed to parse password reset token from Mailpit message"
        )

        var resetComponents = URLComponents()
        resetComponents.scheme = "https"
        resetComponents.host = "localhost"
        resetComponents.path = "/reset-password"
        resetComponents.queryItems = [URLQueryItem(name: "token", value: resetToken)]
        let resetLinkURL = try XCTUnwrap(resetComponents.string)

        app.terminate()

        let resetApp = XCUIApplication()
        resetApp.launchArguments = [
            "-uitest-defaults-suite", suite,
            "-uitest-keychain-service", keychainService,
            "-uitest-api-base-url", apiBaseURL,
            "-resetLinkURL", resetLinkURL
        ]
        resetApp.launch()

        let resetScreen = resetApp.descendants(matching: .any).matching(identifier: "password.reset.screen").firstMatch
        XCTAssertTrue(
            resetScreen.waitForExistence(timeout: networkTimeout)
                || resetApp.staticTexts["Đặt mật khẩu mới"].firstMatch.waitForExistence(timeout: 5),
            "Launching with -resetLinkURL must open reset password screen"
        )

        let resetNewToggle = resetApp.buttons["password.newToggle"].firstMatch
        XCTAssertTrue(resetNewToggle.waitForExistence(timeout: networkTimeout))
        scrollUntilHittable(resetNewToggle, in: resetApp)
        resetNewToggle.tap()

        let resetConfirmToggle = resetApp.buttons["password.confirmToggle"].firstMatch
        XCTAssertTrue(resetConfirmToggle.waitForExistence(timeout: 5))
        scrollUntilHittable(resetConfirmToggle, in: resetApp)
        resetConfirmToggle.tap()

        let resetNewField = resetApp.textFields["password.newField"].firstMatch
        XCTAssertTrue(resetNewField.waitForExistence(timeout: 5))
        scrollUntilHittable(resetNewField, in: resetApp)
        resetNewField.tap()
        resetNewField.typeText(password3)

        let resetConfirmField = resetApp.textFields["password.confirmField"].firstMatch
        XCTAssertTrue(resetConfirmField.waitForExistence(timeout: 5))
        scrollUntilHittable(resetConfirmField, in: resetApp)
        resetConfirmField.tap()
        resetConfirmField.typeText(password3)

        dismissKeyboard(in: resetApp, fallbackHeading: "Đặt mật khẩu mới")

        let resetSubmit = resetApp.buttons["password.submitButton"].firstMatch
        scrollUntilHittable(resetSubmit, in: resetApp)
        resetSubmit.tap()

        XCTAssertTrue(
            resetApp.textFields["login.identifierField"].firstMatch.waitForExistence(timeout: networkTimeout),
            "Completing password reset must return to Login screen"
        )
        let loginBanner = resetApp.descendants(matching: .any).matching(identifier: "login.bannerMessage").firstMatch
        XCTAssertTrue(
            loginBanner.waitForExistence(timeout: 8)
                || resetApp.staticTexts["Đã đặt lại mật khẩu. Hãy đăng nhập bằng mật khẩu mới."].firstMatch.waitForExistence(timeout: 5),
            "Login screen must display post-reset confirmation notice"
        )

        login(identifier: username, password: password3, in: resetApp)
        waitForAuthenticatedHome(in: resetApp)
        openAccountTab(in: resetApp)
        logoutFromAccount(in: resetApp)

        login(identifier: "ops.admin", password: "Thi360@dev", in: resetApp)
        let roleNoticeScreen = resetApp.descendants(matching: .any).matching(identifier: "roleNotice.screen").firstMatch
        XCTAssertTrue(
            roleNoticeScreen.waitForExistence(timeout: networkTimeout),
            "MANAGE account must be blocked with APP_STUDENT_ONLY role notice screen"
        )
        let roleDescription = resetApp.staticTexts["roleNotice.description"].firstMatch
        XCTAssertTrue(roleDescription.waitForExistence(timeout: 5))
        XCTAssertTrue(
            roleDescription.label.contains("ops.admin"),
            "Role notice screen must mention blocked account username"
        )

        let switchAccountButton = resetApp.buttons["roleNotice.switchAccountButton"].firstMatch
        XCTAssertTrue(switchAccountButton.waitForExistence(timeout: 5))
        scrollUntilHittable(switchAccountButton, in: resetApp)
        switchAccountButton.tap()
        XCTAssertTrue(
            resetApp.textFields["login.identifierField"].firstMatch.waitForExistence(timeout: networkTimeout),
            "Switching account from role notice must return to Login screen"
        )
    }

    private func login(identifier: String, password: String, in app: XCUIApplication) {
        let identifierField = app.textFields["login.identifierField"].firstMatch
        XCTAssertTrue(identifierField.waitForExistence(timeout: networkTimeout))
        scrollUntilHittable(identifierField, in: app)
        identifierField.tap()
        identifierField.typeText(identifier)

        let passwordField = app.secureTextFields["login.passwordField"].firstMatch
        XCTAssertTrue(passwordField.waitForExistence(timeout: 5))
        scrollUntilHittable(passwordField, in: app)
        passwordField.tap()
        passwordField.typeText(password)

        let submitButton = app.buttons["login.submitButton"].firstMatch
        scrollUntilHittable(submitButton, in: app)
        submitButton.tap()
    }

    private func waitForAuthenticatedHome(in app: XCUIApplication) {
        let homeHeading = app.staticTexts["catalog.heading"].firstMatch
        let homeTitle = app.staticTexts["Chọn bài thi"].firstMatch
        let bioLater = app.buttons["biometric.enable.laterButton"].firstMatch
        let deadline = Date().addingTimeInterval(networkTimeout)

        while Date() < deadline {
            if homeHeading.exists || homeTitle.exists {
                return
            }
            if bioLater.exists && bioLater.isHittable {
                bioLater.tap()
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.25))
        }
        XCTAssertTrue(homeHeading.exists || homeTitle.exists, "Expected authenticated Home screen")
    }

    private func openAccountTab(in app: XCUIApplication) {
        let tabButton = app.tabBars.buttons["Tài khoản"].firstMatch
        if tabButton.waitForExistence(timeout: 3) {
            tabButton.tap()
            return
        }
        let destButton = app.buttons["shell.dest.account"].firstMatch
        XCTAssertTrue(destButton.waitForExistence(timeout: 5))
        destButton.tap()
    }

    private func logoutFromAccount(in app: XCUIApplication) {
        let logoutButton = app.buttons["account.logoutButton"].firstMatch
        XCTAssertTrue(logoutButton.waitForExistence(timeout: networkTimeout))
        scrollUntilHittable(logoutButton, in: app)
        logoutButton.tap()

        let confirmById = app.alerts.buttons["account.logout.confirmButton"].firstMatch
        if confirmById.waitForExistence(timeout: 5) {
            confirmById.tap()
        } else {
            let confirmByTitle = app.alerts.buttons["Đăng xuất"].firstMatch
            XCTAssertTrue(confirmByTitle.waitForExistence(timeout: 5), "Logout confirmation alert button must appear")
            confirmByTitle.tap()
        }

        XCTAssertTrue(
            app.textFields["login.identifierField"].firstMatch.waitForExistence(timeout: networkTimeout),
            "Logout must return to Login screen"
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

    private func dismissKeyboard(in app: XCUIApplication, fallbackHeading: String) {
        let heading = app.staticTexts[fallbackHeading].firstMatch
        if heading.exists && heading.isHittable {
            heading.tap()
        }
    }

    private struct MailpitMessageDetail {
        let id: String
        let text: String
        let html: String
    }

    private func awaitMailpitMessage(
        recipientEmail: String,
        excluding seenIDs: Set<String>,
        matcher: (MailpitMessageDetail) -> Bool
    ) throws -> MailpitMessageDetail {
        let deadline = Date().addingTimeInterval(mailpitTimeout)
        let base = mailpitBaseURL.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        var lastError = "none"

        while Date() < deadline {
            do {
                guard let listURL = URL(string: "\(base)/api/v1/messages?limit=50") else {
                    throw NSError(domain: "SmokeMailpit", code: -1)
                }
                let (data, response) = try performSyncRequest(URLRequest(url: listURL))
                if let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode),
                   let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                   let messages = json["messages"] as? [[String: Any]]
                {
                    for entry in messages {
                        guard let id = entry["ID"] as? String, !seenIDs.contains(id) else { continue }
                        guard message(entry, hasRecipient: recipientEmail) else { continue }
                        if let detail = try fetchMailpitMessage(base: base, id: id), matcher(detail) {
                            return detail
                        }
                    }
                }
            } catch {
                lastError = "\(error)"
            }
            Thread.sleep(forTimeInterval: mailpitPollInterval)
        }
        XCTFail("Timed out waiting for Mailpit email to \(recipientEmail) (lastError=\(lastError))")
        throw NSError(domain: "SmokeMailpit", code: -2)
    }

    private func message(_ entry: [String: Any], hasRecipient email: String) -> Bool {
        guard let recipients = entry["To"] as? [[String: Any]] else { return false }
        return recipients.contains { recipient in
            (recipient["Address"] as? String)?.caseInsensitiveCompare(email) == .orderedSame
        }
    }

    private func fetchMailpitMessage(base: String, id: String) throws -> MailpitMessageDetail? {
        guard let url = URL(string: "\(base)/api/v1/message/\(id)") else { return nil }
        let (data, response) = try performSyncRequest(URLRequest(url: url))
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode),
              let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        else {
            return nil
        }
        return MailpitMessageDetail(
            id: id,
            text: (json["Text"] as? String) ?? "",
            html: (json["HTML"] as? String) ?? ""
        )
    }

    private func extractOtpCode(from message: MailpitMessageDetail) -> String? {
        if let code = firstRegexCapture(#"(?<![#\d])(\d{6})(?!\d)"#, in: message.text) {
            return code
        }
        // Strip HTML tags before scanning fallback body so CSS hex colors like #047857 are never misread as OTP digits.
        let stripped = message.html.replacingOccurrences(of: #"<[^>]+>"#, with: " ", options: .regularExpression)
        return firstRegexCapture(#"(?<![#\d])(\d{6})(?!\d)"#, in: stripped)
    }

    private func extractResetToken(from message: MailpitMessageDetail) -> String? {
        let pattern = #"/reset-password\?[^\s"'<>]*\btoken=([A-Za-z0-9._~%-]+)"#
        let raw = firstRegexCapture(pattern, in: message.text)
            ?? firstRegexCapture(pattern, in: message.html.replacingOccurrences(of: "&amp;", with: "&"))
        guard let raw else { return nil }
        let decoded = raw.removingPercentEncoding?.trimmingCharacters(in: .whitespacesAndNewlines)
        return (decoded?.isEmpty == false) ? decoded : nil
    }

    private func firstRegexCapture(_ pattern: String, in source: String) -> String? {
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return nil }
        let range = NSRange(source.startIndex..., in: source)
        guard let match = regex.firstMatch(in: source, range: range),
              match.numberOfRanges > 1,
              let captureRange = Range(match.range(at: 1), in: source)
        else {
            return nil
        }
        return String(source[captureRange])
    }

    private func performSyncRequest(_ request: URLRequest) throws -> (Data, URLResponse) {
        let semaphore = DispatchSemaphore(value: 0)
        var capturedData: Data?
        var capturedResponse: URLResponse?
        var capturedError: Error?

        let task = URLSession.shared.dataTask(with: request) { data, response, error in
            capturedData = data
            capturedResponse = response
            capturedError = error
            semaphore.signal()
        }
        task.resume()

        if semaphore.wait(timeout: .now() + 15) == .timedOut {
            task.cancel()
            throw URLError(.timedOut)
        }
        if let capturedError {
            throw capturedError
        }
        guard let capturedData, let capturedResponse else {
            throw URLError(.badServerResponse)
        }
        return (capturedData, capturedResponse)
    }
}
