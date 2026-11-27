import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class BundleGroupTests: XCTestCase {
    func testBundleGroupCountsCodeFootnoteViewBundleAndCrossRefJump() async throws {
        let bundleX = try MyExamsTestFixtures.cloneBundleRefTemplate(bundleId: "bundle-x", code: "BX-100", name: "Bộ đề X")
        let bundleY = try MyExamsTestFixtures.cloneBundleRefTemplate(bundleId: "bundle-y", code: "BY-200", name: "Bộ đề Y")
        let bundleZ = try MyExamsTestFixtures.cloneBundleRefTemplate(bundleId: "bundle-z", code: "BZ-300", name: "Bộ đề Z")

        let card1 = try MyExamsTestFixtures.cloneMyExamCardJSON(templateState: "CAN_START") { item in
            item["entitlementId"] = "ent-1"
            item["code"] = "EXAM-1"
            item["name"] = "Đề số 1"
            item["bundles"] = [bundleZ, bundleX]
        }
        let card2 = try MyExamsTestFixtures.cloneMyExamCardJSON(templateState: "CAN_START") { item in
            item["entitlementId"] = "ent-2"
            item["code"] = "EXAM-2"
            item["name"] = "Đề số 2"
            item["bundles"] = [bundleX, bundleY]
        }
        let card3 = try MyExamsTestFixtures.cloneMyExamCardJSON(templateState: "CAN_START") { item in
            item["entitlementId"] = "ent-3"
            item["code"] = "EXAM-3"
            item["name"] = "Đề số 3"
            item["bundles"] = [bundleX]
        }

        let payload = try MyExamsTestFixtures.makeMyExamsPayload(items: [card1, card2, card3])
        let cards = try MyExamsTestFixtures.decodeMyExamCards(from: payload)
        let grouped = MyExamsLayout.group(cards)

        let groupX = try XCTUnwrap(grouped.bundleGroups.first(where: { $0.ref.bundleId == "bundle-x" }))
        XCTAssertEqual(groupX.cards.count, 2)
        XCTAssertEqual(groupX.crossRefs.count, 1)
        XCTAssertEqual(groupX.totalCount, 3)

        let groupY = try XCTUnwrap(grouped.bundleGroups.first(where: { $0.ref.bundleId == "bundle-y" }))
        XCTAssertTrue(groupY.cards.isEmpty)
        XCTAssertEqual(groupY.crossRefs.count, 1)
        XCTAssertEqual(groupY.ref.code, "BY-200")

        let stack = try MyExamsTestStackFactory.makeStack { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/exams", data: payload)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let router = AppRouter(initialDestination: .myExams)
        let feed = MyExamsFeed(
            accountId: "acc-1",
            api: stack.myExamsAPI,
            clock: stack.clock,
            retrySleeper: RecordingRetrySleeper(),
            initialFullName: "An",
            initialUsername: "an"
        )
        await feed.loadOnAppear()

        var focusedTargetBundleId: String?
        var reportedFrames: [String: CGRect] = [:]
        let screen = MyExamsScreen(
            feed: feed,
            router: router,
            locale: .vi,
            showsBrandHeader: false,
            onFocusBundleGroup: { focusedTargetBundleId = $0 }
        )
        let (window, _) = CatalogViewInspector.host(
            screen
                .environment(\.brandPalette, .defaultLight)
                .environment(\.statusPalette, .light)
                .environment(\.myExamsLayoutFrameObserver, { id, frame in reportedFrames[id] = frame }),
            size: CGSize(width: 390, height: 2600),
            settleSeconds: 0.15
        )
        defer { window.isHidden = true }

        let text = screen.renderedStrings.joined(separator: "\n")
        XCTAssertTrue(text.contains("Bộ đề X"))
        XCTAssertTrue(text.contains("3 bài"))
        XCTAssertTrue(text.contains("BX-100"))
        XCTAssertTrue(text.contains("Bộ đề Y"))
        XCTAssertTrue(text.contains("BY-200"))
        XCTAssertTrue(text.contains(MyExamsStrings.text("bundle.home.groupFootnote", locale: .vi)))

        XCTAssertNotNil(reportedFrames["myExams.crossRef.bundle-y.EXAM-2"], "Cross-ref row for EXAM-2 in cross-ref-only group Y must lay out")
        XCTAssertNotNil(reportedFrames["myExams.bundleGroup.viewBundle.bundle-y"], "View bundle button for cross-ref-only group Y must lay out")

        let nav = MyExamsNavigationHandler(router: router, clock: stack.clock)
        nav.openBundleDetail(code: groupY.ref.code)
        XCTAssertEqual(router.path(for: .myExams), [.product(code: "BY-200")])

        router.back()
        stack.clock.advance(by: 1.0)
        nav.openBundleDetail(code: groupX.ref.code)
        XCTAssertEqual(router.path(for: .myExams), [.product(code: "BX-100")])

        let ySection = BundleGroupSectionView(
            group: groupY,
            isRegularWidth: false,
            locale: .vi,
            focusedBundleId: AccessibilityFocusState<String?>().projectedValue,
            onViewBundle: { router.openProduct(code: $0) },
            onCardAction: { _ in },
            onJumpToBundle: { focusedTargetBundleId = $0 }
        )
        let (yWindow, _) = CatalogViewInspector.host(
            ySection
                .environment(\.brandPalette, .defaultLight)
                .environment(\.statusPalette, .light),
            size: CGSize(width: 390, height: 400),
            settleSeconds: 0.08
        )
        defer { yWindow.isHidden = true }
        let yText = ySection.renderedStrings.joined(separator: "\n")
        XCTAssertTrue(yText.contains("Đề số 2"))
        XCTAssertTrue(yText.contains("Bộ đề X"))
        XCTAssertTrue(yText.contains(MyExamsStrings.text("bundle.home.groupFootnote", locale: .vi)))

        ySection.onJumpToBundle(groupY.crossRefs[0].primaryBundle.bundleId)
        XCTAssertEqual(focusedTargetBundleId, "bundle-x")
    }
}
