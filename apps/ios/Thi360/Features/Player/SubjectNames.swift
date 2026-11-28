import Foundation

enum SubjectNames {
    static let knownCodes: Set<String> = [
        "ANH",
        "CNCN",
        "CNNN",
        "DIA",
        "ECON_LAW",
        "GDKTPL",
        "HOA",
        "INFORMATICS",
        "LY",
        "SCIENTIFIC_THINKING",
        "SINH",
        "SU",
        "TECH_AGRICULTURE",
        "TECH_INDUSTRY",
        "TIENG_ANH",
        "TIN",
        "TOAN",
        "VAN",
        "VIETNAMESE"
    ]

    static func resolve(
        code: String,
        prepareSubjects: [String: Components.Schemas.ChoiceSubjectView] = [:],
        locale: AppLocale,
        bundle: Bundle = .main
    ) -> String {
        let trimmed = code.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return "" }

        let upper = trimmed.uppercased()
        if knownCodes.contains(upper) {
            return PlayerStrings.text("attempt.subjects.codes.\(upper)", locale: locale, bundle: bundle)
        }

        if let meta = prepareSubjects[trimmed] ?? prepareSubjects[upper] {
            let localized: String?
            switch locale {
            case .en:
                localized = nonEmpty(meta.nameEn) ?? nonEmpty(meta.nameVi)
            case .vi:
                localized = nonEmpty(meta.nameVi) ?? nonEmpty(meta.nameEn)
            }
            if let localized {
                return localized
            }
        }

        return trimmed
    }

    private static func nonEmpty(_ value: String?) -> String? {
        guard let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines), !trimmed.isEmpty else {
            return nil
        }
        return trimmed
    }
}
