import SwiftUI

struct CatalogFilterHeaderView: View {
    @Bindable var feed: CatalogFeed
    let locale: AppLocale
    let onOpenFilterSheet: () -> Void

    @Environment(\.brandPalette) private var palette

    private var effectiveKinds: [ExamKindInfo] {
        feed.filters.effectiveKinds
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            VStack(alignment: .leading, spacing: 6) {
                Text(CatalogStrings.text("storefront.catalog.searchLabel", locale: locale))
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(palette.textSecondaryColor)
                    .accessibilityIdentifier("catalog.searchLabel")

                HStack(spacing: 8) {
                    Image(systemName: "magnifyingglass")
                        .font(.subheadline)
                        .foregroundStyle(palette.textMutedColor)
                        .accessibilityHidden(true)

                    TextField(
                        CatalogStrings.text("storefront.catalog.searchPlaceholder", locale: locale),
                        text: Binding(
                            get: { feed.searchText },
                            set: { feed.updateSearchText($0) }
                        )
                    )
                    .textFieldStyle(.plain)
                    .submitLabel(.search)
                    .onSubmit {
                        Task { await feed.submitSearchImmediately() }
                    }
                    .foregroundStyle(palette.textColor)
                    .accessibilityLabel(CatalogStrings.text("storefront.catalog.searchLabel", locale: locale))
                    .accessibilityIdentifier("catalog.searchField")

                    if !feed.searchText.isEmpty {
                        Button {
                            feed.updateSearchText("")
                        } label: {
                            Image(systemName: "xmark.circle.fill")
                                .font(.body)
                                .foregroundStyle(palette.textMutedColor)
                                .frame(minWidth: 44, minHeight: 44)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(CatalogStrings.text("storefront.catalog.clearFilters", locale: locale))
                        .accessibilityIdentifier("catalog.searchClearButton")
                    }
                }
                .padding(.leading, 12)
                .padding(.trailing, feed.searchText.isEmpty ? 12 : 4)
                .frame(minHeight: 44)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(palette.bgSurfaceColor)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .strokeBorder(palette.borderStrongColor, lineWidth: 1)
                )
            }

            if !effectiveKinds.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    Text(CatalogStrings.text("storefront.catalog.kindLabel", locale: locale))
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(palette.textMutedColor)

                    CatalogFlowLayout(horizontalSpacing: 8, verticalSpacing: 8) {
                        kindChip(
                            title: CatalogStrings.text("storefront.catalog.kindAll", locale: locale),
                            isSelected: feed.query.kind == nil,
                            identifier: "catalog.kindChip.ALL"
                        ) {
                            Task { await feed.selectKind(nil) }
                        }

                        ForEach(effectiveKinds, id: \.code) { kind in
                            kindChip(
                                title: kind.code,
                                isSelected: feed.query.kind == kind.code,
                                identifier: "catalog.kindChip.\(kind.code)"
                            ) {
                                Task { await feed.selectKind(kind.code) }
                            }
                        }
                    }
                }
                .accessibilityElement(children: .contain)
                .accessibilityLabel(CatalogStrings.text("storefront.catalog.kindLabel", locale: locale))
                .accessibilityIdentifier("catalog.kindGroup")
            }

            CatalogFlowLayout(horizontalSpacing: 8, verticalSpacing: 8) {
                Button(action: onOpenFilterSheet) {
                    HStack(spacing: 6) {
                        Image(systemName: "slider.horizontal.3")
                            .font(.subheadline.weight(.semibold))
                            .accessibilityHidden(true)
                        Text(CatalogStrings.text("storefront.catalog.filtersLabel", locale: locale))
                            .font(.subheadline.weight(.semibold))
                            .fixedSize(horizontal: false, vertical: true)
                        if feed.query.activeSheetFilterCount > 0 {
                            Text("\(feed.query.activeSheetFilterCount)")
                                .font(.caption2.weight(.bold))
                                .foregroundStyle(palette.onPrimaryColor)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(
                                    Capsule(style: .continuous)
                                        .fill(palette.primaryFillColor)
                                )
                        }
                    }
                    .foregroundStyle(palette.primaryTextColor)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .frame(minWidth: 44, minHeight: 44)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(palette.primaryContainerColor)
                    )
                    .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
                .buttonStyle(.plain)
                .accessibilityLabel(CatalogStrings.text("storefront.catalog.filtersLabel", locale: locale))
                .accessibilityIdentifier("catalog.filterButton")

                Button {
                    Task { await feed.clearFilters() }
                } label: {
                    Text(CatalogStrings.text("storefront.catalog.clearFilters", locale: locale))
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(palette.textSecondaryColor)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10)
                        .frame(minWidth: 44, minHeight: 44)
                        .background(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(palette.bgSurfaceColor)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .strokeBorder(palette.borderColor, lineWidth: 1)
                        )
                        .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
                .buttonStyle(.plain)
                .accessibilityLabel(CatalogStrings.text("storefront.catalog.clearFilters", locale: locale))
                .accessibilityIdentifier("catalog.clearFiltersButton")
            }

            if !activeFilterSummaryPills.isEmpty {
                CatalogFlowLayout(horizontalSpacing: 6, verticalSpacing: 6) {
                    ForEach(activeFilterSummaryPills, id: \.self) { pill in
                        CatalogBadgeView(text: pill, style: .primary)
                    }
                }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel(CatalogStrings.text("storefront.catalog.filtersLabel", locale: locale))
        .accessibilityIdentifier("catalog.filterHeader")
    }

    private var activeFilterSummaryPills: [String] {
        var pills: [String] = []
        if let format = feed.query.format {
            pills.append(CatalogStrings.formatLabel(for: format.rawValue, locale: locale))
        }
        if let partCode = feed.query.part, let kindCode = feed.query.kind {
            if let matched = feed.filters.parts.first(where: { $0.kindCode == kindCode && $0.code == partCode }) {
                pills.append(matched.name)
            } else {
                pills.append(partCode)
            }
        } else if let subjectCode = feed.query.subject {
            if let matched = feed.filters.subjects.first(where: { $0.code == subjectCode }) {
                pills.append(matched.name.localized(for: locale))
            } else {
                pills.append(subjectCode)
            }
        }
        if feed.query.sort != .newest {
            pills.append(
                "\(CatalogStrings.text("storefront.catalog.sortLabel", locale: locale)): \(CatalogStrings.text(feed.query.sort.localizationKey, locale: locale))"
            )
        }
        return pills
    }

    private func kindChip(
        title: String,
        isSelected: Bool,
        identifier: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 4) {
                if isSelected {
                    Image(systemName: "checkmark")
                        .font(.caption.weight(.bold))
                        .accessibilityHidden(true)
                }
                Text(title)
                    .font(.subheadline.weight(isSelected ? .semibold : .medium))
                    .fixedSize(horizontal: false, vertical: true)
            }
            .foregroundStyle(isSelected ? palette.onPrimaryColor : palette.textColor)
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .frame(minWidth: 44, minHeight: 44)
            .background(
                Capsule(style: .continuous)
                    .fill(isSelected ? palette.primaryFillColor : palette.bgSurfaceColor)
            )
            .overlay(
                Capsule(style: .continuous)
                    .strokeBorder(isSelected ? Color.clear : palette.borderColor, lineWidth: 1)
            )
            .contentShape(Capsule(style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(title)
        .accessibilityAddTraits(isSelected ? [.isSelected] : [])
        .accessibilityIdentifier(identifier)
    }
}

