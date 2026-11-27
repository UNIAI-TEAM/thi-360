import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class VerifyEmailAutoSendTests: XCTestCase {
    func testAutoSendRunsOnceAcrossLayoutRotationBackgroundLockUnlockAndReopensAsFreshAttempt() async throws {
        let unverifiedData = try ProfileFixtureHelper.mutateJSON("me-profile-200.json") { dict in
            dict["emailVerified"] = false
        }
        let clock = ManualClock(start: Date(timeIntervalSince1970: 1_760_000_000))
        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens(),
            capability: .faceIDAvailable,
            initialEnrolled: true
        ) { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/profile", statusCode: 200, data: unverifiedData)
            transport.registerEmpty(method: .post, path: "/api/v1/me/email/verification", statusCode: 202)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }
        stack.sessionStore.unlockSession()

        let router = AppRouter(initialDestination: .account)
        let accountVM = router.accountViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager
        )
        router.openProfile()
        router.openProfileVerifyEmail()

        let model = accountVM.verifyEmailModel()
        await model.loadAndAutoSendIfNeeded(locale: .vi)
        XCTAssertTrue(model.autoSendAttempted)
        XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/me/profile"), 1)
        XCTAssertEqual(stack.transport.callCount(method: .post, path: "/api/v1/me/email/verification"), 1)
        XCTAssertEqual(model.screenState, .ready(email: "student.demo@thi360.vn"))
        guard case .counting = model.activeCooldown else {
            return XCTFail("202 auto-send must start 60-second cooldown")
        }

        let view = NavigationStack {
            VerifyEmailView(model: model, preferences: stack.preferences, onBackToProfile: {})
        }
        .environment(\.brandPalette, .defaultLight)
        .environment(\.statusPalette, .light)

        let (window, controller) = RecoveryViewTestHelper.host(
            view,
            size: CGSize(width: 390, height: 844),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { window.isHidden = true }

        window.frame = CGRect(x: 0, y: 0, width: 951, height: 669)
        controller.traitOverrides.horizontalSizeClass = .regular
        window.layoutIfNeeded()
        ProfileFixtureHelper.pumpMainRunLoop(seconds: 0.1)

        _ = stack.biometricManager.handleScenePhaseChange(.background, sessionState: stack.sessionStore.state)
        stack.sessionStore.lockSession()
        stack.sessionStore.unlockSession()
        _ = stack.biometricManager.handleScenePhaseChange(.active, sessionState: stack.sessionStore.state)

        await model.loadAndAutoSendIfNeeded(locale: .vi)
        XCTAssertEqual(
            stack.transport.callCount(method: .post, path: "/api/v1/me/email/verification"),
            1,
            "Layout changes, backgrounding, lock/unlock must never trigger a second auto-send"
        )

        router.back()
        accountVM.clearVerifyEmailModelIfPopped(router: router, destination: .account)
        router.openProfileVerifyEmail()
        let reopenedModel = accountVM.verifyEmailModel()
        XCTAssertFalse(reopenedModel === model)
        await reopenedModel.loadAndAutoSendIfNeeded(locale: .vi)
        XCTAssertEqual(stack.transport.callCount(method: .post, path: "/api/v1/me/email/verification"), 2)
    }

    func testAutoSend429ShortDailyLimitGenericFailureRetryAndAlreadyVerified() async throws {
        let unverifiedData = try ProfileFixtureHelper.mutateJSON("me-profile-200.json") { dict in
            dict["emailVerified"] = false
        }
        let verifiedData = try ContractFixtureLoader.loadApiFixture("me-profile-200.json")
        let short429 = try ContractFixtureLoader.loadApiFixture("me-email-verification-429-rate-limited.json")
        let daily429 = try ProfileFixtureHelper.mutateJSON("me-email-verification-429-rate-limited.json") { dict in
            dict["retryAfter"] = 7_200
        }

        let clock = ManualClock(start: Date(timeIntervalSince1970: 1_760_000_000))
        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        ) { transport in
            transport.enqueue(method: .get, path: "/api/v1/me/profile", handlers: [
                { _ in ContractFakeTransport.emptyResponse(statusCode: 500) },
                { _ in ContractFakeTransport.jsonResponse(statusCode: 200, data: unverifiedData) }
            ])
            transport.registerJSON(method: .post, path: "/api/v1/me/email/verification", statusCode: 429, data: short429)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let service = OpenAPIProfileService(apiClient: stack.sessionStore.apiClient)
        let store = AccountProfileStore(profileService: service, sessionStore: stack.sessionStore)

        let model1 = VerifyEmailModel(profileStore: store, profileService: service, clock: clock)
        await model1.loadAndAutoSendIfNeeded(locale: .vi)
        XCTAssertEqual(model1.screenState, .loadFailed)
        XCTAssertEqual(stack.transport.callCount(method: .post, path: "/api/v1/me/email/verification"), 0)

        await model1.retryLoad(locale: .vi)
        XCTAssertEqual(model1.screenState, .ready(email: "student.demo@thi360.vn"))
        XCTAssertEqual(stack.transport.callCount(method: .post, path: "/api/v1/me/email/verification"), 1)
        guard case .counting = model1.activeCooldown else {
            return XCTFail("429 retryAfter < 3600 must start counting cooldown")
        }

        stack.transport.registerJSON(method: .get, path: "/api/v1/me/profile", statusCode: 200, data: unverifiedData)
        stack.transport.registerJSON(method: .post, path: "/api/v1/me/email/verification", statusCode: 429, data: daily429)
        let model2 = VerifyEmailModel(profileStore: store, profileService: service, clock: clock)
        await model2.loadAndAutoSendIfNeeded(locale: .vi)
        XCTAssertTrue(model2.activeCooldown.isDailyLimit)
        XCTAssertNotNil(model2.dailyLimitUntilDate)

        stack.transport.registerNetworkError(method: .post, path: "/api/v1/me/email/verification", code: .notConnectedToInternet)
        let model3 = VerifyEmailModel(profileStore: store, profileService: service, clock: clock)
        await model3.loadAndAutoSendIfNeeded(locale: .vi)
        XCTAssertEqual(model3.alertKey, "account.verifyEmail.alerts.resendFailed")
        XCTAssertFalse(model3.isCooldownActive, "Resend must remain clickable immediately after generic/offline error")

        stack.transport.resetRecordedRequests()
        stack.transport.registerJSON(method: .get, path: "/api/v1/me/profile", statusCode: 200, data: verifiedData)
        let model4 = VerifyEmailModel(profileStore: store, profileService: service, clock: clock)
        await model4.loadAndAutoSendIfNeeded(locale: .vi)
        XCTAssertEqual(model4.screenState, .alreadyVerified)
        XCTAssertEqual(stack.transport.callCount(method: .post, path: "/api/v1/me/email/verification"), 0)
    }
}
