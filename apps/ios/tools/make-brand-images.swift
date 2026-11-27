#!/usr/bin/env swift
import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

let fileManager = FileManager.default
let scriptURL = URL(fileURLWithPath: #filePath)
let repoRoot = scriptURL
    .deletingLastPathComponent()
    .deletingLastPathComponent()
    .deletingLastPathComponent()
    .deletingLastPathComponent()

let sourceURL = repoRoot.appendingPathComponent("docs/design/app-hoc-vien/brand/thi360-icon.png")
let assetsRoot = repoRoot.appendingPathComponent("apps/ios/Thi360/Resources/Assets.xcassets", isDirectory: true)
let appIconDir = assetsRoot.appendingPathComponent("AppIcon.appiconset", isDirectory: true)
let brandMarkDir = assetsRoot.appendingPathComponent("BrandMark.imageset", isDirectory: true)

func fail(_ message: String) -> Never {
    fputs("make-brand-images: \(message)\n", stderr)
    exit(1)
}

guard let imageSource = CGImageSourceCreateWithURL(sourceURL as CFURL, nil),
      let sourceImage = CGImageSourceCreateImageAtIndex(imageSource, 0, nil)
else {
    fail("Cannot load source image at \(sourceURL.path)")
}

guard sourceImage.width == 700, sourceImage.height == 700 else {
    fail("Expected 700x700 source image, got \(sourceImage.width)x\(sourceImage.height)")
}

let srgbSpace = CGColorSpace(name: CGColorSpace.sRGB)!

func writeOpaquePNG(_ image: CGImage, to url: URL) throws {
    let width = image.width
    let height = image.height
    let bytesPerPixel = 4
    let bytesPerRow = width * bytesPerPixel
    var raw = [UInt8](repeating: 255, count: height * bytesPerRow)

    guard let ctx = CGContext(
        data: &raw,
        width: width,
        height: height,
        bitsPerComponent: 8,
        bytesPerRow: bytesPerRow,
        space: srgbSpace,
        bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue
    ) else {
        fail("Failed creating opaque CGContext")
    }

    ctx.setFillColor(CGColor(colorSpace: srgbSpace, components: [1, 1, 1, 1])!)
    ctx.fill(CGRect(x: 0, y: 0, width: width, height: height))
    ctx.interpolationQuality = .high
    ctx.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))

    var rgbBytes = [UInt8](repeating: 255, count: width * height * 3)
    for i in 0..<(width * height) {
        rgbBytes[i * 3] = raw[i * 4]
        rgbBytes[i * 3 + 1] = raw[i * 4 + 1]
        rgbBytes[i * 3 + 2] = raw[i * 4 + 2]
    }

    guard let provider = CGDataProvider(data: Data(rgbBytes) as CFData),
          let rgbImage = CGImage(
              width: width,
              height: height,
              bitsPerComponent: 8,
              bitsPerPixel: 24,
              bytesPerRow: width * 3,
              space: srgbSpace,
              bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.none.rawValue),
              provider: provider,
              decode: nil,
              shouldInterpolate: true,
              intent: .defaultIntent
          )
    else {
        fail("Failed creating 24-bit RGB CGImage")
    }

    guard let destination = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil) else {
        fail("Failed creating PNG destination at \(url.path)")
    }
    CGImageDestinationAddImage(destination, rgbImage, nil)
    guard CGImageDestinationFinalize(destination) else {
        fail("Failed writing PNG at \(url.path)")
    }
}

func renderIconOnWhite(canvasSize: Int, markSize: Int) -> CGImage {
    guard let ctx = CGContext(
        data: nil,
        width: canvasSize,
        height: canvasSize,
        bitsPerComponent: 8,
        bytesPerRow: 0,
        space: srgbSpace,
        bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
    ) else {
        fail("Failed creating icon context")
    }
    ctx.setFillColor(CGColor(colorSpace: srgbSpace, components: [1, 1, 1, 1])!)
    ctx.fill(CGRect(x: 0, y: 0, width: canvasSize, height: canvasSize))
    ctx.interpolationQuality = .high
    let inset = CGFloat(canvasSize - markSize) / 2.0
    ctx.draw(sourceImage, in: CGRect(x: inset, y: inset, width: CGFloat(markSize), height: CGFloat(markSize)))
    guard let rendered = ctx.makeImage() else {
        fail("Failed extracting rendered icon")
    }
    return rendered
}

