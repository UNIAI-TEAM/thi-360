import AVFoundation
import Foundation
import HTTPTypes
import OpenAPIRuntime
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

enum AttachmentsTestResources {
    private static var resourcesDirURL: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent("Resources", isDirectory: true)
    }

    static func load(_ fileName: String) throws -> Data {
        try Data(contentsOf: resourcesDirURL.appendingPathComponent(fileName))
    }

    static func pdfData() throws -> Data {
        try load("bang-so-lieu.pdf")
    }

    static func silence2sWav() throws -> Data {
        try load("silence-2s.wav")
    }

    static func silence35sWav() throws -> Data {
        try load("silence-35s.wav")
    }
}

final class MockAudioSessionController: AudioSessionControlling {
    struct CategoryCall: Equatable {
        let category: AVAudioSession.Category
        let mode: AVAudioSession.Mode
        let options: AVAudioSession.CategoryOptions
    }

    struct ActiveCall: Equatable {
        let active: Bool
        let options: AVAudioSession.SetActiveOptions
    }

    private(set) var categoryCalls: [CategoryCall] = []
    private(set) var activeCalls: [ActiveCall] = []

    func setCategory(
        _ category: AVAudioSession.Category,
        mode: AVAudioSession.Mode,
        options: AVAudioSession.CategoryOptions
    ) throws {
        categoryCalls.append(CategoryCall(category: category, mode: mode, options: options))
    }

    func setActive(_ active: Bool, options: AVAudioSession.SetActiveOptions) throws {
        activeCalls.append(ActiveCall(active: active, options: options))
    }
}

@MainActor
final class FakeAudioPlaybackEngine: AudioPlaybackEngine {
    struct StartCall {
        let byteCount: Int
        let fileName: String
        let startPositionMs: Int64
    }

    var simulatedDurationMs: Int64
    var shouldFailNextStart = false
    var autoAdvanceWithClock: TestEngineClock?

    private final class AtomicBoolBox: @unchecked Sendable {
        private let lock = NSLock()
        private var value = false

        var current: Bool {
            get {
                lock.lock()
                defer { lock.unlock() }
                return value
            }
            set {
                lock.lock()
                value = newValue
                lock.unlock()
            }
        }
    }

    private let playingBox = AtomicBoolBox()

    private(set) var isPlaying: Bool {
        get { playingBox.current }
        set { playingBox.current = newValue }
    }

    nonisolated var isPlayingThreadSafe: Bool {
        playingBox.current
    }

    private(set) var hasLoadedAudioData = false
    private(set) var startCalls: [StartCall] = []
    private(set) var seekCalls: [Int64] = []
    private(set) var stopCount = 0
    private(set) var stopTimestamps: [ContinuousClock.Instant] = []
    private(set) var lastDataByteCount = 0

    private var callbacks: AudioPlaybackCallbacks?
    private var anchorPositionMs: Int64 = 0
    private var anchorClockInstant: ContinuousClock.Instant?
    private var manualPositionOverrideMs: Int64?

    init(durationMs: Int64 = 30_000, clock: TestEngineClock? = nil) {
        self.simulatedDurationMs = durationMs
        self.autoAdvanceWithClock = clock
    }

    func start(
        data: Data,
        fileName: String,
        startPositionMs: Int64,
        callbacks: AudioPlaybackCallbacks
    ) -> AudioPlaybackStartResult {
        startCalls.append(StartCall(byteCount: data.count, fileName: fileName, startPositionMs: startPositionMs))
        if shouldFailNextStart {
            shouldFailNextStart = false
            isPlaying = false
            hasLoadedAudioData = false
            return .failed
        }
        self.callbacks = callbacks
        self.isPlaying = true
        self.hasLoadedAudioData = !data.isEmpty
        self.lastDataByteCount = data.count
        self.anchorPositionMs = startPositionMs
        self.anchorClockInstant = autoAdvanceWithClock?.now
        self.manualPositionOverrideMs = nil
        return .started(durationMs: simulatedDurationMs)
    }

    func setManualPositionMs(_ positionMs: Int64) {
        manualPositionOverrideMs = positionMs
        anchorPositionMs = positionMs
        anchorClockInstant = autoAdvanceWithClock?.now
    }

    func advanceManualPosition(byMs deltaMs: Int64) {
        let current = currentPositionMs()
        setManualPositionMs(current + deltaMs)
    }

