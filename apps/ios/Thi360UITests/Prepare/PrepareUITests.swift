import Foundation
import Network
import XCTest

final class PrepareUITests: XCTestCase {
    private var server: PrepareFixtureHTTPServer?

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    override func tearDownWithError() throws {
        XCUIDevice.shared.orientation = .portrait
        server?.stop()
        server = nil
    }

    func testBackFromWaiting() throws {
        let contractsDir = Self.contractsMobileDirectory()
        let openStartingData = try Self.loadIndexedFixture(
            contractsMobileDir: contractsDir,
            operationId: "prepare",
            status: 200,
            variant: "open-starting"
        )
        let startingStateData = try Self.loadIndexedFixture(
            contractsMobileDir: contractsDir,
            operationId: "getState",
            status: 200,
            variant: "starting"
        )
        let notFoundData = try Self.loadIndexedFixture(
            contractsMobileDir: contractsDir,
            operationId: "prepare",
            status: 404,
            variant: "not-found"
        )

        let fixtureServer = try Self.makeStandardServer(contractsDir: contractsDir) { method, path in
            if method == "GET", path == "/api/v1/me/entitlements/ent-ui-wait/prepare" {
                return (200, openStartingData)
            }
            if method == "GET", path == "/api/v1/me/attempts/00000000-0000-7000-8000-000000000003" {
                return (200, startingStateData)
            }
            return (404, notFoundData)
        }
        try fixtureServer.start()
        self.server = fixtureServer

        let app = launchApp(
            baseURL: "http://127.0.0.1:\(fixtureServer.port)",
            extraArgs: [
                "-uitest-destination", "myExams",
                "-uitest-route", "prepare/ent-ui-wait"
            ]
        )

        let waitingTitle = app.staticTexts["waiting.title"].firstMatch
        XCTAssertTrue(waitingTitle.waitForExistence(timeout: 10), "Waiting screen must appear immediately for openAttempt=STARTING")

        let pollDeadline = Date().addingTimeInterval(6.0)
        while fixtureServer.count(method: "GET", path: "/api/v1/me/attempts/00000000-0000-7000-8000-000000000003") == 0
                && Date() < pollDeadline
        {
            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        }
        XCTAssertGreaterThanOrEqual(
            fixtureServer.count(method: "GET", path: "/api/v1/me/attempts/00000000-0000-7000-8000-000000000003"),
            1,
            "Waiting screen must actively poll GET /api/v1/me/attempts/{id} before swipe-back"
        )

        let window = app.windows.firstMatch
        let startCoord = window.coordinate(withNormalizedOffset: CGVector(dx: 0.02, dy: 0.5))
        let endCoord = window.coordinate(withNormalizedOffset: CGVector(dx: 0.85, dy: 0.5))
        startCoord.press(forDuration: 0.05, thenDragTo: endCoord)

        let waitDisappeared = NSPredicate(format: "exists == false")
        expectation(for: waitDisappeared, evaluatedWith: waitingTitle)
        waitForExpectations(timeout: 6.0)

        RunLoop.current.run(until: Date().addingTimeInterval(0.4))
        let callsRightAfterBack = fixtureServer.count(
            method: "GET",
            path: "/api/v1/me/attempts/00000000-0000-7000-8000-000000000003"
        )

        RunLoop.current.run(until: Date().addingTimeInterval(2.5))
        let callsAfterSilenceWindow = fixtureServer.count(
            method: "GET",
            path: "/api/v1/me/attempts/00000000-0000-7000-8000-000000000003"
        )
        XCTAssertEqual(
            callsAfterSilenceWindow,
            callsRightAfterBack,
            "No further attempt state polls may fire after leaving waiting screen via swipe-back"
        )
    }

