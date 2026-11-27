import Foundation
import HTTPTypes
import OpenAPIRuntime
import OpenAPIURLSession

enum AttemptAssetError: Error, Equatable, Sendable {
    case blocked
    case invalidAssetId
    case httpStatus(Int)
    case emptyPayload
    case network(URLError.Code)
    case writeFailed
}

extension AttemptAssetRef {
    private static let uuidRegex = try! NSRegularExpression(
        pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
        options: .caseInsensitive
    )
    private static let assetIdRegex = try! NSRegularExpression(pattern: "^[0-9a-zA-Z_-]+$")
    private static let solutionAssetRegex = try! NSRegularExpression(
        pattern: "^/api/v1/me/attempts/[^/?#]+/solution-assets/([0-9a-zA-Z_-]+)(?:[?#].*)?$",
        options: .caseInsensitive
    )
    private static let attemptAssetRegex = try! NSRegularExpression(
        pattern: "^/api/v1/me/attempts/[^/?#]+/assets/([0-9a-zA-Z_-]+)(?:[?#].*)?$",
        options: .caseInsensitive
    )
    private static let adminAssetRegex = try! NSRegularExpression(
        pattern: "^(?:https?://[^/\"'\\s<>]+)?/api/v1/admin/question-assets/([0-9a-fA-F-]+)(?:[?#].*)?$",
        options: .caseInsensitive
    )

    static func resolve(src: String, dataAssetId: String) -> AttemptAssetRef {
        let trimmedSrc = src.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedId = dataAssetId.trimmingCharacters(in: .whitespacesAndNewlines)

        if let id = firstCapture(solutionAssetRegex, in: trimmedSrc), matches(assetIdRegex, id) {
            return .solution(assetId: id)
        }
        if let id = firstCapture(attemptAssetRegex, in: trimmedSrc), matches(assetIdRegex, id) {
            return .attempt(assetId: id)
        }
        if let id = firstCapture(adminAssetRegex, in: trimmedSrc), matches(assetIdRegex, id) {
            return .attempt(assetId: id)
        }

        if trimmedSrc.hasPrefix("//")
            || trimmedSrc.lowercased().hasPrefix("http://")
            || trimmedSrc.lowercased().hasPrefix("https://")
        {
            if matches(uuidRegex, trimmedId) {
                return .attempt(assetId: trimmedId)
            }
            return .blocked
        }

        if trimmedSrc.isEmpty, matches(uuidRegex, trimmedId) {
            return .attempt(assetId: trimmedId)
        }
        if trimmedSrc.hasPrefix("/"), !trimmedSrc.hasPrefix("//"), matches(uuidRegex, trimmedId) {
            return .attempt(assetId: trimmedId)
        }

        return .blocked
    }

    private static func firstCapture(_ regex: NSRegularExpression, in text: String) -> String? {
        let nsText = text as NSString
        guard let match = regex.firstMatch(in: text, range: NSRange(location: 0, length: nsText.length)),
              match.numberOfRanges > 1
        else {
            return nil
        }
        let range = match.range(at: 1)
        guard range.location != NSNotFound else { return nil }
        return nsText.substring(with: range)
    }

    private static func matches(_ regex: NSRegularExpression, _ value: String) -> Bool {
        let nsText = value as NSString
        guard nsText.length > 0 else { return false }
        return regex.firstMatch(in: value, range: NSRange(location: 0, length: nsText.length)) != nil
    }
}

final class TempAssetFile: @unchecked Sendable {
    let url: URL
    private let lock = NSLock()
    private var isClosed = false
    private let onClose: (URL) -> Void

    init(url: URL, onClose: @escaping (URL) -> Void) {
        self.url = url
        self.onClose = onClose
    }

    func close() {
        lock.lock()
        let shouldClose = !isClosed
        isClosed = true
        lock.unlock()
        guard shouldClose else { return }
        try? FileManager.default.removeItem(at: url)
        onClose(url)
    }

