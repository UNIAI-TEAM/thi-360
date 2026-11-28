import Foundation
import ImageIO
import SwiftUI
import UIKit
import UniformTypeIdentifiers
import XCTest
@testable import Thi360

final class FakeCameraAccess: CameraAccessProviding, @unchecked Sendable {
    private let lock = NSLock()
    private var _isAvailable: Bool
    private var _authorization: CameraAuthorizationStatus
    private var _requestResult: Bool
    private var _requestCallCount: Int = 0

    init(
        isAvailable: Bool = true,
        authorization: CameraAuthorizationStatus = .granted,
        requestResult: Bool = true
    ) {
        self._isAvailable = isAvailable
        self._authorization = authorization
        self._requestResult = requestResult
    }

    var isAvailable: Bool {
        get { lock.lock(); defer { lock.unlock() }; return _isAvailable }
        set { lock.lock(); _isAvailable = newValue; lock.unlock() }
    }

    var authorization: CameraAuthorizationStatus {
        get { lock.lock(); defer { lock.unlock() }; return _authorization }
        set { lock.lock(); _authorization = newValue; lock.unlock() }
    }

    var requestCallCount: Int {
        lock.lock()
        defer { lock.unlock() }
        return _requestCallCount
    }

    func request() async -> Bool {
        lock.lock()
        _requestCallCount += 1
        let granted = _requestResult
        _authorization = granted ? .granted : .denied
        lock.unlock()
        return granted
    }
}

enum ProfileFixtureHelper {
    static func mutateJSON(
        _ fixtureName: String,
        mutate: (inout [String: Any]) -> Void
    ) throws -> Data {
        let baseData = try ContractFixtureLoader.loadApiFixture(fixtureName)
        var dict = try XCTUnwrap(JSONSerialization.jsonObject(with: baseData) as? [String: Any])
        mutate(&dict)
        return try JSONSerialization.data(withJSONObject: dict)
    }

