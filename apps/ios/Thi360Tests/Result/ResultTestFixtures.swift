import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

struct IndexedResultFixtureEntry: Decodable {
    let file: String
    let operationId: String
    let method: String
    let path: String
    let status: Int
    let variant: String?
}

enum ResultTestFixtures {
    private static let cachedIndex: [IndexedResultFixtureEntry] = {
        let url = ContractFixtureLoader.contractsMobileURL.appendingPathComponent("api/index.json")
        let data = try! Data(contentsOf: url)
        return try! JSONDecoder().decode([IndexedResultFixtureEntry].self, from: data)
    }()

    static func entry(operationId: String, status: Int, variant: String) throws -> IndexedResultFixtureEntry {
        guard let matched = cachedIndex.first(where: {
            $0.operationId == operationId && $0.status == status && ($0.variant ?? "") == variant
        }) else {
            throw NSError(
                domain: "ResultTestFixtures",
                code: 1,
                userInfo: [NSLocalizedDescriptionKey: "Missing index.json entry for \(operationId) \(status) \(variant)"]
            )
        }
        return matched
    }

    static func loadData(operationId: String, status: Int, variant: String) throws -> Data {
        let matched = try entry(operationId: operationId, status: status, variant: variant)
        return try ContractFixtureLoader.loadData(relativePath: matched.file)
    }

    static func loadRelativePath(operationId: String, status: Int, variant: String) throws -> String {
        try entry(operationId: operationId, status: status, variant: variant).file
    }

    static func decodeResult(variant: String, mutate: ((inout [String: Any]) -> Void)? = nil) throws -> AttemptResult {
        var data = try loadData(operationId: Operations.getResult.id, status: 200, variant: variant)
        if let mutate {
            var root = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
            mutate(&root)
            data = try JSONSerialization.data(withJSONObject: root)
        }
        let dto = try MobileAPIClient.makeJSONDecoder().decode(Components.Schemas.AttemptResultResponse.self, from: data)
        return AttemptResult(dto: dto, fallbackAttemptId: dto.attemptId ?? "att-fixture")
    }

    static func decodeReviewPayload(
        variant: String,
        mutate: ((inout [String: Any]) -> Void)? = nil
    ) throws -> ReviewWirePayload {
        var data = try loadData(operationId: Operations.getReview.id, status: 200, variant: variant)
        if let mutate {
            var root = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
            mutate(&root)
            data = try JSONSerialization.data(withJSONObject: root)
        }
        let dto = try MobileAPIClient.makeJSONDecoder().decode(Components.Schemas.AttemptReviewResponse.self, from: data)
        let answers = ItemReviewMapper.extractStudentAnswers(fromReviewData: data)
        return ReviewWirePayload(response: dto, studentAnswers: answers)
    }

    static func decodeCatalogOptions(variant: String) throws -> [String: CatalogOptionItem] {
        let data = try loadData(operationId: Operations.getOptions.id, status: 200, variant: variant)
        let dtos = try MobileAPIClient.makeJSONDecoder().decode([Components.Schemas.CatalogOptionResponse].self, from: data)
        var map: [String: CatalogOptionItem] = [:]
        for item in dtos {
            map[item.code] = CatalogOptionItem(code: item.code, nameVi: item.nameVi, nameEn: item.nameEn ?? "")
        }
        return map
    }

    static func registerCatalogOptions(on transport: ContractFakeTransport) throws {
        let examData = try loadData(operationId: Operations.getOptions.id, status: 200, variant: "exam")
        let subjectData = try loadData(operationId: Operations.getOptions.id, status: 200, variant: "subject")
        transport.register(method: .get, path: "/api/v1/catalog/options") { request in
            if request.path.contains("group=SUBJECT") {
                return ContractFakeTransport.jsonResponse(statusCode: 200, data: subjectData)
            }
            return ContractFakeTransport.jsonResponse(statusCode: 200, data: examData)
        }
    }
}

final class FakeResultAppClock: AppClock, @unchecked Sendable {
    private let lock = NSLock()
    private var current: Date

    init(_ initial: Date = Date(timeIntervalSince1970: 1_764_118_800)) {
        self.current = initial
    }

    var now: Date {
        lock.lock()
        defer { lock.unlock() }
        return current
    }

    func advance(by seconds: TimeInterval) {
        lock.lock()
        current = current.addingTimeInterval(seconds)
        lock.unlock()
    }
}

struct FakePollInstant: InstantProtocol, Sendable {
    var offset: Duration

    func advanced(by duration: Duration) -> FakePollInstant {
        FakePollInstant(offset: offset + duration)
    }

    func duration(to other: FakePollInstant) -> Duration {
        other.offset - offset
    }

    static func < (lhs: FakePollInstant, rhs: FakePollInstant) -> Bool {
        lhs.offset < rhs.offset
    }
}

final class FakePollClock: Clock, @unchecked Sendable {
    typealias Instant = FakePollInstant
    typealias Duration = Swift.Duration

