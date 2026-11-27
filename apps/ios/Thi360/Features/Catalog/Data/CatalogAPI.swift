import Foundation
import HTTPTypes
import OpenAPIRuntime

protocol CatalogAPIClientProtocol: Sendable {
    func fetchFilters() async throws -> CatalogFilters
    func fetchProducts(_ params: CatalogQuery.ProductsRequestParams) async throws -> CatalogPageResponse
    func fetchProductDetail(code: String) async throws -> ProductOutcome
    func queryExamAccess(codes: [String]) async throws -> [ExamAccessRow]
}

final class CatalogRawBodyBox: @unchecked Sendable {
    private let lock = NSLock()
    private var statusCode: Int?
    private var data: Data?

    func store(statusCode: Int, data: Data) {
        lock.lock()
        self.statusCode = statusCode
        self.data = data
        lock.unlock()
    }

    func snapshot() -> (statusCode: Int, data: Data)? {
        lock.lock()
        defer { lock.unlock() }
        guard let statusCode, let data else { return nil }
        return (statusCode, data)
    }
}

enum CatalogRawBodyContext {
    @TaskLocal static var box: CatalogRawBodyBox?
}

struct CatalogProductWireMiddleware: ClientMiddleware {
    func intercept(
        _ request: HTTPRequest,
        body: HTTPBody?,
        baseURL: URL,
        operationID: String,
        next: @Sendable (HTTPRequest, HTTPBody?, URL) async throws -> (HTTPResponse, HTTPBody?)
    ) async throws -> (HTTPResponse, HTTPBody?) {
        let (response, responseBody) = try await next(request, body, baseURL)
        guard operationID == Operations.getStorefrontProduct.id,
              response.status.code == 200 || response.status.code == 410,
              let responseBody,
              let box = CatalogRawBodyContext.box
        else {
            return (response, responseBody)
        }

        let collected = try await Data(collecting: responseBody, upTo: 8_388_608)
        box.store(statusCode: response.status.code, data: collected)

        if response.status.code == 410 {
            let normalized = Self.normalizeGonePayloadForGeneratedDecoder(collected)
            return (response, HTTPBody(normalized))
        }
        return (response, HTTPBody(collected))
    }

    // Spec R-28 / AC-704: 410 payload may omit product.name or send null, whereas generated Product.name is non-optional.
    private static func normalizeGonePayloadForGeneratedDecoder(_ data: Data) -> Data {
        guard var root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
              var product = root["product"] as? [String: Any]
        else {
            return data
        }
        let needsNamePatch = (product["name"] == nil) || (product["name"] is NSNull)
        let needsCodePatch = (product["code"] == nil) || (product["code"] is NSNull)
        guard needsNamePatch || needsCodePatch else {
            return data
        }
        if needsNamePatch {
            product["name"] = ""
        }
        if needsCodePatch {
            product["code"] = ""
        }
        root["product"] = product
        return (try? JSONSerialization.data(withJSONObject: root)) ?? data
    }
}

final class CatalogAPI: CatalogAPIClientProtocol, Sendable {
    private let client: Client
    private let refresher: TokenRefresher?

    init(client: Client, refresher: TokenRefresher? = nil) {
        self.client = client
        self.refresher = refresher
    }

    convenience init(apiClient: MobileAPIClient) {
        self.init(client: apiClient.authenticatedClient, refresher: apiClient.refresher)
    }

    static func makeWithWireMiddleware(
        serverURL: URL,
        tokenStore: any TokenStore,
        transport: any ClientTransport,
        refresher: TokenRefresher,
        logger: RedactingAppLogger = RedactingAppLogger(),
        localeProvider: @escaping @Sendable () async -> AppLocale
    ) -> CatalogAPI {
        let configuration = Configuration(dateTranscoder: FlexibleISO8601DateTranscoder())
        let middlewares: [any ClientMiddleware] = [
            CatalogProductWireMiddleware(),
            AcceptLanguageMiddleware(localeProvider: localeProvider),
            RetryAfterMiddleware(),
            AuthBearerMiddleware(tokenStore: tokenStore, refresher: refresher),
            NetworkLoggingMiddleware(logger: logger)
        ]
        let client = Client(
            serverURL: serverURL,
            configuration: configuration,
            transport: transport,
            middlewares: middlewares
        )
        return CatalogAPI(client: client, refresher: refresher)
    }

