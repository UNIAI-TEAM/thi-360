import Foundation
import Network
import XCTest

final class CatalogFlowUITests: XCTestCase {
    private var server: CatalogQueryFixtureHTTPServer?

    override func setUpWithError() throws {
        continueAfterFailure = false

        let repoRoot = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
        let contractsMobileDir = repoRoot.appendingPathComponent("contracts/mobile", isDirectory: true)
        let filtersData = try Self.loadIndexedFixture(
            contractsMobileDir: contractsMobileDir,
            operationId: "getStorefrontFilters",
            status: 200
        )
        let listBaseData = try Self.loadIndexedFixture(
            contractsMobileDir: contractsMobileDir,
            operationId: "listStorefrontProducts",
            status: 200,
            variant: ""
        )
        let examDetailBaseData = try Self.loadIndexedFixture(
            contractsMobileDir: contractsMobileDir,
            operationId: "getStorefrontProduct",
            status: 200,
            variant: "exam"
        )
        let notFoundData = try Self.loadIndexedFixture(
            contractsMobileDir: contractsMobileDir,
            operationId: "getStorefrontProduct",
            status: 404,
            variant: "not-found"
        )
        let accessBaseData = try Self.loadIndexedFixture(
            contractsMobileDir: contractsMobileDir,
            operationId: "queryMyExamAccess",
            status: 200
        )
        let loginData = try Self.loadIndexedFixture(
            contractsMobileDir: contractsMobileDir,
            operationId: "mobileLogin",
            status: 200,
            variant: ""
        )
        let refreshData = try Self.loadIndexedFixture(
            contractsMobileDir: contractsMobileDir,
            operationId: "mobileRefresh",
            status: 200
        )
        let profileData = try Self.loadIndexedFixture(
            contractsMobileDir: contractsMobileDir,
            operationId: "getMyProfile",
            status: 200,
            variant: ""
        )
        let meData = try Self.loadIndexedFixture(
            contractsMobileDir: contractsMobileDir,
            operationId: "me",
            status: 200
        )

        let listRoot = try XCTUnwrap(JSONSerialization.jsonObject(with: listBaseData) as? [String: Any])
        let templateItem = try XCTUnwrap((listRoot["items"] as? [[String: Any]])?.first)
        let detailTemplate = try XCTUnwrap(JSONSerialization.jsonObject(with: examDetailBaseData) as? [String: Any])

        var allItems: [[String: Any]] = []
        for idx in 1...24 {
            var copy = templateItem
            let code = String(format: "HSA-UI-%02d", idx)
            copy["code"] = code
            copy["name"] = "Đề thi HSA số \(idx)"
            allItems.append(copy)
        }

        let page0Data = try JSONSerialization.data(withJSONObject: [
            "items": Array(allItems[0..<12]),
            "page": 0,
            "size": 12,
            "totalItems": 24,
            "totalPages": 2
        ])
        let page1Data = try JSONSerialization.data(withJSONObject: [
            "items": Array(allItems[12..<24]),
            "page": 1,
            "size": 12,
            "totalItems": 24,
            "totalPages": 2
        ])

        var detail20 = detailTemplate
        detail20["code"] = "HSA-UI-20"
        detail20["name"] = "Đề thi HSA số 20"
        let detail20Data = try JSONSerialization.data(withJSONObject: detail20)

        let fixtureServer = try CatalogQueryFixtureHTTPServer { method, fullTarget in
            let components = URLComponents(string: "http://127.0.0.1\(fullTarget)")
            let path = components?.path ?? fullTarget
            let query = Dictionary(
                uniqueKeysWithValues: (components?.queryItems ?? []).map { ($0.name, $0.value ?? "") }
            )

            if method == "POST", path == "/api/v1/auth/mobile/login" { return (200, loginData) }
            if method == "POST", path == "/api/v1/auth/mobile/refresh" { return (200, refreshData) }
            if method == "GET", path == "/api/v1/me" { return (200, meData) }
            if method == "GET", path == "/api/v1/me/profile" { return (200, profileData) }
            if method == "GET", path == "/api/v1/public/storefront/filters" { return (200, filtersData) }
            if method == "GET", path == "/api/v1/public/storefront/products" {
                let page = Int(query["page"] ?? "0") ?? 0
                return (200, page == 0 ? page0Data : page1Data)
            }
            if method == "GET", path == "/api/v1/public/storefront/products/HSA-UI-20" {
                return (200, detail20Data)
            }
            if method == "GET", path.hasPrefix("/api/v1/public/storefront/products/") {
                return (200, examDetailBaseData)
            }
            if method == "POST", path == "/api/v1/me/exam-access/query" {
                return (200, accessBaseData)
            }
            return (404, notFoundData)
        }
        try fixtureServer.start()
        self.server = fixtureServer
    }

