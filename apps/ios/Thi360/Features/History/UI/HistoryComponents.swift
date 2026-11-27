import SwiftUI

struct HistorySkeletonView: View {
    let locale: AppLocale

    @Environment(\.brandPalette) private var palette

    var body: some View {
        VStack(spacing: 12) {
            ForEach(0..<3, id: \.self) { index in
                VStack(alignment: .leading, spacing: 10) {
                    RoundedRectangle(cornerRadius: 6, style: .continuous)
                        .fill(palette.bgSubtleColor)
                        .frame(maxWidth: .infinity, minHeight: 20, maxHeight: 20)
                    HStack(spacing: 8) {
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(palette.bgSubtleColor)
                            .frame(width: 78, height: 22)
                        RoundedRectangle(cornerRadius: 6, style: .continuous)
                            .fill(palette.bgSubtleColor)
                            .frame(width: 120, height: 14)
                    }
                    HStack {
                        RoundedRectangle(cornerRadius: 6, style: .continuous)
                            .fill(palette.bgSubtleColor)
                            .frame(width: 90, height: 18)
                        Spacer()
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .fill(palette.bgSubtleColor)
                            .frame(width: 108, height: 40)
                    }
                }
                .padding(16)
                .background(
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .fill(palette.bgSurfaceColor)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .strokeBorder(palette.borderColor, lineWidth: 1)
                )
                .accessibilityIdentifier("history.skeletonCard.\(index)")
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(MyExamsStrings.text("history.loading", locale: locale))
        .accessibilityIdentifier("history.loadingSkeleton")
        .reportMyExamsFrame("history.loadingSkeleton")
    }

    var accessibilityLoadingLabel: String {
        MyExamsStrings.text("history.loading", locale: locale)
    }
}

struct HistoryStaleBannerView: View {
    let lastLoadedAt: Date?
    let isRetrying: Bool
    let locale: AppLocale
    let onRetry: () -> Void

    @Environment(\.statusPalette) private var statusPalette
    @Environment(\.brandPalette) private var palette

    private var titleText: String {
        MyExamsStrings.text("history.stale.title", locale: locale)
    }

    private var descriptionText: String {
        if let lastLoadedAt {
            let formatted = HistoryRowFormat.formatDateTime(lastLoadedAt)
            return MyExamsStrings.text(
                "history.stale.description",
                locale: locale,
                replacements: ["time": formatted]
            )
        }
        return MyExamsStrings.text("history.stale.descriptionNoTime", locale: locale)
    }

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(statusPalette.warningColor)
                .padding(.top, 2)
                .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 6) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(titleText)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(statusPalette.warningColor)
                        .fixedSize(horizontal: false, vertical: true)
                    Text(descriptionText)
                        .font(.footnote)
                        .foregroundStyle(palette.textSecondaryColor)
                        .fixedSize(horizontal: false, vertical: true)
                }

                Button {
                    guard !isRetrying else { return }
                    onRetry()
                } label: {
                    HStack(spacing: 6) {
                        if isRetrying {
                            ProgressView()
                                .controlSize(.small)
                                .tint(statusPalette.warningColor)
                        }
                        Text(AppStrings.common("common.retry", locale: locale))
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(statusPalette.warningColor)
                    }
                    .frame(minWidth: 44, minHeight: 44, alignment: .leading)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .disabled(isRetrying)
                .accessibilityLabel(AppStrings.common("common.retry", locale: locale))
                .accessibilityIdentifier("history.staleBanner.retryButton")
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(statusPalette.warningBgColor)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .strokeBorder(statusPalette.warningColor.opacity(0.35), lineWidth: 1)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("history.staleBanner")
    }

    var renderedStrings: [String] {
        [titleText, descriptionText, AppStrings.common("common.retry", locale: locale)]
    }
}

struct HistoryFilterBarView: View {
    @Bindable var feed: HistoryFeed
    let locale: AppLocale
    let onOpenFilterSheet: () -> Void

    @Environment(\.brandPalette) private var palette

    private var selectedKindLabel: String {
        guard let kindCode = feed.filter.kind else {
            return MyExamsStrings.text("history.filters.kindAll", locale: locale)
        }
        if let option = feed.examKindOptions.first(where: { $0.code == kindCode }) {
            return HistoryRowFormat.resolveOptionLabel(option, locale: locale)
        }
        return kindCode
    }

