import Foundation

@MainActor
final class PartCommands {
    private let attemptId: String
    private let api: any AttemptAPIClient
    private let keyGenerator: @Sendable () -> String

    private var choiceSignature: String?
    private var choiceIdempotency: IdempotentOperation
    private var retryOperations: [PartID: IdempotentOperation] = [:]
    private var submitIdempotency: IdempotentOperation

    private(set) var unconfirmedFinishPartId: PartID?
    private(set) var isSending: Bool = false

    init(
        attemptId: String,
        api: any AttemptAPIClient,
        keyGenerator: @escaping @Sendable () -> String = { UUID().uuidString.lowercased() }
    ) {
        self.attemptId = attemptId
        self.api = api
        self.keyGenerator = keyGenerator
        self.choiceIdempotency = IdempotentOperation(keyGenerator: keyGenerator)
        self.submitIdempotency = IdempotentOperation(keyGenerator: keyGenerator)
    }

    func choosePart(
        partId: PartID,
        optionCode: String,
        subjectCodes: [String]?,
        sessionId: String
    ) async throws -> Components.Schemas.PartChoiceResponse {
        let signature = "\(partId):\(optionCode):\((subjectCodes ?? []).joined(separator: ","))"
        if choiceSignature != signature {
            choiceSignature = signature
            await choiceIdempotency.reset()
        }

        let key = await nextKey(from: choiceIdempotency)
        isSending = true
        defer { isSending = false }

        do {
            let response = try await api.confirmPartChoice(
                attemptId: attemptId,
                partId: partId,
                sessionId: sessionId,
                idempotencyKey: key,
                optionCode: optionCode,
                subjectCodes: subjectCodes
            )
            await choiceIdempotency.reset()
            choiceSignature = nil
            return response
        } catch let apiError as AttemptAPIError {
            if !apiError.isRetainableIdempotencyError {
                await choiceIdempotency.reset()
            }
            throw apiError
        } catch {
            throw error
        }
    }

    func retryGeneration(
        partId: PartID,
        sessionId: String
    ) async throws -> Components.Schemas.PartChoiceResponse {
        let operation = retryOperation(for: partId)
        let key = await nextKey(from: operation)
        isSending = true
        defer { isSending = false }

        do {
            let response = try await api.retryPartGeneration(
                attemptId: attemptId,
                partId: partId,
                sessionId: sessionId,
                idempotencyKey: key,
                reason: nil
            )
            await operation.reset()
            return response
        } catch let apiError as AttemptAPIError {
            if !apiError.isRetainableIdempotencyError {
                await operation.reset()
            }
            throw apiError
        } catch {
            throw error
        }
    }

    func finishPart(
        partId: PartID,
        sessionId: String,
        finalAnswers: [AttemptAnswerChangeInput]
    ) async throws -> Components.Schemas.FinishPartResponse {
        isSending = true
        defer { isSending = false }
        unconfirmedFinishPartId = nil

        do {
            let response = try await api.finishPart(
                attemptId: attemptId,
                partId: partId,
                sessionId: sessionId,
                finalAnswers: finalAnswers
            )
            return response
        } catch let apiError as AttemptAPIError {
            if apiError.isRetainableIdempotencyError {
                unconfirmedFinishPartId = partId
            }
            throw apiError
        } catch {
            unconfirmedFinishPartId = partId
            throw error
        }
    }

    func submit(
        sessionId: String,
        finalAnswers: [AttemptAnswerChangeInput]
    ) async throws -> Components.Schemas.SubmitAttemptResponse {
        let key = await nextKey(from: submitIdempotency)
        isSending = true
        defer { isSending = false }

        do {
            let response = try await api.submit(
                attemptId: attemptId,
                sessionId: sessionId,
                idempotencyKey: key,
                finalAnswers: finalAnswers
            )
            await submitIdempotency.reset()
            return response
        } catch let apiError as AttemptAPIError {
            if case .conflict = apiError {
                await submitIdempotency.reset()
            }
            throw apiError
        } catch {
            throw error
        }
    }

    func clearUnconfirmedFinishPart() {
        unconfirmedFinishPartId = nil
    }

    func clearSubmitIdempotencyKey() async {
        await submitIdempotency.reset()
    }

    private func retryOperation(for partId: PartID) -> IdempotentOperation {
        if let existing = retryOperations[partId] {
            return existing
        }
        let op = IdempotentOperation(keyGenerator: keyGenerator)
        retryOperations[partId] = op
        return op
    }

    private func nextKey(from operation: IdempotentOperation) async -> String {
        if let existing = await operation.activeKey {
            return existing
        }
        let generated = keyGenerator()
        await operation.restore(key: generated)
        return generated
    }
}
