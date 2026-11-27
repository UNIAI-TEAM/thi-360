import Foundation
import Observation

@Observable
@MainActor
final class RegisterViewModel {
    static let defaultResendSeconds = 60

    var fullName: String = ""
    var username: String = ""
    var email: String = ""
    var password: String = ""
    var acceptTerms: Bool = false
    var isPasswordVisible: Bool = false

    var otpCode: String = ""

    private(set) var state: RegistrationState = .form(data: RegistrationFormData())
    private(set) var fullNameError: RegisterFieldError?
    private(set) var usernameError: RegisterFieldError?
    private(set) var emailError: RegisterFieldError?
    private(set) var passwordError: RegisterFieldError?
    private(set) var termsError: RegisterFieldError?
    private(set) var registerAlert: RegisterAlert?

    private(set) var otpCodeError: OtpFieldError?
    private(set) var otpAlert: OtpAlert?
    private(set) var didResendCode: Bool = false
    private(set) var dailyLimitUntil: Date?

    private(set) var isSubmitting: Bool = false
    private(set) var isVerifying: Bool = false
    private(set) var isResending: Bool = false
    private(set) var currentTime: Date

    private let sessionStore: SessionStore
    private let apiClient: any MobileRecoveryClientProtocol
    private let legalRepository: LegalRepository
    private let clock: any AppClock
    private let logger: RedactingAppLogger

    init(
        sessionStore: SessionStore,
        apiClient: (any MobileRecoveryClientProtocol)? = nil,
        legalRepository: LegalRepository = LegalRepository(),
        clock: any AppClock = SystemClock(),
        logger: RedactingAppLogger = RedactingAppLogger()
    ) {
        self.sessionStore = sessionStore
        self.apiClient = apiClient ?? sessionStore.apiClient
        self.legalRepository = legalRepository
        self.clock = clock
        self.logger = logger
        self.currentTime = clock.now
    }

    var passwordEvaluation: PasswordRuleEvaluation {
        PasswordRules.evaluate(password)
    }

    var resendSecondsRemaining: Int {
        guard case let .otp(_, _, resendAt, _) = state else { return 0 }
        let remaining = resendAt.timeIntervalSince(currentTime)
        return remaining > 0 ? Int(ceil(remaining)) : 0
    }

    var canResend: Bool {
        guard case .otp = state else { return false }
        return resendSecondsRemaining == 0 && !isResending && !isVerifying
    }

    func tick() {
        currentTime = clock.now
        if let dailyLimitUntil, currentTime >= dailyLimitUntil {
            self.dailyLimitUntil = nil
        }
    }

    func togglePasswordVisibility() {
        isPasswordVisible.toggle()
    }

    func updateOtpInput(_ raw: String) {
        let digitsOnly = String(raw.filter { $0.isASCII && $0.isNumber }.prefix(RegistrationValidation.otpLength))
        if otpCode != digitsOnly {
            otpCode = digitsOnly
        }
        if otpCodeError != nil && digitsOnly.count == RegistrationValidation.otpLength {
            otpCodeError = nil
        }
    }

    func clearFieldErrorOnEdit(
        fullName: Bool = false,
        username: Bool = false,
        email: Bool = false,
        password: Bool = false,
        terms: Bool = false
    ) {
        if fullName, fullNameError != nil {
            let trimmed = self.fullName.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmed.isEmpty && trimmed.count <= RegistrationValidation.maxFullNameLength {
                fullNameError = nil
            }
        }
        if username, usernameError != nil, RegistrationValidation.isValidUsername(self.username) {
            usernameError = nil
        }
        if email, emailError != nil, RegistrationValidation.isValidEmail(self.email) {
            emailError = nil
        }
        if password, passwordError != nil,
           PasswordRules.isStrong(self.password),
           !PasswordRules.matchesUsername(self.password, username: self.username)
        {
            passwordError = nil
        }
        if terms, termsError != nil, self.acceptTerms {
            termsError = nil
        }
    }

