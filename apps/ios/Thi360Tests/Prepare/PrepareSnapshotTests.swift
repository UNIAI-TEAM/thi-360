import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class PrepareSnapshotTests: XCTestCase {
    private struct Viewport {
        let name: String
        let size: CGSize
        let sizeClass: UIUserInterfaceSizeClass
        let shellWidth: ShellWidth
    }

    private let viewports: [Viewport] = [
        Viewport(name: "iphone-390x844", size: CGSize(width: 390, height: 844), sizeClass: .compact, shellWidth: .narrow),
        Viewport(name: "ipad-landscape-1180x820", size: CGSize(width: 1180, height: 820), sizeClass: .regular, shellWidth: .twoColumn),
        Viewport(name: "ipad-portrait-820x1180", size: CGSize(width: 820, height: 1180), sizeClass: .regular, shellWidth: .twoColumn),
        Viewport(name: "duo-folded-466x678", size: CGSize(width: 466, height: 678), sizeClass: .compact, shellWidth: .narrow),
        Viewport(name: "duo-unfolded-669x951", size: CGSize(width: 669, height: 951), sizeClass: .regular, shellWidth: .twoColumn)
    ]

    func testPrepareAndWaitingSnapshotMatrixLightAndDark() throws {
        let stack = try PrepareTestStackFactory.makeStack()
        defer { PrepareTestStackFactory.cleanup(stack) }

        let freeResp = try PrepareTestFixtures.loadPrepareResponse(variant: "free-navigation")
        let perPartResp = try PrepareTestFixtures.loadPrepareResponse(variant: "per-part")
        let sharedResp = try PrepareTestFixtures.loadPrepareResponse(variant: "shared")
        let (_, preChoiceResp) = try PrepareTestFixtures.makeSubjectChoicePrepareData(
            preselectedCode: "SCIENCE",
            chooseSubjects: 2,
            includeSecondFixedOption: true
        )
        let (_, exhaustedResp) = try PrepareTestFixtures.makeExhaustedPrepareData(
            source: "PURCHASE",
            servingStatus: "PUBLISHED"
        )
        let openInProgressResp = try PrepareTestFixtures.loadPrepareResponse(variant: "open-in-progress")

        let prepareStates: [(name: String, state: PrepareScreenState)] = [
            (
                "prepare-free",
                PrepareScreenState(
                    load: .loaded(value: freeResp, stale: false),
                    selection: .empty,
                    gate: StartGate.resolve(response: freeResp, selection: .empty)
                )
            ),
            (
                "prepare-per-part",
                PrepareScreenState(
                    load: .loaded(value: perPartResp, stale: false),
                    selection: .empty,
                    gate: StartGate.resolve(response: perPartResp, selection: .empty)
                )
            ),
            (
                "prepare-shared",
                PrepareScreenState(
                    load: .loaded(value: sharedResp, stale: false),
                    selection: .empty,
                    gate: StartGate.resolve(response: sharedResp, selection: .empty)
                )
            ),
            (
                "prepare-pre-choice",
                PrepareScreenState(
                    load: .loaded(value: preChoiceResp, stale: false),
                    selection: PreChoiceSelection(optionCode: "SCIENCE", subjectCodes: ["LY"]),
                    gate: StartGate.resolve(
                        response: preChoiceResp,
                        selection: PreChoiceSelection(optionCode: "SCIENCE", subjectCodes: ["LY"])
                    )
                )
            ),
            (
                "prepare-exhausted",
                PrepareScreenState(
                    load: .loaded(value: exhaustedResp, stale: false),
                    selection: .empty,
                    gate: StartGate.resolve(response: exhaustedResp, selection: .empty)
                )
            ),
            (
                "prepare-open-in-progress",
                PrepareScreenState(
                    load: .loaded(value: openInProgressResp, stale: false),
                    selection: .empty,
                    gate: StartGate.resolve(response: openInProgressResp, selection: .empty)
                )
            ),
            (
                "prepare-error",
                PrepareScreenState(
                    load: .failed(kind: .server, canRetry: true, retryAfter: nil)
                )
            )
        ]

        for entry in prepareStates {
            let vm = PrepareViewModel(
                entitlementId: "ent-snap",
                api: stack.api,
                initialState: entry.state
            )
            for viewport in viewports {
                try assertLightAndDarkSnapshots(prefix: "\(entry.name)-\(viewport.name)", viewport: viewport) {
                    PrepareView(
                        viewModel: vm,
                        locale: .vi,
                        supportContact: SupportContact(phone: "1900 1234", email: "support@thi360.vn", hours: "8h–21h"),
                        usesNavigationBarChrome: false,
                        onOpenAttempt: { _ in },
                        onOpenCheckout: { _ in },
                        onOpenMyExams: {}
                    )
                }
            }
        }

        let waitingContext = StartContext(
            entitlementId: "ent-snap",
            preChoice: nil,
            examTitle: "HSA đầy đủ (thu nhỏ)",
            examKindLabel: "HSA",
            firstPartName: "Toán học và xử lí số liệu",
            firstPartMinutes: 75,
            timing: "PER_PART"
        )

        let waitingVariants: [(name: String, phase: WaitingPhase, abortReason: String?)] = [
            ("waiting-polling", .polling(elapsed: 15), nil),
            ("waiting-busy", .busy, nil),
            ("waiting-failed", .failed(abortReason: "STALE_STARTING"), "STALE_STARTING")
        ]

        for variant in waitingVariants {
            for viewport in viewports {
                try assertLightAndDarkSnapshots(prefix: "\(variant.name)-\(viewport.name)", viewport: viewport) {
                    AttemptWaitingView(
                        attemptId: "att-snap",
                        examTitle: waitingContext.examTitle,
                        context: waitingContext,
                        origin: .prepare,
                        onReady: { _ in },
                        onRestarted: { _ in },
                        onOpenPrepare: { _ in },
                        onMyExams: {},
                        controlledPhase: variant.phase,
                        controlledAbortReason: variant.abortReason,
                        timing: "PER_PART",
                        locale: .vi
                    )
                }
            }
        }
    }

    func testPrepareAndWaitingDynamicType130And200AuditAndSnapshots() throws {
        let stack = try PrepareTestStackFactory.makeStack()
        defer { PrepareTestStackFactory.cleanup(stack) }

        let (_, preChoiceResp) = try PrepareTestFixtures.makeSubjectChoicePrepareData(
            preselectedCode: "SCIENCE",
            chooseSubjects: 2,
            includeSecondFixedOption: true
        )
        let selection = PreChoiceSelection(optionCode: "SCIENCE", subjectCodes: ["LY", "HOA"])
        let vm = PrepareViewModel(
            entitlementId: "ent-dt",
            api: stack.api,
            initialState: PrepareScreenState(
                load: .loaded(value: preChoiceResp, stale: false),
                selection: selection,
                gate: StartGate.resolve(response: preChoiceResp, selection: selection)
            )
        )
        let waitingContext = StartContext(
            entitlementId: "ent-dt",
            preChoice: nil,
            examTitle: "HSA đầy đủ (thu nhỏ)",
            examKindLabel: "HSA",
            firstPartName: "Toán học và xử lí số liệu",
            firstPartMinutes: 75,
            timing: "PER_PART"
        )

        let scales: [(suffix: String, uiCategory: UIContentSizeCategory, dynamicType: DynamicTypeSize)] = [
            ("dt130", .extraExtraExtraLarge, .xxxLarge),
            ("dt200", .accessibilityLarge, .accessibility2)
        ]

        let phoneSize = CGSize(width: 390, height: 844)
        for scale in scales {
            try auditDynamicType(
                name: "prepare-\(scale.suffix)",
                size: phoneSize,
                uiCategory: scale.uiCategory,
                dynamicType: scale.dynamicType
            ) {
                PrepareView(
                    viewModel: vm,
                    locale: .vi,
                    usesNavigationBarChrome: false,
                    onOpenAttempt: { _ in },
                    onOpenCheckout: { _ in },
                    onOpenMyExams: {}
                )
            }

            try auditDynamicType(
                name: "waiting-polling-\(scale.suffix)",
                size: phoneSize,
                uiCategory: scale.uiCategory,
                dynamicType: scale.dynamicType
            ) {
                AttemptWaitingView(
                    attemptId: "att-dt",
                    examTitle: waitingContext.examTitle,
                    context: waitingContext,
                    origin: .prepare,
                    onReady: { _ in },
                    onRestarted: { _ in },
                    onOpenPrepare: { _ in },
                    onMyExams: {},
                    controlledPhase: .polling(elapsed: 10),
                    timing: "PER_PART",
                    locale: .vi
                )
            }

            try auditDynamicType(
                name: "waiting-failed-\(scale.suffix)",
                size: phoneSize,
                uiCategory: scale.uiCategory,
                dynamicType: scale.dynamicType
            ) {
                AttemptWaitingView(
                    attemptId: "att-dt-fail",
                    examTitle: waitingContext.examTitle,
                    context: waitingContext,
                    origin: .prepare,
                    onReady: { _ in },
                    onRestarted: { _ in },
                    onOpenPrepare: { _ in },
                    onMyExams: {},
                    controlledPhase: .failed(abortReason: "STALE_STARTING"),
                    controlledAbortReason: "STALE_STARTING",
                    timing: "PER_PART",
                    locale: .vi
                )
            }
        }
    }

    private func auditDynamicType<Content: View>(
        name: String,
        size: CGSize,
        uiCategory: UIContentSizeCategory,
        dynamicType: DynamicTypeSize,
        @ViewBuilder content: () -> Content
    ) throws {
        let (window, controller) = PrepareViewInspector.host(
            content(),
            size: size,
            style: .light,
            sizeClass: .compact,
            shellWidth: .narrow,
            uiCategory: uiCategory,
            dynamicType: dynamicType,
            settleSeconds: 0.1
        )
        defer { window.isHidden = true }

        let nodes = CatalogViewInspector.collectNodes(in: controller.view, window: window)
        for button in nodes.filter({ $0.traits.contains(.button) }) {
            XCTAssertFalse(
                button.label.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                "\(name) button '\(button.identifier)' must have a VoiceOver label"
            )
            guard button.frameInWindow.width > 0 && button.frameInWindow.height > 0 else { continue }
            XCTAssertGreaterThanOrEqual(button.frameInWindow.width, 43.5, "\(name) button '\(button.identifier)' width < 44pt")
            XCTAssertGreaterThanOrEqual(button.frameInWindow.height, 43.5, "\(name) button '\(button.identifier)' height < 44pt")
        }

        for node in nodes where node.frameInWindow.width > 0 && node.frameInWindow.height > 0 {
            XCTAssertGreaterThanOrEqual(node.frameInWindow.minX, -1.0, "\(name) '\(node.identifier)' overflows left")
            XCTAssertLessThanOrEqual(node.frameInWindow.maxX, size.width + 1.0, "\(name) '\(node.identifier)' overflows right")
        }

        let scrollViews = RecoveryViewTestHelper.findViews(ofType: UIScrollView.self, in: controller.view)
        XCTAssertFalse(scrollViews.isEmpty, "\(name) must be vertically scrollable")
        for scrollView in scrollViews where scrollView.contentSize.width > 0 {
            XCTAssertLessThanOrEqual(scrollView.contentSize.width, size.width + 1.0, "\(name) horizontal scroll overflow")
        }

        let (image, png) = try CatalogViewInspector.renderPNG(window: window, size: size)
        XCTAssertGreaterThan(png.count, 2048, "Dynamic Type snapshot \(name) unexpectedly empty")
        try persistSnapshot(image: image, png: png, name: name)
    }

    private func assertLightAndDarkSnapshots<Content: View>(
        prefix: String,
        viewport: Viewport,
        @ViewBuilder content: () -> Content
    ) throws {
        let lightPNG = try captureSnapshot(name: "\(prefix)-light", viewport: viewport, style: .light, content: content())
        let darkPNG = try captureSnapshot(name: "\(prefix)-dark", viewport: viewport, style: .dark, content: content())
        XCTAssertNotEqual(lightPNG, darkPNG, "Light and dark snapshots for \(prefix) must differ visually")
    }

    private func captureSnapshot<Content: View>(
        name: String,
        viewport: Viewport,
        style: UIUserInterfaceStyle,
        content: Content
    ) throws -> Data {
        let (window, controller) = PrepareViewInspector.host(
            content,
            size: viewport.size,
            style: style,
            sizeClass: viewport.sizeClass,
            shellWidth: viewport.shellWidth,
            settleSeconds: 0.08
        )
        defer { window.isHidden = true }

        let scrollViews = RecoveryViewTestHelper.findViews(ofType: UIScrollView.self, in: controller.view)
        for scrollView in scrollViews where scrollView.contentSize.width > 0 {
            XCTAssertLessThanOrEqual(
                scrollView.contentSize.width,
                viewport.size.width + 1.0,
                "\(name) UIScrollView horizontal overflow \(scrollView.contentSize.width) > \(viewport.size.width)"
            )
        }

        let (image, png) = try CatalogViewInspector.renderPNG(window: window, size: viewport.size)
        XCTAssertGreaterThan(png.count, 2048, "Snapshot \(name) unexpectedly empty")
        try persistSnapshot(image: image, png: png, name: name)
        return png
    }

    private func persistSnapshot(image: UIImage, png: Data, name: String) throws {
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
            try FileManager.default.createDirectory(at: dirURL, withIntermediateDirectories: true)
            try png.write(to: dirURL.appendingPathComponent("\(name).png"))
        }
    }
}
