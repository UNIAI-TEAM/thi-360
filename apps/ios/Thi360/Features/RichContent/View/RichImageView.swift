import SwiftUI
import UIKit

enum RichImageLoadState {
    case loading
    case loaded(UIImage)
    case failed
}

private struct RichLoadedUIImageView: UIViewRepresentable {
    let image: UIImage
    let alt: String
    let targetSize: CGSize
    let exposesAccessibilityElement: Bool

    func makeUIView(context: Context) -> UIImageView {
        let imageView = UIImageView(image: image)
        imageView.contentMode = .scaleAspectFit
        imageView.clipsToBounds = true
        imageView.isUserInteractionEnabled = false
        imageView.overrideUserInterfaceStyle = .light
        imageView.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        imageView.setContentCompressionResistancePriority(.defaultLow, for: .vertical)
        imageView.setContentHuggingPriority(.defaultLow, for: .horizontal)
        imageView.setContentHuggingPriority(.defaultLow, for: .vertical)
        imageView.isAccessibilityElement = exposesAccessibilityElement
        imageView.accessibilityElementsHidden = !exposesAccessibilityElement
        imageView.accessibilityLabel = exposesAccessibilityElement ? alt : nil
        imageView.accessibilityTraits = .image
        return imageView
    }

    func updateUIView(_ uiView: UIImageView, context: Context) {
        uiView.image = image
        uiView.isAccessibilityElement = exposesAccessibilityElement
        uiView.accessibilityElementsHidden = !exposesAccessibilityElement
        uiView.accessibilityLabel = exposesAccessibilityElement ? alt : nil
    }

    func sizeThatFits(_ proposal: ProposedViewSize, uiView: UIImageView, context: Context) -> CGSize? {
        CGSize(
            width: proposal.width ?? targetSize.width,
            height: proposal.height ?? targetSize.height
        )
    }
}

private struct RichImageErrorButton: UIViewRepresentable {
    let alt: String
    let errorMessage: String
    let tokens: RichContentTokens
    let targetWidth: CGFloat
    let onRetry: () -> Void

    final class Coordinator: NSObject {
        var onRetry: () -> Void

        init(onRetry: @escaping () -> Void) {
            self.onRetry = onRetry
        }

        @objc func handleTap() {
            onRetry()
        }
    }

    final class ErrorCardButton: UIButton {
        let iconView = UIImageView(image: UIImage(systemName: "photo.badge.exclamationmark"))
        let altLabel = UILabel()
        let messageLabel = UILabel()
        private let stack = UIStackView()

        override init(frame: CGRect) {
            super.init(frame: frame)
            layer.cornerRadius = 10
            layer.borderWidth = 1
            clipsToBounds = true

            iconView.contentMode = .scaleAspectFit
            iconView.preferredSymbolConfiguration = UIImage.SymbolConfiguration(textStyle: .title2)
            iconView.isAccessibilityElement = false

            altLabel.font = .preferredFont(forTextStyle: .footnote)
            altLabel.adjustsFontForContentSizeCategory = true
            altLabel.textAlignment = .center
            altLabel.numberOfLines = 0
            altLabel.isAccessibilityElement = false

            messageLabel.font = .preferredFont(forTextStyle: .footnote)
            messageLabel.adjustsFontForContentSizeCategory = true
            messageLabel.textAlignment = .center
            messageLabel.numberOfLines = 0
            messageLabel.isAccessibilityElement = false

            stack.axis = .vertical
            stack.alignment = .center
            stack.spacing = 8
            stack.isUserInteractionEnabled = false
            stack.translatesAutoresizingMaskIntoConstraints = false
            stack.addArrangedSubview(iconView)
            stack.addArrangedSubview(altLabel)
            stack.addArrangedSubview(messageLabel)

            addSubview(stack)
            NSLayoutConstraint.activate([
                stack.topAnchor.constraint(equalTo: topAnchor, constant: 14),
                stack.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -14),
                stack.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 14),
                stack.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -14),
                heightAnchor.constraint(greaterThanOrEqualToConstant: 88)
            ])

            accessibilityIdentifier = "richContent.image.error"
            accessibilityTraits = .button
            isAccessibilityElement = true
        }

        required init?(coder: NSCoder) {
            nil
        }

        func configure(alt: String, errorMessage: String, tokens: RichContentTokens) {
            let trimmedAlt = alt.trimmingCharacters(in: .whitespacesAndNewlines)
            backgroundColor = UIColor(hex: tokens.brand.bgSubtle)
            layer.borderColor = UIColor(hex: tokens.brand.borderStrong).cgColor
            iconView.tintColor = tokens.textMutedUIColor
            altLabel.textColor = tokens.textSecondaryUIColor
            altLabel.text = trimmedAlt
            altLabel.isHidden = trimmedAlt.isEmpty
            messageLabel.textColor = tokens.textMutedUIColor
            messageLabel.text = errorMessage
            accessibilityLabel = trimmedAlt.isEmpty ? errorMessage : "\(trimmedAlt). \(errorMessage)"
        }
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(onRetry: onRetry)
    }

    func makeUIView(context: Context) -> ErrorCardButton {
        let button = ErrorCardButton()
        button.configure(alt: alt, errorMessage: errorMessage, tokens: tokens)
        button.addTarget(context.coordinator, action: #selector(Coordinator.handleTap), for: .touchUpInside)
        return button
    }

    func updateUIView(_ uiView: ErrorCardButton, context: Context) {
        context.coordinator.onRetry = onRetry
        uiView.configure(alt: alt, errorMessage: errorMessage, tokens: tokens)
    }

    func sizeThatFits(_ proposal: ProposedViewSize, uiView: ErrorCardButton, context: Context) -> CGSize? {
        let width = max(proposal.width ?? targetWidth, 1)
        let fitting = uiView.systemLayoutSizeFitting(
            CGSize(width: width, height: UIView.layoutFittingCompressedSize.height),
            withHorizontalFittingPriority: .required,
            verticalFittingPriority: .fittingSizeLevel
        )
        return CGSize(width: width, height: max(ceil(fitting.height), 88))
    }
}

