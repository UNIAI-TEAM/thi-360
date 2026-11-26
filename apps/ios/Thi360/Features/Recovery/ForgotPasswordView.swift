import SwiftUI

struct ForgotPasswordView: View {
    @Bindable var viewModel: ForgotPasswordViewModel
    @Bindable var preferences: AppPreferences
    let onBackToLogin: () -> Void

    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.brandPalette) private var palette
    @FocusState private var isEmailFocused: Bool

    private var locale: AppLocale { preferences.locale }
    private var isRegularLayout: Bool { horizontalSizeClass == .regular }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                RecoveryCircleNavButton(
                    systemName: "chevron.left",
                    accessibilityLabel: RecoveryStrings.text("recovery.forgot.backToLogin", locale: locale),
                    accessibilityIdentifier: "forgot.navBackButton",
                    action: onBackToLogin
                )
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .background(isRegularLayout ? palette.bgPageColor : palette.bgSurfaceColor)

            AdaptiveFormScrollContainer(isRegularLayout: isRegularLayout) {
                switch viewModel.state {
                case .form:
                    formContent
                case .sent:
                    sentContent
                }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("forgot.screen")
        .onAppear {
            Thi360AppDelegate.refreshSupportedOrientations(for: horizontalSizeClass)
        }
        .onChange(of: horizontalSizeClass) { _, newValue in
            Thi360AppDelegate.refreshSupportedOrientations(for: newValue)
        }
        .task(id: viewModel.resendSecondsRemaining > 0) {
            guard viewModel.resendSecondsRemaining > 0 else { return }
            while !Task.isCancelled && viewModel.resendSecondsRemaining > 0 {
                try? await Task.sleep(nanoseconds: 500_000_000)
                viewModel.tick()
            }
        }
    }

    private var formContent: some View {
        VStack(alignment: .leading, spacing: 20) {
            VStack(alignment: .leading, spacing: 8) {
                Text(RecoveryStrings.text("recovery.forgot.title", locale: locale))
                    .font(.largeTitle.weight(.bold))
                    .foregroundStyle(palette.textColor)
                    .accessibilityAddTraits(.isHeader)
                    .accessibilityIdentifier("forgot.heading")

                Text(RecoveryStrings.text("recovery.forgot.description", locale: locale))
                    .font(.body)
                    .foregroundStyle(palette.textSecondaryColor)
                    .fixedSize(horizontal: false, vertical: true)
            }

            if let alert = viewModel.alert {
                RecoveryAlertBanner(
                    message: alert.message(locale: locale),
                    isDestructive: true,
                    accessibilityIdentifier: "forgot.alertBanner"
                )
            }

            RecoveryTextFieldRow(
                label: RecoveryStrings.text("recovery.forgot.emailLabel", locale: locale),
                placeholder: RecoveryStrings.text("recovery.forgot.emailPlaceholder", locale: locale),
                text: $viewModel.email,
                hint: nil,
                errorMessage: viewModel.emailError?.message(locale: locale),
                isFocused: isEmailFocused,
                usesCardSurfaceInput: isRegularLayout,
                contentType: .emailAddress,
                keyboardType: .emailAddress,
                autocapitalization: .never,
                disableAutocorrection: true,
                submitLabel: .done,
                fieldIdentifier: "forgot.emailField",
                errorIdentifier: "forgot.emailError",
                onSubmit: {
                    isEmailFocused = false
                    Task { await viewModel.submit() }
                }
            )
            .focused($isEmailFocused)
            .onChange(of: viewModel.email) { _, _ in
                viewModel.clearEmailErrorIfNeeded()
            }

            RecoveryPrimaryButton(
                title: RecoveryStrings.text("recovery.forgot.submit", locale: locale),
                loadingTitle: RecoveryStrings.text("recovery.forgot.sending", locale: locale),
                isLoading: viewModel.isSubmitting,
                identifier: "forgot.submitButton",
                action: {
                    isEmailFocused = false
                    Task { await viewModel.submit() }
                }
            )

            backToLoginButton
        }
    }

    private var sentContent: some View {
        let remaining = viewModel.resendSecondsRemaining
        return VStack(alignment: .leading, spacing: 20) {
            ZStack {
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .fill(palette.primaryContainerStrongColor)
                    .frame(width: 92, height: 92)
                Image(systemName: "envelope")
                    .font(.system(size: 40, weight: .regular))
                    .foregroundStyle(palette.primaryTextColor)
            }
            .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 8) {
                Text(RecoveryStrings.text("recovery.forgot.sentTitle", locale: locale))
                    .font(.largeTitle.weight(.bold))
                    .foregroundStyle(palette.textColor)
                    .accessibilityAddTraits(.isHeader)
                    .accessibilityIdentifier("forgot.sentHeading")

                Text(RecoveryStrings.text("recovery.forgot.sentMessage", locale: locale))
                    .font(.body)
                    .foregroundStyle(palette.textSecondaryColor)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("forgot.sentMessage")
            }

            VStack(alignment: .leading, spacing: 8) {
                Text(RecoveryStrings.text("recovery.forgot.sentNote1", locale: locale))
                Text(RecoveryStrings.text("recovery.forgot.sentNote2", locale: locale))
                Text(RecoveryStrings.text("recovery.forgot.sentNote3", locale: locale))
            }
            .font(.subheadline)
            .foregroundStyle(palette.textSecondaryColor)
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(isRegularLayout ? palette.bgPageColor : palette.bgSubtleColor)
            )

            if let alert = viewModel.alert {
                RecoveryAlertBanner(
                    message: alert.message(locale: locale),
                    isDestructive: true,
                    accessibilityIdentifier: "forgot.alertBanner"
                )
            }

            Button {
                Task { await viewModel.resend() }
            } label: {
                HStack(spacing: 8) {
                    if viewModel.isSubmitting {
                        ProgressView()
                            .tint(palette.primaryTextColor)
                    }
                    Text(
                        remaining > 0
                            ? RecoveryStrings.text(
                                "recovery.forgot.resendIn",
                                locale: locale,
                                replacements: ["time": CooldownFormatter.mmss(remaining)]
                            )
                            : RecoveryStrings.text("recovery.forgot.resend", locale: locale)
                    )
                    .font(.body.weight(.semibold))
                    .monospacedDigit()
                }
                .foregroundStyle(remaining > 0 ? palette.textMutedColor : palette.primaryTextColor)
                .frame(maxWidth: .infinity, minHeight: 50)
                .padding(.horizontal, 16)
                .background(
                    Capsule(style: .continuous)
                        .fill(isRegularLayout ? palette.bgPageColor : palette.bgSubtleColor)
                )
                .contentShape(Capsule(style: .continuous))
            }
            .buttonStyle(.plain)
            .disabled(!viewModel.canResend)
            .accessibilityIdentifier("forgot.resendButton")

            backToLoginButton
        }
    }

    private var backToLoginButton: some View {
        Button(action: onBackToLogin) {
            Text(RecoveryStrings.text("recovery.forgot.backToLogin", locale: locale))
                .font(.body)
                .foregroundStyle(palette.primaryTextColor)
                .frame(maxWidth: .infinity, minHeight: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("forgot.backToLoginButton")
    }
}
