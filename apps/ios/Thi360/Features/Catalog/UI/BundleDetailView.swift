import SwiftUI

struct BundleDetailView: View {
    let product: ProductDetailModel
    let sellable: Bool
    let accessCell: AccessCell
    let locale: AppLocale
    let supportContact: SupportContact
    let onOpenExam: (String) -> Void
    let onBuyBundle: () -> Void
    let onReloadAccess: () -> Void

    @Environment(\.brandPalette) private var palette
    @Environment(\.statusPalette) private var statusPalette

    var readyAccessItems: [BundleAccessItemRow] {
        guard case let .ready(row) = accessCell else { return [] }
        return row.items ?? []
    }

    func accessItem(for item: BundleDetailItemModel) -> BundleAccessItemRow? {
        guard !readyAccessItems.isEmpty else { return nil }
        if !item.examId.isEmpty, let byId = readyAccessItems.first(where: { $0.examId == item.examId }) {
            return byId
        }
        return readyAccessItems.first(where: { $0.code == item.code })
    }

    var renderedExamCountBadge: String {
        CatalogStrings.plural("bundle.detail.examCountValue", count: product.items.count, locale: locale)
    }

    func renderedItemAccessChips(for item: BundleDetailItemModel) -> [String] {
        guard let itemAccess = accessItem(for: item) else { return [] }
        var chips: [String] = []
        if itemAccess.attemptsLeft > 0 {
            chips.append(CatalogStrings.plural("storefront.access.attemptsLeft", count: itemAccess.attemptsLeft, locale: locale))
        }
        if itemAccess.hasPendingFulfillment || itemAccess.state == .checking {
            chips.append(CatalogStrings.text("storefront.access.checking", locale: locale))
        }
        return chips
    }

    var renderedExistingEntitlementLines: [String] {
        guard sellable else { return [] }
        return readyAccessItems.filter { $0.attemptsLeft > 0 }.map { owned in
            let displayName = owned.name.nonEmpty
                ?? product.items.first(where: { $0.code == owned.code })?.name
                ?? owned.code
            return CatalogStrings.plural(
                "bundle.detail.existingEntitlement",
                count: owned.attemptsLeft,
                locale: locale,
                replacements: ["name": displayName]
            )
        }
    }

    var renderedPendingOrderLines: [String] {
        guard sellable else { return [] }
        return readyAccessItems.filter { $0.hasPendingFulfillment || $0.state == .checking }.map { pending in
            let displayName = pending.name.nonEmpty
                ?? product.items.first(where: { $0.code == pending.code })?.name
                ?? pending.code
            if let orderCode = pending.pendingOrderCode?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty {
                return CatalogStrings.text(
                    "bundle.detail.pendingOrder",
                    locale: locale,
                    replacements: ["orderCode": orderCode, "name": displayName]
                )
            }
            return CatalogStrings.text(
                "bundle.detail.pendingOrderNoCode",
                locale: locale,
                replacements: ["name": displayName]
            )
        }
    }

    var isBuyBundleButtonEnabled: Bool {
        guard sellable else { return false }
        if case .ready = accessCell { return true }
        return false
    }

