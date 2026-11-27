import SwiftUI

struct QuestionSetView<Attachments: View>: View {
    let item: QuestionItem
    let passage: String
    let children: [QuestionItem]
    let answers: [String: AnswerValue]
    let mode: QuestionMode
    let flaggedIds: Set<String>
    let onAnswer: (String, AnswerValue) -> Void
    let onFlag: (String, Bool) -> Void
    let assets: AttemptAssetSource?
    let formulaEngine: any FormulaEngine
    let locale: AppLocale
    let fixedWidth: CGFloat?
    @ViewBuilder let attachments: () -> Attachments

    @Environment(\.brandPalette) private var brandPalette
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    private var isSideBySide: Bool {
        if let fixedWidth {
            return horizontalSizeClass == .regular && fixedWidth >= 700
        }
        return horizontalSizeClass == .regular
    }

    private var displayRange: (from: Int, to: Int) {
        item.displayRange
    }

    private var captionText: String {
        QuestionStrings.setPassageCaption(from: displayRange.from, to: displayRange.to, locale: locale)
    }

    private var rangeBarText: String {
        QuestionStrings.questionRangeLabel(from: displayRange.from, to: displayRange.to, locale: locale)
    }

    var body: some View {
        Group {
            if isSideBySide {
                regularSplitLayout
            } else {
                compactStackedLayout
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("question.\(item.id).setContainer")
    }

    private var compactStackedLayout: some View {
        VStack(alignment: .leading, spacing: 18) {
            passageCard(contentWidth: fixedWidth.map { max($0 - 28, 80) }, showRangeBar: false)
            childrenStack(contentWidth: fixedWidth)
            setSolutionBlock(contentWidth: fixedWidth.map { max($0 - 32, 80) })
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("question.\(item.id).setCompact")
        .questionProbe(id: "question.\(item.id).setCompact", label: captionText)
    }

    private var regularSplitLayout: some View {
        let paneWidth = fixedWidth.map { max(($0 - 20) / 2, 240) }

        return HStack(alignment: .top, spacing: 20) {
            ScrollView {
                passageCard(
                    contentWidth: paneWidth.map { max($0 - 28, 120) },
                    showRangeBar: true
                )
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .accessibilityIdentifier("question.\(item.id).passageScroll")
            .questionProbe(id: "question.\(item.id).passageScroll", label: rangeBarText)

            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    childrenStack(contentWidth: paneWidth)
                    setSolutionBlock(contentWidth: paneWidth.map { max($0 - 32, 120) })
                }
                .padding(.bottom, 16)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .accessibilityIdentifier("question.\(item.id).childrenScroll")
            .questionProbe(id: "question.\(item.id).childrenScroll")
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("question.\(item.id).setRegularSplit")
        .questionProbe(id: "question.\(item.id).setRegularSplit", label: captionText)
    }

    @ViewBuilder
    private func passageCard(contentWidth: CGFloat?, showRangeBar: Bool) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            if showRangeBar {
                HStack(spacing: 8) {
                    Text(rangeBarText)
                        .font(.caption.weight(.bold))
                        .foregroundStyle(brandPalette.primaryTextColor)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(Capsule().fill(brandPalette.primaryContainerColor))
                        .accessibilityIdentifier("question.\(item.id).rangeBar")

                    Text(captionText)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(brandPalette.textSecondaryColor)
                        .fixedSize(horizontal: false, vertical: true)
                }
            } else {
                Text(captionText)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(brandPalette.primaryTextColor)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("question.\(item.id).passageCaption")
            }

            attachments()

            if let stem = item.stem, !stem.isEmpty {
                QuestionRichText(
                    document: RichHTMLParser.parse(stem),
                    assets: assets,
                    mode: mode.richContentMode,
                    formulaEngine: formulaEngine,
                    locale: locale,
                    fixedWidth: contentWidth
                )
            }

            if !passage.isEmpty {
                QuestionRichText(
                    document: RichHTMLParser.parse(passage),
                    assets: assets,
                    mode: mode.richContentMode,
                    formulaEngine: formulaEngine,
                    locale: locale,
                    fixedWidth: contentWidth
                )
                .accessibilityIdentifier("question.\(item.id).passageBody")
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(brandPalette.bgSubtleColor)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .strokeBorder(brandPalette.borderColor, lineWidth: 1)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("question.\(item.id).passageCard")
    }

    private func childrenStack(contentWidth: CGFloat?) -> some View {
        VStack(alignment: .leading, spacing: 20) {
            ForEach(children) { child in
                QuestionView(
                    item: child,
                    answer: answers[child.id],
                    answers: answers,
                    mode: childMode(for: child),
                    flagged: flaggedIds.contains(child.id),
                    flaggedIds: flaggedIds,
                    onAnswer: { childId, value in
                        onAnswer(childId, value)
                    },
                    onFlag: { childId, nextFlag in
                        onFlag(childId, nextFlag)
                    },
                    assets: assets,
                    formulaEngine: formulaEngine,
                    locale: locale,
                    fixedWidth: contentWidth
                )
                .id(child.id)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    @ViewBuilder
    private func setSolutionBlock(contentWidth: CGFloat?) -> some View {
        if let solutionHtml = mode.reviewData?.visibleSolutionHtml {
            SolutionBlockView(
                html: solutionHtml,
                assets: assets,
                formulaEngine: formulaEngine,
                locale: locale,
                fixedWidth: contentWidth
            )
        }
    }

    private func childMode(for child: QuestionItem) -> QuestionMode {
        switch mode {
        case .answering:
            .answering
        case .locked:
            .locked
        case let .review(setReview):
            .review(setReview.childReview(for: child.id))
        }
    }
}
