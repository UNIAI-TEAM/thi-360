import { buildCatalogueDraftEntries } from "./extract";
import type {
  ApprovalTableRow,
  ExemptionRow,
  FormulaCatalogue,
  FormulaEntry,
  FormulaExemptionsFile,
  WebRendersManifest,
} from "./types";

const MHCHEM_RE = /\\(?:ce|pu)(?![A-Za-z])/;
const VALID_DISPLAYS = new Set(["inline", "block"]);
const VALID_ORIGINS = new Set(["catalogue", "repo-sample"]);
const VALID_KINDS = new Set(["curriculum", "syntax"]);
const VALID_APPS = new Set(["android", "ios", "both"]);

const EXEMPTION_ROW_KEYS = [
  "latex",
  "display",
  "app",
  "reason",
  "beyondCurriculum",
  "origin",
  "group",
  "kind",
  "approvalRef",
] as const;

export const EXPECTED_APPROVAL_POLICY =
  "Công thức thuộc nhóm chương trình của danh mục (`kind = curriculum`), công thức mẫu có sẵn và công thức không có nhóm chỉ được miễn khi người yêu cầu đồng ý rõ cho đúng công thức đó. Công thức thuộc nhóm cú pháp (`kind = syntax`) được miễn khi dòng của nó ghi đủ lý do và người yêu cầu đã duyệt danh sách (D-8).";

export const EXPECTED_APPROVAL_COLUMNS = [
  "Mã",
  "Nhóm",
  "Loại nhóm",
  "Công thức",
  "Dạng",
  "App",
  "Lý do",
  "Vượt chương trình",
  "Câu đồng ý nguyên văn",
  "Người",
  "Ngày",
] as const;

export interface CatalogueDiscrepancy {
  group: string;
  latex: string;
  display: "inline" | "block";
  webRenders: boolean;
  catalogueExpected: boolean;
}

function entryCatalogueKey(entry: {
  group: string | null;
  rejectedArea: string | null;
  latex: string;
  display: string;
}): string {
  return `${entry.group ?? `rejected:${entry.rejectedArea ?? "null"}`}\u0000${entry.display}\u0000${entry.latex}`;
}

function formatLocator(group: string | null, rejectedArea: string | null, display: string, latex: string): string {
  const scope = group ?? `rejected(${rejectedArea ?? "null"})`;
  return `[group=${scope}, display=${display}, latex=${latex}]`;
}

export function validateCatalogueMatch(
  entries: readonly FormulaEntry[],
  catalogue: FormulaCatalogue,
): string[] {
  const errors: string[] = [];
  const catalogueEntries = entries.filter((entry) => entry.origin === "catalogue");
  const expectedDrafts = buildCatalogueDraftEntries(catalogue);

  const actualGroups = new Set(catalogueEntries.map((e) => e.group).filter((g): g is string => g !== null));
  const expectedGroups = new Set(expectedDrafts.map((e) => e.group).filter((g): g is string => g !== null));
  if (actualGroups.size !== 225) {
    errors.push(`Expected 225 catalogue groups, got ${actualGroups.size}`);
  }
  for (const g of expectedGroups) {
    if (!actualGroups.has(g)) errors.push(`Missing group in formulas.json: ${g}`);
  }
  for (const g of actualGroups) {
    if (!expectedGroups.has(g)) errors.push(`Unexpected group in formulas.json: ${g}`);
  }

  const expectedByKey = new Map<string, typeof expectedDrafts[number]>();
  for (const exp of expectedDrafts) {
    const key = entryCatalogueKey(exp);
    if (expectedByKey.has(key)) {
      errors.push(`Duplicate expected catalogue entry ${formatLocator(exp.group, exp.rejectedArea, exp.display, exp.latex)}`);
    }
    expectedByKey.set(key, exp);
  }

  const actualByKey = new Map<string, FormulaEntry>();
  for (const act of catalogueEntries) {
    const key = entryCatalogueKey(act);
    if (actualByKey.has(key)) {
      errors.push(`Duplicate entry in formulas.json ${formatLocator(act.group, act.rejectedArea, act.display, act.latex)}`);
    }
    actualByKey.set(key, act);
  }

  for (const [key, exp] of expectedByKey) {
    const act = actualByKey.get(key);
    if (!act) {
      errors.push(`Missing catalogue entry ${formatLocator(exp.group, exp.rejectedArea, exp.display, exp.latex)}`);
      continue;
    }
    if (act.kind !== exp.kind) {
      errors.push(
        `Kind mismatch at ${formatLocator(exp.group, exp.rejectedArea, exp.display, exp.latex)}: expected ${String(exp.kind)}, got ${String(act.kind)}`,
      );
    }
    if (act.rejectedArea !== exp.rejectedArea) {
      errors.push(
        `rejectedArea mismatch at ${formatLocator(exp.group, exp.rejectedArea, exp.display, exp.latex)}: expected ${String(exp.rejectedArea)}, got ${String(act.rejectedArea)}`,
      );
    }
  }

  for (const [key, act] of actualByKey) {
    if (!expectedByKey.has(key)) {
      errors.push(`Extra catalogue entry in formulas.json ${formatLocator(act.group, act.rejectedArea, act.display, act.latex)}`);
    }
  }

  if (catalogueEntries.length !== expectedDrafts.length) {
    errors.push(
      `Catalogue entry count mismatch: expected ${expectedDrafts.length}, got ${catalogueEntries.length}`,
    );
  }

  return errors;
}

