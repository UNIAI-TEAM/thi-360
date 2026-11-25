import SwiftUI

struct RoleNoticeView: View {
    let username: String
    @Bindable var preferences: AppPreferences
    let onSwitchAccount: () -> Void

    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.brandPalette) private var palette

    private var locale: AppLocale { preferences.locale }

    private var trimmedUsername: String {
        username.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(spacing: 24) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 24, style: .continuous)
                            .fill(palette.bgSubtleColor)
                            .frame(width: 92, height: 92)
                        Image(systemName: "person.crop.circle.badge.xmark")
                            .font(.system(size: 42, weight: .regular))
                            .foregroundStyle(palette.textMutedColor)
                    }
                    .accessibilityHidden(true)

                    VStack(spacing: 12) {
                        Text(AuthStrings.text("auth.roleNotice.title", locale: locale))
                            .font(.title2.weight(.bold))
                            .foregroundStyle(palette.textColor)
                            .multilineTextAlignment(.center)
                            .accessibilityAddTraits(.isHeader)
                            .accessibilityIdentifier("roleNotice.title")

                        roleNoticeDescription
                            .font(.body)
                            .foregroundStyle(palette.textSecondaryColor)
                            .multilineTextAlignment(.center)
                            .fixedSize(horizontal: false, vertical: true)
                            .accessibilityIdentifier("roleNotice.description")

                        Text(AuthStrings.text("auth.roleNotice.guidance", locale: locale))
                            .font(.body)
                            .foregroundStyle(palette.textSecondaryColor)
                            .multilineTextAlignment(.center)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                .frame(maxWidth: 460)
                .frame(maxWidth: .infinity)
                .padding(.horizontal, 24)
                .padding(.vertical, 48)
            }

            VStack(spacing: 8) {
                Button(action: onSwitchAccount) {
                    Text(AuthStrings.text("auth.roleNotice.switchAccount", locale: locale))
                        .font(.body.weight(.semibold))
                        .foregroundStyle(palette.onPrimaryColor)
                        .frame(maxWidth: .infinity, minHeight: 50)
                        .padding(.horizontal, 20)
                        .background(
                            Capsule(style: .continuous)
                                .fill(palette.primaryFillColor)
                        )
                        .contentShape(Capsule(style: .continuous))
                }
                .buttonStyle(.plain)
                .frame(maxWidth: 460)
                .accessibilityIdentifier("roleNotice.switchAccountButton")
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)
            .padding(.bottom, 20)
        }
        .background(palette.bgSurfaceColor.ignoresSafeArea())
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("roleNotice.screen")
        .onAppear {
            Thi360AppDelegate.refreshSupportedOrientations(for: horizontalSizeClass)
        }
        .onChange(of: horizontalSizeClass) { _, newValue in
            Thi360AppDelegate.refreshSupportedOrientations(for: newValue)
        }
    }

    @ViewBuilder
    private var roleNoticeDescription: some View {
        if trimmedUsername.isEmpty {
            Text(AuthStrings.text("auth.roleNotice.bodyGeneric", locale: locale))
        } else {
            let prefix = AuthStrings.text("auth.roleNotice.bodyPrefix", locale: locale)
            let suffix = AuthStrings.text("auth.roleNotice.bodySuffix", locale: locale)
            Text("\(prefix)\(Text(trimmedUsername).fontWeight(.semibold).foregroundStyle(palette.textColor))\(suffix)")
        }
    }
}
