import Foundation
import Network

final class RecoveryFixtureHTTPServer: @unchecked Sendable {
    typealias Responder = @Sendable (String, String, Data) -> (Int, Data)

    private let listener: NWListener
    private let queue = DispatchQueue(label: "vn.unicom.thi360.uitest.recovery.httpserver")
    private let responder: Responder
    private let lock = NSLock()
    private var requests: [(method: String, path: String, body: Data)] = []
    private(set) var port: UInt16 = 0

    var recordedPaths: [String] {
        lock.lock()
        defer { lock.unlock() }
        return requests.map { "\($0.method) \($0.path)" }
    }

    init(responder: @escaping Responder) throws {
        let parameters = NWParameters.tcp
        parameters.requiredLocalEndpoint = NWEndpoint.hostPort(host: .ipv4(.loopback), port: .any)
        self.listener = try NWListener(using: parameters)
        self.responder = responder
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
            throw NSError(domain: "RecoveryFixtureHTTPServer", code: -1)
        }
        if let startError {
            throw startError
        }
        guard port > 0 else {
            throw NSError(domain: "RecoveryFixtureHTTPServer", code: -2)
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

        lock.lock()
        requests.append((method, path, body))
        lock.unlock()

        let (statusCode, responseBody) = responder(method, path, body)
        let reason: String
        switch statusCode {
        case 200: reason = "OK"
        case 201: reason = "Created"
        case 202: reason = "Accepted"
        case 204: reason = "No Content"
        case 400: reason = "Bad Request"
        case 422: reason = "Unprocessable Entity"
        default: reason = "Response"
        }

        var responseHeader = "HTTP/1.1 \(statusCode) \(reason)\r\n"
        if statusCode != 204 {
            responseHeader += "Content-Type: application/json; charset=utf-8\r\n"
            responseHeader += "Content-Length: \(responseBody.count)\r\n"
        }
        responseHeader += "Connection: close\r\n\r\n"

        var payload = Data(responseHeader.utf8)
        if statusCode != 204 {
            payload.append(responseBody)
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