export function validateCatalogueKatexCounts(
  entries: readonly FormulaEntry[],
  catalogue: FormulaCatalogue,
): { discrepancies: CatalogueDiscrepancy[]; errors: string[] } {
  const discrepancies: CatalogueDiscrepancy[] = [];
  const errors: string[] = [];

  const catalogueEntries = entries.filter((e) => e.origin === "catalogue");
  const groupedEntries = catalogueEntries.filter((e) => e.group !== null);
  const rejectedEntries = catalogueEntries.filter((e) => e.group === null);

  const inlineOnlyRejected = new Set(
    catalogue.rejected.filter((r) => r.inlineOnly === true).map((r) => `${r.area}\u0000${r.latex.trim()}`),
  );

  for (const entry of groupedEntries) {
    if (!entry.katexRenders) {
      discrepancies.push({
        group: entry.group!,
        latex: entry.latex,
        display: entry.display,
        webRenders: entry.katexRenders,
        catalogueExpected: true,
      });
    }
  }

  for (const entry of rejectedEntries) {
    const isInlineOnly = inlineOnlyRejected.has(`${entry.rejectedArea ?? ""}\u0000${entry.latex}`);
    const expected = entry.display === "block" && isInlineOnly;
    if (entry.katexRenders !== expected) {
      discrepancies.push({
        group: `rejected(${entry.rejectedArea ?? "null"})`,
        latex: entry.latex,
        display: entry.display,
        webRenders: entry.katexRenders,
        catalogueExpected: expected,
      });
    }
  }

  const groupBlockTrue = groupedEntries.filter((e) => e.display === "block" && e.katexRenders).length;
  const groupInlineTrue = groupedEntries.filter((e) => e.display === "inline" && e.katexRenders).length;
  const rejectedInlineFalse = rejectedEntries.filter((e) => e.display === "inline" && !e.katexRenders).length;
  const rejectedBlockFalse = rejectedEntries.filter((e) => e.display === "block" && !e.katexRenders).length;
  const rejectedBlockTrue = rejectedEntries.filter((e) => e.display === "block" && e.katexRenders).length;

  let mhchemCount = 0;
  for (const part of catalogue.parts) {
    for (const group of part.groups) {
      for (const formula of group.formulas) {
        if (MHCHEM_RE.test(formula.latex)) mhchemCount += 1;
      }
    }
  }

  if (groupBlockTrue !== catalogue.counts.formulas) {
    errors.push(`Grouped block katexRenders=true: expected ${catalogue.counts.formulas}, got ${groupBlockTrue}`);
  }
  const expectedGroupInline = catalogue.counts.formulas - catalogue.counts.blockOnly;
  if (groupInlineTrue !== expectedGroupInline) {
    errors.push(`Grouped inline katexRenders=true: expected ${expectedGroupInline}, got ${groupInlineTrue}`);
  }
  if (rejectedInlineFalse !== catalogue.counts.rejected) {
    errors.push(`Rejected inline katexRenders=false: expected ${catalogue.counts.rejected}, got ${rejectedInlineFalse}`);
  }
  const expectedRejectedBlockTrue = catalogue.rejected.filter((r) => r.inlineOnly === true).length;
  const expectedRejectedBlockFalse = catalogue.counts.rejected - expectedRejectedBlockTrue;
  if (rejectedBlockFalse !== expectedRejectedBlockFalse) {
    errors.push(`Rejected block katexRenders=false: expected ${expectedRejectedBlockFalse}, got ${rejectedBlockFalse}`);
  }
  if (rejectedBlockTrue !== expectedRejectedBlockTrue) {
    errors.push(`Rejected block katexRenders=true (inlineOnly): expected ${expectedRejectedBlockTrue}, got ${rejectedBlockTrue}`);
  }
  if (mhchemCount !== catalogue.counts.mhchemFormulas) {
    errors.push(`mhchem formula count: expected ${catalogue.counts.mhchemFormulas}, got ${mhchemCount}`);
  }

  return { discrepancies, errors };
}

