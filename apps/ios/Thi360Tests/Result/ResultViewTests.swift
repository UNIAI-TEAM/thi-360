import Foundation
import SwiftUI
import XCTest
@testable import Thi360

@MainActor
final class ResultViewTests: XCTestCase {
    func testHeaderAndInfo() throws {
        let examOptions = try ResultTestFixtures.decodeCatalogOptions(variant: "exam")
        let subjectOptions = try ResultTestFixtures.decodeCatalogOptions(variant: "subject")

        let result = try ResultTestFixtures.decodeResult(variant: "regrading") { root in
            root["examKindCode"] = "HSA"
            root["scope"] = "FULL"
            root["partCode"] = "P3"
            root["subjectCode"] = "TOAN"
            root["attemptNo"] = 2
            root["startedAt"] = "2026-11-26T01:05:00Z"
            root["finishedAt"] = "2026-11-26T02:20:30Z"
            root["endReason"] = "CHOICE_TIMEOUT"
            let part3Subjects: [String] = ["LY", "HOA"]
            let part3Choice: [String: Any] = ["optionCode": "KH", "subjectCodes": part3Subjects]
            let part4Subjects: [String] = []
            let part4Choice: [String: Any] = ["optionCode": "ANH", "subjectCodes": part4Subjects]
            let choices: [String: Any] = [
                "a_part3": part3Choice,
                "b_part4": part4Choice
            ]
            root["choices"] = choices
        }

        let (sessionStore, _, _, _) = try SessionTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        )
        let vm = ResultViewModel(attemptId: result.attemptId, repository: ResultRepository(apiClient: sessionStore.apiClient))
        vm.setStateForTesting(
            loadState: .loaded(value: result, stale: false),
            examOptions: examOptions,
            subjectOptions: subjectOptions
        )

        let (window, _, sink, _) = ResultViewTestHelper.host(
            ResultView(
                viewModel: vm,
                locale: .vi,
                onOpenReview: { _ in },
                onContinueAttempt: { _ in },
                onGoMyExams: {},
                onGoHistory: {},
                autoLoadOnAppear: false
            ),
            size: CGSize(width: 390, height: 1800)
        )
        defer { window.isHidden = true }

        XCTAssertEqual(sink.nodes["result.header.eyebrow"]?.label, "Kết quả lượt thi")
        XCTAssertEqual(sink.nodes["result.header.scope"]?.label, "Bài thi FULL")
        XCTAssertEqual(sink.nodes["result.header.attemptNo"]?.label, "Lần 2")
        XCTAssertEqual(sink.nodes["result.header.statusChip"]?.label, "Đang chấm lại")

        XCTAssertTrue(sink.nodes["result.info.part"]?.label.contains("P3") == true)
        XCTAssertTrue(sink.nodes["result.info.subject"]?.label.contains("Toán") == true)
        XCTAssertTrue(sink.nodes["result.info.startedAt"]?.label.contains("08:05, 26/11/2026") == true)
        XCTAssertTrue(sink.nodes["result.info.finishedAt"]?.label.contains("09:20, 26/11/2026") == true)
        XCTAssertTrue(sink.nodes["result.info.duration"]?.label.contains("1 giờ 15 phút") == true)
        XCTAssertTrue(sink.nodes["result.info.endReason"]?.label.contains("Quá hạn chọn phần 3") == true)

        let choicesText = try XCTUnwrap(sink.nodes["result.info.choices"]?.label)
        XCTAssertTrue(choicesText.contains("Khoa học:"))
        XCTAssertTrue(choicesText.contains(", "))
        XCTAssertTrue(choicesText.contains(" · Tiếng Anh"))

        let noChoicesResult = try ResultTestFixtures.decodeResult(variant: "scored") { root in
            root.removeValue(forKey: "partCode")
            root.removeValue(forKey: "subjectCode")
            root.removeValue(forKey: "endReason")
            root["choices"] = [:]
        }
        vm.setStateForTesting(loadState: .loaded(value: noChoicesResult, stale: false))
        let (window2, _, sink2, _) = ResultViewTestHelper.host(
            ResultView(
                viewModel: vm,
                locale: .vi,
                onOpenReview: { _ in },
                onContinueAttempt: { _ in },
                onGoMyExams: {},
                onGoHistory: {},
                autoLoadOnAppear: false
            ),
            size: CGSize(width: 390, height: 1400)
        )
        defer { window2.isHidden = true }

