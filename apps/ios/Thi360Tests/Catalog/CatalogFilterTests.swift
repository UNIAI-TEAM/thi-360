import Foundation
import SwiftUI
import XCTest
@testable import Thi360

@MainActor
final class CatalogFilterTests: XCTestCase {
    func testKindChips() async throws {
        var filtersObj = try CatalogTestFixtures.loadJSONObject(
            operationId: Operations.getStorefrontFilters.id,
            status: 200
        )
        filtersObj["catalogKinds"] = [
            ["code": "HSA", "nameVi": "HSA", "nameEn": "HSA"],
            ["code": "TSA", "nameVi": "TSA", "nameEn": "TSA"]
        ]
        filtersObj["parts"] = [
            ["kindCode": "HSA", "code": "P1", "name": "Tư duy định lượng", "count": 2]
        ]
        let filtersData = try CatalogTestFixtures.encodeJSON(filtersObj)
        let nonEmptyData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.listStorefrontProducts.id,
            status: 200,
            fileContains: "storefront-products-list-200.json"
        )
        let emptyData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.listStorefrontProducts.id,
            status: 200,
            fileContains: "storefront-products-list-200-empty.json"
        )
        let accessData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.queryMyExamAccess.id,
            status: 200
        )

        let stack = try CatalogTestStackFactory.makeStack { transport in
            transport.registerJSON(method: .get, path: "/api/v1/public/storefront/filters", data: filtersData)
            transport.register(method: .get, path: "/api/v1/public/storefront/products") { request in
                let query = CatalogTestStackFactory.queryItems(from: request)
                if query["kind"] == "TSA" {
                    return ContractFakeTransport.jsonResponse(statusCode: 200, data: emptyData)
                }
                return ContractFakeTransport.jsonResponse(statusCode: 200, data: nonEmptyData)
            }
            transport.registerJSON(method: .post, path: "/api/v1/me/exam-access/query", data: accessData)
        }
        defer {
            UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName)
        }

        let feed = CatalogFeed(accountId: "acc-1", api: stack.api)
        await feed.loadInitialIfNeeded()

        XCTAssertEqual(feed.filters.effectiveKinds.map(\.code), ["HSA", "TSA"])
        XCTAssertNil(feed.query.kind)

        await feed.selectPart(kind: "HSA", part: "P1")
        XCTAssertEqual(feed.query.kind, "HSA")
        XCTAssertEqual(feed.query.part, "P1")

        stack.transport.resetRecordedRequests()
        await feed.selectKind("TSA")

        let req = try XCTUnwrap(stack.transport.requests(method: .get, path: "/api/v1/public/storefront/products").first)
        let items = CatalogTestStackFactory.queryItems(from: req)
        XCTAssertEqual(items["kind"], "TSA")
        XCTAssertEqual(items["page"], "0")
        XCTAssertNil(items["part"])
        XCTAssertNil(feed.query.part)
        XCTAssertTrue(feed.isNoResultsState)

        let fallbackOnly = CatalogFilters(
            catalogKinds: [],
            kinds: [ExamKindInfo(code: "VACT", name: LocalizedLabel(nameVi: "VACT", nameEn: "VACT"))],
            formats: [],
            parts: [],
            subjects: []
        )
        XCTAssertEqual(fallbackOnly.effectiveKinds.map(\.code), ["VACT"])
        XCTAssertTrue(CatalogFilters.empty.effectiveKinds.isEmpty)
    }

    func testFormatOptions() async throws {
        var filtersObj = try CatalogTestFixtures.loadJSONObject(
            operationId: Operations.getStorefrontFilters.id,
            status: 200
        )
        filtersObj["formats"] = [
            ["code": "SUBJECT", "count": 2],
            ["code": "FULL", "count": 4]
        ]
        let filtersData = try CatalogTestFixtures.encodeJSON(filtersObj)
        let productsData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.listStorefrontProducts.id,
            status: 200,
            fileContains: "storefront-products-list-200.json"
        )
        let accessData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.queryMyExamAccess.id,
            status: 200
        )

        let stack = try CatalogTestStackFactory.makeStack { transport in
            transport.registerJSON(method: .get, path: "/api/v1/public/storefront/filters", data: filtersData)
            transport.registerJSON(method: .get, path: "/api/v1/public/storefront/products", data: productsData)
            transport.registerJSON(method: .post, path: "/api/v1/me/exam-access/query", data: accessData)
        }
        defer {
            UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName)
        }

        let feed = CatalogFeed(accountId: "acc-1", api: stack.api)
        await feed.loadInitialIfNeeded()

        let labels = [CatalogStrings.text("storefront.catalog.formatAll", locale: .vi)]
            + feed.filters.orderedFormats.map { CatalogStrings.formatLabel(for: $0.rawValue, locale: .vi) }
        XCTAssertEqual(labels, ["Tất cả hình thức", "Bài thi FULL", "Bài thi theo môn"])

        stack.transport.resetRecordedRequests()
        await feed.selectFormat(.bundle)
        let bundleReq = try XCTUnwrap(stack.transport.requests(method: .get, path: "/api/v1/public/storefront/products").first)
        XCTAssertEqual(CatalogTestStackFactory.queryItems(from: bundleReq)["format"], "BUNDLE")
        XCTAssertEqual(CatalogTestStackFactory.queryItems(from: bundleReq)["page"], "0")

        await feed.selectFormat(nil)
        let clearReq = try XCTUnwrap(stack.transport.requests(method: .get, path: "/api/v1/public/storefront/products").last)
        XCTAssertNil(CatalogTestStackFactory.queryItems(from: clearReq)["format"])
    }

    func testSort() async throws {
        let generated = try CatalogTestFixtures.makeProductCardJSONList(totalCount: 3, codePrefix: "SORT") { idx, card in
            card["price"] = [300_000, 100_000, 200_000][idx]
            card["listPrice"] = [300_000, 100_000, 200_000][idx]
        }
        let orderedPage = try CatalogTestFixtures.makePageData(allItems: generated, page: 0)
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
            transport.registerJSON(method: .get, path: "/api/v1/public/storefront/products", data: orderedPage)
            transport.registerJSON(method: .post, path: "/api/v1/me/exam-access/query", data: accessData)
        }
        defer {
            UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName)
        }

        let feed = CatalogFeed(accountId: "acc-1", api: stack.api)
        await feed.loadInitialIfNeeded()
        stack.transport.resetRecordedRequests()

        await feed.selectSort(.priceAsc)
        let req = try XCTUnwrap(stack.transport.requests(method: .get, path: "/api/v1/public/storefront/products").first)
        XCTAssertEqual(CatalogTestStackFactory.queryItems(from: req)["sort"], "PRICE_ASC")
        XCTAssertEqual(CatalogTestStackFactory.queryItems(from: req)["page"], "0")
        XCTAssertEqual(feed.loadedCards.map(\.code), ["SORT-001", "SORT-002", "SORT-003"])
    }

    func testPartSubject() async throws {
        var filtersObj = try CatalogTestFixtures.loadJSONObject(
            operationId: Operations.getStorefrontFilters.id,
            status: 200
        )
        filtersObj["parts"] = [
            ["kindCode": "HSA", "code": "P1", "name": "Tư duy định lượng", "count": 2]
        ]
        let filtersData = try CatalogTestFixtures.encodeJSON(filtersObj)
        let productsData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.listStorefrontProducts.id,
            status: 200,
            fileContains: "storefront-products-list-200.json"
        )
        let accessData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.queryMyExamAccess.id,
            status: 200
        )

        let stack = try CatalogTestStackFactory.makeStack { transport in
            transport.registerJSON(method: .get, path: "/api/v1/public/storefront/filters", data: filtersData)
            transport.registerJSON(method: .get, path: "/api/v1/public/storefront/products", data: productsData)
            transport.registerJSON(method: .post, path: "/api/v1/me/exam-access/query", data: accessData)
        }
        defer {
            UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName)
        }

        let feed = CatalogFeed(accountId: "acc-1", api: stack.api)
        await feed.loadInitialIfNeeded()

        let visibleAll = feed.filters.visibleParts(forKind: nil)
        XCTAssertEqual(visibleAll.count, 1)
        XCTAssertEqual(visibleAll[0].displayLabel(selectedKind: nil), "HSA · Tư duy định lượng")

        let math = try XCTUnwrap(feed.filters.subjects.first(where: { $0.code == "TOAN" }))
        XCTAssertEqual(math.name.localized(for: .en), "Mathematics")
        XCTAssertEqual(visibleAll[0].name, "Tư duy định lượng")

        stack.transport.resetRecordedRequests()
        await feed.selectPart(kind: "HSA", part: "P1")
        let partReq = try XCTUnwrap(stack.transport.requests(method: .get, path: "/api/v1/public/storefront/products").first)
        let partQuery = CatalogTestStackFactory.queryItems(from: partReq)
        XCTAssertEqual(partQuery["kind"], "HSA")
        XCTAssertEqual(partQuery["part"], "P1")
        XCTAssertEqual(partQuery["page"], "0")
        XCTAssertNil(partQuery["subject"])
        XCTAssertEqual(feed.query.kind, "HSA")

        stack.transport.resetRecordedRequests()
        await feed.selectSubject("TOAN")
        let subjectReq = try XCTUnwrap(stack.transport.requests(method: .get, path: "/api/v1/public/storefront/products").first)
        let subjectQuery = CatalogTestStackFactory.queryItems(from: subjectReq)
        XCTAssertEqual(subjectQuery["kind"], "HSA")
        XCTAssertEqual(subjectQuery["subject"], "TOAN")
        XCTAssertNil(subjectQuery["part"])

        XCTAssertTrue(feed.filters.visibleParts(forKind: "TSA").isEmpty)
        XCTAssertEqual(feed.filters.subjects.map(\.code), ["TOAN", "VAN"])
    }
}
