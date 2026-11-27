import XCTest
@testable import Thi360

final class CatalogQueryTests: XCTestCase {
    func testWithKindDropsPartAndKeepsSubject() {
        let initial = CatalogQuery(
            q: "hsa",
            kind: "HSA",
            format: .part,
            part: "P1",
            subject: "TOAN",
            sort: .priceAsc
        )
        let switched = initial.withKind("TSA")

        XCTAssertEqual(switched.kind, "TSA")
        XCTAssertNil(switched.part)
        XCTAssertEqual(switched.subject, "TOAN")
        XCTAssertEqual(switched.q, "hsa")
        XCTAssertEqual(switched.format, .part)
        XCTAssertEqual(switched.sort, .priceAsc)
    }

    func testWithPartSetsKindAndDropsSubject() {
        let initial = CatalogQuery(q: "", kind: nil, format: nil, part: nil, subject: "TOAN", sort: .newest)
        let updated = initial.withPart(kind: "HSA", part: "P1")

        XCTAssertEqual(updated.kind, "HSA")
        XCTAssertEqual(updated.part, "P1")
        XCTAssertNil(updated.subject)
    }

    func testWithSubjectDropsPartAndKeepsKind() {
        let initial = CatalogQuery(q: "", kind: "HSA", format: nil, part: "P1", subject: nil, sort: .newest)
        let updated = initial.withSubject("TOAN")

        XCTAssertEqual(updated.kind, "HSA")
        XCTAssertNil(updated.part)
        XCTAssertEqual(updated.subject, "TOAN")
    }

    func testIsFilteredIncludesNonDefaultSortAndClearedResetsAll() {
        XCTAssertFalse(CatalogQuery.empty.isFiltered)
        XCTAssertTrue(CatalogQuery.empty.withSort(.priceDesc).isFiltered)
        XCTAssertFalse(CatalogQuery.empty.withSearch("   ").isFiltered)

        let filtered = CatalogQuery(
            q: "toan",
            kind: "HSA",
            format: .part,
            part: "P1",
            subject: nil,
            sort: .priceDesc
        )
        XCTAssertTrue(filtered.isFiltered)
        XCTAssertEqual(filtered.cleared(), .empty)
    }

    func testParamsOmitsWhitespaceQAndUsesPageSize12() {
        let query = CatalogQuery(q: "   ", kind: "HSA", format: .bundle, part: nil, subject: "TOAN", sort: .priceAsc)
        let params = query.params(page: 2)

        XCTAssertNil(params.q)
        XCTAssertEqual(params.kind, "HSA")
        XCTAssertEqual(params.format, .bundle)
        XCTAssertNil(params.part)
        XCTAssertEqual(params.subject, "TOAN")
        XCTAssertEqual(params.sort, .priceAsc)
        XCTAssertEqual(params.page, 2)
        XCTAssertEqual(params.size, 12)
    }
}