        XCTAssertNil(sink2.nodes["result.info.part"])
        XCTAssertNil(sink2.nodes["result.info.subject"])
        XCTAssertNil(sink2.nodes["result.info.endReason"])
        XCTAssertTrue(sink2.nodes["result.info.choices"]?.label.contains("Không có") == true)
    }

    func testFaultBanners() throws {
        let sentinelFaultMessage = "SECRET_BACKEND_FAULT_MESSAGE_DO_NOT_RENDER"
        let compensated = try ResultTestFixtures.decodeResult(variant: "compensated") { root in
            root["faultMessage"] = sentinelFaultMessage
            root["faultReason"] = "Mất kết nối máy chủ chấm"
        }
        let systemFault = try ResultTestFixtures.decodeResult(variant: "system-fault") { root in
            root["faultMessage"] = sentinelFaultMessage
            root["faultReason"] = "Đóng tự động do gián đoạn"
        }

        let (sessionStore, _, _, _) = try SessionTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        )
        let vm = ResultViewModel(attemptId: compensated.attemptId, repository: ResultRepository(apiClient: sessionStore.apiClient))
        vm.setStateForTesting(loadState: .loaded(value: compensated, stale: false))

        let (win1, _, sink1, _) = ResultViewTestHelper.host(
            ResultView(
                viewModel: vm,
                locale: .vi,
                onOpenReview: { _ in },
                onContinueAttempt: { _ in },
                onGoMyExams: {},
                onGoHistory: {},
                autoLoadOnAppear: false
            )
        )
        let banner1 = try XCTUnwrap(sink1.nodes["result.fault.banner"]?.label)
        XCTAssertTrue(banner1.contains("Lượt bị ảnh hưởng bởi lỗi hệ thống, đã cấp bù 1 lượt"))
        XCTAssertTrue(banner1.contains("Ghi chú: Mất kết nối máy chủ chấm"))
        XCTAssertFalse(sink1.allRecordedStrings.joined().contains(sentinelFaultMessage))
        win1.isHidden = true

        vm.setStateForTesting(loadState: .loaded(value: systemFault, stale: false))
        let (win2, _, sink2, _) = ResultViewTestHelper.host(
            ResultView(
                viewModel: vm,
                locale: .vi,
                onOpenReview: { _ in },
                onContinueAttempt: { _ in },
                onGoMyExams: {},
                onGoHistory: {},
                autoLoadOnAppear: false
            )
        )
        let banner2 = try XCTUnwrap(sink2.nodes["result.fault.banner"]?.label)
        XCTAssertTrue(banner2.contains("Lượt kết thúc do lỗi hệ thống"))
        XCTAssertTrue(banner2.contains("Ghi chú: Đóng tự động do gián đoạn"))
        XCTAssertFalse(sink2.allRecordedStrings.joined().contains(sentinelFaultMessage))
        win2.isHidden = true
    }

    func testReviewEntryMatrix() throws {
        let (sessionStore, _, _, _) = try SessionTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        )
        let repo = ResultRepository(apiClient: sessionStore.apiClient)

        let states: [(String, AttemptResultState)] = [
            ("grading", .grading),
            ("grading-error", .gradingError),
            ("pending-review", .pendingReview),
            ("scored", .scored)
        ]
        let views = ["SCORE", "SCORE_AND_ANSWERS", "FULL_SOLUTION"]

        for (variant, expectedState) in states {
            for viewLevel in views {
                for available in [false, true] {
                    let item = try ResultTestFixtures.decodeResult(variant: variant) { root in
                        root["resultView"] = viewLevel
                        root["reviewAvailable"] = available
                    }
                    let shouldShow = available && viewLevel != "SCORE" && (expectedState == .scored || expectedState == .pendingReview)
                    XCTAssertEqual(
                        item.canOpenReview,
                        shouldShow,
                        "Matrix mismatch for state=\(expectedState) view=\(viewLevel) available=\(available)"
                    )
                }
            }
        }

        let gradingError = try ResultTestFixtures.decodeResult(variant: "grading-error")
        let vm = ResultViewModel(attemptId: gradingError.attemptId, repository: repo)
        vm.setStateForTesting(loadState: .loaded(value: gradingError, stale: false))
        let (winErr, _, sinkErr, _) = ResultViewTestHelper.host(
            ResultView(
                viewModel: vm,
                locale: .vi,
                onOpenReview: { _ in },
                onContinueAttempt: { _ in },
                onGoMyExams: {},
                onGoHistory: {},
                autoLoadOnAppear: false
            )
        )
        XCTAssertEqual(sinkErr.nodes["result.gradingError.heading"]?.label, "Đang xử lý kết quả")
        XCTAssertEqual(sinkErr.nodes["result.gradingError.message"]?.label, "Bài làm đã được ghi nhận, kết quả đang được xử lý")
        XCTAssertNil(sinkErr.nodes["result.total.scoreValue"])
        XCTAssertNil(sinkErr.nodes["result.actions.reviewButton"])
        winErr.isHidden = true

        let pendingReview = try ResultTestFixtures.decodeResult(variant: "pending-review")
        vm.setStateForTesting(loadState: .loaded(value: pendingReview, stale: false))
        let (winPending, _, sinkPending, _) = ResultViewTestHelper.host(
            ResultView(
                viewModel: vm,
                locale: .vi,
                onOpenReview: { _ in },
                onContinueAttempt: { _ in },
                onGoMyExams: {},
                onGoHistory: {},
                autoLoadOnAppear: false
            ),
            size: CGSize(width: 390, height: 1400)
        )
        XCTAssertEqual(sinkPending.nodes["result.actions.reviewButton"]?.label, "Xem lại câu đã chấm tự động")
        XCTAssertEqual(sinkPending.nodes["result.actions.pendingReviewNote"]?.label, "Câu tự luận xem lại được sau khi chấm xong.")
        winPending.isHidden = true

        let scoreOnly = try ResultTestFixtures.decodeResult(variant: "zero-score")
        vm.setStateForTesting(loadState: .loaded(value: scoreOnly, stale: false))
        let (winScoreOnly, _, sinkScoreOnly, _) = ResultViewTestHelper.host(
            ResultView(
                viewModel: vm,
                locale: .vi,
                onOpenReview: { _ in },
                onContinueAttempt: { _ in },
                onGoMyExams: {},
                onGoHistory: {},
                autoLoadOnAppear: false
            ),
            size: CGSize(width: 390, height: 1400)
        )
        XCTAssertNil(sinkScoreOnly.nodes["result.actions.reviewButton"])
        XCTAssertEqual(
            sinkScoreOnly.nodes["result.actions.scoreOnlyNote"]?.label,
            "Bài thi này chỉ cho xem điểm, không có phần xem lại bài làm."
        )
        winScoreOnly.isHidden = true
    }

    func testTotalAndStats() throws {
        let (sessionStore, _, _, _) = try SessionTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        )
        let repo = ResultRepository(apiClient: sessionStore.apiClient)

        let pending = try ResultTestFixtures.decodeResult(variant: "pending-review")
        let vm = ResultViewModel(attemptId: pending.attemptId, repository: repo)
        vm.setStateForTesting(loadState: .loaded(value: pending, stale: false))

        let (winPending, _, sinkPending, _) = ResultViewTestHelper.host(
            ResultView(
                viewModel: vm,
                locale: .vi,
                onOpenReview: { _ in },
                onContinueAttempt: { _ in },
                onGoMyExams: {},
                onGoHistory: {},
                autoLoadOnAppear: false
            ),
            size: CGSize(width: 390, height: 1400)
        )
        XCTAssertEqual(sinkPending.nodes["result.total.pendingTitle"]?.label, "Chờ chấm")
        XCTAssertTrue(sinkPending.nodes["result.total.pendingReturnNote"]?.label.contains("Bạn có thể rời màn hình.") == true)
        XCTAssertNotNil(sinkPending.nodes["result.stats.correct"])
        XCTAssertNotNil(sinkPending.nodes["result.stats.partial"])
        XCTAssertNotNil(sinkPending.nodes["result.stats.wrong"])
        XCTAssertNotNil(sinkPending.nodes["result.stats.blank"])
        XCTAssertEqual(sinkPending.nodes["result.stats.pending"]?.value, "highlighted")
        XCTAssertEqual(sinkPending.nodes["result.stats.denominator"]?.label, "Trên 27 câu phải làm.")
        winPending.isHidden = true

        let regrading = try ResultTestFixtures.decodeResult(variant: "regrading")
        vm.setStateForTesting(loadState: .loaded(value: regrading, stale: false))
        let (winRegrading, _, sinkRegrading, _) = ResultViewTestHelper.host(
            ResultView(
                viewModel: vm,
                locale: .vi,
                onOpenReview: { _ in },
                onContinueAttempt: { _ in },
                onGoMyExams: {},
                onGoHistory: {},
                autoLoadOnAppear: false
            ),
            size: CGSize(width: 390, height: 1400)
        )
        XCTAssertEqual(sinkRegrading.nodes["result.total.scoreValue"]?.label, "45,5/47,75")
        XCTAssertEqual(sinkRegrading.nodes["result.total.regradingChip"]?.label, "Đang chấm lại")
        XCTAssertNotNil(sinkRegrading.nodes["result.total.regradingNote"])
        winRegrading.isHidden = true

        let zero = try ResultTestFixtures.decodeResult(variant: "zero-score")
        vm.setStateForTesting(loadState: .loaded(value: zero, stale: false))
        let (winZero, _, sinkZero, _) = ResultViewTestHelper.host(
            ResultView(
                viewModel: vm,
                locale: .vi,
                onOpenReview: { _ in },
                onContinueAttempt: { _ in },
                onGoMyExams: {},
                onGoHistory: {},
                autoLoadOnAppear: false
            ),
            size: CGSize(width: 390, height: 1200)
        )
        XCTAssertEqual(sinkZero.nodes["result.total.scoreValue"]?.label, "0/5")
        XCTAssertNil(sinkZero.nodes["result.total.pendingTitle"])
        XCTAssertEqual(
            sinkZero.nodes["result.total.scaleDescription"]?.label,
            "Tổng điểm các câu trong phạm vi bài làm. Câu bỏ trống được 0 điểm."
        )
        winZero.isHidden = true
    }
}
