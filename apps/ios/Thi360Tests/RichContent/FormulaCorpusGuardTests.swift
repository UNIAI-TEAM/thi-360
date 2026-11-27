import Foundation
import XCTest
@testable import Thi360

final class FormulaCorpusGuardTests: XCTestCase {
    func testBundledCorpusExemptionsAndWebRendersAreValid() throws {
        let corpus = try FormulaCorpusLoader.loadCorpus()
        let exemptions = try FormulaCorpusLoader.loadExemptions()
        let acceptanceReport = try? String(contentsOf: FormulaCorpusLoader.acceptanceReportURL, encoding: .utf8)
        let webRendersDir = FormulaCorpusLoader.richTextBundleURL.appendingPathComponent("web-renders", isDirectory: true)

        let errors = FormulaCorpusLoader.validateCorpus(
            corpus,
            exemptions: exemptions,
            acceptanceReportContent: acceptanceReport,
            webRendersDirectory: webRendersDir
        )
        XCTAssertTrue(errors.isEmpty, "Formula corpus guard failed:\n" + errors.joined(separator: "\n"))
    }

    func testGuardRejectsMissingOrigin() throws {
        var corpus = try FormulaCorpusLoader.loadCorpus()
        let first = corpus.entries[0]
        let broken = FormulaEntry(
            latex: first.latex,
            display: first.display,
            origin: nil,
            group: first.group,
            kind: first.kind,
            rejectedArea: first.rejectedArea,
            katexRenders: first.katexRenders,
            sheetKey: first.sheetKey
        )
        var modified = corpus.entries
        modified[0] = broken
        corpus = FormulaCorpusFile(
            katexVersion: corpus.katexVersion,
            mhchem: corpus.mhchem,
            catalogue: corpus.catalogue,
            entries: modified
        )

        let errors = FormulaCorpusLoader.validateCorpus(
            corpus,
            exemptions: FormulaExemptionsFile(version: 1, rows: []),
            acceptanceReportContent: nil
        )
        XCTAssertTrue(errors.contains { $0.contains("missing origin") }, "Expected missing origin error, got: \(errors)")
    }

    func testGuardRejectsMissingGroupOrKindOnCatalogueRenderableFormula() throws {
        let corpus = try FormulaCorpusLoader.loadCorpus()
        let idx = try XCTUnwrap(corpus.entries.firstIndex { $0.origin == "catalogue" && $0.katexRenders && $0.rejectedArea == nil })
        let base = corpus.entries[idx]

        var noGroupEntries = corpus.entries
        noGroupEntries[idx] = FormulaEntry(
            latex: base.latex, display: base.display, origin: base.origin,
            group: nil, kind: base.kind, rejectedArea: nil,
            katexRenders: true, sheetKey: base.sheetKey
        )
        let noGroupErrors = FormulaCorpusLoader.validateCorpus(
            FormulaCorpusFile(katexVersion: corpus.katexVersion, mhchem: corpus.mhchem, catalogue: corpus.catalogue, entries: noGroupEntries),
            exemptions: FormulaExemptionsFile(version: 1, rows: []),
            acceptanceReportContent: nil
        )
        XCTAssertTrue(noGroupErrors.contains { $0.contains("missing group") })

        var noKindEntries = corpus.entries
        noKindEntries[idx] = FormulaEntry(
            latex: base.latex, display: base.display, origin: base.origin,
            group: base.group, kind: nil, rejectedArea: nil,
            katexRenders: true, sheetKey: base.sheetKey
        )
        let noKindErrors = FormulaCorpusLoader.validateCorpus(
            FormulaCorpusFile(katexVersion: corpus.katexVersion, mhchem: corpus.mhchem, catalogue: corpus.catalogue, entries: noKindEntries),
            exemptions: FormulaExemptionsFile(version: 1, rows: []),
            acceptanceReportContent: nil
        )
        XCTAssertTrue(noKindErrors.contains { $0.contains("kind") })
    }

    func testGuardRejectsWrongCountsOrKatexVersion() throws {
        let corpus = try FormulaCorpusLoader.loadCorpus()

        let wrongVersion = FormulaCorpusFile(
            katexVersion: "0.18.0",
            mhchem: corpus.mhchem,
            catalogue: corpus.catalogue,
            entries: corpus.entries
        )
        let versionErrors = FormulaCorpusLoader.validateCorpus(
            wrongVersion,
            exemptions: FormulaExemptionsFile(version: 1, rows: []),
            acceptanceReportContent: nil
        )
        XCTAssertTrue(versionErrors.contains { $0.contains("0.19.1") })

        let droppedEntries = Array(corpus.entries.dropFirst(10))
        let wrongCount = FormulaCorpusFile(
            katexVersion: corpus.katexVersion,
            mhchem: corpus.mhchem,
            catalogue: corpus.catalogue,
            entries: droppedEntries
        )
        let countErrors = FormulaCorpusLoader.validateCorpus(
            wrongCount,
            exemptions: FormulaExemptionsFile(version: 1, rows: []),
            acceptanceReportContent: nil
        )
        XCTAssertTrue(countErrors.contains { $0.contains("1915") || $0.contains("225") })
    }

    func testGuardRejectsInvalidExemptionRows() throws {
        let corpus = try FormulaCorpusLoader.loadCorpus()

        let missingReasonAndFlag = FormulaExemptionsFile(
            version: 1,
            rows: [
                FormulaExemption(
                    latex: "\\mathscr{F}",
                    display: .block,
                    app: "ios",
                    reason: "",
                    beyondCurriculum: nil,
                    origin: "catalogue",
                    group: "SX-01",
                    kind: "syntax",
                    approvalRef: nil
                )
            ]
        )
        let r1 = FormulaCorpusLoader.validateCorpus(corpus, exemptions: missingReasonAndFlag, acceptanceReportContent: nil)
        XCTAssertTrue(r1.contains { $0.contains("missing reason") })
        XCTAssertTrue(r1.contains { $0.contains("missing beyondCurriculum") })

        let unapprovedCurriculum = FormulaExemptionsFile(
            version: 1,
            rows: [
                FormulaExemption(
                    latex: "x^2 + y^2 = z^2",
                    display: .block,
                    app: "ios",
                    reason: "Unsupported glyph",
                    beyondCurriculum: false,
                    origin: "catalogue",
                    group: "M10-01",
                    kind: "curriculum",
                    approvalRef: nil
                )
            ]
        )
        let r2 = FormulaCorpusLoader.validateCorpus(corpus, exemptions: unapprovedCurriculum, acceptanceReportContent: nil)
        XCTAssertTrue(r2.contains { $0.contains("approvalRef") })

        let unapprovedRepoSample = FormulaExemptionsFile(
            version: 1,
            rows: [
                FormulaExemption(
                    latex: "x^2 + y^2 = z^2",
                    display: .block,
                    app: "ios",
                    reason: "Unsupported glyph",
                    beyondCurriculum: false,
                    origin: "repo-sample",
                    group: nil,
                    kind: nil,
                    approvalRef: "D-8-MISSING"
                )
            ]
        )
        let r3 = FormulaCorpusLoader.validateCorpus(
            corpus,
            exemptions: unapprovedRepoSample,
            acceptanceReportContent: "# Acceptance report without this ref"
        )
        XCTAssertTrue(r3.contains { $0.contains("not recorded in acceptance report") })
    }
}
