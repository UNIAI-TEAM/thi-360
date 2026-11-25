import SwiftUI

struct BrandPalette: Codable, Equatable, Sendable {
    let primaryFill: String
    let onPrimary: String
    let primaryText: String
    let primaryContainer: String
    let primaryContainerStrong: String
    let accent: String
    let onAccent: String
    let bgPage: String
    let bgSurface: String
    let bgSubtle: String
    let text: String
    let textSecondary: String
    let textMuted: String
    let border: String
    let borderStrong: String
    let dangerText: String
    let dangerBg: String

    static let defaultLight = BrandPalette(
        primaryFill: "#047857",
        onPrimary: "#FFFFFF",
        primaryText: "#047857",
        primaryContainer: "#ECFDF5",
        primaryContainerStrong: "#DCFCE7",
        accent: "#F59223",
        onAccent: "#1A1A1F",
        bgPage: "#F7F8F9",
        bgSurface: "#FFFFFF",
        bgSubtle: "#F1F2F4",
        text: "#11181C",
        textSecondary: "#3A3A42",
        textMuted: "#6B6B72",
        border: "#E8E8EC",
        borderStrong: "#D4D4D8",
        dangerText: "#B91C1C",
        dangerBg: "#FEF2F2"
    )

    static let defaultDark = BrandPalette(
        primaryFill: "#55B38F",
        onPrimary: "#11181C",
        primaryText: "#10B981",
        primaryContainer: "#0A1F19",
        primaryContainerStrong: "#0C1F18",
        accent: "#F59223",
        onAccent: "#1A1A1F",
        bgPage: "#0F1418",
        bgSurface: "#171D22",
        bgSubtle: "#1E262C",
        text: "#E6EAED",
        textSecondary: "#C5CCD2",
        textMuted: "#A9B2BA",
        border: "#2A3238",
        borderStrong: "#3A444C",
        dangerText: "#F87171",
        dangerBg: "#2E1517"
    )

    var allHexValues: [String] {
        [
            primaryFill,
            onPrimary,
            primaryText,
            primaryContainer,
            primaryContainerStrong,
            accent,
            onAccent,
            bgPage,
            bgSurface,
            bgSubtle,
            text,
            textSecondary,
            textMuted,
            border,
            borderStrong,
            dangerText,
            dangerBg
        ]
    }

    var primaryFillColor: Color { Color(hex: primaryFill) }
    var onPrimaryColor: Color { Color(hex: onPrimary) }
    var primaryTextColor: Color { Color(hex: primaryText) }
    var primaryContainerColor: Color { Color(hex: primaryContainer) }
    var primaryContainerStrongColor: Color { Color(hex: primaryContainerStrong) }
    var accentColor: Color { Color(hex: accent) }
    var onAccentColor: Color { Color(hex: onAccent) }
    var bgPageColor: Color { Color(hex: bgPage) }
    var bgSurfaceColor: Color { Color(hex: bgSurface) }
    var bgSubtleColor: Color { Color(hex: bgSubtle) }
    var textColor: Color { Color(hex: text) }
    var textSecondaryColor: Color { Color(hex: textSecondary) }
    var textMutedColor: Color { Color(hex: textMuted) }
    var borderColor: Color { Color(hex: border) }
    var borderStrongColor: Color { Color(hex: borderStrong) }
    var dangerTextColor: Color { Color(hex: dangerText) }
    var dangerBgColor: Color { Color(hex: dangerBg) }
}

struct BrandPaletteSet: Codable, Equatable, Sendable {
    let presetId: String
    let defaultMode: ThemeMode
    let light: BrandPalette
    let dark: BrandPalette

    static let defaultThi360Green = BrandPaletteSet(
        presetId: "thi360-green",
        defaultMode: .light,
        light: .defaultLight,
        dark: .defaultDark
    )

    func palette(for mode: ThemeMode) -> BrandPalette {
        switch mode {
        case .light: light
        case .dark: dark
        }
    }
}

extension Color {
    init(hex: String) {
        let cleaned = hex.trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "#", with: "")
        guard cleaned.count == 6, let value = UInt64(cleaned, radix: 16) else {
            self = .clear
            return
        }
        let red = Double((value >> 16) & 0xFF) / 255.0
        let green = Double((value >> 8) & 0xFF) / 255.0
        let blue = Double(value & 0xFF) / 255.0
        self.init(.sRGB, red: red, green: green, blue: blue, opacity: 1.0)
    }
}

private struct BrandPaletteEnvironmentKey: EnvironmentKey {
    static let defaultValue: BrandPalette = .defaultLight
}

extension EnvironmentValues {
    var brandPalette: BrandPalette {
        get { self[BrandPaletteEnvironmentKey.self] }
        set { self[BrandPaletteEnvironmentKey.self] = newValue }
    }
}
