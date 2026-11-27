import Foundation
import UIKit
import XCTest
@testable import Thi360

final class FormulaSheetTests: XCTestCase {
    private static let engine = SwaTexFormulaEngine(timeoutSeconds: 1.5, displayScale: 2.0, useCache: false)

    func testGeneratesAll226SideBySideFormulaComparisonSheets() throws {
        let corpus = try FormulaCorpusLoader.loadCorpus()
        let sheetedEntries = corpus.entries.filter { $0.sheetKey != nil }

        for entry in sheetedEntries {
            XCTAssertEqual(entry.display, .block, "Only block entries should carry a sheetKey: \(entry.sheetKey ?? "")")
            XCTAssertTrue(entry.katexRenders, "Rejected entries must not carry a sheetKey: \(entry.sheetKey ?? "")")
            XCTAssertNil(entry.rejectedArea, "Rejected entries must not appear in formula sheets")
        }

        let grouped = Dictionary(grouping: sheetedEntries) { entry -> String in
            entry.sheetDirectory ?? ""
        }
        let sheetNames = grouped.keys.sorted()
        XCTAssertEqual(sheetNames.count, 226, "Expected exactly 226 formula sheets (225 catalogue groups + repo-sample)")

        let outputDir = ProcessInfo.processInfo.environment["THI360_FORMULA_SHEETS_DIR"]
        if let outputDir, !outputDir.isEmpty {
            try FileManager.default.createDirectory(
                at: URL(fileURLWithPath: outputDir, isDirectory: true),
                withIntermediateDirectories: true
            )
        }

        var manifestRows: [String: Int] = [:]

        for sheetName in sheetNames {
            let entries = (grouped[sheetName] ?? []).sorted {
                ($0.sheetItemNumber ?? "") < ($1.sheetItemNumber ?? "")
            }
            XCTAssertFalse(entries.isEmpty, "Sheet \(sheetName) must contain at least one entry")

            let sheetImage = try composeSheet(sheetName: sheetName, entries: entries)
            let pngData = try XCTUnwrap(sheetImage.pngData(), "Failed encoding PNG for sheet \(sheetName)")
            XCTAssertGreaterThan(pngData.count, 256, "Sheet \(sheetName) PNG unexpectedly small")

            let attachment = XCTAttachment(data: pngData, uniformTypeIdentifier: "public.png")
            attachment.name = "\(sheetName).png"
            attachment.lifetime = .keepAlways
            add(attachment)

            if let outputDir, !outputDir.isEmpty {
                let target = URL(fileURLWithPath: outputDir, isDirectory: true)
                    .appendingPathComponent("\(sheetName).png")
                try pngData.write(to: target)
            }
            manifestRows[sheetName] = entries.count
        }

        XCTAssertEqual(manifestRows.count, 226)
        if let outputDir, !outputDir.isEmpty {
            let encoder = JSONEncoder()
            encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
            let manifestData = try encoder.encode(manifestRows)
            try manifestData.write(
                to: URL(fileURLWithPath: outputDir, isDirectory: true).appendingPathComponent("sheets-manifest.json")
            )
        }
    }

