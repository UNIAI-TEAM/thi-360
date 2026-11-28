import SwiftUI

enum PrepareRoutes {
    @MainActor
    static func prepare(
        entitlementId: String,
        destination: Destination,
        router: AppRouter,
        preferences: AppPreferences
    ) -> some View {
        PrepareRouteHostView(
            entitlementId: entitlementId,
            destination: destination,
            router: router,
            preferences: preferences
        )
    }
}

private struct PrepareRouteHostView: View {
    let entitlementId: String
    let destination: Destination
    @Bindable var router: AppRouter
    @Bindable var preferences: AppPreferences

    @Environment(\.sessionStore) private var sessionStore
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @State private var hostId = UUID()

    var body: some View {
        let viewModel = PrepareViewModelRegistry.shared.viewModel(
            for: router,
            destination: destination,
            entitlementId: entitlementId,
            sessionStore: sessionStore,
            localeProvider: { preferences.locale },
            onOpenAttempt: { attemptId in
                PrepareViewModelRegistry.shared.evict(
                    router: router,
                    destination: destination,
                    entitlementId: entitlementId
                )
                router.openAttemptAfterStart(attemptId: attemptId)
            }
        )

        PrepareView(
            viewModel: viewModel,
            locale: preferences.locale,
            supportContact: AppEnvironment.current.supportContact,
            usesNavigationBarChrome: true,
            onOpenAttempt: { attemptId in
                PrepareViewModelRegistry.shared.evict(
                    router: router,
                    destination: destination,
                    entitlementId: entitlementId
                )
                router.openAttemptAfterStart(attemptId: attemptId)
            },
            onOpenCheckout: { code in
                router.openCheckout(code: code)
            },
            onOpenMyExams: {
                PrepareViewModelRegistry.shared.evict(
                    router: router,
                    destination: destination,
                    entitlementId: entitlementId
                )
                var currentStack = router.path(for: destination)
                currentStack.removeAll { $0 == .prepare(entitlementId: entitlementId) }
                router.setPath(currentStack, for: destination)
                router.switchTab(.myExams, reload: false)
            }
        )
        .toolbar {
            #if THI360_DEV
            if router.stateProbeEnabled {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        router.toggleForcedSizeClass(current: horizontalSizeClass)
                    } label: {
                        Image(systemName: "rectangle.split.2x1")
                            .frame(minWidth: 44, minHeight: 44)
                    }
                    .accessibilityLabel(ShellStrings.text("shell.probe.toggleSizeClass", locale: preferences.locale))
                    .accessibilityIdentifier("prepare.probe.toggleSizeClass")
                }
            }
            #endif
        }
        .onAppear {
            viewModel.registerVisibleHost(hostId)
            Thi360AppDelegate.refreshSupportedOrientations(for: horizontalSizeClass)
        }
        .onChange(of: horizontalSizeClass) { _, newValue in
            Thi360AppDelegate.refreshSupportedOrientations(for: newValue)
        }
        .onDisappear {
            viewModel.unregisterVisibleHost(hostId)
            let stillOnStack = router.path(for: destination).contains(.prepare(entitlementId: entitlementId))
            if !stillOnStack {
                PrepareViewModelRegistry.shared.evict(
                    router: router,
                    destination: destination,
                    entitlementId: entitlementId
                )
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier(AppRoute.prepare(entitlementId: entitlementId).accessibilityIdentifier)
    }
}
