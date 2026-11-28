import SwiftUI

struct PlayerTopBar: View {
    let ui: PlayerUI
    let locale: AppLocale
    let onBack: () -> Void
    let onToggleCollapse: () -> Void
    let onAction: () -> Void
    let onSelectPart: (PartID) -> Void

    @Environment(\.brandPalette) private var palette
    @Environment(\.statusPalette) private var statusPalette

    var body: some View {
        VStack(spacing: 0) {
            VStack(spacing: 0) {
                if ui.headerCollapsed {
                    collapsedBar
                } else {
                    expandedBar
                }

                if !ui.headerCollapsed && ui.showPartTabs {
                    Divider()
                        .overlay(palette.borderColor)
                    PartTabs(
                        tabs: ui.partTabs,
                        locale: locale,
                        onSelectPart: onSelectPart
                    )
                }
            }
            .background(palette.bgSurfaceColor)
            .overlay(alignment: .bottom) {
                Rectangle()
                    .fill(palette.borderColor)
                    .frame(height: 1)
            }

            if ui.isOffline {
                offlineBanner
            }

            if ui.readOnlyBanner != .none {
                readOnlyBanner
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("player.topBar")
    }

    private var expandedBar: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .center, spacing: 8) {
                backButton

                let titleText = ui.examTitle.isEmpty
                    ? PlayerStrings.text("attempt.player.pageTitle", locale: locale)
                    : ui.examTitle
                Text(titleText)
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(palette.textColor)
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .playerProbe("player.topBar.examTitle", label: titleText)

                collapseButton
            }

            ViewThatFits(in: .horizontal) {
                HStack(spacing: 8) {
                    if ui.timer.visible {
                        timerPill
                    }
                    saveStatusPill
                    Spacer(minLength: 4)
                    actionButton
                }
                VStack(alignment: .leading, spacing: 8) {
                    WrappingHStackLayout(horizontalSpacing: 8, verticalSpacing: 6) {
                        if ui.timer.visible {
                            timerPill
                        }
                        saveStatusPill
                    }
                    actionButton
                }
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
    }

    private var collapsedBar: some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: 8) {
                backButton
                if !ui.activePartDisplayName.isEmpty {
                    Text(ui.activePartDisplayName)
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(palette.textColor)
                        .lineLimit(1)
                        .playerProbe("player.topBar.collapsedPartName", label: ui.activePartDisplayName)
                }
                if ui.timer.visible {
                    timerPill
                }
                saveStatusPill
                Spacer(minLength: 4)
                actionButton
                collapseButton
            }
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 8) {
                    backButton
                    if !ui.activePartDisplayName.isEmpty {
                        Text(ui.activePartDisplayName)
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(palette.textColor)
                            .lineLimit(1)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .playerProbe("player.topBar.collapsedPartName", label: ui.activePartDisplayName)
                    } else {
                        Spacer(minLength: 0)
                    }
                    collapseButton
                }
                WrappingHStackLayout(horizontalSpacing: 8, verticalSpacing: 6) {
                    if ui.timer.visible {
                        timerPill
                    }
                    saveStatusPill
                    actionButton
                }
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
    }

    private var backButton: some View {
        let label = PlayerStrings.text("attempt.header.backLabel", locale: locale)
        return Button(action: onBack) {
            Image(systemName: "chevron.backward")
                .font(.body.weight(.semibold))
                .foregroundStyle(palette.textColor)
                .frame(minWidth: 44, minHeight: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
        .playerProbe("player.topBar.backButton", label: label, isButton: true)
    }

    private var collapseButton: some View {
        let labelKey = ui.headerCollapsed ? "attempt.header.expandHeader" : "attempt.header.collapseHeader"
        let label = PlayerStrings.text(labelKey, locale: locale)
        return Button(action: onToggleCollapse) {
            Image(systemName: ui.headerCollapsed ? "chevron.down" : "chevron.up")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(palette.textSecondaryColor)
                .frame(minWidth: 44, minHeight: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
        .playerProbe("player.topBar.collapseToggle", label: label, isButton: true)
    }

    private var timerPill: some View {
        let prefix: String
        if ui.timer.isShared {
            prefix = PlayerStrings.text("attempt.timer.sharedRemaining", locale: locale)
        } else {
            prefix = PlayerStrings.text(
                "attempt.timer.partRemaining",
                locale: locale,
                replacements: ["ord": String(ui.timer.partOrd ?? 1)]
            )
        }
        let a11y = TimerText.accessibilityLabel(prefix: prefix, parts: ui.timer.parts, locale: locale)
        let fgColor = ui.timer.isUrgent ? palette.dangerTextColor : palette.textColor
        let bgColor = ui.timer.isUrgent ? palette.dangerBgColor : palette.bgSubtleColor
        let borderColor = ui.timer.isUrgent ? palette.dangerTextColor : palette.borderColor

        return HStack(spacing: 5) {
            Image(systemName: "clock")
                .font(.caption.weight(.bold))
                .accessibilityHidden(true)
            Text(ui.timer.formatted)
                .font(.subheadline.weight(.bold).monospacedDigit())
                .fixedSize(horizontal: true, vertical: false)
        }
        .foregroundStyle(fgColor)
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(
            Capsule(style: .continuous)
                .fill(bgColor)
        )
        .overlay(
            Capsule(style: .continuous)
                .strokeBorder(borderColor, lineWidth: 1)
        )
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(a11y)
        .accessibilityValue(ui.timer.isUrgent ? "urgent" : "normal")
        .playerProbe(
            "player.topBar.timer",
            label: a11y,
            value: ui.timer.isUrgent ? "urgent" : "normal"
        )
    }

    private var saveStatusPill: some View {
        let parts = PlayerStrings.saveStatusText(ui.saveStatus, locale: locale)
        let displayText = parts.reason.map { "\(parts.label) · \($0)" } ?? parts.label

        let symbolName: String
        let fgColor: Color
        let bgColor: Color
        switch ui.saveStatus {
        case .saved:
            symbolName = "checkmark.circle.fill"
            fgColor = statusPalette.successColor
            bgColor = statusPalette.successBgColor
        case .saving:
            symbolName = "arrow.triangle.2.circlepath"
            fgColor = statusPalette.infoColor
            bgColor = statusPalette.infoBgColor
        case .offline:
            symbolName = "wifi.slash"
            fgColor = statusPalette.warningColor
            bgColor = statusPalette.warningBgColor
        case let .unsaved(reason):
            switch reason {
            case .rejected, .sessionConflict:
                symbolName = "exclamationmark.triangle.fill"
                fgColor = palette.dangerTextColor
                bgColor = palette.dangerBgColor
            case .networkError, .serverError, .rateLimited:
                symbolName = "exclamationmark.circle.fill"
                fgColor = statusPalette.warningColor
                bgColor = statusPalette.warningBgColor
            }
        }

        return HStack(spacing: 5) {
            Image(systemName: symbolName)
                .font(.caption2.weight(.semibold))
                .accessibilityHidden(true)
            Text(displayText)
                .font(.caption.weight(.semibold))
                .lineLimit(2)
                .fixedSize(horizontal: false, vertical: true)
        }
        .foregroundStyle(fgColor)
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(
            Capsule(style: .continuous)
                .fill(bgColor)
        )
        .accessibilityElement(children: .combine)
        .playerProbe("player.topBar.saveStatus", label: displayText)
    }

    @ViewBuilder
    private var actionButton: some View {
        switch ui.topAction {
        case .none:
            EmptyView()
        case .finishPart:
            primaryActionButton(
                title: PlayerStrings.text("attempt.header.finishPart", locale: locale),
                identifier: "player.topBar.actionButton"
            )
        case .submit:
            primaryActionButton(
                title: PlayerStrings.text("attempt.header.submit", locale: locale),
                identifier: "player.topBar.actionButton"
            )
        }
    }

    private func primaryActionButton(title: String, identifier: String) -> some View {
        Button(action: onAction) {
            Text(title)
                .font(.footnote.weight(.bold))
                .foregroundStyle(palette.onPrimaryColor)
                .padding(.horizontal, 14)
                .frame(minHeight: 44)
                .background(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(palette.primaryFillColor)
                        .opacity(ui.topActionEnabled ? 1.0 : 0.45)
                )
                .contentShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(!ui.topActionEnabled)
        .playerProbe(identifier, label: title, isButton: true, isEnabled: ui.topActionEnabled)
    }

    private var offlineBanner: some View {
        let bannerText = PlayerStrings.text("attempt.player.offlineBanner", locale: locale)
        return HStack(alignment: .top, spacing: 8) {
            Image(systemName: "wifi.slash")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(statusPalette.warningColor)
                .padding(.top, 2)
                .accessibilityHidden(true)
            Text(bannerText)
                .font(.footnote.weight(.medium))
                .foregroundStyle(statusPalette.warningColor)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
        .background(statusPalette.warningBgColor)
        .overlay(alignment: .bottom) {
            Rectangle()
                .fill(statusPalette.warningColor.opacity(0.35))
                .frame(height: 1)
        }
        .accessibilityElement(children: .combine)
        .playerProbe("player.offlineBanner", label: bannerText)
    }

    private var readOnlyBanner: some View {
        let key = ui.readOnlyBanner == .partClosed
            ? "attempt.readOnly.partClosed"
            : "attempt.readOnly.unavailable"
        let text = PlayerStrings.text(key, locale: locale)
        return HStack(alignment: .top, spacing: 8) {
            Image(systemName: "eye.fill")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(statusPalette.infoColor)
                .padding(.top, 2)
                .accessibilityHidden(true)
            Text(text)
                .font(.footnote.weight(.medium))
                .foregroundStyle(statusPalette.infoColor)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
        .background(statusPalette.infoBgColor)
        .overlay(alignment: .bottom) {
            Rectangle()
                .fill(statusPalette.infoColor.opacity(0.3))
                .frame(height: 1)
        }
        .accessibilityElement(children: .combine)
        .playerProbe("player.readOnlyBanner", label: text)
    }
}