    deinit {
        close()
    }
}

private struct BinaryAssetContentTypeMiddleware: ClientMiddleware {
    func intercept(
        _ request: HTTPRequest,
        body: HTTPBody?,
        baseURL: URL,
        operationID: String,
        next: @Sendable (HTTPRequest, HTTPBody?, URL) async throws -> (HTTPResponse, HTTPBody?)
    ) async throws -> (HTTPResponse, HTTPBody?) {
        let (response, responseBody) = try await next(request, body, baseURL)
        guard response.status.code == 200,
              operationID == Operations.downloadAttemptAsset.id || operationID == Operations.downloadSolutionAsset.id
        else {
            return (response, responseBody)
        }
        // Backend serves image/*, audio/*, application/pdf while OpenAPI declares application/octet-stream.
        var normalized = response
        normalized.headerFields[.contentType] = "application/octet-stream"
        return (normalized, responseBody)
    }
}

final class AttemptAssetSource: @unchecked Sendable {
    static let tempDirectoryName = "AttemptAssets"
    static let defaultMemoryByteLimit = 32 * 1024 * 1024
    static let maxAssetBytes = 25 * 1024 * 1024
    static let tempFileWriteOptions: Data.WritingOptions = [.atomic, .completeFileProtection]
    static let tempFileProtection: FileProtectionType = .complete

    let attemptId: String
    private let apiClient: Client
    private let cachesDirectoryURL: URL
    private let memoryCache = NSCache<NSString, NSData>()
    private let lock = NSLock()
    private var inFlightTasks: [AttemptAssetRef: Task<Data, Error>] = [:]
    private var openTempFiles: Set<URL> = []
    private var purgeGeneration: UInt64 = 0

    private static let registryLock = NSLock()
    private static var activeSources: [WeakSourceBox] = []
    private static var globalPurgeGeneration: UInt64 = 0

    private final class WeakSourceBox {
        weak var source: AttemptAssetSource?
        init(_ source: AttemptAssetSource) { self.source = source }
    }

    init(
        attemptId: String,
        serverURL: URL,
        tokenStore: any TokenStore,
        refresher: TokenRefresher,
        transport: (any ClientTransport)? = nil,
        session: URLSession? = nil,
        logger: RedactingAppLogger = RedactingAppLogger(),
        localeProvider: @escaping @Sendable () async -> AppLocale = { .vi },
        cachesDirectoryURL: URL? = nil,
        memoryByteLimit: Int = AttemptAssetSource.defaultMemoryByteLimit
    ) {
        self.attemptId = attemptId
        self.cachesDirectoryURL = cachesDirectoryURL ?? Self.defaultCachesDirectoryURL()

        let resolvedTransport: any ClientTransport
        if let transport {
            resolvedTransport = transport
        } else {
            let assetSession = session ?? Self.makeNoDiskCacheSession()
            resolvedTransport = URLSessionTransport(configuration: .init(session: assetSession))
        }

        let configuration = Configuration(dateTranscoder: FlexibleISO8601DateTranscoder())
        let acceptLanguage = AcceptLanguageMiddleware(localeProvider: localeProvider)
        let retryAfter = RetryAfterMiddleware()
        let bearer = AuthBearerMiddleware(tokenStore: tokenStore, refresher: refresher)
        let logging = NetworkLoggingMiddleware(logger: logger)
        let binaryContentType = BinaryAssetContentTypeMiddleware()

        self.apiClient = Client(
            serverURL: serverURL,
            configuration: configuration,
            transport: resolvedTransport,
            middlewares: [binaryContentType, acceptLanguage, retryAfter, bearer, logging]
        )

        self.memoryCache.totalCostLimit = memoryByteLimit
        Self.register(self)
    }

