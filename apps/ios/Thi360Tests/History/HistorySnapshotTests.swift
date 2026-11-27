import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class HistorySnapshotTests: XCTestCase {
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

    func testHistorySnapshotMatrixLightAndDark() async throws {
        let attemptsData = try MyExamsTestFixtures.loadIndexedData(operationId: Operations.listHistory.id, status: 200, variant: "")
        let resp = try MyExamsTestFixtures.decodeHistoryPage(from: attemptsData)
        let loadedTime = ISO8601DateFormatter().date(from: "2026-11-20T02:05:00Z")!

        let page = HistoryPage(items: resp.items, page: 0, totalItems: resp.totalItems, totalPages: 1, hasMore: false, isLoadingMore: false)
        let router = AppRouter(initialDestination: .history)

        let defaultFeed = HistoryFeed(accountId: "acc-1", api: PreviewEmptyHistoryAPI())
        defaultFeed.setLoadedStateForTesting(page: page, lastLoadedAt: loadedTime, stale: false)

        let emptyFeed = HistoryFeed(accountId: "acc-1", api: PreviewEmptyHistoryAPI())
        emptyFeed.setEmptyStateForTesting(filter: .empty)

        let noMatchFeed = HistoryFeed(accountId: "acc-1", api: PreviewEmptyHistoryAPI())
        noMatchFeed.setEmptyStateForTesting(filter: HistoryFilter(q: "hsa", scope: .part))

        let staleFeed = HistoryFeed(accountId: "acc-1", api: PreviewEmptyHistoryAPI())
        staleFeed.setLoadedStateForTesting(page: page, lastLoadedAt: loadedTime, stale: true)

        let errorFeed = HistoryFeed(accountId: "acc-1", api: PreviewEmptyHistoryAPI())
        errorFeed.setFailedStateForTesting(kind: .server)

        let states: [(String, HistoryFeed)] = [
            ("default", defaultFeed),
            ("empty", emptyFeed),
            ("nomatch", noMatchFeed),
            ("stale", staleFeed),
            ("error", errorFeed)
        ]

        for (stateName, feed) in states {
            for viewport in viewports {
                try assertLightAndDarkSnapshots(
                    prefix: "history-\(stateName)-\(viewport.name)",
                    viewport: viewport
                ) {
                    HistoryScreen(
                        feed: feed,
                        router: router,
                        resetFilters: false,
                        locale: .vi,
                        showsBrandHeader: viewport.shellWidth == .narrow
                    )
                }
                XCTAssertFalse(feed.isSwitchingFilter, "Snapshot state '\(stateName)' must remain at rest")
                XCTAssertFalse(feed.isRetryingStale, "Snapshot state '\(stateName)' must remain at rest")
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
