import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class RichSamplesSnapshotTests: XCTestCase {
    private struct SampleFixtureFile: Decodable {
        let assetId: String
        let samples: [Sample]
        struct Sample: Decodable {
            let name: String
            let valid: Bool
            let allowImages: Bool
            let html: String
        }
    }

    override func tearDown() {
        RecordingAssetURLProtocol.reset()
        super.tearDown()
    }

    func testAll43SamplesSnapshotsLightAndDarkOnIPhone17() async throws {
        let data = try Data(contentsOf: FormulaCorpusLoader.richTextSamplesURL)
        let file = try JSONDecoder().decode(SampleFixtureFile.self, from: data)
        XCTAssertEqual(file.samples.count, 43, "Expected 43 HTML samples (12 valid + 31 invalid)")

        let chartPNG = loadSampleChartPNG()
        RecordingAssetURLProtocol.reset()
        RecordingAssetURLProtocol.handler = { req in
            let resp = HTTPURLResponse(
                url: req.url!,
                statusCode: 200,
                httpVersion: "HTTP/1.1",
                headerFields: ["Content-Type": "image/png"]
            )!
            return (resp, chartPNG)
        }

        let tokenStore = InMemoryTokenStore(
            initialTokens: SessionTokens(
                accessToken: "snap-token",
                refreshToken: "snap-refresh",
                accessExpiresIn: 3600,
                refreshExpiresIn: 86_400,
                mustChangePassword: false
            )
        )
        let refresher = TokenRefresher(
            tokenStore: tokenStore,
            refreshExecutor: { _ in throw MobileApiError.unexpectedResponse(statusCode: 401) }
        )
        let assetSource = AttemptAssetSource(
            attemptId: "55555555-5555-4555-8555-555555555555",
            serverURL: URL(string: "https://api.thi360.test")!,
            tokenStore: tokenStore,
            refresher: refresher,
            session: AttemptAssetSource.makeNoDiskCacheSession(protocolClasses: [RecordingAssetURLProtocol.self])
        )
        defer { assetSource.purgeAll() }
        _ = try await assetSource.load(.attempt(assetId: file.assetId))

        let outputDir = ProcessInfo.processInfo.environment["THI360_RICH_SAMPLES_DIR"]
        if let outputDir, !outputDir.isEmpty {
            try FileManager.default.createDirectory(
                at: URL(fileURLWithPath: outputDir, isDirectory: true),
                withIntermediateDirectories: true
            )
        }

        var capturedCount = 0
        for sample in file.samples {
            let doc = RichHTMLParser.parse(sample.html, options: RichParseOptions(allowImages: sample.allowImages))
            for mode in ThemeMode.allCases {
                let palette = BrandPaletteSet.defaultThi360Green.palette(for: mode)
                let size = CGSize(width: 390, height: 640)
                let view = ScrollView {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(sample.name)
                            .font(.caption.monospaced())
                            .foregroundStyle(palette.textMutedColor)
                        RichContentView(
                            document: doc,
                            assets: assetSource,
                            fixedWidth: 358
                        )
                    }
                    .padding(16)
                }
                .background(palette.bgSurfaceColor)
                .environment(\.brandPalette, palette)
                .environment(\.statusPalette, StatusPalette.forMode(mode))
                .environment(\.colorScheme, mode == .dark ? .dark : .light)

                let (window, _) = RecoveryViewTestHelper.host(
                    view,
                    size: size,
                    sizeClass: .compact,
                    themeMode: mode
                )
                defer { window.isHidden = true }

                let snapName = "\(sample.name)-\(mode.rawValue)"
                let png = try renderSnapshot(window: window, size: size, name: snapName)
                if let outputDir, !outputDir.isEmpty {
                    let target = URL(fileURLWithPath: outputDir, isDirectory: true)
                        .appendingPathComponent("\(snapName).png")
                    try png.write(to: target)
                }
                capturedCount += 1
            }
        }

        XCTAssertEqual(capturedCount, 86, "Must capture 43 samples x 2 themes = 86 PNG snapshots")
    }

    private func renderSnapshot(window: UIWindow, size: CGSize, name: String) throws -> Data {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 2.0
        format.opaque = true
        let image = UIGraphicsImageRenderer(size: size, format: format).image { ctx in
            let drew = window.drawHierarchy(in: CGRect(origin: .zero, size: size), afterScreenUpdates: true)
            if !drew {
                window.layer.render(in: ctx.cgContext)
            }
        }
        let png = try XCTUnwrap(image.pngData())
        XCTAssertGreaterThan(png.count, 512, "Snapshot \(name) unexpectedly empty")
        let attachment = XCTAttachment(image: image)
        attachment.name = "\(name).png"
        attachment.lifetime = .keepAlways
        add(attachment)
        return png
    }

    private func loadSampleChartPNG() -> Data {
        let bundle = Bundle(for: RichSamplesSnapshotTests.self)
        if let url = bundle.url(forResource: "sample-chart", withExtension: "png"),
           let data = try? Data(contentsOf: url)
        {
            return data
        }
        return UIGraphicsImageRenderer(size: CGSize(width: 160, height: 100)).image { ctx in
            UIColor(hex: "#047857").setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: 160, height: 100))
        }.pngData()!
    }
}
