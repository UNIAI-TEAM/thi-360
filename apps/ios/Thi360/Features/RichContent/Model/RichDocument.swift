import Foundation

enum RichContentMode: Equatable, Hashable, Sendable {
    case answering
    case locked
    case review
}

struct RichParseOptions: Equatable, Hashable, Sendable {
    var allowImages: Bool
    var blankNumberStart: Int
    var slotMarkers: Bool

    init(
        allowImages: Bool = true,
        blankNumberStart: Int = 1,
        slotMarkers: Bool = false
    ) {
        self.allowImages = allowImages
        self.blankNumberStart = blankNumberStart
        self.slotMarkers = slotMarkers
    }
}

enum AttemptAssetRef: Equatable, Hashable, Sendable {
    case attempt(assetId: String)
    case solution(assetId: String)
    case blocked
}

enum FormulaDisplay: String, Codable, Equatable, Hashable, Sendable {
    case inline
    case block
}

enum FormulaNotation: String, Codable, Equatable, Hashable, Sendable {
    case tex
    case ce
    case pu
}

struct Formula: Equatable, Hashable, Sendable {
    let source: String
    let display: FormulaDisplay
    let notation: FormulaNotation

    init(source: String, display: FormulaDisplay, notation: FormulaNotation? = nil) {
        self.source = source
        self.display = display
        self.notation = notation ?? Self.detectNotation(in: source)
    }

    var delimitedSource: String {
        switch display {
        case .inline:
            "\\(\(source)\\)"
        case .block:
            "\\[\(source)\\]"
        }
    }

    static func detectNotation(in latex: String) -> FormulaNotation {
        let scalars = Array(latex.unicodeScalars)
        var i = 0
        var hasPu = false
        while i + 2 < scalars.count {
            if scalars[i] == "\\" {
                let c1 = scalars[i + 1]
                let c2 = scalars[i + 2]
                let nextIsLetter = (i + 3 < scalars.count) && isAsciiLetter(scalars[i + 3])
                if !nextIsLetter {
                    if c1 == "c" && c2 == "e" {
                        return .ce
                    }
                    if c1 == "p" && c2 == "u" {
                        hasPu = true
                    }
                }
            }
            i += 1
        }
        return hasPu ? .pu : .tex
    }

    private static func isAsciiLetter(_ scalar: Unicode.Scalar) -> Bool {
        (scalar.value >= 65 && scalar.value <= 90) || (scalar.value >= 97 && scalar.value <= 122)
    }
}

struct RichTextStyle: OptionSet, Hashable, Sendable {
    let rawValue: UInt8

    static let bold = RichTextStyle(rawValue: 1 << 0)
    static let italic = RichTextStyle(rawValue: 1 << 1)
    static let underline = RichTextStyle(rawValue: 1 << 2)
    static let strikethrough = RichTextStyle(rawValue: 1 << 3)
    static let `subscript` = RichTextStyle(rawValue: 1 << 4)
    static let superscript = RichTextStyle(rawValue: 1 << 5)
}

struct RichImageData: Equatable, Hashable, Sendable {
    let ref: AttemptAssetRef
    let alt: String
    let width: Int?
    let height: Int?

    var aspectRatio: CGFloat? {
        guard let width, let height, width > 0, height > 0 else { return nil }
        return CGFloat(width) / CGFloat(height)
    }
}

indirect enum RichInline: Equatable, Hashable, Sendable {
    case text(String, style: RichTextStyle)
    case lineBreak
    case formula(Formula)
    case labeledUnderline(label: String, children: [RichInline])
    case blank(ordinal: Int)
    case image(RichImageData)
    case slot(id: String)
}

enum FigureImageWidth: String, Equatable, Hashable, Sendable {
    case small
    case medium
    case full

    var columnFraction: CGFloat {
        switch self {
        case .small: 0.4
        case .medium: 0.7
        case .full: 1.0
        }
    }
}

enum TableHeaderScope: String, Equatable, Hashable, Sendable {
    case col
    case row
}

struct RichTableCell: Equatable, Hashable, Sendable {
    let isHeader: Bool
    let scope: TableHeaderScope?
    let isNumeric: Bool
    let colspan: Int
    let rowspan: Int
    let blocks: [RichBlock]
}

struct RichTableRow: Equatable, Hashable, Sendable {
    let isHeaderRow: Bool
    let cells: [RichTableCell]
}

struct RichTable: Equatable, Hashable, Sendable {
    let rows: [RichTableRow]
}

struct RichListItem: Equatable, Hashable, Sendable {
    let blocks: [RichBlock]
}

enum QuoteKind: String, Equatable, Hashable, Sendable {
    case poem
    case prose
    case source
}

struct RichQuote: Equatable, Hashable, Sendable {
    let kind: QuoteKind
    let lineNumberStep: Int
    let paragraphs: [[RichInline]]
    let footer: [RichInline]?
    let source: [RichInline]?
}

indirect enum RichBlock: Equatable, Hashable, Sendable {
    case paragraph([RichInline])
    case formulaBlock(Formula)
    case list(ordered: Bool, items: [RichListItem])
    case table(RichTable)
    case dataTableFigure(caption: [RichInline]?, table: RichTable, notes: [[RichInline]], sources: [[RichInline]])
    case crossFigure(table: RichTable)
    case imageFigure(
        image: RichImageData,
        width: FigureImageWidth,
        zoomable: Bool,
        caption: [RichInline]?,
        sources: [[RichInline]]
    )
    case quote(RichQuote)
}

struct SanitizeViolation: Equatable, Hashable, Sendable {
    let tag: String
    let attribute: String?
    let value: String?

    init(tag: String, attribute: String? = nil, value: String? = nil) {
        self.tag = tag
        self.attribute = attribute
        self.value = value
    }
}

struct RichDocument: Equatable, Sendable {
    let blocks: [RichBlock]
    let violations: [SanitizeViolation]
    let blankNumberStart: Int

    init(
        blocks: [RichBlock],
        violations: [SanitizeViolation] = [],
        blankNumberStart: Int = 1
    ) {
        self.blocks = blocks
        self.violations = violations
        self.blankNumberStart = blankNumberStart
    }

    var isEmpty: Bool {
        blocks.isEmpty
    }
}
