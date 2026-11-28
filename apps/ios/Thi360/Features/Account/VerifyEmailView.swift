import SwiftUI

struct VerifyEmailView: View {
    @Bindable var model: VerifyEmailModel
    @Bindable var preferences: AppPreferences
    let onBackToProfile: () -> Void

    @Environment(\.brandPalette) private var palette
    @Environment(\.statusPalette) private var statusPalette
    @Environment(\.scenePhase) private var scenePhase

    private var locale: AppLocale { preferences.locale }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                contentByState
            }
            .frame(maxWidth: 520, alignment: .leading)
            .frame(maxWidth: .infinity, alignment: .center)
            .padding(20)
        }
        .background(palette.bgPageColor.ignoresSafeArea())
        .navigationTitle(ProfileStrings.text("account.verifyEmail.title", locale: locale))
        .navigationBarTitleDisplayMode(.inline)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("verifyEmail.screen")
        .task {
            await model.loadAndAutoSendIfNeeded(locale: locale)
        }
        .onChange(of: scenePhase) { _, newPhase in
            if newPhase == .active {
                model.tickCooldown(locale: locale)
            }
        }
        .task(id: model.cooldown) {
            guard model.cooldown != .ready else { return }
            while !Task.isCancelled && model.isCooldownActive {
                try? await Task.sleep(nanoseconds: 500_000_000)
                if !Task.isCancelled {
                    model.tickCooldown(locale: locale)
                }
            }
        }
    }

    @ViewBuilder
    private var contentByState: some View {
        switch model.screenState {
        case .loading:
            loadingView
        case .loadFailed:
            loadErrorView
        case .alreadyVerified, .verified:
            alreadyVerifiedView
        case let .ready(email):
            readyFormView(email: email)
        }
    }

    private var loadingView: some View {
        VStack(spacing: 12) {
            ProgressView()
                .tint(palette.primaryTextColor)
            Text(AppStrings.common("common.loading", locale: locale))
                .font(.subheadline)
                .foregroundStyle(palette.textMutedColor)
        }
        .frame(maxWidth: .infinity, minHeight: 220)
        .padding(24)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(AppStrings.common("common.loading", locale: locale))
        .accessibilityIdentifier("verifyEmail.loading")
    }

    private var loadErrorView: some View {
        VStack(spacing: 14) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 32, weight: .semibold))
                .foregroundStyle(palette.dangerTextColor)
                .accessibilityHidden(true)

            Text(ProfileStrings.text("account.verifyEmail.errorLoadTitle", locale: locale))
                .font(.headline)
                .foregroundStyle(palette.textColor)
                .multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader)
                .accessibilityIdentifier("verifyEmail.error.title")

            Text(ProfileStrings.text("account.verifyEmail.errorLoad", locale: locale))
                .font(.subheadline)
                .foregroundStyle(palette.textMutedColor)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityIdentifier("verifyEmail.error.description")

            Button {
                Task { await model.retryLoad(locale: locale) }
            } label: {
                Text(AppStrings.common("common.retry", locale: locale))
                    .font(.body.weight(.semibold))
                    .foregroundStyle(palette.onPrimaryColor)
                    .padding(.horizontal, 24)
                    .frame(minWidth: 120, minHeight: 44)
                    .background(
                        Capsule(style: .continuous)
                            .fill(palette.primaryFillColor)
                    )
                    .contentShape(Capsule(style: .continuous))
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("verifyEmail.error.retryButton")
        }
        .frame(maxWidth: .infinity)
        .padding(24)
        .background(cardSurface)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("verifyEmail.error")
    }

    private var alreadyVerifiedView: some View {
        VStack(alignment: .leading, spacing: 18) {
            Text(ProfileStrings.text("account.verifyEmail.title", locale: locale))
                .font(.title2.weight(.bold))
                .foregroundStyle(palette.textColor)
                .accessibilityAddTraits(.isHeader)

            HStack(alignment: .top, spacing: 10) {
                Image(systemName: "checkmark.circle.fill")
                    .font(.title3)
                    .foregroundStyle(statusPalette.successColor)
                    .accessibilityHidden(true)

                Text(ProfileStrings.text("account.verifyEmail.alreadyVerified", locale: locale))
                    .font(.body)
                    .foregroundStyle(statusPalette.successColor)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(14)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(statusPalette.successBgColor)
            )
            .accessibilityIdentifier("verifyEmail.alreadyVerifiedBanner")

            Button {
                onBackToProfile()
            } label: {
                Text(ProfileStrings.text("account.verifyEmail.continue", locale: locale))
                    .font(.body.weight(.semibold))
                    .foregroundStyle(palette.onPrimaryColor)
                    .frame(maxWidth: .infinity, minHeight: 48)
                    .background(
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .fill(palette.primaryFillColor)
                    )
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("verifyEmail.continueButton")
        }
        .padding(20)
        .background(cardSurface)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("verifyEmail.alreadyVerified")
    }

    private func readyFormView(email: String) -> some View {
        let codeBinding = Binding<String>(
            get: { model.code },
            set: { model.updateCode($0) }
        )

        return VStack(alignment: .leading, spacing: 18) {
            ZStack {
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(palette.primaryContainerColor)
                    .frame(width: 64, height: 64)
                Image(systemName: "envelope.badge")
                    .font(.system(size: 28, weight: .semibold))
                    .foregroundStyle(palette.primaryTextColor)
            }
            .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 8) {
                Text(ProfileStrings.text("account.verifyEmail.title", locale: locale))
                    .font(.title2.weight(.bold))
                    .foregroundStyle(palette.textColor)
                    .accessibilityAddTraits(.isHeader)
                    .accessibilityIdentifier("verifyEmail.heading")

                sentToText(email: email)
                    .font(.subheadline)
                    .foregroundStyle(palette.textSecondaryColor)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("verifyEmail.sentTo")
            }

            if let alertKey = model.alertKey {
                HStack(alignment: .top, spacing: 8) {
                    Image(systemName: "exclamationmark.circle.fill")
                        .font(.subheadline)
                        .foregroundStyle(palette.dangerTextColor)
                        .accessibilityHidden(true)
                    Text(ProfileStrings.text(alertKey, locale: locale))
                        .font(.footnote)
                        .foregroundStyle(palette.dangerTextColor)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(12)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(palette.dangerBgColor)
                )
                .accessibilityElement(children: .combine)
                .accessibilityIdentifier("verifyEmail.alert")
            }

            if let limitUntil = model.dailyLimitUntilDate {
                HStack(alignment: .top, spacing: 8) {
                    Image(systemName: "clock.fill")
                        .font(.subheadline)
                        .foregroundStyle(statusPalette.warningColor)
                        .accessibilityHidden(true)
                    Text(
                        ProfileStrings.text(
                            "account.verifyEmail.dailyLimit",
                            locale: locale,
                            replacements: ["time": SendCooldown.formatDailyLimit(limitUntil)]
                        )
                    )
                    .font(.footnote)
                    .foregroundStyle(statusPalette.warningColor)
                    .fixedSize(horizontal: false, vertical: true)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(12)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(statusPalette.warningBgColor)
                )
                .accessibilityElement(children: .combine)
                .accessibilityIdentifier("verifyEmail.dailyLimitBanner")
            }

            VStack(alignment: .leading, spacing: 6) {
                Text(ProfileStrings.text("account.verifyEmail.codeLabel", locale: locale))
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(palette.textSecondaryColor)

                TextField(
                    ProfileStrings.text("account.verifyEmail.codePlaceholder", locale: locale),
                    text: codeBinding
                )
                .textFieldStyle(.plain)
                .keyboardType(.numberPad)
                .textContentType(.oneTimeCode)
                .padding(.horizontal, 14)
                .frame(minHeight: 48)
                .foregroundStyle(palette.textColor)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(palette.bgSurfaceColor)
                        .overlay(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .strokeBorder(
                                    model.codeErrorKey != nil ? palette.dangerTextColor : palette.borderStrongColor,
                                    lineWidth: model.codeErrorKey != nil ? 1.5 : 1
                                )
                        )
                )
                .accessibilityLabel(ProfileStrings.text("account.verifyEmail.codeLabel", locale: locale))
                .accessibilityIdentifier("verifyEmail.codeField")

                if let errorKey = model.codeErrorKey {
                    let message: String = if errorKey == "account.verifyEmail.codeInvalid" {
                        ProfileStrings.text(
                            errorKey,
                            locale: locale,
                            replacements: ["count": String(model.remainingAttempts ?? 0)]
                        )
                    } else {
                        ProfileStrings.text(errorKey, locale: locale)
                    }
                    Text(message)
                        .font(.footnote)
                        .foregroundStyle(palette.dangerTextColor)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityIdentifier("verifyEmail.codeError")
                }
            }

            Button {
                Task { await model.submitCode(onVerified: onBackToProfile) }
            } label: {
                HStack(spacing: 8) {
                    if model.isSubmitting {
                        ProgressView()
                            .tint(palette.onPrimaryColor)
                    }
                    Text(ProfileStrings.text("account.verifyEmail.submit", locale: locale))
                        .font(.body.weight(.semibold))
                }
                .foregroundStyle(palette.onPrimaryColor)
                .frame(maxWidth: .infinity, minHeight: 48)
                .background(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(palette.primaryFillColor)
                )
                .opacity(model.isSubmitting ? 0.6 : 1)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(model.isSubmitting)
            .accessibilityIdentifier("verifyEmail.submitButton")

            VStack(spacing: 8) {
                HStack(spacing: 12) {
                    Text(ProfileStrings.text("account.verifyEmail.notReceived", locale: locale))
                        .font(.subheadline)
                        .foregroundStyle(palette.textMutedColor)
                        .fixedSize(horizontal: false, vertical: true)

                    Spacer(minLength: 4)

                    let resendDisabled = model.isResending || model.isCooldownActive
                    Button {
                        Task { await model.resendCode(locale: locale) }
                    } label: {
                        HStack(spacing: 6) {
                            if model.isResending {
                                ProgressView()
                                    .tint(palette.primaryTextColor)
                            }
                            Text(ProfileStrings.text("account.verifyEmail.resend", locale: locale))
                                .font(.subheadline.weight(.semibold))
                        }
                        .foregroundStyle(palette.primaryTextColor)
                        .padding(.horizontal, 12)
                        .frame(minWidth: 44, minHeight: 44)
                        .opacity(resendDisabled ? 0.45 : 1)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .disabled(resendDisabled)
                    .accessibilityIdentifier("verifyEmail.resendButton")
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 6)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(palette.bgSurfaceColor)
                )

                if case .counting = model.activeCooldown {
                    Text(
                        ProfileStrings.text(
                            "account.verifyEmail.resendIn",
                            locale: locale,
                            replacements: ["time": SendCooldown.formatClock(model.cooldownRemainingSeconds)]
                        )
                    )
                    .font(.footnote.monospacedDigit())
                    .foregroundStyle(palette.textMutedColor)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .accessibilityIdentifier("verifyEmail.resendCountdown")
                }

                if model.showCodeResentNotice {
                    Text(ProfileStrings.text("account.verifyEmail.codeResent", locale: locale))
                        .font(.footnote.weight(.medium))
                        .foregroundStyle(statusPalette.successColor)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .accessibilityIdentifier("verifyEmail.codeResentNotice")
                }
            }

            Button {
                onBackToProfile()
            } label: {
                Text(ProfileStrings.text("account.verifyEmail.later", locale: locale))
                    .font(.body.weight(.semibold))
                    .foregroundStyle(palette.textSecondaryColor)
                    .frame(maxWidth: .infinity, minHeight: 48)
                    .background(
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .fill(palette.bgSubtleColor)
                    )
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("verifyEmail.laterButton")

            footnoteView
        }
    }

    private func sentToText(email: String) -> Text {
        let template = ProfileStrings.text("account.verifyEmail.sentTo", locale: locale)
        let parts = template.components(separatedBy: "{email}")
        if parts.count == 2 {
            return Text(parts[0])
                + Text(email).fontWeight(.semibold).foregroundStyle(palette.textColor)
                + Text(parts[1])
        }
        return Text(template.replacingOccurrences(of: "{email}", with: email))
    }

    private var footnoteView: some View {
        let raw = ProfileStrings.text("account.verifyEmail.footnote", locale: locale)
        let parsed = Self.parseFootnoteLink(raw)

        return VStack(alignment: .leading, spacing: 6) {
            Text(parsed.plainText)
                .font(.footnote)
                .foregroundStyle(palette.textMutedColor)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityIdentifier("verifyEmail.footnote")

            if !parsed.linkText.isEmpty {
                Button {
                    onBackToProfile()
                } label: {
                    Text(parsed.linkText)
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(palette.primaryTextColor)
                        .underline()
                        .frame(minWidth: 44, minHeight: 44, alignment: .leading)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("verifyEmail.footnoteProfileLink")
            }
        }
    }

    static func parseFootnoteLink(_ raw: String) -> (plainText: String, linkText: String) {
        guard let openRange = raw.range(of: "<profile>"),
              let closeRange = raw.range(of: "</profile>", range: openRange.upperBound..<raw.endIndex)
        else {
            return (raw, "")
        }
        let link = String(raw[openRange.upperBound..<closeRange.lowerBound])
        let plain = raw
            .replacingOccurrences(of: "<profile>", with: "")
            .replacingOccurrences(of: "</profile>", with: "")
        return (plain, link)
    }

    private var cardSurface: some View {
        RoundedRectangle(cornerRadius: 20, style: .continuous)
            .fill(palette.bgSurfaceColor)
            .overlay(
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .strokeBorder(palette.borderColor, lineWidth: 1)
            )
    }
}
