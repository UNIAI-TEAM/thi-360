import Foundation
import Observation
import SwiftUI

enum AppLocale: String, CaseIterable, Codable, Identifiable, Sendable {
    case vi
    case en

    var id: String { rawValue }
    var locale: Locale { Locale(identifier: rawValue) }
}

enum ThemeMode: String, CaseIterable, Codable, Identifiable, Sendable {
    case light
    case dark

    var id: String { rawValue }

    var colorScheme: ColorScheme {
        switch self {
        case .light: .light
        case .dark: .dark
        }
    }
}

@Observable
@MainActor
final class AppPreferences {
    static let localeStorageKey = "thi360.preferences.locale"
    static let themeModeStorageKey = "thi360.preferences.themeMode"

    private let defaults: UserDefaults

    var locale: AppLocale {
        didSet {
            defaults.set(locale.rawValue, forKey: Self.localeStorageKey)
        }
    }

    var themeMode: ThemeMode? {
        didSet {
            if let themeMode {
                defaults.set(themeMode.rawValue, forKey: Self.themeModeStorageKey)
            } else {
                defaults.removeObject(forKey: Self.themeModeStorageKey)
            }
        }
    }

    init(userDefaults: UserDefaults = .standard) {
        self.defaults = userDefaults

        if let storedLocale = userDefaults.string(forKey: Self.localeStorageKey),
           let parsedLocale = AppLocale(rawValue: storedLocale)
        {
            self.locale = parsedLocale
        } else {
            self.locale = .vi
        }

        if let storedTheme = userDefaults.string(forKey: Self.themeModeStorageKey),
           let parsedTheme = ThemeMode(rawValue: storedTheme)
        {
            self.themeMode = parsedTheme
        } else {
            self.themeMode = nil
        }
    }

    func effectiveThemeMode(serverDefault: ThemeMode = .light) -> ThemeMode {
        themeMode ?? serverDefault
    }

    static func makeForCurrentProcess(
        arguments: [String] = ProcessInfo.processInfo.arguments,
        environment: AppEnvironment = .current
    ) -> AppPreferences {
        guard environment.isDev else {
            return AppPreferences(userDefaults: .standard)
        }

        let suiteName = argumentValue(after: "-uitest-defaults-suite", in: arguments)
        let defaults: UserDefaults
        if let suiteName, let suite = UserDefaults(suiteName: suiteName) {
            defaults = suite
        } else {
            defaults = .standard
        }

        if arguments.contains("-uitest-reset-preferences") {
            defaults.removeObject(forKey: Self.localeStorageKey)
            defaults.removeObject(forKey: Self.themeModeStorageKey)
        }

        let preferences = AppPreferences(userDefaults: defaults)

        if let rawLocale = argumentValue(after: "-uitest-locale", in: arguments),
           let overrideLocale = AppLocale(rawValue: rawLocale)
        {
            preferences.locale = overrideLocale
        }

        if let rawTheme = argumentValue(after: "-uitest-theme", in: arguments),
           let overrideTheme = ThemeMode(rawValue: rawTheme)
        {
            preferences.themeMode = overrideTheme
        }

        return preferences
    }

    private static func argumentValue(after flag: String, in arguments: [String]) -> String? {
        guard let index = arguments.firstIndex(of: flag), index + 1 < arguments.count else {
            return nil
        }
        return arguments[index + 1]
    }
}
