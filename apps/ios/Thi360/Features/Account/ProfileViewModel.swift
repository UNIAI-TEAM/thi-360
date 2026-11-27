import Foundation
import Observation

struct ProfileToastMessage: Equatable, Identifiable, Sendable {
    let id: UUID
    let message: String

    init(id: UUID = UUID(), message: String) {
        self.id = id
        self.message = message
    }
}

@Observable
@MainActor
final class ProfileViewModel {
    let profileStore: AccountProfileStore
    let profileService: any ProfileServiceProtocol
    let cameraAccess: any CameraAccessProviding
    let clock: any AppClock
    let openSettings: @MainActor () -> Void
    let announce: @MainActor (String) -> Void

    var fullName: String = ""
    var phone: String = ""
    private(set) var fullNameError: FullNameValidationError?
    private(set) var phoneError: PhoneValidationError?
    private(set) var infoGenericError: Bool = false
    private(set) var isSavingInfo: Bool = false

    private(set) var isUploadingAvatar: Bool = false
    private(set) var isRemovingAvatar: Bool = false
    var isShowingAvatarSourceDialog: Bool = false
    var isShowingPhotosPicker: Bool = false
    var isShowingCameraPicker: Bool = false
    private(set) var isShowingCameraDeniedBanner: Bool = false
    private(set) var avatarErrorKey: String?

    var isShowingChangeEmailForm: Bool = false
    var newEmail: String = ""
    var currentPassword: String = ""
    var isCurrentPasswordVisible: Bool = false
    private(set) var newEmailErrorKey: String?
    private(set) var currentPasswordErrorKey: String?
    private(set) var changeEmailAlertKey: String?
    private(set) var isSendingEmailChange: Bool = false

    var pendingCode: String = ""
    private(set) var pendingCodeErrorKey: String?
    private(set) var pendingCodeRemainingAttempts: Int?
    private(set) var pendingAlertKey: String?
    private(set) var isConfirmingEmailChange: Bool = false

    private(set) var cooldown: SendCooldown = .ready
    private(set) var cooldownTick: Int = 0
    private(set) var activeToast: ProfileToastMessage?

    @ObservationIgnored private var syncedProfile: AccountProfile?
    @ObservationIgnored private var baselineUsername: String?
    @ObservationIgnored private var baselineFullName: String = ""
    @ObservationIgnored private var baselinePhone: String = ""
    @ObservationIgnored private var didAnnounceCooldownEnd: Bool = true
    @ObservationIgnored private var toastDismissTask: Task<Void, Never>?

    init(
        profileStore: AccountProfileStore,
        profileService: any ProfileServiceProtocol,
        cameraAccess: any CameraAccessProviding,
        clock: any AppClock = SystemClock(),
        openSettings: @escaping @MainActor () -> Void = {},
        announce: @escaping @MainActor (String) -> Void = { _ in }
    ) {
        self.profileStore = profileStore
        self.profileService = profileService
        self.cameraAccess = cameraAccess
        self.clock = clock
        self.openSettings = openSettings
        self.announce = announce

        if let existing = profileStore.loadedProfile {
            syncFields(from: existing, forceReset: false)
        }
    }

    var profileState: LoadState<AccountProfile> {
        profileStore.state
    }

    var refreshError: LoadFailureKind? {
        profileStore.refreshError
    }

    var profile: AccountProfile? {
        profileStore.loadedProfile
    }

    var isAvatarBusy: Bool {
        isUploadingAvatar || isRemovingAvatar
    }

    var hasCamera: Bool {
        cameraAccess.isAvailable
    }

    var isInfoDirty: Bool {
        guard let profile else { return false }
        return PersonalInfoValidator.isDirty(fullName: fullName, phone: phone, saved: profile)
    }

    var hasUnsavedDraft: Bool {
        isInfoDirty || isShowingChangeEmailForm || !pendingCode.isEmpty
    }

    var activeCooldown: SendCooldown {
        _ = cooldownTick
        return cooldown.resolved(clock: clock)
    }

    var cooldownRemainingSeconds: Int {
        _ = cooldownTick
        return cooldown.remainingSeconds(clock: clock)
    }

    var isCooldownActive: Bool {
        activeCooldown != .ready
    }

    func onAppearLoad() async {
        if let existing = profileStore.loadedProfile, baselineUsername == nil {
            syncFields(from: existing, forceReset: false)
        }
        if let updated = await profileStore.refresh() {
            syncFields(from: updated, forceReset: false)
        }
    }

    func retryLoad() async {
        if let updated = await profileStore.refresh() {
            syncFields(from: updated, forceReset: false)
        }
    }

