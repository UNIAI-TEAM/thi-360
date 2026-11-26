import { existsSync, readdirSync, readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

const LEGACY_BRAND_RE = new RegExp(["thi", "[ \\t\\u00A0]+", "360"].join(""), "i");

const SCAN_ROOTS = [
  "apps/web-student/src",
  "apps/web-admin/src",
  "packages/ui/src",
  "e2e/tests",
] as const;

function walkFiles(dir: string): string[] {
  const results: string[] = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name === "node_modules" || entry.name === ".next") continue;
      results.push(...walkFiles(full));
    } else if (entry.isFile()) {
      results.push(full);
    }
  }
  return results;
}

function isBinary(buffer: Buffer): boolean {
  return buffer.includes(0);
}

describe("brand display name scan (web & e2e)", () => {
  it("matches spaced variants and ignores technical identifiers", () => {
    expect(LEGACY_BRAND_RE.test("Thi" + " 360")).toBe(true);
    expect(LEGACY_BRAND_RE.test("THi" + " 360")).toBe(true);
    expect(LEGACY_BRAND_RE.test("THI" + " 360")).toBe(true);
    expect(LEGACY_BRAND_RE.test("Thi" + "\u00A0360")).toBe(true);
    expect(LEGACY_BRAND_RE.test("Thi" + "\t360")).toBe(true);

    expect(LEGACY_BRAND_RE.test("Thi360")).toBe(false);
    expect(LEGACY_BRAND_RE.test("thi360")).toBe(false);
    expect(LEGACY_BRAND_RE.test("thi-360")).toBe(false);
    expect(LEGACY_BRAND_RE.test("thi360-green")).toBe(false);
  });

  it("finds zero spaced brand name occurrences across web apps, ui package, and e2e tests", () => {
    const repoRoot = path.resolve(import.meta.dirname, "../../../..");
    let scannedCount = 0;
    const violations: string[] = [];

    for (const relRoot of SCAN_ROOTS) {
      const absRoot = path.join(repoRoot, relRoot);
      expect(existsSync(absRoot), `missing scan root: ${relRoot}`).toBe(true);
      for (const file of walkFiles(absRoot)) {
        const raw = readFileSync(file);
        if (isBinary(raw)) continue;
        scannedCount += 1;
        const lines = raw.toString("utf8").split(/\r?\n/);
        lines.forEach((line, index) => {
          if (LEGACY_BRAND_RE.test(line)) {
            violations.push(`${path.relative(repoRoot, file)}:${index + 1}: ${line.trim()}`);
          }
        });
      }
    }

    expect(scannedCount).toBeGreaterThan(0);
    expect(violations).toEqual([]);
  });
});
