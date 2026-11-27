import Foundation
import HTTPTypes
import OpenAPIRuntime
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class FakePaymentBrowser: PaymentBrowser {
    private(set) var openedURLs: [URL] = []
    var outcomes: [PaymentBrowserOutcome]
    var onOpen: (@MainActor (URL) async -> PaymentBrowserOutcome)?

    init(outcomes: [PaymentBrowserOutcome] = [.dismissed]) {
        self.outcomes = outcomes
    }

    func open(_ url: URL) async -> PaymentBrowserOutcome {
        openedURLs.append(url)
        if let onOpen {
            return await onOpen(url)
        }
        if outcomes.count > 1 {
            return outcomes.removeFirst()
        }
        return outcomes.first ?? .dismissed
    }
}

final class SequenceKeyGenerator: @unchecked Sendable {
    private let lock = NSLock()
    private var counter = 0
    private let prefix: String

    init(prefix: String = "00000000-0000-4000-8000-") {
        self.prefix = prefix
    }

    func next() -> String {
        lock.lock()
        defer { lock.unlock() }
        counter += 1
        return String(format: "%@%012d", prefix, counter)
    }

    var generatedCount: Int {
        lock.lock()
        defer { lock.unlock() }
        return counter
    }
}

enum PaymentFixtureHelper {
    static func loadJSONObject(apiFixture: String) throws -> [String: Any] {
        let data = try ContractFixtureLoader.loadApiFixture(apiFixture)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
    }

    static func mutatingFixture(
        _ apiFixture: String,
        mutate: (inout [String: Any]) -> Void
    ) throws -> Data {
        var obj = try loadJSONObject(apiFixture: apiFixture)
        mutate(&obj)
        return try JSONSerialization.data(withJSONObject: obj, options: [.sortedKeys])
    }

    static func decodeFixture<T: Decodable>(_ type: T.Type, apiFixture: String) throws -> T {
        let data = try ContractFixtureLoader.loadApiFixture(apiFixture)
        return try MobileAPIClient.makeJSONDecoder().decode(type, from: data)
    }

    static func decodeJSON<T: Decodable>(_ type: T.Type, data: Data) throws -> T {
        try MobileAPIClient.makeJSONDecoder().decode(type, from: data)
    }

    static func requestBodyJSON(_ request: RecordedHTTPRequest) throws -> [String: Any] {
        let data = try XCTUnwrap(request.bodyData)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
    }
}

@MainActor
enum PaymentTestFactory {
    struct Harness {
        let sessionStore: SessionStore
        let tokenStore: InMemoryTokenStore
        let transport: ContractFakeTransport
        let preferences: AppPreferences
        let storeProvider: AccountScopedStoreProvider
        let pendingOrderStore: PendingPaymentOrderStore
        let gateway: OpenApiPaymentGateway
        let browser: FakePaymentBrowser
        let clock: ManualClock
        let logSink: InMemoryLogSink
        let storeDirURL: URL
    }

    static func makeHarness(
        accountId: String = "019320a0-0000-7000-8000-000000000001",
        username: String = "student.demo",
        locale: AppLocale = .vi,
        environment: BuildEnvironment = .dev,
        browserOutcomes: [PaymentBrowserOutcome] = [.dismissed],
        configure: (ContractFakeTransport) throws -> Void = { _ in }
    ) throws -> Harness {
        let storeDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("thi360-payment-test-\(UUID().uuidString)", isDirectory: true)
        let storeProvider = AccountScopedStoreProvider(rootDirectoryURL: storeDir)
        let logSink = InMemoryLogSink()
        let tokens = AccountTestFactory.sampleTokens(username: username, accountId: accountId)
        let (sessionStore, tokenStore, transport, preferences) = try SessionTestFactory.makeContractStack(
            initialTokens: tokens,
            locale: locale,
            environment: environment,
            logSink: logSink,
            storeProvider: storeProvider,
            configure: { transport in
                try transport.registerAuthDefaults()
                try configure(transport)
            }
        )
        let pendingOrderStore = PendingPaymentOrderStore(
            storeProvider: storeProvider,
            accountIdProvider: { (try? tokenStore.read())?.accountId }
        )
        let gateway = OpenApiPaymentGateway(apiClient: sessionStore.apiClient)
        let browser = FakePaymentBrowser(outcomes: browserOutcomes)
        let clock = ManualClock()

        return Harness(
            sessionStore: sessionStore,
            tokenStore: tokenStore,
            transport: transport,
            preferences: preferences,
            storeProvider: storeProvider,
            pendingOrderStore: pendingOrderStore,
            gateway: gateway,
            browser: browser,
            clock: clock,
            logSink: logSink,
            storeDirURL: storeDir
        )
    }

    static func registerExamCheckoutDefaults(
        on transport: ContractFakeTransport,
        code: String = "EXAM-DEMO-01",
        quoteFixture: String = "price-quote-200.json",
        accessFixture: String = "exam-access-query-200.json"
    ) throws {
        try transport.registerFixture(
            method: .get,
            path: "/api/v1/public/storefront/products/\(code)",
            statusCode: 200,
            relativeFixturePath: "api/storefront-products-detail-200-exam.json"
        )
        try transport.registerFixture(
            method: .post,
            path: "/api/v1/me/price-quote",
            statusCode: 200,
            relativeFixturePath: "api/\(quoteFixture)"
        )
        try transport.registerFixture(
            method: .post,
            path: "/api/v1/me/exam-access/query",
            statusCode: 200,
            relativeFixturePath: "api/\(accessFixture)"
        )
    }

    static func registerBundleCheckoutDefaults(
        on transport: ContractFakeTransport,
        code: String = "BUNDLE-DEMO-01",
        quoteFixture: String = "price-quote-200.json"
    ) throws {
        try transport.registerFixture(
            method: .get,
            path: "/api/v1/public/storefront/products/\(code)",
            statusCode: 200,
            relativeFixturePath: "api/storefront-products-detail-200-bundle.json"
        )
        try transport.registerFixture(
            method: .post,
            path: "/api/v1/me/price-quote",
            statusCode: 200,
            relativeFixturePath: "api/\(quoteFixture)"
        )
        let bundleAccessData = try PaymentFixtureHelper.mutatingFixture("exam-access-query-200.json") { obj in
            obj["items"] = [
                [
                    "code": code,
                    "state": "NONE",
                    "attemptsLeft": 0,
                    "entitlementId": NSNull(),
                    "activeAttemptId": NSNull(),
                    "hasPendingFulfillment": false,
                    "pendingOrderCode": NSNull(),
                    "items": [
                        [
                            "examId": "00000000-0000-7000-8000-000000000001",
                            "code": "EXAM-DEMO-02",
                            "name": "Phần Tư duy định lượng",
                            "state": "CAN_START",
                            "attemptsLeft": 2,
                            "entitlementId": "ent-1",
                            "activeAttemptId": NSNull(),
                            "hasPendingFulfillment": false,
                            "pendingOrderCode": NSNull()
                        ],
                        [
                            "examId": "00000000-0000-7000-8000-000000000002",
                            "code": "EXAM-DEMO-03",
                            "name": "Phần Tư duy định tính",
                            "state": "NONE",
                            "attemptsLeft": 0,
                            "entitlementId": NSNull(),
                            "activeAttemptId": NSNull(),
                            "hasPendingFulfillment": false,
                            "pendingOrderCode": NSNull()
                        ]
                    ]
                ]
            ]
        }
        transport.registerJSON(
            method: .post,
            path: "/api/v1/me/exam-access/query",
            statusCode: 200,
            data: bundleAccessData
        )
    }
}
