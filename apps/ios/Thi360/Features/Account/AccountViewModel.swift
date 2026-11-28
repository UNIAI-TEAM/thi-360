import Foundation
import Observation
import UIKit

@Observable
@MainActor
final class AccountViewModel {
    typealias ProfileLoader = @Sendable () async throws -> Components.Schemas.ProfileResponse

    let profileStore: AccountProfileStore
    let profileService: any ProfileServiceProtocol
    let accountDeletionService: any AccountDeletionServiceProtocol
    let clock: any AppClock
    let cameraAccess: any CameraAccessProviding
    let openSettings: @MainActor () -> Void
    let announce: @MainActor (String) -> Void

    private(set) var biometricFeedbackKey: String?
    private(set) var isUpdatingBiometric: Bool = false
    private(set) var isLoggingOut: Bool = false
    var isShowingLogoutConfirmation: Bool = false
    var selectedDetail: AccountDetailSelection = .profile
    var hasExplicitDetailSelection: Bool = false
    @ObservationIgnored var isInTwoPaneLayout: Bool = false

    let sessionStore: SessionStore
    let biometricManager: BiometricManager

    @ObservationIgnored private var cachedProfileVM: ProfileViewModel?
    @ObservationIgnored private var cachedVerifyEmailModel: VerifyEmailModel?
    @ObservationIgnored private var cachedDeleteAccountVM: DeleteAccountViewModel?
    @ObservationIgnored private var cleanupRegistrationTask: Task<Void, Never>?
    @ObservationIgnored private weak var attachedImageLoader: ImageLoader?

    init(
        sessionStore: SessionStore,
        biometricManager: BiometricManager,
        profileService: (any ProfileServiceProtocol)? = nil,
        accountDeletionService: (any AccountDeletionServiceProtocol)? = nil,
        profileLoader: ProfileLoader? = nil,
        clock: any AppClock = SystemClock(),
        cameraAccess: (any CameraAccessProviding)? = nil,
        openSettings: (@MainActor () -> Void)? = nil,
        announce: (@MainActor (String) -> Void)? = nil
    ) {
        self.sessionStore = sessionStore
        self.biometricManager = biometricManager
        let resolvedProfileService: any ProfileServiceProtocol = profileService
            ?? OpenAPIProfileService(apiClient: sessionStore.apiClient)
        self.profileService = resolvedProfileService
        self.accountDeletionService = accountDeletionService
            ?? OpenAPIAccountDeletionService(apiClient: sessionStore.apiClient)
        self.clock = clock
        self.cameraAccess = cameraAccess ?? SystemCameraAccess()
        self.openSettings = openSettings ?? {
            guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
            UIApplication.shared.open(url)
        }
        self.announce = announce ?? { message in
            UIAccessibility.post(notification: .announcement, argument: message)
        }

        if let profileLoader {
            self.profileStore = AccountProfileStore(
                fetchProfile: { AccountProfile(from: try await profileLoader()) },
                isAuthenticatedProvider: { sessionStore.state.isAuthenticated }
            )
        } else {
            self.profileStore = AccountProfileStore(
                profileService: resolvedProfileService,
                sessionStore: sessionStore
            )
        }

        let participant = self.profileStore.makeCleanupParticipant()
        self.cleanupRegistrationTask = Task {
            await sessionStore.sessionCleanup.register(participant)
        }
    }

    var profileState: LoadState<AccountProfile> {
        profileStore.state
    }

    var loadedProfile: AccountProfile? {
        profileStore.loadedProfile
    }

    var isBiometricSwitchOn: Bool {
        biometricManager.isEnabled
    }

    var isBiometricSwitchDisabled: Bool {
        !biometricManager.capability.isAvailable || isUpdatingBiometric
    }

    func ensureCleanupRegistered() async {
        await cleanupRegistrationTask?.value
    }

    func ensureProfileViewModel() -> ProfileViewModel {
        if let existing = cachedProfileVM {
            return existing
        }
        let vm = ProfileViewModel(
            profileStore: profileStore,
            profileService: profileService,
            cameraAccess: cameraAccess,
            clock: clock,
            openSettings: openSettings,
            announce: announce
        )
        cachedProfileVM = vm
        return vm
    }

