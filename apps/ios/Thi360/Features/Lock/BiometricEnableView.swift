import SwiftUI

struct BiometricEnableView: View {
    @Bindable var biometricManager: BiometricManager
    @Bindable var preferences: AppPreferences
    let sessionStore: SessionStore

    @Environment(\.brandPalette) private var palette
    @State private var errorMessageKey: String?
    @State private var didAttemptEnable: Bool = false

    private var locale: AppLocale { preferences.locale }

    private var biometricKind: BiometricKind {
        biometricManager.capability.kind
    }

    private var biometricLabel: String {
        let name = biometricKind.displayName
        return name.isEmpty ? "Face ID" : name
    }

    private var subtitleKey: String {
        biometricKind == .touchID ? "biometric.enable.subtitleTouchID" : "biometric.enable.subtitleFaceID"
    }

    var body: some View {
        VStack(spacing: 0) {
            Spacer(minLength: 24)

            VStack(spacing: 24) {
                ZStack {
                    RoundedRectangle(cornerRadius: 24, style: .continuous)
                        .fill(palette.primaryContainerStrongColor)
                        .frame(width: 92, height: 92)
                    Image(systemName: biometricKind.symbolName)
                        .font(.system(size: 44, weight: .regular))
                        .foregroundStyle(palette.primaryTextColor)
                }
                .accessibilityHidden(true)

                VStack(spacing: 8) {
                    Text(
                        AccountStrings.text(
                            "biometric.enable.title",
                            locale: locale,
                            replacements: ["biometric": biometricLabel]
                        )
                    )
                    .font(.largeTitle.weight(.bold))
                    .foregroundStyle(palette.textColor)
                    .multilineTextAlignment(.center)
                    .accessibilityAddTraits(.isHeader)
                    .accessibilityIdentifier("biometric.enable.title")

                    Text(AccountStrings.text(subtitleKey, locale: locale))
                        .font(.body)
                        .foregroundStyle(palette.textSecondaryColor)
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                }

                VStack(alignment: .leading, spacing: 10) {
                    Text(AccountStrings.text("biometric.enable.pointAutoLock", locale: locale))
                    Text(
                        AccountStrings.text(
                            "biometric.enable.pointFallback",
                            locale: locale,
                            replacements: ["biometric": biometricLabel]
                        )
                    )
                    Text(AccountStrings.text("biometric.enable.pointToggle", locale: locale))
                }
                .font(.subheadline)
                .foregroundStyle(palette.textColor)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 16)
                .padding(.vertical, 14)
                .background(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .fill(palette.bgSubtleColor)
                )

                if let key = errorMessageKey {
                    HStack(alignment: .top, spacing: 10) {
                        Image(systemName: "exclamationmark.circle")
                            .font(.body.weight(.medium))
                            .foregroundStyle(palette.dangerTextColor)
                            .accessibilityHidden(true)

                        Text(
                            AccountStrings.text(
                                key,
                                locale: locale,
                                replacements: ["biometric": biometricLabel]
                            )
                        )
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
                    .accessibilityIdentifier("biometric.enable.errorBanner")
                }
            }
            .frame(maxWidth: 440)
            .padding(.horizontal, 20)

            Spacer(minLength: 24)

            VStack(spacing: 8) {
                Button {
                    Task { await handleEnableTapped() }
                } label: {
                    HStack(spacing: 8) {
                        if biometricManager.isEvaluatingBiometrics {
                            ProgressView()
                                .tint(palette.onPrimaryColor)
                        }
                        Text(
                            AccountStrings.text(
                                "biometric.enable.primaryButton",
                                locale: locale,
                                replacements: ["biometric": biometricLabel]
                            )
                        )
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
                .disabled(biometricManager.isEvaluatingBiometrics)
                .accessibilityIdentifier("biometric.enable.confirmButton")

                if didAttemptEnable {
                    Button {
                        biometricManager.dismissEnablePromptWithoutDeclining()
                    } label: {
                        Text(AccountStrings.text("biometric.enable.continueWithout", locale: locale))
                            .font(.body)
                            .foregroundStyle(palette.primaryTextColor)
                            .frame(maxWidth: .infinity, minHeight: 44)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("biometric.enable.continueWithoutButton")
                } else {
                    Button {
                        biometricManager.declineEnablePromptLater()
                    } label: {
                        Text(AccountStrings.text("biometric.enable.laterButton", locale: locale))
                            .font(.body)
                            .foregroundStyle(palette.primaryTextColor)
                            .frame(maxWidth: .infinity, minHeight: 44)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("biometric.enable.laterButton")
                }
            }
            .frame(maxWidth: 440)
            .padding(.horizontal, 20)
            .padding(.bottom, 24)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(palette.bgSurfaceColor.ignoresSafeArea())
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("biometric.enable.screen")
    }

    private func handleEnableTapped() async {
        guard let refreshToken = sessionStore.currentRefreshToken, !refreshToken.isEmpty else {
            biometricManager.dismissEnablePromptWithoutDeclining()
            return
        }
        errorMessageKey = nil
        didAttemptEnable = true

        let reason = AccountStrings.text(
            "account.biometric.reasonEnable",
            locale: locale,
            replacements: ["biometric": biometricLabel]
        )
        let result = await biometricManager.enableFromPrompt(refreshToken: refreshToken, reason: reason)
        switch result {
        case .succeeded:
            break
        case .lockout:
            errorMessageKey = "account.biometric.lockout"
        case .cancelled, .failed, .invalidated, .unavailable:
            errorMessageKey = "account.biometric.enableFailed"
        }
    }
}
