import Foundation
import HTTPTypes
import OpenAPIRuntime
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class CatalogSnapshotTests: XCTestCase {
    private struct Viewport {
        let name: String
        let size: CGSize
        let sizeClass: UIUserInterfaceSizeClass
        let shellWidth: ShellWidth
    }

    private let viewports: [Viewport] = [
        Viewport(name: "iphone-390x844", size: CGSize(width: 390, height: 844), sizeClass: .compact, shellWidth: .narrow),
        Viewport(name: "ipad-landscape-1180x820", size: CGSize(width: 1180, height: 820), sizeClass: .regular, shellWidth: .twoColumn),
        Viewport(name: "ipad-portrait-820x1180", size: CGSize(width: 820, height: 1180), sizeClass: .regular, shellWidth: .twoColumn),
        Viewport(name: "duo-folded-466x678", size: CGSize(width: 466, height: 678), sizeClass: .compact, shellWidth: .narrow),
        Viewport(name: "duo-unfolded-669x951", size: CGSize(width: 669, height: 951), sizeClass: .regular, shellWidth: .twoColumn)
    ]

    func testCatalogAndDetailSnapshotMatrixLightAndDark() async throws {
        let pngData = CatalogTestFixtures.makeTinyPNGData(color: .systemTeal)
        let examData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.getStorefrontProduct.id,
            status: 200,
            variant: "exam"
        )
        let bundleData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.getStorefrontProduct.id,
            status: 200,
            variant: "bundle"
        )

        let stack = try CatalogTestStackFactory.makeStack { transport in
            try CatalogTestStackFactory.registerDefaultStorefrontFixtures(on: transport)
            transport.registerJSON(method: .get, path: "/api/v1/public/storefront/products/EXAM-DEMO-01", data: examData)
            transport.registerJSON(method: .get, path: "/api/v1/public/storefront/products/BUNDLE-DEMO-01", data: bundleData)
            transport.register(method: .get, path: "/api/v1/public/covers/exam.png") { _ in
                var fields = HTTPFields()
                fields[.contentType] = "image/png"
                return (HTTPResponse(status: .ok, headerFields: fields), HTTPBody(pngData))
            }
        }
        defer {
            UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName)
        }

        let imageLoader = ImageLoader(
            apiBaseURL: URL(string: "https://api.thi360.test")!,
            tokenStore: stack.tokenStore,
            fetcher: ClientTransportImageDataFetcher(transport: stack.transport)
        )

        let feed = CatalogFeed(accountId: "acc-1", api: stack.api)
        await feed.loadInitialIfNeeded()

        let decoder = MobileAPIClient.makeJSONDecoder()
        let examGenerated = try decoder.decode(Components.Schemas.ProductDetail.self, from: examData)
        let examWithoutCover = CatalogAPI.mapProductDetail(examGenerated, supplementalJSONData: examData)
        let examWithCover = ProductDetailModel(
            code: examWithoutCover.code,
            name: examWithoutCover.name,
            summary: examWithoutCover.summary,
            coverUrl: "/api/v1/public/covers/exam.png",
            examKind: examWithoutCover.examKind,
            format: examWithoutCover.format,
            scopeLabel: examWithoutCover.scopeLabel,
            buildMode: examWithoutCover.buildMode,
            questionCount: examWithoutCover.questionCount,
            durationMinutes: examWithoutCover.durationMinutes,
            maxPoints: examWithoutCover.maxPoints,
            attemptsGranted: examWithoutCover.attemptsGranted,
            listPrice: examWithoutCover.listPrice,
            price: examWithoutCover.price,
            promotion: examWithoutCover.promotion,
            versionNo: examWithoutCover.versionNo,
            description: examWithoutCover.description,
            objectives: examWithoutCover.objectives,
            audience: examWithoutCover.audience,
            parts: examWithoutCover.parts,
            choices: examWithoutCover.choices,
            scoring: examWithoutCover.scoring,
            omittedEssayGroups: examWithoutCover.omittedEssayGroups,
            benefits: examWithoutCover.benefits,
            resultView: examWithoutCover.resultView,
            productType: examWithoutCover.productType,
            items: [],
            saleState: nil
        )
        let bundleGenerated = try decoder.decode(Components.Schemas.ProductDetail.self, from: bundleData)
        let bundleDetail = CatalogAPI.mapProductDetail(bundleGenerated, supplementalJSONData: bundleData)
        let router = AppRouter(initialDestination: .home)

        for viewport in viewports {
            try assertLightAndDarkSnapshots(
                prefix: "catalog-\(viewport.name)",
                viewport: viewport
            ) {
                CatalogHomeView(
                    feed: feed,
                    router: router,
                    locale: .vi,
                    showsBrandHeader: viewport.shellWidth == .narrow,
                    imageLoader: imageLoader
                )
            }
        }

        for viewport in viewports {
            try assertLightAndDarkSnapshots(
                prefix: "exam-detail-nocover-\(viewport.name)",
                viewport: viewport
            ) {
                ScrollView {
                    ExamDetailView(
                        product: examWithoutCover,
                        accessCell: .ready(.none(code: examWithoutCover.code)),
                        locale: .vi,
                        supportContact: .empty,
                        imageLoader: imageLoader,
                        onAccessButtonTap: { _ in },
                        onReloadAccess: {}
                    )
                    .padding(16)
                }
            }

            try assertLightAndDarkSnapshots(
                prefix: "exam-detail-cover-\(viewport.name)",
                viewport: viewport
            ) {
                ScrollView {
                    ExamDetailView(
                        product: examWithCover,
                        accessCell: .ready(.none(code: examWithCover.code)),
                        locale: .vi,
                        supportContact: .empty,
                        imageLoader: imageLoader,
                        onAccessButtonTap: { _ in },
                        onReloadAccess: {}
                    )
                    .padding(16)
                }
            }
        }

        for viewport in viewports {
            try assertLightAndDarkSnapshots(
                prefix: "bundle-detail-\(viewport.name)",
                viewport: viewport
            ) {
                ScrollView {
                    BundleDetailView(
                        product: bundleDetail,
                        sellable: true,
                        accessCell: .ready(.none(code: bundleDetail.code)),
                        locale: .vi,
                        supportContact: .empty,
                        onOpenExam: { _ in },
                        onBuyBundle: {},
                        onReloadAccess: {}
                    )
                    .padding(16)
                }
            }
        }

        for viewport in viewports {
            try assertLightAndDarkSnapshots(prefix: "status-410-\(viewport.name)", viewport: viewport) {
                ScrollView {
                    ExamGoneView(
                        code: "EXAM-OLD-01",
                        name: "Đề thi cũ",
                        locale: .vi,
                        onReturnToCatalog: {},
                        onOpenMyExams: {}
                    )
                    .padding(16)
                }
            }
            try assertLightAndDarkSnapshots(prefix: "status-404-\(viewport.name)", viewport: viewport) {
                ScrollView {
                    ExamNotFoundView(locale: .vi, onReturnToCatalog: {})
                        .padding(16)
                }
            }
            try assertLightAndDarkSnapshots(prefix: "status-409-\(viewport.name)", viewport: viewport) {
                ScrollView {
                    BundleUnavailableView(locale: .vi, onReturnToCatalog: {})
                        .padding(16)
                }
            }
        }
    }

    private func assertLightAndDarkSnapshots<Content: View>(
        prefix: String,
        viewport: Viewport,
        @ViewBuilder content: () -> Content
    ) throws {
        let lightPNG = try captureSnapshot(
            name: "\(prefix)-light",
            viewport: viewport,
            style: .light,
            palette: .defaultLight,
            statusPalette: .light,
            content: content()
        )
        let darkPNG = try captureSnapshot(
            name: "\(prefix)-dark",
            viewport: viewport,
            style: .dark,
            palette: .defaultDark,
            statusPalette: .dark,
            content: content()
        )
        XCTAssertNotEqual(lightPNG, darkPNG, "Light and dark snapshots for \(prefix) must differ visually")
    }

    private func captureSnapshot<Content: View>(
        name: String,
        viewport: Viewport,
        style: UIUserInterfaceStyle,
        palette: BrandPalette,
        statusPalette: StatusPalette,
        content: Content
    ) throws -> Data {
        let wrapped = content
            .background(palette.bgPageColor.ignoresSafeArea())
            .environment(\.brandPalette, palette)
            .environment(\.statusPalette, statusPalette)
            .environment(\.shellWidth, viewport.shellWidth)

        let (window, controller) = CatalogViewInspector.host(
            wrapped,
            size: viewport.size,
            style: style,
            sizeClass: viewport.sizeClass,
            settleSeconds: 0.08
        )
        defer { window.isHidden = true }

        let scrollViews = RecoveryViewTestHelper.findViews(ofType: UIScrollView.self, in: controller.view)
        for scrollView in scrollViews where scrollView.contentSize.width > 0 {
            XCTAssertLessThanOrEqual(
                scrollView.contentSize.width,
                viewport.size.width + 1.0,
                "\(name) UIScrollView contentSize.width (\(scrollView.contentSize.width)) overflows \(viewport.size.width)"
            )
        }

        let (image, png) = try CatalogViewInspector.renderPNG(window: window, size: viewport.size)
        XCTAssertGreaterThan(png.count, 2048, "Snapshot \(name) unexpectedly empty")

        let attachment = XCTAttachment(image: image)
        attachment.name = "\(name).png"
        attachment.lifetime = .keepAlways
        add(attachment)

        let env = ProcessInfo.processInfo.environment
        let outputDir = [env["TEST_RUNNER_THI360_SNAPSHOT_DIR"], env["THI360_SNAPSHOT_DIR"]]
            .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .first(where: { !$0.isEmpty })

        if let outputDir {
            let dirURL = URL(fileURLWithPath: outputDir, isDirectory: true)
            try FileManager.default.createDirectory(at: dirURL, withIntermediateDirectories: true)
            try png.write(to: dirURL.appendingPathComponent("\(name).png"))
        }

        return png
    }
}
