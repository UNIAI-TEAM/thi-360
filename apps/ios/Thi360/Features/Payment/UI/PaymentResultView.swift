import SwiftUI

struct PaymentResultView: View {
    @Bindable var viewModel: PaymentResultViewModel
    let locale: AppLocale
    let supportContact: SupportContact
    let onGoMyExams: () -> Void
    let onViewOrderDetail: (String) -> Void
    let onViewOrders: () -> Void
    let onBuyAgain: (String) -> Void
    let onOpenCatalog: () -> Void
    let onSwitchAccount: () -> Void

    @Environment(\.brandPalette) private var palette
    @Environment(\.statusPalette) private var statusPalette
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                contentForState
            }
            .frame(maxWidth: 680, alignment: .leading)
            .frame(maxWidth: .infinity, alignment: .center)
            .padding(16)
        }
        .background(palette.bgPageColor.ignoresSafeArea())
        .navigationTitle(PaymentStrings.text("checkout.pageTitle", locale: locale))
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await viewModel.startIfNeeded()
        }
        .onChange(of: scenePhase) { _, newPhase in
            viewModel.onScenePhaseChanged(newPhase)
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("paymentResult.screen")
    }

    @ViewBuilder
    private var contentForState: some View {
        switch viewModel.state {
        case .verifying, .loading:
            VStack(spacing: 14) {
                PaymentSkeletonCard(lines: 3)
                PaymentSkeletonCard(lines: 4)
            }
            .accessibilityIdentifier("paymentResult.loading")

        case .invalidSignature:
            statusCard(
                symbolName: "xmark.shield.fill",
                tone: .danger,
                heading: PaymentStrings.text("checkout.result.invalidSignatureHeading", locale: locale),
                badgeText: nil,
                bodyText: PaymentStrings.text("checkout.result.invalidSignatureBody", locale: locale),
                identifier: "paymentResult.invalidSignature"
            ) {
                PaymentPrimaryButton(
                    title: PaymentStrings.text("checkout.result.viewOrders", locale: locale),
                    identifier: "paymentResult.viewOrdersButton",
                    action: onViewOrders
                )
                PaymentSecondaryButton(
                    title: PaymentStrings.text("checkout.result.viewMyExams", locale: locale),
                    identifier: "paymentResult.goMyExamsButton",
                    action: onGoMyExams
                )
            }

        case .verifyNotFound:
            statusCard(
                symbolName: "questionmark.circle.fill",
                tone: .danger,
                heading: PaymentStrings.text("checkout.result.missingOrderHeading", locale: locale),
                badgeText: nil,
                bodyText: PaymentStrings.text("checkout.result.verifyNotFoundBody", locale: locale),
                identifier: "paymentResult.verifyNotFound"
            ) {
                PaymentPrimaryButton(
                    title: PaymentStrings.text("checkout.result.viewOrders", locale: locale),
                    identifier: "paymentResult.viewOrdersButton",
                    action: onViewOrders
                )
                PaymentSecondaryButton(
                    title: PaymentStrings.text("checkout.result.viewMyExams", locale: locale),
                    identifier: "paymentResult.goMyExamsButton",
                    action: onGoMyExams
                )
            }

        case let .notOwned(orderCode, username):
            statusCard(
                symbolName: "person.crop.circle.badge.exclamationmark",
                tone: .warning,
                heading: PaymentStrings.text("checkout.result.notOwnedHeading", locale: locale),
                badgeText: nil,
                bodyText: PaymentStrings.text(
                    "checkout.result.notOwnedBody",
                    locale: locale,
                    replacements: [
                        "code": orderCode,
                        "username": username ?? "—"
                    ]
                ),
                identifier: "paymentResult.notOwned"
            ) {
                PaymentPrimaryButton(
                    title: PaymentStrings.text("checkout.account.switchAccount", locale: locale),
                    identifier: "paymentResult.switchAccountButton",
                    action: onSwitchAccount
                )
                PaymentSecondaryButton(
                    title: PaymentStrings.text("checkout.result.viewOrders", locale: locale),
                    identifier: "paymentResult.viewOrdersButton",
                    action: onViewOrders
                )
            }

        case .loadError:
            statusCard(
                symbolName: "exclamationmark.triangle.fill",
                tone: .danger,
                heading: PaymentStrings.text("checkout.result.loadErrorHeading", locale: locale),
                badgeText: nil,
                bodyText: PaymentStrings.text("checkout.result.loadErrorBody", locale: locale),
                identifier: "paymentResult.loadError"
            ) {
                PaymentPrimaryButton(
                    title: AppStrings.common("common.retry", locale: locale),
                    identifier: "paymentResult.retryLoadButton"
                ) {
                    Task { await viewModel.retryLoad() }
                }
                PaymentSecondaryButton(
                    title: PaymentStrings.text("checkout.result.viewOrders", locale: locale),
                    identifier: "paymentResult.viewOrdersButton",
                    action: onViewOrders
                )
            }

        case let .fulfilled(order):
            let bodyKey = (order.paymentMethod == .FREE && order.voucherDiscountVnd > 0)
                ? "checkout.result.successFreeBody"
                : "checkout.result.successBody"
            statusCard(
                symbolName: "checkmark.circle.fill",
                tone: .success,
                heading: PaymentStrings.text("checkout.result.successHeading", locale: locale),
                badgeText: PaymentFormatters.orderStateLabel(order.state.rawValue, locale: locale),
                bodyText: PaymentStrings.text(bodyKey, locale: locale, replacements: ["code": order.code]),
                identifier: "paymentResult.fulfilled"
            ) {
                orderSummaryBlock(order: order, includeFulfilledAt: true)

                PaymentPrimaryButton(
                    title: PaymentStrings.text("checkout.result.viewMyExams", locale: locale),
                    identifier: "paymentResult.goMyExamsButton",
                    action: onGoMyExams
                )
                PaymentSecondaryButton(
                    title: PaymentStrings.text("checkout.result.viewOrder", locale: locale),
                    identifier: "paymentResult.viewOrderButton"
                ) {
                    onViewOrderDetail(order.code)
                }
            }

        case let .failed(order, reasonCode, txnRef, retryPayError):
            let reasonMessage = VnpayReasonMapper.message(for: reasonCode, locale: locale)
            statusCard(
                symbolName: "xmark.circle.fill",
                tone: .danger,
                heading: PaymentStrings.text("checkout.result.failedHeading", locale: locale),
                badgeText: PaymentFormatters.orderStateLabel(order.state.rawValue, locale: locale),
                bodyText: PaymentStrings.text(
                    "checkout.result.failedBody",
                    locale: locale,
                    replacements: ["code": order.code]
                ),
                identifier: "paymentResult.failed"
            ) {
                PaymentBanner(
                    tone: .danger,
                    symbolName: "info.circle.fill",
                    message: reasonMessage,
                    identifier: "paymentResult.failedReason"
                )

                if let txnRef, !txnRef.isEmpty {
                    let amountText = PaymentFormatters.formatOrderDetailAmount(order: order, locale: locale)
                    Text("\(PaymentStrings.text("checkout.result.txnRefLabel", locale: locale)) \(txnRef) · \(amountText)")
                        .font(.footnote.monospaced())
                        .foregroundStyle(palette.textSecondaryColor)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityIdentifier("paymentResult.txnRef")
                }

                if let retryPayError {
                    retryErrorBanner(errorKey: retryPayError, productCode: order.productCode)
                }

                PaymentPrimaryButton(
                    title: PaymentStrings.text("checkout.result.retryPay", locale: locale),
                    isLoading: viewModel.isRetryingPay,
                    isDisabled: viewModel.isRetryingPay,
                    identifier: "paymentResult.retryPayButton"
                ) {
                    Task { await viewModel.retryPay() }
                }

                PaymentSecondaryButton(
                    title: PaymentStrings.text("checkout.result.viewOrder", locale: locale),
                    identifier: "paymentResult.viewOrderButton"
                ) {
                    onViewOrderDetail(order.code)
                }
            }

        case let .checking(order, timedOut, pollError):
            let heading = PaymentStrings.text(
                timedOut ? "checkout.result.checkingTimeoutHeading" : "checkout.result.checkingHeading",
                locale: locale
            )
            let body = PaymentStrings.text(
                timedOut ? "checkout.result.checkingTimeoutBody" : "checkout.result.checkingPending",
                locale: locale,
                replacements: ["code": order.code]
            )
            statusCard(
                symbolName: "clock.fill",
                tone: .warning,
                heading: heading,
                badgeText: PaymentStrings.text("checkout.result.checkingBadge", locale: locale),
                bodyText: body,
                identifier: "paymentResult.checking"
            ) {
                if timedOut {
                    Text(checkingSupportLine(orderCode: order.code))
                        .font(.footnote)
                        .foregroundStyle(palette.textMutedColor)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityIdentifier("paymentResult.checkingTimeoutSupport")
                }

                if pollError {
                    PaymentBanner(
                        tone: .warning,
                        symbolName: "wifi.exclamationmark",
                        message: PaymentStrings.text(
                            timedOut ? "checkout.result.pollErrorTimeoutNotice" : "checkout.result.pollErrorNotice",
                            locale: locale
                        ),
                        identifier: "paymentResult.pollErrorNotice"
                    )
                }

                orderSummaryBlock(order: order, includeFulfilledAt: false)

                PaymentPrimaryButton(
                    title: PaymentStrings.text("checkout.result.checkAgain", locale: locale),
                    isLoading: viewModel.isCheckingAgain,
                    isDisabled: viewModel.isCheckingAgain,
                    identifier: "paymentResult.checkAgainButton"
                ) {
                    Task { await viewModel.checkAgainNow() }
                }

                PaymentSecondaryButton(
                    title: PaymentStrings.text("checkout.result.viewOrder", locale: locale),
                    identifier: "paymentResult.viewOrderButton"
                ) {
                    onViewOrderDetail(order.code)
                }

                PaymentSecondaryButton(
                    title: PaymentStrings.text("checkout.result.viewMyExams", locale: locale),
                    identifier: "paymentResult.goMyExamsButton",
                    action: onGoMyExams
                )
            }

        case let .noResultYet(order, timedOut, pollError, retryPayError):
            let body: String = if let expiresAt = order.expiresAt {
                PaymentStrings.text(
                    "checkout.result.noResultBody",
                    locale: locale,
                    replacements: [
                        "code": order.code,
                        "time": PaymentFormatters.formatTimeOnly(expiresAt)
                    ]
                )
            } else {
                PaymentStrings.text(
                    "checkout.result.noResultBodyNoExpiry",
                    locale: locale,
                    replacements: ["code": order.code]
                )
            }
            statusCard(
                symbolName: "hourglass",
                tone: .warning,
                heading: PaymentStrings.text("checkout.result.noResultHeading", locale: locale),
                badgeText: PaymentFormatters.orderStateLabel(order.state.rawValue, locale: locale),
                bodyText: body,
                identifier: "paymentResult.noResultYet"
            ) {
                if pollError {
                    PaymentBanner(
                        tone: .warning,
                        symbolName: "wifi.exclamationmark",
                        message: PaymentStrings.text(
                            timedOut ? "checkout.result.pollErrorTimeoutNotice" : "checkout.result.pollErrorNotice",
                            locale: locale
                        ),
                        identifier: "paymentResult.pollErrorNotice"
                    )
                }

                if let retryPayError {
                    retryErrorBanner(errorKey: retryPayError, productCode: order.productCode)
                }

                orderSummaryBlock(order: order, includeFulfilledAt: false)

                PaymentPrimaryButton(
                    title: PaymentStrings.text("orders.actions.continuePayment", locale: locale),
                    isLoading: viewModel.isRetryingPay,
                    isDisabled: viewModel.isRetryingPay,
                    identifier: "paymentResult.continuePaymentButton"
                ) {
                    Task { await viewModel.retryPay() }
                }

                PaymentSecondaryButton(
                    title: PaymentStrings.text("checkout.result.checkAgain", locale: locale),
                    isDisabled: viewModel.isCheckingAgain,
                    identifier: "paymentResult.checkAgainButton"
                ) {
                    Task { await viewModel.checkAgainNow() }
                }

                PaymentSecondaryButton(
                    title: PaymentStrings.text("checkout.result.viewOrder", locale: locale),
                    identifier: "paymentResult.viewOrderButton"
                ) {
                    onViewOrderDetail(order.code)
                }
            }

        case let .closed(order):
            statusCard(
                symbolName: "doc.text.fill",
                tone: PaymentFormatters.orderStateTone(order.state.rawValue),
                heading: PaymentStrings.text("checkout.result.closedHeading", locale: locale),
                badgeText: PaymentFormatters.orderStateLabel(order.state.rawValue, locale: locale),
                bodyText: PaymentStrings.text(
                    "checkout.result.closedBody",
                    locale: locale,
                    replacements: ["code": order.code]
                ),
                identifier: "paymentResult.closed"
            ) {
                orderSummaryBlock(order: order, includeFulfilledAt: false)

                if order.state == .REPLACED,
                   let replacement = order.replacedByOrderCode,
                   !replacement.isEmpty
                {
                    PaymentPrimaryButton(
                        title: PaymentStrings.text(
                            "orders.actions.viewReplacement",
                            locale: locale,
                            replacements: ["code": replacement]
                        ),
                        identifier: "paymentResult.viewReplacementButton"
                    ) {
                        onViewOrderDetail(replacement)
                    }
                } else {
                    PaymentPrimaryButton(
                        title: PaymentStrings.text("checkout.result.buyAgain", locale: locale),
                        identifier: "paymentResult.buyAgainButton"
                    ) {
                        onBuyAgain(order.productCode)
                    }
                }

                PaymentSecondaryButton(
                    title: PaymentStrings.text("checkout.result.viewOrders", locale: locale),
                    identifier: "paymentResult.viewOrdersButton",
                    action: onViewOrders
                )
            }
        }
    }

    private func statusCard<Content: View>(
        symbolName: String,
        tone: PaymentStatusTone,
        heading: String,
        badgeText: String?,
        bodyText: String,
        identifier: String,
        @ViewBuilder content: @escaping () -> Content
    ) -> some View {
        PaymentCard(identifier: identifier) {
            HStack(alignment: .center, spacing: 10) {
                Image(systemName: symbolName)
                    .font(.title2.weight(.semibold))
                    .foregroundStyle(iconColor(for: tone))
                    .accessibilityHidden(true)

                if let badgeText {
                    PaymentStatusChip(text: badgeText, tone: tone)
                }
            }

            Text(heading)
                .font(.title3.weight(.bold))
                .foregroundStyle(palette.textColor)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
                .accessibilityIdentifier("paymentResult.heading")

            Text(bodyText)
                .font(.subheadline)
                .foregroundStyle(palette.textSecondaryColor)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityIdentifier("paymentResult.body")

            content()
        }
    }

    private func orderSummaryBlock(order: Components.Schemas.OrderDetail, includeFulfilledAt: Bool) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Divider().overlay(palette.borderColor)
            PaymentKeyValueRow(
                label: PaymentStrings.text("orders.detail.fields.product", locale: locale),
                value: order.productName,
                emphasizeValue: true
            )
            PaymentKeyValueRow(
                label: PaymentStrings.text("orders.detail.fields.amountDue", locale: locale),
                value: PaymentFormatters.formatOrderDetailAmount(order: order, locale: locale),
                emphasizeValue: true
            )
            if includeFulfilledAt, let fulfilledAt = order.fulfilledAt {
                PaymentKeyValueRow(
                    label: PaymentStrings.text("orders.detail.fields.fulfilledAt", locale: locale),
                    value: PaymentFormatters.formatDateTime(fulfilledAt),
                    identifier: "paymentResult.fulfilledAt"
                )
            }
        }
    }

    @ViewBuilder
    private func retryErrorBanner(errorKey: OrderActionErrorKey, productCode: String) -> some View {
        PaymentBanner(
            tone: .danger,
            symbolName: "exclamationmark.circle.fill",
            title: nil,
            message: PaymentStrings.text(errorKey.messageKey, locale: locale),
            identifier: "paymentResult.retryPayError"
        ) {
            switch errorKey {
            case .productDiscontinued, .productNotFound:
                PaymentSecondaryButton(
                    title: PaymentStrings.text("orders.actions.toCatalog", locale: locale),
                    fullWidth: false,
                    identifier: "paymentResult.retryPayError.catalogButton",
                    action: onOpenCatalog
                )
            default:
                EmptyView()
            }
        }
    }

    private func checkingSupportLine(orderCode: String) -> String {
        if let contact = PaymentFormatters.formatSupportSummary(supportContact) {
            return PaymentStrings.text(
                "checkout.result.checkingTimeoutSupport",
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

    private func iconColor(for tone: PaymentStatusTone) -> Color {
        switch tone {
        case .success: statusPalette.successColor
        case .warning: statusPalette.warningColor
        case .info: statusPalette.infoColor
        case .danger: palette.dangerTextColor
        case .neutral: palette.textSecondaryColor
        }
    }
}