    func currentPositionMs() -> Int64 {
        if let manualPositionOverrideMs {
            return manualPositionOverrideMs
        }
        if isPlaying, let clock = autoAdvanceWithClock, let origin = anchorClockInstant {
            let elapsed = max(.zero, clock.now - origin)
            let elapsedMs = Int64((ServerClock.timeInterval(from: elapsed) * 1_000.0).rounded())
            return min(simulatedDurationMs, anchorPositionMs + elapsedMs)
        }
        return anchorPositionMs
    }

    func seekToAuthorized(_ positionMs: Int64) {
        seekCalls.append(positionMs)
        manualPositionOverrideMs = nil
        anchorPositionMs = positionMs
        anchorClockInstant = autoAdvanceWithClock?.now
    }

    func enforcePlaybackRateAndAntiSeek() {}

    func stopAndRelease() {
        isPlaying = false
        hasLoadedAudioData = false
        callbacks = nil
        stopCount += 1
        if let clock = autoAdvanceWithClock {
            stopTimestamps.append(clock.now)
        }
    }

    func simulateCompletion() {
        callbacks?.onCompleted()
    }

    func simulateError() {
        callbacks?.onError()
    }

    func simulateInterruptionBegan() {
        callbacks?.onInterruptionBegan()
    }

    func simulateOldDeviceUnavailable() {
        callbacks?.onOldDeviceUnavailable()
    }

    func simulateRemotePause() {
        callbacks?.onRemotePause()
    }
}

final class AudioServerFixtureRouter: @unchecked Sendable {
    enum Endpoint: String, Hashable, Sendable {
        case openPlay
        case resume
        case progress
        case end
        case fetchAudio
    }

    struct RecordedAudioCall: Sendable {
        let endpoint: Endpoint
        let method: HTTPRequest.Method
        let path: String
        let query: String?
        let headers: HTTPFields
        let bodyData: Data?

        func headerValue(_ name: String) -> String? {
            guard let field = HTTPField.Name(name) else { return nil }
            return headers[field]
        }

        var authorizationHeader: String? { headerValue("Authorization") }
        var sessionHeader: String? { headerValue("X-Attempt-Session") }

        func decodedJSON() -> [String: Any]? {
            guard let bodyData else { return nil }
            return (try? JSONSerialization.jsonObject(with: bodyData)) as? [String: Any]
        }
    }

    struct QueuedResponse: @unchecked Sendable {
        let statusCode: Int
        let data: Data
        let contentType: String
        let networkError: URLError.Code?
        let gate: AttemptTestGate?

        static func json(
            statusCode: Int,
            data: Data,
            gate: AttemptTestGate? = nil
        ) -> QueuedResponse {
            QueuedResponse(
                statusCode: statusCode,
                data: data,
                contentType: "application/json; charset=utf-8",
                networkError: nil,
                gate: gate
            )
        }

        static func fixture(
            operationId: String,
            status: Int,
            variant: String = "",
            gate: AttemptTestGate? = nil,
            mutate: ((inout [String: Any]) -> Void)? = nil
        ) throws -> QueuedResponse {
            var obj = try AttemptFixtureCatalog.loadJSONObject(
                operationId: operationId,
                status: status,
                variant: variant
            )
            mutate?(&obj)
            let data = try JSONSerialization.data(withJSONObject: obj)
            return .json(statusCode: status, data: data, gate: gate)
        }

        static func binary(
            statusCode: Int = 200,
            data: Data,
            contentType: String = "audio/wav",
            gate: AttemptTestGate? = nil
        ) -> QueuedResponse {
            QueuedResponse(
                statusCode: statusCode,
                data: data,
                contentType: contentType,
                networkError: nil,
                gate: gate
            )
        }

        static func networkError(
            _ code: URLError.Code = .notConnectedToInternet,
            gate: AttemptTestGate? = nil
        ) -> QueuedResponse {
            QueuedResponse(
                statusCode: 0,
                data: Data(),
                contentType: "application/json",
                networkError: code,
                gate: gate
            )
        }
    }

    private struct AssetState {
        var maxPlays: Int
        var durationMs: Int64
        var playsUsed: Int
        var activePlayId: String?
        var positionMs: Int64
        var state: String
        var endReason: String?
        var requestIdToPlayId: [String: String] = [:]
        var playSequence: Int = 0
        var lastLeaseRenewedAt: ContinuousClock.Instant?
    }

