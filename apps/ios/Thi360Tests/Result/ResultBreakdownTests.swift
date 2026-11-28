import Foundation
import SwiftUI
import XCTest
@testable import Thi360

@MainActor
final class ResultBreakdownTests: XCTestCase {
    func testParts() throws {
        let result = try ResultTestFixtures.decodeResult(variant: "pending-review") { root in
            root["endReason"] = "CHOICE_TIMEOUT"
            let p1Stats: [String: Any] = [
                "correctCount": 31,
                "partialCount": 4,
                "wrongCount": 11,
                "blankCount": 4,
                "pendingReviewCount": 0,
                "totalCount": 50
            ]
            let part1: [String: Any] = [
                "partId": "p1",
                "partCode": "P1",
                "partName": "Tư duy định lượng",
                "ord": 0,
                "skipped": false,
                "pendingReview": false,
                "score": 36.5,
                "maxScore": 50.0,
                "stats": p1Stats
            ]
            let part2: [String: Any] = [
                "partId": "p2",
                "partCode": "P2_CODE",
                "ord": 1,
                "skipped": true,
                "pendingReview": false,
                "maxScore": 50.0
            ]
            let part3: [String: Any] = [
                "partId": "p3",
                "partCode": "P3",
                "partName": "Tự luận Ngữ văn",
                "ord": 2,
                "skipped": false,
                "pendingReview": true,
                "maxScore": 25.0
            ]
            let parts: [[String: Any]] = [part1, part2, part3]
            root["parts"] = parts
        }

        let (window, _, sink, _) = ResultViewTestHelper.host(
            ScrollView {
                ResultPartsBreakdownSection(
                    parts: result.parts,
                    endReason: result.endReason,
                    hasPendingAttempt: result.isPendingUnregraded,
                    locale: .vi
                )
                .padding(16)
            },
            size: CGSize(width: 390, height: 1200)
        )
        defer { window.isHidden = true }

        XCTAssertEqual(
            sink.nodes["result.parts.section.subtitle"]?.label,
            "Điểm của phần đã chấm xong là điểm riêng của phần đó, chưa phải điểm tổng."
        )

        let card0 = try XCTUnwrap(sink.nodes["result.parts.card.0"]?.label)
        XCTAssertTrue(card0.contains("Tư duy định lượng"))
        XCTAssertTrue(card0.contains("Đúng một phần: 4"))
        XCTAssertTrue(card0.contains("Điểm phần: 36,5/50"))

        let card1 = try XCTUnwrap(sink.nodes["result.parts.card.1"]?.label)
        XCTAssertTrue(card1.contains("P2_CODE"))
        XCTAssertTrue(card1.contains("Bị bỏ qua"))
        XCTAssertTrue(card1.contains("0/50"))
        XCTAssertTrue(card1.contains("Không làm phần 3: quá thời gian chọn"))

        let card2 = try XCTUnwrap(sink.nodes["result.parts.card.2"]?.label)
        XCTAssertTrue(card2.contains("Tự luận Ngữ văn"))
        XCTAssertTrue(card2.contains("Chấm tay"))
        XCTAssertTrue(card2.contains("Chờ chấm"))
        XCTAssertTrue(card2.contains("Điểm tối đa của phần: 25."))
        XCTAssertFalse(card2.contains("Điểm phần:"))
    }

