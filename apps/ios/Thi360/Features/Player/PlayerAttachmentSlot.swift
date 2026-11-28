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

private struct PlayerAttachmentHostKey: EnvironmentKey {
    static let defaultValue: (any PlayerAttachmentHost)? = nil
}

extension EnvironmentValues {
    var playerAttachmentHost: (any PlayerAttachmentHost)? {
        get { self[PlayerAttachmentHostKey.self] }
        set { self[PlayerAttachmentHostKey.self] = newValue }
    }
}
