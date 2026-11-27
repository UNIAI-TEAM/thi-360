import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class PendingEmailTests: XCTestCase {
    func testPendingEmailErrorsResendPrefillAnd204Confirmation() async throws {
        let pendingProfileData = try ContractFixtureLoader.loadApiFixture("me-profile-200-avatar-pending-email.json")
        let invalidCodeData = try ContractFixtureLoader.loadApiFixture("me-email-change-confirm-422-email-code-invalid.json")
        let expiredCodeData = try ContractFixtureLoader.loadApiFixture("me-email-verify-422-email-code-expired.json")
        let takenData = try ContractFixtureLoader.loadApiFixture("me-email-change-409-email-taken.json")

        let clock = ManualClock(start: Date(timeIntervalSince1970: 1_760_000_000))
        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        ) { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/profile", statusCode: 200, data: pendingProfileData)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let accountVM = AccountViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager,
            clock: clock
        )
        await accountVM.loadProfile()
        let vm = accountVM.ensureProfileViewModel()
        XCTAssertEqual(vm.profile?.trimmedPendingEmail, "student.new@thi360.vn")

        let view = NavigationStack {
            ProfileView(viewModel: vm, preferences: stack.preferences)
        }
        .environment(\.brandPalette, .defaultLight)
        .environment(\.statusPalette, .light)
        let (window, controller) = RecoveryViewTestHelper.host(
            view,
            size: CGSize(width: 390, height: 1700),
            sizeClass: .compact,
            themeMode: .light
        )
        let textFields = RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: controller.view)
        XCTAssertTrue(
            textFields.contains { $0.keyboardType == .numberPad && $0.textContentType == .oneTimeCode },
            "Pending email OTP field must configure numberPad + oneTimeCode"
        )
        window.isHidden = true

        vm.updatePendingCode("12345")
        await vm.confirmPendingEmail(locale: .vi)
        XCTAssertEqual(vm.pendingCodeErrorKey, "account.profile.email.codeRequired")
        XCTAssertEqual(stack.transport.callCount(method: .post, path: "/api/v1/me/email/change/confirm"), 0)

        stack.transport.registerJSON(method: .post, path: "/api/v1/me/email/change/confirm", statusCode: 422, data: invalidCodeData)
        vm.updatePendingCode("999999")
        await vm.confirmPendingEmail(locale: .vi)
        XCTAssertEqual(vm.pendingCodeErrorKey, "account.profile.email.codeInvalid")
        XCTAssertEqual(vm.pendingCodeRemainingAttempts, 4)
        XCTAssertEqual(vm.pendingCode, "999999")
        XCTAssertEqual(accountVM.loadedProfile?.email, "student.demo@thi360.vn")
        XCTAssertEqual(accountVM.loadedProfile?.pendingEmail, "student.new@thi360.vn")

        stack.transport.registerJSON(method: .post, path: "/api/v1/me/email/change/confirm", statusCode: 422, data: expiredCodeData)
        await vm.confirmPendingEmail(locale: .vi)
        XCTAssertEqual(vm.pendingAlertKey, "account.profile.email.confirmErrors.EXPIRED")
        XCTAssertEqual(accountVM.loadedProfile?.pendingEmail, "student.new@thi360.vn")

        stack.transport.registerJSON(method: .post, path: "/api/v1/me/email/change/confirm", statusCode: 409, data: takenData)
        await vm.confirmPendingEmail(locale: .vi)
        XCTAssertEqual(vm.pendingAlertKey, "account.profile.email.confirmErrors.EMAIL_TAKEN")
        XCTAssertEqual(accountVM.loadedProfile?.email, "student.demo@thi360.vn")

        stack.transport.registerEmpty(method: .post, path: "/api/v1/me/email/change/confirm", statusCode: 503)
        await vm.confirmPendingEmail(locale: .vi)
        XCTAssertEqual(vm.pendingAlertKey, "account.profile.email.confirmErrors.SYNC_FAILED")

        stack.transport.registerEmpty(method: .post, path: "/api/v1/me/email/change/confirm", statusCode: 500)
        await vm.confirmPendingEmail(locale: .vi)
        XCTAssertEqual(vm.pendingAlertKey, "account.profile.email.confirmErrors.generic")

        stack.transport.registerNetworkError(method: .post, path: "/api/v1/me/email/change/confirm", code: .notConnectedToInternet)
        await vm.confirmPendingEmail(locale: .vi)
        XCTAssertEqual(vm.pendingAlertKey, "account.profile.email.confirmErrors.generic")
        XCTAssertEqual(accountVM.loadedProfile?.pendingEmail, "student.new@thi360.vn")

        vm.resendPendingEmailCode(locale: .vi)
        XCTAssertTrue(vm.isShowingChangeEmailForm)
        XCTAssertEqual(vm.newEmail, "student.new@thi360.vn")
        XCTAssertEqual(vm.currentPassword, "")
        vm.cancelChangeEmailForm()

        let confirmGate = TestAsyncGate()
        stack.transport.resetRecordedRequests()
        stack.transport.register(method: .post, path: "/api/v1/me/email/change/confirm") { _ in
            await confirmGate.enterAndWait()
            return ContractFakeTransport.emptyResponse(statusCode: 204)
        }
        vm.updatePendingCode("123456")

        let first = Task { @MainActor in await vm.confirmPendingEmail(locale: .vi) }
        await confirmGate.waitUntilEntered()
        XCTAssertTrue(vm.isConfirmingEmailChange)
        let second = Task { @MainActor in await vm.confirmPendingEmail(locale: .vi) }
        await confirmGate.release()
        await first.value
        await second.value

        XCTAssertEqual(stack.transport.callCount(method: .post, path: "/api/v1/me/email/change/confirm"), 1)
        XCTAssertEqual(accountVM.loadedProfile?.email, "student.new@thi360.vn")
        XCTAssertEqual(accountVM.loadedProfile?.emailVerified, true)
        XCTAssertNil(accountVM.loadedProfile?.pendingEmail)
        XCTAssertEqual(vm.pendingCode, "")
        XCTAssertEqual(vm.activeToast?.message, "Đã đổi email.")
    }
}
