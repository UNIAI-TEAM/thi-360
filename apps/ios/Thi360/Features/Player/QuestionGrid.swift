import SwiftUI

struct PlayerNarrowBottomBar: View {
    let currentIndex: Int
    let answeredCount: Int
    let totalCount: Int
    let locale: AppLocale
    let onOpenGrid: () -> Void

    @Environment(\.brandPalette) private var palette

    private var ratio: Double {
        totalCount > 0 ? min(1.0, max(0.0, Double(answeredCount) / Double(totalCount))) : 0.0
    }

    var body: some View {
        VStack(spacing: 0) {
            Rectangle()
                .fill(palette.borderColor)
                .frame(height: 1)

            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(
                        PlayerStrings.text(
                            "attempt.nav.answeredCount",
                            locale: locale,
                            replacements: [
                                "answered": String(answeredCount),
                                "total": String(totalCount)
                            ]
                        )
                    )
                    .font(.caption.weight(.semibold).monospacedDigit())
                    .foregroundStyle(palette.textSecondaryColor)
                    .lineLimit(1)
                    .accessibilityIdentifier("player.bottomBar.answeredCount")

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
                    .accessibilityHidden(true)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                Button(action: onOpenGrid) {
                    HStack(spacing: 6) {
                        Image(systemName: "square.grid.3x3.fill")
                            .font(.caption.weight(.semibold))
                            .accessibilityHidden(true)
                        Text(
                            PlayerStrings.text(
                                "attempt.nav.openGridCompact",
                                locale: locale,
                                replacements: [
                                    "current": String(currentIndex),
                                    "total": String(totalCount)
                                ]
                            )
                        )
                        .font(.subheadline.weight(.bold).monospacedDigit())
                    }
                    .foregroundStyle(palette.primaryTextColor)
                    .padding(.horizontal, 14)
                    .frame(minWidth: 72, minHeight: 44)
                    .background(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .fill(palette.primaryContainerColor)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .strokeBorder(palette.primaryFillColor.opacity(0.45), lineWidth: 1)
                    )
                    .contentShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                }
                .buttonStyle(.plain)
                .accessibilityLabel(
                    PlayerStrings.text(
                        "attempt.nav.openGrid",
                        locale: locale,
                        replacements: [
                            "current": String(currentIndex),
                            "total": String(totalCount)
                        ]
                    )
                )
                .accessibilityIdentifier("player.bottomBar.openGrid")
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .background(palette.bgSurfaceColor)
        }
        .accessibilityElement(children: .contain)
        .playerProbe("player.bottomBar")
    }
}

struct QuestionGridSidebar: View {
    let ui: PlayerUI
    let locale: AppLocale
    let width: CGFloat
    let onSelectFilter: (PlayerGridFilter) -> Void
    let onSelectItem: (ItemID) -> Void

    @Environment(\.brandPalette) private var palette

    var body: some View {
        QuestionGridBody(
            ui: ui,
            locale: locale,
            showsCloseButton: false,
            onClose: {},
            onSelectFilter: onSelectFilter,
            onSelectItem: onSelectItem
        )
        .frame(width: width)
        .frame(maxHeight: .infinity)
        .background(palette.bgSurfaceColor)
        .overlay(alignment: .leading) {
            Rectangle()
                .fill(palette.borderColor)
                .frame(width: 1)
        }
        .accessibilityElement(children: .contain)
        .playerProbe("player.grid.sidebar")
    }
}

struct QuestionGridSheet: View {
    let ui: PlayerUI
    let locale: AppLocale
    let onDismiss: () -> Void
    let onSelectFilter: (PlayerGridFilter) -> Void
    let onSelectItem: (ItemID) -> Void

    @Environment(\.brandPalette) private var palette

    var body: some View {
        QuestionGridBody(
            ui: ui,
            locale: locale,
            showsCloseButton: true,
            onClose: onDismiss,
            onSelectFilter: onSelectFilter,
            onSelectItem: { itemId in
                onSelectItem(itemId)
                onDismiss()
            }
        )
        .background(palette.bgSurfaceColor.ignoresSafeArea())
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("player.grid.sheet")
    }
}

struct QuestionGridCellStyle: Equatable {
    let backgroundHex: String
    let foregroundHex: String
    let borderHex: String
    let flagHex: String?
    let isDashed: Bool

