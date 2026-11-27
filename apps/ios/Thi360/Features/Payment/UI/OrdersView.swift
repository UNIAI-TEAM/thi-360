import SwiftUI

struct OrdersView<DetailPane: View>: View {
    @Bindable var viewModel: OrdersViewModel
    let locale: AppLocale
    var supportContact: SupportContact = .empty
    let onOpenOrderDetail: (String) -> Void
    let onGoMyExams: () -> Void
    let onCreateNewOrder: (String) -> Void
    let onOpenCatalog: () -> Void
    @ViewBuilder let detailPane: (String) -> DetailPane

    @Environment(\.brandPalette) private var palette
    @Environment(\.statusPalette) private var statusPalette
    @Environment(\.shellWidth) private var shellWidth
    @Environment(\.scenePhase) private var scenePhase

    @State private var hasAppearedOnce = false
    @State private var wasBackgrounded = false

    static func resolvedShellWidth(shellWidth: ShellWidth, availableWidth: CGFloat) -> ShellWidth {
        if shellWidth == .twoColumn && availableWidth > 0 && availableWidth < 600 {
            return .wide
        }
        return shellWidth
    }

    var body: some View {
        GeometryReader { proxy in
            let effectiveShellWidth = Self.resolvedShellWidth(
                shellWidth: shellWidth,
                availableWidth: proxy.size.width
            )

            ZStack(alignment: .bottom) {
                ListDetailLayout(
                    selection: Binding<String?>(
                        get: { effectiveShellWidth == .twoColumn ? viewModel.selectedOrderCode : nil },
                        set: { viewModel.selectedOrderCode = $0 }
                    ),
                    listWidthFraction: 0.42,
                    list: {
                        listPane(isTwoColumn: effectiveShellWidth == .twoColumn)
                    },
                    detail: { selectedCode in
                        detailPane(selectedCode)
                    },
                    emptyDetail: {
                        emptyDetailPane
                    }
                )
                .environment(\.shellWidth, effectiveShellWidth)

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

                if let target = viewModel.cancelTarget {
                    PaymentCancelDialogOverlay(
                        orderCode: target.code,
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
        }
        .background(palette.bgPageColor.ignoresSafeArea())
        .navigationTitle(PaymentStrings.text("orders.pageTitle", locale: locale))
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await viewModel.loadInitialIfNeeded()
        }
        .onAppear {
            if hasAppearedOnce {
                Task { await viewModel.reloadFirstPage() }
            } else {
                hasAppearedOnce = true
            }
        }
        .onChange(of: scenePhase) { _, newPhase in
            switch newPhase {
            case .background, .inactive:
                wasBackgrounded = true
            case .active:
                if wasBackgrounded {
                    wasBackgrounded = false
                    Task { await viewModel.reloadFirstPage() }
                }
            @unknown default:
                break
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("orders.screen")
    }

    private func listPane(isTwoColumn: Bool) -> some View {
        RefreshableContainer(onRefresh: {
            await viewModel.loadFirstPage(isRefresh: true)
        }) {
            VStack(alignment: .leading, spacing: 14) {
                VStack(alignment: .leading, spacing: 6) {
                    Text(PaymentStrings.text("orders.pageTitle", locale: locale))
                        .font(.title2.weight(.bold))
                        .foregroundStyle(palette.textColor)
                        .accessibilityAddTraits(.isHeader)
                        .accessibilityIdentifier("orders.heading")

                    if !viewModel.username.isEmpty {
                        Text(
                            PaymentStrings.text(
                                "orders.subtitle",
                                locale: locale,
                                replacements: ["username": viewModel.username]
                            )
                        )
                        .font(.footnote)
                        .foregroundStyle(palette.textMutedColor)
                        .fixedSize(horizontal: false, vertical: true)
                    }
                }

                if let actionError = viewModel.actionError {
                    actionErrorBanner(actionError)
                }

                switch viewModel.state {
                case .loading:
                    VStack(spacing: 12) {
                        PaymentSkeletonCard(lines: 3)
                        PaymentSkeletonCard(lines: 3)
                        PaymentSkeletonCard(lines: 3)
                    }
                    .accessibilityIdentifier("orders.loading")

                case .empty:
                    PaymentCard(identifier: "orders.empty") {
                        Text(PaymentStrings.text("orders.emptyTitle", locale: locale))
                            .font(.headline)
                            .foregroundStyle(palette.textColor)
                        Text(PaymentStrings.text("orders.emptyDescription", locale: locale))
                            .font(.subheadline)
                            .foregroundStyle(palette.textSecondaryColor)
                            .fixedSize(horizontal: false, vertical: true)
                        PaymentPrimaryButton(
                            title: PaymentStrings.text("orders.exploreExams", locale: locale),
                            identifier: "orders.exploreExamsButton",
                            action: onOpenCatalog
                        )
                    }

                case .failed:
                    PaymentCard(identifier: "orders.error") {
                        Text(PaymentStrings.text("orders.errorTitle", locale: locale))
                            .font(.headline)
                            .foregroundStyle(palette.textColor)
                        Text(PaymentStrings.text("orders.errorDescription", locale: locale))
                            .font(.subheadline)
                            .foregroundStyle(palette.textSecondaryColor)
                            .fixedSize(horizontal: false, vertical: true)
                        PaymentPrimaryButton(
                            title: AppStrings.common("common.retry", locale: locale),
                            identifier: "orders.retryButton"
                        ) {
                            Task { await viewModel.loadFirstPage(isRefresh: false) }
                        }
                    }

                case let .loaded(items, stale):
                    if stale {
                        staleBanner
                    }

                    LazyVStack(spacing: 12) {
                        ForEach(items, id: \.id) { order in
                            orderCard(order: order, isTwoColumn: isTwoColumn)
                                .onAppear {
                                    if order.id == items.last?.id {
                                        Task { await viewModel.loadNextPageIfNeeded() }
                                    }
                                }
                        }

                        if viewModel.isLoadingNextPage {
                            ProgressView()
                                .tint(palette.primaryTextColor)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 12)
                                .accessibilityIdentifier("orders.loadingMore")
                        }
                    }
                }
            }
            .padding(16)
        }
        .background(palette.bgPageColor)
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
            identifier: "orders.staleBanner"
        ) {
            PaymentSecondaryButton(
                title: AppStrings.common("common.retry", locale: locale),
                fullWidth: false,
                identifier: "orders.staleBanner.retryButton"
            ) {
                Task { await viewModel.loadFirstPage(isRefresh: true) }
            }
        }
    }

    private func actionErrorBanner(_ error: OrdersActionError) -> some View {
        PaymentBanner(
            tone: .danger,
            symbolName: "exclamationmark.circle.fill",
            title: nil,
            message: PaymentStrings.text(error.code.messageKey, locale: locale),
            identifier: "orders.actionErrorBanner"
        ) {
            switch error.code {
            case .orderExpired, .paymentAttemptsExceeded:
                PaymentSecondaryButton(
                    title: PaymentStrings.text("orders.actions.createNew", locale: locale),
                    fullWidth: false,
                    identifier: "orders.actionError.createNewButton"
                ) {
                    onCreateNewOrder(error.productCode)
                }
            case .productDiscontinued, .productNotFound:
                PaymentSecondaryButton(
                    title: PaymentStrings.text("orders.actions.toCatalog", locale: locale),
                    fullWidth: false,
                    identifier: "orders.actionError.toCatalogButton",
                    action: onOpenCatalog
                )
            default:
                EmptyView()
            }
        }
    }

    private func orderCard(
        order: Components.Schemas.OrderSummary,
        isTwoColumn: Bool
    ) -> some View {
        let isSelected = isTwoColumn && viewModel.selectedOrderCode == order.code
        let amountText = PaymentFormatters.formatOrderListAmount(
            paymentMethod: order.paymentMethod,
            amountDueVnd: order.amountDueVnd,
            voucherDiscountVnd: order.voucherDiscountVnd,
            locale: locale
        )

        return VStack(alignment: .leading, spacing: 10) {
            Button {
                if isTwoColumn {
                    viewModel.selectedOrderCode = order.code
                } else {
                    onOpenOrderDetail(order.code)
                }
            } label: {
                VStack(alignment: .leading, spacing: 8) {
                    HStack(alignment: .center, spacing: 8) {
                        Text(order.code)
                            .font(.footnote.monospaced().weight(.semibold))
                            .foregroundStyle(palette.textSecondaryColor)
                            .lineLimit(1)
                        Spacer(minLength: 6)
                        PaymentStatusChip(
                            text: PaymentFormatters.orderStateLabel(order.state.rawValue, locale: locale),
                            tone: PaymentFormatters.orderStateTone(order.state.rawValue),
                            identifier: "orders.row.\(order.code).state"
                        )
                    }

                    HStack(alignment: .firstTextBaseline, spacing: 8) {
                        Text(order.productName)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(palette.textColor)
                            .multilineTextAlignment(.leading)
                            .fixedSize(horizontal: false, vertical: true)
                        if order.productType == .BUNDLE {
                            PaymentStatusChip(
                                text: PaymentStrings.text("orders.bundleBadge", locale: locale),
                                tone: .info
                            )
                        }
                    }

                    ViewThatFits(in: .horizontal) {
                        HStack {
                            Text(PaymentFormatters.formatDateTime(order.createdAt))
                                .font(.caption)
                                .foregroundStyle(palette.textMutedColor)
                            Spacer(minLength: 8)
                            Text(amountText)
                                .font(.subheadline.weight(.bold))
                                .foregroundStyle(palette.textColor)
                        }

                        VStack(alignment: .leading, spacing: 4) {
                            Text(PaymentFormatters.formatDateTime(order.createdAt))
                                .font(.caption)
                                .foregroundStyle(palette.textMutedColor)
                            Text(amountText)
                                .font(.subheadline.weight(.bold))
                                .foregroundStyle(palette.textColor)
                        }
                    }

                    if order.state == .PENDING_PAYMENT, let expiresAt = order.expiresAt {
                        Text(
                            PaymentStrings.text(
                                "orders.pendingHoldUntil",
                                locale: locale,
                                replacements: ["time": PaymentFormatters.formatTimeOnly(expiresAt)]
                            )
                        )
                        .font(.caption)
                        .foregroundStyle(statusPalette.warningColor)
                    }

                    if order.state == .PAID {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(PaymentStrings.text("orders.stateSubtext.PAID", locale: locale))
                                .font(.caption.weight(.medium))
                                .foregroundStyle(statusPalette.warningColor)
                            Text(paidSupportLine(orderCode: order.code))
                                .font(.caption)
                                .foregroundStyle(palette.textMutedColor)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("orders.row.\(order.code).open")

            if order.state == .PENDING_PAYMENT {
                ViewThatFits(in: .horizontal) {
                    HStack(spacing: 8) {
                        PaymentPrimaryButton(
                            title: PaymentStrings.text("orders.actions.continuePayment", locale: locale),
                            isLoading: viewModel.payingOrderCode == order.code,
                            isDisabled: viewModel.anyActionRunning,
                            identifier: "orders.row.\(order.code).payButton"
                        ) {
                            Task { await viewModel.continuePayment(for: order) }
                        }

                        PaymentSecondaryButton(
                            title: PaymentStrings.text("orders.actions.cancel", locale: locale),
                            isDisabled: viewModel.anyActionRunning,
                            isDestructive: true,
                            fullWidth: false,
                            identifier: "orders.row.\(order.code).cancelButton"
                        ) {
                            viewModel.requestCancel(for: order)
                        }
                    }

                    VStack(spacing: 8) {
                        PaymentPrimaryButton(
                            title: PaymentStrings.text("orders.actions.continuePayment", locale: locale),
                            isLoading: viewModel.payingOrderCode == order.code,
                            isDisabled: viewModel.anyActionRunning,
                            identifier: "orders.row.\(order.code).payButton"
                        ) {
                            Task { await viewModel.continuePayment(for: order) }
                        }

                        PaymentSecondaryButton(
                            title: PaymentStrings.text("orders.actions.cancel", locale: locale),
                            isDisabled: viewModel.anyActionRunning,
                            isDestructive: true,
                            identifier: "orders.row.\(order.code).cancelButton"
                        ) {
                            viewModel.requestCancel(for: order)
                        }
                    }
                }
            } else if order.state == .FULFILLED {
                PaymentSecondaryButton(
                    title: PaymentStrings.text("orders.actions.goMyExams", locale: locale),
                    fullWidth: false,
                    identifier: "orders.row.\(order.code).goMyExamsButton",
                    action: onGoMyExams
                )
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(isSelected ? palette.primaryContainerColor : palette.bgSurfaceColor)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .strokeBorder(
                    isSelected ? palette.primaryFillColor : palette.borderColor,
                    lineWidth: isSelected ? 1.5 : 1
                )
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("orders.row.\(order.code)")
    }

    private func paidSupportLine(orderCode: String) -> String {
        if let contact = PaymentFormatters.formatSupportSummary(supportContact) {
            return PaymentStrings.text(
                "orders.paidCheckingSupport",
                locale: locale,
                replacements: ["contact": contact, "code": orderCode]
            )
        }
        return PaymentStrings.text(
            "orders.paidCheckingSupportPending",
            locale: locale,
            replacements: [
                "code": orderCode,
                "pending": supportContact.pendingMessage(locale: locale)
            ]
        )
    }

    private var emptyDetailPane: some View {
        VStack(spacing: 12) {
            Image(systemName: "doc.text.magnifyingglass")
                .font(.system(size: 36))
                .foregroundStyle(palette.textMutedColor)
                .accessibilityHidden(true)
            Text(PaymentStrings.text("orders.detail.selectPrompt", locale: locale))
                .font(.subheadline)
                .foregroundStyle(palette.textMutedColor)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(palette.bgPageColor)
        .accessibilityIdentifier("orders.detail.emptyPrompt")
    }
}
