import Foundation
import HTTPTypes
import OpenAPIRuntime
import XCTest
@testable import Thi360

struct ContractFixtureIndexEntry: Codable, Equatable, Sendable {
    let file: String
    let operationId: String
    let method: String
    let path: String
    let status: Int
    let schema: String?
    let description: String?
}

struct ContractFixtureIndexFile: Decodable, Sendable {
    let version: Int?
    let fixtures: [ContractFixtureIndexEntry]?
    let entries: [ContractFixtureIndexEntry]?

    var allEntries: [ContractFixtureIndexEntry] {
        fixtures ?? entries ?? []
    }
}

final class ContractFakeTransport: ClientTransport, @unchecked Sendable {
    typealias RouteHandler = @Sendable (RecordedHTTPRequest) async throws -> (HTTPResponse, HTTPBody?)

    struct RouteKey: Hashable, Sendable {
        let method: String
        let path: String

        init(method: HTTPRequest.Method, path: String) {
            self.method = method.rawValue.uppercased()
            self.path = Self.normalize(path: path)
        }

        init(method: String, path: String) {
            self.method = method.uppercased()
            self.path = Self.normalize(path: path)
        }

        static func normalize(path: String) -> String {
            let withoutQuery = path.split(separator: "?", maxSplits: 1).first.map(String.init) ?? path
            if withoutQuery.hasPrefix("/") {
                return withoutQuery
            }
            return "/\(withoutQuery)"
        }
    }

    private let lock = NSLock()
    private var handlers: [RouteKey: RouteHandler] = [:]
    private var queues: [RouteKey: [RouteHandler]] = [:]
    private var fallbackHandler: RouteHandler?
    private var requests: [RecordedHTTPRequest] = []
    private var unmatched: [RecordedHTTPRequest] = []

    init(fallbackHandler: RouteHandler? = nil) {
        self.fallbackHandler = fallbackHandler
    }

    func register(
        method: HTTPRequest.Method,
        path: String,
        handler: @escaping RouteHandler
    ) {
        let key = RouteKey(method: method, path: path)
        lock.lock()
        handlers[key] = handler
        lock.unlock()
    }

    func registerJSON(
        method: HTTPRequest.Method,
        path: String,
        statusCode: Int = 200,
        data: Data,
        headers: [String: String] = [:]
    ) {
        register(method: method, path: path) { _ in
            Self.jsonResponse(statusCode: statusCode, data: data, headers: headers)
        }
    }

    func registerFixture(
        method: HTTPRequest.Method,
        path: String,
        statusCode: Int = 200,
        relativeFixturePath: String,
        headers: [String: String] = [:]
    ) throws {
        let data = try ContractFixtureLoader.loadData(relativePath: relativeFixturePath)
        registerJSON(method: method, path: path, statusCode: statusCode, data: data, headers: headers)
    }

    func registerEmpty(
        method: HTTPRequest.Method,
        path: String,
        statusCode: Int = 204,
        headers: [String: String] = [:]
    ) {
        register(method: method, path: path) { _ in
            Self.emptyResponse(statusCode: statusCode, headers: headers)
        }
    }

    func registerNetworkError(
        method: HTTPRequest.Method,
        path: String,
        code: URLError.Code = .notConnectedToInternet
    ) {
        register(method: method, path: path) { _ in
            throw URLError(code)
        }
    }

    func registerUnauthorized(
        method: HTTPRequest.Method,
        path: String,
        relativeFixturePath: String = "api/me-401-unauthenticated.json"
    ) throws {
        try registerFixture(method: method, path: path, statusCode: 401, relativeFixturePath: relativeFixturePath)
    }

    func registerRateLimited(
        method: HTTPRequest.Method,
        path: String,
        retryAfterHeader: String? = nil,
        bodyData: Data? = nil
    ) {
        var headers: [String: String] = [:]
        if let retryAfterHeader {
            headers["Retry-After"] = retryAfterHeader
        }
        if let bodyData {
            registerJSON(method: method, path: path, statusCode: 429, data: bodyData, headers: headers)
        } else {
            registerEmpty(method: method, path: path, statusCode: 429, headers: headers)
        }
    }

    func enqueue(
        method: HTTPRequest.Method,
        path: String,
        handlers newHandlers: [RouteHandler]
    ) {
        let key = RouteKey(method: method, path: path)
        lock.lock()
        queues[key, default: []].append(contentsOf: newHandlers)
        lock.unlock()
    }

    func setFallback(_ handler: RouteHandler?) {
        lock.lock()
        fallbackHandler = handler
        lock.unlock()
    }

    var recordedRequests: [RecordedHTTPRequest] {
        lock.lock()
        defer { lock.unlock() }
        return requests
    }

    var unmatchedRequests: [RecordedHTTPRequest] {
        lock.lock()
        defer { lock.unlock() }
        return unmatched
    }

    func requests(method: HTTPRequest.Method, path: String) -> [RecordedHTTPRequest] {
        let expected = RouteKey(method: method, path: path)
        return recordedRequests.filter {
            RouteKey(method: $0.method, path: $0.path) == expected
        }
    }

    func callCount(method: HTTPRequest.Method, path: String) -> Int {
        requests(method: method, path: path).count
    }

    func resetRecordedRequests() {
        lock.lock()
        requests.removeAll()
        unmatched.removeAll()
        lock.unlock()
    }