    func syncFromStoreIfChanged() {
        guard let current = profileStore.loadedProfile, current != syncedProfile else { return }
        syncFields(from: current, forceReset: false)
    }

    func updateFullName(_ raw: String) {
        fullName = PersonalInfoValidator.clampFullNameInput(raw)
        if fullNameError != nil {
            fullNameError = nil
        }
        infoGenericError = false
    }

    func updatePhone(_ raw: String) {
        phone = raw
        if phoneError != nil {
            phoneError = nil
        }
        infoGenericError = false
    }

    func discardInfoChanges() {
        guard let profile else { return }
        fullName = profile.fullName
        phone = profile.phone ?? ""
        fullNameError = nil
        phoneError = nil
        infoGenericError = false
    }

    func savePersonalInfo(locale: AppLocale) async {
        guard !isSavingInfo, let profile else { return }
        fullNameError = nil
        phoneError = nil
        infoGenericError = false

        let validation = PersonalInfoValidator.validate(
            fullName: fullName,
            phone: phone,
            savedPhone: profile.phone
        )
        if !validation.isValid {
            fullNameError = validation.fullNameError
            phoneError = validation.phoneError
            return
        }

        let patch = PersonalInfoValidator.buildPatch(
            fullName: fullName,
            phone: phone,
            savedPhone: profile.phone
        )

        isSavingInfo = true
        defer { isSavingInfo = false }

        switch await profileService.updateProfile(fullName: patch.fullName, phone: patch.phone) {
        case let .success(updated):
            profileStore.replace(updated)
            syncFields(from: updated, forceReset: true)
            showToast(ProfileStrings.text("account.profile.info.saved", locale: locale))
        case let .failure(error):
            applyInfoError(error)
        }
    }

    func requestAvatarSourceSelection() {
        guard !isAvatarBusy else { return }
        avatarErrorKey = nil
        isShowingCameraDeniedBanner = false
        isShowingAvatarSourceDialog = true
    }

    func selectGallerySource() {
        isShowingAvatarSourceDialog = false
        isShowingCameraDeniedBanner = false
        isShowingPhotosPicker = true
    }

    func selectCameraSource() async {
        isShowingAvatarSourceDialog = false
        guard cameraAccess.isAvailable else { return }

        switch cameraAccess.authorization {
        case .granted:
            isShowingCameraDeniedBanner = false
            isShowingCameraPicker = true
        case .denied:
            isShowingCameraDeniedBanner = true
            isShowingCameraPicker = false
        case .notDetermined:
            let granted = await cameraAccess.request()
            if granted {
                isShowingCameraDeniedBanner = false
                isShowingCameraPicker = true
            } else {
                isShowingCameraDeniedBanner = true
                isShowingCameraPicker = false
            }
        }
    }

    func openSystemSettings() {
        openSettings()
    }

    func cancelMediaPicker() {
        isShowingAvatarSourceDialog = false
        isShowingPhotosPicker = false
        isShowingCameraPicker = false
    }

    func handleSelectedImageData(_ rawData: Data, locale: AppLocale) async {
        guard !isAvatarBusy else { return }
        avatarErrorKey = nil
        isShowingCameraDeniedBanner = false
        isUploadingAvatar = true
        defer { isUploadingAvatar = false }

        let prepared = AvatarImagePreparer.prepare(rawData)
        switch prepared {
        case .unreadable:
            avatarErrorKey = "account.profile.avatar.errors.INVALID_IMAGE"
            return
        case let .prepared(data, mime):
            let avatar = PreparedAvatar(data: data, mimeType: mime)
            switch await profileService.uploadAvatar(avatar) {
            case let .success(updated):
                profileStore.replace(updated)
                syncedProfile = updated
                showToast(ProfileStrings.text("account.profile.avatar.uploaded", locale: locale))
            case let .failure(error):
                avatarErrorKey = Self.avatarErrorKey(for: error)
            }
        }
    }

    func removeAvatar(locale: AppLocale) async {
        guard !isAvatarBusy else { return }
        avatarErrorKey = nil
        isRemovingAvatar = true
        defer { isRemovingAvatar = false }

        switch await profileService.removeAvatar() {
        case .success:
            if let current = profileStore.loadedProfile {
                let updated = current.clearingAvatar()
                profileStore.replace(updated)
                syncedProfile = updated
            }
            showToast(ProfileStrings.text("account.profile.avatar.removed", locale: locale))
        case .failure:
            avatarErrorKey = "account.profile.avatar.errors.generic"
        }
    }

