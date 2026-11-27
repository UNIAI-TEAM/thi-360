import Foundation
import XCTest
@testable import Thi360

@MainActor
final class NewPasswordFromLinkTests: XCTestCase {
    func testResetSuccessClearsLocalSessionAndRoutesToLoginWithPostResetBanner() async throws {
        let gate = TestAsyncGate()
        let existingTokens = SessionTokens(
            accessToken: "existing-user-access",
            refreshToken: "existing-user-refresh",
            accessExpiresIn: 300,
            refreshExpiresIn: 604_800,
            mustChangePassword: false,
            username: "another.student"
        )

        let (sessionStore, tokenStore, transport, _) = try SessionTestFactory.makeContractStack(
            initialTokens: existingTokens
        ) { transport in
            transport.register(method: .post, path: "/api/v1/auth/password/reset") { _ in
                await gate.enterAndWait()
                return ContractFakeTransport.emptyResponse(statusCode: 204)
            }
        }
        XCTAssertEqual(sessionStore.state, .active)

        let link = ResetLink(token: "valid-reset-token-xyz")
        let router = AppRouter(initialResetLink: link)
        let vm = router.resetPasswordViewModel(for: link, sessionStore: sessionStore)
        vm.newPassword = "NewStrong1!"
        vm.confirmPassword = "NewStrong1!"

        let first = Task { @MainActor in
            await vm.submit()
        }
        await gate.waitUntilEntered()
        XCTAssertTrue(vm.isSubmitting)

        let duplicate = Task { @MainActor in
            await vm.submit()
        }
        await duplicate.value
        XCTAssertEqual(transport.callCount(method: .post, path: "/api/v1/auth/password/reset"), 1, "Rapid duplicate reset submit must send only once")

        await gate.release()
        await first.value

        XCTAssertTrue(vm.didComplete)
        XCTAssertNil(try tokenStore.read(), "Successful password reset must wipe any stored session on device")
        XCTAssertEqual(sessionStore.state, .signedOut(reason: .passwordReset))
        XCTAssertNil(router.activeResetLink)
        XCTAssertFalse(vm.hasActiveResetToken)

        let loginVM = LoginViewModel(sessionStore: sessionStore, initialReason: .passwordReset)
        XCTAssertEqual(
            loginVM.serverBanner?.message(locale: .vi),
            "Đã đặt lại mật khẩu. Hãy đăng nhập bằng mật khẩu mới."
        )
        XCTAssertEqual(
            loginVM.serverBanner?.message(locale: .en),
            "Your password has been reset. Sign in with your new password."
        )
    }

    func testInvalidOrExpiredResetTokenShowsInvalidLinkScreenAndRoutesToForgotPassword() async throws {
        let invalidFixture = try ContractFixtureLoader.loadApiFixture("password-reset-422-reset-token-invalid.json")
        let (sessionStore, _, _, _) = try SessionTestFactory.makeContractStack { transport in
            transport.registerJSON(method: .post, path: "/api/v1/auth/password/reset", statusCode: 422, data: invalidFixture)
        }

        let link = ResetLink(token: "expired-reset-token")
        let router = AppRouter(initialResetLink: link)
        let vm = router.resetPasswordViewModel(for: link, sessionStore: sessionStore)
        vm.newPassword = "NewStrong1!"
        vm.confirmPassword = "NewStrong1!"

        await vm.submit()

        XCTAssertTrue(vm.isResetLinkInvalid, "422 RESET_TOKEN_INVALID must transition to invalid-link state")
        XCTAssertFalse(vm.hasActiveResetToken, "Invalid reset token must be wiped immediately")
        XCTAssertEqual(
            RecoveryStrings.text("recovery.reset.invalidTitle", locale: .vi),
            "Liên kết không còn dùng được"
        )
        XCTAssertEqual(
            RecoveryStrings.text("recovery.reset.invalidTitle", locale: .en),
            "This link can no longer be used"
        )

        router.openForgotPasswordFromExpiredResetLink(isAuthenticated: false)
        XCTAssertNil(router.activeResetLink)
        XCTAssertEqual(router.signedOutRoute, .forgotPassword)
    }

    func testWeakOrMismatchedPasswordsShowFieldErrorsWithoutCallingNetwork() async throws {
        let (sessionStore, _, transport, _) = try SessionTestFactory.makeContractStack()

        let vm = NewPasswordViewModel(
            mode: .fromLink(ResetLink(token: "valid-token")),
            sessionStore: sessionStore
        )

        vm.newPassword = "weak"
        vm.confirmPassword = "weak"
        await vm.submit()
        XCTAssertEqual(vm.newPasswordError, .newPasswordWeak)
        XCTAssertTrue(transport.recordedRequests.isEmpty)

        vm.newPassword = "NewStrong1!"
        vm.confirmPassword = "Different1!"
        await vm.submit()
        XCTAssertNil(vm.newPasswordError)
        XCTAssertEqual(vm.confirmPasswordError, .confirmPasswordMismatch)
        XCTAssertTrue(transport.recordedRequests.isEmpty)
    }
}
