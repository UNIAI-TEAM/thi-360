import Foundation
import XCTest

final class PlayerUITestFixtureServer: @unchecked Sendable {
    private struct IndexEntry: Decodable {
        let file: String
        let operationId: String
        let status: Int
        let variant: String?
    }

    let httpServer: ContractFixtureHTTPServer
    let attemptId: String
    let firstItemId: String
    let secondItemId: String
    let firstPartId: String

    private let lock = NSLock()
    private var stateObj: [String: Any]
    private var activeSessionId: String?
    private var saveAnswersStatusCode: Int = 200
    private var saveAnswersDelaySeconds: TimeInterval = 0
    private var submitDelaySeconds: TimeInterval = 0
    private var heartbeatCount: Int = 0
    private var getStateCount: Int = 0
    private var saveAnswersCount: Int = 0
    private var submitCount: Int = 0
    private var claimSessionCount: Int = 0
    private let deadlineDate: Date

    private static let isoFormatter: ISO8601DateFormatter = {
        let fmt = ISO8601DateFormatter()
        fmt.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return fmt
    }()

    init(
        attemptId: String = "00000000-0000-7000-8000-000000000077",
        variant: String = "in-progress-shared",
        durationSeconds: TimeInterval = 1800,
        mutateState: ((inout [String: Any]) -> Void)? = nil
    ) throws {
        let server = try ContractFixtureHTTPServer()
        try server.registerStandardAuthFixtures()
        self.httpServer = server
        self.attemptId = attemptId
        self.deadlineDate = Date().addingTimeInterval(durationSeconds)

        var state = try Self.loadIndexedJSON(operationId: "getState", status: 200, variant: variant)
        state["attemptId"] = attemptId
        state["serverNow"] = Self.isoFormatter.string(from: Date())
        if state["state"] as? String == "IN_PROGRESS" {
            let dl = Self.isoFormatter.string(from: deadlineDate)
            if state["deadlineAt"] != nil && !(state["deadlineAt"] is NSNull) {
                state["deadlineAt"] = dl
            }
            if var parts = state["parts"] as? [[String: Any]] {
                for i in parts.indices where parts[i]["state"] as? String == "OPEN" {
                    if parts[i]["deadlineAt"] != nil && !(parts[i]["deadlineAt"] is NSNull) {
                        parts[i]["deadlineAt"] = dl
                    }
                }
                state["parts"] = parts
            }
        }
        mutateState?(&state)
        self.stateObj = state

        let parts = (state["parts"] as? [[String: Any]]) ?? []
        self.firstPartId = (parts.first?["partId"] as? String) ?? ""
        let items = (parts.first?["items"] as? [[String: Any]]) ?? []
        let leaves = items.filter { ($0["questionType"] as? String) != "QUESTION_SET" }
        self.firstItemId = (leaves.first?["itemId"] as? String) ?? ""
        self.secondItemId = (leaves.dropFirst().first?["itemId"] as? String) ?? firstItemId

        try registerRoutes()
        try server.start()
    }

    var baseURL: String {
        "http://127.0.0.1:\(httpServer.port)"
    }

    var counts: (getState: Int, claim: Int, heartbeat: Int, save: Int, submit: Int) {
        lock.lock()
        defer { lock.unlock() }
        return (getStateCount, claimSessionCount, heartbeatCount, saveAnswersCount, submitCount)
    }

    func setSaveAnswersStatusCode(_ status: Int) {
        lock.lock()
        saveAnswersStatusCode = status
        lock.unlock()
    }

    func setSaveAnswersDelay(_ seconds: TimeInterval) {
        lock.lock()
        saveAnswersDelaySeconds = seconds
        lock.unlock()
    }

    func setSubmitDelay(_ seconds: TimeInterval) {
        lock.lock()
        submitDelaySeconds = seconds
        lock.unlock()
    }

    func transitionToClosedTimeUp() throws {
        var closed = try Self.loadIndexedJSON(operationId: "getState", status: 200, variant: "closed-time-up")
        closed["attemptId"] = attemptId
        closed["serverNow"] = Self.isoFormatter.string(from: Date())
        lock.lock()
        stateObj = closed
        lock.unlock()
    }

