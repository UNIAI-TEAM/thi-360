import { createHash } from "node:crypto";
import type { LegalBlock, LegalDocument, LegalDocumentId, LegalDocumentLocale, LegalDocumentStatus } from "./legal-document";

// Unicode escapes keep Vietnamese protocol markers ASCII-safe for web-student source-scan.test.ts.
const VERSION_HEADER_RE = /^>\s*Phi\u00ean b\u1ea3n:\s*(.*)$/;
const STATUS_HEADER_RE = /^>\s*Tr\u1ea1ng th\u00e1i:\s*(.*)$/;
const EFFECTIVE_DATE_LINE_RE = /^C\u00f3 hi\u1ec7u l\u1ef1c t\u1eeb:\s*(.*)$/;
const EFFECTIVE_DATE_PLACEHOLDER = "[NG\u00c0Y C\u00d3 HI\u1ec6U L\u1ef0C]";
const OPEN_POINTS_MARKER = "\u0110i\u1ec3m c\u1ea7n \u0111\u01a1n v\u1ecb v\u1eadn h\u00e0nh x\u00e1c nh\u1eadn";
const MUST_BUMP_VERSION_MSG = "ph\u1ea3i t\u0103ng phi\u00ean b\u1ea3n";

const VERSION_PATTERN = /^[0-9]{4}-[0-9]{2}-[0-9]{2}(-draft-[0-9]+)?$/;
const VALID_STATUSES = new Set<LegalDocumentStatus>(["draft", "published"]);

export const LEGAL_SOURCES = [
  { id: "terms", sourceFile: "thi360-dieu-kien-giao-dich-chung.md" },
  { id: "privacy", sourceFile: "thi360-chinh-sach-bao-mat-thong-tin.md" },
] as const satisfies readonly { readonly id: LegalDocumentId; readonly sourceFile: string }[];

export interface ParseLegalMarkdownOptions {
  readonly id: LegalDocumentId;
  readonly locale?: LegalDocumentLocale;
  readonly sourcePath?: string;
  readonly existingDoc?: Pick<LegalDocument, "version" | "contentHash">;
}

function canonicalizeBlock(block: LegalBlock): LegalBlock {
  switch (block.type) {
    case "heading":
      return { type: "heading", level: block.level, text: block.text };
    case "paragraph":
      return { type: "paragraph", text: block.text };
    case "list":
      return { type: "list", ordered: block.ordered, items: [...block.items] };
    case "table":
      return {
        type: "table",
        headers: [...block.headers],
        rows: block.rows.map((row) => [...row]),
      };
  }
}

export function computeLegalContentHash(title: string, blocks: readonly LegalBlock[]): string {
  const canonicalPayload = JSON.stringify({
    title,
    blocks: blocks.map(canonicalizeBlock),
  });
  return createHash("sha256").update(canonicalPayload, "utf8").digest("hex");
}

function isValidIsoDate(year: number, month: number, day: number): boolean {
  const date = new Date(Date.UTC(year, month - 1, day));
  return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day;
}

function parseDmyToIso(raw: string, sourceLabel: string, lineNo: number): string | null {
  const trimmed = raw.trim();
  if (!trimmed || trimmed === EFFECTIVE_DATE_PLACEHOLDER) {
    return null;
  }
  const match = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(trimmed);
  if (!match) {
    throw new Error(`${sourceLabel}:${lineNo}: invalid effective date "${trimmed}" (expected DD/MM/YYYY or ${EFFECTIVE_DATE_PLACEHOLDER})`);
  }
  const day = Number(match[1]);
  const month = Number(match[2]);
  const year = Number(match[3]);
  if (!isValidIsoDate(year, month, day)) {
    throw new Error(`${sourceLabel}:${lineNo}: impossible calendar date "${trimmed}"`);
  }
  return `${match[3]}-${match[2]}-${match[1]}`;
}

function splitTableRow(line: string): string[] {
  const trimmed = line.trim();
  const inner = trimmed.replace(/^\|/, "").replace(/\|$/, "");
  return inner.split("|").map((cell) => cell.trim());
}