    override func tearDownWithError() throws {
        XCUIDevice.shared.orientation = .portrait
        server?.stop()
        server = nil
    }

    func testFilterAndScrollSurviveDetailTabSwitchAndRotation() throws {
        let server = try XCTUnwrap(server)
        let baseURL = "http://127.0.0.1:\(server.port)"
        let suite = "vn.unicom.thi360.uitest.catalogflow.\(UUID().uuidString)"

        XCUIDevice.shared.orientation = .portrait

        let app = XCUIApplication()
        app.launchArguments = [
            "-uitest-defaults-suite", suite,
            "-uitest-reset-preferences",
            "-uitest-authenticated",
            "-uitest-api-base-url", baseURL
        ]
        app.launch()

        let heading = app.staticTexts["catalog.heading"].firstMatch
        XCTAssertTrue(heading.waitForExistence(timeout: 10), "Catalog heading must appear on Home")

        let hsaChip = app.buttons["catalog.kindChip.HSA"].firstMatch
        XCTAssertTrue(hsaChip.waitForExistence(timeout: 8), "HSA kind chip must exist")
        hsaChip.tap()
        let card01Title = app.buttons["catalog.card.title.HSA-UI-01"].firstMatch
        XCTAssertTrue(
            card01Title.waitForExistence(timeout: 8),
            "First filtered HSA card must load"
        )

        // Drag directly along the horizontal center of the visible catalog card column (avoiding the iPad sidebar).
        let card01FrameBefore = card01Title.frame
        let windowFrame = app.windows.firstMatch.frame
        let listCenterDX = max(0.1, min(0.9, card01FrameBefore.midX / max(1, windowFrame.width)))
        let dragStart = app.windows.firstMatch.coordinate(withNormalizedOffset: CGVector(dx: listCenterDX, dy: 0.82))
        let dragEnd = app.windows.firstMatch.coordinate(withNormalizedOffset: CGVector(dx: listCenterDX, dy: 0.15))
        let dragDownStart = app.windows.firstMatch.coordinate(withNormalizedOffset: CGVector(dx: listCenterDX, dy: 0.25))
        let dragDownEnd = app.windows.firstMatch.coordinate(withNormalizedOffset: CGVector(dx: listCenterDX, dy: 0.78))

        let card20Title = app.buttons["catalog.card.title.HSA-UI-20"].firstMatch
        let card22Title = app.buttons["catalog.card.title.HSA-UI-22"].firstMatch
        var swipes = 0
        while (!card20Title.exists || !card20Title.isHittable) && swipes < 24 {
            if card22Title.exists && !card20Title.exists {
                dragDownStart.press(forDuration: 0.02, thenDragTo: dragDownEnd)
            } else if card20Title.exists {
                dragStart.press(forDuration: 0.02, thenDragTo: app.windows.firstMatch.coordinate(withNormalizedOffset: CGVector(dx: listCenterDX, dy: 0.5)))
            } else {
                dragStart.press(forDuration: 0.02, thenDragTo: dragEnd, withVelocity: .fast, thenHoldForDuration: 0)
            }
            swipes += 1
        }
        let showingText = app.staticTexts["catalog.showingLine"].firstMatch.exists
            ? app.staticTexts["catalog.showingLine"].firstMatch.label
            : "(none)"
        XCTAssertTrue(
            card20Title.exists,
            "Card 20 (HSA-UI-20) must be loaded after scrolling page 2 (listCenterDX=\(listCenterDX), card01Before=\(card01FrameBefore), showing=\(showingText), productCalls=\(server.productCallCount), targets=\(server.allTargets))"
        )

        let productCallsBeforeNavigation = server.productCallCount

        card20Title.tap()
        let detailCode = app.staticTexts["productDetail.code"].firstMatch
        XCTAssertTrue(detailCode.waitForExistence(timeout: 8), "Product detail for HSA-UI-20 must open")
        XCTAssertTrue(detailCode.label.contains("HSA-UI-20"))

        let inlineBack = app.buttons["productDetail.backButton"].firstMatch
        if inlineBack.exists && inlineBack.isHittable {
            inlineBack.tap()
        }

        openDestination(titled: "Lịch sử", identifier: "shell.dest.history", in: app)
        XCTAssertTrue(app.staticTexts["Lịch sử"].firstMatch.waitForExistence(timeout: 5))
        openDestination(titled: "Trang chủ", identifier: "shell.dest.home", in: app)

        XCUIDevice.shared.orientation = .landscapeLeft
        RunLoop.current.run(until: Date().addingTimeInterval(0.6))
        XCUIDevice.shared.orientation = .portrait
        RunLoop.current.run(until: Date().addingTimeInterval(0.6))

        XCTAssertEqual(
            server.productCallCount,
            productCallsBeforeNavigation,
            "Opening detail, switching tabs, and rotating must NOT trigger new /products requests"
        )
        XCTAssertTrue(
            app.buttons["catalog.card.title.HSA-UI-20"].firstMatch.waitForExistence(timeout: 5),
            "Card 20 must remain at scroll position after detail, tab switch, and rotation"
        )
        let hsaChipAfter = app.buttons["catalog.kindChip.HSA"].firstMatch
        if hsaChipAfter.exists {
            XCTAssertTrue(hsaChipAfter.isSelected, "HSA kind chip must remain selected")
        }
    }

