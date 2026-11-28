import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class ChangeEmailFormTests: XCTestCase {
    func testInlineFormValidationErrorsDoubleSubmitAnd202PendingBlock() async throws {
        let initialData = try ContractFixtureLoader.loadApiFixture("me-profile-200.json")
        let takenData = try ContractFixtureLoader.loadApiFixture("me-email-change-409-email-taken.json")
        let wrongPasswordData = try ContractFixtureLoader.loadApiFixture("me-email-change-400-wrong-old-password.json")
        let invalidEmailData = try ContractFixtureLoader.loadApiFixture("me-email-change-400-new-email.json")
        let rateLimitedData = try ContractFixtureLoader.loadApiFixture("me-email-change-429-rate-limited.json")
        let throttledData = try ContractFixtureLoader.loadApiFixture("me-email-change-429-login-throttled.json")

        let clock = ManualClock(start: Date(timeIntervalSince1970: 1_760_000_000))
        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        ) { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/profile", statusCode: 200, data: initialData)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let accountVM = AccountViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager,
            clock: clock
        )
        await accountVM.loadProfile()
        let vm = accountVM.ensureProfileViewModel()

        XCTAssertFalse(vm.isShowingChangeEmailForm)
        vm.toggleChangeEmailForm()
        XCTAssertTrue(vm.isShowingChangeEmailForm)

        let view = NavigationStack {
            ProfileView(viewModel: vm, preferences: stack.preferences)
        }
        .environment(\.brandPalette, .defaultLight)
        .environment(\.statusPalette, .light)
        let (window, controller) = ProfileFixtureHelper.host(
            view,
            size: CGSize(width: 390, height: 1500),
            sizeClass: .compact,
            themeMode: .light
        )
        let fields = RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: controller.view)
        XCTAssertTrue(fields.contains { $0.keyboardType == .emailAddress }, "New email field must use email keyboard")
        let labels = ProfileFixtureHelper.collectAccessibilityLabels(in: controller.view)
        XCTAssertTrue(labels.contains("Hiện mật khẩu hiện tại"))
        window.isHidden = true

        vm.updateNewEmail("")
        vm.updateCurrentPassword("")
        await vm.submitEmailChange(locale: .vi)
        XCTAssertEqual(vm.newEmailErrorKey, "account.profile.email.newEmailRequired")
        XCTAssertEqual(vm.currentPasswordErrorKey, "account.profile.email.currentPasswordRequired")
        XCTAssertEqual(stack.transport.callCount(method: .post, path: "/api/v1/me/email/change"), 0)

        vm.updateNewEmail("invalid-email")
        vm.updateCurrentPassword("Password123!")
        await vm.submitEmailChange(locale: .vi)
        XCTAssertEqual(vm.newEmailErrorKey, "account.profile.email.newEmailInvalid")
        XCTAssertNil(vm.currentPasswordErrorKey)
        XCTAssertEqual(stack.transport.callCount(method: .post, path: "/api/v1/me/email/change"), 0)

        vm.updateNewEmail("  taken@thi360.vn  ")
        vm.updateCurrentPassword("Password123!")
        stack.transport.registerJSON(method: .post, path: "/api/v1/me/email/change", statusCode: 409, data: takenData)
        await vm.submitEmailChange(locale: .vi)
        XCTAssertEqual(vm.newEmailErrorKey, "account.profile.email.newEmailTaken")
        XCTAssertEqual(vm.newEmail, "  taken@thi360.vn  ")
        XCTAssertEqual(vm.currentPassword, "Password123!")
        XCTAssertNil(accountVM.loadedProfile?.pendingEmail)

        stack.transport.registerJSON(method: .post, path: "/api/v1/me/email/change", statusCode: 400, data: wrongPasswordData)
        await vm.submitEmailChange(locale: .vi)
        XCTAssertEqual(vm.currentPasswordErrorKey, "account.profile.email.wrongPassword")

        stack.transport.registerJSON(method: .post, path: "/api/v1/me/email/change", statusCode: 400, data: invalidEmailData)
        await vm.submitEmailChange(locale: .vi)
        XCTAssertEqual(vm.newEmailErrorKey, "account.profile.email.newEmailInvalid")

        stack.transport.registerJSON(method: .post, path: "/api/v1/me/email/change", statusCode: 429, data: rateLimitedData)
        await vm.submitEmailChange(locale: .vi)
        XCTAssertEqual(vm.changeEmailAlertKey, "account.profile.email.changeErrors.RATE_LIMITED")
        XCTAssertEqual(vm.cooldownRemainingSeconds, 60)
        XCTAssertTrue(vm.isCooldownActive)

        clock.advance(by: 61)
        vm.tickCooldown(locale: .vi)
        XCTAssertFalse(vm.isCooldownActive)
        stack.transport.registerJSON(method: .post, path: "/api/v1/me/email/change", statusCode: 429, data: throttledData)
        await vm.submitEmailChange(locale: .vi)
        XCTAssertEqual(vm.changeEmailAlertKey, "account.profile.email.changeErrors.RATE_LIMITED")
        XCTAssertEqual(vm.cooldownRemainingSeconds, 60)

        clock.advance(by: 61)
        vm.tickCooldown(locale: .vi)
        stack.transport.registerEmpty(method: .post, path: "/api/v1/me/email/change", statusCode: 500)
        await vm.submitEmailChange(locale: .vi)
        XCTAssertEqual(vm.changeEmailAlertKey, "account.profile.email.changeErrors.generic")

        stack.transport.registerNetworkError(method: .post, path: "/api/v1/me/email/change", code: .notConnectedToInternet)
        await vm.submitEmailChange(locale: .vi)
        XCTAssertEqual(vm.changeEmailAlertKey, "account.profile.email.changeErrors.generic")
        XCTAssertNil(accountVM.loadedProfile?.pendingEmail)

        let gate = TestAsyncGate()
        stack.transport.resetRecordedRequests()
        stack.transport.register(method: .post, path: "/api/v1/me/email/change") { _ in
            await gate.enterAndWait()
            return ContractFakeTransport.emptyResponse(statusCode: 202)
        }
        vm.updateNewEmail("  new.student@thi360.vn ")
        vm.updateCurrentPassword("Password123!")

        let first = Task { @MainActor in await vm.submitEmailChange(locale: .vi) }
        await gate.waitUntilEntered()
        XCTAssertTrue(vm.isSendingEmailChange)
        let second = Task { @MainActor in await vm.submitEmailChange(locale: .vi) }
        await gate.release()
        await first.value
        await second.value

        let requests = stack.transport.requests(method: .post, path: "/api/v1/me/email/change")
        XCTAssertEqual(requests.count, 1)
        let reqJSON = try XCTUnwrap(JSONSerialization.jsonObject(with: try XCTUnwrap(requests.first?.bodyData)) as? [String: Any])
        XCTAssertEqual(reqJSON["newEmail"] as? String, "new.student@thi360.vn")
        XCTAssertEqual(reqJSON["currentPassword"] as? String, "Password123!")

        XCTAssertFalse(vm.isShowingChangeEmailForm)
        XCTAssertEqual(accountVM.loadedProfile?.pendingEmail, "new.student@thi360.vn")
        XCTAssertEqual(accountVM.loadedProfile?.email, "student.demo@thi360.vn", "Current email must stay unchanged until confirmed")
        XCTAssertEqual(accountVM.loadedProfile?.emailVerified, true)
        XCTAssertEqual(vm.cooldownRemainingSeconds, 60)
    }
}
