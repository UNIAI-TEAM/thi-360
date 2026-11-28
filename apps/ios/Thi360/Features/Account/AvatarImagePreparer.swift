import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

enum AvatarImagePreparer {
    static let maxEdgePixels = 4_096
    static let maxBytes = 5 * 1024 * 1024

    private static let jpegQualities: [CGFloat] = [0.9, 0.82, 0.72, 0.6, 0.48, 0.36, 0.25]

    static func prepare(_ data: Data) -> AvatarPrepareResult {
        guard !data.isEmpty,
              let source = CGImageSourceCreateWithData(data as CFData, nil),
              CGImageSourceGetCount(source) > 0,
              let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
              let rawWidth = properties[kCGImagePropertyPixelWidth] as? Int,
              let rawHeight = properties[kCGImagePropertyPixelHeight] as? Int,
              rawWidth > 0,
              rawHeight > 0
        else {
            return .unreadable
        }

        let topOrientation = (properties[kCGImagePropertyOrientation] as? NSNumber)?.uint32Value
        let tiffOrientation = ((properties[kCGImagePropertyTIFFDictionary] as? [CFString: Any])?[kCGImagePropertyTIFFOrientation] as? NSNumber)?.uint32Value
        let orientation = topOrientation ?? tiffOrientation ?? 1
        let maxSourceEdge = max(rawWidth, rawHeight)
        let sourceType = (CGImageSourceGetType(source) as String?)?.lowercased()

        if orientation == 1,
           maxSourceEdge <= maxEdgePixels,
           data.count <= maxBytes,
           let passthroughMime = passthroughMimeType(for: sourceType),
           CGImageSourceCreateImageAtIndex(source, 0, nil) != nil
        {
            return .prepared(data, mime: passthroughMime)
        }

        var targetEdge = min(maxSourceEdge, maxEdgePixels)
        while true {
            guard let cgImage = createOrientedImage(from: source, maxPixelSize: targetEdge) else {
                return .unreadable
            }
            for quality in jpegQualities {
                if let encoded = encodeJPEG(cgImage, quality: quality), encoded.count <= maxBytes {
                    return .prepared(encoded, mime: "image/jpeg")
                }
            }
            guard targetEdge > 256 else {
                return .unreadable
            }
            targetEdge = max(256, Int(Double(targetEdge) * 0.75))
        }
    }

    private static func passthroughMimeType(for uti: String?) -> String? {
        switch uti {
        case UTType.jpeg.identifier.lowercased():
            "image/jpeg"
        case UTType.png.identifier.lowercased():
            "image/png"
        case UTType.webP.identifier.lowercased(), "org.webmproject.webp":
            "image/webp"
        default:
            nil
        }
    }

    private static func createOrientedImage(from source: CGImageSource, maxPixelSize: Int) -> CGImage? {
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceShouldCacheImmediately: true,
            kCGImageSourceThumbnailMaxPixelSize: maxPixelSize
        ]
        return CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary)
    }

    private static func encodeJPEG(_ image: CGImage, quality: CGFloat) -> Data? {
        let buffer = NSMutableData()
        guard let destination = CGImageDestinationCreateWithData(
            buffer as CFMutableData,
            UTType.jpeg.identifier as CFString,
            1,
            nil
        ) else {
            return nil
        }
        let options: [CFString: Any] = [
            kCGImageDestinationLossyCompressionQuality: quality
        ]
        CGImageDestinationAddImage(destination, image, options as CFDictionary)
        guard CGImageDestinationFinalize(destination) else {
            return nil
        }
        return buffer as Data
    }
}
