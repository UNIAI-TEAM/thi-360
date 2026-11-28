import Foundation
import Network
import XCTest

final class AttachmentsUITestServer: @unchecked Sendable {
    struct RecordedRequest: Sendable {
        let method: String
        let path: String
        let query: String?
        let headers: [String: String]
        let body: Data

        func headerValue(_ name: String) -> String? {
            let lowered = name.lowercased()
            return headers.first(where: { $0.key.lowercased() == lowered })?.value
        }

        func decodedJSON() -> [String: Any]? {
            (try? JSONSerialization.jsonObject(with: body)) as? [String: Any]
        }
    }

    struct HTTPResponsePayload: Sendable {
        let statusCode: Int
        let contentType: String
        let body: Data
        let headers: [String: String]

        init(
            statusCode: Int,
            contentType: String = "application/json; charset=utf-8",
            body: Data = Data(),
            headers: [String: String] = [:]
        ) {
            self.statusCode = statusCode
            self.contentType = contentType
            self.body = body
            self.headers = headers
        }
    }

    static let attemptId = "00000000-0000-7000-8000-000000000001"
    static let partId = "00000000-0000-7000-8000-000000000006"
    static let pdfAssetId = "00000000-0000-7000-8000-000000000099"
    static let audioAssetId = "00000000-0000-7000-8000-000000000008"
    static let imageAssetId = "00000000-0000-7000-8000-000000000077"

