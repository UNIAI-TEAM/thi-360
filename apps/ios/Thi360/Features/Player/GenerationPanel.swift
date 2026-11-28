import SwiftUI

struct GenerationPanel: View {
    static let defaultBusyNoticeDelay: Duration = .seconds(15)

    let panel: GenerationPanelUI
    let locale: AppLocale
    let busyNoticeDelay: Duration
    let onRetry: () -> Void

    @Environment(\.brandPalette) private var palette
    @Environment(\.statusPalette) private var statusPalette

    @State private var showBusyNotice = false

    init(
        panel: GenerationPanelUI,
        locale: AppLocale,
        busyNoticeDelay: Duration = GenerationPanel.defaultBusyNoticeDelay,
        onRetry: @escaping () -> Void
    ) {
        self.panel = panel
        self.locale = locale
        self.busyNoticeDelay = busyNoticeDelay
        self.onRetry = onRetry
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                if panel.isFailed {
                    failedCard
                } else {
                    generatingCard
                }
            }
            .frame(maxWidth: 520)
            .frame(maxWidth: .infinity)
            .padding(20)
        }
        .background(palette.bgPageColor)
        .task(id: "\(panel.partId)::\(panel.isFailed)") {
            showBusyNotice = false
            guard !panel.isFailed else { return }
            do {
                try await Task.sleep(for: busyNoticeDelay)
                if !Task.isCancelled {
                    showBusyNotice = true
                }
            } catch {}
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("player.generation")
    }

    private var generatingCard: some View {
        VStack(spacing: 14) {
            ZStack {
                Circle()
                    .fill(palette.primaryContainerColor)
                    .frame(width: 60, height: 60)
                ProgressView()
                    .tint(palette.primaryTextColor)
                    .scaleEffect(1.15)
            }
            .accessibilityHidden(true)

            let genTitle = PlayerStrings.text("attempt.partChoice.generatingTitle", locale: locale)
            Text(genTitle)
                .font(.title3.weight(.bold))
                .foregroundStyle(palette.textColor)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
                .playerProbe("player.generation.title", label: genTitle)

            if !panel.partName.isEmpty || panel.chosenOptionName != nil {
                let subtitle = [panel.partName, panel.chosenOptionName ?? ""]
                    .filter { !$0.isEmpty }
                    .joined(separator: " · ")
                Text(subtitle)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(palette.primaryTextColor)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .playerProbe("player.generation.partName", label: subtitle)
            }

            let genDesc = PlayerStrings.text("attempt.partChoice.generatingDescription", locale: locale)
            Text(genDesc)
                .font(.body)
                .foregroundStyle(palette.textSecondaryColor)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .playerProbe("player.generation.description", label: genDesc)

            if showBusyNotice {
                let busyText = PlayerStrings.text("attempt.partChoice.busyNotice", locale: locale)
                HStack(alignment: .top, spacing: 8) {
                    Image(systemName: "clock")
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(statusPalette.warningColor)
                        .padding(.top, 2)
                        .accessibilityHidden(true)
                    Text(busyText)
                        .font(.footnote.weight(.medium))
                        .foregroundStyle(statusPalette.warningColor)
                        .fixedSize(horizontal: false, vertical: true)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(12)
                .background(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(statusPalette.warningBgColor)
                )
                .accessibilityElement(children: .combine)
                .playerProbe("player.generation.busyNotice", label: busyText)
            }
        }
        .padding(24)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(palette.bgSurfaceColor)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .strokeBorder(palette.borderColor, lineWidth: 1)
        )
    }

    private var failedCard: some View {
        let canRetry = !panel.isLocked && !panel.isRetrying

        return VStack(spacing: 14) {
            ZStack {
                Circle()
                    .fill(palette.dangerBgColor)
                    .frame(width: 60, height: 60)
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.system(size: 26, weight: .semibold))
                    .foregroundStyle(palette.dangerTextColor)
            }
            .accessibilityHidden(true)

            let failedTitle = PlayerStrings.text("attempt.partChoice.failedTitle", locale: locale)
            Text(failedTitle)
                .font(.title3.weight(.bold))
                .foregroundStyle(palette.textColor)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
                .playerProbe("player.generation.failedTitle", label: failedTitle)

            if !panel.partName.isEmpty {
                Text(panel.partName)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(palette.textSecondaryColor)
                    .playerProbe("player.generation.partName", label: panel.partName)
            }

            let failedDesc = PlayerStrings.text("attempt.partChoice.failedDescription", locale: locale)
            Text(failedDesc)
                .font(.body)
                .foregroundStyle(palette.textSecondaryColor)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .playerProbe("player.generation.failedDescription", label: failedDesc)

            if let errorCode = panel.errorCode {
                let errMsg = PlayerStrings.partChoiceErrorMessage(code: errorCode, locale: locale)
                HStack(alignment: .top, spacing: 8) {
                    Image(systemName: "exclamationmark.circle.fill")
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(palette.dangerTextColor)
                        .padding(.top, 2)
                        .accessibilityHidden(true)
                    Text(errMsg)
                        .font(.footnote.weight(.medium))
                        .foregroundStyle(palette.dangerTextColor)
                        .fixedSize(horizontal: false, vertical: true)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(12)
                .background(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(palette.dangerBgColor)
                )
                .accessibilityElement(children: .combine)
                .playerProbe("player.generation.errorBanner", label: errMsg)
            }

            Button(action: onRetry) {
                HStack(spacing: 8) {
                    if panel.isRetrying {
                        ProgressView()
                            .tint(palette.onPrimaryColor)
                    } else {
                        Image(systemName: "arrow.clockwise")
                            .font(.body.weight(.semibold))
                            .accessibilityHidden(true)
                    }
                    Text(PlayerStrings.text("attempt.partChoice.failedRetry", locale: locale))
                        .font(.body.weight(.bold))
                }
                .foregroundStyle(palette.onPrimaryColor)
                .frame(maxWidth: .infinity, minHeight: 48)
                .padding(.horizontal, 16)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(palette.primaryFillColor)
                        .opacity(canRetry ? 1.0 : 0.45)
                )
            }
            .buttonStyle(.plain)
            .disabled(!canRetry)
            .accessibilityIdentifier("player.generation.retryButton")
        }
        .padding(24)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(palette.bgSurfaceColor)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .strokeBorder(palette.borderColor, lineWidth: 1)
        )
    }
}
