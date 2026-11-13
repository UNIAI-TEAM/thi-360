import { describe, expect, it } from "vitest";
import en from "./en.json";
import vi from "./vi.json";

function collectLeafPaths(obj: unknown, prefix = ""): string[] {
  if (typeof obj !== "object" || obj === null || Array.isArray(obj)) {
    return [prefix];
  }
  const paths: string[] = [];
  for (const [key, value] of Object.entries(obj)) {
    const next = prefix ? `${prefix}.${key}` : key;
    paths.push(...collectLeafPaths(value, next));
  }
  return paths;
}

describe("i18n messages", () => {
  it("vi.json và en.json có cùng tập khóa và thứ tự khóa", () => {
    expect(collectLeafPaths(en)).toEqual(collectLeafPaths(vi));
  });
});
