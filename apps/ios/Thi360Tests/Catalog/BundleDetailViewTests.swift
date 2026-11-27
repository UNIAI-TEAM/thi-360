import Foundation
import SwiftUI
import XCTest
@testable import Thi360

@MainActor
final class BundleDetailViewTests: XCTestCase {
    func testBundleFourItemsPerItemAccessChipsAndBuyPanel() throws {
        let items: [BundleDetailItemModel] = [
            makeBundleItem(id: "id-1", code: "EX-TOAN", name: "Toán", scope: "SUBJECT", subjectVi: "Toán", subjectEn: "Math"),
            makeBundleItem(id: "id-2", code: "EX-ANH", name: "Anh", scope: "SUBJECT", subjectVi: "Anh", subjectEn: "English"),
            makeBundleItem(id: "id-3", code: "EX-VAN", name: "Văn", scope: "SUBJECT", subjectVi: "Văn", subjectEn: "Literature"),
            makeBundleItem(id: "id-4", code: "EX-LY", name: "Lý", scope: "PART", subjectVi: "Vật lý", subjectEn: "Physics", partCode: "P2")
        ]

        XCTAssertEqual(CatalogFormatting.bundleItemSubtitle(for: items[0], locale: .vi), "Toán · Bài thi theo môn")

        let bundle = ProductDetailModel(
            code: "BUNDLE-4",
            name: "Bộ đề 4 môn HSA",
            summary: "Tóm tắt bộ đề",
            coverUrl: "https://api.thi360.test/api/v1/public/ignore-bundle-cover.png",
            examKind: ExamKindInfo(code: "HSA", name: LocalizedLabel(nameVi: "HSA", nameEn: "HSA")),
            format: "BUNDLE",
            scopeLabel: nil,
            buildMode: "STANDARD",
            questionCount: IntRangeValue(min: 160, max: 160),
            durationMinutes: IntRangeValue(min: 240, max: 240),
            maxPoints: DecimalRangeValue(min: 200, max: 200),
            attemptsGranted: 2,
            listPrice: 400_000,
            price: 320_000,
            promotion: nil,
            versionNo: 2,
            description: nil,
            objectives: nil,
            audience: nil,
            parts: [],
            choices: [],
            scoring: DetailScoringModel(timing: nil, maxScore: nil, referenceMax: nil, practiceScoring: nil),
            omittedEssayGroups: [],
            benefits: DetailBenefitsModel(aiExplanation: true, strengthWeakness: true, practice: false, note: nil),
            resultView: "FULL_SOLUTION",
            productType: "BUNDLE",
            items: items,
            saleState: nil
        )
        XCTAssertNil(bundle.effectiveCoverURL, "Bundle must never fetch coverUrl")

        for nonReadyCell in [AccessCell.loading, AccessCell.failed] {
            let view = BundleDetailView(
                product: bundle,
                sellable: true,
                accessCell: nonReadyCell,
                locale: .vi,
                supportContact: .empty,
                onOpenExam: { _ in },
                onBuyBundle: {},
                onReloadAccess: {}
            )
            for item in items {
                XCTAssertTrue(view.renderedItemAccessChips(for: item).isEmpty, "Zero per-item chips while \(nonReadyCell)")
            }
        }

        let accessRow = ExamAccessRow(
            code: "BUNDLE-4",
            state: .canStart,
            attemptsLeft: 2,
            entitlementId: "ent-b",
            activeAttemptId: nil,
            hasPendingFulfillment: true,
            pendingOrderCode: "DH003",
            items: [
                BundleAccessItemRow(
                    examId: "id-1",
                    code: "EX-TOAN",
                    name: "Toán",
                    state: .canStart,
                    attemptsLeft: 2,
                    entitlementId: "ent-1",
                    activeAttemptId: nil,
                    hasPendingFulfillment: false,
                    pendingOrderCode: nil
                ),
                BundleAccessItemRow(
                    examId: "id-2",
                    code: "EX-ANH",
                    name: "Anh",
                    state: .checking,
                    attemptsLeft: 0,
                    entitlementId: nil,
                    activeAttemptId: nil,
                    hasPendingFulfillment: true,
                    pendingOrderCode: "DH003"
                ),
                BundleAccessItemRow(
                    examId: "id-3",
                    code: "EX-VAN",
                    name: "Văn",
                    state: .none,
                    attemptsLeft: 0,
                    entitlementId: nil,
                    activeAttemptId: nil,
                    hasPendingFulfillment: false,
                    pendingOrderCode: nil
                ),
                BundleAccessItemRow(
                    examId: "id-4",
                    code: "EX-LY",
                    name: "Lý",
                    state: .checking,
                    attemptsLeft: 0,
                    entitlementId: nil,
                    activeAttemptId: nil,
                    hasPendingFulfillment: true,
                    pendingOrderCode: nil
                )
            ]
        )

        let readyView = BundleDetailView(
            product: bundle,
            sellable: true,
            accessCell: .ready(accessRow),
            locale: .vi,
            supportContact: .empty,
            onOpenExam: { _ in },
            onBuyBundle: {},
            onReloadAccess: {}
        )

        XCTAssertEqual(readyView.renderedExamCountBadge, "4 bài")
        XCTAssertEqual(readyView.renderedItemAccessChips(for: items[0]), ["Còn 2 lượt"])
        XCTAssertEqual(readyView.renderedItemAccessChips(for: items[1]), ["Đang kiểm tra"])
        XCTAssertTrue(readyView.renderedItemAccessChips(for: items[2]).isEmpty)
        XCTAssertEqual(readyView.renderedItemAccessChips(for: items[3]), ["Đang kiểm tra"])

        XCTAssertTrue(readyView.isBuyBundleButtonEnabled)
        XCTAssertEqual(readyView.renderedExistingEntitlementLines, [
            "Bạn đã có 2 lượt bài Toán; mua bộ sẽ cộng thêm lượt"
        ])
        XCTAssertEqual(readyView.renderedPendingOrderLines, [
            "Đơn DH003 cho bài Anh đang được kiểm tra. Bạn không cần thanh toán lại.",
            "Có đơn đang được kiểm tra cho bài Lý. Bạn không cần thanh toán lại."
        ])
        let panelActions = readyView.renderedBuyPanelActionLabels
        XCTAssertTrue(panelActions.contains("Mua bộ đề"))
        XCTAssertFalse(panelActions.contains("Tiếp tục"))
        XCTAssertFalse(panelActions.contains("Vào bài thi"))

        let (window, _) = CatalogViewInspector.host(
            ScrollView { readyView.padding(16) }
                .environment(\.brandPalette, .defaultLight)
                .environment(\.statusPalette, .light),
            size: CGSize(width: 390, height: 844)
        )
        defer { window.isHidden = true }
        let (_, png) = try CatalogViewInspector.renderPNG(window: window, size: CGSize(width: 390, height: 844))
        XCTAssertGreaterThan(png.count, 4096)
    }

