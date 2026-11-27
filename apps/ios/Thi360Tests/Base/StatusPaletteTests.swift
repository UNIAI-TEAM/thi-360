import Foundation
import XCTest
@testable import Thi360

final class StatusPaletteTests: XCTestCase {
    private struct ContractStatusColorsFile: Decodable {
        let light: StatusPalette
        let dark: StatusPalette
    }

    func testMatchesContractStatusColorsFileAndMeetsContrastInBothModes() throws {
        let contractURL = ContractFixtureLoader.contractsMobileURL
            .appendingPathComponent("appearance/status-colors.json")
        let data = try Data(contentsOf: contractURL)
        let contract = try JSONDecoder().decode(ContractStatusColorsFile.self, from: data)

        XCTAssertTrue(
            StatusPalette.light.equalsIgnoringHexCase(contract.light),
            "StatusPalette.light (\(StatusPalette.light)) must match contracts/mobile/appearance/status-colors.json (\(contract.light))"
        )
        XCTAssertTrue(
            StatusPalette.dark.equalsIgnoringHexCase(contract.dark),
            "StatusPalette.dark (\(StatusPalette.dark)) must match contracts/mobile/appearance/status-colors.json (\(contract.dark))"
        )

        let modes: [(name: String, status: StatusPalette, brand: BrandPalette)] = [
            ("light", .light, .defaultLight),
            ("dark", .dark, .defaultDark)
        ]

        for mode in modes {
            let pairs: [(name: String, fg: String, bg: String)] = [
                ("success/successBg", mode.status.success, mode.status.successBg),
                ("warning/warningBg", mode.status.warning, mode.status.warningBg),
                ("info/infoBg", mode.status.info, mode.status.infoBg),
                ("onDanger/dangerSolid", mode.status.onDanger, mode.status.dangerSolid),
                ("dangerText/dangerBg", mode.brand.dangerText, mode.brand.dangerBg)
            ]

            for pair in pairs {
                let ratio = try Self.contrastRatio(foregroundHex: pair.fg, backgroundHex: pair.bg)
                XCTAssertGreaterThanOrEqual(
                    ratio,
                    4.5,
                    "\(mode.name) \(pair.name) (\(pair.fg) on \(pair.bg)) contrast \(String(format: "%.2f", ratio)) < 4.5"
                )
            }
        }
    }

    private static func contrastRatio(foregroundHex: String, backgroundHex: String) throws -> Double {
        let l1 = try relativeLuminance(hex: foregroundHex)
        let l2 = try relativeLuminance(hex: backgroundHex)
        let lighter = max(l1, l2)
        let darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private static func relativeLuminance(hex: String) throws -> Double {
        let cleaned = hex.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: "#", with: "")
        guard cleaned.count == 6, let value = UInt64(cleaned, radix: 16) else {
            throw NSError(domain: "StatusPaletteTests", code: 1, userInfo: [NSLocalizedDescriptionKey: "Invalid hex: \(hex)"])
        }
        let r = linearize(Double((value >> 16) & 0xFF) / 255.0)
        let g = linearize(Double((value >> 8) & 0xFF) / 255.0)
        let b = linearize(Double(value & 0xFF) / 255.0)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private static func linearize(_ channel: Double) -> Double {
        channel <= 0.03928 ? (channel / 12.92) : pow((channel + 0.055) / 1.055, 2.4)
    }
}
