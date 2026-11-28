import Foundation
import XCTest
@testable import Thi360

@MainActor
final class StartContextStoreTests: XCTestCase {
    func testSaveReadAcrossProcessRestartAccountIsolationMoveAndAbortedRetention() async throws {
        let rootDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("thi360-start-context-tests-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: rootDir) }

        let provider1 = AccountScopedStoreProvider(rootDirectoryURL: rootDir)
        let storeA1 = try XCTUnwrap(provider1.store(for: "account-A"))
        let contextStoreA1 = StartContextStore(store: storeA1)

        let context = StartContext(
            entitlementId: "ent-100",
            preChoice: PreparePreChoiceInput(optionCode: "SCIENCE", subjectCodes: ["LY", "HOA"]),
            examTitle: "HSA đầy đủ",
            examKindLabel: "Đánh giá năng lực ĐHQG Hà Nội (HSA)",
            firstPartName: "Toán học và xử lí số liệu",
            firstPartMinutes: 75,
            timing: "PER_PART"
        )
        let saved = try await contextStoreA1.save(context, attemptId: "att-1")
        XCTAssertTrue(saved)

        let raw = await storeA1.read(namespace: "attempt", key: "startContext:att-1")
        XCTAssertNotNil(raw, "Context must be stored under namespace 'attempt' and key 'startContext:{attemptId}'")

        let providerRestarted = AccountScopedStoreProvider(rootDirectoryURL: rootDir)
        let storeA2 = try XCTUnwrap(providerRestarted.store(for: "account-A"))
        let storeB = try XCTUnwrap(providerRestarted.store(for: "account-B"))

        let readAfterRestart = await StartContextStore(store: storeA2).read(attemptId: "att-1")
        XCTAssertEqual(readAfterRestart, context, "StartContext must survive process restart")

        let readByAccountB = await StartContextStore(store: storeB).read(attemptId: "att-1")
        XCTAssertNil(readByAccountB, "Account B must never read Account A's StartContext")

        let moved = try await StartContextStore(store: storeA2).move(from: "att-1", to: "att-2")
        XCTAssertTrue(moved)
        let oldAfterMove = await StartContextStore(store: storeA2).read(attemptId: "att-1")
        let newAfterMove = await StartContextStore(store: storeA2).read(attemptId: "att-2")
        XCTAssertNil(oldAfterMove, "Old startContext key must be removed after retry moves to new attemptId")
        XCTAssertEqual(newAfterMove, context)

        let draftStoreA = DraftStore(store: storeA2)
        try await draftStoreA.deleteStartContext(attemptId: "att-2")
        let afterIOSSaveDelete = await StartContextStore(store: storeA2).read(attemptId: "att-2")
        XCTAssertNil(afterIOSSaveDelete, "IOS-SAVE DraftStore.deleteStartContext must delete the exact StartContextStore key")
    }

    func testStartFlowSavesPerPartAndSharedDurationAndRetainsContextOnAborted() async throws {
        let sharedData = try PrepareTestFixtures.loadIndexedData(
            operationId: Operations.prepare.id,
            status: 200,
            variant: "shared"
        )
        let abortedStartData = try PrepareTestFixtures.makeStartData(
            status: 200,
            variant: "aborted",
            attemptId: "att-aborted-shared"
        )
        let stack = try PrepareTestStackFactory.makeStack(accountId: "account-shared") { transport in
            transport.registerJSON(
                method: .get,
                path: "/api/v1/me/entitlements/ent-shared/prepare",
                data: sharedData
            )
            transport.registerJSON(
                method: .post,
                path: "/api/v1/me/attempts",
                statusCode: 200,
                data: abortedStartData
            )
        }
        defer { PrepareTestStackFactory.cleanup(stack) }

        let vm = PrepareViewModel(
            entitlementId: "ent-shared",
            api: stack.api,
            startContextStore: stack.startContextStore,
            localeProvider: { .vi }
        )
        await vm.loadIfNeeded()
        await vm.startAttemptAndWait()

        XCTAssertEqual(vm.state.waiting, .failed(abortReason: "GENERATION_FAILED"))
        let saved = await stack.startContextStore.read(attemptId: "att-aborted-shared")
        XCTAssertNotNil(saved, "ABORTED attempt must keep its StartContext for retry")
        XCTAssertEqual(saved?.entitlementId, "00000000-0000-7000-8000-000000000001")
        XCTAssertEqual(saved?.examTitle, "H-SCA Toán (bộ dữ liệu)")
        XCTAssertEqual(saved?.examKindLabel, "Đánh giá năng lực H-SCA")
        XCTAssertEqual(saved?.firstPartName, "Phần 1 · Trắc nghiệm")
        XCTAssertEqual(saved?.firstPartMinutes, 90, "SHARED exam must save full exam duration (90)")
        XCTAssertEqual(saved?.timing, "SHARED")
    }

    func testStartFlowSavesFirstPartDurationForPerPartExam() async throws {
        let perPartData = try PrepareTestFixtures.loadIndexedData(
            operationId: Operations.prepare.id,
            status: 200,
            variant: "per-part"
        )
        let startingData = try PrepareTestFixtures.makeStartData(
            status: 202,
            variant: "starting",
            attemptId: "att-per-part-1"
        )
        let clock = VirtualPrepareClock()
        let stack = try PrepareTestStackFactory.makeStack(accountId: "account-per-part") { transport in
            transport.registerJSON(
                method: .get,
                path: "/api/v1/me/entitlements/ent-per-part/prepare",
                data: perPartData
            )
            transport.registerJSON(
                method: .post,
                path: "/api/v1/me/attempts",
                statusCode: 202,
                data: startingData
            )
        }
        defer { PrepareTestStackFactory.cleanup(stack) }

        let vm = PrepareViewModel(
            entitlementId: "ent-per-part",
            api: stack.api,
            startContextStore: stack.startContextStore,
            clock: clock,
            localeProvider: { .vi }
        )
        defer { vm.clear() }
        await vm.loadIfNeeded()
        await vm.startAttemptAndWait()

        let saved = await stack.startContextStore.read(attemptId: "att-per-part-1")
        XCTAssertEqual(saved?.firstPartName, "Toán học và xử lí số liệu")
        XCTAssertEqual(saved?.firstPartMinutes, 5, "PER_PART exam must save first part duration (5), not full 15")
        XCTAssertEqual(saved?.timing, "PER_PART")
    }
}