func writeTransparentPNG(size: Int, to url: URL) throws {
    guard size <= sourceImage.width else {
        fail("Refusing to upscale BrandMark to \(size)px from \(sourceImage.width)px")
    }
    guard let ctx = CGContext(
        data: nil,
        width: size,
        height: size,
        bitsPerComponent: 8,
        bytesPerRow: 0,
        space: srgbSpace,
        bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
    ) else {
        fail("Failed creating BrandMark context")
    }
    ctx.clear(CGRect(x: 0, y: 0, width: size, height: size))
    ctx.interpolationQuality = .high
    ctx.draw(sourceImage, in: CGRect(x: 0, y: 0, width: size, height: size))
    guard let rendered = ctx.makeImage() else {
        fail("Failed extracting BrandMark image")
    }
    guard let destination = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil) else {
        fail("Failed creating PNG destination at \(url.path)")
    }
    CGImageDestinationAddImage(destination, rendered, nil)
    guard CGImageDestinationFinalize(destination) else {
        fail("Failed writing BrandMark PNG at \(url.path)")
    }
}

do {
    try fileManager.createDirectory(at: appIconDir, withIntermediateDirectories: true)
    try fileManager.createDirectory(at: brandMarkDir, withIntermediateDirectories: true)

    let appIconImage = renderIconOnWhite(canvasSize: 1024, markSize: 700)
    try writeOpaquePNG(appIconImage, to: appIconDir.appendingPathComponent("AppIcon.png"))

    let appIconContents = """
    {
      "images" : [
        {
          "filename" : "AppIcon.png",
          "idiom" : "universal",
          "platform" : "ios",
          "size" : "1024x1024"
        }
      ],
      "info" : {
        "author" : "xcode",
        "version" : 1
      }
    }
    """
    try appIconContents.write(to: appIconDir.appendingPathComponent("Contents.json"), atomically: true, encoding: .utf8)

    try writeTransparentPNG(size: 96, to: brandMarkDir.appendingPathComponent("BrandMark@1x.png"))
    try writeTransparentPNG(size: 192, to: brandMarkDir.appendingPathComponent("BrandMark@2x.png"))
    try writeTransparentPNG(size: 288, to: brandMarkDir.appendingPathComponent("BrandMark@3x.png"))

    let brandMarkContents = """
    {
      "images" : [
        {
          "filename" : "BrandMark@1x.png",
          "idiom" : "universal",
          "scale" : "1x"
        },
        {
          "filename" : "BrandMark@2x.png",
          "idiom" : "universal",
          "scale" : "2x"
        },
        {
          "filename" : "BrandMark@3x.png",
          "idiom" : "universal",
          "scale" : "3x"
        }
      ],
      "info" : {
        "author" : "xcode",
        "version" : 1
      },
      "properties" : {
        "template-rendering-intent" : "original"
      }
    }
    """
    try brandMarkContents.write(to: brandMarkDir.appendingPathComponent("Contents.json"), atomically: true, encoding: .utf8)

    let args = CommandLine.arguments
    if let previewFlagIndex = args.firstIndex(of: "--preview-dir"), previewFlagIndex + 1 < args.count {
        let previewDir = URL(fileURLWithPath: args[previewFlagIndex + 1], isDirectory: true)
        try fileManager.createDirectory(at: previewDir, withIntermediateDirectories: true)
        for previewSize in [1024, 180, 120] {
            let scaledMark = Int((Double(previewSize) * 700.0 / 1024.0).rounded())
            let preview = renderIconOnWhite(canvasSize: previewSize, markSize: scaledMark)
            try writeOpaquePNG(preview, to: previewDir.appendingPathComponent("app-icon-\(previewSize).png"))
        }
    }

    print("Generated AppIcon (1024x1024, mark 700x700) and BrandMark (96/192/288 px).")
} catch {
    fail(error.localizedDescription)
}
