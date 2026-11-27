import Foundation
import HTTPTypes
import OpenAPIRuntime
import XCTest
@testable import Thi360

struct AttemptFixtureEntry: Decodable, Sendable {
    let file: String
    let operationId: String
    let method: String
    let path: String
    let status: Int
    let variant: String?
}

enum AttemptFixtureCatalog {
    private static let cachedEntries: [AttemptFixtureEntry] = {
        let indexURL = ContractFixtureLoader.contractsMobileURL
            .appendingPathComponent("api/index.json")
        guard let data = try? Data(contentsOf: indexURL),
              let entries = try? JSONDecoder().decode([AttemptFixtureEntry].self, from: data)
        else {
            return []
        }
        return entries
    }()

    static func loadData(operationId: String, status: Int, variant: String = "") throws -> Data {
        guard let entry = cachedEntries.first(where: {
            $0.operationId == operationId
                && $0.status == status
                && ($0.variant ?? "") == variant
        }) else {
            throw NSError(
                domain: "AttemptFixtureCatalog",
                code: 404,
                userInfo: [
                    NSLocalizedDescriptionKey: "Fixture not found in index.json for operationId=\(operationId) status=\(status) variant='\(variant)'"
                ]
            )
        }
        return try ContractFixtureLoader.loadData(relativePath: entry.file)
    }

    static func loadJSONObject(operationId: String, status: Int, variant: String = "") throws -> [String: Any] {
        let data = try loadData(operationId: operationId, status: status, variant: variant)
        guard let obj = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw NSError(domain: "AttemptFixtureCatalog", code: 500, userInfo: nil)
        }
        return obj
    }

    static func loadRichTextAttempt50JSON() throws -> [String: Any] {
        let data = try ContractFixtureLoader.loadData(relativePath: "rich-text/attempt-50.json")
        guard let obj = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw NSError(domain: "AttemptFixtureCatalog", code: 500, userInfo: nil)
        }
        return obj
    }
}

final class FakeAttemptServer: @unchecked Sendable {
    enum Endpoint: String, Hashable, Sendable {
        case getState
        case claimSession
        case heartbeat
        case saveAnswers
        case finishPart
        case submit
        case confirmPartChoice
        case retryPartGeneration
    }

    struct OverrideResponse: @unchecked Sendable {
        let statusCode: Int
        let data: Data
        let headers: [String: String]
        let networkError: URLError.Code?
        let delay: Duration?
        let gate: AttemptTestGate?

        static func json(
            statusCode: Int,
            object: [String: Any],
            headers: [String: String] = [:],
            delay: Duration? = nil,
            gate: AttemptTestGate? = nil
        ) throws -> OverrideResponse {
            let data = try JSONSerialization.data(withJSONObject: object)
            return OverrideResponse(
                statusCode: statusCode,
                data: data,
                headers: headers,
                networkError: nil,
                delay: delay,
                gate: gate
            )
        }

        static func fixture(
            operationId: String,
            status: Int,
            variant: String = "",
            headers: [String: String] = [:],
            delay: Duration? = nil,
            gate: AttemptTestGate? = nil,
            mutate: ((inout [String: Any]) -> Void)? = nil
        ) throws -> OverrideResponse {
            var obj = try AttemptFixtureCatalog.loadJSONObject(
                operationId: operationId,
                status: status,
                variant: variant
            )
            mutate?(&obj)
            return try .json(statusCode: status, object: obj, headers: headers, delay: delay, gate: gate)
        }

        static func networkError(
            _ code: URLError.Code = .notConnectedToInternet,
            delay: Duration? = nil,
            gate: AttemptTestGate? = nil
        ) -> OverrideResponse {
            OverrideResponse(
                statusCode: 0,
                data: Data(),
                headers: [:],
                networkError: code,
                delay: delay,
                gate: gate
            )
        }

