import SwiftUI

struct ScrollToItemRequest: Equatable, Sendable {
    let itemId: ItemID
    let nonce: UInt64
}

private struct VisibleUnitAnchor: Equatable {
    let itemId: ItemID
    let minY: CGFloat
}

private struct VisibleUnitPreferenceKey: PreferenceKey {
    static var defaultValue: [VisibleUnitAnchor] = []
    static func reduce(value: inout [VisibleUnitAnchor], nextValue: () -> [VisibleUnitAnchor]) {
        value.append(contentsOf: nextValue())
    }
}

struct PlayerContent: View {
    let attemptId: String
    let ui: PlayerUI
    let answers: [ItemID: LocalAnswer]
    let locale: AppLocale
    let scrollRequest: ScrollToItemRequest?
    let assets: AttemptAssetSource?
    let formulaEngine: any FormulaEngine
    let contentWidth: CGFloat?
    let duoRightPaneQuestionsOnly: Bool
    let onAnswer: (ItemID, AnswerValue) -> Void
    let onFlag: (ItemID, Bool) -> Void
    let onVisibleItemChanged: (PartID, ItemID) -> Void
    let onUnitMaterialized: ((String) -> Void)?

    @Environment(\.brandPalette) private var palette
    @Environment(\.playerAttachmentHost) private var attachmentHost

    @State private var lastInitialScrolledPartKey: String?
    @State private var suppressTrackingUntil: Date = .distantPast
    @State private var isUserScrollTracking: Bool = false
    @State private var latestAnchors: [VisibleUnitAnchor] = []

    init(
        attemptId: String,
        ui: PlayerUI,
        answers: [ItemID: LocalAnswer],
        locale: AppLocale,
        scrollRequest: ScrollToItemRequest?,
        assets: AttemptAssetSource?,
        formulaEngine: any FormulaEngine = SwaTexFormulaEngine.shared,
        contentWidth: CGFloat? = nil,
        duoRightPaneQuestionsOnly: Bool = false,
        onAnswer: @escaping (ItemID, AnswerValue) -> Void,
        onFlag: @escaping (ItemID, Bool) -> Void,
        onVisibleItemChanged: @escaping (PartID, ItemID) -> Void,
        onUnitMaterialized: ((String) -> Void)? = nil
    ) {
        self.attemptId = attemptId
        self.ui = ui
        self.answers = answers
        self.locale = locale
        self.scrollRequest = scrollRequest
        self.assets = assets
        self.formulaEngine = formulaEngine
        self.contentWidth = contentWidth
        self.duoRightPaneQuestionsOnly = duoRightPaneQuestionsOnly
        self.onAnswer = onAnswer
        self.onFlag = onFlag
        self.onVisibleItemChanged = onVisibleItemChanged
        self.onUnitMaterialized = onUnitMaterialized
    }

    private var answerValuesMap: [String: AnswerValue] {
        var result: [String: AnswerValue] = [:]
        result.reserveCapacity(answers.count)
        for (itemId, local) in answers {
            if let value = local.value {
                result[itemId] = value
            }
        }
        return result
    }

    private var flaggedIds: Set<String> {
        var ids = Set<String>()
        for (itemId, local) in answers where local.flagged {
            ids.insert(itemId)
        }
        return ids
    }

    private var useTwoColumnForQuestionSet: Bool {
        guard !duoRightPaneQuestionsOnly else { return false }
        if ui.layout == .wide, let width = contentWidth {
            return width >= 620
        }
        return false
    }