    let attemptId: String
    private let server: FakeAttemptServer
    private let clock: TestEngineClock?
    private let lock = NSLock()
    private var assetStates: [String: AssetState] = [:]
    private var audioPayloads: [String: Data] = [:]
    private var queues: [Endpoint: [QueuedResponse]] = [:]
    private var calls: [RecordedAudioCall] = []
    private var leaseTimeoutSeconds: Double?

    init(server: FakeAttemptServer, clock: TestEngineClock? = nil) {
        self.attemptId = server.attemptId
        self.server = server
        self.clock = clock
    }

    func setLeaseTimeout(seconds: Double?) {
        lock.lock()
        leaseTimeoutSeconds = seconds
        lock.unlock()
    }

    func configureAsset(
        assetId: String,
        maxPlays: Int = 2,
        durationMs: Int64 = 30_000,
        playsUsed: Int = 0,
        activePlayId: String? = nil,
        positionMs: Int64 = 0,
        state: String = "IDLE",
        endReason: String? = nil,
        audioData: Data? = nil,
        knownPlayIds: [String] = []
    ) throws {
        let bytes: Data
        if let audioData {
            bytes = audioData
        } else {
            bytes = try AttachmentsTestResources.silence35sWav()
        }

        let initialState = AssetState(
            maxPlays: maxPlays,
            durationMs: durationMs,
            playsUsed: playsUsed,
            activePlayId: activePlayId,
            positionMs: positionMs,
            state: state,
            endReason: endReason,
            lastLeaseRenewedAt: clock?.now
        )

        lock.lock()
        assetStates[assetId] = initialState
        audioPayloads[assetId] = bytes
        lock.unlock()

        registerRoutes(for: assetId, knownPlayIds: Set([activePlayId].compactMap { $0 } + knownPlayIds))
        syncServerAttemptAudioPlays()
    }

    func registerPlayId(_ playId: String, for assetId: String) {
        registerPlayRoutes(assetId: assetId, playId: playId)
    }

    func enqueue(_ endpoint: Endpoint, _ response: QueuedResponse) {
        lock.lock()
        queues[endpoint, default: []].append(response)
        lock.unlock()
    }

    func enqueue(_ endpoint: Endpoint, _ responses: [QueuedResponse]) {
        lock.lock()
        queues[endpoint, default: []].append(contentsOf: responses)
        lock.unlock()
    }

    func recordedCalls(for endpoint: Endpoint? = nil) -> [RecordedAudioCall] {
        lock.lock()
        defer { lock.unlock() }
        guard let endpoint else { return calls }
        return calls.filter { $0.endpoint == endpoint }
    }

    func callCount(for endpoint: Endpoint) -> Int {
        recordedCalls(for: endpoint).count
    }

    func resetCalls() {
        lock.lock()
        calls.removeAll()
        lock.unlock()
    }

    func updateAssetState(
        assetId: String,
        playsUsed: Int,
        activePlayId: String?,
        positionMs: Int64,
        state: String,
        endReason: String? = nil
    ) {
        lock.lock()
        if var existing = assetStates[assetId] {
            existing.playsUsed = playsUsed
            existing.activePlayId = activePlayId
            existing.positionMs = positionMs
            existing.state = state
            existing.endReason = endReason
            assetStates[assetId] = existing
        }
        lock.unlock()
        if let activePlayId {
            registerPlayRoutes(assetId: assetId, playId: activePlayId)
        }
        syncServerAttemptAudioPlays()
    }

    func syncServerAttemptAudioPlays() {
        let snapshotEntries: [[String: Any]] = {
            lock.lock()
            defer { lock.unlock() }
            return assetStates.keys.sorted().compactMap { assetId in
                guard let st = assetStates[assetId] else { return nil }
                var entry: [String: Any] = [
                    "assetId": assetId,
                    "playsUsed": st.playsUsed,
                    "playsLeft": max(0, st.maxPlays - st.playsUsed),
                    "durationMs": st.durationMs,
                    "positionMs": st.positionMs,
                    "state": st.state
                ]
                if let playId = st.activePlayId {
                    entry["playId"] = playId
                }
                if let endReason = st.endReason {
                    entry["endReason"] = endReason
                }
                return entry
            }
        }()

        let policyMax: Int = {
            lock.lock()
            defer { lock.unlock() }
            return assetStates.values.first?.maxPlays ?? 2
        }()

        server.mutateState { stateJSON in
            stateJSON["audioPolicy"] = ["maxPlays": policyMax]
            stateJSON["audioPlays"] = snapshotEntries
        }
    }

