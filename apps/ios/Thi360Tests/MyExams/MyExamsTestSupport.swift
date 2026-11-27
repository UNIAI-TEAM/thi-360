import Foundation
import HTTPTypes
import OpenAPIRuntime
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

enum MyExamsTestFixtures {
    private struct IndexedFixtureEntry: Decodable {
        let file: String
        let operationId: String
        let status: Int
        let variant: String?
    }

    private static func loadIndexEntries() throws -> [IndexedFixtureEntry] {
        let data = try ContractFixtureLoader.loadData(relativePath: "api/index.json")
        return try JSONDecoder().decode([IndexedFixtureEntry].self, from: data)
    }

    static func loadIndexedData(
        operationId: String,
        status: Int,
        variant: String? = nil
    ) throws -> Data {
        let entries = try loadIndexEntries()
        let byOpStatus = entries.filter { $0.operationId == operationId && $0.status == status }
        let matched: IndexedFixtureEntry?
        if let variant {
            matched = byOpStatus.first { ($0.variant ?? "") == variant }
        } else {
            matched = byOpStatus.first { ($0.variant ?? "").isEmpty } ?? byOpStatus.first
        }
        guard let matched else {
            throw NSError(
                domain: "MyExamsTestFixtures",
                code: 1,
                userInfo: [NSLocalizedDescriptionKey: "Missing indexed fixture for \(operationId) status=\(status) variant=\(variant ?? "*")"]
            )
        }
        let relative = matched.file.contains("/") ? matched.file : "api/\(matched.file)"
        return try ContractFixtureLoader.loadData(relativePath: relative)
    }

