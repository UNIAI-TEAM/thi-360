import SwiftUI

enum AttemptRoutes {
    @MainActor
    static func attempt(
        attemptId: String,
        destination: Destination,
        router: AppRouter,
        preferences: AppPreferences
    ) -> some View {
        AttemptRouteHostView(
            attemptId: attemptId,
            destination: destination,
            router: router,
            preferences: preferences
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("shell.chromeless.attempt")
    }
}

private struct AttemptRouteHostView: View {
    let attemptId: String
    let destination: Destination
    @Bindable var router: AppRouter
    @Bindable var preferences: AppPreferences

    @Environment(\.sessionStore) private var sessionStore
    @Environment(\.connectivityMonitor) private var connectivityMonitor
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    private var effectiveSizeClass: UserInterfaceSizeClass? {
        if router.stateProbeEnabled, let forced = router.forcedSizeClassForUITest {
            return forced
        }
        return horizontalSizeClass
    }

    var body: some View {
        let runtime = PlayerRuntime.shared
        let engine = runtime.engine(
            for: attemptId,
            sessionStore: sessionStore,
            connectivity: connectivityMonitor
        )
        let startContextStore = sessionStore?.currentAccountStore().map(StartContextStore.init(store:))
        let prepareAPI: (any PrepareAPIClientProtocol)? = sessionStore.map {
            PrepareAPI(apiClient: $0.apiClient)
        }
        let partChoiceLoader: PartChoiceOptionsLoader? = sessionStore.map { store in
            PartChoiceOptionsLoader(
                startContextStore: startContextStore,
                api: OpenAPIPartChoiceMetadataAPI(apiClient: store.apiClient)
            )
        }
        let assets = runtime.assetSource(
            for: attemptId,
            sessionStore: sessionStore,
            localeProvider: { @MainActor in preferences.locale }
        )
        let attachmentHost = runtime.attachmentHost(
            for: attemptId,
            engine: engine,
            sessionStore: sessionStore,
            assets: assets
        )

        PlayerHostView(
            attemptId: attemptId,
            engine: engine,
            bodyStore: runtime.bodyStore,
            locale: preferences.locale,
            partChoiceLoader: partChoiceLoader,
            prepareAPI: prepareAPI,
            startContextStore: startContextStore,
            assets: assets,
            managesScenePhase: false,
            onCloseAttempt: {
                runtime.release(attemptId: attemptId)
                router.closeAttempt()
            },
            onBackToMyExams: {
                runtime.release(attemptId: attemptId)
                router.closeAttempt()
                router.switchTab(.myExams, reload: false)
            },
            onViewResult: {
                runtime.release(attemptId: attemptId)
                router.openResultFromAttempt(attemptId: attemptId)
            },
            onReplaceAttempt: { newAttemptId in
                runtime.release(attemptId: attemptId)
                router.replaceAttempt(attemptId: newAttemptId)
            },
            onOpenPrepare: { entitlementId in
                runtime.release(attemptId: attemptId)
                router.closeAttempt()
                router.openPrepare(entitlementId: entitlementId)
            }
        )
        .environment(\.horizontalSizeClass, effectiveSizeClass)
        .environment(\.playerAttachmentHost, attachmentHost)
        .overlay(alignment: .bottomTrailing) {
            #if THI360_DEV
            if router.stateProbeEnabled {
                Button {
                    router.toggleForcedSizeClass(current: effectiveSizeClass)
                } label: {
                    Image(systemName: "rectangle.split.2x1")
                        .frame(minWidth: 44, minHeight: 44)
                }
                .accessibilityLabel(ShellStrings.text("shell.probe.toggleSizeClass", locale: preferences.locale))
                .accessibilityIdentifier("player.probe.toggleSizeClass")
            }
            #endif
        }
        .bindAttemptEngineLifecycle(
            registry: runtime.engineRegistry,
            inboundLinkRouter: router.inboundLinkRouter
        )
        .onAppear {
            runtime.bindLifecycleIfNeeded(router: router, sessionStore: sessionStore)
            Thi360AppDelegate.refreshSupportedOrientations(for: effectiveSizeClass)
        }
        .onChange(of: effectiveSizeClass) { _, newValue in
            Thi360AppDelegate.refreshSupportedOrientations(for: newValue)
        }
        .onDisappear {
            if router.presentedAttempt?.attemptId != attemptId {
                runtime.release(attemptId: attemptId)
            }
        }
    }
}
