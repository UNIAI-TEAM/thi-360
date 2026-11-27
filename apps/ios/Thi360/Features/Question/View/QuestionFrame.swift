import SwiftUI

struct FiniteWidthLayout: Layout {
    var fallbackWidth: CGFloat = 600

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let safeWidth: CGFloat? = {
            guard let w = proposal.width else { return fallbackWidth }
            return w.isFinite ? max(w, 1) : fallbackWidth
        }()
        let safeHeight: CGFloat? = {
            guard let h = proposal.height else { return nil }
            return h.isFinite ? max(h, 1) : nil
        }()
        let safeProposal = ProposedViewSize(width: safeWidth, height: safeHeight)
        guard let subview = subviews.first else { return .zero }
        let measured = subview.sizeThatFits(safeProposal)
        return CGSize(
            width: measured.width.isFinite ? measured.width : fallbackWidth,
            height: measured.height.isFinite ? measured.height : 1
        )
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        guard let subview = subviews.first else { return }
        let safeWidth = bounds.width.isFinite && bounds.width > 0 ? bounds.width : fallbackWidth
        let safeHeight = bounds.height.isFinite && bounds.height > 0 ? bounds.height : nil
        subview.place(
            at: bounds.origin,
            anchor: .topLeading,
            proposal: ProposedViewSize(width: safeWidth, height: safeHeight)
        )
    }
}

struct QuestionRichText: View {
    let document: RichDocument
    let assets: AttemptAssetSource?
    let mode: RichContentMode
    let slots: [String: () -> AnyView]
    let blankNumberStart: Int?
    let formulaEngine: any FormulaEngine
    let locale: AppLocale
    let fixedWidth: CGFloat?

    init(
        document: RichDocument,
        assets: AttemptAssetSource? = nil,
        mode: RichContentMode = .answering,
        slots: [String: () -> AnyView] = [:],
        blankNumberStart: Int? = nil,
        formulaEngine: any FormulaEngine = SwaTexFormulaEngine.shared,
        locale: AppLocale = .vi,
        fixedWidth: CGFloat? = nil
    ) {
        self.document = document
        self.assets = assets
        self.mode = mode
        self.slots = slots
        self.blankNumberStart = blankNumberStart
        self.formulaEngine = formulaEngine
        self.locale = locale
        self.fixedWidth = fixedWidth
    }

    var body: some View {
        FiniteWidthLayout(fallbackWidth: max(fixedWidth ?? 358, 60)) {
            RichContentView(
                document: document,
                assets: assets,
                mode: mode,
                slots: slots,
                blankNumberStart: blankNumberStart,
                formulaEngine: formulaEngine,
                locale: locale,
                fixedWidth: fixedWidth
            )
        }
    }
}

struct WrappingHStackLayout: Layout {
    var horizontalSpacing: CGFloat = 8
    var verticalSpacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var currentX: CGFloat = 0
        var currentY: CGFloat = 0
        var lineHeight: CGFloat = 0
        var maxLineWidth: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(ProposedViewSize(width: maxWidth.isFinite ? maxWidth : nil, height: nil))
            if currentX > 0 && currentX + size.width > maxWidth {
                maxLineWidth = max(maxLineWidth, currentX - horizontalSpacing)
                currentX = 0
                currentY += lineHeight + verticalSpacing
                lineHeight = 0
            }
            currentX += size.width + horizontalSpacing
            lineHeight = max(lineHeight, size.height)
        }
        if currentX > 0 {
            maxLineWidth = max(maxLineWidth, currentX - horizontalSpacing)
        }
        return CGSize(
            width: maxWidth.isFinite ? min(maxWidth, maxLineWidth) : maxLineWidth,
            height: currentY + lineHeight
        )
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var currentX = bounds.minX
        var currentY = bounds.minY
        var lineHeight: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(ProposedViewSize(width: bounds.width, height: nil))
            if currentX > bounds.minX && currentX + size.width > bounds.maxX + 0.5 {
                currentX = bounds.minX
                currentY += lineHeight + verticalSpacing
                lineHeight = 0
            }
            subview.place(
                at: CGPoint(x: currentX, y: currentY),
                anchor: .topLeading,
                proposal: ProposedViewSize(width: min(size.width, bounds.width), height: size.height)
            )
            currentX += size.width + horizontalSpacing
            lineHeight = max(lineHeight, size.height)
        }
    }
}

