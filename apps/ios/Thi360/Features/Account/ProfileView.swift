import PhotosUI
import SwiftUI

struct ProfileView: View {
    @Bindable var viewModel: ProfileViewModel
    @Bindable var preferences: AppPreferences
    var isEmbeddedPane: Bool = false
    var onVerifyEmail: () -> Void = {}

    @Environment(\.brandPalette) private var palette
    @Environment(\.statusPalette) private var statusPalette
    @Environment(\.scenePhase) private var scenePhase

    @State private var selectedPhotoItem: PhotosPickerItem?
    @State private var avatarReloadToken: Int = 0
    @State private var didDisappearOnce: Bool = false

    private var locale: AppLocale { preferences.locale }

    var body: some View {
        ZStack(alignment: .top) {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    if isEmbeddedPane {
                        Text(ProfileStrings.text("account.profile.title", locale: locale))
                            .font(.largeTitle.weight(.bold))
                            .foregroundStyle(palette.textColor)
                            .accessibilityAddTraits(.isHeader)
                            .accessibilityIdentifier("profile.heading")
                    }

                    contentByState
                }
                .frame(maxWidth: isEmbeddedPane ? 680 : 620, alignment: .leading)
                .frame(maxWidth: .infinity, alignment: .center)
                .padding(20)
            }
            .background(palette.bgPageColor.ignoresSafeArea())

            if let toast = viewModel.activeToast {
                toastBanner(toast)
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
                    .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .animation(.easeInOut(duration: 0.2), value: viewModel.activeToast?.id)
        .navigationTitle(isEmbeddedPane ? "" : ProfileStrings.text("account.profile.title", locale: locale))
        .navigationBarTitleDisplayMode(.inline)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("profile.screen")
        .onAppear {
            if didDisappearOnce {
                didDisappearOnce = false
                avatarReloadToken &+= 1
            }
            viewModel.syncFromStoreIfChanged()
            viewModel.tickCooldown(locale: locale)
        }
        .onDisappear {
            didDisappearOnce = true
        }
        .task {
            await viewModel.onAppearLoad()
        }
        .onChange(of: viewModel.profile) { _, _ in
            viewModel.syncFromStoreIfChanged()
        }
        .onChange(of: scenePhase) { _, newPhase in
            if newPhase == .active {
                viewModel.tickCooldown(locale: locale)
            }
        }
        .task(id: viewModel.cooldown) {
            guard viewModel.cooldown != .ready else { return }
            while !Task.isCancelled && viewModel.isCooldownActive {
                try? await Task.sleep(nanoseconds: 500_000_000)
                if !Task.isCancelled {
                    viewModel.tickCooldown(locale: locale)
                }
            }
        }
        .confirmationDialog(
            ProfileStrings.text("account.profile.avatar.title", locale: locale),
            isPresented: $viewModel.isShowingAvatarSourceDialog,
            titleVisibility: .visible
        ) {
            Button(ProfileStrings.text("account.profile.avatar.sourceGallery", locale: locale)) {
                viewModel.selectGallerySource()
            }
            .accessibilityIdentifier("profile.avatar.sourceGalleryButton")

            if viewModel.hasCamera {
                Button(ProfileStrings.text("account.profile.avatar.sourceCamera", locale: locale)) {
                    Task { await viewModel.selectCameraSource() }
                }
                .accessibilityIdentifier("profile.avatar.sourceCameraButton")
            }

            Button(ProfileStrings.text("account.profile.email.cancel", locale: locale), role: .cancel) {
                viewModel.cancelMediaPicker()
            }
        }
        .photosPicker(
            isPresented: $viewModel.isShowingPhotosPicker,
            selection: $selectedPhotoItem,
            matching: .images
        )
        .onChange(of: selectedPhotoItem) { _, newItem in
            guard let newItem else { return }
            selectedPhotoItem = nil
            Task {
                if let data = try? await newItem.loadTransferable(type: Data.self) {
                    await viewModel.handleSelectedImageData(data, locale: locale)
                } else {
                    await viewModel.handleSelectedImageData(Data(), locale: locale)
                }
            }
        }
        .fullScreenCover(isPresented: $viewModel.isShowingCameraPicker) {
            CameraImagePickerView(
                onPickedData: { data in
                    viewModel.isShowingCameraPicker = false
                    Task { await viewModel.handleSelectedImageData(data, locale: locale) }
                },
                onCancel: {
                    viewModel.cancelMediaPicker()
                }
            )
            .ignoresSafeArea()
        }
    }

