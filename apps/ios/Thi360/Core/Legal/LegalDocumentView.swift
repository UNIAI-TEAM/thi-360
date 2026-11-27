import SwiftUI

struct LegalDocumentView: View {
    let legalId: LegalId
    @Bindable var preferences: AppPreferences
    var repository: LegalRepository = LegalRepository()
    var webBaseURL: URL = AppEnvironment.current.webBaseURL
    var onOpenOnWeb: ((URL) -> Void)? = nil

    @Environment(\.brandPalette) private var palette
    @Environment(\.statusPalette) private var statusPalette
    @Environment(\.openURL) private var openURL
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    private var locale: AppLocale { preferences.locale }

    private var loadResult: Result<LegalDocument, LegalLoadError> {
        repository.load(legalId, locale: locale)
    }

    private var navigationTitleText: String {
        if case let .success(document) = loadResult {
            return document.title
        }
        return AppStrings.legal(legalId.titleKey, locale: locale)
    }

    private var webURL: URL {
        legalId.webURL(webBaseURL: webBaseURL)
    }

    var body: some View {
        ScrollView(.vertical) {
            VStack(alignment: .leading, spacing: 16) {
                switch loadResult {
                case let .success(document):
                    documentContent(document)
                case .failure:
                    errorContent
                }
            }
            .frame(maxWidth: 720, alignment: .leading)
            .padding(20)
            .frame(maxWidth: .infinity, alignment: .center)
        }
        .background(palette.bgPageColor.ignoresSafeArea())
        .navigationTitle(navigationTitleText)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if AppEnvironment.current.isDev,
               ProcessInfo.processInfo.arguments.contains("-uitest-state-probe")
            {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        preferences.locale = (preferences.locale == .vi) ? .en : .vi
                    } label: {
                        Image(systemName: "globe")
                    }
                    .accessibilityIdentifier("legal.probe.toggleLocale")
                }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("legal.screen.\(legalId.rawValue)")
        .onAppear {
            Thi360AppDelegate.refreshSupportedOrientations(for: horizontalSizeClass)
        }
        .onChange(of: horizontalSizeClass) { _, newValue in
            Thi360AppDelegate.refreshSupportedOrientations(for: newValue)
        }
    }

    @ViewBuilder
    private func documentContent(_ document: LegalDocument) -> some View {
        Text(document.title)
            .font(.title2.weight(.bold))
            .foregroundStyle(palette.textColor)
            .fixedSize(horizontal: false, vertical: true)
            .accessibilityAddTraits(.isHeader)
            .accessibilityIdentifier("legal.title")

        if document.status == .draft {
            HStack(alignment: .top, spacing: 10) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(statusPalette.warningColor)
                    .accessibilityHidden(true)

                Text(AppStrings.legal("legal.draftBanner", locale: locale))
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(statusPalette.warningColor)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(statusPalette.warningBgColor)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .strokeBorder(statusPalette.warningColor.opacity(0.35), lineWidth: 1)
            )
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier("legal.draftBanner")
        }

        if let effectiveDate = document.effectiveDate {
            Text(
                AppStrings.legal(
                    "legal.effectiveFrom",
                    locale: locale,
                    replacements: ["date": LegalDocument.formatEffectiveDate(effectiveDate)]
                )
            )
            .font(.footnote.weight(.medium))
            .foregroundStyle(palette.textMutedColor)
            .fixedSize(horizontal: false, vertical: true)
            .accessibilityIdentifier("legal.effectiveDate")
        }

        if let note = document.note?.trimmingCharacters(in: .whitespacesAndNewlines), !note.isEmpty {
            HStack(alignment: .top, spacing: 10) {
                Image(systemName: "info.circle.fill")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(statusPalette.infoColor)
                    .accessibilityHidden(true)

                Text(note)
                    .font(.subheadline)
                    .foregroundStyle(statusPalette.infoColor)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(statusPalette.infoBgColor)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .strokeBorder(statusPalette.infoColor.opacity(0.3), lineWidth: 1)
            )
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier("legal.note")
        }

        VStack(alignment: .leading, spacing: 14) {
            ForEach(Array(document.blocks.enumerated()), id: \.offset) { index, block in
                blockView(block, index: index)
            }
        }

        openOnWebButton
            .padding(.top, 8)
    }

    @ViewBuilder
    private func blockView(_ block: LegalBlock, index: Int) -> some View {
        switch block {
        case let .heading(level, text):
            Text(text)
                .font(level <= 2 ? .title3.weight(.bold) : .headline.weight(.semibold))
                .foregroundStyle(palette.textColor)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, level <= 2 ? 8 : 4)
                .accessibilityAddTraits(.isHeader)
                .accessibilityIdentifier("legal.block.heading.\(index)")

        case let .paragraph(text):
            Text(text)
                .font(.body)
                .foregroundStyle(palette.textSecondaryColor)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityIdentifier("legal.block.paragraph.\(index)")

        case let .list(ordered, items):
            VStack(alignment: .leading, spacing: 8) {
                ForEach(Array(items.enumerated()), id: \.offset) { itemIndex, item in
                    HStack(alignment: .top, spacing: 8) {
                        Text(ordered ? "\(itemIndex + 1)." : "•")
                            .font(.body.weight(.semibold))
                            .foregroundStyle(palette.textSecondaryColor)
                            .accessibilityHidden(true)

                        Text(item)
                            .font(.body)
                            .foregroundStyle(palette.textSecondaryColor)
                            .fixedSize(horizontal: false, vertical: true)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
            }
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("legal.block.list.\(index)")

        case let .table(headers, rows):
            tableView(headers: headers, rows: rows, index: index)
        }
    }

    private func tableView(headers: [String], rows: [[String]], index: Int) -> some View {
        let columnCount = max(headers.count, rows.map(\.count).max() ?? 0)
        return ScrollView(.horizontal, showsIndicators: true) {
            Grid(alignment: .topLeading, horizontalSpacing: 0, verticalSpacing: 0) {
                if !headers.isEmpty {
                    GridRow {
                        ForEach(0..<columnCount, id: \.self) { col in
                            let headerText = col < headers.count ? headers[col] : ""
                            Text(headerText)
                                .font(.footnote.weight(.semibold))
                                .foregroundStyle(palette.textColor)
                                .fixedSize(horizontal: false, vertical: true)
                                .frame(minWidth: 120, maxWidth: 240, alignment: .leading)
                                .padding(10)
                                .background(palette.bgSubtleColor)
                                .overlay(alignment: .trailing) {
                                    if col < columnCount - 1 {
                                        Rectangle()
                                            .fill(palette.borderColor)
                                            .frame(width: 1)
                                    }
                                }
                        }
                    }

                    Divider()
                        .overlay(palette.borderColor)
                }

                ForEach(Array(rows.enumerated()), id: \.offset) { rowIndex, row in
                    GridRow {
                        ForEach(0..<columnCount, id: \.self) { col in
                            let cellText = col < row.count ? row[col] : ""
                            Text(cellText)
                                .font(.footnote)
                                .foregroundStyle(palette.textSecondaryColor)
                                .fixedSize(horizontal: false, vertical: true)
                                .frame(minWidth: 120, maxWidth: 240, alignment: .leading)
                                .padding(10)
                                .background(palette.bgSurfaceColor)
                                .overlay(alignment: .trailing) {
                                    if col < columnCount - 1 {
                                        Rectangle()
                                            .fill(palette.borderColor)
                                            .frame(width: 1)
                                    }
                                }
                        }
                    }

                    if rowIndex < rows.count - 1 {
                        Divider()
                            .overlay(palette.borderColor)
                    }
                }
            }
        }
        .background(palette.bgSurfaceColor)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .strokeBorder(palette.borderColor, lineWidth: 1)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("legal.block.table.\(index)")
    }

    private var errorContent: some View {
        VStack(spacing: 14) {
            Image(systemName: "doc.text.magnifyingglass")
                .font(.system(size: 32, weight: .semibold))
                .foregroundStyle(palette.dangerTextColor)
                .accessibilityHidden(true)

            Text(AppStrings.common("errorPage.title", locale: locale))
                .font(.headline)
                .foregroundStyle(palette.textColor)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
                .accessibilityIdentifier("legal.error.title")

            Text(AppStrings.common("errorPage.description", locale: locale))
                .font(.subheadline)
                .foregroundStyle(palette.textMutedColor)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityIdentifier("legal.error.description")

            openOnWebButton
                .padding(.top, 4)
        }
        .frame(maxWidth: .infinity)
        .padding(24)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(palette.bgSurfaceColor)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .strokeBorder(palette.borderColor, lineWidth: 1)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("legal.error")
    }

    private var openOnWebButton: some View {
        Button {
            triggerOpenOnWeb()
        } label: {
            HStack(spacing: 8) {
                Text(AppStrings.legal("legal.openOnWeb", locale: locale))
                    .font(.subheadline.weight(.semibold))
                Image(systemName: "arrow.up.right.square")
                    .font(.subheadline.weight(.semibold))
                    .accessibilityHidden(true)
            }
            .foregroundStyle(palette.primaryTextColor)
            .padding(.horizontal, 16)
            .frame(minHeight: 44)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(palette.primaryContainerColor)
            )
            .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("legal.openOnWebButton")
    }

    func triggerOpenOnWeb() {
        let target = webURL
        if let onOpenOnWeb {
            onOpenOnWeb(target)
        } else {
            openURL(target)
        }
    }
}
