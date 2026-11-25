import Foundation
import Observation
import SwiftUI
import UIKit

@Observable
@MainActor
final class AppRouter {
    var selectedDestination: Destination = .home
    var draftProbeText: String = ""
    let stateProbeEnabled: Bool
    var forcedSizeClassForUITest: UserInterfaceSizeClass?

    init(
        initialDestination: Destination = .home,
        stateProbeEnabled: Bool = false
    ) {
        self.selectedDestination = initialDestination
        self.stateProbeEnabled = stateProbeEnabled
    }

    func toggleForcedSizeClass(current: UserInterfaceSizeClass?) {
        guard stateProbeEnabled else { return }
        let active = forcedSizeClassForUITest ?? current ?? .compact
        forcedSizeClassForUITest = (active == .compact) ? .regular : .compact
    }

    static func makeForCurrentProcess(
        arguments: [String] = ProcessInfo.processInfo.arguments,
        environment: AppEnvironment = .current
    ) -> AppRouter {
        guard environment.isDev else {
            return AppRouter()
        }

        let probeEnabled = arguments.contains("-uitest-state-probe")
        var initialDestination: Destination = .home
        if let index = arguments.firstIndex(of: "-uitest-destination"),
           index + 1 < arguments.count,
           let parsed = Destination(rawValue: arguments[index + 1])
        {
            initialDestination = parsed
        }
        return AppRouter(initialDestination: initialDestination, stateProbeEnabled: probeEnabled)
    }
}

struct AppRootView: View {
    @Bindable var router: AppRouter
    @Bindable var preferences: AppPreferences
    var sessionStore: SessionStore? = nil
    var paletteSet: BrandPaletteSet = .defaultThi360Green
    var idiomOverride: UIUserInterfaceIdiom?

    private var effectiveThemeMode: ThemeMode {
        preferences.effectiveThemeMode(serverDefault: paletteSet.defaultMode)
    }

    private var activePalette: BrandPalette {
        paletteSet.palette(for: effectiveThemeMode)
    }

    private var resolvedIdiom: UIUserInterfaceIdiom {
        idiomOverride ?? UIDevice.current.userInterfaceIdiom
    }

    var body: some View {
        rootContent
            .environment(\.brandPalette, activePalette)
            .environment(\.locale, preferences.locale.locale)
            .tint(activePalette.primaryFillColor)
            .preferredColorScheme(effectiveThemeMode.colorScheme)
            .task {
                await sessionStore?.validateSessionOnLaunch()
            }
    }

    @ViewBuilder
    private var rootContent: some View {
        if let sessionStore {
            switch sessionStore.state {
            case let .signedOut(reason):
                switch reason {
                case let .studentOnly(username):
                    RoleNoticeView(
                        username: username,
                        preferences: preferences,
                        onSwitchAccount: {
                            sessionStore.acknowledgeRoleNotice()
                        }
                    )
                case .none, .expired, .passwordReset:
                    LoginFlowHostView(
                        sessionStore: sessionStore,
                        preferences: preferences,
                        idiom: resolvedIdiom,
                        reason: reason
                    )
                }

            case .mustChangePassword:
                VStack(spacing: 0) {
                    if sessionStore.connectivityState == .offline {
                        OfflineStatusBanner(
                            locale: preferences.locale,
                            isRetrying: sessionStore.isRetryingConnection,
                            onRetry: { Task { await sessionStore.retryConnection() } }
                        )
                    }
                    MustChangePasswordGateView(
                        locale: preferences.locale,
                        onSignOut: { Task { await sessionStore.signOut() } }
                    )
                }

            case .active:
                VStack(spacing: 0) {
                    if sessionStore.connectivityState == .offline {
                        OfflineStatusBanner(
                            locale: preferences.locale,
                            isRetrying: sessionStore.isRetryingConnection,
                            onRetry: { Task { await sessionStore.retryConnection() } }
                        )
                    }
                    AdaptiveShellView(
                        router: router,
                        preferences: preferences,
                        idiom: resolvedIdiom
                    )
                }

            case .locked:
                LockedGateView(
                    locale: preferences.locale,
                    onSignOut: { Task { await sessionStore.signOut() } }
                )
            }
        } else {
            AdaptiveShellView(
                router: router,
                preferences: preferences,
                idiom: resolvedIdiom
            )
        }
    }
}

private struct LoginFlowHostView: View {
    let sessionStore: SessionStore
    @Bindable var preferences: AppPreferences
    let idiom: UIUserInterfaceIdiom
    let reason: SignedOutReason

    @State private var viewModel: LoginViewModel

    init(
        sessionStore: SessionStore,
        preferences: AppPreferences,
        idiom: UIUserInterfaceIdiom,
        reason: SignedOutReason
    ) {
        self.sessionStore = sessionStore
        self.preferences = preferences
        self.idiom = idiom
        self.reason = reason
        _viewModel = State(
            initialValue: LoginViewModel(sessionStore: sessionStore, initialReason: reason)
        )
    }

    var body: some View {
        LoginView(
            viewModel: viewModel,
            preferences: preferences,
            idiom: idiom
        )
        .onChange(of: reason) { _, newReason in
            viewModel.updateSignedOutReason(newReason)
        }
    }
}
