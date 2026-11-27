import SwiftUI

struct ProductCardCoverView: View {
    let product: CatalogProductCard
    let locale: AppLocale
    var imageLoader: ImageLoader? = nil

    @Environment(\.brandPalette) private var palette

    private var discountPercent: Int? {
        CatalogFormatting.discountPercent(
            listPrice: product.listPrice,
            price: product.price,
            hasPromotion: product.promotion != nil
        )
    }

    var body: some View {
        Color.clear
            .aspectRatio(16 / 9, contentMode: .fit)
            .overlay {
                if let remoteURL = product.effectiveCoverURL {
                    RemoteImage(
                        urlString: remoteURL,
                        contentMode: .fill,
                        loader: imageLoader,
                        authenticated: false
                    ) {
                        defaultCover
                    }
                } else {
                    defaultCover
                }
            }
            .clipped()
            .accessibilityHidden(true)
    }

    var defaultTopLabel: String {
        product.isBundle
            ? product.examKind.code
            : product.examKind.localizedNameOrCode(for: locale)
    }

    var defaultBottomLabel: String {
        product.isBundle
            ? AppStrings.common("storefront.format.BUNDLE", locale: locale)
            : product.code
    }

    private var defaultCover: some View {
        let topLabel = defaultTopLabel
        let bottomLabel = defaultBottomLabel

        return ZStack(alignment: .topLeading) {
            LinearGradient(
                colors: [palette.primaryFillColor, palette.primaryFillColor.opacity(0.84)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )

            VStack(alignment: .leading, spacing: 8) {
                HStack(alignment: .top, spacing: 8) {
                    Text(topLabel)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(palette.onPrimaryColor)
                        .lineLimit(1)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(
                            Capsule(style: .continuous)
                                .fill(palette.onPrimaryColor.opacity(0.18))
                        )

                    Spacer(minLength: 0)

                    if let percent = discountPercent {
                        Text("-\(percent)%")
                            .font(.caption.weight(.bold))
                            .foregroundStyle(palette.onAccentColor)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(
                                Capsule(style: .continuous)
                                    .fill(palette.accentColor)
                            )
                    }
                }

                Spacer(minLength: 0)

                HStack(spacing: 6) {
                    Image(systemName: product.isBundle ? "shippingbox.fill" : "book.closed.fill")
                        .font(.subheadline)
                        .foregroundStyle(palette.onPrimaryColor)
                    Text(bottomLabel)
                        .font(product.isBundle ? .headline.weight(.bold) : .subheadline.weight(.semibold))
                        .foregroundStyle(palette.onPrimaryColor)
                        .lineLimit(1)
                }
            }
            .padding(14)
        }
    }
}

struct ExamDetailCoverView: View {
    let product: ProductDetailModel
    let locale: AppLocale
    var imageLoader: ImageLoader? = nil

    @Environment(\.brandPalette) private var palette

    var usesBrandMarkPlaceholder: Bool {
        product.effectiveCoverURL == nil
    }

    var placeholderKindCode: String {
        product.examKind.code
    }

    var body: some View {
        Color.clear
            .aspectRatio(16 / 6, contentMode: .fit)
            .overlay {
                if let remoteURL = product.effectiveCoverURL {
                    RemoteImage(
                        urlString: remoteURL,
                        contentMode: .fill,
                        loader: imageLoader,
                        authenticated: false
                    ) {
                        brandPlaceholder
                    }
                } else {
                    brandPlaceholder
                }
            }
            .clipped()
            .accessibilityHidden(true)
    }

    private var brandPlaceholder: some View {
        ZStack {
            palette.bgSubtleColor
            HStack(spacing: 12) {
                BrandMark(markSize: 34, locale: locale)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(palette.bgSurfaceColor)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .strokeBorder(palette.borderColor, lineWidth: 1)
                    )

                Text(product.examKind.code)
                    .font(.title3.weight(.bold))
                    .foregroundStyle(palette.textSecondaryColor)
                    .lineLimit(1)
            }
            .padding(.horizontal, 16)
        }
        .accessibilityHidden(true)
    }
}

struct BundleDetailBannerView: View {
    let examKindCode: String
    let locale: AppLocale

    @Environment(\.brandPalette) private var palette

    var body: some View {
        ZStack(alignment: .leading) {
            LinearGradient(
                colors: [palette.primaryFillColor, palette.primaryFillColor.opacity(0.84)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )

            VStack(alignment: .leading, spacing: 8) {
                Text(examKindCode)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(palette.onPrimaryColor)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background(
                        Capsule(style: .continuous)
                            .fill(palette.onPrimaryColor.opacity(0.18))
                    )

                HStack(spacing: 8) {
                    Image(systemName: "shippingbox.fill")
                        .font(.title3)
                        .foregroundStyle(palette.onPrimaryColor)
                    Text(AppStrings.common("storefront.format.BUNDLE", locale: locale))
                        .font(.title3.weight(.bold))
                        .foregroundStyle(palette.onPrimaryColor)
                }
            }
            .padding(16)
        }
        .frame(maxWidth: .infinity, minHeight: 96, alignment: .leading)
        .accessibilityHidden(true)
    }
}
