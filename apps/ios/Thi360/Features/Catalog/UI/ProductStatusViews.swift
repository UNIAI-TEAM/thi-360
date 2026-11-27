import SwiftUI

enum ProductStatusTone {
    case warning
    case muted
}

struct ProductStatusCardLayout<Actions: View>: View {
    let symbolName: String
    let tone: ProductStatusTone
    let eyebrow: String
    let title: String
    let description: String
    var meta: String? = nil
    let identifier: String
    @ViewBuilder let actions: () -> Actions

    @Environment(\.brandPalette) private var palette
    @Environment(\.statusPalette) private var statusPalette

    private var accentColor: Color {
        switch tone {
        case .warning: statusPalette.warningColor
        case .muted: palette.textMutedColor
        }
    }

    private var iconBackground: Color {
        switch tone {
        case .warning: statusPalette.warningBgColor
        case .muted: palette.bgSubtleColor
        }
    }

    var body: some View {
        VStack(spacing: 16) {
            ZStack {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(iconBackground)
                    .frame(width: 56, height: 56)

                Image(systemName: symbolName)
                    .font(.system(size: 26, weight: .semibold))
                    .foregroundStyle(accentColor)
            }
            .accessibilityHidden(true)

            Text(eyebrow)
                .font(.caption.weight(.semibold))
                .tracking(0.8)
                .foregroundStyle(accentColor)
                .multilineTextAlignment(.center)
                .accessibilityIdentifier("\(identifier).eyebrow")

            Text(title)
                .font(.title2.weight(.bold))
                .foregroundStyle(palette.textColor)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
                .accessibilityIdentifier("\(identifier).title")

            Text(description)
                .font(.body)
                .foregroundStyle(palette.textMutedColor)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityIdentifier("\(identifier).description")

            if let meta, !meta.isEmpty {
                Text(meta)
                    .font(.footnote)
                    .foregroundStyle(palette.textMutedColor)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("\(identifier).meta")
            }

            VStack(spacing: 10) {
                actions()
            }
            .padding(.top, 4)
        }
        .frame(maxWidth: 520)
        .padding(.horizontal, 24)
        .padding(.vertical, 32)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(palette.bgSurfaceColor)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .strokeBorder(palette.borderColor, lineWidth: 1)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier(identifier)
    }
}

struct ExamGoneView: View {
    let code: String
    let name: String
    let locale: AppLocale
    let onReturnToCatalog: () -> Void
    let onOpenMyExams: () -> Void

    @Environment(\.brandPalette) private var palette

    var renderedStrings: [String] {
        [
            CatalogStrings.text("storefront.gone.eyebrow", locale: locale),
            CatalogStrings.text("storefront.gone.title", locale: locale),
            CatalogFormatting.goneDescription(code: code, name: name, locale: locale),
            CatalogStrings.text("storefront.gone.owned", locale: locale),
            CatalogStrings.text("storefront.gone.toCatalog", locale: locale),
            CatalogStrings.text("storefront.gone.myExams", locale: locale)
        ]
    }

    var body: some View {
        ProductStatusCardLayout(
            symbolName: "nosign",
            tone: .warning,
            eyebrow: CatalogStrings.text("storefront.gone.eyebrow", locale: locale),
            title: CatalogStrings.text("storefront.gone.title", locale: locale),
            description: CatalogFormatting.goneDescription(code: code, name: name, locale: locale),
            meta: CatalogStrings.text("storefront.gone.owned", locale: locale),
            identifier: "productDetail.goneView"
        ) {
            Button(action: onReturnToCatalog) {
                Text(CatalogStrings.text("storefront.gone.toCatalog", locale: locale))
                    .font(.body.weight(.semibold))
                    .foregroundStyle(palette.onPrimaryColor)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.horizontal, 20)
                    .padding(.vertical, 12)
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(palette.primaryFillColor)
                    )
                    .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("productDetail.gone.toCatalogButton")

            Button(action: onOpenMyExams) {
                Text(CatalogStrings.text("storefront.gone.myExams", locale: locale))
                    .font(.body.weight(.semibold))
                    .foregroundStyle(palette.textColor)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.horizontal, 20)
                    .padding(.vertical, 12)
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(palette.bgSubtleColor)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .strokeBorder(palette.borderColor, lineWidth: 1)
                    )
                    .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("productDetail.gone.myExamsButton")
        }
    }
}

