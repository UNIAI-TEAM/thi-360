import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class RegisterViewTests: XCTestCase {
    func testInvalidInputsShowFieldErrorsWithoutCallingNetwork() async {
        let (sessionStore, _, transport, _) = SessionTestFactory.makeStack { _ in
            MockClientTransport.emptyResponse(statusCode: 500)
        }

        let viewModel = RegisterViewModel(sessionStore: sessionStore)
        await viewModel.submitRegistration()

        XCTAssertEqual(viewModel.fullNameError, .fullNameRequired)
        XCTAssertEqual(viewModel.usernameError, .usernameRequired)
        XCTAssertEqual(viewModel.emailError, .emailRequired)
        XCTAssertEqual(viewModel.passwordError, .passwordRequired)
        XCTAssertEqual(viewModel.termsError, .termsRequired)
        XCTAssertTrue(transport.recordedRequests.isEmpty)

        viewModel.fullName = "Tran Minh Anh"
        viewModel.username = "-bad"
        viewModel.email = "not-an-email"
        viewModel.password = "weak"
        viewModel.acceptTerms = false
        await viewModel.submitRegistration()

        XCTAssertNil(viewModel.fullNameError)
        XCTAssertEqual(viewModel.usernameError, .usernamePattern)
        XCTAssertEqual(viewModel.emailError, .emailInvalid)
        XCTAssertEqual(viewModel.passwordError, .passwordWeak)
        XCTAssertEqual(viewModel.termsError, .termsRequired)
        XCTAssertEqual(
            viewModel.usernameError?.message(locale: .vi),
            "Tên đăng nhập chưa đúng quy tắc. Xem hướng dẫn bên dưới ô nhập."
        )
        XCTAssertEqual(viewModel.emailError?.message(locale: .vi), "Email chưa đúng định dạng.")
        XCTAssertTrue(transport.recordedRequests.isEmpty)

        viewModel.username = "Ab1!xxxx"
        viewModel.email = "student@thi360.vn"
        viewModel.password = "Ab1!xxxx"
        viewModel.acceptTerms = true
        await viewModel.submitRegistration()
        XCTAssertEqual(viewModel.passwordError, .passwordSameAsUsername)
        XCTAssertTrue(transport.recordedRequests.isEmpty)
    }

    func testPasswordChecklistUpdatesLiveAsUserTypes() {
        let (sessionStore, _, _, _) = SessionTestFactory.makeStack { _ in
            MockClientTransport.emptyResponse(statusCode: 500)
        }
        let viewModel = RegisterViewModel(sessionStore: sessionStore)

        XCTAssertFalse(viewModel.passwordEvaluation.isStrong)
        viewModel.password = "Matkhau1"
        XCTAssertTrue(viewModel.passwordEvaluation.hasValidLength)
        XCTAssertTrue(viewModel.passwordEvaluation.hasUppercase)
        XCTAssertTrue(viewModel.passwordEvaluation.hasLowercase)
        XCTAssertTrue(viewModel.passwordEvaluation.hasDigit)
        XCTAssertFalse(viewModel.passwordEvaluation.hasSpecial)

        viewModel.password = "Matkhau1!"
        XCTAssertTrue(viewModel.passwordEvaluation.hasSpecial)
        XCTAssertTrue(viewModel.passwordEvaluation.isStrong)
    }

    func testUsernameTakenAndEmailTakenShowUnderMatchingFieldsAndPreserveData() async throws {
        let usernameTakenData = try ContractFixtureLoader.loadApiFixture("register-409-username-taken.json")
        let emailTakenData = try ContractFixtureLoader.loadApiFixture("register-409-email-taken.json")

        let (session1, _, _, _) = SessionTestFactory.makeStack { _ in
            MockClientTransport.jsonResponse(statusCode: 409, data: usernameTakenData)
        }
        let vm1 = RegisterViewModel(sessionStore: session1)
        vm1.fullName = "Tran Minh Anh"
        vm1.username = "taken.user"
        vm1.email = "new@thi360.vn"
        vm1.password = "StrongPass1!"
        vm1.acceptTerms = true

        await vm1.submitRegistration()
        XCTAssertEqual(vm1.usernameError, .usernameTaken)
        XCTAssertEqual(
            vm1.usernameError?.message(locale: .vi),
            "Tên đăng nhập này đã được sử dụng. Hãy chọn tên khác."
        )
        XCTAssertEqual(vm1.fullName, "Tran Minh Anh")
        XCTAssertEqual(vm1.username, "taken.user")
        XCTAssertEqual(vm1.email, "new@thi360.vn")
        XCTAssertEqual(vm1.password, "StrongPass1!")

        let (session2, _, _, _) = SessionTestFactory.makeStack { _ in
            MockClientTransport.jsonResponse(statusCode: 409, data: emailTakenData)
        }
        let vm2 = RegisterViewModel(sessionStore: session2)
        vm2.fullName = "Tran Minh Anh"
        vm2.username = "unique.user"
        vm2.email = "taken@thi360.vn"
        vm2.password = "StrongPass1!"
        vm2.acceptTerms = true

        await vm2.submitRegistration()
        XCTAssertEqual(vm2.emailError, .emailTaken)
        XCTAssertEqual(
            vm2.emailError?.message(locale: .vi),
            "Email này đã có tài khoản. Hãy đăng nhập hoặc dùng Quên mật khẩu."
        )
        XCTAssertEqual(vm2.username, "unique.user")
        XCTAssertEqual(vm2.email, "taken@thi360.vn")
    }

    func testRegisterViewRendersSnapshotsLightAndDark() throws {
        var pngs: [ThemeMode: Data] = [:]
        for mode in ThemeMode.allCases {
            let (sessionStore, _, _, preferences) = SessionTestFactory.makeStack { _ in
                MockClientTransport.emptyResponse(statusCode: 500)
            }
            preferences.themeMode = mode
            let vm = RegisterViewModel(sessionStore: sessionStore)
            vm.fullName = "Trần Minh Anh"
            vm.username = "minhanh.tran"
            vm.email = "minhanh@example.com"
            vm.password = "Matkhau1"
            vm.acceptTerms = true

            let view = RegisterView(viewModel: vm, preferences: preferences, onBackToLogin: {})
                .environment(\.brandPalette, BrandPaletteSet.defaultThi360Green.palette(for: mode))

            let size = CGSize(width: 390, height: 844)
            let (window, controller) = RecoveryViewTestHelper.host(view, size: size, sizeClass: .compact, themeMode: mode)
            defer { window.isHidden = true }

            XCTAssertGreaterThanOrEqual(RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: controller.view).count, 4)
            pngs[mode] = try RecoveryViewTestHelper.captureSnapshot(
                window: window,
                size: size,
                name: "register-iphone17-390x844-\(mode.rawValue)",
                in: self
            )
        }
        XCTAssertNotEqual(pngs[.light], pngs[.dark])
    }
}
