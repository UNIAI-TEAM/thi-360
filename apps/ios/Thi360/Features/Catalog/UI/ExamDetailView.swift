import SwiftUI

struct ExamDetailView: View {
    let product: ProductDetailModel
    let accessCell: AccessCell
    let locale: AppLocale
    let supportContact: SupportContact
    var imageLoader: ImageLoader? = nil
    let onAccessButtonTap: (AccessButtonKind) -> Void
    let onReloadAccess: () -> Void

    @Environment(\.brandPalette) private var palette
    @Environment(\.statusPalette) private var statusPalette

    var renderedStructureTitle: String {
        CatalogStrings.text("storefront.detail.structureTitle", locale: locale)
    }

    var renderedPartRows: [(order: Int, name: String, nature: String, questions: String, duration: String, maxPoints: String)] {
        product.parts.map { part in
            let qText = CatalogFormatting.formatIntRange(part.questionCount, locale: locale) ?? "—"
            let dText: String = {
                guard let mins = CatalogFormatting.formatIntRange(part.durationMinutes, locale: locale) else { return "—" }
                return AppStrings.common("storefront.units.minutes", locale: locale, replacements: ["value": mins])
            }()
            let pText = CatalogFormatting.formatDecimalRange(part.maxPoints, locale: locale) ?? "—"
            return (
                order: part.order,
                name: part.name,
                nature: CatalogStrings.text(part.required ? "storefront.detail.required" : "storefront.detail.optional", locale: locale),
                questions: qText,
                duration: dText,
                maxPoints: pText
            )
        }
    }

    var renderedAnswerTypesSummary: String {
        CatalogFormatting.formatAnswerTypeSummary(product.parts.flatMap(\.answerTypes), locale: locale)
    }

    var renderedMaxScoreLine: String? {
        guard let maxScoreText = product.resolvedMaxScoreText(locale: locale) else { return nil }
        return CatalogStrings.text("storefront.detail.how.maxScore", locale: locale, replacements: ["score": maxScoreText])
    }

    var renderedEssayNote: String? {
        product.hasEssay ? CatalogStrings.text("storefront.detail.how.essayNote", locale: locale) : nil
    }

    var renderedResultTiming: String {
        CatalogStrings.text(
            product.hasEssay ? "storefront.detail.rights.afterEssay" : "storefront.detail.rights.afterGrading",
            locale: locale
        )
    }

    var renderedPromotionLine: String? {
        guard let promotion = product.promotion else { return nil }
        let endsText = CatalogStrings.text(
            "storefront.detail.promoEnds",
            locale: locale,
            replacements: ["date": CatalogFormatting.formatDayMonth(promotion.endsAt)]
        )
        return "\(promotion.name) · \(endsText)"
    }

    var renderedPricePanelBullets: [String] {
        var bullets = [
            CatalogStrings.text("storefront.detail.includesTax", locale: locale),
            CatalogStrings.plural("storefront.detail.attemptsPerPurchase", count: product.attemptsGranted, locale: locale)
        ]
        if let q = CatalogFormatting.formatIntRange(product.questionCount, locale: locale),
           let m = CatalogFormatting.formatIntRange(product.durationMinutes, locale: locale)
        {
            bullets.append(
                CatalogStrings.text("storefront.detail.perAttempt", locale: locale, replacements: ["questions": q, "minutes": m])
            )
        }
        return bullets
    }

    var renderedPriceView: ProductPriceView {
        ProductPriceView(
            listPrice: product.listPrice,
            price: product.price,
            promotion: product.promotion,
            locale: locale,
            isLarge: true
        )
    }

    var renderedAccessSection: AccessActionSectionView {
        AccessActionSectionView(
            code: product.code,
            versionNo: product.versionNo,
            price: Int(product.price),
            isBundle: false,
            isPricePanel: true,
            cell: accessCell,
            locale: locale,
            onButtonTap: onAccessButtonTap,
            onReloadAccess: onReloadAccess
        )
    }

    var renderedPolicyStrings: [String] {
        var lines = [
            CatalogStrings.text("storefront.detail.policy.title", locale: locale),
            CatalogStrings.text("storefront.detail.policy.priceNote", locale: locale),
            CatalogStrings.text(
                "storefront.detail.policy.refund",
                locale: locale,
                replacements: ["policy": CatalogStrings.text("storefront.detail.policy.refundPending", locale: locale)]
            )
        ]
        let channels = [supportContact.phone, supportContact.email].filter { !$0.isEmpty }.joined(separator: " · ")
        if channels.isEmpty {
            lines.append(supportContact.pendingMessage(locale: locale))
        } else {
            lines.append(CatalogStrings.text("storefront.detail.policy.contactLine", locale: locale, replacements: ["contact": channels]))
        }
        if !supportContact.hours.isEmpty {
            lines.append(CatalogStrings.text("storefront.detail.policy.hoursLine", locale: locale, replacements: ["hours": supportContact.hours]))
        }
        return lines
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            headerCard
            if !product.parts.isEmpty {
                structureSection
            }
            if !product.choices.isEmpty {
                choicesSection
            }
            howSection
            rightsSection
            CatalogPolicySectionView(locale: locale, supportContact: supportContact)
            pricePanelSection
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("productDetail.examView")
    }

