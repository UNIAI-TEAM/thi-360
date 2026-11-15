import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import en from "@/messages/en.json";
import vi from "@/messages/vi.json";

/**
 * C-09: một khóa gọi trong mã mà không có trong file dịch thì next-intl in nguyên tên khóa ra màn hình
 * ("catalog.emptyItemsFilteredTitle"). Test này quét các lời gọi `t("khóa")` có khóa viết thẳng trong hai
 * feature Cấu hình chung và Lớp học, rồi đối chiếu với cả hai file dịch. Khóa ghép bằng template string
 * (ví dụ `errorCodes.${code}`) không quét được ở đây; các bảng mã đó có test riêng.
 */
const FEATURE_ROOTS = ["../catalog", "../classes"].map((relative) =>
  fileURLToPath(new URL(relative, import.meta.url)),
);

function sourceFiles(directory: string): string[] {
  return readdirSync(directory).flatMap((entry) => {
    const path = join(directory, entry);
    if (statSync(path).isDirectory()) return entry === "__tests__" ? [] : sourceFiles(path);
    return /\.tsx?$/.test(entry) && !/\.test\.tsx?$/.test(entry) ? [path] : [];
  });
}

function hasMessage(messages: unknown, path: string): boolean {
  let node: unknown = messages;
  for (const part of path.split(".")) {
    if (typeof node !== "object" || node === null || !(part in node)) return false;
    node = (node as Record<string, unknown>)[part];
  }
  return typeof node === "string";
}

function staticKeysOf(source: string): string[] {
  const namespaceByTranslator = new Map<string, string>();
  for (const match of source.matchAll(/const\s+(\w+)\s*=\s*useTranslations\(\s*"([\w.]+)"\s*\)/g)) {
    namespaceByTranslator.set(match[1]!, match[2]!);
  }
  const keys: string[] = [];
  for (const [translator, namespace] of namespaceByTranslator) {
    const call = new RegExp(`(?<![\\w.])${translator}\\(\\s*"([^"]+)"`, "g");
    for (const match of source.matchAll(call)) keys.push(`${namespace}.${match[1]!}`);
  }
  return keys;
}

describe("khóa dịch của Cấu hình chung và Lớp học (C-09)", () => {
  const usedKeys = [
    ...new Set(
      FEATURE_ROOTS.flatMap(sourceFiles).flatMap((file) => staticKeysOf(readFileSync(file, "utf8"))),
    ),
  ].sort();

  it("quét được khóa trong mã nguồn", () => {
    expect(usedKeys.length).toBeGreaterThan(200);
    expect(usedKeys).toContain("catalog.emptyItemsFilteredTitle");
  });

  it.each([
    ["vi", vi],
    ["en", en],
  ])("mọi khóa viết thẳng trong mã đều có trong %s.json", (_locale, messages) => {
    expect(usedKeys.filter((key) => !hasMessage(messages, key))).toEqual([]);
  });
});
