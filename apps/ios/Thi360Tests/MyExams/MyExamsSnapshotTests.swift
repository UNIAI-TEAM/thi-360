import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class MyExamsSnapshotTests: XCTestCase {
    private struct Viewport {
        let name: String
        let size: CGSize
        let sizeClass: UIUserInterfaceSizeClass
        let shellWidth: ShellWidth
    }

    private let viewports: [Viewport] = [
        Viewport(name: "iphone-390x844", size: CGSize(width: 390, height: 844), sizeClass: .compact, shellWidth: .narrow),
        Viewport(name: "ipad-landscape-1180x820", size: CGSize(width: 1180, height: 820), sizeClass: .regular, shellWidth: .twoColumn),
        Viewport(name: "ipad-portrait-820x1180", size: CGSize(width: 820, height: 1180), sizeClass: .regular, shellWidth: .twoColumn),
        Viewport(name: "duo-folded-466x678", size: CGSize(width: 466, height: 678), sizeClass: .compact, shellWidth: .narrow),
        Viewport(name: "duo-unfolded-669x951", size: CGSize(width: 669, height: 951), sizeClass: .regular, shellWidth: .twoColumn)
    ]

    func testMyExamsSnapshotMatrixLightAndDark() async throws {
        let myExamsData = try MyExamsTestFixtures.loadIndexedData(operationId: Operations.listMyExams.id, status: 200, variant: "")
        let cards = try MyExamsTestFixtures.decodeMyExamCards(from: myExamsData)
        let loadedTime = ISO8601DateFormatter().date(from: "2026-11-20T02:05:00Z")!

        let stack = try MyExamsTestStackFactory.makeStack { transport in
            try MyExamsTestStackFactory.registerDefaultMyExamsAndHistoryFixtures(on: transport)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let router = AppRouter(initialDestination: .myExams)

        let defaultFeed = MyExamsFeed(accountId: "acc-1", api: PreviewEmptyMyExamsAPI(), initialFullName: "Nguyễn Minh Anh", initialUsername: "hv01")
        defaultFeed.setLoadedStateForTesting(items: cards, lastLoadedAt: loadedTime, stale: false)

        let staleFeed = MyExamsFeed(accountId: "acc-1", api: PreviewEmptyMyExamsAPI(), initialFullName: "Nguyễn Minh Anh", initialUsername: "hv01")
        staleFeed.setLoadedStateForTesting(items: cards, lastLoadedAt: loadedTime, stale: true)

        let emptyFeed = MyExamsFeed(accountId: "acc-1", api: PreviewEmptyMyExamsAPI(), initialFullName: "Nguyễn Minh Anh", initialUsername: "hv01")
        emptyFeed.setLoadedStateForTesting(items: [], lastLoadedAt: loadedTime, stale: false)

        let errorFeed = MyExamsFeed(accountId: "acc-1", api: PreviewEmptyMyExamsAPI(), initialFullName: "Nguyễn Minh Anh", initialUsername: "hv01")
        errorFeed.setFailedStateForTesting(kind: .server)

        let states: [(String, MyExamsFeed)] = [
            ("default", defaultFeed),
            ("empty", emptyFeed),
            ("stale", staleFeed),
            ("error", errorFeed)
        ]

        for (stateName, feed) in states {
            for viewport in viewports {
                try assertLightAndDarkSnapshots(
                    prefix: "myexams-\(stateName)-\(viewport.name)",
                    viewport: viewport
                ) {
                    MyExamsScreen(
                        feed: feed,
                        router: router,
                        locale: .vi,
                        showsBrandHeader: viewport.shellWidth == .narrow,
                        supportContact: SupportContact(phone: "1900 1234", email: "hotro@thi360.vn", hours: "8:00–21:00")
                    )
                }
            }
        }
    }

    private func assertLightAndDarkSnapshots<Content: View>(
        prefix: String,
        viewport: Viewport,
        @ViewBuilder content: () -> Content
    ) throws {
        let lightPNG = try captureSnapshot(
            name: "\(prefix)-light",
            viewport: viewport,
            style: .light,
            palette: .defaultLight,
            statusPalette: .light,
            content: content()
        )
        let darkPNG = try captureSnapshot(
            name: "\(prefix)-dark",
            viewport: viewport,
            style: .dark,
            palette: .defaultDark,
            statusPalette: .dark,
            content: content()
        )
        XCTAssertNotEqual(lightPNG, darkPNG, "Light and dark snapshots for \(prefix) must differ visually")
    }

    private func captureSnapshot<Content: View>(
        name: String,
        viewport: Viewport,
        style: UIUserInterfaceStyle,
        palette: BrandPalette,
        statusPalette: StatusPalette,
        content: Content
    ) throws -> Data {
        let wrapped = content
            .background(palette.bgPageColor.ignoresSafeArea())
            .environment(\.brandPalette, palette)
            .environment(\.statusPalette, statusPalette)
            .environment(\.shellWidth, viewport.shellWidth)

        let (window, controller) = CatalogViewInspector.host(
            wrapped,
            size: viewport.size,
            style: style,
            sizeClass: viewport.sizeClass,
            settleSeconds: 0.08
        )
        defer { window.isHidden = true }

        let scrollViews = RecoveryViewTestHelper.findViews(ofType: UIScrollView.self, in: controller.view)
        for scrollView in scrollViews where scrollView.contentSize.width > 0 {
            XCTAssertLessThanOrEqual(
                scrollView.contentSize.width,
                viewport.size.width + 1.0,
                "\(name) UIScrollView contentSize.width (\(scrollView.contentSize.width)) overflows \(viewport.size.width)"
            )
        }

        let (image, png) = try CatalogViewInspector.renderPNG(window: window, size: viewport.size)
        XCTAssertGreaterThan(png.count, 2048, "Snapshot \(name) unexpectedly empty")

        let attachment = XCTAttachment(image: image)
        attachment.name = "\(name).png"
        attachment.lifetime = .keepAlways
        add(attachment)

        let env = ProcessInfo.processInfo.environment
        let outputDir = [env["TEST_RUNNER_THI360_SNAPSHOT_DIR"], env["THI360_SNAPSHOT_DIR"]]
            .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .first(where: { !$0.isEmpty })

        if let outputDir {
            let dirURL = URL(fileURLWithPath: outputDir, isDirectory: true)
            try FileManager.default.createDirectory(at: dirURL, withIntermediateDirectories: true)
            try png.write(to: dirURL.appendingPathComponent("\(name).png"))
        }

        return png
    }
}