        static func serverError(
            statusCode: Int = 503,
            code: String = "SERVICE_UNAVAILABLE",
            delay: Duration? = nil,
            gate: AttemptTestGate? = nil
        ) throws -> OverrideResponse {
            let obj: [String: Any] = [
                "code": code,
                "message": "Server busy",
                "fieldErrors": [],
                "traceId": "fake-5xx"
            ]
            return try .json(statusCode: statusCode, object: obj, delay: delay, gate: gate)
        }
    }

    struct RecordedCall: Sendable {
        let endpoint: Endpoint
        let method: HTTPRequest.Method
        let path: String
        let headers: HTTPFields
        let bodyData: Data?
        let timestamp: Date

        func headerValue(_ name: String) -> String? {
            guard let fieldName = HTTPField.Name(name) else { return nil }
            return headers[fieldName]
        }

        var sessionHeader: String? {
            headerValue("X-Attempt-Session")
        }

        var idempotencyKeyHeader: String? {
            headerValue("Idempotency-Key")
        }

        var authorizationHeader: String? {
            headerValue("Authorization")
        }

        func decodedJSON() -> [String: Any]? {
            guard let bodyData else { return nil }
            return (try? JSONSerialization.jsonObject(with: bodyData)) as? [String: Any]
        }
    }

    let transport: ContractFakeTransport
    let attemptId: String

    private let lock = NSLock()
    private let clock: TestEngineClock?
    private let clockOriginInstant: ContinuousClock.Instant?
    private var serverNowBase: Date
    private var manualServerNowOverride: Date?

    private var stateJSON: [String: Any]
    private var activeSessionId: String?
    private var activeSessionLastSeenAt: Date?
    private var externalTakeoverApplied = false

    private var queuedOverrides: [Endpoint: [OverrideResponse]] = [:]
    private var persistentDelay: [Endpoint: Duration] = [:]
    private var persistentGate: [Endpoint: AttemptTestGate] = [:]
    private var inFlightCountByEndpoint: [Endpoint: Int] = [:]
    private var maxConcurrentByEndpoint: [Endpoint: Int] = [:]
    private var recordedCalls: [RecordedCall] = []

    var onBeforeHandle: (@Sendable (RecordedCall) -> Void)?

    init(
        attemptId: String? = nil,
        initialStateVariant: String = "in-progress-shared",
        useRichTextAttempt50: Bool = false,
        initialServerNow: Date = FlexibleISO8601DateTranscoder.parse("2026-11-20T10:00:00Z")!,
        clock: TestEngineClock? = nil,
        mutateInitialState: ((inout [String: Any]) -> Void)? = nil
    ) throws {
        var json: [String: Any]
        if useRichTextAttempt50 {
            json = try AttemptFixtureCatalog.loadRichTextAttempt50JSON()
        } else {
            json = try AttemptFixtureCatalog.loadJSONObject(
                operationId: Operations.getState.id,
                status: 200,
                variant: initialStateVariant
            )
        }

        let resolvedAttemptId = attemptId ?? (json["attemptId"] as? String) ?? "00000000-0000-7000-8000-000000000001"
        json["attemptId"] = resolvedAttemptId
        let isoNow = Self.isoString(initialServerNow)
        json["serverNow"] = isoNow

        if json["state"] as? String == "IN_PROGRESS" {
            let defaultDeadline = Self.isoString(initialServerNow.addingTimeInterval(1800))
            if json["deadlineAt"] != nil && !(json["deadlineAt"] is NSNull) {
                json["deadlineAt"] = defaultDeadline
            }
            if var parts = json["parts"] as? [[String: Any]] {
                for idx in parts.indices {
                    if parts[idx]["state"] as? String == "OPEN",
                       parts[idx]["deadlineAt"] != nil && !(parts[idx]["deadlineAt"] is NSNull)
                    {
                        parts[idx]["deadlineAt"] = defaultDeadline
                    }
                    if parts[idx]["state"] as? String == "AWAITING_CHOICE",
                       parts[idx]["choiceDeadlineAt"] != nil && !(parts[idx]["choiceDeadlineAt"] is NSNull)
                    {
                        parts[idx]["choiceDeadlineAt"] = Self.isoString(initialServerNow.addingTimeInterval(900))
                    }
                }
                json["parts"] = parts
            }
        }
        mutateInitialState?(&json)

        self.attemptId = resolvedAttemptId
        self.stateJSON = json
        self.serverNowBase = initialServerNow
        self.clock = clock
        self.clockOriginInstant = clock?.now
        self.transport = ContractFakeTransport()

        self.transport.setFallback { [weak self] request in
            guard let self else {
                return ContractFakeTransport.emptyResponse(statusCode: 500)
            }
            return try await self.handle(request)
        }
    }