    private func registerRoutes(for assetId: String, knownPlayIds: Set<String>) {
        let openPath = "/api/v1/me/attempts/\(attemptId)/audio/\(assetId)/plays"
        server.transport.register(method: .post, path: openPath) { [weak self] request in
            guard let self else { return ContractFakeTransport.emptyResponse(statusCode: 500) }
            return try await self.handleOpenPlay(assetId: assetId, request: request)
        }

        let assetPath = "/api/v1/me/attempts/\(attemptId)/assets/\(assetId)"
        server.transport.register(method: .get, path: assetPath) { [weak self] request in
            guard let self else { return ContractFakeTransport.emptyResponse(statusCode: 500) }
            return try await self.handleFetchAudio(assetId: assetId, request: request)
        }

        for i in 1...12 {
            registerPlayRoutes(assetId: assetId, playId: "play-\(assetId.suffix(4))-\(i)")
        }
        for known in knownPlayIds {
            registerPlayRoutes(assetId: assetId, playId: known)
        }
    }

    private func registerPlayRoutes(assetId: String, playId: String) {
        let base = "/api/v1/me/attempts/\(attemptId)/audio/\(assetId)/plays/\(playId)"
        server.transport.register(method: .post, path: "\(base)/resume") { [weak self] request in
            guard let self else { return ContractFakeTransport.emptyResponse(statusCode: 500) }
            return try await self.handleResume(assetId: assetId, playId: playId, request: request)
        }
        server.transport.register(method: .put, path: "\(base)/progress") { [weak self] request in
            guard let self else { return ContractFakeTransport.emptyResponse(statusCode: 500) }
            return try await self.handleProgress(assetId: assetId, playId: playId, request: request)
        }
        server.transport.register(method: .post, path: "\(base)/end") { [weak self] request in
            guard let self else { return ContractFakeTransport.emptyResponse(statusCode: 500) }
            return try await self.handleEnd(assetId: assetId, playId: playId, request: request)
        }
    }

    private func recordAndPopQueue(
        endpoint: Endpoint,
        request: RecordedHTTPRequest
    ) -> QueuedResponse? {
        let pathParts = request.path.split(separator: "?", maxSplits: 1).map(String.init)
        let call = RecordedAudioCall(
            endpoint: endpoint,
            method: request.method,
            path: pathParts.first ?? request.path,
            query: pathParts.count > 1 ? pathParts[1] : nil,
            headers: request.headers,
            bodyData: request.bodyData
        )
        lock.lock()
        calls.append(call)
        var q = queues[endpoint] ?? []
        let next = q.isEmpty ? nil : q.removeFirst()
        queues[endpoint] = q
        lock.unlock()
        return next
    }

    private func deliverQueued(_ queued: QueuedResponse) async throws -> (HTTPResponse, HTTPBody?) {
        if let gate = queued.gate {
            await gate.enterAndWait()
        }
        if let networkError = queued.networkError {
            throw URLError(networkError)
        }
        var fields = HTTPFields()
        fields[.contentType] = queued.contentType
        let response = HTTPResponse(status: HTTPResponse.Status(code: queued.statusCode), headerFields: fields)
        return (response, HTTPBody(queued.data))
    }

    private enum RouteOutcome {
        case ok([String: Any])
        case fail(Int, [String: Any])
    }

