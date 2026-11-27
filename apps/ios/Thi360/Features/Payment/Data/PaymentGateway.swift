import Foundation
import HTTPTypes
import OpenAPIRuntime

enum ProductLookupResult: Equatable, Sendable {
    case success(LoadedProductDetail)
    case notFound
    case gone(Components.Schemas.Product)
    case unavailable(LoadedProductDetail?)
    case failure(MobileApiError)
}

enum PriceQuoteResult: Equatable, Sendable {
    case success(Components.Schemas.PriceQuote)
    case gone(Components.Schemas.Product)
    case productUnavailable
    case notFound
    case voucherRejected(VoucherErrorKey)
    case rateLimited
    case unauthorized
    case failure(MobileApiError)
}

enum ExamAccessResult: Equatable, Sendable {
    case success([Components.Schemas.ExamAccessItem])
    case forbidden
    case failure(MobileApiError)
}

enum CreateOrderOutcome: Equatable, Sendable {
    case success(Components.Schemas.CreateOrderResponse)
    case priceChanged(Components.Schemas.PriceQuote)
    case accountChanged
    case productGone(Components.Schemas.Product)
    case productUnavailable
    case voucherRejected(VoucherErrorKey)
    case paymentAttemptsExceeded
    case unauthorized
    case deterministicFailure(MobileApiError)
}

enum VnpayVerifyResult: Equatable, Sendable {
    case success(Components.Schemas.VnpayReturnResponse)
    case invalidSignature
    case orderNotFound
    case failure(MobileApiError)
}

protocol PaymentGateway: Sendable {
    func fetchCurrentUser() async throws -> Components.Schemas.MeResponse
    func loadProduct(code: String) async -> ProductLookupResult
    func requestQuote(
        code: String,
        voucherCode: String?,
        seenPrice: Int64?,
        seenVersionNo: Int?
    ) async -> PriceQuoteResult
    func queryExamAccess(codes: [String]) async -> ExamAccessResult
    func createOrder(
        request: Components.Schemas.CreateOrderRequest,
        idempotencyKey: String
    ) async throws -> CreateOrderOutcome
    func verifyVnpayReturn(vnpParams: [String: String]) async -> VnpayVerifyResult
    func listOrders(page: Int, size: Int) async throws -> Components.Schemas.PageResponseOrderSummary
    func getOrder(orderCode: String) async throws -> Components.Schemas.OrderDetail
    func payOrder(orderCode: String, idempotencyKey: String) async throws -> Components.Schemas.PayOrderResponse
    func cancelOrder(orderCode: String, idempotencyKey: String) async throws -> Components.Schemas.OrderDetail
}

final class PaymentProductWireCaptureStore: @unchecked Sendable {
    private let lock = NSLock()
    private var bundleMetaByProductCode: [String: [String: BundleItemMeta]] = [:]

    func record(productCode: String, bodyData: Data) {
        let meta = Self.decodeBundleItemMeta(from: bodyData)
        lock.lock()
        bundleMetaByProductCode[productCode] = meta
        lock.unlock()
    }

    func consumeMeta(for productCode: String) -> [String: BundleItemMeta] {
        lock.lock()
        defer { lock.unlock() }
        return bundleMetaByProductCode[productCode] ?? [:]
    }

    static func decodeBundleItemMeta(from data: Data) -> [String: BundleItemMeta] {
        struct RawItem: Decodable {
            let code: String
            let subjectName: Components.Schemas.LocalizedName?
            let questionCount: Components.Schemas.IntRange?
            let durationMinutes: Components.Schemas.IntRange?
            let maxPoints: Components.Schemas.DecimalRange?
        }
        struct RawEnvelope: Decodable {
            let items: [RawItem]?
            let product: RawProduct?
        }
        struct RawProduct: Decodable {
            let items: [RawItem]?
        }

        guard let decoded = try? MobileAPIClient.makeJSONDecoder().decode(RawEnvelope.self, from: data) else {
            return [:]
        }
        let items = decoded.items ?? decoded.product?.items ?? []
        var map: [String: BundleItemMeta] = [:]
        for item in items {
            map[item.code] = BundleItemMeta(
                subjectName: item.subjectName,
                questionCount: item.questionCount,
                durationMinutes: item.durationMinutes,
                maxPoints: item.maxPoints
            )
        }
        return map
    }
}

