import Foundation

struct StartErrorDisplay: Equatable, Sendable {
    let primary: String
    let supportLine: String?
}

enum StartErrorMessage {
    static func refusal(from error: any Error) -> StartRefusal {
        let classified = MobileApiError.classify(error)
        switch classified {
        case .network:
            return .generic
        case let .unexpectedResponse(statusCode):
            return statusCode == 429 ? .rateLimited : .generic
        case let .api(statusCode, payload):
            if statusCode == 429 || payload.code == "RATE_LIMITED" {
                return .rateLimited
            }
            switch payload.code {
            case "NO_ATTEMPTS_LEFT":
                return .noAttemptsLeft
            case "CHOICE_NOT_ALLOWED":
                return .choiceNotAllowed
            case "NO_SERVING_OPTION":
                return .noServingOption
            case "SUBJECTS_INVALID":
                return .subjectsInvalid
            default:
                return .generic
            }
        }
    }

    static func display(
        for refusal: StartRefusal,
        locale: AppLocale,
        supportContact: SupportContact = .empty,
        bundle: Bundle = .main
    ) -> StartErrorDisplay {
        let key: String
        switch refusal {
        case .noAttemptsLeft: key = "prepare.startErrors.NO_ATTEMPTS_LEFT"
        case .choiceNotAllowed: key = "prepare.startErrors.CHOICE_NOT_ALLOWED"
        case .noServingOption: key = "prepare.startErrors.NO_SERVING_OPTION"
        case .subjectsInvalid: key = "prepare.startErrors.SUBJECTS_INVALID"
        case .rateLimited: key = "prepare.startErrors.RATE_LIMITED"
        case .generic: key = "prepare.startErrors.generic"
        }

        let primary = PrepareStrings.text(key, locale: locale, bundle: bundle)
        let supportLine: String?
        if refusal == .noServingOption {
            supportLine = noServingSupportLine(
                supportContact: supportContact,
                locale: locale,
                bundle: bundle
            )
        } else {
            supportLine = nil
        }
        return StartErrorDisplay(primary: primary, supportLine: supportLine)
    }

    static func noServingSupportLine(
        supportContact: SupportContact,
        locale: AppLocale,
        bundle: Bundle = .main
    ) -> String {
        let summary = PrepareFormatting.supportSummary(for: supportContact)
        if !summary.isEmpty {
            return PrepareStrings.text(
                "prepare.noServingOption.descriptionWithContact",
                locale: locale,
                replacements: ["contact": summary],
                bundle: bundle
            )
        }
        return PrepareStrings.text("prepare.noServingOption.contactPending", locale: locale, bundle: bundle)
    }
}

final class StartCommand: Sendable {
    private let api: any PrepareAPIClientProtocol
    private let operation: IdempotentOperation

    init(
        api: any PrepareAPIClientProtocol,
        operation: IdempotentOperation = IdempotentOperation()
    ) {
        self.api = api
        self.operation = operation
    }

    var activeKey: String? {
        get async { await operation.activeKey }
    }

    var isInFlight: Bool {
        get async { await operation.isRunning }
    }

    func resetKey() async {
        await operation.reset()
    }

    func start(
        entitlementId: String,
        preChoice: PreparePreChoiceInput? = nil
    ) async -> StartOutcome? {
        do {
            guard let response = try await operation.run({ key in
                try await self.api.startAttempt(
                    entitlementId: entitlementId,
                    preChoice: preChoice,
                    idempotencyKey: key
                )
            }) else {
                return nil
            }
            return .opened(attemptId: response.attemptId, response.state)
        } catch {
            return .refused(StartErrorMessage.refusal(from: error))
        }
    }
}
