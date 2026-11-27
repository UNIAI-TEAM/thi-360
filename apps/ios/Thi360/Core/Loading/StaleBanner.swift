import SwiftUI

struct StaleBanner: View {
    let message: String
    let locale: AppLocale
    var onRetry: (() -> Void)? = nil

    @Environment(\.statusPalette) private var statusPalette
    @Environment(\.brandPalette) private var palette

    var body: some View {
        HStack(alignment: .center, spacing: 10) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(statusPalette.warningColor)
                .accessibilityHidden(true)

            Text(message)
                .font(.footnote.weight(.medium))
                .foregroundStyle(statusPalette.warningColor)
                .fixedSize(horizontal: false, vertical: true)

            Spacer(minLength: 8)

            if let onRetry {
                Button(action: onRetry) {
                    Text(AppStrings.common("common.retry", locale: locale))
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(statusPalette.warningColor)
                        .padding(.horizontal, 10)
                        .frame(minWidth: 44, minHeight: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("loadState.staleBanner.retryButton")
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(statusPalette.warningBgColor)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .strokeBorder(statusPalette.warningColor.opacity(0.25), lineWidth: 1)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("loadState.staleBanner")
    }
}