struct PaymentProductWireMiddleware: ClientMiddleware {
    private let captureStore: PaymentProductWireCaptureStore

    init(captureStore: PaymentProductWireCaptureStore) {
        self.captureStore = captureStore
    }

    func intercept(
        _ request: HTTPRequest,
        body: HTTPBody?,
        baseURL: URL,
        operationID: String,
        next: @Sendable (HTTPRequest, HTTPBody?, URL) async throws -> (HTTPResponse, HTTPBody?)
    ) async throws -> (HTTPResponse, HTTPBody?) {
        let (response, responseBody) = try await next(request, body, baseURL)
        guard operationID == Operations.getStorefrontProduct.id,
              response.status.code == 200,
              let responseBody
        else {
            return (response, responseBody)
        }

        let collected = try await Data(collecting: responseBody, upTo: 4_194_304)
        if let rawPath = request.path {
            let pathOnly = rawPath.split(separator: "?", maxSplits: 1).first.map(String.init) ?? rawPath
            if let lastSegment = pathOnly.split(separator: "/").last {
                let code = String(lastSegment).removingPercentEncoding ?? String(lastSegment)
                captureStore.record(productCode: code, bodyData: collected)
            }
        }
        return (response, HTTPBody(collected))
    }
}

final class OpenApiPaymentGateway: PaymentGateway, Sendable {
    private let publicClient: Client
    private let productClient: Client
    private let authenticatedClient: Client
    private let productCaptureStore: PaymentProductWireCaptureStore
    private let meFetcher: @Sendable () async throws -> Components.Schemas.MeResponse

    init(
        publicClient: Client,
        productClient: Client? = nil,
        authenticatedClient: Client,
        productCaptureStore: PaymentProductWireCaptureStore = PaymentProductWireCaptureStore(),
        meFetcher: @escaping @Sendable () async throws -> Components.Schemas.MeResponse
    ) {
        self.publicClient = publicClient
        self.productClient = productClient ?? publicClient
        self.authenticatedClient = authenticatedClient
        self.productCaptureStore = productCaptureStore
        self.meFetcher = meFetcher
    }

    convenience init(apiClient: MobileAPIClient) {
        let captureStore = PaymentProductWireCaptureStore()
        let wireMiddleware = PaymentProductWireMiddleware(captureStore: captureStore)
        let productClient = apiClient.makePublicClient(leadingMiddlewares: [wireMiddleware])
        self.init(
            publicClient: apiClient.publicClient,
            productClient: productClient,
            authenticatedClient: apiClient.authenticatedClient,
            productCaptureStore: captureStore,
            meFetcher: { try await apiClient.me() }
        )
    }

    func fetchCurrentUser() async throws -> Components.Schemas.MeResponse {
        try await meFetcher()
    }

    func loadProduct(code: String) async -> ProductLookupResult {
        do {
            let output = try await productClient.getStorefrontProduct(path: .init(code: code))
            switch output {
            case let .ok(response):
                let detail = try response.body.json
                let meta = productCaptureStore.consumeMeta(for: code)
                return .success(LoadedProductDetail(detail: detail, bundleItemMetaByCode: meta))
            case .notFound:
                return .notFound
            case let .gone(response):
                let payload = try response.body.json
                return .gone(payload.product)
            case let .conflict(response):
                let bodyData = try await Self.collectBodyData(try response.body.any)
                let apiError = Self.decodeApiError(statusCode: 409, data: bodyData)
                if apiError.apiCode == "PRODUCT_UNAVAILABLE" {
                    let embedded = Self.decodeEmbeddedProductDetail(from: bodyData)
                    return .unavailable(embedded)
                }
                return .failure(apiError)
            case let .badRequest(response):
                return .failure(await MobileApiError.decodeErrorBody(statusCode: 400, body: try response.body.any))
            case let .unauthorized(response):
                return .failure(await MobileApiError.decodeErrorBody(statusCode: 401, body: try response.body.any))
            case let .forbidden(response):
                return .failure(await MobileApiError.decodeErrorBody(statusCode: 403, body: try response.body.any))
            case let .preconditionFailed(response):
                return .failure(await MobileApiError.decodeErrorBody(statusCode: 412, body: try response.body.any))
            case let .contentTooLarge(response):
                return .failure(await MobileApiError.decodeErrorBody(statusCode: 413, body: try response.body.any))
            case let .unprocessableContent(response):
                return .failure(await MobileApiError.decodeErrorBody(statusCode: 422, body: try response.body.any))
            case let .tooManyRequests(response):
                return .failure(await MobileApiError.decodeErrorBody(statusCode: 429, body: try response.body.any))
            case let .internalServerError(response):
                return .failure(await MobileApiError.decodeErrorBody(statusCode: 500, body: try response.body.any))
            case let .notImplemented(response):
                return .failure(await MobileApiError.decodeErrorBody(statusCode: 501, body: try response.body.any))
            case let .badGateway(response):
                return .failure(await MobileApiError.decodeErrorBody(statusCode: 502, body: try response.body.any))
            case let .undocumented(statusCode, payload):
                return .failure(await Self.decodeOptionalError(statusCode: statusCode, body: payload.body))
            }
        } catch {
            return .failure(MobileApiError.classify(error))
        }
    }

