import Foundation

enum CatalogOptionType: String, Hashable, CaseIterable, Sendable {
    case exam = "EXAM"
    case subject = "SUBJECT"
}

struct CatalogOptionItem: Codable, Equatable, Hashable, Sendable {
    let code: String
    let nameVi: String
    let nameEn: String

    func localizedName(for locale: AppLocale) -> String {
        switch locale {
        case .vi: nameVi.isEmpty ? code : nameVi
        case .en: nameEn.isEmpty ? (nameVi.isEmpty ? code : nameVi) : nameEn
        }
    }
}

actor CatalogOptionsCache {
    typealias Fetcher = @Sendable (CatalogOptionType) async throws -> [CatalogOptionItem]

    static let defaultTTLSeconds: TimeInterval = 300

    private struct Entry {
        let items: [CatalogOptionItem]
        let fetchedAt: Date
    }

    private let ttlSeconds: TimeInterval
    private let clock: any AppClock
    private let fetcher: Fetcher
    private var entries: [CatalogOptionType: Entry] = [:]
    private var inFlight: [CatalogOptionType: Task<[CatalogOptionItem], Error>] = [:]

    init(
        ttlSeconds: TimeInterval = CatalogOptionsCache.defaultTTLSeconds,
        clock: any AppClock = SystemClock(),
        fetcher: @escaping Fetcher
    ) {
        self.ttlSeconds = ttlSeconds
        self.clock = clock
        self.fetcher = fetcher
    }

    func options(for type: CatalogOptionType) async throws -> [CatalogOptionItem] {
        let now = clock.now
        if let entry = entries[type] {
            let age = now.timeIntervalSince(entry.fetchedAt)
            if age >= 0 && age < ttlSeconds {
                return entry.items
            }
        }

        if let existingTask = inFlight[type] {
            return try await existingTask.value
        }

        let fetchBlock = fetcher
        let task = Task<[CatalogOptionItem], Error> {
            try await fetchBlock(type)
        }
        inFlight[type] = task

        do {
            let items = try await task.value
            inFlight[type] = nil
            entries[type] = Entry(items: items, fetchedAt: clock.now)
            return items
        } catch {
            inFlight[type] = nil
            throw error
        }
    }

    func clear() {
        entries.removeAll()
        for task in inFlight.values {
            task.cancel()
        }
        inFlight.removeAll()
    }
}
