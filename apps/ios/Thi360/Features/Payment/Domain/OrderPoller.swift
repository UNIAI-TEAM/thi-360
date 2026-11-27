import Foundation
import SwiftUI

enum OrderPollDirective: Equatable, Sendable {
    case `continue`
    case stop
}

@MainActor
final class OrderPoller {
    nonisolated static let defaultInterval: TimeInterval = 2.0
    nonisolated static let defaultWindow: TimeInterval = 60.0

    typealias SleepOperation = @MainActor (TimeInterval) async throws -> Void

    private let interval: TimeInterval
    private let window: TimeInterval
    private let clock: any AppClock
    private let sleepOperation: SleepOperation

    private var startedAt: Date?
    private var activeTask: Task<Void, Never>?
    private var isActivePhase = true
    private var isEnabled = false
    private var isCallInFlight = false
    private var pendingImmediatePoll = false
    private var generation = 0
    private var pollAction: (@MainActor () async -> OrderPollDirective)?
    private var timeoutAction: (@MainActor () -> Void)?

    init(
        interval: TimeInterval = OrderPoller.defaultInterval,
        window: TimeInterval = OrderPoller.defaultWindow,
        clock: any AppClock = SystemClock(),
        sleep: @escaping SleepOperation = { seconds in
            let nanos = UInt64(max(0, seconds) * 1_000_000_000)
            try await Task.sleep(nanoseconds: nanos)
        }
    ) {
        self.interval = interval
        self.window = window
        self.clock = clock
        self.sleepOperation = sleep
    }

    var isRunning: Bool {
        isEnabled
    }

    var hasInFlightCall: Bool {
        isCallInFlight
    }

    func start(
        onPoll: @escaping @MainActor () async -> OrderPollDirective,
        onTimeout: @escaping @MainActor () -> Void
    ) {
        pollAction = onPoll
        timeoutAction = onTimeout
        if !isEnabled {
            startedAt = clock.now
            isEnabled = true
        }
        if isActivePhase {
            scheduleLoop(immediateFirstPoll: false)
        }
    }

    func restartWindow(
        onPoll: @escaping @MainActor () async -> OrderPollDirective,
        onTimeout: @escaping @MainActor () -> Void
    ) {
        generation &+= 1
        activeTask?.cancel()
        activeTask = nil
        pollAction = onPoll
        timeoutAction = onTimeout
        startedAt = clock.now
        isEnabled = true
        if isCallInFlight {
            pendingImmediatePoll = true
        } else if isActivePhase {
            scheduleLoop(immediateFirstPoll: true)
        }
    }

    func setScenePhase(_ phase: ScenePhase) {
        let shouldBeActive = (phase == .active)
        guard shouldBeActive != isActivePhase else { return }
        isActivePhase = shouldBeActive
        if !shouldBeActive {
            generation &+= 1
            activeTask?.cancel()
            activeTask = nil
            pendingImmediatePoll = false
        } else if isEnabled {
            if isCallInFlight {
                pendingImmediatePoll = true
            } else {
                scheduleLoop(immediateFirstPoll: true)
            }
        }
    }

    func stop() {
        generation &+= 1
        isEnabled = false
        pendingImmediatePoll = false
        activeTask?.cancel()
        activeTask = nil
        startedAt = nil
        pollAction = nil
        timeoutAction = nil
    }

    private func scheduleLoop(immediateFirstPoll: Bool) {
        guard activeTask == nil, isEnabled, isActivePhase, !isCallInFlight else { return }
        let loopGeneration = generation

        activeTask = Task { @MainActor [weak self] in
            guard let self else { return }
            defer {
                if self.generation == loopGeneration {
                    self.activeTask = nil
                }
            }

            var doImmediate = immediateFirstPoll
            while !Task.isCancelled && self.generation == loopGeneration && self.isEnabled && self.isActivePhase {
                let start = self.startedAt ?? self.clock.now
                self.startedAt = start

                if !doImmediate {
                    let elapsedBefore = max(0, self.clock.now.timeIntervalSince(start))
                    if elapsedBefore >= self.window {
                        self.finishTimedOut()
                        return
                    }
                    let wait = min(self.interval, self.window - elapsedBefore)
                    do {
                        try await self.sleepOperation(wait)
                    } catch {
                        return
                    }
                    if Task.isCancelled || self.generation != loopGeneration || !self.isEnabled || !self.isActivePhase {
                        return
                    }
                    let elapsedAfterWait = max(0, self.clock.now.timeIntervalSince(start))
                    if elapsedAfterWait >= self.window {
                        self.finishTimedOut()
                        return
                    }
                }
                doImmediate = false

                guard let poll = self.pollAction else {
                    return
                }
                self.isCallInFlight = true
                let directive = await poll()
                self.isCallInFlight = false

                if self.pendingImmediatePoll && self.isEnabled && self.isActivePhase {
                    self.pendingImmediatePoll = false
                    self.scheduleLoop(immediateFirstPoll: true)
                    return
                }

                if Task.isCancelled || self.generation != loopGeneration {
                    return
                }
                if directive == .stop || !self.isEnabled {
                    self.stop()
                    return
                }

                let elapsedAfterCall = max(0, self.clock.now.timeIntervalSince(start))
                if elapsedAfterCall >= self.window {
                    self.finishTimedOut()
                    return
                }
            }
        }
    }

    private func finishTimedOut() {
        let callback = timeoutAction
        isEnabled = false
        pendingImmediatePoll = false
        activeTask = nil
        callback?()
    }
}