    convenience init(
        attemptId: String,
        mobileAPIClient: MobileAPIClient,
        tokenStore: any TokenStore,
        session: URLSession? = nil,
        logger: RedactingAppLogger = RedactingAppLogger(),
        localeProvider: @escaping @Sendable () async -> AppLocale = { .vi },
        cachesDirectoryURL: URL? = nil,
        memoryByteLimit: Int = AttemptAssetSource.defaultMemoryByteLimit
    ) {
        self.init(
            attemptId: attemptId,
            serverURL: mobileAPIClient.serverURL,
            tokenStore: tokenStore,
            refresher: mobileAPIClient.refresher,
            transport: nil,
            session: session,
            logger: logger,
            localeProvider: localeProvider,
            cachesDirectoryURL: cachesDirectoryURL,
            memoryByteLimit: memoryByteLimit
        )
    }

    deinit {
        Self.pruneRegistry()
    }

    var tempDirectoryURL: URL {
        cachesDirectoryURL.appendingPathComponent(Self.tempDirectoryName, isDirectory: true)
    }

    func cachedData(for ref: AttemptAssetRef) -> Data? {
        guard let key = cacheKey(for: ref) else { return nil }
        return memoryCache.object(forKey: key) as Data?
    }

    private func currentGenerations() -> (local: UInt64, global: UInt64) {
        let local = lock.withLock { purgeGeneration }
        let global = Self.registryLock.withLock { Self.globalPurgeGeneration }
        return (local, global)
    }

    private func generationsMatch(_ expected: (local: UInt64, global: UInt64)) -> Bool {
        let current = currentGenerations()
        return current.local == expected.local && current.global == expected.global
    }

    func load(_ ref: AttemptAssetRef) async throws -> Data {
        switch ref {
        case .blocked:
            throw AttemptAssetError.blocked
        case let .attempt(assetId), let .solution(assetId):
            guard !assetId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
                throw AttemptAssetError.invalidAssetId
            }
        }

        let startGens = currentGenerations()
        if let cached = cachedData(for: ref) {
            try Task.checkCancellation()
            guard generationsMatch(startGens) else { throw CancellationError() }
            return cached
        }

        let task: Task<Data, Error> = lock.withLock {
            if let existing = inFlightTasks[ref] {
                return existing
            }
            let created = Task<Data, Error> { [weak self] in
                guard let self else { throw AttemptAssetError.blocked }
                return try await self.fetchRemote(ref)
            }
            inFlightTasks[ref] = created
            return created
        }

