import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class ResultDynamicTypeTests: XCTestCase {
    private let phoneSize = CGSize(width: 390, height: 844)
    private let scales: [(label: String, uiCategory: UIContentSizeCategory, dynamicType: DynamicTypeSize)] = [
        ("130%", .extraExtraExtraLarge, .xxxLarge),
        ("200%", .accessibilityLarge, .accessibility2)
    ]

    private func makeRepository() throws -> ResultRepository {
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
        return ResultRepository(
            api: OpenAPIResultAPI(apiClient: apiClient),
            catalogOptionsCache: CatalogOptionsCache(apiClient: apiClient)
        )
    }

    func testResultAndReviewDynamicType130And200TouchTargetsAndVoiceOverLabels() throws {
        let repo = try makeRepository()
        let examOptions = try ResultTestFixtures.decodeCatalogOptions(variant: "exam")
        let subjectOptions = try ResultTestFixtures.decodeCatalogOptions(variant: "subject")

        let scoredResult = try ResultTestFixtures.decodeResult(variant: "scored")
        let scoredVM = ResultViewModel(attemptId: scoredResult.attemptId, repository: repo)
        scoredVM.setStateForTesting(loadState: .loaded(value: scoredResult, stale: false), examOptions: examOptions, subjectOptions: subjectOptions)

        let vactResult = try ResultTestFixtures.decodeResult(variant: "vact")
        let vactVM = ResultViewModel(attemptId: vactResult.attemptId, repository: repo)
        vactVM.setStateForTesting(loadState: .loaded(value: vactResult, stale: false), examOptions: examOptions, subjectOptions: subjectOptions)

        let fullModel = ReviewMapper.map(
            try ResultTestFixtures.decodeReviewPayload(variant: "full-solution"),
            resultSummary: scoredResult
        )
        let reviewVM = ReviewViewModel(
            attemptId: fullModel.attemptId,
            repository: repo,
            assetSource: ResultViewTestHelper.makeOfflineAssetSource(attemptId: fullModel.attemptId)
        )
        reviewVM.setModelForTesting(fullModel)

        for scale in scales {
            try verifyDynamicType(
                screen: "ResultScored@\(scale.label)",
                scale: scale,
                expectedButtonIDs: ["result.actions.reviewButton", "result.actions.myExamsButton", "result.actions.historyButton"],
                view: ResultView(
                    viewModel: scoredVM,
                    locale: .vi,
                    onOpenReview: { _ in },
                    onContinueAttempt: { _ in },
                    onGoMyExams: {},
                    onGoHistory: {},
                    autoLoadOnAppear: false
                )
            )

            try verifyDynamicType(
                screen: "ResultVact@\(scale.label)",
                scale: scale,
                expectedButtonIDs: ["result.vact.component.SCIENTIFIC_THINKING.toggleSubsections"],
                view: ResultView(
                    viewModel: vactVM,
                    locale: .vi,
                    onOpenReview: { _ in },
                    onContinueAttempt: { _ in },
                    onGoMyExams: {},
                    onGoHistory: {},
                    autoLoadOnAppear: false
                )
            )

            try verifyDynamicType(
                screen: "ReviewFullSolution@\(scale.label)",
                scale: scale,
                expectedButtonIDs: ["review.palette.prevButton", "review.palette.nextButton"],
                allowHorizontalStrips: true,
                view: ReviewView(
                    viewModel: reviewVM,
                    locale: .vi,
                    onBackToResult: {},
                    onGoMyExams: {},
                    onGoHistory: {},
                    autoLoadOnAppear: false
                )
            )
        }
    }

    private func verifyDynamicType<Content: View>(
        screen: String,
        scale: (label: String, uiCategory: UIContentSizeCategory, dynamicType: DynamicTypeSize),
        expectedButtonIDs: [String],
        allowHorizontalStrips: Bool = false,
        view: Content
    ) throws {
        let sink = ResultInspectionSink()
        let wrapped = view
            .environment(\.brandPalette, .defaultLight)
            .environment(\.statusPalette, .light)
            .environment(\.shellWidth, .narrow)
            .environment(\.dynamicTypeSize, scale.dynamicType)
            .environment(\.resultInspector, sink)

        let (window, controller) = CatalogViewInspector.host(
            wrapped,
            size: phoneSize,
            uiCategory: scale.uiCategory,
            settleSeconds: 0.12
        )
        defer { window.isHidden = true }

        for buttonID in expectedButtonIDs {
            let node = try XCTUnwrap(sink.nodes[buttonID], "\(screen) missing expected control '\(buttonID)'")
            XCTAssertFalse(node.label.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, "\(screen) '\(buttonID)' must have a VoiceOver label")
            XCTAssertGreaterThanOrEqual(node.frame.width, 43.5, "\(screen) '\(buttonID)' width \(node.frame.width)pt < 44pt")
            XCTAssertGreaterThanOrEqual(node.frame.height, 43.5, "\(screen) '\(buttonID)' height \(node.frame.height)pt < 44pt")
            XCTAssertGreaterThanOrEqual(node.frame.minX, -1.5, "\(screen) '\(buttonID)' overflows left")
            XCTAssertLessThanOrEqual(node.frame.maxX, phoneSize.width + 1.5, "\(screen) '\(buttonID)' overflows right (\(node.frame.maxX)pt)")
        }

        for node in sink.nodes.values where node.isButton {
            XCTAssertFalse(node.label.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, "\(screen) button '\(node.identifier)' missing accessible label")
            if node.frame.height > 0 {
                XCTAssertGreaterThanOrEqual(node.frame.height, 43.5, "\(screen) button '\(node.identifier)' height \(node.frame.height)pt < 44pt")
            }
        }

        let scrollViews = RecoveryViewTestHelper.findViews(ofType: UIScrollView.self, in: controller.view)
        XCTAssertFalse(scrollViews.isEmpty, "\(screen) must remain vertically scrollable")
        for scrollView in scrollViews where scrollView.contentSize.width > 0 && scrollView.bounds.width > 0 {
            let isHorizontalStrip = allowHorizontalStrips
                && scrollView.contentSize.height <= scrollView.bounds.height + 2.0
                && scrollView.bounds.height < 140
            if !isHorizontalStrip {
                XCTAssertLessThanOrEqual(
                    scrollView.contentSize.width,
                    phoneSize.width + 1.5,
                    "\(screen) vertical UIScrollView overflows horizontally (\(scrollView.contentSize.width)pt)"
                )
            }
        }

        let (_, png) = try CatalogViewInspector.renderPNG(window: window, size: phoneSize)
        XCTAssertGreaterThan(png.count, 2048, "\(screen) snapshot unexpectedly empty")
    }
}