struct QuestionFrame<Attachments: View, Content: View>: View {
    let item: QuestionItem
    let mode: QuestionMode
    let flagged: Bool
    let onFlag: (Bool) -> Void
    let manualTag: Bool
    let assets: AttemptAssetSource?
    let formulaEngine: any FormulaEngine
    let locale: AppLocale
    let fixedWidth: CGFloat?
    @ViewBuilder let attachments: () -> Attachments
    @ViewBuilder let content: () -> Content

    @Environment(\.brandPalette) private var brandPalette
    @Environment(\.statusPalette) private var statusPalette

    init(
        item: QuestionItem,
        mode: QuestionMode,
        flagged: Bool = false,
        onFlag: @escaping (Bool) -> Void = { _ in },
        manualTag: Bool = false,
        assets: AttemptAssetSource? = nil,
        formulaEngine: any FormulaEngine = SwaTexFormulaEngine.shared,
        locale: AppLocale = .vi,
        fixedWidth: CGFloat? = nil,
        @ViewBuilder attachments: @escaping () -> Attachments = { EmptyView() },
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.item = item
        self.mode = mode
        self.flagged = flagged
        self.onFlag = onFlag
        self.manualTag = manualTag
        self.assets = assets
        self.formulaEngine = formulaEngine
        self.locale = locale
        self.fixedWidth = fixedWidth
        self.attachments = attachments
        self.content = content
    }

    private var titleText: String {
        QuestionStrings.questionTitle(for: item, locale: locale)
    }

    private var typeChipText: String {
        QuestionStrings.typeChipLabel(for: item, locale: locale)
    }

    private var flagAccessibilityLabel: String {
        QuestionStrings.text(flagged ? "question.flagRemove" : "question.flagForReview", locale: locale)
    }

