import Foundation

struct ResetLink: Equatable, Sendable, CustomStringConvertible, CustomDebugStringConvertible {
    let id: UUID
    let token: String

    init(token: String, id: UUID = UUID()) {
        self.id = id
        self.token = token
    }

    var description: String {
        "ResetLink(token: <redacted>)"
    }

    var debugDescription: String {
        description
    }

    static let launchArgumentKey = "-resetLinkURL"
    static let resetPasswordPath = "/reset-password"

    static func parse(
        _ url: URL,
        environment: AppEnvironment = .current
    ) -> ResetLink? {
        guard let allowedHost = environment.webBaseURL.host?.lowercased(), !allowedHost.isEmpty else {
            return nil
        }
        return parse(url, allowedWebHost: allowedHost, environmentKind: environment.kind)
    }

    static func parse(
        _ url: URL,
        allowedWebHost: String,
        environmentKind: BuildEnvironment
    ) -> ResetLink? {
        guard let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
              let scheme = components.scheme?.lowercased(),
              let host = components.host?.lowercased(),
              host == allowedWebHost.lowercased()
        else {
            return nil
        }

        switch environmentKind {
        case .demo:
            guard scheme == "https" else { return nil }
        case .dev:
            guard scheme == "https" || scheme == "http" else { return nil }
        }

        guard components.path == resetPasswordPath else {
            return nil
        }

        guard let rawToken = components.queryItems?.first(where: { $0.name == "token" })?.value else {
            return nil
        }

        let trimmedToken = rawToken.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedToken.isEmpty else {
            return nil
        }

        return ResetLink(token: trimmedToken)
    }

    static func fromUserActivity(
        _ userActivity: NSUserActivity,
        environment: AppEnvironment = .current
    ) -> ResetLink? {
        guard userActivity.activityType == NSUserActivityTypeBrowsingWeb,
              let url = userActivity.webpageURL
        else {
            return nil
        }
        return parse(url, environment: environment)
    }

    static func fromLaunchArguments(
        _ arguments: [String] = ProcessInfo.processInfo.arguments,
        environment: AppEnvironment = .current
    ) -> ResetLink? {
        // -resetLinkURL is a dev-only UI test launch hook and must always be ignored in demo builds (Spec AC-7).
        guard environment.isDev else {
            return nil
        }

        guard let index = arguments.firstIndex(of: launchArgumentKey),
              index + 1 < arguments.count,
              let url = URL(string: arguments[index + 1])
        else {
            return nil
        }

        return parse(url, environment: environment)
    }
}
