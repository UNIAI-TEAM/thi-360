import Foundation

enum RetryPreChoiceResolution: Equatable, Sendable {
    case direct(PreparePreChoiceInput?)
    case mustReturnToPrepare
}

enum WaitingRetryOutcome: Equatable, Sendable {
    case restarted(
        newAttemptId: String,
        state: AttemptOpenState,
        preChoice: PreparePreChoiceInput?,
        context: StartContext?
    )
    case refused(StartRefusal)
    case reloadPrepare
    case openPrepare(entitlementId: String)
    case goToMyExams
    case alreadyInFlight
}

final class WaitingRetry: Sendable {
    private let startCommand: StartCommand
    private let startContextStore: StartContextStore

    init(
        startCommand: StartCommand,
        startContextStore: StartContextStore
    ) {
        self.startCommand = startCommand
        self.startContextStore = startContextStore
    }

    func retry(
        attemptId: String,
        origin: WaitingOrigin,
        entitlementId: String? = nil,
        prepareResponse: AttemptPrepareResponse? = nil,
        inMemoryPreChoice: PreparePreChoiceInput? = nil,
        inMemoryContext: StartContext? = nil,
        fallbackContextBuilder: ((PreparePreChoiceInput?) -> StartContext?)? = nil
    ) async -> WaitingRetryOutcome {
        switch origin {
        case .prepare:
            return await retryFromPrepare(
                attemptId: attemptId,
                entitlementId: entitlementId,
                prepareResponse: prepareResponse,
                inMemoryPreChoice: inMemoryPreChoice,
                inMemoryContext: inMemoryContext,
                fallbackContextBuilder: fallbackContextBuilder
            )
        case .player:
            return await retryFromPlayer(
                attemptId: attemptId,
                inMemoryContext: inMemoryContext
            )
        }
    }

    static func resolvePreparePreChoice(
        inMemoryPreChoice: PreparePreChoiceInput?,
        savedPreChoice: PreparePreChoiceInput?,
        prepareResponse: AttemptPrepareResponse?
    ) -> RetryPreChoiceResolution {
        if let inMemoryPreChoice {
            return .direct(inMemoryPreChoice)
        }
        if let savedPreChoice {
            return .direct(savedPreChoice)
        }
        guard let response = prepareResponse else {
            return .mustReturnToPrepare
        }
        guard let preChoice = response.preChoice else {
            return .direct(nil)
        }
        let options = preChoice.options ?? []
        if options.count == 1,
           let single = options.first,
           let code = single.code?.trimmingCharacters(in: .whitespacesAndNewlines),
           !code.isEmpty,
           PreChoiceSelection.requiredSubjectCount(for: single) <= 0
        {
            return .direct(PreparePreChoiceInput(optionCode: code, subjectCodes: nil))
        }
        return .mustReturnToPrepare
    }

    private func retryFromPrepare(
        attemptId: String,
        entitlementId: String?,
        prepareResponse: AttemptPrepareResponse?,
        inMemoryPreChoice: PreparePreChoiceInput?,
        inMemoryContext: StartContext?,
        fallbackContextBuilder: ((PreparePreChoiceInput?) -> StartContext?)?
    ) async -> WaitingRetryOutcome {
        let savedContext = await startContextStore.read(attemptId: attemptId)
        let resolvedEntitlementId = nonEmpty(entitlementId)
            ?? nonEmpty(prepareResponse?.entitlementId)
            ?? nonEmpty(savedContext?.entitlementId)
            ?? nonEmpty(inMemoryContext?.entitlementId)
        guard let resolvedEntitlementId else {
            return .reloadPrepare
        }

        let resolution = Self.resolvePreparePreChoice(
            inMemoryPreChoice: inMemoryPreChoice,
            savedPreChoice: savedContext?.preChoice ?? inMemoryContext?.preChoice,
            prepareResponse: prepareResponse
        )
        let preChoicePayload: PreparePreChoiceInput?
        switch resolution {
        case let .direct(payload):
            preChoicePayload = payload
        case .mustReturnToPrepare:
            return .reloadPrepare
        }

        guard let outcome = await startCommand.start(
            entitlementId: resolvedEntitlementId,
            preChoice: preChoicePayload
        ) else {
            return .alreadyInFlight
        }

        switch outcome {
        case let .opened(newAttemptId, openedState):
            let baseContext = inMemoryContext ?? savedContext ?? fallbackContextBuilder?(preChoicePayload)
            let updatedContext = baseContext?.withPreChoice(preChoicePayload)
            if let updatedContext {
                _ = try? await startContextStore.move(
                    from: attemptId,
                    to: newAttemptId,
                    updatedContext: updatedContext
                )
            }
            return .restarted(
                newAttemptId: newAttemptId,
                state: openedState,
                preChoice: preChoicePayload,
                context: updatedContext
            )

        case let .refused(refusal):
            return .refused(refusal)
        }
    }

    private func retryFromPlayer(
        attemptId: String,
        inMemoryContext: StartContext?
    ) async -> WaitingRetryOutcome {
        let stored = await startContextStore.read(attemptId: attemptId)
        guard let context = inMemoryContext ?? stored,
              let resolvedEntitlementId = nonEmpty(context.entitlementId)
        else {
            return .goToMyExams
        }

        guard let outcome = await startCommand.start(
            entitlementId: resolvedEntitlementId,
            preChoice: context.preChoice
        ) else {
            return .alreadyInFlight
        }

        switch outcome {
        case let .opened(newAttemptId, openedState):
            _ = try? await startContextStore.move(
                from: attemptId,
                to: newAttemptId,
                updatedContext: context
            )
            return .restarted(
                newAttemptId: newAttemptId,
                state: openedState,
                preChoice: context.preChoice,
                context: context
            )

        case let .refused(refusal):
            switch refusal {
            case .choiceNotAllowed, .subjectsInvalid:
                return .openPrepare(entitlementId: resolvedEntitlementId)
            default:
                return .refused(refusal)
            }
        }
    }

    private func nonEmpty(_ value: String?) -> String? {
        guard let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines), !trimmed.isEmpty else {
            return nil
        }
        return trimmed
    }
}
