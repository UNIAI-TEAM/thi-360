import SwiftUI

private struct MyExamsLayoutFrameObserverKey: EnvironmentKey {
    static let defaultValue: ((String, CGRect) -> Void)? = nil
}

extension EnvironmentValues {
    var myExamsLayoutFrameObserver: ((String, CGRect) -> Void)? {
        get { self[MyExamsLayoutFrameObserverKey.self] }
        set { self[MyExamsLayoutFrameObserverKey.self] = newValue }
    }
}

private struct MyExamsFrameReporterModifier: ViewModifier {
    let identifier: String
    @Environment(\.myExamsLayoutFrameObserver) private var observer

    func body(content: Content) -> some View {
        if let observer {
            content.background(
                GeometryReader { proxy in
                    let frame = proxy.frame(in: .global)
                    Color.clear
                        .onAppear { observer(identifier, frame) }
                        .onChange(of: frame) { _, newFrame in observer(identifier, newFrame) }
                }
            )
        } else {
            content
        }
    }
}

extension View {
    func reportMyExamsFrame(_ identifier: String) -> some View {
        modifier(MyExamsFrameReporterModifier(identifier: identifier))
    }
}

struct MyExamsChipFlowLayout: Layout {
    let horizontalSpacing: CGFloat
    let verticalSpacing: CGFloat

    init(horizontalSpacing: CGFloat = 6, verticalSpacing: CGFloat = 6) {
        self.horizontalSpacing = horizontalSpacing
        self.verticalSpacing = verticalSpacing
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        computePlacement(maxWidth: proposal.width ?? .infinity, subviews: subviews).size
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let placement = computePlacement(maxWidth: bounds.width, subviews: subviews)
        for (index, subview) in subviews.enumerated() where index < placement.origins.count {
            let origin = placement.origins[index]
            let size = placement.sizes[index]
            subview.place(
                at: CGPoint(x: bounds.minX + origin.x, y: bounds.minY + origin.y),
                proposal: ProposedViewSize(width: size.width, height: size.height)
            )
        }
    }

    private struct Placement {
        let origins: [CGPoint]
        let sizes: [CGSize]
        let size: CGSize
    }

    private func computePlacement(maxWidth: CGFloat, subviews: Subviews) -> Placement {
        guard !subviews.isEmpty else {
            return Placement(origins: [], sizes: [], size: .zero)
        }
        let availableWidth = maxWidth.isFinite ? max(1, maxWidth) : .greatestFiniteMagnitude
        var origins: [CGPoint] = []
        var sizes: [CGSize] = []
        origins.reserveCapacity(subviews.count)
        sizes.reserveCapacity(subviews.count)

        var cursorX: CGFloat = 0
        var cursorY: CGFloat = 0
        var rowHeight: CGFloat = 0
        var usedWidth: CGFloat = 0

        for subview in subviews {
            let measured = subview.sizeThatFits(ProposedViewSize(width: availableWidth, height: nil))
            let width = min(measured.width, availableWidth)
            let height = measured.height

            if cursorX > 0 && cursorX + horizontalSpacing + width > availableWidth {
                cursorX = 0
                cursorY += rowHeight + verticalSpacing
                rowHeight = 0
            }
            if cursorX > 0 {
                cursorX += horizontalSpacing
            }

            origins.append(CGPoint(x: cursorX, y: cursorY))
            sizes.append(CGSize(width: width, height: height))

            cursorX += width
            usedWidth = max(usedWidth, cursorX)
            rowHeight = max(rowHeight, height)
        }

        let totalHeight = cursorY + rowHeight
        let finalWidth = maxWidth.isFinite ? min(maxWidth, usedWidth) : usedWidth
        return Placement(origins: origins, sizes: sizes, size: CGSize(width: finalWidth, height: totalHeight))
    }
}

enum MyExamsBadgeStyle {
    case subtle
    case primary
    case info
    case success
    case warning
    case danger
    case outlined
}

