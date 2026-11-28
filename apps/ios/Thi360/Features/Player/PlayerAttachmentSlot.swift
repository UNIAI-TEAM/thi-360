import SwiftUI

struct PlayerAttachmentContext: Equatable, Sendable {
    let attemptId: String
    let partId: PartID
    let item: Components.Schemas.AttemptItemView
    let childItems: [Components.Schemas.AttemptItemView]
    let canInteract: Bool
    let isLocked: Bool
    let locale: AppLocale

    init(
        attemptId: String,
        partId: PartID,
        item: Components.Schemas.AttemptItemView,
        childItems: [Components.Schemas.AttemptItemView] = [],
        canInteract: Bool,
        isLocked: Bool,
        locale: AppLocale
    ) {
        self.attemptId = attemptId
        self.partId = partId
        self.item = item
        self.childItems = childItems
        self.canInteract = canInteract
        self.isLocked = isLocked
        self.locale = locale
    }

    static func hasQuestionSetAttachments(
        parent: Components.Schemas.AttemptItemView,
        children: [Components.Schemas.AttemptItemView]
    ) -> Bool {
        if let list = parent.attachments, !list.isEmpty {
            return true
        }
        return children.contains { child in
            (child.attachments ?? []).contains { att in
                (att.mime ?? "").trimmingCharacters(in: .whitespacesAndNewlines).lowercased().hasPrefix("audio/")
            }
        }
    }
}

@MainActor
protocol PlayerAttachmentHost: AnyObject {
    func attachmentsView(for context: PlayerAttachmentContext) -> AnyView
    func onBeforeClose(_ target: CloseTarget)
}

@MainActor
final class LivePlayerAttachmentHost: PlayerAttachmentHost {
    private let attemptId: String
    private let engine: AttemptEngine
    private let gateway: any AttemptAudioGateway
    private let assetSource: AttemptAssetSource
    private var openersByItemKey: [String: AttachmentOpener] = [:]
    private var controllersByKey: [String: AttemptAudioController] = [:]
    private var isClosed = false

    init(
        attemptId: String,
        engine: AttemptEngine,
        gateway: any AttemptAudioGateway,
        assetSource: AttemptAssetSource
    ) {
        self.attemptId = attemptId
        self.engine = engine
        self.gateway = gateway
        self.assetSource = assetSource
    }

    var activeOpenerCount: Int {
        openersByItemKey.count
    }

    var activeControllerCount: Int {
        controllersByKey.count
    }

    func attachmentsView(for context: PlayerAttachmentContext) -> AnyView {
        guard !isClosed else { return AnyView(EmptyView()) }
        let merged = AttachmentMerge.mergeQuestionAttachments(
            parentAttachments: context.item.attachments,
            childItems: context.childItems.isEmpty ? nil : context.childItems
        )
        guard !merged.isEmpty else { return AnyView(EmptyView()) }

        let opener = openerFor(context: context)
        let externalDisabled = !context.canInteract || context.isLocked
        return AnyView(
            LiveAttachmentsSlotView(
                attachments: merged,
                opener: opener,
                locale: context.locale,
                externalDisabled: externalDisabled,
                controllerProvider: { [weak self] attachment in
                    self?.controller(
                        for: attachment,
                        partId: context.partId,
                        initialExternalDisabled: externalDisabled
                    )
                }
            )
        )
    }

    func onBeforeClose(_ target: CloseTarget) {
        guard !isClosed else { return }
        for controller in controllersByKey.values {
            switch target {
            case .attempt:
                controller.pauseForInterruption(.leftScreen)
            case let .part(partId):
                if controller.partId == nil || controller.partId == partId {
                    controller.pauseForInterruption(.leftScreen)
                }
            }
        }
    }

    func close() {
        guard !isClosed else { return }
        isClosed = true
        for opener in openersByItemKey.values {
            opener.close()
        }
        openersByItemKey.removeAll()
        for controller in controllersByKey.values {
            controller.close()
        }
        controllersByKey.removeAll()
    }

    private func openerFor(context: PlayerAttachmentContext) -> AttachmentOpener {
        let itemKey = context.item.itemId ?? "\(context.partId)::\(context.item.displayNo ?? 0)"
        if let existing = openersByItemKey[itemKey] {
            return existing
        }
        let created = AttachmentOpener(assetSource: assetSource)
        openersByItemKey[itemKey] = created
        return created
    }

    private func controller(
        for attachment: QuestionAttachment,
        partId: PartID,
        initialExternalDisabled: Bool
    ) -> AttemptAudioController? {
        guard !isClosed else { return nil }
        let key = "\(partId)::\(attachment.assetId)"
        if let existing = controllersByKey[key] {
            return existing
        }
        let created = AttemptAudioController(
            attemptId: attemptId,
            assetId: attachment.assetId,
            partId: partId.isEmpty ? nil : partId,
            fileName: attachment.fileName,
            engine: engine,
            gateway: gateway,
            externalDisabled: initialExternalDisabled
        )
        controllersByKey[key] = created
        return created
    }
}

private struct LiveAttachmentsSlotView: View {
    let attachments: [QuestionAttachment]
    let opener: AttachmentOpener
    let locale: AppLocale
    let externalDisabled: Bool
    let controllerProvider: @MainActor (QuestionAttachment) -> AttemptAudioController?

    var body: some View {
        AttachmentsView(attachments: attachments, opener: opener, locale: locale) { attachment in
            if let controller = controllerProvider(attachment) {
                LiveAudioPlayerWrapper(
                    controller: controller,
                    locale: locale,
                    externalDisabled: externalDisabled
                )
            }
        }
    }
}

private struct LiveAudioPlayerWrapper: View {
    let controller: AttemptAudioController
    let locale: AppLocale
    let externalDisabled: Bool

    var body: some View {
        AttemptAudioPlayerView(controller: controller, locale: locale)
            .onAppear {
                controller.setExternalDisabled(externalDisabled)
            }
            .onChange(of: externalDisabled) { _, newValue in
                controller.setExternalDisabled(newValue)
            }
    }
}

private struct PlayerAttachmentHostKey: EnvironmentKey {
    static let defaultValue: (any PlayerAttachmentHost)? = nil
}

extension EnvironmentValues {
    var playerAttachmentHost: (any PlayerAttachmentHost)? {
        get { self[PlayerAttachmentHostKey.self] }
        set { self[PlayerAttachmentHostKey.self] = newValue }
    }
}
