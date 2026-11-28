import Foundation
import XCTest
@testable import Thi360

@MainActor
final class AttachmentStorageTests: XCTestCase {
    private let assetId = "00000000-0000-7000-8000-000000000008"
    private let pdfAssetId = "00000000-0000-7000-8000-000000000099"

    override func tearDown() {
        RecordingAssetURLProtocol.reset()
        super.tearDown()
    }

    func testNoSharedLocationAndAudioStaysStrictlyInMemoryUntilRelease() async throws {
        let fm = FileManager.default
        let docsBefore = Self.listFiles(in: fm.urls(for: .documentDirectory, in: .userDomainMask).first)
        let cachesBefore = Self.listFiles(in: fm.urls(for: .cachesDirectory, in: .userDomainMask).first)
        let tmpBefore = Self.listFiles(in: fm.temporaryDirectory)

        let coordinator = AudioSessionCoordinator(
            audioSession: MockAudioSessionController(),
            notificationCenter: NotificationCenter(),
            commandCenter: .shared(),
            nowPlayingCenter: .default()
        )
        let stack = try AttachmentsTestStack.make()
        defer { stack.cleanup() }
        try stack.router.configureAsset(
            assetId: assetId,
            maxPlays: 2,
            durationMs: 35_000,
            audioData: try AttachmentsTestResources.silence35sWav()
        )

        let ready = await stack.clock.waitUntil { stack.engine.state.activeSessionId != nil }
        XCTAssertTrue(ready)

        let realEngine = AVAudioPlayerPlaybackEngine(coordinator: coordinator)
        let controller = stack.makeController(assetId: assetId, fileName: "in-memory.wav", playbackEngine: realEngine)
        defer { controller.close() }

        controller.onPrimaryAction()
        let playing = await stack.clock.waitUntil {
            if case .playing = controller.uiState.playbackState { return true }
            return false
        }
        XCTAssertTrue(playing)
        XCTAssertTrue(controller.hasInMemoryAudioData)

        // Leaving screen drops audio bytes from memory immediately.
        controller.onLeaveScreen()
        XCTAssertFalse(controller.isActivelyPlayingMedia)
        XCTAssertFalse(controller.hasInMemoryAudioData, "In-memory audio Data must be released when leaving screen (AC-21)")

        let docsAfter = Self.listFiles(in: fm.urls(for: .documentDirectory, in: .userDomainMask).first)
        let cachesAfter = Self.listFiles(in: fm.urls(for: .cachesDirectory, in: .userDomainMask).first)
        let tmpAfter = Self.listFiles(in: fm.temporaryDirectory)

        XCTAssertEqual(docsAfter.subtracting(docsBefore), [], "Audio playback must never write files to Documents/")
        let newCacheAudioFiles = cachesAfter.subtracting(cachesBefore).filter {
            $0.hasSuffix(".wav") || $0.hasSuffix(".mp3") || $0.hasSuffix(".m4a") || $0.contains("AttemptAssets")
        }
        XCTAssertEqual(newCacheAudioFiles, [], "Audio playback must never write audio files to Caches/")
        let newTmpAudioFiles = tmpAfter.subtracting(tmpBefore).filter {
            $0.hasSuffix(".wav") || $0.hasSuffix(".mp3") || $0.hasSuffix(".m4a")
        }
        XCTAssertEqual(newTmpAudioFiles, [], "Audio playback must never write audio files to tmp/")
    }

