import Foundation
import SwiftUI
import XCTest
@testable import Thi360

@MainActor
final class WaitingViewTests: XCTestCase {
    func testContent() throws {
        let perPartContext = StartContext(
            entitlementId: "ent-1",
            preChoice: nil,
            examTitle: "HSA đầy đủ (thu nhỏ)",
            examKindLabel: "HSA",
            firstPartName: "Toán học và xử lí số liệu",
            firstPartMinutes: 75,
            timing: "PER_PART"
        )
        let perPartView = AttemptWaitingView(
            attemptId: "att-1",
            examTitle: perPartContext.examTitle,
            context: perPartContext,
            origin: .prepare,
            onReady: { _ in },
            onRestarted: { _ in },
            onOpenPrepare: { _ in },
            onMyExams: {},
            controlledPhase: .polling(elapsed: 12),
            timing: "PER_PART",
            locale: .vi
        )

        let p1 = perPartView.rendered
        XCTAssertEqual(p1.topBarTitle, "HSA đầy đủ (thu nhỏ)")
        XCTAssertEqual(p1.clockPausedLabel, "Chưa tính giờ")
        XCTAssertEqual(p1.pollingTitle, "Đang chuẩn bị đề")
        XCTAssertEqual(
            p1.pollingDescription,
            "Hệ thống đang tạo bộ câu hỏi cho lượt thi của bạn. Đồng hồ chỉ bắt đầu khi đề đã sẵn sàng, nên bạn không mất thời gian làm bài trong lúc chờ."
        )
        XCTAssertEqual(p1.firstPartLabel, "Phần mở đầu")
        XCTAssertEqual(p1.firstPartName, "Toán học và xử lí số liệu")
        XCTAssertEqual(p1.durationLabel, "Thời gian của phần")
        XCTAssertEqual(p1.durationValue, "75 phút")
        XCTAssertEqual(
            p1.reassureFullText,
            "Đề sẵn sàng thì màn làm bài tự mở. Lỡ thoát ra thì mở lại từ Bài thi của tôi và bấm Tiếp tục; lượt không bị bắt đầu lại."
        )
        XCTAssertEqual(p1.reassureLinkText, "Bài thi của tôi")
        XCTAssertFalse(p1.reassureFullText.contains("trang"), "A-87 wording must not mention 'trang'")

        let (window1, _) = PrepareViewInspector.host(perPartView)
        let (_, pollingPNG) = try CatalogViewInspector.renderPNG(window: window1, size: CGSize(width: 390, height: 844))
        window1.isHidden = true
        XCTAssertGreaterThan(pollingPNG.count, 2048)

        let sharedContext = StartContext(
            entitlementId: "ent-2",
            preChoice: nil,
            examTitle: "H-SCA Toán",
            examKindLabel: "HSCA",
            firstPartName: "Phần 1 · Trắc nghiệm",
            firstPartMinutes: 90,
            timing: "SHARED"
        )
        let sharedBusyView = AttemptWaitingView(
            attemptId: "att-2",
            examTitle: sharedContext.examTitle,
            context: sharedContext,
            origin: .prepare,
            onReady: { _ in },
            onRestarted: { _ in },
            onOpenPrepare: { _ in },
            onMyExams: {},
            controlledPhase: .busy,
            timing: "SHARED",
            locale: .vi
        )

        let p2 = sharedBusyView.rendered
        XCTAssertEqual(p2.durationLabel, "Thời gian làm bài", "SHARED timing must use 'Thời gian làm bài'")
        XCTAssertEqual(p2.durationValue, "90 phút")
        XCTAssertEqual(p2.busyNotice, "Sau 90 giây vẫn chưa xong")
        XCTAssertEqual(p2.busyTitle, "Hệ thống đang bận")
        XCTAssertEqual(
            p2.busyDescription,
            "Đề vẫn đang được chuẩn bị. Ứng dụng tự kiểm tra lại, bạn không cần bấm gì. Đồng hồ chưa chạy."
        )
        XCTAssertFalse((p2.busyDescription ?? "").contains("trang"), "A-87 busy description must not mention 'trang'")
        XCTAssertEqual(p2.backToMyExamsTitle, "Về Bài thi của tôi")

        let (window2, _) = PrepareViewInspector.host(sharedBusyView)
        let (_, busyPNG) = try CatalogViewInspector.renderPNG(window: window2, size: CGSize(width: 390, height: 844))
        window2.isHidden = true
        XCTAssertNotEqual(pollingPNG, busyPNG)

        let enBusyView = AttemptWaitingView(
            attemptId: "att-3",
            examTitle: "H-SCA Math",
            context: sharedContext,
            origin: .prepare,
            onReady: { _ in },
            onRestarted: { _ in },
            onOpenPrepare: { _ in },
            onMyExams: {},
            controlledPhase: .busy,
            timing: "SHARED",
            locale: .en
        )
        let p3 = enBusyView.rendered
        XCTAssertEqual(p3.durationLabel, "Exam duration")
        XCTAssertEqual(p3.reassureLinkText, "My exams")
        XCTAssertTrue(p3.reassureFullText.contains("If you leave this screen, reopen it from My exams"))
        XCTAssertFalse(p3.reassureFullText.lowercased().contains("this page"), "A-87 EN wording must not mention 'this page'")
        XCTAssertFalse((p3.busyDescription ?? "").lowercased().contains("page"))
    }

