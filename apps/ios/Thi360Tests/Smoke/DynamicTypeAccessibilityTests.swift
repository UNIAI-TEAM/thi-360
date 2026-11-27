import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class DynamicTypeAccessibilityTests: XCTestCase {
    private let phoneSize = CGSize(width: 390, height: 844)
    private let minTouchTargetPt: CGFloat = 44
    private let touchTargetTolerancePt: CGFloat = 0.5
    private let horizontalOverflowTolerancePt: CGFloat = 1.0

    private struct DynamicTypeScale {
        let label: String
        let snapshotSuffix: String?
        let uiCategory: UIContentSizeCategory
        let swiftUISize: DynamicTypeSize
    }

    private let requiredScales: [DynamicTypeScale] = [
        DynamicTypeScale(
            label: "135%",
            snapshotSuffix: "dt130",
            uiCategory: .extraExtraExtraLarge,
            swiftUISize: .xxxLarge
        ),
        DynamicTypeScale(
            label: "194%",
            snapshotSuffix: nil,
            uiCategory: .accessibilityMedium,
            swiftUISize: .accessibility1
        ),
        DynamicTypeScale(
            label: "235%",
            snapshotSuffix: "dt200",
            uiCategory: .accessibilityLarge,
            swiftUISize: .accessibility2
        )
    ]

    private struct InspectedNode {
        let identifier: String
        let label: String
        let traits: UIAccessibilityTraits
        let frameInWindow: CGRect
        let isSwitchControl: Bool
    }

    func testLoginDynamicTypeAndAccessibility() throws {
        for scale in requiredScales {
            let (sessionStore, _, _, preferences) = SessionTestFactory.makeStack { _ in
                MockClientTransport.emptyResponse(statusCode: 500)
            }
            let viewModel = LoginViewModel(sessionStore: sessionStore)
            viewModel.identifier = "minhanh.tran"

            let view = LoginView(viewModel: viewModel, preferences: preferences, idiom: .phone)
                .environment(\.brandPalette, .defaultLight)
                .environment(\.dynamicTypeSize, scale.swiftUISize)

            try verifyScreen(
                name: "Login",
                snapshotPrefix: "qa-login",
                scale: scale,
                view: view
            )
        }
    }

    func testRegisterDynamicTypeAndAccessibility() throws {
        for scale in requiredScales {
            let (sessionStore, _, _, preferences) = SessionTestFactory.makeStack { _ in
                MockClientTransport.emptyResponse(statusCode: 500)
            }
            let viewModel = RegisterViewModel(sessionStore: sessionStore)
            viewModel.fullName = "Trần Minh Anh"
            viewModel.username = "minhanh.tran"
            viewModel.email = "minhanh@example.com"
            viewModel.password = "QaSmoke1!a"
            viewModel.acceptTerms = true

            let view = RegisterView(viewModel: viewModel, preferences: preferences, onBackToLogin: {})
                .environment(\.brandPalette, .defaultLight)
                .environment(\.dynamicTypeSize, scale.swiftUISize)

            try verifyScreen(
                name: "Register",
                snapshotPrefix: "qa-register",
                scale: scale,
                view: view
            )
        }
    }

    func testLockDynamicTypeAndAccessibility() throws {
        for scale in requiredScales {
            let stack = AccountTestFactory.makeStack(
                initialTokens: AccountTestFactory.sampleTokens(username: "minhanh.tran"),
                capability: .faceIDAvailable,
                initialEnrolled: true
            ) { _ in
                MockClientTransport.emptyResponse(statusCode: 200)
            }
            defer {
                UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName)
            }

            let viewModel = LockViewModel(
                sessionStore: stack.sessionStore,
                biometricManager: stack.biometricManager
            )
            let view = LockView(
                viewModel: viewModel,
                preferences: stack.preferences,
                displayName: "Trần Minh Anh"
            )
            .environment(\.brandPalette, .defaultLight)
            .environment(\.dynamicTypeSize, scale.swiftUISize)

            try verifyScreen(
                name: "Lock",
                snapshotPrefix: "qa-lock",
                scale: scale,
                view: view,
                requireScrollView: true
            )
        }
    }

    func testLockWithErrorBannerDynamicTypeAndAccessibility() async throws {
        for scale in requiredScales {
            let stack = AccountTestFactory.makeStack(
                initialTokens: AccountTestFactory.sampleTokens(username: "minhanh.tran"),
                capability: .faceIDAvailable,
                initialEnrolled: true
            ) { _ in
                MockClientTransport.emptyResponse(statusCode: 200)
            }
            defer {
                UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName)
            }

            stack.secretStore.nextReadOutcomeOverride = .lockout
            let viewModel = LockViewModel(
                sessionStore: stack.sessionStore,
                biometricManager: stack.biometricManager
            )
            await viewModel.unlockWithBiometrics(locale: .vi)
            XCTAssertNotNil(viewModel.errorMessage(locale: .vi), "LockViewModel must expose lockout error banner")

            let view = LockView(
                viewModel: viewModel,
                preferences: stack.preferences,
                displayName: "Trần Minh Anh"
            )
            .environment(\.brandPalette, .defaultLight)
            .environment(\.dynamicTypeSize, scale.swiftUISize)

            try verifyScreen(
                name: "LockWithError",
                snapshotPrefix: "qa-lock-error",
                scale: scale,
                view: view,
                requireScrollView: true,
                captureDt130: false
            )
        }
    }

    func testBiometricEnableDynamicTypeAndAccessibility() throws {
        for scale in requiredScales {
            let stack = AccountTestFactory.makeStack(
                initialTokens: AccountTestFactory.sampleTokens(username: "minhanh.tran"),
                capability: .faceIDAvailable,
                initialEnrolled: false
            ) { _ in
                MockClientTransport.emptyResponse(statusCode: 200)
            }
            defer {
                UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName)
            }

            let view = BiometricEnableView(
                biometricManager: stack.biometricManager,
                preferences: stack.preferences,
                sessionStore: stack.sessionStore
            )
            .environment(\.brandPalette, .defaultLight)
            .environment(\.dynamicTypeSize, scale.swiftUISize)

            try verifyScreen(
                name: "BiometricEnable",
                snapshotPrefix: "qa-biometric-enable",
                scale: scale,
                view: view,
                requireScrollView: true,
                captureDt130: false
            )
        }
    }

    func testHomeDynamicTypeAndAccessibility() throws {
        let filtersData = try ContractFixtureLoader.loadApiFixture("storefront-filters-200.json")
        let productsData = try ContractFixtureLoader.loadApiFixture("storefront-products-list-200.json")
        let accessData = try ContractFixtureLoader.loadApiFixture("exam-access-query-200.json")

        for scale in requiredScales {
            let stack = AccountTestFactory.makeStack(
                initialTokens: AccountTestFactory.sampleTokens(username: "minhanh.tran"),
                capability: .faceIDAvailable,
                initialEnrolled: false
            ) { request in
                let path = request.path.split(separator: "?", maxSplits: 1).first.map(String.init) ?? request.path
                if path == "/api/v1/public/storefront/filters" {
                    return MockClientTransport.jsonResponse(statusCode: 200, data: filtersData)
                }
                if path == "/api/v1/public/storefront/products" {
                    return MockClientTransport.jsonResponse(statusCode: 200, data: productsData)
                }
                if path == "/api/v1/me/exam-access/query" {
                    return MockClientTransport.jsonResponse(statusCode: 200, data: accessData)
                }
                return MockClientTransport.emptyResponse(statusCode: 200)
            }
            defer {
                UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName)
            }

            let router = AppRouter(initialDestination: .home)
            defer { CatalogFeedRegistry.shared.clear(for: router) }

            let view = AppRootView(
                router: router,
                preferences: stack.preferences,
                sessionStore: stack.sessionStore,
                biometricManager: stack.biometricManager,
                idiomOverride: .phone
            )
            .environment(\.dynamicTypeSize, scale.swiftUISize)

            try verifyScreen(
                name: "Home",
                snapshotPrefix: "qa-home",
                scale: scale,
                view: view
            )
        }
    }

    func testAccountDynamicTypeAndAccessibility() async throws {
        let profileData = try ContractFixtureLoader.loadApiFixture("me-profile-200.json")

        for scale in requiredScales {
            let stack = AccountTestFactory.makeStack(
                initialTokens: AccountTestFactory.sampleTokens(username: "minhanh.tran"),
                capability: .faceIDAvailable,
                initialEnrolled: true
            ) { _ in
                MockClientTransport.jsonResponse(statusCode: 200, data: profileData)
            }
            defer {
                UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName)
            }
            stack.sessionStore.unlockSession()

            let router = AppRouter(initialDestination: .account)
            let accountVM = router.accountViewModel(
                sessionStore: stack.sessionStore,
                biometricManager: stack.biometricManager
            )
            await accountVM.loadProfile()

            let view = AppRootView(
                router: router,
                preferences: stack.preferences,
                sessionStore: stack.sessionStore,
                biometricManager: stack.biometricManager,
                idiomOverride: .phone
            )
            .environment(\.dynamicTypeSize, scale.swiftUISize)

            try verifyScreen(
                name: "Account",
                snapshotPrefix: "qa-account",
                scale: scale,
                view: view
            )
        }
    }

    private func verifyScreen<Content: View>(
        name: String,
        snapshotPrefix: String,
        scale: DynamicTypeScale,
        view: Content,
        requireScrollView: Bool = false,
        captureDt130: Bool = true
    ) throws {
        let (window, controller) = host(view, size: phoneSize, uiCategory: scale.uiCategory)
        defer { window.isHidden = true }

        let contextLabel = "\(name)@\(scale.label)"
        let nodes = collectInspectedNodes(in: controller.view, window: window)

        let buttons = nodes.filter { $0.traits.contains(.button) && !$0.isSwitchControl }
        for button in buttons {
            XCTAssertFalse(
                button.label.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                "\(contextLabel) button '\(button.identifier)' must have a non-empty VoiceOver accessibilityLabel"
            )
            guard button.frameInWindow.width > 0 && button.frameInWindow.height > 0 else { continue }
            XCTAssertGreaterThanOrEqual(
                button.frameInWindow.width,
                minTouchTargetPt - touchTargetTolerancePt,
                "\(contextLabel) button '\(button.identifier)' width \(button.frameInWindow.width)pt < \(minTouchTargetPt)pt"
            )
            XCTAssertGreaterThanOrEqual(
                button.frameInWindow.height,
                minTouchTargetPt - touchTargetTolerancePt,
                "\(contextLabel) button '\(button.identifier)' height \(button.frameInWindow.height)pt < \(minTouchTargetPt)pt"
            )
        }

        for node in nodes where node.frameInWindow.width > 0 && node.frameInWindow.height > 0 {
            XCTAssertGreaterThanOrEqual(
                node.frameInWindow.minX,
                -horizontalOverflowTolerancePt,
                "\(contextLabel) element '\(node.identifier)' overflows left edge (minX=\(node.frameInWindow.minX))"
            )
            XCTAssertLessThanOrEqual(
                node.frameInWindow.maxX,
                phoneSize.width + horizontalOverflowTolerancePt,
                "\(contextLabel) element '\(node.identifier)' overflows \(phoneSize.width)pt width (maxX=\(node.frameInWindow.maxX))"
            )
        }

        let scrollViews = RecoveryViewTestHelper.findViews(ofType: UIScrollView.self, in: controller.view)
        if requireScrollView {
            XCTAssertFalse(
                scrollViews.isEmpty,
                "\(contextLabel) must host a UIScrollView so 200% Dynamic Type content remains vertically scrollable"
            )
        }
        if scrollViews.isEmpty {
            let fittingSize = controller.sizeThatFits(in: CGSize(width: phoneSize.width, height: CGFloat.greatestFiniteMagnitude))
            XCTAssertLessThanOrEqual(
                fittingSize.height,
                phoneSize.height + 1.0,
                "\(contextLabel) has no UIScrollView and its fitting height (\(fittingSize.height)pt) overflows \(phoneSize.height)pt viewport"
            )
        } else {
            for scrollView in scrollViews where scrollView.contentSize.width > 0 {
                XCTAssertLessThanOrEqual(
                    scrollView.contentSize.width,
                    phoneSize.width + horizontalOverflowTolerancePt,
                    "\(contextLabel) UIScrollView contentSize.width (\(scrollView.contentSize.width)pt) overflows \(phoneSize.width)pt"
                )
            }
        }

        if let suffix = scale.snapshotSuffix, captureDt130 || suffix == "dt200" {
            try captureQASnapshot(
                window: window,
                size: phoneSize,
                name: "\(snapshotPrefix)-\(suffix)"
            )
        }
    }

    private func host<Content: View>(
        _ view: Content,
        size: CGSize,
        uiCategory: UIContentSizeCategory
    ) -> (UIWindow, UIHostingController<Content>) {
        let controller = UIHostingController(rootView: view)
        controller.traitOverrides.horizontalSizeClass = .compact
        controller.traitOverrides.userInterfaceStyle = .light
        controller.traitOverrides.preferredContentSizeCategory = uiCategory

        let window: UIWindow
        if let scene = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).first {
            window = UIWindow(windowScene: scene)
            window.frame = CGRect(origin: .zero, size: size)
        } else {
            window = UIWindow(frame: CGRect(origin: .zero, size: size))
        }
        window.rootViewController = controller
        window.overrideUserInterfaceStyle = .light
        window.makeKeyAndVisible()
        window.setNeedsLayout()
        window.layoutIfNeeded()
        RunLoop.main.run(until: Date().addingTimeInterval(0.08))
        window.layoutIfNeeded()
        return (window, controller)
    }

    private func collectInspectedNodes(in root: UIView, window: UIWindow) -> [InspectedNode] {
        var collected: [InspectedNode] = []
        var visited = Set<ObjectIdentifier>()

        func visitObject(_ object: Any) {
            guard let nsObject = object as? NSObject else { return }
            let oid = ObjectIdentifier(nsObject)
            guard visited.insert(oid).inserted else { return }

            if let view = nsObject as? UIView {
                if view.isHidden || view.alpha < 0.01 {
                    return
                }
                if view.isAccessibilityElement {
                    let frame = view.convert(view.bounds, to: window)
                    let identifier = view.accessibilityIdentifier ?? String(describing: type(of: view))
                    collected.append(
                        InspectedNode(
                            identifier: identifier,
                            label: view.accessibilityLabel ?? "",
                            traits: view.accessibilityTraits,
                            frameInWindow: frame,
                            isSwitchControl: (view is UISwitch) || identifier == "account.biometricToggle"
                        )
                    )
                    return
                }
                if let elements = view.accessibilityElements, !elements.isEmpty {
                    for element in elements {
                        visitObject(element)
                    }
                }
                for subview in view.subviews {
                    visitObject(subview)
                }
                return
            }

            if nsObject.isAccessibilityElement {
                let rawFrame = nsObject.accessibilityFrame
                let frame = (!rawFrame.isNull && !rawFrame.isInfinite && !rawFrame.isEmpty)
                    ? window.convert(rawFrame, from: nil)
                    : .zero
                let identifier = (nsObject as? UIAccessibilityIdentification)?.accessibilityIdentifier
                    ?? nsObject.accessibilityLabel
                    ?? String(describing: type(of: nsObject))
                collected.append(
                    InspectedNode(
                        identifier: identifier,
                        label: nsObject.accessibilityLabel ?? "",
                        traits: nsObject.accessibilityTraits,
                        frameInWindow: frame,
                        isSwitchControl: identifier == "account.biometricToggle"
                    )
                )
            }
            if let nested = nsObject.accessibilityElements, !nested.isEmpty {
                for element in nested {
                    visitObject(element)
                }
            }
        }

        visitObject(root)
        return collected
    }

    private func captureQASnapshot(window: UIWindow, size: CGSize, name: String) throws {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 2.0
        format.opaque = true
        let renderer = UIGraphicsImageRenderer(size: size, format: format)
        let image = renderer.image { context in
            let drew = window.drawHierarchy(in: CGRect(origin: .zero, size: size), afterScreenUpdates: true)
            if !drew {
                window.layer.render(in: context.cgContext)
            }
        }
        let png = try XCTUnwrap(image.pngData())
        XCTAssertGreaterThan(png.count, 4096, "Snapshot \(name) unexpectedly small")

        let attachment = XCTAttachment(image: image)
        attachment.name = "\(name).png"
        attachment.lifetime = .keepAlways
        add(attachment)

        let env = ProcessInfo.processInfo.environment
        let outputDir = [env["TEST_RUNNER_THI360_SNAPSHOT_DIR"], env["THI360_SNAPSHOT_DIR"]]
            .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .first(where: { !$0.isEmpty })

        if let outputDir {
            let dirURL = URL(fileURLWithPath: outputDir, isDirectory: true)
            try FileManager.default.createDirectory(at: dirURL, withIntermediateDirectories: true)
            try png.write(to: dirURL.appendingPathComponent("\(name).png"))
        }
    }
}
