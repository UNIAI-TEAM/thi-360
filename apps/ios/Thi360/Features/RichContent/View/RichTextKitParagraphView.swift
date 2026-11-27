import SwiftUI
import UIKit

enum RichDynamicTypeFont {
    static let maxScaleFactor: CGFloat = 2.0

    static func scaledBodyPointSize(
        baseSize: CGFloat = 17,
        traitCollection: UITraitCollection
    ) -> CGFloat {
        let metrics = UIFontMetrics(forTextStyle: .body)
        let scaled = metrics.scaledValue(for: baseSize, compatibleWith: traitCollection)
        return min(scaled, baseSize * maxScaleFactor)
    }

    static func scaledFont(
        baseSize: CGFloat = 17,
        style: RichTextStyle = [],
        textStyle: UIFont.TextStyle = .body,
        monospaced: Bool = false,
        tabularDigits: Bool = false,
        traitCollection: UITraitCollection
    ) -> UIFont {
        let metrics = UIFontMetrics(forTextStyle: textStyle)
        let scaledSize = min(
            metrics.scaledValue(for: baseSize, compatibleWith: traitCollection),
            baseSize * maxScaleFactor
        )
        let effectiveSize = (style.contains(.subscript) || style.contains(.superscript))
            ? max(scaledSize * 0.72, 9)
            : scaledSize

        var font: UIFont
        if monospaced {
            let weight: UIFont.Weight = style.contains(.bold) ? .semibold : .regular
            font = UIFont.monospacedSystemFont(ofSize: effectiveSize, weight: weight)
        } else {
            let weight: UIFont.Weight = style.contains(.bold) ? .semibold : .regular
            font = UIFont.systemFont(ofSize: effectiveSize, weight: weight)
        }

        var traits: UIFontDescriptor.SymbolicTraits = []
        if style.contains(.bold) { traits.insert(.traitBold) }
        if style.contains(.italic) { traits.insert(.traitItalic) }
        if !traits.isEmpty, let descriptor = font.fontDescriptor.withSymbolicTraits(traits) {
            font = UIFont(descriptor: descriptor, size: effectiveSize)
        }

        if tabularDigits {
            let featureSettings: [[UIFontDescriptor.FeatureKey: Int]] = [
                [
                    .type: kNumberSpacingType,
                    .selector: kMonospacedNumbersSelector
                ]
            ]
            let descriptor = font.fontDescriptor.addingAttributes([.featureSettings: featureSettings])
            font = UIFont(descriptor: descriptor, size: effectiveSize)
        }

        return font
    }
}

final class FormulaTextAttachment: NSTextAttachment {
    let formulaSource: String
    let formulaAscent: CGFloat
    let formulaDescent: CGFloat

    init(
        image: UIImage,
        width: CGFloat,
        ascent: CGFloat,
        descent: CGFloat,
        delimitedSource: String
    ) {
        self.formulaSource = delimitedSource
        self.formulaAscent = ascent
        self.formulaDescent = descent
        super.init(data: nil, ofType: nil)
        self.image = image
        self.bounds = CGRect(x: 0, y: -descent, width: width, height: ascent + descent)
        self.accessibilityLabel = delimitedSource
    }

    required init?(coder: NSCoder) {
        nil
    }
}

final class SlotTextAttachment: NSTextAttachment {
    static let utiType = "vn.unicom.thi360.richcontent.slot"

    private static let registerOnce: Void = {
        NSTextAttachment.registerViewProviderClass(
            SlotTextAttachmentViewProvider.self,
            forFileType: SlotTextAttachment.utiType
        )
    }()

    let slotID: String
    let referenceFont: UIFont
    weak var slotHostPool: RichSlotHostPool?

    init(slotID: String, referenceFont: UIFont, slotHostPool: RichSlotHostPool) {
        _ = Self.registerOnce
        self.slotID = slotID
        self.referenceFont = referenceFont
        self.slotHostPool = slotHostPool
        super.init(data: Data(slotID.utf8), ofType: Self.utiType)
    }

    required init?(coder: NSCoder) {
        nil
    }
}