    static func loadJSONObject(
        operationId: String,
        status: Int,
        variant: String? = nil
    ) throws -> [String: Any] {
        let data = try loadIndexedData(operationId: operationId, status: status, variant: variant)
        guard let object = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw NSError(domain: "MyExamsTestFixtures", code: 2)
        }
        return object
    }

    static func loadJSONArray(
        operationId: String,
        status: Int,
        variant: String? = nil
    ) throws -> [[String: Any]] {
        let data = try loadIndexedData(operationId: operationId, status: status, variant: variant)
        guard let array = try JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
            throw NSError(domain: "MyExamsTestFixtures", code: 3)
        }
        return array
    }

    static func encodeJSON(_ object: Any) throws -> Data {
        try JSONSerialization.data(withJSONObject: object, options: [.sortedKeys])
    }

    static func myExamsItemTemplates() throws -> [[String: Any]] {
        let root = try loadJSONObject(operationId: Operations.listMyExams.id, status: 200, variant: "")
        guard let items = root["items"] as? [[String: Any]], !items.isEmpty else {
            throw NSError(domain: "MyExamsTestFixtures", code: 4)
        }
        return items
    }

    static func cloneMyExamCardJSON(
        templateState: String = "CAN_START",
        mutate: (inout [String: Any]) -> Void
    ) throws -> [String: Any] {
        let templates = try myExamsItemTemplates()
        var item = templates.first(where: { ($0["state"] as? String) == templateState }) ?? templates[0]
        mutate(&item)
        return item
    }

    static func makeMyExamsPayload(items: [[String: Any]]) throws -> Data {
        var root = try loadJSONObject(operationId: Operations.listMyExams.id, status: 200, variant: "")
        root["items"] = items
        return try encodeJSON(root)
    }

    static func decodeMyExamCards(from data: Data) throws -> [MyExamCard] {
        let decoder = MobileAPIClient.makeJSONDecoder()
        let generated = try decoder.decode(Components.Schemas.MyExamsResponse.self, from: data)
        return MyExamsAPI.mapCards(generated.items, supplementalJSONData: data)
    }

    static func cloneBundleRefTemplate(bundleId: String, code: String, name: String) throws -> [String: Any] {
        let templates = try myExamsItemTemplates()
        guard let bundledCard = templates.first(where: { !((($0["bundles"] as? [[String: Any]]) ?? []).isEmpty) }),
              var bundleRef = (bundledCard["bundles"] as? [[String: Any]])?.first
        else {
            throw NSError(domain: "MyExamsTestFixtures", code: 5)
        }
        bundleRef["bundleId"] = bundleId
        bundleRef["code"] = code
        bundleRef["name"] = name
        return bundleRef
    }

    static func historyItemTemplates() throws -> [[String: Any]] {
        let root = try loadJSONObject(operationId: Operations.listHistory.id, status: 200, variant: "")
        guard let items = root["items"] as? [[String: Any]], !items.isEmpty else {
            throw NSError(domain: "MyExamsTestFixtures", code: 6)
        }
        return items
    }

    static func cloneHistoryItemJSON(
        templateState: String = "SCORED",
        mutate: (inout [String: Any]) -> Void
    ) throws -> [String: Any] {
        let templates = try historyItemTemplates()
        var item = templates.first(where: { ($0["state"] as? String) == templateState }) ?? templates[0]
        mutate(&item)
        return item
    }

    static func makeHistoryPagePayload(
        items: [[String: Any]],
        page: Int = 0,
        size: Int = 20,
        totalItems: Int? = nil,
        totalPages: Int? = nil
    ) throws -> Data {
        var root = try loadJSONObject(operationId: Operations.listHistory.id, status: 200, variant: "")
        let resolvedTotal = totalItems ?? items.count
        let resolvedPages = totalPages ?? max(0, (resolvedTotal + size - 1) / size)
        root["items"] = items
        root["page"] = page
        root["size"] = size
        root["totalItems"] = resolvedTotal
        root["totalPages"] = resolvedPages
        return try encodeJSON(root)
    }

    static func decodeHistoryPage(from data: Data) throws -> HistoryPageResponse {
        let generated = try MobileAPIClient.makeJSONDecoder().decode(
            Components.Schemas.PageResponseAttemptHistoryItem.self,
            from: data
        )
        return HistoryAPI.mapPage(generated, rawJSONData: data)
    }

    static func makeMePayload(fullName: String?, username: String) throws -> Data {
        var root = try loadJSONObject(operationId: Operations.me.id, status: 200)
        root["username"] = username
        root["fullName"] = fullName ?? NSNull()
        return try encodeJSON(root)
    }

    // Plan A6 derivation: backend contract fixtures do not emit 500; derived by setting `code` to `INTERNAL_ERROR` on certified 429 fixture.
    static func makeInternalServerError500Data() throws -> Data {
        var root = try loadJSONObject(
            operationId: Operations.listStorefrontProducts.id,
            status: 429,
            variant: "rate-limited"
        )
        root["code"] = "INTERNAL_ERROR"
        return try encodeJSON(root)
    }

    // Plan A6 derivation: derived from certified listMyExams 403 forbidden fixture with code BAD_REQUEST for 400 testing.
    static func makeBadRequest400Data() throws -> Data {
        var root = try loadJSONObject(
            operationId: Operations.listMyExams.id,
            status: 403,
            variant: "forbidden"
        )
        root["code"] = "BAD_REQUEST"
        return try encodeJSON(root)
    }
}

final class MyExamsManualClock: AppClock, @unchecked Sendable {
    private let lock = NSLock()
    private var currentDate: Date

    init(now: Date = Date(timeIntervalSince1970: 1_763_632_800)) {
        self.currentDate = now
    }

    var now: Date {
        lock.lock()
        defer { lock.unlock() }
        return currentDate
    }

    func set(_ date: Date) {
        lock.lock()
        currentDate = date
        lock.unlock()
    }

    func advance(by seconds: TimeInterval) {
        lock.lock()
        currentDate = currentDate.addingTimeInterval(seconds)
        lock.unlock()
    }
}

final class RecordingRetrySleeper: MyExamsRetrySleeper, @unchecked Sendable {
    private let lock = NSLock()
    private var attempts: [Int] = []

