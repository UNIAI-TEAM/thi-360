import Foundation
import Network

struct ContractHTTPResponse: Sendable {
    let statusCode: Int
    let body: Data
    let headers: [String: String]

    init(statusCode: Int, body: Data = Data(), headers: [String: String] = [:]) {
        self.statusCode = statusCode
        self.body = body
        self.headers = headers
    }
}

final class ContractFixtureHTTPServer: @unchecked Sendable {
    typealias RouteHandler = @Sendable (String, String, Data) -> ContractHTTPResponse

    struct RouteKey: Hashable, Sendable {
        let method: String
        let path: String

        init(method: String, path: String) {
            self.method = method.uppercased()
            self.path = path.split(separator: "?", maxSplits: 1).first.map(String.init) ?? path
        }
    }

    private let listener: NWListener
    private let queue = DispatchQueue(label: "vn.unicom.thi360.uitest.contract.httpserver")
    private let lock = NSLock()
    private var routes: [RouteKey: RouteHandler] = [:]
    private var fallback: RouteHandler?
    private var requests: [(method: String, path: String, body: Data)] = []
    private(set) var port: UInt16 = 0

    static var repoRootURL: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
    }

    static func loadFixture(_ relativePath: String) throws -> Data {
        let url = repoRootURL
            .appendingPathComponent("contracts/mobile", isDirectory: true)
            .appendingPathComponent(relativePath)
        return try Data(contentsOf: url)
    }

    init(fallback: RouteHandler? = nil) throws {
        let parameters = NWParameters.tcp
        parameters.requiredLocalEndpoint = NWEndpoint.hostPort(host: .ipv4(.loopback), port: .any)
        self.listener = try NWListener(using: parameters)
        self.fallback = fallback
    }

    var recordedPaths: [String] {
        lock.lock()
        defer { lock.unlock() }
        return requests.map { "\($0.method) \($0.path)" }
    }

    func callCount(method: String, path: String) -> Int {
        let target = "\(method.uppercased()) \(path)"
        return recordedPaths.filter { $0 == target }.count
    }

    func register(method: String, path: String, handler: @escaping RouteHandler) {
        let key = RouteKey(method: method, path: path)
        lock.lock()
        routes[key] = handler
        lock.unlock()
    }

    func register(method: String, path: String, statusCode: Int, body: Data = Data(), headers: [String: String] = [:]) {
        register(method: method, path: path) { _, _, _ in
            ContractHTTPResponse(statusCode: statusCode, body: body, headers: headers)
        }
    }

    func registerFixture(method: String, path: String, statusCode: Int = 200, relativePath: String, headers: [String: String] = [:]) throws {
        let data = try Self.loadFixture(relativePath)
        register(method: method, path: path, statusCode: statusCode, body: data, headers: headers)
    }

    func registerStandardAuthFixtures() throws {
        try registerFixture(method: "POST", path: "/api/v1/auth/mobile/login", statusCode: 200, relativePath: "auth/login-200.json")
        try registerFixture(method: "POST", path: "/api/v1/auth/mobile/refresh", statusCode: 200, relativePath: "auth/refresh-200.json")
        register(method: "POST", path: "/api/v1/auth/mobile/logout", statusCode: 204)
        try registerFixture(method: "GET", path: "/api/v1/me", statusCode: 200, relativePath: "api/me-200.json")
        try registerFixture(method: "GET", path: "/api/v1/me/profile", statusCode: 200, relativePath: "api/me-profile-200.json")
    }

    func start() throws {
        let ready = DispatchSemaphore(value: 0)
        var startError: Error?

        listener.stateUpdateHandler = { [weak self] state in
            switch state {
            case .ready:
                self?.port = self?.listener.port?.rawValue ?? 0
                ready.signal()
            case let .failed(error):
                startError = error
                ready.signal()
            default:
                break
            }
        }

        listener.newConnectionHandler = { [weak self] connection in
            self?.handleConnection(connection)
        }

        listener.start(queue: queue)
        let waitResult = ready.wait(timeout: .now() + 5)
        if waitResult == .timedOut {
            throw NSError(domain: "ContractFixtureHTTPServer", code: -1)
        }
        if let startError {
            throw startError
        }
        guard port > 0 else {
            throw NSError(domain: "ContractFixtureHTTPServer", code: -2)
        }
    }

    func stop() {
        listener.cancel()
    }

    private func handleConnection(_ connection: NWConnection) {
        connection.start(queue: queue)
        receive(on: connection, buffer: Data())
    }

    private func receive(on connection: NWConnection, buffer: Data) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 65_536) { [weak self] chunk, _, isComplete, error in
            guard let self else {
                connection.cancel()
                return
            }
            if error != nil {
                connection.cancel()
                return
            }

            var accumulated = buffer
            if let chunk {
                accumulated.append(chunk)
            }

            let separator = Data("\r\n\r\n".utf8)
            if let headerRange = accumulated.range(of: separator) {
                let headerData = accumulated[..<headerRange.lowerBound]
                let headerString = String(data: headerData, encoding: .utf8) ?? ""
                let contentLength = Self.parseContentLength(from: headerString)
                let bodyData = Data(accumulated[headerRange.upperBound...])

                if bodyData.count >= contentLength || isComplete {
                    self.respond(headerString: headerString, body: bodyData, on: connection)
                    return
                }
            }

            if isComplete {
                connection.cancel()
                return
            }
            self.receive(on: connection, buffer: accumulated)
        }
    }

    private func respond(headerString: String, body: Data, on connection: NWConnection) {
        let firstLine = headerString.split(separator: "\r\n", maxSplits: 1).first ?? ""
        let parts = firstLine.split(separator: " ")
        let method = parts.count > 0 ? String(parts[0]) : "GET"
        let fullTarget = parts.count > 1 ? String(parts[1]) : "/"
        let path = fullTarget.split(separator: "?", maxSplits: 1).first.map(String.init) ?? fullTarget
        let key = RouteKey(method: method, path: path)

        let handler: RouteHandler? = {
            lock.lock()
            defer { lock.unlock() }
            requests.append((method, path, body))
            return routes[key] ?? fallback
        }()

        let response = handler?(method, path, body) ?? ContractHTTPResponse(
            statusCode: 404,
            body: Data(#"{"code":"NOT_FOUND","message":"Not found","fieldErrors":[],"traceId":"0"}"#.utf8)
        )

        let reason: String
        switch response.statusCode {
        case 200: reason = "OK"
        case 201: reason = "Created"
        case 202: reason = "Accepted"
        case 204: reason = "No Content"
        case 400: reason = "Bad Request"
        case 401: reason = "Unauthorized"
        case 403: reason = "Forbidden"
        case 404: reason = "Not Found"
        case 422: reason = "Unprocessable Entity"
        case 429: reason = "Too Many Requests"
        default: reason = "Response"
        }

        var responseHeader = "HTTP/1.1 \(response.statusCode) \(reason)\r\n"
        if response.statusCode != 204 {
            responseHeader += "Content-Type: application/json; charset=utf-8\r\n"
            responseHeader += "Content-Length: \(response.body.count)\r\n"
        }
        for (name, value) in response.headers {
            responseHeader += "\(name): \(value)\r\n"
        }
        responseHeader += "Connection: close\r\n\r\n"

        var payload = Data(responseHeader.utf8)
        if response.statusCode != 204 {
            payload.append(response.body)
        }

        connection.send(content: payload, completion: .contentProcessed { _ in
            connection.cancel()
        })
    }

    private static func parseContentLength(from headerString: String) -> Int {
        for line in headerString.split(separator: "\r\n") {
            let lowered = line.lowercased()
            if lowered.hasPrefix("content-length:") {
                let value = lowered.dropFirst("content-length:".count).trimmingCharacters(in: .whitespaces)
                return Int(value) ?? 0
            }
        }
        return 0
    }
}
