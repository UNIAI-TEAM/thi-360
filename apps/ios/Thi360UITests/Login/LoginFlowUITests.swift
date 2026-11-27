import Foundation
import Network
import XCTest

final class LoginFlowUITests: XCTestCase {
    private var server: LocalFixtureHTTPServer?

    override func setUpWithError() throws {
        continueAfterFailure = false
        let repoRoot = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
        let authDir = repoRoot.appendingPathComponent("contracts/mobile/auth", isDirectory: true)
        let apiDir = repoRoot.appendingPathComponent("contracts/mobile/api", isDirectory: true)
        let loginBody = try Data(contentsOf: authDir.appendingPathComponent("login-200.json"))
        let refreshBody = try Data(contentsOf: authDir.appendingPathComponent("refresh-200.json"))
        let filtersBody = try Data(contentsOf: apiDir.appendingPathComponent("storefront-filters-200.json"))
        let productsBody = try Data(contentsOf: apiDir.appendingPathComponent("storefront-products-list-200.json"))
        let accessBody = try Data(contentsOf: apiDir.appendingPathComponent("exam-access-query-200.json"))

        let mockServer = try LocalFixtureHTTPServer { method, path in
            if method == "POST", path == "/api/v1/auth/mobile/login" {
                return (200, loginBody)
            }
            if method == "POST", path == "/api/v1/auth/mobile/refresh" {
                return (200, refreshBody)
            }
            if method == "GET", path == "/api/v1/public/storefront/filters" {
                return (200, filtersBody)
            }
            if method == "GET", path == "/api/v1/public/storefront/products" {
                return (200, productsBody)
            }
            if method == "POST", path == "/api/v1/me/exam-access/query" {
                return (200, accessBody)
            }
            return (404, Data(#"{"code":"NOT_FOUND","message":"Not found","fieldErrors":[],"traceId":"0"}"#.utf8))
        }
        try mockServer.start()
        self.server = mockServer
    }

    override func tearDownWithError() throws {
        server?.stop()
        server = nil
    }

    func testSuccessAndRelaunch() throws {
        let server = try XCTUnwrap(server)
        let suite = "vn.unicom.thi360.uitest.login.\(UUID().uuidString)"
        let keychainService = "vn.unicom.thi360.uitest.keychain.\(UUID().uuidString)"
        let baseURL = "http://127.0.0.1:\(server.port)"

        let app = XCUIApplication()
        app.launchArguments = [
            "-uitest-defaults-suite", suite,
            "-uitest-reset-preferences",
            "-uitest-keychain-service", keychainService,
            "-uitest-reset-session",
            "-uitest-api-base-url", baseURL
        ]
        app.launch()

        let identifierField = app.textFields["login.identifierField"]
        XCTAssertTrue(identifierField.waitForExistence(timeout: 8), "Cold launch without session must show Login screen")

        let passwordField = app.secureTextFields["login.passwordField"]
        XCTAssertTrue(passwordField.waitForExistence(timeout: 5), "Password field must be visible on Login screen")

        let passwordToggle = app.buttons["login.passwordToggle"]
        XCTAssertTrue(passwordToggle.waitForExistence(timeout: 5))
        XCTAssertGreaterThanOrEqual(round(passwordToggle.frame.width * 100) / 100, 44)
        XCTAssertGreaterThanOrEqual(round(passwordToggle.frame.height * 100) / 100, 44)

        let forgotButton = app.buttons["login.forgotPasswordButton"]
        XCTAssertTrue(forgotButton.waitForExistence(timeout: 5))
        XCTAssertGreaterThanOrEqual(round(forgotButton.frame.height * 100) / 100, 44)

        let registerButton = app.buttons["login.registerButton"]
        XCTAssertTrue(registerButton.waitForExistence(timeout: 5))
        XCTAssertGreaterThanOrEqual(round(registerButton.frame.height * 100) / 100, 44)

        let submitButton = app.buttons["login.submitButton"]
        XCTAssertTrue(submitButton.waitForExistence(timeout: 5))
        XCTAssertGreaterThanOrEqual(round(submitButton.frame.height * 100) / 100, 44)

        let languageButton = app.buttons["login.languageButton"]
        XCTAssertTrue(languageButton.waitForExistence(timeout: 5))
        XCTAssertGreaterThanOrEqual(round(languageButton.frame.width * 100) / 100, 44)
        XCTAssertGreaterThanOrEqual(round(languageButton.frame.height * 100) / 100, 44)

        identifierField.tap()
        identifierField.typeText("student.demo")

        passwordField.tap()
        passwordField.typeText("Password123!")

        submitButton.tap()

        let homeHeading = app.staticTexts["catalog.heading"].firstMatch
        XCTAssertTrue(
            homeHeading.waitForExistence(timeout: 8),
            "Successful login must navigate to Home catalog destination"
        )
        XCTAssertEqual(homeHeading.label, "Chọn bài thi")
        XCTAssertTrue(
            server.recordedPaths.contains("POST /api/v1/auth/mobile/login"),
            "Expected login call to local fixture server, got: \(server.recordedPaths)"
        )

        app.terminate()

        let relaunched = XCUIApplication()
        relaunched.launchArguments = [
            "-uitest-defaults-suite", suite,
            "-uitest-keychain-service", keychainService,
            "-uitest-api-base-url", baseURL
        ]
        relaunched.launch()

        let relaunchedHeading = relaunched.staticTexts["catalog.heading"].firstMatch
        XCTAssertTrue(
            relaunchedHeading.waitForExistence(timeout: 8),
            "Relaunching after successful login must open directly into Home without showing Login screen"
        )
        XCTAssertEqual(relaunchedHeading.label, "Chọn bài thi")
        XCTAssertFalse(relaunched.textFields["login.identifierField"].exists)
    }
}

private final class LocalFixtureHTTPServer: @unchecked Sendable {
    typealias Responder = @Sendable (String, String) -> (Int, Data)

    private let listener: NWListener
    private let queue = DispatchQueue(label: "vn.unicom.thi360.uitest.httpserver")
    private let responder: Responder
    private let lock = NSLock()
    private var paths: [String] = []
    private(set) var port: UInt16 = 0

    var recordedPaths: [String] {
        lock.lock()
        defer { lock.unlock() }
        return paths
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
            throw NSError(domain: "LocalFixtureHTTPServer", code: -1, userInfo: [NSLocalizedDescriptionKey: "Timed out starting HTTP server"])
        }
        if let startError {
            throw startError
        }
        guard port > 0 else {
            throw NSError(domain: "LocalFixtureHTTPServer", code: -2, userInfo: [NSLocalizedDescriptionKey: "Failed to bind ephemeral loopback port"])
        }
    }

    func stop() {
        listener.cancel()
    }

    private func handleConnection(_ connection: NWConnection) {
        connection.start(queue: queue)
        receiveHTTPRequest(on: connection, buffer: Data())
    }

    private func receiveHTTPRequest(on connection: NWConnection, buffer: Data) {
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
                let bodyLength = accumulated.count - headerRange.upperBound

                if bodyLength >= contentLength || isComplete {
                    self.respond(to: headerString, on: connection)
                    return
                }
            }

            if isComplete {
                connection.cancel()
                return
            }
            self.receiveHTTPRequest(on: connection, buffer: accumulated)
        }
    }

    private func respond(to headerString: String, on connection: NWConnection) {
        let firstLine = headerString.split(separator: "\r\n", maxSplits: 1).first ?? ""
        let parts = firstLine.split(separator: " ")
        let method = parts.count > 0 ? String(parts[0]) : "GET"
        let fullTarget = parts.count > 1 ? String(parts[1]) : "/"
        let path = fullTarget.split(separator: "?", maxSplits: 1).first.map(String.init) ?? fullTarget

        lock.lock()
        paths.append("\(method) \(path)")
        lock.unlock()

        let (statusCode, body) = responder(method, path)
        let reason: String
        switch statusCode {
        case 200: reason = "OK"
        case 201: reason = "Created"
        case 204: reason = "No Content"
        case 401: reason = "Unauthorized"
        case 403: reason = "Forbidden"
        case 404: reason = "Not Found"
        default: reason = "Response"
        }

        var responseHeader = "HTTP/1.1 \(statusCode) \(reason)\r\n"
        responseHeader += "Content-Type: application/json; charset=utf-8\r\n"
        responseHeader += "Content-Length: \(body.count)\r\n"
        responseHeader += "Connection: close\r\n\r\n"

        var responseData = Data(responseHeader.utf8)
        responseData.append(body)

        connection.send(content: responseData, completion: .contentProcessed { _ in
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
