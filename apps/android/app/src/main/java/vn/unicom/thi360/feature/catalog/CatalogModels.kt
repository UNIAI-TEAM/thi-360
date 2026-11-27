package vn.unicom.thi360.feature.catalog

import java.math.BigDecimal
import vn.unicom.thi360.core.network.generated.model.AnswerTypeGroup
import vn.unicom.thi360.core.network.generated.model.BundleAccessExamItem
import vn.unicom.thi360.core.network.generated.model.BundleDetailItem
import vn.unicom.thi360.core.network.generated.model.CatalogKind
import vn.unicom.thi360.core.network.generated.model.DecimalRange
import vn.unicom.thi360.core.network.generated.model.DetailBenefits
import vn.unicom.thi360.core.network.generated.model.DetailChoiceGroup
import vn.unicom.thi360.core.network.generated.model.DetailChoiceOption
import vn.unicom.thi360.core.network.generated.model.DetailPart
import vn.unicom.thi360.core.network.generated.model.DetailScoring
import vn.unicom.thi360.core.network.generated.model.ExamAccessItem
import vn.unicom.thi360.core.network.generated.model.ExamKindRef
import vn.unicom.thi360.core.network.generated.model.FormatFilter
import vn.unicom.thi360.core.network.generated.model.IntRange as GeneratedIntRange
import vn.unicom.thi360.core.network.generated.model.KindFilter
import vn.unicom.thi360.core.network.generated.model.LocalizedName
import vn.unicom.thi360.core.network.generated.model.OmittedEssayGroup
import vn.unicom.thi360.core.network.generated.model.PageResponseProductCard
import vn.unicom.thi360.core.network.generated.model.PartFilter
import vn.unicom.thi360.core.network.generated.model.ProductCard
import vn.unicom.thi360.core.network.generated.model.ProductDetail
import vn.unicom.thi360.core.network.generated.model.PromotionBadge
import vn.unicom.thi360.core.network.generated.model.StorefrontFilters
import vn.unicom.thi360.core.network.generated.model.SubjectAnswerTypes
import vn.unicom.thi360.core.network.generated.model.SubjectFilter
import vn.unicom.thi360.core.network.generated.model.SubjectRef

data class IntRangeValue(
    val min: Int,
    val max: Int,
)

data class DecimalRangeValue(
    val min: BigDecimal,
    val max: BigDecimal,
)

data class LocalizedNameData(
    val nameVi: String,
    val nameEn: String,
)

data class ExamKindData(
    val code: String,
    val nameVi: String,
    val nameEn: String,
)

data class PromotionBadgeData(
    val name: String,
    val endsAt: String,
)

data class KindFilterItem(
    val code: String,
    val nameVi: String,
    val nameEn: String,
    val count: Long? = null,
)

data class FormatFilterItem(
    val code: String,
    val count: Long,
)

data class PartFilterItem(
    val kindCode: String,
    val code: String,
    val name: String,
    val count: Long,
)

data class SubjectFilterItem(
    val code: String,
    val nameVi: String,
    val nameEn: String,
    val count: Long,
)

data class StorefrontFiltersData(
    val catalogKinds: List<KindFilterItem>,
    val kinds: List<KindFilterItem>,
    val formats: List<FormatFilterItem>,
    val parts: List<PartFilterItem>,
    val subjects: List<SubjectFilterItem>,
)

data class ProductCardItem(
    val code: String,
    val name: String,
    val summary: String?,
    val coverUrl: String?,
    val examKind: ExamKindData,
    val format: String,
    val productType: String?,
    val buildMode: String,
    val scopeLabel: LocalizedNameData?,
    val questionCount: IntRangeValue?,
    val durationMinutes: IntRangeValue?,
    val attemptsGranted: Int,
    val listPrice: Long,
    val price: Long,
    val promotion: PromotionBadgeData?,
    val versionNo: Int,
) {
    val isBundle: Boolean
        get() = productType == PRODUCT_TYPE_BUNDLE || format == FORMAT_BUNDLE

    val isReview: Boolean
        get() = buildMode == BUILD_MODE_REVIEW
}

data class ProductPageData(
    val items: List<ProductCardItem>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
)