    @ViewBuilder
    private var contentByState: some View {
        switch viewModel.profileState {
        case .loading:
            loadingSkeletonView
        case let .loaded(profile, stale):
            VStack(alignment: .leading, spacing: 20) {
                if stale, let refreshError = viewModel.refreshError {
                    staleRefreshBanner(kind: refreshError)
                }

                Text(ProfileStrings.text("account.profile.intro", locale: locale))
                    .font(.subheadline)
                    .foregroundStyle(palette.textMutedColor)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("profile.intro")
                    .accountProbe(
                        id: "profile.intro",
                        label: ProfileStrings.text("account.profile.intro", locale: locale)
                    )

                if profile.needsEmailVerificationBanner {
                    unverifiedEmailBanner
                }

                avatarSection(profile)

                personalInfoSection(profile)

                emailSection(profile)
            }
        case .empty:
            errorCard(isOffline: false)
        case let .failed(kind, _, _):
            errorCard(isOffline: kind == .offline)
        }
    }

    private var loadingSkeletonView: some View {
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
        .accessibilityIdentifier("profile.loading")
        .accountProbe(
            id: "profile.loading",
            label: AppStrings.common("common.loading", locale: locale)
        )
    }

    private func errorCard(isOffline: Bool) -> some View {
        let title = isOffline
            ? AppStrings.common("common.offline.title", locale: locale)
            : ProfileStrings.text("account.profile.errorTitle", locale: locale)
        let description = isOffline
            ? AppStrings.common("common.offline.description", locale: locale)
            : ProfileStrings.text("account.profile.errorDescription", locale: locale)

        return VStack(spacing: 14) {
            Image(systemName: isOffline ? "wifi.slash" : "exclamationmark.triangle")
                .font(.system(size: 32, weight: .semibold))
                .foregroundStyle(palette.dangerTextColor)
                .accessibilityHidden(true)

            Text(title)
                .font(.headline)
                .foregroundStyle(palette.textColor)
                .multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader)
                .accessibilityIdentifier("profile.error.title")

            Text(description)
                .font(.subheadline)
                .foregroundStyle(palette.textMutedColor)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityIdentifier("profile.error.description")

            Button {
                Task { await viewModel.retryLoad() }
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
            .accessibilityIdentifier("profile.error.retryButton")
        }
        .frame(maxWidth: .infinity)
        .padding(24)
        .background(cardSurface)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier(isOffline ? "profile.offline" : "profile.error")
    }

    private func staleRefreshBanner(kind: LoadFailureKind) -> some View {
        let isOffline = kind == .offline
        let title = isOffline
            ? AppStrings.common("common.offline.title", locale: locale)
            : ProfileStrings.text("account.profile.errorTitle", locale: locale)
        let description = isOffline
            ? AppStrings.common("common.offline.description", locale: locale)
            : ProfileStrings.text("account.profile.errorDescription", locale: locale)

        return VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 10) {
                Image(systemName: isOffline ? "wifi.slash" : "exclamationmark.triangle.fill")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(palette.dangerTextColor)
                    .accessibilityHidden(true)

                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(palette.dangerTextColor)
                    Text(description)
                        .font(.footnote)
                        .foregroundStyle(palette.textSecondaryColor)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }

            Button {
                Task { await viewModel.retryLoad() }
            } label: {
                Text(AppStrings.common("common.retry", locale: locale))
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(palette.primaryTextColor)
                    .padding(.horizontal, 14)
                    .frame(minWidth: 44, minHeight: 44)
                    .background(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .fill(palette.bgSurfaceColor)
                    )
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("profile.stale.retryButton")
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(palette.dangerBgColor)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .strokeBorder(palette.dangerTextColor.opacity(0.3), lineWidth: 1)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("profile.staleBanner")
    }