    func testRotationKeepsSelection() throws {
        let contractsDir = Self.contractsMobileDirectory()
        let subjectChoiceData = try Self.makeTwoOptionSubjectChoicePrepareData(contractsDir: contractsDir)
        let notFoundData = try Self.loadIndexedFixture(
            contractsMobileDir: contractsDir,
            operationId: "prepare",
            status: 404,
            variant: "not-found"
        )

        let fixtureServer = try Self.makeStandardServer(contractsDir: contractsDir) { method, path in
            if method == "GET", path == "/api/v1/me/entitlements/ent-ui-rot/prepare" {
                return (200, subjectChoiceData)
            }
            return (404, notFoundData)
        }
        try fixtureServer.start()
        self.server = fixtureServer

        XCUIDevice.shared.orientation = .portrait

        let app = launchApp(
            baseURL: "http://127.0.0.1:\(fixtureServer.port)",
            extraArgs: [
                "-uitest-state-probe",
                "-uitest-destination", "myExams",
                "-uitest-route", "prepare/ent-ui-rot"
            ]
        )

        let scienceOption = app.buttons["prepare.preChoice.option.SCIENCE"].firstMatch
        XCTAssertTrue(scienceOption.waitForExistence(timeout: 10), "PreChoice SCIENCE option must appear")
        scrollToHittable(scienceOption, in: app)
        scienceOption.tap()

        let lySubject = app.buttons["prepare.preChoice.subject.LY"].firstMatch
        XCTAssertTrue(lySubject.waitForExistence(timeout: 6), "Subject LY must appear after selecting SCIENCE")
        scrollToHittable(lySubject, in: app)
        lySubject.tap()

        let hoaSubject = app.buttons["prepare.preChoice.subject.HOA"].firstMatch
        XCTAssertTrue(hoaSubject.waitForExistence(timeout: 6), "Subject HOA must appear")
        scrollToHittable(hoaSubject, in: app)
        hoaSubject.tap()

        let progressLabel = app.staticTexts["prepare.preChoice.subjectProgress"].firstMatch
        XCTAssertTrue(progressLabel.waitForExistence(timeout: 5))
        XCTAssertTrue(progressLabel.label.contains("2/2"), "Expected 2/2 progress before rotation, got \(progressLabel.label)")

        let prepareCallsBefore = fixtureServer.count(method: "GET", path: "/api/v1/me/entitlements/ent-ui-rot/prepare")
        XCTAssertEqual(prepareCallsBefore, 1)

        XCUIDevice.shared.orientation = .landscapeLeft
        RunLoop.current.run(until: Date().addingTimeInterval(0.6))
        XCUIDevice.shared.orientation = .portrait
        RunLoop.current.run(until: Date().addingTimeInterval(0.6))

        let toggleSizeClass = app.buttons["prepare.probe.toggleSizeClass"].firstMatch
        if toggleSizeClass.waitForExistence(timeout: 4) {
            toggleSizeClass.tap()
            RunLoop.current.run(until: Date().addingTimeInterval(0.5))
            if app.buttons["prepare.probe.toggleSizeClass"].firstMatch.waitForExistence(timeout: 4) {
                app.buttons["prepare.probe.toggleSizeClass"].firstMatch.tap()
                RunLoop.current.run(until: Date().addingTimeInterval(0.5))
            }
        }

        let scienceAfter = app.buttons["prepare.preChoice.option.SCIENCE"].firstMatch
        XCTAssertTrue(scienceAfter.waitForExistence(timeout: 6), "SCIENCE option must remain visible after rotation/resize")
        XCTAssertTrue(scienceAfter.isSelected, "SCIENCE option must stay selected after rotation/resize")

        let lyAfter = app.buttons["prepare.preChoice.subject.LY"].firstMatch
        let hoaAfter = app.buttons["prepare.preChoice.subject.HOA"].firstMatch
        XCTAssertTrue(lyAfter.exists && lyAfter.isSelected, "Subject LY must stay selected after rotation/resize")
        XCTAssertTrue(hoaAfter.exists && hoaAfter.isSelected, "Subject HOA must stay selected after rotation/resize")

        let prepareCallsAfter = fixtureServer.count(method: "GET", path: "/api/v1/me/entitlements/ent-ui-rot/prepare")
        XCTAssertEqual(
            prepareCallsAfter,
            prepareCallsBefore,
            "Rotation and size-class change must NOT duplicate GET /prepare calls"
        )
    }

    private func launchApp(baseURL: String, extraArgs: [String]) -> XCUIApplication {
        let suite = "vn.unicom.thi360.uitest.prepare.\(UUID().uuidString)"
        let app = XCUIApplication()
        app.launchArguments = [
            "-uitest-defaults-suite", suite,
            "-uitest-reset-preferences",
            "-uitest-authenticated",
            "-uitest-api-base-url", baseURL
        ] + extraArgs
        app.launch()
        return app
    }

