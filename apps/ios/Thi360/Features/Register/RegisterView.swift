import SwiftUI

struct RegisterConsentSentence: Equatable {
    static let termsLinkURL = URL(string: "thi360-legal://terms")!
    static let privacyLinkURL = URL(string: "thi360-legal://privacy")!

    let attributedText: AttributedString
    let plainText: String
    let termsLabel: String?
    let privacyLabel: String?

    static func parse(_ template: String, linkColor: Color) -> RegisterConsentSentence {
        var attributed = AttributedString()
        var plain = ""
        var termsLabel: String?
        var privacyLabel: String?

        var cursor = template.startIndex
        while cursor < template.endIndex {
            let termsRange = template.range(of: "<terms>", range: cursor..<template.endIndex)
            let privacyRange = template.range(of: "<privacy>", range: cursor..<template.endIndex)

            let nextTag: (id: LegalId, openRange: Range<String.Index>, closeTag: String)?
            switch (termsRange, privacyRange) {
            case let (t?, p?):
                if t.lowerBound <= p.lowerBound {
                    nextTag = (.terms, t, "</terms>")
                } else {
                    nextTag = (.privacy, p, "</privacy>")
                }
            case let (t?, nil):
                nextTag = (.terms, t, "</terms>")
            case let (nil, p?):
                nextTag = (.privacy, p, "</privacy>")
            case (nil, nil):
                nextTag = nil
            }

            guard let tag = nextTag,
                  let closeRange = template.range(of: tag.closeTag, range: tag.openRange.upperBound..<template.endIndex)
            else {
                let tail = String(template[cursor..<template.endIndex])
                attributed.append(AttributedString(tail))
                plain.append(tail)
                break
            }

            let prefix = String(template[cursor..<tag.openRange.lowerBound])
            if !prefix.isEmpty {
                attributed.append(AttributedString(prefix))
                plain.append(prefix)
            }

            let linkText = String(template[tag.openRange.upperBound..<closeRange.lowerBound])
            var run = AttributedString(linkText)
            run.link = (tag.id == .terms) ? Self.termsLinkURL : Self.privacyLinkURL
            run.foregroundColor = linkColor
            run.underlineStyle = .single
            attributed.append(run)
            plain.append(linkText)

            switch tag.id {
            case .terms:
                termsLabel = linkText
            case .privacy:
                privacyLabel = linkText
            }

            cursor = closeRange.upperBound
        }

        return RegisterConsentSentence(
            attributedText: attributed,
            plainText: plain,
            termsLabel: termsLabel,
            privacyLabel: privacyLabel
        )
    }
}

