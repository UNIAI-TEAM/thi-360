import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class MockPlayerAttachmentHost: PlayerAttachmentHost {
    var renderedContexts: [PlayerAttachmentContext] = []
    var beforeCloseTargets: [CloseTarget] = []
    var onBeforeCloseHook: ((CloseTarget) -> Void)?

    func attachmentsView(for context: PlayerAttachmentContext) -> AnyView {
        renderedContexts.append(context)
        return AnyView(
            Text("Attachment-\(context.item.itemId ?? "")")
                .font(.caption)
                .accessibilityIdentifier("player.mockAttachment.\(context.item.itemId ?? "")")
        )
    }

    func onBeforeClose(_ target: CloseTarget) {
        beforeCloseTargets.append(target)
        onBeforeCloseHook?(target)
    }
}

final class MockPartChoiceMetadataAPI: PartChoiceMetadataAPIProtocol, @unchecked Sendable {
    private let lock = NSLock()
    var myExamsResult: Result<[MyExamCard], Error> = .success([])
    var prepareResult: Result<AttemptPrepareResponse, Error> = .failure(MobileApiError.unexpectedResponse(statusCode: 500))
    private(set) var myExamsCallCount = 0
    private(set) var prepareCallIds: [String] = []

    func fetchMyExams() async throws -> [MyExamCard] {
        let result: Result<[MyExamCard], Error> = lock.withLock {
            myExamsCallCount += 1
            return myExamsResult
        }
        return try result.get()
    }

    func prepare(entitlementId: String) async throws -> AttemptPrepareResponse {
        let result: Result<AttemptPrepareResponse, Error> = lock.withLock {
            prepareCallIds.append(entitlementId)
            return prepareResult
        }
        return try result.get()
    }
}

@MainActor
final class PlayerTestHarness {
    let tempDir: URL
    let storeProvider: AccountScopedStoreProvider
    let accountStore: AccountScopedStore
    let clock: TestEngineClock
    let connectivity: ConnectivityMonitor
    let server: FakeAttemptServer
    let bodyStore: PlayerQuestionBodyStore
    let api: AttemptAPI
    let engine: AttemptEngine
    let attemptId: String
    let accountId: String

