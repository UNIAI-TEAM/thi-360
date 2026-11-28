import Foundation

enum FullNameValidationError: Equatable, Sendable {
    case required
    case tooLong
}

enum PhoneValidationError: Equatable, Sendable {
    case invalid
}

struct PersonalInfoValidationResult: Equatable, Sendable {
    let fullNameError: FullNameValidationError?
    let phoneError: PhoneValidationError?

    var isValid: Bool {
        fullNameError == nil && phoneError == nil
    }
}

struct PersonalInfoPatch: Equatable, Sendable {
    let fullName: String
    let phone: String?
}

enum PersonalInfoValidator {
    static let fullNameMaxCharacters = 200

    private static let phoneRegex = try! NSRegularExpression(pattern: #"^0\d{9}$"#)
    private static let emailRegex = try! NSRegularExpression(pattern: #"^[^\s@]+@[^\s@]+\.[^\s@]+$"#)
    private static let otpRegex = try! NSRegularExpression(pattern: #"^\d{6}$"#)

    static func clampFullNameInput(_ raw: String) -> String {
        guard raw.count > fullNameMaxCharacters else { return raw }
        return String(raw.prefix(fullNameMaxCharacters))
    }

    static func isDirty(fullName: String, phone: String, saved: AccountProfile) -> Bool {
        fullName != saved.fullName || phone != (saved.phone ?? "")
    }

    static func validate(
        fullName: String,
        phone: String,
        savedPhone: String?
    ) -> PersonalInfoValidationResult {
        let trimmedName = fullName.trimmingCharacters(in: .whitespacesAndNewlines)
        let nameError: FullNameValidationError?
        if trimmedName.isEmpty {
            nameError = .required
        } else if trimmedName.count > fullNameMaxCharacters {
            nameError = .tooLong
        } else {
            nameError = nil
        }

        let trimmedPhone = phone.trimmingCharacters(in: .whitespacesAndNewlines)
        let savedTrimmed = (savedPhone ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let phoneError: PhoneValidationError?
        if trimmedPhone == savedTrimmed || trimmedPhone.isEmpty || matches(phoneRegex, trimmedPhone) {
            phoneError = nil
        } else {
            phoneError = .invalid
        }

        return PersonalInfoValidationResult(
            fullNameError: nameError,
            phoneError: phoneError
        )
    }

    static func buildPatch(
        fullName: String,
        phone: String,
        savedPhone: String?
    ) -> PersonalInfoPatch {
        let trimmedName = fullName.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedPhone = phone.trimmingCharacters(in: .whitespacesAndNewlines)
        let savedTrimmed = (savedPhone ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        return PersonalInfoPatch(
            fullName: trimmedName,
            phone: trimmedPhone != savedTrimmed ? trimmedPhone : nil
        )
    }

    static func isValidEmail(_ raw: String) -> Bool {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        return matches(emailRegex, trimmed)
    }

    static func isValidVerificationCode(_ raw: String) -> Bool {
        matches(otpRegex, raw)
    }

    private static func matches(_ regex: NSRegularExpression, _ value: String) -> Bool {
        let range = NSRange(value.startIndex..., in: value)
        return regex.firstMatch(in: value, options: [], range: range) != nil
    }
}