struct MyExamsBadgeView: View {
    let text: String
    let style: MyExamsBadgeStyle
    var systemImage: String? = nil

    @Environment(\.brandPalette) private var palette
    @Environment(\.statusPalette) private var statusPalette

    private var foreground: Color {
        switch style {
        case .subtle, .outlined: palette.textSecondaryColor
        case .primary: palette.primaryTextColor
        case .info: statusPalette.infoColor
        case .success: statusPalette.successColor
        case .warning: statusPalette.warningColor
        case .danger: palette.dangerTextColor
        }
    }

    private var background: Color {
        switch style {
        case .subtle: palette.bgSubtleColor
        case .primary: palette.primaryContainerColor
        case .info: statusPalette.infoBgColor
        case .success: statusPalette.successBgColor
        case .warning: statusPalette.warningBgColor
        case .danger: palette.dangerBgColor
        case .outlined: Color.clear
        }
    }

    var body: some View {
        HStack(spacing: 4) {
            if let systemImage {
                Image(systemName: systemImage)
                    .font(.caption.weight(.semibold))
                    .accessibilityHidden(true)
            }
            Text(text)
                .font(.caption.weight(style == .outlined ? .medium : .semibold))
                .fixedSize(horizontal: false, vertical: true)
        }
        .foregroundStyle(foreground)
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(
            Capsule(style: .continuous)
                .fill(background)
        )
        .overlay(
            Capsule(style: .continuous)
                .strokeBorder(style == .outlined ? palette.borderColor : Color.clear, lineWidth: 1)
        )
    }
}

struct MyExamsSkeletonView: View {
    let locale: AppLocale
    let isRegularWidth: Bool

    @Environment(\.brandPalette) private var palette

    private var columns: [GridItem] {
        if isRegularWidth {
            return [
                GridItem(.flexible(), spacing: 16, alignment: .top),
                GridItem(.flexible(), spacing: 16, alignment: .top)
            ]
        }
        return [GridItem(.flexible(), spacing: 16, alignment: .top)]
    }

