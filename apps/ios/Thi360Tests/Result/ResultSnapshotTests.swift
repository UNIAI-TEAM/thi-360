import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class ResultSnapshotTests: XCTestCase {
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

    private func makeViewModel(
        result: AttemptResult,
        examOptions: [String: CatalogOptionItem],
        subjectOptions: [String: CatalogOptionItem]
    ) throws -> ResultViewModel {
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
        let vm = ResultViewModel(attemptId: result.attemptId, repository: repo)
        vm.setStateForTesting(loadState: .loaded(value: result, stale: false), examOptions: examOptions, subjectOptions: subjectOptions)
        return vm
    }

    func testResultSnapshotMatrixLightAndDark() throws {
        let examOptions = try ResultTestFixtures.decodeCatalogOptions(variant: "exam")
        let subjectOptions = try ResultTestFixtures.decodeCatalogOptions(variant: "subject")

        let scored = try ResultTestFixtures.decodeResult(variant: "scored")
        let grading = try ResultTestFixtures.decodeResult(variant: "grading")
        let pendingReview = try ResultTestFixtures.decodeResult(variant: "pending-review")
        let regrading = try ResultTestFixtures.decodeResult(variant: "regrading")
        let gradingError = try ResultTestFixtures.decodeResult(variant: "grading-error")
        let vact = try ResultTestFixtures.decodeResult(variant: "vact")
        let breakdown = try ResultTestFixtures.decodeResult(variant: "scored") { root in
            let segStats: [String: Any] = [
                "correctCount": 40,
                "partialCount": 0,
                "wrongCount": 8,
                "blankCount": 2,
                "pendingReviewCount": 0,
                "totalCount": 50
            ]
            let segment: [String: Any] = [
                "partCode": "P1",
                "code": "SEG1",
                "name": "Toán học",
                "fromPosition": 1,
                "toPosition": 50,
                "score": 40.0,
                "maxPoints": 50.0,
                "pendingReview": false,
                "stats": segStats
            ]
            let segments: [[String: Any]] = [segment]
            root["segments"] = segments

            let subStats: [String: Any] = [
                "correctCount": 40,
                "partialCount": 0,
                "wrongCount": 8,
                "blankCount": 2,
                "pendingReviewCount": 0,
                "totalCount": 50
            ]
            let subject: [String: Any] = [
                "code": "TOAN",
                "name": "Toán học",
                "score": 40.0,
                "maxScore": 50.0,
                "pendingReview": false,
                "stats": subStats
            ]
            let subjects: [[String: Any]] = [subject]
            root["subjects"] = subjects
        }
        XCTAssertEqual(breakdown.segments.first?.fromPosition, 1)
        XCTAssertEqual(breakdown.segments.first?.toPosition, 50)
        XCTAssertEqual(breakdown.segments.first?.maxPoints, 50.0)
        XCTAssertEqual(breakdown.segments.first?.stats?.correctCount, 40)
        XCTAssertEqual(breakdown.subjects.first?.stats?.correctCount, 40)

        let states: [(String, AttemptResult)] = [
            ("scored", scored),
            ("grading", grading),
            ("pending-review", pendingReview),
            ("regrading", regrading),
            ("grading-error", gradingError),
            ("vact", vact),
            ("breakdown", breakdown)
        ]

        for (stateName, result) in states {
            let vm = try makeViewModel(
                result: result,
                examOptions: examOptions,
                subjectOptions: subjectOptions
            )
            for viewport in viewports {
                let prefix = "result-\(stateName)-\(viewport.name)"
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
        viewModel: ResultViewModel
    ) throws -> Data {
        let content = ResultView(
            viewModel: viewModel,
            locale: .vi,
            onOpenReview: { _ in },
            onContinueAttempt: { _ in },
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
