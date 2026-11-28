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
}

struct RedactingAppLogger: Sendable {
    static let redactedToken = "<redacted>"

    private static let sensitiveHeaderNames: Set<String> = [
        "authorization",
        "cookie",
        "set-cookie",
        "x-api-key",
        "x-xsrf-token",
        "x-attempt-session"
    ]

    private static let sensitiveQueryKeys: Set<String> = [
        "token",
        "reset_token",
        "resettoken",
        "access_token",
        "refresh_token",
        "password",
        "oldpassword",
        "currentpassword",
        "newpassword",
        "confirmpassword",
        "code",
        "otp",
        "otpcode",
        "verificationcode",
        "vouchercode",
        "voucher_code"
    ]

    private static let containerFieldPatterns: [(NSRegularExpression, Character, Character)] = {
        let definitions: [(String, Character, Character)] = [
            (#"(?i)"finalAnswers"\s*:\s*"#, "[", "]"),
            (#"(?i)"answer"\s*:\s*"#, "{", "}"),
            (#"(?i)"studentAnswer"\s*:\s*"#, "{", "}"),
            (#"(?i)"expectedAnswer"\s*:\s*"#, "{", "}"),
            (#"(?i)"gradingDetail"\s*:\s*"#, "{", "}"),
            (#"(?i)"essayFeedback"\s*:\s*"#, "{", "}")
        ]
        return definitions.compactMap { pattern, openChar, closeChar in
            guard let regex = try? NSRegularExpression(pattern: pattern) else { return nil }
            return (regex, openChar, closeChar)
        }
    }()

    private static let redactionPatterns: [(NSRegularExpression, String)] = {
        let sensitiveFieldNames = "password|oldPassword|currentPassword|newPassword|confirmPassword|accessToken|refreshToken|resetToken|reset_token|token|code|otp|otpCode|verificationCode|voucherCode|voucher_code|vnp_[A-Za-z0-9_]+|x-attempt-session|answer|finalAnswers|html|text"
        let definitions: [(String, String)] = [
            (#"(?i)(Bearer\s+)[^\s,;"')]+"#, "$1<redacted>"),
            ("(?i)(\"(?:\(sensitiveFieldNames))\"\\s*:\\s*\")(?:\\\\.|[^\"\\\\])*(\")", "$1<redacted>$2"),
            ("(?i)(\"(?:\(sensitiveFieldNames))\"\\s*:\\s*)(?!\"|null\\b)[^\\s,}\\]]+", "$1\"<redacted>\""),
            ("(?i)\\b(\(sensitiveFieldNames))(\\s*:\\s*Optional\\(\")(?:\\\\.|[^\"\\\\])*(\"\\))", "$1$2<redacted>$3"),
            ("(?i)\\b(\(sensitiveFieldNames))(\\s*:\\s*Optional\\()(?!\"|nil\\))[^)]+(\\))", "$1$2<redacted>$3"),
            ("(?i)\\b(\(sensitiveFieldNames))(\\s*:\\s*\")(?:\\\\.|[^\"\\\\])*(\")", "$1$2<redacted>$3"),
            ("(?i)\\b(x-attempt-session)(\\s*:\\s*)[^\\s&,;)\"'\\]\\}]+", "$1$2<redacted>"),
            ("(?i)\\b(\(sensitiveFieldNames))(\\s*=\\s*)[^\\s&,;)\"'\\]\\}]+", "$1$2<redacted>")
        ]
        return definitions.compactMap { pattern, replacement in
            guard let regex = try? NSRegularExpression(pattern: pattern) else { return nil }
            return (regex, replacement)
        }
    }()

    private static let reviewRedactionPatterns: [(NSRegularExpression, String)] = {
        let fields = "studentAnswer|expectedAnswer|gradingDetail|solution|essayFeedback|comment"
        let definitions: [(String, String)] = [
            ("(?i)(\"(?:\(fields))\"\\s*:\\s*\")(?:\\\\.|[^\"\\\\])*(\")", "$1<redacted>$2"),
            ("(?i)(\"(?:\(fields))\"\\s*:\\s*)(?!\"|null\\b)[^\\s,}\\]]+", "$1\"<redacted>\""),
            ("(?i)\\b(\(fields))(\\s*:\\s*Optional\\(\")(?:\\\\.|[^\"\\\\])*(\"\\))", "$1$2<redacted>$3"),
            ("(?i)\\b(\(fields))(\\s*:\\s*\")(?:\\\\.|[^\"\\\\])*(\")", "$1$2<redacted>$3"),
            ("(?i)\\b(\(fields))(\\s*=\\s*)[^\\s&,;)\"'\\]\\}]+", "$1$2<redacted>")
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

    static func isSensitiveQueryKey(_ name: String) -> Bool {
        let lowered = name.lowercased()
        return sensitiveQueryKeys.contains(lowered) || lowered.hasPrefix("vnp_")
    }

    static func sanitizePath(_ rawPath: String) -> String {
        guard var components = URLComponents(string: rawPath) else {
            return redact(rawPath)
        }
        if let queryItems = components.queryItems, !queryItems.isEmpty {
            components.queryItems = queryItems.map { item in
                if isSensitiveQueryKey(item.name) {
                    return URLQueryItem(name: item.name, value: redactedToken)
                }
                return item
            }
        }
        return components.string ?? redact(rawPath)
    }

    static func redact(_ input: String) -> String {
        var result = input
        for (regex, openChar, closeChar) in containerFieldPatterns {
            result = redactJSONContainers(
                in: result,
                fieldPattern: regex,
                openChar: openChar,
                closeChar: closeChar
            )
        }
        for (regex, replacement) in redactionPatterns + reviewRedactionPatterns {
            let range = NSRange(result.startIndex..., in: result)
            result = regex.stringByReplacingMatches(in: result, options: [], range: range, withTemplate: replacement)
        }
        return result
    }

    private static func redactJSONContainers(
        in input: String,
        fieldPattern: NSRegularExpression,
        openChar: Character,
        closeChar: Character
    ) -> String {
        var output = ""
        output.reserveCapacity(input.count)
        var searchStart = input.startIndex

        while searchStart < input.endIndex {
            let nsRange = NSRange(searchStart..<input.endIndex, in: input)
            guard let match = fieldPattern.firstMatch(in: input, options: [], range: nsRange),
                  let matchRange = Range(match.range, in: input)
            else {
                output.append(contentsOf: input[searchStart..<input.endIndex])
                break
            }

            let valueStart = matchRange.upperBound
            guard valueStart < input.endIndex, input[valueStart] == openChar else {
                output.append(contentsOf: input[searchStart..<valueStart])
                searchStart = valueStart
                continue
            }

            output.append(contentsOf: input[searchStart..<valueStart])
            output.append("\"\(redactedToken)\"")

            var index = valueStart
            var depth = 0
            var inString = false
            var escaped = false

            while index < input.endIndex {
                let ch = input[index]
                if inString {
                    if escaped {
                        escaped = false
                    } else if ch == "\\" {
                        escaped = true
                    } else if ch == "\"" {
                        inString = false
                    }
                } else {
                    if ch == "\"" {
                        inString = true
                    } else if ch == openChar {
                        depth += 1
                    } else if ch == closeChar {
                        depth -= 1
                        if depth == 0 {
                            index = input.index(after: index)
                            break
                        }
                    }
                }
                index = input.index(after: index)
            }

            searchStart = index
        }

        return output
    }
}