    var renderedBuyPanelActionLabels: [String] {
        if !sellable {
            return [
                CatalogStrings.text("bundle.detail.unavailableTitle", locale: locale),
                CatalogStrings.text("bundle.detail.unavailableBody", locale: locale),
                CatalogStrings.text("bundle.buyLabel", locale: locale)
            ]
        }
        switch accessCell {
        case .loading:
            return [CatalogStrings.text("storefront.access.loading", locale: locale)]
        case .failed:
            return [
                CatalogStrings.text("storefront.access.loadError", locale: locale),
                CatalogStrings.text("storefront.access.reload", locale: locale)
            ]
        case .ready:
            return [CatalogStrings.text("bundle.buyLabel", locale: locale)]
                + renderedExistingEntitlementLines
                + renderedPendingOrderLines
                + [CatalogStrings.text("bundle.buyNote", locale: locale)]
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            headerCard
            itemsSection
            howSection
            CatalogPolicySectionView(locale: locale, supportContact: supportContact)
            buyPanelSection
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("productDetail.bundleView")
    }

    private var headerCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            BundleDetailBannerView(examKindCode: product.examKind.code, locale: locale)

            VStack(alignment: .leading, spacing: 12) {
                CatalogFlowLayout(horizontalSpacing: 6, verticalSpacing: 6) {
                    CatalogBadgeView(
                        text: product.examKind.localizedNameOrCode(for: locale),
                        style: .primary
                    )
                    CatalogBadgeView(
                        text: CatalogStrings.formatLabel(for: product.format, locale: locale),
                        style: .subtle
                    )
                    CatalogBadgeView(
                        text: CatalogStrings.plural(
                            "bundle.detail.examCountValue",
                            count: product.items.count,
                            locale: locale
                        ),
                        style: .subtle
                    )
                    if product.isReview {
                        CatalogBadgeView(
                            text: AppStrings.common("storefront.review", locale: locale),
                            style: .info
                        )
                    }
                }

                Text("\(CatalogStrings.text("storefront.detail.codeLabel", locale: locale)) \(product.code)")
                    .font(.footnote)
                    .foregroundStyle(palette.textMutedColor)
                    .accessibilityIdentifier("productDetail.code")

                Text(product.name)
                    .font(.title2.weight(.bold))
                    .foregroundStyle(palette.textColor)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityAddTraits(.isHeader)
                    .accessibilityIdentifier("productDetail.title")

                if let summary = product.summary {
                    Text(summary)
                        .font(.body)
                        .foregroundStyle(palette.textSecondaryColor)
                        .fixedSize(horizontal: false, vertical: true)
                }

                if let description = product.description {
                    Text(description)
                        .font(.subheadline)
                        .foregroundStyle(palette.textSecondaryColor)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .padding(16)
        }
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(palette.bgSurfaceColor)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .strokeBorder(palette.borderColor, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    private var itemsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            CatalogFlowLayout(horizontalSpacing: 8, verticalSpacing: 6) {
                CatalogSectionHeading(
                    title: CatalogStrings.text("bundle.detail.itemsTitle", locale: locale)
                )
                CatalogBadgeView(
                    text: CatalogStrings.plural(
                        "bundle.detail.examCountValue",
                        count: product.items.count,
                        locale: locale
                    ),
                    style: .subtle
                )
            }

            VStack(spacing: 12) {
                ForEach(Array(product.items.enumerated()), id: \.element.id) { index, item in
                    bundleExamRow(item: item, number: index + 1)
                }
            }

            Text(CatalogStrings.text("bundle.detail.itemsNote", locale: locale))
                .font(.caption)
                .foregroundStyle(palette.textMutedColor)
                .fixedSize(horizontal: false, vertical: true)
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("productDetail.bundleItemsSection")
    }

    private func bundleExamRow(item: BundleDetailItemModel, number: Int) -> some View {
        let itemAccess = accessItem(for: item)
        let subtitle = CatalogFormatting.bundleItemSubtitle(for: item, locale: locale)
        let questions = CatalogFormatting.formatIntRange(item.questionCount, locale: locale)
        let duration = CatalogFormatting.formatIntRange(item.durationMinutes, locale: locale)
        let maxPoints = CatalogFormatting.formatDecimalRange(item.maxPoints, locale: locale)

        return VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 12) {
                Text(String(number))
                    .font(.footnote.weight(.bold))
                    .foregroundStyle(palette.primaryTextColor)
                    .frame(width: 28, height: 28)
                    .background(
                        Circle()
                            .fill(palette.primaryContainerColor)
                    )
                    .accessibilityHidden(true)

                VStack(alignment: .leading, spacing: 6) {
                    Button {
                        onOpenExam(item.code)
                    } label: {
                        VStack(alignment: .leading, spacing: 4) {
                            HStack(alignment: .firstTextBaseline, spacing: 6) {
                                Text(item.name)
                                    .font(.headline.weight(.semibold))
                                    .foregroundStyle(palette.textColor)
                                    .multilineTextAlignment(.leading)
                                    .fixedSize(horizontal: false, vertical: true)
                                Spacer(minLength: 4)
                                Image(systemName: "chevron.right")
                                    .font(.caption.weight(.semibold))
                                    .foregroundStyle(palette.textMutedColor)
                                    .accessibilityHidden(true)
                            }

                            if !subtitle.isEmpty {
                                Text(subtitle)
                                    .font(.footnote)
                                    .foregroundStyle(palette.textMutedColor)
                                    .multilineTextAlignment(.leading)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                        }
                        .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("\(number). \(item.name)")
                    .accessibilityIdentifier("productDetail.bundleItem.\(item.code)")

                    CatalogFlowLayout(horizontalSpacing: 6, verticalSpacing: 6) {
                        if item.isReview {
                            CatalogBadgeView(
                                text: AppStrings.common("storefront.review", locale: locale),
                                style: .info
                            )
                        }

                        if let itemAccess {
                            if itemAccess.attemptsLeft > 0 {
                                CatalogBadgeView(
                                    text: CatalogStrings.plural(
                                        "storefront.access.attemptsLeft",
                                        count: itemAccess.attemptsLeft,
                                        locale: locale
                                    ),
                                    style: .primary
                                )
                            }
                            if itemAccess.hasPendingFulfillment || itemAccess.state == .checking {
                                CatalogBadgeView(
                                    text: CatalogStrings.text("storefront.access.checking", locale: locale),
                                    style: .warning
                                )
                            }
                        }
                    }

                    let metricParts: [String] = {
                        var result: [String] = []
                        if let questions {
                            result.append(
                                AppStrings.common(
                                    "storefront.units.questions",
                                    locale: locale,
                                    replacements: ["value": questions]
                                )
                            )
                        }
                        if let duration {
                            result.append(
                                AppStrings.common(
                                    "storefront.units.minutes",
                                    locale: locale,
                                    replacements: ["value": duration]
                                )
                            )
                        }
                        if let maxPoints {
                            result.append(
                                CatalogStrings.text(
                                    "storefront.detail.how.maxScore",
                                    locale: locale,
                                    replacements: ["score": maxPoints]
                                )
                            )
                        }
                        return result
                    }()

                    if !metricParts.isEmpty {
                        Text(metricParts.joined(separator: " · "))
                            .font(.subheadline)
                            .foregroundStyle(palette.textSecondaryColor)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }

            Divider()

            VStack(alignment: .leading, spacing: 8) {
                let attemptsText = CatalogStrings.plural(
                    "storefront.units.attempts",
                    table: AppStrings.commonTable,
                    count: item.attemptsGranted,
                    locale: locale
                )
                CatalogFlowLayout(horizontalSpacing: 10, verticalSpacing: 4) {
                    Text("\(CatalogStrings.text("bundle.detail.examRightsPrefix", locale: locale)) \(attemptsText)")
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(palette.textColor)
                        .fixedSize(horizontal: false, vertical: true)

                    if let resultView = item.resultView {
                        Text(CatalogStrings.resultViewLabel(for: resultView, locale: locale))
                            .font(.footnote)
                            .foregroundStyle(palette.textSecondaryColor)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }

                CatalogFlowLayout(horizontalSpacing: 14, verticalSpacing: 6) {
                    inlineBoolean(
                        label: CatalogStrings.text("storefront.detail.rights.practice", locale: locale),
                        value: item.benefits.practice
                    )
                    inlineBoolean(
                        label: CatalogStrings.text("storefront.detail.rights.strengths", locale: locale),
                        value: item.benefits.strengthWeakness
                    )
                    inlineBoolean(
                        label: CatalogStrings.text("storefront.detail.rights.ai", locale: locale),
                        value: item.benefits.aiExplanation
                    )
                }

                if let note = item.benefits.note {
                    Text("\(CatalogStrings.text("storefront.detail.rights.noteLabel", locale: locale)) \(note)")
                        .font(.caption)
                        .foregroundStyle(palette.textMutedColor)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(palette.bgSurfaceColor)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .strokeBorder(palette.borderColor, lineWidth: 1)
        )
    }

    private func inlineBoolean(label: String, value: Bool) -> some View {
        HStack(spacing: 4) {
            Text("\(label):")
                .font(.caption)
                .foregroundStyle(palette.textMutedColor)
            CatalogYesNoLabel(value: value, locale: locale)
        }
    }

    private var howSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            CatalogSectionHeading(
                title: CatalogStrings.text("bundle.detail.howTitle", locale: locale)
            )

            VStack(alignment: .leading, spacing: 10) {
                bulletPoint(CatalogStrings.text("bundle.detail.how1", locale: locale))
                bulletPoint(CatalogStrings.text("bundle.detail.how2", locale: locale))
                bulletPoint(CatalogStrings.text("bundle.detail.how3", locale: locale))
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
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
        .accessibilityIdentifier("productDetail.bundleHowSection")
    }

    private func bulletPoint(_ text: String) -> some View {
        HStack(alignment: .top, spacing: 8) {
            Circle()
                .fill(palette.primaryFillColor)
                .frame(width: 6, height: 6)
                .padding(.top, 7)
                .accessibilityHidden(true)
            Text(text)
                .font(.subheadline)
                .foregroundStyle(palette.textSecondaryColor)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var buyPanelSection: some View {
        let questions = CatalogFormatting.formatIntRange(product.questionCount, locale: locale)
        let minutes = CatalogFormatting.formatIntRange(product.durationMinutes, locale: locale)
        let ownedItems = readyAccessItems.filter { $0.attemptsLeft > 0 }
        let pendingItems = readyAccessItems.filter { $0.hasPendingFulfillment || $0.state == .checking }

        return VStack(alignment: .leading, spacing: 12) {
            Text(CatalogStrings.text("bundle.detail.priceLabel", locale: locale))
                .font(.caption.weight(.semibold))
                .foregroundStyle(palette.textMutedColor)
                .textCase(.uppercase)
                .accessibilityAddTraits(.isHeader)

            ProductPriceView(
                listPrice: product.listPrice,
                price: product.price,
                promotion: product.promotion,
                locale: locale,
                isLarge: true
            )

            if let promotion = product.promotion {
                let endsText = CatalogStrings.text(
                    "storefront.detail.promoEnds",
                    locale: locale,
                    replacements: ["date": CatalogFormatting.formatDayMonth(promotion.endsAt)]
                )
                Text("\(promotion.name) · \(endsText)")
                    .font(.footnote.weight(.medium))
                    .foregroundStyle(statusPalette.warningColor)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("productDetail.promotionLine")
            }

            Divider()

            VStack(alignment: .leading, spacing: 8) {
                summaryRow(
                    label: CatalogStrings.text("bundle.detail.examCountLabel", locale: locale),
                    value: CatalogStrings.plural(
                        "bundle.detail.examCountValue",
                        count: product.items.count,
                        locale: locale
                    )
                )
                summaryRow(
                    label: CatalogStrings.text("bundle.detail.attemptsPerExamLabel", locale: locale),
                    value: CatalogStrings.plural(
                        "storefront.units.attempts",
                        table: AppStrings.commonTable,
                        count: product.attemptsGranted,
                        locale: locale
                    )
                )
                if let questions {
                    summaryRow(
                        label: CatalogStrings.text("bundle.detail.totalQuestionsLabel", locale: locale),
                        value: AppStrings.common(
                            "storefront.units.questions",
                            locale: locale,
                            replacements: ["value": questions]
                        )
                    )
                }
                if let minutes {
                    summaryRow(
                        label: CatalogStrings.text("bundle.detail.totalDurationLabel", locale: locale),
                        value: AppStrings.common(
                            "storefront.units.minutes",
                            locale: locale,
                            replacements: ["value": minutes]
                        )
                    )
                }
            }

            Text(CatalogStrings.text("storefront.detail.includesTax", locale: locale))
                .font(.caption)
                .foregroundStyle(palette.textMutedColor)

            if !sellable {
                VStack(alignment: .leading, spacing: 10) {
                    HStack(alignment: .top, spacing: 10) {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(statusPalette.warningColor)
                            .padding(.top, 2)
                            .accessibilityHidden(true)

                        VStack(alignment: .leading, spacing: 4) {
                            Text(CatalogStrings.text("bundle.detail.unavailableTitle", locale: locale))
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(statusPalette.warningColor)
                                .fixedSize(horizontal: false, vertical: true)
                            Text(CatalogStrings.text("bundle.detail.unavailableBody", locale: locale))
                                .font(.footnote)
                                .foregroundStyle(palette.textColor)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(statusPalette.warningBgColor)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .strokeBorder(statusPalette.warningColor.opacity(0.45), lineWidth: 1)
                    )
                    .accessibilityElement(children: .combine)
                    .accessibilityIdentifier("productDetail.bundleUnavailableBanner")

                    disabledBuyBundleButton
                }
            } else {
                switch accessCell {
                case .loading:
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(palette.bgSubtleColor)
                        .frame(maxWidth: .infinity, minHeight: 44, maxHeight: 44)
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel(CatalogStrings.text("storefront.access.loading", locale: locale))
                        .accessibilityIdentifier("productDetail.bundleBuyLoading")

                case .failed:
                    VStack(alignment: .leading, spacing: 8) {
                        Text(CatalogStrings.text("storefront.access.loadError", locale: locale))
                            .font(.footnote)
                            .foregroundStyle(palette.dangerTextColor)
                            .fixedSize(horizontal: false, vertical: true)

                        Button(action: onReloadAccess) {
                            HStack(spacing: 6) {
                                Image(systemName: "arrow.clockwise")
                                    .font(.subheadline.weight(.semibold))
                                    .accessibilityHidden(true)
                                Text(CatalogStrings.text("storefront.access.reload", locale: locale))
                                    .font(.subheadline.weight(.semibold))
                            }
                            .foregroundStyle(palette.textColor)
                            .padding(.horizontal, 14)
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
                        .accessibilityIdentifier("productDetail.bundleReloadAccess")
                    }

                case .ready:
                    Button(action: onBuyBundle) {
                        Text(CatalogStrings.text("bundle.buyLabel", locale: locale))
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
                    .accessibilityLabel(CatalogStrings.text("bundle.buyLabel", locale: locale))
                    .accessibilityIdentifier("productDetail.bundleBuyButton")

                    if !ownedItems.isEmpty {
                        VStack(alignment: .leading, spacing: 8) {
                            ForEach(ownedItems) { owned in
                                let displayName = owned.name.nonEmpty
                                    ?? product.items.first(where: { $0.code == owned.code })?.name
                                    ?? owned.code
                                HStack(alignment: .top, spacing: 8) {
                                    Image(systemName: "info.circle.fill")
                                        .font(.footnote)
                                        .foregroundStyle(statusPalette.infoColor)
                                        .padding(.top, 2)
                                        .accessibilityHidden(true)
                                    Text(
                                        CatalogStrings.plural(
                                            "bundle.detail.existingEntitlement",
                                            count: owned.attemptsLeft,
                                            locale: locale,
                                            replacements: ["name": displayName]
                                        )
                                    )
                                    .font(.footnote)
                                    .foregroundStyle(palette.textColor)
                                    .fixedSize(horizontal: false, vertical: true)
                                }
                            }
                        }
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(statusPalette.infoBgColor)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .strokeBorder(palette.primaryFillColor.opacity(0.25), lineWidth: 1)
                        )
                    }

                    if !pendingItems.isEmpty {
                        VStack(alignment: .leading, spacing: 8) {
                            ForEach(pendingItems) { pending in
                                let displayName = pending.name.nonEmpty
                                    ?? product.items.first(where: { $0.code == pending.code })?.name
                                    ?? pending.code
                                let message: String = {
                                    if let orderCode = pending.pendingOrderCode?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty {
                                        return CatalogStrings.text(
                                            "bundle.detail.pendingOrder",
                                            locale: locale,
                                            replacements: ["orderCode": orderCode, "name": displayName]
                                        )
                                    }
                                    return CatalogStrings.text(
                                        "bundle.detail.pendingOrderNoCode",
                                        locale: locale,
                                        replacements: ["name": displayName]
                                    )
                                }()
                                HStack(alignment: .top, spacing: 8) {
                                    Image(systemName: "info.circle.fill")
                                        .font(.footnote)
                                        .foregroundStyle(statusPalette.warningColor)
                                        .padding(.top, 2)
                                        .accessibilityHidden(true)
                                    Text(message)
                                        .font(.footnote)
                                        .foregroundStyle(palette.textColor)
                                        .fixedSize(horizontal: false, vertical: true)
                                }
                            }
                        }
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(statusPalette.warningBgColor)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .strokeBorder(statusPalette.warningColor.opacity(0.4), lineWidth: 1)
                        )
                    }

                    Text(CatalogStrings.text("bundle.buyNote", locale: locale))
                        .font(.caption)
                        .foregroundStyle(palette.textMutedColor)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(palette.bgSurfaceColor)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .strokeBorder(palette.borderColor, lineWidth: 1)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("productDetail.bundleBuyPanel")
    }

    private var disabledBuyBundleButton: some View {
        Button(action: {}) {
            Text(CatalogStrings.text("bundle.buyLabel", locale: locale))
                .font(.body.weight(.semibold))
                .foregroundStyle(palette.textMutedColor)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 16)
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
        }
        .buttonStyle(.plain)
        .disabled(true)
        .accessibilityLabel(CatalogStrings.text("bundle.buyLabel", locale: locale))
        .accessibilityIdentifier("productDetail.bundleBuyButton")
    }

    private func summaryRow(label: String, value: String) -> some View {
        ViewThatFits(in: .horizontal) {
            HStack(alignment: .firstTextBaseline, spacing: 12) {
                Text(label)
                    .font(.subheadline)
                    .foregroundStyle(palette.textMutedColor)
                Spacer(minLength: 8)
                Text(value)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(palette.textColor)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(label)
                    .font(.subheadline)
                    .foregroundStyle(palette.textMutedColor)
                Text(value)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(palette.textColor)
            }
        }
    }
}
