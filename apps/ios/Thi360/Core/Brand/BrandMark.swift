import SwiftUI

struct BrandMark: View {
    static let maxMarkSize: CGFloat = 96

    let markSize: CGFloat
    let locale: AppLocale
    var titleFont: Font? = nil

    @Environment(\.brandPalette) private var palette

    var clampedMarkSize: CGFloat {
        min(max(16, markSize), Self.maxMarkSize)
    }

    private var resolvedTitleFont: Font {
        if let titleFont {
            return titleFont
        }
        return clampedMarkSize <= 32 ? .headline.weight(.bold) : .title2.weight(.bold)
    }

    var body: some View {
        HStack(spacing: clampedMarkSize <= 32 ? 8 : 10) {
            Image("BrandMark")
                .renderingMode(.original)
                .resizable()
                .interpolation(.high)
                .aspectRatio(contentMode: .fit)
                .frame(width: clampedMarkSize, height: clampedMarkSize)
                .accessibilityHidden(true)

            Text(ShellStrings.text("shell.app.name", locale: locale))
                .font(resolvedTitleFont)
                .foregroundStyle(palette.textColor)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(ShellStrings.text("shell.app.name", locale: locale))
        .accessibilityIdentifier("brand.mark")
    }
}
