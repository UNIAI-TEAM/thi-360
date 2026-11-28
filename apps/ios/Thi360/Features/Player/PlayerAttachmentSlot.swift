import SwiftUI

struct PlayerAttachmentContext: Equatable, Sendable {
    let attemptId: String
    let partId: PartID
    let item: Components.Schemas.AttemptItemView
    let canInteract: Bool
    let isLocked: Bool
    let locale: AppLocale
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