    var body: some View {
        let mappedAnswers = answerValuesMap
        let flagged = flaggedIds
        let activePartId = ui.activePart?.partId ?? ""

        ScrollViewReader { proxy in
            ScrollView {
                if ui.unitGroups.isEmpty {
                    Text(PlayerStrings.text("attempt.player.noActiveQuestion", locale: locale))
                        .font(.body)
                        .foregroundStyle(palette.textMutedColor)
                        .frame(maxWidth: .infinity, minHeight: 200)
                        .padding(24)
                        .accessibilityIdentifier("player.content.empty")
                } else {
                    LazyVStack(alignment: .leading, spacing: 20) {
                        ForEach(ui.unitGroups) { group in
                            groupHeader(group.header)

                            ForEach(group.units) { unit in
                                unitCard(
                                    unit: unit,
                                    partId: activePartId,
                                    mappedAnswers: mappedAnswers,
                                    flagged: flagged
                                )
                                .onAppear {
                                    onUnitMaterialized?(unit.key)
                                }
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 16)
                }
            }
            .coordinateSpace(name: "player.content.scroll")
            .onScrollPhaseChange { oldPhase, newPhase in
                switch newPhase {
                case .interacting, .decelerating:
                    isUserScrollTracking = true
                case .idle:
                    if (oldPhase == .interacting || oldPhase == .decelerating),
                       Date() >= suppressTrackingUntil,
                       !activePartId.isEmpty,
                       let topItem = Self.pickTopVisibleItem(from: latestAnchors),
                       ui.currentItemId != topItem
                    {
                        onVisibleItemChanged(activePartId, topItem)
                    }
                    isUserScrollTracking = false
                default:
                    isUserScrollTracking = false
                }
            }
            .onPreferenceChange(VisibleUnitPreferenceKey.self) { anchors in
                latestAnchors = anchors
                guard isUserScrollTracking,
                      Date() >= suppressTrackingUntil,
                      !activePartId.isEmpty,
                      let topItem = Self.pickTopVisibleItem(from: anchors)
                else {
                    return
                }
                if ui.currentItemId != topItem {
                    onVisibleItemChanged(activePartId, topItem)
                }
            }
            .onAppear {
                scrollToInitialPositionIfNeeded(proxy: proxy, partId: activePartId)
            }
            .onChange(of: activePartId) { _, newPartId in
                scrollToInitialPositionIfNeeded(proxy: proxy, partId: newPartId)
            }
            .onChange(of: scrollRequest) { _, request in
                guard let request else { return }
                performScroll(to: request.itemId, proxy: proxy, animated: true)
            }
        }
        .accessibilityElement(children: .contain)
        .playerProbe("player.content")
    }

    private func scrollToInitialPositionIfNeeded(proxy: ScrollViewProxy, partId: String) {
        guard !partId.isEmpty, let target = ui.currentItemId else { return }
        let partKey = "\(attemptId)::\(partId)"
        guard lastInitialScrolledPartKey != partKey else { return }
        lastInitialScrolledPartKey = partKey
        if target != ui.allGridCells.first?.itemId {
            performScroll(to: target, proxy: proxy, animated: false)
        }
    }

    private func performScroll(to itemId: ItemID, proxy: ScrollViewProxy, animated: Bool) {
        suppressTrackingUntil = Date().addingTimeInterval(0.6)
        isUserScrollTracking = false
        let apply = {
            if animated {
                withAnimation(.easeInOut(duration: 0.2)) {
                    proxy.scrollTo(itemId, anchor: .top)
                }
            } else {
                proxy.scrollTo(itemId, anchor: .top)
            }
        }
        apply()
        Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(80))
            apply()
        }
    }

    @ViewBuilder
    private func groupHeader(_ header: UnitGroupHeader) -> some View {
        switch header {
        case .none:
            EmptyView()

        case let .segment(key, code, name, fromNo, toNo, commandCount, maxPoints, isUngrouped):
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
            var metaParts: [String] = [
                PlayerStrings.text(
                    "attempt.segments.range",
                    locale: locale,
                    replacements: ["from": String(fromNo), "to": String(toNo)]
                )
            ]
            let _ = {
                if let commandCount, commandCount > 0 {
                    metaParts.append(PlayerStrings.plural("attempt.segments.commands", count: commandCount, locale: locale))
                }
                if let maxPoints, maxPoints > 0 {
                    metaParts.append(
                        PlayerStrings.text(
                            "attempt.segments.maxPoints",
                            locale: locale,
                            replacements: ["points": PlayerStrings.formatPoints(maxPoints, locale: locale)]
                        )
                    )
                }
            }()

            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.headline.weight(.bold))
                    .foregroundStyle(palette.textColor)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityAddTraits(.isHeader)
                Text(metaParts.joined(separator: " · "))
                    .font(.caption.weight(.medium))
                    .foregroundStyle(palette.textMutedColor)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(palette.bgSubtleColor)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .strokeBorder(palette.borderColor, lineWidth: 1)
            )
            .accessibilityElement(children: .combine)
            .playerProbe("player.group.segment.\(key)", label: title)

        case let .subject(key, subjectCode, fromNo, toNo, questionCount):
            let resolvedName = SubjectNames.resolve(
                code: subjectCode,
                prepareSubjects: ui.subjectsByCode,
                locale: locale
            )
            let heading = PlayerStrings.text(
                "attempt.subjects.heading",
                locale: locale,
                replacements: ["name": resolvedName]
            )
            let range = PlayerStrings.text(
                "attempt.subjects.range",
                locale: locale,
                replacements: ["from": String(fromNo), "to": String(toNo)]
            )
            let countText = PlayerStrings.plural("attempt.subjects.questionCount", count: questionCount, locale: locale)

