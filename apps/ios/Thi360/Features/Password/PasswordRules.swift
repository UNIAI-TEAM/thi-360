import Foundation

enum PasswordRuleKind: String, CaseIterable, Identifiable, Equatable, Sendable {
    case length
    case uppercase
    case lowercase
    case digit
    case special

    var id: String { rawValue }

    var localizationKey: String {
        switch self {
        case .length: "recovery.password.ruleLength"
        case .uppercase: "recovery.password.ruleUpper"
        case .lowercase: "recovery.password.ruleLower"
        case .digit: "recovery.password.ruleDigit"
        case .special: "recovery.password.ruleSpecial"
        }
    }
}

struct PasswordRuleStatus: Identifiable, Equatable, Sendable {
    let kind: PasswordRuleKind
    let isMet: Bool

    var id: PasswordRuleKind { kind }
}

struct PasswordRuleEvaluation: Equatable, Sendable {
    let hasValidLength: Bool
    let hasUppercase: Bool
    let hasLowercase: Bool
    let hasDigit: Bool
    let hasSpecial: Bool

    var isStrong: Bool {
        hasValidLength && hasUppercase && hasLowercase && hasDigit && hasSpecial
    }

    var items: [PasswordRuleStatus] {
        [
            PasswordRuleStatus(kind: .length, isMet: hasValidLength),
            PasswordRuleStatus(kind: .uppercase, isMet: hasUppercase),
            PasswordRuleStatus(kind: .lowercase, isMet: hasLowercase),
            PasswordRuleStatus(kind: .digit, isMet: hasDigit),
            PasswordRuleStatus(kind: .special, isMet: hasSpecial)
        ]
    }
}

enum PasswordRules {
    static let minLength = 8
    static let maxLength = 64

    static func evaluate(_ password: String) -> PasswordRuleEvaluation {
        let count = password.count
        let hasValidLength = count >= minLength && count <= maxLength

        var hasUppercase = false
        var hasLowercase = false
        var hasDigit = false
        var hasSpecial = false

        for scalar in password.unicodeScalars {
            switch scalar.properties.generalCategory {
            case .uppercaseLetter:
                hasUppercase = true
            case .lowercaseLetter:
                hasLowercase = true
            case .decimalNumber:
                hasDigit = true
            default:
                if !scalar.properties.isWhitespace {
                    hasSpecial = true
                }
            }
        }

        return PasswordRuleEvaluation(
            hasValidLength: hasValidLength,
            hasUppercase: hasUppercase,
            hasLowercase: hasLowercase,
            hasDigit: hasDigit,
            hasSpecial: hasSpecial
        )
    }

    static func isStrong(_ password: String) -> Bool {
        evaluate(password).isStrong
    }

    static func matchesUsername(_ password: String, username: String?) -> Bool {
        guard let trimmedUsername = username?.trimmingCharacters(in: .whitespacesAndNewlines),
              !trimmedUsername.isEmpty
        else {
            return false
        }
        return password.lowercased() == trimmedUsername.lowercased()
    }
}
