import Foundation

struct AppearanceTokensPayload: Codable, Equatable, Sendable {
    let version: Int
    let mode: ThemeMode
    let presetId: String
    let light: BrandPalette
    let dark: BrandPalette

    var isValid: Bool {
        version >= 0
            && !presetId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && light.hasValidHexColors
            && dark.hasValidHexColors
    }

    func toBrandPaletteSet() -> BrandPaletteSet {
        BrandPaletteSet(
            presetId: presetId,
            defaultMode: mode,
            light: light,
            dark: dark
        )
    }

    static func decodeValidated(from data: Data) -> AppearanceTokensPayload? {
        guard let decoded = try? JSONDecoder().decode(AppearanceTokensPayload.self, from: data),
              decoded.isValid
        else {
            return nil
        }
        return decoded
    }
}

struct CachedAppearanceRecord: Codable, Equatable, Sendable {
    let version: Int
    let etag: String?
    let paletteSet: BrandPaletteSet

    var isValid: Bool {
        version >= 0
            && !paletteSet.presetId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && paletteSet.light.hasValidHexColors
            && paletteSet.dark.hasValidHexColors
    }
}

protocol AppearanceCacheStore: Sendable {
    func read() -> CachedAppearanceRecord?
    func write(_ record: CachedAppearanceRecord)
    func clear()
}

final class UserDefaultsAppearanceCacheStore: AppearanceCacheStore, @unchecked Sendable {
    static let defaultKey = "thi360.appearance.cachedRecord"

    private let defaults: UserDefaults
    private let key: String

    init(userDefaults: UserDefaults = .standard, key: String = UserDefaultsAppearanceCacheStore.defaultKey) {
        self.defaults = userDefaults
        self.key = key
    }

    func read() -> CachedAppearanceRecord? {
        guard let data = defaults.data(forKey: key),
              let decoded = try? JSONDecoder().decode(CachedAppearanceRecord.self, from: data),
              decoded.isValid
        else {
            return nil
        }
        return decoded
    }

    func write(_ record: CachedAppearanceRecord) {
        guard record.isValid, let data = try? JSONEncoder().encode(record) else { return }
        defaults.set(data, forKey: key)
    }

    func clear() {
        defaults.removeObject(forKey: key)
    }
}

final class InMemoryAppearanceCacheStore: AppearanceCacheStore, @unchecked Sendable {
    private let lock = NSLock()
    private var stored: CachedAppearanceRecord?

    init(initial: CachedAppearanceRecord? = nil) {
        self.stored = initial
    }

    func read() -> CachedAppearanceRecord? {
        lock.lock()
        defer { lock.unlock() }
        return stored
    }

    func write(_ record: CachedAppearanceRecord) {
        lock.lock()
        defer { lock.unlock() }
        stored = record
    }

    func clear() {
        lock.lock()
        defer { lock.unlock() }
        stored = nil
    }
}
