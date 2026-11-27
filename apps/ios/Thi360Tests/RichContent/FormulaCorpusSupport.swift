import Foundation
import XCTest
@testable import Thi360

struct FormulaCorpusCatalogueCounts: Codable, Equatable {
    let groups: Int
    let formulas: Int
    let curriculumFormulas: Int?
    let syntaxFormulas: Int?
    let mhchemFormulas: Int?
    let blockOnly: Int?
    let rejected: Int
}

struct FormulaCorpusCatalogueMeta: Codable, Equatable {
    let path: String
    let date: String
    let counts: FormulaCorpusCatalogueCounts
}

struct FormulaEntry: Codable, Equatable {
    let latex: String
    let display: FormulaDisplay
    let origin: String?
    let group: String?
    let kind: String?
    let rejectedArea: String?
    let katexRenders: Bool
    let sheetKey: String?

    var sheetDirectory: String? {
        guard let sheetKey, let slash = sheetKey.firstIndex(of: "/") else { return nil }
        return String(sheetKey[..<slash])
    }

    var sheetItemNumber: String? {
        guard let sheetKey, let slash = sheetKey.firstIndex(of: "/") else { return nil }
        return String(sheetKey[sheetKey.index(after: slash)...])
    }
}

struct FormulaCorpusFile: Codable, Equatable {
    let katexVersion: String
    let mhchem: Bool
    let catalogue: FormulaCorpusCatalogueMeta
    let entries: [FormulaEntry]
}

struct FormulaExemption: Codable, Equatable {
    let latex: String
    let display: FormulaDisplay
    let app: String
    let reason: String?
    let beyondCurriculum: Bool?
    let origin: String?
    let group: String?
    let kind: String?
    let approvalRef: String?
}

struct FormulaExemptionsFile: Codable, Equatable {
    let version: Int
    let rows: [FormulaExemption]
}

enum FormulaCheckOutcome: String, Codable, Equatable {
    case rendered
    case fallback
    case crash
    case timeout
}

struct FormulaCheckRow: Codable, Equatable {
    let index: Int
    let latex: String
    let sheetKey: String?
    let rejectedArea: String?
    let group: String?
    let kind: String?
    let form: String
    let outcome: FormulaCheckOutcome
    let expectedRendered: Bool
    let exempted: Bool
}

