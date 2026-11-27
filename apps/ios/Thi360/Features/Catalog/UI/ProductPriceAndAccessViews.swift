import SwiftUI

struct ProductPriceView: View {
    let listPrice: Int64
    let price: Int64
    let promotion: PromotionInfo?
    let locale: AppLocale
    var isLarge: Bool = false

    @Environment(\.brandPalette) private var palette
    @Environment(\.statusPalette) private var statusPalette

    var discountPercent: Int? {
        CatalogFormatting.discountPercent(
            listPrice: listPrice,
            price: price,
            hasPromotion: promotion != nil
        )
    }

    var renderedListPriceText: String? {
        discountPercent != nil ? CatalogFormatting.formatPrice(listPrice, locale: locale) : nil
    }

    var renderedListPriceAccessibilityLabel: String? {
        guard let listPriceText = renderedListPriceText else { return nil }
        return "\(CatalogStrings.text("storefront.listPriceLabel", locale: locale)) \(listPriceText)"
    }

    var renderedDiscountText: String? {
        guard let percent = discountPercent else { return nil }
        return CatalogStrings.text("storefront.discount", locale: locale, replacements: ["percent": String(percent)])
    }

    var renderedSalePriceText: String {
        CatalogFormatting.formatPrice(price, locale: locale)
    }

    var renderedPromoEndsText: String? {
        guard discountPercent != nil, let promotion else { return nil }
        return CatalogStrings.text(
            "storefront.promoEnds",
            locale: locale,
            replacements: ["date": CatalogFormatting.formatDayMonth(promotion.endsAt)]
        )
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            if let listPriceText = renderedListPriceText,
               let listPriceA11y = renderedListPriceAccessibilityLabel,
               let discountText = renderedDiscountText
            {
                CatalogFlowLayout(horizontalSpacing: 8, verticalSpacing: 4) {
                    Text(listPriceText)
                        .font(.footnote)
                        .strikethrough(true, color: palette.textMutedColor)
                        .foregroundStyle(palette.textMutedColor)
                        .accessibilityLabel(listPriceA11y)

                    Text(discountText)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(statusPalette.warningColor)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 2)
                        .background(
                            Capsule(style: .continuous)
                                .fill(statusPalette.warningBgColor)
                        )
                }
            }

