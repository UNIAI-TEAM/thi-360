import Foundation
import HTTPTypes
import OpenAPIRuntime
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

enum PrepareTestFixtures {
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
                domain: "PrepareTestFixtures",
                code: 1,
                userInfo: [
                    NSLocalizedDescriptionKey: "Missing indexed fixture for \(operationId) status=\(status) variant=\(variant ?? "*")"
                ]
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
        guard let obj = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw NSError(domain: "PrepareTestFixtures", code: 2)
        }
        return obj
    }

    static func encodeJSON(_ object: Any) throws -> Data {
        try JSONSerialization.data(withJSONObject: object, options: [.sortedKeys])
    }

    static func decodePrepare(from data: Data) throws -> AttemptPrepareResponse {
        try MobileAPIClient.makeJSONDecoder().decode(AttemptPrepareResponse.self, from: data)
    }

    static func loadPrepareResponse(variant: String) throws -> AttemptPrepareResponse {
        let data = try loadIndexedData(operationId: Operations.prepare.id, status: 200, variant: variant)
        return try decodePrepare(from: data)
    }

    static func mutatePrepare(
        variant: String,
        mutate: (inout [String: Any]) -> Void
    ) throws -> (Data, AttemptPrepareResponse) {
        var json = try loadJSONObject(operationId: Operations.prepare.id, status: 200, variant: variant)
        mutate(&json)
        let data = try encodeJSON(json)
        return (data, try decodePrepare(from: data))
    }

    // Plan A6 derivation: backend contract fixtures do not emit 500; derived by setting `code` to `INTERNAL_ERROR` on certified listStorefrontProducts 429 rate-limited fixture.
    static func makeInternalServerError500Data() throws -> Data {
        var root = try loadJSONObject(
            operationId: Operations.listStorefrontProducts.id,
            status: 429,
            variant: "rate-limited"
        )
        root["code"] = "INTERNAL_ERROR"
        return try encodeJSON(root)
    }

    // Plan A6 derivation: backend does not rate-limit POST /api/v1/me/attempts today (Spec A-44); derived from certified listStorefrontProducts 429 rate-limited fixture.
    static func makeRateLimited429Data() throws -> Data {
        try loadIndexedData(
            operationId: Operations.listStorefrontProducts.id,
            status: 429,
            variant: "rate-limited"
        )
    }

    // Plan A6 derivation: mutates only `preChoice` on certified `prepare` 200 `pre-choice` fixture to test 2 options, SUBJECT_CHOICE with chooseSubjects=N, or 0 options.
    static func makeSubjectChoicePrepareData(
        preselectedCode: String? = nil,
        chooseSubjects: Int = 2,
        includeSecondFixedOption: Bool = true
    ) throws -> (Data, AttemptPrepareResponse) {
        let perPartJSON = try loadJSONObject(operationId: Operations.prepare.id, status: 200, variant: "per-part")
        let afterChoices = (perPartJSON["afterStartChoices"] as? [[String: Any]]) ?? []
        let templateOptions = (afterChoices.first?["options"] as? [[String: Any]]) ?? []
        guard var scienceOpt = templateOptions.first else {
            throw NSError(domain: "PrepareTestFixtures", code: 3)
        }
        scienceOpt["chooseSubjects"] = chooseSubjects
        scienceOpt["questionCount"] = 60
        scienceOpt["durationMinutes"] = 75
        scienceOpt["maxPoints"] = 60.5

        var options: [[String: Any]] = [scienceOpt]
        if includeSecondFixedOption, templateOptions.count > 1 {
            var englishOpt = templateOptions[1]
            englishOpt["questionCount"] = 50
            englishOpt["durationMinutes"] = 60
            englishOpt["maxPoints"] = 50.0
            options.append(englishOpt)
        }

        return try mutatePrepare(variant: "pre-choice") { root in
            var preChoice = (root["preChoice"] as? [String: Any]) ?? [:]
            preChoice["kind"] = "SUBJECT_CHOICE"
            preChoice["preselectedCode"] = preselectedCode ?? NSNull()
            preChoice["options"] = options
            root["preChoice"] = preChoice
        }
    }

    // Plan A6 derivation: mutates only `grantSource` (copied from certified `class` fixture) and/or `servingStatus` on certified `exhausted-*` fixture because backend sets grantSource=null when attemptsLeft=0.
    static func makeExhaustedPrepareData(
        source: String,
        servingStatus: String = "PUBLISHED"
    ) throws -> (Data, AttemptPrepareResponse) {
        let baseVariant: String
        switch source {
        case "CLASS": baseVariant = "exhausted-class"
        case "COMPENSATION": baseVariant = "exhausted-compensation"
        default: baseVariant = "exhausted-purchase"
        }
        let classJSON = try loadJSONObject(operationId: Operations.prepare.id, status: 200, variant: "class")
        let perPartJSON = try loadJSONObject(operationId: Operations.prepare.id, status: 200, variant: "per-part")

        return try mutatePrepare(variant: baseVariant) { root in
            root["attemptsLeft"] = 0
            root["servingStatus"] = servingStatus
            if source == "CLASS" {
                root["grantSource"] = classJSON["grantSource"]
            } else if source == "PURCHASE" {
                root["grantSource"] = perPartJSON["grantSource"]
            } else if source == "COMPENSATION" {
                var gs = (classJSON["grantSource"] as? [String: Any]) ?? [:]
                gs["source"] = "COMPENSATION"
                gs["orderCode"] = NSNull()
                root["grantSource"] = gs
            }
        }
    }

    // Plan A6 derivation: mutates only `attemptId`, `state`, and `abortReason` on certified `getState` 200 `aborted` fixture.
    static func makeAttemptStateData(
        variant: String,
        attemptId: String? = nil,
        abortReason: String?? = nil
    ) throws -> Data {
        var json = try loadJSONObject(operationId: Operations.getState.id, status: 200, variant: variant)
        if let attemptId { json["attemptId"] = attemptId }
        if let abortReason { json["abortReason"] = abortReason ?? NSNull() }
        return try encodeJSON(json)
    }

    // Plan A6 derivation: mutates only `attemptId` and `abortReason` on certified `start` 200/202 fixture.
    static func makeStartData(
        status: Int,
        variant: String,
        attemptId: String? = nil,
        abortReason: String?? = nil
    ) throws -> Data {
        var json = try loadJSONObject(operationId: Operations.start.id, status: status, variant: variant)
        if let attemptId { json["attemptId"] = attemptId }
        if let abortReason { json["abortReason"] = abortReason ?? NSNull() }
        return try encodeJSON(json)
    }
}