struct RegisterView: View {
    @Bindable var viewModel: RegisterViewModel
    @Bindable var preferences: AppPreferences
    let onBackToLogin: () -> Void
    var onOpenLegal: (LegalId) -> Void = { _ in }

    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.brandPalette) private var palette
    @FocusState private var focusedField: RegisterField?

    private enum RegisterField: Hashable {
        case fullName
        case username
        case email
        case password
        case otp
    }

    private var locale: AppLocale { preferences.locale }
    private var isRegularLayout: Bool { horizontalSizeClass == .regular }

    var body: some View {
        Group {
            switch viewModel.state {
            case .form, .done:
                registrationFormScreen
            case let .otp(_, email, _, _):
                otpScreen(email: email)
            }
        }
        .onAppear {
            Thi360AppDelegate.refreshSupportedOrientations(for: horizontalSizeClass)
        }
        .onChange(of: horizontalSizeClass) { _, newValue in
            Thi360AppDelegate.refreshSupportedOrientations(for: newValue)
        }
    }

    private var registrationFormScreen: some View {
        VStack(spacing: 0) {
            HStack {
                RecoveryCircleNavButton(
                    systemName: "chevron.left",
                    accessibilityLabel: RecoveryStrings.text("recovery.common.back", locale: locale),
                    accessibilityIdentifier: "register.backButton",
                    action: onBackToLogin
                )
                Spacer(minLength: 0)
                Text(RecoveryStrings.text("recovery.register.title", locale: locale))
                    .font(.headline)
                    .foregroundStyle(palette.textColor)
                    .accessibilityAddTraits(.isHeader)
                    .accessibilityIdentifier("register.heading")
                Spacer(minLength: 0)
                Color.clear
                    .frame(width: 44, height: 44)
                    .accessibilityHidden(true)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .background(isRegularLayout ? palette.bgPageColor : palette.bgSurfaceColor)

            AdaptiveFormScrollContainer(isRegularLayout: isRegularLayout) {
                VStack(alignment: .leading, spacing: 16) {
                    if let alert = viewModel.registerAlert {
                        RecoveryAlertBanner(
                            message: alert.message(locale: locale),
                            isDestructive: true,
                            accessibilityIdentifier: "register.alertBanner"
                        )
                    }

                    RecoveryTextFieldRow(
                        label: RecoveryStrings.text("recovery.register.fullNameLabel", locale: locale),
                        placeholder: RecoveryStrings.text("recovery.register.fullNamePlaceholder", locale: locale),
                        text: $viewModel.fullName,
                        hint: nil,
                        errorMessage: viewModel.fullNameError?.message(locale: locale),
                        isFocused: focusedField == .fullName,
                        usesCardSurfaceInput: isRegularLayout,
                        contentType: .name,
                        keyboardType: .default,
                        autocapitalization: .words,
                        disableAutocorrection: false,
                        submitLabel: .next,
                        fieldIdentifier: "register.fullNameField",
                        errorIdentifier: "register.fullNameError",
                        onSubmit: { focusedField = .username }
                    )
                    .focused($focusedField, equals: .fullName)
                    .onChange(of: viewModel.fullName) { _, _ in
                        viewModel.clearFieldErrorOnEdit(fullName: true)
                    }

                    RecoveryTextFieldRow(
                        label: RecoveryStrings.text("recovery.register.usernameLabel", locale: locale),
                        placeholder: RecoveryStrings.text("recovery.register.usernamePlaceholder", locale: locale),
                        text: $viewModel.username,
                        hint: RecoveryStrings.text("recovery.register.usernameHint", locale: locale),
                        errorMessage: viewModel.usernameError?.message(locale: locale),
                        isFocused: focusedField == .username,
                        usesCardSurfaceInput: isRegularLayout,
                        contentType: .username,
                        keyboardType: .asciiCapable,
                        autocapitalization: .never,
                        disableAutocorrection: true,
                        submitLabel: .next,
                        fieldIdentifier: "register.usernameField",
                        errorIdentifier: "register.usernameError",
                        onSubmit: { focusedField = .email }
                    )
                    .focused($focusedField, equals: .username)
                    .onChange(of: viewModel.username) { _, _ in
                        viewModel.clearFieldErrorOnEdit(username: true)
                    }

                    RecoveryTextFieldRow(
                        label: RecoveryStrings.text("recovery.register.emailLabel", locale: locale),
                        placeholder: RecoveryStrings.text("recovery.register.emailPlaceholder", locale: locale),
                        text: $viewModel.email,
                        hint: RecoveryStrings.text("recovery.register.emailHint", locale: locale),
                        errorMessage: viewModel.emailError?.message(locale: locale),
                        isFocused: focusedField == .email,
                        usesCardSurfaceInput: isRegularLayout,
                        contentType: .emailAddress,
                        keyboardType: .emailAddress,
                        autocapitalization: .never,
                        disableAutocorrection: true,
                        submitLabel: .next,
                        fieldIdentifier: "register.emailField",
                        errorIdentifier: "register.emailError",
                        onSubmit: { focusedField = .password }
                    )
                    .focused($focusedField, equals: .email)
                    .onChange(of: viewModel.email) { _, _ in
                        viewModel.clearFieldErrorOnEdit(email: true)
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        RecoveryPasswordFieldRow(
                            label: RecoveryStrings.text("recovery.register.passwordLabel", locale: locale),
                            placeholder: RecoveryStrings.text("recovery.register.passwordPlaceholder", locale: locale),
                            text: $viewModel.password,
                            isVisible: viewModel.isPasswordVisible,
                            showLabel: AuthStrings.text("auth.login.showPassword", locale: locale),
                            hideLabel: AuthStrings.text("auth.login.hidePassword", locale: locale),
                            errorMessage: viewModel.passwordError?.message(locale: locale),
                            isFocused: focusedField == .password,
                            usesCardSurfaceInput: isRegularLayout,
                            contentType: .newPassword,
                            submitLabel: .done,
                            fieldIdentifier: "register.passwordField",
                            toggleIdentifier: "register.passwordToggle",
                            errorIdentifier: "register.passwordError",
                            onToggleVisibility: { viewModel.togglePasswordVisibility() },
                            onSubmit: {
                                focusedField = nil
                                Task { await viewModel.submitRegistration() }
                            }
                        )
                        .focused($focusedField, equals: .password)
                        .onChange(of: viewModel.password) { _, _ in
                            viewModel.clearFieldErrorOnEdit(password: true)
                        }

                        PasswordRulesChecklistView(
                            evaluation: viewModel.passwordEvaluation,
                            locale: locale
                        )
                    }

                    termsSection

                    RecoveryPrimaryButton(
                        title: RecoveryStrings.text("recovery.register.submit", locale: locale),
                        loadingTitle: RecoveryStrings.text("recovery.register.submitting", locale: locale),
                        isLoading: viewModel.isSubmitting,
                        identifier: "register.submitButton",
                        action: {
                            focusedField = nil
                            Task { await viewModel.submitRegistration() }
                        }
                    )

                    HStack(spacing: 4) {
                        Text(RecoveryStrings.text("recovery.register.haveAccount", locale: locale))
                            .font(.subheadline)
                            .foregroundStyle(palette.textMutedColor)

                        Button(action: onBackToLogin) {
                            Text(RecoveryStrings.text("recovery.register.loginLink", locale: locale))
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(palette.primaryTextColor)
                                .frame(minHeight: 44)
                                .padding(.horizontal, 6)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityIdentifier("register.loginButton")
                    }
                    .frame(maxWidth: .infinity, alignment: .center)
                }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("register.screen")
    }

    private var termsSection: some View {
        let template = RecoveryStrings.text("recovery.register.termsLabel", locale: locale)
        let consent = RegisterConsentSentence.parse(template, linkColor: palette.primaryTextColor)

        return VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .top, spacing: 6) {
                Button {
                    focusedField = nil
                    viewModel.acceptTerms.toggle()
                    viewModel.clearFieldErrorOnEdit(terms: true)
                } label: {
                    ZStack {
                        Circle()
                            .fill(viewModel.acceptTerms ? palette.primaryFillColor : Color.clear)
                            .frame(width: 22, height: 22)
                            .overlay(
                                Circle()
                                    .strokeBorder(
                                        viewModel.termsError != nil
                                            ? palette.dangerTextColor
                                            : (viewModel.acceptTerms ? palette.primaryFillColor : palette.borderStrongColor),
                                        lineWidth: 1.5
                                    )
                            )
                        if viewModel.acceptTerms {
                            Image(systemName: "checkmark")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundStyle(palette.onPrimaryColor)
                        }
                    }
                    .frame(minWidth: 44, minHeight: 44)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(consent.plainText)
                .accessibilityAddTraits(viewModel.acceptTerms ? [.isSelected] : [])
                .accessibilityIdentifier("register.termsCheckbox")

                Text(consent.attributedText)
                    .font(.subheadline)
                    .foregroundStyle(palette.textSecondaryColor)
                    .multilineTextAlignment(.leading)
                    .fixedSize(horizontal: false, vertical: true)
                    .lineSpacing(4)
                    .padding(.top, 10)
                    .environment(\.openURL, OpenURLAction { url in
                        if url == RegisterConsentSentence.termsLinkURL {
                            focusedField = nil
                            onOpenLegal(.terms)
                            return .handled
                        }
                        if url == RegisterConsentSentence.privacyLinkURL {
                            focusedField = nil
                            onOpenLegal(.privacy)
                            return .handled
                        }
                        return .systemAction
                    })
                    .accessibilityIdentifier("register.termsSentence")

                Spacer(minLength: 0)
            }

            if let termsLabel = consent.termsLabel, let privacyLabel = consent.privacyLabel {
                VStack(alignment: .leading, spacing: 2) {
                    Button {
                        focusedField = nil
                        onOpenLegal(.terms)
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "doc.text")
                                .font(.footnote.weight(.semibold))
                                .accessibilityHidden(true)
                            Text(termsLabel)
                                .font(.footnote.weight(.semibold))
                                .underline()
                                .multilineTextAlignment(.leading)
                        }
                        .foregroundStyle(palette.primaryTextColor)
                        .frame(minHeight: 44, alignment: .leading)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(.isLink)
                    .accessibilityIdentifier("register.termsLinkTerms")

                    Button {
                        focusedField = nil
                        onOpenLegal(.privacy)
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "lock.shield")
                                .font(.footnote.weight(.semibold))
                                .accessibilityHidden(true)
                            Text(privacyLabel)
                                .font(.footnote.weight(.semibold))
                                .underline()
                                .multilineTextAlignment(.leading)
                        }
                        .foregroundStyle(palette.primaryTextColor)
                        .frame(minHeight: 44, alignment: .leading)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(.isLink)
                    .accessibilityIdentifier("register.termsLinkPrivacy")
                }
                .padding(.leading, 48)
            }

            if let error = viewModel.termsError {
                Text(error.message(locale: locale))
                    .font(.footnote)
                    .foregroundStyle(palette.dangerTextColor)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.leading, 4)
                    .accessibilityIdentifier("register.termsError")
            }
        }
    }

    private func otpScreen(email: String) -> some View {
        let resendRemaining = viewModel.resendSecondsRemaining
        return VStack(spacing: 0) {
            HStack {
                RecoveryCircleNavButton(
                    systemName: "chevron.left",
                    accessibilityLabel: RecoveryStrings.text("recovery.otp.backToEdit", locale: locale),
                    accessibilityIdentifier: "otp.navBackButton",
                    action: { viewModel.backToEdit() }
                )
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .background(isRegularLayout ? palette.bgPageColor : palette.bgSurfaceColor)

            AdaptiveFormScrollContainer(isRegularLayout: isRegularLayout) {
                VStack(alignment: .leading, spacing: 20) {
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
                        Text(RecoveryStrings.text("recovery.otp.title", locale: locale))
                            .font(.largeTitle.weight(.bold))
                            .foregroundStyle(palette.textColor)
                            .accessibilityAddTraits(.isHeader)
                            .accessibilityIdentifier("otp.heading")

                        otpEmailDescription(email: email)
                            .font(.body)
                            .foregroundStyle(palette.textSecondaryColor)
                            .fixedSize(horizontal: false, vertical: true)
                            .accessibilityIdentifier("otp.emailDescription")
                    }

                    if let alert = viewModel.otpAlert {
                        RecoveryAlertBanner(
                            message: alert.message(locale: locale),
                            isDestructive: true,
                            accessibilityIdentifier: "otp.alertBanner"
                        )
                    }

                    if let dailyLimitUntil = viewModel.dailyLimitUntil {
                        RecoveryAlertBanner(
                            message: RecoveryStrings.text(
                                "recovery.otp.dailyLimit",
                                locale: locale,
                                replacements: ["time": CooldownFormatter.dailyLimitDateString(dailyLimitUntil, locale: locale)]
                            ),
                            isDestructive: false,
                            isWarning: true,
                            accessibilityIdentifier: "otp.dailyLimitBanner"
                        )
                    }

                    if viewModel.didResendCode {
                        RecoveryAlertBanner(
                            message: RecoveryStrings.text("recovery.otp.codeResent", locale: locale),
                            isDestructive: false,
                            accessibilityIdentifier: "otp.resentBanner"
                        )
                    }

                    otpCodeInputSection

                    RecoveryPrimaryButton(
                        title: RecoveryStrings.text("recovery.otp.submit", locale: locale),
                        loadingTitle: RecoveryStrings.text("recovery.otp.verifying", locale: locale),
                        isLoading: viewModel.isVerifying,
                        identifier: "otp.submitButton",
                        action: {
                            focusedField = nil
                            Task { await viewModel.submitOtp() }
                        }
                    )

                    VStack(spacing: 4) {
                        Text(RecoveryStrings.text("recovery.otp.notReceived", locale: locale))
                            .font(.subheadline)
                            .foregroundStyle(palette.textMutedColor)

                        if resendRemaining > 0 {
                            Text(
                                RecoveryStrings.text(
                                    "recovery.otp.resendIn",
                                    locale: locale,
                                    replacements: ["time": CooldownFormatter.mmss(resendRemaining)]
                                )
                            )
                            .font(.subheadline.weight(.semibold))
                            .monospacedDigit()
                            .foregroundStyle(palette.textColor)
                            .accessibilityIdentifier("otp.resendCountdown")
                        } else {
                            Button {
                                Task { await viewModel.resendOtp() }
                            } label: {
                                Text(RecoveryStrings.text("recovery.otp.resend", locale: locale))
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(palette.primaryTextColor)
                                    .frame(minHeight: 44)
                                    .padding(.horizontal, 12)
                                    .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            .disabled(!viewModel.canResend)
                            .accessibilityIdentifier("otp.resendButton")
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .center)

                    Button {
                        viewModel.backToEdit()
                    } label: {
                        Text(RecoveryStrings.text("recovery.otp.backToEdit", locale: locale))
                            .font(.body)
                            .foregroundStyle(palette.primaryTextColor)
                            .frame(maxWidth: .infinity, minHeight: 44)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("otp.backToEditButton")
                }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("otp.screen")
        .task(id: resendRemaining > 0) {
            guard resendRemaining > 0 else { return }
            while !Task.isCancelled && viewModel.resendSecondsRemaining > 0 {
                try? await Task.sleep(nanoseconds: 500_000_000)
                viewModel.tick()
            }
        }
    }

    private var otpCodeInputSection: some View {
        let inputBackground = isRegularLayout ? palette.bgPageColor : palette.bgSubtleColor
        let digits = Array(viewModel.otpCode)

        return VStack(alignment: .leading, spacing: 8) {
            Text(RecoveryStrings.text("recovery.otp.codeLabel", locale: locale))
                .font(.footnote.weight(.semibold))
                .foregroundStyle(palette.textMutedColor)
                .padding(.leading, 4)

            ZStack {
                HStack(spacing: 8) {
                    ForEach(0..<RegistrationValidation.otpLength, id: \.self) { index in
                        let char = index < digits.count ? String(digits[index]) : ""
                        let isActiveBox = focusedField == .otp && min(digits.count, RegistrationValidation.otpLength - 1) == index

                        Text(char)
                            .font(.title2.weight(.semibold))
                            .monospacedDigit()
                            .foregroundStyle(palette.textColor)
                            .frame(maxWidth: .infinity, minHeight: 58)
                            .background(
                                RoundedRectangle(cornerRadius: 14, style: .continuous)
                                    .fill(inputBackground)
                            )
                            .overlay(
                                RoundedRectangle(cornerRadius: 14, style: .continuous)
                                    .strokeBorder(
                                        viewModel.otpCodeError != nil
                                            ? palette.dangerTextColor
                                            : (isActiveBox ? palette.primaryFillColor : palette.borderColor),
                                        lineWidth: (isActiveBox || viewModel.otpCodeError != nil) ? 1.5 : 1
                                    )
                            )
                    }
                }
                .allowsHitTesting(false)
                .accessibilityHidden(true)

                TextField(
                    "",
                    text: Binding(
                        get: { viewModel.otpCode },
                        set: { viewModel.updateOtpInput($0) }
                    )
                )
                .keyboardType(.numberPad)
                .textContentType(.oneTimeCode)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled(true)
                .submitLabel(.done)
                .focused($focusedField, equals: .otp)
                .onSubmit {
                    Task { await viewModel.submitOtp() }
                }
                .foregroundStyle(.clear)
                .tint(.clear)
                .frame(maxWidth: .infinity, minHeight: 58)
                .accessibilityLabel(RecoveryStrings.text("recovery.otp.codeLabel", locale: locale))
                .accessibilityValue(viewModel.otpCode)
                .accessibilityIdentifier("otp.codeField")
            }

            if let error = viewModel.otpCodeError {
                Text(error.message(locale: locale))
                    .font(.footnote)
                    .foregroundStyle(palette.dangerTextColor)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.leading, 4)
                    .accessibilityIdentifier("otp.codeError")
            }

            Text(RecoveryStrings.text("recovery.otp.footnote", locale: locale))
                .font(.footnote)
                .foregroundStyle(palette.textMutedColor)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.leading, 4)
        }
    }

    private func otpEmailDescription(email: String) -> Text {
        let prefix = RecoveryStrings.text("recovery.otp.sentToPrefix", locale: locale)
        let suffix = RecoveryStrings.text("recovery.otp.sentToSuffix", locale: locale)
        return Text("\(prefix)\(Text(email).fontWeight(.semibold).foregroundStyle(palette.textColor))\(suffix)")
    }
}
