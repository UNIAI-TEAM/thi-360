import SwiftUI

struct AccountShellView: View {
    @Bindable var router: AppRouter
    @Bindable var preferences: AppPreferences
    let effectiveSizeClass: UserInterfaceSizeClass?
    var showsInlineProbe: Bool = true

    @Environment(\.brandPalette) private var palette

    private var locale: AppLocale { preferences.locale }

    private var effectiveTheme: ThemeMode {
        preferences.effectiveThemeMode()
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text(ShellStrings.text("shell.tab.account", locale: locale))
                    .font(.largeTitle.weight(.bold))
                    .foregroundStyle(palette.textColor)
                    .accessibilityAddTraits(.isHeader)
                    .accessibilityIdentifier("shell.account.heading")

                VStack(alignment: .leading, spacing: 8) {
                    Text(ShellStrings.text("shell.account.sectionDisplay", locale: locale))
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(palette.textMutedColor)
                        .padding(.horizontal, 12)

                    VStack(spacing: 0) {
                        languageRow
                        Divider()
                            .overlay(palette.borderColor)
                            .padding(.leading, 58)
                        appearanceRow
                    }
                    .background(
                        RoundedRectangle(cornerRadius: 20, style: .continuous)
                            .fill(palette.bgSurfaceColor)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 20, style: .continuous)
                            .strokeBorder(palette.borderColor, lineWidth: 1)
                    )
                }