    var currentServerNow: Date {
        lock.lock()
        defer { lock.unlock() }
        return computeServerNowLocked()
    }

    func setServerNow(_ date: Date) {
        lock.lock()
        manualServerNowOverride = date
        serverNowBase = date
        stateJSON["serverNow"] = Self.isoString(date)
        lock.unlock()
    }

    func setState(variant: String, mutate: ((inout [String: Any]) -> Void)? = nil) throws {
        var json = try AttemptFixtureCatalog.loadJSONObject(
            operationId: Operations.getState.id,
            status: 200,
            variant: variant
        )
        json["attemptId"] = attemptId
        json["serverNow"] = Self.isoString(currentServerNow)
        mutate?(&json)
        lock.lock()
        stateJSON = json
        lock.unlock()
    }

    func mutateState(_ transform: (inout [String: Any]) -> Void) {
        lock.lock()
        transform(&stateJSON)
        lock.unlock()
    }

    func currentStateJSON() -> [String: Any] {
        lock.lock()
        defer { lock.unlock() }
        return stateJSON
    }

    func setActiveSession(_ sessionId: String?, lastSeenAt: Date? = nil) {
        lock.lock()
        activeSessionId = sessionId
        activeSessionLastSeenAt = lastSeenAt ?? computeServerNowLocked()
        externalTakeoverApplied = false
        lock.unlock()
    }

    func simulateExternalTakeover(newSessionId: String = "external-session-9999", lastSeenAt: Date? = nil) {
        lock.lock()
        activeSessionId = newSessionId
        activeSessionLastSeenAt = lastSeenAt ?? computeServerNowLocked()
        externalTakeoverApplied = true
        lock.unlock()
    }

    func enqueue(_ endpoint: Endpoint, responses: [OverrideResponse]) {
        lock.lock()
        queuedOverrides[endpoint, default: []].append(contentsOf: responses)
        lock.unlock()
    }

    func enqueue(_ endpoint: Endpoint, _ response: OverrideResponse) {
        enqueue(endpoint, responses: [response])
    }

    func setDelay(_ delay: Duration?, for endpoint: Endpoint) {
        lock.lock()
        persistentDelay[endpoint] = delay
        lock.unlock()
    }

    func setGate(_ gate: AttemptTestGate?, for endpoint: Endpoint) {
        lock.lock()
        persistentGate[endpoint] = gate
        lock.unlock()
    }

    var allCalls: [RecordedCall] {
        lock.lock()
        defer { lock.unlock() }
        return recordedCalls
    }

    func calls(for endpoint: Endpoint) -> [RecordedCall] {
        allCalls.filter { $0.endpoint == endpoint }
    }

    func callCount(for endpoint: Endpoint) -> Int {
        calls(for: endpoint).count
    }

    func maxConcurrency(for endpoint: Endpoint) -> Int {
        lock.lock()
        defer { lock.unlock() }
        return maxConcurrentByEndpoint[endpoint] ?? 0
    }

    func resetCalls() {
        lock.lock()
        recordedCalls.removeAll()
        maxConcurrentByEndpoint.removeAll()
        lock.unlock()
    }

    func firstLeafItemId(inPartIndex partIndex: Int = 0) -> String {
        leafItemIds(inPartIndex: partIndex).first ?? ""
    }

    func leafItemIds(inPartIndex partIndex: Int = 0) -> [String] {
        lock.lock()
        defer { lock.unlock() }
        guard let parts = stateJSON["parts"] as? [[String: Any]],
              partIndex < parts.count,
              let items = parts[partIndex]["items"] as? [[String: Any]]
        else {
            return []
        }
        return items.compactMap { item in
            guard (item["questionType"] as? String) != "QUESTION_SET" else { return nil }
            return item["itemId"] as? String
        }
    }