enum FormulaCorpusLoader {
    static var repoRootURL: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
    }

    static var richTextBundleURL: URL {
        let testBundle = Bundle(for: FormulaCorpusBundleMarker.self)
        if let bundled = testBundle.resourceURL?.appendingPathComponent("rich-text", isDirectory: true),
           FileManager.default.fileExists(atPath: bundled.appendingPathComponent("formulas.json").path)
        {
            return bundled
        }
        return repoRootURL.appendingPathComponent("contracts/mobile/rich-text", isDirectory: true)
    }

    static var richTextSamplesURL: URL {
        let testBundle = Bundle(for: FormulaCorpusBundleMarker.self)
        if let bundled = testBundle.url(forResource: "rich-text-samples", withExtension: "json") {
            return bundled
        }
        return repoRootURL.appendingPathComponent("docs/specs/fixtures/rich-text-samples.json")
    }

    static var acceptanceReportURL: URL {
        repoRootURL.appendingPathComponent("docs/acceptance/app-hoc-vien-cong-thi.md")
    }

    static func loadCorpus(from url: URL? = nil) throws -> FormulaCorpusFile {
        let target = url ?? richTextBundleURL.appendingPathComponent("formulas.json")
        let data = try Data(contentsOf: target)
        return try JSONDecoder().decode(FormulaCorpusFile.self, from: data)
    }

    static func loadExemptions(from url: URL? = nil) throws -> FormulaExemptionsFile {
        let target = url ?? richTextBundleURL.appendingPathComponent("formula-exemptions.json")
        let data = try Data(contentsOf: target)
        return try JSONDecoder().decode(FormulaExemptionsFile.self, from: data)
    }

    static func webRenderImageURL(for sheetKey: String) -> URL {
        richTextBundleURL
            .appendingPathComponent("web-renders", isDirectory: true)
            .appendingPathComponent("\(sheetKey).png")
    }

    static func validateCorpus(
        _ corpus: FormulaCorpusFile,
        exemptions: FormulaExemptionsFile,
        acceptanceReportContent: String?,
        webRendersDirectory: URL? = nil
    ) -> [String] {
        var errors: [String] = []

        if corpus.katexVersion != "0.19.1" {
            errors.append("Expected katexVersion 0.19.1, got '\(corpus.katexVersion)'")
        }

        if corpus.catalogue.counts.groups != 225 {
            errors.append("Expected catalogue.counts.groups == 225, got \(corpus.catalogue.counts.groups)")
        }
        if corpus.catalogue.counts.formulas != 1915 {
            errors.append("Expected catalogue.counts.formulas == 1915, got \(corpus.catalogue.counts.formulas)")
        }
        if corpus.catalogue.counts.rejected != 93 {
            errors.append("Expected catalogue.counts.rejected == 93, got \(corpus.catalogue.counts.rejected)")
        }

        var distinctGroups = Set<String>()
        var catalogueRenderableBlocks = 0
        var rejectedBlocks = 0
        var sheetDirs = Set<String>()

        for (idx, entry) in corpus.entries.enumerated() {
            let trimmedOrigin = entry.origin?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            if trimmedOrigin.isEmpty {
                errors.append("Entry[\(idx)] missing origin: \(entry.latex)")
                continue
            }

            if trimmedOrigin == "catalogue" && entry.rejectedArea == nil && entry.katexRenders {
                let trimmedGroup = entry.group?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                let trimmedKind = entry.kind?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                if trimmedGroup.isEmpty {
                    errors.append("Entry[\(idx)] renderable catalogue formula missing group: \(entry.latex)")
                } else {
                    distinctGroups.insert(trimmedGroup)
                }
                if trimmedKind != "curriculum" && trimmedKind != "syntax" {
                    errors.append("Entry[\(idx)] renderable catalogue formula has invalid/missing kind '\(trimmedKind)': \(entry.latex)")
                }
                if entry.display == .block {
                    catalogueRenderableBlocks += 1
                }
            }

            if trimmedOrigin == "catalogue" && entry.rejectedArea != nil && entry.display == .block {
                rejectedBlocks += 1
            }

            if let dir = entry.sheetDirectory {
                sheetDirs.insert(dir)
            }
        }

        if distinctGroups.count != 225 {
            errors.append("Expected 225 distinct groups in entries, got \(distinctGroups.count)")
        }
        if catalogueRenderableBlocks != 1915 {
            errors.append("Expected 1915 renderable catalogue block formulas, got \(catalogueRenderableBlocks)")
        }
        if rejectedBlocks != 93 {
            errors.append("Expected 93 rejected catalogue formulas, got \(rejectedBlocks)")
        }

        if let webRendersDirectory {
            if sheetDirs.count != 226 {
                errors.append("Expected 226 sheet directories in formulas.json, got \(sheetDirs.count)")
            }
            for dir in sheetDirs.sorted() {
                let dirURL = webRendersDirectory.appendingPathComponent(dir, isDirectory: true)
                var isDir: ObjCBool = false
                if !FileManager.default.fileExists(atPath: dirURL.path, isDirectory: &isDir) || !isDir.boolValue {
                    errors.append("Missing web-renders directory for sheet '\(dir)'")
                }
            }
            for entry in corpus.entries {
                guard let sheetKey = entry.sheetKey else { continue }
                let imgURL = webRendersDirectory.appendingPathComponent("\(sheetKey).png")
                if !FileManager.default.fileExists(atPath: imgURL.path) {
                    errors.append("Missing web-renders PNG for sheetKey '\(sheetKey)'")
                }
            }
        }

        for (idx, row) in exemptions.rows.enumerated() {
            let trimmedReason = row.reason?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            if trimmedReason.isEmpty {
                errors.append("Exemption[\(idx)] missing reason for '\(row.latex)'")
            }
            if row.beyondCurriculum == nil {
                errors.append("Exemption[\(idx)] missing beyondCurriculum flag for '\(row.latex)'")
            }
            let requiresIndividualApproval =
                row.kind == "curriculum"
                || row.origin == "repo-sample"
                || row.group == nil
                || row.group?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == true

            if requiresIndividualApproval {
                let ref = row.approvalRef?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                if ref.isEmpty {
                    errors.append("Exemption[\(idx)] requires individual approvalRef for '\(row.latex)' (origin=\(row.origin ?? "nil"), kind=\(row.kind ?? "nil"), group=\(row.group ?? "nil"))")
                } else if let report = acceptanceReportContent {
                    let hasRef = report.contains(ref)
                    let hasLatex = report.contains(row.latex)
                    if !hasRef || !hasLatex {
                        errors.append("Exemption[\(idx)] approvalRef '\(ref)' or formula '\(row.latex)' not recorded in acceptance report")
                    }
                } else {
                    errors.append("Exemption[\(idx)] has approvalRef '\(ref)' but acceptance report is missing")
                }
            }
        }

        return errors
    }
}

private final class FormulaCorpusBundleMarker {}
