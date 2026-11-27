import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class ProductCardViewTests: XCTestCase {
    func testPartReviewCardChipsSpecsTwoLineClampAndOpenDetailCallsProductEndpoint() async throws {
        let shortCard = CatalogProductCard(
            code: "HSA-PART-SHORT",
            name: "Đề ôn phần Tư duy định lượng",
            summary: "Tóm tắt",
            coverUrl: nil,
            examKind: ExamKindInfo(code: "HSA", name: LocalizedLabel(nameVi: "HSA", nameEn: "HSA")),
            format: "PART",
            scopeLabel: LocalizedLabel(nameVi: "Tư duy định lượng", nameEn: "Quantitative"),
            buildMode: "REVIEW",
            questionCount: IntRangeValue(min: 48, max: 50),
            durationMinutes: IntRangeValue(min: 75, max: 75),
            attemptsGranted: 2,
            listPrice: 120_000,
            price: 120_000,
            promotion: nil,
            versionNo: 1,
            productType: "EXAM"
        )

        let veryLongCard = CatalogProductCard(
            code: "HSA-PART-REV-01",
            name: String(repeating: "Đề ôn phần Tư duy định lượng rất dài ", count: 8),
            summary: "Tóm tắt",
            coverUrl: nil,
            examKind: ExamKindInfo(code: "HSA", name: LocalizedLabel(nameVi: "HSA", nameEn: "HSA")),
            format: "PART",
            scopeLabel: LocalizedLabel(nameVi: "Tư duy định lượng", nameEn: "Quantitative"),
            buildMode: "REVIEW",
            questionCount: IntRangeValue(min: 48, max: 50),
            durationMinutes: IntRangeValue(min: 75, max: 75),
            attemptsGranted: 2,
            listPrice: 120_000,
            price: 120_000,
            promotion: nil,
            versionNo: 1,
            productType: "EXAM"
        )

        var openedCode: String?
        let cardView = CatalogProductCardView(
            product: veryLongCard,
            accessCell: .ready(.none(code: veryLongCard.code)),
            locale: .vi,
            isSelected: false,
            onOpenDetail: { openedCode = veryLongCard.code },
            onAccessButtonTap: { _ in },
            onReloadAccess: {}
        )

        XCTAssertEqual(cardView.renderedBadges, ["HSA", "Bài thi theo phần", "Tư duy định lượng", "Bài ôn"])
        XCTAssertEqual(cardView.renderedSpecsLine, "48–50 câu · 75 phút · 2 lượt")
        XCTAssertTrue(cardView.renderedAccessSection.renderedStrings.contains("Xem chi tiết"))

        // Measure fitted height of 1-line title card vs 8-line-long title card -> .lineLimit(2) caps growth to <= 30pt!
        let shortHeight = fittedHeight(of: CatalogProductCardView(
            product: shortCard,
            accessCell: .ready(.none(code: shortCard.code)),
            locale: .vi,
            isSelected: false,
            onOpenDetail: {},
            onAccessButtonTap: { _ in },
            onReloadAccess: {}
        ))
        let longHeight = fittedHeight(of: cardView)
        XCTAssertLessThan(
            longHeight - shortHeight,
            40.0,
            "Card title must clamp to at most 2 lines (short=\(shortHeight), long=\(longHeight))"
        )

        let examDetailData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.getStorefrontProduct.id,
            status: 200,
            fileContains: "exam"
        )
        let accessData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.queryMyExamAccess.id,
            status: 200
        )
        let stack = try CatalogTestStackFactory.makeStack { transport in
            transport.registerJSON(method: .get, path: "/api/v1/public/storefront/products/HSA-PART-REV-01", data: examDetailData)
            transport.registerJSON(method: .post, path: "/api/v1/me/exam-access/query", data: accessData)
        }
        defer {
            UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName)
        }

        cardView.onOpenDetail()
        let detailVM = ProductDetailViewModel(code: try XCTUnwrap(openedCode), api: stack.api)
        await detailVM.loadIfNeeded()
        XCTAssertEqual(
            stack.transport.callCount(method: .get, path: "/api/v1/public/storefront/products/HSA-PART-REV-01"),
            1
        )
    }

    func testBundleCardWithoutCountsAndUnknownFormatRawCode() {
        let bundleCard = CatalogProductCard(
            code: "BD-NO-SPEC",
            name: "Bộ đề HSA 1 lượt",
            summary: nil,
            coverUrl: nil,
            examKind: ExamKindInfo(code: "HSA", name: LocalizedLabel(nameVi: "HSA", nameEn: "HSA")),
            format: "BUNDLE",
            scopeLabel: nil,
            buildMode: "STANDARD",
            questionCount: nil,
            durationMinutes: nil,
            attemptsGranted: 1,
            listPrice: 200_000,
            price: 200_000,
            promotion: nil,
            versionNo: 1,
            productType: "BUNDLE"
        )
        let view = CatalogProductCardView(
            product: bundleCard,
            accessCell: .ready(.none(code: bundleCard.code)),
            locale: .vi,
            isSelected: false,
            onOpenDetail: {},
            onAccessButtonTap: { _ in },
            onReloadAccess: {}
        )
        XCTAssertEqual(view.renderedSpecsLine, "1 lượt mỗi bài")
        XCTAssertEqual(view.renderedBadges, ["HSA", "Bộ đề"])

        XCTAssertEqual(CatalogStrings.formatLabel(for: "CUSTOM_NEW_FORMAT", locale: .vi), "CUSTOM_NEW_FORMAT")
        XCTAssertEqual(CatalogStrings.formatLabel(for: "CUSTOM_NEW_FORMAT", locale: .en), "CUSTOM_NEW_FORMAT")
    }

    private func fittedHeight(of cardView: CatalogProductCardView) -> CGFloat {
        let hosted = cardView
            .frame(width: 358)
            .environment(\.brandPalette, .defaultLight)
            .environment(\.statusPalette, .light)
        let (window, controller) = CatalogViewInspector.host(hosted, size: CGSize(width: 390, height: 844), settleSeconds: 0.08)
        defer { window.isHidden = true }
        controller.safeAreaRegions = []
        return controller.sizeThatFits(in: CGSize(width: 358, height: CGFloat.greatestFiniteMagnitude)).height
    }
}
