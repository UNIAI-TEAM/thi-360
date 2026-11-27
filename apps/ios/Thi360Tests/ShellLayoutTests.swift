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

    func testIPhoneDuoUnfoldedRendersRouteRootAndEmptyDetailAndReloadsLeftPane() {
        let suite = "vn.unicom.thi360.tests.layout.duo.routes.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }

        let preferences = AppPreferences(userDefaults: defaults)
        let router = AppRouter(initialDestination: .home)
        let rootView = AppRootView(router: router, preferences: preferences, idiomOverride: .phone)
        let controller = UIHostingController(rootView: rootView)
        controller.traitOverrides.horizontalSizeClass = .regular

        let window: UIWindow
        if let activeScene = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).first {
            window = UIWindow(windowScene: activeScene)
            window.frame = CGRect(origin: .zero, size: CGSize(width: 951, height: 669))
        } else {
            window = UIWindow(frame: CGRect(origin: .zero, size: CGSize(width: 951, height: 669)))
        }
        window.rootViewController = controller
        window.makeKeyAndVisible()
        pumpLayout(window: window)
        defer { window.isHidden = true }

        XCTAssertTrue(findViews(ofType: UITabBar.self, in: controller.view).isEmpty)
        let defaultNavControllers = findViewControllers(ofType: UINavigationController.self, in: controller)
        XCTAssertEqual(defaultNavControllers.count, 2)
        let combinedPaneWidth = defaultNavControllers.reduce(CGFloat(0)) { $0 + $1.view.bounds.width }
        XCTAssertEqual(
            combinedPaneWidth + 1,
            controller.view.bounds.width,
            accuracy: 2.0,
            "Both Duo half-panes plus the 1pt divider must fill the host width"
        )
        for navController in defaultNavControllers {
            let paneWidth = navController.view.bounds.width
            XCTAssertGreaterThan(paneWidth, 300)
            XCTAssertLessThan(paneWidth, 600, "Hosted Duo half-pane must remain below the 600pt twoColumn threshold")
            XCTAssertEqual(
                ShellWidth.resolve(
                    horizontalSizeClass: .regular,
                    layoutMode: .phoneTwoPaneBook,
                    availableWidth: paneWidth
                ),
                .wide,
                "Hosted Duo half-pane must resolve to .wide, never .twoColumn"
            )
        }

        router.openProduct(code: "duo-exam-1")
        pumpLayout(window: window)
        XCTAssertEqual(router.path(for: .home), [.product(code: "duo-exam-1")])

        router.switchTab(.myExams, reload: true)
        pumpLayout(window: window)
        XCTAssertEqual(router.selectedDestination, .myExams)
        XCTAssertEqual(router.reloadCount(for: .myExams), 1)
        XCTAssertEqual(router.path(for: .myExams), [])

        router.openHistory(resetFilters: true)
        pumpLayout(window: window)
        XCTAssertEqual(router.selectedDestination, .history)
        XCTAssertTrue(router.historyResetFilters)
        XCTAssertEqual(router.reloadCount(for: .history), 1)

        let landscapeHalf = AdaptiveShellView.phoneBookHalfPaneShellWidth(
            horizontalSizeClass: .regular,
            layoutMode: .phoneTwoPaneBook,
            totalWidth: 951
        )
        let portraitHalf = AdaptiveShellView.phoneBookHalfPaneShellWidth(
            horizontalSizeClass: .regular,
            layoutMode: .phoneTwoPaneBook,
            totalWidth: 669
        )
        let initialZeroHalf = AdaptiveShellView.phoneBookHalfPaneShellWidth(
            horizontalSizeClass: .regular,
            layoutMode: .phoneTwoPaneBook,
            totalWidth: 0
        )
        XCTAssertEqual(landscapeHalf, .wide, "Default 475pt half-pane on iPhone Duo must resolve to .wide, never .twoColumn")
        XCTAssertEqual(portraitHalf, .wide, "Default 334pt half-pane on iPhone Duo portrait must resolve to .wide, never .twoColumn")
        XCTAssertEqual(initialZeroHalf, .wide, "Initial 0pt layout pass on Duo half-pane must never flicker to .twoColumn")
    }

    func testIPhoneDuoUnfoldedFullSpanModeRendersSingleFullWidthNavigationStackAndTwoColumnShellWidth() throws {
        let prevCatalog = CatalogRoutes.spansBothPanesOnPhoneBook
        let prevMyExams = MyExamsRoutes.myExamsSpansBothPanesOnPhoneBook
        let prevHistory = MyExamsRoutes.historySpansBothPanesOnPhoneBook
        defer {
            CatalogRoutes.spansBothPanesOnPhoneBook = prevCatalog
            MyExamsRoutes.myExamsSpansBothPanesOnPhoneBook = prevMyExams
            MyExamsRoutes.historySpansBothPanesOnPhoneBook = prevHistory
        }

        CatalogRoutes.spansBothPanesOnPhoneBook = true
        MyExamsRoutes.myExamsSpansBothPanesOnPhoneBook = true
        MyExamsRoutes.historySpansBothPanesOnPhoneBook = true

        let suite = "vn.unicom.thi360.tests.layout.duo.fullspan.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }

        let preferences = AppPreferences(userDefaults: defaults)
        let router = AppRouter(initialDestination: .myExams)
        let rootView = AppRootView(router: router, preferences: preferences, idiomOverride: .phone)
        let controller = UIHostingController(rootView: rootView)
        controller.traitOverrides.horizontalSizeClass = .regular

        let window: UIWindow
        if let activeScene = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).first {
            window = UIWindow(windowScene: activeScene)
            window.frame = CGRect(origin: .zero, size: CGSize(width: 951, height: 669))
        } else {
            window = UIWindow(frame: CGRect(origin: .zero, size: CGSize(width: 951, height: 669)))
        }
        window.rootViewController = controller
        window.makeKeyAndVisible()
        pumpLayout(window: window)
        defer { window.isHidden = true }

        let hostSize = controller.view.bounds.size
        let navControllers = findViewControllers(ofType: UINavigationController.self, in: controller)
        XCTAssertEqual(navControllers.count, 1, "Full-span Duo destination must render a single full-width NavigationStack")
        let fullWidth = try XCTUnwrap(navControllers.first?.view.bounds.width)
        XCTAssertEqual(fullWidth, hostSize.width, accuracy: 2.0, "Full-span Duo NavigationStack must span full host width")
        XCTAssertGreaterThanOrEqual(fullWidth, 600)
        XCTAssertEqual(
            ShellWidth.resolve(horizontalSizeClass: .regular, layoutMode: .phoneTwoPaneBook, availableWidth: fullWidth),
            .twoColumn
        )

        let rootImage = renderWindow(window, size: hostSize)
        let barY = max(0, hostSize.height - 64)
        let bottomLeadingBarRect = CGRect(x: 20, y: barY, width: 100, height: 50)
        let bottomTrailingEmptyRect = CGRect(x: hostSize.width - 120, y: barY, width: 100, height: 50)
        XCTAssertTrue(
            hasNonUniformPixels(in: rootImage, rect: bottomLeadingBarRect),
            "Full-span Duo root must render duoLeftTabBar at bottom-leading"
        )
        XCTAssertFalse(
            hasNonUniformPixels(in: rootImage, rect: bottomTrailingEmptyRect),
            "Full-span Duo root must keep bottom-trailing clear of duoLeftTabBar"
        )

        let prevAnimationsEnabled = UIView.areAnimationsEnabled
        UIView.setAnimationsEnabled(false)
        defer { UIView.setAnimationsEnabled(prevAnimationsEnabled) }

        router.openPrepare(entitlementId: "ent-duo-full")
        pumpLayout(window: window, duration: 0.15)
        XCTAssertEqual(router.path(for: .myExams), [.prepare(entitlementId: "ent-duo-full")])
        XCTAssertEqual(
            navControllers.first?.viewControllers.count,
            2,
            "Pushed route on full-span Duo must push onto the single full-width NavigationStack"
        )
        let pushedWidth = try XCTUnwrap(navControllers.first?.topViewController?.view.bounds.width)
        XCTAssertEqual(
            pushedWidth,
            hostSize.width,
            accuracy: 2.0,
            "Pushed full-span route must span the full host width"
        )
        let pushedImage = renderWindow(window, size: hostSize)
        XCTAssertNotEqual(
            rootImage.pngData(),
            pushedImage.pngData(),
            "Pushing route on full-span Duo must replace root and hide duoLeftTabBar"
        )

        router.back()
        pumpLayout(window: window, duration: 0.15)
        XCTAssertEqual(router.path(for: .myExams), [])
        XCTAssertEqual(navControllers.first?.viewControllers.count, 1)
        let restoredImage = renderWindow(window, size: hostSize)
        XCTAssertTrue(
            hasNonUniformPixels(in: restoredImage, rect: bottomLeadingBarRect),
            "Returning to full-span Duo root must restore duoLeftTabBar"
        )
        XCTAssertFalse(
            hasNonUniformPixels(in: restoredImage, rect: bottomTrailingEmptyRect),
            "Returning to full-span Duo root must restore trailing bottom clearance"
        )

        router.switchTab(.account)
        pumpLayout(window: window)
        XCTAssertEqual(
            findViewControllers(ofType: UINavigationController.self, in: controller).count,
            2,
            "Account tab must keep two-pane Duo split even when portal tabs opt into full-span"
        )
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

    private func pumpLayout(window: UIWindow, duration: TimeInterval = 0.05) {
        window.setNeedsLayout()
        window.layoutIfNeeded()
        RunLoop.main.run(until: Date().addingTimeInterval(duration))
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

    private func renderWindow(_ window: UIWindow, size: CGSize) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1.0
        format.opaque = true
        let renderer = UIGraphicsImageRenderer(size: size, format: format)
        return renderer.image { context in
            let drew = window.drawHierarchy(in: CGRect(origin: .zero, size: size), afterScreenUpdates: true)
            if !drew {
                window.layer.render(in: context.cgContext)
            }
        }
    }

    private func hasNonUniformPixels(in image: UIImage, rect: CGRect) -> Bool {
        guard let cgImage = image.cgImage,
              let data = cgImage.dataProvider?.data,
              let bytes = CFDataGetBytePtr(data)
        else {
            return false
        }
        let scale = image.scale
        let minX = max(0, Int(rect.minX * scale))
        let maxX = min(cgImage.width - 1, Int(rect.maxX * scale))
        let minY = max(0, Int(rect.minY * scale))
        let maxY = min(cgImage.height - 1, Int(rect.maxY * scale))
        guard minX < maxX, minY < maxY else { return false }

        let bytesPerPixel = max(4, cgImage.bitsPerPixel / 8)
        let bytesPerRow = cgImage.bytesPerRow
        let baseOffset = minY * bytesPerRow + minX * bytesPerPixel
        let baseR = Int(bytes[baseOffset])
        let baseG = Int(bytes[baseOffset + 1])
        let baseB = Int(bytes[baseOffset + 2])

        var y = minY
        while y <= maxY {
            var x = minX
            while x <= maxX {
                let offset = y * bytesPerRow + x * bytesPerPixel
                if abs(Int(bytes[offset]) - baseR) > 6
                    || abs(Int(bytes[offset + 1]) - baseG) > 6
                    || abs(Int(bytes[offset + 2]) - baseB) > 6 {
                    return true
                }
                x += 4
            }
            y += 4
        }
        return false
    }
}
