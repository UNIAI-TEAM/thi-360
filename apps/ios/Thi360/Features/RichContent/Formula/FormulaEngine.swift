import CoreGraphics
import Foundation
import SwaTex
import SwaTexRender
import UIKit

enum FormulaRender: @unchecked Sendable {
    case rendered(image: UIImage, width: CGFloat, ascent: CGFloat, descent: CGFloat)
    case unsupported(source: String)

    var isRendered: Bool {
        if case .rendered = self { return true }
        return false
    }
}

protocol FormulaEngine: Sendable {
    func render(_ formula: Formula, pointSize: CGFloat, color: UIColor) -> FormulaRender
}

final class SwaTexFormulaEngine: FormulaEngine, @unchecked Sendable {
    static let shared = SwaTexFormulaEngine()
    static let defaultTimeoutSeconds: TimeInterval = 1.5

    private let timeoutSeconds: TimeInterval
    private let displayScale: CGFloat
    private let useCache: Bool
    private let renderQueue = DispatchQueue(
        label: "vn.unicom.thi360.richcontent.swatex",
        qos: .userInitiated,
        attributes: .concurrent
    )
    private let cache = NSCache<NSString, CachedFormulaBox>()

    private final class CachedFormulaBox: NSObject {
        let render: FormulaRender
        init(_ render: FormulaRender) {
            self.render = render
        }
    }

    init(
        timeoutSeconds: TimeInterval = SwaTexFormulaEngine.defaultTimeoutSeconds,
        displayScale: CGFloat = 2.0,
        useCache: Bool = true
    ) {
        self.timeoutSeconds = timeoutSeconds
        self.displayScale = max(displayScale, 1.0)
        self.useCache = useCache
        self.cache.countLimit = 512
    }

    func render(_ formula: Formula, pointSize: CGFloat, color: UIColor) -> FormulaRender {
        let clampedPointSize = max(pointSize, 1.0)
        let swaColor = Self.swaTexColor(from: color)
        let cacheKey: NSString? = useCache ? Self.makeCacheKey(
            formula: formula,
            pointSize: clampedPointSize,
            color: swaColor,
            scale: displayScale
        ) : nil

        if let cacheKey, let cached = cache.object(forKey: cacheKey) {
            return cached.render
        }

        let result: FormulaRender
        if timeoutSeconds > 0 {
            result = renderWithTimeout(
                formula: formula,
                pointSize: clampedPointSize,
                swaColor: swaColor,
                timeout: timeoutSeconds
            )
        } else {
            result = renderSynchronously(
                formula: formula,
                pointSize: clampedPointSize,
                swaColor: swaColor
            )
        }

        if let cacheKey {
            cache.setObject(CachedFormulaBox(result), forKey: cacheKey)
        }
        return result
    }

    func clearCache() {
        cache.removeAllObjects()
    }

    private func renderWithTimeout(
        formula: Formula,
        pointSize: CGFloat,
        swaColor: SwaTex.Color,
        timeout: TimeInterval
    ) -> FormulaRender {
        let semaphore = DispatchSemaphore(value: 0)
        let box = LockedRenderResult()
        let scale = displayScale

        renderQueue.async {
            let outcome = Self.executeRender(
                formula: formula,
                pointSize: pointSize,
                swaColor: swaColor,
                displayScale: scale
            )
            box.set(outcome)
            semaphore.signal()
        }

        let waitResult = semaphore.wait(timeout: .now() + timeout)
        if waitResult == .timedOut {
            return .unsupported(source: formula.delimitedSource)
        }
        return box.get() ?? .unsupported(source: formula.delimitedSource)
    }

    private func renderSynchronously(
        formula: Formula,
        pointSize: CGFloat,
        swaColor: SwaTex.Color
    ) -> FormulaRender {
        Self.executeRender(
            formula: formula,
            pointSize: pointSize,
            swaColor: swaColor,
            displayScale: displayScale
        )
    }

    private static let katexForbiddenCommandsRegex = try! NSRegularExpression(
        pattern: #"\\(?:DeclareMathOperator|hfill)(?![A-Za-z])"#
    )
    private static let katexDisplayOnlyRegex = try! NSRegularExpression(
        pattern: #"\\(?:tag\*?(?![A-Za-z])|begin\{(?:align\*?|equation\*?|gather\*?|alignat\*?|CD)\})"#
    )

    private static func isRejectedByKaTeXContract(_ formula: Formula) -> Bool {
        let trimmed = formula.source.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.hasPrefix("$") {
            return true
        }
        let range = NSRange(location: 0, length: (trimmed as NSString).length)
        if katexForbiddenCommandsRegex.firstMatch(in: trimmed, range: range) != nil {
            return true
        }
        if formula.display == .inline,
           katexDisplayOnlyRegex.firstMatch(in: trimmed, range: range) != nil
        {
            return true
        }
        return false
    }

