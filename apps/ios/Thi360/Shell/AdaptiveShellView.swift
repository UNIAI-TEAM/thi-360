import SwiftUI
import UIKit

struct AdaptiveShellView: View {
    @Bindable var router: AppRouter
    @Bindable var preferences: AppPreferences
    let idiom: UIUserInterfaceIdiom
    var sessionStore: SessionStore? = nil
    var accountViewModel: AccountViewModel? = nil
    var serverDefaultThemeMode: ThemeMode = .light

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
                NavigationStack {
                    destinationContent(for: destination, showsBrandHeader: destination == .home)
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
            NavigationStack {
                destinationContent(for: router.selectedDestination, showsBrandHeader: false)
            }
        }
        .navigationSplitViewStyle(.balanced)
    }

    private var phoneTwoPaneBookShell: some View {
        HStack(spacing: 0) {
            VStack(spacing: 0) {
                NavigationStack {
                    leftBookPaneContent
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

            NavigationStack {
                rightBookPaneContent
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
            duoLeftOverviewPane(for: router.selectedDestination)
        }
    }

    private func duoLeftOverviewPane(for destination: Destination) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                BrandWordmarkHeader(locale: preferences.locale)

                Text(ShellStrings.text(destination.titleKey, locale: preferences.locale))
                    .font(.largeTitle.weight(.bold))
                    .foregroundStyle(palette.textColor)
                    .accessibilityAddTraits(.isHeader)

                if let descriptionKey = destination.placeholderDescriptionKey {
                    Text(ShellStrings.text(descriptionKey, locale: preferences.locale))
                        .font(.subheadline)
                        .foregroundStyle(palette.textMutedColor)
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
                }
            }
            .padding(20)
        }
        .background(palette.bgPageColor.ignoresSafeArea())
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
        case .home, .myExams, .history:
            PlaceholderDestinationView(
                destination: router.selectedDestination,
                locale: preferences.locale,
                showsBrandHeader: false
            )
        }
    }

    @ViewBuilder
    private func destinationContent(for destination: Destination, showsBrandHeader: Bool) -> some View {
        switch destination {
        case .home, .myExams, .history:
            PlaceholderDestinationView(
                destination: destination,
                locale: preferences.locale,
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