final class SlotTextAttachmentViewProvider: NSTextAttachmentViewProvider {
    override init(
        textAttachment: NSTextAttachment,
        parentView: UIView?,
        textLayoutManager: NSTextLayoutManager?,
        location: any NSTextLocation
    ) {
        super.init(
            textAttachment: textAttachment,
            parentView: parentView,
            textLayoutManager: textLayoutManager,
            location: location
        )
        tracksTextAttachmentViewBounds = true
    }

    override func loadView() {
        guard let attachment = textAttachment as? SlotTextAttachment,
              let pool = attachment.slotHostPool
        else {
            view = UIView(frame: CGRect(x: 0, y: 0, width: 64, height: 28))
            return
        }
        let hosted = MainActor.assumeIsolated {
            pool.hostedView(for: attachment.slotID)
        }
        view = hosted
    }

    override func attachmentBounds(
        for attributes: [NSAttributedString.Key: Any],
        location: any NSTextLocation,
        textContainer: NSTextContainer?,
        proposedLineFragment: CGRect,
        position: CGPoint
    ) -> CGRect {
        guard let attachment = textAttachment as? SlotTextAttachment,
              let pool = attachment.slotHostPool
        else {
            return CGRect(x: 0, y: -4, width: 64, height: 28)
        }
        let maxW = max((textContainer?.size.width ?? 320) - 4, 60)
        let size = MainActor.assumeIsolated {
            pool.measure(slotID: attachment.slotID, maxWidth: maxW)
        }
        let font = attachment.referenceFont
        let yOffset = -((size.height - font.capHeight) / 2)
        return CGRect(x: 0, y: yOffset, width: size.width, height: size.height)
    }
}

@MainActor
final class RichSlotHostPool {
    private var factories: [String: () -> AnyView] = [:]
    private var controllers: [String: UIHostingController<AnyView>] = [:]

    func updateFactories(_ newFactories: [String: () -> AnyView]) {
        self.factories = newFactories
        for (id, controller) in controllers {
            if let factory = newFactories[id] {
                controller.rootView = factory()
            }
        }
        for id in Array(controllers.keys) where newFactories[id] == nil {
            controllers.removeValue(forKey: id)
        }
    }

    func hostedView(for slotID: String) -> UIView {
        if let existing = controllers[slotID] {
            return existing.view
        }
        let rootView = factories[slotID]?() ?? AnyView(EmptyView())
        let controller = UIHostingController(rootView: rootView)
        controller.view.backgroundColor = .clear
        controller.sizingOptions = .intrinsicContentSize
        controllers[slotID] = controller
        return controller.view
    }

    func measure(slotID: String, maxWidth: CGFloat) -> CGSize {
        let hostView = hostedView(for: slotID)
        let target = CGSize(width: maxWidth, height: UIView.layoutFittingCompressedSize.height)
        let measured = hostView.systemLayoutSizeFitting(
            target,
            withHorizontalFittingPriority: .fittingSizeLevel,
            verticalFittingPriority: .fittingSizeLevel
        )
        return CGSize(
            width: min(max(measured.width, 44), maxWidth),
            height: max(measured.height, 28)
        )
    }
}

final class RichTextKitTextView: UITextView {
    var hasInteractiveSlots = false
    var customAccessibilityDescription: String = ""

    convenience init() {
        self.init(usingTextLayoutManager: true)
        isEditable = false
        isSelectable = false
        isScrollEnabled = false
        dataDetectorTypes = []
        backgroundColor = .clear
        textContainerInset = .zero
        textContainer.lineFragmentPadding = 0
        textContainer.lineBreakMode = .byWordWrapping
        adjustsFontForContentSizeCategory = false
        setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        setContentHuggingPriority(.required, for: .vertical)
    }

    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        guard hasInteractiveSlots else { return nil }
        let hit = super.hitTest(point, with: event)
        return hit === self ? nil : hit
    }

    override var accessibilityLabel: String? {
        get { customAccessibilityDescription.isEmpty ? super.accessibilityLabel : customAccessibilityDescription }
        set { customAccessibilityDescription = newValue ?? "" }
    }
}

struct RichParagraphSegment: Equatable {
    enum Kind: Equatable {
        case textRun([RichInline])
        case overflowFormula(Formula)
        case inlineImage(RichImageData)
    }
    let kind: Kind
}

