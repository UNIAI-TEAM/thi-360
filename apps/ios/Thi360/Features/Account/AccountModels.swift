import Foundation

struct AccountProfile: Equatable, Sendable {
    let username: String
    let fullName: String
    let email: String?
    let emailVerified: Bool

    init(
        username: String,
        fullName: String,
        email: String?,
        emailVerified: Bool
    ) {
        self.username = username
        self.fullName = fullName
        self.email = email
        self.emailVerified = emailVerified
    }

    init(from response: Components.Schemas.ProfileResponse) {
        self.username = response.username
        self.fullName = response.fullName
        self.email = response.email
        self.emailVerified = response.emailVerified
    }

    var trimmedEmail: String? {
        guard let email else { return nil }
        let cleaned = email.trimmingCharacters(in: .whitespacesAndNewlines)
        return cleaned.isEmpty ? nil : cleaned
    }

    var hasEmail: Bool {
        trimmedEmail != nil
    }

    var displayName: String {
        let cleaned = fullName.trimmingCharacters(in: .whitespacesAndNewlines)
        return cleaned.isEmpty ? username : cleaned
    }

    var initials: String {
        Self.makeInitials(fullName: fullName, username: username)
    }

    static func makeInitials(fullName: String?, username: String?) -> String {
        let parts = (fullName ?? "")
            .split(whereSeparator: { $0.isWhitespace })
            .map(String.init)
            .filter { !$0.isEmpty }

        if parts.count >= 2 {
            let lastTwo = parts.suffix(2)
            let chars = lastTwo.compactMap { $0.first }.map { String($0).uppercased() }.joined()
            if !chars.isEmpty {
                return chars
            }
        }

        if let first = parts.first, !first.isEmpty {
            return String(first.prefix(2)).uppercased()
        }

        let cleanUser = (username ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        if !cleanUser.isEmpty {
            return String(cleanUser.prefix(2)).uppercased()
        }

        return "TV"
    }
}

enum AccountProfileLoadState: Equatable, Sendable {
    case loading
    case loaded(AccountProfile)
    case offline
    case error
}

enum AccountDetailSelection: String, Equatable, Hashable, Sendable {
    case changePassword
}
