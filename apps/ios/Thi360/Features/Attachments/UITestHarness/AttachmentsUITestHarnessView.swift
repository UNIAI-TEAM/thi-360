#if THI360_DEV
import SwiftUI

enum AttachmentsUITestScenario: String {
    case attachmentOpen
    case audioLifecycle
    case storage

    static func fromLaunchArguments(_ arguments: [String] = ProcessInfo.processInfo.arguments) -> AttachmentsUITestScenario? {
        guard AppEnvironment.current.isDev,
              let index = arguments.firstIndex(of: "-uitest-attachments-harness"),
              index + 1 < arguments.count
        else {
            return nil
        }
        return AttachmentsUITestScenario(rawValue: arguments[index + 1])
    }

    static func progressIntervalMs(_ arguments: [String] = ProcessInfo.processInfo.arguments) -> Int {
        guard let index = arguments.firstIndex(of: "-uitest-audio-progress-ms"),
              index + 1 < arguments.count,
              let parsed = Int(arguments[index + 1]),
              parsed >= 200
        else {
            return 10_000
        }
        return parsed
    }
}

@MainActor
private final class AttachmentsHarnessStore {
    static let shared = AttachmentsHarnessStore()

    var engine: AttemptEngine?
    var audioController: AttemptAudioController?
    var opener: AttachmentOpener?
    var assetSource: AttemptAssetSource?
    var mountTempCount: Int = -1
    var lastBackgroundStopElapsedMs: Int = -1
    var configuredScenario: AttachmentsUITestScenario?

    static let attemptId = "00000000-0000-7000-8000-000000000001"
    static let partId = "00000000-0000-7000-8000-000000000006"
    static let pdfAssetId = "00000000-0000-7000-8000-000000000099"
    static let audioAssetId = "00000000-0000-7000-8000-000000000008"
    static let imageAssetId = "00000000-0000-7000-8000-000000000077"

    func ensureConfigured(
        scenario: AttachmentsUITestScenario,
        sessionStore: SessionStore?,
        connectivityMonitor: ConnectivityMonitor?
    ) {
        if configuredScenario == scenario, opener != nil, audioController != nil {
            return
        }

        mountTempCount = AttemptAssetSource.tempFileCountForHarness()

        let resolvedSession = sessionStore
        let apiClient: MobileAPIClient
        let tokenStore: any TokenStore
        let storeProvider: AccountScopedStoreProvider

        if let resolvedSession {
            apiClient = resolvedSession.apiClient
            tokenStore = resolvedSession.tokenStore
            storeProvider = resolvedSession.storeProvider
            if (try? tokenStore.read()) == nil {
                try? tokenStore.write(
                    SessionTokens(
                        accessToken: "uitest-access-token",
                        refreshToken: "uitest-refresh-token",
                        accessExpiresIn: 3_600,
                        refreshExpiresIn: 86_400,
                        mustChangePassword: false,
                        username: "student.uitest",
                        accountId: "account-uitest-1"
                    )
                )
            }
        } else {
            let memStore = InMemoryTokenStore(
                initialTokens: SessionTokens(
                    accessToken: "uitest-access-token",
                    refreshToken: "uitest-refresh-token",
                    accessExpiresIn: 3_600,
                    refreshExpiresIn: 86_400,
                    mustChangePassword: false,
                    username: "student.uitest",
                    accountId: "account-uitest-1"
                )
            )
            tokenStore = memStore
            storeProvider = AccountScopedStoreProvider()
            apiClient = MobileAPIClient(
                serverURL: AppEnvironment.current.apiBaseURL,
                tokenStore: memStore,
                localeProvider: { .vi }
            )
        }

        let accountId = resolvedSession?.currentAccountId ?? "account-uitest-1"
        guard let accountStore = storeProvider.store(for: accountId) else {
            return
        }
        let connectivity = connectivityMonitor ?? ConnectivityMonitor(initialIsOnline: true, autoStart: false)

        let source = AttemptAssetSource(
            attemptId: Self.attemptId,
            mobileAPIClient: apiClient,
            tokenStore: tokenStore
        )
        let attemptAPI = AttemptAPI(apiClient: apiClient)
        let attemptEngine = AttemptEngine(
            attemptId: Self.attemptId,
            accountId: accountId,
            api: attemptAPI,
            store: accountStore,
            connectivity: connectivity
        )
        let gateway = OpenAPIAttemptAudioGateway(
            attemptId: Self.attemptId,
            apiClient: apiClient,
            activeSessionIdProvider: { @MainActor [weak attemptEngine] in
                attemptEngine?.state.activeSessionId
            }
        )
        let intervalMs = AttachmentsUITestScenario.progressIntervalMs()
        let controller = AttemptAudioController(
            attemptId: Self.attemptId,
            assetId: Self.audioAssetId,
            partId: Self.partId,
            fileName: "silence-35s.wav",
            engine: attemptEngine,
            gateway: gateway,
            progressInterval: .milliseconds(intervalMs),
            positionPollInterval: .milliseconds(200)
        )

        self.assetSource = source
        self.engine = attemptEngine
        self.audioController = controller
        self.opener = AttachmentOpener(assetSource: source)
        self.configuredScenario = scenario
    }

    func reset() {
        audioController?.close()
        opener?.close()
        engine?.dispose()
        assetSource?.purgeAll()
        audioController = nil
        opener = nil
        engine = nil
        assetSource = nil
        configuredScenario = nil
        lastBackgroundStopElapsedMs = -1
    }
}

