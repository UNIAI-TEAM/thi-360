import SwiftUI

struct StatusPalette: Codable, Equatable, Sendable {
    let success: String
    let successBg: String
    let warning: String
    let warningBg: String
    let info: String
    let infoBg: String
    let dangerSolid: String
    let onDanger: String

    static let light = StatusPalette(
        success: "#047857",
        successBg: "#ecfdf5",
        warning: "#b45309",
        warningBg: "#fffbeb",
        info: "#0e7490",
        infoBg: "#ecfeff",
        dangerSolid: "#dc2626",
        onDanger: "#ffffff"
    )

    static let dark = StatusPalette(
        success: "#34d399",
        successBg: "#0f2a21",
        warning: "#fbbf24",
        warningBg: "#2b2210",
        info: "#22d3ee",
        infoBg: "#0f2a30",
        dangerSolid: "#dc2626",
        onDanger: "#ffffff"
    )

    static func forMode(_ mode: ThemeMode) -> StatusPalette {
        switch mode {
        case .light: .light
        case .dark: .dark
        }
    }

    var allHexValues: [String] {
        [success, successBg, warning, warningBg, info, infoBg, dangerSolid, onDanger]
    }

    func equalsIgnoringHexCase(_ other: StatusPalette) -> Bool {
        zip(allHexValues, other.allHexValues).allSatisfy {
            $0.caseInsensitiveCompare($1) == .orderedSame
        }
    }

    var successColor: Color { Color(hex: success) }
    var successBgColor: Color { Color(hex: successBg) }
    var warningColor: Color { Color(hex: warning) }
    var warningBgColor: Color { Color(hex: warningBg) }
    var infoColor: Color { Color(hex: info) }
    var infoBgColor: Color { Color(hex: infoBg) }
    var dangerSolidColor: Color { Color(hex: dangerSolid) }
    var onDangerColor: Color { Color(hex: onDanger) }
}

private struct StatusPaletteEnvironmentKey: EnvironmentKey {
    static let defaultValue: StatusPalette = .light
}

extension EnvironmentValues {
    var statusPalette: StatusPalette {
        get { self[StatusPaletteEnvironmentKey.self] }
        set { self[StatusPaletteEnvironmentKey.self] = newValue }
    }
}