    static func resolve(
        completion: ItemCompletion,
        flagged: Bool,
        palette: BrandPalette,
        statusPalette: StatusPalette
    ) -> QuestionGridCellStyle {
        if flagged {
            switch completion {
            case .answered:
                return QuestionGridCellStyle(
                    backgroundHex: statusPalette.dangerSolid,
                    foregroundHex: statusPalette.onDanger,
                    borderHex: statusPalette.dangerSolid,
                    flagHex: statusPalette.onDanger,
                    isDashed: false
                )
            case .partial:
                return QuestionGridCellStyle(
                    backgroundHex: palette.dangerBg,
                    foregroundHex: palette.dangerText,
                    borderHex: palette.dangerText,
                    flagHex: palette.dangerText,
                    isDashed: true
                )
            case .blank:
                return QuestionGridCellStyle(
                    backgroundHex: palette.dangerBg,
                    foregroundHex: palette.dangerText,
                    borderHex: palette.dangerText,
                    flagHex: palette.dangerText,
                    isDashed: false
                )
            }
        }

        switch completion {
        case .answered:
            return QuestionGridCellStyle(
                backgroundHex: palette.primaryContainer,
                foregroundHex: palette.primaryText,
                borderHex: palette.primaryFill,
                flagHex: nil,
                isDashed: false
            )
        case .partial:
            return QuestionGridCellStyle(
                backgroundHex: palette.primaryContainer,
                foregroundHex: palette.primaryText,
                borderHex: palette.primaryFill,
                flagHex: nil,
                isDashed: true
            )
        case .blank:
            return QuestionGridCellStyle(
                backgroundHex: palette.bgSurface,
                foregroundHex: palette.text,
                borderHex: palette.borderStrong,
                flagHex: nil,
                isDashed: false
            )
        }
    }
}

struct QuestionGridBody: View {
    let ui: PlayerUI
    let locale: AppLocale
    let showsCloseButton: Bool
    let onClose: () -> Void
    let onSelectFilter: (PlayerGridFilter) -> Void
    let onSelectItem: (ItemID) -> Void

    @Environment(\.brandPalette) private var palette
    @Environment(\.statusPalette) private var statusPalette

    private let columns = [GridItem(.adaptive(minimum: 44, maximum: 56), spacing: 8)]

    var body: some View {
        VStack(spacing: 0) {
            headerSection
            Divider().overlay(palette.borderColor)

            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    if ui.filteredGridCells.isEmpty {
                        let emptyText = PlayerStrings.text("attempt.nav.emptyFiltered", locale: locale)
                        Text(emptyText)
                            .font(.subheadline)
                            .foregroundStyle(palette.textMutedColor)
                            .frame(maxWidth: .infinity, alignment: .center)
                            .padding(.vertical, 28)
                            .playerProbe("player.grid.emptyFiltered", label: emptyText)
                    } else {
                        ForEach(ui.gridGroups) { group in
                            VStack(alignment: .leading, spacing: 8) {
                                groupHeaderView(group.header)

                                LazyVGrid(columns: columns, alignment: .leading, spacing: 8) {
                                    ForEach(group.cells) { cell in
                                        cellButton(cell)
                                    }
                                }
                            }
                        }
                    }

                    Divider()
                        .overlay(palette.borderColor)
                        .padding(.top, 4)

                    legendSection
                }
                .padding(14)
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel(PlayerStrings.text("attempt.nav.gridLabel", locale: locale))
    }

    private var headerSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(PlayerStrings.text("attempt.nav.gridLabel", locale: locale))
                        .font(.headline.weight(.bold))
                        .foregroundStyle(palette.textColor)
                        .accessibilityAddTraits(.isHeader)
                        .accessibilityIdentifier("player.grid.title")

                    Text(
                        PlayerStrings.text(
                            "attempt.nav.answeredCount",
                            locale: locale,
                            replacements: [
                                "answered": String(ui.answeredInActivePart),
                                "total": String(ui.totalInActivePart)
                            ]
                        )
                    )
                    .font(.caption.weight(.semibold).monospacedDigit())
                    .foregroundStyle(palette.textSecondaryColor)
                    .accessibilityIdentifier("player.grid.answeredCount")
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                if showsCloseButton {
                    Button(action: onClose) {
                        Image(systemName: "xmark")
                            .font(.body.weight(.semibold))
                            .foregroundStyle(palette.textSecondaryColor)
                            .frame(minWidth: 44, minHeight: 44)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(PlayerStrings.text("attempt.nav.closeGrid", locale: locale))
                    .accessibilityIdentifier("player.grid.closeButton")
                }
            }

            WrappingHStackLayout(horizontalSpacing: 6, verticalSpacing: 6) {
                filterChip(.all, key: "attempt.nav.filterAll")
                filterChip(.unanswered, key: "attempt.nav.filterUnanswered")
                filterChip(.flagged, key: "attempt.nav.filterFlagged")
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
    }