            VStack(alignment: .leading, spacing: 4) {
                Text(heading)
                    .font(.headline.weight(.bold))
                    .foregroundStyle(palette.primaryTextColor)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityAddTraits(.isHeader)
                Text("\(range) · \(countText)")
                    .font(.caption.weight(.medium))
                    .foregroundStyle(palette.textMutedColor)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(palette.primaryContainerColor)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .strokeBorder(palette.primaryFillColor.opacity(0.35), lineWidth: 1)
            )
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier("player.group.subject.\(key)")
        }
    }

    @ViewBuilder
    private func unitCard(
        unit: QuestionDisplayUnit,
        partId: PartID,
        mappedAnswers: [String: AnswerValue],
        flagged: Set<String>
    ) -> some View {
        let cardWidth = contentWidth.map { max($0 - 60, 220) }

        if duoRightPaneQuestionsOnly,
           case let .questionSet(_, _, _, _, _, _, children) = unit.questionItem
        {
            VStack(alignment: .leading, spacing: 16) {
                ForEach(Array(zip(unit.childItems, children)), id: \.1.id) { rawChild, mappedChild in
                    let childId = mappedChild.id
                    let childMode = ui.questionMode(for: childId, answers: answers)
                    singleQuestionCard(
                        item: mappedChild,
                        rawItem: rawChild,
                        partId: partId,
                        mode: childMode,
                        mappedAnswers: mappedAnswers,
                        flagged: flagged,
                        cardWidth: cardWidth,
                        showsAttachments: false
                    )
                    .id(childId)
                    .background(anchorReader(itemId: childId))
                }
            }
        } else if unit.kind == .questionSet {
            let anyChildEditable = unit.leafIds.contains { ui.questionMode(for: $0, answers: answers).isAnswering }
            let setMode: QuestionMode = anyChildEditable ? .answering : .locked
            let firstLeafId = unit.leafIds.first ?? unit.key

            VStack(alignment: .leading, spacing: 12) {
                QuestionView(
                    item: unit.questionItem,
                    answers: mappedAnswers,
                    mode: setMode,
                    flaggedIds: flagged,
                    onAnswer: { itemId, value in
                        guard ui.questionMode(for: itemId, answers: answers).isAnswering else { return }
                        onAnswer(itemId, value)
                    },
                    onFlag: { itemId, nextFlag in
                        guard ui.questionMode(for: itemId, answers: answers).isAnswering else { return }
                        onFlag(itemId, nextFlag)
                    },
                    assets: assets,
                    formulaEngine: formulaEngine,
                    locale: locale,
                    fixedWidth: cardWidth,
                    forceTwoColumn: useTwoColumnForQuestionSet
                ) {
                    questionSetAttachmentsSlot(unit: unit, partId: partId, mode: setMode)
                }
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(palette.bgSurfaceColor)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .strokeBorder(palette.borderColor, lineWidth: 1)
            )
            .id(unit.key)
            .background(anchorReader(itemId: firstLeafId))
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("player.unit.\(unit.key)")
        } else {
            let itemId = unit.questionItem.id
            let mode = ui.questionMode(for: itemId, answers: answers)
            singleQuestionCard(
                item: unit.questionItem,
                rawItem: unit.parentItem,
                partId: partId,
                mode: mode,
                mappedAnswers: mappedAnswers,
                flagged: flagged,
                cardWidth: cardWidth
            )
            .id(itemId)
            .background(anchorReader(itemId: itemId))
        }
    }

    private func singleQuestionCard(
        item: QuestionItem,
        rawItem: Components.Schemas.AttemptItemView,
        partId: PartID,
        mode: QuestionMode,
        mappedAnswers: [String: AnswerValue],
        flagged: Set<String>,
        cardWidth: CGFloat?,
        showsAttachments: Bool = true
    ) -> some View {
        QuestionView(
            item: item,
            answer: mappedAnswers[item.id],
            answers: mappedAnswers,
            mode: mode,
            flagged: flagged.contains(item.id),
            flaggedIds: flagged,
            onAnswer: { itemId, value in
                guard mode.isAnswering else { return }
                onAnswer(itemId, value)
            },
            onFlag: { itemId, nextFlag in
                guard mode.isAnswering else { return }
                onFlag(itemId, nextFlag)
            },
            assets: assets,
            formulaEngine: formulaEngine,
            locale: locale,
            fixedWidth: cardWidth
        ) {
            if showsAttachments {
                attachmentsSlot(item: rawItem, partId: partId, mode: mode)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(palette.bgSurfaceColor)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .strokeBorder(palette.borderColor, lineWidth: 1)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("player.unit.\(item.id)")
    }

    @ViewBuilder
    private func attachmentsSlot(
        item: Components.Schemas.AttemptItemView,
        partId: PartID,
        mode: QuestionMode
    ) -> some View {
        if let attachmentHost, let list = item.attachments, !list.isEmpty {
            attachmentHost.attachmentsView(
                for: PlayerAttachmentContext(
                    attemptId: attemptId,
                    partId: partId,
                    item: item,
                    canInteract: ui.canUseAttachments && mode.isAnswering,
                    isLocked: !mode.isAnswering,
                    locale: locale
                )
            )
        }
    }

    @ViewBuilder
    private func questionSetAttachmentsSlot(
        unit: QuestionDisplayUnit,
        partId: PartID,
        mode: QuestionMode
    ) -> some View {
        if let attachmentHost,
           PlayerAttachmentContext.hasQuestionSetAttachments(parent: unit.parentItem, children: unit.childItems)
        {
            attachmentHost.attachmentsView(
                for: PlayerAttachmentContext(
                    attemptId: attemptId,
                    partId: partId,
                    item: unit.parentItem,
                    childItems: unit.childItems,
                    canInteract: ui.canUseAttachments && mode.isAnswering,
                    isLocked: !mode.isAnswering,
                    locale: locale
                )
            )
        }
    }

    private func anchorReader(itemId: ItemID) -> some View {
        GeometryReader { geo in
            Color.clear.preference(
                key: VisibleUnitPreferenceKey.self,
                value: [VisibleUnitAnchor(itemId: itemId, minY: geo.frame(in: .named("player.content.scroll")).minY)]
            )
        }
    }

    private static func pickTopVisibleItem(from anchors: [VisibleUnitAnchor]) -> ItemID? {
        guard !anchors.isEmpty else { return nil }
        let sorted = anchors.sorted { $0.minY < $1.minY }
        if let firstVisible = sorted.first(where: { $0.minY >= -40 }) {
            return firstVisible.itemId
        }
        return sorted.last?.itemId
    }
}

struct DuoPassagePane: View {
    let attemptId: String
    let ui: PlayerUI
    let answers: [ItemID: LocalAnswer]
    let locale: AppLocale
    let assets: AttemptAssetSource?
    let formulaEngine: any FormulaEngine
    let paneWidth: CGFloat?

    @Environment(\.brandPalette) private var palette
    @Environment(\.playerAttachmentHost) private var attachmentHost

    private var activeQuestionSetUnit: QuestionDisplayUnit? {
        guard let currentId = ui.currentItemId else { return nil }
        for group in ui.unitGroups {
            for unit in group.units where unit.kind == .questionSet {
                if unit.leafIdSet.contains(currentId) {
                    return unit
                }
            }
        }
        return nil
    }

    var body: some View {
        if let unit = activeQuestionSetUnit,
           case let .questionSet(_, _, _, stem, passage, blankStart, _) = unit.questionItem
        {
            let range = unit.questionItem.displayRange
            let rangeText = QuestionStrings.questionRangeLabel(from: range.from, to: range.to, locale: locale)
            let caption = QuestionStrings.setPassageCaption(from: range.from, to: range.to, locale: locale)
            let richWidth = paneWidth.map { max($0 - 32, 160) }
            let anyChildEditable = unit.leafIds.contains { ui.questionMode(for: $0, answers: answers).isAnswering }
            let setMode: QuestionMode = anyChildEditable ? .answering : .locked

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    HStack(spacing: 8) {
                        Text(rangeText)
                            .font(.caption.weight(.bold))
                            .foregroundStyle(palette.primaryTextColor)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 4)
                            .background(Capsule().fill(palette.primaryContainerColor))
                        Text(caption)
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(palette.textSecondaryColor)
                            .fixedSize(horizontal: false, vertical: true)
                    }

                    if let attachmentHost,
                       PlayerAttachmentContext.hasQuestionSetAttachments(parent: unit.parentItem, children: unit.childItems),
                       let partId = ui.activePart?.partId
                    {
                        attachmentHost.attachmentsView(
                            for: PlayerAttachmentContext(
                                attemptId: attemptId,
                                partId: partId,
                                item: unit.parentItem,
                                childItems: unit.childItems,
                                canInteract: ui.canUseAttachments && setMode.isAnswering,
                                isLocked: !setMode.isAnswering,
                                locale: locale
                            )
                        )
                    }

                    if let stem, !stem.isEmpty {
                        QuestionRichText(
                            document: RichHTMLParser.parse(stem),
                            assets: assets,
                            mode: setMode.richContentMode,
                            formulaEngine: formulaEngine,
                            locale: locale,
                            fixedWidth: richWidth
                        )
                    }

                    if !passage.isEmpty {
                        QuestionRichText(
                            document: RichHTMLParser.parse(passage),
                            assets: assets,
                            mode: setMode.richContentMode,
                            blankNumberStart: blankStart ?? 1,
                            formulaEngine: formulaEngine,
                            locale: locale,
                            fixedWidth: richWidth
                        )
                    }
                }
                .padding(14)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .background(palette.bgSubtleColor)
            .overlay(alignment: .leading) {
                Rectangle()
                    .fill(palette.borderColor)
                    .frame(width: 1)
            }
            .accessibilityElement(children: .contain)
            .playerProbe("player.duo.passagePane", label: rangeText)
        }
    }
}
