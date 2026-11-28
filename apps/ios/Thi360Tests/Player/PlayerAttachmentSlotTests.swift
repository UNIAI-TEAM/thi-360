import Foundation
import SwiftUI
import XCTest
@testable import Thi360

@MainActor
private final class SpyAttachmentHost: PlayerAttachmentHost {
    private(set) var receivedContexts: [PlayerAttachmentContext] = []

    func attachmentsView(for context: PlayerAttachmentContext) -> AnyView {
        receivedContexts.append(context)
        return AnyView(
            Text("Attachments:\(context.item.itemId ?? ""):\(context.childItems.count)")
                .playerProbe("spy.attachments.\(context.item.itemId ?? "")", label: "attachments")
        )
    }

    func onBeforeClose(_ target: CloseTarget) {}
}

@MainActor
final class PlayerAttachmentSlotTests: XCTestCase {
    func testQuestionSetEmptyParentWithChildAudioAndPdfAcrossCompactPadAndDuoOpen() throws {
        // Plan A6 mutation: build a QUESTION_SET on attempt-50 with empty parent attachments, child PDF, and child audio/* to verify R-127/R-132/ĐT 512/ĐT 727 and D-9 hosting.
        let (attempt, bodies) = try decodeQuestionSetWithChildAttachments()
        let part = try XCTUnwrap(attempt.parts?.first)
        let items = part.items ?? []
        let parentItem = try XCTUnwrap(items.first(where: { $0.questionType == .QUESTION_SET }))
        let parentId = try XCTUnwrap(parentItem.itemId)
        let children = items.filter { $0.parentItemId == parentId }
        XCTAssertGreaterThanOrEqual(children.count, 2)
        let firstChildId = try XCTUnwrap(children[0].itemId)

        XCTAssertTrue(PlayerAttachmentContext.hasQuestionSetAttachments(parent: parentItem, children: children))

        var state = EngineState.initial(isOnline: true)
        state.load = .loaded(value: (), stale: false)
        state.attempt = attempt
        state.session = .editing(sessionId: "sess-att")
        state.position = Position(partId: part.partId ?? "", itemId: firstChildId)
        state.clock = Countdown(target: .attempt, remaining: .seconds(1200))

        let configs: [(name: String, size: CGSize, sizeClass: UIUserInterfaceSizeClass, idiom: UIUserInterfaceIdiom)] = [
            ("compact", CGSize(width: 390, height: 844), .compact, .phone),
            ("iPadRegular", CGSize(width: 1180, height: 820), .regular, .pad),
            ("phoneDuoOpen", CGSize(width: 951, height: 669), .regular, .phone)
        ]

        for cfg in configs {
            let spy = SpyAttachmentHost()
            let (win, vc) = PlayerViewTestHost.host(
                PlayerView(
                    attemptId: attempt.attemptId,
                    engineState: state,
                    localUI: PlayerLocalUI(),
                    bodies: bodies,
                    locale: .vi,
                    idiomOverride: cfg.idiom
                ),
                size: cfg.size,
                sizeClass: cfg.sizeClass,
                attachmentHost: spy,
                settleSeconds: 0.1
            )
            defer { win.isHidden = true }

            let contextsForParent = spy.receivedContexts.filter { $0.item.itemId == parentId }
            let contextsForChildren = spy.receivedContexts.filter { ctx in
                children.contains { $0.itemId == ctx.item.itemId }
            }
            XCTAssertFalse(
                contextsForParent.isEmpty,
                "\(cfg.name): attachmentHost must be called on QUESTION_SET parent when child carries audio/*"
            )
            XCTAssertEqual(
                contextsForParent.first?.childItems.count,
                children.count,
                "\(cfg.name): PlayerAttachmentContext must forward all childItems for audio hoisting/dedup"
            )
            XCTAssertEqual(
                contextsForParent.first?.canInteract,
                true,
                "\(cfg.name): editable set must allow attachment interaction"
            )
            XCTAssertEqual(
                contextsForParent.first?.isLocked,
                false,
                "\(cfg.name): editable set must not be locked"
            )
            XCTAssertTrue(
                contextsForChildren.isEmpty,
                "\(cfg.name): individual child cards must NEVER invoke attachmentsView (got \(contextsForChildren.count))"
            )

            if cfg.name == "phoneDuoOpen" {
                XCTAssertNotNil(PlayerViewTestHost.findNode(id: "player.duo.leftPane", in: vc.view, window: win))
                XCTAssertNotNil(PlayerViewTestHost.findNode(id: "player.duo.passagePane", in: vc.view, window: win))
                XCTAssertNotNil(PlayerViewTestHost.findNode(id: "player.duo.rightPane", in: vc.view, window: win))
            }
        }

        for child in children {
            if let cid = child.itemId {
                state.answers[cid] = LocalAnswer(value: nil, seq: 1, flagged: false, sync: .locked)
            }
        }
        let lockedSpy = SpyAttachmentHost()
        let (lockedWin, _) = PlayerViewTestHost.host(
            PlayerView(
                attemptId: attempt.attemptId,
                engineState: state,
                localUI: PlayerLocalUI(),
                bodies: bodies,
                locale: .vi,
                idiomOverride: .phone
            ),
            size: CGSize(width: 951, height: 669),
            sizeClass: .regular,
            attachmentHost: lockedSpy,
            settleSeconds: 0.1
        )
        defer { lockedWin.isHidden = true }

        let lockedCtx = try XCTUnwrap(lockedSpy.receivedContexts.first(where: { $0.item.itemId == parentId }))
        XCTAssertFalse(lockedCtx.canInteract, "DuoPassagePane must disable attachment interaction when all child items are .locked")
        XCTAssertTrue(lockedCtx.isLocked, "DuoPassagePane must propagate item-level .locked state")
    }

