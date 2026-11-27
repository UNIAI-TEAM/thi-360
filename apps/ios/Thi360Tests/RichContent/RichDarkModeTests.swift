import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class RichDarkModeTests: XCTestCase {
    private struct SampleFixtureFile: Decodable {
        let assetId: String
        let samples: [Sample]
        struct Sample: Decodable { let name: String; let html: String }
    }

    override func tearDown() {
        RecordingAssetURLProtocol.reset()
        super.tearDown()
    }

    func testTokensAndContrast() async throws {
        let darkTokens = RichContentTokens(
            brand: .defaultDark,
            status: .dark,
            colorScheme: .dark
        )
        let lightTokens = RichContentTokens(
            brand: .defaultLight,
            status: .light,
            colorScheme: .light
        )

        XCTAssertEqual(lightTokens.tableHeadHex.lowercased(), "#fafafb")
        XCTAssertEqual(darkTokens.tableHeadHex.lowercased(), "#1a2126")
        XCTAssertEqual(darkTokens.brand.text.lowercased(), "#e6eaed")
        XCTAssertEqual(darkTokens.brand.borderStrong.lowercased(), "#3a444c")
        XCTAssertEqual(darkTokens.status.info.lowercased(), "#22d3ee")
        XCTAssertEqual(darkTokens.status.infoBg.lowercased(), "#0f2a30")
        XCTAssertEqual(darkTokens.status.warning.lowercased(), "#fbbf24")
        XCTAssertEqual(darkTokens.status.warningBg.lowercased(), "#2b2210")

        XCTAssertGreaterThanOrEqual(
            RichContentTokens.contrastRatio(foregroundHex: darkTokens.brand.text, backgroundHex: darkTokens.brand.bgSurface),
            4.5
        )
        XCTAssertGreaterThanOrEqual(
            RichContentTokens.contrastRatio(foregroundHex: darkTokens.brand.text, backgroundHex: darkTokens.tableHeadHex),
            4.5
        )
        XCTAssertGreaterThanOrEqual(
            RichContentTokens.contrastRatio(foregroundHex: darkTokens.brand.textMuted, backgroundHex: darkTokens.brand.bgSurface),
            4.5
        )
        XCTAssertGreaterThanOrEqual(
            RichContentTokens.contrastRatio(foregroundHex: darkTokens.status.info, backgroundHex: darkTokens.status.infoBg),
            4.5
        )
        XCTAssertGreaterThanOrEqual(
            RichContentTokens.contrastRatio(foregroundHex: darkTokens.status.warning, backgroundHex: darkTokens.status.warningBg),
            4.5
        )

        let data = try Data(contentsOf: FormulaCorpusLoader.richTextSamplesURL)
        let file = try JSONDecoder().decode(SampleFixtureFile.self, from: data)
        let targetSamples = ["math-inline-and-block", "data-table", "set-passage-blanks"]

        for name in targetSamples {
            let html = try XCTUnwrap(file.samples.first { $0.name == name }?.html)
            let doc = RichHTMLParser.parse(html)

            var pngs: [ThemeMode: Data] = [:]
            for mode in ThemeMode.allCases {
                let palette = BrandPaletteSet.defaultThi360Green.palette(for: mode)
                let view = ScrollView {
                    RichContentView(document: doc, fixedWidth: 358)
                        .padding(16)
                }
                .background(palette.bgSurfaceColor)
                .environment(\.brandPalette, palette)
                .environment(\.statusPalette, StatusPalette.forMode(mode))
                .environment(\.colorScheme, mode == .dark ? .dark : .light)

                let (window, controller) = RecoveryViewTestHelper.host(
                    view,
                    size: CGSize(width: 390, height: 500),
                    sizeClass: .compact,
                    themeMode: mode
                )
                defer { window.isHidden = true }

                if mode == .dark {
                    let textViews = RecoveryViewTestHelper.findViews(ofType: RichTextKitTextView.self, in: controller.view)
                    XCTAssertFalse(textViews.isEmpty)
                    if let fg = textViews[0].attributedText.attribute(.foregroundColor, at: 0, effectiveRange: nil) as? UIColor {
                        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
                        fg.getRed(&r, green: &g, blue: &b, alpha: &a)
                        XCTAssertGreaterThan(r, 0.7, "Dark mode text must use light foreground token #E6EAED")
                    }
                }

                pngs[mode] = try RecoveryViewTestHelper.captureSnapshot(
                    window: window,
                    size: CGSize(width: 390, height: 500),
                    name: "richcontent-darkcheck-\(name)-\(mode.rawValue)",
                    in: self
                )
            }
            XCTAssertNotEqual(pngs[.light], pngs[.dark], "Light and dark renders must differ for \(name)")
        }

        let solidRed = UIGraphicsImageRenderer(size: CGSize(width: 80, height: 80)).image { ctx in
            UIColor(red: 0.9, green: 0.15, blue: 0.15, alpha: 1.0).setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: 80, height: 80))
        }.pngData()!

        RecordingAssetURLProtocol.reset()
        RecordingAssetURLProtocol.handler = { req in
            let resp = HTTPURLResponse(
                url: req.url!,
                statusCode: 200,
                httpVersion: "HTTP/1.1",
                headerFields: ["Content-Type": "image/png"]
            )!
            return (resp, solidRed)
        }
        let tokenStore = InMemoryTokenStore(
            initialTokens: SessionTokens(
                accessToken: "dark-token", refreshToken: "r", accessExpiresIn: 3600, refreshExpiresIn: 86_400, mustChangePassword: false
            )
        )
        let refresher = TokenRefresher(tokenStore: tokenStore, refreshExecutor: { _ in throw MobileApiError.unexpectedResponse(statusCode: 401) })
        let source = AttemptAssetSource(
            attemptId: "44444444-4444-4444-8444-444444444444",
            serverURL: URL(string: "https://api.thi360.test")!,
            tokenStore: tokenStore,
            refresher: refresher,
            session: AttemptAssetSource.makeNoDiskCacheSession(protocolClasses: [RecordingAssetURLProtocol.self])
        )
        defer { source.purgeAll() }
        _ = try await source.load(.attempt(assetId: file.assetId))

        let imgHTML = #"<figure class="image" data-width="medium" data-zoom="false"><img src="" data-asset-id="\#(file.assetId)" alt="red-box"></figure>"#
        let imgDoc = RichHTMLParser.parse(imgHTML)
        let darkImgView = RichContentView(document: imgDoc, assets: source, fixedWidth: 358)
            .padding(16)
            .background(BrandPalette.defaultDark.bgSurfaceColor)
            .environment(\.brandPalette, .defaultDark)
            .environment(\.statusPalette, .dark)
            .environment(\.colorScheme, .dark)

        let (imgWin, imgCtrl) = RecoveryViewTestHelper.host(
            darkImgView,
            size: CGSize(width: 390, height: 320),
            sizeClass: .compact,
            themeMode: .dark
        )
        defer { imgWin.isHidden = true }
        try await Task.sleep(nanoseconds: 150_000_000)

        let renderedImgView = try XCTUnwrap(
            RecoveryViewTestHelper.findViews(ofType: UIImageView.self, in: imgCtrl.view).first { $0.bounds.width > 40 }
        )
        let renderedCG = try XCTUnwrap(renderedImgView.image?.cgImage)
        let centerRGB = sampleCenterPixelRGBA(renderedCG)
        XCTAssertGreaterThan(centerRGB.r, 0.8, "Dark mode must preserve original red channel of images")
        XCTAssertLessThan(centerRGB.g, 0.3, "Dark mode must not invert image colors")
        XCTAssertLessThan(centerRGB.b, 0.3, "Dark mode must not invert image colors")
    }

    private func sampleCenterPixelRGBA(_ cgImage: CGImage) -> (r: CGFloat, g: CGFloat, b: CGFloat, a: CGFloat) {
        var pixel = [UInt8](repeating: 0, count: 4)
        let colorSpace = CGColorSpaceCreateDeviceRGB()
        guard let ctx = CGContext(
            data: &pixel,
            width: 1,
            height: 1,
            bitsPerComponent: 8,
            bytesPerRow: 4,
            space: colorSpace,
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else {
            return (0, 0, 0, 0)
        }
        ctx.draw(cgImage, in: CGRect(x: -cgImage.width / 2, y: -cgImage.height / 2, width: cgImage.width, height: cgImage.height))
        return (CGFloat(pixel[0]) / 255, CGFloat(pixel[1]) / 255, CGFloat(pixel[2]) / 255, CGFloat(pixel[3]) / 255)
    }
}
