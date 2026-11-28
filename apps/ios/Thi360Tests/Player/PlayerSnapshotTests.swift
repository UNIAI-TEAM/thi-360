import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class PlayerSnapshotTests: XCTestCase {
    private struct Viewport {
        let name: String
        let size: CGSize
        let sizeClass: UIUserInterfaceSizeClass
        let idiom: UIUserInterfaceIdiom
    }

    private let viewports: [Viewport] = [
        Viewport(name: "iphone-390x844", size: CGSize(width: 390, height: 844), sizeClass: .compact, idiom: .phone),
        Viewport(name: "ipad-landscape-1180x820", size: CGSize(width: 1180, height: 820), sizeClass: .regular, idiom: .pad),
        Viewport(name: "ipad-portrait-820x1180", size: CGSize(width: 820, height: 1180), sizeClass: .regular, idiom: .pad),
        Viewport(name: "duo-folded-466x678", size: CGSize(width: 466, height: 678), sizeClass: .compact, idiom: .phone),
        Viewport(name: "duo-open-951x669", size: CGSize(width: 951, height: 669), sizeClass: .regular, idiom: .phone)
    ]

    func testPlayerSurfaceMatrixLightAndDarkAcrossAllViewports() throws {
        let (sharedAttempt, sharedBodies) = try PlayerTestFixtures.decodeAttemptState(variant: "in-progress-shared")
        let (perPartAttempt, perPartBodies) = try PlayerTestFixtures.decodeAttemptState(variant: "in-progress-per-part")
        let (choiceAttempt, choiceBodies) = try PlayerTestFixtures.decodeAttemptState(variant: "awaiting-choice")
        let (genAttempt, _) = try PlayerTestFixtures.decodeAttemptState(variant: "generating")
        let (genFailAttempt, _) = try PlayerTestFixtures.decodeAttemptState(variant: "generation-failed")
        let (closedAttempt, _) = try PlayerTestFixtures.decodeAttemptState(variant: "closed-submitted")
        let preparePerPart = try PlayerTestFixtures.decodePrepare(variant: "per-part")
        let choiceMeta = PlayerTestFixtures.makeMetadata(from: preparePerPart)

        var activeState = EngineState.initial(isOnline: true)
        activeState.load = .loaded(value: (), stale: false)
        activeState.attempt = sharedAttempt
        activeState.session = .editing(sessionId: "sess-snap")
        activeState.clock = Countdown(target: .attempt, remaining: .seconds(1520))
        if let firstPart = sharedAttempt.parts?.first {
            activeState.position = Position(partId: firstPart.partId ?? "", itemId: UnitGrouping.leafItems(in: firstPart).first?.itemId)
        }

        var takeoverState = activeState
        takeoverState.session = .openElsewhere(lastSeenAt: FlexibleISO8601DateTranscoder.parse("2026-11-20T09:50:00Z")!)

        var evictedState = activeState
        evictedState.session = .evicted

        var perPartState = EngineState.initial(isOnline: true)
        perPartState.load = .loaded(value: (), stale: false)
        perPartState.attempt = perPartAttempt
        perPartState.session = .editing(sessionId: "sess-snap-pp")
        perPartState.clock = Countdown(target: .part(perPartAttempt.parts?.first?.partId ?? ""), remaining: .seconds(900))
        if let firstPart = perPartAttempt.parts?.first {
            perPartState.position = Position(partId: firstPart.partId ?? "", itemId: UnitGrouping.leafItems(in: firstPart).first?.itemId)
        }

        var choiceState = EngineState.initial(isOnline: true)
        choiceState.load = .loaded(value: (), stale: false)
        choiceState.attempt = choiceAttempt
        choiceState.session = .editing(sessionId: "sess-snap-ch")
        if let awaiting = choiceAttempt.parts?.first(where: { $0.state == .AWAITING_CHOICE }) {
            choiceState.position = Position(partId: awaiting.partId ?? "", itemId: nil)
            choiceState.clock = Countdown(target: .choice(awaiting.partId ?? ""), remaining: .seconds(420))
        }

        var genState = EngineState.initial(isOnline: true)
        genState.load = .loaded(value: (), stale: false)
        genState.attempt = genAttempt
        genState.session = .editing(sessionId: "sess-snap-gen")
        if let genPart = genAttempt.parts?.first(where: { $0.state == .GENERATING }) {
            genState.position = Position(partId: genPart.partId ?? "", itemId: nil)
        }

        var genFailState = EngineState.initial(isOnline: true)
        genFailState.load = .loaded(value: (), stale: false)
        genFailState.attempt = genFailAttempt
        genFailState.session = .editing(sessionId: "sess-snap-fail")
        if let failPart = genFailAttempt.parts?.first(where: { $0.state == .GENERATION_FAILED }) {
            genFailState.position = Position(partId: failPart.partId ?? "", itemId: nil)
        }

        var closedState = EngineState.initial(isOnline: true)
        closedState.load = .loaded(value: (), stale: false)
        closedState.attempt = closedAttempt
        closedState.closed = .submitted
        closedState.session = .readOnly(.closed(.submitted))

        let scenarios: [(name: String, state: EngineState, bodies: [ItemID: AttemptQuestionBodyValue], local: PlayerLocalUI, meta: LoadState<PartChoiceMetadata>)] = [
            ("player-active", activeState, sharedBodies, PlayerLocalUI(), .loading),
            ("player-part-choice", choiceState, choiceBodies, PlayerLocalUI(), .loaded(value: choiceMeta, stale: false)),
            ("player-generating", genState, [:], PlayerLocalUI(), .loading),
            ("player-generation-failed", genFailState, [:], PlayerLocalUI(), .loading),
            ("player-dialog-takeover", takeoverState, sharedBodies, PlayerLocalUI(), .loading),
            ("player-dialog-finish-part", perPartState, perPartBodies, PlayerLocalUI(dialog: .finishPart), .loading),
            ("player-dialog-submit", activeState, sharedBodies, PlayerLocalUI(dialog: .submit), .loading),
            ("player-closed", closedState, [:], PlayerLocalUI(), .loading),
            ("player-evicted", evictedState, sharedBodies, PlayerLocalUI(), .loading)
        ]

        for viewport in viewports {
            let (lightPNG, darkPNG) = try captureLightAndDark(
                prefix: "player-active-\(viewport.name)",
                viewport: viewport
            ) {
                PlayerView(
                    attemptId: sharedAttempt.attemptId,
                    engineState: activeState,
                    localUI: PlayerLocalUI(),
                    bodies: sharedBodies,
                    locale: .vi,
                    idiomOverride: viewport.idiom
                )
            }
            XCTAssertNotEqual(lightPNG, darkPNG)
        }

        let primaryViewports = [viewports[0], viewports[1]]
        for scenario in scenarios.dropFirst() {
            for viewport in primaryViewports {
                let (lightPNG, darkPNG) = try captureLightAndDark(
                    prefix: "\(scenario.name)-\(viewport.name)",
                    viewport: viewport
                ) {
                    PlayerView(
                        attemptId: scenario.state.attempt?.attemptId ?? "att-snap",
                        engineState: scenario.state,
                        localUI: scenario.local,
                        bodies: scenario.bodies,
                        locale: .vi,
                        partChoiceLoad: scenario.meta,
                        idiomOverride: viewport.idiom
                    )
                }
                XCTAssertNotEqual(lightPNG, darkPNG)
            }
        }
    }

    func testPlayerDynamicType130And200AuditAndSnapshots() throws {
        let (attempt, bodies) = try PlayerTestFixtures.decodeAttemptState(variant: "in-progress-shared")
        var state = EngineState.initial(isOnline: true)
        state.load = .loaded(value: (), stale: false)
        state.attempt = attempt
        state.session = .editing(sessionId: "sess-dt")
        state.clock = Countdown(target: .attempt, remaining: .seconds(1200))
        if let firstPart = attempt.parts?.first {
            state.position = Position(partId: firstPart.partId ?? "", itemId: UnitGrouping.leafItems(in: firstPart).first?.itemId)
        }

        let scales: [(suffix: String, uiCategory: UIContentSizeCategory, dynamicType: DynamicTypeSize)] = [
            ("dt130", .extraExtraExtraLarge, .xxxLarge),
            ("dt200", .accessibilityLarge, .accessibility2)
        ]
        let size = CGSize(width: 390, height: 844)
        var pngs: [Data] = []

        for scale in scales {
            let (win, vc) = PlayerViewTestHost.host(
                PlayerView(
                    attemptId: attempt.attemptId,
                    engineState: state,
                    localUI: PlayerLocalUI(),
                    bodies: bodies,
                    locale: .vi,
                    idiomOverride: .phone
                ),
                size: size,
                style: .light,
                sizeClass: .compact,
                uiCategory: scale.uiCategory,
                dynamicType: scale.dynamicType,
                settleSeconds: 0.1
            )
            defer { win.isHidden = true }

            let nodes = CatalogViewInspector.collectNodes(in: vc.view, window: win)
            for button in nodes.filter({ $0.traits.contains(.button) }) {
                XCTAssertFalse(
                    button.label.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                    "\(scale.suffix) button '\(button.identifier)' missing VoiceOver label"
                )
                guard button.frameInWindow.width > 0 && button.frameInWindow.height > 0 else { continue }
                XCTAssertGreaterThanOrEqual(button.frameInWindow.height, 43.5, "\(scale.suffix) button '\(button.identifier)' height < 44pt")
            }

            let scrollViews = RecoveryViewTestHelper.findViews(ofType: UIScrollView.self, in: vc.view)
            XCTAssertFalse(scrollViews.isEmpty, "\(scale.suffix) player content must be vertically scrollable")

            let (img, png) = try CatalogViewInspector.renderPNG(window: win, size: size)
            XCTAssertGreaterThan(png.count, 2048)
            persistSnapshot(image: img, png: png, name: "player-active-\(scale.suffix)")
            pngs.append(png)
        }

        XCTAssertEqual(Set(pngs).count, scales.count, "DT 130% and DT 200% snapshots must differ visually")
    }

    private func captureLightAndDark<Content: View>(
        prefix: String,
        viewport: Viewport,
        @ViewBuilder content: () -> Content
    ) throws -> (Data, Data) {
        let lightPNG = try capture(name: "\(prefix)-light", viewport: viewport, style: .light, content: content())
        let darkPNG = try capture(name: "\(prefix)-dark", viewport: viewport, style: .dark, content: content())
        return (lightPNG, darkPNG)
    }

    private func capture<Content: View>(
        name: String,
        viewport: Viewport,
        style: UIUserInterfaceStyle,
        content: Content
    ) throws -> Data {
        let (win, _) = PlayerViewTestHost.host(
            content,
            size: viewport.size,
            style: style,
            sizeClass: viewport.sizeClass,
            settleSeconds: 0.08
        )
        defer { win.isHidden = true }
        let (img, png) = try CatalogViewInspector.renderPNG(window: win, size: viewport.size)
        XCTAssertGreaterThan(png.count, 2048, "Snapshot \(name) unexpectedly empty")
        persistSnapshot(image: img, png: png, name: name)
        return png
    }

    private func persistSnapshot(image: UIImage, png: Data, name: String) {
        let attachment = XCTAttachment(image: image)
        attachment.name = "\(name).png"
        attachment.lifetime = .keepAlways
        add(attachment)

        let env = ProcessInfo.processInfo.environment
        let outputDir = [env["TEST_RUNNER_THI360_SNAPSHOT_DIR"], env["THI360_SNAPSHOT_DIR"]]
            .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .first(where: { !$0.isEmpty })

        if let outputDir {
            let dirURL = URL(fileURLWithPath: outputDir, isDirectory: true)
            try? FileManager.default.createDirectory(at: dirURL, withIntermediateDirectories: true)
            try? png.write(to: dirURL.appendingPathComponent("\(name).png"))
        }
    }
}