    var recordedAttempts: [Int] {
        lock.lock()
        defer { lock.unlock() }
        return attempts
    }

    private func record(_ attempt: Int) {
        lock.lock()
        attempts.append(attempt)
        lock.unlock()
    }

    func sleep(attempt: Int) async throws {
        record(attempt)
    }
}

final class ControllableHistoryDebounceSleeper: HistoryDebounceSleeper, @unchecked Sendable {
    private let lock = NSLock()
    private var pending: [(id: UUID, ms: UInt64, continuation: CheckedContinuation<Void, Error>)] = []
    private var delays: [UInt64] = []

    var sleepCallDelays: [UInt64] {
        lock.lock()
        defer { lock.unlock() }
        return delays
    }

    var pendingCount: Int {
        lock.lock()
        defer { lock.unlock() }
        return pending.count
    }

    func sleep(milliseconds: UInt64) async throws {
        let entryId = UUID()
        try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { continuation in
                lock.lock()
                delays.append(milliseconds)
                pending.append((entryId, milliseconds, continuation))
                lock.unlock()
            }
        } onCancel: {
            lock.lock()
            if let index = pending.firstIndex(where: { $0.id == entryId }) {
                let removed = pending.remove(at: index)
                lock.unlock()
                removed.continuation.resume(throwing: CancellationError())
            } else {
                lock.unlock()
            }
        }
    }

    func fireNext() {
        lock.lock()
        let next = pending.isEmpty ? nil : pending.removeFirst()
        lock.unlock()
        next?.continuation.resume()
    }

    func fireAll() {
        lock.lock()
        let batch = pending
        pending.removeAll()
        lock.unlock()
        for item in batch {
            item.continuation.resume()
        }
    }
}

final class StubNetworkPathObserver: NetworkPathObserving, @unchecked Sendable {
    func start(onUpdate: @escaping @Sendable (Bool) -> Void) {}
    func stop() {}
}

@MainActor
enum MyExamsTestStackFactory {
    struct Stack {
        let sessionStore: SessionStore
        let tokenStore: InMemoryTokenStore
        let transport: ContractFakeTransport
        let myExamsAPI: MyExamsAPI
        let historyAPI: HistoryAPI
        let optionsCache: CatalogOptionsCache
        let connectivityMonitor: ConnectivityMonitor
        let clock: MyExamsManualClock
        let preferences: AppPreferences
        let defaultsSuiteName: String
    }

    static func makeStack(
        initialTokens: SessionTokens? = SessionTokens(
            accessToken: "test-access-token",
            refreshToken: "test-refresh-token",
            accessExpiresIn: 900,
            refreshExpiresIn: 86_400,
            mustChangePassword: false,
            username: "student.demo",
            accountId: "019320a0-0000-7000-8000-000000000001"
        ),
        locale: AppLocale = .vi,
        initialIsOnline: Bool = true,
        clock: MyExamsManualClock = MyExamsManualClock(),
        configure: (ContractFakeTransport) throws -> Void = { _ in }
    ) throws -> Stack {
        let suite = "vn.unicom.thi360.tests.myexams.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        let preferences = AppPreferences(userDefaults: defaults)
        preferences.locale = locale

        let tokenStore = InMemoryTokenStore(initialTokens: initialTokens)
        let transport = ContractFakeTransport()
        try transport.registerAuthDefaults()
        try configure(transport)

        let logger = RedactingAppLogger(environment: .dev, sink: InMemoryLogSink())
        let serverURL = URL(string: "https://api.thi360.test")!
        let localeProvider: @Sendable () async -> AppLocale = {
            await MainActor.run { preferences.locale }
        }

        let mobileClient = MobileAPIClient(
            serverURL: serverURL,
            tokenStore: tokenStore,
            transport: transport,
            logger: logger,
            localeProvider: localeProvider
        )
        let storeProvider = AccountScopedStoreProvider(
            rootDirectoryURL: FileManager.default.temporaryDirectory
                .appendingPathComponent("thi360-myexams-test-\(UUID().uuidString)", isDirectory: true)
        )
        let sessionStore = SessionStore(
            tokenStore: tokenStore,
            apiClient: mobileClient,
            storeProvider: storeProvider,
            logger: logger,
            initialStateOverride: initialTokens == nil ? nil : .active,
            skipsLaunchValidation: true
        )
        let myExamsAPI = MyExamsAPI(apiClient: mobileClient)
        let historyAPI = HistoryAPI(apiClient: mobileClient)
        let optionsCache = CatalogOptionsCache(apiClient: mobileClient, clock: clock)
        let monitor = ConnectivityMonitor(
            initialIsOnline: initialIsOnline,
            observer: StubNetworkPathObserver(),
            autoStart: false
        )

        return Stack(
            sessionStore: sessionStore,
            tokenStore: tokenStore,
            transport: transport,
            myExamsAPI: myExamsAPI,
            historyAPI: historyAPI,
            optionsCache: optionsCache,
            connectivityMonitor: monitor,
            clock: clock,
            preferences: preferences,
            defaultsSuiteName: suite
        )
    }