    func toggleChangeEmailForm() {
        if isShowingChangeEmailForm {
            cancelChangeEmailForm()
        } else {
            isShowingChangeEmailForm = true
            newEmailErrorKey = nil
            currentPasswordErrorKey = nil
            changeEmailAlertKey = nil
        }
    }

    func cancelChangeEmailForm() {
        isShowingChangeEmailForm = false
        currentPassword = ""
        isCurrentPasswordVisible = false
        newEmailErrorKey = nil
        currentPasswordErrorKey = nil
        changeEmailAlertKey = nil
    }

    func updateNewEmail(_ value: String) {
        newEmail = value
        newEmailErrorKey = nil
        changeEmailAlertKey = nil
    }

    func updateCurrentPassword(_ value: String) {
        currentPassword = value
        currentPasswordErrorKey = nil
        changeEmailAlertKey = nil
    }

    func submitEmailChange(locale: AppLocale) async {
        guard !isSendingEmailChange else { return }
        tickCooldown(locale: locale)
        guard !isCooldownActive else { return }

        newEmailErrorKey = nil
        currentPasswordErrorKey = nil
        changeEmailAlertKey = nil

        let trimmedEmail = newEmail.trimmingCharacters(in: .whitespacesAndNewlines)
        var hasValidationError = false
        if trimmedEmail.isEmpty {
            newEmailErrorKey = "account.profile.email.newEmailRequired"
            hasValidationError = true
        } else if !PersonalInfoValidator.isValidEmail(trimmedEmail) {
            newEmailErrorKey = "account.profile.email.newEmailInvalid"
            hasValidationError = true
        }
        if currentPassword.isEmpty {
            currentPasswordErrorKey = "account.profile.email.currentPasswordRequired"
            hasValidationError = true
        }
        if hasValidationError {
            return
        }

        isSendingEmailChange = true
        defer { isSendingEmailChange = false }

        let submittedPassword = currentPassword
        switch await profileService.requestEmailChange(newEmail: trimmedEmail, currentPassword: submittedPassword) {
        case .success:
            isShowingChangeEmailForm = false
            currentPassword = ""
            isCurrentPasswordVisible = false
            newEmail = ""
            pendingCode = ""
            pendingCodeErrorKey = nil
            pendingCodeRemainingAttempts = nil
            pendingAlertKey = nil
            if let current = profileStore.loadedProfile {
                let updated = current.withPendingEmail(trimmedEmail)
                profileStore.replace(updated)
                syncedProfile = updated
            }
            startCooldown(retryAfterSeconds: nil, locale: locale)
        case let .failure(error):
            applyEmailChangeError(error, locale: locale)
        }
    }

    func updatePendingCode(_ raw: String) {
        pendingCode = raw
        pendingCodeErrorKey = nil
        pendingCodeRemainingAttempts = nil
        pendingAlertKey = nil
    }

    func confirmPendingEmail(locale: AppLocale) async {
        guard !isConfirmingEmailChange else { return }
        pendingCodeErrorKey = nil
        pendingCodeRemainingAttempts = nil
        pendingAlertKey = nil

        guard PersonalInfoValidator.isValidVerificationCode(pendingCode) else {
            pendingCodeErrorKey = "account.profile.email.codeRequired"
            return
        }

        isConfirmingEmailChange = true
        defer { isConfirmingEmailChange = false }

        let submittedCode = pendingCode
        switch await profileService.confirmEmailChange(code: submittedCode) {
        case .success:
            pendingCode = ""
            if let current = profileStore.loadedProfile, let pending = current.trimmedPendingEmail {
                let updated = current.withConfirmedEmail(pending)
                profileStore.replace(updated)
                syncedProfile = updated
            }
            showToast(ProfileStrings.text("account.profile.email.changed", locale: locale))
        case let .failure(error):
            applyConfirmEmailError(error)
        }
    }

    func resendPendingEmailCode(locale: AppLocale) {
        tickCooldown(locale: locale)
        guard !isCooldownActive, let pending = profile?.trimmedPendingEmail else { return }
        newEmail = pending
        currentPassword = ""
        isCurrentPasswordVisible = false
        newEmailErrorKey = nil
        currentPasswordErrorKey = nil
        changeEmailAlertKey = nil
        isShowingChangeEmailForm = true
    }

    func tickCooldown(locale: AppLocale) {
        cooldownTick &+= 1
        let previous = cooldown
        let next = cooldown.resolved(clock: clock)
        cooldown = next
        if previous != .ready, next == .ready, !didAnnounceCooldownEnd {
            didAnnounceCooldownEnd = true
            announce(ProfileStrings.text("account.profile.email.cooldownEnd", locale: locale))
        }
    }