    var body: some View {
        LazyVGrid(columns: columns, alignment: .leading, spacing: 16) {
            ForEach(0..<3, id: \.self) { index in
                VStack(alignment: .leading, spacing: 12) {
                    HStack(spacing: 8) {
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(palette.bgSubtleColor)
                            .frame(width: 72, height: 22)
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(palette.bgSubtleColor)
                            .frame(width: 84, height: 22)
                    }
                    RoundedRectangle(cornerRadius: 6, style: .continuous)
                        .fill(palette.bgSubtleColor)
                        .frame(maxWidth: .infinity, minHeight: 20, maxHeight: 20)
                    RoundedRectangle(cornerRadius: 6, style: .continuous)
                        .fill(palette.bgSubtleColor)
                        .frame(width: 140, height: 14)
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(palette.bgSubtleColor)
                        .frame(maxWidth: .infinity, minHeight: 44, maxHeight: 44)
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(palette.bgSubtleColor)
                        .frame(maxWidth: .infinity, minHeight: 44, maxHeight: 44)
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
                .accessibilityIdentifier("myExams.skeletonCard.\(index)")
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(AppStrings.common("common.loading", locale: locale))
        .accessibilityIdentifier("myExams.loadingSkeleton")
        .reportMyExamsFrame("myExams.loadingSkeleton")
    }

    var accessibilityLoadingLabel: String {
        AppStrings.common("common.loading", locale: locale)
    }
}

struct MyExamsStaleBannerView: View {
    let lastLoadedAt: Date?
    let isReloading: Bool
    let locale: AppLocale
    let onRetry: () -> Void

    @Environment(\.statusPalette) private var statusPalette
    @Environment(\.brandPalette) private var palette

    private var titleText: String {
        MyExamsStrings.text("home.staleTitle", locale: locale)
    }

    private var descriptionText: String {
        if let lastLoadedAt {
            let formatted = MyExamsFormatting.formatTimeHm(lastLoadedAt)
            return MyExamsStrings.text(
                "home.staleDescription",
                locale: locale,
                replacements: ["time": formatted]
            )
        }
        return MyExamsStrings.text("home.staleDescriptionNoTime", locale: locale)
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
                    guard !isReloading else { return }
                    onRetry()
                } label: {
                    HStack(spacing: 6) {
                        if isReloading {
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
                .disabled(isReloading)
                .accessibilityLabel(AppStrings.common("common.retry", locale: locale))
                .accessibilityIdentifier("myExams.staleBanner.retryButton")
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
        .accessibilityIdentifier("myExams.staleBanner")
    }

    var renderedStrings: [String] {
        [titleText, descriptionText, AppStrings.common("common.retry", locale: locale)]
    }
}

struct CheckingExamCardView: View {
    let card: MyExamCard
    let supportContact: SupportContact
    let isReloading: Bool
    let locale: AppLocale
    let onViewOrder: (String) -> Void
    let onReload: () -> Void

    @Environment(\.brandPalette) private var palette
    @Environment(\.statusPalette) private var statusPalette

    private var checkingMessage: CheckingCardMessage {
        CheckingMessage.of(card, support: supportContact, locale: locale)
    }

    var body: some View {
        let message = checkingMessage
        VStack(alignment: .leading, spacing: 12) {
            MyExamsChipFlowLayout(horizontalSpacing: 6, verticalSpacing: 6) {
                MyExamsBadgeView(
                    text: card.examKind.displayName(for: locale),
                    style: .subtle
                )
                MyExamsBadgeView(
                    text: MyExamsStrings.text("home.checking.status", locale: locale),
                    style: .warning,
                    systemImage: "clock"
                )
            }

            Text(card.name)
                .font(.headline)
                .foregroundStyle(palette.textColor)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityIdentifier("myExams.checkingCard.title.\(card.code)")

            Text(message.text)
                .font(.subheadline)
                .foregroundStyle(palette.textSecondaryColor)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityIdentifier("myExams.checkingCard.message.\(card.code)")

            HStack(spacing: 8) {
                if let orderCode = message.viewOrderCode {
                    Button {
                        onViewOrder(orderCode)
                    } label: {
                        Text(MyExamsStrings.text("home.checking.viewOrder", locale: locale))
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(palette.textColor)
                            .frame(maxWidth: .infinity, minHeight: 44)
                            .padding(.horizontal, 12)
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
                    .accessibilityLabel(MyExamsStrings.text("home.checking.viewOrder", locale: locale))
                    .accessibilityIdentifier("myExams.checkingCard.viewOrder.\(orderCode)")
                    .reportMyExamsFrame("myExams.checkingCard.viewOrder.\(orderCode)")
                }

                Button {
                    guard !isReloading else { return }
                    onReload()
                } label: {
                    HStack(spacing: 6) {
                        if isReloading {
                            ProgressView()
                                .controlSize(.small)
                                .tint(palette.textColor)
                        } else {
                            Image(systemName: "arrow.clockwise")
                                .font(.subheadline.weight(.semibold))
                                .accessibilityHidden(true)
                        }
                        Text(MyExamsStrings.text("home.checking.reload", locale: locale))
                            .font(.subheadline.weight(.semibold))
                    }
                    .foregroundStyle(palette.textColor)
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .padding(.horizontal, 12)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(palette.bgSubtleColor)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .strokeBorder(palette.borderColor, lineWidth: 1)
                    )
                    .opacity(isReloading ? 0.65 : 1.0)
                    .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
                .buttonStyle(.plain)
                .disabled(isReloading)
                .accessibilityLabel(MyExamsStrings.text("home.checking.reload", locale: locale))
                .accessibilityIdentifier("myExams.checkingCard.reload.\(card.code)")
                .reportMyExamsFrame("myExams.checkingCard.reload.\(card.code)")
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
                .strokeBorder(statusPalette.warningColor.opacity(0.45), lineWidth: 1)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("myExams.checkingCard.\(card.id)")
    }

    var hasViewOrderButton: Bool {
        checkingMessage.viewOrderCode != nil
    }

    var renderedStrings: [String] {
        let message = checkingMessage
        var out = [
            card.examKind.displayName(for: locale),
            MyExamsStrings.text("home.checking.status", locale: locale),
            card.name,
            message.text,
            MyExamsStrings.text("home.checking.reload", locale: locale)
        ]
        if message.viewOrderCode != nil {
            out.append(MyExamsStrings.text("home.checking.viewOrder", locale: locale))
        }
        return out
    }
}

struct MyExamCardView: View {
    let card: MyExamCard
    let locale: AppLocale
    let onAction: (MyExamCardButtonAction) -> Void

    @Environment(\.brandPalette) private var palette
    @Environment(\.statusPalette) private var statusPalette

    private var buttons: MyExamCardButtons {
        CardButtons.of(card)
    }

    private var notes: MyExamCardNotes {
        CardNotes.of(card)
    }

    private var sourceLabel: String {
        if card.isClassAssigned {
            if let className = card.trimmedClassName {
                return MyExamsStrings.text(
                    "home.card.classAssigned",
                    locale: locale,
                    replacements: ["className": className]
                )
            }
            return MyExamsStrings.text("home.card.classAssignedNoName", locale: locale)
        }
        return MyExamsStrings.text("home.card.purchased", locale: locale)
    }

    private var statusBadgeStyle: MyExamsBadgeStyle {
        switch card.state {
        case "IN_PROGRESS": .info
        case "CAN_START": .success
        case "EXHAUSTED": .outlined
        default: .subtle
        }
    }

    var body: some View {
        let resolvedButtons = buttons
        let resolvedNotes = notes

        VStack(alignment: .leading, spacing: 12) {
            MyExamsChipFlowLayout(horizontalSpacing: 6, verticalSpacing: 6) {
                MyExamsBadgeView(
                    text: card.examKind.displayName(for: locale),
                    style: .subtle
                )
                MyExamsBadgeView(
                    text: sourceLabel,
                    style: card.isClassAssigned ? .info : .primary,
                    systemImage: card.isClassAssigned ? "person.2" : nil
                )
                if card.isReview {
                    MyExamsBadgeView(
                        text: AppStrings.common("storefront.review", locale: locale),
                        style: .info
                    )
                }
                MyExamsBadgeView(
                    text: MyExamsStrings.examStateLabel(for: card.state, locale: locale),
                    style: statusBadgeStyle
                )
            }

            Text(card.name)
                .font(.headline)
                .foregroundStyle(palette.textColor)
                .lineLimit(2)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityIdentifier("myExams.card.title.\(card.code)")
                .reportMyExamsFrame("myExams.card.title.\(card.code)")

            MyExamsChipFlowLayout(horizontalSpacing: 6, verticalSpacing: 6) {
                MyExamsBadgeView(
                    text: MyExamsStrings.formatLabel(for: card.format, locale: locale),
                    style: .outlined
                )
                if let scopeText = card.scopeLabel?.localized(for: locale) {
                    MyExamsBadgeView(
                        text: scopeText,
                        style: .outlined
                    )
                }
            }

            if let specsLine = MyExamsFormatting.cardSpecsLine(for: card, locale: locale) {
                Text(specsLine)
                    .font(.subheadline)
                    .foregroundStyle(palette.textMutedColor)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("myExams.card.specs.\(card.code)")
            }

            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(MyExamsStrings.text("home.card.attemptsLeft", locale: locale))
                    .font(.subheadline)
                    .foregroundStyle(palette.textSecondaryColor)
                Spacer(minLength: 8)
                Text(String(card.attemptsLeft))
                    .font(.title3.weight(.bold).monospacedDigit())
                    .foregroundStyle(palette.textColor)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(palette.bgPageColor)
            )
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier("myExams.card.attemptsLeft.\(card.code)")

            VStack(alignment: .leading, spacing: 8) {
                if let attemptId = resolvedButtons.continueAttemptId {
                    primaryActionButton(
                        title: MyExamsStrings.text("home.actions.continue", locale: locale),
                        identifier: "myExams.card.action.continue.\(card.code)"
                    ) {
                        onAction(.continue(attemptId: attemptId))
                    }
                } else if let entitlementId = resolvedButtons.startEntitlementId {
                    primaryActionButton(
                        title: MyExamsStrings.text("home.actions.start", locale: locale),
                        identifier: "myExams.card.action.start.\(card.code)"
                    ) {
                        onAction(.start(entitlementId: entitlementId))
                    }
                }

                if let rebuyCode = resolvedButtons.rebuyCode {
                    primaryActionButton(
                        title: MyExamsStrings.text("home.actions.rebuy", locale: locale),
                        identifier: "myExams.card.action.rebuy.\(card.code)"
                    ) {
                        onAction(.rebuy(code: rebuyCode))
                    }
                }

                ForEach(resolvedNotes.middleNotes, id: \.self) { note in
                    Text(MyExamsStrings.text(note.localizationKey, locale: locale))
                        .font(.footnote)
                        .foregroundStyle(palette.textMutedColor)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityIdentifier("myExams.card.note.\(note.localizationKey).\(card.code)")
                        .reportMyExamsFrame("myExams.card.note.\(note.localizationKey).\(card.code)")
                }

                if resolvedButtons.hasSecondaryRow {
                    secondaryButtonsRow(buttons: resolvedButtons)
                }

                if let pendingOrderCode = resolvedNotes.pendingTopUpOrderCode {
                    Text(
                        MyExamsStrings.text(
                            "home.notes.pendingTopUp",
                            locale: locale,
                            replacements: ["orderCode": pendingOrderCode]
                        )
                    )
                    .font(.footnote.weight(.medium))
                    .foregroundStyle(statusPalette.warningColor)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("myExams.card.note.pendingTopUp.\(card.code)")
                    .reportMyExamsFrame("myExams.card.note.pendingTopUp.\(card.code)")
                }

                if let buyMoreCode = resolvedButtons.buyMoreCode {
                    secondaryActionButton(
                        title: MyExamsStrings.text("home.actions.buyMore", locale: locale),
                        identifier: "myExams.card.action.buyMore.\(card.code)"
                    ) {
                        onAction(.buyMore(code: buyMoreCode))
                    }
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
        .accessibilityIdentifier("myExams.card.\(card.id)")
    }

    @ViewBuilder
    private func secondaryButtonsRow(buttons: MyExamCardButtons) -> some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: 8) {
                secondaryButtonsItems(buttons: buttons)
            }
            VStack(spacing: 8) {
                secondaryButtonsItems(buttons: buttons)
            }
        }
    }

    @ViewBuilder
    private func secondaryButtonsItems(buttons: MyExamCardButtons) -> some View {
        if let resultAttemptId = buttons.viewResultAttemptId {
            secondaryActionButton(
                title: MyExamsStrings.text("home.actions.viewResult", locale: locale),
                identifier: "myExams.card.action.viewResult.\(card.code)"
            ) {
                onAction(.viewResult(attemptId: resultAttemptId))
            }
        }
        if buttons.showsHistory {
            secondaryActionButton(
                title: MyExamsStrings.text("home.actions.history", locale: locale),
                identifier: "myExams.card.action.history.\(card.code)"
            ) {
                onAction(.history)
            }
        }
    }

    private func primaryActionButton(title: String, identifier: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(palette.onPrimaryColor)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .frame(maxWidth: .infinity, minHeight: 44)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(palette.primaryFillColor)
                )
                .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(title)
        .accessibilityIdentifier(identifier)
        .reportMyExamsFrame(identifier)
    }

    private func secondaryActionButton(title: String, identifier: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(palette.textColor)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .frame(maxWidth: .infinity, minHeight: 44)
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
        .accessibilityLabel(title)
        .accessibilityIdentifier(identifier)
        .reportMyExamsFrame(identifier)
    }

    var renderedStrings: [String] {
        let resolvedButtons = buttons
        let resolvedNotes = notes
        var out: [String] = [
            card.examKind.displayName(for: locale),
            sourceLabel
        ]
        if card.isReview {
            out.append(AppStrings.common("storefront.review", locale: locale))
        }
        out.append(MyExamsStrings.examStateLabel(for: card.state, locale: locale))
        out.append(card.name)
        out.append(MyExamsStrings.formatLabel(for: card.format, locale: locale))
        if let scopeText = card.scopeLabel?.localized(for: locale) {
            out.append(scopeText)
        }
        if let specs = MyExamsFormatting.cardSpecsLine(for: card, locale: locale) {
            out.append(specs)
        }
        out.append(MyExamsStrings.text("home.card.attemptsLeft", locale: locale))
        out.append(String(card.attemptsLeft))
        if resolvedButtons.continueAttemptId != nil {
            out.append(MyExamsStrings.text("home.actions.continue", locale: locale))
        } else if resolvedButtons.startEntitlementId != nil {
            out.append(MyExamsStrings.text("home.actions.start", locale: locale))
        }
        if resolvedButtons.rebuyCode != nil {
            out.append(MyExamsStrings.text("home.actions.rebuy", locale: locale))
        }
        for note in resolvedNotes.middleNotes {
            out.append(MyExamsStrings.text(note.localizationKey, locale: locale))
        }
        if resolvedButtons.viewResultAttemptId != nil {
            out.append(MyExamsStrings.text("home.actions.viewResult", locale: locale))
        }
        if resolvedButtons.showsHistory {
            out.append(MyExamsStrings.text("home.actions.history", locale: locale))
        }
        if let pendingOrderCode = resolvedNotes.pendingTopUpOrderCode {
            out.append(MyExamsStrings.text("home.notes.pendingTopUp", locale: locale, replacements: ["orderCode": pendingOrderCode]))
        }
        if resolvedButtons.buyMoreCode != nil {
            out.append(MyExamsStrings.text("home.actions.buyMore", locale: locale))
        }
        return out
    }
}

struct BundleGroupSectionView: View {
    let group: BundleGroup
    let isRegularWidth: Bool
    let locale: AppLocale
    let focusedBundleId: AccessibilityFocusState<String?>.Binding
    let onViewBundle: (String) -> Void
    let onCardAction: (MyExamCardButtonAction) -> Void
    let onJumpToBundle: (String) -> Void

    @Environment(\.brandPalette) private var palette

    private var columns: [GridItem] {
        if isRegularWidth {
            return [
                GridItem(.flexible(), spacing: 16, alignment: .top),
                GridItem(.flexible(), spacing: 16, alignment: .top)
            ]
        }
        return [GridItem(.flexible(), spacing: 16, alignment: .top)]
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            VStack(alignment: .leading, spacing: 6) {
                HStack(alignment: .center, spacing: 8) {
                    Image(systemName: "square.stack.3d.up")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(palette.primaryTextColor)
                        .accessibilityHidden(true)

                    Text(group.ref.name)
                        .font(.headline.weight(.bold))
                        .foregroundStyle(palette.textColor)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityAddTraits(.isHeader)
                        .accessibilityFocused(focusedBundleId, equals: group.ref.bundleId)
                        .accessibilityIdentifier("myExams.bundleGroup.heading.\(group.ref.bundleId)")

                    Spacer(minLength: 0)
                }

                HStack(alignment: .center, spacing: 8) {
                    MyExamsBadgeView(
                        text: MyExamsStrings.plural("home.examCount", count: group.totalCount, locale: locale),
                        style: .subtle
                    )
                    Text(group.ref.code)
                        .font(.footnote)
                        .foregroundStyle(palette.textMutedColor)
                        .fixedSize(horizontal: false, vertical: true)

                    Spacer(minLength: 8)

                    Button {
                        onViewBundle(group.ref.code)
                    } label: {
                        Text(MyExamsStrings.text("bundle.home.viewBundle", locale: locale))
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(palette.primaryTextColor)
                            .padding(.horizontal, 6)
                            .frame(minWidth: 44, minHeight: 44)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(MyExamsStrings.text("bundle.home.viewBundle", locale: locale))
                    .accessibilityIdentifier("myExams.bundleGroup.viewBundle.\(group.ref.bundleId)")
                    .reportMyExamsFrame("myExams.bundleGroup.viewBundle.\(group.ref.bundleId)")
                }
            }
            .padding(.horizontal, 4)

            if !group.cards.isEmpty {
                LazyVGrid(columns: columns, alignment: .leading, spacing: 16) {
                    ForEach(group.cards) { card in
                        MyExamCardView(card: card, locale: locale, onAction: onCardAction)
                    }
                }
            }

            if !group.crossRefs.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    ForEach(group.crossRefs) { ref in
                        crossReferenceRow(ref)
                    }
                }
            }

            Text(MyExamsStrings.text("bundle.home.groupFootnote", locale: locale))
                .font(.footnote)
                .foregroundStyle(palette.textMutedColor)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 4)
                .accessibilityIdentifier("myExams.bundleGroup.footnote.\(group.ref.bundleId)")
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(palette.bgSubtleColor)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .strokeBorder(palette.borderColor, lineWidth: 1)
        )
        .id("myExams.bundleGroup.\(group.ref.bundleId)")
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("myExams.bundleGroup.\(group.ref.bundleId)")
    }

    private func crossReferenceRow(_ ref: BundleCrossReference) -> some View {
        let parts = MyExamsStrings.crossReferenceParts(
            examName: ref.examName,
            bundleName: ref.primaryBundle.name,
            locale: locale
        )

        return Button {
            onJumpToBundle(ref.primaryBundle.bundleId)
        } label: {
            HStack(alignment: .top, spacing: 10) {
                Image(systemName: "arrow.up.right")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(palette.textMutedColor)
                    .padding(.top, 3)
                    .accessibilityHidden(true)

                (Text(parts.prefix)
                    .foregroundColor(palette.textSecondaryColor)
                 + Text(parts.examName)
                    .fontWeight(.semibold)
                    .foregroundColor(palette.textColor)
                 + Text(parts.middle)
                    .foregroundColor(palette.textSecondaryColor)
                 + Text(parts.bundleName)
                    .fontWeight(.semibold)
                    .foregroundColor(palette.primaryTextColor)
                    .underline()
                 + Text(parts.suffix)
                    .foregroundColor(palette.textSecondaryColor))
                    .font(.subheadline)
                    .multilineTextAlignment(.leading)
                    .fixedSize(horizontal: false, vertical: true)

                Spacer(minLength: 0)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(palette.bgSurfaceColor)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .strokeBorder(palette.borderColor, lineWidth: 1)
            )
            .contentShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(parts.plainText)
        .accessibilityIdentifier("myExams.crossRef.\(group.ref.bundleId).\(ref.examCode)")
        .reportMyExamsFrame("myExams.crossRef.\(group.ref.bundleId).\(ref.examCode)")
    }

    var renderedStrings: [String] {
        var out: [String] = [
            group.ref.name,
            MyExamsStrings.plural("home.examCount", count: group.totalCount, locale: locale),
            group.ref.code,
            MyExamsStrings.text("bundle.home.viewBundle", locale: locale)
        ]
        for card in group.cards {
            out.append(contentsOf: MyExamCardView(card: card, locale: locale, onAction: { _ in }).renderedStrings)
        }
        for ref in group.crossRefs {
            out.append(
                MyExamsStrings.crossReferenceParts(
                    examName: ref.examName,
                    bundleName: ref.primaryBundle.name,
                    locale: locale
                ).plainText
            )
        }
        out.append(MyExamsStrings.text("bundle.home.groupFootnote", locale: locale))
        return out
    }
}
