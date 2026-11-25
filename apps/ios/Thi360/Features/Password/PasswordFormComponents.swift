import SwiftUI

struct RecoveryCircleNavButton: View {
    let systemName: String
    let accessibilityLabel: String
    let accessibilityIdentifier: String
    let action: () -> Void

    @Environment(\.brandPalette) private var palette

    var body: some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(palette.textColor)
                .frame(width: 44, height: 44)
                .background(
                    Circle()
                        .fill(palette.bgSubtleColor)
                )
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(accessibilityLabel)
        .accessibilityIdentifier(accessibilityIdentifier)
    }
}

struct RecoveryAlertBanner: View {
    let message: String
    let isDestructive: Bool
    let isWarning: Bool
    let accessibilityIdentifier: String

    @Environment(\.brandPalette) private var palette

    init(
        message: String,
        isDestructive: Bool = true,
        isWarning: Bool = false,
        accessibilityIdentifier: String
    ) {
        self.message = message
        self.isDestructive = isDestructive
        self.isWarning = isWarning
        self.accessibilityIdentifier = accessibilityIdentifier
    }

    var body: some View {
        let fg: Color = {
            if isDestructive { return palette.dangerTextColor }
            if isWarning { return palette.textColor }
            return palette.primaryTextColor
        }()
        let bg: Color = {
            if isDestructive { return palette.dangerBgColor }
            if isWarning { return palette.bgSubtleColor }
            return palette.primaryContainerColor
        }()
        let icon: String = {
            if isDestructive { return "exclamationmark.circle" }
            if isWarning { return "clock" }
            return "checkmark.circle"
        }()

        HStack(alignment: .top, spacing: 10) {
            Image(systemName: icon)
                .font(.body.weight(.medium))
                .foregroundStyle(fg)
                .accessibilityHidden(true)

            Text(message)
                .font(.subheadline)
                .foregroundStyle(fg)
                .fixedSize(horizontal: false, vertical: true)

            Spacer(minLength: 0)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(bg)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .strokeBorder(fg.opacity(0.25), lineWidth: 1)
        )
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier(accessibilityIdentifier)
    }
}

struct RecoveryTextFieldRow: View {
    let label: String
    let placeholder: String
    @Binding var text: String
    let hint: String?
    let errorMessage: String?
    let isFocused: Bool
    let usesCardSurfaceInput: Bool
    let contentType: UITextContentType?
    let keyboardType: UIKeyboardType
    let autocapitalization: TextInputAutocapitalization
    let disableAutocorrection: Bool
    let submitLabel: SubmitLabel
    let fieldIdentifier: String
    let errorIdentifier: String
    let onSubmit: () -> Void

    @Environment(\.brandPalette) private var palette

    var body: some View {
        let inputBackground = usesCardSurfaceInput ? palette.bgPageColor : palette.bgSubtleColor

        VStack(alignment: .leading, spacing: 6) {
            Text(label)
                .font(.footnote.weight(.semibold))
                .foregroundStyle(palette.textMutedColor)
                .padding(.leading, 4)

            TextField(placeholder, text: $text)
                .textFieldStyle(.plain)
                .textInputAutocapitalization(autocapitalization)
                .autocorrectionDisabled(disableAutocorrection)
                .textContentType(contentType)
                .keyboardType(keyboardType)
                .submitLabel(submitLabel)
                .onSubmit(onSubmit)
                .padding(.horizontal, 16)
                .frame(minHeight: 50)
                .foregroundStyle(palette.textColor)
                .background(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(inputBackground)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .strokeBorder(
                            borderColor(hasError: errorMessage != nil, isFocused: isFocused),
                            lineWidth: (isFocused || errorMessage != nil) ? 1.5 : 1
                        )
                )
                .accessibilityLabel(label)
                .accessibilityIdentifier(fieldIdentifier)

            if let errorMessage {
                Text(errorMessage)
                    .font(.footnote)
                    .foregroundStyle(palette.dangerTextColor)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.leading, 4)
                    .accessibilityIdentifier(errorIdentifier)
            } else if let hint {
                Text(hint)
                    .font(.footnote)
                    .foregroundStyle(palette.textMutedColor)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.leading, 4)
            }
        }
    }

    private func borderColor(hasError: Bool, isFocused: Bool) -> Color {
        if hasError { return palette.dangerTextColor }
        if isFocused { return palette.primaryFillColor }
        return palette.borderColor
    }
}

struct RecoveryPasswordFieldRow: View {
    let label: String
    let placeholder: String
    @Binding var text: String
    let isVisible: Bool
    let showLabel: String
    let hideLabel: String
    let errorMessage: String?
    let isFocused: Bool
    let usesCardSurfaceInput: Bool
    let contentType: UITextContentType
    let submitLabel: SubmitLabel
    let fieldIdentifier: String
    let toggleIdentifier: String
    let errorIdentifier: String
    let onToggleVisibility: () -> Void
    let onSubmit: () -> Void

    @Environment(\.brandPalette) private var palette