    private struct IndexedFixtureEntry: Decodable {
        let file: String
        let operationId: String
        let status: Int
        let variant: String?
    }

    private static func loadIndexedFixture(
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
                domain: "CatalogFlowUITests",
                code: 1,
                userInfo: [NSLocalizedDescriptionKey: "Missing indexed fixture for \(operationId) status=\(status) variant=\(variant ?? "*")"]
            )
        }
        let relative = matched.file.contains("/") ? matched.file : "api/\(matched.file)"
        return try Data(contentsOf: contractsMobileDir.appendingPathComponent(relative))
    }

    private func openDestination(titled title: String, identifier: String, in app: XCUIApplication) {
        let tabButton = app.tabBars.buttons[title].firstMatch
        if tabButton.waitForExistence(timeout: 2) {
            tabButton.tap()
            return
        }
        let button = app.buttons[identifier].firstMatch
        XCTAssertTrue(button.waitForExistence(timeout: 5), "Destination \(identifier) not found")
        button.tap()
    }
}

private final class CatalogQueryFixtureHTTPServer: @unchecked Sendable {
    typealias Handler = @Sendable (String, String) -> (Int, Data)

    private let listener: NWListener
    private let queue = DispatchQueue(label: "vn.unicom.thi360.uitest.catalogquery")
    private let handler: Handler
    private let lock = NSLock()
    private var recordedTargets: [String] = []
    private(set) var port: UInt16 = 0

    var productCallCount: Int {
        lock.lock()
        defer { lock.unlock() }
        return recordedTargets.filter { $0.hasPrefix("GET /api/v1/public/storefront/products?") || $0 == "GET /api/v1/public/storefront/products" }.count
    }

    var allTargets: [String] {
        lock.lock()
        defer { lock.unlock() }
        return recordedTargets
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
            throw NSError(domain: "CatalogQueryFixtureHTTPServer", code: -1)
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

        lock.lock()
        recordedTargets.append("\(method) \(fullTarget)")
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