    func testBundleBuiltFromRouteWithOnlyCodePreservesWireFieldsAndNavigatesToChildExamAnd404() async throws {
        let bundleData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.getStorefrontProduct.id,
            status: 200,
            variant: "bundle"
        )
        let notFoundData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.getStorefrontProduct.id,
            status: 404
        )
        let accessData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.queryMyExamAccess.id,
            status: 200
        )

        let stack = try CatalogTestStackFactory.makeStack { transport in
            transport.registerJSON(method: .get, path: "/api/v1/public/storefront/products/BUNDLE-DEMO-01", data: bundleData)
            transport.registerJSON(method: .get, path: "/api/v1/public/storefront/products/EXAM-MISSING", statusCode: 404, data: notFoundData)
            transport.registerJSON(method: .post, path: "/api/v1/me/exam-access/query", data: accessData)
        }
        defer {
            UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName)
        }

        let vm = ProductDetailViewModel(code: "BUNDLE-DEMO-01", api: stack.api)
        await vm.loadIfNeeded()

        guard case let .bundle(detail, sellable) = vm.loadedOutcome else {
            return XCTFail("Expected bundle outcome, got \(String(describing: vm.loadedOutcome))")
        }
        XCTAssertTrue(sellable)
        XCTAssertEqual(detail.items.count, 2)
        XCTAssertEqual(detail.items[0].subjectName?.nameVi, "Toán học")
        XCTAssertEqual(detail.items[0].questionCount, IntRangeValue(min: 50, max: 50))
        XCTAssertEqual(detail.items[0].durationMinutes, IntRangeValue(min: 75, max: 75))
        XCTAssertEqual(detail.items[0].maxPoints, DecimalRangeValue(min: 50, max: 50))

        let router = AppRouter(initialDestination: .home)
        let nav = CatalogNavigationHandler(router: router)
        nav.openProductRoute(code: detail.items[0].code)
        XCTAssertEqual(router.path(for: .home), [.product(code: "EXAM-DEMO-02")])

        let missingVM = ProductDetailViewModel(code: "EXAM-MISSING", api: stack.api)
        await missingVM.loadIfNeeded()
        XCTAssertEqual(missingVM.loadedOutcome, .notFound)
    }

    private func makeBundleItem(
        id: String,
        code: String,
        name: String,
        scope: String,
        subjectVi: String,
        subjectEn: String,
        partCode: String? = nil
    ) -> BundleDetailItemModel {
        BundleDetailItemModel(
            examId: id,
            code: code,
            name: name,
            scope: scope,
            partCode: partCode,
            subjectName: LocalizedLabel(nameVi: subjectVi, nameEn: subjectEn),
            buildMode: "STANDARD",
            questionCount: IntRangeValue(min: 40, max: 40),
            durationMinutes: IntRangeValue(min: 60, max: 60),
            maxPoints: DecimalRangeValue(min: 50, max: 50),
            attemptsGranted: 2,
            benefits: DetailBenefitsModel(aiExplanation: true, strengthWeakness: true, practice: false, note: nil),
            resultView: "FULL_SOLUTION",
            versionNo: 1
        )
    }
}
