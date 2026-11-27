import Foundation
import XCTest
@testable import Thi360

final class FakeNetworkPathObserver: NetworkPathObserving, @unchecked Sendable {
    private let lock = NSLock()
    private var handler: (@Sendable (Bool) -> Void)?

    func start(onUpdate: @escaping @Sendable (Bool) -> Void) {
        lock.lock()
        handler = onUpdate
        lock.unlock()
    }

    func stop() {
        lock.lock()
        handler = nil
        lock.unlock()
    }

    func emit(isOnline: Bool) {
        let active: (@Sendable (Bool) -> Void)? = {
            lock.lock()
            defer { lock.unlock() }
            return handler
        }()
        active?(isOnline)
    }
}

@MainActor
final class ConnectivityMonitorTests: XCTestCase {
    func testEmitsOnlineStateAndNotifiesOnlyOnStateChange() async throws {
        let observer = FakeNetworkPathObserver()
        let monitor = ConnectivityMonitor(initialIsOnline: true, observer: observer, autoStart: false)

        var transitions: [Bool] = []
        _ = monitor.addObserver { isOnline in
            transitions.append(isOnline)
        }

        monitor.start()
        XCTAssertTrue(monitor.isOnline)
        XCTAssertTrue(transitions.isEmpty)

        observer.emit(isOnline: true)
        try await Task.sleep(nanoseconds: 30_000_000)
        XCTAssertTrue(transitions.isEmpty, "Repeat same state must not notify observers")

        observer.emit(isOnline: false)
        try await Task.sleep(nanoseconds: 30_000_000)
        XCTAssertFalse(monitor.isOnline)
        XCTAssertEqual(transitions, [false])

        observer.emit(isOnline: true)
        try await Task.sleep(nanoseconds: 30_000_000)
        XCTAssertTrue(monitor.isOnline)
        XCTAssertEqual(transitions, [false, true])

        monitor.stop()
    }
}