enum RichInlineBuilder {
    static func segmentInlines(
        _ inlines: [RichInline],
        availableWidth: CGFloat,
        pointSize: CGFloat,
        textColor: UIColor,
        formulaEngine: any FormulaEngine
    ) -> [RichParagraphSegment] {
        var segments: [RichParagraphSegment] = []
        var buffer: [RichInline] = []

        func flush() {
            if !buffer.isEmpty {
                segments.append(RichParagraphSegment(kind: .textRun(buffer)))
                buffer.removeAll(keepingCapacity: true)
            }
        }

        for inline in inlines {
            switch inline {
            case let .image(imageData):
                flush()
                segments.append(RichParagraphSegment(kind: .inlineImage(imageData)))

            case let .formula(formula):
                if formula.display == .block {
                    flush()
                    segments.append(RichParagraphSegment(kind: .overflowFormula(formula)))
                } else if availableWidth > 0 {
                    let rendered = formulaEngine.render(formula, pointSize: pointSize, color: textColor)
                    switch rendered {
                    case let .rendered(_, width, _, _):
                        if width > availableWidth {
                            flush()
                            segments.append(RichParagraphSegment(kind: .overflowFormula(formula)))
                        } else {
                            buffer.append(inline)
                        }
                    case let .unsupported(source):
                        let badgeWidth = measureFallbackBadgeWidth(source: source, pointSize: pointSize)
                        if badgeWidth > availableWidth {
                            flush()
                            segments.append(RichParagraphSegment(kind: .overflowFormula(formula)))
                        } else {
                            buffer.append(inline)
                        }
                    }
                } else {
                    buffer.append(inline)
                }

            default:
                buffer.append(inline)
            }
        }

        flush()
        return segments
    }

