// @vitest-environment node
import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";
import type { LegalDocument, LegalDocumentId, LegalListBlock } from "./legal-document";
import { LEGAL_SOURCES, parseLegalMarkdown } from "./parse-legal-markdown";

const REPO_ROOT = path.resolve(import.meta.dirname, "../../../../..");
const DOCS_LEGAL_DIR = path.join(REPO_ROOT, "docs/legal");
const CONTRACTS_LEGAL_DIR = path.join(REPO_ROOT, "contracts/mobile/legal");

const EMAIL_RE = /[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/g;
const WEB_HOST_RE = /\b(?:https?:\/\/)?([a-z0-9-]+(?:\.[a-z0-9-]+)+)(?:\/[^\s,.;:)"']*)?/gi;
const VERSION_OR_DECIMAL_RE = /^\d+(?:\.\d+)+$/;

function collectAllText(doc: LegalDocument): string[] {
  const parts: string[] = [doc.title];
  if (doc.note) parts.push(doc.note);
  for (const block of doc.blocks) {
    switch (block.type) {
      case "heading":
      case "paragraph":
        parts.push(block.text);
        break;
      case "list":
        parts.push(...block.items);
        break;
      case "table":
        parts.push(...block.headers);
        for (const row of block.rows) parts.push(...row);
        break;
    }
  }
  return parts;
}

function findOperatorList(doc: LegalDocument): readonly string[] {
  const headingPrefix = doc.id === "terms" ? "9." : "5.";
  const idx = doc.blocks.findIndex((b) => b.type === "heading" && b.level === 2 && b.text.startsWith(headingPrefix));
  if (idx < 0) return [];
  const next = doc.blocks[idx + 1];
  return next && next.type === "list" ? (next as LegalListBlock).items : [];
}

function validateLegalContent(
  docs: readonly LegalDocument[],
  sourceDocs: Record<LegalDocumentId, LegalDocument>,
): string[] {
  const errors: string[] = [];

  for (const doc of docs) {
    const label = `${doc.id}.${doc.locale}.json`;
    const allTexts = collectAllText(doc);
    const joined = allTexts.join("\n");

    if (!joined.includes("Thi360")) {
      errors.push(`${label}: missing product name "Thi360"`);
    }
    if (/Thi 360/.test(joined)) {
      errors.push(`${label}: contains disallowed legacy name "Thi 360"`);
    }

    for (const text of allTexts) {
      const withoutEmails = text.replace(EMAIL_RE, " ");
      for (const match of withoutEmails.matchAll(WEB_HOST_RE)) {
        const host = match[1]!.toLowerCase();
        if (VERSION_OR_DECIMAL_RE.test(host)) continue;
        if (host !== "thi360.vn") {
          errors.push(`${label}: disallowed web host "${host}" in "${text}"`);
        }
      }
    }

    const sourceOperatorLines = findOperatorList(sourceDocs[doc.id]);
    const actualOperatorLines = findOperatorList(doc);
    if (actualOperatorLines.length !== sourceOperatorLines.length) {
      errors.push(
        `${label}: operator block line count mismatch (${actualOperatorLines.length} vs ${sourceOperatorLines.length})`,
      );
      continue;
    }

    if (doc.locale === "vi") {
      for (let i = 0; i < sourceOperatorLines.length; i += 1) {
        if (actualOperatorLines[i] !== sourceOperatorLines[i]) {
          errors.push(
            `${label}: operator line[${i}] differs from docs/legal/ ("${actualOperatorLines[i]}" vs "${sourceOperatorLines[i]}")`,
          );
        }
      }
    } else {
      const sourceText = sourceOperatorLines.join("\n");
      const actualText = actualOperatorLines.join("\n");
      const taxId = /0109600534/.exec(sourceText)?.[0];
      const email = /[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/.exec(sourceText)?.[0];
      const phone = /\b0\d{9}\b/.exec(sourceText)?.[0];

      if (taxId && !actualText.includes(taxId)) {
        errors.push(`${label}: missing tax code ${taxId}`);
      }
      if (email && !actualText.includes(email)) {
        errors.push(`${label}: missing operator email ${email}`);
      }
      if (phone && !actualText.includes(phone)) {
        errors.push(`${label}: missing operator phone ${phone}`);
      }
      if (!actualText.includes("HSA")) {
        errors.push(`${label}: missing operator company name HSA`);
      }
      if (!actualText.includes("11B") || !actualText.includes("3/29") || !actualText.includes("2/26")) {
        errors.push(`${label}: missing operator Hanoi/HCMC office addresses`);
      }
      if (!actualText.includes("thi360.vn")) {
        errors.push(`${label}: missing website thi360.vn in operator block`);
      }
    }
  }

  return errors;
}

function loadCommittedDocs(): LegalDocument[] {
  const docs: LegalDocument[] = [];
  for (const { id } of LEGAL_SOURCES) {
    for (const locale of ["vi", "en"] as const) {
      docs.push(JSON.parse(readFileSync(path.join(CONTRACTS_LEGAL_DIR, `${id}.${locale}.json`), "utf8")) as LegalDocument);
    }
  }
  return docs;
}

function loadSourceDocs(): Record<LegalDocumentId, LegalDocument> {
  const map = {} as Record<LegalDocumentId, LegalDocument>;
  for (const { id, sourceFile } of LEGAL_SOURCES) {
    map[id] = parseLegalMarkdown(readFileSync(path.join(DOCS_LEGAL_DIR, sourceFile), "utf8"), {
      id,
      sourcePath: `docs/legal/${sourceFile}`,
    });
  }
  return map;
}

describe("legal content audit (ĐT 753)", () => {
  it("mọi địa chỉ web là thi360.vn, tên dịch vụ là Thi360, thông tin đơn vị vận hành trùng docs/legal/", () => {
    const docs = loadCommittedDocs();
    const sources = loadSourceDocs();
    expect(validateLegalContent(docs, sources)).toEqual([]);
  });

  it("báo lỗi khi xuất hiện địa chỉ khác host, tên Thi 360 hoặc lệch mã số thuế", () => {
    const docs = loadCommittedDocs();
    const sources = loadSourceDocs();

    const withOtherHost: LegalDocument[] = docs.map((doc, idx) =>
      idx === 0
        ? { ...doc, blocks: [...doc.blocks, { type: "paragraph", text: "Xem thêm tại https://hsavnu.edu.vn/x" }] }
        : doc,
    );
    expect(validateLegalContent(withOtherHost, sources).join("\n")).toContain("hsavnu.edu.vn");

    const withWrongTax: LegalDocument[] = docs.map((doc) => {
      if (doc.id !== "terms" || doc.locale !== "vi") return doc;
      return {
        ...doc,
        blocks: doc.blocks.map((block) =>
          block.type === "list"
            ? { ...block, items: block.items.map((item) => item.replace("0109600534", "9999999999")) }
            : block,
        ),
      };
    });
    expect(validateLegalContent(withWrongTax, sources).join("\n")).toContain("operator line");

    const withLegacyName: LegalDocument[] = docs.map((doc, idx) =>
      idx === 1 ? { ...doc, title: "Thi 360 Terms" } : doc,
    );
    expect(validateLegalContent(withLegacyName, sources).join("\n")).toContain("Thi 360");
  });
});
