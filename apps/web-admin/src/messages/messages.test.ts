import { describe, expect, it } from "vitest";
import { createTranslator } from "next-intl";
import en from "./en.json";
import vi from "./vi.json";
const VIETNAMESE_DIACRITIC_RE =
  /[àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđÀÁẢÃẠĂẰẮẲẴẶÂẦẤẨẪẬÈÉẺẼẸÊỀẾỂỄỆÌÍỈĨỊÒÓỎÕỌÔỒỐỔỖỘƠỜỚỞỠỢÙÚỦŨỤƯỪỨỬỮỰỲÝỶỸỴĐ]/;

function collectLeaves(obj: unknown, prefix = ""): Array<{ path: string; value: unknown }> {
  if (typeof obj !== "object" || obj === null || Array.isArray(obj)) {
    return [{ path: prefix, value: obj }];
  }
  const leaves: Array<{ path: string; value: unknown }> = [];
  for (const [key, value] of Object.entries(obj)) {
    const next = prefix ? `${prefix}.${key}` : key;
    leaves.push(...collectLeaves(value, next));
  }
  return leaves;
}

function extractPlaceholders(text: string): string[] {
  return Array.from(new Set(Array.from(text.matchAll(/\{([a-zA-Z0-9_]+)\}/g), (m) => m[1]!))).sort();
}

describe("i18n messages", () => {
  it("vi.json và en.json có cùng tập khóa, thứ tự khóa, giá trị khác rỗng và cùng tập placeholder", () => {
    const enLeaves = collectLeaves(en);
    const viLeaves = collectLeaves(vi);

    expect(enLeaves.map((entry) => entry.path)).toEqual(viLeaves.map((entry) => entry.path));

    for (let i = 0; i < viLeaves.length; i++) {
      const viLeaf = viLeaves[i]!;
      const enLeaf = enLeaves[i]!;
      expect(typeof viLeaf.value, `vi.${viLeaf.path} type`).toBe("string");
      expect(typeof enLeaf.value, `en.${enLeaf.path} type`).toBe("string");
      expect((viLeaf.value as string).trim().length, `vi.${viLeaf.path} non-empty`).toBeGreaterThan(0);
      expect((enLeaf.value as string).trim().length, `en.${enLeaf.path} non-empty`).toBeGreaterThan(0);
      expect(
        extractPlaceholders(enLeaf.value as string),
        `placeholders for ${viLeaf.path}`,
      ).toEqual(extractPlaceholders(viLeaf.value as string));
      expect(VIETNAMESE_DIACRITIC_RE.test(enLeaf.value as string), `en.${enLeaf.path} has Vietnamese`).toBe(false);
    }
  });
});

describe("en plurals of bulk-import confirm and catalog branch move", () => {
  const t = createTranslator({ locale: "en", messages: en });
  const cases = [
    {
      key: "bulkImport.confirm.cards.willCreateValue",
      values: {},
      one: "1 account",
      other: "5 accounts",
    },
    {
      key: "bulkImport.confirm.confirmAction",
      values: {},
      one: "Confirm creating 1 account",
      other: "Confirm creating 5 accounts",
    },
    {
      key: "bulkImport.preview.continueConfirm",
      values: {},
      one: "Confirm creating 1 account",
      other: "Confirm creating 5 accounts",
    },
    {
      key: "catalog.moveBranchDialog.summaryWithChildren",
      values: { levelName: "Chapter", code: "C1", name: "Algebra" },
      one: "Moving: Chapter C1 — Algebra, with 1 child item.",
      other: "Moving: Chapter C1 — Algebra, with 5 child items.",
    },
  ] as const;

  it.each(cases)("$key: số ít với 1, số nhiều với 5", ({ key, values, one, other }) => {
    // The key set is a literal union only for generated types; the catalog is checked by messages.test parity.
    const translate = t as unknown as (k: string, v: Record<string, unknown>) => string;
    expect(translate(key, { ...values, count: 1 })).toBe(one);
    expect(translate(key, { ...values, count: 5 })).toBe(other);
  });
});
