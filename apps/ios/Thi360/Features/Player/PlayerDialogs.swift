import SwiftUI

struct PlayerDialogBackdrop<Content: View>: View {
    let dismissible: Bool
    let onDismiss: () -> Void
    @ViewBuilder let content: () -> Content

    @Environment(\.brandPalette) private var palette

    var body: some View {
        ZStack {
            Color.black.opacity(0.45)
                .ignoresSafeArea()
                .onTapGesture {
                    if dismissible {
                        onDismiss()
                    }
                }

            ScrollView {
                VStack {
                    content()
                        .padding(20)
                        .frame(maxWidth: 480)
                        .background(
                            RoundedRectangle(cornerRadius: 18, style: .continuous)
                                .fill(palette.bgSurfaceColor)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 18, style: .continuous)
                                .strokeBorder(palette.borderColor, lineWidth: 1)
                        )
                        .shadow(color: .black.opacity(0.18), radius: 20, x: 0, y: 10)
                }
                .frame(maxWidth: .infinity)
                .padding(20)
            }
            .scrollBounceBehavior(.basedOnSize)
        }
        .accessibilityAddTraits(.isModal)
    }
}

struct TakeoverDialog: View {
    let examTitle: String
    let lastSeenAt: Date?
    let isSending: Bool
    let errorCode: String?
    let locale: AppLocale
    let onConfirm: () -> Void
    let onCancel: () -> Void

    @Environment(\.brandPalette) private var palette

    var body: some View {
        PlayerDialogBackdrop(dismissible: !isSending, onDismiss: onCancel) {
            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 10) {
                    Image(systemName: "laptopcomputer.and.iphone")
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(palette.primaryTextColor)
                        .accessibilityHidden(true)
                    Text(PlayerStrings.text("attempt.takeover.title", locale: locale))
                        .font(.headline.weight(.bold))
                        .foregroundStyle(palette.textColor)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityAddTraits(.isHeader)
                        .accessibilityIdentifier("player.takeover.title")
                }

                Text(PlayerStrings.text("attempt.takeover.description", locale: locale))
                    .font(.subheadline)
                    .foregroundStyle(palette.textSecondaryColor)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("player.takeover.description")

                VStack(alignment: .leading, spacing: 6) {
                    if !examTitle.isEmpty {
                        detailRow(
                            label: PlayerStrings.text("attempt.takeover.attemptLabel", locale: locale),
                            value: examTitle,
                            identifier: "player.takeover.examTitle"
                        )
                    }
                    if let lastSeenAt {
                        detailRow(
                            label: PlayerStrings.text("attempt.takeover.lastSeenLabel", locale: locale),
                            value: TimerText.formatLastSeenAt(lastSeenAt, locale: locale),
                            identifier: "player.takeover.lastSeenAt"
                        )
                    }
                }
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(palette.bgSubtleColor)
                )

                VStack(alignment: .leading, spacing: 4) {
                    Text("• \(PlayerStrings.text("attempt.takeover.noteClock", locale: locale))")
                        .font(.footnote)
                        .foregroundStyle(palette.textMutedColor)
                        .fixedSize(horizontal: false, vertical: true)
                    Text("• \(PlayerStrings.text("attempt.takeover.noteUnsaved", locale: locale))")
                        .font(.footnote)
                        .foregroundStyle(palette.textMutedColor)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .playerProbe(
                    "player.takeover.notes",
                    label: "\(PlayerStrings.text("attempt.takeover.noteClock", locale: locale)) \(PlayerStrings.text("attempt.takeover.noteUnsaved", locale: locale))"
                )

                if errorCode != nil {
                    let errText = PlayerStrings.text("attempt.takeover.error", locale: locale)
                    Text(errText)
                        .font(.footnote.weight(.medium))
                        .foregroundStyle(palette.dangerTextColor)
                        .padding(10)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(palette.dangerBgColor)
                        )
                        .playerProbe("player.takeover.error", label: errText)
                }

