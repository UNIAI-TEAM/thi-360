import SwiftUI

enum ProfileRoutes {
    @MainActor
    static func profile(
        destination: Destination,
        router: AppRouter,
        preferences: AppPreferences
    ) -> some View {
        ProfileRouteHostView(
            destination: destination,
            router: router,
            preferences: preferences
        )
    }

    @MainActor
    static func profileVerifyEmail(
        destination: Destination,
        router: AppRouter,
        preferences: AppPreferences
    ) -> some View {
        VerifyEmailRouteHostView(
            destination: destination,
            router: router,
            preferences: preferences
        )
    }

    @MainActor
    static func deleteAccount(
        destination: Destination,
        router: AppRouter,
        preferences: AppPreferences
    ) -> some View {
        DeleteAccountRouteHostView(
            destination: destination,
            router: router,
            preferences: preferences
        )
    }
}

private struct ProfileRouteHostView: View {
    let destination: Destination
    @Bindable var router: AppRouter
    @Bindable var preferences: AppPreferences

    @Environment(\.sessionStore) private var sessionStore
    @State private var fallbackBiometricManager = BiometricManager()

    var body: some View {
        if let sessionStore {
            let biometricManager = sessionStore.biometricManager ?? fallbackBiometricManager
            let accountVM = router.accountViewModel(
                sessionStore: sessionStore,
                biometricManager: biometricManager
            )
            ProfileView(
                viewModel: accountVM.ensureProfileViewModel(),
                preferences: preferences,
                isEmbeddedPane: false,
                onVerifyEmail: {
                    router.openProfileVerifyEmail()
                }
            )
            .onDisappear {
                accountVM.clearProfileViewModelIfPopped(router: router, destination: destination)
            }
        } else {
            ProfileFallbackEmptyHostView(
                titleKey: "account.profile.title",
                identifier: "profile.screen",
                preferences: preferences
            )
        }
    }
}

private struct VerifyEmailRouteHostView: View {
    let destination: Destination
    @Bindable var router: AppRouter
    @Bindable var preferences: AppPreferences

    @Environment(\.sessionStore) private var sessionStore
    @State private var fallbackBiometricManager = BiometricManager()

    var body: some View {
        if let sessionStore {
            let biometricManager = sessionStore.biometricManager ?? fallbackBiometricManager
            let accountVM = router.accountViewModel(
                sessionStore: sessionStore,
                biometricManager: biometricManager
            )
            VerifyEmailView(
                model: accountVM.verifyEmailModel(),
                preferences: preferences,
                onBackToProfile: {
                    accountVM.clearVerifyEmailModel()
                    router.back()
                }
            )
            .onDisappear {
                accountVM.clearVerifyEmailModelIfPopped(router: router, destination: destination)
            }
        } else {
            ProfileFallbackEmptyHostView(
                titleKey: "account.verifyEmail.title",
                identifier: "verifyEmail.screen",
                preferences: preferences
            )
        }
    }
}

private struct DeleteAccountRouteHostView: View {
    let destination: Destination
    @Bindable var router: AppRouter
    @Bindable var preferences: AppPreferences

    @Environment(\.sessionStore) private var sessionStore
    @State private var fallbackBiometricManager = BiometricManager()

    var body: some View {
        if let sessionStore {
            let biometricManager = sessionStore.biometricManager ?? fallbackBiometricManager
            let accountVM = router.accountViewModel(
                sessionStore: sessionStore,
                biometricManager: biometricManager
            )
            DeleteAccountView(
                viewModel: accountVM.ensureDeleteAccountViewModel(),
                preferences: preferences,
                isEmbeddedPane: false,
                onOpenMyExams: {
                    router.switchTab(.myExams)
                },
                onOpenOrders: {
                    router.openOrders()
                }
            )
            .onDisappear {
                accountVM.clearDeleteAccountViewModelIfPopped(
                    router: router,
                    destination: destination,
                    isEmbedded: false
                )
            }
        } else {
            ProfileFallbackEmptyHostView(
                titleKey: "account.deletion.title",
                identifier: "deleteAccount.screen",
                preferences: preferences
            )
        }
    }
}

private struct ProfileFallbackEmptyHostView: View {
    let titleKey: String
    let identifier: String
    @Bindable var preferences: AppPreferences

    @Environment(\.brandPalette) private var palette

    var body: some View {
        VStack(spacing: 12) {
            ProgressView()
                .tint(palette.primaryTextColor)
            Text(AppStrings.common("common.loading", locale: preferences.locale))
                .font(.subheadline)
                .foregroundStyle(palette.textMutedColor)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(palette.bgPageColor.ignoresSafeArea())
        .navigationTitle(ProfileStrings.text(titleKey, locale: preferences.locale))
        .navigationBarTitleDisplayMode(.inline)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier(identifier)
    }
}