struct CatalogFilterSheetView: View {
    @Bindable var feed: CatalogFeed
    let locale: AppLocale
    let onDismiss: () -> Void

    @Environment(\.brandPalette) private var palette

    private var orderedFormats: [CatalogFormat] {
        feed.filters.orderedFormats
    }

    private var visibleParts: [CatalogPartOption] {
        feed.filters.visibleParts(forKind: feed.query.kind)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    formatSection
                    partAndSubjectSection
                    sortSection
                }
                .padding(20)
            }
            .background(palette.bgPageColor.ignoresSafeArea())
            .navigationTitle(CatalogStrings.text("storefront.catalog.filtersLabel", locale: locale))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(CatalogStrings.text("storefront.catalog.clearFilters", locale: locale)) {
                        Task { await feed.clearFilters() }
                    }
                    .accessibilityIdentifier("catalog.filter.clearButton")
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(CatalogStrings.text("storefront.catalog.closeFilters", locale: locale), action: onDismiss)
                        .fontWeight(.semibold)
                        .accessibilityIdentifier("catalog.filter.doneButton")
                }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("catalog.filterSheet")
    }

    private var formatSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(CatalogStrings.text("storefront.catalog.formatLabel", locale: locale))
                .font(.headline)
                .foregroundStyle(palette.textColor)
                .accessibilityAddTraits(.isHeader)

            VStack(alignment: .leading, spacing: 10) {
                CatalogFlowLayout(horizontalSpacing: 8, verticalSpacing: 8) {
                    optionChip(
                        title: CatalogStrings.text("storefront.catalog.formatAll", locale: locale),
                        isSelected: feed.query.format == nil,
                        identifier: "catalog.filter.format.ALL"
                    ) {
                        Task { await feed.selectFormat(nil) }
                    }

                    ForEach(orderedFormats, id: \.self) { format in
                        optionChip(
                            title: CatalogStrings.formatLabel(for: format.rawValue, locale: locale),
                            isSelected: feed.query.format == format,
                            identifier: "catalog.filter.format.\(format.rawValue)"
                        ) {
                            Task { await feed.selectFormat(format) }
                        }
                    }
                }
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(palette.bgSurfaceColor)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .strokeBorder(palette.borderColor, lineWidth: 1)
            )

            Text(
                "\(CatalogStrings.text("storefront.catalog.formatHintLabel", locale: locale)) \(CatalogStrings.text("storefront.catalog.formatHint", locale: locale))"
            )
            .font(.footnote)
            .foregroundStyle(palette.textMutedColor)
            .fixedSize(horizontal: false, vertical: true)
            .accessibilityIdentifier("catalog.filter.formatHint")
        }
    }

    private var partAndSubjectSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(CatalogStrings.text("storefront.catalog.partLabel", locale: locale))
                .font(.headline)
                .foregroundStyle(palette.textColor)
                .accessibilityAddTraits(.isHeader)

            VStack(alignment: .leading, spacing: 10) {
                CatalogFlowLayout(horizontalSpacing: 8, verticalSpacing: 8) {
                    optionChip(
                        title: CatalogStrings.text("storefront.catalog.partAll", locale: locale),
                        isSelected: feed.query.part == nil && feed.query.subject == nil,
                        identifier: "catalog.filter.part.ALL"
                    ) {
                        Task { await feed.clearPartAndSubject() }
                    }

                    ForEach(visibleParts) { part in
                        let label = part.displayLabel(selectedKind: feed.query.kind)
                        let selected = feed.query.part == part.code && feed.query.kind == part.kindCode
                        optionChip(
                            title: label,
                            isSelected: selected,
                            identifier: "catalog.filter.part.\(part.kindCode).\(part.code)"
                        ) {
                            Task { await feed.selectPart(kind: part.kindCode, part: part.code) }
                        }
                    }

                    ForEach(feed.filters.subjects) { subject in
                        let label = subject.name.localized(for: locale)
                        let selected = feed.query.subject == subject.code && feed.query.part == nil
                        optionChip(
                            title: label,
                            isSelected: selected,
                            identifier: "catalog.filter.subject.\(subject.code)"
                        ) {
                            Task { await feed.selectSubject(subject.code) }
                        }
                    }
                }
            }
            .padding(14)
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
    }

    private var sortSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(CatalogStrings.text("storefront.catalog.sortLabel", locale: locale))
                .font(.headline)
                .foregroundStyle(palette.textColor)
                .accessibilityAddTraits(.isHeader)

            VStack(spacing: 8) {
                ForEach(CatalogSort.allCases, id: \.self) { sort in
                    let isSelected = feed.query.sort == sort
                    let title = CatalogStrings.text(sort.localizationKey, locale: locale)
                    Button {
                        Task { await feed.selectSort(sort) }
                    } label: {
                        HStack {
                            Text(title)
                                .font(.body.weight(isSelected ? .semibold : .regular))
                                .foregroundStyle(isSelected ? palette.primaryTextColor : palette.textColor)
                                .fixedSize(horizontal: false, vertical: true)
                            Spacer(minLength: 8)
                            if isSelected {
                                Image(systemName: "checkmark")
                                    .font(.subheadline.weight(.bold))
                                    .foregroundStyle(palette.primaryTextColor)
                                    .accessibilityHidden(true)
                            }
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 12)
                        .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                        .background(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(isSelected ? palette.primaryContainerColor : palette.bgSurfaceColor)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .strokeBorder(isSelected ? palette.primaryFillColor : palette.borderColor, lineWidth: 1)
                        )
                        .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(title)
                    .accessibilityAddTraits(isSelected ? [.isSelected] : [])
                    .accessibilityIdentifier("catalog.filter.sort.\(sort.rawValue)")
                }
            }
        }
    }

    private func optionChip(
        title: String,
        isSelected: Bool,
        identifier: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 4) {
                if isSelected {
                    Image(systemName: "checkmark")
                        .font(.caption.weight(.bold))
                        .accessibilityHidden(true)
                }
                Text(title)
                    .font(.subheadline.weight(isSelected ? .semibold : .medium))
                    .fixedSize(horizontal: false, vertical: true)
            }
            .foregroundStyle(isSelected ? palette.onPrimaryColor : palette.textColor)
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .frame(minWidth: 44, minHeight: 44)
            .background(
                Capsule(style: .continuous)
                    .fill(isSelected ? palette.primaryFillColor : palette.bgSubtleColor)
            )
            .contentShape(Capsule(style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(title)
        .accessibilityAddTraits(isSelected ? [.isSelected] : [])
        .accessibilityIdentifier(identifier)
    }
}
