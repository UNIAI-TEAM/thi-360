import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class VerifyEmailCodeTests: XCTestCase {
    func testCodeFieldKeyboardOneTimeCodeValidationErrorRetentionAnd204ReloadsProfile() async throws {
        let unverifiedData = try ProfileFixtureHelper.mutateJSON("me-profile-200.json") { dict in
            dict["emailVerified"] = false
        }
        let verifiedData = try ContractFixtureLoader.loadApiFixture("me-profile-200.json")
        let invalidData = try ContractFixtureLoader.loadApiFixture("me-email-verify-422-email-code-invalid.json")
        let expiredData = try ContractFixtureLoader.loadApiFixture("me-email-verify-422-email-code-expired.json")

        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        ) { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/profile", statusCode: 200, data: unverifiedData)
            transport.registerEmpty(method: .post, path: "/api/v1/me/email/verification", statusCode: 202)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let router = AppRouter(initialDestination: .account)
        let accountVM = router.accountViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager
        )
        let model = accountVM.verifyEmailModel()
        await model.loadAndAutoSendIfNeeded(locale: .vi)

        let view = NavigationStack {
            VerifyEmailView(model: model, preferences: stack.preferences, onBackToProfile: {})
        }
        .environment(\.brandPalette, .defaultLight)
        .environment(\.statusPalette, .light)
        let (window, controller) = RecoveryViewTestHelper.host(
            view,
            size: CGSize(width: 390, height: 900),
            sizeClass: .compact,
            themeMode: .light
        )
        let textFields = RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: controller.view)
        let otpField = try XCTUnwrap(textFields.first, "VerifyEmailView must render code UITextField")
        XCTAssertEqual(otpField.keyboardType, .numberPad)
        XCTAssertEqual(otpField.textContentType, .oneTimeCode)
        window.isHidden = true

        for badCode in ["", "12345", "1234567", "12345a"] {
            model.updateCode(badCode)
            await model.submitCode(onVerified: {})
            XCTAssertEqual(model.codeErrorKey, "account.verifyEmail.codeRequired")
            XCTAssertEqual(ProfileStrings.text("account.verifyEmail.codeRequired", locale: .vi), "Vui lòng nhập mã gồm 6 chữ số.")
            XCTAssertEqual(model.code, badCode, "Entered code must be preserved after validation error")
        }
        XCTAssertEqual(stack.transport.callCount(method: .post, path: "/api/v1/me/email/verify"), 0)

        stack.transport.registerJSON(method: .post, path: "/api/v1/me/email/verify", statusCode: 422, data: invalidData)
        model.updateCode("111111")
        await model.submitCode(onVerified: {})
        XCTAssertEqual(model.codeErrorKey, "account.verifyEmail.codeInvalid")
        XCTAssertEqual(model.remainingAttempts, 4)
        XCTAssertEqual(
            ProfileStrings.text("account.verifyEmail.codeInvalid", locale: .vi, replacements: ["count": "4"]),
            "Mã chưa đúng. Bạn còn 4 lần thử."
        )
        XCTAssertEqual(model.code, "111111", "Code field must preserve entered digits after invalid code error")

        stack.transport.registerJSON(method: .post, path: "/api/v1/me/email/verify", statusCode: 422, data: expiredData)
        await model.submitCode(onVerified: {})
        XCTAssertEqual(model.alertKey, "account.verifyEmail.alerts.codeExpired")
        XCTAssertEqual(model.code, "111111")

        stack.transport.registerEmpty(method: .post, path: "/api/v1/me/email/verify", statusCode: 500)
        await model.submitCode(onVerified: {})
        XCTAssertEqual(model.alertKey, "account.verifyEmail.alerts.generic")
        XCTAssertEqual(
            ProfileStrings.text("account.verifyEmail.alerts.generic", locale: .vi),
            "Không xác minh được email. Vui lòng thử lại."
        )
        XCTAssertEqual(model.code, "111111")

        stack.transport.registerNetworkError(method: .post, path: "/api/v1/me/email/verify", code: .notConnectedToInternet)
        await model.submitCode(onVerified: {})
        XCTAssertEqual(model.alertKey, "account.verifyEmail.alerts.generic")

        let verifyGate = TestAsyncGate()
        stack.transport.resetRecordedRequests()
        stack.transport.registerJSON(method: .get, path: "/api/v1/me/profile", statusCode: 200, data: verifiedData)
        stack.transport.register(method: .post, path: "/api/v1/me/email/verify") { _ in
            await verifyGate.enterAndWait()
            return ContractFakeTransport.emptyResponse(statusCode: 204)
        }

        var navigatedBackCount = 0
        model.updateCode("654321")
        let firstSubmit = Task { @MainActor in
            await model.submitCode(onVerified: { navigatedBackCount += 1 })
        }
        await verifyGate.waitUntilEntered()
        XCTAssertTrue(model.isSubmitting)
        let secondSubmit = Task { @MainActor in
            await model.submitCode(onVerified: { navigatedBackCount += 1 })
        }
        await verifyGate.release()
        await firstSubmit.value
        await secondSubmit.value

        let verifyRequests = stack.transport.requests(method: .post, path: "/api/v1/me/email/verify")
        XCTAssertEqual(verifyRequests.count, 1, "Double-tap verify must issue strictly one POST")
        let bodyJSON = try XCTUnwrap(JSONSerialization.jsonObject(with: try XCTUnwrap(verifyRequests.first?.bodyData)) as? [String: Any])
        XCTAssertEqual(bodyJSON["code"] as? String, "654321")
        XCTAssertEqual(navigatedBackCount, 1)
        XCTAssertEqual(accountVM.loadedProfile?.emailVerified, true)
        XCTAssertEqual(accountVM.loadedProfile?.needsEmailVerificationBanner, false)
        XCTAssertEqual(AccountStrings.text("account.profile.emailVerified", locale: .vi), "Email đã xác minh")

        XCTAssertEqual(ProfileStrings.text("account.verifyEmail.errorLoadTitle", locale: .vi), "Không tải được màn hình")
        XCTAssertEqual(ProfileStrings.text("account.verifyEmail.errorLoadTitle", locale: .en), "Unable to load screen")
        XCTAssertEqual(ProfileStrings.text("account.verifyEmail.alreadyVerified", locale: .vi), "Email của bạn đã được xác minh.")
        XCTAssertEqual(ProfileStrings.text("account.verifyEmail.continue", locale: .vi), "Tiếp tục")
        XCTAssertEqual(ProfileStrings.text("account.verifyEmail.later", locale: .vi), "Để sau")
        let footnote = VerifyEmailView.parseFootnoteLink(ProfileStrings.text("account.verifyEmail.footnote", locale: .vi))
        XCTAssertEqual(footnote.linkText, "Hồ sơ")
        XCTAssertFalse(footnote.plainText.contains("<profile>"))
    }
}
