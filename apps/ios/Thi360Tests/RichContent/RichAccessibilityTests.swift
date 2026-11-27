import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class RichAccessibilityTests: XCTestCase {
    func testFormulaLabelIsSourceAndImageLabelIsAlt() async throws {
        let assetId = "9b8f8b1f-9b8f-4b8f-9b8f-9b8f8b8f8b8f"
        let samplePNG = UIGraphicsImageRenderer(size: CGSize(width: 60, height: 40)).image { ctx in
            UIColor.systemGreen.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: 60, height: 40))
        }.pngData()!

        RecordingAssetURLProtocol.reset()
        defer { RecordingAssetURLProtocol.reset() }
        RecordingAssetURLProtocol.handler = { req in
            let resp = HTTPURLResponse(
                url: req.url!,
                statusCode: 200,
                httpVersion: "HTTP/1.1",
                headerFields: ["Content-Type": "image/png"]
            )!
            return (resp, samplePNG)
        }
        let tokenStore = InMemoryTokenStore(
            initialTokens: SessionTokens(
                accessToken: "a11y-token",
                refreshToken: "r",
                accessExpiresIn: 3600,
                refreshExpiresIn: 86_400,
                mustChangePassword: false
            )
        )
        let refresher = TokenRefresher(
            tokenStore: tokenStore,
            refreshExecutor: { _ in throw MobileApiError.unexpectedResponse(statusCode: 401) }
        )
        let source = AttemptAssetSource(
            attemptId: "88888888-8888-4888-8888-888888888888",
            serverURL: URL(string: "https://api.thi360.test")!,
            tokenStore: tokenStore,
            refresher: refresher,
            session: AttemptAssetSource.makeNoDiskCacheSession(protocolClasses: [RecordingAssetURLProtocol.self])
        )
        defer { source.purgeAll() }
        _ = try await source.load(.attempt(assetId: assetId))

        let html = """
        <p>Inline <span class="math-inline">\\(\\frac{a}{b}\\)</span> and broken <span class="math-inline">\\(\\broken{syntax}\\)</span></p>
        <span class="math-block">\\[x^2 + y^2 = z^2\\]</span>
        <span class="math-block">\\[\\frac{1}{\\]</span>
        <figure class="image" data-width="medium" data-zoom="false">
          <img src="" data-asset-id="\(assetId)" alt="Biểu đồ dân số" />
        </figure>
        """
        let doc = RichHTMLParser.parse(html)
        let view = RichContentView(document: doc, assets: source, fixedWidth: 358)
            .padding(16)
            .environment(\.brandPalette, .defaultLight)
            .environment(\.statusPalette, .light)

        let (window, controller) = RecoveryViewTestHelper.host(
            view,
            size: CGSize(width: 390, height: 600),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { window.isHidden = true }

        let textViews = RecoveryViewTestHelper.findViews(ofType: RichTextKitTextView.self, in: controller.view)
        let paraTV = try XCTUnwrap(textViews.first)
        XCTAssertTrue(paraTV.customAccessibilityDescription.contains(#"\(\frac{a}{b}\)"#))
        XCTAssertTrue(paraTV.customAccessibilityDescription.contains(#"\(\broken{syntax}\)"#))

        var attachmentA11y: [String] = []
        paraTV.attributedText.enumerateAttribute(
            .attachment,
            in: NSRange(location: 0, length: paraTV.attributedText.length)
        ) { value, _, _ in
            if let att = value as? NSTextAttachment, let label = att.accessibilityLabel {
                attachmentA11y.append(label)
            }
        }
        XCTAssertEqual(attachmentA11y, [#"\(\frac{a}{b}\)"#, #"\(\broken{syntax}\)"#])

        let blockScrolls = RecoveryViewTestHelper.findViews(ofType: HorizontalFormulaScrollView.self, in: controller.view)
        let blockLabels = blockScrolls.compactMap(\.accessibilityLabel)
        XCTAssertTrue(blockLabels.contains(#"\[x^2 + y^2 = z^2\]"#), "Block formula VoiceOver label must be delimited LaTeX source")
        XCTAssertTrue(blockLabels.contains(#"\[\frac{1}{\]"#), "Broken block formula VoiceOver label must be delimited LaTeX source")

        let imageViews = RecoveryViewTestHelper.findViews(ofType: UIImageView.self, in: controller.view)
        XCTAssertTrue(
            imageViews.contains { $0.isAccessibilityElement && $0.accessibilityLabel == "Biểu đồ dân số" },
            "Image must expose alt text for VoiceOver"
        )
    }

    private func collectAccessibilityLabels(in view: UIView) -> [String] {
        var labels: [String] = []
        if let label = view.accessibilityLabel, !label.isEmpty {
            labels.append(label)
        }
        for sub in view.subviews {
            labels.append(contentsOf: collectAccessibilityLabels(in: sub))
        }
        return labels
    }
}
