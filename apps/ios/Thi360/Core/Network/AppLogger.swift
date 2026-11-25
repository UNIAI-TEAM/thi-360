import Foundation
import os

enum LogLevel: String, Equatable, Sendable {
    case debug
    case info
    case warning
    case error
}

struct LogEntry: Equatable, Sendable {
    let level: LogLevel
    let category: String
    let message: String
}

protocol LogSink: Sendable {
    func write(_ entry: LogEntry)
}

final class OSLogSink: LogSink, @unchecked Sendable {
    private let subsystem: String

    init(subsystem: String = "vn.unicom.thi360") {
        self.subsystem = subsystem
    }

    func write(_ entry: LogEntry) {
        let logger = Logger(subsystem: subsystem, category: entry.category)
        switch entry.level {
        case .debug:
            logger.debug("\(entry.message, privacy: .public)")
        case .info:
            logger.info("\(entry.message, privacy: .public)")
        case .warning:
            logger.warning("\(entry.message, privacy: .public)")
        case .error:
            logger.error("\(entry.message, privacy: .public)")
        }
    }
}

final class InMemoryLogSink: LogSink, @unchecked Sendable {
    private let lock = NSLock()
    private var entries: [LogEntry] = []

    func write(_ entry: LogEntry) {
        lock.lock()
        defer { lock.unlock() }
        entries.append(entry)
    }

    var recordedEntries: [LogEntry] {
        lock.lock()
        defer { lock.unlock() }
        return entries
    }

    func clear() {
        lock.lock()
        defer { lock.unlock() }
        entries.removeAll()
    }
}

struct RedactingAppLogger: Sendable {
    static let redactedToken = "<redacted>"

    private static let sensitiveHeaderNames: Set<String> = [
        "authorization",
        "cookie",
        "set-cookie",
        "x-api-key",
        "x-xsrf-token"
    ]

    private static let sensitiveQueryKeys: Set<String> = [
        "token",
        "access_token",
        "refresh_token",
        "password",
        "code",
        "otp"
    ]

    private static let redactionPatterns: [(NSRegularExpression, String)] = {
        let definitions: [(String, String)] = [
            (#"(?i)(Bearer\s+)[^\s,;"')]+"#, "$1<redacted>"),
            (#"(?i)("(?:password|oldPassword|newPassword|accessToken|refreshToken|token|code|otp)"\s*:\s*")[^"]*(")"#, "$1<redacted>$2"),
            (#"(?i)\b(password|oldPassword|newPassword|accessToken|refreshToken|token|code|otp)(\s*:\s*")[^"]*(")"#, "$1$2<redacted>$3"),
            (#"(?i)\b(password|oldPassword|newPassword|accessToken|refreshToken|token|code|otp)(\s*=\s*)[^\s&,;)]+"#, "$1$2<redacted>")
        ]
        return definitions.compactMap { pattern, replacement in
            guard let regex = try? NSRegularExpression(pattern: pattern) else { return nil }
            return (regex, replacement)
        }
    }()

    let environment: BuildEnvironment
    private let sink: any LogSink

    init(
        environment: BuildEnvironment = AppEnvironment.current.kind,
        sink: any LogSink = OSLogSink()
    ) {
        self.environment = environment
        self.sink = sink
    }

    func log(_ level: LogLevel, category: String, message: String) {
        if environment == .demo && level == .debug {
            return
        }
        let sanitized = Self.redact(message)
        sink.write(LogEntry(level: level, category: category, message: sanitized))
    }

    func logHTTP(
        method: String,
        path: String,
        statusCode: Int? = nil,
        headers: [(String, String)] = [],
        errorDescription: String? = nil
    ) {
        let safePath = Self.sanitizePath(path)
        var parts = ["\(method) \(safePath)"]
        if let statusCode {
            parts.append("status=\(statusCode)")
        }
        if environment == .dev && !headers.isEmpty {
            let safeHeaders = headers
                .map { name, value in
                    let redactedValue = Self.isSensitiveHeader(name) ? Self.redactedToken : Self.redact(value)
                    return "\(name)=\(redactedValue)"
                }
                .joined(separator: ",")
            parts.append("headers=[\(safeHeaders)]")
        }
        if let errorDescription {
            parts.append("error=\(Self.redact(errorDescription))")
        }
        log(.info, category: "network", message: parts.joined(separator: " "))
    }

    static func isSensitiveHeader(_ name: String) -> Bool {
        sensitiveHeaderNames.contains(name.lowercased())
    }

    static func sanitizePath(_ rawPath: String) -> String {
        guard var components = URLComponents(string: rawPath) else {
            return redact(rawPath)
        }
        if let queryItems = components.queryItems, !queryItems.isEmpty {
            components.queryItems = queryItems.map { item in
                if sensitiveQueryKeys.contains(item.name.lowercased()) {
                    return URLQueryItem(name: item.name, value: redactedToken)
                }
                return item
            }
        }
        return components.string ?? redact(rawPath)
    }

    static func redact(_ input: String) -> String {
        var result = input
        for (regex, replacement) in redactionPatterns {
            let range = NSRange(result.startIndex..., in: result)
            result = regex.stringByReplacingMatches(in: result, options: [], range: range, withTemplate: replacement)
        }
        return result
    }
}