    func fetchFilters() async throws -> CatalogFilters {
        do {
            let output = try await client.getStorefrontFilters()
            switch output {
            case let .ok(response):
                let payload = try await MobileAPIClient.decodeJSONBody(
                    Components.Schemas.StorefrontFilters.self,
                    from: try response.body.any
                )
                return Self.mapFilters(payload)
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
                throw await Self.decodeOptionalError(statusCode: statusCode, body: payload.body)
            }
        } catch {
            throw MobileApiError.classify(error)
        }
    }

    func fetchProducts(_ params: CatalogQuery.ProductsRequestParams) async throws -> CatalogPageResponse {
        do {
            let formatPayload = params.format.flatMap {
                Operations.listStorefrontProducts.Input.Query.formatPayload(rawValue: $0.rawValue)
            }
            let sortPayload = Operations.listStorefrontProducts.Input.Query.sortPayload(rawValue: params.sort.rawValue)
            let output = try await client.listStorefrontProducts(
                query: .init(
                    q: params.q,
                    kind: params.kind,
                    format: formatPayload,
                    subject: params.subject,
                    part: params.part,
                    sort: sortPayload,
                    page: Int32(params.page),
                    size: Int32(params.size)
                )
            )
            switch output {
            case let .ok(response):
                let payload = try await MobileAPIClient.decodeJSONBody(
                    Components.Schemas.PageResponseProductCard.self,
                    from: try response.body.any
                )
                return Self.mapPage(payload)
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
                throw await Self.decodeOptionalError(statusCode: statusCode, body: payload.body)
            }
        } catch {
            throw MobileApiError.classify(error)
        }
    }

    func fetchProductDetail(code: String) async throws -> ProductOutcome {
        let captureBox = CatalogRawBodyBox()
        return try await CatalogRawBodyContext.$box.withValue(captureBox) {
            do {
                let output = try await client.getStorefrontProduct(path: .init(code: code))
                switch output {
                case let .ok(response):
                    let generated = try response.body.json
                    let rawData = captureBox.snapshot()?.statusCode == 200 ? captureBox.snapshot()?.data : nil
                    let detail = Self.mapProductDetail(generated, supplementalJSONData: rawData)
                    if detail.isBundle {
                        let sellable = detail.saleState?.uppercased() != "UNAVAILABLE"
                        return .bundle(detail, sellable: sellable)
                    }
                    return .exam(detail)

                case .notFound:
                    return .notFound

                case let .gone(response):
                    let generated = try response.body.json
                    let resolvedCode = generated.product.code.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty ?? code
                    let resolvedName = generated.product.name.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty ?? resolvedCode
                    return .gone(code: resolvedCode, name: resolvedName)

                case let .conflict(response):
                    let bodyData = (try? await Data(collecting: try response.body.any, upTo: 8_388_608)) ?? Data()
                    if let outcome = Self.parseUnavailableConflict(from: bodyData) {
                        return outcome
                    }
                    throw await MobileApiError.decodeErrorBody(statusCode: 409, body: HTTPBody(bodyData))

                case let .badRequest(response):
                    throw await MobileApiError.decodeErrorBody(statusCode: 400, body: try response.body.any)
                case let .unauthorized(response):
                    throw await MobileApiError.decodeErrorBody(statusCode: 401, body: try response.body.any)
                case let .forbidden(response):
                    throw await MobileApiError.decodeErrorBody(statusCode: 403, body: try response.body.any)
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
                    if statusCode == 410, let body = payload.body {
                        let data = (try? await Data(collecting: body, upTo: 65_536)) ?? Data()
                        return Self.parseGoneFallback(from: data, requestedCode: code)
                    }
                    throw await Self.decodeOptionalError(statusCode: statusCode, body: payload.body)
                }
            } catch {
                if let snapshot = captureBox.snapshot(), snapshot.statusCode == 410 {
                    return Self.parseGoneFallback(from: snapshot.data, requestedCode: code)
                }
                throw MobileApiError.classify(error)
            }
        }
    }

    func queryExamAccess(codes: [String]) async throws -> [ExamAccessRow] {
        guard !codes.isEmpty else { return [] }
        do {
            return try await executeExamAccessQuery(codes: codes)
        } catch {
            let apiError = MobileApiError.classify(error)
            // Spec A-77: 403 triggers session refresh; APP_STUDENT_ONLY/401 handled by TokenRefresher, otherwise retry once.
            if apiError.statusCode == 403, let refresher {
                _ = try await refresher.refreshIfNeeded(failedAccessToken: nil)
                return try await executeExamAccessQuery(codes: codes)
            }
            throw apiError
        }
    }

