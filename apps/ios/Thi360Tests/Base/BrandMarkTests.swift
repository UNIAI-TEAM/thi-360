import CoreGraphics
import Foundation
import ImageIO
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class BrandMarkTests: XCTestCase {
    func testNameContrast() throws {
        let lightPairs: [(name: String, fg: String, bg: String)] = [
            ("light text on bgPage", BrandPalette.defaultLight.text, BrandPalette.defaultLight.bgPage),
            ("light text on bgSurface", BrandPalette.defaultLight.text, BrandPalette.defaultLight.bgSurface)
        ]
        let darkPairs: [(name: String, fg: String, bg: String)] = [
            ("dark text on bgPage", BrandPalette.defaultDark.text, BrandPalette.defaultDark.bgPage),
            ("dark text on bgSurface", BrandPalette.defaultDark.text, BrandPalette.defaultDark.bgSurface)
        ]

        for pair in lightPairs + darkPairs {
            let ratio = try Self.contrastRatio(fgHex: pair.fg, bgHex: pair.bg)
            XCTAssertGreaterThanOrEqual(ratio, 4.5, "\(pair.name) contrast \(ratio) < 4.5")
        }
    }

    func testNeverUpscaled() throws {
        XCTAssertEqual(BrandMark.maxMarkSize, 96)
        XCTAssertEqual(BrandMark(markSize: 28, locale: .vi).clampedMarkSize, 28)
        XCTAssertEqual(BrandMark(markSize: 96, locale: .vi).clampedMarkSize, 96)
        XCTAssertEqual(BrandMark(markSize: 140, locale: .vi).clampedMarkSize, 96)

        let imagesetURL = ContractFixtureLoader.repoRootURL
            .appendingPathComponent("apps/ios/Thi360/Resources/Assets.xcassets/BrandMark.imageset", isDirectory: true)
        let contentsData = try Data(contentsOf: imagesetURL.appendingPathComponent("Contents.json"))
        let contentsJSON = try XCTUnwrap(JSONSerialization.jsonObject(with: contentsData) as? [String: Any])
        let properties = try XCTUnwrap(contentsJSON["properties"] as? [String: Any])
        XCTAssertEqual(properties["template-rendering-intent"] as? String, "original")

        let expectedFiles: [(String, Int)] = [
            ("BrandMark@1x.png", 96),
            ("BrandMark@2x.png", 192),
            ("BrandMark@3x.png", 288)
        ]
        for (filename, dim) in expectedFiles {
            let url = imagesetURL.appendingPathComponent(filename)
            let source = try XCTUnwrap(CGImageSourceCreateWithURL(url as CFURL, nil), "Missing \(filename)")
            let props = try XCTUnwrap(CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any])
            XCTAssertEqual(props[kCGImagePropertyPixelWidth] as? Int, dim, "\(filename) width")
            XCTAssertEqual(props[kCGImagePropertyPixelHeight] as? Int, dim, "\(filename) height")
        }

        let appRoot = ContractFixtureLoader.repoRootURL.appendingPathComponent("apps/ios/Thi360", isDirectory: true)
        let regex = try NSRegularExpression(pattern: #"BrandMark\([^)]*markSize:\s*([0-9]+(?:\.[0-9]+)?)"#)
        let enumerator = try XCTUnwrap(FileManager.default.enumerator(at: appRoot, includingPropertiesForKeys: nil))
        for case let fileURL as URL in enumerator where fileURL.pathExtension == "swift" {
            let content = try String(contentsOf: fileURL, encoding: .utf8)
            let range = NSRange(content.startIndex..., in: content)
            for match in regex.matches(in: content, range: range) {
                if let sizeRange = Range(match.range(at: 1), in: content),
                   let value = Double(content[sizeRange])
                {
                    XCTAssertLessThanOrEqual(value, 96, "\(fileURL.lastPathComponent) passes markSize \(value) > 96")
                }
            }
        }
    }

    func testMarkIgnoresBrandPalette() throws {
        let navyPalette = BrandPalette(
            primaryFill: "#1d4ed8",
            onPrimary: "#ffffff",
            primaryText: "#1d4ed8",
            primaryContainer: "#eff6ff",
            primaryContainerStrong: "#dbeafe",
            accent: "#ea580c",
            onAccent: "#ffffff",
            bgPage: "#f7f8f9",
            bgSurface: "#ffffff",
            bgSubtle: "#f1f2f4",
            text: "#1e3a8a",
            textSecondary: "#3a3a42",
            textMuted: "#6b6b72",
            border: "#e8e8ec",
            borderStrong: "#d4d4d8",
            dangerText: "#b91c1c",
            dangerBg: "#fef2f2"
        )

        let greenImage = try renderBrandMark(palette: .defaultLight)
        let navyImage = try renderBrandMark(palette: navyPalette)

        // With 240x180pt @ 2x (480x360px) centered BrandMark(48pt), icon sits at x=[20..116], text sits to the right.
        let iconCropRect = CGRect(x: 20, y: 132, width: 96, height: 96)
        let greenIcon = try XCTUnwrap(greenImage.cropping(to: iconCropRect))
        let navyIcon = try XCTUnwrap(navyImage.cropping(to: iconCropRect))

        let greenIconPixels = try Self.rgbaBytes(of: greenIcon)
        let navyIconPixels = try Self.rgbaBytes(of: navyIcon)
        XCTAssertTrue(
            greenIconPixels.contains { $0 < 240 },
            "Icon crop must contain actual rendered BrandMark pixels, not blank white"
        )
        XCTAssertEqual(greenIconPixels, navyIconPixels, "BrandMark icon pixels must not change when BrandPalette changes")

        let textCropRect = CGRect(x: 132, y: 132, width: 320, height: 96)
        let greenText = try XCTUnwrap(greenImage.cropping(to: textCropRect))
        let navyText = try XCTUnwrap(navyImage.cropping(to: textCropRect))
        XCTAssertNotEqual(
            try Self.rgbaBytes(of: greenText),
            try Self.rgbaBytes(of: navyText),
            "BrandMark name text must follow BrandPalette.textColor"
        )
    }

    private func renderBrandMark(palette: BrandPalette) throws -> CGImage {
        let size = CGSize(width: 240, height: 180)
        let view = ZStack(alignment: .leading) {
            Color.white.ignoresSafeArea()
            BrandMark(markSize: 48, locale: .vi)
                .padding(.leading, 10)
        }
        .frame(width: size.width, height: size.height)
        .ignoresSafeArea()
        .environment(\.brandPalette, palette)

        let (window, _) = RecoveryViewTestHelper.host(view, size: size, sizeClass: .compact, themeMode: .light)
        defer { window.isHidden = true }

        let format = UIGraphicsImageRendererFormat()
        format.scale = 2.0
        format.opaque = true
        let renderer = UIGraphicsImageRenderer(size: size, format: format)
        let rendered = renderer.image { ctx in
            let drew = window.drawHierarchy(in: CGRect(origin: .zero, size: size), afterScreenUpdates: true)
            if !drew {
                window.layer.render(in: ctx.cgContext)
            }
        }
        return try XCTUnwrap(rendered.cgImage)
    }

    private static func rgbaBytes(of image: CGImage) throws -> [UInt8] {
        var bytes = [UInt8](repeating: 0, count: image.width * image.height * 4)
        let cs = CGColorSpace(name: CGColorSpace.sRGB)!
        let ctx = try XCTUnwrap(
            CGContext(
                data: &bytes,
                width: image.width,
                height: image.height,
                bitsPerComponent: 8,
                bytesPerRow: image.width * 4,
                space: cs,
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
            )
        )
        ctx.draw(image, in: CGRect(x: 0, y: 0, width: image.width, height: image.height))
        return bytes
    }

    private static func contrastRatio(fgHex: String, bgHex: String) throws -> Double {
        let l1 = try luminance(fgHex)
        let l2 = try luminance(bgHex)
        return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
    }

    private static func luminance(_ hex: String) throws -> Double {
        let clean = hex.replacingOccurrences(of: "#", with: "")
        guard let v = UInt64(clean, radix: 16) else { throw NSError(domain: "BrandMarkTests", code: 1) }
        func lin(_ c: Double) -> Double { c <= 0.03928 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4) }
        return 0.2126 * lin(Double((v >> 16) & 0xFF) / 255)
            + 0.7152 * lin(Double((v >> 8) & 0xFF) / 255)
            + 0.0722 * lin(Double(v & 0xFF) / 255)
    }
}