    func partId(at index: Int = 0) -> String {
        lock.lock()
        defer { lock.unlock() }
        guard let parts = stateJSON["parts"] as? [[String: Any]],
              index < parts.count
        else {
            return ""
        }
        return parts[index]["partId"] as? String ?? ""
    }

    func makeAttemptAPI(
        tokenStore: any TokenStore = InMemoryTokenStore(
            initialTokens: SessionTokens(
                accessToken: "attempt-test-access-token",
                refreshToken: "attempt-test-refresh-token",
                accessExpiresIn: 300,
                refreshExpiresIn: 604_800,
                mustChangePassword: false,
                username: "student.test",
                accountId: "account-test-1"
            )
        ),
        refresher: TokenRefresher? = nil,
        environment: BuildEnvironment = .dev,
        logSink: InMemoryLogSink = InMemoryLogSink(),
        locale: AppLocale = .vi
    ) -> AttemptAPI {
        let logger = RedactingAppLogger(environment: environment, sink: logSink)
        let resolvedRefresher: TokenRefresher
        if let refresher {
            resolvedRefresher = refresher
        } else {
            let apiClient = MobileAPIClient(
                serverURL: URL(string: "https://api.thi360.test")!,
                tokenStore: tokenStore,
                transport: transport,
                logger: logger,
                localeProvider: { locale }
            )
            resolvedRefresher = apiClient.refresher
        }
        return AttemptAPI(
            serverURL: URL(string: "https://api.thi360.test")!,
            tokenStore: tokenStore,
            refresher: resolvedRefresher,
            transport: transport,
            logger: logger,
            localeProvider: { locale }
        )
    }

