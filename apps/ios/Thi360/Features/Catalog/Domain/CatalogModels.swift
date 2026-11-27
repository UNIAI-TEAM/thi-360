import Foundation

enum CatalogFormat: String, CaseIterable, Hashable, Sendable {
    case full = "FULL"
    case part = "PART"
    case subject = "SUBJECT"
    case bundle = "BUNDLE"
}

enum CatalogSort: String, CaseIterable, Hashable, Sendable {
    case newest = "NEWEST"
    case priceAsc = "PRICE_ASC"
    case priceDesc = "PRICE_DESC"

    var localizationKey: String {
        "storefront.catalog.sort.\(rawValue)"
    }
}

struct CatalogQuery: Equatable, Hashable, Sendable {
    static let pageSize = 12
    static let empty = CatalogQuery()

    struct ProductsRequestParams: Equatable, Hashable, Sendable {
        let q: String?
        let kind: String?
        let format: CatalogFormat?
        let subject: String?
        let part: String?
        let sort: CatalogSort
        let page: Int
        let size: Int
    }

    let q: String
    let kind: String?
    let format: CatalogFormat?
    let part: String?
    let subject: String?
    let sort: CatalogSort

    init(
        q: String = "",
        kind: String? = nil,
        format: CatalogFormat? = nil,
        part: String? = nil,
        subject: String? = nil,
        sort: CatalogSort = .newest
    ) {
        self.q = q
        let cleanKind = kind?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
        self.kind = cleanKind
        self.format = format
        self.part = cleanKind != nil ? part?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty : nil
        self.subject = subject?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
        self.sort = sort
    }

    var trimmedQ: String? {
        q.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
    }

    var isFiltered: Bool {
        trimmedQ != nil
            || kind != nil
            || format != nil
            || part != nil
            || subject != nil
            || sort != .newest
    }

    var activeSheetFilterCount: Int {
        var count = 0
        if format != nil { count += 1 }
        if part != nil || subject != nil { count += 1 }
        if sort != .newest { count += 1 }
        return count
    }

    func withSearch(_ value: String) -> CatalogQuery {
        CatalogQuery(q: value, kind: kind, format: format, part: part, subject: subject, sort: sort)
    }

    func withKind(_ newKind: String?) -> CatalogQuery {
        CatalogQuery(q: q, kind: newKind, format: format, part: nil, subject: subject, sort: sort)
    }

    func withFormat(_ newFormat: CatalogFormat?) -> CatalogQuery {
        CatalogQuery(q: q, kind: kind, format: newFormat, part: part, subject: subject, sort: sort)
    }

    func withPart(kind partKind: String, part partCode: String) -> CatalogQuery {
        CatalogQuery(q: q, kind: partKind, format: format, part: partCode, subject: nil, sort: sort)
    }

    func withSubject(_ newSubject: String?) -> CatalogQuery {
        CatalogQuery(q: q, kind: kind, format: format, part: nil, subject: newSubject, sort: sort)
    }

    func clearingPartAndSubject() -> CatalogQuery {
        CatalogQuery(q: q, kind: kind, format: format, part: nil, subject: nil, sort: sort)
    }

    func withSort(_ newSort: CatalogSort) -> CatalogQuery {
        CatalogQuery(q: q, kind: kind, format: format, part: part, subject: subject, sort: newSort)
    }

    func cleared() -> CatalogQuery {
        .empty
    }

    func params(page: Int) -> ProductsRequestParams {
        ProductsRequestParams(
            q: trimmedQ,
            kind: kind,
            format: format,
            subject: subject,
            part: kind != nil ? part : nil,
            sort: sort,
            page: max(0, page),
            size: Self.pageSize
        )
    }
}

struct LocalizedLabel: Equatable, Hashable, Sendable {
    let nameVi: String
    let nameEn: String

    func localized(for locale: AppLocale) -> String {
        switch locale {
        case .vi:
            nameVi.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
                ?? nameEn.trimmingCharacters(in: .whitespacesAndNewlines)
        case .en:
            nameEn.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
                ?? nameVi.trimmingCharacters(in: .whitespacesAndNewlines)
        }
    }
}

struct ExamKindInfo: Equatable, Hashable, Sendable {
    let code: String
    let name: LocalizedLabel

    func localizedNameOrCode(for locale: AppLocale) -> String {
        name.localized(for: locale).nonEmpty ?? code
    }
}

struct CatalogPartOption: Equatable, Hashable, Identifiable, Sendable {
    let kindCode: String
    let code: String
    let name: String
    let count: Int64

    var id: String { "\(kindCode):\(code)" }

