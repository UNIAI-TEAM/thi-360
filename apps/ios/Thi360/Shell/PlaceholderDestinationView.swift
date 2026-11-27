import SwiftUI

struct BrandWordmarkHeader: View {
    let locale: AppLocale

    var body: some View {
        BrandMark(markSize: 30, locale: locale)
    }
}

struct PlaceholderDestinationView: View {
    let destination: Destination
    let locale: AppLocale
    var showsBrandHeader: Bool = false

    @Environment(\.brandPalette) private var palette

    private var title: String {
        ShellStrings.text(destination.titleKey, locale: locale)
    }

    private var emptyTitle: String {
        ShellStrings.text("shell.placeholder.title", locale: locale)
    }

    private var emptyDescription: String {
        guard let key = destination.placeholderDescriptionKey else { return "" }
        return ShellStrings.text(key, locale: locale)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                if showsBrandHeader {
                    BrandWordmarkHeader(locale: locale)
                }

                Text(title)
                    .font(.largeTitle.weight(.bold))
                    .foregroundStyle(palette.textColor)
                    .accessibilityAddTraits(.isHeader)
                    .accessibilityIdentifier("shell.placeholder.heading.\(destination.rawValue)")

                VStack(spacing: 12) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .fill(palette.primaryContainerColor)
                            .frame(width: 56, height: 56)
                        Image(systemName: destination.symbolName)
                            .font(.system(size: 24, weight: .medium))
                            .foregroundStyle(palette.primaryTextColor)
                    }
                    .accessibilityHidden(true)

                    Text(emptyTitle)
                        .font(.headline)
                        .foregroundStyle(palette.textColor)
                        .multilineTextAlignment(.center)
                        .accessibilityIdentifier("shell.placeholder.emptyTitle.\(destination.rawValue)")

                    if !emptyDescription.isEmpty {
                        Text(emptyDescription)
                            .font(.subheadline)
                            .foregroundStyle(palette.textMutedColor)
                            .multilineTextAlignment(.center)
                            .frame(maxWidth: 340)
                    }
                }
                .frame(maxWidth: .infinity, minHeight: 260)
                .padding(24)
                .background(
                    RoundedRectangle(cornerRadius: 20, style: .continuous)
                        .fill(palette.bgSurfaceColor)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 20, style: .continuous)
                        .strokeBorder(palette.borderColor, lineWidth: 1)
                )
            }
            .frame(maxWidth: 680, alignment: .leading)
            .frame(maxWidth: .infinity, alignment: .center)
            .padding(20)
        }
        .background(palette.bgPageColor.ignoresSafeArea())
        .accessibilityIdentifier("shell.placeholder.view.\(destination.rawValue)")
    }
}