    private func decodeQuestionSetWithChildAttachments() throws -> (
        Components.Schemas.AttemptStateResponse,
        [ItemID: AttemptQuestionBodyValue]
    ) {
        let rawData = try ContractFixtureLoader.loadData(relativePath: "rich-text/attempt-50.json")
        var json = try XCTUnwrap(JSONSerialization.jsonObject(with: rawData) as? [String: Any])
        var parts = try XCTUnwrap(json["parts"] as? [[String: Any]])
        var items = try XCTUnwrap(parts[0]["items"] as? [[String: Any]])
        let parentIdx = try XCTUnwrap(items.firstIndex(where: { ($0["questionType"] as? String) == "QUESTION_SET" }))
        let parentId = try XCTUnwrap(items[parentIdx]["itemId"] as? String)
        items[parentIdx]["attachments"] = []

        var childIndices: [Int] = []
        for idx in items.indices where (items[idx]["parentItemId"] as? String) == parentId {
            childIndices.append(idx)
        }
        XCTAssertGreaterThanOrEqual(childIndices.count, 2)
        items[childIndices[0]]["attachments"] = [
            [
                "assetId": "00000000-0000-7000-8000-00000000a001",
                "filename": "child-doc.pdf",
                "mime": "application/pdf",
                "sizeBytes": 12000
            ]
        ]
        items[childIndices[1]]["attachments"] = [
            [
                "assetId": "00000000-0000-7000-8000-00000000a002",
                "filename": "child-track.mp3",
                "mime": "audio/mpeg",
                "sizeBytes": 48000
            ]
        ]
        let subset = [items[parentIdx]] + childIndices.map { items[$0] }
        parts[0]["items"] = subset
        json["parts"] = parts
        let mutatedData = try JSONSerialization.data(withJSONObject: json)
        let decoded = try MobileAPIClient.makeJSONDecoder().decode(
            Components.Schemas.AttemptStateResponse.self,
            from: mutatedData
        )
        let bodies = QuestionItemMapper.extractBodies(fromAttemptStateData: mutatedData)
        return (decoded, bodies)
    }
}