    func displayLabel(selectedKind: String?) -> String {
        selectedKind != nil ? name : "\(kindCode) · \(name)"
    }
}

struct CatalogSubjectOption: Equatable, Hashable, Identifiable, Sendable {
    let code: String
    let name: LocalizedLabel
    let count: Int64

    var id: String { code }
}

struct CatalogFilters: Equatable, Hashable, Sendable {
    static let empty = CatalogFilters(
        catalogKinds: [],
        kinds: [],
        formats: [],
        parts: [],
        subjects: []
    )

    let catalogKinds: [ExamKindInfo]
    let kinds: [ExamKindInfo]
    let formats: [String]
    let parts: [CatalogPartOption]
    let subjects: [CatalogSubjectOption]

    var effectiveKinds: [ExamKindInfo] {
        !catalogKinds.isEmpty ? catalogKinds : kinds
    }

    var orderedFormats: [CatalogFormat] {
        let available = Set(formats)
        return CatalogFormat.allCases.filter { available.contains($0.rawValue) }
    }

    func visibleParts(forKind selectedKind: String?) -> [CatalogPartOption] {
        guard let selectedKind else { return parts }
        return parts.filter { $0.kindCode == selectedKind }
    }
}

struct IntRangeValue: Equatable, Hashable, Sendable {
    let min: Int
    let max: Int
}

struct DecimalRangeValue: Equatable, Hashable, Sendable {
    let min: Double
    let max: Double
}

struct PromotionInfo: Equatable, Hashable, Sendable {
    let name: String
    let endsAt: Date
}

struct CatalogProductCard: Equatable, Hashable, Identifiable, Sendable {
    let code: String
    let name: String
    let summary: String?
    let coverUrl: String?
    let examKind: ExamKindInfo
    let format: String
    let scopeLabel: LocalizedLabel?
    let buildMode: String
    let questionCount: IntRangeValue?
    let durationMinutes: IntRangeValue?
    let attemptsGranted: Int
    let listPrice: Int64
    let price: Int64
    let promotion: PromotionInfo?
    let versionNo: Int
    let productType: String?

    var id: String { code }

    var isBundle: Bool {
        productType == "BUNDLE" || format == "BUNDLE"
    }

    var isReview: Bool {
        buildMode == "REVIEW"
    }

    // Spec R-19 / AC-65: Bundles always show the default bundle cover and never request a remote cover image.
    var effectiveCoverURL: String? {
        guard !isBundle else { return nil }
        return coverUrl?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
    }
}

struct CatalogPageResponse: Equatable, Sendable {
    let items: [CatalogProductCard]
    let page: Int
    let size: Int
    let totalItems: Int
    let totalPages: Int
}

enum CatalogTailState: Equatable, Sendable {
    case idle
    case loading
    case failed(LoadFailureKind)
}

struct CatalogPage: Equatable, Sendable {
    let cards: [CatalogProductCard]
    let total: Int
    let nextPage: Int?
    let tail: CatalogTailState
}

enum ExamAccessState: String, Equatable, Hashable, Sendable {
    case inProgress = "IN_PROGRESS"
    case canStart = "CAN_START"
    case checking = "CHECKING"
    case exhausted = "EXHAUSTED"
    case none = "NONE"
}

struct BundleAccessItemRow: Equatable, Hashable, Identifiable, Sendable {
    let examId: String
    let code: String
    let name: String
    let state: ExamAccessState
    let attemptsLeft: Int
    let entitlementId: String?
    let activeAttemptId: String?
    let hasPendingFulfillment: Bool
    let pendingOrderCode: String?

    var id: String { examId.nonEmpty ?? code }
}

struct ExamAccessRow: Equatable, Hashable, Sendable {
    let code: String
    let state: ExamAccessState
    let attemptsLeft: Int
    let entitlementId: String?
    let activeAttemptId: String?
    let hasPendingFulfillment: Bool
    let pendingOrderCode: String?
    let items: [BundleAccessItemRow]?

    static func none(code: String) -> ExamAccessRow {
        ExamAccessRow(
            code: code,
            state: .none,
            attemptsLeft: 0,
            entitlementId: nil,
            activeAttemptId: nil,
            hasPendingFulfillment: false,
            pendingOrderCode: nil,
            items: nil
        )
    }
}

enum AccessCell: Equatable, Sendable {
    case loading
    case ready(ExamAccessRow)
    case failed
}

struct AnswerTypeGroupModel: Equatable, Hashable, Sendable {
    let questionType: String
    let questionCount: Int
    let childAnswerType: String?
}

