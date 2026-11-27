import Foundation
import XCTest
@testable import Thi360

@MainActor
final class AttemptLocalDataTests: XCTestCase {
    // AC-13: closed/submitted removes draft, position, sessionId, AND startContext:{attemptId};
    // ABORTED removes draft, position, sessionId while KEEPING startContext:{attemptId} across restart.
    func testCleanupOnClose() async throws {
        // Case 1: Submitted / closed removes all 4 keys including startContext:{attemptId}
        do {
            let clock = TestEngineClock()
            let (_, store, dir) = try AttemptEngineTestHarness.makeStore(accountId: "account-A")
            defer { try? FileManager.default.removeItem(at: dir) }
            let (connectivity, _) = AttemptEngineTestHarness.makeConnectivity()
            let server = try FakeAttemptServer(initialStateVariant: "in-progress-shared", clock: clock)
            let itemId = server.firstLeafItemId()
            let partId = server.partId(at: 0)

            // Pre-seed startContext:{attemptId} (written by IOS-PREP per B1.12)
            try await store.write(
                Data(#"{"entitlementId":"ent-1"}"#.utf8),
                namespace: "attempt",
                key: "startContext:\(server.attemptId)"
            )

            let engine = AttemptEngine(
                attemptId: server.attemptId,
                accountId: "account-A",
                api: server.makeAttemptAPI(),
                store: store,
                clock: clock,
                connectivity: connectivity
            )
            defer { engine.dispose() }
            let active1 = await clock.waitUntil { engine.state.activeSessionId != nil }
            XCTAssertTrue(active1)

            connectivity.applyUpdate(false)
            await clock.settle()
            engine.answer(itemId: itemId, value: .singleChoice(optionId: "a"))
            engine.position(partId: partId, itemId: itemId)
            await engine.waitForIdleForTesting()
            let draftBefore = await store.read(namespace: "attempt", key: "draft:\(server.attemptId):\(itemId)")
            let posBefore = await store.read(namespace: "attempt", key: "position:\(server.attemptId)")
            let sessBefore = await store.read(namespace: "attempt", key: "session:\(server.attemptId)")
            let ctxBefore = await store.read(namespace: "attempt", key: "startContext:\(server.attemptId)")
            XCTAssertNotNil(draftBefore)
            XCTAssertNotNil(posBefore)
            XCTAssertNotNil(sessBefore)
            XCTAssertNotNil(ctxBefore)

            connectivity.applyUpdate(true)
            await clock.settle()
            engine.submit()
            let closedSubmitted = await clock.waitUntil { engine.state.closed == .submitted }
            XCTAssertTrue(closedSubmitted)
            await engine.waitForIdleForTesting()

            let draftAfter = await store.read(namespace: "attempt", key: "draft:\(server.attemptId):\(itemId)")
            let posAfter = await store.read(namespace: "attempt", key: "position:\(server.attemptId)")
            let sessAfter = await store.read(namespace: "attempt", key: "session:\(server.attemptId)")
            let ctxAfter = await store.read(namespace: "attempt", key: "startContext:\(server.attemptId)")
            XCTAssertNil(draftAfter)
            XCTAssertNil(posAfter)
            XCTAssertNil(sessAfter)
            XCTAssertNil(ctxAfter)
        }

        // Case 2: ABORTED deletes draft, position, sessionId, and KEEPS startContext:{attemptId} even after process restart!
        do {
            let clock = TestEngineClock()
            let (_, store, dir) = try AttemptEngineTestHarness.makeStore(accountId: "account-A")
            defer { try? FileManager.default.removeItem(at: dir) }
            let (connectivity, _) = AttemptEngineTestHarness.makeConnectivity()
            let server = try FakeAttemptServer(initialStateVariant: "aborted", clock: clock)
            let draftStore = DraftStore(store: store)

            try await draftStore.saveDraft(
                ItemDraftRecord(itemId: "item-1", seq: 1, answer: .singleChoice(optionId: "a"), flagged: false),
                attemptId: server.attemptId
            )
            try await draftStore.writePosition(Position(partId: "part-1", itemId: "item-1"), attemptId: server.attemptId)
            try await draftStore.writeSessionId("sess-aborted", attemptId: server.attemptId)
            let startContextPayload = Data(#"{"entitlementId":"ent-aborted-1"}"#.utf8)
            try await store.write(startContextPayload, namespace: "attempt", key: "startContext:\(server.attemptId)")

            let engine = AttemptEngine(
                attemptId: server.attemptId,
                accountId: "account-A",
                api: server.makeAttemptAPI(),
                store: store,
                clock: clock,
                connectivity: connectivity
            )
            defer { engine.dispose() }
            let abortedClosed = await clock.waitUntil {
                if case .aborted = engine.state.closed { return true }
                return false
            }
            XCTAssertTrue(abortedClosed)
            await engine.waitForIdleForTesting()

            // Re-open AccountScopedStore from same root directory (simulating process restart)
            let restartedProvider = AccountScopedStoreProvider(rootDirectoryURL: dir)
            let restartedStore = try XCTUnwrap(restartedProvider.store(for: "account-A"))

            let abortedDraft = await restartedStore.read(namespace: "attempt", key: "draft:\(server.attemptId):item-1")
            let abortedPos = await restartedStore.read(namespace: "attempt", key: "position:\(server.attemptId)")
            let abortedSess = await restartedStore.read(namespace: "attempt", key: "session:\(server.attemptId)")
            let abortedCtx = await restartedStore.read(namespace: "attempt", key: "startContext:\(server.attemptId)")
            XCTAssertNil(abortedDraft)
            XCTAssertNil(abortedPos)
            XCTAssertNil(abortedSess)
            XCTAssertEqual(
                abortedCtx,
                startContextPayload,
                "ABORTED must preserve startContext:{attemptId} across process restarts"
            )
        }
    }

    // AC-14: Account A/B isolation matrix with real AccountScopedStore, SessionCleanup, and SessionStore:
    // 1. Account B cannot read or send Account A's draft, position, sessionId, or startContext.
    // 2. Account A logout (Account tab & Lock screen 'other account') wipes A's attempt namespace (including crash-recovery on start),
    //    preserves B's data, and makes zero extra save calls.
    // 3. Lock screen 'Enter password' signing into B preserves A's data and hides it from B.
    // 4. Token refresh failure (.expired) preserves A's data; when A signs back in and reopens attempt, drafts reconcile & send.
    func testAccountIsolationMatrix() async throws {
        let rootDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("thi360-isolation-matrix-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: rootDir) }

        let clock = TestEngineClock()
        let (connectivity, _) = AttemptEngineTestHarness.makeConnectivity()
        let server = try FakeAttemptServer(initialStateVariant: "in-progress-shared", clock: clock)
        let itemId = server.firstLeafItemId()
        let partId = server.partId(at: 0)

        let provider = AccountScopedStoreProvider(rootDirectoryURL: rootDir)
        let storeA = try XCTUnwrap(provider.store(for: "account-A"))
        let storeB = try XCTUnwrap(provider.store(for: "account-B"))

        // Seed Account A and Account B data
        let draftStoreA = DraftStore(store: storeA)
        let draftStoreB = DraftStore(store: storeB)
        try await draftStoreA.saveDraft(
            ItemDraftRecord(itemId: itemId, partId: partId, seq: 3, answer: .singleChoice(optionId: "a"), flagged: true, dirtyAnswer: true, dirtyFlag: true),
            attemptId: server.attemptId
        )
        try await draftStoreA.writePosition(Position(partId: partId, itemId: itemId), attemptId: server.attemptId)
        try await draftStoreA.writeSessionId("session-A-uuid", attemptId: server.attemptId)
        try await storeA.write(Data("ctx-A".utf8), namespace: "attempt", key: "startContext:\(server.attemptId)")

        try await draftStoreB.saveDraft(
            ItemDraftRecord(itemId: itemId, partId: partId, seq: 1, answer: .singleChoice(optionId: "b"), flagged: false, dirtyAnswer: true, dirtyFlag: false),
            attemptId: server.attemptId
        )
        try await storeB.write(Data("ctx-B".utf8), namespace: "attempt", key: "startContext:\(server.attemptId)")

        // 1. Account B engine MUST NOT read or send Account A's draft, position, sessionId, or startContext
        let engineB = AttemptEngine(
            attemptId: server.attemptId,
            accountId: "account-B",
            api: server.makeAttemptAPI(),
            store: storeB,
            clock: clock,
            connectivity: connectivity,
            sessionIdGenerator: { "session-B-uuid" }
        )
        defer { engineB.dispose() }
        let engineBReady = await clock.waitUntil {
            engineB.state.session == .editing(sessionId: "session-B-uuid") && engineB.state.save == .saved
        }
        XCTAssertTrue(engineBReady)
        XCTAssertEqual(engineB.state.answers[itemId]?.value, .singleChoice(optionId: "b"))
        XCTAssertEqual(engineB.state.answers[itemId]?.flagged, false)
        XCTAssertNotEqual(server.calls(for: .claimSession).first?.decodedJSON()?["sessionId"] as? String, "session-A-uuid")
        engineB.dispose()

        // 2. Lock screen "Enter password" (reauthenticateFromLock) into Account B preserves A's data & hides from B
        let lockDir = rootDir.appendingPathComponent("lock-password", isDirectory: true)
        let lockProvider = AccountScopedStoreProvider(rootDirectoryURL: lockDir)
        let lockCleanup = SessionCleanup(storeProvider: lockProvider)
        await lockCleanup.register(AttemptDataCleanup(storeProvider: lockProvider))
        let lockStoreA = try XCTUnwrap(lockProvider.store(for: "account-A"))
        try await DraftStore(store: lockStoreA).saveDraft(
            ItemDraftRecord(itemId: itemId, partId: partId, seq: 4, answer: .singleChoice(optionId: "a"), flagged: false, dirtyAnswer: true, dirtyFlag: false),
            attemptId: server.attemptId
        )

        let meTemplate = try ContractFixtureLoader.loadApiFixture("me-200.json")
        var meBObj = try XCTUnwrap(JSONSerialization.jsonObject(with: meTemplate) as? [String: Any])
        meBObj["id"] = "account-B"
        meBObj["username"] = "userB"
        let meBJSON = try JSONSerialization.data(withJSONObject: meBObj)
        let loginBJSON = try ContractFixtureLoader.loadAuthFixture("login-200.json")
        let lockStack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens(username: "userA", accountId: "account-A", refreshToken: "refresh-A"),
            initialEnrolled: true,
            storeProvider: lockProvider,
            sessionCleanup: lockCleanup
        ) { transport in
            transport.registerJSON(method: .post, path: "/api/v1/auth/mobile/login", statusCode: 200, data: loginBJSON)
            transport.registerJSON(method: .get, path: "/api/v1/me", statusCode: 200, data: meBJSON)
            transport.registerEmpty(method: .post, path: "/api/v1/auth/mobile/logout", statusCode: 204)
        }
        defer { UserDefaults(suiteName: lockStack.defaultsSuiteName)?.removePersistentDomain(forName: lockStack.defaultsSuiteName) }

        lockStack.sessionStore.lockSession()
        try await lockStack.sessionStore.reauthenticateFromLock(identifier: "userB", password: "Password123!")
        XCTAssertEqual(lockStack.sessionStore.currentAccountId, "account-B")
        let aDraftAfterLockPassword = await lockProvider.read(accountId: "account-A", namespace: "attempt", key: "draft:\(server.attemptId):\(itemId)")
        XCTAssertNotNil(aDraftAfterLockPassword, "Lock-screen Enter Password into B must preserve Account A's drafts (AC-14 / ĐT 414)")
        let bReadsADraft = await lockProvider.read(accountId: "account-B", namespace: "attempt", key: "draft:\(server.attemptId):\(itemId)")
        XCTAssertNil(bReadsADraft, "Account B must never see Account A's drafts")

        // 3. Token refresh failure (.expired) preserves Account A's drafts; when A logs back in, drafts reconcile & send!
        let expDir = rootDir.appendingPathComponent("expired", isDirectory: true)
        let expProvider = AccountScopedStoreProvider(rootDirectoryURL: expDir)
        let expCleanup = SessionCleanup(storeProvider: expProvider)
        await expCleanup.register(AttemptDataCleanup(storeProvider: expProvider))
        let expStoreA = try XCTUnwrap(expProvider.store(for: "account-A"))
        try await DraftStore(store: expStoreA).saveDraft(
            ItemDraftRecord(itemId: itemId, partId: partId, seq: 6, answer: .singleChoice(optionId: "c"), flagged: false, dirtyAnswer: true, dirtyFlag: false),
            attemptId: server.attemptId
        )

        let expStack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens(username: "userA", accountId: "account-A", refreshToken: "refresh-expired"),
            storeProvider: expProvider,
            sessionCleanup: expCleanup
        )
        defer { UserDefaults(suiteName: expStack.defaultsSuiteName)?.removePersistentDomain(forName: expStack.defaultsSuiteName) }

        await expStack.sessionStore.clearLocalSession(reason: .expired)
        XCTAssertEqual(expStack.sessionStore.state, .signedOut(reason: .expired))
        let expiredDraftA = await expProvider.read(accountId: "account-A", namespace: "attempt", key: "draft:\(server.attemptId):\(itemId)")
        XCTAssertNotNil(
            expiredDraftA,
            "Session expiry must preserve Account A's drafts (AC-14 / ĐT 428)"
        )

        let serverReopen = try FakeAttemptServer(initialStateVariant: "in-progress-shared", clock: clock)
        let reopenedA = AttemptEngine(
            attemptId: serverReopen.attemptId,
            accountId: "account-A",
            api: serverReopen.makeAttemptAPI(),
            store: expStoreA,
            clock: clock,
            connectivity: connectivity
        )
        defer { reopenedA.dispose() }
        let reopenedSaved = await clock.waitUntil {
            serverReopen.callCount(for: .saveAnswers) == 1
                && reopenedA.state.answers[itemId]?.value == .singleChoice(optionId: "c")
                && reopenedA.state.save == .saved
        }
        XCTAssertTrue(reopenedSaved)

        // 4. Account A logout (Account tab & Lock screen 'other account') wipes A's attempt namespace, preserves B, zero extra saves;
        //    and interrupted logout completes on cold app start!
        for (idx, viaLockScreen) in [false, true].enumerated() {
            let logoutDir = rootDir.appendingPathComponent("logout-\(idx)", isDirectory: true)
            let lProvider = AccountScopedStoreProvider(rootDirectoryURL: logoutDir)
            let lCleanup = SessionCleanup(storeProvider: lProvider)
            let registry = AttemptEngineRegistry()
            await lCleanup.register(registry.makeCleanupParticipant(storeProvider: lProvider))

            let lStoreA = try XCTUnwrap(lProvider.store(for: "account-A"))
            let lStoreB = try XCTUnwrap(lProvider.store(for: "account-B"))
            try await DraftStore(store: lStoreA).saveDraft(
                ItemDraftRecord(itemId: itemId, partId: partId, seq: 2, answer: .singleChoice(optionId: "a"), flagged: false, dirtyAnswer: true, dirtyFlag: false),
                attemptId: server.attemptId
            )
            try await lStoreA.write(Data("ctx-A".utf8), namespace: "attempt", key: "startContext:\(server.attemptId)")
            try await lStoreB.write(Data("ctx-B".utf8), namespace: "attempt", key: "startContext:\(server.attemptId)")

            let logoutServer = try FakeAttemptServer(initialStateVariant: "in-progress-shared", clock: clock)
            _ = registry.engine(for: logoutServer.attemptId, accountId: "account-A") {
                AttemptEngine(
                    attemptId: logoutServer.attemptId,
                    accountId: "account-A",
                    api: logoutServer.makeAttemptAPI(),
                    store: lStoreA,
                    clock: clock,
                    connectivity: connectivity
                )
            }
            let savesBeforeLogout = logoutServer.callCount(for: .saveAnswers)

            let lStack = AccountTestFactory.makeContractStack(
                initialTokens: AccountTestFactory.sampleTokens(username: "userA", accountId: "account-A", refreshToken: "refresh-logout-\(idx)"),
                initialEnrolled: true,
                storeProvider: lProvider,
                sessionCleanup: lCleanup
            ) { transport in
                transport.registerEmpty(method: .post, path: "/api/v1/auth/mobile/logout", statusCode: 204)
            }
            defer { UserDefaults(suiteName: lStack.defaultsSuiteName)?.removePersistentDomain(forName: lStack.defaultsSuiteName) }

            if viaLockScreen {
                lStack.sessionStore.lockSession()
                let lockVM = LockViewModel(sessionStore: lStack.sessionStore, biometricManager: lStack.biometricManager)
                await lockVM.signInWithOtherAccount()
            } else {
                let accountVM = AccountViewModel(sessionStore: lStack.sessionStore, biometricManager: lStack.biometricManager)
                await accountVM.confirmLogout()
            }

            XCTAssertTrue(registry.activeAttemptIds.isEmpty)
            XCTAssertEqual(logoutServer.callCount(for: .saveAnswers), savesBeforeLogout, "Logout must not send extra saves")
            let logoutDraftA = await lProvider.read(accountId: "account-A", namespace: "attempt", key: "draft:\(server.attemptId):\(itemId)")
            let logoutCtxA = await lProvider.read(accountId: "account-A", namespace: "attempt", key: "startContext:\(server.attemptId)")
            let logoutCtxB = await lProvider.read(accountId: "account-B", namespace: "attempt", key: "startContext:\(server.attemptId)")
            XCTAssertNil(logoutDraftA)
            XCTAssertNil(logoutCtxA)
            XCTAssertEqual(
                logoutCtxB,
                Data("ctx-B".utf8),
                "Account B's data must remain intact when Account A signs out"
            )
        }

        // Interrupted logout completes on next app start
        let crashDir = rootDir.appendingPathComponent("crash-logout", isDirectory: true)
        let crashProvider = AccountScopedStoreProvider(rootDirectoryURL: crashDir)
        let crashStoreA = try XCTUnwrap(crashProvider.store(for: "account-A"))
        try await crashStoreA.write(Data("draft-leftover".utf8), namespace: "attempt", key: "draft:\(server.attemptId):\(itemId)")
        let crashCleanup = SessionCleanup(storeProvider: crashProvider)
        await crashCleanup.recordPendingLogoutForTesting("account-A")

        let nextLaunchProvider = AccountScopedStoreProvider(rootDirectoryURL: crashDir)
        let nextLaunchCleanup = SessionCleanup(storeProvider: nextLaunchProvider)
        await nextLaunchCleanup.register(AttemptDataCleanup(storeProvider: nextLaunchProvider))
        await nextLaunchCleanup.runAppStart()

        let leftoverDraft = await nextLaunchProvider.read(accountId: "account-A", namespace: "attempt", key: "draft:\(server.attemptId):\(itemId)")
        XCTAssertNil(
            leftoverDraft,
            "Interrupted logout must finish wiping Account A's attempt namespace on next app start"
        )
    }
}
