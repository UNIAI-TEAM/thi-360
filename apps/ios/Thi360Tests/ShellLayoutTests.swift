import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class ShellLayoutTests: XCTestCase {
    func testCompactPhoneResolvesToBottomTabBar() {
        XCTAssertEqual(
            ShellLayoutResolver.resolve(idiom: .phone, horizontalSizeClass: .compact),
            .compactTabBar
        )

        let (window, controller, _) = makeHostedShell(
            idiom: .phone,
            size: CGSize(width: 390, height: 844),
            horizontalSizeClass: .compact,
            themeMode: .light,
            destination: .home
        )
        defer { window.isHidden = true }

        let tabBars = findViews(ofType: UITabBar.self, in: controller.view)
        XCTAssertEqual(tabBars.count, 1)
        XCTAssertEqual(tabBars.first?.items?.count, 4)
    }

    func testIPadRegularResolvesToSidebar() {
        XCTAssertEqual(
            ShellLayoutResolver.resolve(idiom: .pad, horizontalSizeClass: .regular),
            .iPadSidebar
        )

        let (window, controller, _) = makeHostedShell(
            idiom: .pad,
            size: CGSize(width: 1180, height: 820),
            horizontalSizeClass: .regular,
            themeMode: .light,
            destination: .account
        )
        defer { window.isHidden = true }

        let splitControllers = findViewControllers(ofType: UISplitViewController.self, in: controller)
        XCTAssertFalse(splitControllers.isEmpty, "iPad regular layout should host NavigationSplitView")
        XCTAssertTrue(findViews(ofType: UITabBar.self, in: controller.view).isEmpty)
    }

    func testIPhoneRegularResolvesToTwoPaneBookWithLeftTabBar() {
        XCTAssertEqual(
            ShellLayoutResolver.resolve(idiom: .phone, horizontalSizeClass: .regular),
            .phoneTwoPaneBook
        )

        let (window, controller, _) = makeHostedShell(
            idiom: .phone,
            size: CGSize(width: 951, height: 669),
            horizontalSizeClass: .regular,
            themeMode: .light,
            destination: .account
        )
        defer { window.isHidden = true }

        XCTAssertTrue(findViews(ofType: UITabBar.self, in: controller.view).isEmpty)
        XCTAssertEqual(controller.view.bounds.size, CGSize(width: 951, height: 669))
    }

    func testSplitViewCompact() {
        XCTAssertEqual(
            ShellLayoutResolver.resolve(idiom: .pad, horizontalSizeClass: .compact),
            .compactTabBar
        )

        let (window, controller, _) = makeHostedShell(
            idiom: .pad,
            size: CGSize(width: 375, height: 1180),
            horizontalSizeClass: .compact,
            themeMode: .light,
            destination: .home
        )
        defer { window.isHidden = true }

        let tabBars = findViews(ofType: UITabBar.self, in: controller.view)
        XCTAssertEqual(tabBars.count, 1)
        XCTAssertEqual(tabBars.first?.items?.count, 4)
    }

    func testStatePreservedAcrossCompactAndRegularTransitions() {
        let suite = "vn.unicom.thi360.tests.layout.state.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }

        let preferences = AppPreferences(userDefaults: defaults)
        let router = AppRouter(initialDestination: .account, stateProbeEnabled: true)
        router.draftProbeText = "Keep across transition"

        let rootView = AppRootView(router: router, preferences: preferences, idiomOverride: .phone)
        let controller = UIHostingController(rootView: rootView)
        controller.traitOverrides.horizontalSizeClass = .compact

        let window: UIWindow
        if let activeScene = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).first {
            window = UIWindow(windowScene: activeScene)
            window.frame = CGRect(origin: .zero, size: CGSize(width: 466, height: 678))
        } else {
            window = UIWindow(frame: CGRect(origin: .zero, size: CGSize(width: 466, height: 678)))
        }
        window.rootViewController = controller
        window.makeKeyAndVisible()
        pumpLayout(window: window)
        defer { window.isHidden = true }

        XCTAssertEqual(findViews(ofType: UITabBar.self, in: controller.view).count, 1)

        controller.traitOverrides.horizontalSizeClass = .regular
        window.frame = CGRect(origin: .zero, size: CGSize(width: 951, height: 669))
        pumpLayout(window: window)

        XCTAssertEqual(router.selectedDestination, .account)
        XCTAssertEqual(router.draftProbeText, "Keep across transition")
        XCTAssertTrue(findViews(ofType: UITabBar.self, in: controller.view).isEmpty)

        controller.traitOverrides.horizontalSizeClass = .compact
        window.frame = CGRect(origin: .zero, size: CGSize(width: 466, height: 678))
        pumpLayout(window: window)

        XCTAssertEqual(router.selectedDestination, .account)
        XCTAssertEqual(router.draftProbeText, "Keep across transition")
        XCTAssertEqual(findViews(ofType: UITabBar.self, in: controller.view).count, 1)
    }

    func testSnapshotMatrixLightAndDark() throws {
        struct SnapshotSpec {
            let name: String
            let idiom: UIUserInterfaceIdiom
            let size: CGSize
            let sizeClass: UIUserInterfaceSizeClass
            let destination: Destination
        }

        let specs: [SnapshotSpec] = [
            SnapshotSpec(name: "iphone17-390x844-home", idiom: .phone, size: CGSize(width: 390, height: 844), sizeClass: .compact, destination: .home),
            SnapshotSpec(name: "ipad-air-11-portrait-820x1180-account", idiom: .pad, size: CGSize(width: 820, height: 1180), sizeClass: .regular, destination: .account),
            SnapshotSpec(name: "ipad-air-11-landscape-1180x820-account", idiom: .pad, size: CGSize(width: 1180, height: 820), sizeClass: .regular, destination: .account),
            SnapshotSpec(name: "iphone-duo-folded-466x678-home", idiom: .phone, size: CGSize(width: 466, height: 678), sizeClass: .compact, destination: .home),
            SnapshotSpec(name: "iphone-duo-unfolded-portrait-669x951-account", idiom: .phone, size: CGSize(width: 669, height: 951), sizeClass: .regular, destination: .account),
            SnapshotSpec(name: "iphone-duo-unfolded-landscape-951x669-account", idiom: .phone, size: CGSize(width: 951, height: 669), sizeClass: .regular, destination: .account)
        ]

        let outputDir = ProcessInfo.processInfo.environment["THI360_SNAPSHOT_DIR"]

        for spec in specs {
            var renderedPNGs: [ThemeMode: Data] = [:]

            for mode in ThemeMode.allCases {
                let (window, _, suite) = makeHostedShell(
                    idiom: spec.idiom,
                    size: spec.size,
                    horizontalSizeClass: spec.sizeClass,
                    themeMode: mode,
                    destination: spec.destination
                )
                defer {
                    window.isHidden = true
                    UserDefaults(suiteName: suite)?.removePersistentDomain(forName: suite)
                }

                let rendererFormat = UIGraphicsImageRendererFormat()
                rendererFormat.scale = 2.0
                rendererFormat.opaque = true
                let renderer = UIGraphicsImageRenderer(size: spec.size, format: rendererFormat)
                let image = renderer.image { context in
                    let drew = window.drawHierarchy(in: CGRect(origin: .zero, size: spec.size), afterScreenUpdates: true)
                    if !drew {
                        window.layer.render(in: context.cgContext)
                    }
                }
                let png = try XCTUnwrap(image.pngData())
                XCTAssertGreaterThan(png.count, 4096, "Snapshot \(spec.name)-\(mode.rawValue) unexpectedly small")
                XCTAssertFalse(isSolidBlackImage(image), "Snapshot \(spec.name)-\(mode.rawValue) rendered solid black")
                renderedPNGs[mode] = png

                let attachment = XCTAttachment(image: image)
                attachment.name = "\(spec.name)-\(mode.rawValue).png"
                attachment.lifetime = .keepAlways
                add(attachment)

                if let outputDir, !outputDir.isEmpty {
                    let dirURL = URL(fileURLWithPath: outputDir, isDirectory: true)
                    try FileManager.default.createDirectory(at: dirURL, withIntermediateDirectories: true)
                    let fileURL = dirURL.appendingPathComponent("\(spec.name)-\(mode.rawValue).png")
                    try png.write(to: fileURL)
                }
            }

            XCTAssertNotEqual(
                renderedPNGs[.light],
                renderedPNGs[.dark],
                "Light and dark snapshots for \(spec.name) must differ"
            )
        }
    }

    private func makeHostedShell(
        idiom: UIUserInterfaceIdiom,
        size: CGSize,
        horizontalSizeClass: UIUserInterfaceSizeClass,
        themeMode: ThemeMode,
        destination: Destination
    ) -> (UIWindow, UIHostingController<AppRootView>, String) {
        let suite = "vn.unicom.thi360.tests.layout.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        let preferences = AppPreferences(userDefaults: defaults)
        preferences.themeMode = themeMode

        let router = AppRouter(initialDestination: destination)
        let rootView = AppRootView(router: router, preferences: preferences, idiomOverride: idiom)
        let controller = UIHostingController(rootView: rootView)
        controller.traitOverrides.horizontalSizeClass = horizontalSizeClass
        controller.traitOverrides.userInterfaceStyle = (themeMode == .dark) ? .dark : .light

        let window: UIWindow
        if let activeScene = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).first {
            window = UIWindow(windowScene: activeScene)
            window.frame = CGRect(origin: .zero, size: size)
        } else {
            window = UIWindow(frame: CGRect(origin: .zero, size: size))
        }
        window.rootViewController = controller
        window.overrideUserInterfaceStyle = (themeMode == .dark) ? .dark : .light
        window.makeKeyAndVisible()
        pumpLayout(window: window)
        return (window, controller, suite)
    }

    private func isSolidBlackImage(_ image: UIImage) -> Bool {
        guard let cgImage = image.cgImage,
              let data = cgImage.dataProvider?.data,
              let bytes = CFDataGetBytePtr(data)
        else {
            return true
        }
        let length = CFDataGetLength(data)
        let step = max(4, (length / 400) * 4)
        var offset = 0
        while offset + 2 < length {
            if bytes[offset] > 4 || bytes[offset + 1] > 4 || bytes[offset + 2] > 4 {
                return false
            }
            offset += step
        }
        return true
    }

    private func pumpLayout(window: UIWindow) {
        window.setNeedsLayout()
        window.layoutIfNeeded()
        RunLoop.main.run(until: Date().addingTimeInterval(0.05))
        window.layoutIfNeeded()
    }

    private func findViews<T: UIView>(ofType type: T.Type, in root: UIView) -> [T] {
        var found: [T] = []
        if let typed = root as? T {
            found.append(typed)
        }
        for subview in root.subviews {
            found.append(contentsOf: findViews(ofType: type, in: subview))
        }
        return found
    }

    private func findViewControllers<T: UIViewController>(
        ofType type: T.Type,
        in root: UIViewController
    ) -> [T] {
        var found: [T] = []
        if let typed = root as? T {
            found.append(typed)
        }
        for child in root.children {
            found.append(contentsOf: findViewControllers(ofType: type, in: child))
        }
        return found
    }
}