    func dismissToast() {
        toastDismissTask?.cancel()
        toastDismissTask = nil
        activeToast = nil
    }

    func clearSensitiveInputs() {
        currentPassword = ""
        isCurrentPasswordVisible = false
        pendingCode = ""
        toastDismissTask?.cancel()
        toastDismissTask = nil
    }

    private func syncFields(from profile: AccountProfile, forceReset: Bool) {
        syncedProfile = profile
        let serverFullName = profile.fullName
        let serverPhone = profile.phone ?? ""
        let infoChangedOnServer = baselineUsername != profile.username
            || baselineFullName != serverFullName
            || baselinePhone != serverPhone
        if baselineUsername == nil || forceReset || infoChangedOnServer {
            baselineUsername = profile.username
            baselineFullName = serverFullName
            baselinePhone = serverPhone
            fullName = serverFullName
            phone = serverPhone
            fullNameError = nil
            phoneError = nil
            infoGenericError = false
        }
    }

    private func startCooldown(retryAfterSeconds: Int?, locale: AppLocale) {
        let next = SendCooldown.start(retryAfterSeconds: retryAfterSeconds, clock: clock)
        cooldown = next
        cooldownTick &+= 1
        switch next {
        case .counting:
            didAnnounceCooldownEnd = false
            let seconds = next.remainingSeconds(clock: clock)
            let formatted = SendCooldown.formatClock(seconds)
            announce(
                ProfileStrings.text(
                    "account.profile.email.cooldownStart",
                    locale: locale,
                    replacements: ["time": formatted]
                )
            )
        case .dailyLimit:
            didAnnounceCooldownEnd = false
        case .ready:
            didAnnounceCooldownEnd = true
        }
    }

    private func applyInfoError(_ error: ProfileError) {
        switch error {
        case let .field(name, code) where name == "fullName":
            fullNameError = (code == "SIZE") ? .tooLong : .required
        case let .field(name, _) where name == "phone":
            phoneError = .invalid
        default:
            infoGenericError = true
        }
    }

    private func applyEmailChangeError(_ error: ProfileError, locale: AppLocale) {
        switch error {
        case .emailTaken:
            newEmailErrorKey = "account.profile.email.newEmailTaken"
        case let .field(name, _) where name == "currentPassword":
            currentPasswordErrorKey = "account.profile.email.wrongPassword"
        case let .field(name, _) where name == "newEmail":
            newEmailErrorKey = "account.profile.email.newEmailInvalid"
        case let .rateLimited(retryAfter):
            changeEmailAlertKey = "account.profile.email.changeErrors.RATE_LIMITED"
            startCooldown(retryAfterSeconds: retryAfter, locale: locale)
        case .throttled:
            changeEmailAlertKey = "account.profile.email.changeErrors.RATE_LIMITED"
            startCooldown(retryAfterSeconds: nil, locale: locale)
        default:
            changeEmailAlertKey = "account.profile.email.changeErrors.generic"
        }
    }

    private func applyConfirmEmailError(_ error: ProfileError) {
        switch error {
        case let .codeInvalid(remaining):
            pendingCodeRemainingAttempts = remaining
            pendingCodeErrorKey = "account.profile.email.codeInvalid"
        case .codeExpired:
            pendingAlertKey = "account.profile.email.confirmErrors.EXPIRED"
        case .emailTaken:
            pendingAlertKey = "account.profile.email.confirmErrors.EMAIL_TAKEN"
        case .syncFailed:
            pendingAlertKey = "account.profile.email.confirmErrors.SYNC_FAILED"
        default:
            pendingAlertKey = "account.profile.email.confirmErrors.generic"
        }
    }

    private static func avatarErrorKey(for error: ProfileError) -> String {
        switch error {
        case .avatarTooLarge:
            "account.profile.avatar.errors.TOO_LARGE"
        case .avatarDimensions:
            "account.profile.avatar.errors.DIMENSIONS_EXCEEDED"
        case .avatarInvalid:
            "account.profile.avatar.errors.INVALID_IMAGE"
        default:
            "account.profile.avatar.errors.generic"
        }
    }

    private func showToast(_ message: String) {
        toastDismissTask?.cancel()
        let toast = ProfileToastMessage(message: message)
        activeToast = toast
        toastDismissTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 3_500_000_000)
            guard !Task.isCancelled else { return }
            await MainActor.run {
                if self?.activeToast?.id == toast.id {
                    self?.activeToast = nil
                }
            }
        }
    }
}