struct RichImageView: View {
    let imageData: RichImageData
    let widthSpec: FigureImageWidth
    let zoomable: Bool
    let captionText: String?
    let assets: AttemptAssetSource?
    let tokens: RichContentTokens
    let locale: AppLocale
    let availableWidth: CGFloat

    @State private var loadState: RichImageLoadState
    @State private var retryNonce: Int = 0
    @State private var isLoadingInFlight = false
    @State private var isRetryQueued = false
    @State private var isPresentingZoom = false

    init(
        imageData: RichImageData,
        widthSpec: FigureImageWidth,
        zoomable: Bool,
        captionText: String?,
        assets: AttemptAssetSource?,
        tokens: RichContentTokens,
        locale: AppLocale,
        availableWidth: CGFloat
    ) {
        self.imageData = imageData
        self.widthSpec = widthSpec
        self.zoomable = zoomable
        self.captionText = captionText
        self.assets = assets
        self.tokens = tokens
        self.locale = locale
        self.availableWidth = availableWidth

        if case .blocked = imageData.ref {
            _loadState = State(initialValue: .failed)
        } else if let assets,
                  let cached = assets.cachedData(for: imageData.ref),
                  let cachedImage = UIImage(data: cached)
        {
            _loadState = State(initialValue: .loaded(cachedImage))
        } else {
            _loadState = State(initialValue: .loading)
        }
    }

    private var targetWidth: CGFloat {
        max(floor(availableWidth * widthSpec.columnFraction), 1)
    }

    var body: some View {
        contentBody
            .frame(width: targetWidth)
            .frame(maxWidth: availableWidth, alignment: .center)
            .task(id: "\(imageData.ref.hashValue)-\(retryNonce)") {
                await performLoad()
            }
            .fullScreenCover(isPresented: $isPresentingZoom) {
                if case let .loaded(uiImage) = loadState {
                    ImageZoomViewer(
                        image: uiImage,
                        alt: imageData.alt,
                        caption: captionText,
                        tokens: tokens,
                        locale: locale,
                        onClose: { isPresentingZoom = false }
                    )
                }
            }
    }

