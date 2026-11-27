import SwiftUI

struct AppRouteView: View {
    let route: AppRoute
    let destination: Destination
    @Bindable var router: AppRouter
    @Bindable var preferences: AppPreferences
    var legalRepository: LegalRepository = LegalRepository()
    var webBaseURL: URL = AppEnvironment.current.webBaseURL

    var body: some View {
        switch route {
        case .catalog:
            CatalogRoutes.homeRoot(router: router, preferences: preferences, showsBrandHeader: true)
        case let .product(code):
            CatalogRoutes.product(code: code, destination: destination, router: router, preferences: preferences)
        case let .checkout(code, seenVersionNo, seenPrice):
            PaymentRoutes.checkout(
                code: code,
                seenVersionNo: seenVersionNo,
                seenPrice: seenPrice,
                destination: destination,
                router: router,
                preferences: preferences
            )
        case let .paymentResult(target):
            PaymentRoutes.paymentResult(
                target: target,
                destination: destination,
                router: router,
                preferences: preferences
            )
        case .orders:
            PaymentRoutes.orders(destination: destination, router: router, preferences: preferences)
        case let .order(orderCode):
            PaymentRoutes.order(
                orderCode: orderCode,
                destination: destination,
                router: router,
                preferences: preferences
            )
        case .myExams:
            MyExamsRoutes.myExamsRoot(router: router, preferences: preferences, showsBrandHeader: false)
        case let .history(resetFilters):
            MyExamsRoutes.historyRoot(resetFilters: resetFilters, router: router, preferences: preferences, showsBrandHeader: false)
        case let .prepare(entitlementId):
            PrepareRoutes.prepare(
                entitlementId: entitlementId,
                destination: destination,
                router: router,
                preferences: preferences
            )
        case let .attempt(attemptId):
            AttemptRoutes.attempt(
                attemptId: attemptId,
                destination: destination,
                router: router,
                preferences: preferences
            )
        case let .result(attemptId):
            ResultRoutes.result(
                attemptId: attemptId,
                destination: destination,
                router: router,
                preferences: preferences
            )
        case let .review(attemptId):
            ResultRoutes.review(
                attemptId: attemptId,
                destination: destination,
                router: router,
                preferences: preferences
            )
        case .profile:
            ProfileRoutes.profile(destination: destination, router: router, preferences: preferences)
        case .profileVerifyEmail:
            ProfileRoutes.profileVerifyEmail(destination: destination, router: router, preferences: preferences)
        case .deleteAccount:
            ProfileRoutes.deleteAccount(destination: destination, router: router, preferences: preferences)
        case let .legal(legalId):
            LegalDocumentView(
                legalId: legalId,
                preferences: preferences,
                repository: legalRepository,
                webBaseURL: webBaseURL
            )
        }
    }
}

struct RoutePlaceholderView: View {
    let route: AppRoute
    let destination: Destination
    @Bindable var router: AppRouter
    @Bindable var preferences: AppPreferences
    var showsCloseAttemptButton: Bool = false

    @Environment(\.brandPalette) private var palette
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @State private var refreshCount: Int = 0

    private var locale: AppLocale { preferences.locale }

    private var draftBinding: Binding<String> {
        Binding(
            get: { router.routeStateStore.state(for: destination, route: route).draftText },
            set: { router.routeStateStore.setDraftText($0, for: destination, route: route) }
        )
    }

    private var savedScrollRow: Int {
        router.routeStateStore.state(for: destination, route: route).scrollRowIndex
    }