    private func handleOpenPlay(assetId: String, request: RecordedHTTPRequest) async throws -> (HTTPResponse, HTTPBody?) {
        if let queued = recordAndPopQueue(endpoint: .openPlay, request: request) {
            return try await deliverQueued(queued)
        }
        let bodyObj = request.bodyData.flatMap { (try? JSONSerialization.jsonObject(with: $0)) as? [String: Any] }
        let requestId = bodyObj?["requestId"] as? String ?? UUID().uuidString

        let result: RouteOutcome = {
            lock.lock()
            defer { lock.unlock() }
            guard var st = assetStates[assetId] else {
                return .fail(404, ["code": "NOT_FOUND", "message": "Not found"])
            }
            if let existingPlayId = st.requestIdToPlayId[requestId] {
                st.lastLeaseRenewedAt = clock?.now
                assetStates[assetId] = st
                return .ok(playResponseObject(playId: existingPlayId, state: st))
            }
            if st.state == "PLAYING", let activePlayId = st.activePlayId {
                return .fail(409, [
                    "code": "AUDIO_PLAY_IN_PROGRESS",
                    "message": "Play already in progress",
                    "playId": activePlayId,
                    "fieldErrors": [],
                    "traceId": "audio-router"
                ])
            }
            if st.playsUsed >= st.maxPlays {
                return .fail(409, [
                    "code": "AUDIO_PLAYS_EXHAUSTED",
                    "message": "No plays left",
                    "fieldErrors": [],
                    "traceId": "audio-router"
                ])
            }
            st.playSequence += 1
            let newPlayId = "play-\(assetId.suffix(4))-\(st.playSequence)"
            st.playsUsed += 1
            st.activePlayId = newPlayId
            st.positionMs = 0
            st.state = "PLAYING"
            st.endReason = nil
            st.requestIdToPlayId[requestId] = newPlayId
            st.lastLeaseRenewedAt = clock?.now
            assetStates[assetId] = st
            return .ok(playResponseObject(playId: newPlayId, state: st))
        }()

        switch result {
        case let .ok(obj):
            if let playId = obj["playId"] as? String {
                registerPlayRoutes(assetId: assetId, playId: playId)
            }
            syncServerAttemptAudioPlays()
            let data = try JSONSerialization.data(withJSONObject: obj)
            return ContractFakeTransport.jsonResponse(statusCode: 200, data: data)
        case let .fail(status, obj):
            let data = try JSONSerialization.data(withJSONObject: obj)
            return ContractFakeTransport.jsonResponse(statusCode: status, data: data)
        }
    }

    private func handleResume(assetId: String, playId: String, request: RecordedHTTPRequest) async throws -> (HTTPResponse, HTTPBody?) {
        if let queued = recordAndPopQueue(endpoint: .resume, request: request) {
            return try await deliverQueued(queued)
        }
        let result: RouteOutcome = {
            lock.lock()
            defer { lock.unlock() }
            guard var st = assetStates[assetId] else {
                return .fail(404, ["code": "NOT_FOUND", "message": "Not found"])
            }
            guard st.activePlayId == playId, st.state == "PLAYING" else {
                return .fail(409, [
                    "code": "AUDIO_PLAY_STALE",
                    "message": "Play is stale",
                    "fieldErrors": [],
                    "traceId": "audio-router"
                ])
            }
            st.lastLeaseRenewedAt = clock?.now
            assetStates[assetId] = st
            return .ok(playResponseObject(playId: playId, state: st))
        }()

        switch result {
        case let .ok(obj):
            syncServerAttemptAudioPlays()
            let data = try JSONSerialization.data(withJSONObject: obj)
            return ContractFakeTransport.jsonResponse(statusCode: 200, data: data)
        case let .fail(status, obj):
            let data = try JSONSerialization.data(withJSONObject: obj)
            return ContractFakeTransport.jsonResponse(statusCode: status, data: data)
        }
    }

    private func handleProgress(assetId: String, playId: String, request: RecordedHTTPRequest) async throws -> (HTTPResponse, HTTPBody?) {
        if let queued = recordAndPopQueue(endpoint: .progress, request: request) {
            return try await deliverQueued(queued)
        }
        let bodyObj = request.bodyData.flatMap { (try? JSONSerialization.jsonObject(with: $0)) as? [String: Any] }
        let positionMs = (bodyObj?["positionMs"] as? NSNumber)?.int64Value ?? 0

        let result: RouteOutcome = {
            lock.lock()
            defer { lock.unlock() }
            guard var st = assetStates[assetId] else {
                return .fail(404, ["code": "NOT_FOUND", "message": "Not found"])
            }
            guard st.activePlayId == playId, st.state == "PLAYING" else {
                return .fail(409, [
                    "code": "AUDIO_PLAY_STALE",
                    "message": "Play is stale",
                    "fieldErrors": [],
                    "traceId": "audio-router"
                ])
            }
            st.positionMs = max(st.positionMs, min(st.durationMs, positionMs))
            st.lastLeaseRenewedAt = clock?.now
            assetStates[assetId] = st
            return .ok(playResponseObject(playId: playId, state: st))
        }()

        switch result {
        case let .ok(obj):
            syncServerAttemptAudioPlays()
            let data = try JSONSerialization.data(withJSONObject: obj)
            return ContractFakeTransport.jsonResponse(statusCode: 200, data: data)
        case let .fail(status, obj):
            let data = try JSONSerialization.data(withJSONObject: obj)
            return ContractFakeTransport.jsonResponse(statusCode: status, data: data)
        }
    }

