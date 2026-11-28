import Foundation
import XCTest
@testable import Thi360

@MainActor
final class ResendCodeTests: XCTestCase {
    func testResendCodeClearsErrorsAnnouncesCooldownOnceAtStartAndEndAndHandlesDailyLimitAndFailures() async throws {
        let unverifiedData = try ProfileFixtureHelper.mutateJSON("me-profile-200.json") { dict in
            dict["emailVerified"] = false
        }
        let invalidData = try ContractFixtureLoader.loadApiFixture("me-email-verify-422-email-code-invalid.json")
        let short429 = try ProfileFixtureHelper.mutateJSON("me-email-verification-429-rate-limited.json") { dict in
            dict["retryAfter"] = 45
        }
        let daily429 = try ProfileFixtureHelper.mutateJSON("me-email-verification-429-rate-limited.json") { dict in
            dict["retryAfter"] = 3_600
        }

        let clock = ManualClock(start: Date(timeIntervalSince1970: 1_760_000_000))
        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        ) { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/profile", statusCode: 200, data: unverifiedData)
            transport.registerNetworkError(method: .post, path: "/api/v1/me/email/verification", code: .notConnectedToInternet)
            transport.registerJSON(method: .post, path: "/api/v1/me/email/verify", statusCode: 422, data: invalidData)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        var announcements: [String] = []
        let service = OpenAPIProfileService(apiClient: stack.sessionStore.apiClient)
        let store = AccountProfileStore(profileService: service, sessionStore: stack.sessionStore)
        let model = VerifyEmailModel(
            profileStore: store,
            profileService: service,
            clock: clock,
            announce: { announcements.append($0) }
        )

        await model.loadAndAutoSendIfNeeded(locale: .vi)
        XCTAssertEqual(model.alertKey, "account.verifyEmail.alerts.resendFailed")
        XCTAssertTrue(announcements.isEmpty)

        model.updateCode("123456")
        await model.submitCode(onVerified: {})
        XCTAssertEqual(model.codeErrorKey, "account.verifyEmail.codeInvalid")

        stack.transport.registerEmpty(method: .post, path: "/api/v1/me/email/verification", statusCode: 202)
        await model.resendCode(locale: .vi)
        XCTAssertTrue(model.showCodeResentNotice)
        XCTAssertNil(model.codeErrorKey)
        XCTAssertNil(model.remainingAttempts)
        XCTAssertEqual(announcements.count, 1)
        XCTAssertEqual(announcements.first, "Có thể gửi lại mã sau 01:00.")

        for _ in 0..<5 {
            clock.advance(by: 10)
            model.tickCooldown(locale: .vi)
        }
        XCTAssertEqual(announcements.count, 1, "Intermediate seconds must NOT post VoiceOver announcements")
        XCTAssertEqual(model.cooldownRemainingSeconds, 10)

        clock.advance(by: 10)
        model.tickCooldown(locale: .vi)
        model.tickCooldown(locale: .vi)
        XCTAssertEqual(announcements.count, 2)
        XCTAssertEqual(announcements.last, "Bạn có thể gửi lại mã bây giờ.")
        XCTAssertEqual(model.activeCooldown, .ready)

        stack.transport.registerJSON(method: .post, path: "/api/v1/me/email/verification", statusCode: 429, data: short429)
        await model.resendCode(locale: .vi)
        XCTAssertEqual(model.cooldownRemainingSeconds, 45)
        XCTAssertTrue(model.isCooldownActive)

        clock.advance(by: 46)
        model.tickCooldown(locale: .vi)
        stack.transport.registerJSON(method: .post, path: "/api/v1/me/email/verification", statusCode: 429, data: daily429)
        await model.resendCode(locale: .vi)
        XCTAssertTrue(model.activeCooldown.isDailyLimit)
        XCTAssertNotNil(model.dailyLimitUntilDate)

        clock.advance(by: 3_601)
        model.tickCooldown(locale: .vi)
        stack.transport.registerEmpty(method: .post, path: "/api/v1/me/email/verification", statusCode: 500)
        await model.resendCode(locale: .vi)
        XCTAssertEqual(model.alertKey, "account.verifyEmail.alerts.resendFailed")
        XCTAssertEqual(
            ProfileStrings.text("account.verifyEmail.alerts.resendFailed", locale: .vi),
            "Không gửi được mã mới. Vui lòng thử lại."
        )
    }

