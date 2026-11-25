import Foundation
import HTTPTypes
import OpenAPIRuntime
import XCTest
@testable import Thi360

enum ContractFixtureLoader {
    static var repoRootURL: URL {
        // apps/ios/Thi360Tests/Session/ContractFixtureLoader.swift -> repo root is 4 parents up.
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
    }

    static var contractsMobileURL: URL {
        repoRootURL.appendingPathComponent("contracts/mobile", isDirectory: true)
    }

    static func loadData(relativePath: String) throws -> Data {
        let url = contractsMobileURL.appendingPathComponent(relativePath)
        return try Data(contentsOf: url)
    }

    static func loadAuthFixture(_ filename: String) throws -> Data {
        try loadData(relativePath: "auth/\(filename)")
    }

    static func loadApiFixture(_ filename: String) throws -> Data {
        try loadData(relativePath: "api/\(filename)")
    }
}

struct RecordedHTTPRequest: Sendable {
    let method: HTTPRequest.Method
    let path: String
    let headers: HTTPFields
    let bodyData: Data?

    func header(_ name: HTTPField.Name) -> String? {
        headers[name]
    }
}

final class MockClientTransport: ClientTransport, @unchecked Sendable {
    typealias Handler = @Sendable (RecordedHTTPRequest) async throws -> (HTTPResponse, HTTPBody?)

    private let lock = NSLock()
    private var handler: Handler
    private var requests: [RecordedHTTPRequest] = []

    init(handler: @escaping Handler) {
        self.handler = handler
    }

    func setHandler(_ newHandler: @escaping Handler) {
        lock.lock()
        self.handler = newHandler
        lock.unlock()
    }

    var recordedRequests: [RecordedHTTPRequest] {
        lock.lock()
        defer { lock.unlock() }
        return requests
    }

    func send(
        _ request: HTTPRequest,
        body: HTTPBody?,
        baseURL: URL,
        operationID: String
    ) async throws -> (HTTPResponse, HTTPBody?) {
        let bodyData: Data?
        if let body {
            bodyData = try await Data(collecting: body, upTo: 1_048_576)
        } else {
            bodyData = nil
        }

        let recorded = RecordedHTTPRequest(
            method: request.method,
            path: request.path ?? "/",
            headers: request.headerFields,
            bodyData: bodyData
        )

        let activeHandler: Handler = {
            lock.lock()
            defer { lock.unlock() }
            requests.append(recorded)
            return handler
        }()

        return try await activeHandler(recorded)
    }

    static func jsonResponse(statusCode: Int, data: Data) -> (HTTPResponse, HTTPBody?) {
        var fields = HTTPFields()
        fields[.contentType] = "application/json; charset=utf-8"
        let response = HTTPResponse(status: HTTPResponse.Status(code: statusCode), headerFields: fields)
        return (response, HTTPBody(data))
    }

    static func emptyResponse(statusCode: Int) -> (HTTPResponse, HTTPBody?) {
        let response = HTTPResponse(status: HTTPResponse.Status(code: statusCode))
        return (response, nil)
    }
}

enum SessionTestFactory {
    @MainActor
    static func makeStack(
        initialTokens: SessionTokens? = nil,
        locale: AppLocale = .vi,
        environment: BuildEnvironment = .dev,
        logSink: InMemoryLogSink = InMemoryLogSink(),
        handler: @escaping MockClientTransport.Handler
    ) -> (SessionStore, InMemoryTokenStore, MockClientTransport, AppPreferences) {
        let suite = "vn.unicom.thi360.tests.session.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        let preferences = AppPreferences(userDefaults: defaults)
        preferences.locale = locale

        let tokenStore = InMemoryTokenStore(initialTokens: initialTokens)
        let transport = MockClientTransport(handler: handler)
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
        let sessionStore = SessionStore(
            tokenStore: tokenStore,
            apiClient: client,
            logger: logger
        )
        return (sessionStore, tokenStore, transport, preferences)
    }
}
