import Foundation
import XCTest
@testable import Thi360

@MainActor
final class BiometricPromptPolicyTests: XCTestCase {
    func testPromptsAfterPasswordLoginWhenEnrolledAndNoLaterAnswer() async throws {
        let loginData = try ContractFixtureLoader.loadAuthFixture("login-200.json")
        let stack = AccountTestFactory.makeStack(
            capability: .faceIDAvailable
        ) { request in
            if request.path.hasSuffix("/api/v1/auth/mobile/login") {
                return MockClientTransport.jsonResponse(statusCode: 200, data: loginData)
            }
            return MockClientTransport.emptyResponse(statusCode: 404)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        XCTAssertNil(stack.biometricManager.lastPromptAnswer)
        XCTAssertFalse(stack.biometricManager.isPromptingEnable)

        try await stack.sessionStore.login(identifier: "student.demo", password: "Password123!")
        XCTAssertEqual(stack.sessionStore.state, .active)
        XCTAssertTrue(stack.biometricManager.isPromptingEnable, "Enrolled device must prompt to enable biometrics after password login")
    }

    func testPromptsAfterForcedPasswordChangeCompletes() async throws {
        let mustChangeData = try ContractFixtureLoader.loadAuthFixture("login-200-must-change-password.json")
        let stack = AccountTestFactory.makeStack(
            capability: .touchIDAvailable
        ) { request in
            if request.path.hasSuffix("/api/v1/auth/mobile/login") {
                return MockClientTransport.jsonResponse(statusCode: 200, data: mustChangeData)
            }
            return MockClientTransport.emptyResponse(statusCode: 204)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        try await stack.sessionStore.login(identifier: "student.demo", password: "Password123!")
        XCTAssertEqual(stack.sessionStore.state, .mustChangePassword)
        XCTAssertFalse(stack.biometricManager.isPromptingEnable, "Must not prompt before forced password change completes")

        try await stack.sessionStore.completeForcedPasswordChange()
        XCTAssertEqual(stack.sessionStore.state, .active)
        XCTAssertTrue(stack.biometricManager.isPromptingEnable, "Must prompt once forced password change completes")
    }

    func testLaterAnswerPersistsAcrossLogoutAndSuppressesSubsequentPrompt() async throws {
        let loginData = try ContractFixtureLoader.loadAuthFixture("login-200.json")
        let stack = AccountTestFactory.makeStack(
            capability: .faceIDAvailable
        ) { request in
            if request.path.hasSuffix("/api/v1/auth/mobile/login") {
                return MockClientTransport.jsonResponse(statusCode: 200, data: loginData)
            }
            if request.path.hasSuffix("/api/v1/auth/mobile/logout") {
                return MockClientTransport.emptyResponse(statusCode: 204)
            }
            return MockClientTransport.emptyResponse(statusCode: 404)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        try await stack.sessionStore.login(identifier: "student.demo", password: "Password123!")
        XCTAssertTrue(stack.biometricManager.isPromptingEnable)

        stack.biometricManager.declineEnablePromptLater()
        XCTAssertFalse(stack.biometricManager.isPromptingEnable)
        XCTAssertEqual(stack.biometricManager.lastPromptAnswer, .later)

        await stack.sessionStore.signOut(reason: .none)
        XCTAssertEqual(stack.biometricManager.lastPromptAnswer, .later, "Later answer must persist across logout on this device")

        try await stack.sessionStore.login(identifier: "student.demo", password: "Password123!")
        XCTAssertEqual(stack.sessionStore.state, .active)
        XCTAssertFalse(stack.biometricManager.isPromptingEnable, "Must not prompt again after user answered Later and re-logged in")
    }

    func testUnavailableHardwareDoesNotPromptAndLocksAccountSwitchWithExplanation() async throws {
        let loginData = try ContractFixtureLoader.loadAuthFixture("login-200.json")
        let cases: [(BiometricUnavailableReason, String, String)] = [
            (.noHardware, "Thiết bị không hỗ trợ sinh trắc học", "This device does not support biometrics"),
            (.notEnrolled, "Chưa đăng ký sinh trắc học trong Cài đặt của máy", "No biometrics are enrolled in device Settings"),
            (.passcodeNotSet, "Đặt mật mã thiết bị trong Cài đặt để bật tính năng này", "Set a device passcode in Settings to enable this feature")
        ]

        for (reason, expectedVi, expectedEn) in cases {
            let stack = AccountTestFactory.makeStack(
                capability: .unavailable(reason)
            ) { request in
                if request.path.hasSuffix("/api/v1/auth/mobile/login") {
                    return MockClientTransport.jsonResponse(statusCode: 200, data: loginData)
                }
                return MockClientTransport.emptyResponse(statusCode: 404)
            }
            defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

            try await stack.sessionStore.login(identifier: "student.demo", password: "Password123!")
            XCTAssertFalse(stack.biometricManager.isPromptingEnable, "Unavailable \(reason) must never prompt")
            XCTAssertEqual(stack.biometricManager.state, .unavailable(reason))

            let accountVM = AccountViewModel(
                sessionStore: stack.sessionStore,
                biometricManager: stack.biometricManager
            )
            XCTAssertTrue(accountVM.isBiometricSwitchDisabled)
            XCTAssertFalse(accountVM.isBiometricSwitchOn)
            XCTAssertEqual(accountVM.biometricSubtitle(locale: .vi), expectedVi)
            XCTAssertEqual(accountVM.biometricSubtitle(locale: .en), expectedEn)
        }
    }
}
