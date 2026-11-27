import Foundation
import SwiftUI
import XCTest
@testable import Thi360

@MainActor
final class LockOnPortalRoutesTests: XCTestCase {
    func testDoesNotLockBeforeFiveMinutesAndLocksAtFiveMinutesPreservingSubRouteAndAttemptState() {
        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens(),
            capability: .faceIDAvailable,
            initialEnrolled: true,
            lockTimeout: LockPolicy.defaultTimeoutSeconds
        )
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        stack.sessionStore.unlockSession()
        let router = AppRouter(initialDestination: .myExams)
        router.openPrepare(entitlementId: "ent-1")
        router.routeStateStore.setDraftText("draft before lock", for: .myExams, route: .prepare(entitlementId: "ent-1"))
        router.openAttempt(attemptId: "att-99")
        router.routeStateStore.setDraftText("attempt note", for: .myExams, route: .attempt(attemptId: "att-99"))

        XCTAssertFalse(stack.biometricManager.handleScenePhaseChange(.background, sessionState: stack.sessionStore.state))
        stack.clock.advance(by: 299)
        let shouldLockAt299 = stack.biometricManager.handleScenePhaseChange(.active, sessionState: stack.sessionStore.state)
        XCTAssertFalse(shouldLockAt299, "4m59s in background must not lock")
        XCTAssertEqual(stack.sessionStore.state, .active)

        XCTAssertFalse(stack.biometricManager.handleScenePhaseChange(.background, sessionState: stack.sessionStore.state))
        stack.clock.advance(by: 300)
        let shouldLockAt300 = stack.biometricManager.handleScenePhaseChange(.active, sessionState: stack.sessionStore.state)
        XCTAssertTrue(shouldLockAt300, "5m00s in background must lock")
        stack.sessionStore.lockSession()
        XCTAssertEqual(stack.sessionStore.state, .locked)

        stack.sessionStore.unlockSession()
        XCTAssertEqual(stack.sessionStore.state, .active)
        XCTAssertEqual(router.presentedAttempt, AttemptRef(attemptId: "att-99", originDestination: .myExams))
        XCTAssertEqual(router.path(for: .myExams), [.prepare(entitlementId: "ent-1")])
        XCTAssertEqual(
            router.routeStateStore.state(for: .myExams, route: .prepare(entitlementId: "ent-1")).draftText,
            "draft before lock"
        )
        XCTAssertEqual(
            router.routeStateStore.state(for: .myExams, route: .attempt(attemptId: "att-99")).draftText,
            "attempt note"
        )
    }
}
