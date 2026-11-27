import Foundation
import HTTPTypes
import OpenAPIRuntime
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

enum CatalogTestFixtures {
    static func loadIndexedData(
        operationId: String,
        status: Int,
        fileContains: String? = nil
    ) throws -> Data {
        let entries = try ContractFakeTransport.loadIndex()
        let matches = entries.filter { entry in
            guard entry.operationId == operationId, entry.status == status else { return false }
            if let fileContains {
                return entry.file.contains(fileContains)
            }
            return true
        }
        guard let matched = matches.first else {
            throw NSError(
                domain: "CatalogTestFixtures",
                code: 1,
                userInfo: [NSLocalizedDescriptionKey: "Missing indexed fixture for \(operationId) status=\(status) filter=\(fileContains ?? "*")"]
            )
        }
        let relative = matched.file.contains("/") ? matched.file : "api/\(matched.file)"
        return try ContractFixtureLoader.loadData(relativePath: relative)
    }

    static func loadJSONObject(
        operationId: String,
        status: Int,
        fileContains: String? = nil
    ) throws -> [String: Any] {
        let data = try loadIndexedData(operationId: operationId, status: status, fileContains: fileContains)
        guard let object = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw NSError(domain: "CatalogTestFixtures", code: 2)
        }
        return object
    }

    static func encodeJSON(_ object: Any) throws -> Data {
        try JSONSerialization.data(withJSONObject: object, options: [.sortedKeys])
    }

    static func makeProductCardJSONList(
        totalCount: Int,
        codePrefix: String = "EXAM-GEN",
        mutate: ((Int, inout [String: Any]) -> Void)? = nil
    ) throws -> [[String: Any]] {
        let listRoot = try loadJSONObject(
            operationId: Operations.listStorefrontProducts.id,
            status: 200,
            fileContains: "storefront-products-list-200.json"
        )
        guard let baseItems = listRoot["items"] as? [[String: Any]], let template = baseItems.first else {
            throw NSError(domain: "CatalogTestFixtures", code: 3)
        }
        var items: [[String: Any]] = []
        items.reserveCapacity(totalCount)
        for index in 0..<totalCount {
            var copy = template
            let code = String(format: "%@-%03d", codePrefix, index + 1)
            copy["code"] = code
            copy["name"] = "Đề thi mẫu \(index + 1) (\(code))"
            mutate?(index, &copy)
            items.append(copy)
        }
        return items
    }

    static func makePageData(
        allItems: [[String: Any]],
        page: Int,
        size: Int = 12,
        totalItemsOverride: Int? = nil,
        totalPagesOverride: Int? = nil
    ) throws -> Data {
        let totalItems = totalItemsOverride ?? allItems.count
        let totalPages = totalPagesOverride ?? max(0, (totalItems + size - 1) / size)
        let start = page * size
        let slice: [[String: Any]]
        if start < allItems.count {
            let end = min(start + size, allItems.count)
            slice = Array(allItems[start..<end])
        } else {
            slice = []
        }
        let payload: [String: Any] = [
            "items": slice,
            "page": page,
            "size": size,
            "totalItems": totalItems,
            "totalPages": totalPages
        ]
        return try encodeJSON(payload)
    }

    static func makeExamAccessData(items: [[String: Any]]) throws -> Data {
        let base = try loadJSONObject(
            operationId: Operations.queryMyExamAccess.id,
            status: 200
        )
        var copy = base
        copy["items"] = items
        return try encodeJSON(copy)
    }

    static func makeExamAccessItem(
        templateState: String,
        code: String,
        attemptsLeft: Int? = nil,
        entitlementId: String?? = nil,
        activeAttemptId: String?? = nil,
        hasPendingFulfillment: Bool? = nil,
        pendingOrderCode: String?? = nil
    ) throws -> [String: Any] {
        let base = try loadJSONObject(
            operationId: Operations.queryMyExamAccess.id,
            status: 200
        )
        guard let baseItems = base["items"] as? [[String: Any]],
              var row = baseItems.first(where: { ($0["state"] as? String) == templateState }) ?? baseItems.first
        else {
            throw NSError(domain: "CatalogTestFixtures", code: 4)
        }
        row["code"] = code
        if templateState == "NONE" {
            row["state"] = "NONE"
            row["attemptsLeft"] = 0
            row["entitlementId"] = NSNull()
            row["activeAttemptId"] = NSNull()
            row["hasPendingFulfillment"] = false
            row["pendingOrderCode"] = NSNull()
        } else {
            row["state"] = templateState
        }
        if let attemptsLeft { row["attemptsLeft"] = attemptsLeft }
        if let entitlementId { row["entitlementId"] = entitlementId ?? NSNull() }
        if let activeAttemptId { row["activeAttemptId"] = activeAttemptId ?? NSNull() }
        if let hasPendingFulfillment { row["hasPendingFulfillment"] = hasPendingFulfillment }
        if let pendingOrderCode { row["pendingOrderCode"] = pendingOrderCode ?? NSNull() }
        return row
    }

    // Plan A6 derivation: backend contract fixtures do not emit 500; derived by setting `code` to `INTERNAL_ERROR` on certified `storefront-products-list-429-rate-limited.json`.
    static func makeInternalServerError500Data() throws -> Data {
        var root = try loadJSONObject(
            operationId: Operations.listStorefrontProducts.id,
            status: 429,
            fileContains: "rate-limited"
        )
        root["code"] = "INTERNAL_ERROR"
        return try encodeJSON(root)
    }

    static func loadForbidden403Data() throws -> Data {
        try loadIndexedData(
            operationId: Operations.listMyExams.id,
            status: 403,
            fileContains: "forbidden"
        )
    }

    // Plan A6 derivation: backend does not emit 200 with saleState="UNAVAILABLE" today; derived by setting only `saleState` on certified `storefront-products-detail-200-bundle.json`.
    static func makeUnavailableBundle200Data() throws -> Data {
        var root = try loadJSONObject(
            operationId: Operations.getStorefrontProduct.id,
            status: 200,
            fileContains: "bundle"
        )
        root["saleState"] = "UNAVAILABLE"
        return try encodeJSON(root)
    }

    // Plan A6 derivation: derived from certified `orders-create-409-product-unavailable.json` (and optional `storefront-products-detail-200-bundle.json` attached as `product`).
    static func makeUnavailableBundle409Data(includeProduct: Bool) throws -> Data {
        var errorRoot = try loadJSONObject(
            operationId: Operations.createMyOrder.id,
            status: 409,
            fileContains: "product-unavailable"
        )
        if includeProduct {
            let bundle = try loadJSONObject(
                operationId: Operations.getStorefrontProduct.id,
                status: 200,
                fileContains: "bundle"
            )
            errorRoot["product"] = bundle
        }
        return try encodeJSON(errorRoot)
    }

    // Plan A6 derivation: derived from certified `storefront-products-list-429-rate-limited.json`.
    static func makeRateLimited429Data() throws -> Data {
        try loadIndexedData(
            operationId: Operations.listStorefrontProducts.id,
            status: 429,
            fileContains: "rate-limited"
        )
    }

    static func makeGone410Data(code: String, name: String?) throws -> Data {
        var root = try loadJSONObject(
            operationId: Operations.getStorefrontProduct.id,
            status: 410
        )
        var product = (root["product"] as? [String: Any]) ?? [:]
        product["code"] = code
        if let name {
            product["name"] = name
        } else {
            product.removeValue(forKey: "name")
        }
        root["product"] = product
        return try encodeJSON(root)
    }

    static func makeTinyPNGData(color: UIColor = .systemGreen) -> Data {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 8, height: 8))
        let image = renderer.image { ctx in
            color.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: 8, height: 8))
        }
        return image.pngData() ?? Data()
    }
}