final class VirtualPrepareClock: PrepareClock, @unchecked Sendable {
    private struct Sleeper {
        let id: UUID
        let deadline: TimeInterval
        let requestedDelay: TimeInterval
        let continuation: CheckedContinuation<Void, Error>
    }

    private let lock = NSLock()
    private var currentSeconds: TimeInterval = 0
    private var sleepers: [Sleeper] = []
    private var recordedSleeps: [TimeInterval] = []

    var nowSeconds: TimeInterval {
        lock.lock()
        defer { lock.unlock() }
        return currentSeconds
    }

    var sleepDurations: [TimeInterval] {
        lock.lock()
        defer { lock.unlock() }
        return recordedSleeps
    }

    var activeSleeperCount: Int {
        lock.lock()
        defer { lock.unlock() }
        return sleepers.count
    }

    func sleep(seconds: TimeInterval) async throws {
        let clamped = max(0, seconds)
        let entryId = UUID()
        try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { continuation in
                lock.lock()
                recordedSleeps.append(clamped)
                if clamped == 0 {
                    lock.unlock()
                    continuation.resume()
                    return
                }
                let deadline = currentSeconds + clamped
                sleepers.append(
                    Sleeper(
                        id: entryId,
                        deadline: deadline,
                        requestedDelay: clamped,
                        continuation: continuation
                    )
                )
                lock.unlock()
            }
        } onCancel: {
            self.lock.lock()
            if let idx = self.sleepers.firstIndex(where: { $0.id == entryId }) {
                let removed = self.sleepers.remove(at: idx)
                self.lock.unlock()
                removed.continuation.resume(throwing: CancellationError())
            } else {
                self.lock.unlock()
            }
        }
    }

    func advance(by delta: TimeInterval, step: TimeInterval? = nil) async {
        let target = nowSeconds + max(0, delta)
        if let step, step > 0 {
            while nowSeconds + 1e-9 < target {
                let next = min(target, nowSeconds + step)
                await advanceTo(next)
            }
        } else {
            await advanceTo(target)
        }
    }

    func advanceTo(_ targetSeconds: TimeInterval) async {
        await settleAsync()
        while true {
            let due: [Sleeper] = lock.withLock {
                let nextDeadline = sleepers.map(\.deadline).min()
                if let nextDeadline, nextDeadline <= targetSeconds + 1e-9 {
                    currentSeconds = max(currentSeconds, nextDeadline)
                    let matching = sleepers.filter { $0.deadline <= currentSeconds + 1e-9 }
                    sleepers.removeAll { $0.deadline <= currentSeconds + 1e-9 }
                    return matching
                }
                currentSeconds = max(currentSeconds, targetSeconds)
                return []
            }
            if due.isEmpty {
                await settleAsync()
                return
            }

            for sleeper in due {
                sleeper.continuation.resume()
            }
            await settleAsync()
        }
    }

    func settle(iterations: Int = 16) async {
        for _ in 0..<iterations {
            await Task.yield()
        }
    }

    func settleAsync() async {
        await settle(iterations: 16)
        try? await Task.sleep(nanoseconds: 3_000_000)
        await settle(iterations: 12)
    }

    @MainActor
    func waitUntil(
        maxYields: Int = 200,
        condition: @MainActor () -> Bool
    ) async -> Bool {
        for _ in 0..<maxYields {
            if condition() { return true }
            await Task.yield()
        }
        return condition()
    }
}

