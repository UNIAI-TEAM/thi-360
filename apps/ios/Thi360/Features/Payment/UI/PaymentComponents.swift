import SwiftUI

struct PaymentStatusChip: View {
    let text: String
    let tone: PaymentStatusTone
    var identifier: String? = nil

    @Environment(\.brandPalette) private var palette
    @Environment(\.statusPalette) private var statusPalette

    private var foreground: Color {
        switch tone {
        case .success: statusPalette.successColor
        case .warning: statusPalette.warningColor
        case .info: statusPalette.infoColor
        case .danger: palette.dangerTextColor
        case .neutral: palette.textSecondaryColor
        }
    }

    private var background: Color {
        switch tone {
        case .success: statusPalette.successBgColor
        case .warning: statusPalette.warningBgColor
        case .info: statusPalette.infoBgColor
        case .danger: palette.dangerBgColor
        case .neutral: palette.bgSubtleColor
        }
    }

    var body: some View {
        Text(text)
            .font(.caption.weight(.semibold))
            .foregroundStyle(foreground)
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(
                Capsule(style: .continuous)
                    .fill(background)
            )
            .overlay(
                Capsule(style: .continuous)
                    .strokeBorder(foreground.opacity(0.22), lineWidth: 1)
            )
            .fixedSize(horizontal: false, vertical: true)
            .modifier(OptionalAccessibilityIdentifier(identifier: identifier))
    }
}

struct PaymentCard<Content: View>: View {
    var identifier: String? = nil
    @ViewBuilder let content: () -> Content

    @Environment(\.brandPalette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            content()
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
        .accessibilityElement(children: .contain)
        .modifier(OptionalAccessibilityIdentifier(identifier: identifier))
    }
}

struct PaymentBanner<Actions: View>: View {
    let tone: PaymentStatusTone
    let symbolName: String
    let title: String?
    let message: String
    var identifier: String? = nil
    @ViewBuilder let actions: () -> Actions

    @Environment(\.brandPalette) private var palette
    @Environment(\.statusPalette) private var statusPalette

    private var foreground: Color {
        switch tone {
        case .success: statusPalette.successColor
        case .warning: statusPalette.warningColor
        case .info: statusPalette.infoColor
        case .danger: palette.dangerTextColor
        case .neutral: palette.textSecondaryColor
        }
    }

    private var background: Color {
        switch tone {
        case .success: statusPalette.successBgColor
        case .warning: statusPalette.warningBgColor
        case .info: statusPalette.infoBgColor
        case .danger: palette.dangerBgColor
        case .neutral: palette.bgSubtleColor
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 10) {
                Image(systemName: symbolName)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(foreground)
                    .padding(.top, 2)
                    .accessibilityHidden(true)

                VStack(alignment: .leading, spacing: 4) {
                    if let title, !title.isEmpty {
                        Text(title)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(foreground)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Text(message)
                        .font(.footnote)
                        .foregroundStyle(palette.textColor)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }

            actions()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(background)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .strokeBorder(foreground.opacity(0.28), lineWidth: 1)
        )
        .accessibilityElement(children: .contain)
        .modifier(OptionalAccessibilityIdentifier(identifier: identifier))
    }
}

extension PaymentBanner where Actions == EmptyView {
    init(
        tone: PaymentStatusTone,
        symbolName: String,
        title: String? = nil,
        message: String,
        identifier: String? = nil
    ) {
        self.init(
            tone: tone,
            symbolName: symbolName,
            title: title,
            message: message,
            identifier: identifier,
            actions: { EmptyView() }
        )
    }
}

struct PaymentPrimaryButton: View {
    let title: String
    var isLoading: Bool = false
    var isDisabled: Bool = false
    var identifier: String? = nil
    let action: () -> Void

    @Environment(\.brandPalette) private var palette

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if isLoading {
                    ProgressView()
                        .tint(palette.onPrimaryColor)
                }
                Text(title)
                    .font(.subheadline.weight(.semibold))
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .foregroundStyle(palette.onPrimaryColor)
            .frame(maxWidth: .infinity, minHeight: 46)
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(palette.primaryFillColor)
                    .opacity(isDisabled || isLoading ? 0.5 : 1.0)
            )
            .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(isDisabled || isLoading)
        .modifier(OptionalAccessibilityIdentifier(identifier: identifier))
    }
}

