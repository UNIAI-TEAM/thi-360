import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class QuestionSamplesSnapshotTests: XCTestCase {
    func testAll11QuestionSamplesSnapshotsLightAndDarkOnIPhone17() throws {
        let samples = try QuestionTestFixtures.loadQuestionSamples()
        XCTAssertEqual(samples.count, 11, "Expected 11 samples from question-samples.json")

        let outputDir = ProcessInfo.processInfo.environment["THI360_QUESTION_SAMPLES_DIR"]
        if let outputDir, !outputDir.isEmpty {
            try FileManager.default.createDirectory(
                at: URL(fileURLWithPath: outputDir, isDirectory: true),
                withIntermediateDirectories: true
            )
        }

        var capturedCount = 0
        for sample in samples {
            for mode in ThemeMode.allCases {
                let palette = BrandPaletteSet.defaultThi360Green.palette(for: mode)
                let size = CGSize(width: 390, height: 740)
                let view = ScrollView {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("\(sample.key) · \(mode.rawValue)")
                            .font(.caption.monospaced())
                            .foregroundStyle(palette.textMutedColor)
                        QuestionView(
                            item: sample.item,
                            answer: sample.submittedAnswer,
                            answers: sample.submittedAnswers,
                            mode: .review(sample.review),
                            onAnswer: { _, _ in },
                            locale: .vi,
                            fixedWidth: 358
                        )
                    }
                    .padding(16)
                }
                .background(palette.bgSurfaceColor)

                let (window, _) = QuestionViewTestHelper.host(
                    view,
                    size: size,
                    sizeClass: .compact,
                    themeMode: mode
                )
                defer { window.isHidden = true }

                let snapName = "q-\(sample.key)-\(mode.rawValue)"
                let (image, png) = try XCTUnwrap(QuestionViewTestHelper.snapshotPNG(window: window, size: size))
                XCTAssertGreaterThan(png.count, 512, "Snapshot \(snapName) unexpectedly empty")
                let attachment = XCTAttachment(image: image)
                attachment.name = "\(snapName).png"
                attachment.lifetime = .keepAlways
                add(attachment)

                if let outputDir, !outputDir.isEmpty {
                    let target = URL(fileURLWithPath: outputDir, isDirectory: true)
                        .appendingPathComponent("\(snapName).png")
                    try png.write(to: target)
                }
                capturedCount += 1
            }
        }

        XCTAssertEqual(capturedCount, 22, "Must capture 11 samples x 2 themes = 22 PNG snapshots")
    }
}

@MainActor
final class FiftyItemPartRenderTests: XCTestCase {
    func test50ItemPartRendersLazilyInScrollViewViewport() throws {
        let data = try Data(contentsOf: QuestionTestFixtures.attempt50FixtureURL)
        let mappedItems = try QuestionItemMapper.mapAllUnits(fromAttemptStateData: data)
        XCTAssertGreaterThanOrEqual(mappedItems.count, 30)

        var appearedIds = Set<String>()
        let (window, _) = QuestionViewTestHelper.host(
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 20) {
                    ForEach(mappedItems) { item in
                        QuestionView(
                            item: item,
                            answer: nil,
                            mode: .answering,
                            onAnswer: { _, _ in },
                            locale: .vi,
                            fixedWidth: 358
                        )
                        .onAppear {
                            appearedIds.insert(item.id)
                        }
                    }
                }
                .padding(16)
            },
            size: CGSize(width: 390, height: 844),
            sizeClass: .compact
        )
        defer { window.isHidden = true }

        QuestionViewTestHelper.pumpRunLoop(0.15)
        XCTAssertFalse(appearedIds.isEmpty, "Viewport items must materialize on initial render")
        XCTAssertLessThan(appearedIds.count, 50, "LazyVStack must not eagerly materialize all 50 items offscreen")
    }
}
