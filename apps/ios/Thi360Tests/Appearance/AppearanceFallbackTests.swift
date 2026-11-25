import Foundation
import XCTest
@testable import Thi360

@MainActor
final class AppearanceFallbackTests: XCTestCase {
    func testFirstLaunchOfflineUsesDefaultLightAndCachedOfflineUsesSavedPalette() async throws {
        let suite = "vn.unicom.thi360.tests.appearance.fallback.\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }

        let cacheStore = UserDefaultsAppearanceCacheStore(userDefaults: defaults)

        // 1. First launch offline with no cache -> uses built-in thi360-green light.
        let offlineRepo = AppearanceRepository(
            fetcher: MockAppearanceFetcher { _ in .networkFailure },
            cacheStore: cacheStore,
            clock: ManualClock()
        )
        XCTAssertEqual(offlineRepo.current, .defaultThi360Green)
        XCTAssertEqual(offlineRepo.current.defaultMode, .light)
        let offlineResult = await offlineRepo.refreshOnLaunch()
        XCTAssertEqual(offlineResult, .networkFailure)
        XCTAssertEqual(offlineRepo.current, .defaultThi360Green)

        // 2. Fetch succeeds with ocean-blue version 2 -> persists to cache.
        let oceanPayload = try AlternatePaletteFixtures.oceanBluePayload(version: 2, mode: .dark)
        let onlineRepo = AppearanceRepository(
            fetcher: MockAppearanceFetcher { _ in .updated(oceanPayload, etag: "\"2\"") },
            cacheStore: cacheStore,
            clock: ManualClock()
        )
        _ = await onlineRepo.refreshOnLaunch()
        XCTAssertEqual(onlineRepo.current.presetId, "ocean-blue")

        // 3. Relaunch offline -> loads cached ocean-blue palette immediately and retains it.
        let relaunchedOfflineRepo = AppearanceRepository(
            fetcher: MockAppearanceFetcher { _ in .networkFailure },
            cacheStore: cacheStore,
            clock: ManualClock()
        )
        XCTAssertEqual(relaunchedOfflineRepo.current.presetId, "ocean-blue")
        XCTAssertEqual(relaunchedOfflineRepo.currentVersion, 2)
        _ = await relaunchedOfflineRepo.refreshOnLaunch()
        XCTAssertEqual(relaunchedOfflineRepo.current.presetId, "ocean-blue")
    }