    private func executeExamAccessQuery(codes: [String]) async throws -> [ExamAccessRow] {
        do {
            let output = try await client.queryMyExamAccess(
                body: .json(.init(codes: codes))
            )
            switch output {
            case let .ok(response):
                let payload = try response.body.json
                return payload.items.map(Self.mapAccessRow)
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
                throw await Self.decodeOptionalError(statusCode: statusCode, body: payload.body)
            }
        } catch {
            throw MobileApiError.classify(error)
        }
    }

    private static func decodeOptionalError(statusCode: Int, body: HTTPBody?) async -> MobileApiError {
        if let body {
            return await MobileApiError.decodeErrorBody(statusCode: statusCode, body: body)
        }
        return .unexpectedResponse(statusCode: statusCode)
    }

    private static func parseGoneFallback(from data: Data, requestedCode: String) -> ProductOutcome {
        guard let root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
              let product = root["product"] as? [String: Any]
        else {
            return .gone(code: requestedCode, name: requestedCode)
        }
        let rawCode = (product["code"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
        let resolvedCode = rawCode ?? requestedCode
        let rawName = (product["name"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
        let resolvedName = rawName ?? resolvedCode
        return .gone(code: resolvedCode, name: resolvedName)
    }

    static func parseUnavailableConflict(from data: Data) -> ProductOutcome? {
        guard let root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
              let errorCode = root["code"] as? String,
              errorCode == "PRODUCT_UNAVAILABLE"
        else {
            return nil
        }

        if let productDict = root["product"] as? [String: Any],
           isFullProductDetailDictionary(productDict),
           let productData = try? JSONSerialization.data(withJSONObject: productDict),
           let generated = try? MobileAPIClient.makeJSONDecoder().decode(Components.Schemas.ProductDetail.self, from: productData)
        {
            let detail = mapProductDetail(generated, supplementalJSONData: productData)
            if detail.isBundle {
                return .bundle(detail, sellable: false)
            }
        }

        return .bundleUnavailable
    }

    private static func isFullProductDetailDictionary(_ dict: [String: Any]) -> Bool {
        guard dict["code"] is String,
              dict["name"] is String,
              dict["listPrice"] is NSNumber,
              dict["price"] is NSNumber,
              let examKind = dict["examKind"] as? [String: Any],
              examKind["code"] is String,
              dict["benefits"] is [String: Any]
        else {
            return false
        }
        return true
    }

    private static func mapFilters(_ dto: Components.Schemas.StorefrontFilters) -> CatalogFilters {
        CatalogFilters(
            catalogKinds: (dto.catalogKinds ?? []).map {
                ExamKindInfo(code: $0.code, name: LocalizedLabel(nameVi: $0.nameVi, nameEn: $0.nameEn))
            },
            kinds: dto.kinds.map {
                ExamKindInfo(code: $0.code, name: LocalizedLabel(nameVi: $0.nameVi, nameEn: $0.nameEn))
            },
            formats: dto.formats.map(\.code),
            parts: dto.parts.map {
                CatalogPartOption(kindCode: $0.kindCode, code: $0.code, name: $0.name, count: $0.count)
            },
            subjects: dto.subjects.map {
                CatalogSubjectOption(
                    code: $0.code,
                    name: LocalizedLabel(nameVi: $0.nameVi, nameEn: $0.nameEn),
                    count: $0.count
                )
            }
        )
    }

    private static func mapPage(_ dto: Components.Schemas.PageResponseProductCard) -> CatalogPageResponse {
        CatalogPageResponse(
            items: dto.items.map(mapCard),
            page: Int(dto.page),
            size: Int(dto.size),
            totalItems: Int(dto.totalItems),
            totalPages: Int(dto.totalPages)
        )
    }

    private static func mapCard(_ dto: Components.Schemas.ProductCard) -> CatalogProductCard {
        CatalogProductCard(
            code: dto.code,
            name: dto.name,
            summary: dto.summary?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty,
            coverUrl: dto.coverUrl?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty,
            examKind: ExamKindInfo(
                code: dto.examKind.code,
                name: LocalizedLabel(nameVi: dto.examKind.nameVi, nameEn: dto.examKind.nameEn)
            ),
            format: dto.format,
            scopeLabel: dto.scopeLabel.map { LocalizedLabel(nameVi: $0.nameVi, nameEn: $0.nameEn) },
            buildMode: dto.buildMode,
            questionCount: dto.questionCount.map { IntRangeValue(min: Int($0.min), max: Int($0.max)) },
            durationMinutes: dto.durationMinutes.map { IntRangeValue(min: Int($0.min), max: Int($0.max)) },
            attemptsGranted: Int(dto.attemptsGranted),
            listPrice: dto.listPrice,
            price: dto.price,
            promotion: dto.promotion.map { PromotionInfo(name: $0.name, endsAt: $0.endsAt) },
            versionNo: Int(dto.versionNo),
            productType: dto.productType
        )
    }

    private struct SupplementalProductDetail: Decodable {
        struct SupplementalBundleItem: Decodable {
            struct IntRangeDTO: Decodable {
                let min: Int
                let max: Int
            }
            struct DecimalRangeDTO: Decodable {
                let min: Double
                let max: Double
            }
            struct LocalizedNameDTO: Decodable {
                let nameVi: String
                let nameEn: String
            }

            let examId: String?
            let code: String?
            let questionCount: IntRangeDTO?
            let durationMinutes: IntRangeDTO?
            let maxPoints: DecimalRangeDTO?
            let subjectName: LocalizedNameDTO?
        }

        let saleState: String?
        let items: [SupplementalBundleItem]?
    }

    static func mapProductDetail(
        _ dto: Components.Schemas.ProductDetail,
        supplementalJSONData: Data? = nil
    ) -> ProductDetailModel {
        let supplemental: SupplementalProductDetail? = supplementalJSONData.flatMap {
            try? MobileAPIClient.makeJSONDecoder().decode(SupplementalProductDetail.self, from: $0)
        }
        let supplementalItems = supplemental?.items ?? []

        let mappedItems: [BundleDetailItemModel] = (dto.items ?? []).enumerated().map { index, item in
            let extra: SupplementalProductDetail.SupplementalBundleItem? = {
                if index < supplementalItems.count,
                   supplementalItems[index].code == item.code || supplementalItems[index].examId == item.examId
                {
                    return supplementalItems[index]
                }
                return supplementalItems.first { $0.examId == item.examId || $0.code == item.code }
            }()

            return BundleDetailItemModel(
                examId: item.examId,
                code: item.code,
                name: item.name,
                scope: item.scope,
                partCode: item.partCode?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty,
                subjectName: extra?.subjectName.map { LocalizedLabel(nameVi: $0.nameVi, nameEn: $0.nameEn) },
                buildMode: item.buildMode,
                questionCount: extra?.questionCount.map { IntRangeValue(min: $0.min, max: $0.max) },
                durationMinutes: extra?.durationMinutes.map { IntRangeValue(min: $0.min, max: $0.max) },
                maxPoints: extra?.maxPoints.map { DecimalRangeValue(min: $0.min, max: $0.max) },
                attemptsGranted: Int(item.attemptsGranted),
                benefits: DetailBenefitsModel(
                    aiExplanation: item.benefits.aiExplanation,
                    strengthWeakness: item.benefits.strengthWeakness,
                    practice: item.benefits.practice,
                    note: item.benefits.note?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
                ),
                resultView: item.resultView?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty,
                versionNo: item.versionNo.map(Int.init)
            )
        }

        return ProductDetailModel(
            code: dto.code,
            name: dto.name,
            summary: dto.summary?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty,
            coverUrl: dto.coverUrl?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty,
            examKind: ExamKindInfo(
                code: dto.examKind.code,
                name: LocalizedLabel(nameVi: dto.examKind.nameVi, nameEn: dto.examKind.nameEn)
            ),
            format: dto.format,
            scopeLabel: dto.scopeLabel.map { LocalizedLabel(nameVi: $0.nameVi, nameEn: $0.nameEn) },
            buildMode: dto.buildMode,
            questionCount: dto.questionCount.map { IntRangeValue(min: Int($0.min), max: Int($0.max)) },
            durationMinutes: dto.durationMinutes.map { IntRangeValue(min: Int($0.min), max: Int($0.max)) },
            maxPoints: dto.maxPoints.map { DecimalRangeValue(min: $0.min, max: $0.max) },
            attemptsGranted: Int(dto.attemptsGranted),
            listPrice: dto.listPrice,
            price: dto.price,
            promotion: dto.promotion.map { PromotionInfo(name: $0.name, endsAt: $0.endsAt) },
            versionNo: Int(dto.versionNo),
            description: dto.description?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty,
            objectives: dto.objectives?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty,
            audience: dto.audience?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty,
            parts: dto.parts
                .sorted { $0.order < $1.order }
                .map { part in
                    DetailPartModel(
                        order: Int(part.order),
                        name: part.name,
                        questionCount: part.questionCount.map { IntRangeValue(min: Int($0.min), max: Int($0.max)) },
                        durationMinutes: part.durationMinutes.map { IntRangeValue(min: Int($0.min), max: Int($0.max)) },
                        maxPoints: part.maxPoints.map { DecimalRangeValue(min: $0.min, max: $0.max) },
                        required: part.required,
                        answerTypes: part.answerTypes.map {
                            AnswerTypeGroupModel(
                                questionType: $0.questionType,
                                questionCount: Int($0.questionCount),
                                childAnswerType: $0.childAnswerType
                            )
                        }
                    )
                },
            choices: dto.choices.map { group in
                DetailChoiceGroupModel(
                    partName: group.partName,
                    options: group.options.map { option in
                        DetailChoiceOptionModel(
                            code: option.code,
                            kind: option.kind,
                            name: option.name,
                            branch: option.branch,
                            chooseSubjects: option.chooseSubjects.map(Int.init),
                            countsByPick: option.countsByPick?.map(Int.init),
                            subjects: option.subjects.map {
                                ExamKindInfo(
                                    code: $0.code,
                                    name: LocalizedLabel(nameVi: $0.nameVi, nameEn: $0.nameEn)
                                )
                            },
                            answerTypes: option.answerTypes.map {
                                AnswerTypeGroupModel(
                                    questionType: $0.questionType,
                                    questionCount: Int($0.questionCount),
                                    childAnswerType: $0.childAnswerType
                                )
                            },
                            subjectAnswerTypes: option.subjectAnswerTypes.map { entry in
                                SubjectAnswerTypesModel(
                                    subject: ExamKindInfo(
                                        code: entry.subject.code,
                                        name: LocalizedLabel(nameVi: entry.subject.nameVi, nameEn: entry.subject.nameEn)
                                    ),
                                    answerTypes: entry.answerTypes.map {
                                        AnswerTypeGroupModel(
                                            questionType: $0.questionType,
                                            questionCount: Int($0.questionCount),
                                            childAnswerType: $0.childAnswerType
                                        )
                                    }
                                )
                            }
                        )
                    }
                )
            },
            scoring: DetailScoringModel(
                timing: dto.scoring.timing,
                maxScore: dto.scoring.maxScore,
                referenceMax: dto.scoring.referenceMax,
                practiceScoring: dto.scoring.practiceScoring
            ),
            omittedEssayGroups: dto.omittedEssayGroups.map {
                OmittedEssayGroupModel(name: $0.name, points: $0.points)
            },
            benefits: DetailBenefitsModel(
                aiExplanation: dto.benefits.aiExplanation,
                strengthWeakness: dto.benefits.strengthWeakness,
                practice: dto.benefits.practice,
                note: dto.benefits.note?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
            ),
            resultView: dto.resultView?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty,
            productType: dto.productType,
            items: mappedItems,
            saleState: supplemental?.saleState?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
        )
    }

    private static func mapAccessRow(_ dto: Components.Schemas.ExamAccessItem) -> ExamAccessRow {
        ExamAccessRow(
            code: dto.code,
            state: ExamAccessState(rawValue: dto.state.rawValue) ?? .none,
            attemptsLeft: Int(dto.attemptsLeft),
            entitlementId: dto.entitlementId?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty,
            activeAttemptId: dto.activeAttemptId?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty,
            hasPendingFulfillment: dto.hasPendingFulfillment,
            pendingOrderCode: dto.pendingOrderCode?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty,
            items: dto.items?.map { item in
                BundleAccessItemRow(
                    examId: item.examId,
                    code: item.code,
                    name: item.name,
                    state: ExamAccessState(rawValue: item.state.rawValue) ?? .none,
                    attemptsLeft: Int(item.attemptsLeft),
                    entitlementId: item.entitlementId?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty,
                    activeAttemptId: item.activeAttemptId?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty,
                    hasPendingFulfillment: item.hasPendingFulfillment,
                    pendingOrderCode: item.pendingOrderCode?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
                )
            }
        )
    }
}
