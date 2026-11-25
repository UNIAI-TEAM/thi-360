import SwiftUI

struct PrivacyShieldView: View {
    let locale: AppLocale

    @Environment(\.brandPalette) private var palette

    var body: some View {
        VStack(spacing: 16) {
            ZStack {
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .fill(palette.primaryFillColor)
                    .frame(width: 68, height: 68)
                Image(systemName: "lock.fill")
                    .font(.system(size: 30, weight: .semibold))
                    .foregroundStyle(palette.onPrimaryColor)
            }
            .accessibilityHidden(true)

            Text(ShellStrings.text("shell.app.name", locale: locale))
                .font(.title2.weight(.bold))
                .foregroundStyle(palette.textColor)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(palette.bgSurfaceColor.ignoresSafeArea())
        .accessibilityElement(children: .combine)
        .accessibilityLabel(AccountStrings.text("lock.privacyShield.label", locale: locale))
        .accessibilityIdentifier("privacy.shield.overlay")
    }
}
