import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class RichImageTests: XCTestCase {
    private let attemptId = "33333333-3333-4333-8333-333333333333"
    private let assetId = "9b8f8b1f-9b8f-4b8f-9b8f-9b8f8b8f8b8f"

    override func tearDown() {
        RecordingAssetURLProtocol.reset()
        super.tearDown()
    }

    private func samplePNGData() -> Data {
        let bundle = Bundle(for: RichImageTests.self)
        if let url = bundle.url(forResource: "sample-chart", withExtension: "png"),
           let data = try? Data(contentsOf: url)
        {
            return data
        }
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 240, height: 160))
        return renderer.image { ctx in
            UIColor(red: 0.05, green: 0.59, blue: 0.41, alpha: 1).setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: 240, height: 160))
        }.pngData()!
    }

    private func makeAssetSource() -> AttemptAssetSource {
        let tokenStore = InMemoryTokenStore(
            initialTokens: SessionTokens(
                accessToken: "img-token",
                refreshToken: "img-refresh",
                accessExpiresIn: 3600,
                refreshExpiresIn: 86_400,
                mustChangePassword: false,
                username: "student1",
                accountId: "acc-img"
            )
        )
        let refresher = TokenRefresher(
            tokenStore: tokenStore,
            refreshExecutor: { _ in throw MobileApiError.unexpectedResponse(statusCode: 401) }
        )
        let session = AttemptAssetSource.makeNoDiskCacheSession(
            protocolClasses: [RecordingAssetURLProtocol.self]
        )
        return AttemptAssetSource(
            attemptId: attemptId,
            serverURL: URL(string: "https://api.thi360.test")!,
            tokenStore: tokenStore,
            refresher: refresher,
            session: session
        )
    }

    func testMediumIs70PercentCentered() async throws {
        let png = samplePNGData()
        RecordingAssetURLProtocol.reset()
        RecordingAssetURLProtocol.handler = { req in
            let response = HTTPURLResponse(
                url: req.url!,
                statusCode: 200,
                httpVersion: "HTTP/1.1",
                headerFields: ["Content-Type": "image/png"]
            )!
            return (response, png)
        }

        let source = makeAssetSource()
        defer { source.purgeAll() }
        _ = try await source.load(.attempt(assetId: assetId))

        let html = """
        <figure class="image" data-width="medium" data-zoom="true">
          <img src="/api/v1/admin/question-assets/\(assetId)" alt="Biểu đồ số lượng lợn 2015 - 2021" data-asset-id="\(assetId)">
          <figcaption>Biểu đồ số lượng lợn và sản lượng thịt lợn</figcaption>
          <p class="figure-source">Nguồn: Số liệu theo https://www.gso.gov.vn</p>
        </figure>
        """
        let doc = RichHTMLParser.parse(html)
        let columnWidth: CGFloat = 358
        let view = RichContentView(document: doc, assets: source, fixedWidth: columnWidth)
            .padding(16)
            .background(BrandPalette.defaultLight.bgSurfaceColor)
            .environment(\.brandPalette, .defaultLight)
            .environment(\.statusPalette, .light)

        let (window, controller) = RecoveryViewTestHelper.host(
            view,
            size: CGSize(width: 390, height: 480),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { window.isHidden = true }

        try await Task.sleep(nanoseconds: 150_000_000)
        window.layoutIfNeeded()

        let expectedInnerWidth = columnWidth - 24
        let expectedMediumWidth = floor(expectedInnerWidth * 0.7)

        let imageViews = RecoveryViewTestHelper.findViews(ofType: UIImageView.self, in: controller.view)
            .filter { $0.bounds.width > 50 }
        let chartView = try XCTUnwrap(imageViews.first, "Expected loaded UIImageView for figure.image")
        let chartFrame = chartView.convert(chartView.bounds, to: window)
        XCTAssertEqual(chartFrame.width, expectedMediumWidth, accuracy: 4.0, "medium image must be ~70% of figure inner column width")

        let centerDelta = abs(chartFrame.midX - window.bounds.midX)
        XCTAssertLessThanOrEqual(centerDelta, 4.0, "medium image must be horizontally centered")

        let textViews = RecoveryViewTestHelper.findViews(ofType: RichTextKitTextView.self, in: controller.view)
        let captionTV = try XCTUnwrap(textViews.first { $0.customAccessibilityDescription.contains("Biểu đồ số lượng lợn") })
        let captionFrame = captionTV.convert(captionTV.bounds, to: window)
        XCTAssertGreaterThanOrEqual(captionFrame.minY, chartFrame.maxY - 2, "Caption must appear below the image")
    }

    func testErrorThenTapRetries() async throws {
        let indexEntries = try ContractFakeTransport.loadIndex()
        let notFoundEntry = try XCTUnwrap(
            indexEntries.first { $0.operationId == "downloadAttemptAsset" && $0.status == 404 }
        )
        let notFoundData = try ContractFixtureLoader.loadData(relativePath: notFoundEntry.file)
        let png = samplePNGData()

        let lock = NSLock()
        var attempts = 0
        RecordingAssetURLProtocol.reset()
        RecordingAssetURLProtocol.handler = { req in
            lock.lock()
            attempts += 1
            let current = attempts
            lock.unlock()

            if current == 1 {
                let resp = HTTPURLResponse(
                    url: req.url!,
                    statusCode: 404,
                    httpVersion: "HTTP/1.1",
                    headerFields: ["Content-Type": "application/json"]
                )!
                return (resp, notFoundData)
            }
            let resp = HTTPURLResponse(
                url: req.url!,
                statusCode: 200,
                httpVersion: "HTTP/1.1",
                headerFields: ["Content-Type": "image/png"]
            )!
            return (resp, png)
        }

        let source = makeAssetSource()
        defer { source.purgeAll() }

        let html = """
        <p>Nội dung đề vẫn giữ nguyên</p>
        <figure class="image" data-width="medium" data-zoom="false">
          <img src="/api/v1/admin/question-assets/\(assetId)" alt="Hình minh họa" data-asset-id="\(assetId)">
        </figure>
        """
        let doc = RichHTMLParser.parse(html)
        let view = RichContentView(document: doc, assets: source, fixedWidth: 358)
            .padding(16)
            .environment(\.brandPalette, .defaultLight)
            .environment(\.statusPalette, .light)

        let (window, controller) = RecoveryViewTestHelper.host(
            view,
            size: CGSize(width: 390, height: 420),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { window.isHidden = true }

        let waitDeadline = Date().addingTimeInterval(3)
        while RecordingAssetURLProtocol.snapshotRequests().isEmpty && Date() < waitDeadline {
            try await Task.sleep(nanoseconds: 50_000_000)
        }
        try await Task.sleep(nanoseconds: 150_000_000)

        XCTAssertEqual(RecordingAssetURLProtocol.snapshotRequests().count, 1)
        let preservedText = RecoveryViewTestHelper.findViews(ofType: RichTextKitTextView.self, in: controller.view)
        XCTAssertTrue(preservedText.contains { $0.customAccessibilityDescription.contains("Nội dung đề vẫn giữ nguyên") })

        let retryData = try await source.load(.attempt(assetId: assetId))
        XCTAssertEqual(retryData, png)
        let requests = RecordingAssetURLProtocol.snapshotRequests()
        XCTAssertEqual(requests.count, 2)
        XCTAssertEqual(requests[0].url?.path, requests[1].url?.path, "Retry must call the exact same attempt asset path")
    }
}
