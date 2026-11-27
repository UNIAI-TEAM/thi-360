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
                    phoneTwoPaneBookShell
                }
            }
            .environment(\.shellWidth, widthClass)
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier(layoutMode.accessibilityIdentifier)
        .onAppear {
            Thi360AppDelegate.refreshSupportedOrientations(for: effectiveSizeClass)
        }
        .onChange(of: effectiveSizeClass) { _, newValue in
            Thi360AppDelegate.refreshSupportedOrientations(for: newValue)
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
                        ZStack {
                            Circle()
                                .fill(palette.primaryContainerStrongColor)
                                .frame(width: 36, height: 36)
                            Text(profile.initials)
                                .font(.footnote.weight(.semibold))
                                .foregroundStyle(palette.primaryTextColor)
                        }
                        .accessibilityHidden(true)

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
        }
        .navigationSplitViewStyle(.balanced)
    }

    private var phoneTwoPaneBookShell: some View {
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
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("shell.duo.rightPane")
        }
        .background(palette.bgPageColor.ignoresSafeArea())
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
                    onOpenChangePassword: {}
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
            if let sessionStore, accountViewModel != nil {
                AccountDuoDetailView(
                    router: router,
                    preferences: preferences,
                    sessionStore: sessionStore,
                    effectiveSizeClass: effectiveSizeClass
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
                AccountView(
                    viewModel: accountViewModel,
                    router: router,
                    preferences: preferences,
                    effectiveSizeClass: effectiveSizeClass,
                    serverDefaultThemeMode: serverDefaultThemeMode,
                    isTwoPaneBook: false,
                    showsInlineProbe: true,
                    onOpenChangePassword: {
                        router.isPresentingChangePassword = true
                    }
                )
            } else {
                AccountShellView(
                    router: router,
                    preferences: preferences,
                    effectiveSizeClass: effectiveSizeClass
                )
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
