import Foundation
import HTTPTypes
import OpenAPIRuntime
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class CoverImageTests: XCTestCase {
    func testSingleExamCoverLoadsPublicURLWithoutAuthorizationHeader() async throws {
        let pngData = CatalogTestFixtures.makeTinyPNGData(color: .systemGreen)
        let transport = ContractFakeTransport()
        transport.register(method: .get, path: "/api/v1/public/covers/exam-01.png") { _ in
            var fields = HTTPFields()
            fields[.contentType] = "image/png"
            return (HTTPResponse(status: .ok, headerFields: fields), HTTPBody(pngData))
        }

        let tokenStore = InMemoryTokenStore(
            initialTokens: SessionTokens(
                accessToken: "secret-bearer-token-do-not-send",
                refreshToken: "refresh-token",
                accessExpiresIn: 900,
                refreshExpiresIn: 86_400,
                mustChangePassword: false,
                username: "student.demo",
                accountId: "acc-1"
            )
        )
        let loader = ImageLoader(
            apiBaseURL: URL(string: "https://api.thi360.test")!,
            tokenStore: tokenStore,
            fetcher: ClientTransportImageDataFetcher(transport: transport)
        )

        let card = makeCard(code: "EX-IMG-1", isBundle: false, coverUrl: "/api/v1/public/covers/exam-01.png")
        XCTAssertEqual(card.effectiveCoverURL, "/api/v1/public/covers/exam-01.png")

        let view = ProductCardCoverView(product: card, locale: .vi, imageLoader: loader)
            .frame(width: 320)
            .environment(\.brandPalette, .defaultLight)

        let (window, controller) = CatalogViewInspector.host(view, size: CGSize(width: 320, height: 220), settleSeconds: 0.2)
        defer { window.isHidden = true }

        let requests = transport.requests(method: .get, path: "/api/v1/public/covers/exam-01.png")
        XCTAssertEqual(requests.count, 1)
        XCTAssertNil(
            requests[0].header(.authorization),
            "Public cover image request MUST NOT attach Authorization Bearer header"
        )

        let nodes = CatalogViewInspector.collectNodes(in: controller.view, window: window)
        XCTAssertTrue(nodes.isEmpty, "ProductCardCoverView must expose zero accessibility elements, got: \(nodes)")

        let detailModel = ProductDetailModel(
            code: "EX-IMG-1",
            name: "Sản phẩm EX-IMG-1",
            summary: nil,
            coverUrl: "/api/v1/public/covers/exam-01.png",
            examKind: ExamKindInfo(code: "HSA", name: LocalizedLabel(nameVi: "HSA", nameEn: "HSA")),
            format: "FULL",
            scopeLabel: nil,
            buildMode: "STANDARD",
            questionCount: IntRangeValue(min: 50, max: 50),
            durationMinutes: IntRangeValue(min: 75, max: 75),
            maxPoints: DecimalRangeValue(min: 100, max: 100),
            attemptsGranted: 2,
            listPrice: 200_000,
            price: 150_000,
            promotion: nil,
            versionNo: 1,
            description: nil,
            objectives: nil,
            audience: nil,
            parts: [],
            choices: [],
            scoring: DetailScoringModel(timing: nil, maxScore: 100, referenceMax: nil, practiceScoring: nil),
            omittedEssayGroups: [],
            benefits: DetailBenefitsModel(aiExplanation: false, strengthWeakness: false, practice: false, note: nil),
            resultView: nil,
            productType: "EXAM",
            items: [],
            saleState: nil
        )
        let detailCoverView = ExamDetailCoverView(product: detailModel, locale: .vi, imageLoader: loader)
            .frame(width: 390)
            .environment(\.brandPalette, .defaultLight)
        let (detailWindow, detailController) = CatalogViewInspector.host(
            detailCoverView,
            size: CGSize(width: 390, height: 220),
            settleSeconds: 0.2
        )
        defer { detailWindow.isHidden = true }

        let detailNodes = CatalogViewInspector.collectNodes(in: detailController.view, window: detailWindow)
        XCTAssertTrue(detailNodes.isEmpty, "ExamDetailCoverView must expose zero accessibility elements, got: \(detailNodes)")
    }

    func testBundleWithCoverURLUsesDefaultCoverAndMakesZeroImageRequests() throws {
        let transport = ContractFakeTransport()
        let loader = ImageLoader(
            apiBaseURL: URL(string: "https://api.thi360.test")!,
            tokenStore: InMemoryTokenStore(),
            fetcher: ClientTransportImageDataFetcher(transport: transport)
        )

        let bundleCard = makeCard(
            code: "BD-IMG-1",
            isBundle: true,
            coverUrl: "/api/v1/public/covers/should-never-load.png"
        )
        XCTAssertNil(bundleCard.effectiveCoverURL)

        let coverView = ProductCardCoverView(product: bundleCard, locale: .vi, imageLoader: loader)
        XCTAssertEqual(coverView.defaultBottomLabel, "Bộ đề")

        let view = coverView
            .frame(width: 320)
            .environment(\.brandPalette, .defaultLight)

        let (window, controller) = CatalogViewInspector.host(view, size: CGSize(width: 320, height: 220), settleSeconds: 0.15)
        defer { window.isHidden = true }

        XCTAssertEqual(transport.recordedRequests.count, 0, "Bundle cover MUST make zero image requests")
        XCTAssertTrue(
            CatalogViewInspector.collectNodes(in: controller.view, window: window).isEmpty,
            "Bundle cover must remain decorative (hidden from accessibility)"
        )
        let (_, png) = try CatalogViewInspector.renderPNG(window: window, size: CGSize(width: 320, height: 220))
        XCTAssertGreaterThan(png.count, 2048)
    }

    func testCoverImage404AndNetworkErrorFallbackPreserveFrameSize() {
        let transport = ContractFakeTransport()
        transport.registerEmpty(method: .get, path: "/api/v1/public/covers/missing.png", statusCode: 404)
        transport.registerNetworkError(method: .get, path: "/api/v1/public/covers/offline.png")

        let loader = ImageLoader(
            apiBaseURL: URL(string: "https://api.thi360.test")!,
            tokenStore: InMemoryTokenStore(),
            fetcher: ClientTransportImageDataFetcher(transport: transport)
        )

        let baseCard = makeCard(code: "EX-BASE", isBundle: false, coverUrl: nil)
        let missingCard = makeCard(code: "EX-404", isBundle: false, coverUrl: "/api/v1/public/covers/missing.png")
        let offlineCard = makeCard(code: "EX-NET", isBundle: false, coverUrl: "/api/v1/public/covers/offline.png")

        let baseSize = measureSize(of: ProductCardCoverView(product: baseCard, locale: .vi, imageLoader: loader))
        let missingSize = measureSize(of: ProductCardCoverView(product: missingCard, locale: .vi, imageLoader: loader))
        let offlineSize = measureSize(of: ProductCardCoverView(product: offlineCard, locale: .vi, imageLoader: loader))

        XCTAssertEqual(baseSize.width, 320, accuracy: 1.0)
        XCTAssertEqual(baseSize.height, 180, accuracy: 1.0)
        XCTAssertEqual(missingSize.width, baseSize.width, accuracy: 1.0)
        XCTAssertEqual(missingSize.height, baseSize.height, accuracy: 1.0)
        XCTAssertEqual(offlineSize.width, baseSize.width, accuracy: 1.0)
        XCTAssertEqual(offlineSize.height, baseSize.height, accuracy: 1.0)
    }

    private func measureSize(of coverView: ProductCardCoverView) -> CGSize {
        let hosted = coverView
            .frame(width: 320)
            .environment(\.brandPalette, .defaultLight)
        let (window, controller) = CatalogViewInspector.host(hosted, size: CGSize(width: 320, height: 220), settleSeconds: 0.15)
        defer { window.isHidden = true }
        controller.safeAreaRegions = []
        return controller.sizeThatFits(in: CGSize(width: 320, height: CGFloat.greatestFiniteMagnitude))
    }

    private func makeCard(code: String, isBundle: Bool, coverUrl: String?) -> CatalogProductCard {
        CatalogProductCard(
            code: code,
            name: "Sản phẩm \(code)",
            summary: nil,
            coverUrl: coverUrl,
            examKind: ExamKindInfo(code: "HSA", name: LocalizedLabel(nameVi: "HSA", nameEn: "HSA")),
            format: isBundle ? "BUNDLE" : "FULL",
            scopeLabel: nil,
            buildMode: "STANDARD",
            questionCount: IntRangeValue(min: 50, max: 50),
            durationMinutes: IntRangeValue(min: 75, max: 75),
            attemptsGranted: 2,
            listPrice: 200_000,
            price: 150_000,
            promotion: nil,
            versionNo: 1,
            productType: isBundle ? "BUNDLE" : "EXAM"
        )
    }
}
