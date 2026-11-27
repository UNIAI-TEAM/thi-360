import SwiftUI

struct ListDetailLayout<Selection: Hashable, ListContent: View, DetailContent: View, EmptyDetail: View>: View {
    @Binding var selection: Selection?
    let listWidthFraction: CGFloat
    @ViewBuilder let list: () -> ListContent
    @ViewBuilder let detail: (Selection) -> DetailContent
    @ViewBuilder let emptyDetail: () -> EmptyDetail

    @Environment(\.shellWidth) private var shellWidth
    @Environment(\.brandPalette) private var palette

    init(
        selection: Binding<Selection?>,
        listWidthFraction: CGFloat = 0.42,
        @ViewBuilder list: @escaping () -> ListContent,
        @ViewBuilder detail: @escaping (Selection) -> DetailContent,
        @ViewBuilder emptyDetail: @escaping () -> EmptyDetail
    ) {
        self._selection = selection
        self.listWidthFraction = listWidthFraction
        self.list = list
        self.detail = detail
        self.emptyDetail = emptyDetail
    }

    var body: some View {
        Group {
            if shellWidth == .twoColumn {
                GeometryReader { proxy in
                    let clampedFraction = min(max(listWidthFraction, 0.3), 0.55)
                    let leftWidth = max(280, proxy.size.width * clampedFraction)
                    HStack(spacing: 0) {
                        list()
                            .frame(width: leftWidth, height: proxy.size.height)
                            .accessibilityElement(children: .contain)
                            .accessibilityIdentifier("layout.listDetail.listPane")

                        Rectangle()
                            .fill(palette.borderColor)
                            .frame(width: 1)

                        Group {
                            if let selected = selection {
                                detail(selected)
                            } else {
                                emptyDetail()
                            }
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .accessibilityElement(children: .contain)
                        .accessibilityIdentifier("layout.listDetail.detailPane")
                    }
                }
                .accessibilityIdentifier("layout.listDetail.twoColumn")
            } else {
                // Keep list mounted underneath so scroll offset survives detail push/pop and width transitions.
                ZStack {
                    list()
                        .opacity(selection == nil ? 1 : 0)
                        .allowsHitTesting(selection == nil)
                        .accessibilityHidden(selection != nil)
                        .accessibilityElement(children: .contain)
                        .accessibilityIdentifier("layout.listDetail.listPane")

                    if let selected = selection {
                        detail(selected)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .background(palette.bgPageColor.ignoresSafeArea())
                            .accessibilityElement(children: .contain)
                            .accessibilityIdentifier("layout.listDetail.singleDetail")
                    }
                }
                .accessibilityIdentifier("layout.listDetail.singleColumn")
            }
        }
    }
}
