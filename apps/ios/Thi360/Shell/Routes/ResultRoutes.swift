import SwiftUI

enum ResultRoutes {
    @MainActor
    static func result(
        attemptId: String,
        destination: Destination,
        router: AppRouter,
        preferences: AppPreferences
    ) -> some View {
        ResultRouteHostView(
            attemptId: attemptId,
            destination: destination,
            router: router,
            preferences: preferences
        )
        .id(attemptId)
    }

    @MainActor
    static func review(
        attemptId: String,
        destination: Destination,
        router: AppRouter,
        preferences: AppPreferences
    ) -> some View {
        ReviewRouteHostView(
            attemptId: attemptId,
            destination: destination,
            router: router,
            preferences: preferences
        )
        .id(attemptId)
    }
}

private struct ResultRouteHostView: View {
    let attemptId: String
    let destination: Destination
    @Bindable var router: AppRouter
    @Bindable var preferences: AppPreferences

    @Environment(\.sessionStore) private var sessionStore
    @Environment(\.catalogOptionsCache) private var catalogOptionsCache
    @Environment(\.connectivityMonitor) private var connectivityMonitor
    @State private var viewModel: ResultViewModel?

    var body: some View {
        Group {
            if let sessionStore {
                if let vm = viewModel {
                    ResultView(
                        viewModel: vm,
                        locale: preferences.locale,
                        onOpenReview: { targetAttemptId in
                            router.openReview(attemptId: targetAttemptId)
                        },
                        onContinueAttempt: { targetAttemptId in
                            router.openAttempt(attemptId: targetAttemptId)
                        },
                        onGoMyExams: {
                            router.switchTab(.myExams, reload: true)
                        },
                        onGoHistory: {
                            router.switchTab(.history, reload: true)
                        }
                    )
                } else {
                    Color.clear
                        .onAppear {
                            let repo = ResultRepositoryRegistry.shared.repository(
                                sessionStore: sessionStore,
                                catalogOptionsCache: catalogOptionsCache
                            )
                            viewModel = ResultViewModel(
                                attemptId: attemptId,
                                repository: repo,
                                connectivityMonitor: connectivityMonitor
                            )
                        }
                }
            } else {
                ResultUnboundSessionFallbackView(
                    title: ResultStrings.text("result.pageTitle", locale: preferences.locale),
                    identifier: AppRoute.result(attemptId: attemptId).accessibilityIdentifier
                )
            }
        }
    }
}

private struct ReviewRouteHostView: View {
    let attemptId: String
    let destination: Destination
    @Bindable var router: AppRouter
    @Bindable var preferences: AppPreferences

    @Environment(\.sessionStore) private var sessionStore
    @Environment(\.catalogOptionsCache) private var catalogOptionsCache
    @Environment(\.connectivityMonitor) private var connectivityMonitor
    @State private var viewModel: ReviewViewModel?

    var body: some View {
        Group {
            if let sessionStore {
                if let vm = viewModel {
                    ReviewView(
                        viewModel: vm,
                        locale: preferences.locale,
                        onBackToResult: {
                            let stack = router.path(for: destination)
                            if stack.count >= 2, case .result = stack[stack.count - 2] {
                                router.back()
                            } else {
                                var next = stack
                                if !next.isEmpty { next.removeLast() }
                                next.append(.result(attemptId: attemptId))
                                router.setPath(next, for: destination)
                            }
                        },
                        onGoMyExams: {
                            router.switchTab(.myExams, reload: true)
                        },
                        onGoHistory: {
                            router.switchTab(.history, reload: true)
                        },
                        shouldPurgeAssetsOnDisappear: {
                            sessionStore.state != .locked
                        }
                    )
                } else {
                    Color.clear
                        .onAppear {
                            let repo = ResultRepositoryRegistry.shared.repository(
                                sessionStore: sessionStore,
                                catalogOptionsCache: catalogOptionsCache
                            )
                            let assetSource = AttemptAssetSource(
                                attemptId: attemptId,
                                mobileAPIClient: sessionStore.apiClient,
                                tokenStore: sessionStore.tokenStore,
                                localeProvider: { [preferences] in
                                    await MainActor.run { preferences.locale }
                                }
                            )
                            viewModel = ReviewViewModel(
                                attemptId: attemptId,
                                repository: repo,
                                assetSource: assetSource,
                                connectivityMonitor: connectivityMonitor
                            )
                        }
                }
            } else {
                ResultUnboundSessionFallbackView(
                    title: ResultStrings.text("review.pageTitle", locale: preferences.locale),
                    identifier: AppRoute.review(attemptId: attemptId).accessibilityIdentifier
                )
            }
        }
    }
}

private struct ResultUnboundSessionFallbackView: View {
    let title: String
    let identifier: String

    @Environment(\.brandPalette) private var palette

    var body: some View {
        VStack(spacing: 12) {
            Text(title)
                .font(.headline)
                .foregroundStyle(palette.textColor)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(palette.bgPageColor.ignoresSafeArea())
        .accessibilityIdentifier(identifier)
    }
}
