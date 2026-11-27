import Foundation
import HTTPTypes
import OpenAPIRuntime
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class PrepareViewTests: XCTestCase {
    func testLoadingAndNotFound() async throws {
        do {
            let stack = try PrepareTestStackFactory.makeStack()
            defer { PrepareTestStackFactory.cleanup(stack) }

            let vm = PrepareViewModel(
                entitlementId: "ent-loading",
                api: stack.api,
                initialState: PrepareScreenState(load: .loading)
            )
            let view = PrepareView(
                viewModel: vm,
                locale: .vi,
                usesNavigationBarChrome: false,
                onOpenAttempt: { _ in },
                onOpenCheckout: { _ in },
                onOpenMyExams: {}
            )
            XCTAssertTrue(view.isShowingLoading)
            XCTAssertFalse(view.showsStartButton, "Start button must NOT exist while loading")

            let (window, _) = PrepareViewInspector.host(view)
            let (_, png) = try CatalogViewInspector.renderPNG(window: window, size: CGSize(width: 390, height: 844))
            window.isHidden = true
            XCTAssertGreaterThan(png.count, 2048)
        }

        do {
            let notFoundData = try PrepareTestFixtures.loadIndexedData(
                operationId: Operations.prepare.id,
                status: 404,
                variant: "not-found"
            )
            let stack = try PrepareTestStackFactory.makeStack { transport in
                transport.registerJSON(
                    method: .get,
                    path: "/api/v1/me/entitlements/ent-404/prepare",
                    statusCode: 404,
                    data: notFoundData
                )
            }
            defer { PrepareTestStackFactory.cleanup(stack) }

            let router = AppRouter(initialDestination: .home, initialRoute: .prepare(entitlementId: "ent-404"))
            let vm = PrepareViewModel(entitlementId: "ent-404", api: stack.api)
            await vm.loadIfNeeded()

            let calls = stack.transport.requests(method: .get, path: "/api/v1/me/entitlements/ent-404/prepare")
            XCTAssertEqual(calls.count, 1, "404 must NOT auto-retry")

            let view = PrepareView(
                viewModel: vm,
                locale: .vi,
                usesNavigationBarChrome: false,
                onOpenAttempt: { _ in },
                onOpenCheckout: { _ in },
                onOpenMyExams: { router.switchTab(.myExams, reload: false) }
            )
            let err = try XCTUnwrap(view.renderedError)
            XCTAssertEqual(err.title, "Không tìm thấy bài thi trong tài khoản đang đăng nhập")
            XCTAssertEqual(err.description, "Quyền làm bài không tồn tại hoặc thuộc về tài khoản khác.")
            XCTAssertEqual(err.backTitle, "Về Bài thi của tôi")
            XCTAssertNil(err.retryTitle, "404 error must not offer Retry")

            let (window, _) = PrepareViewInspector.host(view)
            let (_, png) = try CatalogViewInspector.renderPNG(window: window, size: CGSize(width: 390, height: 844))
            window.isHidden = true
            XCTAssertGreaterThan(png.count, 2048)

            view.onOpenMyExams()
            XCTAssertEqual(router.selectedDestination, .myExams)
        }
    }

    func testLoadErrorRetriesOnce() async throws {
        let server500Data = try PrepareTestFixtures.makeInternalServerError500Data()
        let forbidden403Data = try PrepareTestFixtures.loadIndexedData(
            operationId: Operations.prepare.id,
            status: 403,
            variant: "attempt-access-forbidden"
        )

        enum CaseKind { case server500, offline, forbidden403 }
        for kind in [CaseKind.server500, .offline, .forbidden403] {
            let stack = try PrepareTestStackFactory.makeStack { transport in
                transport.register(method: .get, path: "/api/v1/me/entitlements/ent-err/prepare") { _ in
                    switch kind {
                    case .server500:
                        var h = HTTPFields()
                        h[.contentType] = "application/json"
                        return (HTTPResponse(status: .internalServerError, headerFields: h), HTTPBody(server500Data))
                    case .offline:
                        throw URLError(.notConnectedToInternet)
                    case .forbidden403:
                        var h = HTTPFields()
                        h[.contentType] = "application/json"
                        return (HTTPResponse(status: .forbidden, headerFields: h), HTTPBody(forbidden403Data))
                    }
                }
            }
            defer { PrepareTestStackFactory.cleanup(stack) }

            let vm = PrepareViewModel(entitlementId: "ent-err", api: stack.api)
            await vm.loadIfNeeded()

            let afterInitial = stack.transport.requests(method: .get, path: "/api/v1/me/entitlements/ent-err/prepare")
            XCTAssertEqual(afterInitial.count, 2, "Expected 1 initial call + 1 auto-retry for \(kind)")

            let view = PrepareView(
                viewModel: vm,
                locale: .vi,
                usesNavigationBarChrome: false,
                onOpenAttempt: { _ in },
                onOpenCheckout: { _ in },
                onOpenMyExams: {}
            )
            let err = try XCTUnwrap(view.renderedError)
            XCTAssertEqual(err.title, "Không tải được thông tin chuẩn bị bài thi")
            XCTAssertEqual(err.description, "Vui lòng kiểm tra kết nối mạng và thử lại.")
            XCTAssertEqual(err.backTitle, "Về Bài thi của tôi")
            XCTAssertEqual(err.retryTitle, "Thử lại")

            let (window, _) = PrepareViewInspector.host(view)
            let (_, png) = try CatalogViewInspector.renderPNG(window: window, size: CGSize(width: 390, height: 844))
            window.isHidden = true
            XCTAssertGreaterThan(png.count, 2048)

            await vm.retryLoad()
            let afterManualRetry = stack.transport.requests(method: .get, path: "/api/v1/me/entitlements/ent-err/prepare")
            XCTAssertEqual(afterManualRetry.count, 3, "Manual retry must add exactly 1 call (0 auto-retries) for \(kind)")
        }
    }

    func testHeader() throws {
        let full = try PrepareTestFixtures.loadPrepareResponse(variant: "per-part")
        do {
            let view = PrepareHeaderSection(
                response: full,
                metrics: PrepareMetrics.of(full),
                locale: .vi
            )
            let h = view.rendered
            XCTAssertEqual(h.examKindLabel, "Đánh giá năng lực ĐHQG Hà Nội (HSA)")
            XCTAssertEqual(h.scopeLabel, "Bài thi FULL")
            XCTAssertNil(h.reviewBadge)
            XCTAssertEqual(h.title, "HSA đầy đủ (thu nhỏ)")
            XCTAssertEqual(h.summary, "3 phần · 37 câu · 15 phút · 37 điểm")

            let (window, _) = PrepareViewInspector.host(view)
            let (_, png) = try CatalogViewInspector.renderPNG(window: window, size: CGSize(width: 390, height: 260))
            window.isHidden = true
            XCTAssertGreaterThan(png.count, 1024)
        }

        // Plan A6 derivation: mutates `buildMode` to `REVIEW` and nulls `durationMinutes` + `parts[*].maxPoints` on certified `shared` fixture.
        let (_, subjectReview) = try PrepareTestFixtures.mutatePrepare(variant: "shared") { root in
            root["buildMode"] = "REVIEW"
            root["durationMinutes"] = NSNull()
            var parts = (root["parts"] as? [[String: Any]]) ?? []
            for idx in parts.indices {
                parts[idx]["maxPoints"] = NSNull()
            }
            root["parts"] = parts
        }
        do {
            let viHeader = PrepareHeaderSection(
                response: subjectReview,
                metrics: PrepareMetrics.of(subjectReview),
                locale: .vi
            ).rendered
            XCTAssertEqual(viHeader.examKindLabel, "Đánh giá năng lực H-SCA")
            XCTAssertEqual(viHeader.scopeLabel, "Bài thi theo môn · Toán học")
            XCTAssertEqual(viHeader.reviewBadge, "Bài ôn")
            XCTAssertEqual(viHeader.title, "H-SCA Toán (bộ dữ liệu)")
            XCTAssertEqual(viHeader.summary, "3 phần · 40 câu")

            let enHeader = PrepareHeaderSection(
                response: subjectReview,
                metrics: PrepareMetrics.of(subjectReview),
                locale: .en
            ).rendered
            XCTAssertEqual(enHeader.examKindLabel, "H-SCA assessment")
            XCTAssertEqual(enHeader.scopeLabel, "Subject exam · Mathematics")
            XCTAssertEqual(enHeader.reviewBadge, "Review exam")
        }

        let partScope = try PrepareTestFixtures.loadPrepareResponse(variant: "class")
        let partHeader = PrepareHeaderSection(
            response: partScope,
            metrics: PrepareMetrics.of(partScope),
            locale: .vi
        ).rendered
        XCTAssertEqual(partHeader.scopeLabel, "Bài thi theo phần")
    }

    func testStructureBlock() throws {
        // Plan A6 derivation: reverses `parts` order, appends unknown `answerFormats` code, sets `listeningPolicy` (`maxPlays: null, seekable: false`), and sets `omittedGroups` on certified `free-navigation` fixture.
        let (_, freeResp) = try PrepareTestFixtures.mutatePrepare(variant: "free-navigation") { root in
            root["timing"] = "PER_PART"
            root["navigationMode"] = "FREE"
            var parts = (root["parts"] as? [[String: Any]]) ?? []
            parts.reverse()
            if !parts.isEmpty {
                parts[0]["required"] = false
            }
            root["parts"] = parts
            root["answerFormats"] = ["SINGLE_CHOICE", "MATCHING", "CUSTOM_FORMAT_X"]
            root["listeningPolicy"] = ["maxPlays": NSNull(), "seekable": false]
            root["omittedGroups"] = [
                ["name": "Nhóm tự luận có điểm", "points": 2.5],
                ["name": "Nhóm tự luận không điểm", "points": NSNull()]
            ]
        }

        do {
            let section = PrepareStructureSection(response: freeResp, locale: .vi)
            let s = section.rendered

            XCTAssertEqual(s.title, "Cấu trúc bài thi")
            XCTAssertEqual(s.timingTitle, "Một đồng hồ chung, chuyển phần tự do")
            XCTAssertEqual(
                s.timingDescription,
                "Bạn có thể chuyển qua lại giữa các phần và chỉnh sửa câu trả lời trong thời gian làm bài chung."
            )
            XCTAssertTrue(s.parts.first?.name.contains("Phần I") == true, "Parts must be sorted ascending by ord")
            XCTAssertEqual(s.parts.first?.order, 1)
            XCTAssertEqual(s.parts.first?.badge, "Bắt buộc")
            XCTAssertTrue(s.parts.contains(where: { $0.badge == "Tự chọn" }))
            XCTAssertEqual(s.answerFormatsLabel, "Dạng câu hỏi")
            XCTAssertEqual(s.answerFormats, ["Trắc nghiệm một đáp án", "Ghép đôi", "CUSTOM_FORMAT_X"])
            XCTAssertEqual(s.listeningTitle, "Chính sách nghe")
            XCTAssertEqual(
                s.listeningBody,
                "Mỗi tệp âm thanh được nghe tối đa 2 lần. Không có thanh tua, không tạm dừng và không đổi tốc độ phát."
            )
            XCTAssertEqual(s.omittedGroupsLabel, "Nhóm tự luận tạm lược khỏi đề tự động:")
            XCTAssertEqual(s.omittedGroups, ["Nhóm tự luận có điểm · 2,5 điểm", "Nhóm tự luận không điểm"])

            let (window, _) = PrepareViewInspector.host(ScrollView { section })
            let (_, png) = try CatalogViewInspector.renderPNG(window: window, size: CGSize(width: 390, height: 844))
            window.isHidden = true
            XCTAssertGreaterThan(png.count, 2048)
        }

        let (_, perPartEmptyBlocks) = try PrepareTestFixtures.mutatePrepare(variant: "per-part") { root in
            root["answerFormats"] = []
            root["listeningPolicy"] = NSNull()
            root["omittedGroups"] = []
        }
        let sPerPart = PrepareStructureSection(response: perPartEmptyBlocks, locale: .vi).rendered
        XCTAssertEqual(sPerPart.timingTitle, "Tính giờ từng phần")
        XCTAssertNil(sPerPart.answerFormatsLabel)
        XCTAssertNil(sPerPart.listeningTitle)
        XCTAssertNil(sPerPart.omittedGroupsLabel)

        let shared = try PrepareTestFixtures.loadPrepareResponse(variant: "shared")
        let sShared = PrepareStructureSection(response: shared, locale: .vi).rendered
        XCTAssertEqual(sShared.timingTitle, "Một đồng hồ chung cho cả bài")
    }

    func testAfterStartChoices() throws {
        let perPart = try PrepareTestFixtures.loadPrepareResponse(variant: "per-part")
        XCTAssertFalse(perPart.afterStartChoices.isEmpty)

        let section = PrepareAfterStartChoicesSection(choices: perPart.afterStartChoices, locale: .vi)
        let r = section.rendered
        XCTAssertEqual(r.title, "Lựa chọn sẽ gặp trong bài")
        XCTAssertEqual(r.groups.count, 1)
        XCTAssertEqual(r.groups[0].partName, "Khoa học hoặc Tiếng Anh")
        XCTAssertEqual(r.groups[0].options.count, 2)
        XCTAssertEqual(r.groups[0].options[0].headline, "SCIENCE · Khoa học (chọn đúng 3 trong 5 môn)")
        XCTAssertEqual(r.groups[0].options[0].subjectsLine, "Vật lí, Hóa học, Sinh học, Lịch sử, Địa lí")
        XCTAssertEqual(r.groups[0].options[1].headline, "ANH · Tiếng Anh")
        XCTAssertEqual(
            r.deadlineNote,
            "Thời gian chọn: tối đa 15 phút kể từ lúc phần trước đóng. Quá thời gian mà chưa xác nhận thì lượt thi kết thúc, phần tự chọn được 0 điểm."
        )
        XCTAssertEqual(r.timerNote, "Đồng hồ của phần tự chọn chỉ chạy khi bộ câu hỏi đã sẵn sàng.")

        let (window, _) = PrepareViewInspector.host(ScrollView { section })
        let (_, png) = try CatalogViewInspector.renderPNG(window: window, size: CGSize(width: 390, height: 600))
        window.isHidden = true
        XCTAssertGreaterThan(png.count, 2048)
    }

    func testInstructions() throws {
        let html = #"<p>Đọc kỹ đề bài trước khi trả lời.</p><img src="https://example.com/secret.png" alt="HiddenImage" />"#
        let section = PrepareInstructionsSection(instructionsHTML: html, locale: .vi)
        let r = section.rendered
        XCTAssertEqual(r.title, "Hướng dẫn làm bài")
        XCTAssertTrue(r.hasRichContent)
        let doc = try XCTUnwrap(r.parsedDocument)
        XCTAssertFalse(doc.blocks.contains { block in
            if case .imageFigure = block { return true }
            return false
        }, "Instructions RichHTMLParser must strip images (allowImages: false)")
        XCTAssertEqual(r.fixedNotes, [
            "Câu trả lời được lưu tự động. Thanh trên cho biết Đang lưu, Đã lưu hoặc Chưa lưu.",
            "Thời gian còn lại lấy theo giờ máy chủ; đổi giờ trên thiết bị không làm thay đổi thời gian.",
            "Mất kết nối vẫn làm bài tiếp được. Câu trả lời được giữ trên thiết bị và gửi khi có mạng lại; đồng hồ vẫn chạy.",
            "Mỗi lượt chỉ làm trên một thiết bị tại một thời điểm. Mở ở thiết bị khác thì phải xác nhận chuyển sang thiết bị đó."
        ])

        do {
            let (window, controller) = PrepareViewInspector.host(ScrollView { section }, size: CGSize(width: 390, height: 844))
            defer { window.isHidden = true }
            let richText = PrepareViewInspector.joinedText(in: controller.view, window: window)
            XCTAssertTrue(richText.contains("Đọc kỹ đề bài trước khi trả lời."), "RichContentView must render instruction text")
            XCTAssertFalse(richText.contains("HiddenImage"), "Stripped <img> must not render alt text")
            let imageViews = RecoveryViewTestHelper.findViews(ofType: UIImageView.self, in: controller.view)
                .filter { $0.image != nil }
            XCTAssertTrue(imageViews.isEmpty, "Instructions must not render any <img> view")
        }

        let blankSection = PrepareInstructionsSection(instructionsHTML: "   ", locale: .vi)
        XCTAssertFalse(blankSection.rendered.hasRichContent)
        XCTAssertEqual(blankSection.rendered.fixedNotes.count, 4)
    }

    func testReadyColumn() throws {
        let grantCases: [(source: String, orderCode: String?, grantedAt: String?, expected: String)] = [
            ("COMPENSATION", nil, "2026-11-20T10:00:00Z", "Cấp bù"),
            ("CLASS", nil, "2026-11-20T10:00:00Z", "Giao theo lớp ngày 20/11"),
            ("CLASS", nil, nil, "Giao theo lớp"),
            ("PURCHASE", "DH261126000001", "2026-11-20T10:00:00Z", "Mua ngày 20/11, đơn DH261126000001"),
            ("PURCHASE", nil, "2026-11-20T10:00:00Z", "Mua ngày 20/11")
        ]

        for entry in grantCases {
            // Plan A6 derivation: sets `grantSource` fields on certified `per-part` fixture to test all 5 grantSource variants.
            let (_, resp) = try PrepareTestFixtures.mutatePrepare(variant: "per-part") { root in
                root["attemptsLeft"] = 3
                let grantSourceDict: [String: Any] = [
                    "source": entry.source,
                    "orderCode": entry.orderCode as Any? ?? NSNull(),
                    "grantedAt": entry.grantedAt as Any? ?? NSNull()
                ]
                root["grantSource"] = grantSourceDict
            }
            let view = PrepareReadySection(
                response: resp,
                metrics: PrepareMetrics.of(resp),
                gate: .open,
                startState: .idle,
                locale: .vi,
                supportContact: .empty,
                showsActionsInline: true,
                onStart: {},
                onContinue: { _ in },
                onBuyMore: { _ in },
                onBackToMyExams: {}
            )
            let r = view.rendered
            XCTAssertEqual(r.title, "Sẵn sàng làm bài")
            XCTAssertEqual(r.attemptsLeftLabel, "Số lượt còn lại")
            XCTAssertEqual(r.attemptsValue, "3 lượt")
            XCTAssertEqual(r.grantSourceLabel, "Lượt này dùng từ")
            XCTAssertEqual(r.grantSourceValue, entry.expected)
            XCTAssertEqual(r.questionsValue, "37 câu")
            XCTAssertEqual(r.durationValue, "15 phút")
            XCTAssertEqual(r.maxPointsValue, "37 điểm")
        }

        let (_, missingMetrics) = try PrepareTestFixtures.mutatePrepare(variant: "class") { root in
            root["attemptsLeft"] = 0
            root["durationMinutes"] = NSNull()
            var parts = (root["parts"] as? [[String: Any]]) ?? []
            for idx in parts.indices {
                parts[idx]["questionCount"] = NSNull()
                parts[idx]["durationMinutes"] = NSNull()
                parts[idx]["maxPoints"] = NSNull()
            }
            root["parts"] = parts
        }
        let viewEmpty = PrepareReadySection(
            response: missingMetrics,
            metrics: PrepareMetrics.of(missingMetrics),
            gate: StartGate.resolve(response: missingMetrics, selection: .empty),
            startState: .idle,
            locale: .vi,
            supportContact: .empty,
            showsActionsInline: true,
            onStart: {},
            onContinue: { _ in },
            onBuyMore: { _ in },
            onBackToMyExams: {}
        )
        let rEmpty = viewEmpty.rendered
        XCTAssertEqual(rEmpty.attemptsValue, "0 lượt")
        XCTAssertEqual(rEmpty.questionsValue, "—")
        XCTAssertEqual(rEmpty.durationValue, "—")
        XCTAssertEqual(rEmpty.maxPointsValue, "—")
        XCTAssertNil(rEmpty.grantSourceLabel, "grantSource row must be hidden when attemptsLeft == 0")
        XCTAssertNil(rEmpty.grantSourceValue)
    }

    func testSubjectPicking() async throws {
        let (data, _) = try PrepareTestFixtures.makeSubjectChoicePrepareData(
            preselectedCode: "SCIENCE",
            chooseSubjects: 2,
            includeSecondFixedOption: true
        )
        let invalidData = try PrepareTestFixtures.loadIndexedData(
            operationId: Operations.start.id,
            status: 422,
            variant: "subjects-invalid"
        )
        let stack = try PrepareTestStackFactory.makeStack { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/entitlements/ent-subj/prepare", data: data)
            transport.registerJSON(method: .post, path: "/api/v1/me/attempts", statusCode: 422, data: invalidData)
        }
        defer { PrepareTestStackFactory.cleanup(stack) }

        let vm = PrepareViewModel(entitlementId: "ent-subj", api: stack.api)
        await vm.loadIfNeeded()
        XCTAssertEqual(vm.state.selection.optionCode, "SCIENCE")

        vm.toggleSubject("LY")
        vm.toggleSubject("HOA")
        XCTAssertEqual(vm.state.gate, .open)

        let response = try XCTUnwrap(vm.loadedResponse)
        let preChoice = try XCTUnwrap(response.preChoice)
        let sectionAtCap = PreparePreChoiceSection(
            preChoice: preChoice,
            selection: vm.state.selection,
            metrics: PrepareMetrics.of(response, selection: vm.state.selection),
            locale: .vi,
            supportContact: .empty,
            onSelectOption: { vm.selectOption($0) },
            onToggleSubject: { vm.toggleSubject($0) }
        )
        let rCap = sectionAtCap.rendered
        XCTAssertEqual(rCap.subjectLegend, "Chọn môn tự chọn")
        XCTAssertEqual(rCap.subjectProgress, "Đã chọn 2/2")
        XCTAssertEqual(rCap.subjects.first(where: { $0.code == "LY" })?.isChecked, true)
        XCTAssertEqual(rCap.subjects.first(where: { $0.code == "LY" })?.isDisabled, false)
        XCTAssertEqual(rCap.subjects.first(where: { $0.code == "SINH" })?.isChecked, false)
        XCTAssertEqual(rCap.subjects.first(where: { $0.code == "SINH" })?.isDisabled, true, "Unchecked subjects must be disabled at cap N")

        vm.toggleSubject("LY")
        XCTAssertEqual(vm.state.selection.subjectCodes, ["HOA"])
        let rUnchecked = PreparePreChoiceSection(
            preChoice: preChoice,
            selection: vm.state.selection,
            metrics: PrepareMetrics.of(response, selection: vm.state.selection),
            locale: .vi,
            supportContact: .empty,
            onSelectOption: { vm.selectOption($0) },
            onToggleSubject: { vm.toggleSubject($0) }
        ).rendered
        XCTAssertEqual(rUnchecked.subjectProgress, "Đã chọn 1/2")
        XCTAssertEqual(rUnchecked.subjects.first(where: { $0.code == "SINH" })?.isDisabled, false, "Unchecking one subject must re-enable others")

        vm.toggleSubject("SINH")
        await vm.startAttemptAndWait()
        XCTAssertEqual(vm.state.start, .refused(.subjectsInvalid))

        vm.selectOption("ANH")
        XCTAssertEqual(vm.state.selection.optionCode, "ANH")
        XCTAssertTrue(vm.state.selection.subjectCodes.isEmpty, "Switching option must clear selected subjects")
        XCTAssertEqual(vm.state.start, .idle, "Switching option must clear displayed start error")
    }

    func testStartBlockedHints() async throws {
        let support = SupportContact(phone: "1900 1234", email: "support@thi360.vn", hours: "8h–21h")

        let (twoOptData, _) = try PrepareTestFixtures.makeSubjectChoicePrepareData(
            preselectedCode: nil,
            chooseSubjects: 2,
            includeSecondFixedOption: true
        )
        let stack1 = try PrepareTestStackFactory.makeStack { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/entitlements/ent-hint/prepare", data: twoOptData)
        }
        defer { PrepareTestStackFactory.cleanup(stack1) }

        let vm1 = PrepareViewModel(entitlementId: "ent-hint", api: stack1.api)
        await vm1.loadIfNeeded()
        XCTAssertEqual(vm1.state.gate, .needOption)

        let viewNeedOpt = PrepareView(
            viewModel: vm1,
            locale: .vi,
            supportContact: support,
            usesNavigationBarChrome: false,
            onOpenAttempt: { _ in },
            onOpenCheckout: { _ in },
            onOpenMyExams: {}
        )
        let actionNeedOpt = try XCTUnwrap(viewNeedOpt.renderedAction)
        XCTAssertFalse(actionNeedOpt.startEnabled)
        XCTAssertEqual(actionNeedOpt.blockedHint, "Vui lòng chọn một phương án trước khi bắt đầu.")

        vm1.selectOption("SCIENCE")
        vm1.toggleSubject("LY")
        XCTAssertEqual(vm1.state.gate, .needSubjects(count: 2))

        let actionNeedSubj = try XCTUnwrap(viewNeedOpt.renderedAction)
        XCTAssertFalse(actionNeedSubj.startEnabled)
        XCTAssertEqual(actionNeedSubj.blockedHint, "Vui lòng chọn đủ 2 môn trước khi bắt đầu.")

        await vm1.startAttemptAndWait()
        XCTAssertEqual(
            stack1.transport.requests(method: .post, path: "/api/v1/me/attempts").count,
            0,
            "Blocked start must never call POST /api/v1/me/attempts"
        )

        let (zeroOptData, _) = try PrepareTestFixtures.mutatePrepare(variant: "pre-choice") { root in
            var pre = (root["preChoice"] as? [String: Any]) ?? [:]
            pre["options"] = []
            root["preChoice"] = pre
        }
        let stack2 = try PrepareTestStackFactory.makeStack { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/entitlements/ent-zero/prepare", data: zeroOptData)
        }
        defer { PrepareTestStackFactory.cleanup(stack2) }

        let vm2 = PrepareViewModel(entitlementId: "ent-zero", api: stack2.api)
        await vm2.loadIfNeeded()
        XCTAssertEqual(vm2.state.gate, .noServingOption)
        await vm2.startAttemptAndWait()
        XCTAssertEqual(stack2.transport.requests(method: .post, path: "/api/v1/me/attempts").count, 0)

        let viewWithContact = PrepareView(
            viewModel: vm2,
            locale: .vi,
            supportContact: support,
            usesNavigationBarChrome: false,
            onOpenAttempt: { _ in },
            onOpenCheckout: { _ in },
            onOpenMyExams: {}
        )
        let preWithContact = try XCTUnwrap(viewWithContact.renderedPreChoice)
        XCTAssertEqual(preWithContact.noServingTitle, "Các mã bạn đã mua hiện không còn được phục vụ.")
        XCTAssertEqual(
            preWithContact.noServingDescription,
            "Vui lòng liên hệ hỗ trợ qua 1900 1234 · support@thi360.vn (8h–21h) để được hoàn tiền hoặc cấp bù."
        )
        XCTAssertEqual(viewWithContact.renderedAction?.startEnabled, false)

        let viewPending = PrepareView(
            viewModel: vm2,
            locale: .vi,
            supportContact: .empty,
            usesNavigationBarChrome: false,
            onOpenAttempt: { _ in },
            onOpenCheckout: { _ in },
            onOpenMyExams: {}
        )
        let prePending = try XCTUnwrap(viewPending.renderedPreChoice)
        XCTAssertEqual(prePending.noServingTitle, "Các mã bạn đã mua hiện không còn được phục vụ.")
        XCTAssertEqual(prePending.noServingDescription, "Thông tin liên hệ hỗ trợ đang được cập nhật.")
    }

    func testOpenAttemptStates() async throws {
        let (inProgressWithPreChoiceData, _) = try PrepareTestFixtures.mutatePrepare(variant: "pre-choice") { root in
            root["openAttempt"] = [
                "attemptId": "00000000-0000-7000-8000-000000000003",
                "state": "IN_PROGRESS"
            ]
        }
        let stack1 = try PrepareTestStackFactory.makeStack { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/entitlements/ent-ip/prepare", data: inProgressWithPreChoiceData)
        }
        defer { PrepareTestStackFactory.cleanup(stack1) }

        let vm1 = PrepareViewModel(entitlementId: "ent-ip", api: stack1.api)
        await vm1.loadIfNeeded()
        XCTAssertEqual(vm1.state.gate, .inProgress(attemptId: "00000000-0000-7000-8000-000000000003"))

        let router = AppRouter(initialDestination: .myExams, initialRoute: .prepare(entitlementId: "ent-ip"))
        let view1 = PrepareView(
            viewModel: vm1,
            locale: .vi,
            usesNavigationBarChrome: false,
            onOpenAttempt: { attemptId in router.openAttemptAfterStart(attemptId: attemptId) },
            onOpenCheckout: { _ in },
            onOpenMyExams: {}
        )
        XCTAssertFalse(view1.showsPreChoice, "IN_PROGRESS openAttempt must hide preChoice block")
        let action1 = try XCTUnwrap(view1.renderedAction)
        XCTAssertEqual(action1.inProgressBanner, "Bạn đang có lượt làm bài chưa hoàn tất. Tiếp tục để làm tiếp, không tạo lượt mới.")
        XCTAssertEqual(action1.continueTitle, "Tiếp tục")
        XCTAssertFalse(action1.showsStartButton)

        if case let .inProgress(attemptId) = vm1.state.gate {
            view1.onOpenAttempt(attemptId)
        }
        XCTAssertEqual(router.presentedAttempt?.attemptId, "00000000-0000-7000-8000-000000000003")
        XCTAssertEqual(stack1.transport.requests(method: .post, path: "/api/v1/me/attempts").count, 0)

        let openStartingData = try PrepareTestFixtures.loadIndexedData(
            operationId: Operations.prepare.id,
            status: 200,
            variant: "open-starting"
        )
        let clock = VirtualPrepareClock()
        let stack2 = try PrepareTestStackFactory.makeStack { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/entitlements/ent-st/prepare", data: openStartingData)
        }
        defer { PrepareTestStackFactory.cleanup(stack2) }

        let vm2 = PrepareViewModel(entitlementId: "ent-st", api: stack2.api, clock: clock)
        defer { vm2.clear() }
        await vm2.loadIfNeeded()

        XCTAssertEqual(vm2.state.waitingAttemptId, "00000000-0000-7000-8000-000000000003")
        if case .polling = vm2.state.waiting {} else {
            XCTFail("Expected direct entry to waiting .polling for openAttempt.state=STARTING")
        }

        let view2 = PrepareView(
            viewModel: vm2,
            locale: .vi,
            usesNavigationBarChrome: false,
            onOpenAttempt: { _ in },
            onOpenCheckout: { _ in },
            onOpenMyExams: {}
        )
        XCTAssertTrue(view2.isShowingWaiting)
    }

    func testExhausted() async throws {
        let cases: [(source: String, servingStatus: String, expectedDesc: String, expectsBuyMore: Bool)] = [
            (
                "CLASS",
                "PUBLISHED",
                "Bạn đã dùng hết số lượt được giao cho bài thi này.",
                false
            ),
            (
                "PURCHASE",
                "PUBLISHED",
                "Bạn có thể mua thêm lượt để tiếp tục luyện tập bài thi này.",
                true
            ),
            (
                "PURCHASE",
                "DISCONTINUED",
                "Bài thi này đã ngừng mở bán nên không thể mua thêm lượt.",
                false
            )
        ]

        for entry in cases {
            let (data, response) = try PrepareTestFixtures.makeExhaustedPrepareData(
                source: entry.source,
                servingStatus: entry.servingStatus
            )
            let stack = try PrepareTestStackFactory.makeStack { transport in
                transport.registerJSON(method: .get, path: "/api/v1/me/entitlements/ent-ex/prepare", data: data)
            }
            defer { PrepareTestStackFactory.cleanup(stack) }

            let router = AppRouter(initialDestination: .myExams, initialRoute: .prepare(entitlementId: "ent-ex"))
            let vm = PrepareViewModel(entitlementId: "ent-ex", api: stack.api)
            await vm.loadIfNeeded()

            let view = PrepareView(
                viewModel: vm,
                locale: .vi,
                usesNavigationBarChrome: false,
                onOpenAttempt: { _ in },
                onOpenCheckout: { code in router.openCheckout(code: code) },
                onOpenMyExams: {}
            )
            let action = try XCTUnwrap(view.renderedAction)
            XCTAssertEqual(action.exhaustedTitle, "Bạn đã dùng hết lượt của bài này.")
            XCTAssertEqual(action.exhaustedDescription, entry.expectedDesc)
            XCTAssertFalse(action.startEnabled, "Start button must be disabled when exhausted")
            XCTAssertEqual(action.showsBuyMore, entry.expectsBuyMore)

            if entry.expectsBuyMore {
                XCTAssertEqual(action.buyMoreTitle, "Mua thêm lượt")
                view.onOpenCheckout(response.examCode)
                XCTAssertTrue(
                    router.path(for: .myExams).contains(.checkout(code: response.examCode)),
                    "Buy more must open checkout/{code} route"
                )
            } else {
                XCTAssertNil(action.buyMoreTitle)
            }
        }
    }

    func testStartRefused() async throws {
        let support = SupportContact(phone: "1900 1234", email: "support@thi360.vn", hours: "8h–21h")
        let (twoOptData, _) = try PrepareTestFixtures.makeSubjectChoicePrepareData(
            preselectedCode: "ANH",
            chooseSubjects: 2,
            includeSecondFixedOption: true
        )

        let refusalFixtures: [(status: Int, data: Data, expectedPrimary: String, expectSupport: Bool)] = [
            (
                409,
                try PrepareTestFixtures.loadIndexedData(operationId: Operations.start.id, status: 409, variant: "no-attempts-left"),
                "Bạn đã dùng hết lượt của bài thi này.",
                false
            ),
            (
                422,
                try PrepareTestFixtures.loadIndexedData(operationId: Operations.start.id, status: 422, variant: "choice-not-allowed"),
                "Lựa chọn hiện tại không hợp lệ hoặc không thuộc phạm vi bạn đã mua.",
                false
            ),
            (
                409,
                try PrepareTestFixtures.loadIndexedData(operationId: Operations.start.id, status: 409, variant: "no-serving-option"),
                "Các phương án bạn đã mua hiện không còn được phục vụ.",
                true
            ),
            (
                422,
                try PrepareTestFixtures.loadIndexedData(operationId: Operations.start.id, status: 422, variant: "subjects-invalid"),
                "Danh sách môn đã chọn chưa đúng số lượng hoặc không hợp lệ.",
                false
            ),
            (
                429,
                try PrepareTestFixtures.makeRateLimited429Data(),
                "Bạn thao tác quá nhanh. Vui lòng chờ một lát rồi thử lại.",
                false
            ),
            (
                500,
                try PrepareTestFixtures.makeInternalServerError500Data(),
                "Không thể bắt đầu bài thi lúc này. Vui lòng thử lại.",
                false
            )
        ]

        for entry in refusalFixtures {
            let stack = try PrepareTestStackFactory.makeStack { transport in
                transport.registerJSON(method: .get, path: "/api/v1/me/entitlements/ent-ref/prepare", data: twoOptData)
                transport.registerJSON(method: .post, path: "/api/v1/me/attempts", statusCode: entry.status, data: entry.data)
            }
            defer { PrepareTestStackFactory.cleanup(stack) }

            let vm = PrepareViewModel(entitlementId: "ent-ref", api: stack.api)
            await vm.loadIfNeeded()
            XCTAssertEqual(vm.state.selection.optionCode, "ANH")

            await vm.startAttemptAndWait()

            XCTAssertEqual(vm.state.selection.optionCode, "ANH", "Selection must be preserved after start refusal")
            XCTAssertEqual(vm.state.gate, .open)

            let view = PrepareView(
                viewModel: vm,
                locale: .vi,
                supportContact: support,
                usesNavigationBarChrome: false,
                onOpenAttempt: { _ in },
                onOpenCheckout: { _ in },
                onOpenMyExams: {}
            )
            let action = try XCTUnwrap(view.renderedAction)
            XCTAssertEqual(action.startError?.primary, entry.expectedPrimary)
            if entry.expectSupport {
                XCTAssertEqual(
                    action.startError?.supportLine,
                    "Vui lòng liên hệ hỗ trợ qua 1900 1234 · support@thi360.vn (8h–21h) để được hoàn tiền hoặc cấp bù."
                )
            } else {
                XCTAssertNil(action.startError?.supportLine)
            }
            XCTAssertTrue(action.startEnabled, "Start button must remain enabled to retry")
        }
    }

    func testReenterStarting() async throws {
        let openStartingData = try PrepareTestFixtures.loadIndexedData(
            operationId: Operations.prepare.id,
            status: 200,
            variant: "open-starting"
        )
        let startingPollData = try PrepareTestFixtures.makeAttemptStateData(
            variant: "starting",
            attemptId: "00000000-0000-7000-8000-000000000003"
        )
        let clock = VirtualPrepareClock()
        let stack = try PrepareTestStackFactory.makeStack { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/entitlements/ent-reenter/prepare", data: openStartingData)
            transport.registerJSON(method: .get, path: "/api/v1/me/attempts/00000000-0000-7000-8000-000000000003", data: startingPollData)
        }
        defer { PrepareTestStackFactory.cleanup(stack) }

        let savedCtx = StartContext(
            entitlementId: "ent-reenter",
            preChoice: nil,
            examTitle: "HSA theo phần Toán (thu nhỏ)",
            examKindLabel: "HSA",
            firstPartName: "Toán học và xử lí số liệu",
            firstPartMinutes: 5,
            timing: "PER_PART"
        )
        try await stack.startContextStore.save(savedCtx, attemptId: "00000000-0000-7000-8000-000000000003")

        let vm = PrepareViewModel(
            entitlementId: "ent-reenter",
            api: stack.api,
            startContextStore: stack.startContextStore,
            clock: clock
        )
        defer { vm.clear() }
        await vm.loadIfNeeded()
        await clock.advance(by: 1.0)

        XCTAssertEqual(vm.state.waitingAttemptId, "00000000-0000-7000-8000-000000000003")
        XCTAssertEqual(vm.state.waitingContext, savedCtx)
        XCTAssertEqual(
            stack.transport.requests(method: .post, path: "/api/v1/me/attempts").count,
            0,
            "Re-entering entitlement with open STARTING attempt must NOT call POST /api/v1/me/attempts"
        )
        XCTAssertEqual(
            stack.transport.requests(method: .get, path: "/api/v1/me/attempts/00000000-0000-7000-8000-000000000003").count,
            1,
            "Re-entering must immediately resume polling existing attemptId"
        )
    }
}