    private var selectedScopeLabel: String {
        guard let scope = feed.filter.scope else {
            return MyExamsStrings.text("history.filters.scopeAll", locale: locale)
        }
        return MyExamsStrings.text(scope.localizationKey, locale: locale)
    }

    private var dateRangeLabel: String {
        switch (feed.filter.from, feed.filter.to) {
        case let (from?, to?):
            return "\(from.displayString) – \(to.displayString)"
        case let (from?, nil):
            return "\(MyExamsStrings.text("history.filters.from", locale: locale)): \(from.displayString)"
        case let (nil, to?):
            return "\(MyExamsStrings.text("history.filters.to", locale: locale)): \(to.displayString)"
        case (nil, nil):
            return MyExamsStrings.text("history.filters.dateRange", locale: locale)
        }
    }

    private var hasDateFilter: Bool {
        feed.filter.from != nil || feed.filter.to != nil
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            VStack(alignment: .leading, spacing: 4) {
                Text(MyExamsStrings.text("history.filters.q", locale: locale))
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(palette.textSecondaryColor)

                HStack(spacing: 8) {
                    Image(systemName: "magnifyingglass")
                        .font(.subheadline)
                        .foregroundStyle(palette.textMutedColor)
                        .accessibilityHidden(true)

                    TextField(
                        MyExamsStrings.text("history.filters.qPlaceholder", locale: locale),
                        text: Binding(
                            get: { feed.searchText },
                            set: { feed.updateSearchText($0) }
                        )
                    )
                    .submitLabel(.search)
                    .onSubmit {
                        Task { await feed.submitSearchImmediately() }
                    }
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .foregroundStyle(palette.textColor)
                    .accessibilityLabel(MyExamsStrings.text("history.filters.q", locale: locale))
                    .accessibilityIdentifier("history.filter.searchField")
                }
                .padding(.horizontal, 12)
                .frame(minHeight: 44)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(palette.bgSubtleColor)
                )
            }

