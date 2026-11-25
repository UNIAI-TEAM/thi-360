import Foundation
import Observation

@Observable
@MainActor
final class AccountViewModel {
    typealias ProfileLoader = @Sendable () async throws -> Components.Schemas.ProfileResponse

    private(set) var profileState: AccountProfileLoadState = .loading
    private(set) var biometricFeedbackKey: String?
    private(set) var isUpdatingBiometric: Bool = false
    private(set) var isLoggingOut: Bool = false
    var isShowingLogoutConfirmation: Bool = false
    var selectedDetail: AccountDetailSelection = .changePassword

    private var hasLoadedProfile: Bool = false
    private var isLoadingProfile: Bool = false

    let sessionStore: SessionStore
    let biometricManager: BiometricManager
    private let profileLoader: ProfileLoader

    init(
        sessionStore: SessionStore,
        biometricManager: BiometricManager,
        profileLoader: ProfileLoader? = nil
    ) {
        self.sessionStore = sessionStore
        self.biometricManager = biometricManager
        self.profileLoader = profileLoader ?? {
            try await sessionStore.apiClient.getMyProfile()
        }
    }

    var loadedProfile: AccountProfile? {
        if case let .loaded(profile) = profileState {
            return profile
        }
        return nil
    }

    var isBiometricSwitchOn: Bool {
        biometricManager.isEnabled
    }

    var isBiometricSwitchDisabled: Bool {
        !biometricManager.capability.isAvailable || isUpdatingBiometric
    }

    func biometricTitle(locale: AppLocale) -> String {
        switch biometricManager.capability.kind {
        case .faceID, .touchID:
            AccountStrings.text(
                "account.biometric.unlockWith",
                locale: locale,
                replacements: ["biometric": biometricManager.capability.kind.displayName]
            )
        case .none:
            AccountStrings.text("account.biometric.genericTitle", locale: locale)
        }
    }

    func biometricSubtitle(locale: AppLocale) -> String {
        switch biometricManager.state {
        case .on, .off:
            return AccountStrings.text("account.biometric.subtitle", locale: locale)
        case let .unavailable(reason):
            switch reason {
            case .noHardware:
                return AccountStrings.text("account.biometric.unavailable.noHardware", locale: locale)
            case .notEnrolled:
                return AccountStrings.text("account.biometric.unavailable.notEnrolled", locale: locale)
            case .passcodeNotSet:
                return AccountStrings.text("account.biometric.unavailable.passcodeNotSet", locale: locale)
            }
        }
    }

    func biometricFeedbackMessage(locale: AppLocale) -> String? {
        guard let key = biometricFeedbackKey else { return nil }
        let name = biometricManager.capability.kind.displayName
        let resolvedName = name.isEmpty ? AccountStrings.text("account.biometric.genericTitle", locale: locale) : name
        return AccountStrings.text(key, locale: locale, replacements: ["biometric": resolvedName])
    }

    func loadProfileIfNeeded() async {
        guard !hasLoadedProfile else { return }
        await loadProfile()
    }

    func loadProfile() async {
        guard !isLoadingProfile else { return }
        isLoadingProfile = true
        profileState = .loading
        defer { isLoadingProfile = false }

        do {
            let response = try await profileLoader()
            guard sessionStore.state.isAuthenticated else { return }
            profileState = .loaded(AccountProfile(from: response))
            hasLoadedProfile = true
        } catch {
            guard sessionStore.state.isAuthenticated else { return }
            let apiError = MobileApiError.classify(error)
            if apiError.isOfflineOrConnectivity {
                profileState = .offline
            } else {
                profileState = .error
            }
        }
    }

    func setBiometricEnabled(_ enabled: Bool, locale: AppLocale) async {
        guard !isUpdatingBiometric else { return }
        biometricFeedbackKey = nil

        if !enabled {
            biometricManager.disable()
            return
        }

        biometricManager.refreshCapability()
        guard biometricManager.capability.isAvailable else { return }
        guard let refreshToken = sessionStore.currentRefreshToken, !refreshToken.isEmpty else {
            biometricFeedbackKey = "account.biometric.enableFailed"
            return
        }

        isUpdatingBiometric = true
        defer { isUpdatingBiometric = false }

        let reason = AccountStrings.text(
            "account.biometric.reasonEnable",
            locale: locale,
            replacements: ["biometric": biometricManager.capability.kind.displayName]
        )
        let result = await biometricManager.enable(refreshToken: refreshToken, reason: reason)
        switch result {
        case .succeeded:
            biometricFeedbackKey = nil
        case .lockout:
            biometricFeedbackKey = "account.biometric.lockout"
        case .failed:
            biometricFeedbackKey = "account.biometric.enableFailed"
        case .cancelled, .invalidated, .unavailable:
            break
        }
    }

    func requestLogout() {
        guard !isLoggingOut else { return }
        isShowingLogoutConfirmation = true
    }

    func cancelLogout() {
        isShowingLogoutConfirmation = false
    }

    func confirmLogout() async {
        guard !isLoggingOut else { return }
        isLoggingOut = true
        isShowingLogoutConfirmation = false
        defer { isLoggingOut = false }

        await sessionStore.signOut(reason: .none)
        resetForSessionChange()
    }

    func resetForSessionChange() {
        profileState = .loading
        hasLoadedProfile = false
        isLoadingProfile = false
        biometricFeedbackKey = nil
        isShowingLogoutConfirmation = false
        isLoggingOut = false
        selectedDetail = .changePassword
    }
}
