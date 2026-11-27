import Foundation
import HTTPTypes
import OpenAPIRuntime
import XCTest
@testable import Thi360

@MainActor
final class CatalogScrollTests: XCTestCase {
    func testSixtyCardsUseStableCodeIdentityWithZeroDuplicatePageOrCoverImageLoads() async throws {
        let pngData = CatalogTestFixtures.makeTinyPNGData(color: .systemBlue)
        let all60 = try CatalogTestFixtures.makeProductCardJSONList(totalCount: 60, codePrefix: "SCR") { idx, card in
            card["coverUrl"] = "/api/v1/public/covers/scr-\(idx + 1).png"
        }
        let filtersData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.getStorefrontFilters.id,
            status: 200
        )
        let accessData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.queryMyExamAccess.id,
            status: 200
        )

        let stack = try CatalogTestStackFactory.makeStack { transport in
            transport.registerJSON(method: .get, path: "/api/v1/public/storefront/filters", data: filtersData)
            transport.register(method: .get, path: "/api/v1/public/storefront/products") { request in
                let page = Int(CatalogTestStackFactory.queryItems(from: request)["page"] ?? "0") ?? 0
                let data = try CatalogTestFixtures.makePageData(allItems: all60, page: page)
                return ContractFakeTransport.jsonResponse(statusCode: 200, data: data)
            }
            transport.registerJSON(method: .post, path: "/api/v1/me/exam-access/query", data: accessData)
            transport.setFallback { request in
                if request.path.hasPrefix("/api/v1/public/covers/") {
                    var fields = HTTPFields()
                    fields[.contentType] = "image/png"
                    return (HTTPResponse(status: .ok, headerFields: fields), HTTPBody(pngData))
                }
                return ContractFakeTransport.emptyResponse(statusCode: 404)
            }
        }
        defer {
            UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName)
        }

        let feed = CatalogFeed(accountId: "acc-1", api: stack.api)
        await feed.loadInitialIfNeeded()
        for _ in 0..<4 {
            await feed.loadNextPageIfNeeded()
        }
        await feed.loadNextPageIfNeeded()
        await feed.loadNextPageIfNeeded()

        XCTAssertEqual(feed.loadedCards.count, 60)
        let ids = feed.loadedCards.map(\.id)
        XCTAssertEqual(ids, feed.loadedCards.map(\.code))
        XCTAssertEqual(Set(ids).count, 60)

        let requestedPages = stack.transport
            .requests(method: .get, path: "/api/v1/public/storefront/products")
            .compactMap { CatalogTestStackFactory.queryItems(from: $0)["page"] }
        XCTAssertEqual(requestedPages, ["0", "1", "2", "3", "4"])

        let loader = ImageLoader(
            apiBaseURL: URL(string: "https://api.thi360.test")!,
            tokenStore: stack.tokenStore,
            fetcher: ClientTransportImageDataFetcher(transport: stack.transport),
            maxCachedImages: 100
        )

        for card in feed.loadedCards {
            if let cover = card.effectiveCoverURL {
                _ = try await loader.loadImage(from: cover, authenticated: false)
            }
        }
        let firstPassCoverRequests = stack.transport.recordedRequests.filter {
            $0.path.hasPrefix("/api/v1/public/covers/")
        }.count
        XCTAssertEqual(firstPassCoverRequests, 60)

        for card in feed.loadedCards.reversed() {
            if let cover = card.effectiveCoverURL {
                _ = try await loader.loadImage(from: cover, authenticated: false)
            }
        }
        let secondPassCoverRequests = stack.transport.recordedRequests.filter {
            $0.path.hasPrefix("/api/v1/public/covers/")
        }.count
        XCTAssertEqual(secondPassCoverRequests, 60, "Already loaded covers must be served from cache without refetching")
    }
}