    func testSegmentsAndSubjects() throws {
        let subjectOptions = try ResultTestFixtures.decodeCatalogOptions(variant: "subject")

        let result = try ResultTestFixtures.decodeResult(variant: "scored") { root in
            let seg1Stats: [String: Any] = [
                "correctCount": 22,
                "partialCount": 3,
                "wrongCount": 8,
                "blankCount": 2,
                "pendingReviewCount": 0,
                "totalCount": 35
            ]
            let seg1: [String: Any] = [
                "partCode": "P1",
                "code": "SEG1",
                "name": "Toán học",
                "fromPosition": 1,
                "toPosition": 35,
                "pendingReview": false,
                "score": 25.5,
                "maxPoints": 35.0,
                "stats": seg1Stats
            ]
            let seg2Stats: [String: Any] = [
                "correctCount": 0,
                "partialCount": 0,
                "wrongCount": 0,
                "blankCount": 0,
                "pendingReviewCount": 1,
                "totalCount": 1
            ]
            let seg2: [String: Any] = [
                "partCode": "P2",
                "code": "SEG2_CODE",
                "fromPosition": 36,
                "toPosition": 50,
                "pendingReview": true,
                "stats": seg2Stats
            ]
            let seg3Stats: [String: Any] = [
                "correctCount": 9,
                "partialCount": 0,
                "wrongCount": 0,
                "blankCount": 0,
                "pendingReviewCount": 0,
                "totalCount": 9
            ]
            let seg3: [String: Any] = [
                "partCode": "P3",
                "code": "SEG3",
                "name": "Không điểm tối đa",
                "pendingReview": false,
                "score": 9.0,
                "stats": seg3Stats
            ]
            let segments: [[String: Any]] = [seg1, seg2, seg3]
            root["segments"] = segments

            let sub1Stats: [String: Any] = [
                "correctCount": 31,
                "partialCount": 2,
                "wrongCount": 11,
                "blankCount": 6,
                "pendingReviewCount": 0,
                "totalCount": 50
            ]
            let sub1: [String: Any] = [
                "code": "TOAN",
                "name": "Fallback Name",
                "pendingReview": false,
                "score": 36.5,
                "maxScore": 50.0,
                "stats": sub1Stats
            ]
            let sub2Stats: [String: Any] = [
                "correctCount": 0,
                "partialCount": 0,
                "wrongCount": 0,
                "blankCount": 0,
                "pendingReviewCount": 1,
                "totalCount": 1
            ]
            let sub2: [String: Any] = [
                "code": "CUSTOM_SUB",
                "name": "Môn tự định nghĩa",
                "pendingReview": true,
                "stats": sub2Stats
            ]
            let sub3: [String: Any] = [
                "code": "RAW_ONLY",
                "pendingReview": false,
                "score": 4.0,
                "maxScore": 10.0
            ]
            let subjects: [[String: Any]] = [sub1, sub2, sub3]
            root["subjects"] = subjects
        }

        let (sessionStore, _, _, _) = try SessionTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        )
        let vm = ResultViewModel(attemptId: result.attemptId, repository: ResultRepository(apiClient: sessionStore.apiClient))
        vm.setStateForTesting(loadState: .loaded(value: result, stale: false), subjectOptions: subjectOptions)

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
            size: CGSize(width: 820, height: 2200),
            sizeClass: .regular,
            shellWidth: .twoColumn
        )
        defer { window.isHidden = true }

        let seg0 = try XCTUnwrap(sink.nodes["result.segments.card.0"]?.label)
        XCTAssertTrue(seg0.contains("Toán học"))
        XCTAssertTrue(seg0.contains("Câu 1–35"))
        XCTAssertTrue(seg0.contains("Điểm phân đoạn: 25,5/35"))
        XCTAssertTrue(seg0.contains("Đúng một phần: 3"))

        let seg1 = try XCTUnwrap(sink.nodes["result.segments.card.1"]?.label)
        XCTAssertTrue(seg1.contains("SEG2_CODE"))
        XCTAssertTrue(seg1.contains("Chấm tay"))
        XCTAssertTrue(seg1.contains("Chờ chấm"))
        XCTAssertFalse(seg1.contains("Điểm phân đoạn:"))

        let seg2 = try XCTUnwrap(sink.nodes["result.segments.card.2"]?.label)
        XCTAssertTrue(seg2.contains("Điểm phân đoạn: 9"))

        let sub0 = try XCTUnwrap(sink.nodes["result.subjects.card.0"]?.label)
        XCTAssertTrue(sub0.contains("Điểm môn: 36,5/50"))
        let sub1 = try XCTUnwrap(sink.nodes["result.subjects.card.1"]?.label)
        XCTAssertTrue(sub1.contains("Môn tự định nghĩa"))
        XCTAssertTrue(sub1.contains("Chấm tay"))
        XCTAssertFalse(sub1.contains("Điểm môn:"))
        let sub2 = try XCTUnwrap(sink.nodes["result.subjects.card.2"]?.label)
        XCTAssertTrue(sub2.contains("RAW_ONLY"))

        let vactWithSubjects = try ResultTestFixtures.decodeResult(variant: "vact") { root in
            let vactSubject: [String: Any] = [
                "code": "TOAN",
                "name": "Toán học",
                "pendingReview": false,
                "score": 10.0,
                "maxScore": 30.0
            ]
            let subjects: [[String: Any]] = [vactSubject]
            root["subjects"] = subjects
        }
        vm.setStateForTesting(loadState: .loaded(value: vactWithSubjects, stale: false), subjectOptions: subjectOptions)
        let (vactWin, _, vactSink, _) = ResultViewTestHelper.host(
            ResultView(
                viewModel: vm,
                locale: .vi,
                onOpenReview: { _ in },
                onContinueAttempt: { _ in },
                onGoMyExams: {},
                onGoHistory: {},
                autoLoadOnAppear: false
            ),
            size: CGSize(width: 390, height: 2000)
        )
        defer { vactWin.isHidden = true }

        XCTAssertNil(vactSink.nodes["result.subjects.section.title"], "V-ACT must never render Subjects section even when response includes subjects")
        XCTAssertNil(vactSink.nodes["result.segments.section.title"], "Empty segments must omit Segments section")
    }

    func testVact() throws {
        let subjectOptions = try ResultTestFixtures.decodeCatalogOptions(variant: "subject")
        let vact = try ResultTestFixtures.decodeResult(variant: "vact")

        let (sessionStore, _, _, _) = try SessionTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        )
        let vm = ResultViewModel(attemptId: vact.attemptId, repository: ResultRepository(apiClient: sessionStore.apiClient))
        vm.setStateForTesting(loadState: .loaded(value: vact, stale: false), subjectOptions: subjectOptions)

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
            size: CGSize(width: 390, height: 2200)
        )
        defer { window.isHidden = true }

        XCTAssertEqual(sink.nodes["result.vact.scoreLabel"]?.label, "Điểm luyện tập")
        XCTAssertEqual(sink.nodes["result.vact.practiceNote"]?.label, "Không phải điểm V-ACT chính thức")
        XCTAssertEqual(
            sink.nodes["result.total.scaleDescription"]?.label,
            "Điểm luyện tập quy từ điểm các câu về thang 1.200."
        )
        XCTAssertEqual(sink.nodes["result.vact.section.title"]?.label, "Kết quả theo thành phần")
        XCTAssertEqual(
            sink.nodes["result.vact.section.subtitle"]?.label,
            "Số câu đúng và tỷ lệ đúng theo từng thành phần (không hiện điểm thành phần)."
        )

        let toggleId = "result.vact.component.SCIENTIFIC_THINKING.toggleSubsections"
        XCTAssertEqual(sink.nodes[toggleId]?.label, "Xem riêng 2 tiểu mục (12 và 18 câu)")
        XCTAssertEqual(sink.nodes[toggleId]?.value, "collapsed")
        XCTAssertNil(sink.nodes["result.vact.component.SCIENTIFIC_THINKING.subsection.0"])

        XCTAssertTrue(sink.performAction(for: toggleId))
        RunLoop.main.run(until: Date().addingTimeInterval(0.05))

        XCTAssertEqual(sink.nodes[toggleId]?.label, "Ẩn chi tiết 2 tiểu mục")
        XCTAssertEqual(sink.nodes[toggleId]?.value, "expanded")
        let sub0 = try XCTUnwrap(sink.nodes["result.vact.component.SCIENTIFIC_THINKING.subsection.0"]?.label)
        let sub1 = try XCTUnwrap(sink.nodes["result.vact.component.SCIENTIFIC_THINKING.subsection.1"]?.label)
        XCTAssertTrue(sub0.contains("Logic và phân tích số liệu"))
        XCTAssertTrue(sub0.contains("12 câu"))
        XCTAssertTrue(sub1.contains("Suy luận khoa học"))
        XCTAssertTrue(sub1.contains("18 câu"))
    }
}