    func testLogoutCleanupStopsActiveAudioAndPurgesTempViewerFiles() async throws {
        let registry = AttemptAudioSessionRegistry.shared
        let coordinator = AudioSessionCoordinator(
            audioSession: MockAudioSessionController(),
            notificationCenter: NotificationCenter(),
            commandCenter: .shared(),
            nowPlayingCenter: .default()
        )
        let stack = try AttachmentsTestStack.make()
        defer { stack.cleanup() }
        try stack.router.configureAsset(
            assetId: assetId,
            maxPlays: 2,
            durationMs: 35_000,
            audioData: try AttachmentsTestResources.silence35sWav()
        )
        let ready = await stack.clock.waitUntil { stack.engine.state.activeSessionId != nil }
        XCTAssertTrue(ready)

        let realEngine = AVAudioPlayerPlaybackEngine(coordinator: coordinator)
        let controller = stack.makeController(
            assetId: assetId,
            fileName: "logout-stop.wav",
            playbackEngine: realEngine,
            registry: registry
        )
        defer { controller.close() }

        controller.onPrimaryAction()
        let playing = await stack.clock.waitUntil {
            if case .playing = controller.uiState.playbackState { return true }
            return false
        }
        XCTAssertTrue(playing)
        XCTAssertTrue(controller.hasInMemoryAudioData)

        let tempCaches = FileManager.default.temporaryDirectory
            .appendingPathComponent("thi360-logout-attach-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: tempCaches) }

        let pdfBytes = try AttachmentsTestResources.pdfData()
        RecordingAssetURLProtocol.reset()
        RecordingAssetURLProtocol.handler = { req in
            let response = HTTPURLResponse(
                url: req.url!,
                statusCode: 200,
                httpVersion: "HTTP/1.1",
                headerFields: ["Content-Type": "application/pdf"]
            )!
            return (response, pdfBytes)
        }
        let session = AttemptAssetSource.makeNoDiskCacheSession(protocolClasses: [RecordingAssetURLProtocol.self])
        let assetSource = AttemptAssetSource(
            attemptId: stack.server.attemptId,
            serverURL: URL(string: "https://api.thi360.test")!,
            tokenStore: stack.tokenStore,
            refresher: stack.apiClient.refresher,
            session: session,
            cachesDirectoryURL: tempCaches
        )
        let opener = AttachmentOpener(assetSource: assetSource, registry: registry)
        defer { opener.close() }

        let opened = await opener.open(
            QuestionAttachment(assetId: pdfAssetId, fileName: "bang-so-lieu.pdf", mime: "application/pdf", url: nil)
        )
        XCTAssertTrue(opened)
        let tempDir = AttemptAssetSource.defaultTempDirectoryURL(cachesDirectoryURL: tempCaches)
        XCTAssertEqual((try? FileManager.default.contentsOfDirectory(atPath: tempDir.path))?.count ?? 0, 1)

        // Default SessionCleanup includes AttemptAssetTempCleanupParticipant() + AttemptAudioCleanupParticipant() (P2-D-65).
        let cleanup = SessionCleanup(storeProvider: stack.storeProvider)
        await cleanup.runLogout(accountId: "account-audio-1")

        XCTAssertFalse(controller.isActivelyPlayingMedia, "Logout cleanup must immediately stop active audio playback")
        XCTAssertFalse(controller.hasInMemoryAudioData, "Logout cleanup must drop in-memory audio Data")
        XCTAssertNil(opener.activePreview, "Logout cleanup must dismiss active viewer preview")
        XCTAssertEqual(
            (try? FileManager.default.contentsOfDirectory(atPath: tempDir.path))?.count ?? 0,
            0,
            "Logout cleanup must purge all temp viewer files in Caches/AttemptAssets/"
        )
    }

    func testDemoLogsRedactBearerAndAttemptSessionOnAudioRequests() async throws {
        let tokenSecret = "demo-audio-bearer-secret-77889"
        let sessionSecret = "demo-audio-session-secret-66554"

        let stack = try AttachmentsTestStack.make(
            environment: .demo,
            accessToken: tokenSecret,
            sessionId: sessionSecret
        )
        defer { stack.cleanup() }
        try stack.router.configureAsset(assetId: assetId, maxPlays: 2, durationMs: 20_000)

        let ready = await stack.clock.waitUntil { stack.engine.state.activeSessionId == sessionSecret }
        XCTAssertTrue(ready)

        let fakePlayback = FakeAudioPlaybackEngine(durationMs: 20_000, clock: stack.clock)
        let controller = stack.makeController(assetId: assetId, playbackEngine: fakePlayback)
        defer { controller.close() }

        controller.onPrimaryAction()
        let playing = await stack.clock.waitUntil {
            if case .playing = controller.uiState.playbackState { return true }
            return false
        }
        XCTAssertTrue(playing)

        await stack.clock.advance(by: .seconds(10))
        let progressed = await stack.clock.waitUntil { stack.router.callCount(for: .progress) >= 1 }
        XCTAssertTrue(progressed)

        fakePlayback.simulateCompletion()
        let ended = await stack.clock.waitUntil { stack.router.callCount(for: .end) == 1 }
        XCTAssertTrue(ended)

        XCTAssertFalse(stack.logSink.recordedEntries.isEmpty, "Expected HTTP logs to be captured for audio calls")
        for entry in stack.logSink.recordedEntries {
            XCTAssertFalse(entry.message.contains(tokenSecret), "Demo log leaked access token: \(entry.message)")
            XCTAssertFalse(entry.message.contains(sessionSecret), "Demo log leaked X-Attempt-Session: \(entry.message)")
        }
    }

    private static func listFiles(in root: URL?) -> Set<String> {
        guard let root, let enumerator = FileManager.default.enumerator(atPath: root.path) else {
            return []
        }
        var results = Set<String>()
        for case let item as String in enumerator {
            results.insert(item)
        }
        return results
    }
}
