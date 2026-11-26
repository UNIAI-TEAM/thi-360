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
    var paths: [Destination: [AppRoute]] = [:]
    var presentedAttempt: AttemptRef?
    var reloadTokens: [Destination: Int] = [:]
    var signedOutPath: [AppRoute] = []
    var draftProbeText: String = ""
    let stateProbeEnabled: Bool
    var forcedSizeClassForUITest: UserInterfaceSizeClass?
    var signedOutRoute: SignedOutRoute = .login
    var recoveryOverlay: RecoveryOverlay?
    var isPresentingChangePassword: Bool

    let routeStateStore = RouteStateStore()
    let inboundLinkRouter = InboundLinkRouter()

    @ObservationIgnored private var cachedVoluntaryPasswordVM: NewPasswordViewModel?
    @ObservationIgnored private var cachedResetPasswordVM: NewPasswordViewModel?
    @ObservationIgnored private var cachedResetLinkID: UUID?
    @ObservationIgnored private var cachedAccountVM: AccountViewModel?
    @ObservationIgnored private var cachedLockVM: LockViewModel?
    @ObservationIgnored private var cachedRegisterVM: RegisterViewModel?

    init(
        initialDestination: Destination = .home,
        initialRoute: AppRoute? = nil,
        stateProbeEnabled: Bool = false,
        initialResetLink: ResetLink? = nil,
        isPresentingChangePassword: Bool = false
    ) {
        self.selectedDestination = initialDestination
        self.stateProbeEnabled = stateProbeEnabled
        self.recoveryOverlay = initialResetLink.map { .resetPassword($0) }
        self.isPresentingChangePassword = isPresentingChangePassword

        if let initialRoute {
            switch initialRoute {
            case .catalog:
                self.selectedDestination = .home
            case .myExams:
                self.selectedDestination = .myExams
            case .history:
                self.selectedDestination = .history
            case let .attempt(attemptId):
                self.presentedAttempt = AttemptRef(
                    attemptId: attemptId,
                    originDestination: initialDestination
                )
            default:
                self.paths[initialDestination] = [initialRoute]
            }
        }

        self.inboundLinkRouter.register(
            id: "auth.resetLink",
            requiresUnlockedSession: false
        ) { [weak self] url, environment in
            guard let resetLink = ResetLink.parse(url, environment: environment) else {
                return nil
            }
            return { [weak self] in
                self?.openResetLink(resetLink)
            }
        }
    }

    var activeResetLink: ResetLink? {
        guard case let .resetPassword(link) = recoveryOverlay else { return nil }
        return link
    }

    var cachedAccountDisplayName: String? {
        cachedAccountVM?.loadedProfile?.displayName
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
        inboundLinkRouter.handleIncomingURL(url, environment: environment)
    }

    @discardableResult
    func handleUserActivity(
        _ userActivity: NSUserActivity,
        environment: AppEnvironment = .current
    ) -> Bool {
        inboundLinkRouter.handleUserActivity(userActivity, environment: environment)
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

    func accountViewModel(
        sessionStore: SessionStore,
        biometricManager: BiometricManager
    ) -> AccountViewModel {
        if let existing = cachedAccountVM,
           existing.sessionStore === sessionStore,
           existing.biometricManager === biometricManager
        {
            return existing
        }
        let viewModel = AccountViewModel(
            sessionStore: sessionStore,
            biometricManager: biometricManager
        )
        cachedAccountVM = viewModel
        return viewModel
    }

    func lockViewModel(
        sessionStore: SessionStore,
        biometricManager: BiometricManager
    ) -> LockViewModel {
        if let existing = cachedLockVM,
           existing.sessionStore === sessionStore,
           existing.biometricManager === biometricManager
        {
            return existing
        }
        let viewModel = LockViewModel(
            sessionStore: sessionStore,
            biometricManager: biometricManager
        )
        cachedLockVM = viewModel
        return viewModel
    }

    func registerViewModel(
        sessionStore: SessionStore,
        legalRepository: LegalRepository = LegalRepository()
    ) -> RegisterViewModel {
        if let existing = cachedRegisterVM {
            return existing
        }
        let viewModel = RegisterViewModel(
            sessionStore: sessionStore,
            legalRepository: legalRepository
        )
        cachedRegisterVM = viewModel
        return viewModel
    }

    func clearRegisterViewModel() {
        cachedRegisterVM = nil
        signedOutPath = []
    }

    func dismissChangePassword() {
        cachedVoluntaryPasswordVM?.clearSensitiveInputs()
        cachedVoluntaryPasswordVM = nil
        isPresentingChangePassword = false
    }

    func resetForSessionCleared() {
        dismissChangePassword()
        cachedAccountVM?.resetForSessionChange()
        cachedAccountVM = nil
        cachedLockVM?.reset()
        cachedLockVM = nil
        cachedRegisterVM = nil
        paths.removeAll()
        presentedAttempt = nil
        reloadTokens.removeAll()
        signedOutPath.removeAll()
        routeStateStore.removeAll()
        inboundLinkRouter.resetPendingWorkForSignOut()
        selectedDestination = .home
    }

    func resetForSessionReplaced() {
        dismissChangePassword()
        cachedAccountVM?.resetForSessionChange()
        cachedAccountVM = nil
        cachedLockVM?.reset()
        cachedLockVM = nil
        cachedRegisterVM = nil
        paths.removeAll()
        presentedAttempt = nil
        reloadTokens.removeAll()
        signedOutPath.removeAll()
        routeStateStore.removeAll()
        selectedDestination = .home
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
        var initialRoute: AppRoute?
        if let index = arguments.firstIndex(of: "-uitest-route"),
           index + 1 < arguments.count
        {
            initialRoute = AppRoute.decode(path: arguments[index + 1])
        }
        let initialResetLink = ResetLink.fromLaunchArguments(arguments, environment: environment)
        let openChangePassword = arguments.contains("-uitest-open-change-password")

        return AppRouter(
            initialDestination: initialDestination,
            initialRoute: initialRoute,
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
    var appearanceRepository: AppearanceRepository? = nil
    var biometricManager: BiometricManager? = nil
    var imageLoader: ImageLoader? = nil
    var legalRepository: LegalRepository = LegalRepository()
    var webBaseURL: URL = AppEnvironment.current.webBaseURL
    var paletteSet: BrandPaletteSet = .defaultThi360Green
    var idiomOverride: UIUserInterfaceIdiom?

    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.scenePhase) private var scenePhase

    private var resolvedPaletteSet: BrandPaletteSet {
        appearanceRepository?.current ?? paletteSet
    }

    private var effectiveThemeMode: ThemeMode {
        preferences.effectiveThemeMode(serverDefault: resolvedPaletteSet.defaultMode)
    }

    private var activePalette: BrandPalette {
        resolvedPaletteSet.palette(for: effectiveThemeMode)
    }

    private var activeStatusPalette: StatusPalette {
        StatusPalette.forMode(effectiveThemeMode)
    }

    private var resolvedIdiom: UIUserInterfaceIdiom {
        idiomOverride ?? UIDevice.current.userInterfaceIdiom
    }

    private var resolvedBiometricManager: BiometricManager? {
        biometricManager ?? sessionStore?.biometricManager
    }

    private var showsPrivacyShield: Bool {
        guard let sessionStore, sessionStore.state.isAuthenticated else { return false }
        if resolvedBiometricManager?.isPrivacyShieldVisible == true {
            return true
        }
        return sessionStore.state == .locked && scenePhase != .active
    }

    var body: some View {
        ZStack {
            rootContent

            if showsPrivacyShield {
                PrivacyShieldView(locale: preferences.locale)
                    .zIndex(100)
            }
        }
        .environment(\.brandPalette, activePalette)
        .environment(\.statusPalette, activeStatusPalette)
        .environment(\.imageLoader, imageLoader)
        .environment(\.locale, preferences.locale.locale)
        .tint(activePalette.primaryFillColor)
        .preferredColorScheme(effectiveThemeMode.colorScheme)
        .onChange(of: sessionStore?.state) { _, newState in
            guard let newState else { return }
            if newState.isAuthenticated {
                router.signedOutRoute = .login
                router.clearRegisterViewModel()
            } else {
                router.resetForSessionCleared()
            }
            Task {
                await router.inboundLinkRouter.notifySessionStateChanged(newState)
            }
        }
        .onChange(of: scenePhase) { _, newPhase in
            if let sessionStore, let manager = resolvedBiometricManager {
                let shouldLock = manager.handleScenePhaseChange(newPhase, sessionState: sessionStore.state)
                if shouldLock {
                    sessionStore.lockSession()
                }
            }
            let currentState = sessionStore?.state ?? .active
            Task {
                await router.inboundLinkRouter.notifyScenePhaseChanged(newPhase, sessionState: currentState)
            }
            if newPhase == .active {
                Task {
                    await appearanceRepository?.refreshOnForegroundIfNeeded()
                }
            }
        }
        .task {
            async let appearanceTask: Void = {
                _ = await appearanceRepository?.refreshOnLaunch()
            }()
            async let sessionTask: Void = {
                await sessionStore?.validateSessionOnLaunch()
            }()
            _ = await (appearanceTask, sessionTask)
            await router.inboundLinkRouter.notifyAppStarted(
                sessionState: sessionStore?.state ?? .active
            )
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
                case .none, .expired, .passwordReset, .accountDeleted:
                    SignedOutFlowHostView(
                        router: router,
                        sessionStore: sessionStore,
                        preferences: preferences,
                        idiom: resolvedIdiom,
                        reason: reason,
                        legalRepository: legalRepository,
                        webBaseURL: webBaseURL
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
                if let manager = resolvedBiometricManager, manager.isPromptingEnable {
                    BiometricEnableView(
                        biometricManager: manager,
                        preferences: preferences,
                        sessionStore: sessionStore
                    )
                } else {
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
                        } else if let attempt = router.presentedAttempt {
                            AttemptRoutes.attempt(
                                attemptId: attempt.attemptId,
                                destination: attempt.originDestination,
                                router: router,
                                preferences: preferences
                            )
                        } else {
                            AdaptiveShellView(
                                router: router,
                                preferences: preferences,
                                idiom: resolvedIdiom,
                                sessionStore: sessionStore,
                                accountViewModel: resolvedBiometricManager.map {
                                    router.accountViewModel(sessionStore: sessionStore, biometricManager: $0)
                                },
                                serverDefaultThemeMode: resolvedPaletteSet.defaultMode,
                                legalRepository: legalRepository,
                                webBaseURL: webBaseURL
                            )
                        }
                    }
                }

            case .locked:
                if let manager = resolvedBiometricManager {
                    LockFlowHostView(
                        router: router,
                        sessionStore: sessionStore,
                        biometricManager: manager,
                        preferences: preferences,
                        idiom: resolvedIdiom
                    )
                } else {
                    LockedGateView(
                        locale: preferences.locale,
                        onSignOut: { Task { await sessionStore.signOut() } }
                    )
                }
            }
        } else {
            if let attempt = router.presentedAttempt {
                AttemptRoutes.attempt(
                    attemptId: attempt.attemptId,
                    destination: attempt.originDestination,
                    router: router,
                    preferences: preferences
                )
            } else {
                AdaptiveShellView(
                    router: router,
                    preferences: preferences,
                    idiom: resolvedIdiom,
                    serverDefaultThemeMode: resolvedPaletteSet.defaultMode,
                    legalRepository: legalRepository,
                    webBaseURL: webBaseURL
                )
            }
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

private struct LockFlowHostView: View {
    @Bindable var router: AppRouter
    let sessionStore: SessionStore
    let biometricManager: BiometricManager
    @Bindable var preferences: AppPreferences
    let idiom: UIUserInterfaceIdiom

    var body: some View {
        let lockVM = router.lockViewModel(sessionStore: sessionStore, biometricManager: biometricManager)
        if lockVM.isShowingPasswordFallback {
            LockPasswordFallbackHostView(
                sessionStore: sessionStore,
                preferences: preferences,
                idiom: idiom,
                prefilledIdentifier: lockVM.lockedUsername,
                onBackToLock: { lockVM.closePasswordFallback() },
                onForgotPassword: { router.recoveryOverlay = .forgotPassword }
            )
        } else {
            LockView(
                viewModel: lockVM,
                preferences: preferences,
                displayName: router.cachedAccountDisplayName
            )
        }
    }
}

private struct LockPasswordFallbackHostView: View {
    @Bindable var preferences: AppPreferences
    let idiom: UIUserInterfaceIdiom
    let onBackToLock: () -> Void
    let onForgotPassword: () -> Void

    @State private var viewModel: LoginViewModel

    init(
        sessionStore: SessionStore,
        preferences: AppPreferences,
        idiom: UIUserInterfaceIdiom,
        prefilledIdentifier: String,
        onBackToLock: @escaping () -> Void,
        onForgotPassword: @escaping () -> Void
    ) {
        self.preferences = preferences
        self.idiom = idiom
        self.onBackToLock = onBackToLock
        self.onForgotPassword = onForgotPassword
        _viewModel = State(
            initialValue: LoginViewModel(
                sessionStore: sessionStore,
                prefilledIdentifier: prefilledIdentifier,
                initialReason: .none,
                isLockPasswordFallback: true
            )
        )
    }

    var body: some View {
        LoginView(
            viewModel: viewModel,
            preferences: preferences,
            idiom: idiom,
            onForgotPassword: onForgotPassword,
            onRegister: {},
            onBackToLock: onBackToLock
        )
    }
}

private struct SignedOutFlowHostView: View {
    @Bindable var router: AppRouter
    let sessionStore: SessionStore
    @Bindable var preferences: AppPreferences
    let idiom: UIUserInterfaceIdiom
    let reason: SignedOutReason
    let legalRepository: LegalRepository
    let webBaseURL: URL

    var body: some View {
        switch router.signedOutRoute {
        case .login:
            LoginFlowHostView(
                sessionStore: sessionStore,
                preferences: preferences,
                idiom: idiom,
                reason: reason,
                onForgotPassword: { router.signedOutRoute = .forgotPassword },
                onRegister: {
                    router.signedOutPath = []
                    router.signedOutRoute = .register
                }
            )
        case .register:
            NavigationStack(path: $router.signedOutPath) {
                RegisterView(
                    viewModel: router.registerViewModel(
                        sessionStore: sessionStore,
                        legalRepository: legalRepository
                    ),
                    preferences: preferences,
                    onBackToLogin: {
                        router.clearRegisterViewModel()
                        router.signedOutRoute = .login
                    },
                    onOpenLegal: { legalId in
                        router.openSignedOutLegal(legalId)
                    }
                )
                .toolbar(.hidden, for: .navigationBar)
                .navigationDestination(for: AppRoute.self) { route in
                    AppRouteView(
                        route: route,
                        destination: .home,
                        router: router,
                        preferences: preferences,
                        legalRepository: legalRepository,
                        webBaseURL: webBaseURL
                    )
                }
            }
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
