import Foundation
import Observation
import SwiftUI
import UIKit

enum SignedOutRoute: Equatable, Sendable {
    case login
    case register
    case forgotPassword
}

enum RecoveryOverlay: Equatable, Sendable {
    case resetPassword(ResetLink)
    case forgotPassword
}

@Observable
@MainActor
final class AppRouter {
    var selectedDestination: Destination = .home
    var draftProbeText: String = ""
    let stateProbeEnabled: Bool
    var forcedSizeClassForUITest: UserInterfaceSizeClass?
    var signedOutRoute: SignedOutRoute = .login
    var recoveryOverlay: RecoveryOverlay?
    var isPresentingChangePassword: Bool

    @ObservationIgnored private var cachedVoluntaryPasswordVM: NewPasswordViewModel?
    @ObservationIgnored private var cachedResetPasswordVM: NewPasswordViewModel?
    @ObservationIgnored private var cachedResetLinkID: UUID?

    init(
        initialDestination: Destination = .home,
        stateProbeEnabled: Bool = false,
        initialResetLink: ResetLink? = nil,
        isPresentingChangePassword: Bool = false
    ) {
        self.selectedDestination = initialDestination
        self.stateProbeEnabled = stateProbeEnabled
        self.recoveryOverlay = initialResetLink.map { .resetPassword($0) }
        self.isPresentingChangePassword = isPresentingChangePassword
    }

    var activeResetLink: ResetLink? {
        guard case let .resetPassword(link) = recoveryOverlay else { return nil }
        return link
    }

    func toggleForcedSizeClass(current: UserInterfaceSizeClass?) {
        guard stateProbeEnabled else { return }
        let active = forcedSizeClassForUITest ?? current ?? .compact
        forcedSizeClassForUITest = (active == .compact) ? .regular : .compact
    }

    @discardableResult
    func handleIncomingURL(
        _ url: URL,
        environment: AppEnvironment = .current
    ) -> Bool {
        guard let resetLink = ResetLink.parse(url, environment: environment) else {
            return false
        }
        openResetLink(resetLink)
        return true
    }

    @discardableResult
    func handleUserActivity(
        _ userActivity: NSUserActivity,
        environment: AppEnvironment = .current
    ) -> Bool {
        guard let resetLink = ResetLink.fromUserActivity(userActivity, environment: environment) else {
            return false
        }
        openResetLink(resetLink)
        return true
    }

    func openResetLink(_ resetLink: ResetLink) {
        cachedResetPasswordVM?.clearResetToken()
        cachedResetPasswordVM = nil
        cachedResetLinkID = nil
        recoveryOverlay = .resetPassword(resetLink)
    }

    func clearRecoveryOverlay() {
        cachedResetPasswordVM?.clearResetToken()
        cachedResetPasswordVM = nil
        cachedResetLinkID = nil
        recoveryOverlay = nil
    }

    func openForgotPasswordFromExpiredResetLink(isAuthenticated: Bool) {
        cachedResetPasswordVM?.clearResetToken()
        cachedResetPasswordVM = nil
        cachedResetLinkID = nil
        if isAuthenticated {
            recoveryOverlay = .forgotPassword
        } else {
            recoveryOverlay = nil
            signedOutRoute = .forgotPassword
        }
    }

    func resetPasswordViewModel(
        for resetLink: ResetLink,
        sessionStore: SessionStore
    ) -> NewPasswordViewModel {
        if let existing = cachedResetPasswordVM, cachedResetLinkID == resetLink.id {
            return existing
        }
        cachedResetPasswordVM?.clearResetToken()
        let viewModel = NewPasswordViewModel(
            mode: .fromLink(resetLink),
            sessionStore: sessionStore,
            onResetCompleted: { [weak self] in
                self?.signedOutRoute = .login
                self?.clearRecoveryOverlay()
            }
        )
        cachedResetPasswordVM = viewModel
        cachedResetLinkID = resetLink.id
        return viewModel
    }

