import Foundation

protocol PartChoiceMetadataAPIProtocol: Sendable {
    func fetchMyExams() async throws -> [MyExamCard]
    func prepare(entitlementId: String) async throws -> AttemptPrepareResponse
}

final class OpenAPIPartChoiceMetadataAPI: PartChoiceMetadataAPIProtocol, Sendable {
    private let myExamsAPI: any MyExamsAPIClientProtocol
    private let prepareAPI: any PrepareAPIClientProtocol

    init(
        myExamsAPI: any MyExamsAPIClientProtocol,
        prepareAPI: any PrepareAPIClientProtocol
    ) {
        self.myExamsAPI = myExamsAPI
        self.prepareAPI = prepareAPI
    }

    convenience init(apiClient: MobileAPIClient) {
        self.init(
            myExamsAPI: MyExamsAPI(apiClient: apiClient),
            prepareAPI: PrepareAPI(apiClient: apiClient)
        )
    }

    func fetchMyExams() async throws -> [MyExamCard] {
        try await myExamsAPI.fetchMyExams()
    }

    func prepare(entitlementId: String) async throws -> AttemptPrepareResponse {
        try await prepareAPI.prepare(entitlementId: entitlementId)
    }
}

struct PartChoiceMetadata: Equatable, Sendable {
    let entitlementId: String
    let choices: [Components.Schemas.AfterStartChoiceView]
    let subjectsByCode: [String: Components.Schemas.ChoiceSubjectView]

    func choice(for part: Components.Schemas.AttemptPartStateView) -> Components.Schemas.AfterStartChoiceView? {
        if let code = part.partCode?.trimmingCharacters(in: .whitespacesAndNewlines), !code.isEmpty {
            if let matched = choices.first(where: {
                $0.partCode?.trimmingCharacters(in: .whitespacesAndNewlines)
                    .caseInsensitiveCompare(code) == .orderedSame
            }) {
                return matched
            }
        }
        if let ord = part.ord {
            if let matched = choices.first(where: { $0.ord == ord }) {
                return matched
            }
        }
        return nil
    }
}

final class PartChoiceOptionsLoader: Sendable {
    private let startContextStore: StartContextStore?
    private let api: any PartChoiceMetadataAPIProtocol

    init(
        startContextStore: StartContextStore?,
        api: any PartChoiceMetadataAPIProtocol
    ) {
        self.startContextStore = startContextStore
        self.api = api
    }

    func load(attemptId: String) async -> LoadState<PartChoiceMetadata> {
        let trimmedAttemptId = attemptId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedAttemptId.isEmpty else {
            return .failed(kind: .notFound, canRetry: true, retryAfter: nil)
        }

        let storedEntitlementId: String? = await startContextStore?
            .read(attemptId: trimmedAttemptId)?
            .entitlementId
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .nonEmptyOrNil

        let entitlementId: String
        if let storedEntitlementId {
            entitlementId = storedEntitlementId
        } else {
            do {
                let cards = try await api.fetchMyExams()
                guard let matched = cards.first(where: { card in
                    guard let active = card.activeAttemptId?.trimmingCharacters(in: .whitespacesAndNewlines) else {
                        return false
                    }
                    return active.caseInsensitiveCompare(trimmedAttemptId) == .orderedSame
                }),
                      let matchedEntitlement = matched.entitlementId?.trimmingCharacters(in: .whitespacesAndNewlines),
                      !matchedEntitlement.isEmpty
                else {
                    return .failed(kind: .notFound, canRetry: true, retryAfter: nil)
                }
                entitlementId = matchedEntitlement
            } catch {
                let classified = LoadFailureKind.classify(MobileApiError.classify(error))
                return .failed(kind: classified.kind, canRetry: true, retryAfter: classified.retryAfter)
            }
        }

        do {
            let response = try await api.prepare(entitlementId: entitlementId)
            var subjects: [String: Components.Schemas.ChoiceSubjectView] = [:]
            for opt in response.preChoice?.options ?? [] {
                for subj in opt.subjects ?? [] {
                    if let code = subj.code?.trimmingCharacters(in: .whitespacesAndNewlines), !code.isEmpty {
                        subjects[code] = subj
                        subjects[code.uppercased()] = subj
                    }
                }
            }
            for choice in response.afterStartChoices ?? [] {
                for opt in choice.options ?? [] {
                    for subj in opt.subjects ?? [] {
                        if let code = subj.code?.trimmingCharacters(in: .whitespacesAndNewlines), !code.isEmpty {
                            subjects[code] = subj
                            subjects[code.uppercased()] = subj
                        }
                    }
                }
            }
            return .loaded(
                value: PartChoiceMetadata(
                    entitlementId: entitlementId,
                    choices: response.afterStartChoices ?? [],
                    subjectsByCode: subjects
                ),
                stale: false
            )
        } catch {
            let classified = LoadFailureKind.classify(MobileApiError.classify(error))
            return .failed(kind: classified.kind, canRetry: true, retryAfter: classified.retryAfter)
        }
    }
}

private extension String {
    var nonEmptyOrNil: String? {
        isEmpty ? nil : self
    }
}
