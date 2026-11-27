import Foundation
import SwiftUI
import XCTest
@testable import Thi360

@MainActor
final class ChangePasswordTests: XCTestCase {
    func testVoluntaryChangePasswordKeepsSessionActiveAndSubmitsOnce() async throws {
        let gate = TestAsyncGate()
        let initialTokens = SessionTokens(
            accessToken: "voluntary-access",
            refreshToken: "voluntary-refresh",
            accessExpiresIn: 300,
            refreshExpiresIn: 604_800,
            mustChangePassword: false,
            username: "minhanh.tran"
        )

        let (sessionStore, tokenStore, transport, preferences) = try SessionTestFactory.makeContractStack(
            initialTokens: initialTokens
        ) { transport in
            transport.register(method: .post, path: "/api/v1/auth/change-password") { _ in
                await gate.enterAndWait()
                return ContractFakeTransport.emptyResponse(statusCode: 204)
            }
        }

        let vm = NewPasswordViewModel(
            mode: .voluntary(username: "minhanh.tran"),
            sessionStore: sessionStore
        )
        vm.oldPassword = "OldPassword1!"
        vm.newPassword = "NewStrongPass2!"
        vm.confirmPassword = "NewStrongPass2!"

        let first = Task { @MainActor in
            await vm.submit()
        }
        await gate.waitUntilEntered()
        XCTAssertTrue(vm.isSubmitting)

        let duplicate = Task { @MainActor in
            await vm.submit()
        }
        await duplicate.value
        XCTAssertEqual(transport.callCount(method: .post, path: "/api/v1/auth/change-password"), 1)

        await gate.release()
        await first.value

        XCTAssertTrue(vm.didComplete)
        XCTAssertEqual(vm.banner, .changedSuccess)
        XCTAssertEqual(vm.oldPassword, "")
        XCTAssertEqual(vm.newPassword, "")
        XCTAssertEqual(vm.confirmPassword, "")
        XCTAssertEqual(sessionStore.state, .active, "Voluntary password change must keep session active")
        XCTAssertEqual(try tokenStore.read()?.accessToken, "voluntary-access")

        let size = CGSize(width: 500, height: 700)
        let view = NewPasswordView(viewModel: vm, preferences: preferences, isEmbeddedPane: true)
            .environment(\.brandPalette, .defaultLight)
        let (window, _) = RecoveryViewTestHelper.host(view, size: size, sizeClass: .regular, themeMode: .light)
        defer { window.isHidden = true }
        _ = try RecoveryViewTestHelper.captureSnapshot(window: window, size: size, name: "change-password-regular-light", in: self)
    }

