import Foundation
import Observation

enum AttachmentOpenState: Equatable, Sendable {
    case idle
    case loading(assetId: String)
    case openFailed(assetId: String)
}

struct ActiveAttachmentPreview: Identifiable {
    let id = UUID()
    let attachment: QuestionAttachment
    let tempFile: TempAssetFile
}

@Observable
@MainActor
final class AttachmentOpener: AudioLogoutCleanable {
    typealias PreviewPresenter = @MainActor (QuestionAttachment, TempAssetFile, @escaping @MainActor () -> Void) throws -> Void

    private(set) var state: AttachmentOpenState = .idle
    var activePreview: ActiveAttachmentPreview?

    @ObservationIgnored private let assetSource: AttemptAssetSource
    @ObservationIgnored private let customPresenter: PreviewPresenter?
    @ObservationIgnored private let registry: AttemptAudioSessionRegistry
    @ObservationIgnored private var openHandles: [ObjectIdentifier: TempAssetFile] = [:]
    @ObservationIgnored private var registryToken: UUID?

    init(
        assetSource: AttemptAssetSource,
        customPresenter: PreviewPresenter? = nil,
        registry: AttemptAudioSessionRegistry? = nil
    ) {
        let resolvedRegistry = registry ?? .shared
        self.assetSource = assetSource
        self.customPresenter = customPresenter
        self.registry = resolvedRegistry
        self.registryToken = resolvedRegistry.register(self)
    }

    @discardableResult
    func open(_ attachment: QuestionAttachment) async -> Bool {
        state = .loading(assetId: attachment.assetId)
        let tempFile: TempAssetFile
        do {
            tempFile = try await assetSource.openForViewer(
                .attempt(assetId: attachment.assetId),
                fileExtension: attachment.fileExtension
            )
        } catch is CancellationError {
            state = .idle
            return false
        } catch {
            state = .openFailed(assetId: attachment.assetId)
            return false
        }

        let handleKey = ObjectIdentifier(tempFile)
        openHandles[handleKey] = tempFile

        if let customPresenter {
            do {
                try customPresenter(attachment, tempFile) { [weak self] in
                    self?.releaseHandle(key: handleKey)
                }
                state = .idle
                return true
            } catch {
                releaseHandle(key: handleKey)
                state = .openFailed(assetId: attachment.assetId)
                return false
            }
        }

        if let previous = activePreview {
            releaseHandle(key: ObjectIdentifier(previous.tempFile))
        }
        activePreview = ActiveAttachmentPreview(attachment: attachment, tempFile: tempFile)
        state = .idle
        return true
    }

    func dismissViewer() {
        guard let preview = activePreview else { return }
        activePreview = nil
        releaseHandle(key: ObjectIdentifier(preview.tempFile))
    }

    func stopAndPurgeForLogout() {
        activePreview = nil
        releaseAllHandles()
        assetSource.purgeAll()
    }

    func close() {
        activePreview = nil
        releaseAllHandles()
        if let registryToken {
            registry.unregister(registryToken)
            self.registryToken = nil
        }
    }

    private func releaseHandle(key: ObjectIdentifier) {
        guard let handle = openHandles.removeValue(forKey: key) else { return }
        handle.close()
    }

    private func releaseAllHandles() {
        let handles = Array(openHandles.values)
        openHandles.removeAll()
        for handle in handles {
            handle.close()
        }
    }
}