    private func composeSheet(sheetName: String, entries: [FormulaEntry]) throws -> UIImage {
        let colWidth: CGFloat = 390
        let gap: CGFloat = 12
        let pad: CGFloat = 12
        let totalWidth = pad * 2 + colWidth * 2 + gap
        let headerHeight: CGFloat = 32
        let textColor = UIColor(hex: BrandPalette.defaultLight.text)
        let mutedColor = UIColor(hex: BrandPalette.defaultLight.textMuted)
        let borderColor = UIColor(hex: BrandPalette.defaultLight.borderStrong)
        let warningColor = UIColor(hex: StatusPalette.light.warning)
        let warningBgColor = UIColor(hex: StatusPalette.light.warningBg)
        let labelFont = UIFont.monospacedSystemFont(ofSize: 11, weight: .regular)
        let titleFont = UIFont.systemFont(ofSize: 14, weight: .bold)

        struct PreparedRow {
            let number: String
            let latex: String
            let webImage: UIImage?
            let appRender: FormulaRender
            let labelHeight: CGFloat
            let contentHeight: CGFloat
            var totalHeight: CGFloat { labelHeight + contentHeight + 16 }
        }

        var prepared: [PreparedRow] = []
        for entry in entries {
            let sheetKey = try XCTUnwrap(entry.sheetKey)
            let number = entry.sheetItemNumber ?? "000"
            let webURL = FormulaCorpusLoader.webRenderImageURL(for: sheetKey)
            let webImage = UIImage(contentsOfFile: webURL.path)
            XCTAssertNotNil(webImage, "Missing web reference PNG at \(webURL.path)")

            let formula = Formula(source: entry.latex, display: .block)
            let appRender = Self.engine.render(formula, pointSize: 17, color: textColor)

            let labelStr = "\(number) · \(entry.latex)" as NSString
            let labelRect = labelStr.boundingRect(
                with: CGSize(width: totalWidth - pad * 2, height: 60),
                options: [.usesLineFragmentOrigin, .usesFontLeading],
                attributes: [.font: labelFont],
                context: nil
            )

            let webHeight = webImage.map { img -> CGFloat in
                let scale = min(1.0, (colWidth - 16) / max(img.size.width, 1))
                return img.size.height * scale
            } ?? 32

            let appHeight: CGFloat
            switch appRender {
            case let .rendered(_, width, ascent, descent):
                let naturalH = ascent + descent
                let scale = min(1.0, (colWidth - 16) / max(width, 1))
                appHeight = naturalH * scale
            case .unsupported:
                appHeight = 32
            }

            prepared.append(
                PreparedRow(
                    number: number,
                    latex: entry.latex,
                    webImage: webImage,
                    appRender: appRender,
                    labelHeight: ceil(labelRect.height) + 4,
                    contentHeight: max(ceil(max(webHeight, appHeight)) + 12, 36)
                )
            )
        }

        let totalHeight = pad + headerHeight + prepared.reduce(0) { $0 + $1.totalHeight } + pad
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1.0
        format.opaque = true

        return UIGraphicsImageRenderer(size: CGSize(width: totalWidth, height: totalHeight), format: format).image { ctx in
            UIColor.white.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: totalWidth, height: totalHeight))

            let titleStr = "\(sheetName) (\(entries.count) formulas) — Left: Web KaTeX 0.19.1 | Right: iOS SwaTex 0.6.0" as NSString
            titleStr.draw(
                at: CGPoint(x: pad, y: pad),
                withAttributes: [.font: titleFont, .foregroundColor: textColor]
            )

            var y = pad + headerHeight
            for row in prepared {
                let rowTop = y
                let labelStr = "\(row.number) · \(row.latex)" as NSString
                labelStr.draw(
                    in: CGRect(x: pad, y: y, width: totalWidth - pad * 2, height: row.labelHeight),
                    withAttributes: [.font: labelFont, .foregroundColor: mutedColor]
                )
                y += row.labelHeight

                let leftBox = CGRect(x: pad, y: y, width: colWidth, height: row.contentHeight)
                let rightBox = CGRect(x: pad + colWidth + gap, y: y, width: colWidth, height: row.contentHeight)

                borderColor.setStroke()
                ctx.stroke(leftBox)
                ctx.stroke(rightBox)

                if let webImg = row.webImage {
                    let scale = min(1.0, (colWidth - 16) / max(webImg.size.width, 1))
                    let drawW = webImg.size.width * scale
                    let drawH = webImg.size.height * scale
                    let drawRect = CGRect(
                        x: leftBox.minX + (leftBox.width - drawW) / 2,
                        y: leftBox.minY + (leftBox.height - drawH) / 2,
                        width: drawW,
                        height: drawH
                    )
                    webImg.draw(in: drawRect)
                }

                switch row.appRender {
                case let .rendered(img, width, ascent, descent):
                    let h = max(ascent + descent, 1)
                    let scale = min(1.0, (colWidth - 16) / max(width, 1))
                    let drawW = width * scale
                    let drawH = h * scale
                    let drawRect = CGRect(
                        x: rightBox.minX + (rightBox.width - drawW) / 2,
                        y: rightBox.minY + (rightBox.height - drawH) / 2,
                        width: drawW,
                        height: drawH
                    )
                    img.draw(in: drawRect)

                case let .unsupported(source):
                    let badgeRect = rightBox.insetBy(dx: 8, dy: 6)
                    warningBgColor.setFill()
                    ctx.fill(badgeRect)
                    warningColor.setStroke()
                    ctx.stroke(badgeRect)
                    (source as NSString).draw(
                        in: badgeRect.insetBy(dx: 4, dy: 4),
                        withAttributes: [.font: labelFont, .foregroundColor: warningColor]
                    )
                }

                y = rowTop + row.totalHeight
            }
        }
    }
}