    func existingProfileViewModel() -> ProfileViewModel? {
        cachedProfileVM
    }

    func clearProfileViewModel() {
        cachedProfileVM?.clearSensitiveInputs()
        cachedProfileVM = nil
    }

    func clearProfileViewModelIfPopped(router: AppRouter, destination: Destination) {
        if isInTwoPaneLayout && selectedDetail == .profile {
            return
        }
        guard !router.path(for: destination).contains(.profile) else { return }
        clearProfileViewModel()
    }

    func handleEmbeddedProfileDisappear(isOnAccountTab: Bool, accountPath: [AppRoute]) {
        if !isOnAccountTab {
            clearProfileViewModel()
            return
        }
        if accountPath.contains(.profileVerifyEmail) {
            cachedProfileVM?.resetCooldown()
        }
    }

    func handleAccountTabExitInTwoPane() {
        if selectedDetail == .profile {
            clearProfileViewModel()
        }
        if selectedDetail == .deleteAccount {
            cachedDeleteAccountVM?.markRecheckNeededIfBlocked()
        }
    }

    func verifyEmailModel() -> VerifyEmailModel {
        if let existing = cachedVerifyEmailModel {
            return existing
        }
        let model = VerifyEmailModel(
            profileStore: profileStore,
            profileService: profileService,
            clock: clock,
            announce: announce
        )
        cachedVerifyEmailModel = model
        return model
    }

    func clearVerifyEmailModelIfPopped(router: AppRouter, destination: Destination) {
        guard !router.path(for: destination).contains(.profileVerifyEmail) else { return }
        cachedVerifyEmailModel = nil
    }

    func clearVerifyEmailModel() {
        cachedVerifyEmailModel = nil
    }

    func ensureDeleteAccountViewModel() -> DeleteAccountViewModel {
        if let existing = cachedDeleteAccountVM {
            return existing
        }
        let vm = DeleteAccountViewModel(
            deletionService: accountDeletionService,
            sessionStore: sessionStore
        )
        cachedDeleteAccountVM = vm
        return vm
    }

    func clearDeleteAccountViewModelIfPopped(router: AppRouter, destination: Destination, isEmbedded: Bool) {
        if isEmbedded {
            return
        }
        guard !router.path(for: destination).contains(.deleteAccount) else { return }
        cachedDeleteAccountVM = nil
    }

    func clearDeleteAccountViewModel() {
        cachedDeleteAccountVM = nil
    }

    func selectDetail(_ selection: AccountDetailSelection) {
        if selectedDetail == .profile && selection != .profile {
            clearProfileViewModel()
        }
        if selectedDetail == .deleteAccount && selection != .deleteAccount {
            cachedDeleteAccountVM?.clearSensitiveInputs()
            cachedDeleteAccountVM = nil
        }
        selectedDetail = selection
        hasExplicitDetailSelection = true
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
        guard !profileStore.hasLoadedProfile else { return }
        _ = await profileStore.refresh()
    }

    func loadProfile() async {
        _ = await profileStore.refresh()
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

        await ensureCleanupRegistered()
        await sessionStore.signOut(reason: .none)
        resetForSessionChange()
    }

    func attachImageLoader(_ loader: ImageLoader?) {
        guard let loader else { return }
        attachedImageLoader = loader
        AvatarInFlightLoader.track(loader)
    }

    func resetForSessionChange() {
        profileStore.wipe()
        attachedImageLoader?.clearMemoryCache()
        AvatarInFlightLoader.clearAllCaches()
        cachedProfileVM?.clearSensitiveInputs()
        cachedProfileVM = nil
        cachedVerifyEmailModel = nil
        cachedDeleteAccountVM?.clearSensitiveInputs()
        cachedDeleteAccountVM = nil
        biometricFeedbackKey = nil
        isShowingLogoutConfirmation = false
        isLoggingOut = false
        selectedDetail = .profile
        hasExplicitDetailSelection = false
    }
}