function isTableSeparatorLine(line: string): boolean {
  const trimmed = line.trim();
  if (!trimmed.startsWith("|") || !trimmed.endsWith("|")) return false;
  const cells = splitTableRow(trimmed);
  return cells.length > 0 && cells.every((cell) => /^:?-{3,}:?$/.test(cell));
}

function isTableRowLine(line: string): boolean {
  const trimmed = line.trim();
  return trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.length > 2;
}

export function parseLegalMarkdown(markdown: string, options: ParseLegalMarkdownOptions): LegalDocument {
  const sourceLabel = options.sourcePath ?? `${options.id}.md`;
  const locale = options.locale ?? "vi";
  const lines = markdown.replace(/\r\n/g, "\n").split("\n");

  let index = 0;
  while (index < lines.length && lines[index]!.trim() === "") {
    index += 1;
  }

  const titleLineNo = index + 1;
  const titleLine = lines[index]?.trim() ?? "";
  if (!titleLine.startsWith("# ") || titleLine.startsWith("## ")) {
    throw new Error(`${sourceLabel}:${titleLineNo}: expected H1 title line starting with "# "`);
  }
  const title = titleLine.slice(2).trim();
  if (!title) {
    throw new Error(`${sourceLabel}:${titleLineNo}: title cannot be empty`);
  }
  index += 1;

  while (index < lines.length && lines[index]!.trim() === "") {
    index += 1;
  }

  const headerStartLineNo = index + 1;
  let version: string | null = null;
  let versionLineNo: number | null = null;
  let statusRaw: string | null = null;
  let statusLineNo: number | null = null;

  while (index < lines.length && lines[index]!.trimStart().startsWith(">")) {
    const lineNo = index + 1;
    const line = lines[index]!.trim();
    const versionMatch = VERSION_HEADER_RE.exec(line);
    if (versionMatch) {
      version = versionMatch[1]!.trim();
      versionLineNo = lineNo;
    }
    const statusMatch = STATUS_HEADER_RE.exec(line);
    if (statusMatch) {
      statusRaw = statusMatch[1]!.trim();
      statusLineNo = lineNo;
    }
    index += 1;
  }

  if (version === null) {
    throw new Error(`${sourceLabel}:${headerStartLineNo}: missing version header line in leading blockquote`);
  }
  if (!VERSION_PATTERN.test(version) || version.length >= 64) {
    throw new Error(
      `${sourceLabel}:${versionLineNo ?? headerStartLineNo}: invalid version "${version}" (expected YYYY-MM-DD or YYYY-MM-DD-draft-N, under 64 chars)`,
    );
  }

  if (statusRaw === null) {
    throw new Error(`${sourceLabel}:${headerStartLineNo}: missing status header line in leading blockquote`);
  }
  if (!VALID_STATUSES.has(statusRaw as LegalDocumentStatus)) {
    throw new Error(
      `${sourceLabel}:${statusLineNo ?? headerStartLineNo}: invalid status "${statusRaw}" (expected draft or published)`,
    );
  }
  const status = statusRaw as LegalDocumentStatus;

  let effectiveDate: string | null = null;
  let effectiveDateLineNo: number | null = null;
  const blocks: LegalBlock[] = [];
  const openPointsMarkerLower = OPEN_POINTS_MARKER.toLocaleLowerCase("vi");

  while (index < lines.length) {
    const rawLine = lines[index]!;
    const trimmed = rawLine.trim();
    const lineNo = index + 1;

    if (!trimmed) {
      index += 1;
      continue;
    }

    if (trimmed.toLocaleLowerCase("vi").includes(openPointsMarkerLower) && status === "published") {
      throw new Error(`${sourceLabel}:${lineNo}: published document cannot contain open confirmation points section`);
    }

    const effectiveMatch = EFFECTIVE_DATE_LINE_RE.exec(trimmed);
    if (effectiveMatch) {
      effectiveDate = parseDmyToIso(effectiveMatch[1]!, sourceLabel, lineNo);
      effectiveDateLineNo = lineNo;
      index += 1;
      continue;
    }

    if (trimmed.startsWith("## ")) {
      blocks.push({ type: "heading", level: 2, text: trimmed.slice(3).trim() });
      index += 1;
      continue;
    }

    if (trimmed.startsWith("### ")) {
      blocks.push({ type: "heading", level: 3, text: trimmed.slice(4).trim() });
      index += 1;
      continue;
    }

    if (trimmed.startsWith("#")) {
      throw new Error(`${sourceLabel}:${lineNo}: unsupported heading level "${trimmed}"`);
    }

    if (trimmed.startsWith("- ")) {
      const items: string[] = [];
      while (index < lines.length && lines[index]!.trim().startsWith("- ")) {
        const itemLineNo = index + 1;
        const itemText = lines[index]!.trim().slice(2).trim();
        if (status === "published" && itemText.toLocaleLowerCase("vi").includes(openPointsMarkerLower)) {
          throw new Error(`${sourceLabel}:${itemLineNo}: published document cannot contain open confirmation points section`);
        }
        items.push(itemText);
        index += 1;
      }
      blocks.push({ type: "list", ordered: false, items });
      continue;
    }

    if (/^\d+\.\s+/.test(trimmed)) {
      const items: string[] = [];
      while (index < lines.length && /^\d+\.\s+/.test(lines[index]!.trim())) {
        const itemLineNo = index + 1;
        const itemText = lines[index]!.trim().replace(/^\d+\.\s+/, "").trim();
        if (status === "published" && itemText.toLocaleLowerCase("vi").includes(openPointsMarkerLower)) {
          throw new Error(`${sourceLabel}:${itemLineNo}: published document cannot contain open confirmation points section`);
        }
        items.push(itemText);
        index += 1;
      }
      blocks.push({ type: "list", ordered: true, items });
      continue;
    }

    if (isTableRowLine(trimmed) && index + 1 < lines.length && isTableSeparatorLine(lines[index + 1]!)) {
      const headers = splitTableRow(trimmed);
      const headerLineNo = lineNo;
      index += 2;
      const rows: string[][] = [];
      while (index < lines.length && isTableRowLine(lines[index]!)) {
        const rowLineNo = index + 1;
        const row = splitTableRow(lines[index]!);
        if (row.length !== headers.length) {
          throw new Error(
            `${sourceLabel}:${rowLineNo}: table row has ${row.length} column(s), expected ${headers.length} (header at line ${headerLineNo})`,
          );
        }
        rows.push(row);
        index += 1;
      }
      blocks.push({ type: "table", headers, rows });
      continue;
    }

    const paragraphParts: string[] = [];
    while (index < lines.length) {
      const nextTrimmed = lines[index]!.trim();
      if (
        !nextTrimmed ||
        nextTrimmed.startsWith("#") ||
        nextTrimmed.startsWith("- ") ||
        /^\d+\.\s+/.test(nextTrimmed) ||
        EFFECTIVE_DATE_LINE_RE.test(nextTrimmed) ||
        (isTableRowLine(nextTrimmed) && index + 1 < lines.length && isTableSeparatorLine(lines[index + 1]!))
      ) {
        break;
      }
      if (status === "published" && nextTrimmed.toLocaleLowerCase("vi").includes(openPointsMarkerLower)) {
        throw new Error(`${sourceLabel}:${index + 1}: published document cannot contain open confirmation points section`);
      }
      paragraphParts.push(nextTrimmed);
      index += 1;
    }
    blocks.push({ type: "paragraph", text: paragraphParts.join(" ") });
  }

  if (status === "published" && effectiveDate === null) {
    throw new Error(
      `${sourceLabel}:${effectiveDateLineNo ?? headerStartLineNo}: published document requires a real effective date (DD/MM/YYYY)`,
    );
  }

  const contentHash = computeLegalContentHash(title, blocks);

  if (options.existingDoc && options.existingDoc.contentHash !== contentHash && options.existingDoc.version === version) {
    throw new Error(
      `${sourceLabel}:${versionLineNo ?? headerStartLineNo}: content changed while version stayed "${version}" (${MUST_BUMP_VERSION_MSG})`,
    );
  }

  return {
    id: options.id,
    locale,
    version,
    status,
    contentHash,
    effectiveDate,
    title,
    blocks,
  };
}