    private func scrollToHittable(_ element: XCUIElement, in app: XCUIApplication, maxSwipes: Int = 6) {
        for _ in 0..<maxSwipes {
            if element.exists && element.isHittable {
                return
            }
            app.swipeUp()
        }
    }

    static func contractsMobileDirectory() -> URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("contracts/mobile", isDirectory: true)
    }

    private struct IndexedFixtureEntry: Decodable {
        let file: String
        let operationId: String
        let status: Int
        let variant: String?
    }

    static func loadIndexedFixture(
        contractsMobileDir: URL,
        operationId: String,
        status: Int,
        variant: String? = nil
    ) throws -> Data {
        let indexData = try Data(contentsOf: contractsMobileDir.appendingPathComponent("api/index.json"))
        let entries = try JSONDecoder().decode([IndexedFixtureEntry].self, from: indexData)
        let byOpStatus = entries.filter { $0.operationId == operationId && $0.status == status }
        let matched: IndexedFixtureEntry?
        if let variant {
            matched = byOpStatus.first { ($0.variant ?? "") == variant }
        } else {
            matched = byOpStatus.first { ($0.variant ?? "").isEmpty } ?? byOpStatus.first
        }
        guard let matched else {
            throw NSError(
                domain: "PrepareUITests",
                code: 1,
                userInfo: [NSLocalizedDescriptionKey: "Missing indexed fixture for \(operationId) \(status) \(variant ?? "*")"]
            )
        }
        let relative = matched.file.contains("/") ? matched.file : "api/\(matched.file)"
        return try Data(contentsOf: contractsMobileDir.appendingPathComponent(relative))
    }

    // Plan A6 derivation: mutates only `preChoice` on certified `pre-choice` fixture using options from certified `per-part` fixture.
    static func makeTwoOptionSubjectChoicePrepareData(contractsDir: URL) throws -> Data {
        let preChoiceData = try loadIndexedFixture(contractsMobileDir: contractsDir, operationId: "prepare", status: 200, variant: "pre-choice")
        let perPartData = try loadIndexedFixture(contractsMobileDir: contractsDir, operationId: "prepare", status: 200, variant: "per-part")
        var preRoot = try XCTUnwrap(JSONSerialization.jsonObject(with: preChoiceData) as? [String: Any])
        let perPartRoot = try XCTUnwrap(JSONSerialization.jsonObject(with: perPartData) as? [String: Any])
        let afterChoices = (perPartRoot["afterStartChoices"] as? [[String: Any]]) ?? []
        var options = (afterChoices.first?["options"] as? [[String: Any]]) ?? []
        if !options.isEmpty {
            options[0]["chooseSubjects"] = 2
        }
        var preChoice = (preRoot["preChoice"] as? [String: Any]) ?? [:]
        preChoice["kind"] = "SUBJECT_CHOICE"
        preChoice["preselectedCode"] = NSNull()
        preChoice["options"] = options
        preRoot["preChoice"] = preChoice
        return try JSONSerialization.data(withJSONObject: preRoot)
    }

    static func makeStandardServer(
        contractsDir: URL,
        handler: @escaping @Sendable (String, String) -> (Int, Data)
    ) throws -> PrepareFixtureHTTPServer {
        let loginData = try loadIndexedFixture(contractsMobileDir: contractsDir, operationId: "mobileLogin", status: 200, variant: "")
        let refreshData = try loadIndexedFixture(contractsMobileDir: contractsDir, operationId: "mobileRefresh", status: 200)
        let profileData = try loadIndexedFixture(contractsMobileDir: contractsDir, operationId: "getMyProfile", status: 200, variant: "")
        let meData = try loadIndexedFixture(contractsMobileDir: contractsDir, operationId: "me", status: 200)
        let filtersData = try loadIndexedFixture(contractsMobileDir: contractsDir, operationId: "getStorefrontFilters", status: 200)
        let productsData = try loadIndexedFixture(contractsMobileDir: contractsDir, operationId: "listStorefrontProducts", status: 200, variant: "")
        let accessData = try loadIndexedFixture(contractsMobileDir: contractsDir, operationId: "queryMyExamAccess", status: 200)

        return try PrepareFixtureHTTPServer { method, fullTarget in
            let components = URLComponents(string: "http://127.0.0.1\(fullTarget)")
            let path = components?.path ?? fullTarget
            if method == "POST", path == "/api/v1/auth/mobile/login" { return (200, loginData) }
            if method == "POST", path == "/api/v1/auth/mobile/refresh" { return (200, refreshData) }
            if method == "GET", path == "/api/v1/me" { return (200, meData) }
            if method == "GET", path == "/api/v1/me/profile" { return (200, profileData) }
            if method == "GET", path == "/api/v1/public/storefront/filters" { return (200, filtersData) }
            if method == "GET", path == "/api/v1/public/storefront/products" { return (200, productsData) }
            if method == "POST", path == "/api/v1/me/exam-access/query" { return (200, accessData) }
            return handler(method, path)
        }
    }
}