    var body: some View {
        let inputBackground = usesCardSurfaceInput ? palette.bgPageColor : palette.bgSubtleColor

        VStack(alignment: .leading, spacing: 6) {
            Text(label)
                .font(.footnote.weight(.semibold))
                .foregroundStyle(palette.textMutedColor)
                .padding(.leading, 4)

            HStack(spacing: 4) {
                Group {
                    if isVisible {
                        TextField(placeholder, text: $text)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled(true)
                    } else {
                        SecureField(placeholder, text: $text)
                    }
                }
                .textFieldStyle(.plain)
                .textContentType(contentType)
                .submitLabel(submitLabel)
                .onSubmit(onSubmit)
                .padding(.leading, 16)
                .frame(minHeight: 50)
                .foregroundStyle(palette.textColor)
                .accessibilityLabel(label)
                .accessibilityIdentifier(fieldIdentifier)

                Button(action: onToggleVisibility) {
                    Image(systemName: isVisible ? "eye.slash" : "eye")
                        .font(.system(size: 17, weight: .medium))
                        .foregroundStyle(palette.textMutedColor)
                        .frame(minWidth: 44, minHeight: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(isVisible ? hideLabel : showLabel)
                .accessibilityIdentifier(toggleIdentifier)
            }
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(inputBackground)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .strokeBorder(
                        borderColor(hasError: errorMessage != nil, isFocused: isFocused),
                        lineWidth: (isFocused || errorMessage != nil) ? 1.5 : 1
                    )
            )

            if let errorMessage {
                Text(errorMessage)
                    .font(.footnote)
                    .foregroundStyle(palette.dangerTextColor)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.leading, 4)
                    .accessibilityIdentifier(errorIdentifier)
            }
        }
    }

    private func borderColor(hasError: Bool, isFocused: Bool) -> Color {
        if hasError { return palette.dangerTextColor }
        if isFocused { return palette.primaryFillColor }
        return palette.borderColor
    }
}

struct PasswordRulesChecklistView: View {
    let evaluation: PasswordRuleEvaluation
    let locale: AppLocale

    @Environment(\.brandPalette) private var palette

    private let columns = [
        GridItem(.flexible(minimum: 120), spacing: 12, alignment: .leading),
        GridItem(.flexible(minimum: 120), spacing: 12, alignment: .leading)
    ]

    var body: some View {
        LazyVGrid(columns: columns, alignment: .leading, spacing: 6) {
            ForEach(evaluation.items) { item in
                let label = RecoveryStrings.text(item.kind.localizationKey, locale: locale)
                let stateLabel = RecoveryStrings.text(
                    item.isMet ? "recovery.password.ruleMet" : "recovery.password.ruleUnmet",
                    locale: locale
                )

                HStack(alignment: .top, spacing: 6) {
                    Image(systemName: item.isMet ? "checkmark" : "circle")
                        .font(.system(size: 12, weight: item.isMet ? .bold : .regular))
                        .foregroundStyle(item.isMet ? palette.primaryTextColor : palette.textMutedColor)
                        .frame(width: 14, height: 18)
                        .accessibilityHidden(true)

                    Text(label)
                        .font(.footnote)
                        .foregroundStyle(item.isMet ? palette.primaryTextColor : palette.textMutedColor)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .accessibilityElement(children: .combine)
                .accessibilityLabel(label)
                .accessibilityValue(stateLabel)
                .accessibilityIdentifier("password.rule.\(item.kind.rawValue)")
            }
        }
        .padding(.horizontal, 4)
        .accessibilityElement(children: .contain)
        .accessibilityLabel(RecoveryStrings.text("recovery.password.rulesLabel", locale: locale))
        .accessibilityIdentifier("password.rulesChecklist")
    }
}

struct RecoveryPrimaryButton: View {
    let title: String
    let loadingTitle: String
    let isLoading: Bool
    let isDisabled: Bool
    let identifier: String
    let action: () -> Void

    @Environment(\.brandPalette) private var palette

    init(
        title: String,
        loadingTitle: String? = nil,
        isLoading: Bool = false,
        isDisabled: Bool = false,
        identifier: String,
        action: @escaping () -> Void
    ) {
        self.title = title
        self.loadingTitle = loadingTitle ?? title
        self.isLoading = isLoading
        self.isDisabled = isDisabled
        self.identifier = identifier
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if isLoading {
                    ProgressView()
                        .tint(palette.onPrimaryColor)
                }
                Text(isLoading ? loadingTitle : title)
                    .font(.body.weight(.semibold))
            }
            .foregroundStyle(palette.onPrimaryColor)
            .frame(maxWidth: .infinity, minHeight: 50)
            .padding(.horizontal, 16)
            .background(
                Capsule(style: .continuous)
                    .fill(palette.primaryFillColor)
                    .opacity((isLoading || isDisabled) ? 0.65 : 1.0)
            )
            .contentShape(Capsule(style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(isLoading || isDisabled)
        .accessibilityIdentifier(identifier)
    }
}

struct AdaptiveFormScrollContainer<Content: View>: View {
    let isRegularLayout: Bool
    let isEmbeddedPane: Bool
    @ViewBuilder let content: () -> Content

    @Environment(\.brandPalette) private var palette

    init(
        isRegularLayout: Bool,
        isEmbeddedPane: Bool = false,
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.isRegularLayout = isRegularLayout
        self.isEmbeddedPane = isEmbeddedPane
        self.content = content
    }

    var body: some View {
        let usesCard = isRegularLayout || isEmbeddedPane
        ScrollView {
            VStack {
                content()
                    .padding(usesCard ? 28 : 20)
                    .frame(maxWidth: isEmbeddedPane ? .infinity : (isRegularLayout ? 500 : .infinity), alignment: .leading)
                    .background(
                        RoundedRectangle(cornerRadius: usesCard ? 24 : 0, style: .continuous)
                            .fill(palette.bgSurfaceColor)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: usesCard ? 24 : 0, style: .continuous)
                            .strokeBorder(usesCard ? palette.borderColor : Color.clear, lineWidth: 1)
                    )
                    .shadow(
                        color: usesCard ? Color.black.opacity(0.06) : Color.clear,
                        radius: usesCard ? 16 : 0,
                        x: 0,
                        y: usesCard ? 6 : 0
                    )
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, usesCard ? 20 : 0)
            .padding(.vertical, usesCard ? 16 : 0)
        }
        .background((usesCard ? palette.bgPageColor : palette.bgSurfaceColor).ignoresSafeArea())
    }
}
