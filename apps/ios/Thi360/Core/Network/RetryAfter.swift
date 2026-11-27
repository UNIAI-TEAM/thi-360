import Foundation
import HTTPTypes

enum RetryAfter {
    static func parseSeconds(_ rawHeader: String?) -> Int? {
        guard let trimmed = rawHeader?.trimmingCharacters(in: .whitespacesAndNewlines),
              !trimmed.isEmpty,
              let seconds = Int(trimmed),
              seconds > 0
        else {
            return nil
        }
        return seconds
    }

    static func parseSeconds(from headers: HTTPFields?) -> Int? {
        guard let headers, let name = HTTPField.Name("Retry-After") else { return nil }
        return parseSeconds(headers[name])
    }

    static func resolve(headerValue: String?, bodyRetryAfter: Int?) -> Int? {
        if let fromHeader = parseSeconds(headerValue) {
            return fromHeader
        }
        if let bodyRetryAfter, bodyRetryAfter > 0 {
            return bodyRetryAfter
        }
        return nil
    }
}
