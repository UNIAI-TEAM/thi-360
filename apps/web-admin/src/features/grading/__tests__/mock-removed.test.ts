import { existsSync, readdirSync, readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

const WEB_ADMIN_ROOT = path.resolve(import.meta.dirname, "../../../..");
const SRC_DIR = path.join(WEB_ADMIN_ROOT, "src");
const ENV_EXAMPLE = path.join(WEB_ADMIN_ROOT, ".env.example");

const BANNED_FILES = [
  "src/lib/mock",
  "src/components/mock-data-banner.tsx",
  "src/components/mockup-placeholder.tsx",
  "src/features/grading/api.mock.ts",
  "src/features/grading/mock",
];

// Construct banned identifiers dynamically so static grep across the repo remains 0 hits.
const BANNED_TOKENS = [
  ["NEXT_PUBLIC", "THI360", "GRADING", "MOCK"].join("_"),
  ["is", "Grading", "Mock", "Enabled"].join(""),
  ["Mock", "Data", "Banner"].join(""),
  ["Mockup", "Placeholder"].join(""),
  ["mock", "State"].join(""),
  ["mock", "Flag"].join(""),
];

function walkFiles(dir: string): string[] {
  const results: string[] = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      results.push(...walkFiles(full));
    } else if (!full.endsWith(path.join("grading", "__tests__", "mock-removed.test.ts"))) {
      results.push(full);
    }
  }
  return results;
}

describe("B8 complete mock removal (AC-1)", () => {
  it("xóa hoàn toàn thư mục và tệp dữ liệu mẫu cũ", () => {
    for (const rel of BANNED_FILES) {
      expect(existsSync(path.join(WEB_ADMIN_ROOT, rel)), `${rel} must not exist`).toBe(false);
    }
  });

  it("không còn biến môi trường, hàm cờ, banner hay tham số ?mockState trong src và .env.example", () => {
    const targets = [...walkFiles(SRC_DIR), ENV_EXAMPLE];
    const matches: string[] = [];

    for (const file of targets) {
      const content = readFileSync(file, "utf8");
      for (const token of BANNED_TOKENS) {
        if (content.includes(token)) {
          matches.push(`${path.relative(WEB_ADMIN_ROOT, file)}: ${token}`);
        }
      }
    }

    expect(matches).toEqual([]);
  });
});
