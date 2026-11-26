// @vitest-environment node
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";
import {
  ENGLISH_LEGAL_TRANSLATION_NOTE,
  type LegalBlock,
  type LegalDocument,
  type LegalDocumentId,
} from "./legal-document";
import { computeLegalContentHash, LEGAL_SOURCES, parseLegalMarkdown } from "./parse-legal-markdown";

const REPO_ROOT = path.resolve(import.meta.dirname, "../../../../..");
const DOCS_LEGAL_DIR = path.join(REPO_ROOT, "docs/legal");
const CONTRACTS_LEGAL_DIR = path.join(REPO_ROOT, "contracts/mobile/legal");

function readJson(filePath: string): LegalDocument {
  return JSON.parse(readFileSync(filePath, "utf8")) as LegalDocument;
}

function serializeDocument(doc: LegalDocument): string {
  return `${JSON.stringify(doc, null, 2)}\n`;
}

function assertEnMirrorsVi(viDoc: LegalDocument, enDoc: LegalDocument): string[] {
  const errors: string[] = [];
  if (enDoc.id !== viDoc.id) errors.push(`id mismatch: en=${enDoc.id}, vi=${viDoc.id}`);
  if (enDoc.locale !== "en") errors.push(`locale must be "en", got ${enDoc.locale}`);
  if (enDoc.version !== viDoc.version) errors.push(`version mismatch: en=${enDoc.version}, vi=${viDoc.version}`);
  if (enDoc.status !== viDoc.status) errors.push(`status mismatch: en=${enDoc.status}, vi=${viDoc.status}`);
  if (enDoc.effectiveDate !== viDoc.effectiveDate) {
    errors.push(`effectiveDate mismatch: en=${enDoc.effectiveDate}, vi=${viDoc.effectiveDate}`);
  }
  if (enDoc.note !== ENGLISH_LEGAL_TRANSLATION_NOTE) {
    errors.push(`note mismatch: expected "${ENGLISH_LEGAL_TRANSLATION_NOTE}", got "${enDoc.note ?? ""}"`);
  }
  const expectedHash = computeLegalContentHash(enDoc.title, enDoc.blocks);
  if (enDoc.contentHash !== expectedHash) {
    errors.push(`en contentHash mismatch: file=${enDoc.contentHash}, computed=${expectedHash}`);
  }
  if (enDoc.blocks.length !== viDoc.blocks.length) {
    errors.push(`block count mismatch: en=${enDoc.blocks.length}, vi=${viDoc.blocks.length}`);
    return errors;
  }
  for (let i = 0; i < viDoc.blocks.length; i += 1) {
    const viBlock = viDoc.blocks[i]!;
    const enBlock = enDoc.blocks[i]!;
    if (enBlock.type !== viBlock.type) {
      errors.push(`block[${i}] type mismatch: en=${enBlock.type}, vi=${viBlock.type}`);
      continue;
    }
    if (viBlock.type === "heading" && enBlock.type === "heading") {
      if (enBlock.level !== viBlock.level) {
        errors.push(`block[${i}] heading level mismatch: en=${enBlock.level}, vi=${viBlock.level}`);
      }
      if (!enBlock.text.trim()) errors.push(`block[${i}] heading text is empty`);
    } else if (viBlock.type === "paragraph" && enBlock.type === "paragraph") {
      if (!enBlock.text.trim()) errors.push(`block[${i}] paragraph text is empty`);
    } else if (viBlock.type === "list" && enBlock.type === "list") {
      if (enBlock.ordered !== viBlock.ordered) {
        errors.push(`block[${i}] list ordered mismatch: en=${enBlock.ordered}, vi=${viBlock.ordered}`);
      }
      if (enBlock.items.length !== viBlock.items.length) {
        errors.push(`block[${i}] list items count mismatch: en=${enBlock.items.length}, vi=${viBlock.items.length}`);
      }
    } else if (viBlock.type === "table" && enBlock.type === "table") {
      if (enBlock.headers.length !== viBlock.headers.length) {
        errors.push(`block[${i}] table headers count mismatch: en=${enBlock.headers.length}, vi=${viBlock.headers.length}`);
      }
      if (enBlock.rows.length !== viBlock.rows.length) {
        errors.push(`block[${i}] table rows count mismatch: en=${enBlock.rows.length}, vi=${viBlock.rows.length}`);
      }
      for (let r = 0; r < Math.min(viBlock.rows.length, enBlock.rows.length); r += 1) {
        if (enBlock.rows[r]!.length !== viBlock.rows[r]!.length) {
          errors.push(
            `block[${i}] table row[${r}] col count mismatch: en=${enBlock.rows[r]!.length}, vi=${viBlock.rows[r]!.length}`,
          );
        }
      }
    }
  }
  return errors;
}

function diffBlocks(expected: readonly LegalBlock[], actual: readonly LegalBlock[]): string[] {
  const diffs: string[] = [];
  const max = Math.max(expected.length, actual.length);
  for (let i = 0; i < max; i += 1) {
    if (JSON.stringify(expected[i]) !== JSON.stringify(actual[i])) {
      diffs.push(
        `block[${i}] differs:\n  expected: ${JSON.stringify(expected[i])}\n  actual:   ${JSON.stringify(actual[i])}`,
      );
    }
  }
  return diffs;
}