    func send(
        _ request: HTTPRequest,
        body: HTTPBody?,
        baseURL: URL,
        operationID: String
    ) async throws -> (HTTPResponse, HTTPBody?) {
        let bodyData: Data?
        if let body {
            bodyData = try await Data(collecting: body, upTo: 6_291_456)
        } else {
            bodyData = nil
        }

        let recorded = RecordedHTTPRequest(
            method: request.method,
            path: request.path ?? "/",
            headers: request.headerFields,
            bodyData: bodyData
        )
        let key = RouteKey(method: request.method, path: recorded.path)

        let selectedHandler: RouteHandler? = {
            lock.lock()
            defer { lock.unlock() }
            requests.append(recorded)
            if var queued = queues[key], !queued.isEmpty {
                let next = queued.removeFirst()
                queues[key] = queued
                return next
            }
            if let registered = handlers[key] {
                return registered
            }
            if let fallback = fallbackHandler {
                return fallback
            }
            unmatched.append(recorded)
            return nil
        }()

        guard let selectedHandler else {
            let errorPayload = #"{"code":"UNMOCKED_ROUTE","message":"No handler registered for \#(key.method) \#(key.path)","fieldErrors":[],"traceId":"fake-transport"}"#
            return Self.jsonResponse(statusCode: 404, data: Data(errorPayload.utf8))
        }

        return try await selectedHandler(recorded)
    }

    static func jsonResponse(
        statusCode: Int,
        data: Data,
        headers: [String: String] = [:]
    ) -> (HTTPResponse, HTTPBody?) {
        var fields = HTTPFields()
        fields[.contentType] = "application/json; charset=utf-8"
        for (name, value) in headers {
            if let fieldName = HTTPField.Name(name) {
                fields[fieldName] = value
            }
        }
        let response = HTTPResponse(status: HTTPResponse.Status(code: statusCode), headerFields: fields)
        return (response, HTTPBody(data))
    }

    static func emptyResponse(
        statusCode: Int,
        headers: [String: String] = [:]
    ) -> (HTTPResponse, HTTPBody?) {
        var fields = HTTPFields()
        for (name, value) in headers {
            if let fieldName = HTTPField.Name(name) {
                fields[fieldName] = value
            }
        }
        let response = HTTPResponse(status: HTTPResponse.Status(code: statusCode), headerFields: fields)
        return (response, nil)
    }

    static func loadIndex() throws -> [ContractFixtureIndexEntry] {
        let indexURL = ContractFixtureLoader.contractsMobileURL
            .appendingPathComponent("api/index.json")
        let data = try Data(contentsOf: indexURL)
        let decoder = JSONDecoder()
        if let array = try? decoder.decode([ContractFixtureIndexEntry].self, from: data) {
            return array
        }
        let wrapper = try decoder.decode(ContractFixtureIndexFile.self, from: data)
        return wrapper.allEntries
    }

    func registerAuthDefaults() throws {
        try registerFixture(method: .post, path: "/api/v1/auth/mobile/login", statusCode: 200, relativeFixturePath: "auth/login-200.json")
        try registerFixture(method: .post, path: "/api/v1/auth/mobile/refresh", statusCode: 200, relativeFixturePath: "auth/refresh-200.json")
        registerEmpty(method: .post, path: "/api/v1/auth/mobile/logout", statusCode: 204)
        try registerFixture(method: .get, path: "/api/v1/me", statusCode: 200, relativeFixturePath: "api/me-200.json")
        try registerFixture(method: .get, path: "/api/v1/me/profile", statusCode: 200, relativeFixturePath: "api/me-profile-200.json")
    }
}

extension SessionTestFactory {
    @MainActor
    static func makeContractStack(
        initialTokens: SessionTokens? = nil,
        locale: AppLocale = .vi,
        environment: BuildEnvironment = .dev,
        logSink: InMemoryLogSink = InMemoryLogSink(),
        storeProvider: AccountScopedStoreProvider? = nil,
        sessionCleanup: SessionCleanup? = nil,
        configure: (ContractFakeTransport) throws -> Void = { _ in }
    ) throws -> (SessionStore, InMemoryTokenStore, ContractFakeTransport, AppPreferences) {
        let suite = "vn.unicom.thi360.tests.contract.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        let preferences = AppPreferences(userDefaults: defaults)
        preferences.locale = locale

        let tokenStore = InMemoryTokenStore(initialTokens: initialTokens)
        let transport = ContractFakeTransport()
        try configure(transport)

        let logger = RedactingAppLogger(environment: environment, sink: logSink)
        let client = MobileAPIClient(
            serverURL: URL(string: "https://api.thi360.test")!,
            tokenStore: tokenStore,
            transport: transport,
            logger: logger,
            localeProvider: {
                await MainActor.run { preferences.locale }
            }
        )
        let resolvedStoreProvider = storeProvider ?? AccountScopedStoreProvider(
            rootDirectoryURL: FileManager.default.temporaryDirectory
                .appendingPathComponent("thi360-test-store-\(UUID().uuidString)", isDirectory: true)
        )
        let sessionStore = SessionStore(
            tokenStore: tokenStore,
            apiClient: client,
            storeProvider: resolvedStoreProvider,
            sessionCleanup: sessionCleanup,
            logger: logger
        )
        return (sessionStore, tokenStore, transport, preferences)
    }
}