    func requestQuote(
        code: String,
        voucherCode: String?,
        seenPrice: Int64?,
        seenVersionNo: Int?
    ) async -> PriceQuoteResult {
        do {
            let output = try await authenticatedClient.quoteMyPrice(
                body: .json(.init(
                    code: code,
                    seenPrice: seenPrice,
                    seenVersionNo: seenVersionNo.map(Int32.init),
                    voucherCode: voucherCode
                ))
            )
            switch output {
            case let .ok(response):
                return .success(try response.body.json)
            case .unauthorized:
                return .unauthorized
            case .notFound:
                return .notFound
            case let .gone(response):
                let payload = try response.body.json
                return .gone(payload.product)
            case let .conflict(response):
                let apiError = await MobileApiError.decodeErrorBody(statusCode: 409, body: try response.body.any)
                if apiError.apiCode == "PRODUCT_UNAVAILABLE" {
                    return .productUnavailable
                }
                return .failure(apiError)
            case let .unprocessableContent(response):
                let apiError = await MobileApiError.decodeErrorBody(statusCode: 422, body: try response.body.any)
                return .voucherRejected(VoucherErrorMapper.map(apiError))
            case .tooManyRequests:
                return .rateLimited
            case let .badRequest(response):
                let apiError = await MobileApiError.decodeErrorBody(statusCode: 400, body: try response.body.any)
                if apiError.apiCode?.hasPrefix("VOUCHER_") == true {
                    return .voucherRejected(VoucherErrorMapper.map(apiError))
                }
                return .failure(apiError)
            case let .forbidden(response):
                return .failure(await MobileApiError.decodeErrorBody(statusCode: 403, body: try response.body.any))
            case let .preconditionFailed(response):
                return .failure(await MobileApiError.decodeErrorBody(statusCode: 412, body: try response.body.any))
            case let .contentTooLarge(response):
                return .failure(await MobileApiError.decodeErrorBody(statusCode: 413, body: try response.body.any))
            case let .internalServerError(response):
                return .failure(await MobileApiError.decodeErrorBody(statusCode: 500, body: try response.body.any))
            case let .notImplemented(response):
                return .failure(await MobileApiError.decodeErrorBody(statusCode: 501, body: try response.body.any))
            case let .badGateway(response):
                return .failure(await MobileApiError.decodeErrorBody(statusCode: 502, body: try response.body.any))
            case let .undocumented(statusCode, payload):
                let apiError = await Self.decodeOptionalError(statusCode: statusCode, body: payload.body)
                if statusCode == 429 || apiError.apiCode == "RATE_LIMITED" {
                    return .rateLimited
                }
                return .failure(apiError)
            }
        } catch {
            return .failure(MobileApiError.classify(error))
        }
    }

