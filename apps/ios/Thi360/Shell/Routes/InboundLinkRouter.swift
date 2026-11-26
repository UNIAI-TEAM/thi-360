import Foundation
import SwiftUI

@MainActor
final class InboundLinkRouter {
    struct RegisteredParser {
        let id: String
        let requiresUnlockedSession: Bool
        let parse: @MainActor (URL, AppEnvironment) -> (@MainActor () -> Void)?
    }

    private struct PendingLink {
        let action: @MainActor () -> Void
    }

    private var parsers: [RegisteredParser] = []
    private var pendingLink: PendingLink?

    private var appStartedHooks: [(id: String, action: @MainActor () async -> Void)] = []
    private var foregroundHooks: [(id: String, action: @MainActor () async -> Void)] = []
    private var hasRunAppStartedHooks = false
    private var pendingAppStarted = false
    private var pendingForeground = false
    private var hasEnteredNonActivePhase = false
    private var currentSessionState: SessionState = .signedOut(reason: .none)

    func register(
        id: String,
        requiresUnlockedSession: Bool = true,
        parser: @escaping @MainActor (URL, AppEnvironment) -> (@MainActor () -> Void)?
    ) {
        parsers.removeAll { $0.id == id }
        parsers.append(
            RegisteredParser(
                id: id,
                requiresUnlockedSession: requiresUnlockedSession,
                parse: parser
            )
        )
    }

    func onAppStarted(id: String, action: @escaping @MainActor () async -> Void) {
        appStartedHooks.removeAll { $0.id == id }
        appStartedHooks.append((id: id, action: action))
    }

    func onForeground(id: String, action: @escaping @MainActor () async -> Void) {
        foregroundHooks.removeAll { $0.id == id }
        foregroundHooks.append((id: id, action: action))
    }

    @discardableResult
    func handleIncomingURL(
        _ url: URL,
        environment: AppEnvironment = .current
    ) -> Bool {
        for entry in parsers where !entry.requiresUnlockedSession {
            if let action = entry.parse(url, environment) {
                action()
                return true
            }
        }

        for entry in parsers where entry.requiresUnlockedSession {
            if let action = entry.parse(url, environment) {
                if currentSessionState == .active {
                    action()
                } else {
                    pendingLink = PendingLink(action: action)
                }
                return true
            }
        }

        return false
    }

    @discardableResult
    func handleUserActivity(
        _ userActivity: NSUserActivity,
        environment: AppEnvironment = .current
    ) -> Bool {
        guard userActivity.activityType == NSUserActivityTypeBrowsingWeb,
              let url = userActivity.webpageURL
        else {
            return false
        }
        return handleIncomingURL(url, environment: environment)
    }

    func notifyAppStarted(sessionState: SessionState) async {
        currentSessionState = sessionState
        if !hasRunAppStartedHooks {
            pendingAppStarted = true
        }
        await flushEligibleWork()
    }

    func notifyScenePhaseChanged(_ phase: ScenePhase, sessionState: SessionState) async {
        currentSessionState = sessionState
        switch phase {
        case .background, .inactive:
            hasEnteredNonActivePhase = true
        case .active:
            if hasEnteredNonActivePhase {
                hasEnteredNonActivePhase = false
                pendingForeground = true
                await flushEligibleWork()
            }
        @unknown default:
            break
        }
    }

    func notifySessionStateChanged(_ newState: SessionState) async {
        currentSessionState = newState
        if newState == .active {
            await flushEligibleWork()
        } else if !newState.isAuthenticated {
            // Do not fire foreground hooks while signed out; keep pendingLink in memory for same-run sign-in.
            pendingForeground = false
        }
    }

    func resetPendingWorkForSignOut() {
        pendingForeground = false
    }

    var hasPendingLink: Bool {
        pendingLink != nil
    }

    private func flushEligibleWork() async {
        guard currentSessionState == .active else { return }

        if let pending = pendingLink {
            pendingLink = nil
            pending.action()
        }

        if pendingAppStarted && !hasRunAppStartedHooks {
            pendingAppStarted = false
            hasRunAppStartedHooks = true
            let hooks = appStartedHooks
            for hook in hooks {
                await hook.action()
            }
        }

        if pendingForeground {
            pendingForeground = false
            let hooks = foregroundHooks
            for hook in hooks {
                await hook.action()
            }
        }
    }
}