    func testFailedVariants() throws {
        let abortCases: [(code: String?, expectedVi: String)] = [
            ("STALE_STARTING", "Việc chuẩn bị đề bị gián đoạn."),
            ("GENERATION_FAILED", "Chưa tạo được bộ câu hỏi cho đề này."),
            ("GENERATION_BUSY", "Hệ thống đang có nhiều yêu cầu tạo đề. Vui lòng thử lại sau giây lát."),
            ("GENERATION_TIMEOUT", "Tạo đề quá thời gian cho phép."),
            ("AUDIO_ASSET_MISSING", "Thiếu tệp âm thanh cần thiết cho đề thi."),
            ("AUDIO_ASSET_INVALID", "Tệp âm thanh của đề thi không hợp lệ hoặc không đọc được."),
            ("TOTALLY_UNKNOWN_CODE", "Không thể chuẩn bị đề thi lúc này."),
            (nil, "Không thể chuẩn bị đề thi lúc này.")
        ]

        for entry in abortCases {
            let view = AttemptWaitingView(
                attemptId: "att-fail",
                examTitle: "HSA đầy đủ",
                context: nil,
                origin: .prepare,
                onReady: { _ in },
                onRestarted: { _ in },
                onOpenPrepare: { _ in },
                onMyExams: {},
                controlledPhase: .failed(abortReason: entry.code),
                controlledAbortReason: entry.code,
                locale: .vi
            )
            let p = view.rendered
            XCTAssertEqual(p.failedNotice, "Chuẩn bị đề thất bại")
            XCTAssertEqual(p.failedHeading, "Chưa chuẩn bị được đề")
            XCTAssertEqual(p.failedReason, entry.expectedVi, "Wrong abort reason message for \(String(describing: entry.code))")
            XCTAssertEqual(p.failedRefundNote, "Lượt đã được trả lại, bạn chưa mất lượt nào. Đồng hồ chưa chạy.")
            XCTAssertEqual(p.backToMyExamsTitle, "Về Bài thi của tôi")
            XCTAssertEqual(p.retryTitle, "Thử lại")

            if entry.code == "STALE_STARTING" {
                let (window, _) = PrepareViewInspector.host(view)
                let (_, png) = try CatalogViewInspector.renderPNG(window: window, size: CGSize(width: 390, height: 844))
                window.isHidden = true
                XCTAssertGreaterThan(png.count, 2048)
            }
        }

        let refusedView = AttemptWaitingView(
            attemptId: "att-fail-refused",
            examTitle: "HSA đầy đủ",
            context: nil,
            origin: .prepare,
            onReady: { _ in },
            onRestarted: { _ in },
            onOpenPrepare: { _ in },
            onMyExams: {},
            controlledPhase: .retryRefused(.noAttemptsLeft),
            controlledAbortReason: "STALE_STARTING",
            locale: .vi
        )
        let pRefused = refusedView.rendered
        XCTAssertEqual(pRefused.retryError?.primary, "Bạn đã dùng hết lượt của bài thi này.")
        XCTAssertEqual(pRefused.failedReason, "Việc chuẩn bị đề bị gián đoạn.")
    }
}