    func stop() {
        httpServer.stop()
    }

    private func registerRoutes() throws {
        let claimTemplate = try Self.loadIndexedJSON(operationId: "claimSession", status: 200)
        let conflictTemplate = try Self.loadIndexedJSON(operationId: "claimSession", status: 409, variant: "attempt-open-elsewhere")
        let hbTemplate = try Self.loadIndexedJSON(operationId: "heartbeat", status: 200)
        let saveTemplate = try Self.loadIndexedJSON(operationId: "saveAnswers", status: 200, variant: "saved")
        let submitTemplate = try Self.loadIndexedJSON(operationId: "submit", status: 200)
        var myExamsObj = try Self.loadIndexedJSON(operationId: "listMyExams", status: 200, variant: "")
        if var cards = myExamsObj["items"] as? [[String: Any]], !cards.isEmpty {
            cards[0]["state"] = "IN_PROGRESS"
            cards[0]["activeAttemptId"] = attemptId
            cards[0]["activeAttemptState"] = "IN_PROGRESS"
            cards[0]["actions"] = ["CONTINUE"]
            myExamsObj["items"] = cards
        }
        let myExamsData = try JSONSerialization.data(withJSONObject: myExamsObj)
        httpServer.register(method: "GET", path: "/api/v1/me/exams", statusCode: 200, body: myExamsData)

        let filtersData = try Self.loadIndexedData(operationId: "getStorefrontFilters", status: 200)
        let productsData = try Self.loadIndexedData(operationId: "listStorefrontProducts", status: 200, variant: "")
        let accessData = try Self.loadIndexedData(operationId: "queryMyExamAccess", status: 200)
        httpServer.register(method: "GET", path: "/api/v1/public/storefront/filters", statusCode: 200, body: filtersData)
        httpServer.register(method: "GET", path: "/api/v1/public/storefront/products", statusCode: 200, body: productsData)
        httpServer.register(method: "POST", path: "/api/v1/me/exam-access/query", statusCode: 200, body: accessData)

        httpServer.register(method: "GET", path: "/api/v1/me/attempts/\(attemptId)") { [weak self] _, _, _ in
            guard let self else { return ContractHTTPResponse(statusCode: 500) }
            let data: Data = self.lock.withLock {
                self.getStateCount += 1
                var copy = self.stateObj
                copy["serverNow"] = Self.isoFormatter.string(from: Date())
                return (try? JSONSerialization.data(withJSONObject: copy)) ?? Data()
            }
            return ContractHTTPResponse(statusCode: 200, body: data)
        }

        httpServer.register(method: "POST", path: "/api/v1/me/attempts/\(attemptId)/session") { [weak self] _, _, body in
            guard let self else { return ContractHTTPResponse(statusCode: 500) }
            let req = (try? JSONSerialization.jsonObject(with: body) as? [String: Any]) ?? [:]
            let reqSessionId = req["sessionId"] as? String
            let takeover = (req["takeover"] as? Bool) ?? false

            return self.lock.withLock {
                self.claimSessionCount += 1
                let nowIso = Self.isoFormatter.string(from: Date())
                if let existing = self.activeSessionId, !takeover, existing != reqSessionId {
                    var conflict = conflictTemplate
                    conflict["lastSeenAt"] = nowIso
                    let data = (try? JSONSerialization.data(withJSONObject: conflict)) ?? Data()
                    return ContractHTTPResponse(statusCode: 409, body: data)
                }
                let granted = reqSessionId ?? UUID().uuidString.lowercased()
                self.activeSessionId = granted
                var resp = claimTemplate
                resp["sessionId"] = granted
                resp["serverNow"] = nowIso
                let data = (try? JSONSerialization.data(withJSONObject: resp)) ?? Data()
                return ContractHTTPResponse(statusCode: 200, body: data)
            }
        }

        httpServer.register(method: "POST", path: "/api/v1/me/attempts/\(attemptId)/heartbeat") { [weak self] _, _, _ in
            guard let self else { return ContractHTTPResponse(statusCode: 500) }
            let data: Data = self.lock.withLock {
                self.heartbeatCount += 1
                var resp = hbTemplate
                resp["serverNow"] = Self.isoFormatter.string(from: Date())
                resp["state"] = (self.stateObj["state"] as? String) ?? "IN_PROGRESS"
                return (try? JSONSerialization.data(withJSONObject: resp)) ?? Data()
            }
            return ContractHTTPResponse(statusCode: 200, body: data)
        }

        httpServer.register(method: "PUT", path: "/api/v1/me/attempts/\(attemptId)/answers") { [weak self] _, _, body in
            guard let self else { return ContractHTTPResponse(statusCode: 500) }
            let (status, delay): (Int, TimeInterval) = self.lock.withLock {
                self.saveAnswersCount += 1
                return (self.saveAnswersStatusCode, self.saveAnswersDelaySeconds)
            }
            if delay > 0 {
                Thread.sleep(forTimeInterval: delay)
            }
            if status != 200 {
                let err = Data(#"{"code":"SERVICE_UNAVAILABLE","message":"Busy","fieldErrors":[],"traceId":"ui-503"}"#.utf8)
                return ContractHTTPResponse(statusCode: status, body: err)
            }
            let req = (try? JSONSerialization.jsonObject(with: body) as? [String: Any]) ?? [:]
            var results: [[String: Any]] = []
            if let changes = req["changes"] as? [[String: Any]] {
                for c in changes {
                    if let itemId = c["itemId"] as? String, let seq = c["seq"] as? NSNumber {
                        results.append(["itemId": itemId, "seq": seq.int64Value, "status": "SAVED", "reason": NSNull()])
                    }
                }
            }
            let data: Data = self.lock.withLock {
                if let pos = req["position"] as? [String: Any] {
                    self.stateObj["lastPosition"] = pos
                }
                var resp = saveTemplate
                resp["results"] = results
                resp["serverNow"] = Self.isoFormatter.string(from: Date())
                return (try? JSONSerialization.data(withJSONObject: resp)) ?? Data()
            }
            return ContractHTTPResponse(statusCode: 200, body: data)
        }

        httpServer.register(method: "POST", path: "/api/v1/me/attempts/\(attemptId)/submit") { [weak self] _, _, _ in
            guard let self else { return ContractHTTPResponse(statusCode: 500) }
            let delay: TimeInterval = self.lock.withLock {
                self.submitCount += 1
                return self.submitDelaySeconds
            }
            if delay > 0 {
                Thread.sleep(forTimeInterval: delay)
            }
            let data: Data = self.lock.withLock {
                self.stateObj["state"] = "GRADING"
                var resp = submitTemplate
                resp["attemptId"] = self.attemptId
                resp["serverNow"] = Self.isoFormatter.string(from: Date())
                resp["results"] = []
                return (try? JSONSerialization.data(withJSONObject: resp)) ?? Data()
            }
            return ContractHTTPResponse(statusCode: 200, body: data)
        }
    }

    static func loadIndexedData(operationId: String, status: Int, variant: String? = nil) throws -> Data {
        let indexData = try ContractFixtureHTTPServer.loadFixture("api/index.json")
        let entries = try JSONDecoder().decode([IndexEntry].self, from: indexData)
        let byOp = entries.filter { $0.operationId == operationId && $0.status == status }
        let matched: IndexEntry?
        if let variant {
            matched = byOp.first { ($0.variant ?? "") == variant }
        } else {
            matched = byOp.first { ($0.variant ?? "").isEmpty } ?? byOp.first
        }
        guard let matched else {
            throw NSError(domain: "PlayerUITestFixtureServer", code: 404)
        }
        let rel = matched.file.contains("/") ? matched.file : "api/\(matched.file)"
        return try ContractFixtureHTTPServer.loadFixture(rel)
    }

    static func loadIndexedJSON(operationId: String, status: Int, variant: String? = nil) throws -> [String: Any] {
        let data = try loadIndexedData(operationId: operationId, status: status, variant: variant)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
    }
}
