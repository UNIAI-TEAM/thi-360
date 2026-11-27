import Foundation
import UIKit
import XCTest
@testable import Thi360

final class FormulaCorpusTests: XCTestCase {
    private static let engine = SwaTexFormulaEngine(
        timeoutSeconds: SwaTexFormulaEngine.defaultTimeoutSeconds,
        displayScale: 2.0,
        useCache: false
    )

    func testSheetsM() throws {
        try runSlice(name: "sheets-M") { entry in
            guard let dir = entry.sheetDirectory else { return false }
            return dir.hasPrefix("M")
        }
    }

    func testSheetsP() throws {
        try runSlice(name: "sheets-P") { entry in
            guard let dir = entry.sheetDirectory else { return false }
            return dir.hasPrefix("P")
        }
    }

    func testSheetsC() throws {
        try runSlice(name: "sheets-C") { entry in
            guard let dir = entry.sheetDirectory else { return false }
            return dir.hasPrefix("C") && !dir.hasPrefix("CH")
        }
    }

    func testSheetsB() throws {
        try runSlice(name: "sheets-B") { entry in
            guard let dir = entry.sheetDirectory else { return false }
            return dir.hasPrefix("B")
        }
    }

    func testSheetsSX() throws {
        try runSlice(name: "sheets-SX") { entry in
            guard let dir = entry.sheetDirectory else { return false }
            return dir.hasPrefix("SX")
        }
    }

    func testSheetsCH() throws {
        try runSlice(name: "sheets-CH") { entry in
            guard let dir = entry.sheetDirectory else { return false }
            return dir.hasPrefix("CH")
        }
    }

    func testSheetRepoSamples() throws {
        try runSlice(name: "sheet-repo-samples") { entry in
            entry.sheetDirectory == "repo-sample"
        }
    }

    func testUnsheetedEntries() throws {
        try runSlice(name: "unsheeted") { entry in
            entry.sheetKey == nil
        }
    }

    private func runSlice(
        name: String,
        predicate: (FormulaEntry) -> Bool
    ) throws {
        let corpus = try FormulaCorpusLoader.loadCorpus()
        let exemptions = try FormulaCorpusLoader.loadExemptions()
        let iosExemptions = exemptions.rows.filter { $0.app.lowercased() == "ios" }

        var rows: [FormulaCheckRow] = []
        var failures: [String] = []
        var proposedExemptions: [FormulaExemption] = []

        let indexedEntries = corpus.entries.enumerated().filter { predicate($0.element) }
        XCTAssertFalse(indexedEntries.isEmpty, "Slice \(name) unexpectedly matched 0 entries")

        for (index, entry) in indexedEntries {
            let isExempt = iosExemptions.contains {
                $0.latex == entry.latex && $0.display == entry.display
            }
            let formula = Formula(source: entry.latex, display: entry.display)
            let rendered = Self.engine.render(formula, pointSize: 17, color: .black)

            let outcome: FormulaCheckOutcome = rendered.isRendered ? .rendered : .fallback
            rows.append(
                FormulaCheckRow(
                    index: index,
                    latex: entry.latex,
                    sheetKey: entry.sheetKey,
                    rejectedArea: entry.rejectedArea,
                    group: entry.group,
                    kind: entry.kind,
                    form: entry.display.rawValue,
                    outcome: outcome,
                    expectedRendered: entry.katexRenders,
                    exempted: isExempt
                )
            )

            if entry.katexRenders {
                if !rendered.isRendered && !isExempt {
                    let loc = entry.sheetKey ?? entry.rejectedArea ?? "unsheeted"
                    failures.append(
                        "[\(index)] expected RENDERED but got FALLBACK | loc=\(loc) group=\(entry.group ?? "nil") kind=\(entry.kind ?? "nil") display=\(entry.display.rawValue) latex=\(entry.latex)"
                    )
                    proposedExemptions.append(
                        FormulaExemption(
                            latex: entry.latex,
                            display: entry.display,
                            app: "ios",
                            reason: "SwaTex 0.6.0 returned unsupported fallback",
                            beyondCurriculum: entry.kind != "curriculum",
                            origin: entry.origin ?? "catalogue",
                            group: entry.group,
                            kind: entry.kind,
                            approvalRef: ""
                        )
                    )
                }
            } else if rendered.isRendered {
                let loc = entry.sheetKey ?? entry.rejectedArea ?? "unsheeted"
                failures.append(
                    "[\(index)] expected UNSUPPORTED (katexRenders=false) but SwaTex RENDERED | loc=\(loc) group=\(entry.group ?? "nil") kind=\(entry.kind ?? "nil") display=\(entry.display.rawValue) latex=\(entry.latex)"
                )
            }
        }

        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]

        if let resultsData = try? encoder.encode(rows) {
            let attachment = XCTAttachment(data: resultsData, uniformTypeIdentifier: "public.json")
            attachment.name = "formula-check-\(name).json"
            attachment.lifetime = .keepAlways
            add(attachment)

            if let outDir = ProcessInfo.processInfo.environment["THI360_FORMULA_CHECK_DIR"], !outDir.isEmpty {
                let dirURL = URL(fileURLWithPath: outDir, isDirectory: true)
                try? FileManager.default.createDirectory(at: dirURL, withIntermediateDirectories: true)
                try? resultsData.write(to: dirURL.appendingPathComponent("formula-check-\(name).json"))
            }
        }

        if !proposedExemptions.isEmpty,
           let proposedData = try? encoder.encode(FormulaExemptionsFile(version: 1, rows: proposedExemptions))
        {
            let attachment = XCTAttachment(data: proposedData, uniformTypeIdentifier: "public.json")
            attachment.name = "proposed-exemptions-\(name).json"
            attachment.lifetime = .keepAlways
            add(attachment)

            if let outDir = ProcessInfo.processInfo.environment["THI360_PROPOSED_EXEMPTIONS_DIR"], !outDir.isEmpty {
                let dirURL = URL(fileURLWithPath: outDir, isDirectory: true)
                try? FileManager.default.createDirectory(at: dirURL, withIntermediateDirectories: true)
                try? proposedData.write(to: dirURL.appendingPathComponent("proposed-exemptions-\(name).json"))
            }
        }

        if !failures.isEmpty {
            XCTFail("FormulaCorpusTests(\(name)): \(failures.count) mismatch(es) out of \(indexedEntries.count):\n" + failures.joined(separator: "\n"))
        }
    }
}
