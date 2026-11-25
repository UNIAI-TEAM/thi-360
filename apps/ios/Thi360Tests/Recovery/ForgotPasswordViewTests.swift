import Foundation
import SwiftUI
import XCTest
@testable import Thi360

@MainActor
final class ForgotPasswordViewTests: XCTestCase {
    func testInvalidEmailShowsInlineErrorWithoutCallingNetwork() async {
        let (sessionStore, _, transport, _) = SessionTestFactory.makeStack { _ in
            MockClientTransport.emptyResponse(statusCode: 500)
        }
        let vm = ForgotPasswordViewModel(apiClient: sessionStore.apiClient, clock: ManualClock())

        await vm.submit()
        XCTAssertEqual(vm.emailError, .emailRequired)
        XCTAssertTrue(transport.recordedRequests.isEmpty)

        vm.email = "invalid-email"
        await vm.submit()
        XCTAssertEqual(vm.emailError, .emailInvalid)
        XCTAssertEqual(vm.emailError?.message(locale: .vi), "Email chưa đúng định dạng.")
        XCTAssertTrue(transport.recordedRequests.isEmpty)
    }

    func testSameSentScreenForExistingAndNonExistingEmailsWith60SecondResendCooldown() async throws {
        let forgotFixture = try ContractFixtureLoader.loadApiFixture("password-forgot-202.json")
        let gate = TestAsyncGate()

        let (sessionStore, _, transport, preferences) = SessionTestFactory.makeStack { request in
            if request.path.hasSuffix("/api/v1/auth/password/forgot") {
                await gate.enterAndWait()
                return MockClientTransport.jsonResponse(statusCode: 202, data: forgotFixture)
            }
            return MockClientTransport.emptyResponse(statusCode: 500)
        }

        let clock = ManualClock()
        let existingVM = ForgotPasswordViewModel(apiClient: sessionStore.apiClient, clock: clock)
        existingVM.email = "existing@thi360.vn"

        let first = Task { @MainActor in
            await existingVM.submit()
        }
        await gate.waitUntilEntered()
        XCTAssertTrue(existingVM.isSubmitting)

        let duplicate = Task { @MainActor in
            await existingVM.submit()
        }
        await duplicate.value
        XCTAssertEqual(transport.recordedRequests.count, 1, "Duplicate submit while in flight must be ignored")

        await gate.release()
        await first.value

        guard case .sent = existingVM.state else {
            XCTFail("Expected sent state")
            return
        }
        XCTAssertEqual(existingVM.resendSecondsRemaining, 60)
        XCTAssertFalse(existingVM.canResend)

        let (session2, _, _, _) = SessionTestFactory.makeStack { _ in
            MockClientTransport.jsonResponse(statusCode: 202, data: forgotFixture)
        }
        let unknownVM = ForgotPasswordViewModel(apiClient: session2.apiClient, clock: clock)
        unknownVM.email = "does-not-exist@example.com"
        await unknownVM.submit()

        guard case .sent = unknownVM.state else {
            XCTFail("Non-existing email must produce identical sent screen")
            return
        }
        XCTAssertEqual(unknownVM.resendSecondsRemaining, existingVM.resendSecondsRemaining)
        XCTAssertEqual(
            RecoveryStrings.text("recovery.forgot.sentTitle", locale: .vi),
            "Hãy kiểm tra hộp thư"
        )

        clock.advance(by: 59)
        existingVM.tick()
        XCTAssertEqual(existingVM.resendSecondsRemaining, 1)
        XCTAssertFalse(existingVM.canResend)
        await existingVM.resend()
        XCTAssertEqual(transport.recordedRequests.count, 1)

        clock.advance(by: 1)
        existingVM.tick()
        XCTAssertEqual(existingVM.resendSecondsRemaining, 0)
        XCTAssertTrue(existingVM.canResend)

        await existingVM.resend()
        XCTAssertEqual(transport.recordedRequests.count, 2)
        XCTAssertEqual(existingVM.resendSecondsRemaining, 60, "Resend must restart 60-second cooldown")
        guard case .sent = existingVM.state else {
            XCTFail("Resend must remain on the same sent screen")
            return
        }

        let size = CGSize(width: 390, height: 844)
        let sentView = ForgotPasswordView(viewModel: existingVM, preferences: preferences, onBackToLogin: {})
            .environment(\.brandPalette, .defaultLight)
        let (window, _) = RecoveryViewTestHelper.host(sentView, size: size, sizeClass: .compact, themeMode: .light)
        defer { window.isHidden = true }
        _ = try RecoveryViewTestHelper.captureSnapshot(window: window, size: size, name: "forgot-sent-iphone17-390x844-light", in: self)
    }
}
