import SwiftUI
import UIKit

struct RichContentTokens: Equatable, Sendable {
    static let tableHeadLightHex = "#fafafb"
    static let tableHeadDarkHex = "#1a2126"

    let isDark: Bool
    let brand: BrandPalette
    let status: StatusPalette
    let tableHeadHex: String

    init(brand: BrandPalette, status: StatusPalette, colorScheme: ColorScheme) {
        self.isDark = (colorScheme == .dark)
        self.brand = brand
        self.status = status
        self.tableHeadHex = (colorScheme == .dark) ? Self.tableHeadDarkHex : Self.tableHeadLightHex
    }

    var textUIColor: UIColor { UIColor(hex: brand.text) }
    var textSecondaryUIColor: UIColor { UIColor(hex: brand.textSecondary) }
    var textMutedUIColor: UIColor { UIColor(hex: brand.textMuted) }
    var primaryTextUIColor: UIColor { UIColor(hex: brand.primaryText) }
    var borderUIColor: UIColor { UIColor(hex: brand.border) }
    var borderStrongUIColor: UIColor { UIColor(hex: brand.borderStrong) }
    var surfaceUIColor: UIColor { UIColor(hex: brand.bgSurface) }
    var pageUIColor: UIColor { UIColor(hex: brand.bgPage) }
    var subtleUIColor: UIColor { UIColor(hex: brand.bgSubtle) }
    var tableHeadUIColor: UIColor { UIColor(hex: tableHeadHex) }
    var infoUIColor: UIColor { UIColor(hex: status.info) }
    var infoBgUIColor: UIColor { UIColor(hex: status.infoBg) }
    var warningUIColor: UIColor { UIColor(hex: status.warning) }
    var warningBgUIColor: UIColor { UIColor(hex: status.warningBg) }

    var tableHeadColor: Color { Color(hex: tableHeadHex) }

    static func contrastRatio(foregroundHex: String, backgroundHex: String) -> Double {
        let l1 = relativeLuminance(hex: foregroundHex)
        let l2 = relativeLuminance(hex: backgroundHex)
        let lighter = max(l1, l2)
        let darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private static func relativeLuminance(hex: String) -> Double {
        let cleaned = hex.trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "#", with: "")
        guard cleaned.count == 6, let value = UInt64(cleaned, radix: 16) else { return 0 }
        let r = linearize(Double((value >> 16) & 0xFF) / 255.0)
        let g = linearize(Double((value >> 8) & 0xFF) / 255.0)
        let b = linearize(Double(value & 0xFF) / 255.0)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private static func linearize(_ channel: Double) -> Double {
        channel <= 0.03928 ? (channel / 12.92) : pow((channel + 0.055) / 1.055, 2.4)
    }
}

extension UIColor {
    convenience init(hex: String) {
        let cleaned = hex.trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "#", with: "")
        guard cleaned.count == 6, let value = UInt64(cleaned, radix: 16) else {
            self.init(white: 0, alpha: 1)
            return
        }
        let r = CGFloat((value >> 16) & 0xFF) / 255.0
        let g = CGFloat((value >> 8) & 0xFF) / 255.0
        let b = CGFloat(value & 0xFF) / 255.0
        self.init(red: r, green: g, blue: b, alpha: 1.0)
    }
}

enum RichContentStrings {
    static let table = "RichContent"

    static func imageLoadError(locale: AppLocale) -> String {
        AppStrings.text("richContent.image.loadError", table: table, locale: locale)
    }

    static func imageZoomButton(locale: AppLocale) -> String {
        AppStrings.text("richContent.image.zoomButton", table: table, locale: locale)
    }

    static func closeLabel(locale: AppLocale) -> String {
        AppStrings.common("common.close", locale: locale)
    }
}
