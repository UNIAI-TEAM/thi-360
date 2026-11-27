import SwiftUI
import UIKit

struct AdaptiveShellView: View {
    @Bindable var router: AppRouter
    @Bindable var preferences: AppPreferences
    let idiom: UIUserInterfaceIdiom
    var sessionStore: SessionStore? = nil
    var accountViewModel: AccountViewModel? = nil
    var serverDefaultThemeMode: ThemeMode = .light
    var legalRepository: LegalRepository = LegalRepository()
    var webBaseURL: URL = AppEnvironment.current.webBaseURL

    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.brandPalette) private var palette
    @Environment(\.accountInspector) private var accountInspector

    private var effectiveSizeClass: UserInterfaceSizeClass? {
        if router.stateProbeEnabled, let forced = router.forcedSizeClassForUITest {
            return forced
        }
        return horizontalSizeClass
    }

    var layoutMode: ShellLayoutMode {
        ShellLayoutResolver.resolve(idiom: idiom, horizontalSizeClass: effectiveSizeClass)
    }

    var body: some View {
        GeometryReader { proxy in
            let widthClass = ShellWidth.resolve(
                horizontalSizeClass: effectiveSizeClass,
                layoutMode: layoutMode,
                availableWidth: proxy.size.width
            )
            Group {
                switch layoutMode {
                case .compactTabBar:
                    compactTabShell
                case .iPadSidebar:
                    iPadSidebarShell
                case .phoneTwoPaneBook:
                    phoneTwoPaneBookShell(totalWidth: proxy.size.width, fullWidthClass: widthClass)
                }
            }
            .environment(\.shellWidth, widthClass)
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier(layoutMode.accessibilityIdentifier)
        .onAppear {
            Thi360AppDelegate.refreshSupportedOrientations(for: effectiveSizeClass)
            accountViewModel?.isInTwoPaneLayout = (layoutMode == .iPadSidebar || layoutMode == .phoneTwoPaneBook)
        }
        .onChange(of: effectiveSizeClass) { _, newValue in
            Thi360AppDelegate.refreshSupportedOrientations(for: newValue)
        }
        .onChange(of: layoutMode) { oldMode, newMode in
            syncAccountNavigationOnLayoutTransition(from: oldMode, to: newMode)
        }
        .onChange(of: router.selectedDestination) { oldDestination, newDestination in
            if oldDestination == .account,
               newDestination != .account,
               let accountViewModel,
               layoutMode == .iPadSidebar || layoutMode == .phoneTwoPaneBook
            {
                accountViewModel.handleAccountTabExitInTwoPane()
            }
        }
    }

    private var compactTabShell: some View {
        TabView(selection: $router.selectedDestination) {
            ForEach(Destination.allCases) { destination in
                NavigationStack(path: router.pathBinding(for: destination)) {
                    destinationContent(for: destination, showsBrandHeader: destination == .home)
                        .id(router.reloadCount(for: destination))
                        .navigationDestination(for: AppRoute.self) { route in
                            AppRouteView(
                                route: route,
                                destination: destination,
                                router: router,
                                preferences: preferences,
                                legalRepository: legalRepository,
                                webBaseURL: webBaseURL
                            )
                        }
                }
                .tabItem {
                    Label(
                        ShellStrings.text(destination.titleKey, locale: preferences.locale),
                        systemImage: destination.symbolName
                    )
                }
                .tag(destination)
            }
        }
        .toolbarBackground(palette.bgSurfaceColor, for: .tabBar)
        .toolbarBackground(.visible, for: .tabBar)
    }

    private var iPadSidebarShell: some View {
        NavigationSplitView(columnVisibility: .constant(.all)) {
            VStack(alignment: .leading, spacing: 8) {
                BrandWordmarkHeader(locale: preferences.locale)
                    .padding(.horizontal, 16)
                    .padding(.top, 16)
                    .padding(.bottom, 12)

                VStack(spacing: 4) {
                    ForEach(Destination.allCases) { destination in
                        sidebarRow(for: destination)
                    }
                }
                .padding(.horizontal, 12)

                Spacer(minLength: 0)

                if let profile = accountViewModel?.loadedProfile {
                    HStack(spacing: 12) {
                        AvatarCircleView(
                            avatarUrl: profile.trimmedAvatarUrl,
                            initials: profile.initials,
                            size: 36,
                            fontSize: 13,
                            locale: preferences.locale,
                            exposeAvatarAccessibility: false
                        )

                        VStack(alignment: .leading, spacing: 2) {
                            Text(profile.displayName)
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(palette.textColor)
                                .lineLimit(1)
                            Text(profile.username)
                                .font(.caption)
                                .foregroundStyle(palette.textMutedColor)
                                .lineLimit(1)
                        }
                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 16)
                    .accessibilityElement(children: .combine)
                    .accessibilityIdentifier("shell.ipad.sidebarProfile")
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .background(palette.bgPageColor.ignoresSafeArea())
            .navigationSplitViewColumnWidth(min: 240, ideal: 280, max: 320)
            .toolbar(.hidden, for: .navigationBar)
            .accessibilityElement(children: .contain)
            .accessibilityLabel(ShellStrings.text("shell.nav.label", locale: preferences.locale))
            .accessibilityIdentifier("shell.ipad.sidebar")
        } detail: {
            NavigationStack(path: router.pathBinding(for: router.selectedDestination)) {
                destinationContent(for: router.selectedDestination, showsBrandHeader: false)
                    .id(router.reloadCount(for: router.selectedDestination))
                    .navigationDestination(for: AppRoute.self) { route in
                        AppRouteView(
                            route: route,
                            destination: router.selectedDestination,
                            router: router,
                            preferences: preferences,
                            legalRepository: legalRepository,
                            webBaseURL: webBaseURL
                        )
                    }
            }
            .environment(\.accountInspector, accountInspector)
        }
        .navigationSplitViewStyle(.balanced)
    }

    private func destinationSpansBothPanesOnPhoneBook(_ destination: Destination) -> Bool {
        switch destination {
        case .home:
            CatalogRoutes.spansBothPanesOnPhoneBook
        case .myExams:
            MyExamsRoutes.myExamsSpansBothPanesOnPhoneBook
        case .history:
            MyExamsRoutes.historySpansBothPanesOnPhoneBook
        case .account:
            false
        }
    }

    static func phoneBookHalfPaneWidth(totalWidth: CGFloat) -> CGFloat {
        max(0, (totalWidth - 1) / 2)
    }

    static func phoneBookHalfPaneShellWidth(
        horizontalSizeClass: UserInterfaceSizeClass?,
        layoutMode: ShellLayoutMode,
        totalWidth: CGFloat
    ) -> ShellWidth {
        let halfWidth = phoneBookHalfPaneWidth(totalWidth: totalWidth)
        guard halfWidth > 0 else { return .wide }
        return ShellWidth.resolve(
            horizontalSizeClass: horizontalSizeClass,
            layoutMode: layoutMode,
            availableWidth: halfWidth
        )
    }

    @ViewBuilder
    private func phoneTwoPaneBookShell(totalWidth: CGFloat, fullWidthClass: ShellWidth) -> some View {
        let halfWidth = Self.phoneBookHalfPaneWidth(totalWidth: totalWidth)
        let halfWidthClass = Self.phoneBookHalfPaneShellWidth(
            horizontalSizeClass: effectiveSizeClass,
            layoutMode: layoutMode,
            totalWidth: totalWidth
        )

        if destinationSpansBothPanesOnPhoneBook(router.selectedDestination) {
            NavigationStack(path: router.pathBinding(for: router.selectedDestination)) {
                destinationContent(for: router.selectedDestination, showsBrandHeader: true)
                    .id(router.reloadCount(for: router.selectedDestination))
                    .navigationDestination(for: AppRoute.self) { route in
                        AppRouteView(
                            route: route,
                            destination: router.selectedDestination,
                            router: router,
                            preferences: preferences,
                            legalRepository: legalRepository,
                            webBaseURL: webBaseURL
                        )
                    }
            }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                if router.path(for: router.selectedDestination).isEmpty {
                    HStack(spacing: 0) {
                        duoLeftTabBar
                            .padding(.horizontal, 12)
                            .padding(.top, 8)
                            .padding(.bottom, 12)
                            .frame(width: halfWidth)
                        Spacer(minLength: 0)
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(palette.bgPageColor.ignoresSafeArea())
            .environment(\.shellWidth, fullWidthClass)
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("shell.duo.fullSpanPane")
        } else {
            HStack(spacing: 0) {
                VStack(spacing: 0) {
                    NavigationStack {
                        leftBookPaneContent
                            .id(router.reloadCount(for: router.selectedDestination))
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)

                    duoLeftTabBar
                        .padding(.horizontal, 12)
                        .padding(.top, 8)
                        .padding(.bottom, 12)
                        .background(palette.bgPageColor)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(palette.bgPageColor)
                .environment(\.shellWidth, halfWidthClass)
                .accessibilityElement(children: .contain)
                .accessibilityIdentifier("shell.duo.leftPane")

                Rectangle()
                    .fill(palette.borderColor)
                    .frame(width: 1)
                    .ignoresSafeArea()

                NavigationStack(path: router.pathBinding(for: router.selectedDestination)) {
                    rightBookPaneContent
                        .id(router.reloadCount(for: router.selectedDestination))
                        .navigationDestination(for: AppRoute.self) { route in
                            AppRouteView(
                                route: route,
                                destination: router.selectedDestination,
                                router: router,
                                preferences: preferences,
                                legalRepository: legalRepository,
                                webBaseURL: webBaseURL
                            )
                        }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(palette.bgPageColor)
                .environment(\.shellWidth, halfWidthClass)
                .accessibilityElement(children: .contain)
                .accessibilityIdentifier("shell.duo.rightPane")
            }
            .background(palette.bgPageColor.ignoresSafeArea())
        }
    }

    @ViewBuilder
    private var leftBookPaneContent: some View {
        switch router.selectedDestination {
        case .account:
            if let accountViewModel {
                AccountView(
                    viewModel: accountViewModel,
                    router: router,
                    preferences: preferences,
                    effectiveSizeClass: effectiveSizeClass,
                    serverDefaultThemeMode: serverDefaultThemeMode,
                    isTwoPaneBook: true,
                    showsInlineProbe: false,
                    onOpenProfile: {
                        if !router.path(for: .account).isEmpty {
                            router.setPath([], for: .account)
                        }
                    },
                    onOpenOrders: {
                        if !router.path(for: .account).isEmpty {
                            router.setPath([], for: .account)
                        }
                    },
                    onOpenLegal: { _ in
                        if !router.path(for: .account).isEmpty {
                            router.setPath([], for: .account)
                        }
                    },
                    onOpenDeleteAccount: {
                        if !router.path(for: .account).isEmpty {
                            router.setPath([], for: .account)
                        }
                    },
                    onOpenChangePassword: {
                        if !router.path(for: .account).isEmpty {
                            router.setPath([], for: .account)
                        }
                    }
                )
            } else {
                AccountShellView(
                    router: router,
                    preferences: preferences,
                    effectiveSizeClass: effectiveSizeClass,
                    showsInlineProbe: false
                )
            }
        case .home, .myExams, .history:
            destinationContent(for: router.selectedDestination, showsBrandHeader: true)
        }
    }

    @ViewBuilder
    private var rightBookPaneContent: some View {
        switch router.selectedDestination {
        case .account:
            if let sessionStore, let accountViewModel {
                AccountDuoDetailView(
                    accountViewModel: accountViewModel,
                    router: router,
                    preferences: preferences,
                    sessionStore: sessionStore,
                    effectiveSizeClass: effectiveSizeClass,
                    legalRepository: legalRepository,
                    webBaseURL: webBaseURL
                )
            } else {
                AccountDetailShellView(
                    router: router,
                    preferences: preferences,
                    effectiveSizeClass: effectiveSizeClass
                )
            }
        case .home:
            CatalogRoutes.homeEmptyDetail(router: router, preferences: preferences)
        case .myExams:
            MyExamsRoutes.myExamsEmptyDetail(router: router, preferences: preferences)
        case .history:
            MyExamsRoutes.historyEmptyDetail(router: router, preferences: preferences)
        }
    }

    @ViewBuilder
    private func destinationContent(for destination: Destination, showsBrandHeader: Bool) -> some View {
        switch destination {
        case .home:
            CatalogRoutes.homeRoot(router: router, preferences: preferences, showsBrandHeader: showsBrandHeader)
        case .myExams:
            MyExamsRoutes.myExamsRoot(router: router, preferences: preferences, showsBrandHeader: showsBrandHeader)
        case .history:
            MyExamsRoutes.historyRoot(
                resetFilters: router.historyResetFilters,
                router: router,
                preferences: preferences,
                showsBrandHeader: showsBrandHeader
            )
        case .account:
            if let accountViewModel {
                if layoutMode == .iPadSidebar, let sessionStore {
                    accountTwoPaneListDetail(
                        accountViewModel: accountViewModel,
                        sessionStore: sessionStore
                    )
                } else {
                    AccountView(
                        viewModel: accountViewModel,
                        router: router,
                        preferences: preferences,
                        effectiveSizeClass: effectiveSizeClass,
                        serverDefaultThemeMode: serverDefaultThemeMode,
                        isTwoPaneBook: false,
                        showsInlineProbe: true,
                        onOpenProfile: {
                            router.openProfile()
                        },
                        onOpenOrders: {
                            router.openOrders()
                        },
                        onOpenLegal: { legalId in
                            router.openLegal(legalId)
                        },
                        onOpenDeleteAccount: {
                            router.openDeleteAccount()
                        },
                        onOpenChangePassword: {
                            router.isPresentingChangePassword = true
                        }
                    )
                }
            } else {
                AccountShellView(
                    router: router,
                    preferences: preferences,
                    effectiveSizeClass: effectiveSizeClass
                )
            }
        }
    }

    private func accountTwoPaneListDetail(
        accountViewModel: AccountViewModel,
        sessionStore: SessionStore
    ) -> some View {
        let selectionBinding = Binding<AccountDetailSelection?>(
            get: { accountViewModel.selectedDetail },
            set: { newValue in
                if let newValue {
                    accountViewModel.selectDetail(newValue)
                }
            }
        )
        return ListDetailLayout(
            selection: selectionBinding,
            listWidthFraction: 0.42
        ) {
            AccountView(
                viewModel: accountViewModel,
                router: router,
                preferences: preferences,
                effectiveSizeClass: effectiveSizeClass,
                serverDefaultThemeMode: serverDefaultThemeMode,
                isTwoPaneBook: true,
                showsInlineProbe: false,
                onOpenProfile: {
                    if !router.path(for: .account).isEmpty {
                        router.setPath([], for: .account)
                    }
                },
                onOpenOrders: {
                    if !router.path(for: .account).isEmpty {
                        router.setPath([], for: .account)
                    }
                },
                onOpenLegal: { _ in
                    if !router.path(for: .account).isEmpty {
                        router.setPath([], for: .account)
                    }
                },
                onOpenDeleteAccount: {
                    if !router.path(for: .account).isEmpty {
                        router.setPath([], for: .account)
                    }
                },
                onOpenChangePassword: {
                    if !router.path(for: .account).isEmpty {
                        router.setPath([], for: .account)
                    }
                }
            )
        } detail: { _ in
            AccountDetailPaneView(
                accountViewModel: accountViewModel,
                router: router,
                preferences: preferences,
                sessionStore: sessionStore,
                effectiveSizeClass: effectiveSizeClass,
                legalRepository: legalRepository,
                webBaseURL: webBaseURL
            )
        } emptyDetail: {
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

    private func syncAccountNavigationOnLayoutTransition(
        from oldMode: ShellLayoutMode,
        to newMode: ShellLayoutMode
    ) {
        let wasTwoPane = oldMode == .iPadSidebar || oldMode == .phoneTwoPaneBook
        let isTwoPane = newMode == .iPadSidebar || newMode == .phoneTwoPaneBook
        accountViewModel?.isInTwoPaneLayout = isTwoPane
        guard router.selectedDestination == .account, let accountViewModel else { return }
        guard wasTwoPane != isTwoPane else { return }

        var accountPath = router.path(for: .account)
        if isTwoPane {
            if let first = accountPath.first {
                switch first {
                case .profile:
                    accountViewModel.selectDetail(.profile)
                    accountPath.removeFirst()
                    router.setPath(accountPath, for: .account)
                case .orders:
                    accountViewModel.selectDetail(.orders)
                    accountPath.removeFirst()
                    router.setPath(accountPath, for: .account)
                case .deleteAccount:
                    accountViewModel.selectDetail(.deleteAccount)
                    accountPath.removeFirst()
                    router.setPath(accountPath, for: .account)
                case let .legal(legalId):
                    accountViewModel.selectDetail(legalId == .terms ? .legalTerms : .legalPrivacy)
                    accountPath.removeFirst()
                    router.setPath(accountPath, for: .account)
                default:
                    break
                }
            }
        } else {
            let hasProfileDraft = accountViewModel.existingProfileViewModel()?.hasUnsavedDraft == true
            if accountPath.isEmpty && (hasProfileDraft || accountViewModel.hasExplicitDetailSelection) {
                switch accountViewModel.selectedDetail {
                case .profile:
                    router.openProfile()
                case .orders:
                    router.openOrders()
                case .deleteAccount:
                    router.openDeleteAccount()
                case .legalTerms:
                    router.openLegal(.terms)
                case .legalPrivacy:
                    router.openLegal(.privacy)
                case .changePassword:
                    router.isPresentingChangePassword = true
                }
            } else if accountPath.first == .profileVerifyEmail {
                accountPath.insert(.profile, at: 0)
                router.setPath(accountPath, for: .account)
            }
        }
    }

    private func sidebarRow(for destination: Destination) -> some View {
        let isSelected = router.selectedDestination == destination
        let title = ShellStrings.text(destination.titleKey, locale: preferences.locale)

        return Button {
            router.selectedDestination = destination
        } label: {
            HStack(spacing: 12) {
                RoundedRectangle(cornerRadius: 2, style: .continuous)
                    .fill(isSelected ? palette.primaryFillColor : Color.clear)
                    .frame(width: 3, height: 22)

                Image(systemName: destination.symbolName)
                    .font(.system(size: 17, weight: isSelected ? .semibold : .regular))
                    .foregroundStyle(isSelected ? palette.primaryTextColor : palette.textSecondaryColor)
                    .frame(width: 22)

                Text(title)
                    .font(.body.weight(isSelected ? .semibold : .medium))
                    .foregroundStyle(isSelected ? palette.primaryTextColor : palette.textColor)
                    .lineLimit(2)

                Spacer(minLength: 0)
            }
            .padding(.horizontal, 10)
            .frame(minHeight: 44)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(isSelected ? palette.primaryContainerColor : Color.clear)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? [.isSelected] : [])
        .accessibilityIdentifier(destination.accessibilityIdentifier)
    }

    private var duoLeftTabBar: some View {
        HStack(spacing: 4) {
            ForEach(Destination.allCases) { destination in
                let isSelected = router.selectedDestination == destination
                let title = ShellStrings.text(destination.titleKey, locale: preferences.locale)

                Button {
                    router.selectedDestination = destination
                } label: {
                    VStack(spacing: 3) {
                        Image(systemName: destination.symbolName)
                            .font(.system(size: 17, weight: isSelected ? .semibold : .regular))
                        Text(title)
                            .font(.caption2.weight(isSelected ? .semibold : .medium))
                            .lineLimit(1)
                            .minimumScaleFactor(0.7)
                    }
                    .foregroundStyle(isSelected ? palette.primaryTextColor : palette.textMutedColor)
                    .frame(maxWidth: .infinity, minHeight: 50)
                    .padding(.horizontal, 4)
                    .background(
                        RoundedRectangle(cornerRadius: 24, style: .continuous)
                            .fill(isSelected ? palette.primaryContainerColor : Color.clear)
                    )
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(isSelected ? [.isSelected] : [])
                .accessibilityIdentifier(destination.accessibilityIdentifier)
            }
        }
        .padding(4)
        .background(
            RoundedRectangle(cornerRadius: 30, style: .continuous)
                .fill(palette.bgSurfaceColor)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 30, style: .continuous)
                .strokeBorder(palette.borderColor, lineWidth: 1)
        )
        .accessibilityElement(children: .contain)
        .accessibilityLabel(ShellStrings.text("shell.nav.label", locale: preferences.locale))
        .accessibilityIdentifier("shell.duo.leftTabBar")
    }
}
