import SwiftUI

struct AttemptAudioPlayerView: View {
    let state: AudioPlayerUIState
    let locale: AppLocale
    let onPrimaryTap: () -> Void
    let onBackground: (() -> Void)?
    let onDisappearLeave: (() -> Void)?

    @Environment(\.brandPalette) private var brandPalette
    @Environment(\.statusPalette) private var statusPalette
    @Environment(\.scenePhase) private var scenePhase

    init(
        controller: AttemptAudioController,
        locale: AppLocale = .vi,
        attachLifecycle: Bool = true
    ) {
        self.state = controller.uiState
        self.locale = locale
        self.onPrimaryTap = { controller.onPrimaryAction() }
        self.onBackground = attachLifecycle ? { controller.onAppBackgrounded() } : nil
        self.onDisappearLeave = attachLifecycle ? { controller.onLeaveScreen() } : nil
    }

    init(
        state: AudioPlayerUIState,
        locale: AppLocale = .vi,
        onPrimaryTap: @escaping () -> Void = {},
        onBackground: (() -> Void)? = nil,
        onDisappearLeave: (() -> Void)? = nil
    ) {
        self.state = state
        self.locale = locale
        self.onPrimaryTap = onPrimaryTap
        self.onBackground = onBackground
        self.onDisappearLeave = onDisappearLeave
    }

    private var buttonLabel: String {
        AttachmentsStrings.text(state.primaryButtonRole.stringKey, locale: locale)
    }

    private var buttonIconName: String {
        switch state.primaryButtonRole {
        case .play: "play.fill"
        case .playing: "waveform"
        case .resume: "arrow.counterclockwise"
        }
    }

    private var playsBadgeText: String {
        if state.isExhausted {
            return AttachmentsStrings.playsExhaustedBadge(max: state.effectiveMaxPlays, locale: locale)
        }
        return AttachmentsStrings.playsRemaining(
            left: state.playsLeft,
            max: state.effectiveMaxPlays,
            locale: locale
        )
    }

    private var timeText: String {
        let elapsed = AudioTimeFormatter.format(milliseconds: state.clampedPositionMs)
        let total = AudioTimeFormatter.format(milliseconds: state.durationMs)
        return "\(elapsed) / \(total)"
    }

    private var buttonBackground: Color {
        if state.primaryButtonRole == .playing {
            return brandPalette.primaryContainerColor
        }
        return state.isPrimaryButtonEnabled ? brandPalette.primaryFillColor : brandPalette.borderColor
    }

    private var buttonForeground: Color {
        if state.primaryButtonRole == .playing {
            return brandPalette.primaryTextColor
        }
        return state.isPrimaryButtonEnabled ? brandPalette.onPrimaryColor : brandPalette.textMutedColor
    }