struct PaymentSecondaryButton: View {
    let title: String
    var isDisabled: Bool = false
    var isDestructive: Bool = false
    var fullWidth: Bool = true
    var identifier: String? = nil
    let action: () -> Void

    @Environment(\.brandPalette) private var palette

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.subheadline.weight(.semibold))
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .foregroundStyle(isDestructive ? palette.dangerTextColor : palette.textColor)
                .frame(maxWidth: fullWidth ? .infinity : nil, minHeight: 44)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(isDestructive ? palette.dangerBgColor : palette.bgSurfaceColor)
                        .opacity(isDisabled ? 0.5 : 1.0)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .strokeBorder(
                            isDestructive ? palette.dangerTextColor.opacity(0.35) : palette.borderStrongColor,
                            lineWidth: 1
                        )
                )
                .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(isDisabled)
        .modifier(OptionalAccessibilityIdentifier(identifier: identifier))
    }
}

struct PaymentDestructiveButton: View {
    let title: String
    var isLoading: Bool = false
    var isDisabled: Bool = false
    var identifier: String? = nil
    let action: () -> Void

    @Environment(\.statusPalette) private var statusPalette

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if isLoading {
                    ProgressView()
                        .tint(statusPalette.onDangerColor)
                }
                Text(title)
                    .font(.subheadline.weight(.semibold))
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .foregroundStyle(statusPalette.onDangerColor)
            .frame(maxWidth: .infinity, minHeight: 46)
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(statusPalette.dangerSolidColor)
                    .opacity(isDisabled || isLoading ? 0.5 : 1.0)
            )
            .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(isDisabled || isLoading)
        .modifier(OptionalAccessibilityIdentifier(identifier: identifier))
    }
}

struct PaymentKeyValueRow: View {
    let label: String
    let value: String
    var valueColor: Color? = nil
    var emphasizeValue: Bool = false
    var identifier: String? = nil

    @Environment(\.brandPalette) private var palette

    var body: some View {
        ViewThatFits(in: .horizontal) {
            HStack(alignment: .firstTextBaseline, spacing: 12) {
                Text(label)
                    .font(.subheadline)
                    .foregroundStyle(palette.textMutedColor)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 8)
                Text(value)
                    .font(emphasizeValue ? .subheadline.weight(.semibold) : .subheadline)
                    .foregroundStyle(valueColor ?? palette.textColor)
                    .multilineTextAlignment(.trailing)
                    .fixedSize(horizontal: false, vertical: true)
            }

            VStack(alignment: .leading, spacing: 4) {
                Text(label)
                    .font(.footnote)
                    .foregroundStyle(palette.textMutedColor)
                    .fixedSize(horizontal: false, vertical: true)
                Text(value)
                    .font(emphasizeValue ? .subheadline.weight(.semibold) : .subheadline)
                    .foregroundStyle(valueColor ?? palette.textColor)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .modifier(OptionalAccessibilityIdentifier(identifier: identifier))
    }
}

struct PaymentSkeletonCard: View {
    var lines: Int = 3

    @Environment(\.brandPalette) private var palette

    var body: some View {
        PaymentCard {
            ForEach(0..<lines, id: \.self) { index in
                RoundedRectangle(cornerRadius: 6, style: .continuous)
                    .fill(palette.bgSubtleColor)
                    .frame(height: index == 0 ? 20 : 14)
                    .frame(maxWidth: index == lines - 1 ? 180 : .infinity, alignment: .leading)
            }
        }
        .accessibilityHidden(true)
    }
}

struct PaymentCancelDialogOverlay: View {
    let orderCode: String
    let locale: AppLocale
    let isCancelling: Bool
    let errorKey: OrderActionErrorKey?
    let onKeep: () -> Void
    let onConfirm: () -> Void

    @Environment(\.brandPalette) private var palette

