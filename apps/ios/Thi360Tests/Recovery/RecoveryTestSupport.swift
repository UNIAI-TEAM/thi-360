import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

final class ManualClock: AppClock, @unchecked Sendable {
    private let lock = NSLock()
    private var current: Date

    init(start: Date = Date(timeIntervalSince1970: 1_764_000_000)) {
        self.current = start
    }

    var now: Date {
        lock.lock()
        defer { lock.unlock() }
        return current
    }

    func advance(by seconds: TimeInterval) {
        lock.lock()
        current = current.addingTimeInterval(seconds)
        lock.unlock()
    }
}

actor TestAsyncGate {
    private var entered = false
    private var released = false
    private var enteredWaiters: [CheckedContinuation<Void, Never>] = []
    private var releaseWaiters: [CheckedContinuation<Void, Never>] = []

    func enterAndWait() async {
        entered = true
        let waiters = enteredWaiters
        enteredWaiters.removeAll()
        waiters.forEach { $0.resume() }

        guard !released else { return }
        await withCheckedContinuation { continuation in
            releaseWaiters.append(continuation)
        }
    }

    func waitUntilEntered() async {
        guard !entered else { return }
        await withCheckedContinuation { continuation in
            enteredWaiters.append(continuation)
        }
    }

    func release() {
        released = true
        let waiters = releaseWaiters
        releaseWaiters.removeAll()
        waiters.forEach { $0.resume() }
    }
}

@MainActor
enum RecoveryViewTestHelper {
    static func host<Content: View>(
        _ view: Content,
        size: CGSize,
        sizeClass: UIUserInterfaceSizeClass,
        themeMode: ThemeMode = .light
    ) -> (UIWindow, UIHostingController<Content>) {
        let controller = UIHostingController(rootView: view)
        controller.traitOverrides.horizontalSizeClass = sizeClass
        controller.traitOverrides.userInterfaceStyle = (themeMode == .dark) ? .dark : .light

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
        RunLoop.main.run(until: Date().addingTimeInterval(0.05))
        window.layoutIfNeeded()
        return (window, controller)
    }

    static func findViews<T: UIView>(ofType type: T.Type, in root: UIView) -> [T] {
        var found: [T] = []
        if let typed = root as? T {
            found.append(typed)
        }
        for subview in root.subviews {
            found.append(contentsOf: findViews(ofType: type, in: subview))
        }
        return found
    }

    static func captureSnapshot(
        window: UIWindow,
        size: CGSize,
        name: String,
        in testCase: XCTestCase
    ) throws -> Data {
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
        testCase.add(attachment)

        if let outputDir = ProcessInfo.processInfo.environment["THI360_SNAPSHOT_DIR"], !outputDir.isEmpty {
            let dirURL = URL(fileURLWithPath: outputDir, isDirectory: true)
            try FileManager.default.createDirectory(at: dirURL, withIntermediateDirectories: true)
            try png.write(to: dirURL.appendingPathComponent("\(name).png"))
        }

        return png
    }
}
