import SwiftUI

struct OfflineStatusBanner: View {
    let locale: AppLocale
    let isRetrying: Bool
    let onRetry: () -> Void

    @Environment(\.brandPalette) private var palette

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "wifi.slash")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(palette.dangerTextColor)
                .accessibilityHidden(true)

            Text(AuthStrings.text("auth.offline.title", locale: locale))
                .font(.subheadline.weight(.medium))
                .foregroundStyle(palette.textColor)
                .fixedSize(horizontal: false, vertical: true)

            Spacer(minLength: 8)

            Button(action: onRetry) {
                HStack(spacing: 6) {
                    if isRetrying {
                        ProgressView()
                            .controlSize(.small)
                    }
                    Text(AuthStrings.text("auth.offline.retry", locale: locale))
                        .font(.subheadline.weight(.semibold))
                }
                .foregroundStyle(palette.primaryTextColor)
                .padding(.horizontal, 12)
                .frame(minWidth: 44, minHeight: 44)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(isRetrying)
            .accessibilityIdentifier("session.offline.retry")
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 6)
        .background(palette.dangerBgColor)
        .overlay(alignment: .bottom) {
            Rectangle()
                .fill(palette.borderColor)
                .frame(height: 1)
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("session.offline.banner")
    }
}

struct LockedGateView: View {
    let locale: AppLocale
    let onSignOut: () -> Void

    @Environment(\.brandPalette) private var palette

    var body: some View {
        VStack(spacing: 24) {
            Spacer(minLength: 0)

            Text(AuthStrings.text("auth.locked.title", locale: locale))
                .font(.title2.weight(.bold))
                .foregroundStyle(palette.textColor)
                .accessibilityAddTraits(.isHeader)

            Spacer(minLength: 0)

            Button(action: onSignOut) {
                Text(AuthStrings.text("auth.locked.signOut", locale: locale))
                    .font(.body.weight(.semibold))
                    .foregroundStyle(palette.onPrimaryColor)
                    .frame(maxWidth: .infinity, minHeight: 50)
                    .background(
                        Capsule(style: .continuous)
                            .fill(palette.primaryFillColor)
                    )
            }
            .buttonStyle(.plain)
            .frame(maxWidth: 440)
            .padding(.horizontal, 20)
            .padding(.bottom, 24)
            .accessibilityIdentifier("session.locked.signOut")
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(palette.bgSurfaceColor.ignoresSafeArea())
        .accessibilityIdentifier("session.locked.screen")
    }
}