    func queryExamAccess(codes: [String]) async -> ExamAccessResult {
        do {
            let output = try await authenticatedClient.queryMyExamAccess(
                body: .json(.init(codes: codes))
            )
            switch output {
            case let .ok(response):
                let payload = try response.body.json
                return .success(payload.items)
            case .forbidden:
                return .forbidden
            case let .badRequest(response):
                return .failure(await MobileApiError.decodeErrorBody(statusCode: 400, body: try response.body.any))
            case let .unauthorized(response):
                return .failure(await MobileApiError.decodeErrorBody(statusCode: 401, body: try response.body.any))
            case let .notFound(response):
                return .failure(await MobileApiError.decodeErrorBody(statusCode: 404, body: try response.body.any))
            case let .conflict(response):
                return .failure(await MobileApiError.decodeErrorBody(statusCode: 409, body: try response.body.any))
            case let .preconditionFailed(response):
                return .failure(await MobileApiError.decodeErrorBody(statusCode: 412, body: try response.body.any))
            case let .contentTooLarge(response):
                return .failure(await MobileApiError.decodeErrorBody(statusCode: 413, body: try response.body.any))
            case let .unprocessableContent(response):
                return .failure(await MobileApiError.decodeErrorBody(statusCode: 422, body: try response.body.any))
            case let .tooManyRequests(response):
                return .failure(await MobileApiError.decodeErrorBody(statusCode: 429, body: try response.body.any))
            case let .internalServerError(response):
                return .failure(await MobileApiError.decodeErrorBody(statusCode: 500, body: try response.body.any))
            case let .notImplemented(response):
                return .failure(await MobileApiError.decodeErrorBody(statusCode: 501, body: try response.body.any))
            case let .badGateway(response):
                return .failure(await MobileApiError.decodeErrorBody(statusCode: 502, body: try response.body.any))
            case let .undocumented(statusCode, payload):
                return .failure(await Self.decodeOptionalError(statusCode: statusCode, body: payload.body))
            }
        } catch {
            return .failure(MobileApiError.classify(error))
        }
    }

    func createOrder(
        request: Components.Schemas.CreateOrderRequest,
        idempotencyKey: String
    ) async throws -> CreateOrderOutcome {
        do {
            let output = try await authenticatedClient.createMyOrder(
                headers: .init(Idempotency_hyphen_Key: idempotencyKey),
                body: .json(request)
            )
            switch output {
            case let .created(response):
                return .success(try response.body.json)
            case let .gone(response):
                if let body = try? response.body.any,
                   let bodyData = try? await Self.collectBodyData(body),
                   let gone = try? MobileAPIClient.makeJSONDecoder().decode(Components.Schemas.ProductGoneResponse.self, from: bodyData)
                {
                    return .productGone(gone.product)
                }
                return .productGone(.init(code: request.code, name: request.code))
            case let .conflict(response):
                guard let body = try? response.body.any,
                      let bodyData = try? await Self.collectBodyData(body)
                else {
                    return .deterministicFailure(.unexpectedResponse(statusCode: 409))
                }
                let apiError = Self.decodeApiError(statusCode: 409, data: bodyData)
                switch apiError.apiCode {
                case "PRICE_CHANGED":
                    if let decoded = try? MobileAPIClient.makeJSONDecoder().decode(Components.Schemas.OrderPriceChangedResponse.self, from: bodyData),
                       let quote = decoded.quote
                    {
                        return .priceChanged(quote)
                    }
                    return .deterministicFailure(apiError)
                case "ACCOUNT_CHANGED":
                    return .accountChanged
                case "PRODUCT_UNAVAILABLE":
                    return .productUnavailable
                default:
                    return .deterministicFailure(apiError)
                }
            case let .unprocessableContent(response):
                let apiError = await Self.decodeOptionalError(statusCode: 422, body: try? response.body.any)
                if apiError.apiCode == "PAYMENT_ATTEMPTS_EXCEEDED" {
                    return .paymentAttemptsExceeded
                }
                if apiError.apiCode?.hasPrefix("VOUCHER_") == true {
                    return .voucherRejected(VoucherErrorMapper.map(apiError))
                }
                return .deterministicFailure(apiError)
            case .unauthorized:
                return .unauthorized
            case let .tooManyRequests(response):
                throw await Self.decodeOptionalError(statusCode: 429, body: try? response.body.any)
            case let .internalServerError(response):
                throw await Self.decodeOptionalError(statusCode: 500, body: try? response.body.any)
            case let .notImplemented(response):
                throw await Self.decodeOptionalError(statusCode: 501, body: try? response.body.any)
            case let .badGateway(response):
                throw await Self.decodeOptionalError(statusCode: 502, body: try? response.body.any)
            case let .badRequest(response):
                let apiError = await Self.decodeOptionalError(statusCode: 400, body: try? response.body.any)
                return .deterministicFailure(apiError)
            case let .forbidden(response):
                let apiError = await Self.decodeOptionalError(statusCode: 403, body: try? response.body.any)
                return .deterministicFailure(apiError)
            case let .notFound(response):
                let apiError = await Self.decodeOptionalError(statusCode: 404, body: try? response.body.any)
                return .deterministicFailure(apiError)
            case let .preconditionFailed(response):
                let apiError = await Self.decodeOptionalError(statusCode: 412, body: try? response.body.any)
                return .deterministicFailure(apiError)
            case let .contentTooLarge(response):
                let apiError = await Self.decodeOptionalError(statusCode: 413, body: try? response.body.any)
                return .deterministicFailure(apiError)
            case let .undocumented(statusCode, payload):
                let apiError = await Self.decodeOptionalError(statusCode: statusCode, body: payload.body)
                if IdempotentOperation.shouldRetainKey(forStatusCode: statusCode) {
                    throw apiError
                }
                return .deterministicFailure(apiError)
            }
        } catch let clientError as ClientError {
            if let statusCode = clientError.response?.status.code,
               !IdempotentOperation.shouldRetainKey(forStatusCode: statusCode)
            {
                return .deterministicFailure(.unexpectedResponse(statusCode: statusCode))
            }
            throw MobileApiError.classify(clientError)
        } catch {
            throw MobileApiError.classify(error)
        }
    }