    var body: some View {
        let review = mode.reviewData

        VStack(alignment: .leading, spacing: 14) {
            attachments()

            headerRow(review: review)

            if let stem = item.stem, !stem.isEmpty {
                QuestionRichText(
                    document: RichHTMLParser.parse(stem),
                    assets: assets,
                    mode: mode.richContentMode,
                    formulaEngine: formulaEngine,
                    locale: locale,
                    fixedWidth: fixedWidth
                )
                .accessibilityIdentifier("question.\(item.id).stem")
            }

            content()

            if let solutionHtml = review?.visibleSolutionHtml {
                SolutionBlockView(
                    html: solutionHtml,
                    assets: assets,
                    formulaEngine: formulaEngine,
                    locale: locale,
                    fixedWidth: fixedWidth.map { max($0 - 32, 80) }
                )
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("question.\(item.id).frame")
    }

    @ViewBuilder
    private func headerRow(review: ItemReview?) -> some View {
        HStack(alignment: .top, spacing: 8) {
            WrappingHStackLayout(horizontalSpacing: 8, verticalSpacing: 6) {
                Text(titleText)
                    .font(.headline.weight(.bold))
                    .foregroundStyle(brandPalette.textColor)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityAddTraits(.isHeader)
                    .accessibilityIdentifier("question.\(item.id).title")
                    .questionProbe(id: "question.\(item.id).title", label: titleText)

                Text(typeChipText)
                    .font(.caption.weight(.medium))
                    .foregroundStyle(brandPalette.primaryTextColor)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background(
                        Capsule()
                            .fill(brandPalette.primaryContainerColor)
                    )
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("question.\(item.id).typeChip")
                    .questionProbe(id: "question.\(item.id).typeChip", label: typeChipText)

                if manualTag {
                    let manualText = QuestionStrings.text("question.manualGrading", locale: locale)
                    Text(manualText)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(statusPalette.infoColor)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(
                            Capsule()
                                .fill(statusPalette.infoBgColor)
                        )
                        .overlay(
                            Capsule()
                                .strokeBorder(statusPalette.infoColor.opacity(0.4), lineWidth: 1)
                        )
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityIdentifier("question.\(item.id).manualTag")
                        .questionProbe(id: "question.\(item.id).manualTag", label: manualText)
                }

                if let outcome = review?.outcome {
                    OutcomeChip(outcome: outcome, locale: locale)
                        .accessibilityIdentifier("question.\(item.id).outcomeChip")
                        .questionProbe(
                            id: "question.\(item.id).outcomeChip",
                            label: QuestionStrings.outcomeLabel(outcome, locale: locale)
                        )
                }

                if let review {
                    let resolvedMax = review.max ?? item.points
                    let pointsLabel = review.earned.map {
                        QuestionStrings.pointsText(earned: $0, max: resolvedMax, locale: locale)
                    } ?? QuestionStrings.noPointsYetText(max: resolvedMax, locale: locale)
                    ReviewPointsView(
                        earned: review.earned,
                        max: resolvedMax,
                        locale: locale
                    )
                    .accessibilityIdentifier("question.\(item.id).reviewPoints")
                    .questionProbe(id: "question.\(item.id).reviewPoints", label: pointsLabel)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            if mode.isAnswering {
                Button {
                    onFlag(!flagged)
                } label: {
                    Image(systemName: flagged ? "flag.fill" : "flag")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(flagged ? statusPalette.warningColor : brandPalette.textSecondaryColor)
                        .frame(minWidth: 44, minHeight: 44)
                        .contentShape(Rectangle())
                        .background(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(flagged ? statusPalette.warningBgColor : brandPalette.bgSurfaceColor)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .strokeBorder(flagged ? statusPalette.warningColor : brandPalette.borderColor, lineWidth: 1)
                        )
                }
                .buttonStyle(.plain)
                .accessibilityLabel(flagAccessibilityLabel)
                .accessibilityAddTraits(flagged ? [.isSelected] : [])
                .accessibilityValue(flagged ? "1" : "0")
                .accessibilityIdentifier("question.\(item.id).flagButton")
                .questionProbe(
                    id: "question.\(item.id).flagButton",
                    label: flagAccessibilityLabel,
                    value: flagged ? "1" : "0",
                    isButton: true,
                    isEnabled: true,
                    isSelected: flagged,
                    action: { onFlag(!flagged) }
                )
            }
        }
    }
}

struct OutcomeGlyph: View {
    let outcome: QuestionOutcome
    let color: Color

    private var symbolName: String {
        switch outcome {
        case .correct: "checkmark"
        case .wrong: "xmark"
        case .partial: "circle.lefthalf.filled"
        case .pendingReview: "clock"
        case .blank: "minus"
        }
    }

    var body: some View {
        Image(systemName: symbolName)
            .font(.caption.weight(.bold))
            .foregroundStyle(color)
            .accessibilityHidden(true)
    }
}

struct OutcomeChip: View {
    let outcome: QuestionOutcome
    let locale: AppLocale

    @Environment(\.brandPalette) private var brandPalette
    @Environment(\.statusPalette) private var statusPalette

    private var foreground: Color {
        switch outcome {
        case .correct: brandPalette.bgSurfaceColor
        case .partial: statusPalette.warningColor
        case .wrong: brandPalette.dangerTextColor
        case .blank: brandPalette.textSecondaryColor
        case .pendingReview: statusPalette.infoColor
        }
    }

    private var background: Color {
        switch outcome {
        case .correct: statusPalette.successColor
        case .partial: statusPalette.warningBgColor
        case .wrong: brandPalette.dangerBgColor
        case .blank: brandPalette.bgSubtleColor
        case .pendingReview: statusPalette.infoBgColor
        }
    }

    private var border: Color {
        switch outcome {
        case .correct: statusPalette.successColor
        case .partial: statusPalette.warningColor
        case .wrong: brandPalette.dangerTextColor
        case .blank: brandPalette.borderStrongColor
        case .pendingReview: statusPalette.infoColor
        }
    }

    var body: some View {
        HStack(spacing: 5) {
            OutcomeGlyph(outcome: outcome, color: foreground)
            Text(QuestionStrings.outcomeLabel(outcome, locale: locale))
                .font(.caption.weight(.semibold))
                .foregroundStyle(foreground)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 4)
        .background(Capsule().fill(background))
        .overlay(
            Capsule()
                .strokeBorder(
                    border,
                    style: outcome == .wrong
                        ? StrokeStyle(lineWidth: 1, dash: [4, 2])
                        : StrokeStyle(lineWidth: 1)
                )
        )
        .accessibilityElement(children: .combine)
    }
}

struct PartOutcomeBadge: View {
    let outcome: QuestionOutcome
    let locale: AppLocale

    @Environment(\.brandPalette) private var brandPalette
    @Environment(\.statusPalette) private var statusPalette

    private var iconColor: Color {
        switch outcome {
        case .correct: statusPalette.successColor
        case .partial: statusPalette.warningColor
        case .wrong: brandPalette.dangerTextColor
        case .blank: brandPalette.textMutedColor
        case .pendingReview: statusPalette.infoColor
        }
    }

    var body: some View {
        OutcomeGlyph(outcome: outcome, color: iconColor)
            .accessibilityHidden(false)
            .accessibilityLabel(QuestionStrings.outcomeLabel(outcome, locale: locale))
    }
}

struct ReviewPointsView: View {
    let earned: Double?
    let max: Double
    let locale: AppLocale

    @Environment(\.brandPalette) private var brandPalette

    var body: some View {
        if let earned {
            Text(QuestionStrings.pointsText(earned: earned, max: max, locale: locale))
                .font(.subheadline.weight(.bold).monospacedDigit())
                .foregroundStyle(brandPalette.textColor)
                .fixedSize(horizontal: false, vertical: true)
        } else {
            Text(QuestionStrings.noPointsYetText(max: max, locale: locale))
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(brandPalette.textSecondaryColor)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

enum ReviewTagTone {
    case correct
    case wrong
    case partial
    case neutral
    case info
}

struct ReviewTag: View {
    let label: String
    let tone: ReviewTagTone

    @Environment(\.brandPalette) private var brandPalette
    @Environment(\.statusPalette) private var statusPalette

    private var foreground: Color {
        switch tone {
        case .correct: brandPalette.bgSurfaceColor
        case .wrong: brandPalette.dangerTextColor
        case .partial: statusPalette.warningColor
        case .info: statusPalette.infoColor
        case .neutral: brandPalette.textColor
        }
    }

    private var background: Color {
        switch tone {
        case .correct: statusPalette.successColor
        case .wrong, .partial, .neutral: brandPalette.bgSurfaceColor
        case .info: statusPalette.infoBgColor
        }
    }

    private var border: Color {
        switch tone {
        case .correct: statusPalette.successColor
        case .wrong: brandPalette.dangerTextColor
        case .partial: statusPalette.warningColor
        case .info: statusPalette.infoColor
        case .neutral: brandPalette.borderStrongColor
        }
    }

    private var isDashed: Bool {
        tone == .wrong || tone == .partial
    }

    var body: some View {
        Text(label)
            .font(.caption.weight(.semibold))
            .foregroundStyle(foreground)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(RoundedRectangle(cornerRadius: 6, style: .continuous).fill(background))
            .overlay(
                RoundedRectangle(cornerRadius: 6, style: .continuous)
                    .strokeBorder(
                        border,
                        style: isDashed
                            ? StrokeStyle(lineWidth: 1, dash: [4, 2])
                            : StrokeStyle(lineWidth: 1)
                    )
            )
            .fixedSize(horizontal: false, vertical: true)
    }
}

struct SolutionBlockView: View {
    let html: String
    let assets: AttemptAssetSource?
    let formulaEngine: any FormulaEngine
    let locale: AppLocale
    let fixedWidth: CGFloat?

    @Environment(\.brandPalette) private var brandPalette
    @Environment(\.statusPalette) private var statusPalette

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(QuestionStrings.text("question.solutionLabel", locale: locale))
                .font(.caption.weight(.semibold))
                .foregroundStyle(brandPalette.primaryTextColor)
                .accessibilityAddTraits(.isHeader)

            QuestionRichText(
                document: RichHTMLParser.parse(html),
                assets: assets,
                mode: .review,
                formulaEngine: formulaEngine,
                locale: locale,
                fixedWidth: fixedWidth
            )
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(statusPalette.successBgColor)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .strokeBorder(statusPalette.successColor.opacity(0.5), lineWidth: 1)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("question.review.solutionBlock")
        .questionProbe(
            id: "question.review.solutionBlock",
            label: QuestionStrings.text("question.solutionLabel", locale: locale)
        )
    }
}
