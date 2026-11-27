import Foundation
import HTTPTypes
import OpenAPIRuntime

protocol MyExamsAPIClientProtocol: Sendable {
    func fetchMyExams() async throws -> [MyExamCard]
    func fetchMe() async throws -> MyExamsUserIdentity?
    func refreshSessionAfterForbidden() async
}

final class MyExamsRawBodyBox: @unchecked Sendable {
    private let lock = NSLock()
    private var data: Data?

    func store(_ data: Data) {
        lock.lock()
        self.data = data
        lock.unlock()
    }

    func snapshot() -> Data? {
        lock.lock()
        defer { lock.unlock() }
        return data
    }
}

enum MyExamsRawBodyContext {
    @TaskLocal static var box: MyExamsRawBodyBox?
}

struct MyExamsWireMiddleware: ClientMiddleware {
    private static let knownStates: Set<String> = ["IN_PROGRESS", "CAN_START", "CHECKING", "EXHAUSTED", "NONE"]
    private static let knownSaleStates: Set<String> = ["ON_SALE", "DISCONTINUED", "UNAVAILABLE"]
    private static let knownActions: Set<String> = ["CONTINUE", "START", "VIEW_RESULT", "HISTORY", "REBUY", "BUY_MORE", "CHECKING"]

    func intercept(
        _ request: HTTPRequest,
        body: HTTPBody?,
        baseURL: URL,
        operationID: String,
        next: @Sendable (HTTPRequest, HTTPBody?, URL) async throws -> (HTTPResponse, HTTPBody?)
    ) async throws -> (HTTPResponse, HTTPBody?) {
        let (response, responseBody) = try await next(request, body, baseURL)
        guard operationID == Operations.listMyExams.id,
              response.status.code == 200,
              let responseBody,
              let box = MyExamsRawBodyContext.box
        else {
            return (response, responseBody)
        }

        let collected = try await Data(collecting: responseBody, upTo: 8_388_608)
        box.store(collected)
        // Generated MyExamCard omits nullable anyOf fields (questionCount, durationMinutes, scopeLabel)
        // and uses strict Swift enums for state/saleState/actions; normalize unknowns for generated decoding
        // while preserving raw values from the captured bytes.
        let normalized = Self.normalizeUnknownEnumsForGeneratedDecoder(collected)
        return (response, HTTPBody(normalized))
    }

    private static func normalizeUnknownEnumsForGeneratedDecoder(_ data: Data) -> Data {
        guard var root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
              var items = root["items"] as? [[String: Any]]
        else {
            return data
        }
        var mutated = false
        for idx in items.indices {
            if let rawState = items[idx]["state"] as? String, !knownStates.contains(rawState) {
                items[idx]["state"] = "NONE"
                mutated = true
            }
            if let rawSaleState = items[idx]["saleState"] as? String, !knownSaleStates.contains(rawSaleState) {
                items[idx]["saleState"] = "UNAVAILABLE"
                mutated = true
            }
            if let rawActions = items[idx]["actions"] as? [String] {
                let filtered = rawActions.filter { knownActions.contains($0) }
                if filtered.count != rawActions.count {
                    items[idx]["actions"] = filtered
                    mutated = true
                }
            }
        }
        guard mutated else { return data }
        root["items"] = items
        return (try? JSONSerialization.data(withJSONObject: root)) ?? data
    }
}

final class MyExamsAPI: MyExamsAPIClientProtocol, Sendable {
    private let client: Client
    private let apiClient: MobileAPIClient?
    private let refresher: TokenRefresher?

    init(client: Client, apiClient: MobileAPIClient? = nil, refresher: TokenRefresher? = nil) {
        self.client = client
        self.apiClient = apiClient
        self.refresher = refresher
    }

    convenience init(apiClient: MobileAPIClient) {
        self.init(
            client: apiClient.makeAuthenticatedClient(leadingMiddlewares: [MyExamsWireMiddleware()]),
            apiClient: apiClient,
            refresher: apiClient.refresher
        )
    }

    func fetchMyExams() async throws -> [MyExamCard] {
        let captureBox = MyExamsRawBodyBox()
        return try await MyExamsRawBodyContext.$box.withValue(captureBox) {
            do {
                let output = try await client.listMyExams()
                switch output {
                case let .ok(response):
                    let generated = try response.body.json
                    return Self.mapCards(generated.items, supplementalJSONData: captureBox.snapshot())
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
    }

    func fetchMe() async throws -> MyExamsUserIdentity? {
        guard let apiClient else { return nil }
        let me = try await apiClient.me()
        return MyExamsUserIdentity(fullName: me.fullName, username: me.username)
    }

    func refreshSessionAfterForbidden() async {
        guard let refresher else { return }
        _ = try? await refresher.refreshIfNeeded(failedAccessToken: nil)
    }

    private struct SupplementalResponse: Decodable {
        struct IntRangeDTO: Decodable {
            let min: Int
            let max: Int
        }
        struct LocalizedNameDTO: Decodable {
            let nameVi: String?
            let nameEn: String?
        }
        struct ItemDTO: Decodable {
            let state: String?
            let saleState: String?
            let actions: [String]?
            let questionCount: IntRangeDTO?
            let durationMinutes: IntRangeDTO?
            let scopeLabel: LocalizedNameDTO?
        }

        let items: [ItemDTO]
    }

    static func mapCards(
        _ generated: [Components.Schemas.MyExamCard],
        supplementalJSONData: Data? = nil
    ) -> [MyExamCard] {
        let supplementalItems: [SupplementalResponse.ItemDTO] = supplementalJSONData
            .flatMap { try? MobileAPIClient.makeJSONDecoder().decode(SupplementalResponse.self, from: $0).items }
            ?? []

        return generated.enumerated().map { index, dto in
            let extra = index < supplementalItems.count ? supplementalItems[index] : nil
            return MyExamCard(
                entitlementId: dto.entitlementId,
                code: dto.code,
                name: dto.name,
                examKind: MyExamKind(
                    code: dto.examKind.code,
                    name: MyExamLocalizedName(nameVi: dto.examKind.nameVi, nameEn: dto.examKind.nameEn)
                ),
                format: dto.format,
                scopeLabel: extra?.scopeLabel.map {
                    MyExamLocalizedName(nameVi: $0.nameVi ?? "", nameEn: $0.nameEn ?? "")
                },
                buildMode: dto.buildMode,
                questionCount: extra?.questionCount.map { MyExamIntRange(min: $0.min, max: $0.max) },
                durationMinutes: extra?.durationMinutes.map { MyExamIntRange(min: $0.min, max: $0.max) },
                attemptsLeft: Int(dto.attemptsLeft),
                attemptsTotal: Int(dto.attemptsTotal),
                saleState: extra?.saleState ?? dto.saleState.rawValue,
                state: extra?.state ?? dto.state.rawValue,
                activeAttemptId: dto.activeAttemptId,
                activeAttemptState: dto.activeAttemptState?.rawValue,
                resultAttemptId: dto.resultAttemptId,
                hasPendingFulfillment: dto.hasPendingFulfillment,
                pendingOrderCode: dto.pendingOrderCode,
                actions: extra?.actions ?? dto.actions.map(\.rawValue),
                bundles: (dto.bundles ?? []).map {
                    MyExamBundleRef(bundleId: $0.bundleId, code: $0.code, name: $0.name)
                },
                entitlementSource: dto.entitlementSource,
                classId: dto.classId,
                className: dto.className
            )
        }
    }
}