data class AnswerTypeGroupData(
    val questionType: String,
    val childAnswerType: String?,
    val questionCount: Int,
)

data class DetailPartData(
    val order: Int,
    val name: String,
    val required: Boolean,
    val questionCount: IntRangeValue?,
    val durationMinutes: IntRangeValue?,
    val maxPoints: DecimalRangeValue?,
    val answerTypes: List<AnswerTypeGroupData>,
)

data class SubjectRefData(
    val code: String,
    val nameVi: String,
    val nameEn: String,
)

data class DetailSubjectAnswerTypesData(
    val subject: SubjectRefData,
    val answerTypes: List<AnswerTypeGroupData>,
)

data class DetailChoiceOptionData(
    val code: String,
    val kind: String,
    val name: String,
    val branch: String?,
    val chooseSubjects: Int?,
    val countsByPick: List<Int>,
    val subjects: List<SubjectRefData>,
    val answerTypes: List<AnswerTypeGroupData>,
    val subjectAnswerTypes: List<DetailSubjectAnswerTypesData>,
)

data class DetailChoiceGroupData(
    val partName: String,
    val options: List<DetailChoiceOptionData>,
)

data class DetailScoringData(
    val maxScore: BigDecimal?,
    val practiceScoring: String?,
    val referenceMax: BigDecimal?,
    val timing: String?,
)

data class OmittedEssayGroupData(
    val name: String,
    val points: BigDecimal?,
)

data class DetailBenefitsData(
    val aiExplanation: Boolean,
    val practice: Boolean,
    val strengthWeakness: Boolean,
    val note: String?,
)

data class BundleDetailItemData(
    val examId: String,
    val code: String,
    val name: String,
    val buildMode: String,
    val scope: String,
    val partCode: String?,
    val subjectName: LocalizedNameData?,
    val questionCount: IntRangeValue?,
    val durationMinutes: IntRangeValue?,
    val maxPoints: DecimalRangeValue?,
    val attemptsGranted: Int,
    val resultView: String?,
    val benefits: DetailBenefitsData,
    val versionNo: Int?,
) {
    val isReview: Boolean
        get() = buildMode == BUILD_MODE_REVIEW
}

data class ProductDetailData(
    val code: String,
    val name: String,
    val summary: String?,
    val description: String?,
    val objectives: String?,
    val audience: String?,
    val coverUrl: String?,
    val examKind: ExamKindData,
    val format: String,
    val productType: String?,
    val buildMode: String,
    val scopeLabel: LocalizedNameData?,
    val questionCount: IntRangeValue?,
    val durationMinutes: IntRangeValue?,
    val maxPoints: DecimalRangeValue?,
    val attemptsGranted: Int,
    val resultView: String?,
    val listPrice: Long,
    val price: Long,
    val promotion: PromotionBadgeData?,
    val versionNo: Int,
    val parts: List<DetailPartData>,
    val choices: List<DetailChoiceGroupData>,
    val scoring: DetailScoringData,
    val omittedEssayGroups: List<OmittedEssayGroupData>,
    val benefits: DetailBenefitsData,
    val items: List<BundleDetailItemData>,
) {
    val isBundle: Boolean
        get() = productType == PRODUCT_TYPE_BUNDLE || format == FORMAT_BUNDLE

    val isReview: Boolean
        get() = buildMode == BUILD_MODE_REVIEW
}

sealed interface ProductOutcome {
    data class Exam(val detail: ProductDetailData) : ProductOutcome
    data class Bundle(val detail: ProductDetailData, val sellable: Boolean) : ProductOutcome
    data class Gone(val code: String, val name: String?) : ProductOutcome
    data object NotFound : ProductOutcome
    data object BundleUnavailable : ProductOutcome
}

sealed interface TailState {
    data object Idle : TailState
    data object Loading : TailState
    data object End : TailState
    data class Error(val retryAfterSeconds: Long? = null) : TailState
}

data class CatalogPage(
    val items: List<ProductCardItem>,
    val total: Long,
    val nextPage: Int,
    val tail: TailState,
)

internal const val PRODUCT_TYPE_BUNDLE = "BUNDLE"
internal const val FORMAT_BUNDLE = "BUNDLE"
internal const val BUILD_MODE_REVIEW = "REVIEW"

