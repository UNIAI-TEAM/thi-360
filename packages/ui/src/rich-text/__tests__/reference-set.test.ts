import { existsSync, mkdirSync, readdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import path from "node:path";
import { beforeAll, describe, expect, it } from "vitest";
import { countWords } from "../../question/count-words";
import { buildCatalogueDraftEntries, collectRepoSampleDraftEntries } from "./reference-set/extract";
import {
  assignSheetKeys,
  buildSheetHtmlDocument,
  evaluateAllDraftEntries,
  getInstalledKatexVersion,
  getKatexCssHref,
  sha256Hex,
  type SheetTile,
} from "./reference-set/render";
import type {
  ApprovalTableRow,
  FormulaCatalogue,
  FormulaEntry,
  FormulaExemptionsFile,
  FormulasFile,
  ReferenceIndexFile,
  WebRendersManifest,
  WordCountFile,
} from "./reference-set/types";
import {
  EXPECTED_APPROVAL_COLUMNS,
  parseApprovalTable,
  validateCatalogueKatexCounts,
  validateCatalogueMatch,
  validateExemptionRules,
  validateExemptionSchema,
  validateKatexVersion,
  validateWebRenderFiles,
  validateWebRendersManifest,
} from "./reference-set/validators";

const REPO_ROOT = path.resolve(import.meta.dirname, "../../../../..");
const CONTRACTS_DIR = path.join(REPO_ROOT, "contracts/mobile/rich-text");
const FORMULAS_PATH = path.join(CONTRACTS_DIR, "formulas.json");
const EXEMPTIONS_PATH = path.join(CONTRACTS_DIR, "formula-exemptions.json");
const INDEX_PATH = path.join(CONTRACTS_DIR, "index.json");
const WORD_COUNT_PATH = path.join(CONTRACTS_DIR, "word-count.json");
const WEB_RENDERS_DIR = path.join(CONTRACTS_DIR, "web-renders");
const MANIFEST_PATH = path.join(WEB_RENDERS_DIR, "manifest.json");
const CATALOGUE_PATH = path.join(REPO_ROOT, "docs/specs/fixtures/formula-catalogue.json");
const ACCEPTANCE_PATH = path.join(REPO_ROOT, "docs/acceptance/app-hoc-vien-cong-thi.md");
const SHEETS_HTML_DIR = path.join(REPO_ROOT, "e2e/test-results/formula-sheets/html");

const WRITE_MODE = process.env.RICH_TEXT_REFERENCE_WRITE === "1";
const SHEETS_MODE = process.env.RICH_TEXT_REFERENCE_SHEETS === "1";

function writeStableJson(filePath: string, data: unknown) {
  mkdirSync(path.dirname(filePath), { recursive: true });
  writeFileSync(filePath, `${JSON.stringify(data, null, 2)}\n`, "utf8");
}

function readJson<T>(filePath: string): T {
  return JSON.parse(readFileSync(filePath, "utf8")) as T;
}

function listPngRelativePaths(rootDir: string): string[] {
  if (!existsSync(rootDir)) return [];
  const results: string[] = [];
  const walk = (dir: string) => {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        walk(full);
      } else if (entry.name.endsWith(".png")) {
        results.push(path.relative(rootDir, full).replace(/\\/g, "/"));
      }
    }
  };
  walk(rootDir);
  return results.sort();
}