    var body: some View {
        ZStack {
            Color.black.opacity(0.45)
                .ignoresSafeArea()
                .onTapGesture {
                    if !isCancelling {
                        onKeep()
                    }
                }

            VStack(alignment: .leading, spacing: 14) {
                Text(
                    PaymentStrings.text(
                        "orders.cancelDialog.title",
                        locale: locale,
                        replacements: ["code": orderCode]
                    )
                )
                .font(.headline)
                .foregroundStyle(palette.textColor)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
                .accessibilityIdentifier("orders.cancelDialog.title")

                Text(
                    PaymentStrings.text(
                        "orders.cancelDialog.description",
                        locale: locale,
                        replacements: ["code": orderCode]
                    )
                )
                .font(.subheadline)
                .foregroundStyle(palette.textSecondaryColor)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityIdentifier("orders.cancelDialog.description")

                if let errorKey {
                    PaymentBanner(
                        tone: .danger,
                        symbolName: "exclamationmark.circle.fill",
                        message: PaymentStrings.text(errorKey.messageKey, locale: locale),
                        identifier: "orders.cancelDialog.error"
                    )
                }

                ViewThatFits(in: .horizontal) {
                    HStack(spacing: 10) {
                        PaymentSecondaryButton(
                            title: PaymentStrings.text("orders.cancelDialog.keep", locale: locale),
                            isDisabled: isCancelling,
                            identifier: "orders.cancelDialog.keepButton",
                            action: onKeep
                        )
                        PaymentDestructiveButton(
                            title: PaymentStrings.text("orders.cancelDialog.confirm", locale: locale),
                            isLoading: isCancelling,
                            isDisabled: isCancelling,
                            identifier: "orders.cancelDialog.confirmButton",
                            action: onConfirm
                        )
                    }

                    VStack(spacing: 10) {
                        PaymentDestructiveButton(
                            title: PaymentStrings.text("orders.cancelDialog.confirm", locale: locale),
                            isLoading: isCancelling,
                            isDisabled: isCancelling,
                            identifier: "orders.cancelDialog.confirmButton",
                            action: onConfirm
                        )
                        PaymentSecondaryButton(
                            title: PaymentStrings.text("orders.cancelDialog.keep", locale: locale),
                            isDisabled: isCancelling,
                            identifier: "orders.cancelDialog.keepButton",
                            action: onKeep
                        )
                    }
                }
            }
            .padding(20)
            .frame(maxWidth: 420)
            .background(
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .fill(palette.bgSurfaceColor)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .strokeBorder(palette.borderColor, lineWidth: 1)
            )
            .padding(24)
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("orders.cancelDialog")
        }
    }
}

struct PaymentToastBanner: View {
    let message: String
    let locale: AppLocale
    let onDismiss: () -> Void

    @Environment(\.brandPalette) private var palette
    @Environment(\.statusPalette) private var statusPalette

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "checkmark.circle.fill")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(statusPalette.successColor)
                .accessibilityHidden(true)

            Text(message)
                .font(.subheadline.weight(.medium))
                .foregroundStyle(palette.textColor)
                .fixedSize(horizontal: false, vertical: true)

            Spacer(minLength: 8)

            Button(action: onDismiss) {
                Image(systemName: "xmark")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(palette.textMutedColor)
                    .frame(minWidth: 44, minHeight: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(AppStrings.common("common.closeToast", locale: locale))
            .accessibilityIdentifier("payment.toast.dismiss")
        }
        .padding(.leading, 14)
        .padding(.trailing, 4)
        .padding(.vertical, 6)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(palette.bgSurfaceColor)
                .shadow(color: Color.black.opacity(0.14), radius: 10, x: 0, y: 4)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .strokeBorder(statusPalette.successColor.opacity(0.35), lineWidth: 1)
        )
        .padding(.horizontal, 16)
        .padding(.bottom, 16)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("payment.toast")
    }
}

private struct OptionalAccessibilityIdentifier: ViewModifier {
    let identifier: String?

    func body(content: Content) -> some View {
        if let identifier {
            content.accessibilityIdentifier(identifier)
        } else {
            content
        }
    }
}