        do {
            let data = try await task.value
            try Task.checkCancellation()
            let shouldCache: Bool = lock.withLock {
                if inFlightTasks[ref] == task {
                    inFlightTasks.removeValue(forKey: ref)
                }
                let globalGen = Self.registryLock.withLock { Self.globalPurgeGeneration }
                let stillValid = !Task.isCancelled && purgeGeneration == startGens.local && globalGen == startGens.global
                if stillValid, let key = cacheKey(for: ref) {
                    memoryCache.setObject(data as NSData, forKey: key, cost: data.count)
                }
                return stillValid
            }
            guard shouldCache else { throw CancellationError() }
            return data
        } catch {
            lock.withLock {
                if inFlightTasks[ref] == task {
                    inFlightTasks.removeValue(forKey: ref)
                }
            }
            throw error
        }
    }

    func openForViewer(
        _ ref: AttemptAssetRef,
        fileExtension: String? = nil
    ) async throws -> TempAssetFile {
        let startGens = currentGenerations()
        let data = try await load(ref)
        try Task.checkCancellation()
        guard generationsMatch(startGens) else { throw CancellationError() }

        let dir = tempDirectoryURL
        let fileManager = FileManager.default
        var writtenURL: URL?

        do {
            try fileManager.createDirectory(at: dir, withIntermediateDirectories: true)
            try Self.excludeFromBackup(dir)

            let filename = Self.randomFilename(fileExtension: fileExtension)
            let fileURL = dir.appendingPathComponent(filename, isDirectory: false)
            try data.write(to: fileURL, options: Self.tempFileWriteOptions)
            writtenURL = fileURL
            try? fileManager.setAttributes(
                [.protectionKey: Self.tempFileProtection],
                ofItemAtPath: fileURL.path
            )
            try Self.excludeFromBackup(fileURL)

            let committed: Bool = lock.withLock {
                let globalGen = Self.registryLock.withLock { Self.globalPurgeGeneration }
                guard !Task.isCancelled, purgeGeneration == startGens.local, globalGen == startGens.global else {
                    return false
                }
                openTempFiles.insert(fileURL)
                return true
            }
            guard committed else {
                try? fileManager.removeItem(at: fileURL)
                if let contents = try? fileManager.contentsOfDirectory(atPath: dir.path), contents.isEmpty {
                    try? fileManager.removeItem(at: dir)
                }
                throw CancellationError()
            }

            return TempAssetFile(url: fileURL) { [weak self] closedURL in
                self?.lock.withLock {
                    self?.openTempFiles.remove(closedURL)
                }
            }
        } catch is CancellationError {
            if let writtenURL {
                try? fileManager.removeItem(at: writtenURL)
            }
            throw CancellationError()
        } catch {
            if let writtenURL {
                try? fileManager.removeItem(at: writtenURL)
            }
            throw AttemptAssetError.writeFailed
        }
    }

    func purgeAll() {
        let tasksToCancel: [Task<Data, Error>] = lock.withLock {
            purgeGeneration &+= 1
            let tasks = Array(inFlightTasks.values)
            inFlightTasks.removeAll()
            openTempFiles.removeAll()
            memoryCache.removeAllObjects()
            return tasks
        }
        tasksToCancel.forEach { $0.cancel() }
        Self.removeTempDirectory(at: tempDirectoryURL)
    }

    static func defaultCachesDirectoryURL() -> URL {
        FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first
            ?? FileManager.default.temporaryDirectory
    }

    static func defaultTempDirectoryURL(cachesDirectoryURL: URL? = nil) -> URL {
        (cachesDirectoryURL ?? defaultCachesDirectoryURL())
            .appendingPathComponent(tempDirectoryName, isDirectory: true)
    }

    static func purgeAllGlobal(cachesDirectoryURL: URL? = nil) {
        let liveSources: [AttemptAssetSource] = registryLock.withLock {
            globalPurgeGeneration &+= 1
            activeSources = activeSources.filter { $0.source != nil }
            return activeSources.compactMap(\.source)
        }
        for source in liveSources {
            source.purgeAll()
        }
        removeTempDirectory(at: defaultTempDirectoryURL(cachesDirectoryURL: cachesDirectoryURL))
    }

    static func makeNoDiskCacheSession(
        protocolClasses: [AnyClass]? = nil
    ) -> URLSession {
        let config = URLSessionConfiguration.ephemeral
        config.urlCache = nil
        config.requestCachePolicy = .reloadIgnoringLocalCacheData
        config.httpCookieStorage = nil
        config.httpShouldSetCookies = false
        if let protocolClasses {
            config.protocolClasses = protocolClasses
        }
        return URLSession(configuration: config)
    }

    private func fetchRemote(_ ref: AttemptAssetRef) async throws -> Data {
        do {
            switch ref {
            case .blocked:
                throw AttemptAssetError.blocked

            case let .attempt(assetId):
                let output = try await apiClient.downloadAttemptAsset(
                    path: .init(attemptId: attemptId, assetId: assetId)
                )
                switch output {
                case let .ok(response):
                    let body = try response.body.binary
                    let data = try await Data(collecting: body, upTo: Self.maxAssetBytes)
                    guard !data.isEmpty else { throw AttemptAssetError.emptyPayload }
                    return data
                case .badRequest:
                    throw AttemptAssetError.httpStatus(400)
                case .unauthorized:
                    throw AttemptAssetError.httpStatus(401)
                case .forbidden:
                    throw AttemptAssetError.httpStatus(403)
                case .notFound:
                    throw AttemptAssetError.httpStatus(404)
                case .conflict:
                    throw AttemptAssetError.httpStatus(409)
                case .preconditionFailed:
                    throw AttemptAssetError.httpStatus(412)
                case .contentTooLarge:
                    throw AttemptAssetError.httpStatus(413)
                case .unprocessableContent:
                    throw AttemptAssetError.httpStatus(422)
                case .tooManyRequests:
                    throw AttemptAssetError.httpStatus(429)
                case .internalServerError:
                    throw AttemptAssetError.httpStatus(500)
                case .notImplemented:
                    throw AttemptAssetError.httpStatus(501)
                case .badGateway:
                    throw AttemptAssetError.httpStatus(502)
                case let .undocumented(statusCode, payload):
                    if statusCode == 200, let body = payload.body {
                        let data = try await Data(collecting: body, upTo: Self.maxAssetBytes)
                        guard !data.isEmpty else { throw AttemptAssetError.emptyPayload }
                        return data
                    }
                    throw AttemptAssetError.httpStatus(statusCode)
                }

            case let .solution(assetId):
                let output = try await apiClient.downloadSolutionAsset(
                    path: .init(attemptId: attemptId, assetId: assetId)
                )
                switch output {
                case let .ok(response):
                    let body = try response.body.binary
                    let data = try await Data(collecting: body, upTo: Self.maxAssetBytes)
                    guard !data.isEmpty else { throw AttemptAssetError.emptyPayload }
                    return data
                case .badRequest:
                    throw AttemptAssetError.httpStatus(400)
                case .unauthorized:
                    throw AttemptAssetError.httpStatus(401)
                case .forbidden:
                    throw AttemptAssetError.httpStatus(403)
                case .notFound:
                    throw AttemptAssetError.httpStatus(404)
                case .conflict:
                    throw AttemptAssetError.httpStatus(409)
                case .preconditionFailed:
                    throw AttemptAssetError.httpStatus(412)
                case .contentTooLarge:
                    throw AttemptAssetError.httpStatus(413)
                case .unprocessableContent:
                    throw AttemptAssetError.httpStatus(422)
                case .tooManyRequests:
                    throw AttemptAssetError.httpStatus(429)
                case .internalServerError:
                    throw AttemptAssetError.httpStatus(500)
                case .notImplemented:
                    throw AttemptAssetError.httpStatus(501)
                case .badGateway:
                    throw AttemptAssetError.httpStatus(502)
                case let .undocumented(statusCode, payload):
                    if statusCode == 200, let body = payload.body {
                        let data = try await Data(collecting: body, upTo: Self.maxAssetBytes)
                        guard !data.isEmpty else { throw AttemptAssetError.emptyPayload }
                        return data
                    }
                    throw AttemptAssetError.httpStatus(statusCode)
                }
            }
        } catch let error as AttemptAssetError {
            throw error
        } catch {
            let classified = MobileApiError.classify(error)
            switch classified {
            case let .network(code):
                throw AttemptAssetError.network(code)
            case let .api(statusCode, _), let .unexpectedResponse(statusCode):
                throw AttemptAssetError.httpStatus(statusCode)
            }
        }
    }

    private func cacheKey(for ref: AttemptAssetRef) -> NSString? {
        switch ref {
        case .blocked:
            nil
        case let .attempt(assetId):
            "\(attemptId)|attempt|\(assetId)" as NSString
        case let .solution(assetId):
            "\(attemptId)|solution|\(assetId)" as NSString
        }
    }

    private static func randomFilename(fileExtension: String?) -> String {
        let base = UUID().uuidString.lowercased()
        guard let rawExt = fileExtension?.trimmingCharacters(in: .whitespacesAndNewlines),
              !rawExt.isEmpty
        else {
            return base
        }
        let cleaned = rawExt
            .replacingOccurrences(of: ".", with: "")
            .filter { $0.isASCII && ($0.isLetter || $0.isNumber) }
            .lowercased()
        guard !cleaned.isEmpty, cleaned.count <= 10 else { return base }
        return "\(base).\(cleaned)"
    }

    private static func excludeFromBackup(_ url: URL) throws {
        var mutableURL = url
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try mutableURL.setResourceValues(values)
    }

    private static func removeTempDirectory(at directoryURL: URL) {
        let fileManager = FileManager.default
        guard fileManager.fileExists(atPath: directoryURL.path) else { return }
        if let contents = try? fileManager.contentsOfDirectory(
            at: directoryURL,
            includingPropertiesForKeys: nil
        ) {
            for item in contents {
                try? fileManager.removeItem(at: item)
            }
        }
        try? fileManager.removeItem(at: directoryURL)
    }

    private static func register(_ source: AttemptAssetSource) {
        registryLock.withLock {
            activeSources = activeSources.filter { $0.source != nil }
            activeSources.append(WeakSourceBox(source))
        }
    }

    private static func pruneRegistry() {
        registryLock.withLock {
            activeSources = activeSources.filter { $0.source != nil }
        }
    }
}