struct SubjectAnswerTypesModel: Equatable, Hashable, Sendable {
    let subject: ExamKindInfo
    let answerTypes: [AnswerTypeGroupModel]
}

struct DetailChoiceOptionModel: Equatable, Hashable, Identifiable, Sendable {
    let code: String
    let kind: String
    let name: String
    let branch: String?
    let chooseSubjects: Int?
    let countsByPick: [Int]?
    let subjects: [ExamKindInfo]
    let answerTypes: [AnswerTypeGroupModel]
    let subjectAnswerTypes: [SubjectAnswerTypesModel]

    var id: String { code }
}

struct DetailChoiceGroupModel: Equatable, Hashable, Identifiable, Sendable {
    let partName: String
    let options: [DetailChoiceOptionModel]

    var id: String { partName }
}

struct DetailPartModel: Equatable, Hashable, Identifiable, Sendable {
    let order: Int
    let name: String
    let questionCount: IntRangeValue?
    let durationMinutes: IntRangeValue?
    let maxPoints: DecimalRangeValue?
    let required: Bool
    let answerTypes: [AnswerTypeGroupModel]

    var id: Int { order }
}

struct DetailScoringModel: Equatable, Hashable, Sendable {
    let timing: String?
    let maxScore: Double?
    let referenceMax: Double?
    let practiceScoring: String?
}

struct OmittedEssayGroupModel: Equatable, Hashable, Identifiable, Sendable {
    let name: String
    let points: Double?

    var id: String { name }
}

struct DetailBenefitsModel: Equatable, Hashable, Sendable {
    let aiExplanation: Bool
    let strengthWeakness: Bool
    let practice: Bool
    let note: String?
}

struct BundleDetailItemModel: Equatable, Hashable, Identifiable, Sendable {
    let examId: String
    let code: String
    let name: String
    let scope: String
    let partCode: String?
    let subjectName: LocalizedLabel?
    let buildMode: String
    let questionCount: IntRangeValue?
    let durationMinutes: IntRangeValue?
    let maxPoints: DecimalRangeValue?
    let attemptsGranted: Int
    let benefits: DetailBenefitsModel
    let resultView: String?
    let versionNo: Int?

    var id: String { examId.nonEmpty ?? code }
    var isReview: Bool { buildMode == "REVIEW" }
}

struct ProductDetailModel: Equatable, Hashable, Sendable {
    let code: String
    let name: String
    let summary: String?
    let coverUrl: String?
    let examKind: ExamKindInfo
    let format: String
    let scopeLabel: LocalizedLabel?
    let buildMode: String
    let questionCount: IntRangeValue?
    let durationMinutes: IntRangeValue?
    let maxPoints: DecimalRangeValue?
    let attemptsGranted: Int
    let listPrice: Int64
    let price: Int64
    let promotion: PromotionInfo?
    let versionNo: Int
    let description: String?
    let objectives: String?
    let audience: String?
    let parts: [DetailPartModel]
    let choices: [DetailChoiceGroupModel]
    let scoring: DetailScoringModel
    let omittedEssayGroups: [OmittedEssayGroupModel]
    let benefits: DetailBenefitsModel
    let resultView: String?
    let productType: String?
    let items: [BundleDetailItemModel]
    let saleState: String?

    var isBundle: Bool {
        productType == "BUNDLE" || format == "BUNDLE"
    }

    var isReview: Bool {
        buildMode == "REVIEW"
    }

    var effectiveCoverURL: String? {
        guard !isBundle else { return nil }
        return coverUrl?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
    }

    var hasEssay: Bool {
        let allGroups = parts.flatMap(\.answerTypes)
            + choices.flatMap(\.options).flatMap { option in
                option.answerTypes + option.subjectAnswerTypes.flatMap(\.answerTypes)
            }
        return allGroups.contains { ($0.childAnswerType ?? $0.questionType) == "ESSAY" }
    }

    var mergedFixedAnswerTypes: [(type: String, count: Int)] {
        CatalogFormatting.mergeAnswerTypes(parts.flatMap(\.answerTypes))
    }

    func resolvedMaxScoreText(locale: AppLocale) -> String? {
        if let maxScore = scoring.maxScore {
            return CatalogFormatting.formatDecimal(maxScore, locale: locale)
        }
        return CatalogFormatting.formatDecimalRange(maxPoints, locale: locale)
    }
}

enum ProductOutcome: Equatable, Sendable {
    case exam(ProductDetailModel)
    case bundle(ProductDetailModel, sellable: Bool)
    case gone(code: String, name: String)
    case notFound
    case bundleUnavailable
}

extension String {
    var nonEmpty: String? {
        isEmpty ? nil : self
    }
}
