import Foundation
import XCTest
@testable import Thi360

@MainActor
final class PlayerAudioStopOrderTests: XCTestCase {
    func testAudioAttachmentStopsStrictlyBeforeHttpFinishPartAndSubmit() async throws {
        let perPart = try PlayerTestHarness(initialStateVariant: "in-progress-per-part")
        defer { perPart.dispose() }
        await perPart.settle()

        let lock = NSLock()
        var finishTimeline: [String] = []

        let mockHost1 = MockPlayerAttachmentHost()
        mockHost1.onBeforeCloseHook = { target in
            lock.withLock {
                if case let .part(partId) = target {
                    finishTimeline.append("audioStop:part:\(partId)")
                }
            }
        }
        perPart.server.onBeforeHandle = { call in
            if call.endpoint == .finishPart {
                lock.withLock {
                    finishTimeline.append("http:finishPart")
                }
            }
        }

        let observer1 = PlayerEventObserver()
        observer1.bind(
            engine: perPart.engine,
            attachmentHost: mockHost1,
            onPartClosed: { _ in },
            onAttemptClosed: { _ in },
            onSessionLost: {}
        )
        defer { observer1.stop() }
        await Task.yield()

        let part0 = perPart.server.partId(at: 0)
        perPart.engine.finishPart(partId: part0)
        await perPart.settle()

        let capturedFinish = lock.withLock { finishTimeline }
        XCTAssertEqual(
            capturedFinish,
            ["audioStop:part:\(part0)", "http:finishPart"],
            "Attachment host must receive stop strictly BEFORE HTTP POST .../finish"
        )

        let shared = try PlayerTestHarness(initialStateVariant: "in-progress-shared")
        defer { shared.dispose() }
        await shared.settle()

        var submitTimeline: [String] = []
        let mockHost2 = MockPlayerAttachmentHost()
        mockHost2.onBeforeCloseHook = { target in
            lock.withLock {
                if case .attempt = target {
                    submitTimeline.append("audioStop:attempt")
                }
            }
        }
        shared.server.onBeforeHandle = { call in
            if call.endpoint == .submit {
                lock.withLock {
                    submitTimeline.append("http:submit")
                }
            }
        }

        let observer2 = PlayerEventObserver()
        observer2.bind(
            engine: shared.engine,
            attachmentHost: mockHost2,
            onPartClosed: { _ in },
            onAttemptClosed: { _ in },
            onSessionLost: {}
        )
        defer { observer2.stop() }
        await Task.yield()

        shared.engine.submit()
        await shared.settle()

        let capturedSubmit = lock.withLock { submitTimeline }
        XCTAssertEqual(
            capturedSubmit,
            ["audioStop:attempt", "http:submit"],
            "Attachment host must receive stop strictly BEFORE HTTP POST .../submit"
        )
    }
}
