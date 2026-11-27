import Foundation
import SwiftUI

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

    init(
        apiClient: MobileAPIClient,
        ttlSeconds: TimeInterval = CatalogOptionsCache.defaultTTLSeconds,
        clock: any AppClock = SystemClock()
    ) {
        self.init(ttlSeconds: ttlSeconds, clock: clock) { type in
            do {
                let output = try await apiClient.publicClient.getOptions(
                    query: .init(group: type.rawValue)
                )
                switch output {
                case let .ok(response):
                    let decoded = try await MobileAPIClient.decodeJSONBody(
                        [Components.Schemas.CatalogOptionResponse].self,
                        from: try response.body.any
                    )
                    return decoded.map {
                        CatalogOptionItem(
                            code: $0.code,
                            nameVi: $0.nameVi,
                            nameEn: $0.nameEn ?? ""
                        )
                    }
                case let .badRequest(response):
                    throw await MobileApiError.decodeErrorBody(statusCode: 400, body: try response.body.any)
                case let .unauthorized(response):
                    throw await MobileApiError.decodeErrorBody(statusCode: 401, body: try response.body.any)
                case let .forbidden(response):
                    throw await MobileApiError.decodeErrorBody(statusCode: 403, body: try response.body.any)
                case let .notFound(response):
                    throw await MobileApiError.decodeErrorBody(statusCode: 404, body: try response.body.any)
                case let .conflict(response):
                    throw await MobileApiError.decodeErrorBody(statusCode: 409, body: try response.body.any)
                case let .preconditionFailed(response):
                    throw await MobileApiError.decodeErrorBody(statusCode: 412, body: try response.body.any)
                case let .contentTooLarge(response):
                    throw await MobileApiError.decodeErrorBody(statusCode: 413, body: try response.body.any)
                case let .unprocessableContent(response):
                    throw await MobileApiError.decodeErrorBody(statusCode: 422, body: try response.body.any)
                case let .tooManyRequests(response):
                    throw await MobileApiError.decodeErrorBody(statusCode: 429, body: try response.body.any)
                case let .internalServerError(response):
                    throw await MobileApiError.decodeErrorBody(statusCode: 500, body: try response.body.any)
                case let .notImplemented(response):
                    throw await MobileApiError.decodeErrorBody(statusCode: 501, body: try response.body.any)
                case let .badGateway(response):
                    throw await MobileApiError.decodeErrorBody(statusCode: 502, body: try response.body.any)
                case let .undocumented(statusCode, payload):
                    if let body = payload.body {
                        throw await MobileApiError.decodeErrorBody(statusCode: statusCode, body: body)
                    }
                    throw MobileApiError.unexpectedResponse(statusCode: statusCode)
                }
            } catch {
                throw MobileApiError.classify(error)
            }
        }
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

private struct CatalogOptionsCacheEnvironmentKey: EnvironmentKey {
    static let defaultValue: CatalogOptionsCache? = nil
}

extension EnvironmentValues {
    var catalogOptionsCache: CatalogOptionsCache? {
        get { self[CatalogOptionsCacheEnvironmentKey.self] }
        set { self[CatalogOptionsCacheEnvironmentKey.self] = newValue }
    }
}
