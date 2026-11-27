import Foundation
import XCTest
@testable import Thi360

@MainActor
final class PrepareFlowTests: XCTestCase {
    func testStartOutcomes() async throws {
        let perPartData = try PrepareTestFixtures.loadIndexedData(
            operationId: Operations.prepare.id,
            status: 200,
            variant: "per-part"
        )

        do {
            let resumedData = try PrepareTestFixtures.makeStartData(
                status: 200,
                variant: "resumed",
                attemptId: "att-resumed-1"
            )
            let stack = try PrepareTestStackFactory.makeStack { transport in
                transport.registerJSON(method: .get, path: "/api/v1/me/entitlements/ent-1/prepare", data: perPartData)
                transport.registerJSON(method: .post, path: "/api/v1/me/attempts", statusCode: 200, data: resumedData)
            }
            defer { PrepareTestStackFactory.cleanup(stack) }

            let router = AppRouter(initialDestination: .myExams, initialRoute: .prepare(entitlementId: "ent-1"))
            XCTAssertEqual(router.path(for: .myExams), [.prepare(entitlementId: "ent-1")])

            let vm = PrepareViewModel(
                entitlementId: "ent-1",
                api: stack.api,
                startContextStore: stack.startContextStore,
                onOpenAttempt: { attemptId in
                    router.openAttemptAfterStart(attemptId: attemptId)
                }
            )
            await vm.loadIfNeeded()
            await vm.startAttemptAndWait()

            XCTAssertNil(vm.state.waiting)
            XCTAssertEqual(router.presentedAttempt?.attemptId, "att-resumed-1")
            XCTAssertFalse(
                router.path(for: .myExams).contains(.prepare(entitlementId: "ent-1")),
                "Opening attempt after start must pop prepare from navigation stack"
            )
        }

        do {
            let startingData = try PrepareTestFixtures.makeStartData(
                status: 202,
                variant: "starting",
                attemptId: "att-starting-2"
            )
            let clock = VirtualPrepareClock()
            let stack = try PrepareTestStackFactory.makeStack { transport in
                transport.registerJSON(method: .get, path: "/api/v1/me/entitlements/ent-2/prepare", data: perPartData)
                transport.registerJSON(method: .post, path: "/api/v1/me/attempts", statusCode: 202, data: startingData)
            }
            defer { PrepareTestStackFactory.cleanup(stack) }

            var openedAttemptIds: [String] = []
            let vm = PrepareViewModel(
                entitlementId: "ent-2",
                api: stack.api,
                startContextStore: stack.startContextStore,
                clock: clock,
                onOpenAttempt: { openedAttemptIds.append($0) }
            )
            defer { vm.clear() }

            await vm.loadIfNeeded()
            await vm.startAttemptAndWait()

            XCTAssertTrue(openedAttemptIds.isEmpty)
            XCTAssertEqual(vm.state.waitingAttemptId, "att-starting-2")
            if case .polling = vm.state.waiting {} else {
                XCTFail("Expected .polling waiting phase after 202 STARTING, got \(String(describing: vm.state.waiting))")
            }

            let view = PrepareView(
                viewModel: vm,
                locale: .vi,
                usesNavigationBarChrome: false,
                onOpenAttempt: { openedAttemptIds.append($0) },
                onOpenCheckout: { _ in },
                onOpenMyExams: {}
            )
            XCTAssertTrue(view.isShowingWaiting)
            let (window, _) = PrepareViewInspector.host(view)
            let (_, png) = try CatalogViewInspector.renderPNG(window: window, size: CGSize(width: 390, height: 844))
            window.isHidden = true
            XCTAssertGreaterThan(png.count, 2048)
        }

        for (reason, expectedCopy) in [
            ("GENERATION_FAILED", "Chưa tạo được bộ câu hỏi cho đề này."),
            ("FUTURE_UNKNOWN_ABORT_REASON", "Không thể chuẩn bị đề thi lúc này.")
        ] {
            let abortedData = try PrepareTestFixtures.makeStartData(
                status: 200,
                variant: "aborted",
                attemptId: "att-aborted-\(reason)",
                abortReason: reason
            )
            let stack = try PrepareTestStackFactory.makeStack { transport in
                transport.registerJSON(method: .get, path: "/api/v1/me/entitlements/ent-3/prepare", data: perPartData)
                transport.registerJSON(method: .post, path: "/api/v1/me/attempts", statusCode: 200, data: abortedData)
            }
            defer { PrepareTestStackFactory.cleanup(stack) }

            let vm = PrepareViewModel(
                entitlementId: "ent-3",
                api: stack.api,
                startContextStore: stack.startContextStore
            )
            await vm.loadIfNeeded()
            await vm.startAttemptAndWait()

            XCTAssertEqual(vm.state.waiting, .failed(abortReason: reason))
            XCTAssertEqual(vm.state.waitingAttemptId, "att-aborted-\(reason)")

            let waitingView = AttemptWaitingView(
                attemptId: "att-aborted-\(reason)",
                examTitle: vm.loadedResponse?.examName ?? "",
                context: vm.state.waitingContext,
                origin: .prepare,
                onReady: { _ in },
                onRestarted: { _ in },
                onOpenPrepare: { _ in },
                onMyExams: {},
                controlledPhase: vm.state.waiting,
                controlledAbortReason: vm.state.waitingAbortReason,
                locale: .vi
            )
            XCTAssertEqual(waitingView.rendered.failedNotice, "Chuẩn bị đề thất bại")
            XCTAssertEqual(waitingView.rendered.failedHeading, "Chưa chuẩn bị được đề")
            XCTAssertEqual(waitingView.rendered.failedReason, expectedCopy)

            let view = PrepareView(
                viewModel: vm,
                locale: .vi,
                usesNavigationBarChrome: false,
                onOpenAttempt: { _ in },
                onOpenCheckout: { _ in },
                onOpenMyExams: {}
            )
            XCTAssertTrue(view.isShowingWaiting)
            let (window, _) = PrepareViewInspector.host(view)
            let (_, png) = try CatalogViewInspector.renderPNG(window: window, size: CGSize(width: 390, height: 844))
            window.isHidden = true
            XCTAssertGreaterThan(png.count, 2048)
        }
    }
}
