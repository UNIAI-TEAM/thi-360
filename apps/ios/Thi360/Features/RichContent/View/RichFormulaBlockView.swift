import SwiftUI
import UIKit

final class HorizontalFormulaScrollView: UIScrollView, UIGestureRecognizerDelegate {
    override init(frame: CGRect) {
        super.init(frame: frame)
        showsHorizontalScrollIndicator = true
        showsVerticalScrollIndicator = false
        alwaysBounceVertical = false
        alwaysBounceHorizontal = false
        bounces = false
        isDirectionalLockEnabled = true
        panGestureRecognizer.delegate = self
    }

    required init?(coder: NSCoder) {
        nil
    }

    override func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        if gestureRecognizer === panGestureRecognizer {
            let velocity = panGestureRecognizer.velocity(in: self)
            return abs(velocity.x) >= abs(velocity.y)
        }
        return super.gestureRecognizerShouldBegin(gestureRecognizer)
    }

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool {
        if gestureRecognizer === panGestureRecognizer {
            let velocity = panGestureRecognizer.velocity(in: self)
            if abs(velocity.x) >= abs(velocity.y) {
                return false
            }
        }
        return true
    }

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldBeRequiredToFailBy otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool {
        guard gestureRecognizer === panGestureRecognizer else { return false }
        if otherGestureRecognizer is UIPanGestureRecognizer {
            let velocity = panGestureRecognizer.velocity(in: self)
            return abs(velocity.x) >= abs(velocity.y)
        }
        return false
    }
}

struct FormulaHorizontalContainerRepresentable<Content: View>: UIViewRepresentable {
    let contentSize: CGSize
    let availableWidth: CGFloat
    let centerIfFits: Bool
    let accessibilityIdentifier: String
    let accessibilityLabel: String
    @ViewBuilder let content: () -> Content

    final class Coordinator {
        var hostingController: UIHostingController<Content>?
    }

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIView(context: Context) -> HorizontalFormulaScrollView {
        let scrollView = HorizontalFormulaScrollView()
        let host = UIHostingController(rootView: content())
        host.view.backgroundColor = .clear
        scrollView.addSubview(host.view)
        context.coordinator.hostingController = host
        return scrollView
    }

    func updateUIView(_ scrollView: HorizontalFormulaScrollView, context: Context) {
        guard let host = context.coordinator.hostingController else { return }
        host.rootView = content()
        let width = max(availableWidth, 1)
        let naturalWidth = max(contentSize.width, 1)
        let naturalHeight = max(contentSize.height, 1)
        let needsScroll = naturalWidth > width + 0.5

        scrollView.isScrollEnabled = needsScroll
        scrollView.accessibilityIdentifier = accessibilityIdentifier
        scrollView.accessibilityLabel = accessibilityLabel

        if needsScroll {
            host.view.frame = CGRect(x: 0, y: 0, width: naturalWidth, height: naturalHeight)
            scrollView.contentSize = CGSize(width: naturalWidth, height: naturalHeight)
        } else {
            let x = centerIfFits ? max((width - naturalWidth) / 2, 0) : 0
            host.view.frame = CGRect(x: x, y: 0, width: naturalWidth, height: naturalHeight)
            scrollView.contentSize = CGSize(width: width, height: naturalHeight)
            if scrollView.contentOffset.x != 0 {
                scrollView.setContentOffset(.zero, animated: false)
            }
        }
    }

    func sizeThatFits(
        _ proposal: ProposedViewSize,
        uiView: HorizontalFormulaScrollView,
        context: Context
    ) -> CGSize? {
        let width = max(proposal.width ?? availableWidth, 1)
        return CGSize(width: width, height: max(ceil(contentSize.height), 1))
    }
}

struct RichFormulaBlockView: View {
    let formula: Formula
    let tokens: RichContentTokens
    let formulaEngine: any FormulaEngine
    let availableWidth: CGFloat
    let centered: Bool

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        let traits = UITraitCollection(preferredContentSizeCategory: uiContentSizeCategory)
        let pointSize = RichDynamicTypeFont.scaledBodyPointSize(baseSize: 17, traitCollection: traits)
        let rendered = formulaEngine.render(formula, pointSize: pointSize, color: tokens.textUIColor)

        switch rendered {
        case let .rendered(image, width, ascent, descent):
            let height = max(ascent + descent, 1)
            let verticalPad: CGFloat = 6
            FormulaHorizontalContainerRepresentable(
                contentSize: CGSize(width: ceil(width) + 4, height: ceil(height) + verticalPad * 2),
                availableWidth: availableWidth,
                centerIfFits: centered,
                accessibilityIdentifier: formula.display == .block
                    ? "richContent.formula.block.scroll"
                    : "richContent.formula.inlineOverflow.scroll",
                accessibilityLabel: formula.delimitedSource
            ) {
                Image(uiImage: image)
                    .resizable()
                    .frame(width: width, height: height)
                    .padding(.vertical, verticalPad)
                    .padding(.horizontal, 2)
                    .accessibilityLabel(formula.delimitedSource)
            }
            .frame(maxWidth: .infinity, alignment: centered ? .center : .leading)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(formula.delimitedSource)
            .accessibilityIdentifier(
                formula.display == .block
                    ? "richContent.formula.block"
                    : "richContent.formula.inlineOverflow"
            )

        case let .unsupported(source):
            let monoFont = UIFont.monospacedSystemFont(ofSize: max(pointSize * 0.8125, 12), weight: .regular)
            let measured = (source as NSString).size(withAttributes: [.font: monoFont])
            let badgeSize = CGSize(width: ceil(measured.width) + 20, height: ceil(measured.height) + 14)

            FormulaHorizontalContainerRepresentable(
                contentSize: badgeSize,
                availableWidth: availableWidth,
                centerIfFits: centered,
                accessibilityIdentifier: "richContent.formula.fallback.scroll",
                accessibilityLabel: source
            ) {
                Text(source)
                    .font(Font(monoFont))
                    .foregroundStyle(tokens.status.warningColor)
                    .fixedSize(horizontal: true, vertical: false)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 5)
                    .background(
                        RoundedRectangle(cornerRadius: 6, style: .continuous)
                            .fill(tokens.status.warningBgColor)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 6, style: .continuous)
                            .strokeBorder(tokens.status.warningColor, lineWidth: 1)
                    )
                    .padding(2)
            }
            .frame(maxWidth: .infinity, alignment: centered ? .center : .leading)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(source)
            .accessibilityIdentifier("richContent.formula.fallback")
        }
    }

    private var uiContentSizeCategory: UIContentSizeCategory {
        UIContentSizeCategory(dynamicTypeSize)
    }
}