    static func makeSamplePNG(
        width: Int = 32,
        height: Int = 32,
        color: UIColor = .systemGreen
    ) throws -> Data {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1.0
        format.opaque = true
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: width, height: height), format: format)
        let image = renderer.image { ctx in
            color.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: width, height: height))
        }
        return try XCTUnwrap(image.pngData())
    }

    static func makeSampleJPEG(
        width: Int = 64,
        height: Int = 48,
        color: UIColor = .systemTeal,
        quality: CGFloat = 0.85
    ) throws -> Data {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1.0
        format.opaque = true
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: width, height: height), format: format)
        let image = renderer.image { ctx in
            color.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: width, height: height))
        }
        return try XCTUnwrap(image.jpegData(compressionQuality: quality))
    }

    static func makeExifRotatedJPEG(
        rawWidth: Int = 200,
        rawHeight: Int = 100,
        exifOrientation: Int = 6
    ) throws -> Data {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1.0
        format.opaque = true
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: rawWidth, height: rawHeight), format: format)
        let image = renderer.image { ctx in
            UIColor.systemOrange.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: rawWidth / 2, height: rawHeight))
            UIColor.systemIndigo.setFill()
            ctx.fill(CGRect(x: rawWidth / 2, y: 0, width: rawWidth / 2, height: rawHeight))
        }
        guard let cgImage = image.cgImage else {
            throw NSError(domain: "ProfileFixtureHelper", code: -1)
        }
        let mutableData = NSMutableData()
        guard let dest = CGImageDestinationCreateWithData(mutableData, UTType.jpeg.identifier as CFString, 1, nil) else {
            throw NSError(domain: "ProfileFixtureHelper", code: -2)
        }
        let props: [CFString: Any] = [
            kCGImagePropertyOrientation: exifOrientation,
            kCGImageDestinationLossyCompressionQuality: 0.9
        ]
        CGImageDestinationAddImage(dest, cgImage, props as CFDictionary)
        guard CGImageDestinationFinalize(dest) else {
            throw NSError(domain: "ProfileFixtureHelper", code: -3)
        }
        return mutableData as Data
    }

    static func imagePixelDimensions(_ data: Data) -> (width: Int, height: Int, orientation: Int)? {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil),
              let props = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
              let w = props[kCGImagePropertyPixelWidth] as? Int,
              let h = props[kCGImagePropertyPixelHeight] as? Int
        else {
            return nil
        }
        let orientation = props[kCGImagePropertyOrientation] as? Int ?? 1
        return (w, h, orientation)
    }

    @MainActor
    static func pumpMainRunLoop(seconds: TimeInterval = 0.08) {
        RunLoop.main.run(until: Date().addingTimeInterval(seconds))
    }

    @MainActor
    static func waitUntil(
        timeout: TimeInterval = 5.0,
        condition: @MainActor () -> Bool
    ) async -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if condition() {
                return true
            }
            RunLoop.main.run(until: Date().addingTimeInterval(0.01))
            try? await Task.sleep(nanoseconds: 20_000_000)
        }
        return condition()
    }

    @MainActor
    static func findControllers<T: UIViewController>(
        ofType type: T.Type,
        in root: UIViewController
    ) -> [T] {
        var result: [T] = []
        if let typed = root as? T {
            result.append(typed)
        }
        for child in root.children {
            result.append(contentsOf: findControllers(ofType: type, in: child))
        }
        return result
    }

    @MainActor
    private static func visitAccessibilityChildren(of nsObject: NSObject, visit: (Any) -> Void) {
        if let elements = nsObject.accessibilityElements, !elements.isEmpty {
            for element in elements {
                visit(element)
            }
        }
        let count = nsObject.accessibilityElementCount()
        if count != NSNotFound && count > 0 {
            for index in 0..<count {
                if let child = nsObject.accessibilityElement(at: index) {
                    visit(child)
                }
            }
        }
        if let view = nsObject as? UIView {
            for subview in view.subviews {
                visit(subview)
            }
        }
    }

    @MainActor
    private static let inspectionSinks = NSMapTable<AnyObject, AccountInspectionSink>.weakToStrongObjects()

    @MainActor
    static func sink(for view: UIView) -> AccountInspectionSink? {
        if let direct = inspectionSinks.object(forKey: view) {
            return direct
        }
        if let window = view.window, let byWindow = inspectionSinks.object(forKey: window) {
            return byWindow
        }
        return nil
    }

    @MainActor
    static func host<Content: View>(
        _ view: Content,
        size: CGSize,
        sizeClass: UIUserInterfaceSizeClass,
        themeMode: ThemeMode = .light,
        contentSizeCategory: UIContentSizeCategory = .large
    ) -> (UIWindow, UIHostingController<AnyView>) {
        let sink = AccountInspectionSink()
        let wrapped = AnyView(view.environment(\.accountInspector, sink))
        let controller = UIHostingController(rootView: wrapped)
        controller.traitOverrides.horizontalSizeClass = sizeClass
        controller.traitOverrides.userInterfaceStyle = (themeMode == .dark) ? .dark : .light
        controller.traitOverrides.preferredContentSizeCategory = contentSizeCategory

        let container = UIViewController()
        container.view.backgroundColor = .clear
        container.traitOverrides.horizontalSizeClass = sizeClass
        container.traitOverrides.userInterfaceStyle = (themeMode == .dark) ? .dark : .light
        container.traitOverrides.preferredContentSizeCategory = contentSizeCategory
        container.addChild(controller)
        controller.view.translatesAutoresizingMaskIntoConstraints = false
        container.view.addSubview(controller.view)
        NSLayoutConstraint.activate([
            controller.view.leadingAnchor.constraint(equalTo: container.view.leadingAnchor),
            controller.view.topAnchor.constraint(equalTo: container.view.topAnchor),
            controller.view.widthAnchor.constraint(equalToConstant: size.width),
            controller.view.heightAnchor.constraint(equalToConstant: size.height)
        ])
        controller.didMove(toParent: container)

        let window: UIWindow
        if let scene = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).first {
            window = UIWindow(windowScene: scene)
            window.frame = CGRect(origin: .zero, size: size)
        } else {
            window = UIWindow(frame: CGRect(origin: .zero, size: size))
        }
        inspectionSinks.setObject(sink, forKey: window)
        inspectionSinks.setObject(sink, forKey: controller.view)
        window.rootViewController = container
        window.overrideUserInterfaceStyle = (themeMode == .dark) ? .dark : .light
        window.makeKeyAndVisible()
        window.setNeedsLayout()
        window.layoutIfNeeded()
        RunLoop.main.run(until: Date().addingTimeInterval(0.08))
        window.layoutIfNeeded()
        return (window, controller)
    }

    @MainActor
    static func teardown(_ window: UIWindow) {
        window.isHidden = true
        window.rootViewController = nil
        window.windowScene = nil
    }

    @MainActor
    static func setRootView<Content: View>(
        _ view: Content,
        on controller: UIHostingController<AnyView>
    ) {
        let sink = sink(for: controller.view)
        controller.rootView = AnyView(view.environment(\.accountInspector, sink))
        controller.view.setNeedsLayout()
        controller.view.layoutIfNeeded()
        RunLoop.main.run(until: Date().addingTimeInterval(0.02))
        controller.view.setNeedsLayout()
        controller.view.layoutIfNeeded()
    }

    @MainActor
    static func collectAccessibilityIdentifiers(in root: UIView) -> Set<String> {
        var result = sink(for: root)?.identifiers ?? Set<String>()
        var visited = Set<ObjectIdentifier>()

        func visit(_ object: Any) {
            guard let nsObject = object as? NSObject else { return }
            let oid = ObjectIdentifier(nsObject)
            guard visited.insert(oid).inserted else { return }

            if let view = nsObject as? UIView, view.isHidden || view.alpha < 0.01 {
                return
            }
            if let identified = nsObject as? UIAccessibilityIdentification,
               let id = identified.accessibilityIdentifier,
               !id.isEmpty
            {
                result.insert(id)
            }
            visitAccessibilityChildren(of: nsObject, visit: visit)
        }
        visit(root)
        return result
    }

    @MainActor
    static func collectAccessibilityLabels(in root: UIView) -> [String] {
        var result: [String] = sink(for: root)?.labels ?? []
        var visited = Set<ObjectIdentifier>()

        func visit(_ object: Any) {
            guard let nsObject = object as? NSObject else { return }
            let oid = ObjectIdentifier(nsObject)
            guard visited.insert(oid).inserted else { return }

            if let view = nsObject as? UIView, view.isHidden || view.alpha < 0.01 {
                return
            }
            if let label = nsObject.accessibilityLabel, !label.isEmpty {
                result.append(label)
            }
            if let value = nsObject.accessibilityValue, !value.isEmpty {
                result.append(value)
            }
            if let uiLabel = nsObject as? UILabel, let text = uiLabel.text, !text.isEmpty {
                result.append(text)
            }
            visitAccessibilityChildren(of: nsObject, visit: visit)
        }
        visit(root)
        return result
    }

    @MainActor
    static func findElementFrame(identifier: String, in root: UIView, window: UIWindow) -> CGRect? {
        if let probed = sink(for: root)?.frame(for: identifier) {
            return probed
        }
        var visited = Set<ObjectIdentifier>()
        var foundFrame: CGRect?

        func visit(_ object: Any) {
            guard foundFrame == nil, let nsObject = object as? NSObject else { return }
            let oid = ObjectIdentifier(nsObject)
            guard visited.insert(oid).inserted else { return }

            if let view = nsObject as? UIView, view.isHidden || view.alpha < 0.01 {
                return
            }
            if let identified = nsObject as? UIAccessibilityIdentification,
               identified.accessibilityIdentifier == identifier
            {
                if let view = nsObject as? UIView {
                    foundFrame = view.convert(view.bounds, to: window)
                    return
                }
                let raw = nsObject.accessibilityFrame
                if !raw.isNull && !raw.isInfinite && !raw.isEmpty {
                    foundFrame = window.convert(raw, from: nil)
                    return
                }
            }
            visitAccessibilityChildren(of: nsObject, visit: visit)
        }

        visit(root)
        return foundFrame
    }

    @MainActor
    static func hostWithDynamicType<Content: View>(
        _ view: Content,
        size: CGSize,
        sizeClass: UIUserInterfaceSizeClass,
        themeMode: ThemeMode = .light,
        contentSizeCategory: UIContentSizeCategory = .large
    ) -> (UIWindow, UIHostingController<Content>) {
        let controller = UIHostingController(rootView: view)
        controller.traitOverrides.horizontalSizeClass = sizeClass
        controller.traitOverrides.userInterfaceStyle = (themeMode == .dark) ? .dark : .light
        controller.traitOverrides.preferredContentSizeCategory = contentSizeCategory

        let window: UIWindow
        if let scene = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).first {
            window = UIWindow(windowScene: scene)
            window.frame = CGRect(origin: .zero, size: size)
        } else {
            window = UIWindow(frame: CGRect(origin: .zero, size: size))
        }
        window.rootViewController = controller
        window.overrideUserInterfaceStyle = (themeMode == .dark) ? .dark : .light
        window.makeKeyAndVisible()
        window.setNeedsLayout()
        window.layoutIfNeeded()
        RunLoop.main.run(until: Date().addingTimeInterval(0.08))
        window.layoutIfNeeded()
        return (window, controller)
    }
}