                VStack(spacing: 8) {
                    let confirmLabel = PlayerStrings.text("attempt.takeover.confirm", locale: locale)
                    Button(action: onConfirm) {
                        HStack(spacing: 8) {
                            if isSending {
                                ProgressView()
                                    .tint(palette.onPrimaryColor)
                            }
                            Text(confirmLabel)
                                .font(.body.weight(.bold))
                        }
                        .foregroundStyle(palette.onPrimaryColor)
                        .frame(maxWidth: .infinity, minHeight: 46)
                        .background(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(palette.primaryFillColor)
                                .opacity(isSending ? 0.55 : 1.0)
                        )
                    }
                    .buttonStyle(.plain)
                    .disabled(isSending)
                    .playerProbe("player.takeover.confirmButton", label: confirmLabel, isButton: true, isEnabled: !isSending)

                    let cancelLabel = PlayerStrings.text("attempt.takeover.cancel", locale: locale)
                    Button(action: onCancel) {
                        Text(cancelLabel)
                            .font(.body.weight(.semibold))
                            .foregroundStyle(palette.textColor)
                            .frame(maxWidth: .infinity, minHeight: 44)
                            .background(
                                RoundedRectangle(cornerRadius: 10, style: .continuous)
                                    .fill(palette.bgSurfaceColor)
                            )
                            .overlay(
                                RoundedRectangle(cornerRadius: 10, style: .continuous)
                                    .strokeBorder(palette.borderStrongColor, lineWidth: 1)
                            )
                            .opacity(isSending ? 0.45 : 1.0)
                    }
                    .buttonStyle(.plain)
                    .disabled(isSending)
                    .playerProbe("player.takeover.cancelButton", label: cancelLabel, isButton: true, isEnabled: !isSending)
                }
            }
        }
        .accessibilityElement(children: .contain)
        .playerProbe("player.takeover.dialog")
    }

    private func detailRow(label: String, value: String, identifier: String) -> some View {
        let text = "\(label): \(value)"
        return VStack(alignment: .leading, spacing: 2) {
            Text(text)
                .font(.footnote.weight(.medium))
                .foregroundStyle(palette.textColor)
                .fixedSize(horizontal: false, vertical: true)
        }
        .playerProbe(identifier, label: text)
    }
}

struct FinishPartDialog: View {
    let summary: FinishPartSummaryUI
    let commandState: CommandState
    let locale: AppLocale
    let onConfirm: () -> Void
    let onReconcile: () -> Void
    let onDismiss: () -> Void

    @Environment(\.brandPalette) private var palette
    @Environment(\.statusPalette) private var statusPalette

    private var isSending: Bool {
        commandState == .sending(.finishPart)
    }

    private var isUnconfirmed: Bool {
        commandState == .unconfirmed(.finishPart)
    }