    private var headerCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            ExamDetailCoverView(product: product, locale: locale, imageLoader: imageLoader)

            VStack(alignment: .leading, spacing: 12) {
                CatalogFlowLayout(horizontalSpacing: 6, verticalSpacing: 6) {
                    CatalogBadgeView(text: product.examKind.code, style: .primary)
                    CatalogBadgeView(
                        text: CatalogStrings.formatLabel(for: product.format, locale: locale),
                        style: .subtle
                    )
                    if let scope = product.scopeLabel?.localized(for: locale).nonEmpty {
                        CatalogBadgeView(text: scope, style: .subtle)
                    }
                    if product.isReview {
                        CatalogBadgeView(
                            text: AppStrings.common("storefront.review", locale: locale),
                            style: .info
                        )
                    }
                }

                Text("\(CatalogStrings.text("storefront.detail.codeLabel", locale: locale)) \(product.code)")
                    .font(.footnote)
                    .foregroundStyle(palette.textMutedColor)
                    .accessibilityIdentifier("productDetail.code")

                Text(product.name)
                    .font(.title2.weight(.bold))
                    .foregroundStyle(palette.textColor)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityAddTraits(.isHeader)
                    .accessibilityIdentifier("productDetail.title")

                if let summary = product.summary {
                    Text(summary)
                        .font(.body)
                        .foregroundStyle(palette.textSecondaryColor)
                        .fixedSize(horizontal: false, vertical: true)
                }

                if let description = product.description {
                    Text(description)
                        .font(.subheadline)
                        .foregroundStyle(palette.textSecondaryColor)
                        .fixedSize(horizontal: false, vertical: true)
                }

                if let objectives = product.objectives {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(CatalogStrings.text("storefront.detail.objectives", locale: locale))
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(palette.textColor)
                        Text(objectives)
                            .font(.subheadline)
                            .foregroundStyle(palette.textMutedColor)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }

                if let audience = product.audience {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(CatalogStrings.text("storefront.detail.audience", locale: locale))
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(palette.textColor)
                        Text(audience)
                            .font(.subheadline)
                            .foregroundStyle(palette.textMutedColor)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
            .padding(16)
        }
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(palette.bgSurfaceColor)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .strokeBorder(palette.borderColor, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    private var structureSection: some View {
        let totalQuestions = CatalogFormatting.formatIntRange(product.questionCount, locale: locale)
        let totalMinutes = CatalogFormatting.formatIntRange(product.durationMinutes, locale: locale)

        return VStack(alignment: .leading, spacing: 12) {
            CatalogFlowLayout(horizontalSpacing: 10, verticalSpacing: 6) {
                CatalogSectionHeading(
                    title: CatalogStrings.text("storefront.detail.structureTitle", locale: locale)
                )
                if let totalQuestions, let totalMinutes {
                    CatalogBadgeView(
                        text: CatalogStrings.text(
                            "storefront.detail.structureTotal",
                            locale: locale,
                            replacements: ["questions": totalQuestions, "minutes": totalMinutes]
                        ),
                        style: .subtle
                    )
                }
            }

            VStack(spacing: 10) {
                ForEach(product.parts) { part in
                    VStack(alignment: .leading, spacing: 8) {
                        HStack(alignment: .top, spacing: 10) {
                            Text(String(part.order))
                                .font(.footnote.weight(.bold))
                                .foregroundStyle(palette.primaryTextColor)
                                .frame(width: 28, height: 28)
                                .background(
                                    Circle()
                                        .fill(palette.primaryContainerColor)
                                )
                                .accessibilityLabel(
                                    "\(CatalogStrings.text("storefront.detail.columns.order", locale: locale)) \(part.order)"
                                )

                            Text(part.name)
                                .font(.headline)
                                .foregroundStyle(palette.textColor)
                                .fixedSize(horizontal: false, vertical: true)
                                .frame(maxWidth: .infinity, alignment: .leading)

                            CatalogBadgeView(
                                text: CatalogStrings.text(
                                    part.required ? "storefront.detail.required" : "storefront.detail.optional",
                                    locale: locale
                                ),
                                style: part.required ? .primary : .warning
                            )
                        }

                        let qText = CatalogFormatting.formatIntRange(part.questionCount, locale: locale) ?? "—"
                        let dText: String = {
                            guard let mins = CatalogFormatting.formatIntRange(part.durationMinutes, locale: locale) else {
                                return "—"
                            }
                            return AppStrings.common(
                                "storefront.units.minutes",
                                locale: locale,
                                replacements: ["value": mins]
                            )
                        }()
                        let pText = CatalogFormatting.formatDecimalRange(part.maxPoints, locale: locale) ?? "—"

                        CatalogFlowLayout(horizontalSpacing: 16, verticalSpacing: 8) {
                            partMetric(
                                label: CatalogStrings.text("storefront.detail.columns.questions", locale: locale),
                                value: qText
                            )
                            partMetric(
                                label: CatalogStrings.text("storefront.detail.columns.duration", locale: locale),
                                value: dText
                            )
                            partMetric(
                                label: CatalogStrings.text("storefront.detail.columns.maxPoints", locale: locale),
                                value: pText
                            )
                        }
                    }
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .fill(palette.bgSurfaceColor)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .strokeBorder(palette.borderColor, lineWidth: 1)
                    )
                }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("productDetail.structureSection")
    }

    private func partMetric(label: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .font(.caption)
                .foregroundStyle(palette.textMutedColor)
            Text(value)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(palette.textColor)
        }
    }

    private var choicesSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            CatalogSectionHeading(
                title: CatalogStrings.text("storefront.detail.choicesTitle", locale: locale)
            )

            ForEach(product.choices) { group in
                VStack(alignment: .leading, spacing: 10) {
                    Text(group.partName)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(palette.textSecondaryColor)

                    ForEach(group.options) { option in
                        VStack(alignment: .leading, spacing: 8) {
                            let headerLine: String = {
                                if let pick = option.chooseSubjects, pick > 0 {
                                    let pickText = CatalogStrings.text(
                                        "storefront.detail.chooseSubjects",
                                        locale: locale,
                                        replacements: [
                                            "count": String(pick),
                                            "total": String(option.subjects.count)
                                        ]
                                    )
                                    return "\(option.name) · \(pickText)"
                                }
                                return option.name
                            }()

                            Text(headerLine)
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(palette.textColor)
                                .fixedSize(horizontal: false, vertical: true)

                            if !option.subjects.isEmpty {
                                CatalogFlowLayout(horizontalSpacing: 6, verticalSpacing: 6) {
                                    ForEach(option.subjects, id: \.code) { subject in
                                        CatalogBadgeView(
                                            text: subject.localizedNameOrCode(for: locale),
                                            style: .subtle
                                        )
                                    }
                                }
                            }

                            let optionTypes = CatalogFormatting.formatAnswerTypeSummary(option.answerTypes, locale: locale)
                            if !optionTypes.isEmpty {
                                Text(optionTypes)
                                    .font(.footnote)
                                    .foregroundStyle(palette.textMutedColor)
                                    .fixedSize(horizontal: false, vertical: true)
                            }

                            ForEach(option.subjectAnswerTypes, id: \.subject.code) { entry in
                                let types = CatalogFormatting.formatAnswerTypeSummary(entry.answerTypes, locale: locale)
                                if !types.isEmpty {
                                    Text(
                                        CatalogStrings.text(
                                            "storefront.detail.subjectAnswerTypes",
                                            locale: locale,
                                            replacements: [
                                                "subject": entry.subject.localizedNameOrCode(for: locale),
                                                "types": types
                                            ]
                                        )
                                    )
                                    .font(.footnote)
                                    .foregroundStyle(palette.textMutedColor)
                                    .fixedSize(horizontal: false, vertical: true)
                                }
                            }

                            if let counts = option.countsByPick, !counts.isEmpty {
                                Text(
                                    CatalogStrings.text(
                                        "storefront.detail.countsByPick",
                                        locale: locale,
                                        replacements: ["counts": counts.map(String.init).joined(separator: " / ")]
                                    )
                                )
                                .font(.footnote)
                                .foregroundStyle(palette.textMutedColor)
                                .fixedSize(horizontal: false, vertical: true)
                            }
                        }
                        .padding(14)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .fill(palette.bgSurfaceColor)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .strokeBorder(palette.borderColor, lineWidth: 1)
                        )
                    }
                }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("productDetail.choicesSection")
    }

    private var howSection: some View {
        let answerTypesText = CatalogFormatting.formatAnswerTypeSummary(
            product.parts.flatMap(\.answerTypes),
            locale: locale
        )
        let maxScoreText = product.resolvedMaxScoreText(locale: locale)
        let timingText: String = {
            var parts: [String] = []
            if let mins = CatalogFormatting.formatIntRange(product.durationMinutes, locale: locale) {
                parts.append(
                    AppStrings.common("storefront.units.minutes", locale: locale, replacements: ["value": mins])
                )
            }
            if let timing = product.scoring.timing, timing == "PER_PART" || timing == "SHARED" {
                parts.append(CatalogStrings.text("storefront.detail.how.timing\(timing)", locale: locale))
            }
            return parts.joined(separator: " · ")
        }()

        return VStack(alignment: .leading, spacing: 12) {
            CatalogSectionHeading(
                title: CatalogStrings.text("storefront.detail.howTitle", locale: locale)
            )

            VStack(alignment: .leading, spacing: 12) {
                if !answerTypesText.isEmpty {
                    infoBlock(
                        title: CatalogStrings.text("storefront.detail.how.answerTypes", locale: locale),
                        value: answerTypesText
                    )
                }

                if let maxScoreText {
                    infoBlock(
                        title: CatalogStrings.text("storefront.detail.how.scoring", locale: locale),
                        value: CatalogStrings.text(
                            "storefront.detail.how.maxScore",
                            locale: locale,
                            replacements: ["score": maxScoreText]
                        )
                    )
                }

                if !timingText.isEmpty {
                    infoBlock(
                        title: CatalogStrings.text("storefront.detail.how.timing", locale: locale),
                        value: timingText
                    )
                }

                infoBlock(
                    title: CatalogStrings.text("storefront.detail.how.switching", locale: locale),
                    value: CatalogStrings.text("storefront.detail.how.switchingValue", locale: locale)
                )

                if product.hasEssay {
                    Text(CatalogStrings.text("storefront.detail.how.essayNote", locale: locale))
                        .font(.footnote)
                        .foregroundStyle(palette.textMutedColor)
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(palette.bgPageColor)
                        )
                        .fixedSize(horizontal: false, vertical: true)
                }

                if !product.omittedEssayGroups.isEmpty {
                    VStack(alignment: .leading, spacing: 6) {
                        ForEach(product.omittedEssayGroups) { group in
                            let prefix = CatalogStrings.text("storefront.detail.how.noEssay", locale: locale)
                            let line: String = {
                                if let points = group.points {
                                    let pointsStr = CatalogStrings.text(
                                        "storefront.detail.how.points",
                                        locale: locale,
                                        replacements: ["value": CatalogFormatting.formatDecimal(points, locale: locale)]
                                    )
                                    return "\(prefix) \(group.name) · \(pointsStr)"
                                }
                                return "\(prefix) \(group.name)"
                            }()
                            Text(line)
                                .font(.footnote)
                                .foregroundStyle(palette.textMutedColor)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(palette.bgSurfaceColor)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .strokeBorder(palette.borderColor, lineWidth: 1)
            )
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("productDetail.howSection")
    }

    private var rightsSection: some View {
        let timingKey = product.hasEssay
            ? "storefront.detail.rights.afterEssay"
            : "storefront.detail.rights.afterGrading"

        return VStack(alignment: .leading, spacing: 12) {
            CatalogSectionHeading(
                title: CatalogStrings.text("storefront.detail.rights.title", locale: locale)
            )

            VStack(alignment: .leading, spacing: 10) {
                keyValueRow(
                    title: CatalogStrings.text("storefront.detail.rights.attempts", locale: locale),
                    value: CatalogStrings.plural(
                        "storefront.detail.attemptsPerPurchase",
                        count: product.attemptsGranted,
                        locale: locale
                    ),
                    boldValue: true
                )

                if let resultView = product.resultView {
                    Divider()
                    keyValueRow(
                        title: CatalogStrings.text("storefront.detail.rights.resultView", locale: locale),
                        value: CatalogStrings.resultViewLabel(for: resultView, locale: locale)
                    )
                }

                Divider()
                keyValueRow(
                    title: CatalogStrings.text("storefront.detail.rights.resultTiming", locale: locale),
                    value: CatalogStrings.text(timingKey, locale: locale)
                )

                Divider()
                booleanRow(
                    title: CatalogStrings.text("storefront.detail.rights.ai", locale: locale),
                    value: product.benefits.aiExplanation
                )

                Divider()
                booleanRow(
                    title: CatalogStrings.text("storefront.detail.rights.strengths", locale: locale),
                    value: product.benefits.strengthWeakness
                )

                Divider()
                booleanRow(
                    title: CatalogStrings.text("storefront.detail.rights.practice", locale: locale),
                    value: product.benefits.practice
                )

                if let note = product.benefits.note {
                    Text("\(CatalogStrings.text("storefront.detail.rights.noteLabel", locale: locale)) \(note)")
                        .font(.footnote)
                        .foregroundStyle(palette.textMutedColor)
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(palette.bgPageColor)
                        )
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(palette.bgSurfaceColor)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .strokeBorder(palette.borderColor, lineWidth: 1)
            )
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("productDetail.rightsSection")
    }

    private var pricePanelSection: some View {
        let questions = CatalogFormatting.formatIntRange(product.questionCount, locale: locale)
        let minutes = CatalogFormatting.formatIntRange(product.durationMinutes, locale: locale)

        return VStack(alignment: .leading, spacing: 12) {
            Text(CatalogStrings.text("storefront.detail.priceHeading", locale: locale))
                .font(.caption.weight(.semibold))
                .foregroundStyle(palette.textMutedColor)
                .textCase(.uppercase)
                .accessibilityAddTraits(.isHeader)

            ProductPriceView(
                listPrice: product.listPrice,
                price: product.price,
                promotion: product.promotion,
                locale: locale,
                isLarge: true
            )

            if let promotion = product.promotion {
                let endsText = CatalogStrings.text(
                    "storefront.detail.promoEnds",
                    locale: locale,
                    replacements: ["date": CatalogFormatting.formatDayMonth(promotion.endsAt)]
                )
                Text("\(promotion.name) · \(endsText)")
                    .font(.footnote.weight(.medium))
                    .foregroundStyle(statusPalette.warningColor)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("productDetail.promotionLine")
            }

            Divider()

            VStack(alignment: .leading, spacing: 8) {
                bulletLine(CatalogStrings.text("storefront.detail.includesTax", locale: locale))
                bulletLine(
                    CatalogStrings.plural(
                        "storefront.detail.attemptsPerPurchase",
                        count: product.attemptsGranted,
                        locale: locale
                    )
                )
                if let questions, let minutes {
                    bulletLine(
                        CatalogStrings.text(
                            "storefront.detail.perAttempt",
                            locale: locale,
                            replacements: ["questions": questions, "minutes": minutes]
                        )
                    )
                }
            }

            AccessActionSectionView(
                code: product.code,
                versionNo: product.versionNo,
                price: Int(product.price),
                isBundle: false,
                isPricePanel: true,
                cell: accessCell,
                locale: locale,
                onButtonTap: onAccessButtonTap,
                onReloadAccess: onReloadAccess
            )
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(palette.bgSurfaceColor)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .strokeBorder(palette.borderColor, lineWidth: 1)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("productDetail.pricePanel")
    }

    private func infoBlock(title: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
                .font(.footnote)
                .foregroundStyle(palette.textMutedColor)
            Text(value)
                .font(.subheadline)
                .foregroundStyle(palette.textColor)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private func keyValueRow(title: String, value: String, boldValue: Bool = false) -> some View {
        ViewThatFits(in: .horizontal) {
            HStack(alignment: .top, spacing: 12) {
                Text(title)
                    .font(.subheadline)
                    .foregroundStyle(palette.textMutedColor)
                Spacer(minLength: 8)
                Text(value)
                    .font(boldValue ? .subheadline.weight(.semibold) : .subheadline)
                    .foregroundStyle(palette.textColor)
                    .multilineTextAlignment(.trailing)
            }
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.subheadline)
                    .foregroundStyle(palette.textMutedColor)
                Text(value)
                    .font(boldValue ? .subheadline.weight(.semibold) : .subheadline)
                    .foregroundStyle(palette.textColor)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private func booleanRow(title: String, value: Bool) -> some View {
        ViewThatFits(in: .horizontal) {
            HStack(alignment: .center, spacing: 12) {
                Text(title)
                    .font(.subheadline)
                    .foregroundStyle(palette.textMutedColor)
                Spacer(minLength: 8)
                CatalogYesNoLabel(value: value, locale: locale)
            }
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.subheadline)
                    .foregroundStyle(palette.textMutedColor)
                CatalogYesNoLabel(value: value, locale: locale)
            }
        }
    }

    private func bulletLine(_ text: String) -> some View {
        HStack(alignment: .top, spacing: 8) {
            Image(systemName: "checkmark")
                .font(.caption.weight(.bold))
                .foregroundStyle(palette.primaryTextColor)
                .padding(.top, 3)
                .accessibilityHidden(true)
            Text(text)
                .font(.subheadline)
                .foregroundStyle(palette.textSecondaryColor)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}
