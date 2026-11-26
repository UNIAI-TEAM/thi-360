import Foundation
import Network
import Observation

protocol NetworkPathObserving: Sendable {
    func start(onUpdate: @escaping @Sendable (Bool) -> Void)
    func stop()
}

final class NWPathMonitorNetworkObserver: NetworkPathObserving, @unchecked Sendable {
    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "vn.unicom.thi360.connectivity.monitor")

    func start(onUpdate: @escaping @Sendable (Bool) -> Void) {
        monitor.pathUpdateHandler = { path in
            onUpdate(path.status == .satisfied)
        }
        monitor.start(queue: queue)
    }

    func stop() {
        monitor.cancel()
    }
}

@Observable
@MainActor
final class ConnectivityMonitor {
    private(set) var isOnline: Bool

    private let observer: any NetworkPathObserving
    private var listeners: [UUID: @MainActor (Bool) -> Void] = [:]
    private var isStarted = false

    init(
        initialIsOnline: Bool = true,
        observer: any NetworkPathObserving = NWPathMonitorNetworkObserver(),
        autoStart: Bool = true
    ) {
        self.isOnline = initialIsOnline
        self.observer = observer
        if autoStart {
            start()
        }
    }

    func start() {
        guard !isStarted else { return }
        isStarted = true
        observer.start { [weak self] satisfied in
            Task { @MainActor in
                self?.applyUpdate(satisfied)
            }
        }
    }

    func stop() {
        guard isStarted else { return }
        isStarted = false
        observer.stop()
    }

    @discardableResult
    func addObserver(_ listener: @escaping @MainActor (Bool) -> Void) -> UUID {
        let token = UUID()
        listeners[token] = listener
        return token
    }

    func removeObserver(_ token: UUID) {
        listeners.removeValue(forKey: token)
    }

    func applyUpdate(_ online: Bool) {
        let changed = isOnline != online
        isOnline = online
        guard changed else { return }
        let currentListeners = Array(listeners.values)
        for listener in currentListeners {
            listener(online)
        }
    }
}