internal fun StorefrontFilters.toDomain(): StorefrontFiltersData = StorefrontFiltersData(
    catalogKinds = catalogKinds.orEmpty().map { it.toDomain() },
    kinds = kinds.map { it.toDomain() },
    formats = formats.map { it.toDomain() },
    parts = parts.map { it.toDomain() },
    subjects = subjects.map { it.toDomain() },
)

private fun CatalogKind.toDomain(): KindFilterItem = KindFilterItem(
    code = code,
    nameVi = nameVi,
    nameEn = nameEn,
    count = null,
)

private fun KindFilter.toDomain(): KindFilterItem = KindFilterItem(
    code = code,
    nameVi = nameVi,
    nameEn = nameEn,
    count = count,
)

private fun FormatFilter.toDomain(): FormatFilterItem = FormatFilterItem(
    code = code,
    count = count,
)

private fun PartFilter.toDomain(): PartFilterItem = PartFilterItem(
    kindCode = kindCode,
    code = code,
    name = name,
    count = count,
)

private fun SubjectFilter.toDomain(): SubjectFilterItem = SubjectFilterItem(
    code = code,
    nameVi = nameVi,
    nameEn = nameEn,
    count = count,
)

private fun GeneratedIntRange.toDomain(): IntRangeValue = IntRangeValue(min = min, max = max)

private fun DecimalRange.toDomain(): DecimalRangeValue = DecimalRangeValue(min = min, max = max)

private fun LocalizedName.toDomain(): LocalizedNameData = LocalizedNameData(nameVi = nameVi, nameEn = nameEn)

private fun ExamKindRef.toDomain(): ExamKindData = ExamKindData(code = code, nameVi = nameVi, nameEn = nameEn)

private fun SubjectRef.toDomain(): SubjectRefData = SubjectRefData(code = code, nameVi = nameVi, nameEn = nameEn)

private fun PromotionBadge.toDomain(): PromotionBadgeData = PromotionBadgeData(
    name = name,
    endsAt = endsAt.toInstant().toString(),
)

internal fun ProductCard.toDomain(): ProductCardItem = ProductCardItem(
    code = code,
    name = name,
    summary = summary?.takeIf { it.isNotBlank() },
    coverUrl = coverUrl?.takeIf { it.isNotBlank() },
    examKind = examKind.toDomain(),
    format = format,
    productType = productType,
    buildMode = buildMode,
    scopeLabel = scopeLabel?.toDomain(),
    questionCount = questionCount?.toDomain(),
    durationMinutes = durationMinutes?.toDomain(),
    attemptsGranted = attemptsGranted,
    listPrice = listPrice,
    price = price,
    promotion = promotion?.toDomain(),
    versionNo = versionNo,
)

internal fun PageResponseProductCard.toDomain(): ProductPageData = ProductPageData(
    items = items.map { it.toDomain() },
    page = page,
    size = propertySize,
    totalItems = totalItems,
    totalPages = totalPages,
)

private fun AnswerTypeGroup.toDomain(): AnswerTypeGroupData = AnswerTypeGroupData(
    questionType = questionType,
    childAnswerType = childAnswerType?.takeIf { it.isNotBlank() },
    questionCount = questionCount,
)

private fun DetailPart.toDomain(): DetailPartData = DetailPartData(
    order = order,
    name = name,
    required = required,
    questionCount = questionCount?.toDomain(),
    durationMinutes = durationMinutes?.toDomain(),
    maxPoints = maxPoints?.toDomain(),
    answerTypes = answerTypes.map { it.toDomain() },
)

private fun SubjectAnswerTypes.toDomain(): DetailSubjectAnswerTypesData = DetailSubjectAnswerTypesData(
    subject = subject.toDomain(),
    answerTypes = answerTypes.map { it.toDomain() },
)

private fun DetailChoiceOption.toDomain(): DetailChoiceOptionData = DetailChoiceOptionData(
    code = code,
    kind = kind,
    name = name,
    branch = branch?.takeIf { it.isNotBlank() },
    chooseSubjects = chooseSubjects,
    countsByPick = countsByPick.orEmpty(),
    subjects = subjects.map { it.toDomain() },
    answerTypes = answerTypes.map { it.toDomain() },
    subjectAnswerTypes = subjectAnswerTypes.map { it.toDomain() },
)