final class ControllableDebounceSleeper: CatalogDebounceSleeper, @unchecked Sendable {
    private let lock = NSLock()
    private var pending: [(id: UUID, ms: UInt64, continuation: CheckedContinuation<Void, Error>)] = []
    private var recordedDelays: [UInt64] = []

    var sleepCallDelays: [UInt64] {
        lock.lock()
        defer { lock.unlock() }
        return recordedDelays
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
                recordedDelays.append(milliseconds)
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

@MainActor
enum CatalogTestStackFactory {
    struct Stack {
        let sessionStore: SessionStore
        let tokenStore: InMemoryTokenStore
        let transport: ContractFakeTransport
        let api: CatalogAPI
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
        configure: (ContractFakeTransport) throws -> Void = { _ in }
    ) throws -> Stack {
        let suite = "vn.unicom.thi360.tests.catalog.\(UUID().uuidString)"
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
                .appendingPathComponent("thi360-catalog-test-\(UUID().uuidString)", isDirectory: true)
        )
        let sessionStore = SessionStore(
            tokenStore: tokenStore,
            apiClient: mobileClient,
            storeProvider: storeProvider,
            logger: logger,
            initialStateOverride: initialTokens == nil ? nil : .active,
            skipsLaunchValidation: true
        )
        let api = CatalogAPI.makeWithWireMiddleware(
            serverURL: serverURL,
            tokenStore: tokenStore,
            transport: transport,
            refresher: mobileClient.refresher,
            logger: logger,
            localeProvider: localeProvider
        )
        return Stack(
            sessionStore: sessionStore,
            tokenStore: tokenStore,
            transport: transport,
            api: api,
            preferences: preferences,
            defaultsSuiteName: suite
        )
    }

    static func registerDefaultStorefrontFixtures(on transport: ContractFakeTransport) throws {
        let filters = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.getStorefrontFilters.id,
            status: 200
        )
        let products = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.listStorefrontProducts.id,
            status: 200,
            fileContains: "storefront-products-list-200.json"
        )
        let access = try CatalogTestFixtures.loadIndexedData(
            operationId: Operations.queryMyExamAccess.id,
            status: 200
        )
        transport.registerJSON(method: .get, path: "/api/v1/public/storefront/filters", data: filters)
        transport.registerJSON(method: .get, path: "/api/v1/public/storefront/products", data: products)
        transport.registerJSON(method: .post, path: "/api/v1/me/exam-access/query", data: access)
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

