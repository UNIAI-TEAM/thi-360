import Foundation

enum SymbolGroup: String, Equatable, Sendable {
    case sign
    case charge
    case `subscript`
    case arrow
    case numeric
}

struct SymbolKey: Identifiable, Equatable, Sendable {
    let id: String
    let label: String
    let insert: String
    let group: SymbolGroup
    let accessibilityKey: String
}

enum SymbolInputRules {
    static let defaultIonSymbols: [SymbolKey] = [
        SymbolKey(id: "plus", label: "+", insert: "+", group: .sign, accessibilityKey: "question.symbolInput.symbolNames.plus"),
        SymbolKey(id: "minus", label: "\u{2212}", insert: "-", group: .sign, accessibilityKey: "question.symbolInput.symbolNames.minus"),
        SymbolKey(id: "lparen", label: "(", insert: "(", group: .sign, accessibilityKey: "question.symbolInput.symbolNames.lparen"),
        SymbolKey(id: "rparen", label: ")", insert: ")", group: .sign, accessibilityKey: "question.symbolInput.symbolNames.rparen"),
        SymbolKey(id: "charge-2plus", label: "\u{00B2}\u{207A}", insert: "2+", group: .charge, accessibilityKey: "question.symbolInput.symbolNames.charge-2plus"),
        SymbolKey(id: "charge-3plus", label: "\u{00B3}\u{207A}", insert: "3+", group: .charge, accessibilityKey: "question.symbolInput.symbolNames.charge-3plus"),
        SymbolKey(id: "charge-2minus", label: "\u{00B2}\u{207B}", insert: "2-", group: .charge, accessibilityKey: "question.symbolInput.symbolNames.charge-2minus"),
        SymbolKey(id: "charge-3minus", label: "\u{00B3}\u{207B}", insert: "3-", group: .charge, accessibilityKey: "question.symbolInput.symbolNames.charge-3minus"),
        SymbolKey(id: "sub-2", label: "\u{2082}", insert: "2", group: .subscript, accessibilityKey: "question.symbolInput.symbolNames.sub-2"),
        SymbolKey(id: "sub-3", label: "\u{2083}", insert: "3", group: .subscript, accessibilityKey: "question.symbolInput.symbolNames.sub-3"),
        SymbolKey(id: "sub-4", label: "\u{2084}", insert: "4", group: .subscript, accessibilityKey: "question.symbolInput.symbolNames.sub-4"),
        SymbolKey(id: "arrow-right", label: "\u{2192}", insert: "->", group: .arrow, accessibilityKey: "question.symbolInput.symbolNames.arrow-right")
    ]

    static let numericHelperKeys: [SymbolKey] = [
        SymbolKey(id: "num-minus", label: "-", insert: "-", group: .numeric, accessibilityKey: "question.symbolInput.symbolNames.minus"),
        SymbolKey(id: "num-slash", label: "/", insert: "/", group: .numeric, accessibilityKey: "question.numericInput.slash"),
        SymbolKey(id: "num-comma", label: ",", insert: ",", group: .numeric, accessibilityKey: "question.numericInput.comma"),
        SymbolKey(id: "num-dot", label: ".", insert: ".", group: .numeric, accessibilityKey: "question.numericInput.dot")
    ]

    private static let symbolHintRegex = try! NSRegularExpression(
        pattern: #"(?:\bion\b|cong thuc|ky hieu|ki hieu|formula|equation|symbol)"#,
        options: [.caseInsensitive]
    )

    static func foldDiacritics(_ raw: String) -> String {
        let decomposed = raw.decomposedStringWithCanonicalMapping
        var scalars = String.UnicodeScalarView()
        scalars.reserveCapacity(decomposed.unicodeScalars.count)
        for scalar in decomposed.unicodeScalars {
            if scalar.value >= 0x0300 && scalar.value <= 0x036F {
                continue
            }
            if scalar.value == 0x0111 || scalar.value == 0x0110 {
                scalars.append("d")
            } else {
                scalars.append(scalar)
            }
        }
        return String(scalars)
    }

    static func shouldShowSymbolInput(answerFormat: AnswerFormat?, inputHint: String?) -> Bool {
        if let format = answerFormat {
            switch format {
            case .chemFormula, .chemEquation, .text:
                return true
            case .integer, .decimal, .fraction:
                return false
            }
        }
        guard let inputHint, !inputHint.isEmpty else {
            return false
        }
        let folded = foldDiacritics(inputHint)
        let nsRange = NSRange(location: 0, length: (folded as NSString).length)
        return symbolHintRegex.firstMatch(in: folded, range: nsRange) != nil
    }

    static func insertTextAtCursor(
        currentValue: String,
        selectionRange: NSRange?,
        insertion: String
    ) -> (nextValue: String, nextCursorUTF16: Int) {
        let nsCurrent = currentValue as NSString
        let length = nsCurrent.length
        let rawStart = selectionRange?.location ?? length
        let rawLength = selectionRange?.length ?? 0
        let start = max(0, min(rawStart, length))
        let end = max(start, min(start + max(0, rawLength), length))
        let clamped = NSRange(location: start, length: end - start)
        let nextValue = nsCurrent.replacingCharacters(in: clamped, with: insertion)
        let nextCursor = start + (insertion as NSString).length
        return (nextValue, nextCursor)
    }
}
