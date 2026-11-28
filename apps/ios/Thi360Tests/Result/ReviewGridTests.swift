import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class ReviewGridTests: XCTestCase {
    private func makeViewModel(model: ReviewModel) throws -> ReviewViewModel {
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
        vm.setModelForTesting(model)
        return vm
    }

    func testNarrowPaletteSummaryNavigationSelectionAndLegend() throws {
        let payload = try ResultTestFixtures.decodeReviewPayload(variant: "score-and-answers")
        let model = ReviewMapper.map(payload)
        XCTAssertEqual(model.groups.count, 3)

        let summary = ReviewModel.paletteSummary(for: model.groups)
        XCTAssertEqual(summary.correct, 1)
        XCTAssertEqual(summary.partial, 0)
        XCTAssertEqual(summary.wrong, 0)
        XCTAssertEqual(summary.blank, 4, "Question set children (3) + standalone blank (1) must be counted individually")

        let vm = try makeViewModel(model: model)
        let (window, _, sink, _) = ResultViewTestHelper.host(
            ReviewView(
                viewModel: vm,
                locale: .vi,
                onBackToResult: {},
                onGoMyExams: {},
                onGoHistory: {},
                autoLoadOnAppear: false
            ),
            size: CGSize(width: 390, height: 844),
            sizeClass: .compact,
            shellWidth: .narrow
        )
        defer { window.isHidden = true }

        XCTAssertNotNil(sink.nodes["review.layout.narrow"])
        XCTAssertNil(sink.nodes["review.layout.wide"])
        XCTAssertEqual(sink.nodes["review.palette.title"]?.label, "Danh sách câu")
        XCTAssertEqual(
            sink.nodes["review.palette.summary"]?.label,
            "Đúng 1 · Đúng một phần 0 · Sai 0 · Bỏ trống 4"
        )
        let legend = try XCTUnwrap(sink.nodes["review.palette.legend"]?.label)
        XCTAssertTrue(legend.contains("đúng"))
        XCTAssertTrue(legend.contains("đúng một phần"))
        XCTAssertTrue(legend.contains("sai"))
        XCTAssertTrue(legend.contains("bỏ trống"))

        let firstGroup = model.groups[0]
        let secondGroup = model.groups[1]
        let setGroup = model.groups[2]

        XCTAssertEqual(sink.nodes["review.palette.cell.\(firstGroup.id)"]?.value, "1")
        XCTAssertEqual(sink.nodes["review.palette.cell.\(firstGroup.id)"]?.label, "Câu 1, đúng")
        XCTAssertEqual(sink.nodes["review.palette.cell.\(firstGroup.id)"]?.isSelected, true)
        XCTAssertEqual(sink.nodes["review.group.\(firstGroup.id)"]?.value, "active")

        XCTAssertEqual(sink.nodes["review.palette.cell.\(setGroup.id)"]?.value, "3–5")
        XCTAssertEqual(sink.nodes["review.palette.cell.\(setGroup.id)"]?.label, "Câu 3–5, bỏ trống")

        XCTAssertEqual(sink.nodes["review.palette.prevButton"]?.isEnabled, false)
        XCTAssertEqual(sink.nodes["review.palette.nextButton"]?.isEnabled, true)

        XCTAssertTrue(sink.performAction(for: "review.palette.nextButton"))
        RunLoop.main.run(until: Date().addingTimeInterval(0.08))
        XCTAssertEqual(vm.activeGroupId, secondGroup.id)
        XCTAssertEqual(sink.nodes["review.palette.prevButton"]?.isEnabled, true)
        XCTAssertEqual(sink.nodes["review.palette.nextButton"]?.isEnabled, true)

        XCTAssertTrue(sink.performAction(for: "review.palette.cell.\(setGroup.id)"))
        RunLoop.main.run(until: Date().addingTimeInterval(0.08))
        XCTAssertEqual(vm.activeGroupId, setGroup.id)
        XCTAssertEqual(sink.nodes["review.palette.cell.\(setGroup.id)"]?.isSelected, true)
        XCTAssertEqual(sink.nodes["review.palette.prevButton"]?.isEnabled, true)
        XCTAssertEqual(sink.nodes["review.palette.nextButton"]?.isEnabled, false)
    }

    func testWideTwoPaneLayoutOnIpadAndDuoUnfolded() throws {
        let payload = try ResultTestFixtures.decodeReviewPayload(variant: "full-solution")
        let model = ReviewMapper.map(payload)
        let firstGroup = try XCTUnwrap(model.groups.first)

        let wideSizes: [(String, CGSize)] = [
            ("iPad-landscape", CGSize(width: 1180, height: 820)),
            ("iPad-portrait", CGSize(width: 820, height: 1180)),
            ("Duo-unfolded", CGSize(width: 669, height: 951)),
            ("Duo-unfolded-585", CGSize(width: 585, height: 951))
        ]

        for (label, size) in wideSizes {
            let vm = try makeViewModel(model: model)
            let (window, controller, sink, _) = ResultViewTestHelper.host(
                ReviewView(
                    viewModel: vm,
                    locale: .vi,
                    onBackToResult: {},
                    onGoMyExams: {},
                    onGoHistory: {},
                    autoLoadOnAppear: false
                ),
                size: size,
                sizeClass: .regular,
                shellWidth: .twoColumn
            )
            defer { window.isHidden = true }

            XCTAssertNotNil(sink.nodes["review.layout.wide"], "\(label) must use two-pane layout")
            XCTAssertNil(sink.nodes["review.layout.narrow"], "\(label) must not use narrow bar")

            let paletteNode = try XCTUnwrap(sink.nodes["review.palette.cell.\(firstGroup.id)"], "\(label) missing palette cell")
            let questionCardNode = try XCTUnwrap(sink.nodes["review.group.\(firstGroup.id)"], "\(label) missing question card")
            XCTAssertLessThan(
                paletteNode.frame.maxX,
                questionCardNode.frame.minX + 1.0,
                "\(label) palette sidebar must sit beside question list"
            )

            let scrollViews = RecoveryViewTestHelper.findViews(ofType: UIScrollView.self, in: controller.view)
            XCTAssertGreaterThanOrEqual(scrollViews.count, 2, "\(label) sidebar and question list must scroll independently")
        }
    }
}