            MyExamsChipFlowLayout(horizontalSpacing: 8, verticalSpacing: 8) {
                Button(action: onOpenFilterSheet) {
                    Image(systemName: "slider.horizontal.3")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(palette.primaryTextColor)
                        .frame(minWidth: 44, minHeight: 44)
                        .background(
                            Capsule(style: .continuous)
                                .fill(palette.bgSubtleColor)
                        )
                        .contentShape(Capsule(style: .continuous))
                }
                .buttonStyle(.plain)
                .accessibilityLabel(MyExamsStrings.text("history.filters.label", locale: locale))
                .accessibilityIdentifier("history.filter.openSheetButton")
                .reportMyExamsFrame("history.filter.openSheetButton")

                Menu {
                    Button(MyExamsStrings.text("history.filters.kindAll", locale: locale)) {
                        Task { await feed.selectKind(nil) }
                    }
                    ForEach(feed.examKindOptions, id: \.code) { option in
                        Button(HistoryRowFormat.resolveOptionLabel(option, locale: locale)) {
                            Task { await feed.selectKind(option.code) }
                        }
                    }
                } label: {
                    filterChipLabel(
                        title: selectedKindLabel,
                        isActive: feed.filter.kind != nil,
                        trailingSymbol: "chevron.down"
                    )
                }
                .accessibilityLabel("\(MyExamsStrings.text("history.filters.kind", locale: locale)): \(selectedKindLabel)")
                .accessibilityIdentifier("history.filter.kindMenu")

                Menu {
                    Button(MyExamsStrings.text("history.filters.scopeAll", locale: locale)) {
                        Task { await feed.selectScope(nil) }
                    }
                    ForEach(HistoryScope.allCases, id: \.self) { scope in
                        Button(MyExamsStrings.text(scope.localizationKey, locale: locale)) {
                            Task { await feed.selectScope(scope) }
                        }
                    }
                } label: {
                    filterChipLabel(
                        title: selectedScopeLabel,
                        isActive: feed.filter.scope != nil,
                        trailingSymbol: "chevron.down"
                    )
                }
                .accessibilityLabel("\(MyExamsStrings.text("history.filters.scope", locale: locale)): \(selectedScopeLabel)")
                .accessibilityIdentifier("history.filter.scopeMenu")

                Button(action: onOpenFilterSheet) {
                    filterChipLabel(
                        title: dateRangeLabel,
                        isActive: hasDateFilter,
                        trailingSymbol: "calendar"
                    )
                }
                .buttonStyle(.plain)
                .accessibilityLabel(dateRangeLabel)
                .accessibilityIdentifier("history.filter.dateChipButton")
                .reportMyExamsFrame("history.filter.dateChipButton")

                if !feed.filter.isEmpty {
                    Button {
                        Task { await feed.clearFilters() }
                    } label: {
                        Text(MyExamsStrings.text("history.filters.clear", locale: locale))
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(palette.primaryTextColor)
                            .padding(.horizontal, 10)
                            .frame(minWidth: 44, minHeight: 44)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(MyExamsStrings.text("history.filters.clear", locale: locale))
                    .accessibilityIdentifier("history.filter.clearButton")
                    .reportMyExamsFrame("history.filter.clearButton")
                }
            }
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(palette.bgSurfaceColor)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .strokeBorder(palette.borderColor, lineWidth: 1)
        )
        .accessibilityElement(children: .contain)
        .accessibilityLabel(MyExamsStrings.text("history.filters.label", locale: locale))
        .accessibilityIdentifier("history.filterBar")
    }

    private func filterChipLabel(title: String, isActive: Bool, trailingSymbol: String) -> some View {
        HStack(spacing: 6) {
            Text(title)
                .font(.subheadline.weight(isActive ? .semibold : .medium))
                .lineLimit(2)
                .multilineTextAlignment(.leading)
            Image(systemName: trailingSymbol)
                .font(.caption.weight(.semibold))
                .accessibilityHidden(true)
        }
        .foregroundStyle(isActive ? palette.primaryTextColor : palette.textColor)
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .frame(minWidth: 44, minHeight: 44)
        .background(
            Capsule(style: .continuous)
                .fill(isActive ? palette.primaryContainerColor : palette.bgSubtleColor)
        )
        .contentShape(Capsule(style: .continuous))
    }
}

struct HistoryFilterSheetView: View {
    @Bindable var feed: HistoryFeed
    let locale: AppLocale
    let onDismiss: () -> Void

    @Environment(\.brandPalette) private var palette
    @Environment(\.calendar) private var calendar

    private var selectedKindLabel: String {
        guard let kindCode = feed.filter.kind else {
            return MyExamsStrings.text("history.filters.kindAll", locale: locale)
        }
        if let option = feed.examKindOptions.first(where: { $0.code == kindCode }) {
            return HistoryRowFormat.resolveOptionLabel(option, locale: locale)
        }
        return kindCode
    }

    private var selectedScopeLabel: String {
        guard let scope = feed.filter.scope else {
            return MyExamsStrings.text("history.filters.scopeAll", locale: locale)
        }
        return MyExamsStrings.text(scope.localizationKey, locale: locale)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(MyExamsStrings.text("history.filters.q", locale: locale))
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(palette.textSecondaryColor)

                        HStack(spacing: 8) {
                            Image(systemName: "magnifyingglass")
                                .font(.subheadline)
                                .foregroundStyle(palette.textMutedColor)
                                .accessibilityHidden(true)

                            TextField(
                                MyExamsStrings.text("history.filters.qPlaceholder", locale: locale),
                                text: Binding(
                                    get: { feed.searchText },
                                    set: { feed.updateSearchText($0) }
                                )
                            )
                            .submitLabel(.search)
                            .onSubmit {
                                Task { await feed.submitSearchImmediately() }
                            }
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .foregroundStyle(palette.textColor)
                            .accessibilityLabel(MyExamsStrings.text("history.filters.q", locale: locale))
                            .accessibilityIdentifier("history.filterSheet.searchField")
                        }
                        .padding(.horizontal, 12)
                        .frame(minHeight: 44)
                        .background(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(palette.bgSurfaceColor)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .strokeBorder(palette.borderColor, lineWidth: 1)
                        )
                    }

