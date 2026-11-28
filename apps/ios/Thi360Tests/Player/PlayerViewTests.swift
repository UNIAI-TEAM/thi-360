import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class PlayerViewTests: XCTestCase {
    func testLoadStates() async throws {
        let loadingState = EngineState.initial(isOnline: true)
        let loadingUI = PlayerUI.make(state: loadingState, layout: .compact, local: PlayerLocalUI())
        XCTAssertEqual(loadingUI.surface, .loading)

        let (loadWin, loadVC) = PlayerViewTestHost.host(
            PlayerView(attemptId: "att-load", engineState: loadingState, locale: .vi)
        )
        defer { loadWin.isHidden = true }
        XCTAssertNotNil(PlayerViewTestHost.findNode(id: "player.loading", in: loadVC.view, window: loadWin))
        XCTAssertTrue(
            PlayerViewTestHost.allText(in: loadVC.view, window: loadWin)
                .joined(separator: " ")
                .contains(AppStrings.common("attempt.waiting.title", locale: .vi))
        )

        let notFoundHarness = try PlayerTestHarness(autoStart: false)
        defer { notFoundHarness.dispose() }
        notFoundHarness.server.enqueue(
            .getState,
            try .fixture(operationId: Operations.getState.id, status: 404, variant: "attempt-not-found")
        )
        notFoundHarness.engine.start()
        await notFoundHarness.settle()

        let nfUI = PlayerUI.make(state: notFoundHarness.engine.state, layout: .compact, local: PlayerLocalUI())
        XCTAssertEqual(nfUI.surface, .loadError)
        XCTAssertEqual(nfUI.loadFailureKind, .notFound)
        XCTAssertFalse(nfUI.canRetryLoad)

        let (nfWin, nfVC) = PlayerViewTestHost.host(
            PlayerView(attemptId: notFoundHarness.attemptId, engineState: notFoundHarness.engine.state, locale: .vi)
        )
        defer { nfWin.isHidden = true }
        XCTAssertEqual(
            PlayerViewTestHost.findNode(id: "player.error.title", in: nfVC.view, window: nfWin)?.label,
            PlayerStrings.text("attempt.errors.notFoundTitle", locale: .vi)
        )
        XCTAssertNil(PlayerViewTestHost.findNode(id: "player.error.retryButton", in: nfVC.view, window: nfWin))
        XCTAssertNotNil(PlayerViewTestHost.findNode(id: "player.error.backToMyExamsButton", in: nfVC.view, window: nfWin))

        let errorHarness = try PlayerTestHarness(autoStart: false)
        defer { errorHarness.dispose() }
        errorHarness.server.enqueue(.getState, try .serverError(statusCode: 503))
        errorHarness.engine.start()
        await errorHarness.settle()

        let err503UI = PlayerUI.make(state: errorHarness.engine.state, layout: .compact, local: PlayerLocalUI())
        XCTAssertEqual(err503UI.surface, .loadError)
        XCTAssertEqual(err503UI.loadFailureKind, .server)
        XCTAssertTrue(err503UI.canRetryLoad)

        let (errWin, errVC) = PlayerViewTestHost.host(
            PlayerView(attemptId: errorHarness.attemptId, engineState: errorHarness.engine.state, locale: .vi)
        )
        defer { errWin.isHidden = true }
        XCTAssertEqual(
            PlayerViewTestHost.findNode(id: "player.error.title", in: errVC.view, window: errWin)?.label,
            PlayerStrings.text("attempt.errors.loadTitle", locale: .vi)
        )
        XCTAssertNotNil(PlayerViewTestHost.findNode(id: "player.error.retryButton", in: errVC.view, window: errWin))
        XCTAssertNotNil(PlayerViewTestHost.findNode(id: "player.error.backToMyExamsButton", in: errVC.view, window: errWin))

        errorHarness.engine.refresh()
        await errorHarness.settle()
        XCTAssertEqual(PlayerUI.make(state: errorHarness.engine.state, layout: .compact, local: PlayerLocalUI()).surface, .active)

        let netHarness = try PlayerTestHarness(autoStart: false)
        defer { netHarness.dispose() }
        netHarness.server.enqueue(.getState, .networkError(.notConnectedToInternet))
        netHarness.engine.start()
        await netHarness.settle()
        let netUI = PlayerUI.make(state: netHarness.engine.state, layout: .compact, local: PlayerLocalUI())
        XCTAssertEqual(netUI.surface, .loadError)
        XCTAssertEqual(netUI.loadFailureKind, .offline)
        XCTAssertTrue(netUI.canRetryLoad)

        let forbHarness = try PlayerTestHarness(autoStart: false)
        defer { forbHarness.dispose() }
        forbHarness.server.enqueue(
            .getState,
            try .json(statusCode: 403, object: ["code": "FORBIDDEN", "message": "Forbidden", "fieldErrors": [], "traceId": "t403"])
        )
        forbHarness.engine.start()
        await forbHarness.settle()
        let forbUI = PlayerUI.make(state: forbHarness.engine.state, layout: .compact, local: PlayerLocalUI())
        XCTAssertEqual(forbUI.surface, .loadError)
        XCTAssertEqual(forbUI.loadFailureKind, .forbidden)

        let suite = "vn.unicom.thi360.tests.player.chromeless.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let preferences = AppPreferences(userDefaults: defaults)
        let router = AppRouter(initialDestination: .myExams)
        router.openAttempt(attemptId: "att-chromeless")
        defer { PlayerRuntime.shared.release(attemptId: "att-chromeless") }

        let (shellWin, shellVC) = RecoveryViewTestHelper.host(
            AppRootView(router: router, preferences: preferences, idiomOverride: .pad),
            size: CGSize(width: 1180, height: 820),
            sizeClass: .regular,
            themeMode: .light
        )
        defer { shellWin.isHidden = true }
        XCTAssertTrue(RecoveryViewTestHelper.findViews(ofType: UITabBar.self, in: shellVC.view).isEmpty)
        XCTAssertNil(PlayerViewTestHost.findNode(id: "shell.ipad.sidebar", in: shellVC.view, window: shellWin))
        XCTAssertEqual(router.presentedAttempt?.attemptId, "att-chromeless")
    }

    func testClosedScreen() async throws {
        let closedVariants: [(variant: String, expectedHeadingKey: String, expectedReason: ClosedReason, mutate: ((inout [String: Any]) -> Void)?)] = [
            (
                "closed-submitted",
                "attempt.closed.heading",
                .submitted,
                { json in
                    if var parts = json["parts"] as? [[String: Any]], !parts.isEmpty {
                        parts[0]["deadlineAt"] = "2026-11-20T10:05:00Z"
                        json["parts"] = parts
                    }
                }
            ),
            ("closed-time-up", "attempt.closed.headingTimeUp", .timeUp, nil),
            ("closed-choice-timeout", "attempt.closed.headingChoiceTimeout", .choiceTimeout, nil)
        ]

        for entry in closedVariants {
            let harness = try PlayerTestHarness(
                initialStateVariant: entry.variant,
                mutateInitialState: entry.mutate
            )
            defer { harness.dispose() }
            await harness.settle()

            let ui = PlayerUI.make(state: harness.engine.state, layout: .compact, local: PlayerLocalUI())
            XCTAssertEqual(ui.surface, .closed, "Variant \(entry.variant) must show closed screen")
            XCTAssertEqual(ui.closedReason, entry.expectedReason)

            let (win, vc) = PlayerViewTestHost.host(
                PlayerView(attemptId: harness.attemptId, engineState: harness.engine.state, locale: .vi)
            )
            defer { win.isHidden = true }
            XCTAssertEqual(
                PlayerViewTestHost.findNode(id: "player.closed.heading", in: vc.view, window: win)?.label,
                PlayerStrings.text(entry.expectedHeadingKey, locale: .vi)
            )
            XCTAssertNotNil(PlayerViewTestHost.findNode(id: "player.closed.viewResultButton", in: vc.view, window: win))
            XCTAssertNotNil(PlayerViewTestHost.findNode(id: "player.closed.backToMyExamsButton", in: vc.view, window: win))
        }

        let timeUpClaim = try PlayerTestHarness(autoStart: false)
        defer { timeUpClaim.dispose() }
        timeUpClaim.server.enqueue(
            .claimSession,
            try .fixture(operationId: Operations.claimSession.id, status: 409, variant: "time-up")
        )
        timeUpClaim.server.enqueue(
            .getState,
            try .fixture(operationId: Operations.getState.id, status: 200, variant: "in-progress-shared")
        )
        timeUpClaim.server.enqueue(
            .getState,
            try .fixture(operationId: Operations.getState.id, status: 200, variant: "closed-time-up")
        )
        timeUpClaim.engine.start()
        await timeUpClaim.settle()
        let timeUpUI = PlayerUI.make(state: timeUpClaim.engine.state, layout: .compact, local: PlayerLocalUI())
        XCTAssertEqual(timeUpUI.surface, .closed)
        XCTAssertEqual(timeUpUI.closedReason, .timeUp)

        let router = AppRouter(initialDestination: .myExams)
        router.openPrepare(entitlementId: "ent-1")
        router.openAttempt(attemptId: "att-closed-1")
        router.openResultFromAttempt(attemptId: "att-closed-1")
        XCTAssertNil(router.presentedAttempt)
        XCTAssertEqual(router.path(for: .myExams), [.result(attemptId: "att-closed-1")])

        let perPart = try PlayerTestHarness(
            initialStateVariant: "in-progress-per-part",
            mutateInitialState: { json in
                if var parts = json["parts"] as? [[String: Any]], !parts.isEmpty {
                    let soon = FakeAttemptServer.isoString(
                        FlexibleISO8601DateTranscoder.parse("2026-11-20T10:00:02Z")!
                    )
                    parts[0]["deadlineAt"] = soon
                    json["parts"] = parts
                }
            }
        )
        defer { perPart.dispose() }
        await perPart.settle()
        let part0Id = perPart.server.partId(at: 0)
        XCTAssertEqual(perPart.engine.state.position?.partId, part0Id)

        perPart.server.mutateState { json in
            if var parts = json["parts"] as? [[String: Any]], parts.count >= 2 {
                parts[0]["state"] = "CLOSED"
                parts[1]["state"] = "OPEN"
                if let p0Items = parts[0]["items"] as? [[String: Any]], let first = p0Items.first {
                    var cloned = first
                    cloned["itemId"] = "item-part1-after-expiry"
                    cloned["answer"] = NSNull()
                    cloned["answerSeq"] = 0
                    parts[1]["items"] = [cloned]
                }
                json["parts"] = parts
            }
        }
        await perPart.clock.advance(by: .seconds(4))
        let movedToPart1 = await perPart.clock.waitUntil {
            perPart.engine.state.position?.partId == perPart.server.partId(at: 1)
        }
        XCTAssertTrue(movedToPart1)
        await perPart.engine.waitForIdleForTesting()
        let part1Id = perPart.server.partId(at: 1)
        XCTAssertEqual(perPart.engine.state.position?.partId, part1Id)
        XCTAssertEqual(perPart.engine.state.position?.itemId, perPart.server.firstLeafItemId(inPartIndex: 1))
    }

    func testBeforeSession() async throws {
        let harness = try PlayerTestHarness(autoStart: false)
        defer { harness.dispose() }
        harness.server.enqueue(.claimSession, .networkError(.notConnectedToInternet))
        harness.engine.start()
        let sawNetError = await harness.clock.waitUntil {
            harness.engine.state.save == .unsaved(.networkError)
        }
        XCTAssertTrue(sawNetError)

        let beforeUI = PlayerUI.make(
            state: harness.engine.state,
            layout: .compact,
            local: PlayerLocalUI(),
            bodies: harness.bodyStore.bodies(for: harness.attemptId)
        )
        XCTAssertEqual(beforeUI.surface, .active)
        XCTAssertTrue(beforeUI.canEdit, "Must allow answering while session is acquiring")
        XCTAssertEqual(beforeUI.questionMode, .answering)
        XCTAssertFalse(beforeUI.topActionEnabled, "FinishPart/Submit must be locked before session is acquired")
        XCTAssertEqual(beforeUI.saveStatus, .unsaved(.networkError))
        XCTAssertEqual(
            PlayerStrings.saveStatusText(beforeUI.saveStatus, locale: .vi).reason,
            PlayerStrings.text("attempt.saveStatus.reasons.network_error", locale: .vi)
        )

        let firstItem = harness.server.firstLeafItemId()
        harness.engine.answer(itemId: firstItem, value: .singleChoice(optionId: "opt-1"))
        XCTAssertEqual(harness.engine.state.answers[firstItem]?.value?.singleChoiceOptionId, "opt-1")

        harness.server.enqueue(.claimSession, try .serverError(statusCode: 503))
        for _ in 0..<5 where harness.engine.state.save != .unsaved(.serverError) {
            await harness.clock.advance(by: .milliseconds(500))
            await Task.yield()
        }
        let saw5xx = await harness.clock.waitUntil {
            harness.engine.state.save == .unsaved(.serverError)
        }
        XCTAssertTrue(saw5xx)
        let ui5xx = PlayerUI.make(state: harness.engine.state, layout: .compact, local: PlayerLocalUI())
        XCTAssertFalse(ui5xx.topActionEnabled)
        XCTAssertEqual(ui5xx.saveStatus, .unsaved(.serverError))

        harness.connectivity.applyUpdate(false)
        let sawOffline = await harness.clock.waitUntil {
            harness.engine.state.save == .offline
        }
        XCTAssertTrue(sawOffline)
        let uiOffline = PlayerUI.make(state: harness.engine.state, layout: .compact, local: PlayerLocalUI())
        XCTAssertEqual(uiOffline.saveStatus, .offline)
        XCTAssertEqual(
            PlayerStrings.saveStatusText(uiOffline.saveStatus, locale: .vi).reason,
            PlayerStrings.text("attempt.saveStatus.reasons.offline", locale: .vi)
        )

        harness.connectivity.applyUpdate(true)
        await harness.settle(advance: .seconds(5))
        let afterUI = PlayerUI.make(state: harness.engine.state, layout: .compact, local: PlayerLocalUI())
        XCTAssertTrue(afterUI.topActionEnabled, "Submit/FinishPart must unlock after session is claimed")
    }

    func testGrouping() throws {
        // Plan A6 mutation: inject displaySegments and multi-subject componentCodes onto certified in-progress-shared fixture.
        let (segAttempt, segBodies) = try PlayerTestFixtures.decodeAttemptState(variant: "in-progress-shared") { json in
            if var parts = json["parts"] as? [[String: Any]], !parts.isEmpty {
                parts[0]["displaySegments"] = [
                    [
                        "code": "SEG_1",
                        "name": "Phần đọc hiểu",
                        "fromPosition": 1,
                        "toPosition": 2,
                        "commandCount": 2,
                        "maxPoints": 4.0
                    ]
                ]
                json["parts"] = parts
            }
        }
        var segState = EngineState.initial(isOnline: true)
        segState.load = .loaded(value: (), stale: false)
        segState.attempt = segAttempt
        segState.session = .editing(sessionId: "sess-1")
        let segUI = PlayerUI.make(state: segState, layout: .wide, local: PlayerLocalUI(), bodies: segBodies)
        XCTAssertGreaterThanOrEqual(segUI.unitGroups.count, 1)
        if case let .segment(_, code, name, _, _, _, _, isUngrouped) = segUI.unitGroups.first?.header {
            XCTAssertEqual(code, "SEG_1")
            XCTAssertEqual(name, "Phần đọc hiểu")
            XCTAssertFalse(isUngrouped)
        } else {
            XCTFail("Expected segment group header")
        }

        let (win, vc) = PlayerViewTestHost.host(
            PlayerView(attemptId: segAttempt.attemptId, engineState: segState, bodies: segBodies, locale: .vi),
            size: CGSize(width: 1024, height: 768),
            sizeClass: .regular
        )
        defer { win.isHidden = true }
        let texts = PlayerViewTestHost.allText(in: vc.view, window: win).joined(separator: " ")
        XCTAssertTrue(texts.contains("Phần đọc hiểu"))

        let (subjAttempt, subjBodies) = try PlayerTestFixtures.decodeAttemptState(variant: "in-progress-shared") { json in
            if var parts = json["parts"] as? [[String: Any]], !parts.isEmpty,
               var items = parts[0]["items"] as? [[String: Any]], items.count >= 2
            {
                parts[0]["displaySegments"] = []
                items[0]["componentCode"] = "LY"
                items[1]["componentCode"] = "CUSTOM_ASTRO"
                parts[0]["items"] = items
                json["parts"] = parts
            }
        }
        var subjState = EngineState.initial(isOnline: true)
        subjState.load = .loaded(value: (), stale: false)
        subjState.attempt = subjAttempt
        subjState.session = .editing(sessionId: "sess-1")
        let customMeta = PartChoiceMetadata(
            entitlementId: "ent-1",
            choices: [],
            subjectsByCode: [
                "CUSTOM_ASTRO": Components.Schemas.ChoiceSubjectView(
                    code: "CUSTOM_ASTRO",
                    nameEn: "Astrophysics",
                    nameVi: "Thiên văn học"
                )
            ]
        )
        let subjUI = PlayerUI.make(
            state: subjState,
            layout: .wide,
            local: PlayerLocalUI(),
            bodies: subjBodies,
            partChoiceLoad: .loaded(value: customMeta, stale: false)
        )
        XCTAssertGreaterThanOrEqual(subjUI.unitGroups.count, 2)
        XCTAssertEqual(
            SubjectNames.resolve(code: "CUSTOM_ASTRO", prepareSubjects: subjUI.subjectsByCode, locale: .vi),
            "Thiên văn học"
        )
        XCTAssertEqual(
            SubjectNames.resolve(code: "CUSTOM_ASTRO", prepareSubjects: subjUI.subjectsByCode, locale: .en),
            "Astrophysics"
        )
        XCTAssertEqual(
            SubjectNames.resolve(code: "UNKNOWN_XYZ", prepareSubjects: subjUI.subjectsByCode, locale: .vi),
            "UNKNOWN_XYZ"
        )
    }
}