struct AttachmentsUITestHarnessView: View {
    let scenario: AttachmentsUITestScenario
    @Bindable var preferences: AppPreferences
    let sessionStore: SessionStore?
    let connectivityMonitor: ConnectivityMonitor?

    @State private var answer: AnswerValue?
    @State private var screenVisible = true
    @State private var imageLoaded = false
    @Environment(\.scenePhase) private var scenePhase

    private let store = AttachmentsHarnessStore.shared

    private var questionItem: QuestionItem {
        .singleChoice(
            id: "00000000-0000-7000-8000-000000000007",
            displayNo: 3,
            points: 1,
            stem: "<p>Nghe đoạn hội thoại hoặc xem bảng số liệu và chọn đáp án đúng.</p>",
            optionOrder: ["o1", "o2"],
            options: [
                ChoiceOption(id: "o1", content: "<p>Đáp án A</p>"),
                ChoiceOption(id: "o2", content: "<p>Đáp án B</p>")
            ]
        )
    }

    private var harnessAttachments: [QuestionAttachment] {
        switch scenario {
        case .attachmentOpen:
            return [
                QuestionAttachment(
                    assetId: AttachmentsHarnessStore.pdfAssetId,
                    fileName: "bang-so-lieu.pdf",
                    mime: "application/pdf",
                    url: nil
                )
            ]
        case .audioLifecycle:
            return [
                QuestionAttachment(
                    assetId: AttachmentsHarnessStore.audioAssetId,
                    fileName: "silence-35s.wav",
                    mime: "audio/wav",
                    url: nil
                )
            ]
        case .storage:
            return [
                QuestionAttachment(
                    assetId: AttachmentsHarnessStore.pdfAssetId,
                    fileName: "bang-so-lieu.pdf",
                    mime: "application/pdf",
                    url: nil
                ),
                QuestionAttachment(
                    assetId: AttachmentsHarnessStore.audioAssetId,
                    fileName: "silence-35s.wav",
                    mime: "audio/wav",
                    url: nil
                )
            ]
        }
    }

    var body: some View {
        let _ = store.ensureConfigured(
            scenario: scenario,
            sessionStore: sessionStore,
            connectivityMonitor: connectivityMonitor
        )
        Group {
            if let opener = store.opener, let controller = store.audioController {
                harnessContent(opener: opener, controller: controller)
            } else {
                ProgressView()
            }
        }
    }

    private func harnessContent(opener: AttachmentOpener, controller: AttemptAudioController) -> some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text(NSHomeDirectory())
                        .font(.caption2.monospaced())
                        .accessibilityIdentifier("harness.attachments.containerPath")

                    let _ = controller.uiState
                    let _ = opener.state
                    let viewerOpen = opener.activePreview != nil
                    Text("MOUNT_COUNT:\(store.mountTempCount)|TEMP_NOW:\(AttemptAssetSource.tempFileCountForHarness())|VIEWER:\(viewerOpen ? "true" : "false")|PLAYING:\(controller.isActivelyPlayingMedia ? "true" : "false")|DATA:\(controller.hasInMemoryAudioData ? "true" : "false")|STOP_MS:\(store.lastBackgroundStopElapsedMs)|IMG:\(imageLoaded ? "true" : "false")")
                        .font(.caption2.monospaced())
                        .accessibilityIdentifier("harness.attachments.telemetry")

                    if screenVisible {
                        QuestionView(
                            item: questionItem,
                            answer: answer,
                            mode: .answering,
                            onAnswer: { next in answer = next },
                            assets: store.assetSource,
                            locale: preferences.locale
                        ) {
                            AttachmentsView(
                                attachments: harnessAttachments,
                                opener: opener,
                                locale: preferences.locale
                            ) { _ in
                                AttemptAudioPlayerView(
                                    controller: controller,
                                    locale: preferences.locale
                                )
                            }
                        }
                    }

                    if scenario == .storage {
                        HStack(spacing: 12) {
                            Button("Load Image") {
                                Task {
                                    if let _ = try? await store.assetSource?.load(.attempt(assetId: AttachmentsHarnessStore.imageAssetId)) {
                                        imageLoaded = true
                                    }
                                }
                            }
                            .buttonStyle(.bordered)
                            .accessibilityIdentifier("harness.attachments.loadImage")

                            Button("Leave Attempt") {
                                screenVisible = false
                                store.assetSource?.purgeAll()
                            }
                            .buttonStyle(.bordered)
                            .accessibilityIdentifier("harness.attachments.leaveAttempt")

                            Button("Logout") {
                                Task {
                                    await sessionStore?.signOut(reason: .none)
                                    store.reset()
                                }
                            }
                            .buttonStyle(.borderedProminent)
                            .accessibilityIdentifier("harness.attachments.logout")
                        }
                    }
                }
                .padding(16)
            }
            .navigationTitle("Attachments Harness")
            .navigationBarTitleDisplayMode(.inline)
        }
        .onChange(of: scenePhase) { _, newPhase in
            if newPhase != .active {
                let t0 = ProcessInfo.processInfo.systemUptime
                controller.onAppBackgrounded()
                let elapsedMs = Int(((ProcessInfo.processInfo.systemUptime - t0) * 1_000.0).rounded())
                store.lastBackgroundStopElapsedMs = elapsedMs
            }
        }
    }
}
#endif