    init(
        attemptId: String? = nil,
        accountId: String = "acc-player-test",
        initialStateVariant: String = "in-progress-shared",
        useRichTextAttempt50: Bool = false,
        initialServerNow: Date = FlexibleISO8601DateTranscoder.parse("2026-11-20T10:00:00Z")!,
        initiallyOnline: Bool = true,
        autoStart: Bool = true,
        mutateInitialState: ((inout [String: Any]) -> Void)? = nil
    ) throws {
        let dir = FileManager.default.temporaryDirectory
            .appendingPathComponent("thi360-player-tests-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        self.tempDir = dir
        self.storeProvider = AccountScopedStoreProvider(rootDirectoryURL: dir)
        self.accountId = accountId
        self.accountStore = storeProvider.store(for: accountId)!
        self.clock = TestEngineClock()
        self.connectivity = ConnectivityMonitor(
            initialIsOnline: initiallyOnline,
            observer: AttemptFakeNetworkPathObserver(),
            autoStart: false
        )
        self.server = try FakeAttemptServer(
            attemptId: attemptId,
            initialStateVariant: initialStateVariant,
            useRichTextAttempt50: useRichTextAttempt50,
            initialServerNow: initialServerNow,
            clock: clock,
            mutateInitialState: mutateInitialState
        )
        self.attemptId = server.attemptId
        self.bodyStore = PlayerQuestionBodyStore()

        let tokenStore = InMemoryTokenStore(
            initialTokens: SessionTokens(
                accessToken: "player-test-access",
                refreshToken: "player-test-refresh",
                accessExpiresIn: 300,
                refreshExpiresIn: 604_800,
                mustChangePassword: false,
                username: "student.player",
                accountId: accountId
            )
        )
        let apiClient = MobileAPIClient(
            serverURL: URL(string: "https://api.thi360.test")!,
            tokenStore: tokenStore,
            transport: server.transport,
            localeProvider: { .vi }
        )
        self.api = PlayerAttemptAPIFactory.make(apiClient: apiClient, bodyStore: bodyStore)
        self.engine = AttemptEngine(
            attemptId: server.attemptId,
            accountId: accountId,
            api: api,
            store: accountStore,
            clock: clock,
            connectivity: connectivity,
            autoStart: autoStart
        )
    }

    func settle(advance: Duration = .milliseconds(50)) async {
        for _ in 0..<4 {
            await Task.yield()
        }
        if advance > .zero {
            await clock.advance(by: advance)
        }
        _ = await clock.waitUntil {
            if case .loading = self.engine.state.load {
                return false
            }
            if self.engine.state.attempt?.state == .IN_PROGRESS && self.connectivity.isOnline {
                if case .acquiring = self.engine.state.session {
                    return false
                }
            }
            return true
        }
        await engine.waitForIdleForTesting()
        for _ in 0..<4 {
            await Task.yield()
        }
        await engine.waitForIdleForTesting()
    }

    func dispose() {
        engine.dispose()
        try? FileManager.default.removeItem(at: tempDir)
    }
}

enum PlayerTestFixtures {
    static func decodeAttemptState(
        variant: String,
        mutate: ((inout [String: Any]) -> Void)? = nil
    ) throws -> (response: Components.Schemas.AttemptStateResponse, bodies: [ItemID: AttemptQuestionBodyValue]) {
        var obj = try AttemptFixtureCatalog.loadJSONObject(
            operationId: Operations.getState.id,
            status: 200,
            variant: variant
        )
        mutate?(&obj)
        let data = try JSONSerialization.data(withJSONObject: obj)
        let decoded = try MobileAPIClient.makeJSONDecoder().decode(Components.Schemas.AttemptStateResponse.self, from: data)
        let bodies = QuestionItemMapper.extractBodies(fromAttemptStateData: data)
        return (decoded, bodies)
    }

    static func decodeAttempt50() throws -> (response: Components.Schemas.AttemptStateResponse, bodies: [ItemID: AttemptQuestionBodyValue]) {
        let data = try ContractFixtureLoader.loadData(relativePath: "rich-text/attempt-50.json")
        let decoded = try MobileAPIClient.makeJSONDecoder().decode(Components.Schemas.AttemptStateResponse.self, from: data)
        let bodies = QuestionItemMapper.extractBodies(fromAttemptStateData: data)
        return (decoded, bodies)
    }

    static func decodePrepare(variant: String, mutate: ((inout [String: Any]) -> Void)? = nil) throws -> AttemptPrepareResponse {
        var obj = try AttemptFixtureCatalog.loadJSONObject(
            operationId: Operations.prepare.id,
            status: 200,
            variant: variant
        )
        mutate?(&obj)
        let data = try JSONSerialization.data(withJSONObject: obj)
        return try MobileAPIClient.makeJSONDecoder().decode(AttemptPrepareResponse.self, from: data)
    }

    static func decodeMyExamsCards(mutate: ((inout [String: Any]) -> Void)? = nil) throws -> [MyExamCard] {
        var obj = try AttemptFixtureCatalog.loadJSONObject(
            operationId: Operations.listMyExams.id,
            status: 200,
            variant: ""
        )
        mutate?(&obj)
        let data = try JSONSerialization.data(withJSONObject: obj)
        let resp = try MobileAPIClient.makeJSONDecoder().decode(Components.Schemas.MyExamsResponse.self, from: data)
        return MyExamsAPI.mapCards(resp.items, supplementalJSONData: data)
    }

