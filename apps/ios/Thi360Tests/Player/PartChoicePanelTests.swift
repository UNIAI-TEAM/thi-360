import Foundation
import XCTest
@testable import Thi360

@MainActor
final class PartChoiceOptionsLoaderTests: XCTestCase {
    func testReadsStartContextFirstThenFallsBackToMyExamsAndPrepareAndHandlesFailure() async throws {
        let dir = FileManager.default.temporaryDirectory
            .appendingPathComponent("thi360-choice-loader-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: dir) }

        let accountStore = try AccountScopedStore(accountId: "acc-loader", rootDirectoryURL: dir)
        let startStore = StartContextStore(store: accountStore)
        let prepareResp = try PlayerTestFixtures.decodePrepare(variant: "per-part")
        let attemptId = "00000000-0000-7000-8000-000000000055"

        _ = try await startStore.save(
            StartContext(entitlementId: "ent-from-ctx", examTitle: "HSA", examKindLabel: "HSA"),
            attemptId: attemptId
        )
        let api1 = MockPartChoiceMetadataAPI()
        api1.prepareResult = .success(prepareResp)
        let loader1 = PartChoiceOptionsLoader(startContextStore: startStore, api: api1)

        let res1 = await loader1.load(attemptId: attemptId)
        guard case let .loaded(meta1, _) = res1 else {
            return XCTFail("Expected loaded metadata from StartContext")
        }
        XCTAssertEqual(meta1.entitlementId, "ent-from-ctx")
        XCTAssertEqual(api1.myExamsCallCount, 0, "Must skip GET /me/exams when StartContextStore has entitlementId")
        XCTAssertEqual(api1.prepareCallIds, ["ent-from-ctx"])

        let otherAttemptId = "00000000-0000-7000-8000-000000000056"
        let cards = try PlayerTestFixtures.decodeMyExamsCards { json in
            if var items = json["items"] as? [[String: Any]], !items.isEmpty {
                items[0]["entitlementId"] = "ent-from-myexams"
                items[0]["activeAttemptId"] = otherAttemptId
                json["items"] = items
            }
        }
        let api2 = MockPartChoiceMetadataAPI()
        api2.myExamsResult = .success(cards)
        api2.prepareResult = .success(prepareResp)
        let loader2 = PartChoiceOptionsLoader(startContextStore: startStore, api: api2)

        let res2 = await loader2.load(attemptId: otherAttemptId)
        guard case let .loaded(meta2, _) = res2 else {
            return XCTFail("Expected loaded metadata via MyExams fallback")
        }
        XCTAssertEqual(meta2.entitlementId, "ent-from-myexams")
        XCTAssertEqual(api2.myExamsCallCount, 1)
        XCTAssertEqual(api2.prepareCallIds, ["ent-from-myexams"])

        let api3 = MockPartChoiceMetadataAPI()
        api3.myExamsResult = .failure(MobileApiError.network(.notConnectedToInternet))
        let loader3 = PartChoiceOptionsLoader(startContextStore: nil, api: api3)
        let res3 = await loader3.load(attemptId: otherAttemptId)
        if case let .failed(kind, canRetry, _) = res3 {
            XCTAssertEqual(kind, .offline)
            XCTAssertTrue(canRetry)
        } else {
            XCTFail("Expected failed load state on error")
        }
    }
}

@MainActor
final class PartChoicePanelTests: XCTestCase {
    func testOptionsSubjectLimitCountdownUrgencyExpiryErrorCodesAndLoadFailureRefusal() async throws {
        let harness = try PlayerTestHarness(
            initialStateVariant: "awaiting-choice",
            mutateInitialState: { json in
                if var parts = json["parts"] as? [[String: Any]] {
                    for i in parts.indices where parts[i]["state"] as? String == "AWAITING_CHOICE" {
                        parts[i]["choiceDeadlineAt"] = FakeAttemptServer.isoString(
                            FlexibleISO8601DateTranscoder.parse("2026-11-20T10:00:25Z")!
                        )
                    }
                    json["parts"] = parts
                }
            }
        )
        defer { harness.dispose() }
        await harness.settle(advance: .zero)

        let failedUI = PlayerUI.make(
            state: harness.engine.state,
            layout: .compact,
            local: PlayerLocalUI(),
            partChoiceLoad: .failed(kind: .server, canRetry: true, retryAfter: nil)
        )
        let failedPanel = try XCTUnwrap(failedUI.choicePanel)
        XCTAssertTrue(failedPanel.isTimerVisible, "Choice countdown must keep running even when metadata fails to load")
        XCTAssertTrue(failedPanel.isUrgent, "Remaining <= 30s must be urgent danger state")

        let (failWin, failVC) = PlayerViewTestHost.host(
            PartChoicePanel(
                panel: failedPanel,
                locale: .vi,
                onSelectOption: { _ in },
                onToggleSubject: { _ in },
                onConfirm: {},
                onRetryLoadOptions: {},
                onBackToMyExams: {}
            )
        )
        defer { failWin.isHidden = true }
        XCTAssertEqual(
            PlayerViewTestHost.findNode(id: "player.partChoice.errorLoadTitle", in: failVC.view, window: failWin)?.label,
            AppStrings.common("prepare.errorLoad.title", locale: .vi)
        )
        XCTAssertNotNil(PlayerViewTestHost.findNode(id: "player.partChoice.retryLoad", in: failVC.view, window: failWin))
        XCTAssertNotNil(PlayerViewTestHost.findNode(id: "player.partChoice.backToMyExams", in: failVC.view, window: failWin))
        XCTAssertNotNil(PlayerViewTestHost.findNode(id: "player.partChoice.deadlineWarning", in: failVC.view, window: failWin))
        XCTAssertEqual(harness.server.callCount(for: .confirmPartChoice), 0)

        let prepareResp = try PlayerTestFixtures.decodePrepare(variant: "per-part") { json in
            if var after = json["afterStartChoices"] as? [[String: Any]], !after.isEmpty,
               var opts = after[0]["options"] as? [[String: Any]], !opts.isEmpty
            {
                opts[0]["chooseSubjects"] = 2
                opts[0]["countsByPick"] = [17, 17]
                after[0]["options"] = opts
                json["afterStartChoices"] = after
            }
        }
        let meta = PlayerTestFixtures.makeMetadata(from: prepareResp)
        let awaitingPart = try XCTUnwrap(harness.engine.state.attempt?.parts?.first(where: { $0.state == .AWAITING_CHOICE }))
        let partId = try XCTUnwrap(awaitingPart.partId)
        let firstOpt = try XCTUnwrap(meta.choice(for: awaitingPart)?.options?.first)
        let optCode = try XCTUnwrap(firstOpt.code)
        let subjCodes = (firstOpt.subjects ?? []).compactMap(\.code)
        XCTAssertGreaterThanOrEqual(subjCodes.count, 3)

        let localOne = PlayerLocalUI(
            choiceDraft: PartChoiceDraft(optionCode: optCode, subjectCodes: [subjCodes[0]]),
            choiceDraftPartId: partId
        )
        let uiOne = PlayerUI.make(
            state: harness.engine.state,
            layout: .compact,
            local: localOne,
            partChoiceLoad: .loaded(value: meta, stale: false)
        )
        XCTAssertFalse(try XCTUnwrap(uiOne.choicePanel?.canConfirm))

        let localTwo = PlayerLocalUI(
            choiceDraft: PartChoiceDraft(optionCode: optCode, subjectCodes: [subjCodes[0], subjCodes[1]]),
            choiceDraftPartId: partId
        )
        let uiTwo = PlayerUI.make(
            state: harness.engine.state,
            layout: .compact,
            local: localTwo,
            partChoiceLoad: .loaded(value: meta, stale: false)
        )
        let panelTwo = try XCTUnwrap(uiTwo.choicePanel)
        XCTAssertTrue(panelTwo.canConfirm)

        let (twoWin, twoVC) = PlayerViewTestHost.host(
            PartChoicePanel(
                panel: panelTwo,
                locale: .vi,
                onSelectOption: { _ in },
                onToggleSubject: { _ in },
                onConfirm: {},
                onRetryLoadOptions: {},
                onBackToMyExams: {}
            )
        )
        defer { twoWin.isHidden = true }
        XCTAssertNotNil(PlayerViewTestHost.findNode(id: "player.partChoice.limitReached", in: twoVC.view, window: twoWin))
        XCTAssertEqual(
            PlayerViewTestHost.findNode(id: "player.partChoice.subject.\(subjCodes[2])", in: twoVC.view, window: twoWin)?.isEnabled,
            false
        )
        XCTAssertNotNil(PlayerViewTestHost.findNode(id: "player.partChoice.option.\(optCode).counts", in: twoVC.view, window: twoWin))

        let singleOptPrepare = try PlayerTestFixtures.decodePrepare(variant: "per-part") { json in
            if var after = json["afterStartChoices"] as? [[String: Any]], !after.isEmpty,
               let opts = after[0]["options"] as? [[String: Any]], let first = opts.first
            {
                var fixed = first
                fixed["chooseSubjects"] = 0
                after[0]["options"] = [fixed]
                json["afterStartChoices"] = after
            }
        }
        let singleMeta = PlayerTestFixtures.makeMetadata(from: singleOptPrepare)
        let singleUI = PlayerUI.make(
            state: harness.engine.state,
            layout: .compact,
            local: PlayerLocalUI(),
            partChoiceLoad: .loaded(value: singleMeta, stale: false)
        )
        XCTAssertEqual(singleUI.choicePanel?.selectedOptionCode, optCode)
        XCTAssertEqual(singleUI.choicePanel?.canConfirm, true)

        XCTAssertEqual(
            PlayerStrings.partChoiceErrorMessage(code: "CHOICE_WINDOW_CLOSED", locale: .vi),
            PlayerStrings.text("attempt.partChoice.errors.CHOICE_WINDOW_CLOSED", locale: .vi)
        )
        XCTAssertEqual(
            PlayerStrings.partChoiceErrorMessage(code: "SUBJECTS_INVALID", locale: .vi),
            PlayerStrings.text("attempt.partChoice.errors.SUBJECTS_INVALID", locale: .vi)
        )
        XCTAssertEqual(
            PlayerStrings.partChoiceErrorMessage(code: "UNKNOWN_SERVER_CODE", locale: .vi),
            PlayerStrings.text("attempt.partChoice.errors.generic", locale: .vi)
        )

        await harness.clock.advance(by: .seconds(30))
        await harness.engine.waitForIdleForTesting()
        let expiredUI = PlayerUI.make(
            state: harness.engine.state,
            layout: .compact,
            local: localTwo,
            partChoiceLoad: .loaded(value: meta, stale: false)
        )
        XCTAssertEqual(expiredUI.choicePanel?.isExpired, true)
        XCTAssertEqual(expiredUI.choicePanel?.canConfirm, false)
    }
}
