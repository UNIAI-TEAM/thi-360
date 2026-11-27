import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers
import XCTest
@testable import Thi360

final class AvatarImagePreparerTests: XCTestCase {
    func testHEICSampleConvertsToJPEGWithinLimits() throws {
        let sampleURL = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent("Resources/sample-avatar.heic")
        let heicData = try Data(contentsOf: sampleURL)
        XCTAssertFalse(heicData.isEmpty)

        let result = AvatarImagePreparer.prepare(heicData)
        guard case let .prepared(outputData, mime) = result else {
            return XCTFail("HEIC sample must convert to JPEG, got \(result)")
        }
        XCTAssertEqual(mime, "image/jpeg", "HEIC must never be uploaded raw; must transcode to JPEG")
        XCTAssertLessThanOrEqual(outputData.count, AvatarImagePreparer.maxBytes)
        let dims = try XCTUnwrap(ProfileFixtureHelper.imagePixelDimensions(outputData))
        XCTAssertLessThanOrEqual(max(dims.width, dims.height), AvatarImagePreparer.maxEdgePixels)
        XCTAssertEqual(dims.orientation, 1)
    }

    func testExifRotatedImageIsBakedToUprightOrientation() throws {
        let rotatedJPEG = try ProfileFixtureHelper.makeExifRotatedJPEG(rawWidth: 200, rawHeight: 100, exifOrientation: 6)
        let rawDims = try XCTUnwrap(ProfileFixtureHelper.imagePixelDimensions(rotatedJPEG))
        XCTAssertEqual(rawDims.width, 200)
        XCTAssertEqual(rawDims.height, 100)
        XCTAssertEqual(rawDims.orientation, 6)

        let result = AvatarImagePreparer.prepare(rotatedJPEG)
        guard case let .prepared(outputData, mime) = result else {
            return XCTFail("Expected prepared rotated JPEG")
        }
        XCTAssertEqual(mime, "image/jpeg")
        let dims = try XCTUnwrap(ProfileFixtureHelper.imagePixelDimensions(outputData))
        XCTAssertEqual(dims.width, 100, "EXIF orientation 6 must rotate width/height upright")
        XCTAssertEqual(dims.height, 200)
        XCTAssertEqual(dims.orientation, 1)
    }

    func testOversized48MPImageScalesDownTo4096AndUnder5MB() throws {
        let width = 8_000
        let height = 6_000
        let colorSpace = CGColorSpaceCreateDeviceRGB()
        guard let ctx = CGContext(
            data: nil,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: width * 4,
            space: colorSpace,
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else {
            return XCTFail("Could not create 48MP bitmap context")
        }
        ctx.setFillColor(red: 0.15, green: 0.55, blue: 0.35, alpha: 1.0)
        ctx.fill(CGRect(x: 0, y: 0, width: width, height: height))
        let cgImage = try XCTUnwrap(ctx.makeImage())

        let rawBuffer = NSMutableData()
        let dest = try XCTUnwrap(CGImageDestinationCreateWithData(rawBuffer, UTType.jpeg.identifier as CFString, 1, nil))
        CGImageDestinationAddImage(dest, cgImage, [kCGImageDestinationLossyCompressionQuality: 0.75] as CFDictionary)
        XCTAssertTrue(CGImageDestinationFinalize(dest))

        let result = AvatarImagePreparer.prepare(rawBuffer as Data)
        guard case let .prepared(outputData, mime) = result else {
            return XCTFail("48MP image must be scaled and compressed cleanly")
        }
        XCTAssertEqual(mime, "image/jpeg")
        XCTAssertLessThanOrEqual(outputData.count, AvatarImagePreparer.maxBytes)
        let dims = try XCTUnwrap(ProfileFixtureHelper.imagePixelDimensions(outputData))
        XCTAssertEqual(max(dims.width, dims.height), AvatarImagePreparer.maxEdgePixels)
        XCTAssertEqual(dims.width, 4_096)
        XCTAssertEqual(dims.height, 3_072)
    }

    func testUnreadableCorruptDataReturnsUnreadable() {
        XCTAssertEqual(AvatarImagePreparer.prepare(Data()), .unreadable)
        XCTAssertEqual(AvatarImagePreparer.prepare(Data("not-an-image".utf8)), .unreadable)
    }
}