    static func makeMetadata(from prepare: AttemptPrepareResponse, entitlementId: String = "ent-1") -> PartChoiceMetadata {
        var subjects: [String: Components.Schemas.ChoiceSubjectView] = [:]
        for opt in prepare.preChoice?.options ?? [] {
            for s in opt.subjects ?? [] {
                if let c = s.code, !c.isEmpty {
                    subjects[c] = s
                    subjects[c.uppercased()] = s
                }
            }
        }
        for choice in prepare.afterStartChoices {
            for opt in choice.options ?? [] {
                for s in opt.subjects ?? [] {
                    if let c = s.code, !c.isEmpty {
                        subjects[c] = s
                        subjects[c.uppercased()] = s
                    }
                }
            }
        }
        return PartChoiceMetadata(
            entitlementId: entitlementId,
            choices: prepare.afterStartChoices,
            subjectsByCode: subjects
        )
    }
}

@MainActor
enum PlayerViewTestHost {
    private static let sinks = NSMapTable<UIView, QuestionInspectionSink>.weakToStrongObjects()

    static func host<Content: View>(
        _ view: Content,
        size: CGSize = CGSize(width: 390, height: 844),
        style: UIUserInterfaceStyle = .light,
        sizeClass: UIUserInterfaceSizeClass = .compact,
        uiCategory: UIContentSizeCategory = .large,
        dynamicType: DynamicTypeSize = .large,
        locale: AppLocale = .vi,
        attachmentHost: (any PlayerAttachmentHost)? = nil,
        settleSeconds: TimeInterval = 0.08
    ) -> (UIWindow, UIHostingController<AnyView>) {
        let themeMode: ThemeMode = (style == .dark) ? .dark : .light
        let palette = BrandPaletteSet.defaultThi360Green.palette(for: themeMode)
        let statusPalette = StatusPalette.forMode(themeMode)
        let swiftUISizeClass: UserInterfaceSizeClass = (sizeClass == .regular) ? .regular : .compact
        let sink = QuestionInspectionSink()

        let wrapped = AnyView(
            view
                .environment(\.brandPalette, palette)
                .environment(\.statusPalette, statusPalette)
                .environment(\.horizontalSizeClass, swiftUISizeClass)
                .environment(\.locale, locale.locale)
                .environment(\.dynamicTypeSize, dynamicType)
                .environment(\.playerAttachmentHost, attachmentHost)
                .environment(\.questionInspector, sink)
                .preferredColorScheme(themeMode.colorScheme)
        )

        let controller = UIHostingController(rootView: wrapped)
        controller.overrideUserInterfaceStyle = style
        controller.traitOverrides.horizontalSizeClass = sizeClass
        controller.traitOverrides.userInterfaceStyle = style
        controller.traitOverrides.preferredContentSizeCategory = uiCategory
        sinks.setObject(sink, forKey: controller.view)

        let window: UIWindow
        if let scene = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).first {
            window = UIWindow(windowScene: scene)
            window.frame = CGRect(origin: .zero, size: size)
        } else {
            window = UIWindow(frame: CGRect(origin: .zero, size: size))
        }
        window.overrideUserInterfaceStyle = style
        window.rootViewController = controller
        window.makeKeyAndVisible()
        controller.view.frame = window.bounds
        window.setNeedsLayout()
        window.layoutIfNeeded()
        RunLoop.main.run(until: Date().addingTimeInterval(settleSeconds))
        window.layoutIfNeeded()
        return (window, controller)
    }

    static func findNode(id: String, in view: UIView, window: UIWindow) -> CatalogViewInspector.Node? {
        if let node = sinks.object(forKey: view)?.nodes[id] {
            var traits: UIAccessibilityTraits = []
            if node.isButton { traits.insert(.button) }
            if !node.isEnabled { traits.insert(.notEnabled) }
            if node.isSelected { traits.insert(.selected) }
            return CatalogViewInspector.Node(
                identifier: node.identifier,
                label: node.label,
                value: node.value ?? "",
                traits: traits,
                frameInWindow: node.frame,
                isEnabled: node.isEnabled
            )
        }
        return CatalogViewInspector.collectNodes(in: view, window: window).first { $0.identifier == id }
    }

    static func allText(in view: UIView, window: UIWindow) -> [String] {
        var result: [String] = []
        if let recorded = sinks.object(forKey: view)?.allRecordedStrings {
            result.append(contentsOf: recorded)
        }
        result.append(
            contentsOf: CatalogViewInspector.collectNodes(in: view, window: window)
                .map(\.label)
                .filter { !$0.isEmpty }
        )
        return result
    }
}