    @MainActor
    static func buildAttributedString(
        from inlines: [RichInline],
        baseSize: CGFloat,
        textStyle: UIFont.TextStyle,
        textColor: UIColor,
        alignment: NSTextAlignment,
        lineSpacing: CGFloat,
        tabularDigits: Bool,
        blankNumberStart: Int,
        tokens: RichContentTokens,
        traitCollection: UITraitCollection,
        formulaEngine: any FormulaEngine,
        slotHostPool: RichSlotHostPool,
        maxAttachmentWidth: CGFloat
    ) -> (attributed: NSAttributedString, accessibilityText: String, hasSlots: Bool) {
        let output = NSMutableAttributedString()
        var a11yParts: [String] = []
        var hasSlots = false

        let baseFont = RichDynamicTypeFont.scaledFont(
            baseSize: baseSize,
            style: [],
            textStyle: textStyle,
            tabularDigits: tabularDigits,
            traitCollection: traitCollection
        )
        let paragraphStyle = NSMutableParagraphStyle()
        paragraphStyle.alignment = alignment
        paragraphStyle.lineSpacing = lineSpacing
        paragraphStyle.lineBreakMode = .byWordWrapping

        for inline in inlines {
            switch inline {
            case let .text(string, style):
                let font = RichDynamicTypeFont.scaledFont(
                    baseSize: baseSize,
                    style: style,
                    textStyle: textStyle,
                    tabularDigits: tabularDigits,
                    traitCollection: traitCollection
                )
                var attrs: [NSAttributedString.Key: Any] = [
                    .font: font,
                    .foregroundColor: textColor,
                    .paragraphStyle: paragraphStyle
                ]
                if style.contains(.underline) {
                    attrs[.underlineStyle] = NSUnderlineStyle.single.rawValue
                }
                if style.contains(.strikethrough) {
                    attrs[.strikethroughStyle] = NSUnderlineStyle.single.rawValue
                }
                if style.contains(.subscript) {
                    attrs[.baselineOffset] = -(baseFont.pointSize * 0.22)
                } else if style.contains(.superscript) {
                    attrs[.baselineOffset] = baseFont.pointSize * 0.34
                }
                output.append(NSAttributedString(string: string, attributes: attrs))
                a11yParts.append(string)

            case .lineBreak:
                output.append(NSAttributedString(
                    string: "\n",
                    attributes: [.font: baseFont, .paragraphStyle: paragraphStyle]
                ))
                a11yParts.append("\n")

            case let .formula(formula):
                let rendered = formulaEngine.render(
                    formula,
                    pointSize: baseFont.pointSize,
                    color: textColor
                )
                switch rendered {
                case let .rendered(image, width, ascent, descent):
                    let attachment = FormulaTextAttachment(
                        image: image,
                        width: width,
                        ascent: ascent,
                        descent: descent,
                        delimitedSource: formula.delimitedSource
                    )
                    let attrStr = NSMutableAttributedString(attachment: attachment)
                    attrStr.addAttribute(.paragraphStyle, value: paragraphStyle, range: NSRange(location: 0, length: 1))
                    output.append(attrStr)
                case let .unsupported(source):
                    let attachment = makeFallbackAttachment(
                        source: source,
                        baseFont: baseFont,
                        tokens: tokens,
                        scale: max(traitCollection.displayScale, 2.0)
                    )
                    let attrStr = NSMutableAttributedString(attachment: attachment)
                    attrStr.addAttribute(.paragraphStyle, value: paragraphStyle, range: NSRange(location: 0, length: 1))
                    output.append(attrStr)
                }
                a11yParts.append(formula.delimitedSource)

            case let .labeledUnderline(label, children):
                let plainChildren = extractPlainText(children)
                let attachment = makeLabeledUnderlineAttachment(
                    text: plainChildren,
                    label: label,
                    baseFont: baseFont,
                    textColor: textColor,
                    accentColor: tokens.primaryTextUIColor,
                    maxWidth: max(maxAttachmentWidth, 80),
                    scale: max(traitCollection.displayScale, 2.0)
                )
                let attrStr = NSMutableAttributedString(attachment: attachment)
                attrStr.addAttribute(.paragraphStyle, value: paragraphStyle, range: NSRange(location: 0, length: 1))
                output.append(attrStr)
                a11yParts.append("\(plainChildren) (\(label))")

            case let .blank(ordinal):
                let displayNumber = blankNumberStart + ordinal - 1
                let chipText = "(\(displayNumber)) ____"
                let attachment = makeBlankChipAttachment(
                    text: chipText,
                    baseFont: baseFont,
                    tokens: tokens,
                    scale: max(traitCollection.displayScale, 2.0)
                )
                let attrStr = NSMutableAttributedString(attachment: attachment)
                attrStr.addAttribute(.paragraphStyle, value: paragraphStyle, range: NSRange(location: 0, length: 1))
                output.append(attrStr)
                a11yParts.append(chipText)

            case let .slot(id):
                hasSlots = true
                let attachment = SlotTextAttachment(
                    slotID: id,
                    referenceFont: baseFont,
                    slotHostPool: slotHostPool
                )
                let attrStr = NSMutableAttributedString(attachment: attachment)
                attrStr.addAttribute(.paragraphStyle, value: paragraphStyle, range: NSRange(location: 0, length: 1))
                output.append(attrStr)
                a11yParts.append("[\(id)]")

            case .image:
                break
            }
        }

        return (output, a11yParts.joined(), hasSlots)
    }

    static func measureFallbackBadgeWidth(source: String, pointSize: CGFloat) -> CGFloat {
        let monoFont = UIFont.monospacedSystemFont(ofSize: max(pointSize * 0.8125, 11), weight: .regular)
        let textSize = (source as NSString).size(withAttributes: [.font: monoFont])
        return ceil(textSize.width) + 14
    }

