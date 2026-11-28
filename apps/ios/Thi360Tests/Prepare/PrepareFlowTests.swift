import Foundation
import HTTPTypes
import OpenAPIRuntime
import SwiftUI
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

    func testPrepareViewModelRegistryEvictionAndSessionIdentity() async throws {
        defer { PrepareViewModelRegistry.shared.clearAll() }

        let (exhaustedData, _) = try PrepareTestFixtures.makeExhaustedPrepareData(
            source: "PURCHASE",
            servingStatus: "PUBLISHED"
        )
        let replenishedData = try PrepareTestFixtures.loadIndexedData(
            operationId: Operations.prepare.id,
            status: 200,
            variant: "per-part"
        )

        do {
            let callCounter = LockedCounter()
            let stack = try PrepareTestStackFactory.makeStack { transport in
                transport.register(method: .get, path: "/api/v1/me/entitlements/ent-buy/prepare") { _ in
                    let call = callCounter.increment()
                    var headers = HTTPFields()
                    headers[.contentType] = "application/json"
                    let body = (call == 1) ? exhaustedData : replenishedData
                    return (HTTPResponse(status: .ok, headerFields: headers), HTTPBody(body))
                }
            }
            defer { PrepareTestStackFactory.cleanup(stack) }

            let sessionStore = SessionStore(
                tokenStore: stack.tokenStore,
                apiClient: stack.mobileClient,
                storeProvider: stack.storeProvider,
                initialStateOverride: .active,
                skipsLaunchValidation: true
            )
            let router = AppRouter(initialDestination: .myExams, initialRoute: .prepare(entitlementId: "ent-buy"))

            let firstRouteView = PrepareRoutes.prepare(
                entitlementId: "ent-buy",
                destination: .myExams,
                router: router,
                preferences: stack.preferences
            )
            .environment(\.sessionStore, sessionStore)

            let (window1, _) = await PrepareViewInspector.hostAsync(firstRouteView, settleNanoseconds: 180_000_000)
            let vm1 = PrepareViewModelRegistry.shared.viewModel(
                for: router,
                destination: .myExams,
                entitlementId: "ent-buy",
                sessionStore: sessionStore,
                localeProvider: { .vi },
                onOpenAttempt: { _ in }
            )
            XCTAssertEqual(stack.transport.requests(method: .get, path: "/api/v1/me/entitlements/ent-buy/prepare").count, 1)
            XCTAssertEqual(vm1.state.gate, .exhausted(.published, canBuyMore: true), "Initial prepare route must display exhausted state")
            let (_, exhaustedPNG) = try CatalogViewInspector.renderPNG(window: window1, size: CGSize(width: 390, height: 844))

            router.openCheckout(code: "HSA-01")
            window1.isHidden = true
            window1.rootViewController = nil
            try? await Task.sleep(nanoseconds: 60_000_000)

            router.switchTab(.myExams, reload: true)
            try? await Task.sleep(nanoseconds: 60_000_000)

            router.openPrepare(entitlementId: "ent-buy")
            let secondRouteView = PrepareRoutes.prepare(
                entitlementId: "ent-buy",
                destination: .myExams,
                router: router,
                preferences: stack.preferences
            )
            .environment(\.sessionStore, sessionStore)

            let (window2, _) = await PrepareViewInspector.hostAsync(secondRouteView, settleNanoseconds: 180_000_000)
            defer { window2.isHidden = true }
            let vm2 = PrepareViewModelRegistry.shared.viewModel(
                for: router,
                destination: .myExams,
                entitlementId: "ent-buy",
                sessionStore: sessionStore,
                localeProvider: { .vi },
                onOpenAttempt: { _ in }
            )
            XCTAssertFalse(vm2 === vm1, "Off-screen stack clear after checkout must evict cached PrepareViewModel")
            XCTAssertEqual(
                stack.transport.requests(method: .get, path: "/api/v1/me/entitlements/ent-buy/prepare").count,
                2,
                "Re-opening prepare after checkout -> switchTab(.myExams, reload: true) must evict stale VM and re-fetch GET /prepare"
            )
            XCTAssertEqual(vm2.state.gate, .open, "Replenished prepare screen must unlock Start gate")
            let (_, replenishedPNG) = try CatalogViewInspector.renderPNG(window: window2, size: CGSize(width: 390, height: 844))
            XCTAssertNotEqual(exhaustedPNG, replenishedPNG)

            router.openResultFromAttempt(attemptId: "att-finished")
            try? await Task.sleep(nanoseconds: 60_000_000)
            router.openPrepare(entitlementId: "ent-buy")
            let vmAfterResult = PrepareViewModelRegistry.shared.viewModel(
                for: router,
                destination: .myExams,
                entitlementId: "ent-buy",
                sessionStore: sessionStore,
                localeProvider: { .vi },
                onOpenAttempt: { _ in }
            )
            XCTAssertFalse(vmAfterResult === vm2, "openResultFromAttempt clearing prepare from stack must evict cached VM")
        }

        do {
            let stack = try PrepareTestStackFactory.makeStack(
                accountId: nil,
                username: "student.alpha",
                refreshToken: "refresh-alpha"
            )
            defer { PrepareTestStackFactory.cleanup(stack) }

            let sessionStore = SessionStore(
                tokenStore: stack.tokenStore,
                apiClient: stack.mobileClient,
                storeProvider: stack.storeProvider,
                initialStateOverride: .active,
                skipsLaunchValidation: true
            )
            let router = AppRouter(initialDestination: .myExams, initialRoute: .prepare(entitlementId: "ent-id"))

            let vmInitial = PrepareViewModelRegistry.shared.viewModel(
                for: router,
                destination: .myExams,
                entitlementId: "ent-id",
                sessionStore: sessionStore,
                localeProvider: { .vi },
                onOpenAttempt: { _ in }
            )

            try stack.tokenStore.write(
                SessionTokens(
                    accessToken: "a1",
                    refreshToken: "refresh-alpha",
                    accessExpiresIn: 900,
                    refreshExpiresIn: 86_400,
                    mustChangePassword: false,
                    username: "STUDENT.ALPHA",
                    accountId: "acc-alpha"
                )
            )
            let vmAfterBackfill = PrepareViewModelRegistry.shared.viewModel(
                for: router,
                destination: .myExams,
                entitlementId: "ent-id",
                sessionStore: sessionStore,
                localeProvider: { .vi },
                onOpenAttempt: { _ in }
            )
            XCTAssertTrue(vmAfterBackfill === vmInitial, "nil -> non-nil accountId backfill for the same user must preserve active VM")

            router.resetForSessionReplaced()
            try? await Task.sleep(nanoseconds: 60_000_000)
            router.openPrepare(entitlementId: "ent-id")
            let vmAfterSessionReplace = PrepareViewModelRegistry.shared.viewModel(
                for: router,
                destination: .myExams,
                entitlementId: "ent-id",
                sessionStore: sessionStore,
                localeProvider: { .vi },
                onOpenAttempt: { _ in }
            )
            XCTAssertFalse(vmAfterSessionReplace === vmInitial, "resetForSessionReplaced() must evict off-screen cached VM")

            let stackNil = try PrepareTestStackFactory.makeStack(
                accountId: nil,
                username: "user.one",
                refreshToken: "refresh-1"
            )
            defer { PrepareTestStackFactory.cleanup(stackNil) }
            let nilSessionStore = SessionStore(
                tokenStore: stackNil.tokenStore,
                apiClient: stackNil.mobileClient,
                storeProvider: stackNil.storeProvider,
                initialStateOverride: .active,
                skipsLaunchValidation: true
            )
            let routerNil = AppRouter(initialDestination: .myExams, initialRoute: .prepare(entitlementId: "ent-nil"))
            let vmUserOne = PrepareViewModelRegistry.shared.viewModel(
                for: routerNil,
                destination: .myExams,
                entitlementId: "ent-nil",
                sessionStore: nilSessionStore,
                localeProvider: { .vi },
                onOpenAttempt: { _ in }
            )

            try stackNil.tokenStore.write(
                SessionTokens(
                    accessToken: "a2",
                    refreshToken: "refresh-2",
                    accessExpiresIn: 900,
                    refreshExpiresIn: 86_400,
                    mustChangePassword: false,
                    username: "user.two",
                    accountId: nil
                )
            )
            let vmUserTwo = PrepareViewModelRegistry.shared.viewModel(
                for: routerNil,
                destination: .myExams,
                entitlementId: "ent-nil",
                sessionStore: nilSessionStore,
                localeProvider: { .vi },
                onOpenAttempt: { _ in }
            )
            XCTAssertFalse(vmUserTwo === vmUserOne, "Consecutive nil-accountId users must NOT share a cached PrepareViewModel")
        }
    }
}

private final class LockedCounter: @unchecked Sendable {
    private let lock = NSLock()
    private var count = 0

    func increment() -> Int {
        lock.lock()
        defer { lock.unlock() }
        count += 1
        return count
    }
}
