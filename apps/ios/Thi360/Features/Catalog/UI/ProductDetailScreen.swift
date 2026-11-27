import SwiftUI

struct ProductDetailScreen: View {
    @Bindable var viewModel: ProductDetailViewModel
    let locale: AppLocale
    var supportContact: SupportContact = AppEnvironment.current.supportContact
    var imageLoader: ImageLoader? = nil
    var showsInlineBackBar: Bool = false
    var onBack: (() -> Void)? = nil
    let onReturnToCatalog: () -> Void
    let onOpenExam: (String) -> Void
    let onOpenMyExams: () -> Void
    let onAccessButtonTap: (AccessButtonKind) -> Void
    let onBuyBundle: (ProductDetailModel) -> Void

    @Environment(\.brandPalette) private var palette

    var body: some View {
        VStack(spacing: 0) {
            if showsInlineBackBar, let onBack {
                HStack(spacing: 8) {
                    Button(action: onBack) {
                        HStack(spacing: 6) {
                            Image(systemName: "chevron.backward")
                                .font(.body.weight(.semibold))
                                .accessibilityHidden(true)
                            Text(CatalogStrings.text("storefront.catalog.backToCatalog", locale: locale))
                                .font(.subheadline.weight(.semibold))
                                .lineLimit(2)
                                .multilineTextAlignment(.leading)
                        }
                        .foregroundStyle(palette.primaryTextColor)
                        .padding(.horizontal, 8)
                        .frame(minHeight: 44, alignment: .leading)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("productDetail.backButton")

                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(palette.bgSurfaceColor)
                .overlay(alignment: .bottom) {
                    Rectangle()
                        .fill(palette.borderColor)
                        .frame(height: 1)
                }
            }

            ScrollView {
                LoadStateView(
                    state: viewModel.loadState,
                    locale: locale,
                    onRetry: {
                        Task { await viewModel.retry() }
                    }
                ) { outcome in
                    outcomeView(outcome)
                        .frame(maxWidth: 760, alignment: .leading)
                        .frame(maxWidth: .infinity, alignment: .center)
                        .padding(16)
                }
            }
            .refreshable {
                await viewModel.pullToRefresh()
            }
            .background(palette.bgPageColor.ignoresSafeArea())
        }
        .background(palette.bgPageColor.ignoresSafeArea())
        .task(id: viewModel.code) {
            await viewModel.loadIfNeeded()
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("productDetail.screen")
    }

    @ViewBuilder
    private func outcomeView(_ outcome: ProductOutcome) -> some View {
        switch outcome {
        case let .exam(detail):
            ExamDetailView(
                product: detail,
                accessCell: viewModel.accessCell,
                locale: locale,
                supportContact: supportContact,
                imageLoader: imageLoader,
                onAccessButtonTap: onAccessButtonTap,
                onReloadAccess: {
                    Task { await viewModel.reloadAccess() }
                }
            )

        case let .bundle(detail, sellable):
            BundleDetailView(
                product: detail,
                sellable: sellable,
                accessCell: viewModel.accessCell,
                locale: locale,
                supportContact: supportContact,
                onOpenExam: onOpenExam,
                onBuyBundle: {
                    onBuyBundle(detail)
                },
                onReloadAccess: {
                    Task { await viewModel.reloadAccess() }
                }
            )

        case let .gone(code, name):
            ExamGoneView(
                code: code,
                name: name,
                locale: locale,
                onReturnToCatalog: onReturnToCatalog,
                onOpenMyExams: onOpenMyExams
            )

        case .notFound:
            ExamNotFoundView(
                locale: locale,
                onReturnToCatalog: onReturnToCatalog
            )

        case .bundleUnavailable:
            BundleUnavailableView(
                locale: locale,
                onReturnToCatalog: onReturnToCatalog
            )
        }
    }
}
