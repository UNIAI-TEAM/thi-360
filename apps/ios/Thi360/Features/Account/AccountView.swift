import SwiftUI

struct AccountView: View {
    @Bindable var viewModel: AccountViewModel
    @Bindable var router: AppRouter
    @Bindable var preferences: AppPreferences
    let effectiveSizeClass: UserInterfaceSizeClass?
    let serverDefaultThemeMode: ThemeMode
    var isTwoPaneBook: Bool = false
    var showsInlineProbe: Bool = true
    var onOpenProfile: (() -> Void)? = nil
    var onOpenOrders: (() -> Void)? = nil
    var onOpenLegal: ((LegalId) -> Void)? = nil
    var onOpenDeleteAccount: (() -> Void)? = nil
    var onOpenChangePassword: () -> Void

    @Environment(\.brandPalette) private var palette
    @Environment(\.statusPalette) private var statusPalette
    @State private var avatarReloadToken: Int = 0
    @State private var didDisappearOnce: Bool = false

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

                accountQuickLinksSection

                securitySection

                displaySection

                legalSection

                logoutSection

                deleteAccountSection
            }
            .frame(maxWidth: isTwoPaneBook ? .infinity : 620, alignment: .leading)
            .frame(maxWidth: .infinity, alignment: .center)
            .padding(20)
        }
        .background(palette.bgPageColor.ignoresSafeArea())
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("shell.account.view")
        .accountProbe(id: "shell.account.view")
        .onAppear {
            if didDisappearOnce {
                didDisappearOnce = false
                avatarReloadToken &+= 1
            }
        }
        .onDisappear {
            didDisappearOnce = true
        }
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

    private func triggerOpenProfile() {
        viewModel.selectDetail(.profile)
        if let onOpenProfile {
            onOpenProfile()
        } else if !isTwoPaneBook {
            router.openProfile()
        }
    }

    private func triggerOpenOrders() {
        viewModel.selectDetail(.orders)
        if let onOpenOrders {
            onOpenOrders()
        } else if !isTwoPaneBook {
            router.openOrders()
        }
    }

    private func triggerOpenLegal(_ legalId: LegalId) {
        viewModel.selectDetail(legalId == .terms ? .legalTerms : .legalPrivacy)
        if let onOpenLegal {
            onOpenLegal(legalId)
        } else if !isTwoPaneBook {
            router.openLegal(legalId)
        }
    }

    private func triggerOpenDeleteAccount() {
        viewModel.selectDetail(.deleteAccount)
        if let onOpenDeleteAccount {
            onOpenDeleteAccount()
        } else if !isTwoPaneBook {
            router.openDeleteAccount()
        }
    }

    @ViewBuilder
    private var profileSection: some View {
        Group {
            switch viewModel.profileState {
            case .loading:
                profileLoadingCard
            case let .loaded(profile, _):
                profileLoadedCard(profile)
            case let .failed(kind, _, _) where kind == .offline:
                profileFailureCard(
                    icon: "wifi.slash",
                    message: AccountStrings.text("account.profile.offline", locale: locale),
                    identifier: "account.profile.offline"
                )
            case .empty, .failed:
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
            AvatarCircleView(
                avatarUrl: profile.trimmedAvatarUrl,
                initials: profile.initials,
                size: avatarSize,
                fontSize: isTwoPaneBook ? 17 : 20,
                locale: locale,
                exposeAvatarAccessibility: false,
                reloadToken: avatarReloadToken
            )

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
                    .foregroundStyle(profile.emailVerified ? palette.primaryTextColor : statusPalette.warningColor)
                    .accessibilityElement(children: .combine)
                    .accessibilityIdentifier("account.profile.emailStatus")
                }
            }

            Spacer(minLength: 0)

            if !isTwoPaneBook {
                Button {
                    triggerOpenProfile()
                } label: {
                    Image(systemName: "chevron.right")
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(palette.textMutedColor)
                        .frame(minWidth: 44, minHeight: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(ProfileStrings.text("header.nav.profile", locale: locale))
                .accessibilityIdentifier("account.profile.openButton")
            }
        }
        .frame(maxWidth: .infinity, minHeight: 56, alignment: .leading)
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(cardSurface)
        .contentShape(Rectangle())
        .onTapGesture {
            triggerOpenProfile()
        }
        .accessibilityElement(children: .contain)
        .accessibilityAction(named: ProfileStrings.text("header.nav.profile", locale: locale)) {
            triggerOpenProfile()
        }
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

    private var accountQuickLinksSection: some View {
        VStack(spacing: 0) {
            navigationRow(
                icon: "person.crop.circle",
                title: ProfileStrings.text("header.nav.profile", locale: locale),
                selection: .profile,
                identifier: "account.profileRow"
            ) {
                triggerOpenProfile()
            }

            Divider()
                .overlay(palette.borderColor)
                .padding(.leading, 58)

            navigationRow(
                icon: "doc.text.fill",
                title: ProfileStrings.text("header.nav.orders", locale: locale),
                selection: .orders,
                identifier: "account.ordersRow"
            ) {
                triggerOpenOrders()
            }
        }
        .background(cardSurface)
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
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
            .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))

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
        navigationRow(
            icon: "key.fill",
            title: AccountStrings.text("account.changePassword", locale: locale),
            selection: .changePassword,
            identifier: "account.changePasswordButton"
        ) {
            viewModel.selectDetail(.changePassword)
            onOpenChangePassword()
        }
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

    private var legalSection: some View {
        VStack(spacing: 0) {
            navigationRow(
                icon: "doc.plaintext",
                title: AppStrings.legal("legal.terms.title", locale: locale),
                selection: .legalTerms,
                identifier: "account.legalTermsRow"
            ) {
                triggerOpenLegal(.terms)
            }

            Divider()
                .overlay(palette.borderColor)
                .padding(.leading, 58)

            navigationRow(
                icon: "hand.raised.fill",
                title: AppStrings.legal("legal.privacy.title", locale: locale),
                selection: .legalPrivacy,
                identifier: "account.legalPrivacyRow"
            ) {
                triggerOpenLegal(.privacy)
            }
        }
        .background(cardSurface)
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
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
        .accountProbe(
            id: "account.logoutButton",
            label: AccountStrings.text("account.logout.button", locale: locale)
        )
    }

    private var deleteAccountSection: some View {
        let isHighlighted = isTwoPaneBook && viewModel.selectedDetail == .deleteAccount
        let title = ProfileStrings.text("account.deletion.title", locale: locale)
        return Button {
            triggerOpenDeleteAccount()
        } label: {
            HStack(spacing: 8) {
                Text(title)
                    .font(.body.weight(.semibold))
                    .foregroundStyle(palette.dangerTextColor)
                    .lineLimit(2)
            }
            .frame(maxWidth: .infinity, minHeight: 52)
            .background(isHighlighted ? palette.dangerBgColor : Color.clear)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .background(cardSurface)
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .accessibilityAddTraits(isHighlighted ? [.isSelected] : [])
        .accessibilityIdentifier("account.deleteAccountRow")
        .accountProbe(id: "account.deleteAccountRow", label: title)
    }

    private func navigationRow(
        icon: String,
        title: String,
        selection: AccountDetailSelection,
        identifier: String,
        action: @escaping () -> Void
    ) -> some View {
        let isHighlighted = isTwoPaneBook && viewModel.selectedDetail == selection

        return Button(action: action) {
            HStack(spacing: 12) {
                settingIconTile(systemName: icon)

                Text(title)
                    .font(.body.weight(isHighlighted ? .semibold : .regular))
                    .foregroundStyle(palette.textColor)
                    .multilineTextAlignment(.leading)
                    .fixedSize(horizontal: false, vertical: true)

                Spacer(minLength: 8)

                if !isTwoPaneBook {
                    Image(systemName: "chevron.right")
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(palette.textMutedColor)
                        .accessibilityHidden(true)
                }
            }
            .frame(minHeight: 52)
            .padding(.horizontal, 16)
            .padding(.vertical, 6)
            .background(isHighlighted ? palette.bgSubtleColor : Color.clear)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isHighlighted ? [.isSelected] : [])
        .accessibilityIdentifier(identifier)
        .accountProbe(id: identifier, label: title)
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

struct AccountDetailPaneView: View {
    @Bindable var accountViewModel: AccountViewModel
    @Bindable var router: AppRouter
    @Bindable var preferences: AppPreferences
    let sessionStore: SessionStore
    let effectiveSizeClass: UserInterfaceSizeClass?
    var legalRepository: LegalRepository = LegalRepository()
    var webBaseURL: URL = AppEnvironment.current.webBaseURL

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

            detailContent
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(palette.bgPageColor.ignoresSafeArea())
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("shell.account.detailPane")
    }

    @ViewBuilder
    private var detailContent: some View {
        switch accountViewModel.selectedDetail {
        case .profile:
            ProfileView(
                viewModel: accountViewModel.ensureProfileViewModel(),
                preferences: preferences,
                isEmbeddedPane: true,
                onVerifyEmail: {
                    router.openProfileVerifyEmail()
                }
            )
        case .orders:
            AppRouteView(
                route: .orders,
                destination: .account,
                router: router,
                preferences: preferences,
                legalRepository: legalRepository,
                webBaseURL: webBaseURL
            )
            .accountProbe(
                id: "account.detail.orders",
                label: ProfileStrings.text("header.nav.orders", locale: preferences.locale)
            )
        case .changePassword:
            NewPasswordView(
                viewModel: router.voluntaryChangePasswordViewModel(sessionStore: sessionStore),
                preferences: preferences,
                isEmbeddedPane: true
            )
        case .legalTerms:
            AppRouteView(
                route: .legal(.terms),
                destination: .account,
                router: router,
                preferences: preferences,
                legalRepository: legalRepository,
                webBaseURL: webBaseURL
            )
            .accountProbe(
                id: "account.detail.legalTerms",
                label: AppStrings.legal("legal.terms.title", locale: preferences.locale)
            )
        case .legalPrivacy:
            AppRouteView(
                route: .legal(.privacy),
                destination: .account,
                router: router,
                preferences: preferences,
                legalRepository: legalRepository,
                webBaseURL: webBaseURL
            )
            .accountProbe(
                id: "account.detail.legalPrivacy",
                label: AppStrings.legal("legal.privacy.title", locale: preferences.locale)
            )
        case .deleteAccount:
            DeleteAccountView(
                viewModel: accountViewModel.ensureDeleteAccountViewModel(),
                preferences: preferences,
                isEmbeddedPane: true,
                onOpenMyExams: {
                    router.switchTab(.myExams)
                },
                onOpenOrders: {
                    router.openOrders()
                }
            )
        }
    }
}

struct AccountDuoDetailView: View {
    @Bindable var accountViewModel: AccountViewModel
    @Bindable var router: AppRouter
    @Bindable var preferences: AppPreferences
    let sessionStore: SessionStore
    let effectiveSizeClass: UserInterfaceSizeClass?
    var legalRepository: LegalRepository = LegalRepository()
    var webBaseURL: URL = AppEnvironment.current.webBaseURL

    var body: some View {
        AccountDetailPaneView(
            accountViewModel: accountViewModel,
            router: router,
            preferences: preferences,
            sessionStore: sessionStore,
            effectiveSizeClass: effectiveSizeClass,
            legalRepository: legalRepository,
            webBaseURL: webBaseURL
        )
    }
}