struct AttemptAssetTempCleanupParticipant: SessionCleanupParticipant, Sendable {
    let id = "richContent.attemptAssets"
    private let cachesDirectoryURL: URL?

    init(cachesDirectoryURL: URL? = nil) {
        self.cachesDirectoryURL = cachesDirectoryURL
    }

    func onLogout(accountId: String) async throws {
        AttemptAssetSource.purgeAllGlobal(cachesDirectoryURL: cachesDirectoryURL)
    }

    func onAppStart() async throws {
        AttemptAssetSource.purgeAllGlobal(cachesDirectoryURL: cachesDirectoryURL)
    }
}

#if THI360_DEV
import UIKit

private final class DevHarnessAssetURLProtocol: URLProtocol {
    static let samplePNG: Data = {
        UIGraphicsImageRenderer(size: CGSize(width: 260, height: 180)).image { ctx in
            UIColor(hex: "#047857").setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: 260, height: 180))
            UIColor(hex: "#F59223").setFill()
            ctx.fill(CGRect(x: 40, y: 40, width: 180, height: 100))
        }.pngData()!
    }()

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        let url = request.url ?? URL(string: "https://api.thi360.test/asset")!
        let response = HTTPURLResponse(
            url: url,
            statusCode: 200,
            httpVersion: "HTTP/1.1",
            headerFields: ["Content-Type": "image/png"]
        )!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Self.samplePNG)
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}

