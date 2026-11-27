import SwiftUI

struct TwoColumnLayout<Start: View, End: View>: View {
    let spacing: CGFloat
    let startRatio: CGFloat
    @ViewBuilder let start: () -> Start
    @ViewBuilder let end: () -> End

    @Environment(\.shellWidth) private var shellWidth

    init(
        spacing: CGFloat = 16,
        startRatio: CGFloat = 0.5,
        @ViewBuilder start: @escaping () -> Start,
        @ViewBuilder end: @escaping () -> End
    ) {
        self.spacing = spacing
        self.startRatio = startRatio
        self.start = start
        self.end = end
    }

    var body: some View {
        Group {
            if shellWidth == .twoColumn {
                GeometryReader { proxy in
                    let available = max(0, proxy.size.width - spacing)
                    let clampedRatio = min(max(startRatio, 0.25), 0.75)
                    HStack(alignment: .top, spacing: spacing) {
                        start()
                            .frame(width: available * clampedRatio, alignment: .topLeading)
                            .accessibilityElement(children: .contain)
                            .accessibilityIdentifier("layout.twoColumn.start")

                        end()
                            .frame(width: available * (1 - clampedRatio), alignment: .topLeading)
                            .accessibilityElement(children: .contain)
                            .accessibilityIdentifier("layout.twoColumn.end")
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                }
                .accessibilityIdentifier("layout.twoColumn.horizontal")
            } else {
                VStack(alignment: .leading, spacing: spacing) {
                    start()
                        .frame(maxWidth: .infinity, alignment: .topLeading)
                        .accessibilityElement(children: .contain)
                        .accessibilityIdentifier("layout.twoColumn.start")

                    end()
                        .frame(maxWidth: .infinity, alignment: .topLeading)
                        .accessibilityElement(children: .contain)
                        .accessibilityIdentifier("layout.twoColumn.end")
                }
                .accessibilityIdentifier("layout.twoColumn.vertical")
            }
        }
    }
}