describe("rich-text reference set (RT-1)", () => {
  let installedKatexVersion = "";
  let catalogue: FormulaCatalogue;
  let computedEntries: FormulaEntry[] = [];
  let computedHashBySheetKey = new Map<string, string>();
  let computedHtmlBySheetKey = new Map<string, string>();
  let formulasFile: FormulasFile;

  beforeAll(async () => {
    installedKatexVersion = getInstalledKatexVersion();
    catalogue = readJson<FormulaCatalogue>(CATALOGUE_PATH);

    const drafts = [
      ...buildCatalogueDraftEntries(catalogue),
      ...collectRepoSampleDraftEntries(REPO_ROOT),
    ];
    const originalWarn = console.warn;
    console.warn = (...args: unknown[]) => {
      if (typeof args[0] === "string" && args[0].startsWith("LaTeX-incompatible input")) return;
      originalWarn(...args);
    };
    let evaluated: Awaited<ReturnType<typeof evaluateAllDraftEntries>>;
    try {
      evaluated = await evaluateAllDraftEntries(drafts);
    } finally {
      console.warn = originalWarn;
    }
    computedEntries = evaluated.entries;
    computedHtmlBySheetKey = evaluated.renderedHtmlBySheetKey;
    computedHashBySheetKey = new Map<string, string>();
    for (const [sheetKey, html] of computedHtmlBySheetKey) {
      computedHashBySheetKey.set(sheetKey, sha256Hex(html));
    }

    if (WRITE_MODE) {
      const nextFormulas: FormulasFile = {
        katexVersion: installedKatexVersion,
        mhchem: true,
        catalogue: {
          path: "docs/specs/fixtures/formula-catalogue.json",
          date: catalogue.date,
          counts: catalogue.counts,
        },
        entries: computedEntries,
      };
      writeStableJson(FORMULAS_PATH, nextFormulas);

      const wordCountDoc = readJson<WordCountFile>(WORD_COUNT_PATH);
      const nextWordCount: WordCountFile = {
        version: wordCountDoc.version,
        cases: wordCountDoc.cases.map((item) => ({
          ...item,
          expected: countWords(item.html),
        })),
      };
      writeStableJson(WORD_COUNT_PATH, nextWordCount);
    }

    if (SHEETS_MODE) {
      const htmlSha256: Record<string, string> = {};
      const tilesBySheet = new Map<string, SheetTile[]>();
      for (const entry of computedEntries) {
        if (entry.sheetKey === null) continue;
        const renderedHtml = computedHtmlBySheetKey.get(entry.sheetKey)!;
        htmlSha256[entry.sheetKey] = computedHashBySheetKey.get(entry.sheetKey)!;
        const sheetName = entry.sheetKey.split("/")[0]!;
        const list = tilesBySheet.get(sheetName) ?? [];
        list.push({ sheetKey: entry.sheetKey, renderedHtml });
        tilesBySheet.set(sheetName, list);
      }

      const manifest: WebRendersManifest = {
        katexVersion: installedKatexVersion,
        htmlSha256,
      };
      writeStableJson(MANIFEST_PATH, manifest);

      rmSync(SHEETS_HTML_DIR, { recursive: true, force: true });
      mkdirSync(SHEETS_HTML_DIR, { recursive: true });
      const katexCssHref = getKatexCssHref();
      for (const [sheetName, tiles] of tilesBySheet) {
        const htmlDoc = buildSheetHtmlDocument(sheetName, tiles, katexCssHref);
        writeFileSync(path.join(SHEETS_HTML_DIR, `${sheetName}.html`), htmlDoc, "utf8");
      }
    }

    formulasFile = readJson<FormulasFile>(FORMULAS_PATH);
  }, 60_000);

  it("entriesCarrySource", () => {
    const groupKindById = new Map<string, "curriculum" | "syntax">();
    for (const part of catalogue.parts) {
      for (const group of part.groups) {
        groupKindById.set(group.id, group.kind);
      }
    }
    const rejectedAreasByLatex = new Map<string, Set<"math" | "science">>();
    for (const rej of catalogue.rejected) {
      const set = rejectedAreasByLatex.get(rej.latex.trim()) ?? new Set();
      set.add(rej.area);
      rejectedAreasByLatex.set(rej.latex.trim(), set);
    }

    expect(formulasFile.entries.length).toBeGreaterThan(0);
    for (const entry of formulasFile.entries) {
      expect(["catalogue", "repo-sample"]).toContain(entry.origin);
      expect(["inline", "block"]).toContain(entry.display);
      expect(entry.latex).toBe(entry.latex.trim());
      expect(entry.latex.length).toBeGreaterThan(0);

      if (entry.origin === "repo-sample") {
        expect(entry.group).toBeNull();
        expect(entry.kind).toBeNull();
        expect(entry.rejectedArea).toBeNull();
      } else if (entry.group !== null) {
        expect(groupKindById.has(entry.group), `Unknown group ${entry.group}`).toBe(true);
        expect(entry.kind).toBe(groupKindById.get(entry.group));
        expect(entry.rejectedArea).toBeNull();
      } else {
        expect(entry.kind).toBeNull();
        expect(["math", "science"]).toContain(entry.rejectedArea);
        expect(rejectedAreasByLatex.get(entry.latex)?.has(entry.rejectedArea!)).toBe(true);
      }
    }
  });

  it("matchesCatalogue", () => {
    const errors = validateCatalogueMatch(formulasFile.entries, catalogue);
    expect(errors).toEqual([]);
    const catalogueEntries = formulasFile.entries.filter((e) => e.origin === "catalogue");
    expect(catalogueEntries).toHaveLength(4008);
  });

  it("catalogueDriftDetected", () => {
    const cloneEntries = () => formulasFile.entries.map((entry) => ({ ...entry }));
    const cloneCatalogue = (): FormulaCatalogue => JSON.parse(JSON.stringify(catalogue)) as FormulaCatalogue;

    const firstGroupEntry = formulasFile.entries.find((e) => e.origin === "catalogue" && e.group !== null)!;
    const blockOnlyGroup = catalogue.parts
      .flatMap((p) => p.groups)
      .find((g) => g.formulas.some((f) => f.blockOnly))!;
    const blockOnlyFormula = blockOnlyGroup.formulas.find((f) => f.blockOnly)!;

    const dropped = cloneEntries().filter(
      (e) =>
        !(
          e.origin === "catalogue" &&
          e.group === firstGroupEntry.group &&
          e.latex === firstGroupEntry.latex &&
          e.display === firstGroupEntry.display
        ),
    );
    const dropErrors = validateCatalogueMatch(dropped, catalogue);
    expect(dropErrors.some((m) => m.includes(firstGroupEntry.group!) && m.includes(firstGroupEntry.latex) && m.includes(firstGroupEntry.display))).toBe(true);

    const added = [
      ...cloneEntries(),
      { ...firstGroupEntry, latex: "\\text{extra-only-in-formulas}" },
    ];
    const addErrors = validateCatalogueMatch(added, catalogue);
    expect(addErrors.some((m) => m.includes(firstGroupEntry.group!) && m.includes("\\text{extra-only-in-formulas}") && m.includes(firstGroupEntry.display))).toBe(true);

    const editedLatex = cloneEntries().map((e) =>
      e.origin === "catalogue" &&
      e.group === firstGroupEntry.group &&
      e.latex === firstGroupEntry.latex &&
      e.display === firstGroupEntry.display
        ? { ...e, latex: `${e.latex} + 0` }
        : e,
    );
    const editErrors = validateCatalogueMatch(editedLatex, catalogue);
    expect(editErrors.some((m) => m.includes(firstGroupEntry.group!) && m.includes(firstGroupEntry.latex) && m.includes(firstGroupEntry.display))).toBe(true);

    const changedGroup = cloneEntries().map((e) =>
      e.origin === "catalogue" &&
      e.group === firstGroupEntry.group &&
      e.latex === firstGroupEntry.latex &&
      e.display === firstGroupEntry.display
        ? { ...e, group: "M10-99" }
        : e,
    );
    const groupErrors = validateCatalogueMatch(changedGroup, catalogue);
    expect(groupErrors.some((m) => m.includes("M10-99"))).toBe(true);

    const changedKind = cloneEntries().map((e) =>
      e.origin === "catalogue" &&
      e.group === firstGroupEntry.group &&
      e.latex === firstGroupEntry.latex &&
      e.display === firstGroupEntry.display
        ? { ...e, kind: e.kind === "curriculum" ? ("syntax" as const) : ("curriculum" as const) }
        : e,
    );
    const kindErrors = validateCatalogueMatch(changedKind, catalogue);
    expect(kindErrors.some((m) => m.includes("Kind mismatch") && m.includes(firstGroupEntry.group!) && m.includes(firstGroupEntry.latex) && m.includes(firstGroupEntry.display))).toBe(true);

    const catDropped = cloneCatalogue();
    const removedFormula = catDropped.parts[0]!.groups[0]!.formulas.shift()!;
    const catDropErrors = validateCatalogueMatch(formulasFile.entries, catDropped);
    expect(catDropErrors.some((m) => m.includes(catDropped.parts[0]!.groups[0]!.id) && m.includes(removedFormula.latex))).toBe(true);

    const catAdded = cloneCatalogue();
    catAdded.parts[0]!.groups[0]!.formulas.push({ latex: "\\text{added-in-catalogue}" });
    const catAddErrors = validateCatalogueMatch(formulasFile.entries, catAdded);
    expect(catAddErrors.some((m) => m.includes(catAdded.parts[0]!.groups[0]!.id) && m.includes("\\text{added-in-catalogue}") && m.includes("block"))).toBe(true);

    const catFlippedBlockOnly = cloneCatalogue();
    const targetGroup = catFlippedBlockOnly.parts.flatMap((p) => p.groups).find((g) => g.id === blockOnlyGroup.id)!;
    const targetFormula = targetGroup.formulas.find((f) => f.latex === blockOnlyFormula.latex)!;
    targetFormula.blockOnly = false;
    const blockOnlyErrors = validateCatalogueMatch(formulasFile.entries, catFlippedBlockOnly);
    expect(blockOnlyErrors.some((m) => m.includes(blockOnlyGroup.id) && m.includes(blockOnlyFormula.latex) && m.includes("inline"))).toBe(true);

    const catChangedKind = cloneCatalogue();
    catChangedKind.parts[0]!.groups[0]!.kind = "syntax";
    const catKindErrors = validateCatalogueMatch(formulasFile.entries, catChangedKind);
    expect(catKindErrors.some((m) => m.includes("Kind mismatch") && m.includes(catChangedKind.parts[0]!.groups[0]!.id))).toBe(true);

    const catChangedGroupId = cloneCatalogue();
    catChangedGroupId.parts[0]!.groups[0]!.id = "M10-RENAMED";
    const catGroupIdErrors = validateCatalogueMatch(formulasFile.entries, catChangedGroupId);
    expect(catGroupIdErrors.some((m) => m.includes("M10-RENAMED"))).toBe(true);
  });

  it("repoSamplesIncluded", () => {
    const expectedRepoSamples = collectRepoSampleDraftEntries(REPO_ROOT);
    const actualRepoSamples = formulasFile.entries.filter((e) => e.origin === "repo-sample");

    expect(expectedRepoSamples.length).toBeGreaterThan(0);
    expect(actualRepoSamples.map((e) => ({ latex: e.latex, display: e.display }))).toEqual(
      expectedRepoSamples.map((e) => ({ latex: e.latex, display: e.display })),
    );
  });

  it("katexColumnMatches", () => {
    expect(formulasFile.entries).toHaveLength(computedEntries.length);

    const mismatches: string[] = [];
    formulasFile.entries.forEach((actual, index) => {
      const expected = computedEntries[index]!;
      if (
        actual.origin !== expected.origin ||
        actual.group !== expected.group ||
        actual.display !== expected.display ||
        actual.latex !== expected.latex ||
        actual.katexRenders !== expected.katexRenders ||
        actual.sheetKey !== expected.sheetKey
      ) {
        mismatches.push(
          `entry[${index}] (${actual.origin}/${actual.group ?? "null"}/${actual.display}/${actual.latex}): ` +
            `file={katexRenders:${actual.katexRenders}, sheetKey:${String(actual.sheetKey)}} ` +
            `computed={katexRenders:${expected.katexRenders}, sheetKey:${String(expected.sheetKey)}}`,
        );
      }
    });
    expect(mismatches).toEqual([]);

    const brokenInline = formulasFile.entries.find(
      (e) => e.origin === "repo-sample" && e.display === "inline" && e.latex === "\\broken{syntax}",
    );
    expect(brokenInline).toBeDefined();
    expect(brokenInline!.katexRenders).toBe(false);

    const brokenBlock = formulasFile.entries.find(
      (e) => e.origin === "repo-sample" && e.display === "block" && e.latex === "\\frac{1}{",
    );
    expect(brokenBlock).toBeDefined();
    expect(brokenBlock!.katexRenders).toBe(false);
  });

  it("katexAgreesWithCatalogue", () => {
    const { discrepancies, errors } = validateCatalogueKatexCounts(computedEntries, catalogue);
    const formatted = discrepancies.map(
      (d) =>
        `group=${d.group} | display=${d.display} | latex=${d.latex} | web=${d.webRenders} | catalogue=${d.catalogueExpected}`,
    );
    expect(formatted, `Discrepancies between web renderMath and formula-catalogue.json:\n${formatted.join("\n")}`).toEqual([]);
    expect(errors).toEqual([]);
  });

  it("katexVersionPinned", () => {
    expect(validateKatexVersion(formulasFile.katexVersion, installedKatexVersion)).toBeNull();
    const mismatchMsg = validateKatexVersion(formulasFile.katexVersion, "0.99.0");
    expect(mismatchMsg).not.toBeNull();
    expect(mismatchMsg).toContain("RICH_TEXT_REFERENCE_WRITE=1");
  });

  it("sheetKeys", () => {
    const recomputed = assignSheetKeys(formulasFile.entries);
    const mismatches: string[] = [];
    formulasFile.entries.forEach((entry, index) => {
      if (entry.sheetKey !== recomputed[index]!.sheetKey) {
        mismatches.push(
          `entry[${index}] (${entry.origin}/${entry.group ?? "null"}/${entry.display}/${entry.latex}): ` +
            `file sheetKey=${String(entry.sheetKey)}, expected=${String(recomputed[index]!.sheetKey)}`,
        );
      }
    });
    expect(mismatches).toEqual([]);

    const sheets = new Map<string, number[]>();
    for (const entry of formulasFile.entries) {
      const shouldHaveSheet =
        entry.display === "block" &&
        entry.katexRenders &&
        (entry.group !== null || entry.origin === "repo-sample");

      if (!shouldHaveSheet) {
        expect(entry.sheetKey, `Unexpected sheetKey on ${entry.latex}`).toBeNull();
        continue;
      }

      expect(entry.sheetKey).not.toBeNull();
      const [sheetName, numPart] = entry.sheetKey!.split("/");
      expect(sheetName).toBe(entry.origin === "repo-sample" ? "repo-sample" : entry.group);
      expect(numPart).toMatch(/^\d{3}$/);
      const list = sheets.get(sheetName!) ?? [];
      list.push(Number.parseInt(numPart!, 10));
      sheets.set(sheetName!, list);
    }

    expect(sheets.size).toBe(226);
    expect(sheets.has("repo-sample")).toBe(true);
    for (const [sheetName, nums] of sheets) {
      const expectedSeq = Array.from({ length: nums.length }, (_, i) => i + 1);
      expect(nums, `Non-contiguous sheet numbering in ${sheetName}`).toEqual(expectedSeq);
    }
  });

  it("exemptionSchema", () => {
    const exemptions = readJson<FormulaExemptionsFile>(EXEMPTIONS_PATH);
    expect(exemptions).toEqual({ version: 1, rows: [] });
    expect(validateExemptionSchema(exemptions)).toEqual([]);

    const baseRow = {
      latex: "x^2",
      display: "inline",
      app: "android",
      reason: "Unsupported macro",
      beyondCurriculum: false,
      origin: "catalogue",
      group: "M10-01",
      kind: "curriculum",
      approvalRef: "EX-01",
    };

    const badPayloads: [string, unknown][] = [
      ["missing reason", { version: 1, rows: [{ ...baseRow, reason: "" }] }],
      ["missing field beyondCurriculum", { version: 1, rows: [Object.fromEntries(Object.entries(baseRow).filter(([k]) => k !== "beyondCurriculum"))] }],
      ["extra field", { version: 1, rows: [{ ...baseRow, extraField: 123 }] }],
      ["invalid app enum", { version: 1, rows: [{ ...baseRow, app: "web" }] }],
      ["invalid display enum", { version: 1, rows: [{ ...baseRow, display: "centered" }] }],
      ["non-boolean beyondCurriculum", { version: 1, rows: [{ ...baseRow, beyondCurriculum: "no" }] }],
      ["invalid origin enum", { version: 1, rows: [{ ...baseRow, origin: "demo-bank" }] }],
      ["invalid kind enum", { version: 1, rows: [{ ...baseRow, kind: "advanced" }] }],
      ["wrong version", { version: 2, rows: [] }],
      ["extra root key", { version: 1, rows: [], extra: true }],
    ];

    for (const [label, payload] of badPayloads) {
      expect(validateExemptionSchema(payload).length, `Expected schema rejection for: ${label}`).toBeGreaterThan(0);
    }
  });

  it("exemptionRules", () => {
    const exemptions = readJson<FormulaExemptionsFile>(EXEMPTIONS_PATH);
    const parsedTable = parseApprovalTable(readFileSync(ACCEPTANCE_PATH, "utf8"));
    expect(validateExemptionRules(exemptions, formulasFile.entries, parsedTable.rows)).toEqual([]);

    const curriculumEntry = formulasFile.entries.find(
      (e) => e.origin === "catalogue" && e.kind === "curriculum" && e.katexRenders,
    )!;
    const syntaxEntry = formulasFile.entries.find(
      (e) => e.origin === "catalogue" && e.kind === "syntax" && e.katexRenders,
    )!;
    const repoSampleEntry = formulasFile.entries.find(
      (e) => e.origin === "repo-sample" && e.katexRenders,
    )!;
    const ungroupedRenderable = formulasFile.entries.find(
      (e) => e.origin === "catalogue" && e.group === null && e.katexRenders,
    )!;
    const nonRenderable = formulasFile.entries.find(
      (e) => e.origin === "catalogue" && !e.katexRenders,
    )!;

    const approvalRows: ApprovalTableRow[] = [
      {
        code: "EX-CURR-1",
        group: curriculumEntry.group,
        kind: curriculumEntry.kind,
        latex: curriculumEntry.latex,
        display: curriculumEntry.display,
        app: "android",
        reason: "Engine limitation",
        beyondCurriculum: "false",
        approvalQuote: "Đồng ý miễn công thức này",
        approver: "M.Trường",
        date: "2026-11-27",
      },
      {
        code: "EX-SYN-1",
        group: syntaxEntry.group,
        kind: syntaxEntry.kind,
        latex: syntaxEntry.latex,
        display: syntaxEntry.display,
        app: "ios",
        reason: "Rare syntax macro",
        beyondCurriculum: "true",
        approvalQuote: "Duyệt danh sách cú pháp",
        approver: "M.Trường",
        date: "2026-11-27",
      },
      {
        code: "EX-WRONG-FORMULA",
        group: curriculumEntry.group,
        kind: curriculumEntry.kind,
        latex: "a + b = c",
        display: curriculumEntry.display,
        app: "android",
        reason: "Different formula",
        beyondCurriculum: "false",
        approvalQuote: "Đồng ý công thức khác",
        approver: "M.Trường",
        date: "2026-11-27",
      },
    ];

    const validExemptions: FormulaExemptionsFile = {
      version: 1,
      rows: [
        {
          latex: curriculumEntry.latex,
          display: curriculumEntry.display,
          app: "android",
          reason: "Engine limitation",
          beyondCurriculum: false,
          origin: curriculumEntry.origin,
          group: curriculumEntry.group,
          kind: curriculumEntry.kind,
          approvalRef: "EX-CURR-1",
        },
        {
          latex: syntaxEntry.latex,
          display: syntaxEntry.display,
          app: "ios",
          reason: "Rare syntax macro",
          beyondCurriculum: true,
          origin: syntaxEntry.origin,
          group: syntaxEntry.group,
          kind: syntaxEntry.kind,
          approvalRef: "EX-SYN-1",
        },
      ],
    };
    expect(validateExemptionRules(validExemptions, formulasFile.entries, approvalRows)).toEqual([]);

    const badRuleCases: [string, FormulaExemptionsFile][] = [
      [
        "missing reason",
        {
          version: 1,
          rows: [{ ...validExemptions.rows[0]!, reason: "" }],
        },
      ],
      [
        "formula absent from formulas.json",
        {
          version: 1,
          rows: [{ ...validExemptions.rows[0]!, latex: "\\text{does-not-exist}" }],
        },
      ],
      [
        "katexRenders=false entry",
        {
          version: 1,
          rows: [
            {
              latex: nonRenderable.latex,
              display: nonRenderable.display,
              app: "android",
              reason: "Not renderable",
              beyondCurriculum: true,
              origin: nonRenderable.origin,
              group: nonRenderable.group,
              kind: nonRenderable.kind,
              approvalRef: "EX-CURR-1",
            },
          ],
        },
      ],
      [
        "group differs from formulas.json",
        {
          version: 1,
          rows: [{ ...validExemptions.rows[0]!, group: "M10-02" }],
        },
      ],
      [
        "kind differs from formulas.json",
        {
          version: 1,
          rows: [{ ...validExemptions.rows[0]!, kind: "syntax" }],
        },
      ],
      [
        "curriculum entry missing approvalRef in table",
        {
          version: 1,
          rows: [{ ...validExemptions.rows[0]!, approvalRef: "EX-MISSING" }],
        },
      ],
      [
        "curriculum entry pointing to approval row for a different formula",
        {
          version: 1,
          rows: [{ ...validExemptions.rows[0]!, approvalRef: "EX-WRONG-FORMULA" }],
        },
      ],
      [
        "repo-sample entry without matching approval row",
        {
          version: 1,
          rows: [
            {
              latex: repoSampleEntry.latex,
              display: repoSampleEntry.display,
              app: "android",
              reason: "Repo sample failure",
              beyondCurriculum: false,
              origin: "repo-sample",
              group: null,
              kind: null,
              approvalRef: "EX-MISSING",
            },
          ],
        },
      ],
      [
        "ungrouped entry without matching approval row",
        {
          version: 1,
          rows: [
            {
              latex: ungroupedRenderable.latex,
              display: ungroupedRenderable.display,
              app: "both",
              reason: "Ungrouped block formula",
              beyondCurriculum: true,
              origin: "catalogue",
              group: null,
              kind: null,
              approvalRef: "EX-MISSING",
            },
          ],
        },
      ],
      [
        "syntax entry missing approvalRef in table",
        {
          version: 1,
          rows: [{ ...validExemptions.rows[1]!, approvalRef: "EX-NOT-APPROVED" }],
        },
      ],
    ];

    for (const [label, payload] of badRuleCases) {
      expect(
        validateExemptionRules(payload, formulasFile.entries, approvalRows).length,
        `Expected rule rejection for: ${label}`,
      ).toBeGreaterThan(0);
    }
  });

  it("indexCounts", () => {
    const index = readJson<ReferenceIndexFile>(INDEX_PATH);
    expect(index.sources.richTextSamples.path).toBe("docs/specs/fixtures/rich-text-samples.json");
    expect(index.sources.questionSamples.path).toBe("apps/web-student/src/app/dev/question-kit/question-samples.json");
    expect(index.sources.formulaCatalogue.path).toBe("docs/specs/fixtures/formula-catalogue.json");

    const richText = readJson<{ samples: { valid: boolean; violations?: unknown[] }[] }>(
      path.join(REPO_ROOT, index.sources.richTextSamples.path),
    );
    const validRichText = richText.samples.filter((s) => s.valid);
    const invalidRichText = richText.samples.filter((s) => !s.valid);
    expect(validRichText).toHaveLength(12);
    expect(invalidRichText).toHaveLength(31);
    for (const invalid of invalidRichText) {
      expect(Array.isArray(invalid.violations) && invalid.violations.length > 0).toBe(true);
    }

    const questionSamples = readJson<{ samples: unknown[] }>(
      path.join(REPO_ROOT, index.sources.questionSamples.path),
    );
    expect(questionSamples.samples).toHaveLength(11);

    const cat = readJson<FormulaCatalogue>(path.join(REPO_ROOT, index.sources.formulaCatalogue.path));
    const groupCount = cat.parts.reduce((sum, p) => sum + p.groups.length, 0);
    expect(groupCount).toBe(225);
    expect(cat.counts.groups).toBe(225);
    expect(cat.counts.formulas).toBe(1915);
    expect(cat.counts.rejected).toBe(93);

    for (const artifactPath of Object.values(index.artifacts)) {
      expect(existsSync(path.join(REPO_ROOT, artifactPath)), `Missing artifact: ${artifactPath}`).toBe(true);
    }
  });

  it("wordCountMatchesWeb", () => {
    const wordCount = readJson<WordCountFile>(WORD_COUNT_PATH);
    expect(wordCount.cases).toHaveLength(30);

    const mismatches: string[] = [];
    for (const item of wordCount.cases) {
      const actual = countWords(item.html);
      if (actual !== item.expected) {
        mismatches.push(`${item.id}: file expected=${item.expected}, countWords=${actual}`);
      }
    }
    expect(mismatches).toEqual([]);
  });

  it("approvalTableReadable", () => {
    expect(existsSync(ACCEPTANCE_PATH)).toBe(true);
    const markdown = readFileSync(ACCEPTANCE_PATH, "utf8");
    const parsed = parseApprovalTable(markdown);
    expect(parsed.hasHeading).toBe(true);
    expect(parsed.hasPolicySentence).toBe(true);
    expect(parsed.columns).toEqual([...EXPECTED_APPROVAL_COLUMNS]);
    expect(parsed.errors).toEqual([]);
    expect(parsed.rows).toEqual([]);

    const exemptions = readJson<FormulaExemptionsFile>(EXEMPTIONS_PATH);
    expect(validateExemptionRules(exemptions, formulasFile.entries, parsed.rows)).toEqual([]);
  });

  it("webRendersMatchEntries", () => {
    const expectedPngs = formulasFile.entries
      .map((e) => e.sheetKey)
      .filter((k): k is string => k !== null)
      .map((k) => `${k}.png`);

    if (!SHEETS_MODE) {
      const actualPngs = listPngRelativePaths(WEB_RENDERS_DIR);
      expect(validateWebRenderFiles(formulasFile.entries, actualPngs)).toEqual([]);
    }

    const samplePng = expectedPngs[0]!;
    const missingErrors = validateWebRenderFiles(
      formulasFile.entries,
      expectedPngs.filter((p) => p !== samplePng),
    );
    expect(missingErrors.some((msg) => msg.includes(samplePng))).toBe(true);

    const extraPng = "M10-01/999.png";
    const extraErrors = validateWebRenderFiles(formulasFile.entries, [...expectedPngs, extraPng]);
    expect(extraErrors.some((msg) => msg.includes(extraPng))).toBe(true);

    const renamedPng = samplePng.replace(/\.png$/, "-renamed.png");
    const renamedErrors = validateWebRenderFiles(
      formulasFile.entries,
      expectedPngs.map((p) => (p === samplePng ? renamedPng : p)),
    );
    expect(renamedErrors.some((msg) => msg.includes(samplePng))).toBe(true);
    expect(renamedErrors.some((msg) => msg.includes(renamedPng))).toBe(true);
  });

  it("webRendersHashCurrent", () => {
    const manifest = readJson<WebRendersManifest>(MANIFEST_PATH);
    expect(validateWebRendersManifest(manifest, computedHashBySheetKey, installedKatexVersion)).toEqual([]);

    const firstKey = Object.keys(manifest.htmlSha256)[0]!;
    const staleManifest: WebRendersManifest = {
      katexVersion: manifest.katexVersion,
      htmlSha256: {
        ...manifest.htmlSha256,
        [firstKey]: "0".repeat(64),
      },
    };
    const staleErrors = validateWebRendersManifest(staleManifest, computedHashBySheetKey, installedKatexVersion);
    expect(staleErrors.some((msg) => msg.includes(firstKey) && msg.includes("formula-sheets.config.ts"))).toBe(true);

    const versionErrors = validateWebRendersManifest(manifest, computedHashBySheetKey, "0.99.0");
    expect(versionErrors.some((msg) => msg.includes("katexVersion") && msg.includes("formula-sheets.config.ts"))).toBe(true);
  });
});
