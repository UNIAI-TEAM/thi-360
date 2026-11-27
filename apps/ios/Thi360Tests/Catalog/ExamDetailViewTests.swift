import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class ExamDetailViewTests: XCTestCase {
    func testExamDetailStructureChoicesHowRightsPolicyAndErrors() async throws {
        let product = ProductDetailModel(
            code: "HSA-EX-17",
            name: "Đề thi HSA Cấu trúc 3 phần",
            summary: nil,
            coverUrl: nil,
            examKind: ExamKindInfo(code: "HSA", name: LocalizedLabel(nameVi: "HSA", nameEn: "HSA")),
            format: "FULL",
            scopeLabel: nil,
            buildMode: "STANDARD",
            questionCount: IntRangeValue(min: 50, max: 50),
            durationMinutes: IntRangeValue(min: 90, max: 90),
            maxPoints: DecimalRangeValue(min: 150, max: 150),
            attemptsGranted: 3,
            listPrice: 200_000,
            price: 200_000,
            promotion: nil,
            versionNo: 2,
            description: nil,
            objectives: nil,
            audience: nil,
            parts: [
                DetailPartModel(
                    order: 1,
                    name: "Phần 1 Định lượng",
                    questionCount: IntRangeValue(min: 15, max: 15),
                    durationMinutes: IntRangeValue(min: 30, max: 30),
                    maxPoints: DecimalRangeValue(min: 50, max: 50),
                    required: true,
                    answerTypes: [
                        AnswerTypeGroupModel(questionType: "QUESTION_SET", questionCount: 10, childAnswerType: "SINGLE_CHOICE"),
                        AnswerTypeGroupModel(questionType: "SINGLE_CHOICE", questionCount: 5, childAnswerType: nil)
                    ]
                ),
                DetailPartModel(
                    order: 2,
                    name: "Phần 2 Khoa học",
                    questionCount: IntRangeValue(min: 20, max: 20),
                    durationMinutes: IntRangeValue(min: 30, max: 30),
                    maxPoints: DecimalRangeValue(min: 50, max: 50),
                    required: false,
                    answerTypes: []
                ),
                DetailPartModel(
                    order: 3,
                    name: "Phần 3 Tự chọn",
                    questionCount: IntRangeValue(min: 15, max: 15),
                    durationMinutes: IntRangeValue(min: 30, max: 30),
                    maxPoints: nil,
                    required: true,
                    answerTypes: []
                )
            ],
            choices: [
                DetailChoiceGroupModel(
                    partName: "Phần 3 Tự chọn",
                    options: [
                        DetailChoiceOptionModel(
                            code: "OPT-VAN",
                            kind: "SUBJECT",
                            name: "Ngữ văn tự luận",
                            branch: nil,
                            chooseSubjects: 1,
                            countsByPick: nil,
                            subjects: [ExamKindInfo(code: "VAN", name: LocalizedLabel(nameVi: "Ngữ văn", nameEn: "Literature"))],
                            answerTypes: [
                                AnswerTypeGroupModel(questionType: "ESSAY", questionCount: 1, childAnswerType: nil)
                            ],
                            subjectAnswerTypes: []
                        )
                    ]
                )
            ],
            scoring: DetailScoringModel(timing: "PER_PART", maxScore: nil, referenceMax: nil, practiceScoring: nil),
            omittedEssayGroups: [],
            benefits: DetailBenefitsModel(aiExplanation: true, strengthWeakness: true, practice: false, note: nil),
            resultView: "FULL_SOLUTION",
            productType: "EXAM",
            items: [],
            saleState: nil
        )

        let support = SupportContact(phone: "1900 1234", email: "hotro@thi360.vn", hours: "8:00 - 21:00")
        let detailView = ExamDetailView(
            product: product,
            accessCell: .ready(.none(code: product.code)),
            locale: .vi,
            supportContact: support,
            onAccessButtonTap: { _ in },
            onReloadAccess: {}
        )

        XCTAssertEqual(detailView.renderedStructureTitle, "Cấu trúc bài thi")
        XCTAssertEqual(detailView.renderedPartRows.count, 3)
        XCTAssertEqual(detailView.renderedPartRows.map(\.order), [1, 2, 3])
        XCTAssertEqual(detailView.renderedPartRows[1].nature, "Tự chọn")
        XCTAssertEqual(detailView.renderedPartRows[2].maxPoints, "—")
        XCTAssertEqual(detailView.renderedAnswerTypesSummary, "Trắc nghiệm một đáp án: 15")
        XCTAssertTrue(product.hasEssay)
        XCTAssertEqual(detailView.renderedEssayNote, "Phần tự luận chấm tay. Điểm tổng có sau khi chấm xong.")
        XCTAssertEqual(detailView.renderedResultTiming, "Sau khi người chấm chấm xong")
        XCTAssertEqual(detailView.renderedMaxScoreLine, "150 điểm")
        XCTAssertNil(product.summary)
        XCTAssertNil(product.description)
        XCTAssertNil(product.objectives)
        XCTAssertNil(product.audience)

        let policyJoined = detailView.renderedPolicyStrings.joined(separator: "\n")
        XCTAssertTrue(policyJoined.contains("Giá và chính sách"))
        XCTAssertTrue(policyJoined.contains("theo chính sách do đơn vị vận hành công bố"))
        XCTAssertTrue(policyJoined.contains("1900 1234"))
        XCTAssertTrue(policyJoined.contains("hotro@thi360.vn"))

        let hosted = ScrollView { detailView.padding(16) }
            .environment(\.brandPalette, .defaultLight)
            .environment(\.statusPalette, .light)
        let (window, controller) = CatalogViewInspector.host(hosted, size: CGSize(width: 390, height: 844))
        defer { window.isHidden = true }
        XCTAssertFalse(RecoveryViewTestHelper.findViews(ofType: UIScrollView.self, in: controller.view).isEmpty)
        let (_, png) = try CatalogViewInspector.renderPNG(window: window, size: CGSize(width: 390, height: 844))
        XCTAssertGreaterThan(png.count, 4096)

        let rate429 = try CatalogTestFixtures.makeRateLimited429Data()
        let serverError500 = try CatalogTestFixtures.makeInternalServerError500Data()
        let stack = try CatalogTestStackFactory.makeStack { transport in
            transport.enqueue(method: .get, path: "/api/v1/public/storefront/products/HSA-EX-17", handlers: [
                { _ in
                    ContractFakeTransport.jsonResponse(statusCode: 500, data: serverError500)
                },
                { _ in
                    ContractFakeTransport.jsonResponse(statusCode: 429, data: rate429)
                },
                { _ in
                    throw URLError(.notConnectedToInternet)
                }
            ])
        }
        defer {
            UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName)
        }

        let vm = ProductDetailViewModel(code: "HSA-EX-17", api: stack.api)
        XCTAssertEqual(vm.loadState, .loading)

        await vm.loadIfNeeded()
        guard case let .failed(kind500, true, _) = vm.loadState else {
            return XCTFail("Expected 500 server failure")
        }
        XCTAssertEqual(kind500, .server)

        await vm.retry()
        guard case let .failed(kind429, true, _) = vm.loadState else {
            return XCTFail("Expected 429 server failure")
        }
        XCTAssertEqual(kind429, .server)

        await vm.retry()
        guard case let .failed(kindOffline, true, _) = vm.loadState else {
            return XCTFail("Expected offline failure")
        }
        XCTAssertEqual(kindOffline, .offline)
    }

    func testCoverPlaceholderBrandMark() throws {
        let examDetailData = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.getStorefrontProduct.id,
            status: 200,
            fileContains: "exam"
        )
        let generated = try MobileAPIClient.makeJSONDecoder().decode(Components.Schemas.ProductDetail.self, from: examDetailData)
        let detail = CatalogAPI.mapProductDetail(generated, supplementalJSONData: examDetailData)
        XCTAssertNil(detail.effectiveCoverURL)

        let cover = ExamDetailCoverView(product: detail, locale: .vi)
        XCTAssertTrue(cover.usesBrandMarkPlaceholder)
        XCTAssertEqual(cover.placeholderKindCode, "HSA")
        XCTAssertNotEqual(cover.placeholderKindCode, "360")

        let (window, _) = CatalogViewInspector.host(
            cover.environment(\.brandPalette, .defaultLight),
            size: CGSize(width: 390, height: 220)
        )
        defer { window.isHidden = true }
        let (_, png) = try CatalogViewInspector.renderPNG(window: window, size: CGSize(width: 390, height: 220))
        XCTAssertGreaterThan(png.count, 2048)
    }

    func testPricePanel() async throws {
        let endsAt = FlexibleISO8601DateTranscoder.parse("2026-12-31T17:30:00Z")!

        let noPromoExam = ProductDetailModel(
            code: "EXAM-PANEL-1",
            name: "Đề thi mẫu",
            summary: nil,
            coverUrl: nil,
            examKind: ExamKindInfo(code: "HSA", name: LocalizedLabel(nameVi: "HSA", nameEn: "HSA")),
            format: "FULL",
            scopeLabel: nil,
            buildMode: "STANDARD",
            questionCount: IntRangeValue(min: 50, max: 50),
            durationMinutes: IntRangeValue(min: 90, max: 90),
            maxPoints: DecimalRangeValue(min: 100, max: 100),
            attemptsGranted: 3,
            listPrice: 200_000,
            price: 200_000,
            promotion: nil,
            versionNo: 1,
            description: nil,
            objectives: nil,
            audience: nil,
            parts: [],
            choices: [],
            scoring: DetailScoringModel(timing: nil, maxScore: 100, referenceMax: nil, practiceScoring: nil),
            omittedEssayGroups: [],
            benefits: DetailBenefitsModel(aiExplanation: true, strengthWeakness: true, practice: false, note: nil),
            resultView: "FULL_SOLUTION",
            productType: "EXAM",
            items: [],
            saleState: nil
        )

        let exhaustedRow = ExamAccessRow(
            code: "EXAM-PANEL-1",
            state: .exhausted,
            attemptsLeft: 0,
            entitlementId: "ent-1",
            activeAttemptId: nil,
            hasPendingFulfillment: false,
            pendingOrderCode: nil,
            items: nil
        )

        let detail1 = ExamDetailView(
            product: noPromoExam,
            accessCell: .ready(exhaustedRow),
            locale: .vi,
            supportContact: .empty,
            onAccessButtonTap: { _ in },
            onReloadAccess: {}
        )
        XCTAssertEqual(detail1.renderedPricePanelBullets, [
            "Đã gồm thuế, phí",
            "3 lượt thi mỗi lần mua",
            "50 câu · 90 phút mỗi lượt"
        ])
        XCTAssertNil(detail1.renderedPromotionLine)
        XCTAssertEqual(detail1.renderedAccessSection.renderedStrings, [
            "Bạn đã dùng hết lượt của bài thi này.",
            "Mua lại"
        ])
        XCTAssertFalse(detail1.renderedAccessSection.renderedStrings.contains("Xem chi tiết"))

        let equalPromoExam = ProductDetailModel(
            code: "EXAM-PANEL-2",
            name: "Đề khuyến mãi đồng giá",
            summary: nil,
            coverUrl: nil,
            examKind: ExamKindInfo(code: "HSA", name: LocalizedLabel(nameVi: "HSA", nameEn: "HSA")),
            format: "FULL",
            scopeLabel: nil,
            buildMode: "STANDARD",
            questionCount: IntRangeValue(min: 50, max: 50),
            durationMinutes: IntRangeValue(min: 90, max: 90),
            maxPoints: DecimalRangeValue(min: 100, max: 100),
            attemptsGranted: 3,
            listPrice: 200_000,
            price: 200_000,
            promotion: PromotionInfo(name: "Ưu đãi đầu năm", endsAt: endsAt),
            versionNo: 1,
            description: nil,
            objectives: nil,
            audience: nil,
            parts: [],
            choices: [],
            scoring: DetailScoringModel(timing: nil, maxScore: 100, referenceMax: nil, practiceScoring: nil),
            omittedEssayGroups: [],
            benefits: DetailBenefitsModel(aiExplanation: true, strengthWeakness: true, practice: false, note: nil),
            resultView: "FULL_SOLUTION",
            productType: "EXAM",
            items: [],
            saleState: nil
        )

        let detail2 = ExamDetailView(
            product: equalPromoExam,
            accessCell: .ready(.none(code: equalPromoExam.code)),
            locale: .vi,
            supportContact: .empty,
            onAccessButtonTap: { _ in },
            onReloadAccess: {}
        )
        XCTAssertEqual(detail2.renderedPromotionLine, "Ưu đãi đầu năm · Kết thúc 01/01")
        XCTAssertNil(detail2.renderedPriceView.renderedDiscountText)
        XCTAssertNil(detail2.renderedPriceView.renderedListPriceText)

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
            transport.registerJSON(method: .get, path: "/api/v1/public/storefront/products/EXAM-DEMO-01", data: examDetailData)
            transport.registerJSON(method: .post, path: "/api/v1/me/exam-access/query", data: accessData)
        }
        defer {
            UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName)
        }

        let vm = ProductDetailViewModel(code: "EXAM-DEMO-01", api: stack.api)
        await vm.loadIfNeeded()
        let accessReqs = stack.transport.requests(method: .post, path: "/api/v1/me/exam-access/query")
        XCTAssertEqual(accessReqs.count, 1)
        XCTAssertEqual(CatalogTestStackFactory.accessRequestCodes(from: accessReqs[0]), ["EXAM-DEMO-01"])
    }
}