final class PrepareFixtureHTTPServer: @unchecked Sendable {
    typealias Handler = @Sendable (String, String) -> (Int, Data)

    private let listener: NWListener
    private let queue = DispatchQueue(label: "vn.unicom.thi360.uitest.preparehttp")
    private let handler: Handler
    private let lock = NSLock()
    private var recordedRequests: [(method: String, path: String)] = []
    private(set) var port: UInt16 = 0

    func count(method: String, path: String) -> Int {
        lock.lock()
        defer { lock.unlock() }
        return recordedRequests.filter { $0.method == method && $0.path == path }.count
    }

    init(handler: @escaping Handler) throws {
        let parameters = NWParameters.tcp
        parameters.requiredLocalEndpoint = NWEndpoint.hostPort(host: .ipv4(.loopback), port: .any)
        self.listener = try NWListener(using: parameters)
        self.handler = handler
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
            connection.start(queue: self?.queue ?? .global())
            self?.receive(on: connection, buffer: Data())
        }
        listener.start(queue: queue)
        if ready.wait(timeout: .now() + 5) == .timedOut {
            throw NSError(domain: "PrepareFixtureHTTPServer", code: -1)
        }
        if let startError { throw startError }
    }

    func stop() {
        listener.cancel()
    }

    private func receive(on connection: NWConnection, buffer: Data) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 65_536) { [weak self] chunk, _, isComplete, error in
            guard let self, error == nil else {
                connection.cancel()
                return
            }
            var accumulated = buffer
            if let chunk { accumulated.append(chunk) }
            let sep = Data("\r\n\r\n".utf8)
            if let range = accumulated.range(of: sep) {
                let headerString = String(data: accumulated[..<range.lowerBound], encoding: .utf8) ?? ""
                let contentLength = Self.parseContentLength(from: headerString)
                if accumulated.count - range.upperBound >= contentLength || isComplete {
                    self.respond(headerString: headerString, on: connection)
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

    private func respond(headerString: String, on connection: NWConnection) {
        let firstLine = headerString.split(separator: "\r\n", maxSplits: 1).first ?? ""
        let parts = firstLine.split(separator: " ")
        let method = parts.count > 0 ? String(parts[0]) : "GET"
        let fullTarget = parts.count > 1 ? String(parts[1]) : "/"
        let cleanPath = URLComponents(string: "http://127.0.0.1\(fullTarget)")?.path ?? fullTarget

        lock.lock()
        recordedRequests.append((method, cleanPath))
        lock.unlock()

        let (status, body) = handler(method, fullTarget)
        var header = "HTTP/1.1 \(status) OK\r\n"
        header += "Content-Type: application/json; charset=utf-8\r\n"
        header += "Content-Length: \(body.count)\r\n"
        header += "Connection: close\r\n\r\n"

        var payload = Data(header.utf8)
        payload.append(body)
        connection.send(content: payload, completion: .contentProcessed { _ in
            connection.cancel()
        })
    }

    private static func parseContentLength(from headerString: String) -> Int {
        for line in headerString.split(separator: "\r\n") {
            let lowered = line.lowercased()
            if lowered.hasPrefix("content-length:") {
                return Int(lowered.dropFirst("content-length:".count).trimmingCharacters(in: .whitespaces)) ?? 0
            }
        }
        return 0
    }
}
