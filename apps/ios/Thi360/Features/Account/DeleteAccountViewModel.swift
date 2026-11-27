import Foundation
import Observation

@Observable
@MainActor
final class DeleteAccountViewModel {
    let deletionService: any AccountDeletionServiceProtocol
    let sessionStore: SessionStore

    private(set) var state: AccountDeletionState = .checking(.loading)
    var currentPassword: String = ""
    var isPasswordVisible: Bool = false
    var isAcknowledged: Bool = false

    @ObservationIgnored private var isChecking: Bool = false
    @ObservationIgnored private var isDeleting: Bool = false
    @ObservationIgnored private var hasAttemptedInitialCheck: Bool = false

    init(
        deletionService: any AccountDeletionServiceProtocol,
        sessionStore: SessionStore
    ) {
        self.deletionService = deletionService
        self.sessionStore = sessionStore
    }

    var isConfirmDialogPresented: Bool {
        if case .confirming = state {
            return true
        }
        return false
    }

    var isSubmittingDeletion: Bool {
        if case .deleting = state {
            return true
        }
        return false
    }

    var canRequestDelete: Bool {
        switch state {
        case .ready, .failed:
            return isAcknowledged && !currentPassword.isEmpty && !isDeleting
        case .checking, .blocked, .confirming, .deleting, .deleted:
            return false
        }
    }

    func loadCheckIfNeeded() async {
        guard !hasAttemptedInitialCheck else { return }
        await loadCheck()
    }

    func markRecheckNeededIfBlocked() {
        if case .blocked = state {
            hasAttemptedInitialCheck = false
        }
    }

    func loadCheck() async {
        guard !isChecking else { return }
        isChecking = true
        hasAttemptedInitialCheck = true
        state = .checking(.loading)
        defer { isChecking = false }

        do {
            let check = try await deletionService.check()
            if !check.canDelete || !check.blockers.isEmpty {
                state = .blocked(check.blockers)
            } else {
                state = .ready(
                    unusedAttempts: check.unusedAttempts,
                    pendingGrading: check.pendingGrading
                )
            }
        } catch {
            let apiError = MobileApiError.classify(error)
            let classified = LoadFailureKind.classify(apiError)
            let normalizedKind: LoadFailureKind = (classified.kind == .offline) ? .offline : .server
            state = .checking(
                .failed(
                    kind: normalizedKind,
                    canRetry: true,
                    retryAfter: classified.retryAfter
                )
            )
        }
    }

    func updatePassword(_ value: String) {
        currentPassword = value
        if case let .failed(_, unusedAttempts, pendingGrading) = state {
            state = .ready(unusedAttempts: unusedAttempts, pendingGrading: pendingGrading)
        }
    }

    func toggleAcknowledge() {
        isAcknowledged.toggle()
    }

    func requestConfirmation() {
        guard canRequestDelete else { return }
        state = .confirming(
            unusedAttempts: state.unusedAttempts,
            pendingGrading: state.pendingGrading
        )
    }

    func cancelConfirmation() {
        guard case let .confirming(unusedAttempts, pendingGrading) = state else { return }
        state = .ready(unusedAttempts: unusedAttempts, pendingGrading: pendingGrading)
    }

    func confirmDelete() async {
        guard !isDeleting else { return }
        let unusedAttempts: Int
        let pendingGrading: Bool
        switch state {
        case let .confirming(unused, pending):
            unusedAttempts = unused
            pendingGrading = pending
        case let .ready(unused, pending), let .failed(_, unused, pending):
            guard isAcknowledged && !currentPassword.isEmpty else { return }
            unusedAttempts = unused
            pendingGrading = pending
        case .checking, .blocked, .deleting, .deleted:
            return
        }

        isDeleting = true
        state = .deleting(unusedAttempts: unusedAttempts, pendingGrading: pendingGrading)
        defer { isDeleting = false }

        let submittedPassword = currentPassword
        let outcome = await deletionService.delete(currentPassword: submittedPassword)
        switch outcome {
        case .deleted:
            currentPassword = ""
            isPasswordVisible = false
            state = .deleted
            await sessionStore.signOut(reason: .accountDeleted)
        case let .blocked(blockers):
            currentPassword = ""
            isPasswordVisible = false
            state = .blocked(blockers)
        case let .failed(kind):
            if kind == .wrongPassword {
                currentPassword = ""
            }
            state = .failed(
                kind: kind,
                unusedAttempts: unusedAttempts,
                pendingGrading: pendingGrading
            )
        }
    }

    func clearSensitiveInputs() {
        currentPassword = ""
        isPasswordVisible = false
    }
}