    func voluntaryChangePasswordViewModel(sessionStore: SessionStore) -> NewPasswordViewModel {
        if let existing = cachedVoluntaryPasswordVM {
            return existing
        }
        let viewModel = NewPasswordViewModel(
            mode: .voluntary(username: sessionStore.currentUsername),
            sessionStore: sessionStore
        )
        cachedVoluntaryPasswordVM = viewModel
        return viewModel
    }

    func dismissChangePassword() {
        cachedVoluntaryPasswordVM?.clearSensitiveInputs()
        cachedVoluntaryPasswordVM = nil
        isPresentingChangePassword = false
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
        let initialResetLink = ResetLink.fromLaunchArguments(arguments, environment: environment)
        let openChangePassword = arguments.contains("-uitest-open-change-password")

        return AppRouter(
            initialDestination: initialDestination,
            stateProbeEnabled: probeEnabled,
            initialResetLink: initialResetLink,
            isPresentingChangePassword: openChangePassword
        )
    }
}

struct AppRootView: View {
    @Bindable var router: AppRouter
    @Bindable var preferences: AppPreferences
    var sessionStore: SessionStore? = nil
    var paletteSet: BrandPaletteSet = .defaultThi360Green
    var idiomOverride: UIUserInterfaceIdiom?

    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

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
            .onChange(of: sessionStore?.state) { _, newState in
                guard let newState else { return }
                if newState.isAuthenticated {
                    router.signedOutRoute = .login
                } else {
                    router.dismissChangePassword()
                }
            }
            .task {
                await sessionStore?.validateSessionOnLaunch()
            }
    }

    @ViewBuilder
    private var rootContent: some View {
        if let sessionStore, let overlay = router.recoveryOverlay {
            recoveryOverlayView(overlay, sessionStore: sessionStore)
        } else if let sessionStore {
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
                    SignedOutFlowHostView(
                        router: router,
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
                    ForcedPasswordChangeHostView(
                        sessionStore: sessionStore,
                        preferences: preferences
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
                    if router.isPresentingChangePassword {
                        let effectiveSizeClass = router.stateProbeEnabled
                            ? (router.forcedSizeClassForUITest ?? horizontalSizeClass)
                            : horizontalSizeClass
                        NewPasswordView(
                            viewModel: router.voluntaryChangePasswordViewModel(sessionStore: sessionStore),
                            preferences: preferences,
                            onClose: { router.dismissChangePassword() },
                            stateProbeToggleAction: router.stateProbeEnabled
                                ? { router.toggleForcedSizeClass(current: effectiveSizeClass) }
                                : nil
                        )
                        .environment(\.horizontalSizeClass, effectiveSizeClass)
                    } else {
                        AdaptiveShellView(
                            router: router,
                            preferences: preferences,
                            idiom: resolvedIdiom
                        )
                    }
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

    @ViewBuilder
    private func recoveryOverlayView(
        _ overlay: RecoveryOverlay,
        sessionStore: SessionStore
    ) -> some View {
        switch overlay {
        case let .resetPassword(resetLink):
            NewPasswordView(
                viewModel: router.resetPasswordViewModel(for: resetLink, sessionStore: sessionStore),
                preferences: preferences,
                onClose: {
                    router.clearRecoveryOverlay()
                },
                onRequestNewResetLink: {
                    router.openForgotPasswordFromExpiredResetLink(
                        isAuthenticated: sessionStore.state.isAuthenticated
                    )
                }
            )
        case .forgotPassword:
            ForgotPasswordHostView(
                apiClient: sessionStore.apiClient,
                preferences: preferences,
                onBackToLogin: {
                    router.clearRecoveryOverlay()
                }
            )
        }
    }
}

private struct SignedOutFlowHostView: View {
    @Bindable var router: AppRouter
    let sessionStore: SessionStore
    @Bindable var preferences: AppPreferences
    let idiom: UIUserInterfaceIdiom
    let reason: SignedOutReason

    var body: some View {
        switch router.signedOutRoute {
        case .login:
            LoginFlowHostView(
                sessionStore: sessionStore,
                preferences: preferences,
                idiom: idiom,
                reason: reason,
                onForgotPassword: { router.signedOutRoute = .forgotPassword },
                onRegister: { router.signedOutRoute = .register }
            )
        case .register:
            RegisterFlowHostView(
                sessionStore: sessionStore,
                preferences: preferences,
                onBackToLogin: { router.signedOutRoute = .login }
            )
        case .forgotPassword:
            ForgotPasswordHostView(
                apiClient: sessionStore.apiClient,
                preferences: preferences,
                onBackToLogin: { router.signedOutRoute = .login }
            )
        }
    }
}

private struct LoginFlowHostView: View {
    let sessionStore: SessionStore
    @Bindable var preferences: AppPreferences
    let idiom: UIUserInterfaceIdiom
    let reason: SignedOutReason
    let onForgotPassword: () -> Void
    let onRegister: () -> Void

    @State private var viewModel: LoginViewModel

    init(
        sessionStore: SessionStore,
        preferences: AppPreferences,
        idiom: UIUserInterfaceIdiom,
        reason: SignedOutReason,
        onForgotPassword: @escaping () -> Void,
        onRegister: @escaping () -> Void
    ) {
        self.sessionStore = sessionStore
        self.preferences = preferences
        self.idiom = idiom
        self.reason = reason
        self.onForgotPassword = onForgotPassword
        self.onRegister = onRegister
        _viewModel = State(
            initialValue: LoginViewModel(sessionStore: sessionStore, initialReason: reason)
        )
    }

    var body: some View {
        LoginView(
            viewModel: viewModel,
            preferences: preferences,
            idiom: idiom,
            onForgotPassword: onForgotPassword,
            onRegister: onRegister
        )
        .onChange(of: reason) { _, newReason in
            viewModel.updateSignedOutReason(newReason)
        }
    }
}

private struct RegisterFlowHostView: View {
    @Bindable var preferences: AppPreferences
    let onBackToLogin: () -> Void
    @State private var viewModel: RegisterViewModel

    init(
        sessionStore: SessionStore,
        preferences: AppPreferences,
        onBackToLogin: @escaping () -> Void
    ) {
        self.preferences = preferences
        self.onBackToLogin = onBackToLogin
        _viewModel = State(initialValue: RegisterViewModel(sessionStore: sessionStore))
    }

    var body: some View {
        RegisterView(
            viewModel: viewModel,
            preferences: preferences,
            onBackToLogin: onBackToLogin
        )
    }
}

private struct ForgotPasswordHostView: View {
    @Bindable var preferences: AppPreferences
    let onBackToLogin: () -> Void
    @State private var viewModel: ForgotPasswordViewModel

    init(
        apiClient: any MobileRecoveryClientProtocol,
        preferences: AppPreferences,
        onBackToLogin: @escaping () -> Void
    ) {
        self.preferences = preferences
        self.onBackToLogin = onBackToLogin
        _viewModel = State(initialValue: ForgotPasswordViewModel(apiClient: apiClient))
    }

    var body: some View {
        ForgotPasswordView(
            viewModel: viewModel,
            preferences: preferences,
            onBackToLogin: onBackToLogin
        )
    }
}

private struct ForcedPasswordChangeHostView: View {
    let sessionStore: SessionStore
    @Bindable var preferences: AppPreferences
    @State private var viewModel: NewPasswordViewModel

    init(
        sessionStore: SessionStore,
        preferences: AppPreferences
    ) {
        self.sessionStore = sessionStore
        self.preferences = preferences
        _viewModel = State(
            initialValue: NewPasswordViewModel(
                mode: .forced(username: sessionStore.currentUsername),
                sessionStore: sessionStore
            )
        )
    }

    var body: some View {
        NewPasswordView(
            viewModel: viewModel,
            preferences: preferences,
            onSignOut: {
                Task { await sessionStore.signOut() }
            }
        )
    }
}
