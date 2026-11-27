import Foundation

enum OptionOrder {
    static func optionLetter(at index: Int) -> String {
        let code = 65 + (index % 26)
        return String(UnicodeScalar(code) ?? "A")
    }

    static func matchLetter(at index: Int) -> String {
        let code = 97 + (index % 26)
        return String(UnicodeScalar(code) ?? "a")
    }

    static func statementLetter(at index: Int) -> String {
        matchLetter(at: index)
    }

    static func order<T: Identifiable>(_ items: [T], by optionOrder: [String]?) -> [T] where T.ID == String {
        guard let optionOrder, !optionOrder.isEmpty else {
            return items
        }
        var position: [String: Int] = [:]
        for (idx, id) in optionOrder.enumerated() where position[id] == nil {
            position[id] = idx
        }
        let indexed = items.enumerated().map { (originalIndex: $0.offset, item: $0.element) }
        return indexed.sorted { lhs, rhs in
            let pA = position[lhs.item.id]
            let pB = position[rhs.item.id]
            switch (pA, pB) {
            case let (a?, b?):
                return a < b
            case (_?, nil):
                return true
            case (nil, _?):
                return false
            case (nil, nil):
                return lhs.originalIndex < rhs.originalIndex
            }
        }.map(\.item)
    }

    static func formatScore(_ value: Double, locale: AppLocale) -> String {
        let rounded = (value * 100).rounded(.toNearestOrAwayFromZero) / 100
        let normalized = rounded == -0.0 ? 0.0 : rounded
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.minimumFractionDigits = 0
        formatter.maximumFractionDigits = 2
        formatter.locale = locale == .vi ? Locale(identifier: "vi_VN") : Locale(identifier: "en_US")
        return formatter.string(from: NSNumber(value: normalized)) ?? String(normalized)
    }

    static func formatInteger(_ value: Int, locale: AppLocale) -> String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.maximumFractionDigits = 0
        formatter.locale = locale == .vi ? Locale(identifier: "vi_VN") : Locale(identifier: "en_US")
        return formatter.string(from: NSNumber(value: value)) ?? String(value)
    }
}