    private static func executeRender(
        formula: Formula,
        pointSize: CGFloat,
        swaColor: SwaTex.Color,
        displayScale: CGFloat
    ) -> FormulaRender {
        guard !isRejectedByKaTeXContract(formula) else {
            return .unsupported(source: formula.delimitedSource)
        }
        let style: MathStyle = (formula.display == .block) ? .display : .text
        do {
            let list = try SwaTexEngine.displayList(
                for: formula.source,
                style: style,
                color: swaColor
            )
            guard !list.truncated else {
                return .unsupported(source: formula.delimitedSource)
            }
            let options = RenderOptions(fontSize: pointSize, padding: 0, backgroundColor: nil)
            let metrics = DisplayListRenderer.metrics(for: list, options: options)
            guard let cgImage = ImageRenderer.image(
                for: list,
                options: options,
                displayScale: displayScale
            ) else {
                return .unsupported(source: formula.delimitedSource)
            }

            let image = UIImage(cgImage: cgImage, scale: displayScale, orientation: .up)
            let width = CGFloat(cgImage.width) / displayScale
            let imageHeight = CGFloat(cgImage.height) / displayScale
            // DisplayListRenderer flips around metrics.height from CG bottom-left origin,
            // so the math baseline sits exactly `descent` points above the image bottom edge.
            let descent = max(metrics.height - metrics.baseline, 0)
            let ascent = max(imageHeight - descent, 0)
            return .rendered(image: image, width: width, ascent: ascent, descent: descent)
        } catch {
            return .unsupported(source: formula.delimitedSource)
        }
    }

    private static func swaTexColor(from uiColor: UIColor) -> SwaTex.Color {
        var r: CGFloat = 0
        var g: CGFloat = 0
        var b: CGFloat = 0
        var a: CGFloat = 1
        if uiColor.getRed(&r, green: &g, blue: &b, alpha: &a) {
            return SwaTex.Color(r: Float(r), g: Float(g), b: Float(b), a: Float(a))
        }
        if let srgb = CGColorSpace(name: CGColorSpace.sRGB),
           let converted = uiColor.cgColor.converted(to: srgb, intent: .defaultIntent, options: nil),
           let c = converted.components
        {
            if c.count >= 4 {
                return SwaTex.Color(r: Float(c[0]), g: Float(c[1]), b: Float(c[2]), a: Float(c[3]))
            }
            if c.count == 2 {
                return SwaTex.Color(r: Float(c[0]), g: Float(c[0]), b: Float(c[0]), a: Float(c[1]))
            }
        }
        return .black
    }

    private static func makeCacheKey(
        formula: Formula,
        pointSize: CGFloat,
        color: SwaTex.Color,
        scale: CGFloat
    ) -> NSString {
        let sizeKey = Int((pointSize * 10).rounded())
        let r = Int((color.r * 255).rounded())
        let g = Int((color.g * 255).rounded())
        let b = Int((color.b * 255).rounded())
        let a = Int((color.a * 255).rounded())
        let scaleKey = Int((scale * 10).rounded())
        return "\(formula.display.rawValue)|\(sizeKey)|\(scaleKey)|\(r),\(g),\(b),\(a)|\(formula.source)" as NSString
    }

    private final class LockedRenderResult: @unchecked Sendable {
        private let lock = NSLock()
        private var value: FormulaRender?

        func set(_ newValue: FormulaRender) {
            lock.lock()
            value = newValue
            lock.unlock()
        }

        func get() -> FormulaRender? {
            lock.lock()
            defer { lock.unlock() }
            return value
        }
    }
}

final class FaultInjectingFormulaEngine: FormulaEngine, @unchecked Sendable {
    enum Behavior: Sendable {
        case delegate
        case throwError
        case delay(TimeInterval)
    }

    private let base: any FormulaEngine
    private let timeoutSeconds: TimeInterval
    private let selector: @Sendable (Formula) -> Behavior
    private let queue = DispatchQueue(
        label: "vn.unicom.thi360.richcontent.faultengine",
        qos: .userInitiated,
        attributes: .concurrent
    )

    init(
        base: any FormulaEngine = SwaTexFormulaEngine.shared,
        timeoutSeconds: TimeInterval = 0.15,
        selector: @escaping @Sendable (Formula) -> Behavior
    ) {
        self.base = base
        self.timeoutSeconds = timeoutSeconds
        self.selector = selector
    }

    func render(_ formula: Formula, pointSize: CGFloat, color: UIColor) -> FormulaRender {
        switch selector(formula) {
        case .delegate:
            return base.render(formula, pointSize: pointSize, color: color)
        case .throwError:
            return .unsupported(source: formula.delimitedSource)
        case let .delay(duration):
            let semaphore = DispatchSemaphore(value: 0)
            let box = ResultBox()
            let underlying = base
            queue.async {
                Thread.sleep(forTimeInterval: duration)
                box.set(underlying.render(formula, pointSize: pointSize, color: color))
                semaphore.signal()
            }
            if semaphore.wait(timeout: .now() + timeoutSeconds) == .timedOut {
                return .unsupported(source: formula.delimitedSource)
            }
            return box.get() ?? .unsupported(source: formula.delimitedSource)
        }
    }

    private final class ResultBox: @unchecked Sendable {
        private let lock = NSLock()
        private var value: FormulaRender?
        func set(_ v: FormulaRender) { lock.lock(); value = v; lock.unlock() }
        func get() -> FormulaRender? { lock.lock(); defer { lock.unlock() }; return value }
    }
}
