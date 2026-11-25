import Foundation
import HTTPTypes
import SwiftUI
import XCTest
@testable import Thi360

@MainActor
final class OtpViewTests: XCTestCase {
    func testValidCodeEntersApp() async throws {
        let registerFixture = try ContractFixtureLoader.loadApiFixture("register-200.json")
        let verifyFixture = try ContractFixtureLoader.loadAuthFixture("register-verify-201.json")
        let gate = TestAsyncGate()

        let (sessionStore, tokenStore, transport, _) = SessionTestFactory.makeStack { request in
            if request.path.hasSuffix("/api/v1/auth/register") {
                return MockClientTransport.jsonResponse(statusCode: 200, data: registerFixture)
            }
            if request.path.hasSuffix("/api/v1/auth/mobile/register/verify") {
                await gate.enterAndWait()
                return MockClientTransport.jsonResponse(statusCode: 201, data: verifyFixture)
            }
            return MockClientTransport.emptyResponse(statusCode: 500)
        }

        let vm = try await makeOtpStepViewModel(sessionStore: sessionStore, clock: ManualClock())
        vm.updateOtpInput("123456")

        let first = Task { @MainActor in
            await vm.submitOtp()
        }
        await gate.waitUntilEntered()
        XCTAssertTrue(vm.isVerifying)

        let duplicate = Task { @MainActor in
            await vm.submitOtp()
        }
        await duplicate.value
        let verifyCalls = transport.recordedRequests.filter { $0.path.hasSuffix("/api/v1/auth/mobile/register/verify") }
        XCTAssertEqual(verifyCalls.count, 1, "Rapid duplicate OTP verification must send only one request")

        await gate.release()
        await first.value

        XCTAssertEqual(vm.state, .done)
        XCTAssertEqual(sessionStore.state, .active)
        let stored = try tokenStore.read()
        XCTAssertEqual(stored?.accessToken, "fixture-access-token")
        XCTAssertEqual(stored?.refreshToken, "fixture-refresh-token")
        XCTAssertEqual(stored?.username, "minhanh.tran")
    }

    func testInvalidCodeShowsRemainingAttemptsAndExpiredCodeShowsGuidance() async throws {
        let registerFixture = try ContractFixtureLoader.loadApiFixture("register-200.json")
        let invalidFixture = try ContractFixtureLoader.loadAuthFixture("register-verify-422-email-code-invalid.json")
        let expiredFixture = try ContractFixtureLoader.loadAuthFixture("register-verify-422-email-code-expired.json")

        let (sessionInvalid, tokenStoreInvalid, transportInvalid, _) = SessionTestFactory.makeStack { request in
            if request.path.hasSuffix("/api/v1/auth/register") {
                return MockClientTransport.jsonResponse(statusCode: 200, data: registerFixture)
            }
            return MockClientTransport.jsonResponse(statusCode: 422, data: invalidFixture)
        }
        let vmInvalid = try await makeOtpStepViewModel(sessionStore: sessionInvalid, clock: ManualClock())

        vmInvalid.updateOtpInput("123")
        await vmInvalid.submitOtp()
        XCTAssertEqual(vmInvalid.otpCodeError, .codeRequired)
        XCTAssertEqual(transportInvalid.recordedRequests.count, 1, "Short OTP must fail locally without calling verify")

        vmInvalid.updateOtpInput("654321")
        await vmInvalid.submitOtp()
        XCTAssertEqual(vmInvalid.otpCodeError, .codeInvalid(remainingAttempts: 4))
        XCTAssertEqual(
            vmInvalid.otpCodeError?.message(locale: .vi),
            "Mã chưa đúng. Bạn còn 4 lần thử."
        )
        XCTAssertEqual(
            vmInvalid.otpCodeError?.message(locale: .en),
            "Incorrect code. You have 4 attempts left."
        )
        XCTAssertNil(try tokenStoreInvalid.read())
        XCTAssertEqual(sessionInvalid.state, .signedOut(reason: .none))

        let (sessionExpired, _, _, _) = SessionTestFactory.makeStack { request in
            if request.path.hasSuffix("/api/v1/auth/register") {
                return MockClientTransport.jsonResponse(statusCode: 200, data: registerFixture)
            }
            return MockClientTransport.jsonResponse(statusCode: 422, data: expiredFixture)
        }
        let vmExpired = try await makeOtpStepViewModel(sessionStore: sessionExpired, clock: ManualClock())
        vmExpired.updateOtpInput("111111")
        await vmExpired.submitOtp()
        XCTAssertEqual(vmExpired.otpAlert, .codeExpired)
        XCTAssertEqual(
            vmExpired.otpAlert?.message(locale: .vi),
            "Mã này không còn dùng được. Bấm Gửi lại mã để nhận mã mới."
        )
    }

