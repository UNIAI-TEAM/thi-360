import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class ResetLinkRoutingTests: XCTestCase {
    func testValidResetLinkParsesAndRoutesViaOnOpenURLAndNSUserActivityIncludingLockedState() {
        let env = AppEnvironment.current
        let webHost = env.webBaseURL.host ?? "localhost"
        let validURL = URL(string: "https://\(webHost)/reset-password?token=abc-reset-token-123")!

        let parsed = ResetLink.parse(validURL, environment: env)
        XCTAssertEqual(parsed?.token, "abc-reset-token-123")

        let (lockedSession, _, _, preferences) = SessionTestFactory.makeStack(
            initialTokens: SessionTokens(
                accessToken: "locked-access",
                refreshToken: "locked-refresh",
                accessExpiresIn: 300,
                refreshExpiresIn: 604_800,
                mustChangePassword: false,
                username: "locked.student"
            )
        ) { _ in
            MockClientTransport.emptyResponse(statusCode: 500)
        }

        let lockedOverrideSession = SessionStore(
            tokenStore: lockedSession.tokenStore,
            apiClient: lockedSession.apiClient,
            initialStateOverride: .locked,
            skipsLaunchValidation: true
        )
        XCTAssertEqual(lockedOverrideSession.state, .locked)

        let router = AppRouter()
        let handledURL = router.handleIncomingURL(validURL, environment: env)
        XCTAssertTrue(handledURL, "Valid /reset-password URL must route to reset screen")
        XCTAssertEqual(router.activeResetLink?.token, "abc-reset-token-123")

        let rootView = AppRootView(
            router: router,
            preferences: preferences,
            sessionStore: lockedOverrideSession,
            idiomOverride: .phone
        )
        let (window, controller) = RecoveryViewTestHelper.host(
            rootView,
            size: CGSize(width: 390, height: 844),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { window.isHidden = true }

        let textFields = RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: controller.view)
        XCTAssertEqual(
            textFields.count,
            2,
            "Opening reset link while locked must bypass lock screen and display new + confirm password fields"
        )

        router.clearRecoveryOverlay()
        XCTAssertNil(router.activeResetLink)

        let activity = NSUserActivity(activityType: NSUserActivityTypeBrowsingWeb)
        activity.webpageURL = URL(string: "https://\(webHost)/reset-password?token=ua-token-456")!
        let handledActivity = router.handleUserActivity(activity, environment: env)
        XCTAssertTrue(handledActivity, "NSUserActivityTypeBrowsingWeb with /reset-password must route to reset screen")
        XCTAssertEqual(router.activeResetLink?.token, "ua-token-456")
    }

    func testOtherPathsForeignHostsAndInvalidLinksAreIgnored() {
        let demoEnv = AppEnvironment(
            kind: .demo,
            apiBaseURL: URL(string: "https://thi360.zone17.click")!,
            webBaseURL: URL(string: "https://thi360.zone17.click")!
        )
        let router = AppRouter()

        let rejectedURLs = [
            "https://thi360.zone17.click/login?token=abc",
            "https://thi360.zone17.click/home?token=abc",
            "https://thi360.zone17.click/reset-password/extra?token=abc",
            "https://thi360.zone17.click/reset-password",
            "https://thi360.zone17.click/reset-password?token=",
            "https://thi360.zone17.click/reset-password?token=%20%20",
            "https://evil.example.com/reset-password?token=abc",
            "http://thi360.zone17.click/reset-password?token=abc",
            "thi360://reset-password?token=abc"
        ]

        for raw in rejectedURLs {
            let url = URL(string: raw)!
            XCTAssertNil(ResetLink.parse(url, environment: demoEnv), "Must reject URL: \(raw)")
            XCTAssertFalse(router.handleIncomingURL(url, environment: demoEnv), "Router must ignore URL: \(raw)")
            XCTAssertNil(router.activeResetLink)
        }

        let wrongActivity = NSUserActivity(activityType: "vn.unicom.thi360.other")
        wrongActivity.webpageURL = URL(string: "https://thi360.zone17.click/reset-password?token=abc")!
        XCTAssertFalse(router.handleUserActivity(wrongActivity, environment: demoEnv))
        XCTAssertNil(router.activeResetLink)
    }

    func testResetLinkLaunchArgumentHonoredInDevAndStrictlyIgnoredInDemo() {
        let devEnv = AppEnvironment(
            kind: .dev,
            apiBaseURL: URL(string: "http://localhost:8080")!,
            webBaseURL: URL(string: "http://localhost:3000")!
        )
        let demoEnv = AppEnvironment(
            kind: .demo,
            apiBaseURL: URL(string: "https://thi360.zone17.click")!,
            webBaseURL: URL(string: "https://thi360.zone17.click")!
        )

        let devArgs = ["-resetLinkURL", "https://localhost/reset-password?token=dev-launch-token"]
        let devRouter = AppRouter.makeForCurrentProcess(arguments: devArgs, environment: devEnv)
        XCTAssertEqual(devRouter.activeResetLink?.token, "dev-launch-token")

        let demoArgs = ["-resetLinkURL", "https://thi360.zone17.click/reset-password?token=demo-launch-token"]
        XCTAssertNil(
            ResetLink.fromLaunchArguments(demoArgs, environment: demoEnv),
            "Demo build must strictly ignore -resetLinkURL launch argument"
        )
        let demoRouter = AppRouter.makeForCurrentProcess(arguments: demoArgs, environment: demoEnv)
        XCTAssertNil(demoRouter.activeResetLink, "Demo AppRouter must never honor -resetLinkURL")

        #if THI360_DEMO
        XCTAssertEqual(AppEnvironment.current.kind, .demo)
        XCTAssertNil(ResetLink.fromLaunchArguments(demoArgs, environment: .current))
        let activeDemoRouter = AppRouter.makeForCurrentProcess(arguments: demoArgs, environment: .current)
        XCTAssertNil(activeDemoRouter.activeResetLink)
        #endif
    }
}