    nonisolated static func accessRequestCodes(from request: RecordedHTTPRequest) -> [String] {
        guard let data = request.bodyData,
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let codes = json["codes"] as? [String]
        else {
            return []
        }
        return codes
    }
}

@MainActor
enum CatalogViewInspector {
    struct Node {
        let identifier: String
        let label: String
        let value: String
        let traits: UIAccessibilityTraits
        let frameInWindow: CGRect
        let isEnabled: Bool
    }

    static func host<Content: View>(
        _ view: Content,
        size: CGSize = CGSize(width: 390, height: 844),
        style: UIUserInterfaceStyle = .light,
        sizeClass: UIUserInterfaceSizeClass = .compact,
        uiCategory: UIContentSizeCategory = .large,
        settleSeconds: TimeInterval = 0.08
    ) -> (UIWindow, UIHostingController<Content>) {
        let controller = UIHostingController(rootView: view)
        controller.traitOverrides.horizontalSizeClass = sizeClass
        controller.traitOverrides.userInterfaceStyle = style
        controller.traitOverrides.preferredContentSizeCategory = uiCategory

        let window: UIWindow
        if let scene = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).first {
            window = UIWindow(windowScene: scene)
            window.frame = CGRect(origin: .zero, size: size)
        } else {
            window = UIWindow(frame: CGRect(origin: .zero, size: size))
        }
        window.rootViewController = controller
        window.overrideUserInterfaceStyle = style
        window.makeKeyAndVisible()
        window.setNeedsLayout()
        window.layoutIfNeeded()
        RunLoop.main.run(until: Date().addingTimeInterval(settleSeconds))
        window.layoutIfNeeded()
        return (window, controller)
    }

    static func settle(window: UIWindow, seconds: TimeInterval = 0.08) {
        window.setNeedsLayout()
        window.layoutIfNeeded()
        RunLoop.main.run(until: Date().addingTimeInterval(seconds))
        window.layoutIfNeeded()
    }

    static func collectNodes(in root: UIView, window: UIWindow) -> [Node] {
        var results: [Node] = []
        var visited = Set<ObjectIdentifier>()

        func visitChildren(of nsObject: NSObject) {
            if let elements = nsObject.accessibilityElements, !elements.isEmpty {
                for element in elements {
                    visit(element)
                }
            }
            let count = nsObject.accessibilityElementCount()
            if count != NSNotFound && count > 0 {
                for index in 0..<count {
                    if let child = nsObject.accessibilityElement(at: index) {
                        visit(child)
                    }
                }
            }
        }

        func visit(_ object: Any) {
            guard let nsObject = object as? NSObject else { return }
            let oid = ObjectIdentifier(nsObject)
            guard visited.insert(oid).inserted else { return }

            if let view = nsObject as? UIView {
                if view.isHidden || view.alpha < 0.01 { return }
                if view.isAccessibilityElement {
                    let frame = view.convert(view.bounds, to: window)
                    let textFallback = (view as? UILabel)?.text ?? (view as? UITextField)?.text ?? ""
                    results.append(
                        Node(
                            identifier: view.accessibilityIdentifier ?? "",
                            label: view.accessibilityLabel ?? textFallback,
                            value: view.accessibilityValue ?? "",
                            traits: view.accessibilityTraits,
                            frameInWindow: frame,
                            isEnabled: !view.accessibilityTraits.contains(.notEnabled)
                        )
                    )
                    return
                }
                visitChildren(of: view)
                for subview in view.subviews { visit(subview) }
                return
            }

            if nsObject.isAccessibilityElement {
                let rawFrame = nsObject.accessibilityFrame
                let frame = (!rawFrame.isNull && !rawFrame.isInfinite && !rawFrame.isEmpty)
                    ? window.convert(rawFrame, from: nil)
                    : .zero
                let identifier = (nsObject as? UIAccessibilityIdentification)?.accessibilityIdentifier ?? ""
                results.append(
                    Node(
                        identifier: identifier,
                        label: nsObject.accessibilityLabel ?? "",
                        value: nsObject.accessibilityValue ?? "",
                        traits: nsObject.accessibilityTraits,
                        frameInWindow: frame,
                        isEnabled: !nsObject.accessibilityTraits.contains(.notEnabled)
                    )
                )
            }
            visitChildren(of: nsObject)
        }

        visit(root)
        return results
    }

    static func allStrings(in root: UIView, window: UIWindow) -> [String] {
        var strings: [String] = []
        for node in collectNodes(in: root, window: window) {
            if !node.label.isEmpty { strings.append(node.label) }
            if !node.value.isEmpty { strings.append(node.value) }
        }
        for label in RecoveryViewTestHelper.findViews(ofType: UILabel.self, in: root) {
            if let text = label.text, !text.isEmpty {
                strings.append(text)
            }
        }
        return strings
    }

    static func renderPNG(window: UIWindow, size: CGSize) throws -> (UIImage, Data) {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 2.0
        format.opaque = true
        let renderer = UIGraphicsImageRenderer(size: size, format: format)
        let image = renderer.image { context in
            let drew = window.drawHierarchy(in: CGRect(origin: .zero, size: size), afterScreenUpdates: true)
            if !drew {
                window.layer.render(in: context.cgContext)
            }
        }
        let png = try XCTUnwrap(image.pngData())
        return (image, png)
    }
}