                    VStack(spacing: 0) {
                        Menu {
                            Button(MyExamsStrings.text("history.filters.kindAll", locale: locale)) {
                                Task { await feed.selectKind(nil) }
                            }
                            ForEach(feed.examKindOptions, id: \.code) { option in
                                Button(HistoryRowFormat.resolveOptionLabel(option, locale: locale)) {
                                    Task { await feed.selectKind(option.code) }
                                }
                            }
                        } label: {
                            sheetMenuRow(
                                label: MyExamsStrings.text("history.filters.kind", locale: locale),
                                value: selectedKindLabel
                            )
                        }
                        .accessibilityIdentifier("history.filterSheet.kindMenu")

                        Divider()

                        Menu {
                            Button(MyExamsStrings.text("history.filters.scopeAll", locale: locale)) {
                                Task { await feed.selectScope(nil) }
                            }
                            ForEach(HistoryScope.allCases, id: \.self) { scope in
                                Button(MyExamsStrings.text(scope.localizationKey, locale: locale)) {
                                    Task { await feed.selectScope(scope) }
                                }
                            }
                        } label: {
                            sheetMenuRow(
                                label: MyExamsStrings.text("history.filters.scope", locale: locale),
                                value: selectedScopeLabel
                            )
                        }
                        .accessibilityIdentifier("history.filterSheet.scopeMenu")
                    }
                    .background(
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .fill(palette.bgSurfaceColor)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .strokeBorder(palette.borderColor, lineWidth: 1)
                    )