    func testServiceUnavailable503MalformedJSONAndBadHexRetainActivePalette() async throws {
        let oceanPayload = try AlternatePaletteFixtures.oceanBluePayload(version: 2, mode: .light)
        let cache = InMemoryAppearanceCacheStore(
            initial: CachedAppearanceRecord(
                version: 2,
                etag: "\"2\"",
                paletteSet: oceanPayload.toBrandPaletteSet()
            )
        )

        // Verify payload boundary rejects malformed JSON, missing tokens, and invalid hex codes.
        XCTAssertNil(AppearanceTokensPayload.decodeValidated(from: Data("not-json".utf8)))
        XCTAssertNil(AppearanceTokensPayload.decodeValidated(from: Data(#"{"version":3,"mode":"light","presetId":"broken"}"#.utf8)))

        let badHexJSON = AlternatePaletteFixtures.oceanBlueVersion2JSON.replacingOccurrences(
            of: "#1d4ed8",
            with: "not-a-hex"
        )
        XCTAssertNil(AppearanceTokensPayload.decodeValidated(from: Data(badHexJSON.utf8)))

        let negativeVersionJSON = AlternatePaletteFixtures.oceanBlueVersion2JSON.replacingOccurrences(
            of: "\"version\": 2",
            with: "\"version\": -1"
        )
        XCTAssertNil(AppearanceTokensPayload.decodeValidated(from: Data(negativeVersionJSON.utf8)))

        // 503 (e.g. backend error or unknown presetId on web route), invalid payload, and network error all retain ocean-blue.
        for failureResult in [AppearanceFetchResult.unavailable, .invalidPayload, .networkFailure] {
            let repo = AppearanceRepository(
                fetcher: MockAppearanceFetcher { _ in failureResult },
                cacheStore: cache,
                clock: ManualClock()
            )
            let result = await repo.refreshOnLaunch()
            XCTAssertEqual(result, failureResult)
            XCTAssertEqual(repo.current.presetId, "ocean-blue")
            XCTAssertEqual(repo.currentVersion, 2)
            XCTAssertEqual(repo.current.light.primaryFill, "#1d4ed8")
        }
    }

    func testURLSessionAppearanceFetcherHandles200And304And503AndMalformedJSON() async throws {
        let greenData = try ContractFixtureLoader.loadData(relativePath: "appearance/tokens-thi360-green.json")

        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [StubAppearanceURLProtocol.self]
        let session = URLSession(configuration: config)
        let fetcher = URLSessionAppearanceFetcher(
            webBaseURL: URL(string: "https://web.thi360.test")!,
            session: session
        )

        StubAppearanceURLProtocol.setHandler { request in
            XCTAssertEqual(request.url?.path, "/mobile/appearance-tokens.json")
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 200,
                httpVersion: nil,
                headerFields: ["ETag": "\"0\"", "Content-Type": "application/json"]
            )!
            return (response, greenData)
        }
        let okResult = await fetcher.fetch(ifNoneMatch: nil)
        guard case let .updated(payload, etag) = okResult else {
            return XCTFail("Expected .updated on 200")
        }
        XCTAssertEqual(payload.presetId, "thi360-green")
        XCTAssertEqual(etag, "\"0\"")

        StubAppearanceURLProtocol.setHandler { request in
            XCTAssertEqual(request.value(forHTTPHeaderField: "If-None-Match"), "\"0\"")
            let response = HTTPURLResponse(url: request.url!, statusCode: 304, httpVersion: nil, headerFields: ["ETag": "\"0\""])!
            return (response, Data())
        }
        let notMod = await fetcher.fetch(ifNoneMatch: "\"0\"")
        XCTAssertEqual(notMod, .notModified)

        StubAppearanceURLProtocol.setHandler { request in
            let response = HTTPURLResponse(url: request.url!, statusCode: 503, httpVersion: nil, headerFields: nil)!
            return (response, Data(#"{"code":"APPEARANCE_UNAVAILABLE"}"#.utf8))
        }
        let unavailable = await fetcher.fetch(ifNoneMatch: "\"0\"")
        XCTAssertEqual(unavailable, .unavailable)

        StubAppearanceURLProtocol.setHandler { request in
            let response = HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: nil, headerFields: nil)!
            return (response, Data("{broken".utf8))
        }
        let invalid = await fetcher.fetch(ifNoneMatch: "\"0\"")
        XCTAssertEqual(invalid, .invalidPayload)
    }
}

private final class StubAppearanceURLProtocol: URLProtocol, @unchecked Sendable {
    private static let lock = NSLock()
    private static var handler: ((URLRequest) throws -> (HTTPURLResponse, Data))?

    static func setHandler(_ newHandler: @escaping (URLRequest) throws -> (HTTPURLResponse, Data)) {
        lock.lock()
        handler = newHandler
        lock.unlock()
    }

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        let currentHandler: ((URLRequest) throws -> (HTTPURLResponse, Data))? = {
            Self.lock.lock()
            defer { Self.lock.unlock() }
            return Self.handler
        }()

        guard let currentHandler else {
            client?.urlProtocol(self, didFailWithError: URLError(.badServerResponse))
            return
        }

        do {
            let (response, data) = try currentHandler(request)
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: data)
            client?.urlProtocolDidFinishLoading(self)
        } catch {
            client?.urlProtocol(self, didFailWithError: error)
        }
    }

    override func stopLoading() {}
}
