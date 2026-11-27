import Foundation
import OpenAPIRuntime

protocol HistoryAPIClientProtocol: Sendable {
    func fetchHistory(_ params: HistoryRequestParams) async throws -> HistoryPageResponse
    func refreshSessionAfterForbidden() async
}

final class HistoryAPI: HistoryAPIClientProtocol, Sendable {
    private let client: Client
    private let refresher: TokenRefresher?

    init(client: Client, refresher: TokenRefresher? = nil) {
        self.client = client
        self.refresher = refresher
    }

    convenience init(apiClient: MobileAPIClient) {
        self.init(client: apiClient.authenticatedClient, refresher: apiClient.refresher)
    }

    func fetchHistory(_ params: HistoryRequestParams) async throws -> HistoryPageResponse {
        do {
            let output = try await client.listHistory(
                query: .init(
                    q: params.q,
                    kind: params.kind,
                    scope: params.scope?.rawValue,
                    examId: nil,
                    from: params.from,
                    to: params.to,
                    page: Int32(params.page),
                    size: Int32(params.size)
                )
            )
            switch output {
            case let .ok(response):
                let data = try await Data(collecting: try response.body.any, upTo: 8_388_608)
                let normalized = Self.normalizeUnknownEnumsForGeneratedDecoder(data)
                let generated = try MobileAPIClient.makeJSONDecoder().decode(
                    Components.Schemas.PageResponseAttemptHistoryItem.self,
                    from: normalized
                )
                return Self.mapPage(generated, rawJSONData: data)
            case let .badRequest(response):
                throw await MobileApiError.decodeErrorBody(statusCode: 400, body: try response.body.any)
            case let .unauthorized(response):
                throw await MobileApiError.decodeErrorBody(statusCode: 401, body: try response.body.any)
            case let .forbidden(response):
                throw await MobileApiError.decodeErrorBody(statusCode: 403, body: try response.body.any)
            case let .notFound(response):
                throw await MobileApiError.decodeErrorBody(statusCode: 404, body: try response.body.any)
            case let .conflict(response):
                throw await MobileApiError.decodeErrorBody(statusCode: 409, body: try response.body.any)
            case let .preconditionFailed(response):
                throw await MobileApiError.decodeErrorBody(statusCode: 412, body: try response.body.any)
            case let .contentTooLarge(response):
                throw await MobileApiError.decodeErrorBody(statusCode: 413, body: try response.body.any)
            case let .unprocessableContent(response):
                throw await MobileApiError.decodeErrorBody(statusCode: 422, body: try response.body.any)
            case let .tooManyRequests(response):
                throw await MobileApiError.decodeErrorBody(statusCode: 429, body: try response.body.any)
            case let .internalServerError(response):
                throw await MobileApiError.decodeErrorBody(statusCode: 500, body: try response.body.any)
            case let .notImplemented(response):
                throw await MobileApiError.decodeErrorBody(statusCode: 501, body: try response.body.any)
            case let .badGateway(response):
                throw await MobileApiError.decodeErrorBody(statusCode: 502, body: try response.body.any)
            case let .undocumented(statusCode, payload):
                if let body = payload.body {
                    throw await MobileApiError.decodeErrorBody(statusCode: statusCode, body: body)
                }
                throw MobileApiError.unexpectedResponse(statusCode: statusCode)
            }
        } catch {
            throw MobileApiError.classify(error)
        }
    }

    func refreshSessionAfterForbidden() async {
        guard let refresher else { return }
        _ = try? await refresher.refreshIfNeeded(failedAccessToken: nil)
    }

    private static let knownStates: Set<String> = [
        "STARTING", "IN_PROGRESS", "GRADING", "PENDING_REVIEW", "SCORED", "GRADING_ERROR", "ABORTED"
    ]
    private static let knownActions: Set<String> = ["CONTINUE", "VIEW_RESULT"]

    private static func normalizeUnknownEnumsForGeneratedDecoder(_ data: Data) -> Data {
        guard var root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
              var items = root["items"] as? [[String: Any]]
        else {
            return data
        }
        var mutated = false
        for idx in items.indices {
            if let rawState = items[idx]["state"] as? String, !knownStates.contains(rawState) {
                items[idx]["state"] = "GRADING_ERROR"
                mutated = true
            }
            if let rawAction = items[idx]["action"] as? String, !knownActions.contains(rawAction) {
                items[idx].removeValue(forKey: "action")
                mutated = true
            }
        }
        guard mutated else { return data }
        root["items"] = items
        return (try? JSONSerialization.data(withJSONObject: root)) ?? data
    }

    private struct RawStateSnapshot: Decodable {
        struct Item: Decodable {
            let state: String?
            let action: String?
        }
        let items: [Item]
    }

    static func mapPage(
        _ dto: Components.Schemas.PageResponseAttemptHistoryItem,
        rawJSONData: Data? = nil
    ) -> HistoryPageResponse {
        let rawItems = rawJSONData
            .flatMap { try? MobileAPIClient.makeJSONDecoder().decode(RawStateSnapshot.self, from: $0).items }
            ?? []

        let mapped = dto.items.enumerated().map { index, item -> HistoryAttemptItem in
            let raw = index < rawItems.count ? rawItems[index] : nil
            let sortedChoices: [HistoryAttemptChoice] = (item.choices?.additionalProperties ?? [:])
                .sorted { $0.key < $1.key }
                .map { key, value in
                    HistoryAttemptChoice(
                        partKey: key,
                        optionCode: value.optionCode,
                        subjectCodes: value.subjectCodes ?? []
                    )
                }

            return HistoryAttemptItem(
                attemptId: item.attemptId ?? "",
                examId: item.examId,
                examVersionNo: item.examVersionNo.map(Int.init),
                examName: item.examName ?? "",
                examKindCode: item.examKindCode,
                scope: item.scope,
                partCode: item.partCode,
                subjectCode: item.subjectCode,
                choices: sortedChoices,
                attemptNo: item.attemptNo.map(Int.init),
                startedAt: item.startedAt,
                finishedAt: item.finishedAt,
                gradedAt: item.gradedAt,
                state: raw?.state ?? item.state?.rawValue,
                regrading: item.regrading ?? false,
                regradingLabel: item.regradingLabel,
                practiceScoring: item.practiceScoring,
                rawScore: item.rawScore,
                displayScore: item.displayScore,
                maxScore: item.maxScore,
                scaleMax: item.scaleMax,
                action: raw?.action ?? item.action?.rawValue,
                resultView: item.resultView,
                faultFlag: item.faultFlag,
                faultReason: item.faultReason,
                compensated: item.compensated ?? false
            )
        }

        return HistoryPageResponse(
            items: mapped,
            page: Int(dto.page),
            size: Int(dto.size),
            totalItems: Int(dto.totalItems),
            totalPages: Int(dto.totalPages)
        )
    }
}
