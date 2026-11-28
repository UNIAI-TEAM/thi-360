import Foundation

struct AccountProfile: Equatable, Sendable {
    let username: String
    let fullName: String
    let email: String?
    let emailVerified: Bool
    let phone: String?
    let avatarAssetId: String?
    let avatarUrl: String?
    let pendingEmail: String?

    init(
        username: String,
        fullName: String,
        email: String?,
        emailVerified: Bool,
        phone: String? = nil,
        avatarAssetId: String? = nil,
        avatarUrl: String? = nil,
        pendingEmail: String? = nil
    ) {
        self.username = username
        self.fullName = fullName
        self.email = Self.nonEmptyTrimmed(email)
        self.emailVerified = emailVerified
        self.phone = Self.nonEmptyTrimmed(phone)
        self.avatarAssetId = Self.nonEmptyTrimmed(avatarAssetId)
        self.avatarUrl = Self.nonEmptyTrimmed(avatarUrl)
        self.pendingEmail = Self.nonEmptyTrimmed(pendingEmail)
    }

    init(from response: Components.Schemas.ProfileResponse) {
        self.init(
            username: response.username,
            fullName: response.fullName,
            email: response.email,
            emailVerified: response.emailVerified,
            phone: response.phone,
            avatarAssetId: response.avatarAssetId,
            avatarUrl: response.avatarUrl,
            pendingEmail: response.pendingEmail
        )
    }

    var trimmedEmail: String? {
        Self.nonEmptyTrimmed(email)
    }

    var hasEmail: Bool {
        trimmedEmail != nil
    }

    var trimmedPhone: String? {
        Self.nonEmptyTrimmed(phone)
    }

    var hasPhone: Bool {
        trimmedPhone != nil
    }

    var trimmedAvatarUrl: String? {
        Self.nonEmptyTrimmed(avatarUrl)
    }

    var hasAvatar: Bool {
        trimmedAvatarUrl != nil
    }

    var trimmedPendingEmail: String? {
        Self.nonEmptyTrimmed(pendingEmail)
    }

    var hasPendingEmail: Bool {
        trimmedPendingEmail != nil
    }

    var needsEmailVerificationBanner: Bool {
        hasEmail && !emailVerified
    }

    var displayName: String {
        let cleaned = fullName.trimmingCharacters(in: .whitespacesAndNewlines)
        return cleaned.isEmpty ? username : cleaned
    }

    var initials: String {
        Self.makeInitials(fullName: fullName, username: username)
    }

    func clearingAvatar() -> AccountProfile {
        AccountProfile(
            username: username,
            fullName: fullName,
            email: email,
            emailVerified: emailVerified,
            phone: phone,
            avatarAssetId: nil,
            avatarUrl: nil,
            pendingEmail: pendingEmail
        )
    }

    func withPendingEmail(_ newPendingEmail: String?) -> AccountProfile {
        AccountProfile(
            username: username,
            fullName: fullName,
            email: email,
            emailVerified: emailVerified,
            phone: phone,
            avatarAssetId: avatarAssetId,
            avatarUrl: avatarUrl,
            pendingEmail: newPendingEmail
        )
    }

    func withEmailVerified(_ verified: Bool) -> AccountProfile {
        AccountProfile(
            username: username,
            fullName: fullName,
            email: email,
            emailVerified: verified,
            phone: phone,
            avatarAssetId: avatarAssetId,
            avatarUrl: avatarUrl,
            pendingEmail: pendingEmail
        )
    }

    func withConfirmedEmail(_ newEmail: String) -> AccountProfile {
        AccountProfile(
            username: username,
            fullName: fullName,
            email: newEmail,
            emailVerified: true,
            phone: phone,
            avatarAssetId: avatarAssetId,
            avatarUrl: avatarUrl,
            pendingEmail: nil
        )
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

    private static func nonEmptyTrimmed(_ value: String?) -> String? {
        guard let value else { return nil }
        let cleaned = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return cleaned.isEmpty ? nil : cleaned
    }
}

enum AccountDetailSelection: String, Equatable, Hashable, Sendable {
    case profile
    case orders
    case changePassword
    case legalTerms
    case legalPrivacy
    case deleteAccount
}
