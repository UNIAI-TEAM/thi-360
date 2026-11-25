import Foundation
import XCTest
@testable import Thi360

@MainActor
final class RecoveryLogRedactionTests: XCTestCase {
    func testDemoLogsNeverExposePasswordsOtpCodesOrResetTokensAcrossAllRecoveryFlows() async throws {
        #if THI360_DEMO
        XCTAssertEqual(AppEnvironment.current.kind, .demo)
        #endif

        let regPassword = "RegDemoSecret!9911"
        let otpCode = "648291"
        let resetToken = "demo-reset-secret-token-7744"
        let resetNewPassword = "ResetDemoSecret!8822"
        let oldPassword = "OldDemoSecret!7733"
        let changedPassword = "ChangedDemoSecret!6644"
        let accessToken = "demo-recovery-access-5511"
        let refreshToken = "demo-recovery-refresh-4422"

        let registerResponse = try ContractFixtureLoader.loadApiFixture("register-200.json")
        let resendResponse = try ContractFixtureLoader.loadApiFixture("register-resend-200.json")
        let forgotResponse = try ContractFixtureLoader.loadApiFixture("password-forgot-202.json")
        let verifyResponse = Data(
            """
            {
              "accessToken": "\(accessToken)",
              "refreshToken": "\(refreshToken)",
              "accessExpiresIn": 300,
              "refreshExpiresIn": 604800,
              "mustChangePassword": false
            }
            """.utf8
        )

        let sink = InMemoryLogSink()
        let (sessionStore, _, _, _) = SessionTestFactory.makeStack(
            environment: .demo,
            logSink: sink
        ) { request in
            if request.path.hasSuffix("/api/v1/auth/register") {
                return MockClientTransport.jsonResponse(statusCode: 200, data: registerResponse)
            }
            if request.path.hasSuffix("/api/v1/auth/register/resend") {
                return MockClientTransport.jsonResponse(statusCode: 200, data: resendResponse)
            }
            if request.path.hasSuffix("/api/v1/auth/mobile/register/verify") {
                return MockClientTransport.jsonResponse(statusCode: 201, data: verifyResponse)
            }
            if request.path.hasSuffix("/api/v1/auth/password/forgot") {
                return MockClientTransport.jsonResponse(statusCode: 202, data: forgotResponse)
            }
            if request.path.hasSuffix("/api/v1/auth/password/reset") {
                return MockClientTransport.emptyResponse(statusCode: 204)
            }
            if request.path.hasSuffix("/api/v1/auth/change-password") {
                return MockClientTransport.emptyResponse(statusCode: 204)
            }
            return MockClientTransport.emptyResponse(statusCode: 500)
        }

        let logger = RedactingAppLogger(environment: .demo, sink: sink)
        let clock = ManualClock()

        let registerVM = RegisterViewModel(sessionStore: sessionStore, clock: clock, logger: logger)
        registerVM.fullName = "Tran Minh Anh"
        registerVM.username = "demo.student"
        registerVM.email = "student.demo@thi360.vn"
        registerVM.password = regPassword
        registerVM.acceptTerms = true
        await registerVM.submitRegistration()

        clock.advance(by: 61)
        registerVM.tick()
        await registerVM.resendOtp()

        registerVM.updateOtpInput(otpCode)
        await registerVM.submitOtp()
        XCTAssertEqual(sessionStore.state, .active)

        let changeVM = NewPasswordViewModel(
            mode: .voluntary(username: "demo.student"),
            sessionStore: sessionStore,
            logger: logger
        )
        changeVM.oldPassword = oldPassword
        changeVM.newPassword = changedPassword
        changeVM.confirmPassword = changedPassword
        await changeVM.submit()
        XCTAssertTrue(changeVM.didComplete)

        let forgotVM = ForgotPasswordViewModel(apiClient: sessionStore.apiClient, clock: clock, logger: logger)
        forgotVM.email = "student.demo@thi360.vn"
        await forgotVM.submit()

        let resetVM = NewPasswordViewModel(
            mode: .fromLink(ResetLink(token: resetToken)),
            sessionStore: sessionStore,
            logger: logger
        )
        resetVM.newPassword = resetNewPassword
        resetVM.confirmPassword = resetNewPassword
        await resetVM.submit()
        XCTAssertTrue(resetVM.didComplete)

        logger.log(.info, category: "recovery", message: #"{"password":"\#(regPassword)","code":"\#(otpCode)","oldPassword":"\#(oldPassword)","newPassword":"\#(changedPassword)","resetToken":"\#(resetToken)"}"#)
        logger.log(.info, category: "recovery", message: "https://thi360.zone17.click/reset-password?token=\(resetToken)&otp=\(otpCode)")
        logger.log(.debug, category: "recovery", message: "debug must be dropped in demo: \(regPassword) \(otpCode) \(resetToken)")

        let entries = sink.recordedEntries
        XCTAssertFalse(entries.isEmpty, "Expected network/recovery logs to be recorded")

        let secrets = [
            regPassword,
            otpCode,
            resetToken,
            resetNewPassword,
            oldPassword,
            changedPassword,
            accessToken,
            refreshToken
        ]
        for entry in entries {
            XCTAssertNotEqual(entry.level, .debug, "Demo build must drop debug logs")
            for secret in secrets {
                XCTAssertFalse(
                    entry.message.contains(secret),
                    "Demo log leaked sensitive value '\(secret)' in entry: \(entry.message)"
                )
            }
        }
    }
}