export function validateKatexVersion(fileVersion: string, installedVersion: string): string | null {
  if (fileVersion !== installedVersion) {
    return (
      `KaTeX version mismatch: file records "${fileVersion}" but installed packages/ui katex is "${installedVersion}". ` +
      `Re-run with RICH_TEXT_REFERENCE_WRITE=1 pnpm --filter @thi360/ui test src/rich-text/__tests__/reference-set.test.ts`
    );
  }
  return null;
}

export function validateExemptionSchema(raw: unknown): string[] {
  const errors: string[] = [];
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) {
    return ["formula-exemptions.json root must be a JSON object"];
  }
  const obj = raw as Record<string, unknown>;
  const rootKeys = Object.keys(obj);
  for (const key of rootKeys) {
    if (key !== "version" && key !== "rows") {
      errors.push(`Unexpected root key "${key}" in formula-exemptions.json`);
    }
  }
  if (obj.version !== 1) {
    errors.push(`Expected version=1 in formula-exemptions.json, got ${JSON.stringify(obj.version)}`);
  }
  if (!Array.isArray(obj.rows)) {
    errors.push("formula-exemptions.json rows must be an array");
    return errors;
  }

  const allowedRowKeys = new Set<string>(EXEMPTION_ROW_KEYS);
  obj.rows.forEach((item, index) => {
    const prefix = `rows[${index}]`;
    if (!item || typeof item !== "object" || Array.isArray(item)) {
      errors.push(`${prefix} must be an object`);
      return;
    }
    const row = item as Record<string, unknown>;
    for (const requiredKey of EXEMPTION_ROW_KEYS) {
      if (!(requiredKey in row)) {
        errors.push(`${prefix} missing required field "${requiredKey}"`);
      }
    }
    for (const key of Object.keys(row)) {
      if (!allowedRowKeys.has(key)) {
        errors.push(`${prefix} has unexpected field "${key}"`);
      }
    }
    if (typeof row.latex !== "string" || row.latex.trim().length === 0 || row.latex !== row.latex.trim()) {
      errors.push(`${prefix}.latex must be a trimmed non-empty string`);
    }
    if (typeof row.display !== "string" || !VALID_DISPLAYS.has(row.display)) {
      errors.push(`${prefix}.display must be "inline" or "block"`);
    }
    if (typeof row.app !== "string" || !VALID_APPS.has(row.app)) {
      errors.push(`${prefix}.app must be "android", "ios", or "both"`);
    }
    if (typeof row.reason !== "string" || row.reason.trim().length === 0) {
      errors.push(`${prefix}.reason must be a non-empty string`);
    }
    if (typeof row.beyondCurriculum !== "boolean") {
      errors.push(`${prefix}.beyondCurriculum must be a boolean`);
    }
    if (typeof row.origin !== "string" || !VALID_ORIGINS.has(row.origin)) {
      errors.push(`${prefix}.origin must be "catalogue" or "repo-sample"`);
    }
    if (row.group !== null && (typeof row.group !== "string" || row.group.trim().length === 0)) {
      errors.push(`${prefix}.group must be a non-empty string or null`);
    }
    if (row.kind !== null && (typeof row.kind !== "string" || !VALID_KINDS.has(row.kind))) {
      errors.push(`${prefix}.kind must be "curriculum", "syntax", or null`);
    }
    if (typeof row.approvalRef !== "string" || row.approvalRef.trim().length === 0) {
      errors.push(`${prefix}.approvalRef must be a non-empty string`);
    }
  });

  return errors;
}

function splitMarkdownRow(line: string): string[] {
  const trimmed = line.trim().replace(/^\|/, "").replace(/\|$/, "");
  const cells: string[] = [];
  let current = "";
  for (let i = 0; i < trimmed.length; i += 1) {
    const ch = trimmed[i]!;
    if (ch === "\\" && trimmed[i + 1] === "|") {
      current += "|";
      i += 1;
      continue;
    }
    if (ch === "|") {
      cells.push(current.trim());
      current = "";
      continue;
    }
    current += ch;
  }
  cells.push(current.trim());
  return cells;
}

function unwrapMarkdownCell(cell: string): string {
  const trimmed = cell.trim();
  if (trimmed.length >= 2 && trimmed.startsWith("`") && trimmed.endsWith("`")) {
    return trimmed.slice(1, -1).trim();
  }
  return trimmed;
}

