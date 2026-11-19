import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

/**
 * Ranh giới bundle (plan FS-4 AC-5): tệp gốc `src/index.ts` được mọi trang import — kéo
 * rich-text/question vào đó là kéo KaTeX vào chunk đầu của `/` và `/login`. Hai cổng con
 * `./rich-text` và `./question` nằm ngoài tệp gốc.
 */
describe("packages/ui barrel boundary", () => {
  const srcDir = path.resolve(import.meta.dirname, "../..");

  it("src/index.ts does not re-export or import rich-text or question", () => {
    const barrel = readFileSync(path.join(srcDir, "index.ts"), "utf8");
    const specifiers = [...barrel.matchAll(/(?:from|import)\s*\(?\s*["']([^"']+)["']/g)].map(
      (match) => match[1]!,
    );
    expect(specifiers.length).toBeGreaterThan(0);
    for (const specifier of specifiers) {
      expect(specifier).not.toMatch(/(^|[/\\])(rich-text|question)([/\\]|$)/);
    }
    // Cả `export * from` lẫn `export { X } from` đều nằm trong `specifiers` ở trên;
    // dòng này chặn thêm cú pháp `export ... = require`/re-export viết tắt nếu có.
    expect(barrel).not.toMatch(/export\s+\{[^}]*RichTextView/);
  });

  it("src/index.ts does not import katex transitively", () => {
    const barrel = readFileSync(path.join(srcDir, "index.ts"), "utf8");
    expect(barrel).not.toMatch(/katex/);
  });

  it("root barrel only imports from existing shared dirs, never renderer/question dirs", () => {
    // Tệp gốc chỉ được phép import tương đối vào các thư mục dùng chung đã có — kiểm
    // duyệt tên thư mục con của từng specifier để mã mới không lách qua danh sách trên.
    const barrel = readFileSync(path.join(srcDir, "index.ts"), "utf8");
    for (const match of barrel.matchAll(/from\s+["']\.\/([^/]+)\//g)) {
      const segment = match[1]!;
      expect(["components", "lib", "messages", "theme", "styles"]).toContain(segment);
    }
  });
});
