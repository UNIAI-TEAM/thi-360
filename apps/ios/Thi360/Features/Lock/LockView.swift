import SwiftUI

struct LockView: View {
    @Bindable var viewModel: LockViewModel
    @Bindable var preferences: AppPreferences
    var displayName: String? = nil

    @Environment(\.brandPalette) private var palette

    private var locale: AppLocale { preferences.locale }

    private var resolvedDisplayName: String {
        let candidate = displayName?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !candidate.isEmpty {
            return candidate
        }
        return viewModel.lockedUsername.isEmpty
            ? ShellStrings.text("shell.app.name", locale: locale)
            : viewModel.lockedUsername
    }

    private var initials: String {
        AccountProfile.makeInitials(fullName: displayName, username: viewModel.lockedUsername)
    }

    var body: some View {
        GeometryReader { proxy in
            ScrollView {
                VStack(spacing: 0) {
                    VStack(spacing: 12) {
                        BrandMark(markSize: 44, locale: locale)
                    }
                    .padding(.top, 44)
                    .padding(.horizontal, 20)

                    Spacer(minLength: 24)

                    VStack(spacing: 20) {
                        ZStack {
                            Circle()
                                .fill(palette.primaryContainerStrongColor)
                                .frame(width: 76, height: 76)
                            Text(initials)
                                .font(.system(size: 28, weight: .semibold))
                                .foregroundStyle(palette.primaryTextColor)
                        }
                        .accessibilityHidden(true)

                        VStack(spacing: 4) {
                            Text(resolvedDisplayName)
                                .font(.title2.weight(.bold))
                                .foregroundStyle(palette.textColor)
                                .multilineTextAlignment(.center)
                                .accessibilityAddTraits(.isHeader)
                                .accessibilityIdentifier("lock.displayName")

                            if !viewModel.lockedUsername.isEmpty {
                                Text(viewModel.lockedUsername)
                                    .font(.subheadline)
                                    .foregroundStyle(palette.textMutedColor)
                                    .accessibilityIdentifier("lock.username")
                            }
                        }

                        Text(viewModel.subtitleText(locale: locale))
                            .font(.body)
                            .foregroundStyle(palette.textSecondaryColor)
                            .multilineTextAlignment(.center)
                            .fixedSize(horizontal: false, vertical: true)
                            .padding(.horizontal, 12)
                            .accessibilityIdentifier("lock.subtitle")

                        if let errorMessage = viewModel.errorMessage(locale: locale) {
                            HStack(alignment: .top, spacing: 10) {
                                Image(systemName: "exclamationmark.circle")
                                    .font(.body.weight(.medium))
                                    .foregroundStyle(palette.dangerTextColor)
                                    .accessibilityHidden(true)

                                Text(errorMessage)
                                    .font(.subheadline)
                                    .foregroundStyle(palette.dangerTextColor)
                                    .fixedSize(horizontal: false, vertical: true)

                                Spacer(minLength: 0)
                            }
                            .padding(.horizontal, 14)
                            .padding(.vertical, 12)
                            .background(
                                RoundedRectangle(cornerRadius: 14, style: .continuous)
                                    .fill(palette.dangerBgColor)
                            )
                            .overlay(
                                RoundedRectangle(cornerRadius: 14, style: .continuous)
                                    .strokeBorder(palette.dangerTextColor.opacity(0.25), lineWidth: 1)
                            )
                            .accessibilityElement(children: .combine)
                            .accessibilityIdentifier("lock.statusBanner")
                        }
                    }
                    .frame(maxWidth: 440)
                    .padding(.horizontal, 20)

                    Spacer(minLength: 24)

                    VStack(spacing: 10) {
                        if !viewModel.requiresPasswordAfterInvalidation && viewModel.biometricManager.capability.isAvailable {
                            Button {
                                Task { await viewModel.unlockWithBiometrics(locale: locale) }
                            } label: {
                                HStack(spacing: 8) {
                                    if viewModel.isAuthenticating {
                                        ProgressView()
                                            .tint(palette.onPrimaryColor)
                                    } else {
                                        Image(systemName: viewModel.biometricKind.symbolName)
                                            .font(.body.weight(.semibold))
                                            .accessibilityHidden(true)
                                    }
                                    Text(viewModel.unlockButtonTitle(locale: locale))
                                        .font(.body.weight(.semibold))
                                }
                                .foregroundStyle(palette.onPrimaryColor)
                                .frame(maxWidth: .infinity, minHeight: 50)
                                .padding(.horizontal, 16)
                                .background(
                                    Capsule(style: .continuous)
                                        .fill(palette.primaryFillColor)
                                )
                                .contentShape(Capsule(style: .continuous))
                            }
                            .buttonStyle(.plain)
                            .disabled(viewModel.isAuthenticating)
                            .accessibilityIdentifier("lock.unlockBiometricButton")
                        }

                        let passwordIsPrimary = viewModel.requiresPasswordAfterInvalidation || !viewModel.biometricManager.capability.isAvailable

                        Button {
                            viewModel.openPasswordFallback()
                        } label: {
                            Text(AccountStrings.text("lock.enterPasswordButton", locale: locale))
                                .font(.body.weight(.semibold))
                                .foregroundStyle(passwordIsPrimary ? palette.onPrimaryColor : palette.primaryTextColor)
                                .frame(maxWidth: .infinity, minHeight: 50)
                                .padding(.horizontal, 16)
                                .background(
                                    Capsule(style: .continuous)
                                        .fill(passwordIsPrimary ? palette.primaryFillColor : palette.bgSubtleColor)
                                )
                                .contentShape(Capsule(style: .continuous))
                        }
                        .buttonStyle(.plain)
                        .accessibilityIdentifier("lock.enterPasswordButton")

                        Button {
                            Task { await viewModel.signInWithOtherAccount() }
                        } label: {
                            Text(AccountStrings.text("lock.otherAccountButton", locale: locale))
                                .font(.body)
                                .foregroundStyle(palette.primaryTextColor)
                                .frame(maxWidth: .infinity, minHeight: 44)
                                .padding(.horizontal, 8)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .disabled(viewModel.isSigningOut)
                        .accessibilityIdentifier("lock.otherAccountButton")
                    }
                    .frame(maxWidth: 440)
                    .padding(.horizontal, 20)
                    .padding(.bottom, 24)
                }
                .frame(maxWidth: .infinity, minHeight: proxy.size.height)
            }
            .scrollBounceBehavior(.basedOnSize)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(palette.bgSurfaceColor.ignoresSafeArea())
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("session.locked.screen")
        .onAppear {
            viewModel.checkInvalidationOnAppear()
        }
    }
}
