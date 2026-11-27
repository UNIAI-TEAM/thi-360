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

        let prepareStates: [(name: String, state: PrepareScreenState, verify: (PrepareViewModel, PrepareView) -> Void)] = [
            (
                "prepare-free",
                PrepareScreenState(
                    load: .loaded(value: freeResp, stale: false),
                    selection: .empty,
                    gate: StartGate.resolve(response: freeResp, selection: .empty)
                ),
                { vm, view in
                    XCTAssertNotNil(vm.loadedResponse)
                    XCTAssertNil(view.renderedError)
                    XCTAssertEqual(vm.state.gate, .open)
                    XCTAssertTrue(view.showsStartButton)
                }
            ),
            (
                "prepare-per-part",
                PrepareScreenState(
                    load: .loaded(value: perPartResp, stale: false),
                    selection: .empty,
                    gate: StartGate.resolve(response: perPartResp, selection: .empty)
                ),
                { vm, view in
                    XCTAssertNotNil(vm.loadedResponse)
                    XCTAssertNil(view.renderedError)
                    XCTAssertEqual(vm.state.gate, .open)
                    XCTAssertTrue(view.showsStartButton)
                }
            ),
            (
                "prepare-shared",
                PrepareScreenState(
                    load: .loaded(value: sharedResp, stale: false),
                    selection: .empty,
                    gate: StartGate.resolve(response: sharedResp, selection: .empty)
                ),
                { vm, view in
                    XCTAssertNotNil(vm.loadedResponse)
                    XCTAssertNil(view.renderedError)
                    XCTAssertEqual(vm.state.gate, .open)
                    XCTAssertTrue(view.showsStartButton)
                }
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
                ),
                { vm, view in
                    XCTAssertNotNil(vm.loadedResponse)
                    XCTAssertNil(view.renderedError)
                    XCTAssertTrue(view.showsPreChoice)
                    XCTAssertEqual(vm.state.selection.optionCode, "SCIENCE")
                    XCTAssertEqual(view.renderedPreChoice?.subjectProgress, "Đã chọn 1/2")
                    XCTAssertEqual(vm.state.gate, .needSubjects(count: 2))
                }
            ),
            (
                "prepare-exhausted",
                PrepareScreenState(
                    load: .loaded(value: exhaustedResp, stale: false),
                    selection: .empty,
                    gate: StartGate.resolve(response: exhaustedResp, selection: .empty)
                ),
                { vm, view in
                    XCTAssertNotNil(vm.loadedResponse)
                    XCTAssertNil(view.renderedError)
                    XCTAssertEqual(view.renderedAction?.exhaustedTitle, "Bạn đã dùng hết lượt của bài này.")
                    XCTAssertEqual(view.renderedAction?.showsBuyMore, true)
                }
            ),
            (
                "prepare-open-in-progress",
                PrepareScreenState(
                    load: .loaded(value: openInProgressResp, stale: false),
                    selection: .empty,
                    gate: StartGate.resolve(response: openInProgressResp, selection: .empty)
                ),
                { vm, view in
                    XCTAssertNotNil(vm.loadedResponse)
                    XCTAssertNil(view.renderedError)
                    XCTAssertFalse(view.showsPreChoice)
                    XCTAssertNotNil(view.renderedAction?.inProgressBanner)
                    XCTAssertEqual(view.renderedAction?.continueTitle, "Tiếp tục")
                }
            ),
            (
                "prepare-error",
                PrepareScreenState(
                    load: .failed(kind: .server, canRetry: true, retryAfter: nil)
                ),
                { vm, view in
                    XCTAssertNil(vm.loadedResponse)
                    XCTAssertEqual(view.renderedError?.title, "Không tải được thông tin chuẩn bị bài thi")
                    XCTAssertEqual(view.renderedError?.retryTitle, "Thử lại")
                }
            )
        ]

        var lightPNGsByViewport: [String: [String: Data]] = [:]
        var darkPNGsByViewport: [String: [String: Data]] = [:]

        for entry in prepareStates {
            let vm = PrepareViewModel(
                entitlementId: "ent-snap",
                api: stack.api,
                initialState: entry.state
            )
            let makePrepareView = {
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
            for viewport in viewports {
                let (lightPNG, darkPNG) = try assertLightAndDarkSnapshots(
                    prefix: "\(entry.name)-\(viewport.name)",
                    viewport: viewport,
                    verifyAfterHost: {
                        entry.verify(vm, makePrepareView())
                    },
                    content: makePrepareView
                )
                lightPNGsByViewport[viewport.name, default: [:]][entry.name] = lightPNG
                darkPNGsByViewport[viewport.name, default: [:]][entry.name] = darkPNG
            }
        }

        for viewport in viewports {
            let lightSet = Set(lightPNGsByViewport[viewport.name]?.values.map { $0 } ?? [])
            XCTAssertEqual(
                lightSet.count,
                prepareStates.count,
                "All \(prepareStates.count) light Prepare snapshots at \(viewport.name) must be visually distinct"
            )
            let darkSet = Set(darkPNGsByViewport[viewport.name]?.values.map { $0 } ?? [])
            XCTAssertEqual(
                darkSet.count,
                prepareStates.count,
                "All \(prepareStates.count) dark Prepare snapshots at \(viewport.name) must be visually distinct"
            )
        }
        XCTAssertEqual(
            stack.transport.requests(method: .get, path: "/api/v1/me/entitlements/ent-snap/prepare").count,
            0,
            "Seeded PrepareViewModel snapshots must not issue network loads on appear"
        )

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

        var waitingLightByViewport: [String: [String: Data]] = [:]
        var waitingDarkByViewport: [String: [String: Data]] = [:]

        for variant in waitingVariants {
            for viewport in viewports {
                let makeWaitingView = {
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
                let (lightPNG, darkPNG) = try assertLightAndDarkSnapshots(
                    prefix: "\(variant.name)-\(viewport.name)",
                    viewport: viewport,
                    verifyAfterHost: {
                        XCTAssertEqual(makeWaitingView().rendered.phase, variant.phase)
                    },
                    content: makeWaitingView
                )
                waitingLightByViewport[viewport.name, default: [:]][variant.name] = lightPNG
                waitingDarkByViewport[viewport.name, default: [:]][variant.name] = darkPNG
            }
        }

        for viewport in viewports {
            XCTAssertEqual(
                Set(waitingLightByViewport[viewport.name]?.values.map { $0 } ?? []).count,
                waitingVariants.count,
                "All waiting light snapshots at \(viewport.name) must be visually distinct"
            )
            XCTAssertEqual(
                Set(waitingDarkByViewport[viewport.name]?.values.map { $0 } ?? []).count,
                waitingVariants.count,
                "All waiting dark snapshots at \(viewport.name) must be visually distinct"
            )
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
        var prepareDTPNGs: [Data] = []
        for scale in scales {
            let makePrepareDT = {
                PrepareView(
                    viewModel: vm,
                    locale: .vi,
                    usesNavigationBarChrome: false,
                    onOpenAttempt: { _ in },
                    onOpenCheckout: { _ in },
                    onOpenMyExams: {}
                )
            }
            let png = try auditDynamicType(
                name: "prepare-\(scale.suffix)",
                size: phoneSize,
                uiCategory: scale.uiCategory,
                dynamicType: scale.dynamicType,
                verifyAfterHost: {
                    XCTAssertNotNil(vm.loadedResponse)
                    XCTAssertNil(makePrepareDT().renderedError)
                    XCTAssertTrue(makePrepareDT().showsPreChoice)
                },
                content: makePrepareDT
            )
            prepareDTPNGs.append(png)

            _ = try auditDynamicType(
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

            _ = try auditDynamicType(
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
        XCTAssertEqual(Set(prepareDTPNGs).count, scales.count, "prepare-dt130 and prepare-dt200 must differ visually")
        XCTAssertEqual(
            stack.transport.requests(method: .get, path: "/api/v1/me/entitlements/ent-dt/prepare").count,
            0,
            "Dynamic Type Prepare audit must not overwrite seeded state with network loads"
        )
    }

    @discardableResult
    private func auditDynamicType<Content: View>(
        name: String,
        size: CGSize,
        uiCategory: UIContentSizeCategory,
        dynamicType: DynamicTypeSize,
        verifyAfterHost: () -> Void = {},
        @ViewBuilder content: () -> Content
    ) throws -> Data {
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

        verifyAfterHost()

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
        return png
    }

    @discardableResult
    private func assertLightAndDarkSnapshots<Content: View>(
        prefix: String,
        viewport: Viewport,
        verifyAfterHost: () -> Void = {},
        @ViewBuilder content: () -> Content
    ) throws -> (light: Data, dark: Data) {
        let lightPNG = try captureSnapshot(
            name: "\(prefix)-light",
            viewport: viewport,
            style: .light,
            verifyAfterHost: verifyAfterHost,
            content: content()
        )
        let darkPNG = try captureSnapshot(
            name: "\(prefix)-dark",
            viewport: viewport,
            style: .dark,
            verifyAfterHost: verifyAfterHost,
            content: content()
        )
        XCTAssertNotEqual(lightPNG, darkPNG, "Light and dark snapshots for \(prefix) must differ visually")
        return (lightPNG, darkPNG)
    }

    private func captureSnapshot<Content: View>(
        name: String,
        viewport: Viewport,
        style: UIUserInterfaceStyle,
        verifyAfterHost: () -> Void,
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

        verifyAfterHost()

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
