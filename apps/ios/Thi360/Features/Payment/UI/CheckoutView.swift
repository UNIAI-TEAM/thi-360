import SwiftUI

struct CheckoutView: View {
    @Bindable var viewModel: CheckoutViewModel
    let locale: AppLocale
    let supportContact: SupportContact
    let fallbackUsername: String?
    let onBackToProduct: () -> Void
    let onBackToCatalog: () -> Void
    let onSwitchAccount: () -> Void
    let onEnterExam: (String) -> Void
    let onContinueAttempt: (String) -> Void
    let onViewPendingOrder: (String?) -> Void

    @Environment(\.brandPalette) private var palette
    @Environment(\.statusPalette) private var statusPalette

    var body: some View {
        ScrollViewReader { scrollProxy in
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    headerSection

                    switch viewModel.product {
                    case .loading:
                        loadingContent
                    case let .failed(kind, _, _):
                        productErrorContent(kind: kind)
                    case let .loaded(loaded, _):
                        loadedContent(loaded: loaded, scrollProxy: scrollProxy)
                    case .empty:
                        productErrorContent(kind: .notFound)
                    }
                }
                .frame(maxWidth: 760, alignment: .leading)
                .frame(maxWidth: .infinity, alignment: .center)
                .padding(16)
            }
            .background(palette.bgPageColor.ignoresSafeArea())
        }
        .navigationTitle(PaymentStrings.text("checkout.pageTitle", locale: locale))
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await viewModel.loadInitialIfNeeded()
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("checkout.screen")
    }

    private var headerSection: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(PaymentStrings.text("checkout.heading", locale: locale))
                .font(.title2.weight(.bold))
                .foregroundStyle(palette.textColor)
                .accessibilityAddTraits(.isHeader)
                .accessibilityIdentifier("checkout.heading")

            Text(
                PaymentStrings.text(
                    viewModel.isBundle ? "bundle.checkout.subtitle" : "checkout.subtitle",
                    locale: locale
                )
            )
            .font(.subheadline)
            .foregroundStyle(palette.textMutedColor)
            .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var loadingContent: some View {
        VStack(spacing: 14) {
            PaymentSkeletonCard(lines: 3)
            PaymentSkeletonCard(lines: 4)
            PaymentSkeletonCard(lines: 3)
        }
        .accessibilityIdentifier("checkout.loading")
    }

    @ViewBuilder
    private func productErrorContent(kind: LoadFailureKind) -> some View {
        switch kind {
        case .notFound:
            PaymentCard(identifier: "checkout.notFound") {
                Text(PaymentStrings.text("checkout.notFound.title", locale: locale))
                    .font(.headline)
                    .foregroundStyle(palette.textColor)
                Text(PaymentStrings.text("checkout.notFound.description", locale: locale))
                    .font(.subheadline)
                    .foregroundStyle(palette.textSecondaryColor)
                    .fixedSize(horizontal: false, vertical: true)
                PaymentPrimaryButton(
                    title: PaymentStrings.text("checkout.backToCatalog", locale: locale),
                    identifier: "checkout.backToCatalogButton",
                    action: onBackToCatalog
                )
            }

        case .gone:
            PaymentCard(identifier: "checkout.gone") {
                Text(PaymentStrings.text("checkout.gone.title", locale: locale))
                    .font(.headline)
                    .foregroundStyle(palette.textColor)
                Text(
                    PaymentStrings.text(
                        "checkout.gone.description",
                        locale: locale,
                        replacements: ["name": viewModel.goneProduct?.name ?? viewModel.code]
                    )
                )
                .font(.subheadline)
                .foregroundStyle(palette.textSecondaryColor)
                .fixedSize(horizontal: false, vertical: true)
                PaymentPrimaryButton(
                    title: PaymentStrings.text("checkout.backToCatalog", locale: locale),
                    identifier: "checkout.backToCatalogButton",
                    action: onBackToCatalog
                )
            }

        case .conflict:
            PaymentCard(identifier: "checkout.unavailable") {
                Text(PaymentStrings.text("bundle.checkout.unavailableTitle", locale: locale))
                    .font(.headline)
                    .foregroundStyle(palette.textColor)
                Text(PaymentStrings.text("payment.bundle.detail.unavailableBody", locale: locale))
                    .font(.subheadline)
                    .foregroundStyle(palette.textSecondaryColor)
                    .fixedSize(horizontal: false, vertical: true)
                PaymentPrimaryButton(
                    title: PaymentStrings.text("bundle.checkout.unavailableActionBack", locale: locale),
                    identifier: "checkout.unavailableBackToBundleButton",
                    action: onBackToProduct
                )
                PaymentSecondaryButton(
                    title: PaymentStrings.text("checkout.backToCatalog", locale: locale),
                    identifier: "checkout.backToCatalogButton",
                    action: onBackToCatalog
                )
            }

        default:
            PaymentCard(identifier: "checkout.loadError") {
                Text(PaymentStrings.text("checkout.loadError.title", locale: locale))
                    .font(.headline)
                    .foregroundStyle(palette.textColor)
                Text(PaymentStrings.text("checkout.loadError.description", locale: locale))
                    .font(.subheadline)
                    .foregroundStyle(palette.textSecondaryColor)
                    .fixedSize(horizontal: false, vertical: true)
                HStack(spacing: 10) {
                    PaymentPrimaryButton(
                        title: AppStrings.common("common.retry", locale: locale),
                        identifier: "checkout.retryLoadButton"
                    ) {
                        Task { await viewModel.retryLoadProduct() }
                    }
                    PaymentSecondaryButton(
                        title: PaymentStrings.text("checkout.backToCatalog", locale: locale),
                        identifier: "checkout.backToCatalogButton",
                        action: onBackToCatalog
                    )
                }
            }
        }
    }

    @ViewBuilder
    private func loadedContent(
        loaded: LoadedProductDetail,
        scrollProxy: ScrollViewProxy
    ) -> some View {
        if viewModel.isBundleUnavailable {
            PaymentBanner(
                tone: .warning,
                symbolName: "exclamationmark.triangle.fill",
                title: PaymentStrings.text("bundle.checkout.unavailableTitle", locale: locale),
                message: PaymentStrings.text("bundle.checkout.unavailableBody", locale: locale),
                identifier: "checkout.bundleUnavailableBanner"
            ) {
                PaymentSecondaryButton(
                    title: PaymentStrings.text("bundle.checkout.unavailableActionBack", locale: locale),
                    fullWidth: false,
                    identifier: "checkout.bundleUnavailableBackButton",
                    action: onBackToProduct
                )
            }
        }

        if let conflict = viewModel.accountConflict {
            PaymentBanner(
                tone: .warning,
                symbolName: "person.crop.circle.badge.exclamationmark",
                title: PaymentStrings.text("checkout.account.changedTitle", locale: locale),
                message: PaymentStrings.text(
                    "checkout.account.changedDescription",
                    locale: locale,
                    replacements: [
                        "previous": conflict.previousUsername,
                        "current": conflict.currentUser.username
                    ]
                ),
                identifier: "checkout.accountConflictBanner"
            ) {
                PaymentPrimaryButton(
                    title: PaymentStrings.text(
                        "checkout.account.continueAsCurrent",
                        locale: locale,
                        replacements: ["username": conflict.currentUser.username]
                    ),
                    identifier: "checkout.accountConflictContinueButton"
                ) {
                    Task { await viewModel.acknowledgeAccountChange() }
                }
            }
        }

        if viewModel.priceChangedBanner, let quote = viewModel.activeQuote {
            let latestFormatted = PaymentFormatters.formatVnd(quote.finalPrice, locale: locale)
            let message: String = if let seen = viewModel.displaySeenPrice, seen != quote.finalPrice {
                PaymentStrings.text(
                    "checkout.priceChanged.descriptionWithSeen",
                    locale: locale,
                    replacements: [
                        "latest": latestFormatted,
                        "seen": PaymentFormatters.formatVnd(seen, locale: locale)
                    ]
                )
            } else {
                PaymentStrings.text(
                    "checkout.priceChanged.description",
                    locale: locale,
                    replacements: ["latest": latestFormatted]
                )
            }

            PaymentBanner(
                tone: .warning,
                symbolName: "tag.fill",
                title: PaymentStrings.text("checkout.priceChanged.title", locale: locale),
                message: message,
                identifier: "checkout.priceChangedBanner"
            )
        }

        accessSection(loaded: loaded, scrollProxy: scrollProxy)
        accountCard(loaded: loaded)
        productCard(loaded: loaded)

        if !viewModel.isBundleUnavailable {
            voucherCard
            summaryCard
                .id("checkout.summary.section")
        }

        refundPolicyCard

        PaymentSecondaryButton(
            title: PaymentStrings.text(
                loaded.isBundle ? "bundle.checkout.backToBundle" : "checkout.backToExam",
                locale: locale
            ),
            identifier: "checkout.backToProductButton",
            action: onBackToProduct
        )
    }

    @ViewBuilder
    private func accessSection(loaded: LoadedProductDetail, scrollProxy: ScrollViewProxy) -> some View {
        switch viewModel.access {
        case .loading, .empty:
            EmptyView()
        case .failed:
            PaymentBanner(
                tone: .danger,
                symbolName: "exclamationmark.triangle.fill",
                title: PaymentStrings.text("checkout.accessBanner.loadErrorTitle", locale: locale),
                message: PaymentStrings.text("checkout.accessBanner.loadErrorDescription", locale: locale),
                identifier: "checkout.accessBanner.error"
            ) {
                PaymentSecondaryButton(
                    title: AppStrings.common("common.retry", locale: locale),
                    fullWidth: false,
                    identifier: "checkout.accessBanner.retryButton"
                ) {
                    Task { await viewModel.retryLoadAccess() }
                }
            }

        case let .loaded(row, _):
            if let row {
                let pendingBundleItems = loaded.isBundle
                    ? (row.items ?? []).filter { $0.hasPendingFulfillment || ($0.pendingOrderCode?.isEmpty == false) }
                    : []
                let existingBundleItems = loaded.isBundle
                    ? (row.items ?? []).filter { $0.attemptsLeft > 0 }
                    : []

                if row.state == .CHECKING || row.hasPendingFulfillment || !pendingBundleItems.isEmpty {
                    let targetOrderCode = row.pendingOrderCode ?? pendingBundleItems.first?.pendingOrderCode

                    if !pendingBundleItems.isEmpty {
                        let lines = pendingBundleItems.map { item -> String in
                            if let orderCode = item.pendingOrderCode, !orderCode.isEmpty {
                                return PaymentStrings.text(
                                    "bundle.checkout.pendingExamOrder",
                                    locale: locale,
                                    replacements: ["orderCode": orderCode, "name": item.name]
                                )
                            }
                            return PaymentStrings.text(
                                "bundle.checkout.pendingExamOrderNoCode",
                                locale: locale,
                                replacements: ["name": item.name]
                            )
                        }
                        PaymentBanner(
                            tone: .warning,
                            symbolName: "clock.fill",
                            title: nil,
                            message: lines.joined(separator: "\n"),
                            identifier: "checkout.accessBanner.checking"
                        ) {
                            PaymentSecondaryButton(
                                title: PaymentStrings.text("checkout.accessBanner.viewPaymentStatus", locale: locale),
                                fullWidth: false,
                                identifier: "checkout.accessBanner.viewStatusButton"
                            ) {
                                onViewPendingOrder(targetOrderCode)
                            }
                        }
                    } else {
                        let title = PaymentStrings.text(
                            "checkout.accessBanner.checkingTitle",
                            locale: locale,
                            replacements: ["orderCode": targetOrderCode ?? "—"]
                        )
                        PaymentBanner(
                            tone: .warning,
                            symbolName: "clock.fill",
                            title: title,
                            message: PaymentStrings.text("checkout.accessBanner.checkingDescription", locale: locale),
                            identifier: "checkout.accessBanner.checking"
                        ) {
                            PaymentSecondaryButton(
                                title: PaymentStrings.text("checkout.accessBanner.viewPaymentStatus", locale: locale),
                                fullWidth: false,
                                identifier: "checkout.accessBanner.viewStatusButton"
                            ) {
                                onViewPendingOrder(targetOrderCode)
                            }
                        }
                    }
                } else if !loaded.isBundle && (row.state == .CAN_START || row.state == .IN_PROGRESS) {
                    let title = PaymentStrings.plural(
                        row.state == .IN_PROGRESS
                            ? "checkout.accessBanner.existingInProgressTitle"
                            : "checkout.accessBanner.existingTitle",
                        count: Int(row.attemptsLeft),
                        locale: locale
                    )
                    PaymentBanner(
                        tone: .info,
                        symbolName: "checkmark.seal.fill",
                        title: title,
                        message: PaymentStrings.text("checkout.subtitle", locale: locale),
                        identifier: "checkout.accessBanner.existing"
                    ) {
                        HStack(spacing: 10) {
                            if row.state == .IN_PROGRESS, let attemptId = row.activeAttemptId, !attemptId.isEmpty {
                                PaymentPrimaryButton(
                                    title: PaymentStrings.text("checkout.accessBanner.continueAttempt", locale: locale),
                                    identifier: "checkout.accessBanner.continueButton"
                                ) {
                                    onContinueAttempt(attemptId)
                                }
                            } else if let entitlementId = row.entitlementId, !entitlementId.isEmpty {
                                PaymentPrimaryButton(
                                    title: PaymentStrings.text("checkout.accessBanner.enterExam", locale: locale),
                                    identifier: "checkout.accessBanner.enterButton"
                                ) {
                                    onEnterExam(entitlementId)
                                }
                            }

                            PaymentSecondaryButton(
                                title: PaymentStrings.text("checkout.accessBanner.buyMore", locale: locale),
                                identifier: "checkout.accessBanner.buyMoreButton"
                            ) {
                                withAnimation {
                                    scrollProxy.scrollTo("checkout.summary.section", anchor: .top)
                                }
                            }
                        }
                    }
                }

                if !existingBundleItems.isEmpty {
                    let existingLines = existingBundleItems.map { item in
                        PaymentStrings.plural(
                            "payment.bundle.detail.existingEntitlement",
                            count: Int(item.attemptsLeft),
                            locale: locale,
                            replacements: ["name": item.name]
                        )
                    }
                    PaymentBanner(
                        tone: .info,
                        symbolName: "info.circle.fill",
                        title: nil,
                        message: existingLines.joined(separator: "\n"),
                        identifier: "checkout.accessBanner.bundleExisting"
                    )
                }
            }
        }
    }

    private func accountCard(loaded: LoadedProductDetail) -> some View {
        let resolvedUsername = viewModel.displayUser?.username ?? fallbackUsername ?? "—"
        let grantNotice: String = if loaded.isBundle {
            PaymentStrings.plural(
                "bundle.checkout.grantNotice",
                count: loaded.bundleItems.count,
                locale: locale,
                replacements: ["username": resolvedUsername]
            )
        } else {
            PaymentStrings.text(
                "checkout.account.grantNotice",
                locale: locale,
                replacements: ["username": resolvedUsername]
            )
        }

        return PaymentCard(identifier: "checkout.accountCard") {
            Text(PaymentStrings.text("checkout.account.title", locale: locale))
                .font(.headline)
                .foregroundStyle(palette.textColor)

            VStack(alignment: .leading, spacing: 4) {
                if let fullName = viewModel.displayUser?.fullName.trimmingCharacters(in: .whitespacesAndNewlines),
                   !fullName.isEmpty
                {
                    Text(fullName)
                        .font(.body.weight(.semibold))
                        .foregroundStyle(palette.textColor)
                        .accessibilityIdentifier("checkout.account.fullName")
                }

                Text(resolvedUsername)
                    .font(.subheadline)
                    .foregroundStyle(palette.textSecondaryColor)
                    .accessibilityIdentifier("checkout.account.username")
            }

            Text(grantNotice)
                .font(.footnote)
                .foregroundStyle(palette.textSecondaryColor)
                .fixedSize(horizontal: false, vertical: true)

            PaymentSecondaryButton(
                title: PaymentStrings.text("checkout.account.switchAccount", locale: locale),
                fullWidth: false,
                identifier: "checkout.account.switchButton",
                action: onSwitchAccount
            )
        }
    }

    private func productCard(loaded: LoadedProductDetail) -> some View {
        let detail = loaded.detail
        let accessByCode: [String: Components.Schemas.BundleAccessExamItem] = Dictionary(
            uniqueKeysWithValues: (viewModel.accessRow?.items ?? []).map { ($0.code, $0) }
        )
        let hasAnyExistingBundleAccess = (viewModel.accessRow?.items ?? []).contains { $0.attemptsLeft > 0 }

        return PaymentCard(identifier: "checkout.productCard") {
            HStack(alignment: .top, spacing: 8) {
                Text(
                    PaymentStrings.text(
                        loaded.isBundle ? "bundle.checkout.sectionTitle" : "checkout.product.sectionTitle",
                        locale: locale
                    )
                )
                .font(.headline)
                .foregroundStyle(palette.textColor)

                Spacer(minLength: 8)

                if let versionNo = viewModel.activeQuote?.versionNo {
                    PaymentStatusChip(
                        text: PaymentStrings.text(
                            "checkout.product.version",
                            locale: locale,
                            replacements: ["version": String(versionNo)]
                        ),
                        tone: .neutral,
                        identifier: "checkout.product.versionBadge"
                    )
                }
            }

            HStack(spacing: 8) {
                if loaded.isBundle {
                    PaymentStatusChip(
                        text: PaymentStrings.text("orders.bundleBadge", locale: locale),
                        tone: .info
                    )
                }
                PaymentStatusChip(
                    text: PaymentFormatters.examKindLabel(detail.examKind, locale: locale),
                    tone: .neutral
                )
            }

            Text(detail.name)
                .font(.title3.weight(.bold))
                .foregroundStyle(palette.textColor)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityIdentifier("checkout.product.name")

            if loaded.isBundle {
                bundleItemsSection(
                    loaded: loaded,
                    accessByCode: accessByCode,
                    hasExistingAccess: hasAnyExistingBundleAccess
                )
            } else {
                singleExamMetadataSection(detail: detail)
            }
        }
    }

    @ViewBuilder
    private func singleExamMetadataSection(detail: Components.Schemas.ProductDetail) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            if let subject = PaymentFormatters.localizedName(detail.scopeLabel, locale: locale) {
                Text(subject)
                    .font(.subheadline)
                    .foregroundStyle(palette.textSecondaryColor)
            }

            let stats: [String] = [
                PaymentFormatters.formatIntRange(detail.questionCount).map {
                    AppStrings.common("storefront.units.questions", locale: locale, replacements: ["value": $0])
                },
                PaymentFormatters.formatIntRange(detail.durationMinutes).map {
                    AppStrings.common("storefront.units.minutes", locale: locale, replacements: ["value": $0])
                },
                PaymentFormatters.formatDecimalRange(detail.maxPoints, locale: locale).map {
                    PaymentStrings.text("payment.detail.maxScore", locale: locale, replacements: ["score": $0])
                }
            ].compactMap { $0 }

            if !stats.isEmpty {
                Text(stats.joined(separator: " · "))
                    .font(.footnote)
                    .foregroundStyle(palette.textMutedColor)
                    .fixedSize(horizontal: false, vertical: true)
            }

            PaymentKeyValueRow(
                label: PaymentStrings.text("orders.detail.fields.attempts", locale: locale),
                value: PaymentStrings.plural(
                    "checkout.product.attemptsGranted",
                    count: Int(detail.attemptsGranted),
                    locale: locale
                ),
                emphasizeValue: true
            )

            if let resultView = PaymentFormatters.resultViewLabel(detail.resultView, locale: locale) {
                PaymentKeyValueRow(
                    label: PaymentStrings.text("orders.detail.benefits.resultView", locale: locale),
                    value: resultView
                )
            }

            Divider().overlay(palette.borderColor)
            VStack(alignment: .leading, spacing: 6) {
                let rights = detail.benefits
                rightRow(
                    label: PaymentStrings.text("payment.detail.rights.ai", locale: locale),
                    enabled: rights.aiExplanation
                )
                rightRow(
                    label: PaymentStrings.text("payment.detail.rights.strengths", locale: locale),
                    enabled: rights.strengthWeakness
                )
                rightRow(
                    label: PaymentStrings.text("payment.detail.rights.practice", locale: locale),
                    enabled: rights.practice
                )
                if let note = rights.note?.trimmingCharacters(in: .whitespacesAndNewlines), !note.isEmpty {
                    Text(note)
                        .font(.footnote)
                        .foregroundStyle(palette.textMutedColor)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    @ViewBuilder
    private func bundleItemsSection(
        loaded: LoadedProductDetail,
        accessByCode: [String: Components.Schemas.BundleAccessExamItem],
        hasExistingAccess: Bool
    ) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(
                PaymentStrings.text(
                    "bundle.checkout.itemsTitle",
                    locale: locale,
                    replacements: ["count": String(loaded.bundleItems.count)]
                )
            )
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(palette.textSecondaryColor)

            ForEach(loaded.bundleItems, id: \.code) { item in
                let meta = loaded.bundleItemMetaByCode[item.code]
                let accessItem = accessByCode[item.code]
                VStack(alignment: .leading, spacing: 6) {
                    HStack(alignment: .top, spacing: 8) {
                        Text(item.name)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(palette.textColor)
                            .fixedSize(horizontal: false, vertical: true)
                        Spacer(minLength: 6)
                        if let accessItem, accessItem.attemptsLeft > 0 {
                            PaymentStatusChip(
                                text: PaymentStrings.plural(
                                    "payment.access.attemptsLeft",
                                    count: Int(accessItem.attemptsLeft),
                                    locale: locale
                                ),
                                tone: .success,
                                identifier: "checkout.bundleItem.attemptsLeft.\(item.code)"
                            )
                        }
                    }

                    let metaParts: [String] = [
                        PaymentFormatters.localizedName(meta?.subjectName, locale: locale),
                        PaymentFormatters.formatIntRange(meta?.questionCount).map {
                            AppStrings.common("storefront.units.questions", locale: locale, replacements: ["value": $0])
                        },
                        PaymentFormatters.formatIntRange(meta?.durationMinutes).map {
                            AppStrings.common("storefront.units.minutes", locale: locale, replacements: ["value": $0])
                        },
                        PaymentFormatters.formatDecimalRange(meta?.maxPoints, locale: locale).map {
                            PaymentStrings.text("payment.detail.maxScore", locale: locale, replacements: ["score": $0])
                        }
                    ].compactMap { $0 }

                    if !metaParts.isEmpty {
                        Text(metaParts.joined(separator: " · "))
                            .font(.caption)
                            .foregroundStyle(palette.textMutedColor)
                            .fixedSize(horizontal: false, vertical: true)
                    }

                    let grantParts: [String] = [
                        PaymentStrings.plural(
                            "checkout.product.attemptsGranted",
                            count: Int(item.attemptsGranted),
                            locale: locale
                        ),
                        PaymentFormatters.resultViewLabel(item.resultView, locale: locale)
                    ].compactMap { $0 }

                    Text(grantParts.joined(separator: " · "))
                        .font(.caption.weight(.medium))
                        .foregroundStyle(palette.textSecondaryColor)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(palette.bgSubtleColor)
                )
                .accessibilityIdentifier("checkout.bundleItem.\(item.code)")
            }

            if hasExistingAccess {
                Text(PaymentStrings.text("bundle.checkout.existingNote", locale: locale))
                    .font(.footnote)
                    .foregroundStyle(statusPalette.infoColor)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("checkout.bundle.existingNote")
            }
        }
    }

    private func rightRow(label: String, enabled: Bool) -> some View {
        HStack(spacing: 8) {
            Image(systemName: enabled ? "checkmark.circle.fill" : "minus.circle")
                .font(.footnote)
                .foregroundStyle(enabled ? statusPalette.successColor : palette.textMutedColor)
                .accessibilityHidden(true)
            Text(label)
                .font(.footnote)
                .foregroundStyle(palette.textSecondaryColor)
            Spacer(minLength: 8)
            Text(
                PaymentStrings.text(
                    enabled ? "payment.detail.rights.yes" : "payment.detail.rights.no",
                    locale: locale
                )
            )
            .font(.footnote.weight(.medium))
            .foregroundStyle(enabled ? statusPalette.successColor : palette.textMutedColor)
        }
    }

    private var voucherCard: some View {
        let inputBinding = Binding<String>(
            get: { viewModel.voucher.codeInput },
            set: { viewModel.updateVoucherInput($0) }
        )

        return PaymentCard(identifier: "checkout.voucherCard") {
            Text(PaymentStrings.text("checkout.voucher.sectionTitle", locale: locale))
                .font(.headline)
                .foregroundStyle(palette.textColor)

            VStack(alignment: .leading, spacing: 8) {
                Text(PaymentStrings.text("checkout.voucher.label", locale: locale))
                    .font(.footnote.weight(.medium))
                    .foregroundStyle(palette.textSecondaryColor)

                HStack(spacing: 8) {
                    TextField(
                        PaymentStrings.text("checkout.voucher.placeholder", locale: locale),
                        text: inputBinding
                    )
                    .textFieldStyle(.plain)
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled()
                    .disabled(viewModel.isVoucherDisabled)
                    .padding(.horizontal, 12)
                    .frame(minHeight: 46)
                    .foregroundStyle(palette.textColor)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(palette.bgSubtleColor)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .strokeBorder(palette.borderStrongColor, lineWidth: 1)
                    )
                    .accessibilityIdentifier("checkout.voucher.input")

                    Button {
                        Task { await viewModel.applyVoucher() }
                    } label: {
                        HStack(spacing: 6) {
                            if viewModel.voucher.isSubmitting {
                                ProgressView()
                                    .tint(palette.onPrimaryColor)
                            }
                            Text(PaymentStrings.text("checkout.voucher.apply", locale: locale))
                                .font(.subheadline.weight(.semibold))
                        }
                        .foregroundStyle(palette.onPrimaryColor)
                        .padding(.horizontal, 16)
                        .frame(minWidth: 88, minHeight: 46)
                        .background(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(palette.primaryFillColor)
                                .opacity(viewModel.isVoucherDisabled ? 0.5 : 1.0)
                        )
                    }
                    .buttonStyle(.plain)
                    .disabled(viewModel.isVoucherDisabled)
                    .accessibilityIdentifier("checkout.voucher.applyButton")
                }
            }

            if viewModel.activeQuote?.voucher != nil {
                PaymentSecondaryButton(
                    title: PaymentStrings.text("checkout.voucher.remove", locale: locale),
                    isDisabled: viewModel.isVoucherDisabled,
                    fullWidth: false,
                    identifier: "checkout.voucher.removeButton"
                ) {
                    Task { await viewModel.removeVoucher() }
                }
            }

            if let feedback = viewModel.voucher.feedback {
                voucherFeedbackView(feedback)
            }
        }
    }

    @ViewBuilder
    private func voucherFeedbackView(_ feedback: VoucherFeedback) -> some View {
        switch feedback {
        case .empty:
            PaymentBanner(
                tone: .danger,
                symbolName: "exclamationmark.circle.fill",
                message: PaymentStrings.text("checkout.voucher.emptyInput", locale: locale),
                identifier: "checkout.voucher.feedback"
            )

        case let .error(key):
            PaymentBanner(
                tone: .danger,
                symbolName: "exclamationmark.circle.fill",
                message: PaymentStrings.text(key.messageKey(isBundle: viewModel.isBundle), locale: locale),
                identifier: "checkout.voucher.feedback"
            )

        case let .notBetter(code):
            PaymentBanner(
                tone: .info,
                symbolName: "info.circle.fill",
                message: PaymentStrings.text(
                    "checkout.voucher.notBetter",
                    locale: locale,
                    replacements: ["code": code]
                ),
                identifier: "checkout.voucher.feedback"
            )

        case let .applied(variant, code, discountVnd):
            let key: String = switch variant {
            case .applied: "checkout.voucher.applied"
            case .combined: "checkout.voucher.appliedCombined"
            case .replacedPromo: "checkout.voucher.appliedReplacedPromo"
            }
            PaymentBanner(
                tone: .success,
                symbolName: "checkmark.circle.fill",
                message: PaymentStrings.text(
                    key,
                    locale: locale,
                    replacements: [
                        "code": code,
                        "discount": PaymentFormatters.formatVnd(discountVnd, locale: locale)
                    ]
                ),
                identifier: "checkout.voucher.feedback"
            )
        }
    }

    @ViewBuilder
    private var summaryCard: some View {
        PaymentCard(identifier: "checkout.summaryCard") {
            Text(PaymentStrings.text("checkout.summary.title", locale: locale))
                .font(.headline)
                .foregroundStyle(palette.textColor)

            switch viewModel.quote {
            case .loading:
                PaymentSkeletonCard(lines: 3)

            case .failed, .empty:
                VStack(alignment: .leading, spacing: 10) {
                    Text(PaymentStrings.text("checkout.loadError.title", locale: locale))
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(palette.dangerTextColor)
                    PaymentSecondaryButton(
                        title: AppStrings.common("common.retry", locale: locale),
                        fullWidth: false,
                        identifier: "checkout.quote.retryButton"
                    ) {
                        Task { await viewModel.retryLoadQuote() }
                    }
                }

            case let .loaded(quote, _):
                VStack(alignment: .leading, spacing: 10) {
                    PaymentKeyValueRow(
                        label: PaymentStrings.text("checkout.summary.listPrice", locale: locale),
                        value: PaymentFormatters.formatVnd(quote.listPrice, locale: locale),
                        identifier: "checkout.summary.listPrice"
                    )

                    if let promotion = quote.promotion, promotion.discountVnd > 0 {
                        let promoLabel = promotion.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                            ? PaymentStrings.text("checkout.summary.promotionFallback", locale: locale)
                            : PaymentStrings.text(
                                "checkout.summary.promotion",
                                locale: locale,
                                replacements: ["name": promotion.name]
                            )
                        PaymentKeyValueRow(
                            label: promoLabel,
                            value: "-\(PaymentFormatters.formatVnd(promotion.discountVnd, locale: locale))",
                            valueColor: statusPalette.successColor,
                            identifier: "checkout.summary.promotion"
                        )
                    }

                    if let voucher = quote.voucher, voucher.discountVnd > 0 {
                        PaymentKeyValueRow(
                            label: PaymentStrings.text(
                                "checkout.summary.voucherDiscount",
                                locale: locale,
                                replacements: ["code": voucher.code]
                            ),
                            value: "-\(PaymentFormatters.formatVnd(voucher.discountVnd, locale: locale))",
                            valueColor: statusPalette.successColor,
                            identifier: "checkout.summary.voucherDiscount"
                        )
                    }

                    Divider().overlay(palette.borderColor)

                    let totalDisplay: String = if quote.finalPrice == 0 && quote.voucher != nil {
                        PaymentStrings.text("checkout.summary.freeVoucher", locale: locale)
                    } else {
                        PaymentFormatters.formatVnd(quote.finalPrice, locale: locale)
                    }

                    PaymentKeyValueRow(
                        label: PaymentStrings.text("checkout.summary.totalDue", locale: locale),
                        value: totalDisplay,
                        valueColor: palette.primaryTextColor,
                        emphasizeValue: true,
                        identifier: "checkout.summary.totalDue"
                    )

                    if case let .failed(submitError) = viewModel.submit {
                        PaymentBanner(
                            tone: .danger,
                            symbolName: "exclamationmark.circle.fill",
                            message: submitErrorMessage(submitError),
                            identifier: "checkout.submitError"
                        )
                    }

                    PaymentPrimaryButton(
                        title: ctaTitle(for: quote),
                        isLoading: viewModel.submit == .submitting,
                        isDisabled: !viewModel.isPayButtonEnabled,
                        identifier: "checkout.payButton"
                    ) {
                        Task { await viewModel.createOrder() }
                    }
                }
            }
        }
    }

    private var refundPolicyCard: some View {
        let supportLine = PaymentFormatters.formatSupportSummary(supportContact)
            ?? supportContact.pendingMessage(locale: locale)

        return PaymentCard(identifier: "checkout.refundCard") {
            Text(PaymentStrings.text("checkout.refund.title", locale: locale))
                .font(.headline)
                .foregroundStyle(palette.textColor)

            Text(
                PaymentStrings.text(
                    viewModel.isBundle ? "bundle.checkout.refundBody" : "checkout.refund.body",
                    locale: locale
                )
            )
            .font(.footnote)
            .foregroundStyle(palette.textSecondaryColor)
            .fixedSize(horizontal: false, vertical: true)

            Text("\(PaymentStrings.text("checkout.refund.supportLabel", locale: locale)) \(supportLine)")
                .font(.footnote)
                .foregroundStyle(palette.textMutedColor)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityIdentifier("checkout.refund.support")
        }
    }

    private func ctaTitle(for quote: Components.Schemas.PriceQuote) -> String {
        if quote.finalPrice == 0 {
            if quote.voucher != nil {
                return PaymentStrings.text("checkout.summary.ctaFree", locale: locale)
            }
            return PaymentStrings.text("checkout.summary.ctaFreeNoVoucher", locale: locale)
        }
        return PaymentStrings.text("checkout.summary.ctaVnpay", locale: locale)
    }

    private func submitErrorMessage(_ error: CheckoutSubmitError) -> String {
        switch error {
        case .rateLimited:
            PaymentStrings.text("checkout.summary.errorRateLimited", locale: locale)
        case .paymentAttemptsExceeded:
            PaymentStrings.text("orders.errors.PAYMENT_ATTEMPTS_EXCEEDED", locale: locale)
        case let .voucher(key):
            PaymentStrings.text(key.messageKey(isBundle: viewModel.isBundle), locale: locale)
        case .generic:
            PaymentStrings.text("checkout.summary.errorGeneric", locale: locale)
        }
    }
}
