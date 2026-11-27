#if THI360_DEV
import SwiftUI

enum RichContentUITestScenario: String {
    case imageZoom
    case imageZoomLocked
    case formulaOverflow
    case tempFiles

    static func fromLaunchArguments(_ arguments: [String] = ProcessInfo.processInfo.arguments) -> RichContentUITestScenario? {
        guard AppEnvironment.current.isDev,
              let index = arguments.firstIndex(of: "-uitest-rich-harness"),
              index + 1 < arguments.count
        else {
            return nil
        }
        return RichContentUITestScenario(rawValue: arguments[index + 1])
    }
}

struct RichContentUITestHarnessView: View {
    let scenario: RichContentUITestScenario
    @Bindable var preferences: AppPreferences

    var body: some View {
        switch scenario {
        case .imageZoom:
            ImageZoomHarnessView(mode: .answering, locale: preferences.locale)
        case .imageZoomLocked:
            ImageZoomHarnessView(mode: .locked, locale: preferences.locale)
        case .formulaOverflow:
            FormulaOverflowHarnessView(locale: preferences.locale)
        case .tempFiles:
            TempFilesHarnessView()
        }
    }
}

private struct ImageZoomHarnessView: View {
    let mode: RichContentMode
    let locale: AppLocale

    @State private var slotValue: String = "giá trị gốc 99"
    @State private var assetSource: AttemptAssetSource
    @State private var isReady = false

    private static let zoomAssetId = "9b8f8b1f-9b8f-4b8f-9b8f-9b8f8b8f8b8f"
    private static let noZoomAssetId = "8a7e7a0e-8a7e-4a7e-8a7e-8a7e7a7e7a7e"
    private static let inlineAssetId = "7c6d6c0d-7c6d-4c6d-7c6d-7c6d6c6d6c6d"

    init(mode: RichContentMode, locale: AppLocale) {
        self.mode = mode
        self.locale = locale
        _assetSource = State(
            initialValue: AttemptAssetSource.makeDevHarnessSource(
                attemptId: "66666666-6666-4666-8666-666666666666"
            )
        )
    }

    private var document: RichDocument {
        let html = """
        <p>Đoạn dẫn đầu số 1 cho bài kiểm thử phóng to ảnh.</p>
        <p>Điền khe trong dòng: [[slot1]] để kiểm giữ giá trị.</p>
        <p>Đoạn đệm 2.</p>
        <p>Đoạn đệm 3.</p>
        <p>Đoạn đệm 4.</p>
        <figure class="image" data-width="medium" data-zoom="true">
          <img src="" alt="Ảnh phóng to được" data-asset-id="\(Self.zoomAssetId)">
          <figcaption>Hình 1. Biểu đồ phóng to</figcaption>
        </figure>
        <figure class="image" data-width="small" data-zoom="false">
          <img src="" alt="Ảnh không phóng to" data-asset-id="\(Self.noZoomAssetId)">
          <figcaption>Hình 2. Không phóng to</figcaption>
        </figure>
        <p><img src="" alt="Ảnh ngoài figure" data-asset-id="\(Self.inlineAssetId)"></p>
        <p>Đoạn cuối cùng sau các ảnh.</p>
        """
        return RichHTMLParser.parse(html, options: RichParseOptions(slotMarkers: true))
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 8) {
                Text(slotValue)
                    .font(.caption.monospaced())
                    .accessibilityIdentifier("harness.zoom.slotEcho")

                if isReady {
                    ScrollView {
                        RichContentView(
                            document: document,
                            assets: assetSource,
                            mode: mode,
                            slots: [
                                "slot1": {
                                    AnyView(
                                        TextField("Ô 1", text: $slotValue)
                                            .textFieldStyle(.roundedBorder)
                                            .submitLabel(.done)
                                            .frame(width: 140, height: 34)
                                            .accessibilityIdentifier("harness.zoom.slotInput")
                                    )
                                }
                            ],
                            locale: locale
                        )
                        .padding(16)
                    }
                    .accessibilityIdentifier("harness.zoom.scrollView")
                }
            }
            .navigationTitle(mode == .locked ? "Locked Zoom Harness" : "Zoom Harness")
            .task {
                _ = try? await assetSource.load(.attempt(assetId: Self.zoomAssetId))
                _ = try? await assetSource.load(.attempt(assetId: Self.noZoomAssetId))
                _ = try? await assetSource.load(.attempt(assetId: Self.inlineAssetId))
                isReady = true
            }
        }
    }
}

private struct FormulaOverflowHarnessView: View {
    let locale: AppLocale
    @State private var currentPage = 0

    private var wideInlineDoc: RichDocument {
        let terms = (1...40).map { "a_{\($0)}" }.joined(separator: " + ")
        let matrixCols = (1...12).map { "m_{1,\($0)}" }.joined(separator: " & ")
        let html = """
        <p>Đầu trang 0</p>
        <p>Biểu thức dài \\(\(terms) = S\\) kết thúc.</p>
        <span class="math-block">\\[\\begin{pmatrix} \(matrixCols) \\end{pmatrix}\\]</span>
        <p>Cuối trang 0</p>
        """
        return RichHTMLParser.parse(html)
    }

    var body: some View {
        VStack(spacing: 8) {
            Text("PAGE:\(currentPage)")
                .font(.headline.monospaced())
                .accessibilityIdentifier("harness.overflow.pageIndicator")

            TabView(selection: $currentPage) {
                ScrollView {
                    RichContentView(
                        document: wideInlineDoc,
                        locale: locale,
                        fixedWidth: 358
                    )
                    .padding(16)
                }
                .tag(0)
                .accessibilityIdentifier("harness.overflow.page0")

                VStack {
                    Text("PAGE_ONE_CONTENT")
                        .accessibilityIdentifier("harness.overflow.page1")
                }
                .tag(1)
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
        }
    }
}

private struct TempFilesHarnessView: View {
    @State private var mountDirCount: Int = -1
    @State private var createdFilePath: String = ""
    @State private var createdExists: Bool = false

    private static let pdfAssetId = "aaaa1111-2222-4333-8444-555566667777"

    var body: some View {
        VStack(spacing: 16) {
            Text("MOUNT_COUNT:\(mountDirCount)")
                .font(.headline.monospaced())
                .accessibilityIdentifier("harness.temp.mountCount")

            Text("CREATED_EXISTS:\(createdExists ? "true" : "false")")
                .font(.subheadline.monospaced())
                .accessibilityIdentifier("harness.temp.createdExists")

            Text(createdFilePath)
                .font(.caption2.monospaced())
                .accessibilityIdentifier("harness.temp.createdPath")

            Button("Open Viewer Temp File") {
                Task {
                    await openTempFile()
                }
            }
            .buttonStyle(.borderedProminent)
            .accessibilityIdentifier("harness.temp.openButton")
        }
        .padding(24)
        .onAppear {
            mountDirCount = AttemptAssetSource.tempFileCountForHarness()
        }
    }

    private func openTempFile() async {
        let source = AttemptAssetSource.makeDevHarnessSource(
            attemptId: "77777777-7777-4777-8777-777777777777"
        )
        if let tempFile = try? await source.openForViewer(.attempt(assetId: Self.pdfAssetId), fileExtension: "pdf") {
            TempFileHarnessRetainer.shared.retained.append(tempFile)
            createdFilePath = tempFile.url.lastPathComponent
            createdExists = AttemptAssetSource.fileExistsForHarness(at: tempFile.url)
        }
    }
}

private final class TempFileHarnessRetainer: @unchecked Sendable {
    static let shared = TempFileHarnessRetainer()
    var retained: [TempAssetFile] = []
}
#endif
