import { describe, expect, it } from "vitest";
import en from "./en.json";
import vi from "./vi.json";

function collectKeys(obj: unknown, prefix = ""): string[] {
  if (typeof obj !== "object" || obj === null || Array.isArray(obj)) {
    return [prefix];
  }
  const keys: string[] = [];
  for (const [key, value] of Object.entries(obj)) {
    const nextPrefix = prefix ? `${prefix}.${key}` : key;
    if (typeof value === "object" && value !== null && !Array.isArray(value)) {
      keys.push(...collectKeys(value, nextPrefix));
    } else {
      keys.push(nextPrefix);
    }
  }
  return keys;
}

describe("messages parity (vi.json / en.json)", () => {
  it("vi.json và en.json có cùng tập khóa và thứ tự khóa", () => {
    expect(collectKeys(en)).toEqual(collectKeys(vi));
  });
});