    func verifyVnpayReturn(vnpParams: [String: String]) async -> VnpayVerifyResult {
        do {
            let output = try await publicClient.verifyVnpayReturn(
                query: .init(params: .init(additionalProperties: vnpParams))
            )
            switch output {
            case let .ok(response):
                return .success(try response.body.json)
            case .badRequest:
                return .invalidSignature
            case let .unprocessableContent(response):
                let apiError = await MobileApiError.decodeErrorBody(statusCode: 422, body: try response.body.any)
                if apiError.apiCode == "VNPAY_SIGNATURE_INVALID" || apiError.statusCode == 422 {
                    return .invalidSignature
                }
                return .failure(apiError)
            case .notFound:
                return .orderNotFound
            case let .unauthorized(response):
                return .failure(await MobileApiError.decodeErrorBody(statusCode: 401, body: try response.body.any))
            case let .forbidden(response):
                return .failure(await MobileApiError.decodeErrorBody(statusCode: 403, body: try response.body.any))
            case let .conflict(response):
                return .failure(await MobileApiError.decodeErrorBody(statusCode: 409, body: try response.body.any))
            case let .preconditionFailed(response):
                return .failure(await MobileApiError.decodeErrorBody(statusCode: 412, body: try response.body.any))
            case let .contentTooLarge(response):
                return .failure(await MobileApiError.decodeErrorBody(statusCode: 413, body: try response.body.any))
            case let .tooManyRequests(response):
                return .failure(await MobileApiError.decodeErrorBody(statusCode: 429, body: try response.body.any))
            case let .internalServerError(response):
                return .failure(await MobileApiError.decodeErrorBody(statusCode: 500, body: try response.body.any))
            case let .notImplemented(response):
                return .failure(await MobileApiError.decodeErrorBody(statusCode: 501, body: try response.body.any))
            case let .badGateway(response):
                return .failure(await MobileApiError.decodeErrorBody(statusCode: 502, body: try response.body.any))
            case let .undocumented(statusCode, payload):
                let apiError = await Self.decodeOptionalError(statusCode: statusCode, body: payload.body)
                if apiError.apiCode == "VNPAY_SIGNATURE_INVALID" || statusCode == 400 || statusCode == 422 {
                    return .invalidSignature
                }
                if statusCode == 404 || apiError.apiCode == "ORDER_NOT_FOUND" {
                    return .orderNotFound
                }
                return .failure(apiError)
            }
        } catch {
            return .failure(MobileApiError.classify(error))
        }
    }

