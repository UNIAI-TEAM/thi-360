import SwiftUI

struct PrepareHeaderPresentation: Equatable {
    let examKindLabel: String
    let scopeLabel: String
    let reviewBadge: String?
    let title: String
    let summary: String
}

struct PrepareHeaderSection: View {
    let response: AttemptPrepareResponse
    let metrics: SelectedPathMetrics
    let locale: AppLocale

    @Environment(\.brandPalette) private var palette
    @Environment(\.statusPalette) private var statusPalette

    var rendered: PrepareHeaderPresentation {
        let examKindLabel = PrepareFormatting.localizedName(
            nameVi: response.examKind.nameVi,
            nameEn: response.examKind.nameEn,
            code: response.examKind.code,
            locale: locale
        )
        let scopeLabel = PrepareStrings.scopeLabel(
            scope: response.scope,
            subject: response.subject,
            locale: locale
        )
        let reviewBadge: String? = response.buildMode == PrepareConstants.reviewBuildMode
            ? PrepareStrings.text("prepare.reviewBadge", locale: locale)
            : nil
        let summary = PrepareFormatting.summaryLine(metrics: metrics, locale: locale)
        return PrepareHeaderPresentation(
            examKindLabel: examKindLabel,
            scopeLabel: scopeLabel,
            reviewBadge: reviewBadge,
            title: response.examName,
            summary: summary
        )
    }

