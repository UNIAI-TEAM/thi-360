import SwiftUI

struct PartTabs: View {
    let tabs: [PartTabUI]
    let locale: AppLocale
    let onSelectPart: (PartID) -> Void

    @Environment(\.brandPalette) private var palette
    @Environment(\.statusPalette) private var statusPalette

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(tabs) { tab in
                    tabView(tab)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 6)
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel(PlayerStrings.text("attempt.parts.navLabel", locale: locale))
        .playerProbe("player.partTabs", label: PlayerStrings.text("attempt.parts.navLabel", locale: locale))
    }

    @ViewBuilder
    private func tabView(_ tab: PartTabUI) -> some View {
        let label = tabAccessibilityLabel(tab)
        if tab.isClickable {
            Button {
                onSelectPart(tab.partId)
            } label: {
                tabContent(tab)
            }
            .buttonStyle(.plain)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(label)
            .accessibilityAddTraits(tab.isSelected ? [.isButton, .isSelected] : [.isButton])
            .playerProbe("player.partTab.\(tab.partId)", label: label, isButton: true, isSelected: tab.isSelected)
        } else {
            tabContent(tab)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(label)
                .accessibilityAddTraits(tab.isSelected ? [.isSelected] : [])
                .playerProbe("player.partTab.\(tab.partId)", label: label, isSelected: tab.isSelected)
        }
    }

    @ViewBuilder
    private func tabContent(_ tab: PartTabUI) -> some View {
        let isCompleted = (tab.state == .CLOSED || tab.state == .SKIPPED)
        let isOpen = (tab.state == .OPEN)
        let isLocked = (tab.state == .PENDING)
        let isFailed = (tab.state == .GENERATION_FAILED)

        let bgColor: Color = {
            if tab.isSelected { return palette.primaryContainerColor }
            if tab.state == .CLOSED { return statusPalette.successBgColor }
            if isFailed { return palette.dangerBgColor }
            return palette.bgSubtleColor
        }()

        let borderColor: Color = {
            if tab.isSelected { return palette.primaryFillColor }
            if tab.state == .CLOSED { return statusPalette.successColor.opacity(0.45) }
            if isFailed { return palette.dangerTextColor.opacity(0.5) }
            return palette.borderColor
        }()

        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 6) {
                if tab.state == .CLOSED {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(statusPalette.successColor)
                        .accessibilityHidden(true)
                } else if tab.state == .SKIPPED {
                    Image(systemName: "forward.end.circle.fill")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(palette.textMutedColor)
                        .accessibilityHidden(true)
                } else if isLocked {
                    Image(systemName: "lock.fill")
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(palette.textMutedColor)
                        .accessibilityHidden(true)
                } else if isFailed {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(palette.dangerTextColor)
                        .accessibilityHidden(true)
                } else if tab.state == .AWAITING_CHOICE || tab.state == .GENERATING {
                    Image(systemName: "clock")
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(statusPalette.warningColor)
                        .accessibilityHidden(true)
                }

                Text(tab.name)
                    .font(.footnote.weight(tab.isSelected ? .bold : .semibold))
                    .foregroundStyle(tab.isSelected ? palette.primaryTextColor : palette.textColor)
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)

                if isOpen {
                    Text(
                        PlayerStrings.text(
                            "attempt.parts.openProgress",
                            locale: locale,
                            replacements: [
                                "answered": String(tab.answeredCount),
                                "total": String(tab.totalCount)
                            ]
                        )
                    )
                    .font(.caption2.weight(.semibold).monospacedDigit())
                    .foregroundStyle(tab.isSelected ? palette.primaryTextColor : palette.textSecondaryColor)
                    .fixedSize(horizontal: true, vertical: false)
                } else if !isCompleted {
                    Text(PlayerStrings.partStateLabel(tab.state, locale: locale))
                        .font(.caption2.weight(.medium))
                        .foregroundStyle(isFailed ? palette.dangerTextColor : palette.textMutedColor)
                        .fixedSize(horizontal: true, vertical: false)
                } else if tab.state == .SKIPPED {
                    Text(PlayerStrings.partStateLabel(.SKIPPED, locale: locale))
                        .font(.caption2.weight(.medium))
                        .foregroundStyle(palette.textMutedColor)
                        .fixedSize(horizontal: true, vertical: false)
                }
            }

            if isOpen {
                let ratio = tab.totalCount > 0 ? min(1.0, max(0.0, Double(tab.answeredCount) / Double(tab.totalCount))) : 0.0
                GeometryReader { proxy in
                    ZStack(alignment: .leading) {
                        Capsule()
                            .fill(palette.borderColor)
                            .frame(height: 4)
                        Capsule()
                            .fill(palette.primaryFillColor)
                            .frame(width: proxy.size.width * ratio, height: 4)
                    }
                }
                .frame(height: 4)
                .frame(minWidth: 80)
                .accessibilityHidden(true)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .frame(minHeight: 44)
        .background(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(bgColor)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .strokeBorder(borderColor, lineWidth: tab.isSelected ? 2 : 1)
        )
        .contentShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
    }

    private func tabAccessibilityLabel(_ tab: PartTabUI) -> String {
        let stateLabel = PlayerStrings.partStateLabel(tab.state, locale: locale)
        if tab.state == .OPEN {
            let progressLabel = PlayerStrings.text(
                "attempt.nav.progressLabel",
                locale: locale,
                replacements: [
                    "answered": String(tab.answeredCount),
                    "total": String(tab.totalCount)
                ]
            )
            return PlayerStrings.text(
                "attempt.parts.tabOpenAccessibility",
                locale: locale,
                replacements: [
                    "name": tab.name,
                    "state": stateLabel,
                    "progress": progressLabel
                ]
            )
        }
        return PlayerStrings.text(
            "attempt.parts.tabAccessibility",
            locale: locale,
            replacements: [
                "name": tab.name,
                "state": stateLabel
            ]
        )
    }
}
