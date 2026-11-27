import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class TwoColumnLayoutTests: XCTestCase {
    func testRendersSideBySideInTwoColumnAndStackedInNarrow() {
        let twoCol = TwoColumnLayout(
            start: {
                Text("Start pane")
                    .frame(maxWidth: .infinity, minHeight: 120)
            },
            end: {
                Text("End pane")
                    .frame(maxWidth: .infinity, minHeight: 120)
            }
        )
        .environment(\.shellWidth, .twoColumn)

        let (wideWindow, wideController) = RecoveryViewTestHelper.host(
            twoCol,
            size: CGSize(width: 1000, height: 600),
            sizeClass: .regular,
            themeMode: .light
        )
        defer { wideWindow.isHidden = true }
        XCTAssertEqual(wideController.view.bounds.width, 1000, accuracy: 1)

        let narrow = TwoColumnLayout(
            start: {
                Text("Start pane")
                    .frame(maxWidth: .infinity, minHeight: 120)
            },
            end: {
                Text("End pane")
                    .frame(maxWidth: .infinity, minHeight: 120)
            }
        )
        .environment(\.shellWidth, .narrow)

        let (narrowWindow, narrowController) = RecoveryViewTestHelper.host(
            narrow,
            size: CGSize(width: 390, height: 844),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { narrowWindow.isHidden = true }
        XCTAssertEqual(narrowController.view.bounds.width, 390, accuracy: 1)
    }
}