                if showsInlineProbe && router.stateProbeEnabled {
                    StateRestorationProbeCard(
                        router: router,
                        locale: locale,
                        effectiveSizeClass: effectiveSizeClass
                    )
                }
            }
            .frame(maxWidth: 620, alignment: .leading)
            .frame(maxWidth: .infinity, alignment: .center)
            .padding(20)
        }
        .background(palette.bgPageColor.ignoresSafeArea())
        .accessibilityIdentifier("shell.account.view")
    }

    private var languageRow: some View {
        let picker = HStack(spacing: 4) {
            localeOptionButton(
                target: .vi,
                title: ShellStrings.text("shell.account.languageVi", locale: locale),
                identifier: "shell.account.locale.vi"
            )
            localeOptionButton(
                target: .en,
                title: ShellStrings.text("shell.account.languageEn", locale: locale),
                identifier: "shell.account.locale.en"
            )
        }
        .padding(3)
        .background(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(palette.bgSubtleColor)
        )
        .fixedSize(horizontal: true, vertical: false)

        return ViewThatFits(in: .horizontal) {
            HStack(spacing: 12) {
                settingIconTile(systemName: "globe")
                Text(ShellStrings.text("shell.account.language", locale: locale))
                    .font(.body)
                    .foregroundStyle(palette.textColor)
                    .fixedSize(horizontal: true, vertical: false)
                Spacer(minLength: 8)
                picker
            }

            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 12) {
                    settingIconTile(systemName: "globe")
                    Text(ShellStrings.text("shell.account.language", locale: locale))
                        .font(.body)
                        .foregroundStyle(palette.textColor)
                        .lineLimit(2)
                    Spacer(minLength: 0)
                }
                HStack {
                    Spacer(minLength: 0)
                    picker
                }
            }
        }
        .frame(minHeight: 52)
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
    }

    private var appearanceRow: some View {
        let picker = HStack(spacing: 4) {
            themeOptionButton(
                target: .light,
                title: ShellStrings.text("shell.account.appearanceLight", locale: locale),
                identifier: "shell.account.theme.light"
            )
            themeOptionButton(
                target: .dark,
                title: ShellStrings.text("shell.account.appearanceDark", locale: locale),
                identifier: "shell.account.theme.dark"
            )
        }
        .padding(3)
        .background(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(palette.bgSubtleColor)
        )
        .fixedSize(horizontal: true, vertical: false)

        return ViewThatFits(in: .horizontal) {
            HStack(spacing: 12) {
                settingIconTile(systemName: effectiveTheme == .dark ? "moon.fill" : "sun.max.fill")
                Text(ShellStrings.text("shell.account.appearance", locale: locale))
                    .font(.body)
                    .foregroundStyle(palette.textColor)
                    .fixedSize(horizontal: true, vertical: false)
                Spacer(minLength: 8)
                picker
            }

            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 12) {
                    settingIconTile(systemName: effectiveTheme == .dark ? "moon.fill" : "sun.max.fill")
                    Text(ShellStrings.text("shell.account.appearance", locale: locale))
                        .font(.body)
                        .foregroundStyle(palette.textColor)
                        .lineLimit(2)
                    Spacer(minLength: 0)
                }
                HStack {
                    Spacer(minLength: 0)
                    picker
                }
            }
        }
        .frame(minHeight: 52)
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
    }

    private func settingIconTile(systemName: String) -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .fill(palette.primaryContainerStrongColor)
                .frame(width: 30, height: 30)
            Image(systemName: systemName)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(palette.primaryTextColor)
        }
        .accessibilityHidden(true)
    }

    private func localeOptionButton(target: AppLocale, title: String, identifier: String) -> some View {
        let isSelected = preferences.locale == target
        return Button {
            preferences.locale = target
        } label: {
            Text(title)
                .font(.footnote.weight(isSelected ? .semibold : .medium))
                .lineLimit(1)
                .fixedSize(horizontal: true, vertical: false)
                .foregroundStyle(isSelected ? palette.onPrimaryColor : palette.textSecondaryColor)
                .padding(.horizontal, 10)
                .frame(minHeight: 38)
                .background(
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .fill(isSelected ? palette.primaryFillColor : Color.clear)
                )
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .frame(minHeight: 44)
        .accessibilityAddTraits(isSelected ? [.isSelected] : [])
        .accessibilityIdentifier(identifier)
    }

    private func themeOptionButton(target: ThemeMode, title: String, identifier: String) -> some View {
        let isSelected = effectiveTheme == target
        return Button {
            preferences.themeMode = target
        } label: {
            Text(title)
                .font(.footnote.weight(isSelected ? .semibold : .medium))
                .lineLimit(1)
                .fixedSize(horizontal: true, vertical: false)
                .foregroundStyle(isSelected ? palette.onPrimaryColor : palette.textSecondaryColor)
                .padding(.horizontal, 10)
                .frame(minHeight: 38)
                .background(
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .fill(isSelected ? palette.primaryFillColor : Color.clear)
                )
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .frame(minHeight: 44)
        .accessibilityAddTraits(isSelected ? [.isSelected] : [])
        .accessibilityIdentifier(identifier)
    }
}

struct AccountDetailShellView: View {
    @Bindable var router: AppRouter
    @Bindable var preferences: AppPreferences
    let effectiveSizeClass: UserInterfaceSizeClass?

    @Environment(\.brandPalette) private var palette

    private var locale: AppLocale { preferences.locale }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                VStack(alignment: .leading, spacing: 12) {
                    Text(ShellStrings.text("shell.account.detailTitle", locale: locale))
                        .font(.title2.weight(.bold))
                        .foregroundStyle(palette.textColor)
                        .accessibilityAddTraits(.isHeader)

                    Text(ShellStrings.text("shell.account.detailSubtitle", locale: locale))
                        .font(.subheadline)
                        .foregroundStyle(palette.textMutedColor)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(24)
                .background(
                    RoundedRectangle(cornerRadius: 20, style: .continuous)
                        .fill(palette.bgSurfaceColor)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 20, style: .continuous)
                        .strokeBorder(palette.borderColor, lineWidth: 1)
                )

                if router.stateProbeEnabled {
                    StateRestorationProbeCard(
                        router: router,
                        locale: locale,
                        effectiveSizeClass: effectiveSizeClass
                    )
                }
            }
            .padding(20)
        }
        .background(palette.bgPageColor.ignoresSafeArea())
        .accessibilityIdentifier("shell.account.detailPane")
    }
}

struct StateRestorationProbeCard: View {
    @Bindable var router: AppRouter
    let locale: AppLocale
    let effectiveSizeClass: UserInterfaceSizeClass?

    @Environment(\.brandPalette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(ShellStrings.text("shell.probe.title", locale: locale))
                .font(.headline)
                .foregroundStyle(palette.textColor)

            TextField(
                ShellStrings.text("shell.probe.placeholder", locale: locale),
                text: $router.draftProbeText
            )
            .textFieldStyle(.plain)
            .padding(.horizontal, 14)
            .frame(minHeight: 48)
            .foregroundStyle(palette.textColor)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(palette.bgSubtleColor)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .strokeBorder(palette.borderStrongColor, lineWidth: 1)
            )
            .accessibilityIdentifier("shell.probe.textField")

            Button {
                router.toggleForcedSizeClass(current: effectiveSizeClass)
            } label: {
                Text(ShellStrings.text("shell.probe.toggleSizeClass", locale: locale))
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(palette.onPrimaryColor)
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(palette.primaryFillColor)
                    )
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("shell.probe.toggleSizeClass")
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(palette.bgSurfaceColor)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .strokeBorder(palette.borderColor, lineWidth: 1)
        )
    }
}