    var body: some View {
        ScrollViewReader { proxy in
            RefreshableContainer(onRefresh: {
                refreshCount += 1
            }) {
                VStack(alignment: .leading, spacing: 16) {
                    if router.stateProbeEnabled {
                        Text("\(refreshCount)")
                            .font(.caption.monospacedDigit())
                            .foregroundStyle(palette.textMutedColor)
                            .accessibilityIdentifier("route.placeholder.refreshCount")
                    }

                    Text(ShellStrings.text(route.nameKey, locale: locale))
                        .font(.title2.weight(.bold))
                        .foregroundStyle(palette.textColor)
                        .accessibilityAddTraits(.isHeader)
                        .accessibilityIdentifier("route.placeholder.title")

                    Text(route.logicalName)
                        .font(.footnote.monospaced())
                        .foregroundStyle(palette.textMutedColor)
                        .accessibilityIdentifier("route.placeholder.logicalName")

                    if !route.parameterSummary.isEmpty {
                        VStack(alignment: .leading, spacing: 6) {
                            Text(ShellStrings.text("shell.route.parameterLabel", locale: locale))
                                .font(.footnote.weight(.semibold))
                                .foregroundStyle(palette.textMutedColor)

                            ForEach(route.parameterSummary, id: \.key) { item in
                                HStack(spacing: 8) {
                                    Text(item.key)
                                        .font(.footnote.monospaced().weight(.semibold))
                                        .foregroundStyle(palette.textSecondaryColor)
                                    Text(item.value)
                                        .font(.footnote.monospaced())
                                        .foregroundStyle(palette.textColor)
                                        .accessibilityIdentifier("route.placeholder.param.\(item.key)")
                                }
                            }
                        }
                        .padding(14)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .fill(palette.bgSurfaceColor)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .strokeBorder(palette.borderColor, lineWidth: 1)
                        )
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        Text(ShellStrings.text("shell.route.draftLabel", locale: locale))
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(palette.textMutedColor)

                        TextField(
                            ShellStrings.text("shell.route.draftPlaceholder", locale: locale),
                            text: draftBinding
                        )
                        .textFieldStyle(.plain)
                        .padding(.horizontal, 14)
                        .frame(minHeight: 48)
                        .foregroundStyle(palette.textColor)
                        .background(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(palette.bgSurfaceColor)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .strokeBorder(palette.borderStrongColor, lineWidth: 1)
                        )
                        .accessibilityIdentifier("route.placeholder.draftField")

                        if router.stateProbeEnabled {
                            Button {
                                router.toggleForcedSizeClass(current: horizontalSizeClass)
                            } label: {
                                Text(ShellStrings.text("shell.probe.toggleSizeClass", locale: locale))
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(palette.onPrimaryColor)
                                    .frame(maxWidth: .infinity, minHeight: 44)
                                    .background(
                                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                                            .fill(palette.primaryFillColor)
                                    )
                            }
                            .buttonStyle(.plain)
                            .accessibilityIdentifier("route.placeholder.toggleSizeClass")
                        }
                    }

                    if showsCloseAttemptButton {
                        Button {
                            router.closeAttempt()
                        } label: {
                            Text(ShellStrings.text("shell.route.closeAttempt", locale: locale))
                                .font(.body.weight(.semibold))
                                .foregroundStyle(palette.onPrimaryColor)
                                .frame(maxWidth: .infinity, minHeight: 48)
                                .background(
                                    Capsule(style: .continuous)
                                        .fill(palette.primaryFillColor)
                                )
                        }
                        .buttonStyle(.plain)
                        .accessibilityIdentifier("route.placeholder.closeAttemptButton")
                    }

                    LazyVStack(alignment: .leading, spacing: 8) {
                        ForEach(1...24, id: \.self) { index in
                            Text(
                                ShellStrings.text(
                                    "shell.route.scrollItem",
                                    locale: locale,
                                    replacements: ["index": String(index)]
                                )
                            )
                            .font(.subheadline)
                            .foregroundStyle(palette.textSecondaryColor)
                            .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                            .padding(.horizontal, 12)
                            .background(
                                RoundedRectangle(cornerRadius: 10, style: .continuous)
                                    .fill(palette.bgSurfaceColor)
                            )
                            .id(index)
                            .accessibilityIdentifier("route.placeholder.row.\(index)")
                            .onAppear {
                                if index > savedScrollRow {
                                    router.routeStateStore.setScrollRowIndex(index, for: destination, route: route)
                                }
                            }
                        }
                    }
                }
                .padding(20)
            }
            .onAppear {
                Thi360AppDelegate.refreshSupportedOrientations(for: horizontalSizeClass)
                if savedScrollRow > 1 {
                    proxy.scrollTo(savedScrollRow, anchor: .bottom)
                }
            }
            .onChange(of: horizontalSizeClass) { _, newValue in
                Thi360AppDelegate.refreshSupportedOrientations(for: newValue)
            }
        }
        .background(palette.bgPageColor.ignoresSafeArea())
        .navigationTitle(ShellStrings.text(route.nameKey, locale: locale))
        .navigationBarTitleDisplayMode(.inline)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier(route.accessibilityIdentifier)
    }
}