    var body: some View {
        PlayerDialogBackdrop(dismissible: !isSending && !isUnconfirmed, onDismiss: onDismiss) {
            if isUnconfirmed {
                unconfirmedContent
            } else {
                confirmContent
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("player.finishPart.dialog")
    }

    private var confirmContent: some View {
        VStack(alignment: .leading, spacing: 14) {
            let titleText = PlayerStrings.text("attempt.dialogs.finishPart.title", locale: locale)
            Text(titleText)
                .font(.headline.weight(.bold))
                .foregroundStyle(palette.textColor)
                .accessibilityAddTraits(.isHeader)
                .playerProbe("player.finishPart.title", label: titleText)

            if !summary.partName.isEmpty {
                Text(summary.partName)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(palette.primaryTextColor)
                    .playerProbe("player.finishPart.partName", label: summary.partName)
            }

            let warningText = PlayerStrings.text("attempt.dialogs.finishPart.warningIrreversible", locale: locale)
            Text(warningText)
                .font(.subheadline)
                .foregroundStyle(palette.textSecondaryColor)
                .fixedSize(horizontal: false, vertical: true)
                .playerProbe("player.finishPart.warning", label: warningText)

            VStack(alignment: .leading, spacing: 6) {
                let missingText = PlayerStrings.text(
                    "attempt.dialogs.finishPart.missingSummary",
                    locale: locale,
                    replacements: [
                        "missing": String(summary.missingCount),
                        "total": String(summary.totalCount)
                    ]
                )
                Text(missingText)
                    .font(.subheadline.weight(.semibold).monospacedDigit())
                    .foregroundStyle(summary.missingCount > 0 ? statusPalette.warningColor : statusPalette.successColor)
                    .playerProbe("player.finishPart.missingSummary", label: missingText)

                if summary.partialCount > 0 {
                    let partialText = PlayerStrings.text(
                        "attempt.dialogs.finishPart.partialSummary",
                        locale: locale,
                        replacements: ["partial": String(summary.partialCount)]
                    )
                    Text(partialText)
                        .font(.footnote.weight(.medium).monospacedDigit())
                        .foregroundStyle(statusPalette.warningColor)
                        .playerProbe("player.finishPart.partialSummary", label: partialText)
                }
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(palette.bgSubtleColor)
            )

            let noteKey = summary.nextPartNeedsChoice
                ? "attempt.dialogs.finishPart.nextPartChoiceNote"
                : "attempt.dialogs.finishPart.nextPartOpensNote"
            let noteText = PlayerStrings.text(noteKey, locale: locale)
            Text(noteText)
                .font(.footnote)
                .foregroundStyle(palette.textSecondaryColor)
                .fixedSize(horizontal: false, vertical: true)
                .playerProbe("player.finishPart.nextNote", label: noteText)

            VStack(spacing: 8) {
                let confirmLabel = PlayerStrings.text("attempt.dialogs.finishPart.confirm", locale: locale)
                Button(action: onConfirm) {
                    HStack(spacing: 8) {
                        if isSending {
                            ProgressView()
                                .tint(palette.onPrimaryColor)
                        }
                        Text(confirmLabel)
                            .font(.body.weight(.bold))
                    }
                    .foregroundStyle(palette.onPrimaryColor)
                    .frame(maxWidth: .infinity, minHeight: 46)
                    .background(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .fill(palette.primaryFillColor)
                            .opacity(isSending ? 0.55 : 1.0)
                    )
                }
                .buttonStyle(.plain)
                .disabled(isSending)
                .playerProbe("player.finishPart.confirmButton", label: confirmLabel, isButton: true, isEnabled: !isSending)

                let cancelLabel = PlayerStrings.text("attempt.dialogs.finishPart.cancel", locale: locale)
                Button(action: onDismiss) {
                    Text(cancelLabel)
                        .font(.body.weight(.semibold))
                        .foregroundStyle(palette.textColor)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .background(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(palette.bgSurfaceColor)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .strokeBorder(palette.borderStrongColor, lineWidth: 1)
                        )
                        .opacity(isSending ? 0.45 : 1.0)
                }
                .buttonStyle(.plain)
                .disabled(isSending)
                .playerProbe("player.finishPart.cancelButton", label: cancelLabel, isButton: true, isEnabled: !isSending)
            }
        }
    }

    private var unconfirmedContent: some View {
        let titleText = PlayerStrings.text("attempt.dialogs.finishPart.unconfirmedTitle", locale: locale)
        let descText = PlayerStrings.text("attempt.dialogs.finishPart.unconfirmedDesc", locale: locale)
        let reconcileLabel = PlayerStrings.text("attempt.dialogs.finishPart.reconcile", locale: locale)
        return VStack(alignment: .leading, spacing: 14) {
            Text(titleText)
                .font(.headline.weight(.bold))
                .foregroundStyle(statusPalette.warningColor)
                .accessibilityAddTraits(.isHeader)
                .playerProbe("player.finishPart.unconfirmedTitle", label: titleText)

            Text(descText)
                .font(.subheadline)
                .foregroundStyle(palette.textSecondaryColor)
                .fixedSize(horizontal: false, vertical: true)
                .playerProbe("player.finishPart.unconfirmedDesc", label: descText)

            Button(action: onReconcile) {
                Text(reconcileLabel)
                    .font(.body.weight(.bold))
                    .foregroundStyle(palette.onPrimaryColor)
                    .frame(maxWidth: .infinity, minHeight: 46)
                    .background(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .fill(palette.primaryFillColor)
                    )
            }
            .buttonStyle(.plain)
            .playerProbe("player.finishPart.reconcileButton", label: reconcileLabel, isButton: true)
        }
    }
}

struct SubmitDialog: View {
    let summary: SubmitSummaryUI
    let commandState: CommandState
    let locale: AppLocale
    let onConfirm: () -> Void
    let onReconcile: () -> Void
    let onDismiss: () -> Void

    @Environment(\.brandPalette) private var palette
    @Environment(\.statusPalette) private var statusPalette

    private var isSending: Bool {
        commandState == .sending(.submit)
    }

    private var isUnconfirmed: Bool {
        commandState == .unconfirmed(.submit)
    }

    var body: some View {
        PlayerDialogBackdrop(dismissible: !isSending && !isUnconfirmed, onDismiss: onDismiss) {
            if isUnconfirmed {
                unconfirmedContent
            } else {
                confirmContent
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("player.submit.dialog")
    }

    private var confirmContent: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(PlayerStrings.text("attempt.dialogs.submitConfirm.title", locale: locale))
                .font(.headline.weight(.bold))
                .foregroundStyle(palette.textColor)
                .accessibilityAddTraits(.isHeader)
                .accessibilityIdentifier("player.submit.title")

            Text(PlayerStrings.text("attempt.dialogs.submitConfirm.description", locale: locale))
                .font(.subheadline)
                .foregroundStyle(palette.textSecondaryColor)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityIdentifier("player.submit.description")

            LazyVGrid(columns: [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)], spacing: 8) {
                metricBox(
                    text: PlayerStrings.text(
                        "attempt.dialogs.submitConfirm.answeredCount",
                        locale: locale,
                        replacements: [
                            "answered": String(summary.answeredCount),
                            "total": String(summary.totalCount)
                        ]
                    ),
                    tint: statusPalette.successColor,
                    bg: statusPalette.successBgColor,
                    identifier: "player.submit.metric.answered"
                )
                metricBox(
                    text: PlayerStrings.text(
                        "attempt.dialogs.submitConfirm.partialCount",
                        locale: locale,
                        replacements: ["count": String(summary.partialCount)]
                    ),
                    tint: statusPalette.warningColor,
                    bg: statusPalette.warningBgColor,
                    identifier: "player.submit.metric.partial"
                )
                metricBox(
                    text: PlayerStrings.text(
                        "attempt.dialogs.submitConfirm.blankCount",
                        locale: locale,
                        replacements: ["count": String(summary.blankCount)]
                    ),
                    tint: palette.textSecondaryColor,
                    bg: palette.bgSubtleColor,
                    identifier: "player.submit.metric.blank"
                )
                metricBox(
                    text: PlayerStrings.text(
                        "attempt.dialogs.submitConfirm.flaggedCount",
                        locale: locale,
                        replacements: ["count": String(summary.flaggedCount)]
                    ),
                    tint: palette.dangerTextColor,
                    bg: palette.dangerBgColor,
                    identifier: "player.submit.metric.flagged"
                )
            }

            if !summary.partBreakdown.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    Text(PlayerStrings.text("attempt.dialogs.submitConfirm.partBreakdownTitle", locale: locale))
                        .font(.footnote.weight(.bold))
                        .foregroundStyle(palette.textColor)

                    ForEach(summary.partBreakdown) { row in
                        HStack(spacing: 8) {
                            Text(row.partName)
                                .font(.footnote.weight(.medium))
                                .foregroundStyle(palette.textColor)
                                .frame(maxWidth: .infinity, alignment: .leading)
                            Text(
                                PlayerStrings.text(
                                    "attempt.dialogs.submitConfirm.partMissingRow",
                                    locale: locale,
                                    replacements: [
                                        "missing": String(row.missingCount),
                                        "total": String(row.totalCount)
                                    ]
                                )
                            )
                            .font(.footnote.weight(.semibold).monospacedDigit())
                            .foregroundStyle(row.missingCount > 0 ? statusPalette.warningColor : statusPalette.successColor)
                        }
                        .accessibilityElement(children: .combine)
                        .accessibilityIdentifier("player.submit.partRow.\(row.partId)")
                    }
                }
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(palette.bgSubtleColor)
                )
            }

