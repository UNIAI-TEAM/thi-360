import SwiftUI

struct OrderDetailView: View {
    @Bindable var viewModel: OrderDetailViewModel
    let locale: AppLocale
    let supportContact: SupportContact
    let onGoMyExams: () -> Void
    let onOpenOrder: (String) -> Void
    let onBackToOrders: () -> Void
    let onCreateNewOrder: (String) -> Void
    let onOpenCatalog: () -> Void
    let onSwitchAccount: () -> Void

    @Environment(\.brandPalette) private var palette
    @Environment(\.statusPalette) private var statusPalette
    @Environment(\.scenePhase) private var scenePhase

    @State private var wasBackgrounded = false

    var body: some View {
        ZStack(alignment: .bottom) {
            RefreshableContainer(onRefresh: {
                await viewModel.load(isRefresh: true)
            }) {
                VStack(alignment: .leading, spacing: 16) {
                    contentForState
                }
                .frame(maxWidth: 760, alignment: .leading)
                .frame(maxWidth: .infinity, alignment: .center)
                .padding(16)
            }
            .background(palette.bgPageColor.ignoresSafeArea())

            if let toastCode = viewModel.toastOrderCode {
                PaymentToastBanner(
                    message: PaymentStrings.text(
                        "orders.cancelDialog.success",
                        locale: locale,
                        replacements: ["code": toastCode]
                    ),
                    locale: locale,
                    onDismiss: { viewModel.dismissToast() }
                )
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .zIndex(20)
            }

            if viewModel.isShowingCancelDialog, let order = viewModel.loadedOrder {
                PaymentCancelDialogOverlay(
                    orderCode: order.code,
                    locale: locale,
                    isCancelling: viewModel.isCancelling,
                    errorKey: viewModel.cancelErrorKey,
                    onKeep: { viewModel.dismissCancelDialog() },
                    onConfirm: {
                        Task { await viewModel.confirmCancel() }
                    }
                )
                .zIndex(30)
            }
        }
        .navigationTitle(PaymentStrings.text("orders.detailPageTitle", locale: locale))
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await viewModel.loadInitialIfNeeded()
        }
        .onChange(of: scenePhase) { _, newPhase in
            switch newPhase {
            case .background, .inactive:
                wasBackgrounded = true
            case .active:
                if wasBackgrounded {
                    wasBackgrounded = false
                    Task { await viewModel.reload() }
                }
            @unknown default:
                break
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("orderDetail.screen")
    }

    @ViewBuilder
    private var contentForState: some View {
        switch viewModel.state {
        case .loading:
            VStack(spacing: 14) {
                PaymentSkeletonCard(lines: 3)
                PaymentSkeletonCard(lines: 5)
                PaymentSkeletonCard(lines: 3)
            }
            .accessibilityIdentifier("orderDetail.loading")

        case .failed, .empty:
            if viewModel.isNotFound {
                PaymentCard(identifier: "orderDetail.notFound") {
                    Text(PaymentStrings.text("orders.notFound.title", locale: locale))
                        .font(.headline)
                        .foregroundStyle(palette.textColor)
                    Text(PaymentStrings.text("orders.notFound.description", locale: locale))
                        .font(.subheadline)
                        .foregroundStyle(palette.textSecondaryColor)
                        .fixedSize(horizontal: false, vertical: true)
                    PaymentPrimaryButton(
                        title: PaymentStrings.text("orders.notFound.otherAccount", locale: locale),
                        identifier: "orderDetail.notFound.switchAccountButton",
                        action: onSwitchAccount
                    )
                    PaymentSecondaryButton(
                        title: PaymentStrings.text("orders.notFound.backToOrders", locale: locale),
                        identifier: "orderDetail.notFound.backButton",
                        action: onBackToOrders
                    )
                }
            } else {
                PaymentCard(identifier: "orderDetail.error") {
                    Text(PaymentStrings.text("orders.detail.errorTitle", locale: locale))
                        .font(.headline)
                        .foregroundStyle(palette.textColor)
                    Text(PaymentStrings.text("orders.errorDescription", locale: locale))
                        .font(.subheadline)
                        .foregroundStyle(palette.textSecondaryColor)
                        .fixedSize(horizontal: false, vertical: true)
                    PaymentPrimaryButton(
                        title: AppStrings.common("common.retry", locale: locale),
                        identifier: "orderDetail.retryButton"
                    ) {
                        Task { await viewModel.load(isRefresh: false) }
                    }
                }
            }

        case let .loaded(order, stale):
            if stale {
                staleBanner
            }

            headerCard(order: order)

            if order.state == .PAID {
                paidCheckingBanner(order: order)
            }

            if let actionError = viewModel.actionError {
                actionErrorBanner(actionError)
            }

            snapshotCard(order: order)
            paymentsCard(order: order)
            historyCard(order: order)
        }
    }

    private var staleBanner: some View {
        let description: String = if let lastUpdated = viewModel.lastUpdatedAt {
            PaymentStrings.text(
                "orders.staleDescription",
                locale: locale,
                replacements: ["time": PaymentFormatters.formatTimeOnly(lastUpdated)]
            )
        } else {
            PaymentStrings.text("orders.staleDescriptionNoTime", locale: locale)
        }

        return PaymentBanner(
            tone: .warning,
            symbolName: "exclamationmark.triangle.fill",
            title: PaymentStrings.text("orders.staleTitle", locale: locale),
            message: description,
            identifier: "orderDetail.staleBanner"
        ) {
            PaymentSecondaryButton(
                title: AppStrings.common("common.retry", locale: locale),
                fullWidth: false,
                identifier: "orderDetail.staleBanner.retryButton"
            ) {
                Task { await viewModel.load(isRefresh: true) }
            }
        }
    }

    private func headerCard(order: Components.Schemas.OrderDetail) -> some View {
        PaymentCard(identifier: "orderDetail.headerCard") {
            Text(
                PaymentStrings.text(
                    "orders.detail.heading",
                    locale: locale,
                    replacements: ["code": order.code]
                )
            )
            .font(.title3.weight(.bold))
            .foregroundStyle(palette.textColor)
            .accessibilityAddTraits(.isHeader)
            .accessibilityIdentifier("orderDetail.heading")

            HStack(spacing: 8) {
                PaymentStatusChip(
                    text: PaymentFormatters.orderStateLabel(order.state.rawValue, locale: locale),
                    tone: PaymentFormatters.orderStateTone(order.state.rawValue),
                    identifier: "orderDetail.stateBadge"
                )
                PaymentStatusChip(
                    text: PaymentFormatters.formatPaymentMethodBadge(
                        paymentMethod: order.paymentMethod,
                        voucherDiscountVnd: order.voucherDiscountVnd,
                        locale: locale
                    ),
                    tone: .neutral,
                    identifier: "orderDetail.methodBadge"
                )
                if order.productType == .BUNDLE {
                    PaymentStatusChip(
                        text: PaymentStrings.text("orders.bundleBadge", locale: locale),
                        tone: .info
                    )
                }
            }

            if order.state == .PENDING_PAYMENT {
                ViewThatFits(in: .horizontal) {
                    HStack(spacing: 10) {
                        PaymentPrimaryButton(
                            title: PaymentStrings.text("orders.actions.continuePayment", locale: locale),
                            isLoading: viewModel.isPaying,
                            isDisabled: viewModel.anyActionRunning,
                            identifier: "orderDetail.payButton"
                        ) {
                            Task { await viewModel.continuePayment() }
                        }

                        PaymentSecondaryButton(
                            title: PaymentStrings.text("orders.actions.cancel", locale: locale),
                            isDisabled: viewModel.anyActionRunning,
                            isDestructive: true,
                            fullWidth: false,
                            identifier: "orderDetail.cancelButton"
                        ) {
                            viewModel.requestCancel()
                        }
                    }

                    VStack(spacing: 10) {
                        PaymentPrimaryButton(
                            title: PaymentStrings.text("orders.actions.continuePayment", locale: locale),
                            isLoading: viewModel.isPaying,
                            isDisabled: viewModel.anyActionRunning,
                            identifier: "orderDetail.payButton"
                        ) {
                            Task { await viewModel.continuePayment() }
                        }

                        PaymentSecondaryButton(
                            title: PaymentStrings.text("orders.actions.cancel", locale: locale),
                            isDisabled: viewModel.anyActionRunning,
                            isDestructive: true,
                            identifier: "orderDetail.cancelButton"
                        ) {
                            viewModel.requestCancel()
                        }
                    }
                }
            } else if order.state == .FULFILLED {
                PaymentPrimaryButton(
                    title: PaymentStrings.text("orders.actions.goMyExams", locale: locale),
                    identifier: "orderDetail.goMyExamsButton",
                    action: onGoMyExams
                )
            } else if order.state == .REPLACED,
                      let replacement = order.replacedByOrderCode,
                      !replacement.isEmpty
            {
                PaymentPrimaryButton(
                    title: PaymentStrings.text(
                        "orders.actions.viewReplacement",
                        locale: locale,
                        replacements: ["code": replacement]
                    ),
                    identifier: "orderDetail.viewReplacementButton"
                ) {
                    onOpenOrder(replacement)
                }
            }
        }
    }

    private func paidCheckingBanner(order: Components.Schemas.OrderDetail) -> some View {
        let supportLine: String = if let contact = PaymentFormatters.formatSupportSummary(supportContact) {
            PaymentStrings.text(
                "orders.paidCheckingSupport",
                locale: locale,
                replacements: ["contact": contact, "code": order.code]
            )
        } else {
            PaymentStrings.text(
                "orders.paidCheckingSupportPending",
                locale: locale,
                replacements: [
                    "code": order.code,
                    "pending": supportContact.pendingMessage(locale: locale)
                ]
            )
        }

        return PaymentBanner(
            tone: .warning,
            symbolName: "clock.fill",
            title: PaymentStrings.text("orders.paidChecking", locale: locale, replacements: ["code": order.code]),
            message: supportLine,
            identifier: "orderDetail.paidCheckingBanner"
        )
    }

    private func actionErrorBanner(_ error: OrdersActionError) -> some View {
        PaymentBanner(
            tone: .danger,
            symbolName: "exclamationmark.circle.fill",
            title: nil,
            message: PaymentStrings.text(error.code.messageKey, locale: locale),
            identifier: "orderDetail.actionErrorBanner"
        ) {
            switch error.code {
            case .orderExpired, .paymentAttemptsExceeded:
                PaymentSecondaryButton(
                    title: PaymentStrings.text("orders.actions.createNew", locale: locale),
                    fullWidth: false,
                    identifier: "orderDetail.actionError.createNewButton"
                ) {
                    onCreateNewOrder(error.productCode)
                }
            case .productDiscontinued, .productNotFound:
                PaymentSecondaryButton(
                    title: PaymentStrings.text("orders.actions.toCatalog", locale: locale),
                    fullWidth: false,
                    identifier: "orderDetail.actionError.toCatalogButton",
                    action: onOpenCatalog
                )
            default:
                EmptyView()
            }
        }
    }

    private func snapshotCard(order: Components.Schemas.OrderDetail) -> some View {
        let benefits = OrderBenefitsSnapshot.parse(from: order.benefits.additionalProperties)
        let resolvedVersion = (order.productType == .BUNDLE ? order.bundleVersionNo : order.examVersionNo)
            ?? order.examVersionNo
            ?? order.bundleVersionNo
        let versionText = resolvedVersion.map { "v\($0)" } ?? "—"

        return PaymentCard(identifier: "orderDetail.snapshotCard") {
            Text(PaymentStrings.text("orders.detail.snapshotTitle", locale: locale))
                .font(.headline)
                .foregroundStyle(palette.textColor)

            VStack(alignment: .leading, spacing: 10) {
                if let buyerDisplay = Self.formatJoinedPair(order.buyerUsername, order.buyerFullName) {
                    PaymentKeyValueRow(
                        label: PaymentStrings.text("orders.detail.fields.buyer", locale: locale),
                        value: buyerDisplay,
                        identifier: "orderDetail.field.buyer"
                    )
                }
                if let productDisplay = Self.formatJoinedPair(order.productName, order.productCode) {
                    PaymentKeyValueRow(
                        label: PaymentStrings.text("orders.detail.fields.product", locale: locale),
                        value: productDisplay,
                        emphasizeValue: true,
                        identifier: "orderDetail.field.product"
                    )
                }
                PaymentKeyValueRow(
                    label: PaymentStrings.text("orders.detail.fields.version", locale: locale),
                    value: versionText,
                    identifier: "orderDetail.field.version"
                )
                PaymentKeyValueRow(
                    label: PaymentStrings.text("orders.detail.fields.attempts", locale: locale),
                    value: PaymentStrings.plural(
                        "orders.detail.attemptsValue",
                        count: Int(order.attemptsGranted),
                        locale: locale
                    )
                )
                PaymentKeyValueRow(
                    label: PaymentStrings.text("orders.detail.fields.listPrice", locale: locale),
                    value: PaymentFormatters.formatVnd(order.listPriceVnd, locale: locale)
                )

                if order.promotionDiscountVnd > 0 {
                    PaymentKeyValueRow(
                        label: PaymentStrings.text("orders.detail.fields.promotion", locale: locale),
                        value: "-\(PaymentFormatters.formatVnd(order.promotionDiscountVnd, locale: locale))",
                        valueColor: statusPalette.successColor
                    )
                }

                if order.voucherDiscountVnd > 0 || (order.voucherCode?.isEmpty == false) {
                    let voucherParts = [
                        order.voucherCode,
                        order.voucherDiscountVnd > 0
                            ? "-\(PaymentFormatters.formatVnd(order.voucherDiscountVnd, locale: locale))"
                            : nil
                    ].compactMap { $0 }
                    PaymentKeyValueRow(
                        label: PaymentStrings.text("orders.detail.fields.voucher", locale: locale),
                        value: voucherParts.joined(separator: " · "),
                        valueColor: statusPalette.successColor
                    )
                }

                PaymentKeyValueRow(
                    label: PaymentStrings.text("orders.detail.fields.amountDue", locale: locale),
                    value: PaymentFormatters.formatOrderDetailAmount(order: order, locale: locale),
                    valueColor: palette.primaryTextColor,
                    emphasizeValue: true,
                    identifier: "orderDetail.field.amountDue"
                )

                Divider().overlay(palette.borderColor)

                PaymentKeyValueRow(
                    label: PaymentStrings.text("orders.detail.fields.createdAt", locale: locale),
                    value: PaymentFormatters.formatDateTime(order.createdAt),
                    identifier: "orderDetail.field.createdAt"
                )
                if let expiresAt = order.expiresAt {
                    PaymentKeyValueRow(
                        label: PaymentStrings.text("orders.detail.fields.expiresAt", locale: locale),
                        value: PaymentFormatters.formatDateTime(expiresAt)
                    )
                }
                if let paidAt = order.paidAt {
                    PaymentKeyValueRow(
                        label: PaymentStrings.text("orders.detail.fields.paidAt", locale: locale),
                        value: PaymentFormatters.formatDateTime(paidAt)
                    )
                }
                if let fulfilledAt = order.fulfilledAt {
                    PaymentKeyValueRow(
                        label: PaymentStrings.text("orders.detail.fields.fulfilledAt", locale: locale),
                        value: PaymentFormatters.formatDateTime(fulfilledAt)
                    )
                }

                if let replacement = order.replacedByOrderCode, !replacement.isEmpty {
                    Button {
                        onOpenOrder(replacement)
                    } label: {
                        Text(
                            PaymentStrings.text(
                                "orders.replacedBy",
                                locale: locale,
                                replacements: ["code": replacement]
                            )
                        )
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(palette.primaryTextColor)
                        .frame(minHeight: 44, alignment: .leading)
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("orderDetail.replacedByLink")
                }

                Divider().overlay(palette.borderColor)

                Text(PaymentStrings.text("orders.detail.fields.benefits", locale: locale))
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(palette.textSecondaryColor)

                benefitsView(benefits: benefits, resultView: order.resultView)

                if !order.items.isEmpty && (order.productType == .BUNDLE || order.items.count > 1) {
                    Divider().overlay(palette.borderColor)
                    Text(
                        PaymentStrings.text(
                            "orders.detail.itemsTitle",
                            locale: locale,
                            replacements: ["count": String(order.items.count)]
                        )
                    )
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(palette.textSecondaryColor)

                    ForEach(order.items, id: \.id) { item in
                        let itemBenefits = OrderBenefitsSnapshot.parse(from: item.benefits.additionalProperties)
                        VStack(alignment: .leading, spacing: 6) {
                            Text(Self.formatBundleItemTitle(examName: item.examName, examCode: item.examCode))
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(palette.textColor)
                                .fixedSize(horizontal: false, vertical: true)
                                .accessibilityIdentifier("orderDetail.item.\(item.examCode)")
                            let subtitleParts = [
                                PaymentStrings.plural(
                                    "orders.detail.attemptsValue",
                                    count: Int(item.attemptsGranted),
                                    locale: locale
                                ),
                                "v\(item.examVersionNo)",
                                PaymentFormatters.resultViewLabel(item.resultView, locale: locale)
                            ].compactMap { $0 }
                            Text(subtitleParts.joined(separator: " · "))
                                .font(.caption)
                                .foregroundStyle(palette.textMutedColor)
                            benefitsView(benefits: itemBenefits, resultView: nil)
                        }
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(palette.bgSubtleColor)
                        )
                    }
                }
            }
        }
    }

    static func formatJoinedPair(_ first: String?, _ second: String?) -> String? {
        let parts = [first, second]
            .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }

    static func formatBundleItemTitle(examName: String, examCode: String?) -> String {
        let trimmedName = examName.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedCode = examCode?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if trimmedCode.isEmpty {
            return trimmedName
        }
        if trimmedName.isEmpty {
            return trimmedCode
        }
        return "\(trimmedName) (\(trimmedCode))"
    }

    @ViewBuilder
    private func benefitsView(benefits: OrderBenefitsSnapshot, resultView: String?) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            if let label = PaymentFormatters.resultViewLabel(resultView, locale: locale) {
                PaymentKeyValueRow(
                    label: PaymentStrings.text("orders.detail.benefits.resultView", locale: locale),
                    value: label
                )
            }
            benefitRow(
                label: PaymentStrings.text("orders.detail.benefits.ai", locale: locale),
                enabled: benefits.aiExplanation
            )
            benefitRow(
                label: PaymentStrings.text("orders.detail.benefits.strengths", locale: locale),
                enabled: benefits.strengthWeakness
            )
            benefitRow(
                label: PaymentStrings.text("orders.detail.benefits.practice", locale: locale),
                enabled: benefits.practice
            )
            if let note = benefits.note {
                Text(note)
                    .font(.footnote)
                    .foregroundStyle(palette.textMutedColor)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private func benefitRow(label: String, enabled: Bool) -> some View {
        PaymentKeyValueRow(
            label: label,
            value: PaymentStrings.text(
                enabled ? "orders.detail.benefits.yes" : "orders.detail.benefits.no",
                locale: locale
            ),
            valueColor: enabled ? statusPalette.successColor : palette.textMutedColor
        )
    }

    private func paymentsCard(order: Components.Schemas.OrderDetail) -> some View {
        let sorted = order.payments.sorted { $0.attemptSeq < $1.attemptSeq }

        return PaymentCard(identifier: "orderDetail.paymentsCard") {
            Text(PaymentStrings.text("orders.detail.payments.title", locale: locale))
                .font(.headline)
                .foregroundStyle(palette.textColor)

            if sorted.isEmpty {
                Text(
                    PaymentStrings.text(
                        order.paymentMethod == .FREE
                            ? "orders.detail.payments.empty"
                            : "orders.detail.payments.emptyVnpay",
                        locale: locale
                    )
                )
                .font(.footnote)
                .foregroundStyle(palette.textMutedColor)
                .accessibilityIdentifier("orderDetail.payments.empty")
            } else {
                VStack(spacing: 10) {
                    ForEach(sorted, id: \.id) { payment in
                        VStack(alignment: .leading, spacing: 8) {
                            HStack(alignment: .center, spacing: 8) {
                                Text("#\(PaymentFormatters.formatAttemptSequence(payment.attemptSeq))")
                                    .font(.footnote.monospaced().weight(.bold))
                                    .foregroundStyle(palette.textSecondaryColor)
                                Text(payment.txnRef)
                                    .font(.footnote.monospaced())
                                    .foregroundStyle(palette.textColor)
                                    .lineLimit(1)
                                Spacer(minLength: 6)
                                PaymentStatusChip(
                                    text: PaymentFormatters.paymentStateLabel(payment.state, locale: locale),
                                    tone: PaymentFormatters.paymentStateTone(payment.state)
                                )
                            }

                            if payment.duplicate {
                                PaymentStatusChip(
                                    text: PaymentStrings.text("orders.detail.payments.duplicate", locale: locale),
                                    tone: .warning,
                                    identifier: "orderDetail.payment.\(payment.attemptSeq).duplicate"
                                )
                            }

                            PaymentKeyValueRow(
                                label: PaymentStrings.text("orders.detail.payments.bank", locale: locale),
                                value: PaymentFormatters.formatBankAndCard(
                                    bankCode: payment.bankCode,
                                    cardType: payment.cardType
                                )
                            )
                            PaymentKeyValueRow(
                                label: PaymentStrings.text("orders.detail.payments.amount", locale: locale),
                                value: PaymentFormatters.formatVnd(payment.amountVnd, locale: locale),
                                emphasizeValue: true
                            )
                            PaymentKeyValueRow(
                                label: PaymentStrings.text("orders.detail.payments.createdAt", locale: locale),
                                value: PaymentFormatters.formatDateTime(payment.createdAt)
                            )

                            if payment.state != .SUCCEEDED,
                               let responseCode = payment.responseCode?.trimmingCharacters(in: .whitespacesAndNewlines),
                               !responseCode.isEmpty
                            {
                                let reasonText = "\(VnpayReasonMapper.message(for: responseCode, locale: locale)) (\(responseCode))"
                                PaymentKeyValueRow(
                                    label: PaymentStrings.text("orders.detail.payments.reason", locale: locale),
                                    value: reasonText,
                                    valueColor: palette.dangerTextColor
                                )
                            }
                        }
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(palette.bgSubtleColor)
                        )
                        .accessibilityIdentifier("orderDetail.payment.\(payment.attemptSeq)")
                    }
                }
            }
        }
    }

    private func historyCard(order: Components.Schemas.OrderDetail) -> some View {
        PaymentCard(identifier: "orderDetail.historyCard") {
            Text(PaymentStrings.text("orders.detail.history.title", locale: locale))
                .font(.headline)
                .foregroundStyle(palette.textColor)

            if order.history.isEmpty {
                Text(PaymentStrings.text("orders.detail.history.empty", locale: locale))
                    .font(.footnote)
                    .foregroundStyle(palette.textMutedColor)
                    .accessibilityIdentifier("orderDetail.history.empty")
            } else {
                VStack(spacing: 10) {
                    ForEach(Array(order.history.enumerated()), id: \.offset) { index, entry in
                        VStack(alignment: .leading, spacing: 4) {
                            HStack(alignment: .firstTextBaseline, spacing: 8) {
                                Text(
                                    OrderHistoryMapper.transitionLabel(
                                        fromState: entry.fromState,
                                        toState: entry.toState,
                                        locale: locale
                                    )
                                )
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(palette.textColor)
                                Spacer(minLength: 6)
                                Text(PaymentFormatters.formatDateTime(entry.at))
                                    .font(.caption)
                                    .foregroundStyle(palette.textMutedColor)
                            }

                            Text(OrderHistoryMapper.actorLabel(entry.actor, locale: locale))
                                .font(.caption.weight(.medium))
                                .foregroundStyle(palette.primaryTextColor)

                            Text(OrderHistoryMapper.reasonLabel(entry.reason ?? "", locale: locale))
                                .font(.footnote)
                                .foregroundStyle(palette.textSecondaryColor)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(palette.bgSubtleColor)
                        )
                        .accessibilityIdentifier("orderDetail.history.\(index)")
                    }
                }
            }
        }
    }
}
