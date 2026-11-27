import CoreGraphics
import Foundation
import ImageIO
import XCTest

final class AppIconTests: XCTestCase {
    private static let expectedIconDimension = 1024
    private static let expectedMarkBoxSize = 700

    func testAppIconIs1024OpaqueWhiteCornersAndMatchesSourceMarkWithinThreshold() throws {
        let repoRoot = ContractFixtureLoader.repoRootURL
        let appIconDir = repoRoot
            .appendingPathComponent("apps/ios/Thi360/Resources/Assets.xcassets/AppIcon.appiconset", isDirectory: true)
        let contentsURL = appIconDir.appendingPathComponent("Contents.json")
        let iconURL = appIconDir.appendingPathComponent("AppIcon.png")
        let sourceMarkURL = repoRoot
            .appendingPathComponent("docs/design/app-hoc-vien/brand/thi360-icon.png")

        let contentsData = try Data(contentsOf: contentsURL)
        let contentsJSON = try XCTUnwrap(JSONSerialization.jsonObject(with: contentsData) as? [String: Any])
        let images = try XCTUnwrap(contentsJSON["images"] as? [[String: Any]])
        XCTAssertEqual(images.count, 1)
        XCTAssertEqual(images[0]["filename"] as? String, "AppIcon.png")
        XCTAssertEqual(images[0]["idiom"] as? String, "universal")
        XCTAssertEqual(images[0]["platform"] as? String, "ios")
        XCTAssertEqual(images[0]["size"] as? String, "1024x1024")

        let iconSource = try XCTUnwrap(CGImageSourceCreateWithURL(iconURL as CFURL, nil))
        let iconProps = try XCTUnwrap(CGImageSourceCopyPropertiesAtIndex(iconSource, 0, nil) as? [CFString: Any])
        XCTAssertEqual(iconProps[kCGImagePropertyPixelWidth] as? Int, Self.expectedIconDimension)
        XCTAssertEqual(iconProps[kCGImagePropertyPixelHeight] as? Int, Self.expectedIconDimension)
        let hasAlpha = (iconProps[kCGImagePropertyHasAlpha] as? Bool) ?? false
        XCTAssertFalse(hasAlpha, "AppIcon.png must NOT contain an alpha channel")

        let iconImage = try XCTUnwrap(CGImageSourceCreateImageAtIndex(iconSource, 0, nil))
        XCTAssertEqual(iconImage.width, Self.expectedIconDimension)
        XCTAssertEqual(iconImage.height, Self.expectedIconDimension)

        let iconBytes = try Self.rgbaPixels(of: iconImage, width: 1024, height: 1024)

        let corners = [(0, 0), (1023, 0), (0, 1023), (1023, 1023)]
        for (x, y) in corners {
            let idx = (y * 1024 + x) * 4
            XCTAssertEqual(iconBytes[idx], 255, "Corner (\(x),\(y)) R must be 255")
            XCTAssertEqual(iconBytes[idx + 1], 255, "Corner (\(x),\(y)) G must be 255")
            XCTAssertEqual(iconBytes[idx + 2], 255, "Corner (\(x),\(y)) B must be 255")
        }

        XCTAssertLessThanOrEqual(Self.expectedMarkBoxSize, 700, "Source mark must never be upscaled beyond 700px")
        let sourceImageSource = try XCTUnwrap(CGImageSourceCreateWithURL(sourceMarkURL as CFURL, nil))
        let sourceMarkImage = try XCTUnwrap(CGImageSourceCreateImageAtIndex(sourceImageSource, 0, nil))
        let expectedMarkBytes = try Self.compositeMarkOnWhite(
            sourceMark: sourceMarkImage,
            boxSize: Self.expectedMarkBoxSize
        )

        let origin = (Self.expectedIconDimension - Self.expectedMarkBoxSize) / 2
        var totalChannelDiff: Double = 0
        var totalChannels: Double = 0
        var pixelsOver48 = 0
        var bluePixels = 0
        var orangePixels = 0
        let totalPixels = Self.expectedMarkBoxSize * Self.expectedMarkBoxSize

        for y in 0..<Self.expectedMarkBoxSize {
            for x in 0..<Self.expectedMarkBoxSize {
                let iconIdx = ((origin + y) * Self.expectedIconDimension + (origin + x)) * 4
                let expIdx = (y * Self.expectedMarkBoxSize + x) * 4

                let dr = abs(Int(iconBytes[iconIdx]) - Int(expectedMarkBytes[expIdx]))
                let dg = abs(Int(iconBytes[iconIdx + 1]) - Int(expectedMarkBytes[expIdx + 1]))
                let db = abs(Int(iconBytes[iconIdx + 2]) - Int(expectedMarkBytes[expIdx + 2]))

                totalChannelDiff += Double(dr + dg + db)
                totalChannels += 3
                if dr > 48 || dg > 48 || db > 48 {
                    pixelsOver48 += 1
                }

                let r = Int(iconBytes[iconIdx])
                let g = Int(iconBytes[iconIdx + 1])
                let b = Int(iconBytes[iconIdx + 2])
                if b > r + 20 && b > 80 {
                    bluePixels += 1
                }
                if r > b + 40 && r > 150 && g > 60 {
                    orangePixels += 1
                }
            }
        }

        let meanChannelDiff = totalChannelDiff / totalChannels
        let outlierFraction = Double(pixelsOver48) / Double(totalPixels)

        XCTAssertLessThanOrEqual(meanChannelDiff, 8.0, "Mean absolute channel diff \(meanChannelDiff)/255 exceeds 8/255")
        XCTAssertLessThanOrEqual(outlierFraction, 0.02, "Outlier pixel fraction \(outlierFraction) exceeds 2%")
        XCTAssertGreaterThan(bluePixels, 1000, "Center mark box must contain blue brand pixels")
        XCTAssertGreaterThan(orangePixels, 1000, "Center mark box must contain orange brand pixels")
    }

    private static func rgbaPixels(of image: CGImage, width: Int, height: Int) throws -> [UInt8] {
        var data = [UInt8](repeating: 0, count: width * height * 4)
        let colorSpace = CGColorSpace(name: CGColorSpace.sRGB)!
        let context = try XCTUnwrap(
            CGContext(
                data: &data,
                width: width,
                height: height,
                bitsPerComponent: 8,
                bytesPerRow: width * 4,
                space: colorSpace,
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
            )
        )
        context.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
        return data
    }

    private static func compositeMarkOnWhite(sourceMark: CGImage, boxSize: Int) throws -> [UInt8] {
        var data = [UInt8](repeating: 255, count: boxSize * boxSize * 4)
        let colorSpace = CGColorSpace(name: CGColorSpace.sRGB)!
        let context = try XCTUnwrap(
            CGContext(
                data: &data,
                width: boxSize,
                height: boxSize,
                bitsPerComponent: 8,
                bytesPerRow: boxSize * 4,
                space: colorSpace,
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
            )
        )
        context.setFillColor(CGColor(srgbRed: 1, green: 1, blue: 1, alpha: 1))
        context.fill(CGRect(x: 0, y: 0, width: boxSize, height: boxSize))
        context.interpolationQuality = .high
        context.draw(sourceMark, in: CGRect(x: 0, y: 0, width: boxSize, height: boxSize))
        return data
    }
}
