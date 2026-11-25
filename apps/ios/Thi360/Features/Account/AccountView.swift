import SwiftUI

struct AccountView: View {
    @Bindable var viewModel: AccountViewModel
    @Bindable var router: AppRouter
    @Bindable var preferences: AppPreferences
    let effectiveSizeClass: UserInterfaceSizeClass?
    let serverDefaultThemeMode: ThemeMode
    var isTwoPaneBook: Bool = false
    var showsInlineProbe: Bool = true
    var onOpenChangePassword: () -> Void

    @Environment(\.brandPalette) private var palette

    private var locale: AppLocale { preferences.locale }

    private var effectiveTheme: ThemeMode {
        preferences.effectiveThemeMode(serverDefault: serverDefaultThemeMode)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                Text(ShellStrings.text("shell.tab.account", locale: locale))
                    .font(.largeTitle.weight(.bold))
                    .foregroundStyle(palette.textColor)
                    .accessibilityAddTraits(.isHeader)
                    .accessibilityIdentifier("shell.account.heading")

                if showsInlineProbe && router.stateProbeEnabled {
                    StateRestorationProbeCard(
                        router: router,
                        locale: locale,
                        effectiveSizeClass: effectiveSizeClass
                    )
                }

                profileSection

                securitySection

                displaySection

                logoutSection
            }
            .frame(maxWidth: isTwoPaneBook ? .infinity : 620, alignment: .leading)
            .frame(maxWidth: .infinity, alignment: .center)
            .padding(20)
        }
        .background(palette.bgPageColor.ignoresSafeArea())
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("shell.account.view")
        .task {
            await viewModel.loadProfileIfNeeded()
        }
        .alert(
            AccountStrings.text("account.logout.confirmTitle", locale: locale),
            isPresented: $viewModel.isShowingLogoutConfirmation
        ) {
            Button(
                AccountStrings.text("account.logout.confirmAction", locale: locale),
                role: .destructive
            ) {
                Task { await viewModel.confirmLogout() }
            }
            .accessibilityIdentifier("account.logout.confirmButton")

            Button(
                AccountStrings.text("account.logout.cancelAction", locale: locale),
                role: .cancel
            ) {
                viewModel.cancelLogout()
            }
            .accessibilityIdentifier("account.logout.cancelButton")
        } message: {
            Text(AccountStrings.text("account.logout.confirmMessage", locale: locale))
        }
    }

    @ViewBuilder
    private var profileSection: some View {
        Group {
            switch viewModel.profileState {
            case .loading:
                profileLoadingCard
            case let .loaded(profile):
                profileLoadedCard(profile)
            case .offline:
                profileFailureCard(
                    icon: "wifi.slash",
                    message: AccountStrings.text("account.profile.offline", locale: locale),
                    identifier: "account.profile.offline"
                )
            case .error:
                profileFailureCard(
                    icon: "exclamationmark.triangle",
                    message: AccountStrings.text("account.profile.error", locale: locale),
                    identifier: "account.profile.error"
                )
            }
        }
    }

    private var profileLoadingCard: some View {
        HStack(spacing: 14) {
            Circle()
                .fill(palette.bgSubtleColor)
                .frame(width: isTwoPaneBook ? 48 : 56, height: isTwoPaneBook ? 48 : 56)
                .overlay {
                    ProgressView()
                        .tint(palette.primaryTextColor)
                }

            VStack(alignment: .leading, spacing: 6) {
                RoundedRectangle(cornerRadius: 6, style: .continuous)
                    .fill(palette.bgSubtleColor)
                    .frame(width: 150, height: 16)
                Text(AccountStrings.text("account.profile.loading", locale: locale))
                    .font(.footnote)
                    .foregroundStyle(palette.textMutedColor)
            }

            Spacer(minLength: 0)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(cardSurface)
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("account.profile.loading")
    }

    private func profileLoadedCard(_ profile: AccountProfile) -> some View {
        let avatarSize: CGFloat = isTwoPaneBook ? 48 : 56

        return HStack(alignment: .center, spacing: 14) {
            ZStack {
                Circle()
                    .fill(palette.primaryContainerStrongColor)
                    .frame(width: avatarSize, height: avatarSize)
                Text(profile.initials)
                    .font(.system(size: isTwoPaneBook ? 17 : 20, weight: .semibold))
                    .foregroundStyle(palette.primaryTextColor)
            }
            .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 4) {
                Text(profile.displayName)
                    .font(.headline)
                    .foregroundStyle(palette.textColor)
                    .lineLimit(2)
                    .accessibilityIdentifier("account.profile.fullName")

                Text(profile.username)
                    .font(.footnote)
                    .foregroundStyle(palette.textMutedColor)
                    .lineLimit(1)
                    .accessibilityIdentifier("account.profile.username")

                if let email = profile.trimmedEmail {
                    Text(email)
                        .font(.footnote)
                        .foregroundStyle(palette.textMutedColor)
                        .lineLimit(2)
                        .accessibilityIdentifier("account.profile.email")

                    HStack(spacing: 4) {
                        Image(systemName: profile.emailVerified ? "checkmark.circle.fill" : "exclamationmark.circle")
                            .font(.caption.weight(.semibold))
                            .accessibilityHidden(true)
                        Text(
                            AccountStrings.text(
                                profile.emailVerified ? "account.profile.emailVerified" : "account.profile.emailUnverified",
                                locale: locale
                            )
                        )
                        .font(.footnote.weight(.semibold))
                    }
                    .foregroundStyle(profile.emailVerified ? palette.primaryTextColor : palette.textMutedColor)
                    .accessibilityIdentifier("account.profile.emailStatus")
                }
            }

            Spacer(minLength: 0)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(cardSurface)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("account.profile.loaded")
    }

    private func profileFailureCard(icon: String, message: String, identifier: String) -> some View {
        HStack(alignment: .center, spacing: 12) {
            Image(systemName: icon)
                .font(.title3.weight(.semibold))
                .foregroundStyle(palette.dangerTextColor)
                .frame(width: 32, height: 32)
                .accessibilityHidden(true)

            Text(message)
                .font(.subheadline)
                .foregroundStyle(palette.textColor)
                .fixedSize(horizontal: false, vertical: true)

            Spacer(minLength: 8)

            Button {
                Task { await viewModel.loadProfile() }
            } label: {
                Text(AccountStrings.text("account.profile.retry", locale: locale))
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(palette.primaryTextColor)
                    .padding(.horizontal, 12)
                    .frame(minWidth: 44, minHeight: 44)
                    .background(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .fill(palette.primaryContainerColor)
                    )
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("account.profile.retryButton")
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(cardSurface)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier(identifier)
    }

    private var securitySection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(AccountStrings.text("account.section.security", locale: locale))
                .font(.footnote.weight(.semibold))
                .foregroundStyle(palette.textMutedColor)
                .padding(.horizontal, 12)

            VStack(spacing: 0) {
                changePasswordRow
                Divider()
                    .overlay(palette.borderColor)
                    .padding(.leading, 58)
                biometricToggleRow
            }
            .background(cardSurface)

            if let feedback = viewModel.biometricFeedbackMessage(locale: locale) {
                Text(feedback)
                    .font(.footnote)
                    .foregroundStyle(palette.dangerTextColor)
                    .padding(.horizontal, 12)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("account.biometric.feedback")
            }
        }
    }

    private var changePasswordRow: some View {
        let isHighlighted = isTwoPaneBook && viewModel.selectedDetail == .changePassword

        return Button {
            viewModel.selectedDetail = .changePassword
            onOpenChangePassword()
        } label: {
            HStack(spacing: 12) {
                settingIconTile(systemName: "key.fill")

                Text(AccountStrings.text("account.changePassword", locale: locale))
                    .font(.body.weight(isHighlighted ? .semibold : .regular))
                    .foregroundStyle(palette.textColor)
                    .lineLimit(2)

                Spacer(minLength: 8)

                Image(systemName: "chevron.right")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(palette.textMutedColor)
                    .accessibilityHidden(true)
            }
            .frame(minHeight: 52)
            .padding(.horizontal, 16)
            .padding(.vertical, 6)
            .background(isHighlighted ? palette.bgSubtleColor : Color.clear)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isHighlighted ? [.isSelected] : [])
        .accessibilityIdentifier("account.changePasswordButton")
    }

    private var biometricToggleRow: some View {
        let toggleBinding = Binding<Bool>(
            get: { viewModel.isBiometricSwitchOn },
            set: { newValue in
                Task {
                    await viewModel.setBiometricEnabled(newValue, locale: locale)
                }
            }
        )

        return HStack(spacing: 12) {
            settingIconTile(systemName: viewModel.biometricManager.capability.kind.symbolName)

            VStack(alignment: .leading, spacing: 2) {
                Text(viewModel.biometricTitle(locale: locale))
                    .font(.body)
                    .foregroundStyle(viewModel.isBiometricSwitchDisabled ? palette.textMutedColor : palette.textColor)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("account.biometric.title")

                Text(viewModel.biometricSubtitle(locale: locale))
                    .font(.footnote)
                    .foregroundStyle(palette.textMutedColor)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("account.biometric.subtitle")
            }

            Spacer(minLength: 8)

            Toggle("", isOn: toggleBinding)
                .labelsHidden()
                .tint(palette.primaryFillColor)
                .disabled(viewModel.isBiometricSwitchDisabled)
                .accessibilityLabel(viewModel.biometricTitle(locale: locale))
                .accessibilityIdentifier("account.biometricToggle")
        }
        .frame(minHeight: 52)
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("account.biometricRow")
    }

    private var displaySection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(ShellStrings.text("shell.account.sectionDisplay", locale: locale))
                .font(.footnote.weight(.semibold))
                .foregroundStyle(palette.textMutedColor)
                .padding(.horizontal, 12)

            VStack(spacing: 0) {
                languageRow
                Divider()
                    .overlay(palette.borderColor)
                    .padding(.leading, 58)
                appearanceRow
            }
            .background(cardSurface)
        }
    }

    private var languageRow: some View {
        let picker = HStack(spacing: 4) {
            segmentedOptionButton(
                title: ShellStrings.text("shell.account.languageVi", locale: locale),
                isSelected: preferences.locale == .vi,
                identifier: "shell.account.locale.vi"
            ) {
                preferences.locale = .vi
            }
            segmentedOptionButton(
                title: ShellStrings.text("shell.account.languageEn", locale: locale),
                isSelected: preferences.locale == .en,
                identifier: "shell.account.locale.en"
            ) {
                preferences.locale = .en
            }
        }
        .padding(3)
        .background(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(palette.bgSubtleColor)
        )
        .fixedSize(horizontal: true, vertical: false)

        return ViewThatFits(in: .horizontal) {
            HStack(spacing: 12) {
                settingIconTile(systemName: "globe")
                Text(ShellStrings.text("shell.account.language", locale: locale))
                    .font(.body)
                    .foregroundStyle(palette.textColor)
                    .fixedSize(horizontal: true, vertical: false)
                Spacer(minLength: 8)
                picker
            }

            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 12) {
                    settingIconTile(systemName: "globe")
                    Text(ShellStrings.text("shell.account.language", locale: locale))
                        .font(.body)
                        .foregroundStyle(palette.textColor)
                        .lineLimit(2)
                    Spacer(minLength: 0)
                }
                HStack {
                    Spacer(minLength: 0)
                    picker
                }
            }
        }
        .frame(minHeight: 52)
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
    }

    private var appearanceRow: some View {
        let picker = HStack(spacing: 4) {
            segmentedOptionButton(
                title: ShellStrings.text("shell.account.appearanceLight", locale: locale),
                isSelected: effectiveTheme == .light,
                identifier: "shell.account.theme.light"
            ) {
                preferences.themeMode = .light
            }
            segmentedOptionButton(
                title: ShellStrings.text("shell.account.appearanceDark", locale: locale),
                isSelected: effectiveTheme == .dark,
                identifier: "shell.account.theme.dark"
            ) {
                preferences.themeMode = .dark
            }
        }
        .padding(3)
        .background(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(palette.bgSubtleColor)
        )
        .fixedSize(horizontal: true, vertical: false)

        return ViewThatFits(in: .horizontal) {
            HStack(spacing: 12) {
                settingIconTile(systemName: effectiveTheme == .dark ? "moon.fill" : "sun.max.fill")
                Text(ShellStrings.text("shell.account.appearance", locale: locale))
                    .font(.body)
                    .foregroundStyle(palette.textColor)
                    .fixedSize(horizontal: true, vertical: false)
                Spacer(minLength: 8)
                picker
            }

            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 12) {
                    settingIconTile(systemName: effectiveTheme == .dark ? "moon.fill" : "sun.max.fill")
                    Text(ShellStrings.text("shell.account.appearance", locale: locale))
                        .font(.body)
                        .foregroundStyle(palette.textColor)
                        .lineLimit(2)
                    Spacer(minLength: 0)
                }
                HStack {
                    Spacer(minLength: 0)
                    picker
                }
            }
        }
        .frame(minHeight: 52)
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
    }

    private var logoutSection: some View {
        Button {
            viewModel.requestLogout()
        } label: {
            HStack(spacing: 8) {
                if viewModel.isLoggingOut {
                    ProgressView()
                        .tint(palette.dangerTextColor)
                }
                Text(AccountStrings.text("account.logout.button", locale: locale))
                    .font(.body.weight(.semibold))
                    .foregroundStyle(palette.dangerTextColor)
            }
            .frame(maxWidth: .infinity, minHeight: 52)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(viewModel.isLoggingOut)
        .background(cardSurface)
        .accessibilityIdentifier("account.logoutButton")
    }

    private var cardSurface: some View {
        RoundedRectangle(cornerRadius: 20, style: .continuous)
            .fill(palette.bgSurfaceColor)
            .overlay(
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .strokeBorder(palette.borderColor, lineWidth: 1)
            )
    }

    private func settingIconTile(systemName: String) -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .fill(palette.primaryContainerStrongColor)
                .frame(width: 30, height: 30)
            Image(systemName: systemName)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(palette.primaryTextColor)
        }
        .accessibilityHidden(true)
    }

    private func segmentedOptionButton(
        title: String,
        isSelected: Bool,
        identifier: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Text(title)
                .font(.footnote.weight(isSelected ? .semibold : .medium))
                .lineLimit(1)
                .fixedSize(horizontal: true, vertical: false)
                .foregroundStyle(isSelected ? palette.onPrimaryColor : palette.textSecondaryColor)
                .padding(.horizontal, 10)
                .frame(minWidth: 44, minHeight: 38)
                .background(
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .fill(isSelected ? palette.primaryFillColor : Color.clear)
                )
                .frame(minWidth: 44, minHeight: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? [.isSelected] : [])
        .accessibilityIdentifier(identifier)
    }
}

struct AccountDuoDetailView: View {
    @Bindable var router: AppRouter
    @Bindable var preferences: AppPreferences
    let sessionStore: SessionStore
    let effectiveSizeClass: UserInterfaceSizeClass?

    @Environment(\.brandPalette) private var palette

    var body: some View {
        VStack(spacing: 0) {
            if router.stateProbeEnabled {
                StateRestorationProbeCard(
                    router: router,
                    locale: preferences.locale,
                    effectiveSizeClass: effectiveSizeClass
                )
                .padding(.horizontal, 20)
                .padding(.top, 16)
            }

            NewPasswordView(
                viewModel: router.voluntaryChangePasswordViewModel(sessionStore: sessionStore),
                preferences: preferences,
                isEmbeddedPane: true
            )
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(palette.bgPageColor.ignoresSafeArea())
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("shell.account.detailPane")
    }
}
