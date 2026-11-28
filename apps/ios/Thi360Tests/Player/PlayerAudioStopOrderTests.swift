import Foundation
import XCTest
@testable import Thi360

private final class LockedTimeline: @unchecked Sendable {
    private let lock = NSLock()
    private var entries: [String] = []

    func append(_ entry: String) {
        lock.withLock {
            entries.append(entry)
        }
    }

    var snapshot: [String] {
        lock.withLock { entries }
    }
}

@MainActor
final class PlayerAudioStopOrderTests: XCTestCase {
    func testAudioAttachmentStopsStrictlyBeforeHttpFinishPartAndSubmit() async throws {
        let perPart = try PlayerTestHarness(initialStateVariant: "in-progress-per-part")
        defer { perPart.dispose() }
        await perPart.settle()

        let finishTimeline = LockedTimeline()

        let mockHost1 = MockPlayerAttachmentHost()
        mockHost1.onBeforeCloseHook = { target in
            if case let .part(partId) = target {
                finishTimeline.append("audioStop:part:\(partId)")
            }
        }
        perPart.server.onBeforeHandle = { call in
            if call.endpoint == .finishPart {
                finishTimeline.append("http:finishPart")
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

        XCTAssertEqual(
            finishTimeline.snapshot,
            ["audioStop:part:\(part0)", "http:finishPart"],
            "Attachment host must receive stop strictly BEFORE HTTP POST .../finish"
        )

        let shared = try PlayerTestHarness(initialStateVariant: "in-progress-shared")
        defer { shared.dispose() }
        await shared.settle()

        let submitTimeline = LockedTimeline()
        let mockHost2 = MockPlayerAttachmentHost()
        mockHost2.onBeforeCloseHook = { target in
            if case .attempt = target {
                submitTimeline.append("audioStop:attempt")
            }
        }
        shared.server.onBeforeHandle = { call in
            if call.endpoint == .submit {
                submitTimeline.append("http:submit")
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

        XCTAssertEqual(
            submitTimeline.snapshot,
            ["audioStop:attempt", "http:submit"],
            "Attachment host must receive stop strictly BEFORE HTTP POST .../submit"
        )
    }
}