function generateFromSources(docsDir: string, writeOutput: boolean): Record<LegalDocumentId, LegalDocument> {
  const generated = {} as Record<LegalDocumentId, LegalDocument>;
  for (const { id, sourceFile } of LEGAL_SOURCES) {
    const sourcePath = path.join(docsDir, sourceFile);
    const outPath = path.join(CONTRACTS_LEGAL_DIR, `${id}.vi.json`);
    const existingDoc = existsSync(outPath) ? readJson(outPath) : undefined;
    const parsed = parseLegalMarkdown(readFileSync(sourcePath, "utf8"), {
      id,
      locale: "vi",
      sourcePath: `docs/legal/${sourceFile}`,
      existingDoc,
    });
    generated[id] = parsed;
    if (writeOutput) {
      mkdirSync(CONTRACTS_LEGAL_DIR, { recursive: true });
      writeFileSync(outPath, serializeDocument(parsed), "utf8");
    }
  }
  return generated;
}

describe("legal-documents generator and contract parity", () => {
  it("viMatchesSource", () => {
    const shouldWrite = process.env.LEGAL_WRITE === "1";
    const generated = generateFromSources(DOCS_LEGAL_DIR, shouldWrite);

    for (const { id } of LEGAL_SOURCES) {
      const outPath = path.join(CONTRACTS_LEGAL_DIR, `${id}.vi.json`);
      const committed = readJson(outPath);
      const expected = generated[id];

      const blockDiffs = diffBlocks(expected.blocks, committed.blocks);
      expect(blockDiffs, `${id}.vi.json blocks drifted from docs/legal/`).toEqual([]);
      expect(committed).toEqual(expected);
      expect(readFileSync(outPath, "utf8")).toBe(serializeDocument(expected));
      expect(committed.version).toBe("2026-11-26");
      expect(committed.status).toBe("published");
      expect(committed.effectiveDate).toBe("2026-11-26");
    }

    expect(existsSync(path.join(DOCS_LEGAL_DIR, "README.md"))).toBe(true);
    expect(existsSync(path.join(CONTRACTS_LEGAL_DIR, "readme.vi.json"))).toBe(false);
    expect(existsSync(path.join(CONTRACTS_LEGAL_DIR, "README.json"))).toBe(false);

    const secondRun = generateFromSources(DOCS_LEGAL_DIR, false);
    for (const { id } of LEGAL_SOURCES) {
      expect(serializeDocument(secondRun[id])).toBe(
        readFileSync(path.join(CONTRACTS_LEGAL_DIR, `${id}.vi.json`), "utf8"),
      );
    }
  });

  it("enMirrorsVi", () => {
    for (const { id } of LEGAL_SOURCES) {
      const viDoc = readJson(path.join(CONTRACTS_LEGAL_DIR, `${id}.vi.json`));
      const enDoc = readJson(path.join(CONTRACTS_LEGAL_DIR, `${id}.en.json`));

      expect(assertEnMirrorsVi(viDoc, enDoc), `${id}.en.json must mirror ${id}.vi.json`).toEqual([]);

      const missingBlockDoc: LegalDocument = {
        ...enDoc,
        blocks: enDoc.blocks.slice(0, -1),
        contentHash: computeLegalContentHash(enDoc.title, enDoc.blocks.slice(0, -1)),
      };
      expect(assertEnMirrorsVi(viDoc, missingBlockDoc).join("\n")).toContain("block count mismatch");

      const extraBlocks = [...enDoc.blocks, { type: "paragraph" as const, text: "Extra paragraph" }];
      const extraBlockDoc: LegalDocument = {
        ...enDoc,
        blocks: extraBlocks,
        contentHash: computeLegalContentHash(enDoc.title, extraBlocks),
      };
      expect(assertEnMirrorsVi(viDoc, extraBlockDoc).join("\n")).toContain("block count mismatch");
    }
  });

  it("headerFieldsRequired", () => {
    const validDraft = [
      "# Văn bản thử nghiệm",
      "",
      "> Phiên bản: 2026-12-01-draft-1",
      "> Trạng thái: draft",
      "",
      "Có hiệu lực từ: [NGÀY CÓ HIỆU LỰC]",
      "",
      "Nội dung thử nghiệm.",
    ].join("\n");

    const parsedDraft = parseLegalMarkdown(validDraft, { id: "terms", sourcePath: "docs/legal/sample.md" });
    expect(parsedDraft.version).toBe("2026-12-01-draft-1");
    expect(parsedDraft.status).toBe("draft");
    expect(parsedDraft.effectiveDate).toBeNull();

    const missingVersion = "# Văn bản\n\n> Trạng thái: draft\n\nNội dung.\n";
    expect(() =>
      parseLegalMarkdown(missingVersion, { id: "terms", sourcePath: "docs/legal/missing-version.md" }),
    ).toThrow(/docs\/legal\/missing-version\.md:3/);

    const missingStatus = "# Văn bản\n\n> Phiên bản: 2026-11-26\n\nNội dung.\n";
    expect(() =>
      parseLegalMarkdown(missingStatus, { id: "terms", sourcePath: "docs/legal/missing-status.md" }),
    ).toThrow(/docs\/legal\/missing-status\.md:3/);

    const badVersion = "# Văn bản\n\n> Phiên bản: 2026-11\n> Trạng thái: draft\n\nNội dung.\n";
    expect(() =>
      parseLegalMarkdown(badVersion, { id: "terms", sourcePath: "docs/legal/bad-version.md" }),
    ).toThrow(/docs\/legal\/bad-version\.md:3/);

    const badStatus = "# Văn bản\n\n> Phiên bản: 2026-11-26\n> Trạng thái: pending\n\nNội dung.\n";
    expect(() =>
      parseLegalMarkdown(badStatus, { id: "terms", sourcePath: "docs/legal/bad-status.md" }),
    ).toThrow(/docs\/legal\/bad-status\.md:4/);
  });

  it("publishedNeedsDateAndNoOpenPoints", () => {
    const placeholderDate = [
      "# Văn bản",
      "",
      "> Phiên bản: 2026-11-26",
      "> Trạng thái: published",
      "",
      "Có hiệu lực từ: [NGÀY CÓ HIỆU LỰC]",
      "",
      "Nội dung.",
    ].join("\n");
    expect(() =>
      parseLegalMarkdown(placeholderDate, { id: "terms", sourcePath: "docs/legal/placeholder.md" }),
    ).toThrow(/docs\/legal\/placeholder\.md:6/);

    const missingDate = [
      "# Văn bản",
      "",
      "> Phiên bản: 2026-11-26",
      "> Trạng thái: published",
      "",
      "Nội dung.",
    ].join("\n");
    expect(() =>
      parseLegalMarkdown(missingDate, { id: "terms", sourcePath: "docs/legal/no-date.md" }),
    ).toThrow(/docs\/legal\/no-date\.md/);

    const withOpenPoints = [
      "# Văn bản",
      "",
      "> Phiên bản: 2026-11-26",
      "> Trạng thái: published",
      "",
      "Có hiệu lực từ: 26/11/2026",
      "",
      "## Điểm cần đơn vị vận hành xác nhận",
      "",
      "- Điểm mở 1",
    ].join("\n");
    expect(() =>
      parseLegalMarkdown(withOpenPoints, { id: "terms", sourcePath: "docs/legal/open-points.md" }),
    ).toThrow(/docs\/legal\/open-points\.md:8/);

    for (const { id, sourceFile } of LEGAL_SOURCES) {
      const parsed = parseLegalMarkdown(readFileSync(path.join(DOCS_LEGAL_DIR, sourceFile), "utf8"), {
        id,
        sourcePath: `docs/legal/${sourceFile}`,
      });
      expect(parsed.status).toBe("published");
      expect(parsed.effectiveDate).toBe("2026-11-26");
    }
  });

  it("contentChangeNeedsNewVersion", () => {
    const sourcePath = path.join(DOCS_LEGAL_DIR, "thi360-dieu-kien-giao-dich-chung.md");
    const raw = readFileSync(sourcePath, "utf8");
    const baseline = parseLegalMarkdown(raw, { id: "terms", sourcePath: "docs/legal/thi360-dieu-kien-giao-dich-chung.md" });

    const modifiedSameVersion = `${raw}\nĐoạn sửa thử nghiệm chưa tăng phiên bản.\n`;
    expect(() =>
      parseLegalMarkdown(modifiedSameVersion, {
        id: "terms",
        sourcePath: "docs/legal/thi360-dieu-kien-giao-dich-chung.md",
        existingDoc: baseline,
      }),
    ).toThrow(/phải tăng phiên bản/);

    const modifiedBumpedVersion = modifiedSameVersion.replace("> Phiên bản: 2026-11-26", "> Phiên bản: 2026-11-27");
    const bumped = parseLegalMarkdown(modifiedBumpedVersion, {
      id: "terms",
      sourcePath: "docs/legal/thi360-dieu-kien-giao-dich-chung.md",
      existingDoc: baseline,
    });
    expect(bumped.version).toBe("2026-11-27");
    expect(bumped.contentHash).not.toBe(baseline.contentHash);
  });

  it("effectiveDateLineIsNotABlock", () => {
    for (const { id } of LEGAL_SOURCES) {
      for (const locale of ["vi", "en"] as const) {
        const doc = readJson(path.join(CONTRACTS_LEGAL_DIR, `${id}.${locale}.json`));
        expect(doc.effectiveDate).toBe("2026-11-26");
        for (const block of doc.blocks) {
          if (block.type === "paragraph") {
            expect(block.text).not.toContain("Có hiệu lực từ");
            expect(block.text).not.toContain("Effective from");
          }
        }
      }
    }
  });
});