struct ExamNotFoundView: View {
    let locale: AppLocale
    let onReturnToCatalog: () -> Void

    @Environment(\.brandPalette) private var palette

    var renderedStrings: [String] {
        [
            CatalogStrings.text("storefront.notFound.eyebrow", locale: locale),
            CatalogStrings.text("storefront.notFound.title", locale: locale),
            CatalogStrings.text("storefront.notFound.description", locale: locale),
            CatalogStrings.text("storefront.notFound.toCatalog", locale: locale)
        ]
    }

    var body: some View {
        ProductStatusCardLayout(
            symbolName: "magnifyingglass",
            tone: .muted,
            eyebrow: CatalogStrings.text("storefront.notFound.eyebrow", locale: locale),
            title: CatalogStrings.text("storefront.notFound.title", locale: locale),
            description: CatalogStrings.text("storefront.notFound.description", locale: locale),
            identifier: "productDetail.notFoundView"
        ) {
            Button(action: onReturnToCatalog) {
                Text(CatalogStrings.text("storefront.notFound.toCatalog", locale: locale))
                    .font(.body.weight(.semibold))
                    .foregroundStyle(palette.onPrimaryColor)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.horizontal, 20)
                    .padding(.vertical, 12)
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(palette.primaryFillColor)
                    )
                    .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("productDetail.notFound.toCatalogButton")
        }
    }
}

struct BundleUnavailableView: View {
    let locale: AppLocale
    let onReturnToCatalog: () -> Void

    @Environment(\.brandPalette) private var palette

    var renderedStrings: [String] {
        [
            CatalogStrings.text("bundle.unavailablePage.eyebrow", locale: locale),
            CatalogStrings.text("bundle.unavailablePage.title", locale: locale),
            CatalogStrings.text("bundle.unavailablePage.description", locale: locale),
            CatalogStrings.text("bundle.buyLabel", locale: locale),
            CatalogStrings.text("bundle.unavailablePage.toCatalog", locale: locale)
        ]
    }

    var isBuyButtonEnabled: Bool { false }

    var body: some View {
        ProductStatusCardLayout(
            symbolName: "nosign",
            tone: .warning,
            eyebrow: CatalogStrings.text("bundle.unavailablePage.eyebrow", locale: locale),
            title: CatalogStrings.text("bundle.unavailablePage.title", locale: locale),
            description: CatalogStrings.text("bundle.unavailablePage.description", locale: locale),
            identifier: "productDetail.bundleUnavailableView"
        ) {
            Button(action: {}) {
                Text(CatalogStrings.text("bundle.buyLabel", locale: locale))
                    .font(.body.weight(.semibold))
                    .foregroundStyle(palette.textMutedColor)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.horizontal, 20)
                    .padding(.vertical, 12)
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(palette.bgSubtleColor)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .strokeBorder(palette.borderColor, lineWidth: 1)
                    )
            }
            .buttonStyle(.plain)
            .disabled(true)
            .accessibilityIdentifier("productDetail.unavailable.disabledBuyButton")

            Button(action: onReturnToCatalog) {
                Text(CatalogStrings.text("bundle.unavailablePage.toCatalog", locale: locale))
                    .font(.body.weight(.semibold))
                    .foregroundStyle(palette.textColor)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.horizontal, 20)
                    .padding(.vertical, 12)
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(palette.bgSubtleColor)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .strokeBorder(palette.borderColor, lineWidth: 1)
                    )
                    .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("productDetail.unavailable.toCatalogButton")
        }
    }
}
