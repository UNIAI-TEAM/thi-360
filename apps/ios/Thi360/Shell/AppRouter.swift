import Foundation
import Observation
import SwiftUI
import UIKit

@Observable
@MainActor
final class AppRouter {
    var selectedDestination: Destination = .home
    var draftProbeText: String = ""
    let stateProbeEnabled: Bool
    var forcedSizeClassForUITest: UserInterfaceSizeClass?

    init(
        initialDestination: Destination = .home,
        stateProbeEnabled: Bool = false
    ) {
        self.selectedDestination = initialDestination
        self.stateProbeEnabled = stateProbeEnabled
    }

    func toggleForcedSizeClass(current: UserInterfaceSizeClass?) {
        guard stateProbeEnabled else { return }
        let active = forcedSizeClassForUITest ?? current ?? .compact
        forcedSizeClassForUITest = (active == .compact) ? .regular : .compact
    }

    static func makeForCurrentProcess(
        arguments: [String] = ProcessInfo.processInfo.arguments,
        environment: AppEnvironment = .current
    ) -> AppRouter {
        guard environment.isDev else {
            return AppRouter()
        }

        let probeEnabled = arguments.contains("-uitest-state-probe")
        var initialDestination: Destination = .home
        if let index = arguments.firstIndex(of: "-uitest-destination"),
           index + 1 < arguments.count,
           let parsed = Destination(rawValue: arguments[index + 1])
        {
            initialDestination = parsed
        }
        return AppRouter(initialDestination: initialDestination, stateProbeEnabled: probeEnabled)
    }
}

struct AppRootView: View {
    @Bindable var router: AppRouter
    @Bindable var preferences: AppPreferences
    var paletteSet: BrandPaletteSet = .defaultThi360Green
    var idiomOverride: UIUserInterfaceIdiom?

    private var effectiveThemeMode: ThemeMode {
        preferences.effectiveThemeMode(serverDefault: paletteSet.defaultMode)
    }

    private var activePalette: BrandPalette {
        paletteSet.palette(for: effectiveThemeMode)
    }

    var body: some View {
        AdaptiveShellView(
            router: router,
            preferences: preferences,
            idiom: idiomOverride ?? UIDevice.current.userInterfaceIdiom
        )
        .environment(\.brandPalette, activePalette)
        .environment(\.locale, preferences.locale.locale)
        .tint(activePalette.primaryFillColor)
        .preferredColorScheme(effectiveThemeMode.colorScheme)
    }
}