    private struct Waiter {
        let id: UUID
        let deadline: FakePollInstant
        let continuation: CheckedContinuation<Void, Error>
    }

    private let lock = NSLock()
    private var currentOffset: Duration = .zero
    private var waiters: [Waiter] = []

    var now: FakePollInstant {
        lock.lock()
        defer { lock.unlock() }
        return FakePollInstant(offset: currentOffset)
    }

    var minimumResolution: Duration { .milliseconds(1) }

    func sleep(until deadline: FakePollInstant, tolerance: Duration?) async throws {
        try Task.checkCancellation()
        let waiterId = UUID()
        try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
                lock.lock()
                if currentOffset >= deadline.offset {
                    lock.unlock()
                    continuation.resume()
                    return
                }
                if Task.isCancelled {
                    lock.unlock()
                    continuation.resume(throwing: CancellationError())
                    return
                }
                waiters.append(Waiter(id: waiterId, deadline: deadline, continuation: continuation))
                lock.unlock()
            }
        } onCancel: {
            self.cancelWaiter(id: waiterId)
        }
    }

    func advance(by duration: Duration) async {
        let ready: [CheckedContinuation<Void, Error>] = lock.withLock {
            currentOffset += duration
            let due = waiters.filter { $0.deadline.offset <= currentOffset }
            waiters.removeAll { $0.deadline.offset <= currentOffset }
            return due.map(\.continuation)
        }
        for continuation in ready {
            continuation.resume()
        }
        await settle()
    }

    func waitForWaiter(timeoutMilliseconds: Int = 500) async {
        let iterations = max(timeoutMilliseconds / 2, 1)
        for _ in 0..<iterations {
            if pendingWaiterCount > 0 { return }
            try? await Task.sleep(for: .milliseconds(2))
        }
    }

    func settle(turns: Int = 16) async {
        for _ in 0..<turns {
            await Task.yield()
        }
        try? await Task.sleep(for: .milliseconds(8))
    }

    var pendingWaiterCount: Int {
        lock.withLock { waiters.count }
    }

    private func cancelWaiter(id: UUID) {
        let cancelled: CheckedContinuation<Void, Error>? = lock.withLock {
            guard let idx = waiters.firstIndex(where: { $0.id == id }) else { return nil }
            return waiters.remove(at: idx).continuation
        }
        cancelled?.resume(throwing: CancellationError())
    }
}

final class ManualNetworkPathObserver: NetworkPathObserving, @unchecked Sendable {
    private let lock = NSLock()
    private var handler: (@Sendable (Bool) -> Void)?

    func start(onUpdate: @escaping @Sendable (Bool) -> Void) {
        lock.lock()
        handler = onUpdate
        lock.unlock()
    }

    func stop() {
        lock.lock()
        handler = nil
        lock.unlock()
    }

    func emit(online: Bool) {
        let callback = lock.withLock { handler }
        callback?(online)
    }
}

@MainActor
enum ResultViewTestHelper {
    static func host<Content: View>(
        _ view: Content,
        size: CGSize = CGSize(width: 390, height: 844),
        style: UIUserInterfaceStyle = .light,
        sizeClass: UIUserInterfaceSizeClass = .compact,
        shellWidth: ShellWidth = .narrow,
        palette: BrandPalette = .defaultLight,
        statusPalette: StatusPalette = .light,
        dynamicTypeSize: DynamicTypeSize = .large,
        settleSeconds: TimeInterval = 0.08
    ) -> (UIWindow, UIViewController, ResultInspectionSink, QuestionInspectionSink) {
        let resultSink = ResultInspectionSink()
        let questionSink = QuestionInspectionSink()
        let wrapped = view
            .environment(\.brandPalette, palette)
            .environment(\.statusPalette, statusPalette)
            .environment(\.shellWidth, shellWidth)
            .environment(\.dynamicTypeSize, dynamicTypeSize)
            .environment(\.resultInspector, resultSink)
            .environment(\.questionInspector, questionSink)

        let (window, controller) = CatalogViewInspector.host(
            wrapped,
            size: size,
            style: style,
            sizeClass: sizeClass,
            settleSeconds: settleSeconds
        )
        return (window, controller, resultSink, questionSink)
    }

    static func makeOfflineAssetSource(attemptId: String = "att-test") -> AttemptAssetSource {
        let tokenStore = InMemoryTokenStore(
            initialTokens: SessionTokens(
                accessToken: "test-token",
                refreshToken: "test-refresh",
                accessExpiresIn: 3600,
                refreshExpiresIn: 86_400,
                mustChangePassword: false
            )
        )
        let refresher = TokenRefresher(
            tokenStore: tokenStore,
            refreshExecutor: { _ in throw MobileApiError.unexpectedResponse(statusCode: 401) }
        )
        let transport = ContractFakeTransport()
        return AttemptAssetSource(
            attemptId: attemptId,
            serverURL: URL(string: "https://api.thi360.test")!,
            tokenStore: tokenStore,
            refresher: refresher,
            transport: transport
        )
    }
}