            if let essayStatus = summary.essayStatus {
                VStack(alignment: .leading, spacing: 6) {
                    Text(PlayerStrings.text("attempt.dialogs.submitConfirm.essaySectionTitle", locale: locale))
                        .font(.footnote.weight(.bold))
                        .foregroundStyle(palette.textColor)

                    let essayKey = essayStatus.hasUnsavedWords
                        ? "attempt.dialogs.submitConfirm.essayUnsaved"
                        : "attempt.dialogs.submitConfirm.essaySaved"
                    let essayText = PlayerStrings.text(
                        essayKey,
                        locale: locale,
                        replacements: ["words": String(essayStatus.totalWords)]
                    )
                    Text(essayText)
                        .font(.footnote.weight(.semibold).monospacedDigit())
                        .foregroundStyle(essayStatus.hasUnsavedWords ? statusPalette.warningColor : statusPalette.successColor)
                        .playerProbe("player.submit.essayStatus", label: essayText)

                    if !summary.essayWarnings.isEmpty {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(PlayerStrings.text("attempt.dialogs.submitConfirm.essayUnderLimitTitle", locale: locale))
                                .font(.caption.weight(.bold))
                                .foregroundStyle(statusPalette.warningColor)
                                .fixedSize(horizontal: false, vertical: true)

                            ForEach(summary.essayWarnings) { warning in
                                let warnText = PlayerStrings.text(
                                    "attempt.dialogs.submitConfirm.essayUnderLimitItem",
                                    locale: locale,
                                    replacements: [
                                        "displayNo": String(warning.displayNo),
                                        "wordCount": String(warning.wordCount),
                                        "wordLimit": String(warning.wordLimit)
                                    ]
                                )
                                Text(warnText)
                                    .font(.caption)
                                    .foregroundStyle(palette.textSecondaryColor)
                                    .fixedSize(horizontal: false, vertical: true)
                                    .playerProbe("player.submit.essayWarning.\(warning.itemId)", label: warnText)
                            }
                        }
                        .padding(10)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(
                            RoundedRectangle(cornerRadius: 8, style: .continuous)
                                .fill(statusPalette.warningBgColor)
                        )
                    }
                }
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(palette.bgSubtleColor)
                )
            }

            VStack(spacing: 8) {
                Button(action: onConfirm) {
                    HStack(spacing: 8) {
                        if isSending {
                            ProgressView()
                                .tint(palette.onPrimaryColor)
                        }
                        Text(PlayerStrings.text("attempt.dialogs.submitConfirm.confirm", locale: locale))
                            .font(.body.weight(.bold))
                    }
                    .foregroundStyle(palette.onPrimaryColor)
                    .frame(maxWidth: .infinity, minHeight: 46)
                    .background(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .fill(palette.primaryFillColor)
                            .opacity(isSending ? 0.55 : 1.0)
                    )
                }
                .buttonStyle(.plain)
                .disabled(isSending)
                .accessibilityIdentifier("player.submit.confirmButton")

                Button(action: onDismiss) {
                    Text(PlayerStrings.text("attempt.dialogs.submitConfirm.cancel", locale: locale))
                        .font(.body.weight(.semibold))
                        .foregroundStyle(palette.textColor)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .background(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(palette.bgSurfaceColor)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .strokeBorder(palette.borderStrongColor, lineWidth: 1)
                        )
                        .opacity(isSending ? 0.45 : 1.0)
                }
                .buttonStyle(.plain)
                .disabled(isSending)
                .accessibilityIdentifier("player.submit.cancelButton")
            }
        }
    }

    private var unconfirmedContent: some View {
        let titleText = PlayerStrings.text("attempt.dialogs.submitConfirm.unconfirmedTitle", locale: locale)
        let descText = PlayerStrings.text("attempt.dialogs.submitConfirm.unconfirmedDesc", locale: locale)
        let reconcileText = PlayerStrings.text("attempt.dialogs.submitConfirm.reconcile", locale: locale)
        return VStack(alignment: .leading, spacing: 14) {
            Text(titleText)
                .font(.headline.weight(.bold))
                .foregroundStyle(statusPalette.warningColor)
                .accessibilityAddTraits(.isHeader)
                .playerProbe("player.submit.unconfirmedTitle", label: titleText)

            Text(descText)
                .font(.subheadline)
                .foregroundStyle(palette.textSecondaryColor)
                .fixedSize(horizontal: false, vertical: true)
                .playerProbe("player.submit.unconfirmedDesc", label: descText)

            Button(action: onReconcile) {
                Text(reconcileText)
                    .font(.body.weight(.bold))
                    .foregroundStyle(palette.onPrimaryColor)
                    .frame(maxWidth: .infinity, minHeight: 46)
                    .background(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .fill(palette.primaryFillColor)
                    )
            }
            .buttonStyle(.plain)
            .playerProbe("player.submit.reconcileButton", label: reconcileText, isButton: true)
        }
    }

    private func metricBox(text: String, tint: Color, bg: Color, identifier: String) -> some View {
        Text(text)
            .font(.caption.weight(.semibold).monospacedDigit())
            .foregroundStyle(tint)
            .fixedSize(horizontal: false, vertical: true)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(10)
            .background(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(bg)
            )
            .playerProbe(identifier, label: text)
    }
}