extension AttemptAssetSource {
    static func makeDevHarnessSource(attemptId: String) -> AttemptAssetSource {
        let tokenStore = InMemoryTokenStore(
            initialTokens: SessionTokens(
                accessToken: "harness-token",
                refreshToken: "harness-refresh",
                accessExpiresIn: 3600,
                refreshExpiresIn: 86_400,
                mustChangePassword: false
            )
        )
        let refresher = TokenRefresher(
            tokenStore: tokenStore,
            refreshExecutor: { _ in throw MobileApiError.unexpectedResponse(statusCode: 401) }
        )
        let session = makeNoDiskCacheSession(protocolClasses: [DevHarnessAssetURLProtocol.self])
        return AttemptAssetSource(
            attemptId: attemptId,
            serverURL: URL(string: "https://api.thi360.test")!,
            tokenStore: tokenStore,
            refresher: refresher,
            session: session
        )
    }

    static func tempFileCountForHarness() -> Int {
        let dir = defaultTempDirectoryURL()
        guard let items = try? FileManager.default.contentsOfDirectory(
            at: dir,
            includingPropertiesForKeys: nil
        ) else {
            return 0
        }
        return items.count
    }

    static func fileExistsForHarness(at url: URL) -> Bool {
        FileManager.default.fileExists(atPath: url.path)
    }
}
#endif
