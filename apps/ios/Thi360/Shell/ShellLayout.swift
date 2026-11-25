import SwiftUI
import UIKit

enum Destination: String, CaseIterable, Identifiable, Hashable, Sendable {
    case home
    case myExams
    case history
    case account

    var id: String { rawValue }

    var titleKey: String {
        switch self {
        case .home: "shell.tab.home"
        case .myExams: "shell.tab.myExams"
        case .history: "shell.tab.history"
        case .account: "shell.tab.account"
        }
    }

    var symbolName: String {
        switch self {
        case .home: "house"
        case .myExams: "doc.text"
        case .history: "clock.arrow.circlepath"
        case .account: "person"
        }
    }

    var placeholderDescriptionKey: String? {
        switch self {
        case .home: "shell.placeholder.homeDescription"
        case .myExams: "shell.placeholder.myExamsDescription"
        case .history: "shell.placeholder.historyDescription"
        case .account: nil
        }
    }

    var isPlaceholder: Bool {
        self != .account
    }

    var accessibilityIdentifier: String {
        "shell.dest.\(rawValue)"
    }
}

enum ShellLayoutMode: String, Equatable, Sendable {
    case compactTabBar
    case iPadSidebar
    case phoneTwoPaneBook

    var accessibilityIdentifier: String {
        "shell.layout.\(rawValue)"
    }
}

enum ShellLayoutResolver {
    static func resolve(
        idiom: UIUserInterfaceIdiom,
        horizontalSizeClass: UserInterfaceSizeClass?
    ) -> ShellLayoutMode {
        guard horizontalSizeClass == .regular else {
            return .compactTabBar
        }
        switch idiom {
        case .pad:
            return .iPadSidebar
        default:
            return .phoneTwoPaneBook
        }
    }

    static func resolve(
        idiom: UIUserInterfaceIdiom,
        horizontalSizeClass: UIUserInterfaceSizeClass
    ) -> ShellLayoutMode {
        let mapped: UserInterfaceSizeClass?
        switch horizontalSizeClass {
        case .regular: mapped = .regular
        case .compact: mapped = .compact
        case .unspecified: mapped = nil
        @unknown default: mapped = nil
        }
        return resolve(idiom: idiom, horizontalSizeClass: mapped)
    }
}