@MainActor
enum PrepareTestStackFactory {
    struct Stack {
        let transport: ContractFakeTransport
        let mobileClient: MobileAPIClient
        let api: PrepareAPI
        let storeProvider: AccountScopedStoreProvider
        let startContextStore: StartContextStore
        let tempDir: URL
        let preferences: AppPreferences
        let defaultsSuiteName: String
    }

    static func makeStack(
        accountId: String = "019320a0-0000-7000-8000-000000000001",
        locale: AppLocale = .vi,
        configure: (ContractFakeTransport) throws -> Void = { _ in }
    ) throws -> Stack {
        let suite = "vn.unicom.thi360.tests.prepare.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        let preferences = AppPreferences(userDefaults: defaults)
        preferences.locale = locale

        let tokens = SessionTokens(
            accessToken: "prepare-test-access",
            refreshToken: "prepare-test-refresh",
            accessExpiresIn: 900,
            refreshExpiresIn: 86_400,
            mustChangePassword: false,
            username: "student.demo",
            accountId: accountId
        )
        let tokenStore = InMemoryTokenStore(initialTokens: tokens)
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
        let tempDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("thi360-prepare-test-\(UUID().uuidString)", isDirectory: true)
        let storeProvider = AccountScopedStoreProvider(rootDirectoryURL: tempDir)
        let startContextStore = StartContextStore(provider: storeProvider, accountId: { accountId })
        let api = PrepareAPI(apiClient: mobileClient)
        return Stack(
            transport: transport,
            mobileClient: mobileClient,
            api: api,
            storeProvider: storeProvider,
            startContextStore: startContextStore,
            tempDir: tempDir,
            preferences: preferences,
            defaultsSuiteName: suite
        )
    }

    static func cleanup(_ stack: Stack) {
        UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName)
        try? FileManager.default.removeItem(at: stack.tempDir)
    }
}

@MainActor
enum PrepareViewInspector {
    static func host<Content: View>(
        _ view: Content,
        size: CGSize = CGSize(width: 390, height: 844),
        style: UIUserInterfaceStyle = .light,
        sizeClass: UIUserInterfaceSizeClass = .compact,
        shellWidth: ShellWidth = .narrow,
        uiCategory: UIContentSizeCategory = .large,
        dynamicType: DynamicTypeSize = .large,
        settleSeconds: TimeInterval = 0.08
    ) -> (UIWindow, UIHostingController<AnyView>) {
        let palette: BrandPalette = (style == .dark) ? .defaultDark : .defaultLight
        let statusPalette: StatusPalette = (style == .dark) ? .dark : .light
        let wrapped = AnyView(
            view
                .background(palette.bgPageColor.ignoresSafeArea())
                .environment(\.brandPalette, palette)
                .environment(\.statusPalette, statusPalette)
                .environment(\.shellWidth, shellWidth)
                .environment(\.dynamicTypeSize, dynamicType)
        )
        return CatalogViewInspector.host(
            wrapped,
            size: size,
            style: style,
            sizeClass: sizeClass,
            uiCategory: uiCategory,
            settleSeconds: settleSeconds
        )
    }

    static func joinedText(in root: UIView, window: UIWindow) -> String {
        CatalogViewInspector.allStrings(in: root, window: window).joined(separator: "\n")
    }

    static func node(
        identifier: String,
        in root: UIView,
        window: UIWindow
    ) -> CatalogViewInspector.Node? {
        CatalogViewInspector.collectNodes(in: root, window: window)
            .first { $0.identifier == identifier }
    }
}