    private static func makeFallbackAttachment(
        source: String,
        baseFont: UIFont,
        tokens: RichContentTokens,
        scale: CGFloat
    ) -> NSTextAttachment {
        let monoFont = UIFont.monospacedSystemFont(ofSize: max(baseFont.pointSize * 0.8125, 11), weight: .regular)
        let attrs: [NSAttributedString.Key: Any] = [
            .font: monoFont,
            .foregroundColor: tokens.warningUIColor
        ]
        let textSize = (source as NSString).size(withAttributes: attrs)
        let padX: CGFloat = 6
        let padY: CGFloat = 2
        let size = CGSize(width: ceil(textSize.width) + padX * 2, height: ceil(textSize.height) + padY * 2)

        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.opaque = false
        let image = UIGraphicsImageRenderer(size: size, format: format).image { ctx in
            let rect = CGRect(origin: .zero, size: size).insetBy(dx: 0.5, dy: 0.5)
            let path = UIBezierPath(roundedRect: rect, cornerRadius: 4)
            tokens.warningBgUIColor.setFill()
            path.fill()
            tokens.warningUIColor.setStroke()
            path.lineWidth = 1
            path.stroke()
            (source as NSString).draw(at: CGPoint(x: padX, y: padY), withAttributes: attrs)
        }

        let attachment = NSTextAttachment()
        attachment.image = image
        let descent = max(-monoFont.descender + padY, 2)
        attachment.bounds = CGRect(x: 0, y: -descent, width: size.width, height: size.height)
        attachment.accessibilityLabel = source
        return attachment
    }

    private static func makeBlankChipAttachment(
        text: String,
        baseFont: UIFont,
        tokens: RichContentTokens,
        scale: CGFloat
    ) -> NSTextAttachment {
        let chipFont = UIFont.systemFont(ofSize: max(baseFont.pointSize * 0.8125, 12), weight: .semibold)
        let attrs: [NSAttributedString.Key: Any] = [
            .font: chipFont,
            .foregroundColor: tokens.infoUIColor
        ]
        let textSize = (text as NSString).size(withAttributes: attrs)
        let padX: CGFloat = 8
        let padY: CGFloat = 2
        let size = CGSize(
            width: ceil(textSize.width) + padX * 2,
            height: max(ceil(textSize.height) + padY * 2, 22)
        )

        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.opaque = false
        let image = UIGraphicsImageRenderer(size: size, format: format).image { _ in
            let rect = CGRect(origin: .zero, size: size).insetBy(dx: 0.5, dy: 0.5)
            let path = UIBezierPath(roundedRect: rect, cornerRadius: 6)
            tokens.infoBgUIColor.setFill()
            path.fill()
            tokens.infoUIColor.setStroke()
            path.lineWidth = 1
            let pattern: [CGFloat] = [4, 2]
            path.setLineDash(pattern, count: pattern.count, phase: 0)
            path.stroke()

            let drawY = (size.height - textSize.height) / 2
            (text as NSString).draw(at: CGPoint(x: padX, y: drawY), withAttributes: attrs)
        }

        let attachment = NSTextAttachment()
        attachment.image = image
        let yOffset = -((size.height - baseFont.capHeight) / 2)
        attachment.bounds = CGRect(x: 0, y: yOffset, width: size.width, height: size.height)
        attachment.accessibilityLabel = text
        return attachment
    }