            CatalogFlowLayout(horizontalSpacing: 10, verticalSpacing: 4) {
                Text(renderedSalePriceText)
                    .font(isLarge ? .title.weight(.bold) : .title3.weight(.bold))
                    .foregroundStyle(palette.textColor)
                    .fixedSize(horizontal: false, vertical: true)

                if let promoEndsText = renderedPromoEndsText {
                    Text(promoEndsText)
                        .font(.caption.weight(.medium))
                        .foregroundStyle(statusPalette.warningColor)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct AccessActionSectionView: View {
    let code: String
    let versionNo: Int
    let price: Int
    let isBundle: Bool
    let isPricePanel: Bool
    let cell: AccessCell
    let locale: AppLocale
    let onButtonTap: (AccessButtonKind) -> Void
    let onReloadAccess: () -> Void
    var onViewDetail: (() -> Void)? = nil

    @Environment(\.brandPalette) private var palette
    @Environment(\.statusPalette) private var statusPalette

    var renderedLoadingLabel: String? {
        guard case .loading = cell else { return nil }
        return CatalogStrings.text("storefront.access.loading", locale: locale)
    }

    var renderedFailedStrings: [String] {
        guard case .failed = cell else { return [] }
        var lines = [
            CatalogStrings.text("storefront.access.loadError", locale: locale),
            CatalogStrings.text("storefront.access.reload", locale: locale)
        ]
        if !isPricePanel, onViewDetail != nil {
            lines.append(CatalogStrings.text("storefront.access.viewDetail", locale: locale))
        }
        return lines
    }

    var renderedReadyPlan: AccessButtonPlan? {
        guard case let .ready(row) = cell else { return nil }
        return isPricePanel
            ? AccessButtons.forPricePanel(row: row, seenVersionNo: versionNo, seenPrice: price)
            : AccessButtons.forCard(row: row, isBundle: isBundle, seenVersionNo: versionNo, seenPrice: price)
    }

    func renderedText(for note: AccessNoteKind) -> String {
        switch note {
        case .continueInProgress:
            return CatalogStrings.text("storefront.access.continueNote", locale: locale)
        case let .attemptsLeft(count):
            return CatalogStrings.plural("storefront.access.attemptsLeft", count: count, locale: locale)
        case .exhausted:
            return CatalogStrings.text("storefront.access.exhaustedNote", locale: locale)
        case let .pendingTopUp(orderCode):
            return CatalogStrings.text("storefront.access.pendingTopUp", locale: locale, replacements: ["orderCode": orderCode])
        case let .checkingOrder(orderCode):
            return CatalogStrings.text("storefront.access.checkingNote", locale: locale, replacements: ["orderCode": orderCode])
        }
    }

    func renderedTitle(for button: AccessButtonKind, isBundle: Bool) -> String {
        switch button {
        case .continue:
            return CatalogStrings.text("storefront.access.continue", locale: locale)
        case .enter:
            return CatalogStrings.text("storefront.access.enter", locale: locale)
        case .buy:
            return CatalogStrings.text(isBundle ? "storefront.access.buyBundle" : "storefront.access.buy", locale: locale)
        case .buyAgain:
            return CatalogStrings.text("storefront.access.buyAgain", locale: locale)
        case .buyMore:
            return CatalogStrings.text("storefront.access.buyMore", locale: locale)
        case .checking:
            return CatalogStrings.text("storefront.access.checking", locale: locale)
        }
    }

    var renderedStrings: [String] {
        switch cell {
        case .loading:
            return [CatalogStrings.text("storefront.access.loading", locale: locale)]
        case .failed:
            return renderedFailedStrings
        case .ready:
            guard let plan = renderedReadyPlan else { return [] }
            var out: [String] = []
            if let lead = plan.leadNote { out.append(renderedText(for: lead)) }
            out.append(renderedTitle(for: plan.primary, isBundle: plan.isBundle))
            if let secondary = plan.secondary { out.append(renderedTitle(for: secondary, isBundle: plan.isBundle)) }
            if let trailing = plan.trailingNote { out.append(renderedText(for: trailing)) }
            if plan.showsViewDetail, onViewDetail != nil {
                out.append(CatalogStrings.text("storefront.access.viewDetail", locale: locale))
            }
            return out
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            switch cell {
            case .loading:
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(palette.bgSubtleColor)
                    .frame(maxWidth: .infinity, minHeight: 44, maxHeight: 44)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(CatalogStrings.text("storefront.access.loading", locale: locale))
                    .accessibilityIdentifier("catalog.access.loading.\(code)")

            case .failed:
                VStack(alignment: .leading, spacing: 8) {
                    Text(CatalogStrings.text("storefront.access.loadError", locale: locale))
                        .font(.footnote)
                        .foregroundStyle(palette.dangerTextColor)
                        .fixedSize(horizontal: false, vertical: true)

                    secondaryButton(
                        title: CatalogStrings.text("storefront.access.reload", locale: locale),
                        systemImage: "arrow.clockwise",
                        identifier: "catalog.access.reload.\(code)",
                        action: onReloadAccess
                    )

                    if !isPricePanel, let onViewDetail {
                        detailSecondaryButton(onTap: onViewDetail)
                    }
                }

            case let .ready(row):
                let plan = isPricePanel
                    ? AccessButtons.forPricePanel(row: row, seenVersionNo: versionNo, seenPrice: price)
                    : AccessButtons.forCard(row: row, isBundle: isBundle, seenVersionNo: versionNo, seenPrice: price)

                if let leadNote = plan.leadNote {
                    noteView(leadNote)
                }

                renderActionButton(plan.primary, isBundle: plan.isBundle, isPrimary: true)

                if let secondary = plan.secondary {
                    renderActionButton(secondary, isBundle: plan.isBundle, isPrimary: false)
                }

                if let trailingNote = plan.trailingNote {
                    noteView(trailingNote)
                }

                if plan.showsViewDetail, let onViewDetail {
                    detailSecondaryButton(onTap: onViewDetail)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    @ViewBuilder
    private func noteView(_ note: AccessNoteKind) -> some View {
        switch note {
        case .continueInProgress:
            Text(CatalogStrings.text("storefront.access.continueNote", locale: locale))
                .font(.footnote.weight(.medium))
                .foregroundStyle(palette.textSecondaryColor)
                .fixedSize(horizontal: false, vertical: true)

        case let .attemptsLeft(count):
            Text(
                CatalogStrings.plural(
                    "storefront.access.attemptsLeft",
                    count: count,
                    locale: locale
                )
            )
            .font(.footnote.weight(.semibold))
            .foregroundStyle(palette.primaryTextColor)
            .fixedSize(horizontal: false, vertical: true)

        case .exhausted:
            Text(CatalogStrings.text("storefront.access.exhaustedNote", locale: locale))
                .font(.footnote.weight(.medium))
                .foregroundStyle(palette.textSecondaryColor)
                .fixedSize(horizontal: false, vertical: true)

        case let .pendingTopUp(orderCode):
            Text(
                CatalogStrings.text(
                    "storefront.access.pendingTopUp",
                    locale: locale,
                    replacements: ["orderCode": orderCode]
                )
            )
            .font(.footnote)
            .foregroundStyle(statusPalette.warningColor)
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(statusPalette.warningBgColor)
            )
            .fixedSize(horizontal: false, vertical: true)

        case let .checkingOrder(orderCode):
            Text(
                CatalogStrings.text(
                    "storefront.access.checkingNote",
                    locale: locale,
                    replacements: ["orderCode": orderCode]
                )
            )
            .font(.footnote)
            .foregroundStyle(statusPalette.warningColor)
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(statusPalette.warningBgColor)
            )
            .fixedSize(horizontal: false, vertical: true)
        }
    }

    @ViewBuilder
    private func renderActionButton(_ button: AccessButtonKind, isBundle: Bool, isPrimary: Bool) -> some View {
        switch button {
        case .continue:
            primaryButton(
                title: CatalogStrings.text("storefront.access.continue", locale: locale),
                identifier: "catalog.access.continue.\(code)"
            ) {
                onButtonTap(button)
            }

        case .enter:
            primaryButton(
                title: CatalogStrings.text("storefront.access.enter", locale: locale),
                identifier: "catalog.access.enter.\(code)"
            ) {
                onButtonTap(button)
            }

        case .buy:
            let label = CatalogStrings.text(
                isBundle ? "storefront.access.buyBundle" : "storefront.access.buy",
                locale: locale
            )
            primaryButton(
                title: label,
                identifier: "catalog.access.buy.\(code)"
            ) {
                onButtonTap(button)
            }

        case .buyAgain:
            primaryButton(
                title: CatalogStrings.text("storefront.access.buyAgain", locale: locale),
                identifier: "catalog.access.buyAgain.\(code)"
            ) {
                onButtonTap(button)
            }

        case .buyMore:
            secondaryButton(
                title: CatalogStrings.text("storefront.access.buyMore", locale: locale),
                systemImage: nil,
                identifier: "catalog.access.buyMore.\(code)"
            ) {
                onButtonTap(button)
            }

        case .checking:
            Button(action: {}) {
                Text(CatalogStrings.text("storefront.access.checking", locale: locale))
                    .font(.body.weight(.semibold))
                    .foregroundStyle(statusPalette.warningColor)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(statusPalette.warningBgColor)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .strokeBorder(statusPalette.warningColor.opacity(0.45), lineWidth: 1)
                    )
            }
            .buttonStyle(.plain)
            .disabled(true)
            .accessibilityIdentifier("catalog.access.checking.\(code)")
        }
    }

    private func primaryButton(title: String, identifier: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.body.weight(.semibold))
                .foregroundStyle(palette.onPrimaryColor)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .frame(maxWidth: .infinity, minHeight: 44)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(palette.primaryFillColor)
                )
                .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(title)
        .accessibilityIdentifier(identifier)
    }

    private func secondaryButton(
        title: String,
        systemImage: String?,
        identifier: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 6) {
                if let systemImage {
                    Image(systemName: systemImage)
                        .font(.subheadline.weight(.semibold))
                        .accessibilityHidden(true)
                }
                Text(title)
                    .font(.subheadline.weight(.semibold))
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .foregroundStyle(palette.textColor)
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
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
        .accessibilityLabel(title)
        .accessibilityIdentifier(identifier)
    }

    private func detailSecondaryButton(onTap: @escaping () -> Void) -> some View {
        let title = CatalogStrings.text("storefront.access.viewDetail", locale: locale)
        return secondaryButton(
            title: title,
            systemImage: nil,
            identifier: "catalog.card.viewDetail.\(code)",
            action: onTap
        )
    }
}

struct CatalogProductCardView: View {
    let product: CatalogProductCard
    let accessCell: AccessCell
    let locale: AppLocale
    let isSelected: Bool
    var imageLoader: ImageLoader? = nil
    let onOpenDetail: () -> Void
    let onAccessButtonTap: (AccessButtonKind) -> Void
    let onReloadAccess: () -> Void

    @Environment(\.brandPalette) private var palette

    var renderedBadges: [String] {
        var badges: [String] = [
            product.examKind.code,
            CatalogStrings.formatLabel(for: product.format, locale: locale)
        ]
        if let scope = product.scopeLabel?.localized(for: locale).nonEmpty {
            badges.append(scope)
        }
        if product.isReview {
            badges.append(AppStrings.common("storefront.review", locale: locale))
        }
        return badges
    }

    var renderedSpecsLine: String {
        CatalogFormatting.cardSpecsLine(for: product, locale: locale)
    }

    var renderedPriceView: ProductPriceView {
        ProductPriceView(
            listPrice: product.listPrice,
            price: product.price,
            promotion: product.promotion,
            locale: locale
        )
    }

    var renderedAccessSection: AccessActionSectionView {
        AccessActionSectionView(
            code: product.code,
            versionNo: product.versionNo,
            price: Int(product.price),
            isBundle: product.isBundle,
            isPricePanel: false,
            cell: accessCell,
            locale: locale,
            onButtonTap: onAccessButtonTap,
            onReloadAccess: onReloadAccess,
            onViewDetail: onOpenDetail
        )
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Button(action: onOpenDetail) {
                ProductCardCoverView(product: product, locale: locale, imageLoader: imageLoader)
            }
            .buttonStyle(.plain)
            .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 10) {
                CatalogFlowLayout(horizontalSpacing: 6, verticalSpacing: 6) {
                    CatalogBadgeView(text: product.examKind.code, style: .primary)
                    CatalogBadgeView(
                        text: CatalogStrings.formatLabel(for: product.format, locale: locale),
                        style: .subtle
                    )
                    if let scope = product.scopeLabel?.localized(for: locale).nonEmpty {
                        CatalogBadgeView(text: scope, style: .subtle)
                    }
                    if product.isReview {
                        CatalogBadgeView(
                            text: AppStrings.common("storefront.review", locale: locale),
                            style: .info
                        )
                    }
                }

                Button(action: onOpenDetail) {
                    Text(product.name)
                        .font(.headline.weight(.bold))
                        .foregroundStyle(palette.textColor)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                        .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(product.name)
                .accessibilityIdentifier("catalog.card.title.\(product.code)")

                let specs = CatalogFormatting.cardSpecsLine(for: product, locale: locale)
                if !specs.isEmpty {
                    Text(specs)
                        .font(.footnote.weight(.medium))
                        .foregroundStyle(palette.textSecondaryColor)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 8)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(palette.bgSubtleColor)
                        )
                        .fixedSize(horizontal: false, vertical: true)
                }

                ProductPriceView(
                    listPrice: product.listPrice,
                    price: product.price,
                    promotion: product.promotion,
                    locale: locale
                )

                AccessActionSectionView(
                    code: product.code,
                    versionNo: product.versionNo,
                    price: Int(product.price),
                    isBundle: product.isBundle,
                    isPricePanel: false,
                    cell: accessCell,
                    locale: locale,
                    onButtonTap: onAccessButtonTap,
                    onReloadAccess: onReloadAccess,
                    onViewDetail: onOpenDetail
                )
            }
            .padding(16)
        }
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(palette.bgSurfaceColor)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .strokeBorder(
                    isSelected ? palette.primaryFillColor : palette.borderColor,
                    lineWidth: isSelected ? 2 : 1
                )
        )
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(isSelected ? [.isSelected] : [])
        .accessibilityIdentifier("catalog.card.\(product.code)")
    }
}