    static func isoString(_ date: Date) -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter.string(from: date)
    }

    private func computeServerNowLocked() -> Date {
        if let manual = manualServerNowOverride {
            return manual
        }
        if let clock, let origin = clockOriginInstant {
            let elapsed = max(.zero, clock.now - origin)
            return serverNowBase.addingTimeInterval(ServerClock.timeInterval(from: elapsed))
        }
        return serverNowBase
    }

    private func classifyEndpoint(method: HTTPRequest.Method, path: String) -> Endpoint? {
        let clean = ContractFakeTransport.RouteKey.normalize(path: path)
        if method == .get && clean.hasSuffix("/attempts/\(attemptId)") {
            return .getState
        }
        if method == .post && clean.hasSuffix("/attempts/\(attemptId)/session") {
            return .claimSession
        }
        if method == .post && clean.hasSuffix("/attempts/\(attemptId)/heartbeat") {
            return .heartbeat
        }
        if method == .put && clean.hasSuffix("/attempts/\(attemptId)/answers") {
            return .saveAnswers
        }
        if method == .post && clean.contains("/attempts/\(attemptId)/parts/") && clean.hasSuffix("/finish") {
            return .finishPart
        }
        if method == .post && clean.hasSuffix("/attempts/\(attemptId)/submit") {
            return .submit
        }
        if method == .post && clean.contains("/attempts/\(attemptId)/parts/") && clean.hasSuffix("/choice") {
            return .confirmPartChoice
        }
        if method == .post && clean.contains("/attempts/\(attemptId)/parts/") && clean.hasSuffix("/retry") {
            return .retryPartGeneration
        }
        return nil
    }

    private func handle(_ request: RecordedHTTPRequest) async throws -> (HTTPResponse, HTTPBody?) {
        guard let endpoint = classifyEndpoint(method: request.method, path: request.path) else {
            let errPayload = #"{"code":"UNMOCKED_ROUTE","message":"Unexpected route \#(request.path)","fieldErrors":[],"traceId":"fake-attempt"}"#
            return ContractFakeTransport.jsonResponse(statusCode: 404, data: Data(errPayload.utf8))
        }

        let (override, delay, gate, callRecord) = beginCall(endpoint: endpoint, request: request)
        defer { endCall(endpoint: endpoint) }

        onBeforeHandle?(callRecord)

        if let effectiveGate = override?.gate ?? gate {
            await effectiveGate.enterAndWait()
        }
        if let effectiveDelay = override?.delay ?? delay {
            if let clock {
                try await clock.sleep(for: effectiveDelay)
            } else {
                try await Task.sleep(for: effectiveDelay)
            }
        }

        if let override {
            if let netErr = override.networkError {
                throw URLError(netErr)
            }
            return ContractFakeTransport.jsonResponse(
                statusCode: override.statusCode,
                data: override.data,
                headers: override.headers
            )
        }

        return try handleDefault(endpoint: endpoint, call: callRecord)
    }

    private func beginCall(
        endpoint: Endpoint,
        request: RecordedHTTPRequest
    ) -> (OverrideResponse?, Duration?, AttemptTestGate?, RecordedCall) {
        lock.lock()
        defer { lock.unlock() }

        let now = computeServerNowLocked()
        let call = RecordedCall(
            endpoint: endpoint,
            method: request.method,
            path: request.path,
            headers: request.headers,
            bodyData: request.bodyData,
            timestamp: now
        )
        recordedCalls.append(call)

        let nextInFlight = (inFlightCountByEndpoint[endpoint] ?? 0) + 1
        inFlightCountByEndpoint[endpoint] = nextInFlight
        maxConcurrentByEndpoint[endpoint] = max(maxConcurrentByEndpoint[endpoint] ?? 0, nextInFlight)

        var override: OverrideResponse?
        if var queue = queuedOverrides[endpoint], !queue.isEmpty {
            override = queue.removeFirst()
            queuedOverrides[endpoint] = queue
        }
        let delay = persistentDelay[endpoint]
        let gate = persistentGate[endpoint]
        return (override, delay, gate, call)
    }

    private func endCall(endpoint: Endpoint) {
        lock.lock()
        defer { lock.unlock() }
        inFlightCountByEndpoint[endpoint] = max(0, (inFlightCountByEndpoint[endpoint] ?? 1) - 1)
    }

    private func handleDefault(endpoint: Endpoint, call: RecordedCall) throws -> (HTTPResponse, HTTPBody?) {
        lock.lock()
        defer { lock.unlock() }

        let now = computeServerNowLocked()
        let nowIso = Self.isoString(now)

        switch endpoint {
        case .getState:
            var copy = stateJSON
            copy["serverNow"] = nowIso
            let data = try JSONSerialization.data(withJSONObject: copy)
            return ContractFakeTransport.jsonResponse(statusCode: 200, data: data)

        case .claimSession:
            let attemptState = stateJSON["state"] as? String ?? "IN_PROGRESS"
            if attemptState != "IN_PROGRESS" {
                let err = try AttemptFixtureCatalog.loadData(
                    operationId: Operations.claimSession.id,
                    status: 409,
                    variant: "attempt-not-open"
                )
                return ContractFakeTransport.jsonResponse(statusCode: 409, data: err)
            }

            let body = call.decodedJSON() ?? [:]
            let reqSessionId = body["sessionId"] as? String
            let takeover = (body["takeover"] as? Bool) ?? false

            if let existing = activeSessionId,
               !takeover,
               existing != reqSessionId
            {
                var conflict = try AttemptFixtureCatalog.loadJSONObject(
                    operationId: Operations.claimSession.id,
                    status: 409,
                    variant: "attempt-open-elsewhere"
                )
                if let seen = activeSessionLastSeenAt {
                    conflict["lastSeenAt"] = Self.isoString(seen)
                }
                let data = try JSONSerialization.data(withJSONObject: conflict)
                return ContractFakeTransport.jsonResponse(statusCode: 409, data: data)
            }

            let grantedSessionId: String
            if takeover {
                grantedSessionId = UUID().uuidString.lowercased()
            } else {
                grantedSessionId = reqSessionId ?? UUID().uuidString.lowercased()
            }
            activeSessionId = grantedSessionId
            activeSessionLastSeenAt = now
            externalTakeoverApplied = false

            var resp = try AttemptFixtureCatalog.loadJSONObject(
                operationId: Operations.claimSession.id,
                status: 200,
                variant: ""
            )
            resp["sessionId"] = grantedSessionId
            resp["serverNow"] = nowIso
            let data = try JSONSerialization.data(withJSONObject: resp)
            return ContractFakeTransport.jsonResponse(statusCode: 200, data: data)

        case .heartbeat:
            if let revoked = checkSessionLocked(call: call, operationId: Operations.heartbeat.id) {
                return revoked
            }
            activeSessionLastSeenAt = now
            var resp = try AttemptFixtureCatalog.loadJSONObject(
                operationId: Operations.heartbeat.id,
                status: 200,
                variant: ""
            )
            resp["serverNow"] = nowIso
            resp["state"] = (stateJSON["state"] as? String) ?? "IN_PROGRESS"
            let data = try JSONSerialization.data(withJSONObject: resp)
            return ContractFakeTransport.jsonResponse(statusCode: 200, data: data)

        case .saveAnswers:
            if let revoked = checkSessionLocked(call: call, operationId: Operations.saveAnswers.id) {
                return revoked
            }
            activeSessionLastSeenAt = now
            let body = call.decodedJSON() ?? [:]
            var results: [[String: Any]] = []

            if let changes = body["changes"] as? [[String: Any]] {
                for change in changes {
                    guard let itemId = change["itemId"] as? String,
                          let seqNum = change["seq"] as? NSNumber
                    else { continue }
                    let seq = seqNum.int64Value
                    let answerObj = change["answer"]
                    let status = applyServerAnswerLocked(itemId: itemId, seq: seq, answer: answerObj)
                    results.append([
                        "itemId": itemId,
                        "seq": seq,
                        "status": status.status,
                        "reason": status.reason ?? NSNull()
                    ])
                }
            }

            if let flags = body["flags"] as? [[String: Any]] {
                for flag in flags {
                    guard let itemId = flag["itemId"] as? String,
                          let flagged = flag["flagged"] as? Bool
                    else { continue }
                    applyServerFlagLocked(itemId: itemId, flagged: flagged)
                }
            }

            if let position = body["position"] as? [String: Any],
               let partId = position["partId"] as? String,
               isPartOpenLocked(partId: partId)
            {
                stateJSON["lastPosition"] = position
            }

            var resp = try AttemptFixtureCatalog.loadJSONObject(
                operationId: Operations.saveAnswers.id,
                status: 200,
                variant: "saved"
            )
            resp["results"] = results
            resp["serverNow"] = nowIso
            let data = try JSONSerialization.data(withJSONObject: resp)
            return ContractFakeTransport.jsonResponse(statusCode: 200, data: data)

        case .finishPart:
            if let revoked = checkSessionLocked(call: call, operationId: Operations.finishPart.id) {
                return revoked
            }
            let body = call.decodedJSON() ?? [:]
            var results: [[String: Any]] = []
            if let finalAnswers = body["finalAnswers"] as? [[String: Any]] {
                for change in finalAnswers {
                    guard let itemId = change["itemId"] as? String,
                          let seqNum = change["seq"] as? NSNumber
                    else { continue }
                    let seq = seqNum.int64Value
                    let status = applyServerAnswerLocked(itemId: itemId, seq: seq, answer: change["answer"])
                    results.append([
                        "itemId": itemId,
                        "seq": seq,
                        "status": status.status,
                        "reason": status.reason ?? NSNull()
                    ])
                }
            }

            if let finishedPartId = extractPartId(from: call.path) {
                closePartAndOpenNextLocked(partId: finishedPartId, nowIso: nowIso)
            }

            var resp = try AttemptFixtureCatalog.loadJSONObject(
                operationId: Operations.finishPart.id,
                status: 200,
                variant: ""
            )
            resp["attemptId"] = attemptId
            resp["serverNow"] = nowIso
            resp["results"] = results
            let data = try JSONSerialization.data(withJSONObject: resp)
            return ContractFakeTransport.jsonResponse(statusCode: 200, data: data)

        case .submit:
            if let revoked = checkSessionLocked(call: call, operationId: Operations.submit.id) {
                return revoked
            }
            let body = call.decodedJSON() ?? [:]
            var results: [[String: Any]] = []
            if let finalAnswers = body["finalAnswers"] as? [[String: Any]] {
                for change in finalAnswers {
                    guard let itemId = change["itemId"] as? String,
                          let seqNum = change["seq"] as? NSNumber
                    else { continue }
                    let seq = seqNum.int64Value
                    let status = applyServerAnswerLocked(itemId: itemId, seq: seq, answer: change["answer"])
                    results.append([
                        "itemId": itemId,
                        "seq": seq,
                        "status": status.status,
                        "reason": status.reason ?? NSNull()
                    ])
                }
            }

            stateJSON["state"] = "GRADING"
            if var parts = stateJSON["parts"] as? [[String: Any]] {
                for idx in parts.indices where parts[idx]["state"] as? String == "OPEN" {
                    parts[idx]["state"] = "CLOSED"
                    parts[idx]["closedAt"] = nowIso
                }
                stateJSON["parts"] = parts
            }

            var resp = try AttemptFixtureCatalog.loadJSONObject(
                operationId: Operations.submit.id,
                status: 200,
                variant: ""
            )
            resp["attemptId"] = attemptId
            resp["serverNow"] = nowIso
            resp["results"] = results
            let data = try JSONSerialization.data(withJSONObject: resp)
            return ContractFakeTransport.jsonResponse(statusCode: 200, data: data)

        case .confirmPartChoice:
            if let revoked = checkSessionLocked(call: call, operationId: Operations.saveAnswers.id) {
                return revoked
            }
            var resp = try AttemptFixtureCatalog.loadJSONObject(
                operationId: Operations.confirmPartChoice.id,
                status: 202,
                variant: ""
            )
            resp["attemptId"] = attemptId
            resp["serverNow"] = nowIso
            if let targetPartId = extractPartId(from: call.path) {
                resp["partId"] = targetPartId
                updatePartStateLocked(partId: targetPartId, to: "GENERATING")
            }
            let data = try JSONSerialization.data(withJSONObject: resp)
            return ContractFakeTransport.jsonResponse(statusCode: 202, data: data)

        case .retryPartGeneration:
            if let revoked = checkSessionLocked(call: call, operationId: Operations.saveAnswers.id) {
                return revoked
            }
            var resp = try AttemptFixtureCatalog.loadJSONObject(
                operationId: Operations.retryPartGeneration.id,
                status: 202,
                variant: ""
            )
            resp["attemptId"] = attemptId
            resp["serverNow"] = nowIso
            if let targetPartId = extractPartId(from: call.path) {
                resp["partId"] = targetPartId
                updatePartStateLocked(partId: targetPartId, to: "GENERATING")
            }
            let data = try JSONSerialization.data(withJSONObject: resp)
            return ContractFakeTransport.jsonResponse(statusCode: 202, data: data)
        }
    }

    private func checkSessionLocked(call: RecordedCall, operationId: String) -> (HTTPResponse, HTTPBody?)? {
        guard let activeSessionId else { return nil }
        guard call.sessionHeader != activeSessionId else { return nil }
        let data = (try? AttemptFixtureCatalog.loadData(
            operationId: operationId,
            status: 409,
            variant: "attempt-session-revoked"
        )) ?? (try? AttemptFixtureCatalog.loadData(
            operationId: Operations.saveAnswers.id,
            status: 409,
            variant: "attempt-session-revoked"
        )) ?? Data()
        return ContractFakeTransport.jsonResponse(statusCode: 409, data: data)
    }

    private func applyServerAnswerLocked(itemId: String, seq: Int64, answer: Any?) -> (status: String, reason: String?) {
        if (stateJSON["state"] as? String) != "IN_PROGRESS" {
            return ("REJECTED", "TIME_UP")
        }
        guard var parts = stateJSON["parts"] as? [[String: Any]] else {
            return ("REJECTED", "NOT_IN_SCOPE")
        }
        for pIdx in parts.indices {
            guard var items = parts[pIdx]["items"] as? [[String: Any]] else { continue }
            for iIdx in items.indices {
                guard items[iIdx]["itemId"] as? String == itemId else { continue }
                if (parts[pIdx]["state"] as? String) != "OPEN" {
                    return ("REJECTED", "PART_CLOSED")
                }
                let currentSeq = (items[iIdx]["answerSeq"] as? NSNumber)?.int64Value ?? 0
                if seq < currentSeq {
                    return ("STALE", nil)
                }
                items[iIdx]["answerSeq"] = seq
                if let answer {
                    items[iIdx]["answer"] = answer
                }
                parts[pIdx]["items"] = items
                stateJSON["parts"] = parts
                return ("SAVED", nil)
            }
        }
        return ("REJECTED", "NOT_IN_SCOPE")
    }

    private func applyServerFlagLocked(itemId: String, flagged: Bool) {
        guard var parts = stateJSON["parts"] as? [[String: Any]] else { return }
        for pIdx in parts.indices {
            guard var items = parts[pIdx]["items"] as? [[String: Any]] else { continue }
            for iIdx in items.indices where items[iIdx]["itemId"] as? String == itemId {
                items[iIdx]["flagged"] = flagged
                parts[pIdx]["items"] = items
                stateJSON["parts"] = parts
                return
            }
        }
    }

    private func isPartOpenLocked(partId: String) -> Bool {
        guard let parts = stateJSON["parts"] as? [[String: Any]] else { return false }
        return parts.first { $0["partId"] as? String == partId }?["state"] as? String == "OPEN"
    }

    private func closePartAndOpenNextLocked(partId: String, nowIso: String) {
        guard var parts = stateJSON["parts"] as? [[String: Any]] else { return }
        var closedOrd: Int?
        for idx in parts.indices where parts[idx]["partId"] as? String == partId {
            parts[idx]["state"] = "CLOSED"
            parts[idx]["closedAt"] = nowIso
            closedOrd = parts[idx]["ord"] as? Int
        }
        if let closedOrd {
            for idx in parts.indices {
                if let ord = parts[idx]["ord"] as? Int,
                   ord > closedOrd,
                   parts[idx]["state"] as? String == "PENDING"
                {
                    parts[idx]["state"] = "OPEN"
                    parts[idx]["startedAt"] = nowIso
                    break
                }
            }
        }
        stateJSON["parts"] = parts
    }

    private func updatePartStateLocked(partId: String, to newState: String) {
        guard var parts = stateJSON["parts"] as? [[String: Any]] else { return }
        for idx in parts.indices where parts[idx]["partId"] as? String == partId {
            parts[idx]["state"] = newState
        }
        stateJSON["parts"] = parts
    }

    private func extractPartId(from path: String) -> String? {
        let components = path.split(separator: "/").map(String.init)
        guard let partsIndex = components.firstIndex(of: "parts"),
              partsIndex + 1 < components.count
        else {
            return nil
        }
        return components[partsIndex + 1]
    }
}

enum AttemptEngineTestHarness {
    @MainActor
    static func makeStore(
        accountId: String = "account-A",
        rootURL: URL? = nil
    ) throws -> (AccountScopedStoreProvider, AccountScopedStore, URL) {
        let dir = rootURL ?? FileManager.default.temporaryDirectory
            .appendingPathComponent("thi360-attempt-engine-\(UUID().uuidString)", isDirectory: true)
        let provider = AccountScopedStoreProvider(rootDirectoryURL: dir)
        let store = try XCTUnwrap(provider.store(for: accountId))
        return (provider, store, dir)
    }

    @MainActor
    static func makeConnectivity(initialOnline: Bool = true) -> (ConnectivityMonitor, AttemptFakeNetworkPathObserver) {
        let observer = AttemptFakeNetworkPathObserver()
        let monitor = ConnectivityMonitor(initialIsOnline: initialOnline, observer: observer, autoStart: true)
        return (monitor, observer)
    }
}
