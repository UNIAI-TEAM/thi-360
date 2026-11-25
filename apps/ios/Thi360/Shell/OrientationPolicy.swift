import SwiftUI
import UIKit

enum OrientationPolicy {
    static func supportedOrientations(
        idiom: UIUserInterfaceIdiom,
        horizontalSizeClass: UIUserInterfaceSizeClass
    ) -> UIInterfaceOrientationMask {
        switch idiom {
        case .pad:
            return .all
        case .phone:
            return horizontalSizeClass == .regular ? .allButUpsideDown : .portrait
        default:
            return .portrait
        }
    }

    static func allowsLandscape(
        idiom: UIUserInterfaceIdiom,
        horizontalSizeClass: UIUserInterfaceSizeClass
    ) -> Bool {
        let mask = supportedOrientations(idiom: idiom, horizontalSizeClass: horizontalSizeClass)
        return mask.contains(.landscapeLeft) || mask.contains(.landscapeRight)
    }
}

final class Thi360AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        supportedInterfaceOrientationsFor window: UIWindow?
    ) -> UIInterfaceOrientationMask {
        let idiom = UIDevice.current.userInterfaceIdiom
        let sizeClass = window?.traitCollection.horizontalSizeClass
            ?? activeHorizontalSizeClass(in: application)
            ?? .compact
        return OrientationPolicy.supportedOrientations(idiom: idiom, horizontalSizeClass: sizeClass)
    }

    private func activeHorizontalSizeClass(in application: UIApplication) -> UIUserInterfaceSizeClass? {
        for scene in application.connectedScenes {
            guard let windowScene = scene as? UIWindowScene else { continue }
            let sizeClass = windowScene.traitCollection.horizontalSizeClass
            if sizeClass != .unspecified {
                return sizeClass
            }
        }
        return nil
    }

    @MainActor
    static func refreshSupportedOrientations(for horizontalSizeClass: UserInterfaceSizeClass?) {
        let uiSizeClass: UIUserInterfaceSizeClass = (horizontalSizeClass == .regular) ? .regular : .compact
        let mask = OrientationPolicy.supportedOrientations(
            idiom: UIDevice.current.userInterfaceIdiom,
            horizontalSizeClass: uiSizeClass
        )

        for scene in UIApplication.shared.connectedScenes {
            guard let windowScene = scene as? UIWindowScene else { continue }
            for window in windowScene.windows {
                window.rootViewController?.setNeedsUpdateOfSupportedInterfaceOrientations()
            }
            if mask == .portrait && windowScene.interfaceOrientation.isLandscape {
                windowScene.requestGeometryUpdate(.iOS(interfaceOrientations: .portrait)) { _ in }
            }
        }
    }
}
