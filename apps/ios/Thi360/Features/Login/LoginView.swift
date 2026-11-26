import SwiftUI
import UIKit

enum LoginLayoutMode: Equatable, Sendable {
    case phonePortrait
    case phoneCompactCover
    case padCard
    case phoneTwoPaneBook

    var accessibilityIdentifier: String {
        switch self {
        case .phonePortrait: "login.layout.phonePortrait"
        case .phoneCompactCover: "login.layout.phoneCompactCover"
        case .padCard: "login.layout.padCard"
        case .phoneTwoPaneBook: "login.layout.phoneTwoPaneBook"
        }
    }

    static func resolve(
        idiom: UIUserInterfaceIdiom,
        horizontalSizeClass: UserInterfaceSizeClass?,
        height: CGFloat
    ) -> LoginLayoutMode {
        if horizontalSizeClass == .regular {
            return idiom == .pad ? .padCard : .phoneTwoPaneBook
        }
        if idiom == .phone && height > 0 && height <= 700 {
            return .phoneCompactCover
        }
        return .phonePortrait
    }
}

struct LoginView: View {
    @Bindable var viewModel: LoginViewModel
    @Bindable var preferences: AppPreferences
    let idiom: UIUserInterfaceIdiom
    var onForgotPassword: () -> Void = {}
    var onRegister: () -> Void = {}
    var onBackToLock: (() -> Void)? = nil

    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.brandPalette) private var palette
    @FocusState private var focusedField: Field?
    @State private var stableContainerHeight: CGFloat = 0

    private enum Field: Hashable {
        case identifier
        case password
    }

    private var locale: AppLocale { preferences.locale }

    private var resolvedLayoutMode: LoginLayoutMode {
        LoginLayoutMode.resolve(
            idiom: idiom,
            horizontalSizeClass: horizontalSizeClass,
            height: stableContainerHeight
        )
    }

    var body: some View {
        Group {
            switch resolvedLayoutMode {
            case .phonePortrait:
                phoneCompactLayout(isCompactCover: false)
            case .phoneCompactCover:
                phoneCompactLayout(isCompactCover: true)
            case .padCard:
                padCardLayout(containerHeight: stableContainerHeight)
            case .phoneTwoPaneBook:
                phoneTwoPaneBookLayout
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background {
            // Measure container height ignoring keyboard insets so opening the keyboard never triggers a layout switch.
            GeometryReader { proxy in
                Color.clear
                    .onAppear {
                        stableContainerHeight = proxy.size.height
                    }
                    .onChange(of: proxy.size.height) { _, newHeight in
                        stableContainerHeight = newHeight
                    }
            }
            .ignoresSafeArea(.keyboard)
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("login.screen")
        .onAppear {
            Thi360AppDelegate.refreshSupportedOrientations(for: horizontalSizeClass)
        }
        .onChange(of: horizontalSizeClass) { _, newValue in
            Thi360AppDelegate.refreshSupportedOrientations(for: newValue)
        }
    }

    private func phoneCompactLayout(isCompactCover: Bool) -> some View {
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                if let onBackToLock {
                    RecoveryCircleNavButton(
                        systemName: "chevron.left",
                        accessibilityLabel: AccountStrings.text("lock.backToLockLabel", locale: locale),
                        accessibilityIdentifier: "lock.passwordFallback.backButton",
                        action: onBackToLock
                    )
                }
                if isCompactCover {
                    loginBrandBadge(compact: true)
                }
                Spacer(minLength: 0)
                languageMenuButton
            }
            .padding(.horizontal, 20)
            .padding(.top, 8)
            .padding(.bottom, 4)

            ScrollView {
                VStack(alignment: .leading, spacing: isCompactCover ? 20 : 28) {
                    if isCompactCover {
                        headingBlock(showsSubtitle: true)
                    } else {
                        VStack(alignment: .leading, spacing: 18) {
                            loginBrandBadge(compact: false)
                            headingBlock(showsSubtitle: true)
                        }
                    }

                    formFieldsAndActions(usesCardSurfaceInputs: false)

                    if onBackToLock == nil {
                        registerFooter
                            .padding(.top, isCompactCover ? 0 : 8)
                    }
                }
                .padding(.horizontal, isCompactCover ? 24 : 20)
                .padding(.top, isCompactCover ? 12 : 16)
                .padding(.bottom, isCompactCover ? 20 : 24)
            }
        }
        .background(palette.bgSurfaceColor.ignoresSafeArea())
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier(
            isCompactCover
                ? LoginLayoutMode.phoneCompactCover.accessibilityIdentifier
                : LoginLayoutMode.phonePortrait.accessibilityIdentifier
        )
    }

    private func padCardLayout(containerHeight: CGFloat) -> some View {
        VStack(spacing: 0) {
            HStack {
                if let onBackToLock {
                    RecoveryCircleNavButton(
                        systemName: "chevron.left",
                        accessibilityLabel: AccountStrings.text("lock.backToLockLabel", locale: locale),
                        accessibilityIdentifier: "lock.passwordFallback.backButton",
                        action: onBackToLock
                    )
                }
                Spacer(minLength: 0)
                languageMenuButton
            }
            .padding(.horizontal, 24)
            .padding(.top, 16)

            ScrollView {
                VStack {
                    VStack(alignment: .leading, spacing: 28) {
                        VStack(alignment: .leading, spacing: 18) {
                            loginBrandBadge(compact: false)
                            headingBlock(showsSubtitle: true)
                        }

                        formFieldsAndActions(usesCardSurfaceInputs: true)
                        if onBackToLock == nil {
                            registerFooter
                        }
                    }
                    .padding(36)
                    .frame(maxWidth: 480)
                    .background(
                        RoundedRectangle(cornerRadius: 28, style: .continuous)
                            .fill(palette.bgSurfaceColor)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 28, style: .continuous)
                            .strokeBorder(palette.borderColor, lineWidth: 1)
                    )
                    .shadow(color: Color.black.opacity(0.08), radius: 20, x: 0, y: 8)
                }
                .frame(maxWidth: .infinity, minHeight: max(0, containerHeight - 84))
                .padding(.horizontal, 24)
                .padding(.vertical, 20)
            }
        }
        .background(palette.bgPageColor.ignoresSafeArea())
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier(LoginLayoutMode.padCard.accessibilityIdentifier)
    }

    private var phoneTwoPaneBookLayout: some View {
        HStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    Spacer(minLength: 16)
                    loginBrandBadge(compact: false)

                    Text(AuthStrings.text("auth.login.headline", locale: locale))
                        .font(.title.weight(.bold))
                        .foregroundStyle(palette.textColor)
                        .fixedSize(horizontal: false, vertical: true)

                    VStack(alignment: .leading, spacing: 14) {
                        featureRow(key: "auth.login.feature1")
                        featureRow(key: "auth.login.feature2")
                        featureRow(key: "auth.login.feature3")
                    }
                    Spacer(minLength: 16)
                }
                .padding(.horizontal, 28)
                .padding(.vertical, 32)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(palette.primaryContainerColor)

            Rectangle()
                .fill(palette.borderColor)
                .frame(width: 1)
                .ignoresSafeArea()

            VStack(spacing: 0) {
                HStack {
                    if let onBackToLock {
                        RecoveryCircleNavButton(
                            systemName: "chevron.left",
                            accessibilityLabel: AccountStrings.text("lock.backToLockLabel", locale: locale),
                            accessibilityIdentifier: "lock.passwordFallback.backButton",
                            action: onBackToLock
                        )
                    }
                    Spacer(minLength: 0)
                    languageMenuButton
                }
                .padding(.horizontal, 24)
                .padding(.top, 16)

                ScrollView {
                    VStack(alignment: .leading, spacing: 24) {
                        headingBlock(showsSubtitle: false)
                        formFieldsAndActions(usesCardSurfaceInputs: false)
                        if onBackToLock == nil {
                            registerFooter
                        }
                    }
                    .padding(.horizontal, 28)
                    .padding(.vertical, 24)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(palette.bgSurfaceColor)
        }
        .background(palette.bgSurfaceColor.ignoresSafeArea())
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier(LoginLayoutMode.phoneTwoPaneBook.accessibilityIdentifier)
    }

    private func featureRow(key: String) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: "checkmark")
                .font(.subheadline.weight(.bold))
                .foregroundStyle(palette.primaryTextColor)
                .frame(width: 22, height: 22)
                .accessibilityHidden(true)

            Text(AuthStrings.text(key, locale: locale))
                .font(.body)
                .foregroundStyle(palette.textColor)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private func loginBrandBadge(compact: Bool) -> some View {
        HStack(spacing: compact ? 8 : 10) {
            ZStack {
                RoundedRectangle(cornerRadius: compact ? 8 : 12, style: .continuous)
                    .fill(palette.primaryFillColor)
                    .frame(width: compact ? 30 : 44, height: compact ? 30 : 44)
                Image(systemName: "checkmark.circle")
                    .font(.system(size: compact ? 16 : 22, weight: .semibold))
                    .foregroundStyle(palette.onPrimaryColor)
            }
            .accessibilityHidden(true)

            Text(ShellStrings.text("shell.app.name", locale: locale))
                .font((compact ? Font.headline : Font.title2).weight(.bold))
                .foregroundStyle(palette.textColor)
        }
    }

    private func headingBlock(showsSubtitle: Bool) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(AuthStrings.text("auth.login.title", locale: locale))
                .font(.largeTitle.weight(.bold))
                .foregroundStyle(palette.textColor)
                .accessibilityAddTraits(.isHeader)
                .accessibilityIdentifier("login.heading")

            if showsSubtitle {
                Text(AuthStrings.text("auth.login.headline", locale: locale))
                    .font(.subheadline)
                    .foregroundStyle(palette.textMutedColor)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private func formFieldsAndActions(usesCardSurfaceInputs: Bool) -> some View {
        let inputBackground = usesCardSurfaceInputs ? palette.bgPageColor : palette.bgSubtleColor

        return VStack(alignment: .leading, spacing: 16) {
            if let banner = viewModel.serverBanner {
                bannerView(banner)
            }

            VStack(alignment: .leading, spacing: 6) {
                Text(AuthStrings.text("auth.login.identifierLabel", locale: locale))
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(palette.textMutedColor)
                    .padding(.leading, 4)

                TextField(
                    AuthStrings.text("auth.login.identifierPlaceholder", locale: locale),
                    text: $viewModel.identifier
                )
                .textFieldStyle(.plain)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled(true)
                .textContentType(.username)
                .keyboardType(.emailAddress)
                .submitLabel(.next)
                .focused($focusedField, equals: .identifier)
                .onSubmit { focusedField = .password }
                .onChange(of: viewModel.identifier) { _, _ in
                    viewModel.clearIdentifierErrorIfNeeded()
                }
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
                            fieldBorderColor(hasError: viewModel.identifierError != nil, isFocused: focusedField == .identifier),
                            lineWidth: (focusedField == .identifier || viewModel.identifierError != nil) ? 1.5 : 1
                        )
                )
                .accessibilityLabel(AuthStrings.text("auth.login.identifierLabel", locale: locale))
                .accessibilityIdentifier("login.identifierField")

                if let error = viewModel.identifierError {
                    Text(error.message(locale: locale))
                        .font(.footnote)
                        .foregroundStyle(palette.dangerTextColor)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.leading, 4)
                        .accessibilityIdentifier("login.identifierError")
                }
            }

            VStack(alignment: .leading, spacing: 6) {
                Text(AuthStrings.text("auth.login.passwordLabel", locale: locale))
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(palette.textMutedColor)
                    .padding(.leading, 4)

                HStack(spacing: 4) {
                    Group {
                        if viewModel.isPasswordVisible {
                            TextField(
                                AuthStrings.text("auth.login.passwordPlaceholder", locale: locale),
                                text: $viewModel.password
                            )
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled(true)
                        } else {
                            SecureField(
                                AuthStrings.text("auth.login.passwordPlaceholder", locale: locale),
                                text: $viewModel.password
                            )
                        }
                    }
                    .textFieldStyle(.plain)
                    .textContentType(.password)
                    .submitLabel(.go)
                    .focused($focusedField, equals: .password)
                    .onSubmit {
                        Task { await viewModel.submit() }
                    }
                    .onChange(of: viewModel.password) { _, _ in
                        viewModel.clearPasswordErrorIfNeeded()
                    }
                    .padding(.leading, 16)
                    .frame(minHeight: 50)
                    .foregroundStyle(palette.textColor)
                    .accessibilityLabel(AuthStrings.text("auth.login.passwordLabel", locale: locale))
                    .accessibilityIdentifier("login.passwordField")

                    Button {
                        viewModel.togglePasswordVisibility()
                    } label: {
                        Image(systemName: viewModel.isPasswordVisible ? "eye.slash" : "eye")
                            .font(.system(size: 17, weight: .medium))
                            .foregroundStyle(palette.textMutedColor)
                            .frame(minWidth: 44, minHeight: 44)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(
                        AuthStrings.text(
                            viewModel.isPasswordVisible ? "auth.login.hidePassword" : "auth.login.showPassword",
                            locale: locale
                        )
                    )
                    .accessibilityIdentifier("login.passwordToggle")
                }
                .background(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(inputBackground)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .strokeBorder(
                            fieldBorderColor(hasError: viewModel.passwordError != nil, isFocused: focusedField == .password),
                            lineWidth: (focusedField == .password || viewModel.passwordError != nil) ? 1.5 : 1
                        )
                )

                if let error = viewModel.passwordError {
                    Text(error.message(locale: locale))
                        .font(.footnote)
                        .foregroundStyle(palette.dangerTextColor)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.leading, 4)
                        .accessibilityIdentifier("login.passwordError")
                }
            }

            HStack {
                Spacer(minLength: 0)
                Button(action: onForgotPassword) {
                    Text(AuthStrings.text("auth.login.forgotLink", locale: locale))
                        .font(.subheadline)
                        .foregroundStyle(palette.primaryTextColor)
                        .frame(minHeight: 44)
                        .padding(.horizontal, 4)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("login.forgotPasswordButton")
            }
            .padding(.top, -6)

            Button {
                focusedField = nil
                Task { await viewModel.submit() }
            } label: {
                HStack(spacing: 8) {
                    if viewModel.isSubmitting {
                        ProgressView()
                            .tint(palette.onPrimaryColor)
                    }
                    Text(
                        AuthStrings.text(
                            viewModel.isSubmitting ? "auth.login.submitting" : "auth.login.submit",
                            locale: locale
                        )
                    )
                    .font(.body.weight(.semibold))
                }
                .foregroundStyle(palette.onPrimaryColor)
                .frame(maxWidth: .infinity, minHeight: 50)
                .padding(.horizontal, 16)
                .background(
                    Capsule(style: .continuous)
                        .fill(palette.primaryFillColor)
                        .opacity(viewModel.isSubmitting ? 0.72 : 1.0)
                )
                .contentShape(Capsule(style: .continuous))
            }
            .buttonStyle(.plain)
            .disabled(viewModel.isSubmitting)
            .accessibilityIdentifier("login.submitButton")
        }
    }

    private func bannerView(_ banner: LoginBannerKind) -> some View {
        let textColor = banner.isDestructive ? palette.dangerTextColor : palette.primaryTextColor
        let bgColor = banner.isDestructive ? palette.dangerBgColor : palette.primaryContainerColor
        let iconName = banner.isDestructive ? "exclamationmark.circle" : "checkmark.circle"

        return HStack(alignment: .top, spacing: 10) {
            Image(systemName: iconName)
                .font(.body.weight(.medium))
                .foregroundStyle(textColor)
                .accessibilityHidden(true)

            Text(banner.message(locale: locale))
                .font(.subheadline)
                .foregroundStyle(textColor)
                .fixedSize(horizontal: false, vertical: true)

            Spacer(minLength: 0)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(bgColor)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .strokeBorder(textColor.opacity(0.25), lineWidth: 1)
        )
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("login.bannerMessage")
    }

    private var registerFooter: some View {
        HStack(spacing: 4) {
            Text(AuthStrings.text("auth.login.noAccount", locale: locale))
                .font(.subheadline)
                .foregroundStyle(palette.textMutedColor)

            Button(action: onRegister) {
                Text(AuthStrings.text("auth.login.registerLink", locale: locale))
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(palette.primaryTextColor)
                    .frame(minHeight: 44)
                    .padding(.horizontal, 6)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("login.registerButton")
        }
        .frame(maxWidth: .infinity, alignment: .center)
    }

    private var languageMenuButton: some View {
        Menu {
            Button {
                preferences.locale = .vi
            } label: {
                Label(
                    AuthStrings.text("auth.login.languageVi", locale: locale),
                    systemImage: preferences.locale == .vi ? "checkmark" : ""
                )
            }
            .accessibilityIdentifier("login.language.vi")

            Button {
                preferences.locale = .en
            } label: {
                Label(
                    AuthStrings.text("auth.login.languageEn", locale: locale),
                    systemImage: preferences.locale == .en ? "checkmark" : ""
                )
            }
            .accessibilityIdentifier("login.language.en")
        } label: {
            Image(systemName: "globe")
                .font(.system(size: 18, weight: .medium))
                .foregroundStyle(palette.textColor)
                .frame(width: 44, height: 44)
                .background(
                    Circle()
                        .fill(palette.bgSubtleColor)
                )
                .overlay(
                    Circle()
                        .strokeBorder(palette.borderColor, lineWidth: 1)
                )
                .contentShape(Circle())
        }
        .accessibilityLabel(AuthStrings.text("auth.login.changeLanguage", locale: locale))
        .accessibilityValue(
            AuthStrings.text(
                preferences.locale == .vi ? "auth.login.languageVi" : "auth.login.languageEn",
                locale: locale
            )
        )
        .accessibilityIdentifier("login.languageButton")
    }

    private func fieldBorderColor(hasError: Bool, isFocused: Bool) -> Color {
        if hasError {
            return palette.dangerTextColor
        }
        if isFocused {
            return palette.primaryFillColor
        }
        return palette.borderColor
    }
}
