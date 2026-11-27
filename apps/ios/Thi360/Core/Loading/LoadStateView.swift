import SwiftUI

struct LoadStateView<T, Content: View, EmptyContent: View, CustomErrorContent: View>: View {
    let state: LoadState<T>
    let locale: AppLocale
    let staleMessage: String?
    let retryBlockedUntil: Date?
    let clock: any AppClock
    let onRetry: () -> Void
    @ViewBuilder let content: (T) -> Content
    @ViewBuilder let empty: () -> EmptyContent
    @ViewBuilder let customError: (LoadFailureKind, Int?) -> CustomErrorContent

    @Environment(\.brandPalette) private var palette
    @State private var stateDerivedBlockedUntil: Date?
    @State private var cooldownTick: Int = 0

    init(
        state: LoadState<T>,
        locale: AppLocale,
        staleMessage: String? = nil,
        retryBlockedUntil: Date? = nil,
        clock: any AppClock = SystemClock(),
        onRetry: @escaping () -> Void = {},
        @ViewBuilder content: @escaping (T) -> Content,
        @ViewBuilder empty: @escaping () -> EmptyContent,
        @ViewBuilder customError: @escaping (LoadFailureKind, Int?) -> CustomErrorContent
    ) {
        self.state = state
        self.locale = locale
        self.staleMessage = staleMessage
        self.retryBlockedUntil = retryBlockedUntil
        self.clock = clock
        self.onRetry = onRetry
        self.content = content
        self.empty = empty
        self.customError = customError
        if retryBlockedUntil == nil,
           case let .failed(_, _, retryAfter?) = state,
           retryAfter > 0
        {
            _stateDerivedBlockedUntil = State(initialValue: clock.now.addingTimeInterval(TimeInterval(retryAfter)))
        } else {
            _stateDerivedBlockedUntil = State(initialValue: nil)
        }
    }

    private var failedRetryAfterSeconds: Int? {
        if case let .failed(_, _, retryAfter?) = state, retryAfter > 0 {
            return retryAfter
        }
        return nil
    }

    var effectiveRetryBlockedUntil: Date? {
        retryBlockedUntil ?? stateDerivedBlockedUntil
    }

    var isRetryBlocked: Bool {
        guard let deadline = effectiveRetryBlockedUntil else { return false }
        return clock.now < deadline
    }

    var body: some View {
        let _ = cooldownTick
        Group {
            switch state {
            case .loading:
                loadingView

            case let .loaded(value, stale):
                VStack(spacing: 0) {
                    if stale, let staleMessage, !staleMessage.isEmpty {
                        StaleBanner(
                            message: staleMessage,
                            locale: locale,
                            onRetry: isRetryBlocked ? nil : { triggerRetryIfAllowed() }
                        )
                        .padding(.horizontal, 16)
                        .padding(.top, 12)
                    }
                    content(value)
                }
                .accessibilityElement(children: .contain)
                .accessibilityIdentifier("loadState.loaded")

            case .empty:
                empty()
                    .accessibilityElement(children: .contain)
                    .accessibilityIdentifier("loadState.empty")

            case let .failed(kind, canRetry, retryAfter):
                switch kind {
                case .offline:
                    failureCard(
                        symbolName: "wifi.slash",
                        title: AppStrings.common("common.offline.title", locale: locale),
                        description: AppStrings.common("common.offline.description", locale: locale),
                        canRetry: canRetry,
                        identifier: "loadState.failed.offline"
                    )
                case .server:
                    failureCard(
                        symbolName: "exclamationmark.triangle",
                        title: AppStrings.common("errorPage.title", locale: locale),
                        description: AppStrings.common("errorPage.description", locale: locale),
                        canRetry: canRetry,
                        identifier: "loadState.failed.server"
                    )
                case .notFound, .gone, .forbidden, .conflict:
                    customError(kind, retryAfter)
                        .accessibilityElement(children: .contain)
                        .accessibilityIdentifier("loadState.failed.custom")
                }
            }
        }
        .onChange(of: failedRetryAfterSeconds) { _, newSeconds in
            if retryBlockedUntil == nil, let newSeconds, newSeconds > 0 {
                stateDerivedBlockedUntil = clock.now.addingTimeInterval(TimeInterval(newSeconds))
            } else {
                stateDerivedBlockedUntil = nil
            }
        }
        .task(id: effectiveRetryBlockedUntil) {
            guard let deadline = effectiveRetryBlockedUntil else { return }
            while !Task.isCancelled && clock.now < deadline {
                let remaining = deadline.timeIntervalSince(clock.now)
                let stepSeconds = min(max(remaining, 0.01), 0.1)
                try? await Task.sleep(nanoseconds: UInt64(stepSeconds * 1_000_000_000))
            }
            if !Task.isCancelled {
                cooldownTick &+= 1
            }
        }
    }