    var body: some View {
        let regionLabel = AttachmentsStrings.regionLabel(locale: locale)
        let progressLabel = AttachmentsStrings.progressLabel(locale: locale)
        let pct = Int((state.progressFraction * 100.0).rounded())

        VStack(alignment: .leading, spacing: 10) {
            if !state.fileName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                HStack(spacing: 6) {
                    Image(systemName: "music.note")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(brandPalette.primaryTextColor)
                        .accessibilityHidden(true)

                    Text(state.fileName)
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(brandPalette.textSecondaryColor)
                        .lineLimit(2)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityIdentifier("audio.fileName.\(state.assetId)")
                        .questionProbe(id: "audio.fileName.\(state.assetId)", label: state.fileName)
                }
            }

            WrappingHStackLayout(horizontalSpacing: 12, verticalSpacing: 8) {
                Button(action: onPrimaryTap) {
                    HStack(spacing: 8) {
                        if state.isBusyOpening {
                            ProgressView()
                                .controlSize(.small)
                                .tint(buttonForeground)
                        } else {
                            Image(systemName: buttonIconName)
                                .font(.subheadline.weight(.semibold))
                                .accessibilityHidden(true)
                        }

                        Text(buttonLabel)
                            .font(.subheadline.weight(.semibold))
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .foregroundStyle(buttonForeground)
                    .padding(.horizontal, 16)
                    .frame(minHeight: 44)
                    .background(Capsule().fill(buttonBackground))
                    .opacity(state.isPrimaryButtonEnabled || state.primaryButtonRole == .playing ? 1.0 : 0.72)
                }
                .buttonStyle(.plain)
                .disabled(!state.isPrimaryButtonEnabled)
                .accessibilityIdentifier("audio.primaryButton.\(state.assetId)")
                .accessibilityLabel(buttonLabel)
                .questionProbe(
                    id: "audio.primaryButton.\(state.assetId)",
                    label: buttonLabel,
                    isButton: true,
                    isEnabled: state.isPrimaryButtonEnabled,
                    action: onPrimaryTap
                )

                VStack(alignment: .leading, spacing: 3) {
                    Text(playsBadgeText)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(state.isExhausted ? brandPalette.textSecondaryColor : statusPalette.infoColor)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 3)
                        .background(
                            Capsule().fill(
                                state.isExhausted ? brandPalette.bgSubtleColor : statusPalette.infoBgColor
                            )
                        )
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityIdentifier("audio.playsBadge.\(state.assetId)")
                        .questionProbe(id: "audio.playsBadge.\(state.assetId)", label: playsBadgeText)

                    Text(timeText)
                        .font(.caption.weight(.medium).monospacedDigit())
                        .foregroundStyle(brandPalette.textSecondaryColor)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityIdentifier("audio.timeLabel.\(state.assetId)")
                        .questionProbe(id: "audio.timeLabel.\(state.assetId)", label: timeText)
                }
            }

            // Read-only progress bar: non-interactive so students cannot scrub audio (R-134).
            GeometryReader { proxy in
                ZStack(alignment: .leading) {
                    Capsule()
                        .fill(brandPalette.bgSubtleColor)
                    Capsule()
                        .fill(brandPalette.primaryFillColor)
                        .frame(width: max(0, proxy.size.width * state.progressFraction))
                }
            }
            .frame(height: 6)
            .allowsHitTesting(false)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(progressLabel)
            .accessibilityValue("\(pct)%")
            .accessibilityIdentifier("audio.progressBar.\(state.assetId)")
            .questionProbe(
                id: "audio.progressBar.\(state.assetId)",
                label: progressLabel,
                value: "\(pct)%"
            )

            if let banner = state.banner {
                bannerView(banner)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(brandPalette.bgSurfaceColor)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .strokeBorder(brandPalette.borderColor, lineWidth: 1)
        )
        .accessibilityElement(children: .contain)
        .accessibilityLabel(regionLabel)
        .accessibilityIdentifier("audio.player.\(state.assetId)")
        .questionProbe(id: "audio.player.\(state.assetId)", label: regionLabel)
        .onChange(of: scenePhase) { _, newPhase in
            if newPhase != .active {
                onBackground?()
            }
        }
        .onDisappear {
            onDisappearLeave?()
        }
    }

    @ViewBuilder
    private func bannerView(_ banner: AudioBanner) -> some View {
        let message = AttachmentsStrings.text(banner.stringKey, locale: locale)
        let style = bannerColors(for: banner.tone)

        HStack(alignment: .top, spacing: 6) {
            Image(systemName: style.icon)
                .font(.caption.weight(.semibold))
                .foregroundStyle(style.foreground)
                .padding(.top, 2)
                .accessibilityHidden(true)

            Text(message)
                .font(.caption.weight(.medium))
                .foregroundStyle(style.foreground)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .fill(style.background)
        )
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("audio.banner.\(state.assetId)")
        .questionProbe(id: "audio.banner.\(state.assetId)", label: message)
    }

    private func bannerColors(for tone: AudioBannerTone) -> (foreground: Color, background: Color, icon: String) {
        switch tone {
        case .info:
            (statusPalette.infoColor, statusPalette.infoBgColor, "info.circle")
        case .warning:
            (statusPalette.warningColor, statusPalette.warningBgColor, "exclamationmark.triangle")
        case .danger:
            (brandPalette.dangerTextColor, brandPalette.dangerBgColor, "exclamationmark.triangle")
        case .neutral:
            (brandPalette.textSecondaryColor, brandPalette.bgSubtleColor, "info.circle")
        }
    }
}
