import Foundation

enum LegalId: String, Codable, Hashable, CaseIterable, Sendable {
    case terms
    case privacy

    var webPath: String {
        switch self {
        case .terms: "/dieu-khoan-giao-dich-chung"
        case .privacy: "/chinh-sach-bao-mat-thong-tin"
        }
    }

    var titleKey: String {
        switch self {
        case .terms: "legal.terms.title"
        case .privacy: "legal.privacy.title"
        }
    }

    func webURL(webBaseURL: URL) -> URL {
        let baseString = webBaseURL.absoluteString.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        return URL(string: "\(baseString)\(webPath)") ?? webBaseURL.appendingPathComponent(String(webPath.dropFirst()))
    }
}

enum LegalStatus: String, Codable, Equatable, Sendable {
    case draft
    case published
}

enum LegalBlock: Equatable, Sendable, Decodable {
    case heading(level: Int, text: String)
    case paragraph(String)
    case list(ordered: Bool, items: [String])
    case table(headers: [String], rows: [[String]])

    private enum CodingKeys: String, CodingKey {
        case type
        case level
        case text
        case ordered
        case items
        case headers
        case rows
    }

    init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let type = try container.decode(String.self, forKey: .type)
        switch type {
        case "heading":
            let level = try container.decode(Int.self, forKey: .level)
            let text = try container.decode(String.self, forKey: .text)
            self = .heading(level: level, text: text)
        case "paragraph":
            let text = try container.decode(String.self, forKey: .text)
            self = .paragraph(text)
        case "list":
            let ordered = try container.decode(Bool.self, forKey: .ordered)
            let items = try container.decode([String].self, forKey: .items)
            self = .list(ordered: ordered, items: items)
        case "table":
            let headers = try container.decode([String].self, forKey: .headers)
            let rows = try container.decode([[String]].self, forKey: .rows)
            self = .table(headers: headers, rows: rows)
        default:
            throw DecodingError.dataCorruptedError(
                forKey: .type,
                in: container,
                debugDescription: "Unsupported legal block type: \(type)"
            )
        }
    }
}

struct LegalDocument: Equatable, Sendable, Decodable {
    let id: LegalId
    let locale: AppLocale
    let version: String
    let status: LegalStatus
    let contentHash: String?
    let effectiveDate: Date?
    let title: String
    let note: String?
    let blocks: [LegalBlock]

    private enum CodingKeys: String, CodingKey {
        case id
        case locale
        case version
        case status
        case contentHash
        case effectiveDate
        case title
        case note
        case blocks
    }

    init(
        id: LegalId,
        locale: AppLocale,
        version: String,
        status: LegalStatus,
        contentHash: String? = nil,
        effectiveDate: Date?,
        title: String,
        note: String? = nil,
        blocks: [LegalBlock]
    ) {
        self.id = id
        self.locale = locale
        self.version = version
        self.status = status
        self.contentHash = contentHash
        self.effectiveDate = effectiveDate
        self.title = title
        self.note = note
        self.blocks = blocks
    }

    init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try container.decode(LegalId.self, forKey: .id)
        self.locale = try container.decode(AppLocale.self, forKey: .locale)
        self.version = try container.decode(String.self, forKey: .version)
        self.status = try container.decode(LegalStatus.self, forKey: .status)
        self.contentHash = try container.decodeIfPresent(String.self, forKey: .contentHash)
        if let rawDate = try container.decodeIfPresent(String.self, forKey: .effectiveDate) {
            let trimmed = rawDate.trimmingCharacters(in: .whitespacesAndNewlines)
            if trimmed.isEmpty {
                self.effectiveDate = nil
            } else {
                guard let parsed = Self.parseISODate(trimmed) else {
                    throw DecodingError.dataCorruptedError(
                        forKey: .effectiveDate,
                        in: container,
                        debugDescription: "Invalid effectiveDate format: \(trimmed)"
                    )
                }
                self.effectiveDate = parsed
            }
        } else {
            self.effectiveDate = nil
        }
        self.title = try container.decode(String.self, forKey: .title)
        self.note = try container.decodeIfPresent(String.self, forKey: .note)
        self.blocks = try container.decode([LegalBlock].self, forKey: .blocks)
    }

    static func parseISODate(_ raw: String) -> Date? {
        let parts = raw.split(separator: "-")
        guard parts.count == 3,
              parts[0].count == 4,
              parts[1].count == 2,
              parts[2].count == 2,
              let year = Int(parts[0]),
              let month = Int(parts[1]),
              let day = Int(parts[2])
        else {
            return nil
        }
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        var components = DateComponents()
        components.year = year
        components.month = month
        components.day = day
        components.hour = 12
        guard let date = calendar.date(from: components) else { return nil }
        let roundTrip = calendar.dateComponents([.year, .month, .day], from: date)
        guard roundTrip.year == year, roundTrip.month == month, roundTrip.day == day else { return nil }
        return date
    }

    static func formatEffectiveDate(_ date: Date) -> String {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        let components = calendar.dateComponents([.year, .month, .day], from: date)
        let day = components.day ?? 1
        let month = components.month ?? 1
        let year = components.year ?? 1970
        return String(format: "%02d/%02d/%04d", day, month, year)
    }
}