    static var repoRootURL: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
    }

    static func loadMobileContract(_ relativePath: String) throws -> Data {
        try Data(contentsOf: repoRootURL.appendingPathComponent("contracts/mobile/\(relativePath)"))
    }

    static func loadAttachmentResource(_ fileName: String) throws -> Data {
        try Data(
            contentsOf: repoRootURL
                .appendingPathComponent("apps/ios/Thi360Tests/Attachments/Resources/\(fileName)")
        )
    }

    static func samplePNGData() -> Data {
        // Minimal valid 1x1 PNG bytes for in-memory image download checks.
        Data(base64Encoded: "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==")!
    }

    private let listener: NWListener
    private let queue = DispatchQueue(label: "vn.unicom.thi360.uitest.attachments.httpserver")
    private let lock = NSLock()
    private var requests: [RecordedRequest] = []
    private(set) var port: UInt16 = 0

    private let loginBody: Data
    private let refreshBody: Data
    private let meBody: Data
    private let profileBody: Data
    private let attemptDetailBody: Data
    private let claimBody: Data
    private let heartbeatBody: Data
    private let pdfData: Data
    private let audioData: Data
    private let imageData: Data

    private let audioDurationMs: Int64
    private let initialOpenPositionMs: Int64
    private let capSavedProgressAtMs: Int64?

    private var playSequence = 0
    private var playsUsed = 0
    private let maxPlays = 2
    private var activePlayId: String?
    private var savedPositionMs: Int64 = 0
    private var audioState = "IDLE"

    init(
        audioFileName: String = "silence-35s.wav",
        audioDurationMs: Int64 = 35_000,
        initialOpenPositionMs: Int64 = 0,
        capSavedProgressAtMs: Int64? = nil
    ) throws {
        self.loginBody = try Self.loadMobileContract("auth/login-200.json")
        self.refreshBody = try Self.loadMobileContract("auth/refresh-200.json")
        self.meBody = try Self.loadMobileContract("api/me-200.json")
        self.profileBody = try Self.loadMobileContract("api/me-profile-200.json")
        self.claimBody = try Self.loadMobileContract("api/attempts-session-200.json")
        self.heartbeatBody = try Self.loadMobileContract("api/attempts-heartbeat-200.json")
        self.pdfData = try Self.loadAttachmentResource("bang-so-lieu.pdf")
        self.audioData = try Self.loadAttachmentResource(audioFileName)
        self.imageData = Self.samplePNGData()
        self.audioDurationMs = audioDurationMs
        self.initialOpenPositionMs = initialOpenPositionMs
        self.capSavedProgressAtMs = capSavedProgressAtMs

        let rawDetail = try Self.loadMobileContract("api/attempts-detail-200-in-progress-shared.json")
        var detailObj = (try JSONSerialization.jsonObject(with: rawDetail) as? [String: Any]) ?? [:]
        let iso = ISO8601DateFormatter()
        iso.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let now = Date()
        detailObj["attemptId"] = Self.attemptId
        detailObj["serverNow"] = iso.string(from: now)
        detailObj["deadlineAt"] = iso.string(from: now.addingTimeInterval(3600))
        detailObj["audioPolicy"] = ["maxPlays": 2]
        detailObj["audioPlays"] = [[
            "assetId": Self.audioAssetId,
            "playsUsed": 0,
            "playsLeft": 2,
            "durationMs": audioDurationMs,
            "positionMs": 0,
            "state": "IDLE"
        ]]
        self.attemptDetailBody = try JSONSerialization.data(withJSONObject: detailObj)

        let parameters = NWParameters.tcp
        parameters.requiredLocalEndpoint = NWEndpoint.hostPort(host: .ipv4(.loopback), port: .any)
        self.listener = try NWListener(using: parameters)
    }

    func start() throws {
        let ready = DispatchSemaphore(value: 0)
        var startError: Error?
        listener.stateUpdateHandler = { [weak self] state in
            switch state {
            case .ready:
                self?.port = self?.listener.port?.rawValue ?? 0
                ready.signal()
            case let .failed(err):
                startError = err
                ready.signal()
            default:
                break
            }
        }
        listener.newConnectionHandler = { [weak self] conn in
            self?.handleConnection(conn)
        }
        listener.start(queue: queue)
        if ready.wait(timeout: .now() + 5) == .timedOut {
            throw NSError(domain: "AttachmentsUITestServer", code: -1)
        }
        if let startError { throw startError }
        guard port > 0 else {
            throw NSError(domain: "AttachmentsUITestServer", code: -2)
        }
    }

    func stop() {
        listener.cancel()
    }

    func recordedRequests() -> [RecordedRequest] {
        lock.lock()
        defer { lock.unlock() }
        return requests
    }

    func callCount(method: String, pathContains: String) -> Int {
        recordedRequests().filter {
            $0.method.uppercased() == method.uppercased() && $0.path.contains(pathContains)
        }.count
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
            if let chunk { accumulated.append(chunk) }
            let separator = Data("\r\n\r\n".utf8)
            if let range = accumulated.range(of: separator) {
                let headerString = String(data: accumulated[..<range.lowerBound], encoding: .utf8) ?? ""
                let contentLength = Self.parseContentLength(from: headerString)
                let body = Data(accumulated[range.upperBound...])
                if body.count >= contentLength || isComplete {
                    self.respond(headerString: headerString, body: body, on: connection)
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
        let lines = headerString.split(separator: "\r\n", omittingEmptySubsequences: false)
        let firstLine = lines.first ?? ""
        let parts = firstLine.split(separator: " ")
        let method = parts.count > 0 ? String(parts[0]) : "GET"
        let fullTarget = parts.count > 1 ? String(parts[1]) : "/"
        let targetParts = fullTarget.split(separator: "?", maxSplits: 1).map(String.init)
        let path = targetParts.first ?? fullTarget
        let query = targetParts.count > 1 ? targetParts[1] : nil

        var headers: [String: String] = [:]
        for line in lines.dropFirst() {
            if let colon = line.firstIndex(of: ":") {
                let key = String(line[..<colon]).trimmingCharacters(in: .whitespaces)
                let val = String(line[line.index(after: colon)...]).trimmingCharacters(in: .whitespaces)
                headers[key] = val
            }
        }

        let req = RecordedRequest(method: method, path: path, query: query, headers: headers, body: body)
        lock.lock()
        requests.append(req)
        lock.unlock()

        let response = route(req)
        var raw = "HTTP/1.1 \(response.statusCode) \(Self.reasonPhrase(for: response.statusCode))\r\n"
        if response.statusCode != 204 {
            raw += "Content-Type: \(response.contentType)\r\n"
            raw += "Content-Length: \(response.body.count)\r\n"
        }
        for (name, value) in response.headers {
            raw += "\(name): \(value)\r\n"
        }
        raw += "Connection: close\r\n\r\n"

        var payload = Data(raw.utf8)
        if response.statusCode != 204 {
            payload.append(response.body)
        }
        connection.send(content: payload, completion: .contentProcessed { _ in
            connection.cancel()
        })
    }

    private func route(_ req: RecordedRequest) -> HTTPResponsePayload {
        switch (req.method.uppercased(), req.path) {
        case ("POST", "/api/v1/auth/mobile/login"):
            return HTTPResponsePayload(statusCode: 200, body: loginBody)
        case ("POST", "/api/v1/auth/mobile/refresh"):
            return HTTPResponsePayload(statusCode: 200, body: refreshBody)
        case ("POST", "/api/v1/auth/mobile/logout"):
            return HTTPResponsePayload(statusCode: 204)
        case ("GET", "/api/v1/me"):
            return HTTPResponsePayload(statusCode: 200, body: meBody)
        case ("GET", "/api/v1/me/profile"):
            return HTTPResponsePayload(statusCode: 200, body: profileBody)
        case ("GET", "/api/v1/me/attempts/\(Self.attemptId)"):
            return HTTPResponsePayload(statusCode: 200, body: currentAttemptDetailBody())
        case ("POST", "/api/v1/me/attempts/\(Self.attemptId)/session"):
            return HTTPResponsePayload(statusCode: 200, body: claimBody)
        case ("POST", "/api/v1/me/attempts/\(Self.attemptId)/heartbeat"):
            return HTTPResponsePayload(statusCode: 200, body: heartbeatBody)
        case ("GET", "/api/v1/me/attempts/\(Self.attemptId)/assets/\(Self.pdfAssetId)"):
            return HTTPResponsePayload(
                statusCode: 200,
                contentType: "application/pdf",
                body: pdfData,
                headers: ["Cache-Control": "private, no-store"]
            )
        case ("GET", "/api/v1/me/attempts/\(Self.attemptId)/assets/\(Self.audioAssetId)"):
            return HTTPResponsePayload(
                statusCode: 200,
                contentType: "audio/wav",
                body: audioData,
                headers: ["Cache-Control": "private, no-store"]
            )
        case ("GET", "/api/v1/me/attempts/\(Self.attemptId)/assets/\(Self.imageAssetId)"):
            return HTTPResponsePayload(
                statusCode: 200,
                contentType: "image/png",
                body: imageData,
                headers: ["Cache-Control": "private, no-store"]
            )
        case ("POST", "/api/v1/me/attempts/\(Self.attemptId)/audio/\(Self.audioAssetId)/plays"):
            return handleOpenPlay()
        default:
            if req.method.uppercased() == "POST", req.path.hasSuffix("/resume") {
                return handleResume()
            }
            if req.method.uppercased() == "PUT", req.path.hasSuffix("/progress") {
                return handleProgress(req)
            }
            if req.method.uppercased() == "POST", req.path.hasSuffix("/end") {
                return handleEnd()
            }
            return HTTPResponsePayload(
                statusCode: 404,
                body: Data(#"{"code":"NOT_FOUND","message":"Not found","fieldErrors":[],"traceId":"uitest-404"}"#.utf8)
            )
        }
    }

    private func currentAttemptDetailBody() -> Data {
        lock.lock()
        let snap: [String: Any] = [
            "assetId": Self.audioAssetId,
            "playId": activePlayId as Any,
            "playsUsed": playsUsed,
            "playsLeft": max(0, maxPlays - playsUsed),
            "durationMs": audioDurationMs,
            "positionMs": savedPositionMs,
            "state": audioState
        ]
        lock.unlock()

        guard var detail = (try? JSONSerialization.jsonObject(with: attemptDetailBody)) as? [String: Any] else {
            return attemptDetailBody
        }
        var cleanedSnap = snap
        if cleanedSnap["playId"] is NSNull {
            cleanedSnap.removeValue(forKey: "playId")
        }
        detail["audioPlays"] = [cleanedSnap]
        return (try? JSONSerialization.data(withJSONObject: detail)) ?? attemptDetailBody
    }

    private func handleOpenPlay() -> HTTPResponsePayload {
        lock.lock()
        playSequence += 1
        playsUsed += 1
        let playId = "uitest-play-\(playSequence)"
        activePlayId = playId
        savedPositionMs = initialOpenPositionMs
        audioState = "PLAYING"
        let obj: [String: Any] = [
            "playId": playId,
            "playsUsed": playsUsed,
            "playsLeft": max(0, maxPlays - playsUsed),
            "durationMs": audioDurationMs,
            "positionMs": savedPositionMs,
            "state": audioState
        ]
        lock.unlock()
        let data = (try? JSONSerialization.data(withJSONObject: obj)) ?? Data()
        return HTTPResponsePayload(statusCode: 200, body: data)
    }

    private func handleResume() -> HTTPResponsePayload {
        lock.lock()
        let playId = activePlayId ?? "uitest-play-1"
        let obj: [String: Any] = [
            "playId": playId,
            "playsUsed": playsUsed,
            "playsLeft": max(0, maxPlays - playsUsed),
            "durationMs": audioDurationMs,
            "positionMs": savedPositionMs,
            "state": "PLAYING"
        ]
        lock.unlock()
        let data = (try? JSONSerialization.data(withJSONObject: obj)) ?? Data()
        return HTTPResponsePayload(statusCode: 200, body: data)
    }

    private func handleProgress(_ req: RecordedRequest) -> HTTPResponsePayload {
        let reported = (req.decodedJSON()?["positionMs"] as? NSNumber)?.int64Value ?? 0
        lock.lock()
        let capped: Int64
        if let cap = capSavedProgressAtMs {
            capped = min(reported, cap)
        } else {
            capped = min(reported, audioDurationMs)
        }
        savedPositionMs = max(savedPositionMs, capped)
        let playId = activePlayId ?? "uitest-play-1"
        let obj: [String: Any] = [
            "playId": playId,
            "playsUsed": playsUsed,
            "playsLeft": max(0, maxPlays - playsUsed),
            "durationMs": audioDurationMs,
            "positionMs": savedPositionMs,
            "state": "PLAYING"
        ]
        lock.unlock()
        let data = (try? JSONSerialization.data(withJSONObject: obj)) ?? Data()
        return HTTPResponsePayload(statusCode: 200, body: data)
    }

    private func handleEnd() -> HTTPResponsePayload {
        lock.lock()
        audioState = "ENDED"
        savedPositionMs = audioDurationMs
        let playId = activePlayId ?? "uitest-play-1"
        let obj: [String: Any] = [
            "playId": playId,
            "playsUsed": playsUsed,
            "playsLeft": max(0, maxPlays - playsUsed),
            "durationMs": audioDurationMs,
            "positionMs": savedPositionMs,
            "state": "ENDED",
            "endReason": "FINISHED"
        ]
        lock.unlock()
        let data = (try? JSONSerialization.data(withJSONObject: obj)) ?? Data()
        return HTTPResponsePayload(statusCode: 200, body: data)
    }

    private static func parseContentLength(from headerString: String) -> Int {
        for line in headerString.split(separator: "\r\n") {
            let lowered = line.lowercased()
            if lowered.hasPrefix("content-length:") {
                let raw = lowered.dropFirst("content-length:".count).trimmingCharacters(in: .whitespaces)
                return Int(raw) ?? 0
            }
        }
        return 0
    }

    private static func reasonPhrase(for status: Int) -> String {
        switch status {
        case 200: "OK"
        case 204: "No Content"
        case 401: "Unauthorized"
        case 404: "Not Found"
        case 409: "Conflict"
        default: "Response"
        }
    }
}

enum AttachmentsSandboxInspector {
    static func scanFiles(under containerPath: String) -> [URL] {
        let root = URL(fileURLWithPath: containerPath, isDirectory: true)
        guard let enumerator = FileManager.default.enumerator(
            at: root,
            includingPropertiesForKeys: [.isRegularFileKey],
            options: [.skipsHiddenFiles]
        ) else {
            return []
        }
        var found: [URL] = []
        for case let url as URL in enumerator {
            if (try? url.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true {
                found.append(url)
            }
        }
        return found
    }

    static func attemptAssetTempFiles(under containerPath: String) -> [URL] {
        let tempDir = URL(fileURLWithPath: containerPath, isDirectory: true)
            .appendingPathComponent("Library/Caches/AttemptAssets", isDirectory: true)
        guard let items = try? FileManager.default.contentsOfDirectory(
            at: tempDir,
            includingPropertiesForKeys: nil
        ) else {
            return []
        }
        return items
    }

    static func filesMatchingMediaExtensionsOrBytes(
        under containerPath: String,
        forbiddenPayloads: [Data]
    ) -> [String] {
        let forbiddenExts: Set<String> = ["pdf", "wav", "mp3", "m4a", "aac", "ogg", "flac"]
        var violations: [String] = []
        for fileURL in scanFiles(under: containerPath) {
            let ext = fileURL.pathExtension.lowercased()
            if forbiddenExts.contains(ext) {
                violations.append("forbidden-ext:\(fileURL.path)")
                continue
            }
            if !forbiddenPayloads.isEmpty, let bytes = try? Data(contentsOf: fileURL) {
                for payload in forbiddenPayloads where !payload.isEmpty && bytes == payload {
                    violations.append("payload-match:\(fileURL.path)")
                    break
                }
            }
        }
        return violations
    }
}
