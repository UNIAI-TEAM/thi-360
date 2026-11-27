import SwiftUI
import UIKit

struct RichQuoteView: View {
    let quote: RichQuote
    let tokens: RichContentTokens
    let assets: AttemptAssetSource?
    let mode: RichContentMode
    let slots: [String: () -> AnyView]
    let blankNumberStart: Int
    let formulaEngine: any FormulaEngine
    let locale: AppLocale
    let availableWidth: CGFloat

    private var showsLineNumbers: Bool {
        quote.kind == .poem && quote.lineNumberStep > 0
    }

    private var innerWidth: CGFloat {
        max(availableWidth - 32, 60)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if showsLineNumbers {
                poemBodyWithLineNumbers
            } else {
                ForEach(Array(quote.paragraphs.enumerated()), id: \.offset) { _, paragraph in
                    RichParagraphView(
                        inlines: paragraph,
                        baseSize: 16,
                        textStyle: .body,
                        textColor: tokens.textUIColor,
                        alignment: .left,
                        lineSpacing: 6,
                        blankNumberStart: blankNumberStart,
                        tokens: tokens,
                        assets: assets,
                        mode: mode,
                        formulaEngine: formulaEngine,
                        slots: slots,
                        locale: locale,
                        availableWidth: innerWidth
                    )
                }
            }

            if let footer = quote.footer, !footer.isEmpty {
                RichParagraphView(
                    inlines: footer,
                    baseSize: 15,
                    textStyle: .callout,
                    textColor: tokens.textSecondaryUIColor,
                    alignment: .right,
                    lineSpacing: 4,
                    blankNumberStart: blankNumberStart,
                    tokens: tokens,
                    assets: assets,
                    mode: mode,
                    formulaEngine: formulaEngine,
                    slots: slots,
                    locale: locale,
                    availableWidth: innerWidth
                )
                .frame(maxWidth: .infinity, alignment: .trailing)
                .accessibilityIdentifier("richContent.quote.footer")
            }

            if let source = quote.source, !source.isEmpty {
                RichParagraphView(
                    inlines: source,
                    baseSize: 13,
                    textStyle: .footnote,
                    textColor: tokens.textMutedUIColor,
                    alignment: .left,
                    lineSpacing: 3,
                    blankNumberStart: blankNumberStart,
                    tokens: tokens,
                    assets: assets,
                    mode: mode,
                    formulaEngine: formulaEngine,
                    slots: slots,
                    locale: locale,
                    availableWidth: innerWidth
                )
                .accessibilityIdentifier("richContent.quote.source")
            }
        }
        .padding(16)
        .frame(maxWidth: availableWidth, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(tokens.brand.bgPageColor)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .strokeBorder(tokens.brand.borderColor, lineWidth: 1)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("richContent.quote.\(quote.kind.rawValue)")
    }

    @ViewBuilder
    private var poemBodyWithLineNumbers: some View {
        let stanzas = Self.numberedPoemStanzas(
            paragraphs: quote.paragraphs,
            step: quote.lineNumberStep
        )
        let gutterWidth: CGFloat = 28
        let gap: CGFloat = 10
        let lineContentWidth = max(innerWidth - gutterWidth - gap, 60)

        VStack(alignment: .leading, spacing: 10) {
            ForEach(Array(stanzas.enumerated()), id: \.offset) { _, stanza in
                VStack(alignment: .leading, spacing: 4) {
                    ForEach(stanza) { line in
                        HStack(alignment: .firstTextBaseline, spacing: gap) {
                            Text(line.displayedNumber ?? "")
                                .font(.caption.monospacedDigit())
                                .foregroundStyle(tokens.brand.textMutedColor)
                                .frame(width: gutterWidth, alignment: .trailing)
                                .accessibilityIdentifier(
                                    line.displayedNumber != nil
                                        ? "richContent.quote.lineNumber.\(line.number)"
                                        : "richContent.quote.lineNumber.empty.\(line.number)"
                                )

                            RichParagraphView(
                                inlines: line.inlines,
                                baseSize: 16,
                                textStyle: .body,
                                textColor: tokens.textUIColor,
                                alignment: .left,
                                lineSpacing: 5,
                                blankNumberStart: blankNumberStart,
                                tokens: tokens,
                                assets: assets,
                                mode: mode,
                                formulaEngine: formulaEngine,
                                slots: slots,
                                locale: locale,
                                availableWidth: lineContentWidth
                            )
                            .frame(maxWidth: lineContentWidth, alignment: .leading)
                        }
                    }
                }
            }
        }
    }

    struct NumberedPoemLine: Identifiable, Equatable {
        let number: Int
        let displayedNumber: String?
        let inlines: [RichInline]
        var id: Int { number }
    }

    static func numberedPoemStanzas(
        paragraphs: [[RichInline]],
        step: Int
    ) -> [[NumberedPoemLine]] {
        var lineCounter = 0
        return paragraphs.map { paragraph in
            var rawLines: [[RichInline]] = [[]]
            for inline in paragraph {
                if case .lineBreak = inline {
                    rawLines.append([])
                } else {
                    rawLines[rawLines.count - 1].append(inline)
                }
            }
            return rawLines.map { lineInlines in
                lineCounter += 1
                let label = (step > 0 && lineCounter % step == 0) ? String(lineCounter) : nil
                return NumberedPoemLine(number: lineCounter, displayedNumber: label, inlines: lineInlines)
            }
        }
    }
}
