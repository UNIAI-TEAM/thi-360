import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative, sep } from "node:path";
import { describe, expect, it } from "vitest";

const SRC_ROOT = join(process.cwd(), "src");
const RICH_TEXT_ROOT = join(SRC_ROOT, "components", "rich-text");

/** Bốn tệp của bộ hiển thị đã chuyển sang `packages/ui` (`@thi360/ui/rich-text`), plan FA-1. */
const MOVED_FILES = ["view/rich-text-view.tsx", "math-render.ts", "sanitize.ts", "html-contract.ts"];

/** Đường dẫn mô-đun tới bản cũ: `.../rich-text/{view/rich-text-view,math-render,sanitize,html-contract}` hoặc `./sanitize` trong thư mục `rich-text`. */
const OLD_SPECIFIER = /^(?:@\/components\/rich-text\/|(?:\.{1,2}\/)+)(?:view\/rich-text-view|math-render|sanitize|html-contract)$/;

function sourceFiles(directory: string): string[] {
  return readdirSync(directory).flatMap((entry) => {
    const path = join(directory, entry);
    if (statSync(path).isDirectory()) return sourceFiles(path);
    return /\.(?:ts|tsx)$/.test(entry) ? [path] : [];
  });
}

function moduleSpecifiers(source: string): string[] {
  const pattern = /(?:\bfrom\s*|\bimport\s*\(?\s*|\bvi\.(?:mock|doMock)\(\s*)["']([^"']+)["']/g;
  return Array.from(source.matchAll(pattern), (match) => match[1]!);
}

describe("web-admin không còn bản riêng của bộ hiển thị rich-text", () => {
  for (const file of MOVED_FILES) {
    it(`${file} không tồn tại`, () => {
      expect(existsSync(join(RICH_TEXT_ROOT, file))).toBe(false);
    });
  }

  it("không còn import nào trỏ tới bốn tệp đã xóa, kể cả trong vi.mock", () => {
    const offenders: string[] = [];
    for (const file of sourceFiles(SRC_ROOT)) {
      if (file === __filename) continue;
      const path = relative(SRC_ROOT, file).split(sep).join("/");
      const insideRichText = path.startsWith("components/rich-text/");
      for (const specifier of moduleSpecifiers(readFileSync(file, "utf8"))) {
        const pointsToOldModule = OLD_SPECIFIER.test(specifier) && (insideRichText || specifier.startsWith("@/"));
        if (pointsToOldModule) offenders.push(`${path}: ${specifier}`);
      }
    }
    expect(offenders).toEqual([]);
  });

  it("chỉ mục của trình soạn không xuất lại bộ hiển thị", () => {
    const index = readFileSync(join(RICH_TEXT_ROOT, "index.ts"), "utf8");
    expect(index).not.toMatch(/RichTextView|sanitizeQuestionHtml|questionHtmlText|hasUnbalancedMath/);
  });
});
