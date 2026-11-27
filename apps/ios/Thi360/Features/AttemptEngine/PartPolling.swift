import Foundation

@MainActor
final class PartPolling {
    static let choiceTimeoutInitialDelay: Duration = .milliseconds(500)
    static let choiceTimeoutRepeatDelay: Duration = .seconds(2)
    static let generatingInterval: Duration = .milliseconds(1500)

    enum Mode: Equatable {
        case idle
        case generating(partId: PartID)
        case choiceExpired(partId: PartID)
    }

    private let clock: any EngineClock
    private let onPoll: @MainActor () async -> Void

    private(set) var mode: Mode = .idle
    private var pollTask: Task<Void, Never>?
    private var disposed = false

    init(
        clock: any EngineClock,
        onPoll: @escaping @MainActor () async -> Void
    ) {
        self.clock = clock
        self.onPoll = onPoll
    }

    func configure(_ nextMode: Mode) {
        guard !disposed else { return }
        if nextMode == mode {
            return
        }
        stop()
        mode = nextMode
        switch nextMode {
        case .idle:
            return
        case .generating:
            pollTask = Task { @MainActor [weak self] in
                await self?.runGeneratingLoop()
            }
        case .choiceExpired:
            pollTask = Task { @MainActor [weak self] in
                await self?.runChoiceExpiredLoop()
            }
        }
    }

    func stop() {
        pollTask?.cancel()
        pollTask = nil
        mode = .idle
    }

    func dispose() {
        disposed = true
        stop()
    }

    private func runGeneratingLoop() async {
        while !disposed && !Task.isCancelled {
            do {
                try await clock.sleep(for: Self.generatingInterval)
            } catch {
                break
            }
            guard !disposed, !Task.isCancelled else { break }
            await onPoll()
        }
    }

    private func runChoiceExpiredLoop() async {
        do {
            try await clock.sleep(for: Self.choiceTimeoutInitialDelay)
        } catch {
            return
        }
        guard !disposed, !Task.isCancelled else { return }
        await onPoll()

        while !disposed && !Task.isCancelled {
            do {
                try await clock.sleep(for: Self.choiceTimeoutRepeatDelay)
            } catch {
                break
            }
            guard !disposed, !Task.isCancelled else { break }
            await onPoll()
        }
    }
}
