import SwiftUI

struct DeleteAccountView: View {
    @Bindable var viewModel: DeleteAccountViewModel
    @Bindable var preferences: AppPreferences
    var isEmbeddedPane: Bool = false
    let onOpenMyExams: () -> Void
    let onOpenOrders: () -> Void

    @Environment(\.brandPalette) private var palette
    @Environment(\.statusPalette) private var statusPalette

    private var locale: AppLocale { preferences.locale }

    private var confirmDialogBinding: Binding<Bool> {
        Binding(
            get: { viewModel.isConfirmDialogPresented },
            set: { presented in
                if !presented {
                    viewModel.cancelConfirmation()
                }
            }
        )
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                if isEmbeddedPane {
                    Text(ProfileStrings.text("account.deletion.title", locale: locale))
                        .font(.largeTitle.weight(.bold))
                        .foregroundStyle(palette.textColor)
                        .accessibilityAddTraits(.isHeader)
                        .accessibilityIdentifier("deleteAccount.heading")
                }

                contentByState
            }
            .frame(maxWidth: isEmbeddedPane ? 680 : 620, alignment: .leading)
            .frame(maxWidth: .infinity, alignment: .center)
            .padding(20)
        }
        .background(palette.bgPageColor.ignoresSafeArea())
        .navigationTitle(isEmbeddedPane ? "" : ProfileStrings.text("account.deletion.title", locale: locale))
        .navigationBarTitleDisplayMode(.inline)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("deleteAccount.screen")
        .accountProbe(
            id: "deleteAccount.screen",
            label: ProfileStrings.text("account.deletion.title", locale: locale)
        )
        .onAppear {
            Task {
                await viewModel.loadCheckIfNeeded()
            }
        }
        .onDisappear {
            viewModel.markRecheckNeededIfBlocked()
        }
        .alert(
            ProfileStrings.text("account.deletion.confirmTitle", locale: locale),
            isPresented: confirmDialogBinding
        ) {
            Button(
                ProfileStrings.text("account.deletion.confirmAction", locale: locale),
                role: .destructive
            ) {
                Task { await viewModel.confirmDelete() }
            }
            .accessibilityIdentifier("deleteAccount.confirmActionButton")

            Button(
                ProfileStrings.text("account.deletion.confirmCancel", locale: locale),
                role: .cancel
            ) {
                viewModel.cancelConfirmation()
            }
            .accessibilityIdentifier("deleteAccount.confirmCancelButton")
        }
    }

    @ViewBuilder
    private var contentByState: some View {
        switch viewModel.state {
        case let .checking(loadState):
            LoadStateView(
                state: loadState,
                locale: locale,
                onRetry: {
                    Task { await viewModel.loadCheck() }
                }
            ) { _ in
                EmptyView()
            }

        case let .blocked(blockers):
            blockedContent(blockers)

        case let .ready(unusedAttempts, pendingGrading),
             let .confirming(unusedAttempts, pendingGrading),
             let .deleting(unusedAttempts, pendingGrading):
            readyFormContent(
                unusedAttempts: unusedAttempts,
                pendingGrading: pendingGrading,
                failureKind: nil
            )

        case let .failed(kind, unusedAttempts, pendingGrading):
            readyFormContent(
                unusedAttempts: unusedAttempts,
                pendingGrading: pendingGrading,
                failureKind: kind
            )

        case .deleted:
            Color.clear
                .accessibilityIdentifier("deleteAccount.deleted")
        }
    }

    private func blockedContent(_ blockers: [DeletionBlocker]) -> some View {
        VStack(alignment: .leading, spacing: 18) {
            VStack(alignment: .leading, spacing: 14) {
                ForEach(blockers, id: \.self) { blocker in
                    blockerRow(blocker)
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(cardSurface)
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("deleteAccount.blockedSection")

            explanationCard
        }
    }

    @ViewBuilder
    private func blockerRow(_ blocker: DeletionBlocker) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 10) {
                Image(systemName: "lock.fill")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(palette.dangerTextColor)
                    .frame(width: 24, height: 24)
                    .accessibilityHidden(true)

                let message = blockerMessage(for: blocker)
                Text(message)
                    .font(.body)
                    .foregroundStyle(palette.textColor)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("deleteAccount.blocker.\(blocker.rawValue)")
                    .accountProbe(id: "deleteAccount.blocker.\(blocker.rawValue)", label: message)
            }

            switch blocker {
            case .attemptInProgress:
                let title = ProfileStrings.text("account.deletion.openMyExams", locale: locale)
                Button {
                    viewModel.markRecheckNeededIfBlocked()
                    onOpenMyExams()
                } label: {
                    Text(title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(palette.onPrimaryColor)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .background(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(palette.primaryFillColor)
                        )
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("deleteAccount.openMyExamsButton")
                .accountProbe(id: "deleteAccount.openMyExamsButton", label: title)

            case .orderBeingChecked:
                EmptyView()

            case .orderPendingPayment:
                let title = ProfileStrings.text("account.deletion.openMyOrders", locale: locale)
                Button {
                    viewModel.markRecheckNeededIfBlocked()
                    onOpenOrders()
                } label: {
                    Text(title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(palette.onPrimaryColor)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .background(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(palette.primaryFillColor)
                        )
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("deleteAccount.openMyOrdersButton")
                .accountProbe(id: "deleteAccount.openMyOrdersButton", label: title)
            }
        }
    }

    private func readyFormContent(
        unusedAttempts: Int,
        pendingGrading: Bool,
        failureKind: AccountDeletionFailureKind?
    ) -> some View {
        let passwordBinding = Binding<String>(
            get: { viewModel.currentPassword },
            set: { viewModel.updatePassword($0) }
        )

        return VStack(alignment: .leading, spacing: 18) {
            explanationCard

            if unusedAttempts > 0 {
                warningBanner(
                    ProfileStrings.text(
                        "account.deletion.unusedAttemptsWarning",
                        locale: locale,
                        replacements: ["n": String(unusedAttempts)]
                    ),
                    identifier: "deleteAccount.unusedAttemptsWarning"
                )
            }

            if pendingGrading {
                warningBanner(
                    ProfileStrings.text("account.deletion.pendingGradingWarning", locale: locale),
                    identifier: "deleteAccount.pendingGradingWarning"
                )
            }

            if let failureKind, failureKind != .wrongPassword {
                let key = (failureKind == .throttled)
                    ? "account.deletion.throttled"
                    : "account.deletion.failed"
                HStack(alignment: .top, spacing: 8) {
                    Image(systemName: "exclamationmark.circle.fill")
                        .font(.subheadline)
                        .foregroundStyle(palette.dangerTextColor)
                        .accessibilityHidden(true)
                    Text(ProfileStrings.text(key, locale: locale))
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
                .accessibilityIdentifier("deleteAccount.errorBanner")
            }

            VStack(alignment: .leading, spacing: 14) {
                VStack(alignment: .leading, spacing: 6) {
                    Text(ProfileStrings.text("account.deletion.passwordLabel", locale: locale))
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(failureKind == .wrongPassword ? palette.dangerTextColor : palette.textSecondaryColor)

                    HStack(spacing: 8) {
                        Group {
                            if viewModel.isPasswordVisible {
                                TextField(
                                    ProfileStrings.text("account.deletion.passwordLabel", locale: locale),
                                    text: passwordBinding
                                )
                                .textInputAutocapitalization(.never)
                                .autocorrectionDisabled(true)
                            } else {
                                SecureField(
                                    ProfileStrings.text("account.deletion.passwordLabel", locale: locale),
                                    text: passwordBinding
                                )
                            }
                        }
                        .textFieldStyle(.plain)
                        .textContentType(.password)
                        .disabled(viewModel.isSubmittingDeletion)
                        .foregroundStyle(palette.textColor)
                        .accessibilityLabel(ProfileStrings.text("account.deletion.passwordLabel", locale: locale))
                        .accessibilityIdentifier("deleteAccount.passwordField")
                        .accountProbe(
                            id: "deleteAccount.passwordField",
                            label: ProfileStrings.text("account.deletion.passwordLabel", locale: locale)
                        )

                        Button {
                            viewModel.isPasswordVisible.toggle()
                        } label: {
                            Image(systemName: viewModel.isPasswordVisible ? "eye.slash" : "eye")
                                .font(.subheadline)
                                .foregroundStyle(palette.textMutedColor)
                                .frame(minWidth: 44, minHeight: 44)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(
                            ProfileStrings.text(
                                viewModel.isPasswordVisible
                                    ? "account.profile.email.passwordHide"
                                    : "account.profile.email.passwordShow",
                                locale: locale
                            )
                        )
                        .accessibilityIdentifier("deleteAccount.passwordToggle")
                    }
                    .padding(.leading, 14)
                    .padding(.trailing, 4)
                    .frame(minHeight: 48)
                    .background(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(palette.bgPageColor)
                            .overlay(
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .strokeBorder(
                                        failureKind == .wrongPassword ? palette.dangerTextColor : palette.borderStrongColor,
                                        lineWidth: failureKind == .wrongPassword ? 1.5 : 1
                                    )
                            )
                    )

                    if failureKind == .wrongPassword {
                        Text(ProfileStrings.text("account.deletion.wrongPassword", locale: locale))
                            .font(.footnote)
                            .foregroundStyle(palette.dangerTextColor)
                            .fixedSize(horizontal: false, vertical: true)
                            .accessibilityIdentifier("deleteAccount.wrongPasswordError")
                    }
                }

                Button {
                    viewModel.toggleAcknowledge()
                } label: {
                    HStack(alignment: .center, spacing: 12) {
                        Image(systemName: viewModel.isAcknowledged ? "checkmark.square.fill" : "square")
                            .font(.title3)
                            .foregroundStyle(viewModel.isAcknowledged ? palette.primaryFillColor : palette.textMutedColor)
                            .frame(width: 24, height: 24)
                            .accessibilityHidden(true)

                        Text(ProfileStrings.text("account.deletion.acknowledge", locale: locale))
                            .font(.subheadline)
                            .foregroundStyle(palette.textColor)
                            .multilineTextAlignment(.leading)
                            .fixedSize(horizontal: false, vertical: true)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .disabled(viewModel.isSubmittingDeletion)
                .accessibilityAddTraits(viewModel.isAcknowledged ? [.isSelected] : [])
                .accessibilityIdentifier("deleteAccount.acknowledgeButton")
                .accountProbe(
                    id: "deleteAccount.acknowledgeButton",
                    label: ProfileStrings.text("account.deletion.acknowledge", locale: locale)
                )
            }
            .padding(16)
            .background(cardSurface)

            Button {
                viewModel.requestConfirmation()
            } label: {
                HStack(spacing: 8) {
                    if viewModel.isSubmittingDeletion {
                        ProgressView()
                            .tint(statusPalette.onDangerColor)
                    }
                    Text(ProfileStrings.text("account.deletion.submit", locale: locale))
                        .font(.body.weight(.semibold))
                }
                .foregroundStyle(statusPalette.onDangerColor)
                .frame(maxWidth: .infinity, minHeight: 50)
                .background(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(statusPalette.dangerSolidColor)
                )
                .opacity(viewModel.canRequestDelete ? 1 : 0.55)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(!viewModel.canRequestDelete)
            .accessibilityIdentifier("deleteAccount.submitButton")
            .accountProbe(
                id: "deleteAccount.submitButton",
                label: ProfileStrings.text("account.deletion.submit", locale: locale)
            )
        }
    }

    private var explanationCard: some View {
        let explanation = ProfileStrings.text("account.deletion.explanation", locale: locale)
        return VStack(alignment: .leading, spacing: 8) {
            Text(explanation)
                .font(.subheadline)
                .foregroundStyle(palette.textSecondaryColor)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityIdentifier("deleteAccount.explanation")
                .accountProbe(id: "deleteAccount.explanation", label: explanation)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(cardSurface)
    }

    private func warningBanner(_ text: String, identifier: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(statusPalette.warningColor)
                .accessibilityHidden(true)

            Text(text)
                .font(.footnote.weight(.medium))
                .foregroundStyle(statusPalette.warningColor)
                .fixedSize(horizontal: false, vertical: true)
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
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier(identifier)
        .accountProbe(id: identifier, label: text)
    }

    private func blockerMessage(for blocker: DeletionBlocker) -> String {
        switch blocker {
        case .attemptInProgress:
            ProfileStrings.text("account.deletion.blockedAttempt", locale: locale)
        case .orderBeingChecked:
            ProfileStrings.text("account.deletion.blockedOrder", locale: locale)
        case .orderPendingPayment:
            ProfileStrings.text("account.deletion.blockedPendingPayment", locale: locale)
        }
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