    func testFieldErrorsMapCorrectlyForWrongOldPasswordSameAsUsernameWeakAndMismatch() async throws {
        let wrongOldFixture = try ContractFixtureLoader.loadApiFixture("change-password-400-wrong-old-password.json")
        let weakFixture = try ContractFixtureLoader.loadApiFixture("change-password-400-password-weak.json")
        let sameAsUsernameJSON = Data(
            #"{"code":"PASSWORD_WEAK","message":"Weak","fieldErrors":[{"field":"newPassword","code":"PASSWORD_SAME_AS_USERNAME","message":"Same"}],"traceId":"0"}"#.utf8
        )

        let initialTokens = SessionTokens(
            accessToken: "voluntary-access",
            refreshToken: "voluntary-refresh",
            accessExpiresIn: 300,
            refreshExpiresIn: 604_800,
            mustChangePassword: false,
            username: "Ab1!xxxx"
        )

        let (session1, _, transport1, _) = try SessionTestFactory.makeContractStack(initialTokens: initialTokens) { transport in
            transport.registerJSON(method: .post, path: "/api/v1/auth/change-password", statusCode: 400, data: wrongOldFixture)
        }
        let vm1 = NewPasswordViewModel(mode: .voluntary(username: "Ab1!xxxx"), sessionStore: session1)

        vm1.oldPassword = "OldPassword1!"
        vm1.newPassword = "Ab1!xxxx"
        vm1.confirmPassword = "Ab1!xxxx"
        await vm1.submit()
        XCTAssertEqual(vm1.newPasswordError, .newPasswordSameAsUsername)
        XCTAssertEqual(vm1.newPasswordError?.message(locale: .vi), "Mật khẩu mới không được trùng tên đăng nhập.")
        XCTAssertTrue(transport1.recordedRequests.isEmpty, "Client-side same-as-username check must not hit network")

        vm1.newPassword = "NewStrongPass2!"
        vm1.confirmPassword = "MismatchPass2!"
        await vm1.submit()
        XCTAssertEqual(vm1.confirmPasswordError, .confirmPasswordMismatch)
        XCTAssertTrue(transport1.recordedRequests.isEmpty)

        vm1.confirmPassword = "NewStrongPass2!"
        await vm1.submit()
        XCTAssertEqual(vm1.oldPasswordError, .wrongOldPassword)
        XCTAssertEqual(vm1.oldPasswordError?.message(locale: .vi), "Mật khẩu hiện tại không đúng.")
        XCTAssertEqual(vm1.oldPasswordError?.message(locale: .en), "Current password is incorrect.")
        XCTAssertEqual(session1.state, .active)

        let (session2, _, _, _) = try SessionTestFactory.makeContractStack(initialTokens: initialTokens) { transport in
            transport.registerJSON(method: .post, path: "/api/v1/auth/change-password", statusCode: 400, data: weakFixture)
        }
        let vm2 = NewPasswordViewModel(mode: .voluntary(username: "other.user"), sessionStore: session2)
        vm2.oldPassword = "OldPassword1!"
        vm2.newPassword = "NewStrongPass2!"
        vm2.confirmPassword = "NewStrongPass2!"
        await vm2.submit()
        XCTAssertEqual(vm2.newPasswordError, .newPasswordWeak)

        let (session3, _, _, _) = try SessionTestFactory.makeContractStack(initialTokens: initialTokens) { transport in
            transport.registerJSON(method: .post, path: "/api/v1/auth/change-password", statusCode: 400, data: sameAsUsernameJSON)
        }
        let vm3 = NewPasswordViewModel(mode: .voluntary(username: "other.user"), sessionStore: session3)
        vm3.oldPassword = "OldPassword1!"
        vm3.newPassword = "NewStrongPass2!"
        vm3.confirmPassword = "NewStrongPass2!"
        await vm3.submit()
        XCTAssertEqual(vm3.newPasswordError, .newPasswordSameAsUsername)
    }

    func testChangePasswordAcceptsBoth204NoContentAnd200Ok() async throws {
        let initialTokens = SessionTokens(
            accessToken: "voluntary-access",
            refreshToken: "voluntary-refresh",
            accessExpiresIn: 300,
            refreshExpiresIn: 604_800,
            mustChangePassword: false,
            username: "minhanh.tran"
        )

        for statusCode in [204, 200] {
            let (sessionStore, _, _, _) = try SessionTestFactory.makeContractStack(initialTokens: initialTokens) { transport in
                transport.registerEmpty(method: .post, path: "/api/v1/auth/change-password", statusCode: statusCode)
            }

            let vm = NewPasswordViewModel(mode: .voluntary(username: "minhanh.tran"), sessionStore: sessionStore)
            vm.oldPassword = "OldPassword1!"
            vm.newPassword = "NewStrongPass2!"
            vm.confirmPassword = "NewStrongPass2!"

            await vm.submit()

            XCTAssertTrue(vm.didComplete, "HTTP \(statusCode) from /api/v1/auth/change-password must succeed")
            XCTAssertEqual(vm.banner, .changedSuccess)
            XCTAssertEqual(vm.oldPassword, "")
            XCTAssertEqual(vm.newPassword, "")
            XCTAssertEqual(vm.confirmPassword, "")
        }
    }
}
