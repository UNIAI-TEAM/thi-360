import Foundation
import SwiftUI

@MainActor
final class AttemptEngineRegistry {
    static let foregroundHookId = "attemptEngine.foreground"

    private var engines: [String: AttemptEngine] = [:]

    func engine(
        for attemptId: String,
        accountId: String,
        factory: () -> AttemptEngine
    ) -> AttemptEngine {
        if let existing = engines[attemptId] {
            if existing.accountId == accountId {
                return existing
            }
            existing.dispose()
            engines.removeValue(forKey: attemptId)
        }
        let created = factory()
        engines[attemptId] = created
        return created
    }

    func existingEngine(for attemptId: String) -> AttemptEngine? {
        engines[attemptId]
    }

    var activeAttemptIds: Set<String> {
        Set(engines.keys)
    }

    func release(_ attemptId: String) {
        guard let removed = engines.removeValue(forKey: attemptId) else { return }
        removed.dispose()
    }

    func releaseAll(forAccount accountId: String? = nil) {
        let targets: [String]
        if let accountId {
            targets = engines.compactMap { key, engine in
                engine.accountId == accountId ? key : nil
            }
        } else {
            targets = Array(engines.keys)
        }
        for attemptId in targets {
            release(attemptId)
        }
    }

    func setForeground(_ isForeground: Bool) {
        let activeEngines = Array(engines.values)
        for engine in activeEngines {
            engine.setForeground(isForeground)
        }
    }

    func bindForegroundHook(to inboundLinkRouter: InboundLinkRouter) {
        inboundLinkRouter.onForeground(id: Self.foregroundHookId) { [weak self] in
            self?.setForeground(true)
        }
    }

    func handleScenePhaseChange(_ phase: ScenePhase) {
        switch phase {
        case .background, .inactive:
            setForeground(false)
        case .active:
            break
        @unknown default:
            break
        }
    }

    func makeCleanupParticipant(storeProvider: AccountScopedStoreProvider) -> AttemptDataCleanup {
        AttemptDataCleanup(
            storeProvider: storeProvider,
            onAccountLogout: { [weak self] accountId in
                await MainActor.run {
                    self?.releaseAll(forAccount: accountId)
                }
            }
        )
    }
}

struct AttemptEngineLifecycleModifier: ViewModifier {
    let registry: AttemptEngineRegistry
    let inboundLinkRouter: InboundLinkRouter
    @Environment(\.scenePhase) private var scenePhase

    func body(content: Content) -> some View {
        content
            .onAppear {
                registry.bindForegroundHook(to: inboundLinkRouter)
            }
            .onChange(of: scenePhase) { _, newPhase in
                registry.handleScenePhaseChange(newPhase)
            }
    }
}

extension View {
    func bindAttemptEngineLifecycle(
        registry: AttemptEngineRegistry,
        inboundLinkRouter: InboundLinkRouter
    ) -> some View {
        modifier(
            AttemptEngineLifecycleModifier(
                registry: registry,
                inboundLinkRouter: inboundLinkRouter
            )
        )
    }
}
