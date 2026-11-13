import { describe, expect, it } from "vitest";
import en from "./en.json";
import vi from "./vi.json";

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
    }
  });
});