    @ViewBuilder
    private var contentBody: some View {
        switch loadState {
        case .loading:
            loadingPlaceholder
                .accessibilityLabel(imageData.alt)
                .accessibilityIdentifier("richContent.image.loading")

        case let .loaded(uiImage):
            let aspectRatio: CGFloat = {
                if uiImage.size.width > 0 && uiImage.size.height > 0 {
                    return uiImage.size.width / uiImage.size.height
                }
                return imageData.aspectRatio ?? 1.5
            }()
            let targetHeight = max(floor(targetWidth / aspectRatio), 24)

            if zoomable {
                Button {
                    isPresentingZoom = true
                } label: {
                    ZStack(alignment: .bottomTrailing) {
                        RichLoadedUIImageView(
                            image: uiImage,
                            alt: imageData.alt,
                            targetSize: CGSize(width: targetWidth, height: targetHeight),
                            exposesAccessibilityElement: false
                        )
                        .allowsHitTesting(false)
                        .frame(width: targetWidth, height: targetHeight)
                        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))

                        Image(systemName: "arrow.up.left.and.arrow.down.right")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(tokens.brand.textColor)
                            .frame(width: 30, height: 30)
                            .background(.ultraThinMaterial, in: Circle())
                            .padding(8)
                            .accessibilityHidden(true)
                    }
                    .frame(width: targetWidth, height: targetHeight)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityElement(children: .ignore)
                .accessibilityAddTraits(.isButton)
                .accessibilityLabel(imageData.alt.isEmpty ? RichContentStrings.imageZoomButton(locale: locale) : imageData.alt)
                .accessibilityHint(RichContentStrings.imageZoomButton(locale: locale))
                .accessibilityIdentifier("richContent.image.zoomable")
            } else {
                RichLoadedUIImageView(
                    image: uiImage,
                    alt: imageData.alt,
                    targetSize: CGSize(width: targetWidth, height: targetHeight),
                    exposesAccessibilityElement: true
                )
                .frame(width: targetWidth, height: targetHeight)
                .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                .accessibilityIdentifier("richContent.image.static")
            }

        case .failed:
            RichImageErrorButton(
                alt: imageData.alt,
                errorMessage: RichContentStrings.imageLoadError(locale: locale),
                tokens: tokens,
                targetWidth: targetWidth,
                onRetry: {
                    guard !isLoadingInFlight, !isRetryQueued else { return }
                    isRetryQueued = true
                    retryNonce += 1
                }
            )
            .frame(width: targetWidth)
        }
    }

    @ViewBuilder
    private var loadingPlaceholder: some View {
        let placeholderHeight: CGFloat = {
            if let ratio = imageData.aspectRatio {
                return max(targetWidth / ratio, 24)
            }
            return 22
        }()

        RoundedRectangle(cornerRadius: 8, style: .continuous)
            .fill(tokens.brand.bgSubtleColor)
            .frame(width: targetWidth, height: placeholderHeight)
            .overlay {
                ProgressView()
                    .tint(tokens.brand.textMutedColor)
            }
    }

    @MainActor
    private func performLoad() async {
        isRetryQueued = false
        guard !isLoadingInFlight else { return }
        if case .blocked = imageData.ref {
            loadState = .failed
            return
        }
        guard let assets else {
            loadState = .failed
            return
        }

        if let cached = assets.cachedData(for: imageData.ref),
           let image = UIImage(data: cached)
        {
            loadState = .loaded(image)
            return
        }

        isLoadingInFlight = true
        loadState = .loading
        defer { isLoadingInFlight = false }

        do {
            let data = try await assets.load(imageData.ref)
            if let image = UIImage(data: data) {
                loadState = .loaded(image)
            } else {
                loadState = .failed
            }
        } catch {
            loadState = .failed
        }
    }
}

struct ImageZoomViewer: View {
    let image: UIImage
    let alt: String
    let caption: String?
    let tokens: RichContentTokens
    let locale: AppLocale
    let onClose: () -> Void

    @State private var currentZoomScale: CGFloat = 1.0
    @State private var dragDismissOffset: CGFloat = 0

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onClose) {
                    HStack(spacing: 6) {
                        Image(systemName: "xmark")
                            .font(.body.weight(.semibold))
                        Text(RichContentStrings.closeLabel(locale: locale))
                            .font(.body.weight(.semibold))
                    }
                    .foregroundStyle(tokens.brand.textColor)
                    .padding(.horizontal, 14)
                    .frame(minWidth: 44, minHeight: 44)
                    .background(
                        Capsule(style: .continuous)
                            .fill(tokens.brand.bgSubtleColor)
                    )
                }
                .buttonStyle(.plain)
                .accessibilityLabel(RichContentStrings.closeLabel(locale: locale))
                .accessibilityIdentifier("richContent.imageZoom.closeButton")

                Spacer()

                Text(String(format: "%.1fx", currentZoomScale))
                    .font(.footnote.monospacedDigit())
                    .foregroundStyle(tokens.brand.textMutedColor)
                    .accessibilityIdentifier("richContent.imageZoom.scaleLabel")
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)

            ZoomableImageScrollView(
                image: image,
                alt: alt,
                maxZoomScale: 4.0,
                onZoomScaleChanged: { scale in
                    currentZoomScale = scale
                }
            )
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .offset(y: dragDismissOffset)
            .simultaneousGesture(
                DragGesture(minimumDistance: 36)
                    .onChanged { value in
                        guard currentZoomScale <= 1.01, value.translation.height > 0 else { return }
                        dragDismissOffset = value.translation.height
                    }
                    .onEnded { value in
                        guard currentZoomScale <= 1.01 else {
                            dragDismissOffset = 0
                            return
                        }
                        if value.translation.height > 100 {
                            onClose()
                        } else {
                            dragDismissOffset = 0
                        }
                    }
            )

            if let caption, !caption.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                Text(caption)
                    .font(.footnote)
                    .foregroundStyle(tokens.brand.textSecondaryColor)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
            }
        }
        .background(tokens.brand.bgSurfaceColor.ignoresSafeArea())
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("richContent.imageZoom.screen")
    }
}