    private func handleEnd(assetId: String, playId: String, request: RecordedHTTPRequest) async throws -> (HTTPResponse, HTTPBody?) {
        if let queued = recordAndPopQueue(endpoint: .end, request: request) {
            return try await deliverQueued(queued)
        }
        let result: RouteOutcome = {
            lock.lock()
            defer { lock.unlock() }
            guard var st = assetStates[assetId] else {
                return .fail(404, ["code": "NOT_FOUND", "message": "Not found"])
            }
            st.state = "ENDED"
            st.endReason = "FINISHED"
            st.positionMs = st.durationMs
            assetStates[assetId] = st
            return .ok(playResponseObject(playId: playId, state: st))
        }()

        switch result {
        case let .ok(obj):
            syncServerAttemptAudioPlays()
            let data = try JSONSerialization.data(withJSONObject: obj)
            return ContractFakeTransport.jsonResponse(statusCode: 200, data: data)
        case let .fail(status, obj):
            let data = try JSONSerialization.data(withJSONObject: obj)
            return ContractFakeTransport.jsonResponse(statusCode: status, data: data)
        }
    }

    private func handleFetchAudio(assetId: String, request: RecordedHTTPRequest) async throws -> (HTTPResponse, HTTPBody?) {
        if let queued = recordAndPopQueue(endpoint: .fetchAudio, request: request) {
            return try await deliverQueued(queued)
        }

        let (payload, leaseExpired): (Data?, Bool) = {
            lock.lock()
            defer { lock.unlock() }
            var expired = false
            if let timeout = leaseTimeoutSeconds,
               let clock,
               let st = assetStates[assetId],
               let renewed = st.lastLeaseRenewedAt
            {
                let elapsed = ServerClock.timeInterval(from: max(.zero, clock.now - renewed))
                if elapsed > timeout {
                    expired = true
                }
            }
            return (audioPayloads[assetId], expired)
        }()

        if leaseExpired {
            let errObj: [String: Any] = [
                "code": "AUDIO_PLAY_STALE",
                "message": "Lease expired",
                "fieldErrors": [],
                "traceId": "audio-lease-expired"
            ]
            let data = try JSONSerialization.data(withJSONObject: errObj)
            return ContractFakeTransport.jsonResponse(statusCode: 409, data: data)
        }

        guard let payload else {
            let data = try AttemptFixtureCatalog.loadData(
                operationId: Operations.downloadAttemptAsset.id,
                status: 404,
                variant: "not-found"
            )
            return ContractFakeTransport.jsonResponse(statusCode: 404, data: data)
        }
        var fields = HTTPFields()
        fields[.contentType] = "audio/wav"
        fields[.cacheControl] = "private, no-store"
        let response = HTTPResponse(status: .ok, headerFields: fields)
        return (response, HTTPBody(payload))
    }

    private func playResponseObject(playId: String, state: AssetState) -> [String: Any] {
        var obj: [String: Any] = [
            "playId": playId,
            "playsUsed": state.playsUsed,
            "playsLeft": max(0, state.maxPlays - state.playsUsed),
            "durationMs": state.durationMs,
            "positionMs": state.positionMs,
            "state": state.state
        ]
        if let endReason = state.endReason {
            obj["endReason"] = endReason
        }
        return obj
    }
}

@MainActor
enum AttachmentsTestStack {
    @MainActor
    struct Context {
        let clock: TestEngineClock
        let server: FakeAttemptServer
        let router: AudioServerFixtureRouter
        let tokenStore: InMemoryTokenStore
        let apiClient: MobileAPIClient
        let engine: AttemptEngine
        let connectivity: ConnectivityMonitor
        let networkObserver: AttemptFakeNetworkPathObserver
        let logSink: InMemoryLogSink
        let storeRootURL: URL
        let storeProvider: AccountScopedStoreProvider