private fun DetailChoiceGroup.toDomain(): DetailChoiceGroupData = DetailChoiceGroupData(
    partName = partName,
    options = options.map { it.toDomain() },
)

private fun DetailScoring.toDomain(): DetailScoringData = DetailScoringData(
    maxScore = maxScore,
    practiceScoring = practiceScoring?.takeIf { it.isNotBlank() },
    referenceMax = referenceMax,
    timing = timing?.takeIf { it.isNotBlank() },
)

private fun OmittedEssayGroup.toDomain(): OmittedEssayGroupData = OmittedEssayGroupData(
    name = name,
    points = points,
)

private fun DetailBenefits.toDomain(): DetailBenefitsData = DetailBenefitsData(
    aiExplanation = aiExplanation,
    practice = practice,
    strengthWeakness = strengthWeakness,
    note = note?.takeIf { it.isNotBlank() },
)

private fun BundleDetailItem.toDomain(): BundleDetailItemData = BundleDetailItemData(
    examId = examId.toString(),
    code = code,
    name = name,
    buildMode = buildMode,
    scope = scope,
    partCode = partCode?.takeIf { it.isNotBlank() },
    subjectName = subjectName?.toDomain(),
    questionCount = questionCount?.toDomain(),
    durationMinutes = durationMinutes?.toDomain(),
    maxPoints = maxPoints?.toDomain(),
    attemptsGranted = attemptsGranted,
    resultView = resultView?.takeIf { it.isNotBlank() },
    benefits = benefits.toDomain(),
    versionNo = versionNo,
)

internal fun ProductDetail.toDomain(): ProductDetailData = ProductDetailData(
    code = code,
    name = name,
    summary = summary?.takeIf { it.isNotBlank() },
    description = description?.takeIf { it.isNotBlank() },
    objectives = objectives?.takeIf { it.isNotBlank() },
    audience = audience?.takeIf { it.isNotBlank() },
    coverUrl = coverUrl?.takeIf { it.isNotBlank() },
    examKind = examKind.toDomain(),
    format = format,
    productType = productType,
    buildMode = buildMode,
    scopeLabel = scopeLabel?.toDomain(),
    questionCount = questionCount?.toDomain(),
    durationMinutes = durationMinutes?.toDomain(),
    maxPoints = maxPoints?.toDomain(),
    attemptsGranted = attemptsGranted,
    resultView = resultView?.takeIf { it.isNotBlank() },
    listPrice = listPrice,
    price = price,
    promotion = promotion?.toDomain(),
    versionNo = versionNo,
    parts = parts.map { it.toDomain() },
    choices = choices.map { it.toDomain() },
    scoring = scoring.toDomain(),
    omittedEssayGroups = omittedEssayGroups.map { it.toDomain() },
    benefits = benefits.toDomain(),
    items = items.orEmpty().map { it.toDomain() },
)

internal fun BundleAccessExamItem.toDomain(): BundleExamAccessRow = BundleExamAccessRow(
    examId = examId.toString(),
    code = code,
    name = name,
    state = ExamAccessStateValue.fromApi(state.value),
    attemptsLeft = attemptsLeft,
    entitlementId = entitlementId?.toString(),
    activeAttemptId = activeAttemptId?.toString(),
    hasPendingFulfillment = hasPendingFulfillment,
    pendingOrderCode = pendingOrderCode?.takeIf { it.isNotBlank() },
)

internal fun ExamAccessItem.toDomain(): ExamAccessRow = ExamAccessRow(
    code = code,
    state = ExamAccessStateValue.fromApi(state.value),
    attemptsLeft = attemptsLeft,
    entitlementId = entitlementId?.toString(),
    activeAttemptId = activeAttemptId?.toString(),
    hasPendingFulfillment = hasPendingFulfillment,
    pendingOrderCode = pendingOrderCode?.takeIf { it.isNotBlank() },
    items = items.orEmpty().map { it.toDomain() },
)
