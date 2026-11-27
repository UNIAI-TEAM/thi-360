import Foundation
@testable import Thi360

final class TestEngineClock: EngineClock, @unchecked Sendable {
    private struct Waiter {
        let id: UUID
        let wakeAt: ContinuousClock.Instant
        let continuation: CheckedContinuation<Void, Error>
    }

    private let lock = NSLock()
    private var currentNow: ContinuousClock.Instant
    private var waiters: [Waiter] = []
    private var recordedSleeps: [Duration] = []

    init(initialNow: ContinuousClock.Instant = ContinuousClock().now) {
        self.currentNow = initialNow
    }

    private func withLock<T>(_ body: () -> T) -> T {
        lock.lock()
        defer { lock.unlock() }
        return body()
    }

    var now: ContinuousClock.Instant {
        withLock { currentNow }
    }

    var pendingSleepCount: Int {
        withLock { waiters.count }
    }

    var sleepDurationsRecorded: [Duration] {
        withLock { recordedSleeps }
    }

    func recordedSleepCount(for duration: Duration) -> Int {
        withLock { recordedSleeps.filter { $0 == duration }.count }
    }

    func sleep(for duration: Duration) async throws {
        if Task.isCancelled {
            throw CancellationError()
        }
        let target: ContinuousClock.Instant? = withLock {
            recordedSleeps.append(duration)
            if duration <= .zero {
                return nil
            }
            return currentNow + duration
        }
        guard let target else {
            await Task.yield()
            return
        }

        let waiterId = UUID()
        try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
                let decision: Int = self.withLock {
                    if Task.isCancelled {
                        return 1
                    }
                    if target <= self.currentNow {
                        return 2
                    }
                    self.waiters.append(Waiter(id: waiterId, wakeAt: target, continuation: continuation))
                    self.waiters.sort { $0.wakeAt < $1.wakeAt }
                    return 0
                }
                if decision == 1 {
                    continuation.resume(throwing: CancellationError())
                } else if decision == 2 {
                    continuation.resume()
                }
            }
        } onCancel: {
            self.cancelWaiter(id: waiterId)
        }
    }

    func advance(by duration: Duration, settleYields: Int = 24) async {
        await settle(yields: settleYields)
        try? await Task.sleep(nanoseconds: 2_000_000)
        await settle(yields: 8)

        let target: ContinuousClock.Instant = withLock {
            currentNow + duration
        }

        while true {
            let nextBatch: [CheckedContinuation<Void, Error>] = withLock {
                guard let earliest = waiters.first, earliest.wakeAt <= target else {
                    currentNow = target
                    return []
                }
                let stepTime = earliest.wakeAt
                currentNow = stepTime
                var ready: [CheckedContinuation<Void, Error>] = []
                while let first = waiters.first, first.wakeAt <= stepTime {
                    ready.append(waiters.removeFirst().continuation)
                }
                return ready
            }

            if nextBatch.isEmpty {
                break
            }
            for cont in nextBatch {
                cont.resume()
            }
            await settle(yields: settleYields)
            try? await Task.sleep(nanoseconds: 3_000_000)
            await settle(yields: 12)
        }

        await settle(yields: settleYields)
        try? await Task.sleep(nanoseconds: 2_000_000)
        await settle(yields: 8)
    }

    func settle(yields: Int = 24) async {
        for _ in 0..<yields {
            await Task.yield()
        }
    }

    func waitUntil(
        timeoutSeconds: TimeInterval = 10.0,
        condition: @MainActor () -> Bool
    ) async -> Bool {
        let deadline = Date().addingTimeInterval(timeoutSeconds)
        while Date() < deadline {
            if await MainActor.run(body: condition) {
                return true
            }
            await Task.yield()
            try? await Task.sleep(nanoseconds: 2_000_000)
        }
        return await MainActor.run(body: condition)
    }

    private func cancelWaiter(id: UUID) {
        let removed: CheckedContinuation<Void, Error>? = withLock {
            guard let index = waiters.firstIndex(where: { $0.id == id }) else { return nil }
            return waiters.remove(at: index).continuation
        }
        removed?.resume(throwing: CancellationError())
    }
}

final class AttemptFakeNetworkPathObserver: NetworkPathObserving, @unchecked Sendable {
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

    func emit(_ online: Bool) {
        lock.lock()
        let active = handler
        lock.unlock()
        active?(online)
    }
}

actor AttemptTestGate {
    private var enteredCount = 0
    private var releasedCount = 0
    private var enterWaiters: [(Int, CheckedContinuation<Void, Never>)] = []
    private var releaseWaiters: [CheckedContinuation<Void, Never>] = []

    func enterAndWait() async {
        enteredCount += 1
        let ready = enterWaiters.filter { enteredCount >= $0.0 }
        enterWaiters.removeAll { enteredCount >= $0.0 }
        for (_, cont) in ready {
            cont.resume()
        }
        if releasedCount > 0 {
            releasedCount -= 1
            return
        }
        await withCheckedContinuation { cont in
            releaseWaiters.append(cont)
        }
    }

    func waitUntilEntered(count: Int = 1) async {
        if enteredCount >= count {
            return
        }
        await withCheckedContinuation { cont in
            enterWaiters.append((count, cont))
        }
    }

    func releaseNext() {
        if !releaseWaiters.isEmpty {
            releaseWaiters.removeFirst().resume()
        } else {
            releasedCount += 1
        }
    }

    func releaseAll() {
        let pending = releaseWaiters
        releaseWaiters.removeAll()
        for cont in pending {
            cont.resume()
        }
    }
}