    func triggerRetryIfAllowed() {
        guard !isRetryBlocked else { return }
        onRetry()
    }

    private var loadingView: some View {
        VStack(spacing: 12) {
            ProgressView()
                .tint(palette.primaryTextColor)
            Text(AppStrings.common("common.loading", locale: locale))
                .font(.subheadline)
                .foregroundStyle(palette.textMutedColor)
        }
        .frame(maxWidth: .infinity, minHeight: 200)
        .padding(24)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(AppStrings.common("common.loading", locale: locale))
        .accessibilityIdentifier("loadState.loading")
    }

    private func failureCard(
        symbolName: String,
        title: String,
        description: String,
        canRetry: Bool,
        identifier: String
    ) -> some View {
        VStack(spacing: 14) {
            Image(systemName: symbolName)
                .font(.system(size: 32, weight: .semibold))
                .foregroundStyle(palette.dangerTextColor)
                .accessibilityHidden(true)

            Text(title)
                .font(.headline)
                .foregroundStyle(palette.textColor)
                .multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader)
                .accessibilityIdentifier("loadState.failed.title")

            Text(description)
                .font(.subheadline)
                .foregroundStyle(palette.textMutedColor)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityIdentifier("loadState.failed.description")

            if canRetry {
                Button {
                    triggerRetryIfAllowed()
                } label: {
                    Text(AppStrings.common("common.retry", locale: locale))
                        .font(.body.weight(.semibold))
                        .foregroundStyle(palette.onPrimaryColor)
                        .padding(.horizontal, 24)
                        .frame(minWidth: 120, minHeight: 44)
                        .background(
                            Capsule(style: .continuous)
                                .fill(palette.primaryFillColor)
                                .opacity(isRetryBlocked ? 0.5 : 1.0)
                        )
                        .contentShape(Capsule(style: .continuous))
                }
                .buttonStyle(.plain)
                .disabled(isRetryBlocked)
                .accessibilityIdentifier("loadState.retryButton")
            }
        }
        .frame(maxWidth: .infinity)
        .padding(24)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(palette.bgSurfaceColor)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .strokeBorder(palette.borderColor, lineWidth: 1)
        )
        .padding(20)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier(identifier)
    }
}

extension LoadStateView where EmptyContent == EmptyView, CustomErrorContent == EmptyView {
    init(
        state: LoadState<T>,
        locale: AppLocale,
        staleMessage: String? = nil,
        retryBlockedUntil: Date? = nil,
        clock: any AppClock = SystemClock(),
        onRetry: @escaping () -> Void = {},
        @ViewBuilder content: @escaping (T) -> Content
    ) {
        self.init(
            state: state,
            locale: locale,
            staleMessage: staleMessage,
            retryBlockedUntil: retryBlockedUntil,
            clock: clock,
            onRetry: onRetry,
            content: content,
            empty: { EmptyView() },
            customError: { _, _ in EmptyView() }
        )
    }
}