    func testLeavingProfileDuringCooldownUnlocksSendAndAppliesServer429RetryAfterOnReentry() async throws {
        let profileData = try ContractFixtureLoader.loadApiFixture("me-profile-200.json")
        let server429 = try ProfileFixtureHelper.mutateJSON("me-email-verification-429-rate-limited.json") { dict in
            dict["retryAfter"] = 42
        }
        let clock = ManualClock(start: Date(timeIntervalSince1970: 1_760_000_000))
        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        ) { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/profile", statusCode: 200, data: profileData)
            transport.registerEmpty(method: .post, path: "/api/v1/me/email/change", statusCode: 202)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let router = AppRouter(initialDestination: .account)
        let accountVM = AccountViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager,
            clock: clock
        )
        await accountVM.loadProfile()

        router.openProfile()
        let firstVM = accountVM.ensureProfileViewModel()
        firstVM.toggleChangeEmailForm()
        firstVM.updateNewEmail("next@thi360.vn")
        firstVM.updateCurrentPassword("Password123!")
        await firstVM.submitEmailChange(locale: .vi)
        XCTAssertTrue(firstVM.isCooldownActive)
        XCTAssertEqual(firstVM.cooldownRemainingSeconds, 60)

        router.back()
        accountVM.clearProfileViewModelIfPopped(router: router, destination: .account)
        XCTAssertNil(accountVM.existingProfileViewModel())

        clock.advance(by: 18)
        stack.transport.registerJSON(method: .post, path: "/api/v1/me/email/change", statusCode: 429, data: server429)

        router.openProfile()
        let reopenedVM = accountVM.ensureProfileViewModel()
        XCTAssertFalse(reopenedVM.isCooldownActive, "Reopening Profile after exit must unlock cooldown so tap hits server 429")
        reopenedVM.resendPendingEmailCode(locale: .vi)
        XCTAssertTrue(reopenedVM.isShowingChangeEmailForm)
        XCTAssertEqual(reopenedVM.newEmail, "next@thi360.vn")
        reopenedVM.updateCurrentPassword("Password123!")
        await reopenedVM.submitEmailChange(locale: .vi)
        XCTAssertEqual(stack.transport.callCount(method: .post, path: "/api/v1/me/email/change"), 2)
        XCTAssertTrue(reopenedVM.isCooldownActive)
        XCTAssertEqual(reopenedVM.cooldownRemainingSeconds, 42)

        accountVM.isInTwoPaneLayout = true
        accountVM.selectDetail(.orders)
        XCTAssertNil(accountVM.existingProfileViewModel(), "Switching 2-col pane away from Profile must clear cooldown")
        accountVM.selectDetail(.profile)
        let twoPaneReopened = accountVM.ensureProfileViewModel()
        XCTAssertFalse(twoPaneReopened.isCooldownActive)
        twoPaneReopened.toggleChangeEmailForm()
        twoPaneReopened.updateNewEmail("again@thi360.vn")
        twoPaneReopened.updateCurrentPassword("Password123!")
        await twoPaneReopened.submitEmailChange(locale: .vi)
        XCTAssertEqual(twoPaneReopened.cooldownRemainingSeconds, 42)

        accountVM.handleAccountTabExitInTwoPane()
        XCTAssertNil(accountVM.existingProfileViewModel(), "Switching tabs away from Account in 2-col must clear Profile VM")
    }
}
