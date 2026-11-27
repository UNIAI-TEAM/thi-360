import Foundation

enum QuestionStrings {
    static let table = "Question"

    static func text(_ key: String, locale: AppLocale, replacements: [String: String] = [:]) -> String {
        AppStrings.text(key, table: table, locale: locale, replacements: replacements)
    }

    static func questionTitle(for item: QuestionItem, locale: AppLocale) -> String {
        let range = item.displayRange
        if case let .cloze(_, _, _, _, _, blanks, _) = item, blanks.count > 1 {
            return questionRangeLabel(from: range.from, to: range.to, locale: locale)
        }
        return questionLabel(no: item.displayNo, locale: locale)
    }

    static func questionLabel(no: Int, locale: AppLocale) -> String {
        text("question.questionLabel", locale: locale, replacements: ["no": OptionOrder.formatInteger(no, locale: locale)])
    }

    static func questionRangeLabel(from: Int, to: Int, locale: AppLocale) -> String {
        text(
            "question.questionRangeLabel",
            locale: locale,
            replacements: [
                "from": OptionOrder.formatInteger(from, locale: locale),
                "to": OptionOrder.formatInteger(to, locale: locale)
            ]
        )
    }

    static func typeChipLabel(for item: QuestionItem, locale: AppLocale) -> String {
        let base = text("question.typeLabels.\(item.questionType.rawValue)", locale: locale)
        if case let .cloze(_, _, _, _, _, blanks, _) = item {
            return "\(base) · \(OptionOrder.formatInteger(blanks.count, locale: locale))"
        }
        return base
    }

    static func outcomeLabel(_ outcome: QuestionOutcome, locale: AppLocale) -> String {
        text("question.outcomes.\(outcome.rawValue)", locale: locale)
    }

    static func pointsText(earned: Double, max: Double, locale: AppLocale) -> String {
        text(
            "question.points",
            locale: locale,
            replacements: [
                "earned": OptionOrder.formatScore(earned, locale: locale),
                "max": OptionOrder.formatScore(max, locale: locale)
            ]
        )
    }

    static func noPointsYetText(max: Double, locale: AppLocale) -> String {
        let prefix = text("question.noPointsYet", locale: locale)
        let suffix = text("question.maxPoints", locale: locale, replacements: ["max": OptionOrder.formatScore(max, locale: locale)])
        return "\(prefix) · \(suffix)"
    }

    static func clozeFilled(filled: Int, total: Int, locale: AppLocale) -> String {
        text(
            "question.clozeFilled",
            locale: locale,
            replacements: [
                "filled": OptionOrder.formatInteger(filled, locale: locale),
                "total": OptionOrder.formatInteger(total, locale: locale)
            ]
        )
    }

    static func clozeBlankAria(questionNo: Int, ordinal: Int, locale: AppLocale) -> String {
        text(
            "question.clozeBlankAria",
            locale: locale,
            replacements: [
                "questionNo": OptionOrder.formatInteger(questionNo, locale: locale),
                "ordinal": OptionOrder.formatInteger(ordinal, locale: locale)
            ]
        )
    }

    static func clozePlaceholder(ordinal: Int, locale: AppLocale) -> String {
        text("question.clozePlaceholder", locale: locale, replacements: ["ordinal": OptionOrder.formatInteger(ordinal, locale: locale)])
    }

    static func matchClear(leftNo: Int, locale: AppLocale) -> String {
        text("question.matchClear", locale: locale, replacements: ["leftNo": OptionOrder.formatInteger(leftNo, locale: locale)])
    }

    static func matchDone(done: Int, total: Int, locale: AppLocale) -> String {
        text(
            "question.matchDone",
            locale: locale,
            replacements: [
                "done": OptionOrder.formatInteger(done, locale: locale),
                "total": OptionOrder.formatInteger(total, locale: locale)
            ]
        )
    }

    static func matchPickLeft(leftNo: Int, locale: AppLocale) -> String {
        text("question.matchPickLeft", locale: locale, replacements: ["leftNo": OptionOrder.formatInteger(leftNo, locale: locale)])
    }

    static func essayWordCount(count: Int, min: Int?, locale: AppLocale) -> String {
        let countStr = OptionOrder.formatInteger(count, locale: locale)
        guard let min else {
            return text("question.essayWordCount", locale: locale, replacements: ["count": countStr])
        }
        return text(
            "question.essayWordCountMin",
            locale: locale,
            replacements: [
                "count": countStr,
                "min": OptionOrder.formatInteger(min, locale: locale)
            ]
        )
    }

    static func wordsNeeded(count: Int, locale: AppLocale) -> String {
        text("question.wordsNeeded", locale: locale, replacements: ["count": OptionOrder.formatInteger(max(0, count), locale: locale)])
    }

    static func setPassageCaption(from: Int, to: Int, locale: AppLocale) -> String {
        text(
            "question.setPassageCaption",
            locale: locale,
            replacements: [
                "from": OptionOrder.formatInteger(from, locale: locale),
                "to": OptionOrder.formatInteger(to, locale: locale)
            ]
        )
    }

    static func countTableSummary(correct: Int, total: Int, earned: Double, locale: AppLocale) -> String {
        text(
            "question.countTableSummary",
            locale: locale,
            replacements: [
                "correct": OptionOrder.formatInteger(correct, locale: locale),
                "total": OptionOrder.formatInteger(total, locale: locale),
                "earned": OptionOrder.formatScore(earned, locale: locale)
            ]
        )
    }
}