        func makeGateway() -> OpenAPIAttemptAudioGateway {
            OpenAPIAttemptAudioGateway(
                attemptId: server.attemptId,
                apiClient: apiClient,
                activeSessionIdProvider: { @MainActor [weak engine] in
                    engine?.state.activeSessionId
                }
            )
        }

        func makeController(
            assetId: String = "00000000-0000-7000-8000-000000000008",
            partId: String? = nil,
            fileName: String = "listening.wav",
            playbackEngine: any AudioPlaybackEngine,
            requestIdGenerator: @escaping () -> String = { UUID().uuidString.lowercased() },
            progressInterval: Duration = .seconds(10),
            positionPollInterval: Duration = .milliseconds(250),
            externalDisabled: Bool = false,
            registry: AttemptAudioSessionRegistry? = nil
        ) -> AttemptAudioController {
            AttemptAudioController(
                attemptId: server.attemptId,
                assetId: assetId,
                partId: partId ?? server.partId(at: 0),
                fileName: fileName,
                engine: engine,
                gateway: makeGateway(),
                playbackEngine: playbackEngine,
                clock: clock,
                requestIdGenerator: requestIdGenerator,
                progressInterval: progressInterval,
                positionPollInterval: positionPollInterval,
                externalDisabled: externalDisabled,
                registry: registry ?? AttemptAudioSessionRegistry()
            )
        }

        func cleanup() {
            engine.dispose()
            try? FileManager.default.removeItem(at: storeRootURL)
        }
    }

    static func make(
        initialStateVariant: String = "in-progress-shared",
        initialOnline: Bool = true,
        environment: BuildEnvironment = .dev,
        accessToken: String = "audio-test-access-token",
        sessionId: String = "audio-test-session-1",
        autoStart: Bool = true,
        mutateInitialState: ((inout [String: Any]) -> Void)? = nil
    ) throws -> Context {
        let clock = TestEngineClock()
        let server = try FakeAttemptServer(
            initialStateVariant: initialStateVariant,
            clock: clock,
            mutateInitialState: mutateInitialState
        )
        let router = AudioServerFixtureRouter(server: server, clock: clock)
        let (storeProvider, store, storeRootURL) = try AttemptEngineTestHarness.makeStore(accountId: "account-audio-1")
        let (connectivity, observer) = AttemptEngineTestHarness.makeConnectivity(initialOnline: initialOnline)
        let tokenStore = InMemoryTokenStore(
            initialTokens: SessionTokens(
                accessToken: accessToken,
                refreshToken: "audio-test-refresh-token",
                accessExpiresIn: 300,
                refreshExpiresIn: 604_800,
                mustChangePassword: false,
                username: "student.audio",
                accountId: "account-audio-1"
            )
        )
        let logSink = InMemoryLogSink()
        let logger = RedactingAppLogger(environment: environment, sink: logSink)
        let apiClient = MobileAPIClient(
            serverURL: URL(string: "https://api.thi360.test")!,
            tokenStore: tokenStore,
            transport: server.transport,
            logger: logger,
            localeProvider: { .vi }
        )
        let attemptAPI = AttemptAPI(apiClient: apiClient)
        let engine = AttemptEngine(
            attemptId: server.attemptId,
            accountId: "account-audio-1",
            api: attemptAPI,
            store: store,
            clock: clock,
            connectivity: connectivity,
            sessionIdGenerator: { sessionId },
            autoStart: autoStart
        )
        return Context(
            clock: clock,
            server: server,
            router: router,
            tokenStore: tokenStore,
            apiClient: apiClient,
            engine: engine,
            connectivity: connectivity,
            networkObserver: observer,
            logSink: logSink,
            storeRootURL: storeRootURL,
            storeProvider: storeProvider
        )
    }

    static func saveSnapshotIfRequested(name: String, image: UIImage, data: Data, testCase: XCTestCase) {
        let attachment = XCTAttachment(image: image)
        attachment.name = "\(name).png"
        attachment.lifetime = .keepAlways
        testCase.add(attachment)

        if let dirPath = ProcessInfo.processInfo.environment["THI360_ATTACHMENTS_SNAPSHOT_DIR"], !dirPath.isEmpty {
            let dirURL = URL(fileURLWithPath: dirPath, isDirectory: true)
            try? FileManager.default.createDirectory(at: dirURL, withIntermediateDirectories: true)
            try? data.write(to: dirURL.appendingPathComponent("\(name).png"))
        }
    }
}