    static func registerDefaultMyExamsAndHistoryFixtures(on transport: ContractFakeTransport) throws {
        let myExamsData = try MyExamsTestFixtures.loadIndexedData(
            operationId: Operations.listMyExams.id,
            status: 200,
            variant: ""
        )
        let attemptsData = try MyExamsTestFixtures.loadIndexedData(
            operationId: Operations.listHistory.id,
            status: 200,
            variant: ""
        )
        let examOptions = try MyExamsTestFixtures.loadIndexedData(
            operationId: Operations.getOptions.id,
            status: 200,
            variant: "exam"
        )
        let subjectOptions = try MyExamsTestFixtures.loadIndexedData(
            operationId: Operations.getOptions.id,
            status: 200,
            variant: "subject"
        )
        transport.registerJSON(method: .get, path: "/api/v1/me/exams", data: myExamsData)
        transport.registerJSON(method: .get, path: "/api/v1/me/attempts", data: attemptsData)
        transport.register(method: .get, path: "/api/v1/catalog/options") { request in
            let query = queryItems(from: request)
            if query["group"] == "SUBJECT" {
                return ContractFakeTransport.jsonResponse(statusCode: 200, data: subjectOptions)
            }
            return ContractFakeTransport.jsonResponse(statusCode: 200, data: examOptions)
        }
    }

    nonisolated static func queryItems(from request: RecordedHTTPRequest) -> [String: String] {
        guard let components = URLComponents(string: "https://api.thi360.test\(request.path)") else {
            return [:]
        }
        var dict: [String: String] = [:]
        for item in components.queryItems ?? [] {
            dict[item.name] = item.value ?? ""
        }
        return dict
    }

    static func waitUntil(
        window: UIWindow? = nil,
        timeout: TimeInterval = 5.0,
        condition: () -> Bool
    ) async throws {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            window?.setNeedsLayout()
            window?.layoutIfNeeded()
            if condition() { return }
            try await Task.sleep(nanoseconds: 20_000_000)
        }
        XCTAssertTrue(condition(), "Timed out waiting for async condition")
    }

    static func findNavigationControllers(in root: UIViewController) -> [UINavigationController] {
        var found: [UINavigationController] = []
        if let nav = root as? UINavigationController {
            found.append(nav)
        }
        for child in root.children {
            found.append(contentsOf: findNavigationControllers(in: child))
        }
        return found
    }

    static func withDefaultTimeZone(_ identifier: String, body: () async throws -> Void) async rethrows {
        let previous = TimeZone.ReferenceType.default
        let tz = TimeZone(identifier: identifier)!
        TimeZone.ReferenceType.default = tz
        defer { TimeZone.ReferenceType.default = previous }
        try await body()
    }
}