    func testResendCooldown() async throws {
        let registerFixture = try ContractFixtureLoader.loadApiFixture("register-200.json")
        let resend200 = Data(#"{"registrationId":"019320a0-0000-7000-8000-000000000002","email":"student.demo@thi360.vn","resendAfterSeconds":45}"#.utf8)
        let resend429 = Data(#"{"code":"RATE_LIMITED","message":"Too fast","fieldErrors":[],"traceId":"0","retryAfter":75,"remainingAttempts":null}"#.utf8)
        let resendDailyLimit = Data(#"{"code":"RATE_LIMITED","message":"Daily limit","fieldErrors":[],"traceId":"0","retryAfter":7200,"remainingAttempts":null}"#.utf8)

        let callCounter = ResendSequence()
        let (sessionStore, _, transport, preferences) = SessionTestFactory.makeStack(locale: .en) { request in
            if request.path.hasSuffix("/api/v1/auth/register") {
                return MockClientTransport.jsonResponse(statusCode: 200, data: registerFixture)
            }
            if request.path.hasSuffix("/api/v1/auth/register/resend") {
                let attempt = await callCounter.next()
                switch attempt {
                case 1:
                    return MockClientTransport.jsonResponse(statusCode: 200, data: resend200)
                case 2:
                    return MockClientTransport.jsonResponse(statusCode: 429, data: resend429)
                default:
                    return MockClientTransport.jsonResponse(statusCode: 429, data: resendDailyLimit)
                }
            }
            return MockClientTransport.emptyResponse(statusCode: 500)
        }

        let clock = ManualClock()
        let vm = try await makeOtpStepViewModel(sessionStore: sessionStore, clock: clock)

        XCTAssertEqual(vm.resendSecondsRemaining, 60)
        XCTAssertFalse(vm.canResend)

        await vm.resendOtp()
        XCTAssertEqual(transport.recordedRequests.filter { $0.path.hasSuffix("/resend") }.count, 0)

        clock.advance(by: 59)
        vm.tick()
        XCTAssertEqual(vm.resendSecondsRemaining, 1)
        XCTAssertFalse(vm.canResend)

        clock.advance(by: 1)
        vm.tick()
        XCTAssertEqual(vm.resendSecondsRemaining, 0)
        XCTAssertTrue(vm.canResend)

        await vm.resendOtp()
        XCTAssertTrue(vm.didResendCode)
        XCTAssertEqual(vm.resendSecondsRemaining, 45, "Cooldown must follow resendAfterSeconds from server response")
        XCTAssertFalse(vm.canResend)
        XCTAssertEqual(
            transport.recordedRequests.last?.header(.acceptLanguage),
            "en",
            "Resend must send Accept-Language matching AppPreferences.locale"
        )
        XCTAssertEqual(
            RecoveryStrings.text("recovery.otp.codeResent", locale: .vi),
            "Đã gửi mã mới. Mã cũ không còn dùng được."
        )

        clock.advance(by: 45)
        vm.tick()
        XCTAssertTrue(vm.canResend)

        await vm.resendOtp()
        XCTAssertFalse(vm.didResendCode)
        XCTAssertEqual(vm.resendSecondsRemaining, 75, "429 response must lock resend using server retryAfter")
        XCTAssertNil(vm.dailyLimitUntil)

        clock.advance(by: 75)
        vm.tick()
        await vm.resendOtp()
        XCTAssertEqual(vm.resendSecondsRemaining, 7200)
        XCTAssertNotNil(vm.dailyLimitUntil, "Long retryAfter >= 3600s must show daily limit notice")

        preferences.themeMode = .light
        let view = RegisterView(viewModel: vm, preferences: preferences, onBackToLogin: {})
            .environment(\.brandPalette, .defaultLight)
        let size = CGSize(width: 390, height: 844)
        let (window, _) = RecoveryViewTestHelper.host(view, size: size, sizeClass: .compact, themeMode: .light)
        defer { window.isHidden = true }
        _ = try RecoveryViewTestHelper.captureSnapshot(window: window, size: size, name: "otp-iphone17-390x844-light", in: self)
    }

    private func makeOtpStepViewModel(
        sessionStore: SessionStore,
        clock: ManualClock
    ) async throws -> RegisterViewModel {
        let vm = RegisterViewModel(sessionStore: sessionStore, clock: clock)
        vm.fullName = "Trần Minh Anh"
        vm.username = "minhanh.tran"
        vm.email = "student.demo@thi360.vn"
        vm.password = "StrongPass1!"
        vm.acceptTerms = true
        await vm.submitRegistration()
        guard case .otp = vm.state else {
            XCTFail("Expected transition to OTP step")
            return vm
        }
        return vm
    }
}

private actor ResendSequence {
    private var count = 0
    func next() -> Int {
        count += 1
        return count
    }
}