function parseNullableCell(cell: string): string | null {
  const unwrapped = unwrapMarkdownCell(cell);
  if (unwrapped === "" || unwrapped === "-" || unwrapped === "—" || unwrapped === "null") {
    return null;
  }
  return unwrapped;
}

export function parseApprovalTable(markdown: string): {
  hasHeading: boolean;
  hasPolicySentence: boolean;
  columns: string[];
  rows: ApprovalTableRow[];
  errors: string[];
} {
  const lines = markdown.replace(/\r\n/g, "\n").split("\n");
  const headingIndex = lines.findIndex((line) => /^##\s+Miễn trừ công thức \(D-8\)\s*$/.test(line.trim()));
  if (headingIndex === -1) {
    return {
      hasHeading: false,
      hasPolicySentence: false,
      columns: [],
      rows: [],
      errors: ['Missing heading "## Miễn trừ công thức (D-8)"'],
    };
  }

  let sectionEnd = lines.length;
  for (let i = headingIndex + 1; i < lines.length; i += 1) {
    if (/^##\s+/.test(lines[i]!.trim())) {
      sectionEnd = i;
      break;
    }
  }

  const sectionLines = lines.slice(headingIndex + 1, sectionEnd);
  const sectionText = sectionLines.join("\n");
  const hasPolicySentence = sectionText.includes(EXPECTED_APPROVAL_POLICY);

  const tableLines = sectionLines.filter((line) => line.trim().startsWith("|"));
  const errors: string[] = [];
  if (!hasPolicySentence) {
    errors.push('Missing required D-8 policy sentence in section "Miễn trừ công thức (D-8)"');
  }
  if (tableLines.length < 2) {
    errors.push("Missing markdown approval table header/separator");
    return { hasHeading: true, hasPolicySentence, columns: [], rows: [], errors };
  }

  const columns = splitMarkdownRow(tableLines[0]!);
  if (
    columns.length !== EXPECTED_APPROVAL_COLUMNS.length ||
    EXPECTED_APPROVAL_COLUMNS.some((col, idx) => columns[idx] !== col)
  ) {
    errors.push(
      `Approval table columns mismatch: expected [${EXPECTED_APPROVAL_COLUMNS.join(", ")}], got [${columns.join(", ")}]`,
    );
  }

  const rows: ApprovalTableRow[] = [];
  for (let i = 2; i < tableLines.length; i += 1) {
    const cells = splitMarkdownRow(tableLines[i]!);
    if (cells.length !== EXPECTED_APPROVAL_COLUMNS.length) {
      errors.push(`Approval table row ${i - 1} has ${cells.length} cells, expected ${EXPECTED_APPROVAL_COLUMNS.length}`);
      continue;
    }
    rows.push({
      code: unwrapMarkdownCell(cells[0]!),
      group: parseNullableCell(cells[1]!),
      kind: parseNullableCell(cells[2]!),
      latex: unwrapMarkdownCell(cells[3]!),
      display: unwrapMarkdownCell(cells[4]!),
      app: unwrapMarkdownCell(cells[5]!),
      reason: unwrapMarkdownCell(cells[6]!),
      beyondCurriculum: unwrapMarkdownCell(cells[7]!),
      approvalQuote: unwrapMarkdownCell(cells[8]!),
      approver: unwrapMarkdownCell(cells[9]!),
      date: unwrapMarkdownCell(cells[10]!),
    });
  }

  return { hasHeading: true, hasPolicySentence, columns, rows, errors };
}

export function validateExemptionRules(
  exemptions: FormulaExemptionsFile,
  formulas: readonly FormulaEntry[],
  approvalRows: readonly ApprovalTableRow[],
): string[] {
  const schemaErrors = validateExemptionSchema(exemptions);
  if (schemaErrors.length > 0) return schemaErrors;

  const errors: string[] = [];
  const exactMap = new Map<string, FormulaEntry>();
  const byOriginLatexDisplay = new Map<string, FormulaEntry[]>();

  for (const entry of formulas) {
    const exactKey = `${entry.origin}\u0000${entry.group ?? "null"}\u0000${entry.display}\u0000${entry.latex}`;
    exactMap.set(exactKey, entry);
    const looseKey = `${entry.origin}\u0000${entry.display}\u0000${entry.latex}`;
    const list = byOriginLatexDisplay.get(looseKey) ?? [];
    list.push(entry);
    byOriginLatexDisplay.set(looseKey, list);
  }

  const approvalsByCode = new Map<string, ApprovalTableRow>();
  for (const row of approvalRows) {
    approvalsByCode.set(row.code, row);
  }

  exemptions.rows.forEach((row: ExemptionRow, index: number) => {
    const prefix = `exemption[${index}] (${row.origin}/${row.group ?? "null"}/${row.display}/${row.latex})`;
    const exactKey = `${row.origin}\u0000${row.group ?? "null"}\u0000${row.display}\u0000${row.latex}`;
    const matched = exactMap.get(exactKey);

    if (!matched) {
      const looseKey = `${row.origin}\u0000${row.display}\u0000${row.latex}`;
      const candidates = byOriginLatexDisplay.get(looseKey);
      if (candidates && candidates.length > 0) {
        errors.push(
          `${prefix}: group "${String(row.group)}" does not match formulas.json groups [${candidates.map((c) => String(c.group)).join(", ")}]`,
        );
        return;
      }
      errors.push(`${prefix}: formula not found in formulas.json`);
      return;
    }

    if (!matched.katexRenders) {
      errors.push(`${prefix}: cannot exempt a formula where katexRenders=false`);
    }
    if (row.group !== matched.group) {
      errors.push(`${prefix}: group mismatch (expected ${String(matched.group)}, got ${String(row.group)})`);
    }
    if (row.kind !== matched.kind) {
      errors.push(`${prefix}: kind mismatch (expected ${String(matched.kind)}, got ${String(row.kind)})`);
    }

    const approval = approvalsByCode.get(row.approvalRef);
    if (!approval) {
      errors.push(`${prefix}: approvalRef "${row.approvalRef}" not found in approval table`);
      return;
    }
    if (!approval.approvalQuote || !approval.approver || !approval.date) {
      errors.push(`${prefix}: approval row "${row.approvalRef}" is missing approval quote, approver, or date`);
    }
    if (approval.group !== row.group || approval.kind !== row.kind) {
      errors.push(
        `${prefix}: approval row "${row.approvalRef}" has group=${String(approval.group)} kind=${String(approval.kind)}, expected group=${String(row.group)} kind=${String(row.kind)}`,
      );
    }
    if (approval.latex !== row.latex) {
      errors.push(
        `${prefix}: approval row "${row.approvalRef}" formula "${approval.latex}" does not match "${row.latex}"`,
      );
    }
  });

  return errors;
}

export function validateWebRenderFiles(
  entries: readonly FormulaEntry[],
  actualRelativePngPaths: readonly string[],
): string[] {
  const expected = new Set(
    entries
      .map((entry) => entry.sheetKey)
      .filter((key): key is string => key !== null)
      .map((key) => `${key}.png`),
  );
  const actual = new Set(actualRelativePngPaths.map((p) => p.replace(/\\/g, "/")));
  const errors: string[] = [];

  for (const rel of expected) {
    if (!actual.has(rel)) {
      errors.push(`Missing web-render PNG: ${rel}`);
    }
  }
  for (const rel of actual) {
    if (!expected.has(rel)) {
      errors.push(`Unexpected web-render PNG: ${rel}`);
    }
  }
  return errors;
}

export function validateWebRendersManifest(
  manifest: WebRendersManifest,
  expectedHashes: ReadonlyMap<string, string>,
  installedKatexVersion: string,
): string[] {
  const errors: string[] = [];
  const recaptureHint =
    "Re-run RICH_TEXT_REFERENCE_SHEETS=1 pnpm --filter @thi360/ui test src/rich-text/__tests__/reference-set.test.ts " +
    "and pnpm --filter @thi360/e2e exec playwright test -c formula-sheets.config.ts";

  if (manifest.katexVersion !== installedKatexVersion) {
    errors.push(
      `web-renders/manifest.json katexVersion="${manifest.katexVersion}" !== installed "${installedKatexVersion}". ${recaptureHint}`,
    );
  }

  const manifestKeys = new Set(Object.keys(manifest.htmlSha256));
  for (const [sheetKey, expectedHash] of expectedHashes) {
    const actualHash = manifest.htmlSha256[sheetKey];
    if (!actualHash) {
      errors.push(`Missing htmlSha256 for "${sheetKey}" in web-renders/manifest.json. ${recaptureHint}`);
    } else if (actualHash !== expectedHash) {
      errors.push(
        `Stale htmlSha256 for "${sheetKey}": manifest=${actualHash}, computed=${expectedHash}. ${recaptureHint}`,
      );
    }
  }
  for (const key of manifestKeys) {
    if (!expectedHashes.has(key)) {
      errors.push(`Extra htmlSha256 key "${key}" in web-renders/manifest.json. ${recaptureHint}`);
    }
  }

  return errors;
}
