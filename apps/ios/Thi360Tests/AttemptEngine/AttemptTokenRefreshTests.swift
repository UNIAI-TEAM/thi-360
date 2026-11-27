import Foundation
import XCTest
@testable import Thi360

@MainActor
final class AttemptTokenRefreshTests: XCTestCase {
    func testThreeConcurrent401sCoalesceIntoSingleRefreshAndKeepSavingStatus() async throws {
        let clock = TestEngineClock()
        let (_, store, dir) = try AttemptEngineTestHarness.makeStore()
        defer { try? FileManager.default.removeItem(at: dir) }
        let (connectivity, _) = AttemptEngineTestHarness.makeConnectivity()
        let server = try FakeAttemptServer(initialStateVariant: "in-progress-shared", clock: clock)

        let expiredAccess = "expired-access-token-1"
        let freshAccess = "refreshed-access-token-2"
        let tokenStore = InMemoryTokenStore(
            initialTokens: SessionTokens(
                accessToken: expiredAccess,
                refreshToken: "valid-refresh-token-1",
                accessExpiresIn: 300,
                refreshExpiresIn: 604_800,
                mustChangePassword: false,
                username: "student.test",
                accountId: "account-A"
            )
        )

        let refreshGate = AttemptTestGate()
        // Plan A6 documented derivation: mutate accessToken field on certified mobileRefresh 200 fixture to verify refreshed bearer.
        var refreshObj = try AttemptFixtureCatalog.loadJSONObject(operationId: Operations.mobileRefresh.id, status: 200)
        refreshObj["accessToken"] = freshAccess
        let refreshData = try JSONSerialization.data(withJSONObject: refreshObj)

        server.transport.register(method: .post, path: "/api/v1/auth/mobile/refresh") { _ in
            await refreshGate.enterAndWait()
            return ContractFakeTransport.jsonResponse(statusCode: 200, data: refreshData)
        }

        let apiClient = MobileAPIClient(
            serverURL: URL(string: "https://api.thi360.test")!,
            tokenStore: tokenStore,
            transport: server.transport,
            localeProvider: { .vi }
        )
        let api = server.makeAttemptAPI(tokenStore: tokenStore, refresher: apiClient.refresher)

        let engine = AttemptEngine(
            attemptId: server.attemptId,
            accountId: "account-A",
            api: api,
            store: store,
            clock: clock,
            connectivity: connectivity
        )
        defer { engine.dispose() }
        let asyncAssertResult1 = await clock.waitUntil { engine.state.activeSessionId != nil }
        XCTAssertTrue(asyncAssertResult1)

        let unauthFixture = try AttemptFixtureCatalog.loadData(
            operationId: Operations.me.id,
            status: 401,
            variant: "unauthenticated"
        )
        let unauthOverride = FakeAttemptServer.OverrideResponse(
            statusCode: 401,
            data: unauthFixture,
            headers: [:],
            networkError: nil,
            delay: nil,
            gate: nil
        )
        server.enqueue(.saveAnswers, unauthOverride)
        server.enqueue(.heartbeat, unauthOverride)
        server.enqueue(.getState, unauthOverride)

        let itemId = server.firstLeafItemId()
        let partId = server.partId(at: 0)

        engine.answer(itemId: itemId, value: .singleChoice(optionId: "a"))
        engine.position(partId: partId, itemId: itemId)
        engine.refresh()
        await clock.advance(by: .seconds(30))

        let allThreeHit401 = await clock.waitUntil {
            server.callCount(for: .saveAnswers) >= 1
                && server.callCount(for: .heartbeat) >= 1
                && server.callCount(for: .getState) >= 2
        }
        XCTAssertTrue(allThreeHit401)
        await refreshGate.waitUntilEntered()
        XCTAssertEqual(engine.state.save, .saving)

        await refreshGate.releaseAll()
        let asyncAssertResult2 = await clock.waitUntil {
            engine.state.save == .saved
                && engine.state.answers[itemId]?.sync == .saved
                && server.calls(for: .saveAnswers).last?.authorizationHeader == "Bearer \(freshAccess)"
                && server.calls(for: .heartbeat).last?.authorizationHeader == "Bearer \(freshAccess)"
                && server.calls(for: .getState).last?.authorizationHeader == "Bearer \(freshAccess)"
        }
        XCTAssertTrue(asyncAssertResult2)

        XCTAssertEqual(
            server.transport.callCount(method: .post, path: "/api/v1/auth/mobile/refresh"),
            1,
            "Three concurrent 401 responses must trigger exactly ONE token refresh"
        )
    }
}
