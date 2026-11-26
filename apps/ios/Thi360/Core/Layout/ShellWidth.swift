import SwiftUI
import UIKit

enum ShellWidth: String, Equatable, Sendable {
    case narrow
    case wide
    case twoColumn

    static func resolve(
        horizontalSizeClass: UserInterfaceSizeClass?,
        layoutMode: ShellLayoutMode,
        availableWidth: CGFloat? = nil
    ) -> ShellWidth {
        guard horizontalSizeClass == .regular else {
            return .narrow
        }
        switch layoutMode {
        case .compactTabBar:
            return .narrow
        case .iPadSidebar, .phoneTwoPaneBook:
            if let availableWidth, availableWidth > 0, availableWidth < 600 {
                return .wide
            }
            return .twoColumn
        }
    }

    static func resolve(
        idiom: UIUserInterfaceIdiom,
        horizontalSizeClass: UserInterfaceSizeClass?,
        availableWidth: CGFloat? = nil
    ) -> ShellWidth {
        let mode = ShellLayoutResolver.resolve(idiom: idiom, horizontalSizeClass: horizontalSizeClass)
        return resolve(horizontalSizeClass: horizontalSizeClass, layoutMode: mode, availableWidth: availableWidth)
    }
}

private struct ShellWidthEnvironmentKey: EnvironmentKey {
    static let defaultValue: ShellWidth = .narrow
}

extension EnvironmentValues {
    var shellWidth: ShellWidth {
        get { self[ShellWidthEnvironmentKey.self] }
        set { self[ShellWidthEnvironmentKey.self] = newValue }
    }
}
