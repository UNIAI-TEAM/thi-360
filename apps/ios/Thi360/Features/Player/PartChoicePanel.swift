import SwiftUI

struct PartChoicePanel: View {
    let panel: ChoicePanelUI
    let locale: AppLocale
    let onSelectOption: (String) -> Void
    let onToggleSubject: (String) -> Void
    let onConfirm: () -> Void
    let onRetryLoadOptions: () -> Void
    let onBackToMyExams: () -> Void

    @Environment(\.brandPalette) private var palette
    @Environment(\.statusPalette) private var statusPalette

    private var selectedOption: Components.Schemas.ChoiceOptionView? {
        guard let code = panel.selectedOptionCode else { return nil }
        return panel.options.first(where: { $0.code == code })
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                headerCard
                bodySection
            }
            .frame(maxWidth: 680, alignment: .leading)
            .frame(maxWidth: .infinity)
            .padding(16)
        }
        .background(palette.bgPageColor)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("player.partChoice")
    }

    private var headerCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 10) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(PlayerStrings.text("attempt.partChoice.title", locale: locale))
                        .font(.title3.weight(.bold))
                        .foregroundStyle(palette.textColor)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityAddTraits(.isHeader)
                        .accessibilityIdentifier("player.partChoice.title")

                    if !panel.partName.isEmpty {
                        Text(panel.partName)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(palette.primaryTextColor)
                            .fixedSize(horizontal: false, vertical: true)
                            .accessibilityIdentifier("player.partChoice.partName")
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                if panel.isTimerVisible {
                    choiceTimerPill
                }
            }

            Text(PlayerStrings.text("attempt.partChoice.description", locale: locale))
                .font(.subheadline)
                .foregroundStyle(palette.textSecondaryColor)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityIdentifier("player.partChoice.description")

            if panel.isExpired {
                noticeBox(
                    text: PlayerStrings.text("attempt.partChoice.deadlineExpired", locale: locale),
                    isDanger: true,
                    identifier: "player.partChoice.expiredNotice"
                )
            } else if panel.isUrgent {
                noticeBox(
                    text: PlayerStrings.text("attempt.partChoice.deadlineWarning", locale: locale),
                    isDanger: true,
                    identifier: "player.partChoice.deadlineWarning"
                )
            }
        }
        .padding(16)
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

    private var choiceTimerPill: some View {
        let parts = TimerText.parts(remaining: panel.remaining)
        let formatted = TimerText.format(parts: parts)
        let prefix = PlayerStrings.text("attempt.partChoice.timerLabel", locale: locale)
        let a11y = TimerText.accessibilityLabel(prefix: prefix, parts: parts, locale: locale)
        let danger = panel.isUrgent || panel.isExpired
        let fg = danger ? palette.dangerTextColor : palette.textColor
        let bg = danger ? palette.dangerBgColor : palette.bgSubtleColor
        let border = danger ? palette.dangerTextColor : palette.borderColor

        return HStack(spacing: 5) {
            Image(systemName: "clock")
                .font(.caption.weight(.bold))
                .accessibilityHidden(true)
            Text(formatted)
                .font(.subheadline.weight(.bold).monospacedDigit())
        }
        .foregroundStyle(fg)
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(Capsule().fill(bg))
        .overlay(Capsule().strokeBorder(border, lineWidth: 1))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(a11y)
        .accessibilityValue(danger ? "urgent" : "normal")
        .accessibilityIdentifier("player.partChoice.timer")
    }

    @ViewBuilder
    private var bodySection: some View {
        switch panel.metadataState {
        case .loading:
            VStack(spacing: 12) {
                ProgressView()
                    .tint(palette.primaryTextColor)
                Text(AppStrings.common("common.loading", locale: locale))
                    .font(.subheadline)
                    .foregroundStyle(palette.textMutedColor)
            }
            .frame(maxWidth: .infinity, minHeight: 160)
            .padding(20)
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(palette.bgSurfaceColor)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .strokeBorder(palette.borderColor, lineWidth: 1)
            )
            .accessibilityIdentifier("player.partChoice.loading")

        case .failed, .empty:
            metadataErrorCard

        case .loaded:
            if panel.options.isEmpty {
                metadataErrorCard
            } else {
                optionsSelectionSection
            }
        }
    }

    private var metadataErrorCard: some View {
        VStack(spacing: 14) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 28, weight: .semibold))
                .foregroundStyle(palette.dangerTextColor)
                .accessibilityHidden(true)

            let errLoadTitle = AppStrings.common("prepare.errorLoad.title", locale: locale)
            Text(errLoadTitle)
                .font(.headline.weight(.bold))
                .foregroundStyle(palette.textColor)
                .multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader)
                .playerProbe("player.partChoice.errorLoadTitle", label: errLoadTitle)

            let errLoadDesc = AppStrings.common("prepare.errorLoad.description", locale: locale)
            Text(errLoadDesc)
                .font(.subheadline)
                .foregroundStyle(palette.textSecondaryColor)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .playerProbe("player.partChoice.errorLoadDescription", label: errLoadDesc)

            WrappingHStackLayout(horizontalSpacing: 10, verticalSpacing: 8) {
                let retryLabel = AppStrings.common("common.retry", locale: locale)
                Button(action: onRetryLoadOptions) {
                    Text(retryLabel)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(palette.onPrimaryColor)
                        .padding(.horizontal, 20)
                        .frame(minHeight: 44)
                        .background(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(palette.primaryFillColor)
                        )
                }
                .buttonStyle(.plain)
                .playerProbe("player.partChoice.retryLoad", label: retryLabel, isButton: true)

                let backLabel = PlayerStrings.text("attempt.errors.loadBackToMyExams", locale: locale)
                Button(action: onBackToMyExams) {
                    Text(backLabel)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(palette.textColor)
                        .padding(.horizontal, 18)
                        .frame(minHeight: 44)
                        .background(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(palette.bgSurfaceColor)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .strokeBorder(palette.borderStrongColor, lineWidth: 1)
                        )
                }
                .buttonStyle(.plain)
                .playerProbe("player.partChoice.backToMyExams", label: backLabel, isButton: true)
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(palette.bgSurfaceColor)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .strokeBorder(palette.borderColor, lineWidth: 1)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("player.partChoice.errorLoad")
    }

    private var optionsSelectionSection: some View {
        let interactive = !panel.isLocked && !panel.isSending && !panel.isExpired

        return VStack(alignment: .leading, spacing: 14) {
            Text(PlayerStrings.text("attempt.partChoice.legendOption", locale: locale))
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(palette.textColor)
                .accessibilityAddTraits(.isHeader)

            VStack(spacing: 10) {
                ForEach(Array(panel.options.enumerated()), id: \.offset) { _, option in
                    let code = option.code ?? ""
                    let isSelected = (!code.isEmpty && panel.selectedOptionCode == code)
                    optionCard(option: option, code: code, isSelected: isSelected, interactive: interactive)
                }
            }

            if panel.requiredSubjectCount > 0, let selectedOption, let subjects = selectedOption.subjects, !subjects.isEmpty {
                subjectPickerCard(subjects: subjects, interactive: interactive)
            }

            if let errorCode = panel.errorCode {
                noticeBox(
                    text: PlayerStrings.partChoiceErrorMessage(code: errorCode, locale: locale),
                    isDanger: true,
                    identifier: "player.partChoice.errorBanner"
                )
            }

            Button(action: onConfirm) {
                HStack(spacing: 8) {
                    if panel.isSending {
                        ProgressView()
                            .tint(palette.onPrimaryColor)
                    }
                    Text(PlayerStrings.text("attempt.partChoice.confirmCta", locale: locale))
                        .font(.body.weight(.bold))
                }
                .foregroundStyle(palette.onPrimaryColor)
                .frame(maxWidth: .infinity, minHeight: 48)
                .padding(.horizontal, 16)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(palette.primaryFillColor)
                        .opacity(panel.canConfirm ? 1.0 : 0.45)
                )
            }
            .buttonStyle(.plain)
            .disabled(!panel.canConfirm)
            .accessibilityIdentifier("player.partChoice.confirmButton")
        }
        .padding(16)
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

    private func optionCard(
        option: Components.Schemas.ChoiceOptionView,
        code: String,
        isSelected: Bool,
        interactive: Bool
    ) -> some View {
        let requiredSubjects = max(0, Int(option.chooseSubjects ?? 0))
        let countsText: String? = {
            guard let counts = option.countsByPick, !counts.isEmpty else { return nil }
            let joined = counts.map(String.init).joined(separator: " / ")
            return PlayerStrings.text(
                "attempt.partChoice.countsPerSubject",
                locale: locale,
                replacements: ["counts": joined]
            )
        }()
        let fixedSubjectsText: String? = {
            guard requiredSubjects == 0, let subjects = option.subjects, !subjects.isEmpty else { return nil }
            let names = subjects.compactMap { subj -> String? in
                guard let c = subj.code else { return nil }
                return SubjectNames.resolve(code: c, prepareSubjects: panel.subjectsByCode, locale: locale)
            }.filter { !$0.isEmpty }
            guard !names.isEmpty else { return nil }
            return PlayerStrings.text(
                "attempt.partChoice.fixedSubjects",
                locale: locale,
                replacements: ["subjects": names.joined(separator: ", ")]
            )
        }()
        let metricsParts: [String] = {
            var parts: [String] = []
            if let q = option.questionCount, q > 0 {
                parts.append(
                    PlayerStrings.text(
                        "attempt.partChoice.optionQuestions",
                        locale: locale,
                        replacements: ["count": String(q)]
                    )
                )
            }
            if let m = option.durationMinutes, m > 0 {
                parts.append(
                    PlayerStrings.text(
                        "attempt.partChoice.optionMinutes",
                        locale: locale,
                        replacements: ["minutes": String(m)]
                    )
                )
            }
            return parts
        }()

        return Button {
            guard interactive, !code.isEmpty else { return }
            onSelectOption(code)
        } label: {
            HStack(alignment: .top, spacing: 12) {
                ZStack {
                    Circle()
                        .fill(isSelected ? palette.primaryFillColor : Color.clear)
                        .frame(width: 22, height: 22)
                    Circle()
                        .strokeBorder(isSelected ? palette.primaryFillColor : palette.borderStrongColor, lineWidth: 1.5)
                        .frame(width: 22, height: 22)
                    if isSelected {
                        Image(systemName: "checkmark")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundStyle(palette.onPrimaryColor)
                    }
                }
                .padding(.top, 2)
                .accessibilityHidden(true)

                VStack(alignment: .leading, spacing: 4) {
                    let title = [code, option.name ?? ""]
                        .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                        .filter { !$0.isEmpty }
                        .joined(separator: " · ")
                    Text(title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(palette.textColor)
                        .fixedSize(horizontal: false, vertical: true)

                    if let countsText {
                        Text(countsText)
                            .font(.footnote)
                            .foregroundStyle(palette.textSecondaryColor)
                            .fixedSize(horizontal: false, vertical: true)
                            .playerProbe("player.partChoice.option.\(code).counts", label: countsText)
                    }

                    if let fixedSubjectsText {
                        Text(fixedSubjectsText)
                            .font(.footnote)
                            .foregroundStyle(palette.textMutedColor)
                            .fixedSize(horizontal: false, vertical: true)
                            .playerProbe("player.partChoice.option.\(code).fixedSubjects", label: fixedSubjectsText)
                    }

                    if !metricsParts.isEmpty {
                        Text(metricsParts.joined(separator: " · "))
                            .font(.footnote.monospacedDigit())
                            .foregroundStyle(palette.textMutedColor)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(12)
            .frame(maxWidth: .infinity, minHeight: 48, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(isSelected ? palette.primaryContainerColor : palette.bgPageColor)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .strokeBorder(isSelected ? palette.primaryFillColor : palette.borderColor, lineWidth: isSelected ? 2 : 1)
            )
            .opacity(interactive ? 1.0 : 0.6)
            .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(!interactive)
        .accessibilityAddTraits(isSelected ? [.isSelected] : [])
        .accessibilityIdentifier("player.partChoice.option.\(code)")
    }

    private func subjectPickerCard(
        subjects: [Components.Schemas.ChoiceSubjectView],
        interactive: Bool
    ) -> some View {
        let selectedCount = panel.selectedSubjectCodes.count
        let requiredCount = panel.requiredSubjectCount
        let limitReached = selectedCount >= requiredCount

        return VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Text(PlayerStrings.text("attempt.partChoice.legendSubjects", locale: locale))
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(palette.textColor)
                    .frame(maxWidth: .infinity, alignment: .leading)

                Text(
                    PlayerStrings.text(
                        "attempt.partChoice.selectedProgress",
                        locale: locale,
                        replacements: [
                            "selected": String(selectedCount),
                            "required": String(requiredCount)
                        ]
                    )
                )
                .font(.footnote.weight(.semibold).monospacedDigit())
                .foregroundStyle(selectedCount == requiredCount ? statusPalette.successColor : statusPalette.warningColor)
                .accessibilityIdentifier("player.partChoice.selectedProgress")
            }

            if limitReached {
                let limitText = PlayerStrings.text(
                    "attempt.partChoice.subjectsLimitReached",
                    locale: locale,
                    replacements: ["required": String(requiredCount)]
                )
                Text(limitText)
                    .font(.footnote)
                    .foregroundStyle(statusPalette.infoColor)
                    .fixedSize(horizontal: false, vertical: true)
                    .playerProbe("player.partChoice.limitReached", label: limitText)
            }

            VStack(spacing: 8) {
                ForEach(Array(subjects.enumerated()), id: \.offset) { _, subject in
                    let subCode = subject.code ?? ""
                    let isChecked = panel.selectedSubjectCodes.contains(subCode)
                    let isDisabled = !interactive || (!isChecked && limitReached)
                    let displayName = SubjectNames.resolve(
                        code: subCode,
                        prepareSubjects: panel.subjectsByCode,
                        locale: locale
                    )

                    Button {
                        guard !isDisabled, !subCode.isEmpty else { return }
                        onToggleSubject(subCode)
                    } label: {
                        HStack(spacing: 10) {
                            Text(displayName)
                                .font(.body.weight(isChecked ? .semibold : .regular))
                                .foregroundStyle(palette.textColor)
                                .fixedSize(horizontal: false, vertical: true)
                                .frame(maxWidth: .infinity, alignment: .leading)

                            ZStack {
                                RoundedRectangle(cornerRadius: 6, style: .continuous)
                                    .fill(isChecked ? palette.primaryFillColor : Color.clear)
                                    .frame(width: 22, height: 22)
                                RoundedRectangle(cornerRadius: 6, style: .continuous)
                                    .strokeBorder(isChecked ? palette.primaryFillColor : palette.borderStrongColor, lineWidth: 1.5)
                                    .frame(width: 22, height: 22)
                                if isChecked {
                                    Image(systemName: "checkmark")
                                        .font(.system(size: 11, weight: .bold))
                                        .foregroundStyle(palette.onPrimaryColor)
                                }
                            }
                            .accessibilityHidden(true)
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 10)
                        .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                        .background(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(isChecked ? palette.primaryContainerColor : palette.bgSurfaceColor)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .strokeBorder(isChecked ? palette.primaryFillColor : palette.borderColor, lineWidth: 1)
                        )
                        .opacity(isDisabled ? 0.45 : 1.0)
                    }
                    .buttonStyle(.plain)
                    .disabled(isDisabled)
                    .accessibilityAddTraits(isChecked ? [.isSelected] : [])
                    .playerProbe(
                        "player.partChoice.subject.\(subCode)",
                        label: displayName,
                        isButton: true,
                        isEnabled: !isDisabled,
                        isSelected: isChecked
                    )
                }
            }
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(palette.bgPageColor)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .strokeBorder(palette.borderColor, lineWidth: 1)
        )
    }

    private func noticeBox(text: String, isDanger: Bool, identifier: String) -> some View {
        HStack(alignment: .top, spacing: 8) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(isDanger ? palette.dangerTextColor : statusPalette.warningColor)
                .padding(.top, 2)
                .accessibilityHidden(true)
            Text(text)
                .font(.footnote.weight(.medium))
                .foregroundStyle(isDanger ? palette.dangerTextColor : statusPalette.warningColor)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(isDanger ? palette.dangerBgColor : statusPalette.warningBgColor)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .strokeBorder((isDanger ? palette.dangerTextColor : statusPalette.warningColor).opacity(0.45), lineWidth: 1)
        )
        .accessibilityElement(children: .combine)
        .playerProbe(identifier, label: text)
    }
}
