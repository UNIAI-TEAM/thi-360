import SwiftUI

struct NewPasswordView: View {
    @Bindable var viewModel: NewPasswordViewModel
    @Bindable var preferences: AppPreferences
    var isEmbeddedPane: Bool = false
    var onClose: (() -> Void)? = nil
    var onRequestNewResetLink: (() -> Void)? = nil
    var onSignOut: (() -> Void)? = nil
    var stateProbeToggleAction: (() -> Void)? = nil

    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.brandPalette) private var palette
    @FocusState private var focusedField: PasswordField?

    private enum PasswordField: Hashable {
        case oldPassword
        case newPassword
        case confirmPassword
    }

    private var locale: AppLocale { preferences.locale }
    private var isRegularLayout: Bool { horizontalSizeClass == .regular }

    var body: some View {
        VStack(spacing: 0) {
            if showsNavigationHeader {
                HStack {
                    if let onClose {
                        RecoveryCircleNavButton(
                            systemName: isCloseIcon ? "xmark" : "chevron.left",
                            accessibilityLabel: RecoveryStrings.text(
                                isCloseIcon ? "recovery.common.close" : "recovery.common.back",
                                locale: locale
                            ),
                            accessibilityIdentifier: "password.closeButton",
                            action: {
                                if case .fromLink = viewModel.mode {
                                    viewModel.clearResetToken()
                                }
                                onClose()
                            }
                        )
                    }
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background(isRegularLayout ? palette.bgPageColor : palette.bgSurfaceColor)
            }

            VStack(spacing: 0) {
                AdaptiveFormScrollContainer(
                    isRegularLayout: isRegularLayout,
                    isEmbeddedPane: isEmbeddedPane
                ) {
                    if case .fromLink = viewModel.mode, viewModel.isResetLinkInvalid {
                        invalidResetLinkContent
                    } else {
                        passwordFormContent
                    }
                }
            }
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier(isRegularLayout ? "password.layout.regular" : "password.layout.compact")
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier(screenIdentifier)
        .onAppear {
            Thi360AppDelegate.refreshSupportedOrientations(for: horizontalSizeClass)
        }
        .onChange(of: horizontalSizeClass) { _, newValue in
            Thi360AppDelegate.refreshSupportedOrientations(for: newValue)
        }
    }

    private var showsNavigationHeader: Bool {
        guard !isEmbeddedPane else { return false }
        switch viewModel.mode {
        case .forced:
            return false
        case .fromLink, .voluntary:
            return onClose != nil
        }
    }

    private var isCloseIcon: Bool {
        if case .fromLink = viewModel.mode { return true }
        return false
    }

    private var screenIdentifier: String {
        switch viewModel.mode {
        case .fromLink: "password.reset.screen"
        case .forced: "session.mustChangePassword.screen"
        case .voluntary: "password.change.screen"
        }
    }

    private var invalidResetLinkContent: some View {
        VStack(alignment: .leading, spacing: 20) {
            ZStack {
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .fill(palette.bgSubtleColor)
                    .frame(width: 92, height: 92)
                Image(systemName: "link.badge.plus")
                    .font(.system(size: 38, weight: .regular))
                    .foregroundStyle(palette.textMutedColor)
            }
            .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 8) {
                Text(RecoveryStrings.text("recovery.reset.invalidTitle", locale: locale))
                    .font(.title2.weight(.bold))
                    .foregroundStyle(palette.textColor)
                    .accessibilityAddTraits(.isHeader)
                    .accessibilityIdentifier("password.reset.invalidTitle")

                Text(RecoveryStrings.text("recovery.reset.invalidMessage", locale: locale))
                    .font(.body)
                    .foregroundStyle(palette.textSecondaryColor)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("password.reset.invalidMessage")
            }

            if let onRequestNewResetLink {
                RecoveryPrimaryButton(
                    title: RecoveryStrings.text("recovery.reset.requestNew", locale: locale),
                    identifier: "password.reset.requestNewButton",
                    action: {
                        viewModel.clearResetToken()
                        onRequestNewResetLink()
                    }
                )
            }

            if let onClose {
                Button {
                    viewModel.clearResetToken()
                    onClose()
                } label: {
                    Text(RecoveryStrings.text("recovery.reset.backToLogin", locale: locale))
                        .font(.body)
                        .foregroundStyle(palette.primaryTextColor)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("password.reset.backToLoginButton")
            }
        }
    }

    private var passwordFormContent: some View {
        let usesCardSurface = isRegularLayout || isEmbeddedPane

        return VStack(alignment: .leading, spacing: 18) {
            headingSection

            if let banner = viewModel.banner {
                RecoveryAlertBanner(
                    message: banner.message(locale: locale),
                    isDestructive: banner.isDestructive,
                    accessibilityIdentifier: "password.bannerMessage"
                )
            }

            if viewModel.mode.requiresCurrentPassword {
                RecoveryPasswordFieldRow(
                    label: RecoveryStrings.text("recovery.password.oldLabel", locale: locale),
                    placeholder: RecoveryStrings.text("recovery.password.oldPlaceholder", locale: locale),
                    text: $viewModel.oldPassword,
                    isVisible: viewModel.isOldPasswordVisible,
                    showLabel: RecoveryStrings.text("recovery.password.oldShow", locale: locale),
                    hideLabel: RecoveryStrings.text("recovery.password.oldHide", locale: locale),
                    errorMessage: viewModel.oldPasswordError?.message(locale: locale),
                    isFocused: focusedField == .oldPassword,
                    usesCardSurfaceInput: usesCardSurface,
                    contentType: .password,
                    submitLabel: .next,
                    fieldIdentifier: "password.oldField",
                    toggleIdentifier: "password.oldToggle",
                    errorIdentifier: "password.oldError",
                    onToggleVisibility: { viewModel.toggleOldPasswordVisibility() },
                    onSubmit: { focusedField = .newPassword }
                )
                .focused($focusedField, equals: .oldPassword)
                .onChange(of: viewModel.oldPassword) { _, _ in
                    viewModel.clearFieldErrorOnEdit(oldPassword: true)
                }
            }

            VStack(alignment: .leading, spacing: 8) {
                RecoveryPasswordFieldRow(
                    label: RecoveryStrings.text("recovery.password.newLabel", locale: locale),
                    placeholder: RecoveryStrings.text("recovery.password.newPlaceholder", locale: locale),
                    text: $viewModel.newPassword,
                    isVisible: viewModel.isNewPasswordVisible,
                    showLabel: RecoveryStrings.text("recovery.password.newShow", locale: locale),
                    hideLabel: RecoveryStrings.text("recovery.password.newHide", locale: locale),
                    errorMessage: viewModel.newPasswordError?.message(locale: locale),
                    isFocused: focusedField == .newPassword,
                    usesCardSurfaceInput: usesCardSurface,
                    contentType: .newPassword,
                    submitLabel: .next,
                    fieldIdentifier: "password.newField",
                    toggleIdentifier: "password.newToggle",
                    errorIdentifier: "password.newError",
                    onToggleVisibility: { viewModel.toggleNewPasswordVisibility() },
                    onSubmit: { focusedField = .confirmPassword }
                )
                .focused($focusedField, equals: .newPassword)
                .onChange(of: viewModel.newPassword) { _, _ in
                    viewModel.clearFieldErrorOnEdit(newPassword: true)
                }

                PasswordRulesChecklistView(
                    evaluation: viewModel.passwordEvaluation,
                    locale: locale
                )
            }

            RecoveryPasswordFieldRow(
                label: RecoveryStrings.text("recovery.password.confirmLabel", locale: locale),
                placeholder: RecoveryStrings.text("recovery.password.confirmPlaceholder", locale: locale),
                text: $viewModel.confirmPassword,
                isVisible: viewModel.isConfirmPasswordVisible,
                showLabel: RecoveryStrings.text("recovery.password.confirmShow", locale: locale),
                hideLabel: RecoveryStrings.text("recovery.password.confirmHide", locale: locale),
                errorMessage: viewModel.confirmPasswordError?.message(locale: locale),
                isFocused: focusedField == .confirmPassword,
                usesCardSurfaceInput: usesCardSurface,
                contentType: .newPassword,
                submitLabel: .done,
                fieldIdentifier: "password.confirmField",
                toggleIdentifier: "password.confirmToggle",
                errorIdentifier: "password.confirmError",
                onToggleVisibility: { viewModel.toggleConfirmPasswordVisibility() },
                onSubmit: {
                    focusedField = nil
                    Task { await viewModel.submit() }
                }
            )
            .focused($focusedField, equals: .confirmPassword)
            .onChange(of: viewModel.confirmPassword) { _, _ in
                viewModel.clearFieldErrorOnEdit(confirmPassword: true)
            }

            RecoveryPrimaryButton(
                title: submitButtonTitle,
                loadingTitle: RecoveryStrings.text("recovery.common.saving", locale: locale),
                isLoading: viewModel.isSubmitting,
                identifier: "password.submitButton",
                action: {
                    focusedField = nil
                    Task { await viewModel.submit() }
                }
            )

            if case .fromLink = viewModel.mode {
                Text(RecoveryStrings.text("recovery.reset.footerNote", locale: locale))
                    .font(.footnote)
                    .foregroundStyle(palette.textMutedColor)
                    .fixedSize(horizontal: false, vertical: true)
            }

            if case .forced = viewModel.mode, let onSignOut {
                Button(action: onSignOut) {
                    Text(RecoveryStrings.text("recovery.forced.signOut", locale: locale))
                        .font(.body.weight(.medium))
                        .foregroundStyle(palette.primaryTextColor)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("session.mustChangePassword.signOut")
            }

            if let stateProbeToggleAction {
                Button(action: stateProbeToggleAction) {
                    Text(ShellStrings.text("shell.probe.toggleSizeClass", locale: locale))
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(palette.primaryTextColor)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .background(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(palette.bgSubtleColor)
                        )
                }
                .buttonStyle(.plain)
                .accessibilityValue(isRegularLayout ? "regular" : "compact")
                .accessibilityIdentifier("changePassword.probe.toggleSizeClass")
            }
        }
    }

    @ViewBuilder
    private var headingSection: some View {
        switch viewModel.mode {
        case .fromLink:
            VStack(alignment: .leading, spacing: 12) {
                Text(RecoveryStrings.text("recovery.reset.title", locale: locale))
                    .font(.largeTitle.weight(.bold))
                    .foregroundStyle(palette.textColor)
                    .accessibilityAddTraits(.isHeader)
                    .accessibilityIdentifier("password.heading")

                HStack(alignment: .top, spacing: 10) {
                    Image(systemName: "link")
                        .font(.body.weight(.medium))
                        .foregroundStyle(palette.primaryTextColor)
                        .accessibilityHidden(true)

                    Text(RecoveryStrings.text("recovery.reset.linkBanner", locale: locale))
                        .font(.subheadline)
                        .foregroundStyle(palette.textColor)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(isRegularLayout ? palette.bgPageColor : palette.bgSubtleColor)
                )
                .accessibilityIdentifier("password.reset.linkBanner")

                Text(RecoveryStrings.text("recovery.reset.description", locale: locale))
                    .font(.body)
                    .foregroundStyle(palette.textSecondaryColor)
                    .fixedSize(horizontal: false, vertical: true)
            }

        case let .forced(username):
            VStack(alignment: .leading, spacing: 8) {
                Text(RecoveryStrings.text("recovery.forced.title", locale: locale))
                    .font(.largeTitle.weight(.bold))
                    .foregroundStyle(palette.textColor)
                    .accessibilityAddTraits(.isHeader)
                    .accessibilityIdentifier("password.heading")

                let trimmedUsername = username?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                Text(
                    trimmedUsername.isEmpty
                        ? RecoveryStrings.text("recovery.forced.description", locale: locale)
                        : RecoveryStrings.text(
                            "recovery.forced.descriptionWithUsername",
                            locale: locale,
                            replacements: ["username": trimmedUsername]
                        )
                )
                .font(.body)
                .foregroundStyle(palette.textSecondaryColor)
                .fixedSize(horizontal: false, vertical: true)
            }

        case let .voluntary(username):
            VStack(alignment: .leading, spacing: 4) {
                Text(RecoveryStrings.text("recovery.change.title", locale: locale))
                    .font((isEmbeddedPane ? Font.title2 : Font.largeTitle).weight(.bold))
                    .foregroundStyle(palette.textColor)
                    .accessibilityAddTraits(.isHeader)
                    .accessibilityIdentifier("password.heading")

                let trimmedUsername = username?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                Text(
                    trimmedUsername.isEmpty
                        ? RecoveryStrings.text("recovery.change.subtitle", locale: locale)
                        : RecoveryStrings.text(
                            "recovery.change.subtitleWithUsername",
                            locale: locale,
                            replacements: ["username": trimmedUsername]
                        )
                )
                .font(.subheadline)
                .foregroundStyle(palette.textMutedColor)
                .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private var submitButtonTitle: String {
        switch viewModel.mode {
        case .fromLink:
            RecoveryStrings.text("recovery.reset.submit", locale: locale)
        case .forced:
            RecoveryStrings.text("recovery.forced.submit", locale: locale)
        case .voluntary:
            RecoveryStrings.text("recovery.change.submit", locale: locale)
        }
    }
}
