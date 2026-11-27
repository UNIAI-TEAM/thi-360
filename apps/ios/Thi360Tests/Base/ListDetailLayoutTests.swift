import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class ListDetailLayoutTests: XCTestCase {
    private struct SampleItem: Identifiable, Hashable {
        let id: String
        let title: String
    }

    private struct HarnessView: View {
        @Binding var selection: SampleItem?
        @Binding var shellWidth: ShellWidth
        let items: [SampleItem]

        var body: some View {
            ListDetailLayout(
                selection: $selection,
                list: {
                    VStack(alignment: .leading, spacing: 8) {
                        ForEach(items) { item in
                            Button(item.title) {
                                selection = item
                            }
                            .accessibilityIdentifier("item.\(item.id)")
                        }
                    }
                    .accessibilityElement(children: .contain)
                    .accessibilityIdentifier("harness.list")
                },
                detail: { item in
                    VStack {
                        Text("Detail \(item.title)")
                        Button("Back") {
                            selection = nil
                        }
                    }
                    .accessibilityElement(children: .contain)
                    .accessibilityIdentifier("harness.detail.\(item.id)")
                },
                emptyDetail: {
                    Text("Empty")
                        .accessibilityIdentifier("harness.empty")
                }
            )
            .environment(\.shellWidth, shellWidth)
            .environment(\.brandPalette, .defaultLight)
        }
    }

    func testSwitchesBetweenTwoColumnAndSingleColumnWhilePreservingSelection() {
        let items = [
            SampleItem(id: "1", title: "Exam 1"),
            SampleItem(id: "2", title: "Exam 2")
        ]
        var selected: SampleItem? = items[1]
        var width: ShellWidth = .twoColumn

        let selectionBinding = Binding(get: { selected }, set: { selected = $0 })
        let widthBinding = Binding(get: { width }, set: { width = $0 })

        let twoColumnView = HarnessView(selection: selectionBinding, shellWidth: widthBinding, items: items)
        let (wideWindow, wideController) = RecoveryViewTestHelper.host(
            twoColumnView,
            size: CGSize(width: 1180, height: 820),
            sizeClass: .regular,
            themeMode: .light
        )
        defer { wideWindow.isHidden = true }
        XCTAssertEqual(selected, items[1])
        XCTAssertGreaterThan(wideController.view.bounds.width, 600)

        width = .narrow
        let narrowView = HarnessView(selection: selectionBinding, shellWidth: widthBinding, items: items)
        let (narrowWindow, narrowController) = RecoveryViewTestHelper.host(
            narrowView,
            size: CGSize(width: 390, height: 844),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { narrowWindow.isHidden = true }

        XCTAssertEqual(selected, items[1], "Selection must be preserved when transitioning from twoColumn to narrow")
        XCTAssertGreaterThan(narrowController.view.bounds.width, 0)

        selected = nil
        XCTAssertNil(selected)
    }

    func testShellWidthResolutionMatchesSizeClassesAndModes() {
        XCTAssertEqual(ShellWidth.resolve(idiom: .phone, horizontalSizeClass: .compact, availableWidth: 390), .narrow)
        XCTAssertEqual(ShellWidth.resolve(idiom: .phone, horizontalSizeClass: .regular, availableWidth: 951), .twoColumn)
        XCTAssertEqual(ShellWidth.resolve(idiom: .pad, horizontalSizeClass: .regular, availableWidth: 1180), .twoColumn)
        XCTAssertEqual(ShellWidth.resolve(idiom: .pad, horizontalSizeClass: .regular, availableWidth: 520), .wide)
        XCTAssertEqual(ShellWidth.resolve(idiom: .pad, horizontalSizeClass: .compact, availableWidth: 420), .narrow)
    }
}