                    VStack(spacing: 0) {
                        fromDateRow
                        Divider()
                        toDateRow
                    }
                    .background(
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .fill(palette.bgSurfaceColor)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .strokeBorder(palette.borderColor, lineWidth: 1)
                    )
                }
                .padding(16)
            }
            .background(palette.bgPageColor.ignoresSafeArea())
            .navigationTitle(MyExamsStrings.text("history.filters.label", locale: locale))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    if !feed.filter.isEmpty {
                        Button(MyExamsStrings.text("history.filters.clear", locale: locale)) {
                            Task { await feed.clearFilters() }
                        }
                        .frame(minWidth: 44, minHeight: 44)
                        .accessibilityIdentifier("history.filterSheet.clearButton")
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(AppStrings.common("common.close", locale: locale), action: onDismiss)
                        .fontWeight(.semibold)
                        .frame(minWidth: 44, minHeight: 44)
                        .accessibilityIdentifier("history.filterSheet.closeButton")
                }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("history.filterSheet")
    }

    private func sheetMenuRow(label: String, value: String) -> some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(label)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(palette.textMutedColor)
                Text(value)
                    .font(.body)
                    .foregroundStyle(palette.primaryTextColor)
                    .multilineTextAlignment(.leading)
            }
            Spacer(minLength: 8)
            Image(systemName: "chevron.up.chevron.down")
                .font(.caption.weight(.semibold))
                .foregroundStyle(palette.primaryTextColor)
                .accessibilityHidden(true)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
        .contentShape(Rectangle())
    }

    @ViewBuilder
    private var fromDateRow: some View {
        let label = MyExamsStrings.text("history.filters.from", locale: locale)
        HStack(spacing: 8) {
            Text(label)
                .font(.body)
                .foregroundStyle(palette.textColor)
            Spacer(minLength: 8)
            if let from = feed.filter.from {
                fromDatePicker(selected: from)
                Button {
                    Task { await feed.selectFromDate(nil) }
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.body)
                        .foregroundStyle(palette.textMutedColor)
                        .frame(minWidth: 44, minHeight: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(MyExamsStrings.text("history.filters.clearFrom", locale: locale))
                .accessibilityIdentifier("history.filterSheet.clearFromButton")
            } else {
                Button {
                    let initial = feed.filter.to ?? HistoryCalendarDate(date: feed.clock.now, calendar: calendar)
                    Task { await feed.selectFromDate(initial) }
                } label: {
                    Text(MyExamsStrings.text("history.filters.selectDate", locale: locale))
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(palette.primaryTextColor)
                        .padding(.horizontal, 10)
                        .frame(minWidth: 44, minHeight: 44)
                        .background(
                            RoundedRectangle(cornerRadius: 8, style: .continuous)
                                .fill(palette.bgSubtleColor)
                        )
                        .contentShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("\(label): \(MyExamsStrings.text("history.filters.selectDate", locale: locale))")
                .accessibilityIdentifier("history.filterSheet.pickFromButton")
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 6)
        .frame(minHeight: 52)
    }

    @ViewBuilder
    private func fromDatePicker(selected: HistoryCalendarDate) -> some View {
        let binding = Binding<Date>(
            get: { selected.toPickerDate(calendar: calendar) },
            set: { newDate in
                let picked = HistoryCalendarDate(date: newDate, calendar: calendar)
                Task { await feed.selectFromDate(picked) }
            }
        )
        if let maxDate = feed.filter.to?.toPickerDate(calendar: calendar) {
            DatePicker(
                MyExamsStrings.text("history.filters.from", locale: locale),
                selection: binding,
                in: ...maxDate,
                displayedComponents: [.date]
            )
            .labelsHidden()
            .accessibilityIdentifier("history.filterSheet.fromDatePicker")
        } else {
            DatePicker(
                MyExamsStrings.text("history.filters.from", locale: locale),
                selection: binding,
                displayedComponents: [.date]
            )
            .labelsHidden()
            .accessibilityIdentifier("history.filterSheet.fromDatePicker")
        }
    }

    @ViewBuilder
    private var toDateRow: some View {
        let label = MyExamsStrings.text("history.filters.to", locale: locale)
        HStack(spacing: 8) {
            Text(label)
                .font(.body)
                .foregroundStyle(palette.textColor)
            Spacer(minLength: 8)
            if let to = feed.filter.to {
                toDatePicker(selected: to)
                Button {
                    Task { await feed.selectToDate(nil) }
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.body)
                        .foregroundStyle(palette.textMutedColor)
                        .frame(minWidth: 44, minHeight: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(MyExamsStrings.text("history.filters.clearTo", locale: locale))
                .accessibilityIdentifier("history.filterSheet.clearToButton")
            } else {
                Button {
                    let today = HistoryCalendarDate(date: feed.clock.now, calendar: calendar)
                    let initial: HistoryCalendarDate
                    if let from = feed.filter.from, today < from {
                        initial = from
                    } else {
                        initial = today
                    }
                    Task { await feed.selectToDate(initial) }
                } label: {
                    Text(MyExamsStrings.text("history.filters.selectDate", locale: locale))
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(palette.primaryTextColor)
                        .padding(.horizontal, 10)
                        .frame(minWidth: 44, minHeight: 44)
                        .background(
                            RoundedRectangle(cornerRadius: 8, style: .continuous)
                                .fill(palette.bgSubtleColor)
                        )
                        .contentShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("\(label): \(MyExamsStrings.text("history.filters.selectDate", locale: locale))")
                .accessibilityIdentifier("history.filterSheet.pickToButton")
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 6)
        .frame(minHeight: 52)
    }

    @ViewBuilder
    private func toDatePicker(selected: HistoryCalendarDate) -> some View {
        let binding = Binding<Date>(
            get: { selected.toPickerDate(calendar: calendar) },
            set: { newDate in
                let picked = HistoryCalendarDate(date: newDate, calendar: calendar)
                Task { await feed.selectToDate(picked) }
            }
        )
        if let minDate = feed.filter.from?.toPickerDate(calendar: calendar) {
            DatePicker(
                MyExamsStrings.text("history.filters.to", locale: locale),
                selection: binding,
                in: minDate...,
                displayedComponents: [.date]
            )
            .labelsHidden()
            .accessibilityIdentifier("history.filterSheet.toDatePicker")
        } else {
            DatePicker(
                MyExamsStrings.text("history.filters.to", locale: locale),
                selection: binding,
                displayedComponents: [.date]
            )
            .labelsHidden()
            .accessibilityIdentifier("history.filterSheet.toDatePicker")
        }
    }
}

struct HistoryRowCardView: View {
    let item: HistoryAttemptItem
    let subjectMap: [String: CatalogOptionItem]
    let locale: AppLocale
    let onContinue: (String) -> Void
    let onViewResult: (String) -> Void

    @Environment(\.brandPalette) private var palette
    @Environment(\.statusPalette) private var statusPalette

    private var statusInfo: HistoryStatusInfo {
        HistoryRowFormat.resolveStatus(state: item.state, regrading: item.regrading)
    }

    private var faultKind: HistoryFaultKind? {
        HistoryRowFormat.resolveFault(for: item)
    }

    private var scoreDisplay: HistoryScoreDisplay {
        HistoryRowFormat.formatScore(for: item, locale: locale)
    }

    private var scopeLine: String {
        HistoryRowFormat.formatScopeLine(for: item, subjectMap: subjectMap, locale: locale)
    }

    private var choicesLine: String {
        HistoryRowFormat.formatChoices(item.choices, subjectMap: subjectMap, locale: locale)
    }

    private var attemptStartedLine: String {
        HistoryRowFormat.formatAttemptStartedLine(for: item, locale: locale)
    }

    private var statusBadgeStyle: MyExamsBadgeStyle {
        switch statusInfo.tone {
        case .success: .success
        case .warning: .warning
        case .info: .info
        case .danger: .danger
        case .neutral: .subtle
        }
    }

    var body: some View {
        let score = scoreDisplay
        let isContinue = item.action == "CONTINUE"
        let isViewResult = item.action == "VIEW_RESULT"

        VStack(alignment: .leading, spacing: 12) {
            VStack(alignment: .leading, spacing: 6) {
                if isViewResult {
                    Button {
                        onViewResult(item.attemptId)
                    } label: {
                        Text(item.examName)
                            .font(.headline)
                            .foregroundStyle(palette.primaryTextColor)
                            .multilineTextAlignment(.leading)
                            .fixedSize(horizontal: false, vertical: true)
                            .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(item.examName)
                    .accessibilityIdentifier("history.row.titleButton.\(item.attemptId)")
                    .reportMyExamsFrame("history.row.titleButton.\(item.attemptId)")
                } else {
                    Text(item.examName)
                        .font(.headline)
                        .foregroundStyle(palette.textColor)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityIdentifier("history.row.titleText.\(item.attemptId)")
                }

                if let fault = faultKind {
                    HStack(alignment: .top, spacing: 6) {
                        Image(systemName: fault == .compensated ? "info.circle" : "exclamationmark.triangle")
                            .font(.subheadline.weight(.semibold))
                            .padding(.top, 2)
                            .accessibilityHidden(true)
                        Text(MyExamsStrings.text(fault.localizationKey, locale: locale))
                            .font(.subheadline)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .foregroundStyle(fault == .compensated ? statusPalette.infoColor : statusPalette.warningColor)
                    .accessibilityIdentifier("history.row.fault.\(item.attemptId)")
                }

                MyExamsChipFlowLayout(horizontalSpacing: 8, verticalSpacing: 6) {
                    MyExamsBadgeView(
                        text: MyExamsStrings.text(statusInfo.localizationKey, locale: locale),
                        style: statusBadgeStyle
                    )
                    Text(scopeLine)
                        .font(.subheadline)
                        .foregroundStyle(palette.textSecondaryColor)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityIdentifier("history.row.scope.\(item.attemptId)")
                }
            }

            ViewThatFits(in: .horizontal) {
                HStack(alignment: .top, spacing: 16) {
                    scoreBlock(score: score)
                    Spacer(minLength: 8)
                    attemptStartedBlock
                }
                VStack(alignment: .leading, spacing: 8) {
                    scoreBlock(score: score)
                    attemptStartedBlock
                }
            }

            if HistoryRowFormat.showsVactPracticeNote(for: item, hasScore: score.hasScore)
                || HistoryRowFormat.showsRegradingNote(for: item)
            {
                MyExamsChipFlowLayout(horizontalSpacing: 6, verticalSpacing: 6) {
                    if HistoryRowFormat.showsVactPracticeNote(for: item, hasScore: score.hasScore) {
                        MyExamsBadgeView(
                            text: MyExamsStrings.text("history.vactPracticeNote", locale: locale),
                            style: .subtle
                        )
                    }
                    if HistoryRowFormat.showsRegradingNote(for: item) {
                        MyExamsBadgeView(
                            text: MyExamsStrings.text("history.regradingNote", locale: locale),
                            style: .subtle
                        )
                    }
                }
            }

            ViewThatFits(in: .horizontal) {
                HStack(alignment: .bottom, spacing: 12) {
                    choiceBlock
                    Spacer(minLength: 8)
                    rowActionButton(isContinue: isContinue, isViewResult: isViewResult)
                }
                VStack(alignment: .leading, spacing: 10) {
                    choiceBlock
                    rowActionButton(isContinue: isContinue, isViewResult: isViewResult)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(palette.bgSurfaceColor)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .strokeBorder(palette.borderColor, lineWidth: 1)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("history.row.\(item.attemptId)")
    }

    private func scoreBlock(score: HistoryScoreDisplay) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(MyExamsStrings.text("history.columns.score", locale: locale))
                .font(.caption)
                .foregroundStyle(palette.textMutedColor)
            Text(score.text)
                .font(score.hasScore ? .headline.monospacedDigit() : .subheadline)
                .foregroundStyle(palette.textColor)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityIdentifier("history.row.score.\(item.attemptId)")
        }
    }

    private var attemptStartedBlock: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(MyExamsStrings.text("history.columns.attemptStarted", locale: locale))
                .font(.caption)
                .foregroundStyle(palette.textMutedColor)
            Text(attemptStartedLine)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(palette.textColor)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityIdentifier("history.row.attemptStarted.\(item.attemptId)")
        }
    }

    private var choiceBlock: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(MyExamsStrings.text("history.columns.choice", locale: locale))
                .font(.caption)
                .foregroundStyle(palette.textMutedColor)
            Text(choicesLine)
                .font(.subheadline)
                .foregroundStyle(palette.textColor)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityIdentifier("history.row.choices.\(item.attemptId)")
        }
    }

    @ViewBuilder
    private func rowActionButton(isContinue: Bool, isViewResult: Bool) -> some View {
        if isContinue {
            Button {
                onContinue(item.attemptId)
            } label: {
                Text(MyExamsStrings.text("history.actions.continue", locale: locale))
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(palette.onPrimaryColor)
                    .padding(.horizontal, 16)
                    .frame(minWidth: 100, minHeight: 44)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(palette.primaryFillColor)
                    )
                    .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(MyExamsStrings.text("history.actions.continue", locale: locale))
            .accessibilityIdentifier("history.row.continueButton.\(item.attemptId)")
            .reportMyExamsFrame("history.row.continueButton.\(item.attemptId)")
        } else if isViewResult {
            Button {
                onViewResult(item.attemptId)
            } label: {
                Text(MyExamsStrings.text("history.actions.viewResult", locale: locale))
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(palette.textColor)
                    .padding(.horizontal, 14)
                    .frame(minWidth: 112, minHeight: 44)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(palette.bgSubtleColor)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .strokeBorder(palette.borderColor, lineWidth: 1)
                    )
                    .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(MyExamsStrings.text("history.actions.viewResult", locale: locale))
            .accessibilityIdentifier("history.row.viewResultButton.\(item.attemptId)")
            .reportMyExamsFrame("history.row.viewResultButton.\(item.attemptId)")
        }
    }

    var hasTitleButton: Bool {
        item.action == "VIEW_RESULT"
    }

    var renderedStrings: [String] {
        let score = scoreDisplay
        var out: [String] = [item.examName]
        if let fault = faultKind {
            out.append(MyExamsStrings.text(fault.localizationKey, locale: locale))
        }
        out.append(MyExamsStrings.text(statusInfo.localizationKey, locale: locale))
        out.append(scopeLine)
        out.append(score.text)
        out.append(attemptStartedLine)
        if HistoryRowFormat.showsVactPracticeNote(for: item, hasScore: score.hasScore) {
            out.append(MyExamsStrings.text("history.vactPracticeNote", locale: locale))
        }
        if HistoryRowFormat.showsRegradingNote(for: item) {
            out.append(MyExamsStrings.text("history.regradingNote", locale: locale))
        }
        out.append(choicesLine)
        if item.action == "CONTINUE" {
            out.append(MyExamsStrings.text("history.actions.continue", locale: locale))
        } else if item.action == "VIEW_RESULT" {
            out.append(MyExamsStrings.text("history.actions.viewResult", locale: locale))
        }
        return out
    }
}
