import Foundation
import Observation

@Observable
@MainActor
final class VerifyEmailModel {
    enum ScreenState: Equatable, Sendable {
        case loading
        case loadFailed
        case alreadyVerified
        case ready(email: String)
        case verified
    }

    let profileStore: AccountProfileStore
    let profileService: any ProfileServiceProtocol
    let clock: any AppClock
    let announce: @MainActor (String) -> Void

    private(set) var screenState: ScreenState = .loading
    private(set) var autoSendAttempted: Bool = false
    var code: String = ""
    private(set) var codeErrorKey: String?
    private(set) var remainingAttempts: Int?
    private(set) var alertKey: String?
    private(set) var showCodeResentNotice: Bool = false
    private(set) var isSubmitting: Bool = false
    private(set) var isResending: Bool = false
    private(set) var cooldown: SendCooldown = .ready
    private(set) var cooldownTick: Int = 0

    @ObservationIgnored private var isLoadingProfile: Bool = false
    @ObservationIgnored private var didAnnounceCooldownEnd: Bool = true

    init(
        profileStore: AccountProfileStore,
        profileService: any ProfileServiceProtocol,
        clock: any AppClock = SystemClock(),
        announce: @escaping @MainActor (String) -> Void = { _ in }
    ) {
        self.profileStore = profileStore
        self.profileService = profileService
        self.clock = clock
        self.announce = announce
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

    var dailyLimitUntilDate: Date? {
        if case let .dailyLimit(until) = activeCooldown {
            return until
        }
        return nil
    }

    func loadAndAutoSendIfNeeded(locale: AppLocale) async {
        switch screenState {
        case .ready, .alreadyVerified, .verified:
            return
        case .loading, .loadFailed:
            break
        }
        await loadProfileAndAutoSend(locale: locale)
    }

    func retryLoad(locale: AppLocale) async {
        await loadProfileAndAutoSend(locale: locale)
    }

    func updateCode(_ value: String) {
        code = value
        codeErrorKey = nil
        remainingAttempts = nil
    }

    func submitCode(onVerified: @MainActor () -> Void) async {
        guard !isSubmitting else { return }
        codeErrorKey = nil
        remainingAttempts = nil
        alertKey = nil

        guard PersonalInfoValidator.isValidVerificationCode(code) else {
            codeErrorKey = "account.verifyEmail.codeRequired"
            return
        }

        isSubmitting = true
        defer { isSubmitting = false }

        let submitted = code
        switch await profileService.verifyEmail(code: submitted) {
        case .success:
            if let fresh = await profileStore.refresh() {
                profileStore.replace(fresh)
            } else if let current = profileStore.loadedProfile {
                profileStore.replace(current.withEmailVerified(true))
            }
            screenState = .verified
            onVerified()
        case let .failure(error):
            switch error {
            case let .codeInvalid(remaining):
                remainingAttempts = remaining
                codeErrorKey = "account.verifyEmail.codeInvalid"
            case .codeExpired:
                alertKey = "account.verifyEmail.alerts.codeExpired"
            default:
                alertKey = "account.verifyEmail.alerts.generic"
            }
        }
    }

    func resendCode(locale: AppLocale) async {
        guard !isResending else { return }
        tickCooldown(locale: locale)
        guard !isCooldownActive else { return }

        alertKey = nil
        showCodeResentNotice = false
        isResending = true
        defer { isResending = false }

        switch await profileService.sendVerification() {
        case .success:
            showCodeResentNotice = true
            codeErrorKey = nil
            remainingAttempts = nil
            startCooldown(retryAfterSeconds: nil, locale: locale)
        case let .failure(.rateLimited(retryAfter)):
            startCooldown(retryAfterSeconds: retryAfter, locale: locale)
        case .failure:
            alertKey = "account.verifyEmail.alerts.resendFailed"
        }
    }

    func tickCooldown(locale: AppLocale) {
        cooldownTick &+= 1
        let previous = cooldown
        let next = cooldown.resolved(clock: clock)
        cooldown = next
        if previous != .ready, next == .ready, !didAnnounceCooldownEnd {
            didAnnounceCooldownEnd = true
            announce(ProfileStrings.text("account.verifyEmail.cooldownEnd", locale: locale))
        }
    }

    private func loadProfileAndAutoSend(locale: AppLocale) async {
        guard !isLoadingProfile else { return }
        isLoadingProfile = true
        screenState = .loading
        defer { isLoadingProfile = false }

        let fetched: AccountProfile
        do {
            fetched = try await profileService.fetchProfile()
            profileStore.replace(fetched)
        } catch {
            screenState = .loadFailed
            return
        }

        if fetched.emailVerified || !fetched.hasEmail {
            screenState = .alreadyVerified
            return
        }

        guard let email = fetched.trimmedEmail else {
            screenState = .alreadyVerified
            return
        }

        screenState = .ready(email: email)

        guard !autoSendAttempted else { return }
        autoSendAttempted = true

        switch await profileService.sendVerification() {
        case .success:
            startCooldown(retryAfterSeconds: nil, locale: locale)
        case let .failure(.rateLimited(retryAfter)):
            startCooldown(retryAfterSeconds: retryAfter, locale: locale)
        case .failure:
            alertKey = "account.verifyEmail.alerts.resendFailed"
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
                    "account.verifyEmail.cooldownStart",
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
}
