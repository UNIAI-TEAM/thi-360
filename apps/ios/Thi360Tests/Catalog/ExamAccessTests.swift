import Foundation
import SwiftUI
import XCTest
@testable import Thi360

@MainActor
final class ExamAccessTests: XCTestCase {
    func testTwelveCardPageSendsSingleQueryShowsLoadingPlaceholderAndDefaultsMissingCodeToNone() async throws {
        let items = try CatalogTestFixtures.makeProductCardJSONList(totalCount: 12, codePrefix: "AC13")
        let pageData = try CatalogTestFixtures.makePageData(allItems: items, page: 0)
        let filtersData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.getStorefrontFilters.id,
            status: 200
        )
        let partialAccess = try CatalogTestFixtures.makeExamAccessData(items: [
            try CatalogTestFixtures.makeExamAccessItem(
                templateState: "CAN_START",
                code: "AC13-001",
                attemptsLeft: 2,
                entitlementId: "ent-1"
            )
        ])

        let gate = CatalogAsyncGate()

        let stack = try CatalogTestStackFactory.makeStack { transport in
            transport.registerJSON(method: .get, path: "/api/v1/public/storefront/filters", data: filtersData)
            transport.registerJSON(method: .get, path: "/api/v1/public/storefront/products", data: pageData)
            transport.register(method: .post, path: "/api/v1/me/exam-access/query") { _ in
                await gate.wait()
                return ContractFakeTransport.jsonResponse(statusCode: 200, data: partialAccess)
            }
        }
        defer {
            UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName)
        }

        let feed = CatalogFeed(accountId: "acc-1", api: stack.api)
        let loadTask = Task { await feed.loadInitialIfNeeded() }

        let deadline = Date().addingTimeInterval(2.0)
        while feed.loadedCards.isEmpty && Date() < deadline {
            try await Task.sleep(nanoseconds: 10_000_000)
        }
        XCTAssertEqual(feed.loadedCards.count, 12)
        XCTAssertEqual(feed.accessCell(for: "AC13-001"), .loading)

        let cardView = CatalogProductCardView(
            product: feed.loadedCards[0],
            accessCell: feed.accessCell(for: "AC13-001"),
            locale: .vi,
            isSelected: false,
            onOpenDetail: {},
            onAccessButtonTap: { _ in },
            onReloadAccess: {}
        )
        XCTAssertEqual(cardView.product.name, feed.loadedCards[0].name)
        XCTAssertEqual(cardView.renderedPriceView.renderedSalePriceText, "200.000 đ")
        XCTAssertEqual(cardView.renderedAccessSection.renderedLoadingLabel, "Đang tải quyền")
        XCTAssertFalse(cardView.renderedAccessSection.renderedStrings.contains("Mua bài thi"))

        let (window, _) = CatalogViewInspector.host(
            cardView
                .environment(\.brandPalette, .defaultLight)
                .environment(\.statusPalette, .light)
        )
        let (_, png) = try CatalogViewInspector.renderPNG(window: window, size: CGSize(width: 390, height: 844))
        XCTAssertGreaterThan(png.count, 2048)
        window.isHidden = true

        await gate.open()
        await loadTask.value

        let accessRequests = stack.transport.requests(method: .post, path: "/api/v1/me/exam-access/query")
        XCTAssertEqual(accessRequests.count, 1)
        XCTAssertEqual(CatalogTestStackFactory.accessRequestCodes(from: accessRequests[0]).count, 12)

        guard case let .ready(firstRow) = feed.accessCell(for: "AC13-001") else {
            return XCTFail("Expected ready access for AC13-001")
        }
        XCTAssertEqual(firstRow.state, .canStart)

        guard case let .ready(missingRow) = feed.accessCell(for: "AC13-012") else {
            return XCTFail("Expected ready default NONE for missing code AC13-012")
        }
        XCTAssertEqual(missingRow.state, .none)
    }

    func testAccessErrors5xx400ValidationFailedAndNetworkReloadSameBatch() async throws {
        let filtersData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.getStorefrontFilters.id,
            status: 200
        )
        let productsData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.listStorefrontProducts.id,
            status: 200,
            fileContains: "storefront-products-list-200.json"
        )
        let validation400 = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.queryMyExamAccess.id,
            status: 400
        )
        let access200 = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.queryMyExamAccess.id,
            status: 200
        )

        let serverError500 = try CatalogTestFixtures.makeInternalServerError500Data()
        let stack = try CatalogTestStackFactory.makeStack { transport in
            transport.registerJSON(method: .get, path: "/api/v1/public/storefront/filters", data: filtersData)
            transport.registerJSON(method: .get, path: "/api/v1/public/storefront/products", data: productsData)
            transport.enqueue(method: .post, path: "/api/v1/me/exam-access/query", handlers: [
                { _ in
                    ContractFakeTransport.jsonResponse(statusCode: 500, data: serverError500)
                },
                { _ in
                    ContractFakeTransport.jsonResponse(statusCode: 400, data: validation400)
                },
                { _ in
                    throw URLError(.notConnectedToInternet)
                },
                { _ in
                    ContractFakeTransport.jsonResponse(statusCode: 200, data: access200)
                }
            ])
        }
        defer {
            UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName)
        }

        let feed = CatalogFeed(accountId: "acc-1", api: stack.api)
        await feed.loadInitialIfNeeded()
        XCTAssertEqual(feed.accessCell(for: "EXAM-DEMO-01"), .failed)

        let failedView = AccessActionSectionView(
            code: "EXAM-DEMO-01",
            versionNo: 1,
            price: 200_000,
            isBundle: false,
            isPricePanel: false,
            cell: .failed,
            locale: .vi,
            onButtonTap: { _ in },
            onReloadAccess: {},
            onViewDetail: {}
        )
        XCTAssertEqual(
            failedView.renderedStrings,
            ["Chưa xác định được quyền của tài khoản.", "Tải lại quyền", "Xem chi tiết"]
        )

        let (window, _) = CatalogViewInspector.host(
            failedView
                .padding(16)
                .environment(\.brandPalette, .defaultLight)
                .environment(\.statusPalette, .light),
            size: CGSize(width: 350, height: 220)
        )
        let (_, failedPNG) = try CatalogViewInspector.renderPNG(window: window, size: CGSize(width: 350, height: 220))
        XCTAssertGreaterThan(failedPNG.count, 1024)
        window.isHidden = true

        await feed.reloadAccess(for: "EXAM-DEMO-01")
        XCTAssertEqual(feed.accessCell(for: "EXAM-DEMO-01"), .failed)

        await feed.reloadAccess(for: "EXAM-DEMO-01")
        XCTAssertEqual(feed.accessCell(for: "EXAM-DEMO-01"), .failed)

        await feed.reloadAccess(for: "EXAM-DEMO-01")
        if case .ready = feed.accessCell(for: "EXAM-DEMO-01") {
        } else {
            XCTFail("Expected recovered access state")
        }

        let batches = stack.transport
            .requests(method: .post, path: "/api/v1/me/exam-access/query")
            .map(CatalogTestStackFactory.accessRequestCodes(from:))
        XCTAssertEqual(batches.count, 4)
        for batch in batches {
            XCTAssertEqual(batch, ["EXAM-DEMO-01", "BUNDLE-DEMO-01"])
        }
    }

    func testUnauthorized401RefreshesAndSignsOutOnRejectedRefresh() async throws {
        let unauth401 = try ContractFixtureLoader.loadApiFixture("me-401-unauthenticated.json")
        let revoked401 = try ContractFixtureLoader.loadAuthFixture("refresh-401-session-revoked.json")

        let stack = try CatalogTestStackFactory.makeStack { transport in
            transport.registerJSON(method: .post, path: "/api/v1/me/exam-access/query", statusCode: 401, data: unauth401)
            transport.registerJSON(method: .post, path: "/api/v1/auth/mobile/refresh", statusCode: 401, data: revoked401)
        }
        defer {
            UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName)
        }

        do {
            _ = try await stack.api.queryExamAccess(codes: ["EXAM-DEMO-01"])
            XCTFail("Expected 401 error")
        } catch {
        }

        XCTAssertEqual(stack.transport.callCount(method: .post, path: "/api/v1/auth/mobile/refresh"), 1)
        XCTAssertNil(try stack.tokenStore.read())
        let deadline = Date().addingTimeInterval(2.0)
        while stack.sessionStore.state != .signedOut(reason: .expired) && Date() < deadline {
            try await Task.sleep(nanoseconds: 10_000_000)
        }
        XCTAssertEqual(stack.sessionStore.state, .signedOut(reason: .expired))
    }

    func testForbidden403FollowsSpecA77Branches() async throws {
        let forbidden403 = try CatalogTestFixtures.loadForbidden403Data()
        let access200 = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.queryMyExamAccess.id,
            status: 200
        )
        let studentOnly403 = try ContractFixtureLoader.loadAuthFixture("refresh-403-app-student-only.json")

        let retryStack = try CatalogTestStackFactory.makeStack { transport in
            transport.enqueue(method: .post, path: "/api/v1/me/exam-access/query", handlers: [
                { _ in ContractFakeTransport.jsonResponse(statusCode: 403, data: forbidden403) },
                { _ in ContractFakeTransport.jsonResponse(statusCode: 200, data: access200) }
            ])
        }
        defer {
            UserDefaults(suiteName: retryStack.defaultsSuiteName)?.removePersistentDomain(forName: retryStack.defaultsSuiteName)
        }
        let rows = try await retryStack.api.queryExamAccess(codes: ["EXAM-DEMO-01"])
        XCTAssertEqual(retryStack.transport.callCount(method: .post, path: "/api/v1/auth/mobile/refresh"), 1)
        XCTAssertEqual(retryStack.transport.callCount(method: .post, path: "/api/v1/me/exam-access/query"), 2)
        XCTAssertFalse(rows.isEmpty)

        let roleStack = try CatalogTestStackFactory.makeStack { transport in
            transport.registerJSON(method: .post, path: "/api/v1/me/exam-access/query", statusCode: 403, data: forbidden403)
            transport.registerJSON(method: .post, path: "/api/v1/auth/mobile/refresh", statusCode: 403, data: studentOnly403)
        }
        defer {
            UserDefaults(suiteName: roleStack.defaultsSuiteName)?.removePersistentDomain(forName: roleStack.defaultsSuiteName)
        }
        do {
            _ = try await roleStack.api.queryExamAccess(codes: ["EXAM-DEMO-01"])
            XCTFail("Expected APP_STUDENT_ONLY error")
        } catch {
        }
        let deadline = Date().addingTimeInterval(2.0)
        while Date() < deadline {
            if case .signedOut(reason: .studentOnly) = roleStack.sessionStore.state { break }
            try await Task.sleep(nanoseconds: 10_000_000)
        }
        if case .signedOut(reason: .studentOnly) = roleStack.sessionStore.state {
        } else {
            XCTFail("Expected signedOut(studentOnly) state on APP_STUDENT_ONLY, got \(roleStack.sessionStore.state)")
        }

        let still403Stack = try CatalogTestStackFactory.makeStack { transport in
            transport.registerJSON(method: .post, path: "/api/v1/me/exam-access/query", statusCode: 403, data: forbidden403)
        }
        defer {
            UserDefaults(suiteName: still403Stack.defaultsSuiteName)?.removePersistentDomain(forName: still403Stack.defaultsSuiteName)
        }
        do {
            _ = try await still403Stack.api.queryExamAccess(codes: ["EXAM-DEMO-01"])
            XCTFail("Expected 403 after single retry")
        } catch {
        }
        XCTAssertEqual(still403Stack.transport.callCount(method: .post, path: "/api/v1/auth/mobile/refresh"), 1)
        XCTAssertEqual(still403Stack.transport.callCount(method: .post, path: "/api/v1/me/exam-access/query"), 2)
    }
}
