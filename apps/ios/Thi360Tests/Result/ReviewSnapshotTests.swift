import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class ReviewSnapshotTests: XCTestCase {
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

    private func makeViewModel(model: ReviewModel, selectedPartIndex: Int = 0) throws -> ReviewViewModel {
        let (_, _, transport, preferences) = try SessionTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        )
        let tokenStore = InMemoryTokenStore(initialTokens: AccountTestFactory.sampleTokens())
        let apiClient = MobileAPIClient(
            serverURL: URL(string: "https://api.thi360.test")!,
            tokenStore: tokenStore,
            transport: transport,
            localeProvider: { await MainActor.run { preferences.locale } }
        )
        let repo = ResultRepository(
            api: OpenAPIResultAPI(apiClient: apiClient),
            catalogOptionsCache: CatalogOptionsCache(apiClient: apiClient)
        )
        let vm = ReviewViewModel(
            attemptId: model.attemptId,
            repository: repo,
            assetSource: ResultViewTestHelper.makeOfflineAssetSource(attemptId: model.attemptId)
        )
        vm.setModelForTesting(model, selectedPartIndex: selectedPartIndex)
        return vm
    }

    func testReviewSnapshotMatrixLightAndDark() throws {
        let scoredResult = try ResultTestFixtures.decodeResult(variant: "scored")
        let pendingResult = try ResultTestFixtures.decodeResult(variant: "pending-review")

        let fullSolution = ReviewMapper.map(
            try ResultTestFixtures.decodeReviewPayload(variant: "full-solution"),
            resultSummary: scoredResult
        )
        let scoreAndAnswers = ReviewMapper.map(
            try ResultTestFixtures.decodeReviewPayload(variant: "score-and-answers"),
            resultSummary: scoredResult
        )
        let pendingReview = ReviewMapper.map(
            try ResultTestFixtures.decodeReviewPayload(variant: "pending-review"),
            resultSummary: pendingResult
        )

        let states: [(String, ReviewModel, Int)] = [
            ("full-solution", fullSolution, 0),
            ("score-and-answers", scoreAndAnswers, 0),
            ("pending-review", pendingReview, 1)
        ]

        for (stateName, model, partIndex) in states {
            let vm = try makeViewModel(model: model, selectedPartIndex: partIndex)
            for viewport in viewports {
                let prefix = "review-\(stateName)-\(viewport.name)"
                let lightPNG = try captureSnapshot(
                    name: "\(prefix)-light",
                    viewport: viewport,
                    style: .light,
                    palette: .defaultLight,
                    statusPalette: .light,
                    viewModel: vm
                )
                let darkPNG = try captureSnapshot(
                    name: "\(prefix)-dark",
                    viewport: viewport,
                    style: .dark,
                    palette: .defaultDark,
                    statusPalette: .dark,
                    viewModel: vm
                )
                XCTAssertNotEqual(lightPNG, darkPNG, "Light and dark snapshots for \(prefix) must differ")
            }
        }
    }

    private func captureSnapshot(
        name: String,
        viewport: Viewport,
        style: UIUserInterfaceStyle,
        palette: BrandPalette,
        statusPalette: StatusPalette,
        viewModel: ReviewViewModel
    ) throws -> Data {
        let content = ReviewView(
            viewModel: viewModel,
            locale: .vi,
            onBackToResult: {},
            onGoMyExams: {},
            onGoHistory: {},
            autoLoadOnAppear: false
        )
        .environment(\.brandPalette, palette)
        .environment(\.statusPalette, statusPalette)
        .environment(\.shellWidth, viewport.shellWidth)

        let (window, controller) = CatalogViewInspector.host(
            content,
            size: viewport.size,
            style: style,
            sizeClass: viewport.sizeClass,
            settleSeconds: 0.1
        )
        defer { window.isHidden = true }

        let scrollViews = RecoveryViewTestHelper.findViews(ofType: UIScrollView.self, in: controller.view)
        for scrollView in scrollViews where scrollView.contentSize.width > 0 && scrollView.bounds.width > 0 {
            let isHorizontalStrip = scrollView.contentSize.height <= scrollView.bounds.height + 2.0
                && scrollView.bounds.height < 120
            if !isHorizontalStrip {
                XCTAssertLessThanOrEqual(
                    scrollView.contentSize.width,
                    scrollView.bounds.width + 1.5,
                    "\(name) vertical UIScrollView contentSize.width (\(scrollView.contentSize.width)) overflows bounds (\(scrollView.bounds.width))"
                )
            }
        }

        let (image, png) = try CatalogViewInspector.renderPNG(window: window, size: viewport.size)
        XCTAssertGreaterThan(png.count, 2048, "Snapshot \(name) unexpectedly empty")

        let attachment = XCTAttachment(image: image)
        attachment.name = "\(name).png"
        attachment.lifetime = .keepAlways
        add(attachment)

        writeSnapshotIfConfigured(png: png, name: name)
        return png
    }

    private func writeSnapshotIfConfigured(png: Data, name: String) {
        let env = ProcessInfo.processInfo.environment
        let outputDir = [env["TEST_RUNNER_THI360_SNAPSHOT_DIR"], env["THI360_SNAPSHOT_DIR"]]
            .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .first(where: { !$0.isEmpty })
        guard let outputDir else { return }
        let dirURL = URL(fileURLWithPath: outputDir, isDirectory: true)
        try? FileManager.default.createDirectory(at: dirURL, withIntermediateDirectories: true)
        try? png.write(to: dirURL.appendingPathComponent("\(name).png"))
    }
}