    @discardableResult
    func validateForm() -> Bool {
        let trimmedFullName = fullName.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedUsername = username.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedEmail = email.trimmingCharacters(in: .whitespacesAndNewlines)

        if trimmedFullName.isEmpty {
            fullNameError = .fullNameRequired
        } else if trimmedFullName.count > RegistrationValidation.maxFullNameLength {
            fullNameError = .fullNameTooLong
        } else {
            fullNameError = nil
        }

        if trimmedUsername.isEmpty {
            usernameError = .usernameRequired
        } else if !RegistrationValidation.isValidUsername(trimmedUsername) {
            usernameError = .usernamePattern
        } else {
            usernameError = nil
        }

        if trimmedEmail.isEmpty {
            emailError = .emailRequired
        } else if !RegistrationValidation.isValidEmail(trimmedEmail) {
            emailError = .emailInvalid
        } else {
            emailError = nil
        }

        if password.isEmpty {
            passwordError = .passwordRequired
        } else if !PasswordRules.isStrong(password) {
            passwordError = .passwordWeak
        } else if PasswordRules.matchesUsername(password, username: trimmedUsername) {
            passwordError = .passwordSameAsUsername
        } else {
            passwordError = nil
        }

        termsError = acceptTerms ? nil : .termsRequired

        return fullNameError == nil
            && usernameError == nil
            && emailError == nil
            && passwordError == nil
            && termsError == nil
    }

    func submitRegistration() async {
        guard !isSubmitting else { return }

        registerAlert = nil
        guard validateForm() else { return }

        isSubmitting = true
        defer { isSubmitting = false }

        let trimmedFullName = fullName.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedUsername = username.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedEmail = email.trimmingCharacters(in: .whitespacesAndNewlines)
        let submittedPassword = password

        let pendingId: String?
        if case let .form(_, existingId) = state {
            pendingId = existingId
        } else {
            pendingId = nil
        }

        do {
            let result = try await apiClient.startRegistration(
                username: trimmedUsername,
                fullName: trimmedFullName,
                email: trimmedEmail,
                password: submittedPassword,
                acceptTerms: true,
                termsVersion: legalRepository.termsVersion(),
                registrationId: pendingId
            )

            let savedData = RegistrationFormData(
                fullName: trimmedFullName,
                username: trimmedUsername,
                email: trimmedEmail,
                password: "",
                acceptTerms: true
            )
            password = ""
            isPasswordVisible = false
            otpCode = ""
            otpCodeError = nil
            otpAlert = nil
            didResendCode = false
            dailyLimitUntil = nil

            let now = clock.now
            currentTime = now
            let cooldownSeconds = max(0, result.resendAfterSeconds)
            let resendAt = now.addingTimeInterval(TimeInterval(cooldownSeconds))

            state = .otp(
                registrationId: result.registrationId,
                email: result.email,
                resendAt: resendAt,
                formData: savedData
            )
            logger.log(.info, category: "register", message: "Registration step 1 succeeded")
        } catch {
            let apiError = MobileApiError.classify(error)
            handleRegistrationError(apiError)
        }
    }

    func submitOtp() async {
        guard case let .otp(registrationId, _, _, savedData) = state, !isVerifying else { return }

        otpAlert = nil
        otpCodeError = nil
        didResendCode = false

        let code = otpCode.trimmingCharacters(in: .whitespacesAndNewlines)
        guard RegistrationValidation.isValidOtpCode(code) else {
            otpCodeError = .codeRequired
            return
        }

        isVerifying = true
        defer { isVerifying = false }

        do {
            let tokens = try await apiClient.verifyRegistration(
                registrationId: registrationId,
                code: code,
                username: savedData.username
            )
            otpCode = ""
            try await sessionStore.adoptSession(tokens)
            state = .done
            logger.log(.info, category: "register", message: "Registration OTP verification succeeded")
        } catch {
            let apiError = MobileApiError.classify(error)
            handleOtpVerifyError(apiError, savedData: savedData, registrationId: registrationId)
        }
    }

