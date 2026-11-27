import SwiftUI

struct CatalogFlowLayout: Layout {
    let horizontalSpacing: CGFloat
    let verticalSpacing: CGFloat

    init(horizontalSpacing: CGFloat = 8, verticalSpacing: CGFloat = 8) {
        self.horizontalSpacing = horizontalSpacing
        self.verticalSpacing = verticalSpacing
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        let placement = computePlacement(maxWidth: maxWidth, subviews: subviews)
        return placement.size
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let placement = computePlacement(maxWidth: bounds.width, subviews: subviews)
        for (index, subview) in subviews.enumerated() where index < placement.origins.count {
            let origin = placement.origins[index]
            let size = placement.sizes[index]
            subview.place(
                at: CGPoint(x: bounds.minX + origin.x, y: bounds.minY + origin.y),
                proposal: ProposedViewSize(width: size.width, height: size.height)
            )
        }
    }

    private struct Placement {
        let origins: [CGPoint]
        let sizes: [CGSize]
        let size: CGSize
    }

    private func computePlacement(maxWidth: CGFloat, subviews: Subviews) -> Placement {
        guard !subviews.isEmpty else {
            return Placement(origins: [], sizes: [], size: .zero)
        }
        let availableWidth = maxWidth.isFinite ? max(1, maxWidth) : .greatestFiniteMagnitude
        var origins: [CGPoint] = []
        var sizes: [CGSize] = []
        origins.reserveCapacity(subviews.count)
        sizes.reserveCapacity(subviews.count)

        var cursorX: CGFloat = 0
        var cursorY: CGFloat = 0
        var rowHeight: CGFloat = 0
        var usedWidth: CGFloat = 0

        for subview in subviews {
            let measured = subview.sizeThatFits(ProposedViewSize(width: availableWidth, height: nil))
            let width = min(measured.width, availableWidth)
            let height = measured.height

            if cursorX > 0 && cursorX + horizontalSpacing + width > availableWidth {
                cursorX = 0
                cursorY += rowHeight + verticalSpacing
                rowHeight = 0
            }

            if cursorX > 0 {
                cursorX += horizontalSpacing
            }

            origins.append(CGPoint(x: cursorX, y: cursorY))
            sizes.append(CGSize(width: width, height: height))

            cursorX += width
            usedWidth = max(usedWidth, cursorX)
            rowHeight = max(rowHeight, height)
        }

        let totalHeight = cursorY + rowHeight
        let finalWidth = maxWidth.isFinite ? min(maxWidth, usedWidth) : usedWidth
        return Placement(
            origins: origins,
            sizes: sizes,
            size: CGSize(width: finalWidth, height: totalHeight)
        )
    }
}

enum CatalogBadgeStyle {
    case primary
    case subtle
    case info
    case warning
    case success
}

struct CatalogBadgeView: View {
    let text: String
    let style: CatalogBadgeStyle

    @Environment(\.brandPalette) private var palette
    @Environment(\.statusPalette) private var statusPalette

    private var foreground: Color {
        switch style {
        case .primary: palette.primaryTextColor
        case .subtle: palette.textSecondaryColor
        case .info: statusPalette.infoColor
        case .warning: statusPalette.warningColor
        case .success: statusPalette.successColor
        }
    }

    private var background: Color {
        switch style {
        case .primary: palette.primaryContainerColor
        case .subtle: palette.bgSubtleColor
        case .info: statusPalette.infoBgColor
        case .warning: statusPalette.warningBgColor
        case .success: statusPalette.successBgColor
        }
    }

    var body: some View {
        Text(text)
            .font(.caption.weight(.semibold))
            .foregroundStyle(foreground)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .fill(background)
            )
            .fixedSize(horizontal: false, vertical: true)
    }
}

struct CatalogSectionHeading: View {
    let title: String

    @Environment(\.brandPalette) private var palette

    var body: some View {
        HStack(spacing: 10) {
            RoundedRectangle(cornerRadius: 2, style: .continuous)
                .fill(palette.primaryFillColor)
                .frame(width: 4, height: 20)
                .accessibilityHidden(true)

            Text(title)
                .font(.headline.weight(.bold))
                .foregroundStyle(palette.textColor)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
        }
    }
}

struct CatalogYesNoLabel: View {
    let value: Bool
    let locale: AppLocale

    @Environment(\.brandPalette) private var palette

    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: value ? "checkmark" : "xmark")
                .font(.caption.weight(.bold))
                .foregroundStyle(value ? palette.primaryTextColor : palette.textMutedColor)
                .accessibilityHidden(true)
            Text(
                CatalogStrings.text(
                    value ? "storefront.detail.rights.yes" : "storefront.detail.rights.no",
                    locale: locale
                )
            )
            .font(.subheadline)
            .foregroundStyle(palette.textColor)
        }
    }
}

struct CatalogPolicySectionView: View {
    let locale: AppLocale
    let supportContact: SupportContact

    @Environment(\.brandPalette) private var palette

    private var channelsText: String {
        [supportContact.phone, supportContact.email]
            .filter { !$0.isEmpty }
            .joined(separator: " · ")
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            CatalogSectionHeading(
                title: CatalogStrings.text("storefront.detail.policy.title", locale: locale)
            )

            VStack(alignment: .leading, spacing: 8) {
                Text(CatalogStrings.text("storefront.detail.policy.priceNote", locale: locale))
                    .font(.subheadline)
                    .foregroundStyle(palette.textSecondaryColor)
                    .fixedSize(horizontal: false, vertical: true)

                let refundPolicy = CatalogStrings.text("storefront.detail.policy.refundPending", locale: locale)
                Text(
                    CatalogStrings.text(
                        "storefront.detail.policy.refund",
                        locale: locale,
                        replacements: ["policy": refundPolicy]
                    )
                )
                .font(.subheadline)
                .foregroundStyle(palette.textSecondaryColor)
                .fixedSize(horizontal: false, vertical: true)

                if channelsText.isEmpty {
                    Text(supportContact.pendingMessage(locale: locale))
                        .font(.subheadline)
                        .foregroundStyle(palette.textSecondaryColor)
                        .fixedSize(horizontal: false, vertical: true)
                } else {
                    Text(
                        CatalogStrings.text(
                            "storefront.detail.policy.contactLine",
                            locale: locale,
                            replacements: ["contact": channelsText]
                        )
                    )
                    .font(.subheadline)
                    .foregroundStyle(palette.textSecondaryColor)
                    .fixedSize(horizontal: false, vertical: true)
                }

                if !supportContact.hours.isEmpty {
                    Text(
                        CatalogStrings.text(
                            "storefront.detail.policy.hoursLine",
                            locale: locale,
                            replacements: ["hours": supportContact.hours]
                        )
                    )
                    .font(.subheadline)
                    .foregroundStyle(palette.textSecondaryColor)
                    .fixedSize(horizontal: false, vertical: true)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(palette.bgSurfaceColor)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .strokeBorder(palette.borderColor, lineWidth: 1)
            )
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("productDetail.policySection")
    }
}