    private func filterChip(_ filter: PlayerGridFilter, key: String) -> some View {
        let isSelected = ui.gridFilter == filter
        let title = PlayerStrings.text(key, locale: locale)
        return Button {
            onSelectFilter(filter)
        } label: {
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(isSelected ? palette.onPrimaryColor : palette.textSecondaryColor)
                .padding(.horizontal, 12)
                .frame(minHeight: 36)
                .background(
                    Capsule(style: .continuous)
                        .fill(isSelected ? palette.primaryFillColor : palette.bgSubtleColor)
                )
                .overlay(
                    Capsule(style: .continuous)
                        .strokeBorder(isSelected ? palette.primaryFillColor : palette.borderColor, lineWidth: 1)
                )
                .contentShape(Capsule(style: .continuous))
        }
        .buttonStyle(.plain)
        .frame(minHeight: 44)
        .accessibilityAddTraits(isSelected ? [.isSelected] : [])
        .accessibilityIdentifier("player.grid.filter.\(filter.rawValue)")
    }

    @ViewBuilder
    private func groupHeaderView(_ header: UnitGroupHeader) -> some View {
        switch header {
        case .none:
            EmptyView()
        case let .segment(_, code, name, fromNo, toNo, _, _, isUngrouped):
            let title: String = {
                if isUngrouped {
                    return PlayerStrings.text("attempt.segments.ungrouped", locale: locale)
                }
                if let name, !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    return name
                }
                if let code, !code.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    return code
                }
                return PlayerStrings.text(
                    "attempt.segments.range",
                    locale: locale,
                    replacements: ["from": String(fromNo), "to": String(toNo)]
                )
            }()
            Text(title)
                .font(.caption.weight(.bold))
                .foregroundStyle(palette.textSecondaryColor)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
                .accessibilityIdentifier("player.grid.groupHeader")