fileprivate final class ZoomContainerScrollView: UIScrollView {
    let imageView = UIImageView()
    private var lastLaidOutBounds: CGSize = .zero

    override func layoutSubviews() {
        super.layoutSubviews()
        guard bounds.width > 0, bounds.height > 0 else { return }
        if zoomScale == 1.0 && (lastLaidOutBounds != bounds.size || imageView.frame.size == .zero) {
            lastLaidOutBounds = bounds.size
            let fitted = fittedImageSize(for: imageView.image?.size ?? .zero, in: bounds.size)
            imageView.frame = CGRect(origin: .zero, size: fitted)
            contentSize = fitted
            centerImageView()
        }
    }

    func centerImageView() {
        let insetX = max((bounds.width - contentSize.width) / 2, 0)
        let insetY = max((bounds.height - contentSize.height) / 2, 0)
        contentInset = UIEdgeInsets(top: insetY, left: insetX, bottom: insetY, right: insetX)
    }

    private func fittedImageSize(for imageSize: CGSize, in containerSize: CGSize) -> CGSize {
        guard imageSize.width > 0, imageSize.height > 0 else { return containerSize }
        let scale = min(containerSize.width / imageSize.width, containerSize.height / imageSize.height)
        return CGSize(width: floor(imageSize.width * scale), height: floor(imageSize.height * scale))
    }
}

struct ZoomableImageScrollView: UIViewRepresentable {
    let image: UIImage
    let alt: String
    let maxZoomScale: CGFloat
    let onZoomScaleChanged: (CGFloat) -> Void

    final class Coordinator: NSObject, UIScrollViewDelegate {
        fileprivate weak var scrollView: ZoomContainerScrollView?
        let onZoomScaleChanged: (CGFloat) -> Void

        init(onZoomScaleChanged: @escaping (CGFloat) -> Void) {
            self.onZoomScaleChanged = onZoomScaleChanged
        }

        func viewForZooming(in scrollView: UIScrollView) -> UIView? {
            (scrollView as? ZoomContainerScrollView)?.imageView
        }

        func scrollViewDidZoom(_ scrollView: UIScrollView) {
            (scrollView as? ZoomContainerScrollView)?.centerImageView()
            onZoomScaleChanged(scrollView.zoomScale)
        }
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(onZoomScaleChanged: onZoomScaleChanged)
    }

    func makeUIView(context: Context) -> UIScrollView {
        let scrollView = ZoomContainerScrollView()
        context.coordinator.scrollView = scrollView
        scrollView.delegate = context.coordinator
        scrollView.minimumZoomScale = 1.0
        scrollView.maximumZoomScale = maxZoomScale
        scrollView.bouncesZoom = true
        scrollView.showsHorizontalScrollIndicator = false
        scrollView.showsVerticalScrollIndicator = false
        scrollView.backgroundColor = .clear
        scrollView.accessibilityIdentifier = "richContent.imageZoom.scrollView"

        let imageView = scrollView.imageView
        imageView.image = image
        imageView.contentMode = .scaleAspectFit
        imageView.overrideUserInterfaceStyle = .light
        imageView.isAccessibilityElement = true
        imageView.accessibilityLabel = alt
        imageView.accessibilityIdentifier = "richContent.imageZoom.imageView"
        scrollView.addSubview(imageView)

        return scrollView
    }

    func updateUIView(_ scrollView: UIScrollView, context: Context) {
        scrollView.maximumZoomScale = maxZoomScale
        guard let zoomScroll = scrollView as? ZoomContainerScrollView else { return }
        zoomScroll.imageView.image = image
        zoomScroll.imageView.accessibilityLabel = alt
        zoomScroll.setNeedsLayout()
    }
}
