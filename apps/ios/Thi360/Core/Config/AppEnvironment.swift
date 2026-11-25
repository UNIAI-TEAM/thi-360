import Foundation

enum BuildEnvironment: String, Equatable, Sendable {
    case dev
    case demo
}

enum AppEnvironmentError: Error, Equatable {
    case missingInfoDictionary
    case invalidEnvironment(String?)
    case invalidURL(key: String, value: String?)
    case insecureDemoURL(key: String, url: URL)
}

struct AppEnvironment: Equatable, Sendable {
    let kind: BuildEnvironment
    let apiBaseURL: URL
    let webBaseURL: URL

    var isDev: Bool { kind == .dev }
    var isDemo: Bool { kind == .demo }

    static let current: AppEnvironment = {
        do {
            return try load(from: Bundle.main.infoDictionary)
        } catch {
            preconditionFailure("Invalid Thi360 Info.plist environment configuration: \(error)")
        }
    }()

    static func load(from infoDictionary: [String: Any]?) throws -> AppEnvironment {
        guard let infoDictionary else {
            throw AppEnvironmentError.missingInfoDictionary
        }

        let rawEnv = (infoDictionary["THI360_ENV"] as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
        guard let rawEnv, let kind = BuildEnvironment(rawValue: rawEnv) else {
            throw AppEnvironmentError.invalidEnvironment(rawEnv)
        }

        let apiBaseURL = try parseURL(key: "THI360_API_BASE_URL", from: infoDictionary, environment: kind)
        let webBaseURL = try parseURL(key: "THI360_WEB_BASE_URL", from: infoDictionary, environment: kind)

        return AppEnvironment(kind: kind, apiBaseURL: apiBaseURL, webBaseURL: webBaseURL)
    }

    private static func parseURL(
        key: String,
        from infoDictionary: [String: Any],
        environment: BuildEnvironment
    ) throws -> URL {
        let raw = (infoDictionary[key] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
        guard
            let raw,
            !raw.isEmpty,
            let url = URL(string: raw),
            let scheme = url.scheme?.lowercased(),
            scheme == "https" || scheme == "http",
            let host = url.host,
            !host.isEmpty
        else {
            throw AppEnvironmentError.invalidURL(key: key, value: raw)
        }

        if environment == .demo && scheme != "https" {
            throw AppEnvironmentError.insecureDemoURL(key: key, url: url)
        }

        return url
    }
}

enum TransportSecurityPolicy {
    private static let allowedDevExceptionHosts: Set<String> = [
        "localhost",
        "127.0.0.1",
        "::1"
    ]

    static func validate(infoDictionary: [String: Any]?, environment: BuildEnvironment) -> [String] {
        guard let infoDictionary else {
            return ["Missing Info.plist dictionary"]
        }

        let ats = infoDictionary["NSAppTransportSecurity"] as? [String: Any]

        switch environment {
        case .demo:
            if ats != nil {
                return ["Demo build must not define NSAppTransportSecurity exceptions"]
            }
            return []

        case .dev:
            guard let ats else {
                return ["Dev build must configure local networking ATS exception"]
            }

            var violations: [String] = []

            if (ats["NSAllowsArbitraryLoads"] as? Bool) == true {
                violations.append("Dev build must not enable NSAllowsArbitraryLoads")
            }
            if (ats["NSAllowsArbitraryLoadsInWebContent"] as? Bool) == true {
                violations.append("Dev build must not enable NSAllowsArbitraryLoadsInWebContent")
            }

            let allowsLocalNetworking = (ats["NSAllowsLocalNetworking"] as? Bool) == true
            let exceptionDomains = ats["NSExceptionDomains"] as? [String: Any] ?? [:]

            for domain in exceptionDomains.keys {
                let normalized = domain.lowercased()
                if !allowedDevExceptionHosts.contains(normalized) && !normalized.hasSuffix(".local") {
                    violations.append("Dev ATS exception domain is not local: \(domain)")
                }
            }

            if !allowsLocalNetworking && exceptionDomains.isEmpty {
                violations.append("Dev ATS must allow local networking or local exception domains")
            }

            return violations
        }
    }

    static func permitsCleartextHTTP(to url: URL, infoDictionary: [String: Any]?) -> Bool {
        guard url.scheme?.lowercased() == "http", let host = url.host?.lowercased() else {
            return false
        }
        guard let ats = infoDictionary?["NSAppTransportSecurity"] as? [String: Any] else {
            return false
        }
        if (ats["NSAllowsArbitraryLoads"] as? Bool) == true {
            return true
        }
        if (ats["NSAllowsLocalNetworking"] as? Bool) == true && isLocalHost(host) {
            return true
        }
        if let domains = ats["NSExceptionDomains"] as? [String: Any] {
            for (domain, rawConfig) in domains {
                let normalizedDomain = domain.lowercased()
                let config = rawConfig as? [String: Any] ?? [:]
                guard (config["NSExceptionAllowsInsecureHTTPLoads"] as? Bool) == true else { continue }
                let includesSubdomains = (config["NSIncludesSubdomains"] as? Bool) == true
                if host == normalizedDomain || (includesSubdomains && host.hasSuffix(".\(normalizedDomain)")) {
                    return true
                }
            }
        }
        return false
    }

    private static func isLocalHost(_ host: String) -> Bool {
        allowedDevExceptionHosts.contains(host) || host.hasSuffix(".local")
    }
}
