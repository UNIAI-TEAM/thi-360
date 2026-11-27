import Foundation
import SwiftUI
import XCTest
@testable import Thi360

@MainActor
final class PriceFormatTests: XCTestCase {
    func testVietnameseDiscountAndPromotionDateAcrossTimeZones() throws {
        let original = NSTimeZone.default
        defer { NSTimeZone.default = original }

        let endsAt = FlexibleISO8601DateTranscoder.parse("2026-12-31T17:30:00Z")!

        for tzIdentifier in ["UTC", "America/New_York"] {
            NSTimeZone.default = TimeZone(identifier: tzIdentifier)!
            XCTAssertEqual(
                CatalogFormatting.formatDayMonth(endsAt),
                "01/01",
                "Promotion date must always format in Asia/Ho_Chi_Minh regardless of device TZ \(tzIdentifier)"
            )
        }

        let priceView = ProductPriceView(
            listPrice: 200_000,
            price: 150_000,
            promotion: PromotionInfo(name: "Khuyến mãi", endsAt: endsAt),
            locale: .vi
        )

        XCTAssertEqual(priceView.renderedListPriceText, "200.000 đ")
        XCTAssertEqual(priceView.renderedListPriceAccessibilityLabel, "Giá gốc 200.000 đ")
        XCTAssertEqual(priceView.renderedDiscountText, "Giảm 25%")
        XCTAssertEqual(priceView.renderedSalePriceText, "150.000 đ")
        XCTAssertEqual(priceView.renderedPromoEndsText, "Kết thúc 01/01")

        let (window, _) = CatalogViewInspector.host(
            priceView
                .padding(16)
                .environment(\.brandPalette, .defaultLight)
                .environment(\.statusPalette, .light),
            size: CGSize(width: 350, height: 140)
        )
        defer { window.isHidden = true }
        let (_, png) = try CatalogViewInspector.renderPNG(window: window, size: CGSize(width: 350, height: 140))
        XCTAssertGreaterThan(png.count, 1024)
    }

    func testSmallDiscountClampsToMinimumOnePercentAndEqualPricesHideDiscount() throws {
        let endsAt = FlexibleISO8601DateTranscoder.parse("2026-12-31T17:30:00Z")!

        let tinyDiscountView = ProductPriceView(
            listPrice: 1_000,
            price: 999,
            promotion: PromotionInfo(name: "Khuyến mãi", endsAt: endsAt),
            locale: .vi
        )
        XCTAssertEqual(tinyDiscountView.renderedDiscountText, "Giảm 1%")

        let equalPriceView = ProductPriceView(
            listPrice: 200_000,
            price: 200_000,
            promotion: PromotionInfo(name: "Khuyến mãi", endsAt: endsAt),
            locale: .vi
        )
        XCTAssertNil(equalPriceView.discountPercent)
        XCTAssertNil(equalPriceView.renderedListPriceText)
        XCTAssertNil(equalPriceView.renderedListPriceAccessibilityLabel)
        XCTAssertNil(equalPriceView.renderedDiscountText)
        XCTAssertNil(equalPriceView.renderedPromoEndsText)
        XCTAssertEqual(equalPriceView.renderedSalePriceText, "200.000 đ")

        let discountedView = ProductPriceView(
            listPrice: 200_000,
            price: 150_000,
            promotion: PromotionInfo(name: "Khuyến mãi", endsAt: endsAt),
            locale: .vi
        )
        let (w1, _) = CatalogViewInspector.host(
            equalPriceView.padding(16).environment(\.brandPalette, .defaultLight).environment(\.statusPalette, .light),
            size: CGSize(width: 350, height: 140)
        )
        let (_, eqPNG) = try CatalogViewInspector.renderPNG(window: w1, size: CGSize(width: 350, height: 140))
        w1.isHidden = true

        let (w2, _) = CatalogViewInspector.host(
            discountedView.padding(16).environment(\.brandPalette, .defaultLight).environment(\.statusPalette, .light),
            size: CGSize(width: 350, height: 140)
        )
        defer { w2.isHidden = true }
        let (_, discPNG) = try CatalogViewInspector.renderPNG(window: w2, size: CGSize(width: 350, height: 140))
        XCTAssertNotEqual(eqPNG, discPNG)
    }

    func testZeroPriceAndEnglishFormatting() {
        XCTAssertEqual(CatalogFormatting.formatPrice(0, locale: .vi), "0 đ")
        XCTAssertEqual(CatalogFormatting.formatPrice(0, locale: .en), "0 VND")
        XCTAssertEqual(CatalogFormatting.formatPrice(150_000, locale: .en), "150,000 VND")
        XCTAssertEqual(CatalogFormatting.formatPrice(200_000, locale: .en), "200,000 VND")
    }
}