    var body: some View {
        let presentation = rendered

        VStack(alignment: .leading, spacing: 10) {
            PrepareFlowWrap(spacing: 8, lineSpacing: 6) {
                if !presentation.examKindLabel.isEmpty {
                    PrepareChip(
                        text: presentation.examKindLabel,
                        foreground: palette.primaryTextColor,
                        background: palette.primaryContainerColor,
                        identifier: "prepare.header.examKind"
                    )
                }
                if !presentation.scopeLabel.isEmpty {
                    PrepareChip(
                        text: presentation.scopeLabel,
                        foreground: palette.textSecondaryColor,
                        background: palette.bgSubtleColor,
                        identifier: "prepare.header.scope"
                    )
                }
                if let reviewBadge = presentation.reviewBadge {
                    PrepareChip(
                        text: reviewBadge,
                        foreground: statusPalette.infoColor,
                        background: statusPalette.infoBgColor,
                        identifier: "prepare.header.reviewBadge"
                    )
                }
            }

            Text(presentation.title)
                .font(.title2.weight(.bold))
                .foregroundStyle(palette.textColor)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
                .accessibilityIdentifier("prepare.header.title")

            if !presentation.summary.isEmpty {
                Text(presentation.summary)
                    .font(.subheadline)
                    .foregroundStyle(palette.textMutedColor)
                    .monospacedDigit()
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("prepare.header.summary")
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("prepare.header")
    }
}

struct PreparePreChoiceSection: View {
    let preChoice: Components.Schemas.PreChoiceView
    let selection: PreChoiceSelection
    let metrics: SelectedPathMetrics
    let locale: AppLocale
    let supportContact: SupportContact
    let onSelectOption: (String) -> Void
    let onToggleSubject: (String) -> Void

    @Environment(\.brandPalette) private var palette
    @Environment(\.statusPalette) private var statusPalette

    var body: some View {
        let model = rendered
        let options = preChoice.options ?? []
        let selectedOption = selection.selectedOption(in: preChoice)
        let requiredSubjects = PreChoiceSelection.requiredSubjectCount(for: selectedOption)

        PrepareCard {
            PrepareSectionHeading(
                title: model.title,
                identifier: "prepare.preChoice.heading"
            )

            if options.isEmpty {
                HStack(alignment: .top, spacing: 10) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .font(.body)
                        .foregroundStyle(palette.dangerTextColor)
                        .accessibilityHidden(true)

                    VStack(alignment: .leading, spacing: 4) {
                        Text(model.noServingTitle ?? "")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(palette.dangerTextColor)
                            .fixedSize(horizontal: false, vertical: true)
                            .accessibilityIdentifier("prepare.noServingOption.title")

                        Text(model.noServingDescription ?? "")
                            .font(.subheadline)
                            .foregroundStyle(palette.textSecondaryColor)
                            .fixedSize(horizontal: false, vertical: true)
                            .accessibilityIdentifier("prepare.noServingOption.description")
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(14)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(palette.dangerBgColor)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .strokeBorder(palette.dangerTextColor.opacity(0.45), lineWidth: 1)
                )
                .accessibilityElement(children: .contain)
                .accessibilityIdentifier("prepare.noServingOption")
            } else {
                Text(PrepareStrings.text("prepare.preChoice.legend", locale: locale))
                    .font(.footnote.weight(.medium))
                    .foregroundStyle(palette.textSecondaryColor)
                    .fixedSize(horizontal: false, vertical: true)

                VStack(spacing: 10) {
                    ForEach(Array(options.enumerated()), id: \.offset) { _, option in
                        let code = option.code ?? ""
                        let isSelected = selection.optionCode == code && !code.isEmpty
                        optionRow(option: option, code: code, isSelected: isSelected)
                    }
                }

                if requiredSubjects > 0, !model.subjects.isEmpty {
                    subjectPickerBlock(model: model, requiredSubjects: requiredSubjects)
                }

                if let selectedOption, let code = selectedOption.code, !code.isEmpty {
                    let pathSummary = PrepareFormatting.optionMetricsLine(
                        questionCount: metrics.questionCount,
                        durationMinutes: metrics.durationMinutes,
                        maxPoints: metrics.maxPoints,
                        locale: locale
                    )
                    ViewThatFits(in: .horizontal) {
                        HStack(spacing: 8) {
                            Text(PrepareStrings.text("prepare.preChoice.selectedPathSummary", locale: locale, replacements: ["code": code]))
                                .font(.footnote)
                                .foregroundStyle(palette.textMutedColor)
                            if !pathSummary.isEmpty {
                                Text(pathSummary)
                                    .font(.footnote.weight(.semibold))
                                    .foregroundStyle(palette.textColor)
                                    .monospacedDigit()
                            }
                            Spacer(minLength: 0)
                        }
                        VStack(alignment: .leading, spacing: 2) {
                            Text(PrepareStrings.text("prepare.preChoice.selectedPathSummary", locale: locale, replacements: ["code": code]))
                                .font(.footnote)
                                .foregroundStyle(palette.textMutedColor)
                            if !pathSummary.isEmpty {
                                Text(pathSummary)
                                    .font(.footnote.weight(.semibold))
                                    .foregroundStyle(palette.textColor)
                                    .monospacedDigit()
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                    .background(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .fill(palette.bgPageColor)
                    )
                    .accessibilityElement(children: .combine)
                    .accessibilityIdentifier("prepare.preChoice.selectedPathSummary")
                }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("prepare.preChoice.section")
    }

    private func optionRow(
        option: Components.Schemas.ChoiceOptionView,
        code: String,
        isSelected: Bool
    ) -> some View {
        let subjects = option.subjects ?? []
        let requiredCount = PreChoiceSelection.requiredSubjectCount(for: option)
        let optionMetrics = PrepareFormatting.optionMetricsLine(
            questionCount: option.questionCount.map(Int.init),
            durationMinutes: option.durationMinutes.map(Int.init),
            maxPoints: option.maxPoints.map(PrepareMetrics.decimalFromDouble),
            locale: locale
        )
        let subjectNames = subjects
            .map { PrepareFormatting.localizedName(nameVi: $0.nameVi, nameEn: $0.nameEn, code: $0.code, locale: locale) }
            .filter { !$0.isEmpty }
            .joined(separator: " · ")

        return Button {
            guard !code.isEmpty else { return }
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
                    let titleText = [code, option.name ?? ""]
                        .filter { !$0.isEmpty }
                        .joined(separator: " · ")
                    Text(titleText)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(palette.textColor)
                        .fixedSize(horizontal: false, vertical: true)

                    if requiredCount > 0 && !subjects.isEmpty {
                        Text(
                            PrepareStrings.text(
                                "prepare.preChoice.chooseSubjectsCount",
                                locale: locale,
                                replacements: [
                                    "count": String(requiredCount),
                                    "total": String(subjects.count)
                                ]
                            )
                        )
                        .font(.footnote)
                        .foregroundStyle(palette.textMutedColor)
                        .fixedSize(horizontal: false, vertical: true)
                    } else if !subjectNames.isEmpty {
                        Text(subjectNames)
                            .font(.footnote)
                            .foregroundStyle(palette.textMutedColor)
                            .fixedSize(horizontal: false, vertical: true)
                    }

                    if !optionMetrics.isEmpty {
                        Text(optionMetrics)
                            .font(.footnote)
                            .foregroundStyle(palette.textSecondaryColor)
                            .monospacedDigit()
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(14)
            .frame(maxWidth: .infinity, minHeight: 48, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(isSelected ? palette.primaryContainerColor : palette.bgSurfaceColor)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .strokeBorder(
                        isSelected ? palette.primaryFillColor : palette.borderColor,
                        lineWidth: isSelected ? 2 : 1
                    )
            )
            .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? [.isSelected] : [])
        .accessibilityIdentifier("prepare.preChoice.option.\(code)")
    }

    private func subjectPickerBlock(
        model: PreparePreChoicePresentation,
        requiredSubjects: Int
    ) -> some View {
        let pickedCount = selection.subjectCodes.count
        let legendText = model.subjectLegend ?? ""
        let progressText = model.subjectProgress ?? ""

        return VStack(alignment: .leading, spacing: 10) {
            ViewThatFits(in: .horizontal) {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text(legendText)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(palette.textColor)
                        .accessibilityIdentifier("prepare.preChoice.subjectLegend")
                    Spacer(minLength: 8)
                    Text(progressText)
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(pickedCount == requiredSubjects ? statusPalette.successColor : statusPalette.warningColor)
                        .monospacedDigit()
                        .accessibilityIdentifier("prepare.preChoice.subjectProgress")
                }
                VStack(alignment: .leading, spacing: 4) {
                    Text(legendText)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(palette.textColor)
                        .accessibilityIdentifier("prepare.preChoice.subjectLegend")
                    Text(progressText)
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(pickedCount == requiredSubjects ? statusPalette.successColor : statusPalette.warningColor)
                        .monospacedDigit()
                        .accessibilityIdentifier("prepare.preChoice.subjectProgress")
                }
            }

            VStack(spacing: 8) {
                ForEach(Array(model.subjects.enumerated()), id: \.offset) { _, subject in
                    let subCode = subject.code
                    let isChecked = subject.isChecked
                    let isDisabled = subject.isDisabled
                    let label = subject.label

                    Button {
                        guard !subCode.isEmpty else { return }
                        onToggleSubject(subCode)
                    } label: {
                        HStack(spacing: 12) {
                            Text(label)
                                .font(.body.weight(isChecked ? .medium : .regular))
                                .foregroundStyle(isChecked ? palette.textColor : palette.textSecondaryColor)
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
                        .contentShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .disabled(isDisabled)
                    .accessibilityAddTraits(isChecked ? [.isSelected] : [])
                    .accessibilityIdentifier("prepare.preChoice.subject.\(subCode)")
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
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("prepare.preChoice.subjects")
    }
}

struct PrepareStructureSection: View {
    let response: AttemptPrepareResponse
    let locale: AppLocale

    @Environment(\.brandPalette) private var palette
    @Environment(\.statusPalette) private var statusPalette

    var body: some View {
        let model = rendered

        PrepareCard {
            PrepareSectionHeading(
                title: model.title,
                identifier: "prepare.structure.heading"
            )

            HStack(alignment: .top, spacing: 10) {
                Image(systemName: "clock")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(palette.primaryTextColor)
                    .padding(.top, 2)
                    .accessibilityHidden(true)

                VStack(alignment: .leading, spacing: 4) {
                    Text(model.timingTitle)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(palette.textColor)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityIdentifier("prepare.structure.timingTitle")

                    Text(model.timingDescription)
                        .font(.footnote)
                        .foregroundStyle(palette.textSecondaryColor)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityIdentifier("prepare.structure.timingDescription")
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(palette.bgPageColor)
            )
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("prepare.structure.timing")

            if !model.parts.isEmpty {
                VStack(spacing: 10) {
                    ForEach(Array(model.parts.enumerated()), id: \.offset) { index, part in
                        partRow(index: index, part: part)
                    }
                }
            }

            if let formatsLabel = model.answerFormatsLabel, !model.answerFormats.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Text(formatsLabel)
                        .font(.footnote.weight(.medium))
                        .foregroundStyle(palette.textMutedColor)
                        .accessibilityIdentifier("prepare.structure.answerFormatsLabel")

                    PrepareFlowWrap(spacing: 8, lineSpacing: 6) {
                        ForEach(Array(model.answerFormats.enumerated()), id: \.offset) { idx, label in
                            PrepareChip(
                                text: label,
                                foreground: palette.textSecondaryColor,
                                background: palette.bgSubtleColor,
                                identifier: "prepare.structure.answerFormat.\(idx)"
                            )
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityElement(children: .contain)
                .accessibilityIdentifier("prepare.structure.answerFormats")
            }

            if let listeningTitle = model.listeningTitle, let listeningBody = model.listeningBody {
                HStack(alignment: .top, spacing: 10) {
                    Image(systemName: "headphones")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(statusPalette.infoColor)
                        .padding(.top, 2)
                        .accessibilityHidden(true)

                    VStack(alignment: .leading, spacing: 4) {
                        Text(listeningTitle)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(statusPalette.infoColor)
                            .fixedSize(horizontal: false, vertical: true)
                            .accessibilityIdentifier("prepare.structure.listeningTitle")

                        Text(listeningBody)
                            .font(.footnote)
                            .foregroundStyle(palette.textSecondaryColor)
                            .fixedSize(horizontal: false, vertical: true)
                            .accessibilityIdentifier("prepare.structure.listeningBody")
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(12)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(statusPalette.infoBgColor)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .strokeBorder(statusPalette.infoColor.opacity(0.45), lineWidth: 1)
                )
                .accessibilityElement(children: .contain)
                .accessibilityIdentifier("prepare.structure.listeningPolicy")
            }

            if let omittedLabel = model.omittedGroupsLabel, !model.omittedGroups.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    Text(omittedLabel)
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(palette.textSecondaryColor)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityIdentifier("prepare.structure.omittedGroupsLabel")

                    ForEach(Array(model.omittedGroups.enumerated()), id: \.offset) { idx, line in
                        HStack(alignment: .top, spacing: 6) {
                            Text("•")
                                .font(.footnote)
                                .foregroundStyle(palette.textMutedColor)
                                .accessibilityHidden(true)
                            Text(line)
                                .font(.footnote)
                                .foregroundStyle(palette.textMutedColor)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        .accessibilityIdentifier("prepare.structure.omittedGroup.\(idx)")
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(12)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(palette.bgPageColor)
                )
                .accessibilityElement(children: .contain)
                .accessibilityIdentifier("prepare.structure.omittedGroups")
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("prepare.structure.section")
    }

    private func partRow(index: Int, part: PrepareStructurePartRowPresentation) -> some View {
        let isRequired = part.badge == PrepareStrings.text("prepare.required", locale: locale)

        return HStack(alignment: .top, spacing: 12) {
            Text("\(part.order)")
                .font(.subheadline.weight(.bold))
                .monospacedDigit()
                .foregroundStyle(palette.primaryTextColor)
                .frame(width: 28, height: 28)
                .background(
                    Circle()
                        .fill(palette.primaryContainerColor)
                )
                .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 4) {
                ViewThatFits(in: .horizontal) {
                    HStack(alignment: .firstTextBaseline, spacing: 8) {
                        Text(part.name)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(palette.textColor)
                            .fixedSize(horizontal: false, vertical: true)
                        Spacer(minLength: 4)
                        requiredChip(text: part.badge, isRequired: isRequired)
                    }
                    VStack(alignment: .leading, spacing: 4) {
                        Text(part.name)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(palette.textColor)
                            .fixedSize(horizontal: false, vertical: true)
                        requiredChip(text: part.badge, isRequired: isRequired)
                    }
                }

                if !part.metrics.isEmpty {
                    Text(part.metrics)
                        .font(.footnote)
                        .foregroundStyle(palette.textSecondaryColor)
                        .monospacedDigit()
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(palette.bgSurfaceColor)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .strokeBorder(palette.borderColor, lineWidth: 1)
        )
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("prepare.structure.part.\(index)")
    }

    private func requiredChip(text: String, isRequired: Bool) -> some View {
        PrepareChip(
            text: text,
            foreground: isRequired ? palette.textSecondaryColor : statusPalette.warningColor,
            background: isRequired ? palette.bgSubtleColor : statusPalette.warningBgColor
        )
    }
}

struct PrepareAfterStartChoicesSection: View {
    let choices: [Components.Schemas.AfterStartChoiceView]
    let locale: AppLocale

    @Environment(\.brandPalette) private var palette

    var body: some View {
        let model = rendered

        PrepareCard {
            PrepareSectionHeading(
                title: model.title,
                identifier: "prepare.afterStartChoices.heading"
            )

            VStack(spacing: 12) {
                ForEach(Array(model.groups.enumerated()), id: \.offset) { choiceIdx, group in
                    VStack(alignment: .leading, spacing: 8) {
                        if !group.partName.isEmpty {
                            Text(group.partName)
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(palette.textColor)
                                .fixedSize(horizontal: false, vertical: true)
                                .accessibilityIdentifier("prepare.afterStartChoices.part.\(choiceIdx)")
                        }

                        ForEach(Array(group.options.enumerated()), id: \.offset) { optIdx, opt in
                            VStack(alignment: .leading, spacing: 2) {
                                Text(opt.headline)
                                    .font(.subheadline.weight(.medium))
                                    .foregroundStyle(palette.textColor)
                                    .fixedSize(horizontal: false, vertical: true)
                                if !opt.subjectsLine.isEmpty {
                                    Text(opt.subjectsLine)
                                        .font(.footnote)
                                        .foregroundStyle(palette.textMutedColor)
                                        .fixedSize(horizontal: false, vertical: true)
                                }
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .accessibilityElement(children: .combine)
                            .accessibilityIdentifier("prepare.afterStartChoices.option.\(choiceIdx).\(optIdx)")
                        }
                    }
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(palette.bgPageColor)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .strokeBorder(palette.borderColor, lineWidth: 1)
                    )
                }
            }

            VStack(alignment: .leading, spacing: 6) {
                bulletLine(
                    text: model.deadlineNote,
                    identifier: "prepare.afterStartChoices.deadlineNote"
                )
                bulletLine(
                    text: model.timerNote,
                    identifier: "prepare.afterStartChoices.timerNote"
                )
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("prepare.afterStartChoices.section")
    }

    private func bulletLine(text: String, identifier: String) -> some View {
        HStack(alignment: .top, spacing: 6) {
            Text("•")
                .font(.footnote)
                .foregroundStyle(palette.textSecondaryColor)
                .accessibilityHidden(true)
            Text(text)
                .font(.footnote)
                .foregroundStyle(palette.textSecondaryColor)
                .fixedSize(horizontal: false, vertical: true)
        }
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier(identifier)
    }
}

struct PrepareInstructionsSection: View {
    let instructionsHTML: String?
    let locale: AppLocale

    @Environment(\.brandPalette) private var palette

    private var parsedDocument: RichDocument? {
        guard let raw = instructionsHTML?.trimmingCharacters(in: .whitespacesAndNewlines),
              !raw.isEmpty
        else {
            return nil
        }
        let doc = RichHTMLParser.parse(raw, options: RichParseOptions(allowImages: false))
        return doc.isEmpty ? nil : doc
    }

    var body: some View {
        let model = rendered
        let noteIds = [
            "prepare.instructions.noteAutoSave",
            "prepare.instructions.noteClock",
            "prepare.instructions.noteOffline",
            "prepare.instructions.noteSingleDevice"
        ]

        PrepareCard {
            PrepareSectionHeading(
                title: model.title,
                identifier: "prepare.instructions.heading"
            )

            if let document = model.parsedDocument {
                RichContentView(
                    document: document,
                    assets: nil,
                    mode: .locked,
                    locale: locale
                )
                .accessibilityIdentifier("prepare.instructions.richContent")
            }

            VStack(alignment: .leading, spacing: 8) {
                ForEach(Array(model.fixedNotes.enumerated()), id: \.offset) { idx, text in
                    noteRow(text: text, identifier: noteIds[idx])
                }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("prepare.instructions.section")
    }

    private func noteRow(text: String, identifier: String) -> some View {
        HStack(alignment: .top, spacing: 8) {
            Text("•")
                .font(.subheadline)
                .foregroundStyle(palette.textSecondaryColor)
                .accessibilityHidden(true)
            Text(text)
                .font(.subheadline)
                .foregroundStyle(palette.textSecondaryColor)
                .fixedSize(horizontal: false, vertical: true)
        }
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier(identifier)
    }
}

struct PrepareReadySection: View {
    let response: AttemptPrepareResponse
    let metrics: SelectedPathMetrics
    let gate: StartGate
    let startState: StartState
    let locale: AppLocale
    let supportContact: SupportContact
    let showsActionsInline: Bool
    let onStart: () -> Void
    let onContinue: (String) -> Void
    let onBuyMore: (String) -> Void
    let onBackToMyExams: () -> Void

    @Environment(\.brandPalette) private var palette
    @Environment(\.statusPalette) private var statusPalette

    var body: some View {
        let model = rendered

        PrepareCard {
            PrepareSectionHeading(
                title: model.title,
                identifier: "prepare.ready.heading"
            )

            VStack(spacing: 10) {
                readyRow(
                    label: model.attemptsLeftLabel,
                    value: model.attemptsValue,
                    valueColor: response.attemptsLeft <= 0 ? statusPalette.warningColor : palette.textColor,
                    identifier: "prepare.ready.attemptsLeft"
                )

                if let grantLabel = model.grantSourceLabel, let grantValue = model.grantSourceValue {
                    readyRow(
                        label: grantLabel,
                        value: grantValue,
                        valueColor: palette.textColor,
                        identifier: "prepare.ready.grantSource"
                    )
                }

                readyRow(
                    label: PrepareStrings.text("prepare.sidebar.questions", locale: locale),
                    value: model.questionsValue,
                    valueColor: palette.textColor,
                    identifier: "prepare.ready.questions"
                )

                readyRow(
                    label: PrepareStrings.text("prepare.sidebar.duration", locale: locale),
                    value: model.durationValue,
                    valueColor: palette.textColor,
                    identifier: "prepare.ready.duration"
                )

                readyRow(
                    label: PrepareStrings.text("prepare.sidebar.maxPoints", locale: locale),
                    value: model.maxPointsValue,
                    valueColor: palette.textColor,
                    identifier: "prepare.ready.maxPoints"
                )
            }

            if showsActionsInline {
                PrepareActionBlock(
                    response: response,
                    gate: gate,
                    startState: startState,
                    locale: locale,
                    supportContact: supportContact,
                    onStart: onStart,
                    onContinue: onContinue,
                    onBuyMore: onBuyMore
                )
            }

            if case .inProgress = gate {
                EmptyView()
            } else {
                Text(PrepareStrings.text("prepare.sidebar.startNote", locale: locale))
                    .font(.footnote)
                    .foregroundStyle(palette.textMutedColor)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("prepare.ready.startNote")
            }

            Button(action: onBackToMyExams) {
                HStack(spacing: 6) {
                    Image(systemName: "arrow.left")
                        .font(.footnote.weight(.semibold))
                        .accessibilityHidden(true)
                    Text(PrepareStrings.text("prepare.backToMyExams", locale: locale))
                        .font(.subheadline.weight(.semibold))
                        .fixedSize(horizontal: false, vertical: true)
                }
                .foregroundStyle(palette.primaryTextColor)
                .frame(minHeight: 44, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("prepare.backToMyExamsButton")
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("prepare.ready.section")
    }

    private func readyRow(
        label: String,
        value: String,
        valueColor: Color,
        identifier: String
    ) -> some View {
        ViewThatFits(in: .horizontal) {
            HStack(alignment: .firstTextBaseline, spacing: 12) {
                Text(label)
                    .font(.subheadline)
                    .foregroundStyle(palette.textMutedColor)
                Spacer(minLength: 8)
                Text(value)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(valueColor)
                    .multilineTextAlignment(.trailing)
                    .monospacedDigit()
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(label)
                    .font(.subheadline)
                    .foregroundStyle(palette.textMutedColor)
                Text(value)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(valueColor)
                    .monospacedDigit()
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier(identifier)
    }
}

struct PrepareActionBlock: View {
    let response: AttemptPrepareResponse
    let gate: StartGate
    let startState: StartState
    let locale: AppLocale
    let supportContact: SupportContact
    let onStart: () -> Void
    let onContinue: (String) -> Void
    let onBuyMore: (String) -> Void

    @Environment(\.brandPalette) private var palette
    @Environment(\.statusPalette) private var statusPalette

    var body: some View {
        let model = rendered

        VStack(alignment: .leading, spacing: 10) {
            switch gate {
            case let .inProgress(attemptId):
                HStack(alignment: .top, spacing: 10) {
                    Image(systemName: "info.circle.fill")
                        .font(.body)
                        .foregroundStyle(statusPalette.infoColor)
                        .accessibilityHidden(true)
                    Text(model.inProgressBanner ?? "")
                        .font(.subheadline)
                        .foregroundStyle(palette.textColor)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityIdentifier("prepare.inProgressBanner")
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(12)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(statusPalette.infoBgColor)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .strokeBorder(statusPalette.infoColor.opacity(0.45), lineWidth: 1)
                )

                Button {
                    onContinue(attemptId)
                } label: {
                    Text(model.continueTitle ?? "")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(palette.onAccentColor)
                        .frame(maxWidth: .infinity, minHeight: 48)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .background(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(palette.accentColor)
                        )
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("prepare.continueButton")

            case .exhausted:
                VStack(alignment: .leading, spacing: 10) {
                    HStack(alignment: .top, spacing: 10) {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .font(.body)
                            .foregroundStyle(palette.dangerTextColor)
                            .accessibilityHidden(true)

                        VStack(alignment: .leading, spacing: 4) {
                            Text(model.exhaustedTitle ?? "")
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(palette.dangerTextColor)
                                .fixedSize(horizontal: false, vertical: true)
                                .accessibilityIdentifier("prepare.exhaustedTitle")

                            Text(model.exhaustedDescription ?? "")
                                .font(.footnote)
                                .foregroundStyle(palette.textSecondaryColor)
                                .fixedSize(horizontal: false, vertical: true)
                                .accessibilityIdentifier("prepare.exhaustedDescription")
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(palette.dangerBgColor)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .strokeBorder(palette.dangerTextColor.opacity(0.45), lineWidth: 1)
                    )

                    if model.showsBuyMore, let buyTitle = model.buyMoreTitle {
                        Button {
                            onBuyMore(response.examCode)
                        } label: {
                            Text(buyTitle)
                                .font(.body.weight(.semibold))
                                .foregroundStyle(palette.primaryTextColor)
                                .frame(maxWidth: .infinity, minHeight: 48)
                                .padding(.horizontal, 16)
                                .padding(.vertical, 8)
                                .background(
                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                        .fill(palette.bgSurfaceColor)
                                )
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                        .strokeBorder(palette.primaryFillColor, lineWidth: 1.5)
                                )
                        }
                        .buttonStyle(.plain)
                        .accessibilityIdentifier("prepare.buyMoreButton")
                    }

                    startButton(title: model.startTitle, enabled: model.startEnabled, sending: false)
                }

            case .open, .noServingOption, .needOption, .needSubjects:
                if let errorDisplay = model.startError {
                    HStack(alignment: .top, spacing: 10) {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .font(.body)
                            .foregroundStyle(palette.dangerTextColor)
                            .accessibilityHidden(true)

                        VStack(alignment: .leading, spacing: 4) {
                            Text(errorDisplay.primary)
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(palette.dangerTextColor)
                                .fixedSize(horizontal: false, vertical: true)
                                .accessibilityIdentifier("prepare.startError.primary")

                            if let supportLine = errorDisplay.supportLine {
                                Text(supportLine)
                                    .font(.footnote)
                                    .foregroundStyle(palette.textSecondaryColor)
                                    .fixedSize(horizontal: false, vertical: true)
                                    .accessibilityIdentifier("prepare.startError.support")
                            }
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(palette.dangerBgColor)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .strokeBorder(palette.dangerTextColor.opacity(0.45), lineWidth: 1)
                    )
                    .accessibilityElement(children: .contain)
                    .accessibilityIdentifier("prepare.startError")
                }

                startButton(title: model.startTitle, enabled: model.startEnabled, sending: startState == .sending)

                if let hint = model.blockedHint {
                    Text(hint)
                        .font(.footnote)
                        .foregroundStyle(statusPalette.warningColor)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityIdentifier("prepare.startHint")
                }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("prepare.actionBlock")
    }

    private var blockedHint: String? {
        switch gate {
        case .needOption:
            return PrepareStrings.text("prepare.preChoice.hintSelectOption", locale: locale)
        case let .needSubjects(count):
            return PrepareStrings.text(
                "prepare.preChoice.hintSelectSubjects",
                locale: locale,
                replacements: ["count": String(count)]
            )
        default:
            return nil
        }
    }

    private func exhaustedDescription(for kind: DescriptionKind) -> String {
        switch kind {
        case .class:
            return PrepareStrings.text("prepare.exhaustedDescriptionClass", locale: locale)
        case .published:
            return PrepareStrings.text("prepare.exhaustedDescription", locale: locale)
        case .discontinued:
            return PrepareStrings.text("prepare.exhaustedDescriptionDiscontinued", locale: locale)
        }
    }

    private func startButton(title: String, enabled: Bool, sending: Bool) -> some View {
        Button(action: onStart) {
            HStack(spacing: 8) {
                if sending {
                    ProgressView()
                        .tint(palette.onAccentColor)
                }
                Text(title)
                    .font(.body.weight(.semibold))
                    .fixedSize(horizontal: false, vertical: true)
                if !sending {
                    Image(systemName: "arrow.right")
                        .font(.body.weight(.semibold))
                        .accessibilityHidden(true)
                }
            }
            .foregroundStyle(palette.onAccentColor)
            .frame(maxWidth: .infinity, minHeight: 50)
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(palette.accentColor)
                    .opacity(enabled ? 1.0 : 0.45)
            )
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .accessibilityIdentifier("prepare.startButton")
    }
}

struct PrepareCard<Content: View>: View {
    @ViewBuilder let content: () -> Content
    @Environment(\.brandPalette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
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

struct PrepareSectionHeading: View {
    let title: String
    var identifier: String? = nil

    @Environment(\.brandPalette) private var palette

    var body: some View {
        HStack(spacing: 10) {
            RoundedRectangle(cornerRadius: 2, style: .continuous)
                .fill(palette.primaryFillColor)
                .frame(width: 4, height: 22)
                .accessibilityHidden(true)

            Text(title)
                .font(.headline.weight(.bold))
                .foregroundStyle(palette.textColor)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
        }
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier(identifier ?? "")
    }
}

struct PrepareChip: View {
    let text: String
    let foreground: Color
    let background: Color
    var identifier: String? = nil

    var body: some View {
        Text(text)
            .font(.caption.weight(.semibold))
            .foregroundStyle(foreground)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(
                Capsule(style: .continuous)
                    .fill(background)
            )
            .accessibilityIdentifier(identifier ?? "")
    }
}

struct PreparePreChoiceSubjectState: Equatable {
    let code: String
    let label: String
    let isChecked: Bool
    let isDisabled: Bool
}

struct PreparePreChoicePresentation: Equatable {
    let title: String
    let noServingTitle: String?
    let noServingDescription: String?
    let subjectLegend: String?
    let subjectProgress: String?
    let subjects: [PreparePreChoiceSubjectState]
}

extension PreparePreChoiceSection {
    var rendered: PreparePreChoicePresentation {
        let options = preChoice.options ?? []
        let title = PrepareStrings.text("prepare.preChoice.title", locale: locale)
        if options.isEmpty {
            return PreparePreChoicePresentation(
                title: title,
                noServingTitle: PrepareStrings.text("prepare.noServingOption.title", locale: locale),
                noServingDescription: StartErrorMessage.noServingSupportLine(supportContact: supportContact, locale: locale),
                subjectLegend: nil,
                subjectProgress: nil,
                subjects: []
            )
        }
        let selectedOption = selection.selectedOption(in: preChoice)
        let requiredSubjects = PreChoiceSelection.requiredSubjectCount(for: selectedOption)
        guard requiredSubjects > 0, let selectedOption else {
            return PreparePreChoicePresentation(
                title: title,
                noServingTitle: nil,
                noServingDescription: nil,
                subjectLegend: nil,
                subjectProgress: nil,
                subjects: []
            )
        }
        let pickedCount = selection.subjectCodes.count
        let subjectStates = (selectedOption.subjects ?? []).map { subject in
            let subCode = subject.code ?? ""
            let isChecked = selection.subjectCodes.contains(subCode)
            let isDisabled = !isChecked && pickedCount >= requiredSubjects
            let label = PrepareFormatting.localizedName(
                nameVi: subject.nameVi,
                nameEn: subject.nameEn,
                code: subCode,
                locale: locale
            )
            return PreparePreChoiceSubjectState(code: subCode, label: label, isChecked: isChecked, isDisabled: isDisabled)
        }
        return PreparePreChoicePresentation(
            title: title,
            noServingTitle: nil,
            noServingDescription: nil,
            subjectLegend: PrepareStrings.text("prepare.preChoice.subjectLegend", locale: locale),
            subjectProgress: PrepareStrings.text(
                "prepare.preChoice.chooseSubjectsProgress",
                locale: locale,
                replacements: ["selected": String(pickedCount), "total": String(requiredSubjects)]
            ),
            subjects: subjectStates
        )
    }
}

struct PrepareStructurePartRowPresentation: Equatable {
    let order: Int
    let name: String
    let metrics: String
    let badge: String
}

struct PrepareStructurePresentation: Equatable {
    let title: String
    let timingTitle: String
    let timingDescription: String
    let parts: [PrepareStructurePartRowPresentation]
    let answerFormatsLabel: String?
    let answerFormats: [String]
    let listeningTitle: String?
    let listeningBody: String?
    let omittedGroupsLabel: String?
    let omittedGroups: [String]
}

extension PrepareStructureSection {
    var rendered: PrepareStructurePresentation {
        let timingDisplay = PrepareTimingDisplay.resolve(for: response)
        let timingSegment = timingDisplay.localizationKeySegment
        let orderedParts = response.parts.sorted { ($0.ord ?? 0) < ($1.ord ?? 0) }
        let partRows = orderedParts.enumerated().map { index, part in
            let isRequired = part.required ?? false
            let metrics = PrepareFormatting.optionMetricsLine(
                questionCount: part.questionCount.map(Int.init),
                durationMinutes: part.durationMinutes.map(Int.init),
                maxPoints: part.maxPoints.map(PrepareMetrics.decimalFromDouble),
                locale: locale
            )
            return PrepareStructurePartRowPresentation(
                order: index + 1,
                name: part.name ?? "",
                metrics: metrics,
                badge: PrepareStrings.text(isRequired ? "prepare.required" : "prepare.optional", locale: locale)
            )
        }
        let formats = response.answerFormats.map { PrepareStrings.answerFormatLabel(for: $0, locale: locale) }
        let listeningTitle: String?
        let listeningBody: String?
        if let listening = response.listeningPolicy {
            let maxPlays = Int(listening.maxPlays ?? 2)
            let maxPlaysLine = PrepareStrings.text(
                "prepare.listeningPolicy.maxPlays",
                locale: locale,
                replacements: ["maxPlays": String(maxPlays)]
            )
            listeningTitle = PrepareStrings.text("prepare.listeningPolicy.title", locale: locale)
            listeningBody = listening.seekable == false
                ? "\(maxPlaysLine) \(PrepareStrings.text("prepare.listeningPolicy.noSeek", locale: locale))"
                : maxPlaysLine
        } else {
            listeningTitle = nil
            listeningBody = nil
        }
        let omittedLines = response.omittedGroups.map { group -> String in
            let name = group.name ?? ""
            guard let points = group.points else { return name }
            let formatted = PrepareFormatting.formatPoints(points, locale: locale)
            let unit = PrepareStrings.text("prepare.units.points", locale: locale, replacements: ["value": formatted])
            return "\(name) · \(unit)"
        }
        return PrepareStructurePresentation(
            title: PrepareStrings.text("prepare.structure.title", locale: locale),
            timingTitle: PrepareStrings.text("prepare.timing.\(timingSegment).title", locale: locale),
            timingDescription: PrepareStrings.text("prepare.timing.\(timingSegment).description", locale: locale),
            parts: partRows,
            answerFormatsLabel: formats.isEmpty ? nil : PrepareStrings.text("prepare.answerFormatsLabel", locale: locale),
            answerFormats: formats,
            listeningTitle: listeningTitle,
            listeningBody: listeningBody,
            omittedGroupsLabel: omittedLines.isEmpty ? nil : PrepareStrings.text("prepare.omittedGroupsLabel", locale: locale),
            omittedGroups: omittedLines
        )
    }
}

struct PrepareAfterStartOptionPresentation: Equatable {
    let headline: String
    let subjectsLine: String
}

struct PrepareAfterStartGroupPresentation: Equatable {
    let partName: String
    let options: [PrepareAfterStartOptionPresentation]
}

struct PrepareAfterStartChoicesPresentation: Equatable {
    let title: String
    let groups: [PrepareAfterStartGroupPresentation]
    let deadlineNote: String
    let timerNote: String
}

extension PrepareAfterStartChoicesSection {
    var rendered: PrepareAfterStartChoicesPresentation {
        let groups = choices.map { choice in
            let opts = (choice.options ?? []).map { opt -> PrepareAfterStartOptionPresentation in
                let subjects = opt.subjects ?? []
                let chooseCount = Int(opt.chooseSubjects ?? 0)
                let subjectNames = subjects
                    .map { PrepareFormatting.localizedName(nameVi: $0.nameVi, nameEn: $0.nameEn, code: $0.code, locale: locale) }
                    .filter { !$0.isEmpty }
                    .joined(separator: ", ")

                var headlineParts: [String] = []
                if let code = opt.code, !code.isEmpty { headlineParts.append(code) }
                if let name = opt.name, !name.isEmpty { headlineParts.append(name) }
                var headline = headlineParts.joined(separator: " · ")
                if chooseCount > 0 && !subjects.isEmpty {
                    let chooseText = PrepareStrings.text(
                        "prepare.afterStartChoices.chooseSubjects",
                        locale: locale,
                        replacements: ["count": String(chooseCount), "total": String(subjects.count)]
                    )
                    headline += " (\(chooseText))"
                }
                return PrepareAfterStartOptionPresentation(headline: headline, subjectsLine: subjectNames)
            }
            return PrepareAfterStartGroupPresentation(partName: choice.partName ?? "", options: opts)
        }
        return PrepareAfterStartChoicesPresentation(
            title: PrepareStrings.text("prepare.afterStartChoices.title", locale: locale),
            groups: groups,
            deadlineNote: PrepareStrings.text("prepare.afterStartChoices.deadlineNote", locale: locale),
            timerNote: PrepareStrings.text("prepare.afterStartChoices.timerNote", locale: locale)
        )
    }
}

struct PrepareInstructionsPresentation: Equatable {
    let title: String
    let hasRichContent: Bool
    let parsedDocument: RichDocument?
    let fixedNotes: [String]
}

extension PrepareInstructionsSection {
    var rendered: PrepareInstructionsPresentation {
        let doc = parsedDocument
        let notes = [
            PrepareStrings.text("prepare.instructions.noteAutoSave", locale: locale),
            PrepareStrings.text("prepare.instructions.noteClock", locale: locale),
            PrepareStrings.text("prepare.instructions.noteOffline", locale: locale),
            PrepareStrings.text("prepare.instructions.noteSingleDevice", locale: locale)
        ]
        return PrepareInstructionsPresentation(
            title: PrepareStrings.text("prepare.instructions.title", locale: locale),
            hasRichContent: doc != nil,
            parsedDocument: doc,
            fixedNotes: notes
        )
    }
}

struct PrepareReadyPresentation: Equatable {
    let title: String
    let attemptsLeftLabel: String
    let attemptsValue: String
    let grantSourceLabel: String?
    let grantSourceValue: String?
    let questionsValue: String
    let durationValue: String
    let maxPointsValue: String
}

extension PrepareReadySection {
    var rendered: PrepareReadyPresentation {
        let grantLabel = PrepareFormatting.grantSourceLabel(for: response, locale: locale)
        return PrepareReadyPresentation(
            title: PrepareStrings.text("prepare.sidebar.title", locale: locale),
            attemptsLeftLabel: PrepareStrings.text("prepare.sidebar.attemptsLeft", locale: locale),
            attemptsValue: PrepareStrings.plural("prepare.sidebar.attemptsValue", count: Int(response.attemptsLeft), locale: locale),
            grantSourceLabel: grantLabel == nil ? nil : PrepareStrings.text("prepare.sidebar.grantSource", locale: locale),
            grantSourceValue: grantLabel,
            questionsValue: metrics.questionCount.map { PrepareStrings.plural("prepare.units.questions", count: $0, locale: locale) } ?? "—",
            durationValue: metrics.durationMinutes.map { PrepareStrings.plural("prepare.units.minutes", count: $0, locale: locale) } ?? "—",
            maxPointsValue: metrics.maxPoints.map {
                PrepareStrings.text("prepare.units.points", locale: locale, replacements: ["value": PrepareFormatting.formatPoints($0, locale: locale)])
            } ?? "—"
        )
    }
}

struct PrepareActionPresentation: Equatable {
    let inProgressBanner: String?
    let continueTitle: String?
    let exhaustedTitle: String?
    let exhaustedDescription: String?
    let showsBuyMore: Bool
    let buyMoreTitle: String?
    let startError: StartErrorDisplay?
    let showsStartButton: Bool
    let startEnabled: Bool
    let startTitle: String
    let blockedHint: String?
}

extension PrepareActionBlock {
    var rendered: PrepareActionPresentation {
        let startTitle = PrepareStrings.text("prepare.startCta", locale: locale)
        switch gate {
        case .inProgress:
            return PrepareActionPresentation(
                inProgressBanner: PrepareStrings.text("prepare.inProgressBanner", locale: locale),
                continueTitle: PrepareStrings.text("prepare.continueAttempt", locale: locale),
                exhaustedTitle: nil,
                exhaustedDescription: nil,
                showsBuyMore: false,
                buyMoreTitle: nil,
                startError: nil,
                showsStartButton: false,
                startEnabled: false,
                startTitle: startTitle,
                blockedHint: nil
            )
        case let .exhausted(kind, canBuyMore):
            return PrepareActionPresentation(
                inProgressBanner: nil,
                continueTitle: nil,
                exhaustedTitle: PrepareStrings.text("prepare.exhaustedTitle", locale: locale),
                exhaustedDescription: exhaustedDescription(for: kind),
                showsBuyMore: canBuyMore,
                buyMoreTitle: canBuyMore ? PrepareStrings.text("prepare.buyMoreAttempts", locale: locale) : nil,
                startError: nil,
                showsStartButton: true,
                startEnabled: false,
                startTitle: startTitle,
                blockedHint: nil
            )
        case .open, .noServingOption, .needOption, .needSubjects:
            let errDisplay: StartErrorDisplay?
            if case let .refused(refusal) = startState {
                errDisplay = StartErrorMessage.display(for: refusal, locale: locale, supportContact: supportContact)
            } else {
                errDisplay = nil
            }
            let isSending = (startState == .sending)
            return PrepareActionPresentation(
                inProgressBanner: nil,
                continueTitle: nil,
                exhaustedTitle: nil,
                exhaustedDescription: nil,
                showsBuyMore: false,
                buyMoreTitle: nil,
                startError: errDisplay,
                showsStartButton: true,
                startEnabled: (gate == .open) && !isSending,
                startTitle: startTitle,
                blockedHint: blockedHint
            )
        }
    }
}

struct PrepareFlowWrap: Layout {
    var spacing: CGFloat = 8
    var lineSpacing: CGFloat = 6

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        let lines = computeLines(maxWidth: maxWidth, subviews: subviews)
        var width: CGFloat = 0
        var height: CGFloat = 0
        for (idx, line) in lines.enumerated() {
            width = max(width, line.width)
            height += line.height
            if idx + 1 < lines.count {
                height += lineSpacing
            }
        }
        return CGSize(width: min(width, maxWidth), height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let lines = computeLines(maxWidth: bounds.width, subviews: subviews)
        var y = bounds.minY
        for line in lines {
            var x = bounds.minX
            for item in line.items {
                subviews[item.index].place(
                    at: CGPoint(x: x, y: y),
                    proposal: ProposedViewSize(width: min(item.size.width, bounds.width), height: item.size.height)
                )
                x += item.size.width + spacing
            }
            y += line.height + lineSpacing
        }
    }

    private struct LineItem {
        let index: Int
        let size: CGSize
    }

    private struct Line {
        var items: [LineItem] = []
        var width: CGFloat = 0
        var height: CGFloat = 0
    }

    private func computeLines(maxWidth: CGFloat, subviews: Subviews) -> [Line] {
        var lines: [Line] = []
        var current = Line()
        let effectiveMax = maxWidth.isFinite ? max(1, maxWidth) : .infinity

        for (index, subview) in subviews.enumerated() {
            let measured = subview.sizeThatFits(ProposedViewSize(width: effectiveMax, height: nil))
            let clampedWidth = min(measured.width, effectiveMax)
            let size = CGSize(width: clampedWidth, height: measured.height)
            let neededWidth = current.items.isEmpty ? size.width : current.width + spacing + size.width

            if !current.items.isEmpty && neededWidth > effectiveMax {
                lines.append(current)
                current = Line()
            }

            current.items.append(LineItem(index: index, size: size))
            current.width = current.items.count == 1 ? size.width : current.width + spacing + size.width
            current.height = max(current.height, size.height)
        }

        if !current.items.isEmpty {
            lines.append(current)
        }
        return lines
    }
}