    func resendOtp() async {
        guard case let .otp(registrationId, email, _, savedData) = state, canResend else { return }

        isResending = true
        defer { isResending = false }

        otpAlert = nil
        didResendCode = false
        dailyLimitUntil = nil

        do {
            let result = try await apiClient.resendRegistrationCode(registrationId: registrationId)
            otpCodeError = nil
            didResendCode = true

            let now = clock.now
            currentTime = now
            let cooldownSeconds = max(0, result.resendAfterSeconds)
            let resendAt = now.addingTimeInterval(TimeInterval(cooldownSeconds))

            state = .otp(
                registrationId: result.registrationId,
                email: result.email,
                resendAt: resendAt,
                formData: savedData
            )
            logger.log(.info, category: "register", message: "Registration OTP resent")
        } catch {
            let apiError = MobileApiError.classify(error)
            switch apiError {
            case let .api(statusCode, payload):
                if statusCode == 429 {
                    let retryAfter = max(1, payload.retryAfter ?? Self.defaultResendSeconds)
                    let now = clock.now
                    currentTime = now
                    let nextResendAt = now.addingTimeInterval(TimeInterval(retryAfter))
                    if retryAfter >= CooldownFormatter.longCooldownThresholdSeconds {
                        dailyLimitUntil = nextResendAt
                    }
                    state = .otp(
                        registrationId: registrationId,
                        email: email,
                        resendAt: nextResendAt,
                        formData: savedData
                    )
                    return
                }
                if payload.code == "EMAIL_CODE_EXPIRED" {
                    otpAlert = .codeExpired
                    return
                }
                otpAlert = .resendFailed
            case .network:
                otpAlert = .offline
            case .unexpectedResponse:
                otpAlert = .resendFailed
            }
        }
    }

    func backToEdit() {
        guard case let .otp(registrationId, _, _, savedData) = state else { return }
        let restored = savedData.withoutPassword()
        fullName = restored.fullName
        username = restored.username
        email = restored.email
        password = ""
        acceptTerms = restored.acceptTerms
        isPasswordVisible = false
        otpCode = ""
        otpCodeError = nil
        otpAlert = nil
        didResendCode = false
        dailyLimitUntil = nil
        registerAlert = nil
        state = .form(data: restored, pendingRegistrationId: registrationId)
    }

    private func handleRegistrationError(_ apiError: MobileApiError) {
        switch apiError {
        case let .api(statusCode, payload):
            if statusCode == 429 {
                registerAlert = .rateLimited
                return
            }
            if applyServerFieldErrors(statusCode: statusCode, payload: payload) {
                return
            }
            registerAlert = .generic
        case .network:
            registerAlert = .offline
        case .unexpectedResponse:
            registerAlert = .generic
        }
    }

    private func handleOtpVerifyError(
        _ apiError: MobileApiError,
        savedData: RegistrationFormData,
        registrationId: String
    ) {
        switch apiError {
        case let .api(statusCode, payload):
            if payload.code == "EMAIL_CODE_INVALID" {
                otpCodeError = .codeInvalid(remainingAttempts: payload.remainingAttempts)
                return
            }
            if payload.code == "EMAIL_CODE_EXPIRED" {
                otpAlert = .codeExpired
                return
            }
            if statusCode == 429 {
                otpAlert = .rateLimited
                return
            }
            if statusCode == 409 || statusCode == 400 {
                let restored = savedData.withoutPassword()
                fullName = restored.fullName
                username = restored.username
                email = restored.email
                password = ""
                acceptTerms = restored.acceptTerms
                state = .form(data: restored, pendingRegistrationId: registrationId)
                if applyServerFieldErrors(statusCode: statusCode, payload: payload) {
                    return
                }
            }
            otpAlert = .generic
        case .network:
            otpAlert = .offline
        case .unexpectedResponse:
            otpAlert = .generic
        }
    }

    @discardableResult
    private func applyServerFieldErrors(statusCode: Int, payload: ApiError) -> Bool {
        var applied = false

        if statusCode == 409, payload.code == "USERNAME_TAKEN" {
            usernameError = .usernameTaken
            applied = true
        }
        if statusCode == 409, payload.code == "EMAIL_TAKEN" {
            emailError = .emailTaken
            applied = true
        }

        for fieldError in payload.fieldErrors {
            switch fieldError.field {
            case "fullName":
                fullNameError = fieldError.code == "SIZE" ? .fullNameTooLong : .fullNameRequired
                applied = true
            case "username":
                if fieldError.code == "USERNAME_TAKEN" {
                    usernameError = .usernameTaken
                } else {
                    usernameError = .usernamePattern
                }
                applied = true
            case "email":
                if fieldError.code == "EMAIL_TAKEN" {
                    emailError = .emailTaken
                } else if fieldError.code == "EMAIL_INVALID" {
                    emailError = .emailInvalid
                } else {
                    emailError = .emailRequired
                }
                applied = true
            case "password":
                if fieldError.code == "PASSWORD_SAME_AS_USERNAME" {
                    passwordError = .passwordSameAsUsername
                } else {
                    passwordError = .passwordWeak
                }
                applied = true
            case "acceptTerms":
                termsError = .termsRequired
                applied = true
            default:
                break
            }
        }

        return applied
    }
}
