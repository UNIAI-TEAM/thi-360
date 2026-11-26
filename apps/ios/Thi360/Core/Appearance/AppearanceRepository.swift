import Foundation
import Observation

enum AppearanceFetchResult: Equatable, Sendable {
    case updated(AppearanceTokensPayload, etag: String?)
    case notModified
    case unavailable
    case invalidPayload
    case networkFailure
}

protocol AppearanceFetching: Sendable {
    func fetch(ifNoneMatch: String?) async -> AppearanceFetchResult
}

struct URLSessionAppearanceFetcher: AppearanceFetching, Sendable {
    let endpointURL: URL
    private let session: URLSession

    init(webBaseURL: URL, session: URLSession = .shared) {
        self.endpointURL = webBaseURL
            .appendingPathComponent("mobile")
            .appendingPathComponent("appearance-tokens.json")
        self.session = session
    }

    func fetch(ifNoneMatch: String?) async -> AppearanceFetchResult {
        var request = URLRequest(url: endpointURL)
        request.httpMethod = "GET"
        request.cachePolicy = .reloadIgnoringLocalCacheData
        request.timeoutInterval = 8
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let ifNoneMatch, !ifNoneMatch.isEmpty {
            request.setValue(ifNoneMatch, forHTTPHeaderField: "If-None-Match")
        }

        do {
            let (data, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse else {
                return .unavailable
            }
            switch http.statusCode {
            case 304:
                return .notModified
            case 200:
                guard let payload = AppearanceTokensPayload.decodeValidated(from: data) else {
                    return .invalidPayload
                }
                let etag = http.value(forHTTPHeaderField: "ETag") ?? "\"\(payload.version)\""
                return .updated(payload, etag: etag)
            default:
                return .unavailable
            }
        } catch {
            return .networkFailure
        }
    }
}

@Observable
@MainActor
final class AppearanceRepository {
    static let foregroundThrottleSeconds: TimeInterval = 60

    private(set) var current: BrandPaletteSet
    private(set) var currentVersion: Int
    private(set) var hasServerConfig: Bool
    private(set) var lastRefreshAt: Date?

    private var cachedETag: String?
    private var isRefreshing: Bool = false

    private let fetcher: any AppearanceFetching
    private let cacheStore: any AppearanceCacheStore
    private let clock: any AppClock
    private let logger: RedactingAppLogger

    init(
        fetcher: any AppearanceFetching,
        cacheStore: any AppearanceCacheStore = UserDefaultsAppearanceCacheStore(),
        clock: any AppClock = SystemClock(),
        logger: RedactingAppLogger = RedactingAppLogger()
    ) {
        self.fetcher = fetcher
        self.cacheStore = cacheStore
        self.clock = clock
        self.logger = logger

        if let cached = cacheStore.read() {
            self.current = cached.paletteSet
            self.currentVersion = cached.version
            self.cachedETag = cached.etag ?? "\"\(cached.version)\""
            self.hasServerConfig = true
        } else {
            self.current = .defaultThi360Green
            self.currentVersion = 0
            self.cachedETag = nil
            self.hasServerConfig = false
        }
    }

    @discardableResult
    func refreshOnLaunch() async -> AppearanceFetchResult {
        await performRefresh()
    }

    @discardableResult
    func refreshOnForegroundIfNeeded() async -> AppearanceFetchResult? {
        let now = clock.now
        if let lastRefreshAt {
            let elapsed = now.timeIntervalSince(lastRefreshAt)
            if elapsed >= 0 && elapsed < Self.foregroundThrottleSeconds {
                return nil
            }
        }
        return await performRefresh()
    }

    @discardableResult
    func refresh() async -> AppearanceFetchResult {
        await performRefresh()
    }

    private func performRefresh() async -> AppearanceFetchResult {
        guard !isRefreshing else { return .notModified }
        isRefreshing = true
        lastRefreshAt = clock.now
        defer { isRefreshing = false }

        let result = await fetcher.fetch(ifNoneMatch: cachedETag)
        switch result {
        case let .updated(payload, etag):
            let resolvedETag = etag ?? "\"\(payload.version)\""
            if !hasServerConfig || payload.version != currentVersion {
                let paletteSet = payload.toBrandPaletteSet()
                current = paletteSet
                currentVersion = payload.version
                cachedETag = resolvedETag
                hasServerConfig = true
                cacheStore.write(
                    CachedAppearanceRecord(
                        version: payload.version,
                        etag: resolvedETag,
                        paletteSet: paletteSet
                    )
                )
                logger.log(.info, category: "appearance", message: "Applied appearance version=\(payload.version) preset=\(payload.presetId)")
            } else {
                cachedETag = resolvedETag
            }
        case .notModified:
            break
        case .unavailable, .invalidPayload, .networkFailure:
            logger.log(.info, category: "appearance", message: "Retaining active appearance palette")
        }
        return result
    }

    static func makeForCurrentProcess(
        arguments: [String] = ProcessInfo.processInfo.arguments,
        environment: AppEnvironment = .current
    ) -> AppearanceRepository {
        let defaults: UserDefaults
        let webBaseURL: URL

        if environment.isDev {
            if let suiteName = argumentValue(after: "-uitest-defaults-suite", in: arguments),
               let suite = UserDefaults(suiteName: suiteName)
            {
                defaults = suite
            } else {
                defaults = .standard
            }

            if let rawURL = argumentValue(after: "-uitest-web-base-url", in: arguments),
               let parsed = URL(string: rawURL),
               let host = parsed.host?.lowercased(),
               host == "127.0.0.1" || host == "localhost"
            {
                webBaseURL = parsed
            } else {
                webBaseURL = environment.webBaseURL
            }
        } else {
            defaults = .standard
            webBaseURL = environment.webBaseURL
        }

        let cacheStore = UserDefaultsAppearanceCacheStore(userDefaults: defaults)
        if environment.isDev && arguments.contains("-uitest-reset-preferences") {
            cacheStore.clear()
        }

        return AppearanceRepository(
            fetcher: URLSessionAppearanceFetcher(webBaseURL: webBaseURL),
            cacheStore: cacheStore,
            logger: RedactingAppLogger(environment: environment.kind)
        )
    }

    private static func argumentValue(after flag: String, in arguments: [String]) -> String? {
        guard let index = arguments.firstIndex(of: flag), index + 1 < arguments.count else {
            return nil
        }
        return arguments[index + 1]
    }
}