        case let .subject(_, subjectCode, _, _, _):
            let resolved = SubjectNames.resolve(
                code: subjectCode,
                prepareSubjects: ui.subjectsByCode,
                locale: locale
            )
            let title = PlayerStrings.text(
                "attempt.subjects.heading",
                locale: locale,
                replacements: ["name": resolved]
            )
            Text(title)
                .font(.caption.weight(.bold))
                .foregroundStyle(palette.textSecondaryColor)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
                .accessibilityIdentifier("player.grid.groupHeader")
        }
    }

    private func cellButton(_ cell: GridCellUI) -> some View {
        let style = QuestionGridCellStyle.resolve(
            completion: cell.completion,
            flagged: cell.flagged,
            palette: palette,
            statusPalette: statusPalette
        )
        let bgColor = Color(hex: style.backgroundHex)
        let fgColor = Color(hex: style.foregroundHex)
        let borderColor = Color(hex: style.borderHex)
        let ringColor = cell.flagged && cell.completion == .answered
            ? palette.textColor
            : palette.primaryFillColor

        let a11yLabel = cellAccessibilityLabel(cell)
        return Button {
            onSelectItem(cell.itemId)
        } label: {
            ZStack {
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .fill(bgColor)
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .strokeBorder(
                        borderColor,
                        style: style.isDashed
                            ? StrokeStyle(lineWidth: 1.5, dash: [4, 2])
                            : StrokeStyle(lineWidth: 1.5)
                    )

                Text("\(cell.displayNo)")
                    .font(.subheadline.weight(.bold).monospacedDigit())
                    .foregroundStyle(fgColor)

                if let flagHex = style.flagHex {
                    VStack {
                        Spacer(minLength: 0)
                        HStack {
                            Spacer(minLength: 0)
                            Image(systemName: "flag.fill")
                                .font(.system(size: 8, weight: .bold))
                                .foregroundStyle(Color(hex: flagHex))
                                .padding(3)
                        }
                    }
                    .accessibilityHidden(true)
                }

                if cell.isUnsaved {
                    VStack {
                        HStack {
                            Spacer(minLength: 0)
                            Circle()
                                .fill(statusPalette.warningColor)
                                .frame(width: 7, height: 7)
                                .padding(3)
                        }
                        Spacer(minLength: 0)
                    }
                    .accessibilityHidden(true)
                }
            }
            .frame(minWidth: 44, minHeight: 44)
            .overlay(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .strokeBorder(cell.isCurrent ? ringColor : Color.clear, lineWidth: 2)
                    .padding(-2)
            )
            .contentShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(a11yLabel)
        .accessibilityAddTraits(cell.isCurrent ? [.isButton, .isSelected] : [.isButton])
        .playerProbe(
            "player.grid.cell.\(cell.itemId)",
            label: a11yLabel,
            isButton: true,
            isSelected: cell.isCurrent
        )
    }

    private func cellAccessibilityLabel(_ cell: GridCellUI) -> String {
        var tokens: [String] = [
            PlayerStrings.text(
                "attempt.nav.questionNumber",
                locale: locale,
                replacements: ["number": String(cell.displayNo)]
            )
        ]
        switch cell.completion {
        case .answered:
            tokens.append(PlayerStrings.text("attempt.nav.stateAnswered", locale: locale))
        case .partial:
            tokens.append(PlayerStrings.text("attempt.nav.statePartial", locale: locale))
        case .blank:
            tokens.append(PlayerStrings.text("attempt.nav.stateBlank", locale: locale))
        }
        if cell.isCurrent {
            tokens.append(PlayerStrings.text("attempt.nav.stateCurrent", locale: locale))
        }
        if cell.flagged {
            tokens.append(PlayerStrings.text("attempt.nav.stateFlagged", locale: locale))
        }
        if cell.isUnsaved {
            tokens.append(PlayerStrings.text("attempt.nav.stateUnsaved", locale: locale))
        } else {
            tokens.append(PlayerStrings.text("attempt.nav.stateSaved", locale: locale))
        }
        return tokens.joined(separator: ", ")
    }

    private var legendSection: some View {
        WrappingHStackLayout(horizontalSpacing: 12, verticalSpacing: 8) {
            legendItem(
                label: PlayerStrings.text("attempt.nav.legendAnswered", locale: locale),
                identifier: "player.grid.legend.answered"
            ) {
                RoundedRectangle(cornerRadius: 4, style: .continuous)
                    .fill(palette.primaryContainerColor)
                    .overlay(
                        RoundedRectangle(cornerRadius: 4, style: .continuous)
                            .strokeBorder(palette.primaryFillColor, lineWidth: 1)
                    )
                    .frame(width: 16, height: 16)
            }

            legendItem(
                label: PlayerStrings.text("attempt.nav.legendPartial", locale: locale),
                identifier: "player.grid.legend.partial"
            ) {
                RoundedRectangle(cornerRadius: 4, style: .continuous)
                    .fill(palette.primaryContainerColor)
                    .overlay(
                        RoundedRectangle(cornerRadius: 4, style: .continuous)
                            .strokeBorder(palette.primaryFillColor, style: StrokeStyle(lineWidth: 1, dash: [3, 2]))
                    )
                    .frame(width: 16, height: 16)
            }

            legendItem(
                label: PlayerStrings.text("attempt.nav.legendBlank", locale: locale),
                identifier: "player.grid.legend.blank"
            ) {
                RoundedRectangle(cornerRadius: 4, style: .continuous)
                    .fill(palette.bgSurfaceColor)
                    .overlay(
                        RoundedRectangle(cornerRadius: 4, style: .continuous)
                            .strokeBorder(palette.borderStrongColor, lineWidth: 1)
                    )
                    .frame(width: 16, height: 16)
            }

            legendItem(
                label: PlayerStrings.text("attempt.nav.legendCurrent", locale: locale),
                identifier: "player.grid.legend.current"
            ) {
                RoundedRectangle(cornerRadius: 4, style: .continuous)
                    .fill(palette.bgSurfaceColor)
                    .overlay(
                        RoundedRectangle(cornerRadius: 4, style: .continuous)
                            .strokeBorder(palette.primaryFillColor, lineWidth: 2)
                    )
                    .frame(width: 16, height: 16)
            }

            legendItem(
                label: PlayerStrings.text("attempt.nav.legendFlagged", locale: locale),
                identifier: "player.grid.legend.flagged"
            ) {
                ZStack {
                    RoundedRectangle(cornerRadius: 4, style: .continuous)
                        .fill(palette.dangerBgColor)
                    RoundedRectangle(cornerRadius: 4, style: .continuous)
                        .strokeBorder(palette.dangerTextColor, lineWidth: 1)
                    Image(systemName: "flag.fill")
                        .font(.system(size: 8, weight: .bold))
                        .foregroundStyle(palette.dangerTextColor)
                }
                .frame(width: 16, height: 16)
            }

            legendItem(
                label: PlayerStrings.text("attempt.nav.legendUnsaved", locale: locale),
                identifier: "player.grid.legend.unsaved"
            ) {
                Circle()
                    .fill(statusPalette.warningColor)
                    .frame(width: 10, height: 10)
                    .frame(width: 16, height: 16)
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("player.grid.legend")
    }

    private func legendItem<Swatch: View>(
        label: String,
        identifier: String,
        @ViewBuilder swatch: () -> Swatch
    ) -> some View {
        HStack(spacing: 6) {
            swatch()
                .accessibilityHidden(true)
            Text(label)
                .font(.caption)
                .foregroundStyle(palette.textSecondaryColor)
                .fixedSize(horizontal: false, vertical: true)
        }
        .accessibilityElement(children: .combine)
        .playerProbe(identifier, label: label)
    }
}