    private static func makeLabeledUnderlineAttachment(
        text: String,
        label: String,
        baseFont: UIFont,
        textColor: UIColor,
        accentColor: UIColor,
        maxWidth: CGFloat,
        scale: CGFloat
    ) -> NSTextAttachment {
        let textAttrs: [NSAttributedString.Key: Any] = [
            .font: baseFont,
            .foregroundColor: textColor
        ]
        let labelFont = UIFont.systemFont(ofSize: max(baseFont.pointSize * 0.65, 11), weight: .bold)
        let labelAttrs: [NSAttributedString.Key: Any] = [
            .font: labelFont,
            .foregroundColor: accentColor
        ]

        let rawTextSize = (text as NSString).boundingRect(
            with: CGSize(width: maxWidth, height: .greatestFiniteMagnitude),
            options: [.usesLineFragmentOrigin, .usesFontLeading],
            attributes: textAttrs,
            context: nil
        ).size
        let labelSize = (label as NSString).size(withAttributes: labelAttrs)

        let width = ceil(min(max(rawTextSize.width, labelSize.width) + 2, maxWidth))
        let textHeight = ceil(max(rawTextSize.height, baseFont.lineHeight))
        let underlineGap: CGFloat = 1.5
        let labelHeight = ceil(labelSize.height)
        let bottomPad: CGFloat = 2
        let totalHeight = textHeight + underlineGap + labelHeight + bottomPad

        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.opaque = false
        let image = UIGraphicsImageRenderer(size: CGSize(width: width, height: totalHeight), format: format).image { ctx in
            let textRect = CGRect(x: 0, y: 0, width: width, height: textHeight)
            (text as NSString).draw(
                with: textRect,
                options: [.usesLineFragmentOrigin, .usesFontLeading],
                attributes: textAttrs,
                context: nil
            )

            let underlineY = textHeight - 0.5
            ctx.cgContext.setStrokeColor(accentColor.cgColor)
            ctx.cgContext.setLineWidth(1.5)
            ctx.cgContext.move(to: CGPoint(x: 0, y: underlineY))
            ctx.cgContext.addLine(to: CGPoint(x: width, y: underlineY))
            ctx.cgContext.strokePath()

            let labelX = max((width - labelSize.width) / 2, 0)
            let labelY = textHeight + underlineGap
            (label as NSString).draw(at: CGPoint(x: labelX, y: labelY), withAttributes: labelAttrs)
        }

        let attachment = NSTextAttachment()
        attachment.image = image
        // Align the top text's baseline with surrounding text while extending descent below to reserve room for the label.
        let baselineFromTop = baseFont.ascender
        let descentBelowBaseline = max(totalHeight - baselineFromTop, 0)
        attachment.bounds = CGRect(x: 0, y: -descentBelowBaseline, width: width, height: totalHeight)
        attachment.accessibilityLabel = "\(text) (\(label))"
        return attachment
    }

    private static func extractPlainText(_ inlines: [RichInline]) -> String {
        inlines.map { item in
            switch item {
            case let .text(s, _): s
            case .lineBreak: " "
            case let .formula(f): f.delimitedSource
            case let .labeledUnderline(_, children): extractPlainText(children)
            case let .blank(ordinal): "(\(ordinal)) ____"
            case let .image(img): img.alt
            case let .slot(id): "[[\(id)]]"
            }
        }.joined()
    }
}

struct RichTextKitParagraphRepresentable: UIViewRepresentable {
    let inlines: [RichInline]
    let baseSize: CGFloat
    let textStyle: UIFont.TextStyle
    let textColor: UIColor
    let alignment: NSTextAlignment
    let lineSpacing: CGFloat
    let tabularDigits: Bool
    let blankNumberStart: Int
    let tokens: RichContentTokens
    let formulaEngine: any FormulaEngine
    let slots: [String: () -> AnyView]
    let availableWidth: CGFloat

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    final class Coordinator {
        @MainActor let slotPool = RichSlotHostPool()
        var lastSignature: String = ""
    }

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIView(context: Context) -> RichTextKitTextView {
        RichTextKitTextView()
    }

    func updateUIView(_ uiView: RichTextKitTextView, context: Context) {
        context.coordinator.slotPool.updateFactories(slots)
        let width = max(availableWidth, 60)
        let traits = uiView.traitCollection

        let built = RichInlineBuilder.buildAttributedString(
            from: inlines,
            baseSize: baseSize,
            textStyle: textStyle,
            textColor: textColor,
            alignment: alignment,
            lineSpacing: lineSpacing,
            tabularDigits: tabularDigits,
            blankNumberStart: blankNumberStart,
            tokens: tokens,
            traitCollection: traits,
            formulaEngine: formulaEngine,
            slotHostPool: context.coordinator.slotPool,
            maxAttachmentWidth: width
        )

        uiView.hasInteractiveSlots = built.hasSlots
        uiView.isUserInteractionEnabled = built.hasSlots
        uiView.customAccessibilityDescription = built.accessibilityText
        uiView.attributedText = built.attributed
        uiView.invalidateIntrinsicContentSize()
    }

    func sizeThatFits(
        _ proposal: ProposedViewSize,
        uiView: RichTextKitTextView,
        context: Context
    ) -> CGSize? {
        let targetWidth = proposal.width ?? max(availableWidth, 60)
        let clampedWidth = max(targetWidth, 1)
        let measured = uiView.sizeThatFits(CGSize(width: clampedWidth, height: .greatestFiniteMagnitude))
        return CGSize(width: clampedWidth, height: max(ceil(measured.height), 1))
    }
}