    private var unverifiedEmailBanner: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 10) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(statusPalette.warningColor)
                    .accessibilityHidden(true)

                VStack(alignment: .leading, spacing: 4) {
                    Text(ProfileStrings.text("account.profile.unverifiedTitle", locale: locale))
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(statusPalette.warningColor)
                        .fixedSize(horizontal: false, vertical: true)

                    Text(ProfileStrings.text("account.profile.unverifiedBody", locale: locale))
                        .font(.footnote)
                        .foregroundStyle(statusPalette.warningColor)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }

            Button {
                onVerifyEmail()
            } label: {
                Text(ProfileStrings.text("account.profile.verifyEmail", locale: locale))
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(statusPalette.warningColor)
                    .padding(.horizontal, 14)
                    .frame(minWidth: 44, minHeight: 44)
                    .background(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .fill(palette.bgSurfaceColor)
                    )
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("profile.verifyEmailButton")
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(statusPalette.warningBgColor)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .strokeBorder(statusPalette.warningColor.opacity(0.35), lineWidth: 1)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("profile.unverifiedBanner")
        .accountProbe(
            id: "profile.unverifiedBanner",
            label: ProfileStrings.text("account.profile.unverifiedTitle", locale: locale)
        )
    }

    private func avatarSection(_ profile: AccountProfile) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            sectionHeader(ProfileStrings.text("account.profile.avatar.title", locale: locale))

            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 14) {
                    AvatarCircleView(
                        avatarUrl: profile.trimmedAvatarUrl,
                        initials: profile.initials,
                        size: 68,
                        fontSize: 22,
                        locale: locale,
                        exposeAvatarAccessibility: true,
                        reloadToken: avatarReloadToken
                    )

                    VStack(alignment: .leading, spacing: 3) {
                        Text(profile.displayName)
                            .font(.headline)
                            .foregroundStyle(palette.textColor)
                            .fixedSize(horizontal: false, vertical: true)
                            .accessibilityIdentifier("profile.avatar.displayName")

                        Text(profile.username)
                            .font(.footnote)
                            .foregroundStyle(palette.textMutedColor)
                            .lineLimit(1)
                            .accessibilityIdentifier("profile.avatar.username")
                    }

                    Spacer(minLength: 0)
                }

                ViewThatFits(in: .horizontal) {
                    HStack(spacing: 10) {
                        avatarUploadButton
                        if profile.hasAvatar {
                            avatarRemoveButton
                        }
                        Spacer(minLength: 0)
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        avatarUploadButton
                        if profile.hasAvatar {
                            avatarRemoveButton
                        }
                    }
                }

                if viewModel.isShowingCameraDeniedBanner {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(ProfileStrings.text("account.profile.avatar.cameraDenied", locale: locale))
                            .font(.footnote)
                            .foregroundStyle(statusPalette.warningColor)
                            .fixedSize(horizontal: false, vertical: true)

                        Button {
                            viewModel.openSystemSettings()
                        } label: {
                            Text(ProfileStrings.text("account.profile.avatar.openSettings", locale: locale))
                                .font(.footnote.weight(.semibold))
                                .foregroundStyle(palette.primaryTextColor)
                                .padding(.horizontal, 12)
                                .frame(minWidth: 44, minHeight: 44)
                                .background(
                                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                                        .fill(palette.bgSurfaceColor)
                                )
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityIdentifier("profile.avatar.openSettingsButton")
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(statusPalette.warningBgColor)
                    )
                    .accessibilityElement(children: .contain)
                    .accessibilityIdentifier("profile.avatar.cameraDeniedBanner")
                }

                if let errorKey = viewModel.avatarErrorKey {
                    inlineErrorBanner(
                        ProfileStrings.text(errorKey, locale: locale),
                        identifier: "profile.avatar.error"
                    )
                }
            }
            .padding(16)
            .background(cardSurface)

            Text(ProfileStrings.text("account.profile.avatar.hint", locale: locale))
                .font(.footnote)
                .foregroundStyle(palette.textMutedColor)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 12)
                .accessibilityIdentifier("profile.avatar.hint")
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("profile.section.avatar")
        .accountProbe(
            id: "profile.section.avatar",
            label: ProfileStrings.text("account.profile.avatar.title", locale: locale)
        )
    }

    private var avatarUploadButton: some View {
        Button {
            viewModel.requestAvatarSourceSelection()
        } label: {
            HStack(spacing: 8) {
                if viewModel.isUploadingAvatar {
                    ProgressView()
                        .tint(palette.primaryTextColor)
                } else {
                    Image(systemName: "arrow.up.to.line")
                        .font(.subheadline.weight(.semibold))
                        .accessibilityHidden(true)
                }
                Text(ProfileStrings.text("account.profile.avatar.upload", locale: locale))
                    .font(.subheadline.weight(.semibold))
                    .fixedSize(horizontal: false, vertical: true)
            }
            .foregroundStyle(palette.primaryTextColor)
            .padding(.horizontal, 14)
            .frame(minWidth: 44, minHeight: 44)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(palette.primaryContainerColor)
            )
            .opacity(viewModel.isAvatarBusy ? 0.6 : 1)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(viewModel.isAvatarBusy)
        .accessibilityIdentifier("profile.avatar.uploadButton")
    }

    private var avatarRemoveButton: some View {
        Button {
            Task { await viewModel.removeAvatar(locale: locale) }
        } label: {
            HStack(spacing: 6) {
                if viewModel.isRemovingAvatar {
                    ProgressView()
                        .tint(palette.dangerTextColor)
                }
                Text(ProfileStrings.text("account.profile.avatar.remove", locale: locale))
                    .font(.subheadline.weight(.semibold))
                    .fixedSize(horizontal: false, vertical: true)
            }
            .foregroundStyle(palette.dangerTextColor)
            .padding(.horizontal, 14)
            .frame(minWidth: 44, minHeight: 44)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(palette.dangerBgColor)
            )
            .opacity(viewModel.isAvatarBusy ? 0.6 : 1)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(viewModel.isAvatarBusy)
        .accessibilityIdentifier("profile.avatar.removeButton")
        .accountProbe(
            id: "profile.avatar.removeButton",
            label: ProfileStrings.text("account.profile.avatar.remove", locale: locale)
        )
    }

    private func personalInfoSection(_ profile: AccountProfile) -> some View {
        let fullNameBinding = Binding<String>(
            get: { viewModel.fullName },
            set: { viewModel.updateFullName($0) }
        )
        let phoneBinding = Binding<String>(
            get: { viewModel.phone },
            set: { viewModel.updatePhone($0) }
        )

        return VStack(alignment: .leading, spacing: 8) {
            sectionHeader(ProfileStrings.text("account.profile.info.title", locale: locale))

            VStack(alignment: .leading, spacing: 14) {
                VStack(alignment: .leading, spacing: 6) {
                    Text(ProfileStrings.text("account.profile.info.usernameLabel", locale: locale))
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(palette.textSecondaryColor)

                    Text(profile.username)
                        .font(.body)
                        .foregroundStyle(palette.textMutedColor)
                        .frame(maxWidth: .infinity, minHeight: 48, alignment: .leading)
                        .padding(.horizontal, 14)
                        .background(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(palette.bgSubtleColor)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .strokeBorder(palette.borderColor, lineWidth: 1)
                        )
                        .accessibilityIdentifier("profile.info.usernameValue")

                    Text(ProfileStrings.text("account.profile.info.usernameHint", locale: locale))
                        .font(.footnote)
                        .foregroundStyle(palette.textMutedColor)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityIdentifier("profile.info.usernameHint")
                }

                VStack(alignment: .leading, spacing: 6) {
                    Text(ProfileStrings.text("account.profile.info.fullNameLabel", locale: locale))
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(palette.textSecondaryColor)

                    TextField(
                        ProfileStrings.text("account.profile.info.fullNameLabel", locale: locale),
                        text: fullNameBinding
                    )
                    .textFieldStyle(.plain)
                    .textContentType(.name)
                    .padding(.horizontal, 14)
                    .frame(minHeight: 48)
                    .foregroundStyle(palette.textColor)
                    .background(fieldBackground(hasError: viewModel.fullNameError != nil))
                    .accessibilityLabel(ProfileStrings.text("account.profile.info.fullNameLabel", locale: locale))
                    .accessibilityIdentifier("profile.info.fullNameField")
                    .accountProbe(
                        id: "profile.info.fullNameField",
                        label: ProfileStrings.text("account.profile.info.fullNameLabel", locale: locale)
                    )

                    if let nameError = viewModel.fullNameError {
                        let key = (nameError == .tooLong)
                            ? "account.profile.info.fullNameTooLong"
                            : "account.profile.info.fullNameRequired"
                        Text(ProfileStrings.text(key, locale: locale))
                            .font(.footnote)
                            .foregroundStyle(palette.dangerTextColor)
                            .fixedSize(horizontal: false, vertical: true)
                            .accessibilityIdentifier("profile.info.fullNameError")
                    }
                }

                VStack(alignment: .leading, spacing: 6) {
                    Text(ProfileStrings.text("account.profile.info.phoneLabel", locale: locale))
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(palette.textSecondaryColor)

                    TextField(
                        ProfileStrings.text("account.profile.info.phonePlaceholder", locale: locale),
                        text: phoneBinding
                    )
                    .textFieldStyle(.plain)
                    .keyboardType(.phonePad)
                    .textContentType(.telephoneNumber)
                    .padding(.horizontal, 14)
                    .frame(minHeight: 48)
                    .foregroundStyle(palette.textColor)
                    .background(fieldBackground(hasError: viewModel.phoneError != nil))
                    .accessibilityLabel(ProfileStrings.text("account.profile.info.phoneLabel", locale: locale))
                    .accessibilityIdentifier("profile.info.phoneField")

                    if let _ = viewModel.phoneError {
                        Text(ProfileStrings.text("account.profile.info.phoneInvalid", locale: locale))
                            .font(.footnote)
                            .foregroundStyle(palette.dangerTextColor)
                            .fixedSize(horizontal: false, vertical: true)
                            .accessibilityIdentifier("profile.info.phoneError")
                    } else if !profile.hasPhone {
                        Text(ProfileStrings.text("account.profile.info.phoneEmpty", locale: locale))
                            .font(.footnote)
                            .foregroundStyle(palette.textMutedColor)
                            .fixedSize(horizontal: false, vertical: true)
                            .accessibilityIdentifier("profile.info.phoneEmptyHint")
                    }
                }

                if viewModel.infoGenericError {
                    inlineErrorBanner(
                        ProfileStrings.text("account.profile.info.genericError", locale: locale),
                        identifier: "profile.info.genericError"
                    )
                }

                ViewThatFits(in: .horizontal) {
                    HStack(spacing: 12) {
                        discardInfoButton
                        saveInfoButton
                    }

                    VStack(spacing: 10) {
                        saveInfoButton
                        discardInfoButton
                    }
                }
            }
            .padding(16)
            .background(cardSurface)
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("profile.section.info")
        .accountProbe(
            id: "profile.section.info",
            label: ProfileStrings.text("account.profile.info.title", locale: locale)
        )
    }

    private var discardInfoButton: some View {
        let disabled = !viewModel.isInfoDirty || viewModel.isSavingInfo
        return Button {
            viewModel.discardInfoChanges()
        } label: {
            Text(ProfileStrings.text("account.profile.info.cancel", locale: locale))
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(palette.textSecondaryColor)
                .frame(maxWidth: .infinity, minHeight: 48)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(palette.bgSubtleColor)
                )
                .opacity(disabled ? 0.55 : 1)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(disabled)
        .accessibilityIdentifier("profile.info.cancelButton")
    }

    private var saveInfoButton: some View {
        let disabled = !viewModel.isInfoDirty || viewModel.isSavingInfo
        return Button {
            Task { await viewModel.savePersonalInfo(locale: locale) }
        } label: {
            HStack(spacing: 8) {
                if viewModel.isSavingInfo {
                    ProgressView()
                        .tint(palette.onPrimaryColor)
                }
                Text(ProfileStrings.text("account.profile.info.save", locale: locale))
                    .font(.subheadline.weight(.semibold))
            }
            .foregroundStyle(palette.onPrimaryColor)
            .frame(maxWidth: .infinity, minHeight: 48)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(palette.primaryFillColor)
            )
            .opacity(disabled ? 0.55 : 1)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(disabled)
        .accessibilityIdentifier("profile.info.saveButton")
    }

    private func emailSection(_ profile: AccountProfile) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            sectionHeader(ProfileStrings.text("account.profile.email.title", locale: locale))

            VStack(alignment: .leading, spacing: 14) {
                HStack(alignment: .center, spacing: 12) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 8, style: .continuous)
                            .fill(palette.primaryContainerStrongColor)
                            .frame(width: 30, height: 30)
                        Image(systemName: "envelope.fill")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(palette.primaryTextColor)
                    }
                    .accessibilityHidden(true)

                    VStack(alignment: .leading, spacing: 4) {
                        if let email = profile.trimmedEmail {
                            Text(email)
                                .font(.body)
                                .foregroundStyle(palette.textColor)
                                .fixedSize(horizontal: false, vertical: true)
                                .accessibilityIdentifier("profile.email.currentValue")

                            let statusText = ProfileStrings.text(
                                profile.emailVerified ? "account.profile.email.verified" : "account.profile.email.unverified",
                                locale: locale
                            )
                            HStack(spacing: 4) {
                                Image(systemName: profile.emailVerified ? "checkmark.circle.fill" : "exclamationmark.circle")
                                    .font(.caption.weight(.semibold))
                                    .accessibilityHidden(true)
                                Text(statusText)
                                    .font(.footnote.weight(.semibold))
                            }
                            .foregroundStyle(profile.emailVerified ? statusPalette.successColor : statusPalette.warningColor)
                            .accessibilityIdentifier("profile.email.statusBadge")
                            .accountProbe(id: "profile.email.statusBadge", label: statusText)
                        } else {
                            let noneText = ProfileStrings.text("account.profile.email.none", locale: locale)
                            Text(noneText)
                                .font(.body)
                                .foregroundStyle(palette.textMutedColor)
                                .fixedSize(horizontal: false, vertical: true)
                                .accessibilityIdentifier("profile.email.noneLabel")
                                .accountProbe(id: "profile.email.noneLabel", label: noneText)
                        }
                    }

                    Spacer(minLength: 0)
                }

                Button {
                    viewModel.toggleChangeEmailForm()
                } label: {
                    HStack {
                        Text(ProfileStrings.text("account.profile.email.change", locale: locale))
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(palette.primaryTextColor)
                        Spacer(minLength: 8)
                        Image(systemName: viewModel.isShowingChangeEmailForm ? "chevron.up" : "chevron.down")
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(palette.textMutedColor)
                            .accessibilityHidden(true)
                    }
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .padding(.horizontal, 12)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(palette.bgSubtleColor)
                    )
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("profile.email.changeButton")
                .accountProbe(
                    id: "profile.email.changeButton",
                    label: ProfileStrings.text("account.profile.email.change", locale: locale)
                )

                if viewModel.isShowingChangeEmailForm {
                    changeEmailInlineForm
                }

                if let pendingEmail = profile.trimmedPendingEmail {
                    pendingEmailBlock(pendingEmail)
                }
            }
            .padding(16)
            .background(cardSurface)
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("profile.section.email")
        .accountProbe(
            id: "profile.section.email",
            label: ProfileStrings.text("account.profile.email.title", locale: locale)
        )
    }

    private var changeEmailInlineForm: some View {
        let emailBinding = Binding<String>(
            get: { viewModel.newEmail },
            set: { viewModel.updateNewEmail($0) }
        )
        let passwordBinding = Binding<String>(
            get: { viewModel.currentPassword },
            set: { viewModel.updateCurrentPassword($0) }
        )
        let sendDisabled = viewModel.isSendingEmailChange || viewModel.isCooldownActive

        return VStack(alignment: .leading, spacing: 12) {
            Divider().overlay(palette.borderColor)

            Text(ProfileStrings.text("account.profile.email.changeTitle", locale: locale))
                .font(.headline)
                .foregroundStyle(palette.textColor)
                .accessibilityAddTraits(.isHeader)

            VStack(alignment: .leading, spacing: 6) {
                Text(ProfileStrings.text("account.profile.email.newEmailLabel", locale: locale))
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(palette.textSecondaryColor)

                TextField(
                    ProfileStrings.text("account.profile.email.newEmailLabel", locale: locale),
                    text: emailBinding
                )
                .textFieldStyle(.plain)
                .keyboardType(.emailAddress)
                .textContentType(.emailAddress)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled(true)
                .padding(.horizontal, 14)
                .frame(minHeight: 48)
                .foregroundStyle(palette.textColor)
                .background(fieldBackground(hasError: viewModel.newEmailErrorKey != nil))
                .accessibilityLabel(ProfileStrings.text("account.profile.email.newEmailLabel", locale: locale))
                .accessibilityIdentifier("profile.email.newEmailField")

                if let key = viewModel.newEmailErrorKey {
                    Text(ProfileStrings.text(key, locale: locale))
                        .font(.footnote)
                        .foregroundStyle(palette.dangerTextColor)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityIdentifier("profile.email.newEmailError")
                }
            }

            VStack(alignment: .leading, spacing: 6) {
                Text(ProfileStrings.text("account.profile.email.currentPasswordLabel", locale: locale))
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(palette.textSecondaryColor)

                HStack(spacing: 8) {
                    Group {
                        if viewModel.isCurrentPasswordVisible {
                            TextField(
                                ProfileStrings.text("account.profile.email.currentPasswordLabel", locale: locale),
                                text: passwordBinding
                            )
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled(true)
                        } else {
                            SecureField(
                                ProfileStrings.text("account.profile.email.currentPasswordLabel", locale: locale),
                                text: passwordBinding
                            )
                        }
                    }
                    .textFieldStyle(.plain)
                    .textContentType(.password)
                    .foregroundStyle(palette.textColor)
                    .accessibilityLabel(ProfileStrings.text("account.profile.email.currentPasswordLabel", locale: locale))
                    .accessibilityIdentifier("profile.email.currentPasswordField")

                    let toggleLabel = ProfileStrings.text(
                        viewModel.isCurrentPasswordVisible
                            ? "account.profile.email.passwordHide"
                            : "account.profile.email.passwordShow",
                        locale: locale
                    )
                    Button {
                        viewModel.isCurrentPasswordVisible.toggle()
                    } label: {
                        Image(systemName: viewModel.isCurrentPasswordVisible ? "eye.slash" : "eye")
                            .font(.subheadline)
                            .foregroundStyle(palette.textMutedColor)
                            .frame(minWidth: 44, minHeight: 44)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(toggleLabel)
                    .accessibilityIdentifier("profile.email.passwordToggle")
                    .accountProbe(id: "profile.email.passwordToggle", label: toggleLabel)
                }
                .padding(.leading, 14)
                .padding(.trailing, 4)
                .frame(minHeight: 48)
                .background(fieldBackground(hasError: viewModel.currentPasswordErrorKey != nil))

                if let key = viewModel.currentPasswordErrorKey {
                    Text(ProfileStrings.text(key, locale: locale))
                        .font(.footnote)
                        .foregroundStyle(palette.dangerTextColor)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityIdentifier("profile.email.currentPasswordError")
                }
            }

            if let alertKey = viewModel.changeEmailAlertKey {
                inlineErrorBanner(
                    ProfileStrings.text(alertKey, locale: locale),
                    identifier: "profile.email.changeAlert"
                )
            }

            ViewThatFits(in: .horizontal) {
                HStack(spacing: 12) {
                    changeEmailCancelButton
                    changeEmailSendButton(disabled: sendDisabled)
                }

                VStack(spacing: 10) {
                    changeEmailSendButton(disabled: sendDisabled)
                    changeEmailCancelButton
                }
            }

            if case .counting = viewModel.activeCooldown {
                Text(
                    ProfileStrings.text(
                        "account.profile.email.sendCodeIn",
                        locale: locale,
                        replacements: ["time": SendCooldown.formatClock(viewModel.cooldownRemainingSeconds)]
                    )
                )
                .font(.footnote.monospacedDigit())
                .foregroundStyle(palette.textMutedColor)
                .accessibilityIdentifier("profile.email.sendCodeCountdown")
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("profile.email.changeForm")
    }

    private var changeEmailCancelButton: some View {
        Button {
            viewModel.cancelChangeEmailForm()
        } label: {
            Text(ProfileStrings.text("account.profile.email.cancel", locale: locale))
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(palette.textSecondaryColor)
                .frame(maxWidth: .infinity, minHeight: 48)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(palette.bgSubtleColor)
                )
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("profile.email.cancelButton")
    }

    private func changeEmailSendButton(disabled: Bool) -> some View {
        Button {
            Task { await viewModel.submitEmailChange(locale: locale) }
        } label: {
            HStack(spacing: 8) {
                if viewModel.isSendingEmailChange {
                    ProgressView()
                        .tint(palette.onPrimaryColor)
                }
                Text(ProfileStrings.text("account.profile.email.sendCode", locale: locale))
                    .font(.subheadline.weight(.semibold))
                    .multilineTextAlignment(.center)
            }
            .foregroundStyle(palette.onPrimaryColor)
            .frame(maxWidth: .infinity, minHeight: 48)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(palette.primaryFillColor)
            )
            .opacity(disabled ? 0.45 : 1)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(disabled)
        .accessibilityIdentifier("profile.email.sendCodeButton")
    }

    private func pendingEmailBlock(_ pendingEmail: String) -> some View {
        let codeBinding = Binding<String>(
            get: { viewModel.pendingCode },
            set: { viewModel.updatePendingCode($0) }
        )

        return VStack(alignment: .leading, spacing: 12) {
            Divider().overlay(palette.borderColor)

            Text(
                ProfileStrings.text(
                    "account.profile.email.pendingTitle",
                    locale: locale,
                    replacements: ["email": pendingEmail]
                )
            )
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(statusPalette.infoColor)
            .fixedSize(horizontal: false, vertical: true)
            .accessibilityIdentifier("profile.email.pendingTitle")

            Text(ProfileStrings.text("account.profile.email.pendingBody", locale: locale))
                .font(.footnote)
                .foregroundStyle(palette.textSecondaryColor)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityIdentifier("profile.email.pendingBody")

            VStack(alignment: .leading, spacing: 6) {
                Text(ProfileStrings.text("account.profile.email.codeLabel", locale: locale))
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(palette.textSecondaryColor)

                TextField(
                    ProfileStrings.text("account.profile.email.codePlaceholder", locale: locale),
                    text: codeBinding
                )
                .textFieldStyle(.plain)
                .keyboardType(.numberPad)
                .textContentType(.oneTimeCode)
                .padding(.horizontal, 14)
                .frame(minHeight: 48)
                .foregroundStyle(palette.textColor)
                .background(fieldBackground(hasError: viewModel.pendingCodeErrorKey != nil))
                .accessibilityLabel(ProfileStrings.text("account.profile.email.codeLabel", locale: locale))
                .accessibilityIdentifier("profile.email.pendingCodeField")

                if let errorKey = viewModel.pendingCodeErrorKey {
                    let message: String = if errorKey == "account.profile.email.codeInvalid" {
                        ProfileStrings.text(
                            errorKey,
                            locale: locale,
                            replacements: ["count": String(viewModel.pendingCodeRemainingAttempts ?? 0)]
                        )
                    } else {
                        ProfileStrings.text(errorKey, locale: locale)
                    }
                    Text(message)
                        .font(.footnote)
                        .foregroundStyle(palette.dangerTextColor)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityIdentifier("profile.email.pendingCodeError")
                }
            }

            if let alertKey = viewModel.pendingAlertKey {
                inlineErrorBanner(
                    ProfileStrings.text(alertKey, locale: locale),
                    identifier: "profile.email.pendingAlert"
                )
            }

            ViewThatFits(in: .horizontal) {
                HStack(spacing: 12) {
                    confirmPendingEmailButton
                    resendPendingEmailButton
                }

                VStack(spacing: 10) {
                    confirmPendingEmailButton
                    resendPendingEmailButton
                }
            }

            if case .counting = viewModel.activeCooldown {
                Text(
                    ProfileStrings.text(
                        "account.profile.email.resendIn",
                        locale: locale,
                        replacements: ["time": SendCooldown.formatClock(viewModel.cooldownRemainingSeconds)]
                    )
                )
                .font(.footnote.monospacedDigit())
                .foregroundStyle(palette.textMutedColor)
                .accessibilityIdentifier("profile.email.pendingCountdown")
            }
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(statusPalette.infoBgColor)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .strokeBorder(statusPalette.infoColor.opacity(0.25), lineWidth: 1)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("profile.email.pendingBlock")
    }

    private var confirmPendingEmailButton: some View {
        Button {
            Task { await viewModel.confirmPendingEmail(locale: locale) }
        } label: {
            HStack(spacing: 8) {
                if viewModel.isConfirmingEmailChange {
                    ProgressView()
                        .tint(palette.onPrimaryColor)
                }
                Text(ProfileStrings.text("account.profile.email.confirm", locale: locale))
                    .font(.subheadline.weight(.semibold))
            }
            .foregroundStyle(palette.onPrimaryColor)
            .frame(maxWidth: .infinity, minHeight: 48)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(palette.primaryFillColor)
            )
            .opacity(viewModel.isConfirmingEmailChange ? 0.6 : 1)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(viewModel.isConfirmingEmailChange)
        .accessibilityIdentifier("profile.email.confirmButton")
    }

    private var resendPendingEmailButton: some View {
        let disabled = viewModel.isCooldownActive
        return Button {
            viewModel.resendPendingEmailCode(locale: locale)
        } label: {
            Text(ProfileStrings.text("account.profile.email.resend", locale: locale))
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(palette.primaryTextColor)
                .frame(maxWidth: .infinity, minHeight: 48)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(palette.bgSurfaceColor)
                )
                .opacity(disabled ? 0.45 : 1)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(disabled)
        .accessibilityIdentifier("profile.email.resendButton")
    }

    private func toastBanner(_ toast: ProfileToastMessage) -> some View {
        HStack(spacing: 10) {
            Image(systemName: "checkmark.circle.fill")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(statusPalette.successColor)
                .accessibilityHidden(true)

            Text(toast.message)
                .font(.subheadline.weight(.medium))
                .foregroundStyle(palette.textColor)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, alignment: .leading)

            Button {
                viewModel.dismissToast()
            } label: {
                Image(systemName: "xmark")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(palette.textMutedColor)
                    .frame(minWidth: 44, minHeight: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(AppStrings.common("common.closeToast", locale: locale))
            .accessibilityIdentifier("profile.toast.closeButton")
        }
        .padding(.leading, 14)
        .padding(.trailing, 4)
        .padding(.vertical, 6)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(palette.bgSurfaceColor)
                .shadow(color: Color.black.opacity(0.14), radius: 10, y: 4)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .strokeBorder(statusPalette.successColor.opacity(0.35), lineWidth: 1)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("profile.toast")
    }

    private func inlineErrorBanner(_ message: String, identifier: String) -> some View {
        HStack(alignment: .top, spacing: 8) {
            Image(systemName: "exclamationmark.circle.fill")
                .font(.subheadline)
                .foregroundStyle(palette.dangerTextColor)
                .accessibilityHidden(true)
            Text(message)
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
        .accessibilityIdentifier(identifier)
    }

    private func sectionHeader(_ title: String) -> some View {
        Text(title)
            .font(.footnote.weight(.semibold))
            .foregroundStyle(palette.textMutedColor)
            .padding(.horizontal, 12)
            .accessibilityAddTraits(.isHeader)
    }

    private var cardSurface: some View {
        RoundedRectangle(cornerRadius: 20, style: .continuous)
            .fill(palette.bgSurfaceColor)
            .overlay(
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .strokeBorder(palette.borderColor, lineWidth: 1)
            )
    }

    private func fieldBackground(hasError: Bool) -> some View {
        RoundedRectangle(cornerRadius: 12, style: .continuous)
            .fill(palette.bgPageColor)
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .strokeBorder(hasError ? palette.dangerTextColor : palette.borderStrongColor, lineWidth: hasError ? 1.5 : 1)
            )
    }
}
