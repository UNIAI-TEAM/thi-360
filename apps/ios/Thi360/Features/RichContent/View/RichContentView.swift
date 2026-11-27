import SwiftUI
import UIKit

struct RichContentView: View {
    let document: RichDocument
    let assets: AttemptAssetSource?
    let mode: RichContentMode
    let slots: [String: () -> AnyView]
    let blankNumberStart: Int
    let formulaEngine: any FormulaEngine
    let locale: AppLocale
    let fixedWidth: CGFloat?

    @Environment(\.brandPalette) private var brandPalette
    @Environment(\.statusPalette) private var statusPalette
    @Environment(\.colorScheme) private var colorScheme
    @State private var measuredWidth: CGFloat = 0

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
        self.blankNumberStart = blankNumberStart ?? document.blankNumberStart
        self.formulaEngine = formulaEngine
        self.locale = locale
        self.fixedWidth = fixedWidth
    }

    private var effectiveWidth: CGFloat {
        if let fixedWidth, fixedWidth > 0 {
            return fixedWidth
        }
        return measuredWidth > 0 ? measuredWidth : 358
    }

    var body: some View {
        let tokens = RichContentTokens(
            brand: brandPalette,
            status: statusPalette,
            colorScheme: colorScheme
        )

        VStack(alignment: .leading, spacing: 10) {
            ForEach(Array(document.blocks.enumerated()), id: \.offset) { _, block in
                RichBlockView(
                    block: block,
                    tokens: tokens,
                    assets: assets,
                    mode: mode,
                    slots: slots,
                    blankNumberStart: blankNumberStart,
                    formulaEngine: formulaEngine,
                    locale: locale,
                    availableWidth: effectiveWidth
                )
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .background {
            if fixedWidth == nil {
                GeometryReader { proxy in
                    Color.clear
                        .onAppear {
                            if proxy.size.width > 0 {
                                measuredWidth = proxy.size.width
                            }
                        }
                        .onChange(of: proxy.size.width) { _, newWidth in
                            if newWidth > 0 {
                                measuredWidth = newWidth
                            }
                        }
                }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("richContent.root")
    }
}

struct RichParagraphView: View {
    let inlines: [RichInline]
    let baseSize: CGFloat
    let textStyle: UIFont.TextStyle
    let textColor: UIColor
    let alignment: NSTextAlignment
    let lineSpacing: CGFloat
    let blankNumberStart: Int
    let tokens: RichContentTokens
    let assets: AttemptAssetSource?
    let mode: RichContentMode
    let formulaEngine: any FormulaEngine
    let slots: [String: () -> AnyView]
    let locale: AppLocale
    let availableWidth: CGFloat

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        let traits = UITraitCollection(preferredContentSizeCategory: UIContentSizeCategory(dynamicTypeSize))
        let font = RichDynamicTypeFont.scaledFont(
            baseSize: baseSize,
            style: [],
            textStyle: textStyle,
            traitCollection: traits
        )
        let segments = RichInlineBuilder.segmentInlines(
            inlines,
            availableWidth: availableWidth,
            pointSize: font.pointSize,
            textColor: textColor,
            formulaEngine: formulaEngine
        )

        VStack(alignment: alignment == .center ? .center : (alignment == .right ? .trailing : .leading), spacing: 6) {
            ForEach(Array(segments.enumerated()), id: \.offset) { _, segment in
                switch segment.kind {
                case let .textRun(runInlines):
                    RichTextKitParagraphRepresentable(
                        inlines: runInlines,
                        baseSize: baseSize,
                        textStyle: textStyle,
                        textColor: textColor,
                        alignment: alignment,
                        lineSpacing: lineSpacing,
                        tabularDigits: false,
                        blankNumberStart: blankNumberStart,
                        tokens: tokens,
                        formulaEngine: formulaEngine,
                        slots: slots,
                        availableWidth: availableWidth
                    )
                case let .overflowFormula(formula):
                    RichFormulaBlockView(
                        formula: formula,
                        tokens: tokens,
                        formulaEngine: formulaEngine,
                        availableWidth: availableWidth,
                        centered: formula.display == .block
                    )
                case let .inlineImage(imageData):
                    RichImageView(
                        imageData: imageData,
                        widthSpec: .full,
                        zoomable: false,
                        captionText: nil,
                        assets: assets,
                        tokens: tokens,
                        locale: locale,
                        availableWidth: availableWidth
                    )
                }
            }
        }
    }
}

struct RichBlockView: View {
    let block: RichBlock
    let tokens: RichContentTokens
    let assets: AttemptAssetSource?
    let mode: RichContentMode
    let slots: [String: () -> AnyView]
    let blankNumberStart: Int
    let formulaEngine: any FormulaEngine
    let locale: AppLocale
    let availableWidth: CGFloat

    var body: some View {
        switch block {
        case let .paragraph(inlines):
            RichParagraphView(
                inlines: inlines,
                baseSize: 17,
                textStyle: .body,
                textColor: tokens.textUIColor,
                alignment: .left,
                lineSpacing: 4,
                blankNumberStart: blankNumberStart,
                tokens: tokens,
                assets: assets,
                mode: mode,
                formulaEngine: formulaEngine,
                slots: slots,
                locale: locale,
                availableWidth: availableWidth
            )

        case let .formulaBlock(formula):
            RichFormulaBlockView(
                formula: formula,
                tokens: tokens,
                formulaEngine: formulaEngine,
                availableWidth: availableWidth,
                centered: true
            )

        case let .list(ordered, items):
            RichListView(
                ordered: ordered,
                items: items,
                tokens: tokens,
                assets: assets,
                mode: mode,
                slots: slots,
                blankNumberStart: blankNumberStart,
                formulaEngine: formulaEngine,
                locale: locale,
                availableWidth: availableWidth
            )

        case let .table(table):
            RichTableView(
                table: table,
                tokens: tokens,
                assets: assets,
                mode: mode,
                slots: slots,
                blankNumberStart: blankNumberStart,
                formulaEngine: formulaEngine,
                locale: locale,
                availableWidth: availableWidth
            )

        case let .dataTableFigure(caption, table, notes, sources):
            let innerWidth = max(availableWidth - 24, 60)
            VStack(alignment: .leading, spacing: 8) {
                if let caption, !caption.isEmpty {
                    RichParagraphView(
                        inlines: caption.map { makeBold($0) },
                        baseSize: 14,
                        textStyle: .footnote,
                        textColor: tokens.textUIColor,
                        alignment: .center,
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
                    .frame(maxWidth: .infinity, alignment: .center)
                    .accessibilityIdentifier("richContent.figure.caption")
                }

                RichTableView(
                    table: table,
                    tokens: tokens,
                    assets: assets,
                    mode: mode,
                    slots: slots,
                    blankNumberStart: blankNumberStart,
                    formulaEngine: formulaEngine,
                    locale: locale,
                    availableWidth: innerWidth
                )

                ForEach(Array(notes.enumerated()), id: \.offset) { _, note in
                    RichParagraphView(
                        inlines: note,
                        baseSize: 13,
                        textStyle: .footnote,
                        textColor: tokens.textMutedUIColor,
                        alignment: .left,
                        lineSpacing: 2,
                        blankNumberStart: blankNumberStart,
                        tokens: tokens,
                        assets: assets,
                        mode: mode,
                        formulaEngine: formulaEngine,
                        slots: slots,
                        locale: locale,
                        availableWidth: innerWidth
                    )
                    .accessibilityIdentifier("richContent.figure.note")
                }

                ForEach(Array(sources.enumerated()), id: \.offset) { _, source in
                    RichParagraphView(
                        inlines: source,
                        baseSize: 13,
                        textStyle: .footnote,
                        textColor: tokens.textMutedUIColor,
                        alignment: .left,
                        lineSpacing: 2,
                        blankNumberStart: blankNumberStart,
                        tokens: tokens,
                        assets: assets,
                        mode: mode,
                        formulaEngine: formulaEngine,
                        slots: slots,
                        locale: locale,
                        availableWidth: innerWidth
                    )
                    .accessibilityIdentifier("richContent.figure.source")
                }
            }
            .padding(12)
            .frame(maxWidth: availableWidth, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(tokens.brand.bgSurfaceColor)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .strokeBorder(tokens.brand.borderColor, lineWidth: 1)
            )
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("richContent.figure.dataTable")

        case let .crossFigure(table):
            let innerWidth = max(availableWidth - 24, 60)
            RichTableView(
                table: table,
                tokens: tokens,
                assets: assets,
                mode: mode,
                slots: slots,
                blankNumberStart: blankNumberStart,
                formulaEngine: formulaEngine,
                locale: locale,
                availableWidth: innerWidth
            )
            .padding(12)
            .frame(maxWidth: availableWidth, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(tokens.brand.bgSurfaceColor)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .strokeBorder(tokens.brand.borderColor, lineWidth: 1)
            )
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("richContent.figure.cross")

        case let .imageFigure(image, width, zoomable, caption, sources):
            let innerWidth = max(availableWidth - 24, 60)
            let captionPlain = caption.map(plainText(from:))
            VStack(alignment: .center, spacing: 8) {
                RichImageView(
                    imageData: image,
                    widthSpec: width,
                    zoomable: zoomable,
                    captionText: captionPlain,
                    assets: assets,
                    tokens: tokens,
                    locale: locale,
                    availableWidth: innerWidth
                )

                if let caption, !caption.isEmpty {
                    RichParagraphView(
                        inlines: caption.map { makeBold($0) },
                        baseSize: 14,
                        textStyle: .footnote,
                        textColor: tokens.textUIColor,
                        alignment: .center,
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
                    .accessibilityIdentifier("richContent.figure.caption")
                }

                ForEach(Array(sources.enumerated()), id: \.offset) { _, source in
                    RichParagraphView(
                        inlines: source,
                        baseSize: 13,
                        textStyle: .footnote,
                        textColor: tokens.textMutedUIColor,
                        alignment: .center,
                        lineSpacing: 2,
                        blankNumberStart: blankNumberStart,
                        tokens: tokens,
                        assets: assets,
                        mode: mode,
                        formulaEngine: formulaEngine,
                        slots: slots,
                        locale: locale,
                        availableWidth: innerWidth
                    )
                    .accessibilityIdentifier("richContent.figure.source")
                }
            }
            .padding(12)
            .frame(maxWidth: availableWidth, alignment: .center)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(tokens.brand.bgSurfaceColor)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .strokeBorder(tokens.brand.borderColor, lineWidth: 1)
            )
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("richContent.figure.image")

        case let .quote(quote):
            RichQuoteView(
                quote: quote,
                tokens: tokens,
                slots: slots,
                blankNumberStart: blankNumberStart,
                formulaEngine: formulaEngine,
                availableWidth: availableWidth
            )
        }
    }

    private func makeBold(_ inline: RichInline) -> RichInline {
        if case let .text(s, style) = inline {
            return .text(s, style: style.union(.bold))
        }
        return inline
    }

    private func plainText(from inlines: [RichInline]) -> String {
        inlines.map { item in
            switch item {
            case let .text(s, _): s
            case .lineBreak: " "
            case let .formula(f): f.delimitedSource
            case let .labeledUnderline(_, children): plainText(from: children)
            case let .blank(ordinal): "(\(blankNumberStart + ordinal - 1)) ____"
            case let .image(img): img.alt
            case let .slot(id): "[[\(id)]]"
            }
        }.joined()
    }
}

private struct RichListView: View {
    let ordered: Bool
    let items: [RichListItem]
    let tokens: RichContentTokens
    let assets: AttemptAssetSource?
    let mode: RichContentMode
    let slots: [String: () -> AnyView]
    let blankNumberStart: Int
    let formulaEngine: any FormulaEngine
    let locale: AppLocale
    let availableWidth: CGFloat

    var body: some View {
        let markerWidth: CGFloat = ordered ? 26 : 16
        let gap: CGFloat = 8
        let contentWidth = max(availableWidth - markerWidth - gap - 8, 60)

        VStack(alignment: .leading, spacing: 6) {
            ForEach(Array(items.enumerated()), id: \.offset) { index, item in
                HStack(alignment: .firstTextBaseline, spacing: gap) {
                    Text(ordered ? "\(index + 1)." : "•")
                        .font(.body)
                        .foregroundStyle(tokens.brand.textColor)
                        .frame(width: markerWidth, alignment: .trailing)
                        .accessibilityIdentifier(ordered ? "richContent.list.ordered.\(index + 1)" : "richContent.list.bullet")

                    VStack(alignment: .leading, spacing: 6) {
                        ForEach(Array(item.blocks.enumerated()), id: \.offset) { _, block in
                            RichBlockView(
                                block: block,
                                tokens: tokens,
                                assets: assets,
                                mode: mode,
                                slots: slots,
                                blankNumberStart: blankNumberStart,
                                formulaEngine: formulaEngine,
                                locale: locale,
                                availableWidth: contentWidth
                            )
                        }
                    }
                    .frame(maxWidth: contentWidth, alignment: .leading)
                }
            }
        }
        .padding(.leading, 8)
    }
}