    func listOrders(page: Int, size: Int) async throws -> Components.Schemas.PageResponseOrderSummary {
        do {
            let output = try await authenticatedClient.listMyOrders(
                query: .init(page: Int32(page), size: Int32(size))
            )
            switch output {
            case let .ok(response):
                return try response.body.json
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
            case let .gone(response):
                throw await MobileApiError.decodeErrorBody(statusCode: 410, body: try response.body.any)
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
        } catch let clientError as ClientError {
            if let statusCode = clientError.response?.status.code {
                throw MobileApiError.unexpectedResponse(statusCode: statusCode)
            }
            throw MobileApiError.classify(clientError)
        } catch {
            throw MobileApiError.classify(error)
        }
    }

    func getOrder(orderCode: String) async throws -> Components.Schemas.OrderDetail {
        do {
            let output = try await authenticatedClient.getMyOrder(path: .init(code: orderCode))
            switch output {
            case let .ok(response):
                return try response.body.json
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
            case let .gone(response):
                throw await MobileApiError.decodeErrorBody(statusCode: 410, body: try response.body.any)
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
        } catch let clientError as ClientError {
            if let statusCode = clientError.response?.status.code {
                throw MobileApiError.unexpectedResponse(statusCode: statusCode)
            }
            throw MobileApiError.classify(clientError)
        } catch {
            throw MobileApiError.classify(error)
        }
    }

    func payOrder(orderCode: String, idempotencyKey: String) async throws -> Components.Schemas.PayOrderResponse {
        do {
            let output = try await authenticatedClient.payMyOrder(
                path: .init(code: orderCode),
                headers: .init(Idempotency_hyphen_Key: idempotencyKey)
            )
            switch output {
            case let .ok(response):
                return try response.body.json
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
            case let .gone(response):
                throw await MobileApiError.decodeErrorBody(statusCode: 410, body: try response.body.any)
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
        } catch let clientError as ClientError {
            if let statusCode = clientError.response?.status.code {
                throw MobileApiError.unexpectedResponse(statusCode: statusCode)
            }
            throw MobileApiError.classify(clientError)
        } catch {
            throw MobileApiError.classify(error)
        }
    }

    func cancelOrder(orderCode: String, idempotencyKey: String) async throws -> Components.Schemas.OrderDetail {
        do {
            let output = try await authenticatedClient.cancelMyOrder(
                path: .init(code: orderCode),
                headers: .init(Idempotency_hyphen_Key: idempotencyKey)
            )
            switch output {
            case let .ok(response):
                return try response.body.json
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
            case let .gone(response):
                throw await MobileApiError.decodeErrorBody(statusCode: 410, body: try response.body.any)
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
        } catch let clientError as ClientError {
            if let statusCode = clientError.response?.status.code {
                throw MobileApiError.unexpectedResponse(statusCode: statusCode)
            }
            throw MobileApiError.classify(clientError)
        } catch {
            throw MobileApiError.classify(error)
        }
    }

    private static func collectBodyData(_ body: HTTPBody) async throws -> Data {
        try await Data(collecting: body, upTo: 2_097_152)
    }

    private static func decodeApiError(statusCode: Int, data: Data) -> MobileApiError {
        if let decoded = try? MobileAPIClient.makeJSONDecoder().decode(ApiError.self, from: data) {
            return .api(statusCode: statusCode, error: decoded)
        }
        return .unexpectedResponse(statusCode: statusCode)
    }

    private static func decodeOptionalError(statusCode: Int, body: HTTPBody?) async -> MobileApiError {
        if let body {
            return await MobileApiError.decodeErrorBody(statusCode: statusCode, body: body)
        }
        return .unexpectedResponse(statusCode: statusCode)
    }

    private static func decodeEmbeddedProductDetail(from data: Data) -> LoadedProductDetail? {
        struct Envelope: Decodable {
            let product: Components.Schemas.ProductDetail?
        }
        guard let envelope = try? MobileAPIClient.makeJSONDecoder().decode(Envelope.self, from: data),
              let product = envelope.product
        else {
            return nil
        }
        let meta = PaymentProductWireCaptureStore.decodeBundleItemMeta(from: data)
        return LoadedProductDetail(detail: product, bundleItemMetaByCode: meta)
    }
}
