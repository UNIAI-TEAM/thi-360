// @vitest-environment node
import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

type LegalBlock =
  | { type: "heading"; level: number; text: string }
  | { type: "paragraph"; text: string }
  | { type: "list"; ordered: boolean; items: string[] }
  | { type: "table"; headers: string[]; rows: string[][] };

type LegalDocument = {
  id: string;
  locale: string;
  version: string;
  status: string;
  blocks: LegalBlock[];
};

const LEGAL_DIR = path.resolve(import.meta.dirname, "../../../../../contracts/mobile/legal");

function readLegal(filename: string): LegalDocument {
  return JSON.parse(readFileSync(path.join(LEGAL_DIR, filename), "utf8")) as LegalDocument;
}

function findListAfterHeading(doc: LegalDocument, headingPattern: RegExp): string[] {
  const headingIndex = doc.blocks.findIndex(
    (block) => block.type === "heading" && headingPattern.test(block.text),
  );
  expect(headingIndex).toBeGreaterThanOrEqual(0);
  const next = doc.blocks[headingIndex + 1];
  expect(next?.type).toBe("list");
  return next?.type === "list" ? next.items : [];
}

describe("Chính sách bảo mật thông tin nêu quyền tự xóa tài khoản (AC-14)", () => {
  it("privacy.vi.json và privacy.en.json ở phiên bản 2026-11-26, trạng thái published và nêu tự xóa trong app lẫn trên website", () => {
    const viDoc = readLegal("privacy.vi.json");
    const enDoc = readLegal("privacy.en.json");

    expect(viDoc.version).toBe("2026-11-26");
    expect(viDoc.status).toBe("published");
    expect(enDoc.version).toBe("2026-11-26");
    expect(enDoc.status).toBe("published");

    const viRightsItems = findListAfterHeading(viDoc, /Quyền của người dùng/i);
    const viSelfDeleteItem = viRightsItems.find((item) => item.includes("thi360.vn/account/delete"));
    expect(viSelfDeleteItem).toBeDefined();
    expect(viSelfDeleteItem).toContain("Tài khoản");
    expect(viSelfDeleteItem).toContain("Xóa tài khoản");
    expect(viSelfDeleteItem).toContain("thi360.vn/account/delete");

    const enRightsItems = findListAfterHeading(enDoc, /User rights/i);
    const enSelfDeleteItem = enRightsItems.find((item) => item.includes("/account/delete"));
    expect(enSelfDeleteItem).toBeDefined();
    expect(enSelfDeleteItem).toContain("Account");
    expect(enSelfDeleteItem).toContain("Delete account");
    expect(enSelfDeleteItem).toContain("thi360.vn/account/delete");
  });
});
