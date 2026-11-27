import Foundation
import SwiftUI

extension AppRouter {
    func path(for destination: Destination) -> [AppRoute] {
        paths[destination] ?? []
    }

    func pathBinding(for destination: Destination) -> Binding<[AppRoute]> {
        Binding(
            get: { self.paths[destination] ?? [] },
            set: { newPath in
                self.setPath(newPath, for: destination)
            }
        )
    }

    func setPath(_ newPath: [AppRoute], for destination: Destination) {
        paths[destination] = newPath
        var active = Set(newPath)
        if let attempt = presentedAttempt, attempt.originDestination == destination {
            active.insert(.attempt(attemptId: attempt.attemptId))
        }
        routeStateStore.removeStates(for: destination, notIn: active)
    }

    func reloadCount(for destination: Destination) -> Int {
        reloadTokens[destination] ?? 0
    }

    func openProduct(code: String) {
        push(.product(code: code))
    }

    func openCheckout(code: String, seenVersionNo: Int? = nil, seenPrice: Int? = nil) {
        push(.checkout(code: code, seenVersionNo: seenVersionNo, seenPrice: seenPrice))
    }

    func openPaymentResult(_ target: PaymentResultTarget) {
        push(.paymentResult(target))
    }

    func openOrders() {
        push(.orders)
    }

    func openOrder(orderCode: String) {
        push(.order(orderCode: orderCode))
    }

    func openPrepare(entitlementId: String) {
        push(.prepare(entitlementId: entitlementId))
    }

    func openResult(attemptId: String) {
        push(.result(attemptId: attemptId))
    }

    func openReview(attemptId: String) {
        push(.review(attemptId: attemptId))
    }

    func openProfile() {
        push(.profile)
    }

    func openProfileVerifyEmail() {
        push(.profileVerifyEmail)
    }

    func openDeleteAccount() {
        push(.deleteAccount)
    }

    func openLegal(_ id: LegalId) {
        push(.legal(id))
    }

    func openHistory(resetFilters: Bool = false) {
        if presentedAttempt != nil {
            closeAttempt()
        }
        historyResetFilters = resetFilters
        selectedDestination = .history
        if resetFilters {
            setPath([], for: .history)
            reloadTokens[.history, default: 0] += 1
        }
    }

    func openSignedOutLegal(_ id: LegalId) {
        signedOutPath.append(.legal(id))
    }

    func openAttempt(attemptId: String) {
        if let previous = presentedAttempt {
            routeStateStore.removeState(
                for: previous.originDestination,
                route: .attempt(attemptId: previous.attemptId)
            )
        }
        presentedAttempt = AttemptRef(attemptId: attemptId, originDestination: selectedDestination)
    }

    func openAttemptAfterStart(attemptId: String) {
        let origin = selectedDestination
        var stack = paths[origin] ?? []
        let removed = stack.filter { route in
            if case .prepare = route { return true }
            return false
        }
        stack.removeAll { route in
            if case .prepare = route { return true }
            return false
        }
        paths[origin] = stack
        for route in removed {
            routeStateStore.removeState(for: origin, route: route)
        }
        presentedAttempt = AttemptRef(attemptId: attemptId, originDestination: origin)
    }

    func replaceAttempt(attemptId: String) {
        let origin = presentedAttempt?.originDestination ?? selectedDestination
        if let previous = presentedAttempt {
            routeStateStore.removeState(
                for: previous.originDestination,
                route: .attempt(attemptId: previous.attemptId)
            )
        }
        presentedAttempt = AttemptRef(attemptId: attemptId, originDestination: origin)
    }

    func closeAttempt() {
        guard let current = presentedAttempt else { return }
        routeStateStore.removeState(
            for: current.originDestination,
            route: .attempt(attemptId: current.attemptId)
        )
        presentedAttempt = nil
        selectedDestination = current.originDestination
    }

    func openResultFromAttempt(attemptId: String) {
        let origin = presentedAttempt?.originDestination ?? selectedDestination
        if let current = presentedAttempt {
            routeStateStore.removeState(
                for: current.originDestination,
                route: .attempt(attemptId: current.attemptId)
            )
            presentedAttempt = nil
        }

        var stack = paths[origin] ?? []
        let removed = stack.filter(\.isAttemptFlowRoute)
        stack.removeAll(where: \.isAttemptFlowRoute)
        for route in removed {
            routeStateStore.removeState(for: origin, route: route)
        }
        stack.append(.result(attemptId: attemptId))
        paths[origin] = stack
        selectedDestination = origin
    }

    func switchTab(_ destination: Destination, reload: Bool = false) {
        selectedDestination = destination
        if reload {
            if destination == .history {
                historyResetFilters = true
            }
            setPath([], for: destination)
            reloadTokens[destination, default: 0] += 1
        }
    }

    func back() {
        if presentedAttempt != nil {
            closeAttempt()
            return
        }
        var stack = paths[selectedDestination] ?? []
        guard let removed = stack.popLast() else { return }
        paths[selectedDestination] = stack
        routeStateStore.removeState(for: selectedDestination, route: removed)
    }

    private func push(_ route: AppRoute) {
        var stack = paths[selectedDestination] ?? []
        stack.append(route)
        paths[selectedDestination] = stack
    }
}
