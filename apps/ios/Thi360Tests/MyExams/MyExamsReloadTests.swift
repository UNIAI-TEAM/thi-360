import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class MyExamsReloadTests: XCTestCase {
    func testReturningFromTabChildRouteAndAttemptReloadsKeepingExistingWithoutSkeleton() async throws {
        let initialCard = try MyExamsTestFixtures.cloneMyExamCardJSON(templateState: "CAN_START") { item in
            item["entitlementId"] = "ent-initial"
            item["code"] = "EXAM-INIT"
            item["name"] = "Đề ban đầu"
            item["attemptsLeft"] = 3
            item["bundles"] = []
        }
        let updatedCard = try MyExamsTestFixtures.cloneMyExamCardJSON(templateState: "CAN_START") { item in
            item["entitlementId"] = "ent-initial"
            item["code"] = "EXAM-INIT"
            item["name"] = "Đề đã cập nhật"
            item["attemptsLeft"] = 2
            item["bundles"] = []
        }
        let initialData = try MyExamsTestFixtures.makeMyExamsPayload(items: [initialCard])
        let updatedData = try MyExamsTestFixtures.makeMyExamsPayload(items: [updatedCard])
        let reloadGate = TestAsyncGate()

        let stack = try MyExamsTestStackFactory.makeStack { transport in
            transport.enqueue(method: .get, path: "/api/v1/me/exams", handlers: [
                { _ in ContractFakeTransport.jsonResponse(statusCode: 200, data: initialData) },
                { _ in
                    await reloadGate.enterAndWait()
                    return ContractFakeTransport.jsonResponse(statusCode: 200, data: updatedData)
                },
                { _ in ContractFakeTransport.jsonResponse(statusCode: 200, data: updatedData) },
                { _ in ContractFakeTransport.jsonResponse(statusCode: 200, data: updatedData) }
            ])
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let router = AppRouter(initialDestination: .myExams)
        let feed = MyExamsFeed(
            accountId: "acc-1",
            api: stack.myExamsAPI,
            connectivityMonitor: stack.connectivityMonitor,
            clock: stack.clock,
            retrySleeper: RecordingRetrySleeper(),
            initialFullName: "An",
            initialUsername: "an"
        )

        let screen = MyExamsScreen(feed: feed, router: router, locale: .vi, showsBrandHeader: false)
            .environment(\.brandPalette, .defaultLight)
            .environment(\.statusPalette, .light)

        let (window, controller) = CatalogViewInspector.host(screen, settleSeconds: 0.12)
        defer { window.isHidden = true }

        try await MyExamsTestStackFactory.waitUntil(window: window) {
            if case let .loaded(items, _) = feed.loadState { return items.first?.name == "Đề ban đầu" }
            return false
        }
        XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/me/exams"), 1)

        router.switchTab(.home)
        CatalogViewInspector.settle(window: window, seconds: 0.06)
        router.switchTab(.myExams)

        await reloadGate.waitUntilEntered()
        CatalogViewInspector.settle(window: window, seconds: 0.05)

        if case let .loaded(duringWaitItems, stale) = feed.loadState {
            XCTAssertFalse(stale)
            XCTAssertEqual(duringWaitItems.first?.name, "Đề ban đầu", "Existing list must stay visible while reloading")
        } else {
            XCTFail("Reload after tab switch must not reset loadState to .loading skeleton; got \(feed.loadState)")
        }
        let duringWaitNodes = CatalogViewInspector.collectNodes(in: controller.view, window: window)
        XCTAssertFalse(duringWaitNodes.contains(where: { $0.identifier == "myExams.loadingSkeleton" }))

        await reloadGate.release()
        try await MyExamsTestStackFactory.waitUntil(window: window) {
            if case let .loaded(items, _) = feed.loadState { return items.first?.name == "Đề đã cập nhật" }
            return false
        }
        XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/me/exams"), 2)

        router.openCheckout(code: "EXAM-INIT")
        CatalogViewInspector.settle(window: window, seconds: 0.05)
        router.back()
        try await MyExamsTestStackFactory.waitUntil(window: window) {
            stack.transport.callCount(method: .get, path: "/api/v1/me/exams") == 3
        }

        router.openAttempt(attemptId: "att-1")
        CatalogViewInspector.settle(window: window, seconds: 0.05)
        router.closeAttempt()
        try await MyExamsTestStackFactory.waitUntil(window: window) {
            stack.transport.callCount(method: .get, path: "/api/v1/me/exams") == 4
        }
    }
}