struct LeaveConfirmDialog: View {
    let locale: AppLocale
    let onStay: () -> Void
    let onLeave: () -> Void

    @Environment(\.brandPalette) private var palette

    var body: some View {
        PlayerDialogBackdrop(dismissible: true, onDismiss: onStay) {
            VStack(alignment: .leading, spacing: 14) {
                Text(PlayerStrings.text("attempt.dialogs.leaveConfirm.title", locale: locale))
                    .font(.headline.weight(.bold))
                    .foregroundStyle(palette.textColor)
                    .accessibilityAddTraits(.isHeader)
                    .accessibilityIdentifier("player.leaveConfirm.title")

                Text(PlayerStrings.text("attempt.dialogs.leaveConfirm.description", locale: locale))
                    .font(.subheadline)
                    .foregroundStyle(palette.textSecondaryColor)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("player.leaveConfirm.description")

                VStack(spacing: 8) {
                    Button(action: onStay) {
                        Text(PlayerStrings.text("attempt.dialogs.leaveConfirm.stay", locale: locale))
                            .font(.body.weight(.bold))
                            .foregroundStyle(palette.onPrimaryColor)
                            .frame(maxWidth: .infinity, minHeight: 46)
                            .background(
                                RoundedRectangle(cornerRadius: 10, style: .continuous)
                                    .fill(palette.primaryFillColor)
                            )
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("player.leaveConfirm.stayButton")

                    Button(action: onLeave) {
                        Text(PlayerStrings.text("attempt.dialogs.leaveConfirm.leave", locale: locale))
                            .font(.body.weight(.semibold))
                            .foregroundStyle(palette.dangerTextColor)
                            .frame(maxWidth: .infinity, minHeight: 44)
                            .background(
                                RoundedRectangle(cornerRadius: 10, style: .continuous)
                                    .fill(palette.dangerBgColor)
                            )
                            .overlay(
                                RoundedRectangle(cornerRadius: 10, style: .continuous)
                                    .strokeBorder(palette.dangerTextColor.opacity(0.4), lineWidth: 1)
                            )
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("player.leaveConfirm.leaveButton")
                }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("player.leaveConfirm.dialog")
    }
}
