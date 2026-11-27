import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class CheckingCardTests: XCTestCase {
    func testFourMessageCombinationsSupportContactOrderNavigationAndReloadTransition() async throws {
        let withSupport = SupportContact(phone: "1900 1234", email: "hotro@thi360.vn", hours: "8:00–21:00")
        let noSupport = SupportContact.empty

        let withOrderJSON = try MyExamsTestFixtures.cloneMyExamCardJSON(templateState: "CHECKING") { item in
            item["code"] = "CHK-ORDER"
            item["name"] = "Đề HSA 01"
            item["pendingOrderCode"] = "DH001"
            item["examKind"] = ["code": "HSA", "nameVi": "Đánh giá năng lực HSA", "nameEn": ""]
        }
        let noOrderJSON = try MyExamsTestFixtures.cloneMyExamCardJSON(templateState: "CHECKING") { item in
            item["code"] = "CHK-NO-ORDER"
            item["name"] = "Đề TSA 02"
            item["pendingOrderCode"] = NSNull()
            item["examKind"] = ["code": "TSA", "nameVi": "", "nameEn": ""]
        }

        let checkingPayload = try MyExamsTestFixtures.makeMyExamsPayload(items: [withOrderJSON, noOrderJSON])
        let cards = try MyExamsTestFixtures.decodeMyExamCards(from: checkingPayload)
        let cardWithOrder = cards[0]
        let cardNoOrder = cards[1]

        XCTAssertEqual(cardWithOrder.examKind.displayName(for: .vi), "Đánh giá năng lực HSA")
        XCTAssertEqual(cardWithOrder.examKind.displayName(for: .en), "Đánh giá năng lực HSA")
        XCTAssertEqual(cardNoOrder.examKind.displayName(for: .vi), "TSA")
        XCTAssertEqual(cardNoOrder.examKind.displayName(for: .en), "TSA")

        let msgOrderSupport = CheckingMessage.of(cardWithOrder, support: withSupport, locale: .vi)
        XCTAssertEqual(msgOrderSupport.messageKey, "home.checking.message")
        XCTAssertEqual(msgOrderSupport.viewOrderCode, "DH001")
        XCTAssertEqual(
            msgOrderSupport.text,
            "Đã nhận thanh toán cho đơn DH001. Bài thi đang được cấp cho tài khoản của bạn, bạn không cần thanh toán lại. Tải lại màn hình sau ít phút; nếu vẫn chưa thấy bài, liên hệ hỗ trợ qua 1900 1234 · hotro@thi360.vn (8:00–21:00) và gửi kèm mã đơn."
        )

        let msgOrderNoSupport = CheckingMessage.of(cardWithOrder, support: noSupport, locale: .vi)
        XCTAssertEqual(msgOrderNoSupport.messageKey, "home.checking.messageNoContact")
        XCTAssertEqual(msgOrderNoSupport.viewOrderCode, "DH001")
        XCTAssertEqual(
            msgOrderNoSupport.text,
            "Đã nhận thanh toán cho đơn DH001. Bài thi đang được cấp cho tài khoản của bạn, bạn không cần thanh toán lại. Tải lại màn hình sau ít phút; nếu vẫn chưa thấy bài, hãy liên hệ hỗ trợ kèm mã đơn. Thông tin liên hệ đang được cập nhật."
        )

        let msgNoOrderSupport = CheckingMessage.of(cardNoOrder, support: withSupport, locale: .vi)
        XCTAssertEqual(msgNoOrderSupport.messageKey, "home.checking.messageNoOrder")
        XCTAssertNil(msgNoOrderSupport.viewOrderCode)
        XCTAssertEqual(
            msgNoOrderSupport.text,
            "Đã nhận thanh toán cho đơn hàng của bạn. Bài thi đang được cấp cho tài khoản, bạn không cần thanh toán lại. Tải lại màn hình sau ít phút; nếu vẫn chưa thấy bài, liên hệ hỗ trợ qua 1900 1234 · hotro@thi360.vn (8:00–21:00)."
        )

        let msgNoOrderNoSupport = CheckingMessage.of(cardNoOrder, support: noSupport, locale: .vi)
        XCTAssertEqual(msgNoOrderNoSupport.messageKey, "home.checking.messageNoOrderNoContact")
        XCTAssertNil(msgNoOrderNoSupport.viewOrderCode)
        XCTAssertEqual(
            msgNoOrderNoSupport.text,
            "Đã nhận thanh toán cho đơn hàng của bạn. Bài thi đang được cấp cho tài khoản, bạn không cần thanh toán lại. Tải lại màn hình sau ít phút; nếu vẫn chưa thấy bài, hãy liên hệ hỗ trợ. Thông tin liên hệ đang được cập nhật."
        )

        XCTAssertEqual(
            CheckingMessage.of(cardWithOrder, support: withSupport, locale: .en).text,
            "Payment received for order DH001. The exam is being granted to your account and you do not need to pay again. Reload the screen in a few minutes; if the exam still does not appear, contact support via 1900 1234 · hotro@thi360.vn (8:00–21:00) and include your order code."
        )
        XCTAssertEqual(
            CheckingMessage.of(cardWithOrder, support: noSupport, locale: .en).text,
            "Payment received for order DH001. The exam is being granted to your account and you do not need to pay again. Reload the screen in a few minutes; if the exam still does not appear, contact support with your order code. Contact information is being updated."
        )
        XCTAssertEqual(
            CheckingMessage.of(cardNoOrder, support: withSupport, locale: .en).text,
            "Payment has been received for your order. The exam is being granted to your account and you do not need to pay again. Reload the screen in a few minutes; if the exam still does not appear, contact support via 1900 1234 · hotro@thi360.vn (8:00–21:00)."
        )
        XCTAssertEqual(
            CheckingMessage.of(cardNoOrder, support: noSupport, locale: .en).text,
            "Payment has been received for your order. The exam is being granted to your account and you do not need to pay again. Reload the screen in a few minutes; if the exam still does not appear, contact support. Contact information is being updated."
        )

        let router = AppRouter(initialDestination: .myExams)
        let nav = MyExamsNavigationHandler(router: router)
        var frames1: [String: CGRect] = [:]
        let withOrderView = CheckingExamCardView(
            card: cardWithOrder,
            supportContact: withSupport,
            isReloading: false,
            locale: .vi,
            onViewOrder: { nav.openOrderDetail(orderCode: $0) },
            onReload: {}
        )

        let (win1, _) = CatalogViewInspector.host(
            withOrderView
                .environment(\.brandPalette, .defaultLight)
                .environment(\.statusPalette, .light)
                .environment(\.myExamsLayoutFrameObserver, { id, frame in frames1[id] = frame }),
            size: CGSize(width: 390, height: 320)
        )
        defer { win1.isHidden = true }
        let text1 = withOrderView.renderedStrings.joined(separator: "\n")
        XCTAssertTrue(text1.contains("Đang kiểm tra"))
        XCTAssertTrue(text1.contains("Đề HSA 01"))
        XCTAssertTrue(withOrderView.hasViewOrderButton)
        XCTAssertNotNil(frames1["myExams.checkingCard.viewOrder.DH001"])
        XCTAssertNotNil(frames1["myExams.checkingCard.reload.CHK-ORDER"])

        withOrderView.onViewOrder("DH001")
        XCTAssertEqual(router.path(for: .myExams), [.order(orderCode: "DH001")])

        var frames2: [String: CGRect] = [:]
        let noOrderView = CheckingExamCardView(
            card: cardNoOrder,
            supportContact: noSupport,
            isReloading: false,
            locale: .vi,
            onViewOrder: { _ in },
            onReload: {}
        )

        let (win2, _) = CatalogViewInspector.host(
            noOrderView
                .environment(\.brandPalette, .defaultLight)
                .environment(\.statusPalette, .light)
                .environment(\.myExamsLayoutFrameObserver, { id, frame in frames2[id] = frame }),
            size: CGSize(width: 390, height: 320)
        )
        defer { win2.isHidden = true }
        XCTAssertFalse(noOrderView.hasViewOrderButton)
        XCTAssertNil(frames2["myExams.checkingCard.viewOrder.DH001"])
        XCTAssertNotNil(frames2["myExams.checkingCard.reload.CHK-NO-ORDER"])

        let grantedCardJSON = try MyExamsTestFixtures.cloneMyExamCardJSON(templateState: "CAN_START") { item in
            item["entitlementId"] = "ent-granted-1"
            item["code"] = "CHK-ORDER"
            item["name"] = "Đề HSA 01"
            item["bundles"] = []
        }
        let grantedPayload = try MyExamsTestFixtures.makeMyExamsPayload(items: [grantedCardJSON])
        let reloadGate = TestAsyncGate()

        let stack = try MyExamsTestStackFactory.makeStack { transport in
            transport.enqueue(method: .get, path: "/api/v1/me/exams", handlers: [
                { _ in ContractFakeTransport.jsonResponse(statusCode: 200, data: try MyExamsTestFixtures.makeMyExamsPayload(items: [withOrderJSON])) },
                { _ in
                    await reloadGate.enterAndWait()
                    return ContractFakeTransport.jsonResponse(statusCode: 200, data: grantedPayload)
                }
            ])
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let feed = MyExamsFeed(
            accountId: "acc-1",
            api: stack.myExamsAPI,
            clock: stack.clock,
            retrySleeper: RecordingRetrySleeper(),
            initialFullName: "An",
            initialUsername: "an"
        )
        await feed.loadOnAppear()
        if case let .loaded(initialCards, _) = feed.loadState {
            XCTAssertEqual(MyExamsLayout.group(initialCards).checking.count, 1)
        } else {
            XCTFail("Expected checking card loaded")
        }

        let reloadTask = Task { await feed.reloadFromCheckingCard() }
        await reloadGate.waitUntilEntered()
        XCTAssertTrue(feed.isReloading)
        await feed.reloadFromCheckingCard()
        XCTAssertEqual(stack.transport.callCount(method: .get, path: "/api/v1/me/exams"), 2, "Second tap on checking reload while busy must be ignored")

        await reloadGate.release()
        await reloadTask.value
        XCTAssertFalse(feed.isReloading)

        guard case let .loaded(finalCards, _) = feed.loadState else {
            return XCTFail("Expected loaded state after grant")
        }
        let grouped = MyExamsLayout.group(finalCards)
        XCTAssertTrue(grouped.checking.isEmpty, "Checking card must disappear once entitlement is granted")
        XCTAssertEqual(grouped.standalone.map(\.code), ["CHK-ORDER"])
    }

    func testMyExamsStringCatalogMatchesWebMessages() throws {
        let repoRoot = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
        let viData = try Data(contentsOf: repoRoot.appendingPathComponent("apps/web-student/src/messages/vi.json"))
        let enData = try Data(contentsOf: repoRoot.appendingPathComponent("apps/web-student/src/messages/en.json"))
        let xcData = try Data(contentsOf: repoRoot.appendingPathComponent("apps/ios/Thi360/Resources/MyExams.xcstrings"))

        let viWeb = try XCTUnwrap(JSONSerialization.jsonObject(with: viData) as? [String: Any])
        let enWeb = try XCTUnwrap(JSONSerialization.jsonObject(with: enData) as? [String: Any])
        let xcRoot = try XCTUnwrap(JSONSerialization.jsonObject(with: xcData) as? [String: Any])
        let strings = try XCTUnwrap(xcRoot["strings"] as? [String: [String: Any]])

        func lookup(_ root: [String: Any], dottedKey: String) -> String? {
            var current: Any = root
            for part in dottedKey.split(separator: ".").map(String.init) {
                guard let dict = current as? [String: Any], let next = dict[part] else { return nil }
                current = next
            }
            return current as? String
        }

        var comparedCount = 0
        for (key, entry) in strings {
            guard let localizations = entry["localizations"] as? [String: [String: Any]] else { continue }
            for (lang, webTree) in [("vi", viWeb), ("en", enWeb)] {
                guard let webVal = lookup(webTree, dottedKey: key),
                      let stringUnit = localizations[lang]?["stringUnit"] as? [String: Any],
                      let iosVal = stringUnit["value"] as? String
                else {
                    continue
                }
                let expected = key.hasPrefix("home.checking.")
                    ? webVal
                        .replacingOccurrences(of: "Tải lại trang", with: "Tải lại màn hình")
                        .replacingOccurrences(of: "Reload the page", with: "Reload the screen")
                    : webVal
                XCTAssertEqual(iosVal, expected, "Mismatch in MyExams.xcstrings for \(key) [\(lang)]")
                comparedCount += 1
            }
        }
        XCTAssertGreaterThan(comparedCount, 120)
    }
}
